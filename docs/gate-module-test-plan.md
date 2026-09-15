# 门禁模块 · 真实数据测试方案（用例设计）

> 目标：在**真实数据库** `cartesktest@192.168.1.95` 上，用不同角色/不同数据范围的账号，验证门禁模块
> （门禁人员录入、审核、删除申请、导入导出、人脸文件、范围可见性）的行为与预期是否一致。
>
> 本文只做**设计**：列出账号矩阵、前置数据、每类用户的具体操作与预期结果。执行结果填到第 7 节的记录表里。
>
> 编写时间：2026-09-15。基线数字取自当时的实库快照。
>
> ---
>
> **⚠️ 2026-09-15 已执行完毕，本文有若干处与实现不符，已就地标注。**
> 完整实测结果、9 条新发现缺陷（F1~F9）及其根因与修复建议见
> **`docs/gate-module-test-report.md`**；缺陷已修复并完成实机复测（39 条全通过）。
> 本文中标了「**2026-09-15 实测勘误**」的地方是设计与实现不一致之处——
> 下一次执行前请先读这些标注，否则会照着错的预期判失败。
>
> 另外：**本机（Windows）的 curl 会把 argv 里的中文按 GBK 编码**，带中文的 `-d` / `-F`
> 要么被后端判成非法 UTF-8 直接 400，要么静默写进乱码。本文脚本里的中文一律要 percent-encode，
> 更稳的做法是改用 `docs/gate-test/harness.py` 那套 Python 封装。

---

## 0. 结论速览（先看这一页）

| 维度 | 设计要点 |
| --- | --- |
| 角色 | SUPER_ADMIN / ADMIN / DEPT_ADMIN / USER 四类，共 8 个测试身份（3 个需新建，5 个复用/派生） |
| 核心变量 | **数据范围**（ALL / DEPARTMENTS / SELF），不是权限码本身 |
| 主要对抗点 | 跨部门按 ID 越权、跨部门导入、跨部门人脸下载、批量审核中的范围外成员、状态机绕过 |
| 已知缺口 | 4 项（G1~G4，见第 6 节），其中 G1「同步状态永远不可能变为已同步」会直接导致一个筛选功能恒为空 |
| 副作用 | 会向 `cartesktest` 写入 `gate_person` / `gate_delete_request` / `users` / `department` / `stored_files` / `audit_event` 数据；清理方案见第 8 节 |

---

## 1. 被测对象（代码事实，先对齐）

### 1.1 接口清单与权限

基址 `http://127.0.0.1:8080`。以下权限码全部来自 `PermissionCatalog`，注解来自 `ParkingApiController` / `ExcelController` / `FileController`。

| # | 方法 | 路径 | 要求权限 | 数据范围如何生效 |
| --- | --- | --- | --- | --- |
| E1 | GET | `/api/gate-persons` | `gate-person:read` | `visibleInScope` 内存过滤后分页，`total` 是过滤后总数 |
| E2 | GET | `/api/gate-persons/{id}` | `gate-person:read` | `requireVisibleRow`，范围外与不存在**同错** |
| E3 | POST | `/api/gate-persons` (multipart) | `gate-person:manage` | 写入前 `requireDepartmentCodeAllowed` |
| E4 | PUT | `/api/gate-persons/{id}` (multipart) | `gate-person:manage` | 读取 + 写入都校验范围 |
| E5 | PUT | `/api/gate-persons/{id}/approve` | `gate-person:review` | `requireVisibleRow` |
| E6 | PUT | `/api/gate-persons/{id}/reject` | `gate-person:review` | `requireVisibleRow` |
| E7 | PUT | `/api/gate-persons/reviews` | `gate-person:review` | 范围只解析一次；任一 ID 越界 → **整批回滚** |
| E8 | DELETE | `/api/gate-persons/{id}` | `denyAll()` | 对**所有**角色 403 |
| E9 | POST | `/api/gate-persons/{id}/delete-requests` | `gate-person:manage` | `requireVisibleRow(人员)` |
| E10 | GET | `/api/gate-persons/delete-requests` | `gate-person:read` | `visibleInScope`（按申请单快照裁剪） |
| E11 | PUT | `/api/gate-persons/delete-requests/{id}/approve` | `gate-person:review` | 申请单 + **人员**双重校验 |
| E12 | PUT | `/api/gate-persons/delete-requests/{id}/reject` | `gate-person:review` | 仅申请单校验 |
| E13 | GET | `/api/person-records` | `person-record:read` | `personRecordsInScope` |
| E14 | GET | `/api/excel/gate-persons/template` | `gate-person:read` | `requireScopable` |
| E15 | GET | `/api/excel/gate-persons/export` | `gate-person:export` | 按范围裁剪 |
| E16 | POST | `/api/excel/gate-persons/import` | `gate-person:manage` | **强制落到当前工作部门** |
| E17 | GET | `/api/files/{uuid}/download` | `file:read` | `fileVisible`（上传者/部门/业务对象三路） |
| E18 | POST | `/api/access-controls` 等 | `access-control:*` | 见 5.11（无前端页，同步未接入） |

### 1.2 门禁人员状态机

```
录入(E3) / Excel导入           编辑且内容真变化(E4)         审核通过(E5/E7)
        │                              │                          │
        ▼                              │                          ▼
   ┌─────────┐   编辑内容变化    ┌──────┴──────┐             ┌──────────┐
   │ PENDING │ ←──────────────── │  APPROVED   │             │ APPROVED │
   │ 未同步  │                   │  未同步     │             │  未同步  │
   └────┬────┘                   └─────────────┘             └──────────┘
        │ 审核驳回(E6/E7)                                       ▲
        ▼                                                       │
   ┌──────────┐  再次编辑内容变化  ┌─────────┐                    │
   │ REJECTED │ ─────────────────→│ PENDING │────────────────────┘
   │ 未同步   │                   └─────────┘
   └──────────┘
```

硬性规则（`reviewGatePerson`）：

1. **只有 `PENDING` 可审核**，其余状态一律 400「该人员当前状态不允许审核，请编辑后重新提交」。
2. **驳回强制置 `NOT_SYNCED`**。
3. **`APPROVED`/`REJECTED` 被编辑且内容真变化 → 打回 `PENDING` + `NOT_SYNCED`**。
4. **只回填 `department_code`（历史行为 null）不算「内容变化」**，不退回待审核、不写更新审计。
5. 删除走「申请 → 审批」，`DELETE /api/gate-persons/{id}` 是 `denyAll()`。

### 1.3 数据范围规则（`DataScopeResolver`）

| 角色 | 默认范围 | 切换工作部门后 |
| --- | --- | --- |
| `SUPER_ADMIN` / `ADMIN` | `ALL` | 该部门**及其全部下级** |
| `DEPT_ADMIN` | 分配范围（显式分配 ∪ 归属部门，`include_descendants` 控制是否含下级） | 只能切到范围内部门；切了就用那一个（**不再展开下级**） |
| `USER` | `SELF`：本人车牌 + 本人门禁身份（按 `gate_person.phone` 匹配） | 无工作部门概念，切换接口 403 |

**失败语义是「看不到」**：`department_code` 为 null 且 `dept` 自由文本解析不出部门编码的行，对所有受限角色不可见。

---

## 2. 测试环境与实测基线

### 2.1 环境

