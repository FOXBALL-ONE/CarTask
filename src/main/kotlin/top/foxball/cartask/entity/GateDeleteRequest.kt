package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "gate_delete_request")
class GateDeleteRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var personId: Long = 0

    @Column(nullable = false, length = 64)
    lateinit var code: String

    @Column(nullable = false, length = 128)
    lateinit var dept: String

    @Column(nullable = false, length = 128)
    lateinit var name: String

    @Column(nullable = false, length = 32)
    lateinit var phone: String

    @Column(name = "id_card", nullable = false, length = 32)
    lateinit var idCard: String

    @Column(length = 1024)
    var face: String? = null

    @Column(nullable = false, length = 512)
    lateinit var reason: String

    @Column(nullable = false)
    lateinit var applyTime: LocalDateTime

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.PENDING

    enum class Status {
        PENDING, APPROVED, REJECTED;
        @JsonValue fun value(): String = when (this) { PENDING -> "待处理"; APPROVED -> "已同意"; REJECTED -> "已拒绝" }
    }
}
