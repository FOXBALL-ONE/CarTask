package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class DocumentUserRequest(
    @param:JsonProperty("username") val username: String? = null,
    @param:JsonProperty("password") val password: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("deptId") val deptId: Long? = null,
    @param:JsonProperty("positionId") val positionId: Long? = null,
    @param:JsonProperty("jobTitle") val jobTitle: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("email") val email: String? = null,
    @param:JsonProperty("roleIds") val roleIds: List<Long>? = null,
    @param:JsonProperty("status") val status: Int? = null,
)

data class DocumentRoleRequest(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("sort") val sort: Int? = null,
    @param:JsonProperty("status") val status: Int? = null,
    @param:JsonProperty("remark") val remark: String? = null,
    @param:JsonProperty("permissions") val permissions: List<String>? = null,
)

/** class DocumentDepartmentRequest：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class DocumentDepartmentRequest {
    @JsonProperty("name")
    var name: String? = null
    
    @JsonProperty("code")
    var code: String? = null
    
    @JsonProperty("parent")
    var parent: Long? = null
        set(value) {
            field = value
            parentProvided = true
        }
    
    @JsonProperty("sort")
    var sort: Int? = null
    
    @JsonProperty("leader")
    var leader: String? = null
    
    @JsonProperty("phone")
    var phone: String? = null
    
    @JsonProperty("status")
    var status: Int? = null
    
    @get:JsonIgnore
    @set:JsonIgnore
    var parentProvided: Boolean = false
}

data class DocumentPostRequest(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("sort") val sort: Int? = null,
    @param:JsonProperty("status") val status: Int? = null,
    @param:JsonProperty("remark") val remark: String? = null,
)

data class DocumentDeviceRequest(
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("brand") val brand: String? = null,
    @param:JsonProperty("model") val model: String? = null,
    @param:JsonProperty("location") val location: String? = null,
    @param:JsonProperty("ip") val ip: String? = null,
    @param:JsonProperty("status") val status: Int? = null,
    @param:JsonProperty("installDate") val installDate: String? = null,
)

data class OwnerRequest(
    @param:JsonProperty("cardId") val cardId: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("dept") val dept: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("spotCount") val spotCount: Int? = null,
    @param:JsonProperty("plateCount") val plateCount: Int? = null,
    @param:JsonProperty("balance") val balance: BigDecimal? = null,
    @param:JsonProperty("status") val status: Int? = null,
)

data class RechargeRequest(@param:JsonProperty("amount") val amount: BigDecimal? = null)

/** class SpotRequest：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class SpotRequest {
    @JsonProperty("code")
    var code: String? = null
    
    @JsonProperty("area")
    var area: String? = null
    
    @JsonProperty("type")
    var type: String? = null
    
    @JsonProperty("owner")
    var owner: String? = null
        set(value) {
            field = value
            ownerProvided = true
        }
    
    @JsonProperty("status")
    var status: Int? = null
    
    @JsonProperty("remark")
    var remark: String? = null
    
    @get:JsonIgnore
    @set:JsonIgnore
    var ownerProvided: Boolean = false
}

data class PlateRequest(
    @param:JsonProperty("plate") val plate: String? = null,
    @param:JsonProperty("owner") val owner: String? = null,
    @param:JsonProperty("ownerId") val ownerId: Long? = null,
    @param:JsonProperty("status") val status: Int? = null,
    @param:JsonProperty("regDate") val regDate: String? = null,
    @param:JsonProperty("carBrand") val carBrand: String? = null,
    @param:JsonProperty("inspectionDate") val inspectionDate: String? = null,
    @param:JsonProperty("inspectionValidUntil") val inspectionValidUntil: String? = null,
    @param:JsonProperty("inspectionRemark") val inspectionRemark: String? = null,
    
    @param:JsonProperty("inspected") val inspected: Boolean? = null,
)

data class GatePersonRequest(
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("dept") val dept: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("idCard") val idCard: String? = null,
    @param:JsonProperty("face") val face: String? = null,
)

data class DeleteRequestBody(@param:JsonProperty("reason") val reason: String? = null)


data class GatePersonReviewBody(
    @param:JsonProperty("ids") val ids: List<Long>? = null,
    @param:JsonProperty("approved") val approved: Boolean? = null,
    @param:JsonProperty("reason") val reason: String? = null,
)

data class StoredOwner(
    val id: Long,
    val cardId: String,
    val name: String,
    val dept: String,
    val phone: String,
    val spotCount: Int,
    val plateCount: Int,
    val balance: BigDecimal,
    val status: Int,
)

data class StoredSpot(
    val id: Long,
    val code: String,
    val area: String,
    val type: String,
    val owner: String?,
    val status: Int,
    val remark: String?,
)

data class StoredPlate(
    val id: Long,
    val plate: String,
    val owner: String,
    val ownerId: Long,
    val status: Int,
    val regDate: String,
    val carBrand: String,
    val inspectionDate: String?,
    val inspectionValidUntil: String?,
    
    val inspectionStatus: String,
    val inspectionRemark: String?,
)

data class StoredGatePerson(
    val id: Long,
    val code: String,
    val dept: String,
    val name: String,
    val phone: String,
    val idCard: String,
    val face: String?,
    val createTime: String,
    val approveStatus: String,
    val syncStatus: String,
)

data class StoredDeleteRequest(
    val id: Long,
    val personId: Long,
    val code: String,
    val dept: String,
    val name: String,
    val phone: String,
    val idCard: String,
    val face: String?,
    val reason: String,
    val applyTime: String,
    val status: String,
)


data class ManagedDepartmentRequest(
    val departments: List<ManagedDepartmentItem>? = null,
)

data class ManagedDepartmentItem(
    @param:JsonProperty("department_id") val departmentId: Long? = null,
    @param:JsonProperty("include_descendants") val includeDescendants: Boolean = false,
)


/** 强制登出的目标用户；支持一次提交多个，前端即在线名册的多选。 */
data class ForceLogoutRequest(
    @param:JsonProperty("user_ids") val userIds: List<Long>? = null,
)
