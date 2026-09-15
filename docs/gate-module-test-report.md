# 门禁模块 · 真机测试执行报告

> 本文是 `docs/gate-module-test-plan.md` 的**实测记录**。方案只做设计，这里记录实际跑出来的结果、
> 与方案预期不符的地方、以及缺陷的根因与修复建议。
>
> - 执行时间：2026-09-15 20:50 ~ 21:20（CST）
> - 后端：`CarTaskApplication`（IDEA 运行配置，`http://127.0.0.1:8080`）
> - 数据库：`jdbc:postgresql://192.168.1.95:5432/cartesktest`
> - Redis：`192.168.1.95:6379` db5
> - 后端日志：`%LOCALAPPDATA%\JetBrains\IntelliJIdea2026.2\tmp\ij_run__CarTaskApplication_13165961012407857783.log`
> - 用例脚本与原始结果：`docs/gate-test/`（`all_results.json` 是全部 151 条的汇总）

---

## 0. 结论速览

> **2026-09-15 补充**：本文记录的缺陷已按第 9 节的优先级完成修复，并在同一环境上做了实机复测
> （`docs/gate-test/t_regress.py` **39 条**、`t_regress2.py` **19 条**，**全通过**）。
> 修复内容、验证方式与新增的回归用例见 **第 10 节**；之后又对这次改动做了一轮代码审查，
> 又修掉 4 条（含一条是第一轮修复引入的回归），见 **第 10.7 节**。
> 下面第 1~8 节保留的是**修复前**的实测记录，作为问题证据。

| 项 | 结果 |
| --- | --- |
| 用例总数 | **151**（方案 5.1~5.12 全覆盖，并在实现行为与方案预期不符处补充了实测用例） |
| 通过 | **140** |
| 失败 | **2**（均为真实缺陷，非脚本问题） |
| 仅记录不断言 | 9（刻画性用例：记录真实行为，方案预期与实测不同） |
| 新发现缺陷 | **9 条**（F1~F9），其中 **F1 是「功能完全不可用且静默成功」的严重缺陷** |
| 方案缺口复核 | G1~G8 逐条证实/修正，见第 5 节 |
| 方案本身需要修订 | 5 处（路径、预期状态码、预期条数），见第 6 节 |
| 未能执行 | 2 条（TC-XLS-15 万行导入、原设计的 TC-FACE-08），见第 7 节 |
| 修复状态 | F1~F6、F9 已修复并复测；G1/G2/G4 已按决策处理；代码审查轮又修掉 4 条（R1~R5）；F7/G5 按决策留存报告（见第 10 节） |

**最需要立刻处理的两件事**（**均已修复**）：

1. ~~**F1**：工作部门切换对**所有可切换的角色**都是「报成功、实际没生效」~~ → 已修复，见第 10 节。
2. ~~**F6**：`CaptchaService.kt` 里未提交的 `println("验证码为：$code")` 已实测把验证码明文写进日志
   （本次后端日志里出现 **49 次**）~~ → 已删除，见第 10 节。

---

## 1. 结果总览（按用例组）

| 用例组 | 条数 | 通过 | 失败 | INFO | 备注 |
| --- | --- | --- | --- | --- | --- |
| 5.1 登录闸门与工作部门 `TC-AUTH-*` | 12 | 11 | **1** | 0 | 唯一失败项 = F1 |
| 5.2 列表与详情 · 范围可见性 `TC-LIST-*` | 16 | 16 | 0 | 0 | 含「范围外 vs 不存在响应逐字一致」 |
| 5.3 单条录入 `TC-CREATE-*` | 17 | 16 | **1** | 0 | 失败项 = F2（413 而非 400） |
| 5.4 编辑与状态回退 `TC-UPDATE-*` | 10 | 10 | 0 | 0 | 事务回滚、无变化不打回都成立 |
| 5.5 审核 `TC-REVIEW-*` | 13 | 13 | 0 | 0 | 含 200 条批量上限与整批回滚 |
| 5.6 删除申请与审批 `TC-DEL-*` | 15 | 15 | 0 | 0 | 含「申请可见 ≠ 删除授权」 |
| 5.7 导入导出 `TC-XLS-*` | 19 | 19 | 0 | 0 | 万行导入未跑，见第 7 节 |
| 5.8 人脸文件 `TC-FACE-*` | 13 | 13 | 0 | 0 | 含 relink/unlink 生命周期 |
| 5.9 人员进出记录 `TC-PR-*` | 9 | 7 | 0 | 2 | INFO = 后端根本没有该导出接口 |
| 5.10 审计留痕 `TC-AUDIT-*` | 9 | 8 | 0 | 1 | INFO = 方案里的审计路径是 404 |
| 5.11 门禁授权 `TC-AC-*` | 9 | 3 | 0 | 6 | INFO = 该模块**完全没有接数据范围** |
| 5.12 越权与对抗 `TC-ATK-*` | 9 | 9 | 0 | 0 | 20 条对抗设想无一越过范围或状态机 |
| **合计** | **151** | **140** | **2** | **9** | |

### 1.1 对抗性用例的结论

方案第 5.12 节的 20 条对抗设想**没有一条能越过范围或状态机**。核心防线经实测确认有效：

- 跨部门按 ID 读/改/审 → 一律 400「人员不存在」，且与「真不存在」**响应体逐字一致**（`TC-ATK-19` 用 40 次探测验证，只有一种响应形态）。
- 批量审核夹带范围外成员 → 400 且**整批回滚**（`TC-REVIEW-06`）。
- 手工选部门参数越权读 → 先裁范围再过滤，`total=0`（`TC-LIST-07`）。
- fail-closed 生效：`department_code IS NULL` 且 `dept` 不可解析的行对 A2/B1 均不可见（**`TC-LIST-03` 实测 total=3，方案里那个 ⚠️ 担心的问题不存在**）。
- 部门管理自我提权（`roleIds=[2]`）、改自己的管理范围 → 均 403。
- 改其他部门用户的手机号 → 403「无权操作其他部门的用户」。
- 图形验证码：不带 token → 401「请输入验证码」；同一 token 连续答错 → 「验证码错误次数过多，请刷新」。

---

## 2. 缺陷清单

### F1 ·【严重】工作部门切换永远静默失败（报了成功，实际没切换）

