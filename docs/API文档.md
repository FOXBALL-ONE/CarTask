# 智慧停车管理系统 - 前端API接口文档

## 基础信息

- **Base URL**: `http://localhost:3000/api`
- **数据格式**: JSON
- **字符编码**: UTF-8

## 通用响应格式

### 成功响应
```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

### 失败响应
```json
{
  "success": false,
  "message": "错误信息"
}
```

---

## 1. 用户管理

### 1.1 获取用户列表
- **接口**: `GET /users`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（用户名/姓名/手机号）
  - `status` (number, 可选): 状态筛选 (1=正常, 0=停用)
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                        // 用户ID
        "username": "admin",            // 用户名（登录账号）
        "password": "123456",           // 密码
        "name": "管理员",                // 真实姓名
        "deptId": 1,                    // 所属部门ID
        "phone": "13800000001",         // 手机号
        "email": "admin@example.com",   // 邮箱
        "roleIds": [1],                 // 角色ID数组
        "status": 1,                    // 状态：1=正常, 0=停用
        "createTime": "2025-01-15 10:00" // 创建时间
      }
    ],
    "total": 10,      // 总记录数
    "page": 1,        // 当前页码
    "pageSize": 8     // 每页数量
  }
}
```

### 1.2 获取单个用户
- **接口**: `GET /users/:id`
- **URL参数**:
  - `id` (number): 用户ID
- **响应**: 返回用户详情对象（结构同上）

### 1.3 新增用户
- **接口**: `POST /users`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "username": "zhangsan",            // 必填，用户名（登录账号）
  "password": "123456",              // 必填，密码
  "name": "张三",                     // 必填，真实姓名
  "deptId": 2,                       // 必填，所属部门ID
  "phone": "13800000002",            // 必填，手机号
  "email": "zhangsan@example.com",   // 可选，邮箱
  "roleIds": [2],                    // 必填，角色ID数组
  "status": 1                        // 必填，状态：1=正常, 0=停用
}
```

### 1.4 更新用户
- **接口**: `PUT /users/:id`
- **URL参数**:
  - `id` (number): 用户ID
- **请求体**: 同新增用户（所有字段都可选，只传需要更新的字段）

### 1.5 删除用户
- **接口**: `DELETE /users/:id`
- **URL参数**:
  - `id` (number): 用户ID

---

## 2. 角色管理

### 2.1 获取角色列表
- **接口**: `GET /roles`
- **响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,                      // 角色ID
      "name": "超级管理员",          // 角色名称
      "code": "ADMIN",              // 角色编码（英文标识）
      "sort": 1,                    // 排序号（数字越小越靠前）
      "status": 1,                  // 状态：1=正常, 0=停用
      "remark": "系统管理员"         // 备注说明
    }
  ]
}
```

### 2.2 新增角色
- **接口**: `POST /roles`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "name": "财务专员",       // 必填，角色名称
  "code": "FINANCE",       // 必填，角色编码（英文，唯一）
  "sort": 5,              // 必填，排序号
  "status": 1,            // 必填，状态：1=正常, 0=停用
  "remark": "财务管理"     // 可选，备注说明
}
```

### 2.3 更新角色
- **接口**: `PUT /roles/:id`
- **URL参数**:
  - `id` (number): 角色ID
- **请求体**: 同新增角色（所有字段可选）

### 2.4 删除角色
- **接口**: `DELETE /roles/:id`
- **URL参数**:
  - `id` (number): 角色ID

---

## 3. 部门管理

### 3.1 获取部门列表
- **接口**: `GET /depts`
- **响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,                      // 部门ID
      "name": "总公司",              // 部门名称
      "code": "HQ",                 // 部门编码
      "parent": null,               // 上级部门ID（null表示顶级部门）
      "sort": 1,                    // 排序号
      "leader": "李总",              // 负责人
      "phone": "010-12345678",      // 联系电话
      "status": 1                   // 状态：1=正常, 0=停用
    }
  ]
}
```

