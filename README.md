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
2. **批次全生命周期**：批次唯一编码 `AG + 日期 + 产地码 + 序列`，种植/采收/质检/物流/销售逐环节登记，每环节生成 `dataHash` 上链。
3. **存证与更正**：关键记录上链返回 `txHash / blockNumber`；录入错误不物理删除，通过 **追加更正说明** 保留完整历史。
4. **扫码溯源**：批次唯一溯源码（二维码），消费者免登录查询，按时间轴展示流转履历、质检摘要、链上凭证，并支持一键校验。
5. **异常批次管控**：质检不合格 → 批次 `ABNORMAL` 状态 → 销售/物流环节前置拦截 → 处置记录上链 → 支持上游来源与下游流向双向追溯。
6. **一致性校验**：链下重算 SHA-256 与链上哈希比对 + 交易回执状态校验，校验失败明确提示，绝不显示"存证成功"。
7. **基层网格化**：村级管理员对辖区农户一对多管理，农户提交数据需村管审核后进入收购/质检环节。
8. **多端适配**：PC 管理端（数据看板、全流程后台）；H5（农户录入、拍照上传、消费者扫码）。

## 五、仓库结构

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
│   └── mobile-h5/           # 移动端（pages / components / api / utils）
├── contracts/               # Solidity 合约、ABI/BIN、编译与部署脚本
├── sql/                     # init.sql、data.sql、versions/ 版本迭代脚本
└── docs/                    # 项目说明、部署手册、测试用例、接口文档
```

## 六、快速启动

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
cd frontend/pc-admin && npm install && npm run dev     # http://localhost:5173
cd frontend/mobile-h5 && npm install && npm run dev    # http://localhost:5174
```

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

## 七、文档索引

- [docs/README.md](docs/README.md) —— 项目详细说明与架构图
- [docs/deploy.md](docs/deploy.md) —— 部署操作手册（本地 / 云服务器 / 链节点）
- [docs/api.md](docs/api.md) —— 接口说明（入参、出参、权限）
- [docs/test.md](docs/test.md) —— 验收测试用例（正常流转、异常拦截、越权、重复提交、一致性）
- [contracts/README.md](contracts/README.md) —— 合约设计与部署说明

## 八、验收演示路径（建议 10 分钟）

1. 农户 H5 登录 → 提交农事记录（拍照）→ 提交采收 → 查看上链回执；
2. 村管 PC 审核 → 生产主体创建批次并绑定农户采收数据；
3. 检测机构上传质检报告（合格）→ 批次转为"质检合格"；
4. 物流企业登记发运 → 销售商登记销售 → 批次"已售出"；
5. 消费者 H5 扫码 → 时间轴溯源 + 链上凭证 + 一致性校验通过；
6. 另建批次质检"不合格" → 销售登记被拦截 → 监管处置 → 上下游追溯定位责任主体；
7. 用农户账号调用质检接口 → 403 越权拦截；重复提交采收 → 409 重复校验。

## 九、开发分工

| 角色 | 负责目录 | 说明 |
| --- | --- | --- |
| 后端 | `/backend`、`/sql` | 业务接口、权限、链交互封装 |
| 前端 | `/frontend` | PC 管理端 + H5 |
| 合约 | `/contracts` | 合约逻辑、编译部署 |
| 文档/测试 | `/docs` | 说明、部署、接口、测试用例 |

模块间仅通过 HTTP 接口与链上合约解耦，单模块修改不影响其他目录。

## 十、License

[MIT](LICENSE) © 2026 agri-origin
