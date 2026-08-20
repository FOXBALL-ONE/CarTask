# 车辆进出管理系统 RBAC 角色权限方案

## 1. 目标与范围

本方案为车辆进出管理系统定义三类登录用户及其系统权限。

文档中的角色模型分为两种状态：

| 状态 | 角色集合 | 说明 |
| --- | --- | --- |
| 历史兼容状态 | ADMIN、USER、CUSTOMER | 设计实施前的历史白名单。CUSTOMER 属于历史兼容角色，不是本方案的目标角色。 |
| 目标状态 | SUPER_ADMIN、ADMIN、USER | 本文档最终采用的三类角色。切换前必须完成账号映射、接口规则调整和会话撤销。 |

| 角色编码 | 角色名称 | 主要职责 |
| --- | --- | --- |
| SUPER_ADMIN | 超级管理员 | 管理系统身份、角色、权限、全局配置和高风险操作。 |
| ADMIN | 管理员 | 负责车辆、门禁、设备、进出流水和组织基础资料的日常运营。 |
| USER | 普通用户 | 提交本人车辆和门禁申请，查询本人可见的申请、车辆和进出记录。 |

本方案覆盖现有系统的用户、角色、权限、部门、职位、车辆主档、门禁授权、进出流水、设备、文件和基础字典模块。

> 系统 RBAC 决定谁可以调用哪个功能；门禁授权记录决定某人员或车辆是否可以在特定时间、区域通行。两者必须分开建模和校验，不能以系统登录角色替代门禁通行授权。

## 2. 设计原则

1. 最小权限：角色只获得完成工作所需的权限。
2. 职责分离：超级管理员负责授权治理，管理员负责业务运营，普通用户不直接操作全局业务数据。
3. 默认拒绝：未配置权限的接口返回 403；不依赖前端菜单隐藏实现安全控制。
4. 数据范围优先：功能权限通过 Spring Security authority 判断，数据范围在 Service 和 Repository 查询、更新时继续限制。
5. 高风险可追溯：角色、权限、账号状态、门禁审批、人工放行、设备配置和数据删除必须保留审计记录。
6. 禁止权限自授：管理员不能创建或提升超级管理员，不能修改权限字典，不能为自己或他人授予越权权限。
7. 权限分层：角色 authority 表示身份级边界，业务 permission authority 表示功能能力，数据范围决定可操作的数据集合。

## 3. 业务模块

| 权限域 | 现有模块/接口 | 说明 |
| --- | --- | --- |
| 系统授权 | 用户、角色、权限 | 账号生命周期、角色定义、权限字典与会话撤销。 |
| 组织资料 | 部门、职位 | 组织树、岗位及人员归属。 |
| 车辆资料 | 车辆主档、车辆类型、车牌类型 | 车主/联系人、通行卡、停车位和车辆基础分类。 |
| 门禁授权 | 门禁授权、门禁授权类型 | 人员授权时段、门禁范围、审核状态及同步状态。 |
| 进出运营 | 车辆进出流水、放行类型、限制类型 | 进出方向、时间、放行渠道、人工说明与异常处理。 |
| 设备与区域 | 设备、区域类型 | 门禁、摄像头、传感器、停车区域和外部平台区域编码。 |
| 文件资料 | 文件上传、文件元数据、文件下载 | 申请附件、车辆资料和人工放行凭证。 |

## 4. 角色定义

### 4.1 超级管理员（SUPER_ADMIN）

超级管理员是系统安全责任人，负责全局治理而非日常值守。建议限定少量实名账号，至少保留两个互为备份的账号。

拥有权限：

- 管理所有用户、角色、权限及会话撤销。
- 管理所有组织、车辆、门禁、设备、进出流水和基础字典。
- 管理管理员账号，但不得删除或禁用最后一个启用的超级管理员。
- 查看全局业务数据、导出数据和审计日志。
- 处理高风险配置、外部平台同步配置和紧急故障处置。

限制：

- 关键操作必须记录操作者、目标对象、变更前后摘要、时间、来源 IP 和请求标识。
- 不允许使用共享账号；生产环境不应把 SUPER_ADMIN 用作日常运营账号。
- SUPER_ADMIN 已纳入目标 SecurityRole 白名单；上线前仍必须完成账号映射、权限配置和旧会话撤销。

### 4.2 管理员（ADMIN）

管理员负责园区、停车场或部门范围内的业务运营，可由一个或多个运营人员承担。

拥有权限：

- 管理普通用户账号；创建账号时只能使用固定的 USER 角色，不能执行角色分配，不能创建、提升、禁用或删除 ADMIN、SUPER_ADMIN。
- 维护本数据范围内的组织资料、车辆主档、门禁授权、设备和基础字典。
- 审核门禁申请、同步授权至设备、处理异常进出和人工放行。
- 查询、导出本数据范围内的进出记录。
- 上传和查看本数据范围内的业务附件。

