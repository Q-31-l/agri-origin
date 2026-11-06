# 部署操作手册

> 农产品质量安全溯源系统 · 环境搭建与部署指南
> 适用于本地开发、答辩演示与云服务器生产部署

---

## 一、环境依赖

### 1.1 必装软件

| 软件 | 版本要求 | 用途 | 校验命令 |
| --- | --- | --- | --- |
| JDK | 1.8（推荐 8u301+） | 后端编译与运行 | `java -version` |
| Maven | 3.6+ | 后端构建 | `mvn -v` |
| MySQL | 8.0+（5.7 亦可） | 链下业务库 | `mysql --version` |
| Node.js | 18.x / 20.x | 前端构建 | `node -v` |
| npm | 9.x+ | 前端依赖 | `npm -v` |
| Git | 任意 | 代码拉取 | `git --version` |

### 1.2 联盟链环境（可选，用于真实上链）

| 软件 | 版本 | 说明 |
| --- | --- | --- |
| FISCO-BCOS | 2.9.x | 区块链节点 |
| Solidity 编译器 | 0.6.10 | 由 `contracts` 工程内的 npm 包提供，无需单独安装 |
| FISCO-BCOS Console | 2.9.x | 可选，用于查询区块与交易 |

> **没有链也能跑**：系统内置"演示模式"，`chain.enabled=false` 时业务全流程可正常演示，所有存证标记为 `SKIPPED`（演示模式），**不会伪造存证成功**。答辩现场无链环境时请使用该模式。

### 1.3 端口规划

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| MySQL | 3306 | 数据库 |
| 后端 Spring Boot | 8080 | 上下文路径 `/api` |
| PC 管理端（dev） | 5173 | Vite 开发服务器 |
| H5 移动端（dev） | 5174 | Vite 开发服务器 |
| FISCO-BCOS 节点 | 20200 / 20201 | channel 端口 |
| FISCO-BCOS 节点 | 8545 / 8546 | JSON-RPC（可选） |

---

## 二、第一步：初始化数据库

### 2.1 创建数据库并执行脚本

```bash
# 1) 登录 MySQL
mysql -u root -p

# 2) 创建库（init.sql 内已含 CREATE DATABASE，可跳过此步）
CREATE DATABASE IF NOT EXISTS agri_trace DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
```

```bash
# 3) 执行建表脚本（在仓库根目录）
mysql -u root -p < sql/init.sql

# 4) 导入演示数据
mysql -u root -p < sql/data.sql
```

Windows PowerShell 下如果重定向报编码错误，改用：

```powershell
Get-Content sql/init.sql -Raw -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4
Get-Content sql/data.sql -Raw -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4
```

### 2.2 验证

```sql
USE agri_trace;
SHOW TABLES;                    -- 应输出 19 张表
SELECT COUNT(*) FROM sys_user;  -- 应为 9
SELECT batch_no, trace_code, status FROM biz_batch;  -- 应为 3 条演示批次
SELECT role_code, role_name, data_scope FROM sys_role; -- 应为 8 个角色
```

### 2.3 演示账号

所有账号密码统一为 **`123456`**。

| 账号 | 姓名 | 角色 | 数据范围 | 主要用途 |
| --- | --- | --- | --- | --- |
| `admin` | 系统管理员 | ADMIN | ALL | 全功能演示、账号管理 |
| `regulator01` | 陈立 | REGULATOR | ALL | 异常处置、上下游定责 |
| `producer01` | 林建国 | PRODUCER | ORG | 创建批次、产品建档 |
| `village01` | 王秀兰 | VILLAGE_ADMIN | VILLAGE | 农户管理、记录审核 |
| `farmer01` | 张大山 | FARMER | SELF | H5 农事记录、采收提交 |
| `farmer02` | 李桂花 | FARMER | SELF | 越权测试对照账号 |
| `inspector01` | 周敏 | INSPECTOR | ORG | 质检报告上传与判定 |
| `logistics01` | 郑海 | LOGISTICS | ORG | 物流交接登记 |
| `seller01` | 吴晓芸 | SELLER | ORG | 销售登记 |

### 2.4 版本升级

后续表结构变更统一放在 `sql/upgrade/` 目录，按 `V{日期}_{说明}.sql` 命名，例如：

```
sql/upgrade/V20260301_add_chain_index.sql
```

---

## 三、第二步：编译与部署智能合约

> 仅当需要真实上链时执行。使用演示模式可跳过本节，直接进入第四节。

### 3.1 编译合约

```bash
cd contracts
npm install
npm run compile
```

产物：

```
contracts/abi/Traceability.abi     # 接口描述
contracts/abi/Traceability.json    # ABI + bytecode
contracts/bin/Traceability.bin     # 部署字节码
```

编译成功标准：控制台输出 `✔ Traceability.sol 编译成功`，且 `bin/Traceability.bin` 大小约 41 KB。

### 3.2 搭建链节点（本地单机四节点）

```bash
# 下载并解压 build_chain.sh（以 FISCO-BCOS 2.9.0 为例）
mkdir -p ~/fisco && cd ~/fisco
curl -#LO https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/v2.9.0/build_chain.sh
chmod u+x build_chain.sh

# 生成 4 节点、单群组（group 1）链
bash build_chain.sh -l 127.0.0.1:4 -p 20200

# 启动
bash nodes/127.0.0.1/start_all.sh
```

验证节点存活：

```bash
tail -f nodes/127.0.0.1/node0/log/log_*.log
# 看到 "g:1,blk:..." 即正常出块
```

### 3.3 拷贝 SDK 证书

```bash
cp nodes/127.0.0.1/sdk/*.crt  <repo>/backend/src/main/resources/fisco/conf/
cp nodes/127.0.0.1/sdk/*.key  <repo>/backend/src/main/resources/fisco/conf/
```

确认目录内容：

```
backend/src/main/resources/fisco/conf/
├── ca.crt
├── sdk.crt
└── sdk.key
```

> 这三个文件是节点准入凭证，已被根目录 `.gitignore` 排除，**不要提交到仓库**。

### 3.4 配置节点地址

编辑 `backend/src/main/resources/fisco/config.toml`：

```toml
[cryptoMaterial]
certPath = "conf"

[network]
peers = ["127.0.0.1:20200", "127.0.0.1:20201"]
```

