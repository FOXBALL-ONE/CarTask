# 审计系统设计与优化实施基线

> 文档状态：实施基线。本文同时描述当前代码能力、已知缺口和目标方案；“已实现”不等于已经满足生产验收。

## 1. 目标与边界

审计系统回答三个问题，并为安全排查、业务追责和合规复核提供可验证证据：

- **何时**：事实发生时间、记录时间、请求或任务关联标识。
- **何人**：认证用户、系统任务或已验签外部系统的实际主体。
- **何事**：稳定定义的动作、目标资源、结果和必要的变更摘要。

系统记录结构化的业务与安全事实，不替代运行日志、HTTP 全量访问日志或车辆进出流水。`AccessRecord` 仍是业务流水；人工放行、纠正等决策另写审计事件，并通过资源标识和 `request_id` 关联。

当前管理端接口为 `/admin/api/audit-events`，客户侧 `frontend/` 不提供全局审计功能。数据库迁移和生产数据库权限变更不在本文实施范围内，由数据库工作单独授权和交付。

## 2. 关键结论与 P0 缺口

当前仓库已经有 `AuditEvent`、`AuditServiceImpl`、请求上下文过滤器、哈希链、查询控制器和部分业务接入。但以下问题必须在生产上线前关闭：

1. 多个服务把 `AuditService` 声明为可空；`AuthService`、安全拒绝处理器和登录流程使用 `runCatching` 吞掉审计写入异常，高风险操作可能在无审计证据时继续成功。高风险路径必须改为强制依赖、失败即回滚或返回 `503`。
2. `AuditServiceImpl.record` 使用普通 `REQUIRED` 事务，没有独立的失败事件写入器。可预期失败和授权拒绝若需要在业务回滚后保留，必须通过独立 Bean 的 `REQUIRES_NEW` 事务写入，不能在同一 Bean 内自调用规避代理。
3. 当前脱敏主要是字段白名单和长度截断，没有实现号牌/手机号掩码；登录失败的 `targetId` 仍可能保存原始登录标识。上线前必须改为按 `target_type` 的显式掩码策略，并增加反向泄露测试。
4. 当前哈希规范化未包含 `trace_id`，也没有规范版本字段。生产前必须固定并版本化规范化算法，算法输入变更必须增加版本，不得静默改变校验结果。
5. `lockPartition` 使用 PostgreSQL 原生 `pg_advisory_xact_lock`；开发配置仍使用 `ddl-auto=create` 和默认 `postgres` 账号。生产必须使用 PostgreSQL 专用校验、受控 schema 交付和非所有者应用账号，才能声称审计表不可更新/删除。
6. `Syn*Task` 在 `@Transactional` 方法内部捕获运行时异常并继续返回，可能提交部分业务变更；任务目前只建立上下文，没有批次成功/失败审计事件。任务应采用“事务内业务处理 + 事务外结果记录”的编排，并禁止吞掉未知提交结果。
7. 查询控制器当前全部限制为 `SUPER_ADMIN`；部门数据范围、`audit:hold` 权限和保全接口尚未实现。本文将其明确为后续阶段能力，不再作为当前实现描述。
8. 当前已提供的是计数器，没有 `audit_write_duration_seconds` 直方图，也没有归档签名、独立对象存储和清理任务。相关验收必须标记为未完成。

## 3. 设计原则

1. 审计事件只追加，不提供业务更新和删除接口；数据库 ACL 负责最终阻断 `UPDATE`、`DELETE`。
2. 新增业务时间使用 `LocalDateTime`；接口传输使用 ISO-8601 `ISO_LOCAL_DATE_TIME`，并统一使用应用配置的业务时区。`recorded_at` 和哈希链顺序不依赖客户端时间。
3. 操作者只来自服务端 `CurrentUserPrincipal` 或受控系统主体；请求体、查询参数和前端 `user_id` 永远不能改变操作者。
4. `action` 使用代码字典，权限编码使用独立的 `Permission.code` 字典。URL、异常自由文本和展示文案不能作为动作主键。
5. 采用最小必要记录和“未知字段默认丢弃”。密码、密码哈希、JWT、Authorization 头、人脸特征、密钥、文件内容和原始敏感请求体永不进入审计数据。
6. 高风险成功操作必须与成功审计事件满足明确的一致性策略；审计写入失败不能被静默忽略。
7. 同步数据库写入是当前选择：它降低丢失窗口并保留数据库事务语义，但会增加业务请求延迟。未建立可靠 outbox、重放和告警前，不引入异步队列替代主审计存储。