### 3.2 新增部门
- **接口**: `POST /depts`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "name": "研发中心",            // 必填，部门名称
  "code": "RD",                 // 必填，部门编码
  "parent": 1,                  // 可选，上级部门ID（null或不传表示顶级部门）
  "sort": 2,                    // 必填，排序号
  "leader": "张经理",            // 可选，负责人
  "phone": "010-87654321",      // 可选，联系电话
  "status": 1                   // 必填，状态
}
```

### 3.3 更新部门
- **接口**: `PUT /depts/:id`
- **URL参数**:
  - `id` (number): 部门ID
- **请求体**: 同新增部门

### 3.4 删除部门
- **接口**: `DELETE /depts/:id`
- **URL参数**:
  - `id` (number): 部门ID

---

## 4. 岗位管理

### 4.1 获取岗位列表
- **接口**: `GET /posts`
- **响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,                      // 岗位ID
      "name": "总经理",              // 岗位名称
      "code": "CEO",                // 岗位编码
      "sort": 1,                    // 排序号
      "status": 1,                  // 状态：1=正常, 0=停用
      "remark": "公司最高管理者"     // 备注说明
    }
  ]
}
```

### 4.2 新增岗位
- **接口**: `POST /posts`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "name": "产品经理",         // 必填，岗位名称
  "code": "PM",              // 必填，岗位编码
  "sort": 5,                 // 必填，排序号
  "status": 1,               // 必填，状态
  "remark": "产品规划与设计"  // 可选，备注说明
}
```

### 4.3 更新岗位
- **接口**: `PUT /posts/:id`
- **URL参数**:
  - `id` (number): 岗位ID
- **请求体**: 同新增岗位

### 4.4 删除岗位
- **接口**: `DELETE /posts/:id`
- **URL参数**:
  - `id` (number): 岗位ID

---

## 5. 车主管理

### 5.1 获取车主列表
- **接口**: `GET /owners`
- **请求参数**:
  - `keyword` (string, 可选): 搜索关键词（卡号/姓名/手机号）
  - `dept` (string, 可选): 部门筛选
  - `status` (number, 可选): 状态筛选（1=正常, 0=停用）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                      // 车主ID
        "cardId": "CAR20240001",      // 车主卡号
        "name": "张伟",                // 姓名
        "dept": "研发中心",            // 所属部门
        "phone": "13800010001",       // 手机号
        "spotCount": 1,               // 拥有车位数量
        "plateCount": 1,              // 绑定车牌数量
        "balance": 500,               // 账户余额（元）
        "status": 1                   // 状态：1=正常, 0=停用
      }
    ],
    "total": 20,
    "page": 1,
    "pageSize": 8
  }
}
```

### 5.2 新增车主
- **接口**: `POST /owners`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "cardId": "CAR20240010",      // 必填，车主卡号（唯一）
  "name": "李四",                // 必填，姓名
  "dept": "市场部",              // 必填，所属部门
  "phone": "13800010010",       // 必填，手机号
  "spotCount": 1,               // 必填，拥有车位数量
  "plateCount": 1,              // 必填，绑定车牌数量
  "balance": 0,                 // 可选，账户余额，默认0
  "status": 1                   // 必填，状态
}
```

### 5.3 更新车主
- **接口**: `PUT /owners/:id`
- **URL参数**:
  - `id` (number): 车主ID
- **请求体**: 同新增车主

### 5.4 删除车主
- **接口**: `DELETE /owners/:id`
- **URL参数**:
  - `id` (number): 车主ID

### 5.5 车主充值
- **接口**: `POST /owners/:id/recharge`
- **URL参数**:
  - `id` (number): 车主ID
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "amount": 100        // 必填，充值金额（元，正数）
}
```
- **响应**:
```json
{
  "success": true,
  "message": "充值成功",
  "data": {
    "id": 1,
    "balance": 600     // 充值后的余额
  }
}
```

---

## 6. 车位管理

### 6.1 获取车位列表
- **接口**: `GET /spots`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（车位编号）
  - `area` (string, 可选): 区域筛选
  - `type` (string, 可选): 类型筛选
  - `status` (number, 可选): 状态筛选（1=已分配, 0=空闲）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                      // 车位ID
        "code": "A-101",              // 车位编号
        "area": "A区",                // 所属区域
        "type": "地面车位",            // 车位类型
        "owner": "张伟",               // 使用人（空闲时为null）
        "status": 1,                  // 状态：1=已分配, 0=空闲
        "remark": "靠近电梯"           // 备注说明
      }
    ],
    "total": 100
  }
}
```

### 6.2 新增车位
- **接口**: `POST /spots`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "code": "A-201",              // 必填，车位编号（唯一）
  "area": "A区",                // 必填，所属区域
  "type": "地面车位",            // 必填，车位类型
  "owner": "李四",               // 可选，使用人
  "status": 0,                  // 必填，状态：1=已分配, 0=空闲
  "remark": "靠近出口"           // 可选，备注说明
}
```

