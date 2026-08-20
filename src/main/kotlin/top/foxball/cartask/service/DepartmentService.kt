package top.foxball.cartask.service

import top.foxball.cartask.entity.Department

/** 组织部门的业务服务。 */
interface DepartmentService : CrudService<Department> {
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