| 项 | 值 |
| --- | --- |
| 后端 | `http://127.0.0.1:8080`（已确认 `/actuator/health` = 200） |
| 数据库 | `jdbc:postgresql://192.168.1.95:5432/cartesktest`（`JPA_DDL_AUTO=update`） |
| Redis | `192.168.1.95:6379` db5（JWT 会话 + 图形验证码） |
| 文件存储 | `C:\Users\28637\IdeaProjects\carTask\st1`，下载前缀 `http://127.0.0.1:8080` |
| 短信验证 | **`SMS_SKIP_VERIFICATION=true`（临时关闭）** → 短信登录/重置密码/换绑不校验验证码 |
| 管理员初始化 | `ADMIN_INITIALIZER_ENABLED=true` + `FORCE_WRITE=true` → `admin` 密码每次重启被重置为 `admin` |
| 图形验证码 | **登录必过**，校验先于凭据校验，答错不计入登录失败次数 |

### 2.2 数据基线（实测快照）

| 表 | 行数 | 说明 |
| --- | --- | --- |
| `users` | 689 | 1 个 `SUPER_ADMIN`（admin，无部门）+ 688 个 `USER`（全部 `department_id=1`） |
| `role` / `roles` | 4 | SUPER_ADMIN(65 权限) / ADMIN(60) / DEPT_ADMIN(49) / USER(3) |
| `user_roles` | 1 | **只有 admin 有角色关联**；688 个 USER 靠 `users.role` 字段 |
| `department` | **1** | id=1「同步车主」，编码 `AUTO-05800DE49C` |
| `user_managed_departments` | 0 | 无任何部门管理范围分配 |
| `gate_person` | **0** | 门禁人员表为空 |
| `gate_delete_request` | 0 | — |
| `person_access_record` | **0** | 人员进出记录为空（**且代码里没有任何写入通道**，见 G3） |
| `access_control` | 0 | 门禁授权表为空 |
| `parking_owner` | 688 | 全部 `dept='同步车主'`，`department_code=NULL`，`linked_user_id=NULL` |
| `parking_plate` | 809 | `linked_user_id` 全为 NULL |
| `access_record` | 6383 | 其中 `car_number IS NULL` 209 条（无牌车） |
| `stored_files` | 6437 | 同步抓拍图片 |
| `audit_event` | 7098 | 审计为追加写，测试后不清 |

关键关联事实：

- **688 个 USER 的 `phone` 与 688 个 `parking_owner.phone` 一一对应**（实测 join 命中 688/688）。
- 因此「给某个 USER 建一条 `gate_person`（phone 相同）」是打通 SELF 范围的最短路径。
- 所有 USER 的 `must_change_password = true`，**首次登录会被 `PasswordChangeRequiredFilter` 拦在 `/api/profile` 之外的一切接口**。
- 初始密码：`Fqjg20221022`（`InitialCredentials.PASSWORD`）。

### 2.3 取图形验证码的两种方法

**方法 A（推荐，与代码无耦合）**：`render()` 把 4 位数字写成 SVG 的 `<text>` 节点，而 SVG 是 base64 明文。

```bash
TOKEN_JSON=$(curl -s http://127.0.0.1:8080/api/auth/captcha)
B64=$(echo "$TOKEN_JSON" | sed -n 's/.*base64,//; s/".*//p')
CODE=$(echo "$B64" | base64 -d | grep -oP '(?<=<text[^>]*>)\d(?=</text>)' | tr -d '\n')
TOKEN=$(echo "$TOKEN_JSON" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "token=$TOKEN code=$CODE"
```

**方法 B（临时）**：后端 stdout 会打印 `验证码为：XXXX`。这是工作区未提交的调试代码
（`CaptchaService.kt` 中 `render()` 内的 `println`）——**提交前必须删除**，测试脚本不要依赖它。

### 2.4 通用登录封装

```bash
login() {  # login <username> <password>
  local j t c
  j=$(curl -s http://127.0.0.1:8080/api/auth/captcha)
  t=$(echo "$j" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  c=$(echo "$j" | sed -n 's/.*base64,//; s/".*//p' | base64 -d \
        | grep -oP '(?<=<text[^>]*>)\d(?=</text>)' | tr -d '\n')
  curl -s -X POST http://127.0.0.1:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\",\"captchaToken\":\"$t\",\"captchaAnswer\":\"$c\"}" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}
```

> 注意：`login` 失败 5 次会触发 15 分钟锁定（`LOGIN_RATE_LIMIT_MAX_ATTEMPTS=5`）。
> 测试脚本里凭据错误只允许试 1~2 次，其余用正确密码。

---

## 3. 测试账号矩阵

| 代号 | 角色 | 账号 / 手机号 | 来源 | 生效数据范围 | 在门禁模块能看到什么 |
| --- | --- | --- | --- | --- | --- |
| **S1** | `SUPER_ADMIN` | `admin` / `admin` | 已存在 | `ALL` | 全部门禁人员、全部删除申请、全部人脸、审计日志 |
| **A1** | `ADMIN` | `t_admin` / `Test@2026` | **需新建** | `ALL`（无部门归属，未切工作部门） | 除审计日志外的全部门禁能力（导出/审核/删除申请） |
| **A2** | `ADMIN` | 同 A1，切工作部门到 D1 | 派生 | `D1 + D1 下级` | 只见 D1 的门禁人员 |
| **B1** | `DEPT_ADMIN` | `t_dept_d1` / `Test@2026` | **需新建**，归属 D1，分配 `[D1]` | `D1` | 只见 D1 的门禁人员；可录入/审核/导出/申请删除 |
| **B2** | `DEPT_ADMIN` | `t_dept_d2` / `Test@2026` | **需新建**，归属 D2，分配 `[D2]` | `D2` | 只见 D2 的门禁人员 |
| **U1** | `USER` | `18950315520`（章规希，车主 1，车牌 `闽A167PG` 4 条记录） | 已存在，需改密 | `SELF` | 门禁人员页**不可见**；能查自己的人员/车辆进出记录 |
| **U2** | `USER` | `13705023122`（余义，车主 2，车牌 `闽A468KS` 6 条记录） | 已存在，需改密 | `SELF` | 同上，用于与 U1 交叉验证互不可见 |
| **U3** | `USER` | `t_user_none` / `Test@2026`，手机号 `13000000099` | **需新建** | `SELF` 全空 | 用于验证「无归属 = 什么都看不到」 |

### 3.1 账号准备步骤

