package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.authentication.SmsVerificationService
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.service.ProfileService
import java.time.LocalDateTime
import java.util.*


@Service
class ProfileServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenSessionRepository: RedisTokenSessionRepository,
    private val smsVerificationService: SmsVerificationService,
    private val auditService: AuditService? = null,
) : ProfileService {
    
    @Transactional
    
    
    override fun get(userId: Long): ProfileService.ProfileData = toData(findUser(userId))
    
    @Transactional
    
    
    override fun update(userId: Long, command: ProfileService.UpdateCommand): ProfileService.ProfileData {
        val user = findUser(userId)
        
        command.name?.let { value ->
            val name = value.trim()
            require(name.isNotEmpty()) { "用户名不能为空" }
            require(name.length <= 64) { "用户名长度不能超过 64 个字符" }
            user.nickName = name
        }
        
        command.email?.let { value ->
            val email = value.trim()
            require(email.isNotEmpty()) { "邮箱不能为空" }
            require(email.length <= 255 && EMAIL_PATTERN.matches(email)) { "邮箱格式无效" }
            require(!userRepository.existsByEmailAndIdNot(email, userId)) { "邮箱已被占用" }
            user.email = email
        }
        
        command.gender?.let { user.gender = it }
        
        user.updatedAt = LocalDateTime.now()
        val saved = userRepository.save(user)
        auditService?.record(
            AuditCommand(
                AuditAction.PROFILE_UPDATED,
                "user",
                userId.toString(),
                targetSummary = mapOf("username" to saved.username),
                afterData = mapOf("name" to saved.nickName, "email" to saved.email, "gender" to saved.gender.name),
            ),
        )
        return toData(saved)
    }
    
    
    @Transactional
    override fun changePhone(userId: Long, command: ProfileService.ChangePhoneCommand): ProfileService.ProfileData {
        val phone = command.phone.trim()
        require(PHONE_PATTERN.matches(phone)) { "手机号格式无效" }
        smsVerificationService.verify(phone, command.code.trim(), SmsVerificationService.Purpose.CHANGE_PHONE)
        require(!userRepository.existsByPhoneAndIdNot(phone, userId)) { "该手机号已被其他账号绑定" }
        
        val user = findUser(userId)
        val previousPhone = user.phone
        user.phone = phone
        user.updatedAt = LocalDateTime.now()
        val saved = userRepository.save(user)
        auditService?.record(
            AuditCommand(
                AuditAction.AUTH_PHONE_CHANGED,
                "user",
                userId.toString(),
                targetSummary = mapOf("username" to saved.username),
                beforeData = mapOf("phone" to previousPhone),
                afterData = mapOf("phone" to saved.phone),
            ),
        )
        return toData(saved)
    }
    
    @Transactional
    
    
    override fun changePassword(userId: Long, command: ProfileService.ChangePasswordCommand) {
        val user = findUser(userId)
        require(passwordEncoder.matches(command.currentPassword, user.passwordHash)) { "原密码不正确" }
        val newPassword = command.newPassword
        require(newPassword.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "新密码长度需为 $MIN_PASSWORD_LENGTH-$MAX_PASSWORD_LENGTH 位"
        }
        require(newPassword.none(Char::isWhitespace)) { "新密码不能包含空白字符" }
        require(!passwordEncoder.matches(newPassword, user.passwordHash)) { "新密码不能与原密码相同" }
        
        user.passwordHash = passwordEncoder.encode(newPassword).toString()
        user.mustChangePassword = false
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        tokenSessionRepository.incrementTokenVersion(userId)
        auditService?.record(
            AuditCommand(
                AuditAction.AUTH_PASSWORD_CHANGED,
                "user",
                userId.toString(),
                targetSummary = mapOf("username" to user.username),
            ),
        )
    }
    
    @Transactional
    
    
    override fun updateAvatar(userId: Long, command: ProfileService.AvatarCommand): ProfileService.ProfileData {
        val user = findUser(userId)
        user.avatar = normalizeAvatar(command.avatar)
        user.updatedAt = LocalDateTime.now()
        val saved = userRepository.save(user)
        auditService?.record(
            AuditCommand(
                AuditAction.PROFILE_UPDATED,
                "user",
                userId.toString(),
                targetSummary = mapOf("username" to saved.username, "avatar_changed" to (saved.avatar != null)),
            ),
        )
        return toData(saved)
    }
    
    @Transactional
    
    
    override fun avatarOf(userId: Long): String? = userRepository.findById(userId).orElse(null)?.avatar
    
    
    private fun normalizeAvatar(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        require(trimmed.length <= MAX_AVATAR_DATA_URL_LENGTH) { "头像过大，请压缩后重试" }
        val match = AVATAR_DATA_URL.matchEntire(trimmed)
            ?: throw IllegalArgumentException("头像格式不受支持，请上传 PNG/JPEG/WebP/GIF 图片")
        val declaredType = match.groupValues[1].lowercase().let { if (it == "jpg") "jpeg" else it }
        val payload = match.groupValues[2]
        val bytes = try {
            Base64.getDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("头像数据不合法")
        }
        require(bytes.size <= MAX_AVATAR_BYTES) { "头像不能超过 ${MAX_AVATAR_BYTES / 1024}KB" }
        require(detectImageType(bytes) == declaredType) { "头像内容与声明的图片格式不一致" }
        return "data:image/$declaredType;base64,$payload"
    }
    
    
    private fun detectImageType(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "png"
        
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpeg"
        bytes.size >= 6 && bytes.decodeToString(0, 3) == "GIF" -> "gif"
        bytes.size >= 12 && bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WEBP" -> "webp"
        else -> null
    }
    
    
    private fun findUser(userId: Long): User = userRepository.findById(userId)
        .orElseThrow { IllegalArgumentException("用户不存在") }
    
    
    private fun toData(user: User): ProfileService.ProfileData = ProfileService.ProfileData(
        userId = requireNotNull(user.id) { "用户 ID 缺失" },
        username = user.username,
        name = user.nickName,
        phone = user.phone,
        email = user.email,
        gender = user.gender,
        role = SecurityRole.normalizeOrNull(user.role) ?: user.role,
        roleName = ROLE_NAMES[SecurityRole.normalizeOrNull(user.role)] ?: user.role,
        departmentName = user.department?.name,
        avatar = user.avatar,
        mustChangePassword = user.mustChangePassword,
        createdAt = user.createdAt,
    )
    
    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 128
        
        
        const val MAX_AVATAR_BYTES = 512 * 1024
        
        
        const val MAX_AVATAR_DATA_URL_LENGTH = 700 * 1024
        
        val PHONE_PATTERN = Regex("^\\+?[0-9]{6,20}$")
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val AVATAR_DATA_URL = Regex("^data:image/(png|jpeg|jpg|webp|gif);base64,([A-Za-z0-9+/]+={0,2})$")
        
        val ROLE_NAMES = mapOf(
            "SUPER_ADMIN" to "超级管理员",
            "ADMIN" to "管理员",
            "USER" to "普通用户",
        )
    }
}
