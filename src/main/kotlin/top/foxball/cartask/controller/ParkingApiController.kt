package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.springframework.http.ResponseEntity
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
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.service.PositionService
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

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
        val filtered = ownerRepository.findAll().filter {
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
        val owner = ParkingOwner().apply {
            this.cardId = cardId
            name = requireNotNull(body.name) { "姓名不能为空" }
            dept = requireNotNull(body.dept) { "部门不能为空" }
            phone = requireNotNull(body.phone) { "手机号不能为空" }
            spotCount = requireNotNull(body.spotCount) { "车位数量不能为空" }
            plateCount = requireNotNull(body.plateCount) { "车牌数量不能为空" }
            balance = body.balance ?: BigDecimal.ZERO
            status = requireNotNull(body.status) { "状态不能为空" }
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
        val owner = ownerRepository.findById(id).orElseThrow { IllegalArgumentException("车主不存在") }
        val previousName = owner.name
        body.cardId?.let { require(!ownerRepository.existsByCardIdAndIdNot(it, id)) { "车主卡号已存在" }; owner.cardId = it }
        body.name?.let { owner.name = it }
        body.dept?.let { owner.dept = it }
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
        val owner = ownerRepository.findById(id).orElseThrow { IllegalArgumentException("车主不存在") }
        require(plateRepository.findAll().none { it.ownerId == id } && spotRepository.findAll().none { it.owner == owner.name }) {
            "车主仍有关联车位或车牌"
        }
        ownerRepository.deleteById(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @PostMapping("/owners/{id}/recharge")
    @PreAuthorize("hasAuthority('owner:manage')")
    fun rechargeOwner(@PathVariable id: Long, @RequestBody body: RechargeRequest): ResponseEntity<Response> {
        val old = ownerRepository.findById(id).orElseThrow { IllegalArgumentException("车主不存在") }
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
        val filtered = spotRepository.findAll().filter { (keyword.isNullOrBlank() || it.code.contains(keyword, true)) && (area.isNullOrBlank() || it.area == area) && (type.isNullOrBlank() || it.type == type) && (status == null || it.status == status) }.sortedBy { it.id }.map { StoredSpot(requireNotNull(it.id), it.code, it.area, it.type, it.owner, it.status, it.remark) }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @PostMapping("/spots")
    @PreAuthorize("hasAuthority('spot:manage')")
    fun createSpot(@RequestBody body: SpotRequest): ResponseEntity<Response> {
        val code = requireNotNull(body.code) { "车位编号不能为空" }
        require(!spotRepository.existsByCode(code)) { "车位编号已存在" }
        val spot = ParkingSpot().apply { this.code = code; area = requireNotNull(body.area); type = requireNotNull(body.type); owner = body.owner; status = requireNotNull(body.status) { "状态不能为空" }; remark = body.remark }
        require(spot.status == 0 || spot.status == 1) { "状态必须为 0 或 1" }
        val saved = spotRepository.save(spot)
        refreshOwnerCounts()
        return responseBuilder.created().data(StoredSpot(requireNotNull(saved.id), saved.code, saved.area, saved.type, saved.owner, saved.status, saved.remark)).build()
    }

    @PutMapping("/spots/{id}")
    @PreAuthorize("hasAuthority('spot:manage')")
    fun updateSpot(@PathVariable id: Long, @RequestBody body: SpotRequest): ResponseEntity<Response> {
        val spot = spotRepository.findById(id).orElseThrow { IllegalArgumentException("车位不存在") }
        body.code?.let { require(!spotRepository.existsByCodeAndIdNot(it, id)) { "车位编号已存在" }; spot.code = it }
        body.area?.let { spot.area = it }; body.type?.let { spot.type = it }
        if (body.ownerProvided || body.status == 0) spot.owner = body.owner
        body.status?.let { spot.status = it }; body.remark?.let { spot.remark = it }
        require(spot.status == 0 || spot.status == 1) { "状态必须为 0 或 1" }
        val saved = spotRepository.save(spot)
        refreshOwnerCounts()
        return responseBuilder.ok().data(StoredSpot(requireNotNull(saved.id), saved.code, saved.area, saved.type, saved.owner, saved.status, saved.remark)).build()
    }

    @DeleteMapping("/spots/{id}")
    @PreAuthorize("hasAuthority('spot:manage')")
    fun deleteSpot(@PathVariable id: Long): ResponseEntity<Response> { require(spotRepository.existsById(id)) { "车位不存在" }; spotRepository.deleteById(id); refreshOwnerCounts(); return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build() }

    @GetMapping("/plates")
    @PreAuthorize("hasAuthority('plate:read')")
    fun listPlates(@RequestParam(required = false) keyword: String?, @RequestParam(required = false) status: Int?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PageData(val items: List<StoredPlate>, val total: Int)
        val filtered = plateRepository.findAll().filter { (keyword.isNullOrBlank() || it.plate.contains(keyword, true) || it.owner.contains(keyword, true)) && (status == null || it.status == status) }.sortedBy { it.id }.map { StoredPlate(requireNotNull(it.id), it.plate, it.owner, it.ownerId, it.status, it.regDate.toString()) }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size); val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @PostMapping("/plates")
    @PreAuthorize("hasAuthority('plate:manage')")
    fun createPlate(@RequestBody body: PlateRequest): ResponseEntity<Response> {
        val plateNumber = requireNotNull(body.plate) { "车牌号不能为空" }
        require(!plateRepository.existsByPlate(plateNumber)) { "车牌号已存在" }
        val ownerId = requireNotNull(body.ownerId) { "车主 ID 不能为空" }
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
        val saved = plateRepository.save(plate)
        refreshOwnerCounts()
        return responseBuilder.created().data(StoredPlate(requireNotNull(saved.id), saved.plate, saved.owner, saved.ownerId, saved.status, saved.regDate.toString())).build()
    }

    @PutMapping("/plates/{id}")
    @PreAuthorize("hasAuthority('plate:manage')")
    fun updatePlate(@PathVariable id: Long, @RequestBody body: PlateRequest): ResponseEntity<Response> {
        val plate = plateRepository.findById(id).orElseThrow { IllegalArgumentException("车牌不存在") }
        body.plate?.let {
            require(!plateRepository.existsByPlateAndIdNot(it, id)) { "车牌号已存在" }
            plate.plate = it
        }
        val ownerId = body.ownerId ?: plate.ownerId
        val owner = ownerRepository.findById(ownerId).orElseThrow { IllegalArgumentException("车主不存在") }
        body.owner?.let { require(it == owner.name) { "车主姓名与车主 ID 不一致" } }
        plate.ownerId = ownerId
        plate.owner = owner.name
        body.status?.let { plate.status = it }
        body.regDate?.let { plate.regDate = LocalDate.parse(it) }
        require(plate.status == 0 || plate.status == 1) { "状态必须为 0 或 1" }
        val saved = plateRepository.save(plate)
        refreshOwnerCounts()
        return responseBuilder.ok().data(StoredPlate(requireNotNull(saved.id), saved.plate, saved.owner, saved.ownerId, saved.status, saved.regDate.toString())).build()
    }

    @DeleteMapping("/plates/{id}")
    @PreAuthorize("hasAuthority('plate:manage')")
    fun deletePlate(@PathVariable id: Long): ResponseEntity<Response> { require(plateRepository.existsById(id)) { "车牌不存在" }; plateRepository.deleteById(id); refreshOwnerCounts(); return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build() }

    @GetMapping("/gate-persons")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun listGatePersons(@RequestParam(required = false) keyword: String?, @RequestParam(required = false) dept: String?, @RequestParam(name = "approveStatus", required = false) approveStatus: String?, @RequestParam(name = "syncStatus", required = false) syncStatus: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        data class PageData(val items: List<StoredGatePerson>, val total: Int)
        val filtered = gatePersonRepository.findAll().filter { (keyword.isNullOrBlank() || listOf(it.code, it.name, it.phone, it.idCard).any { value -> value.contains(keyword, true) }) && (dept.isNullOrBlank() || it.dept == dept) && (approveStatus.isNullOrBlank() || it.approveStatus.name == approveStatus || (approveStatus == "审核中" && it.approveStatus == GatePerson.ApproveStatus.PENDING) || (approveStatus == "通过" && it.approveStatus == GatePerson.ApproveStatus.APPROVED) || (approveStatus == "拒绝" && it.approveStatus == GatePerson.ApproveStatus.REJECTED)) && (syncStatus.isNullOrBlank() || syncStatus == it.syncStatus.name || (syncStatus == "已同步" && it.syncStatus == GatePerson.SyncStatus.SYNCED) || (syncStatus == "未同步" && it.syncStatus == GatePerson.SyncStatus.NOT_SYNCED)) }.sortedBy { it.id }.map { StoredGatePerson(requireNotNull(it.id), it.code, it.dept, it.name, it.phone, it.idCard, it.face, it.createTime.toString(), when (it.approveStatus) { GatePerson.ApproveStatus.PENDING -> "审核中"; GatePerson.ApproveStatus.APPROVED -> "通过"; GatePerson.ApproveStatus.REJECTED -> "拒绝" }, if (it.syncStatus == GatePerson.SyncStatus.SYNCED) "已同步" else "未同步") }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size); val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @GetMapping("/gate-persons/{id}")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun getGatePerson(@PathVariable id: Long): ResponseEntity<Response> {
        val person = gatePersonRepository.findById(id).orElseThrow { IllegalArgumentException("人员不存在") }
        val data = StoredGatePerson(requireNotNull(person.id), person.code, person.dept, person.name, person.phone, person.idCard, person.face, person.createTime.toString(), person.approveStatus.value(), person.syncStatus.value())
        return responseBuilder.ok().data(data).build()
    }

    @PostMapping("/gate-persons", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun createGatePersonMultipart(
        @RequestPart("code") code: String,
        @RequestPart("dept") dept: String,
        @RequestPart("name") name: String,
        @RequestPart("phone") phone: String,
        @RequestPart(name = "idCard") idCard: String,
        @RequestPart("face") face: MultipartFile,
    ): ResponseEntity<Response> {
        requireImageUpload(face)
        val file = fileService.upload(face)
        val person = GatePerson().apply {
            this.code = code
            this.dept = dept
            this.name = name
            this.phone = phone
            this.idCard = idCard
            this.face = file.downloadUrl
            createTime = LocalDateTime.now()
            updatedAt = createTime
        }
        require(!gatePersonRepository.existsByCode(person.code)) { "人员编号已存在" }
        require(!gatePersonRepository.existsByIdCard(person.idCard)) { "身份证号已存在" }
        val saved = gatePersonRepository.save(person)
        val data = StoredGatePerson(requireNotNull(saved.id), saved.code, saved.dept, saved.name, saved.phone, saved.idCard, saved.face, saved.createTime.toString(), saved.approveStatus.value(), saved.syncStatus.value())
        return responseBuilder.created().data(data).build()
    }

    @PutMapping("/gate-persons/{id}", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAuthority('gate-person:manage')")
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
        val person = gatePersonRepository.findById(id).orElseThrow { IllegalArgumentException("人员不存在") }
        code?.let { require(!gatePersonRepository.existsByCodeAndIdNot(it, id)) { "人员编号已存在" }; person.code = it }
        dept?.let { person.dept = it }
        name?.let { person.name = it }
        phone?.let { person.phone = it }
        idCard?.let { require(!gatePersonRepository.existsByIdCardAndIdNot(it, id)) { "身份证号已存在" }; person.idCard = it }
        face?.let { person.face = fileService.upload(it).downloadUrl }
        val saved = gatePersonRepository.save(person)
        val data = StoredGatePerson(requireNotNull(saved.id), saved.code, saved.dept, saved.name, saved.phone, saved.idCard, saved.face, saved.createTime.toString(), saved.approveStatus.value(), saved.syncStatus.value())
        return responseBuilder.ok().data(data).build()
    }

    @PutMapping("/gate-persons/{id}/approve")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun approveGatePerson(@PathVariable id: Long): ResponseEntity<Response> { val person = gatePersonRepository.findById(id).orElseThrow { IllegalArgumentException("人员不存在") }; person.approveStatus = GatePerson.ApproveStatus.APPROVED; gatePersonRepository.save(person); return responseBuilder.ok().message("审批通过").build() }

    @PutMapping("/gate-persons/{id}/reject")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun rejectGatePerson(@PathVariable id: Long): ResponseEntity<Response> { val person = gatePersonRepository.findById(id).orElseThrow { IllegalArgumentException("人员不存在") }; person.approveStatus = GatePerson.ApproveStatus.REJECTED; gatePersonRepository.save(person); return responseBuilder.ok().message("审批拒绝").build() }

    @DeleteMapping("/gate-persons/{id}")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun deleteGatePerson(@PathVariable id: Long): ResponseEntity<Response> { require(gatePersonRepository.existsById(id)) { "人员不存在" }; gatePersonRepository.deleteById(id); return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build() }

    @PostMapping("/gate-persons/{id}/delete-requests")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun createDeleteRequest(@PathVariable id: Long, @RequestBody body: DeleteRequestBody): ResponseEntity<Response> {
        val person = gatePersonRepository.findById(id).orElseThrow { IllegalArgumentException("人员不存在") }
        val request = GateDeleteRequest().apply {
            personId = id
            code = person.code
            dept = person.dept
            name = person.name
            phone = person.phone
            idCard = person.idCard
            face = person.face
            reason = requireNotNull(body.reason) { "删除原因不能为空" }
            applyTime = LocalDateTime.now()
        }
        val saved = gateDeleteRequestRepository.save(request)
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
        val requests = gateDeleteRequestRepository.findAll()
            .filter { request ->
                (keyword.isNullOrBlank() || listOf(request.code, request.name, request.phone, request.idCard).any { it.contains(keyword, true) }) &&
                    (status.isNullOrBlank() || request.status.name == status || request.status.value() == status)
            }
            .sortedByDescending { it.applyTime }
            .map { request -> StoredDeleteRequest(requireNotNull(request.id), request.personId, request.code, request.dept, request.name, request.phone, request.idCard, request.face, request.reason, request.applyTime.toString(), request.status.value()) }
        val from = ((page - 1) * pageSize).coerceAtMost(requests.size)
        val to = (from + pageSize).coerceAtMost(requests.size)
        val rs = Response(requests.subList(from, to), requests.size)
        return responseBuilder.ok().data(rs).build()
    }

    @Transactional
    @PutMapping("/gate-persons/delete-requests/{id}/approve")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun approveDeleteRequest(@PathVariable id: Long): ResponseEntity<Response> {
        val request = gateDeleteRequestRepository.findById(id).orElseThrow { IllegalArgumentException("删除申请不存在") }
        require(request.status == GateDeleteRequest.Status.PENDING) { "删除申请已处理" }
        require(gatePersonRepository.existsById(request.personId)) { "人员不存在" }
        gatePersonRepository.deleteById(request.personId)
        request.status = GateDeleteRequest.Status.APPROVED
        gateDeleteRequestRepository.save(request)
        return responseBuilder.ok().message("已同意删除申请").build()
    }

    @PutMapping("/gate-persons/delete-requests/{id}/reject")
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun rejectDeleteRequest(@PathVariable id: Long): ResponseEntity<Response> { val request = gateDeleteRequestRepository.findById(id).orElseThrow { IllegalArgumentException("删除申请不存在") }; require(request.status == GateDeleteRequest.Status.PENDING) { "删除申请已处理" }; request.status = GateDeleteRequest.Status.REJECTED; gateDeleteRequestRepository.save(request); return responseBuilder.ok().message("已拒绝删除申请").build() }

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
        val filtered = personAccessRecordRepository.findAll().filter {
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
            val amount: BigDecimal,
            val method: String?,
            val status: String,
            val photo: String?,
        )
        data class PageData(val items: List<VehicleRecord>, val total: Int)
        val all = accessRecordRepository.findAll().filter {
            val recordDirection = if (it.inAndOut == AccessRecord.InAndOut.IN) "进" else "出"
            (keyword.isNullOrBlank() || it.carNumber.orEmpty().contains(keyword, true) || it.carOwnerName.orEmpty().contains(keyword, true)) &&
                (direction.isNullOrBlank() || recordDirection == direction) && (gate.isNullOrBlank() || it.gateName == gate) &&
                (startDate == null || it.inAndOutTime.toLocalDate() >= startDate) &&
                (endDate == null || it.inAndOutTime.toLocalDate() <= endDate)
        }.map {
            VehicleRecord(
                requireNotNull(it.id),
                it.carNumber,
                it.carOwnerName,
                it.departmentName,
                it.inAndOutTime.toString(),
                if (it.inAndOut == AccessRecord.InAndOut.IN) "进" else "出",
                it.gateName,
                it.feeAmount,
                it.passType ?: when (it.releaseChannel) {
                    AccessRecord.ReleaseChannel.AUTOMATIC -> "车牌识别"
                    AccessRecord.ReleaseChannel.MANUAL, AccessRecord.ReleaseChannel.REMOTE -> "刷卡"
                    AccessRecord.ReleaseChannel.UNKNOWN, null -> null
                },
                it.recordStatus,
                it.photoUrl,
            )
        }.filter { passType.isNullOrBlank() || it.method == passType }.sortedByDescending { it.time }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
        return responseBuilder.ok().data(PageData(all.subList(from, to), all.size)).build()
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
        val used = spotRepository.findAll().count { it.status == 1 }
        val total = spotRepository.count().toInt()
        val violations = violationRecordRepository.findAllWithViolationType()
        val violationTypes = violations.groupingBy { it.violationType.violationName ?: "未分类" }.eachCount().entries.map { mapOf<String, Any>("name" to it.key, "value" to it.value, "color" to "#3B6DFF") }
        val violationDates = (0..6).map { LocalDate.now().minusDays((6 - it).toLong()) }
        val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val violationLabels = violationDates.map { weekLabels[it.dayOfWeek.value - 1] }
        val violationTrend = Trend(violationLabels, listOf(Series("违规次数", violationDates.map { day -> violations.count { it.violationTime.toLocalDate() == day } }, "#E9A568")))
        val accessRecords = accessRecordRepository.findAll().filter { it.inAndOutTime.toLocalDate() == LocalDate.now() }
        val inoutLabels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00")
        val inoutTrend = Trend(inoutLabels, listOf(
            Series("进场", inoutLabels.mapIndexed { index, _ -> accessRecords.count { it.inAndOut == AccessRecord.InAndOut.IN && it.inAndOutTime.hour / 4 == index } }, "#38BDF8"),
            Series("出场", inoutLabels.mapIndexed { index, _ -> accessRecords.count { it.inAndOut == AccessRecord.InAndOut.OUT && it.inAndOutTime.hour / 4 == index } }, "#6EE7B7"),
        ))
        val rs = Dashboard(
            listOf(Stat("车位总数", total, "0%", "flat", "blue"), Stat("已分配", used, "0%", "flat", "green"), Stat("空闲车位", total - used, "0%", "flat", "orange"), Stat("今日违规", violations.count { it.violationTime.toLocalDate() == java.time.LocalDate.now() }, "0%", "flat", "red")),
            spotRepository.findAll().groupBy { it.area }.entries.map { (area, rows) -> Parking(area, rows.size, rows.count { it.status == 1 }) }, violationTypes, violationTrend, inoutTrend,
        )
        return responseBuilder.ok().data(rs).build()
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

    private fun requireImageUpload(file: MultipartFile) {
        require(file.contentType?.startsWith("image/", ignoreCase = true) == true) { "人脸照片必须为图片格式" }
        val extension = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
        require(extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) { "人脸照片格式不受支持" }
    }

}