```bash
# ── 0. 管理员令牌（admin 密码被 FORCE_WRITE 固定为 admin）
S1=$(login admin admin)

# ── 1. 建第二个部门 D2（现有库只有 D1，跨部门用例必须有第二个部门）
# ⚠️ 2026-09-15 实测勘误：路径是 /api/departments（不是 /api/depts），
#    参数走 query/form（不是 JSON body），字段名是 department_code / sort_order。
#    另外本机 curl 会把 argv 里的中文按 GBK 编码，中文值一律 percent-encode；
#    更稳的做法是改用脚本（见 docs/gate-test/harness.py）而不是 curl。
D2=$(curl -s -X POST "http://127.0.0.1:8080/api/departments?name=%E9%97%A8%E7%A6%81%E6%B5%8B%E8%AF%95%E9%83%A8%E9%97%A8&department_code=GATE-D2&sort_order=99" \
  -H "Authorization: Bearer $S1" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
D1=1

# ── 2. 建三个管理账号（roleIds: 2=ADMIN, 3=DEPT_ADMIN, 4=USER）
#    JSON 入口是 POST /api/users（consumes=application/json），需要 deptId + phone + roleIds
mkins() {  # mkins <username> <name> <phone> <deptId> <roleId>
  curl -s -X POST http://127.0.0.1:8080/api/users -H "Authorization: Bearer $S1" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"Test@2026\",\"name\":\"$2\",\"deptId\":$4,\"phone\":\"$3\",\"roleIds\":[$5],\"status\":1}" \
    | sed -n 's/.*"id":\([0-9]*\).*/\1/p'
}
A1_ID=$(mkins t_admin     "门禁测试-平台管理"   13000000001 $D1 2)
B1_ID=$(mkins t_dept_d1   "门禁测试-部门管理D1" 13000000002 $D1 3)
B2_ID=$(mkins t_dept_d2   "门禁测试-部门管理D2" 13000000003 $D2 3)
U3_ID=$(mkins t_user_none "门禁测试-普通无归属" 13000000099 $D1 4)

# ── 3. 分配部门管理范围（只有 SUPER_ADMIN + user:role-assign 能做）
curl -s -X PUT "http://127.0.0.1:8080/api/users/$B1_ID/managed-departments" \
  -H "Authorization: Bearer $S1" -H 'Content-Type: application/json' \
  -d "{\"departments\":[{\"department_id\":$D1,\"include_descendants\":false}]}"
curl -s -X PUT "http://127.0.0.1:8080/api/users/$B2_ID/managed-departments" \
  -H "Authorization: Bearer $S1" -H 'Content-Type: application/json' \
  -d "{\"departments\":[{\"department_id\":$D2,\"include_descendants\":false}]}"

# ── 4. 新建账号首次登录必须改密（mustChangePassword 默认 true），改密后会话全失效需重登
#    对 t_admin / t_dept_d1 / t_dept_d2 / t_user_none 各做一次：
T=$(login t_admin Test@2026)
curl -s -X PUT http://127.0.0.1:8080/api/profile/password -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' \
  -d '{"current_password":"Test@2026","new_password":"Test@2026x"}'
# 之后统一用 Test@2026x 登录

# ── 5. 普通用户 U1 / U2 走短信登录（短信验证已关闭，任意 code 均可），再改密
TU1=$(curl -s -X POST http://127.0.0.1:8080/api/auth/sms/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"18950315520","code":"000000"}' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
curl -s -X PUT http://127.0.0.1:8080/api/profile/password -H "Authorization: Bearer $TU1" \
  -H 'Content-Type: application/json' \
  -d '{"current_password":"Fqjg20221022","new_password":"Test@2026x"}'
# U2 同理（13705023122）
```

> **改密是有副作用的**：`ProfileServiceImpl.changePassword` 会 `incrementTokenVersion`，撤销该账号全部历史会话。
> 这是真实业务数据被修改，执行前请确认这些账号属于测试范围。

---

## 4. 前置测试数据构造

### 4.1 门禁人员（走真实接口，不直接 INSERT）

```bash
mkface() { printf '\x89PNG\r\n\x1a\n' > /tmp/f.png; head -c 200 /dev/urandom >> /tmp/f.png; }
mkface

# D1 门下 3 人：1 人 PENDING、1 人 APPROVED、1 人用于删除申请
curl -s -X POST http://127.0.0.1:8080/api/gate-persons -H "Authorization: Bearer $B1" \
  -F 'code=GP-D1-001' -F 'dept=同步车主' -F 'name=章规希' \
  -F 'phone=18950315520' -F 'idCard=350102199001010011' -F 'face=@/tmp/f.png;type=image/png'
curl -s -X POST http://127.0.0.1:8080/api/gate-persons -H "Authorization: Bearer $B1" \
  -F 'code=GP-D1-002' -F 'dept=同步车主' -F 'name=门禁测试甲' \
  -F 'phone=13000000201' -F 'idCard=350102199001010012' -F 'face=@/tmp/f.png;type=image/png'
curl -s -X POST http://127.0.0.1:8080/api/gate-persons -H "Authorization: Bearer $B1" \
  -F 'code=GP-D1-003' -F 'dept=同步车主' -F 'name=门禁测试乙' \
  -F 'phone=13000000202' -F 'idCard=350102199001010013' -F 'face=@/tmp/f.png;type=image/png'

# D2 门下 1 人：由 B2 录入
curl -s -X POST http://127.0.0.1:8080/api/gate-persons -H "Authorization: Bearer $B2" \
  -F 'code=GP-D2-001' -F 'dept=门禁测试部门' -F 'name=门禁测试丙' \
  -F 'phone=13000000203' -F 'idCard=350102199001010014' -F 'face=@/tmp/f.png;type=image/png'
```

> `dept` 字段填部门**名称**或**编码**均可，`stampDepartmentCode` 先按编码、后按名称精确匹配。
> 该字段会写入 `department_code`，是范围判定的唯一依据。

### 4.2 「解析不出部门」的 fail-closed 行（直连 SQL，仅此一处需要）

```sql
-- 目的：验证 dept 自由文本无法解析 + department_code 为 NULL 时，对所有受限角色不可见
INSERT INTO gate_person (code, dept, department_code, name, phone, id_card, face,
                         create_time, approve_status, sync_status, updated_at)
VALUES ('GP-ORPHAN', '这是一个不存在的部门', NULL, '孤儿数据', '13000000299',
        '350102199001010099', NULL, now(), 'PENDING', 'NOT_SYNCED', now());
```

### 4.3 人员进出记录（直连 SQL —— 该表无任何写入接口，见 G3）

```sql
-- U1 本人记录（linked_user_id 命中 SELF 范围的主路径）
INSERT INTO person_access_record (person, card_id, dept, department_code, linked_user_id,
                                  time, direction, gate, method, status, photo)
VALUES ('章规希', '4179', '同步车主', 'AUTO-05800DE49C', 2, now() - interval '1 hour',
        '进', '测试通道A', '人脸识别', '正常', NULL),
       ('章规希', '4179', '同步车主', 'AUTO-05800DE49C', 2, now() - interval '30 minutes',
        '出', '测试通道A', '人脸识别', '正常', NULL),
       -- U2 的记录：用于验证 U1 看不到 U2 的
       ('余义', '7405', '同步车主', 'AUTO-05800DE49C', 3, now() - interval '20 minutes',
        '进', '测试通道B', '人脸识别', '正常', NULL),
       -- 卡号命中门禁人员但 linked_user_id 为空：验证 cardId 分支
       ('门禁测试甲', 'GP-D1-002', '同步车主', 'AUTO-05800DE49C', NULL, now() - interval '10 minutes',
        '进', '测试通道C', '刷卡', '正常', NULL);
```

> `linked_user_id` 的取值请先用 `SELECT id FROM users WHERE phone='18950315520'` 确认真实值（基线为 2）。

---

## 5. 用例设计

图例：**✅ 通过** = 预期正常返回；**⛔ 403** = `AccessDeniedException` → 403「禁止访问」；
**🚫 400** = `IllegalArgumentException` → 400（带业务消息）；**🔒 401** = 未认证。

### 5.1 登录闸门与工作部门（TC-AUTH-*）

| ID | 账号 | 操作 | 预期结果 | 理由 |
| --- | --- | --- | --- | --- |
| TC-AUTH-01 | S1 | `GET /api/auth/session` | 200，`role=SUPER_ADMIN`，`working_department_id=null`，`scope=ALL`，可选部门含 D1+D2 | `defaultWorkingDepartment` 只对 DEPT_ADMIN 生效 |
| TC-AUTH-02 | A1 | 登录 → `GET /api/auth/session` | 200，`working_department_id=null`，`scope=ALL` | 平台管理默认全局 |
| TC-AUTH-03 | B1 | 登录 → 检查工作部门 | **默认已落在 D1**（不是 null） | `DEPT_ADMIN → user.department.id` |
| TC-AUTH-04 | B1 | `PUT /api/auth/working-department?department_id=<D2>` | ⛔ 403「无权切换到该部门」 | D2 不在 B1 的分配范围 |
| TC-AUTH-05 | B1 | `PUT /api/auth/working-department`（不带参数） | ⛔ 403「必须选择一个工作部门」 | 部门管理不能选「全部」 |
| TC-AUTH-06 | A1 | `PUT /api/auth/working-department?department_id=<D1>` → `GET /api/auth/session` | 200，`scope=DEPARTMENT`，`working_department_id=D1` | 全局角色可收窄 |
| TC-AUTH-07 | U1 | `PUT /api/auth/working-department` | ⛔ 403「普通用户没有工作部门」 | — |
| TC-AUTH-08 | U1 | 改密前访问 `GET /api/gate-persons` | ⛔ 403「首次登录必须先修改密码」 | `PasswordChangeRequiredFilter` |
| TC-AUTH-09 | U1 | 改密后重登，再访问 | ⛔ 403（无 `gate-person:read`）——与 TC-AUTH-08 的消息**不同** | 两条闸门必须能区分 |
| TC-AUTH-10 | S1 | `PUT /api/auth/working-department?department_id=99999` | 🚫 400「部门不存在」 | — |

