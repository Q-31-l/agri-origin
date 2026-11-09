# REST 接口文档

> 农产品质量安全溯源系统 · 接口清单
> Base URL：`http://{host}:8080/api` · 在线调试：`http://{host}:8080/api/doc.html`

---

## 一、通用约定

### 1.1 请求规范

| 项 | 说明 |
| --- | --- |
| 协议 | HTTP / HTTPS |
| 基础路径 | `/api`（`server.servlet.context-path`） |
| 编码 | UTF-8 |
| 请求体格式 | `application/json`（文件上传为 `multipart/form-data`） |
| 时间格式 | 日期 `yyyy-MM-dd`，时间 `yyyy-MM-dd HH:mm:ss` |
| 认证方式 | `Authorization: Bearer {token}` |

### 1.2 统一响应结构

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {},
  "timestamp": 1767225600000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | `0` 表示成功，非 0 见 §1.4 |
| `message` | string | 提示文案，可直接展示给用户 |
| `data` | any | 业务数据，失败时为 `null` |
| `timestamp` | long | 服务器时间戳（毫秒） |

### 1.3 分页响应结构

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "records": [],
    "total": 128,
    "page": 1,
    "size": 10
  }
}
```

分页请求统一使用 `page`（从 1 开始）与 `size`（默认 10）。

### 1.4 业务状态码

| code | 含义 | 典型场景 |
| --- | --- | --- |
| `0` | 成功 | — |
| `400` | 参数错误 | 必填项缺失、格式不合法 |
| `401` | 未登录 / token 失效 | 未携带 token、token 过期 |
| `403` | 无权限 | 越权操作、数据不在范围内 |
| `404` | 资源不存在 | 批次/文件不存在 |
| `409` | 冲突 | 重复提交、状态冲突、**并发修改冲突**（见下） |
| `4001` | 业务状态不允许 | 未质检就发运、异常批次操作 |
| `500` | 服务端异常 | 未捕获异常 |
| `5001` | 上链失败 | 节点不可用、合约 revert |
| `5002` | 链上链下数据不一致 | 一致性校验不通过 |

> **并发修改冲突（409）**：`biz_batch`、`biz_abnormal_disposal`、`base_farm` 三张表启用了 MyBatis-Plus 乐观锁
> （`@Version` + `version` 列）。更新时 SQL 会带上 `WHERE id=? AND version=?`，
> 影响行数为 0 即说明"你读到的数据已经被别人改了"，后端统一抛
> `409 {"message":"该记录已被他人修改，请刷新后重新操作"}`。
> 前端收到该提示后刷新页面即可，**不会出现两个人同时操作、后写覆盖前写**的情况。
> 其他表只追加不覆盖，未引入版本号。

### 1.5 权限标记说明

接口文档中「权限」列对应 `@RequirePerm` 权限点，「角色」列对应 `@RequireRoles` 白名单。二者同时存在时必须同时满足。

---

## 二、认证 `/auth`

### 2.1 登录

```
POST /auth/login
```

**权限**：公开

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 登录账号 |
| `password` | string | 是 | 明文密码 |

```json
{ "username": "admin", "password": "123456" }
```

**响应**

```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 43200,
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshExpiresIn": 604800,
    "user": {
      "id": 1,
      "username": "admin",
      "realName": "系统管理员",
      "phone": "13800000001",
      "roleCode": "ADMIN",
      "roleName": "系统管理员",
      "dataScope": "ALL",
      "orgName": "平台运营中心",
      "villageId": null,
      "villageName": null,
      "farmId": null,
      "farmName": null
    },
    "permissions": ["batch:list", "batch:create", "..."]
  }
}
```

**错误**

| code | message |
| --- | --- |
| 400 | 账号或密码错误 |
| 403 | 账号已被禁用，请联系管理员 |

### 2.2 刷新令牌

```
POST /auth/refresh
```

**权限**：公开（凭 refresh token 代替登录态）

`access token` 默认 12 小时（`jwt.expire-hours`），`refresh token` 默认 7 天（`jwt.refresh-expire-days`）。
access 过期后前端用它静默换发，用户无需重新登录；换发时**用户信息、角色、权限都重新读库**，
所以管理员调整角色后，最长一个 access 周期内三端都会自动拿到新权限。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `refreshToken` | string | 是 | 登录时返回的 refreshToken |

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

**响应**：与 2.1 完全一致（新的 access + 新的 refresh，旧 refresh 不再使用）

**错误**

| HTTP | code | message |
| --- | --- | --- |
| 401 | 401 | 登录状态已失效，请重新登录（refresh 过期 / 伪造 / 类型不符） |
| 403 | 403 | 账号已被禁用，请联系管理员 |

> **两个 token 不能混用**：都在 payload 里带 `typ` 声明，access 是 `typ=access`，refresh 是 `typ=refresh`。
> 把 refresh token 塞进 `Authorization` 头会被拦截器判 401，把 access token 拿去调 `/auth/refresh` 同样 401。
> 这样避免了"长效凭证被当成短效凭证使用"这类越权隐患。

### 2.3 获取当前用户

```
GET /auth/profile
```

**权限**：登录即可

**响应**：`data` 为 `UserVO` + `permissions` 数组。

### 2.4 退出登录

```
POST /auth/logout
```

**权限**：登录即可

说明：JWT 无状态，服务端不做黑名单，前端清除本地 token 即可。

### 2.5 修改密码

```
PUT /auth/password
```

**权限**：登录即可

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `oldPassword` | string | 是 | 原密码 |
| `newPassword` | string | 是 | 新密码，≥ 6 位 |

```json
{ "oldPassword": "123456", "newPassword": "abc12345" }
```

### 2.6 微信小程序登录

```
POST /auth/wx-login
```

**权限**：公开

小程序端 `uni.login({ provider: 'weixin' })` 拿到的一次性 `code` 交给后端换取 `openid`：

- `openid` 已绑定平台账号 → 直接返回与 2.1 完全一致的登录结构；
- `openid` 未绑定 → 返回 `needBind: true` + 一次性 `bindTicket`（10 分钟有效，用后即废），前端跳转绑定页。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 是 | `uni.login` 返回的 code |

```json
{ "code": "081Abc..." }
```

**响应（已绑定）**：同 2.1

**响应（未绑定）**

```json
{
  "code": 0,
  "message": "该微信尚未绑定平台账号",
  "data": { "needBind": true, "bindTicket": "69a52d7d06f8412eb4341ba15e450637" }
}
```

> 未配置 `app.wx.appid` / `app.wx.secret` 时，后端进入 mock 模式（固定模拟 openid），用于本地无小程序资质时联调；日志会显式打印 mock 提示，不会伪装成真实微信登录。

### 2.7 微信 App 登录（开放平台）

```
POST /auth/wx-app-login
```

**权限**：公开

App 走的是**开放平台**，与小程序**不是同一个 appid、换回来的 openid 也不同**，服务端分别存两列，
所以「小程序绑过微信」不等于「App 也绑过」，反之亦然（同一个人两端各绑一次，账号是同一个）。

请求体两种形态都支持：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 二选一 | 微信 OpenSDK 授权返回的 code（推荐） |
| `openid` | string | 二选一 | 旧版 SDK 直接返回的 openid |
| `accessToken` | string | 二选一 | 与 `openid` 配对，后端会调 `sns/auth` 校验真实性，防止伪造 openid 登录任意账号 |

```json
{ "code": "071Xy..." }
```

```json
{ "openid": "o6_bmjrPTlm6_2sgVt7hMZOPfL2M", "accessToken": "ACCESS_TOKEN" }
```

**响应**：同 2.6（已绑定 → 同 2.1；未绑定 → `needBind` + `bindTicket`）

> 未配置 `app.wx.app-appid` / `app.wx.app-secret` 时该端独立进入 mock 模式（openid 为 `mock-app-openid-local-demo`），
> 可以在没有移动应用资质的情况下完整走通 App 端绑定闭环。

### 2.8 微信首次登录绑定账号

```
POST /auth/wx-bind
```

**权限**：公开（凭票据代替登录态）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bindTicket` | string | 是 | 2.6 / 2.7 返回的票据（票据里已带来源端，小程序与 App 共用本接口） |
| `username` | string | 是 | 平台账号 |
| `password` | string | 是 | 平台密码 |

