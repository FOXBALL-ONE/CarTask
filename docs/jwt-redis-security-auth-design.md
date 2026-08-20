# JWT + Redis + Spring Security 登录认证校验模块设计

> 本文是**两轮优化后的落地设计**。范围只包含单 JWT 登录、Redis 会话校验和 Spring Security 上下文注入；不实现 refresh token、数据库迁移或认证中心。

## 1. 设计目标

本模块为当前 Kotlin + Spring Boot 项目提供单 token 登录认证能力，满足以下硬约束：

- 登录成功后只签发一枚 JWT；
- token 通过登录响应体返回，并通过响应头 `Authorization` 再返回一次；
- 后续请求只接受 `Authorization: Bearer <token>`；
- token 的**加密保存副本**写入 Redis，会话记录以 `jti` 为索引，Redis TTL 不超过 JWT 的 `exp`；
- 每个请求认证成功后，把当前用户写入 Spring Security 的 `SecurityContext`；
- 后续 Controller、Service 或方法级授权可以通过 `SecurityContextHolder` / `@AuthenticationPrincipal` 获取当前请求用户；
- 不使用 HTTP Session 保存登录状态，保持 `SessionCreationPolicy.STATELESS`；
- token 过期后只能重新登录，不提供续期接口；
- 不新增数据库迁移脚本。本设计只约定认证所需的 Redis 数据结构。

### 1.1 关键决策

| 问题 | 结论 | 原因 |
| --- | --- | --- |
| 是否使用双 token | 否 | 只有 JWT，过期重新登录，避免 refresh rotation 和 token family 复杂度 |
| Redis 保存什么 | 保存 token 的 SHA-256 指纹，并额外保存 AES-GCM 加密后的 token 副本 | 满足“token 保存在 Redis”的要求，同时避免 Redis 泄露即可直接冒用 bearer token |
| 每次请求是否查数据库 | 默认不查 | 通过 Redis 会话、用户 token version 和会话中的用户快照完成低延迟校验 |
| 用户禁用/改密如何立即失效 | 先递增 Redis token version，再提交用户变更 | 先撤销是安全优先；即使业务事务回滚，也只是要求重新登录，不会放行旧 token |
| Redis 故障是否仅凭 JWT 放行 | 否，fail-closed | 否则登出、封禁和退出所有设备会失效 |
| Bearer 解析由谁负责 | 自定义 `OncePerRequestFilter`，复用 `DefaultBearerTokenResolver` | Redis 会话检查是自定义步骤，避免同时启用两套 JWT 过滤器 |

### 1.2 请求头职责边界

服务端可以在**登录响应**中设置 `Authorization` 响应头，但不能替客户端修改未来请求的请求头。客户端必须读取响应体或响应头中的 token，并在后续请求主动写入：

```http
Authorization: Bearer <jwt>
```

因此本文中“写入请求头”统一指客户端请求拦截器/HTTP 客户端在后续请求中写入 `Authorization`；服务端只负责读取和校验该请求头。

### 1.3 明确的 token 语义

本模块只有一种 token，称为 `access token` 仅用于说明用途，不代表还存在 refresh token。token 过期后必须重新登录，不提供续期接口，不在后台静默签发第二枚 token。

JWT 的签名和 `exp` 只能证明 token 的格式、来源及时间范围，不能单独证明 token 仍处于登录状态。因此本模块采用：

```text
JWT 验签 + issuer/audience/时间校验 + Redis 会话校验 + token version 校验
```

`User.status` 和 `User.enabled` 在登录时校验；后续禁用、封禁、改密和角色变更必须先递增 token version，不能只修改数据库而不撤销旧会话。

## 2. 与当前项目的关系和实施状态

当前项目已经具备以下基础：

- `spring-boot-starter-security`；
- `SecurityConfig` 中已经配置 `SessionCreationPolicy.STATELESS`；
- CORS 已允许 `Authorization` 请求头；
- 未匹配公开规则的接口默认要求 `authenticated()`；
- 当前用户实体 `User` 使用 `id`、`username`、`role`、`status`、`enabled` 等字段。

本方案现已完成代码接入：包含 JWT 签发与验签、Redis 会话、token version、登录与登出接口、认证过滤器、SecurityContext 注入、敏感用户变更撤销、登录失败限流和认证测试。旧 refresh 公开路径及不存在的历史认证公开路径已从 `SecurityConfig` 清理。

建议新增的包结构如下：

```text
src/main/kotlin/top/foxball/cartask/
├── authentication/
│   ├── AuthService.kt
│   ├── CurrentUserPrincipal.kt
│   ├── JwtAuthenticationFilter.kt
│   ├── JwtProperties.kt
│   ├── JwtTokenService.kt
│   ├── LoginAttemptLimiter.kt
│   ├── RedisTokenSession.kt
│   ├── RedisTokenSessionRepository.kt
│   └── SecurityRole.kt
├── config/
│   ├── AuthenticationConfig.kt
│   ├── CorsProperties.kt
│   └── SecurityConfig.kt
└── controller/
    └── AuthController.kt
```

文件名和具体包名可以根据现有目录风格调整，但认证过滤器、token 生成、Redis 会话和 Spring Security 配置应保持职责分离。

## 3. 认证协议

### 3.1 登录接口

```http
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "admin",
  "password": "admin"
}
```

登录请求不把账号和密码放在 URL、查询参数或普通日志中。按照项目 Controller 约定，登录参数应直接绑定到服务层命令，例如 `AuthService.LoginCommand`，不要新增名为 `LoginRequest` 的控制器级包装 DTO。

