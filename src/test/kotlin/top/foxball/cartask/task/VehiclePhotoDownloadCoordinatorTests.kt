package top.foxball.cartask.task

import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.FileService
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * VehiclePhotoDownloadCoordinator 的行为测试，对应方案文档 §11.1。
 *
 * 限速和并发都直接依赖真实时间，这里不注入时钟，而是把单次下载的耗时放大到 100ms 级别，
 * 再用「同刻在飞的请求数」和「相邻启动时间差」去量——阈值留得比调度抖动大一个量级。
 */
class VehiclePhotoDownloadCoordinatorTests {
    private val fileService = mock<FileService>()
    private val repository = mock<AccessRecordRepository>()


    private fun record(id: Long, sourceUrl: String? = "https://image.example.com/$id.jpg"): AccessRecord =
        AccessRecord().apply {
            this.id = id
            carNumber = "闽A12345"
            inAndOut = AccessRecord.InAndOut.IN
            inAndOutTime = LocalDateTime.of(2026, 9, 19, 10, 0)
            sourcePhotoUrl = sourceUrl
            photoUrl = sourceUrl
            photoSyncStatus = AccessRecord.PhotoSyncStatus.PENDING
        }


    private fun stored(record: AccessRecord) {
        whenever(repository.findById(record.id!!)).thenReturn(Optional.of(record))
    }


    private fun fileData(url: String = "https://files.example.com/local.jpg") =
        FileService.FileData(UUID.randomUUID(), "capture.jpg", "image/jpeg", 3, url, LocalDateTime.now())


    private fun coordinator(concurrency: Int = 4, interval: Duration = Duration.ZERO) =
        VehiclePhotoDownloadCoordinator(concurrency, interval, fileService, repository)


    private fun task(id: Long, url: String? = "https://image.example.com/$id.jpg") =
        VehiclePhotoDownloadCoordinator.PhotoDownloadTask(id, url, "闽A12345")


    // ---------------------------------------------------------------- 配置校验

