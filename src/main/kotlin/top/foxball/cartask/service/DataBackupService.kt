package top.foxball.cartask.service

import java.nio.file.Path


interface DataBackupService {
    
    data class Summary(
        val databaseProduct: String,
        val tableCount: Int,
        val fileCount: Long,
        val fileBytes: Long,
        val storageRoot: String,
    )
    
    
    data class Stats(
        val tableCount: Int,
        val rowCount: Long,
        val sqlBytes: Long,
        val fileCount: Int,
        val fileBytes: Long,
        val missingFiles: Int,
        val archiveIncluded: Boolean,
        val archiveBytes: Long,
        val durationMillis: Long,
    )
    
    
    data class Artifact(
        val path: Path,
        val filename: String,
        val contentType: String,
        val cleanupRoot: Path,
        val stats: Stats,
    )
    
    
    fun summary(): Summary
    
    
    fun export(includeFiles: Boolean): Artifact
}