限制：

- 不管理角色定义和权限字典。
- 不得修改超级管理员、其他管理员的角色或账号状态。
- 不得删除进出流水，应通过更正或作废保留原始记录。
- 不得绕过门禁授权审核直接生成长期有效授权。
- 设备同步、人工放行、流水更正属于高风险操作，建议作为管理员的按需能力授权，不作为每个 ADMIN 账号的默认权限。

### 4.3 普通用户（USER）

普通用户是车辆使用人、访客申请人或一般业务人员。

拥有权限：

- 查看和维护本人车辆资料、通行卡资料与申请附件。
- 新建、查看、撤回本人门禁申请。
- 查看本人车辆和本人授权范围内的进出流水。
- 下载本人有权访问的附件。
- 查看本人账号基础信息并修改允许自助修改的资料。

限制：

- 无权查看其他用户、其他部门或全局车辆、门禁、进出流水。
- 无权审批申请、修改审核结果、人工放行或同步设备。
- 无权管理设备、组织、基础字典、角色和权限。
- 无权自行变更系统角色、部门、职位、账号启停状态。

## 5. 权限编码规范

权限代码使用全小写的 资源:动作 格式，例如：

    access-control:review
    access-record:export
    device:manage

建议动作定义：

| 动作 | 含义 |
| --- | --- |
| read | 查询详情、列表或统计。 |
| create | 创建业务数据。 |
| update | 更新既有业务数据。 |
| delete | 删除可安全删除的基础资料。 |
| manage | 一个低风险模块的完整维护权限；不用于角色、权限、审批和设备同步等高风险操作。 |
| apply | 提交或撤回本人申请。 |
| review | 审批、驳回或撤销审批。 |
| correct | 更正业务流水，必须保留原始记录。 |
| release | 人工或远程放行。 |
| sync | 下发、重试或查看外部设备同步。 |
| export | 导出查询结果。 |
| revoke | 撤销会话、授权或高风险状态。 |

权限代码不支持通配符。角色获得某项能力时，必须显式关联对应的权限记录。

权限命名只表达功能，不表达数据范围。不要把 vehicle:read:department、vehicle:read:self 这类范围混入权限代码；范围应由当前用户、部门、资源归属和业务状态共同计算。

### 5.1 权限元数据

Permission.code、Permission.name、Permission.description 和 Permission.enabled 是当前模型已有的基础字段。为了支持后台授权、风险提示和审计，目标模型还应为每条权限维护以下元数据：

| 元数据 | 建议值 | 用途 |
| --- | --- | --- |
| domain | USER、VEHICLE、ACCESS_CONTROL、ACCESS_RECORD、DEVICE、DICTIONARY、FILE、SECURITY | 权限所属业务域。 |
| risk_level | LOW、MEDIUM、HIGH、CRITICAL | 控制二次确认、审批和告警策略。 |
| scope_policy | GLOBAL、ADMIN_SCOPE、OWNER、APPLICANT、RESOURCE_SCOPE | 指定权限默认需要哪种数据范围。 |
| audit_required | true/false | 是否必须记录审计事件；高风险权限必须为 true。 |
| destructive | true/false | 是否可能删除、撤销、覆盖或改变通行状态。 |
| description | 稳定的业务说明 | 向管理员解释权限效果，不作为鉴权依据。 |

元数据不能替代 Spring Security authority。鉴权仍以 code 为准，元数据用于后台展示、授权审批、审计和策略校验。当前 Permission 实体尚未包含这些字段，落地时应先确定字段和兼容策略，再进行数据结构变更。

建议风险分级：查询为 LOW，普通资料维护为 MEDIUM，门禁审核、设备同步、导出和人工放行为 HIGH，角色/权限管理、密钥配置和全局会话撤销为 CRITICAL。

## 6. 建议权限目录与角色矩阵

表中的“全局”表示全部数据范围；“范围内”表示管理员被授予的园区或部门范围；“本人”表示当前登录用户拥有或发起的数据。

