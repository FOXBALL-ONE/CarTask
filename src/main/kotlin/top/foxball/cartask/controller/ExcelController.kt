package top.foxball.cartask.controller

import com.alibaba.excel.EasyExcel
import com.alibaba.excel.annotation.ExcelProperty
import jakarta.transaction.Transactional
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.*
import top.foxball.cartask.repository.*
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ExcelResourcePolicy
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.service.DeviceService
import top.foxball.cartask.service.PositionService
import top.foxball.cartask.service.UserService
import top.foxball.cartask.shared.GatePersonFields
import top.foxball.cartask.shared.PlateNumbers
import top.foxball.cartask.shared.ResponseBuilder
import top.foxball.cartask.shared.VehicleInspection
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime

private const val XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class DepartmentExcelRow {
    @field:ExcelProperty("部门编码")
    var code: String? = null
    @field:ExcelProperty("部门名称")
    var name: String? = null
    @field:ExcelProperty("上级部门编码")
    var parentCode: String? = null
}

class UserExcelRow {
    @field:ExcelProperty("部门编码")
    var departmentCode: String? = null
    @field:ExcelProperty("岗位编码")
    var positionCode: String? = null

    @field:ExcelProperty("账号")
    var username: String? = null

    @field:ExcelProperty("姓名")
    var name: String? = null

    @field:ExcelProperty("邮箱")
    var email: String? = null

    @field:ExcelProperty("密码")
    var password: String? = null

    @field:ExcelProperty("手机号")
    var phone: String? = null

    @field:ExcelProperty("性别")
    var gender: String? = null

    @field:ExcelProperty("部门ID")
    var departmentId: Long? = null

    @field:ExcelProperty("岗位ID")
    var positionId: Long? = null

    @field:ExcelProperty("角色编码")
    var role: String? = null

    @field:ExcelProperty("状态")
    var status: String? = null

    @field:ExcelProperty("启用")
    var enabled: String? = null
}

data class UserExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("账号") val username: String,
    @field:ExcelProperty("姓名") val name: String?,
    @field:ExcelProperty("邮箱") val email: String,
    @field:ExcelProperty("手机号") val phone: String?,
    @field:ExcelProperty("性别") val gender: String,
    @field:ExcelProperty("部门名称") val departmentName: String?,
    @field:ExcelProperty("岗位名称") val positionName: String?,
    @field:ExcelProperty("角色编码") val role: String,
    @field:ExcelProperty("状态") val status: String,
    @field:ExcelProperty("启用") val enabled: String,
)

class PositionExcelRow {
    @field:ExcelProperty("岗位名称")
    var name: String? = null

    @field:ExcelProperty("岗位编码")
    var code: String? = null

    @field:ExcelProperty("显示排序")
    var sort: Int? = null

    @field:ExcelProperty("状态")
    var status: String? = null

    @field:ExcelProperty("备注")
    var remark: String? = null
}

data class PositionExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("岗位名称") val name: String,
    @field:ExcelProperty("岗位编码") val code: String,
    @field:ExcelProperty("显示排序") val sort: Int,
    @field:ExcelProperty("状态") val status: String,
    @field:ExcelProperty("备注") val remark: String?,
)

class OwnerExcelRow {
    @field:ExcelProperty("卡号")
    var cardId: String? = null
    @field:ExcelProperty("姓名")
    var name: String? = null
    @field:ExcelProperty("部门")
    var dept: String? = null
    @field:ExcelProperty("手机号")
    var phone: String? = null
    @field:ExcelProperty("车位数量")
    var spotCount: Int? = null
    @field:ExcelProperty("车牌数量")
    var plateCount: Int? = null
    @field:ExcelProperty("余额")
    var balance: String? = null
    @field:ExcelProperty("状态")
    var status: Int? = null
}

data class OwnerExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("卡号") val cardId: String,
    @field:ExcelProperty("姓名") val name: String,
    @field:ExcelProperty("部门") val dept: String,
    @field:ExcelProperty("手机号") val phone: String,
    @field:ExcelProperty("车位数量") val spotCount: Int,
    @field:ExcelProperty("车牌数量") val plateCount: Int,
    @field:ExcelProperty("余额") val balance: String,
    @field:ExcelProperty("状态") val status: String,
)

class SpotExcelRow {
    @field:ExcelProperty("车位编号")
    var code: String? = null
    @field:ExcelProperty("区域")
    var area: String? = null
    @field:ExcelProperty("类型")
    var type: String? = null
    @field:ExcelProperty("车主姓名")
    var owner: String? = null
    @field:ExcelProperty("状态")
    var status: Int? = null
    @field:ExcelProperty("备注")
    var remark: String? = null
}

data class SpotExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("车位编号") val code: String,
    @field:ExcelProperty("区域") val area: String,
    @field:ExcelProperty("类型") val type: String,
    @field:ExcelProperty("车主姓名") val owner: String?,
    @field:ExcelProperty("状态") val status: String,
    @field:ExcelProperty("备注") val remark: String?,
)

class PlateExcelRow {
    @field:ExcelProperty("车主卡号")
    var ownerCard: String? = null
    @field:ExcelProperty("车牌号")
    var plate: String? = null
    @field:ExcelProperty("车主ID")
    var ownerId: Long? = null
    @field:ExcelProperty("状态")
    var status: Int? = null
    @field:ExcelProperty("登记日期")
    var regDate: String? = null
    @field:ExcelProperty("车辆类型")
    var carBrand: String? = null
}

data class PlateExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("车牌号") val plate: String,
    @field:ExcelProperty("车主") val owner: String,
    @field:ExcelProperty("车主ID") val ownerId: Long,
    @field:ExcelProperty("状态") val status: String,
    @field:ExcelProperty("登记日期") val regDate: String,
    @field:ExcelProperty("车辆类型") val carBrand: String,
)

class PlateInspectionExcelRow {
    @field:ExcelProperty("车牌号")
    var plate: String? = null
    @field:ExcelProperty("是否已年检")
    var inspected: String? = null
    @field:ExcelProperty("年检日期")
    var inspectionDate: String? = null
    @field:ExcelProperty("年检有效期至")
    var validUntil: String? = null
    @field:ExcelProperty("备注")
    var remark: String? = null
}

data class PlateInspectionExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("车牌号") val plate: String,
    @field:ExcelProperty("车主") val owner: String,
    @field:ExcelProperty("登记日期") val regDate: String,
    @field:ExcelProperty("年检状态") val inspectionStatus: String,
    @field:ExcelProperty("是否已年检") val inspected: String,
    @field:ExcelProperty("年检日期") val inspectionDate: String?,
    @field:ExcelProperty("年检有效期至") val inspectionValidUntil: String?,
    @field:ExcelProperty("备注") val inspectionRemark: String?,
)

