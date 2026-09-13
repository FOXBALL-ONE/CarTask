package top.foxball.cartask.task

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.support.CronTrigger
import top.foxball.cartask.config.MaintenanceGate
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.service.SyncScheduleChangedEvent
import top.foxball.cartask.service.SyncScheduleService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SyncScheduleSchedulerTests {
    private val areaInfoTask = mock<SynAreaInfoTask>()
    private val carCapInfoTask = mock<SynCarCapInfoTask>()
    private val ownerArchiveTask = mock<SynOwnerArchiveTask>()
    private val accountGenerateTask = mock<SynAccountGenerateTask>()

    private val catalog = SyncScheduleCatalog(
        synAreaInfoTask = areaInfoTask,
        synCarCapInfoTask = carCapInfoTask,
        synOwnerArchiveTask = ownerArchiveTask,
        synAccountGenerateTask = accountGenerateTask,
        keytopProperties = KeytopProperties(),
        ownerArchiveCron = "0 45 2 * * *",
        accountGenerateCron = "0 0 3 * * *",
    )

    private val future = mock<ScheduledFuture<Any>>()
    private val taskScheduler = mock<TaskScheduler>()
    private val provider = mock<ObjectProvider<TaskScheduler>>()
    private val service = mock<SyncScheduleService>()
    private val gate = MaintenanceGate()

    private fun scheduler() = SyncScheduleScheduler(catalog, service, gate, provider)

    /** 让每次注册都返回一个句柄，并按目录里的默认周期回应取值。 */
    private fun stubScheduling() {
        whenever(provider.ifAvailable).thenReturn(taskScheduler)
        catalog.definitions.forEach { whenever(service.cronFor(it.key)).thenReturn(it.defaultCron) }
        whenever(taskScheduler.schedule(any<Runnable>(), any<Trigger>())).thenReturn(future)
    }

    @Test
    fun `启动时为目录里的每个任务注册触发时间`() {
        stubScheduling()

        scheduler().scheduleAll()

        val triggers = argumentCaptor<Trigger>()
        verify(taskScheduler, times(catalog.definitions.size)).schedule(any<Runnable>(), triggers.capture())
        // 每个任务都必须真的拿到 cron 触发器：CronTrigger 的构造过程就是一次解析，
        // 注册成坏表达式会在这里被挡住；至于「表达式永不触发」，由服务层保存时用
        // next() 为空来拒绝，这里不必重复。
        triggers.allValues.forEach { trigger ->
            assertTrue(trigger is CronTrigger, "注册的触发器必须是 cron 触发器，实际：$trigger")
        }
        catalog.definitions.forEach { definition -> verify(service).cronFor(definition.key) }
    }

    @Test
    fun `改完周期只重新注册被改的那个任务`() {
        stubScheduling()
        val scheduler = scheduler()
        scheduler.scheduleAll()

        val changedKey = SynCarCapInfoTask.TASK_KEY
        whenever(service.cronFor(changedKey)).thenReturn("0 */10 * * * *")
        scheduler.onScheduleChanged(SyncScheduleChangedEvent(changedKey))

        // 全部定义各注册一次，加上被改的那一个再注册一次。
        verify(taskScheduler, times(catalog.definitions.size + 1)).schedule(any<Runnable>(), any<Trigger>())
        verify(service, times(2)).cronFor(changedKey)
        verify(service, times(1)).cronFor(SynAreaInfoTask.TASK_KEY)
    }

    @Test
    fun `未知任务的变更事件不会注册任何东西`() {
        stubScheduling()

        scheduler().onScheduleChanged(SyncScheduleChangedEvent("no.such.task"))

        verify(taskScheduler, never()).schedule(any<Runnable>(), any<Trigger>())
    }

    @Test
    fun `没有可用调度器时安静跳过而不是让应用起不来`() {
        whenever(provider.ifAvailable).thenReturn(null)

        scheduler().scheduleAll()

        verify(taskScheduler, never()).schedule(any<Runnable>(), any<Trigger>())
    }

    @Test
    fun `周期非法时只跳过那一个任务`() {
        stubScheduling()
        val brokenKey = SynAreaInfoTask.TASK_KEY
        whenever(service.cronFor(brokenKey)).thenReturn("坏表达式")

        scheduler().scheduleAll()

        verify(taskScheduler, times(catalog.definitions.size - 1)).schedule(any<Runnable>(), any<Trigger>())
    }

    @Test
    fun `调度器返回空句柄时不影响其余任务注册`() {
        stubScheduling()
        whenever(taskScheduler.schedule(any<Runnable>(), any<Trigger>())).thenReturn(null)

        // 只应记一条告警：半数任务因为取不到句柄而抛异常，才是真正的故障。
        scheduler().scheduleAll()

        verify(taskScheduler, times(catalog.definitions.size)).schedule(any<Runnable>(), any<Trigger>())
    }

    @Test
    fun `生成备份期间跳过同步而不是照跑`() {
        stubScheduling()
        val registered = argumentCaptor<Runnable>()
        scheduler().scheduleAll()
        verify(taskScheduler, times(catalog.definitions.size)).schedule(registered.capture(), any<Trigger>())

        // 另起一个线程持着写锁，等价于「备份正在生成」。
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread {
            gate.runExclusive {
                holding.countDown()
                release.await()
            }
        }
        assertTrue(holding.await(5, TimeUnit.SECONDS), "没能进入备份状态")

        try {
            registered.allValues.forEach { it.run() }

            // 备份期间同步写进去就是半个批次的数据，必须一条都不跑。
            verifyNoInteractions(areaInfoTask, carCapInfoTask, ownerArchiveTask, accountGenerateTask)
        } finally {
            release.countDown()
            holder.join(5_000)
        }

        // 备份结束后立刻恢复：闸门不能被某次执行长期占着。
        registered.allValues.forEach { it.run() }
        verify(areaInfoTask).synAreaInfo()
    }
}
