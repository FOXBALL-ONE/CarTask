package top.foxball.cartask.keytop

import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import top.foxball.cartask.config.DotenvLoader
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 为同步任务中的 Keytop 请求提供单实例全局限频。
 *
 * 同步入口开始时取得 [Snapshot] 并在整个任务中复用。配置文件可以在任务运行期间
 * 被修改，但不会影响已经取得的快照；下一次同步开始时才会读取新值。
 */
@Component
class KeytopSyncRateLimiter(
    private val environment: Environment,
) {
    private val nextAvailableNanos = AtomicLong(0)
    private val snapshotContext = ThreadLocal<Snapshot?>()

    fun snapshot(): Snapshot = snapshotContext.get() ?: Snapshot(
        interval = readInterval(ENV_KEY, PROPERTY_KEY, DEFAULT_INTERVAL),
        photoDownloadInterval = readInterval(
            PHOTO_INTERVAL_ENV_KEY,
            PHOTO_INTERVAL_PROPERTY_KEY,
            DEFAULT_PHOTO_INTERVAL,
        ),
        photoDownloadConcurrency = readConcurrency(),
    )

    fun hasSnapshot(): Boolean = snapshotContext.get() != null

    fun <T> withSnapshot(snapshot: Snapshot, action: () -> T): T {
        val previous = snapshotContext.get()
        snapshotContext.set(snapshot)
        return try {
            action()
        } finally {
            if (previous == null) snapshotContext.remove() else snapshotContext.set(previous)
        }
    }

    /** KeytopServiceImpl 在真正发起 HTTP 请求前调用。 */
    fun acquire() {
        snapshotContext.get()?.let(::acquire)
    }

    private fun acquire(snapshot: Snapshot) {
        val intervalNanos = snapshot.interval.toNanos()
        if (intervalNanos <= 0) return

        while (true) {
            val now = System.nanoTime()
            val expected = nextAvailableNanos.get()
            val target = maxOf(now, expected)
            val next = target + intervalNanos
            if (!nextAvailableNanos.compareAndSet(expected, next)) continue

            val waitNanos = target - now
            if (waitNanos <= 0) return
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Keytop 请求限频等待被中断", exception)
            }
            return
        }
    }

    private fun readInterval(envKey: String, propertyKey: String, defaultValue: String): Duration {
        val raw = DotenvLoader.read(DotenvLoader.defaultPath())[envKey]
            ?: environment.getProperty(propertyKey)
            ?: defaultValue
        val interval = parseDuration(raw, envKey)
        require(!interval.isNegative) { "$envKey 不能为负数" }
        require(interval <= MAX_INTERVAL) { "$envKey 不能超过 60 秒" }
        return interval
    }

    private fun readConcurrency(): Int {
        val raw = DotenvLoader.read(DotenvLoader.defaultPath())[PHOTO_CONCURRENCY_ENV_KEY]
            ?: environment.getProperty(PHOTO_CONCURRENCY_PROPERTY_KEY)
            ?: DEFAULT_PHOTO_CONCURRENCY.toString()
        val concurrency = raw.trim().toIntOrNull()
            ?: throw IllegalArgumentException("$PHOTO_CONCURRENCY_ENV_KEY 必须是整数")
        require(concurrency in 1..16) { "$PHOTO_CONCURRENCY_ENV_KEY 必须在 1 到 16 之间" }
        return concurrency
    }

    private fun parseDuration(raw: String, envKey: String): Duration {
        val value = raw.trim()
        require(value.isNotEmpty()) { "$envKey 不能为空" }
        return try {
            val match = Regex("^(\\d+)(ms|s|m|h|d)?$", RegexOption.IGNORE_CASE).matchEntire(value)
            if (match == null) {
                Duration.parse(value)
            } else {
                when (match.groupValues[2].lowercase()) {
                    "", "ms" -> Duration.ofMillis(match.groupValues[1].toLong())
                    "s" -> Duration.ofSeconds(match.groupValues[1].toLong())
                    "m" -> Duration.ofMinutes(match.groupValues[1].toLong())
                    "h" -> Duration.ofHours(match.groupValues[1].toLong())
                    "d" -> Duration.ofDays(match.groupValues[1].toLong())
                    else -> error("unknown duration unit")
                }
            }
        } catch (exception: Exception) {
            throw IllegalArgumentException("无法解析 Keytop 限频间隔：$value（示例：200ms、1s、30s）", exception)
        }
    }

    data class Snapshot(
        val interval: Duration,
        val photoDownloadInterval: Duration = Duration.ofMillis(200),
        val photoDownloadConcurrency: Int = 4,
    )

    private companion object {
        const val ENV_KEY = "KEYTOP_SYNC_REQUEST_INTERVAL"
        const val PROPERTY_KEY = "keytop.sync-request-interval"
        const val DEFAULT_INTERVAL = "200ms"
        const val PHOTO_INTERVAL_ENV_KEY = "KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL"
        const val PHOTO_INTERVAL_PROPERTY_KEY = "keytop.car-cap-info-photo-download-interval"
        const val DEFAULT_PHOTO_INTERVAL = "200ms"
        const val PHOTO_CONCURRENCY_ENV_KEY = "KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY"
        const val PHOTO_CONCURRENCY_PROPERTY_KEY = "keytop.car-cap-info-photo-download-concurrency"
        const val DEFAULT_PHOTO_CONCURRENCY = 4
        val MAX_INTERVAL: Duration = Duration.ofSeconds(60)
    }
}
