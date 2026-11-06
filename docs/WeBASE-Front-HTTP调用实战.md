# 农产品溯源系统的链上存证接入：基于 WeBASE-Front 的 HTTP 调用实现

> 适用范围：本项目（基于 FISCO BCOS 联盟链的农产品质量安全溯源系统）的链上接入方案说明。
> 说明：本系统业务主链路通过 FISCO BCOS Java SDK 直连节点完成；本文记录的是**第二种接入路径**——
> 不依赖任何区块链 SDK，仅用普通 HTTP 请求经 WeBASE-Front 交易网关调用链上合约，
> 覆盖「查询 → 写入 → 回执 → 事件」全流程。文中所有请求、响应、区块号与交易哈希均为
> 本项目环境（2026-11-06）实测结果，未做任何美化。

---

## 1. 为什么要在 SDK 之外，再引入一条 HTTP 调用路径

本系统的核心业务（批次登记、农事/采收/质检/物流/销售各环节存证）由后端服务通过
`fisco-bcos-java-sdk` 直连联盟链节点完成，这是任务书指定的方式，负责承担高频、大批量的存证写入。

在此基础上再引入 WeBASE-Front HTTP 路径，是出于三方面考虑：

1. **可视化运维**：WeBASE-Front 提供区块、交易回执、合约、节点监控的 Web 控制台，运维与演示人员无需
   安装 SDK 即可直接查看链上数据；
2. **降低接入门槛**：第三方系统（如监管平台、物流系统）往往不是 Java 技术栈，只需要会发 HTTP 请求就能
   读写链上合约，不必各自维护 `ca.crt / sdk.crt / sdk.key` 证书与 SDK 依赖；
3. **私钥集中托管**：签名账户的私钥保存在 WeBASE-Front 服务端的密钥库里，业务侧只持有账户地址，
   私钥不落地到每个接入方，安全性更好。

三种常见接入方式的对比如下（本表基于本项目实际选型整理）：

| 接入方式 | 依赖 | 私钥位置 | 适合场景 |
|---|---|---|---|
| Java SDK 直连 | SDK 包 + 节点证书 | 业务侧自持 | 系统内部主链路（本项目） |
| WeBASE-Front HTTP | 仅 HTTP 客户端 | WeBASE-Front 托管 | 控制台可视化、异构系统接入（本文） |
| 自签名交易 | 本地加密库 | 业务侧自持 | 私钥不能出域的场景 |

---

## 2. WeBASE-Front 交易网关的定位与一次调用的完整链路

WeBASE-Front 不是区块链本身，也不是替代 SDK 的库，它是一个**交易网关**：对外收 HTTP 请求，
对内完成「定位函数 → 编码 calldata → 本地私钥签名 → 广播交易 → 轮询回执」这一串动作，
最后把结果原样返回给调用方。

一次 `POST /trans/handle` 的完整过程如下：

```
业务系统                    WeBASE-Front                    FISCO BCOS 节点
   │                            │                                │
   │ ① 提交请求体               │                                │
   │ （函数名 + 入参 + 整包 ABI）│                                │
   ├───────────────────────────>│                                │
   │                            │ ② 依据 ABI 定位函数签名          │
   │                            │ ③ 编码 calldata                │
   │                            │ ④ 取本地私钥签名                │
   │                            │ ⑤ 广播原始交易                  │
   │                            ├───────────────────────────────>│
   │                            │                                │ ⑥ 打包出块
   │                            │ ⑦ 轮询交易回执                  │
   │                            │<───────────────────────────────┤
   │ ⑧ 返回回执（写）或出参（读）│                                │
   │<───────────────────────────┤                                │
```

这里最关键的一点是：**同一个接口同时承载「查询」和「写入」两种语义**，WeBASE-Front 依据 ABI 里
函数的 `stateMutability` 自动分流：

- 若函数是 `view` / `pure`，走 `eth_call` 语义，**不产生交易、不出块、不消耗 gas**，直接返回解码后的出参数组；
- 若函数是 `nonpayable` / `payable`，走 `sendRawTransaction`，**同步等待回执**，返回的是回执对象。

