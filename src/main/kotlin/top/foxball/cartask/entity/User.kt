package top.foxball.cartask.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Column
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, unique = true, length = 64)
    lateinit var username: String

    @Column(nullable = false, unique = true, length = 255)
    lateinit var email: String

    @Column(nullable = false, length = 255)
    lateinit var passwordHash: String

    @Column(nullable = false, length = 32)
    var role: String = "USER"

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
