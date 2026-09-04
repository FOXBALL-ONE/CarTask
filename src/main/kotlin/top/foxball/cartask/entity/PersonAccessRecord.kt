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
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "person_access_record")
class PersonAccessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 128)
    lateinit var person: String

    @Column(name = "card_id", length = 64)
    var cardId: String? = null

    @Column(length = 128)
    var dept: String? = null

    @Column(nullable = false)
    lateinit var time: LocalDateTime

    @Column(nullable = false, length = 8)
    lateinit var direction: String

    @Column(length = 128)
    var gate: String? = null

    @Column(length = 64)
    var method: String? = null

    @Column(nullable = false, length = 16)
    var status: String = "正常"

    @Column(length = 1024)
    var photo: String? = null
}
