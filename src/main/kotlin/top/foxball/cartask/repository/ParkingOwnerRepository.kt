package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ParkingOwner

interface ParkingOwnerRepository : JpaRepository<ParkingOwner, Long> {
    fun existsByCardId(cardId: String): Boolean
    fun existsByCardIdAndIdNot(cardId: String, id: Long): Boolean
}
