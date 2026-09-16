package top.foxball.cartask.service


data class ParkingAreaSyncResult(
    val receivedCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    
    val lotName: String? = null,
    
    val totalPlaceCount: Int? = null,
)


interface ParkingAreaSyncService {
    
    
    fun synchronize(): ParkingAreaSyncResult
}
