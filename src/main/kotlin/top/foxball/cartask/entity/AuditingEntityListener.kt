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
    fun onPrePersist(entity: Any) {
        val now = LocalDateTime.now()
        setIfPresent(entity, "createdAt", now, onlyWhenNull = true)
        setIfPresent(entity, "updatedAt", now, onlyWhenNull = true)
        setIfPresent(entity, "updateTime", now, onlyWhenNull = true)
    }

    @PreUpdate
    fun onPreUpdate(entity: Any) {
        val now = LocalDateTime.now()
        setIfPresent(entity, "updatedAt", now, onlyWhenNull = false)
        setIfPresent(entity, "updateTime", now, onlyWhenNull = false)
    }

    private fun setIfPresent(entity: Any, propertyName: String, value: LocalDateTime, onlyWhenNull: Boolean) {
        val field = findField(entity.javaClass, propertyName) ?: return
        field.isAccessible = true
        if (!onlyWhenNull || field.get(entity) == null) {
            field.set(entity, value)
        }
    }

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
