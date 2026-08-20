# 审计系统设计

## 1. 目标与范围

审计系统用于回答下列问题，并为安全事件排查、业务追责和合规复核提供不可抵赖的证据：

- **何时**：事件在何时发生、由哪个请求或后台任务触发。
- **何人**：已认证用户、系统任务或外部系统中的实际操作者是谁。
- **何事**：对哪个业务对象执行了哪个稳定定义的操作，结果和关键变更是什么。

系统记录的是结构化的**业务与安全审计事件**，不是替代应用运行日志、HTTP 全量访问日志或车辆进出流水。`AccessRecord` 仍是业务流水；对它的人工放行、纠正等人工决策另写审计事件，二者通过资源标识和 `request_id` 关联。

本设计适用于当前 `carTask` 后端。管理端未来通过 `/admin/api/audit-events` 查询审计记录；客户侧 `frontend/` 不展示全局审计功能。数据库迁移不在本设计的实施范围内。

## 2. 设计原则

1. 审计记录以附加写入为主，业务代码没有更新、删除审计记录的能力。
2. 时间使用 `LocalDateTime`，API 按 `ISO_LOCAL_DATE_TIME` 传输，例如 `2026-08-20T14:30:00`。
3. 操作者身份只取自服务端认证上下文 `CurrentUserPrincipal`，不信任请求体、参数或前端传入的 `user_id`。
4. 操作使用稳定机器代码，展示文案由后台按代码映射，不能把 URL 或自由文本作为唯一操作标识。
5. 只记录还原事实所需的最小数据；密码、密码哈希、原始 JWT、人脸特征、密钥、文件内容和完整敏感个人信息绝不进入审计记录。
6. 高风险操作没有成功落库的审计事件时，业务操作应失败并告警，不能静默放行。
7. 审计动作和权限编码是两个不同的字典：`action` 描述已经发生的事实，`Permission.code` 描述允许谁执行或查看该事实。

## 2.1 与现有权限模型的衔接

当前 `Permission` 实体只有 `code`、`name`、`description`、`enabled` 四个字段，`Role.permissions` 通过 `role_permissions` 关联权限，JWT 认证时由 `RolePermissionService` 读取启用权限并写入 `CurrentUserPrincipal`。本设计不要求立即向 `Permission` 增加风险字段或创建数据库迁移；审计动作元数据先由代码中的 `AuditAction` 字典维护。

审计相关权限建议使用现有编码规范的小写 `domain:verb` 形式，并纳入权限初始化清单：

| 权限编码 | 作用 | 建议角色 |
| --- | --- | --- |
| `audit:read` | 查询脱敏审计事件 | `SUPER_ADMIN`，按范围授予安全审计管理员 |
| `audit:export` | 导出限定范围审计事件 | 仅 `SUPER_ADMIN` 或经审批的安全审计管理员 |
| `audit:verify` | 查看哈希链/归档校验结果 | 仅 `SUPER_ADMIN` 与安全运维账号 |
| `audit:hold` | 设置或解除调查保全标记 | 仅 `SUPER_ADMIN`，要求二次确认 |

权限判断仍使用 `hasAuthority(...)` 和规范化后的 `SecurityPermission`；不能通过 `Permission.name`、前端菜单或 `role` 字符串判断审计权限。`enabled=false` 的权限不会进入新 JWT，但已签发会话在权限变更后必须按现有会话版本策略撤销。对 `Permission` 或 `Role` 的创建、修改、禁用、删除以及角色权限关联变化，审计事件的 `authorities` 至少保存实际执行者的 `permission:manage` 或 `role:manage` 快照，不能只记录角色名。

## 3. 总体架构

```text
HTTP 请求 / 定时任务 / 外部回调
          |
          v
审计上下文：request_id、来源、认证主体、任务主体
          |
          v
控制器鉴权 -> 服务层执行业务规则 -> AuditService.record(...)
          |                                      |
          |                                      v
          |                             audit_event 追加写入
          v                                      |
统一响应                                      哈希链与告警/归档任务
                                                 |
                                                 v
                                  /admin/api/audit-events 只读查询与导出
```

`AuditService` 是唯一写入入口。控制器负责提取 HTTP 输入和调用服务；业务服务在已经确定动作、目标、结果和变更摘要的位置显式调用审计服务。不要通过 JPA `@PreUpdate`、AOP 自动序列化实体或 HTTP Filter 全量抓取请求体来推断业务审计：这些方式无法可靠识别业务意图，且极易泄露敏感字段。

