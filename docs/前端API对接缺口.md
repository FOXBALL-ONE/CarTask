# 前端 API 对接缺口

本文记录 `frontend/` 管理端与后端控制器的实际对接情况。后端接口基地址由 `BASE_URL` 配置，默认是 `http://127.0.0.1:8080/api`。除登录和验证码外，所有请求都由 `useHttp` 自动附加 `Authorization: Bearer <access_token>`。

## 已对接端点

| 前端页面 | HTTP 端点 | 当前用途 | 状态 |
| --- | --- | --- | --- |
| 登录 | `GET /auth/captcha` | 获取 token 和 Base64 SVG 图片 | 已对接 |
| 登录 | `POST /auth/login` | 提交用户名、密码、`captchaToken`、`captchaAnswer` | 已对接 |
| 仪表盘 | `GET /dashboard` | 加载统计、车位和违规类型 | 已对接 |
| 用户 | `GET /users` | 查询用户列表 | 已对接 |
| 用户 | `POST /users` | 新增用户，使用 JSON 文档兼容入口 | 已对接 |
| 用户 | `PUT /users/{id}` | 编辑用户，使用 JSON 文档兼容入口 | 已对接 |
| 用户 | `DELETE /users/{id}` | 删除用户 | 已对接 |
| 用户 | `PUT /users/{id}/account-status` | 启用或停用账号 | 已对接 |
| 角色 | `GET /roles` | 查询角色列表 | 已对接 |
| 角色 | `POST /roles` | 新增角色 | 已对接，权限矩阵尚未提交 |
| 角色 | `PUT /roles/{id}` | 编辑角色基础字段 | 已对接，权限矩阵尚未提交 |
| 角色 | `DELETE /roles/{id}` | 删除角色 | 已对接 |
| 部门 | `GET /depts` | 查询部门树数据 | 已对接 |
| 部门 | `POST /depts` | 新增部门 | 已对接 |
| 部门 | `PUT /depts/{id}` | 编辑部门 | 已对接 |
| 部门 | `DELETE /depts/{id}` | 删除部门 | 已对接 |
| 岗位 | `GET /posts` | 查询岗位列表 | 已修复。后端兼容端点是 `/posts`，不是 `/positions` |
| 岗位 | `POST /posts` | 新增岗位 | 已修复，提交 `code`、`sort` 和数字 `status` |
| 岗位 | `PUT /posts/{id}` | 编辑岗位 | 已修复 |
| 岗位 | `DELETE /posts/{id}` | 删除岗位 | 已修复 |
| 车主 | `GET /owners` | 查询车主列表和筛选 | 已对接 |
| 车主 | `POST /owners` | 新增车主 | 已对接 |
| 车主 | `PUT /owners/{id}` | 编辑车主 | 已对接 |
| 车主 | `DELETE /owners/{id}` | 删除车主 | 已对接 |
| 车位 | `GET /spots` | 查询车位列表、区域和状态筛选 | 已对接 |
| 车牌 | `GET /plates` | 查询车牌列表和状态筛选 | 已对接 |
| 设备 | `GET /devices` | 查询设备列表，并传递关键词、类型和状态 | 已对接 |
| 设备 | `POST /devices` | 新增设备，使用 JSON 文档兼容入口 | 已对接 |
| 设备 | `PUT /devices/{id}` | 编辑设备，使用 JSON 文档兼容入口 | 已对接 |
| 设备 | `DELETE /devices/{id}` | 删除设备 | 已对接 |
| 门禁人员 | `GET /gate-persons` | 查询人员列表，并传递关键词、部门、审批和同步状态 | 已对接 |
| 门禁人员 | `POST /gate-persons` | multipart 上传人脸和人员字段 | 已对接 |
| 门禁人员 | `PUT /gate-persons/{id}` | multipart 更新人员 | 已对接 |
| 门禁人员 | `PUT /gate-persons/{id}/approve` | 审批通过 | 已对接 |
| 门禁人员 | `PUT /gate-persons/{id}/reject` | 审批拒绝 | 已对接 |
| 门禁人员 | `DELETE /gate-persons/{id}` | 直接删除人员 | 已对接 |
| 门禁删除申请 | `GET /gate-persons/delete-requests` | 查询删除申请 | 已对接 |
| 门禁删除申请 | `PUT /gate-persons/delete-requests/{id}/approve` | 同意删除 | 已对接 |
| 门禁删除申请 | `PUT /gate-persons/delete-requests/{id}/reject` | 拒绝删除 | 已对接 |
| 人员记录 | `GET /person-records` | 查询人员进出记录 | 已对接，并传递关键词、方向、通道 |
| 车辆记录 | `GET /vehicle-records` | 查询车辆进出记录 | 已对接，并传递关键词、方向 |
| 日志 | `GET /login-logs` | 查询登录日志 | 已对接，并传递关键词和状态 |
| 日志 | `GET /operation-logs` | 查询操作日志 | 已对接，并传递关键词和模块 |

## 已发现并修复的问题

### 岗位端点错误

前端原来请求 `/positions`，后端文档兼容层实际提供 `/posts`。前端已统一改为：

```text
GET    /posts
POST   /posts
PUT    /posts/{id}
DELETE /posts/{id}
```