成功响应示例：

```http
HTTP/1.1 200 OK
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Cache-Control: no-store
Pragma: no-cache
```

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_at": "2026-08-19T18:30:00",
    "user": {
      "user_id": 1,
      "username": "admin",
      "role": "ADMIN"
    }
  }
}
```

`Authorization` 响应头是为了方便客户端统一读取；真正用于后续认证的是客户端发出的请求头。服务端不能修改已经到达服务端的登录请求头，客户端必须把登录响应中的 token 保存到内存或安全存储，并在后续请求中主动附加：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

登录成功、登出成功和 401/403/503 认证响应均设置 `Cache-Control: no-store`；登录响应额外设置兼容旧代理的 `Pragma: no-cache`。如果前端通过浏览器跨域读取响应头，需要在 CORS `exposedHeaders` 中增加 `Authorization`。如果前端只读取响应体中的 `data.access_token`，则不依赖响应头，但后续请求仍必须使用 `Authorization` 请求头。

### 3.2 受保护接口

```http
GET /api/users/me
Authorization: Bearer <jwt>
```

请求头规则：

1. Header 名必须是 `Authorization`；
2. Scheme 必须是大小写不敏感的 `Bearer`；
3. token 前后不应有额外引号；
4. 不接受 URL 查询参数中的 token；
5. 不接受 Cookie 中的 token；
6. 不接受自定义 `token`、`access_token` 请求头，避免协议分裂。

### 3.3 登出接口

```http
POST /api/auth/logout
Authorization: Bearer <jwt>
```

登出成功后删除当前 `jti` 对应的 Redis 会话。当前实现要求 Bearer token 先通过完整 JWT + Redis 校验，因此 token 已过期、已登出或 Redis 会话不存在时返回 401；格式错误或签名错误的 token 同样返回 401。这样可以避免把无效凭据误判为成功登出。

### 3.4 不提供 refresh

以下接口不在本模块范围内，也不应继续作为公开认证入口：

```text
POST /api/auth/refresh
POST /api/auth/token/renew
```

token 过期后返回 401，客户端清理本地 token 并跳转登录页。重新登录会生成新的 `jti` 和新的 Redis 会话。

## 4. JWT 载荷设计

### 4.1 标准 Claims

建议使用 HS256 或非对称算法签发 JWT。单体部署或共享密钥管理简单时可使用 HS256；多服务验签、密钥轮换或独立认证服务场景优先使用 RS256/ES256。算法必须固定在配置和验签器中，禁止根据 token header 的 `alg` 动态信任算法。

JWT 载荷建议如下：

| Claim | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `iss` | String | 是 | 签发方，例如 `carTask` |
| `sub` | String | 是 | 用户 ID，使用 `User.id` 转成字符串 |
| `aud` | String | 是 | API 受众，例如 `carTask-api` |
| `jti` | String | 是 | 单枚 token 的 UUID，Redis 会话主键 |
| `iat` | NumericDate | 是 | 签发时间 |
| `exp` | NumericDate | 是 | 过期时间 |
| `username` | String | 是 | 登录用户名；仅作为当前 principal 的快照 |
| `role` | String | 是 | 角色编码，例如 `ADMIN`、`USER` |
| `token_version` | Number | 是 | 用户会话版本，用于撤销该用户全部 token |

`iat` 和 `exp` 是 JWT 协议要求的 NumericDate，应使用 epoch seconds；接口响应中的 `expires_at` 属于业务展示字段，使用项目约定的 ISO-8601 `LocalDateTime` 字符串。`kid` 位于 JWT **header** 而不是 payload，它标识签名密钥 ID，只允许命中服务端维护的 key allowlist。

不要把以下内容写进 JWT：

- 密码、密码哈希、邮箱验证码；
- 完整用户实体或部门/职位对象；
- 高敏感个人资料；
- 可以频繁变化且必须立即一致的权限明细；
- Redis 连接信息或内部路径。

### 4.2 签名密钥轮换

JWT header 必须包含受服务端控制的 `kid`。验签时只在本地配置的签名密钥 allowlist 中按 `kid` 选择密钥，绝不根据 token 自带的 URL、`jku`、`x5u` 等字段下载密钥，也不接受 `alg=none`。

轮换流程：

1. 发布新密钥，并把旧密钥保留为**仅验签**；
2. 新登录只使用新的 `kid` 签发；
3. 等待最长 JWT TTL + clock skew 过去；
4. 删除旧密钥；
5. 如发生密钥泄露，立即删除泄露 `kid`、递增所有用户 token version 或清空认证 Redis 前缀，并要求重新登录。

HS256 场景下签名密钥与 Redis `token_ciphertext` 加密密钥必须分离；不得用同一 secret 同时做 JWT HMAC 和 AES-GCM。

### 4.3 有效期

建议初始配置：

```yaml
shopmall:
  security:
    jwt:
      issuer: ${JWT_ISSUER:carTask}
      audience: ${JWT_AUDIENCE:carTask-api}
      active-signing-key-id: ${JWT_ACTIVE_SIGNING_KEY_ID:local}
      keys:
        local: ${JWT_SIGNING_KEY_LOCAL:}
      ttl: ${JWT_TTL:2h}
      clock-skew: ${JWT_CLOCK_SKEW:30s}
      token-storage-encryption-key: ${JWT_STORAGE_ENCRYPTION_KEY:}
      token-storage-encryption-key-id: ${JWT_STORAGE_ENCRYPTION_KEY_ID:local}
