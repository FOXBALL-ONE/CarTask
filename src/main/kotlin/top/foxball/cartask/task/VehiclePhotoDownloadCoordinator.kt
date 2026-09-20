package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.PlateNumbers
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.ArrayDeque

/**
 * 车辆图片下载协调器。
 *
 * 负责并行下载车辆进出记录的抓拍图片，执行全局启动间隔限速和并发控制。
 * 一个同步任务运行期间持有一个协调器实例，任务结束后关闭。
 */
class VehiclePhotoDownloadCoordinator(
    private val concurrency: Int,
    private val startInterval: Duration,
    private val fileService: FileService,
    private val accessRecordRepository: AccessRecordRepository,
) : AutoCloseable {

    private val executor: ExecutorService = ThreadPoolExecutor(
        concurrency,
        concurrency,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(concurrency * 2),
        ThreadFactory { runnable ->
            Thread(runnable, "vehicle-photo-download-${threadCounter.getAndIncrement()}").apply {
                isDaemon = false
            }
        },
        ThreadPoolExecutor.AbortPolicy()
    )

    private val rateLimiter = RateLimiter(startInterval)
    private val logger = LoggerFactory.getLogger(javaClass)


    /**
     * 提交一批图片下载任务并等待完成。
     *
     * @param tasks 待下载的记录 ID 和源地址列表
     * @return 下载统计：成功数、失败数、跳过数
     */
    /** 将一批图片任务按有界窗口提交，并等待全部任务完成后汇总结果。 */
    fun downloadBatch(tasks: List<PhotoDownloadTask>): DownloadBatchResult {
        if (tasks.isEmpty()) return DownloadBatchResult(0, 0, 0)

        var successCount = 0
        var failedCount = 0
        var skippedCount = 0

        // 只保留一个不超过并行数的 Future 窗口，避免拒绝策略让提交线程越过并发上限执行任务。
        val futures = ArrayDeque<Future<PhotoDownloadResult>>(concurrency)
        /** 收集单个异步结果，将异常转换为失败计数而不影响其它任务。 */
        fun collect(future: Future<PhotoDownloadResult>) {
            try {
                when (future.get()) {
                    PhotoDownloadResult.SUCCESS -> successCount++
                    PhotoDownloadResult.FAILED -> failedCount++
                    PhotoDownloadResult.SKIPPED -> skippedCount++
                }
            } catch (exception: Exception) {
                logger.warn("图片下载任务执行异常", exception)
                failedCount++
            }
        }

        tasks.forEach { task ->
            if (futures.size >= concurrency) collect(futures.removeFirst())
            futures.addLast(executor.submit<PhotoDownloadResult> {
                rateLimiter.acquire()
                downloadSingle(task)
            })
        }
        while (futures.isNotEmpty()) collect(futures.removeFirst())

        return DownloadBatchResult(successCount, failedCount, skippedCount)
    }


    /** 下载单张抓拍图片并更新车辆进出记录的同步状态。 */
    private fun downloadSingle(task: PhotoDownloadTask): PhotoDownloadResult {
        try {
            val record = accessRecordRepository.findById(task.recordId).orElse(null)
            if (record == null) {
                logger.debug("记录 {} 已删除，跳过图片下载", task.recordId)
                return PhotoDownloadResult.SKIPPED
            }

            val url = task.sourcePhotoUrl?.trim()
            if (url.isNullOrBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                record.photoSyncStatus = AccessRecord.PhotoSyncStatus.NOT_AVAILABLE
                record.photoUrl = url
                accessRecordRepository.save(record)
                return PhotoDownloadResult.SKIPPED
            }

            val fileData = fileService.importRemote(
                url,
                FileService.FileOrigin(
                    businessType = StoredFile.BUSINESS_VEHICLE_PLATE,
                    businessId = PlateNumbers.normalize(task.plateNumber),
                ),
            )

            record.photoUrl = fileData.downloadUrl
            record.photoSyncStatus = AccessRecord.PhotoSyncStatus.LOCAL
            record.photoSyncError = null
            accessRecordRepository.save(record)

            return PhotoDownloadResult.SUCCESS
        } catch (exception: Exception) {
            try {
                val record = accessRecordRepository.findById(task.recordId).orElse(null)
                if (record != null) {
                    record.photoUrl = task.sourcePhotoUrl
                    record.photoSyncStatus = AccessRecord.PhotoSyncStatus.FAILED
                    record.photoSyncError = exception.message?.take(2048) ?: exception.javaClass.simpleName
                    accessRecordRepository.save(record)
                }
            } catch (saveException: Exception) {
                logger.warn("保存图片下载失败状态时异常：recordId={}", task.recordId, saveException)
            }
            logger.warn("车辆进出抓拍图片下载失败：{}", task.sourcePhotoUrl, exception)
            return PhotoDownloadResult.FAILED
        }
    }


    /** 关闭图片下载线程池，等待已提交任务完成后再强制停止。 */
    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("图片下载执行器未能在规定时间内关闭")
                }
            }
        } catch (exception: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }


    data class PhotoDownloadTask(
        val recordId: Long,
        val sourcePhotoUrl: String?,
        val plateNumber: String?,
    )


    data class DownloadBatchResult(
        val successCount: Int,
        val failedCount: Int,
        val skippedCount: Int,
    )


    private enum class PhotoDownloadResult {
        SUCCESS,
        FAILED,
        SKIPPED,
    }


    /**
     * 全局启动间隔限速器。
     *
     * 确保任意两次下载请求的启动时间至少相隔指定间隔。
     * 使用 System.nanoTime() 避免系统时间回拨影响。
     */
    private class RateLimiter(private val interval: Duration) {
        private val intervalNanos = interval.toNanos()
        private val nextAvailable = AtomicLong(0)


        /** 原子预占下一次请求时间，保证并发下载的全局启动间隔。 */
        fun acquire() {
            if (intervalNanos <= 0) return

            val now = System.nanoTime()
            while (true) {
                val expected = nextAvailable.get()
                val target = maxOf(now, expected)
                val next = target + intervalNanos

                if (nextAvailable.compareAndSet(expected, next)) {
                    val waitNanos = target - now
                    if (waitNanos > 0) {
                        try {
                            TimeUnit.NANOSECONDS.sleep(waitNanos)
                        } catch (exception: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw RuntimeException("图片下载限速等待被中断", exception)
                        }
                    }
                    return
                }
            }
        }
    }


    companion object {
        private val threadCounter = AtomicLong(0)
    }
}
