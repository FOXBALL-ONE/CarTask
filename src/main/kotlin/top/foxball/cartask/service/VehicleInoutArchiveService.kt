package top.foxball.cartask.service

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
import java.time.LocalDate
import java.time.LocalDateTime


@Service
class VehicleInoutArchiveService(
    private val userService: UserService,
    private val ownerRepository: ParkingOwnerRepository,
    private val plateRepository: ParkingPlateRepository,
    private val departmentRepository: DepartmentRepository,
    private val scopeGuard: ScopeGuard,
) {
    
    data class Archives(val plate: ParkingPlate, val accountUsername: String)
    
    
    @Transactional
    fun createArchives(plate: String, input: VehicleInoutRequestService.NewOwnerCommand): Archives {
        requireAuthorities()
        val scope = scopeGuard.currentScope()
        
        val ownerName = GatePersonFields.requireName(input.name)
        require(ownerName.length <= NICK_NAME_MAX) { "车主姓名长度不能超过 $NICK_NAME_MAX 个字符" }
        val phone = GatePersonFields.requirePhone(input.phone)
        val jobTitle = requireNotNull(input.jobTitle.trim().takeIf(String::isNotEmpty)) { "职务不能为空" }
        require(jobTitle.length <= JOB_TITLE_MAX) { "职务长度不能超过 $JOB_TITLE_MAX 个字符" }
        val plateNumber = requireNotNull(plate.trim().takeIf(String::isNotEmpty)) { "车牌号不能为空" }
        require(plateNumber.length <= PLATE_MAX) { "车牌号长度不能超过 $PLATE_MAX 个字符" }
        val plateKey = requireNotNull(PlateNumbers.normalize(plateNumber)) { "车牌号不能为空" }
        
        val department =
            requireNotNull(
                departmentRepository.findById(requireNotNull(input.departmentId) { "部门不能为空" })
                    .orElse(null)
            ) {
                "部门不存在"
            }
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
        
        val account = userService.create(
            UserService.CreateCommand(
                username = phone,
                email = InitialCredentials.placeholderEmail(phone),
                credential = input.password?.trim()?.takeIf(String::isNotEmpty) ?: InitialCredentials.PASSWORD,
                phone = phone,
                departmentId = department.id,
                nickName = ownerName,
                jobTitle = jobTitle,
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
    
    
    private fun requireAuthorities() {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
            ?: throw AccessDeniedException("缺少有效的操作人上下文")
        val missing = REQUIRED_AUTHORITIES.filterNot { it in principal.permissions }
        if (missing.isNotEmpty()) {
            throw AccessDeniedException("顺带新建档案需要权限：${missing.joinToString("、")}，请改用已建档车牌登记")
        }
    }
    
    
    private fun autoCardId(phone: String): String = "$CARD_ID_PREFIX$phone"
    
    private companion object {
        val REQUIRED_AUTHORITIES = listOf("user:create", "owner:manage", "plate:manage")
        
        
        const val CARD_ID_PREFIX = "AUTO-"
        
        const val STATUS_ENABLED = 1
        const val PLATE_MAX = 32
        
        
        const val NICK_NAME_MAX = 64
        const val JOB_TITLE_MAX = 64
    }
}