**响应**：同 2.1（绑定成功即登录）

**错误**

| code | message |
| --- | --- |
| 400 | 绑定票据已失效，请重新微信登录 |
| 401 | 账号或密码错误 |
| 403 | 账号已被禁用，请联系管理员 |
| 409 | 该账号已绑定其他微信 / 该微信已绑定账号 xxx |

### 2.9 解绑微信 / 查询绑定状态

```
POST /auth/wx-unbind?platform=mp|app    权限：登录即可
GET  /auth/wx-status?platform=mp|app    权限：登录即可，返回 true / false
```

`platform` 省略时默认 `mp`（小程序）。两端的绑定互相独立：

| 端 | 存储字段 | 换取 openid 的接口 |
| --- | --- | --- |
| 小程序 `mp` | `sys_user.wx_openid` | `sns/jscode2session` |
| App `app` | `sys_user.wx_app_openid` | `sns/oauth2/access_token` |

解绑后该端微信再次登录会重新走"未绑定 → 绑定"流程，账号密码登录不受影响。

---

## 二·补、实时通知（WebSocket）

```
ws://<host>/api/ws?token=<JWT>
```

**鉴权**：握手阶段解析 query 中的 `token`，无效则拒绝握手（H5 与小程序均无法在 WebSocket 握手中携带 header，因此只能走 query）。

**消息格式**

| 方向 | 内容 |
| --- | --- |
| 上行 | 文本 `ping`（心跳），服务端回文本 `pong` |
| 下行 | `{"type":"batch|abnormal|chain","batchNo":"AG2026...","action":"AUDIT","message":"批次已核验通过","timestamp":1793848939344}` |
| 连接建立时 | `{"type":"open","action":"connected","message":"实时通知已连接","timestamp":...}` |

**广播触发点**

| type | action | 触发位置 |
| --- | --- | --- |
| `chain` | 事件类型（`BATCH`/`PLANTING`/…） | 任意业务数据上链存证（成功与失败都推） |
| `batch` | `AUDIT` / `CLOSE` | 批次核验通过或驳回 / 异常批次结案 |
| `abnormal` | `DISPOSE` | 异常处置登记 |

前端收到后把相关列表缓存标记为脏，并立即刷新当前打开列表页 —— 即"一端操作、三端同步"。

---

## 三、批次 `/batch`

### 3.1 批次分页

```
GET /batch/page
```

**权限**：`batch:list`

**查询参数**

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | long | 否 | 1 | 页码 |
| `size` | long | 否 | 10 | 每页条数 |
| `keyword` | string | 否 | — | 匹配批次号 / 产品名 / 农户姓名 |
| `status` | string | 否 | — | 批次状态 |
| `abnormalFlag` | int | 否 | — | `1` 仅异常，`0` 仅正常 |
| `villageId` | long | 否 | — | 行政村筛选（仅 ALL 范围生效） |
| `farmId` | long | 否 | — | **按生产主体筛选**：指定地块/农场 |
| `farmerId` | long | 否 | — | **按生产主体筛选**：指定农户 |
| `startDate` | string | 否 | — | **按时间筛选**：建档日期起，格式 `yyyy-MM-dd`（含当天） |
| `endDate` | string | 否 | — | **按时间筛选**：建档日期止，格式 `yyyy-MM-dd`（含当天） |

> **任务书「按批次、主体和时间查询」的对应关系**：按批次 → `keyword`（批次号精确/模糊匹配）；按主体 → `farmId` 或 `farmerId`；按时间 → `startDate` + `endDate`（对 `create_time` 取日期部分比较）。
>
> 主体与时间筛选**叠加在数据范围之上**（AND 关系），不会突破角色可见范围。例如农户传了 `farmerId=6`（他人），仍会被 `applyScope` 追加 `farmer_id = 当前用户`，结果为空而不是越权返回。
>
> 非 `ALL` 数据范围的账号会被自动注入 `village_id` / `farmer_id` / `create_by` 条件，无需前端传参。

**响应 `records[]` 元素**（`BizBatch` 主要字段）

```json
{
  "id": 1,
  "batchNo": "AG20260512001",
  "traceCode": "TC20260512001",
  "productId": 1,
  "productName": "红颜草莓",
  "variety": "红颜",
  "farmId": 1,
  "farmName": "溪头一号草莓大棚",
  "farmerId": 5,
  "farmerName": "张大山",
  "villageId": 1,
  "villageName": "溪头村",
  "plantingDate": "2026-02-10",
  "harvestDate": "2026-05-10",
  "quantity": 1200.00,
  "unit": "kg",
  "status": "SOLD",
  "auditStatus": "APPROVED",
  "abnormalFlag": 0,
  "chainStatus": "SUCCESS",
  "chainTxHash": "0x3f2a...",
  "blockNumber": 18,
  "remark": "示范：全流程正常批次"
}
```

### 3.2 批次下拉选项

```
GET /batch/options?status=QUALIFIED
```

**权限**：`batch:list`

返回当前用户数据范围内的批次精简列表，用于表单选择。

### 3.3 批次详情

```
GET /batch/{id}
```

**权限**：`batch:detail`（且数据须在范围内）

**响应 `data`**（`BatchDetailVO`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `batch` | object | 批次主信息 |
| `plantings` | array | 农事记录 |
| `harvests` | array | 采收记录 |
| `qualities` | array | 质检报告 |
| `logistics` | array | 物流记录 |
| `sales` | array | 销售记录 |
| `disposals` | array | 异常处置 |
| `corrections` | array | 更正说明 |
| `chainRecords` | array | 存证流水 |
| `timeline` | array | 时间轴（按时间正序） |
| `chainEnabled` | boolean | 是否接入链 |
| `chainMessage` | string | 链状态说明 |

**`timeline[]` 元素**