| 项 | 内容 |
| --- | --- |
| 用例 | `TC-AUTH-06b`（失败）、补充验证（DEPT_ADMIN 同样复现） |
| 现象 | `PUT /api/auth/working-department?department_id=2` 返回 **200「工作部门已切换」**，但下一次请求里 `working_department_id` 仍是原值、`scope` 也没变。 |
| 实测证据 | A1（平台管理）切到 D1 → PUT 200，紧接着 `GET /api/auth/session` 仍是 `scope=ALL, working_department_id=null`。<br>另建一个管理 `[D1, D2]` 的部门管理账号切到 D2 → PUT 200，会话仍 `working_department_id=1`，且**仍能看到 D1 的 215 条门禁人员**。<br>用 `MONITOR` 抓 Redis 命令，切部门时只有 `WATCH shopmall:auth:jwt:<jti>` → `UNWATCH`，**没有 `MULTI`/`SET`/`EXEC`**。 |
| 根因 | 两处叠加：<br>**① 时区不一致**：`JwtTokenService.kt:112-113` 把会话时间戳存成 **UTC** 的 `LocalDateTime`（`LocalDateTime.ofInstant(now, ZoneOffset.UTC)`）；而 `RedisTokenSessionRepository.kt:142` 用 `Duration.between(LocalDateTime.now(), session.expiresAt)` 算剩余 TTL。宿主机时区是 UTC+8，`LocalDateTime.now()` 比 `expiresAt` 大 8 小时 → `ttl.isNegative` 成立 → 直接 `unwatch()` 并 `return false`，**写库这一步从来没执行过**。<br>**② 返回值被丢弃**：`WorkingDepartmentService.kt:78` 调 `sessionRepository.updateWorkingDepartment(...)` 时不接收 `Boolean` 返回值，无论成功失败都继续走 `stateOf(principal.userId, principal.role, departmentId)`——而这里用的是**入参** `departmentId`，所以响应里的 `current_id` 看起来是对的，更像「切成功了」。 |
| 影响 | 平台管理 / 超级管理员的「收窄到某个部门」功能**完全不可用**；部门管理在多部门间切换同样无效。前端切换器会显示成功、实际数据范围不变，用户没有任何反馈渠道。任何依赖「A2 = 收窄后的平台管理」做权限验证的结论都不成立。 |
| 修复建议 | ① TTL 判定改成同一时钟口径，例如 `Duration.between(Instant.now(), session.expiresAt.toInstant(ZoneOffset.UTC))`；或直接不自己算 TTL，用 Redis 的 `TTL` 命令取值。<br>② `switchTo` 必须检查返回值，`false` 时抛异常（例如 `AuthenticationInfrastructureException("登录状态已失效，请重新登录")`），不能静默成功。<br>③ 补一个回归测试：切部门后**重新发起一次请求**再断言 scope（本次就是因为只断言了 PUT 的响应体而漏掉）。 |

### F2 ·【中】人脸照片的 2MB 业务上限不可达，用户看到的是英文 413

| 项 | 内容 |
| --- | --- |
| 用例 | `TC-CREATE-12`（失败） |
| 现象 | 传 3MB PNG → **HTTP 413 `Uploaded file exceeds the configured size limit.`**，而不是方案预期的 400「人脸照片不能超过 2MB」。 |
| 实测证据 | 逐档探测：**0.9MB → 201**；1.1MB / 1.9MB / 2.1MB → **413**。有效上限是 **1MB**。 |
| 根因 | `application.yaml` 没有配置 `spring.servlet.multipart.max-file-size`，Spring Boot 默认 **1MB**，小于业务常量 `FACE_PHOTO_MAX_BYTES = 2MB`（`ParkingApiController.kt:2056`）。请求在进入 Controller 之前就被容器拦掉，`requireImageUpload` 的 `file.size <= FACE_PHOTO_MAX_BYTES` 那行**永远不会执行**，是一条死代码。 |
| 影响 | ① 业务规则（2MB）与实际行为（1MB）不一致，用户按提示准备 1.5MB 的图会被莫名拒绝；② 错误消息是框架英文原文，与全局中文提示风格不符，前端也难以据此做字段级提示。 |
| 修复建议 | 在 `application.yaml` 显式声明 `spring.servlet.multipart.max-file-size / max-request-size`（与业务上限对齐，例如 2MB/10MB），让业务校验真正生效；同时把 `GlobalExceptionHandler.onMaxUploadSizeExceededException` 的消息改成中文业务措辞并带上上限值。 |

### F3 ·【高】门禁授权模块（`access_control`）完全没有接入数据范围

| 项 | 内容 |
| --- | --- |
| 用例 | `TC-AC-06a/b/c`（方案预期 400「记录不存在」，实测全部放行） |
| 现象 | 只管理 D1 的部门管理 B1 可以：**列出** D2 的授权、**读取** D2 的授权详情、**审核** D2 的授权。 |
| 实测证据 | `GET /api/access-controls/{D2的id}` → **200**（返回了 D2 的记录）；`GET /api/access-controls?page=1&pageSize=100` → 列表里同时含 D1 和 D2 的授权；`POST /api/access-controls/{D2的id}/review?approved=true&review_reason=越权审核` → **200**。 |
| 根因 | 该模块的实现里没有任何范围调用：`AccessControlServiceImpl.list` 直接 `repository.findAll(PageRequest.of(...))`；`get/update/review` 直接 `repository.findById(id).orElseThrow { ... }`。全仓库搜索该 Service 没有 `ScopeGuard` / `DataScope` 的引用。 |
| 影响 | `department_id` 是这张表的真外键，本该是最容易做范围过滤的一张表，却是唯一一张**完全没做**的。任何持有 `access-control:read/review` 的部门管理都能越权操作其它部门的授权。目前该模块没有前端页面、同步也未接入（`sync` 直接 500），所以暴露面有限，但一旦接页面就是直接的数据越权。 |
| 修复建议 | 与其它模块保持同一口径：`list` 用 `scopeGuard.currentScope()` + `department_id in scope.departmentIds` 过滤；`get/update/review/sync` 统一走 `scopeGuard.requireVisibleRow(...)`，范围外与不存在返回同一条「记录不存在」。 |

### F4 ·【高】`POST /api/access-controls` 返回 500，但记录已经落库

| 项 | 内容 |
| --- | --- |
| 现象 | 新建门禁授权 → **HTTP 500 `Internal Server Error`**，响应体里没有新记录的 id。 |
| 实测证据 | 后端日志：<br>`HttpMessageNotWritableException: Could not write JSON: lateinit property departmentNumber has not been initialized`<br>`Caused by: kotlin.UninitializedPropertyAccessException: lateinit property departmentNumber has not been initialized`<br>但数据库里记录**确实存在**：`access_control` 表里查到了 `name=门禁授权D1, department_id=1, review_status=PENDING` 和 `name=门禁授权D2, department_id=2`。 |
| 根因 | Controller 的 `create` 直接返回实体，`department` 是懒加载代理；Jackson 序列化时触发 `Department.departmentNumber`（Kotlin `lateinit`）在未初始化的代理上取值 → 抛异常。事务在 service 层已经提交，所以**数据落了、响应炸了**。 |
| 影响 | 典型的「写成功、报失败」：调用方拿到 500 且没有 id，会认为失败并重试；重试要么撞 `person_number` 唯一约束，要么在库中留下重复记录。 |
| 修复建议 | ① 返回 DTO 而不是实体（该 Controller 其它接口也应统一）；② 或对 `department` 用 `@EntityGraph` / fetch join 预取；③ 兜底：给响应序列化加一层「懒加载代理安全」策略，避免此类异常再变成 500。 |

### F5 ·【中】403 与参数错误的提示中英混杂，且无法区分「未登录 / 无权限 / 越界」

