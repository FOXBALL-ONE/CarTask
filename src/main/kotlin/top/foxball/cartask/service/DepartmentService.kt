package top.foxball.cartask.service

import top.foxball.cartask.entity.Department
import org.springframework.data.domain.Page

/** 组织部门的业务服务。 */
interface DepartmentService {
    fun create(entity: Department): Department
    fun createBatch(entities: List<Department>): List<Department>
    fun get(id: Long): Department
    fun getBatch(ids: List<Long>): List<Department>
    fun list(page: Int, pageSize: Int): Page<Department>
    fun update(id: Long, entity: Department): Department
    fun updateBatch(entities: List<Department>): List<Department>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
    data class CreateCommand(
        val name: String,
        val departmentNumber: String,
        val superiorId: Long?,
        val sortOrder: Int,
        val director: String?,
        val contactPhone: String?,
    )

    data class UpdateCommand(
        val name: String?,
        val departmentNumber: String?,
        val superiorId: Long?,
        val sortOrder: Int?,
        val director: String?,
        val contactPhone: String?,
    )

    fun create(command: CreateCommand): Department

    fun createDepartments(commands: List<CreateCommand>): List<Department>

    fun listAll(): List<Department>

    fun update(id: Long, command: UpdateCommand): Department

    fun updateBatch(ids: List<Long>, command: UpdateCommand): List<Department>

    fun deleteDepartment(id: Long)

    fun deleteDepartments(ids: List<Long>)
}
