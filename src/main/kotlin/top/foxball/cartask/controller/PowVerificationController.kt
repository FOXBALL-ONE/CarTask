package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.authentication.PowVerificationService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/verification/pow")
class PowVerificationController(
    private val powVerificationService: PowVerificationService,
    private val responseBuilder: ResponseBuilder,
) {
    data class ChallengeRequest(
        @param:JsonProperty("purpose") val purpose: String,
        @param:JsonProperty("mode") val mode: String? = null,
    )

    data class ProofRequest(
        @param:JsonProperty("challenge_id") val challengeId: String,
        @param:JsonProperty("purpose") val purpose: String,
        @param:JsonProperty("nonce") val nonce: String,
    )

    @PostMapping("/challenges")
    fun challenge(@RequestBody request: ChallengeRequest): ResponseEntity<Response> {
        val challenge = powVerificationService.issue(request.purpose)
        data class ChallengeResponse(
            @param:JsonProperty("challenge_id") val challengeId: String,
            val purpose: String,
            val seed: String,
            @param:JsonProperty("difficulty_bits") val difficultyBits: Int,
            @param:JsonProperty("expires_at") val expiresAt: java.time.LocalDateTime,
            val algorithm: String,
            @param:JsonProperty("protocol_version") val protocolVersion: Int,
        )
        val rs = ChallengeResponse(
            challenge.challengeId,
            challenge.purpose,
            challenge.seed,
            challenge.difficultyBits,
            challenge.expiresAt,
            challenge.algorithm,
            challenge.protocolVersion,
        )
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }

    @PostMapping("/verify")
    fun verify(@RequestBody request: ProofRequest): ResponseEntity<Response> {
        powVerificationService.verify(request.challengeId, request.purpose, request.nonce)
        data class VerifyResponse(@param:JsonProperty("verified") val verified: Boolean)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(VerifyResponse(true))
            .build()
    }
}
