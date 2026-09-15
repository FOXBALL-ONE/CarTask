package top.foxball.cartask.scope

import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.UserManagedDepartment
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository

/** 一个可切换的工作部门选项。 */
data class WorkingDepartmentOption(val id: Long, val name: String)

/** 某个用户被分配的部门管理范围条目。 */
data class ManagedDepartmentView(val departmentId: Long, val departmentName: String, val includeDescendants: Boolean)

/** 替换部门管理范围时的输入条目。 */
data class ManagedDepartmentInput(val departmentId: Long, val includeDescendants: Boolean)

/** 当前工作部门状态，登录响应与会话查询共用。 */
data class WorkingDepartmentState(
    val currentId: Long?,
    val currentName: String?,
    /** ALL / DEPARTMENT / SELF，供前端决定是否显示切换器。 */
    val scope: String,
    val options: List<WorkingDepartmentOption>,
)

/**
 * 当前工作部门的读取与切换。
 *
 * 部门范围是「按人」分配的：部门管理只能在自己被分配的部门之间切换，平台管理与超级管理员
 * 可以在全部部门之间切换（含「全部」这一档）。
 */
@Service
class WorkingDepartmentService(
    private val departmentRepository: DepartmentRepository,
    private val userManagedDepartmentRepository: UserManagedDepartmentRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: RedisTokenSessionRepository,
) {
    @Transactional(readOnly = true)
            /**
             * stateOf：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param userId 参与本次处理的输入参数。
             * @param role 参与本次处理的输入参数。
             * @param workingDepartmentId 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun stateOf(userId: Long, role: String, workingDepartmentId: Long?): WorkingDepartmentState {
        val normalizedRole = SecurityRole.normalizeOrNull(role)
        val scope = when (normalizedRole) {
            SecurityRole.USER -> "SELF"
            SecurityRole.DEPT_ADMIN -> "DEPARTMENT"
            else -> if (workingDepartmentId == null) "ALL" else "DEPARTMENT"
        }
        val allowed = allowedDepartmentIds(userId, role)
        val options = departmentRepository.findAll()
            .filter { allowed == null || requireNotNull(it.id) in allowed }
            .sortedBy { it.sortOrder }
            .map { WorkingDepartmentOption(requireNotNull(it.id), it.name) }
        val currentId = workingDepartmentId?.takeIf { allowed == null || it in allowed }
        return WorkingDepartmentState(
            currentId = currentId,
            currentName = currentId?.let { id -> options.firstOrNull { it.id == id }?.name },
            scope = scope,
            options = options,
        )
    }

    /**
     * 切换当前工作部门并写回会话。
     *
     * 校验规则：普通用户不允许有工作部门；部门管理只能选被分配的部门且不能选「全部」；
     * 平台管理与超级管理员可选任一**真实存在**的部门或「全部」（null）。
     */
    @Transactional(readOnly = true)
    fun switchTo(principal: CurrentUserPrincipal, departmentId: Long?): WorkingDepartmentState {
        if (principal.role == SecurityRole.USER) {
            throw AccessDeniedException("普通用户没有工作部门")
        }
        val allowed = allowedDepartmentIds(principal.userId, principal.role)
        if (allowed == null) {
            // 不受部门限制的角色：可以为「全部」，也可以落到任一存在的部门。
            if (departmentId != null) {
                requireNotNull(departmentRepository.findById(departmentId).orElse(null)) { "部门不存在" }
            }
        } else {
            if (departmentId == null) throw AccessDeniedException("必须选择一个工作部门")
            if (departmentId !in allowed) throw AccessDeniedException("无权切换到该部门")
        }

        sessionRepository.updateWorkingDepartment(principal.tokenId, departmentId)
        return stateOf(principal.userId, principal.role, departmentId)
    }

    /**
     * 该用户可以切换到的部门集合。
     *
     * 返回 null 表示不受部门限制（可选「全部」或任一部门）。部门管理取「显式分配 ∪ 归属部门」，
     * 与范围解析保持同一口径。
     */
    @Transactional(readOnly = true)
    fun allowedDepartmentIds(userId: Long, role: String): Set<Long>? = when (SecurityRole.normalizeOrNull(role)) {
        SecurityRole.DEPT_ADMIN -> {
            val assigned = userManagedDepartmentRepository.findDepartmentIds(userId)
            val ownDepartment = userRepository.findById(userId).orElse(null)?.department?.id
            (assigned + setOfNotNull(ownDepartment)).toSet()
        }

        SecurityRole.USER -> emptySet()
        else -> null
    }

    /** 某个用户被分配的部门管理范围。 */
    @Transactional(readOnly = true)
    fun managedDepartmentsOf(userId: Long): List<ManagedDepartmentView> {
        val assignments = userManagedDepartmentRepository.findByUserId(userId)
        return assignments.mapNotNull { assignment ->
            val departmentId = assignment.department.id ?: return@mapNotNull null
            val name = departmentRepository.findById(departmentId).orElse(null)?.name ?: return@mapNotNull null
            ManagedDepartmentView(departmentId, name, assignment.includeDescendants)
        }
    }

    /**
     * 整体替换某个用户的部门管理范围。
     *
     * 每次替换都会撤销该用户已签发的会话：工作部门的合法集合变了，继续沿用旧会话里的
     * 工作部门可能出现「当前部门已不在范围内」的状态。
     */
    @Transactional
    fun replaceManagedDepartments(userId: Long, items: List<ManagedDepartmentInput>): List<ManagedDepartmentView> {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在: $userId") }
        val departmentIds = items.map { item -> item.departmentId }
        require(departmentIds.distinct().size == departmentIds.size) { "部门不能重复" }
        val departments = departmentIds.associateWith { departmentId ->
            departmentRepository.findById(departmentId)
                .orElseThrow { IllegalArgumentException("部门不存在: $departmentId") }
        }

        userManagedDepartmentRepository.deleteByUserId(userId)
        userManagedDepartmentRepository.saveAll(
            items.map { item ->
                UserManagedDepartment().apply {
                    this.user = user
                    this.department = departments.getValue(item.departmentId)
                    this.includeDescendants = item.includeDescendants
                }
            },
        )
        sessionRepository.incrementTokenVersion(userId)
        return managedDepartmentsOf(userId)
    }
}
