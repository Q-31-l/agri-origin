# 测试用例文档

> 农产品质量安全溯源系统 · 验收测试用例
> 覆盖功能正确性、权限安全、异常管控、数据一致性四大维度

---

## 一、测试范围与环境

### 1.1 测试策略

| 层次 | 手段 | 覆盖目标 |
| --- | --- | --- |
| 单元测试 | JUnit 5 + Mockito（`backend/src/test`） | 摘要算法、鉴权、越权、重复提交、异常熔断 |
| 接口测试 | Knife4j / curl / Postman | 入参校验、错误码、响应结构 |
| 功能测试 | 手工执行本文用例 | 全生命周期流转、多端适配、界面交互 |
| 一致性测试 | 篡改数据库后重新校验 | 链上链下比对能力 |
| 兼容性测试 | Chrome / Edge / Safari + iOS / Android | 多端适配 |

### 1.2 测试环境

```
后端：Spring Boot 2.7.18 · JDK 1.8 · MySQL 8.0 · FISCO-BCOS 2.9.x
前端：PC Chrome 120+ / H5 iPhone Safari + Android Chrome
数据：sql/init.sql + sql/data.sql（12 条演示批次，含 2 个异常批次）
链状态：chain.enabled = true 与 false 两种模式各测一轮
```

### 1.3 演示数据基线

`data.sql` 共 12 个批次，满足任务书「至少 10 个批次、其中 2 个检测异常批次、每批次不少于 4 条业务节点记录」的要求。

| 批次号 | 溯源码 | 产品 | 状态 | 节点记录数 | 用途 |
| --- | --- | --- | --- | :-: | --- |
| `AG20260512001` | `TC20260512001` | 红颜草莓 | SOLD | 9 | 正常全流程正向用例、扫码溯源 |
| `AG20260515002` | `TC20260515002` | 小白菜 | ABNORMAL | 7 | 异常批次①：农药残留超标熔断与定责 |
| `AG20260601003` | `TC20260601003` | 脐橙 | PLANTING | 4 | 农事录入、采收提交、村级核验用例 |
| `AG20260518004` | `TC20260518004` | 红颜草莓 | SOLD | 8 | 二茬草莓正常批次 |
| `AG20260520005` | `TC20260520005` | 小白菜 | SOLD | 9 | 叶菜正常批次（两笔销售） |
| `AG20260522006` | `TC20260522006` | 脐橙 | ABNORMAL | 7 | 异常批次②：重金属超标熔断与定责 |
| `AG20260525007` | `TC20260525007` | 红颜草莓 | QUALIFIED | 6 | 质检合格待发运，可用于物流用例 |
| `AG20260528008` | `TC20260528008` | 小白菜 | ON_SALE | 8 | 部分售出，`ON_SALE` 状态样例 |
| `AG20260602009` | `TC20260602009` | 脐橙 | HARVESTED | 5 | 已采收待送检，可用于质检用例 |
| `AG20260605010` | `TC20260605010` | 红颜草莓 | PLANTING | 4 | 生长期批次，农事记录部分待审核 |
| `AG20260608011` | `TC20260608011` | 小白菜 | PLANTING | 4 | 生长期批次 |
| `AG20260610012` | `TC20260610012` | 脐橙 | PLANTING | 4 | 生长期批次 |

> **状态与记录自洽性**：`SOLD` 批次的销售记录累计数量等于批次产量（如批次 1 为 900+300=1200 kg）；`ON_SALE` 批次为部分售出（批次 8 售 250 kg / 共 700 kg）；`ABNORMAL` 批次均有对应的 `FAIL` 质检报告与处置任务；`PLANTING` 批次没有采收记录。验收时可直接用这几点核查数据设计是否严谨。
>
> 数据范围：`villageId=1`（溪头村）为批次 1、2、4、5、7、8、10、11；`villageId=2`（上寨村）为批次 3、6、9、12。`farmer01`（张大山）对应前者，`farmer02`（李桂花）对应后者。

---

## 二、自动化单元测试

### 2.1 执行方式

```bash
cd backend
mvn test
```