```json
{
  "time": "2026-02-10 09:00",
  "type": "PLANTING",
  "title": "农事记录 · 整地施肥",
  "operator": "张大山",
  "status": "APPROVED",
  "detail": "深翻 25cm，施用有机肥 800kg/亩",
  "dataHash": "a3f1...64位",
  "fileHash": "b7c2...64位",
  "txHash": "0x9d4e...",
  "blockNumber": 19,
  "chainStatus": "SUCCESS",
  "evidences": [
    { "id": 12, "name": "现场照片.jpg", "url": "/api/file/download/12", "mediaType": "IMAGE", "hash": "c1d2..." },
    { "id": 13, "name": "施肥视频.mp4", "url": "/api/file/download/13", "mediaType": "VIDEO", "hash": "d2e3..." }
  ]
}
```

> `evidences` 为该时间轴节点的实证材料（图片 / 视频 / 报告），仅当该环节上传过凭证时返回；
> 消费者溯源页据此直接查看现场照片、播放现场视频，且哈希可追溯至链上摘要。

### 3.4 创建批次

```
POST /batch
```

**权限**：`batch:create` · **角色**：`PRODUCER`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productId` | long | 是 | 产品品类 ID |
| `farmId` | long | 否 | 农场 ID |
| `farmerId` | long | 否 | 农户 ID |
| `plantingDate` | date | 是 | 种植日期，不晚于今天 |
| `quantity` | decimal | 是 | 预计产量，> 0 |
| `unit` | string | 否 | 单位，默认 `kg` |
| `remark` | string | 否 | 备注 |

```json
{
  "productId": 1,
  "farmId": 1,
  "farmerId": 5,
  "plantingDate": "2026-06-01",
  "quantity": 800,
  "unit": "kg",
  "remark": "第二批春茬草莓"
}
```

**响应**：`data` 为新批次 ID。

**副作用**：自动生成 `batchNo`（`AG` + `yyyyMMdd` + 3 位序号）与 `traceCode`（`TC` + 同规则），并同步上链登记。

### 3.5 修改批次

```
PUT /batch/{id}
```

**权限**：`batch:update`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `remark` | string | 否 | 批次备注，最多 255 字 |

说明：仅允许修改 `remark`，核心字段（产品、农场、农户、日期、数量）在建档时确定归属、数据范围与链上摘要，不可修改。如需修正请使用「更正说明」接口（追加式留痕）。

> 契约说明：本接口只接受 `remark`。此前误用建档 DTO 校验，要求客户端必须回传 `productId/plantingDate/quantity`，却只写入 `remark`——即"必填但静默丢弃"，已修正为独立入参对象。

### 3.6 批次核验

```
POST /batch/{id}/audit
```

**权限**：`batch:audit` · **角色**：`VILLAGE_ADMIN`、`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `auditStatus` | string | 是 | `APPROVED` / `REJECTED` |
| `auditRemark` | string | 否 | 审核意见 |

### 3.7 批次一致性校验（快捷入口）

```
GET /batch/{id}/verify
```

**权限**：`chain:verify`

返回结构同 §8.2。

---

## 四、农事记录 `/planting`

### 4.1 农事记录分页

```
GET /planting/page?page=1&size=10&batchId=1&batchNo=AG20260512001
```

**权限**：`planting:list`

**响应 `records[]`**

```json
{
  "id": 1,
  "batchId": 1,
  "batchNo": "AG20260512001",
  "recordType": "FERTILIZE",
  "operationDate": "2026-03-05",
  "content": "追施复合肥，配合滴灌",
  "materialName": "复合肥 15-15-15",
  "dosage": "15kg/亩",
  "operatorId": 5,
  "operatorName": "张大山",
  "images": "/api/file/download/12,/api/file/download/13",
  "gps": "119.123456,26.054321",
  "auditStatus": "APPROVED",
  "auditRemark": "记录属实",
  "dataHash": "c81f...",
  "chainStatus": "SUCCESS",
  "chainTxHash": "0x7b2c...",
  "blockNumber": 20,
  "createTime": "2026-03-05 10:22:33"
}
```

### 4.2 按批次查询农事记录

```
GET /planting/batch/{batchId}
```

**权限**：`planting:list` · 返回数组

### 4.3 提交农事记录

```
POST /planting
```

**权限**：`planting:create` · **角色**：`FARMER`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `recordType` | string | 是 | `SOIL` / `SOWING` / `FERTILIZE` / `IRRIGATE` / `PEST` / `WEED` / `OTHER` |
| `operationDate` | date | 是 | 作业日期，不晚于今天 |
| `content` | string | 是 | 农事内容 |
| `materialName` | string | 否 | 投入品名称 |
| `dosage` | string | 否 | 使用用量 |
| `images` | string | 否 | 图片 URL，逗号分隔 |
| `gps` | string | 否 | 定位坐标 `经度,纬度` |

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 批次不属于当前农户 | 403 无权操作该批次 |
| 批次未通过核验 | 4001 批次尚未通过核验 |
| 批次已结案 | 4001 批次已结案，不可新增记录 |
| 同批次同日同类型已存在 | 409 该批次当日已存在同类型农事记录 |
| 作业日期晚于今天 | 400 作业日期不能晚于今天 |

**副作用**：计算记录摘要 → 合约 `addRecord` → 写 `chain_record`（`bizType=PLANTING`）。

### 4.4 审核农事记录

```
POST /planting/{id}/audit
```

**权限**：`planting:audit` · **角色**：`VILLAGE_ADMIN`、`ADMIN`

请求体同 §3.6 `AuditDTO`。

---

## 五、采收记录 `/harvest`

### 5.1 采收记录分页

```
GET /harvest/page?page=1&size=10&batchId=1
```

**权限**：`harvest:list`

**响应 `records[]`**

```json
{
  "id": 1,
  "batchId": 1,
  "batchNo": "AG20260512001",
  "harvestDate": "2026-05-10",
  "quantity": 1150.00,
  "unit": "kg",
  "qualityLevel": "A",
  "weather": "晴，24℃",
  "operatorId": 5,
  "operatorName": "张大山",
  "images": "/api/file/download/15",
  "remark": "第二茬采收",
  "auditStatus": "APPROVED",
  "dataHash": "d92a...",
  "chainStatus": "SUCCESS",
  "createTime": "2026-05-10 16:30:00"
}
```

### 5.2 提交采收信息

```
POST /harvest
```

**权限**：`harvest:create` · **角色**：`FARMER`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `harvestDate` | date | 是 | 采收日期 |
| `quantity` | decimal | 是 | 采收数量，> 0.01 |
| `unit` | string | 否 | 默认 `kg` |
| `qualityLevel` | string | 否 | `A` / `B` / `C`，默认 `A` |
| `weather` | string | 否 | 天气 |
| `images` | string | 否 | 图片 URL |
| `remark` | string | 否 | 备注 |

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 该批次已有采收记录 | 409 该批次已存在采收记录（一批次仅一条） |
| 批次状态不是 `PLANTING` | 4001 当前状态不允许提交采收 |

**副作用**：批次状态 `PLANTING` → `HARVESTED`，上链 `bizType=HARVEST`。

### 5.3 审核采收信息

```
POST /harvest/{id}/audit
```