| 权限代码 | 功能说明 | 超级管理员 | 管理员 | 普通用户 |
| --- | --- | :---: | :---: | :---: |
| user:read | 查询用户 | 全局 | 范围内普通用户 | - |
| user:create | 创建用户 | 是 | 仅 USER | - |
| user:update | 更新用户资料 | 全局 | 范围内普通用户 | - |
| user:disable | 启用、停用或封禁用户 | 全局 | 范围内普通用户，不能操作 ADMIN/SUPER_ADMIN | - |
| user:role-assign | 为用户分配角色 | 是 | - | - |
| role:read | 查询角色 | 是 | 仅查看，不可修改 | - |
| role:manage | 创建、修改、删除角色及关联权限 | 是 | - | - |
| permission:read | 查询权限字典 | 是 | 是 | - |
| permission:manage | 维护权限字典 | 是 | - | - |
| session:revoke | 强制用户重新登录或撤销会话 | 全局 | 范围内普通用户 | 本人退出 |
| department:read | 查询部门树 | 全局 | 范围内 | 本人可见范围 |
| department:manage | 维护部门 | 是 | 范围内 | - |
| position:read | 查询职位 | 全局 | 范围内 | 本人可见范围 |
| position:manage | 维护职位 | 是 | 范围内 | - |
| vehicle:read | 查询车辆主档 | 全局 | 范围内 | 本人 |
| vehicle:create | 新建车辆主档 | 全局 | 范围内 | 本人申请 |
| vehicle:update | 更新车辆主档、通行卡、停车位 | 全局 | 范围内 | 本人待审核资料；已审核资料需重新审核 |
| vehicle:delete | 删除车辆主档 | 是 | 范围内且满足留存规则 | - |
| access-control:read | 查询门禁授权 | 全局 | 范围内 | 本人 |
| access-control:apply | 提交、撤回本人门禁申请 | 是 | 可代录范围内申请 | 本人 |
| access-control:review | 审批、驳回、撤销审批 | 是 | 范围内 | - |
| access-control:update | 修改已审批授权的有效期、门禁范围 | 是 | 范围内，修改后重新审核 | - |
| access-control:sync | 下发、重试或查看设备同步 | 是 | 范围内 | - |
| access-record:read | 查询进出流水 | 全局 | 范围内 | 本人车辆 |
| access-record:correct | 更正进出流水 | 是 | 范围内 | - |
| access-record:export | 导出进出流水 | 是 | 范围内 | - |
| access-record:release | 人工或远程放行并留痕 | 是 | 范围内 | - |
| device:read | 查询设备状态 | 全局 | 范围内 | - |
| device:manage | 维护设备资料、启停状态 | 是 | 范围内 | - |
| device:sync | 向设备或外部平台下发配置 | 是 | 范围内 | - |
| dictionary:read | 查询车辆、车牌、门禁、放行、限制、区域字典 | 全局 | 范围内 | 申请所需数据 |
| dictionary:manage | 维护上述基础字典 | 是 | 范围内 | - |
| file:upload | 上传业务附件 | 是 | 范围内 | 本人 |
| file:read | 查看或下载业务附件 | 全局 | 范围内 | 本人 |
| file:delete | 删除业务附件 | 是 | 范围内且未被引用 | 本人未提交且未被业务记录引用的附件 |
| audit:read | 查询安全与业务审计日志 | 全局 | 范围内业务日志 | - |

以下权限仅建议授予 SUPER_ADMIN：

    role:manage
    permission:manage
    audit:read
    security:configure
    integration:configure

session:revoke 采用范围化授权：SUPER_ADMIN 可全局撤销会话；ADMIN 仅可撤销授权范围内的普通用户会话，不能撤销 ADMIN 或 SUPER_ADMIN 会话。

security:configure 和 integration:configure 对应未来的安全策略、JWT/Redis 密钥轮换、外部停车或门禁平台连接配置。当前没有对应业务接口时，不应预先暴露接口。

### 6.1 管理员权限分层

ADMIN 只表示管理员身份，不等于自动拥有所有业务权限。建议建立以下两个配置档：

| 配置档 | 默认用途 | 建议权限 |
| --- | --- | --- |
| ADMIN_OPERATIONS | 日常查询、资料维护和申请审核 | 用户普通账号管理、组织资料、车辆资料、门禁申请/审核、设备查询、字典查询、文件管理。 |
| ADMIN_DUTY | 值班或调度岗位 | 在 ADMIN_OPERATIONS 基础上按需增加 access-record:release、access-record:correct、access-control:sync、device:sync。 |

access-record:release、access-record:correct、access-control:sync 和 device:sync 应单独审批授权、单独审计，不能仅因为用户具有 ROLE_ADMIN 就放行。

### 6.2 角色继承与权限授予边界

本系统采用显式权限关联，不在数据库中实现隐式角色继承。为了便于理解，业务上可以把角色视为以下能力集合：

| 角色 | 能力集合 | 是否允许被普通管理员授予 |
| --- | --- | --- |
| SUPER_ADMIN | 全局系统治理和全部业务能力 | 否。只能由现有 SUPER_ADMIN 按安全流程授予。 |
| ADMIN | 受数据范围约束的运营能力 | 仅 SUPER_ADMIN 可授予。 |
| USER | 本人自助能力 | ADMIN 可按固定 USER 模板创建账号；角色变更仍由 SUPER_ADMIN 执行。 |

