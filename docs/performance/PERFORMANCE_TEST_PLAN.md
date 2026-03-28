# 性能压测与优化实施方案

## 1. 目标与验收基线

| 指标 | 目标值 |
|---|---|
| 核心接口 P95 | < 500ms |
| 复杂查询接口 P95 | < 1.5s |
| 管理端并发用户 | 200 |
| 租客端并发用户 | 1000 |
| 正常负载 CPU | < 70% |
| DB 连接池使用率 | < 80% |
| 5000 间房账单生成耗时 | < 10 分钟 |

## 2. 真实接口映射（当前代码）

本项目实际接口路径与需求草案存在差异，压测应以当前后端实现为准。

| 场景 | 建议压测接口 |
|---|---|
| 登录认证 | `POST /api/auth/login`、`POST /api/tenant/login` |
| 楼栋/房间查询 | `GET /api/buildings`、`GET /api/rooms` |
| 租户管理 | `GET /api/tenants`、`GET /api/tenants/{id}` |
| 合同管理 | `GET /api/contracts`、`POST /api/contracts/batch-renew` |
| 水电录入 | `GET /api/meter-readings`、`POST /api/meter-readings`、`POST /api/meter-readings/import/confirm` |
| 账单管理 | `GET /api/bills`、`POST /api/bills/generate`、`POST /api/bills/{id}/payments` |
| 维修工单 | `GET /api/repair-orders`、`PUT /api/repair-orders/{id}/assign` |
| 租客端核心 | `GET /api/bill/list`、`POST /api/payment/create`、`POST /api/repair/create` |
| 定时任务 | 账单生成、催缴、备份任务（后台执行） |

## 3. 压测执行分层

### 3.1 基准测试（单用户）
- 使用 `backend/scripts/perf/perf_baseline.ps1` 获取单接口延迟基线。
- 输出建议保留为发布前后对比数据。

### 3.2 负载测试（分并发阶梯）
- 管理端：10 -> 20 -> ... -> 200。
- 租客端：50 -> 100 -> ... -> 1000。
- 每个台阶至少稳定 3 到 5 分钟。

### 3.3 稳定性测试
- 目标并发 80% 连续运行 24h。
- 重点观察：Full GC、连接池耗尽、慢 SQL 突增。

### 3.4 峰值测试
- 月末场景混压：`账单生成 + 收款 + 列表查询 + 租客支付`。

## 4. 监控观测建议

### 4.1 必采指标
- 应用：QPS、RT(P50/P90/P95/P99)、错误率。
- JVM：堆使用、GC 次数与停顿、线程池排队长度。
- DB：连接池活跃数、慢查询数、锁等待、Buffer Pool 命中率。
- 主机：CPU、内存、磁盘 IO、网络吞吐。

### 4.2 推荐工具
- Prometheus + Grafana
- SkyWalking/Pinpoint
- MySQL 慢查询日志 + `EXPLAIN ANALYZE`

## 5. 已落地的首批优化动作

已新增幂等索引迁移脚本：
- `backend/sql/migrate_add_performance_indexes.sql`

覆盖表：
- `bill`、`contract`、`meter_reading`、`operation_log`、`room`、`payment_record`、`repair_order`、`complaint_suggestion`。

## 6. 执行命令

### 6.1 执行索引迁移

```powershell
Set-Location d:\idealcprofessional\projects\house2.0
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -proot -e "source d:/idealcprofessional/projects/house2.0/backend/sql/migrate_add_performance_indexes.sql"
```

### 6.2 运行基准压测脚本

```powershell
Set-Location d:\idealcprofessional\projects\house2.0
powershell -ExecutionPolicy Bypass -File .\backend\scripts\perf\perf_baseline.ps1
```

可选环境变量：
- `PERF_BASE`（默认 `http://localhost:8080/api`）
- `PERF_USER`（默认 `admin`）
- `PERF_PASS`（默认 `admin123`）

## 7. 持续治理机制

- 每次大版本上线前执行一次基准压测 + 一次 80% 负载稳定性测试。
- 每周巡检慢 SQL Top10。
- 每月回归一次峰值场景（账单+支付混压）。
- 将 P95/P99 与错误率纳入发布门禁。