```

生产环境必须通过环境变量或密钥管理系统注入 `JWT_SIGNING_KEY_LOCAL`（或轮换 key）和 `JWT_STORAGE_ENCRYPTION_KEY`，不能把真实密钥提交到仓库。HS256 密钥至少使用 256 bit 的随机值；开发环境固定 token、默认管理员密码和长期 secret 不得在生产环境启用。

`ttl` 与 Redis key 的 TTL 必须使用同一个有效期来源。Redis TTL 应设置为：

```text
jwt.exp - 当前服务器时间
```

不能把 Redis TTL 固定成比 JWT 更长的值，否则会产生无效会话垃圾；也不应比 JWT 短太多，否则合法 JWT 会提前失效。

## 5. Redis 会话模型

### 5.1 Key 设计

每一枚 token 使用独立的 Redis key：

```text
shopmall:auth:jwt:{jti}
```

示例：

```text
shopmall:auth:jwt:4c61c2a0-9e1b-4b7f-9b03-1c682e7b4d3e
```

用户 token version 使用：

```text
shopmall:auth:user:{userId}:token-version
```

登录失败窗口使用规范化用户名的 SHA-256 摘要，避免把账号明文放入 Redis key：

```text
shopmall:auth:login-failure:{usernameSha256Base64Url}
```

Redis key 必须使用固定前缀，避免与验证码、缓存和限流数据冲突。生产环境建议给认证 key 设置独立 ACL 权限，认证服务只能访问 `shopmall:auth:*`。

### 5.2 会话值设计

Redis value 使用 JSON，推荐字段如下：

```json
{
  "user_id": "1",
  "username": "admin",
  "role": "ADMIN",
  "token_version": 3,
  "token_hash": "sha256-of-the-full-jwt",
  "token_ciphertext": "base64(aes-256-gcm(cipher(token, aad=jti|user_id|exp)))",
  "token_encryption_key_id": "redis-token-2026-08",
  "session_schema_version": 1,
  "issued_at": "2026-08-19T16:30:00",
  "expires_at": "2026-08-19T18:30:00",
  "client_ip": "optional",
  "user_agent_hash": "optional"
}
```

`token_ciphertext` 是对完整 JWT 的 AES-256-GCM 加密结果，`token_hash` 用于请求时快速比较。AES-GCM 的 AAD 固定使用 `jti|user_id|exp`，防止密文被替换到另一会话；`token_encryption_key_id` 支持该加密密钥独立轮换；`session_schema_version` 让未来字段变更可以明确兼容范围。Redis 中不保存密码、密码哈希或完整的明文 token。加密密钥必须通过 `JWT_STORAGE_ENCRYPTION_KEY` 注入，且与 JWT 签名密钥分开管理。

请求认证时，过滤器从请求头拿到原始 JWT，先验签并得到 `jti`，再计算 `token_hash` 与 Redis 会话比较；正常请求不需要解密 `token_ciphertext`。遇到未知 `session_schema_version`、未知 `token_encryption_key_id` 或无法解析的会话值，一律拒绝认证并记录安全事件，不能尝试宽松兼容。

如果业务明确要求运维人员能直接在 Redis CLI 看到原始 token，应单独评估风险并由安全负责人批准；不把明文 token 作为默认实现。

### 5.3 写入、TTL 和单 token 撤销

登录时使用 `SET NX EX` 写入会话：

```text
SET shopmall:auth:jwt:{jti} <json> NX EX <jwt-exp-now>
```

`jti` 使用密码学安全的随机 UUID；极低概率的 key 冲突必须重新生成 `jti`，不能覆盖已有会话。

Redis TTL 必须以服务端同一时钟计算：

```text
ttlSeconds = max(1, jwt.exp - currentEpochSeconds)
```

不要把 Redis TTL 写成固定值，也不要让 Redis 会话比 JWT 长。Redis 自动过期后，该 JWT 即使验签成功也必须返回 401。

单 token 登出：

```text
DEL shopmall:auth:jwt:{jti}
```

登出接口应幂等：会话已经不存在时仍可返回成功；但带有格式错误或签名错误的 Bearer token 仍返回 401。

### 5.4 用户级撤销与状态变更顺序

用户 token version 以 Redis `INCR` 维护：

```text
INCR shopmall:auth:user:{userId}:token-version
```

登录前使用 `SETNX ... 0` 初始化 version；过滤器读取不到 version 时，若对应 token session 仍存在，应按认证状态不可用处理，不把缺失当成 `0` 放行。这样可以避免 version key 被误删后放行旧 token。

以下操作必须先递增 version，再执行数据库变更：

- 修改密码；
- 管理员禁用或封禁用户；
- 修改角色或关键权限；
- 退出所有设备；
- 账号被盗后的强制重新登录。

顺序约定：

```text
Redis INCR token-version 成功
    -> 执行数据库变更
    -> 数据库失败时允许用户重新登录，但不得回滚 Redis version
