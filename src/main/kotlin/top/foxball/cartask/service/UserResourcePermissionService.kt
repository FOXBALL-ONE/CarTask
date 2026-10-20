package top.foxball.cartask.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import top.foxball.cartask.authentication.RoleAssignmentPolicy
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.UserResourcePermission
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.repository.UserResourcePermissionRepository
import top.foxball.cartask.scope.ManagedDepartmentInput
import top.foxball.cartask.scope.ManagedDepartmentView
import top.foxball.cartask.scope.WorkingDepartmentService

data class UserResourcePermissionData(
    val resourceType: UserResourcePermission.ResourceType,
    val reviewEnabled: Boolean,
)

@Service
class UserResourcePermissionService(
    private val repository: UserResourcePermissionRepository,
    private val userRepository: UserRepository,
    private val departmentRepository: DepartmentRepository,
    private val workingDepartmentService: WorkingDepartmentService,
    private val roleAssignmentPolicy: RoleAssignmentPolicy,
) {
    fun grantsFor(userId: Long): List<UserResourcePermission> = repository.findByUserId(userId)

    fun departmentBindingsFor(userId: Long): List<ManagedDepartmentView> = workingDepartmentService.managedDepartmentsOf(userId)

    @Transactional
    fun configure(
        userId: Long,
        primaryDepartmentId: Long?,
        departmentIds: Collection<Long>,
        resourceTypes: Collection<String>,
        reviewResourceTypes: Collection<String>,
    ): List<UserResourcePermissionData> {
        roleAssignmentPolicy.requireSuperAdminForUserResourceAssignment()
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在: $userId") }
        val types = resourceTypes.map(UserResourcePermission.ResourceType::parse).toSet()
        val reviews = reviewResourceTypes.map(UserResourcePermission.ResourceType::parse).toSet()
        require(reviews.all { it in types }) { "审核权限必须属于已绑定的资源种类" }
        require(types.isEmpty() || SecurityRole.normalizeOrNull(user.role) == SecurityRole.USER) { "经办资源权限只能分配给普通用户账号" }
        val ids = departmentIds.toList()
        require(ids.distinct().size == ids.size) { "部门不能重复" }
        require(types.isEmpty() || ids.isNotEmpty()) { "经办账号至少要绑定一个部门" }
        require(types.isEmpty() || primaryDepartmentId in ids) { "主部门必须包含在部门绑定范围内" }
        ids.forEach { departmentId ->
            departmentRepository.findById(departmentId).orElseThrow { IllegalArgumentException("部门不存在: $departmentId") }
        }
        workingDepartmentService.replaceManagedDepartments(
            userId,
            ids.map { departmentId -> ManagedDepartmentInput(departmentId, includeDescendants = types.isNotEmpty()) },
        )
        return replace(userId, types.map { it.wireName }, reviews.map { it.wireName })
    }

    private fun replace(userId: Long, resourceTypes: Collection<String>, reviewResourceTypes: Collection<String>): List<UserResourcePermissionData> {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在: $userId") }
        val types = resourceTypes.map(UserResourcePermission.ResourceType::parse).toSet()
        val reviewTypes = reviewResourceTypes.map(UserResourcePermission.ResourceType::parse).toSet()
        require(reviewTypes.all { it in types }) { "审核权限必须属于已绑定的资源种类" }
        repository.deleteByUserId(userId)
        repository.flush()
        repository.saveAll(types.map { type ->
            UserResourcePermission().apply {
                this.user = user
                resourceType = type
                reviewEnabled = type in reviewTypes
            }
        })
        return repository.findByUserId(userId).map { UserResourcePermissionData(it.resourceType, it.reviewEnabled) }
    }
}