### 6.3 更新车位
- **接口**: `PUT /spots/:id`
- **URL参数**:
  - `id` (number): 车位ID
- **请求体**: 同新增车位

### 6.4 删除车位
- **接口**: `DELETE /spots/:id`
- **URL参数**:
  - `id` (number): 车位ID

---

## 7. 车牌管理

### 7.1 获取车牌列表
- **接口**: `GET /plates`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（车牌号/车主姓名）
  - `status` (number, 可选): 状态筛选（1=正常, 0=停用）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                      // 车牌ID
        "plate": "京A12345",          // 车牌号
        "owner": "张伟",               // 车主姓名
        "ownerId": 1,                 // 车主ID
        "status": 1,                  // 状态：1=正常, 0=停用
        "regDate": "2024-01-15"       // 登记日期
      }
    ],
    "total": 50
  }
}
```

### 7.2 新增车牌
- **接口**: `POST /plates`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "plate": "京B98765",          // 必填，车牌号（唯一）
  "owner": "李四",               // 必填，车主姓名
  "ownerId": 2,                 // 必填，车主ID
  "status": 1,                  // 必填，状态
  "regDate": "2024-03-20"       // 必填，登记日期（YYYY-MM-DD）
}
```

### 7.3 更新车牌
- **接口**: `PUT /plates/:id`
- **URL参数**:
  - `id` (number): 车牌ID
- **请求体**: 同新增车牌

### 7.4 删除车牌
- **接口**: `DELETE /plates/:id`
- **URL参数**:
  - `id` (number): 车牌ID

---

## 8. 设备管理

### 8.1 获取设备列表
- **接口**: `GET /devices`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（设备编号/名称）
  - `type` (string, 可选): 类型筛选
  - `status` (number, 可选): 状态筛选（1=在线, 0=离线）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                          // 设备ID
        "code": "PK-001",                 // 设备编号
        "name": "入口车牌识别机",          // 设备名称
        "type": "车场设备",                // 设备类型
        "brand": "Keytop",                // 品牌
        "model": "KT-CR100",              // 型号
        "location": "地下车库入口",         // 安装位置
        "ip": "192.168.1.100",            // IP地址
        "status": 1,                      // 状态：1=在线, 0=离线
        "installDate": "2024-01-10"       // 安装日期
      }
    ],
    "total": 15
  }
}
```

### 8.2 新增设备
- **接口**: `POST /devices`
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "code": "PK-010",                 // 必填，设备编号（唯一）
  "name": "出口道闸",                // 必填，设备名称
  "type": "车场设备",                // 必填，设备类型
  "brand": "Keytop",                // 必填，品牌
  "model": "KT-B200",               // 必填，型号
  "location": "地下车库出口",         // 必填，安装位置
  "ip": "192.168.1.110",            // 必填，IP地址
  "status": 1,                      // 必填，状态
  "installDate": "2024-03-15"       // 必填，安装日期（YYYY-MM-DD）
}
```

### 8.3 更新设备
- **接口**: `PUT /devices/:id`
- **URL参数**:
  - `id` (number): 设备ID
- **请求体**: 同新增设备

### 8.4 删除设备
- **接口**: `DELETE /devices/:id`
- **URL参数**:
  - `id` (number): 设备ID

---

## 9. 门禁管理