因此客户端拿到响应后，必须先判断它是「数组」还是「回执对象」，再决定怎么解析。

### 2.1 交易处理接口的请求体字段

`/trans/handle` 的请求体共 12 个字段，本项目实际取值如下（字段语义以线上 Swagger 与官方接口文档 5.2 节交叉核对为准）：

| 字段 | 类型 | 必填 | 本项目取值 / 说明 |
|---|---|---|---|
| `groupId` | int | 是 | `1`，联盟链群组编号 |
| `user` | String | 是 | 签名账户地址，必须是 WeBASE-Front 本地密钥库中的地址（本项目为 `agri-admin`） |
| `contractName` | String | 是 | 合约名，如 `StorageCenter` / `Traceability` |
| `contractPath` | String | 否 | 合约在控制台合约库中的目录，如 `demo` / `agri` |
| `contractAddress` | String | 是 | 合约地址 |
| `funcName` | String | 是 | 要调用的函数名 |
| `funcParam` | List | 否 | 入参，**1.5.5 起统一用字符串数组**，如 `["hash-001"]` |
| `contractAbi` | List | 是 | 整包 ABI（JSON 数组），也可传单个函数 ABI |
| `version` | String | 否 | 空串；**`version` 与 `contractAddress` 不能同时为空** |
| `useCns` | bool | 是 | `false`，本项目不走 CNS 命名服务 |
| `cnsName` | String | 否 | 空串，`useCns=true` 时才需要 |
| `useAes` | bool | 否 | `false`，保留的兼容字段 |

三条最容易踩的规则：

1. `funcParam` 在 1.5.5 之前是 JSON 数组，1.5.5 起改为字符串数组——数组入参需要整体塞进一个字符串里，例如 `set(string[] l)` 要写成 `["[\"a\",\"b\"]"]`。
2. `version` 与 `contractAddress` 至少要给一个，两者都为空会报「version and address cannot all be null」。
3. `user` 不是随意填的地址，必须是服务端密钥库里已有私钥的地址，否则签名环节会失败。

### 2.2 签名账户的前提

`user` 字段对应的私钥由 WeBASE-Front 托管。查看本地密钥库：

```bash
curl -s -u webase:Agri@2026 "http://127.0.0.1:5002/WeBASE-Front/privateKey/localKeyStores"
```

本项目返回：

```json
[
  {"address": "0xf19449c2228cc1b9fe36d9c00c65080479c8bdc3", "userName": "agri-admin", "type": 0}
]
```

其中 `type=0` 表示私钥就保存在 WeBASE-Front 本地（`type=1` 表示托管在 WeBASE-Sign）。
本项目只用本地私钥这一种，对应 `/trans/handle` 接口。

---

## 3. 两条接入路径对应的两个合约

### 3.1 生产合约 Traceability（业务存证，只读接入）

本系统的业务存证由 `Traceability` 合约承担，它由后端 Java SDK 部署，地址
`0x0426e8fbda2b5e61c64c56773ab251e03f4bbedb`。该合约的写操作（批次登记、环节存证、异常标记、处置、更正）
全部走 SDK，HTTP 路径只对它做**只读核对**——这也是一种交叉验证手段：用完全独立的第二条链路去读同一条链，
证明「控制台看到的数据就是业务写上去的数据」。

例如 HTTP 只读查询批次总数：

```
GET /trans/handle  →  funcName=getBatchCount, funcParam=[]
返回：["44"]
```

### 3.2 演示合约 StorageCenter（读写全流程）

为了完整演示 HTTP 写交易、回执与事件，本项目通过 WeBASE-Front 部署了一个 `StorageCenter` 演示合约，
地址 `0x26e59c7c12e540b7ced0a3a4ecdd8edc65335aa9`，部署者即签名账户 `agri-admin`（合约 owner）。

该合约以「数据哈希 → 数据内容/附加信息」的映射为核心，提供了写入、访问计数、删除、批量回读等函数，
逻辑上正好对应溯源存证的「写入存证数据 → 消费者扫码查阅 → 记录访问次数」这一场景：

