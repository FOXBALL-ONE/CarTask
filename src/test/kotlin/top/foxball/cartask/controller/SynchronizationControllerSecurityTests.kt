package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping

class SynchronizationControllerSecurityTests {
    @Test
    fun `同步接口的每个处理方法都校验目标权限`() {
        val expected = mapOf(
            "syncProgress" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAnyAuthority('dictionary:sync', 'vehicle-record:sync', 'owner:sync', 'account:sync')",
            "synchronizeParkingAreas" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('dictionary:sync')",
            "previewAccessRecordSynchronization" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('vehicle-record:sync')",
            "synchronizeAccessRecords" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('vehicle-record:sync')",
            "synchronizeOwners" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('owner:sync')",
            "synchronizeAccounts" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('account:sync')",
            "syncHistory" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('sync-history:read')",
            "syncSchedules" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('sync-schedule:manage')",
            "updateSyncSchedule" to "(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('sync-schedule:manage')",
        )
        val handlers = SynchronizationController::class.java.declaredMethods
            .filter { method ->
                method.annotations.any { annotation ->
                    annotation.annotationClass.java.isAnnotationPresent(RequestMapping::class.java)
                }
            }
            .associateBy { it.name }

        assertEquals(expected.keys, handlers.keys)
        expected.forEach { (methodName, expression) ->
            val annotation = handlers.getValue(methodName).getAnnotation(PreAuthorize::class.java)
            assertNotNull(annotation, "$methodName 必须声明 @PreAuthorize")
            assertEquals(expression, annotation.value, "$methodName 的权限映射不正确")
        }
    }
}
