package top.foxball.cartask.controller

import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.entity.User
import top.foxball.cartask.entity.UserManagedDepartment
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.GateDeleteRequestRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.StoredFileRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.scope.WithCurrentUser
import top.foxball.cartask.shared.GatePersonFields
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 门禁人员模块的行为级用例。
 *
 * 这里断言「行为」而不是 `@PreAuthorize` 字符串：越权删除、可重复审核、只有当前页被审核、
 * 导入静默改写部门这几类缺陷都能在权限注解完全正确的前提下发生，只有跑真实调用链才拦得住。
 *
 * 编号一律拼上进程内的自增序号：同一个内存库在本 JVM 的多个用例间共享，用固定编号在重复执行
 * 本类时会撞唯一约束，测试就变成不可重复运行的了。
 */
@SpringBootTest(properties = [
    "app.mock-data.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:gate_person_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
])
@ActiveProfiles("test")
class GatePersonReviewIntegrationTests(
    @Autowired private val api: ParkingApiController,
    @Autowired private val excel: ExcelController,
    @Autowired private val persons: GatePersonRepository,
    @Autowired private val deleteRequests: GateDeleteRequestRepository,
    @Autowired private val files: StoredFileRepository,
    @Autowired private val departments: DepartmentRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val managedDepartments: UserManagedDepartmentRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @MockitoBean
    lateinit var auditService: AuditService

    @AfterEach
    fun clearAuthentication() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:review"])
    fun `审核只接受待审核记录且驳回会重置同步状态`() {
        val approvable = savePerson("GP-REVIEW")
        api.approveGatePerson(requireNotNull(approvable.id), null)
        assertEquals(GatePerson.ApproveStatus.APPROVED, reload(approvable).approveStatus)

        // 状态机必须是单向的：已决记录不能反复改判。
        assertTrue(assertFailsWith<IllegalArgumentException> { api.approveGatePerson(requireNotNull(approvable.id), null) }
            .message!!.contains("不允许审核"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.rejectGatePerson(requireNotNull(approvable.id), null) }
            .message!!.contains("不允许审核"))

        val rejectable = savePerson("GP-REVIEW")
        val synced = reload(rejectable)
        synced.syncStatus = GatePerson.SyncStatus.SYNCED
        persons.save(synced)
        api.rejectGatePerson(requireNotNull(rejectable.id), "证件不清晰")

        val rejected = reload(rejectable)
        assertEquals(GatePerson.ApproveStatus.REJECTED, rejected.approveStatus)
        // 驳回的人不能继续留在「已同步」上，否则会出现已拒绝却已下发的矛盾状态。
        assertEquals(GatePerson.SyncStatus.NOT_SYNCED, rejected.syncStatus)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:review"])
    fun `审核审计能区分通过与驳回且载荷不为空`() {
        val approved = savePerson("GP-AUDIT-OK")
        val rejected = savePerson("GP-AUDIT-NO")
        api.approveGatePerson(requireNotNull(approved.id), null)
        api.rejectGatePerson(requireNotNull(rejected.id), "照片与本人不符")

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val reviews = captor.allValues.filter { it.action == AuditAction.GATE_PERSON_REVIEWED }
        assertEquals(2, reviews.size)

        // 审计载荷会被 AuditServiceImpl 的键白名单过滤，键名写错就只剩一个空 map——
        // 那样 approve 与 reject 在审计里完全无法区分，等于没写。
        reviews.forEach { command ->
            assertFalse(command.beforeData.isNullOrEmpty(), "审核前后状态不能为空：${command.beforeData}")
            assertFalse(command.afterData.isNullOrEmpty(), "审核后状态不能为空：${command.afterData}")
        }
        val outcomes = reviews.map { it.afterData?.get("review_status") }.toSet()
        assertEquals(setOf(GatePerson.ApproveStatus.APPROVED.value(), GatePerson.ApproveStatus.REJECTED.value()), outcomes)
        assertTrue(reviews.any { it.reason == "照片与本人不符" })
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:manage", "gate-person:review"])
    fun `批量审核逐条校验状态且整批失败时不留半步结果`() {
        val first = savePerson("GP-BATCH")
        val second = savePerson("GP-BATCH")
        api.approveGatePerson(requireNotNull(second.id), null)

        assertFailsWith<IllegalArgumentException> {
            api.reviewGatePersons(GatePersonReviewBody(ids = listOf(requireNotNull(first.id), requireNotNull(second.id)), approved = true))
        }
        // 事务回滚：第一条也不能落库，否则「整批审核」会退化成部分成功。
        assertEquals(GatePerson.ApproveStatus.PENDING, reload(first).approveStatus)

        api.reviewGatePersons(GatePersonReviewBody(ids = listOf(requireNotNull(first.id)), approved = true))
        assertEquals(GatePerson.ApproveStatus.APPROVED, reload(first).approveStatus)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:manage", "gate-person:review"])
    fun `只改内容才退回待审核而空更新不动状态`() {
        val person = savePerson("GP-EDIT")
        api.approveGatePerson(requireNotNull(person.id), null)
        val synced = reload(person)
        synced.syncStatus = GatePerson.SyncStatus.SYNCED
        persons.save(synced)

        // 前端回填表单原样提交（各字段没变）不该把已通过的人打回待审核。
        api.updateGatePersonMultipart(requireNotNull(person.id), null, null, reload(person).name, null, null, null)
        assertEquals(GatePerson.ApproveStatus.APPROVED, reload(person).approveStatus)
        assertEquals(GatePerson.SyncStatus.SYNCED, reload(person).syncStatus)

        api.updateGatePersonMultipart(requireNotNull(person.id), null, null, "改名后", null, null, null)
        val updated = reload(person)
        // 先送审、通过后再改，必须重新审一次，否则审核可以被静默绕过。
        assertEquals("改名后", updated.name)
        assertEquals(GatePerson.ApproveStatus.PENDING, updated.approveStatus)
        assertEquals(GatePerson.SyncStatus.NOT_SYNCED, updated.syncStatus)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:manage", "gate-person:review"])
    fun `历史数据没有部门编码时空更新不会打回待审核`() {
        val department = departments.save(Department().apply { name = "历史部门"; departmentNumber = "LEGACY-DEPT" })
        val person = savePerson("GP-LEGACY")
        val legacy = reload(person)
        // 历史行只有部门名、没有编码：department_code 为 null 是 GatePerson 注释里明确存在的形态。
        legacy.dept = department.name
        legacy.departmentCode = null
        legacy.approveStatus = GatePerson.ApproveStatus.APPROVED
        legacy.syncStatus = GatePerson.SyncStatus.SYNCED
        persons.save(legacy)

        // 前端保存会原样提交所有字段（含部门名），此时没有可见内容发生变化。
        api.updateGatePersonMultipart(
            requireNotNull(person.id), legacy.code, department.name, legacy.name, legacy.phone, legacy.idCard, null,
        )

        val updated = reload(person)
        // 编码会被回填，但它不是用户可见内容，不能因此把已通过的记录打回待审核。
        assertEquals(department.departmentNumber, updated.departmentCode)
        assertEquals(GatePerson.ApproveStatus.APPROVED, updated.approveStatus)
        assertEquals(GatePerson.SyncStatus.SYNCED, updated.syncStatus)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:review"])
    fun `批量审核条数超过上限时整批拒绝`() {
        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.reviewGatePersons(GatePersonReviewBody(ids = (1L..201L).toList(), approved = true))
        }.message!!.contains("不能超过"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:manage", "gate-person:review"])
    fun `改编号会重绑人脸文件而删除人员会解除关联`() {
        val first = departments.save(Department().apply { name = "锚点甲部"; departmentNumber = "ANCHOR-A" })
        val second = departments.save(Department().apply { name = "锚点乙部"; departmentNumber = "ANCHOR-B" })
        val person = savePerson("GP-FILE", first.departmentNumber)
        val id = requireNotNull(person.id)
        api.updateGatePersonMultipart(id, null, null, null, null, null, imageFile("face.png"))
        val linked = files.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, person.code)
        assertEquals(1, linked.size)
        val fileId = linked.single().id

        val newCode = "${person.code}-NEW"
        api.updateGatePersonMultipart(id, newCode, null, null, null, null, null)
        // 编号是人脸文件的业务标识：不重绑，本人范围再也取不到自己的照片。
        assertTrue(files.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, newCode).isNotEmpty())
        assertTrue(files.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, person.code).isEmpty())

        // 只改部门、编号不变：部门锚点必须跟着改，否则旧部门在人员调走之后仍能下载。
        api.updateGatePersonMultipart(id, null, second.departmentNumber, null, null, null, null)
        assertEquals(second.departmentNumber, files.findById(fileId).orElseThrow().departmentCode)

        api.approveDeleteRequest(savePersonDeleteRequest(reload(person), "离职"))
        val orphaned = files.findById(fileId).orElseThrow()
        // 人已删除，文件不能再按编号或部门被反查下载（部门锚点也要一起清掉）。
        assertNull(orphaned.businessType)
        assertNull(orphaned.businessId)
        assertNull(orphaned.departmentCode)

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        // 人员被物理删除是 CRITICAL 级事实，必须单独立案，而不是只留在申请单的状态流转里。
        val deletion = captor.allValues.single { it.action == AuditAction.GATE_PERSON_DELETED }
        assertEquals("gate_person", deletion.targetType)
        assertEquals(true, deletion.afterData?.get("deleted"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:manage"])
    fun `录入校验拒绝非法身份证与超长手机号且不落库`() {
        val badIdCard = uniqueCode("GP-BAD-IDCARD")
        assertTrue(createError(badIdCard, "13800000000", "123").contains("身份证号必须为 18 位"))
        assertFalse(persons.existsByCode(badIdCard))

        val badPhone = uniqueCode("GP-BAD-PHONE")
        assertTrue(createError(badPhone, "abcdefgh", "110101199001010011").contains("手机号格式不正确"))
        assertFalse(persons.existsByCode(badPhone))

        val longPhone = uniqueCode("GP-BAD-LONG")
        assertTrue(createError(longPhone, "1".repeat(33), "110101199001010011").contains("长度不能超过"))
        assertFalse(persons.existsByCode(longPhone))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:manage"])
    fun `人脸照片只接受真实图片内容且限制大小`() {
        val script = MockMultipartFile("face", "face.png", "image/png", "alert(1)".toByteArray())
        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.createGatePersonMultipart("GP-FAKE-SCRIPT", "校验部", "假图片", "13800000000", "110101199001010011", script)
        }.message!!.contains("内容不是受支持的图片"))

        val oversized = MockMultipartFile("face", "big.png", "image/png", ByteArray(2 * 1024 * 1024 + 1))
        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.createGatePersonMultipart("GP-FAKE-BIG", "校验部", "超大图片", "13800000000", "110101199001010011", oversized)
        }.message!!.contains("不能超过 2MB"))

        assertFalse(persons.existsByCode("GP-FAKE-SCRIPT"))
        assertFalse(persons.existsByCode("GP-FAKE-BIG"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read"])
    fun `只有读权限时无法导出门禁人员`() {
        savePerson("GP-EXPORT-DENIED")
        // 导出拿的是明文身份证与手机号，不能「能看列表就能批量导出」。
        assertFailsWith<AccessDeniedException> { excel.export("gate-persons") }
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["gate-person:read", "gate-person:export"])
    fun `导出门禁人员留下敏感数据导出审计`() {
        savePerson("GP-EXPORT")

        val response = excel.export("gate-persons")
        assertEquals(200, response.statusCode.value())

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val exported = captor.allValues.single { it.action == AuditAction.SENSITIVE_DATA_EXPORTED }
        assertEquals("gate_person", exported.targetType)
        // 键名必须落在 AuditServiceImpl 的白名单里，否则这条记录只剩空载荷。
        assertFalse(exported.targetSummary.isNullOrEmpty())
        assertTrue(exported.targetSummary!!.containsKey("record_count"))
    }

    @Test
    fun `删除申请链路按工作部门裁剪且不能越权删除他人部门的人员`() {
        val scope = departmentAdminScope()
        val permissions = setOf("gate-person:read", "gate-person:manage", "gate-person:review")

        // 先用不受限的管理员把两个部门的人员与申请都准备好：受限身份本来就建不出范围外的申请，
        // 那正是本用例后半段要断言的行为。
        authenticate(scope.userId, "ADMIN", permissions)
        val mine = savePerson("GP-SCOPE-A", scope.departmentCode)
        val other = savePerson("GP-SCOPE-B", "SCOPE-B")
        val mineRequest = savePersonDeleteRequest(mine, "本部门离职")
        val otherRequest = savePersonDeleteRequest(other, "他部门离职")
        // 申请单自己也要落部门编码，否则列表没法按范围裁剪。
        assertEquals(scope.departmentCode, findDeleteRequest(mineRequest).departmentCode)

        authenticate(scope.userId, "DEPT_ADMIN", permissions)

        assertEquals(listOf(mine.code), listedDeleteRequestCodes())
        // 列表与详情都不能泄露范围外人员的身份证与手机号。
        assertFalse(listedPersonCodes().contains(other.code))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.getGatePerson(requireNotNull(other.id)) }
            .message!!.contains("人员不存在"))

        // 为范围外人员发起删除申请、以及审批范围外的申请，都必须按「不存在」处理。
        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.createDeleteRequest(requireNotNull(other.id), DeleteRequestBody("越权申请"))
        }.message!!.contains("人员不存在"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.approveDeleteRequest(otherRequest) }
            .message!!.contains("删除申请不存在"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.rejectDeleteRequest(otherRequest) }
            .message!!.contains("删除申请不存在"))

        api.approveDeleteRequest(mineRequest)
        assertFalse(persons.existsById(requireNotNull(mine.id)))
        assertTrue(persons.existsById(requireNotNull(other.id)))
    }

    @Test
    fun `范围受限导入到其它部门时带行号报错而不是静默改写`() {
        val scope = departmentAdminScope()
        authenticate(scope.userId, "DEPT_ADMIN", setOf("gate-person:manage"))

        val foreignCode = uniqueCode("GP-IMPORT-B")
        val foreign = gatePersonWorkbook(listOf(listOf(foreignCode, "他部门", "跨部门", "13800000009", "110101199001010019")))
        val error = assertFailsWith<IllegalArgumentException> {
            excel.import("gate-persons", MockMultipartFile("file", "gate.xlsx", null, foreign))
        }.message!!
        assertTrue(error.contains("只能导入到当前工作部门"))
        assertFalse(persons.existsByCode(foreignCode))

        val ownCode = uniqueCode("GP-IMPORT-A")
        val own = gatePersonWorkbook(listOf(listOf(ownCode, scope.departmentName, "本部门", "13800000009", "110101199001010019")))
        excel.import("gate-persons", MockMultipartFile("file", "gate.xlsx", null, own))

        val imported = persons.findByCode(ownCode)!!
        // 批量上传的人也走同一套审核流程，而不是导入即成可用身份。
        assertEquals(GatePerson.ApproveStatus.PENDING, imported.approveStatus)
        assertEquals(GatePerson.SyncStatus.NOT_SYNCED, imported.syncStatus)
        assertEquals(scope.departmentCode, imported.departmentCode)
        // 样表不带人脸照片，导入记录的人脸为空是明确行为，不是漏赋值。
        assertNull(imported.face)
    }

    /**
     * G4 回归：编号与身份证号是**全局**唯一（`uk_gate_person_code` / `uk_gate_person_id_card`），
     * 撞到的记录可能属于别的部门。单条录入、Excel 导入、以及并发下落到底层唯一约束这三条路径
     * 必须共用同一句提示——只要哪条路径的措辞不一样，就多出一个能用来猜
     * 「别的部门存在哪些编号」的差异点。
     *
     * 注意这只锁住「不额外暴露归属部门」，**并不等于关掉了侧信道**：唯一约束是全局的，
     * 「创建失败」本身就已经说明该编号在库中存在。要真正关掉得把唯一性收窄到部门。
     */
    @Test
    fun `跨部门撞号与本部门撞号的提示完全一致`() {
        val scope = departmentAdminScope()
        authenticate(scope.userId, "DEPT_ADMIN", setOf("gate-person:manage", "gate-person:read"))

        val inside = savePerson("GP-DUP-IN", scope.departmentCode)
        val outside = savePerson("GP-DUP-OUT", DEFAULT_DEPARTMENT_CODE)

        assertEquals(
            GatePersonFields.CODE_EXISTS_MESSAGE,
            createError(inside.code, "13800000021", "110101199001010021"),
        )
        assertEquals(
            createError(inside.code, "13800000022", "110101199001010022"),
            createError(outside.code, "13800000023", "110101199001010023"),
            "范围外编号与本部门编号的冲突提示必须完全一致",
        )

        assertEquals(
            GatePersonFields.ID_CARD_EXISTS_MESSAGE,
            createError(uniqueCode("GP-DUP-IDC-IN"), "13800000024", inside.idCard),
        )
        assertEquals(
            createError(uniqueCode("GP-DUP-IDC-IN"), "13800000025", inside.idCard),
            createError(uniqueCode("GP-DUP-IDC-OUT"), "13800000026", outside.idCard),
            "范围外身份证号与本部门身份证号的冲突提示必须完全一致",
        )
    }

    private data class DepartmentAdminScope(val userId: Long, val departmentCode: String, val departmentName: String)

    /**
     * 建一个只管理 [SCOPE_CODE] 的部门管理，重复调用返回同一份数据。
     *
     * 范围按人分配（[UserManagedDepartment]）而不是按角色；`@WithCurrentUser` 的 userId 是注解常量，
     * 拿不到刚插入的主键，所以这些用例在测试体内自行建立安全上下文。
     */
    private fun departmentAdminScope(): DepartmentAdminScope {
        val department = departments.findAll().firstOrNull { it.departmentNumber == SCOPE_CODE }
            ?: departments.save(Department().apply { name = "范围甲部"; departmentNumber = SCOPE_CODE })
        val user = users.findAll().firstOrNull { it.username == SCOPE_ADMIN_USERNAME }
            ?: users.save(User().apply {
                username = SCOPE_ADMIN_USERNAME
                email = "gate-scope-admin@local.invalid"
                passwordHash = "x"
                role = "DEPT_ADMIN"
                this.department = department
                val now = LocalDateTime.now()
                createdAt = now
                updatedAt = now
            })
        val userId = requireNotNull(user.id)
        if (managedDepartments.findByUserId(userId).isEmpty()) {
            managedDepartments.save(UserManagedDepartment().apply {
                this.user = user
                this.department = department
            })
        }
        return DepartmentAdminScope(userId, SCOPE_CODE, department.name)
    }

    private fun authenticate(userId: Long, role: String, permissions: Set<String>) {
        val principal = CurrentUserPrincipal(
            userId = userId,
            username = "gate-scope-admin",
            role = role,
            tokenId = "test-token",
            permissions = permissions,
        )
        SecurityContextHolder.setContext(
            SecurityContextImpl(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)),
        )
    }

    /** 直接经仓库建人：单条录入接口要人脸图片，本轮只关心审核与范围。 */
    private fun savePerson(codeLabel: String, departmentCode: String? = null): GatePerson {
        val index = SEQUENCE.incrementAndGet()
        val department = departmentCode ?: DEFAULT_DEPARTMENT_CODE
        return persons.save(GatePerson().apply {
            code = "$codeLabel-$index"
            dept = department
            this.departmentCode = department
            name = "测试人员$index"
            phone = "138" + index.toString().padStart(8, '0')
            idCard = "1101011990" + index.toString().padStart(8, '0')
            createTime = LocalDateTime.now()
            updatedAt = createTime
        })
    }

    private fun uniqueCode(label: String) = "$label-${SEQUENCE.incrementAndGet()}"

    private fun savePersonDeleteRequest(person: GatePerson, reason: String): Long {
        val response = api.createDeleteRequest(requireNotNull(person.id), DeleteRequestBody(reason))
        @Suppress("UNCHECKED_CAST")
        return (response.body!!.data as StoredDeleteRequest).id
    }

    private fun findDeleteRequest(id: Long) = deleteRequests.findById(id).orElseThrow()

    /** 列表响应是端点内的局部 data class，按 JSON 取字段，不依赖类型可见性。 */
    private fun listedDeleteRequestCodes(): List<String> = listed(api.listDeleteRequests(null, null, 1, 100).body!!.data)
        .map { it.get("code").asText() }
        // 只有本用例会为 GP-SCOPE 前缀的人建申请，取全部再与期望比对即可暴露「范围外也可见」。
        .filter { it.startsWith("GP-SCOPE") }
        .sorted()

    private fun listedPersonCodes(): List<String> = listed(api.listGatePersons(null, null, null, null, 1, 100).body!!.data)
        .map { it.get("code").asText() }

    private fun listed(data: Any?): List<JsonNode> = objectMapper.valueToTree<JsonNode>(data).get("items").values().toList()

    private fun reload(person: GatePerson): GatePerson = persons.findById(requireNotNull(person.id)).orElseThrow()

    private fun createError(code: String, phone: String, idCard: String): String =
        assertFailsWith<IllegalArgumentException> {
            api.createGatePersonMultipart(code, "校验部", "校验人员", phone, idCard, imageFile("face.png"))
        }.message!!

    private fun imageFile(name: String) = MockMultipartFile("face", name, "image/png", PNG_BYTES)

    private fun gatePersonWorkbook(rows: List<List<String>>): ByteArray {
        val headers = listOf("人员编号", "部门", "姓名", "手机号", "身份证号")
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("数据")
            sheet.createRow(0).also { header -> headers.forEachIndexed { index, name -> header.createCell(index).setCellValue(name) } }
            rows.forEachIndexed { index, values ->
                sheet.createRow(index + 1).also { row -> values.forEachIndexed { cell, value -> row.createCell(cell).setCellValue(value) } }
            }
            workbook.write(output)
        }
        return output.toByteArray()
    }

    private companion object {
        /** 同一个内存库在本类的多个用例间共享，编号必须跨用例、跨重复执行都唯一。 */
        val SEQUENCE = AtomicInteger(0)

        const val DEFAULT_DEPARTMENT_CODE = "GATE-TEST-DEPT"

        const val SCOPE_CODE = "SCOPE-A"

        const val SCOPE_ADMIN_USERNAME = "gate-scope-admin"

        /** 合法 PNG 的文件头，用于通过内容校验。 */
        val PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        )
    }
}
