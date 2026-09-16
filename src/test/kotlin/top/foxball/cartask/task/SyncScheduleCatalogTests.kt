package top.foxball.cartask.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.springframework.scheduling.support.CronExpression
import top.foxball.cartask.keytop.KeytopProperties
import java.time.LocalDateTime
import java.time.ZoneId





class SyncScheduleCatalogTests {
    private val areaInfoTask = mock<SynAreaInfoTask>()
    private val carCapInfoTask = mock<SynCarCapInfoTask>()
    private val ownerArchiveTask = mock<SynOwnerArchiveTask>()
    private val accountGenerateTask = mock<SynAccountGenerateTask>()

    private fun catalog(
        areaCron: String = "0 0 2 * * *",
        carCapCron: String = "0 */5 * * * *",
        reconciliationCron: String = "0 30 3 * * *",
        ownerArchiveCron: String = "0 45 2 * * *",
        accountGenerateCron: String = "0 0 3 * * *",
    ) = SyncScheduleCatalog(
        synAreaInfoTask = areaInfoTask,
        synCarCapInfoTask = carCapInfoTask,
        synOwnerArchiveTask = ownerArchiveTask,
        synAccountGenerateTask = accountGenerateTask,
        keytopProperties = KeytopProperties().copy(
            areaSyncCron = areaCron,
            carCapInfoSyncCron = carCapCron,
            carCapInfoReconciliationCron = reconciliationCron,
        ),
        ownerArchiveCron = ownerArchiveCron,
        accountGenerateCron = accountGenerateCron,
    )

    @Test
    fun `默认周期沿用配置项里的执行时刻`() {
        val definitions = catalog().definitions.associateBy { it.key }

        assertEquals(
            setOf(
                SynAreaInfoTask.TASK_KEY,
                SynCarCapInfoTask.TASK_KEY,
                SynCarCapInfoTask.RECONCILIATION_TASK_KEY,
                SynOwnerArchiveTask.TASK_KEY,
                SynAccountGenerateTask.TASK_KEY,
            ),
            definitions.keys,
        )
        assertEquals("0 0 2 * * *", definitions.getValue(SynAreaInfoTask.TASK_KEY).defaultCron)
        assertEquals("0 */5 * * * *", definitions.getValue(SynCarCapInfoTask.TASK_KEY).defaultCron)
        assertEquals("0 30 3 * * *", definitions.getValue(SynCarCapInfoTask.RECONCILIATION_TASK_KEY).defaultCron)
        assertEquals("0 45 2 * * *", definitions.getValue(SynOwnerArchiveTask.TASK_KEY).defaultCron)
        assertEquals("0 0 3 * * *", definitions.getValue(SynAccountGenerateTask.TASK_KEY).defaultCron)
    }

    @Test
    fun `周期一律按上海时区解释`() {
        assertEquals(ZoneId.of("Asia/Shanghai"), SyncScheduleCatalog.ZONE)
    }

    @Test
    fun `目录里的默认周期都能算出下次执行时间`() {
        catalog().definitions.forEach { definition ->
            assertNotNull(
                definition.defaultCron.takeIf { it.isNotBlank() },
                "任务 ${definition.key} 没有默认周期",
            )
            assertNotNull(
                CronExpression.parse(definition.defaultCron).next(LocalDateTime.now(SyncScheduleCatalog.ZONE)),
                "任务 ${definition.key} 的默认周期 ${definition.defaultCron} 永远不会触发",
            )
        }
    }

    @Test
    fun `周期取自配置而不是写死在目录里`() {
        val definitions = catalog(areaCron = "0 15 1 * * *", ownerArchiveCron = "0 5 4 * * *").definitions
            .associateBy { it.key }

        assertEquals("0 15 1 * * *", definitions.getValue(SynAreaInfoTask.TASK_KEY).defaultCron)
        assertEquals("0 5 4 * * *", definitions.getValue(SynOwnerArchiveTask.TASK_KEY).defaultCron)
    }

    @Test
    fun `触发时调用的是对应任务自己的定时入口`() {
        val definitions = catalog().definitions.associateBy { it.key }

        definitions.getValue(SynAreaInfoTask.TASK_KEY).trigger()
        verify(areaInfoTask).synAreaInfo()

        definitions.getValue(SynCarCapInfoTask.TASK_KEY).trigger()
        verify(carCapInfoTask).synCarCapInfoList()

        definitions.getValue(SynCarCapInfoTask.RECONCILIATION_TASK_KEY).trigger()
        verify(carCapInfoTask).reconcileCarCapInfoList()

        definitions.getValue(SynOwnerArchiveTask.TASK_KEY).trigger()
        verify(ownerArchiveTask).synOwnerArchive()

        definitions.getValue(SynAccountGenerateTask.TASK_KEY).trigger()
        verify(accountGenerateTask).synAccountGenerate()

        verifyNoMoreInteractions(areaInfoTask, carCapInfoTask, ownerArchiveTask, accountGenerateTask)
    }
}
