package top.foxball.cartask.authentication

/**
 * SmsVerificationService：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * SmsVerificationService 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.slf4j.LoggerFactory
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
/**
 * SmsVerificationService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class SmsVerificationService(
    private val redisTemplate: StringRedisTemplate,
    private val smsClient: SmsClient,
    private val properties: SmsProperties,
) {
    private val random = SecureRandom()
    private val logger = LoggerFactory.getLogger(javaClass)
    
    
    val verificationSkipped: Boolean get() = properties.skipVerification
    
    init {
        if (properties.skipVerification) {
            logger.warn(
                "短信验证已临时关闭（cartask.sms.skip-verification=true）：短信登录、重置密码与换绑手机号都不再校验验证码，恢复请改为 false",
            )
        }
    }
    
    
    /**
     * send 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** send：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun send(phone: String, purpose: Purpose) {
        if (properties.skipVerification) {
            logger.warn("短信验证已临时关闭，跳过发送验证码：purpose={} phone={}", purpose, phone)
            return
        }
        val normalized = normalize(phone)
        try {
            val codeKey = key(normalized, purpose)
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
    
    
    /**
     * verify 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** verify：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun verify(phone: String, code: String, purpose: Purpose) {
        if (properties.skipVerification) {
            logger.warn("短信验证已临时关闭，跳过验证码校验：purpose={} phone={}", purpose, phone)
            return
        }
        val key = key(normalize(phone), purpose)
        val stored = redisTemplate.opsForValue().getAndDelete(key)
        if (stored == null || !MessageDigest.isEqual(stored.toByteArray(), hash(code).toByteArray())) {
            throw VerificationCodeInvalidException()
        }
    }
    
    
    /** normalize：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun normalize(phone: String): String {
        val value = phone.trim()
        require(Regex("^\\+?[0-9]{6,20}$").matches(value)) { "手机号格式无效" }
        return value
    }
    
    
    /** hash：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    
    
    /** key：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun key(phone: String, purpose: Purpose) = "shopmall:auth:sms:${purpose.name.lowercase()}:$phone"
    
    
    /** cooldownKey：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun cooldownKey(phone: String, purpose: Purpose) =
        "shopmall:auth:sms:cooldown:${purpose.name.lowercase()}:$phone"
    
    enum class Purpose {
        LOGIN,
        RESET_PASSWORD,
        
        
        CHANGE_PHONE,
    }
    
    companion object {
        
        val CODE_TTL: Duration = Duration.ofMinutes(5)
        
        
        val SEND_INTERVAL: Duration = Duration.ofSeconds(60)
    }
}


