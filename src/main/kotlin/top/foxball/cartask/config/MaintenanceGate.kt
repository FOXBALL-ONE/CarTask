package top.foxball.cartask.config

import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 生成备份期间的静默闸门。
 *
 * 备份要的是一份「停下来照的相」：逐表读取的过程中只要有同步任务在写，导出的就是半个批次的数据——
 * 某张表已经是新的、关联的另一张还是旧的，恢复出来自相矛盾。所以在正式生成期间：
 *
 *  - 普通 HTTP 请求一律快速失败（503），只放行备份接口本身与健康检查；
 *  - 定时同步任务直接跳过；已经在跑的同步会让备份等它跑完再开始。
 *
 * 用读写锁而不是一个布尔开关：布尔开关有窗口——检查通过之后、置位之前进来的请求照样写得进去。
 * 读锁覆盖整个请求或整次同步，备份拿写锁，两侧严格互斥。
 *
 * 锁用公平模式：备份排队等写锁期间，新来的请求一样拿不到读锁，不会因为源源不断的流量把备份饿死。
 */
@Component
class MaintenanceGate {
    private val lock = ReentrantReadWriteLock(true)

    /**
     * 当前线程持有读锁的层数。
     *
     * 手工触发的同步是在请求线程里跑的，那里已经由过滤器拿过一次读锁；同步任务再拿一次会变成
     * 重入，释放次数对不上就会把锁多还一次。用层数把成对的进出配平。
     */
    private val depth = ThreadLocal.withInitial { 0 }

    /** 普通请求或同步任务：拿读锁；备份进行中（或已在排队）时立刻返回 false，不排队等。 */
    fun enterNormalOperation(): Boolean {
        if (depth.get() > 0) {
            depth.set(depth.get() + 1)
            return true
        }
        if (!lock.readLock().tryLock()) {
            return false
        }
        depth.set(1)
        return true
    }

    fun leaveNormalOperation() {
        val current = depth.get()
        if (current == 0) return
        if (current > 1) {
            depth.set(current - 1)
            return
        }
        depth.remove()
        lock.readLock().unlock()
    }

    /** 生成备份：拿写锁，等在途的请求与同步结束后开始，期间挡住新的。 */
    fun <T> runExclusive(block: () -> T): T {
        lock.writeLock().lock()
        try {
            return block()
        } finally {
            lock.writeLock().unlock()
        }
    }
}
