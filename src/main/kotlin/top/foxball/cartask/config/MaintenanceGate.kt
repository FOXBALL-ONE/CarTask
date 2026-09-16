package top.foxball.cartask.config

/**
 * MaintenanceGate 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReentrantReadWriteLock


@Component
/**
 * MaintenanceGate 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class MaintenanceGate {
    private val lock = ReentrantReadWriteLock(true)
    
    
    private val depth = ThreadLocal.withInitial { 0 }
    
    
    /**
     * enterNormalOperation 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
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
    
    
    /**
     * leaveNormalOperation 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
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
    
    
    fun <T> runExclusive(block: () -> T): T {
        lock.writeLock().lock()
        try {
            return block()
        } finally {
            lock.writeLock().unlock()
        }
    }
}