预期输出：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`，构建结果 `BUILD SUCCESS`。

### 2.2 用例清单

| 编号 | 测试类 | 方法 | 验证点 | 预期 |
| --- | --- | --- | --- | --- |
| UT-01 | `DigestHelperTest` | `batchDigestShouldBeStableAnd64Hex` | 同一批次多次计算结果一致，且为 64 位十六进制 | 通过 |
| UT-02 | `DigestHelperTest` | `batchDigestShouldChangeWhenTampered` | 修改任一字段后摘要变化 | 通过 |
| UT-03 | `DigestHelperTest` | `digestShouldTolerateNullFields` | 空字段不抛异常且结果稳定 | 通过 |
| UT-04 | `AuthServiceTest` | `shouldLoginWithCorrectPassword` | 正确账密登录成功并返回 token | 通过 |
| UT-05 | `AuthServiceTest` | `shouldRejectWrongPassword` | 错误密码返回 400「账号或密码错误」 | 通过 |
| UT-06 | `AuthServiceTest` | `shouldRejectDisabledAccount` | 禁用账号返回 403 | 通过 |
| UT-07 | `PlantingServiceTest` | `shouldRejectOtherFarmerBatch` | 农户操作他人物批次返回 403 | 通过 |
| UT-08 | `PlantingServiceTest` | `shouldRejectDuplicateSubmission` | 同批次同日同类型重复提交返回 409 | 通过 |
| UT-09 | `PlantingServiceTest` | `shouldRejectUnapprovedBatch` | 批次未核验时提交返回 4001 | 通过 |
| UT-10 | `PlantingServiceTest` | `shouldSaveAndMarkSkippedWhenChainDisabled` | 演示模式下落库且 `chainStatus=SKIPPED` | 通过 |
| UT-11 | `SalesServiceTest` | `abnormalBatchShouldBeBlocked` | 异常批次销售返回 409 | 通过 |
| UT-12 | `SalesServiceTest` | `unqualifiedBatchShouldBeBlockedBeforeLogistics` | 未质检合格不可发运，返回 4001 | 通过 |
| UT-13 | `SalesServiceTest` | `soldOutBatchShouldBeBlocked` | 库存售罄后再销售返回 409 | 通过 |

---

## 三、功能验收用例

### 3.1 登录与授权

#### TC-AUTH-01 正确账号登录

| 项 | 内容 |
| --- | --- |
| 前置 | 后端与 PC 端已启动，数据库有演示数据 |
| 步骤 | 1. 打开 `http://localhost:5173`<br>2. 输入 `admin` / `123456`<br>3. 点击登录 |
| 预期 | 1. 跳转 `/dashboard`<br>2. 侧边栏显示全部菜单<br>3. `localStorage` 存有 token<br>4. 右上角显示「系统管理员」 |

#### TC-AUTH-02 错误密码

| 项 | 内容 |
| --- | --- |
| 步骤 | 输入 `admin` / `000000` 登录 |
| 预期 | 提示「账号或密码错误」，停留在登录页，不产生 token |

#### TC-AUTH-03 Token 失效

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 登录成功<br>2. 手动清空 `localStorage.token`<br>3. 刷新页面 |
| 预期 | 自动跳转 `/login`，并携带 `redirect` 参数 |

#### TC-AUTH-04 未携带 Token 访问接口

```bash
curl -i http://localhost:8080/api/batch/page
```

| 项 | 内容 |
| --- | --- |
| 预期 | HTTP 401，响应体 `{"code":401,"message":"未登录或登录已过期"}` |

---

### 3.2 越权拦截（核心验收点）

#### TC-PERM-01 功能权限越权：农户访问质检接口

| 项 | 内容 |
| --- | --- |
| 前置 | `farmer01` 无 `quality:create` 权限 |
| 步骤 | 1. 用 `farmer01` 登录取 token<br>2. POST `/api/quality` 提交质检报告 |
| 预期 | HTTP 403，`{"code":403,"message":"无操作权限：quality:create"}`<br>数据库无新增质检记录 |

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"farmer01","password":"123456"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

