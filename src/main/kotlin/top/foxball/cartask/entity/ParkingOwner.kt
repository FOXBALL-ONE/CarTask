package top.foxball.cartask.entity

import jakarta.persistence.*
import top.foxball.cartask.scope.DepartmentScoped
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "parking_owner",
    indexes = [
        Index(name = "idx_parking_owner_phone", columnList = "phone"),
        Index(name = "idx_parking_owner_linked_user", columnList = "linked_user_id"),
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_parking_owner_card_id", columnNames = ["card_id"])],
)
class ParkingOwner : DepartmentScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(name = "card_id", nullable = false, length = 64)
    lateinit var cardId: String
    
    @Column(nullable = false, length = 128)
    lateinit var name: String
    
    @Column(nullable = false, length = 128)
    lateinit var dept: String
    
    @Column(nullable = false, length = 32)
    lateinit var phone: String
    
    
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null
    
    
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null
    
    @Column(nullable = false)
    var spotCount: Int = 0
    
    @Column(nullable = false)
    var plateCount: Int = 0
    
    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO
    
    @Column(nullable = false)
    var status: Int = 1
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    override val scopeDeptFreeText: String? get() = dept
    
    override val scopeDeptCode: String? get() = departmentCode
}
