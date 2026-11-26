# 房屋租赁管理系统

包含后端（Spring Boot 3.4.2，Java 17）和 **三个独立前端**（管理端 / 员工移动端 / 租户自助端）：

| 目录 | 说明 | 端口 |
|---|---|---|
| `backend/` | 后端服务，唯一 API 提供方 | 8081 |
| `frontend/` | 管理端（桌面浏览器，Element Plus） | 5173 |
| `staff-mobile/` | 员工移动端（扫码录入/报修，Capacitor 打 APK） | 5180 |
| `tenant-frontend/` | 租户自助端（查账单/在线支付/报修/投诉/续租申请/电子签署） | 5188 |

移动端已从 `frontend/` 完全拆出：独立依赖、独立构建产物、独立 Android 工程（`com.house.staff`），可单独部署与发版。

租户自助端是独立部署的 Web 应用（手机浏览器即可，无需装 App），与员工端共用同一套后端接口，但**面向租客**：查看账单并在线支付、提交报修/投诉并确认评价、申请续租、完成电子签约。

## 环境要求

- JDK **17**（Lombok 与 JDK 21+ 不兼容，编译会报 `TypeTag :: UNKNOWN`）
- Maven 3.6+
- Node 18+
- MySQL 8.x

## 后端（backend）

运行：

```bash
cd backend
mvn spring-boot:run   # 默认端口 8081，可用 SERVER_PORT 覆盖
```

