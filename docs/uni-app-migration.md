# agri-origin 前端 uni-app 三端改造方案

> 目标：把现有 `pc-admin`（Vue3 + Vite + Element Plus）与 `mobile-h5`（Vue3 + Vite + Vant）两套独立前端，收敛为一套 **uni-app（Vue3 + Vite）** 工程，同时产出 **H5 网页端 / 微信小程序端 / Android·iOS App 端**，三端共用 `backend` 的同一套 REST 接口与同一份账号体系。
>
> 本文档对应当前仓库实际代码（`frontend/pc-admin` 28 个源文件、`frontend/mobile-h5` 24 个源文件），所有迁移映射均按真实文件名给出，可直接照做。
>
> 版本 v1.0 · 编写时间 2026-11-05

---

## 目录

- [零、开工前的三个判断](#零开工前的三个判断)
- [一、项目目录结构重构方案](#一项目目录结构重构方案)
  - [1.1 仓库根目录总览](#11-仓库根目录总览)
  - [1.2 frontend/uni-app 完整目录树](#12-frontenduni-app-完整目录树)
  - [1.3 原有两套 Vue 项目的迁移映射](#13-原有两套-vue-项目的迁移映射)
  - [1.4 PC / 移动 / 小程序 / App 页面的存放与隔离规则](#14-pc--移动--小程序--app-页面的存放与隔离规则)
  - [1.5 五个核心配置文件逐项说明](#15-五个核心配置文件逐项说明)
  - [1.6 多环境配置统一管理](#16-多环境配置统一管理)
- [二、现有前端代码迁移与三端适配方案](#二现有前端代码迁移与三端适配方案)
  - [2.1 必改语法清单](#21-必改语法清单)
  - [2.2 Axios 拦截器 → uni.request 封装](#22-axios-拦截器--unirequest-封装)
  - [2.3 Pinia 的可用性与迁移](#23-pinia-的可用性与迁移)
  - [2.4 样式与组件库适配](#24-样式与组件库适配)
  - [2.5 端差异场景与条件编译](#25-端差异场景与条件编译)
  - [2.6 三端统一扫码封装](#26-三端统一扫码封装)
- [三、三端数据互通与后端对接方案](#三三端数据互通与后端对接方案)
  - [3.1 JWT 三端登录一致性](#31-jwt-三端登录一致性)
  - [3.2 微信小程序登录打通](#32-微信小程序登录打通)
  - [3.3 文件上传三端适配](#33-文件上传三端适配)
  - [3.4 多端数据一致性设计](#34-多端数据一致性设计)
  - [3.5 WebSocket 统一接入](#35-websocket-统一接入)
- [四、各端调试、打包与发布全流程](#四各端调试打包与发布全流程)
- [五、项目维护与问题排查规范](#五项目维护与问题排查规范)
- [附录 A：后端最小改造清单](#附录-a后端最小改造清单)
- [附录 B：迁移执行顺序（建议排期）](#附录-b迁移执行顺序建议排期)

---

## 零、开工前的三个判断

在动手建目录之前，有三个问题必须先定，否则后面会返工。

### 判断 1：uni-app 用 CLI 工程，不用 HBuilderX 默认工程

仓库现在没有 uni-app 目录。创建时选哪种形态，直接决定目录树长什么样：

| 形态 | 目录特征 | 产物路径 | 是否推荐 |
| --- | --- | --- | --- |
| HBuilderX 可视化工程 | 根目录直接是 `pages.json` / `pages/` / `unpackage/` | `unpackage/dist/build/h5` 等 | 上手快，但 git 里混入 IDE 元数据，无法接入现有 npm 工程化 |
| **CLI 工程（Vue3 + Vite）** | 源码全部收在 `src/` 下，根目录只有 `package.json` / `vite.config.js` | `dist/build/h5` 等 | **推荐**。与现有 `pc-admin` 的 Vite 工程习惯一致，可用 npm 生态、可用 CI |

创建命令（在 `frontend/` 下执行）：

```bash
cd frontend
npx degit dcloudio/uni-preset-vue#vite uni-app
cd uni-app
npm install
# 装业务依赖
npm i pinia wot-design-uni dayjs
npm i -D sass
```

> **版本纪律**：`package.json` 里 `@dcloudio/*` 的版本号形如 `3.0.0-4020920240930001`，这串后缀是编译器版本，**必须全家族一致**（`uni-app`、`uni-h5`、`uni-mp-weixin`、`uni-app-plus`、`vite-plugin-uni` 用同一串）。不要单独升级某一个。需要整体升级时用官方工具：`npx @dcloudio/uvm@latest`。

### 判断 2：Element Plus 不能进小程序和 App，PC 页面必须物理隔离

这是整个改造里最容易踩的坑，先说结论：

- Element Plus 的每一个组件都直接操作 DOM（`document`、`window`、`ResizeObserver`），**微信小程序和 App 的 vue 页面没有 DOM**，一旦引用就会在编译或运行期直接崩；
- 因此 **PC 管理端页面不能与移动页面共用同一个页面目录**，必须单独放 `src/pages-pc/`，并且整页用 `#ifdef H5` 包裹；
- 反过来，Element Plus **可以在 uni-app 的 H5 端跑**（H5 编译产物就是普通网页），所以在 PC 页面里保留 Element Plus 是可行的，但要注意它的全局样式重置会和 uni-app 的 `uni.scss` 打架，需要限定作用域。

落地策略（本文档采用）：

| 端 | 组件方案 | 页面目录 |
| --- | --- | --- |
| H5 宽屏（PC 管理端） | 保留 Element Plus（仅 `#ifdef H5` 内引用） | `src/pages-pc/` |
| H5 窄屏 + 小程序 + App | wot-design-uni / uni-ui | `src/pages/` |
| 纯原生能力页（分享海报、订阅消息、App 更新） | 原生 API + 自绘 | `src/pages-mp/`、`src/pages-app/` |

> 想彻底摆脱 Element Plus 也可以，但 10 个后台表格页要全部重写成 `scroll-view` 虚拟列表，工作量大约是本文档全部迁移工作的 40%，毕设阶段不划算。**保留、隔离、逐步替换**是更理性的路径。

### 判断 3：PC 端和移动端不是"两套页面"，是"两棵页面树 + 一个分发器"

uni-app 的 `pages.json` 第一个 `pages` 项就是启动页，三端共用。但 PC 要宽屏布局、移动要 tabBar，二者不可能共用一个首页。做法：

```
pages/launch/index.vue   ← 唯一启动页（三端都进这里）
        │
        ├─ H5 且屏宽 ≥ 900px  → uni.reLaunch('/pages-pc/dashboard/index')
        ├─ H5 且屏宽 < 900px  → uni.reLaunch('/pages/tabbar/home')
        ├─ 微信小程序          → uni.reLaunch('/pages/tabbar/home')
        └─ App                → uni.reLaunch('/pages/tabbar/home')
```

这样一个 `pages.json` 容纳两棵树，登录态、Pinia、请求封装全部共用，而布局互不干扰。`pages-pc/*` 在小程序构建时通过 `pages.json` 条件编译整段剔除，不会占用主包体积。

---

## 一、项目目录结构重构方案

### 1.1 仓库根目录总览

改造后仓库根目录：

```
agri-origin/
├── backend/                    # 【不动】Spring Boot 2.7 后端
│   ├── src/main/java/com/agri/trace/
│   │   ├── controller/         # REST 入口（三端共用，不区分端）
│   │   ├── service/            # 业务逻辑
│   │   ├── mapper/             # MyBatis-Plus
│   │   ├── entity/  dto/  vo/
│   │   ├── sdk/                # FISCO-BCOS 合约调用封装
│   │   ├── security/           # JWT、拦截器、@RequirePerm
│   │   ├── config/             # WebMvc / Jackson / Knife4j
│   │   └── common/             # R 统一响应、异常、枚举
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-prod.yml
│   └── pom.xml
├── contracts/                  # 【不动】Solidity 合约 + 编译产物 + 部署脚本
├── sql/                        # 【不动】schema.sql / data.sql
├── docs/                       # 【不动】设计文档 + 本次新增 uni-app 方案
│   └── uni-app-migration.md    # 本文档
├── frontend/                   # 【改造区】
│   ├── uni-app/                # ★ 新主体，三端唯一源码
│   ├── mobile-h5/              # 迁移完成后归档（打 tag 后删除）
│   └── pc-admin/               # 迁移完成后归档（打 tag 后删除）
├── .gitignore
├── LICENSE
└── README.md
```

三个后端相关目录（`backend`、`contracts`、`sql`）**一行都不用改**就能支撑三端——这是当前架构最大的优势，因为所有业务逻辑都在服务端，前端只是渲染层。真正需要动的后端代码只有 §3.2 的微信登录和 §3.3 的上传兼容，属于新增而非修改。

`frontend/pc-admin` 和 `frontend/mobile-h5` 的处理节奏：

1. 迁移期间**保留**，作为对照实现（页面细节、交互文案、样式数值都可以照抄）；
2. 全部页面上线验证通过后，打 tag `legacy-frontend`，然后 `git rm -r` 删除；
3. 不要一上来就删——迁移过程中一定会遇到"这个页面原来那个圆角是多少"的问题，删了就得翻 git 历史。

### 1.2 frontend/uni-app 完整目录树

下面是落地后的完整结构，**每一级都标注了职责与存放内容**。这是本文档的核心，后续所有章节都围绕它展开。

```
frontend/uni-app/
│
├── package.json                     # 依赖与脚本：dev:h5 / dev:mp-weixin / dev:app / build:*
├── vite.config.js                   # Vite 配置：uni 插件、别名、H5 代理、SCSS 全局注入
├── index.html                       # H5 端 HTML 模板（改 title / viewport / 统计脚本）
├── jsconfig.json                    # 编辑器路径提示（@ → src），不参与编译
├── .env                             # 三端公共变量（VITE_APP_NAME 等）
├── .env.development                 # 开发环境：接口地址、链开关、调试开关
├── .env.staging                     # 预发环境（体验版小程序用）
├── .env.production                  # 生产环境
├── .gitignore
│
└── src/
    │
    ├── main.js                      # ★ 应用入口：createSSRApp，注册 Pinia、全局组件、全局指令
    ├── App.vue                      # ★ 应用根组件：onLaunch / onShow / onHide、全局登录态检查、版本更新
    ├── pages.json                   # ★ 页面路由表 + 全局样式 + tabBar + 分包 + 条件编译
    ├── manifest.json                # ★ 三端身份与能力声明：AppID、包名、权限、SDK、域名
    ├── uni.scss                     # ★ 全局 SCSS 变量（尺寸、颜色、圆角），自动注入到每个组件
    │
    ├── config/                      # ── 配置层：所有"改一下就能变"的东西都在这里
    │   ├── env.js                   # 环境变量出口（唯一读 import.meta.env 的地方）
    │   ├── constants.js             # 业务常量：批次状态、角色码、权限码、字典映射
    │   ├── routes.js                # 路由常量表（避免页面路径字符串散落各处）
    │   └── platform.js              # 平台判定：isH5 / isMp / isApp / isPc / 设备信息
    │
    ├── api/                         # ── 接口层：只描述"请求什么"，不关心怎么发
    │   ├── index.js                 # 统一出口，页面只 import 这里
    │   └── modules/
    │       ├── auth.js              # 登录 / 资料 / 改密 / 微信绑定
    │       ├── batch.js             # 批次 CRUD、审核、链上校验
    │       ├── planting.js          # 农事记录
    │       ├── harvest.js           # 采收
    │       ├── quality.js           # 质检
    │       ├── logistics.js         # 物流
    │       ├── sales.js             # 销售
    │       ├── abnormal.js          # 异常处置
    │       ├── chain.js             # 链上存证查询
    │       ├── trace.js             # 公开溯源（免登录）
    │       ├── file.js              # 上传 / 删除
    │       ├── stats.js             # 看板统计
    │       ├── user.js              # 用户与角色
    │       └── base.js              # 农场 / 产品 / 村庄 / 字典 / 日志
    │
    ├── utils/                       # ── 工具层：与业务无关的通用能力
    │   ├── request.js               # ★ uni.request 封装：Token、错误码、401、加载、去重、重试
    │   ├── upload.js                # ★ uni.uploadFile 封装：进度、多文件、失败重试
    │   ├── download.js              # 文件下载与预览（报告 PDF）
    │   ├── storage.js               # 存储封装（带前缀、TTL、JSON 安全解析）
    │   ├── auth.js                  # Token 读写与清理（唯一操作 token 的地方）
    │   ├── router.js                # 路由封装：navigateTo 参数序列化、tab 页跳转、防重复点击
    │   ├── scan.js                  # ★ 三端统一扫码（小程序码 / 二维码 / 条形码）
    │   ├── socket.js                # ★ WebSocket 封装：心跳、重连、消息分发
    │   ├── ui.js                    # toast / loading / confirm / 震动，屏蔽三端差异
    │   ├── format.js                # 日期、金额、状态文案、手机号脱敏
    │   ├── validator.js             # 表单校验规则
    │   ├── permission.js            # 前端权限判断（与后端 @RequirePerm 对齐）
    │   └── errors.js                # 错误码 → 中文提示映射表
    │
    ├── store/                       # ── 状态层：跨页面共享的运行时状态
    │   ├── index.js                 # createPinia 实例 + 持久化插件装配
    │   ├── user.js                  # 登录态、用户信息、权限码、角色
    │   ├── app.js                   # 全局 UI 状态：屏幕尺寸、网络、主题、tab 索引
    │   └── cache.js                 # 列表缓存与失效标记（多端一致性用）
    │
    ├── components/                  # ── 公共组件：至少两个页面用到才放这里
    │   ├── common/                  # 与业务无关的壳组件
    │   │   ├── app-navbar.vue       # 自定义导航栏（适配刘海屏、返回、标题、右侧插槽）
    │   │   ├── empty-state.vue      # 空状态占位
    │   │   ├── load-more.vue        # 上拉加载状态（loading / noMore / error）
    │   │   ├── search-bar.vue       # 搜索框（带防抖、清空）
    │   │   ├── status-tag.vue       # 状态标签（迁移自 pc-admin/StatusTag.vue）
    │   │   ├── stat-card.vue         # 统计卡片（迁移自 pc-admin/StatCard.vue）
    │   │   ├── chain-badge.vue      # 链上存证徽标（迁移自 pc-admin/ChainBadge.vue）
    │   │   ├── photo-uploader.vue   # 拍照/相册上传（替代 van-uploader）
    │   │   ├── safe-area.vue        # 底部安全区占位（iPhone 刘海屏）
    │   │   └── section-card.vue     # 通用卡片容器
    │   └── business/                # 与溯源业务强绑定的组件
    │       ├── batch-card.vue       # 批次列表项
    │       ├── trace-timeline.vue   # 溯源时间轴（种植→采收→检测→运输→销售）
    │       ├── trace-node.vue       # 时间轴单个节点
    │       ├── quality-report.vue   # 质检报告卡片 + PDF 预览
    │       ├── qr-code.vue          # 二维码渲染（替代 qrcode 库直调）
    │       ├── batch-picker.vue     # 批次选择器（表单里反复出现）
    │       └── perm-guard.vue       # 权限包裹组件，无权限时渲染插槽 fallback
    │
    ├── layouts/                     # ── 布局壳：只负责"框"，不含业务
    │   ├── pc-shell.vue             # PC 宽屏壳：左侧菜单 + 顶部栏 + 内容区
    │   ├── pc-table.vue             # PC 表格壳：工具栏 + 表格 + 分页（替代 el-table 重复代码）
    │   └── mobile-shell.vue         # 移动壳：自定义导航 + 内容区（tabBar 用原生，不在这里）
    │
    ├── pages/                       # ── ★ 三端通用页面（移动优先，H5窄屏/小程序/App 共用）
    │   ├── launch/index.vue         # 启动分发器（见 §1.4）
    │   ├── auth/
    │   │   ├── login.vue            # 登录（账号密码，三端通用）
    │   │   ├── wx-bind.vue          # 小程序微信登录后的账号绑定（仅 MP 可达）
    │   │   └── password.vue         # 修改密码
    │   ├── tabbar/                  # 必须是 tabBar 页面，路径要与 pages.json 一致
    │   │   ├── home.vue             # 工作台
    │   │   ├── batch.vue            # 我的批次
    │   │   ├── trace.vue            # 扫码溯源入口
    │   │   └── mine.vue             # 我的
    │   ├── batch/
    │   │   ├── list.vue             # 批次列表（带筛选：主体/时间/状态）
    │   │   ├── detail.vue           # 批次详情（全流程时间轴 + 存证校验）
    │   │   ├── create.vue           # 新建批次
    │   │   └── edit.vue             # 编辑批次
    │   ├── planting/
    │   │   ├── list.vue             # 农事记录列表
    │   │   └── form.vue             # 新增农事（拍照 + GPS）
    │   ├── harvest/form.vue         # 采收登记
    │   ├── quality/
    │   │   ├── list.vue             # 质检列表
    │   │   └── form.vue             # 质检录入（报告上传）
    │   ├── logistics/form.vue       # 物流登记
    │   ├── sales/form.vue           # 销售登记
    │   ├── abnormal/index.vue       # 异常管控与处置
    │   ├── chain/index.vue          # 链上存证列表与校验
    │   ├── trace/index.vue          # ★ 公开溯源页（消费者扫码落地页，免登录）
    │   └── common/
    │       ├── 403.vue              # 无权限
    │       ├── 404.vue              # 页面不存在
    │       ├── webview.vue          # 外链容器（小程序用 web-view，App 用 plus）
    │       └── settings.vue         # 设置（清理缓存、关于、退出）
    │
    ├── pages-pc/                    # ── ★ PC 宽屏专属页面（整页 #ifdef H5，小程序构建时剔除）
    │   ├── dashboard/index.vue      # 数据看板
    │   ├── batch/list.vue
    │   ├── batch/detail.vue
    │   ├── planting/index.vue
    │   ├── harvest/index.vue
    │   ├── quality/index.vue
    │   ├── logistics/index.vue
    │   ├── sales/index.vue
    │   ├── abnormal/index.vue
    │   ├── chain/index.vue
    │   ├── trace/index.vue
    │   └── system/
    │       ├── user.vue
    │       ├── farm.vue
    │       ├── product.vue
    │       └── log.vue
    │
    ├── pages-mp/                    # ── ★ 微信小程序专属页面（#ifdef MP-WEIXIN）
    │   ├── poster/index.vue         # 分享海报（canvas 绘制 + 保存相册）
    │   ├── subscribe/index.vue      # 订阅消息授权引导
    │   └── privacy/index.vue        # 隐私协议授权页（2023 后强制）
    │
    ├── pages-app/                   # ── ★ App 专属页面（#ifdef APP-PLUS）
    │   ├── update/index.vue         # 版本检测与安装
    │   ├── permission/index.vue     # 权限申请引导（相机/相册/定位）
    │   └── about/index.vue          # 关于与备案信息
    │
    ├── pages-sub/                   # ── 小程序分包（主包 2MB 限制的解药）
    │   ├── system/                  # 低频管理页
    │   │   ├── user.vue
    │   │   ├── farm.vue
    │   │   └── product.vue
    │   └── static/                  # 分包专用静态资源（图片随分包走）
    │
    ├── static/                      # ── 静态资源（会被完整打进各端包体）
    │   ├── tabbar/                  # tabBar 图标：必须是本地 png，81×81
    │   │   ├── home.png  home-active.png
    │   │   ├── batch.png batch-active.png
    │   │   ├── trace.png trace-active.png
    │   │   └── mine.png  mine-active.png
    │   ├── images/
    │   │   ├── logo.png
    │   │   ├── empty.png            # 空状态插画
    │   │   ├── placeholder.png      # 图片加载占位
    │   │   └── banner-home.png
    │   └── icons/                   # 小图标（能用字体图标就别放图）
    │
    ├── static-pc/                   # ── PC 端私有静态资源（仅 H5 构建时复制）
    │   └── favicon.ico
    │
    ├── styles/                      # ── 全局样式（与 uni.scss 分工：变量 vs 规则）
    │   ├── common.scss              # 通用类：flex、间距、文字截断、卡片
    │   ├── reset.scss               # 跨端标签差异修正
    │   ├── theme.scss               # 主题变量（主色、状态色）
    │   ├── pc.scss                  # PC 专属样式（媒体查询 ≥900px 时生效）
    │   └── mp.scss                  # 小程序专属样式修正
    │
    ├── composables/                 # ── 组合式函数：把页面里的重复逻辑抽出来
    │   ├── useList.js               # 列表页通用：分页、下拉刷新、上拉加载、搜索、筛选
    │   ├── useForm.js               # 表单通用：校验、提交、防重复、脏检查
    │   ├── usePermission.js         # 权限判断 + 无权跳转
    │   ├── useNetwork.js            # 网络状态监听与离线提示
    │   ├── useSafeArea.js           # 安全区尺寸
    │   └── useShare.js              # 分享（三端差异化，内部条件编译）
    │
    ├── directives/                  # 全局指令
    │   ├── perm.js                  # v-perm="'batch:create'" 无权限移除节点
    │   └── debounce.js              # v-debounce
    │
    ├── wxcomponents/                # 小程序原生组件（如 ec-canvas 图表），仅 MP 打包
    │
    └── hybrid/                      # App 端本地 HTML（如地图、富文本编辑器），仅 APP 打包
```

**为什么这么分（一眼定位原则）**

| 你要找什么 | 去哪里 | 记忆规则 |
| --- | --- | --- |
| 某个页面的界面与交互 | `src/pages/**` 或 `src/pages-pc/**` | 按业务域分目录，目录名 = 后端 Controller 名 |
| 某个按钮/卡片的样式细节 | `src/components/common\|business/` | 组件名即用途，全部 kebab-case |
| 某个接口的 URL 与入参 | `src/api/modules/xxx.js` | 一个后端 Controller 对应一个文件 |
| 请求为什么带不上 Token | `src/utils/request.js` → `src/utils/auth.js` | 请求问题只看这两个文件 |
| 某个提示文案从哪来 | `src/utils/errors.js` 或 `src/config/constants.js` | 文案集中，不散落在页面 |
| 接口地址 / 链开关 | `src/config/env.js` ← `.env.*` | 只此一处读环境变量 |
| 页面为什么跳不过去 | `src/pages.json` + `src/utils/router.js` | 先查路由是否注册，再查跳转 API |
| 某端独有的怪异表现 | 搜 `#ifdef` 关键字 | 所有端差异都必须带条件编译注释 |

### 1.3 原有两套 Vue 项目的迁移映射

现有代码 52 个文件，按下表逐条处理。**"复用"= 复制后小改；"重写"= 结构保留、内部实现换掉；"废弃"= 不迁移**。

#### pc-admin（28 个文件）

| 原文件 | 目标位置 | 处理方式 | 说明 |
| --- | --- | --- | --- |
| `index.html` | `index.html` | 复用 | 只改 `<title>` 与 viewport（`viewport-fit=cover`） |
| `package.json` | `package.json` | 重写 | 依赖换成 `@dcloudio/*` + `pinia` + `wot-design-uni`，脚本换成 `dev:h5` 等 |
| `vite.config.js` | `vite.config.js` | 重写 | 加 `uni()` 插件；`@` 别名保留；H5 `server.proxy` 保留 |
| `src/main.js` | `src/main.js` | 重写 | `createApp` → `createSSRApp`，且必须 `return { app }` |
| `src/App.vue` | `src/App.vue` | 重写 | 去掉 `<router-view>`，改用 `onLaunch/onShow/onHide` |
| `src/api/index.js` | `src/api/modules/*.js`（14 个文件） | 拆分复用 | 按注释块拆成 auth/batch/planting/... 一 Controller 一文件，函数体几乎不改 |
| `src/api/request.js` | `src/utils/request.js` | 重写 | axios → uni.request，见 §2.2 |
| `src/store/user.js` | `src/store/user.js` | 复用 + 改存储 | `localStorage` → `uni.getStorageSync`，其余逻辑（hasPerm/login/logout）原样保留 |
| `src/router/index.js` | `src/pages.json` + `src/utils/router.js` + `src/config/routes.js` | 拆解 | 路由表 → pages.json；守卫 → `App.vue onLaunch` + `composables/usePermission` |
| `src/layout/index.vue` | `src/layouts/pc-shell.vue` | 重写 | `el-menu` 保留（H5 内可用），菜单过滤逻辑（`visibleMenus`）原样搬过来 |
| `src/components/StatusTag.vue` | `src/components/common/status-tag.vue` | 重写 | `el-tag` → 自绘 `view` + 状态色映射（§2.4） |
| `src/components/StatCard.vue` | `src/components/common/stat-card.vue` | 重写 | 纯展示，`div` → `view`，CSS 基本不动 |
| `src/components/ChainBadge.vue` | `src/components/common/chain-badge.vue` | 重写 | 同上，注意 `SKIPPED` 态的文案保留 |
| `src/styles/index.css` | `src/styles/common.scss` | 复用 | 改后缀为 scss，`px` → `rpx`（§2.4） |
| `src/views/dashboard/index.vue` | `src/pages-pc/dashboard/index.vue` | 重写 | echarts 不能直接用（无 DOM），改用 `<canvas>` + `uni.createCanvasContext` 或 H5 内条件编译 echarts |
| `src/views/batch/list.vue` | `src/pages-pc/batch/list.vue` | 重写 | `el-table` → `pc-table.vue` 壳 + `el-table`（仅 H5） |
| `src/views/batch/detail.vue` | `src/pages-pc/batch/detail.vue` | 重写 | 结构复杂，建议先做移动版 `pages/batch/detail.vue`，PC 复用同一份数据组装逻辑 |
| `src/views/planting/index.vue` | `src/pages-pc/planting/index.vue` | 重写 | 同上 |
| `src/views/harvest/index.vue` | `src/pages-pc/harvest/index.vue` | 重写 | 同上 |
| `src/views/quality/index.vue` | `src/pages-pc/quality/index.vue` | 重写 | 同上 |
| `src/views/logistics/index.vue` | `src/pages-pc/logistics/index.vue` | 重写 | 同上 |
| `src/views/sales/index.vue` | `src/pages-pc/sales/index.vue` | 重写 | 同上 |
| `src/views/abnormal/index.vue` | `src/pages-pc/abnormal/index.vue` | 重写 | 处置弹窗逻辑可整段复用 |
| `src/views/chain/index.vue` | `src/pages-pc/chain/index.vue` | 重写 | 同上 |
| `src/views/trace/index.vue` | `src/pages-pc/trace/index.vue` + `src/pages/trace/index.vue` | 拆两版 | PC 宽屏版与移动版共用 `trace-timeline.vue` 组件 |
| `src/views/system/user.vue` | `src/pages-pc/system/user.vue` | 重写 | 表格 + 弹窗表单，`el-dialog` → 页面内抽屉（`scroll-view`） |
| `src/views/system/farm.vue` | `src/pages-pc/system/farm.vue` | 重写 | 同上 |
| `src/views/system/product.vue` | `src/pages-pc/system/product.vue` | 重写 | 同上 |
| `src/views/system/log.vue` | `src/pages-pc/system/log.vue` | 重写 | 同上 |
| `src/views/error/403.vue` | `src/pages/common/403.vue` | 重写 | uni-app 无路由级 403，改为无权时主动 `navigateTo` |
| `src/views/error/404.vue` | `src/pages/common/404.vue` | 重写 | uni-app 无通配路由，改为 `onLoad` 校验参数非法时跳转 |

#### mobile-h5（24 个文件）

| 原文件 | 目标位置 | 处理方式 | 说明 |
| --- | --- | --- | --- |
| `index.html` | 合并到根 `index.html` | 废弃 | uni-app 只有一个 H5 模板 |
| `package.json` | 合并 | 废弃 | 依赖并入 uni-app |
| `vite.config.js` | 合并到根 `vite.config.js` | 废弃 | 同上 |
| `src/main.js` | `src/main.js` | 废弃 | 用统一的 |
| `src/App.vue` | `src/App.vue` | 废弃 | 用统一的 |
| `src/api/index.js` | 与 pc-admin 合并到 `src/api/modules/*.js` | 复用 | 两套 API 函数重复度 90%，取并集即可 |
| `src/api/request.js` | `src/utils/request.js` | 重写 | 注意 H5 版用的是 `agri_h5_token`，统一成 `agri_token` |
| `src/store/user.js` | `src/store/user.js` | 复用 + 改存储 | 同上 |
| `src/router/index.js` | `src/pages.json` | 废弃 | 15 条路由改写为 pages.json 节点 |
| `src/layout/index.vue` | 删除 | 废弃 | `van-tabbar` 由 `pages.json` 原生 tabBar 替代（性能更好） |
| `src/pages/home.vue` | `src/pages/tabbar/home.vue` | 复用 | 九宫格入口改 `uni.navigateTo` |
| `src/pages/batches.vue` | `src/pages/batch/list.vue` | 复用 | `van-list` → `scroll-view` + `load-more.vue` |
| `src/pages/batch-detail.vue` | `src/pages/batch/detail.vue` | 复用 | 700 行的大文件，重点改 `van-popup`、`van-tag`、`van-icon` |
| `src/pages/planting.vue` | `src/pages/planting/list.vue` | 复用 | |
| `src/pages/planting-form.vue` | `src/pages/planting/form.vue` | 复用 + 重写上传 | `van-uploader` → `photo-uploader.vue`（§3.3） |
| `src/pages/harvest.vue` | `src/pages/harvest/form.vue` | 复用 + 重写上传 | 同上 |
| `src/pages/trace.vue` | `src/pages/trace/index.vue` | 复用 | 时间轴部分是核心资产，`van-steps` → 自绘 `trace-timeline.vue` |
| `src/pages/login.vue` | `src/pages/auth/login.vue` | 复用 | `van-field` → `wd-input`，样式可整段搬 |
| `src/pages/mine.vue` | `src/pages/tabbar/mine.vue` | 复用 | |
| `src/styles/index.css` | `src/styles/common.scss` | 复用 | px → rpx |

**复用率评估**：模板结构（HTML 骨架）复用约 70%，`<script setup>` 里的数据组装、格式化、校验逻辑复用约 85%，样式复用约 60%，路由与请求层 0%（必须重写）。也就是说**真正要重写的只有"标签 + 路由 + 请求 + 端 API"四件事**，业务逻辑基本是搬。

### 1.4 PC / 移动 / 小程序 / App 页面的存放与隔离规则

四条硬规则，团队里任何人加页面都必须遵守：

**规则一：按"谁能打开"分目录，不按"功能"分目录**

```
pages/        三端都能打开（移动布局）
pages-pc/     只有 H5 宽屏能打开
pages-mp/     只有微信小程序能打开
pages-app/    只有 App 能打开
pages-sub/    只有小程序能打开，且必须分包（低频页面）
```

不要出现 `pages/pc-batch-list.vue` 这种命名——目录即平台，文件名只描述业务。

**规则二：页面目录镜像后端 Controller**

```
后端 BatchController        →  pages/batch/ + pages-pc/batch/
后端 PlantingController     →  pages/planting/ + pages-pc/planting/
后端 SystemUserController   →  pages-pc/system/user.vue
```

排查时从接口报错能直接反推页面目录，这是"精准定位"最有效的手段。

**规则三：端差异往下沉，页面里不写 `if (isMp) {...}`**

允许写条件编译的地方只有三处：`utils/`、`components/`、`pages-mp|pages-app/`。**`pages/` 里的通用页面禁止出现平台判断**，需要差异就把差异封进组件或 utils，页面只调统一 API。

**规则四：`pages.json` 用条件编译分段，PC 路由不污染小程序**

```jsonc
{
  "pages": [
    { "path": "pages/launch/index", "style": { "navigationStyle": "custom" } },
    // #ifdef H5
    { "path": "pages-pc/dashboard/index", "style": { "navigationStyle": "custom" } },
    { "path": "pages-pc/batch/list", "style": { "navigationStyle": "custom" } },
    // #endif
    { "path": "pages/tabbar/home", "style": { "navigationBarTitleText": "工作台" } }
    // ... 其余通用页
  ]
}
```

> `pages.json` 支持以 `// #ifdef` 注释形式做条件编译（HBuilderX 与 CLI 均支持）。如果你的 CLI 版本对该特性支持不完整（表现为小程序构建时报"页面 pages-pc/xxx 不存在"），兜底方案是在 `vite.config.js` 里挂一个 `buildStart` 钩子，按 `process.env.UNI_PLATFORM` 生成 `pages.json`：源码改用 `pages.config.js` 导出完整配置，构建前按平台裁剪后写入 `src/pages.json`。
>
> ```js
> // vite.config.js（兜底方案，仅在条件编译失效时才需要）
> import fs from 'node:fs'
> import path from 'node:path'
>
> function pagesGenerator() {
>   return {
>     name: 'gen-pages-json',
>     buildStart() {
>       const platform = process.env.UNI_PLATFORM || 'h5'
>       const cfgPath = path.resolve(__dirname, 'src/pages.config.js')
>       if (!fs.existsSync(cfgPath)) return
>       delete require.cache[cfgPath]
>       const cfg = require(cfgPath).default(platform)
>       fs.writeFileSync(
>         path.resolve(__dirname, 'src/pages.json'),
>         JSON.stringify(cfg, null, 2),
>         'utf8'
>       )
>     }
>   }
> }
> ```

**启动分发器的实现**（`src/pages/launch/index.vue`）：

```vue
<template>
  <view class="launch">
    <image src="/static/images/logo.png" class="logo" mode="aspectFit" />
    <text class="tip">农安链溯源</text>
  </view>
</template>

<script setup>
import { onLoad } from '@dcloudio/uni-app'
import { getToken } from '@/utils/auth'
import { isPcWidth } from '@/config/platform'

onLoad(() => {
  const hasToken = !!getToken()
  // #ifdef H5
  if (isPcWidth()) {
    return uni.reLaunch({ url: hasToken ? '/pages-pc/dashboard/index' : '/pages/auth/login' })
  }
  // #endif
  uni.reLaunch({ url: hasToken ? '/pages/tabbar/home' : '/pages/auth/login' })
})
</script>
```

`isPcWidth()` 放在 `config/platform.js`：

```js
export function isPcWidth() {
  // #ifdef H5
  return window.innerWidth >= 900
  // #endif
  // #ifndef H5
  return false
  // #endif
}
```

### 1.5 五个核心配置文件逐项说明

#### 1.5.1 `pages.json` —— 路由表 + 全局窗口 + tabBar + 分包

它替代了原 `vue-router`，**所有页面必须在这里注册**，否则 `navigateTo` 直接失败。每个配置项解决的问题：

| 配置项 | 作用 | 本项目的具体值 / 注意点 |
| --- | --- | --- |
| `pages[0]` | **启动页**，数组第一项就是应用首页 | 必须是 `pages/launch/index`，由它做三端分发 |
| `pages[].path` | 页面路径，**不带 `.vue` 后缀，不带开头 `/`** | `pages/batch/detail`；写成 `/pages/batch/detail` 会报错 |
| `pages[].style.navigationStyle` | `default` 用系统导航栏，`custom` 用自绘 | 登录页、详情页用 `custom`（要沉浸式头部） |
| `pages[].style.navigationBarTitleText` | 导航栏标题 | H5 端同时会写进 `document.title` |
| `pages[].style.enablePullDownRefresh` | 是否开启下拉刷新 | 列表页 `true`，配合页面内 `onPullDownRefresh` |
| `pages[].style.app-plus.titleNView` | App 端原生导航栏按钮（分享、菜单） | 详情页加"分享"按钮 |
| `pages[].style.h5.titleNView` | H5 端隐藏导航栏 | 与 `navigationStyle:custom` 配合 |
| `globalStyle.navigationBarBackgroundColor` | 导航栏背景 | `#1f7a4d`（主题绿） |
| `globalStyle.backgroundColor` | 页面背景（下拉回弹露出的颜色） | `#f5f7f9` |
| `globalStyle.rpxCalcMaxDeviceWidth` | **rpx 计算的最大设备宽度**，超过则按此值换算 | `960`。不加这个，PC 上 750rpx 会变成半个屏幕宽 |
| `globalStyle.rpxCalcBaseDeviceWidth` | rpx 换算基准宽度 | `375` |
| `tabBar` | 原生底部导航，**小程序最多 5 个、H5/App 也是 5 个** | 4 个：工作台/批次/溯源/我的 |
| `tabBar.list[].iconPath` | 图标路径，**必须是本地 png，不支持网络图、不支持字体图标** | `static/tabbar/home.png`，建议 81×81 |
| `tabBar.list[].pagePath` | 必须是 `pages` 里已注册的页面，且**不能带参数** | `pages/tabbar/home` |
| `tabBar.midButton` | 中间凸起按钮（仅 App/H5 支持，小程序不支持） | 不用，避免端差异 |
| `easycom` | 组件自动按需引入，免写 `import` | 配 `wot-design-uni` 规则，见下 |
| `subPackages` | 小程序分包，主包必须 ≤ 2MB | `pages-sub` 放低频管理页 |
| `preloadRule` | 进入某页时预下载分包 | 进入 `tabbar/home` 时预载 `pages-sub` |
| `condition` | **小程序开发者工具的编译模式**，可直接启动到指定页面带参数 | 配 3 个：批次详情、溯源页、农事表单，调试时省去手点 |
| `uniIdRouter` | 未登录自动跳登录页（uni-id 体系） | 本项目用自写守卫，不用它 |

关键片段（`easycom` + `subPackages` + `condition`）：

```jsonc
{
  "easycom": {
    "autoscan": true,
    "custom": {
      "^wd-(.*)": "wot-design-uni/components/wd-$1/wd-$1.vue"
    }
  },
  // 小程序分包：主包只放 tabBar 页与高频业务页，低频管理页丢进分包
  "subPackages": [
    {
      "root": "pages-sub",
      "pages": [
        { "path": "system/user", "style": { "navigationBarTitleText": "用户与角色" } },
        { "path": "system/farm", "style": { "navigationBarTitleText": "农场档案" } },
        { "path": "system/product", "style": { "navigationBarTitleText": "产品品类" } }
      ]
    }
  ],
  // 进入首页时后台预下载分包，点进去不转圈
  "preloadRule": {
    "pages/tabbar/home": { "network": "all", "packages": ["pages-sub"] }
  },
  "condition": {
    "current": 0,
    "list": [
      { "name": "批次详情", "path": "pages/batch/detail", "query": "id=1" },
      { "name": "溯源页", "path": "pages/trace/index", "query": "code=AGRI20241101001" },
      { "name": "农事表单", "path": "pages/planting/form", "query": "batchId=1" }
    ]
  }
}
```

> 分包注意三点：
> 1. `subPackages[].root` 是相对 `src` 的目录名，**该目录下必须有 `pages` 里声明的文件**，否则编译报错；
> 2. 分包页面跳转路径要带 root：`uni.navigateTo({ url: '/pages-sub/system/user' })`；
> 3. 分包里的静态资源要放在分包目录下（`pages-sub/static/`），放 `static/` 会打进主包。

#### 1.5.2 `manifest.json` —— 三端身份与能力声明

这是**唯一区分三端的地方**，也是最容易配错的地方。

| 配置项 | 解决什么问题 | 必填值 / 注意 |
| --- | --- | --- |
| `name` | 应用名称（打包后显示） | `农安链溯源` |
| `appid` | **DCloud AppID**，云打包、App 唯一标识 | `__UNI__XXXXXXX`，HBuilderX 登录后自动生成；CLI 项目需手动填 |
| `versionName` / `versionCode` | 版本号，`versionCode` 必须递增否则应用市场拒绝 | `1.0.0` / `100` |
| `app-plus.distribute.android.packageName` | Android 包名，**上架后不可改** | `com.agri.trace` |
| `app-plus.distribute.android.permissions` | Android 权限声明 | 相机、存储、定位、网络，见 §4.4 |
| `app-plus.distribute.android.abiFilters` | 打包的 CPU 架构 | `["armeabi-v7a","arm64-v8a"]`，加 `x86` 会让包大 20MB |
| `app-plus.distribute.ios` | iOS 证书与隐私描述 | `privacyDescription` 必填，否则审核被拒 |
| `app-plus.modules` | 需加载的原生模块 | `Camera`、`Barcode`、`Geolocation`、`Share` |
| `app-plus.splashscreen` | 启动图 | 关闭自动启动图避免白屏闪烁 |
| `mp-weixin.appid` | **微信小程序 AppID**，没有它无法真机预览 | `wx` 开头 18 位 |
| `mp-weixin.setting.urlCheck` | 是否校验合法域名，**开发期设 false 免配域名** | 生产必须 `true`，否则审核不过 |
| `mp-weixin.setting.es6` / `minified` | 是否转 ES5 / 压缩 | 都 `true` |
| `mp-weixin.usingComponents` | 全局注册小程序原生组件 | 一般不用 |
| `mp-weixin.permission` | 位置等敏感权限的用途说明 | `scope.userLocation` 必须写 `desc` |
| `mp-weixin.requiredPrivateInfos` | 声明使用的隐私接口 | `["chooseLocation","getLocation"]` |
| `h5.router.mode` | H5 路由模式 | `history`（美观）或 `hash`（免 nginx 配置），见 §4.2 |
| `h5.router.base` | 部署到二级目录时的基路径 | `/agri/` |
| `h5.title` | 页面标题 | `农安链溯源` |
| `h5.devServer` | H5 开发代理 | **Vue3+Vite 下改用 `vite.config.js` 的 `server.proxy`**，此处不生效 |
| `h5.optimization.treeShaking` | 摇树优化 | `true` |

#### 1.5.3 `App.vue` —— 应用生命周期与全局守卫

原项目的路由守卫（`router.beforeEach`）在 uni-app 里没有 vue-router，必须改由 `App.vue` 的 `onLaunch` 承担：

| 原逻辑 | uni-app 落点 |
| --- | --- |
| `if (!store.token) next('/login')` | `onLaunch` 里判断 + 各页面 `onLoad` 用 `usePermission().requireLogin()` |
| `if (!store.hasPerm(perm)) next('/403')` | 页面 `onLoad` 里调 `requirePerm('batch:list')` |
| `document.title = ...` | `uni.setNavigationBarTitle({ title })`，H5 端自动同步 `document.title` |
| `scrollBehavior: { top: 0 }` | `pages.json` 里每页 `"scrollTop": 0`，或 `uni.pageScrollTo({ scrollTop: 0 })` |
| 全局错误处理 | `onError` 钩子 + `onUnhandledRejection` |

`App.vue` 骨架：

```vue
<script>
export default {
  onLaunch() {
    // 1. 恢复登录态
    const store = useUserStore()
    store.restore()
    // 2. 记录启动来源（小程序扫码进入时 query 在 onLoad 里）
    // 3. #ifdef APP-PLUS 检查更新
    // #ifdef APP-PLUS
    checkAppUpdate()
    // #endif
    // 4. #ifdef MP-WEIXIN 检查隐私协议
    // #ifdef MP-WEIXIN
    if (wx.getPrivacySetting) {
      wx.getPrivacySetting({ success: (r) => { if (r.needAuthorization) uni.navigateTo({ url: '/pages-mp/privacy/index' }) } })
    }
    // #endif
  },
  onShow() { /* 回到前台时刷新未读数、同步时间戳 */ },
  onHide() { /* 清理定时器、断开 WebSocket */ },
  onError(err) { console.error('[App onError]', err) }
}
</script>
```

> **注意**：`App.vue` **不能有 `<template>`**，写了会编译报错。它只承载生命周期和全局样式。

#### 1.5.4 `main.js` —— 应用实例

Vue3 版 uni-app 的入口与普通 Vue3 不同，必须用 `createSSRApp` 并 `return { app }`：

```js
import { createSSRApp } from 'vue'
import * as Pinia from 'pinia'
import App from './App.vue'
import { setupStore } from '@/store'
import perm from '@/directives/perm'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = Pinia.createPinia()
  app.use(pinia)
  setupStore(pinia)          // 持久化插件等
  app.directive('perm', perm)
  return { app, Pinia }      // ★ 必须把 Pinia 一起返回，否则小程序端 store 失效
}
```

三个坑：
1. `return { app, Pinia }` —— 少返回 `Pinia` 会导致小程序端 `useStore()` 报 "no active Pinia"；
2. 不要在这里 `app.mount()`，uni-app 自己接管；
3. 全局组件用 `easycom` 自动引入，不要在这里 `app.component()` 注册（小程序端不支持运行时注册）。

#### 1.5.5 `uni.scss` —— 全局样式变量

它会被**自动注入到每个 `<style lang="scss">` 的开头**，所以只能放变量和 mixin，**不能放具体选择器**（否则每个组件都会重复输出一份 CSS，包体积爆炸）。

```scss
/* 主题色 */
$brand:        #1f7a4d;
$brand-light:  #e8f5ee;
$danger:       #d93026;
$warning:      #e6a23c;
$success:      #16a34a;
$info:         #64748b;

/* 文字 */
$text-1: #1f2937;
$text-2: #4b5563;
$text-3: #9ca3af;

/* 背景与边框 */
$bg-page:   #f5f7f9;
$bg-card:   #ffffff;
$border:    #e5e7eb;

/* 尺寸（用 rpx，750 设计稿） */
$radius-sm: 8rpx;
$radius:    16rpx;
$radius-lg: 24rpx;
$gap-sm:    16rpx;
$gap:       24rpx;
$gap-lg:    32rpx;

/* 字号 */
$fs-xs: 22rpx;
$fs-sm: 26rpx;
$fs:    28rpx;
$fs-lg: 32rpx;
$fs-xl: 40rpx;
```

### 1.6 多环境配置统一管理

核心原则：**全项目只有 `src/config/env.js` 一个文件读 `import.meta.env`**，其他任何地方都从这个文件 import。这样切换环境只改 `.env.*`，不用全局搜 `baseURL`。

#### 1.6.1 环境变量文件

`.env`（三端公共，不含环境差异）：

```ini
VITE_APP_NAME=农安链溯源
VITE_APP_VERSION=1.0.0
VITE_UPLOAD_MAX_MB=10
```

`.env.development`：

```ini
VITE_ENV=development
VITE_API_BASE=http://localhost:8080
VITE_CHAIN_ENABLED=true
VITE_DEBUG=true
```

`.env.staging`（体验版小程序/内测 App 用）：

```ini
VITE_ENV=staging
VITE_API_BASE=https://staging.agri-origin.com
VITE_CHAIN_ENABLED=true
VITE_DEBUG=true
```

`.env.production`：

```ini
VITE_ENV=production
VITE_API_BASE=https://api.agri-origin.com
VITE_CHAIN_ENABLED=true
VITE_DEBUG=false
```

#### 1.6.2 统一出口 `src/config/env.js`

难点在于：H5 走同域 `/api` 由 nginx 反代最省事；小程序**必须**用完整 https 域名；App 也用完整域名但可以走内网 IP 调试。同时小程序还要区分"开发版/体验版/正式版"。

```js
// src/config/env.js —— 全项目唯一读环境变量的地方

const RAW = {
  env: import.meta.env.VITE_ENV || 'development',
  appName: import.meta.env.VITE_APP_NAME || '农安链溯源',
  version: import.meta.env.VITE_APP_VERSION || '1.0.0',
  apiBase: import.meta.env.VITE_API_BASE || '',
  chainEnabled: import.meta.env.VITE_CHAIN_ENABLED !== 'false',
  debug: import.meta.env.VITE_DEBUG === 'true',
  uploadMaxMb: Number(import.meta.env.VITE_UPLOAD_MAX_MB || 10)
}

/** 小程序端根据「开发版/体验版/正式版」二次修正接口地址 */
function resolveMpEnvVersion() {
  // #ifdef MP-WEIXIN
  try {
    return uni.getAccountInfoSync().miniProgram.envVersion // develop | trial | release
  } catch (e) {
    return 'develop'
  }
  // #endif
  // #ifndef MP-WEIXIN
  return ''
  // #endif
}

function resolveApiBase() {
  const v = resolveMpEnvVersion()
  if (v === 'trial') return 'https://staging.agri-origin.com'
  if (v === 'release') return 'https://api.agri-origin.com'
  return RAW.apiBase
}

export const ENV = {
  ...RAW,
  apiBase: resolveApiBase(),
  /** 请求完整前缀：H5 生产走同域 /api；其余走绝对域名 */
  get baseURL() {
    // #ifdef H5
    if (RAW.env === 'production' && !RAW.apiBase) return '/api'
    // #endif
    return `${resolveApiBase()}/api`
  }
}

export const IS_DEV = ENV.env === 'development'
export const IS_PROD = ENV.env === 'production'
```

用法：

```js
import { ENV } from '@/config/env'
uni.request({ url: ENV.baseURL + '/batch/page' })
```

#### 1.6.3 构建命令与环境的对应

`package.json`：

```json
{
  "scripts": {
    "dev:h5": "uni",
    "dev:mp-weixin": "uni -p mp-weixin",
    "dev:app": "uni -p app",
    "build:h5": "uni build",
    "build:mp-weixin": "uni build -p mp-weixin",
    "build:app": "uni build -p app"
  }
}
```

Vite 的 `mode` 决定加载哪个 `.env.*`。uni CLI 默认 `dev` 用 `development`、`build` 用 `production`。要打预发包：

```bash
npm run build:mp-weixin -- --mode staging
```

> **注意**：小程序/App 端 `import.meta.env` 由 Vite 在**编译期**静态替换，所以 `.env` 里的值会被写死进包里，**改环境必须重新编译**。不要把"运行时切换环境"做进小程序（审核不允许）。

---

## 二、现有前端代码迁移与三端适配方案

### 2.1 必改语法清单

下面 12 类是**必改**项，其余（`ref`、`computed`、`watch`、`<script setup>`、`defineProps`）全部原样保留。按"改起来最容易漏"排序。

#### ① 标签替换

uni-app 只认内置组件，H5 标签（`div`/`span`/`img`/`input`）在小程序端会编译成 `unknown element`，样式全部失效。

| 原标签 | uni-app 组件 | 备注 |
| --- | --- | --- |
| `<div>` | `<view>` | 默认 `display: flex; flex-direction: column`，与原 div 的 block 行为不同，需要显式写 `flex-direction: row` |
| `<span>` | `<text>` | 只有 `<text>` 内的文字才受 `font-size`/`color` 控制；`<view>` 直接写文字在小程序端可以显示但不建议 |
| `<img>` | `<image>` | 必须写 `mode`（`aspectFill` / `widthFix`），否则宽高比失真 |
| `<input>` | `<input>` 或 `<input type="text">` | uni 的 input 是原生组件（小程序），层级最高，**无法被弹窗遮挡** |
| `<textarea>` | `<textarea>` | 同上，是原生组件，`z-index` 无效 |
| `<a href>` | `<navigator url>` 或 `@click` + `uni.navigateTo` | |
| `<ul>/<li>` | `<view>` 循环 | |
| `<br>` | 无，用块级 `<view>` 或 `\n` + `white-space: pre-line` | |
| `<iframe>` | `<web-view>`（仅小程序/App，且需业务域名） | H5 端 `web-view` 编译成 iframe |
| `<video>/<audio>` | `<video>/<audio>` | 属性名不同，见官方文档 |

批量替换命令（在 `src/` 下，先备份再执行）：

```bash
# 只在 .vue 文件的 template 区替换，执行后必须人工 review
grep -rl "<div" src --include=*.vue | xargs sed -i 's/<div\b/<view/g; s/<\/div>/<\/view>/g'
grep -rl "<span" src --include=*.vue | xargs sed -i 's/<span\b/<text/g; s/<\/span>/<\/text>/g'
grep -rl "<img" src --include=*.vue | xargs sed -i 's/<img\b/<image/g'
```

> `sed` 会把 `<script>` 里的字符串也替换掉，务必 `git diff` 逐文件确认。更稳的做法是一个目录一个目录手动改。

#### ② 路由跳转

原 `vue-router` 的 `router.push` / `<router-link>` 全部失效。

| 原写法 | uni-app 写法 | 差异 |
| --- | --- | --- |
| `router.push('/batch/1')` | `uni.navigateTo({ url: '/pages/batch/detail?id=1' })` | 保留当前页，最多 10 层 |
| `router.replace('/login')` | `uni.redirectTo({ url: '/pages/auth/login' })` | 关闭当前页 |
| `router.push('/home')`（tab 页） | `uni.switchTab({ url: '/pages/tabbar/home' })` | **只能跳 tabBar 页，且不能带参数** |
| 清空栈重开 | `uni.reLaunch({ url })` | 登录成功、退出登录后用 |
| `router.back()` | `uni.navigateBack({ delta: 1 })` | |
| `route.params.id` | `onLoad(options => options.id)` | 参数只能是字符串 |
| `route.query` | 同上 | 对象参数需 `JSON.stringify` + `encodeURIComponent` |

封装 `utils/router.js` 解决三个痛点：参数自动序列化、tab 页自动识别、防重复点击：

```js
// src/utils/router.js
import { TAB_PAGES } from '@/config/routes'

let lastNav = 0
function lock() {
  const now = Date.now()
  if (now - lastNav < 300) return false   // 300ms 内重复点击直接丢弃
  lastNav = now
  return true
}

function buildUrl(path, params) {
  if (!params) return path
  const qs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => {
      const v = typeof params[k] === 'object' ? JSON.stringify(params[k]) : params[k]
      return `${k}=${encodeURIComponent(v)}`
    })
    .join('&')
  return qs ? `${path}?${qs}` : path
}

export function nav(path, params) {
  if (!lock()) return
  if (TAB_PAGES.includes(path)) return uni.switchTab({ url: path })
  uni.navigateTo({ url: buildUrl(path, params) })
}

export function redirect(path, params) {
  uni.redirectTo({ url: buildUrl(path, params) })
}

export function relaunch(path, params) {
  uni.reLaunch({ url: buildUrl(path, params) })
}

export function back(delta = 1) {
  const pages = getCurrentPages()
  if (pages.length <= delta) return uni.reLaunch({ url: '/pages/tabbar/home' })
  uni.navigateBack({ delta })
}
```

`src/config/routes.js`：

```js
export const ROUTES = {
  LOGIN: '/pages/auth/login',
  HOME: '/pages/tabbar/home',
  BATCH_LIST: '/pages/batch/list',
  BATCH_DETAIL: '/pages/batch/detail',
  PLANTING_FORM: '/pages/planting/form',
  TRACE: '/pages/trace/index',
  PC_DASHBOARD: '/pages-pc/dashboard/index'
}

/** 所有 tabBar 页面，必须与 pages.json 里 tabBar.list 完全一致 */
export const TAB_PAGES = [
  '/pages/tabbar/home',
  '/pages/tabbar/batch',
  '/pages/tabbar/trace',
  '/pages/tabbar/mine'
]
```

#### ③ 本地存储

| 原 API | uni-app API | 注意 |
| --- | --- | --- |
| `localStorage.getItem(k)` | `uni.getStorageSync(k)` | 返回 `''` 而非 `null` |
| `localStorage.setItem(k, v)` | `uni.setStorageSync(k, v)` | 可直接存对象，内部自动序列化 |
| `localStorage.removeItem(k)` | `uni.removeStorageSync(k)` | |
| `localStorage.clear()` | `uni.clearStorageSync()` | **会清掉所有 key，慎用** |
| `sessionStorage` | 无对应 | 改用内存变量或 storage + 过期时间 |

小程序存储限制：**单个 key ≤ 1MB，总量 ≤ 10MB**。不要把批次列表整个缓存进去。

封装 `utils/storage.js` 统一加前缀和 TTL：

```js
// src/utils/storage.js
const PREFIX = 'agri_'

export const storage = {
  set(key, value, ttlSeconds) {
    const payload = { v: value, t: Date.now(), e: ttlSeconds ? Date.now() + ttlSeconds * 1000 : 0 }
    try {
      uni.setStorageSync(PREFIX + key, payload)
    } catch (e) {
      console.warn('[storage] 写入失败，可能超限', key, e)
    }
  },
  get(key, fallback = null) {
    try {
      const raw = uni.getStorageSync(PREFIX + key)
      if (!raw || typeof raw !== 'object' || !('v' in raw)) return fallback
      if (raw.e && Date.now() > raw.e) {
        uni.removeStorageSync(PREFIX + key)
        return fallback
      }
      return raw.v
    } catch (e) {
      return fallback
    }
  },
  remove(key) {
    uni.removeStorageSync(PREFIX + key)
  },
  clearAuth() {
    ;['token', 'user', 'perms'].forEach((k) => uni.removeStorageSync(PREFIX + k))
  }
}
```

> 原项目 token key 是 `agri_token`（PC）和 `agri_h5_token`（H5），**统一成 `agri_token`**，否则同一浏览器里两个应用的登录态不共享。

#### ④ 网络请求

`axios` 在小程序端完全不可用（依赖 `XMLHttpRequest`）。全部换 `uni.request`，见 §2.2。

#### ⑤ DOM 操作

这是最硬的一条：**小程序和 App 没有 DOM，`document`、`window`、`localStorage`、`navigator` 全部 undefined**。

| 原写法 | 替代方案 |
| --- | --- |
| `document.getElementById('x').scrollTop` | `<scroll-view :scroll-top="x">` 或 `uni.pageScrollTo()` |
| `el.scrollIntoView()` | `uni.pageScrollTo({ selector: '#anchor' })`（H5/App 支持） |
| `window.innerWidth` | `uni.getSystemInfoSync().windowWidth` |
| `window.addEventListener('resize')` | `uni.onWindowResize(cb)` |
| `document.title = 'x'` | `uni.setNavigationBarTitle({ title: 'x' })` |
| `document.body.appendChild` | 不存在，改用组件 + `v-if` |
| `getBoundingClientRect()` | `uni.createSelectorQuery().select('#x').boundingClientRect(cb).exec()` |
| `new Image()` 预加载 | `uni.getImageInfo({ src })` |
| `echarts.init(dom)` | 小程序用 `ec-canvas`，App 用 `renderjs`，H5 直接用（条件编译） |
| `FileReader` | `uni.getFileSystemManager().readFile()`（小程序/App） |
| `Blob` / `URL.createObjectURL` | 小程序不支持，用 `tempFilePath` |

**`document.title` 的批量迁移**：原 `router.beforeEach` 里那句 `document.title = ...` 删掉，改为每个页面 `onLoad` 里：

```js
onLoad(() => { uni.setNavigationBarTitle({ title: '批次详情' }) })
```

或者在 `pages.json` 里直接配 `navigationBarTitleText`，一行搞定，**推荐后者**。

#### ⑥ 生命周期

| 原（vue-router / Vue） | uni-app | 用途 |
| --- | --- | --- |
| `onMounted` | `onReady`（页面）/ `onMounted`（组件仍可用） | DOM 就绪，可做 `createSelectorQuery` |
| — | `onLoad(options)` | **接收路由参数，只触发一次** |
| — | `onShow` | 每次页面显示（从其他页返回也触发），**刷新数据的正确位置** |
| — | `onHide` | 页面隐藏，停轮询 |
| — | `onUnload` | 页面销毁，清定时器/断开 socket |
| — | `onPullDownRefresh` | 需 `pages.json` 开启 `enablePullDownRefresh` |
| — | `onReachBottom` | 需页面内容超出屏幕，配 `onReachBottomDistance` |
| `onBeforeUnmount` | `onUnload` + `onBeforeUnmount` | 两者都要清 |

> **最常见的 bug**：数据在 `onMounted` 里加载，从详情页返回列表页时数据不刷新。正确做法是 `onLoad` 里初始化筛选条件、`onShow` 里拉数据（或用 `onShow` 里判断 `needRefresh` 标记）。

#### ⑦ 弹窗 / Toast

| 原 | uni-app |
| --- | --- |
| `ElMessage.error(msg)` / `showFailToast(msg)` | `uni.showToast({ title: msg, icon: 'none' })` |
| `ElMessage.success` | `uni.showToast({ title: msg, icon: 'success' })` |
| `ElMessageBox.confirm` | `uni.showModal({ title, content, success })` |
| `ElLoading.service()` | `uni.showLoading({ title, mask: true })` |
| `showToast` 长文案 | 小程序 `icon:'none'` 最多显示 2 行，超过用 `uni.showModal` |

`utils/ui.js` 统一封装（顺带解决 toast 被 loading 挡住的问题）：

```js
// src/utils/ui.js
let loadingCount = 0

export function toast(title, icon = 'none', duration = 2000) {
  if (!title) return
  uni.showToast({ title: String(title).slice(0, 40), icon, duration, mask: false })
}

export function loading(title = '加载中') {
  loadingCount++
  if (loadingCount === 1) uni.showLoading({ title, mask: true })
}

export function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) uni.hideLoading()
}

export function confirm(content, title = '提示') {
  return new Promise((resolve) => {
    uni.showModal({
      title, content, confirmColor: '#1f7a4d',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

export function vibrate() {
  // #ifndef H5
  uni.vibrateShort({ fail: () => {} })
  // #endif
}
```

#### ⑧ 图片与静态资源

- `<image src="/static/images/a.png">` —— 绝对路径以 `static` 为根，**必须以 `/` 开头**；
- CSS 里 `background-image: url('~@/static/...')` —— 小程序端不支持背景图引用本地大图，超过 40KB 的图片小程序会报错，改用 `<image>` 定位；
- 网络图必须 https（小程序强制）。

#### ⑨ 表单组件差异

`<input>` / `<textarea>` 在小程序是**原生组件**，层级永远在最上层，`z-index` 无法覆盖。因此：

- 弹出选择器时，先 `v-if="!showPicker"` 隐藏 input，再弹；
- 或者用 `cover-view`（仅小程序）；
- `placeholder-style` 代替 `::placeholder` 伪类（小程序不支持伪元素选择器）。

#### ⑩ 事件差异

| 原 | uni-app | 说明 |
| --- | --- | --- |
| `@click.stop` | `@click.stop` | 支持 |
| `@touchstart.prevent` | `@touchstart.stop.prevent` | 小程序部分事件不支持 `prevent` |
| `@scroll` | `<scroll-view @scroll>` | 小程序页面不能监听 window scroll，必须用 `scroll-view` |
| `@longpress` | `@longpress` | 支持 |

#### ⑪ CSS 差异

- 不支持 `*` 通配选择器（小程序 WXSS 限制）；
- 不支持属性选择器 `[class^="x"]`；
- 不支持 `position: fixed` 在某些原生组件上（`video`、`map`、`canvas`）—— 用 `cover-view`；
- `calc()` 支持，但 `calc(100vh - 88rpx)` 在小程序端 `vh` 可用；
- 伪元素 `::before/::after` 支持，但**不能用于 `<text>`**。

#### ⑫ 第三方库替换

| 原依赖 | 是否能用于三端 | 替代 |
| --- | --- | --- |
| `axios` | 否 | `uni.request` |
| `element-plus` | 仅 H5 | `pages-pc` 内条件编译保留；移动端用 wot-design-uni |
| `vant` | 否（依赖 DOM） | wot-design-uni（API 高度相似，迁移成本最低） |
| `qrcode`（canvas 版） | 否 | `tki-qrcode` 或自绘 `qr-code.vue` |
| `dayjs` | 是 | 保留 |
| `lodash-es` | 是（纯函数） | 保留，按需 import |
| `echarts` | H5 可，小程序需 `ec-canvas`，App 需 `renderjs` | 见 §2.4 |

### 2.2 Axios 拦截器 → uni.request 封装

原封装（`pc-admin/src/api/request.js`）做了四件事，新封装必须一一对齐并补强：

| 原逻辑 | 新实现 |
| --- | --- |
| `baseURL: '/api'` | `ENV.baseURL`（三端不同，见 §1.6） |
| 请求拦截器加 `Authorization: Bearer` | 同，从 `utils/auth` 读 |
| 响应拦截器 `code !== 0` 弹错并 reject | 同，错误文案走 `errors.js` |
| 401 清 token + 跳登录 | 同，加**并发去重**（多个请求同时 401 只跳一次） |
| 403 提示越权 | 同 |
| 无 | **新增**：loading 计数、请求去重、超时重试、请求取消、日志 |

完整实现（`src/utils/request.js`）：

```js
// src/utils/request.js
import { ENV } from '@/config/env'
import { getToken, clearAuth } from '@/utils/auth'
import { toast, loading, hideLoading } from '@/utils/ui'
import { messageOf } from '@/utils/errors'

/** 并发 loading 计数：多个请求同时进行时只显示一个 loading */
let loadingCount = 0
/** 进行中的请求：用于去重 */
const pending = new Map()
/** 401 只处理一次 */
let redirecting = false

function startLoading() {
  if (loadingCount++ === 0) uni.showLoading({ title: '加载中', mask: true })
}
function endLoading() {
  if (loadingCount > 0 && --loadingCount === 0) uni.hideLoading()
}

function requestKey(config) {
  return [config.method, config.url, JSON.stringify(config.data || {})].join('|')
}

function buildQuery(params) {
  if (!params) return ''
  const qs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${k}=${encodeURIComponent(typeof params[k] === 'object' ? JSON.stringify(params[k]) : params[k])}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

function handleUnauthorized() {
  if (redirecting) return
  redirecting = true
  clearAuth()
  toast('登录状态已失效，请重新登录')
  // 通知 Pinia 清空内存态（避免 request 反向依赖 store 造成循环引用）
  uni.$emit('auth:expired')
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/auth/login' })
    redirecting = false
  }, 600)
}

/**
 * 统一请求
 * @param {object} options
 * @param {string} options.url        以 / 开头的接口路径，如 /batch/page
 * @param {string} [options.method]   GET/POST/PUT/DELETE
 * @param {object} [options.data]     POST body
 * @param {object} [options.params]   GET query
 * @param {object} [options.header]   额外请求头
 * @param {boolean}[options.loading]  是否显示全局 loading，默认 false
 * @param {boolean}[options.auth]     是否携带 token，默认 true
 * @param {boolean}[options.dedupe]   是否去重，默认 true
 * @param {number} [options.timeout]  超时毫秒，默认 20000
 * @param {number} [options.retry]    失败重试次数，默认 0（仅网络错误重试）
 */
export function request(options) {
  const {
    url, method = 'GET', data, params, header = {},
    loading: useLoading = false, auth = true, dedupe = true,
    timeout = 20000, retry = 0
  } = options

  // #ifdef H5
  // H5 端接口地址若是相对路径，说明走同域代理，无需拼域名
  // #endif
  const fullUrl = /^https?:\/\//.test(url) ? url : ENV.baseURL + url + buildQuery(params)
  const key = requestKey({ method, url: fullUrl, data })

  if (dedupe && pending.has(key)) {
    // 重复请求直接复用上一个 Promise，避免重复提交/重复 loading
    return pending.get(key)
  }

  if (useLoading) startLoading()

  const promise = new Promise((resolve, reject) => {
    const task = uni.request({
      url: fullUrl,
      method,
      data: method === 'GET' ? undefined : data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(auth && getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
        ...header
      },
      success(res) {
        const { statusCode, data: body } = res
        const bizCode = body && typeof body.code !== 'undefined' ? body.code : null
        const message = (body && body.message) || ''

        // 【关键】后端 GlobalExceptionHandler 对业务异常返回 HTTP 400 + body.code=4001/5001/5002
        // 因此必须「先看 body.code，再看 HTTP status」，否则业务码会被 HTTP 400 覆盖掉
        if (statusCode === 401 || bizCode === 401) {
          handleUnauthorized()
          return reject({ code: 401, message: '登录已失效' })
        }
        if (statusCode === 403 || bizCode === 403) {
          toast(message || '越权操作已被拦截')
          return reject({ code: 403, message: message || '无权限' })
        }

        // 统一响应体 { code, message, data }
        if (bizCode !== null) {
          if (bizCode === 0) return resolve(body)
          const msg = message || messageOf(bizCode) || '操作失败'
          toast(msg)
          return reject({ code: bizCode, message: msg, httpStatus: statusCode })
        }

        // 非统一响应体（如文件流、健康检查）
        if (statusCode < 200 || statusCode >= 300) {
          const msg = message || `服务异常（${statusCode}）`
          toast(msg)
          return reject({ code: statusCode, message: msg })
        }
        resolve(body)
      },
      fail(err) {
        // 网络层失败：可重试
        if (retry > 0) {
          pending.delete(key)
          return request({ ...options, retry: retry - 1 }).then(resolve, reject)
        }
        const msg = /timeout/i.test(err.errMsg || '')
          ? '请求超时，请检查网络'
          : '网络连接失败，请稍后重试'
        toast(msg)
        if (ENV.debug) console.error('[request fail]', fullUrl, err)
        reject({ code: -1, message: msg, raw: err })
      },
      complete() {
        if (useLoading) endLoading()
        pending.delete(key)
      }
    })
    // 暴露 task 以便外部取消（如页面卸载）
    promise.__task = task
  })

  if (dedupe) pending.set(key, promise)
  return promise
}

/* ---------- 语法糖，保持与原 axios 版本的调用手感一致 ---------- */
export const http = {
  get: (url, params, opt = {}) => request({ url, method: 'GET', params, ...opt }),
  post: (url, data, opt = {}) => request({ url, method: 'POST', data, ...opt }),
  put: (url, data, opt = {}) => request({ url, method: 'PUT', data, ...opt }),
  del: (url, params, opt = {}) => request({ url, method: 'DELETE', params, ...opt })
}

export default request
```

配套 `utils/auth.js`（**唯一**读写 token 的地方）：

```js
// src/utils/auth.js
import { storage } from './storage'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'
const PERM_KEY = 'perms'

export const getToken = () => storage.get(TOKEN_KEY, '')
export const setToken = (t) => storage.set(TOKEN_KEY, t)
export const getUser = () => storage.get(USER_KEY, null)
export const setUser = (u) => storage.set(USER_KEY, u)
export const getPerms = () => storage.get(PERM_KEY, [])
export const setPerms = (p) => storage.set(PERM_KEY, p)
export const clearAuth = () => storage.clearAuth()
```

改造前后对比，页面代码几乎不用动：

```js
// 原：axios
// batchPage({ page: 1, size: 10 })

// 新：api/modules/batch.js
import { http } from '@/utils/request'
export const batchPage = (params) => http.get('/batch/page', params)
export const batchDetail = (id) => http.get(`/batch/${id}`)
export const createBatch = (data) => http.post('/batch', data)
// 页面里调用完全一致：
const res = await batchPage({ page: 1, size: 10 })
```

**三端兼容要点**：

1. **H5 跨域**：开发用 `vite.config.js` 的 `server.proxy`；生产用 nginx 反代，接口走同域 `/api`，不产生跨域；
2. **小程序**：必须 https，且域名在微信后台白名单（`request` 合法域名），本地调试可用"不校验合法域名"；
3. **App**：无跨域限制，直接请求绝对地址；但要处理 **iOS ATS**（必须 https，或 `manifest` 里配置 `NSAllowsArbitraryLoads`）；
4. **`uni.request` 没有 `params` 概念**，GET 参数必须手动拼到 URL（已封装）；
5. **响应体类型**：`uni.request` 会自动 `JSON.parse`（当 `dataType` 为 json 时），但 `uni.uploadFile` **不会**，需要手动 `JSON.parse(res.data)`（见 §3.3）。

### 2.3 Pinia 的可用性与迁移

**结论：可以继续用，且官方支持。** uni-app Vue3 版本从 3.2.x 起内置 Pinia 支持，CLI 项目需自己 `npm i pinia`。

三个必须注意的点：

#### ① `main.js` 必须返回 Pinia

```js
export function createApp() {
  const app = createSSRApp(App)
  const pinia = Pinia.createPinia()
  app.use(pinia)
  return { app, Pinia }     // ★ 少了 Pinia 小程序端会报 "getActivePinia() was called but there was no active Pinia"
}
```

#### ② 持久化插件要自己写，别用 web 版

`pinia-plugin-persistedstate` 默认走 `localStorage`，小程序端直接崩。要么传 `storage` 适配器，要么用 `uni.setStorageSync` 手写一个 20 行的插件：

```js
// src/store/index.js
import { createPinia } from 'pinia'

const PERSIST_KEYS = ['user']

export function setupStore(pinia) {
  // 启动时恢复
  PERSIST_KEYS.forEach((id) => {
    try {
      const raw = uni.getStorageSync(`pinia_${id}`)
      if (raw) pinia.state.value[id] = { ...pinia.state.value[id], ...raw }
    } catch (e) { /* ignore */ }
  })

  // 订阅变化后写回
  pinia.subscribe((mutation, state) => {
    if (PERSIST_KEYS.includes(mutation.storeId)) {
      uni.setStorageSync(`pinia_${mutation.storeId}`, state[mutation.storeId])
    }
  })
}
```

#### ③ 改 `localStorage` 为 `uni.getStorageSync`

原 `store/user.js` 只需改 6 行：

```js
// src/store/user.js
import { defineStore } from 'pinia'
import { login as loginApi, getProfile, logout as logoutApi } from '@/api'
import { getToken, setToken, getUser, setUser, getPerms, setPerms, clearAuth } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    user: getUser(),
    permissions: getPerms(),
    lastSyncAt: 0
  }),
  getters: {
    roleCode: (s) => (s.user ? s.user.roleCode : ''),
    displayName: (s) => (s.user ? s.user.realName : ''),
    isAdmin: (s) => (s.user ? s.user.roleCode === 'ADMIN' : false)
  },
  actions: {
    hasPerm(perm) {
      if (!perm) return true
      if (this.isAdmin) return true
      return this.permissions.includes(perm)
    },
    async login(form) {
      const res = await loginApi(form)
      this.applyAuth(res.data)
      return res.data
    },
    applyAuth(data) {
      this.token = data.token
      this.user = data.user
      this.permissions = data.permissions || []
      this.lastSyncAt = Date.now()
      setToken(this.token); setUser(this.user); setPerms(this.permissions)
    },
    async reloadProfile() {
      const res = await getProfile()
      this.user = res.data
      setUser(this.user)
      this.lastSyncAt = Date.now()
      return res.data
    },
    /** 冷启动时若已有 token，静默刷新一次用户信息，保证多端数据一致 */
    async restore() {
      if (!this.token) return
      try { await this.reloadProfile() } catch (e) { /* token 失效由 request 统一处理 */ }
    },
    async logout() {
      try { if (this.token) await logoutApi() } catch (e) { /* 忽略 */ }
      this.$reset()
      clearAuth()
    }
  }
})
```

在 `App.vue` 里监听 401 事件，清空内存态：

```js
// App.vue onLaunch
uni.$on('auth:expired', () => { useUserStore().$reset() })
```

#### ④ 不建议放进 Pinia 的东西

- 列表数据（分页结果）—— 放页面局部 `ref`，Pinia 只存"需要跨页共享"的；
- 表单临时值 —— 同上；
- 大对象（批次详情全量 JSON）—— 小程序内存有限。

### 2.4 样式与组件库适配

#### ① px → rpx

uni-app 的 `rpx` 是响应式单位：**750rpx = 屏幕宽度**。设计稿按 750 宽出图时，`1px（设计稿）= 1rpx`。

换算规则：

| 原单位 | 新单位 | 说明 |
| --- | --- | --- |
| 设计稿 750 宽下的 px | 直接用 rpx | 375 屏上自动减半 |
| 已按 375 宽写死的 px | `px * 2` 转 rpx | 如 `font-size: 14px` → `28rpx` |
| 1px 边框 | `1px` 保留 | 用 rpx 会出现"半像素消失"或"粗边框"，**边框一律用 px** |
| 阴影、圆角 | 小值用 px，大值用 rpx | `border-radius: 8px` 可保留 |

批量转换脚本（先把 `px` 全部换成 `rpx`，再手工把 `1px` 边框、`box-shadow` 改回 `px`）：

```bash
# 只处理 style 块，执行后必须 review
find src -name "*.vue" -exec sed -i 's/\([0-9]\+\)px/\1rpx/g' {} \;
# 再把 1px 边框改回来
find src -name "*.vue" -exec sed -i 's/1rpx solid/1px solid/g' {} \;
```

> **PC 端的坑**：`rpx` 在宽屏上会等比放大到离谱（1920px 屏上 750rpx = 1920px）。必须在 `pages.json` 的 `globalStyle` 里加：
>
> ```jsonc
> "rpxCalcMaxDeviceWidth": 960,
> "rpxCalcBaseDeviceWidth": 375
> ```
>
> 这样超过 960px 后按 960 换算，PC 上字号才不会变成巨无霸。**该配置仅 H5 与 App-vue 生效，小程序固定按 750 换算**，所以小程序端无需担心。

#### ② Element Plus 处理

| 场景 | 处理方式 |
| --- | --- |
| `pages-pc/**` 页面 | 保留 `el-*` 组件，整页 `#ifdef H5`，`import ElementPlus from 'element-plus'` 在页面内按需引入 |
| `pages/**` 通用页面 | **禁止**出现任何 `el-` 标签 |
| 全局样式 | Element Plus 的 `reset.css` 会重置 `body`，与 uni 冲突。**不要全局 `app.use(ElementPlus)`**，改为在 `pages-pc` 的每个页面内 `import 'element-plus/dist/index.css'`，并用 `.pc-scope` 包裹限制作用域 |

`pages-pc` 页面骨架：

```vue
<template>
  <!-- #ifdef H5 -->
  <view class="pc-scope">
    <pc-shell>
      <el-table :data="rows">...</el-table>
    </pc-shell>
  </view>
  <!-- #endif -->
</template>

<script setup>
// #ifdef H5
import { ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/dist/index.css'
// #endif
</script>
```

> 只在 H5 引入，小程序构建时整段被剔除，不会报错也不会增大包体。

#### ③ Vant 处理

Vant **完全不能用于小程序/App**（它大量使用 `document`、`getComputedStyle`）。组件对应关系：

| Vant | wot-design-uni | 说明 |
| --- | --- | --- |
| `van-button` | `wd-button` | 属性名不同：`type` → `type`，`round` → `round` |
| `van-field` | `wd-input` | `v-model` 用法一致 |
| `van-cell-group` | `wd-cell-group` | |
| `van-list` | `wd-loadmore` + `scroll-view` | 或用 `composables/useList` |
| `van-popup` | `wd-popup` | `position="bottom"` 一致 |
| `van-uploader` | 自封装 `photo-uploader.vue` | Vant 的上传逻辑要重写（§3.3） |
| `van-tabbar` | `pages.json` 原生 tabBar | **不要用组件模拟**，原生性能好且小程序支持 |
| `van-steps` | 自绘 `trace-timeline.vue` | 溯源时间轴需要自定义样式，组件库的都太死板 |
| `van-tag` | `wd-tag` 或自绘 | 状态标签自绘更可控 |
| `van-icon` | `wd-icon` | 图标名不同，需逐个对照 |
| `showToast` | `uni.showToast` | |
| `showDialog` | `uni.showModal` | |

组件库选型对比：

| 组件库 | 跨端完整度 | 体积 | 维护 | 推荐度 |
| --- | --- | --- | --- | --- |
| **wot-design-uni** | 高，Vue3 原生 | 按需引入，小 | 活跃 | ★★★★★ |
| uni-ui（官方） | 最高，官方保证 | 中 | 官方 | ★★★★ |
| uview-plus | 高 | 较大 | 活跃 | ★★★☆ |
| Vant Weapp（原生小程序） | 仅小程序 | 小 | 活跃 | 不适用三端 |

**本方案选 wot-design-uni**，理由：Vue3 组合式 API 写法、支持 `easycom` 按需、主题变量可覆盖、组件覆盖 90% 的移动场景。uni-ui 作为补充（`uni-popup`、`uni-datetime-picker` 在某些场景更稳）。

安装与配置：

```bash
npm i wot-design-uni
```

```js
// vite.config.js
import { UniWotUiResolver } from 'wot-design-uni/resolver'  // 可选，用 easycom 则不需要
```

`pages.json` 里配 `easycom`（见 §1.5.1），之后任何页面直接写 `<wd-button>` 即可，无需 import。

#### ④ 三端样式统一策略

| 层级 | 做法 |
| --- | --- |
| 变量层 | 全部集中在 `uni.scss`（颜色、字号、间距、圆角），组件里只引用变量，不写魔法值 |
| 基础样式 | `styles/common.scss` 定义原子类：`.flex`、`.flex-between`、`.text-ellipsis`、`.card`、`.gap-*` |
| 端修正 | `styles/pc.scss`、`styles/mp.scss` 用条件编译引入 |
| 组件样式 | 每个组件 `<style lang="scss" scoped>`，禁止跨组件选择器 |
| 安全区 | 底部固定按钮统一包 `safe-area.vue`（`padding-bottom: env(safe-area-inset-bottom)`） |

`App.vue` 里全局引入：

```vue
<style lang="scss">
@import '@/styles/reset.scss';
@import '@/styles/common.scss';
@import '@/styles/theme.scss';
/* #ifdef H5 */
@import '@/styles/pc.scss';
/* #endif */
/* #ifdef MP-WEIXIN */
@import '@/styles/mp.scss';
/* #endif */
</style>
```

#### ⑤ 屏幕适配

三档断点（`pages-pc` 用媒体查询，`pages` 用 rpx 自动适配）：

```scss
/* styles/pc.scss */
.pc-scope {
  min-height: 100vh;
  background: #f5f7f9;
  /* 900-1280 收缩侧边栏 */
  @media (max-width: 1280px) { .pc-side { width: 180px !important; } }
  /* 1280-1600 标准 */
  @media (min-width: 1280px) { .pc-side { width: 220px; } }
}
```

> 移动端不需要写媒体查询 —— `rpx` 已经把 375 / 390 / 414 / 768 全部覆盖了。

### 2.5 端差异场景与条件编译

#### 条件编译语法速查

| 位置 | 语法 |
| --- | --- |
| JS | `// #ifdef MP-WEIXIN` … `// #endif` |
| 模板 | `<!-- #ifdef H5 -->` … `<!-- #endif -->` |
| 样式 | `/* #ifdef APP-PLUS */` … `/* #endif */` |
| 取反 | `#ifndef H5`（不是 H5 时生效） |
| 多平台或 | `#ifdef H5 \|\| APP-PLUS` |
| 多平台与 | 不支持 `&&`，需嵌套 |

平台标识：`H5`、`MP-WEIXIN`、`MP-ALIPAY`、`MP`（所有小程序）、`APP-PLUS`、`APP-ANDROID`、`APP-IOS`、`APP`。

#### 高频端差异场景总表

| 场景 | H5 | 微信小程序 | App | 封装位置 |
| --- | --- | --- | --- | --- |
| **扫码** | 无原生 API，需 `html5-qrcode` | `uni.scanCode` | `uni.scanCode` | `utils/scan.js` |
| **拍照/相册** | `uni.chooseImage`（input file） | 需隐私授权 + `uni.chooseImage` | 需动态权限 + `uni.chooseImage` | `utils/upload.js` + `photo-uploader.vue` |
| **文件上传** | `uni.uploadFile`（XHR） | `uni.uploadFile`（需 uploadFile 域名） | `uni.uploadFile` | `utils/upload.js` |
| **分享** | 无系统分享，复制链接/二维码 | `onShareAppMessage` + 分享到会话 | `uni.share`（需 SDK 配置） | `composables/useShare.js` |
| **支付** | 跳转收银台 / 二维码 | `uni.requestPayment`（需商户号） | 微信/支付宝 SDK | `utils/pay.js`（本项目未涉及） |
| **定位** | 浏览器定位（需 https） | `uni.getLocation` + 隐私声明 | `uni.getLocation` + 权限 | `utils/location.js` |
| **保存图片到相册** | 触发下载 | `uni.saveImageToPhotosAlbum` | `uni.saveImageToPhotosAlbum` | `utils/album.js` |
| **原生导航栏** | 可隐藏 | 胶囊按钮占位需避让 | 状态栏高度需计算 | `app-navbar.vue` |
| **返回拦截** | `beforeunload` | 不支持 | `onBackPress` | `App.vue` + 页面内 |
| **长按识别二维码** | 不支持 | `image show-menu-by-longpress` | 不支持 | `qr-code.vue` |
| **推送** | Web Push（另说） | 订阅消息 | 厂商推送/uni-push | `utils/push.js` |
| **版本更新** | 刷新即最新 | 微信自动更新 | `plus.runtime.getProperty` + 下载 | `pages-app/update` |
| **剪贴板** | `uni.setClipboardData` | 同 | 同 | 无差异 |
| **震动** | 不支持 | 支持 | 支持 | `utils/ui.js`（已封装） |

#### 分享封装示例

```js
// src/composables/useShare.js
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

export function useShare(getShareData) {
  // #ifdef MP-WEIXIN
  onShareAppMessage(() => ({ title: '农安链溯源', path: '/pages/trace/index', ...getShareData() }))
  onShareTimeline(() => ({ title: '农安链溯源 · 扫码查溯源' }))
  // #endif

  // #ifdef APP-PLUS
  const share = () => {
    uni.share({
      provider: 'weixin',
      scene: 'WXSceneSession',
      type: 0,
      href: getShareData().href,
      title: getShareData().title,
      success: () => uni.showToast({ title: '分享成功' }),
      fail: () => uni.showToast({ title: '分享失败', icon: 'none' })
    })
  }
  return { share }
  // #endif

  // #ifdef H5
  const share = () => {
    uni.setClipboardData({
      data: getShareData().href,
      success: () => uni.showToast({ title: '链接已复制' })
    })
  }
  return { share }
  // #endif
}
```

#### 原生能力调用规范

1. **所有 `uni.xxx` 之外的原生 API（`wx.xxx`、`plus.xxx`）必须写在 `utils/` 或 `pages-mp|pages-app/` 里**，页面只调封装后的统一函数；
2. 调用前必须做**能力存在性判断**：`if (uni.canIUse('scanCode'))`；
3. 失败必须走 `fail` 回调，不要只写 `success`（用户拒绝授权时不写 `fail` 会静默失败，极难排查）。

### 2.6 三端统一扫码封装

**调用方式必须完全一致**：`const code = await scan()`。

底层差异：

| 端 | 能力 | 限制 |
| --- | --- | --- |
| 微信小程序 | `uni.scanCode` | 无需额外配置；需用户授权相机（首次自动弹） |
| App | `uni.scanCode` | 需在 `manifest.json` 声明 `Barcode` 模块 |
| H5 手机端 | 站内实时取景（`utils/scan-overlay`） | 必须自己调摄像头 + 解码库；**http 环境下浏览器禁止调摄像头，必须 https 或 localhost** |
| H5 电脑端 | 不申请摄像头，选二维码图片识别 | 笔记本摄像头扫码体验差且多数台式机没有，直接给"选择图片文件" |

> H5 端不提供"拍照识别"：调起系统相机只是拍一张照片，用户以为在扫码，
> 实际还要回到相册里挑图，多一步且容易误解。手机端就两个选择——实时扫码、
> 从相册选图；电脑端就一个——选图片文件。

实现（`src/utils/scan.js`，单文件条件编译，最稳）：

```js
// src/utils/scan.js
import { toast } from './ui'

/**
 * 统一扫码
 * @param {object} opts
 * @param {boolean} [opts.onlyFromCamera] 是否只允许相机（不允许从相册选）
 * @param {string[]}[opts.scanType]       扫码类型，默认 ['qrCode','barCode']
 * @returns {Promise<string>} 扫码结果字符串
 */
export function scan(opts = {}) {
  const { onlyFromCamera = false, scanType = ['qrCode', 'barCode'] } = opts

  // #ifdef MP-WEIXIN || APP-PLUS
  return new Promise((resolve, reject) => {
    uni.scanCode({
      onlyFromCamera,
      scanType,
      success: (res) => resolve(res.result),
      fail: (err) => {
        // 用户主动取消不算错误，静默返回
        if (/cancel/i.test(err.errMsg || '')) return reject({ cancel: true })
        toast('扫码失败，请重试')
        reject(err)
      }
    })
  })
  // #endif

  // #ifdef H5
  return scanByH5()
  // #endif
}

/* ---------------- H5 专用实现 ---------------- */
// #ifdef H5
let scannerInstance = null

async function scanByH5() {
  // 1. 非安全上下文直接提示（http 下 getUserMedia 不存在）
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    toast('当前环境不支持摄像头扫码，请使用 https 访问或手动输入溯源码')
    return Promise.reject({ code: 'NO_CAMERA' })
  }

  // 2. 动态加载解码库，避免打进小程序包
  const { Html5Qrcode } = await import('html5-qrcode')

  // 3. 需要一个容器，页面里放 <view id="scan-reader" />
  let el = document.getElementById('scan-reader')
  if (!el) {
    el = document.createElement('div')
    el.id = 'scan-reader'
    document.body.appendChild(el)
  }

  scannerInstance = new Html5Qrcode('scan-reader')

  return new Promise((resolve, reject) => {
    scannerInstance
      .start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText) => {
          stopH5Scan()
          resolve(decodedText)
        },
        () => { /* 每帧解码失败，忽略 */ }
      )
      .catch((err) => {
        toast('无法启动摄像头，请检查浏览器权限')
        reject(err)
      })
  })
}

export function stopH5Scan() {
  if (scannerInstance) {
    scannerInstance.stop().then(() => scannerInstance.clear()).catch(() => {})
    scannerInstance = null
  }
}
// #endif
```

页面里三端写法完全一致：

```vue
<script setup>
import { scan, stopH5Scan } from '@/utils/scan'
import { nav, ROUTES } from '@/utils/router'

async function onScan() {
  try {
    const code = await scan({ onlyFromCamera: false })
    // 溯源编码可能是 URL 或纯编码，统一提取最后一段
    const traceCode = extractCode(code)
    nav('/pages/trace/index', { code: traceCode })
  } catch (e) {
    if (e && e.cancel) return
  }
}

function extractCode(raw) {
  if (!raw) return ''
  const m = String(raw).match(/([A-Za-z0-9]{8,})$/)
  return m ? m[1] : raw
}

// H5 页面卸载时务必停掉摄像头，否则指示灯常亮
onUnload(() => {
  // #ifdef H5
  stopH5Scan()
  // #endif
})
</script>
```

**必须配的 `html5-qrcode` 依赖**：

```bash
npm i html5-qrcode
```

**H5 端兜底方案**（用户拒绝摄像头 / PC 无摄像头）：

```vue
<!-- pages/tabbar/trace.vue 里 -->
<view class="manual">
  <wd-input v-model="code" placeholder="请输入包装上的溯源码" />
  <wd-button @click="query">查 询</wd-button>
</view>
```

> 生产建议：PC 端优先展示"输入框 + 二维码图片"，扫码按钮作为辅助；移动 H5 首选唤起摄像头。不要把扫码做成唯一入口。

---

## 三、三端数据互通与后端对接方案

### 3.1 JWT 三端登录一致性

#### 现状核对

读 `backend/.../security/JwtUtil.java` 可以确认：当前 JWT 是 **HS256 无状态令牌**，有效期 12 小时（`jwt.expire-hours`），claim 里带了 `userId`、`roleCode`、`dataScope`、`villageId`、`farmId`，服务端**不保存会话**，`/auth/logout` 只是空实现（源码注释写明"无状态，前端清除 token 即可"）。

这个设计对三端是**天然友好**的：

- 三端各自持有自己的 token，互不干扰，同一账号可以在手机、电脑、小程序**同时在线**；
- 服务端每次请求都从 token 解析身份，`AuthInterceptor` + `applyScope` 按 `dataScope` 过滤数据，所以三端看到的业务数据**必然一致**（同一份 MySQL、同一套权限规则）；
- 不需要"设备绑定"、"会话表"、"单点登录"等额外机制。

**结论：账号密码登录这条路，后端一行都不用改。**

#### 前端需要统一的四件事

后端不用改，不代表前端可以随便写。三端必须遵守同一套规则，否则会出现"H5 登录了、小程序还是未登录"的假象（其实是两套 key）。

| 规则 | 具体要求 |
| --- | --- |
| **Token key 统一** | 全部用 `agri_token`。原 PC 用 `agri_token`、原 H5 用 `agri_h5_token`，必须统一（`utils/auth.js` 里已收口） |
| **携带方式统一** | 一律 `Authorization: Bearer <token>`，由 `utils/request.js` 自动注入，页面里禁止手写 header |
| **401 处理统一** | 先尝试静默续期并重放原请求；续期也失败才清 token → 通知 Pinia → `reLaunch` 到登录页。三端同一套逻辑，都收在 `utils/request.js` |
| **过期时间感知** | 登录返回体里带 `expiresIn` / `refreshExpiresIn`，全部存下来；`App.vue onShow` 里若 access 快过期就先续期 |

#### Token 存储的端差异

| 端 | 实际落点 | 安全性 | 建议 |
| --- | --- | --- | --- |
| H5 | `localStorage`（uni 封装） | 中，XSS 可读 | 不存敏感业务数据，只存 token + 用户基础信息 |
| 微信小程序 | 小程序 Storage（沙箱） | 较高，其他小程序读不到 | 同上 |
| App | 原生 storage（`plus.storage`） | 较高，但 root 设备可读 | 同上；**不要**把密码存进去 |

`utils/auth.js` 已经用 `uni.setStorageSync` 统一，三端自动落到各自容器，代码零差异。

#### 12 小时过期的体验问题与两种解法

现状是 token 到期必须重新登录。移动端用户对此容忍度低（正在地里拍照片，突然要重登）。

**解法 A（后端零改造，推荐毕设阶段用）**：滑动续期提示

```js
// src/utils/auth.js 增加
export function isTokenExpiringSoon(thresholdSec = 1800) {
  const exp = storage.get('token_exp', 0)
  return exp > 0 && exp - Date.now() / 1000 < thresholdSec
}
```

在 `App.vue` 的 `onShow` 里检查，快过期时静默调一次 `/auth/profile`（如果后端未来支持续签则换成续签接口），否则到期正常跳登录。

**解法 B（后端小改造，生产推荐）**：增加 refresh token

```java
// AuthController 新增
@Operation(summary = "刷新令牌")
@PostMapping("/refresh")
public R<LoginVO> refresh(@RequestBody RefreshTokenDTO dto) {
    return R.ok("续期成功", authService.refresh(dto.getRefreshToken()));
}
```

`refreshToken` 用同样的 JWT 签发，但 `expire-hours` 设为 7 天，claim 里加 `"type":"refresh"`，`AuthInterceptor` 解析时校验 `type != refresh`（防止 refresh token 直接当访问令牌用）。前端在 401 时先尝试刷新一次，成功则重放原请求，失败才跳登录。

> **实现状态：A + B 均已落地（2026-11）。** 最终形态与本条的设想一致，只是把"类型"声明命名为 `typ`，并且：

| 项 | 实现要点 | 代码位置 |
| --- | --- | --- |
| 双 token 签发 | 签一次登录同时下发 access（`typ=access`，12h）与 refresh（`typ=refresh`，7d）；refresh 里**只放 userId**，权限换发时重新读库 | `JwtUtil.generate/generateRefresh`、`AuthService.issueToken` |
| 类型隔离 | `parse()` 拒绝 `typ=refresh`，`parseRefresh()` 拒绝非 refresh。把 refresh 塞进 `Authorization` 头、或拿 access 调 `/auth/refresh`，都是 401 | `JwtUtil` |
| 免鉴权放行 | `/auth/refresh` 加入 `WebConfig.excludePathPatterns`（调用时 access 往往已过期） | `WebConfig` |
| 权限即时生效 | 换发时 `permissionCache` 重新查角色权限，管理员改角色后最长 12 小时内三端自动拿到新权限，且**不刷新 `last_login_time`** | `AuthService.issueToken(user, false)` |
| 前端单飞 | 页面并发 6 个请求一起 401，只发一次续期请求，其余等同一个 Promise | `utils/token.js` |
| 401 重放 | 先续期再重放原请求，失败才 `handleUnauthorized`；`auth:false` 的登录类请求不参与续期，避免"密码错了却提示登录失效" | `utils/request.js` |
| 主动续期 | `App.vue onShow` 里 `isTokenExpiringSoon()` 命中就直接续，用户看不到中断 | `App.vue` |

验证脚本：`frontend/uni-app/scripts/e2e-refresh.js`（把 access 改成非法值后刷新页面，断言"自动换新 token + 业务数据照常加载 + 不被踢回登录页"，再反向断言"refresh 也失效时必须回登录页"）。

> 两种解法不冲突：先用 A 保证体验，后续加 B 提升可用性。**不要**为了"续期"把 token 有效期改成 30 天，那等于放弃安全边界。

#### 三端数据一致性的保障点

同一账号在三端登录后，数据一致性由后端保证，前端只需注意三件事：

1. **不要在前端计算业务状态**（如"这个批次是否可销售"），一律用后端返回的 `status`/`canSale` 字段，否则三端规则会漂移；
2. **写操作成功后主动刷新相关列表**，不要依赖本地缓存（见 §3.4）；
3. **同一个页面在 `onShow` 里重新拉取数据**，而不是只在 `onLoad` 拉一次——这是多端操作后"另一端点开还是旧数据"的根因。

### 3.2 微信小程序登录打通

> **实现状态：已落地（2026-11）。** 实际实现与本节的差异只有字段名（`wx_bound_at`）与多出的两个辅助接口，对应代码：
>
> | 位置 | 内容 |
> | --- | --- |
> | `sql/patch-wx-login.sql` | 幂等 ALTER：`wx_openid` / `wx_unionid` / `wx_bound_at` + `uk_wx_openid` 唯一索引（`init.sql` 已同步） |
> | `backend/.../service/WxAuthService.java` | code2session、票据、绑定、解绑、mock 模式 |
> | `backend/.../dto/WxLoginDTO.java`、`WxBindDTO.java` | 入参 |
> | `backend/.../controller/AuthController.java` | `POST /auth/wx-login`、`POST /auth/wx-bind`、`POST /auth/wx-unbind`、`GET /auth/wx-status` |
> | `backend/.../vo/LoginVO.java` | 复用 `LoginVO`，新增 `needBind` / `bindTicket` 两个可选字段（null 不序列化） |
> | `frontend/.../store/user.js`、`pages/auth/login.vue`、`pages/auth/wx-bind.vue` | 前端三处调用已对齐 |
>
> 未配置 `app.wx.appid` / `app.wx.secret` 时自动进入 **mock 模式**：不请求微信服务器，使用固定模拟 openid，本地可完整验证"首次 needBind → 绑定 → 直接登录 → 解绑后回到 needBind"闭环；启动与登录日志都会显式打印 mock 提示，不会伪装成真实微信登录。

#### 两条路线的取舍

| 维度 | 路线 A：账号密码直登 | 路线 B：微信一键登录 + 绑定现有账号 |
| --- | --- | --- |
| 后端改造 | **零** | 需加字段 + 2 个接口 |
| 用户体验 | 每次输账号密码，小程序里很别扭 | 点一下即登录，符合小程序习惯 |
| 账号体系 | 完全复用现有 `sys_user` | 复用，openid 作为附加绑定 |
| 审核风险 | 无 | 需说明"获取用户信息"用途 |
| 落地成本 | 0.5 天 | 2～3 天 |
| 适用阶段 | 毕设演示、内部使用 | 正式对外 |

**建议：两条都实现，A 作为兜底入口保留在登录页底部（"账号密码登录"），B 作为默认快捷入口。** 这样即使微信接口挂了、或用户没绑过，也进得去。

#### 数据结构改造

```sql
-- 实际落地文件：sql/patch-wx-login.sql（对已有库幂等；init.sql 已同步）
ALTER TABLE `sys_user`
  ADD COLUMN `wx_openid`   VARCHAR(64) DEFAULT NULL COMMENT '微信小程序 openid' AFTER `avatar`,
  ADD COLUMN `wx_unionid`  VARCHAR(64) DEFAULT NULL COMMENT '微信开放平台 unionid' AFTER `wx_openid`,
  ADD COLUMN `wx_bound_at` DATETIME    DEFAULT NULL COMMENT '微信绑定时间' AFTER `wx_unionid`;

-- openid 唯一；MySQL 的 UNIQUE 允许多个 NULL，未绑定的用户不受影响
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_wx_openid` (`wx_openid`);
```

#### 后端实现

**1）微信接口客户端**

```java
// backend/.../sdk/WxMiniClient.java
@Component
public class WxMiniClient {

    @Value("${wx.mini.appid:}")   private String appid;
    @Value("${wx.mini.secret:}")  private String secret;

    /** 用 code 换 openid / session_key */
    public Map<String, Object> code2Session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appid
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        String json = HttpUtil.get(url, 5000);
        Map<String, Object> map = JSONUtil.parseObj(json);
        if (map.containsKey("errcode") && ((Number) map.get("errcode")).intValue() != 0) {
            throw new BizException(4001, "微信登录失败：" + map.get("errmsg"));
        }
        return map; // 含 openid / unionid / session_key
    }
}
```

`application.yml` 增加：

```yaml
wx:
  mini:
    appid: ${WX_MINI_APPID:}
    secret: ${WX_MINI_SECRET:}
```

> 密钥**不要写进仓库**，用环境变量注入。小程序 secret 一旦泄露，任何人都能以你的小程序身份换 openid。

**2）登录与绑定接口**

```java
// backend/.../controller/WxAuthController.java
@RestController
@RequestMapping("/auth")
public class WxAuthController {

    @Resource private WxAuthService wxAuthService;

    /** 小程序静默登录：已绑定直接发 token，未绑定返回 bindTicket */
    @PostMapping("/wx-login")
    public R<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        return R.ok(wxAuthService.loginByCode(body.get("code")));
    }

    /** 首次登录绑定现有账号 */
    @PostMapping("/wx-bind")
    public R<LoginVO> wxBind(@RequestBody WxBindDTO dto) {
        return R.ok("绑定成功", wxAuthService.bind(dto));
    }

    /** 解除绑定（可选） */
    @PostMapping("/wx-unbind")
    public R<Void> wxUnbind() {
        wxAuthService.unbind(UserContext.userId());
        return R.ok();
    }
}
```

`WxAuthService` 的核心逻辑：

```java
public Map<String, Object> loginByCode(String code) {
    if (StrUtil.isBlank(code)) throw BizException.badRequest("缺少 code");
    Map<String, Object> session = wxMiniClient.code2Session(code);
    String openid = StrUtil.toString(session.get("openid"));

    SysUser user = userMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getWxOpenid, openid).eq(SysUser::getDeleted, 0));

    Map<String, Object> result = new HashMap<>();
    if (user != null) {
        if (user.getStatus() != 1) throw new BizException(4003, "账号已被禁用");
        LoginUser lu = buildLoginUser(user);              // 复用现有 AuthService 的组装逻辑
        result.put("needBind", false);
        result.put("token", jwtUtil.generate(lu));
        result.put("user", toUserVO(user));
        result.put("permissions", permissionCache.permsOf(user.getRoleCode()));
        result.put("expiresIn", jwtUtil.getExpireSeconds());
    } else {
        // 未绑定：签发一个 5 分钟有效、type=bind 的临时票据，避免服务端存状态
        String ticket = jwtUtil.generateBindTicket(openid);
        result.put("needBind", true);
        result.put("bindTicket", ticket);
        result.put("openidMasked", StrUtil.hide(openid, 6, openid.length() - 4));
    }
    return result;
}

public LoginVO bind(WxBindDTO dto) {
    String openid = jwtUtil.parseBindTicket(dto.getBindTicket());   // 校验 type=bind 且未过期
    SysUser user = userMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).eq(SysUser::getDeleted, 0));
    if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
        throw new BizException(4002, "账号或密码错误");
    }
    if (user.getWxOpenid() != null && !user.getWxOpenid().equals(openid)) {
        throw new BizException(4004, "该账号已绑定其他微信，请先解绑");
    }
    user.setWxOpenid(openid);
    user.setWxBindTime(LocalDateTime.now());
    userMapper.updateById(user);
    return authService.issueToken(user);   // 复用现有签发逻辑，返回与 /auth/login 完全一致的 LoginVO
}
```

> 用 JWT 做 `bindTicket` 的好处：**不引入 Redis**，无状态、自动过期、无法伪造。`JwtUtil` 加两个方法即可（`generateBindTicket` / `parseBindTicket`，密钥复用 `jwt.secret`，`expire` 设 5 分钟）。
>
> 关键点：`/auth/wx-login` 与 `/auth/wx-bind` 必须加入 `WebConfig` 的 `excludePathPatterns`，因为它们本身是免登录接口。别忘了这一步，否则会 401。

**3）返回体与现有登录保持一致**

`LoginVO` 已经是 `{token, user, permissions, expiresIn}` 结构，微信登录返回同样的结构，前端就能复用同一套 `store.applyAuth()`，不用写第二套登录逻辑。这是"打通"的关键——**不是打通数据库，是打通返回体**。

#### 前端实现

```js
// src/api/modules/auth.js
export const wxLogin = (code) => http.post('/auth/wx-login', { code })
export const wxBind  = (data) => http.post('/auth/wx-bind', data)
```

```js
// src/pages/auth/login.vue 里的微信登录入口
async function loginByWeixin() {
  // #ifdef MP-WEIXIN
  uni.showLoading({ title: '登录中', mask: true })
  try {
    const { code } = await new Promise((resolve, reject) =>
      uni.login({ provider: 'weixin', success: resolve, fail: reject })
    )
    const res = await wxLogin(code)
    if (res.data.needBind) {
      // 未绑定 → 带票据跳绑定页
      uni.redirectTo({ url: `/pages/auth/wx-bind?ticket=${encodeURIComponent(res.data.bindTicket)}` })
      return
    }
    store.applyAuth(res.data)
    uni.reLaunch({ url: '/pages/tabbar/home' })
  } catch (e) {
    // 微信接口异常时降级到账号密码
    toast('微信登录暂不可用，请使用账号密码登录')
    showAccountForm.value = true
  } finally {
    uni.hideLoading()
  }
  // #endif

  // #ifndef MP-WEIXIN
  toast('当前端不支持微信一键登录')
  // #endif
}
```

绑定页 `pages/auth/wx-bind.vue` 只做一件事：收账号密码 + ticket，调 `/auth/wx-bind`，成功后 `applyAuth` + `reLaunch`。

#### App 端微信登录

> **实现状态：已落地（2026-11）。** 与小程序**共用绑定流程，但不共用 appid / openid**。

App 的微信登录与小程序不同：走**开放平台**，后端调 `https://api.weixin.qq.com/sns/oauth2/access_token` 换 `openid/unionid`。
关键差异在于 **openid 是「应用 + 用户」维度的**——同一个人在小程序和 App 里，openid 是两个不同的值。
所以两者必须分列存储，否则会出现「App 登录命中了小程序的绑定关系」这种串号问题：

| 端 | 字段 | 换取接口 | 配置项 |
| --- | --- | --- | --- |
| 小程序 | `sys_user.wx_openid` | `sns/jscode2session` | `app.wx.appid` / `app.wx.secret` |
| App | `sys_user.wx_app_openid` | `sns/oauth2/access_token` | `app.wx.app-appid` / `app.wx.app-secret` |

**两种客户端形态都要兼容**。`uni.login({ provider: 'weixin' })` 在 App 端的返回随 SDK 版本不同：
新版给 `code`（推荐，走 oauth2 换），老版直接给 `authResult.access_token + openid`。
后者**必须**回头找微信校验一次，否则客户端可以伪造任意 openid 直接登录别人的账号：

```js
// store/user.js
async wxAppLogin(loginRes = {}) {
  const auth = loginRes.authResult || {}
  const payload = {
    code: loginRes.code || auth.code || '',
    openid: loginRes.openid || auth.openid || '',
    accessToken: loginRes.accessToken || loginRes.access_token || auth.access_token || ''
  }
  const res = await wxAppLoginApi(payload)
  ...
}
```

服务端 `WxAuthService.verifyAppOpenid()` 在拿到 `openid + accessToken` 时会调 `sns/auth`，返回 `errcode != 0` 一律拒绝。

日志中的 mock 提示同样按端区分：未配置 `app-appid/app-secret` 时 App 端独立 mock（`mock-app-openid-local-demo`），
可以在**没有移动应用资质**的情况下完整验证「App 首次登录 → 绑定 → 直接登录 → 解绑」闭环。

真机上线的前提条件（缺一不可）：

1. 微信开放平台创建**移动应用**，审核通过拿到 `AppID`；
2. `manifest.json` → `app-plus.distribute.sdkConfigs.oauth.weixin` 填 `appid` 和 `UniversalLinks`（iOS 必须）；
3. 云打包时勾选 **OAuth 登录** 模块；
4. iOS 需配置 URL Scheme 与 Universal Links，否则回调回不来。

> 毕设答辩阶段如果开放平台审核没下来，**不影响演示**：App 端保留账号密码入口，微信登录按钮在未配置时走 mock 也能点通。

### 3.3 文件上传三端适配

#### 后端接口现状

```
POST /file/upload        （需 file:upload 权限）
参数：file (MultipartFile)、bizType、bizId
返回：{ id, fileName, filePath, fileHash, fileSize }
```

`filePath` 的形态是 `/file/download/{id}`（`properties.getPublicPrefix() + id` 拼出来的相对路径），**不是完整 URL**。前端展示图片时必须自己拼 origin，且**不能拼 `baseURL`**（因为 `baseURL` 带 `/api` 前缀，而下载接口是 `/file/download/**`，不在 `/api` 下）。

```js
// src/utils/file.js
import { ENV } from '@/config/env'

/** 把后端返回的相对 filePath 转成可直接给 <image src> 的地址 */
export function resolveFileUrl(filePath) {
  if (!filePath) return ''
  if (/^(https?:|blob:|wxfile:)/.test(filePath)) return filePath
  const origin = ENV.baseURL.replace(/\/api$/, '')
  return origin + (filePath.startsWith('/') ? filePath : '/' + filePath)
}
```

#### 三端差异

| 关注点 | H5 | 微信小程序 | App |
| --- | --- | --- | --- |
| 选图 API | `uni.chooseImage` | `uni.chooseImage` | `uni.chooseImage` |
| 临时路径 | `blob:http://...` | `wxfile://tmp_xxx` / `http://tmp/...` | `_doc/uniapp_temp/...` |
| 上传 API | `uni.uploadFile` | `uni.uploadFile` | `uni.uploadFile` |
| 域名白名单 | 无（同域或 CORS） | **必须配 uploadFile 合法域名** | 无 |
| Content-Type | 自动 | **禁止手动设置**，否则 boundary 丢失报错 | 自动 |
| 返回体 | 字符串，需 `JSON.parse` | 字符串，需 `JSON.parse` | 字符串，需 `JSON.parse` |
| 体积限制 | 后端 `maxSize` | 单文件 ≤ 10MB（wx 限制），超限需分片 | 后端 `maxSize` |
| 权限 | 浏览器摄像头/文件权限 | 相机/相册隐私授权（2023 后强制） | 运行时权限（CAMERA / READ_MEDIA_IMAGES） |
| 压缩 | 无 | `sizeType: ['compressed']` 生效 | `sizeType: ['compressed']` 生效 |

#### 统一上传封装

```js
// src/utils/upload.js
import { ENV } from '@/config/env'
import { getToken } from '@/utils/auth'
import { toast } from '@/utils/ui'

/**
 * 上传单个文件
 * @param {object} opts
 * @param {string} opts.filePath              chooseImage / chooseFile 返回的临时路径
 * @param {string} [opts.bizType]             业务类型：planting / harvest / quality ...
 * @param {number} [opts.bizId]               业务主键
 * @param {(p:number)=>void} [opts.onProgress] 进度回调 0-100
 * @returns {Promise<{id,fileName,filePath,fileHash,fileSize}>}
 */
export function uploadFile({ filePath, bizType, bizId, onProgress }) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const task = uni.uploadFile({
      url: `${ENV.baseURL}/file/upload`,
      filePath,
      name: 'file',                                   // 必须与后端 @RequestParam("file") 一致
      formData: {
        ...(bizType ? { bizType } : {}),
        ...(bizId ? { bizId: String(bizId) } : {})
      },
      // 只带 Authorization，绝对不要加 Content-Type（小程序会丢 boundary）
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(res) {
        let body
        try {
          body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        } catch (e) {
          return reject({ code: -2, message: '上传响应解析失败' })
        }
        if (res.statusCode === 401) return reject({ code: 401, message: '登录已失效' })
        if (res.statusCode < 200 || res.statusCode >= 300 || body.code !== 0) {
          const msg = body.message || '上传失败'
          toast(msg)
          return reject(body)
        }
        resolve(body.data)
      },
      fail(err) {
        toast('上传失败，请检查网络')
        reject(err)
      }
    })

    if (onProgress && task && task.onProgressUpdate) {
      task.onProgressUpdate((e) => onProgress(e.progress))
    }
  })
}

/**
 * 批量上传（串行，避免小程序并发限制与内存压力）
 * 小程序 uploadFile 并发上限 10，但串行更稳，且能保证顺序
 */
export async function uploadFiles(paths, { bizType, bizId, onItemProgress } = {}) {
  const results = []
  for (let i = 0; i < paths.length; i++) {
    const r = await uploadFile({
      filePath: paths[i],
      bizType, bizId,
      onProgress: (p) => onItemProgress && onItemProgress(i, p)
    })
    results.push(r)
  }
  return results
}

/** 选择并压缩图片 */
export function chooseImages(count = 3) {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],          // 压缩图，减小上传体积
      sourceType: ['camera', 'album'],
      success: (res) => resolve(res.tempFilePaths),
      fail: (err) => {
        if (/cancel/i.test(err.errMsg || '')) return reject({ cancel: true })
        toast('无法访问相册或相机，请检查权限')
        reject(err)
      }
    })
  })
}
```

#### `photo-uploader.vue` 组件（替代 `van-uploader`）

原 `planting-form.vue` / `harvest.vue` 用的是 `van-uploader` + `after-read`，迁移后统一用这个组件，内部封装了选图、上传、进度、删除、失败重试：

```vue
<template>
  <view class="uploader">
    <view v-for="(f, i) in list" :key="i" class="item">
      <image :src="f.url" mode="aspectFill" class="thumb" @click="preview(i)" />
      <view v-if="f.status === 'uploading'" class="mask">{{ f.progress }}%</view>
      <view v-if="f.status === 'error'" class="mask error" @click="retry(i)">重试</view>
      <view class="del" @click="remove(i)">×</view>
    </view>
    <view v-if="list.length < max" class="add" @click="pick">
      <text class="plus">+</text>
      <text class="tip">{{ tip }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { chooseImages, uploadFile } from '@/utils/upload'
import { toast } from '@/utils/ui'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 3 },
  bizType: String,
  bizId: [Number, String],
  tip: { type: String, default: '拍照上传' }
})
const emit = defineEmits(['update:modelValue', 'change'])
const list = ref([])

watch(() => props.modelValue, (v) => {
  if (v && v.length !== list.value.length) {
    list.value = v.map((x) => ({ url: x.filePath, ...x, status: 'done', progress: 100 }))
  }
}, { immediate: true })

async function pick() {
  try {
    const paths = await chooseImages(props.max - list.value.length)
    for (const p of paths) {
      const item = { url: p, status: 'uploading', progress: 0 }
      list.value.push(item)
      const idx = list.value.length - 1
      try {
        const res = await uploadFile({
          filePath: p,
          bizType: props.bizType,
          bizId: props.bizId,
          onProgress: (pg) => { list.value[idx].progress = pg }
        })
        list.value[idx] = { ...res, url: p, status: 'done', progress: 100 }
      } catch (e) {
        list.value[idx].status = 'error'
      }
    }
    sync()
  } catch (e) {
    if (!e.cancel) console.warn(e)
  }
}

function remove(i) { list.value.splice(i, 1); sync() }
function preview(i) { uni.previewImage({ current: i, urls: list.value.map((x) => x.url) }) }
function retry(i) { list.value[i].status = 'pending'; pick() }
function sync() { emit('update:modelValue', list.value.filter((x) => x.status === 'done')); emit('change', list.value) }

/** 供父组件在提交前校验：是否还有未完成的上传 */
defineExpose({ isUploading: () => list.value.some((x) => x.status === 'uploading') })
</script>
```

父页面提交前必须检查：

```js
const uploader = ref(null)
async function submit() {
  if (uploader.value?.isUploading()) return toast('照片仍在上传，请稍候')
  // ...提交
}
```

这与原 `planting-form.vue` 第 252 行的逻辑完全一致（原文："照片仍在上传，请稍候"），行为不变。

#### 三个必须注意的坑

**坑 1：小程序 uploadFile 域名白名单**

微信公众平台 → 开发 → 开发管理 → 服务器域名 → **uploadFile 合法域名**，填 `https://api.agri-origin.com`。注意：

- `request` 和 `uploadFile` 是**两套独立白名单**，只配 request 会导致上传失败；
- 域名必须备案、必须 https、不能带端口、不能是 IP；
- 开发期可在开发者工具勾"不校验合法域名"，但**真机预览时该选项无效**，必须配好域名或用内网穿透。

**坑 2：小程序隐私协议（2023-09 起强制）**

调用 `chooseImage`、`chooseMedia`、`getLocation` 等接口前，若用户未同意隐私协议，微信会直接拒绝。处理方式：

```js
// App.vue onLaunch 或页面 onLoad
// #ifdef MP-WEIXIN
if (wx.requirePrivacyAuthorize) {
  wx.requirePrivacyAuthorize({
    success: () => {},
    fail: () => uni.navigateTo({ url: '/pages-mp/privacy/index' })
  })
}
// #endif
```

同时必须在**微信公众平台 → 设置 → 服务内容声明 → 用户隐私保护指引**里勾选"摄像头""相册""位置信息"，否则审核不通过。

**坑 3：App 端大图 OOM**

App 端连续上传 10 张 5MB 原图，低端安卓机容易内存溢出闪退。必须：

- `sizeType: ['compressed']`；
- 串行上传（`uploadFiles` 已实现）；
- 上传完成及时释放临时文件（App 端可调 `plus.io.resolveLocalFileSystemURL` 删除，非必须）。

### 3.4 多端数据一致性设计

多端操作同一份数据，冲突不可避免。分三层治理：**写入防冲突、读取防过期、缓存防脏**。

#### 第一层：写入防冲突（乐观锁）

场景：村管在 PC 上审核批次 A，同时农户在手机上编辑批次 A。后提交的会覆盖前者。

> **实现状态：已落地（2026-11）。** 实际只给「会被覆盖写」的三张表加了版本号，而不是所有表都加，理由见下。

```sql
-- 实际落地文件：sql/patch-optimistic-lock.sql（幂等；init.sql 已同步）
ALTER TABLE `biz_batch`              ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `update_time`;
ALTER TABLE `biz_abnormal_disposal`  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `create_time`;
ALTER TABLE `base_farm`              ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `status`;
```

**为什么只选这三张表**：

| 表 | 是否会"后写覆盖前写" | 结论 |
| --- | --- | --- |
| `biz_batch` | 会。核验 / 采收 / 质检 / 物流 / 销售 / 结案都在改同一行的 `status`，是并发热点 | **加** |
| `biz_abnormal_disposal` | 会。同一异常可能被两个人同时点「提交处置」 | **加** |
| `base_farm` | 会。PC 端多人同时编辑同一地块档案 | **加** |
| `biz_planting_record` / `biz_harvest_record` / `biz_logistics_record` / `biz_sales_record` / `chain_record` | 不会。这些是**只追加**的流水，一条记录创建后不再被其他人改 | 不加 |
| `sys_user` / `base_product` | 低频后台操作，且冲突时"后写覆盖"就是管理员的真实意图 | 不加 |

给只追加的表加版本号只会带来无谓的 409，反而降低可用性——**乐观锁该用在该用的地方**。

MyBatis-Plus 自带支持，实体加注解即可：

```java
@Version
private Integer version;
```

配置插件（`MybatisPlusConfig`，顺序遵循官方建议：分页 → 乐观锁 → 防全表更新）：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    pagination.setMaxLimit(200L);
    interceptor.addInnerInterceptor(pagination);
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    return interceptor;
}
```

实际生成的 SQL 会多出两处（`backend.log` 里可以抓到原句）：

```sql
UPDATE biz_batch SET ..., version=? WHERE id=? AND version=? AND deleted=0
```

**坑：`updateById` 返回 0 不抛异常。** 这是最容易踩的地方——不检查返回值的话，并发冲突被静默吞掉，
比不加锁更危险（因为它给了人"已经加锁"的错觉）。所以统一走一个断言：

```java
// common/Db.java
public static void updated(int rows) {
    if (rows == 0) throw BizException.stale();   // 409 该记录已被他人修改，请刷新后重新操作
}
```

```java
Db.updated(batchMapper.updateById(batch));       // 影响 0 行 → 409
```

**另一个坑：先落库还是先上链。** `AbnormalService.dispose()` 原来先调合约再写库，
一旦版本冲突回滚，链上已经留下一条重复的「处置完成」存证——而链上记录删不掉。
所以改成**先落库做版本比对，再上链**，冲突的人在写库这步就被拦下，链上不会脏。`close()` 同理。

> **已知取舍**：采收 / 质检 / 物流 / 销售四条流程仍是「先上链再改批次状态」。
> 它们的子记录本身是追加写，冲突只可能发生在批次状态那一行；极端并发下会在链上多出一条对应记录。
> 这四条流程的改造会牵动 digest 计算顺序，收益（仅限并发场景）与回归风险不成比例，因此保持现状并在文档中标注。

前端处理（`utils/request.js` 已统一，业务页面通常不用写）：

```js
try {
  await auditBatch(id, { auditStatus, auditRemark })
} catch (e) {
  if (e.code === 409) {
    const ok = await confirm('数据已被其他端修改，是否刷新查看最新内容？')
    if (ok) loadList()
  }
}
```

实测（60 并发同时核验同一批次）：

```
HTTP 状态: {"200":1,"409":59}
消息分布: { "操作成功": 1, "该批次已核验，请勿重复操作": 54, "该记录已被他人修改，请刷新后重新操作": 5 }
```

其中 5 次**通过了状态前置校验、最终被版本号拦住**，证明乐观锁确实在兜底，而不只是个摆设：

| 防线 | 拦住次数 | 说明 |
| --- | --- | --- |
| 业务状态前置校验（`auditStatus != PENDING`） | 54 | 读到的已经是别人提交后的状态 |
| 乐观锁版本比对（`AND version=?`） | 5 | 读的时候还是 PENDING，写的时候已经被改 |

> 更轻量的替代方案：用 `update_time` 做 CAS（`WHERE id=? AND update_time=?`）。不用改表结构，但精度只到秒，并发极高时会漏判。毕设用 `version` 更规范，答辩也好讲。

#### 第二层：读取防过期

| 场景 | 规则 |
| --- | --- |
| 页面首次进入 | `onLoad` 拉数据 |
| 从其他页面返回 | `onShow` 判断 `needRefresh` 标记，为 true 则重拉 |
| 下拉刷新 | 强制重拉，清空分页 |
| 应用从后台恢复 | `App.vue onShow` 里若距上次同步 > 5 分钟，标脏当前页 |
| 写操作成功 | 主动给相关列表打脏标记，返回时触发刷新 |

`store/cache.js` 实现脏标记：

```js
// src/store/cache.js
import { defineStore } from 'pinia'

export const useCacheStore = defineStore('cache', {
  state: () => ({
    dirty: {},           // { 'batch:list': true }
    lastSyncAt: {}       // { 'batch:list': 1730000000000 }
  }),
  actions: {
    markDirty(key) { this.dirty[key] = true },
    markFresh(key) { this.dirty[key] = false; this.lastSyncAt[key] = Date.now() },
    isDirty(key) { return !!this.dirty[key] },
    /** 超过 maxAgeMs 视为过期 */
    isStale(key, maxAgeMs = 5 * 60 * 1000) {
      const t = this.lastSyncAt[key]
      return !t || Date.now() - t > maxAgeMs
    }
  }
})
```

列表页统一接入（`composables/useList.js` 已封装）：

```js
onShow(() => {
  if (cache.isDirty('batch:list') || cache.isStale('batch:list')) {
    reload()
  }
})
```

#### 第三层：缓存策略

**只缓存"可容忍短暂不一致"的数据**，关键业务数据永远以服务端为准。

| 数据 | 是否缓存 | 存储位置 | TTL | 失效时机 |
| --- | --- | --- | --- | --- |
| token / 用户信息 / 权限码 | 是 | `uni.setStorageSync` | 跟随 token | 退出登录、401 |
| 字典（产品品类、批次状态） | 是 | storage | 24h | 手动清缓存 |
| 农场列表、村庄列表 | 是 | storage | 1h | 新增/编辑后 |
| 批次列表分页 | 否 | 页面内存 | — | 每次 `onShow` 重拉 |
| 批次详情 | 否 | 页面内存 | — | 每次进入重拉 |
| 溯源结果（消费者查） | 是 | storage | 10min | 手动刷新 |
| 统计数据（看板） | 是 | storage | 2min | 定时刷新 |

实现要点：**不要给列表做本地持久化缓存**。小程序 storage 只有 10MB，且列表数据变更频繁，缓存带来的"看起来是旧数据"的问题远大于省下的那点请求。

#### 第四层：幂等（防重复提交）

三端都可能因为网络抖动导致"提交成功但前端以为失败，用户再点一次"。

轻量方案（无需 Redis）：

1. **前端按钮防重**：`useForm` 里 `submitting` 标记 + 300ms 节流；
2. **请求去重**：`utils/request.js` 的 `pending` Map 已实现——相同 method+url+body 在未返回前不会发第二次；
3. **后端唯一约束兜底**：`biz_batch.batch_no` 已有唯一索引，重复创建会抛 `DuplicateKeyException`，全局异常处理器转成 `4091 该批次已存在`。

如果要做严格的分布式幂等，再引入 `clientRequestId` + 数据库唯一索引表：

```sql
CREATE TABLE `sys_request_log` (
  `request_id` VARCHAR(64) NOT NULL COMMENT '前端生成的 UUID',
  `user_id` BIGINT DEFAULT NULL,
  `api` VARCHAR(128) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_id`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 多端同时登录的取舍

| 策略 | 实现成本 | 体验 | 建议 |
| --- | --- | --- | --- |
| **允许多端同时在线**（现状） | 0 | 三端数据实时一致，最方便 | **推荐**，毕设与内部系统都够用 |
| 后登录踢掉先登录 | 需 Redis 存 `userId → tokenId`，每次请求比对 | 用户在手机和电脑间切换会被频繁踢 | 不建议 |
| 限制同时在线设备数 | 需 Redis ZSET | 适合付费账号场景 | 不需要 |

> 现有 JWT 无状态设计天然支持"多端同时在线"，这正好满足你"三端数据完全同步"的要求，**不要额外加单点限制**。

### 3.5 WebSocket 统一接入

> **实现状态：已落地（2026-11）。** 采用 Spring 原生 `@EnableWebSocket` + `TextWebSocketHandler`（而非本节草稿里的 `@ServerEndpoint`，两者都能用，前者能与 Spring 的依赖注入、握手拦截器自然配合）：
>
> | 项 | 实际值 |
> | --- | --- |
> | 端点 | `ws://<host>/api/ws?token=<JWT>`（注意 context-path `/api` 不能丢） |
> | 服务端 | `config/WebSocketConfig.java` + `websocket/TraceWebSocketHandler.java` + `websocket/WsHandshakeInterceptor.java` |
> | 握手鉴权 | 解析 query 里的 `token`，失败直接拒绝握手（H5 / 小程序无法带 header，只能走 query） |
> | 心跳 | 客户端发文本 `ping`，服务端回文本 `pong` |
> | 下行消息 | `{"type":"batch|abnormal|chain","batchNo":"...","action":"...","message":"...","timestamp":1712...}` |
> | 广播触发点 | `ChainService.saveRecord`（每次存证）、`BatchService.audit`（核验）、`AbnormalService.dispose/close`（处置 / 结案） |
> | 前端接收 | `App.vue` 订阅 → 标记列表缓存为脏 + `uni.$emit('realtime:changed')` → `composables/useRealtime.js` 供各列表页订阅后立即刷新 |
> | 已验证 | PC 列表页保持不动，外部接口核验批次后 3 秒内该行"审核"按钮自动消失（无需刷新） |
>
> 踩坑记录（已在代码里修正）：原 `utils/socket.js` 计算地址时 `.replace(/\/api$/,'')` 把上下文路径删掉了，非 H5 端会连到 `/ws` 而非 `/api/ws`；H5 端还需要 `vite.config.js` 的 `/api` 代理加 `ws: true` 才能转发升级请求。

#### 先说结论

现有后端**没有** WebSocket。如果只是毕设演示，用**轮询**（30 秒拉一次未读数）成本最低；如果要做"异常批次实时告警"，再上 WebSocket。下面给完整的统一封装，接入成本不高。

#### 三端的关键差异

| 端 | 协议 | 自定义 header | 域名要求 |
| --- | --- | --- | --- |
| H5 | `ws://` / `wss://` | **不支持**（浏览器 WebSocket API 无 header 参数） | https 页面必须用 wss，否则混合内容被拦 |
| 微信小程序 | `wss://` 强制 | **不支持** | 必须配 socket 合法域名，不能带端口 |
| App | `ws://` / `wss://` | 支持 | 无 |

**因为 H5 和小程序都不能带 header，token 只能走 query 参数**：`wss://api.agri-origin.com/ws?token=xxx`。这是三端统一的前提。

> 安全提示：token 出现在 URL 里会被 nginx 访问日志记录。生产环境建议改成「先用 https 接口换一个一次性 ticket，再用 ticket 连 ws」，毕设阶段可直接用 token。

#### 后端最小实现

先在 `pom.xml` 加依赖（现有 `pom.xml` 没有 websocket starter）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

```java
// backend/.../config/WebSocketConfig.java
@Configuration
public class WebSocketConfig {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
```

```java
// backend/.../ws/NotifyEndpoint.java
@Component
@ServerEndpoint("/ws")
public class NotifyEndpoint {

    private static final Map<Long, Set<Session>> SESSIONS = new ConcurrentHashMap<>();
    private static JwtUtil jwtUtil;

    @Autowired
    public void setJwtUtil(JwtUtil util) { NotifyEndpoint.jwtUtil = util; }

    private Long userId;

    @OnOpen
    public void onOpen(Session session) {
        String token = queryParam(session, "token");
        try {
            userId = jwtUtil.parse(token).getUserId();
        } catch (Exception e) {
            try { session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "invalid token")); } catch (Exception ignore) {}
            return;
        }
        SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @OnClose
    public void onClose(Session session) {
        Set<Session> set = SESSIONS.get(userId);
        if (set != null) { set.remove(session); if (set.isEmpty()) SESSIONS.remove(userId); }
    }

    @OnMessage
    public void onMessage(String msg, Session session) {
        if ("ping".equals(msg)) {           // 心跳应答，防 nginx 60s 断连
            try { session.getBasicRemote().sendText("pong"); } catch (Exception ignore) {}
        }
    }

    @OnError
    public void onError(Session session, Throwable t) { /* log */ }

    /** 给指定用户推送 */
    public static void sendTo(Long userId, String json) {
        Set<Session> set = SESSIONS.get(userId);
        if (set == null) return;
        set.forEach(s -> { try { s.getBasicRemote().sendText(json); } catch (Exception ignore) {} });
    }

    /** 广播（异常批次告警给所有监管角色） */
    public static void broadcast(String json) {
        SESSIONS.values().forEach(set ->
            set.forEach(s -> { try { s.getBasicRemote().sendText(json); } catch (Exception ignore) {} }));
    }

    private String queryParam(Session session, String key) {
        String q = session.getQueryString();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2 && p[0].equals(key)) return URLDecoder.decode(p[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
```

注意 `AuthInterceptor` 会拦截 `/ws` 并尝试解析 header 里的 token（ws 握手请求没有 Authorization），必须把 `/ws` 加入 `excludePathPatterns`，鉴权交给 `onOpen` 自己做。

#### 前端统一封装

```js
// src/utils/socket.js
import { ENV } from '@/config/env'
import { getToken } from '@/utils/auth'

class SocketClient {
  constructor() {
    this.task = null
    this.connected = false
    this.listeners = new Map()   // event -> Set<fn>
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.reconnectCount = 0
    this.manualClose = false
  }

  get url() {
    const base = ENV.baseURL.replace(/^http/, 'ws').replace(/\/api$/, '')
    return `${base}/ws?token=${encodeURIComponent(getToken())}`
  }

  connect() {
    if (this.connected || this.task) return
    this.manualClose = false
    this.task = uni.connectSocket({
      url: this.url,
      complete: () => {}
    })

    this.task.onOpen(() => {
      this.connected = true
      this.reconnectCount = 0
      this.startHeartbeat()
      this.emit('open')
    })

    this.task.onMessage((res) => {
      if (res.data === 'pong') return
      let msg
      try { msg = JSON.parse(res.data) } catch (e) { return }
      this.emit(msg.type || 'message', msg)
    })

    this.task.onClose(() => {
      this.connected = false
      this.task = null
      this.stopHeartbeat()
      this.emit('close')
      if (!this.manualClose) this.scheduleReconnect()
    })

    this.task.onError(() => {
      this.connected = false
      this.task = null
      this.stopHeartbeat()
      if (!this.manualClose) this.scheduleReconnect()
    })
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return
    // 指数退避：1s, 2s, 4s, 8s, 最多 30s
    const delay = Math.min(1000 * Math.pow(2, this.reconnectCount++), 30000)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.connected && this.task) {
        this.task.send({ data: 'ping' })
      }
    }, 25000)   // 小于 nginx 默认 60s 超时
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) { clearInterval(this.heartbeatTimer); this.heartbeatTimer = null }
  }

  on(event, fn) {
    if (!this.listeners.has(event)) this.listeners.set(event, new Set())
    this.listeners.get(event).add(fn)
    return () => this.off(event, fn)
  }

  off(event, fn) {
    const set = this.listeners.get(event)
    if (set) set.delete(fn)
  }

  emit(event, payload) {
    const set = this.listeners.get(event)
    if (set) set.forEach((fn) => { try { fn(payload) } catch (e) { console.error(e) } })
  }

  close() {
    this.manualClose = true
    this.stopHeartbeat()
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null }
    if (this.task) { this.task.close({}); this.task = null }
    this.connected = false
  }
}

export const socket = new SocketClient()
```

页面里使用：

```js
// 异常告警页
import { socket } from '@/utils/socket'

let off = null
onLoad(() => {
  socket.connect()
  off = socket.on('abnormal', (msg) => {
    toast(`新异常：${msg.data.batchNo}`)
    reload()
  })
})
onUnload(() => { off && off() })
```

`App.vue` 里统一管理生命周期：

```js
onShow() { if (useUserStore().token) socket.connect() },
onHide() { socket.close() }     // 只在前台保活，省电省流量
```

#### 消息格式约定

```json
{ "type": "abnormal", "data": { "batchId": 2, "batchNo": "AGRI20241101002", "level": "HIGH", "message": "农残检测超标" }, "ts": 1730000000000 }
```

`type` 取值：`abnormal`（异常告警）、`audit`（审核结果）、`chain`（上链完成）、`system`（系统公告）。前端按 `type` 订阅，未知类型走默认 `message` 通道。

---

## 四、各端调试、打包与发布全流程

### 4.1 三端开发调试

#### 4.1.1 H5 端

**CLI 方式（推荐）**

```bash
cd frontend/uni-app
npm run dev:h5
# 默认 http://localhost:5173
```

**HBuilderX 方式**：文件 → 导入 → 从本地目录导入（选 `frontend/uni-app`）→ 运行 → 运行到浏览器 → Chrome。

前置条件：后端已在 `localhost:8080` 启动。确认方式：

```bash
curl http://localhost:8080/api/trace/AGRI20241101001
```

Vite 代理配置（`vite.config.js`）：

```js
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'path'

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',          // 允许手机连同一局域网调试
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
        // 后端接口本身带 /api 前缀，不需要 rewrite
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 注意：uni.scss 由 uni 编译器自动注入到每个 <style lang="scss">，不要在这里重复 @import，
        // 否则变量会被定义两次（部分 sass 版本会告警甚至报错）。
        // 这里只注入额外的 mixin / function 文件。
        additionalData: '@import "@/styles/mixins.scss";'
      }
    }
  }
})
```

> 如果你更习惯普通 Vue 项目那套写法，也可以把变量全部放进 `styles/variables.scss` 并用 `additionalData` 注入，但**不要同时**保留 `src/uni.scss` 里的同名变量，否则后注入的会覆盖前者，出现"改了变量不生效"的怪现象。

**H5 调试常见报错**

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 页面白屏，控制台报 `Cannot read property 'createElement' of undefined` | 引用了只在浏览器可用的库，但被 SSR 预渲染 | 组件内动态 `import()`，或加 `#ifdef H5` |
| `Failed to resolve import "@/xxx"` | 别名没配 | 检查 `vite.config.js` alias + `jsconfig.json` paths |
| 接口 404 | `ENV.baseURL` 拼错（`/api/api/...`） | 后端接口自带 `/api`，`ENV.baseURL` 不要再加一层 |
| 接口 CORS 报错 | 直接请求了 `http://localhost:8080` 而非走代理 | 开发环境 `VITE_API_BASE` 留空，走 `/api` 相对路径 |
| 刷新页面 404 | history 模式 + 无 nginx 回退 | 开发无影响；生产见 §4.2 |
| `rpx` 在宽屏巨大 | 没配 `rpxCalcMaxDeviceWidth` | 见 §1.5.1 |
| 热更新失效 | 改了 `pages.json` / `manifest.json` | 这两个文件改动需要重启 dev 服务 |

#### 4.1.2 微信小程序端

**第一步：编译**

```bash
npm run dev:mp-weixin
# 产物：dist/dev/mp-weixin（watch 模式，改代码自动重编）
```

**第二步：导入开发者工具（最容易错的一步）**

打开微信开发者工具 → 导入项目 → **目录必须选 `dist/dev/mp-weixin`**，不是 `frontend/uni-app`，也不是 `src`。

> 选错目录的症状：工具里看不到页面、报"app.json 未找到"、空白工程。

**第三步：填 AppID**

两种方式：
- 在 `src/manifest.json` → `mp-weixin.appid` 里填，重新编译；
- 在开发者工具"详情 → 基本信息 → AppID"里改（仅当次生效，重编会覆盖）。

如果没有 AppID，可点"测试号"，但**测试号无法真机预览、无法上传**。

**第四步：关闭域名校验（仅开发）**

开发者工具 → 详情 → 本地设置 → 勾选：

- ☑ 不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书

真机预览时该选项**不生效**，必须配好域名或用内网穿透（ngrok / cpolar / frp）。

**第五步：调试技巧**

| 技巧 | 用法 |
| --- | --- |
| 直接启动到指定页 | `pages.json` 的 `condition` 已配好，工具顶部"普通编译"下拉切换 |
| 看 `console.log` | 工具 Console 面板（`uni` 的日志也在里面） |
| 看请求 | Network 面板，注意 `uploadFile` 单独一类 |
| 看 storage | Storage 面板，key 带 `agri_` 前缀 |
| 真机调试 | 工具"真机调试"扫码，可看 vConsole |
| 自定义编译模式 | 传参调试详情页：`pages/batch/detail` + `id=1` |

**小程序常见报错**

| 报错 | 原因 | 解决 |
| --- | --- | --- |
| `request:fail url not in domain list` | 域名未配 / 未勾"不校验" | 配 request 合法域名，或开发期勾选 |
| `uploadFile:fail url not in domain list` | 只配了 request 域名 | 单独配 **uploadFile 合法域名** |
| `errMsg: "url not in domainList"` 但域名已配 | 域名带端口 / 用了 IP / 非 https | 域名不能带端口、不能是 IP |
| `module "xxx" is not defined` | 用了 npm 包但没"构建 npm" | 工具 → 工具 → 构建 npm（uni-app 编译型项目一般不需要，除非用了原生 npm 包） |
| `Component is not found in path "wd-button"` | easycom 规则没配 / 组件未安装 | 检查 `pages.json` easycom + `node_modules/wot-design-uni` |
| `Some selectors are not allowed in component wxss` | 用了 `*` 或属性选择器 | 改类选择器 |
| 页面能显示但样式全乱 | 用了 `div`/`span` 等非 uni 标签 | 全局搜索替换 |
| `wx.xxx is not a function` | 基础库版本低 | 工具"详情 → 本地设置 → 调试基础库"调高 |
| 包体积超 2MB | 主包太大 | 分包 + 图片放 CDN |
| 真机白屏，工具正常 | 用了 `document`/`window` 等浏览器 API | 加 `#ifdef H5` 或改用 uni API |

#### 4.1.3 App 端

**第一步：HBuilderX 打开项目**

HBuilderX → 文件 → 导入 → 从本地目录导入 → 选 `frontend/uni-app`。

CLI 项目用 HBuilderX 打开后，HBuilderX 会自动识别 `src` 下的 `manifest.json`，**不要手动移动文件**。

**第二步：配置 `manifest.json` 基础项**

- `appid`：点"重新获取"生成 DCloud AppID（云打包必需）；
- `name`：`农安链溯源`；
- `versionName` / `versionCode`：`1.0.0` / `100`。

**第三步：运行到手机**

方式一（标准基座，最快）：运行 → 运行到手机或模拟器 → 运行到 Android App 基座 / iOS App 基座。

前提：
- Android：手机开"开发者选项 → USB 调试"，HBuilderX 能识别设备；
- iOS：需 macOS + Xcode，或用 iOS 真机调试证书（Windows 上只能云打包后安装）。

方式二（自定义基座，用到原生插件时必须）：
运行 → 运行到手机或模拟器 → **制作自定义调试基座** → 选 Android → 打包 → 完成后运行到自定义基座。

> 本项目不需要原生插件（相机、扫码都是 uni 内置模块），**用标准基座即可**，省 20 分钟。

**第四步：调试**

- HBuilderX 控制台看 `console.log`；
- App 端可用 `chrome://inspect` 调试 WebView（Android）；
- 真机日志：HBuilderX → 视图 → 显示终端 → `adb logcat`。

**App 常见报错**

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 运行到手机提示"未检测到设备" | 驱动/授权问题 | 装手机厂商 USB 驱动；手机弹窗点"允许调试" |
| App 启动白屏 | `manifest` 里 `app-plus` 配置错误，或首页路径不存在 | 检查 `pages.json` 首页；看 `adb logcat` |
| 相机/相册打不开 | 权限未声明 / 用户拒绝 | 补 `manifest` permissions + 运行时引导 |
| 扫码无反应 | 未勾选 Barcode 模块 | `manifest` → App 模块配置 → 勾选 Barcode |
| `plus is not defined` | 代码在 H5/小程序执行了 App 专属 API | 加 `#ifdef APP-PLUS` |
| iOS 请求失败 | ATS 限制 http | 全部用 https，或配 `NSAppTransportSecurity` |
| 热更新不生效 | 改了原生配置 | 原生配置改动必须重新打包 |

### 4.2 H5 打包与部署

#### 打包

```bash
npm run build:h5
# 产物：dist/build/h5
```

`dist/build/h5` 内容：

```
dist/build/h5/
├── index.html
├── assets/            # js / css / 字体
├── static/            # 图片等静态资源
└── favicon.ico
```

#### 与原 Vue 项目部署的区别

| 项 | 原 pc-admin（Vite） | uni-app H5 |
| --- | --- | --- |
| 产物目录 | `dist/` | `dist/build/h5/` |
| 路由 | `createWebHistory()` | `manifest.json` → `h5.router.mode` |
| 基路径 | `vite.config.js` 的 `base` | `manifest.json` → `h5.router.base`（**不是 vite base**） |
| 入口 HTML | 项目根的 `index.html` | 编译生成，源模板仍是根 `index.html` |
| 样式单位 | px | rpx 已编译成 `calc()` / vw，产物里看不到 rpx |
| 环境变量 | `.env.*` | 同，但 `mode` 由 uni CLI 控制 |

#### 路由模式选择

```jsonc
// manifest.json
"h5": {
  "router": {
    "mode": "history",     // 或 "hash"
    "base": "/"
  }
}
```

| 模式 | URL 形态 | 优点 | 缺点 | 适用 |
| --- | --- | --- | --- | --- |
| `history` | `https://x.com/batch/detail?id=1` | 美观、利于分享与 SEO | 需 nginx `try_files` 回退，否则刷新 404 | **推荐**，有 nginx 就用 |
| `hash` | `https://x.com/#/pages/batch/detail?id=1` | 零配置，纯静态托管即可 | URL 丑，微信分享有时被截断 | 部署到 GitHub Pages / OSS 静态托管 |

**history 模式必须配 nginx 回退**，否则用户刷新详情页直接 404：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

#### 二级目录部署

比如部署到 `https://example.com/agri/`：

1. `manifest.json` → `h5.router.base` 设为 `/agri/`；
2. 重新打包；
3. nginx：

```nginx
# 推荐用 root，不要用 alias
root /var/www;

location /agri/ {
    try_files $uri $uri/ /agri/index.html;
}
```

> **为什么不用 `alias`**：`alias` 与 `try_files` 组合有已知的路径拼接问题——`$uri` 已包含 `/agri/` 前缀，再与 `alias /var/www/agri/` 拼接会变成 `/var/www/agri/agri/...`，表现是"首页能开、刷新内页 404"。用 `root /var/www` 让 nginx 自然拼成 `/var/www/agri/...` 才是稳的。
>
> 如果必须用 `alias`，回退目标要写成 `try_files $uri $uri/ /agri/index.html;` 并在 `location` 末尾不加 `/`，同时实测刷新内页——不同 nginx 版本行为不一致，建议直接避开。

#### 完整 nginx 配置（推荐直接抄）

```nginx
server {
    listen 80;
    server_name agri-origin.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name agri-origin.com;

    ssl_certificate     /etc/nginx/ssl/agri.pem;
    ssl_certificate_key /etc/nginx/ssl/agri.key;

    # ---------- H5 前端 ----------
    root /var/www/agri;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;      # history 模式必须
    }

    # 静态资源长缓存（文件名带 hash，可放心缓存）
    location ~* \.(js|css|png|jpg|jpeg|gif|svg|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # ---------- 后端接口 ----------
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 20m;              # 上传大图必需，默认 1m 会 413
    }

    # 上传文件预览（后端 /file/download/**，不带 /api）
    location /file/ {
        proxy_pass http://127.0.0.1:8080/file/;
    }

    # WebSocket
    location /ws {
        proxy_pass http://127.0.0.1:8080/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;               # 大于前端 25s 心跳间隔
    }
}
```

> **`client_max_body_size 20m` 是最容易漏的一行**。漏了它，前端上传 5MB 的质检报告会收到 413，而且后端日志里什么都没有（请求没到后端就被 nginx 拒了）。

#### 跨域处理

| 场景 | 方案 |
| --- | --- |
| 开发环境 | `vite.config.js` 的 `server.proxy` |
| 生产同域部署 | nginx 反代 `/api`，**不产生跨域**（推荐） |
| 生产分离部署（前端 CDN + 后端独立域名） | 后端 `WebConfig.addCorsMappings` 已放开 `allowedOriginPatterns("*")`，可直接用；生产建议收紧到具体域名 |
| 小程序 / App | 无浏览器同源策略，**不存在跨域** |

> 现有 `WebConfig` 里配的是 `allowedOriginPatterns("*")` + `allowCredentials(true)`，这个组合是合法的（用 `patterns` 而非 `origins` 时 Spring 允许）。但如果将来改成 `allowedOrigins("*")`，启动会直接抛异常。

### 4.3 微信小程序完整发布流程

#### 第一步：准备资质与配置

| 项 | 要求 | 在哪配 |
| --- | --- | --- |
| 小程序 AppID | 已注册的企业/个人小程序 | 微信公众平台 → 开发 → 开发管理 → 开发设置 |
| 小程序类目 | 建议「工具 → 信息查询」或「生活服务 → 其他」 | 设置 → 基本设置 → 服务类目 |
| 用户隐私保护指引 | 必须声明：摄像头、相册、位置 | 设置 → 服务内容声明 → 用户隐私保护指引 |
| 服务器域名 | 见下表 | 开发 → 开发管理 → 开发设置 → 服务器域名 |

**服务器域名四类都要配**（漏一个就有一个功能不可用）：

| 类型 | 填什么 | 用在哪 |
| --- | --- | --- |
| request 合法域名 | `https://api.agri-origin.com` | `uni.request` |
| socket 合法域名 | `wss://api.agri-origin.com` | `uni.connectSocket` |
| uploadFile 合法域名 | `https://api.agri-origin.com` | `uni.uploadFile` |
| downloadFile 合法域名 | `https://api.agri-origin.com` | `uni.downloadFile`（下载质检报告 PDF） |

域名要求：**已备案、https、不能带端口、不能是 IP、每月只能改 5 次**。

#### 第二步：配置 `manifest.json`

```jsonc
"mp-weixin": {
  "appid": "wx1234567890abcdef",
  "setting": {
    "urlCheck": true,        // 生产必须 true
    "es6": true,
    "minified": true,
    "postcss": true
  },
  "usingComponents": true,
  "permission": {
    "scope.userLocation": {
      "desc": "用于记录农事操作与采收地点，作为溯源信息的一部分"
    }
  },
  "requiredPrivateInfos": ["getLocation", "chooseLocation"],
  "lazyCodeLoading": "requiredComponents"
}
```

> `permission.scope.userLocation.desc` 必须写清楚用途，且要跟隐私指引里填的一致，否则审核会以"用途描述不明确"驳回。

#### 第三步：上传代码

**方式一（HBuilderX）**：发行 → 小程序-微信（仅适用于微信小程序）→ 填 AppID → 上传 → 填版本号与备注。

**方式二（开发者工具）**：先 `npm run build:mp-weixin` → 开发者工具导入 `dist/build/mp-weixin` → 点"上传" → 填版本号 `1.0.0`、项目备注。

版本号规范建议：`主版本.次版本.修订号`，例如 `1.0.0`、`1.0.1`。**微信后台的版本号与 `manifest.json` 的 `versionName` 无关**，以开发者工具里填的为准。

#### 第四步：设为体验版并自测

公众平台 → 管理 → 版本管理 → 开发版本 → 选刚上传的版本 → "选为体验版" → 生成体验二维码。

体验版必测清单：

- [ ] 账号密码能登录
- [ ] 微信一键登录能走通（首次绑定 / 二次直接进）
- [ ] 扫码能跳溯源页
- [ ] 拍照上传能成功（这条最容易因为 uploadFile 域名失败）
- [ ] 批次详情时间轴完整
- [ ] 下拉刷新、上拉加载正常
- [ ] 退出登录后重进需要重新登录
- [ ] 弱网（工具里限速）下不白屏

#### 第五步：提交审核

版本管理 → 开发版本 → "提交审核"，必填：

| 字段 | 填法 |
| --- | --- |
| 功能页面 | 选 3～5 个主要页面，如首页、批次详情、溯源查询 |
| 标题 | `农安链溯源` |
| 服务类目 | 与基本设置一致 |
| 标签 | `溯源`、`农产品`、`区块链`（不要滥用） |
| 测试账号 | **必填**：`farmer01` / `123456`，并在备注里写"评审老师可直接用此账号体验农事记录与批次查询" |
| 补充说明 | 说明这是毕业设计系统，数据为模拟数据；说明区块链存证为联盟链演示环境 |

审核时长：一般 2 小时～2 个工作日。

#### 常见驳回原因与对策

| 驳回理由 | 真实原因 | 对策 |
| --- | --- | --- |
| "小程序功能不完整/无法使用" | 审核员用测试账号登录后看不到数据 | 测试账号要有数据权限（用 `admin` 或给 `farmer01` 造够批次） |
| "缺少用户隐私保护指引" | 调用了相机/相册/位置但没声明 | 补齐隐私指引 + `requiredPrivateInfos` |
| "服务类目与实际功能不符" | 选了"电商"但实际是查询工具 | 改类目为"工具-信息查询" |
| "涉及区块链/虚拟货币" | 文案里有"链上资产""通证"等词 | 只写"区块链存证""数据上链"，不涉及任何代币表述 |
| "页面存在诱导分享" | 分享按钮文案"分享得积分" | 去掉利益诱导，只写"分享给好友" |
| "测试账号无效" | 密码错 / 账号被禁用 | 提交前用另一个微信号实测一遍 |

### 4.4 App 端打包（Android / iOS）

#### 4.4.1 Android 云打包

**第一步：生成签名证书**（一次性，务必保存好，丢了就无法覆盖升级）

```bash
keytool -genkey -alias agri -keyalg RSA -keysize 2048 -validity 36500 \
        -keystore agri.keystore
```

- `-validity 36500` = 100 年，应用市场要求有效期覆盖到 2033 年以后；
- 记住 alias（`agri`）和两个密码（store 密码、key 密码）；
- **`agri.keystore` 不要提交到 git**，加入 `.gitignore`，单独备份。

**第二步：配置 `manifest.json`**

```jsonc
"app-plus": {
  "distribute": {
    "android": {
      "packagename": "com.agri.trace",
      "abiFilters": ["armeabi-v7a", "arm64-v8a"],
      "minSdkVersion": 21,
      "targetSdkVersion": 33,
      "permissions": [
        "<uses-permission android:name=\"android.permission.INTERNET\"/>",
        "<uses-permission android:name=\"android.permission.CAMERA\"/>",
        "<uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\"/>",
        "<uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>",
        "<uses-permission android:name=\"android.permission.READ_MEDIA_IMAGES\"/>",
        "<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\"/>",
        "<uses-permission android:name=\"android.permission.ACCESS_COARSE_LOCATION\"/>",
        "<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\"/>"
      ]
    },
    "ios": {
      "privacyDescription": {
        "NSCameraUsageDescription": "用于拍摄农事操作现场照片，作为溯源凭证",
        "NSPhotoLibraryUsageDescription": "用于从相册选择农产品与质检报告照片",
        "NSPhotoLibraryAddUsageDescription": "用于保存溯源二维码到相册",
        "NSLocationWhenInUseUsageDescription": "用于记录农事操作地点"
      }
    },
    "sdkConfigs": {}
  },
  "modules": {
    "Camera": {},
    "Barcode": {},
    "Geolocation": {},
    "Share": {}
  }
}
```

> 权限原则：**只申请真正用到的**。多申请一个定位权限，应用市场会要求你单独说明用途，iOS 审核会直接拒。

**第三步：云打包**

HBuilderX → 发行 → 原生App-云打包：

| 选项 | 填法 |
| --- | --- |
| 打包方式 | 使用 DCloud 老版云打包（免费）或新版（收费，速度快） |
| Android 包名 | `com.agri.trace` |
| 证书 | 选"使用自有证书"，上传 `agri.keystore`，填 alias 与密码 |
| 渠道包 | 不需要 |
| 广告联盟 | 关闭（毕设不要勾，否则启动有广告） |
| 打包类型 | 正式包 |

等待 3～15 分钟，完成后自动下载 APK。

**第四步：产物位置**

```
frontend/uni-app/unpackage/release/apk/
└── __UNI__XXXXXXX__20261105103000.apk
```

同时 HBuilderX 控制台会给一个下载链接（保留 7 天）。

> CLI 项目用 HBuilderX 打开后，`unpackage/` 会生成在项目根目录（与 `src/` 同级），**这个目录不要提交 git**。

#### 4.4.2 iOS 打包

前置条件（缺一不可）：

| 条件 | 说明 |
| --- | --- |
| Apple 开发者账号 | 个人 $99/年；企业 $299/年 |
| Bundle ID | 与 `manifest` 里一致，如 `com.agri.trace` |
| 发布证书 `.p12` | 在 Apple Developer 后台创建 Distribution Certificate，导出 p12 并设密码 |
| Provisioning Profile `.mobileprovision` | App Store 类型，绑定 Bundle ID 与证书 |
| Universal Links | 若用到微信登录/分享，必须配 `apple-app-site-association` |

云打包选项：

| 选项 | 填法 |
| --- | --- |
| Bundle ID | `com.agri.trace` |
| 私钥证书 | 上传 `.p12`，填密码 |
| 私钥密码 | 导出时设的密码 |
| profile 文件 | 上传 `.mobileprovision` |

产物：`.ipa` 文件，通过 Transporter 上传到 App Store Connect，或用 TestFlight 分发。

> Windows 环境无法本地打包 iOS，只能云打包。**iOS 上架审核比小程序严格得多**，毕设阶段建议：Android 出 APK 答辩演示 + iOS 只做云打包验证可运行，不上架。

#### 4.4.3 App 分发方式

| 方式 | 适用 | 操作 |
| --- | --- | --- |
| 直接安装 APK | 答辩演示、内部测试 | 发文件给测试者，允许"未知来源安装" |
| 蒲公英 / fir.im | 内测分发，有下载页和二维码 | 上传 APK，生成短链 |
| 应用市场 | 正式发布 | 华为/小米/OPPO/vivo 开发者后台，需软著 |
| TestFlight | iOS 内测 | App Store Connect 上传，邀请测试员 |

> 答辩场景最稳的是**蒲公英 + 二维码**：老师扫码即可安装，不用连电脑。

### 4.5 各端产物一览

| 端 | 命令 | 产物目录 | 最终交付物 |
| --- | --- | --- | --- |
| H5（CLI） | `npm run build:h5` | `frontend/uni-app/dist/build/h5/` | 整个目录上传服务器 |
| H5（HBuilderX） | 发行 → 网站-PC/手机H5 | `frontend/uni-app/unpackage/dist/build/h5/` | 同上 |
| 微信小程序（CLI） | `npm run build:mp-weixin` | `frontend/uni-app/dist/build/mp-weixin/` | 用开发者工具打开该目录上传 |
| 微信小程序（HBuilderX） | 发行 → 小程序-微信 | `frontend/uni-app/unpackage/dist/build/mp-weixin/` | 同上 |
| App Android | HBuilderX 云打包 | `frontend/uni-app/unpackage/release/apk/*.apk` | APK 文件 |
| App iOS | HBuilderX 云打包 | `frontend/uni-app/unpackage/release/ios/*.ipa` | IPA 文件 |
| App 本地打包（Android） | HBuilderX 本地打包 | `unpackage/release/apk/` 或 Android Studio 工程 `unpackage/resources/` | APK |

**开发态产物**（调试时用，不要发布）：

```
dist/dev/h5/           H5 开发产物（dev:h5 时内存中，不落盘）
dist/dev/mp-weixin/    小程序开发产物 ← 开发者工具导入这个
dist/dev/app/          App 开发产物
```

**打包脚本建议**（`package.json`）：

```json
{
  "scripts": {
    "dev:h5": "uni",
    "dev:mp-weixin": "uni -p mp-weixin",
    "dev:app": "uni -p app",
    "build:h5": "uni build",
    "build:h5:staging": "uni build --mode staging",
    "build:mp-weixin": "uni build -p mp-weixin",
    "build:mp-weixin:staging": "uni build -p mp-weixin --mode staging",
    "build:app": "uni build -p app",
    "clean": "rimraf dist unpackage"
  }
}
```

---

## 五、项目维护与问题排查规范

### 5.1 目录划分原则

整个 `src` 目录按**"离用户多远"**分层，越靠上越接近界面，越靠下越接近数据：

```
页面 pages/          ← 只做「取数据 + 拼界面 + 跳转」，不写业务规则
  ↓ 调用
组合式 composables/  ← 页面级复用逻辑（分页、表单、权限）
  ↓ 调用
组件 components/     ← 纯展示与交互，不发请求
  ↓ 调用
接口 api/            ← 只声明 URL 与参数，不含逻辑
  ↓ 调用
工具 utils/          ← 请求封装、存储、扫码、格式化，与业务无关
  ↓ 读取
配置 config/         ← 环境、常量、路由表、平台判定
```

四条硬性原则：

1. **单向依赖**：上层可以调下层，下层**禁止**反向 import 上层。`utils/request.js` 不能 import `store/user.js`（会造成循环依赖），所以 401 用 `uni.$emit('auth:expired')` 通知，而不是直接调 store。
2. **一个后端 Controller 对应一个 api 文件**：`BatchController` → `api/modules/batch.js`。找接口不用全局搜 URL。
3. **页面不写魔法值**：状态码、角色码、颜色、尺寸全部来自 `config/constants.js` 或 `uni.scss`。
4. **端差异只允许出现在三处**：`utils/`、`components/`、`pages-mp|pages-app/`。`pages/` 里出现 `#ifdef` 就说明抽象漏了。

### 5.2 命名规范

| 类型 | 规范 | 示例 | 反例 |
| --- | --- | --- | --- |
| 目录 | kebab-case，单数 | `batch/`、`quality/` | `Batches/`、`batchList/` |
| 页面文件 | kebab-case，动词/名词明确 | `detail.vue`、`form.vue`、`list.vue` | `detailPage.vue`、`index2.vue` |
| 组件文件 | kebab-case，至少两段 | `batch-card.vue`、`trace-timeline.vue` | `Card.vue`、`card1.vue` |
| 组件使用 | 模板里用 kebab-case | `<batch-card />` | `<BatchCard />`（H5 可以，小程序有风险） |
| api 函数 | 动词 + 名词，camelCase | `batchPage`、`createBatch`、`auditBatch` | `getBatchData`、`batchApi1` |
| api 文件 | 与后端 Controller 同名（小写） | `batch.js`、`abnormal.js` | `batchApi.js` |
| store | `useXxxStore`，id 用单数小写 | `useUserStore` / `'user'` | `useUsersStore` / `'users'` |
| 常量 | UPPER_SNAKE_CASE | `BATCH_STATUS`、`ROLE_CODE` | `batchStatus` |
| 事件（emit） | kebab-case | `@change`、`@update:modelValue` | `@onChange` |
| CSS 类 | BEM-lite：`块-元素` | `.batch-card`、`.batch-card__title` | `.title1`、`.red-text` |
| storage key | `agri_` 前缀 + 小写下划线 | `agri_token`、`agri_last_sync` | `token`、`userInfo` |
| 路由常量 | `ROUTES.XXX` | `ROUTES.BATCH_DETAIL` | 页面里直接写 `'/pages/batch/detail'` |
| 条件编译标记 | 独占一行，缩进对齐 | `// #ifdef MP-WEIXIN` | 写在行尾 |

### 5.3 端专属代码的存放与识别规范

三端代码隔离靠**目录 + 文件名后缀 + 注释**三层标识，任何一层都能让人一眼看出"这是哪端的代码"。

#### 目录级隔离（首选）

| 目录 | 打包进哪些端 | 识别特征 |
| --- | --- | --- |
| `pages/` | H5 + 小程序 + App | 无平台前缀 |
| `pages-pc/` | 仅 H5 | 目录名带 `-pc` |
| `pages-mp/` | 仅微信小程序 | 目录名带 `-mp` |
| `pages-app/` | 仅 App | 目录名带 `-app` |
| `pages-sub/` | 仅小程序（分包） | 目录名带 `-sub` |
| `static/` | 全部 | 无前缀 |
| `static-pc/` | 仅 H5 | 带 `-pc` |

#### 文件后缀级隔离（同一逻辑的多端实现）

当一个函数在三端实现完全不同（如扫码），用平台后缀文件，**编译时自动择优**：

```
utils/scan/
├── index.js          # 兜底（小程序/App 用 uni.scanCode）
├── index.h5.js       # H5 专用（html5-qrcode）
└── index.app.js      # App 专用（如需原生增强）
```

`import { scan } from '@/utils/scan'` 在三端都能用，H5 构建时自动取 `index.h5.js`。

支持的平台后缀：`.h5.js`、`.mp-weixin.js`、`.app.js`、`.nvue`。**注意**：后缀必须紧跟文件名，`index.h5.js` 正确，`index-h5.js` 无效。

> 后缀方案的优先级高于目录方案，但可读性差一些。建议：**整页差异用目录，单函数差异用后缀，局部几行差异用注释条件编译**。

#### 注释级隔离（最小粒度）

```js
// #ifdef MP-WEIXIN
// 微信小程序：需要用户主动点击才能触发分享菜单
// #endif
```

规范：条件编译注释必须**成对出现且缩进对齐**，块内代码不超过 20 行。超过 20 行说明该抽成独立文件了。

#### 禁止事项（Code Review 必查）

| 禁止 | 原因 | 正确做法 |
| --- | --- | --- |
| `pages/` 里出现 `#ifdef MP-WEIXIN` | 破坏通用性 | 抽到 `pages-mp/` 或用 `composables/` 封装 |
| `utils/` 里 import `@/store/*` | 循环依赖 | 用 `uni.$emit` 事件解耦 |
| 页面里直接写 `uni.request` | 绕过统一封装，401 不处理 | 走 `api/modules/*` |
| 页面里直接写 `uni.setStorageSync('token')` | key 分散，多端不一致 | 走 `utils/auth` |
| 模板里写 `v-if="uni.getSystemInfoSync().platform === 'ios'"` | 每次渲染都调用系统 API，卡顿 | 启动时算一次，存 `store/app.js` |
| `el-` 组件出现在 `pages/` | 小程序编译失败 | 移到 `pages-pc/` |
| 硬编码接口地址 | 切环境要改多处 | 用 `ENV.baseURL` |

### 5.4 标准排查路径

#### 场景一：某一端接口请求失败

按这个顺序查，**不要跳步**：

```
① 同一接口在其他端是否正常？
   ├─ 正常 → 是该端特有问题 → 跳到 ③
   └─ 都不正常 → 后端问题 → 查 ②

② 后端是否正常？
   ├─ curl 直连后端：curl -H "Authorization: Bearer xxx" http://localhost:8080/api/batch/page
   ├─ 401 → token 问题（查 src/utils/auth.js 的 key 是否为 agri_token）
   ├─ 404 → 路径拼错（查 src/config/env.js 的 baseURL 是否多/少了一层 /api）
   ├─ 400 + body.code=4001 → 业务状态不允许（不是 bug，查业务规则）
   └─ 500 → 看后端日志

③ 该端特有的网络限制
   ├─ 小程序 → 开发者工具「详情 → 本地设置」是否勾选不校验域名；
   │           正式版看公众平台 request 合法域名是否配了当前域名
   ├─ App → 是否 http（iOS ATS 拦截）；Android 9+ 默认禁止明文 http
   └─ H5 → 控制台是否有 CORS 报错（看 Network 面板的 Response Headers）

④ 请求是否真的发出了？
   ├─ H5：Network 面板
   ├─ 小程序：Network 面板（注意 uploadFile 在单独分类）
   └─ App：HBuilderX 控制台 + chrome://inspect

⑤ 请求头是否正确？
   └─ 查 src/utils/request.js 的 header 组装 + src/utils/auth.js 的 getToken()

⑥ 响应体结构是否符合预期？
   └─ 后端返回 { code, message, data }，前端是否取 res.data.data
```

**对应文件清单**（按顺序打开）：

| 顺序 | 文件 | 看什么 |
| --- | --- | --- |
| 1 | `src/config/env.js` | `baseURL` 是否拼对 |
| 2 | `src/utils/auth.js` | token 是否读到 |
| 3 | `src/utils/request.js` | header、超时、错误分支 |
| 4 | `src/api/modules/xxx.js` | URL 与 method 是否正确 |
| 5 | `.env.development` | 环境变量是否生效 |
| 6 | `manifest.json` → `mp-weixin` | 域名、`urlCheck` |

#### 场景二：样式错乱

```
① 只有某一端乱？
   ├─ 小程序乱 → 用了 div/span/img 等非 uni 标签（全局搜 <div）
   │             或用了 * / 属性选择器（小程序 WXSS 不支持）
   │             或 rpx 单位没转（还是 px）
   ├─ H5 宽屏乱 → 没配 rpxCalcMaxDeviceWidth（750rpx 撑满 1920px）
   ├─ App 乱 → 原生组件（input/textarea/video）层级问题，用 cover-view
   └─ 都乱 → 全局样式被覆盖，查 styles/ 引入顺序

② 某个组件样式不生效？
   ├─ scoped 是否加了？加 scoped 后父组件改不了子组件内部样式，用 :deep()
   ├─ 小程序不支持标签选择器，view { } 无效，必须用 class
   └─ easycom 组件是否真的加载了（看 Elements 面板有没有该节点）

③ 布局塌陷？
   ├─ <view> 默认 flex-column，横向排列要显式 flex-direction: row
   └─ 图片没写 mode，宽高比失真
```

**对应文件**：`src/uni.scss`（变量）→ `src/styles/common.scss`（原子类）→ 组件 `<style scoped>` → `pages.json` 的 `globalStyle`。

#### 场景三：功能点了没反应

```
① 有没有报错？
   └─ 控制台 / vConsole / adb logcat

② 事件绑上了吗？
   ├─ @click 写成 @tap 也能用，但 @click.native 无效
   └─ 自定义组件上的 @click 需要在组件内 emit

③ 跳转失败？
   ├─ 目标页是否在 pages.json 注册（最常见）
   ├─ tabBar 页是否用了 navigateTo（必须 switchTab）
   └─ 参数是否超长/含特殊字符（需 encodeURIComponent）

④ 权限被拦？
   ├─ 前端：v-perm / usePermission 是否误判
   └─ 后端：403 → 角色没有该权限码（查 sys_role_perm 表）

⑤ 原生能力不可用？
   ├─ 小程序：是否用户拒绝授权（uni.getSetting 查 authSetting）
   ├─ App：manifest 是否勾了对应模块
   └─ H5：是否非 https（摄像头/定位不可用）
```

#### 场景四：上传失败

```
① HTTP 状态码是多少？
   ├─ 413 → nginx client_max_body_size 太小（最常见）
   ├─ 401 → header 没带 Authorization
   ├─ 400 → 参数名不是 file，或后端文件类型校验拒绝
   └─ 无状态码（fail 回调）→ 域名/网络问题

② 小程序额外检查
   ├─ uploadFile 合法域名是否单独配了
   ├─ header 里是否手贱加了 Content-Type（必须去掉）
   └─ 是否超过 10MB

③ App 额外检查
   └─ 相机/相册权限是否授予

④ 返回体解析
   └─ uni.uploadFile 返回的是字符串，必须 JSON.parse（utils/upload.js 已处理）
```

#### 场景五：三端数据不一致

```
① 是不是缓存？
   └─ 列表页 onShow 是否重拉；cache.isDirty / isStale 是否生效

② 是不是权限导致数据范围不同？
   └─ 同一账号在三端 roleCode 是否一致（看 /auth/profile 返回）

③ 是不是并发覆盖？
   └─ 后端 updateById 返回 0 行 → 4090 冲突（需加 version 字段，见 §3.4）

④ 是不是时钟问题？
   └─ 时间筛选依赖后端 now()，不要用前端本地时间
```

### 5.5 错误码速查表

后端 `ResultCode` 定义 + 前端自定义码，**排查时先看码，再看文案**：

| code | 含义 | 出现场景 | 前端处理 | 排查方向 |
| --- | --- | --- | --- | --- |
| `0` | 成功 | — | resolve | — |
| `400` | 参数错误 | 表单校验失败、缺参数 | toast 后端 message | 看后端 `@Valid` 注解与 DTO 字段名 |
| `401` | 未登录 / token 失效 | token 过期（12h）、被清空 | 清 token + reLaunch 登录页 | `utils/auth.js` key 是否统一 |
| `403` | 越权 | 农户访问用户管理 | toast「越权操作已被拦截」 | 查 `@RequirePerm` 与 `sys_role_perm` |
| `404` | 资源不存在 | 批次 id 不存在 | toast | 检查参数是否传成 `undefined` |
| `409` | 冲突 | 重复提交、异常批次禁止销售 | toast + 刷新 | 业务规则拦截，非 bug |
| `4001` | 业务状态不允许 | 未质检就发运、已关闭批次再操作 | toast 后端 message | 查批次当前 status |
| `5001` | 上链失败 | FISCO 节点不可用 | 提示「存证失败，业务已保存」 | 查 `chain.enabled` 与节点连通性 |
| `5002` | 链上链下不一致 | 摘要校验失败 | 红色告警 + 展示差异 | 数据被直接改库，属预期告警 |
| `500` | 服务器错误 | 未捕获异常 | toast「服务异常」 | 后端日志 |
| `-1` | 网络层失败（前端自定义） | 断网、超时、域名不合法 | toast「网络连接失败」 | 见 §5.4 场景一 |
| `-2` | 上传响应解析失败（前端自定义） | 返回非 JSON（网关错误页） | toast | 看 Network 的 Response 原文 |

> **注意 `4001` 与 HTTP 400 的关系**：后端 `GlobalExceptionHandler` 对业务异常统一返回 **HTTP 400**，但 body 里保留真实业务码（`4001`/`5001`/`5002`）。所以前端封装必须**先读 body.code**，否则会把「未质检不能发运」误报成「参数错误」。这一点 §2.2 的 `request.js` 已处理。

### 5.6 上线前检查清单

**三端通用**

- [ ] `ENV.baseURL` 指向生产域名，`ENV.debug = false`
- [ ] 控制台无 `console.log` 泄露敏感信息（token、手机号）
- [ ] 所有 `#ifdef` 块成对闭合
- [ ] `pages.json` 里没有残留的测试页面
- [ ] 图片全部走 https，无本地大图（>200KB）
- [ ] 弱网测试：3G 限速下首屏 < 3s
- [ ] 空数据、超长文本、特殊字符（emoji）不撑破布局
- [ ] 退出登录后返回，不能通过返回键回到已登录页面

**H5 专项**

- [ ] history 模式配了 `try_files`
- [ ] 二级目录部署时 `h5.router.base` 与 nginx `location` 一致
- [ ] 刷新任意详情页不 404
- [ ] `client_max_body_size` ≥ 20m

**小程序专项**

- [ ] request / uploadFile / downloadFile / socket 四类域名都配了
- [ ] 隐私指引已声明相机、相册、位置
- [ ] `urlCheck: true`
- [ ] 主包 < 2MB（工具「代码依赖分析」可看）
- [ ] 提交审核时填了可用测试账号
- [ ] 体验版完整走一遍业务流程

**App 专项**

- [ ] `versionCode` 比上一版大
- [ ] 签名证书与上一版一致（否则无法覆盖安装）
- [ ] 权限只申请用到的
- [ ] iOS `privacyDescription` 四项齐全
- [ ] 启动图、应用图标齐全（1024×1024）
- [ ] 真机（低端安卓）跑一遍不闪退

### 5.7 视觉规范与品牌资源

**品牌标识**

图形只有一个来源：`src/static/logo/mark.svg`（草坡 + 双叶嫩芽 + 链上节点）。PNG 由脚本从 SVG 导出，**不要手工改 PNG**：

```bash
node scripts/gen-logo-png.js   # 输出 src/static/logo/mark.png（256×256）
```

| 文件 | 用途 |
| --- | --- |
| `static/logo/mark.svg` | 设计源文件；海报、文档、印刷用 |
| `static/logo/mark.png` | 运行时唯一引用；小程序 / App 不保证支持内联 SVG，统一用 `<image>` |

组件 `components/common/brand-logo.vue` 封装了尺寸档位与配色：

| prop | 取值 | 说明 |
| --- | --- | --- |
| `size` | `sm` / `md` / `lg` | 34 / 46 / 64 px |
| `tone` | `light` / `dark` | 深底浅字 / 浅底深字 |
| `stack` | `Boolean` | 徽章在上、文字在下（启动页、关于页） |
| `iconOnly` | `Boolean` | 只出图形，不出文字 |

新增页面若需要品牌位，一律复用该组件，不要再手写「溯」字方块。

**为什么不用纯 CSS 画图形**：徽章内部由渐变、贝塞尔叶形、多层阴影叠出来，用 `border-radius` + `rotate` 拼几何体在不同渲染引擎下（H5 / 小程序 WebView / App WebView）会出现叶形方向不一致、圆角裁切差异。改成「SVG 定稿 → 导出 PNG」后，三端像素级一致，且不依赖内联 SVG 支持。

**毛玻璃导航（PC 侧栏）**

实现要点，按重要性排序：

1. **背景要先"有东西可糊"**。`backdrop-filter: blur()` 作用在纯色或纯线性渐变上几乎看不出变化——这是最初"选中项像一块纯色胶囊"的根本原因。现在 `.side` 是三层径向光斑，再叠 `.side::before` 的双层极淡斜纹理（115° / 25°，白 0.055 / 0.035，间距 24 / 36px）；模糊之后才看得出"透过去并被揉开"。
2. **玻璃主体 `.mi-glass`**：低不透明度斜渐变 + `blur(12px) saturate(165%) brightness(1.1)`，四边用 `inset box-shadow` 压高光（不用 `border`，否则尺寸变化会让文字抖 1px），`::before` 是左侧发光指示条，`::after` 是上半部柔和反射光。
3. **切换只改透明度，不做一次性扫光**。`uni.reLaunch` 会重建整个页面，动画每次挂载都会重播，观感就是"点一下闪一下"。扫光已经移除。

**PC 登录页（`pages/auth/login.vue` + `components/auth/pc-login.vue`）**

手机版 login 直接放到 1440 宽会横向拉满：hero 变成一条横幅、输入框拉成长条、按钮宽度失控。所以按宽度分流，两套版式共用同一套登录逻辑：

| 宽度 | 版式 |
| --- | --- |
| ≥900px | 左右分栏：左 57% 品牌画面，右 43% 表单 |
| 600–899px | 手机版，但用 px 覆盖把 rpx 的放大倍数压回去，收成 480px 居中卡片 |
| <600px | 原手机版（叠层 hero + 白色卡片） |

左栏不是一张背景图，而是可维护的图层堆叠：

- 深绿渐变 + 双径向光斑 + 56px 网格（`mask-image` 只在中心可见）；
- 内联 SVG 线稿：五道梯田等高线、一株嫩芽、四个链节点与虚线路径；
- 两枚"链上回执"浮签（`存证成功 / 区块 #8,241,905`、`可信度 88 / 100 A 级`），用 `backdrop-filter` 做玻璃质感；
- 五环节链条（种植→采收→质检→物流→销售）逐个亮起，底部三项指标；
- 窄屏（≤1180px）自动收起浮签，避免压到链条。

右栏表单：384px 卡片、焦点环、密码可见切换、记住账号（**只存用户名**，`agri_last_user`）、演示账号 4 列网格、消费者扫码入口。

**页面切换为什么"柔"（整页快照渐变）**

`uni.reLaunch` 是"销毁整页 → 重建整页"，中间有约 60–80ms 空档。只挂骨架屏能遮住空档，但"旧内容瞬间消失 → 骨架 → 新内容"本身仍是两跳，快速来回点菜单会觉得"一卡一卡"。最终方案是**整页快照渐变**：

1. 点击菜单的瞬间，把整个 `document.body` 深拷贝成一层 `position: fixed` 的快照（`#pc-snap`），**canvas 逐帧 `drawImage` 拷像素**，所以 ECharts 图表不会变空白；快照不属于任何页面，`reLaunch` 销毁旧页时它不受影响，用户视野里画面完全静止。
2. 新页面在快照下面静默挂载（实测生产构建 30–55ms 正文就绪），随后两帧之后快照以 `opacity 0.26s ease` 淡出——视觉是一次连续化开，没有空白帧、没有骨架跳变。
3. 快照失败（极端内核）自动退化为骨架遮罩；快速连点会先移除上一张快照再重新抓，最终不残留（实测连点 3 次后 `#pc-snap` / `#pc-veil` 均为 0）。
4. 配套三处：选中高亮在 `onLoad` 同步路由（删掉原先 `setTimeout(syncCurrent, 200)` 的"先灭后亮"）；侧栏滚动位置存模块级变量，导航后复原；内容区 0.14s 淡入，遮掉表格首帧抖动。

实测（生产构建，每 20ms 采样，点"异常管控 / 销售管理 / 采收登记"）：

| 指标 | 结果 |
| --- | --- |
| 正文出现耗时 | 30 / 52 / 55 ms |
| 空白帧 | 0 |
| 选中高亮缺失帧 | 0 |
| 快照在场时长 | 约 220ms（11/30 帧），随后自动移除 |
| 控制台错误 | 0 |

**预览生产构建（重要）**

`npm run dev:h5` 是**按需编译**：第一次进入某个页面时 Vite 才现场转换该页模块，切换必然带顿挫，这不代表上线效果。要看真实流畅度请用生产构建：

```bash
npm run build:h5
npm run preview:h5      # 静态托管 dist/build/h5，并把 /api 代理到 localhost:8080
# 打开 http://localhost:4173
```

预打包的 chunk 第一次即可用（之后走 immutable 长缓存），切页只剩渲染耗时，这才是答辩演示时的状态。生产预览服务器是 `scripts/serve-h5.js`（纯 Node，无额外依赖）。

**各端的版式差异**

| 端 | 入口 | 主框架 | 专属页 |
| --- | --- | --- | --- |
| PC（H5 ≥900px） | 左右分栏登录 | 深绿侧栏 + 白顶栏 + 内容区 | 15 个 `pages-pc` 页面 |
| 移动 H5 | 叠层 hero + 卡片 | tabBar 4 项 + 页面内导航 | 与小程序共用 |
| 微信小程序 | 同上，另有微信一键登录 | tabBar + 胶囊按钮避让 | 3 个 `pages-mp` |
| App | 同上，另有微信 App 登录 | tabBar + 安全区适配 | 3 个 `pages-app`（关于 / 更新 / …） |

**单位约定**

| 场景 | 单位 | 原因 |
| --- | --- | --- |
| `pages-pc/**`、`layouts/pc-shell.vue`、`components/pc/**` | `px` | PC 不走 rpx 缩放，1440 宽下 rpx 会放大到 1.92 倍 |
| `pages/**`、`components/common|business/**`、`pages-app/**` | `rpx` | 随屏幕宽度等比缩放 |
| 品牌标识组件 | `px` | 图形尺寸敏感，按 375 宽设计；PC 侧栏直接用 `sm` 档 |

**新增业务组件**

| 组件 | 用途 | 数据来源 |
| --- | --- | --- |
| `components/business/trust-score.vue` | 移动端可信度卡片（大分数 + 五维进度条 + 可展开扣分说明） | `TraceVO.trust` |
| `pages-pc/batch/detail.vue` 内联块 | PC 可信度卡片（横向五维排布） | `BatchDetailVO.trust` |

评分模型、计分规则与阶段化处理见 [trust-score.md](./trust-score.md)。

---

## 附录 A：后端最小改造清单

| 改造项 | 是否必须 | 状态 | 影响范围 | 说明 |
| --- | --- | --- | --- | --- |
| 账号密码登录支持三端 | **不需要** | ✅ 零改动 | — | JWT 无状态，天然支持 |
| CORS 配置 | 不需要（已配） | ✅ 零改动 | — | `WebConfig` 已放开，生产建议收紧域名 |
| `/file/upload` 兼容三端 | **不需要** | ✅ 零改动 | — | `uni.uploadFile` 的标准 multipart，后端无感 |
| `/ws` 加入拦截器白名单并实现端点 | 需要（上 WebSocket 时） | ✅ 已完成 | `WebConfig`、`WebSocketConfig`、`TraceWebSocketHandler` | 见 §3.5 |
| 微信小程序登录 | 需要（做一键登录时） | ✅ 已完成 | `sys_user` 三列 + 4 个接口 | 见 §3.2 |
| 微信 App 登录（开放平台） | 需要（App 端做一键登录时） | ✅ 已完成 | `sys_user.wx_app_openid` + `/auth/wx-app-login` | 与小程序 appid/openid 不同源，分列存储；见 §3.2 |
| 异常处置状态取值统一 | 需要 | ✅ 已修正 | `AbnormalService`、`Constants` | 原实现写 `FINISHED`，与库中 `HANDLING/DONE` 不一致，导致状态标签失效且结案永远被拦 |
| 乐观锁 `version` | 建议 | ✅ 已完成 | `biz_batch`、`biz_abnormal_disposal`、`base_farm` + MP 插件 | 见 §3.4 |
| refresh token | 可选 | ✅ 已完成 | `JwtUtil`、`AuthService`、`/auth/refresh` | 见 §3.1 |
| 溯源可信度评分 | 可选（创新点） | ✅ 已完成 | `TrustScoreService`、`TrustScoreVO`、`TraceVO.trust`、`BatchDetailVO.trust` | 纯内存计算、只读、无 DB 改动；见 [trust-score.md](./trust-score.md) |
| 单点登录限制 | **不建议** | — | — | 与"三端同步"目标冲突 |

> 一句话总结：**微信一键登录（小程序 + App）、WebSocket、乐观锁、refresh token 四块新增改造已全部完成，其余后端代码没有为新前端做任何修改。** 这是当前架构分层清晰带来的红利。

## 附录 B：迁移执行顺序（建议排期）

按这个顺序做，每一步都能独立验证，不会出现"改到一半跑不起来"。

| 阶段 | 内容 | 产出 | 验收标准 | 预估 |
| --- | --- | --- | --- | --- |
| **S0** | 建 `frontend/uni-app` 骨架，配 `vite.config.js`、`pages.json`、`manifest.json`、`uni.scss` | 能跑起空白页 | `npm run dev:h5` 打开不报错 | 0.5 天 |
| **S1** | 迁 `config/`、`utils/`、`api/`、`store/`（纯 JS 层，无 UI） | 请求能打通登录接口 | 在页面里调 `login()` 能拿到 token | 1 天 |
| **S2** | 迁登录页 + 启动分发器 + 权限守卫 | 三端都能登录 | H5/小程序/App 均能登录并进首页 | 1 天 |
| **S3** | 迁移动端核心页：工作台、批次列表、批次详情、溯源页 | 4 个页面可用 | 数据与旧 H5 一致 | 2 天 |
| **S4** | 迁表单页：农事、采收、质检（含上传组件） | 能拍照上传并提交 | 三端上传均成功 | 2 天 |
| **S5** | 迁 PC 页面（`pages-pc/`，保留 Element Plus） | 10 个后台页可用 | 与原 pc-admin 功能一致 | 3 天 |
| **S6** | 小程序专属：分享、海报、隐私授权 | 可分享可保存 | 真机分享卡片正常 | 1 天 |
| **S7** | 微信一键登录 + 后端改造 | 一键登录闭环 | 首次绑定、二次直登 | 2 天 |
| **S8** | 三端联调 + 打包发布 | H5 上线、小程序提审、APK | 三端同账号数据一致 | 2 天 |
| **S9** | 删除旧工程，更新文档 | 仓库只剩一套前端 | `git rm -r frontend/pc-admin frontend/mobile-h5` | 0.5 天 |

**关键里程碑**：S2 完成即证明"三端一套代码"可行；S3 完成即可用于答辩演示；S5 完成才算 PC 端真正迁移完。

**风险提示**：

1. **S5 是最大工作量**，10 个后台表格页重写，建议先用 `pc-table.vue` 抽一个通用壳，再批量套；
2. **S7 依赖微信资质**，如果小程序 AppID 还没下来，先做 S1～S6，S7 后置；
3. **不要跳过 S1**。很多人直接从页面开始迁，结果请求层返工，所有页面都要改一遍。

---

## 结语

这套方案的核心不是"把 Vue 换成 uni-app"，而是**三层收口**：

- **请求收口到 `utils/request.js`** —— 三端网络行为一致；
- **配置收口到 `config/env.js`** —— 切环境不改代码；
- **端差异收口到目录 + 条件编译** —— 差异可见、可查、可删。

做到这三点，后续无论加页面还是排查问题，路径都是确定的：**页面 → 组件 → 接口 → 工具 → 配置**，五步之内必能定位。

> 文档维护：改动目录结构或新增端专属目录时，请同步更新 §1.2 的目录树与 §5.2 的命名表。本文档与代码不同步，比没有文档更糟。




