package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.StoredFile
import java.util.UUID

interface StoredFileRepository : JpaRepository<StoredFile, UUID>
