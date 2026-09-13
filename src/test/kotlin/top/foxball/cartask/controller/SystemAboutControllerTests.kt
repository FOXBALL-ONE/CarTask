package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.shared.ResponseBuilder
import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource

class SystemAboutControllerTests {
    private val connection = mock<Connection>()
    private val metaData = mock<DatabaseMetaData>()
    private val dataSource = mock<DataSource>()

    private fun controller(): SystemAboutController {
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.metaData).thenReturn(metaData)
        whenever(metaData.databaseProductName).thenReturn("PostgreSQL")
        whenever(metaData.databaseProductVersion).thenReturn("16.3")
        return SystemAboutController(dataSource, ResponseBuilder(), "carTask")
    }

    @Test
    fun `关于系统要求仪表盘读权限`() {
        val annotation = SystemAboutController::class.java
            .getDeclaredMethod("about")
            .getAnnotation(PreAuthorize::class.java)

        assertNotNull(annotation, "about 必须声明 @PreAuthorize")
        assertEquals("hasAuthority('dashboard:read')", annotation.value)
    }

    @Test
    fun `返回版本、运行环境与数据库产品`() {
        val response = controller().about()

        assertEquals(200, response.statusCode.value())
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals("carTask", data.get("application").get("name").asString())
        assertNotNull(data.get("application").get("version").asString())
        assertNotNull(data.get("runtime").get("java_version").asString())
        assertNotNull(data.get("runtime").get("started_at").asString())
        assertTrue(data.get("runtime").get("uptime_millis").asLong() >= 0)
        assertNotNull(data.get("framework").get("spring_boot_version").asString())
        assertEquals("PostgreSQL", data.get("database").get("product").asString())
        assertEquals("16.3", data.get("database").get("version").asString())
    }

    @Test
    fun `不把部署细节带出去`() {
        val payload = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(
            controller().about().body?.data,
        ).toString()

        // 主机名、连接串、内存这些都在「系统监控」里，这一页任何登录用户都能看，多一个字段就是多泄一点。
        listOf("postgresql://", "jdbc:", "host", "url", "heap", "memory").forEach { leaked ->
            assertFalse(payload.contains(leaked, ignoreCase = true), "关于系统不应包含 $leaked：$payload")
        }
    }

    @Test
    fun `数据库读不到时页面仍能打开`() {
        whenever(dataSource.connection).thenThrow(IllegalStateException("连不上库"))

        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(
            SystemAboutController(dataSource, ResponseBuilder(), "carTask").about().body?.data,
        )

        assertEquals("未知", data.get("database").get("product").asString())
        assertEquals("不可用", data.get("database").get("version").asString())
        assertNotNull(data.get("runtime").get("java_version").asString())
    }
}
