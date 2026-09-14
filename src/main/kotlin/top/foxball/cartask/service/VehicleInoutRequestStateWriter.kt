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

/**
 * 申请单下发状态的独立写入器。
 *
 * 存在的理由是事务边界：下发会真实写科拓车场，而车场里的月卡撤不掉，所以「已经发过卡」这件事
 * 必须比任何一次 HTTP 调用活得久。把它跟外部调用放在同一个事务里，审计或数据库任一处抛错都会把
 * 这条标记一起回滚，重试就会再发一张卡——见 [VehicleInoutRequestService.synchronize] 的调用方。
 *
 * 因此每一次状态写入都用 [Propagation.REQUIRES_NEW] 单独提交，与调用方的事务互不牵连。
 * 单独一个 Bean 而不是同类私有方法：自调用不经过代理，注解不会生效。
 */
@Service
class VehicleInoutRequestStateWriter(
    private val repository: VehicleInoutRequestRepository,
    private val auditService: AuditService,
) {
    /**
     * 记下「科拓已受理新增月卡」，并在拿到卡号时一并落库。
     *
     * 在 `AddCarCardNo` 返回成功后立刻调用：此后的任何时候本地都还读不到卡号，
     * 若不能区分「没有卡」与「有卡但没读回卡号」，重试就会再新增一张。
     * [cardId] 随后读到就再写一次，写失败也不回退 [VehicleInoutRequest.cardIssued]。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markCardIssued(id: Long, cardId: Long? = null): Boolean {
        val request = repository.findById(id).orElse(null) ?: return false
        if (request.cardIssued && (cardId == null || request.cardId == cardId)) return true
        request.cardIssued = true
        if (cardId != null) request.cardId = cardId
        repository.save(request)
        return true
    }

    /**
     * 写下发结论。失败也写：一次网络超时不能让审批结论看起来像没发生过。
     *
     * **不写审计**：审计与状态必须落在两个事务里。它们同处一个事务时，审计插入失败会把
     * 「已经发过卡」这条事实一起回滚，重试就再发一张卡——而车场里的卡撤不掉。
     */
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

    /**
     * 写下发审计。
     *
     * 独立事务：审计失败只回滚审计，不能把已经在车场生效的下发结论一起抹掉。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordOutcomeAudit(id: Long, synced: Boolean, message: String?) {
        val request = repository.findById(id).orElse(null) ?: return
        auditService.record(
            AuditCommand(
                action = AuditAction.VEHICLE_INOUT_REQUEST_SYNCED,
                targetType = TARGET_TYPE,
                targetId = id.toString(),
                // 下发失败是可重试的外部故障，不是被拒绝的授权，所以用 FAILED 而不是 DENIED。
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
