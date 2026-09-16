package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ParkingSpot

interface ParkingSpotRepository : JpaRepository<ParkingSpot, Long> {
    
    
    fun findByCode(code: String): ParkingSpot?
    
    
    fun existsByCode(code: String): Boolean
    
    
    fun existsByCodeAndIdNot(code: String, id: Long): Boolean
}
