package top.foxball.cartask.service

import java.time.LocalDateTime

/** 用户账户的创建、查询、更新和删除服务。 */
interface UserService {
    data class CreateCommand(val username: String, val email: String, val credential: String, val role: String, val enabled: Boolean)
    data class UpdateCommand(val username: String?, val email: String?, val credential: String?, val role: String?, val enabled: Boolean?)
    data class UserData(val id: Long, val username: String, val email: String, val role: String, val enabled: Boolean, val createdAt: LocalDateTime, val updatedAt: LocalDateTime)
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
