package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.VehicleInoutRequest

interface VehicleInoutRequestRepository : JpaRepository<VehicleInoutRequest, Long> {
    
    
    fun existsByPlateNormalizedAndStatusIn(
        plateNormalized: String,
        statuses: Collection<VehicleInoutRequest.Status>,
    ): Boolean
    
    
    fun existsByPlateNormalizedAndStatusInAndIdNot(
        plateNormalized: String,
        statuses: Collection<VehicleInoutRequest.Status>,
        id: Long,
    ): Boolean
}
