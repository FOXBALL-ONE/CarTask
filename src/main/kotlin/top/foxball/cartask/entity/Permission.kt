package top.foxball.cartask.entity

import jakarta.persistence.*


@Entity
@Table(name = "permissions")
class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, unique = true, length = 128)
    lateinit var code: String
    
    
    @Column(nullable = false, length = 128)
    lateinit var name: String
    
    
    @Column(length = 255)
    var description: String? = null
    
    
    @Column(nullable = false)
    var enabled: Boolean = true
}
