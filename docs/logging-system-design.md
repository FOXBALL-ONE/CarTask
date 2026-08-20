# ShopMall 运行日志系统设计

> 设计文档输出位置：`docs/logging-system-design.md`。本文按 2026-08-20 现有项目结构编写，并完成三轮设计复核与优化。

## 1. 文档目的

本文为 `carTask` 后端设计运行日志系统，解决接口请求、定时任务、外部平台调用和系统异常的统一记录、按天落盘、检索、留存与告警问题。

本系统记录的是**运行事实**，用于故障定位、性能分析和运维审查；用户登录、权限变更、人工放行等不可抵赖的业务事实仍使用现有的[审计系统设计](audit-system-design.md)，不能用普通文本日志替代。

默认日志目录为项目工作目录下的 `logs/`，可以通过环境变量 `APP_LOG_DIR` 指定绝对路径，例如 `D:/shopmall/logs` 或 `/var/log/shopmall`。日志按服务器本地日历日滚动，默认时区为 `Asia/Shanghai`。

## 2. 目标与非目标

### 2.1 目标

- 所有应用组件通过 SLF4J 统一输出，禁止直接使用 `System.out`、`System.err`。
- 每天生成独立日志文件，重启不会覆盖历史文件。
- 通过 `request_id`、`trace_id` 串联一次请求产生的日志；定时任务和外部回调也有稳定关联标识。
- 使用结构化 JSON，便于 `jq`、Loki、ELK 等工具检索，同时保留本地人工阅读能力。
- 对异常堆栈、慢请求、外部接口失败和日志自身写入失败提供可观测指标与告警。
- 日志内容默认脱敏，并限制单条消息大小，避免凭据、请求体和高基数标签泄露。

### 2.2 非目标

- 不把日志表作为业务数据库；日志文件也不承担审计事件的防篡改证明责任。
- 不记录密码、JWT 原文、Cookie、密钥、完整身份证件/车牌、人脸信息、上传文件内容或完整请求体。
- 不在应用内实现复杂的日志搜索页面；运维检索由日志平台或主机工具负责。

## 3. 现状与约束

- 项目使用 Spring Boot 4、Kotlin、Gradle，依赖中的 Spring Boot starter 已提供 SLF4J 与 Logback 基础能力。
- 代码中已有 `LoggerFactory` 调用和 `AuditService`；新增方案应复用现有日志 API，不替换审计服务。
- 生产环境可能多实例部署；每个实例写本地文件，再由日志采集器转发到集中平台。
- 数据库迁移不在本设计范围内。本设计只涉及应用配置、日志格式、文件目录与运维策略。

## 4. 总体架构

```text
HTTP 请求 / 定时任务 / Keytop 回调
          |
          v
请求上下文 Filter：request_id、trace_id、来源、操作者摘要
          |
          v
业务代码 -> SLF4J Logger -> Logback AsyncAppender
                                      |
                         +------------+------------+
                         |                         |
                         v                         v
                logs/app/app.log          logs/error/error.log
                按天滚动并压缩            仅 WARN/ERROR，按天滚动
                         |
                         v
             Fluent Bit / Filebeat -> 集中日志平台
```

应用只负责可靠地写本地文件和输出指标，不在业务线程中调用远程日志平台。采集器使用 `*.log` 与 `*.log.gz` 文件，记录 inode/offset，避免重启重复采集。

## 5. 日志目录与文件策略

### 5.1 路径约定

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_LOG_DIR` | `${user.dir}/logs` | 日志根目录；启动时创建，必须是可写目录。 |
| `APP_LOG_TIME_ZONE` | `Asia/Shanghai` | 日滚动和日志时间显示时区。 |
| `APP_LOG_RETENTION_DAYS` | `30` | 本地保留天数；集中平台可按合规策略保留更久。 |
| `APP_LOG_TOTAL_SIZE_CAP` | `10GB` | 单个日志类型的压缩文件总大小上限。 |

目录结构固定如下：

```text
${APP_LOG_DIR}/
  app/app.log
  app/app.2026-08-20.0.log.gz
  error/error.log
  error/error.2026-08-20.0.log.gz
  archive/                 # 可选，仅供运维归档，不由应用直接上传
