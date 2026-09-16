package top.foxball.cartask.scope

/**
 * WorkingDepartmentService 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.JwtAuthenticationException
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.UserManagedDepartment
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository


data class WorkingDepartmentOption(val id: Long, val name: String)


data class ManagedDepartmentView(val departmentId: Long, val departmentName: String, val includeDescendants: Boolean)


data class ManagedDepartmentInput(val departmentId: Long, val includeDescendants: Boolean)


data class WorkingDepartmentState(
    val currentId: Long?,
    val currentName: String?,
    
    val scope: String,
    val options: List<WorkingDepartmentOption>,
)


@Service
/**
 * WorkingDepartmentService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class WorkingDepartmentService(
    private val departmentRepository: DepartmentRepository,
    private val userManagedDepartmentRepository: UserManagedDepartmentRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: RedisTokenSessionRepository,
) {
    @Transactional(readOnly = true)
            
            
            /**
             * stateOf 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
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
    
    
    @Transactional(readOnly = true)
            /**
             * switchTo 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun switchTo(principal: CurrentUserPrincipal, departmentId: Long?): WorkingDepartmentState {
        if (principal.role == SecurityRole.USER) {
            throw AccessDeniedException("普通用户没有工作部门")
        }
        val allowed = allowedDepartmentIds(principal.userId, principal.role)
        if (allowed == null) {
            if (departmentId != null) {
                requireNotNull(departmentRepository.findById(departmentId).orElse(null)) { "部门不存在" }
            }
        } else {
            if (departmentId == null) throw AccessDeniedException("必须选择一个工作部门")
            if (departmentId !in allowed) throw AccessDeniedException("无权切换到该部门")
        }
        
        if (!sessionRepository.updateWorkingDepartment(principal.tokenId, departmentId)) {
            throw JwtAuthenticationException("登录状态已失效，请重新登录")
        }
        return stateOf(principal.userId, principal.role, departmentId)
    }
    
    
    @Transactional(readOnly = true)
            /**
             * allowedDepartmentIds 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun allowedDepartmentIds(userId: Long, role: String): Set<Long>? = when (SecurityRole.normalizeOrNull(role)) {
        SecurityRole.DEPT_ADMIN -> {
            val assigned = userManagedDepartmentRepository.findDepartmentIds(userId)
            val ownDepartment = userRepository.findById(userId).orElse(null)?.department?.id
            (assigned + setOfNotNull(ownDepartment)).toSet()
        }
        
        SecurityRole.USER -> emptySet()
        else -> null
    }
    
    
    @Transactional(readOnly = true)
            /**
             * managedDepartmentsOf 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun managedDepartmentsOf(userId: Long): List<ManagedDepartmentView> {
        val assignments = userManagedDepartmentRepository.findByUserId(userId)
        return assignments.mapNotNull { assignment ->
            val departmentId = assignment.department.id ?: return@mapNotNull null
            val name = departmentRepository.findById(departmentId).orElse(null)?.name ?: return@mapNotNull null
            ManagedDepartmentView(departmentId, name, assignment.includeDescendants)
        }
    }
    
    
    @Transactional
            /**
             * replaceManagedDepartments 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun replaceManagedDepartments(userId: Long, items: List<ManagedDepartmentInput>): List<ManagedDepartmentView> {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在: $userId") }
        val departmentIds = items.map { item -> item.departmentId }
        require(departmentIds.distinct().size == departmentIds.size) { "部门不能重复" }
        val departments = departmentIds.associateWith { departmentId ->
            departmentRepository.findById(departmentId)
                .orElseThrow { IllegalArgumentException("部门不存在: $departmentId") }
        }
        
        userManagedDepartmentRepository.deleteByUserId(userId)
        userManagedDepartmentRepository.flush()
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


