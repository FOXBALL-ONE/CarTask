package top.foxball.cartask.authentication

/**
 * JwtTokenService：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * JwtTokenService 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


@Service
/**
 * JwtTokenService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class JwtTokenService(
    private val properties: JwtProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    private val signingKeys: Map<String, SecretKey>
    private val decoders: Map<String, JwtDecoder>
    private val encoder: JwtEncoder
    private val storageEncryptionKey: SecretKey
    
    init {
        require(properties.issuer.isNotBlank()) { "JWT issuer 不能为空" }
        require(properties.audience.isNotBlank()) { "JWT audience 不能为空" }
        require(properties.activeSigningKeyId.isNotBlank()) { "JWT active signing key ID 不能为空" }
        require(properties.ttl.isPositive) { "JWT ttl 必须大于 0" }
        require(!properties.clockSkew.isNegative) { "JWT clock skew 不能为负数" }
        signingKeys = properties.keys.mapValues { (_, encoded) -> hmacKey(encoded) }
        require(signingKeys.containsKey(properties.activeSigningKeyId)) { "JWT active signing key 不存在" }
        storageEncryptionKey = aesKey(properties.tokenStorageEncryptionKey)
        require(properties.tokenStorageEncryptionKeyId.isNotBlank()) { "JWT token storage encryption key ID 不能为空" }
        
        val activeJwk = OctetSequenceKey.Builder(signingKeys.getValue(properties.activeSigningKeyId).encoded)
            .keyID(properties.activeSigningKeyId)
            .algorithm(JWSAlgorithm.HS256)
            .build()
        encoder = NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(activeJwk)))
        decoders = signingKeys.mapValues { (_, key) -> decoderFor(key) }
    }
    
    /**
     * IssuedToken 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class IssuedToken(
        val accessToken: AccessTokenValue,
        val tokenId: String,
        val issuedAt: LocalDateTime,
        val expiresAt: LocalDateTime,
        val tokenHash: String,
        val tokenCiphertext: String,
        val tokenEncryptionKeyId: String,
    )
    
    /**
     * VerifiedToken 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class VerifiedToken(
        val tokenId: String,
        val userId: Long,
        val username: String,
        val role: String,
        val tokenVersion: Long,
        val tokenHash: String,
    )
    
    
    /**
     * issue 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** issue：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun issue(userId: Long, username: String, role: String, tokenVersion: Long): IssuedToken {
        val normalizedRole = SecurityRole.normalize(role)
        val now = Instant.now(clock)
        val expiration = now.plus(properties.ttl)
        val tokenId = UUID.randomUUID().toString()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .audience(listOf(properties.audience))
            .subject(userId.toString())
            .id(tokenId)
            .issuedAt(now)
            .expiresAt(expiration)
            .claim("username", username)
            .claim("role", normalizedRole)
            .claim("token_version", tokenVersion)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).keyId(properties.activeSigningKeyId).build()
        val jwtValue = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedToken(
            accessToken = jwtValue,
            tokenId = tokenId,
            issuedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC),
            expiresAt = LocalDateTime.ofInstant(expiration, ZoneOffset.UTC),
            tokenHash = fingerprint(jwtValue),
            tokenCiphertext = encryptForStorage(jwtValue, tokenId),
            tokenEncryptionKeyId = properties.tokenStorageEncryptionKeyId,
        )
    }
    
    
    /**
     * verify 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** verify：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun verify(jwtValue: AccessTokenValue): VerifiedToken {
        val keyId = extractKeyId(jwtValue)
        val jwt = try {
            decoders[keyId]?.decode(jwtValue) ?: throw JwtAuthenticationException("JWT kid 不受支持")
        } catch (ex: JwtAuthenticationException) {
            throw ex
        } catch (ex: Exception) {
            throw JwtAuthenticationException("JWT 格式或签名无效", ex)
        }
        val userId = jwt.subject?.toLongOrNull() ?: throw JwtAuthenticationException("JWT subject 无效")
        val issuedAt = jwt.issuedAt ?: throw JwtAuthenticationException("JWT iat 缺失")
        if (issuedAt.isAfter(Instant.now(clock).plus(properties.clockSkew))) {
            throw JwtAuthenticationException("JWT iat 无效")
        }
        val tokenId = jwt.id?.takeIf(StringUtils::hasText) ?: throw JwtAuthenticationException("JWT jti 缺失")
        val username = jwt.getClaimAsString("username")?.takeIf(StringUtils::hasText)
            ?: throw JwtAuthenticationException("JWT username 缺失")
        val roleClaim = jwt.getClaimAsString("role")?.takeIf(StringUtils::hasText)
            ?: throw JwtAuthenticationException("JWT role 缺失")
        val role = SecurityRole.normalizeOrNull(roleClaim)
            ?: throw JwtAuthenticationException("JWT role 无效")
        val tokenVersion = (jwt.claims["token_version"] as? Number)?.toLong()
            ?: throw JwtAuthenticationException("JWT token version 缺失")
        return VerifiedToken(tokenId, userId, username, role, tokenVersion, fingerprint(jwtValue))
    }
    
    
    /** decoderFor：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun decoderFor(key: SecretKey): JwtDecoder = NimbusJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()
        .also { decoder ->
            val timestampValidator = JwtTimestampValidator(properties.clockSkew).apply { setClock(clock) }
            val audienceValidator = OAuth2TokenValidator<Jwt> { jwt ->
                if (jwt.audience?.contains(properties.audience) == true) OAuth2TokenValidatorResult.success()
                else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "JWT audience 无效", null))
            }
            decoder.setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    timestampValidator,
                    JwtIssuerValidator(properties.issuer),
                    audienceValidator,
                ),
            )
        }
    
    
    /** extractKeyId：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun extractKeyId(jwtValue: AccessTokenValue): String = try {
        val parts = jwtValue.split('.')
        if (parts.size != 3) throw JwtAuthenticationException("JWT 格式无效")
        val headerText = String(
            Base64.getUrlDecoder().decode(parts[0]),
            StandardCharsets.UTF_8,
        )
        val header = objectMapper.readTree(headerText)
        if (header.path("alg").asString() != "HS256") throw JwtAuthenticationException("JWT 算法不受支持")
        header.path("kid").asString().takeIf { it.isNotBlank() } ?: throw JwtAuthenticationException("JWT kid 缺失")
    } catch (ex: JwtAuthenticationException) {
        throw ex
    } catch (ex: Exception) {
        throw JwtAuthenticationException("JWT header 无效", ex)
    }
    
    
    /** hmacKey：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun hmacKey(encoded: String): SecretKey {
        val bytes = decodeBase64(encoded, "JWT signing key")
        require(bytes.size >= 32) { "JWT signing key 至少需要 256 位" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }
    
    
    /** aesKey：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun aesKey(encoded: String): SecretKey {
        val bytes = decodeBase64(encoded, "JWT storage encryption key")
        require(bytes.size == 32) { "JWT storage encryption key 必须为 256 位" }
        return SecretKeySpec(bytes, "AES")
    }
    
    
    /** decodeBase64：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun decodeBase64(value: String, name: String): ByteArray {
        require(value.isNotBlank()) { "$name 不能为空" }
        return try {
            Base64.getDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("$name 必须为 Base64", ex)
        }
    }
    
    
    /** encryptForStorage：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun encryptForStorage(jwtValue: AccessTokenValue, tokenId: String): String = try {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, storageEncryptionKey, GCMParameterSpec(128, nonce))
        cipher.updateAAD("shopmall:auth:jwt:$tokenId".toByteArray(StandardCharsets.UTF_8))
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(nonce + cipher.doFinal(jwtValue.toByteArray(StandardCharsets.UTF_8)))
    } catch (ex: Exception) {
        throw AuthenticationInfrastructureException("JWT Redis 副本加密失败", ex)
    }
    
    
    /** fingerprint：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun fingerprint(jwtValue: AccessTokenValue): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(jwtValue.toByteArray(StandardCharsets.UTF_8)))
}