```

如果 Redis `INCR` 失败，禁止继续执行会影响认证安全的数据库变更，并返回 503。这样保证“数据库已经禁用但旧 token 仍可用”的窗口不会被业务代码主动扩大。

Redis 重启或丢失认证 key 后，所有缺失会话的 JWT 都失效；客户端必须重新登录。认证 key 不应与普通缓存使用可随意淘汰的策略，建议使用持久化、监控和独立容量预算。

### 5.5 原子读取与并发边界

认证过滤器不应分别执行 `GET session`、`GET token-version` 后再在 Java 代码中拼接判断。推荐由 Redis Lua 脚本或等价的原子命令组合完成以下操作：

```text
输入：jti、userId、tokenHash、tokenVersion、now
原子读取：session key + user token-version key
原子比较：session.user_id、session.token_hash、session.token_version、session.expires_at、当前 token-version
输出：VALID / SESSION_MISSING / VERSION_MISSING / MISMATCH
```

这样可以避免两个 `GET` 之间发生版本递增而得到不一致快照。原子读取完成后，另一个请求仍可能立刻登出或撤销该 token；这种“已通过校验的在途请求可能完成一次”是无状态 HTTP 的正常竞态边界。设计保证撤销完成后的**新认证请求**不再通过，不承诺中断已进入业务链的请求。

注销或用户级撤销不需要扫描 token key：删除当前 `jti` 或递增 version 即可。若需要审计撤销动作，写入独立的结构化审计日志，不要依赖 Redis keyspace notification 作为唯一来源。

### 5.5 Redis 故障策略

默认采用 fail-closed：Redis 不可用时，受保护请求返回 503，不允许仅凭 JWT 放行。认证失败和基础设施故障必须区分：

- token 缺失、格式错误、验签失败、会话不存在或版本不一致：401；
- Redis 连接超时、命令失败、序列化失败、认证状态无法确定：503；
- 公开接口可以继续按公开规则访问，但不能绕过认证过滤器访问受保护资源。

可观测性要求：

- 记录 Redis 连接失败、超时和序列化失败；
- 日志只记录 `jti` 的脱敏摘要、用户 ID 和请求路径，不记录完整 JWT、密文或签名 secret；
- 对 Redis 连接、命令和获取连接分别设置超时；
- 认证失败、Redis 故障、token 撤销分别使用不同的监控指标；
- 503 响应可以附带 `Retry-After`，但不能泄露 Redis 地址。

## 6. 登录签发流程

```mermaid
sequenceDiagram
    participant C as Client
    participant A as AuthController/AuthService
    participant U as UserRepository
    participant P as PasswordEncoder
    participant J as JwtTokenService
    participant R as Redis

    C->>A: POST /api/auth/login + username/password
    A->>U: 按 username 查询 User
    A->>P: matches(password, passwordHash)
    A->>A: 校验 enabled=true、status=Activity
    A->>R: 读取用户 token_version
    A->>J: 生成 jti、签发 JWT
    A->>R: 保存 shopmall:auth:jwt:{jti}，TTL=exp-now
    A-->>C: body.data.access_token + Authorization 响应头
    C->>C: 保存 token
```

登录失败统一返回 401，避免区分“用户不存在”和“密码错误”，防止账号枚举。用户被禁用、封禁或状态不是 `Activity` 时同样拒绝登录。

签发 JWT 前应确保 `User.id` 已存在。新注册用户不能在数据库事务尚未提交且没有确定 ID 时签发 token。

## 7. 每次请求的认证流程

```mermaid
sequenceDiagram
    participant C as Client
    participant F as JwtAuthenticationFilter
    participant J as JwtTokenService
    participant R as Redis
    participant S as SecurityContextHolder
    participant E as Endpoint

    C->>F: Authorization: Bearer <jwt>
    F->>J: 解析并验证签名、算法、iss、aud、iat、exp、jti
    F->>R: GET shopmall:auth:jwt:{jti}
    R-->>F: 会话记录或不存在
    F->>R: GET shopmall:auth:user:{userId}:token-version
    F->>F: 比较 user_id、token_hash、token_version、expires_at
    F->>S: 写入 Authentication
    F->>E: 继续过滤器链
    E->>S: 获取当前用户和权限
    E-->>C: 业务响应
```

### 7.1 过滤器判定矩阵

| 请求头 / 校验结果 | 过滤器动作 | 最终结果 |
| --- | --- | --- |
| 无 `Authorization` | 不创建 Authentication，继续链 | 公开接口继续；受保护接口由 Spring Security 返回 401 |
| scheme 不是 `Bearer` | 立即委托 401 | 401 |
| Bearer token 为空或格式非法 | 立即委托 401 | 401 |
| JWT 签名、算法、issuer、audience、时间校验失败 | 立即委托 401 | 401 |
| JWT 合法但 Redis session 不存在 | 立即委托 401 | 401 |
| Redis session 与 JWT 的 user/jti/hash/version 不一致，或 version key 缺失 | 立即委托 401 | 401 |
| Redis 连接、命令或序列化失败 | 不继续业务链，委托基础设施错误响应 | 503 |
| 全部校验成功 | 写入 Authentication 后继续链 | 由授权规则决定 200/403 |

带有 `Authorization` 但认证失败时不能“清空上下文后继续”，否则公开接口规则可能让非法 token 被误当作匿名请求放行。没有请求头和带有非法请求头必须严格区分。

### 7.2 过滤器实现边界

过滤器应继承 `OncePerRequestFilter`，并放在授权检查之前。Bearer 解析复用 `DefaultBearerTokenResolver`，不要手写多个正则版本，也不要同时注册自定义 JWT 过滤器和 Spring Security Resource Server 的默认 Bearer 过滤器。

推荐职责顺序：

```text
提取 Bearer token
    -> JwtTokenService 验签和 Claims 校验
    -> RedisTokenSessionRepository 校验会话、token_hash、token_version
    -> 构造 Authentication
    -> 写入 SecurityContext
    -> filterChain.doFilter