> 若节点部署在远程服务器，把 `127.0.0.1` 换成服务器公网/内网 IP，并确保 20200-20203 端口可达。

### 3.5 开启链配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
chain:
  enabled: true                 # 从 false 改为 true
  config-file: classpath:fisco/config.toml
  group-id: 1
  contract-address: ""          # 部署后再填
  abi-path: classpath:contracts/Traceability.abi
  private-key: ""               # 留空则用 SDK 临时账户；只在本机调试时填，生产环境见 10.2（backend.env）
  strict: true
```

### 3.6 部署合约

**方式 A：一键脚本（推荐）**

```bash
# 在仓库根目录执行
bash contracts/scripts/deploy.sh
```

脚本会自动构建后端 Jar 并调用部署入口，成功后输出：

```
=================================================
 合约部署成功
 合约地址 : 0x8f2a...c41d
...
在区块高度 : 0x1a
=================================================
```

**方式 B：手动指定参数**

```bash
bash contracts/scripts/deploy.sh \
     --group-id 1 \
     --private-key 4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318
```

**方式 C：手动执行 Java 入口**

```bash
cd backend && mvn -DskipTests clean package
cd ..
java -cp backend/target/agri-trace-backend.jar \
     -Dloader.main=com.agri.trace.sdk.ContractDeployTool \
     org.springframework.boot.loader.PropertiesLauncher
```

### 3.7 回填合约地址

把上一步输出的地址写入 `application.yml`：

```yaml
chain:
  contract-address: "0x8f2a...c41d"
```

重启后端，日志出现以下内容即接入成功：

```
FISCO-BCOS 客户端初始化成功：group=1, contract=0x8f2a..., block=26
```

---

## 四、第三步：启动后端

### 4.1 修改数据库连接

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/agri_trace?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 你的数据库密码
```

> 生产环境建议改用环境变量注入：`${DB_URL}`、`${DB_USERNAME}`、`${DB_PASSWORD}`，避免密码入库。

### 4.2 编译与运行

```bash
cd backend

# 跑单元测试
mvn test

# 打包
mvn clean package -DskipTests

# 运行
java -jar target/agri-trace-backend.jar
```

或直接开发模式启动：

```bash
mvn spring-boot:run
```

### 4.3 验证

| 检查项 | 地址 | 预期 |
| --- | --- | --- |
| 服务存活 | `http://localhost:8080/api/chain/status` | 返回 JSON，含 `enabled` / `ready` |
| 接口文档 | `http://localhost:8080/api/doc.html` | Knife4j 调试页面 |
| 登录接口 | `POST http://localhost:8080/api/auth/login` | `{"code":0,...}` |

登录验证示例：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

### 4.4 关键配置项说明

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | 8080 | 后端端口 |
| `server.servlet.context-path` | `/api` | 所有接口前缀 |
| `jwt.secret` | — | **生产必须修改**，否则存在伪造 token 风险 |
| `jwt.expire-hours` | 12 | token 有效期（小时） |
| `chain.enabled` | false | `false` 为演示模式 |
| `chain.strict` | true | `true` 时上链失败抛出异常回滚业务；`false` 时落库并标记 `FAILED` |
| `storage.upload-dir` | `./uploads` | 文件存储目录，生产建议改绝对路径 |
| `storage.allowed-ext` | `jpg,jpeg,png,webp,pdf,mp4,mov,m4v,webm` | 允许的扩展名（含图片与现场视频） |
| `storage.max-size` | 104857600 | 单文件上限 100MB（视频实证需要较大体积） |
| `mybatis-plus.configuration.log-impl` | StdOutImpl | 生产建议改为 `NoLoggingImpl` 提升性能 |

---

## 五、第四步：启动 PC 管理端

```bash
cd frontend/pc-admin
npm install
npm run dev
```

访问 `http://localhost:5173`，使用 `admin / 123456` 登录。

**开发代理**（`vite.config.js`）：

```js
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

**生产构建**：

```bash
npm run build      # 产物在 dist/
```

---

## 六、第五步：启动移动端 H5

```bash
cd frontend/mobile-h5
npm install
npm run dev
```

访问 `http://localhost:5174`，使用 `farmer01 / 123456` 登录农户端；
消费者可直接访问 `http://localhost:5174/#/trace?code=TC20260512001` 免登录溯源。

**生产构建**：

```bash
npm run build      # 产物在 dist/，base 已配置为 './'，可放任意子目录
```

> H5 使用 hash 路由，静态服务器无需额外配置 `try_files`。
> 若要让二维码指向真实域名，修改 `frontend/pc-admin/src/views/batch/detail.vue` 中的 `h5Base` 常量。

---

## 六·补、第五步（推荐）：启动三端统一工程 uni-app

`frontend/uni-app` 是一个工程出三个端，替代上面的 `pc-admin` + `mobile-h5`。两者共用 5173 端口，**不要同时启动**。

### 1. 安装与开发

```bash
cd frontend/uni-app
npm install
```

| 端 | 命令 | 访问方式 |
| --- | --- | --- |
| H5 / PC | `npm run dev:h5` | `http://localhost:5173`（宽度 ≥900px 自动进 PC 管理台，窄屏进移动工作台） |
| 微信小程序 | `npm run dev:mp-weixin` | 微信开发者工具导入 `dist/dev/mp-weixin` |
| App | `npm run dev:app` | HBuilderX 运行到手机或模拟器 |

### 2. 生产构建

```bash
npm run build:h5          # dist/build/h5          1.5MB，history 路由，Nginx 需配 try_files
npm run build:mp-weixin   # dist/build/mp-weixin   28 页，PC 页面不进包，微信开发者工具上传
npm run build:app         # dist/build/app         HBuilderX 云打包生成 apk / ipa
```

`pages.json` 由 `src/pages.full.json` + `node scripts/gen-pages.js` 生成（脚本按平台裁剪页面，注释形式的 `// #ifdef` 会被原样保留）。**不要直接手改 `pages.json`**，改完 `pages.full.json` 后执行：

```bash
node scripts/gen-pages.js            # 重新生成
node scripts/gen-pages.js --restore  # 还原为全量（本地开发用）
```

### 3. 微信登录配置

