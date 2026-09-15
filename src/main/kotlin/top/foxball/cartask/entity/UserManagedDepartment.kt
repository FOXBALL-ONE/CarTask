package top.foxball.cartask.entity

import jakarta.persistence.*

/**
 * 用户被分配的部门管理范围。
 *
 * 「支持多个部门管理」落在这里而不是角色上：范围是**按人**分配的（两个部门管理各管自己的部门
 * 就需要不同范围），而放到角色上还会因为 [Role] 变更会撤销该角色全部持有者的会话，改一个人的
 * 范围就把所有人踢下线。
 *
 * [User.department] 表示的是这个人的归属部门，与这里的管理范围是两回事，不要混用。
 */
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

    /** 为 true 时范围同时包含该部门的下级部门。 */
    @Column(name = "include_descendants", nullable = false, columnDefinition = "boolean default false")
    var includeDescendants: Boolean = false
}
