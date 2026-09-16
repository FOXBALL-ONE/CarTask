package top.foxball.cartask.config

/**
 * SystemRoleInitializer 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository


@Component
/**
 * SystemRoleInitializer 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class SystemRoleInitializer(
    private val roleRepository: RoleRepository,
) {
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @EventListener(classes = [ApplicationReadyEvent::class])
    @Transactional
            
            
            /**
             * write 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun write() {
        ensure(SecurityRole.SUPER_ADMIN, "超级管理员")
        ensure(SecurityRole.ADMIN, "平台管理")
        ensure(SecurityRole.DEPT_ADMIN, "部门管理")
        ensure(SecurityRole.USER, "普通用户")
    }
    
    
    private fun ensure(role: String, displayName: String) {
        val existing = roleRepository.findByNameIgnoreCase(role)
        if (existing == null) {
            roleRepository.save(
                Role().apply {
                    name = role
                    description = displayName
                    enabled = true
                },
            )
            return
        }
        if (existing.description.isNullOrBlank()) {
            existing.description = displayName
            roleRepository.save(existing)
        }
    }
}


