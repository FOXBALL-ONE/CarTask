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
/** 基于 JPA 的门禁授权记录服务。 */
class AccessControlServiceImpl(
    private val repository: AccessControlRepository,
    private val departmentRepository: DepartmentRepository,
    private val accessControlTypeRepository: AccessControlTypeRepository,
    private val scopeGuard: ScopeGuard,
    private val auditService: AuditService? = null,
) : AccessControlService {
    @Transactional
    /**
     * create：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
    /**
     * createBatch：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entities 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun createBatch(entities: List<AccessControl>): List<AccessControl> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        require(entities.all { entityId(it) == null }) { "创建记录时不能指定 ID" }
        val scope = scopeGuard.currentScope()
        // 同一批里自己撞自己也要拦：逐条查库查不到还没有落库的兄弟行。
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
    /**
     * get：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun get(id: Long): AccessControl = scopeGuard.requireVisibleAccessControl(
        repository.findById(id).orElse(null),
        scopeGuard.currentScope(),
        "记录不存在",
    ).withAssociationsLoaded()

    @Transactional
    /**
     * getBatch：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param ids 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun getBatch(ids: List<Long>): List<AccessControl> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val scope = scopeGuard.currentScope()
        // 范围外的行在这里就被摘掉，与「不存在」共用同一句错误，避免用响应差异探测别的部门。
        val recordsById = repository.findAllById(distinctIds)
            .filter { scopeGuard.accessControlVisible(it, scope) }
            .associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it).withAssociationsLoaded() }
    }

    @Transactional
    /**
     * list：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param page 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun list(page: Int, pageSize: Int): Page<AccessControl> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val pageable = PageRequest.of(page - 1, pageSize)
        val scope = scopeGuard.currentScope()
        val found = when (scope.kind) {
            ScopeKind.ALL -> repository.findAll(pageable)
            // 本模块服务的是主数据管理场景，普通用户本来就没有这些接口的权限。
            ScopeKind.SELF -> Page.empty(pageable)
            ScopeKind.DEPARTMENTS ->
                if (scope.departmentIds.isEmpty()) Page.empty(pageable)
                else repository.findByDepartment_IdIn(scope.departmentIds, pageable)
        }
        return found.map { it.withAssociationsLoaded() }
    }

    @Transactional
    /**
     * update：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
        // copyEditableProperties 是逐属性覆盖，会把请求体里没带的 department 一并写成 null，
        // 所以先记下原值，再按「请求体没带就保持原样」的语义落回去。
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
    /**
     * updateBatch：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entities 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
    /**
     * review：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param approved 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
    /**
     * synchronize：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun synchronize(id: Long): AccessControl {
        val current = scopeGuard.requireVisibleAccessControl(
            repository.findById(id).orElse(null),
            scopeGuard.currentScope(),
            "记录不存在",
        )
        if (current.reviewStatus != AccessControl.ReviewStatus.APPROVED || current.synchronizedLoading) {
            throw AccessDeniedException("只有未同步的已审核授权可以下发")
        }
        // 用 BusinessException 而不是 IllegalStateException：后者在 GlobalExceptionHandler 里没有
        // 专用处理器，会落到兜底分支变成 500 + 空消息，用户只看到「Internal Server Error」，
        // 完全不知道是功能还没做。501 才是这个事实的准确表达。
        throw BusinessException(HttpStatus.NOT_IMPLEMENTED, "门禁设备同步尚未接入，不能标记为已同步")
    }

    @Transactional
    /**
     * delete：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun delete(id: Long) {
        throw AccessDeniedException("门禁授权不允许物理删除")
    }

    @Transactional
    /**
     * deleteBatch：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param ids 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun deleteBatch(ids: List<Long>) {
        throw AccessDeniedException("门禁授权不允许物理删除")
    }

    /**
     * entityId：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * copyEditableProperties：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param source 参与本次处理的输入参数。
     * @param target 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * actorName：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun actorName(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal)?.username
            ?: throw AccessDeniedException("缺少有效的审核人上下文")

    /**
     * 把请求体里的部门引用换成受管实体。
     *
     * 反序列化出来的 `Department` 通常只有 `{id: 1}`，name / departmentNumber 这些 lateinit
     * 字段全是空的。原样存下去，之后任何一次读取（包括序列化响应）都会在
     * 「lateinit property departmentNumber has not been initialized」上抛异常——
     * 这正是 `POST /api/access-controls` 一直返回 500、但记录其实已经落库的原因。
     * 顺手也把「部门不存在」挡在写库之前。
     *
     * @param fallback 请求体没带部门时保留的部门（更新场景传原值，创建场景传 null）。
     */
    private fun resolveDepartment(source: AccessControl, fallback: Department?): Department? {
        val departmentId = source.department?.id ?: return fallback
        return departmentRepository.findById(departmentId)
            .orElseThrow { IllegalArgumentException("部门不存在：$departmentId") }
    }

    /**
     * 把请求体里的授权类型引用换成受管实体；请求体没带就保持原值。
     *
     * 与部门完全同理，而且这里踩过：`copyEditableProperties` 逐属性覆盖，缺省即写 null，
     * 反序列化出来的 `AccessControlType` 又只有 id。实测过一次 GET→PUT 原样提交之后
     * `access_control_type_id` 被静默清空（只建不改的记录类型还在，被 PUT 过的那条没了）。
     * 传进来的 id 不存在时在这里拒绝，而不是等 PostgreSQL 在 flush 时抛外键异常变成 500。
     */
    private fun resolvePermission(source: AccessControl, fallback: AccessControlType?): AccessControlType? {
        val permissionId = source.accessControlPermission?.id ?: return fallback
        return accessControlTypeRepository.findById(permissionId)
            .orElseThrow { IllegalArgumentException("门禁授权类型不存在：$permissionId") }
    }

    /**
     * 人员编号在这张表上是全局唯一（`access_control.person_number`）。
     *
     * 数据库约束当然是最后一道防线，但它的约束名是 Hibernate 生成的哈希串，
     * [top.foxball.cartask.handler.GlobalExceptionHandler] 没法按名字映射，落到兜底就是
     * 500 + 空消息。所以先查一次，给出能看懂的错误。
     *
     * @param excludeId 更新场景传自身的 id，避免把自己判成重复。
     */
    private fun requirePersonNumberAvailable(personNumber: String?, excludeId: Long?) {
        if (personNumber.isNullOrBlank()) return
        val existing = repository.findByPersonNumber(personNumber) ?: return
        require(entityId(existing) == excludeId) { "人员编号已存在" }
    }

    /**
     * 写路径：目标部门必须在当前范围内，且受限范围下必须显式指定部门。
     *
     * 不强制指定的话，部门管理能造出一条 department_id 为空的记录——按 fail-closed 口径
     * 那条记录对自己同样不可见，等于凭空产生一条谁也管不到的数据。
     */
    private fun requireManageableDepartment(entity: AccessControl, scope: DataScope) {
        if (scope.unrestricted) return
        require(entity.department?.id != null) { "必须指定部门" }
        scopeGuard.requireDepartmentAllowed(entity.department?.id, scope)
    }

    /**
     * 返回前先把两个关联摸一下，让它们在事务内完成初始化。
     *
     * 响应里要带部门名和授权类型名，而控制器拼响应时事务已经结束。不在这里初始化的话，
     * 序列化就会依赖 `spring.jpa.open-in-view`（默认开着，但那是隐式依赖，关掉之后才会在线上暴露成 500）。
     */
    private fun AccessControl.withAssociationsLoaded(): AccessControl = apply {
        department?.name
        accessControlPermission?.accessControlName
    }

    /**
     * requireValidAuthorizationPeriod：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
