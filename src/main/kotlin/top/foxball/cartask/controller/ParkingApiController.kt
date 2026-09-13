package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.springframework.http.ResponseEntity
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.GateDeleteRequest
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingSpot
import top.foxball.cartask.entity.Position
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.logging.LogVisibilityService
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.GateDeleteRequestRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.repository.PersonAccessRecordRepository
import top.foxball.cartask.repository.AuditEventRepository
import top.foxball.cartask.repository.OperationLogRepository
import top.foxball.cartask.entity.OperationLog
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.service.DashboardSpotStatsService
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.service.PositionService
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.GatePersonFields
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import top.foxball.cartask.shared.VehicleInspection

/** 文档 v1 前端接口的兼容层。缺少独立领域表的展示资源在此保持进程内状态。 */
@RestController
@RequestMapping("/api")
class ParkingApiController(
    private val responseBuilder: ResponseBuilder,
    private val departmentService: DepartmentService,
    private val positionService: PositionService,
    private val accessRecordRepository: AccessRecordRepository,
    private val ownerRepository: ParkingOwnerRepository,
    private val spotRepository: ParkingSpotRepository,
    private val plateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
    private val gateDeleteRequestRepository: GateDeleteRequestRepository,
    private val personAccessRecordRepository: PersonAccessRecordRepository,
    private val fileService: FileService,
    private val auditEventRepository: AuditEventRepository,
    private val operationLogRepository: OperationLogRepository,
    private val violationRecordRepository: ViolationRecordRepository,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService,
    private val logVisibilityService: LogVisibilityService,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val scopeGuard: ScopeGuard,
    private val dashboardSpotStatsService: DashboardSpotStatsService,
) {
    @GetMapping("/depts")
    @PreAuthorize("hasAuthority('department:read')")
    fun listDepartments(): ResponseEntity<Response> {
        data class DepartmentData(
            val id: Long,
            val name: String,
            val code: String,
            val parent: Long?,
            val sort: Int,
            val leader: String?,
            val phone: String?,
            val status: Int,
        )
        val rs = departmentService.listAll().map {
            DepartmentData(requireNotNull(it.id), it.name, it.departmentNumber, it.superior?.id, it.sortOrder, it.director, it.contactPhone, it.status)
        }
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/depts")
    @PreAuthorize("hasAuthority('department:manage')")
    fun createDepartment(@RequestBody body: DocumentDepartmentRequest): ResponseEntity<Response> {
        require(body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val department = departmentService.create(DepartmentService.CreateCommand(
            requireNotNull(body.name), requireNotNull(body.code), body.parent, requireNotNull(body.sort) { "排序不能为空" }, body.leader, body.phone, requireNotNull(body.status) { "状态不能为空" }.also { require(it == 0 || it == 1) { "状态必须为 0 或 1" } },
        ))
        return responseBuilder.created().data(mapOf(
            "id" to department.id, "name" to department.name, "code" to department.departmentNumber,
            "parent" to department.superior?.id, "sort" to department.sortOrder, "leader" to department.director,
            "phone" to department.contactPhone, "status" to department.status,
        )).build()
    }

    @PutMapping("/depts/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    fun updateDepartment(@PathVariable id: Long, @RequestBody body: DocumentDepartmentRequest): ResponseEntity<Response> {
        require(body.status == null || body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val parentId = if (body.parentProvided) body.parent ?: 0L else null
        val department = departmentService.update(id, DepartmentService.UpdateCommand(body.name, body.code, parentId, body.sort, body.leader, body.phone, body.status))
        return responseBuilder.ok().data(mapOf(
            "id" to department.id, "name" to department.name, "code" to department.departmentNumber,
            "parent" to department.superior?.id, "sort" to department.sortOrder, "leader" to department.director,
            "phone" to department.contactPhone, "status" to department.status,
        )).build()
    }

    @DeleteMapping("/depts/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    fun deleteDepartment(@PathVariable id: Long): ResponseEntity<Response> {
        departmentService.deleteDepartment(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @GetMapping("/posts")
    @PreAuthorize("hasAuthority('position:read')")
    fun listPosts(): ResponseEntity<Response> {
        data class PostData(val id: Long, val name: String, val code: String, val sort: Int, val status: Int, val remark: String?)
        val allPosts = mutableListOf<Position>()
        var sourcePage = 1
        var sourceTotal = 0L
        do {
            val source = positionService.list(sourcePage, 100)
            allPosts += source.content
            sourceTotal = source.totalElements
            sourcePage++
        } while (allPosts.size < sourceTotal)
        val rs = allPosts.map { PostData(requireNotNull(it.id), it.name, it.codeNumber, it.orderNumber, if (it.status == Position.Status.Activity) 1 else 0, it.remark) }
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/posts")
    @PreAuthorize("hasAuthority('position:manage')")
    fun createPost(@RequestBody body: DocumentPostRequest): ResponseEntity<Response> {
        require(body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val position = Position().apply {
            name = requireNotNull(body.name)
            codeNumber = requireNotNull(body.code)
            orderNumber = requireNotNull(body.sort) { "排序不能为空" }
            status = if (requireNotNull(body.status) { "状态不能为空" } == 0) Position.Status.BANNED else Position.Status.Activity
            remark = body.remark
        }
        val saved = positionService.create(position)
        return responseBuilder.created().data(mapOf("id" to saved.id, "name" to saved.name, "code" to saved.codeNumber, "sort" to saved.orderNumber, "status" to if (saved.status == Position.Status.Activity) 1 else 0, "remark" to body.remark)).build()
    }

    @PutMapping("/posts/{id}")
    @PreAuthorize("hasAuthority('position:manage')")
    fun updatePost(@PathVariable id: Long, @RequestBody body: DocumentPostRequest): ResponseEntity<Response> {
        require(body.status == null || body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val current = positionService.get(id)
        val position = Position().apply {
            this.id = id
            name = body.name ?: current.name
            codeNumber = body.code ?: current.codeNumber
            orderNumber = body.sort ?: current.orderNumber
            status = body.status?.let { if (it == 0) Position.Status.BANNED else Position.Status.Activity } ?: current.status
            remark = body.remark ?: current.remark
        }
        val saved = positionService.update(id, position)
        return responseBuilder.ok().data(mapOf("id" to saved.id, "name" to saved.name, "code" to saved.codeNumber, "sort" to saved.orderNumber, "status" to if (saved.status == Position.Status.Activity) 1 else 0, "remark" to saved.remark)).build()
    }

    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasAuthority('position:manage')")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Response> {
        positionService.delete(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @GetMapping("/owners")
    @PreAuthorize("hasAuthority('owner:read')")
    fun listOwners(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) dept: String?,
        @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PageData(val items: List<StoredOwner>, val total: Int, val page: Int, @param:JsonProperty("pageSize") val pageSizeValue: Int)
        val scope = dataScopeResolver.current()
        // 范围谓词始终参与：客户端传的 dept 只能在此基础上继续收窄，不可能放宽范围。
        val visibleOwnerIds = if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
        val filtered = ownerRepository.findAll().filter {
            (visibleOwnerIds == null || requireNotNull(it.id) in visibleOwnerIds) &&
                (keyword.isNullOrBlank() || it.cardId.contains(keyword, true) || it.name.contains(keyword, true) || it.phone.contains(keyword, true)) &&
                (dept.isNullOrBlank() || it.dept == dept) && (status == null || it.status == status)
        }.sortedBy { it.id }.map { StoredOwner(requireNotNull(it.id), it.cardId, it.name, it.dept, it.phone, it.spotCount, it.plateCount, it.balance, it.status) }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size, page, pageSize)).build()
    }

    @PostMapping("/owners")
    @PreAuthorize("hasAuthority('owner:manage')")
    fun createOwner(@RequestBody body: OwnerRequest): ResponseEntity<Response> {
        val cardId = requireNotNull(body.cardId) { "车主卡号不能为空" }
        require(!ownerRepository.existsByCardId(cardId)) { "车主卡号已存在" }
        val dept = requireNotNull(body.dept) { "部门不能为空" }
        val scope = scopeGuard.currentScope()
        val deptCode = scopeQuerySupport.stampDepartmentCode(dept, null)
        // 写路径同样要校验：否则部门管理能造出一条自己看不见、却归属别的部门的记录。
        scopeGuard.requireDepartmentCodeAllowed(deptCode, scope)
        val owner = ParkingOwner().apply {
            this.cardId = cardId
            name = requireNotNull(body.name) { "姓名不能为空" }
            this.dept = dept
            phone = requireNotNull(body.phone) { "手机号不能为空" }
            spotCount = requireNotNull(body.spotCount) { "车位数量不能为空" }
            plateCount = requireNotNull(body.plateCount) { "车牌数量不能为空" }
            balance = body.balance ?: BigDecimal.ZERO
            status = requireNotNull(body.status) { "状态不能为空" }
            departmentCode = deptCode
        }
        require(owner.spotCount >= 0 && owner.plateCount >= 0) { "数量不能为负数" }
        require(owner.balance >= BigDecimal.ZERO) { "余额不能为负数" }
        require(owner.status == 0 || owner.status == 1) { "状态必须为 0 或 1" }
        val saved = ownerRepository.save(owner)
        return responseBuilder.created().data(StoredOwner(requireNotNull(saved.id), saved.cardId, saved.name, saved.dept, saved.phone, saved.spotCount, saved.plateCount, saved.balance, saved.status)).build()
    }

    @PutMapping("/owners/{id}")
    @PreAuthorize("hasAuthority('owner:manage')")
    fun updateOwner(@PathVariable id: Long, @RequestBody body: OwnerRequest): ResponseEntity<Response> {
        val scope = scopeGuard.currentScope()
        val owner = scopeGuard.requireVisibleRow(ownerRepository.findById(id).orElse(null), scope, "车主不存在")
        val previousName = owner.name
        body.cardId?.let { require(!ownerRepository.existsByCardIdAndIdNot(it, id)) { "车主卡号已存在" }; owner.cardId = it }
        body.name?.let { owner.name = it }
        body.dept?.let {
            val newCode = scopeQuerySupport.stampDepartmentCode(it, owner.departmentCode)
            // 既不能把本部门车主挪到范围外，也不能把范围外车主挪进来。
            scopeGuard.requireDepartmentCodeAllowed(newCode, scope)
            owner.dept = it
            owner.departmentCode = newCode
        }
        body.phone?.let { owner.phone = it }
        body.spotCount?.let { owner.spotCount = it }
        body.plateCount?.let { owner.plateCount = it }
        body.balance?.let { owner.balance = it }
        body.status?.let { owner.status = it }
        require(owner.spotCount >= 0 && owner.plateCount >= 0) { "数量不能为负数" }
        require(owner.balance >= BigDecimal.ZERO) { "余额不能为负数" }
        require(owner.status == 0 || owner.status == 1) { "状态必须为 0 或 1" }
        if (owner.name != previousName) {
            spotRepository.findAll().filter { it.owner == previousName }.forEach { it.owner = owner.name }
            plateRepository.findAll().filter { it.ownerId == id }.forEach { it.owner = owner.name }
            spotRepository.flush()
            plateRepository.flush()
        }
        val saved = ownerRepository.save(owner)
        return responseBuilder.ok().data(StoredOwner(requireNotNull(saved.id), saved.cardId, saved.name, saved.dept, saved.phone, saved.spotCount, saved.plateCount, saved.balance, saved.status)).build()
    }

    @DeleteMapping("/owners/{id}")
    @PreAuthorize("hasAuthority('owner:manage')")
    fun deleteOwner(@PathVariable id: Long): ResponseEntity<Response> {
        val owner = scopeGuard.requireVisibleRow(
            ownerRepository.findById(id).orElse(null),
            scopeGuard.currentScope(),
            "车主不存在",
        )
        require(plateRepository.findAll().none { it.ownerId == id } && spotRepository.findAll().none { it.owner == owner.name }) {
            "车主仍有关联车位或车牌"
        }
        ownerRepository.deleteById(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @PostMapping("/owners/{id}/recharge")
    @PreAuthorize("hasAuthority('owner:manage')")
    fun rechargeOwner(@PathVariable id: Long, @RequestBody body: RechargeRequest): ResponseEntity<Response> {
        val old = scopeGuard.requireVisibleRow(
            ownerRepository.findById(id).orElse(null),
            scopeGuard.currentScope(),
            "车主不存在",
        )
        val amount = body.amount ?: throw IllegalArgumentException("充值金额不能为空")
        require(amount > BigDecimal.ZERO) { "充值金额必须为正数" }
        old.balance += amount
        val updated = ownerRepository.save(old)
        return responseBuilder.ok().message("充值成功").data(mapOf("id" to id, "balance" to updated.balance)).build()
    }

    @GetMapping("/spots")
    @PreAuthorize("hasAuthority('spot:read')")
    fun listSpots(
        @RequestParam(required = false) keyword: String?, @RequestParam(required = false) area: String?,
        @RequestParam(required = false) type: String?, @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PageData(val items: List<StoredSpot>, val total: Int)
        val scope = dataScopeResolver.current()
        val visibleOwnerCodes = if (scope.unrestricted) null else scopeQuerySupport.ownerCardIdsInScope(scope)
        val filtered = spotRepository.findAll().filter { scopeQuerySupport.spotVisible(visibleOwnerCodes, it.ownerCode) && (keyword.isNullOrBlank() || it.code.contains(keyword, true)) && (area.isNullOrBlank() || it.area == area) && (type.isNullOrBlank() || it.type == type) && (status == null || it.status == status) }.sortedBy { it.id }.map { StoredSpot(requireNotNull(it.id), it.code, it.area, it.type, it.owner, it.status, it.remark) }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @PostMapping("/spots")
    @PreAuthorize("hasAuthority('spot:manage')")
    fun createSpot(@RequestBody body: SpotRequest): ResponseEntity<Response> {
        val code = requireNotNull(body.code) { "车位编号不能为空" }
        require(!spotRepository.existsByCode(code)) { "车位编号已存在" }
        val ownerCode = scopeQuerySupport.stampOwnerCode(body.owner, null)
        // 车位没有自己的部门字段，归属完全靠车主；目标车主不在范围内就不允许建。
        scopeGuard.requireOwnerCodeAllowed(ownerCode, scopeGuard.currentScope())
        val spot = ParkingSpot().apply { this.code = code; area = requireNotNull(body.area); type = requireNotNull(body.type); owner = body.owner; this.ownerCode = ownerCode; status = requireNotNull(body.status) { "状态不能为空" }; remark = body.remark }
        require(spot.status == 0 || spot.status == 1) { "状态必须为 0 或 1" }
        val saved = spotRepository.save(spot)
        refreshOwnerCounts()
        return responseBuilder.created().data(StoredSpot(requireNotNull(saved.id), saved.code, saved.area, saved.type, saved.owner, saved.status, saved.remark)).build()
    }

    @PutMapping("/spots/{id}")
    @PreAuthorize("hasAuthority('spot:manage')")
    fun updateSpot(@PathVariable id: Long, @RequestBody body: SpotRequest): ResponseEntity<Response> {
        val scope = scopeGuard.currentScope()
        val spot = scopeGuard.requireVisibleSpot(spotRepository.findById(id).orElse(null), scope, "车位不存在")
        body.code?.let { require(!spotRepository.existsByCodeAndIdNot(it, id)) { "车位编号已存在" }; spot.code = it }
        body.area?.let { spot.area = it }; body.type?.let { spot.type = it }
        if (body.ownerProvided || body.status == 0) {
            val newOwnerCode = scopeQuerySupport.stampOwnerCode(body.owner, spot.ownerCode)
            scopeGuard.requireOwnerCodeAllowed(newOwnerCode, scope)
            spot.owner = body.owner
            spot.ownerCode = newOwnerCode
        }
        body.status?.let { spot.status = it }; body.remark?.let { spot.remark = it }
        require(spot.status == 0 || spot.status == 1) { "状态必须为 0 或 1" }
        val saved = spotRepository.save(spot)
        refreshOwnerCounts()
        return responseBuilder.ok().data(StoredSpot(requireNotNull(saved.id), saved.code, saved.area, saved.type, saved.owner, saved.status, saved.remark)).build()
    }

    @DeleteMapping("/spots/{id}")
    @PreAuthorize("hasAuthority('spot:manage')")
    fun deleteSpot(@PathVariable id: Long): ResponseEntity<Response> {
        scopeGuard.requireVisibleSpot(spotRepository.findById(id).orElse(null), scopeGuard.currentScope(), "车位不存在")
        spotRepository.deleteById(id); refreshOwnerCounts()
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @GetMapping("/plates")
    @PreAuthorize("hasAuthority('plate:read')")
    fun listPlates(@RequestParam(required = false) keyword: String?, @RequestParam(required = false) status: Int?, @RequestParam(name = "inspectionStatus", required = false) inspectionStatus: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PageData(val items: List<StoredPlate>, val total: Int)
        val scope = dataScopeResolver.current()
        val visibleOwnerIds = if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
        // 年检状态按当天判定：一次请求里取一个日期，避免跨零点时同一页出现两种判定。
        val today = LocalDate.now()
        val filtered = plateRepository.findAll().filter { scopeQuerySupport.plateVisible(visibleOwnerIds, scope.userId, it) && (keyword.isNullOrBlank() || it.plate.contains(keyword, true) || it.owner.contains(keyword, true)) && (status == null || it.status == status) && (inspectionStatus.isNullOrBlank() || VehicleInspection.status(it.inspectionDate, it.inspectionValidUntil, today) == inspectionStatus) }.sortedBy { it.id }.map { StoredPlate(requireNotNull(it.id), it.plate, it.owner, it.ownerId, it.status, it.regDate.toString(), it.inspectionDate?.toString(), it.inspectionValidUntil?.toString(), VehicleInspection.status(it.inspectionDate, it.inspectionValidUntil, today), it.inspectionRemark) }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size); val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @PostMapping("/plates")
    @PreAuthorize("hasAuthority('plate:manage')")
    fun createPlate(@RequestBody body: PlateRequest): ResponseEntity<Response> {
        val plateNumber = requireNotNull(body.plate) { "车牌号不能为空" }
        require(!plateRepository.existsByPlate(plateNumber)) { "车牌号已存在" }
        val ownerId = requireNotNull(body.ownerId) { "车主 ID 不能为空" }
        scopeGuard.requireOwnerAllowed(ownerId, scopeGuard.currentScope())
        val owner = ownerRepository.findById(ownerId).orElseThrow { IllegalArgumentException("车主不存在") }
        val ownerName = requireNotNull(body.owner) { "车主姓名不能为空" }
        require(owner.name == ownerName) { "车主姓名与车主 ID 不一致" }
        val plate = ParkingPlate().apply {
            plate = plateNumber
            this.owner = owner.name
            this.ownerId = ownerId
            status = requireNotNull(body.status) { "状态不能为空" }
            regDate = LocalDate.parse(requireNotNull(body.regDate))
        }
        require(plate.status == 0 || plate.status == 1) { "状态必须为 0 或 1" }
        applyInspection(plate, body)
        val saved = plateRepository.save(plate)
        refreshOwnerCounts()
        return responseBuilder.created().data(StoredPlate(requireNotNull(saved.id), saved.plate, saved.owner, saved.ownerId, saved.status, saved.regDate.toString(), saved.inspectionDate?.toString(), saved.inspectionValidUntil?.toString(), VehicleInspection.status(saved.inspectionDate, saved.inspectionValidUntil, LocalDate.now()), saved.inspectionRemark)).build()
    }

    @PutMapping("/plates/{id}")
    @PreAuthorize("hasAuthority('plate:manage')")
    fun updatePlate(@PathVariable id: Long, @RequestBody body: PlateRequest): ResponseEntity<Response> {
        val scope = scopeGuard.currentScope()
        val plate = scopeGuard.requireVisiblePlate(plateRepository.findById(id).orElse(null), scope, "车牌不存在")
        body.plate?.let {
            require(!plateRepository.existsByPlateAndIdNot(it, id)) { "车牌号已存在" }
            plate.plate = it
        }
        val ownerId = body.ownerId ?: plate.ownerId
        scopeGuard.requireOwnerAllowed(ownerId, scope)
        val owner = ownerRepository.findById(ownerId).orElseThrow { IllegalArgumentException("车主不存在") }
        body.owner?.let { require(it == owner.name) { "车主姓名与车主 ID 不一致" } }
        plate.ownerId = ownerId
        plate.owner = owner.name
        body.status?.let { plate.status = it }
        body.regDate?.let { plate.regDate = LocalDate.parse(it) }
        require(plate.status == 0 || plate.status == 1) { "状态必须为 0 或 1" }
        applyInspection(plate, body)
        val saved = plateRepository.save(plate)
        refreshOwnerCounts()
        return responseBuilder.ok().data(StoredPlate(requireNotNull(saved.id), saved.plate, saved.owner, saved.ownerId, saved.status, saved.regDate.toString(), saved.inspectionDate?.toString(), saved.inspectionValidUntil?.toString(), VehicleInspection.status(saved.inspectionDate, saved.inspectionValidUntil, LocalDate.now()), saved.inspectionRemark)).build()
    }

    @DeleteMapping("/plates/{id}")
    @PreAuthorize("hasAuthority('plate:manage')")
    fun deletePlate(@PathVariable id: Long): ResponseEntity<Response> {
        scopeGuard.requireVisiblePlate(plateRepository.findById(id).orElse(null), scopeGuard.currentScope(), "车牌不存在")
        plateRepository.deleteById(id); refreshOwnerCounts()
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @GetMapping("/gate-persons")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun listGatePersons(@RequestParam(required = false) keyword: String?, @RequestParam(required = false) dept: String?, @RequestParam(name = "approveStatus", required = false) approveStatus: String?, @RequestParam(name = "syncStatus", required = false) syncStatus: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PageData(val items: List<StoredGatePerson>, val total: Int)
        val filtered = scopeQuerySupport.visibleInScope(dataScopeResolver.current(), gatePersonRepository.findAll())
            .filter { person ->
                (keyword.isNullOrBlank() || listOf(person.code, person.name, person.phone, person.idCard).any { value -> value.contains(keyword, true) }) &&
                    (dept.isNullOrBlank() || person.dept == dept) &&
                    (approveStatus.isNullOrBlank() || approveStatus == person.approveStatus.name || approveStatus == person.approveStatus.value()) &&
                    (syncStatus.isNullOrBlank() || syncStatus == person.syncStatus.name || syncStatus == person.syncStatus.value())
            }
            .sortedBy { it.id }
            .map { StoredGatePerson(requireNotNull(it.id), it.code, it.dept, it.name, it.phone, it.idCard, it.face, it.createTime.toString(), it.approveStatus.value(), it.syncStatus.value()) }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size); val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @GetMapping("/gate-persons/{id}")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun getGatePerson(@PathVariable id: Long): ResponseEntity<Response> {
        val person = scopeGuard.requireVisibleRow(
            gatePersonRepository.findById(id).orElse(null),
            scopeGuard.currentScope(),
            "人员不存在",
        )
        val data = StoredGatePerson(requireNotNull(person.id), person.code, person.dept, person.name, person.phone, person.idCard, person.face, person.createTime.toString(), person.approveStatus.value(), person.syncStatus.value())
        return responseBuilder.ok().data(data).build()
    }

    @PostMapping("/gate-persons", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAuthority('gate-person:manage')")
    @Transactional
    fun createGatePersonMultipart(
        @RequestPart("code") code: String,
        @RequestPart("dept") dept: String,
        @RequestPart("name") name: String,
        @RequestPart("phone") phone: String,
        @RequestPart(name = "idCard") idCard: String,
        @RequestPart("face") face: MultipartFile,
    ): ResponseEntity<Response> {
        requireImageUpload(face)
        val validatedCode = GatePersonFields.requireCode(code)
        val validatedDept = GatePersonFields.requireDept(dept)
        val validatedName = GatePersonFields.requireName(name)
        val validatedPhone = GatePersonFields.requirePhone(phone)
        val validatedIdCard = GatePersonFields.requireIdCard(idCard)
        // 唯一性必须先于上传：先传文件再报唯一性冲突，文件已经独立落库，会永久留在存储里。
        require(!gatePersonRepository.existsByCode(validatedCode)) { "人员编号已存在" }
        require(!gatePersonRepository.existsByIdCard(validatedIdCard)) { "身份证号已存在" }
        val gatePersonDepartmentCode = scopeQuerySupport.stampDepartmentCode(validatedDept, null)
        // 与车主一致：不允许在范围外的部门下新建门禁人员。
        scopeGuard.requireDepartmentCodeAllowed(gatePersonDepartmentCode, scopeGuard.currentScope())
        val file = fileService.upload(face)
        val person = GatePerson().apply {
            this.code = validatedCode
            this.dept = validatedDept
            this.departmentCode = gatePersonDepartmentCode
            this.name = validatedName
            this.phone = validatedPhone
            this.idCard = validatedIdCard
            this.face = file.downloadUrl
            createTime = LocalDateTime.now()
            updatedAt = createTime
        }
        val saved = gatePersonRepository.save(person)
        // 关联到人员编号：本人范围下靠它才能取到自己的门禁图片，否则只有上传部门看得到。
        fileService.linkBusiness(file.id, StoredFile.BUSINESS_GATE_PERSON, saved.code)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_CREATED,
                "gate_person",
                saved.id?.toString(),
                targetSummary = mapOf("code" to saved.code, "department_code" to saved.departmentCode),
                afterData = mapOf("review_status" to saved.approveStatus.value(), "synchronized" to (saved.syncStatus == GatePerson.SyncStatus.SYNCED)),
            ),
        )
        val data = StoredGatePerson(requireNotNull(saved.id), saved.code, saved.dept, saved.name, saved.phone, saved.idCard, saved.face, saved.createTime.toString(), saved.approveStatus.value(), saved.syncStatus.value())
        return responseBuilder.created().data(data).build()
    }

    @PutMapping("/gate-persons/{id}", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAuthority('gate-person:manage')")
    @Transactional
    fun updateGatePersonMultipart(
        @PathVariable id: Long,
        @RequestPart("code", required = false) code: String?,
        @RequestPart("dept", required = false) dept: String?,
        @RequestPart("name", required = false) name: String?,
        @RequestPart("phone", required = false) phone: String?,
        @RequestPart(name = "idCard", required = false) idCard: String?,
        @RequestPart("face", required = false) face: MultipartFile?,
    ): ResponseEntity<Response> {
        face?.let(::requireImageUpload)
        val scope = scopeGuard.currentScope()
        val person = scopeGuard.requireVisibleRow(gatePersonRepository.findById(id).orElse(null), scope, "人员不存在")
        val previousCode = person.code
        val previousDepartmentCode = person.departmentCode
        val previousStatus = person.approveStatus
        val previousSyncStatus = person.syncStatus
        // 只有内容真的变了才算改动：没变的 PUT（前端回填表单后原样提交）不该把已通过的人打回待审核。
        var changed = false
        code?.let {
            val validatedCode = GatePersonFields.requireCode(it)
            require(!gatePersonRepository.existsByCodeAndIdNot(validatedCode, id)) { "人员编号已存在" }
            if (validatedCode != person.code) { person.code = validatedCode; changed = true }
        }
        dept?.let {
            val validatedDept = GatePersonFields.requireDept(it)
            val newCode = scopeQuerySupport.stampDepartmentCode(validatedDept, person.departmentCode)
            scopeGuard.requireDepartmentCodeAllowed(newCode, scope)
            if (validatedDept != person.dept) {
                person.dept = validatedDept
                changed = true
            }
            // 部门编码可能只是被回填（历史行 department_code 为 null，见 GatePerson 的注释）。
            // 它不是用户可见内容，所以照常落库，但不算「改动」：不退回待审核，也不写更新审计。
            // 否则前端原样提交一条已通过的历史记录就会把它打回待审核。
            if (newCode != person.departmentCode) person.departmentCode = newCode
        }
        name?.let { val validatedName = GatePersonFields.requireName(it); if (validatedName != person.name) { person.name = validatedName; changed = true } }
        phone?.let { val validatedPhone = GatePersonFields.requirePhone(it); if (validatedPhone != person.phone) { person.phone = validatedPhone; changed = true } }
        idCard?.let {
            val validatedIdCard = GatePersonFields.requireIdCard(it)
            require(!gatePersonRepository.existsByIdCardAndIdNot(validatedIdCard, id)) { "身份证号已存在" }
            if (validatedIdCard != person.idCard) { person.idCard = validatedIdCard; changed = true }
        }
        val uploadedFace = face?.let { fileService.upload(it) }
        uploadedFace?.let { person.face = it.downloadUrl; changed = true }
        // 已审核通过的内容被改动后必须回到待审核：否则「先送审、通过后再改」可以静默绕过审核。
        if (changed && person.approveStatus != GatePerson.ApproveStatus.PENDING) {
            person.approveStatus = GatePerson.ApproveStatus.PENDING
            person.syncStatus = GatePerson.SyncStatus.NOT_SYNCED
        }
        val saved = gatePersonRepository.save(person)
        // 人脸文件既按人员编号判定归属、也按部门快照判定归属：编号或部门变了都要重新锚定，
        // 否则旧编号再也取不到照片，或旧部门在人员调走之后仍能下载。
        if (previousCode != saved.code || previousDepartmentCode != saved.departmentCode) {
            fileService.relinkBusiness(StoredFile.BUSINESS_GATE_PERSON, previousCode, saved.code, saved.departmentCode)
        }
        uploadedFace?.let { fileService.linkBusiness(it.id, StoredFile.BUSINESS_GATE_PERSON, saved.code) }
        if (changed) {
            auditService.record(
                AuditCommand(
                    AuditAction.GATE_PERSON_UPDATED,
                    "gate_person",
                    id.toString(),
                    targetSummary = mapOf("code" to saved.code, "department_code" to saved.departmentCode),
                    beforeData = mapOf("review_status" to previousStatus.value(), "synchronized" to (previousSyncStatus == GatePerson.SyncStatus.SYNCED)),
                    afterData = mapOf("review_status" to saved.approveStatus.value(), "synchronized" to (saved.syncStatus == GatePerson.SyncStatus.SYNCED)),
                ),
            )
        }
        val data = StoredGatePerson(requireNotNull(saved.id), saved.code, saved.dept, saved.name, saved.phone, saved.idCard, saved.face, saved.createTime.toString(), saved.approveStatus.value(), saved.syncStatus.value())
        return responseBuilder.ok().data(data).build()
    }

    @PutMapping("/gate-persons/{id}/approve")
    @PreAuthorize("hasAuthority('gate-person:review')")
    @Transactional
    fun approveGatePerson(@PathVariable id: Long, @RequestParam(required = false) reason: String?): ResponseEntity<Response> {
        val saved = reviewGatePerson(requireVisibleGatePerson(id), approved = true, reason = reason)
        return responseBuilder.ok().message("审批通过").data(mapOf("id" to saved.id, "approveStatus" to saved.approveStatus.value())).build()
    }

    @PutMapping("/gate-persons/{id}/reject")
    @PreAuthorize("hasAuthority('gate-person:review')")
    @Transactional
    fun rejectGatePerson(@PathVariable id: Long, @RequestParam(required = false) reason: String?): ResponseEntity<Response> {
        val saved = reviewGatePerson(requireVisibleGatePerson(id), approved = false, reason = reason)
        return responseBuilder.ok().message("审批拒绝").data(mapOf("id" to saved.id, "approveStatus" to saved.approveStatus.value())).build()
    }

    /**
     * 批量审核。
     *
     * 单独开一个端点而不是让前端循环单条 PUT：单条调用下「部分成功」是常态，前端既拿不到一个
     * 明确的审核结论，也没法把失败的那几个人一次性告诉用户。
     */
    @PutMapping("/gate-persons/reviews")
    @PreAuthorize("hasAuthority('gate-person:review')")
    @Transactional
    fun reviewGatePersons(@RequestBody body: GatePersonReviewBody): ResponseEntity<Response> {
        val ids = requireNotNull(body.ids) { "审核列表不能为空" }
        require(ids.isNotEmpty()) { "审核列表不能为空" }
        require(ids.size <= BATCH_REVIEW_MAX) { "单次批量审核不能超过 $BATCH_REVIEW_MAX 人，请分批提交" }
        require(ids.distinct().size == ids.size) { "审核记录的 ID 不能重复" }
        val approved = requireNotNull(body.approved) { "必须指定审核结论" }
        // 范围只解析一次：current() 每次都要重读整张部门表，逐条解析会把一次批量放大成 N 倍库查询。
        val scope = scopeGuard.currentScope()
        val byId = gatePersonRepository.findAllById(ids).associateBy { requireNotNull(it.id) }
        // 一次取齐，不再逐条 findById；缺失与范围外共用同一个错误，避免用响应差异探测别的部门。
        val reviewed = ids.map { id ->
            reviewGatePerson(scopeGuard.requireVisibleRow(byId[id], scope, "人员不存在"), approved, body.reason)
        }
        return responseBuilder.ok().message("已审核 ${reviewed.size} 人").data(mapOf("reviewed" to reviewed.size)).build()
    }

    /**
     * 门禁人员物理删除。
     *
     * 与门禁授权一致地置为 denyAll：删除人员必须走「申请删除 → 审批同意」这条留痕路径，
     * 否则持有 manage 的部门管理可以直接删掉本部门人员，删除审核形同虚设。
     */
    @DeleteMapping("/gate-persons/{id}")
    @PreAuthorize("denyAll()")
    fun deleteGatePerson(@PathVariable id: Long): ResponseEntity<Response> {
        scopeGuard.requireVisibleRow(gatePersonRepository.findById(id).orElse(null), scopeGuard.currentScope(), "人员不存在")
        gatePersonRepository.deleteById(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @PostMapping("/gate-persons/{id}/delete-requests")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun createDeleteRequest(@PathVariable id: Long, @RequestBody body: DeleteRequestBody): ResponseEntity<Response> {
        val person = scopeGuard.requireVisibleRow(gatePersonRepository.findById(id).orElse(null), scopeGuard.currentScope(), "人员不存在")
        val request = GateDeleteRequest().apply {
            personId = id
            code = person.code
            dept = person.dept
            departmentCode = person.departmentCode
            name = person.name
            phone = person.phone
            idCard = person.idCard
            face = person.face
            reason = requireNotNull(body.reason?.trim()?.takeIf(String::isNotEmpty)) { "删除原因不能为空" }
            applyTime = LocalDateTime.now()
        }
        val saved = gateDeleteRequestRepository.save(request)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_DELETE_REQUESTED,
                "gate_delete_request",
                saved.id?.toString(),
                reason = saved.reason,
                targetSummary = mapOf("person_id" to id, "code" to saved.code, "department_code" to saved.departmentCode),
                afterData = mapOf("status" to saved.status.value()),
            ),
        )
        val data = StoredDeleteRequest(
            requireNotNull(saved.id), saved.personId, saved.code, saved.dept, saved.name,
            saved.phone, saved.idCard, saved.face, saved.reason, saved.applyTime.toString(), saved.status.value(),
        )
        return responseBuilder.created().data(data).build()
    }

    @GetMapping("/gate-persons/delete-requests")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun listDeleteRequests(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class Response(val items: List<StoredDeleteRequest>, val total: Int)
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        // 删除申请带姓名、手机号与身份证快照，必须和门禁人员本身一样按工作部门裁剪。
        val requests = scopeQuerySupport.visibleInScope(dataScopeResolver.current(), gateDeleteRequestRepository.findAll())
            .filter { request ->
                (keyword.isNullOrBlank() || listOf(request.code, request.name, request.phone, request.idCard).any { it.contains(keyword, true) }) &&
                    (status.isNullOrBlank() || request.status.name == status || request.status.value() == status)
            }
            .sortedByDescending { it.applyTime }
            .map { StoredDeleteRequest(requireNotNull(it.id), it.personId, it.code, it.dept, it.name, it.phone, it.idCard, it.face, it.reason, it.applyTime.toString(), it.status.value()) }
        val from = ((page - 1) * pageSize).coerceAtMost(requests.size)
        val to = (from + pageSize).coerceAtMost(requests.size)
        val rs = Response(requests.subList(from, to), requests.size)
        return responseBuilder.ok().data(rs).build()
    }

    @Transactional
    @PutMapping("/gate-persons/delete-requests/{id}/approve")
    @PreAuthorize("hasAuthority('gate-person:review')")
    fun approveDeleteRequest(@PathVariable id: Long): ResponseEntity<Response> {
        val scope = scopeGuard.currentScope()
        val request = scopeGuard.requireVisibleRow(gateDeleteRequestRepository.findById(id).orElse(null), scope, "删除申请不存在")
        require(request.status == GateDeleteRequest.Status.PENDING) { "删除申请已处理" }
        // 真正被删的是人员，范围校验必须落在人员上：申请单本身的可见性不构成删除授权。
        val person = scopeGuard.requireVisibleRow(gatePersonRepository.findById(request.personId).orElse(null), scope, "人员不存在")
        gatePersonRepository.deleteById(requireNotNull(person.id))
        // 人脸是敏感生物特征，人员已删除就不能再按它的编号被反查下载。
        fileService.unlinkBusiness(StoredFile.BUSINESS_GATE_PERSON, person.code)
        request.status = GateDeleteRequest.Status.APPROVED
        gateDeleteRequestRepository.save(request)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_DELETE_REQUEST_REVIEWED,
                "gate_delete_request",
                id.toString(),
                reason = request.reason,
                targetSummary = mapOf("person_id" to request.personId, "code" to request.code),
                beforeData = mapOf("status" to GateDeleteRequest.Status.PENDING.value()),
                afterData = mapOf("status" to request.status.value()),
            ),
        )
        // 人员被物理删除是 CRITICAL 级事实，单独立案；申请单的状态流转不足以表达它。
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_DELETED,
                "gate_person",
                person.id?.toString(),
                reason = request.reason,
                targetSummary = mapOf("code" to person.code, "department_code" to person.departmentCode, "person_id" to person.id),
                afterData = mapOf("deleted" to true),
            ),
        )
        return responseBuilder.ok().message("已同意删除申请").build()
    }

    @PutMapping("/gate-persons/delete-requests/{id}/reject")
    @PreAuthorize("hasAuthority('gate-person:review')")
    fun rejectDeleteRequest(@PathVariable id: Long): ResponseEntity<Response> {
        val request = scopeGuard.requireVisibleRow(gateDeleteRequestRepository.findById(id).orElse(null), scopeGuard.currentScope(), "删除申请不存在")
        require(request.status == GateDeleteRequest.Status.PENDING) { "删除申请已处理" }
        request.status = GateDeleteRequest.Status.REJECTED
        gateDeleteRequestRepository.save(request)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_DELETE_REQUEST_REVIEWED,
                "gate_delete_request",
                id.toString(),
                reason = request.reason,
                targetSummary = mapOf("person_id" to request.personId, "code" to request.code),
                beforeData = mapOf("status" to GateDeleteRequest.Status.PENDING.value()),
                afterData = mapOf("status" to request.status.value()),
            ),
        )
        return responseBuilder.ok().message("已拒绝删除申请").build()
    }

    @GetMapping("/person-records")
    @PreAuthorize("hasAuthority('person-record:read')")
    fun personRecords(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false) gate: String?,
        @RequestParam(name = "passType", required = false) passType: String?,
        @RequestParam(name = "status", required = false) recordStatus: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PersonRecordData(val id: Long, val person: String, @param:JsonProperty("cardId") val cardId: String?, val dept: String?, val time: String, val direction: String, val gate: String?, val method: String?, val status: String, val photo: String?)
        data class PageData(val items: List<PersonRecordData>, val total: Int)
        val scope = dataScopeResolver.current()
        val filtered = scopeQuerySupport.personRecordsInScope(scope, personAccessRecordRepository.findAll()).filter {
            (keyword.isNullOrBlank() || it.person.contains(keyword, true) || it.cardId.orEmpty().contains(keyword, true)) &&
                (direction.isNullOrBlank() || it.direction == direction) && (gate.isNullOrBlank() || it.gate == gate) &&
                (passType.isNullOrBlank() || it.method == passType) && (recordStatus.isNullOrBlank() || it.status == recordStatus) && (startDate == null || !it.time.toLocalDate().isBefore(startDate)) &&
                (endDate == null || !it.time.toLocalDate().isAfter(endDate))
        }.sortedByDescending { it.time }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val items = filtered.subList(from, to).map { PersonRecordData(requireNotNull(it.id), it.person, it.cardId, it.dept, it.time.toString(), it.direction, it.gate, it.method, it.status, it.photo) }
        return responseBuilder.ok().data(PageData(items, filtered.size)).build()
    }

    @Transactional
    @GetMapping("/vehicle-records")
    @PreAuthorize("hasAuthority('vehicle-record:read')")
    fun vehicleRecords(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false) gate: String?,
        @RequestParam(name = "passType", required = false) passType: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class VehicleRecord(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val amount: BigDecimal,
            val method: String?,
            val status: String,
            val photo: String?,
        )
        data class PageData(val items: List<VehicleRecord>, val total: Int)
        val directionFilter = when (direction) {
            "进" -> AccessRecord.InAndOut.IN
            "出" -> AccessRecord.InAndOut.OUT
            else -> null
        }
        val spec = vehicleRecordSpec(keyword?.trim()?.ifBlank { null }, directionFilter, gate?.ifBlank { null }, passType?.ifBlank { null }, startDate, endDate)
            // 范围必须下推到 SQL：本接口是数据库分页并直接返回 totalElements 的，事后过滤会让
            // 总数失真，甚至出现「总数大于 0 但当前页为空」。
            .and(scopeQuerySupport.accessRecordSpec(dataScopeResolver.current()))
        val records = accessRecordRepository.findAll(spec, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "inAndOutTime", "id")))
        val items = records.content.map {
            VehicleRecord(
                requireNotNull(it.id),
                it.carNumber,
                it.carOwnerName,
                it.departmentName,
                it.inAndOutTime.toString(),
                if (it.inAndOut == AccessRecord.InAndOut.IN) "进" else "出",
                it.gateName,
                AccessRecord.displayVehicleTypeName(it.vehicleTypeName) ?: it.carType?.carName,
                it.feeAmount,
                it.passType ?: when (it.releaseChannel) {
                    AccessRecord.ReleaseChannel.AUTOMATIC -> "车牌识别"
                    AccessRecord.ReleaseChannel.MANUAL, AccessRecord.ReleaseChannel.REMOTE -> "刷卡"
                    AccessRecord.ReleaseChannel.UNKNOWN, null -> null
                },
                it.recordStatus,
                it.photoUrl,
            )
        }
        return responseBuilder.ok().data(PageData(items, records.totalElements.toInt())).build()
    }

    @GetMapping("/login-logs")
    @PreAuthorize("hasAuthority('audit:read')")
    fun loginLogs(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class LogData(val id: Long, @param:JsonProperty("user") val user: String, val ip: String?, val location: String?, val browser: String?, val os: String?, val status: String, val time: String, val message: String?)
        data class PageData(val items: List<LogData>, val total: Int)
        val visibleAfter = logVisibilityService.visibleAfter(LogVisibilityService.Type.LOGIN)
        val logs = auditEventRepository.findAll().filter {
            val targetUsername = runCatching { objectMapper.readTree(it.targetSummary ?: "{}").get("username")?.asString() }.getOrNull()
            it.category.name == "AUTHENTICATION" && (it.action == "AUTH_LOGIN_SUCCEEDED" || it.action == "AUTH_LOGIN_FAILED") &&
                (keyword.isNullOrBlank() || it.actorUsername.contains(keyword, true) || it.sourceIp.orEmpty().contains(keyword, true) || targetUsername?.contains(keyword, true) == true) &&
                (status.isNullOrBlank() || (status == "成功" && it.result.name == "SUCCESS") || (status == "失败" && it.result.name != "SUCCESS")) &&
                (visibleAfter == null || it.occurredAt.isAfter(visibleAfter)) &&
                (startDate == null || !it.occurredAt.toLocalDate().isBefore(startDate)) && (endDate == null || !it.occurredAt.toLocalDate().isAfter(endDate))
        }.sortedByDescending { it.occurredAt }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(logs.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(logs.size)
        val items = logs.subList(from, to).map {
            val userAgent = it.userAgent.orEmpty()
            val browser = when {
                userAgent.contains("Edg", true) -> "Edge"
                userAgent.contains("Chrome", true) -> "Chrome"
                userAgent.contains("Firefox", true) -> "Firefox"
                userAgent.contains("Safari", true) -> "Safari"
                else -> userAgent.ifBlank { null }
            }
            val os = when {
                userAgent.contains("Windows", true) -> "Windows"
                userAgent.contains("Mac OS", true) -> "macOS"
                userAgent.contains("Android", true) -> "Android"
                userAgent.contains("iPhone", true) -> "iOS"
                userAgent.contains("Linux", true) -> "Linux"
                else -> null
            }
            val location = it.sourceIp?.let { ip ->
                val privateNetwork = ip == "127.0.0.1" || ip == "::1" || ip.startsWith("10.") ||
                    ip.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(ip)
                if (privateNetwork) "内网" else "外网"
            }
            val message = when (it.action) {
                "AUTH_LOGIN_SUCCEEDED" -> "登录成功"
                "AUTH_LOGIN_FAILED" -> "登录失败"
                else -> it.reason ?: it.action
            }
            LogData(requireNotNull(it.id), it.actorUsername, it.sourceIp, location, browser, os, if (it.result.name == "SUCCESS") "成功" else "失败", it.occurredAt.toString(), message)
        }
        return responseBuilder.ok().data(PageData(items, logs.size)).build()
    }

    @DeleteMapping("/login-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('audit:delete')")
    fun clearLoginLogs(): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("cleared_at") val clearedAt: String)
        val clearedAt = logVisibilityService.clear(LogVisibilityService.Type.LOGIN)
        auditService.record(
            AuditCommand(
                action = AuditAction.LOGS_CLEARED,
                targetType = "login_log",
                scopeSummary = mapOf("target_type" to "login_log", "occurred_to" to clearedAt.toString()),
            ),
        )
        val rs = Response(clearedAt.toString())
        return responseBuilder.ok().message("登录日志已清空").data(rs).build()
    }

    @GetMapping("/operation-logs")
    @PreAuthorize("hasAuthority('audit:read')")
    fun operationLogs(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) module: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class LogData(val id: Long, val user: String, val module: String, val action: String, @param:JsonProperty("desc") val description: String, val ip: String?, val status: String, val time: String, val cost: String?)
        data class PageData(val items: List<LogData>, val total: Int)
        val visibleAfter = logVisibilityService.visibleAfter(LogVisibilityService.Type.OPERATION)
        val logs = operationLogRepository.findAll().filter {
            val moduleName = it.path.trim('/').split('/').drop(1).firstOrNull()?.let { segment ->
                when (segment) {
                    "users", "depts", "roles", "permissions" -> "用户管理"
                    "files" -> "文件管理"
                    "access-controls", "gate-persons" -> "门禁管理"
                    "access-records", "records" -> "记录管理"
                    "devices", "synchronizations" -> "设备管理"
                    else -> segment
                }
            } ?: "系统"
            !it.path.startsWith("/api/auth/") &&
                (keyword.isNullOrBlank() || it.actorUsername.contains(keyword, true) || it.path.contains(keyword, true) || it.method.contains(keyword, true)) &&
                (module.isNullOrBlank() || moduleName.equals(module, true)) &&
                (status.isNullOrBlank() || (status == "成功" && it.result == OperationLog.Result.SUCCESS) || (status == "失败" && it.result != OperationLog.Result.SUCCESS)) &&
                (visibleAfter == null || it.occurredAt.isAfter(visibleAfter)) &&
                (startDate == null || !it.occurredAt.toLocalDate().isBefore(startDate)) && (endDate == null || !it.occurredAt.toLocalDate().isAfter(endDate))
        }.sortedByDescending { it.occurredAt }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(logs.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(logs.size)
        val items = logs.subList(from, to).map {
            val moduleName = it.path.trim('/').split('/').drop(1).firstOrNull()?.let { segment ->
                when (segment) {
                    "users", "depts", "roles", "permissions" -> "用户管理"
                    "files" -> "文件管理"
                    "access-controls", "gate-persons" -> "门禁管理"
                    "access-records", "records" -> "记录管理"
                    "devices", "synchronizations" -> "设备管理"
                    else -> segment
                }
            } ?: "系统"
            val actionName = when (it.method.uppercase()) {
                "POST" -> "新增"
                "PUT", "PATCH" -> "修改"
                "DELETE" -> "删除"
                else -> "查询"
            }
            val description = "${it.method.uppercase()} ${it.path}"
            LogData(requireNotNull(it.id), it.actorUsername, moduleName, actionName, description, it.sourceIp, if (it.result == OperationLog.Result.SUCCESS) "成功" else "失败", it.occurredAt.toString(), "${it.durationMs}ms")
        }
        return responseBuilder.ok().data(PageData(items, logs.size)).build()
    }

    @DeleteMapping("/operation-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('audit:delete')")
    fun clearOperationLogs(): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("cleared_at") val clearedAt: String)
        val clearedAt = logVisibilityService.clear(LogVisibilityService.Type.OPERATION)
        auditService.record(
            AuditCommand(
                action = AuditAction.LOGS_CLEARED,
                targetType = "operation_log",
                scopeSummary = mapOf("target_type" to "operation_log", "occurred_to" to clearedAt.toString()),
            ),
        )
        val rs = Response(clearedAt.toString())
        return responseBuilder.ok().message("操作日志已清空").data(rs).build()
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('dashboard:read')")
    fun dashboard(): ResponseEntity<Response> {
        data class Stat(val label: String, val value: Int, val delta: String, val trend: String, val color: String)
        data class Parking(val area: String, val total: Int, val used: Int)
        data class Series(val name: String, val data: List<Int>, val color: String)
        data class Trend(val labels: List<String>, val series: List<Series>)
        data class Dashboard(val stats: List<Stat>, val parking: List<Parking>, @param:JsonProperty("violationTypes") val violationTypes: List<Map<String, Any>>, @param:JsonProperty("violationTrend") val violationTrend: Trend, @param:JsonProperty("inoutTrend") val inoutTrend: Trend)
        val scope = dataScopeResolver.current()
        // 车位指标按配置的车场/区域统计：总数取科拓同步的区域容量，已分配取本地已登记且启用的车位数。
        val spotStats = dashboardSpotStatsService.currentStats(scope)
        val violations = violationRecordRepository.findAllWithViolationType()
            .filter { scopeQuerySupport.violationSubjectVisible(scope, it.subject) }
        val violationTypes = violations.groupingBy { it.violationType.violationName ?: "未分类" }.eachCount().entries.map { mapOf<String, Any>("name" to it.key, "value" to it.value, "color" to "#3B6DFF") }
        val violationDates = (0..6).map { LocalDate.now().minusDays((6 - it).toLong()) }
        val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val violationLabels = violationDates.map { weekLabels[it.dayOfWeek.value - 1] }
        val violationTrend = Trend(violationLabels, listOf(Series("违规次数", violationDates.map { day -> violations.count { it.violationTime.toLocalDate() == day } }, "#E9A568")))
        val todayStart = LocalDate.now().atStartOfDay()
        val todaySpec = Specification<AccessRecord> { root, _, cb ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("inAndOutTime"), todayStart),
                cb.lessThan(root.get("inAndOutTime"), todayStart.plusDays(1)),
            )
        }
        val accessRecords = accessRecordRepository.findAll(
            Specification.where(scopeQuerySupport.accessRecordSpec(scope)).and(todaySpec),
        )
        val inoutLabels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00")
        val inoutTrend = Trend(inoutLabels, listOf(
            Series("进场", inoutLabels.mapIndexed { index, _ -> accessRecords.count { it.inAndOut == AccessRecord.InAndOut.IN && it.inAndOutTime.hour / 4 == index } }, "#38BDF8"),
            Series("出场", inoutLabels.mapIndexed { index, _ -> accessRecords.count { it.inAndOut == AccessRecord.InAndOut.OUT && it.inAndOutTime.hour / 4 == index } }, "#6EE7B7"),
        ))
        val rs = Dashboard(
            listOf(Stat("车位总数", spotStats.total, "0%", "flat", "blue"), Stat("已分配", spotStats.used, "0%", "flat", "green"), Stat("空闲车位", (spotStats.total - spotStats.used).coerceAtLeast(0), "0%", "flat", "orange"), Stat("今日违规", violations.count { it.violationTime.toLocalDate() == java.time.LocalDate.now() }, "0%", "flat", "red")),
            spotStats.zones.map { Parking(it.zoneName, it.total, it.used) }, violationTypes, violationTrend, inoutTrend,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /**
     * 把请求里的年检信息写到车牌上。
     *
     * `inspected` 只表达「已年检 / 未年检」两种意图，两者都是整条登记的替换：
     * false 清空年检信息，true 按请求登记（没给年检日期就按今天，没给有效期就按年检日期起一年）。
     * 只有 `inspected` 缺省时才退化成局部更新，只写明确给出的字段——这样只改车牌号或状态的请求
     * 不会顺手抹掉已有的年检记录。
     *
     * 备注用「字段是否出现」区分意图：传空串表示清空，不传则保持原值。
     */
    private fun applyInspection(plate: ParkingPlate, body: PlateRequest) {
        if (body.inspected == false) {
            plate.inspectionDate = null
            plate.inspectionValidUntil = null
            plate.inspectionRemark = null
            return
        }
        if (body.inspected == true) {
            val inspectedOn = body.inspectionDate?.let(LocalDate::parse) ?: LocalDate.now()
            val validUntil = body.inspectionValidUntil?.let(LocalDate::parse) ?: VehicleInspection.defaultValidUntil(inspectedOn)
            require(!validUntil.isBefore(inspectedOn)) { "年检有效期不能早于年检日期" }
            plate.inspectionDate = inspectedOn
            plate.inspectionValidUntil = validUntil
            body.inspectionRemark?.let { plate.inspectionRemark = it.trim().takeIf(String::isNotEmpty) }
            return
        }
        body.inspectionDate?.let { plate.inspectionDate = LocalDate.parse(it) }
        body.inspectionValidUntil?.let { plate.inspectionValidUntil = LocalDate.parse(it) }
        body.inspectionRemark?.let { plate.inspectionRemark = it.trim().takeIf(String::isNotEmpty) }
        val inspectedOn = plate.inspectionDate
        val validUntil = plate.inspectionValidUntil
        require(inspectedOn == null || validUntil == null || !validUntil.isBefore(inspectedOn)) { "年检有效期不能早于年检日期" }
    }

    private fun refreshOwnerCounts() {
        val owners = ownerRepository.findAll()
        val spots = spotRepository.findAll()
        val plates = plateRepository.findAll()
        owners.forEach { owner ->
            owner.spotCount = spots.count { it.owner == owner.name }
            owner.plateCount = plates.count { it.ownerId == owner.id }
        }
        if (owners.isNotEmpty()) ownerRepository.saveAll(owners)
    }

    /** 车辆进出记录列表的数据库筛选条件；未传入的条件不参与查询。 */
    private fun vehicleRecordSpec(
        keyword: String?,
        inAndOut: AccessRecord.InAndOut?,
        gate: String?,
        passType: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): Specification<AccessRecord> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()
        keyword?.let {
            val pattern = "%${it.lowercase()}%"
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("carNumber")), pattern),
                    cb.like(cb.lower(root.get("carOwnerName")), pattern),
                ),
            )
        }
        inAndOut?.let { predicates.add(cb.equal(root.get<AccessRecord.InAndOut>("inAndOut"), it)) }
        gate?.let { predicates.add(cb.equal(root.get<String>("gateName"), it)) }
        passType?.let { predicates.add(cb.equal(root.get<String>("passType"), it)) }
        startDate?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("inAndOutTime"), it.atStartOfDay())) }
        endDate?.let { predicates.add(cb.lessThan(root.get("inAndOutTime"), it.plusDays(1).atStartOfDay())) }
        cb.and(*predicates.toTypedArray())
    }

    /** 门禁人员审核的单一入口：单条与批量共用，避免两条路径的状态机走偏。 */
    private fun reviewGatePerson(person: GatePerson, approved: Boolean, reason: String?): GatePerson {
        val id = requireNotNull(person.id)
        val before = person.approveStatus
        require(before == GatePerson.ApproveStatus.PENDING) { "该人员当前状态不允许审核，请编辑后重新提交" }
        person.approveStatus = if (approved) GatePerson.ApproveStatus.APPROVED else GatePerson.ApproveStatus.REJECTED
        // 驳回的人不能继续留在「已同步」上，否则会出现已拒绝却已下发的矛盾状态。
        if (!approved) person.syncStatus = GatePerson.SyncStatus.NOT_SYNCED
        val saved = gatePersonRepository.save(person)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_REVIEWED,
                "gate_person",
                id.toString(),
                reason = reason,
                targetSummary = mapOf("code" to saved.code),
                beforeData = mapOf("review_status" to before.value()),
                afterData = mapOf("review_status" to saved.approveStatus.value()),
            ),
        )
        return saved
    }

    /** 按 ID 取范围内人员；不存在与范围外共用同一个错误，避免用响应差异探测别的部门。 */
    private fun requireVisibleGatePerson(id: Long): GatePerson =
        scopeGuard.requireVisibleRow(gatePersonRepository.findById(id).orElse(null), scopeGuard.currentScope(), "人员不存在")

    private fun requireImageUpload(file: MultipartFile) {
        require(file.size <= FACE_PHOTO_MAX_BYTES) { "人脸照片不能超过 2MB" }
        require(file.contentType?.startsWith("image/", ignoreCase = true) == true) { "人脸照片必须为图片格式" }
        val extension = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
        require(extension in FACE_PHOTO_EXTENSIONS) { "人脸照片格式不受支持" }
        // 只信客户端给的内容类型与扩展名，会被「改个后缀 + 伪造 Content-Type」绕过，再核对文件头。
        require(matchesImageSignature(file)) { "人脸照片内容不是受支持的图片" }
    }

    /**
     * 按文件头判断是不是受支持的图片。
     *
     * WebP 不能只看 RIFF 前缀——WAV、AVI 同样是 RIFF 容器，必须再核对偏移 8 处的 WEBP 标识，
     * 否则把音频改名为 face.webp 就能通过校验。
     */
    private fun matchesImageSignature(file: MultipartFile): Boolean {
        val header = ByteArray(IMAGE_HEADER_BYTES)
        val size = file.inputStream.use { it.read(header) }
        if (size < 4) return false
        fun matches(offset: Int, vararg expected: Int): Boolean =
            size >= offset + expected.size && expected.indices.all { header[offset + it] == expected[it].toByte() }
        return matches(0, 0xFF, 0xD8, 0xFF) || // JPEG
            matches(0, 0x89, 'P'.code, 'N'.code, 'G'.code) || // PNG
            matches(0, 'G'.code, 'I'.code, 'F'.code) || // GIF
            matches(0, 'B'.code, 'M'.code) || // BMP
            (matches(0, 'R'.code, 'I'.code, 'F'.code, 'F'.code) && matches(8, 'W'.code, 'E'.code, 'B'.code, 'P'.code)) // WebP
    }

    private companion object {
        /** 与原型一致：人脸照片上限 2MB。 */
        const val FACE_PHOTO_MAX_BYTES = 2 * 1024 * 1024

        /** 单次批量审核的条数上限：一次请求要逐条写审计并占用一个事务，必须有个封顶。 */
        const val BATCH_REVIEW_MAX = 200

        const val IMAGE_HEADER_BYTES = 12

        val FACE_PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

}