## 3.1 当前代码边界的接入点

| 现有边界 | 首批接入动作 | 接入位置 |
| --- | --- | --- |
| `AuthController` / `AuthService` | `AUTH_LOGIN_SUCCEEDED`、`AUTH_LOGIN_FAILED`、`AUTH_LOGOUT` | 登录校验完成、会话删除完成或失败原因确定后。登录失败不得记录密码。 |
| `UserServiceImpl` | `USER_CREATED`、`USER_STATUS_CHANGED`、`USER_ROLE_ASSIGNED` | 用户保存、状态/角色改变和会话版本递增的同一服务事务。 |
| `RoleServiceImpl` | `ROLE_CHANGED` | 角色启停、名称治理和权限集合变化后，记录前后权限代码集合。 |
| `PermissionServiceImpl` | `PERMISSION_CHANGED` | 权限编码/名称/启停变更及删除尝试；删除被拒绝也要记录 `DENIED`。 |
| `AccessControlServiceImpl` | `ACCESS_CONTROL_CREATED`、`ACCESS_CONTROL_REVIEWED`、`ACCESS_CONTROL_SYNCED` | 审核状态机和同步结果确定处。人脸字段只记录“已配置/未配置”。 |
| `AccessRecordServiceImpl` | `ACCESS_RECORD_CORRECTED`、`ACCESS_RECORD_RELEASED` | `correct`/`release` 命令执行处；保存修正原因和前后白名单字段。 |
| `FileServiceImpl` | `FILE_DELETED`、敏感下载事件 | 删除成功或拒绝处；不能记录文件内容和存储绝对路径。 |
| `Syn*Task`、Keytop 回调 | `DEVICE_SYNC_*`、`ACCESS_RECORD_SYNCED` | 每个任务批次一个事件，外部事件 ID 作为幂等键；记录处理/成功/失败计数。 |

控制器上的 `@PreAuthorize` 继续负责快速拒绝；服务层仍须执行状态、数据范围和最后一次权限校验。审计不应放在控制器返回响应之后的“尽力而为”回调中，否则客户端已看到成功而数据库可能没有证据。

## 4. 审计事件模型

审计主表建议命名为 `audit_event`。它是追加式事件表，不与现有实体共用 `AuditingEntityListener` 的更新时间语义。

| 字段 | 类型/示例 | 是否必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 数据库主键，用于分页。 |
| `event_id` | UUID | 是 | 对外可见的事件唯一标识；有唯一约束。 |
| `occurred_at` | `LocalDateTime` | 是 | 业务动作实际完成或被拒绝的时间。 |
| `recorded_at` | `LocalDateTime` | 是 | 审计记录同步持久化时间。 |
| `request_id` | UUID | 否 | 单个 HTTP 请求、下游设备调用和其产生事件的关联标识。 |
| `trace_id` | String | 否 | 与外部链路追踪系统关联；没有则为空。 |
| `actor_type` | 枚举 | 是 | `USER`、`SYSTEM`、`EXTERNAL`、`ANONYMOUS`。 |
| `actor_user_id` | Long | 否 | 已认证用户 ID；`USER` 时必填。 |
| `actor_username` | String | 是 | 操作者用户名或系统任务名的快照，不依赖用户后续改名。 |
| `actor_role` | String | 否 | 认证时角色快照，例如 `ADMIN`。 |
| `authorities` | JSON 数组 | 否 | 本次授权决策实际使用的权限代码摘要，例如 `["access-control:review"]`。 |
| `action` | String | 是 | 稳定操作代码，例如 `ACCESS_CONTROL_REVIEW`。 |
| `category` | 枚举 | 是 | `AUTHENTICATION`、`AUTHORIZATION`、`ACCOUNT`、`ACCESS_CONTROL`、`ACCESS_RECORD`、`DEVICE`、`FILE`、`CONFIGURATION`、`DATA_EXPORT`。 |
| `risk_level` | 枚举 | 是 | `LOW`、`MEDIUM`、`HIGH`、`CRITICAL`。 |
| `target_type` | String | 是 | 资源类型，例如 `access_control`、`user`、`stored_file`。 |
| `target_id` | String | 否 | 资源主键或外部资源标识；批量操作使用批次 ID。 |
| `target_summary` | JSON 对象 | 否 | 不含敏感数据的资源识别摘要，例如车牌后四位、设备编号。 |
| `scope_summary` | JSON 对象 | 否 | 当次数据范围摘要，例如部门 ID 列表和范围策略。 |
| `result` | 枚举 | 是 | `SUCCESS`、`DENIED`、`FAILED`。 |
| `reason_code` | String | 否 | 稳定原因代码，例如 `STATUS_INVALID`、`PERMISSION_DENIED`。 |
| `reason` | String | 否 | 审批、驳回、更正或失败的人工说明；长度受限并脱敏。 |
| `before_data` | JSON 对象 | 否 | 变更前白名单字段的脱敏摘要。 |
| `after_data` | JSON 对象 | 否 | 变更后白名单字段的脱敏摘要。 |
| `source_ip` | String | 否 | 客户端地址；仅在可信反向代理配置下解析转发头。 |
| `user_agent` | String | 否 | 截断后的 User-Agent，最大 512 字符。 |
| `source_system` | String | 是 | `WEB`、`SCHEDULER`、`KEYTOP` 等受控来源代码。 |
| `sequence_no` | Long | 是 | 分区内单调递增的写入序号，用于哈希链校验和按写入时间追溯。 |
| `previous_hash` | String | 否 | 同一审计分区按 `sequence_no` 的上一条事件摘要；分区首条为空。 |
| `event_hash` | String | 是 | 当前事件规范化内容加 `previous_hash` 的 SHA-256 摘要。 |
| `idempotency_key` | String | 否 | 外部调用或任务重试的去重键；在来源和动作范围内唯一。 |

