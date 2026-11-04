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
| `409` | 冲突 | 重复提交、库存不足、状态冲突 |
| `4001` | 业务状态不允许 | 未质检就发运、异常批次操作 |
| `500` | 服务端异常 | 未捕获异常 |
| `5001` | 上链失败 | 节点不可用、合约 revert |
| `5002` | 链上链下数据不一致 | 一致性校验不通过 |

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
    "expiresIn": 43200,
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

### 2.2 获取当前用户

```
GET /auth/profile
```

**权限**：登录即可

**响应**：`data` 为 `UserVO` + `permissions` 数组。

### 2.3 退出登录

```
POST /auth/logout
```

**权限**：登录即可

说明：JWT 无状态，服务端不做黑名单，前端清除本地 token 即可。

### 2.4 修改密码

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
| `keyword` | string | 否 | — | 匹配批次号 / 溯源码 / 产品名 |
| `status` | string | 否 | — | 批次状态 |
| `abnormalFlag` | int | 否 | — | `1` 仅异常，`0` 仅正常 |
| `villageId` | long | 否 | — | 行政村筛选（仅 ALL 范围生效） |

> 非 `ALL` 数据范围的账号会被自动注入 `village_id` / `farmer_id` 条件，无需前端传参。

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
  "chainStatus": "SUCCESS"
}
```

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

说明：仅允许修改 `remark`，核心字段（产品、农场、农户、日期、数量）不可修改，以保证链上摘要稳定。如需修正请使用「更正说明」接口。

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

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 批次状态非 `QUALIFIED` | 4001 批次状态不允许发运，当前状态：XXX |
| 批次为异常状态 | 4001 异常批次禁止发运，请先完成处置 |

**副作用**：批次 → `IN_TRANSIT`，上链 `bizType=LOGISTICS`。

### 7.3 销售记录分页

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

### 7.4 销售登记

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

**业务校验**

| 场景 | 结果 |
| --- | --- |
| 批次为异常状态 | 409 异常批次已熔断，禁止销售 |
| 无物流记录（状态非 `IN_TRANSIT`） | 409 批次尚未完成物流交接，不可销售 |
| 销售数量 > 剩余库存 | 409 批次库存不足，剩余 XXX |
| 批次已售罄（`SOLD`） | 409 批次已售罄 |

**副作用**：累计销售数量 ≥ 批次数量 → `SOLD`，否则 `ON_SALE`；上链 `bizType=SALES`。

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

```json
{
  "disposalMeasure": "整批就地无害化销毁",
  "disposalResult": "已销毁 600kg，全程录像留证",
  "abnormalType": "PESTICIDE"
}
```

**副作用**：处置状态 → `DISPOSED`，上链 `bizType=DISPOSAL`。

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
    "blockNumber": 26,
    "groupId": 1
  }
}
```

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
  ]
}
```

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
| `file` | file | 是 | 文件本体，≤ 20MB，扩展名限 `jpg/jpeg/png/webp/pdf` |
| `bizType` | string | 否 | 业务类型，如 `PLANTING` / `QUALITY` |
| `bizId` | long | 否 | 关联业务 ID |

**响应**

```json
{
  "code": 0,
  "message": "上传成功",
  "data": {
    "id": 20,
    "fileName": "检测报告.pdf",
    "filePath": "/api/file/download/20",
    "fileHash": "e4b1c9...64位",
    "fileSize": 524288
  }
}
```

> `fileHash` 为该文件的 SHA-256，直接作为上链的 `fileHash` 字段使用。

### 11.2 文件下载

```
GET /file/download/{id}
```

**权限**：公开

返回文件二进制流，`Content-Disposition` 带原始文件名（已 URL 编码）。

---

## 十二、统计 `/stats`

### 12.1 看板总览

```
GET /stats/overview
```

**权限**：`stats:overview`

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

**权限**：登录 · **角色**：`PRODUCER`、`VILLAGE_ADMIN`、`REGULATOR`、`ADMIN`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `batchId` | long | 是 | 批次 ID |
| `bizType` | string | 是 | 被更正的业务类型 `PLANTING/HARVEST/QUALITY/LOGISTICS/SALES` |
| `bizId` | long | 是 | 被更正的记录 ID |
| `reason` | string | 是 | 更正原因 |
| `content` | string | 是 | 更正说明内容 |

```json
{
  "batchId": 1,
  "bizType": "PLANTING",
  "bizId": 12,
  "reason": "录入笔误",
  "content": "实际用量为 12kg/亩，原记录 21kg/亩有误，以本次说明为准"
}
```

**副作用**：新增 `biz_correction` 记录（原始记录不变），上链 `bizType=CORRECTION`。

### 14.2 更正记录分页

```
GET /correction/page?page=1&size=10&batchId=1
```

**权限**：`batch:list`

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
| 物流 | GET | `/logistics/page` | `logistics:list` | — |
| | POST | `/logistics` | `logistics:create` | LOGISTICS, ADMIN |
| 销售 | GET | `/sales/page` | `sales:list` | — |
| | POST | `/sales` | `sales:create` | SELLER, ADMIN |
| 异常 | GET | `/abnormal/page` | `abnormal:list` | — |
| | POST | `/abnormal/{id}/dispose` | `abnormal:dispose` | REGULATOR, ADMIN |
| | GET | `/abnormal/{id}/trace` | `abnormal:trace` | — |
| | POST | `/abnormal/batch/{id}/close` | `abnormal:dispose` | REGULATOR, ADMIN |
| 存证 | GET | `/chain/page` | `chain:list` | — |
| | GET | `/chain/verify/{batchNo}` | `chain:verify` | — |
| | GET | `/chain/status` | 登录 | — |
| 溯源 | GET | `/trace/{code}` | 公开 | — |
| | GET | `/trace/{code}/verify` | 公开 | — |
| 文件 | POST | `/file/upload` | `file:upload` | — |
| | GET | `/file/download/{id}` | 公开 | — |
| 统计 | GET | `/stats/overview` | `stats:overview` | — |
| | GET | `/stats/chain-failed` | `chain:list` | — |
| 更正 | POST | `/correction` | 登录 | PRODUCER, VILLAGE_ADMIN, REGULATOR, ADMIN |
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
