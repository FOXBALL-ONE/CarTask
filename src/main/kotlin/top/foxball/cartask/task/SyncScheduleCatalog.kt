package top.foxball.cartask.task

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import top.foxball.cartask.keytop.KeytopProperties
import java.time.ZoneId


data class SyncScheduleDefinition(
    val key: String,
    val name: String,
    val description: String,
    val defaultCron: String,
    val trigger: () -> Unit,
)


@Component
class SyncScheduleCatalog(
    private val synAreaInfoTask: SynAreaInfoTask,
    private val synCarCapInfoTask: SynCarCapInfoTask,
    private val synOwnerArchiveTask: SynOwnerArchiveTask,
    private val synAccountGenerateTask: SynAccountGenerateTask,
    private val plateKeytopSyncTask: PlateKeytopSyncTask,
    keytopProperties: KeytopProperties,
    @Value("\${app.owner-archive-cron:0 45 2 * * *}") ownerArchiveCron: String,
    @Value("\${app.account-generate-cron:0 0 3 * * *}") accountGenerateCron: String,
) {
    val definitions: List<SyncScheduleDefinition> = listOf(
        SyncScheduleDefinition(
            key = SynAreaInfoTask.TASK_KEY,
            name = SynAreaInfoTask.TASK_NAME,
            description = "按科拓区域编码同步停车区域字典，接口返回为空时不覆盖本地数据。",
            defaultCron = keytopProperties.areaSyncCron,
            trigger = { synAreaInfoTask.synAreaInfo() },
        ),
        SyncScheduleDefinition(
            key = SynCarCapInfoTask.TASK_KEY,
            name = SynCarCapInfoTask.TASK_NAME,
            description = "按上次成功检查点增量拉取车辆进出记录与抓拍图片。",
            defaultCron = keytopProperties.carCapInfoSyncCron,
            trigger = { synCarCapInfoTask.synCarCapInfoList() },
        ),
        SyncScheduleDefinition(
            key = SynCarCapInfoTask.RECONCILIATION_TASK_KEY,
            name = SynCarCapInfoTask.RECONCILIATION_TASK_NAME,
            description = "回补超过增量回看窗口才在上游可见的进出记录，不推进增量检查点。",
            defaultCron = keytopProperties.carCapInfoReconciliationCron,
            trigger = { synCarCapInfoTask.reconcileCarCapInfoList() },
        ),
        SyncScheduleDefinition(
            key = PlateKeytopSyncTask.TASK_KEY,
            name = PlateKeytopSyncTask.TASK_NAME,
            description = "处理待同步的有效车牌月卡，并将成功、失败或阻塞状态回写到车牌档案。",
            defaultCron = keytopProperties.plateSyncCron,
            trigger = { plateKeytopSyncTask.synchronize() },
        ),
        SyncScheduleDefinition(
            key = SynOwnerArchiveTask.TASK_KEY,
            name = SynOwnerArchiveTask.TASK_NAME,
            description = "为有进出记录但缺车主档案的车牌回查科拓卡片信息并补建档案。",
            defaultCron = ownerArchiveCron,
            trigger = { synOwnerArchiveTask.synOwnerArchive() },
        ),
        SyncScheduleDefinition(
            key = SynAccountGenerateTask.TASK_KEY,
            name = SynAccountGenerateTask.TASK_NAME,
            description = "为已有车主档案的业主补建平台登录账号，已有账号自动跳过。",
            defaultCron = accountGenerateCron,
            trigger = { synAccountGenerateTask.synAccountGenerate() },
        ),
    )
    
    private val byKey = definitions.associateBy { it.key }
    
    
    fun find(key: String): SyncScheduleDefinition? = byKey[key]
    
    companion object {
        
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