### 5.2 列表与详情 · 范围可见性（TC-LIST-*）

**前置**：4.1 造完 4 人（GP-D1-001/002/003 属 D1，GP-D2-001 属 D2），4.2 造 GP-ORPHAN。

| ID | 账号 | 操作 | 预期 `total` | 预期可见 code | 理由 |
| --- | --- | --- | --- | --- | --- |
| TC-LIST-01 | S1 | `GET /api/gate-persons?pageSize=100` | **5** | 全部 5 条 | `ALL` 不过滤 |
| TC-LIST-02 | A1（未切部门） | 同上 | **5** | 全部 5 条 | `ALL` |
| TC-LIST-03 | A2（切 D1） | 同上 | **4** | GP-D1-001/002/003 **+ GP-ORPHAN？** | 见下方⚠️ |
| TC-LIST-04 | B1（范围 D1） | 同上 | **3** | GP-D1-001/002/003 | GP-ORPHAN 因 `department_code=NULL` 且 dept 不可解析 → 不可见 |
| TC-LIST-05 | B2（范围 D2） | 同上 | **1** | GP-D2-001 | 跨部门互不可见 |
| TC-LIST-06 | U1 / U2 / U3 | `GET /api/gate-persons` | — | ⛔ 403 | USER 角色无 `gate-person:read` |
| TC-LIST-07 | B1 | `GET /api/gate-persons?dept=门禁测试部门` | **0** | — | 服务端先按范围裁剪，再按 `dept` 精确匹配；D2 的行已被裁掉 |
| TC-LIST-08 | B1 | `GET /api/gate-persons?approveStatus=审核中` | 仅 PENDING 的条数 | — | 同时接受 `审核中` 与 `PENDING` 两种写法 |
| TC-LIST-09 | B1 | `GET /api/gate-persons?syncStatus=已同步` | **0** | — | 见 **G1**：没有任何接口能把状态置为 SYNCED |
| TC-LIST-10 | B1 | `GET /api/gate-persons?pageSize=101` | 🚫 400「每页数量必须在 1 到 100 之间」 | — |
| TC-LIST-11 | B1 | `GET /api/gate-persons/{GP-D2-001的id}` | 🚫 400「人员不存在」 | 范围外与不存在**同错**，防止按响应差异探测 |
| TC-LIST-12 | B1 | `GET /api/gate-persons/99999999` | 🚫 400「人员不存在」 | 与 TC-LIST-11 响应**完全一致** |
| TC-LIST-13 | S1 / B1 | `GET /api/gate-persons?pageSize=2&page=2` | 200，`items.size ≤ 2`，`total` 与全量一致 | 内存分页，`total` 精确 |

> ⚠️ **TC-LIST-03 需要实测确认**：A2 的范围是 `departmentScope(D1, expandDescendants=true)`，即 D1 的**编码集合**
> `{AUTO-05800DE49C}`。GP-ORPHAN 的 `department_code` 是 NULL，`dept` 无法解析 → 按 fail-closed 应当**不可见**，
> 预期 total=3（与 B1 相同）。若实测为 4，说明「下级展开」路径漏了 fail-closed 判定，是缺陷。

### 5.3 单条录入（TC-CREATE-*）

**前置**：B1 工作部门=D1，B2 工作部门=D2。

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-CREATE-01 | B1 | 新建 `code=GP-D1-010`，`dept=同步车主`，含人脸 | **201**，`approveStatus=审核中`，`syncStatus=未同步` | 新录入一律待审核 |
| TC-CREATE-02 | S1 | 新建 `code=GP-G-001`，`dept=门禁测试部门` | **201** | 全局角色可写任意部门 |
| TC-CREATE-03 | B1 | 新建 `dept=门禁测试部门`（D2） | ⛔ 403「无权在其它部门下操作数据」 | `requireDepartmentCodeAllowed` |
| TC-CREATE-04 | B2 | 新建 `dept=同步车主`（D1） | ⛔ 403 同上 | — |
| TC-CREATE-05 | B1 | 新建 `dept=这是一个不存在的部门` | ⛔ 403 | `stampDepartmentCode` 返回 null → 视为越界 |
| TC-CREATE-06 | A2（切 D1） | 新建 `dept=门禁测试部门` | ⛔ 403 | 平台管理收窄后同样受限 |
| TC-CREATE-07 | B1 | `code=GP-D1-001`（已存在） | 🚫 400「人员编号已存在」，**且不产生任何文件** | 唯一性检查先于上传 |
| TC-CREATE-08 | B1 | `idCard=350102199001010011`（已存在） | 🚫 400「身份证号已存在」 | — |
| TC-CREATE-09 | B1 | `phone=abc` | 🚫 400「手机号格式不正确，只能填写数字」 | 正则 `^\d{6,20}$` |
| TC-CREATE-10 | B1 | `idCard=35010219900101`（14 位） | 🚫 400「身份证号必须为 18 位」 | — |
| TC-CREATE-11 | B1 | 不传 `face` | 🚫 400（缺少必需 part） | 新建人脸必填 |
| TC-CREATE-12 | B1 | `face` 传 3MB PNG | 🚫 400「人脸照片不能超过 2MB」 | — |

> ⚠️ **2026-09-15 实测勘误 + 已修复**：这条预期原本**不成立**——`spring.servlet.multipart.max-file-size`
> 没有配置时 Spring Boot 默认只有 1MB，小于业务常量 2MB，请求在进 Controller 之前就被容器拦成
> **413 `Uploaded file exceeds the configured size limit.`**，`requireImageUpload` 里那句
> 「人脸照片不能超过 2MB」是永远执行不到的死代码。
> 已在 `application.yaml` 显式配置 `max-file-size: 5MB`（**必须大于业务上限 2MB**，否则业务校验仍然到不了），
> 并把 413 的文案中文化。修复后 2.1MB 确实返回 400「人脸照片不能超过 2MB」，本行预期成立。
| TC-CREATE-13 | B1 | `face` 传 `note.txt`（内容为纯文本，文件名改 `note.png`、Content-Type 伪造 `image/png`） | 🚫 400「人脸照片内容不是受支持的图片」 | 魔数校验；**这是最容易被绕过的一层** |
| TC-CREATE-14 | B1 | `face` 传 `.webp`，内容实为 WAV（`RIFF....WAVE`） | 🚫 400 同上 | WebP 需核对偏移 8 处标识 |
| TC-CREATE-15 | B1 | `face` 传 `a.gif`（真实 GIF） | **201** | 白名单含 gif |
| TC-CREATE-16 | U1 / U3 | 新建任意人员 | ⛔ 403 | 无 `gate-person:manage` |

### 5.4 编辑与状态回退（TC-UPDATE-*）