角色分配必须同时检查四个条件：操作者拥有 user:role-assign；目标角色不高于操作者可授予的最高角色；目标用户属于操作者可管理的数据范围；变更已写入审计记录并按要求撤销旧会话。按照本方案，只有 SUPER_ADMIN 默认拥有 user:role-assign。

## 7. Spring Security 落地

### 7.1 标准表达式

方法级授权使用 Spring Security 的 PreAuthorize：

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun managePermissions() = ...

    @PreAuthorize("hasAuthority('access-control:review')")
    fun reviewAccessControl() = ...

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('device:manage')")
    fun updateDevice() = ...

登录认证成功后，Authentication.authorities 必须同时包含角色和权限，例如：

    ROLE_SUPER_ADMIN
    ROLE_ADMIN
    ROLE_USER
    access-control:review
    access-record:read

URL 级规则负责区分公开接口和必须登录的接口；业务 Controller 或 Service 通过 PreAuthorize 负责精确权限判断。不能只依赖 URL 前缀或前端菜单进行权限控制。

### 7.2 当前接口的建议映射

| 接口模块 | 建议权限 |
| --- | --- |
| /api/users | 按方法拆分 user:read、user:create、user:update、user:disable、user:role-assign；管理员创建用户固定为 USER，角色分配和更新必须另行校验目标用户角色边界。 |
| /api/roles | role:read、role:manage。 |
| /api/permissions | permission:read、permission:manage。 |
| /api/departments、/api/positions | department 和 position 对应的显式读写权限。 |
| /api/car-master-infos | vehicle:read、vehicle:create、vehicle:update、vehicle:delete。 |
| /api/access-controls | access-control:read、apply、review、update、sync。审批与同步不应与普通更新共用权限。 |
| /api/access-records | access-record:read、correct、export、release。禁止以通用删除接口处理已形成的进出流水。 |
| /api/devices | device:read、device:manage、device:sync。设备下发必须独立于资料维护。 |
| 类型、限制、区域相关接口 | dictionary:read、dictionary:manage；后续可按字典类型进一步细分。 |
| /api/files | file:upload、file:read、file:delete，并校验文件所属业务对象和数据范围。 |

历史版本曾使用 user:manage、role:manage、permission:manage 作为过渡权限。当前实现已将用户、角色和权限接口拆分为本方案的读、建、改、停用或删除权限；部署时仍需清理历史权限记录。

当前实现中的以下规则只能视为过渡规则：

- UserController 类级规则允许 ROLE_ADMIN 访问整个用户管理控制器；
- RoleController 类级规则允许 ROLE_ADMIN 访问整个角色控制器；
- PermissionController 类级规则允许 ROLE_ADMIN 访问整个权限控制器。

目标状态下，管理员只能拥有对应的 read 或普通用户管理权限，不能因为角色本身访问 role:manage、permission:manage 或越权用户操作。应将权限注解下沉到方法级，并对目标用户角色和目标数据范围再次校验。

## 8. 数据范围与当前模型的实施前提

hasAuthority 只能判断功能权限，不能保证普通用户只能看到自己的数据。必须在数据模型和查询层增加并使用数据归属字段。

| 业务对象 | 建议新增或明确的字段 | 用途 |
| --- | --- | --- |
| 车辆主档 CarMasterInfo | owner_user_id、department_id | 判断本人车辆和部门车辆。 |
| 门禁授权 AccessControl | applicant_user_id、reviewer_user_id、department_id | 限制本人申请、审批人和审批范围。 |
| 进出流水 AccessRecord | car_master_id、device_id、operator_user_id | 追溯车辆、设备和人工放行操作者。 |
| 文件 StoredFile | owner_user_id、业务对象类型、业务对象 ID | 防止通过猜测文件 ID 下载其他人的附件。 |
| 用户 User | 保留 department_id，增加角色变更人和时间审计 | 支持管理员的组织范围限制。 |

数据范围建议：

| 角色 | 默认范围 |
| --- | --- |
| SUPER_ADMIN | 全局。 |
| ADMIN | 指定园区或部门及其下级部门；没有配置范围时默认拒绝业务数据操作。 |
| USER | 本人创建、本人拥有或明确授权给本人的数据。 |

在上述归属字段和查询过滤完成前，普通用户只能访问专门的“我的申请、我的车辆、我的记录”接口；不能直接开放现有通用 CRUD 列表接口。管理员也不能仅依靠 URL 前缀访问全量数据，Repository 查询必须带入授权范围。

