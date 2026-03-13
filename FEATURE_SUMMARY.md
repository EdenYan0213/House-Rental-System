# 房屋租赁管理系统 2.0 - 功能开发总结

## 项目概述
基于 Spring Boot + Vue 3 的房屋租赁管理系统，包含租户管理、合同管理、房源管理等功能。

---

## 一、租户黑名单功能（已完成）

### 1.1 数据模型
**文件**: `backend/src/main/java/com/house/model/Tenant.java`

新增字段：
| 字段名 | 类型 | 说明 |
|--------|------|------|
| is_blacklisted | Boolean | 是否在黑名单中 |
| blacklist_reason | String | 加入原因 |
| blacklisted_at | LocalDateTime | 加入时间 |
| blacklisted_by | Long | 操作人ID |

### 1.2 后端接口
**文件**: `backend/src/main/java/com/house/controller/TenantController.java`

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/tenants/blacklist | GET | 获取黑名单列表 |
| /api/tenants/{id}/blacklist-check | GET | 检查租户黑名单状态 |
| /api/tenants/{id}/blacklist | POST | 加入黑名单 |
| /api/tenants/{id}/blacklist | DELETE | 移出黑名单 |

**文件**: `backend/src/main/java/com/house/service/TenantService.java`

- `getBlacklistedTenants()` - 获取黑名单列表（支持搜索和筛选）
- `isBlacklisted()` - 检查是否在黑名单
- `getBlacklistInfo()` - 获取黑名单详情
- `addToBlacklist()` - 加入黑名单（含重复检查）
- `removeFromBlacklist()` - 移出黑名单

### 1.3 前端页面
**文件**: `frontend/src/pages/Blacklist.vue`
- 黑名单列表页面
- 支持按姓名/手机号搜索
- 支持按原因筛选
- 手动加入黑名单功能
- 移出黑名单功能

**文件**: `frontend/src/pages/Tenants.vue`
- 租户列表显示黑名单标签
- 详情页显示黑名单警告横幅
- 支持手动加入/移出黑名单

**文件**: `frontend/src/pages/Contracts.vue`
- 退租时可选择加入黑名单
- 勾选后显示原因选择（欠费/扰民/房屋损坏/其他）

**文件**: `frontend/src/pages/ContractCreate.vue`
- 选择租户时显示黑名单警告
- 点击下一步弹出二次确认框

### 1.4 功能特性
- ✅ 退租时加入黑名单（复选框+原因选择）
- ✅ 手动加入黑名单（租户列表/详情页）
- ✅ 黑名单列表管理（搜索/筛选/移出）
- ✅ 重复加入防护（已存在则提示）
- ✅ 租户列表显示黑名单状态
- ✅ 租户详情页显示警告横幅
- ✅ 合同创建时警告+二次确认

---

## 二、租户合同状态功能（已完成）

### 2.1 后端实现
**文件**: `backend/src/main/java/com/house/service/TenantService.java`

在 `search()` 方法中查询每个租户是否有有效合同：
```java
boolean hasActiveContract = contractRepository.findAll().stream()
    .anyMatch(c -> c.getTenantId() != null && c.getTenantId().equals(tenant.getId()) 
        && "ACTIVE".equals(c.getStatus()));
tenant.setActiveContract(hasActiveContract);
```

### 2.2 前端显示
**文件**: `frontend/src/pages/Tenants.vue`

租户列表表格显示"合同"列：
- 有合同：绿色标签"有合同"
- 无合同：灰色文字"无合同"

---

## 三、水电读数 Excel 批量导入（已完成）

### 3.1 后端接口与逻辑
**文件**: `backend/src/main/java/com/house/controller/MeterReadingController.java`

新增接口：
| 接口 | 方法 | 说明 |
|------|------|------|
| /api/meter-readings/import-template | GET | 下载标准 Excel 模板 |
| /api/meter-readings/import/parse | POST | 上传后解析并校验（预览前） |
| /api/meter-readings/import/confirm | POST | 确认导入并批量新增/覆盖 |

实现要点：
- ✅ 支持 `.xlsx/.xls`，文件大小限制 `<=10MB`
- ✅ 按 Sheet 名匹配楼栋（忽略前后空格）
- ✅ 标题列校验：`日期/房间号/水表读数/电表读数`（顺序不限）
- ✅ 日期格式支持 `yyyy-mm-dd`、`yyyy/mm/dd`、`yyyy.mm.dd` 及 Excel 日期单元格
- ✅ 校验房间号是否存在于对应楼栋
- ✅ 读数必须为非负数字
- ✅ 同 Sheet 内同一 `日期+房间号` 重复检测
- ✅ 返回 `成功数据 + 错误数据 + 统计摘要`
- ✅ 导入确认使用事务，保证全量成功或全量失败
- ✅ 导入权限限制为 `ADMIN/SUPER_ADMIN`