curl -i -X POST http://localhost:8080/api/quality \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"batchId":1,"reportNo":"R001","inspectDate":"2026-05-10","inspectResult":"PASS"}'
```

#### TC-PERM-02 角色白名单越权：检测机构创建批次

| 项 | 内容 |
| --- | --- |
| 步骤 | 用 `inspector01` 的 token 调用 `POST /api/batch` |
| 预期 | 403，`{"message":"当前角色不允许该操作"}` |

#### TC-PERM-03 数据范围越权：农户查看他人批次

| 项 | 内容 |
| --- | --- |
| 前置 | `farmer01`（张大山）拥有批次 1、2、4、5、7、8、10、11；`farmer02`（李桂花）拥有批次 3、6、9、12 |
| 步骤 | 1. 用 `farmer02` 登录<br>2. 请求 `GET /api/batch/1` |
| 预期 | 403，`{"message":"无权访问该数据"}`，**不返回任何批次内容** |

#### TC-PERM-04 数据范围收窄：列表只返回本村数据

| 项 | 内容 |
| --- | --- |
| 步骤 | 用 `village01`（溪头村）请求 `GET /api/batch/page` |
| 预期 | 仅返回 `villageId=1` 的 8 个批次（1、2、4、5、7、8、10、11），不含上寨村的 3、6、9、12 |

#### TC-PERM-05 前端菜单随权限渲染

| 项 | 内容 |
| --- | --- |
| 步骤 | 分别用 `admin`、`inspector01`、`seller01` 登录 PC 端观察侧边栏 |
| 预期 | `admin` 显示全部菜单；`inspector01` 仅显示看板/批次/质检/溯源/存证；`seller01` 仅显示看板/批次/销售/溯源/存证 |

#### TC-PERM-06 越权页兜底

| 项 | 内容 |
| --- | --- |
| 步骤 | 用 `seller01` 直接在地址栏输入 `/system/user` |
| 预期 | 路由守卫拦截，跳转 `/403` 页面并提示无权限 |

---

### 3.3 批次全生命周期

#### TC-BATCH-01 创建批次并自动上链

| 项 | 内容 |
| --- | --- |
| 前置 | `producer01` 登录，`chain.enabled=true` |
| 步骤 | 1. 进入「批次管理」→「新建批次」<br>2. 选择产品「红颜草莓」、农场、农户<br>3. 种植日期 `2026-06-01`，预计产量 `800` kg<br>4. 提交 |
| 预期 | 1. 列表新增记录，状态「种植中」<br>2. 批次号格式 `AG20260601XXX`<br>3. 溯源码格式 `TC20260601XXX`<br>4. 链状态「已上链」，显示交易哈希<br>5. `chain_record` 新增 `biz_type=BATCH`、`status=SUCCESS` 记录 |

#### TC-BATCH-02 批次号唯一性

| 项 | 内容 |
| --- | --- |
| 步骤 | 同一天连续创建 3 个批次 |
| 预期 | 批次号末三位递增 `001`→`002`→`003`，无重复；数据库唯一索引不报错 |

#### TC-BATCH-03 录入农事记录（H5）

| 项 | 内容 |
| --- | --- |
| 前置 | `farmer01` 登录 H5；批次 3 状态为 `PLANTING`、核验状态为 `PENDING`，需先执行 TC-BATCH-02 完成核验 |
| 步骤 | 1. 工作台 →「农事记录」→「新增农事记录」<br>2. 选批次 `AG20260601003`、类型「追肥」、日期今天<br>3. 填写内容「施用复合肥 15kg/亩」<br>4. 拍照上传 1 张<br>5. 点击「获取定位」<br>6. 提交 |
| 预期 | 1. 提示「提交成功」并跳回列表<br>2. 新记录显示「已上链」<br>3. 照片可点击放大预览<br>4. 后端 `chain_record` 有 `biz_type=PLANTING` 记录 |

#### TC-BATCH-04 未来日期拦截

| 项 | 内容 |
| --- | --- |
| 步骤 | 新增农事记录，作业日期选择明天的日期 |
| 预期 | 前端日期选择器最大值为今天（无法选未来）；若绕过前端直接调接口，返回 400「作业日期不能晚于今天」 |

#### TC-BATCH-05 重复提交拦截

| 项 | 内容 |
| --- | --- |
| 步骤 | 对同一批次、同一日期、同一农事类型连续提交两次 |
| 预期 | 第二次返回 409 `{"message":"该批次当日已存在同类型农事记录，请勿重复提交"}`，数据库仅一条 |

#### TC-BATCH-06 采收信息提交（一批次一条）

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 对批次 3 提交采收：日期今天、数量 `2800` kg、等级 A<br>2. 再次对批次 3 提交采收 |
| 预期 | 1. 第一次成功，批次状态变为「已采收」<br>2. 第二次返回 409「该批次已存在采收记录」 |

#### TC-BATCH-07 农事 / 采收审核

| 项 | 内容 |
| --- | --- |
| 前置 | `village01` 登录 PC 端 |
| 步骤 | 1. 进入「农事记录」，找到 `PENDING` 记录<br>2. 点击「审核」→「通过」并填写意见<br>3. 进入「采收管理」审核采收记录 |
| 预期 | 记录状态变为「已通过」，审核人显示「王秀兰」，审核意见入库 |

#### TC-BATCH-08 质检合格全流程

| 项 | 内容 |
| --- | --- |
| 前置 | `inspector01` 登录 |
| 步骤 | 1. 进入「质检报告」→「上传报告」<br>2. 选批次 3，报告号 `JC2026-0601`，检测日期今天<br>3. 填写检测项（农残、重金属），结论「合格」<br>4. 上传 PDF 报告<br>5. 提交 |
| 预期 | 1. 提示上传成功，显示文件 SHA-256<br>2. 批次状态变为「质检合格」<br>3. 报告列表显示结论「合格」<br>4. 上链 `biz_type=QUALITY`，`file_hash` 有值 |

#### TC-BATCH-09 报告号唯一性

| 项 | 内容 |
| --- | --- |
| 步骤 | 用相同报告号 `JC2026-0601` 再次提交 |
| 预期 | 409「报告编号已存在」 |

#### TC-BATCH-10 物流交接登记

| 项 | 内容 |
| --- | --- |
| 前置 | `logistics01` 登录，批次 3 为「质检合格」 |
| 步骤 | 1. 进入「物流管理」→「新增交接」<br>2. 选批次 3，运单号 `SF20260601001`<br>3. 起运地「溪头村」、目的地「永辉超市闽侯店」<br>4. 车牌、司机、发运时间<br>5. 提交 |
| 预期 | 批次状态变为「运输中」，上链 `biz_type=LOGISTICS` |

#### TC-BATCH-11 未质检不可发运

| 项 | 内容 |
| --- | --- |
| 步骤 | 对状态为「已采收」的批次登记物流 |
| 预期 | 4001「批次状态不允许发运，当前状态：已采收」 |

#### TC-BATCH-12 销售登记

| 项 | 内容 |
| --- | --- |
| 前置 | `seller01` 登录，批次 3 为「运输中」 |
| 步骤 | 1. 进入「销售管理」→「新增销售」<br>2. 选批次 3，市场「永辉超市闽侯旗舰店」<br>3. 单价 `12.00`，数量 `500` kg<br>4. 提交 |
| 预期 | 1. 批次状态变为「在售」<br>2. 上链 `biz_type=SALES`<br>3. 再销 `2400` kg 后状态变为「已售出」<br>4. 继续销售返回 409「批次库存不足」 |

#### TC-BATCH-13 无物流不可销售

| 项 | 内容 |
| --- | --- |
| 步骤 | 对仅有采收记录的批次直接登记销售 |
| 预期 | 409「批次尚未完成物流交接，不可销售」 |

---

### 3.4 异常批次管控（核心验收点）

#### TC-ABN-01 质检不合格自动熔断

| 项 | 内容 |
| --- | --- |
| 前置 | `inspector01` 登录，准备一个「已采收」批次 |
| 步骤 | 1. 上传质检报告，结论选「不合格」，结论说明「毒死蜱残留 0.08mg/kg 超标」<br>2. 提交 |
| 预期 | 1. 批次状态变为「异常管控」，`abnormal_flag=1`<br>2. 自动生成处置任务（`biz_abnormal_disposal`，状态 PENDING）<br>3. 上链 `biz_type=ABNORMAL`<br>4. 合约状态变为 `ABNORMAL`<br>5. 批次列表该行标红 |

#### TC-ABN-02 异常批次禁止发运

| 项 | 内容 |
| --- | --- |
| 前置 | 批次 2 为异常状态 |
| 步骤 | 用 `logistics01` 对批次 2 登记物流 |
| 预期 | 4001「异常批次禁止发运，请先完成处置」 |

#### TC-ABN-03 异常批次禁止销售

| 项 | 内容 |
| --- | --- |
| 步骤 | 用 `seller01` 对批次 2 登记销售 |
| 预期 | 409「异常批次已熔断，禁止销售」；前端销售页该批次选项置灰不可选 |

#### TC-ABN-04 合约层熔断（链上强制）

| 项 | 内容 |
| --- | --- |
| 前置 | 合约状态为 `ABNORMAL` |
| 步骤 | 用 FISCO-BCOS Console 直接调用 `addRecord(batchNo, "SALES", ...)` |
| 预期 | 交易 revert，返回「abnormal batch: only DISPOSAL/CORRECTION allowed」<br>说明：即使绕过后端直接操作链，也无法写入非法记录 |

#### TC-ABN-05 异常处置登记

| 项 | 内容 |
| --- | --- |
| 前置 | `regulator01` 登录 |
| 步骤 | 1. 进入「异常管控」，找到批次 2<br>2. 点击「处置登记」<br>3. 处置措施「整批就地无害化销毁」<br>4. 处置结果「已销毁 600kg，全程录像留证」<br>5. 提交 |
| 预期 | 1. 处置状态变为「已处置」<br>2. 上链 `biz_type=DISPOSAL`<br>3. 处置时间与经办人入库 |

#### TC-ABN-06 上下游追溯与定责

| 项 | 内容 |
| --- | --- |
| 步骤 | 在批次 2 上点击「上下游追溯」 |
| 预期 | 抽屉/弹窗展示：<br>1. **上游**：行政村（溪头村）、农场（溪头一号草莓大棚）、农户（张大山）、生产主体<br>2. **下游**：物流企业、销售商、买家、销售市场<br>3. **责任主体卡片**：标注「种植环节 - 张大山」为主要责任环节<br>4. 关联记录时间轴可点击跳转 |

#### TC-ABN-07 结案归档

| 项 | 内容 |
| --- | --- |
| 步骤 | 处置完成后点击「结案」 |
| 预期 | 1. 批次状态变为「已结案」<br>2. 上链 `biz_type=CLOSE`<br>3. 该批次不可再新增任何业务记录（返回 4001） |

#### TC-ABN-08 异常批次仍可写更正说明

| 项 | 内容 |
| --- | --- |
| 步骤 | 对异常批次提交更正说明 |
| 预期 | 提交成功（熔断只针对正常流转，更正与处置始终允许），上链 `biz_type=CORRECTION` |

---

### 3.5 更正说明机制

#### TC-CORR-01 追加更正说明

| 项 | 内容 |
| --- | --- |
| 前置 | `producer01` 登录，批次 1 有一条农事记录 |
| 步骤 | 1. 进入批次详情 → 某条农事记录 →「更正说明」<br>2. 原因选「录入笔误」<br>3. 内容填写「实际用量为 12kg/亩，原记录 21kg/亩有误」<br>4. 提交 |
| 预期 | 1. 更正说明新增成功<br>2. **原记录内容保持不变**（关键验证点）<br>3. 上链 `biz_type=CORRECTION`<br>4. 溯源时间轴按时间顺序同时展示原记录与更正说明 |

#### TC-CORR-02 原始记录不可编辑

| 项 | 内容 |
| --- | --- |
| 步骤 | 尝试通过任何界面编辑已提交的农事记录内容 |
| 预期 | 界面无编辑入口；直接调用后端无对应 PUT 接口，返回 405/404 |

#### TC-CORR-03 更正后一致性仍通过

| 项 | 内容 |
| --- | --- |
| 步骤 | 提交更正说明后，对批次 1 做一致性校验 |
| 预期 | `passed=true`，原始记录摘要与链上一致，更正说明作为独立记录单独校验 |

---

### 3.6 链上链下一致性校验（核心验收点）

#### TC-CHAIN-01 正常批次校验通过

| 项 | 内容 |
| --- | --- |
| 前置 | `chain.enabled=true`，批次 1 全流程已完成且全部上链成功 |
| 步骤 | 批次详情 →「一致性校验」 |
| 预期 | 1. 绿色提示「链上链下数据一致，未被篡改」<br>2. 显示「共 N 条存证，匹配 N 条」<br>3. 每条记录 `hashMatched=true`、`receiptOk=true` |

接口直调：

```bash
curl http://localhost:8080/api/chain/verify/AG20260512001 \
  -H "Authorization: Bearer $TOKEN"