请求体使用后端文档字段：

```json
{
  "name": "运营员",
  "code": "OPERATOR",
  "sort": 1,
  "status": 1,
  "remark": ""
}
```

### 设备状态伪造

前端新增设备原来通过 `Math.random()` 生成在线或离线状态。后端当前文档入口只接收并保存 `status`，不会返回真实设备探测结果。现已改为使用表单状态，默认按离线写入；页面提示不再声称已经连接平台获取状态。

### 搜索参数未发送

设备、门禁人员、人员记录、车辆记录和日志页面原来只加载前 100 条数据，再全部在浏览器中过滤。现在搜索和重置时会把后端已支持的参数发送到 API；仍保留本地分页和少量字段过滤。

## 后端缺少或前端尚未完成的端点

### 1. 日志清空

日志页面的“清空日志”按钮没有对应后端端点。当前点击后只提示“后端暂未提供日志清空接口”，不会删除数据库记录。

需要后端明确提供并授权，例如：

```text
DELETE /operation-logs
DELETE /login-logs
```

或提供一个带日志类型和时间范围的统一删除接口。接口必须限制为超级管理员，并记录审计事件。

### 2. 角色权限矩阵保存

角色页面展示了菜单权限矩阵，但 `POST /roles` 和 `PUT /roles/{id}` 当前只提交角色名称、编码、排序、状态和备注。页面中的 `selectedPermissions` 没有映射为后端权限 ID 或权限编码，因此勾选权限不会生效。

需要后端提供角色权限字段或专用端点，例如：

```text
PUT /roles/{id}/permissions
```

请求体应明确使用权限 ID 或稳定权限编码，并返回保存后的权限集合。

### 3. 用户角色变更专用端点未接入

用户页面创建和编辑时提交 `roleIds`，与后端 JSON 文档兼容入口一致。已存在用户的主角色变更属于高权限操作，后端提供：

```text
PUT /users/{id}/role?role={ADMIN|USER|SUPER_ADMIN}
```

此端点仅允许超级管理员调用。前端尚未根据当前用户权限将普通编辑和角色变更拆分，因此不能在非超级管理员流程中伪造角色更新。

### 4. 车主充值未接入

后端提供：

```text
POST /owners/{id}/recharge
```

前端车主页面目前没有充值操作入口，也没有调用该端点。需要补充值输入、金额校验和成功后刷新余额。

### 5. 门禁删除申请创建未接入

后端提供：

```text
POST /gate-persons/{id}/delete-requests
```

当前前端人员页直接调用 `DELETE /gate-persons/{id}`，没有“提交删除申请”的流程。若业务要求审批删除，应增加删除原因输入，并根据权限选择提交申请或直接删除。

### 6. 门禁人员详情端点未使用

后端提供 `GET /gate-persons/{id}`，前端详情弹窗目前直接使用列表行数据。列表字段不足时不会重新请求详情，需要在详情字段扩展时接入该端点。

### 7. 车位和车牌管理写操作未接入

后端已有车位和车牌的新增、编辑、删除端点，但当前页面是只读展示：

```text
POST/PUT/DELETE /spots[/{id}]
POST/PUT/DELETE /plates[/{id}]
```

页面文案说明数据由车主信息自动生成，因此需要先确认业务是否允许前端直接维护；在业务确认前不应伪造写操作。

### 8. 部分列表筛选参数缺失

以下筛选控件已在页面中存在，但后端列表端点没有对应请求参数，因此目前只能过滤已加载的前 100 条数据：

| 页面 | 已有控件 | 后端端点当前支持 | 缺少的参数 |
| --- | --- | --- | --- |
| 人员进出记录 | 关键词、方向、通道、状态 | `keyword`、`direction`、`gate`、`passType`、日期范围 | `status` |
| 车辆进出记录 | 关键词、方向、通道、放行类型 | `keyword`、`direction`、日期范围 | `gate`、`passType` |
| 操作日志 | 关键词、模块、状态 | `keyword`、`module`、日期范围 | `status` |

建议扩展现有 `GET` 端点的可选查询参数，并在数据库查询或完整结果集上应用筛选后再分页。门禁删除申请的关键词和状态筛选也只在前端执行，但该端点当前返回全部申请，不受 100 条上限影响。

## 当前分页限制

用户、角色、设备、门禁人员、人员记录、车辆记录和日志页面部分请求仍使用 `pageSize=100` 后在前端分页。后端虽然支持分页，但页面尚未完全改为“服务端分页 + 服务端筛选”。当数据量超过 100 条时，这些页面会显示不完整数据。后续应统一让 API 请求使用当前页和当前筛选条件，并使用响应中的 `total` 计算页码。

## 字段约定

- `/depts`、`/posts`、`/owners`、`/spots`、`/plates`、`/devices`、`/gate-persons` 是文档兼容端点，使用页面当前的 camelCase 字段。
- 通用领域控制器仍可能要求 snake_case 参数，例如 `page_size`、`department_id`、`position_id`、`created_at`。
- 日期时间字段按项目约定传输 ISO-8601 字符串；前端不能自行转换成自定义格式。
- 登录、验证码、JWT 和密码字段不得写入普通日志。
