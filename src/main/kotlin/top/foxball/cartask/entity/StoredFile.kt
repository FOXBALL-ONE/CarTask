package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "stored_files",
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
}
