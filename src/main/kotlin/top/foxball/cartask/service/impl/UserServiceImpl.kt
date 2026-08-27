package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
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
    private val auditService: AuditService? = null,
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
            User().apply {
                username = command.username
                nickName = command.nickName?.trim()?.takeIf(String::isNotEmpty)
                email = command.email
                passwordHash = passwordEncoder.encode(command.credential).toString()
                role = SecurityRole.normalize(command.role)
                enabled = command.enabled
                phone = command.phone
                gender = command.gender
                department = command.departmentId?.let { departmentId ->
                    departmentRepository.findById(departmentId)
                        .orElseThrow { IllegalArgumentException("部门不存在: $departmentId") }
                }
                position = command.positionId?.let { positionId ->
                    positionRepository.findById(positionId)
                        .orElseThrow { IllegalArgumentException("职位不存在: $positionId") }
                }
                status = command.status
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

    /** 校验分页参数后返回用户分页数据。 */
    @Transactional
    override fun list(page: Int, pageSize: Int): UserService.PageData {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val result = userRepository.findAll(PageRequest.of(page - 1, pageSize))
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
                    command.phone != null || command.gender != null || command.departmentId != null || command.positionId != null ||
                    command.status != null || command.nickName != null
        ) {
            "至少提供一个待更新字段"
        }
        val users = getBatch(ids).map { findUser(it.id) }
        val beforeById = users.associate { it.id!! to mapOf("username" to it.username, "role" to it.role, "enabled" to it.enabled, "status" to it.status.name) }
        roleAssignmentPolicy.validateManagement(users.map { it.role })
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
        if (command.credential != null || command.role != null || command.enabled != null || command.status != null) {
            users.forEach { tokenSessionRepository.incrementTokenVersion(it.id!!) }
        }
        val now = LocalDateTime.now()
        users.forEach { user ->
            command.username?.let { user.username = it }
            command.email?.let { user.email = it }
            command.credential?.let { credential -> user.passwordHash = passwordEncoder.encode(credential).toString() }
            command.role?.let { user.role = SecurityRole.normalize(it) }
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
            command.status?.let { user.status = it }
            command.nickName?.let { user.nickName = it.trim().takeIf(String::isNotEmpty) }
            user.updatedAt = now
        }
        val savedUsers = userRepository.saveAll(users)
        savedUsers.forEach { user ->
            val action = when {
                command.role != null -> AuditAction.USER_ROLE_ASSIGNED
                command.enabled != null || command.status != null -> AuditAction.USER_STATUS_CHANGED
                else -> AuditAction.USER_UPDATED
            }
            auditService?.record(
                AuditCommand(
                    action,
                    "user",
                    user.id?.toString(),
                    beforeData = beforeById[user.id],
                    afterData = mapOf("username" to user.username, "role" to user.role, "enabled" to user.enabled, "status" to user.status.name),
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
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )
}
