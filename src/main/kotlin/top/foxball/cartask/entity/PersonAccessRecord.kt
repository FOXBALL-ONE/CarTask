package top.foxball.cartask.entity

import jakarta.persistence.*
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
    
    
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null
    
    
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null
    
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
