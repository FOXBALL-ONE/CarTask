package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Department
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.service.DepartmentService

@Service
/** 基于 JPA 的组织部门服务。 */
class DepartmentServiceImpl(
    private val departmentRepository: DepartmentRepository,
) : JpaCrudService<Department>(departmentRepository), DepartmentService {
    @Transactional
    override fun create(command: DepartmentService.CreateCommand): Department {
        val department = Department()
        applyCreateCommand(command, department)
        return departmentRepository.save(department)
    }

    @Transactional
    override fun createDepartments(commands: List<DepartmentService.CreateCommand>): List<Department> {
        require(commands.isNotEmpty()) { "部门列表不能为空" }
        val codes = commands.map { it.departmentNumber.trim() }
        require(codes.distinct().size == codes.size) { "部门编码不能重复" }
        val departments = commands.map { command ->
            Department().also { applyCreateCommand(command, it) }
        }
        return departmentRepository.saveAll(departments)
    }

    @Transactional
    override fun listAll(): List<Department> = departmentRepository.findAll(
        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name"), Sort.Order.asc("id")),
    )

    @Transactional
    override fun update(id: Long, command: DepartmentService.UpdateCommand): Department {
        val department = departmentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("部门不存在: $id") }
        applyUpdateCommand(id, command, department)
        return departmentRepository.save(department)
    }

    @Transactional
    override fun updateBatch(ids: List<Long>, command: DepartmentService.UpdateCommand): List<Department> {
        require(ids.isNotEmpty()) { "部门 ID 列表不能为空" }
        require(ids.all { it > 0 }) { "部门 ID 必须大于 0" }
        require(ids.distinct().size == ids.size) { "部门 ID 不能重复" }
        val departments = departmentRepository.findAllById(ids)
        val foundIds = departments.mapNotNull { it.id }.toSet()
        val missingIds = ids.filterNot(foundIds::contains)
        require(missingIds.isEmpty()) { "部分部门不存在: ${missingIds.joinToString(",")}" }
        departments.forEach { applyUpdateCommand(it.id!!, command, it) }
        return departmentRepository.saveAll(departments)
    }

    @Transactional
    override fun deleteDepartment(id: Long) {
        require(id > 0) { "部门 ID 必须大于 0" }
        require(departmentRepository.existsById(id)) { "部门不存在: $id" }
        require(!departmentRepository.existsBySuperiorId(id)) { "部门存在下级部门，请先删除或移动下级部门" }
        departmentRepository.deleteById(id)
    }

    @Transactional
    override fun deleteDepartments(ids: List<Long>) {
        require(ids.isNotEmpty()) { "部门 ID 列表不能为空" }
        require(ids.all { it > 0 }) { "部门 ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val departments = departmentRepository.findAllById(distinctIds)
        val foundIds = departments.mapNotNull { it.id }.toSet()
        val missingIds = distinctIds.filterNot(foundIds::contains)
        require(missingIds.isEmpty()) { "部分部门不存在: ${missingIds.joinToString(",")}" }
        val selectedIds = distinctIds.toSet()
        val outsideChildExists = departmentRepository.findAll().any { department ->
            department.superior?.id in selectedIds && department.id !in selectedIds
        }
        require(!outsideChildExists) { "选中的部门存在未选中的下级部门，请先删除或移动下级部门" }
        departmentRepository.deleteAll(departments)
    }

    private fun applyCreateCommand(command: DepartmentService.CreateCommand, department: Department) {
        require(command.name.isNotBlank()) { "部门名称不能为空" }
        require(command.departmentNumber.isNotBlank()) { "部门编码不能为空" }
        department.name = command.name.trim()
        department.departmentNumber = command.departmentNumber.trim()
        department.superior = command.superiorId?.let { superiorId ->
            require(superiorId > 0) { "上级部门 ID 必须大于 0" }
            departmentRepository.findById(superiorId)
                .orElseThrow { IllegalArgumentException("部门不存在: $superiorId") }
        }
        department.sortOrder = command.sortOrder
        department.director = command.director?.trim()?.takeIf(String::isNotEmpty)
        department.contactPhone = command.contactPhone?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun applyUpdateCommand(
        id: Long,
        command: DepartmentService.UpdateCommand,
        department: Department,
    ) {
        command.name?.let {
            require(it.isNotBlank()) { "部门名称不能为空" }
            department.name = it.trim()
        }
        command.departmentNumber?.let {
            require(it.isNotBlank()) { "部门编码不能为空" }
            department.departmentNumber = it.trim()
        }
        command.superiorId?.let { superiorId ->
            if (superiorId == 0L) {
                department.superior = null
            } else {
                require(superiorId > 0) { "上级部门 ID 必须大于等于 0" }
                require(superiorId != id) { "部门不能将自身设置为上级部门" }
                val childrenBySuperior = departmentRepository.findAll().groupBy { it.superior?.id }
                val descendantIds = mutableSetOf<Long>()
                val pending = ArrayDeque<Long>()
                pending.add(id)
                while (pending.isNotEmpty()) {
                    childrenBySuperior[pending.removeFirst()].orEmpty().mapNotNull { it.id }.forEach { childId ->
                        if (descendantIds.add(childId)) {
                            pending.add(childId)
                        }
                    }
                }
                require(superiorId !in descendantIds) { "部门不能移动到自己的下级部门" }
                department.superior = departmentRepository.findById(superiorId)
                    .orElseThrow { IllegalArgumentException("部门不存在: $superiorId") }
            }
        }
        command.sortOrder?.let { department.sortOrder = it }
        if (command.director != null) {
            department.director = command.director.trim().takeIf(String::isNotEmpty)
        }
        if (command.contactPhone != null) {
            department.contactPhone = command.contactPhone.trim().takeIf(String::isNotEmpty)
        }
    }
}