## 4. 当前能力矩阵

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| 追加式实体、索引、事件 ID | 已实现 | `AuditEvent`、`AuditEventRepository` 已存在。当前表是逻辑分区，不是 PostgreSQL 声明式分区。 |
| 请求关联上下文 | 部分实现 | `AuditRequestContextFilter` 校验或生成 UUID 格式 `X-Request-Id` 并回传；任务 `withRun` 默认主体为 `SYSTEM`，尚未保存具体任务代码。 |
| 用户、角色、权限、门禁、流水、文件接入 | 部分实现 | 服务层已有显式调用；多个依赖可空，认证流程还会吞写入异常。 |
| 授权拒绝审计 | 部分实现 | `SecurityConfig` 和 `GlobalExceptionHandler` 均有处理，需继续验证不会重复或丢失。 |
| 设备/科拓批次审计 | 未实现 | `Syn*Task` 只建立上下文；应补批次 ID、处理数、成功数、失败数和幂等键。 |
| 查询、详情、导出、链校验 | 已实现基础版 | 目前接口仅 `SUPER_ADMIN` 可用；导出上限 1000 条，查询时间范围由服务校验。 |
| 失败独立事务 | 未实现 | 需要单独的 `REQUIRES_NEW` 写入服务。 |
| 数据范围授权与调查保全 | 未实现 | 不得在验收中按已具备能力描述。 |
| 归档签名、保留清理、实时告警 | 未实现 | 仅作为后续交付阶段。 |

## 5. 与现有权限模型的衔接

当前 `Permission` 包含 `code`、`name`、`description`、`enabled`，角色通过 `role_permissions` 关联权限，JWT 认证将启用权限写入 `CurrentUserPrincipal`。审计动作的类别和风险先由代码中的 `AuditAction` 维护，不要求立即修改权限实体。

首期初始化以下权限，并通过 `hasAuthority(...)` 判断：

| 权限编码 | 作用 | 首期授权 |
| --- | --- | --- |
| `audit:read` | 查询和查看脱敏事件 | `SUPER_ADMIN` |
| `audit:export` | 导出限定时间范围事件 | `SUPER_ADMIN`，需单独审批 |
| `audit:verify` | 查看链校验结果 | `SUPER_ADMIN` 或安全运维账号 |

`audit:hold` 只有在保全模型和接口交付后才加入初始化清单。不能用 `Permission.name`、前端菜单或角色字符串代替权限判断。权限或角色治理事件至少保存实际授权决策使用的权限快照；会话版本变更仍按现有认证策略执行。

## 6. 总体架构与接入边界

```text
HTTP 请求 / 定时任务 / 外部回调
          |
          v
请求上下文：request_id、source_system、认证主体
          |
          v
鉴权 -> 服务层业务规则 -> AuditService.record(...)
          |                         |
          v                         v
      统一响应                 audit_event 追加写入
                                    |
                         链校验 / 归档 / 告警 / 查询
```

`AuditService` 是唯一写入口。控制器只提取 HTTP 输入；业务服务在已经确定动作、目标、结果和白名单摘要的位置调用审计。禁止通过 JPA 生命周期回调、AOP 自动序列化实体或 Filter 抓取请求体推断业务意图。

首期接入边界如下：

