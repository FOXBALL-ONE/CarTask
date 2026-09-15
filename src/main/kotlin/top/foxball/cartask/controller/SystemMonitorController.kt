package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import com.zaxxer.hikari.HikariDataSource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.authentication.PermissionCatalog
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.sql.DataSource

@RestController
@RequestMapping("/api/system-monitor")
class SystemMonitorController(
    private val responseBuilder: ResponseBuilder,
    private val dataSource: DataSource,
    private val redisTemplate: StringRedisTemplate,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('${PermissionCatalog.SYSTEM_MONITOR_READ}')")
            /**
             * getSnapshot：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun getSnapshot(): ResponseEntity<Response> {
        data class UsageData(val used: Long, val committed: Long, val max: Long)
        data class DiskData(
            val path: String,
            @param:JsonProperty("file_system") val fileSystem: String,
            @param:JsonProperty("total_bytes") val totalBytes: Long,
            @param:JsonProperty("usable_bytes") val usableBytes: Long,
            @param:JsonProperty("used_bytes") val usedBytes: Long,
        )

        data class SystemData(
            @param:JsonProperty("host_name") val hostName: String,
            @param:JsonProperty("host_address") val hostAddress: String,
            @param:JsonProperty("operating_system") val operatingSystem: String,
            val architecture: String,
            @param:JsonProperty("available_processors") val availableProcessors: Int,
            @param:JsonProperty("system_cpu_load") val systemCpuLoad: Double?,
            @param:JsonProperty("process_cpu_load") val processCpuLoad: Double?,
            @param:JsonProperty("physical_memory") val physicalMemory: UsageData,
            val disks: List<DiskData>,
        )

        data class JvmData(
            val name: String,
            val vendor: String,
            val version: String,
            @param:JsonProperty("start_time") val startTime: LocalDateTime,
            @param:JsonProperty("uptime_millis") val uptimeMillis: Long,
            @param:JsonProperty("heap_memory") val heapMemory: UsageData,
            @param:JsonProperty("non_heap_memory") val nonHeapMemory: UsageData,
            @param:JsonProperty("thread_count") val threadCount: Int,
            @param:JsonProperty("peak_thread_count") val peakThreadCount: Int,
        )

        data class GcData(
            val name: String,
            @param:JsonProperty("collection_count") val collectionCount: Long,
            @param:JsonProperty("collection_time_millis") val collectionTimeMillis: Long,
        )

        data class DatabaseData(
            val status: String,
            val product: String?,
            val version: String?,
            val driver: String?,
            val url: String?,
            @param:JsonProperty("active_connections") val activeConnections: Int?,
            @param:JsonProperty("idle_connections") val idleConnections: Int?,
            @param:JsonProperty("total_connections") val totalConnections: Int?,
            @param:JsonProperty("max_connections") val maxConnections: Int?,
            @param:JsonProperty("waiting_threads") val waitingThreads: Int?,
        )

        data class RedisData(
            val status: String,
            val version: String?,
            val mode: String?,
            val port: String?,
            @param:JsonProperty("uptime_seconds") val uptimeSeconds: Long?,
            @param:JsonProperty("connected_clients") val connectedClients: Long?,
            @param:JsonProperty("used_memory_bytes") val usedMemoryBytes: Long?,
            @param:JsonProperty("max_memory_bytes") val maxMemoryBytes: Long?,
            @param:JsonProperty("key_count") val keyCount: Long?,
            @param:JsonProperty("operations_per_second") val operationsPerSecond: Long?,
            @param:JsonProperty("keyspace_hits") val keyspaceHits: Long?,
            @param:JsonProperty("keyspace_misses") val keyspaceMisses: Long?,
        )

        data class Response(
            @param:JsonProperty("captured_at") val capturedAt: LocalDateTime,
            val system: SystemData,
            val jvm: JvmData,
            val garbageCollectors: List<GcData>,
            val database: DatabaseData,
            val redis: RedisData,
        )

        val runtime = Runtime.getRuntime()
        val operatingSystem = ManagementFactory.getOperatingSystemMXBean()
        val extendedOperatingSystem = operatingSystem as? com.sun.management.OperatingSystemMXBean
        val memory = ManagementFactory.getMemoryMXBean()
        val thread = ManagementFactory.getThreadMXBean()
        val host = runCatching { InetAddress.getLocalHost() }.getOrNull()
        val disks = File.listRoots().mapNotNull { root ->
            runCatching {
                val fileStore = Files.getFileStore(root.toPath())
                val totalBytes = fileStore.totalSpace
                val usableBytes = fileStore.usableSpace
                DiskData(
                    root.absolutePath,
                    fileStore.type(),
                    totalBytes,
                    usableBytes,
                    (totalBytes - usableBytes).coerceAtLeast(0)
                )
            }.getOrNull()
        }
        val heap = memory.heapMemoryUsage
        val nonHeap = memory.nonHeapMemoryUsage
        val physicalTotal = extendedOperatingSystem?.totalMemorySize ?: -1L
        val physicalFree = extendedOperatingSystem?.freeMemorySize ?: -1L
        val physicalUsed = if (physicalTotal >= 0 && physicalFree >= 0) physicalTotal - physicalFree else -1L
        val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans().map { collector ->
            GcData(collector.name, collector.collectionCount, collector.collectionTime)
        }
        val hikari = dataSource as? HikariDataSource
        val pool = hikari?.hikariPoolMXBean
        val database = runCatching {
            dataSource.connection.use { connection ->
                val metadata = connection.metaData
                DatabaseData(
                    status = "UP",
                    product = metadata.databaseProductName,
                    version = metadata.databaseProductVersion,
                    driver = metadata.driverName,
                    url = metadata.url.substringBefore('?').replace(Regex("//[^/@]+@"), "//"),
                    activeConnections = pool?.activeConnections,
                    idleConnections = pool?.idleConnections,
                    totalConnections = pool?.totalConnections,
                    maxConnections = hikari?.maximumPoolSize,
                    waitingThreads = pool?.threadsAwaitingConnection,
                )
            }
        }.getOrElse {
            DatabaseData(
                "DOWN",
                null,
                null,
                null,
                null,
                pool?.activeConnections,
                pool?.idleConnections,
                pool?.totalConnections,
                hikari?.maximumPoolSize,
                pool?.threadsAwaitingConnection
            )
        }
        val redis = runCatching {
            val rawInfo = redisTemplate.execute<String> { connection ->
                when (val result = connection.execute("INFO", "all".toByteArray(StandardCharsets.UTF_8))) {
                    is ByteArray -> result.toString(StandardCharsets.UTF_8)
                    is String -> result
                    null -> ""
                    else -> result.toString()
                }
            }.orEmpty()
            val values = rawInfo.lineSequence()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') && line.contains(':') }
                .associate { line -> line.substringBefore(':') to line.substringAfter(':') }
            val keyCount = values
                .filterKeys { it.startsWith("db") }
                .values
                .sumOf { value -> Regex("(?:^|,)keys=(\\d+)").find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L }
            RedisData(
                status = "UP",
                version = values["redis_version"],
                mode = values["redis_mode"],
                port = values["tcp_port"],
                uptimeSeconds = values["uptime_in_seconds"]?.toLongOrNull(),
                connectedClients = values["connected_clients"]?.toLongOrNull(),
                usedMemoryBytes = values["used_memory"]?.toLongOrNull(),
                maxMemoryBytes = values["maxmemory"]?.toLongOrNull(),
                keyCount = keyCount,
                operationsPerSecond = values["instantaneous_ops_per_sec"]?.toLongOrNull(),
                keyspaceHits = values["keyspace_hits"]?.toLongOrNull(),
                keyspaceMisses = values["keyspace_misses"]?.toLongOrNull(),
            )
        }.getOrElse {
            RedisData("DOWN", null, null, null, null, null, null, null, null, null, null, null)
        }
        val rs = Response(
            capturedAt = LocalDateTime.now(),
            system = SystemData(
                hostName = host?.hostName ?: "unknown",
                hostAddress = host?.hostAddress ?: "unknown",
                operatingSystem = "${operatingSystem.name} ${operatingSystem.version}".trim(),
                architecture = operatingSystem.arch,
                availableProcessors = runtime.availableProcessors(),
                systemCpuLoad = extendedOperatingSystem?.cpuLoad?.takeIf { it >= 0 },
                processCpuLoad = extendedOperatingSystem?.processCpuLoad?.takeIf { it >= 0 },
                physicalMemory = UsageData(physicalUsed, physicalTotal, physicalTotal),
                disks = disks,
            ),
            jvm = JvmData(
                name = System.getProperty("java.vm.name", "unknown"),
                vendor = System.getProperty("java.vendor", "unknown"),
                version = System.getProperty("java.version", "unknown"),
                startTime = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().startTime)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime,
                heapMemory = UsageData(heap.used, heap.committed, heap.max),
                nonHeapMemory = UsageData(nonHeap.used, nonHeap.committed, nonHeap.max),
                threadCount = thread.threadCount,
                peakThreadCount = thread.peakThreadCount,
            ),
            garbageCollectors = garbageCollectors,
            database = database,
            redis = redis,
        )
        return responseBuilder.ok().data(rs).build()
    }
}