```

过滤器中不实现登录密码校验、不查询并装载 JPA 实体、不构造 Controller 响应 DTO。用户展示数据需要时由业务服务按 principal.userId 查询。

### 7.3 错误处理

过滤器不能把所有异常都包装成 401。建议定义两个明确的错误出口：

- `InvalidBearerTokenException`、JWT 校验异常、Redis 会话不存在：交给 `AuthenticationEntryPoint`，返回 401；
- `RedisConnectionFailureException`、Redis 命令超时或状态无法确认：交给认证基础设施错误处理器，返回 503。

错误响应应复用项目现有的 JSON 响应格式，并设置 `Cache-Control: no-store`，避免浏览器或代理缓存认证失败结果。

## 8. Spring Security 上下文设计

### 8.1 Principal

建议定义只读的当前用户 principal：

```kotlin
data class CurrentUserPrincipal(
    val userId: Long,
    val username: String,
    val role: String,
    val tokenId: String,
) {
    val authorities: List<SimpleGrantedAuthority>
        get() = listOf(SimpleGrantedAuthority("ROLE_$role"))
}
```

实际实现可以让该类型实现 `UserDetails`，也可以直接作为 `Authentication.principal`。不建议把 JPA `User` 实体直接放入 SecurityContext，避免懒加载、密码哈希泄露和跨层耦合。

角色映射规则：

```text
User.role = ADMIN  -> ROLE_ADMIN
User.role = USER   -> ROLE_USER
```

如果数据库中的 role 已经包含 `ROLE_` 前缀，必须统一规范化，避免出现 `ROLE_ROLE_ADMIN`。

### 8.2 写入 SecurityContext

认证成功后创建认证对象，并写入当前请求线程绑定的上下文：

```kotlin
val principal = CurrentUserPrincipal(
    userId = session.userId,
    username = session.username,
    role = session.role,
    tokenId = claims.jti,
)

val authentication = UsernamePasswordAuthenticationToken(
    principal,
    null,
    principal.authorities,
).apply {
    details = WebAuthenticationDetailsSource().buildDetails(request)
}

val context = SecurityContextHolder.createEmptyContext()
context.authentication = authentication
SecurityContextHolder.setContext(context)
filterChain.doFilter(request, response)
```

要点：

- credentials 必须设置为 `null`，不能把 JWT 或密码放在 `Authentication.credentials` 中；
- 使用 `createEmptyContext()`，不要直接复用可能残留的上下文；
- 认证过滤器必须在业务 Controller 之前执行；
- Spring Security 的请求过滤器负责在请求结束时清理上下文；
- `STATELESS` 只表示不使用 HTTP Session，不影响当前请求内使用 `SecurityContext`；
- 如果业务启动异步线程，必须使用 Spring Security 的 `DelegatingSecurityContextExecutor`、`DelegatingSecurityContextRunnable` 等方式显式传播上下文，不能假设普通线程池会自动继承。

### 8.3 在后续操作中获取当前用户

Controller 中推荐使用 `@AuthenticationPrincipal`：

```kotlin
@GetMapping("/me")
fun me(
    @AuthenticationPrincipal principal: CurrentUserPrincipal,
): ResponseEntity<Response> {
    data class Response(
        val id: Long,
        val username: String,
        val role: String,
    )

    val rs = Response(principal.userId, principal.username, principal.role)
    return responseBuilder.ok().data(rs).build()
}
```

需要在 Service 或其他组件中获取时：

```kotlin
val authentication = SecurityContextHolder.getContext().authentication
val principal = authentication?.principal as? CurrentUserPrincipal
    ?: throw AuthenticationCredentialsNotFoundException("当前请求未认证")
