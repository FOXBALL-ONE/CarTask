package top.foxball.cartask.service

import com.fasterxml.jackson.annotation.JsonProperty
import top.foxball.cartask.entity.User
import java.time.LocalDateTime


interface UserService {
    
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
        @param:JsonProperty("nick_name") val nickName: String? = null,
        @param:JsonProperty("role_ids") val roleIds: List<Long>? = null,
        
        
        @param:JsonProperty("must_change_password") val mustChangePassword: Boolean = true,
        @param:JsonProperty("job_title") val jobTitle: String? = null,
    )
    
    
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
        @param:JsonProperty("nick_name") val nickName: String? = null,
        @param:JsonProperty("role_ids") val roleIds: List<Long>? = null,
        @param:JsonProperty("job_title") val jobTitle: String? = null,
    )
    
    
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
        val name: String? = null,
        @param:JsonProperty("role_ids") val roleIds: List<Long> = emptyList(),
        @param:JsonProperty("job_title") val jobTitle: String? = null,
    )
    
    
    data class PageData(val users: List<UserData>, val page: Int, val pageSize: Int, val total: Long)
    
    
    fun create(command: CreateCommand): UserData
    
    
    fun createBatch(commands: List<CreateCommand>): List<UserData>
    
    
    fun get(id: Long): UserData
    
    
    fun getBatch(ids: List<Long>): List<UserData>
    
    
    fun list(page: Int, pageSize: Int): PageData
    
    
    fun update(id: Long, command: UpdateCommand): UserData
    
    
    fun updateBatch(ids: List<Long>, command: UpdateCommand): List<UserData>
    
    
    fun delete(id: Long)
    
    
    fun deleteBatch(ids: List<Long>)
    
    
    fun existsByUsername(username: String): Boolean
    
    
    fun findExistingUsernames(usernames: Collection<String>): Set<String>
}
