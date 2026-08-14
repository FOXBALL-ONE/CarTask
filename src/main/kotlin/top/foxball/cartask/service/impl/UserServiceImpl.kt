package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.service.UserService
import java.time.LocalDateTime

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserService {

    @Transactional
    override fun create(command: UserService.CreateCommand): UserService.UserData =
        createBatch(listOf(command)).single()

    @Transactional
    override fun createBatch(commands: List<UserService.CreateCommand>): List<UserService.UserData> {
        require(commands.isNotEmpty()) { "用户列表不能为空" }
        require(commands.map { it.username }.distinct().size == commands.size) { "批量创建的用户名不能重复" }
        require(commands.map { it.email }.distinct().size == commands.size) { "批量创建的邮箱不能重复" }
        commands.forEach { command ->
            require(command.username.isNotBlank()) { "用户名不能为空" }
            require(command.email.isNotBlank()) { "邮箱不能为空" }
            require(command.credential.isNotBlank()) { "凭据不能为空" }
            require(!userRepository.existsByUsername(command.username)) { "用户名已存在" }
            require(!userRepository.existsByEmail(command.email)) { "邮箱已存在" }
        }
        val now = LocalDateTime.now()
        return userRepository.saveAll(commands.map { command ->
            User().apply {
                username = command.username
                email = command.email
                passwordHash = passwordEncoder.encode(command.credential).toString()
                role = command.role
                enabled = command.enabled
                createdAt = now
                updatedAt = now
            }
        }).map(::toData)
    }

    @Transactional
    override fun get(id: Long): UserService.UserData = toData(findUser(id))

    @Transactional
    override fun getBatch(ids: List<Long>): List<UserService.UserData> {
        require(ids.isNotEmpty()) { "用户 ID 列表不能为空" }
        val usersById = userRepository.findAllById(ids.distinct()).associateBy { it.id!! }
        val missingIds = ids.distinct().filterNot(usersById::containsKey)
        require(missingIds.isEmpty()) { "用户不存在: ${missingIds.joinToString(",")}" }
        return ids.map { toData(usersById.getValue(it)) }
    }

    @Transactional
    override fun list(page: Int, pageSize: Int): UserService.PageData {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val result = userRepository.findAll(PageRequest.of(page - 1, pageSize))
        return UserService.PageData(result.content.map(::toData), page, pageSize, result.totalElements)
    }

    @Transactional
    override fun update(id: Long, command: UserService.UpdateCommand): UserService.UserData =
        updateBatch(listOf(id), command).single()

    @Transactional
    override fun updateBatch(ids: List<Long>, command: UserService.UpdateCommand): List<UserService.UserData> {
        require(ids.isNotEmpty()) { "用户 ID 列表不能为空" }
        require(ids.distinct().size == ids.size) { "用户 ID 不能重复" }
        require(command.username != null || command.email != null || command.credential != null || command.role != null || command.enabled != null) {
            "至少提供一个待更新字段"
        }
        val users = getBatch(ids).map { findUser(it.id) }
        command.username?.let { username ->
            require(username.isNotBlank()) { "用户名不能为空" }
            require(users.all { it.username == username } || !userRepository.existsByUsername(username)) { "用户名已存在" }
        }
        command.email?.let { email ->
            require(email.isNotBlank()) { "邮箱不能为空" }
            require(users.all { it.email == email } || !userRepository.existsByEmail(email)) { "邮箱已存在" }
        }
        command.credential?.let { require(it.isNotBlank()) { "凭据不能为空" } }
        val now = LocalDateTime.now()
        users.forEach { user ->
            command.username?.let { user.username = it }
            command.email?.let { user.email = it }
            command.credential?.let { credential -> user.passwordHash = passwordEncoder.encode(credential).toString() }
            command.role?.let { user.role = it }
            command.enabled?.let { user.enabled = it }
            user.updatedAt = now
        }
        return userRepository.saveAll(users).map(::toData)
    }

    @Transactional
    override fun delete(id: Long) = deleteBatch(listOf(id))

    @Transactional
    override fun deleteBatch(ids: List<Long>) {
        require(ids.distinct().size == ids.size) { "用户 ID 不能重复" }
        val users = getBatch(ids).map { findUser(it.id) }
        userRepository.deleteAll(users)
    }

    private fun findUser(id: Long): User = userRepository.findById(id)
        .orElseThrow { IllegalArgumentException("用户不存在: $id") }

    private fun toData(user: User): UserService.UserData = UserService.UserData(
        id = user.id!!,
        username = user.username,
        email = user.email,
        role = user.role,
        enabled = user.enabled,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )
}