### 8.1 数据范围判定顺序

对每次详情、列表、更新、删除、审批和导出操作，按以下顺序判断：

1. 先判断功能权限，例如 access-control:review。
2. 再解析用户数据范围：全局、部门树、指定区域或本人。
3. 查询目标资源及其归属关系；资源不存在和资源不在范围内应使用一致的业务错误语义，避免泄露对象是否存在。
4. 对状态机进行校验，例如只有 PENDING 才能审核，已下发授权不能直接修改。
5. 执行写操作并记录操作者、数据范围、前后值摘要和请求标识。

列表、批量接口和导出接口必须使用同一套范围过滤规则，不能只保护详情接口。

## 9. 关键业务规则

1. 门禁申请、新建、审批和下发由 access-control:apply、access-control:review、access-control:sync 分别控制。
2. 已审批且已下发的门禁授权修改有效期或范围时，应重新进入审核并重新同步，不能直接覆盖。
3. 进出流水属于审计性数据，不允许物理删除；更正时保存原始值、更正原因和操作者。
4. 人工或远程放行必须记录放行渠道、操作者、时间、原因、关联车辆和设备；已有任何放行渠道的流水不允许再次人工放行。
5. 禁用用户、变更角色、变更权限后应撤销该用户 JWT/Redis 会话，使新权限在下一请求生效。
6. 删除字典、设备、车辆或附件前必须检查是否被门禁授权、车辆主档、进出流水或审批记录引用。
7. 超级管理员和管理员的角色变更应采用双人复核或至少审计告警，避免单人提升权限。

### 9.1 门禁授权状态机

现有 AccessControl.reviewStatus 包含 PENDING、APPROVED、REJECTED，synchronized_loading 表示是否已同步到设备。推荐采用以下状态约束：

| 当前状态 | 操作 | 必需权限 | 结果 |
| --- | --- | --- | --- |
| PENDING | 提交申请 | access-control:apply | 申请进入待审核；提交后申请人不可直接修改关键字段。 |
| PENDING | 驳回 | access-control:review | 状态变为 REJECTED，并记录原因。 |
| PENDING | 审批通过 | access-control:review | 状态变为 APPROVED，等待同步。 |
| APPROVED 且未同步 | 修改授权范围/时间 | access-control:update | 修改后回到 PENDING，重新审核。 |
| APPROVED 且未同步 | 下发设备 | access-control:sync | 同步成功后标记已同步；失败必须记录错误并允许重试。 |
| APPROVED 且已同步 | 修改授权范围/时间 | access-control:update | 先撤销或生成设备变更任务，再回到 PENDING，不能直接覆盖有效授权。 |
| REJECTED | 重新申请 | access-control:apply | 生成新申请或显式重新提交版本，保留原驳回记录。 |

任何状态都不允许通过通用 update 接口绕过状态机。同步成功不能只依赖客户端传入 synchronized_loading，必须由服务端根据设备响应写入。

### 9.2 组合鉴权规则

业务写操作必须同时满足以下条件：

    allow = authenticated
        AND has_required_authority
        AND within_data_scope
        AND state_transition_allowed
        AND audit_context_complete

缺少任一条件都应拒绝操作。特别是拥有 access-control:review 不代表可以审核范围外的申请；拥有 access-record:release 不代表可以对任意设备或任意车辆放行。

## 10. 初始角色配置建议

### SUPER_ADMIN

关联本方案全部权限。初始仅创建必要的实名运维账号，避免默认账号和共享账号。

### ADMIN

默认关联以下运营权限：

    user:read
    user:create
    user:update
    user:disable
    role:read
    permission:read
    department:read
    department:manage
    position:read
    position:manage
    vehicle:read
    vehicle:create
    vehicle:update
    vehicle:delete
    access-control:read
    access-control:apply
    access-control:review
    access-control:update
    access-record:read
    access-record:export
    device:read
    device:manage
    dictionary:read
    dictionary:manage
    file:upload
    file:read
    file:delete

实际授予时还必须配置管理员的数据范围，不能因为拥有上述权限就默认拥有全局数据。

ADMIN_DUTY 账号在经过岗位审批后，才额外关联以下高风险权限：

    access-control:sync
    access-record:correct
    access-record:release
    device:sync

高风险权限不应成为 ADMIN 角色的隐含权限；撤销岗位职责时必须同步撤销这些权限和相关会话。

### USER

默认关联以下自助权限：

    vehicle:read
    vehicle:create
    vehicle:update
    access-control:read
    access-control:apply
    access-record:read
    dictionary:read
    file:upload
    file:read
    file:delete

所有普通用户权限均附带“本人”数据范围，不能凭此读取或修改任意车辆和记录。