小程序与 App 是**两套 appid**（前者在小程序后台，后者在微信开放平台移动应用），配置项分开：

```yaml
app:
  wx:
    # 小程序（sns/jscode2session）
    appid: ${WX_APPID:}             # 小程序后台 → 开发管理 → 开发设置
    secret: ${WX_SECRET:}
    # App（sns/oauth2/access_token）
    app-appid: ${WX_APP_APPID:}     # 微信开放平台 → 移动应用 → AppID
    app-secret: ${WX_APP_SECRET:}
    # 留空 appid/secret 时该端自动 mock，可完整走通绑定流程；显式强制 mock 才置 true
    mock-enabled: ${WX_MOCK:false}
```

两端的 openid **不是同一个值**，服务端分别存 `wx_openid`（小程序）与 `wx_app_openid`（App），
所以「小程序绑过微信」不等于「App 也绑过」——同一个人两端各绑一次，账号仍是同一个。

真机联调时还需：

- 小程序：`frontend/uni-app/src/manifest.json` 填 `mp-weixin.appid`，微信后台把 `https://<api域名>` 加入 request / uploadFile / socket 合法域名；
- App：`manifest.json` → `app-plus.distribute.sdkConfigs.oauth.weixin` 填开放平台 `appid` 与 `UniversalLinks`（iOS 必须），云打包勾选 **OAuth 登录** 模块。

已有数据库升级（幂等，可重复执行）：

```bash
mysql -uroot -p agri_trace < sql/patch-wx-login.sql        # 微信绑定字段 + 唯一索引
mysql -uroot -p agri_trace < sql/patch-optimistic-lock.sql # 乐观锁 version 列
```

全新建库无需执行这两个补丁，`sql/init.sql` 已包含。

### 3·补. 令牌有效期

```yaml
jwt:
  expire-hours: 12          # access token
  refresh-expire-days: 7    # refresh token，过期后才需要重新登录
```

前端在 access 过期时会自动调 `/auth/refresh` 换发，用户无感知；只有 refresh 也过期（默认 7 天未使用）才回登录页。
无需 Redis、无需会话表——refresh token 本身就是自校验的 JWT。

### 4. WebSocket 反向代理

实时通知走 `wss://<域名>/api/ws?token=xxx`，Nginx 必须显式配置升级头，否则握手 400：

```nginx
location /api/ws {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 300s;   # 大于客户端 25s 心跳间隔即可
}
```

---

## 七、第六步：生产部署（Nginx）

### 7.1 目录规划

```
/opt/agri-origin/
├── app/agri-trace-backend.jar
├── app/uploads/
├── web/admin/            # PC 端 dist
├── web/h5/               # H5 端 dist
└── logs/
```

### 7.2 启动后端

```bash
nohup java -jar /opt/agri-origin/app/agri-trace-backend.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/agri_trace?..."
  > /opt/agri-origin/logs/app.log 2>&1 &
```

建议用 systemd 托管：

```ini
# /etc/systemd/system/agri-trace.service
[Unit]
Description=Agri Trace Backend
After=network.target mysql.service

[Service]
Type=simple
WorkingDirectory=/opt/agri-origin/app
ExecStart=/usr/bin/java -Xms512m -Xmx1g -jar /opt/agri-origin/app/agri-trace-backend.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now agri-trace
systemctl status agri-trace
```

### 7.3 Nginx 配置

```nginx
server {
    listen       80;
    server_name  trace.example.com;

    # PC 管理端
    location / {
        root  /opt/agri-origin/web/admin;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 移动端 H5
    location /h5/ {
        alias /opt/agri-origin/web/h5/;
        index index.html;
        try_files $uri $uri/ /h5/index.html;
    }

    # 后端接口
    location /api/ {
        proxy_pass         http://127.0.0.1:8080/api/;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        client_max_body_size 120m;
    }

    # 上传文件（若使用静态映射）
    location /uploads/ {
        alias /opt/agri-origin/app/uploads/;
        expires 7d;
    }
}
```

#### 7.3.1 启用 HTTPS（扫码功能的前提）

浏览器只在**安全上下文**（HTTPS 或 `localhost`）下开放 `navigator.mediaDevices.getUserMedia`。
手机端 H5 扫码要调摄像头，站点必须是 HTTPS，否则点"扫码"只能落到"从相册选图"。
因此 80 端口除了 ACME 校验外全部 301 到 HTTPS，用户输 IP 也会自动跳过去。

有域名时最省事：

```bash
certbot --nginx -d trace.example.com
```

没有域名、只有公网 IP 时，用 `sslip.io` 的通配解析 + `acme.sh` 也能签出受信任证书
（`8-130-105-150.sslip.io` 会自动解析到 `8.130.105.150`，无需买域名）：

```bash
# 1) 安装 acme.sh（若官方安装脚本报 cannot copy，手动落到 ~/.acme.sh 再 --install）
curl -s https://get.acme.sh | sh -s email=you@example.com

# 2) HTTP-01 签发：nginx 的 80 端口必须能从公网访问到 /.well-known/acme-challenge/
~/.acme.sh/acme.sh --issue -d 8-130-105-150.sslip.io --webroot /var/www/html

# 3) 装到 nginx 读的位置，并让 acme.sh 在续期后自动 reload
~/.acme.sh/acme.sh --install-cert -d 8-130-105-150.sslip.io \
  --key-file       /etc/nginx/ssl/agri.key \
  --fullchain-file /etc/nginx/ssl/agri.crt \
  --reloadcmd      "systemctl reload nginx"

# 4) 确认续期定时任务存在（用官方脚本装 acme.sh 失败过一次，cron 就没装上，
#    结果证书 90 天后在 ~/.acme.sh 里续了、nginx 里还是过期的那份）
crontab -l | grep acme.sh || ~/.acme.sh/acme.sh --install-cronjob
```

> 验证续期链路是否真的通了：`grep -E 'Le_Real|Le_Reload'
> ~/.acme.sh/<域名>/<域名>.conf` 应当能看到 `Le_RealFullChainPath` 与 `Le_ReloadCmd`。
> 只有手抄过证书、没跑 `--install-cert` 时，这两项为空，续期不会更新 nginx。

