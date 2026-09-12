package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "person_access_record")
class PersonAccessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 128)
    lateinit var person: String

    @Column(name = "card_id", length = 64)
    var cardId: String? = null

    @Column(length = 128)
    var dept: String? = null

    /** 部门编码，取值来自 [Department.departmentNumber]；历史数据为 null，读时按 [dept] 兜底解析。 */
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null

    /**
     * 归属账号。本表只有人员姓名和卡号，没有手机号，无法按手机号回溯到用户；
     * 写入时若能按卡号匹配到 [GatePerson]，就把该人员的归属账号落在这里，普通用户才能看到自己的记录。
     */
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null

    @Column(nullable = false)
    lateinit var time: LocalDateTime

    @Column(nullable = false, length = 8)
    lateinit var direction: String

    @Column(length = 128)
    var gate: String? = null

    @Column(length = 64)
    var method: String? = null

    @Column(nullable = false, length = 16)
    var status: String = "正常"

    @Column(length = 1024)
    var photo: String? = null
}
