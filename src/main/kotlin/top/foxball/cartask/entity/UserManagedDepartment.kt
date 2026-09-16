package top.foxball.cartask.entity

import jakarta.persistence.*


@Entity
@Table(
    name = "user_managed_departments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_managed_department", columnNames = ["user_id", "department_id"]),
    ],
)
class UserManagedDepartment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    lateinit var department: Department
    
    
    @Column(name = "include_descendants", nullable = false, columnDefinition = "boolean default false")
    var includeDescendants: Boolean = false
}
