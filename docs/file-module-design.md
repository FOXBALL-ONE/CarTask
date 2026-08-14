# 文件模块设计

## 1. 目标

本模块提供本地文件系统存储能力，并使用数据库保存文件元数据。当前阶段包含：

- 单文件上传；
- 按服务端本地日期分目录存储；
- 上传后使用 UUID 重命名物理文件，同时保留原文件的最后一级后缀名；
- 返回由环境变量中的基础 URL 拼接得到的下载链接；
- 按文件 ID 下载，并通过 `Content-Disposition` 恢复客户端看到的原文件名；
- 查询单个文件的元数据。

当前阶段不处理用户身份、文件归属和下载链接签名，文件接口均按公开接口设计。文件删除、批量上传、断点续传、对象存储和病毒扫描不在本期范围内。

## 2. 核心约定

### 2.1 环境变量

| 环境变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `FILE_STORAGE_ROOT` | 否 | 当前进程工作目录，即 Java 的 `user.dir` | 文件存储根目录，可以使用相对路径或绝对路径 |
| `FILE_BASE_URL` | 是 | 无 | 对外访问服务的基础 URL，例如 `https://api.example.com` |

`FILE_STORAGE_ROOT` 未设置或内容为空白时，使用 `Paths.get(System.getProperty("user.dir"))`。生产环境建议显式设置独立的数据盘目录，避免文件写入应用部署目录。

`FILE_BASE_URL` 必须满足以下条件：

- 是绝对的 `http` 或 `https` URL；
- 不包含查询参数和 fragment；
- 启动时去掉末尾的 `/`；
- 未设置或格式错误时应用启动失败，不能静默生成错误链接。

Spring Boot 配置建议：

```yaml
app:
  file:
    storage-root: ${FILE_STORAGE_ROOT:${user.dir}}
    base-url: ${FILE_BASE_URL}
```

通过 `@ConfigurationProperties(prefix = "app.file")` 统一读取和校验配置。业务服务不直接、分散地调用 `System.getenv`。

### 2.2 日期目录

目录使用应用所在时区的本地日期，格式固定为：

```text
yyyy/MM/dd
```

例如在 2026-08-14 上传文件：

```text
<storage-root>/2026/08/14/550e8400-e29b-41d4-a716-446655440000.pdf
```

日期只在上传开始时计算一次。应用实例应配置一致的系统时区，避免共享存储上的目录日期不一致。

### 2.3 文件名规则

上传时同时维护两种文件名：

- `original_filename`：经过安全校验的原始文件名，仅用于展示和下载响应；
- `stored_filename`：磁盘上的文件名，格式为 `<UUID><extension>`。

处理规则如下：

1. 对浏览器传入的名称同时按 `/` 和 `\` 取最后一段，不能把客户端路径写入数据库。
2. 拒绝空文件名、超过 255 个字符的文件名，以及包含控制字符、回车或换行的文件名。
3. 后缀取原文件名最后一个 `.` 之后的内容；以 `.` 开头且没有其他 `.` 的名称视为无后缀。
4. 后缀仅允许 1 到 20 个 ASCII 字母或数字，保留原大小写；后缀不符合规则时拒绝上传，不进行猜测或替换。
5. 无后缀文件直接使用 UUID 作为物理文件名。
6. 通过 `CREATE_NEW` 创建文件，绝不覆盖已有文件；极低概率的 UUID 冲突应重新生成 ID 后重试。

示例：

| 原文件名 | 物理文件名示例 |
| --- | --- |
| `报价单.pdf` | `550e8400-e29b-41d4-a716-446655440000.pdf` |
| `archive.tar.gz` | `550e8400-e29b-41d4-a716-446655440000.gz` |
| `README` | `550e8400-e29b-41d4-a716-446655440000` |
| `.env` | `550e8400-e29b-41d4-a716-446655440000` |

原始完整名称始终保存在数据库中，因此物理文件只保留最后一级后缀不会影响下载时恢复名称。

## 3. 数据模型

表名建议为 `stored_files`：

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `UUID` | 主键 | 应用生成，同时用于物理文件名和接口标识 |
| `original_filename` | `VARCHAR(255)` | 非空 | 下载时恢复的原文件名 |
| `stored_filename` | `VARCHAR(64)` | 非空、唯一 | UUID 加可选后缀名 |
| `relative_path` | `VARCHAR(512)` | 非空、唯一 | 相对存储根目录的路径，统一使用 `/` 分隔 |
| `content_type` | `VARCHAR(255)` | 可空 | 上传时报告的 MIME 类型 |
| `size_bytes` | `BIGINT` | 非空、`>= 0` | 文件字节数 |
| `sha256` | `CHAR(64)` | 非空 | 写入文件时流式计算的 SHA-256 |
| `created_at` | `TIMESTAMP WITHOUT TIME ZONE` | 非空 | 上传完成时间，映射为 `LocalDateTime` |

参考表结构如下。它用于说明实体约束，不代表在本项目中新增迁移脚本：

```sql
CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(64) NOT NULL UNIQUE,
    relative_path VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