指定 JDK 17（macOS 多版本时）：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run
```

后端默认使用 `application.yml` 中配置的 MySQL 数据库。敏感配置通过环境变量覆盖：

| 变量 | 说明 |
|---|---|
| `JWT_SECRET` | JWT 签名密钥 |
| `CRYPTO_SECRET` | 身份证等字段的 AES 加密密钥 |
| `UPLOAD_DIR` | 上传目录 |
| `QR_BASE_URL` | 二维码指向的前端地址 |

### 数据库初始化

首次初始化：

```bash
mysql -uroot -proot < backend/sql/init_db.sql
```

老版本升级按顺序执行增量脚本（全部幂等，可重复执行）：

```bash
for f in backend/sql/migrate_*.sql; do mysql -uroot -proot < "$f"; done
```

单独执行时按文件名顺序即可，关键依赖：

```bash
mysql -uroot -proot < backend/sql/migrate_add_missing_entity_columns.sql   # 多数查询依赖
mysql -uroot -proot < backend/sql/migrate_add_building_image_url.sql
mysql -uroot -proot < backend/sql/migrate_add_repair_and_appliance.sql
mysql -uroot -proot < backend/sql/migrate_add_operation_snapshot.sql
mysql -uroot -proot < backend/sql/migrate_add_theme_preference.sql
mysql -uroot -proot < backend/sql/migrate_normalize_status_values.sql
mysql -uroot -proot < backend/sql/migrate_files_to_db.sql   # 文件统一存数据库（image_blob.stored_name）
```

说明：

- 楼栋卡片图片依赖 `building.image_url` 字段，未执行该迁移会导致楼栋查询报错。
- `migrate_normalize_status_values.sql` 把历史数据中房间/账单状态的中英文混写统一到规范值，并补充查询索引。代码已兼容两种写法，但执行后筛选与索引效率更好。
- `migrate_add_remind_log_scene.sql` 给 `remind_log` 加 `scene` 列，把租户登录/改手机号的验证码短信从「催缴记录」里分离出来（历史数据会按 `room_number` 自动回填）。

### 敏感字段加密

`tenant.id_card` 与 `sms_config.access_secret` 通过 `CryptoConverter`（AES/GCM，随机 IV）加密存储，密文带 `v2:` 前缀。

**升级老库需要先执行列宽迁移**，否则密文写不进去（`Data too long`）：

```bash
mysql -uroot -proot < backend/sql/migrate_widen_encrypted_columns.sql
```

启动时 `SensitiveDataEncryptionMigrator` 会自动把历史明文就地加密，幂等、可重复执行。密钥来自 `app.crypto.secret`（环境变量 `CRYPTO_SECRET`），经 SHA-256 派生为 256 位，因此密钥长度不受限。

> 历史坑：旧实现把密钥硬编码成 18 字节，而 AES 只接受 16/24/32 字节，`Cipher.init` 必然失败；异常被 catch 后原样返回明文，导致"加密"从未生效、库里一直是明文。现已改为失败即抛异常，绝不降级成明文落库。

### 水电批量录入

水电录入页（`/meter-readings`）提供三种录入方式：

| 方式 | 适用场景 |
|---|---|
| 表格逐间填写 | 少量房间、读数需要精确核对 |
| Excel 导入 | 已有抄表台账，需要精确读数 |
| **批量增量录入** | 大批房间统一递增，先出一版再个别修正 |

批量增量：勾选房间 → 填抄表日期 → 填水/电增量 → 系统按「上期读数 + 增量」生成。

```http
POST /api/meter-readings/generate-by-delta
{"roomIds":[1,2],"recordDate":"2026-11-25","waterDelta":8,"electricDelta":150,"overwrite":false}
```

- 基线取**目标日期之前**的最后一条读数，因此补录历史月份不会生成倒退的读数。
- 没有历史读数的房间会被跳过并在响应里给出原因（`skipped[].reason`），不会静默失败。
- 目标日期已有记录时，默认跳过；`overwrite: true` 才会覆盖。
- 只填一个增量时，另一个字段沿用上期读数。

### 演示数据

`backend/sql/seed_tenants.py` 生成 100 户完整业务数据（楼栋 → 房间 → 租户 → 合同 → 抄表 → 账单 → 收款 → 押金），用于演示和压测列表分页：

```bash
python3 backend/sql/seed_tenants.py            # 造数
python3 backend/sql/seed_tenants.py --dry-run  # 只看将执行什么
```

- 新建 3 栋楼（code `B`/`C`/`D`）共 100 间房，**不会动**原有数据。
- 租期 6~18 个月均匀分布；合同状态约 86% 进行中，其余为已到期/已解约。
- 账单从合同起始月生成到上月，状态按账龄分布（老账单基本已付，近 1~3 个月留未付/部分付）。
- 数据自洽校验内置在脚本末尾：已付账单金额与收款合计一致、账单不早于合同起始、房间不被重复占用、抄表单调递增。
- 重复执行会先按 `tenant.remark='SEED_2026'` 清理上一次的种子数据。

### 状态取值约定

状态字段是字符串，历史上存在中英文两种写法，现已统一：

- **房间**（`room.status`）：`空闲` / `已租` / `维修`。代码通过 `RoomStatus` 归一化，兼容 `IDLE`/`RENTED`/`MAINTENANCE`。
- **账单**（`bill.status`）：`PENDING` / `PARTIAL` / `PAID` / `VOID`。代码通过 `BillStatus` 归一化，兼容 `待支付`/`部分支付`/`已支付`/`作废`。

新增状态判断请使用 `RoomStatus` / `BillStatus`，不要在各处直接比较字面量。

API 示例：
- GET http://localhost:8081/api/buildings

## 管理端（frontend）

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build
```

## 员工移动端（staff-mobile）

```bash
cd staff-mobile
npm install
npm run dev                 # http://localhost:5180
npm run build               # 产物在 staff-mobile/dist

# 打包 Android APK
npm run android:sync
npm run android:open        # 或用 Android Studio 打开 staff-mobile/android
npm run android:apk:debug
```

移动端同样通过 `/api` 访问后端，默认代理到 `http://localhost:8081`（可用 `VITE_PROXY_TARGET` 覆盖）。真机调试时在登录页的「连接设置」里填写后端地址，如 `http://10.0.2.2:8081/api`。