| 函数 | 可变性 | 权限 | 返回形态 |
|---|---|---|---|
| `owner()` | view | 无 | 出参数组 |
| `getDataInfoByHash(hash)` | view | 无 | 出参数组（4 元素） |
| `getDataAccessCount(hash)` | view | 无 | 出参数组 |
| `writeData(hash, data, dataInfo)` | nonpayable | onlyOwner | 回执对象 |
| `accessData(hash)` | nonpayable | 无 | 回执对象（出参在 `output` 字段） |
| `deleteData(hash)` | nonpayable | onlyOwner | 回执对象 |

---

## 4. 全流程实测

> 以下全部在本项目环境（WeBASE-Front v1.5.5，FISCO BCOS 2.9.1，群组 1）执行，数据为真实抓取。

### 4.1 服务探活与链状态

`/version` 返回的是**纯文本**而非 JSON，这一点要在客户端做兼容：

```bash
curl -s -u webase:Agri@2026 "http://127.0.0.1:5002/WeBASE-Front/version"
# v1.5.5
```

查询群组与当前块高：

```bash
curl -s -u webase:Agri@2026 "http://127.0.0.1:5002/WeBASE-Front/1/web3/blockNumber"
# 207
```

块高既用于健康检查，也用于事件查询时限定 `toBlock` 的上限。

### 4.2 定位合约

`/contract/contractList` 是 **POST** 接口，用 GET 会报方法不支持。本项目合约库中登记了两个业务相关合约：

```
Traceability（path=agri，0x0426e8fb…，Java SDK 部署）
StorageCenter（path=demo，0x26e59c7c…，WeBASE-Front 部署）
```

### 4.3 只读查询：不上链、不消耗 gas

以 `Traceability.getBatchCount` 为例，命中 view 函数时直接返回解码后的出参数组：

```
["44"]
```

以 `StorageCenter` 为例，写入前查询某哈希对应的数据（此时尚未写入）：

```
["", "", "0", "0"]
```

需要留意的是：返回值是**字符串化的数组**，数字 `0` 返回成 `"0"`，业务侧做数值比较前要显式转换。

### 4.4 写交易：同步等待回执

调用 `writeData`（带 onlyOwner，须用 `agri-admin`）写入一条存证数据，实测响应关键字段：

```
transactionHash = 0xf3493113c4c6a7a7a643f32d414da2ad596da046a9bb8cf5ba6342272621988e
blockNumber     = 210
status          = 0x0
statusOK        = true
gasUsed         = 73005
logs            = 1 条（writeRecord 事件）
```

从这份回执可以读出四点：

1. `/trans/handle` 是**同步**的：返回时交易已经上块，业务侧无需再轮询；
2. FISCO BCOS 沿用以太坊早期的状态语义，`status = "0x0"` 才是成功（`0x16` 表示 revert），与以太坊主流的约定相反；
3. `output` 为空是因为 `writeData` 无返回值，有返回值的写函数会把 ABI 编码的出参放进 `output`；
4. 回执里自带 `logs`，就是本次交易触发的事件，无需额外订阅服务。

### 4.5 写后读回与计数校验

写入后再次查询同一哈希，数据已被读回：

```
["Hello FISCO BCOS Traceability", "uploader=agri-admin;size=35B", "0", "0"]
```

调用 `accessData`（有返回值的写函数，同时计数 +1）后：

```
accessData 区块 = 211，statusOK = true
getDataAccessCount = ["1"]
```

「写入 → 上链 → 读回 → 计数递增」的闭环成立。

### 4.6 独立查询回执

用 `/{groupId}/web3/transactionReceipt/{txHash}` 可独立复核回执：

```
status = 0x0, block = 210, statusOK = true
```

### 4.7 事件解析

回执里的 `logs` 就是事件，`writeData` 交易触发的 `writeRecord` 事件可直接按
`(address writer, string method, string firstParam, uint256 time)` 解码，
无需依赖事件订阅服务——这是性价比最高的做法：交易是自己发的，回执天然带日志。

