package top.foxball.cartask.service

import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.shared.GatePersonFields
import top.foxball.cartask.shared.InitialCredentials
import top.foxball.cartask.shared.PlateNumbers

/**
 * 为一条进出申请顺带建立车主档案、平台账号与车牌档案。
 *
 * 三条记录一起落库：只建车主的申请单没有可挂的车牌，只建账号的业主登录后看不到自己的车，
 * 三者缺一都是半成品数据，所以它们和申请单共用同一个事务。
 *
 * 与业主账号生成任务（[top.foxball.cartask.task.SynAccountGenerateTask]）保持同一套约定：
 * 登录名取手机号、初始密码取 [InitialCredentials.PASSWORD]、邮箱用占位地址。两处口径必须一致，
 * 否则同一个人从不同入口进来会得到两个登录名。
 */
@Service
class VehicleInoutArchiveService(
    private val userService: UserService,
    private val ownerRepository: ParkingOwnerRepository,
    private val plateRepository: ParkingPlateRepository,
    private val departmentRepository: DepartmentRepository,
    private val scopeGuard: ScopeGuard,
) {
    /** 顺带建档的产物：新车牌档案，以及新建账号的登录名（写进申请单的审计）。 */
    data class Archives(val plate: ParkingPlate, val accountUsername: String)

    /**
     * 建档并返回新建的车牌档案与账号登录名。
     *
     * 这里是**新建**语义而不是「有则复用」：车牌或车主已存在时直接报错并指明改用哪条路径。
     * 悄悄复用现有档案会让操作人以为自己在建一份新档案，实际却把车牌挂到了别人的车主名下。
     */
    @Transactional
    fun createArchives(plate: String, input: VehicleInoutRequestService.NewOwnerCommand): Archives {
        requireAuthorities()
        val scope = scopeGuard.currentScope()

        val ownerName = GatePersonFields.requireName(input.name)
        // 车主姓名会同时写进账号昵称（users.nick_name 是 varchar(64)），所以按更窄的那一侧收口；
        // 不收口就是把一个 65 字以上的姓名交给数据库，最后以 500 的形式暴露给操作人。
        require(ownerName.length <= NICK_NAME_MAX) { "车主姓名长度不能超过 $NICK_NAME_MAX 个字符" }
        val phone = GatePersonFields.requirePhone(input.phone)
        val jobTitle = requireNotNull(input.jobTitle.trim().takeIf(String::isNotEmpty)) { "职务不能为空" }
        require(jobTitle.length <= JOB_TITLE_MAX) { "职务长度不能超过 $JOB_TITLE_MAX 个字符" }
        val plateNumber = requireNotNull(plate.trim().takeIf(String::isNotEmpty)) { "车牌号不能为空" }
        require(plateNumber.length <= PLATE_MAX) { "车牌号长度不能超过 $PLATE_MAX 个字符" }
        val plateKey = requireNotNull(PlateNumbers.normalize(plateNumber)) { "车牌号不能为空" }

        val department = requireNotNull(departmentRepository.findById(requireNotNull(input.departmentId) { "部门不能为空" }).orElse(null)) {
            "部门不存在"
        }
        // 写路径必须按范围校验：否则部门管理能在这里造出属于别的部门的车主与账号。
        scopeGuard.requireDepartmentAllowed(department.id, scope)
        scopeGuard.requireDepartmentCodeAllowed(department.departmentNumber, scope)

        require(plateRepository.findAll().none { PlateNumbers.normalize(it.plate) == plateKey }) {
            "车牌 $plateNumber 已建档，请直接选择已建档车牌登记"
        }
        require(ownerRepository.findByPhone(phone).isEmpty()) {
            "手机号 $phone 已有车主档案，请改用已建档车牌登记"
        }
        require(!userService.existsByUsername(phone)) {
            "手机号 $phone 已注册平台账号，请改用已建档车牌登记"
        }

        // 手机号同时是登录名，所以先建账号，才能把车主档案的归属账号一并落上——
        // 少了这一步，普通用户登录后按本人范围看不到自己名下的车。
        val account = userService.create(
            UserService.CreateCommand(
                username = phone,
                email = InitialCredentials.placeholderEmail(phone),
                credential = input.password?.trim()?.takeIf(String::isNotEmpty) ?: InitialCredentials.PASSWORD,
                phone = phone,
                departmentId = department.id,
                nickName = ownerName,
                jobTitle = jobTitle,
                // 初始密码是公开信息，必须首次登录改掉；操作人自己设的密码不是，不强制。
                mustChangePassword = input.password.isNullOrBlank(),
            ),
        )

        val now = LocalDateTime.now()
        val owner = ownerRepository.save(
            ParkingOwner().apply {
                cardId = autoCardId(phone)
                name = ownerName
                dept = department.name
                departmentCode = department.departmentNumber
                this.phone = phone
                linkedUserId = account.id
                spotCount = 0
                plateCount = 1
                status = STATUS_ENABLED
                createdAt = now
                updatedAt = now
            },
        )

        val savedPlate = plateRepository.save(
            ParkingPlate().apply {
                this.plate = plateNumber
                this.owner = ownerName
                ownerId = requireNotNull(owner.id)
                status = STATUS_ENABLED
                regDate = LocalDate.now()
                carBrand = ""
                createdAt = now
                updatedAt = now
            },
        )
        return Archives(plate = savedPlate, accountUsername = account.username)
    }

    /**
     * 顺带建档等同于手工建账号 + 建车主 + 建车牌，因此要求调用方持有对应的三项权限。
     *
     * 只在页面上藏掉入口不够：这个接口本身只声明了 vehicle-inout-request:apply，
     * 不额外校验的话，只有申请权限的人就能凭空造出平台账号。
     */
    private fun requireAuthorities() {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
            ?: throw AccessDeniedException("缺少有效的操作人上下文")
        val missing = REQUIRED_AUTHORITIES.filterNot { it in principal.permissions }
        if (missing.isNotEmpty()) {
            throw AccessDeniedException("顺带新建档案需要权限：${missing.joinToString("、")}，请改用已建档车牌登记")
        }
    }

    /** 车主卡号由手机号派生：手机号在车主档案里已按唯一处理，派生值不会撞车。 */
    private fun autoCardId(phone: String): String = "$CARD_ID_PREFIX$phone"

    private companion object {
        val REQUIRED_AUTHORITIES = listOf("user:create", "owner:manage", "plate:manage")

        /** 与 SynAccountGenerateTask 自动建部门的编码前缀同一风格，便于与科拓同步来的卡号区分。 */
        const val CARD_ID_PREFIX = "AUTO-"

        const val STATUS_ENABLED = 1
        const val PLATE_MAX = 32

        /** 与 users.nick_name 的列宽一致；车主姓名要写进昵称，所以这是两者中更紧的那个上限。 */
        const val NICK_NAME_MAX = 64
        const val JOB_TITLE_MAX = 64
    }
}
