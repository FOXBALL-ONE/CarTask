package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.beans.BeanWrapperImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.SecurityPermission
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.service.RoleService

@Service
/** 基于 JPA 的角色与权限集合服务。 */
class RoleServiceImpl(
    private val repository: RoleRepository,
    private val userRepository: UserRepository,
    private val tokenSessionRepository: RedisTokenSessionRepository,
) : RoleService {
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    override fun create(entity: Role): Role {
        require(entityId(entity) == null) { "创建记录时不能指定 ID" }
        normalizeRoleName(entity)
        requireEnabledRole(entity)
        requireSuperAdminGovernance(entity)
        return repository.save(entity)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    override fun createBatch(entities: List<Role>): List<Role> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        require(entities.all { entityId(it) == null }) { "创建记录时不能指定 ID" }
        entities.forEach {
            normalizeRoleName(it)
            requireEnabledRole(it)
            requireSuperAdminGovernance(it)
        }
        return repository.saveAll(entities)
    }

    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    override fun get(id: Long): Role = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    override fun getBatch(ids: List<Long>): List<Role> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val recordsById = repository.findAllById(distinctIds).associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it) }
    }

    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    override fun list(page: Int, pageSize: Int): Page<Role> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return repository.findAll(PageRequest.of(page - 1, pageSize))
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    override fun update(id: Long, entity: Role): Role {
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        requireStableRole(current, entity)
        copyEditableProperties(entity, current)
        normalizeRoleName(current)
        requireSuperAdminGovernance(current)
        revokeRoleSessions(current.name)
        return repository.save(current)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    override fun updateBatch(entities: List<Role>): List<Role> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更新记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更新记录的 ID 不能重复" }
        val currentById = repository.findAllById(ids.filterNotNull()).associateBy { entityId(it) }
        val missingIds = ids.filterNotNull().filterNot(currentById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        val updated = entities.map { incoming ->
            val current = currentById.getValue(entityId(incoming))
            requireStableRole(current, incoming)
            copyEditableProperties(incoming, current)
            normalizeRoleName(current)
            requireSuperAdminGovernance(current)
            current
        }
        updated.forEach { revokeRoleSessions(it.name) }
        return repository.saveAll(updated)
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    override fun delete(id: Long) {
        require(id > 0) { "ID 必须大于 0" }
        val role = repository.findById(id).orElseThrow { IllegalArgumentException("记录不存在: $id") }
        throw org.springframework.security.access.AccessDeniedException("系统角色不允许删除: ${SecurityRole.normalize(role.name)}")
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    override fun deleteBatch(ids: List<Long>) {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val records = repository.findAllById(distinctIds)
        val recordsById = records.associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        throw org.springframework.security.access.AccessDeniedException("系统角色不允许删除: ${records.joinToString(",") { SecurityRole.normalize(it.name) }}")
    }

    private fun entityId(entity: Role): Long? {
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

    private fun copyEditableProperties(source: Role, target: Role) {
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

    private fun normalizeRoleName(entity: Role) {
        entity.name = SecurityRole.normalize(entity.name)
    }

    private fun requireEnabledRole(entity: Role) {
        require(entity.enabled) { "系统角色必须保持启用" }
    }

    private fun requireStableRole(current: Role, incoming: Role) {
        val currentName = SecurityRole.normalize(current.name)
        val incomingName = SecurityRole.normalize(incoming.name)
        require(currentName == incomingName) { "系统角色名称不可变更" }
        require(incoming.enabled) { "系统角色不能禁用" }
    }

    private fun requireSuperAdminGovernance(role: Role) {
        if (SecurityRole.normalize(role.name) != "SUPER_ADMIN") return
        val enabledCodes = role.permissions.asSequence()
            .filter { it.enabled }
            .map { SecurityPermission.normalize(it.code) }
            .toSet()
        require(SecurityRole.SUPER_ADMIN_GOVERNANCE_PERMISSIONS.all(enabledCodes::contains)) {
            "超级管理员必须保留完整的安全治理权限"
        }
    }

    private fun revokeRoleSessions(role: String) {
        userRepository.findAllByRoleIn(listOf(SecurityRole.normalize(role)))
            .mapNotNull { it.id }
            .forEach(tokenSessionRepository::incrementTokenVersion)
    }
}