| 代码边界 | 已有或首期动作 | 规则 |
| --- | --- | --- |
| `AuthService` | 登录成功、登录失败、登出 | 登录失败只保存掩码后的标识和稳定原因码；Redis 会话与数据库审计不具备分布式原子性，审计失败时必须删除或撤销刚创建的会话并返回 `503`。 |
| `UserServiceImpl` | 用户创建、更新、状态、角色、删除 | 数据库业务变更和成功审计同一事务；会话撤销数量写入摘要。 |
| `RoleServiceImpl`、`PermissionServiceImpl` | `ROLE_CHANGED`、`PERMISSION_CHANGED` | 保存权限代码集合差异；删除被拒绝也要记录拒绝事实。 |
| `AccessControlServiceImpl` | 创建、更新、审核；同步动作待设备接入 | 人脸字段只记录是否配置，不记录内容。 |
| `AccessRecordServiceImpl` | 人工纠正、人工/远程放行 | 保存白名单前后值、渠道和原因。 |
| `FileServiceImpl` | 上传、敏感下载；删除动作待补齐 | 不记录内容、绝对路径或可复原的存储密钥。 |
| `SecurityConfig`、全局异常处理 | 高风险授权拒绝 | 统一生成去重键，避免同一请求重复记录。 |
| `Syn*Task`、Keytop 外部回调 | 批次同步待补齐 | 每个批次使用稳定批次 ID；验签失败只记录有限拒绝信息。 |

## 7. 事件模型与不变量

主表命名为 `audit_event`，事件不可变，不复用普通实体的更新时间语义。核心字段如下：

| 字段 | 类型/规则 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 数据库内部分页键，不对外暴露。 |
| `event_id` | UUID，唯一 | 对外查询标识。 |
| `occurred_at` / `recorded_at` | `LocalDateTime` | 事实发生时间 / 审计持久化时间。 |
| `request_id` | UUID 文本，可空 | HTTP、任务批次和外部调用关联标识。 |
| `trace_id` | 文本，可空 | 有链路追踪时保存；必须纳入哈希规范化输入。 |
| `actor_type` | `USER`、`SYSTEM`、`EXTERNAL`、`ANONYMOUS` | `USER` 时 `actor_user_id` 必填；系统和外部主体使用受控代码。 |
| `actor_user_id` / `actor_username` / `actor_role` | 快照 | 用户改名或改角色后仍可还原当时主体。登录失败不得把原始凭据写入这些字段。 |
| `authorities` | JSON 数组 | 当次授权决策使用的规范化权限快照，不保存 JWT。 |
| `action` / `category` / `risk_level` | 字典值 | 动作代码、类别、风险级别。 |
| `target_type` / `target_id` | 受控文本 | 目标类型必填；目标 ID 可空，禁止放入原始文件路径或凭据。 |
| `target_summary` / `scope_summary` | 脱敏 JSON | 只允许白名单键和值。 |
| `result` | `SUCCESS`、`DENIED`、`FAILED` | 未知提交结果不得伪造为 `FAILED`，应产生运行告警并进入人工核查。 |
| `reason_code` / `reason` | 稳定码 / 脱敏文本 | 原因码供查询和告警，人工说明限长并去控制字符。 |
| `before_data` / `after_data` | 脱敏 JSON | 只记录还原事实所需的字段。 |
| `source_ip` / `user_agent` | 受控来源、最大 512 字符 | 仅可信代理配置后才解析转发头；否则使用连接地址。 |
| `source_system` | 受控枚举 | `WEB`、`SYSTEM`、`SCHEDULER`、`KEYTOP` 等，不接受任意客户端字符串。 |
| `partition_key` / `sequence_no` | `yyyy-MM` / 分区内递增 | 依据 `recorded_at` 分配，不能由 `occurred_at` 或客户端决定。 |
| `previous_hash` / `event_hash` | SHA-256 十六进制 | 串联同一逻辑分区的写入顺序。 |
| `idempotency_key` | 受控文本，可空 | 外部事件 ID 或任务批次 ID；唯一性范围为来源、动作和键。 |

生产前必须固定以下不变量并以测试保护：`USER` 必须有用户 ID，匿名主体不能有用户 ID；动作代码、来源代码和目标类型符合字典；请求 ID 为 UUID 文本；JSON 使用 UTF-8、固定键序和明确空值规则；哈希规范化包含所有不可变字段以及 `canonicalization_version`（或等价的版本常量）。

## 8. 动作字典与记录级别

