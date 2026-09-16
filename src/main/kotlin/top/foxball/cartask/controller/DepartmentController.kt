package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/departments")

/** class DepartmentController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class DepartmentController(
    private val departmentService: DepartmentService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping
    @PreAuthorize("hasAuthority('department:manage')")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun create(
        @RequestParam name: String,
        @RequestParam(name = "department_code") departmentCode: String,
        @RequestParam(name = "superior_id", required = false) superiorId: Long?,
        @RequestParam(name = "sort_order", defaultValue = "0") sortOrder: Int,
        @RequestParam(required = false) director: String?,
        @RequestParam(name = "contact_phone", required = false) contactPhone: String?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        val department = departmentService.create(
            DepartmentService.CreateCommand(name, departmentCode, superiorId, sortOrder, director, contactPhone),
        )
        val rs = Response(
            department.id!!,
            department.name,
            department.departmentNumber,
            department.superior?.id,
            department.sortOrder,
            department.director,
            department.contactPhone,
        )
        return responseBuilder.created().data(rs).build()
    }
    
    
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('department:manage')")
            /** createBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createBatch(
        @RequestParam name: List<String>,
        @RequestParam(name = "department_code") departmentCode: List<String>,
        @RequestParam(name = "superior_id", required = false) superiorId: List<Long>?,
        @RequestParam(name = "sort_order", required = false) sortOrder: List<Int>?,
        @RequestParam(required = false) director: List<String>?,
        @RequestParam(name = "contact_phone", required = false) contactPhone: List<String>?,
    ): ResponseEntity<Response> {
        data class DepartmentData(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        data class Response(val departments: List<DepartmentData>)
        
        require(name.isNotEmpty()) { "部门列表不能为空" }
        require(name.size == departmentCode.size) { "部门名称和编码数量必须一致" }
        require(superiorId == null || superiorId.size == name.size) { "上级部门数量必须与部门数量一致" }
        require(sortOrder == null || sortOrder.size == name.size) { "排序值数量必须与部门数量一致" }
        require(director == null || director.size == name.size) { "负责人数量必须与部门数量一致" }
        require(contactPhone == null || contactPhone.size == name.size) { "联系电话数量必须与部门数量一致" }
        val departments = departmentService.createDepartments(name.indices.map {
            DepartmentService.CreateCommand(
                name[it],
                departmentCode[it],
                superiorId?.getOrNull(it),
                sortOrder?.getOrNull(it) ?: 0,
                director?.getOrNull(it),
                contactPhone?.getOrNull(it),
            )
        })
        val rs = Response(departments.map {
            DepartmentData(
                it.id!!,
                it.name,
                it.departmentNumber,
                it.superior?.id,
                it.sortOrder,
                it.director,
                it.contactPhone,
            )
        })
        return responseBuilder.created().data(rs).build()
    }
    
    
    @PostMapping("/{superiorId}/children/batch")
    @PreAuthorize("hasAuthority('department:manage')")
            /** createChildrenBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createChildrenBatch(
        @PathVariable superiorId: Long,
        @RequestParam name: List<String>,
        @RequestParam(name = "department_code") departmentCode: List<String>,
        @RequestParam(name = "sort_order", required = false) sortOrder: List<Int>?,
        @RequestParam(required = false) director: List<String>?,
        @RequestParam(name = "contact_phone", required = false) contactPhone: List<String>?,
    ): ResponseEntity<Response> {
        data class DepartmentData(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        data class Response(val departments: List<DepartmentData>)
        
        require(name.isNotEmpty()) { "下级部门列表不能为空" }
        require(name.size == departmentCode.size) { "部门名称和编码数量必须一致" }
        require(sortOrder == null || sortOrder.size == name.size) { "排序值数量必须与部门数量一致" }
        require(director == null || director.size == name.size) { "负责人数量必须与部门数量一致" }
        require(contactPhone == null || contactPhone.size == name.size) { "联系电话数量必须与部门数量一致" }
        val departments = departmentService.createDepartments(name.indices.map {
            DepartmentService.CreateCommand(
                name[it],
                departmentCode[it],
                superiorId,
                sortOrder?.getOrNull(it) ?: 0,
                director?.getOrNull(it),
                contactPhone?.getOrNull(it),
            )
        })
        val rs = Response(departments.map {
            DepartmentData(
                it.id!!,
                it.name,
                it.departmentNumber,
                it.superior?.id,
                it.sortOrder,
                it.director,
                it.contactPhone,
            )
        })
        return responseBuilder.created().data(rs).build()
    }
    
    
    @GetMapping
    @PreAuthorize("hasAuthority('department:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun list(): ResponseEntity<Response> {
        data class DepartmentData(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        data class Response(
            val departments: List<DepartmentData>,
            val total: Int,
        )
        
        val departments = departmentService.listAll()
        val rs = Response(departments.map {
            DepartmentData(
                it.id!!,
                it.name,
                it.departmentNumber,
                it.superior?.id,
                it.sortOrder,
                it.director,
                it.contactPhone,
            )
        }, departments.size)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('department:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        val department = departmentService.get(id)
        val rs = Response(
            department.id!!,
            department.name,
            department.departmentNumber,
            department.superior?.id,
            department.sortOrder,
            department.director,
            department.contactPhone,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/batch")
    @PreAuthorize("hasAuthority('department:read')")
            /** getBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class DepartmentData(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        data class Response(val departments: List<DepartmentData>)
        
        val departments = departmentService.getBatch(id)
        val rs = Response(departments.map {
            DepartmentData(
                it.id!!,
                it.name,
                it.departmentNumber,
                it.superior?.id,
                it.sortOrder,
                it.director,
                it.contactPhone,
            )
        })
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun update(
        @PathVariable id: Long,
        @RequestParam(required = false) name: String?,
        @RequestParam(name = "department_code", required = false) departmentCode: String?,
        @RequestParam(name = "superior_id", required = false) superiorId: Long?,
        @RequestParam(name = "sort_order", required = false) sortOrder: Int?,
        @RequestParam(required = false) director: String?,
        @RequestParam(name = "contact_phone", required = false) contactPhone: String?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val name: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("superior_id") val superiorId: Long?,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            val director: String?,
            @param:JsonProperty("contact_phone") val contactPhone: String?,
        )
        
        val department = departmentService.update(
            id,
            DepartmentService.UpdateCommand(name, departmentCode, superiorId, sortOrder, director, contactPhone),
        )
        val rs = Response(
            department.id!!,
            department.name,
            department.departmentNumber,
            department.superior?.id,
            department.sortOrder,
            department.director,
            department.contactPhone,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/batch")
    @PreAuthorize("hasAuthority('department:manage')")
            /** updateBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateBatch(
        @RequestParam id: List<Long>,
        @RequestParam(required = false) name: String?,
        @RequestParam(name = "department_code", required = false) departmentCode: String?,
        @RequestParam(name = "superior_id", required = false) superiorId: Long?,
        @RequestParam(name = "sort_order", required = false) sortOrder: Int?,
        @RequestParam(required = false) director: String?,
        @RequestParam(name = "contact_phone", required = false) contactPhone: String?,
    ): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("department_ids") val departmentIds: List<Long>)
        
        departmentService.updateBatch(
            id,
            DepartmentService.UpdateCommand(name, departmentCode, superiorId, sortOrder, director, contactPhone),
        )
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
            /** delete：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long)
        
        departmentService.deleteDepartment(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('department:manage')")
            /** deleteBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("department_ids") val departmentIds: List<Long>)
        
        departmentService.deleteDepartments(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
}
