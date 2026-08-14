package top.foxball.cartask.service

import java.time.LocalDateTime

interface UserService {
    data class CreateCommand(val username: String, val email: String, val credential: String, val role: String, val enabled: Boolean)
    data class UpdateCommand(val username: String?, val email: String?, val credential: String?, val role: String?, val enabled: Boolean?)
    data class UserData(val id: Long, val username: String, val email: String, val role: String, val enabled: Boolean, val createdAt: LocalDateTime, val updatedAt: LocalDateTime)
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
}