### 3.2 前端导入向导
**文件**: `frontend/src/pages/MeterReadings.vue`

在水电录入页面新增“Excel导入”按钮与四步导入向导：
- 步骤1：上传文件（拖拽/点击）+ 下载模板
- 步骤2：解析校验结果展示（总数/成功/错误）
- 步骤3：预览确认（楼栋数、日期覆盖数、样例数据）
- 步骤4：导入结果反馈（成功/失败统计）

同时支持：
- ✅ 错误列表滚动展示
- ✅ 错误报告 CSV 下载（Sheet名、行号、日期、房间号、错误原因）
- ✅ 确认导入后刷新当前水电数据列表

### 3.3 前端 API 封装
**文件**: `frontend/src/api/index.js`

新增方法：
- `downloadMeterReadingImportTemplate()`
- `parseMeterReadingImportFile(file)`
- `confirmMeterReadingImport(records)`

### 3.4 依赖变更
**文件**: `backend/pom.xml`

- 新增 `org.apache.poi:poi-ooxml:5.2.5` 用于 Excel 模板生成与解析

---

## 四、待完善功能

### 4.1 权限控制
当前 SecurityConfig 配置为 `permitAll()`，黑名单相关接口的 `@PreAuthorize` 注解已添加但被注释：
```java
// @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
```

需要在配置好 JWT 认证后启用。

### 4.2 性能优化
- 黑名单检测目前使用 `contractRepository.findAll()`，数据量大时可优化为直接查询

---

## 五、技术栈

### 后端
- Spring Boot 3.4.2
- Java 17+
- MyBatis + MySQL
- Spring Security

### 前端
- Vue 3 + Vite
- Element Plus
- Axios

---

## 六、数据库相关

### 黑名单相关表结构变更
如需手动执行 SQL：
```sql
ALTER TABLE tenant 
ADD COLUMN is_blacklisted BOOLEAN DEFAULT FALSE,
ADD COLUMN blacklist_reason VARCHAR(255),
ADD COLUMN blacklisted_at DATETIME,
ADD COLUMN blacklisted_by BIGINT;

CREATE INDEX idx_tenant_blacklisted ON tenant(is_blacklisted);
```

---

## 七、API 文档

### 租户管理
- `GET /api/tenants` - 获取租户列表（含合同状态）
- `POST /api/tenants` - 创建租户
- `PUT /api/tenants/{id}` - 更新租户
- `DELETE /api/tenants/{id}` - 删除租户

### 黑名单管理
- `GET /api/tenants/blacklist` - 获取黑名单列表
- `GET /api/tenants/{id}/blacklist-check` - 检查黑名单状态
- `POST /api/tenants/{id}/blacklist` - 加入黑名单
- `DELETE /api/tenants/{id}/blacklist` - 移出黑名单

### 合同管理
- `GET /api/contracts` - 获取合同列表
- `POST /api/contracts` - 创建合同
- `POST /api/contracts/{id}/terminate` - 解约合同
- `POST /api/contracts/{id}/renew` - 续签合同

---

*最后更新: 2026-03-13*

## 八、已实现功能总览（补充）

下面按功能模块列出当前仓库中已实现并在前后端均可用的主要功能点（含关键文件参考）。

