package top.foxball.cartask.service

import com.fasterxml.jackson.annotation.JsonProperty
import top.foxball.cartask.authentication.CredentialValue
import top.foxball.cartask.entity.User
import java.time.LocalDateTime


interface ProfileService {
    
    data class ProfileData(
        @param:JsonProperty("user_id") val userId: Long,
        val username: String,
        val name: String?,
        val phone: String?,
        val email: String,
        val gender: User.Gender,
        val role: String,
        @param:JsonProperty("role_name") val roleName: String,
        @param:JsonProperty("department_name") val departmentName: String?,
        val avatar: String?,
        @param:JsonProperty("must_change_password") val mustChangePassword: Boolean,
        @param:JsonProperty("created_at") val createdAt: LocalDateTime,
    )
    
    
    data class UpdateCommand(
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("email") val email: String? = null,
        @param:JsonProperty("gender") val gender: User.Gender? = null,
    )
    
    
    data class ChangePhoneCommand(
        @param:JsonProperty("phone") val phone: String,
        @param:JsonProperty("code") val code: String = "",
    )
    
    
    data class ChangePasswordCommand(
        @param:JsonProperty("current_password") val currentPassword: CredentialValue,
        @param:JsonProperty("new_password") val newPassword: CredentialValue,
    )
    
    
    data class AvatarCommand(
        @param:JsonProperty("avatar") val avatar: String,
    )
    
    
    fun get(userId: Long): ProfileData
    
    
    fun update(userId: Long, command: UpdateCommand): ProfileData
    
    
    fun changePhone(userId: Long, command: ChangePhoneCommand): ProfileData
    
    
    fun changePassword(userId: Long, command: ChangePasswordCommand)
    
    
    fun updateAvatar(userId: Long, command: AvatarCommand): ProfileData
    
    
    fun avatarOf(userId: Long): String?
}
