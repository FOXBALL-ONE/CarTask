package top.foxball.cartask.shared

/**
 * VehicleInspection 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import java.time.LocalDate


/**
 * VehicleInspection 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object VehicleInspection {
    
    
    /**
     * defaultValidUntil 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun defaultValidUntil(inspectedOn: LocalDate): LocalDate = inspectedOn.plusYears(1)
    
    
    /**
     * inspected 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun inspected(inspectionDate: LocalDate?, validUntil: LocalDate?): Boolean =
        inspectionDate != null || validUntil != null
    
    
    /**
     * status 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun status(inspectionDate: LocalDate?, validUntil: LocalDate?, today: LocalDate): String = when {
        !inspected(inspectionDate, validUntil) -> "未年检"
        validUntil != null && validUntil.isBefore(today) -> "已过期"
        else -> "有效"
    }
}