**前置**：GP-D1-002 已审核通过（先执行 TC-REVIEW-01）。

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-UPDATE-01 | B1 | 把 GP-D1-002 的 `name` 从「门禁测试甲」改成「门禁测试甲2」 | 200，`approveStatus` **回到「审核中」**，`syncStatus=未同步`，写 `GATE_PERSON_UPDATED` 审计 | 防「先过审再改」绕过审核 |
| TC-UPDATE-02 | B1 | 原样回填 GP-D1-002 全部字段（值都不变，不传 face） | 200，状态**保持**当前值不变，**不写**更新审计 | 「无变化不算改动」 |
| TC-UPDATE-03 | B1 | 只传 `dept=同步车主`（`department_code` 从 NULL 回填为编码的行） | 200，状态不变 | 编码回填不是用户可见内容 |
| TC-UPDATE-04 | B1 | 改 GP-D2-001 的 `name` | 🚫 400「人员不存在」 | 编辑入口先做范围校验 |
| TC-UPDATE-05 | B1 | 把 GP-D1-001 的 `dept` 改成「门禁测试部门」 | ⛔ 403「无权在其它部门下操作数据」 | 防止把数据挪出范围 |
| TC-UPDATE-06 | B1 | 改 `code` 为 `GP-D1-002`（与他人重复） | 🚫 400「人员编号已存在」 | — |
| TC-UPDATE-07 | B1 | 改 GP-D1-001 的 `dept` 从「同步车主」改成「门禁测试部门」，同时改 `name` | ⛔ 403，**且 name 也不能被改** | 方法级事务：抛异常整条回滚 |
| TC-UPDATE-08 | B1 | 给 GP-D1-001 换一张人脸 | 200，`face` URL 变化；状态回「审核中」；旧文件按新 code/部门重新挂载 | `relinkBusiness` |
| TC-UPDATE-09 | S1 | 改 GP-D1-001 任意字段 | 200 | 全局不受限 |
| TC-UPDATE-10 | U3 | 改任意人员 | ⛔ 403 | 无 manage |

### 5.5 审核（TC-REVIEW-*）

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-REVIEW-01 | B1 | `PUT /api/gate-persons/{GP-D1-002}/approve` | 200「审批通过」，`approveStatus=通过`，写 `GATE_PERSON_REVIEWED` 审计 | — |
| TC-REVIEW-02 | B1 | 对同一条**再次** approve | 🚫 400「该人员当前状态不允许审核，请编辑后重新提交」 | 状态机单向 |
| TC-REVIEW-03 | B1 | 对同一条 reject | 🚫 400 同上 | 已决记录不能改判 |
| TC-REVIEW-04 | B1 | `reject` GP-D1-003，reason=「证件不清晰」 | 200「审批拒绝」，`approveStatus=拒绝`，`syncStatus=未同步` | 驳回强制重置同步状态 |
| TC-REVIEW-05 | B1 | 审核 GP-D2-001 | 🚫 400「人员不存在」 | 范围校验 |
| TC-REVIEW-06 | B1 | `PUT /api/gate-persons/reviews`，body `{ids:[GP-D1-002, GP-D2-001], approved:true}` | 🚫 400，**整批回滚，GP-D1-002 状态不变** | 批量里混入范围外成员 |
| TC-REVIEW-07 | B1 | 批量审核 200 条（构造 200 个 PENDING） | 200「已审核 200 人」 | 上限边界 |
| TC-REVIEW-08 | B1 | 批量审核 201 条 | 🚫 400「单次批量审核不能超过 200 人，请分批提交」 | — |
| TC-REVIEW-09 | B1 | `{ids:[1,1], approved:true}` | 🚫 400「审核记录的 ID 不能重复」 | — |
| TC-REVIEW-10 | B1 | `{ids:[], approved:true}` | 🚫 400「审核列表不能为空」 | — |
| TC-REVIEW-11 | B1 | `{ids:[1]}`（不传 approved） | 🚫 400「必须指定审核结论」 | — |
| TC-REVIEW-12 | A1 / S1 | 审核 D1 与 D2 的人各一次 | 200 | 全局范围 |
| TC-REVIEW-13 | U1 | 审核任意人员 | ⛔ 403 | 无 `gate-person:review` |

### 5.6 删除申请与审批（TC-DEL-*）

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-DEL-01 | B1 | `DELETE /api/gate-persons/{GP-D1-003}` | ⛔ 403 | `@PreAuthorize("denyAll()")`，**对 S1/A1 也是 403** |
| TC-DEL-02 | S1 | 同上 | ⛔ 403 | 确认没有「超管后门」 |
| TC-DEL-03 | B1 | `POST /api/gate-persons/{GP-D1-003}/delete-requests`，`reason="离职"` | 201，`status=待处理`，写 `GATE_DELETE_REQUESTED` 审计 | — |
| TC-DEL-04 | B1 | 同上但 `reason=""` / 不传 | 🚫 400「删除原因不能为空」 | — |
| TC-DEL-05 | B1 | 对 GP-D2-001 提删除申请 | 🚫 400「人员不存在」 | 范围校验 |
| TC-DEL-06 | B1 | `GET /api/gate-persons/delete-requests` | 200，**只含 D1 的申请**，`total` 与 D1 一致 | 申请单按 `department_code` 裁剪 |
| TC-DEL-07 | B2 | `GET /api/gate-persons/delete-requests` | 200，**看不到 D1 的申请**（含身份证/手机号快照） | 防跨部门敏感信息泄露 |
| TC-DEL-08 | U1 | `GET /api/gate-persons/delete-requests` | ⛔ 403 | 无 `gate-person:read` |
| TC-DEL-09 | B1 | `PUT .../delete-requests/{id}/approve` | 200「已同意删除申请」；人员**物理删除**；写 `GATE_DELETE_REQUEST_REVIEWED` + `GATE_PERSON_DELETED`（CRITICAL）两条审计 | — |
| TC-DEL-10 | B1 | 对同一申请再次 approve | 🚫 400「删除申请已处理」 | — |
| TC-DEL-11 | B1 | `reject` 另一条申请 | 200「已拒绝删除申请」，`status=已拒绝`，**人员仍在** | — |
| TC-DEL-12 | B1 | 对 D2 的申请 approve/reject | 🚫 400「删除申请不存在」 | — |
| TC-DEL-13 | **B1** | 先由 S1 把 D1 的某人 `dept` 改到 D2，再由 B1 审批该人的既有删除申请 | 🚫 400「人员不存在」，申请单状态**保持待处理** | 人员已移出范围，申请单可见性不构成删除授权 |
| TC-DEL-14 | B2 | 审批 D1 的申请 | 🚫 400「删除申请不存在」 | — |
| TC-DEL-15 | U3 | 提交删除申请 | ⛔ 403 | 无 manage |