val currentUserId = principal.userId
```

只有在多个调用点确实需要相同的独立抽象时，才提取当前用户访问器；一次性逻辑保留在调用位置，遵循项目的 private function 约定。

不要从请求参数、表单字段或客户端自传的 `user_id` 判断当前操作者。资源归属和审计用户必须以 `SecurityContext` 中的 principal 为准。

## 9. 组件职责

### 9.1 `AuthController`

负责：

- 接收登录和登出 HTTP 请求；
- 按项目 Controller 规范直接声明 HTTP 输入；
- 调用 `AuthService`；
- 在 endpoint 方法内部声明响应 `data class Response`；
- 在 endpoint 方法内部映射登录结果并构造 `val rs = Response(...)`；
- 通过注入的 `ResponseBuilder` 返回统一响应；
- 登录成功时设置 `Authorization` 响应头。

不负责：

- 解析 JWT；
- 访问 Redis；
- 直接操作 `SecurityContextHolder`；
- 把密码或完整 token 写日志。

### 9.2 `AuthService`

负责：

- 查询用户；
- 使用 `PasswordEncoder` 验证密码；
- 校验用户 `enabled` 和 `status`；
- 读取用户 token version；
- 调用 `JwtTokenService` 签发 token；
- 保存 Redis token session；
- 执行当前 token 登出及用户全量撤销。

密码仍使用项目现有的 Argon2 `PasswordEncoder`，JWT 只负责登录后的身份承载，不能替代密码哈希。

### 9.3 `JwtTokenService`

负责：

- 生成 `jti`；
- 签发 JWT；
- 验证 JWT 签名和标准 Claims；
- 校验 issuer、audience、算法、时间和必填字段；
- 计算完整 JWT 的 SHA-256 指纹；
- 不负责访问 Redis 和查询用户数据库。

### 9.4 `RedisTokenSessionRepository`

负责：

- 按 `jti` 保存、查询和删除 token session；
- 按用户 ID 获取/递增 token version；
- 设置与 JWT `exp` 一致的 TTL；
- 处理 Redis 序列化和超时；
- 不负责 JWT 签名验证。

### 9.5 `JwtAuthenticationFilter`

负责：

- 从 `Authorization` 请求头提取 Bearer token；
- 调用 `JwtTokenService` 验证 token；
- 调用 Redis session repository 验证白名单和 token version；
- 构造 `Authentication`；
- 写入 `SecurityContext`；
- 认证失败时委托统一的 401 处理。

过滤器中不应实现登录密码校验，也不应直接拼接业务响应 DTO。

## 10. Spring Security 配置要点

现有 `SecurityConfig` 可按以下原则调整：

```kotlin
@Bean
fun securityFilterChain(
    http: HttpSecurity,
    jwtAuthenticationFilter: JwtAuthenticationFilter,
): SecurityFilterChain {
    http
        .csrf { it.disable() }
        .cors(Customizer.withDefaults())
        .sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }
        .securityContext {
            it.securityContextRepository(NullSecurityContextRepository())
        }
        .requestCache { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/api/auth/login",
                "/api/auth/logout",
                "/error",
                "/actuator/health",
                "/actuator/info",
            ).permitAll()
            it.requestMatchers("/admin/api/**").hasRole("ADMIN")
            it.anyRequest().authenticated()
        }
        .addFilterBefore(
            jwtAuthenticationFilter,
            UsernamePasswordAuthenticationFilter::class.java,
        )

    return http.build()
}
```

上面是结构示意，实际响应体仍应复用项目已有的 401/403 JSON 写法。`NullSecurityContextRepository` 与禁用 request cache 用于明确保证当前认证不会被保存到 HTTP Session；它们不能替代 `SecurityContextHolder`，后者仍在当前请求的过滤器链内可用。必须保留现有业务接口的公开规则，并根据实际需求调整，不要用示例配置覆盖整个授权矩阵。

CORS 配置如果要让浏览器读取登录响应头，增加：

```kotlin
exposedHeaders = listOf(
    "Authorization",
    "Retry-After",
    "X-RateLimit-Limit",
    "X-RateLimit-Remaining",
)
```

请求头 `allowedHeaders` 中已经存在 `Authorization`，无需再引入自定义 token header。此方案不使用认证 Cookie，因此认证相关的浏览器跨域请求不需要 `allowCredentials=true`；生产环境应把 `allowedOriginPatterns = listOf("*")` 收敛为明确的前端 Origin allowlist。若项目其他功能仍依赖跨域 Cookie，必须单独评估，不能为了 JWT 登录放宽整个应用的跨域凭据策略。

## 11. 配置和依赖

### 11.1 Gradle 依赖

在 Spring Boot BOM 管理范围内增加 Redis 和 Spring Security 的 JOSE/JWT 模块。因为本文明确采用自定义 `OncePerRequestFilter` + `DefaultBearerTokenResolver`，不要再额外启用 Resource Server 默认 Bearer 过滤器：

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("org.springframework.security:spring-security-oauth2-resource-server")
implementation("org.springframework.security:spring-security-oauth2-jose")
```

这两个 Spring Security 模块分别提供 Bearer token 解析相关类型和 Nimbus JOSE/JWT 编解码能力。具体模块的传递依赖应以当前 Spring Boot BOM 解析结果为准；不手工指定 Spring Security 版本，也不同时引入 JJWT、java-jwt 等第二套 JWT 实现。

### 11.2 应用配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      timeout: ${REDIS_TIMEOUT:2s}

shopmall:
  security:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:8090,http://127.0.0.1:8090}
      allowed-origin-patterns: ${CORS_ALLOWED_ORIGIN_PATTERNS:}
      allow-credentials: ${CORS_ALLOW_CREDENTIALS:true}
    login-rate-limit:
      enabled: ${LOGIN_RATE_LIMIT_ENABLED:true}
      max-attempts: ${LOGIN_RATE_LIMIT_MAX_ATTEMPTS:5}
      window: ${LOGIN_RATE_LIMIT_WINDOW:15m}
    jwt:
      issuer: ${JWT_ISSUER:carTask}
      audience: ${JWT_AUDIENCE:carTask-api}
      active-signing-key-id: ${JWT_ACTIVE_SIGNING_KEY_ID:local}
      keys:
        local: ${JWT_SIGNING_KEY_LOCAL:}
      ttl: ${JWT_TTL:2h}
      clock-skew: ${JWT_CLOCK_SKEW:30s}
      token-storage-encryption-key: ${JWT_STORAGE_ENCRYPTION_KEY:}
      token-storage-encryption-key-id: ${JWT_STORAGE_ENCRYPTION_KEY_ID:local}
