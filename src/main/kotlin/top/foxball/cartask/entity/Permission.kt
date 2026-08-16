package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 可授予角色的最小权限单元。 */
@Entity
@Table(name = "permissions")
class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 权限的稳定编码，例如 `product:read`。 */
    @Column(nullable = false, unique = true, length = 128)
    lateinit var code: String

    /** 权限的显示名称。 */
    @Column(nullable = false, length = 128)
    lateinit var name: String

    /** 权限用途说明。 */
    @Column(length = 255)
    var description: String? = null

    /** 禁用后，权限不应再被新建角色授予。 */
    @Column(nullable = false)
    var enabled: Boolean = true
}
