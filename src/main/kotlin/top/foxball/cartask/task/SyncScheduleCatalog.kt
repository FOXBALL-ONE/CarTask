package top.foxball.cartask.task

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import top.foxball.cartask.keytop.KeytopProperties
import java.time.ZoneId

/**
 * 一个可以被改周期的同步任务。
 *
 * [key] 直接取自各任务自己的 `TASK_KEY` 常量，而不是另写一份字面量：周期和执行历史在页面上
 * 是按这个键对上的，两处各写一遍迟早会漂。
 */
data class SyncScheduleDefinition(
    val key: String,
    val name: String,
    val description: String,
    val defaultCron: String,
    val trigger: () -> Unit,
)

/**
 * 可调周期的同步任务目录。
 *
 * 默认周期全部来自配置（环境变量），目录本身只负责把「有哪些任务、默认怎么跑、怎么触发」
 * 汇总到一处；是否存在覆盖值由 [top.foxball.cartask.service.SyncScheduleService] 决定。
 */
@Component
class SyncScheduleCatalog(
    private val synAreaInfoTask: SynAreaInfoTask,
    private val synCarCapInfoTask: SynCarCapInfoTask,
    private val synOwnerArchiveTask: SynOwnerArchiveTask,
    private val synAccountGenerateTask: SynAccountGenerateTask,
    keytopProperties: KeytopProperties,
    // 这两项原本只写在 @Scheduled 的占位符里，没有对应的 properties 类；沿用原有键名，
    // 不改配置路径，避免破坏外部按 app.*-cron 写的覆盖。
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
        /** 与原 @Scheduled 注解上的 zone 保持一致，改周期不改变 cron 的解释时区。 */
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