nginx 侧把 80/443 的公共 location 抽到 snippet 里复用（80 只做 ACME 与 HTTP→HTTPS 跳转，
443 挂证书并开 http2）。完整文件见仓库 `ops/nginx-agri-origin.conf` 与 `ops/nginx-agri-app.conf`：

```nginx
# 裸 IP 没有证书，先映射成同 IP 的 sslip.io 域名再跳，
# 否则用户输 8.130.105.150 会被跳到 https://8.130.105.150 然后证书报错
map $host $agri_canonical_host {
    default                                                          $host;
    "~^(?<a>\d{1,3})\.(?<b>\d{1,3})\.(?<c>\d{1,3})\.(?<d>\d{1,3})$"  "$a-$b-$c-$d.sslip.io";
}

server {
    listen 80 default_server;
    server_name _;
    # ACME 必须留在 80 明文，且不能被下面的 301 吃掉
    location ^~ /.well-known/acme-challenge/ { root /var/www/acme; default_type text/plain; try_files $uri =404; }
    location / { return 301 https://$agri_canonical_host$request_uri; }
}
server {
    listen 443 ssl http2 default_server;
    server_name 8-130-105-150.sslip.io;
    ssl_certificate     /etc/nginx/ssl/agri.crt;
    ssl_certificate_key /etc/nginx/ssl/agri.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    include /etc/nginx/snippets/agri-app.conf;   # /api/ws、/api/、静态资源、history 回退
}
```

> **为什么要强制跳**：手机端扫码是实时取景，HTTP 页面拿不到摄像头，点"扫码"只能
> 退化成"从相册选一张二维码照片"。统一跳 HTTPS 之后，手机浏览器 / 微信里点扫码
> 就是真的实时扫，不会出现两种行为。
>
> **踩坑**：如果 `curl https://<域名>/` 通、公网却超时，先别急着改配置——
> 可能只是**云安全组没放行 443**。在控制台入方向加一条 `443/TCP` 即可，服务器里改不了。
> 反过来，本机 `curl` / socket 探测超时但浏览器能打开时，多半是本机代理或沙箱只放行了 80，
> 用浏览器或外部探测（例如在线 HTTP 检测服务）复核一次再下结论。

后端生成溯源码里的跳转地址也要跟着改成 HTTPS，否则扫码后落到 HTTP 页面（又变成非安全上下文）：

```bash
# /etc/systemd/system/agri-backend.service
Environment="TRACE_URL_PREFIX=https://8-130-105-150.sslip.io/pages/trace/index?code="
```

### 7.4 部署检查清单

- [ ] `jwt.secret` 已改为随机长字符串
- [ ] `spring.datasource.password` 改为环境变量注入
- [ ] `mybatis-plus.configuration.log-impl` 改为 `NoLoggingImpl`
- [ ] `storage.upload-dir` 改为绝对路径并确保写权限
- [ ] `chain.enabled=true` 且 `contract-address` 已填
- [ ] 证书文件权限设为 `600`，属主为运行用户
- [ ] 数据库账号不使用 root，改为最小权限账号
- [ ] 已配置日志轮转（logrotate）
- [ ] 已配置定时备份（`mysqldump` + 上传目录）
- [ ] 已上 HTTPS 且 80 已 301 到 HTTPS，`TRACE_URL_PREFIX` 也指向 HTTPS（否则扫码只能选图）
- [ ] 云安全组已放行 443（只放行 80 时，HTTPS 在服务器内可达、公网不可达）

---

## 八、演示模式运行（无链环境）

答辩现场若无法联网或无链节点，按以下配置运行：

```yaml
chain:
  enabled: false
```

行为说明：

| 能力 | 演示模式表现 |
| --- | --- |
| 登录、权限、越权拦截 | 完全正常 |
| 批次创建与全流程流转 | 完全正常 |
| 异常熔断（业务层） | 完全正常（Service 层拦截） |
| 异常熔断（合约层） | 不生效（无链） |
| 存证状态 | 统一为 `SKIPPED`，前端显示灰色「演示模式」 |
| 一致性校验 | 返回 `passed=false` + `chainEnabled=false`，提示"未接入区块链，无法校验" |
| 存证流水 | 正常入库，`tx_hash` / `block_number` 为空 |

**答辩讲解要点**：系统在无链环境下**不会显示"存证成功"**，这是刻意设计——宁可展示"未上链"，也不伪造可信性。接入链后无需改一行代码即可切换为真实存证。

---

## 九、常见问题排查

### 9.1 后端启动失败

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `Access denied for user` | 数据库账号密码错误 | 检查 `application.yml` 的 `username` / `password` |
| `Unknown database 'agri_trace'` | 未执行 `init.sql` | 执行 `mysql -u root -p < sql/init.sql` |
| `Table 'agri_trace.sys_user' doesn't exist` | 未执行 `data.sql` 或建表失败 | 重新执行两个脚本并检查报错 |
| `Port 8080 was already in use` | 端口占用 | `netstat -ano \| findstr 8080` 找到进程后终止，或改 `server.port` |
| 启动卡在 `HikariPool` | 数据库不可达 | 检查 MySQL 是否启动、防火墙是否放行 |

### 9.2 链相关

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `缺少链证书文件：fisco/conf/ca.crt` | 证书未拷贝 | 从节点 `sdk/` 目录拷贝三个文件 |
| `chain.contract-address 未配置或格式错误` | 未部署或地址为空 | 执行 `contracts/scripts/deploy.sh` 后回填地址 |
| `链客户端初始化失败：connect failed` | 节点未启动或 peers 配置错 | 检查 `config.toml` 的 `peers`，确认节点日志正常出块 |
| `交易回执状态异常：0x16` | 合约 revert（多为权限或状态不符） | 查看后端日志确认具体方法，检查部署账户是否被 `authorize` |
| 存证状态一直 `FAILED` | SDK 与节点版本不匹配 | 确认节点为 2.9.x，SDK 为 2.9.1 |
| 后端日志出现 `ConnectionManager - connect to 127.0.0.1:20200 failed` / `Processing message timeout` | **计划内停链**（升级、重启节点）时 SDK 内部重连器的正常输出 | 无需处理；链恢复后 `/chain/status` 自动回到 `ready=true`，不必重启后端。若链没停却持续刷，再查节点进程与 20200-20203 端口 |
| `fisco-bcos -v` 是新版本但 `getClientVersion` 仍报旧版本 | 换错文件：systemd 执行的是 `nodes/127.0.0.1/fisco-bcos` | 换对路径后重启四节点，见 11.4 |

