package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/departments")
/** 组织部门的树形和批量管理接口。 */
class DepartmentController(
    private val departmentService: DepartmentService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 创建一个部门。 */
    @PostMapping
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

    /** 批量创建部门。所有字段按数组下标对应。 */
    @PostMapping("/batch")
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

    /** 向指定部门批量添加多个下级部门。 */
    @PostMapping("/{superiorId}/children/batch")
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

    /** 查询全部部门，返回扁平化部门树节点供前端构建树。 */
    @GetMapping
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

    /** 按部门 ID 查询部门。 */
    @GetMapping("/{id:[0-9]+}")
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

    /** 按多个部门 ID 批量查询部门。 */
    @GetMapping("/batch")
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

    /** 更新一个部门。未传入的字段保持不变。 */
    @PutMapping("/{id}")
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

    /** 对多个部门应用相同的更新内容。 */
    @PutMapping("/batch")
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

    /** 删除一个部门。 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long)

        departmentService.deleteDepartment(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }

    /** 批量删除部门。 */
    @DeleteMapping("/batch")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("department_ids") val departmentIds: List<Long>)

        departmentService.deleteDepartments(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
}
