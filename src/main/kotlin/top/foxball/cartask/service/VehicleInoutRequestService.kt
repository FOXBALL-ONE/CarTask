package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.VehicleInoutRequest
import java.time.LocalDateTime


interface VehicleInoutRequestService {
    
    
    data class NewOwnerCommand(
        val name: String,
        val phone: String,
        val departmentId: Long,
        val jobTitle: String,
        val password: String?,
    )
    
    data class CreateCommand(
        val plate: String,
        val cardName: String,
        val areaCode: String?,
        val validFrom: LocalDateTime,
        val validTo: LocalDateTime,
        
        val newOwner: NewOwnerCommand? = null,
    )
    
    
    data class UpdateCommand(
        val plate: String? = null,
        val cardName: String? = null,
        val areaCode: String? = null,
        val validFrom: LocalDateTime? = null,
        val validTo: LocalDateTime? = null,
    )
    
    
    data class ListFilter(
        val keyword: String? = null,
        val status: VehicleInoutRequest.Status? = null,
        val syncStatus: VehicleInoutRequest.SyncStatus? = null,
        val startDate: java.time.LocalDate? = null,
        val endDate: java.time.LocalDate? = null,
    )
    
    
    data class SyncOutcome(
        val synced: Boolean,
        val message: String,
        val cardId: Long?,
    )
    
    
    fun list(filter: ListFilter, page: Int, pageSize: Int): Page<VehicleInoutRequest>
    
    
    fun get(id: Long): VehicleInoutRequest
    
    
    fun create(command: CreateCommand): VehicleInoutRequest
    
    
    fun update(id: Long, command: UpdateCommand): VehicleInoutRequest
    
    
    fun review(id: Long, approved: Boolean, reason: String?): VehicleInoutRequest
    
    
    fun cancel(id: Long, reason: String?): VehicleInoutRequest
    
    
    fun synchronize(id: Long): SyncOutcome
}