`before_data`、`after_data`、`target_summary`、`scope_summary` 和 `authorities` 当前以 UTF-8 JSON 文本存储；业务查询字段保持独立列，避免依赖 JSON 全表扫描。字段值须按固定键序、UTF-8 和明确的空值规则规范化后再计算 `event_hash`，保证同一事实产生相同摘要。`occurred_at` 是事实发生时间，可以早于或晚于 `recorded_at`，但永远不能决定哈希链顺序。

## 5. 操作字典与记录级别

`action` 由代码常量集中维护，并附带类别、风险级别、是否必须审计及可记录字段白名单。新增高风险权限或高风险接口时，必须先增加对应动作代码和测试。

| 动作代码 | 触发时机 | 风险 | 必须记录的事实 |
| --- | --- | --- | --- |
| `AUTH_LOGIN_SUCCEEDED` / `AUTH_LOGIN_FAILED` | 登录成功或失败 | MEDIUM | 用户名、结果、失败原因、来源。 |
| `AUTH_LOGOUT` / `AUTH_SESSION_REVOKED` | 注销、禁用用户或权限变更后撤销会话 | HIGH | 操作者、受影响用户、撤销原因和会话数量。 |
| `USER_CREATED` / `USER_STATUS_CHANGED` | 新建用户、启停/封禁账号 | HIGH | 目标用户、原状态、新状态、原因。 |
| `USER_ROLE_ASSIGNED` | 修改用户角色 | CRITICAL | 目标用户、原角色、新角色、操作者权限。 |
| `ROLE_PERMISSIONS_CHANGED` | 修改角色权限 | CRITICAL | 目标角色、增删权限集合、原因。 |
| `ACCESS_CONTROL_CREATED` / `ACCESS_CONTROL_REVIEWED` | 新建或审核门禁授权 | HIGH | 授权 ID、审核前后状态、审批意见。 |
| `ACCESS_CONTROL_SYNCED` | 下发或同步设备 | HIGH | 授权 ID、设备/平台摘要、同步结果。 |
| `ACCESS_RECORD_RELEASED` | 人工或远程放行 | CRITICAL | 流水 ID、放行渠道、原因、操作者。 |
| `ACCESS_RECORD_CORRECTED` | 更正进出流水 | CRITICAL | 原值、修正值、修正原因。 |
| `DEVICE_CREATED` / `DEVICE_UPDATED` | 修改设备或外部连接配置 | HIGH | 设备 ID、允许记录的配置差异。 |
| `FILE_DELETED` | 删除文件 | HIGH | 文件 ID、归属资源、删除原因；不记录文件内容。 |
| `SENSITIVE_DATA_EXPORTED` | 导出用户、门禁、流水或审计数据 | HIGH | 导出范围、过滤条件摘要、记录数、导出用途。 |
| `AUTHORIZATION_DENIED` | 高风险接口被拒绝 | MEDIUM | 目标、所需权限、实际角色、拒绝原因。 |
| `PERMISSION_CHANGED` | 创建、修改、启停或删除权限字典 | CRITICAL | 权限 ID、原/新 `code`、`enabled` 状态及关联角色影响。 |
| `ROLE_CHANGED` | 创建、修改或启停角色、调整角色权限 | CRITICAL | 角色 ID、原/新名称、启用状态、增删权限和受影响会话数。 |

