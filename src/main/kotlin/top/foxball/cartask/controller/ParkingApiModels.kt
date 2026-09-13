package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class DocumentUserRequest(
    @param:JsonProperty("username") val username: String? = null,
    @param:JsonProperty("password") val password: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("deptId") val deptId: Long? = null,
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
    @param:JsonProperty("inspectionDate") val inspectionDate: String? = null,
    @param:JsonProperty("inspectionValidUntil") val inspectionValidUntil: String? = null,
    @param:JsonProperty("inspectionRemark") val inspectionRemark: String? = null,
    /** 是否已年检：false 清空年检登记，true 按请求整条登记（缺年检日期按今天、缺有效期按年检日期起一年）。 */
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

/** 批量审核门禁人员：结论对整批生效，避免前端循环单条调用产生「部分成功」。 */
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
    val inspectionDate: String?,
    val inspectionValidUntil: String?,
    /** 年检状态文案：未年检 / 有效 / 已过期，由后端按当前日期判定，避免各端各算一套。 */
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

/** 替换某个用户的部门管理范围。 */
data class ManagedDepartmentRequest(
    val departments: List<ManagedDepartmentItem>? = null,
)

data class ManagedDepartmentItem(
    @param:JsonProperty("department_id") val departmentId: Long? = null,
    @param:JsonProperty("include_descendants") val includeDescendants: Boolean = false,
)