### 5.7 导入导出（TC-XLS-*）

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-XLS-01 | B1 | `GET /api/excel/gate-persons/template` | 200，xlsx，含「人员编号/部门/姓名/手机号/身份证号」表头 | 需要 `gate-person:read` |
| TC-XLS-02 | U1 / U3 | 同上 | ⛔ 403 | — |
| TC-XLS-03 | B1 | `GET /api/excel/gate-persons/export` | 200，**只含 D1 的 3 人** | 按范围裁剪 |
| TC-XLS-04 | B2 | 同上 | 200，**只含 D2 的 1 人** | 与 TC-XLS-03 行数不同即证明裁剪生效 |
| TC-XLS-05 | A1（未切） | 同上 | 200，5 人 | 全局 |
| TC-XLS-06 | A2（切 D1） | 同上 | 200，3 人（同 B1） | — |
| TC-XLS-07 | S1 | `GET /api/excel/gate-persons/export` | 200 | — |
| TC-XLS-08 | **B1** | `POST /api/excel/gate-persons/import`，表里 `部门=门禁测试部门` | 🚫 400「第N行只能导入到当前工作部门 同步车主（AUTO-05800DE49C）」 | **不静默改写**，带行号 |
| TC-XLS-09 | B1 | 导入，表里 `部门` 留空 | 201，全部落到 D1（`dept=同步车主`） | `forcedDepartment` 兜底 |
| TC-XLS-10 | B1 | 导入，表里 `部门=同步车主` | 201 | — |
| TC-XLS-11 | A1（未切部门） | 导入 `部门=门禁测试部门` | 201，落到 D2 | 全局范围不强制部门 |
| TC-XLS-12 | B1 | 导入两行相同 `人员编号` | 🚫 400「导入文件中的人员编号不能重复」 | 整份文件失败回滚 |
| TC-XLS-13 | B1 | 导入的 `人员编号` 与库中已存在（含 **D2** 的 `GP-D2-001`）相同 | 🚫 400「导入文件中包含已存在的人员编号」 | ⚠️ 见 **G4**：跨部门唯一性会泄露「别的部门存在该编号」 |
| TC-XLS-14 | B1 | 导入 1 行合法 + 1 行非法身份证 | 🚫 400，**合法那行也不落库** | 事务回滚 |
| TC-XLS-15 | B1 | 导入含 1 万行的合法文件 | 201，`count=10000`；审计只采样前 20 个 code | 避免审计记录被撑爆 |
| TC-XLS-16 | A1（切到 D1，但把工作部门手工改成 D2 之外的部门后） | 导入 | 🚫 400「当前工作部门不在你的管理范围内，请切换工作部门后再导入」 | 会话里的工作部门可能已脱节 |
| TC-XLS-17 | U1 | 导入 / 导出 | ⛔ 403 | — |
| TC-XLS-18 | B1 | `GET /api/excel/all/template` | ⛔ 403 | 需要 `department:manage` 等一串 manage，DEPT_ADMIN 被排除 |

### 5.8 人脸文件（TC-FACE-*）

**前置**：记录 TC-CREATE-01 返回的 `face` URL（形如 `http://127.0.0.1:8080/api/files/<uuid>/download`）。

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-FACE-01 | B1 | `GET <GP-D1-001.face>` | 200，返回图片字节 | 同部门 |
| TC-FACE-02 | B2 | 同上 | 🔒 **404「文件不存在」** | `fileVisible` 的部门分支不命中；与「文件真不存在」同错 |
| TC-FACE-03 | A1（未切部门） | 同上 | 200 | `ALL` |
| TC-FACE-04 | A2（切 D1） | 同上 | 200 | D1 在内 |
| TC-FACE-05 | A2（切 D2） | 同上 | 404 | 已收窄到 D2 |
| TC-FACE-06 | S1 | 同上 | 200 | — |
| TC-FACE-07 | U1 | 同上 | 本人门禁身份关联的照片 **200**；别人的 **404** | **2026-09-15 已改**：原来 USER 没有 `file:read` 一律 403，导致 `fileVisible` 的 SELF 分支永远走不到（G2）。已给 USER 授予 `file:read` 并放开 `FileController` 的读接口角色条件；权限先于范围判定这条不变，只是现在能走到范围判定了 |
| TC-FACE-08 | B1 | 经 `businessType=gate_person, businessId=GP-D1-001` 反查（`ScopeQuerySupport.gatePersonCodesInScope`） | 命中 | 业务对象反查路径 |
| TC-FACE-09 | B1 | `GET /api/files/<不存在的uuid>` | 404「文件不存在」 | 与 TC-FACE-02 响应一致 |
| TC-FACE-10 | B1 | 删除人员（TC-DEL-09 之后）再 `GET` 其人脸 | 404 | `unlinkBusiness` 把 `department_code` 与业务关联清空 |
| TC-FACE-11 | B1 | 把 GP-D1-001 的 `code` 改成 `GP-D1-001B` 后再取旧 URL | 200 仍可下载（业务 id 已重挂到新 code，文件本身未动） | `relinkBusiness` |
| TC-FACE-12 | B1 | 把 GP-D1-001 从 D1 改到 D2（需 S1 操作）后，B1 取旧 URL | 404 | 归属部门快照随重挂载变更 |

### 5.9 人员进出记录 · 普通用户自助（TC-PR-*）

**前置**：4.3 已插入 4 条记录；GP-D1-001 的 `phone=18950315520` 已存在（这使 U1 的 SELF 范围含 `gatePersonCodes=[GP-D1-001]`）。

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-PR-01 | U1 | `GET /api/person-records` | 200，`total=2`（`linked_user_id=U1` 的 2 条），看不到「余义」的 | SELF 分支第 1 路 |
| TC-PR-02 | U2 | 同上 | 200，`total=1`（余义），看不到章规希的 | — |
| TC-PR-03 | U3 | 同上 | 200，`total=0` | SELF 全空 = 默认拒绝，**不是错误** |
| TC-PR-04 | U1 | `GET /api/person-records?keyword=余义` | 200，`total=0` | 先在范围内裁剪再按关键词过滤 |
| TC-PR-05 | B1 | `GET /api/person-records` | 200，`total=4`（`department_code=AUTO-05800DE49C` 全部命中） | DEPARTMENTS 分支 |
| TC-PR-06 | B2 | 同上 | 200，`total=0` | D2 下无记录 |
| TC-PR-07 | U1 | `GET /api/excel/person-records/export` | 404 / 405 —— **该接口不存在** | 前端「导出」是本地 CSV，见 **G5** |
| TC-PR-08 | U1 | `GET /api/person-records?pageSize=101` | 🚫 400 | — |

> **注意**：`person_access_record` 无任何写入接口（**G3**），因此本节依赖 4.3 的直连 SQL。
> 该表的 `linked_user_id` 由「写入时按卡号匹配 GatePerson」得到——既然写入通道不存在，这个字段在生产中永远是 NULL。

### 5.10 审计留痕（TC-AUDIT-*）

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-AUDIT-01 | 任意 | 执行 TC-CREATE-01 / TC-UPDATE-01 / TC-REVIEW-01 / TC-DEL-03 / TC-DEL-09 | `audit_event` 依次新增 `GATE_PERSON_CREATED` / `GATE_PERSON_UPDATED` / `GATE_PERSON_REVIEWED` / `GATE_DELETE_REQUESTED` / `GATE_DELETE_REQUEST_REVIEWED` + `GATE_PERSON_DELETED` | 见 `AuditAction` |
| TC-AUDIT-02 | — | 查 `GATE_PERSON_DELETED` 的 `risk_level` | `CRITICAL` | 人员物理删除单独立案 |
| TC-AUDIT-03 | — | 查 `GATE_PERSON_CREATED` 的 `risk_level` | `HIGH` | — |
| TC-AUDIT-04 | A1 | `GET /admin/api/audit-events` | ⛔ 403 | 审计查询要求 `hasRole('SUPER_ADMIN')`，ADMIN 也不行 |
| TC-AUDIT-05 | S1 | 同上 | 200 | — |
| TC-AUDIT-06 | B1 | 上述任一越权操作 | 新增 `AUTHORIZATION_DENIED` 审计（含 path/method） | `GlobalExceptionHandler.onAccessDeniedException` |
| TC-AUDIT-07 | — | 检查 TC-UPDATE-02（无变化提交） | **不产生** `GATE_PERSON_UPDATED` | — |
| TC-AUDIT-08 | — | 检查 TC-XLS-15（万行导入） | `GATE_PERSON_CREATED` 只有 1 条，`sample_codes` 长度 ≤ 20 | 采样保护 |

### 5.11 门禁授权（access_control）· 邻接模块（TC-AC-*）

> 该表**无前端页面**（与「门禁人员」是两码事），`synchronize` 明确抛 `IllegalStateException`。