### 9.3 前端相关

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 接口 404 | 代理未生效或后端未启动 | 确认后端在 8080 且 `context-path=/api` |
| 登录后立刻跳回登录页 | token 存储失败或已过期 | 清 `localStorage` 后重试；检查 `jwt.expire-hours` |
| `npm install` 卡住 | 网络问题 | 使用镜像：`npm install --registry=https://registry.npmmirror.com` |
| 页面空白且控制台报 `Failed to fetch dynamically imported module` | 路由指向的页面文件缺失 | 确认 `src/pages` 下文件齐全，重新 `npm run build` |
| 上传图片 404 | `storage.public-prefix` 与实际访问路径不一致 | 默认 `/api/file/download/`，确认 Nginx 已代理 `/api/` |

### 9.4 中文乱码

- 数据库：确保库、表、连接串均为 `utf8mb4`；
- 后端：`server.servlet.encoding.charset=UTF-8` 且 `force=true`（已默认配置）；
- 脚本导入：Windows 下使用 `--default-character-set=utf8mb4`。

---

## 十、生产环境：进程托管、故障自愈与上线自检

本章对应 `ops/` 目录，全部配置均为线上实际在用的那一份，可直接复制。

### 10.1 链节点交给 systemd（不再用 start.sh）

`build_chain.sh` 生成的 `start.sh` 用 `ps aux | grep fisco-bcos` 判断节点是否在跑，
四个节点共用一个二进制路径，于是：只要有一个节点活着，其余三个就会被判为"已运行"并跳过启动。
节点数掉到 2 个以下后 PBFT 凑不够 2/3，区块停止增长，表现为"接口不报错但上链一直不成功"。

改为一个节点一个 systemd 实例，由 systemd 自己的 cgroup 追踪 PID：

```bash
cp ops/fisco-node@.service /etc/systemd/system/
cp ops/fisco-chain.target /etc/systemd/system/
systemctl daemon-reload
systemctl enable fisco-node@node0 fisco-node@node1 fisco-node@node2 fisco-node@node3
systemctl start  fisco-node@node0 fisco-node@node1 fisco-node@node2 fisco-node@node3
systemctl enable fisco-chain.target

ps -ef | grep -c '[f]isco-bcos -c config.ini'   # 期望 4
```

### 10.2 敏感参数：私钥与口令放 backend.env

链账户私钥能以合约 owner 身份签名，泄露等于把存证写权限交出去；数据库口令、JWT 签名密钥同理。
这类值一律不写进 systemd 单元、也不进版本库，改由 `EnvironmentFile=` 注入：

```bash
install -m 600 ops/backend.env.example /opt/agri-origin/app/backend.env
vi /opt/agri-origin/app/backend.env      # 填 CHAIN_PRIVATE_KEY / DB_PASSWORD / JWT_SECRET
systemctl daemon-reload && systemctl restart agri-backend
```

`ops/agri-backend.service` 对应的三处取值：

| 配置项 | 来源 |
| --- | --- |
| `--chain.private-key=${CHAIN_PRIVATE_KEY}` | `backend.env` |
| `spring.datasource.password` | 环境变量 `DB_PASSWORD`（`application.yml` 写的是 `${DB_PASSWORD:root}`） |
| `jwt.secret` | 环境变量 `JWT_SECRET`（`application.yml` 保留了仅供本地开发的默认值） |

两道兜底，避免"配错了还照常启动"：

- `EnvironmentFile=` 指向的文件不存在 → systemd 直接启动失败；
- `ExecStartPre` 再校验一次 `CHAIN_PRIVATE_KEY` 非空 → 空值同样拒绝启动。

> **私钥一旦外泄怎么处理**：光改文件没用，要在链上收回权限。
> 若后端账户正是合约 owner，用旧账户执行 `transferOwnership(新地址)`；
> 若只是被 `authorize()` 的账户，先 `revoke(旧地址)` 再 `authorize(新地址)`
> （方法签名见 `contracts/README.md`）。历史区块里的旧签名无法抹除，但写权限已经收回。

### 10.3 启动顺序：链先就绪，后端再起

`ops/agri-backend.service` 里 `After=` 与 `Wants=` 都指向 `fisco-chain.target`。
即便如此，仍可能出现"后端先起、链还没监听 20200"的竞态——所以客户端不再"初始化一次失败就永久不可用"：

- 首次构建失败后进入 10 秒冷却，之后每个业务请求都会尝试重建 SDK 连接；
- `isReady()` 附带 3 秒缓存的健康探测，节点中途宕机会把 `ready` 立即置为 `false`，
  恢复后自动回到 `true`，**全程不需要重启后端进程**。

验证方式：

```bash
systemctl stop fisco-node@node0 fisco-node@node1 fisco-node@node2 fisco-node@node3
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/chain/status   # ready=false + 失败原因
systemctl start fisco-node@node0 fisco-node@node1 fisco-node@node2 fisco-node@node3
sleep 20
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/chain/status   # ready=true，不重启后端
```

### 10.4 nginx：WebSocket 必须单独配 Upgrade

```nginx
location = /api/ws {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

少了这两个头，nginx 把握手当普通 HTTP 转给后端，日志里会持续刷
`Handshake failed due to invalid Upgrade header: null`，三端"一端操作、多端同步"直接失效。

自检：

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  "http://8.130.105.150/api/ws?token=$TOKEN"     # 期望 101
```

无 token 或 token 非法时应返回 **401**（而不是 200）。

### 10.5 上线自检脚本

```bash
python3 scripts/e2e-acceptance.py
```

覆盖 49 项断言：8 个角色登录、建档→核验→农事→采收→质检→物流→销售逐环节真实上链、
监管复核、免登录溯源、链上链下一致性、更正留痕、不合格熔断→销售拦截→处置→结案、
越权与权限负例（403/409）、数据范围隔离（看板与列表口径一致）、区块高度递增。

脚本直连 `http://127.0.0.1:8080/api`，需在服务器本机执行（避免公网抖动影响判定）。

