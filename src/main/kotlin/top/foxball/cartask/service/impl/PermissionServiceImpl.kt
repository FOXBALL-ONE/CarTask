package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.beans.BeanWrapperImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.SecurityPermission
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.service.PermissionService

@Service
/** 基于 JPA 的权限字典服务。 */
class PermissionServiceImpl(
    private val repository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val tokenSessionRepository: RedisTokenSessionRepository,
) : PermissionService {
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('permission:manage')")
    override fun create(entity: Permission): Permission {
        require(entityId(entity) == null) { "创建记录时不能指定 ID" }
        normalizePermissionCode(entity)
        return repository.save(entity)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('permission:manage')")
    override fun createBatch(entities: List<Permission>): List<Permission> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        require(entities.all { entityId(it) == null }) { "创建记录时不能指定 ID" }
        entities.forEach(::normalizePermissionCode)
        return repository.saveAll(entities)
    }

    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('permission:read')")
    override fun get(id: Long): Permission = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('permission:read')")
    override fun getBatch(ids: List<Long>): List<Permission> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val recordsById = repository.findAllById(distinctIds).associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it) }
    }

    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('permission:read')")
    override fun list(page: Int, pageSize: Int): Page<Permission> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return repository.findAll(PageRequest.of(page - 1, pageSize))
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('permission:manage')")
    override fun update(id: Long, entity: Permission): Permission {
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        val previousCode = SecurityPermission.normalize(current.code)
        copyEditableProperties(entity, current)
        normalizePermissionCode(current)
        requireGovernancePermissionChange(previousCode, current)
        revokeRoleSessionsUsingPermission(id)
        return repository.save(current)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('permission:manage')")
    override fun updateBatch(entities: List<Permission>): List<Permission> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更新记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更新记录的 ID 不能重复" }
        val currentById = repository.findAllById(ids.filterNotNull()).associateBy { entityId(it) }
        val missingIds = ids.filterNotNull().filterNot(currentById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        val previousCodes = currentById.mapValues { (_, permission) -> SecurityPermission.normalize(permission.code) }
        val updated = entities.map { incoming ->
            val current = currentById.getValue(entityId(incoming))
            copyEditableProperties(incoming, current)
            normalizePermissionCode(current)
            requireGovernancePermissionChange(previousCodes.getValue(entityId(incoming)!!), current)
            current
        }
        updated.forEach { revokeRoleSessionsUsingPermission(entityId(it)!!) }
        return repository.saveAll(updated)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('permission:manage')")
    override fun delete(id: Long) {
        require(id > 0) { "ID 必须大于 0" }
        val permission = repository.findById(id).orElseThrow { IllegalArgumentException("记录不存在: $id") }
        requireNotAssigned(permission.id!!)
        repository.delete(permission)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('permission:manage')")
    override fun deleteBatch(ids: List<Long>) {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val records = repository.findAllById(distinctIds)
        val recordsById = records.associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        distinctIds.forEach(::requireNotAssigned)
        repository.deleteAll(distinctIds.map(recordsById::getValue))
    }

    private fun entityId(entity: Permission): Long? {
        var type: Class<*>? = entity.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField("id")
                field.isAccessible = true
                return (field.get(entity) as? Number)?.toLong()
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw IllegalArgumentException("实体缺少 Long 类型的 id 属性")
    }

    private fun copyEditableProperties(source: Permission, target: Permission) {
        val sourceWrapper = BeanWrapperImpl(source)
        val targetWrapper = BeanWrapperImpl(target)
        val protectedProperties = setOf("id", "createdAt", "updatedAt", "updateTime", "passwordHash")
        targetWrapper.propertyDescriptors
            .asSequence()
            .map { it.name }
            .filterNot(protectedProperties::contains)
            .filter { sourceWrapper.isReadableProperty(it) && targetWrapper.isWritableProperty(it) }
            .forEach { property ->
                targetWrapper.setPropertyValue(property, sourceWrapper.getPropertyValue(property))
            }
    }

    private fun normalizePermissionCode(entity: Permission) {
        entity.code = SecurityPermission.normalize(entity.code)
    }

    private fun requireGovernancePermissionChange(previousCode: String, current: Permission) {
        val superAdmin = roleRepository.findByNameIgnoreCase("SUPER_ADMIN") ?: return
        if (superAdmin.permissions.none { it.id == current.id }) return
        val nextCode = SecurityPermission.normalize(current.code)
        require(current.enabled && nextCode == previousCode) {
            "超级管理员已使用的治理权限不能禁用或改码"
        }
    }

    private fun requireNotAssigned(permissionId: Long) {
        require(roleRepository.findAll().none { role -> role.permissions.any { it.id == permissionId } }) {
            "已关联角色的权限不能删除"
        }
    }

    private fun revokeRoleSessionsUsingPermission(permissionId: Long) {
        val roleNames = roleRepository.findAll()
            .filter { role -> role.permissions.any { it.id == permissionId } }
            .map { it.name }
            .distinct()
        userRepository.findAllByRoleIn(roleNames)
            .mapNotNull { it.id }
            .forEach(tokenSessionRepository::incrementTokenVersion)
    }
}