```

预期片段：

```json
{
  "code": 0,
  "data": {
    "batchNo": "AG20260512001",
    "chainEnabled": true,
    "passed": true,
    "totalRecords": 6,
    "matchedRecords": 6
  }
}
```

#### TC-CHAIN-02 篡改数据库后可被发现（关键演示）

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 直接修改数据库：`UPDATE biz_harvest_record SET quantity=9999 WHERE batch_id=1;`<br>2. 重新执行一致性校验 |
| 预期 | 1. `passed=false`<br>2. 提示「第 N 条记录摘要不一致，疑似被篡改」<br>3. 该条记录 `hashMatched=false`，并展示 `localHash` 与 `chainHash` 的差异<br>4. 未修改的其他记录仍显示匹配 |

#### TC-CHAIN-03 回执校验

| 项 | 内容 |
| --- | --- |
| 步骤 | 手工把某条 `chain_record.tx_hash` 改为一个不存在的哈希，再校验 |
| 预期 | `receiptOk=false`，该条判定不通过，提示「交易回执校验失败」 |

#### TC-CHAIN-04 演示模式不伪造通过

| 项 | 内容 |
| --- | --- |
| 前置 | `chain.enabled=false` |
| 步骤 | 创建新批次并执行一致性校验 |
| 预期 | 1. 存证状态为灰色「演示模式」（`SKIPPED`）<br>2. 校验返回 `passed=false`、`chainEnabled=false`<br>3. 提示「未接入区块链，无法校验」<br>4. **绝不显示存证成功** |

#### TC-CHAIN-05 上链失败如实标记

| 项 | 内容 |
| --- | --- |
| 前置 | `chain.enabled=true` 但 `contract-address` 配置错误 |
| 步骤 | 创建一个批次 |
| 预期 | 1. 业务数据正常入库<br>2. `chain_status=FAILED`，前端显示红色「上链失败」<br>3. `chain_record.error_msg` 记录具体原因<br>4. 界面不出现「存证成功」字样 |

---

### 3.7 扫码溯源（消费者侧）

#### TC-TRACE-01 免登录查询

| 项 | 内容 |
| --- | --- |
| 步骤 | 未登录状态访问 H5 `#/trace?code=TC20260512001` |
| 预期 | 1. 无需登录直接展示结果<br>2. 显示产品名「红颜草莓」、产地、农户、种植/采收日期<br>3. 质检结论「合格」<br>4. 时间轴按时间正序展示全部环节<br>5. 每条记录显示存证徽标 |

