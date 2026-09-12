package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.ManyToMany
import jakarta.persistence.JoinTable
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 系统用户及其组织归属信息。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 登录用户名。 */
    @Column(nullable = false, unique = true, length = 64)
    lateinit var username: String


    @Column(length = 64)
    var nickName: String? = null

    /** 用户邮箱。 */
    @Column(nullable = false, unique = true, length = 255)
    lateinit var email: String

    /** 经过密码编码器处理后的密码，不保存明文密码。 */
    @Column(nullable = false, length = 255)
    lateinit var passwordHash: String

    /**
     * 头像图片。以压缩后的 data URL 存储，而不是文件系统 URL：
     * 前端 <img> 无法携带 Bearer token，走文件下载接口会拿到 401，
     * 因此头像必须能直接内联渲染。
     */
    @Column(columnDefinition = "text")
    var avatar: String? = null

    /**
     * 是否为管理员或系统下发的初始密码，为 true 时首次登录必须修改密码。
     * 初始密码对管理员可见（业主账号生成还是固定口令），不改就是可被冒用的长期凭据。
     * 带 default false 是为了让 ddl-auto=update 能给已有数据的表安全补列。
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    var mustChangePassword: Boolean = false

    /** 联系电话；未填写时为空。 */
    @Column(length = 32)
    var phone: String? = null

    /** 性别信息，未知时使用 UNKNOWN。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var gender: Gender = Gender.UNKNOWN

    /** 用户所属部门；未分配部门时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null

    /** 用户所属职位；未分配职位时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    var position: Position? = null

    /** 文档接口中的角色关联；保留 [role] 作为现有认证系统的主角色编码。 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var roles: MutableSet<Role> = linkedSetOf()

    /** 兼容现有权限接口的角色编码，例如 USER 或 ADMIN。 */
    @Column(nullable = false, length = 32)
    var role: String = "USER"

    /** 账户生命周期状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity

    /** 兼容现有用户管理接口的启用开关。 */
    @Column(nullable = false)
    var enabled: Boolean = true

    /** 创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 最后更新时间。 */
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
