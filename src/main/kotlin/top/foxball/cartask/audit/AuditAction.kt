package top.foxball.cartask.audit

/**
 * AuditAction 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import top.foxball.cartask.entity.AuditEvent


/**
 * AuditAction 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
enum class AuditAction(
    val code: String,
    val category: AuditEvent.Category,
    val riskLevel: AuditEvent.RiskLevel,
) {
    AUTH_LOGIN_SUCCEEDED("AUTH_LOGIN_SUCCEEDED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.MEDIUM),
    AUTH_LOGIN_FAILED("AUTH_LOGIN_FAILED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.MEDIUM),
    AUTH_LOGOUT("AUTH_LOGOUT", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.HIGH),
    AUTH_PASSWORD_CHANGED("AUTH_PASSWORD_CHANGED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.HIGH),
    
    
    AUTH_PHONE_CHANGED("AUTH_PHONE_CHANGED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.HIGH),
    PROFILE_UPDATED("PROFILE_UPDATED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.MEDIUM),
    AUTHORIZATION_DENIED("AUTHORIZATION_DENIED", AuditEvent.Category.AUTHORIZATION, AuditEvent.RiskLevel.MEDIUM),
    USER_CREATED("USER_CREATED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.HIGH),
    USER_UPDATED("USER_UPDATED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.MEDIUM),
    USER_ROLE_ASSIGNED("USER_ROLE_ASSIGNED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.CRITICAL),
    USER_STATUS_CHANGED("USER_STATUS_CHANGED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.HIGH),
    USER_DELETED("USER_DELETED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.CRITICAL),
    ROLE_CHANGED("ROLE_CHANGED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.CRITICAL),
    PERMISSION_CHANGED("PERMISSION_CHANGED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.CRITICAL),
    
    
    SYNC_SCHEDULE_CHANGED("SYNC_SCHEDULE_CHANGED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_CREATED("ACCESS_CONTROL_CREATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_UPDATED("ACCESS_CONTROL_UPDATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_REVIEWED("ACCESS_CONTROL_REVIEWED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_SYNCED("ACCESS_CONTROL_SYNCED", AuditEvent.Category.DEVICE, AuditEvent.RiskLevel.HIGH),
    GATE_PERSON_CREATED("GATE_PERSON_CREATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    GATE_PERSON_UPDATED("GATE_PERSON_UPDATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    GATE_PERSON_DELETED("GATE_PERSON_DELETED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.CRITICAL),
    GATE_PERSON_REVIEWED("GATE_PERSON_REVIEWED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    GATE_DELETE_REQUESTED("GATE_DELETE_REQUESTED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.MEDIUM),
    GATE_DELETE_REQUEST_REVIEWED(
        "GATE_DELETE_REQUEST_REVIEWED",
        AuditEvent.Category.ACCESS_CONTROL,
        AuditEvent.RiskLevel.HIGH
    ),
    
    
    VEHICLE_INOUT_REQUEST_CREATED(
        "VEHICLE_INOUT_REQUEST_CREATED",
        AuditEvent.Category.ACCESS_CONTROL,
        AuditEvent.RiskLevel.HIGH
    ),
    VEHICLE_INOUT_REQUEST_UPDATED(
        "VEHICLE_INOUT_REQUEST_UPDATED",
        AuditEvent.Category.ACCESS_CONTROL,
        AuditEvent.RiskLevel.HIGH
    ),
    VEHICLE_INOUT_REQUEST_REVIEWED(
        "VEHICLE_INOUT_REQUEST_REVIEWED",
        AuditEvent.Category.ACCESS_CONTROL,
        AuditEvent.RiskLevel.HIGH
    ),
    VEHICLE_INOUT_REQUEST_CANCELLED(
        "VEHICLE_INOUT_REQUEST_CANCELLED",
        AuditEvent.Category.ACCESS_CONTROL,
        AuditEvent.RiskLevel.MEDIUM
    ),
    
    
    VEHICLE_INOUT_REQUEST_SYNCED("VEHICLE_INOUT_REQUEST_SYNCED", AuditEvent.Category.DEVICE, AuditEvent.RiskLevel.HIGH),
    ACCESS_RECORD_CORRECTED(
        "ACCESS_RECORD_CORRECTED",
        AuditEvent.Category.ACCESS_RECORD,
        AuditEvent.RiskLevel.CRITICAL
    ),
    ACCESS_RECORD_RELEASED("ACCESS_RECORD_RELEASED", AuditEvent.Category.ACCESS_RECORD, AuditEvent.RiskLevel.CRITICAL),
    FILE_UPLOADED("FILE_UPLOADED", AuditEvent.Category.FILE, AuditEvent.RiskLevel.MEDIUM),
    FILE_DOWNLOADED("FILE_DOWNLOADED", AuditEvent.Category.FILE, AuditEvent.RiskLevel.HIGH),
    SENSITIVE_DATA_EXPORTED("SENSITIVE_DATA_EXPORTED", AuditEvent.Category.DATA_EXPORT, AuditEvent.RiskLevel.HIGH),
    
    
    DATA_BACKUP_CREATED("DATA_BACKUP_CREATED", AuditEvent.Category.DATA_EXPORT, AuditEvent.RiskLevel.CRITICAL),
    LOGS_CLEARED("LOGS_CLEARED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.CRITICAL),
}


