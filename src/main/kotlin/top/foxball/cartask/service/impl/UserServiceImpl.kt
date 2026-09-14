package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.RoleAssignmentPolicy
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.PositionRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.scope.ScopeKind
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.service.UserService
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import java.time.LocalDateTime

@Service
/** 用户账户服务，负责凭据编码及用户信息一致性校验。 */
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val departmentRepository: DepartmentRepository,
    private val positionRepository: PositionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenSessionRepository: RedisTokenSessionRepository,
    private val roleAssignmentPolicy: RoleAssignmentPolicy,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeGuard: ScopeGuard,
    private val auditService: AuditService? = null,
    private val roleRepository: RoleRepository? = null,
) : UserService {

    /** 将单条创建委托给批量创建路径，复用一致的校验和密码编码。 */
    @Transactional
    override fun create(command: UserService.CreateCommand): UserService.UserData =
        createBatch(listOf(command)).single()

    /** 校验用户名和邮箱唯一性后批量创建用户，并统一记录创建时间。 */
    @Transactional
    override fun createBatch(commands: List<UserService.CreateCommand>): List<UserService.UserData> {
        require(commands.isNotEmpty()) { "用户列表不能为空" }
        require(commands.map { it.username }.distinct().size == commands.size) { "批量创建的用户名不能重复" }
        require(commands.map { it.email }.distinct().size == commands.size) { "批量创建的邮箱不能重复" }
        commands.forEach { command ->
            require(command.username.isNotBlank()) { "用户名不能为空" }
            require(command.email.isNotBlank()) { "邮箱不能为空" }
            require(command.credential.isNotBlank()) { "凭据不能为空" }
            roleAssignmentPolicy.validateAssignment(command.role)
            require(!userRepository.existsByUsername(command.username)) { "用户名已存在" }
            require(!userRepository.existsByEmail(command.email)) { "邮箱已存在" }
        }
        val now = LocalDateTime.now()
        val savedUsers = userRepository.saveAll(commands.map { command ->
            val assignedRoles = resolveRoles(command.roleIds)
            User().apply {
                username = command.username
                nickName = command.nickName?.trim()?.takeIf(String::isNotEmpty)
                email = command.email
                passwordHash = passwordEncoder.encode(command.credential).toString()
                role = assignedRoles.mapNotNull { SecurityRole.normalizeOrNull(it.name) }.firstOrNull()
                    ?: SecurityRole.normalize(command.role)
                enabled = command.enabled
                phone = command.phone
                gender = command.gender
                jobTitle = command.jobTitle?.trim()?.takeIf(String::isNotEmpty)
                department = command.departmentId?.let { departmentId ->
                    departmentRepository.findById(departmentId)
                        .orElseThrow { IllegalArgumentException("部门不存在: $departmentId") }
                }
                position = command.positionId?.let { positionId ->
                    positionRepository.findById(positionId)
                        .orElseThrow { IllegalArgumentException("职位不存在: $positionId") }
                }
                status = command.status
                mustChangePassword = command.mustChangePassword
                roles = assignedRoles
                createdAt = now
                updatedAt = now
            }
        })
        savedUsers.forEach { user ->
            auditService?.record(
                AuditCommand(
                    AuditAction.USER_CREATED,
                    "user",
                    user.id?.toString(),
                    targetSummary = mapOf("username" to user.username, "role" to user.role, "department_id" to user.department?.id),
                    afterData = mapOf("enabled" to user.enabled, "status" to user.status.name),
                ),
            )
        }
        return savedUsers.map(::toData)
    }

    /** 按 ID 查询用户并映射为不含密码的返回数据。 */
    @Transactional
    override fun get(id: Long): UserService.UserData = toData(findUser(id))

    /** 保持请求 ID 顺序地批量查询用户，并拒绝缺失记录。 */
    @Transactional
    override fun getBatch(ids: List<Long>): List<UserService.UserData> {
        require(ids.isNotEmpty()) { "用户 ID 列表不能为空" }
        val usersById = userRepository.findAllById(ids.distinct()).associateBy { it.id!! }
        val missingIds = ids.distinct().filterNot(usersById::containsKey)
        require(missingIds.isEmpty()) { "用户不存在: ${missingIds.joinToString(",")}" }
        return ids.map { toData(usersById.getValue(it)) }
    }

    /**
     * 校验分页参数后返回用户分页数据。
     *
     * 这里同时承担数据范围过滤：Excel 导出用户是靠循环调用本方法捞全量的，范围过滤放在这一层，
     * 接口与导出就都受同一套规则约束，不需要在导出侧再写一遍。
     */
    @Transactional
    override fun list(page: Int, pageSize: Int): UserService.PageData {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val scope = dataScopeResolver.current()
        val pageable = PageRequest.of(page - 1, pageSize)
        // 范围必须下推到 SQL：本方法返回 totalElements，事后过滤会让总数失真，
        // 而导出的 while 循环是照着这个总数捞的，少捞了也不会报错。
        val result = when (scope.kind) {
            ScopeKind.ALL -> userRepository.findAll(pageable)

            ScopeKind.DEPARTMENTS -> if (scope.departmentIds.isEmpty()) {
                Page.empty<User>(pageable)
            } else {
                userRepository.findAllByDepartment_IdIn(scope.departmentIds, pageable)
            }

            // 本人范围只会被普通用户命中，而普通用户本就没有用户管理权限；
            // 这里只返回本人，避免一旦将来放开权限就直接看到全员。
            ScopeKind.SELF -> {
                val self = userRepository.findById(requireNotNull(scope.userId)).orElse(null)
                if (self == null) Page.empty<User>(pageable) else PageImpl(listOf(self), pageable, 1)
            }
        }
        return UserService.PageData(result.content.map(::toData), page, pageSize, result.totalElements)
    }

    /** 将单条更新委托给批量更新路径，保证规则一致。 */
    @Transactional
    override fun update(id: Long, command: UserService.UpdateCommand): UserService.UserData =
        updateBatch(listOf(id), command).single()

    /** 校验更新字段与唯一约束后批量更新用户。 */
    @Transactional
    override fun updateBatch(ids: List<Long>, command: UserService.UpdateCommand): List<UserService.UserData> {
        require(ids.isNotEmpty()) { "用户 ID 列表不能为空" }
        require(ids.distinct().size == ids.size) { "用户 ID 不能重复" }
        require(
            command.username != null || command.email != null || command.credential != null || command.role != null || command.enabled != null ||
                    command.phone != null || command.gender != null || command.departmentId != null || command.positionId != null || command.jobTitle != null ||
                    command.status != null || command.nickName != null || command.roleIds != null
        ) {
            "至少提供一个待更新字段"
        }
        val users = getBatch(ids).map { findUser(it.id) }
        // 手机号是短信登录与重置密码的凭据，管理员改他人手机号必须留下改前/改后；
        // 改密的审计已经是高风险动作，凭据改写不能只有一条「用户资料已更新」。
        val phoneInCommand = command.phone != null
        val beforeById = users.associate {
            it.id!! to buildMap<String, Any?> {
                put("username", it.username)
                put("role", it.role)
                put("enabled", it.enabled)
                put("status", it.status.name)
                if (phoneInCommand) put("phone", it.phone)
            }
        }
        roleAssignmentPolicy.validateManagement(users.map { it.role })
        // 范围校验放在服务层：JSON、表单与批量三个更新入口共用本方法，写在一处就不会出现
        // 「新加的入口忘了加校验」；这里也是手机号唯一的越权改写入口，手机号本身是登录凭据。
        val scope = dataScopeResolver.current()
        users.forEach { scopeGuard.requireUserInScope(it.department?.id, scope) }
        requireActiveSuperAdminRemains(users) { user ->
            val role = command.role?.let(SecurityRole::normalize) ?: user.role
            val enabled = command.enabled ?: user.enabled
            val status = command.status ?: user.status
            role == "SUPER_ADMIN" && enabled && status == User.Status.Activity
        }
        command.username?.let { username ->
            require(username.isNotBlank()) { "用户名不能为空" }
            require(users.all { it.username == username } || !userRepository.existsByUsername(username)) { "用户名已存在" }
        }
        command.email?.let { email ->
            require(email.isNotBlank()) { "邮箱不能为空" }
            require(users.all { it.email == email } || !userRepository.existsByEmail(email)) { "邮箱已存在" }
        }
        command.credential?.let { require(it.isNotBlank()) { "凭据不能为空" } }
        command.role?.let(roleAssignmentPolicy::validateAssignment)
        if (command.credential != null || command.role != null || command.roleIds != null || command.enabled != null || command.status != null) {
            users.forEach { tokenSessionRepository.incrementTokenVersion(it.id!!) }
        }
        val now = LocalDateTime.now()
        users.forEach { user ->
            command.username?.let { user.username = it }
            command.email?.let { user.email = it }
            command.credential?.let { credential -> user.passwordHash = passwordEncoder.encode(credential).toString() }
            command.role?.let { roleName ->
                val normalizedRole = SecurityRole.normalize(roleName)
                user.role = normalizedRole
                if (command.roleIds == null) {
                    val repository = roleRepository ?: throw IllegalStateException("角色仓储不可用")
                    val assignedRole = repository.findByNameIgnoreCase(normalizedRole)
                        ?: throw IllegalArgumentException("角色不存在: $normalizedRole")
                    user.roles = linkedSetOf(assignedRole)
                }
            }
            command.enabled?.let { user.enabled = it }
            command.phone?.let { user.phone = it }
            command.gender?.let { user.gender = it }
            command.departmentId?.let { departmentId ->
                user.department = departmentRepository.findById(departmentId)
                    .orElseThrow { IllegalArgumentException("部门不存在: $departmentId") }
            }
            command.positionId?.let { positionId ->
                user.position = positionRepository.findById(positionId)
                    .orElseThrow { IllegalArgumentException("职位不存在: $positionId") }
            }
            command.jobTitle?.let { jobTitle -> user.jobTitle = jobTitle.trim().takeIf(String::isNotEmpty) }
            command.status?.let { user.status = it }
            command.nickName?.let { user.nickName = it.trim().takeIf(String::isNotEmpty) }
            command.roleIds?.let {
                val assignedRoles = resolveRoles(it)
                user.roles = assignedRoles
                assignedRoles.mapNotNull { role -> SecurityRole.normalizeOrNull(role.name) }.firstOrNull()?.let { role ->
                    roleAssignmentPolicy.validateAssignment(role)
                    user.role = role
                }
            }
            user.updatedAt = now
        }
        val savedUsers = userRepository.saveAll(users)
        savedUsers.forEach { user ->
            val action = when {
                command.role != null || command.roleIds != null -> AuditAction.USER_ROLE_ASSIGNED
                command.enabled != null || command.status != null -> AuditAction.USER_STATUS_CHANGED
                else -> AuditAction.USER_UPDATED
            }
            auditService?.record(
                AuditCommand(
                    action,
                    "user",
                    user.id?.toString(),
                    beforeData = beforeById[user.id],
                    afterData = buildMap<String, Any?> {
                        put("username", user.username)
                        put("role", user.role)
                        put("enabled", user.enabled)
                        put("status", user.status.name)
                        if (phoneInCommand) put("phone", user.phone)
                    },
                    targetSummary = mapOf("username" to user.username),
                ),
            )
        }
        return savedUsers.map(::toData)
    }

    /** 将单条删除委托给批量删除路径。 */
    @Transactional
    override fun delete(id: Long) = deleteBatch(listOf(id))

    /** 查询所有目标用户后批量删除，避免静默忽略不存在的 ID。 */
    @Transactional
    override fun deleteBatch(ids: List<Long>) {
        require(ids.distinct().size == ids.size) { "用户 ID 不能重复" }
        val users = getBatch(ids).map { findUser(it.id) }
        roleAssignmentPolicy.validateManagement(users.map { it.role })
        requireActiveSuperAdminRemains(users) { false }
        users.forEach { tokenSessionRepository.incrementTokenVersion(it.id!!) }
        userRepository.deleteAll(users)
        users.forEach { user ->
            auditService?.record(
                AuditCommand(
                    AuditAction.USER_DELETED,
                    "user",
                    user.id?.toString(),
                    targetSummary = mapOf("username" to user.username, "role" to user.role),
                    result = top.foxball.cartask.entity.AuditEvent.Result.SUCCESS,
                ),
            )
        }
    }

    @Transactional
    override fun existsByUsername(username: String): Boolean = userRepository.existsByUsername(username)

    @Transactional
    override fun findExistingUsernames(usernames: Collection<String>): Set<String> {
        if (usernames.isEmpty()) return emptySet()
        return userRepository.findAllByUsernameIn(usernames).map { it.username }.toSet()
    }

    /** 查找用户；不存在时抛出参数错误。 */
    private fun findUser(id: Long): User = userRepository.findById(id)
        .orElseThrow { IllegalArgumentException("用户不存在: $id") }

    private fun requireActiveSuperAdminRemains(
        users: Collection<User>,
        remainsActiveSuperAdmin: (User) -> Boolean,
    ) {
        val removedCount = users.count { user ->
            user.role == "SUPER_ADMIN" && user.enabled && user.status == User.Status.Activity && !remainsActiveSuperAdmin(user)
        }
        if (removedCount == 0) return
        val activeCount = userRepository.countByRoleAndEnabledTrueAndStatus("SUPER_ADMIN", User.Status.Activity)
        if (activeCount - removedCount < 1) {
            throw AccessDeniedException("不能禁用、降级或删除最后一个启用的超级管理员")
        }
    }

    /** 将实体映射为不暴露密码哈希的服务返回数据。 */
    private fun toData(user: User): UserService.UserData = UserService.UserData(
        id = user.id!!,
        username = user.username,
        name = user.nickName,
        email = user.email,
        role = user.role,
        enabled = user.enabled,
        phone = user.phone,
        gender = user.gender,
        departmentId = user.department?.id,
        positionId = user.position?.id,
        status = user.status,
        roleIds = user.roles.mapNotNull { it.id }.toList(),
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
        jobTitle = user.jobTitle,
    )

    private fun resolveRoles(roleIds: List<Long>?): MutableSet<top.foxball.cartask.entity.Role> {
        if (roleIds == null) return linkedSetOf()
        require(roleIds.distinct().size == roleIds.size) { "角色 ID 不能重复" }
        require(roleIds.all { it > 0 }) { "角色 ID 必须大于 0" }
        val repository = roleRepository ?: throw IllegalStateException("角色仓储不可用")
        val roles = repository.findAllById(roleIds)
        require(roles.size == roleIds.size) { "部分角色不存在" }
        val rolesById = roles.associateBy { requireNotNull(it.id) }
        val orderedRoles = roleIds.map { rolesById.getValue(it) }
        orderedRoles.mapNotNull { SecurityRole.normalizeOrNull(it.name) }
            .forEach(roleAssignmentPolicy::validateAssignment)
        return orderedRoles.toCollection(linkedSetOf())
    }
}
