package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootVersion
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.sql.DataSource

data class AboutApplicationData(
    val name: String,
    val version: String,
)

data class AboutRuntimeData(
    @param:JsonProperty("java_version") val javaVersion: String,
    @param:JsonProperty("java_vendor") val javaVendor: String,
    @param:JsonProperty("jvm_name") val jvmName: String,
    @param:JsonProperty("started_at") val startedAt: LocalDateTime,
    @param:JsonProperty("uptime_millis") val uptimeMillis: Long,
    @param:JsonProperty("time_zone") val timeZone: String,
)

data class AboutFrameworkData(
    @param:JsonProperty("spring_boot_version") val springBootVersion: String?,
)

data class AboutDatabaseData(
    val product: String,
    val version: String,
)

data class AboutData(
    val application: AboutApplicationData,
    val runtime: AboutRuntimeData,
    val framework: AboutFrameworkData,
    val database: AboutDatabaseData,
)

/**
 * 「关于系统」页面的信息接口。
 *
 * 刻意只给版本与运行环境：主机名、地址、内存、连接串这些都在「系统监控」里，那边有
 * `system-monitor:read` 把着。这里任何登录用户都能看，多放一个字段就是多泄一点部署细节。
 */
@RestController
@RequestMapping("/api/system/about")
class SystemAboutController(
    private val dataSource: DataSource,
    private val responseBuilder: ResponseBuilder,
    @Value("\${spring.application.name:carTask}") private val applicationName: String,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('dashboard:read')")
            /**
             * about：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun about(): ResponseEntity<Response> {
        val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
        val rs = AboutData(
            application = AboutApplicationData(
                name = applicationName,
                version = implementationVersion() ?: DEV_VERSION,
            ),
            runtime = AboutRuntimeData(
                javaVersion = System.getProperty("java.version") ?: "未知",
                javaVendor = System.getProperty("java.vendor") ?: "未知",
                jvmName = System.getProperty("java.vm.name") ?: "未知",
                startedAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(runtimeMxBean.startTime),
                    ZoneId.systemDefault()
                ),
                uptimeMillis = runtimeMxBean.uptime,
                timeZone = ZoneId.systemDefault().id,
            ),
            framework = AboutFrameworkData(springBootVersion = SpringBootVersion.getVersion()),
            database = readDatabaseInfo(),
        )
        return responseBuilder.ok().data(rs).build()
    }

    /**
     * 从 manifest 读版本。
     *
     * 打成可执行 jar 运行时才有值；在 IDE 里跑的是 classes 目录，读不到，用 [DEV_VERSION] 兜底，
     * 免得页面上显示一个空版本号。
     */
    private fun implementationVersion(): String? =
        javaClass.`package`?.implementationVersion?.takeIf { it.isNotBlank() }

    /** 取库的产品与版本；连不上库时不能连带整个接口失败，页面还得能打开。 */
    private fun readDatabaseInfo(): AboutDatabaseData = runCatching {
        dataSource.connection.use { connection ->
            val meta = connection.metaData
            AboutDatabaseData(
                product = meta.databaseProductName ?: "未知",
                version = meta.databaseProductVersion ?: "未知",
            )
        }
    }.getOrElse {
        AboutDatabaseData(product = "未知", version = "不可用")
    }

    private companion object {
        const val DEV_VERSION = "开发模式（未打包）"
    }
}
