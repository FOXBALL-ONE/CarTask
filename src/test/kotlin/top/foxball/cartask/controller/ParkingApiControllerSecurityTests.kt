package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping

class ParkingApiControllerSecurityTests {
    @Test
    fun `兼容接口的每个处理方法都校验目标权限`() {
        val expected = mapOf(
            "listDepartments" to "hasAuthority('department:read')",
            "createDepartment" to "hasAuthority('department:manage')",
            "updateDepartment" to "hasAuthority('department:manage')",
            "deleteDepartment" to "hasAuthority('department:manage')",
            "listPosts" to "hasAuthority('position:read')",
            "createPost" to "hasAuthority('position:manage')",
            "updatePost" to "hasAuthority('position:manage')",
            "deletePost" to "hasAuthority('position:manage')",
            "listOwners" to "hasAuthority('owner:read')",
            "createOwner" to "hasAuthority('owner:manage')",
            "updateOwner" to "hasAuthority('owner:manage')",
            "deleteOwner" to "hasAuthority('owner:manage')",
            "rechargeOwner" to "hasAuthority('owner:manage')",
            "listSpots" to "hasAuthority('spot:read')",
            "createSpot" to "hasAuthority('spot:manage')",
            "updateSpot" to "hasAuthority('spot:manage')",
            "deleteSpot" to "hasAuthority('spot:manage')",
            "listPlates" to "hasAuthority('plate:read')",
            "createPlate" to "hasAuthority('plate:manage')",
            "updatePlate" to "hasAuthority('plate:manage')",
            "deletePlate" to "hasAuthority('plate:manage')",
            "listGatePersons" to "hasAuthority('gate-person:read')",
            "getGatePerson" to "hasAuthority('gate-person:read')",
            "createGatePersonMultipart" to "hasAuthority('gate-person:manage')",
            "updateGatePersonMultipart" to "hasAuthority('gate-person:manage')",
            "approveGatePerson" to "hasAuthority('gate-person:manage')",
            "rejectGatePerson" to "hasAuthority('gate-person:manage')",
            "deleteGatePerson" to "hasAuthority('gate-person:manage')",
            "createDeleteRequest" to "hasAuthority('gate-person:manage')",
            "listDeleteRequests" to "hasAuthority('gate-person:read')",
            "approveDeleteRequest" to "hasAuthority('gate-person:manage')",
            "rejectDeleteRequest" to "hasAuthority('gate-person:manage')",
            "personRecords" to "hasAuthority('person-record:read')",
            "vehicleRecords" to "hasAuthority('vehicle-record:read')",
            "loginLogs" to "hasAuthority('audit:read')",
            "clearLoginLogs" to "hasRole('SUPER_ADMIN') and hasAuthority('audit:delete')",
            "operationLogs" to "hasAuthority('audit:read')",
            "clearOperationLogs" to "hasRole('SUPER_ADMIN') and hasAuthority('audit:delete')",
            "dashboard" to "hasAuthority('dashboard:read')",
        )
        val handlers = ParkingApiController::class.java.declaredMethods
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