- **用户认证与权限**：登录/登出、路由守卫、基于角色的页面访问控制（前端路由守卫参见 [frontend/src/router/index.js](frontend/src/router/index.js)，后端登录参见 [backend/src/main/java/com/house/controller/AuthController.java](backend/src/main/java/com/house/controller/AuthController.java)）。
- **仪表盘**：统计卡片、图表（echarts），合同到期提醒（[frontend/src/pages/Dashboard.vue](frontend/src/pages/Dashboard.vue)、[backend/src/main/java/com/house/controller/DashboardController.java](backend/src/main/java/com/house/controller/DashboardController.java)）。
- **楼栋/房间管理**：楼栋列表、房间列表、房间详情与房间资产（[frontend/src/pages/Buildings.vue](frontend/src/pages/Buildings.vue)、[frontend/src/pages/Rooms.vue](frontend/src/pages/Rooms.vue)、[frontend/src/pages/RoomAssets.vue](frontend/src/pages/RoomAssets.vue)、[backend/src/main/java/com/house/controller/BuildingController.java](backend/src/main/java/com/house/controller/BuildingController.java)、[backend/src/main/java/com/house/controller/RoomController.java](backend/src/main/java/com/house/controller/RoomController.java)）。
- **租户管理**：增删改查、黑名单、导出、证件脱敏显示（[frontend/src/pages/Tenants.vue](frontend/src/pages/Tenants.vue)、[backend/src/main/java/com/house/controller/TenantController.java](backend/src/main/java/com/house/controller/TenantController.java)）。
- **合同管理**：创建/续租/解约/批量操作/导出（[frontend/src/pages/Contracts.vue](frontend/src/pages/Contracts.vue)、[frontend/src/pages/ContractCreate.vue](frontend/src/pages/ContractCreate.vue)、[backend/src/main/java/com/house/controller/ContractController.java](backend/src/main/java/com/house/controller/ContractController.java)）。
- **账单管理与收款**：生成账单、收款、重新生成、作废、账单详情（[frontend/src/pages/Bills.vue](frontend/src/pages/Bills.vue)、[frontend/src/pages/BillDetail.vue](frontend/src/pages/BillDetail.vue)、[backend/src/main/java/com/house/controller/BillController.java](backend/src/main/java/com/house/controller/BillController.java)）。
- **水电读数**：手动录入 + Excel 批量导入向导（解析/校验/确认导入），导出读数（[frontend/src/pages/MeterReadings.vue](frontend/src/pages/MeterReadings.vue)、[backend/src/main/java/com/house/controller/MeterReadingController.java](backend/src/main/java/com/house/controller/MeterReadingController.java)）。
- **费用项与押金管理**：月租、押金、费用项维护（[frontend/src/pages/FeeItems.vue](frontend/src/pages/FeeItems.vue)、[frontend/src/pages/Deposits.vue](frontend/src/pages/Deposits.vue)、[backend/src/main/java/com/house/controller/FeeItemController.java](backend/src/main/java/com/house/controller/FeeItemController.java)、[backend/src/main/java/com/house/controller/DepositController.java](backend/src/main/java/com/house/controller/DepositController.java)）。
- **维修工单与维修人员管理**：工单创建/指派/状态流转、维修工管理（[frontend/src/pages/RepairOrders.vue](frontend/src/pages/RepairOrders.vue)、[frontend/src/pages/RepairWorkers.vue](frontend/src/pages/RepairWorkers.vue)、[backend/src/main/java/com/house/controller/RepairOrderController.java](backend/src/main/java/com/house/controller/RepairOrderController.java)、[backend/src/main/java/com/house/controller/RepairWorkerController.java](backend/src/main/java/com/house/controller/RepairWorkerController.java)）。
- **文件上传与导出**：图片/附件上传、Excel 导出与房间器具核对单 PDF 导出（[frontend/src/components/FileUpload.vue](frontend/src/components/FileUpload.vue)、[backend/src/main/java/com/house/controller/FileController.java](backend/src/main/java/com/house/controller/FileController.java)、[backend/src/main/java/com/house/controller/ExportController.java](backend/src/main/java/com/house/controller/ExportController.java)、PDF 生成实现参见 ExportController）。
- **消息中心与操作日志**：系统消息、操作日志查看与导出（[frontend/src/pages/Messages.vue](frontend/src/pages/Messages.vue)、[frontend/src/pages/Logs.vue](frontend/src/pages/Logs.vue)、[backend/src/main/java/com/house/controller/MessageController.java](backend/src/main/java/com/house/controller/MessageController.java)、[backend/src/main/java/com/house/controller/LogController.java](backend/src/main/java/com/house/controller/LogController.java)）。
- **用户与权限管理**：用户列表、角色管理、路由权限保护（[frontend/src/pages/Users.vue](frontend/src/pages/Users.vue)、[backend/src/main/java/com/house/controller/UserController.java](backend/src/main/java/com/house/controller/UserController.java)）。
- **移动端轻量页面**：二维码跳转到移动端页面，移动端水电/报修/账单查看（[frontend/src/pages/mobile/*](frontend/src/pages/mobile/)）。
- **回退/撤销支持**：部分危险操作（如删除合同/删除租户/解约/作废）会创建操作快照并支持短期撤销（参见 `OperationSnapshot`、[backend/src/main/java/com/house/controller/OperationSnapshotController.java](backend/src/main/java/com/house/controller/OperationSnapshotController.java) 与各 controller 中的 snapshot 使用示例）。

以上为当前仓库中已实现且已在前端页面中可访问的主要功能模块与对应文件参考。如果希望我把这些条目按照产品文档格式（功能简介、用户流程、权限、接口示例）重新排版或补充 API 样例，请告诉我优先级。
