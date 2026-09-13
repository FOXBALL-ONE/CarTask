package top.foxball.cartask.audit

import top.foxball.cartask.entity.AuditEvent

/** 审计动作的稳定代码字典；权限编码由 Permission 独立维护。 */
enum class AuditAction(
    val code: String,
    val category: AuditEvent.Category,
    val riskLevel: AuditEvent.RiskLevel,
) {
    AUTH_LOGIN_SUCCEEDED("AUTH_LOGIN_SUCCEEDED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.MEDIUM),
    AUTH_LOGIN_FAILED("AUTH_LOGIN_FAILED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.MEDIUM),
    AUTH_LOGOUT("AUTH_LOGOUT", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.HIGH),
    AUTH_PASSWORD_CHANGED("AUTH_PASSWORD_CHANGED", AuditEvent.Category.AUTHENTICATION, AuditEvent.RiskLevel.HIGH),
    PROFILE_UPDATED("PROFILE_UPDATED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.MEDIUM),
    AUTHORIZATION_DENIED("AUTHORIZATION_DENIED", AuditEvent.Category.AUTHORIZATION, AuditEvent.RiskLevel.MEDIUM),
    USER_CREATED("USER_CREATED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.HIGH),
    USER_UPDATED("USER_UPDATED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.MEDIUM),
    USER_ROLE_ASSIGNED("USER_ROLE_ASSIGNED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.CRITICAL),
    USER_STATUS_CHANGED("USER_STATUS_CHANGED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.HIGH),
    USER_DELETED("USER_DELETED", AuditEvent.Category.ACCOUNT, AuditEvent.RiskLevel.CRITICAL),
    ROLE_CHANGED("ROLE_CHANGED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.CRITICAL),
    PERMISSION_CHANGED("PERMISSION_CHANGED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.CRITICAL),
    ACCESS_CONTROL_CREATED("ACCESS_CONTROL_CREATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_UPDATED("ACCESS_CONTROL_UPDATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_REVIEWED("ACCESS_CONTROL_REVIEWED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_CONTROL_SYNCED("ACCESS_CONTROL_SYNCED", AuditEvent.Category.DEVICE, AuditEvent.RiskLevel.HIGH),
    GATE_PERSON_CREATED("GATE_PERSON_CREATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    GATE_PERSON_UPDATED("GATE_PERSON_UPDATED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    GATE_PERSON_DELETED("GATE_PERSON_DELETED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.CRITICAL),
    GATE_PERSON_REVIEWED("GATE_PERSON_REVIEWED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    GATE_DELETE_REQUESTED("GATE_DELETE_REQUESTED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.MEDIUM),
    GATE_DELETE_REQUEST_REVIEWED("GATE_DELETE_REQUEST_REVIEWED", AuditEvent.Category.ACCESS_CONTROL, AuditEvent.RiskLevel.HIGH),
    ACCESS_RECORD_CORRECTED("ACCESS_RECORD_CORRECTED", AuditEvent.Category.ACCESS_RECORD, AuditEvent.RiskLevel.CRITICAL),
    ACCESS_RECORD_RELEASED("ACCESS_RECORD_RELEASED", AuditEvent.Category.ACCESS_RECORD, AuditEvent.RiskLevel.CRITICAL),
    FILE_UPLOADED("FILE_UPLOADED", AuditEvent.Category.FILE, AuditEvent.RiskLevel.MEDIUM),
    FILE_DOWNLOADED("FILE_DOWNLOADED", AuditEvent.Category.FILE, AuditEvent.RiskLevel.HIGH),
    SENSITIVE_DATA_EXPORTED("SENSITIVE_DATA_EXPORTED", AuditEvent.Category.DATA_EXPORT, AuditEvent.RiskLevel.HIGH),
    /** 导出整库 SQL 与附件压缩包。比普通导出更敏感：产物里有全部账号口令散列与生物特征照片。 */
    DATA_BACKUP_CREATED("DATA_BACKUP_CREATED", AuditEvent.Category.DATA_EXPORT, AuditEvent.RiskLevel.CRITICAL),
    LOGS_CLEARED("LOGS_CLEARED", AuditEvent.Category.CONFIGURATION, AuditEvent.RiskLevel.CRITICAL),
}