## 11. 与当前认证模型的衔接

当前 SecurityRole 目标白名单为 SUPER_ADMIN、ADMIN、USER。历史 CUSTOMER 账号在迁移完成前不能继续登录，采用本方案时应：

    setOf("SUPER_ADMIN", "ADMIN", "USER")

同时完成以下工作：

1. 为 SUPER_ADMIN、ADMIN、USER 创建启用的 Role 记录。
2. 将本方案中的权限代码写入 Permission.code。
3. 将权限关联到对应 Role.permissions。
4. 角色变更后撤销受影响用户的 Redis token session 或递增 token version。
5. 在 Controller/Service 上按本方案拆分 PreAuthorize。
6. 完成数据归属字段与 Repository 查询过滤后，再开放普通用户自助接口。

CUSTOMER 若仅为旧系统兼容角色，应先冻结新建和分配，并将现有 hasRole('CUSTOMER') 的业务接口迁移到 USER 或显式业务权限。当前代码已移除该角色；历史账号必须先完成映射或停用。本文档只定义目标权限模型，不包含数据库迁移工作。

## 12. 实施路线

### 阶段一：冻结模型和建立权限字典

1. 确认目标角色只包括 SUPER_ADMIN、ADMIN、USER，登记 CUSTOMER 的兼容账号和使用接口。
2. 建立 Permission 记录，权限 code 必须唯一、稳定、全小写，不因页面名称或接口路径变化而改变。
3. 建立 Role 与 Permission 的关联关系，SUPER_ADMIN 的权限集合由系统配置保护，不允许通过普通管理接口修改。
4. 为每个权限记录补充用途说明、风险等级、是否允许导出和默认数据范围，供前端菜单和审计展示。

### 阶段二：完成认证与会话切换

1. 将 SecurityRole 白名单从当前兼容集合迁移到目标集合，并为旧 CUSTOMER 账号执行明确映射。
2. JWT 认证成功后，把角色 authority 和启用权限 authority 放入 SecurityContext；不从请求参数、请求体或 JWT 外部输入直接追加权限。
3. 角色、权限、账号状态或数据范围变化后，撤销受影响用户的 Redis 会话或递增 token version。
4. 迁移期间不允许旧 JWT 继续携带旧角色执行高风险操作；切换完成后统一要求重新登录。

### 阶段三：按接口拆分方法级授权

1. 公开接口继续由 URL 级规则明确 permitAll；所有业务写接口默认 authenticated。
2. 在 Service 或 Controller 方法上使用 Spring Security 的 PreAuthorize，分别校验角色和权限 authority。
3. 禁止使用一个 manage 权限覆盖角色、审批、设备同步、人工放行和流水更正等不同风险动作。
4. 对用户角色变更、门禁审核、人工放行、设备同步、流水更正和导出接口增加二次业务校验。

### 阶段四：完成数据范围和审计

1. 为车辆、门禁授权、进出流水和文件建立明确的用户、部门、设备或业务对象归属。
2. 为 ADMIN 配置一个或多个部门/区域范围；范围为空时，对业务数据默认拒绝而不是默认全局。
3. 将列表、详情、批量、导出和下载统一接入数据范围过滤。
4. 建立审计记录并配置高风险操作告警，尤其关注角色提升、权限变更、人工放行和批量导出。

### 阶段五：灰度切换和收口

1. 先对只读接口启用细粒度权限，再启用写入、审批、同步和导出权限。
2. 以管理员运营账号进行灰度验证，确认其只能访问授权部门和区域。
3. 完成 CUSTOMER 兼容接口迁移后，关闭旧角色新建和分配。
4. 移除过渡 user:manage 规则，保留权限字典和角色配置变更记录。

## 13. 权限种子清单

以下清单用于初始化权限字典。它是业务配置清单，不是数据库迁移脚本；部署时应使用项目既有的初始化或后台配置机制，并保证重复执行幂等。

    user:read
    user:create
    user:update
    user:disable
    role:read
    role:manage
    permission:read
    permission:manage
    session:revoke
    department:read
    department:manage
    position:read
    position:manage
    vehicle:read
    vehicle:create
    vehicle:update
    vehicle:delete
    access-control:read
    access-control:apply
    access-control:review
    access-control:update
    access-control:sync
    access-record:read
    access-record:correct
    access-record:export
    access-record:release
    device:read
    device:manage
    device:sync
    dictionary:read
    dictionary:manage
    file:upload
    file:read
    file:delete
    audit:read
    security:configure
    integration:configure

权限种子中的 security:configure 和 integration:configure 在没有实际接口前只能保留为受保护配置项，不能关联到普通 ADMIN。

