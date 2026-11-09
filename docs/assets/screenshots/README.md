# 运行截图

本目录存放系统**实际运行**时的界面截图，用于毕设报告插图、答辩演示与 README 的界面预览。

## 采集方式

所有截图都来自线上真实环境 `https://8-130-105-150.sslip.io`（FISCO-BCOS 2.9.1 四节点单群组，**真实上链**），
用无头浏览器注入各角色登录态后逐页采集，**未做手工修饰、未使用占位数据**。

- PC 页面：1440 × 900，DPR 1
- 移动页面：390 × 844，DPR 2（输出 780 × 1688）

页面级截图脚本见 `frontend/uni-app/scripts/shot-*.js`；全流程造数与逐环节截图用的脚手架在开发区
（`.workbuddy/shotenv/`），未纳入版本库。

## 目录结构

```
screenshots/
├── uni-pc/     PC 管理端（1440×900）
└── uni/        移动端（780×1688）
```

## PC 管理端 · `uni-pc/`

| 文件 | 页面 | 说明 |
| --- | --- | --- |
| `pc-login.png` | 登录 | 左侧品牌叙事 + 右侧表单 + 演示账号一键填入 |
| `pc-dashboard.png` | 数据看板 | 4 张指标卡、状态分布、近 7 日建档、村级分布、存证概况 |
| `pc-batch-list.png` | 批次管理 | 搜索/筛选、状态与上链标签、行内详情与核验 |
| `pc-batch-detail.png` | 批次详情 | 可信度评分 + 审核进度 + 二维码 + 存证 + 时间轴 |
| `pc-batch-detail-bottom.png` | 批次详情（下滑） | 基础信息、存证流水、全流程时间轴 |
| `pc-workflow-todo.png` | 审核待办（村管） | 本村批次核验 / 农事 / 采收，可直接通过或驳回 |
| `pc-workflow-todo-regulator.png` | 审核待办（监管） | 质检 / 物流 / 销售复核 |
| `pc-planting.png` | 农事记录 | 按批次筛选、类型中文化、8 列表格 |
| `pc-harvest.png` | 采收登记 | 采收量/等级/天气，仅种植中批次可选 |
| `pc-quality.png` | 质量检测 | 检测项明细、报告 PDF 上传 |
| `pc-logistics.png` | 物流运输 | 车牌/司机、发运与到达、温控 |
| `pc-sales.png` | 销售管理 | 仅合格及之后批次可选，金额 = 单价 × 数量 |
| `pc-abnormal.png` | 异常管控 | 统计卡、异常类型与处置状态、处置弹窗 |
| `pc-chain.png` | 链上存证 | 链状态、区块高度、逐笔流水、一致性校验 |
| `pc-trace.png` | 溯源查询 | 溯源码/批次号查询、真实样本码 |
| `pc-trace-result.png` | 溯源结果 | 免登录、档案 + 时间轴 + 链上凭证 |
| `pc-sys-user.png` | 用户与角色 | 账号、角色标签、手机号脱敏、重置密码 |
| `pc-sys-farm.png` | 农场档案 | 农场主与所属村关联 |
| `pc-sys-product.png` | 产品品类 | 品类/品种/单位/保质期 |
| `pc-sys-log.png` | 操作日志 | 操作人、模块、动作、URI、IP、耗时、状态 |
| `role-admin.png` | 菜单裁剪 | 管理员 15 项 |
| `role-farmer01.png` | 菜单裁剪 | 农户 6 项 |
| `role-logistics01.png` | 菜单裁剪 | 物流企业 5 项 |
| `register-pc.png` | 自助注册 | 身份选择、基本信息、登录凭证 |

### 全流程推进（同一条批次 `AG20261109011` 的四个时刻）

| 文件 | 时刻 | 进度 |
| --- | --- | --- |
| `flow-01-created.png` | 建档完成，等村管核验 | 29%（2/7） |
| `flow-02-planting-pending.png` | 农事已提交，等村管审核 | 43%（3/7） |
| `flow-03-quality-pending.png` | 质检已上传，等监管复核 | 71%（5/7） |
| `flow-04-completed.png` | 全流程完成 | 100%（7/7） |

## 移动端 · `uni/`

| 文件 | 页面 | 说明 |
| --- | --- | --- |
| `m-login.png` | 登录 | 品牌区 + 表单 + 角色快捷填充 |
| `m-home.png` | 工作台（农户） | 统计、快捷入口、最近批次 |
| `m-home-manager.png` | 工作台（村管） | "有 N 项待你审核"横幅 + 审核待办入口 |
| `m-batch-list.png` | 我的批次 | 状态标签、搜索、分页加载 |
| `m-batch-detail.png` | 批次详情 | 审核进度百分比、进度条、逐环节提交/审核人 |
| `m-workflow-todo.png` | 审核待办 | 卡片流，底部通过/驳回 |
| `m-planting-list.png` | 农事记录 | 审核状态与审核人 |
| `m-submit-notice.png` | 提交提示 | 提交后弹出「等待溪头村 王秀兰、曹聪（村级管理员）审核」 |
| `m-trace.png` | 消费者溯源 | 可信度评分 + 五维得分 + 时间轴 + 链上校验 |
| `m-chain.png` | 链上存证 | 链状态与流水 |
| `register-mobile.png` | 自助注册 | 身份卡、所属村推荐、登录凭证 |
| `register-trace-batch.png` | 注册账号走完的批次 | 五个自助注册账号完成的 `AG20261107003` 溯源页 |

## 关于存证状态

所有截图都取自当前线上部署（`frontend/uni-app` 三端统一工程）的 FISCO-BCOS 2.9.1 四节点真实链，
存证状态为「已上链」，交易哈希与区块高度可直接在链上回查。

历史遗留的两套独立前端（`frontend/pc-admin`、`frontend/mobile-h5`）在 `chain.enabled=false` 下采集的截图
已在本版清理，避免与当前界面混淆——它们不再代表系统外观。

## 重新采集

```bash
# 1. 启动数据库、后端、前端（npm run build:h5 + nginx 或 npm run dev:h5）
# 2. 用无头浏览器脚本按角色逐页采集（需 puppeteer-core + 本机 Edge/Chrome）
node frontend/uni-app/scripts/shot.js
```

> 截图只覆盖主要页面与关键交互态。系统实际共 51 个页面（移动 29 + PC 16 + 小程序/App 专用各 3），
> 未逐页出图，避免报告里堆砌重复度高的列表页。
