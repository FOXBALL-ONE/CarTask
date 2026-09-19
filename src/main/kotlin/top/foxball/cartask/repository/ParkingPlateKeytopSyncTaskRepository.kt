package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ParkingPlateKeytopSyncTask
import java.time.LocalDateTime

interface ParkingPlateKeytopSyncTaskRepository : JpaRepository<ParkingPlateKeytopSyncTask, Long> {
    fun findFirstByStatusInAndNextAttemptAtIsNullOrderByIdAsc(
        statuses: Collection<ParkingPlateKeytopSyncTask.Status>,
    ): ParkingPlateKeytopSyncTask?

    fun findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(
        statuses: Collection<ParkingPlateKeytopSyncTask.Status>,
        now: LocalDateTime,
    ): ParkingPlateKeytopSyncTask?

    fun findByStatus(status: ParkingPlateKeytopSyncTask.Status): List<ParkingPlateKeytopSyncTask>

    fun findByPlateIdAndVersionAndOperation(
        plateId: Long,
        version: Long,
        operation: ParkingPlateKeytopSyncTask.Operation,
    ): ParkingPlateKeytopSyncTask?
}
