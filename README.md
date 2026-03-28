# 房屋租赁管理系统

包含后端（Spring Boot 3.4.2，Java17）和前端（Vue3 + Vite）。

## 后端（backend）

运行：

```bash
cd backend
mvn spring-boot:run
```

后端默认使用 `application.yml` 中配置的 MySQL 数据库。

首次初始化数据库可执行：

```bash
mysql -uroot -proot < backend/sql/init_db.sql
```

如果是老版本升级，请执行增量脚本：

```bash
mysql -uroot -proot < backend/sql/migrate_add_building_unit_prices.sql
mysql -uroot -proot < backend/sql/migrate_add_building_image_url.sql
```

说明：楼栋卡片图片依赖 `building.image_url` 字段，未执行该迁移会导致楼栋查询报错。

API示例：
- GET http://localhost:8080/api/buildings

## 前端（frontend）

运行：

```bash
cd frontend
npm install
npm run dev
```

前端使用 Element Plus 做基础 UI，登录页面和仪表盘为示例页面。

## 后续工作建议
- 按 PRD 补全实体、业务逻辑、定时任务（账单生成）、文件上传、收款逻辑。
- 为身份证等敏感字段完善更安全的加密/密钥管理。
- 添加安全认证（JWT）和权限控制。
- 添加单元测试与接口测试。

## 性能压测与优化

已新增性能压测与索引优化落地文件：

- 方案文档：`docs/performance/PERFORMANCE_TEST_PLAN.md`
- 基准压测脚本：`backend/scripts/perf/perf_baseline.ps1`
- 索引迁移脚本：`backend/sql/migrate_add_performance_indexes.sql`

示例：

```bash
powershell -ExecutionPolicy Bypass -File backend/scripts/perf/perf_baseline.ps1
```
