package top.foxball.cartask.entity

import jakarta.persistence.*


@Entity
@Table(name = "position")
class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, length = 128)
    lateinit var name: String
    
    
    @Column(name = "position_code", nullable = false, unique = true, length = 64)
    lateinit var codeNumber: String
    
    
    @Column(name = "sort_order", nullable = false)
    var orderNumber: Int = 0
    
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity
    
    @Column(length = 512)
    var remark: String? = null
    
    enum class Status {
        Activity,
        BANNED,
    }
}