当前代码中的 `AuditAction` 已包含：`AUTH_LOGIN_SUCCEEDED`、`AUTH_LOGIN_FAILED`、`AUTH_LOGOUT`、`AUTHORIZATION_DENIED`、`USER_CREATED`、`USER_UPDATED`、`USER_ROLE_ASSIGNED`、`USER_STATUS_CHANGED`、`USER_DELETED`、`ROLE_CHANGED`、`PERMISSION_CHANGED`、`ACCESS_CONTROL_CREATED`、`ACCESS_CONTROL_UPDATED`、`ACCESS_CONTROL_REVIEWED`、`ACCESS_CONTROL_SYNCED`、`ACCESS_RECORD_CORRECTED`、`ACCESS_RECORD_RELEASED`、`FILE_UPLOADED`、`FILE_DOWNLOADED`、`SENSITIVE_DATA_EXPORTED`。

动作字典需额外维护“是否必须审计、字段白名单、告警级别、允许来源、幂等策略”。以下动作仍是规划项，不能写入当前已实现清单：`AUTH_SESSION_REVOKED`、`DEVICE_*`、`ACCESS_RECORD_SYNCED`、`FILE_DELETED`、独立的角色权限集合变更代码。

记录规则：

- `CRITICAL`：角色/权限治理、账号权限提升、人工或远程放行、流水更正；成功事件必须告警。
- `HIGH`：账号状态、门禁审核、设备配置、敏感下载和导出。
- `MEDIUM`：登录成功/失败、授权拒绝、普通文件上传。
- 普通查询不逐条记录；敏感导出、批量下载和审计查询按操作记录。
- 大批量同步写一条批次事件，附处理/成功/失败计数和批次标识；只有需要逐项追责的失败才追加明细。

## 9. 上下文、事务和失败处理

### 9.1 上下文

`AuditRequestContextFilter` 为请求接受或生成 UUID 格式的 `X-Request-Id`，在响应回传。它从 `SecurityContextHolder` 获取 `CurrentUserPrincipal`，不读取请求体中的操作者字段。

- 已认证请求：保存用户 ID、用户名、规范化角色和权限快照。
- 登录失败：`ANONYMOUS`，登录标识使用掩码或不可逆指纹；不得写密码、Authorization 头或原始 JWT。
- 定时任务：`SYSTEM`，必须显式传入稳定任务代码和批次 ID；不能所有任务都只显示为 `SYSTEM`。
- 外部回调：先验签和校验来源，再使用 `EXTERNAL` 与受控系统代码；验签失败不保存原始报文。

`X-Forwarded-For` 和 `Forwarded` 只有在部署明确配置可信代理时才可使用。

### 9.2 事务策略

| 场景 | 业务事务 | 审计策略 |
| --- | --- | --- |
| 数据库写操作成功 | 业务变更和审计使用同一 `REQUIRED` 事务 | 任一失败整体回滚；不得在提交后异步“补记”。 |
| 已知业务失败 | 业务事务回滚或无变更 | 用独立 `REQUIRES_NEW` 写 `FAILED`；写失败至少产生运行告警。 |
| 授权拒绝 | 不进入业务写入 | 统一拒绝处理器用独立短事务写 `DENIED`，按请求 ID 去重。 |
| Redis 会话操作 | 与数据库不具备分布式原子性 | 审计失败时执行撤销/删除补偿；补偿失败告警并禁止返回成功。 |
| 外部设备调用 | 先持久化本地批次/状态 | 每次尝试使用新的事件或明确重试关联，外部事件 ID 作为幂等键。 |
| 异常导致提交结果未知 | 不确定 | 不伪造 `FAILED`；记录 `AUDIT_WRITE_OR_TRANSACTION_UNKNOWN` 运行告警并保留请求/事务日志证据。 |

审计写入器必须是非空依赖。禁止用 `runCatching { auditService.record(...) }` 后继续返回成功；若某些低风险动作允许降级，必须在动作字典中显式声明并监控，而不是由调用方随意决定。

### 9.3 任务编排

不要在 `@Transactional` 方法内部捕获异常后继续结束事务。任务应拆为：

1. 生成任务批次 ID，并在 `AuditRequestContext.withRun` 中建立 `SYSTEM` 上下文。
2. 在业务事务中处理一批数据；遇到异常时回滚或明确保存失败状态。
3. 在事务外用独立审计事务记录批次结果，包含总数、成功数、失败数、开始/结束时间和外部请求 ID。

## 10. 并发、幂等和哈希链

