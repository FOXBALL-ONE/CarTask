package top.foxball.cartask.service

import com.fasterxml.jackson.annotation.JsonProperty
import top.foxball.cartask.authentication.CredentialValue
import top.foxball.cartask.entity.User
import java.time.LocalDateTime

/**
 * 个人中心：资料、头像、改密与手机号换绑。
 *
 * 所有操作都以登录态自身的 userId 为对象，不接受调用方指定他人；管理员改他人手机号走
 * [top.foxball.cartask.service.UserService.update]，不需要走这里的短信校验。
 */
interface ProfileService {
    /** 个人中心展示所需的当前用户数据。 */
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

    /**
     * 可自助修改的资料字段；未提供的字段保持原值。
     *
     * 这里**刻意没有 phone**：手机号是本系统的登录凭据（短信登录与重置密码都认它），
     * 自助换绑必须先证明本人掌握新号码，走 [changePhone]。把它留在这里就等于开了一条
     * 「改资料顺便换掉登录凭据」的旁路。
     */
    data class UpdateCommand(
        @param:JsonProperty("name") val name: String? = null,
        @param:JsonProperty("email") val email: String? = null,
        @param:JsonProperty("gender") val gender: User.Gender? = null,
    )

    /**
     * 自助换绑手机号；[code] 是发到 [phone] 上的短信验证码。
     *
     * [code] 给默认值是为了短信验证被临时关闭时可以不传；开着的时候传空照样校验失败。
     */
    data class ChangePhoneCommand(
        @param:JsonProperty("phone") val phone: String,
        @param:JsonProperty("code") val code: String = "",
    )

    /** 改密请求；必须提供原密码，避免会话被窃取后直接改密。 */
    data class ChangePasswordCommand(
        @param:JsonProperty("current_password") val currentPassword: CredentialValue,
        @param:JsonProperty("new_password") val newPassword: CredentialValue,
    )

    /** 头像请求；[avatar] 为压缩后的 data URL，空字符串表示清除头像。 */
    data class AvatarCommand(
        @param:JsonProperty("avatar") val avatar: String,
    )

    /** 查询当前用户的完整资料。 */
    fun get(userId: Long): ProfileData

    /** 修改当前用户的资料字段。 */
    fun update(userId: Long, command: UpdateCommand): ProfileData

    /** 校验短信验证码后换绑当前用户的手机号。 */
    fun changePhone(userId: Long, command: ChangePhoneCommand): ProfileData

    /** 修改当前用户的密码，并撤销其全部历史会话。 */
    fun changePassword(userId: Long, command: ChangePasswordCommand)

    /** 设置或清除当前用户的头像。 */
    fun updateAvatar(userId: Long, command: AvatarCommand): ProfileData

    /** 只取头像，供登录态查询接口复用，避免把整个实体带出去。 */
    fun avatarOf(userId: Long): String?
}
