package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import top.foxball.cartask.entity.SyncCheckpoint
import jakarta.persistence.LockModeType

interface SyncCheckpointRepository : JpaRepository<SyncCheckpoint, Long> {
    fun findFirstBySyncKey(syncKey: String): SyncCheckpoint?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findBySyncKey(syncKey: String): SyncCheckpoint?
}
