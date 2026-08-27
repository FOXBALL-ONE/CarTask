package top.foxball.cartask.controller

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
)

data class DocumentDepartmentRequest(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("parent") val parent: Long? = null,
    @param:JsonProperty("sort") val sort: Int? = null,
    @param:JsonProperty("leader") val leader: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("status") val status: Int? = null,
)

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

data class SpotRequest(
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("area") val area: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("owner") val owner: String? = null,
    @param:JsonProperty("status") val status: Int? = null,
    @param:JsonProperty("remark") val remark: String? = null,
)

data class PlateRequest(
    @param:JsonProperty("plate") val plate: String? = null,
    @param:JsonProperty("owner") val owner: String? = null,
    @param:JsonProperty("ownerId") val ownerId: Long? = null,
    @param:JsonProperty("status") val status: Int? = null,
    @param:JsonProperty("regDate") val regDate: String? = null,
)

data class GatePersonRequest(
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("dept") val dept: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("idCard") val idCard: String? = null,
    @param:JsonProperty("face") val face: String? = null,
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
