package top.foxball.cartask.authentication

/** 管理端可配置的稳定权限字典。 */
object PermissionCatalog {
    data class Definition(val code: String, val name: String)

    const val SYSTEM_MONITOR_READ = "system-monitor:read"
    const val LEGACY_SYSTEM_MONITOR_READ = "system:monitor:read"

    val definitions = listOf(
        Definition("dashboard:read", "查看仪表盘"),
        Definition(SYSTEM_MONITOR_READ, "查看系统监控"),
        Definition("user:read", "查看用户"),
        Definition("user:create", "新增用户"),
        Definition("user:update", "编辑用户"),
        Definition("user:disable", "停用或删除用户"),
        Definition("user:role-assign", "分配用户角色"),
        Definition("role:read", "查看角色"),
        Definition("role:manage", "管理角色"),
        Definition("permission:read", "查看权限"),
        Definition("permission:manage", "管理权限"),
        Definition("department:read", "查看部门"),
        Definition("department:manage", "管理部门"),
        Definition("position:read", "查看岗位"),
        Definition("position:manage", "管理岗位"),
        Definition("owner:read", "查看车主"),
        Definition("owner:manage", "管理车主"),
        Definition("owner:sync", "补建车主信息"),
        Definition("spot:read", "查看车位"),
        Definition("spot:manage", "管理车位"),
        Definition("plate:read", "查看车牌"),
        Definition("plate:manage", "管理车牌"),
        // 车辆进出申请登记：登记的是「给这个车牌下发月卡」的申请，审批通过后才会写科拓平台。
        // 登记、审核、下发是三个独立动作：登记的人不该必然能审批，审批的人也不该必然能写外部平台。
        Definition("vehicle-inout-request:read", "查看车辆进出申请"),
        Definition("vehicle-inout-request:apply", "登记车辆进出申请"),
        Definition("vehicle-inout-request:review", "审核车辆进出申请"),
        Definition("vehicle-inout-request:sync", "下发车辆进出申请到科拓"),
        Definition("device:read", "查看设备"),
        Definition("device:manage", "管理设备"),
        Definition("gate-person:read", "查看门禁人员"),
        Definition("gate-person:manage", "管理门禁人员"),
        Definition("gate-person:review", "审核门禁人员"),
        Definition("gate-person:export", "导出门禁人员"),
        Definition("person-record:read", "查看人员进出记录"),
        Definition("person-record:export", "导出人员进出记录"),
        Definition("vehicle-record:read", "查看车辆进出记录"),
        Definition("vehicle-record:export", "导出车辆进出记录"),
        Definition("vehicle-record:sync", "同步车辆进出记录"),
        Definition("violation:read", "查看违规管理"),
        Definition("violation:manage", "管理违规记录与规则"),
        Definition("violation:export", "导出违规记录"),
        Definition("audit:read", "查看审计日志"),
        Definition("audit:export", "导出审计日志"),
        Definition("audit:verify", "校验审计日志"),
        Definition("audit:delete", "清空管理端日志"),
        Definition("access-control:read", "查看门禁授权"),
        Definition("access-control:apply", "申请门禁授权"),
        Definition("access-control:update", "编辑门禁授权"),
        Definition("access-control:review", "审核门禁授权"),
        Definition("access-control:sync", "同步门禁授权"),
        Definition("access-record:read", "查看通行记录"),
        Definition("access-record:correct", "修正通行记录"),
        Definition("access-record:release", "人工放行"),
        Definition("vehicle:read", "查看车辆"),
        Definition("vehicle:create", "新增车辆"),
        Definition("vehicle:update", "编辑车辆"),
        Definition("vehicle:delete", "删除车辆"),
        Definition("dictionary:read", "查看字典"),
        Definition("dictionary:manage", "管理字典"),
        Definition("dictionary:sync", "同步停车区域字典"),
        Definition("account:sync", "生成车辆业主账号"),
        Definition("sync-history:read", "查看同步执行历史"),
        // 周期是全局调度配置，改坏了会静默停止数据拉取，因此不给部门管理。
        Definition("sync-schedule:manage", "修改同步任务周期"),
        Definition("file:read", "读取文件"),
        Definition("file:upload", "上传文件"),
        // 只授给超级管理员：产物里是整库数据加全部附件，平台管理与部门管理都不该拿到。
        Definition("backup:manage", "数据备份"),
    )
}
