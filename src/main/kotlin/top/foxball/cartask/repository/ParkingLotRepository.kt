package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ParkingLot

interface ParkingLotRepository : JpaRepository<ParkingLot, Long> {
    
    
    fun findByParkCode(parkCode: String): ParkingLot?
}