#### TC-TRACE-02 手动输入溯源编号

| 项 | 内容 |
| --- | --- |
| 步骤 | 在溯源页输入框输入 `TC20260512001` 后回车 |
| 预期 | 同 TC-TRACE-01 |

#### TC-TRACE-03 支持批次号兜底

| 项 | 内容 |
| --- | --- |
| 步骤 | 输入批次号 `AG20260512001` |
| 预期 | 同样返回完整履历（后端同时支持溯源码与批次号） |

#### TC-TRACE-04 无效编码

| 项 | 内容 |
| --- | --- |
| 步骤 | 输入 `NOTEXIST123` |
| 预期 | 提示「未查询到该批次，请核对溯源码」，页面不报错、不白屏 |

#### TC-TRACE-05 二维码内容正确

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. PC 端批次详情点击「溯源二维码」<br>2. 用手机扫码 |
| 预期 | 二维码内容为 `{h5Base}/#/trace?code={traceCode}`，扫码后直接打开 H5 溯源页并自动查询 |

#### TC-TRACE-06 异常批次溯源可见管控状态

| 项 | 内容 |
| --- | --- |
| 步骤 | 查询批次 2（`TC20260515002`） |
| 预期 | 顶部显示红色「该批次已被管控」，质检结论「不合格」，时间轴含异常标记与处置记录 |