| 项 | 内容 |
| --- | --- |
| 现象 | 同一个 403，不同来源给出完全不同的文案： |
| 实测证据 | 方法级安全拒绝 → `Access Denied`（英文，`TC-AUTH-09`、`TC-LIST-06`、`TC-DEL-01`、`TC-FACE-07` 等大量出现）<br>业务主动抛 `AccessDeniedException` → 中文，如「无权切换到该部门」「普通用户没有工作部门」「无权在其它部门下操作数据」<br>缺少 multipart 字段 → `Required request part "face" is not provided!`（英文，`TC-CREATE-11`）<br>文件过大 → `Uploaded file exceeds the configured size limit.`（英文，`TC-CREATE-12`） |
| 根因 | `GlobalExceptionHandler.onAccessDeniedException` 用 `ex.message ?: "禁止访问"` 直接透传；方法级安全抛的是 Spring 的 `AuthorizationDeniedException`，其 message 固定为 `Access Denied`。参数类异常另有各自的框架原文。 |
| 影响 | 前端只能拿到英文兜底文案，无法给用户可读提示；运维排查时也无法从消息区分「令牌失效」「权限不足」「数据范围外」。 |
| 修复建议 | 在 `onAccessDeniedException` 里对「没有业务消息的」`AuthorizationDeniedException` 统一替换成中文通用文案（如「没有操作权限」），并保留 `reason_code` 供程序分支；参数类异常同样收敛成中文（可参考 `HttpMessageNotReadableException` 已有的处理方式）。 |

### F6 ·【严重·必须提交前处理】图形验证码明文进了日志（即方案 G7）

| 项 | 内容 |
| --- | --- |
| 现象 | 后端标准输出里出现 `验证码为：5883`、`验证码为：5584`、`验证码为：2624`…… |
| 实测证据 | `grep -c "验证码为" <运行日志>` = **49**；`git diff CaptchaService.kt` 里能看到 `+ println("验证码为：${code}")`（工作区未提交改动）。 |
| 影响 | 图形验证码是登录链路上的第二因子。明文进日志后，任何能读到日志的人（运维、日志采集平台、把日志转发出去的第三方）都能配合已知用户名直接登录；本次测试我自己就是这么取验证码的，等价于验证码完全失效。日志还会被归档、备份、导出，泄漏是持久的。 |
| 修复建议 | 删除该 `println`；若确实需要调试，改成 `log.debug` 且只在非生产 profile 打开，并且**不要打印答案本身**（可打印 token 前 8 位用于对账）。建议同时加一条 CI 检查：源码里出现 `println(` 直接失败。 |

### F7 ·【低】`/api/excel/person-records/export` 对所有角色恒 403

| 项 | 内容 |
| --- | --- |
| 用例 | `TC-PR-07` / `TC-PR-07b` |
| 现象 | 该路径**任何角色**（含 SUPER_ADMIN）都返回 403 `Access Denied`。 |
| 实测证据 | S1 / A1 / B1 / B2 / U1 五个身份全部 403；模板路径 `/api/excel/person-records/template` 同样 403。已确认 B1 的权限集合里**确实有** `person-record:export`。 |
| 根因 | 路径命中了 `ExcelController` 的通用 `GET /{resource}/export`，但该方法的 `@PreAuthorize` SpEL 里列举了 `users/positions/owners/spots/plates/plate-inspections/devices/gate-persons`，**没有 `person-records` 分支**，表达式恒为 false。 |
| 影响 | 权限码 `person-record:export` 存在、且被授予 SUPER_ADMIN / ADMIN / DEPT_ADMIN 三个角色，却没有任何后端接口使用它（前端「导出」是本地拼 CSV，只导当前页，见 G5）。权限矩阵与实际能力对不上，会让使用者以为导出已实现。 |
| 修复建议 | 二选一：① 补上 `#resource == 'person-records' and hasAuthority('person-record:export')` 分支并真正实现服务端导出；② 若确认不需要，删掉 `person-record:export` 权限码，让该路径干净地返回 404（现在是 403，会误导为「有接口但没权限」）。 |

### F8 ·【低】审计接口路径、必填参数与文档不符

| 项 | 内容 |
| --- | --- |
| 现象 | 方案里写的 `GET /api/audit-events` 实际是 **404**；真实路径是 `GET /admin/api/audit-events`。 |
| 补充发现 | 该接口还要求 `occurred_from` + `occurred_to` **必填**（缺失报 400「审计查询必须指定开始和结束时间」），且**查询窗口不能超过 31 天**（超出报 400）。列表字段名是 `data.events`（不是 `items`）。 |
| 影响 | 方案与实现脱节，按方案写的脚本/文档会全部踩空。`page_size` 等内容也需要按实现补齐。 |
| 修复建议 | 更新方案文档；若希望降低使用门槛，可考虑给时间范围一个默认值（如最近 7 天）而不是强制必填。 |

### 附：门禁授权 `sync` 返回 500（方案 TC-AC-02 已预期）

实测确认：对已审核通过的授权调 `POST /api/access-controls/{id}/sync` → **500 `Internal Server Error`**。
根因是 `IllegalStateException("门禁设备同步尚未接入")` 没有专用异常处理器，落到兜底 500 且消息为空。
属于方案的已知项，建议按方案结论处理：接口约定应返回 **501 / 400 + 明确消息**，而不是 500。

---

## 3. 方案的 4 条通过判据复核

| 判据 | 结论 |
| --- | --- |
| ① 第 5 节全部 ✅/⛔/🚫 用例：状态码与 message 与预期一致 | **大部分一致**，2 条不一致（`TC-AUTH-06b`、`TC-CREATE-12`），另有 9 条实测行为与方案预期不同（已按 INFO 记录并逐条说明） |
| ② 第 5.12 节 20 条对抗性用例无一越过范围或状态机 | **通过**。20 条全部守住 |
| ③ 第 6 节 G1~G8 逐条确认并给出结论 | **已完成**，见第 5 节 |
| ④ 跨部门对比用例数据条数必须不同 | **通过**：`TC-LIST-04`(D1=3) vs `TC-LIST-05`(D2=1)；`TC-XLS-03`(3) vs `TC-XLS-04`(1)；`TC-FACE-01`(200) vs `TC-FACE-02`(404) 全部不同 |

---

## 4. 亮点（实现对的部分，建议保持）

这些是本次实测确认**做得好、不要回退**的设计：

1. **「范围外」与「不存在」同错**：`TC-LIST-11/12`、`TC-FACE-02/09`、`TC-ATK-19`（40 次探测）都验证了响应体逐字一致，无法用响应差异做存在性探测。这是很多系统做不到的。
2. **唯一性检查先于文件上传**：`TC-CREATE-07` 传重复编号被拒后，`stored_files` 里没有多出任何文件。注释里写的理由（否则文件会永久留在存储里）确实被落实了。
3. **失败即不可见（fail-closed）**：`department_code IS NULL` 且 `dept` 文本解析不出部门编码的行，对所有受限角色不可见。方案里那个 ⚠️（担心 A2 走「展开下级」路径时漏掉 fail-closed）**实测不成立**，`TC-LIST-03` total=3，没有泄漏。
4. **事务边界正确**：`TC-UPDATE-07`（越界改部门 + 改姓名）整条回滚，姓名没被改掉；`TC-REVIEW-06`（批量夹带范围外成员）整批回滚；`TC-XLS-14`（1 行合法 + 1 行非法）合法行也没落库。
5. **状态机无法绕过**：`TC-UPDATE-01` 证明「先过审再改内容」会被打回 `审核中` + `未同步`；`TC-UPDATE-02` 证明「原样回填」不会误伤已通过记录；`TC-UPDATE-03` 证明「只回填 department_code」不算内容变化。
6. **文件生命周期管理到位**：改编号后旧 URL 仍可下载（`relinkBusiness` 只重挂业务锚点），人员删除后脸图立即 404（`unlinkBusiness`），挪部门后原部门立刻失去访问权（`TC-FACE-10/11/12`）。
7. **删除必须走申请审批**：`DELETE /api/gate-persons/{id}` 是 `denyAll()`，**超管也是 403**（`TC-DEL-02` 专门验证了没有后门）；且审批时会**双重校验申请单和人员**，`TC-DEL-13` 证明「申请单可见」不构成删除授权。
8. **审计采样保护有效**：批量导入 200 行只落 **1 条** `GATE_PERSON_CREATED`，`sample_codes` 长度 ≤ 20（`TC-AUDIT-08`）；删除人员单独立案为 `CRITICAL` 级（`TC-AUDIT-02`）。
9. **验证码防爆破是按 token 计数的**：同一个 token 连续答错会触发「验证码错误次数过多，请刷新」，换新 token 则重新计数——这个粒度是对的（挡的是对同一张图的暴力枚举）。

