package top.foxball.cartask.config

import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository

/**
 * 补齐四个内置角色行。
 *
 * 角色行不是可有可无的展示数据：认证时 [top.foxball.cartask.authentication.RolePermissionService]
 * 按用户的主角色编码查角色行取权限，查不到直接以「用户角色未配置」拒绝登录。而此前只有
 * AdminInitializer 建 ADMIN（且默认关闭）、MockDataInitializer 建 USER，全新库里没有
 * SUPER_ADMIN 行，超级管理员根本无法登录。本初始化器把四个角色一次性补齐。
 *
 * 幂等且不覆盖人工配置：仅在角色缺失时新建，仅在描述为空时补默认显示名。
 */
@Component
class SystemRoleInitializer(
    private val roleRepository: RoleRepository,
) {
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @EventListener(classes = [ApplicationReadyEvent::class])
    @Transactional
            /**
             * write：创建、保存或初始化相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun write() {
        ensure(SecurityRole.SUPER_ADMIN, "超级管理员")
        ensure(SecurityRole.ADMIN, "平台管理")
        ensure(SecurityRole.DEPT_ADMIN, "部门管理")
        ensure(SecurityRole.USER, "普通用户")
    }

    /**
     * ensure：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param role 参与本次处理的输入参数。
     * @param displayName 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
        // 显示名允许管理员在角色管理里改写，这里只补空白，避免每次启动覆盖人工配置。
        if (existing.description.isNullOrBlank()) {
            existing.description = displayName
            roleRepository.save(existing)
        }
    }
}