---

### 3.8 多端适配

#### TC-MOBILE-01 农户端完整操作链

| 项 | 内容 |
| --- | --- |
| 步骤 | iPhone Safari 用 `farmer01` 登录 H5 → 工作台 → 农事记录 → 新增（含拍照、定位）→ 提交 → 我的批次 → 批次详情 |
| 预期 | 全流程无横向滚动条，底部安全区不被遮挡，拍照可调用系统相机/相册，提交成功有 toast |

#### TC-MOBILE-02 消费者扫码链路

| 项 | 内容 |
| --- | --- |
| 步骤 | 微信扫一扫 PC 端生成的二维码 |
| 预期 | 打开 H5 溯源页，自动展示批次履历，无需登录 |

#### TC-MOBILE-03 响应式检查

| 项 | 内容 |
| --- | --- |
| 步骤 | 分别在 375×667、390×844、414×896 三种尺寸下浏览 |
| 预期 | 卡片、按钮、时间轴均正常排布；文字不溢出；点击区域不小于 40px |

#### TC-PC-01 管理端分辨率适配

| 项 | 内容 |
| --- | --- |
| 步骤 | 在 1366×768 与 1920×1080 下浏览看板与表格页 |
| 预期 | 侧边栏可折叠；表格出现横向滚动条而非挤压变形；图表自适应宽度 |

#### TC-UNI-01 一套代码三端分发

| 项 | 内容 |
| --- | --- |
| 步骤 | `frontend/uni-app` 执行 `npm run build:h5` 与 `npm run build:mp-weixin` |
| 预期 | 两个产物均编译成功；H5 ≤ 2MB；小程序 `app.json` 中 `pages` 不含任何 `pages-pc/*`，`tabBar` 为 4 项 |
| 实测 | 通过（2026-11-05）：H5 1.5MB；小程序 28 页 / 0 个 `pages-pc` / tabBar 4 项 |

#### TC-UNI-02 屏宽自动分发

| 项 | 内容 |
| --- | --- |
| 步骤 | H5 以 1440×900 打开 `http://localhost:5173`，再以 390×844 打开同一地址 |
| 预期 | 宽屏落在 `/pages-pc/dashboard/index`；窄屏落在移动工作台 `/pages/tabbar/home` |
| 实测 | 通过：1440×900 → PC 管理台，14 个页面均可访问 |

#### TC-UNI-03 微信登录绑定闭环

| 项 | 内容 |
| --- | --- |
| 步骤 | ① `POST /auth/wx-login` 首次 → ② 拿 `bindTicket` 调 `POST /auth/wx-bind`（admin/123456）→ ③ 再次 `POST /auth/wx-login` → ④ `GET /auth/wx-status` → ⑤ `POST /auth/wx-unbind` → ⑥ 再次 `POST /auth/wx-login` |
| 预期 | ① `needBind=true` 且有 `bindTicket`；② 返回与账号密码登录同结构的 `token/user/permissions`；③ 直接返回 token，不再要求绑定；④ `true`；⑤ 成功；⑥ 回到 `needBind=true` |
| 实测 | 全部通过；错误密码绑定返回 `401 账号或密码错误`；重复绑定他人微信返回 409 |

#### TC-UNI-04 三端实时同步（WebSocket）

| 项 | 内容 |
| --- | --- |
| 步骤 | PC 端停在批次列表不动，从另一个客户端（模拟小程序端）调用 `POST /batch/{id}/audit` 核验一个 PENDING 批次 |
| 预期 | PC 列表在数秒内自动刷新，该行"审核"按钮消失，用户未做任何点击或刷新 |
| 实测 | 通过：3 秒内完成，行文本由「… 种植中 未存证 详情 核验 审核」变为「… 种植中 未存证 详情 核验」 |
| 说明 | 服务端 `/api/ws?token=` 广播 `{"type":"batch","action":"AUDIT"}`，客户端收到后标记缓存脏 + 刷新当前页 |