---

## 5. 方案第 6 节缺口（G1~G8）复核结论

| 编号 | 方案描述 | 复核结论 | 建议 |
| --- | --- | --- | --- |
| **G1** | `syncStatus` 永远不可能变成「已同步」 | **证实**。全仓库 `GatePerson.SyncStatus.SYNCED` 的赋值点只有 `MockDataInitializer.kt:602`（mock 数据）；`ParkingApiController` 里其余出现都是**读取比较**。没有任何下发接口写这个状态。 | **修复或移除筛选选项**。当前「同步状态=已同步」筛选恒为空，`GATE_PERSON_CREATED` 审计里的 `synchronized` 恒 false——这是一个「看起来能用、实际永远为空」的功能，比没有更误导。若短期不做下发，建议先把该筛选项摘掉并在 UI 上注明。 |
| **G2** | 普通用户拿不到 `file:read`，`fileVisible` 的 SELF 分支永远走不到 | **证实**。`PermissionCatalogInitializer.kt:213` 的 `USER_PERMISSION_CODES` 只有 `dashboard:read` / `vehicle-record:read` / `person-record:read`；`TC-FACE-07` 实测 U1 访问人脸得 403。 | **按设计意图择一**：要么给普通用户 `file:read`（让 SELF 分支真正生效，用户能看自己的脸图），要么删掉 `fileVisible` 里那条永远不可达的 SELF 分支，避免误导后续维护者。 |
| **G3** | `person_access_record` 没有任何写入通道 | **证实，但需修正措辞**。`PersonAccessRecord()` 的构造点只有 `MockDataInitializer.kt:655`；另有一处 `personAccessRecordRepository.saveAll` 在 `DepartmentLinkBackfillService.kt:121`，但它只**回填既有行**的 `department_code` / `linked_user_id`，不产生新记录。 | 生产环境「人员进出」页永远为空，`linkedUserId` 永远是 NULL → `personRecordsInScope` 的 SELF 分支实际失效（本次 `TC-PR-01/02/03` 能跑通，是因为我用 SQL 直插了 4 条）。**单独立项**：接入真实人员进出数据源。 |
| **G4** | `code` / `idCard` 全局唯一 → 跨部门存在性侧信道 | **证实**。`TC-CREATE-08` 用 D2 已存在的身份证号建 D1 记录 → 400「身份证号已存在」；`TC-XLS-13` 用 **D2 的 `GP-D2-001` 编号**导入 → 400「导入文件中包含已存在的人员编号」。错误消息**不带部门信息**，但结论本身泄漏了「别的部门存在这个编号」。 | 建议按方案：受限范围下把重复判定收窄到范围内，或统一返回不含归属的通用错误。注意这条在**导入路径**同样成立。 |
| **G5** | 人员进出「导出」是前端本地 CSV，不是服务端导出 | **证实，且比方案描述的更严重**。方案说 U1 会拿到 404/405，实测是 **403**：该路径命中了通用 `/{resource}/export` 映射，而 `@PreAuthorize` 的 SpEL 里没有 `person-records` 分支，导致**所有角色（含超管）恒 403**（见 F7）。`person-record:export` 权限码存在但无接口使用。 | 见 F7。 |
| **G6** | 列表为「全表加载 + 内存过滤 + 内存分页」 | **证实**。`listGatePersons` 用 `findAll()` 后 `visibleInScope` 内存过滤再内存分页；`ScopeQuerySupport` 顶部有 TODO。当前库 689→696 个用户、门禁人员 225 行，**无感知**；本次跑到 200 条批量时响应仍正常。 | 保持单独立项，不紧急。但注意 `total` 语义依赖内存精确计数，换成数据库分页时要一并考虑。 |
| **G7** | 工作区有未提交的验证码调试输出 | **证实，且已造成实际泄漏**。见 F6。 | **提交前必须删**。这是本次唯一一条「代码在手上就能立刻止血」的严重问题。 |
| **G8** | 已删除人员的信息在删除申请单里长期留存 | **证实**。实测 `gate_delete_request` 里 5 条记录（含 `APPROVED` / `REJECTED` 的），`phone`（11 位）、`id_card`（18 位）、`face`（URL）快照**全部保留**。 | 属「留痕」设计取舍，但需按个人信息最小化要求做决定。**注意**：`face` 字段虽然留了 URL，但因为 `unlinkBusiness` 已经解绑，**实际取不到图**（`TC-FACE-10` 已证 404），这一点比方案预期的好。建议明确保留期限与脱敏策略。 |

---

## 6. 方案本身需要修订的地方

实测发现方案里有 5 处与实现不符，建议就地更正，否则下次执行还会踩：

| 位置 | 方案写的 | 实际是 |
| --- | --- | --- |
| 第 2.3 节取验证码脚本 | `sed -n 's/.*"base64,//; s/".*//p'` 等 shell 管道 | **可用**，但注意 `render()` 把 4 位数字写在 `<text>` 节点里、SVG 是 base64 明文，这个前提本身没问题。真正的坑在别处：**Windows 上 curl 会把 argv 里的中文按 GBK 编码**，导致带中文的 `-d`/`-F` 直接 400（本次脚本全部改用 Python 发送）。建议在方案里注明，或统一改用脚本而非 curl。 |
| 第 3.1 节建部门 D2 | `POST /api/depts` + JSON body | 实际路径是 `POST /api/departments`，且参数是 **query/form**（`name`、`department_code`、`sort_order`），不是 JSON。 |
| 第 5.3 节 `TC-CREATE-12` | 预期 `400 人脸照片不能超过 2MB` | 实际 `413 Uploaded file exceeds the configured size limit.`（有效上限 1MB，见 F2）。**方案要改的不只是预期，还有 `FACE_PHOTO_MAX_BYTES` 与容器配置的不一致。** |
| 第 5.10 节 | `GET /api/audit-events` | 实际 `GET /admin/api/audit-events`，且 `occurred_from`/`occurred_to` 必填、窗口 ≤ 31 天、列表字段是 `data.events`（见 F8）。 |
| 第 5.11 节 `TC-AC-06` | 「审核范围外部门的授权 → 400 记录不存在」，理由写「该表有真正的 department_id 外键」 | **该表虽然有真外键，但实现里一行范围校验都没有**，实测 200 放行（见 F3）。方案把「有外键」误当成了「有范围校验」。 |

