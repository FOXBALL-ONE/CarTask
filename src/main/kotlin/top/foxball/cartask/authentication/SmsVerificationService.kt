package top.foxball.cartask.authentication

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import top.foxball.cartask.handler.EmailSendFailedException
import top.foxball.cartask.handler.VerificationCodeInvalidException
import top.foxball.cartask.handler.VerificationCodeRateLimitException
import top.foxball.cartask.sms.SmsClient
import top.foxball.cartask.sms.SmsProperties
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration

@Service
class SmsVerificationService(
    private val redisTemplate: StringRedisTemplate,
    private val smsClient: SmsClient,
    private val properties: SmsProperties,
) {
    private val random = SecureRandom()

    fun send(phone: String, purpose: Purpose) {
        val normalized = normalize(phone)
        try {
            val codeKey = key(normalized, purpose)
            // 重发间隔与验证码有效期分开计数，否则「5 分钟有效」会被误当成「5 分钟才能重发」。
            val cooldownKey = cooldownKey(normalized, purpose)
            if (redisTemplate.hasKey(cooldownKey) == true) throw VerificationCodeRateLimitException()
            val code = (100000 + random.nextInt(900000)).toString()
            redisTemplate.opsForValue().set(codeKey, hash(code), CODE_TTL)
            redisTemplate.opsForValue().set(cooldownKey, "1", SEND_INTERVAL)
            try {
                smsClient.sendVerificationCode(normalized, code)
            } catch (ex: Exception) {
                redisTemplate.delete(codeKey)
                redisTemplate.delete(cooldownKey)
                throw EmailSendFailedException("短信发送失败，请稍后重试")
            }
        } catch (ex: VerificationCodeRateLimitException) {
            throw ex
        } catch (ex: DataAccessException) {
            throw EmailSendFailedException("短信服务暂不可用，请稍后重试")
        }
    }

    fun verify(phone: String, code: String, purpose: Purpose) {
        val key = key(normalize(phone), purpose)
        val stored = redisTemplate.opsForValue().getAndDelete(key)
        if (stored == null || !MessageDigest.isEqual(stored.toByteArray(), hash(code).toByteArray())) {
            throw VerificationCodeInvalidException()
        }
    }

    private fun normalize(phone: String): String {
        val value = phone.trim()
        require(Regex("^\\+?[0-9]{6,20}$").matches(value)) { "手机号格式无效" }
        return value
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun key(phone: String, purpose: Purpose) = "shopmall:auth:sms:${purpose.name.lowercase()}:$phone"

    private fun cooldownKey(phone: String, purpose: Purpose) = "shopmall:auth:sms:cooldown:${purpose.name.lowercase()}:$phone"

    enum class Purpose { LOGIN, RESET_PASSWORD }

    companion object {
        /** 验证码有效期。 */
        val CODE_TTL: Duration = Duration.ofMinutes(5)

        /** 同一手机号同一用途的最小重发间隔，与登录页倒计时（login.vue 的 SMS_RESEND_SECONDS）一致。 */
        val SEND_INTERVAL: Duration = Duration.ofSeconds(60)
    }
}