### 10.6 日志轮转（必做）

`ops/agri-backend.service` 用 systemd 的 `StandardOutput=append:` 把标准输出直接追加到
`/opt/agri-origin/app/backend.log`，systemd 自身不做轮转，Spring Boot 也没有配置文件输出。
**若不配置 logrotate，该文件会无限增长**（实测约 9MB/天，一年约 3GB，逐步吃满根分区）。

```bash
cp ops/logrotate-agri-origin.conf /etc/logrotate.d/agri-origin
chmod 644 /etc/logrotate.d/agri-origin
logrotate -d /etc/logrotate.d/agri-origin   # 演练，确认无语法错误
```

关键点：

- 必须用 `copytruncate`。systemd 以 `O_APPEND` 长期持有该文件句柄，直接 `mv` 重命名后
  进程仍写旧 inode，新文件永远是空的；`copytruncate` 先复制再截断，无需重启服务。
- 策略为 `daily + maxsize 50M + rotate 14 + compress`：无论按时间还是按体积，都不会失控。

> 首次安装时 `logrotate -d` 可能提示 `log has already been rotated`。这是因为尚无状态记录时
> logrotate 以日志文件的 mtime 作为上次轮转时间，属正常现象，次日或超过 50MB 即会轮转。

---

## 十一、WeBASE-Front 可视化控制台与链版本升级

项目七条业务链路都已真实上链，但只有 txHash 是不够的——**要能"看见"区块和交易**才算闭环。
WeBASE-Front 是官方出的联盟链控制台（区块浏览器 + 交易回执 + 合约 ABI 调用 + 节点监控），
版本 **1.5.5** 配套链 **2.9.1**。

### 11.1 安装（不重建链）

已有的 `/opt/fisco` 链**不需要重建**，Front 只是这条链的一个 SDK 客户端：

```bash
# 1) 下载并解压（约 136MB）
curl -fL --noproxy '*' -o /opt/webase-front.zip \
  https://github.com/WeBankBlockchain/WeBASELargeFiles/releases/download/v1.5.5/webase-front.zip
mkdir -p /opt/webase-front && cd /opt/webase-front
python3 -c "import zipfile;zipfile.ZipFile('/opt/webase-front.zip').extractall('/opt/webase-front')"

# 2) Front 按 <nodePath>/sdk 读证书；build_chain 只在 nodes/127.0.0.1 下放了一份 sdk
ln -s ../sdk /opt/fisco/nodes/127.0.0.1/node0/sdk
cp /opt/fisco/nodes/127.0.0.1/sdk/{ca.crt,sdk.crt,sdk.key} /opt/webase-front/webase-front/conf/

# 3) 改 conf/application.yml（只改这三处，端口保持 5002）
#    constant.nodePath    -> /opt/fisco/nodes/127.0.0.1/node0
#    constant.monitorDisk -> /opt/fisco
#    sdk.certPath         -> /opt/webase-front/webase-front/conf

# 4) 用 systemd 托管
cp ops/webase-front.service /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now webase-front
```

> **坑 1：不能用 `java -jar`。** `apps/WeBASE-Front.jar` 没有 `Main-Class`，
> 直接 `-jar` 报 `no main manifest attribute`。官方 `start.sh` 用的是
> `-cp "conf/:apps/*:lib/*" com.webank.webase.front.Application`，systemd 单元必须照抄这个写法。
>
> **坑 2：`nodePath` 必须指向 node 目录，且该目录下要有 `sdk/`。** build_chain 把证书放在
> `nodes/127.0.0.1/sdk`（四个节点共用），node0 下没有，所以要补软链。
>
> **坑 3：事件订阅页空白不是故障。** 出块/合约事件订阅依赖 RabbitMQ（默认 127.0.0.1:5672），
> 未安装时那两页显示空表，其余功能不受影响。

### 11.2 公网暴露必须加认证

WeBASE-Front **没有内置登录**，而它能用节点私钥签交易、部署合约——裸奔在公网等于把链的控制权交出去。
因此只监听 `127.0.0.1:5002`，由 nginx 反代并强制 HTTP Basic 认证：

```nginx
location /WeBASE-Front/ {
    auth_basic "WeBASE-Front";
    auth_basic_user_file /etc/nginx/.htpasswd_webase;
    proxy_pass http://127.0.0.1:5002/WeBASE-Front/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 300s;
    proxy_buffering off;
}
```

```bash
printf 'webase:%s\n' "$(openssl passwd -apr1 '你的密码')" > /etc/nginx/.htpasswd_webase
chmod 640 /etc/nginx/.htpasswd_webase && chown root:www-data /etc/nginx/.htpasswd_webase
nginx -t && systemctl reload nginx
```

访问：`http://8.130.105.150/WeBASE-Front/`（演示账号 `webase` / `Agri@2026`）。

验收要看到三件事，缺一不可：

```bash
# 无认证必须 401，带认证必须 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/WeBASE-Front/                    # 401
curl -s -u webase:Agri@2026 -o /dev/null -w '%{http_code}\n' http://127.0.0.1/WeBASE-Front/ # 200
# 高度必须与后端一致
curl -s -u webase:Agri@2026 http://127.0.0.1/WeBASE-Front/1/web3/blockNumber
```

### 11.3 用 Front 回读业务交易（证明"看的是同一条链"）

`scripts/front-verify.py` 从 `chain_record` 取一条真实存证，再用 Front 的 REST 接口回读：
区块里只有 1 笔交易、hash 与库里一致、`to` 是部署的合约地址、回执 `status=0x0`。

```bash
python3 scripts/front-verify.py
# 业务存证: 0xcf27af77b804d2983f7c0b...
#   批次=AG20261106008 类型=CLOSE 区块=151
# Front 区块高度: 151
# 区块 151 交易数: 1
# 命中业务交易: 是
#   from=0xfabd07030babe671b9  to=0x0426e8fbda2b5e61c6  input 长度=324 字节
# 回执: status=0x0 区块=151 gasUsed=54613
```

### 11.4 HTTPS 站点下合约IDE 报「Solc版本加载失败」

**现象**：控制台其它页面都正常，一进「合约管理 → 合约IDE」就弹
`Solc版本加载失败，请检查/static/js目录中是否存在对应的合约版本`。

