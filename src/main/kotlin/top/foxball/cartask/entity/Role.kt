package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import jakarta.persistence.Transient
import com.fasterxml.jackson.annotation.JsonProperty

/** 一组可复用权限的集合。 */
@Entity
@Table(name = "roles")
class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 角色编码，例如 `SUPER_ADMIN`、`ADMIN` 或 `USER`。 */
    @Column(nullable = false, unique = true, length = 64)
    lateinit var name: String

    /** 角色显示名称。 */
    @Column(length = 255)
    var description: String? = null

    /** 禁用角色后，不应再用它授予访问权限。 */
    @Column(nullable = false)
    var enabled: Boolean = true

    /** 文档兼容字段；权限服务仍以 [name] 和 [enabled] 为准。 */
    @Transient
    @JsonProperty("code")
    var documentCode: String? = null

    @Transient
    @JsonProperty("sort")
    var documentSort: Int? = null

    @Transient
    @JsonProperty("remark")
    var documentRemark: String? = null

    @Transient
    @JsonProperty("status")
    var documentStatus: Int? = null

    /** 角色拥有的权限；关联表只保存角色和权限的 ID。 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")],
    )
    var permissions: MutableSet<Permission> = linkedSetOf()
}
