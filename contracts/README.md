# contracts · 链上存证合约

> 与后端业务代码完全解耦：后端仅通过 `backend/src/main/java/com/agri/trace/sdk` 访问合约，合约变更不影响业务层。

## 一、目录

```
contracts/
├── contracts/
│   ├── TraceLib.sol        # 工具库：字符串判等、摘要计算（内联，不单独部署）
│   └── Traceability.sol    # 核心存证合约
├── abi/                    # 编译产物（ABI）
│   ├── Traceability.abi
│   └── Traceability.json   # ABI + bytecode 合并产物
├── bin/
│   └── Traceability.bin    # 部署用 bytecode
├── scripts/
│   ├── compile.js          # solc 0.6.10 编译脚本
│   └── deploy.sh           # 部署脚本（调用后端 ContractDeployTool）
└── package.json
```

## 二、链上数据结构

| 结构 | 字段 | 说明 |
| --- | --- | --- |
| `BatchInfo` | batchNo / traceCode / productName / origin / latestHash / status / abnormalReason / createdAt / recordCount | 批次索引与最新摘要 |
| `Record` | bizType / bizId / dataHash / fileHash / operatorName / timestamp / abnormal | 单条流转存证 |

- `status`：`NORMAL` → `ABNORMAL` → `CLOSED`，不可逆向；
- `dataHash`：链下业务字段规范化拼接后的 SHA-256（64 位十六进制），链上以 string 存储；
- 异常批次（`ABNORMAL`）仅允许写入 `DISPOSAL`（处置）与 `CORRECTION`（更正）记录，**链上强制熔断正常流转**。

## 三、方法清单

| 方法 | 权限 | 说明 |
| --- | --- | --- |
| `registerBatch(batchNo, traceCode, productName, origin, dataHash, operatorName)` | 白名单 | 批次登记 |
| `addRecord(batchNo, bizType, bizId, dataHash, fileHash, operatorName)` | 白名单 | 追加流转记录 |
| `markAbnormal(batchNo, reason, dataHash, operatorName)` | 白名单 | 标记异常（质检不合格） |
| `disposeAbnormal(batchNo, result, dataHash, operatorName)` | 白名单 | 追加处置结果 |
| `closeBatch(batchNo, dataHash, operatorName)` | 白名单 | 结案 |
| `addCorrection(batchNo, bizType, bizId, correctionHash, operatorName)` | 白名单 | 追加更正说明 |
| `getBatch(batchNo)` | 公开 | 查询批次摘要 |
| `getRecord(batchNo, index)` / `getRecordCount` | 公开 | 查询存证记录 |
| `verifyRecord(batchNo, index, dataHash)` | 公开 | 一致性校验 |
| `authorize` / `revoke` / `transferOwnership` | owner | 写入地址白名单 |

事件：`BatchRegistered`、`RecordAdded`、`BatchMarkedAbnormal`、`BatchDisposed`、`BatchClosed`、`CorrectionAdded`，均含 `dataHash` 与时间戳，可直接作为链上凭证展示。

## 四、编译

```bash
cd contracts
npm install
npm run compile
```

要求 `solc 0.6.10`（FISCO-BCOS 2.9.x 兼容版本）。产物写入 `abi/` 与 `bin/`。

## 五、部署

### 方式一：脚本部署（推荐，使用 Java SDK）

```bash
# 1. 启动链并拷贝证书到 backend/src/main/resources/fisco/
# 2. 修改 application.yml 中 chain 配置
cd contracts
bash scripts/deploy.sh
# 输出：contract address = 0x...
# 将该地址写入 application.yml -> chain.contract-address
```

### 方式二：FISCO console 部署

```bash
# 将 abi/bin 拷贝到 console/contracts/sdk 目录后
./console.sh
[group:1]> deploy Traceability
contract address: 0x...
```

### 方式三：WeBASE-Front

合约管理 → 合约部署 → 选择 `Traceability.sol` → 部署 → 复制合约地址。

> 部署完成后，需调用 `authorize(address)` 将后端 SDK 账户地址加入白名单（owner 默认已授权）。
> 后端 `chain.private-key` 对应的地址即 SDK 账户地址。

## 六、升级策略

联盟链场景下合约不可篡改，业务升级采用 **新增合约 + 数据迁移标记** 的方式：
新合约部署后，在 `application.yml` 中切换 `chain.contract-address`，历史批次仍可通过旧地址查询，接口层按 `chain_record.contract_address` 路由（预留字段）。
