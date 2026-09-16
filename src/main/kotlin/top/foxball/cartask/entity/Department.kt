package top.foxball.cartask.entity

import jakarta.persistence.*


@Entity
@Table(
    name = "department",
    indexes = [
        Index(name = "idx_department_superior", columnList = "superior_id"),
    ],
)
class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, length = 128)
    lateinit var name: String
    
    
    @Column(name = "department_code", nullable = false, unique = true, length = 64)
    lateinit var departmentNumber: String
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superior_id")
    var superior: Department? = null
    
    
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
    
    
    @Column(length = 128)
    var director: String? = null
    
    
    @Column(name = "contact_phone", length = 32)
    var contactPhone: String? = null
    
    @Column(nullable = false)
    var status: Int = 1
}
