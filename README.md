# agri-origin · 基于 FISCO-BCOS 联盟链的农产品质量安全溯源系统

> 以"农产品批次"为主线，覆盖 **种植采收 → 质量检测 → 物流运输 → 终端销售** 全流程的联盟链溯源平台。
> 关键业务数据与文件摘要实时上链存证，支持扫码溯源、异常批次拦截、链上链下一致性校验与基层网格化管理。

![license](https://img.shields.io/badge/license-MIT-blue)
![java](https://img.shields.io/badge/Java-8+-orange)
![springboot](https://img.shields.io/badge/SpringBoot-2.7.18-green)
![vue](https://img.shields.io/badge/Vue-3.4-42b883)
![uniapp](https://img.shields.io/badge/uni--app-3.0-2b9939)
![fisco](https://img.shields.io/badge/FISCO--BCOS-2.9.1-2f6feb)
![webase](https://img.shields.io/badge/WeBASE--Front-1.5.5-1f6feb)
![mysql](https://img.shields.io/badge/MySQL-8.0-4479a1)

---

## 一、项目背景

农产品从田间到餐桌链路长、参与主体多，传统中心化溯源系统存在 **数据可被单方篡改、责任难以界定、跨主体互不信任** 等问题。本项目引入 FISCO-BCOS 联盟链作为可信底座：

- 业务明细存储在 MySQL（链下），保证查询性能；
- 业务摘要（SHA-256）与文件摘要写入联盟链（链上），保证不可篡改；
- 通过"链上哈希 ↔ 链下重算哈希"比对，实现 **数据一致性校验**；
- 检测不合格批次在链上打标，形成全联盟可见的 **销售熔断**，并支持上下游双向追溯定责。

## 二、技术栈

| 层次 | 技术选型 |
| --- | --- |
| 前端 PC 管理端 | Vue 3.4 + Vite 5.2 + Element Plus 2.7 + Pinia 2.1 + Vue Router 4 + ECharts 5.5 + Axios 1.6 |
| 前端 移动端 H5 | Vue 3.4 + Vite 5.2 + Vant 4.9 + Pinia 2.1 + `vue-qrcode-reader`（扫码） |
| 前端 三端统一（当前线上形态） | uni-app 3.0（Vue 3.4 + Vite 5.2 + Pinia 2.1）：**一套代码**产出 H5（PC + 移动自适应）/ 微信小程序 / Android·iOS App，共用同一后端与同一账号体系，方案与落地记录见 [docs/uni-app-migration.md](./docs/uni-app-migration.md) |
| 后端 | Spring Boot 2.7.18 + MyBatis-Plus 3.5.3 + JWT + Knife4j 4.4 + Hutool 5.8 |
| 数据库 | MySQL 8.0（utf8mb4，逻辑删除 + 乐观锁 `@Version`） |
| 区块链 | FISCO-BCOS **2.9.1**（Solidity 0.6.10，4 节点 PBFT 单群组）+ fisco-bcos-java-sdk 2.9.1 |
| 链可视化 | WeBASE-Front 1.5.5（区块浏览器 / 交易回执 / 节点监控，nginx Basic 认证） |
| 文件存储 | 本地磁盘（可切换 OSS/MinIO），文件 SHA-256 摘要上链 |
| 部署与运维 | nginx 反代 + systemd（4 个链节点 / 后端 / WeBASE-Front 全部单元化）+ logrotate，链断连自愈无需重启 |

## 三、角色与权限

| 角色编码 | 角色 | 核心职责 | 端 |
| --- | --- | --- | --- |
| `FARMER` | 农户 | 农事记录、生长信息、采收提交、现场拍照 | H5 |
| `VILLAGE_ADMIN` | 村级管理员 | 辖区农户管理、数据审核、批次核验 | PC / H5 |
| `PRODUCER` | 生产主体 | 产品与批次建档、全流程跟踪 | PC |
| `INSPECTOR` | 检测机构 | 质检报告上传、合格/不合格判定 | PC |
| `LOGISTICS` | 物流企业 | 物流交接登记、温控与回单上传 | PC |
| `SELLER` | 销售商 | 销售登记、批次核销 | PC |
| `REGULATOR` | 监管人员 | 全量监管、异常处置、一致性抽查 | PC |
| `ADMIN` | 系统管理员 | 用户/角色/字典/日志管理 | PC |

权限模型：**RBAC（角色-权限）+ 数据范围（SELF / VILLAGE / ORG / ALL）**，接口层通过 `@RequireRoles`、`@RequirePerm` 双注解校验，越权统一返回 `403 / 40301`。

| 角色 | 数据范围 | 实际可见范围 | 审核职责 |
| --- | --- | --- | --- |
| 系统管理员 | `ALL` | 全量 | 拥有全部权限（含 `chain:repair` 存量补注册） |
| 监管人员 | `ALL` | 全量 | 质检 / 物流 / 销售三类记录的复核、异常处置与结案 |
| 生产主体 | `ORG` | 本人建档的批次及其全流程记录 | — |
| 村级管理员 | `VILLAGE` | 本村批次、本村农户与本村农事/采收记录 | 批次核验、农事审核、采收审核 |
| 农户 | `SELF` | 本人批次与本人录入的记录 | — |
| 检测机构 | `ORG` | 质检报告（本机构出具） | — |
| 物流企业 | `ORG` | 本人承运的运单 | — |
| 销售商 | `ORG` | 本人登记的销售记录 | — |

> **数据范围不是装饰**：分页查询、详情查询、看板统计、更正记录、上下游追溯都会按上表收窄。
> 例如农户 `/stats/overview` 的批次总量与 `/batch/page` 的 total 恒等，生产主体看不到他人建档的批次详情，
> 村管核验其他村批次直接 403。
>
> **审核权限与列表权限必须成对**：任一 `*:audit` 都必须同时拥有对应 `*:list`，否则前端菜单被裁掉、
> 列表 403，审核按钮永远点不到。因此质检/物流/销售的复核权只授予监管方与管理员，村级管理员不持有——
> 这三类记录不属于"农户侧数据"，村管不需要也不应该看到物流报价与销售单价。
>
> 权限点共 **42 个**，角色授权 **139 条**，无"有权限点无角色"的悬空授权，也无"有角色无权限点"的孤儿授权
> （`sql/versions/patch-permission-matrix.sql` 末尾自带这两项自检 SQL）。

## 四、核心功能

1. **认证与权限**：JWT 无状态登录、令牌过期续期、角色路由守卫、接口级越权拦截、操作审计日志。
2. **批次全生命周期**：批次唯一编码 `AG + yyyyMMdd + 3 位当日序号`（如 `AG20260512001`），种植/采收/质检/物流/销售逐环节登记，每环节生成 `dataHash` 上链。
3. **存证与更正**：关键记录上链返回 `txHash / blockNumber`；录入错误不物理删除，通过 **追加更正说明** 保留完整历史。
4. **扫码溯源**：批次唯一溯源码（二维码），消费者免登录查询，按时间轴展示流转履历、质检摘要、链上凭证，并支持一键校验。扫码按端分工：小程序 / App 用宿主原生扫码，手机浏览器走站内实时取景（取景框、手电筒、扫描线），电脑端不申请摄像头、直接选择二维码图片识别。
5. **溯源可信度评分（AgriTrust-5D）**：把"有没有上链"的二值判断升级为 0–100 的可量化指标，从流程完整度、链上存证度、质量合规性、异常洁净度、信息完备度五个维度加权计分，并逐条输出扣分理由；按批次所处阶段动态调整考核环节，避免把"尚未发生"误判为"记录缺失"。模型说明见 [docs/trust-score.md](./docs/trust-score.md)。
6. **异常批次管控**：质检不合格 → 批次 `ABNORMAL` 状态 → 销售/物流环节前置拦截 → 处置记录上链 → 支持上游来源与下游流向双向追溯。
7. **一致性校验**：链下重算 SHA-256 与链上哈希比对 + 交易回执状态校验，校验失败明确提示，绝不显示"存证成功"。
8. **基层网格化**：村级管理员对辖区农户一对多管理，农户提交数据需村管审核后进入收购/质检环节。
9. **录入即留痕**：农事与采收的"现场实证"（图片或视频，至少 1 条）为强制项，文件 SHA-256 与业务摘要一并上链；作业地点打开表单即自动定位（GPS → 农场档案兜底），采收天气按当日实况自动带出（设备定位 → 网络位置兜底），两者都需用户确认而非默认填充。
10. **多端适配**：PC 管理端（数据看板、全流程后台）；H5（农户录入、拍照上传、消费者实时扫码）；小程序 / App 与 H5 同一套代码、同一账号体系。

## 五、界面预览

以下截图均来自实际运行，分两组环境：

- **5.1 / 5.2**：独立 PC / H5 工程，`chain.enabled=false` 演示模式，链上存证如实标记为「未上链」；
- **5.3**：当前线上环境，FISCO-BCOS 2.9.1 四节点单群组、**真实上链**（区块高度 `#177`、存证 165 条、成功率 100%）。

数据均来自 `sql/data.sql` 的演示批次。

### 5.1 PC 管理端（Vue 3 + Element Plus）

**数据看板**：批次总量、异常批次、上链成功率、状态分布与近 7 日建档趋势。

![数据看板](docs/assets/screenshots/pc-02-dashboard.png)

**批次管理**：支持按批次号、状态、生产主体、建档时间区间组合筛选，表格内直接展示村级核验与存证状态。

![批次管理](docs/assets/screenshots/pc-03-batch-list.png)

**批次详情**：档案信息、溯源二维码、全流程时间轴（含投入品与操作人）。

![批次详情](docs/assets/screenshots/pc-04-batch-detail.png)

**异常管控**：不合格批次自动熔断，可追溯上下游并登记处置结果。

![异常管控](docs/assets/screenshots/pc-06-abnormal.png)

**链上存证**：全部上链流水与回执；该套工程截图为演示模式，如实显示「未上链」，不伪造交易哈希。

![链上存证](docs/assets/screenshots/pc-07-chain.png)

**其余页面**

| 登录 | 质量检测 |
| --- | --- |
| ![](docs/assets/screenshots/pc-01-login.png) | ![](docs/assets/screenshots/pc-05-quality.png) |
| **溯源查询** | **用户与角色** |
| ![](docs/assets/screenshots/pc-08-trace.png) | ![](docs/assets/screenshots/pc-09-system-user.png) |

### 5.2 移动端 H5（Vue 3 + Vant）

**消费者溯源**（免登录扫码）：产品档案、质检结论、链上存证校验、全流程时间轴。

<img src="docs/assets/screenshots/h5-05-trace.png" width="320" alt="消费者溯源" />

**登录与工作台**：账号登录、我的批次、农事记录、采收提交、扫码溯源入口。

<img src="docs/assets/screenshots/h5-01-login.png" width="260" alt="H5 登录" />
<img src="docs/assets/screenshots/h5-02-home.png" width="260" alt="农户工作台" />
<img src="docs/assets/screenshots/h5-03-batches.png" width="260" alt="我的批次" />

**农事记录**：按批次逐条登记农事操作，投入品与操作人一并留痕。

<img src="docs/assets/screenshots/h5-04-planting.png" width="320" alt="农事记录" />

> 代表性截图见上；`docs/assets/screenshots/` 下共 62 张原图，覆盖三套前端的全部页面与关键交互态。

### 5.3 三端统一工程（uni-app · 当前线上部署）

`frontend/uni-app` 把上面两套独立前端收敛为**一套代码**，三个平台产物来自同一份 `src/`，共用同一后端接口与同一账号体系（同一账号在三端看到完全同步的数据）：

| 端 | 开发 | 构建 | 产物目录 |
| --- | --- | --- | --- |
| PC / H5（浏览器） | `npm run dev:h5` | `npm run build:h5` | `dist/build/h5` |
| 微信小程序 | `npm run dev:mp-weixin` | `npm run build:mp-weixin` | `dist/build/mp-weixin`（导入微信开发者工具） |
| Android / iOS App | `npm run dev:app` | `npm run build:app`（HBuilderX 云打包） | `dist/build/app` |

H5 端**纯自动自适应**：手机设备（iPhone / Android 手机）永远进入移动工作台；桌面 / 平板按窗口宽度判定，≥ 768px 自动进入 PC 管理台（14 个页面），更窄自动进入移动工作台，随窗口缩放实时切换，无需任何手动操作。小程序与 App 只打包各自需要的页面（构建产物 28 页，PC 页面完全不进包）。

**启动页**：打开小程序 / H5 先播一段品牌开场——标识带着两圈涟漪浮起，标题、副标题、分隔线、标语依次入场，底部进度条按时长走满，结束后淡出再进登录页（已登录用户 1.3s，未登录 1.9s），不会一打开就怼一个登录框。动画全部用 CSS，小程序端不做 SVG 依赖。

**登录页**：左侧是品牌叙事（五环节上链、SHA-256 校验、可信度评分），右侧账号登录，演示账号可一键填入；主按钮下方是**自助注册**入口，三端位置一致。

![uni-app 登录页](docs/assets/screenshots/uni-pc/pc-login.png)

**自助注册**（农户 / 销售商）：PC 左栏讲清"注册之后会发生什么"，右侧一张卡填完即登录；手机端同款表单、选村用底部面板，小程序与 App 复用同一页面。

<p>
  <img src="docs/assets/screenshots/register-pc.png" width="620" alt="PC 自助注册" />
  <img src="docs/assets/screenshots/register-mobile.png" width="180" alt="手机端自助注册" />
</p>

**数据看板**（与后端实时联动，看板数据随任意端写入自动刷新）

![uni-app 数据看板](docs/assets/screenshots/uni-pc/pc-dashboard.png)

**批次管理**：PC 端按下宽屏重排为 9 列表格，移动端为卡片流；筛选、分页、核验、审核同一套接口。

![uni-app 批次管理](docs/assets/screenshots/uni-pc/pc-batch-list.png)

**批次详情**：档案信息、溯源二维码、AgriTrust-5D 可信度评分、全流程时间轴、链上存证与一致性校验。

![uni-app 批次详情](docs/assets/screenshots/uni-pc/pc-batch-detail.png)

**链上存证**：链服务状态、区块高度、合约地址、存证流水与链上链下一致性校验。截图里是真实交易哈希与区块号，不是占位数据。

![uni-app 链上存证](docs/assets/screenshots/uni-pc/pc-chain.png)

**全流程业务页面（12 页）**

| 农事记录 | 采收登记 | 质量检测 |
| --- | --- | --- |
| <img src="docs/assets/screenshots/uni-pc/pc-planting.png" width="360" alt="农事记录"> | <img src="docs/assets/screenshots/uni-pc/pc-harvest.png" width="360" alt="采收登记"> | <img src="docs/assets/screenshots/uni-pc/pc-quality.png" width="360" alt="质量检测"> |
| **物流运输** | **销售管理** | **异常管控** |
| <img src="docs/assets/screenshots/uni-pc/pc-logistics.png" width="360" alt="物流运输"> | <img src="docs/assets/screenshots/uni-pc/pc-sales.png" width="360" alt="销售管理"> | <img src="docs/assets/screenshots/uni-pc/pc-abnormal.png" width="360" alt="异常管控"> |
| **溯源查询** | **溯源结果（消费者视角）** | **用户与角色** |
| <img src="docs/assets/screenshots/uni-pc/pc-trace.png" width="360" alt="溯源查询"> | <img src="docs/assets/screenshots/uni-pc/pc-trace-result.png" width="360" alt="溯源结果"> | <img src="docs/assets/screenshots/uni-pc/pc-sys-user.png" width="360" alt="用户与角色"> |
| **农场档案** | **产品品类** | **操作日志** |
| <img src="docs/assets/screenshots/uni-pc/pc-sys-farm.png" width="360" alt="农场档案"> | <img src="docs/assets/screenshots/uni-pc/pc-sys-product.png" width="360" alt="产品品类"> | <img src="docs/assets/screenshots/uni-pc/pc-sys-log.png" width="360" alt="操作日志"> |

**移动端（同一份代码，390×844）**

| 登录 | 工作台 | 批次列表 |
| --- | --- | --- |
| <img src="docs/assets/screenshots/uni/m-login.png" width="220" alt="移动端登录"> | <img src="docs/assets/screenshots/uni/m-home.png" width="220" alt="移动端工作台"> | <img src="docs/assets/screenshots/uni/m-batch-list.png" width="220" alt="移动端批次列表"> |
| **批次详情** | **我的** | **消费者溯源** |
| <img src="docs/assets/screenshots/uni/m-batch-detail.png" width="220" alt="移动端批次详情"> | <img src="docs/assets/screenshots/uni/m-mine.png" width="220" alt="移动端我的"> | <img src="docs/assets/screenshots/uni/m-trace-trust.png" width="220" alt="移动端溯源"> |

**三端互通的五个支撑点**

| 能力 | 实现 |
| --- | --- |
| 同一账号三端登录 | 后端 JWT 无状态（12h），PC / H5 / 小程序 / App 并发登录互不影响 |
| 微信一键登录 | 小程序走 `sns/jscode2session`、App 走开放平台 `sns/oauth2/access_token`，openid 分列存储，绑定后与账号密码登录完全等价 |
| 静默续期 | access 12h + refresh 7d，401 时自动换发并重放原请求，用户无感；两 token 用 `typ` 声明隔离，不能混用 |
| 数据完全一致 | 三端只通过同一套 REST 接口读写，无本地库；写操作走乐观锁（`@Version`），并发冲突返回 409 而不是后写覆盖前写 |
| 实时同步 | `ws://<host>/api/ws?token=` 广播批次核验 / 异常处置 / 上链存证事件，接收端自动刷新列表，无需手动刷新 |

> 微信登录在未配置 appid/secret 时按端进入 mock 模式，可完整验证"首次绑定 → 直接登录 → 解绑"闭环，日志会显式标注，不伪装成真实微信登录。

**品牌标识**：徽章内是「草坡 + 破土而出的双叶嫩芽 + 一枚链上节点」，把农业与区块链压进同一个图形。图形以 SVG 为源（`src/static/logo/mark.svg`），导出 256px PNG 供小程序 / App 使用，三端渲染完全一致；组件 `components/common/brand-logo.vue` 支持横向 / 竖向两种排布与深底浅底两套配色，PC 侧栏、登录页、启动页、关于页共用。

**导航选中态**：PC 侧栏选中项采用毛玻璃效果——半透明多层高光带 + `backdrop-filter` 背景模糊 + 1px 内描边 + 左侧发光指示条，切换时有一次扫光过渡。侧栏底色铺了三层径向光斑，为模糊提供可被"揉开"的内容，否则纯色背景上的 `backdrop-filter` 是看不出效果的。

**角色差异**：菜单不是写死的，而是按登录用户的权限码实时裁剪，分组里一项不剩时连分组标题一起隐藏。同一套代码，系统管理员看到 14 项、农户 6 项、物流企业 5 项。

| 系统管理员（14 项） | 农户（6 项） | 物流企业（5 项） |
| --- | --- | --- |
| ![](docs/assets/screenshots/uni-pc/role-admin.png) | ![](docs/assets/screenshots/uni-pc/role-farmer01.png) | ![](docs/assets/screenshots/uni-pc/role-logistics01.png) |

**可信度评分**：消费者扫码后拿到的不是一串原始记录，而是一份带扣分理由的可信度报告——左边是消费者溯源页，右边是 PC 批次详情。

| 移动端溯源页 | PC 批次详情 |
| --- | --- |
| ![](docs/assets/screenshots/uni/m-trace-trust.png) | ![](docs/assets/screenshots/uni-pc/pc-batch-detail.png) |

## 六、仓库结构

```
agri-origin/
├── backend/                 # Spring Boot 后端（后端开发只关注此目录 + /sql）
│   ├── src/main/java/com/agri/trace/
│   │   ├── controller/      # 接口层
│   │   ├── service/         # 业务逻辑层（impl 实现）
│   │   ├── mapper/          # 数据访问层（MyBatis-Plus）
│   │   ├── entity/          # 实体类
│   │   ├── dto/ vo/         # 入参 / 出参对象
│   │   ├── config/          # 配置类
│   │   ├── security/        # JWT、权限拦截、上下文
│   │   ├── sdk/             # FISCO-BCOS SDK 封装（唯一链交互出口）
│   │   └── common/          # 统一响应、异常、工具
│   ├── src/main/resources/  # application.yml、logback、mapper xml、fisco 证书
│   └── src/test/java/       # 单元测试
├── frontend/
│   ├── pc-admin/            # PC 管理端（layout / views / components / api / utils）
│   ├── mobile-h5/           # 移动端（pages / components / api / utils）
│   └── uni-app/             # 三端统一工程（H5 / 微信小程序 / App），一套代码三个产物
├── contracts/               # Solidity 合约、ABI/BIN、编译与部署脚本
├── ops/                     # 生产部署配置（systemd 单元、nginx 站点配置，与线上一致）
├── scripts/                 # 运维/验收脚本（e2e-acceptance.py 全流程真实上链验收）
├── sql/                     # init.sql、data.sql、增量补丁（patch-wx-login / patch-optimistic-lock / patch-permission-matrix）
└── docs/                    # 项目说明、部署手册、测试用例、接口文档、uni-app 改造方案
```

## 七、快速启动

### 1. 环境要求

- JDK 8+、Maven 3.6+、Node.js 18+、MySQL 8.0+
- FISCO-BCOS 2.9.1 节点（可用 `build_chain.sh` 一键搭 4 节点单群组）

### 2. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE agri_trace DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p agri_trace < sql/init.sql
mysql -uroot -p agri_trace < sql/data.sql
```

### 3. 部署合约

```bash
cd contracts && npm install && npm run compile     # 生成 abi/ bin/
# 将 abi/Traceability.abi 与 bin/Traceability.bin 交给 FISCO console 部署
bash scripts/deploy.sh
# 把输出的合约地址写入 backend/src/main/resources/application.yml -> chain.contract-address
```

> 无链环境也可启动：`chain.enabled=false` 时系统进入 **演示模式**，所有业务可跑通，但页面明确标注"未上链"，一致性校验返回失败——**绝不伪造存证成功**。

### 4. 启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/agri-trace-backend.jar
# 接口文档 http://localhost:8080/api/doc.html
```

### 5. 启动前端

```bash
# 方式一：三端统一工程（推荐，一个工程出三个端）
cd frontend/uni-app && npm install
npm run dev:h5          # http://localhost:5173 —— 宽屏自动进 PC 管理台，窄屏自动进移动端
npm run dev:mp-weixin   # 产物 dist/dev/mp-weixin，用微信开发者工具导入
npm run dev:app         # 产物 dist/dev/app，用 HBuilderX 运行到手机 / 模拟器

# 想看真实流畅度（切页无顿挫）请用生产构建预览：
npm run build:h5 && npm run preview:h5   # http://localhost:4173，已把 /api 代理到 8080

# 方式二：两套独立前端（仅用于对照历史版本）
cd frontend/pc-admin && npm install && npm run dev     # http://localhost:5173
cd frontend/mobile-h5 && npm install && npm run dev    # http://localhost:5174
```

> 三端工程与旧前端共用 5173 端口，二者**不要同时启动**。
> 微信登录：小程序填 `application.yml` 的 `app.wx.appid/secret`，App 填 `app.wx.app-appid/app-secret`（两套 appid 不同源）；不配则按端进入 mock 模式，可完整走通绑定闭环。
> 小程序真机预览还需在 `frontend/uni-app/src/manifest.json` 填 `mp-weixin.appid`。

### 6. 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | 123456 | 系统管理员 |
| producer01 | 123456 | 生产主体 |
| farmer01 | 123456 | 农户（溪头村） |
| farmer02 | 123456 | 农户（上寨村，用于验证村级数据隔离） |
| village01 | 123456 | 村级管理员 |
| inspector01 | 123456 | 检测机构 |
| logistics01 | 123456 | 物流企业 |
| seller01 | 123456 | 销售商 |
| regulator01 | 123456 | 监管人员 |

**自助注册（三端同一入口）**：登录页底部的「自助注册」——浏览器（PC / 手机）、微信小程序、App 都能直接走到 `/pages/auth/register`，官网门户的 Web 入口卡也放了「注册账号」按钮。**溯源链上每一个录入方都能自己注册**，不用再找管理员代开账号：

| 身份 | 注册后能做什么 | 必填项 |
| --- | --- | --- |
| 农户 | 填报农事、提交采收（只看被指派给自己的批次） | 所属村 |
| 生产主体 | 登记地块 → 建立批次 → 跟踪全流程 | 所属村 + 合作社/家庭农场名称 |
| 检测机构 | 上传质检报告、给出 PASS/FAIL 判定 | 检测机构名称 |
| 物流企业 | 登记运输交接、冷链温度 | 物流企业名称 |
| 销售商 | 登记销售、批次核销 | 门店/企业名称 |

- 村管、监管、管理员三种**治理身份不开放自助注册**：村管能管本村账号、监管能看全量数据、管理员能改配置，开放等于把别人的数据处置权交给任意访客，只能线下实名开通。注册页第六格是这三个身份的说明位，点它会给出提示而不是静默无反应；
- 注册成功即签发 JWT，不用再输一遍密码；后端按来源 IP 限流（每 IP 每小时 5 个成功账号 / 30 次请求，只统计成功账号，填错信息不扣配额）；
- 新注册的**生产主体**可以自己登记地块（`PRODUCER` 新增 `farm:create/farm:update`，服务层强制只能建在本村），否则新主体没有地块就建不了批次；
- 新农户注册后批次列表为空属于正常业务状态——账号要先被生产主体或村管指派到批次，才会看到农事与采收任务；批次建档后还要村管核验，农户才能录农事（流程顺序在服务层强制）。

**全链路真实验收**（`scripts/verify-full-chain.py`，服务器本机执行）：新注册的 5 个账号依次完成 建地块 → 建批次 → 村管核验 → 农事（带现场实证）→ 采收 → 质检（带报告文件）→ 物流 → 销售 → 消费者扫码。产物批次 `AG20261107003 / TC20261107003`：履历 6 个环节齐全，链上 6 条存证全部 `SUCCESS`，`/chain/verify` 校验 6/6 通过。这条批次的履历页（免登录可查）：

<img src="docs/assets/screenshots/register-trace-batch.png" width="300" alt="五个自助注册账号走完的批次溯源页" />

新账号登录后不会只剩一片 0：PC 看板会按角色给一条引导（生产主体「去批次管理」、检测「去质量检测」、物流「去物流运输」、销售「去销售管理」、农户则是「等待被指派批次」），移动端工作台的快捷入口同样按权限裁剪。

## 七·补、生产部署与运维

单机 ECS 上的实际部署形态（`ops/` 内是线上在用的那一份；链账户私钥、数据库口令、JWT 密钥已抽到 `backend.env`，单元文件本身不含任何密钥）：

| 关注点 | 做法 | 对应文件 |
| --- | --- | --- |
| 链节点托管 | 每个节点一个 systemd 实例 `fisco-node@node0..3`，`Restart=always`；不再用 `build_chain` 生成的 `start.sh`（它用 `ps aux \| grep fisco-bcos` 判活，四个节点共用一个二进制路径，只掉三个时会被误判为"已运行"） | `ops/fisco-node@.service`、`ops/fisco-chain.target` |
| 启动顺序 | 后端单元 `After=/Wants=fisco-chain.target`，链先起、后端后起 | `ops/agri-backend.service` |
| 链断连自愈 | 客户端健康探测（3 秒缓存）+ 失败后按 10 秒冷却重建 SDK 连接，**无需重启后端**；`/chain/status` 的 `ready` 反映真实链路状态 | `BcosClient` |
| WebSocket | nginx 单独给 `location = /api/ws` 配 `Upgrade`/`Connection` 头与 1 小时读超时，否则握手被当普通 HTTP，实时同步失效 | `ops/nginx-agri-origin.conf` |
| 握手鉴权 | 无 token / token 非法一律返回 **401**（只 `return false` 会得到 200，客户端无法区分鉴权失败与服务异常，会无限重连） | `WsHandshakeInterceptor` |
| ABI 加载 | 走 classpath（`getInputStream`），jar 内可用，不再依赖外部 `--chain.abi-path` | `BcosClient#readAbi` |
| 链可视化 | WeBASE-Front 1.5.5 只监听 `127.0.0.1:5002`，nginx 反代 + HTTP Basic 认证；它没有内置登录且能用节点私钥签交易，**不能裸奔公网** | `ops/webase-front.service`、`ops/nginx-agri-origin.conf` |
| 链版本 | 2.9.0 → 2.9.1 原地换二进制（`nodes/127.0.0.1/fisco-bcos`，不是安装根目录那份），数据/证书不动，区块高度不倒退 | `docs/deploy.md` 11.4 |
| 日志轮转 | systemd `StandardOutput=append` 不轮转，配 `logrotate`（`copytruncate`，否则进程写旧 inode） | `ops/logrotate-agri-origin.conf` |
| 密钥管理 | 链账户私钥 / 数据库口令 / JWT 密钥统一放 `backend.env`（`600`），由 `EnvironmentFile=` 注入；文件缺失或私钥为空则拒绝启动 | `ops/agri-backend.service`、`ops/backend.env.example` |

上线自检（`scripts/e2e-acceptance.py` 在服务器本机执行，覆盖 49 项断言）：

```bash
python3 scripts/e2e-acceptance.py     # 8 角色登录 → 全流程真实上链 → 异常熔断 → 越权负例 → 数据范围隔离
```

> 脚本会真实写链（每轮新增若干区块），输出形如 `验收结果：49/49 通过`，
> 任一项失败即非零退出，可直接接入发布流水线。

**线上地址（当前运行中）**

| 入口 | 地址 | 说明 |
| --- | --- | --- |
| **官网总门户** | <https://8-147-61-0.sslip.io/> | 平台介绍 + 业务系统入口；小程序 / App 入口已预留 |
| 业务系统（H5/PC 自适应） | <https://8-147-61-0.sslip.io/pages/launch/index> | 9 个演示账号，密码统一 `123456`；`http://` 会自动 301 到 HTTPS |
| 免登录溯源页 | <https://8-147-61-0.sslip.io/pages/trace/index?code=TC20261106013> | 消费者扫码后的落地页 |
| 链上控制台 | <https://8-147-61-0.sslip.io/WeBASE-Front/> | 账号 `webase` / `Agri@2026`；看区块、交易回执、节点状态 |

> **门户与业务系统的路径关系**：门户只接管根路径 `/`（以及 `/portal/`），
> 其余路径仍由 uni-app H5 处理，所以 `/pages/launch/index`、`/pages/trace/index?code=…`
> 等既有链接与二维码不受影响。门户源码见 [portal/index.html](portal/index.html)，
> 部署脚本见 `scripts/deploy-portal.py`。

> **地址为什么是 `8-147-61-0.sslip.io`**：裸 IP 签不出受信任证书，
> `sslip.io` 会把 `a-b-c-d.sslip.io` 解析回 `a.b.c.d`，因此不用买域名也能上 HTTPS。
> 而**扫码必须 HTTPS**——浏览器只在安全上下文下开放摄像头，HTTP 页面点扫码只能落到
> "从相册选图"。站点已把 80 全部 301 到 HTTPS，直接输 IP 也会自动跳过去，不用记域名。
> 证书与反代配置见 [docs/deploy.md 7.3.1](./docs/deploy.md#731-启用-https扫码功能的前提)。

## 八、文档索引

- [docs/README.md](docs/README.md) —— 项目详细说明与架构图
- [docs/deploy.md](docs/deploy.md) —— 部署操作手册（本地 / 云服务器 / 链节点）
- [docs/api.md](docs/api.md) —— 接口说明（入参、出参、权限）
- [docs/trust-score.md](docs/trust-score.md) —— 溯源可信度评分模型（AgriTrust-5D）的计分规则与设计取舍
- [docs/uni-app-migration.md](docs/uni-app-migration.md) —— uni-app 三端改造方案与已落地清单
- [docs/test.md](docs/test.md) —— 验收测试用例（正常流转、异常拦截、越权、重复提交、一致性）
- [docs/assets/README.md](docs/assets/README.md) —— 流程图、用例图、模拟质检报告与农产品图片素材
- [contracts/README.md](contracts/README.md) —— 合约设计与部署说明
- [docs/项目一核对报告.md](docs/项目一核对报告.md) —— 实训任务书逐条核对（需求→实现→证据）
- [docs/WeBASE-Front-HTTP调用实战.md](docs/WeBASE-Front-HTTP调用实战.md) —— 第二种链上接入方式：HTTP 调用实现（本项目实测）
- [docs/deploy.md §11.6](docs/deploy.md#116-webase-front-http-调用实战) —— 第二种链上接入方式：HTTP 调用实战

## 九、验收演示路径（建议 10 分钟）

1. 农户 H5 登录 → 提交农事记录（拍照）→ 提交采收 → 查看上链回执；
2. 村管 PC 审核 → 生产主体创建批次并绑定农户采收数据；
3. 检测机构上传质检报告（合格）→ 批次转为"质检合格"；
4. 物流企业登记发运 → 销售商登记销售 → 批次"已售出"；
5. 消费者 H5 扫码 → 时间轴溯源 + 链上凭证 + 一致性校验通过；
6. 另建批次质检"不合格" → 销售登记被拦截 → 监管处置 → 上下游追溯定位责任主体；
7. 用农户账号调用质检接口 → 403 越权拦截；重复提交采收 → 409 重复校验。

## 十、开发分工

| 角色 | 负责目录 | 说明 |
| --- | --- | --- |
| 后端 | `/backend`、`/sql` | 业务接口、权限、链交互封装 |
| 前端 | `/frontend` | PC 管理端 + H5 |
| 合约 | `/contracts` | 合约逻辑、编译部署 |
| 文档/测试 | `/docs` | 说明、部署、接口、测试用例 |

模块间仅通过 HTTP 接口与链上合约解耦，单模块修改不影响其他目录。

## 十一、License

[MIT](LICENSE) © 2026 agri-origin