class DeviceExcelRow {
    @field:ExcelProperty("设备编号")
    var code: String? = null
    @field:ExcelProperty("设备名称")
    var name: String? = null
    @field:ExcelProperty("设备类型")
    var type: String? = null
    @field:ExcelProperty("品牌")
    var brand: String? = null
    @field:ExcelProperty("型号")
    var model: String? = null
    @field:ExcelProperty("安装位置")
    var location: String? = null
    @field:ExcelProperty("IP地址")
    var ip: String? = null
    @field:ExcelProperty("安装日期")
    var installDate: String? = null
    @field:ExcelProperty("状态")
    var status: String? = null
    @field:ExcelProperty("显示排序")
    var sort: Int? = null
}

data class DeviceExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("设备编号") val code: String?,
    @field:ExcelProperty("设备名称") val name: String?,
    @field:ExcelProperty("设备类型") val type: String?,
    @field:ExcelProperty("品牌") val brand: String?,
    @field:ExcelProperty("型号") val model: String?,
    @field:ExcelProperty("安装位置") val location: String?,
    @field:ExcelProperty("IP地址") val ip: String?,
    @field:ExcelProperty("安装日期") val installDate: String?,
    @field:ExcelProperty("状态") val status: String,
    @field:ExcelProperty("显示排序") val sort: Int,
)

/**
 * 门禁人员导入行。
 *
 * 这里只有真正会被导入的列。审核与同步状态不开放给样表：导入一律落在待审核、未同步，
 * 让样表里出现无法生效的「审核状态」列，只会让填表人以为批量预审核已经生效。
 */
class GatePersonExcelRow {
    @field:ExcelProperty("人员编号")
    var code: String? = null
    @field:ExcelProperty("部门")
    var dept: String? = null
    @field:ExcelProperty("姓名")
    var name: String? = null
    @field:ExcelProperty("手机号")
    var phone: String? = null
    @field:ExcelProperty("身份证号")
    var idCard: String? = null
}

data class GatePersonExportRow(
    @field:ExcelProperty("编号") val id: Long,
    @field:ExcelProperty("人员编号") val code: String,
    @field:ExcelProperty("部门") val dept: String,
    @field:ExcelProperty("姓名") val name: String,
    @field:ExcelProperty("手机号") val phone: String,
    @field:ExcelProperty("身份证号") val idCard: String,
    @field:ExcelProperty("创建时间") val createTime: String,
    @field:ExcelProperty("审核状态") val approveStatus: String,
    @field:ExcelProperty("同步状态") val syncStatus: String,
)