**权限**：`harvest:audit` · **角色**：`VILLAGE_ADMIN`、`ADMIN`

---

## 六、质检报告 `/quality`

### 6.1 质检报告分页

```
GET /quality/page?page=1&size=10&batchId=1
```

**权限**：`quality:list`

**响应 `records[]`**

```json
{
  "id": 1,
  "batchId": 1,
  "batchNo": "AG20260512001",
  "reportNo": "JC2026-0512-001",
  "inspectOrg": "闽侯县农产品质量检测中心",
  "inspectorId": 7,
  "inspectorName": "周敏",
  "inspectDate": "2026-05-11",
  "inspectResult": "PASS",
  "standardNo": "GB 2763-2021",
  "itemsJson": "[{\"name\":\"毒死蜱\",\"value\":\"0.01\",\"unit\":\"mg/kg\",\"limit\":\"0.02\",\"result\":\"合格\"}]",
  "conclusion": "所检项目均符合 GB 2763-2021 限量要求",
  "fileName": "检测报告.pdf",
  "filePath": "/api/file/download/20",
  "fileHash": "e4b1...64位",
  "fileSize": 524288,
  "dataHash": "f0c3...64位",
  "chainStatus": "SUCCESS",
  "chainTxHash": "0x1a2b...",
  "blockNumber": 21,
  "createTime": "2026-05-11 14:00:00"
}
```

### 6.2 按批次查询质检报告

```
GET /quality/batch/{batchId}
```

**权限**：`quality:list`

### 6.3 上传质检报告并判定

```
POST /quality
```

**权限**：`quality:create` · **角色**：`INSPECTOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `reportNo` | string | 是 | 报告编号（全局唯一） |
| `inspectDate` | date | 是 | 检测日期 |
| `inspectResult` | string | 是 | `PASS` / `FAIL` |
| `standardNo` | string | 否 | 执行标准号 |
| `itemsJson` | string | 否 | 检测项 JSON 字符串 |
| `conclusion` | string | 否 | 检测结论说明 |
| `fileName` | string | 否 | 报告文件名 |
| `filePath` | string | 否 | 报告下载地址（先调 `/file/upload`） |
| `fileHash` | string | 否 | 报告 SHA-256 |
| `fileSize` | long | 否 | 报告字节数 |
| `evidenceIds` | long[] | 否 | 现场实证图片/视频文件 ID（`/file/upload` 返回），哈希并入上链摘要 |

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 报告编号已存在 | 409 报告编号已存在 |
| 同批次同检测日已有报告 | 409 该批次当日已出具质检报告 |

**副作用（关键）**

| 结论 | 批次状态变化 | 额外动作 |
| --- | --- | --- |
| `PASS` | → `QUALIFIED` | 上链 `bizType=QUALITY` |
| `FAIL` | → `ABNORMAL`，`abnormalFlag=1` | 合约 `markAbnormal` + 上链 `bizType=ABNORMAL` + 自动创建处置任务 |

### 6.4 审核质检报告

```
POST /quality/{id}/audit
```

**权限**：`quality:audit` · **角色**：`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `auditStatus` | string | 是 | `APPROVED` / `REJECTED` |
| `auditRemark` | string | 否 | 审核意见，驳回时必填 |

**副作用**：仅回写 `audit_status` / `audit_remark`，不改动报告内容与链上摘要。

---

## 七、物流与销售

### 7.1 物流记录分页

```
GET /logistics/page?page=1&size=10&batchId=1
```

**权限**：`logistics:list`

**响应 `records[]`**（主要字段）

```json
{
  "id": 1,
  "batchId": 1,
  "batchNo": "AG20260512001",
  "logisticsNo": "SF20260512001",
  "fromOrg": "溪头村冷链集配中心",
  "toOrg": "永辉超市闽侯旗舰店",
  "vehicleNo": "闽A·D12345",
  "driverName": "郑海",
  "driverPhone": "13800000008",
  "departTime": "2026-05-12 06:00:00",
  "arriveTime": "2026-05-12 09:30:00",
  "temperature": "2-6℃",
  "status": "ARRIVED",
  "handoverFile": "/api/file/download/22",
  "fileHash": "a7d2...",
  "chainStatus": "SUCCESS",
  "createTime": "2026-05-12 06:10:00"
}
```

### 7.2 物流交接登记

```
POST /logistics
```

**权限**：`logistics:create` · **角色**：`LOGISTICS`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `logisticsNo` | string | 是 | 运单号 |
| `fromOrg` | string | 否 | 起运方 |
| `toOrg` | string | 否 | 目的方 |
| `vehicleNo` | string | 否 | 车牌号 |
| `driverName` | string | 否 | 司机 |
| `driverPhone` | string | 否 | 司机电话 |
| `departTime` | datetime | 是 | 发运时间 |
| `arriveTime` | datetime | 否 | 到达时间 |
| `temperature` | string | 否 | 运输温度 |
| `status` | string | 否 | `DEPARTED` / `ARRIVED`，默认 `DEPARTED` |
| `handoverFile` | string | 否 | 交接单地址 |
| `fileHash` | string | 否 | 交接单 SHA-256 |
| `remark` | string | 否 | 备注 |
| `evidenceIds` | long[] | 否 | 现场实证图片/视频文件 ID（`/file/upload` 返回），哈希并入上链摘要 |

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 批次状态非 `QUALIFIED` | 4001 批次状态不允许发运，当前状态：XXX |
| 批次为异常状态 | 4001 异常批次禁止发运，请先完成处置 |

**副作用**：批次 → `IN_TRANSIT`，上链 `bizType=LOGISTICS`。

### 7.3 审核物流记录

```
POST /logistics/{id}/audit
```

**权限**：`logistics:audit` · **角色**：`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `auditStatus` | string | 是 | `APPROVED` / `REJECTED` |
| `auditRemark` | string | 否 | 审核意见，驳回时必填 |

**副作用**：仅回写 `audit_status` / `audit_remark`，不改动物流记录与链上摘要。

### 7.4 销售记录分页

```
GET /sales/page?page=1&size=10&batchId=1
```

**权限**：`sales:list`

**响应 `records[]`**（主要字段）

```json
{
  "id": 1,
  "batchId": 1,
  "batchNo": "AG20260512001",
  "marketName": "永辉超市闽侯旗舰店",
  "salesDate": "2026-05-13",
  "price": 12.80,
  "quantity": 500.00,
  "unit": "kg",
  "amount": 6400.00,
  "buyerName": "吴晓芸",
  "buyerPhone": "13800000009",
  "chainStatus": "SUCCESS",
  "createTime": "2026-05-13 10:00:00"
}
```

### 7.5 销售登记

```
POST /sales
```

**权限**：`sales:create` · **角色**：`SELLER`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `marketName` | string | 是 | 销售市场 |
| `salesDate` | date | 是 | 销售日期 |
| `price` | decimal | 是 | 单价 |
| `quantity` | decimal | 是 | 销售数量 |
| `unit` | string | 否 | 默认 `kg` |
| `buyerName` | string | 否 | 买家名称 |
| `buyerPhone` | string | 否 | 买家电话 |
| `evidenceIds` | long[] | 否 | 现场实证图片/视频文件 ID（`/file/upload` 返回），哈希并入上链摘要 |

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 批次为异常状态 | 409 异常批次已熔断，禁止销售 |
| 无物流记录（状态非 `IN_TRANSIT`） | 409 批次尚未完成物流交接，不可销售 |
| 销售数量 > 剩余库存 | 409 批次库存不足，剩余 XXX |
| 批次已售罄（`SOLD`） | 409 批次已售罄 |

**副作用**：累计销售数量 ≥ 批次数量 → `SOLD`，否则 `ON_SALE`；上链 `bizType=SALES`。

### 7.6 审核销售记录

```
POST /sales/{id}/audit
```

**权限**：`sales:audit` · **角色**：`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `auditStatus` | string | 是 | `APPROVED` / `REJECTED` |
| `auditRemark` | string | 否 | 审核意见，驳回时必填 |