当前 PostgreSQL 实现按 `recorded_at` 生成自然月逻辑分区，并在同一事务中使用 `pg_advisory_xact_lock(hashtextextended(partition_key, 0))`：锁定分区、读取链尾、分配 `sequence_no`、计算并插入事件。哈希链顺序按实际写入顺序，不按 `occurred_at` 排序。

外部设备、消息消费和任务必须提供服务端生成或校验过的 `idempotency_key`。使用唯一约束 `(source_system, action, idempotency_key)`；重复投递返回原事件，不追加第二条。跨自然月并发重试仍必须处理唯一约束冲突并返回已存在事件，不能把冲突当成新的业务失败。

哈希输入必须包含除数据库自增 ID 外的所有不可变字段、上一摘要和规范版本。规范化实现应有固定键序、UTF-8 编码、空值规则和黄金样例测试。哈希链只能发现篡改，不能阻止拥有数据库完全控制权的攻击者重写整条链；生产还需要独立密钥签名的每日校验清单，并保存在独立权限域。

## 11. 数据保护和数据库权限

每个 `target_type` 都维护白名单，未知字段默认丢弃：

- 用户：用户名、角色、账号状态、部门 ID；手机号只保留后四位或保存不可逆指纹。
- 门禁授权：状态、有效期、部门 ID、门禁点数量；`face_info` 永不记录。
- 车辆：号牌只保留后四位；不得把完整号牌放在 `target_id`、`reason` 或 JSON 嵌套值中。
- 文件：文件 ID、归属资源、原始文件名（必要时再脱敏）、大小和类型；不记录内容、绝对路径和存储凭据。
- 登录失败：保存掩码标识或稳定指纹，不保存原始用户名、密码或请求体。

字符串需去控制字符并限长；原因文本需要凭据关键字正则防护。脱敏测试必须检查嵌套 Map、数组、异常文本和未知键。

生产数据库必须使用非所有者应用账号：业务应用对审计表仅有 `INSERT`、`SELECT`，无 `UPDATE`、`DELETE`；表所有者和维护账号独立。开发环境的 `postgres` 默认账号和 `ddl-auto=create` 不能作为生产安全依据。数据库管理员的修复、归档和清理需通过受控运维流程并在外部不可变日志留痕。

## 12. 查询、导出与授权

### 12.1 首期接口

首期继续使用 `SUPER_ADMIN` 加权限编码，待数据范围模型交付后再开放安全审计管理员：

| 接口 | 权限 | 约束 |
| --- | --- | --- |
| `GET /admin/api/audit-events` | `audit:read` | 必须提供 `occurred_from`、`occurred_to`；最长 31 天，每页最多 100 条。 |
| `GET /admin/api/audit-events/{event_id}` | `audit:read` | 按事件 ID 查询仍需执行同等范围授权。 |
| `GET /admin/api/audit-events/verify?partition_key=yyyy-MM` | `audit:verify` | 仅返回校验结果、事件数和序号范围。 |
| `POST /admin/api/audit-events/export` | `audit:export` | 必须提供时间范围，最长 7 天，最多 1000 条；导出本身写 `SENSITIVE_DATA_EXPORTED`。 |

所有时间参数和响应时间使用 `LocalDateTime` 的 ISO-8601 文本。查询排序为 `occurred_at DESC, event_id DESC`；链追溯使用 `partition_key` 和 `sequence_no`。普通响应不返回权限快照、来源 IP、User-Agent、哈希原文或数据库内部 ID；`event_hash` 只在确有校验用途时返回。

### 12.2 后续数据范围

第二阶段增加基于部门/资源归属的数据范围策略：普通管理员只能查看其范围内的业务事件；认证、角色、权限、全局配置和链校验事件默认仅安全管理员可见。范围判断必须在列表和单条详情分别执行，不能因为知道 `event_id` 就绕过授权。`audit:hold`、二次确认和保全标记在此阶段一起交付。

## 13. 保留、归档、告警和指标

建议在线保留 180 天、归档至少 3 年，最终期限以法务、合同和隐私要求为准。清理只允许针对已校验、已归档且无保全标记的逻辑分区；清理动作需外部留痕。签名校验清单应包含分区、末端摘要、事件数、校验时间和签名密钥版本。

至少建立以下告警：

