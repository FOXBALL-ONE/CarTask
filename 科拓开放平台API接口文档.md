# 科拓开放平台 API 接口文档

> 基于 PHP SDK 反编译还原，涵盖 16 个接口（8 个已实现 + 7 个推测 + 1 个用户卡查询）。
> 适用环境：PHP >= 7.4

---

## 一、公共约定

### 1.1 基础信息

| 项目 | 值 |
|---|---|
| 基础 URL（生产） | `https://kp-open.keytop.cn/unite-api` |
| 基础 URL（测试） | `http://tsktapps.keytop.cn/unite-api` |
| 请求方法 | `POST` |
| Content-Type | `application/json` |
| 自定义 Header | `version: 1.0.0` |
| 超时建议 | 30 秒 |

### 1.2 公共请求体字段

每个接口的请求体都包含以下公共字段（下文「请求格式」中不再重复列出）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `appId` | int | 应用 ID |
| `parkId` | string | 车场编号 |
| `serviceCode` | string | 业务码（见各接口） |
| `ts` | long | 毫秒时间戳 |
| `reqId` | string | UUID v4 |
| `key` | string | MD5 大写签名（见第二节） |

### 1.3 公共响应

响应为 JSON，常见结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

---

## 二、签名方法（paramsSign）

### 2.1 签名规则

签名用于填充请求体中的 `key` 字段，规则如下（还原自 Java `SignUtils.paramsSign`）：

1. **排除字段**
   - key = `"key"`（签名本身）
   - key = `"appId"`
2. **排除值**
   - value = `null`
   - value = `""`（空字符串）
   - value 为数组 / Map / Iterable（对应 PHP 的 `array`）
3. **排序**
   - 按 key 字母序升序排列（对应 Java `TreeMap`，PHP 用 `ksort`）
4. **拼接**
   - 格式：`k1=v1&k2=v2&...&kN=vN&{appSecret}`
   - 末尾追加 `&` + `appSecret`
5. **摘要**
   - 对拼接结果做 MD5，转**大写**，得到 32 位签名串

### 2.2 类型转换规则

| PHP 类型 | 转换为字符串 |
|---|---|
| bool | `"true"` / `"false"` |
| int / float | `(string)` |
| string | 原值 |
| array | **排除**（不参与签名） |
| null | **排除** |

### 2.3 签名示例

**输入请求体**（未含 key）：

```json
{
  "appId": 12250,
  "parkId": "591007282",
  "serviceCode": "getCarCardList",
  "ts": 1718000000000,
  "reqId": "abc123",
  "pageIndex": 1,
  "pageSize": 100
}
```

**Step 1 — 过滤后**（排除 appId、保留其余非空非数组字段）：

```
parkId=591007282, serviceCode=getCarCardList, ts=1718000000000,
reqId=abc123, pageIndex=1, pageSize=100
```

**Step 2 — 按 key 字母序排列**：

```
pageIndex, pageSize, parkId, reqId, serviceCode, ts
```

**Step 3 — 拼接**（假设 appSecret = `3e3fc3c957dc43b58c299005dcb673b8`）：

```
pageIndex=1&pageSize=100&parkId=591007282&reqId=abc123&serviceCode=getCarCardList&ts=1718000000000&3e3fc3c957dc43b58c299005dcb673b8
```

**Step 4 — MD5 大写**：

```
key = strtoupper(md5(上述字符串))
```

### 2.4 PHP 参考实现

```php
public function paramsSign(array $req): string
{
    // Step 1: 过滤
    $filtered = [];
    foreach ($req as $k => $v) {
        if ($k === 'key')      continue;   // 排除 key
        if ($k === 'appId')    continue;   // 排除 appId
        if ($v === null)       continue;   // 排除 null
        if (is_string($v) && $v === '') continue;  // 排除空串
        if (is_array($v))      continue;   // 排除数组

        // 转字符串
        if (is_bool($v)) {
            $filtered[$k] = $v ? 'true' : 'false';
        } elseif (is_int($v) || is_float($v)) {
            $filtered[$k] = (string)$v;
        } else {
            $filtered[$k] = (string)$v;
        }
    }

    // Step 2: 按 key 字母序排列
    ksort($filtered);

    // Step 3: 拼接
    $parts = [];
    foreach ($filtered as $k => $v) {
        $parts[] = $k . '=' . $v;
    }
    $temp = implode('&', $parts) . '&' . $this->appSecret;

    // Step 4: MD5 大写
    return strtoupper(md5($temp));
}
```

