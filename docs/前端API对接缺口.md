# 前端 API 对接状态

本文记录 `frontend/` 管理端与后端兼容接口的当前对接状态。基地址由 `BASE_URL` 配置，默认是 `http://127.0.0.1:8080/api`。除登录和验证码外，请求由 `useHttp` 自动附加 JWT。

## 已完成

| 领域 | 端点或行为 |
| --- | --- |
| 认证 | `GET /auth/captcha`、`POST /auth/login`、`POST /auth/logout`，登录页已提交验证码并处理会话失效 |
| 仪表盘 | `GET /dashboard` |
| 用户 | `GET/POST/PUT/DELETE /users`，角色变更使用 `PUT /users/{id}/role`，状态使用 `PUT /users/{id}/account-status` |
| 角色 | `GET/POST/PUT/DELETE /roles`，详情返回 `permission_codes`，权限使用 `PUT /roles/{id}/permissions` |
| 组织 | `GET/POST/PUT/DELETE /depts`、`GET/POST/PUT/DELETE /posts` |
| 车主 | `GET/POST/PUT/DELETE /owners`，充值使用 `POST /owners/{id}/recharge` |
| 车位 | `GET/POST/PUT/DELETE /spots` |
| 车牌 | `GET/POST/PUT/DELETE /plates` |
| 设备 | `GET/POST/PUT/DELETE /devices`，列表支持关键词、类型和状态筛选 |
| 门禁人员 | 列表筛选、详情、上传、批量审核、导出、删除申请创建与审批均已接入 |
| 通行记录 | 人员和车辆记录支持服务端筛选及分页 |
| 日志 | 登录和操作日志支持关键词、模块、状态和分页；清空使用对应 `DELETE` 端点 |

## 关键契约

角色权限请求体是稳定权限编码数组，例如：

```json
["user:read", "user:update"]
```

日志清空不会物理删除追加式审计事件。后端使用 Redis 保存每类日志的可见时间点，并写入 `LOGS_CLEARED` 审计事件，因此审计哈希链仍可校验。清空接口要求超级管理员和 `audit:delete` 权限。

门禁人员删除按钮提交删除原因到 `POST /gate-persons/{id}/delete-requests`，审批页调用同意或拒绝接口。直接删除端点 `DELETE /gate-persons/{id}` 与门禁授权一样是 `denyAll()`：删除必须走「申请删除 → 审批同意」这条留痕路径，否则持有录入权限的部门管理可以直接删掉本部门人员，删除审核就形同虚设。

门禁人员的权限按职责拆成四个：`gate-person:read`（查看）、`gate-person:manage`（录入与申请删除）、`gate-person:review`（审核人员与删除申请）、`gate-person:export`（导出门禁人员）。批量审核用 `PUT /gate-persons/reviews`，结论对整批生效。

门禁人员的人脸照片与进出抓拍一样走受鉴权保护的下载接口，`<img>` 带不上 Bearer 头，必须先用同一套凭据取 blob 再换成 object URL 渲染。

## 剩余限制

- 权限目录会在启动时补齐内置权限编码，不覆盖已存在权限；内置角色为空时建立默认权限集合。
- 车主、车位、车牌的页面下拉选项可能额外请求最多 100 条数据生成候选项，主列表本身使用服务端分页。
- 日志、记录和门禁申请的兼容层当前使用内存结果集过滤后分页，数据量较大时应继续迁移到数据库查询。
- 门禁人员页面按 Excel 模板批量导入（`GET/POST /api/excel/gate-persons/{template,import}`），导入记录一律为待审核；样表不含人脸照片，导入后需逐条补充照片，页面不再提供「CSV 直接导入」这条并不存在的路径。
- 日期筛选控件尚未在管理页面提供；后端已支持 `startDate`、`endDate` 的 ISO-8601 日期参数。

## 字段约定

- 兼容端点使用页面字段（例如 `pageSize`、`deptId`、`ownerId`）；通用领域控制器使用 snake_case 参数（例如 `page_size`、`department_id`）。
- 日期时间传输使用 ISO-8601 字符串，`LocalDateTime` 不带时区偏移。
- 登录、验证码、JWT、密码和设备密钥不得写入普通日志。
