package top.foxball.cartask.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.config.FileProperties
import top.foxball.cartask.config.MaintenanceGate
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.handler.BackupInProgressException
import top.foxball.cartask.repository.StoredFileRepository
import top.foxball.cartask.service.BackupProgressService
import top.foxball.cartask.service.DataBackupService
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.sql.DataSource


@Service
class DataBackupServiceImpl(
    private val dataSource: DataSource,
    private val storedFileRepository: StoredFileRepository,
    private val fileProperties: FileProperties,
    private val maintenanceGate: MaintenanceGate,
    private val backupProgress: BackupProgressService,
    private val auditService: AuditService? = null,
) : DataBackupService {
    private val log = LoggerFactory.getLogger(javaClass)
    
    
    private val exportLock = ReentrantLock()
    
    
    override fun summary(): DataBackupService.Summary {
        val (product, tableCount) = dataSource.connection.use { connection ->
            val meta = connection.metaData
            val name = listOfNotNull(
                meta.databaseProductName?.trim()?.takeIf { it.isNotEmpty() },
                meta.databaseProductVersion?.trim()?.takeIf { it.isNotEmpty() },
            ).joinToString(" ")
            name to loadTables(connection).size
        }
        return DataBackupService.Summary(
            databaseProduct = product,
            tableCount = tableCount,
            fileCount = storedFileRepository.count(),
            fileBytes = storedFileRepository.sumSizeBytes(),
            storageRoot = fileProperties.rootPath.toString(),
        )
    }
    
    
    override fun export(includeFiles: Boolean): DataBackupService.Artifact {
        if (!exportLock.tryLock()) {
            throw BackupInProgressException()
        }
        try {
            return maintenanceGate.runExclusive { generateArtifact(includeFiles) }
        } finally {
            exportLock.unlock()
        }
    }
    
    
    private fun generateArtifact(includeFiles: Boolean): DataBackupService.Artifact {
        val startedAt = System.nanoTime()
        backupProgress.startCounting()
        var workRoot: Path? = null
        var completed = false
        try {
            val workDir = Files.createTempDirectory(TEMP_DIR_PREFIX)
            workRoot = workDir
            val stamp = LocalDateTime.now().format(FILE_STAMP_FORMATTER)
            val sqlPath = workDir.resolve("cartask-backup-$stamp.sql")
            val dump = writeSqlDump(sqlPath)
            val artifact = if (includeFiles) {
                val archivePath = workDir.resolve("cartask-backup-$stamp.zip")
                val archive = writeArchive(archivePath, sqlPath, SQL_ENTRY_NAME)
                DataBackupService.Artifact(
                    path = archivePath,
                    filename = archivePath.fileName.toString(),
                    contentType = ZIP_CONTENT_TYPE,
                    cleanupRoot = workDir,
                    stats = stats(
                        dump,
                        archiveIncluded = true,
                        archiveBytes = archive.bytes,
                        files = archive,
                        startedAt = startedAt
                    ),
                )
            } else {
                DataBackupService.Artifact(
                    path = sqlPath,
                    filename = sqlPath.fileName.toString(),
                    contentType = SQL_CONTENT_TYPE,
                    cleanupRoot = workDir,
                    stats = stats(dump, archiveIncluded = false, archiveBytes = 0, files = null, startedAt = startedAt),
                )
            }
            recordAudit(artifact.stats)
            completed = true
            backupProgress.finish()
            return artifact
        } catch (exception: Exception) {
            backupProgress.fail(exception.message ?: exception.javaClass.simpleName)
            throw exception
        } finally {
            if (!completed) workRoot?.let { deleteRecursively(it) }
        }
    }
    
    
    private fun stats(
        dump: DumpOutcome,
        archiveIncluded: Boolean,
        archiveBytes: Long,
        files: ArchiveOutcome?,
        startedAt: Long,
    ): DataBackupService.Stats = DataBackupService.Stats(
        tableCount = dump.tableCount,
        rowCount = dump.rowCount,
        sqlBytes = dump.bytes,
        fileCount = files?.packed ?: 0,
        fileBytes = files?.bytes ?: 0,
        missingFiles = files?.missing ?: 0,
        archiveIncluded = archiveIncluded,
        archiveBytes = archiveBytes,
        durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
    )
    
    
    private fun recordAudit(stats: DataBackupService.Stats) {
        auditService?.record(
            AuditCommand(
                AuditAction.DATA_BACKUP_CREATED,
                "data_backup",
                targetSummary = mapOf(
                    "include_files" to stats.archiveIncluded,
                    "table_count" to stats.tableCount,
                    "row_count" to stats.rowCount,
                    "file_count" to stats.fileCount,
                    "missing_files" to stats.missingFiles,
                    "sql_bytes" to stats.sqlBytes,
                    "archive_bytes" to stats.archiveBytes,
                    "duration_ms" to stats.durationMillis,
                ),
            ),
        )
    }
    
    
    private data class DumpOutcome(val tableCount: Int, val rowCount: Long, val bytes: Long)
    
    
    private fun writeSqlDump(target: Path): DumpOutcome {
        var counts = DumpCounts(0, 0)
        Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            .use { out ->
                dataSource.connection.use { connection ->
                    val originalAutoCommit = connection.autoCommit
                    runCatching { connection.autoCommit = false }
                    runCatching { connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ }
                        .onFailure { log.warn("数据库不支持 REPEATABLE READ，本次按默认隔离级别导出：{}", it.message) }
                    try {
                        counts = dumpTo(connection, out)
                    } finally {
                        runCatching {
                            if (!originalAutoCommit) connection.rollback()
                            connection.autoCommit = originalAutoCommit
                        }
                    }
                }
            }
        return DumpOutcome(counts.tableCount, counts.rowCount, Files.size(target))
    }
    
    private data class DumpCounts(val tableCount: Int, val rowCount: Long)
    
    
    private fun dumpTo(connection: Connection, out: BufferedWriter): DumpCounts {
        val meta = connection.metaData
        val postgres = isPostgres(meta)
        val catalog = connection.catalog
        val tables = loadTables(connection)
        val columns = tables.associateWith { readColumns(meta, catalog, it) }
        val primaryKeys = tables.associateWith { readPrimaryKey(meta, catalog, it) }
        val foreignKeys = tables.associateWith { readForeignKeys(meta, catalog, it, tables) }
        
        val rowsTotal = countRows(connection, tables)
        
        writeHeader(out, meta, tables.size)
        if (postgres) out.write("SET standard_conforming_strings = on;\n")
        out.write("BEGIN;\n\n")
        
        backupProgress.startDumping(tables.size, rowsTotal)
        out.write("-- ========== 表结构 ==========\n")
        out.write("-- 表已存在时整段跳过：正常情况下先启动应用让 ddl-auto 建好结构，再执行本脚本。\n\n")
        tables.forEach { table ->
            out.write(
                createTableStatement(
                    table,
                    columns.getValue(table),
                    primaryKeys.getValue(table),
                    foreignKeys.getValue(table)
                )
            )
            out.write("\n")
        }
        
        out.write("-- ========== 表数据 ==========\n\n")
        var rowCount = 0L
        tables.forEachIndexed { index, table ->
            val tableColumns = columns.getValue(table)
            if (tableColumns.isEmpty()) return@forEachIndexed
            rowCount += writeInserts(connection, out, table, tableColumns, postgres) { rowsWritten ->
                backupProgress.dumping(index + 1, rowCount + rowsWritten)
            }
            backupProgress.dumping(index + 1, rowCount)
        }
        
        if (postgres) {
            out.write("-- ========== 自增序列复位 ==========\n")
            out.write("-- 上面的 INSERT 都带着显式主键，序列不会自己往前走；不复位的话应用下次插入必定撞主键。\n\n")
            tables.forEach { table ->
                columns.getValue(table).filter { it.autoIncrement }.forEach { autoColumn ->
                    out.write(sequenceResetStatement(table, autoColumn))
                }
            }
            out.write("\n")
        }
        out.write("COMMIT;\n")
        return DumpCounts(tables.size, rowCount)
    }
    
    
    private fun writeHeader(out: BufferedWriter, meta: DatabaseMetaData, tableCount: Int) {
        out.write("-- carTask 数据备份\n")
        out.write("-- 生成时间: ${LocalDateTime.now().format(HEADER_TIME_FORMATTER)}\n")
        out.write("-- 数据库: ${meta.databaseProductName} ${meta.databaseProductVersion}\n")
        out.write("-- 表数量: $tableCount\n")
        out.write("--\n")
        out.write("-- 恢复步骤：先启动应用，让 ddl-auto 把表结构建出来，再用 psql -f 执行本脚本。\n")
        out.write("-- 脚本里的 CREATE TABLE IF NOT EXISTS 只是空库兜底，已存在的表会被整段跳过。\n")
        out.write("-- 执行前请确认目标库为空或已确认可以覆盖同主键数据，脚本不会做任何清理。\n\n")
    }
    
    
    private fun loadTables(connection: Connection): List<TableRef> {
        val meta = connection.metaData
        val catalog = connection.catalog
        val schemaPattern = connection.schema?.trim()?.takeIf { it.isNotEmpty() }
        val tables = mutableListOf<TableRef>()
        meta.getTables(catalog, schemaPattern, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val name = rs.getString("TABLE_NAME") ?: continue
                val schema = rs.getString("TABLE_SCHEM")
                if (!isBusinessTable(schema, name)) continue
                tables += TableRef(schema, name)
            }
        }
        return tables.sortedBy { it.qualifiedName }
    }
    
    
    private fun isBusinessTable(schema: String?, name: String): Boolean {
        val normalizedSchema = schema?.lowercase()
        if (normalizedSchema != null && normalizedSchema in SYSTEM_SCHEMAS) return false
        val normalizedName = name.lowercase()
        return SYSTEM_TABLE_PREFIXES.none { normalizedName.startsWith(it) }
    }
    
    
    private fun readColumns(meta: DatabaseMetaData, catalog: String?, table: TableRef): List<ColumnMeta> {
        val columns = mutableListOf<ColumnMeta>()
        meta.getColumns(catalog, table.schema, table.name, "%").use { rs ->
            while (rs.next()) {
                val typeName = rs.getString("TYPE_NAME") ?: "text"
                columns += ColumnMeta(
                    name = rs.getString("COLUMN_NAME"),
                    typeName = typeName,
                    size = rs.getInt("COLUMN_SIZE"),
                    decimals = rs.getInt("DECIMAL_DIGITS"),
                    nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                    defaultValue = rs.getString("COLUMN_DEF")?.trim()?.takeIf { it.isNotEmpty() },
                    autoIncrement = rs.getString("IS_AUTOINCREMENT").equals("YES", ignoreCase = true),
                    timeZoneAware = typeName.lowercase() in TIME_ZONE_TYPE_NAMES,
                    ordinal = rs.getInt("ORDINAL_POSITION"),
                )
            }
        }
        return columns.sortedBy { it.ordinal }
    }
    
    
    private fun readPrimaryKey(meta: DatabaseMetaData, catalog: String?, table: TableRef): PrimaryKey? {
        val columns = sortedMapOf<Int, String>()
        var name: String? = null
        meta.getPrimaryKeys(catalog, table.schema, table.name).use { rs ->
            while (rs.next()) {
                val column = rs.getString("COLUMN_NAME") ?: continue
                columns[rs.getInt("KEY_SEQ")] = column
                name = name ?: rs.getString("PK_NAME")
            }
        }
        if (columns.isEmpty()) return null
        return PrimaryKey(name?.takeIf { it.isNotBlank() } ?: "pk_${table.name}", columns.values.toList())
    }
    
    
    private fun readForeignKeys(
        meta: DatabaseMetaData,
        catalog: String?,
        table: TableRef,
        known: List<TableRef>,
    ): List<ForeignKey> {
        val knownByName = known.associateBy { it.name }
        val keys = linkedMapOf<String, MutableList<Pair<Int, Pair<String, String>>>>()
        val names = mutableMapOf<String, String?>()
        val rules = mutableMapOf<String, Pair<Int, Int>>()
        val parents = mutableMapOf<String, TableRef>()
        meta.getImportedKeys(catalog, table.schema, table.name).use { rs ->
            while (rs.next()) {
                val parent = rs.getString("PKTABLE_NAME") ?: continue
                val parentTable = knownByName[parent] ?: continue
                val keyName = rs.getString("FK_NAME")?.takeIf { it.isNotBlank() } ?: "fk_${table.name}_${parent}"
                val column = rs.getString("FKCOLUMN_NAME") ?: continue
                val parentColumn = rs.getString("PKCOLUMN_NAME") ?: continue
                keys.getOrPut(keyName) { mutableListOf() } += rs.getInt("KEY_SEQ") to (column to parentColumn)
                names[keyName] = rs.getString("FK_NAME")
                rules[keyName] = rs.getInt("UPDATE_RULE") to rs.getInt("DELETE_RULE")
                parents[keyName] = parentTable
            }
        }
        return keys.map { (keyName, pairs) ->
            val (updateRule, deleteRule) = rules.getValue(keyName)
            ForeignKey(
                name = names[keyName]?.takeIf { it.isNotBlank() } ?: keyName,
                columns = pairs.sortedBy { it.first }.map { it.second.first },
                parentTable = parents.getValue(keyName),
                parentColumns = pairs.sortedBy { it.first }.map { it.second.second },
                onUpdate = referentialAction(updateRule),
                onDelete = referentialAction(deleteRule),
            )
        }
    }
    
    
    private fun referentialAction(rule: Int): String? = when (rule) {
        DatabaseMetaData.importedKeyCascade -> "CASCADE"
        DatabaseMetaData.importedKeySetNull -> "SET NULL"
        DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT"
        DatabaseMetaData.importedKeyRestrict -> "RESTRICT"
        else -> null
    }
    
    
    private fun createTableStatement(
        table: TableRef,
        columns: List<ColumnMeta>,
        primaryKey: PrimaryKey?,
        foreignKeys: List<ForeignKey>,
    ): String = buildString {
        append("CREATE TABLE IF NOT EXISTS ${table.qualifiedName} (\n")
        val definitions = columns.map { column -> "  ${columnDefinition(column)}" }
        val constraints = buildList {
            primaryKey?.let {
                add(
                    "  CONSTRAINT ${quoteIdentifier(it.name)} PRIMARY KEY (${
                        it.columns.joinToString(", ") { column ->
                            quoteIdentifier(
                                column
                            )
                        }
                    })"
                )
            }
            foreignKeys.forEach { key ->
                add(
                    "  CONSTRAINT ${quoteIdentifier(key.name)} FOREIGN KEY (${
                        key.columns.joinToString(", ") { column ->
                            quoteIdentifier(
                                column
                            )
                        }
                    })" +
                            " REFERENCES ${key.parentTable.qualifiedName} (${
                                key.parentColumns.joinToString(", ") { column ->
                                    quoteIdentifier(
                                        column
                                    )
                                }
                            })" +
                            (key.onUpdate?.let { " ON UPDATE $it" } ?: "") +
                            (key.onDelete?.let { " ON DELETE $it" } ?: ""),
                )
            }
        }
        append((definitions + constraints).joinToString(",\n"))
        append("\n);\n")
    }
    
    
    private fun columnDefinition(column: ColumnMeta): String = buildString {
        append("${quoteIdentifier(column.name)} ${columnSqlType(column)}")
        if (!column.nullable) append(" NOT NULL")
        when {
            column.autoIncrement && column.defaultValue == null -> append(" GENERATED BY DEFAULT AS IDENTITY")
            column.defaultValue != null -> append(" DEFAULT ${column.defaultValue}")
        }
    }
    
    
    private fun columnSqlType(column: ColumnMeta): String {
        val base = column.typeName.lowercase()
        return when {
            base in CHARACTER_TYPE_NAMES && column.size > 0 -> "$base(${column.size})"
            base in DECIMAL_TYPE_NAMES && column.size > 0 && column.decimals >= 0 -> "$base(${column.size},${column.decimals})"
            else -> base
        }
    }
    
    
    private fun writeInserts(
        connection: Connection,
        out: BufferedWriter,
        table: TableRef,
        columns: List<ColumnMeta>,
        postgres: Boolean,
        onProgress: (Long) -> Unit,
    ): Long {
        val projection = columns.joinToString(", ") { quoteIdentifier(it.name) }
        var rows = 0L
        out.write("-- ${table.qualifiedName}\n")
        connection.createStatement().use { statement ->
            statement.fetchSize = FETCH_SIZE
            statement.executeQuery("SELECT $projection FROM ${table.qualifiedName}").use { rs ->
                val prefix = "INSERT INTO ${table.qualifiedName} ($projection) VALUES ("
                while (rs.next()) {
                    out.write(prefix)
                    columns.forEachIndexed { index, column ->
                        if (index > 0) out.write(", ")
                        out.write(literal(readValue(rs, index + 1, column, postgres), postgres))
                    }
                    out.write(");\n")
                    rows++
                    if (rows % FETCH_SIZE == 0L) onProgress(rows)
                }
            }
        }
        out.write("\n")
        onProgress(rows)
        return rows
    }
    
    
    private fun countRows(connection: Connection, tables: List<TableRef>): Long {
        backupProgress.counting(0, tables.size)
        var total = 0L
        tables.forEachIndexed { index, table ->
            total += connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM ${table.qualifiedName}").use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
            backupProgress.counting(index + 1, tables.size)
        }
        return total
    }
    
    
    private fun readValue(rs: ResultSet, index: Int, column: ColumnMeta, postgres: Boolean): Any? =
        if (postgres && column.timeZoneAware) rs.getObject(index, OffsetDateTime::class.java) else rs.getObject(index)
    
    
    private fun literal(value: Any?, postgres: Boolean): String = when (value) {
        null -> "NULL"
        is Boolean -> if (value) "TRUE" else "FALSE"
        is ByteArray -> if (postgres) {
            "'\\x${HexFormat.of().formatHex(value)}'::bytea"
        } else {
            "X'${HexFormat.of().formatHex(value)}'"
        }
        
        is BigDecimal -> value.toPlainString()
        is Number -> value.toString()
        is UUID -> quote(value.toString())
        is OffsetDateTime -> quote(value.format(OFFSET_DATE_TIME_FORMATTER))
        is LocalDateTime -> quote(value.format(LOCAL_DATE_TIME_FORMATTER))
        is LocalDate -> quote(value.toString())
        is LocalTime -> quote(value.toString())
        is Instant -> quote(value.atOffset(ZoneOffset.UTC).format(OFFSET_DATE_TIME_FORMATTER))
        is Timestamp -> quote(value.toLocalDateTime().format(LOCAL_DATE_TIME_FORMATTER))
        is java.sql.Date -> quote(value.toString())
        is java.sql.Time -> quote(value.toString())
        is java.sql.Array -> (value.array as? Array<*>)?.joinToString(", ", "ARRAY[", "]") { literal(it, postgres) }
            ?: "NULL"
        
        else -> quote(value.toString())
    }
    
    
    private fun quote(value: String): String = "'${value.replace("'", "''")}'"
    
    
    private fun sequenceResetStatement(table: TableRef, column: ColumnMeta): String {
        val tableName = listOfNotNull(table.schema, table.name).joinToString(".")
        return "SELECT setval(pg_get_serial_sequence(${quote(tableName)}, ${quote(column.name)})," +
                " COALESCE((SELECT MAX(${quoteIdentifier(column.name)}) FROM ${table.qualifiedName}), 0) + 1, false);\n"
    }
    
    private data class ArchiveOutcome(val packed: Int, val bytes: Long, val missing: Int)
    
    
    private fun writeArchive(target: Path, sqlPath: Path, sqlEntryName: String): ArchiveOutcome {
        val stored = storedFileRepository.findAll().sortedBy { it.relativePath }
        var packed = 0
        var bytes = 0L
        var missing = 0
        backupProgress.startArchiving(stored.size)
        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(sqlEntryName))
                Files.newInputStream(sqlPath).use { it.transferTo(zip) }
                zip.closeEntry()
                
                val statuses = mutableMapOf<UUID, String>()
                stored.forEachIndexed { index, file ->
                    val resolved = resolveStoredPath(file.relativePath)
                    if (resolved != null && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
                        zip.putNextEntry(ZipEntry("$FILES_ENTRY_PREFIX${zipEntryName(file.relativePath)}"))
                        Files.newInputStream(resolved).use { it.transferTo(zip) }
                        zip.closeEntry()
                        packed++
                        bytes += Files.size(resolved)
                        statuses[file.id] = "OK"
                    } else {
                        missing++
                        statuses[file.id] = "MISSING"
                        log.warn("备份时附件物理文件缺失: id={} path={}", file.id, file.relativePath)
                    }
                    backupProgress.archiving(index + 1)
                }
                
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                writeManifest(zip, stored, statuses)
                zip.closeEntry()
            }
        }
        return ArchiveOutcome(packed, bytes, missing)
    }
    
    
    private fun writeManifest(out: OutputStream, files: List<StoredFile>, statuses: Map<UUID, String>) {
        out.write(UTF8_BOM)
        out.write((MANIFEST_HEADERS.joinToString(",") + "\n").toByteArray(StandardCharsets.UTF_8))
        files.forEach { file ->
            val row = listOf(
                file.id.toString(),
                file.originalFilename,
                file.storedFilename,
                file.relativePath,
                file.contentType ?: "",
                file.sizeBytes.toString(),
                file.sha256,
                file.createdAt.toString(),
                file.uploadedByUserId?.toString() ?: "",
                file.departmentCode ?: "",
                file.businessType ?: "",
                file.businessId ?: "",
                statuses[file.id] ?: "MISSING",
            )
            out.write((row.joinToString(",") { csvField(it) } + "\n").toByteArray(StandardCharsets.UTF_8))
        }
    }
    
    
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${
            value.replace(
                "\"",
                "\"\""
            )
        }\"" else value
    
    
    private fun zipEntryName(relativePath: String): String = relativePath.replace('\\', '/').trimStart('/')
    
    
    private fun resolveStoredPath(relativePath: String): Path? {
        val relative = try {
            Path.of(relativePath).normalize()
        } catch (_: InvalidPathException) {
            return null
        }
        if (relative.isAbsolute || relative.startsWith("..")) return null
        val resolved = fileProperties.rootPath.resolve(relative).normalize()
        return resolved.takeIf { it.startsWith(fileProperties.rootPath) }
    }
    
    private fun isPostgres(meta: DatabaseMetaData): Boolean =
        meta.databaseProductName?.contains("PostgreSQL", ignoreCase = true) == true
    
    
    private fun deleteRecursively(root: Path) {
        runCatching {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
            Files.walk(root).use { paths ->
                paths.sorted { left, right -> right.compareTo(left) }
                    .forEach { path -> runCatching { Files.deleteIfExists(path) } }
            }
        }.onFailure { log.warn("清理备份临时目录失败: {}", root, it) }
    }
    
    private data class TableRef(val schema: String?, val name: String) {
        
        val qualifiedName: String
            get() = if (schema.isNullOrBlank()) quoteIdentifier(name) else "${quoteIdentifier(schema)}.${
                quoteIdentifier(
                    name
                )
            }"
    }
    
    private data class ColumnMeta(
        val name: String,
        val typeName: String,
        val size: Int,
        val decimals: Int,
        val nullable: Boolean,
        val defaultValue: String?,
        val autoIncrement: Boolean,
        val timeZoneAware: Boolean,
        val ordinal: Int,
    )
    
    private data class PrimaryKey(val name: String, val columns: List<String>)
    
    private data class ForeignKey(
        val name: String,
        val columns: List<String>,
        val parentTable: TableRef,
        val parentColumns: List<String>,
        val onUpdate: String?,
        val onDelete: String?,
    )
    
    private companion object {
        const val TEMP_DIR_PREFIX = "cartask-backup-"
        const val SQL_CONTENT_TYPE = "application/sql"
        const val ZIP_CONTENT_TYPE = "application/zip"
        const val FILES_ENTRY_PREFIX = "files/"
        const val MANIFEST_ENTRY_NAME = "manifest.csv"
        
        
        const val SQL_ENTRY_NAME = "database.sql"
        const val FETCH_SIZE = 500
        
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        
        val FILE_STAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val HEADER_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val LOCAL_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
        val OFFSET_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx")
        
        val MANIFEST_HEADERS = listOf(
            "id", "original_filename", "stored_filename", "relative_path", "content_type", "size_bytes",
            "sha256", "created_at", "uploaded_by_user_id", "department_code", "business_type", "business_id", "status",
        )
        
        val SYSTEM_SCHEMAS = setOf(
            "information_schema", "pg_catalog", "pg_toast", "sys", "system",
            "mysql", "performance_schema", "pg_temp_1", "pg_toast_temp_1",
        )
        val SYSTEM_TABLE_PREFIXES = listOf("pg_", "sql_")
        
        val CHARACTER_TYPE_NAMES = setOf(
            "char", "bpchar", "character", "varchar", "varchar2", "nvarchar", "nvarchar2", "nchar", "character varying",
        )
        val DECIMAL_TYPE_NAMES = setOf("numeric", "decimal", "number")
        val TIME_ZONE_TYPE_NAMES = setOf("timestamptz", "timestamp with time zone", "timestamp(6) with time zone")
    }
}


private fun quoteIdentifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""
