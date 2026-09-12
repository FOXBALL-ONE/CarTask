package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ParkingOwner

interface ParkingOwnerRepository : JpaRepository<ParkingOwner, Long> {
    fun findByCardId(cardId: String): ParkingOwner?
    fun findByPhone(phone: String): List<ParkingOwner>
    fun findByCardIdIn(cardIds: Collection<String>): List<ParkingOwner>
    fun findByLinkedUserId(userId: Long): List<ParkingOwner>
    fun findByDepartmentCode(departmentCode: String): List<ParkingOwner>
    fun existsByCardId(cardId: String): Boolean
    fun existsByCardIdAndIdNot(cardId: String, id: Long): Boolean
    fun countByDepartmentCode(departmentCode: String): Long
}