普通查询不逐条写入审计，以免放大存储量；涉及敏感数据的导出、批量下载和审计日志查询按操作记录。针对暴力探测产生的 `DENIED` 事件按“操作者/来源 IP/动作/分钟”聚合计数，保留首末发生时间和总次数。

## 6. 写入与事务语义

### 6.1 审计上下文

入口 Filter 为每个请求接受或生成 UUID 格式的 `X-Request-Id`，在响应中回传，并将其放入请求属性。`AuditContext` 从该属性和 `SecurityContextHolder` 读取：

- 认证成功请求：`CurrentUserPrincipal.userId`、`username`、规范化角色和权限快照。
- 未认证登录失败：`ANONYMOUS`、已脱敏的登录标识和来源；不得把密码或 Authorization 头写入上下文。
- 定时任务：`SYSTEM`，操作者为稳定任务代码，如 `SYN_CAR_CAP_INFO_TASK`。
- 科拓等外部回调：`EXTERNAL`，操作者为已校验的来源系统代码；验签失败只记录有限的拒绝信息。

`X-Forwarded-For`、`Forwarded` 仅在部署层明确配置可信代理时读取；否则使用 Servlet 连接地址，避免客户端伪造来源 IP。

### 6.2 成功、失败和拒绝

| 情形 | 业务事务 | 审计写入策略 |
| --- | --- | --- |
| 成功的写操作 | 与业务变更同一数据库事务 | 同一事务插入 `SUCCESS` 事件；任一写入失败则整体回滚。 |
| 可预期的业务失败 | 业务事务回滚或无变更 | 用独立短事务写 `FAILED` 事件，含稳定失败原因。 |
| 授权拒绝 | 不进入业务写入 | 安全层或统一拒绝处理器写/聚合 `DENIED` 事件。 |
| 外部设备调用失败 | 记录本地业务状态后 | 写 `FAILED`；重试时记录新的事件，使用同一 `request_id` 或业务批次 ID 关联。 |

服务层使用显式的 `AuditCommand` 调用 `AuditService.record`，其内容在业务对象加载后、提交前构造。`before_data` 取修改前的白名单快照，`after_data` 取命令校验后的最终值；不要记录完整实体或原始请求 JSON。对批量操作写一条批次事件和必要的失败明细，避免每行数据造成不可控放大。

### 6.3 顺序、并发与重试

哈希链的顺序按实际写入顺序而非 `occurred_at` 排列。`AuditService` 在同一数据库事务中使用 PostgreSQL `pg_advisory_xact_lock(hashtextextended(partition_key, 0))` 锁定自然月分区，再分配 `sequence_no`、读取上一事件、计算摘要并插入事件。这样多实例并发写入不会得到相同的前序摘要，也不会因客户端时间错误破坏链。

外部设备调用、消息消费和定时任务必须传入受控的 `idempotency_key`，建议由来源代码、外部事件 ID 或任务批次 ID 构成。插入使用唯一约束 `(source_system, action, idempotency_key)`；重复投递返回已存在事件，不重新追加链。对普通人工 HTTP 请求不将可由客户端伪造的 `Idempotency-Key` 直接作为审计去重键，除非业务命令已经在服务端完成幂等校验。当前实现使用同步数据库写入，调用线程会等待审计写入完成，以保证业务事务和审计事务一致；不使用异步队列或后台消费者。