- `CRITICAL` 操作成功、权限提升、账号启用、人工/远程放行。
- 高风险审计写入失败、审计存储不可用、任务批次失败。
- 哈希链或签名清单不一致。
- 用户、来源 IP 或任务在短时间内大量登录失败、授权拒绝或导出。
- 超出常用时间、部门或设备范围的敏感导出。

指标标签只允许动作代码、结果和来源系统等低基数字段，禁止用户名、车牌、请求参数：

- `audit_events_written_total`
- `audit_events_reused_total`
- `audit_write_errors_total`
- `audit_chain_verification_failed_total`
- `audit_export_records_total`
- 目标阶段增加 `audit_write_duration_seconds` 直方图和归档/清理失败计数。

当前代码已提供前五项中的计数器，但写入耗时、归档、保全和告警链路尚未完成。高风险同步写操作采用 fail-closed；审计存储不可用时返回 `503` 或回滚业务事务，不能退回 Redis、普通日志或客户端日志作为主存储。

## 14. 分阶段实施顺序

### P0：修正一致性和泄露风险

1. 将高风险路径的 `AuditService` 改为强制依赖，移除吞异常后继续成功的逻辑。
2. 新增独立失败/拒绝审计事务；补充登录 Redis 会话失败补偿。
3. 实现按目标类型的掩码器、来源/动作字典校验和嵌套数据泄露测试。
4. 固定哈希规范版本，补齐 `trace_id` 输入和并发唯一键冲突测试。
5. 修正 `Syn*Task` 的事务编排，禁止在事务内吞掉异常。

### P1：补齐业务覆盖

1. 接入任务批次、设备同步、Keytop 外部回调和会话撤销事件。
2. 为每个高风险动作补齐 before/after 白名单、原因码、告警级别和幂等策略。
3. 通过影子核对统计高风险接口的应记数与实记数，确认无漏记后启用 fail-closed。

### P2：开放受控查询

1. 固定首期接口的时间范围、分页、导出和响应字段契约。
2. 增加安全审计管理员的数据范围判断和单条详情复核。
3. 交付 `audit:hold`、二次确认和保全标记后，再开放调查保全权限。

### P3：完整性运营

1. 建立每日链校验、独立密钥签名和隔离存储。
2. 交付归档、保留、保全豁免清理和运维审计。
3. 接入指标、告警、故障演练和恢复流程。

## 15. 测试与验收

自动化测试至少覆盖：

| 测试 | 断言 |
| --- | --- |
| 字典和不变量 | 动作、来源、目标类型唯一且符合格式；主体字段组合合法；高风险动作均有白名单和告警级别。 |
| 上下文 | JWT 只能提供 `CurrentUserPrincipal` 主体；请求体 `user_id` 不生效；匿名、任务、外部主体分类正确。 |
| 事务 | 成功业务与审计同提交；业务回滚不残留成功事件；失败/拒绝使用新事务；高风险审计失败导致回滚或 `503`。 |
| Redis 补偿 | 登录审计失败不会留下可用会话；补偿失败有告警且不返回成功。 |
| 幂等并发 | 同一键只产生一条事件；并发写入的序号唯一、前序摘要连续；跨月冲突可恢复。 |
| 脱敏 | 密码、JWT、密钥、人脸、文件内容、完整号牌、原始手机号和未知字段永不进入任何审计字段。 |
| 授权 | 无 `audit:read/export/verify` 分别被拒绝；首期只有超级管理员可查；后续范围规则不能越权。 |
| 完整性 | 任意字段、序号、链头或签名清单被改动都能发现；未完成归档或有保全标记的分区不能清理。 |
| 任务 | 失败不提交部分业务结果；批次事件包含总数、成功数、失败数和外部关联 ID。 |

上线验收必须同时满足：

- 每个已纳入范围的高风险操作都能按 `event_id` 或 `request_id` 找到时间、主体、动作、目标、结果和脱敏摘要。
- 业务成功与成功审计按对应事务策略一致；失败和拒绝不会静默丢失。
- 审计中不存在密码、JWT、密钥、完整人脸信息、文件内容或未经掩码的敏感标识。
- 应用账号无法更新或删除审计记录；链校验、写入失败和导出行为有指标与告警。
- 未交付的数据范围、保全、归档签名和清理能力不得写入“已完成”验收结论。
