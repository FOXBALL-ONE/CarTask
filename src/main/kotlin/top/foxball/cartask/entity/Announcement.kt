package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "announcement")
class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 160)
    lateinit var title: String

    @Column(nullable = false, columnDefinition = "text")
    lateinit var content: String

    @JsonProperty("published_at")
    @Column(name = "published_at", nullable = false)
    lateinit var publishedAt: LocalDateTime

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
