package top.foxball.cartask.scope

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.entity.Department
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.entity.UserResourcePermission

/** 人脸登记所有读写、审核和文件读取共用的范围策略。 */
@Component
class FaceRegistrationScopePolicy(
    private val dataScopeResolver: DataScopeResolver,
    private val userRepository: UserRepository,
    private val departmentRepository: DepartmentRepository,
    private val gatePersonRepository: GatePersonRepository,
) {
    fun principal(): CurrentUserPrincipal =
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
            ?: error("数据范围解析失败：当前请求没有认证主体")

    fun visible(person: GatePerson): Boolean {
        val scope = dataScopeResolver.current()
        if (scope.resourceKinds != null && UserResourcePermission.ResourceType.FACE !in scope.resourceKinds) return false
        if (scope.unrestricted) return true
        if (scope.departmentCodes.isNotEmpty()) {
            return person.departmentCode in scope.departmentCodes ||
                (person.departmentCode == null && person.dept in scope.departmentNames)
        }
        // 资源级授权不会扩大用户、车主等其他资源的 SELF 范围。
        if ("gate-person:department-scope" !in principal().permissions) return person.code in scope.gatePersonCodes
        val current = currentDepartment() ?: return false
        return person.departmentCode == current.departmentNumber ||
            (person.departmentCode == null && person.dept == current.name)
    }

    fun requireVisible(person: GatePerson?): GatePerson {
        if (person == null || !visible(person)) throw IllegalArgumentException("人员不存在")
        return person
    }

    fun resolveDepartment(requestedCode: String?): DepartmentInfo {
        val principal = principal()
        val current = currentDepartment()
        require(
            principal.role != SecurityRole.USER || principal.resourcePermissions.isEmpty() ||
                    "face" in principal.resourcePermissions,
        ) { "当前账号没有人脸资源权限" }
        if (principal.role == SecurityRole.USER && principal.resourcePermissions.isEmpty()) {
            require("gate-person:department-scope" in principal.permissions) { "当前账号没有人脸部门范围授权" }
        }
        val requested = requestedCode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            departmentRepository.findByDepartmentNumber(it) ?: throw IllegalArgumentException("工作部门不存在")
        }
        val selected = when {
            requested != null -> {
                val scope = dataScopeResolver.current()
                require(scope.unrestricted || requested.departmentNumber in scope.departmentCodes) {
                    "无权使用该工作部门"
                }
                requested
            }
            current != null -> current
            else -> throw IllegalArgumentException("当前账号没有可用的工作部门")
        }
        return DepartmentInfo(
            name = selected.name,
            code = selected.departmentNumber,
        )
    }

    fun resolveDepartmentText(value: String): DepartmentInfo {
        val trimmed = value.trim()
        val department = departmentRepository.findByDepartmentNumber(trimmed)
            ?: departmentRepository.findAll().firstOrNull { it.name == trimmed }
            ?: throw IllegalArgumentException("工作部门不存在")
        val scope = dataScopeResolver.current()
        require(scope.unrestricted || department.departmentNumber in scope.departmentCodes) { "无权使用该工作部门" }
        return DepartmentInfo(department.name, department.departmentNumber)
    }

    fun requirePhotoFile(file: StoredFile) {
        require("gate-person:read" in principal().permissions) { "人员不存在" }
        val person = requireVisible(file.businessId?.let(gatePersonRepository::findByCode))
        require(file.businessType == StoredFile.BUSINESS_GATE_PERSON && file.businessId == person.code) { "人员不存在" }
    }

    fun departmentOf(code: String): String? = gatePersonRepository.findByCode(code)?.departmentCode

    private fun currentDepartment(): Department? {
        val currentPrincipal = principal()
        if (currentPrincipal.role == SecurityRole.USER && currentPrincipal.resourcePermissions.isNotEmpty() && currentPrincipal.workingDepartmentId == null) {
            return null
        }
        return currentPrincipal.workingDepartmentId?.let { departmentRepository.findById(it).orElse(null) }
            ?: userRepository.findById(currentPrincipal.userId).orElse(null)?.department
    }

    data class DepartmentInfo(val name: String, val code: String)
}
