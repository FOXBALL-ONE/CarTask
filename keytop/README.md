# Keytop 模拟端

该模块按根目录 `openapi.yaml` 提供科拓开放平台的 15 个 POST 接口，使用内存保存月卡和黑名单数据，适合本地联调 `src` 内的 `KeytopServiceImpl`。

启动：

```powershell
cd keytop
.\gradlew.bat bootRun
```

默认地址为 `http://127.0.0.1:8090/unite-api`，默认配置为 `appId=12250`、`parkId=591007282`、`appSecret=secret`、`version=1.0.0`。可通过 Spring 配置覆盖 `keytop.app-id`、`keytop.park-id`、`keytop.app-secret`、`keytop.version`。

请求必须携带 `version: 1.0.0`，并按主工程 `KeytopSignature.paramsSign` 规则生成大写 MD5；错误请求返回 HTTP 400，成功响应使用文档中的 `resCode`、`resMsg` 和 JSON 字符串 `data`。

验证：

```powershell
.\gradlew.bat test
```