## AI 助手

管理端顶栏的「AI 助手」（快捷键 `Ctrl/⌘ + J`，仅 `SUPER_ADMIN` / `ADMIN` 可见）支持自然语言指令：

- 录入水电：`今天A栋101水表1250.5电表3680`，支持一次多个房间
- 发送账单短信：`给A栋101的张三发账单短信`
- 查询合同：`查一下张三的合同`
- 查询用电量：`A栋101这个月用了多少电`

后端走 Spring AI `ChatClient` + `@Tool`，模型只负责解析意图，实际数据读写仍由 Java 工具方法完成（不会让模型直接操作数据库）。

**必须先配置模型密钥**，否则接口会返回 `NOT_CONFIGURED`，前端会显示配置指引。任选一个 provider 配置即可：

```bash
# 智谱 GLM（默认 primary）
export AI_PRIMARY_API_KEY=your-key

# 硅基流动
export AI_SILICONFLOW_API_KEY=your-key

# OpenAI 兼容
export AI_BACKUP_API_KEY=your-key
```

`app.ai-assistant.default-model` 决定首选 provider（`primary` / `siliconflow` / `backup`），失败后按顺序降级重试，次数由 `max-retries` 控制。超时时间在 `application.yml` 的 `timeout-millis` 里，默认 2.5s——工具调用场景建议不要低于 2s。

**密钥只从环境变量读取**，`application.yml` 中全部是 `${...:}` 空默认值，不要把真实密钥写进仓库。

## 测试

前端使用 Element Plus。路由按页面懒加载，未匹配的路径会显示 404 页面。

主要约定：

- 所有请求走 `src/api/index.js` 的 axios 实例，错误提示统一使用 `getErrorMessage(error, fallback)`。
- 权限控制：路由 `meta.roles` 控制页面访问，`src/api/index.js` 底部的 `can*()` 辅助函数控制按钮显隐。
- 主题色使用 `src/styles/theme.css` 中的 CSS 变量，避免硬编码颜色（否则暗色模式不可读）。
- 搜索输入使用 `src/utils/debounce.js` 做防抖。

## 测试

```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
```

覆盖范围（64 个用例）：

- `StatusNormalizeTest` — 房间/账单状态中英归一化与终态判定（39 例）
- `PaymentServiceTest` — 收款状态流转、重复收款拦截、批量收款（11 例）
- `MoveOutServiceTest` — 退租预检押金计算、欠费拦截、扣款与强制退租（14 例）

测试使用 Mockito 纯单测，不依赖数据库。

## 已知限制

- 前端无自动化测试（无 lint/typecheck 脚本），改动后需手工验证。
- 部分列表接口（楼栋、房间、账单、水电）返回全量数据，账单页采用前端分页；数据量大时需改为后端分页。
- 数据库迁移脚本按顺序手工执行，尚未接入 Flyway/Liquibase。

## 后续工作建议

- 为 `BillService`、`ContractService` 等核心逻辑补充单元测试。
- 引入 Flyway 收敛 SQL 迁移，避免新环境漂移。
- 为长列表接口补后端分页与索引。
- 为身份证等敏感字段完善密钥管理（轮换、KMS 托管）。
- 为在线支付回调增加签名校验（当前 `POST /api/payment/callback` 仅依赖 outTradeNo 不可猜）。
- 拆分超过 700 行的大页面（`Rooms.vue`、`MeterReadings.vue`、`Bills.vue` 等）为 composable。

## 性能压测与优化

已新增性能压测与索引优化落地文件：

- 方案文档：`docs/performance/PERFORMANCE_TEST_PLAN.md`
- 基准压测脚本：`backend/scripts/perf/perf_baseline.ps1`
- 索引迁移脚本：`backend/sql/migrate_add_performance_indexes.sql`

示例：

```bash
powershell -ExecutionPolicy Bypass -File backend/scripts/perf/perf_baseline.ps1
```