```

数据库只保存相对路径，不能保存包含环境相关根目录的绝对路径。这样调整 `FILE_STORAGE_ROOT` 后，只需整体迁移磁盘内容，无需更新每条元数据。

## 4. 模块结构

建议沿用项目现有 Spring Boot/Kotlin 分层：

```text
src/main/kotlin/top/foxball/cartask/
├── config/FileProperties.kt
├── controller/FileController.kt
├── entity/StoredFile.kt
├── repository/StoredFileRepository.kt
├── service/FileService.kt
└── service/impl/FileServiceImpl.kt
```

各层职责：

- `FileProperties`：绑定环境配置，规范化根目录和基础 URL，并在启动阶段验证目录可创建、可写。
- `FileController`：声明 multipart 输入、文件 ID，构造接口响应和二进制下载响应。
- `StoredFile`：映射 `stored_files` 元数据表，时间字段使用 `LocalDateTime`。
- `StoredFileRepository`：使用 `JpaRepository<StoredFile, UUID>` 查询和持久化元数据。
- `FileService`：定义上传、元数据查询和打开下载文件的业务契约。
- `FileServiceImpl`：处理路径、物理写入、摘要计算、数据库补偿和下载链接拼接。

控制器遵循项目约定：HTTP 参数使用显式 `@RequestPart`、`@PathVariable`；JSON 字段使用 snake_case；普通 JSON 响应通过 `ResponseBuilder` 返回；端点响应类定义在对应方法内部。二进制下载响应直接返回 `ResponseEntity<Resource>`。

## 5. 接口设计

### 5.1 上传文件

```http
POST /api/files
Content-Type: multipart/form-data
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | 是 | 使用 `@RequestPart("file") MultipartFile` 接收 |

成功响应：`201 Created`

```json
{
  "status": 201,
  "message": "Created",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "original_filename": "报价单.pdf",
    "content_type": "application/pdf",
    "size_bytes": 102400,
    "download_url": "https://api.example.com/api/files/550e8400-e29b-41d4-a716-446655440000/download",
    "created_at": "2026-08-14T16:20:30"
  }
}
```

响应不返回 `stored_filename`、`relative_path` 或存储根目录，避免把内部存储结构变成外部接口契约。

### 5.2 查询元数据

```http
GET /api/files/{id}
```

成功响应：`200 OK`，`data` 结构与上传响应一致。每次响应都用当前 `FILE_BASE_URL` 和文件 ID 重新生成 `download_url`，数据库中不保存完整下载 URL。

### 5.3 下载文件

```http
GET /api/files/{id}/download
```

成功响应：`200 OK`，关键响应头：

```http
Content-Type: application/pdf
Content-Length: 102400
Content-Disposition: attachment; filename="..."; filename*=UTF-8''%E6%8A%A5%E4%BB%B7%E5%8D%95.pdf
X-Content-Type-Options: nosniff
Cache-Control: no-store
```

实现应使用 Spring 的 `ContentDisposition.attachment().filename(originalFilename, UTF_8)` 生成响应头，不能手工拼接原文件名。文件内容通过 `FileSystemResource` 流式返回，不能一次性读入 `ByteArray`。

下载流程只接受文件 ID。原始文件名必须从数据库查询，客户端无权通过查询参数指定下载名称。

## 6. 上传流程与一致性

文件系统与数据库不能加入同一个原子事务，因此采用“先落盘、数据库失败则补偿删除”的策略：

1. 校验上传文件和原始文件名。
2. 生成 UUID、日期目录、物理文件名和相对路径。
3. 通过根目录安全解析得到最终路径，并创建日期目录。
4. 在同一日期目录创建 `.<UUID>.uploading` 临时文件，流式写入并同时计算 SHA-256 和实际字节数。
5. 写入成功后，将临时文件原子移动为最终物理文件；文件系统不支持原子移动时退回普通移动。
6. 使用 `TransactionTemplate` 保存元数据并 `flush`，确保数据库提交在方法返回前完成。
7. 数据库保存或提交失败时删除最终文件；任一步骤失败时删除临时文件。
8. 数据库提交成功后构造下载 URL 并返回响应。

使用 `TransactionTemplate` 的原因是事务提交异常必须在文件服务的补偿代码范围内可见。只在一个带 `@Transactional` 的方法体中捕获异常并不充分，因为代理可能在方法返回后才执行提交。

该策略保证常规失败不会产生“有数据库记录但无文件”的状态。进程在最终文件移动成功、数据库提交前被强制终止时仍可能留下孤立文件；后续运维任务可根据 `relative_path` 和临时文件年龄进行对账清理，但不属于本期接口范围。

## 7. 下载流程与路径安全

下载时执行以下步骤：