---

## 三、接口列表

### 模块 A：月卡管理（`/api/wec/`）

---

#### 接口 1：获取月卡列表

- **URL**：`POST /api/wec/GetCarCardList`
- **Method**：POST
- **serviceCode**：`getCarCardList`
- **请求格式**（业务参数）：

```json
{
  "pageIndex": 1,
  "pageSize": 100
}
```

---

#### 接口 2：新增月卡

- **URL**：`POST /api/wec/AddCarCardNo`
- **Method**：POST
- **serviceCode**：`addCarCardNo`
- **请求格式**（业务参数）：

> ⚠️ `cardInfo` / `carLotList` / `plateNoInfo` 是 **JSON 字符串**，不是嵌套对象

```json
{
  "userId": 1,
  "userName": "操作人",
  "cardInfo": "{\"cardName\":\"张三月卡\",\"useName\":\"张三\",\"tel\":\"13800138000\",\"roomId\":\"R001\",\"remak\":\"备注\",\"contact\":\"李四\",\"assist\":\"王五\"}",
  "carLotList": "[{\"lotName\":\"A区车位\",\"carType\":1,\"sequence\":1,\"areaName\":\"A区\",\"areaId\":[1,2,3],\"lotCount\":1}]",
  "plateNoInfo": "[{\"plateNo\":\"闽A12345\",\"etcNo\":\"ETC001\",\"remark\":\"测试\"}]"
}
```

**cardInfo 结构**：

| 字段 | 类型 | 说明 |
|---|---|---|
| cardName | string | 月卡名称 |
| useName | string | 使用人 |
| tel | string | 电话 |
| roomId | string | 房间 ID |
| remak | string | 备注（⚠️ 原系统字段名就是 `remak`，不是 remark） |
| contact | string | 联系人 |
| assist | string | 协助人 |

**carLotList 单项结构**：

| 字段 | 类型 | 说明 |
|---|---|---|
| lotName | string | 车位名称 |
| carType | int | 车辆类型（默认 1） |
| sequence | int | 序号（硬编码 1） |
| areaName | string | 区域名称 |
| areaId | int[] | 区域 ID 数组（由逗号分隔字符串转换） |
| lotCount | int | 车位数量（默认 1） |

**plateNoInfo 单项结构**：

| 字段 | 类型 | 说明 |
|---|---|---|
| plateNo | string | 车牌号 |
| etcNo | string | ETC 编号 |
| remark | string | 车牌备注 |

---

#### 接口 3：获取月卡详情（按月卡 ID）

- **URL**：`POST /api/wec/GetCarCardInfo`
- **Method**：POST
- **serviceCode**：`getCarCardInfo`
- **请求格式**：

```json
{
  "cardId": 12345
}
```

---

#### 接口 4：获取月卡详情（按车牌号）

- **URL**：`POST /api/wec/GetCarCardInfo`（与接口 3 同 URL，靠参数名区分）
- **Method**：POST
- **serviceCode**：`getCarCardInfo`
- **请求格式**：

```json
{
  "plateNo": "闽A12345"
}
```

---

#### 接口 5：修改月卡

- **URL**：`POST /api/wec/ModifyCarCardNo`
- **Method**：POST
- **serviceCode**：`modifyCarCardNo`
- **请求格式**（与新增相比，三个 JSON 字符串内多出 id 类字段）：

```json
{
  "userId": 1,
  "userName": "操作人",
  "cardInfo": "{\"cardId\":12345,\"cardName\":\"\",\"useName\":\"\",\"tel\":\"\",\"roomId\":\"\",\"remak\":\"\",\"contact\":\"\",\"assist\":\"\"}",
  "carLotList": "[{\"id\":1,\"lotName\":\"\",\"carType\":1,\"sequence\":1,\"areaName\":\"\",\"areaId\":[1],\"lotCount\":1}]",
  "plateNoInfo": "[{\"id\":1,\"plateNo\":\"\",\"etcNo\":\"\",\"remark\":\"\",\"plateState\":1}]"
}
```

**与新增的差异**：