> 注：`/event/eventLogs/list` 读的是 WeBASE-Front **本地事件表**，而该表由「事件订阅任务」写入，
> 未注册订阅时查到的总是空数组。需要跨系统实时推送时才走订阅 + 消息队列，日常对账优先用回执 `logs`。

---

## 5. 工程化封装

### 5.1 可复用的脚本交付物

本项目提供 `scripts/webase-http-demo.py`，把上面的全流程固化成一条可重复执行的脚本，
覆盖「探活 → 只读生产合约 → 演示合约读写 → 回执复核 → 计数校验」，接口与参数集中在上方配置区。

### 5.2 分层设计思想

参考的工程化思路是「配置、工具、合约、映射」四层分离，职责如下，本项目据此组织：

| 层次 | 职责 | 是否含业务语义 |
|---|---|---|
| 配置层 | 服务地址、群组、超时、默认用户、合约登记表 | 否 |
| 工具层 | 发请求、校验业务码、统一解析三种响应形态（JSON/数组/裸文本） | 否 |
| 合约层 | 绑定某个合约的地址与 ABI，暴露 `call`（读）与 `send`（写） | 是 |
| 映射层 | 把链上字符串数组 / ABI 编码出参映射成业务对象 | 否 |

这样分层后，换合约只需改配置，换业务只需新增 DTO，工具层无需改动。

### 5.3 一个关键设计：返回值形态要统一处理

工具层必须同时兼容三种返回：`/version` 的裸文本、只读函数的字符串数组、写函数的回执对象。
尤其是有返回值的写函数，`/trans/handle` 只对 view 函数做解码，写函数的出参在 `output` 里保持 ABI 编码，
需要客户端自行按 ABI 的出参类型解码。

### 5.4 地址与哈希的 YAML 陷阱

把地址写进配置时，`0x` 开头的值必须加引号——不加引号会被当成十六进制整数解析，
导致地址变成一长串十进制数，调用时直接报地址非法。

---

## 6. 常见问题与排查要点

| 现象 | 原因 | 处理 |
|---|---|---|
| HTTP 200、有交易哈希，但数据没变 | 只看了哈希，没判 `status` | 必须判 `status == "0x0"` 或 `statusOK == true` |
| `contractList` 用 GET 报方法不支持 | 它是 POST 接口 | 改用 POST 并带 JSON 请求体 |
| 报「Invalid method」或「cannot encode」 | 函数名拼错，或入参个数/类型与 ABI 不符 | 对照 ABI 的 `inputs` 逐项核对 |
| 报「version and address cannot all be null」 | 两个字段都为空 | 至少给 `contractAddress` |
| 报「address is invalid」且日志里地址是一长串数字 | YAML 把 `0x…` 当十六进制整数 | 地址/哈希/topic 一律加引号 |
| 事件查询 `code=0` 但 `data=[]` | 未注册事件订阅 | 改解析回执 `logs` |
| 合约 `require` 失败 | `status=0x16`、`statusMsg=RevertInstruction` | 解码 `output` 中的 `Error(string)` 拿原因 |

状态码语义务必记牢（与以太坊相反）：

| status | 含义 | 处理 |
|---|---|---|
| `0x0` | 成功 | 正常解析出参/日志 |
| `0x16` | 执行回滚（revert） | 解码 `output` 拿原因，绝不能当作成功 |
| `0x1` | 未知异常 | 记录并告警 |

---

## 7. 小结

本项目在 Java SDK 主链路之外，打通了第二条链上接入路径：业务侧只发 HTTP 请求，经 WeBASE-Front
交易网关完成签名、编码、广播与回执轮询，即可对链上合约做只读查询与写交易。这条路径同时服务于
可视化运维、异构系统接入与私钥集中托管三方面需求，与 SDK 主链路指向同一条链、同一套数据，
二者可交叉验证。文中所述流程、参数与坑点均已在本项目环境逐项实测通过。
