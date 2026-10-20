package top.foxball.cartask.entity

import jakarta.persistence.*

/** Account-level business resource scope and review capability. */
@Entity
@Table(
    name = "user_resource_permissions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_resource_permission", columnNames = ["user_id", "resource_type"]),
    ],
)
class UserResourcePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 24)
    lateinit var resourceType: ResourceType

    @Column(name = "review_enabled", nullable = false, columnDefinition = "boolean default false")
    var reviewEnabled: Boolean = false

    enum class ResourceType(val wireName: String, val label: String) {
        VEHICLE("vehicle", "车辆"),
        FACE("face", "人脸"),
        VIOLATION("violation", "违规"),
        ;

        companion object {
            fun parse(value: String): ResourceType = entries.firstOrNull {
                it.name.equals(value.trim(), ignoreCase = true) || it.wireName.equals(value.trim(), ignoreCase = true)
            } ?: throw IllegalArgumentException("不支持的资源种类: $value")
        }
    }
}
