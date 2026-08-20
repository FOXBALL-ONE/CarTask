package top.foxball.cartask.authentication

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
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
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
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** JWT 的签发、严格验签及 Redis token 密文副本加密。 */
@Service
class JwtTokenService(
    private val properties: JwtProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    private val signingKeys: Map<kotlin.String, SecretKey>
    private val decoders: Map<kotlin.String, JwtDecoder>
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
    
    data class IssuedToken(
        val accessToken: AccessTokenValue,
        val tokenId: kotlin.String,
        val issuedAt: LocalDateTime,
        val expiresAt: LocalDateTime,
        val tokenHash: kotlin.String,
        val tokenCiphertext: kotlin.String,
        val tokenEncryptionKeyId: kotlin.String,
    )
    
    data class VerifiedToken(
        val tokenId: kotlin.String,
        val userId: Long,
        val username: kotlin.String,
        val role: kotlin.String,
        val tokenVersion: Long,
        val tokenHash: kotlin.String,
    )
    
    fun issue(userId: Long, username: kotlin.String, role: kotlin.String, tokenVersion: Long): IssuedToken {
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
    
    private fun extractKeyId(jwtValue: AccessTokenValue): kotlin.String = try {
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
    
    private fun hmacKey(encoded: kotlin.String): SecretKey {
        val bytes = decodeBase64(encoded, "JWT signing key")
        require(bytes.size >= 32) { "JWT signing key 至少需要 256 位" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }
    
    private fun aesKey(encoded: kotlin.String): SecretKey {
        val bytes = decodeBase64(encoded, "JWT storage encryption key")
        require(bytes.size == 32) { "JWT storage encryption key 必须为 256 位" }
        return SecretKeySpec(bytes, "AES")
    }
    
    private fun decodeBase64(value: kotlin.String, name: kotlin.String): ByteArray {
        require(value.isNotBlank()) { "$name 不能为空" }
        return try {
            Base64.getDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("$name 必须为 Base64", ex)
        }
    }
    
    private fun encryptForStorage(jwtValue: AccessTokenValue, tokenId: kotlin.String): kotlin.String = try {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, storageEncryptionKey, GCMParameterSpec(128, nonce))
        cipher.updateAAD("shopmall:auth:jwt:$tokenId".toByteArray(StandardCharsets.UTF_8))
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(nonce + cipher.doFinal(jwtValue.toByteArray(StandardCharsets.UTF_8)))
    } catch (ex: Exception) {
        throw AuthenticationInfrastructureException("JWT Redis 副本加密失败", ex)
    }
    
    private fun fingerprint(jwtValue: AccessTokenValue): kotlin.String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(jwtValue.toByteArray(StandardCharsets.UTF_8)))
}