### 9.1 获取门禁人员列表
- **接口**: `GET /gate-persons`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（编号/姓名/手机号/身份证）
  - `dept` (string, 可选): 部门筛选
  - `approveStatus` (string, 可选): 审核状态筛选（审核中/通过/拒绝）
  - `syncStatus` (string, 可选): 同步状态筛选（已同步/未同步）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                                  // 人员ID
        "code": "GP0001",                         // 人员编号
        "dept": "研发中心",                        // 所属部门
        "name": "张伟",                            // 姓名
        "phone": "13800010001",                   // 手机号
        "idCard": "110101199001011234",           // 身份证号
        "face": "https://picsum.photos/...",      // 人脸照片URL
        "createTime": "2025-03-15 09:00",         // 创建时间
        "approveStatus": "通过",                   // 审核状态：审核中/通过/拒绝
        "syncStatus": "已同步"                     // 同步状态：已同步/未同步
      }
    ],
    "total": 20
  }
}
```

### 9.2 获取单个门禁人员
- **接口**: `GET /gate-persons/:id`
- **URL参数**:
  - `id` (number): 人员ID
- **响应**: 返回人员详情对象（结构同上）

### 9.3 新增门禁人员
- **接口**: `POST /gate-persons`
- **Content-Type**: `multipart/form-data`
- **请求体**:
  - `code` (string, 必填): 人员编号（唯一）
  - `dept` (string, 必填): 所属部门
  - `name` (string, 必填): 姓名
  - `phone` (string, 必填): 手机号
  - `idCard` (string, 必填): 身份证号
  - `face` (File, 必填): 人脸照片文件（图片格式）
- **说明**: 新增后默认审核状态为"审核中"，同步状态为"未同步"

### 9.4 更新门禁人员
- **接口**: `PUT /gate-persons/:id`
- **URL参数**:
  - `id` (number): 人员ID
- **Content-Type**: `multipart/form-data`
- **请求体**: 同新增人员（所有字段可选，face字段可选择性更新）

### 9.5 审批通过
- **接口**: `PUT /gate-persons/:id/approve`
- **URL参数**:
  - `id` (number): 人员ID
- **说明**: 将审核状态改为"通过"
- **响应**:
```json
{
  "success": true,
  "message": "审批通过"
}
```

### 9.6 审批拒绝
- **接口**: `PUT /gate-persons/:id/reject`
- **URL参数**:
  - `id` (number): 人员ID
- **说明**: 将审核状态改为"拒绝"
- **响应**:
```json
{
  "success": true,
  "message": "审批拒绝"
}
```

### 9.7 删除门禁人员
- **接口**: `DELETE /gate-persons/:id`
- **URL参数**:
  - `id` (number): 人员ID
- **说明**: 直接删除人员记录

### 9.8 获取删除申请列表
- **接口**: `GET /gate-persons/delete-requests`
- **说明**: 获取所有人员删除申请记录
- **响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 9001,                               // 申请ID
      "personId": 4,                            // 关联的人员ID
      "code": "GP0004",                         // 人员编号
      "dept": "行政部",                          // 部门
      "name": "赵敏",                            // 姓名
      "phone": "13800010004",                   // 手机号
      "idCard": "110108199107204567",           // 身份证号
      "face": "https://i.pravatar.cc/...",      // 人脸照片URL
      "reason": "人员离职",                      // 删除原因
      "applyTime": "2025-08-22 14:30",          // 申请时间
      "status": "待处理"                         // 申请状态：待处理/已同意/已拒绝
    }
  ]
}
```

### 9.9 同意删除申请
- **接口**: `PUT /gate-persons/delete-requests/:id/approve`
- **URL参数**:
  - `id` (number): 申请ID
- **说明**: 同意删除申请，会同时删除对应的门禁人员记录
- **响应**:
```json
{
  "success": true,
  "message": "已同意删除申请"
}
```

### 9.10 拒绝删除申请
- **接口**: `PUT /gate-persons/delete-requests/:id/reject`
- **URL参数**:
  - `id` (number): 申请ID
- **说明**: 拒绝删除申请，人员记录保留
- **响应**:
```json
{
  "success": true,
  "message": "已拒绝删除申请"
}
```

---

## 10. 记录管理

