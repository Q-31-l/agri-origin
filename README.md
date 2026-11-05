# agri-origin · 基于 FISCO-BCOS 联盟链的农产品质量安全溯源系统

> 以"农产品批次"为主线，覆盖 **种植采收 → 质量检测 → 物流运输 → 终端销售** 全流程的联盟链溯源平台。
> 关键业务数据与文件摘要实时上链存证，支持扫码溯源、异常批次拦截、链上链下一致性校验与基层网格化管理。

![license](https://img.shields.io/badge/license-MIT-blue)
![java](https://img.shields.io/badge/Java-8+-orange)
![springboot](https://img.shields.io/badge/SpringBoot-2.7.18-green)
![vue](https://img.shields.io/badge/Vue-3.4-42b883)
![fisco](https://img.shields.io/badge/FISCO--BCOS-2.9.x-2f6feb)

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
| 前端 PC 管理端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router + ECharts + Axios |
| 前端 移动端 H5 | Vue 3 + Vite 5 + Vant 4 + Pinia + `vue-qrcode-reader`（扫码） |
| 前端 三端统一（已实现） | uni-app（Vue 3 + Vite 5 + Pinia）：**一套代码**产出 H5（PC + 移动自适应）/ 微信小程序 / Android·iOS App，共用同一后端与同一账号体系，方案与落地记录见 [docs/uni-app-migration.md](./docs/uni-app-migration.md) |
| 后端 | Spring Boot 2.7.18 + MyBatis-Plus 3.5 + JWT + Knife4j + Hutool |
| 数据库 | MySQL 8.0（utf8mb4） |
| 区块链 | FISCO-BCOS 2.9.x（Solidity 0.6.10）+ fisco-bcos-java-sdk 2.9.1 |
| 文件存储 | 本地磁盘（可切换 OSS/MinIO），文件 SHA-256 摘要上链 |

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

权限模型：**RBAC（角色-权限）+ 数据范围（本人 / 本村 / 本组织 / 全部）**，接口层通过 `@RequireRoles`、`@RequirePerm` 双注解校验，越权统一返回 `403 / 40301`。

## 四、核心功能

1. **认证与权限**：JWT 无状态登录、令牌过期续期、角色路由守卫、接口级越权拦截、操作审计日志。
2. **批次全生命周期**：批次唯一编码 `AG + yyyyMMdd + 3 位当日序号`（如 `AG20260512001`），种植/采收/质检/物流/销售逐环节登记，每环节生成 `dataHash` 上链。
3. **存证与更正**：关键记录上链返回 `txHash / blockNumber`；录入错误不物理删除，通过 **追加更正说明** 保留完整历史。
4. **扫码溯源**：批次唯一溯源码（二维码），消费者免登录查询，按时间轴展示流转履历、质检摘要、链上凭证，并支持一键校验。
5. **溯源可信度评分（AgriTrust-5D）**：把"有没有上链"的二值判断升级为 0–100 的可量化指标，从流程完整度、链上存证度、质量合规性、异常洁净度、信息完备度五个维度加权计分，并逐条输出扣分理由；按批次所处阶段动态调整考核环节，避免把"尚未发生"误判为"记录缺失"。模型说明见 [docs/trust-score.md](./docs/trust-score.md)。
6. **异常批次管控**：质检不合格 → 批次 `ABNORMAL` 状态 → 销售/物流环节前置拦截 → 处置记录上链 → 支持上游来源与下游流向双向追溯。
7. **一致性校验**：链下重算 SHA-256 与链上哈希比对 + 交易回执状态校验，校验失败明确提示，绝不显示"存证成功"。
8. **基层网格化**：村级管理员对辖区农户一对多管理，农户提交数据需村管审核后进入收购/质检环节。
9. **多端适配**：PC 管理端（数据看板、全流程后台）；H5（农户录入、拍照上传、消费者扫码）。

## 五、界面预览

以下截图取自本工程实际启动后的运行状态（`chain.enabled=false` 演示模式，数据来自 `sql/data.sql`）。

### 5.1 PC 管理端

**数据看板**：批次总量、异常批次、上链成功率、状态分布与近 7 日建档趋势。

![数据看板](docs/assets/screenshots/pc-02-dashboard.png)

**批次管理**：支持按批次号、状态、生产主体、建档时间区间组合筛选，表格内直接展示村级核验与存证状态。

![批次管理](docs/assets/screenshots/pc-03-batch-list.png)

**批次详情**：档案信息、溯源二维码、全流程时间轴（含投入品与操作人）。

![批次详情](docs/assets/screenshots/pc-04-batch-detail.png)

**异常管控**：不合格批次自动熔断，可追溯上下游并登记处置结果。

![异常管控](docs/assets/screenshots/pc-06-abnormal.png)

**链上存证**：全部上链流水与回执；演示模式下如实显示未上链，不伪造交易哈希。

![链上存证](docs/assets/screenshots/pc-07-chain.png)

### 5.2 移动端 H5

**消费者溯源**（免登录扫码）：产品档案、质检结论、链上存证校验、全流程时间轴。

<img src="docs/assets/screenshots/h5-05-trace.png" width="320" alt="消费者溯源" />

**农户工作台**：我的批次、农事记录、采收提交、扫码溯源四个入口。

<img src="docs/assets/screenshots/h5-02-home.png" width="300" alt="农户工作台" />
<img src="docs/assets/screenshots/h5-04-planting.png" width="300" alt="农事记录" />

> 全部 14 张截图见 [docs/assets/screenshots/](docs/assets/screenshots/)，含 PC 登录/质量检测/溯源查询/系统用户与 H5 登录/批次列表。

### 5.3 三端统一工程（uni-app · 已落地）

`frontend/uni-app` 把上面两套独立前端收敛为**一套代码**，三个平台产物来自同一份 `src/`，共用同一后端接口与同一账号体系（同一账号在三端看到完全同步的数据）：

| 端 | 开发 | 构建 | 产物目录 |
| --- | --- | --- | --- |
| PC / H5（浏览器） | `npm run dev:h5` | `npm run build:h5` | `dist/build/h5` |
| 微信小程序 | `npm run dev:mp-weixin` | `npm run build:mp-weixin` | `dist/build/mp-weixin`（导入微信开发者工具） |
| Android / iOS App | `npm run dev:app` | `npm run build:app`（HBuilderX 云打包） | `dist/build/app` |

H5 端宽度 ≥ 768px 自动进入 PC 管理台（14 个页面），窄屏自动进入移动工作台；小程序与 App 只打包各自需要的页面（构建产物 28 页，PC 页面完全不进包）。

**PC / 移动视图可手动互通切换**（不依赖窗口宽度）：PC 管理台顶栏有「📱 手机版」、移动端登录页与「我的」页有「🖥 切换到电脑版」，点击后在本地强制切换视图模式，方便同一台电脑上预览两端效果。

**数据看板**（与后端实时联动，看板数据随任意端写入自动刷新）

![uni-app 数据看板](docs/assets/screenshots/uni-pc/pc-dashboard.png)

**批次管理**：PC 端按下宽屏重排为 9 列表格，移动端为卡片流；筛选、分页、核验、审核同一套接口。

![uni-app 批次管理](docs/assets/screenshots/uni-pc/pc-batch-list.png)

**批次详情**：档案信息、溯源二维码、全流程时间轴、链上存证与一致性校验。

![uni-app 批次详情](docs/assets/screenshots/uni-pc/pc-batch-detail.png)

**链上存证**：链状态、区块高度、合约地址、存证流水与链上链下一致性校验。

![uni-app 链上存证](docs/assets/screenshots/uni-pc/pc-chain.png)

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

**角色差异**：菜单不是写死的，而是按登录用户的权限码实时裁剪，分组里一项不剩时连分组标题一起隐藏。同一套代码，系统管理员看到 14 项、农户 6 项、物流企业 4 项。

| 系统管理员（14 项） | 农户（6 项） | 物流企业（4 项） |
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
├── sql/                     # init.sql、data.sql、增量补丁（patch-wx-login / patch-optimistic-lock）
└── docs/                    # 项目说明、部署手册、测试用例、接口文档、uni-app 改造方案
```

## 七、快速启动

### 1. 环境要求

- JDK 8+、Maven 3.6+、Node.js 18+、MySQL 8.0+
- FISCO-BCOS 2.9.x 节点（可用 `build_chain.sh` 一键搭 4 节点单群组）

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
| farmer01 | 123456 | 农户 |
| village01 | 123456 | 村级管理员 |
| inspector01 | 123456 | 检测机构 |
| logistics01 | 123456 | 物流企业 |
| seller01 | 123456 | 销售商 |
| regulator01 | 123456 | 监管人员 |

## 八、文档索引

- [docs/README.md](docs/README.md) —— 项目详细说明与架构图
- [docs/deploy.md](docs/deploy.md) —— 部署操作手册（本地 / 云服务器 / 链节点）
- [docs/api.md](docs/api.md) —— 接口说明（入参、出参、权限）
- [docs/trust-score.md](docs/trust-score.md) —— 溯源可信度评分模型（AgriTrust-5D）的计分规则与设计取舍
- [docs/uni-app-migration.md](docs/uni-app-migration.md) —— uni-app 三端改造方案与已落地清单
- [docs/test.md](docs/test.md) —— 验收测试用例（正常流转、异常拦截、越权、重复提交、一致性）
- [docs/assets/README.md](docs/assets/README.md) —— 流程图、用例图、模拟质检报告与农产品图片素材
- [contracts/README.md](contracts/README.md) —— 合约设计与部署说明

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
