package top.foxball.cartask.task

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionOperations
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.SyncCheckpoint
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.SyncCheckpointRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 生产 Keytop 的受控增量联调。仅当 KEYTOP_LIVE_INCREMENTAL_SYNC=true 时执行。
 * 调用路径仅包含 getCarInoutInfo 查询；同步结果写入当前配置的本地数据库和文件存储。
 */
@EnabledIfEnvironmentVariable(named = "KEYTOP_LIVE_INCREMENTAL_SYNC", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.task.scheduling.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update",
        "app.mock-data.enabled=false",
        "cartask.security.jwt.keys.local=QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=",
        "cartask.security.jwt.token-storage-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
    ],
)
class KeytopLiveIncrementalSyncTests(
    @Autowired private val task: SynCarCapInfoTask,
    @Autowired private val checkpointRepository: SyncCheckpointRepository,
    @Autowired private val accessRecordRepository: AccessRecordRepository,
    @Autowired private val transactionOperations: TransactionOperations,
) {
    @Test
    fun `执行一次受控车辆进出增量同步`() {
        task.synCarCapInfoList()

        val checkpoint = transactionOperations.execute {
            checkpointRepository.findBySyncKey("keytop.car_cap_info")
        }
        assertNotNull(checkpoint)
        assertEquals(SyncCheckpoint.Status.SUCCESS, checkpoint.status)
        println(
            "Keytop 增量同步完成：cursor=${checkpoint.cursorTime}, external_id=${checkpoint.cursorExternalId}, " +
                "records=${accessRecordRepository.count()}, local_photos=${accessRecordRepository.countByPhotoSyncStatus(AccessRecord.PhotoSyncStatus.LOCAL)}, " +
                "failed_photos=${accessRecordRepository.countByPhotoSyncStatus(AccessRecord.PhotoSyncStatus.FAILED)}",
        )
    }
}
