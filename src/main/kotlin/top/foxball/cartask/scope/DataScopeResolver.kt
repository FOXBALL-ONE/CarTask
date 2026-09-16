package top.foxball.cartask.scope

/**
 * DataScopeResolver 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

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


@Component
/**
 * DataScopeResolver 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class DataScopeResolver(
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val userRepository: UserRepository,
    private val userManagedDepartmentRepository: UserManagedDepartmentRepository,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
) {
    
    @Transactional(readOnly = true)
            /**
             * current 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun current(): DataScope = forPrincipal(
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
            ?: error("数据范围解析失败：当前请求没有认证主体"),
    )
    
    @Transactional(readOnly = true)
            
            
            /**
             * forPrincipal 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun forPrincipal(principal: CurrentUserPrincipal): DataScope {
        val departments = departmentLinkResolver.snapshot()
        return when (principal.role) {
            SecurityRole.USER -> selfScope(principal.userId)
            
            SecurityRole.DEPT_ADMIN -> {
                val managed = managedDepartmentIds(principal.userId, departments)
                val working = principal.workingDepartmentId
                if (working != null && working in managed) {
                    departmentScope(departments, setOf(working), expandDescendants = false)
                } else {
                    departmentScope(departments, managed, expandDescendants = false)
                }
            }
            
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