另有两处**预期偏保守、实测更好**，可以放宽：

- 第 5.2 节 `TC-LIST-03` 的 ⚠️：担心 A2（平台管理收窄到 D1）会因「展开下级」路径漏掉 fail-closed 而看到 `GP-ORPHAN`。实测 **total=3，没有泄漏**。
- 第 5.8 节 `TC-FACE-02` 预期「🔒 404」：实测确实是 404，且与「文件真不存在」的响应体完全一致（这点方案写对了，实测确认）。

---

## 7. 未执行的用例与原因

| 用例 | 状态 | 原因 |
| --- | --- | --- |
| **TC-XLS-15** 导入 1 万行 | **未执行** | 会在 `cartesktest` 真库写入 1 万条 `gate_person`，而本次执行环境下**无法批量清理**（SQL 侧的批量 `DELETE FROM gate_person WHERE code LIKE 'XLS-%'` 被安全策略拦截；走删除申请审批则需 2 万次请求）。权衡后没有跑。<br>**该用例要验证的两点已被替代覆盖**：`count` 正确性由 `TC-XLS-09/10/11` 的小批量导入验证；「审计只采样前 20 个 code」由 `TC-AUDIT-08` 用 200 行导入验证（实测落 1 条 `GATE_PERSON_CREATED`，`sample_codes` 长度 ≤ 20）。<br>**如需补跑，请先确认清理手段**（建议用 psql 直连，或临时允许该批量删除）。 |
| **TC-FACE-08** 经 `businessType/businessId` 反查 | **已执行，但换了手法** | 原设计想把 `GP-D1-002` 的文件 `department_code` 置 NULL 来强制走反查分支，该 `UPDATE stored_files` 被安全策略拦截。改用**现网既有数据**完成：`GP-G-001`（由超管在 D2 创建）的上传文件本身 `department_code` 就是 NULL，用它验证——B2(D2) 取到 **200**（证明反查分支可达），B1(D1) 取到 **404**（业务对象不在范围内）。结论比原设计更贴近真实。**

另外顺带发现：`stored_files` 里 **7285 条 `vehicle_plate` 抓拍图**的 `department_code` 也是 NULL，它们全部走车牌反查分支——这说明同步任务写入的图片在部门范围下的可见性**完全依赖反查**，值得单独回归。

---

## 8. 数据残留与清理

测试在 `cartesktest` 上留下了以下痕迹（清理 SQL 见方案第 8 节，注意批量 DELETE 在本环境受限）：

| 表 | 测试前 | 测试后 | 说明 |
| --- | --- | --- | --- |
| `users` | 689 | **696** | +7 个测试账号：`t_admin`(690) / `t_dept_d1`(692) / `t_dept_d2`(693) / `t_user_none`(694) / `t_first_login`(695) / `t_user_d2`(696) / `t_dept_d1d2`(697)。（另有一个编码过程用的 `t_admin2`(691) 在准备阶段已通过接口删除） |
| `department` | 1 | **2** | +`GATE-D2`（部门编码） |
| `gate_person` | 0 | **228** | 明细：`GP-*` 夹具 10（含 SQL 直插的 `GP-ORPHAN` / `GP-BACKFILL`）＋ `REV-*` 批量 200 ＋ `XLS-*` 导入 8 ＋ 删除/人脸探针 5（另 4 条探针中的 1 条已被 `TC-DEL-09` 物理删除）＋ 跨部门审核探针 `D2-REV-*` 2 |
| `gate_delete_request` | 0 | **9** | 全部为 `DEL-*` / `FACE-DEL-*` 探针 |
| `access_control` | 0 | **6** | `AC-D1-*` / `AC-D2-*` 各 2 轮（其中 4 条因 F4 的 500 而「客户端看不见但已落库」） |
| `person_access_record` | 0 | **4** | `TC-PR-*` 需要的直插数据 |
| `stored_files` | 7028 | **7308** | 测试上传的人脸图（`st1/` 下有对应的物理文件） |
| `audit_event` | 7729 | **9022** | 追加写，按方案建议**保留**作为本次测试证据 |

**不可自动回滚的副作用（已发生）**：

- `U1(18950315520)` / `U2(13705023122)` 的密码已从 `Fqjg20221022` 改为 `Test@2026x`，`must_change_password` 由 true 变 false；改密触发了 `incrementTokenVersion`，这两个账号在别处的登录已被踢下线。
- 新建账号 `t_*` 的密码：首登后为 `Test@2026x`。
- `admin` 密码由 `FORCE_WRITE` 固定为 `admin`（既有配置，非本次引入）。
- `st1/` 下的物理人脸文件需手工删除，路径可从 `SELECT relative_path FROM stored_files WHERE business_id LIKE 'GP-%' OR business_id LIKE 'DEL-%'` 定位。

---

## 9. 改进建议汇总（按优先级）

### P0 — 提交前必须处理

1. **删除 `CaptchaService.kt` 的 `println("验证码为：$code")`**（F6）。这是唯一一条「代码就在手上、影响却是登录第二因子失效」的问题。
2. **修 F1（工作部门切换）**：改 TTL 时钟口径 + `switchTo` 检查返回值。这两处各自都是小改动，但不改则所有「收窄到某部门」的分权都是假的。
3. **给 F1 补回归测试**：现有实现之所以能长期带着这个 bug 而不被发现，正因为「切完只看 PUT 的响应体」也会通过。测试必须**在切换后重新发一次请求**再断言 `scope`。

### P1 — 本次迭代内处理

4. **对齐 multipart 配置与业务上限**（F2）：显式配置 `spring.servlet.multipart.*`，让「2MB」这条规则真正生效；同时把 413 的消息中文化。
5. **给 `access_control` 补数据范围**（F3）：`list` 按 `department_id` 过滤，`get/update/review` 走 `requireVisibleRow`。
6. **修 `POST /api/access-controls` 的 500**（F4）：返回 DTO 或预取 `department`。同步给 `sync` 接口一个专用异常处理（返回 501/400 而不是空消息 500）。
7. **统一错误消息语言**（F5）：把框架兜底文案收敛成中文，并让 403 能区分「无权限 / 范围外 / 未认证」。

### P2 — 排期处理

8. **处理 G1**：`syncStatus` 的「已同步」永远取不到——要么实现下发，要么先摘掉筛选项。留着一个恒为空的筛选比没有更误导。
9. **决定 G2**：给普通用户 `file:read`，还是删掉 `fileVisible` 里那条不可达的 SELF 分支。
10. **单独立项 G3**：接入 `person_access_record` 的真实数据源。
11. **收敛 G4**：受限范围下把 `code`/`idCard` 的唯一性判定收窄到范围内，或统一返回不含归属信息的通用错误（单条录入与 Excel 导入两条路径都要改）。
12. **决定 G5 / F7**：实现服务端人员进出导出，或删掉 `person-record:export` 权限码并让路径返回 404。
13. **明确 G8 的留存策略**：`gate_delete_request` 里的手机号/身份证快照保留多久、是否脱敏。

