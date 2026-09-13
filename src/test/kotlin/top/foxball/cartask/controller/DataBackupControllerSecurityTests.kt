package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping

class DataBackupControllerSecurityTests {
    @Test
    fun `备份接口只对超级管理员开放`() {
        val expected = mapOf(
            "summary" to "hasRole('SUPER_ADMIN') and hasAuthority('backup:manage')",
            "export" to "hasRole('SUPER_ADMIN') and hasAuthority('backup:manage')",
        )
        val handlers = DataBackupController::class.java.declaredMethods
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