## 14. 测试矩阵

### 14.1 角色与权限

| 场景 | 预期结果 |
| --- | --- |
| 未登录访问受保护接口 | 401，不进入业务方法。 |
| USER 调用 admin-only 方法 | 403。 |
| ADMIN 没有 access-record:release | 403，不能人工放行。 |
| ADMIN 具备 access-record:release 但资源不在部门范围 | 403 或统一的不可访问结果。 |
| SUPER_ADMIN 调用角色/权限管理 | 允许，并写审计记录。 |
| ADMIN 尝试授予 SUPER_ADMIN | 拒绝，不修改数据。 |
| 权限被禁用后使用旧 token | 下一请求不再拥有该 authority，按策略返回 403 或要求重新认证。 |

### 14.2 数据范围

| 场景 | 预期结果 |
| --- | --- |
| USER 查询车辆列表 | 只返回本人车辆。 |
| USER 猜测其他车辆 ID | 不能读取或修改。 |
| ADMIN 查询部门列表 | 只返回本人授权部门及下级范围。 |
| ADMIN 批量更新跨部门资源 | 整体拒绝或只允许明确的原子子集，不能静默越权。 |
| 导出进出流水 | 同时校验 access-record:export 和数据范围。 |
| 下载附件 | 同时校验 file:read 和附件所属业务对象范围。 |

### 14.3 业务状态和审计

| 场景 | 预期结果 |
| --- | --- |
| 普通用户撤回已提交申请 | 允许；已审核或已同步申请按状态拒绝。 |
| 管理员审核已审核申请 | 拒绝重复审核，并记录原因。 |
| 修改已同步门禁授权 | 进入重新审核和重新同步流程。 |
| 人工放行缺少原因或车辆关联 | 拒绝，不产生放行记录。 |
| 删除已有进出流水 | 接口不存在或返回拒绝；只能走更正流程。 |
| 角色/权限变更 | 写审计并撤销受影响会话。 |

## 15. 运维与安全风险

| 风险 | 后果 | 控制措施 |
| --- | --- | --- |
| 将 ADMIN 当作全能角色 | 运营人员可修改权限或越权查看数据 | 使用显式 permission authority 和数据范围，禁止仅用 hasRole 放行全部管理接口。 |
| 只保护详情接口 | 列表、批量或导出泄露数据 | 所有查询入口统一使用范围过滤。 |
| 权限变更后旧 token 仍有效 | 用户继续使用旧能力 | 递增 token version 或撤销会话，并测试并发边界。 |
| 人工放行无关联信息 | 无法追责或复盘 | 强制原因、车辆、设备、操作者和时间字段。 |
| 权限代码随接口重命名 | 历史角色权限失效或误授 | 权限代码作为稳定业务标识，接口路径只是映射。 |
| 通过前端隐藏按钮控制权限 | 可直接构造请求绕过 | 后端 URL 和方法级授权双重保护。 |
| 角色接口允许自我提升 | 形成权限扩大路径 | 校验操作者可授予角色上限，并禁止操作最后超级管理员。 |
| 文件只按 ID 下载 | 附件越权泄露 | 检查文件业务归属、权限和数据范围。 |

## 16. 前端与管理后台约束

前端只能根据后端返回的角色和权限改善交互，不能承担安全决策。建议提供当前用户信息接口，返回用户 ID、角色、权限代码和允许展示的数据范围摘要，但不返回角色关联表、内部审计配置或其他用户权限。

| 前端场景 | 行为要求 |
| --- | --- |
| 菜单和路由 | 没有相应权限时不展示或禁止进入，但后端仍必须独立校验。 |
| 操作按钮 | 审核、同步、人工放行、删除、导出等按钮根据 authority 和资源状态共同展示。 |
| 403 响应 | 显示无权限，不自动改写请求或退化为其他操作。 |
| 401 响应 | 清理本地认证状态并引导重新登录，不能无限重试。 |
| 角色编辑 | ADMIN 不显示角色分配控件；SUPER_ADMIN 也不能删除最后一个超级管理员。 |
| 数据范围 | 前端过滤只用于体验，后端返回结果必须已经完成范围过滤。 |
| 权限变更 | 用户重新登录或会话被撤销后刷新菜单和按钮，不长期缓存旧权限。 |

管理后台的角色编辑页面应按业务域分组权限，并显著标识 HIGH、CRITICAL、destructive 权限。批量勾选不能默认包含高风险权限，高风险权限变更应要求二次确认和填写原因。

## 17. 审计事件规范

建议对以下事件写入结构化审计日志：登录失败与会话撤销、账号启停、角色分配、角色权限变更、门禁审核、设备同步、人工放行、流水更正、敏感导出和文件删除。

