package top.foxball.cartask.service

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.audit.*
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.StoredFileRepository
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.scope.FaceRegistrationScopePolicy
import top.foxball.cartask.shared.GatePersonFields
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.HexFormat
import java.util.zip.ZipInputStream

/** Excel 元数据与照片包的整批校验、预览及原子提交。 */
@Service
class FaceRegistrationWorkbookService(
    private val registrations: FaceRegistrationService,
    private val scopePolicy: FaceRegistrationScopePolicy,
    private val persons: GatePersonRepository,
    private val storedFiles: StoredFileRepository,
    private val departments: DepartmentRepository,
    private val audit: AuditService,
) {
    data class RowResult(val row: Int, val name: String, val idCardLastFour: String, val photoFilename: String, val errors: List<String>)
    data class ImportResult(val imported: Int, val valid: Boolean, val rows: List<RowResult>)

    @Transactional
    fun import(file: MultipartFile, photos: MultipartFile, preview: Boolean): ImportResult {
        require(!file.isEmpty && file.size <= 5 * 1024 * 1024) { "Excel 文件不能为空或超过 5MB" }
        require(!photos.isEmpty && photos.size <= 50 * 1024 * 1024) { "照片压缩包不能为空或超过 50MB" }
        scopePolicy.resolveDepartment(null)
        val photoIndex = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(photos.inputStream, StandardCharsets.UTF_8).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(!entry.isDirectory) { "照片压缩包只允许包含照片文件，不允许目录" }
                val name = normalizedFilename(entry.name)
                require(photoIndex.size < 200) { "照片压缩包最多包含 200 张照片" }
                require(name !in photoIndex) { "照片压缩包包含重复文件名" }
                val bytes = zip.readNBytes(2 * 1024 * 1024 + 1)
                require(bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024) { "照片压缩包包含空文件或超过 2MB 的照片" }
                totalBytes += bytes.size
                require(totalBytes <= 100 * 1024 * 1024) { "照片压缩包解压后不能超过 100MB" }
                photoIndex[name] = bytes
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(photoIndex.isNotEmpty()) { "照片压缩包不能为空" }
        val formatter = DataFormatter()
        val prepared = mutableListOf<PreparedRow>()
        val results = mutableListOf<RowResult>()
        val seenIds = mutableSetOf<String>()
        val seenPhotos = mutableSetOf<String>()
        val seenPhotoHashes = mutableSetOf<String>()
        file.inputStream.use { input ->
            WorkbookFactory.create(input).use { workbook ->
                require(workbook.numberOfSheets == 1) { "导入文件只能包含一张工作表" }
                val sheet = workbook.getSheetAt(0)
                require(sheet.lastRowNum <= 200) { "一次最多导入 200 条登记" }
                val headers = listOf("姓名", "手机号", "身份证号", "照片文件名", "单位编码")
                val header = sheet.getRow(0) ?: throw IllegalArgumentException("Excel 缺少表头")
                require(header.lastCellNum <= 5 && headers.take(4).indices.all { formatter.formatCellValue(header.getCell(it)).trim() == headers[it] }) {
                    "Excel 表头应为：姓名、手机号、身份证号、照片文件名、单位编码（可选）"
                }
                for (index in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(index) ?: continue
                    require(row.lastCellNum <= 5) { "Excel 第 ${index + 1} 行列数超过上限" }
                    val values = (0..4).map { cell ->
                        val value = row.getCell(cell)
                        require(value?.cellType != org.apache.poi.ss.usermodel.CellType.FORMULA) { "Excel 不允许包含公式" }
                        formatter.formatCellValue(value).trim().also { require(it.length <= 255) { "Excel 单元格内容不能超过 255 个字符" } }
                    }
                    if (values.all { it.isBlank() }) continue
                    val errors = mutableListOf<String>()
                    var name = values[0]
                    var phone = values[1]
                    var idCard = values[2]
                    var photoName = values[3]
                    var departmentCode = values[4].takeIf { it.isNotBlank() }
                    var photo: MultipartFile? = null
                    try { name = GatePersonFields.requireName(name) } catch (ex: IllegalArgumentException) { errors.add(ex.message.orEmpty()) }
                    try { phone = GatePersonFields.requirePhone(phone) } catch (ex: IllegalArgumentException) { errors.add(ex.message.orEmpty()) }
                    try {
                        idCard = GatePersonFields.requireIdCard(idCard)
                        if (!seenIds.add(idCard)) errors.add("身份证号在本次导入中重复")
                        if (persons.existsByIdCard(idCard)) errors.add(GatePersonFields.ID_CARD_EXISTS_MESSAGE)
                    } catch (ex: IllegalArgumentException) { errors.add(ex.message.orEmpty()) }
                    try {
                        val current = scopePolicy.resolveDepartment(null)
                        if (departmentCode == current.code) departmentCode = null
                        scopePolicy.resolveDepartment(departmentCode)
                    } catch (ex: IllegalArgumentException) { errors.add(ex.message.orEmpty()) }
                    try {
                        photoName = normalizedFilename(photoName)
                        if (!seenPhotos.add(photoName)) errors.add("照片不能关联多个人员")
                        val bytes = photoIndex[photoName] ?: throw IllegalArgumentException("压缩包中缺少指定照片")
                        photo = PhotoPart(photoName, bytes)
                        val photoHash = top.foxball.cartask.shared.FacePhoto.requireValid(photo)
                        if (!seenPhotoHashes.add(photoHash)) errors.add("照片内容不能关联多个人员")
                        if (storedFiles.existsBySha256AndBusinessType(photoHash, StoredFile.BUSINESS_GATE_PERSON)) {
                            errors.add("照片已关联其他登记记录，请使用独立照片")
                        }
                    } catch (ex: IllegalArgumentException) { errors.add(ex.message.orEmpty()) }
                    results.add(RowResult(index + 1, name, idCard.takeLast(4), photoName.substringAfterLast('/').substringAfterLast('\\'), errors))
                    if (errors.isEmpty()) prepared.add(PreparedRow(name, phone, idCard, requireNotNull(photo), departmentCode))
                }
            }
        }
        require(results.isNotEmpty()) { "Excel 没有可导入的数据" }
        val valid = results.all { it.errors.isEmpty() }
        if (preview || !valid) return ImportResult(0, valid, results)
        prepared.forEach { registrations.register(it.name, it.phone, it.idCard, it.photo, it.departmentCode) }
        audit.record(AuditCommand(
            AuditAction.GATE_PERSON_IMPORTED, "gate_person",
            targetSummary = mapOf("record_count" to prepared.size, "error_count" to 0,
                "workbook_sha256" to HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.bytes))),
        ))
        return ImportResult(prepared.size, true, results)
    }

    fun export(query: FaceRegistrationService.ListQuery): ByteArray {
        val items = mutableListOf<FaceRegistrationService.Detail>()
        var page = 1
        do {
            val result = registrations.list(query, page++, 100)
            items.addAll(result.items)
            require(items.size <= 10000) { "一次最多导出 10000 条登记，请缩小筛选范围" }
        } while (items.size < result.total)
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("人脸登记")
            val headers = listOf("人员编号", "姓名", "手机号", "身份证号", "所属单位", "审核状态", "同步状态", "更新时间")
            val header = sheet.createRow(0)
            headers.forEachIndexed { index, title -> header.createCell(index).setCellValue(title) }
            items.forEachIndexed { index, item ->
                val row = sheet.createRow(index + 1)
                listOf(item.code, item.name, item.phone, "************${item.idCard.takeLast(4)}", item.dept,
                    item.approveStatus.value(), item.syncStatus.value(), item.updatedAt.toString())
                    .forEachIndexed { cell, value -> row.createCell(cell).setCellValue(value) }
            }
            workbook.write(output)
        }
        audit.record(AuditCommand(AuditAction.SENSITIVE_DATA_EXPORTED, "gate_person", targetSummary = mapOf("record_count" to items.size, "masked" to true)))
        return output.toByteArray()
    }

    private fun normalizedFilename(value: String): String {
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        require(normalized.isNotEmpty() && normalized.length <= 128 &&
            normalized.none { it == '/' || it == '\\' || it == ':' || it.code < 32 } && normalized != "." && normalized != "..") {
            "照片文件名不合法，只允许不含目录的文件名"
        }
        require(normalized.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")) { "照片格式只支持 JPEG、PNG、WEBP" }
        return normalized
    }

    private data class PreparedRow(val name: String, val phone: String, val idCard: String, val photo: MultipartFile, val departmentCode: String?)

    private class PhotoPart(private val filename: String, private val bytes: ByteArray) : MultipartFile {
        override fun getName() = "photo"
        override fun getOriginalFilename() = filename
        override fun getContentType() = when (filename.substringAfterLast('.').lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; else -> "image/webp" }
        override fun isEmpty() = bytes.isEmpty()
        override fun getSize() = bytes.size.toLong()
        override fun getBytes() = bytes
        override fun getInputStream() = bytes.inputStream()
        override fun transferTo(dest: java.io.File) = dest.writeBytes(bytes)
    }
}
