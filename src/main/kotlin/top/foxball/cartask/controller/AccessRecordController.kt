package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
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
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.service.AccessRecordService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-records")
/** 车辆进出记录的查询与管理接口。 */
class AccessRecordController(
    private val service: AccessRecordService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 创建一条实体记录。 */
    @PostMapping
    @PreAuthorize("denyAll()")
    fun create(@RequestBody entity: AccessRecord): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()

    /** 批量创建实体记录。 */
    @PostMapping("/batch")
    @PreAuthorize("denyAll()")
    fun createBatch(@RequestBody entities: List<AccessRecord>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()

    /** 按主键获取一条进出记录，返回前端进出记录展示格式。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val passType: String?,
            val passDesc: String?,
            val photo: String?,
        )

        val record = service.get(id)
        val rs = Response(
            record.id,
            record.plate,
            record.owner,
            record.dept,
            record.time,
            record.direction,
            record.gate,
            record.vehicleType,
            record.passType,
            record.passDesc,
            record.photo,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 按多个主键批量获取进出记录。 */
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:read')")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class RecordData(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val passType: String?,
            val passDesc: String?,
            val photo: String?,
        )
        data class Response(val records: List<RecordData>)

        val records = service.getBatch(id)
        val rs = Response(records.map {
            RecordData(
                it.id,
                it.plate,
                it.owner,
                it.dept,
                it.time,
                it.direction,
                it.gate,
                it.vehicleType,
                it.passType,
                it.passDesc,
                it.photo,
            )
        })
        return responseBuilder.ok().data(rs).build()
    }

    /** 分页查询进出记录。 */
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> {
        data class RecordData(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val passType: String?,
            val passDesc: String?,
            val photo: String?,
        )
        data class Response(
            val records: List<RecordData>,
            val page: Int,
            @param:JsonProperty("page_size") val pageSize: Int,
            val total: Long,
        )

        val result = service.list(page, pageSize)
        val rs = Response(
            result.records.map {
                RecordData(
                    it.id,
                    it.plate,
                    it.owner,
                    it.dept,
                    it.time,
                    it.direction,
                    it.gate,
                    it.vehicleType,
                    it.passType,
                    it.passDesc,
                    it.photo,
                )
            },
            result.page,
            result.pageSize,
            result.total,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 更新指定主键的实体记录。 */
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:correct')")
    fun update(
        @PathVariable id: Long,
        @RequestParam(name = "correction_reason") correctionReason: String,
        @RequestBody entity: AccessRecord,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val passType: String?,
            val passDesc: String?,
            val photo: String?,
        )

        val record = service.correct(id, entity, correctionReason)
        val rs = Response(
            record.id,
            record.plate,
            record.owner,
            record.dept,
            record.time,
            record.direction,
            record.gate,
            record.vehicleType,
            record.passType,
            record.passDesc,
            record.photo,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 批量更新实体记录。 */
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:correct')")
    fun updateBatch(
        @RequestParam(name = "correction_reason") correctionReason: String,
        @RequestBody entities: List<AccessRecord>,
    ): ResponseEntity<Response> {
        data class RecordData(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val passType: String?,
            val passDesc: String?,
            val photo: String?,
        )
        data class Response(val records: List<RecordData>)

        val records = service.correctBatch(entities, correctionReason)
        val rs = Response(records.map {
            RecordData(
                it.id,
                it.plate,
                it.owner,
                it.dept,
                it.time,
                it.direction,
                it.gate,
                it.vehicleType,
                it.passType,
                it.passDesc,
                it.photo,
            )
        })
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:release')")
    fun release(
        @PathVariable id: Long,
        @RequestParam(name = "release_reason") releaseReason: String,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val plate: String?,
            val owner: String?,
            val dept: String?,
            val time: String,
            val direction: String,
            val gate: String?,
            val vehicleType: String?,
            val passType: String?,
            val passDesc: String?,
            val photo: String?,
        )

        val record = service.release(id, releaseReason)
        val rs = Response(
            record.id,
            record.plate,
            record.owner,
            record.dept,
            record.time,
            record.direction,
            record.gate,
            record.vehicleType,
            record.passType,
            record.passDesc,
            record.photo,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 删除指定主键的实体记录。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("denyAll()")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }

    /** 批量删除实体记录。 */
    @DeleteMapping("/batch")
    @PreAuthorize("denyAll()")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