### P3 — 长期

14. **G6**：把门禁人员列表从「全表加载 + 内存过滤 + 内存分页」改成带部门条件的数据库查询。（当前规模无感。）
15. **同步抓拍图的范围判定单独回归**：7285 条 `vehicle_plate` 文件全无 `department_code`，可见性完全依赖车牌反查分支，值得单独加一组测试守住。
16. **CI 加一条检查**：源码中出现 `println(` 直接失败，防止调试输出再次混进提交。

---

## 10. 修复记录与复测结果（2026-09-15 补充）

### 10.1 修复清单

| 编号 | 修复内容 | 涉及文件 |
| --- | --- | --- |
| **F1** | ① `replaceWorkingDepartment` 的剩余有效期改为**取 Redis key 自身的 TTL**（`ops.getExpire`），不再拿 UTC 的 `expiresAt` 与本机时钟相减——时区口径不一致正是它恒为负的根因；② `switchTo` 检查 `updateWorkingDepartment` 的返回值，失败时抛 `JwtAuthenticationException("登录状态已失效，请重新登录")`，不再静默成功。 | `RedisTokenSessionRepository.kt`、`WorkingDepartmentService.kt` |
| **F2** | ① `application.yaml` 显式配置 `spring.servlet.multipart.max-file-size: 5MB` / `max-request-size: 12MB`。**关键是容器上限必须大于业务上限**：设成等于 2MB 的话请求仍会在进 Controller 前被拦，业务校验依旧是死代码（这一版先设了 2MB，实测发现 2.1MB 仍然到不了业务层，已改成 5MB）。② 413 文案中文化并带上限值。 | `application.yaml`、`GlobalExceptionHandler.kt` |
| **F3** | 新增 `ScopeGuard.requireVisibleAccessControl` / `accessControlVisible`（这张表有真 `department_id` 外键，直接比对外键，不走编码解析）；`AccessControlServiceImpl` 的 `list` / `get` / `getBatch` / `update` / `updateBatch` / `review` / `synchronize` 全部接入；`list` 用新增的 `findByDepartment_IdIn` **把范围下推到 SQL**，不再是全表分页。 | `ScopeGuard.kt`、`AccessControlRepository.kt`、`AccessControlServiceImpl.kt` |
| **F4** | ① 新增 `AccessControlView` DTO，控制器不再直接序列化实体（懒加载代理 + 请求体里那个只有 `id` 的 `Department` 空壳就是 500 的根因）；② `create`/`update` 时用 `resolveDepartment` 把请求体里的部门换成**受管实体**，顺带把「部门不存在」挡在写库之前；③ `update` 改成「请求体没带部门就保持原值」（`copyEditableProperties` 是逐属性覆盖，原样提交会把部门清成 null）；④ `sync` 改抛 `BusinessException(501)`，不再是 500 + 空消息。 | `AccessControlController.kt`、`AccessControlServiceImpl.kt` |
| **F5** | `onAccessDeniedException` 对 Spring 的 `AuthorizationDeniedException`（message 固定为英文 `Access Denied`）兜底成「没有操作权限」；缺少请求参数/请求部分/请求头、参数类型不匹配、方法不支持、上传超限全部改中文。 | `GlobalExceptionHandler.kt` |
| **F6** | 删除 `CaptchaService.render()` 里的 `println("验证码为：$code")`。 | `CaptchaService.kt` |
| **F9**（本次复测新发现） | `replaceManagedDepartments` 在 `deleteByUserId` 之后显式 `flush()`。Hibernate 的 flush 顺序是 **INSERT 先于 DELETE**，同一个事务里删了再插同一组 `(user_id, department_id)` 会撞 `uk_user_managed_department` 报 500——而「把同一份范围再保存一次」是前端原样提交就会走到的常规路径。首次测试时因为库里还没有分配记录所以没暴露，这次重复执行准备脚本才踩到。 | `WorkingDepartmentService.kt` |

### 10.2 按决策处理的三项

| 缺口 | 处理方式 |
| --- | --- |
| **G2** | 给 USER 角色加 `file:read`（`PermissionCatalogInitializer.USER_PERMISSION_CODES`），同时把 `FileController` 的 `get` / `download` 两个读接口的角色条件放开到 USER。只放开读：这两个接口都经 `findVisibleFile` 按当前范围过滤，SELF 范围下只能拿到本人上传的、本人车牌关联的、以及本人门禁身份关联的文件。存量环境的角色在启动时会由已有的 `grantMissing` 路径自动补授。 |
| **G4** | 把「编号/身份证号冲突」的提示收敛成 `GatePersonFields.CODE_EXISTS_MESSAGE` / `ID_CARD_EXISTS_MESSAGE` 两个常量，单条录入、Excel 导入、以及并发下落到底层唯一约束兜底这三条路径共用同一句，避免措辞差异成为新的差异点。**注意：这只抹掉了额外信息，并没有关掉侧信道**——唯一约束是全局的，「创建失败」本身就说明编号在库中存在，要真正关掉必须把唯一性收窄到部门（方案 B，本次未采纳）。已在代码注释里写明这一点。 |
| **G1** | 前端 `gate-persons.vue` 摘掉「同步状态」筛选控件（保留列显示）。原因：后端没有任何接口会把人员置为 `SYNCED`（写入点只有 mock 数据），「已同步」恒为空、「未同步」恒等于全部，两个选项都筛不出东西，留着比没有更误导。代码里留了注释说明何时可以加回来。 |

### 10.3 未处理（按决策或另立项）

| 项 | 状态 |
| --- | --- |
| **F7 / G5**（人员进出导出恒 403、`person-record:export` 无接口使用） | 按决策**留在报告里**，本次不动。前端「导出」仍是本地拼 CSV、只导当前页。 |
| **G3**（`person_access_record` 无写入通道） | 单独立项：需要接入真实人员进出数据源。 |
| **G6**（门禁人员列表全表加载 + 内存过滤） | P3，当前规模无感。 |
| **G8**（删除申请单里的手机号/身份证快照长期留存） | 待产品确认留存期限与脱敏策略。 |

### 10.4 新增的回归用例

| 测试 | 覆盖 |
| --- | --- |
| `WorkingDepartmentServiceTests`（新建，5 个用例） | F1 的两半都锁住：「会话说没写成功时不能返回成功」+「写成功时会话状态确实是新的」；另外覆盖「切回全部」、「普通用户没有工作部门」、以及 **F9** 的「先落删除再插入」顺序（用 `inOrder` 断言）。 |
| `AccessControlServiceTests`（5 → 11 个用例） | 用**真的** `ScopeGuard`（只替换范围来源 `DataScopeResolver`），否则可见性判定就成了「打桩返回什么就是什么」。新增：跨部门读/审/改同错、本部门可读可审、受限范围不能建无部门或范围外的授权、创建时把只有 id 的部门换成受管实体、更新不带部门时保持原值、`sync` 返回 501。 |
| `GatePersonReviewIntegrationTests`（+1） | G4：跨部门撞号与本部门撞号的**响应体逐字一致**（编号与身份证号各一组）。 |
| `PermissionCatalogInitializerTests` | 断言 USER 角色拿到 `file:read`。 |

### 10.5 复测结果

**① 单元 / 集成测试**

