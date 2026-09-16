package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "violation_subject")
class ViolationSubject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 16)
    var subjectType: SubjectType = SubjectType.VEHICLE
    
    
    @Column(name = "subject_name", nullable = false, length = 128)
    lateinit var subjectName: String
    
    
    @Column(name = "subject_number", nullable = false, unique = true, length = 64)
    lateinit var subjectNumber: String
    
    
    @Column(length = 32)
    var phone: String? = null
    
    
    @Column(length = 255)
    var remark: String? = null
    
    
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null
    
    
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class SubjectType {
        PERSON,
        VEHICLE,
        OTHER,
    }
    
    enum class Status {
        Activity,
        BANNED,
    }
}