### 10.1 获取人员进出记录
- **接口**: `GET /person-records`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（姓名/卡号）
  - `direction` (string, 可选): 方向筛选（进/出）
  - `gate` (string, 可选): 门禁点筛选
  - `passType` (string, 可选): 通行方式筛选（人脸识别/刷卡/密码）
  - `startDate` (string, 可选): 开始日期（YYYY-MM-DD）
  - `endDate` (string, 可选): 结束日期（YYYY-MM-DD）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                              // 记录ID
        "person": "张伟",                      // 人员姓名
        "cardId": "C001",                     // 卡号
        "dept": "安保部",                      // 部门
        "time": "2026-08-23 08:02:15",        // 通行时间
        "direction": "进",                     // 方向：进/出
        "gate": "东门门禁",                    // 门禁点
        "method": "人脸识别",                  // 通行方式：人脸识别/刷卡/密码
        "status": "正常",                      // 状态：正常/异常
        "photo": "https://picsum.photos/..."  // 抓拍照片URL
      }
    ],
    "total": 100
  }
}
```

### 10.2 获取车辆进出记录
- **接口**: `GET /vehicle-records`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（车牌号/车主姓名）
  - `direction` (string, 可选): 方向筛选（进/出）
  - `startDate` (string, 可选): 开始日期（YYYY-MM-DD）
  - `endDate` (string, 可选): 结束日期（YYYY-MM-DD）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                              // 记录ID
        "plate": "京A12345",                  // 车牌号
        "owner": "张伟",                       // 车主姓名
        "dept": "研发中心",                    // 部门
        "time": "2026-08-23 08:15:22",        // 通行时间
        "direction": "进",                     // 方向：进/出
        "gate": "地下车库入口",                 // 通行门点
        "amount": 0,                          // 收费金额（元）
        "method": "车牌识别",                  // 识别方式：车牌识别/刷卡
        "status": "正常",                      // 状态：正常/异常
        "photo": "https://picsum.photos/..."  // 车辆照片URL
      }
    ],
    "total": 200
  }
}
```

---

## 11. 日志管理

### 11.1 获取登录日志
- **接口**: `GET /login-logs`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（用户名/IP地址）
  - `status` (string, 可选): 状态筛选（成功/失败）
  - `startDate` (string, 可选): 开始日期（YYYY-MM-DD）
  - `endDate` (string, 可选): 结束日期（YYYY-MM-DD）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                          // 日志ID
        "user": "admin",                  // 用户名
        "ip": "192.168.1.10",             // IP地址
        "location": "内网",                // 登录地点
        "browser": "Chrome 119",          // 浏览器
        "os": "Windows 11",               // 操作系统
        "status": "成功",                  // 登录状态：成功/失败
        "time": "2026-08-23 08:00:15",    // 登录时间
        "message": "登录成功"              // 提示信息
      }
    ],
    "total": 50
  }
}
```

### 11.2 获取操作日志
- **接口**: `GET /operation-logs`
- **请求参数**: 
  - `keyword` (string, 可选): 搜索关键词（用户名/操作描述）
  - `module` (string, 可选): 模块筛选
  - `startDate` (string, 可选): 开始日期（YYYY-MM-DD）
  - `endDate` (string, 可选): 结束日期（YYYY-MM-DD）
  - `page` (number, 可选): 页码，默认1
  - `pageSize` (number, 可选): 每页数量，默认8
- **响应**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,                          // 日志ID
        "user": "admin",                  // 操作用户
        "module": "用户管理",              // 所属模块
        "action": "新增",                  // 操作动作：新增/修改/删除/查询
        "desc": "新增用户【张三】",         // 操作描述
        "ip": "192.168.1.10",             // IP地址
        "status": "成功",                  // 操作状态：成功/失败
        "time": "2026-08-23 09:15:30",    // 操作时间
        "cost": "120ms"                   // 耗时
      }
    ],
    "total": 100
  }
}
```

---

## 12. 仪表盘

