package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.BeanWrapperImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.handler.BusinessException
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.repository.AccessControlTypeRepository
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.scope.ScopeKind
import top.foxball.cartask.service.AccessControlService

@Service

class AccessControlServiceImpl(
    private val repository: AccessControlRepository,
    private val departmentRepository: DepartmentRepository,
    private val accessControlTypeRepository: AccessControlTypeRepository,
    private val scopeGuard: ScopeGuard,
    private val auditService: AuditService? = null,
) : AccessControlService {
    @Transactional
    
    
    override fun create(entity: AccessControl): AccessControl {
        require(entityId(entity) == null) { "创建记录时不能指定 ID" }
        requireValidAuthorizationPeriod(entity)
        val scope = scopeGuard.currentScope()
        entity.department = resolveDepartment(entity, null)
        entity.accessControlPermission = resolvePermission(entity, null)
        requirePersonNumberAvailable(entity.personNumber, null)
        requireManageableDepartment(entity, scope)
        entity.reviewStatus = AccessControl.ReviewStatus.PENDING
        entity.synchronizedLoading = false
        val saved = repository.save(entity)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_CONTROL_CREATED,
                "access_control",
                saved.id?.toString(),
                targetSummary = mapOf("name" to saved.name, "department_id" to saved.department?.id),
                afterData = mapOf(
                    "review_status" to saved.reviewStatus.name,
                    "synchronized" to saved.synchronizedLoading
                ),
            ),
        )
        return saved.withAssociationsLoaded()
    }
    
    @Transactional
    
    
    override fun createBatch(entities: List<AccessControl>): List<AccessControl> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        require(entities.all { entityId(it) == null }) { "创建记录时不能指定 ID" }
        val scope = scopeGuard.currentScope()
        val numbers = entities.mapNotNull { it.personNumber?.takeIf(String::isNotBlank) }
        require(numbers.distinct().size == numbers.size) { "人员编号不能重复" }
        entities.forEach {
            requireValidAuthorizationPeriod(it)
            it.department = resolveDepartment(it, null)
            it.accessControlPermission = resolvePermission(it, null)
            requirePersonNumberAvailable(it.personNumber, null)
            requireManageableDepartment(it, scope)
            it.reviewStatus = AccessControl.ReviewStatus.PENDING
            it.synchronizedLoading = false
        }
        val saved = repository.saveAll(entities)
        saved.forEach {
            auditService?.record(
                AuditCommand(
                    AuditAction.ACCESS_CONTROL_CREATED,
                    "access_control",
                    it.id?.toString(),
                    targetSummary = mapOf("name" to it.name, "department_id" to it.department?.id),
                    afterData = mapOf(
                        "review_status" to it.reviewStatus.name,
                        "synchronized" to it.synchronizedLoading
                    ),
                ),
            )
        }
        return saved.map { it.withAssociationsLoaded() }
    }
    
    @Transactional
    
    
    override fun get(id: Long): AccessControl = scopeGuard.requireVisibleAccessControl(
        repository.findById(id).orElse(null),
        scopeGuard.currentScope(),
        "记录不存在",
    ).withAssociationsLoaded()
    
    @Transactional
    
    
    override fun getBatch(ids: List<Long>): List<AccessControl> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val scope = scopeGuard.currentScope()
        val recordsById = repository.findAllById(distinctIds)
            .filter { scopeGuard.accessControlVisible(it, scope) }
            .associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it).withAssociationsLoaded() }
    }
    
    @Transactional
    
    
    override fun list(page: Int, pageSize: Int): Page<AccessControl> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val pageable = PageRequest.of(page - 1, pageSize)
        val scope = scopeGuard.currentScope()
        val found = when (scope.kind) {
            ScopeKind.ALL -> repository.findAll(pageable)
            ScopeKind.SELF -> Page.empty(pageable)
            ScopeKind.DEPARTMENTS ->
                if (scope.departmentIds.isEmpty()) Page.empty(pageable)
                else repository.findByDepartment_IdIn(scope.departmentIds, pageable)
        }
        return found.map { it.withAssociationsLoaded() }
    }
    
    @Transactional
    
    
    override fun update(id: Long, entity: AccessControl): AccessControl {
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val scope = scopeGuard.currentScope()
        val current = scopeGuard.requireVisibleAccessControl(
            repository.findById(id).orElse(null), scope, "记录不存在",
        )
        if (current.reviewStatus == AccessControl.ReviewStatus.REJECTED) {
            throw AccessDeniedException("已驳回的门禁申请必须重新提交")
        }
        val before = mapOf("review_status" to current.reviewStatus.name, "synchronized" to current.synchronizedLoading)
        val previousDepartment = current.department
        val previousPermission = current.accessControlPermission
        copyEditableProperties(entity, current)
        current.department = resolveDepartment(entity, previousDepartment)
        current.accessControlPermission = resolvePermission(entity, previousPermission)
        requirePersonNumberAvailable(current.personNumber, id)
        requireManageableDepartment(current, scope)
        requireValidAuthorizationPeriod(current)
        if (current.reviewStatus == AccessControl.ReviewStatus.APPROVED) {
            current.reviewStatus = AccessControl.ReviewStatus.PENDING
            current.synchronizedLoading = false
        }
        val saved = repository.save(current)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_CONTROL_UPDATED,
                "access_control",
                id.toString(),
                beforeData = before,
                afterData = mapOf(
                    "review_status" to saved.reviewStatus.name,
                    "synchronized" to saved.synchronizedLoading
                ),
            ),
        )
        return saved.withAssociationsLoaded()
    }
    
    @Transactional
    
    
    override fun updateBatch(entities: List<AccessControl>): List<AccessControl> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更新记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更新记录的 ID 不能重复" }
        val scope = scopeGuard.currentScope()
        val currentById = repository.findAllById(ids.filterNotNull())
            .filter { scopeGuard.accessControlVisible(it, scope) }
            .associateBy { entityId(it) }
        val missingIds = ids.filterNotNull().filterNot(currentById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        val beforeById = currentById.mapValues { (_, accessControl) ->
            mapOf(
                "review_status" to accessControl.reviewStatus.name,
                "synchronized" to accessControl.synchronizedLoading
            )
        }
        val updated = entities.map { incoming ->
            val current = currentById.getValue(entityId(incoming))
            if (current.reviewStatus == AccessControl.ReviewStatus.REJECTED) {
                throw AccessDeniedException("已驳回的门禁申请必须重新提交")
            }
            val previousDepartment = current.department
            val previousPermission = current.accessControlPermission
            copyEditableProperties(incoming, current)
            current.department = resolveDepartment(incoming, previousDepartment)
            current.accessControlPermission = resolvePermission(incoming, previousPermission)
            requirePersonNumberAvailable(current.personNumber, entityId(current))
            requireManageableDepartment(current, scope)
            requireValidAuthorizationPeriod(current)
            if (current.reviewStatus == AccessControl.ReviewStatus.APPROVED) {
                current.reviewStatus = AccessControl.ReviewStatus.PENDING
                current.synchronizedLoading = false
            }
            current
        }
        val saved = repository.saveAll(updated)
        saved.forEach { accessControl ->
            auditService?.record(
                AuditCommand(
                    AuditAction.ACCESS_CONTROL_UPDATED,
                    "access_control",
                    accessControl.id?.toString(),
                    beforeData = beforeById[accessControl.id],
                    afterData = mapOf(
                        "review_status" to accessControl.reviewStatus.name,
                        "synchronized" to accessControl.synchronizedLoading
                    ),
                ),
            )
        }
        return saved.map { it.withAssociationsLoaded() }
    }
    
    @Transactional
    
    
    override fun review(id: Long, approved: Boolean, reason: String): AccessControl {
        require(reason.isNotBlank()) { "审核原因不能为空" }
        val current = scopeGuard.requireVisibleAccessControl(
            repository.findById(id).orElse(null),
            scopeGuard.currentScope(),
            "记录不存在",
        )
        if (current.reviewStatus != AccessControl.ReviewStatus.PENDING || current.synchronizedLoading) {
            throw AccessDeniedException("当前门禁申请状态不允许审核")
        }
        val actor = actorName()
        val before = current.reviewStatus.name
        current.reviewStatus =
            if (approved) AccessControl.ReviewStatus.APPROVED else AccessControl.ReviewStatus.REJECTED
        logger.warn(
            "门禁授权审核，accessControlId={}, actor={}, approved={}, reason={}",
            id,
            actor,
            approved,
            reason,
        )
        val saved = repository.save(current)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_CONTROL_REVIEWED,
                "access_control",
                id.toString(),
                reason = reason,
                beforeData = mapOf("review_status" to before),
                afterData = mapOf("review_status" to saved.reviewStatus.name),
            ),
        )
        return saved.withAssociationsLoaded()
    }
    
    @Transactional
    
    
    override fun synchronize(id: Long): AccessControl {
        val current = scopeGuard.requireVisibleAccessControl(
            repository.findById(id).orElse(null),
            scopeGuard.currentScope(),
            "记录不存在",
        )
        if (current.reviewStatus != AccessControl.ReviewStatus.APPROVED || current.synchronizedLoading) {
            throw AccessDeniedException("只有未同步的已审核授权可以下发")
        }
        throw BusinessException(HttpStatus.NOT_IMPLEMENTED, "门禁设备同步尚未接入，不能标记为已同步")
    }
    
    @Transactional
    
    
    override fun delete(id: Long) {
        throw AccessDeniedException("门禁授权不允许物理删除")
    }
    
    @Transactional
    
    
    override fun deleteBatch(ids: List<Long>) {
        throw AccessDeniedException("门禁授权不允许物理删除")
    }
    
    
    private fun entityId(entity: AccessControl): Long? {
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
    
    
    private fun copyEditableProperties(source: AccessControl, target: AccessControl) {
        val sourceWrapper = BeanWrapperImpl(source)
        val targetWrapper = BeanWrapperImpl(target)
        val protectedProperties = setOf(
            "id", "createdAt", "updatedAt", "updateTime", "passwordHash", "reviewStatus", "synchronizedLoading",
        )
        targetWrapper.propertyDescriptors
            .asSequence()
            .map { it.name }
            .filterNot(protectedProperties::contains)
            .filter { sourceWrapper.isReadableProperty(it) && targetWrapper.isWritableProperty(it) }
            .forEach { property ->
                targetWrapper.setPropertyValue(property, sourceWrapper.getPropertyValue(property))
            }
    }
    
    
    private fun actorName(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal)?.username
            ?: throw AccessDeniedException("缺少有效的审核人上下文")
    
    
    private fun resolveDepartment(source: AccessControl, fallback: Department?): Department? {
        val departmentId = source.department?.id ?: return fallback
        return departmentRepository.findById(departmentId)
            .orElseThrow { IllegalArgumentException("部门不存在：$departmentId") }
    }
    
    
    private fun resolvePermission(source: AccessControl, fallback: AccessControlType?): AccessControlType? {
        val permissionId = source.accessControlPermission?.id ?: return fallback
        return accessControlTypeRepository.findById(permissionId)
            .orElseThrow { IllegalArgumentException("门禁授权类型不存在：$permissionId") }
    }
    
    
    private fun requirePersonNumberAvailable(personNumber: String?, excludeId: Long?) {
        if (personNumber.isNullOrBlank()) return
        val existing = repository.findByPersonNumber(personNumber) ?: return
        require(entityId(existing) == excludeId) { "人员编号已存在" }
    }
    
    
    private fun requireManageableDepartment(entity: AccessControl, scope: DataScope) {
        if (scope.unrestricted) return
        require(entity.department?.id != null) { "必须指定部门" }
        scopeGuard.requireDepartmentAllowed(entity.department?.id, scope)
    }
    
    
    private fun AccessControl.withAssociationsLoaded(): AccessControl = apply {
        department?.name
        accessControlPermission?.accessControlName
    }
    
    
    private fun requireValidAuthorizationPeriod(entity: AccessControl) {
        val start = entity.upTime
        val end = entity.endTime
        require(end == null || (start != null && end.isAfter(start))) {
            "授权结束时间必须晚于开始时间"
        }
    }
    
    private companion object {
        val logger = LoggerFactory.getLogger(AccessControlServiceImpl::class.java)
    }
}
