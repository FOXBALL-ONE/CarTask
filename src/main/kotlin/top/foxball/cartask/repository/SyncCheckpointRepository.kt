package top.foxball.cartask.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import top.foxball.cartask.entity.SyncCheckpoint

interface SyncCheckpointRepository : JpaRepository<SyncCheckpoint, Long> {
    
    
    fun findFirstBySyncKey(syncKey: String): SyncCheckpoint?
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    
    
    fun findBySyncKey(syncKey: String): SyncCheckpoint?
}
