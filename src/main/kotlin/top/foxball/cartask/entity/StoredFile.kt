package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "stored_files",
    indexes = [
        Index(name = "idx_stored_files_business", columnList = "business_type,business_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_stored_files_stored_filename", columnNames = ["stored_filename"]),
        UniqueConstraint(name = "uk_stored_files_relative_path", columnNames = ["relative_path"]),
    ],
)
class StoredFile {
    @Id
    lateinit var id: UUID
    
    @Column(name = "original_filename", nullable = false, length = 255)
    lateinit var originalFilename: String
    
    @Column(name = "stored_filename", nullable = false, unique = true, length = 64)
    lateinit var storedFilename: String
    
    @Column(name = "relative_path", nullable = false, unique = true, length = 512)
    lateinit var relativePath: String
    
    @Column(name = "content_type", length = 255)
    var contentType: String? = null
    
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0
    
    @Column(nullable = false, length = 64)
    lateinit var sha256: String
    
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(name = "uploaded_by_user_id")
    var uploadedByUserId: Long? = null
    
    
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null
    
    
    @Column(name = "business_type", length = 32)
    var businessType: String? = null
    
    
    @Column(name = "business_id", length = 128)
    var businessId: String? = null
    
    companion object {
        
        const val BUSINESS_VEHICLE_PLATE = "vehicle_plate"
        
        
        const val BUSINESS_GATE_PERSON = "gate_person"
    }
}