业务事务最终回滚时，同一事务内的 `SUCCESS` 审计事件自然回滚。业务服务在确认失败原因后可显式记录 `FAILED`，但不得捕获所有异常后记录失败并继续返回成功。系统异常若无法安全判定业务是否提交，不伪造结果为 `FAILED`，而是记录 `AUDIT_WRITE_OR_TRANSACTION_UNKNOWN` 运行告警，并交由事务/日志证据排查。

## 7. 数据保护与防篡改

### 7.1 脱敏白名单

每个 `target_type` 维护可记录字段白名单。例如用户仅允许用户名、角色、账号状态和部门 ID；门禁授权仅允许状态、有效期、部门 ID、门禁点数量；车辆号牌保留后四位；手机号保留后四位；`face_info` 永不记录。未知字段默认丢弃，而不是尝试自动脱敏。

审计表的数据源账号只授予 `INSERT`、`SELECT`，不授予业务应用 `UPDATE`、`DELETE`。查询接口只读；数据库管理员的维护操作需通过受控运维流程，并在外部运维日志中留痕。

### 7.2 哈希链、校验与归档

按 `recorded_at` 的自然月建立逻辑分区，每个分区内按 `sequence_no` 串联 `previous_hash` 和 `event_hash`。摘要输入应包含除自增 `id` 以外的所有不可变事实字段及前一摘要。每日任务校验链完整性，生成包含分区、末端摘要、事件数和校验时间的签名校验清单，并写入与业务数据库隔离的对象存储或日志平台。

哈希链用于发现篡改，不能单独防止拥有数据库完全控制权的攻击者重写整条链。因此校验清单必须使用独立密钥签名，并保存在独立权限域；产生不一致时立即告警、冻结受影响分区的清理任务，并保留现场。

## 8. 查询、导出与授权

审计查询只对拥有 `audit:read` 的受控管理角色开放。`audit:export` 必须是独立权限，且每次导出写入 `SENSITIVE_DATA_EXPORTED`。一般管理员按其数据范围仅能查看关联目标属于其部门范围的业务事件；认证、角色、权限、全局配置和审计防篡改事件默认仅 `SUPER_ADMIN` 可见。

建议接口如下，所有时间参数和响应时间均为 ISO-8601 LocalDateTime：

| 接口 | 权限 | 用途 |
| --- | --- | --- |
| `GET /admin/api/audit-events` | `audit:read` | 分页查询；筛选 `occurred_at` 范围、操作者、动作、目标、结果、风险级别和 `request_id`。 |
| `GET /admin/api/audit-events/{event_id}` | `audit:read` | 查看单条事件与脱敏前后差异。 |
| `GET /admin/api/audit-events/verify` | `audit:verify` | 查询某时段哈希链与校验清单状态，仅安全管理员可用。 |
| `POST /admin/api/audit-events/export` | `audit:export` | 同步返回最多 1000 条受限范围导出数据；支持 `actor_user_id` 等筛选，导出行为本身记录审计。 |

列表默认按 `occurred_at DESC, event_id DESC` 使用页码分页，并限制每页 100 条；查询必须提供开始/结束时间，单次查询最长 31 天。按写入顺序追溯或校验时使用分区和 `sequence_no`。导出最长 7 天且最多返回 1000 条。响应中的 `before_data`、`after_data` 延续写入时的脱敏结果，不向前端补充原始数据。

查询响应只返回 `event_id`、`occurred_at`、`recorded_at`、`request_id`、操作者快照、`action`、`target_*`、`result`、原因和脱敏差异；不返回权限快照、来源 IP、User-Agent、哈希计算原文、认证凭据或数据库内部序号。按 `event_id` 查询时必须再次执行数据范围判断，不能因为知道 UUID 就绕过范围。

## 9. 保留、归档和告警

建议在线保留 180 天，归档保留至少 3 年；最终保留期以组织的法务、合同和隐私要求为准。到期清理仅针对已完成完整性校验、已归档且未被调查保全标记的分区，清理操作本身需在外部不可变运维记录中留痕。

应建立以下实时或定时告警：

- `CRITICAL` 操作成功、任何角色或权限提升、账号启用、人工/远程放行。
- 高风险事件审计写入失败、审计存储不可用或队列积压超过阈值。
- 哈希链或签名校验不一致。
- 单一用户、来源 IP 或任务短时间出现大量认证失败、授权拒绝或导出尝试。
- 超出常用时间、部门范围或设备范围的敏感导出。

