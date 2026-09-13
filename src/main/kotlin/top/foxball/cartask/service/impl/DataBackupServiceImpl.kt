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
import top.foxball.cartask.service.DataBackupService
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.sql.DataSource
import kotlin.concurrent.withLock

/**
 * 用 JDBC 元数据把整库导出成可直接执行的 SQL 脚本，并按需把登记在 `stored_files` 的附件一起打包。
 *
 * 刻意不依赖 pg_dump 之类的命令行工具：应用是以可执行 jar 交付的，目标机器上不保证装过
 * PostgreSQL 客户端，而版本不匹配的 pg_dump 会直接拒绝连接。JDBC 元数据拿到的结构与数据
 * 足以覆盖本项目的表，代价是类型名按驱动上报的原样输出（PostgreSQL 上就是 `int8`、`bpchar`
 * 这类内部名，它们本身是合法的 DDL 类型名）。
 */
@Service
class DataBackupServiceImpl(
    private val dataSource: DataSource,
    private val storedFileRepository: StoredFileRepository,
    private val fileProperties: FileProperties,
    private val maintenanceGate: MaintenanceGate,
    private val auditService: AuditService? = null,
) : DataBackupService {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 同一时刻只允许一次导出。
     *
     * 导出要逐表扫全库并把附件读一遍，两个请求叠在一起既慢又容易把磁盘写满；
     * 拿不到锁直接回 409，比排队到超时更容易看懂。
     */
    private val exportLock = ReentrantLock()

    override fun summary(): DataBackupService.Summary {
        // 先在连接里取完库信息再放回连接池：附件数量走的是仓库自己的连接，
        // 攥着一条连接去要第二条，池子小的时候就是一个必然的死等。
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
            // 只把「生成」这一段关进闸门：产物写完之后数据已经定型，之后的下载不该再挡着其他请求。
            return maintenanceGate.runExclusive { generateArtifact(includeFiles) }
        } finally {
            exportLock.unlock()
        }
    }

    private fun generateArtifact(includeFiles: Boolean): DataBackupService.Artifact {
        val startedAt = System.nanoTime()
        val workRoot = Files.createTempDirectory(TEMP_DIR_PREFIX)
        val stamp = LocalDateTime.now().format(FILE_STAMP_FORMATTER)
        var completed = false
        try {
            val sqlPath = workRoot.resolve("cartask-backup-$stamp.sql")
            val dump = writeSqlDump(sqlPath)
            val artifact = if (includeFiles) {
                val archivePath = workRoot.resolve("cartask-backup-$stamp.zip")
                val archive = writeArchive(archivePath, sqlPath, SQL_ENTRY_NAME)
                DataBackupService.Artifact(
                    path = archivePath,
                    filename = archivePath.fileName.toString(),
                    contentType = ZIP_CONTENT_TYPE,
                    cleanupRoot = workRoot,
                    stats = stats(dump, archiveIncluded = true, archiveBytes = archive.bytes, files = archive, startedAt = startedAt),
                )
            } else {
                DataBackupService.Artifact(
                    path = sqlPath,
                    filename = sqlPath.fileName.toString(),
                    contentType = SQL_CONTENT_TYPE,
                    cleanupRoot = workRoot,
                    stats = stats(dump, archiveIncluded = false, archiveBytes = 0, files = null, startedAt = startedAt),
                )
            }
            recordAudit(artifact.stats)
            completed = true
            return artifact
        } finally {
            // 成功时产物还要给调用方写出，只有失败才在这里就地清理，避免留下半个文件。
            if (!completed) deleteRecursively(workRoot)
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

    // ---------------------------------------------------------------- SQL 导出

    private data class DumpOutcome(val tableCount: Int, val rowCount: Long, val bytes: Long)

    private fun writeSqlDump(target: Path): DumpOutcome {
        var counts = DumpCounts(0, 0)
        Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { out ->
            dataSource.connection.use { connection ->
                val originalAutoCommit = connection.autoCommit
                // 关掉自动提交后 PostgreSQL 才会按 fetchSize 用游标逐批取数；开着自动提交时
                // 驱动会把整张表一次性读进内存，全库备份的第一受害者就是应用自己。
                // 这条连接只读，结束时回滚即可，不影响脚本里那句文本形式的 BEGIN。
                runCatching { connection.autoCommit = false }
                // 再抬一次隔离级别：默认的 READ COMMITTED 每条语句各自取快照，逐表读取时前后两张表
                // 可能落在不同时间点。闸门只能挡住本实例的写入，挡不住别的实例或直接连库的写入，
                // 快照隔离才是同一份数据。
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

        writeHeader(out, meta, tables.size)
        if (postgres) out.write("SET standard_conforming_strings = on;\n")
        out.write("BEGIN;\n\n")

        out.write("-- ========== 表结构 ==========\n")
        out.write("-- 表已存在时整段跳过：正常情况下先启动应用让 ddl-auto 建好结构，再执行本脚本。\n\n")
        tables.forEach { table ->
            out.write(createTableStatement(table, columns.getValue(table), primaryKeys.getValue(table), foreignKeys.getValue(table)))
            out.write("\n")
        }

        out.write("-- ========== 表数据 ==========\n\n")
        var rowCount = 0L
        tables.forEach { table ->
            val tableColumns = columns.getValue(table)
            if (tableColumns.isEmpty()) return@forEach
            rowCount += writeInserts(connection, out, table, tableColumns, postgres)
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

    /** 取当前用户可见的普通表，剔除数据库自身的系统表。 */
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
                // 指向系统表（或本次不导出的表）的外键一律不写，否则空库执行时必然找不到被引用的表。
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
            primaryKey?.let { add("  CONSTRAINT ${quoteIdentifier(it.name)} PRIMARY KEY (${it.columns.joinToString(", ") { column -> quoteIdentifier(column) }})") }
            foreignKeys.forEach { key ->
                add(
                    "  CONSTRAINT ${quoteIdentifier(key.name)} FOREIGN KEY (${key.columns.joinToString(", ") { column -> quoteIdentifier(column) }})" +
                        " REFERENCES ${key.parentTable.qualifiedName} (${key.parentColumns.joinToString(", ") { column -> quoteIdentifier(column) }})" +
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
            // 自增列优先按 IDENTITY 写：PostgreSQL 的 serial 会在 COLUMN_DEF 里带一段
            // nextval('..._seq'::regclass)，而那个序列在空库执行时并不存在。
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
                }
            }
        }
        out.write("\n")
        return rows
    }

    private fun readValue(rs: ResultSet, index: Int, column: ColumnMeta, postgres: Boolean): Any? =
        // timestamptz 必须按带偏移的类型读，否则驱动会先转成 JVM 默认时区再交出来，
        // 生成的字面量就跟着服务器的时区设置漂移。
        if (postgres && column.timeZoneAware) rs.getObject(index, OffsetDateTime::class.java) else rs.getObject(index)

    private fun literal(value: Any?, postgres: Boolean): String = when (value) {
        null -> "NULL"
        is Boolean -> if (value) "TRUE" else "FALSE"
        is ByteArray -> if (postgres) {
            "'\\x${HexFormat.of().formatHex(value)}'::bytea"
        } else {
            "X'${HexFormat.of().formatHex(value)}'"
        }
        // 必须排在 Int/Long 之前：它会走 Number 分支被截断成整数。
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
        is java.sql.Array -> (value.array as? Array<*>)?.joinToString(", ", "ARRAY[", "]") { literal(it, postgres) } ?: "NULL"
        else -> quote(value.toString())
    }

    /**
     * 只转义单引号。
     *
     * 反斜杠保持原样：PostgreSQL 从 9.1 起默认 standard_conforming_strings=on，反斜杠在普通
     * 字符串里就是字面量（脚本开头也显式设了一次），H2 同样如此。用 E'' 转义反而会在 H2 上报语法错误。
     */
    private fun quote(value: String): String = "'${value.replace("'", "''")}'"

    private fun sequenceResetStatement(table: TableRef, column: ColumnMeta): String {
        val tableName = listOfNotNull(table.schema, table.name).joinToString(".")
        return "SELECT setval(pg_get_serial_sequence(${quote(tableName)}, ${quote(column.name)})," +
            " COALESCE((SELECT MAX(${quoteIdentifier(column.name)}) FROM ${table.qualifiedName}), 0) + 1, false);\n"
    }

    // ---------------------------------------------------------------- 附件打包

    private data class ArchiveOutcome(val packed: Int, val bytes: Long, val missing: Int)

    private fun writeArchive(target: Path, sqlPath: Path, sqlEntryName: String): ArchiveOutcome {
        val stored = storedFileRepository.findAll().sortedBy { it.relativePath }
        var packed = 0
        var bytes = 0L
        var missing = 0
        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(sqlEntryName))
                Files.newInputStream(sqlPath).use { it.transferTo(zip) }
                zip.closeEntry()

                val statuses = mutableMapOf<UUID, String>()
                stored.forEach { file ->
                    val resolved = resolveStoredPath(file.relativePath)
                    if (resolved != null && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
                        zip.putNextEntry(ZipEntry("$FILES_ENTRY_PREFIX${zipEntryName(file.relativePath)}"))
                        Files.newInputStream(resolved).use { it.transferTo(zip) }
                        zip.closeEntry()
                        packed++
                        bytes += Files.size(resolved)
                        statuses[file.id] = "OK"
                    } else {
                        // 元数据还在、物理文件没了：如实记进清单，而不是静默少一个文件。
                        missing++
                        statuses[file.id] = "MISSING"
                        log.warn("备份时附件物理文件缺失: id={} path={}", file.id, file.relativePath)
                    }
                }

                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                writeManifest(zip, stored, statuses)
                zip.closeEntry()
            }
        }
        return ArchiveOutcome(packed, bytes, missing)
    }

    private fun writeManifest(out: OutputStream, files: List<StoredFile>, statuses: Map<UUID, String>) {
        // 带 UTF-8 BOM：不带的话 Excel 打开这份清单会把中文文件名显示成乱码。
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
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

    /** 压缩包内的条目名统一用正斜杠；Windows 上拼出来的相对路径会带反斜杠。 */
    private fun zipEntryName(relativePath: String): String = relativePath.replace('\\', '/').trimStart('/')

    /**
     * 把数据库里的相对路径限制在文件根目录内，与下载时的口径一致。
     *
     * 备份会把这里取到的文件原样打进压缩包，越界的路径（绝对路径、`..` 逃逸）必须当成
     * 文件缺失处理，否则一个被改过的 `relative_path` 就能把服务器上的任意文件读进备份里。
     */
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

    // ---------------------------------------------------------------- 工具

    private fun isPostgres(meta: DatabaseMetaData): Boolean =
        meta.databaseProductName?.contains("PostgreSQL", ignoreCase = true) == true

    private fun deleteRecursively(root: Path) {
        runCatching {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
            Files.walk(root).use { paths ->
                // 先深后浅：父目录在枚举流里也会出现，顺序反了会因为目录非空而删不掉。
                paths.sorted { left, right -> right.compareTo(left) }.forEach { path -> runCatching { Files.deleteIfExists(path) } }
            }
        }.onFailure { log.warn("清理备份临时目录失败: {}", root, it) }
    }

    private data class TableRef(val schema: String?, val name: String) {
        /** PostgreSQL 里带上库名（catalog）是非法写法，所以只限定到 schema。 */
        val qualifiedName: String
            get() = if (schema.isNullOrBlank()) quoteIdentifier(name) else "${quoteIdentifier(schema)}.${quoteIdentifier(name)}"
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

        /** 压缩包里的 SQL 固定叫这个名字：解包的人一眼知道先恢复哪个文件，不必先读文件名里的时间戳。 */
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

/**
 * 标识符一律加双引号。
 *
 * 本项目所有表名列名都是小写，加引号与不加在 PostgreSQL 上等价，但加引号能挡住
 * 关键字撞名（`user`、`order` 这类）和大小写混写的表——后者不加引号会被数据库折成小写而找不到表。
 */
private fun quoteIdentifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""
