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
import java.util.Base64

/** 个人中心服务实现；资料、头像、密码与手机号均只作用于当前登录用户自身。 */
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

        // 邮箱在库中非空且唯一，因此只允许改成另一个合法邮箱，不允许清空。
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

    /**
     * 换绑手机号。
     *
     * 顺序是「先验码、再查重」：反过来的话，拿到一个普通会话就能拿任意号码去试探「这个号是否已被
     * 注册」，而验码要求先掌握该号码，探测成本直接变成「必须持有该手机卡」。
     * 手机号同时是短信登录与重置密码的凭据，所以查重不能省：重复绑定会让 findByPhone 直接抛错，
     * 两个账号谁都登不上。
     */
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
        // 原密码错误属于参数问题而非登录态失效，用 IllegalArgumentException 转 400，
        // 不能抛 BadCredentialsException——那会返回 401 并被前端当作会话过期而强制登出。
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
        // 改密后撤销该用户全部历史会话，既踢掉旧设备，也让会话里的「必须改密」标记随之失效。
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

    /**
     * 校验头像 data URL：只接受内联图片、限制体积，并核对文件头与声明的媒体类型一致，
     * 避免把非图片内容存进用户资料再被原样渲染出去。
     */
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

    /** 按文件头识别真实图片类型，识别不出时返回 null。 */
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

        /** 解码后的头像上限；前端会先压到 256×256，正常只有几十 KB。 */
        const val MAX_AVATAR_BYTES = 512 * 1024

        /** 先按字符串长度拦一道，避免对超大请求体做 Base64 解码。 */
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