#### TC-UNI-05 三端账号与数据一致性

| 项 | 内容 |
| --- | --- |
| 步骤 | 同一账号 `farmer01` 分别在 H5、小程序（开发者工具）、App 登录，一端新增农事记录后，另外两端下拉刷新 |
| 预期 | 三端 `userId/roleCode/permissions` 完全一致；新增记录在另外两端可见；无任何端使用本地库，全部走同一 REST 接口 |
| 说明 | 后端 JWT 无状态（12h、无 Redis 单点限制），三端并发登录互不影响 |

#### TC-UNI-06 令牌静默续期

| 项 | 内容 |
| --- | --- |
| 步骤 | ① 正常登录 → ② 把 `localStorage.agri_token` 改成非法值（模拟 access 过期），保留 `agri_refresh_token` → ③ 刷新页面并进入批次列表 → ④ 清掉 refresh 后重复一次 |
| 预期 | ② 登录返回 `token` + `refreshToken`；③ 自动调 `/auth/refresh` 换新并在新 token 下重放原请求，列表正常出数、**不跳登录页**；④ refresh 也失效时才 `reLaunch` 到登录页 |
| 实测 | 通过（`node scripts/e2e-refresh.js`，四项断言全 PASS，控制台仅剩故意制造的 401） |
| 补充 | 两个 token 靠 `typ` 声明隔离：refresh 塞进 `Authorization` 头 → 401；access 调 `/auth/refresh` → 401（均已实测） |

#### TC-UNI-07 并发修改冲突（乐观锁）

| 项 | 内容 |
| --- | --- |
| 步骤 | 对同一 PENDING 批次同时发起 60 个 `POST /batch/{id}/audit`（`node <temp>/race.js 11 60`） |
| 预期 | 只有 1 个 200；其余全部 409，且**不出现两条"核验通过"**；数据库 `version` 只 +1 |
| 实测 | 通过：`{"200":1,"409":59}`；其中 54 次由"该批次已核验"前置校验拦下，**5 次通过前置校验后被 `AND version=?` 拦下**，说明乐观锁在真正兜底 |
| 补充 | 生成的 SQL 含 `WHERE id=? AND version=? AND deleted=0`；`AbnormalService.dispose/close` 已改为"先落库再上链"，避免并发冲突在链上留下重复存证 |

#### TC-UNI-08 微信 App 端登录（开放平台）

| 项 | 内容 |
| --- | --- |
| 步骤 | ① `POST /auth/wx-app-login {"code":"x"}` → ② 用票据绑定 admin → ③ 再次 App 登录 → ④ `GET /auth/wx-status?platform=app` 与 `?platform=mp` → ⑤ `POST /auth/wx-unbind?platform=app` → ⑥ 再次 App 登录 |
| 预期 | ① `needBind=true` + 票据；② 返回 token 且写库落在 `wx_app_openid`；③ 直接出 token；④ app=`true`、mp=`false`（两端绑定互相独立）；⑤ 成功；⑥ 回到 `needBind=true` |
| 实测 | 全部通过（未配置开放平台资质时 App 端独立 mock，日志显式提示 `mock-app-openid-local-demo`） |

---

### 3.9 文件上传与摘要

#### TC-FILE-01 上传返回 SHA-256

| 项 | 内容 |
| --- | --- |
| 步骤 | 上传一张 JPG 质检报告 |
| 预期 | 响应含 `id`、`filePath`、`fileHash`（64 位）、`fileSize`；`fileHash` 与本地 `sha256sum` 结果一致 |

#### TC-FILE-02 非法扩展名拦截

| 项 | 内容 |
| --- | --- |
| 步骤 | 上传 `.exe` 文件 |
| 预期 | 400「不支持的文件类型，仅允许：jpg,jpeg,png,webp,pdf」 |

#### TC-FILE-03 超大文件拦截

| 项 | 内容 |
| --- | --- |
| 步骤 | 上传 25MB 文件 |
| 预期 | 400「文件大小不能超过 20MB」 |

#### TC-FILE-04 文件下载免登录

| 项 | 内容 |
| --- | --- |
| 步骤 | 未登录状态访问 `/api/file/download/{id}` |
| 预期 | 正常返回文件流（用于溯源页展示质检报告） |

---

### 3.10 操作日志

#### TC-LOG-01 写操作留痕

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 用 `admin` 创建一个批次<br>2. 进入「操作日志」 |
| 预期 | 日志新增一条：操作人 `admin`、模块 `batch`、方法 `POST`、路径 `/batch`、耗时、IP |

