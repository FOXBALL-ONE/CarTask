package top.foxball.cartask.scope

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.shared.PlateNumbers

/**
 * 按当前登录用户解析出本次请求生效的 [DataScope]。
 *
 * 角色到范围的映射：
 * - 普通用户：始终 [ScopeKind.SELF]，只看本人车牌与本人门禁身份；
 * - 部门管理：始终 [ScopeKind.DEPARTMENTS]，限定在被分配的部门内。选中的工作部门若在
 *   分配范围内则收窄到该部门，否则用整个分配范围；
 * - 平台管理与超级管理员：默认 [ScopeKind.ALL]；切换了具体工作部门后收窄到该部门。
 *
 * 解析失败时**直接抛异常**而不是返回「全部」：后台任务误调到带范围的查询时会立刻在测试里失败，
 * 而不是静默绕过范围把全量数据暴露出去。
 *
 * 注意这里不做缓存。归属关系（车主手机号、车牌关联）一旦被撤销，缓存会让撤销在缓存过期前
 * 不生效——那是安全问题，不是性能问题。每次请求多几条走索引的查询是可接受的代价。
 */
@Component
class DataScopeResolver(
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val userRepository: UserRepository,
    private val userManagedDepartmentRepository: UserManagedDepartmentRepository,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
) {
    /** 解析当前请求的数据范围。 */
    @Transactional(readOnly = true)
    fun current(): DataScope = forPrincipal(
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
            ?: error("数据范围解析失败：当前请求没有认证主体"),
    )

    @Transactional(readOnly = true)
    fun forPrincipal(principal: CurrentUserPrincipal): DataScope {
        // 部门快照一次请求取一份，同一请求内所有解析复用。
        val departments = departmentLinkResolver.snapshot()
        return when (principal.role) {
            SecurityRole.USER -> selfScope(principal.userId)

            SecurityRole.DEPT_ADMIN -> {
                // 管理范围由分配决定，含下级的展开已经在 managedDepartmentIds 里按标记做过，
                // 这里不再二次展开，否则「本部门」和「本部门及以下」就没有区别了。
                val managed = managedDepartmentIds(principal.userId, departments)
                val working = principal.workingDepartmentId
                if (working != null && working in managed) {
                    departmentScope(departments, setOf(working), expandDescendants = false)
                } else {
                    departmentScope(departments, managed, expandDescendants = false)
                }
            }

            // 超级管理员与平台管理：默认全局；选了具体工作部门就收窄到该部门**及其下级**——
            // 手工选一个上级部门时，只看到该部门本身而不含下级会非常反直觉。
            else -> principal.workingDepartmentId
                ?.let { departmentScope(departments, setOf(it), expandDescendants = true) }
                ?: DataScope.All
        }
    }

    private fun departmentScope(
        departments: DepartmentSnapshot,
        departmentIds: Set<Long>,
        expandDescendants: Boolean,
    ): DataScope {
        val ids = if (expandDescendants) departments.withDescendants(departmentIds) else departmentIds
        return DataScope.departments(
            ids = ids,
            codes = departments.codesOf(ids),
            names = departments.namesOf(ids),
        )
    }

    /**
     * 部门管理可管的部门集合。
     *
     * 显式分配优先，没有分配时退回归属部门——否则部门管理首次登录会得到一个空范围，
     * 页面全空却看不出原因。
     */
    private fun managedDepartmentIds(userId: Long, departments: DepartmentSnapshot): Set<Long> {
        val assigned = userManagedDepartmentRepository.findDepartmentIds(userId)
        val withDescendants = userManagedDepartmentRepository.findDepartmentIdsWithDescendants(userId)
        val ids = assigned
            .flatMap { id -> if (id in withDescendants) departments.withDescendants(setOf(id)) else setOf(id) }
            .toSet()
        if (ids.isNotEmpty()) return ids
        return setOfNotNull(userRepository.findById(userId).orElse(null)?.department?.id)
    }

    private fun selfScope(userId: Long): DataScope {
        val phone = userRepository.findById(userId).orElse(null)?.phone?.trim()?.takeIf(String::isNotEmpty)

        // 车主归属由 ScopeQuerySupport 统一解析（显式指定优先，手机号命中且未被指定给别人的次之），
        // 避免范围解析与逐行可见性判定各算一套而产生分歧。
        val ownerIds = scopeQuerySupport.ownersOf(userId, phone).mapNotNull { it.id }.toSet()

        val plates = buildList {
            addAll(parkingPlateRepository.findByLinkedUserId(userId))
            if (ownerIds.isNotEmpty()) addAll(parkingPlateRepository.findByOwnerIdIn(ownerIds))
        }
        val gatePersons = if (phone == null) emptyList() else gatePersonRepository.findByPhone(phone)

        return DataScope.self(
            userId = userId,
            phone = phone,
            carNumbers = PlateNumbers.normalizeAll(plates.map { it.plate }),
            gatePersonCodes = gatePersons.map { it.code }.toSet(),
            gatePersonNames = gatePersons.map { it.name }.toSet(),
        )
    }
}
