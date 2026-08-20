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
    ACCESS_RECORD_CORRECTED("ACCESS_RECORD_CORRECTED", AuditEvent.Category.ACCESS_RECORD, AuditEvent.RiskLevel.CRITICAL),
    ACCESS_RECORD_RELEASED("ACCESS_RECORD_RELEASED", AuditEvent.Category.ACCESS_RECORD, AuditEvent.RiskLevel.CRITICAL),
    FILE_UPLOADED("FILE_UPLOADED", AuditEvent.Category.FILE, AuditEvent.RiskLevel.MEDIUM),
    FILE_DOWNLOADED("FILE_DOWNLOADED", AuditEvent.Category.FILE, AuditEvent.RiskLevel.HIGH),
    SENSITIVE_DATA_EXPORTED("SENSITIVE_DATA_EXPORTED", AuditEvent.Category.DATA_EXPORT, AuditEvent.RiskLevel.HIGH),
}
