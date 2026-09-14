package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 申请登记接口的权限映射锁。
 *
 * 权限注解写错不会让任何用例变红——它只会让某个角色在运行时静默拿到或失去能力，
 * 所以这里把映射本身钉死：漏写 `@PreAuthorize`、或登记与审批被合并成同一个码，都会在这里失败。
 */
class VehicleInoutRequestControllerSecurityTests {
    @Test
    fun `申请接口的每个处理方法都校验目标权限`() {
        val expected = mapOf(
            "list" to "hasAuthority('vehicle-inout-request:read')",
            "get" to "hasAuthority('vehicle-inout-request:read')",
            "create" to "hasAuthority('vehicle-inout-request:apply')",
            "update" to "hasAuthority('vehicle-inout-request:apply')",
            "cancel" to "hasAuthority('vehicle-inout-request:apply')",
            // 登记与审核分离：能登记的人不该顺带审自己提的申请。
            "review" to "hasAuthority('vehicle-inout-request:review')",
            // 下发会真实写科拓平台，与审批分开授权。
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