    @Test
    fun `负的下载间隔会让配置绑定失败`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            KeytopProperties(carCapInfoPhotoDownloadInterval = Duration.ofMillis(-1))
        }
        assertTrue(failure.message.orEmpty().contains("间隔"))
    }


    @Test
    fun `下载间隔超过六十秒会让配置绑定失败`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            KeytopProperties(carCapInfoPhotoDownloadInterval = Duration.ofSeconds(61))
        }
        assertTrue(failure.message.orEmpty().contains("60 秒"))
    }


    @Test
    fun `并行数小于 1 会让配置绑定失败`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            KeytopProperties(carCapInfoPhotoDownloadConcurrency = 0)
        }
        assertTrue(failure.message.orEmpty().contains("1 到 16"))
    }


    @Test
    fun `并行数大于 16 会让配置绑定失败`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            KeytopProperties(carCapInfoPhotoDownloadConcurrency = 17)
        }
        assertTrue(failure.message.orEmpty().contains("1 到 16"))
    }


    @Test
    fun `默认配置是 200 毫秒加 4 个并行`() {
        val properties = KeytopProperties()
        assertEquals(Duration.ofMillis(200), properties.carCapInfoPhotoDownloadInterval)
        assertEquals(4, properties.carCapInfoPhotoDownloadConcurrency)
    }


    // ---------------------------------------------------------------- 并发与限速

    @Test
    fun `实际同时下载数不超过配置的并行数`() {
        // 任务数刻意超过「线程数 + 队列容量」，把提交端的兜底路径也压进来
        val concurrency = 2
        val taskCount = 10
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        repeat(taskCount) { stored(record(it.toLong() + 1)) }
        whenever(fileService.importRemote(any(), any())).thenAnswer {
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            Thread.sleep(80)
            inFlight.decrementAndGet()
            fileData()
        }

        coordinator(concurrency).use { it.downloadBatch((1..taskCount).map { index -> task(index.toLong()) }) }

        assertTrue(
            peak.get() <= concurrency,
            "并行数配置为 $concurrency，实际峰值却是 ${peak.get()} 个并发下载",
        )
    }


    @Test
    fun `相邻两次下载的启动时间满足全局最小间隔`() {
        val intervalMillis = 60L
        val starts = ConcurrentLinkedQueue<Long>()
        repeat(4) { stored(record(it.toLong() + 1)) }
        whenever(fileService.importRemote(any(), any())).thenAnswer {
            starts.add(System.nanoTime())
            fileData()
        }

        coordinator(concurrency = 4, interval = Duration.ofMillis(intervalMillis)).use {
            it.downloadBatch((1..4).map { index -> task(index.toLong()) })
        }

        val sorted = starts.toList().sorted()
        assertEquals(4, sorted.size)
        val gaps = sorted.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        // 留 15ms 容差吸收线程调度抖动，但仍能区分「限速生效」和「完全不限速」
        assertTrue(
            gaps.all { it >= intervalMillis - 15 },
            "配置间隔 ${intervalMillis}ms，实际相邻启动间隔为 $gaps",
        )
    }


    @Test
    fun `间隔为零时不做额外等待`() {
        val taskCount = 6
        repeat(taskCount) { stored(record(it.toLong() + 1)) }
        whenever(fileService.importRemote(any(), any())).thenAnswer { fileData() }

        val startedAt = System.nanoTime()
        coordinator(concurrency = 4, interval = Duration.ZERO).use {
            it.downloadBatch((1..taskCount).map { index -> task(index.toLong()) })
        }
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        // 若 0ms 被当成「每张等 0ms」以外的实现走岔，这里会明显超出
        assertTrue(elapsedMillis < 2000, "0ms 间隔下提交 $taskCount 个任务耗时 ${elapsedMillis}ms，像是被限速了")
    }


    // ---------------------------------------------------------------- 单张结果

    @Test
    fun `下载成功写入 LOCAL 与本地地址并清掉错误信息`() {
        val target = record(1).apply { photoSyncError = "上一次的错误" }
        stored(target)
        whenever(fileService.importRemote(eq(target.sourcePhotoUrl!!), any())).thenReturn(fileData("https://files.example.com/a.jpg"))

        val result = coordinator().use { it.downloadBatch(listOf(task(1))) }

        assertEquals(1, result.successCount)
        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, target.photoSyncStatus)
        assertEquals("https://files.example.com/a.jpg", target.photoUrl)
        assertNull(target.photoSyncError)
    }


    @Test
    fun `下载异常写入 FAILED 并保留源地址和截断后的错误信息`() {
        val target = record(1)
        stored(target)
        whenever(fileService.importRemote(any(), any())).thenThrow(IllegalArgumentException("下载远程图片失败：HTTP 404"))

        val result = coordinator().use { it.downloadBatch(listOf(task(1))) }

        assertEquals(1, result.failedCount)
        assertEquals(AccessRecord.PhotoSyncStatus.FAILED, target.photoSyncStatus)
        assertEquals(target.sourcePhotoUrl, target.photoUrl)
        assertEquals("下载远程图片失败：HTTP 404", target.photoSyncError)
    }


    @Test
    fun `单张失败不影响同批其它图片`() {
        val ok = record(1)
        val bad = record(2)
        stored(ok)
        stored(bad)
        whenever(fileService.importRemote(eq(ok.sourcePhotoUrl!!), any())).thenReturn(fileData())
        whenever(fileService.importRemote(eq(bad.sourcePhotoUrl!!), any())).thenThrow(IllegalStateException("下载远程图片失败"))

        val result = coordinator().use { it.downloadBatch(listOf(task(1), task(2))) }

        assertEquals(1, result.successCount)
        assertEquals(1, result.failedCount)
        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, ok.photoSyncStatus)
        assertEquals(AccessRecord.PhotoSyncStatus.FAILED, bad.photoSyncStatus)
    }


    @Test
    fun `记录已被删除时跳过而不是让整批失败`() {
        whenever(repository.findById(any())).thenReturn(Optional.empty())

        val result = coordinator().use { it.downloadBatch(listOf(task(404))) }

        assertEquals(1, result.skippedCount)
        assertEquals(0, result.failedCount)
        verify(fileService, never()).importRemote(any(), any())
    }


    @Test
    fun `没有远程地址的记录标为 NOT_AVAILABLE 且不发起下载`() {
        val target = record(1, sourceUrl = null)
        stored(target)

        val result = coordinator().use { it.downloadBatch(listOf(task(1, url = null))) }

        assertEquals(1, result.skippedCount)
        assertEquals(AccessRecord.PhotoSyncStatus.NOT_AVAILABLE, target.photoSyncStatus)
        verify(fileService, never()).importRemote(any(), any())
    }


    @Test
    fun `非 HTTP 地址不会被当成图片下载`() {
        val target = record(1, sourceUrl = "ftp://image.example.com/1.jpg")
        stored(target)

        val result = coordinator().use { it.downloadBatch(listOf(task(1, target.sourcePhotoUrl!!))) }

        assertEquals(1, result.skippedCount)
        assertEquals(AccessRecord.PhotoSyncStatus.NOT_AVAILABLE, target.photoSyncStatus)
        verify(fileService, never()).importRemote(any(), any())
    }


    @Test
    fun `下载时把车牌和业务类型传给文件服务`() {
        val target = record(1)
        stored(target)
        whenever(fileService.importRemote(any(), any())).thenReturn(fileData())

        coordinator().use { it.downloadBatch(listOf(task(1))) }

        val origin = org.mockito.kotlin.argumentCaptor<FileService.FileOrigin>()
        verify(fileService).importRemote(eq(target.sourcePhotoUrl!!), origin.capture())
        assertEquals(StoredFile.BUSINESS_VEHICLE_PLATE, origin.firstValue.businessType)
        assertEquals("闽A12345", origin.firstValue.businessId)
    }


    @Test
    fun `空任务批次不做任何事`() {
        val result = coordinator().use { it.downloadBatch(emptyList()) }

        assertEquals(0, result.successCount + result.failedCount + result.skippedCount)
        verify(fileService, never()).importRemote(any(), any())
    }


    // 注意：这里刻意没有「关闭后再提交任务」的用例。
    // 线程池用的是 CallerRunsPolicy，它的 rejectedExecution 会先看 !e.isShutdown()；
    // 执行器已经关闭时就直接把任务丢掉、既不执行也不结算 Future，于是 downloadBatch
    // 里的 future.get() 永远等不到结果——写出来就是一个必然超时的用例。
    // 详见测试报告里的对应条目。


    @Test
    fun `关闭会等待正在进行的下载结束`() {
        val target = record(1)
        stored(target)
        val started = CountDownLatch(1)
        whenever(fileService.importRemote(any(), any())).thenAnswer {
            started.countDown()
            Thread.sleep(300)
            fileData()
        }
        // 在独立线程提交，主线程负责关闭，验证 close 会等它跑完
        val working = coordinator()
        val submitter = Thread { working.downloadBatch(listOf(task(1))) }
        submitter.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))

        working.close()

        submitter.join(5000)
        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, target.photoSyncStatus)
    }
}