```
./gradlew test  →  435 tests completed, 3 failed, 2 skipped
```

3 个失败全部是 `SynCarCapInfoTaskTests` 的**存量红**（断言 30 天窗口，与 `INITIAL_SYNC_DAYS=1` 不符），
与本次改动无关；修复前跑同一套也是这 3 个。**无新增回归。**

**② 实机复测**（`docs/gate-test/t_regress.py`，39 条，**全部通过**）

| 组 | 条数 | 验证内容 | 结果 |
| --- | --- | --- | --- |
| A. F1 工作部门切换 | 9 | 切换后**重新发请求**会话确实收窄；直连 Redis 核对写入真的落盘；收窄后可见条数严格减少；部门管理多部门切换同样生效；原有 403/400 语义未回退；F9 重复提交管理范围不再 500 | ✅ 9/9 |
| B. F2 上传上限 | 4 | 0.9MB→201；**1.1MB→201（修复前是 413）**；**2.1MB→400「人脸照片不能超过 2MB」（修复前是容器 413 英文）**；6MB→413 中文提示 | ✅ 4/4 |
| C. F3/F4 门禁授权 | 8 | 创建返回 201 且带 id / 部门名，条数只 +1（不再有「看不见的落库」）；建到范围外 403；按 ID 读别部门 400；**列表只出现 D1**；越权审核 400；本部门审核 200；sync 501；删除仍 403 | ✅ 8/8 |
| D. F5 错误消息 | 3 | 403 兜底为「没有操作权限」；缺 multipart 部分为「缺少必需的请求部分：face」；业务错误仍中文 | ✅ 3/3 |
| E. F6 验证码 | 1 | 重启后的日志里「验证码为」出现 **0 次**（修复前 49 次） | ✅ 1/1 |
| F. G2 普通用户读图 | 3 | USER 已持有 `file:read`；**能取到本人门禁身份的照片（200）**；别人部门的照片仍是 404 | ✅ 3/3 |
| G. G4 撞号提示 | 3 | 本部门撞号与跨部门撞号的**响应体逐字一致**（编号、身份证号各一组） | ✅ 3/3 |
| H. 未改动行为回归 | 8 | 范围隔离（D1/D2 互不可见）、跨部门按 ID 读被拒、改内容打回待审核、状态机单向、物理删除对超管关闭、删除申请提交/驳回、SELF 记录范围 | ✅ 8/8 |

**③ 补充实机探测**（`docs/gate-test/t_regress2.py`，19 条，**全部通过**）

只打「改过但上面那 39 条没覆盖」的接口：

| 组 | 验证内容 |
| --- | --- |
| access_control 批量三件套 | `getBatch` / `updateBatch` 混入别部门 id → 400 且不落半步结果；`createBatch` 建到范围外 → 403；单条 `PUT` 跨部门 → 400；**把自己的记录改到别部门 → 403 且实际部门未变** |
| 新建入参边界 | 部门不存在 → 400；受限范围下不给部门 → 400「必须指定部门」；全局角色不给部门 → 201（该记录对受限角色不可见，fail-closed 预期） |
| 文件接口对 USER 放开后 | 元数据接口 `GET /api/files/{id}` 只能看自己的（别人的 404）；**上传仍然 403**，没有顺手放开写 |
| 审查修复（见 10.7） | 撞号 → 400 而不是 500；坏类型 → 400 而不是外键异常 500；**GET→PUT 原样提交后 `access_control_type_id` 保持不变** |
| 收紧后仍可用 | 本部门授权仍可读；本部门人员的脸图仍可下载 |

**④ 前端**：`npx nuxt build` 通过（G1 的筛选控件改动无残留引用）。

### 10.6 为什么没有重跑原来那套 151 条用例

原来那套大量依赖「干净基线」的**绝对条数**（例如 `TC-LIST-01` 期望 `total=5`、`TC-XLS-03` 期望导出 3 行），
而库里已经累积了上一轮写入的 228 条门禁人员、9 条删除申请、若干测试账号与部门。
批量清理被本机工具链的安全策略拦下（见第 7 节），在不清库的前提下那些断言必然失真。

因此复测改成了**不依赖绝对条数的定向回归**（第 10.5 ②），逐条对着缺陷验证；
未改动的核心行为用「相对关系」断言（互不可见、严格变少、两种情形逐字一致）来兜底。
如果需要完整重跑 151 条，请先按方案第 8 节清理测试数据（**建议用 psql 直连执行批量 DELETE**，
本机 MCP 工具链不允许批量删除）。

### 10.7 代码审查轮（同日）发现的补充修复

修复完成后对本次改动的 15 个文件做了一轮针对性代码审查，提出 6 条。逐条核实后：
**4 条属实（其中 1 条是第一轮修复引入的回归）**、1 条属**工作区里既有的未提交改动**（不是本次动的）、1 条是琐碎清理。

| # | 发现 | 核实 | 处理 |
| --- | --- | --- | --- |
| **R1** | 【中】**F4 的修复漏了同源的 `accessControlPermission`**：`copyEditableProperties` 逐属性覆盖，而新的响应视图又没有这个字段，于是「取回来再原样提交」会静默清空 `access_control_type_id`。另外挂一个不存在的类型不会被校验，直接撞 PostgreSQL 外键 → 500 | **属实，且是本次引入的回归**（旧实现把实体整个序列化出去，round-trip 至少能把值带回来）。真机复现：`id=20`（只建不改）类型 = `1`；`id=19`（建完做一次 GET→PUT）类型被清成 **NULL** | 已修：`resolvePermission(source, fallback)` 与部门同源处理（只在请求体带了才覆盖 + 校验存在性 + 视图暴露 `accessControlPermissionId`/`Name`）。复测：修复后新建的 `id=22` 做同样 round-trip，类型仍是 `1` |
| **R2** | 【低】`access_control.person_number` 是唯一，但既没预校验、`onDataIntegrityViolationException` 也没有对应分支 → 撞号落到 500 + 空消息 | **属实。** 真机复现：第二次 POST 撞同一编号 → **500**。库里的约束名是 `ukepcmmojnf2qav4dweqpehekoa` —— Hibernate 生成的哈希串，**没法按名字映射**（这正是它没进 `when` 分支的原因） | 已修：`requirePersonNumberAvailable()` 预校验，撞号返回 400「人员编号已存在」；批量内部自己撞自己也拦。复测：第二次 POST → **400** |
| **R3** | 【低】新增的 `findByDepartment_IdIn` 过滤在 `access_control.department_id` 上，而该列没有索引（PostgreSQL 不会为外键自动建索引） | 属实 | 已修：`@Table(indexes=[Index("idx_access_control_department", "department_id")])`。复测 `pg_indexes` 确认索引已由 `ddl-auto=update` 补建到存量表上 |
| **R4** | 【低】`GatePersonFields` 的两个冲突提示常量文档说「所有路径必须共用」，但 `ParkingApiController` 的**更新**路径仍写着字面量 | 属实（第一轮只替换了 create 路径，漏了 `existsByCodeAndIdNot` / `existsByIdCardAndIdNot` 两处） | 已修，两处改用常量 |
| **R5** | 【低】`CaptchaService.kt` 里有一条 IDE 留下的无引用 `import kotlin.math.log` | 属实，但是**工作区既有的 IDE 变更**（该文件在我开始前就是 modified 状态） | 已删。删掉之后该文件与 `HEAD` **完全一致**（第一轮删 `println` 的效果就是让它回到 HEAD 状态） |
| **R6** | 【低】`gate-persons.vue` 的 `search()` 会被 `validateDepartmentFilter()` 卡住：`loadDepartments()` 在缺少 `department:read` 或 `/depts` 报错时直接返回空候选，此后「单位」框里输入任何内容都会让搜索整体静默失败 | 属实，但**不是我这次改的**：把「单位」从 `<select>` 换成 `<input list>` + `validateDepartmentFilter()` 是工作区里**已存在的未提交改动** | **未动**（改动别的工作区内容不合适）。这里记一笔提醒：`departmentOptions` 为空时该守卫会让纯关键词搜索也失效，建议改成「候选为空就不校验」或失败时放开 |