```

`app.log` 和 `error.log` 是当前活动文件；日期文件在每天 `00:00:00`（配置时区）切换。应用异常退出时，Logback 在下次启动时继续使用当天文件，不删除历史文件。单日文件超过 `256MB` 时增加序号，防止单个文件无限增长。

### 5.2 滚动与留存

- 使用 Logback `SizeAndTimeBasedRollingPolicy`，模式为 `yyyy-MM-dd.i`，压缩格式为 gzip。
- 每天只保留最近 30 天本地文件，超过总大小上限时优先删除最旧的已压缩文件。
- 删除前检查文件不在调查保全清单中；需要长期保存时由采集器或归档任务复制到对象存储。
- 单实例多进程不得共享同一活动文件；容器部署时为每个实例挂载独立目录或在文件名中加入实例标识。
- 磁盘使用率达到 70%/85%/95% 分别告警。达到 95% 时停止 DEBUG/INFO 文件写入并保留 WARN/ERROR，同时保留控制台错误输出。

## 6. 结构化事件模型

每行一个 JSON 对象，字段顺序固定，消息中的换行符转义为 `\\n`，不得输出多行 JSON。建议字段如下：

| 字段 | 必填 | 示例 | 规则 |
| --- | --- | --- | --- |
| `timestamp` | 是 | `2026-08-20T14:30:12.042+08:00` | ISO-8601，使用 `APP_LOG_TIME_ZONE`。 |
| `level` | 是 | `INFO` | `TRACE/DEBUG/INFO/WARN/ERROR`。 |
| `service` | 是 | `carTask` | 来自 `spring.application.name`。 |
| `instance` | 是 | `cartask-01` | 主机名或容器实例 ID，禁止用 IP 作为唯一身份。 |
| `logger` | 是 | `...AccessRecordServiceImpl` | 类名，便于按模块筛选。 |
| `message` | 是 | `人工放行完成` | 使用稳定模板，变量作为结构化字段传入。 |
| `request_id` | 否 | `uuid` | HTTP 请求或任务批次关联 ID。 |
| `trace_id` | 否 | `otel-trace-id` | 已接入链路追踪时填写。 |
| `actor_type` | 否 | `USER/SYSTEM/EXTERNAL` | 只记录分类；用户标识使用脱敏快照。 |
| `actor_id` | 否 | `user:42` | 不记录用户名、手机号等原文。 |
| `operation` | 否 | `access_record.release` | 稳定机器码，不使用 URL 作为操作名。 |
| `target_type` / `target_id` | 否 | `access_record/123` | 目标 ID 采用业务主键，批量任务使用批次 ID。 |
| `duration_ms` | 否 | `184` | 请求、任务或外部调用耗时。 |
| `error_code` / `exception` | 否 | `KEYTOP_TIMEOUT` | 异常类型和稳定错误码；堆栈只在 `error.log`。 |
| `data` | 否 | `{...}` | 仅允许字段白名单，禁止原始请求参数。 |

`data` 的字段白名单按模块维护：车辆号牌最多保留后四位，手机号最多保留后四位；密码、令牌、签名、文件路径、`face_info` 和未知字段一律丢弃。所有日志值先经过统一脱敏器，再进入 JSON 编码器。

## 7. 级别与写入规则

- `ERROR`：请求失败、任务失败、外部依赖不可用、日志文件写入失败。必须带稳定 `error_code`，异常对象作为最后一个参数传入，禁止把堆栈拼到 `message`。
- `WARN`：可恢复的降级、重试、参数被丢弃、慢请求阈值超限、权限拒绝聚合。
- `INFO`：服务启动/停止、任务批次摘要、关键状态转换和配置加载结果，不逐条记录普通查询。
- `DEBUG`：开发或短时排障信息，生产默认关闭；不得用 DEBUG 绕过脱敏规则。
- `TRACE`：默认禁用，仅在本地临时启用并设置自动过期时间。

消息模板使用参数化写法，例如 `logger.info("门禁授权审核完成，controlId={}", id)`；禁止字符串拼接和直接打印对象（对象可能包含敏感字段）。批处理每批记录一条汇总（处理数、成功数、失败数、耗时），失败明细使用受限数量和脱敏 ID。

## 8. 请求上下文与关联 ID

1. `AuditRequestContextFilter` 同时负责日志 MDC：读取合法 UUID 格式的 `X-Request-Id`，否则生成新 UUID，并在响应中回传。
2. 若存在受信任的链路追踪上下文，写入 `trace_id`；没有追踪系统时保持为空，不自行伪造。
3. JWT 解析完成后，只把 `CurrentUserPrincipal` 的用户 ID、角色类别和权限摘要放入 MDC；认证失败使用 `ANONYMOUS`。
4. 定时任务在任务入口生成 `task_run_id`，写入 `request_id`，并在开始、结束、跳过和异常路径清理 MDC。
5. 外部回调通过验签后的来源系统和外部事件 ID 关联；验签失败不得记录签名原文。
6. Filter 使用 `try/finally` 清理 MDC，避免线程池复用导致用户信息串线；异步任务显式传递并在执行后清理上下文。

## 9. Logback 实现建议

在 `src/main/resources/logback-spring.xml` 中配置两个异步文件 Appender：

- `ASYNC_APP` -> `app/app.log`，接收 `INFO` 及以上。
- `ASYNC_ERROR` -> `error/error.log`，仅接收 `WARN` 及以上。
- `CONSOLE` -> 标准输出，生产保留 `WARN` 及以上，开发环境接收 `INFO`。

两个 RollingFileAppender 均使用 `SizeAndTimeBasedRollingPolicy`：

```text
${APP_LOG_DIR:-${user.dir}/logs}/app/app.log
${APP_LOG_DIR:-${user.dir}/logs}/app/app.%d{yyyy-MM-dd}.%i.log.gz
maxFileSize=256MB
maxHistory=${APP_LOG_RETENTION_DAYS:-30}
totalSizeCap=${APP_LOG_TOTAL_SIZE_CAP:-10GB}
```

异步队列容量建议 8192，`neverBlock=false`，队列满时让业务线程短暂等待，避免静默丢失关键日志；同时递增 `logging_queue_rejected_total`。对低价值 DEBUG 可在队列满时丢弃，但必须由独立策略明确配置。编码器输出 UTF-8 JSON，并通过 MDC 转换器写入关联字段。

日志级别按包配置：`top.foxball.cartask=INFO`，第三方库默认 `WARN`。Actuator 只暴露级别调整给受控运维网络，调整操作写入外部运维审计，重启后恢复配置文件级别。

## 10. 集中采集与运行安全

- Fluent Bit/Filebeat 只采集 `app.*.log*` 与 `error.*.log*`，解析 JSON 后附加环境、实例和版本标签。
- 采集器以文件偏移量和 inode 去重，网络不可用时本地缓存最多 1GB；缓存达到上限按级别从 DEBUG 到 INFO 丢弃，并发出告警。
- 生产文件权限为 `0640`、目录 `0750`，运行用户与采集器加入受控日志组；禁止通过 Web 静态资源暴露日志目录。
- 日志平台按环境隔离索引，普通开发账号不得读取生产日志；导出和删除由平台权限控制并留痕。
- 日志中禁止写入 Authorization、Cookie、`Set-Cookie`、数据库连接串和完整 URL 查询参数；统一异常处理器只输出错误码和 `request_id`。

## 11. 监控、告警与降级

至少暴露以下低基数指标：

| 指标 | 标签 | 告警建议 |
| --- | --- | --- |
| `application_log_events_total` | `level`、`logger_group` | ERROR 比例突增。 |
| `application_log_write_errors_total` | `appender`、`error_type` | 任意值持续 1 分钟。 |
| `logging_queue_depth` | `appender` | 超过 70% 持续 5 分钟。 |
| `logging_queue_rejected_total` | `appender` | 出现即告警并检查磁盘/采集器。 |
| `application_slow_operations_total` | `operation` | 5 分钟内超过基线。 |
| `log_disk_usage_ratio` | `instance` | 70%/85%/95% 分级。 |

日志系统故障不应阻塞低风险读请求；高风险写操作仍由现有审计系统执行 fail-closed。普通运行日志写入失败时保留控制台 `ERROR`，返回业务结果不因日志文件故障改变，并触发告警。严禁在异常处理器中递归记录同一日志写入异常。

## 12. 测试与验收

### 12.1 自动化测试

- JSON 每行可独立解析，字段类型和 UTF-8 编码正确；异常堆栈不破坏单行格式。
- 跨午夜滚动生成新日期文件；单日超过 256MB 生成序号文件；重启不覆盖历史文件。
- `APP_LOG_DIR`、时区、留存和总大小配置生效；目录不可写时产生指标与告警。
- Filter 正确生成/透传 `request_id`，并在请求结束清理 MDC；并发请求不会串用 actor 或 trace 字段。
- 脱敏测试确保密码、JWT、Cookie、密钥、完整车牌、人脸字段和未知 JSON 键永不落盘。
- 异步队列满、采集器断网和磁盘达到阈值时，降级顺序与指标符合配置。
- 关键服务日志包含稳定 `operation`、目标摘要和 `duration_ms`，不包含原始请求体。

### 12.2 验收标准

- 在指定 `APP_LOG_DIR` 下，每个自然日都有可读取的 `app` 文件，超过大小会分片并压缩。
- 给定 `request_id` 可以在本地或集中平台还原一次请求的主要处理链路。
- 30 天前日志按策略清理或已完成归档，保全文件不会被删除。
- 日志写入错误、队列拒绝、磁盘不足和集中采集延迟均可通过指标发现。
- 安全抽查无法从运行日志恢复密码、令牌、密钥或完整敏感个人信息。

## 13. 实施顺序

1. 添加 `logback-spring.xml`、日志目录配置和 JSON 编码器，先覆盖现有 `LoggerFactory` 输出。
2. 扩展 `AuditRequestContextFilter` 的 MDC 管理，补充任务/外部回调上下文。
3. 建立脱敏白名单和统一日志模板，逐步替换对象直出、字符串拼接和不稳定消息。
4. 接入 Micrometer 指标、磁盘/队列告警和采集器；在测试环境验证跨午夜、重启和磁盘故障。
5. 生产灰度启用集中采集，确认 30 天本地留存与归档策略后再收紧日志级别。

## 14. 三轮迭代优化记录

### 第 1 轮：从“能写文件”补齐可运维性

初稿只规定了 SLF4J 和每日文件，无法关联请求，也没有错误日志分流、留存上限和目录配置。优化后增加 `APP_LOG_DIR`、按天加按大小滚动、`app/error` 双文件、压缩、留存和实例隔离，满足“以天为单位写入指定路径”的核心要求。

### 第 2 轮：从可运维性补齐安全与一致性

第二轮发现 JSON 日志若直接打印请求对象会泄露密码、JWT、Cookie 和人脸字段，异步队列也可能造成线程上下文串线。优化后加入结构化字段白名单、统一脱敏、MDC `request_id/trace_id`、Filter 清理规则、队列背压和权限隔离，并明确运行日志不替代审计日志。

### 第 3 轮：从安全性补齐故障降级与可验证交付

第三轮聚焦多实例、磁盘耗尽、采集器断网和滚动边界。优化后加入 inode/offset 采集、实例目录隔离、磁盘分级告警、缓存上限、低价值日志丢弃顺序、指标命名、跨午夜/重启/不可写目录测试及验收标准，形成可上线的实施闭环。
