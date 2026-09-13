package top.foxball.cartask.service

import java.nio.file.Path

/** 生成数据库与附件备份产物的服务。仅超级管理员可用。 */
interface DataBackupService {
    /** 备份前的概览，用于页面上先告诉使用者这次会备份多少东西。 */
    data class Summary(
        val databaseProduct: String,
        val tableCount: Int,
        val fileCount: Long,
        val fileBytes: Long,
        val storageRoot: String,
    )

    /** 一次备份实际产出的内容。 */
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

    /**
     * 备份产物。
     *
     * [path] 落在 [cleanupRoot] 这个临时目录里，响应写完后由调用方整个删掉——
     * 备份动辄上百 MB，不能留在磁盘上等下一次清理。
     */
    data class Artifact(
        val path: Path,
        val filename: String,
        val contentType: String,
        val cleanupRoot: Path,
        val stats: Stats,
    )

    fun summary(): Summary

    /**
     * 生成备份。
     *
     * [includeFiles] 为 false 时只产出 SQL 文件；为 true 时产出一个压缩包，内含同一份 SQL、
     * 全部登记在册的附件以及附件清单。SQL 无论选不选压缩包都会生成。
     */
    fun export(includeFiles: Boolean): Artifact
}