告警消息只带 `event_id`、动作、时间、操作者和目标摘要；收件方通过受控查询页面查看细节。

## 9.1 运行指标与降级策略

至少暴露以下指标（按动作和结果分组，禁止把用户名、车牌或请求参数作为高基数字段）：

- `audit_events_written_total`：写入成功、拒绝、失败和重复投递数量。
- `audit_write_duration_seconds`：审计写入耗时分布。
- `audit_write_errors_total`：数据库、序列/链头锁、脱敏校验和 JSON 规范化错误。
- `audit_chain_verification_failed_total`：链校验失败数量。
- `audit_export_records_total`：导出记录数和拒绝数。

当前同步实现已提供 `audit_events_written_total`、`audit_events_reused_total`、`audit_write_errors_total`、`audit_export_records_total` 和 `audit_chain_verification_failed_total` 计数器；标签仅使用动作编码和结果枚举，避免用户、车牌、请求参数等高基数信息进入指标系统。

高风险同步/写操作采用 fail-closed：审计存储不可用时返回 503 或回滚业务事务。当前实现不使用异步队列、线程池或后台消费者，所有审计事件均在调用线程中同步落库；禁止通过 Redis、普通应用日志或客户端日志作为审计主存储的替代品。

## 10. 实施顺序

1. 定义 `AuditAction`、`AuditCategory`、`AuditResult`、`RiskLevel` 与字段白名单，并实现请求/任务审计上下文。
2. 实现追加式 `AuditEvent` 实体、仓储和 `AuditService`；补充数据库权限、索引和哈希链生成机制。具体建表迁移由另行授权的数据库工作处理。
3. 先接入登录、登出、会话撤销、用户状态与角色权限变更，再接入门禁审核、设备同步、放行、流水更正、文件删除和数据导出。
4. 提供只读管理查询、范围控制和导出审计；最后启用校验清单、归档和告警。
5. 上线前用影子模式比对高风险接口是否漏记；确认覆盖率后将“高风险审计写入失败即回滚”设为强制策略。

## 10.1 测试矩阵

实现时至少覆盖以下自动化测试：

| 测试 | 断言 |
| --- | --- |
| `AuditAction`/权限字典 | 所有高风险动作有唯一代码、风险级别和字段白名单；`audit:*` 编码符合 `SecurityPermission` 规则。 |
| 上下文提取 | 有效 JWT 只能得到 `CurrentUserPrincipal` 的用户/角色/权限；请求体中的 `user_id` 不影响操作者。匿名失败、任务和外部回调主体正确分类。 |
| 事务一致性 | 业务成功与审计同提交；业务回滚不残留成功事件；失败事件使用新事务；审计写入失败使高风险操作失败。 |
| 幂等与并发 | 同一幂等键只产生一条事件；并发写入的 `sequence_no` 唯一且哈希链连续。 |
| 脱敏 | `password`、JWT、`face_info`、密钥、文件内容和未知字段永不进入 JSON；车牌/手机号按白名单遮罩。 |
| 授权与范围 | 缺少 `audit:read`、`audit:export`、`audit:verify`、`audit:hold` 分别返回 403；管理员不能读取其他部门或全局安全事件；导出会产生可查询的导出事件。 |
| 完整性与保留 | 篡改字段或链头会被校验发现；有保全标记的分区不会被清理。 |

## 11. 验收标准

- 任意已审计操作均能按事件 ID 或请求 ID 查询到发生时间、操作者、动作、目标、结果和变更摘要。
- 用户角色、权限、账号状态、门禁审核、设备同步、人工放行、流水更正、文件删除和敏感导出均有对应事件。
- 审计中的操作者来自 `CurrentUserPrincipal` 或受控系统主体，伪造请求参数不能改变记录的操作者。
- 成功的高风险业务操作与 `SUCCESS` 审计事件原子提交；业务失败和授权拒绝保留可追溯结果。
- 审计数据不包含密码、JWT、密钥、完整人脸信息、文件内容或原始敏感请求体。
- 业务应用账号无法更新或删除审计记录；日常校验能发现哈希链断裂或归档清单不一致。
- 无 `audit:read` 权限的用户不能查询；非超级管理员不能越其数据范围读取全局安全审计；每次审计导出都可被再次审计。
- 审计写入失败、链校验失败和归档/清理失败均有指标与告警，不会静默丢失。