1. 按 UUID 查询元数据，不存在则返回 `404 Not Found`。
2. 使用 `storageRoot.resolve(relativePath).normalize()` 解析文件路径。
3. 验证解析结果仍以规范化后的 `storageRoot` 开头，防止路径穿越。
4. 验证目标是普通文件且不跟随符号链接；磁盘文件不存在或类型异常时返回 `404 Not Found` 并记录错误日志。
5. 从元数据读取原文件名、MIME 类型和长度，构造下载响应。
6. `content_type` 为空或无效时使用 `application/octet-stream`。

服务端不能使用请求中的路径、物理文件名或原文件名直接定位磁盘文件。外部永远只暴露不可推导磁盘目录的 UUID。

## 8. 下载链接生成

链接不入库，在返回文件元数据时实时拼接：

```text
trimTrailingSlash(FILE_BASE_URL)
  + /api/files/
  + encodePathSegment(fileId)
  + /download
```

实现建议使用 `UriComponentsBuilder.fromUriString(baseUrl).pathSegment(...)`，避免字符串拼接导致重复 `/` 或路径编码错误。

例子：

```text
FILE_BASE_URL=https://api.example.com
fileId=550e8400-e29b-41d4-a716-446655440000

https://api.example.com/api/files/550e8400-e29b-41d4-a716-446655440000/download
```

如果服务部署在带前缀的地址下，基础 URL 可以包含路径，例如 `https://example.com/shopmall`，最终链接应保留该前缀。

## 9. 错误处理

| 场景 | HTTP 状态 | 处理 |
| --- | --- | --- |
| 未提交 `file`、文件名为空或文件为空 | `400 Bad Request` | 返回参数错误 |
| 文件名或后缀不符合规则 | `400 Bad Request` | 不创建磁盘文件和元数据 |
| 超过 Spring multipart 大小限制 | `413 Payload Too Large` | 由全局异常处理统一响应 |
| 文件 ID 格式错误 | `400 Bad Request` | 参数绑定失败 |
| 元数据不存在或磁盘文件不存在 | `404 Not Found` | 不泄露物理路径 |
| 磁盘不可写、移动失败 | `500 Internal Server Error` | 清理临时文件并记录异常 |
| 数据库写入失败 | `500 Internal Server Error` | 补偿删除已写入文件 |

日志可以记录文件 ID、相对路径和异常堆栈，但不能输出上传内容，也不应输出完整存储根目录到普通业务日志。

## 10. 授权边界

本期不设计 owner、访问范围、签名参数或下载时效：

- `POST /api/files`、`GET /api/files/{id}` 和 `GET /api/files/{id}/download` 均不依赖登录用户；
- 若沿用项目当前 Spring Security 的默认鉴权规则，需要为上述路径显式配置 `permitAll()`，否则“模块不校验授权”仍会被全局过滤器拦截；
- 下载链接知道即可访问，不应承载敏感文件；
- 后续增加授权时，优先在元数据中增加归属关系，并使用短期签名 URL，而不是改变磁盘目录或暴露真实路径。

## 11. 启动校验

应用启动时应完成以下检查，失败则终止启动：

- 根路径可规范化为绝对路径；
- 根目录不存在时可以创建；
- 根路径存在时必须是目录；
- 根目录可写；
- 基础 URL 合法且已配置。

启动检查只能提前发现明显配置问题。每次上传仍须处理磁盘满、权限变更和 I/O 故障。

## 12. 测试范围

单元测试至少覆盖：

- 有后缀、无后缀、多个点和点文件的重命名结果；
- 中英文原文件名以及非法控制字符；
- 根目录默认值和环境变量覆盖；
- 基础 URL 末尾 `/`、带部署前缀以及非法 URL；
- `resolve` 后逃逸根目录的路径被拒绝；
- 数据库失败后最终文件和临时文件被删除；
- 下载响应恢复原始中文文件名。

集成测试使用临时目录和测试数据库，至少验证：

- 上传后磁盘路径为日期目录，文件内容和 SHA-256 正确；
- 数据库保存的是相对路径而不是绝对路径；
- 上传响应及元数据响应中的下载 URL 正确；
- 同名文件连续上传不会覆盖；
- 下载内容、`Content-Length`、`Content-Type` 和 `Content-Disposition` 正确；
- 元数据存在但磁盘文件丢失时返回 `404`。

## 13. 验收标准

- 不设置 `FILE_STORAGE_ROOT` 时，文件写入当前进程工作目录下的日期目录。
- 设置 `FILE_STORAGE_ROOT` 后，所有文件只能写入该根目录内部。
- 上传 `report.pdf` 后，磁盘文件名为 UUID 加 `.pdf`，数据库保留 `report.pdf`。
- 文件元数据写入数据库，且不存储环境相关的绝对路径或完整下载 URL。
- 接口返回的下载链接以 `FILE_BASE_URL` 为基础正确拼接。
- 访问下载链接时响应文件名为上传时的原文件名。
- 上传或数据库操作失败后不残留本次请求创建的临时文件。
