package top.foxball.cartask.entity

import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.lang.reflect.Field
import java.time.LocalDateTime


class AuditingEntityListener {
    @PrePersist
    
    
    fun onPrePersist(entity: Any) {
        val now = LocalDateTime.now()
        setIfPresent(entity, "createdAt", now, onlyWhenNull = true)
        setIfPresent(entity, "createTime", now, onlyWhenNull = true)
        setIfPresent(entity, "applyTime", now, onlyWhenNull = true)
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
