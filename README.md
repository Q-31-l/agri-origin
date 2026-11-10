# agri-origin · 基于 FISCO-BCOS 联盟链的农产品质量安全溯源系统

> 一批菜从 **建档 → 村级核验 → 农事 → 采收 → 质检 → 物流 → 销售**，每一步由谁录入、谁审核、什么时候上链，
> 在系统里都能查到、能验、能被追责。消费者扫码看到的是链上哈希与链下数据比对后的结果，而不是一张截图。

![license](https://img.shields.io/badge/license-MIT-blue)
![java](https://img.shields.io/badge/Java-8+-orange)
![springboot](https://img.shields.io/badge/SpringBoot-2.7.18-green)
![vue](https://img.shields.io/badge/Vue-3.4-42b883)
![uniapp](https://img.shields.io/badge/uni--app-3.0-2b9939)
![fisco](https://img.shields.io/badge/FISCO--BCOS-2.9.1-2f6feb)
![mysql](https://img.shields.io/badge/MySQL-8.0-4479a1)
![status](https://img.shields.io/badge/线上运行-https%3A%2F%2F8--130--105--150.sslip.io-1f7a4d)

**线上地址**：<https://8-130-105-150.sslip.io/> · 演示账号见 [§15.1](#151-演示账号)，密码统一 `123456`

**技术关键词**：Spring Boot · MyBatis-Plus · JWT + RBAC · uni-app 三端 · MySQL 8 · FISCO-BCOS 2.9.1 · Solidity 0.6.10 · SHA-256 存证

---

## 目录

| 章节 | 内容 |
| --- | --- |
| [一](#一这是个什么系统) | 这是个什么系统 |
| [二](#二界面预览) | 界面预览（含一条批次的完整旅程） |
| [三](#三项目背景与要解决的问题) | 项目背景与要解决的问题 |
| [四](#四系统架构) | 系统架构 |
| [五](#五技术栈) | 技术栈 |
| [六](#六角色权限与数据范围) | 角色、权限与数据范围 |
| [七](#七业务流程与状态机) | 业务流程与状态机 |
| [八](#八提交审核闭环) | 提交—审核闭环（本项目重点） |
| [九](#九链上存证设计) | 链上存证设计 |
| [十](#十溯源可信度评分-agritrust5d) | 溯源可信度评分 AgriTrust-5D |
| [十一](#十一数据模型) | 数据模型 |
| [十二](#十二接口概览) | 接口概览 |
| [十三](#十三部署与运维) | 部署与运维 |
| [十四](#十四测试与验收) | 测试与验收 |
| [十五](#十五快速启动与演示) | 快速启动与演示 |
| [十六](#十六仓库结构) | 仓库结构 |
| [十七](#十七文档索引) | 文档索引 |
| [十八](#十八开发分工与许可) | 开发分工与许可 |
| [十九](#十九已知限制与后续计划) | 已知限制与后续计划 |

---

## 一、这是个什么系统

一句话：**给农产品建一份"谁也改不了、谁做的谁负责"的电子档案**。

一份档案（系统里叫**批次**）从生产主体建档开始，依次经过村级核验、农事记录、采收登记、质量检测、物流运输、终端销售七个环节。
每个环节的提交人、提交时间、审核人、审核意见，以及业务数据的 SHA-256 摘要，都会写进联盟链；链下 MySQL 只负责"查得快"，
链上只存"改不了"的摘要。消费者扫码时，服务端会把链下数据重新算一遍哈希，和链上的比对——对不上就明确告诉你对不上，绝不假报"存证成功"。

系统要同时服务八种角色，所以设计上有三个绕不开的点：

1. **数据范围**：农户只看自己的批次，村管只看本村，监管看全量。范围不是前端过滤，是 SQL 层直接收窄，越权返回 403。
2. **流程顺序**：没通过村级核验的批次，农户提交农事会被直接拒绝；质检不合格的批次，销售登记会被熔断拦截。顺序在服务层强制，不靠界面约束。
3. **提交之后呢**：这一条最容易做漏——农户点了提交、页面提示"成功"，然后就没有然后了。本系统把"提交—审核"做成闭环：
   提交方立刻看到"等待谁审核"，审核方在"审核待办"里看到轮到自己处理的事，批次详情能看到七个环节逐级的进度。

### 1.1 五个能拿得出手的设计

| # | 设计 | 为什么重要 |
| --- | --- | --- |
| 1 | **一环节一存证 + 链下重算比对** | 不是"有区块链"四个字，而是每条记录都能拿链上哈希跟本地重算结果对一遍，对不上就报错 |
| 2 | **提交—审核闭环** | 提交方知道在等谁、审核方知道有几件事、进度逐级可见，流程不会停在"提交成功"四个字上 |
| 3 | **数据范围在 SQL 层收窄** | 农户/村管/监管看到的数据天然不同，越权返回 403，而不是前端把按钮藏起来 |
| 4 | **流程顺序与熔断由服务层强制** | 没核验不能录农事、不合格不能卖，绕过界面直接调接口同样会被拦 |
| 5 | **AgriTrust-5D 可信度评分** | 把"上没上链"的二值判断变成 0–100 分 + 扣分理由，消费者看得懂，答辩也讲得清 |

---

## 二、界面预览

以下截图全部来自**线上真实运行的这一版**（`https://8-130-105-150.sslip.io`，真实上链，非演示模式），
数据是同一条批次 `AG20261109011`（小白菜 · 上海青）从建档走到售出的真实记录。

### 2.1 一条批次的完整旅程（同一页面的四个时刻）

批次详情页最上面是**溯源可信度评分**，下面是**审核进度**：百分比、进度条、七个环节的横向节点条，
再往下逐个环节列出"谁提交、什么时候提的、谁审的、什么时候审的、审核意见"。四个时刻对照着看，就是一个流程的推进过程：

| 时刻 | 进度 | 卡在哪 |
| --- | --- | --- |
| ① 生产主体刚建档 | 29% | 等待村级核验 |
| ② 村管核验通过、农户提交农事 | 43% | 等待村管审核农事 |
| ③ 农事/采收通过、检测机构上传报告 | 71% | 等待监管复核质检 |
| ④ 物流、销售登记并复核完成 | 100% | 全流程完成，可扫码溯源 |

**① 建档完成，等村管核验**（29%，2/7 环节已推进）

![建档待核验](docs/assets/screenshots/uni-pc/flow-01-created.png)

**② 农事已提交，等村管审核**（进度条前进，农事环节变橙）

![农事待审](docs/assets/screenshots/uni-pc/flow-02-planting-pending.png)

**③ 质检报告已上传，等监管复核**

![质检待复核](docs/assets/screenshots/uni-pc/flow-03-quality-pending.png)

**④ 全流程完成，可信度 99 分、7/7 环节已推进**

![全流程完成](docs/assets/screenshots/uni-pc/flow-04-completed.png)

> 每个环节的"已提交 / 待审核 / 已驳回 / 未开始"用颜色区分；被驳回时会显示驳回原因，提交方修正后可重新提交。
> 进度条按"已推进环节"计算（已提交或已通过），所以不会出现"货都卖了，进度还停在 43%"这种别扭的显示。

批次详情往下还有基础信息、存证流水、全流程时间轴，滑动一屏能看到：

![批次详情下半部分](docs/assets/screenshots/uni-pc/pc-batch-detail-bottom.png)

### 2.2 提交—审核闭环：三个界面互相咬合

这是本系统与"能录入、能查询"的普通溯源系统最大的区别。三张页面说的是同一件事：

**(1) 提交方：提交完立刻知道在等谁**（移动端实测截图，弹窗由 `/workflow/notice` 实时返回）

<img src="docs/assets/screenshots/uni/m-submit-notice.png" width="280" alt="提交后提示等待审核" />

**(2) 审核方：登录就看到"有几项在等我"**（村管工作台，黄色横幅直接进待办）

<img src="docs/assets/screenshots/uni/m-home-manager.png" width="280" alt="村管移动端工作台" />

**(3) 审核方：待办列表按业务分组，可直接通过/驳回**

村管视角——本村批次核验、本村农事、本村采收（含尚未建档的"手写作物"）：

![村管审核待办](docs/assets/screenshots/uni-pc/pc-workflow-todo.png)

监管视角——质检、物流、销售三类复核（村级核验不属于监管的日常职责，不推给他，否则真正等他复核的事会被埋掉）：

![监管审核待办](docs/assets/screenshots/uni-pc/pc-workflow-todo-regulator.png)

移动端同一份数据，卡片式排布，单手可审：

<img src="docs/assets/screenshots/uni/m-workflow-todo.png" width="300" alt="移动端审核待办" />

三处提示的口径完全一致：**"已提交，等待<村名> <审核人姓名>（角色）审核"**，人走了、账号停用了会立刻从名单里消失，
一个村配了很多村管时只列前三个加"等 N 人"，不会把提示条撑爆。

### 2.3 PC 管理端（宽屏自适应）

**登录**：左侧品牌叙事，右侧账号登录，演示账号一键填入。

![登录](docs/assets/screenshots/uni-pc/pc-login.png)

**数据看板**：批次总量、异常批次、质检合格率、上链成功率，状态分布、近 7 日建档趋势、村级分布、链上存证概览。

![数据看板](docs/assets/screenshots/uni-pc/pc-dashboard.png)

**批次管理**：按批次号/产品/溯源码搜索，状态与存证状态在表格里直接可见，行内可"详情 / 核验"。

![批次管理](docs/assets/screenshots/uni-pc/pc-batch-list.png)

**批次详情**：可信度评分、审核进度、溯源二维码、链上存证、全流程时间轴（见 [§2.1](#21-一条批次的完整旅程同一页面的四个时刻)）。

![批次详情](docs/assets/screenshots/uni-pc/pc-batch-detail.png)

**七类业务页面**

| 农事记录 | 采收登记 | 质量检测 |
| --- | --- | --- |
| <img src="docs/assets/screenshots/uni-pc/pc-planting.png" width="360" alt="农事记录"> | <img src="docs/assets/screenshots/uni-pc/pc-harvest.png" width="360" alt="采收登记"> | <img src="docs/assets/screenshots/uni-pc/pc-quality.png" width="360" alt="质量检测"> |
| **物流运输** | **销售管理** | **异常管控** |
| <img src="docs/assets/screenshots/uni-pc/pc-logistics.png" width="360" alt="物流运输"> | <img src="docs/assets/screenshots/uni-pc/pc-sales.png" width="360" alt="销售管理"> | <img src="docs/assets/screenshots/uni-pc/pc-abnormal.png" width="360" alt="异常管控"> |

**链上存证**：链服务状态、当前区块高度、合约地址、上链成功/失败/跳过计数、逐笔流水（交易哈希、区块号、Gas、状态）。

![链上存证](docs/assets/screenshots/uni-pc/pc-chain.png)

**系统管理**

| 用户与角色 | 农场档案 | 产品品类 | 操作日志 |
| --- | --- | --- | --- |
| <img src="docs/assets/screenshots/uni-pc/pc-sys-user.png" width="300" alt="用户与角色"> | <img src="docs/assets/screenshots/uni-pc/pc-sys-farm.png" width="300" alt="农场档案"> | <img src="docs/assets/screenshots/uni-pc/pc-sys-product.png" width="300" alt="产品品类"> | <img src="docs/assets/screenshots/uni-pc/pc-sys-log.png" width="300" alt="操作日志"> |

**菜单按权限实时裁剪**：同一套代码，管理员 15 项、农户 6 项、物流企业 5 项，没有权限的分组连标题一起不渲染。

| 系统管理员 | 农户 | 物流企业 |
| --- | --- | --- |
| <img src="docs/assets/screenshots/uni-pc/role-admin.png" width="360" alt="管理员菜单"> | <img src="docs/assets/screenshots/uni-pc/role-farmer01.png" width="360" alt="农户菜单"> | <img src="docs/assets/screenshots/uni-pc/role-logistics01.png" width="360" alt="物流企业菜单"> |

### 2.4 移动端（同一份代码，390×844）

农户在田里用手机录入，是这套系统最主要的使用场景，所以移动端不是"PC 页面缩小"，而是独立的一套页面与交互。

| 登录 | 工作台（农户） | 我的批次 |
| --- | --- | --- |
| <img src="docs/assets/screenshots/uni/m-login.png" width="230" alt="移动端登录"> | <img src="docs/assets/screenshots/uni/m-home.png" width="230" alt="农户工作台"> | <img src="docs/assets/screenshots/uni/m-batch-list.png" width="230" alt="我的批次"> |
| **批次详情（含进度）** | **农事记录（可见审核状态）** | **链上存证** |
| <img src="docs/assets/screenshots/uni/m-batch-detail.png" width="230" alt="批次详情"> | <img src="docs/assets/screenshots/uni/m-planting-list.png" width="230" alt="农事记录"> | <img src="docs/assets/screenshots/uni/m-chain.png" width="230" alt="链上存证"> |

移动端批次详情把七个环节竖排，逐条写明"提交 张大山 · 19:11 / 审核 王秀兰 · 19:12"，不用点开二级页就能看完整个履历：

<img src="docs/assets/screenshots/uni/m-batch-detail.png" width="330" alt="移动端批次详情" />

**录入即留痕**：农事与采收都必须上传"现场实证"（图片或视频至少 1 条），文件 SHA-256 与业务摘要一并上链；
作业地点打开表单自动定位（GPS → 农场档案兜底），采收天气按当日实况带出，两者都需要用户确认而不是默认填充。

### 2.5 消费者溯源（免登录）

扫码进的就是这页：产品档案、可信度评分（含五个维度的得分与扣分说明）、全流程时间轴、链上存证与一致性校验。

<img src="docs/assets/screenshots/uni/m-trace.png" width="300" alt="移动端消费者溯源" />

PC 端同一份数据，宽屏排成左右两栏：

![PC 溯源结果](docs/assets/screenshots/uni-pc/pc-trace-result.png)

溯源查询入口页，支持输溯源码/批次号，也给几个真实样本码一键试：

![溯源查询](docs/assets/screenshots/uni-pc/pc-trace.png)

### 2.6 自助注册（三端同一入口）

登录页底部「立即注册」→ 注册页。农户、生产主体、检测机构、物流企业、销售商五类身份可自助注册，注册成功直接签发 JWT 进系统；
村管、监管、管理员三种治理身份不开放自助注册（能改别人数据的账号只走线下实名开通），点它们会提示原因而不是静默无反应。

![PC 自助注册](docs/assets/screenshots/uni-pc/register-pc.png)

<img src="docs/assets/screenshots/uni/register-mobile.png" width="300" alt="手机端自助注册" />

五个自助注册的账号走完的一条真实批次（`AG20261107003`），消费者扫码看到的样子：

<img src="docs/assets/screenshots/uni/register-trace-batch.png" width="300" alt="自助注册账号走完的批次" />

---

## 三、项目背景与要解决的问题

农产品从田间到餐桌链路长、参与主体多。传统中心化溯源系统的通病是：**平台自己既当运动员又当裁判**，
数据存在平台库里，改一条记录不留痕，出了事各主体互相不认账。本项目用联盟链把"记账权"分散到各参与方：

| 问题 | 传统做法 | 本项目的做法 |
| --- | --- | --- |
| 数据可被单方篡改 | 平台数据库，改了就改了 | 业务明细在 MySQL，摘要上链；改一个字段哈希就变，校验立刻失败 |
| 责任难界定 | 只记录"哪个企业"，不记录"哪个人" | 每条记录带提交人、审核人、时间、设备定位，逐级签字 |
| 跨主体互不信任 | 各自系统，接口对不上 | 同一联盟链、同一合约，检测/物流/销售都由链上事件留痕 |
| 出了问题追不动 | 只能查本环节 | 支持上游来源 + 下游流向双向追溯，异常批次全链熔断 |
| 消费者看不懂 | 一堆专业字段 | 折算成 0–100 可信度评分 + 逐条扣分理由 |

设计上的一条硬规则：**不伪造成功**。链不可用时系统进"演示模式"，页面如实显示"未上链"，
`/chain/verify` 返回失败，绝不在没有交易哈希的情况下显示"存证成功"。

---

## 四、系统架构

```
┌──────────────────────────────────────────────────────────────────────┐
│  客户端（uni-app 一套代码，三个产物）                                  │
│  PC 管理端（≥768px 自动进入）  ·  移动 H5  ·  微信小程序  ·  Android/iOS │
└───────────────────────────┬──────────────────────────────────────────┘
                            │ HTTPS / WebSocket（同一套 REST 接口，无本地库）
┌───────────────────────────▼──────────────────────────────────────────┐
│  Spring Boot 2.7.18                                                   │
│  ├─ Controller  接口层：@RequireRoles / @RequirePerm 双注解            │
│  ├─ Service     业务层：流程顺序校验、数据范围收窄、审核状态机          │
│  ├─ Workflow    审核流：进度 / 待办 / 提交提示（本项目的"闭环"）        │
│  ├─ Mapper      MyBatis-Plus：逻辑删除 + 乐观锁 @Version               │
│  └─ sdk.BcosClient  唯一链交互出口：健康探测、断连重建、ABI 从 classpath 读│
└──────┬───────────────────────────────────────┬────────────────────────┘
       │ JDBC                                   │ JSON-RPC（channel 长连接）
┌──────▼──────────────┐              ┌─────────▼───────────────────────┐
│  MySQL 8.0          │              │  FISCO-BCOS 2.9.1               │
│  业务明细（可查询）  │              │  4 节点 PBFT 单群组              │
│  权限 / 字典 / 日志  │              │  Traceability.sol 存证合约       │
└─────────────────────┘              │  WeBASE-Front 区块浏览器         │
                                     └─────────────────────────────────┘
```

**数据流向**（以提交一条农事记录为例）：

```
农户 H5 填表 → 上传现场实证（算文件 SHA-256）
   → POST /planting（服务端校验：批次存在、已通过核验、本人有权、当日同类未重复）
   → 组装业务摘要 dataHash（SHA-256）
   → BcosClient 调合约存证 → 返回 txHash / blockNumber
   → 写 biz_planting_record（含 dataHash / audit_status=PENDING）+ chain_record
   → WebSocket 广播事件 → 村管的待办与工作台角标自动刷新
```

---

## 五、技术栈

| 层次 | 技术选型 | 说明 |
| --- | --- | --- |
| 前端（三端统一） | uni-app 3.0（Vue 3.4 + Vite 5.2 + Pinia 2.1） | 一份 `src/` 出 H5 / 微信小程序 / App 三个产物 |
| 前端（H5 自适应） | 自研 PC shell + 移动 tabbar | ≥768px 进 PC 管理端（16 页），更窄进移动工作台，随窗口缩放实时切换 |
| 后端 | Spring Boot 2.7.18 + MyBatis-Plus 3.5.3 + JWT + Knife4j 4.4 + Hutool 5.8 | 统一响应 `R<T>`、`BizException` 分码 |
| 数据库 | MySQL 8.0（utf8mb4） | 逻辑删除 `deleted`、乐观锁 `version` |
| 区块链 | FISCO-BCOS **2.9.1** + Solidity 0.6.10 + fisco-bcos-java-sdk 2.9.1 | 4 节点 PBFT 单群组，一环节一存证 |
| 链可视化 | WeBASE-Front 1.5.5 | 只监听 127.0.0.1，nginx 反代 + Basic 认证 |
| 实时同步 | Spring WebSocket | 批次核验 / 异常处置 / 上链存证事件广播 |
| 文件存储 | 本地磁盘（可切 OSS/MinIO） | 文件 SHA-256 摘要随业务摘要上链 |
| 部署 | nginx + systemd + logrotate | 4 个链节点 / 后端 / WeBASE 全部单元化 |

### 为什么是"链下明细 + 链上摘要"

全量数据上链在联盟链上跑不动：一次写入要全节点共识，图片和长文本更不可能。所以采用**摘要上链**：

- 链下存明细，按业务字段排序后序列化，算 SHA-256 得 `dataHash`；
- 链上只存 `batchNo + eventType + dataHash + fileHash`；
- 校验时按同样规则重算、比对。**任何字段被改，哈希必然不同**——这就够证明"没被改过"了。

---

## 六、角色、权限与数据范围

### 6.1 八种角色

| 角色编码 | 角色 | 核心职责 | 主用端 |
| --- | --- | --- | --- |
| `FARMER` | 农户 | 农事记录、采收提交、现场拍照 | 移动 H5 |
| `VILLAGE_ADMIN` | 村级管理员 | 辖区农户管理、批次核验、农事/采收审核 | PC / H5 |
| `PRODUCER` | 生产主体 | 地块登记、产品与批次建档、全流程跟踪 | PC |
| `INSPECTOR` | 检测机构 | 质检报告上传、合格/不合格判定 | PC |
| `LOGISTICS` | 物流企业 | 运输交接登记、温控与回单上传 | PC |
| `SELLER` | 销售商 | 销售登记、批次核销 | PC |
| `REGULATOR` | 监管人员 | 全量监管、质检/物流/销售复核、异常处置 | PC |
| `ADMIN` | 系统管理员 | 全权限：用户治理、流程兜底、违规撤销、链账户与存量补注册 | PC |

### 6.2 权限模型

**RBAC（角色-权限）+ 数据范围**，接口层用 `@RequireRoles`、`@RequirePerm` 双注解校验，越权统一 `403 / 40301`。

- 权限点 **46 个**，角色授权 **148 条**，无"有权限点没角色"的悬空授权，也无"有角色没权限点"的孤儿授权
  （`sql/versions/patch-permission-matrix.sql` 末尾自带这两项自检 SQL）。
- `*:audit` 必须与对应 `*:list` 成对授予，否则会出现"有审核权限但列表 403，按钮永远点不到"。
- **系统管理员是唯一不受权限点约束的角色**：`@RequirePerm` 在校验前直接放行 `ADMIN`，
  且 `sys_role_permission` 里始终持有全部权限点（新增权限点时由补丁脚本补齐差量）。
  这样"加一个权限点"不会出现"接口通了、菜单却看不见"的半截状态。

### 6.3 数据范围

| 角色 | 范围 | 实际可见 | 审核职责 |
| --- | --- | --- | --- |
| 系统管理员 | `ALL` | 全量 | 全部（含 `chain:repair` 存量补注册） |
| 监管人员 | `ALL` | 全量 | 质检 / 物流 / 销售复核、异常处置与结案 |
| 生产主体 | `ORG` | 本人建档的批次及其全流程记录 | — |
| 村级管理员 | `VILLAGE` | 本村批次、本村农户、本村农事/采收 | 批次核验、农事审核、采收审核 |
| 农户 | `SELF` | 本人批次与本人录入的记录 | — |
| 检测机构 | `ORG` | 本机构出具的质检报告 | — |
| 物流企业 | `ORG` | 本人承运的运单 | — |
| 销售商 | `ORG` | 本人登记的销售记录 | — |

> 数据范围不是前端过滤：分页查询、详情、看板统计、更正记录、上下游追溯全部按上表收窄。
> 农户的 `/stats/overview` 批次总量与 `/batch/page` 的 total 恒等；村管核验其他村批次直接 403。

### 6.4 管理员兜底能力

流程之外的两种"事故处理"，只有系统管理员能做。它们不是新增的业务环节，
而是给已经出错的流程留一条可追责的退路。

**（1）账号治理**

| 操作 | 接口 | 规则 |
| --- | --- | --- |
| 启用 / 停用 | `PUT /user/{id}/status` | 停用后令牌当次请求即失效（不等 12 小时过期）；不能停用自己；不能停用最后一个启用的管理员 |
| 删除 | `DELETE /user/{id}` | 仅管理员；不能删除自己；不能删除最后一个启用的管理员；逻辑删除并释放登录名 |

删除为什么是逻辑删除：批次表冗余了责任人姓名，各环节留痕里也记着操作人。
物理删行会让"这个批次当年是谁报的"永久失去答案，而答不出这个问题的溯源系统没有意义。
删除后账号立即无法登录，登录名改写为 `原名#del{id}` 以释放唯一键——
`uk_username` 不含删除标记，不改名的话重新注册同名账号会撞唯一索引，
表现为"提示账号已存在却查不到这个人"。

**（2）违规记录撤销**

| 操作 | 接口 | 说明 |
| --- | --- | --- |
| 撤销业务记录 | `POST /admin/revoke` | `bizType` ∈ 农事/采收/质检/物流/销售，必须填理由 |
| 撤销实证文件 | `POST /admin/file/{id}/revoke` | 针对单张不合规照片、无关的截图或报告 |

设计上守三条：

1. **只做链下标记**。业务记录走既有的 `deleted`，存证流水打 `revoked` 标记。
   链上数据按设计不可删，撤销表达的是"不再采信"，不是"历史没发生过"。
2. **逆序撤销**。上游记录被撤后，下游就失去了存在前提（采收撤了，质检报告凭什么出）。
   有下游有效记录时直接拒绝，并提示先撤下游——
   这正是 §7.3 修掉的"链路断裂"问题，不能从后门重新放进来。
3. **撤销后重算批次阶段**。按"当前仍然有效的已审核记录"重新推导，而不是在原状态上加减。
   否则会出现外层显示"已采收"、里层一条记录都没有的老问题。

**撤销不会让存证校验变成"数据被篡改"。** 证据摘要 `evidenceHash` 仍按**原始文件集合**重算
（`listEvidence` 返回全量，展示走 `listEvidenceVisible` 过滤撤销项），
一致性校验则跳过 `revoked=1` 的存证并把它从分母里剔除。语义上很清楚：
链上记录的是"提交当时有什么"，撤销改变的是"现在是否采信"，两者不冲突。
撤销行为本身由 `OperationLogAspect` 自动记入操作日志（操作人、IP、理由）。

---

## 七、业务流程与状态机

### 7.1 七个环节、谁做、做完变成什么

| # | 环节 | 提交方 | 审核方 | 上链事件 | 完成后批次状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | 批次建档 | 生产主体 | 村级管理员核验 | `BATCH_REGISTER` | `PLANTING` + 待核验 |
| 2 | 村级核验 | 村级管理员 | —（核验本身即审核动作） | 核验结果随批次事件 | 已核验，农户可录农事 |
| 3 | 农事记录 | 农户 | 村级管理员 | `PLANTING_RECORD` | 不改变批次状态 |
| 4 | 采收登记 | 农户 | 村级管理员 | `HARVEST_RECORD` | `HARVESTED` |
| 5 | 质量检测 | 检测机构 | 监管人员复核 | `QUALITY_REPORT` | 合格 `QUALIFIED` / 不合格 `ABNORMAL` |
| 6 | 物流运输 | 物流企业 | 监管人员复核 | `LOGISTICS_HANDOVER` | `IN_TRANSIT` → `ON_SALE` |
| 7 | 终端销售 | 销售商 | 监管人员复核 | `SALES_RECORD` | `ON_SALE` / 售罄 `SOLD`；批次可关闭 `CLOSED` |

> **状态推进时机**：上表的"完成后批次状态"指**审核通过之后**。提交动作本身不改变 `biz_batch.status`
> ——采收提交后批次仍是 `PLANTING`，村管审核通过才变 `HARVESTED`；质检报告上传后仍是 `HARVESTED`，
> 监管复核通过才变 `QUALIFIED`。批次状态因此只表达"已经过审核的事实"，不会出现
> "外层显示已采收、审核进度里还写着待审核"的两套说法。

### 7.2 批次状态机

```
PLANTING ──采收──> HARVESTED ──质检合格──> QUALIFIED ──发运──> IN_TRANSIT
   │                    │                                        │
   │                    └──质检不合格──> ABNORMAL <──异常处置────┘
   │                                        │
   └──────────── 销售登记通过 ──> ON_SALE ──> SOLD ──> CLOSED
```

**熔断规则**：`ABNORMAL` 状态下销售登记与物流发运都会被前置拦截（返回业务错误而不是静默失败），
必须由监管登记处置结果、处置完成后才能继续。异常与处置都上链，形成"发现问题—谁处理的—结果如何"的完整链条。

**顺序强制**：每一环都要求前置环节**审核通过**才放行，而不是"上一步提交了"就放行——

| 操作 | 前置条件 | 不满足时的返回 |
| --- | --- | --- |
| 录入农事 | 批次已通过村级核验 | 400「批次尚未通过村级核验」 |
| 提交采收 | 该批次没有待审核的农事记录 | 400「还有 N 条农事记录等待村管审核」 |
| 上传质检 | 批次状态为 `HARVESTED`（采收已审核通过） | 400「仅已采收批次可提交质检报告」 |
| 登记物流 | 批次状态为 `QUALIFIED`（质检已复核） | 400「仅已质检合格批次可发运」 |
| 登记销售 | 批次状态为 `IN_TRANSIT` / `ON_SALE`（物流已复核） | 400「仅已完成物流交接的批次可销售」 |

这样"一级一级往上走"就不只是界面上的提示，而是后端真正拦得住：采收没审完，检测机构点上传会被拒绝；
质检没复核，物流企业选不到这个批次；物流没复核，销售商提交会被打回。

### 7.3 外层状态与内层进度同源

线上曾出现"批次详情外层显示 `已采收`，点开审核进度却是 `采收登记 待审核`"。根因是旧版在**提交时**就推进了
批次状态，而明细记录仍是待审核，两套口径各说各话。现在四处展示全部由同一批业务表推导：

| 展示位置 | 数据来源 | 口径 |
| --- | --- | --- |
| 批次状态标签 | `biz_batch.status` | 只反映**已审核通过**的最高阶段 |
| 列表"待审 · 采收登记" | `/batch/page` 返回的 `pendingStage` | 后端按环节顺序算出的当前卡点 |
| 详情页卡点标签 | `/workflow/progress/{id}` 第一个未完成环节 | 与下方进度条同源 |
| 扫码溯源页 | `detailPublic` 只下发 `audit_status=APPROVED` 的记录 | 消费者看不到未审核/被驳回的数据 |

配套的数据订正脚本：`sql/versions/patch-stage-consistency*.sql`——按"已通过记录"重算历史批次状态，
并给早期演示数据里"流程已走完、审核却挂着待审"的记录补上审核留痕（`audit_by=system`），幂等可重复执行。

---

## 八、提交—审核闭环

这是本系统区别于"能录入就行"的普通溯源系统的核心设计，单独用一节讲清楚。

### 8.1 三个问题

1. 提交方点了提交，页面只说"成功"——**然后呢？**
2. 审核方登录后不知道有新东西等他处理——**谁来审？**
3. 出问题时两边各说各话——**到底卡在哪一环？**

### 8.2 三个接口

| 接口 | 作用 | 权限 |
| --- | --- | --- |
| `GET /workflow/progress/{batchId}` | 七环节逐级进度：状态、提交人/时间、审核人/时间、审核意见、当前等谁 | `batch:list`（越权 403） |
| `GET /workflow/todo` | "我的审核待办"：按角色权限与数据范围聚合，只返回确实轮到他处理的数据 | 登录即可，按权限裁剪 |
| `GET /workflow/notice?bizType=&bizId=` | 提交后的提示文案：等谁、通过后进哪一环 | 提交人本人 |

三个接口共用**同一套阶段规则与数据范围**——进度说"在等村管"，村管的待办里就一定有这条；
不会出现"进度显示等审核、待办里却没有"的对不上。接口文档见 [`docs/api.md` §14b](docs/api.md)。

### 8.3 进度是怎么算的

七个环节各有 `DONE / PENDING / REJECTED / TODO / ABNORMAL` 五种状态：

- `TODO`：还没到这一步；`PENDING`：已提交、在等审核；`DONE`：已通过；`REJECTED`：被驳回；`ABNORMAL`：异常处置中。
- 百分比按**已推进环节**算（`DONE + PENDING + ABNORMAL`），不是只算"已通过"——否则已售出的批次会莫名显示 43%。
- 顶部一句话说清"现在卡在谁那里"，例如：「已提交，等待溪头村 王秀兰、曹聪（村级管理员）审核」。

### 8.4 待办怎么聚合

| 角色 | 待办内容 |
| --- | --- |
| 村级管理员 | 本村待核验批次、本村待审农事（含尚未建档的"手写作物"记录）、本村待审采收 |
| 监管人员 | 全量待复核质检 / 物流 / 销售 + 待处置异常批次 |
| 农户 / 生产主体 / 检测 / 物流 / 销售 | 无审核职责，返回空列表 |

> 村级核验只推给村管和管理员。监管虽然也持有 `batch:audit`（可介入越级核验），但"村级核验"不是他的日常职责，
> 放进待办只会把真正等他复核的质检/物流/销售顶下去。

### 8.5 驳回要能重提

- 农事实行"同类当日唯一"，但驳回记录不再占用这个名额——被驳回后可以修正重提，而不是卡死。
- 采收被驳回后批次状态回退到"种植中"，重新提交后再推进。
- 任何驳回都必须写原因，原因会通过 `audit_remark` 回传提交方，并显示在进度与列表里。

---

## 九、链上存证设计

### 9.1 合约

`contracts/Traceability.sol`（Solidity 0.6.10），核心是一个"按批次号组织的存证事件表"：

```solidity
// 简化示意
function record(string batchNo, string eventType, string dataHash, string fileHash)
    public returns (uint256 index);
function getRecordCount(string batchNo) public view returns (uint256);
function getRecord(string batchNo, uint256 index) public view returns (...);
```

- 写入：每个环节提交后写入一条，返回交易哈希与区块号，落库到 `chain_record`；
- 读取：`/chain/verify/{batchNo}` 逐条比对链上摘要与链下重算结果；
- 只追加不修改：录入错误不物理删除，走**更正记录**（`biz_correction`），更正本身也上链，历史完整保留。

### 9.2 存证事件

| event_type | 触发时机 |
| --- | --- |
| `BATCH_REGISTER` | 批次建档 |
| `PLANTING_RECORD` | 农事记录提交 |
| `HARVEST_RECORD` | 采收登记 |
| `QUALITY_REPORT` | 质检报告上传（含报告文件哈希） |
| `LOGISTICS_HANDOVER` | 物流交接登记 |
| `SALES_RECORD` | 销售登记 |
| `ABNORMAL_DISPOSAL` | 异常处置 |
| `BATCH_CLOSE` | 批次关闭 |

每张 `chain_record` 存 `batch_no / biz_type / biz_id / event_type / data_hash / file_hash / tx_hash / block_number / gas_used / status`，
`status` 取 `SUCCESS / FAILED / SKIPPED`（演示模式）。截图时全库 245 条存证、失败 0 条。

### 9.3 一致性校验与"不伪造成功"

- 链下按同样规则重算 SHA-256 与链上 `dataHash` 比对，同时校验交易回执 `status`；
- 校验失败明确提示"链上链下不一致"，并指出是哪一条；
- `chain.enabled=false` 时系统进演示模式：业务照常跑，页面标注"未上链"，`verify` 返回失败——
  **绝不把一个不存在的交易说成存证成功**。

### 9.4 链连接的工程细节

| 关注点 | 做法 |
| --- | --- |
| 断连自愈 | 客户端健康探测（3 秒缓存）+ 失败后 10 秒冷却重建 SDK 连接，**无需重启后端** |
| ABI 加载 | 走 classpath（`getInputStream`），jar 内可用，不依赖外部 `--chain.abi-path` |
| 节点托管 | 每节点一个 systemd 实例 `fisco-node@node0..3`，`Restart=always` |
| 启动顺序 | `agri-backend.service` 的 `After=/Wants=fisco-chain.target`，链先起、后端后起 |

---

## 十、溯源可信度评分 AgriTrust-5D

"上链了没有"是个二值判断，消费者看不出好坏。AgriTrust-5D 把它拆成 0–100 的五维加权分：

| 维度 | 权重 | 计分依据 |
| --- | --- | --- |
| 流程完整度 | 30 | 按批次所处阶段，考核"本应完成"的环节是否齐全（不把尚未发生的环节算作缺失） |
| 链上存证度 | 30 | 应存证条数与实际 `SUCCESS` 条数之比 |
| 质量合规性 | 20 | 质检结论 PASS/FAIL、检测项是否齐全 |
| 异常洁净度 | 15 | 是否有异常批次标记、是否已处置完成 |
| 信息完备度 | 5 | 农场、品种、报告文件等字段的完整程度 |

每条扣分都输出**可读理由**（如"缺少采收记录（-30）""档案信息缺失：农场（-1）"），消费者看到的是解释而不是一个分数。
模型细节与取舍见 [`docs/trust-score.md`](docs/trust-score.md)。

---

## 十一、数据模型

核心 14 张表（完整 DDL 见 `sql/init.sql`）：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `biz_batch` | 批次档案 | `batch_no`（`AG+yyyyMMdd+3位`）、`trace_code`、`status`、`audit_status`、`farmer_id`、`village_id`、`chain_status` |
| `biz_planting_record` | 农事记录 | `record_type`、`operation_date`、`content`、`evidence`、`audit_status/audit_by/audit_time` |
| `biz_harvest_record` | 采收登记 | `harvest_date`、`quantity`、`quality_level`、`weather`、`audit_*` |
| `biz_quality_report` | 质检报告 | `report_no`、`inspect_result`、`standard_no`、`file_hash`、`audit_*` |
| `biz_logistics_record` | 物流交接 | `logistics_no`、`from_org/to_org`、`depart_time`、`temperature`、`audit_*` |
| `biz_sales_record` | 销售登记 | `market_name`、`price`、`quantity`、`buyer_name`、`audit_*` |
| `biz_abnormal_disposal` | 异常处置 | `disposal_type`、`result`、`handler` |
| `biz_correction` | 更正记录 | `biz_type`、`biz_id`、`reason`、`before/after` |
| `chain_record` | 存证索引 | `tx_hash`、`block_number`、`data_hash`、`status` |
| `sys_user` / `sys_role` / `sys_permission` / `sys_role_permission` | RBAC | `role_code`、`perm_code`、`data_scope` |
| `sys_oper_log` | 操作日志 | `module`、`action`、`params`、`ip` |

**批次编码规则**：`AG + yyyyMMdd + 3 位当日序号`（如 `AG20261109011`），溯源码 `TC + 同序号`，两者一一对应。

---

## 十二、接口概览

| 分组 | 代表接口 | 说明 |
| --- | --- | --- |
| 认证 | `POST /auth/login`、`/auth/register`、`/auth/refresh`、`/auth/profile` | JWT 12h + refresh 7d，`typ` 声明隔离 |
| 批次 | `GET /batch/page`、`GET /batch/{id}`、`POST /batch`、`POST /batch/{id}/audit`、`POST /batch/{id}/close` | 分页/详情/建档/核验/关闭 |
| 农事 | `POST /planting`、`GET /planting/page`、`POST /planting/{id}/audit` | 提交、列表、审核 |
| 采收 | `POST /harvest`、`POST /harvest/{id}/audit` | 同上 |
| 质检 | `POST /quality`、`POST /quality/{id}/audit` | 含报告文件哈希 |
| 物流 | `POST /logistics`、`POST /logistics/{id}/audit` | 含温控、交接单 |
| 销售 | `POST /sales`、`POST /sales/{id}/audit` | 熔断在服务层 |
| 审核流 | `GET /workflow/progress/{batchId}`、`/workflow/todo`、`/workflow/notice` | 见 [§8](#八提交审核闭环) |
| 溯源 | `GET /trace/{code}`（免登录）、`GET /trace/verify/{batchNo}` | 扫码落地页数据 |
| 链 | `GET /chain/status`、`/chain/page`、`/chain/verify/{batchNo}`、`POST /chain/repair` | 状态、流水、校验、补注册 |
| 统计 | `GET /stats/overview` | 按数据范围收窄 |
| 文件 | `POST /file/upload`、`GET /file/download/{id}`、`GET /file/evidence` | 落盘 + 摘要；撤销后下载返回 403 |
| 账号治理 | `PUT /user/{id}/status`、`DELETE /user/{id}`、`GET /user/pending`、`POST /user/{id}/audit` | 启停、删除、注册审核，见 [§6.4](#64-管理员兜底能力) |
| 兜底处置 | `POST /admin/revoke`、`POST /admin/file/{id}/revoke` | 撤销违规记录与证据，仅系统管理员 |

完整入参/出参/权限见 [`docs/api.md`](docs/api.md)，在线文档 `/api/doc.html`（Knife4j）。

---

## 十三、部署与运维

单机 ECS 上的实际形态（`ops/` 内即线上在用的那一份；链账户私钥、数据库口令、JWT 密钥已抽到 `backend.env`，单元文件本身不含任何密钥）：

> **`sql/init.sql` 是"从零建库"脚本，内含 `USE agri_trace` 与 `DROP TABLE`，在已有数据的库上执行会清空全部业务数据。**
> 存量环境只应执行 `sql/versions/patch-*.sql` 增量补丁，且执行前必须先备份。
> 若只是校验脚本语法，请另起一个 MySQL 实例，不要连生产库。

| 关注点 | 做法 | 对应文件 |
| --- | --- | --- |
| 链节点托管 | 每节点一个 systemd 实例，`Restart=always`；不用 `build_chain` 的 `start.sh`（它按进程名判活，四节点共用一个二进制路径，只掉三个会被误判为"已运行"） | `ops/fisco-node@.service`、`ops/fisco-chain.target` |
| 启动顺序 | 后端 `After=/Wants=fisco-chain.target` | `ops/agri-backend.service` |
| 链断连自愈 | 健康探测 + 10 秒冷却重建 SDK，无需重启后端 | `BcosClient` |
| WebSocket | nginx 单独给 `location = /api/ws` 配 `Upgrade`/`Connection` 与 1 小时读超时 | `ops/nginx-agri-origin.conf` |
| 握手鉴权 | 无 token / 非法 token 一律 401（只 `return false` 会得到 200，客户端无法区分鉴权失败与服务异常，会无限重连） | `WsHandshakeInterceptor` |
| 链可视化 | WeBASE-Front 只监听 `127.0.0.1:5002`，nginx 反代 + Basic 认证（它没有登录且能用节点私钥签交易，不能裸奔公网） | `ops/webase-front.service` |
| 日志轮转 | systemd `append` 不轮转，配 `logrotate`（`copytruncate`，否则进程写旧 inode） | `ops/logrotate-agri-origin.conf` |
| 密钥管理 | 私钥/口令/密钥统一放 `backend.env`（`600`），`EnvironmentFile=` 注入；缺失或私钥为空则拒绝启动 | `ops/agri-backend.service`、`ops/backend.env.example` |
| HTTPS | 用 `8-130-105-150.sslip.io` 解析回源 IP，免域名签发受信任证书；80 全部 301 到 443（**扫码必须 HTTPS**，否则浏览器不给摄像头） | `docs/deploy.md` 7.3.1 |

### 13.1 一次真实故障复盘：链上批次号不能复用

线上曾出现「村管点审核通过 → 弹窗“上链失败，操作已回滚：交易执行失败：batch already exists”」。这不是审核权限或前端问题，而是**编号分配与链上不可删除特性冲突**：

- 合约 `registerBatch` 的校验是 `require(!_batches[batchNo].exists, "batch already exists")`，同一批次号只能注册一次；
- 原编号规则是「当日批次数 + 1」，删除过测试批次后会回退（库里剩 `001`、`011` 时 `count=2` → 又生成 `003`）；
- 数据库里删得掉，链上删不掉：`003` 是历史批次用过的号，于是补档/建档写链必然 revert，整笔审核事务回滚。

修复分三层，任何一层都能独立兜住：

1. **按最大序号递增**：取当日 `batch_no` 最大值 +1，而不是按条数，删除后不会回退；
2. **分配前问链**：候选号先用 `batchExistsOf` 探一次，链上已占用就跳过（这类“库已删、链仍在”的孤儿号会永久占位）；
3. **写链撞号重试**：`registerBatch` 若仍返回 `batch already exists`，自动换下一个可用号、重算摘要重写，最多 3 次；其它链上错误（未授权、节点不可用）原样抛出，不掩盖真问题。

同时把合约的英文 revert 翻译成可操作的中文提示（如“该批次号已在链上存证（历史批次占用），换个批次号重试即可”），避免界面出现半截英文。

> 结论：WeBASE-Front 只是链的可视化控制台，不参与业务写链，也不是本次故障来源；链上 4 节点、296 区块、248 条存证全部正常。

---

## 十四、测试与验收

| 脚本 | 覆盖内容 |
| --- | --- |
| `scripts/e2e-acceptance.py` | 8 角色登录 → 全流程真实上链 → 异常熔断 → 越权负例 → 数据范围隔离，49 项断言，任一失败非零退出 |
| `scripts/verify-full-chain.py` | 五个自助注册账号从建地块到售出，验证"新注册的人能不能真的把一批菜走完" |
| `scripts/verify-audit-flow.py` | 审核流：村管可见性、待办归属、审核留痕、进度推进 |
| `scripts/verify-auth-cache.py` | 权限缓存一致性（改角色后无需重启即生效） |
| `scripts/verify-handwrite-batch.py` | 农户手写作物 → 村管审核 → 自动建档 |
| `scripts/verify-register-login.py` / `verify-self-register.py` | 自助注册、限流、登录闭环 |

已在线上回归的关键场景：

- 村管农事列表曾因查询条件拼接错误恒为空（农户提交成功、村管看不到），修复后本村 58 条全部可见，链路上第一环不再断；
- 采收驳回后可重提、批次状态回退；
- 质检不合格 → 销售登记被拦截 → 监管处置 → 上下游追溯定位责任主体；
- 农户账号调质检接口 → 403；重复提交采收 → 409；重复同类农事 → 409。

---

## 十五、快速启动与演示

### 15.1 演示账号

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `123456` | 系统管理员 | 全部权限，含链上补注册 |
| `producer01` | `123456` | 生产主体 | 建档、指派农户 |
| `farmer01` | `123456` | 农户（溪头村） | 农事、采收 |
| `farmer02` | `123456` | 农户（上寨村） | 用于验证村级数据隔离 |
| `village01` | `123456` | 村级管理员 | 本村核验与审核 |
| `inspector01` | `123456` | 检测机构 | 质检报告 |
| `logistics01` | `123456` | 物流企业 | 运输交接 |
| `seller01` | `123456` | 销售商 | 销售登记 |
| `regulator01` | `123456` | 监管人员 | 复核与异常处置 |

### 15.2 本地启动

**环境要求**：JDK 8+、Maven 3.6+、Node.js 18+、MySQL 8.0+、FISCO-BCOS 2.9.1（可用 `build_chain.sh` 一键搭 4 节点单群组）

```bash
# 1. 数据库
mysql -uroot -p -e "CREATE DATABASE agri_trace DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p agri_trace < sql/init.sql
mysql -uroot -p agri_trace < sql/data.sql

# 2. 合约
cd contracts && npm install && npm run compile    # 生成 abi/ bin/
# 将 abi/Traceability.abi 与 bin/Traceability.bin 交给 FISCO console 部署
# 把合约地址写入 backend/src/main/resources/application.yml -> chain.contract-address

# 3. 后端
cd backend && mvn clean package -DskipTests
java -jar target/agri-trace-backend.jar           # 接口文档 http://localhost:8080/api/doc.html

# 4. 前端（三端统一工程）
cd frontend/uni-app && npm install
npm run dev:h5          # http://localhost:5173，宽屏自动 PC / 窄屏自动移动
npm run dev:mp-weixin   # 产物 dist/dev/mp-weixin，微信开发者工具导入
npm run dev:app         # 产物 dist/dev/app，HBuilderX 运行到手机
```

> 无链环境也能跑：`chain.enabled=false` 进演示模式，业务全通，页面如实标注"未上链"，一致性校验返回失败。
> 微信登录未配 appid/secret 时按端进入 mock 模式，可完整验证"首次绑定 → 直接登录 → 解绑"，日志显式标注，不伪装成真实微信登录。

### 15.3 十分钟演示路径（建议按这个顺序讲）

1. **农户**（手机）登录 → 选批次 → 新增农事（拍照上传）→ 提交后弹出「已提交，等待溪头村 王秀兰、曹聪（村级管理员）审核」；
2. **村管**（PC）登录 → 工作台提示"有 N 项待你审核" → 进「审核待办」→ 通过（或填原因驳回）；
3. 回到**批次详情**看进度条从"农事待审"变成"农事已完成"，并显示审核人姓名与时间；
4. **检测机构**上传质检报告（合格）→ 批次转"检测合格" → **监管**在待办里复核；
5. **物流企业**登记发运 → **销售商**登记销售 → 批次"销售中"；
6. **消费者**手机扫码（或打开 `/pages/trace/index?code=TC20261109011`）→ 可信度评分 + 时间轴 + 链上凭证 + 一键一致性校验；
7. 演示异常：另建批次质检"不合格" → 销售登记被拦截 → 监管处置 → 上游来源与下游流向定位责任主体；
8. 演示越权：农户账号调质检接口 → 403；重复提交采收 → 409。

### 15.4 线上入口

| 入口 | 地址 | 说明 |
| --- | --- | --- |
| 官网门户 | <https://8-130-105-150.sslip.io/> | 平台介绍 + 业务系统入口 |
| 业务系统 | <https://8-130-105-150.sslip.io/pages/launch/index> | 9 个演示账号，密码 `123456` |
| 免登录溯源 | <https://8-130-105-150.sslip.io/pages/trace/index?code=TC20261109011> | 消费者扫码落地页 |
| 链上控制台 | <https://8-130-105-150.sslip.io/WeBASE-Front/> | 账号 `webase` / `Agri@2026` |

---

## 十六、仓库结构

```
agri-origin/
├── backend/                    # Spring Boot 后端（后端开发只关注此目录 + /sql）
│   └── src/main/java/com/agri/trace/
│       ├── controller/         # 接口层
│       ├── service/            # 业务逻辑（WorkflowService 是审核流引擎）
│       ├── mapper/             # MyBatis-Plus 数据访问
│       ├── entity/ dto/ vo/    # 实体 / 入参 / 出参
│       ├── security/           # JWT、@RequirePerm、数据范围上下文
│       ├── sdk/                # FISCO-BCOS 封装（唯一链交互出口）
│       └── common/             # 统一响应、异常、常量
├── frontend/
│   └── uni-app/                # 三端统一工程（H5 / 微信小程序 / App）
│       ├── src/pages/          # 移动端页面（29 页）
│       ├── src/pages-pc/       # PC 管理端页面（16 页）
│       ├── src/components/     # 业务组件（workflow-steps、pc-workflow、evidence-uploader…）
│       ├── src/composables/    # useForm / usePage 等通用逻辑
│       └── scripts/            # 截图与端到端脚本
├── contracts/                  # Solidity 合约、ABI/BIN、编译部署脚本
├── ops/                        # 生产部署配置（systemd、nginx、logrotate）
├── scripts/                    # 验收与运维脚本（e2e-acceptance、verify-*）
├── sql/                        # init.sql、data.sql、增量补丁
│   └── versions/               # patch-wx-login / patch-optimistic-lock /
│                               # patch-permission-matrix / patch-audit-trail
├── docs/                       # 说明、部署、接口、测试、评分模型
│   └── assets/screenshots/     # 本文所有截图（uni-pc / uni）
└── portal/                     # 官网门户静态页（只接管根路径）
```

---

## 十七、文档索引

- [docs/README.md](docs/README.md) —— 项目详细说明与架构图
- [docs/deploy.md](docs/deploy.md) —— 部署手册（本地 / 云服务器 / 链节点 / HTTPS）
- [docs/api.md](docs/api.md) —— 接口说明（入参、出参、权限）；审核流见 §14b
- [docs/trust-score.md](docs/trust-score.md) —— AgriTrust-5D 计分规则与设计取舍
- [docs/uni-app-migration.md](docs/uni-app-migration.md) —— 三端改造方案与落地清单
- [docs/test.md](docs/test.md) —— 验收用例（正常流转、异常拦截、越权、重复提交、一致性）
- [contracts/README.md](contracts/README.md) —— 合约设计与部署
- [docs/WeBASE-Front-HTTP调用实战.md](docs/WeBASE-Front-HTTP调用实战.md) —— 第二种链上接入方式
- [docs/assets/README.md](docs/assets/README.md) —— 流程图、用例图、素材说明

---

## 十八、开发分工与许可

| 角色 | 负责目录 | 内容 |
| --- | --- | --- |
| 后端 | `/backend`、`/sql` | 业务接口、权限、审核流、链交互封装 |
| 前端 | `/frontend` | PC 管理端 + 移动 H5 + 小程序/App |
| 合约 | `/contracts` | 合约逻辑、编译部署 |
| 文档 / 测试 | `/docs`、`/scripts` | 说明、部署、接口、验收脚本 |

模块间只通过 HTTP 接口与链上合约解耦，单模块修改不影响其他目录。

## 十九、已知限制与后续计划

写在这里不是自曝其短，而是把边界说清楚——哪些是设计取舍，哪些是还没做的：

| 限制 | 现状 | 后续 |
| --- | --- | --- |
| 部署形态 | 单机 ECS，4 个链节点与后端同机（答辩与演示够用） | 生产环境应把节点分散到各参与方机房，形成真正的多机构联盟 |
| 存证粒度 | 摘要上链，明细留在 MySQL | 高价值环节（质检结论、销售核销）可考虑全量上链 |
| 文件存储 | 本地磁盘 + 文件哈希上链 | 可切 OSS/MinIO，切换只改存储实现 |
| 检测数据来源 | 检测报告由机构人工上传 | 对接 LIMS 或省级追溯平台接口，减少人工录入 |
| 链上治理 | 单群组、固定节点、无准入合约 | 增加节点准入与权限合约，支持动态增删机构 |
| 移动端发布 | H5 已上线；小程序 / App 已出包但未上架 | 补小程序类目资质与隐私协议后提交审核 |

---

[MIT](LICENSE) © 2026 agri-origin
