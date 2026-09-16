package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ParkingPlate

interface ParkingPlateRepository : JpaRepository<ParkingPlate, Long> {
    
    
    fun findByPlate(plate: String): ParkingPlate?
    
    
    fun findByOwnerIdIn(ownerIds: Collection<Long>): List<ParkingPlate>
    
    
    fun findByLinkedUserId(userId: Long): List<ParkingPlate>
    
    
    fun existsByPlate(plate: String): Boolean
    
    
    fun existsByPlateAndIdNot(plate: String, id: Long): Boolean
}