#### TC-LOG-02 查询操作不记日志

| 项 | 内容 |
| --- | --- |
| 步骤 | 连续刷新批次列表 3 次，查看日志 |
| 预期 | 无新增日志（切面只记录非 GET 请求） |

---

## 四、兼容性与性能

### 4.1 浏览器兼容矩阵

| 浏览器 | 版本 | PC 端 | H5 |
| --- | --- | --- | --- |
| Chrome | 120+ | ✅ | ✅ |
| Edge | 120+ | ✅ | ✅ |
| Firefox | 121+ | ✅ | ✅ |
| Safari (macOS) | 17+ | ✅ | ✅ |
| Safari (iOS) | 16+ | — | ✅ |
| Android Chrome | 120+ | — | ✅ |
| 微信内置浏览器 | 8.x | — | ✅ |

### 4.2 性能验收

| 指标 | 目标值 | 测量方式 |
| --- | --- | --- |
| 登录接口响应 | < 500ms | 浏览器 Network |
| 批次列表（1000 条数据） | < 800ms | 分页查询 10 条 |
| 一致性校验（10 条存证） | < 3s | 含链上查询耗时 |
| 首次加载（PC 端） | < 2s | 生产构建 + gzip |
| 首次加载（H5） | < 1.5s | 生产构建 |
| 并发 50 请求 | 无错误、无超时 | JMeter / ab |

```bash
# 简单压测
ab -n 200 -c 50 -H "Authorization: Bearer $TOKEN" \
   http://localhost:8080/api/batch/page
```

---

## 五、缺陷等级定义

| 等级 | 定义 | 处理要求 |
| --- | --- | --- |
| P0 致命 | 系统无法启动、数据丢失、越权可直接读写他人数据 | 必须修复后才能验收 |
| P1 严重 | 核心流程阻断（无法创建批次/无法上链/异常未熔断） | 必须修复 |
| P2 一般 | 功能可用但结果不符预期（提示文案错误、状态未刷新） | 迭代内修复 |
| P3 轻微 | 样式错位、文案瑕疵 | 可延后 |

---

## 六、验收通过标准

| 类别 | 通过标准 |
| --- | --- |
| 自动化测试 | `mvn test` 全部通过，13 个用例 0 失败 |
| 功能测试 | 本文 P0 / P1 用例 100% 通过 |
| 权限测试 | 越权用例（TC-PERM-01 ~ 06）全部拦截成功 |
| 一致性测试 | 篡改后能被发现（TC-CHAIN-02），正常批次校验通过（TC-CHAIN-01） |
| 无链模式 | 演示模式可完整跑通全流程且不伪造存证（TC-CHAIN-04） |
| 多端 | PC 与 H5 核心路径均可完成 |
| 缺陷 | 无 P0、P1 遗留缺陷 |

---

## 七、附录：快速回归脚本

```bash
#!/usr/bin/env bash
# 接口级快速回归（需先启动后端）
BASE=http://localhost:8080/api

login() {
  curl -s -X POST $BASE/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"123456\"}" \
    | grep -o '"token":"[^"]*' | cut -d'"' -f4
}

echo "== 1. 登录 =="
T_ADMIN=$(login admin)
[ -n "$T_ADMIN" ] && echo "  ✔ admin 登录成功" || echo "  ✘ admin 登录失败"

T_FARMER=$(login farmer01)
T_FARMER2=$(login farmer02)

echo "== 2. 未登录访问 =="
code=$(curl -s -o /dev/null -w "%{http_code}" $BASE/batch/page)
[ "$code" = "401" ] && echo "  ✔ 未登录返回 401" || echo "  ✘ 期望 401，实际 $code"

echo "== 3. 越权拦截 =="
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/quality \
  -H "Authorization: Bearer $T_FARMER" -H "Content-Type: application/json" \
  -d '{"batchId":1,"reportNo":"X","inspectDate":"2026-05-10","inspectResult":"PASS"}')
[ "$code" = "403" ] && echo "  ✔ 农户调质检接口返回 403" || echo "  ✘ 期望 403，实际 $code"

echo "== 4. 数据范围 =="
code=$(curl -s -o /dev/null -w "%{http_code}" $BASE/batch/1 -H "Authorization: Bearer $T_FARMER2")
[ "$code" = "403" ] && echo "  ✔ 跨农户访问返回 403" || echo "  ✘ 期望 403，实际 $code"

echo "== 5. 公开溯源 =="
ok=$(curl -s $BASE/trace/TC20260512001 | grep -c '"code":0')
[ "$ok" -ge 1 ] && echo "  ✔ 免登录溯源可用" || echo "  ✘ 溯源接口异常"

echo "== 6. 一致性校验 =="
curl -s $BASE/trace/TC20260512001/verify | head -c 200
echo ""

echo "回归完成"
```
