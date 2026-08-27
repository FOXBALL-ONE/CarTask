package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.springframework.http.ResponseEntity
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
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.Position
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.service.PositionService
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
) {
    private val sequence = AtomicLong(1)
    private val owners = ConcurrentHashMap<Long, StoredOwner>()
    private val spots = ConcurrentHashMap<Long, StoredSpot>()
    private val plates = ConcurrentHashMap<Long, StoredPlate>()
    private val gatePersons = ConcurrentHashMap<Long, StoredGatePerson>()
    private val deleteRequests = ConcurrentHashMap<Long, StoredDeleteRequest>()

    @GetMapping("/depts")
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
            DepartmentData(requireNotNull(it.id), it.name, it.departmentNumber, it.superior?.id, it.sortOrder, it.director, it.contactPhone, 1)
        }
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/depts")
    fun createDepartment(@RequestBody body: DocumentDepartmentRequest): ResponseEntity<Response> {
        val department = departmentService.create(DepartmentService.CreateCommand(
            requireNotNull(body.name), requireNotNull(body.code), body.parent, body.sort ?: 0, body.leader, body.phone,
        ))
        return responseBuilder.created().data(mapOf(
            "id" to department.id, "name" to department.name, "code" to department.departmentNumber,
            "parent" to department.superior?.id, "sort" to department.sortOrder, "leader" to department.director,
            "phone" to department.contactPhone, "status" to 1,
        )).build()
    }

    @PutMapping("/depts/{id}")
    fun updateDepartment(@PathVariable id: Long, @RequestBody body: DocumentDepartmentRequest): ResponseEntity<Response> {
        val department = departmentService.update(id, DepartmentService.UpdateCommand(body.name, body.code, body.parent, body.sort, body.leader, body.phone))
        return responseBuilder.ok().data(mapOf(
            "id" to department.id, "name" to department.name, "code" to department.departmentNumber,
            "parent" to department.superior?.id, "sort" to department.sortOrder, "leader" to department.director,
            "phone" to department.contactPhone, "status" to 1,
        )).build()
    }

    @DeleteMapping("/depts/{id}")
    fun deleteDepartment(@PathVariable id: Long): ResponseEntity<Response> {
        departmentService.deleteDepartment(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @GetMapping("/posts")
    fun listPosts(): ResponseEntity<Response> {
        data class PostData(val id: Long, val name: String, val code: String, val sort: Int, val status: Int, val remark: String?)
        val rs = positionService.list(1, 100).content.map { PostData(requireNotNull(it.id), it.name, it.codeNumber, it.orderNumber, if (it.status == Position.Status.Activity) 1 else 0, null) }
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/posts")
    fun createPost(@RequestBody body: DocumentPostRequest): ResponseEntity<Response> {
        val position = Position().apply {
            name = requireNotNull(body.name)
            codeNumber = requireNotNull(body.code)
            orderNumber = body.sort ?: 0
            status = if (body.status == 0) Position.Status.BANNED else Position.Status.Activity
        }
        val saved = positionService.create(position)
        return responseBuilder.created().data(mapOf("id" to saved.id, "name" to saved.name, "code" to saved.codeNumber, "sort" to saved.orderNumber, "status" to if (saved.status == Position.Status.Activity) 1 else 0, "remark" to body.remark)).build()
    }

    @PutMapping("/posts/{id}")
    fun updatePost(@PathVariable id: Long, @RequestBody body: DocumentPostRequest): ResponseEntity<Response> {
        val position = Position().apply {
            this.id = id
            name = body.name ?: ""
            codeNumber = body.code ?: ""
            orderNumber = body.sort ?: 0
            status = if (body.status == 0) Position.Status.BANNED else Position.Status.Activity
        }
        val saved = positionService.update(id, position)
        return responseBuilder.ok().data(mapOf("id" to saved.id, "name" to saved.name, "code" to saved.codeNumber, "sort" to saved.orderNumber, "status" to if (saved.status == Position.Status.Activity) 1 else 0, "remark" to body.remark)).build()
    }

    @DeleteMapping("/posts/{id}")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Response> {
        positionService.delete(id)
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @GetMapping("/owners")
    fun listOwners(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) dept: String?,
        @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class PageData(val items: List<StoredOwner>, val total: Int, val page: Int, @param:JsonProperty("pageSize") val pageSizeValue: Int)
        val filtered = owners.values.filter {
            (keyword.isNullOrBlank() || it.cardId.contains(keyword, true) || it.name.contains(keyword, true) || it.phone.contains(keyword, true)) &&
                (dept.isNullOrBlank() || it.dept == dept) && (status == null || it.status == status)
        }.sortedBy { it.id }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size, page, pageSize)).build()
    }

    @PostMapping("/owners")
    fun createOwner(@RequestBody body: OwnerRequest): ResponseEntity<Response> {
        val id = sequence.getAndIncrement()
        val owner = StoredOwner(id, requireNotNull(body.cardId), requireNotNull(body.name), requireNotNull(body.dept), requireNotNull(body.phone), body.spotCount ?: 0, body.plateCount ?: 0, body.balance ?: BigDecimal.ZERO, body.status ?: 1)
        owners[id] = owner
        return responseBuilder.created().data(owner).build()
    }

    @PutMapping("/owners/{id}")
    fun updateOwner(@PathVariable id: Long, @RequestBody body: OwnerRequest): ResponseEntity<Response> {
        val old = owners[id] ?: throw IllegalArgumentException("车主不存在")
        val owner = StoredOwner(id, body.cardId ?: old.cardId, body.name ?: old.name, body.dept ?: old.dept, body.phone ?: old.phone, body.spotCount ?: old.spotCount, body.plateCount ?: old.plateCount, body.balance ?: old.balance, body.status ?: old.status)
        owners[id] = owner
        return responseBuilder.ok().data(owner).build()
    }

    @DeleteMapping("/owners/{id}")
    fun deleteOwner(@PathVariable id: Long): ResponseEntity<Response> {
        requireNotNull(owners.remove(id)) { "车主不存在" }
        return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build()
    }

    @PostMapping("/owners/{id}/recharge")
    fun rechargeOwner(@PathVariable id: Long, @RequestBody body: RechargeRequest): ResponseEntity<Response> {
        val old = owners[id] ?: throw IllegalArgumentException("车主不存在")
        val amount = body.amount ?: throw IllegalArgumentException("充值金额不能为空")
        require(amount > BigDecimal.ZERO) { "充值金额必须为正数" }
        val updated = old.copy(balance = old.balance + amount)
        owners[id] = updated
        return responseBuilder.ok().message("充值成功").data(mapOf("id" to id, "balance" to updated.balance)).build()
    }

    @GetMapping("/spots")
    fun listSpots(
        @RequestParam(required = false) keyword: String?, @RequestParam(required = false) area: String?,
        @RequestParam(required = false) type: String?, @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class PageData(val items: List<StoredSpot>, val total: Int)
        val filtered = spots.values.filter { (keyword.isNullOrBlank() || it.code.contains(keyword, true)) && (area.isNullOrBlank() || it.area == area) && (type.isNullOrBlank() || it.type == type) && (status == null || it.status == status) }.sortedBy { it.id }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @PostMapping("/spots")
    fun createSpot(@RequestBody body: SpotRequest): ResponseEntity<Response> {
        val id = sequence.getAndIncrement(); val spot = StoredSpot(id, requireNotNull(body.code), requireNotNull(body.area), requireNotNull(body.type), body.owner, body.status ?: 0, body.remark); spots[id] = spot
        return responseBuilder.created().data(spot).build()
    }

    @PutMapping("/spots/{id}")
    fun updateSpot(@PathVariable id: Long, @RequestBody body: SpotRequest): ResponseEntity<Response> {
        val old = spots[id] ?: throw IllegalArgumentException("车位不存在"); val spot = old.copy(code = body.code ?: old.code, area = body.area ?: old.area, type = body.type ?: old.type, owner = body.owner ?: old.owner, status = body.status ?: old.status, remark = body.remark ?: old.remark); spots[id] = spot
        return responseBuilder.ok().data(spot).build()
    }

    @DeleteMapping("/spots/{id}")
    fun deleteSpot(@PathVariable id: Long): ResponseEntity<Response> { requireNotNull(spots.remove(id)) { "车位不存在" }; return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build() }

    @GetMapping("/plates")
    fun listPlates(@RequestParam(required = false) keyword: String?, @RequestParam(required = false) status: Int?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int): ResponseEntity<Response> {
        data class PageData(val items: List<StoredPlate>, val total: Int)
        val filtered = plates.values.filter { (keyword.isNullOrBlank() || it.plate.contains(keyword, true) || it.owner.contains(keyword, true)) && (status == null || it.status == status) }.sortedBy { it.id }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size); val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @PostMapping("/plates")
    fun createPlate(@RequestBody body: PlateRequest): ResponseEntity<Response> { val id = sequence.getAndIncrement(); val plate = StoredPlate(id, requireNotNull(body.plate), requireNotNull(body.owner), requireNotNull(body.ownerId), body.status ?: 1, requireNotNull(body.regDate)); plates[id] = plate; return responseBuilder.created().data(plate).build() }

    @PutMapping("/plates/{id}")
    fun updatePlate(@PathVariable id: Long, @RequestBody body: PlateRequest): ResponseEntity<Response> { val old = plates[id] ?: throw IllegalArgumentException("车牌不存在"); val plate = old.copy(plate = body.plate ?: old.plate, owner = body.owner ?: old.owner, ownerId = body.ownerId ?: old.ownerId, status = body.status ?: old.status, regDate = body.regDate ?: old.regDate); plates[id] = plate; return responseBuilder.ok().data(plate).build() }

    @DeleteMapping("/plates/{id}")
    fun deletePlate(@PathVariable id: Long): ResponseEntity<Response> { requireNotNull(plates.remove(id)) { "车牌不存在" }; return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build() }

    @GetMapping("/gate-persons")
    fun listGatePersons(@RequestParam(required = false) keyword: String?, @RequestParam(required = false) dept: String?, @RequestParam(name = "approveStatus", required = false) approveStatus: String?, @RequestParam(name = "syncStatus", required = false) syncStatus: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int): ResponseEntity<Response> {
        data class PageData(val items: List<StoredGatePerson>, val total: Int)
        val filtered = gatePersons.values.filter { (keyword.isNullOrBlank() || listOf(it.code, it.name, it.phone, it.idCard).any { value -> value.contains(keyword, true) }) && (dept.isNullOrBlank() || it.dept == dept) && (approveStatus.isNullOrBlank() || it.approveStatus == approveStatus) && (syncStatus.isNullOrBlank() || it.syncStatus == syncStatus) }.sortedBy { it.id }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size); val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(filtered.size)
        return responseBuilder.ok().data(PageData(filtered.subList(from, to), filtered.size)).build()
    }

    @GetMapping("/gate-persons/{id}")
    fun getGatePerson(@PathVariable id: Long): ResponseEntity<Response> = responseBuilder.ok().data(gatePersons[id] ?: throw IllegalArgumentException("人员不存在")).build()

    @PostMapping("/gate-persons")
    fun createGatePerson(@RequestBody body: GatePersonRequest): ResponseEntity<Response> { val id = sequence.getAndIncrement(); val person = StoredGatePerson(id, requireNotNull(body.code), requireNotNull(body.dept), requireNotNull(body.name), requireNotNull(body.phone), requireNotNull(body.idCard), body.face, LocalDateTime.now().toString(), "审核中", "未同步"); gatePersons[id] = person; return responseBuilder.created().data(person).build() }

    @PostMapping("/gate-persons", consumes = ["multipart/form-data"])
    fun createGatePersonMultipart(
        @RequestPart code: String,
        @RequestPart dept: String,
        @RequestPart name: String,
        @RequestPart phone: String,
        @RequestPart(name = "idCard") idCard: String,
        @RequestPart face: MultipartFile,
    ): ResponseEntity<Response> = createGatePerson(GatePersonRequest(code, dept, name, phone, idCard, face.originalFilename))

    @PutMapping("/gate-persons/{id}")
    fun updateGatePerson(@PathVariable id: Long, @RequestBody body: GatePersonRequest): ResponseEntity<Response> { val old = gatePersons[id] ?: throw IllegalArgumentException("人员不存在"); val person = old.copy(code = body.code ?: old.code, dept = body.dept ?: old.dept, name = body.name ?: old.name, phone = body.phone ?: old.phone, idCard = body.idCard ?: old.idCard, face = body.face ?: old.face); gatePersons[id] = person; return responseBuilder.ok().data(person).build() }

    @PutMapping("/gate-persons/{id}", consumes = ["multipart/form-data"])
    fun updateGatePersonMultipart(
        @PathVariable id: Long,
        @RequestPart(required = false) code: String?,
        @RequestPart(required = false) dept: String?,
        @RequestPart(required = false) name: String?,
        @RequestPart(required = false) phone: String?,
        @RequestPart(name = "idCard", required = false) idCard: String?,
        @RequestPart(required = false) face: MultipartFile?,
    ): ResponseEntity<Response> = updateGatePerson(id, GatePersonRequest(code, dept, name, phone, idCard, face?.originalFilename))

    @PutMapping("/gate-persons/{id}/approve")
    fun approveGatePerson(@PathVariable id: Long): ResponseEntity<Response> { val old = gatePersons[id] ?: throw IllegalArgumentException("人员不存在"); gatePersons[id] = old.copy(approveStatus = "通过"); return responseBuilder.ok().message("审批通过").build() }

    @PutMapping("/gate-persons/{id}/reject")
    fun rejectGatePerson(@PathVariable id: Long): ResponseEntity<Response> { val old = gatePersons[id] ?: throw IllegalArgumentException("人员不存在"); gatePersons[id] = old.copy(approveStatus = "拒绝"); return responseBuilder.ok().message("审批拒绝").build() }

    @DeleteMapping("/gate-persons/{id}")
    fun deleteGatePerson(@PathVariable id: Long): ResponseEntity<Response> { requireNotNull(gatePersons.remove(id)) { "人员不存在" }; return responseBuilder.ok().message("删除成功").data(mapOf("id" to id)).build() }

    @GetMapping("/gate-persons/delete-requests")
    fun listDeleteRequests(): ResponseEntity<Response> = responseBuilder.ok().data(deleteRequests.values.sortedBy { it.id }).build()

    @PutMapping("/gate-persons/delete-requests/{id}/approve")
    fun approveDeleteRequest(@PathVariable id: Long): ResponseEntity<Response> { val request = deleteRequests[id] ?: throw IllegalArgumentException("删除申请不存在"); deleteRequests[id] = request.copy(status = "已同意"); gatePersons.remove(request.personId); return responseBuilder.ok().message("已同意删除申请").build() }

    @PutMapping("/gate-persons/delete-requests/{id}/reject")
    fun rejectDeleteRequest(@PathVariable id: Long): ResponseEntity<Response> { val request = deleteRequests[id] ?: throw IllegalArgumentException("删除申请不存在"); deleteRequests[id] = request.copy(status = "已拒绝"); return responseBuilder.ok().message("已拒绝删除申请").build() }

    @GetMapping("/person-records")
    fun personRecords(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false) gate: String?,
        @RequestParam(name = "passType", required = false) passType: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(mapOf("items" to emptyList<Any>(), "total" to 0, "page" to page, "pageSize" to pageSize)).build()

    @GetMapping("/vehicle-records")
    fun vehicleRecords(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
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
        data class PageData(val items: List<VehicleRecord>, val total: Int, val page: Int, @param:JsonProperty("pageSize") val pageSizeValue: Int)
        val all = accessRecordRepository.findAll().filter {
            val recordDirection = if (it.inAndOut == AccessRecord.InAndOut.IN) "进" else "出"
            (keyword.isNullOrBlank() || it.carNumber.orEmpty().contains(keyword, true) || it.carOwnerName.orEmpty().contains(keyword, true)) &&
                (direction.isNullOrBlank() || recordDirection == direction) &&
                (startDate == null || it.inAndOutTime.toLocalDate() >= startDate) &&
                (endDate == null || it.inAndOutTime.toLocalDate() <= endDate)
        }.map {
            VehicleRecord(
                requireNotNull(it.id),
                it.carNumber,
                it.carOwnerName,
                null,
                it.inAndOutTime.toString(),
                if (it.inAndOut == AccessRecord.InAndOut.IN) "进" else "出",
                null,
                BigDecimal.ZERO,
                it.releaseChannel?.name,
                "正常",
                null,
            )
        }
        val from = ((page - 1).coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
        val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
        return responseBuilder.ok().data(PageData(all.subList(from, to), all.size, page, pageSize)).build()
    }

    @GetMapping("/login-logs")
    fun loginLogs(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(mapOf("items" to emptyList<Any>(), "total" to 0, "page" to page, "pageSize" to pageSize)).build()

    @GetMapping("/operation-logs")
    fun operationLogs(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) module: String?,
        @RequestParam(name = "startDate", required = false) startDate: LocalDate?,
        @RequestParam(name = "endDate", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(mapOf("items" to emptyList<Any>(), "total" to 0, "page" to page, "pageSize" to pageSize)).build()

    @GetMapping("/dashboard")
    fun dashboard(): ResponseEntity<Response> {
        data class Stat(val label: String, val value: Int, val delta: String, val trend: String, val color: String)
        data class Parking(val area: String, val total: Int, val used: Int)
        data class Series(val name: String, val data: List<Int>, val color: String)
        data class Trend(val labels: List<String>, val series: List<Series>)
        data class Dashboard(val stats: List<Stat>, val parking: List<Parking>, @param:JsonProperty("violationTypes") val violationTypes: List<Map<String, Any>>, @param:JsonProperty("violationTrend") val violationTrend: Trend, @param:JsonProperty("inoutTrend") val inoutTrend: Trend)
        val used = spots.values.count { it.status == 1 }
        val total = spots.size
        val rs = Dashboard(
            listOf(Stat("车位总数", total, "0%", "flat", "blue"), Stat("已分配", used, "0%", "flat", "green"), Stat("空闲车位", total - used, "0%", "flat", "orange"), Stat("今日违规", 0, "0%", "flat", "red")),
            listOf(Parking("全部", total, used)), emptyList(), Trend(emptyList(), emptyList()), Trend(emptyList(), emptyList()),
        )
        return responseBuilder.ok().data(rs).build()
    }

}