### 12.1 获取仪表盘数据
- **接口**: `GET /dashboard`
- **说明**: 获取仪表盘所有统计数据，包括概览统计、车位使用情况、违规类型分布、违规趋势、进出场趋势
- **响应**:
```json
{
  "success": true,
  "data": {
    "stats": [
      {
        "label": "车位总数",          // 统计项名称
        "value": 500,               // 统计值
        "delta": "+2.5%",           // 变化率（相比上期）
        "trend": "up",              // 趋势：up=上升, down=下降, flat=持平
        "color": "blue"             // 显示颜色：blue/green/orange/red
      },
      {
        "label": "已分配",
        "value": 380,
        "delta": "+1.2%",
        "trend": "up",
        "color": "green"
      },
      {
        "label": "空闲车位",
        "value": 120,
        "delta": "-3.1%",
        "trend": "down",
        "color": "orange"
      },
      {
        "label": "今日违规",
        "value": 8,
        "delta": "+0.5%",
        "trend": "up",
        "color": "red"
      }
    ],
    "parking": [
      {
        "area": "A区",              // 区域名称
        "total": 150,              // 总车位数
        "used": 120                // 已使用车位数
      },
      {
        "area": "B区",
        "total": 180,
        "used": 145
      },
      {
        "area": "C区",
        "total": 120,
        "used": 85
      },
      {
        "area": "D区",
        "total": 50,
        "used": 30
      }
    ],
    "violationTypes": [
      {
        "name": "超时停车",         // 违规类型名称
        "value": 45,               // 违规次数
        "color": "#3B6DFF"         // 显示颜色（十六进制）
      },
      {
        "name": "占用他人车位",
        "value": 28,
        "color": "#E9A568"
      },
      {
        "name": "未缴费",
        "value": 15,
        "color": "#38BDF8"
      },
      {
        "name": "违规停放",
        "value": 12,
        "color": "#6EE7B7"
      }
    ],
    "violationTrend": {
      "labels": [                  // X轴标签（日期）
        "周一", "周二", "周三", "周四", "周五", "周六", "周日"
      ],
      "series": [
        {
          "name": "违规次数",       // 数据系列名称
          "data": [12, 15, 10, 18, 14, 8, 6],  // Y轴数据点
          "color": "#E9A568"       // 线条/柱状颜色
        }
      ]
    },
    "inoutTrend": {
      "labels": [                  // X轴标签（时间点）
        "00:00", "04:00", "08:00", "12:00", "16:00", "20:00"
      ],
      "series": [
        {
          "name": "进场",
          "data": [5, 8, 45, 30, 50, 25],
          "color": "#38BDF8"
        },
        {
          "name": "出场",
          "data": [3, 6, 20, 35, 55, 30],
          "color": "#6EE7B7"
        }
      ]
    }
  }
}
```

**字段说明**：
- `stats`: 顶部概览统计卡片数组
  - `label`: 统计项标题
  - `value`: 数值
  - `delta`: 变化百分比（正数带+号，负数带-号）
  - `trend`: 趋势方向（up/down/flat）
  - `color`: 卡片主题色

- `parking`: 各区域车位使用情况数组
  - `area`: 区域名称
  - `total`: 区域总车位数
  - `used`: 已使用车位数
  - 使用率 = used / total × 100%

- `violationTypes`: 违规类型饼图数据
  - `name`: 违规类型名称
  - `value`: 发生次数
  - `color`: 扇区颜色

- `violationTrend`: 违规趋势折线图数据
  - `labels`: X轴时间标签
  - `series`: 数据系列数组
    - `name`: 系列名称
    - `data`: 数据点数组（与labels一一对应）
    - `color`: 线条颜色

- `inoutTrend`: 进出场趋势折线图数据
  - `labels`: X轴时间标签
  - `series`: 多条数据线（进场、出场）
    - `name`: 系列名称
    - `data`: 数据点数组
    - `color`: 线条颜色

---

## 状态码说明

| 状态 | 说明 |
|------|------|
| 1 | 正常/启用/已同步 |
| 0 | 停用/禁用/未同步 |

## 审批状态

- `审核中`: 待审核
- `通过`: 审核通过
- `拒绝`: 审核拒绝

## 删除申请状态

- `待处理`: 等待处理
- `已同意`: 已同意删除
- `已拒绝`: 已拒绝删除

## 错误处理

所有API在发生错误时返回：
```json
{
  "success": false,
  "message": "具体错误信息"
}
```

常见错误信息：
- `"人员不存在"`: 找不到指定的人员
- `"删除申请不存在"`: 找不到指定的删除申请
- `"操作失败"`: 通用错误
- `"网络请求失败"`: 网络连接问题

---

## 注意事项

1. **文件上传**: 上传人脸照片时使用`multipart/form-data`格式
2. **分页**: 默认`page=1`, `pageSize=8`
3. **日期格式**: `YYYY-MM-DD HH:mm:ss`
4. **apiCall函数**: 前端使用的`apiCall`函数已自动处理了响应，直接返回`data`字段，无需再次访问`result.data`

---

**文档版本**: v1.0  
**更新日期**: 2026-08-27