| ID | 账号 | 操作 | 预期 | 理由 |
| --- | --- | --- | --- | --- |
| TC-AC-01 | B1 | `POST /api/access-controls` | 201，`reviewStatus=PENDING`，`synchronizedLoading=false` | — |
| TC-AC-02 | B1 | `PUT /api/access-controls/{id}/sync`（先审核通过） | **500** | `throw IllegalStateException("门禁设备同步尚未接入")`，**无专用异常处理器** → 落到兜底 500 |
| TC-AC-03 | B1 | `DELETE /api/access-controls/{id}` | ⛔ 403 | `denyAll()` |
| TC-AC-04 | B1 | `POST /api/access-controls/{id}/review?approved=true&review_reason=x` | 200 | 审核原因必填 |
| TC-AC-05 | B1 | `review` 时 `review_reason` 留空 | 🚫 400「审核原因不能为空」 | — |
| TC-AC-06 | B1 | 审核**范围外**部门的授权 | 🚫 400「记录不存在」 | 该表有真正的 `department_id` 外键 |

> ⚠️ **2026-09-15 实测勘误 + 已修复**：本行原本**不成立**。方案把「该表有真正的 department_id 外键」
> 当成了「有范围校验」，但实现里一行范围校验都没有——`list` 直接 `findAll(PageRequest)`，
> `get/update/review` 直接 `findById`，全仓库没有引用 `ScopeGuard`。实测 B1 能**列出、读取、审核** D2 的授权，
> 全部 200 放行。已补 `ScopeGuard.requireVisibleAccessControl` 并把范围下推到 SQL，修复后本行预期成立。
>
> 另外 `GET /api/audit-events`（方案 5.10 节写的路径）实际是 **404**，真实路径是 `/admin/api/audit-events`，
> 且 `occurred_from` / `occurred_to` 必填、窗口不得超过 31 天、列表字段是 `data.events`。

> ⚠️ **TC-AC-02 的 500 是设计问题**：接口约定应返回 400/501 而不是 500 空消息。执行后请在结论里记录。

### 5.12 越权与对抗性用例汇总（TC-ATK-*）

这一组是本方案的核心，全部**手动构造请求**，不走前端：

| ID | 攻击设想 | 操作 | 预期防线 |
| --- | --- | --- | --- |
| TC-ATK-01 | 用 ID 直接读别部门数据 | B1 `GET /api/gate-persons/{D2的id}` | 🚫 400，且与不存在同错（TC-LIST-11/12） |
| TC-ATK-02 | 用 ID 直接改别部门数据 | B1 `PUT /api/gate-persons/{D2的id}` | 🚫 400 |
| TC-ATK-03 | 用 ID 直接审别部门数据 | B1 `PUT /api/gate-persons/{D2的id}/approve` | 🚫 400 |
| TC-ATK-04 | 批量审核夹带 | B1 `reviews` 混入 D2 的 id | 🚫 400 + 整批回滚 |
| TC-ATK-05 | 伪造部门参数越权读 | B1 `GET /api/gate-persons?dept=门禁测试部门` | 200 但 `total=0`（服务端先裁范围） |
| TC-ATK-06 | 导入时写入别部门 | B1 导入 `部门=门禁测试部门` | 🚫 400 带行号 |
| TC-ATK-07 | 把数据挪出范围 | B1 改 `dept` 到 D2 | ⛔ 403 |
| TC-ATK-08 | 借删除申请删别部门的人 | B1 审批 D2 的申请 / 对 D2 的人提申请 | 🚫 400 |
| TC-ATK-09 | 先提申请再把人挪走，制造「申请可见但人员不可见」 | S1 改 dept 后 B1 审批 | 🚫 400，申请状态不变（TC-DEL-13） |
| TC-ATK-10 | 绕过审核：先过审再改 | B1 approve 后立刻改 `name` | 200，但状态回 PENDING（TC-UPDATE-01） |
| TC-ATK-11 | 用无归属账号试探 | U3 访问门禁全部接口 | 全 403；`/api/person-records` 返回 `total=0` 而非报错 |
| TC-ATK-12 | 用 UUID 直接下载别部门人脸 | B2 `GET /api/files/{D1的人脸uuid}/download` | 404（TC-FACE-02） |
| TC-ATK-13 | 用 UUID 下载「已删除人员」的人脸 | 删除后重放旧 URL | 404（TC-FACE-10） |
| TC-ATK-14 | 用手机号自助换绑窃取他人身份 | U1 `PUT /api/profile/phone` 换成 U2 的手机号 | 400「该手机号已被其他账号绑定」（短信验证已关闭，所以这是唯一防线） |
| TC-ATK-15 | 部门管理自我提权 | B1 `POST /api/users` 创建 `roleIds=[2]` 的账号 | ⛔ 403「只有超级管理员可以管理管理员角色」 |
| TC-ATK-16 | 部门管理改自己范围 | B1 `PUT /api/users/{自己}/managed-departments` | ⛔ 403（要求 SUPER_ADMIN） |
| TC-ATK-17 | 改他人手机号（手机号=登录凭据） | B1 `PUT /api/users/{D2用户的id}` 改 phone | ⛔ 403「无权操作其他部门的用户」 |
| TC-ATK-18 | fail-closed 绕过 | 直接 INSERT 一条 `department_code=NULL` 的门禁人员，看 B1/B2/A2 是否可见 | 均不可见（TC-LIST-03/04） |
| TC-ATK-19 | 枚举探测 | 连续请求 20 个不存在的 id，与 20 个范围外 id | 两类响应**完全一致**（状态码 + message） |
| TC-ATK-20 | 验证码绕过 | 不带 `captchaToken` 登录 | 401「请输入验证码」；连续答错 5 次 → 「验证码错误次数过多，请刷新」 |

---

## 6. 现状缺口（预期为「不通过」的项，需单独立项）

| 编号 | 缺口 | 证据 | 影响 |
| --- | --- | --- | --- |
| **G1** | **`syncStatus` 永远不可能变成「已同步」** | 全仓库搜索 `GatePerson.SyncStatus.SYNCED` 的写入点只有 `MockDataInitializer`（mock 数据）；`reviewGatePerson` 只写 `NOT_SYNCED`，`update` 只写 `NOT_SYNCED`。没有任何下发接口。 | 列表的「同步状态」筛选（含「已同步」选项）恒为空；`GATE_PERSON_CREATED` 审计里的 `synchronized` 恒 false。这是一个**看起来能用、实际永远为空**的功能。 |
| **G2** | **普通用户拿不到 `file:read`，无法查看自己的人脸照片** | `USER_PERMISSION_CODES` 只有 `dashboard:read` / `vehicle-record:read` / `person-record:read`；`FileController` 要求 `file:read`。 | 与「本人范围能取到自己的门禁图片」的设计意图（`fileVisible` 的 SELF 分支）不一致：那条分支**永远走不到**。<br>**2026-09-15 已修复**：给 USER 授予 `file:read`，并把 `FileController` 的 `get` / `download` 两个读接口的角色条件放开到 USER（这两个接口都经 `findVisibleFile` 按范围过滤）。实测 U1 能取到本人门禁身份的照片，别人的仍是 404。 |
| **G3** | **`person_access_record` 没有任何写入通道** | 全仓库写入点只有 `MockDataInitializer.kt:655`。 | 生产环境的「人员进出」页永远为空；`linkedUserId`（SELF 归属的关键字段）永远是 NULL，`personRecordsInScope` 的 SELF 分支实际失效。 |
| **G4** | **`code` / `idCard` 全局唯一 → 跨部门存在性侧信道** | `uk_gate_person_code` / `uk_gate_person_id_card` 是全局唯一约束，错误消息为「人员编号已存在」「身份证号已存在」。 | D1 的部门管理可以逐个试出 D2 存在哪些人员编号/身份证号。同类问题在 Excel 导入路径同样存在。**建议**：受限范围下把重复判定收窄到范围内，或统一返回不含归属信息的通用错误。 |
| **G5** | 人员进出「导出」是前端本地 CSV，不是服务端导出 | `person-records.vue:166 exportRecords()` 直接拼 CSV，只导当前页。 | `person-record:export` 权限码存在但**没有任何后端接口使用**；导出会静默丢数据。 |
| **G6** | 列表为「全表加载 + 内存过滤 + 内存分页」 | `ParkingApiController.listGatePersons` 用 `findAll()`；`ScopeQuerySupport` 顶部有 TODO。 | 数据量上来后 `total` 与实际页会失真、响应变慢。当前 689 用户规模无感。 |
| **G7** | 工作区有未提交的验证码调试输出 | `CaptchaService.kt` `render()` 中的 `println("验证码为：...")`（`git diff` 可见）。 | 验证码明文进日志 = 登录二次因子失效。**提交前必须删。** |
| **G8** | 已删除人员的信息在删除申请单里长期留存 | `GateDeleteRequest` 保存 `name/phone/idCard/face` 快照，审批通过后不清理。 | 属「留痕」设计取舍，但需确认是否符合个人信息最小化要求；`face` URL 已解绑所以取不到图。 |