@RestController
@RequestMapping("/api/excel")
/** 系统基础资料、车辆资料、设备及门禁人员的 Excel 模板、导入和导出接口。 */
class ExcelController(
    private val userService: UserService,
    private val departmentService: DepartmentService,
    private val positionService: PositionService,
    private val deviceService: DeviceService,
    private val deviceRepository: DeviceRepository,
    private val departmentRepository: DepartmentRepository,
    private val positionRepository: PositionRepository,
    private val ownerRepository: ParkingOwnerRepository,
    private val spotRepository: ParkingSpotRepository,
    private val plateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
    private val responseBuilder: ResponseBuilder,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val excelResourcePolicy: ExcelResourcePolicy,
    private val auditService: AuditService,
) {
    @GetMapping("/{resource}/template")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and ((#resource == 'users' and hasAuthority('user:read')) or (#resource == 'positions' and hasAuthority('position:read')) or (#resource == 'owners' and hasAuthority('owner:read')) or (#resource == 'spots' and hasAuthority('spot:read')) or (#resource == 'plates' and hasAuthority('plate:read')) or (#resource == 'plate-inspections' and hasAuthority('plate:read')) or (#resource == 'devices' and hasAuthority('device:read')) or (#resource == 'gate-persons' and hasAuthority('gate-person:read')))")
            /**
             * template：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param resource 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun template(@PathVariable resource: String): ResponseEntity<ByteArrayResource> {
        excelResourcePolicy.requireScopable(resource)
        val (filename, rows, type) = when (resource) {
            "users" -> Triple("用户导入模板.xlsx", listOf(UserExcelRow()), UserExcelRow::class.java)
            "positions" -> Triple("岗位导入模板.xlsx", listOf(PositionExcelRow()), PositionExcelRow::class.java)
            "owners" -> Triple("车主导入模板.xlsx", listOf(OwnerExcelRow()), OwnerExcelRow::class.java)
            "spots" -> Triple("车位导入模板.xlsx", listOf(SpotExcelRow()), SpotExcelRow::class.java)
            "plates" -> Triple("车牌导入模板.xlsx", listOf(PlateExcelRow()), PlateExcelRow::class.java)
            "plate-inspections" -> Triple(
                "车辆年检导入模板.xlsx",
                listOf(PlateInspectionExcelRow()),
                PlateInspectionExcelRow::class.java
            )

            "devices" -> Triple("设备导入模板.xlsx", listOf(DeviceExcelRow()), DeviceExcelRow::class.java)
            "gate-persons" -> Triple(
                "门禁人员导入模板.xlsx",
                listOf(GatePersonExcelRow()),
                GatePersonExcelRow::class.java
            )

            else -> throw IllegalArgumentException("不支持的 Excel 数据类型: $resource")
        }
        return writeWorkbook(filename, rows, type)
    }

    @GetMapping("/all/template")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('department:manage') and hasAuthority('user:create') and hasAuthority('position:manage') and hasAuthority('owner:manage') and hasAuthority('spot:manage') and hasAuthority('plate:manage') and hasAuthority('device:manage') and hasAuthority('gate-person:manage')")
            /**
             * allTemplate：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun allTemplate(): ResponseEntity<ByteArrayResource> {
        excelResourcePolicy.requireScopable("all")
        val sheets = listOf(
            Triple(
                "说明", listOf("填写说明"), listOf(
                    listOf("本文件是基础资料新增样表，包含真实会导入的示例行。使用前请修改示例值；不需要的数据删除示例行但保留工作表及表头。"),
                    listOf("按部门树→岗位→用户、车主→车位→车牌的依赖顺序导入；部门编码、岗位编码、车主卡号可以引用本文件或已存在的数据。"),
                    listOf("用户通过部门编码、岗位编码关联；车牌通过车主卡号关联；不要同时填写编码和数据库ID。车位的车主姓名必须唯一匹配已有或本次导入的车主。"),
                    listOf("已有编码、账号、邮箱、车牌等重复时拒绝新增，整份文件失败全部回滚。空工作表跳过。"),
                    listOf("登记日期和安装日期使用ISO日期，例如2026-09-09。车主、车位、车牌状态为0或1；其他状态使用正常或停用。"),
                    listOf("用户密码必填且会加密；请替换示例密码。角色使用系统允许分配的角色编码。门禁人员导入后为待审核、未同步。"),
                    listOf("这是基础资料导入，不包括权限配置、通行记录、审批、审计和文件。全部导出文件不是导入样表，缺少用户密码等必填信息。")
                )
            ),
            Triple(
                "部门", listOf("部门编码", "部门名称", "上级部门编码"), listOf(
                    listOf("SAMPLE_ROOT", "示例总公司", ""),
                    listOf("SAMPLE_DEPT", "示例运营部", "SAMPLE_ROOT")
                )
            ),
            Triple(
                "岗位", listOf("岗位名称", "岗位编码", "显示排序", "状态", "备注"),
                listOf(listOf("示例操作员", "SAMPLE_POST", "0", "正常", "请修改示例"))
            ),
            Triple(
                "用户",
                listOf(
                    "账号",
                    "姓名",
                    "邮箱",
                    "密码",
                    "手机号",
                    "性别",
                    "部门编码",
                    "岗位编码",
                    "角色编码",
                    "状态",
                    "启用"
                ),
                listOf(
                    listOf(
                        "sample_user",
                        "示例用户",
                        "sample@example.com",
                        "ReplaceMe!2026",
                        "13800000000",
                        "未知",
                        "SAMPLE_DEPT",
                        "SAMPLE_POST",
                        "USER",
                        "正常",
                        "是"
                    )
                )
            ),
            Triple(
                "车主", listOf("卡号", "姓名", "部门", "手机号", "余额", "状态"),
                listOf(listOf("SAMPLE_CARD", "示例车主", "示例运营部", "13800000001", "0", "1"))
            ),
            Triple(
                "车位", listOf("车位编号", "区域", "类型", "车主姓名", "状态", "备注"),
                listOf(listOf("SAMPLE_SPOT", "示例A区", "固定", "示例车主", "1", ""))
            ),
            Triple(
                "车牌", listOf("车牌号", "车主卡号", "状态", "登记日期", "车辆类型"),
                listOf(listOf("京A12345", "SAMPLE_CARD", "1", "2026-09-09", "小型轿车"))
            ),
            Triple(
                "设备",
                listOf(
                    "设备编号",
                    "设备名称",
                    "设备类型",
                    "品牌",
                    "型号",
                    "安装位置",
                    "IP地址",
                    "安装日期",
                    "状态",
                    "显示排序"
                ),
                listOf(
                    listOf(
                        "SAMPLE_DEVICE",
                        "示例摄像头",
                        "摄像头",
                        "示例品牌",
                        "示例型号",
                        "示例入口",
                        "192.0.2.10",
                        "2026-09-09",
                        "正常",
                        "0"
                    )
                )
            ),
            Triple(
                "门禁人员", listOf("人员编号", "部门", "姓名", "手机号", "身份证号"),
                listOf(listOf("SAMPLE_PERSON", "示例运营部", "示例人员", "13800000002", "110101199001010010"))
            )
        )
        val output = ByteArrayOutputStream()
        val writer = EasyExcel.write(output).build()
        try {
            sheets.forEachIndexed { index, (name, headers, rows) ->
                writer.write(rows, EasyExcel.writerSheet(index, name).head(headers.map { listOf(it) }).build())
            }
        } finally {
            writer.finish()
        }
        val disposition =
            ContentDisposition.attachment().filename("全部数据导入样表.xlsx", StandardCharsets.UTF_8).build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(ByteArrayResource(output.toByteArray()))
    }

    @GetMapping("/{resource}/export")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and ((#resource == 'users' and hasAuthority('user:read')) or (#resource == 'positions' and hasAuthority('position:read')) or (#resource == 'owners' and hasAuthority('owner:read')) or (#resource == 'spots' and hasAuthority('spot:read')) or (#resource == 'plates' and hasAuthority('plate:read')) or (#resource == 'plate-inspections' and hasAuthority('plate:read')) or (#resource == 'devices' and hasAuthority('device:read')) or (#resource == 'gate-persons' and hasAuthority('gate-person:export')))")
            /**
             * export：执行数据同步、探测或文件处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param resource 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun export(@PathVariable resource: String): ResponseEntity<ByteArrayResource> {
        excelResourcePolicy.requireScopable(resource)
        val scope = dataScopeResolver.current()
        // 车主与车位没有自己的部门字段，归属要经车主判定；先算一次，避免逐行重算。
        val visibleOwnerIds = if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
        val visibleOwnerCodes = if (scope.unrestricted) null else scopeQuerySupport.ownerCardIdsInScope(scope)
        return when (resource) {
            "users" -> {
                val users = mutableListOf<UserService.UserData>()
                var page = 1
                var total = Long.MAX_VALUE
                while (users.size < total) {
                    val result = userService.list(page++, 100)
                    users += result.users
                    total = result.total
                }
                val departmentNames = departmentService.listAll().associate { requireNotNull(it.id) to it.name }
                val positions = mutableListOf<Position>()
                var positionPage = 1
                var positionTotal = Long.MAX_VALUE
                while (positions.size < positionTotal) {
                    val result = positionService.list(positionPage++, 100)
                    positions += result.content
                    positionTotal = result.totalElements
                }
                val positionNames = positions.associate { requireNotNull(it.id) to it.name }
                writeWorkbook(
                    "用户列表.xlsx",
                    users.map {
                        UserExportRow(
                            it.id, it.username, it.name, it.email, it.phone, it.gender.name,
                            it.departmentId?.let(departmentNames::get), it.positionId?.let(positionNames::get), it.role,
                            if (it.status == User.Status.Activity) "正常" else "停用",
                            if (it.enabled) "是" else "否",
                        )
                    },
                    UserExportRow::class.java,
                )
            }

            "positions" -> {
                val positions = mutableListOf<Position>()
                var page = 1
                var total = Long.MAX_VALUE
                while (positions.size < total) {
                    val result = positionService.list(page++, 100)
                    positions += result.content
                    total = result.totalElements
                }
                writeWorkbook(
                    "岗位列表.xlsx",
                    positions.map {
                        PositionExportRow(
                            requireNotNull(it.id), it.name, it.codeNumber, it.orderNumber,
                            if (it.status == Position.Status.Activity) "正常" else "停用", it.remark,
                        )
                    },
                    PositionExportRow::class.java,
                )
            }

            "owners" -> writeWorkbook(
                "车主列表.xlsx",
                scopeQuerySupport.visibleInScope(scope, ownerRepository.findAll()).map {
                    OwnerExportRow(
                        requireNotNull(it.id),
                        it.cardId,
                        it.name,
                        it.dept,
                        it.phone,
                        it.spotCount,
                        it.plateCount,
                        it.balance.toPlainString(),
                        if (it.status == 1) "正常" else "停用"
                    )
                },
                OwnerExportRow::class.java
            )

            "spots" -> writeWorkbook(
                "车位列表.xlsx",
                spotRepository.findAll().filter { scopeQuerySupport.spotVisible(visibleOwnerCodes, it.ownerCode) }.map {
                    SpotExportRow(
                        requireNotNull(it.id),
                        it.code,
                        it.area,
                        it.type,
                        it.owner,
                        if (it.status == 1) "正常" else "停用",
                        it.remark
                    )
                },
                SpotExportRow::class.java
            )

            "plates" -> writeWorkbook(
                "车牌列表.xlsx",
                plateRepository.findAll().filter { scopeQuerySupport.plateVisible(visibleOwnerIds, scope.userId, it) }
                    .map {
                        PlateExportRow(
                            requireNotNull(it.id),
                            it.plate,
                            it.owner,
                            it.ownerId,
                            if (it.status == 1) "正常" else "停用",
                            it.regDate.toString(),
                            it.carBrand
                        )
                    },
                PlateExportRow::class.java
            )

            "plate-inspections" -> {
                val today = LocalDate.now()
                writeWorkbook(
                    "车辆年检信息.xlsx",
                    plateRepository.findAll()
                        .filter { scopeQuerySupport.plateVisible(visibleOwnerIds, scope.userId, it) }.sortedBy { it.id }
                        .map {
                            PlateInspectionExportRow(
                                requireNotNull(it.id), it.plate, it.owner, it.regDate.toString(),
                                VehicleInspection.status(it.inspectionDate, it.inspectionValidUntil, today),
                                if (VehicleInspection.inspected(
                                        it.inspectionDate,
                                        it.inspectionValidUntil
                                    )
                                ) "是" else "否",
                                it.inspectionDate?.toString(), it.inspectionValidUntil?.toString(), it.inspectionRemark
                            )
                        },
                    PlateInspectionExportRow::class.java
                )
            }

            "devices" -> {
                val devices = mutableListOf<Device>()
                var page = 1
                var total = Long.MAX_VALUE
                while (devices.size < total) {
                    val result = deviceService.list(page++, 100)
                    devices += result.content
                    total = result.totalElements
                }
                writeWorkbook("设备列表.xlsx", devices.map {
                    DeviceExportRow(
                        requireNotNull(it.id),
                        it.deviceCode,
                        it.deviceName,
                        it.deviceType,
                        it.brand,
                        it.model,
                        it.location,
                        it.ip,
                        it.installDate,
                        if (it.status == Device.Status.Activity) "正常" else "停用",
                        it.orderNumber
                    )
                }, DeviceExportRow::class.java)
            }

            "gate-persons" -> {
                val rows = scopeQuerySupport.visibleInScope(scope, gatePersonRepository.findAll()).map {
                    GatePersonExportRow(
                        requireNotNull(it.id),
                        it.code,
                        it.dept,
                        it.name,
                        it.phone,
                        it.idCard,
                        it.createTime.toString(),
                        it.approveStatus.value(),
                        it.syncStatus.value()
                    )
                }
                val response = writeWorkbook("门禁人员列表.xlsx", rows, GatePersonExportRow::class.java)
                // 工作簿真的写出来了才留痕：先记后写会在写出失败时留下一条「已成功导出」的假记录。
                auditService.record(
                    AuditCommand(
                        AuditAction.SENSITIVE_DATA_EXPORTED,
                        "gate_person",
                        targetSummary = mapOf("record_count" to rows.size, "original_filename" to "门禁人员列表.xlsx"),
                    ),
                )
                response
            }

            else -> throw IllegalArgumentException("不支持的 Excel 数据类型: $resource")
        }
    }

    @GetMapping("/all/export")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:read') and hasAuthority('position:read') and hasAuthority('owner:read') and hasAuthority('spot:read') and hasAuthority('plate:read') and hasAuthority('device:read') and hasAuthority('gate-person:export')")
            /**
             * exportAll：执行数据同步、探测或文件处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun exportAll(): ResponseEntity<ByteArrayResource> {
        excelResourcePolicy.requireScopable("all")
        val users = mutableListOf<UserService.UserData>()
        var userPage = 1
        var userTotal = Long.MAX_VALUE
        while (users.size < userTotal) {
            val result = userService.list(userPage++, 100)
            users += result.users
            userTotal = result.total
        }
        val departmentNames = departmentService.listAll().associate { requireNotNull(it.id) to it.name }
        val positions = mutableListOf<Position>()
        var positionPage = 1
        var positionTotal = Long.MAX_VALUE
        while (positions.size < positionTotal) {
            val result = positionService.list(positionPage++, 100)
            positions += result.content
            positionTotal = result.totalElements
        }
        val positionNames = positions.associate { requireNotNull(it.id) to it.name }
        val userRows = users.map {
            UserExportRow(
                it.id, it.username, it.name, it.email, it.phone, it.gender.name,
                it.departmentId?.let(departmentNames::get), it.positionId?.let(positionNames::get), it.role,
                if (it.status == User.Status.Activity) "正常" else "停用", if (it.enabled) "是" else "否"
            )
        }
        val positionRows = positions.map {
            PositionExportRow(
                requireNotNull(it.id), it.name, it.codeNumber, it.orderNumber,
                if (it.status == Position.Status.Activity) "正常" else "停用", it.remark
            )
        }
        val ownerRows = ownerRepository.findAll().map {
            OwnerExportRow(
                requireNotNull(it.id), it.cardId, it.name, it.dept, it.phone, it.spotCount,
                it.plateCount, it.balance.toPlainString(), if (it.status == 1) "正常" else "停用"
            )
        }
        val spotRows = spotRepository.findAll().map {
            SpotExportRow(
                requireNotNull(it.id), it.code, it.area, it.type, it.owner,
                if (it.status == 1) "正常" else "停用", it.remark
            )
        }
        val plateRows = plateRepository.findAll().map {
            PlateExportRow(
                requireNotNull(it.id), it.plate, it.owner, it.ownerId,
                if (it.status == 1) "正常" else "停用", it.regDate.toString(), it.carBrand
            )
        }
        val devices = mutableListOf<Device>()
        var devicePage = 1
        var deviceTotal = Long.MAX_VALUE
        while (devices.size < deviceTotal) {
            val result = deviceService.list(devicePage++, 100)
            devices += result.content
            deviceTotal = result.totalElements
        }
        val deviceRows = devices.map {
            DeviceExportRow(
                requireNotNull(it.id),
                it.deviceCode,
                it.deviceName,
                it.deviceType,
                it.brand,
                it.model,
                it.location,
                it.ip,
                it.installDate,
                if (it.status == Device.Status.Activity) "正常" else "停用",
                it.orderNumber
            )
        }
        val gateRows = gatePersonRepository.findAll().map {
            GatePersonExportRow(
                requireNotNull(it.id), it.code, it.dept, it.name, it.phone, it.idCard,
                it.createTime.toString(), it.approveStatus.value(), it.syncStatus.value()
            )
        }
        val output = ByteArrayOutputStream()
        val writer = EasyExcel.write(output).build()
        try {
            writer.write(userRows, EasyExcel.writerSheet(0, "用户").head(UserExportRow::class.java).build())
            writer.write(positionRows, EasyExcel.writerSheet(1, "岗位").head(PositionExportRow::class.java).build())
            writer.write(ownerRows, EasyExcel.writerSheet(2, "车主").head(OwnerExportRow::class.java).build())
            writer.write(spotRows, EasyExcel.writerSheet(3, "车位").head(SpotExportRow::class.java).build())
            writer.write(plateRows, EasyExcel.writerSheet(4, "车牌").head(PlateExportRow::class.java).build())
            writer.write(deviceRows, EasyExcel.writerSheet(5, "设备").head(DeviceExportRow::class.java).build())
            writer.write(gateRows, EasyExcel.writerSheet(6, "门禁人员").head(GatePersonExportRow::class.java).build())
        } finally {
            writer.finish()
        }
        val disposition = ContentDisposition.attachment().filename("全部数据.xlsx", StandardCharsets.UTF_8).build()
        // 「导出全部数据」同样含明文身份证与手机号，即使范围未受限也要留痕；写出成功后才记。
        // targetType 用独立的 excel_all，否则按 target_type=gate_person 检索会把「整库导出」
        // 误当成门禁人员导出，条数也严重偏低。
        val exportedRowCount = userRows.size + positionRows.size + ownerRows.size + spotRows.size +
                plateRows.size + deviceRows.size + gateRows.size
        auditService.record(
            AuditCommand(
                AuditAction.SENSITIVE_DATA_EXPORTED,
                "excel_all",
                targetSummary = mapOf("record_count" to exportedRowCount, "original_filename" to "全部数据.xlsx"),
            ),
        )
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .contentLength(output.size().toLong())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(ByteArrayResource(output.toByteArray()))
    }

    @PostMapping("/{resource}/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Transactional
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and ((#resource == 'all' and hasAuthority('department:manage') and hasAuthority('user:create') and hasAuthority('position:manage') and hasAuthority('owner:manage') and hasAuthority('spot:manage') and hasAuthority('plate:manage') and hasAuthority('device:manage') and hasAuthority('gate-person:manage')) or (#resource == 'users' and hasAuthority('user:create')) or (#resource == 'positions' and hasAuthority('position:manage')) or (#resource == 'owners' and hasAuthority('owner:manage')) or (#resource == 'spots' and hasAuthority('spot:manage')) or (#resource == 'plates' and hasAuthority('plate:manage')) or (#resource == 'plate-inspections' and hasAuthority('plate:manage')) or (#resource == 'devices' and hasAuthority('device:manage')) or (#resource == 'gate-persons' and hasAuthority('gate-person:manage')))")
            /**
             * import：执行数据同步、探测或文件处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param resource 参与本次处理的输入参数。
             * @param file 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun import(
        @PathVariable("resource") resource: String,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<top.foxball.cartask.shared.Response> {
        excelResourcePolicy.requireScopable(resource)
        require(!file.isEmpty) { "导入文件不能为空" }
        data class Response(val count: Int, val counts: Map<String, Int>)

        val sheetNames = linkedMapOf(
            "departments" to "部门", "positions" to "岗位", "users" to "用户",
            "owners" to "车主", "spots" to "车位", "plates" to "车牌", "devices" to "设备", "gate-persons" to "门禁人员"
        )
        val counts = linkedMapOf<String, Int>()
        if (resource == "all") {
            val reader = EasyExcel.read(file.inputStream).build()
            try {
                val names = reader.excelExecutor().sheetList().map { it.sheetName }
                require(names.containsAll(sheetNames.values)) { "请使用全部导入样表，须包含：${sheetNames.values.joinToString()}" }
            } finally {
                reader.finish()
            }
        }
        // 范围受限时导入必须落到当前工作部门；表里填了别的部门直接带行号报错，而不是悄悄改写。
        val forcedDepartment = excelResourcePolicy.forcedImportDepartment()
        val forcedImportRole = excelResourcePolicy.forcedImportRole()
        for (currentResource in if (resource == "all") sheetNames.keys else listOf(resource)) {
            val sheetName = if (resource == "all") sheetNames.getValue(currentResource) else null
            counts[currentResource] = 0
            when (currentResource) {
                "departments" -> {
                    require(resource == "all") { "部门请通过全部导入样表导入" }
                    val rows = EasyExcel.read(file.inputStream).head(DepartmentExcelRow::class.java).sheet(sheetName)
                        .doReadSync<DepartmentExcelRow>()
                    val existing = departmentRepository.findAll().associateBy { it.departmentNumber }.toMutableMap()
                    val pending = linkedMapOf<String, DepartmentExcelRow>()
                    rows.forEachIndexed { index, row ->
                        val code = requireNotBlank(row.code, "部门第${index + 2}行编码不能为空")
                        require(code !in existing && code !in pending) { "部门编码重复：$code" }
                        requireNotBlank(row.name, "部门第${index + 2}行名称不能为空")
                        pending[code] = row
                    }
                    while (pending.isNotEmpty()) {
                        val ready =
                            pending.filter { (_, row) -> row.parentCode.isNullOrBlank() || row.parentCode!!.trim() in existing }
                        require(ready.isNotEmpty()) { "部门上级不存在或存在循环引用：${pending.keys.joinToString()}" }
                        ready.forEach { (code, row) ->
                            val department = Department().apply {
                                departmentNumber = code
                                name = row.name!!.trim()
                                superior = row.parentCode?.trim()?.takeIf { it.isNotEmpty() }?.let(existing::getValue)
                            }
                            existing[code] = departmentRepository.save(department)
                            pending.remove(code)
                        }
                    }
                    counts[currentResource] = rows.size
                }

                "users" -> {
                    val rows = EasyExcel.read(file.inputStream).head(UserExcelRow::class.java).sheet(sheetName)
                        .doReadSync<UserExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val commands = rows.mapIndexed { index, row ->
                        val line = index + 2
                        UserService.CreateCommand(
                            username = requireNotBlank(row.username, "第${line}行账号不能为空"),
                            email = row.email?.trim().takeUnless { it.isNullOrEmpty() }
                                ?: "${row.username}@local.invalid",
                            credential = requireNotBlank(row.password, "第${line}行密码不能为空"),
                            // 角色编码这一列是提权入口：部门管理导入时一律强制为普通用户。
                            role = forcedImportRole ?: (row.role?.trim().takeUnless { it.isNullOrEmpty() } ?: "USER"),
                            enabled = parseBoolean(row.enabled, true, "第${line}行启用"),
                            phone = row.phone?.trim().takeUnless { it.isNullOrEmpty() },
                            gender = parseGender(row.gender, line),
                            departmentId = if (forcedDepartment != null) {
                                row.departmentCode?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                                    require(code == forcedDepartment.code) {
                                        "第${line}行只能导入到当前工作部门 ${forcedDepartment.name}（${forcedDepartment.code}）"
                                    }
                                }
                                forcedDepartment.id
                            } else {
                                row.departmentCode?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                                    require(row.departmentId == null) { "第${line}行部门编码和ID不能同时填写" }
                                    requireNotNull(
                                        departmentRepository.findAll()
                                            .singleOrNull { it.departmentNumber == code }) { "第${line}行部门编码不存在：$code" }.id
                                } ?: row.departmentId
                            },
                            positionId = row.positionCode?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                                require(row.positionId == null) { "第${line}行岗位编码和ID不能同时填写" }
                                requireNotNull(positionRepository.findByCodeNumber(code)) { "第${line}行岗位编码不存在：$code" }.id
                            } ?: row.positionId,
                            status = parseStatus(row.status, line),
                            nickName = row.name?.trim().takeUnless { it.isNullOrEmpty() },
                        )
                    }
                    val imported = userService.createBatch(commands)
                    counts[currentResource] = imported.size
                }

                "positions" -> {
                    val rows = EasyExcel.read(file.inputStream).head(PositionExcelRow::class.java).sheet(sheetName)
                        .doReadSync<PositionExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val entities = rows.mapIndexed { index, row ->
                        val line = index + 2
                        Position().apply {
                            name = requireNotBlank(row.name, "第${line}行岗位名称不能为空")
                            codeNumber = requireNotBlank(row.code, "第${line}行岗位编码不能为空")
                            orderNumber = row.sort ?: 0
                            status = parsePositionStatus(row.status, line)
                            remark = row.remark?.trim().takeUnless { it.isNullOrEmpty() }
                        }
                    }
                    val codes = entities.map { it.codeNumber }
                    require(codes.distinct().size == codes.size) { "岗位编码不能重复" }
                    require(codes.none { positionRepository.findByCodeNumber(it) != null }) { "岗位编码已存在" }
                    val imported = positionService.createBatch(entities)
                    counts[currentResource] = imported.size
                }

                "owners" -> {
                    val rows = EasyExcel.read(file.inputStream).head(OwnerExcelRow::class.java).sheet(sheetName)
                        .doReadSync<OwnerExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val entities = rows.mapIndexed { index, row ->
                        val line = index + 2
                        ParkingOwner().apply {
                            cardId = requireNotBlank(row.cardId, "第${line}行卡号不能为空")
                            name = requireNotBlank(row.name, "第${line}行姓名不能为空")
                            dept = forcedDepartment?.name ?: requireNotBlank(row.dept, "第${line}行部门不能为空")
                            departmentCode = forcedDepartment?.code
                                ?: scopeQuerySupport.stampDepartmentCode(dept, null)
                            phone = requireNotBlank(row.phone, "第${line}行手机号不能为空")
                            spotCount = row.spotCount ?: 0
                            plateCount = row.plateCount ?: 0
                            balance = row.balance?.trim().takeUnless { it.isNullOrEmpty() }?.let { BigDecimal(it) }
                                ?: BigDecimal.ZERO
                            status = row.status ?: 1
                            require(spotCount >= 0 && plateCount >= 0) { "第${line}行数量不能为负数" }
                            require(balance >= BigDecimal.ZERO) { "第${line}行余额不能为负数" }
                            require(status == 0 || status == 1) { "第${line}行状态必须为 0 或 1" }
                        }
                    }
                    val cardIds = entities.map { it.cardId }
                    require(cardIds.distinct().size == cardIds.size) { "导入文件中的卡号不能重复" }
                    require(cardIds.none { ownerRepository.existsByCardId(it) }) { "导入文件中包含已存在的车主卡号" }
                    val now = LocalDateTime.now()
                    entities.forEach { it.createdAt = now; it.updatedAt = now }
                    val imported = ownerRepository.saveAll(entities)
                    counts[currentResource] = imported.size
                }

                "spots" -> {
                    val rows = EasyExcel.read(file.inputStream).head(SpotExcelRow::class.java).sheet(sheetName)
                        .doReadSync<SpotExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val entities = rows.mapIndexed { index, row ->
                        val line = index + 2
                        ParkingSpot().apply {
                            code = requireNotBlank(row.code, "第${line}行车位编号不能为空")
                            area = requireNotBlank(row.area, "第${line}行区域不能为空")
                            type = requireNotBlank(row.type, "第${line}行类型不能为空")
                            owner = row.owner?.trim().takeUnless { it.isNullOrEmpty() }
                            if (resource == "all" && owner != null) {
                                require(ownerRepository.findAll().count { it.name == owner } == 1) {
                                    "车位第${line}行车主姓名不存在或重名：$owner"
                                }
                            }
                            status = row.status ?: 0
                            remark = row.remark?.trim().takeUnless { it.isNullOrEmpty() }
                            require(status == 0 || status == 1) { "第${line}行状态必须为 0 或 1" }
                        }
                    }
                    val codes = entities.map { it.code }
                    require(codes.distinct().size == codes.size) { "导入文件中的车位编号不能重复" }
                    require(codes.none { spotRepository.existsByCode(it) }) { "导入文件中包含已存在的车位编号" }
                    val now = LocalDateTime.now()
                    entities.forEach { it.createdAt = now; it.updatedAt = now }
                    val imported = spotRepository.saveAll(entities)
                    counts[currentResource] = imported.size
                }

                "plates" -> {
                    val rows = EasyExcel.read(file.inputStream).head(PlateExcelRow::class.java).sheet(sheetName)
                        .doReadSync<PlateExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val entities = rows.mapIndexed { index, row ->
                        val line = index + 2
                        val ownerId = row.ownerCard?.trim()?.takeIf { it.isNotEmpty() }?.let { card ->
                            require(row.ownerId == null) { "第${line}行车主卡号和ID不能同时填写" }
                            requireNotNull(
                                ownerRepository.findAll()
                                    .singleOrNull { it.cardId == card }) { "第${line}行车主卡号不存在：$card" }.id
                        } ?: requireNotNull(row.ownerId) { "第${line}行车主卡号或ID不能为空" }
                        val owner = ownerRepository.findById(ownerId)
                            .orElseThrow { IllegalArgumentException("第${line}行车主不存在: $ownerId") }
                        ParkingPlate().apply {
                            plate = requireNotBlank(row.plate, "第${line}行车牌号不能为空")
                            this.ownerId = ownerId
                            this.owner = owner.name
                            status = row.status ?: 1
                            regDate = LocalDate.parse(requireNotBlank(row.regDate, "第${line}行登记日期不能为空"))
                            carBrand = row.carBrand?.trim().orEmpty()
                            require(status == 0 || status == 1) { "第${line}行状态必须为 0 或 1" }
                        }
                    }
                    val plates = entities.map { it.plate }
                    require(plates.distinct().size == plates.size) { "导入文件中的车牌号不能重复" }
                    require(plates.none { plateRepository.existsByPlate(it) }) { "导入文件中包含已存在的车牌号" }
                    val now = LocalDateTime.now()
                    entities.forEach { it.createdAt = now; it.updatedAt = now }
                    val imported = plateRepository.saveAll(entities)
                    val allOwners = ownerRepository.findAll()
                    val allPlates = plateRepository.findAll()
                    val allSpots = spotRepository.findAll()
                    allOwners.forEach { owner ->
                        owner.plateCount = allPlates.count { it.ownerId == owner.id }
                        owner.spotCount = allSpots.count { it.owner == owner.name }
                    }
                    ownerRepository.flush()
                    counts[currentResource] = imported.size
                }

                "plate-inspections" -> {
                    val rows =
                        EasyExcel.read(file.inputStream).head(PlateInspectionExcelRow::class.java).sheet(sheetName)
                            .doReadSync<PlateInspectionExcelRow>()
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val scope = dataScopeResolver.current()
                    val visibleOwnerIds = if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
                    // 年检表按车牌号关联已有车辆：车牌在档案、进出记录里存在间隔符写法差异，先归一化再匹配。
                    val platesByNumber = plateRepository.findAll().groupBy { PlateNumbers.normalize(it.plate) }
                    val updated = linkedMapOf<Long, ParkingPlate>()
                    rows.forEachIndexed { index, row ->
                        val line = index + 2
                        val number = requireNotBlank(row.plate, "第${line}行车牌号不能为空")
                        // 归一化后重号的档案只可能是间隔符写法不同造成的重复登记，拒绝而不是随便挑一辆改。
                        val candidates = platesByNumber[PlateNumbers.normalize(number)].orEmpty()
                        val plate = requireNotNull(candidates.singleOrNull()) {
                            if (candidates.isEmpty()) "第${line}行车牌号不存在：$number" else "第${line}行车牌号对应多辆车辆档案，请先在车牌信息中去重：$number"
                        }
                        require(
                            scopeQuerySupport.plateVisible(
                                visibleOwnerIds,
                                scope.userId,
                                plate
                            )
                        ) { "第${line}行车牌号不在当前数据范围内：$number" }
                        val plateId = requireNotNull(plate.id)
                        require(updated.put(plateId, plate) == null) { "第${line}行车牌号重复：$number" }
                        // 「是否已年检」必填：留空时如果按未年检处理，会把已经登记好的年检记录悄悄清掉。
                        if (parseBoolean(
                                requireNotBlank(row.inspected, "第${line}行是否已年检不能为空"),
                                true,
                                "第${line}行是否已年检"
                            )
                        ) {
                            val inspectedOn =
                                row.inspectionDate?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
                                    ?: LocalDate.now()
                            val validUntil =
                                row.validUntil?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
                            require(validUntil == null || !validUntil.isBefore(inspectedOn)) { "第${line}行年检有效期不能早于年检日期" }
                            plate.inspectionDate = inspectedOn
                            plate.inspectionValidUntil = validUntil ?: VehicleInspection.defaultValidUntil(inspectedOn)
                            plate.inspectionRemark = row.remark?.trim()?.takeIf { it.isNotEmpty() }
                        } else {
                            plate.inspectionDate = null
                            plate.inspectionValidUntil = null
                            plate.inspectionRemark = null
                        }
                    }
                    plateRepository.saveAll(updated.values)
                    counts[currentResource] = updated.size
                }

                "devices" -> {
                    val rows = EasyExcel.read(file.inputStream).head(DeviceExcelRow::class.java).sheet(sheetName)
                        .doReadSync<DeviceExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val entities = rows.mapIndexed { index, row ->
                        val line = index + 2
                        Device().apply {
                            deviceCode = requireNotBlank(row.code, "第${line}行设备编号不能为空")
                            deviceName = requireNotBlank(row.name, "第${line}行设备名称不能为空")
                            deviceType = requireNotBlank(row.type, "第${line}行设备类型不能为空")
                            brand = requireNotBlank(row.brand, "第${line}行品牌不能为空")
                            model = requireNotBlank(row.model, "第${line}行型号不能为空")
                            location = requireNotBlank(row.location, "第${line}行安装位置不能为空")
                            ip = requireNotBlank(row.ip, "第${line}行IP地址不能为空")
                            installDate = requireNotBlank(row.installDate, "第${line}行安装日期不能为空").also {
                                LocalDate.parse(it)
                            }
                            status = parseDeviceStatus(row.status, line)
                            orderNumber = row.sort ?: 0
                        }
                    }
                    val codes = entities.mapNotNull { it.deviceCode }
                    require(codes.distinct().size == codes.size) { "导入文件中的设备编号不能重复" }
                    require(codes.none { deviceRepository.findByDeviceCode(it) != null }) { "导入文件中包含已存在的设备编号" }
                    val imported = deviceService.createBatch(entities)
                    counts[currentResource] = imported.size
                }

                "gate-persons" -> {
                    val rows = EasyExcel.read(file.inputStream).head(GatePersonExcelRow::class.java).sheet(sheetName)
                        .doReadSync<GatePersonExcelRow>()
                    if (resource == "all" && rows.isEmpty()) continue
                    require(rows.isNotEmpty()) { "Excel 中没有可导入的数据" }
                    val entities = rows.mapIndexed { index, row ->
                        val line = "第${index + 2}行"
                        val code = GatePersonFields.requireCode(row.code, line)
                        // 范围受限时导入必须落到当前工作部门；表里填了别的部门直接带行号报错，
                        // 而不是悄悄改写（那会让用户以为导入到了自己填的部门）。
                        val requestedDept = row.dept?.trim()?.takeIf(String::isNotEmpty)
                        if (forcedDepartment != null && requestedDept != null &&
                            requestedDept != forcedDepartment.name &&
                            scopeQuerySupport.stampDepartmentCode(requestedDept, null) != forcedDepartment.code
                        ) {
                            throw IllegalArgumentException("${line}只能导入到当前工作部门 ${forcedDepartment.name}（${forcedDepartment.code}）")
                        }
                        val dept = forcedDepartment?.name ?: GatePersonFields.requireDept(requestedDept, line)
                        GatePerson().apply {
                            this.code = code
                            this.dept = dept
                            departmentCode = forcedDepartment?.code ?: scopeQuerySupport.stampDepartmentCode(dept, null)
                            name = GatePersonFields.requireName(row.name, line)
                            phone = GatePersonFields.requirePhone(row.phone, line)
                            idCard = GatePersonFields.requireIdCard(row.idCard, line)
                            createTime = LocalDateTime.now()
                            updatedAt = createTime
                        }
                    }
                    val codes = entities.map { it.code }
                    val idCards = entities.map { it.idCard }
                    require(codes.distinct().size == codes.size) { "导入文件中的人员编号不能重复" }
                    require(idCards.distinct().size == idCards.size) { "导入文件中的身份证号不能重复" }
                    require(codes.none { gatePersonRepository.existsByCode(it) }) { GatePersonFields.CODE_EXISTS_MESSAGE }
                    require(idCards.none { gatePersonRepository.existsByIdCard(it) }) { GatePersonFields.ID_CARD_EXISTS_MESSAGE }
                    val imported = gatePersonRepository.saveAll(entities)
                    counts[currentResource] = imported.size
                    // 批量录入同样要留痕；编号只采样前 20 个，避免一次导入上万行把审计记录撑爆。
                    auditService.record(
                        AuditCommand(
                            AuditAction.GATE_PERSON_CREATED,
                            "gate_person",
                            targetSummary = mapOf(
                                "record_count" to imported.size,
                                "sample_codes" to imported.map { it.code }.take(20),
                            ),
                            afterData = mapOf(
                                "review_status" to GatePerson.ApproveStatus.PENDING.value(),
                                "synchronized" to false,
                            ),
                        ),
                    )
                }

                else -> throw IllegalArgumentException("不支持的 Excel 数据类型: $resource")
            }
        }
        require(counts.values.sum() > 0) { "Excel 中没有可导入的数据" }
        if (resource == "all") {
            val plateCounts = plateRepository.findAll().groupingBy { it.ownerId }.eachCount()
            val spotCounts = spotRepository.findAll().groupingBy { it.owner }.eachCount()
            ownerRepository.findAll().forEach { owner ->
                owner.plateCount = plateCounts[owner.id] ?: 0
                owner.spotCount = spotCounts[owner.name] ?: 0
            }
            ownerRepository.flush()
        }
        val rs = Response(counts.values.sum(), counts)
        return responseBuilder.created().data(rs).build()
    }

    private fun <T> writeWorkbook(
        filename: String,
        rows: List<T>,
        type: Class<out T>
    ): ResponseEntity<ByteArrayResource> {
        val output = ByteArrayOutputStream()
        EasyExcel.write(output, type).sheet("数据").doWrite(rows)
        val disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .contentLength(output.size().toLong())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(ByteArrayResource(output.toByteArray()))
    }

    /**
     * requireNotBlank：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param message 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireNotBlank(value: String?, message: String): String =
        requireNotNull(value?.trim().takeUnless { it.isNullOrEmpty() }) { message }

    /**
     * parseGender：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param line 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseGender(value: String?, line: Int): User.Gender = when (value?.trim()?.uppercase()) {
        null, "", "UNKNOWN", "未知" -> User.Gender.UNKNOWN
        "MALE", "男" -> User.Gender.MALE
        "FEMALE", "女" -> User.Gender.FEMALE
        else -> throw IllegalArgumentException("第${line}行性别必须为 MALE、FEMALE 或 UNKNOWN")
    }

    /**
     * parseStatus：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param line 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseStatus(value: String?, line: Int): User.Status = when (value?.trim()?.uppercase()) {
        null, "", "ACTIVITY", "正常", "1" -> User.Status.Activity
        "BANNED", "停用", "0" -> User.Status.BANNED
        else -> throw IllegalArgumentException("第${line}行状态必须为正常/停用")
    }

    /**
     * parsePositionStatus：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param line 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parsePositionStatus(value: String?, line: Int): Position.Status = when (value?.trim()?.uppercase()) {
        null, "", "ACTIVITY", "正常", "1" -> Position.Status.Activity
        "BANNED", "停用", "0" -> Position.Status.BANNED
        else -> throw IllegalArgumentException("第${line}行状态必须为正常/停用")
    }

    /**
     * parseDeviceStatus：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param line 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseDeviceStatus(value: String?, line: Int): Device.Status = when (value?.trim()?.uppercase()) {
        null, "", "ACTIVITY", "正常", "1" -> Device.Status.Activity
        "BANNED", "停用", "0" -> Device.Status.BANNED
        else -> throw IllegalArgumentException("第${line}行状态必须为正常/停用")
    }

    /**
     * parseBoolean：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param default 参与本次处理的输入参数。
     * @param label 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseBoolean(value: String?, default: Boolean, label: String): Boolean =
        when (value?.trim()?.uppercase()) {
            null, "" -> default
            "TRUE", "YES", "是", "1" -> true
            "FALSE", "NO", "否", "0" -> false
            else -> throw IllegalArgumentException("$label 必须为是/否")
        }
}