**审查同时确认无恙的部分**（避免误伤）：F1 的 `getExpire` 用法与单连接 WATCH/MULTI 事务是自洽的、`<= 0` 覆盖了 Redis 的 `-1`/`-2`；`access_control` 九条服务路径都过了范围判定，`SELF` 分支因接口要求管理角色而不可达、不是缺口；`FileController` 放开 USER 后两条路径都经 `findVisibleFile`；F5 的英文常量与 spring-security 7.1.0 的实际文案一致，且没有任何测试或前端代码断言被改掉的旧文案。

**复测**：`t_regress.py` **39/39**、`t_regress2.py` **19/19**（新增 3 条对着 R1/R2 的用例）、单测 **435 个、3 个存量红、无新增回归**。

### 10.8 遗留提醒

- **G4 的侧信道只是「没有额外泄漏」，并没有关闭**。当前实现下 D1 的部门管理依然可以通过
  「创建该编号失败」推出 D2 存在这个编号。要真正关闭需要把 `uk_gate_person_code` /
  `uk_gate_person_id_card` 从全局唯一改为按部门唯一（涉及数据迁移与业务语义变更）。
- **`spring.servlet.multipart.max-file-size` 现在是 5MB**，比业务上限 2MB 大。这个关系不能反过来——
  一旦容器上限低于或等于业务上限，对应的人脸照片校验就会重新变成永远执行不到的死代码。
  已在 `application.yaml` 里写明原因，**改这个值前请先读那段注释**。
- **`file:read` 现在授给了 USER 角色**。放开的是 `GET /api/files/{id}` 与 `/{id}/download` 两个读接口，
  它们都经 `findVisibleFile` 按当前范围过滤；新增涉及文件的接口时，不要只靠 `@PreAuthorize`
  的角色列表，务必确认走了范围判定。
- **`gate-persons.vue` 里有一处与本模块无关的未提交改动**（「单位」筛选从下拉框改成可输入的 datalist），
  上面 R6 记了它的一处 UX 风险，本次没有改动它。
- **`access_control.person_number` 的唯一约束名是 Hibernate 生成的哈希串**。以后若要按约束名
  在 `GlobalExceptionHandler` 里映射，必须像 `GatePerson` 那样在 `@Table(uniqueConstraints=...)`
  里显式命名——但改名字对存量表来说是加约束不是改名字，需要单独评估。

---

## 附录 A：执行环境与方法

- **后端启停**：通过 IDEA 的运行配置 `CarTaskApplication` 启动，用 MCP 拿到运行日志路径
  `%LOCALAPPDATA%\JetBrains\IntelliJIdea2026.2\tmp\ij_run__CarTaskApplication_13165961012407857783.log`，
  从中读取 `GlobalExceptionHandler` 的异常栈来定位 500 的根因。
- **数据库读写**：通过 IDEA 的 `cartesktest@192.168.1.95` 连接直接执行 SQL（造 `GP-ORPHAN` / `GP-BACKFILL`、
  `person_access_record` 夹具、复核 `audit_event` / `gate_delete_request` / `stored_files`）。
- **Redis 核查**：本机没有 `redis-cli`，用 Python 手写 RESP 协议直连，并用 `MONITOR` 抓命令流，
  这是定位 F1 的关键手段（看到了 `WATCH` → `UNWATCH` 而没有 `MULTI`/`EXEC`）。
- **取图形验证码**：利用 `render()` 把 4 位数字写成 SVG `<text>` 节点、而 SVG 是 base64 明文这一点解码，
  与代码零耦合；**没有依赖** `println` 那条调试卷码（它只被用来佐证 F6 的风险）。
- **测试脚本**：全部用 Python（stdlib + openpyxl），不用 curl。
  原因：Windows 上 curl 会把 argv 里的中文按 ANSI 代码页（GBK）编码，后端收到非法 UTF-8 直接 400——
  这是本次踩到的第一个坑，已在第 6 节记录。脚本在 `docs/gate-test/`：

  | 文件 | 用途 |
  | --- | --- |
  | `harness.py` | HTTP/multipart/断言/结果记录/Redis 直连 |
  | `prep.py` | 阶段 0：环境自检 + 测试账号与部门准备 |
  | `fixtures.py` | 阶段 1：门禁人员夹具 |
  | `t_auth.py` `t_list.py` `t_create.py` `t_update.py` `t_review_a.py` `t_review_b.py` `t_del.py` `t_xls_export.py` `t_xls_import.py` `t_face.py` `t_face2.py` `t_pr.py` `t_audit.py` `t_ac.py` `t_atk.py` | 各用例组 |
  | `aggregate.py` | 汇总所有 `results_*.json` 到 `all_results.json` |

  执行顺序（有依赖，按此顺序跑）：
  `prep → fixtures → t_auth → t_list → t_xls_export → t_face → t_create → t_review_a → t_update → t_review_b → t_del → t_pr → t_xls_import → t_audit → t_ac → t_atk → t_face2 → aggregate`

  > **注意**：脚本运行期会在本目录生成 `tokens.json`（各角色的 JWT 令牌）与若干 `*.json` 中间状态。
  > 本次执行结束后已**删除 `tokens.json`**（里面是真实可用的登录凭据，不应进仓库）；
  > 重新执行时由 `prep.py` 重新登录并生成。建议把 `docs/gate-test/*.json` 中除 `all_results.json`
  > 以外的运行期文件加进 `.gitignore`。

## 附录 B：本次绕过 F1 的手段（仅测试用，不是修复）

因为 F1 导致 `PUT /api/auth/working-department` 写不进 Redis，而下游 `A2 = 平台管理收窄到 D1` 的用例
必须先处于「已切换」状态，脚本用一个直连 Redis 的辅助函数把会话里的 `working_department_id`
直接写进去（`harness.redis_client().set_working_department(token, dept_id)`）。

写入的字段与 `replaceWorkingDepartment` **成功路径完全一致**，因此下游用例的结论有效。
**这不是修复方案**——正式修复请按 F1 的建议改代码。

> 顺带说明：F1 的一个直接推论是 **`TC-AUTH-06`「切换成功」这条用例本身是假通过**——
> 它断言的是 PUT 的响应体，而响应体里的 `current_id` 来自入参而非会话。本次已补 `TC-AUTH-06b`
> （会话复查）特意把这一点暴露出来。