---

## 7. 执行记录表

> **2026-09-15：本节未逐行填写。** 实际执行结果按用例组汇总在 `docs/gate-module-test-report.md`
> 第 1 节，逐条的原始记录在 `docs/gate-test/all_results.json`（151 条），
> 修复后的复测记录在 `docs/gate-test/results_regress.json`（39 条）。
> 保留本节作为下一次执行时的记录模板。

每个用例执行后填写。判定口径：**实际结果与第 5 节预期完全一致 = 通过**；
状态码一致但 message 不同、或数据条数不同 = 失败。

| 用例 ID | 执行账号 | 实际 HTTP | 实际 message | 实际数据 | 判定 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-AUTH-01 | S1 | | | | ☐ | |
| TC-LIST-03 | A2 | | | | ☐ | 重点：GP-ORPHAN 是否泄露 |
| TC-LIST-04 | B1 | | | | ☐ | |
| TC-CREATE-13 | B1 | | | | ☐ | 魔数校验 |
| TC-UPDATE-07 | B1 | | | | ☐ | 事务回滚 |
| TC-REVIEW-06 | B1 | | | | ☐ | 整批回滚 |
| TC-DEL-13 | B1 | | | | ☐ | 对抗性 |
| TC-XLS-08 | B1 | | | | ☐ | 不静默改写 |
| TC-FACE-02 | B2 | | | | ☐ | 跨部门文件 |
| TC-ATK-19 | B1 | | | | ☐ | 探测一致性 |
| … | | | | | ☐ | |

### 7.1 通过判据（退出标准）

1. 第 5 节全部 ✅/⛔/🚫 用例：状态码与 message 与预期一致。
2. 第 5.12 节全部 20 条对抗性用例：**没有一条能越过范围或状态机**。
3. 第 6 节 G1~G8 逐条确认，并给出「接受 / 修复 / 单独立项」的结论。
4. 所有跨部门对比用例（TC-LIST-04 vs 05、TC-XLS-03 vs 04、TC-FACE-01 vs 02）**数据条数必须不同**——
   如果相同，说明范围过滤没生效。

---

## 8. 数据清理与回滚

测试会在真实库留下痕迹，按以下顺序清理：

```sql
-- 1. 删除测试门禁人员（先删删除申请，再删人员；无外键但有业务一致性要求）
DELETE FROM gate_delete_request WHERE code LIKE 'GP-D1-%' OR code LIKE 'GP-D2-%' OR code LIKE 'GP-G-%'
   OR code = 'GP-ORPHAN' OR code LIKE 'XLS-%';
DELETE FROM gate_person WHERE code LIKE 'GP-D1-%' OR code LIKE 'GP-D2-%' OR code LIKE 'GP-G-%'
   OR code = 'GP-ORPHAN' OR code LIKE 'XLS-%';

-- 2. 删除测试人员进出记录
DELETE FROM person_access_record WHERE person IN ('章规希','余义','门禁测试甲')
  AND gate IN ('测试通道A','测试通道B','测试通道C');

-- 3. 解绑并清理测试上传的人脸文件（st1 目录下的物理文件需手工删）
DELETE FROM stored_files WHERE business_type = 'gate_person'
   AND business_id LIKE 'GP-%';
-- 物理文件路径见 SELECT relative_path FROM stored_files WHERE ...，删除 st1/<relative_path>

-- 4. 删除测试账号与部门
DELETE FROM user_managed_departments WHERE user_id IN
  (SELECT id FROM users WHERE username LIKE 't_%');
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 't_%');
DELETE FROM users WHERE username LIKE 't_%';
DELETE FROM department WHERE department_number = 'GATE-D2';

-- 5. 保留 audit_event（追加写，是本次测试的证据）；如必须清理：
-- DELETE FROM audit_event WHERE occurred_at > '<测试开始时间>' AND category = 'ACCESS_CONTROL';
```

**无法自动回滚的副作用（执行前必须知情）**：

| 副作用 | 说明 |
| --- | --- |
| `users.passwordHash` 被改 | U1/U2 的密码从 `Fqjg20221022` 变为测试密码，`must_change_password` 从 true 变 false |
| 会话被撤销 | 改密会 `incrementTokenVersion`，这些账号在别处的登录会被踢下线 |
| `admin` 密码 | 后端每次重启都会被 `FORCE_WRITE` 重置为 `admin`（既有配置，非本次引入） |
| Redis | 验证码与会话 key 有 TTL，自动过期 |
| `st1/` 物理文件 | 上传的人脸图片需手工删（第 3 步给出了定位方式） |

---

## 附录 A：门禁权限 × 角色对照

| 权限码 | SUPER_ADMIN | ADMIN | DEPT_ADMIN | USER |
| --- | --- | --- | --- | --- |
| `gate-person:read` | ✅ | ✅ | ✅ | ❌ |
| `gate-person:manage` | ✅ | ✅ | ✅ | ❌ |
| `gate-person:review` | ✅ | ✅ | ✅ | ❌ |
| `gate-person:export` | ✅ | ✅ | ✅ | ❌ |
| `person-record:read` | ✅ | ✅ | ✅ | ✅ |
| `person-record:export` | ✅ | ✅ | ✅ | ❌（且无对应后端接口，见 G5） |
| `file:read` | ✅ | ✅ | ✅ | ✅（2026-09-15 已授予，解决 G2；接口按 SELF 范围过滤） |
| `file:upload` | ✅ | ✅ | ✅ | ❌ |
| `department:read` | ✅ | ✅ | ✅ | ❌ |
| `department:manage` | ✅ | ✅ | ❌ | ❌ |
| `user:role-assign` | ✅ | ❌ | ❌ | ❌ |
| `audit:read` | ✅ | ✅（但接口要求 `hasRole('SUPER_ADMIN')`，实测 403） | ❌ | ❌ |
| `backup:manage` | ✅ | ❌ | ❌ | ❌ |

## 附录 B：范围可见性速查

| 实体 | 范围字段 | 受限角色如何判定 |
| --- | --- | --- |
| `gate_person` | `department_code` → 回退 `dept` 自由文本 | 解析不出 → **不可见** |
| `gate_delete_request` | 同上（申请时快照） | 同上 |
| `person_access_record` | `department_code` / `linked_user_id` / `card_id` / `person` | SELF：`linkedUserId` 或 `cardId∈gatePersonCodes` 或 `person∈gatePersonNames` |
| `stored_files` | `uploaded_by_user_id` / `department_code` / `business_type+business_id` | 三路任一命中；SELF 走上传者或业务对象 |
| `access_control` | `department_id`（真外键） | — |
| `access_record` | `car_number_normalized` / `department_name` 快照 | 部门维度是**「或」**关系，抗部门改名 |
