package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.service.AccessRecordService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-records")

/** class AccessRecordController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class AccessRecordController(
    private val service: AccessRecordService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping
    @PreAuthorize("denyAll()")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun create(@RequestBody entity: AccessRecord): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()
    
    
    @PostMapping("/batch")
    @PreAuthorize("denyAll()")
            /** createBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createBatch(@RequestBody entities: List<AccessRecord>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()
    
    
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:read')")
            /** getBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:correct')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-record:correct')")
            /** updateBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
            
            
            /** release：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("denyAll()")
            /** delete：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }
    
    
    @DeleteMapping("/batch")
    @PreAuthorize("denyAll()")
            /** deleteBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
