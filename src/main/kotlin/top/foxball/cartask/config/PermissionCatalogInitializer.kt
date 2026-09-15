package top.foxball.cartask.config

import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.PermissionCatalog
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository

/**
 * 补齐内置权限字典，不覆盖数据库中已有权限的名称或启用状态。
 *
 * 必须跑在角色初始化器之后：权限是按角色行填充的，角色行还不存在时这一步会整段跳过，
 * 结果是那个角色能登录但没有任何权限。用显式 @Order 固定，不依赖同序监听器的注册顺序。
 * @Order 必须标在方法上——ApplicationListenerMethodAdapter 只读方法上的注解，标在类上会被忽略。
 */
@Component
class PermissionCatalogInitializer(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
) {
    @Order(Ordered.LOWEST_PRECEDENCE)
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
        val existingPermissions = permissionRepository.findAll().toMutableList()
        val legacyMonitorPermission = existingPermissions.singleOrNull { permission ->
            permission.code.equals(PermissionCatalog.LEGACY_SYSTEM_MONITOR_READ, ignoreCase = true)
        }
        if (legacyMonitorPermission != null) {
            val monitorPermission = existingPermissions.singleOrNull { permission ->
                permission.code == PermissionCatalog.SYSTEM_MONITOR_READ
            }
            if (monitorPermission == null) {
                legacyMonitorPermission.code = PermissionCatalog.SYSTEM_MONITOR_READ
                permissionRepository.save(legacyMonitorPermission)
            } else {
                roleRepository.findAll().forEach { role ->
                    if (role.permissions.remove(legacyMonitorPermission)) {
                        role.permissions.add(monitorPermission)
                        roleRepository.save(role)
                    }
                }
                permissionRepository.delete(legacyMonitorPermission)
                existingPermissions.remove(legacyMonitorPermission)
            }
        }
        val existingCodes = existingPermissions.map { it.code }.toSet()
        val missing = PermissionCatalog.definitions
            .filterNot { it.code in existingCodes }
            .map { definition ->
                Permission().apply {
                    code = definition.code
                    name = definition.name
                    enabled = true
                }
            }
        if (missing.isNotEmpty()) permissionRepository.saveAll(missing)
        val allPermissions = permissionRepository.findAll().associateBy { it.code }
        val superAdmin = roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)
        if (superAdmin != null) {
            val permissionCount = superAdmin.permissions.size
            superAdmin.permissions.addAll(allPermissions.values)
            if (superAdmin.permissions.size != permissionCount) roleRepository.save(superAdmin)
        }
        val admin = roleRepository.findByNameIgnoreCase(SecurityRole.ADMIN)
        if (admin != null && admin.permissions.isEmpty()) {
            admin.permissions = allPermissions.values
                .filterNot { it.code in SUPER_ADMIN_ONLY_PERMISSION_CODES }
                .toMutableSet()
            roleRepository.save(admin)
        }
        if (admin != null) {
            val monitorPermission = allPermissions[PermissionCatalog.SYSTEM_MONITOR_READ]
            if (monitorPermission != null && admin.permissions.add(monitorPermission)) roleRepository.save(admin)
            // 新增的同步权限需要补授给已有权限配置的 ADMIN 角色，避免存量环境看不到新功能。
            val ensured = allPermissions.filterKeys { it in ADMIN_ENSURED_PERMISSION_CODES }.values
            if (grantMissing(admin, ensured)) roleRepository.save(admin)
        }
        val deptAdmin = roleRepository.findByNameIgnoreCase(SecurityRole.DEPT_ADMIN)
        if (deptAdmin != null && deptAdmin.permissions.isEmpty()) {
            deptAdmin.permissions = allPermissions.values
                .filterNot { it.code in DEPT_ADMIN_DENIED_PERMISSION_CODES }
                .toMutableSet()
            roleRepository.save(deptAdmin)
        }
        if (deptAdmin != null) {
            // 后补进入字典的权限同样要补授，避免存量环境里部门管理看不到新功能。
            val ensured = allPermissions.filterKeys { it in DEPT_ADMIN_ENSURED_PERMISSION_CODES }.values
            if (grantMissing(deptAdmin, ensured)) roleRepository.save(deptAdmin)
        }
        val user = roleRepository.findByNameIgnoreCase(SecurityRole.USER)
        if (user != null && user.permissions.isEmpty()) {
            user.permissions = USER_PERMISSION_CODES.mapNotNull { allPermissions[it] }.toMutableSet()
            roleRepository.save(user)
        }
        if (user != null) {
            // 后补的普通用户可读权限必须补授，否则存量环境里普通用户看不到自己的进出记录。
            val ensured = allPermissions.filterKeys { it in USER_PERMISSION_CODES }.values
            if (grantMissing(user, ensured)) roleRepository.save(user)
        }
    }

    /**
     * 把缺失的权限补授给角色，返回是否真的有变化。
     *
     * 这里**不能**写成 `ensured.any { role.permissions.add(it) }`：Kotlin 的 `any` 在第一个 true 之后
     * 就短路，同一角色一次缺两个权限（例如同时新增的 gate-person:review 与 gate-person:export）时，
     * 一次启动只补得上一个，另一个要等下次重启，中间这段时间对应的功能一直 403。
     */
    private fun grantMissing(role: Role, permissions: Collection<Permission>): Boolean {
        var changed = false
        permissions.forEach { permission ->
            if (role.permissions.add(permission)) changed = true
        }
        return changed
    }

    private companion object {
        /** 平台管理（ADMIN）角色不授予的治理类权限。 */
        val SUPER_ADMIN_ONLY_PERMISSION_CODES = setOf(
            "role:manage",
            "permission:manage",
            "user:role-assign",
            "audit:delete",
            // 备份产物是整库 SQL 加全部附件（含生物特征照片与口令散列），只留给超级管理员。
            "backup:manage",
        )

        /** 后补进入权限字典的能力，即使 ADMIN 角色已有权限配置也必须补授。 */
        val ADMIN_ENSURED_PERMISSION_CODES = setOf(
            "owner:sync",
            "account:sync",
            "sync-history:read",
            // 同步周期原来由环境变量固定，现在开放到页面上；平台管理本该有此能力，
            // 不补授就是存量环境里的 ADMIN 看得见页面却改不动。
            "sync-schedule:manage",
            // 审核与导出从 gate-person:manage 拆出来，存量环境的 ADMIN 原本靠 manage 隐含拥有这两项能力，
            // 不补授就是一次静默的权限收回。
            "gate-person:review",
            "gate-person:export",
            // 新功能整族补授：平台管理本来就该有，不补授的话存量环境里页面可见却一步都点不动。
            "vehicle-inout-request:read",
            "vehicle-inout-request:apply",
            "vehicle-inout-request:review",
            "vehicle-inout-request:sync",
        )

        /**
         * 部门管理不授予的权限，采用排除法以便新增权限码时自动纳入。
         *
         * device:read 与 position:read 一并排除是刻意的：Device 与 Position 表在数据模型上
         * 没有任何部门关联字段，授予只读只会得到一个要么全量泄露、要么恒为空的接口。
         */
        val DEPT_ADMIN_DENIED_PERMISSION_CODES = setOf(
            "role:manage",
            "permission:manage",
            "user:role-assign",
            "user:disable",
            "department:manage",
            "audit:read",
            "audit:export",
            "audit:verify",
            "audit:delete",
            "system-monitor:read",
            "device:read",
            "device:manage",
            "position:read",
            "dictionary:manage",
            // 备份是超级管理员的专属能力，部门管理不得染指。
            "backup:manage",
            // 同步周期是全局调度配置，部门管理不该改。
            "sync-schedule:manage",
        )

        /** 后补进入权限字典、需要补授给已有部门管理角色的能力。 */
        val DEPT_ADMIN_ENSURED_PERMISSION_CODES = setOf(
            "vehicle-record:read",
            "person-record:read",
            "owner:sync",
            "account:sync",
            "sync-history:read",
            // 与 ADMIN 同理：拆分前部门管理靠 gate-person:manage 就能审核和导出，拆分后必须补授，
            // 否则存量环境的部门管理会突然失去审核与导出能力。
            "gate-person:review",
            "gate-person:export",
            // 车辆进出申请登记在部门维度运营：部门管理需要看、登记、审核与下发自己部门车牌的申请。
            "vehicle-inout-request:read",
            "vehicle-inout-request:apply",
            "vehicle-inout-request:review",
            "vehicle-inout-request:sync",
        )

        /**
         * 普通用户的自助读取能力。
         *
         * 只给"查看"类权限，且这些接口都会按本人范围过滤（普通用户看到的是自己名下的车牌与
         * 自己的门禁身份），因此不构成越权。管理类权限一律不给。
         */
        val USER_PERMISSION_CODES = setOf(
            "dashboard:read",
            "vehicle-record:read",
            "person-record:read",
        )
    }
}