**不是缺文件**。`v0.4.25.js` 等 6 个 solc 编译器文件都在，HTTP 访问时也一切正常。
真正的原因是前端把编译器地址**写死成 `http://`**，再在 Web Worker 里 `importScripts()` 加载：

```js
url: "http://" + this.host + "/WeBASE-Front/static/js/v0.4.25.js"
```

页面一旦切到 HTTPS，浏览器按**混合内容**直接拦截，请求根本到不了 nginx，
所以 nginx 日志里连一条 `v0.4.25.js` 的记录都没有——这也是定位该问题的关键特征。

**修法**：把协议改成跟随页面（`location.protocol`），并把补丁文件放进
`conf/static/static/js/`。启动 classpath 是 `conf/:apps/*:lib/*`，`conf/` 排在 jar 之前，
所以能覆盖 jar 内同名资源，**厂商 jar 一个字节都不用动**。

同一次修复还解决了第二个隐性缺陷：前端个别接口写死
`headers:{Authorization:"Token "+localStorage.getItem("token")||!1}`，
未登录时发出 `Authorization: Token null`，把 nginx 的 Basic 认证顶掉，
导致「编译成功但保存/方法注册 401」。改成没有 token 就不带这个头即可。

```bash
bash ops/webase-front-https-fix.sh        # 幂等，自动备份、打补丁、重启、自检
```

验收（用无痕窗口，避免旧 chunk 缓存）：

1. 打开 `/WeBASE-Front/#/contract`，不再弹错，版本下拉显示 `v0.4.25`；
2. 左侧 `template` 展开 → 点开 `Evidence.sol` → 点「编译」，
   下方出现 `contractName / abi / bytecodeBin` 结果面板；
3. `POST /WeBASE-Front/contract/save`、`POST /WeBASE-Front/method/add` 均返回 200。

回滚：`rm -f /opt/webase-front/webase-front/conf/static/static/js/*.js && systemctl restart webase-front`。

**修完别人还看到旧报错？是浏览器缓存**。这个控制台的 chunk 文件名带内容哈希，
但热补丁是「同名文件、内容变了」，浏览器启发式缓存会继续用旧文件。
已在 `/WeBASE-Front/` 的 nginx location 里加 `add_header Cache-Control "no-cache" always;`
（见 `ops/nginx-agri-app.conf`），此后每次打开都会回源校验，有变化自动下发新文件。
老用户只需 **Ctrl+F5 强刷一次**即可拿到修复后的文件。

> **「合约列表」显示暂无数据不是故障**：该页只查 `contractStatus=2`（已部署）的合约，
> 只编译不部署不会出现在那里；本系统的合约由后端 SDK 部署，同样不进这张表。
> 要看业务合约与存证，用「数据概览 / 区块与交易」或后端的 `/api/chain/record` 更直接。

**已知非阻塞项：在线工具的 ACE 编辑器缺一个 JSON 校验 worker**。官方 1.5.5 安装包没有随包
`ace/c755e7fc…js`（webpack 构建产物），首次打开「在线工具 → ABI编码/Event查看」的 JSON 编辑器时，
后台会报一条 `importScripts` 404。**不影响编辑器显示与输入，也不影响 Hash/签名/其它页面**，
只是少了「输入 JSON 时实时红线校验」这一项。属厂商打包缺文件，无法在不动前端的前提下补齐。

### 11.5 链版本升级（2.9.0 → 2.9.1）

2.9.1 是 2.9.x 的补丁版本，数据与证书完全兼容，**原地换二进制**即可，不需要重建链：

```bash
curl -fL -o /tmp/fisco291.tar.gz \
  https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/v2.9.1/fisco-bcos.tar.gz
mkdir -p /tmp/fisco291 && tar -xzf /tmp/fisco291.tar.gz -C /tmp/fisco291 && chmod +x /tmp/fisco291/fisco-bcos

systemctl stop fisco-node@node0 fisco-node@node1 fisco-node@node2 fisco-node@node3
cp -p /opt/fisco/nodes/127.0.0.1/fisco-bcos /opt/fisco/nodes/127.0.0.1/fisco-bcos-2.9.0.bak
install -m 755 /tmp/fisco291/fisco-bcos /opt/fisco/nodes/127.0.0.1/fisco-bcos
systemctl start fisco-node@node0 fisco-node@node1 fisco-node@node2 fisco-node@node3
```

> **最容易踩的坑：改错文件。** `build_chain` 会在 `nodes/127.0.0.1/` 下再放一份二进制，
> systemd 单元执行的是 `nodes/127.0.0.1/fisco-bcos`，**不是**安装目录根下的 `/opt/fisco/fisco-bcos`。
> 换错文件的表现是：`fisco-bcos -v` 显示 2.9.1，但 `getClientVersion` 仍报 2.9.0，
> 因为进程用的还是旧文件。判据只认节点自报版本：

```bash
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","method":"getClientVersion","params":[],"id":1}' http://127.0.0.1:8545
# {"FISCO-BCOS Version":"2.9.1","Supported Version":"2.9.0", ...}
```

`Supported Version` 仍是 `2.9.0` 是**正确**的：它来自 `config.ini` 的 `supported_version`，
标识创世兼容版本，官方明确要求不可修改。后端 `/chain/status` 返回的 `nodeVersion` 取的是前者。

升级后必须复验：节点 4/4 active、`connectedNodes=3`、区块高度不倒退、`scripts/e2e-acceptance.py` 全绿。

---

### 11.6 WeBASE-Front HTTP 调用实战（不依赖 SDK 的第二种接入方式）

后端业务主链路走 Java SDK；此外还打通了**纯 HTTP 经 WeBASE-Front 调合约**的方式，
与参考材料《WeBASE-Front HTTP 调用实战》同款 `/trans/handle` 全流程。

**定位**：WeBASE-Front 是「私钥托管 + ABI 编解码 + 交易签名 + 回执轮询」的交易网关。
业务系统只发 HTTP POST 就能调链，`view` 函数走 `eth_call`（不上链），写函数同步等回执。

**前提（缺一不可）**：
1. WeBASE-Front 本地密钥库里有签名用户（`/trans/handle` 的 `user` 字段）；
2. 合约地址 + 整包 ABI；
3. `version` 与 `contractAddress` 至少给一个；`funcParam` 在 1.5.5 是**字符串数组**。

