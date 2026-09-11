package top.foxball.cartask.controller

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.security.access.prepost.PreAuthorize
import top.foxball.cartask.authentication.PermissionCatalog

class SystemMonitorControllerTests {
    @Test
    fun `系统监控仅要求独立查看权限`() {
        val annotation = SystemMonitorController::class.java
            .getDeclaredMethod("getSnapshot")
            .getAnnotation(PreAuthorize::class.java)

        assertNotNull(annotation)
        assertEquals("hasAuthority('system-monitor:read')", annotation.value)
        assertEquals("查看系统监控", PermissionCatalog.definitions.single { it.code == PermissionCatalog.SYSTEM_MONITOR_READ }.name)
    }
}
