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


@RestController
@RequestMapping("/api/system/about")
/** class SystemAboutController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class SystemAboutController(
    private val dataSource: DataSource,
    private val responseBuilder: ResponseBuilder,
    @Value("\${spring.application.name:carTask}") private val applicationName: String,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('dashboard:read')")
            
            
            /** about：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    private fun implementationVersion(): String? =
        javaClass.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
    
    
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
