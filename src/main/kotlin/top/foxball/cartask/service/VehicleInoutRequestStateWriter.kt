package top.foxball.cartask.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.entity.VehicleInoutRequest
import top.foxball.cartask.repository.VehicleInoutRequestRepository
import java.time.LocalDateTime


@Service
class VehicleInoutRequestStateWriter(
    private val repository: VehicleInoutRequestRepository,
    private val auditService: AuditService,
) {
    
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markCardIssued(id: Long, cardId: Long? = null): Boolean {
        val request = repository.findById(id).orElse(null) ?: return false
        if (request.cardIssued && (cardId == null || request.cardId == cardId)) return true
        request.cardIssued = true
        if (cardId != null) request.cardId = cardId
        repository.save(request)
        return true
    }
    
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordOutcome(
        id: Long,
        synced: Boolean,
        message: String?,
        cardId: Long?,
        cardIssued: Boolean,
        occurredAt: LocalDateTime,
    ) {
        val request = repository.findById(id).orElse(null) ?: return
        if (cardId != null) request.cardId = cardId
        request.cardIssued = request.cardIssued || cardIssued
        if (synced) {
            request.syncStatus = VehicleInoutRequest.SyncStatus.SYNCED
            request.syncMessage = null
            request.syncedAt = occurredAt
        } else {
            request.syncStatus = VehicleInoutRequest.SyncStatus.FAILED
            request.syncMessage = message
        }
        repository.save(request)
    }
    
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordOutcomeAudit(id: Long, synced: Boolean, message: String?) {
        val request = repository.findById(id).orElse(null) ?: return
        auditService.record(
            AuditCommand(
                action = AuditAction.VEHICLE_INOUT_REQUEST_SYNCED,
                targetType = TARGET_TYPE,
                targetId = id.toString(),
                result = if (synced) AuditEvent.Result.SUCCESS else AuditEvent.Result.FAILED,
                reasonCode = if (synced) null else "KEYTOP_CALL_FAILED",
                reason = if (synced) null else message,
                targetSummary = mapOf(
                    "plate" to request.plate,
                    "owner" to request.owner,
                    "department_code" to request.departmentCode,
                    "monthly_card" to request.cardName,
                    "card_id" to request.cardId,
                ),
                afterData = if (synced) {
                    mapOf("synchronized" to true, "valid_from" to request.validFrom, "valid_to" to request.validTo)
                } else {
                    mapOf("synchronized" to false, "sync_message" to message)
                },
            ),
        )
    }
    
    private companion object {
        const val TARGET_TYPE = "vehicle_inout_request"
        val logger = LoggerFactory.getLogger(VehicleInoutRequestStateWriter::class.java)
    }
}
