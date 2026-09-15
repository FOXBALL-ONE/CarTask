package top.foxball.cartask.entity

import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * 为实体统一维护创建和更新时间。
 *
 * 实体仍可在业务代码中显式赋值；创建回调只填充尚未设置的时间，更新时间回调始终使用当前本地时间。
 * 这样既兼容文件服务等已有赋值路径，也避免通用 CRUD 写入非空时间列时遗漏审计字段。
 */
class AuditingEntityListener {
    @PrePersist
            /**
             * onPrePersist：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param entity 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onPrePersist(entity: Any) {
        val now = LocalDateTime.now()
        setIfPresent(entity, "createdAt", now, onlyWhenNull = true)
        setIfPresent(entity, "createTime", now, onlyWhenNull = true)
        setIfPresent(entity, "applyTime", now, onlyWhenNull = true)
        setIfPresent(entity, "updatedAt", now, onlyWhenNull = true)
        setIfPresent(entity, "updateTime", now, onlyWhenNull = true)
    }

    @PreUpdate
            /**
             * onPreUpdate：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param entity 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun onPreUpdate(entity: Any) {
        val now = LocalDateTime.now()
        setIfPresent(entity, "updatedAt", now, onlyWhenNull = false)
        setIfPresent(entity, "updateTime", now, onlyWhenNull = false)
    }

    /**
     * setIfPresent：创建、保存或初始化相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @param propertyName 参与本次处理的输入参数。
     * @param value 参与本次处理的输入参数。
     * @param onlyWhenNull 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun setIfPresent(entity: Any, propertyName: String, value: LocalDateTime, onlyWhenNull: Boolean) {
        val field = findField(entity.javaClass, propertyName) ?: return
        field.isAccessible = true
        if (!onlyWhenNull || field.get(entity) == null) {
            field.set(entity, value)
        }
    }

    /**
     * findField：查询或读取相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param type 参与本次处理的输入参数。
     * @param propertyName 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun findField(type: Class<*>, propertyName: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(propertyName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
