# 房屋租赁管理系统

包含后端（Spring Boot 3.4.2，Java 17）和前端（Vue 3 + Vite）。

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

老版本升级按顺序执行增量脚本：

```bash
mysql -uroot -proot < backend/sql/migrate_add_building_unit_prices.sql
mysql -uroot -proot < backend/sql/migrate_add_building_image_url.sql
mysql -uroot -proot < backend/sql/migrate_add_repair_and_appliance.sql
mysql -uroot -proot < backend/sql/migrate_add_operation_snapshot.sql
mysql -uroot -proot < backend/sql/migrate_add_theme_preference.sql
mysql -uroot -proot < backend/sql/migrate_normalize_status_values.sql
```

说明：

- 楼栋卡片图片依赖 `building.image_url` 字段，未执行该迁移会导致楼栋查询报错。
- `migrate_normalize_status_values.sql` 把历史数据中房间/账单状态的中英文混写统一到规范值，并补充查询索引。代码已兼容两种写法，但执行后筛选与索引效率更好。

### 状态取值约定

状态字段是字符串，历史上存在中英文两种写法，现已统一：

- **房间**（`room.status`）：`空闲` / `已租` / `维修`。代码通过 `RoomStatus` 归一化，兼容 `IDLE`/`RENTED`/`MAINTENANCE`。
- **账单**（`bill.status`）：`PENDING` / `PARTIAL` / `PAID` / `VOID`。代码通过 `BillStatus` 归一化，兼容 `待支付`/`部分支付`/`已支付`/`作废`。

新增状态判断请使用 `RoomStatus` / `BillStatus`，不要在各处直接比较字面量。

API 示例：
- GET http://localhost:8081/api/buildings

## 前端（frontend）

运行：

```bash
cd frontend
npm install
npm run dev
```

构建：

```bash
npm run build
```

前端使用 Element Plus。路由按页面懒加载，未匹配的路径会显示 404 页面。

主要约定：

- 所有请求走 `src/api/index.js` 的 axios 实例，错误提示统一使用 `getErrorMessage(error, fallback)`。
- 权限控制：路由 `meta.roles` 控制页面访问，`src/api/index.js` 底部的 `can*()` 辅助函数控制按钮显隐。
- 主题色使用 `src/styles/theme.css` 中的 CSS 变量，避免硬编码颜色（否则暗色模式不可读）。
- 搜索输入使用 `src/utils/debounce.js` 做防抖。

## 已知限制

- 无自动化测试（无 `src/test`、无 lint/typecheck 脚本），改动后需手工验证。
- 部分列表接口（楼栋、房间、账单、水电）返回全量数据，账单页采用前端分页；数据量大时需改为后端分页。
- 数据库迁移脚本按顺序手工执行，尚未接入 Flyway/Liquibase。

## 后续工作建议

- 为 `BillService`、`ContractService` 等核心逻辑补充单元测试。
- 引入 Flyway 收敛 SQL 迁移，避免新环境漂移。
- 为长列表接口补后端分页与索引。
- 为身份证等敏感字段完善密钥管理（轮换、KMS 托管）。
- 清理仓库内的开发残留文件（`frontend/login.json`、`frontend/send_login.js` 含默认口令）。

## 性能压测与优化

已新增性能压测与索引优化落地文件：

- 方案文档：`docs/performance/PERFORMANCE_TEST_PLAN.md`
- 基准压测脚本：`backend/scripts/perf/perf_baseline.ps1`
- 索引迁移脚本：`backend/sql/migrate_add_performance_indexes.sql`

示例：

```bash
powershell -ExecutionPolicy Bypass -File backend/scripts/perf/perf_baseline.ps1
```
