package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Device
import top.foxball.cartask.service.DeviceService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/devices")
/** 接入设备的管理接口。 */
class DeviceController(
    private val service: DeviceService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 文档兼容的 JSON 设备创建入口。 */
    @PostMapping(consumes = ["application/json"])
    @PreAuthorize("hasAuthority('device:manage')")
    fun createDocument(@RequestBody body: DocumentDeviceRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val code: String?,
            val name: String?,
            val type: String?,
            val brand: String?,
            val model: String?,
            val location: String?,
            val ip: String?,
            val status: Int,
            @param:JsonProperty("installDate") val installDate: String?,
        )

        val device = Device().apply {
            deviceCode = requireNotNull(body.code) { "设备编号不能为空" }
            deviceName = requireNotNull(body.name) { "设备名称不能为空" }
            deviceType = requireNotNull(body.type) { "设备类型不能为空" }
            brand = requireNotNull(body.brand) { "设备品牌不能为空" }
            model = requireNotNull(body.model) { "设备型号不能为空" }
            location = requireNotNull(body.location) { "设备安装位置不能为空" }
            ip = requireNotNull(body.ip) { "设备 IP 地址不能为空" }
            installDate = requireNotNull(body.installDate) { "设备安装日期不能为空" }.also { LocalDate.parse(it) }
            status = requireNotNull(body.status) { "设备状态不能为空" }.let { value -> require(value == 0 || value == 1) { "状态必须为 0 或 1" }; if (value == 0) Device.Status.BANNED else Device.Status.Activity }
        }
        val saved = service.create(device)
        val savedId = requireNotNull(saved.id)
        val rs = Response(
            savedId, saved.deviceCode, saved.deviceName, saved.deviceType, saved.brand, saved.model,
            saved.location, saved.ip, if (saved.status == Device.Status.Activity) 1 else 0, saved.installDate,
        )
        return responseBuilder.created().data(rs).build()
    }

    /** 文档兼容的 JSON 设备更新入口。 */
    @PutMapping("/{id}", consumes = ["application/json"])
    @PreAuthorize("hasAuthority('device:manage')")
    fun updateDocument(@PathVariable id: Long, @RequestBody body: DocumentDeviceRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val code: String?,
            val name: String?,
            val type: String?,
            val brand: String?,
            val model: String?,
            val location: String?,
            val ip: String?,
            val status: Int,
            @param:JsonProperty("installDate") val installDate: String?,
        )

        require(body.status == null || body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val current = service.get(id)
        val device = Device().apply {
            this.id = id
            deviceCode = body.code ?: current.deviceCode
            deviceName = body.name ?: current.deviceName
            deviceType = body.type ?: current.deviceType
            brand = body.brand ?: current.brand
            model = body.model ?: current.model
            location = body.location ?: current.location
            ip = body.ip ?: current.ip
            installDate = body.installDate?.also { LocalDate.parse(it) } ?: current.installDate
            orderNumber = current.orderNumber
            status = body.status?.let { if (it == 0) Device.Status.BANNED else Device.Status.Activity } ?: current.status
        }
        val saved = service.update(id, device)
        val rs = Response(
            requireNotNull(saved.id), saved.deviceCode, saved.deviceName, saved.deviceType, saved.brand, saved.model,
            saved.location, saved.ip, if (saved.status == Device.Status.Activity) 1 else 0, saved.installDate,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 文档兼容的设备列表入口。 */
    @GetMapping
    @PreAuthorize("hasAuthority('device:read')")
    fun listDocument(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class DeviceData(
            val id: Long,
            val code: String?,
            val name: String?,
            val type: String?,
            val brand: String?,
            val model: String?,
            val location: String?,
            val ip: String?,
            val status: Int,
            @param:JsonProperty("installDate") val installDate: String?,
        )
        data class Response(
            val items: List<DeviceData>,
            val total: Int,
        )

        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val allDevices = mutableListOf<Device>()
        var sourcePage = 1
        var sourceTotal = 0L
        do {
            val source = service.list(sourcePage, 100)
            allDevices += source.content
            sourceTotal = source.totalElements
            sourcePage++
        } while (allDevices.size < sourceTotal)
        val filtered = allDevices.asSequence()
            .filter { keyword.isNullOrBlank() || it.deviceCode?.contains(keyword, true) == true || it.deviceName?.contains(keyword, true) == true }
            .filter { type.isNullOrBlank() || it.deviceType == type }
            .filter { status == null || (if (it.status == Device.Status.Activity) 1 else 0) == status }
            .map {
                DeviceData(
                    requireNotNull(it.id), it.deviceCode, it.deviceName, it.deviceType,
                    it.brand, it.model, it.location, it.ip,
                    if (it.status == Device.Status.Activity) 1 else 0, it.installDate,
                )
            }
            .toList()
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        val rs = Response(filtered.subList(from, to), filtered.size)
        return responseBuilder.ok().data(rs).build()
    }

    /** 创建一条实体记录。 */
    @PostMapping(consumes = ["application/x-www-form-urlencoded"])
    @PreAuthorize("hasAuthority('device:manage')")
    fun create(@RequestBody entity: Device): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()

    /** 批量创建实体记录。 */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('device:manage')")
    fun createBatch(@RequestBody entities: List<Device>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()

    /** 按主键获取一条实体记录。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id)).build()

    /** 按多个主键批量获取实体记录。 */
    @GetMapping("/batch")
    @PreAuthorize("hasAuthority('device:read')")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()

    /** 分页查询实体记录。 */
    @GetMapping(params = ["page_size"])
    @PreAuthorize("hasAuthority('device:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()

    /** 更新指定主键的实体记录。 */
    @PutMapping("/{id}", consumes = ["application/x-www-form-urlencoded"])
    @PreAuthorize("hasAuthority('device:manage')")
    fun update(@PathVariable id: Long, @RequestBody entity: Device): ResponseEntity<Response> =
        responseBuilder.ok().data(service.update(id, entity)).build()

    /** 批量更新实体记录。 */
    @PutMapping("/batch")
    @PreAuthorize("hasAuthority('device:manage')")
    fun updateBatch(@RequestBody entities: List<Device>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities)).build()

    /** 删除指定主键的实体记录。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:manage')")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }

    /** 批量删除实体记录。 */
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('device:manage')")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
