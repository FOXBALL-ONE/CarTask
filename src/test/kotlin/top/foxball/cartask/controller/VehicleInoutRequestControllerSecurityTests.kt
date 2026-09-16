package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping







class VehicleInoutRequestControllerSecurityTests {
    @Test
    fun `申请接口的每个处理方法都校验目标权限`() {
        val expected = mapOf(
            "list" to "hasAuthority('vehicle-inout-request:read')",
            "get" to "hasAuthority('vehicle-inout-request:read')",
            "create" to "hasAuthority('vehicle-inout-request:apply')",
            "update" to "hasAuthority('vehicle-inout-request:apply')",
            "cancel" to "hasAuthority('vehicle-inout-request:apply')",
            "review" to "hasAuthority('vehicle-inout-request:review')",
            "synchronize" to "hasAuthority('vehicle-inout-request:sync')",
        )
        val handlers = VehicleInoutRequestController::class.java.declaredMethods
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