| 位置 | 新增字段 |
|---|---|
| cardInfo | `cardId` |
| carLotList 项 | `id` |
| plateNoInfo 项 | `id`、`plateState` |

---

#### 接口 6：删除月卡

- **URL**：`POST /api/wec/DelCarCardInfo`
- **Method**：POST
- **serviceCode**：`delCarCardInfo`
- **请求格式**：

```json
{
  "cardId": 12345
}
```

---

#### 接口 7：月卡缴费

- **URL**：`POST /api/wec/PayCarCardFee`
- **Method**：POST
- **serviceCode**：`payCarCardFee`
- **请求格式**（`payChannel`/`chargeMethod`/`chargeNumber`/`amount`/`freeNumber` 在原代码中硬编码）：

```json
{
  "userId": 1,
  "userName": "操作人",
  "cardId": 12345,
  "orderNo": "UUID",
  "carType": 1,
  "payChannel": 1,
  "chargeMethod": 1,
  "chargeNumber": 1,
  "amount": 0,
  "freeNumber": 0,
  "validFrom": "2024-04-01 00:00:00",
  "validTo": "2024-05-01 00:00:00",
  "createTime": "2024-04-01 00:00:00"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| orderNo | string | 订单号（UUID） |
| carType | int | 车辆类型 |
| payChannel | int | 支付渠道（硬编码 1） |
| chargeMethod | int | 充值方式（硬编码 1） |
| chargeNumber | int | 充值数量（硬编码 1） |
| amount | int | 金额（硬编码 0） |
| freeNumber | int | 免费数量（硬编码 0） |
| validFrom | string | 有效期开始 `yyyy-MM-dd HH:mm:ss` |
| validTo | string | 有效期结束 `yyyy-MM-dd HH:mm:ss` |
| createTime | string | 创建时间 |

---

#### 接口 8：月卡退款

- **URL**：`POST /api/wec/RefundCarCardFee`
- **Method**：POST
- **serviceCode**：`refundCarCardFee`
- **请求格式**（`refundNumber` = `differentDays(validFrom, validTo) + 1`）：

```json
{
  "userId": 1,
  "userName": "操作人",
  "cardId": 12345,
  "orderNo": "UUID",
  "carType": 1,
  "payChannel": 1,
  "refundMethod": 2,
  "refundNumber": 15,
  "amount": 0,
  "freeNumber": 0,
  "validFrom": "2024-04-01 00:00:00",
  "validTo": "2024-04-15 00:00:00",
  "createTime": "2024-04-01 00:00:00",
  "remark": "有效期缩短"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| refundMethod | int | 退款方式（硬编码 2） |
| refundNumber | int | 退款天数 = 两日期相差天数 + 1 |
| amount | int | 退款金额（硬编码 0） |
| remark | string | 备注（默认"有效期缩短"） |

---

### 模块 B：用户卡查询（`/api/carCard/`）

---

#### 接口 9：按车牌查用户卡

- **URL**：`POST /api/carCard/GetCardInfoByUser` ⚠️ 路径是 `/api/carCard/`，不是 `/api/wec/`
- **Method**：POST
- **serviceCode**：`getCardInfoByUser`
- **请求格式**：

```json
{
  "plateNo": "闽A12345"
}
```

---

### 模块 C：进出记录 / 区域 / 字典（`/api/wec/`，推测接口）

---

#### 接口 10：获取车辆进出记录

- **URL**：`POST /api/wec/GetCarInoutInfo`
- **Method**：POST
- **serviceCode**：`getCarInoutInfo`
- **请求格式**（`plateNo`/`startTime`/`endTime` 可选）：

```json
{
  "pageIndex": 1,
  "pageSize": 1000,
  "plateNo": "闽A12345",
  "startTime": "2024-04-01 00:00:00",
  "endTime": "2024-04-30 23:59:59"
}
```

---

#### 接口 11：获取车位区域

- **URL**：`POST /api/wec/GetParkingPlaceArea`
- **Method**：POST
- **serviceCode**：`getParkingPlaceArea`
- **请求格式**：无业务参数（仅公共字段）

```json
{}
```

---

#### 接口 12：获取字典列表

- **URL**：`POST /api/wec/GetDictList`
- **Method**：POST
- **serviceCode**：`GetDictDataList` ⚠️ **首字母大写 G**，是 16 个接口里唯一一个
- **请求格式**（`dictType` 可选）：

```json
{
  "dictType": "car_type"
}
```

---

### 模块 D：黑名单管理（`/api/blacklist/`，推测接口）

---

#### 接口 13：查询黑名单

- **URL**：`POST /api/blacklist/QueryCarBlackInfo`
- **Method**：POST
- **serviceCode**：`queryCarBlackInfo`
- **请求格式**（`plateNo` 可选）：

```json
{
  "pageIndex": 1,
  "pageSize": 100,
  "plateNo": "闽A12345"
}
```

---

#### 接口 14：新增黑名单

- **URL**：`POST /api/blacklist/AddCarBlackInfo`
- **Method**：POST
- **serviceCode**：`addCarBlackInfo`
- **请求格式**：

```json
{
  "plateNo": "闽A12345",
  "reason": "违规",
  "remark": ""
}
```

---

#### 接口 15：修改黑名单

- **URL**：`POST /api/blacklist/ModifyCarBlackInfo`
- **Method**：POST
- **serviceCode**：`modifyCarBlackInfo`
- **请求格式**：

```json
{
  "id": 1,
  "plateNo": "闽A12345",
  "reason": "违规",
  "remark": ""
}
```

---

#### 接口 16：删除黑名单

- **URL**：`POST /api/blacklist/DelCarBlackInfo`
- **Method**：POST
- **serviceCode**：`delCarBlackInfo`
- **请求格式**（`id` / `plateNo` 至少传一个）：

```json
{
  "id": 1,
  "plateNo": "闽A12345"
}
```

---

## 四、速查表

| # | 接口名称 | URL 路径 | serviceCode |
|---|---|---|---|
| 1 | 月卡列表 | /api/wec/GetCarCardList | getCarCardList |
| 2 | 新增月卡 | /api/wec/AddCarCardNo | addCarCardNo |
| 3 | 月卡详情(按 ID) | /api/wec/GetCarCardInfo | getCarCardInfo |
| 4 | 月卡详情(按车牌) | /api/wec/GetCarCardInfo | getCarCardInfo |
| 5 | 修改月卡 | /api/wec/ModifyCarCardNo | modifyCarCardNo |
| 6 | 删除月卡 | /api/wec/DelCarCardInfo | delCarCardInfo |
| 7 | 月卡缴费 | /api/wec/PayCarCardFee | payCarCardFee |
| 8 | 月卡退款 | /api/wec/RefundCarCardFee | refundCarCardFee |
| 9 | 用户卡查询 | /api/carCard/GetCardInfoByUser | getCardInfoByUser |
| 10 | 进出记录 | /api/wec/GetCarInoutInfo | getCarInoutInfo |
| 11 | 车位区域 | /api/wec/GetParkingPlaceArea | getParkingPlaceArea |
| 12 | 字典列表 | /api/wec/GetDictList | **GetDictDataList** |
| 13 | 查询黑名单 | /api/blacklist/QueryCarBlackInfo | queryCarBlackInfo |
| 14 | 新增黑名单 | /api/blacklist/AddCarBlackInfo | addCarBlackInfo |
| 15 | 修改黑名单 | /api/blacklist/ModifyCarBlackInfo | modifyCarBlackInfo |
| 16 | 删除黑名单 | /api/blacklist/DelCarBlackInfo | delCarBlackInfo |

---

## 五、注意事项

1. **接口 3 与 4 同 URL 同 serviceCode**，靠参数名（`cardId` vs `plateNo`）区分。
2. **接口 12 的 serviceCode 首字母大写**（`GetDictDataList`），其余 15 个全是小写开头。
3. **接口 2、5 的 `cardInfo`/`carLotList`/`plateNoInfo` 是 JSON 字符串**而非嵌套对象，且 cardInfo 里备注字段拼写为 `remak`（原系统拼写如此，非笔误）。
4. **接口 7、8 的金额相关字段被硬编码为 0**，疑似只登记有效期不真实扣款；若需真实支付需另行确认。
5. **接口 10~16 为推测接口**，参数结构基于命名约定推断，实际调用时可能需要调整。
6. **签名时数组字段会被排除**，但请求体中仍会发送数组（如 `areaId`），签名与发送是两套逻辑。
7. **测试环境与生产环境域名不同**，切换时需同步修改 `baseUrl`。

---

_文档生成时间：2026-08-16_
