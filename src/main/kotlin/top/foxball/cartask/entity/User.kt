package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_phone", columnList = "phone"),
        Index(name = "idx_users_department", columnList = "department_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_username", columnNames = ["username"]),
        UniqueConstraint(name = "uk_users_phone", columnNames = ["phone"]),
    ],
)
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, length = 64)
    lateinit var username: String
    
    
    @Column(length = 64)
    var nickName: String? = null
    
    
    @Column(nullable = false, unique = true, length = 255)
    lateinit var email: String
    
    
    @Column(nullable = false, length = 255)
    lateinit var passwordHash: String
    
    
    @Column(columnDefinition = "text")
    var avatar: String? = null
    
    
    @Column(nullable = false, columnDefinition = "boolean default false")
    var mustChangePassword: Boolean = false
    
    
    @Column(length = 32)
    var phone: String? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var gender: Gender = Gender.UNKNOWN
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    var position: Position? = null
    
    
    @Column(length = 128)
    var jobTitle: String? = null
    
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var roles: MutableSet<Role> = linkedSetOf()
    
    
    @Column(nullable = false, length = 32)
    var role: String = "USER"
    
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity
    
    
    @Column(nullable = false)
    var enabled: Boolean = true
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class Gender {
        MALE,
        FEMALE,
        UNKNOWN,
    }
    
    enum class Status {
        Activity,
        BANNED,
    }
}
