package top.foxball.cartask.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.SyncSchedule
import top.foxball.cartask.handler.ParamErrorException
import top.foxball.cartask.handler.ResourceNotFoundException
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.repository.SyncScheduleRepository
import top.foxball.cartask.task.SynAccountGenerateTask
import top.foxball.cartask.task.SynAreaInfoTask
import top.foxball.cartask.task.SynCarCapInfoTask
import top.foxball.cartask.task.SyncScheduleCatalog
import top.foxball.cartask.task.SynOwnerArchiveTask
import java.time.LocalDateTime
import java.util.Optional

class SyncScheduleServiceTests {
    private val store = mutableMapOf<String, SyncSchedule>()

    private val repository = mock<SyncScheduleRepository>().also { repository ->
        whenever(repository.findAll()).thenAnswer { store.values.toList() }
        whenever(repository.findById(any())).thenAnswer { Optional.ofNullable(store[it.getArgument<String>(0)]) }
        whenever(repository.save(any())).thenAnswer {
            val entity = it.getArgument<SyncSchedule>(0)
            store[entity.taskKey] = entity
            entity
        }
        whenever(repository.delete(any<SyncSchedule>())).thenAnswer {
            store.remove(it.getArgument<SyncSchedule>(0).taskKey)
            Unit
        }
    }

    private val auditService = mock<AuditService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()

    private val catalog = SyncScheduleCatalog(
        synAreaInfoTask = mock<SynAreaInfoTask>(),
        synCarCapInfoTask = mock<SynCarCapInfoTask>(),
        synOwnerArchiveTask = mock<SynOwnerArchiveTask>(),
        synAccountGenerateTask = mock<SynAccountGenerateTask>(),
        keytopProperties = KeytopProperties(),
        ownerArchiveCron = "0 45 2 * * *",
        accountGenerateCron = "0 0 3 * * *",
    )

    private val service = SyncScheduleService(catalog, repository, auditService, eventPublisher)

    private fun saveOverride(taskKey: String, cron: String) {
        store[taskKey] = SyncSchedule().apply {
            this.taskKey = taskKey
            cronExpression = cron
            updatedAt = LocalDateTime.now()
        }
    }

    @Test
    fun `没有覆盖记录时返回配置里的默认周期`() {
        val views = service.list()

        assertEquals(catalog.definitions.size, views.size)
        views.forEach { view ->
            assertFalse(view.customized, "${view.taskKey} 不该被标记为已自定义")
            assertEquals(view.defaultCron, view.cron)
            assertNotNull(view.nextRunAt, "${view.taskKey} 的默认周期应当能算出下次执行时间")
        }
        assertTrue(views.all { it.updatedAt == null && it.updatedBy == null })
    }

    @Test
    fun `全部默认周期都是可解析的六段式表达式`() {
        catalog.definitions.forEach { definition ->
            assertNotNull(
                service.nextRunAt(definition.key),
                "任务 ${definition.key} 的默认周期 ${definition.defaultCron} 解析不出下次执行时间",
            )
        }
    }

    @Test
    fun `改周期会落库并记录审计与重排事件`() {
        val taskKey = SynCarCapInfoTask.TASK_KEY

        val updated = service.update(taskKey, "0 */10 * * * *")

        assertEquals("0 */10 * * * *", updated.cron)
        assertTrue(updated.customized)
        assertEquals("0 */10 * * * *", store.getValue(taskKey).cronExpression)

        val command = argumentCaptor<AuditCommand>()
        verify(auditService).record(command.capture())
        assertEquals(AuditAction.SYNC_SCHEDULE_CHANGED, command.firstValue.action)
        assertEquals(taskKey, command.firstValue.targetId)
        assertEquals(SynCarCapInfoTask.TASK_NAME, command.firstValue.targetSummary?.get("task"))
        assertEquals("0 */10 * * * *", command.firstValue.targetSummary?.get("cron"))
        assertEquals(catalog.find(taskKey)!!.defaultCron, command.firstValue.targetSummary?.get("previous_cron"))
        verify(eventPublisher).publishEvent(any<SyncScheduleChangedEvent>())
    }

    @Test
    fun `改回默认值会删掉覆盖记录`() {
        val taskKey = SynAreaInfoTask.TASK_KEY
        val defaultCron = catalog.find(taskKey)!!.defaultCron
        service.update(taskKey, "0 30 4 * * *")
        assertTrue(store.containsKey(taskKey))

        val reverted = service.update(taskKey, defaultCron)

        assertFalse(store.containsKey(taskKey), "与默认值相同的覆盖记录应当被删除")
        assertEquals(defaultCron, reverted.cron)
        assertFalse(reverted.customized)
    }

    @Test
    fun `非法 cron 被拒绝且不落库`() {
        val taskKey = SynAccountGenerateTask.TASK_KEY

        assertThrows(ParamErrorException::class.java) { service.update(taskKey, "不是 cron") }
        // 五段式是最常见的误填，必须明确拒绝而不是当六段式收下。
        assertThrows(ParamErrorException::class.java) { service.update(taskKey, "*/5 * * * *") }
        assertThrows(ParamErrorException::class.java) { service.update(taskKey, "  ") }

        assertFalse(store.containsKey(taskKey))
        verify(auditService, never()).record(any())
    }

    @Test
    fun `未知任务返回不存在`() {
        assertThrows(ResourceNotFoundException::class.java) { service.update("no.such.task", "0 0 1 * * *") }
    }

    @Test
    fun `库里存了坏表达式时退回默认值而不是让调度器炸掉`() {
        val taskKey = SynOwnerArchiveTask.TASK_KEY
        saveOverride(taskKey, "坏表达式")

        val cron = service.cronFor(taskKey)

        assertEquals(catalog.find(taskKey)!!.defaultCron, cron)
        // 页面显示的口径与调度器一致：都回退到默认值，避免「显示一个值、按另一个值跑」。
        assertEquals(catalog.find(taskKey)!!.defaultCron, service.list().first { it.taskKey == taskKey }.cron)
    }
}