```

生产环境应使用独立 Redis database 或明确的 key prefix，避免与缓存、验证码和限流数据互相覆盖。密码、JWT signing secret 与 Redis token 加密密钥不能写入 `application.yaml` 的真实默认值。应用启动时必须校验 active signing key、验签 key allowlist、JWT storage encryption key 和 key ID 已完整配置；任一缺失均终止启动。

## 12. 异常和响应规则

| 场景 | HTTP 状态 | 说明 |
| --- | ---: | --- |
| 用户名或密码错误 | 401 | 不区分具体原因 |
| 用户已禁用或封禁 | 401 | 不泄露账户状态细节 |
| 登录失败达到窗口阈值 | 429 | 返回 `Retry-After`，用户名存在性仍不泄露 |
| 缺少 Bearer token 且访问受保护接口 | 401 | 由认证入口点返回统一 JSON |
| Bearer token 格式错误 | 401 | 不继续访问业务接口 |
| JWT 签名、issuer、audience 或时间校验失败 | 401 | token 不可信或已过期 |
| JWT 合法但 Redis session 不存在 | 401 | 已登出、已撤销或 Redis TTL 到期 |
| JWT 与 Redis 用户、版本或 hash 不一致 | 401 | 会话绑定校验失败 |
| 已认证但角色不足 | 403 | 由 access denied handler 返回统一 JSON |
| Redis 不可用 | 503 或统一认证失败 | 默认 fail-closed，不仅凭 JWT 放行 |

错误响应中不能包含：

- 原始 JWT；
- JWT payload；
- Redis key 中的完整 `jti`（可记录脱敏摘要）；
- 密码匹配的具体失败原因；
- 数据库或 Redis 内网地址。

## 13. 安全边界

1. JWT 是 bearer credential，任何拿到 token 的主体都可以在有效期内使用它，必须通过 HTTPS 传输。
2. 前端优先将 token 保存在内存；若必须持久化，应使用平台安全存储，避免把 token 暴露给不可信脚本。
3. 不把 token 放到 URL、查询参数、日志、异常消息、埋点和 Referer 可传播的位置。
4. JWT secret 必须轮换。发生密钥轮换时，应支持旧 key 在短暂过渡期验签，或通过密钥版本标识进行切换；轮换策略不能依赖重新部署所有客户端。
5. Redis 必须启用认证、网络隔离和最小权限；生产环境使用 TLS，并限制只有应用服务可以访问认证 key prefix。
6. 认证过滤器不能因为 Redis 短暂不可用而降级为“仅验证 JWT”。否则登出和封禁会失效。
7. JWT 中的 role 是签发时快照。角色变更后必须递增 token version 或删除该用户全部 token，避免旧角色继续生效。
8. 如果一个用户允许多设备同时登录，每台设备产生不同 `jti`；登出只删除当前 `jti`，退出所有设备则递增用户 token version。
9. 不实现 refresh token，因此不需要 refresh cookie、refresh rotation、token family 或双 token 黑名单。
10. 登录接口使用 Redis 按规范化用户名统计失败次数，默认 15 分钟最多 5 次；达到阈值返回 429 和 `Retry-After`。生产环境还应基于网关增加 IP/设备维度限流和监控告警。
11. JWT 和 Redis session 中的角色只接受 `ADMIN`、`USER`、`CUSTOMER` 白名单，并统一去除 `ROLE_` 前缀、转换为大写，拒绝任意字符串形成动态 authority。

## 14. 测试设计

### 14.1 `JwtTokenService` 单元测试

- 可以签发包含 `iss`、`aud`、`sub`、`jti`、`iat`、`exp`、`role` 的 token；
- 正确 token 可以验签；
- 修改 payload 或签名后验签失败；
- issuer 或 audience 不匹配失败；
- 过期 token 失败；
- 缺少 `jti`、`sub` 或 `exp` 失败；
- 算法不在允许列表中失败；
- token hash 对同一字符串稳定，对不同字符串不同。

### 14.2 Redis 会话测试

- 登录后生成 `shopmall:auth:jwt:{jti}`；
- Redis key TTL 与 JWT 剩余有效期一致；
- 删除 key 后，同一 JWT 立即返回 401；
- token version 递增后，旧 JWT 返回 401，新登录 JWT 可以使用；
- Redis value 不包含密码、密码哈希或明文 JWT，且 token 密文包含正确 AAD、加密 key ID 和 schema version；
- `jti` 冲突时不会覆盖已有会话；
- 原子校验脚本对 session/version 不一致、version key 缺失和会话已过期返回预期结果；
- Redis 序列化、超时和不可用时走 fail-closed，并区分 401 与 503。

### 14.3 Spring Security 集成测试

至少覆盖：

- `/api/auth/login` 无 token 可以访问；
- 公开 GET 接口无 token 可以访问；
- 受保护接口无 token 返回 401；
- 受保护接口带有效 JWT 且 Redis session 存在时返回 200；
- 业务代码可以通过 `SecurityContextHolder` 获取正确的 `CurrentUserPrincipal.userId`；
- `@AuthenticationPrincipal` 获取到的用户与 JWT/Redis 记录一致；
- `ADMIN` 可访问管理端，普通用户返回 403；
- 已登出的 JWT 返回 401，非法 Bearer token 不会作为匿名请求继续访问公开接口；
- 用户被禁用/封禁或 token version 变化后旧 JWT 返回 401；
- Redis 不可用返回 503，而不是误报 401 或仅凭 JWT 放行；
- token 不会被保存到 HTTP Session，request cache 也不会创建认证相关 Session；
- 请求结束后 SecurityContext 不会泄漏到下一个请求或线程；
- 并发执行“认证校验 + 登出/递增 token version”时，新请求在撤销完成后必定被拒绝；允许已通过认证的在途请求按竞态边界完成。

### 14.4 Controller 测试

- 登录成功时响应体包含 `access_token`，响应头包含 `Authorization: Bearer ...` 和 `Cache-Control: no-store`；
- 跨域响应可读取 `Authorization`（如果前端依赖响应头）；
- 登录、登出及认证错误响应均不被浏览器或中间代理缓存；
- 密码错误不会返回“用户不存在”或“密码错误”的细分信息；
- 登出只删除当前 token，不影响同一用户其他设备的 token；
- 不存在 `/api/auth/refresh` 的成功续期行为。

## 15. 实施顺序

1. 增加 Redis 和 JWT 相关依赖，确认版本由 Spring Boot BOM 管理。
2. 增加 `JwtProperties`，启动时校验生产环境 secret 不为空且长度符合要求。
3. 实现 `JwtTokenService`，先完成签发、验签和 claims 校验。
4. 实现 Redis session repository，完成 TTL、单 token 删除和 token version。
5. 实现 `AuthService.login` 和 `AuthService.logout`。
6. 实现 Redis 原子校验脚本，并在 `JwtAuthenticationFilter` 中于授权判断前写入 SecurityContext。
7. 调整 `SecurityConfig`：保留现有公开接口规则，删除 refresh 相关规则，保留 STATELESS，配置 `NullSecurityContextRepository` 并禁用 request cache。
8. 在 CORS 中按需暴露 `Authorization` 响应头，并把生产 Origin 收敛为 allowlist。
9. 增加单元、Redis 集成、并发撤销和 Spring Security 集成测试。
10. 本地使用 Redis 验证登录、请求头认证、登出、改密撤销、角色校验与 Redis 故障 503。

## 16. 验收标准

- 登录成功只签发一枚 JWT，不返回 refresh token，不设置 refresh cookie；响应体字段为 `data.access_token` 和 `data.expires_at`；
- 登录成功后 Redis 中存在该 token 对应的会话记录，TTL 不超过 JWT 的 `exp`，会话不含明文 JWT；
- 客户端后续请求使用 `Authorization: Bearer <token>`；
- JWT 签名正确但 Redis session 被删除时，受保护接口仍返回 401；
- 认证通过后，`SecurityContextHolder.getContext().authentication` 非空，且 principal 含正确用户 ID、用户名和角色；
- 同一请求后续 Service 能获取当前用户，不能被请求体中的伪造 `user_id` 覆盖；
- `ADMIN` 和普通用户的权限由 Spring Security 授权规则决定；
- 登出只撤销当前 `jti`；退出所有设备通过 token version 撤销该用户旧 token；
- JWT 过期后只能重新登录，不能通过 refresh 接口续期；
- Redis 故障时返回 503，且不降级为仅依赖 JWT 的放行模式；
- 认证状态使用原子 session/version 校验；撤销完成后的新请求不会再被认证；
- 不修改数据库迁移脚本，不把密码、原始 token、密文或密钥写入日志。

## 17. 本项目已处理的旧配置

仓库中仍保留 `DevTokenProperties.kt` 的历史说明，但当前认证配置没有注册固定 token 管理器，也没有固定 token 认证旁路；`JwtAuthenticationFilter` 对所有 Bearer token 统一执行 JWT + Redis 校验。因此不能仅通过增加配置项恢复固定 token。

当前已完成的处理：

- 新认证过滤器只在 Spring Security chain 中执行，未启用 Servlet 容器重复注册；
- `/api/auth/refresh` 不再作为公开续期接口；
- 管理端、用户管理和文件上传路由不再被错误地全部 `permitAll`；文件 GET 元数据和下载仍按当前业务规则公开，若文件包含私有数据必须移除对应 `permitAll` 规则。
- Redis 不可用时不会回退到仅验证 JWT；
- 如未来确需本地固定 token，必须新增仅测试 profile 的显式实现，并确保它仍经过 Redis 会话校验，不能在生产环境形成旁路。

## 18. 两轮优化记录

### 第一轮：认证状态与失败语义

- 明确“服务端写登录响应头”和“客户端写后续请求头”的职责边界；
- 将 Redis 会话升级为 token 指纹 + 加密 token 副本，避免保存明文 bearer token；
- 采用 `SET NX EX`、严格 TTL、token version 和先撤销后变更的安全顺序；
- 将 token 无效（401）与 Redis 不可用（503）分离；
- 明确非法 Bearer token 不能退化成匿名访问。

### 第二轮：可运维性、并发与无状态保证

- 加入 `kid` allowlist、签名密钥轮换和 Redis token 加密密钥独立轮换；
- 为加密 token 增加 AAD、key ID 与会话 schema version；
- 使用 Redis 原子 session/version 校验，明确撤销与在途请求的竞态边界；
- 明确配置 `NullSecurityContextRepository` 和禁用 request cache，避免意外创建或保存 HTTP Session；
- 收紧用户、管理端和文件上传接口的授权矩阵，移除不存在或危险的公开认证路径，并明确文件 GET 接口的公开范围；
- 将 CORS Origin 改为配置化 allowlist，禁止“携带凭据 + 全局通配 Origin”；
- 增加角色白名单和 Redis 登录失败窗口限流；
- 增加 `Cache-Control: no-store`、认证过滤器、JWT 验签和角色规范化测试。

## 19. 当前验证结果与后续运行要求

- `compileTestKotlin` 已通过；
- JWT 签发/验签、篡改、未知 `kid`、SecurityContext 注入、Redis 故障 503、非法 Bearer 和角色白名单测试已通过；
- 为解除全项目编译阻塞，将部门业务批量创建方法从与通用 CRUD 擦除冲突的 `createBatch(List<CreateCommand>)` 改为 `createDepartments(...)`；HTTP 接口路径未改变；
- 上线前仍必须使用真实 Redis、数据库和生产密钥执行登录、登出、改密撤销、角色变更撤销、并发撤销及 CORS 集成验证；
- `JWT_SIGNING_KEY_LOCAL` 必须是至少 32 字节随机值的 Base64，`JWT_STORAGE_ENCRYPTION_KEY` 必须是 32 字节随机值的 Base64，不能使用仓库内默认明文密钥。