| 字段 | 说明 |
| --- | --- |
| event_id | 审计事件唯一标识。 |
| occurred_at | ISO-8601 LocalDateTime 业务时间。 |
| request_id | 请求链路标识，用于关联 API 和下游设备调用。 |
| actor_user_id / actor_role | 操作者身份快照。 |
| authorities | 本次决策使用的关键权限摘要；不得记录 JWT。 |
| action | 稳定操作代码，例如 ACCESS_CONTROL_REVIEW。 |
| target_type / target_id | 被操作资源类型和标识。 |
| scope | 本次授权使用的数据范围摘要。 |
| result | SUCCESS、DENIED、FAILED。 |
| reason | 审批、驳回、更正、放行或失败原因。 |
| before / after | 脱敏后的关键字段变更摘要。 |
| source_ip / user_agent | 请求来源；按隐私和留存策略保存。 |

审计日志不能记录密码、原始 JWT、人脸特征、完整密钥、文件内容或不必要的个人敏感信息。DENIED 事件应限流和聚合，避免攻击流量压垮审计存储。

## 18. 发布、回滚与应急权限

### 18.1 发布顺序

1. 先建立权限字典和角色关联，但保持旧授权规则生效。
2. 为管理员配置数据范围，并通过只读影子校验比较新旧授权结果。
3. 先启用查询权限，再启用普通写权限，最后启用审批、同步、放行、导出等高风险权限。
4. 切换角色白名单并撤销所有旧会话，要求用户重新登录。
5. 观察 401、403、审批失败、设备同步失败和越权告警后再移除兼容规则。

### 18.2 回滚策略

- 回滚只恢复上一版已审核的角色权限配置，不恢复已撤销的 JWT 会话。
- 回滚权限规则时不能临时把业务接口改为 permitAll；必要时只允许受控 SUPER_ADMIN 处置。
- 保留权限配置版本号和变更记录，角色关联更新应整体提交，避免出现半套权限。
- 若数据范围过滤出现故障，默认关闭受影响的写、导出和下载能力，而不是放开全局数据。
- 若设备同步权限故障，停止新下发并保留重试任务，不修改已形成的进出流水。

### 18.3 应急账号

应急账号不作为第四类业务角色，仍属于 SUPER_ADMIN。账号凭据离线保管、默认禁用或受强认证保护；启用需要双人审批，使用后立即轮换凭据、撤销会话并复核全部审计事件。

## 19. 最终决策摘要

| 决策项 | 结论 |
| --- | --- |
| 目标角色 | SUPER_ADMIN、ADMIN、USER。CUSTOMER 仅作为迁移期兼容角色。 |
| 授权模型 | 角色 authority + 显式 permission authority + 服务端数据范围 + 业务状态机。 |
| 超级管理员 | 全局治理角色，少量实名账号，不用于日常值守。 |
| 普通管理员 | 默认 ADMIN_OPERATIONS；高风险能力通过 ADMIN_DUTY 权限集按需授予。 |
| 普通用户 | 仅本人数据和自助申请能力。 |
| 角色授予 | 只有 SUPER_ADMIN 可执行角色分配；ADMIN 只能按固定 USER 模板创建账号。 |
| 进出流水 | 不物理删除，只允许受审计的更正。 |
| 门禁授权 | 申请、审核、修改、同步分别授权，并受状态机约束。 |
| 会话策略 | 角色、权限、状态或范围变化后撤销旧会话。 |
| 前端职责 | 仅控制交互展示，不能作为授权边界。 |
| 失败策略 | 默认拒绝；数据库、Redis、范围或状态信息不可用时不降级放行。 |

## 20. 验收清单

- [ ] 未登录访问受保护接口返回 401。
- [ ] 已登录但无对应 authority 的用户返回 403。
- [ ] SUPER_ADMIN 可管理角色和权限，ADMIN 与 USER 不可管理。
- [ ] ADMIN 不能创建、提升、禁用或删除管理员和超级管理员。
- [ ] USER 只能访问本人数据，不能通过 ID、分页参数或文件 ID 越权读取其他数据。
- [ ] 门禁申请、审核、设备下发分别由三项权限控制。
- [ ] 进出流水更正与人工放行均有不可篡改的审计记录。
- [ ] 用户角色或权限变更后，旧 JWT 会话在下一请求失效或重新加载最新权限。
- [ ] 所有文件下载均校验所属业务对象和数据范围。
- [ ] 高风险权限在后台有显著标识、二次确认和授权原因。
- [ ] 审计日志不包含 JWT、密码、人脸特征或文件内容。
- [ ] 权限配置可按版本整体回滚，回滚过程不会出现 permitAll 临时旁路。
