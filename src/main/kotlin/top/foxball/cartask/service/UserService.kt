package top.foxball.cartask.service

import com.fasterxml.jackson.annotation.JsonProperty
import top.foxball.cartask.entity.User
import java.time.LocalDateTime

/** 用户账户的创建、查询、更新和删除服务。 */
interface UserService {
    /** 创建用户所需的业务命令；该命令也作为 JSON 请求体直接绑定，避免凭据出现在 URL 中。 */
    data class CreateCommand(
        @param:JsonProperty("username") val username: String,
        @param:JsonProperty("email") val email: String,
        @param:JsonProperty("credential") val credential: String,
        @param:JsonProperty("role") val role: String = "USER",
        @param:JsonProperty("enabled") val enabled: Boolean = true,
        @param:JsonProperty("phone") val phone: String? = null,
        @param:JsonProperty("gender") val gender: User.Gender = User.Gender.UNKNOWN,
        @param:JsonProperty("department_id") val departmentId: Long? = null,
        @param:JsonProperty("position_id") val positionId: Long? = null,
        @param:JsonProperty("status") val status: User.Status = User.Status.Activity,
    )

    /** 用户可变字段命令；未提供的字段保持原值。 */
    data class UpdateCommand(
        @param:JsonProperty("username") val username: String? = null,
        @param:JsonProperty("email") val email: String? = null,
        @param:JsonProperty("credential") val credential: String? = null,
        @param:JsonProperty("role") val role: String? = null,
        @param:JsonProperty("enabled") val enabled: Boolean? = null,
        @param:JsonProperty("phone") val phone: String? = null,
        @param:JsonProperty("gender") val gender: User.Gender? = null,
        @param:JsonProperty("department_id") val departmentId: Long? = null,
        @param:JsonProperty("position_id") val positionId: Long? = null,
        @param:JsonProperty("status") val status: User.Status? = null,
    )

    /** 对外返回的用户数据，不包含密码哈希和实体关联对象。 */
    data class UserData(
        val id: Long,
        val username: String,
        val email: String,
        val role: String,
        val enabled: Boolean,
        val phone: String?,
        val gender: User.Gender,
        @param:JsonProperty("department_id") val departmentId: Long?,
        @param:JsonProperty("position_id") val positionId: Long?,
        val status: User.Status,
        @param:JsonProperty("created_at") val createdAt: LocalDateTime,
        @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
    )

    /** 分页返回数据。 */
    data class PageData(val users: List<UserData>, val page: Int, val pageSize: Int, val total: Long)

    /** 创建一个用户账户。 */
    fun create(command: CreateCommand): UserData
    /** 批量创建用户账户。 */
    fun createBatch(commands: List<CreateCommand>): List<UserData>
    /** 按用户 ID 查询账户信息。 */
    fun get(id: Long): UserData
    /** 按多个用户 ID 批量查询账户信息。 */
    fun getBatch(ids: List<Long>): List<UserData>
    /** 分页查询用户账户。 */
    fun list(page: Int, pageSize: Int): PageData
    /** 更新一个用户账户。 */
    fun update(id: Long, command: UpdateCommand): UserData
    /** 使用同一变更内容批量更新用户账户。 */
    fun updateBatch(ids: List<Long>, command: UpdateCommand): List<UserData>
    /** 删除一个用户账户。 */
    fun delete(id: Long)
    /** 批量删除用户账户。 */
    fun deleteBatch(ids: List<Long>)
}