**副作用**：仅回写 `audit_status` / `audit_remark`，不改动销售记录与链上摘要。

---

## 八、异常管控 `/abnormal`

### 8.1 异常批次分页

```
GET /abnormal/page?page=1&size=10&batchNo=X&status=PENDING
```

**权限**：`abnormal:list`

**查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `batchNo` | string | 批次号模糊匹配 |
| `status` | string | 处置状态 `PENDING` / `DISPOSED` / `CLOSED` |

**响应 `records[]`**

```json
{
  "id": 1,
  "batchId": 2,
  "batchNo": "AG20260515002",
  "abnormalType": "PESTICIDE",
  "abnormalReason": "毒死蜱残留 0.08mg/kg，超出 GB 2763-2021 限量 0.02mg/kg",
  "abnormalHash": "b1c2...",
  "disposalMeasure": null,
  "disposalResult": null,
  "disposalHash": null,
  "status": "PENDING",
  "handlerId": null,
  "handlerName": null,
  "handleTime": null,
  "chainStatus": "SUCCESS",
  "createTime": "2026-05-15 08:40:00"
}
```

### 8.2 处置登记

```
POST /abnormal/{disposalId}/dispose
```

**权限**：`abnormal:dispose` · **角色**：`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `disposalMeasure` | string | 是 | 处置措施 |
| `disposalResult` | string | 是 | 处置结果 |
| `abnormalType` | string | 否 | 异常类型 |
| `evidenceIds` | long[] | 否 | 实证材料文件 ID（`/file/upload` 返回），图片 / 视频哈希并入上链摘要 |

```json
{
  "disposalMeasure": "整批就地无害化销毁",
  "disposalResult": "已销毁 600kg，全程录像留证",
  "abnormalType": "PESTICIDE",
  "evidenceIds": [20, 21, 22]
}
```

**副作用**：处置状态 → `DISPOSED`，绑定实证文件到本记录，上链 `bizType=DISPOSAL`。
链上摘要 = `DISPOSAL(批次号, 措施, 结果, 处置人, 时间, 证据根哈希)`。

### 8.3 上下游追溯与定责

```
GET /abnormal/{disposalId}/trace
```

**权限**：`abnormal:trace`

**响应 `data`**（`AbnormalTraceVO`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `batch` | object | 异常批次信息 |
| `disposals` | array | 处置记录列表 |
| `upstream` | array | 上游批次（同农户/同地块历史批次） |
| `downstream` | array | 下游流向（物流、销售、买家） |
| `responsibilities` | array | 责任主体定位结果 |
| `evidences` | map | 每条处置记录的实证材料（key=处置记录 id，含异常上报 + 处置两个阶段的图片/视频） |

**`responsibilities[]` 示例**

```json
[
  { "stage": "种植环节", "subject": "张大山", "role": "农户", "evidence": "农事记录 #12 显示 2026-04-20 施用毒死蜱" },
  { "stage": "检测环节", "subject": "周敏", "role": "检测机构", "evidence": "报告 JC2026-0515-001 判定不合格" }
]
```

### 8.4 异常结案

```
POST /abnormal/batch/{batchId}/close
```

**权限**：`abnormal:dispose` · **角色**：`REGULATOR`、`ADMIN`

**前置**：该批次处置状态必须为 `DISPOSED`，否则 4001「请先完成处置登记」。

**副作用**：批次 → `CLOSED`，上链 `bizType=CLOSE`；此后不可再新增任何业务记录。

---

## 九、存证与校验 `/chain`

### 9.1 存证流水分页

```
GET /chain/page?page=1&size=10&batchNo=AG20260512001&status=SUCCESS
```

**权限**：`chain:list`

**响应 `records[]`**

```json
{
  "id": 1,
  "batchNo": "AG20260512001",
  "contractAddress": "0x8f2a...",
  "chainIndex": null,
  "bizType": "BATCH",
  "bizId": 1,
  "eventType": "BATCH",
  "dataHash": "a3f1c2...64位",
  "fileHash": null,
  "txHash": "0x9d4e...",
  "blockNumber": 18,
  "gasUsed": 128450,
  "status": "SUCCESS",
  "errorMsg": null,
  "createTime": "2026-05-12 09:00:00"
}
```

### 9.2 一致性校验

```
GET /chain/verify/{batchNo}
```

**权限**：`chain:verify`

**响应 `data`**（`ChainVerifyVO`）

```json
{
  "batchNo": "AG20260512001",
  "chainEnabled": true,
  "passed": true,
  "message": "6 条存证全部校验通过，链上链下数据一致",
  "verifiedAt": "2026-06-01 10:12:33",
  "totalRecords": 6,
  "matchedRecords": 6,
  "items": [
    {
      "name": "批次登记",
      "bizType": "BATCH",
      "localHash": "a3f1c2...",
      "chainHash": "a3f1c2...",
      "txHash": "0x9d4e...",
      "chainIndex": null,
      "hashMatched": true,
      "receiptOk": true,
      "matched": true,
      "message": "校验通过"
    }
  ]
}
```

**`items[]` 判定字段**

| 字段 | 说明 |
| --- | --- |
| `hashMatched` | 链下重算摘要 == 链上摘要 |
| `receiptOk` | 交易回执存在且状态成功 |
| `matched` | `hashMatched && receiptOk` |

**演示模式响应**

```json
{
  "batchNo": "AG20260512001",
  "chainEnabled": false,
  "passed": false,
  "message": "未接入区块链，无法进行链上链下一致性校验",
  "totalRecords": 0,
  "matchedRecords": 0,
  "items": []
}
```

### 9.3 链服务状态

```
GET /chain/status
```

**权限**：登录即可

```json
{
  "code": 0,
  "data": {
    "enabled": true,
    "ready": true,
    "message": "链服务正常",
    "contractAddress": "0x8f2a...",
    "blockNumber": 151,
    "nodeVersion": "2.9.1",
    "groupId": 1
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `enabled` | 配置是否开启链上存证（`chain.enabled`） |
| `ready` | 真实链路可用性，见下方说明 |
| `message` | 不可用时的具体原因（连接失败 / 合约地址未配置 / 未启用） |
| `blockNumber` | 当前区块高度，读取失败为 `null` |
| `nodeVersion` | 节点二进制版本（`getClientVersion` 的 `FISCO-BCOS Version`，60 秒缓存）。注意它与 `Supported Version` 不同：后者是 `config.ini` 的 `supported_version`，标识创世兼容版本，升级后仍为 `2.9.0` 属正常 |
| `groupId` | 群组号，单群组固定为 1 |

> `ready` 不再是"启动时连上过一次"的静态标记，而是每 3 秒最多探测一次的真实链路状态：
> 节点宕机时立即变为 `false` 并在 `message` 里给出原因，节点恢复后**无需重启后端**，
> 下一个请求会自动重建 SDK 连接并回到 `true`。

### 9.4 存量批次补注册（运维）

```
POST /chain/repair/batches
```

**权限**：`chain:repair` + 角色 `ADMIN` / `REGULATOR`

合约除 `registerBatch` 外的所有写方法都带 `batchExists` 校验，未上链的历史/演示批次
一旦被写入农事、质检、物流等记录就会 revert。该接口把 `chain_status != SUCCESS` 的
批次逐条补注册（链上已存在的只回写本地状态，不重复注册），单条失败不影响其余批次。

```json
{
  "code": 0,
  "data": {
    "total": 12,
    "repaired": 12,
    "alreadyOnChain": 0,
    "failed": 0,
    "failedDetails": []
  }
}
```

> 正常的业务写入路径已内置"懒注册"：任一环节写链前若发现批次未上链，会先补注册再写记录，
> 因此该接口只用于批量修复存量数据，日常运行不需要人工调用。

---

## 十、扫码溯源 `/trace`（公开）

> 本节接口**无需登录**，前端 H5 溯源页直接调用。

### 10.1 按溯源码查询履历

```
GET /trace/{code}
```

**路径参数 `code`**：溯源码（`TCxxxx`）或批次号（`AGxxxx`）

**权限**：公开

**响应 `data`**（`TraceVO`）

```json
{
  "traceCode": "TC20260512001",
  "batchNo": "AG20260512001",
  "productName": "红颜草莓",
  "variety": "红颜",
  "origin": "溪头村溪头一号草莓大棚",
  "farmName": "溪头一号草莓大棚",
  "farmerName": "张大山",
  "villageName": "溪头村",
  "plantingDate": "2026-02-10",
  "harvestDate": "2026-05-10",
  "quantity": 1200.00,
  "unit": "kg",
  "status": "SOLD",
  "abnormalFlag": 0,
  "chainStatus": "SUCCESS",
  "chainTxHash": "0x9d4e...",
  "blockNumber": 18,
  "chainEnabled": true,
  "chainMessage": null,
  "qualityResult": "PASS",
  "qualityReportNo": "JC2026-0512-001",
  "qualityOrg": "闽侯县农产品质量检测中心",
  "timeline": [
    {
      "time": "2026-02-10 09:00",
      "type": "BATCH",
      "title": "批次建档",
      "operator": "林建国",
      "detail": "红颜草莓 1200kg，产地 溪头村",
      "dataHash": "a3f1...",
      "txHash": "0x9d4e...",
      "chainStatus": "SUCCESS"
    }
  ],
  "trust": {
    "score": 88,
    "grade": "A",
    "gradeText": "高度可信",
    "summary": "关键环节齐全、链上存证完整，可作为可信溯源凭证",
    "dimensions": [
      { "key": "flow", "name": "流程完整度", "score": 30, "max": 30, "note": "5/5 个应完成环节" },
      { "key": "chain", "name": "链上存证度", "score": 18, "max": 30, "note": "演示模式未接入链服务" },
      { "key": "quality", "name": "质量合规性", "score": 20, "max": 20, "note": "最近检测合格 · JC2026-0512-001" },
      { "key": "abnormal", "name": "异常洁净度", "score": 15, "max": 15, "note": "无异常记录" },
      { "key": "info", "name": "信息完备度", "score": 5, "max": 5, "note": "关键字段齐全" }
    ],
    "deductions": ["演示模式：链服务未启用，未做真实上链校验（-12）"]
  }
}
```

**`trust`（AgriTrust-5D 溯源可信度）**：把"能不能溯源"升级为"有多可信"的量化指标，五维加权满分 100，等级 `A≥85 / B≥70 / C≥50 / D<50`。`dimensions` 给出每一维的实得分与说明，`deductions` 逐条列出扣分原因，前端可直接展示。计分规则见 `docs/trust-score.md`。

**错误**：`404 批次不存在或溯源码有误`

### 10.2 消费者侧存证校验

```
GET /trace/{code}/verify
```

**权限**：公开 · 返回结构同 §9.2

> 该接口用于 H5 端校验按钮，仅做轻量校验（摘要比对 + 回执确认），不返回完整 `items` 明细中的敏感字段。

---

## 十一、文件 `/file`

### 11.1 上传文件

```
POST /file/upload
Content-Type: multipart/form-data
```

**权限**：`file:upload`

**表单字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 文件本体，≤ 100MB，扩展名限 `jpg/jpeg/png/webp/pdf/mp4/mov/m4v/webm` |
| `bizType` | string | 否 | 业务类型，如 `PLANTING` / `QUALITY` / `DISPOSAL` |
| `bizId` | long | 否 | 关联业务 ID（处置等场景先空传，提交时再绑定） |

**响应**

```json
{
  "code": 0,
  "message": "上传成功",
  "data": {
    "id": 20,
    "fileName": "现场处置.mp4",
    "filePath": "/api/file/download/20",
    "fileHash": "e4b1c9...64位",
    "fileSize": 524288,
    "mediaType": "VIDEO"
  }
}
```

> `fileHash` 为该文件的 SHA-256；`mediaType` 为 `IMAGE` / `VIDEO` / `FILE` 三值之一，
> 供前端区分"缩略图展示"还是"内联视频播放"。
>
> **实证存证机制**：图片 / 视频不是外挂附件——它们的 SHA-256 会按序聚合成一枚"证据根哈希"，
> 随业务摘要一并上链。消费者在溯源页看到的每一张图、每一段视频都能与链上摘要核验，
> 任何替换都会被"一键校验"发现。

### 11.2 文件下载

```
GET /file/download/{id}
```

**权限**：公开

返回文件二进制流，`Content-Disposition` 带原始文件名（已 URL 编码）。图片与视频直接以 `inline` 方式回放。

### 11.3 某条业务记录的实证材料（公开）

```
GET /file/evidence?bizType=DISPOSAL&bizId=3
```

**权限**：公开（供消费者溯源页展示）

**响应 `data[]`**（`EvidenceVO`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 文件 ID |
| `name` | string | 原始文件名 |
| `url` | string | 可访问地址 `/api/file/download/{id}` |
| `mediaType` | string | `IMAGE` / `VIDEO` / `FILE` |
| `contentType` | string | 原始 MIME 类型 |
| `size` | long | 字节数 |
| `hash` | string | 文件内容 SHA-256 |
| `createTime` | string | 上传时间 |

---

## 十二、统计 `/stats`

### 12.1 看板总览

```
GET /stats/overview
```

**权限**：`stats:overview`

**数据范围**：农户→本人批次，村管→本村批次，生产主体→本人建档批次，监管/管理员→全站。
`totalBatches`、各状态计数、`totalReports`、`totalChainRecords`、`chainSuccess/Failed/Skipped`、
`qualifiedRate`、`statusDistribution`、`villageDistribution`、`trend` **全部按同一范围收窄**，
与列表接口的 `total` 严格一致（`chain_record` 只落 `batch_no`，服务端按可见批次号回查）。

**响应 `data`**（`StatsVO`）

```json
{
  "totalBatches": 12,
  "plantingBatches": 3,
  "qualifiedBatches": 4,
  "inTransitBatches": 1,
  "onSaleBatches": 1,
  "soldBatches": 2,
  "abnormalBatches": 1,
  "todayBatches": 2,
  "totalFarmers": 2,
  "totalVillages": 2,
  "totalReports": 3,
  "totalChainRecords": 48,
  "chainSuccess": 45,
  "chainFailed": 2,
  "chainSkipped": 1,
  "qualifiedRate": 83.3,
  "chainSuccessRate": 93.8,
  "statusDistribution": [
    { "status": "PLANTING", "label": "种植中", "count": 3 }
  ],
  "villageDistribution": [
    { "villageName": "溪头村", "count": 8 }
  ],
  "trend": [
    { "date": "2026-05-28", "count": 2 }
  ]
}
```

### 12.2 近期上链失败记录

```
GET /stats/chain-failed
```

**权限**：`chain:list` · 返回最近 20 条 `status=FAILED` 的存证流水。

---

## 十三、系统管理 `/`

> 注意：这些接口挂在根路径下，实际地址为 `/api/user/page`、`/api/dict/{type}` 等。

### 13.1 用户管理

| 方法 | 路径 | 权限 | 角色 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/user/page` | `user:list` | — | 分页查询（`keyword` / `roleCode` / `villageId`） |
| GET | `/user/list?roleCode=FARMER` | `user:list` | — | 按角色下拉（`villageId` 可选） |
| POST | `/user` | `user:create` | ADMIN、VILLAGE_ADMIN | 新增，默认密码 `123456` |
| PUT | `/user/{id}` | `user:update` | ADMIN、VILLAGE_ADMIN | 编辑 |
| PUT | `/user/{id}/password` | `user:reset` | ADMIN、VILLAGE_ADMIN | 重置密码 |
| GET | `/user/me` | 登录 | — | 当前用户信息 |

**`UserDTO`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 登录账号 |
| `password` | string | 否 | 新增时留空则默认 `123456` |
| `realName` | string | 是 | 真实姓名 |
| `phone` | string | 否 | 手机号 |
| `roleCode` | string | 是 | 角色码 |
| `orgName` | string | 否 | 所属组织 |
| `villageId` | long | 否 | 所属行政村 |
| `farmId` | long | 否 | 所属农场 |
| `status` | int | 否 | `1` 启用 / `0` 禁用，默认 1 |

### 13.2 基础数据

| 方法 | 路径 | 权限 | 角色 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/village/list` | `village:list` | — | 行政村列表 |
| POST | `/village` | `village:create` | ADMIN | 新增行政村 |
| GET | `/farm/page` | `farm:list` | — | 农场分页（`keyword` / `villageId`） |
| GET | `/farm/list` | `farm:list` | — | 农场下拉（`villageId` 可选） |
| POST | `/farm` | `farm:create` | ADMIN、VILLAGE_ADMIN | 新增农场 |
| PUT | `/farm/{id}` | `farm:update` | ADMIN、VILLAGE_ADMIN | 编辑农场 |
| GET | `/product/page` | `product:list` | — | 产品分页（`keyword`） |
| GET | `/product/list` | `product:list` | — | 产品下拉 |
| POST | `/product` | `product:create` | ADMIN、PRODUCER | 新增产品 |
| PUT | `/product/{id}` | `product:update` | ADMIN、PRODUCER | 编辑产品 |

### 13.3 字典与日志

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/dict/{type}` | 登录 | 数据字典 |
| GET | `/log/page` | `log:list` | 操作日志（`username` / `module`） |

**`dict` 支持的 type**

| type | 说明 |
| --- | --- |
| `batch_status` | 批次状态 |
| `record_type` | 农事类型 |
| `abnormal_type` | 异常类型 |
| `audit_status` | 审核状态 |

---

## 十四、更正说明 `/correction`

### 14.1 提交更正说明

```
POST /correction
```

**权限**：`correction:create` · **角色**：`PRODUCER`、`VILLAGE_ADMIN`、`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `bizType` | string | 是 | 被更正的业务类型 `PLANTING/HARVEST/QUALITY/LOGISTICS/SALES` |
| `bizId` | long | 是 | 被更正的记录 ID |
| `reason` | string | 是 | 更正原因 |
| `content` | string | 是 | 更正说明内容 |
| `evidenceIds` | long[] | 否 | 更正实证材料文件 ID（`/file/upload` 返回），图片/视频哈希并入更正摘要上链 |

```json
{
  "batchId": 1,
  "bizType": "PLANTING",
  "bizId": 12,
  "reason": "录入笔误",
  "content": "实际用量为 12kg/亩，原记录 21kg/亩有误，以本次说明为准",
  "evidenceIds": [30, 31]
}
```

**副作用**：新增 `biz_correction` 记录（原始记录不变），上链 `bizType=CORRECTION`。

### 14.2 更正记录分页

```
GET /correction/page?page=1&size=10&batchId=1
```

**权限**：`batch:list`

---

## 十四·补、审核流 `/workflow`

> 解决同一个问题：「我提交了，然后呢？」提交方看进度，审核方看待办，中间不靠口头传递。
> 三个接口共用一套阶段规则与数据范围，保证"进度说在等村管、村管的待办里就一定有"。

### 14b.1 批次全链路进度

```
GET /workflow/progress/{batchId}
```

**权限**：`batch:list`（越权访问他村/他人批次返回 403）

**响应 `data`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `currentStage` | string | 当前所处环节标题 |
| `currentStageCode` | string | `BATCH` / `BATCH_AUDIT` / `PLANTING` / `HARVEST` / `QUALITY` / `LOGISTICS` / `SALES` / `DONE` |
| `waitingRole` / `waitingNames` | string | 当前在等谁（角色名 / 具体人姓名） |
| `tip` | string | 一句话进度描述，页面直接展示 |
| `doneCount` / `advancedCount` / `totalCount` | int | 已通过 / 已推进 / 总环节数 |
| `percent` | int | 0-100，已推进环节占比 |
| `stages[]` | array | 七个环节，每项含 `status`（`DONE`/`PENDING`/`REJECTED`/`TODO`/`ABNORMAL`）、`submitBy`、`submitTime`、`auditBy`、`auditTime`、`auditRemark`、`tip` |

### 14b.2 我的审核待办

```
GET /workflow/todo
```

**权限**：登录即可；按权限点与数据范围裁剪，只返回"确实轮到他审"的数据

**响应 `data`**：`total` + `groups[]`，每组含 `type`（`BATCH`/`PLANTING`/`HARVEST`/`QUALITY`/`LOGISTICS`/`SALES`/`ABNORMAL`）、`title`、`count`、`items[]`（`batchNo`、`productName`、`submitBy`、`submitTime`、`summary`、`from`）。

- 村管：本村批次核验、本村农事、本村采收（含尚未建档批次的"手写作物"记录）
- 监管：全量质检 / 物流 / 销售复核与异常处置
- 农户 / 生产主体 / 检测 / 物流 / 销售：无审核职责，返回空列表

### 14b.3 提交后的等待审核提示

```
GET /workflow/notice?bizType=PLANTING&bizId=123
```

**响应 `data`**：`title`、`message`（如「已提交，等待溪头村 王秀兰（村级管理员）审核」）、`waitingRole`、`waitingNames`、`nextStep`（通过后进入哪一环节）。

**`bizType` 取值**：`BATCH`、`PLANTING`、`HARVEST`、`QUALITY`、`LOGISTICS`、`SALES`

---

## 十五、接口速查表

| 模块 | 方法 | 路径 | 权限 | 角色限制 |
| --- | --- | --- | --- | --- |
| 认证 | POST | `/auth/login` | 公开 | — |
| | GET | `/auth/profile` | 登录 | — |
| | POST | `/auth/logout` | 登录 | — |
| | PUT | `/auth/password` | 登录 | — |
| 批次 | GET | `/batch/page` | `batch:list` | — |
| | GET | `/batch/options` | `batch:list` | — |
| | GET | `/batch/{id}` | `batch:detail` | — |
| | POST | `/batch` | `batch:create` | PRODUCER, ADMIN |
| | PUT | `/batch/{id}` | `batch:update` | — |
| | POST | `/batch/{id}/audit` | `batch:audit` | VILLAGE_ADMIN, REGULATOR, ADMIN |
| | GET | `/batch/{id}/verify` | `chain:verify` | — |
| 农事 | GET | `/planting/page` | `planting:list` | — |
| | GET | `/planting/batch/{id}` | `planting:list` | — |
| | POST | `/planting` | `planting:create` | FARMER, ADMIN |
| | POST | `/planting/{id}/audit` | `planting:audit` | VILLAGE_ADMIN, ADMIN |
| 采收 | GET | `/harvest/page` | `harvest:list` | — |
| | POST | `/harvest` | `harvest:create` | FARMER, ADMIN |
| | POST | `/harvest/{id}/audit` | `harvest:audit` | VILLAGE_ADMIN, ADMIN |
| 质检 | GET | `/quality/page` | `quality:list` | — |
| | GET | `/quality/batch/{id}` | `quality:list` | — |
| | POST | `/quality` | `quality:create` | INSPECTOR, ADMIN |
| | POST | `/quality/{id}/audit` | `quality:audit` | REGULATOR, ADMIN |
| 物流 | GET | `/logistics/page` | `logistics:list` | — |
| | POST | `/logistics` | `logistics:create` | LOGISTICS, ADMIN |
| | POST | `/logistics/{id}/audit` | `logistics:audit` | REGULATOR, ADMIN |
| 销售 | GET | `/sales/page` | `sales:list` | — |
| | POST | `/sales` | `sales:create` | SELLER, ADMIN |
| | POST | `/sales/{id}/audit` | `sales:audit` | REGULATOR, ADMIN |
| 异常 | GET | `/abnormal/page` | `abnormal:list` | — |
| | POST | `/abnormal/{id}/dispose` | `abnormal:dispose` | REGULATOR, ADMIN |
| | GET | `/abnormal/{id}/trace` | `abnormal:trace` | — |
| | POST | `/abnormal/batch/{id}/close` | `abnormal:dispose` | REGULATOR, ADMIN |
| 存证 | GET | `/chain/page` | `chain:list` | — |
| | GET | `/chain/verify/{batchNo}` | `chain:verify` | — |
| | GET | `/chain/status` | 登录 | — |
| | POST | `/chain/repair/batches` | `chain:repair` | REGULATOR, ADMIN |
| 溯源 | GET | `/trace/{code}` | 公开 | — |
| | GET | `/trace/{code}/verify` | 公开 | — |
| 文件 | POST | `/file/upload` | `file:upload` | — |
| | GET | `/file/download/{id}` | 公开 | — |
| 统计 | GET | `/stats/overview` | `stats:overview` | — |
| | GET | `/stats/chain-failed` | `chain:list` | — |
| 更正 | POST | `/correction` | `correction:create` | PRODUCER, VILLAGE_ADMIN, REGULATOR, ADMIN |
| | GET | `/correction/page` | `batch:list` | — |
| 系统 | GET | `/user/page` | `user:list` | — |
| | GET | `/user/list` | `user:list` | — |
| | POST | `/user` | `user:create` | ADMIN, VILLAGE_ADMIN |
| | PUT | `/user/{id}` | `user:update` | ADMIN, VILLAGE_ADMIN |
| | PUT | `/user/{id}/password` | `user:reset` | ADMIN, VILLAGE_ADMIN |
| | GET | `/user/me` | 登录 | — |
| | GET | `/village/list` | `village:list` | — |
| | POST | `/village` | `village:create` | ADMIN |
| | GET | `/farm/page` | `farm:list` | — |
| | GET | `/farm/list` | `farm:list` | — |
| | POST | `/farm` | `farm:create` | ADMIN, VILLAGE_ADMIN |
| | PUT | `/farm/{id}` | `farm:update` | ADMIN, VILLAGE_ADMIN |
| | GET | `/product/page` | `product:list` | — |
| | GET | `/product/list` | `product:list` | — |
| | POST | `/product` | `product:create` | ADMIN, PRODUCER |
| | PUT | `/product/{id}` | `product:update` | ADMIN, PRODUCER |
| | GET | `/dict/{type}` | 登录 | — |
| | GET | `/log/page` | `log:list` | — |

---

## 十六、调用示例

### 16.1 完整流程脚本

```bash
BASE=http://localhost:8080/api

# 1) 登录
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"producer01","password":"123456"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

# 2) 创建批次
curl -s -X POST $BASE/batch -H "$AUTH" -H "$JSON" \
  -d '{"productId":1,"farmId":1,"farmerId":5,"plantingDate":"2026-06-01","quantity":800,"unit":"kg"}'

# 3) 查询批次列表
curl -s "$BASE/batch/page?page=1&size=5" -H "$AUTH"

# 4) 查看时间轴
curl -s "$BASE/batch/1" -H "$AUTH"

# 5) 一致性校验
curl -s "$BASE/chain/verify/AG20260512001" -H "$AUTH"

# 6) 消费者免登录溯源
curl -s "$BASE/trace/TC20260512001"
```

### 16.2 前端请求封装约定

```js
// frontend/pc-admin/src/api/request.js
// 响应拦截器统一处理：
//   code === 0        → 返回整个 body（含 data）
//   401              → 清除 token 并跳转 /login
//   code 非 0         → ElMessage.error(message) 并 reject

// 业务调用统一写法：
const res = await batchPage({ page: 1, size: 10 })
res.data.records   // 列表
res.data.total     // 总数
```