**本环境实测结果**：

| 对象 | 地址 | 方式 | 结果 |
|---|---|---|---|
| 生产合约 Traceability | `0x0426e8fb…bbedb` | HTTP 只读 | `getBatchCount()=44`、`getBatch(...)` 正确 |
| 演示合约 StorageCenter | `0x26e59c7c…aa9` | HTTP 读写 | `writeData` 区块 212、`accessData` 区块 213，回执 `0x0`，事件 1 条 |

**建用户 + 部署演示合约 + 跑全流程**：

```bash
# 1) 建一个本地签名用户（随机 32 字节私钥，WeBASE 自动派生地址）
KEY=$(openssl rand -hex 32)
curl -s -u webase:Agri@2026 \
  "http://127.0.0.1:5002/WeBASE-Front/privateKey/import?privateKey=$KEY&userName=agri-admin"
# 返回 {"address":"0x...","userName":"agri-admin",...} —— 记下 address

# 2) 一键跑全流程脚本（探活/只读/写入/回执/计数，见 scripts/webase-http-demo.py）
FRONT_BASE=http://127.0.0.1:5002/WeBASE-Front python3 scripts/webase-http-demo.py
```

**注意**：本机 WeBASE-Front 服务端编译器固定 `solc 0.4.25`；`/contract/contractCompile` 的
入参是 `{contractName, solidityBase64}`（源码要 Base64），出参 `contractAbi` 是**字符串**需二次解析；
`/contract/deploy` 返回的是**裸合约地址字符串**，不是 JSON——客户端要按文本处理。

**把业务合约登记进合约库**（让控制台「合约列表/IDE」能看到本系统的合约及真实地址）：

```bash
# POST /contract/save —— contractSource 存源码、contractAbi 存 ABI 字符串、contractAddress 存已部署地址
curl -s -u webase:Agri@2026 -X POST http://127.0.0.1:5002/WeBASE-Front/contract/save \
  -H 'Content-Type: application/json' -d '{
    "groupId":1, "contractName":"Traceability", "contractPath":"agri",
    "contractSource":"<Traceability.sol 源码>", "contractAbi":"<Traceability.abi 文本>",
    "contractAddress":"0x0426e8fbda2b5e61c64c56773ab251e03f4bbedb",
    "contractBin":"<bytecode>", "bytecodeBin":"<bytecode>", "version":"0.6.10"
  }'
```

登记后合约库应有：`Traceability`（path=agri，地址 `0x0426e8fb…`）与 `StorageCenter`（path=demo，
地址 `0x26e59c7c…`）。注意「合约列表」UI 只显示 `contractStatus=2`（经控制台部署）的条目，
SDK 部署的 `Traceability` 登记后是 `contractStatus=1`，在「合约IDE」的 `agri` 目录可见、可打开编译，
经 HTTP `/trans/handle` 可直接读写——两条链上路径都已打通。

---

## 十二、附录：一键本地启动脚本

`scripts/dev-start.sh`（可选自建）：

```bash
#!/usr/bin/env bash
set -e

echo "==> 检查数据库"
mysql -u root -p -e "SELECT 1" > /dev/null

echo "==> 启动后端"
(cd backend && mvn -q spring-boot:run) &
sleep 20

echo "==> 启动 PC 管理端"
(cd frontend/pc-admin && npm run dev) &

echo "==> 启动 H5"
(cd frontend/mobile-h5 && npm run dev) &

echo "全部服务已启动："
echo "  PC  : http://localhost:5173"
echo "  H5  : http://localhost:5174"
echo "  API : http://localhost:8080/api"
wait
```

---

## 十三、参考资料

实训任务书「素材准备」要求准备的 FISCO BCOS 官方开发文档，统一收录于此，便于搭建与排错时查证。

### 13.1 FISCO BCOS

| 资料 | 地址 |
| --- | --- |
| 官方文档总入口 | https://fisco-bcos-documentation.readthedocs.io/zh-cn/latest/ |
| 2.9.x 版本文档 | https://fisco-bcos-documentation.readthedocs.io/zh-cn/v2.9.0/ |
| 搭建联盟链（build_chain.sh） | https://fisco-bcos-documentation.readthedocs.io/zh-cn/v2.9.0/docs/installation.html |
| Java SDK 使用说明 | https://fisco-bcos-documentation.readthedocs.io/zh-cn/v2.9.0/docs/sdk/java_sdk.html |
| 合约开发（Solidity） | https://fisco-bcos-documentation.readthedocs.io/zh-cn/v2.9.0/docs/smartcontract/ |
| 控制台使用 | https://fisco-bcos-documentation.readthedocs.io/zh-cn/v2.9.0/docs/manual/console.html |
| 源码仓库 | https://github.com/FISCO-BCOS/FISCO-BCOS |
| Java SDK 仓库 | https://github.com/FISCO-BCOS/java-sdk |

> **版本提示**：本工程按 **链 2.9.x + Java SDK 2.9.1** 适配。SDK 3.x 的 API 与 2.9.x 差异较大（`TransactionProcessor` 系列类已重构），不要混用，否则会出现 `NoSuchMethodError`。

### 13.2 业务标准

| 标准号 | 名称 | 在本项目中的用途 |
| --- | --- | --- |
| GB 2763-2021 | 食品安全国家标准 食品中农药最大残留限量 | 质检报告的农残限量判定依据（毒死蜱 0.02 mg/kg） |
| GB 2762-2022 | 食品安全国家标准 食品中污染物限量 | 重金属判定依据（镉 0.05 mg/kg） |

> 上述限量为教学演示取值，实际判定应以标准原文和检测机构出具的限量要求为准。

### 13.3 框架文档

| 框架 | 文档 |
| --- | --- |
| Spring Boot 2.7 | https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/ |
| MyBatis-Plus | https://baomidou.com/ |
| Vue 3 | https://cn.vuejs.org/guide/ |
| Element Plus | https://element-plus.org/zh-CN/ |
| Vant 4 | https://vant-ui.github.io/vant/ |
| Solidity 0.6.10 | https://docs.soliditylang.org/en/v0.6.10/ |
