# AGENTS.md

Agent guide for this repo. Read `README.md` and `FEATURE_SUMMARY.md` before changing business flows — they document the feature set and the manual SQL migrations.

## What this is

房屋租赁管理系统 (house rental management): 楼栋/房间、租户、合同、账单与收款、押金、水电抄表、维修工单、用户/权限、操作日志与撤销快照，外加减量移动端页面（扫码进入）。

**Four** apps, no monorepo tooling — treat them as separate projects:

- `backend/` — Spring Boot 3.4.2, Java 17, Maven. Package root `com.house`. The only API provider.
- `frontend/` — 管理端 (desktop admin). Vue 3 + Vite 4 + Element Plus, plain JavaScript. Root alias `@` → `frontend/src`. Port 5173.
- `staff-mobile/` — 员工移动端 (staff mobile). Separate Vue 3 + Vite project with its own dependencies, `package.json`, `vite.config.js`, router and Capacitor Android project (`com.house.staff`). Port 5180.
- `tenant-frontend/` — 租客自助端 (tenant self-service portal). Vue 3 + Vite 4 + vue-router, **no Element Plus** (hand-rolled components, plain CSS in `src/styles.css`). Port 5188. Audience is tenants, not staff.
- `backend/sql/` — schema, hand-maintained.
- `backend/test-data/meter-import/` — fixtures for the meter-reading Excel import (see its `README.md`).

**Do not re-introduce cross-imports.** Each frontend is standalone: `frontend/` must not import from `staff-mobile/` or `tenant-frontend/`, and so on. They share no code, only the HTTP API. `frontend/` has no Capacitor dependency. `tenant-frontend/` is **not** included in any root build script — build it separately with `cd tenant-frontend && npm run build`.

## Commands

```bash
# backend (from backend/)
mvn spring-boot:run          # dev server on :8081 (SERVER_PORT)
mvn -DskipTests package      # build jar
mysql -uroot -proot < sql/init_db.sql   # first-time schema

# frontend (from frontend/)
npm install
npm run dev                  # :5173, proxies /api -> :8081
npm run build                # production bundle

# staff-mobile (from staff-mobile/)
npm install
npm run dev                  # :5180, proxies /api -> :8081
npm run build                # -> staff-mobile/dist
npm run android:sync         # sync web assets into the Capacitor project
npm run android:apk:debug    # build a debug APK

# tenant-frontend (from tenant-frontend/)
npm install
npm run dev                  # :5188, proxies /api -> :8081
npm run build                # -> tenant-frontend/dist
```

**Requires JDK 17.** Lombok 1.18.36 crashes on JDK 21+ with `com.sun.tools.javac.code.TypeTag :: UNKNOWN`. On a machine with multiple JDKs use `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn ...`.

**Backend has JUnit tests** (`backend/src/test/`, run with `mvn test` on JDK 17) covering status normalization, payment state transitions, move-out settlement, meter generation, user profile updates and the AI entity resolver — **118 cases**, all Mockito-based unit tests with no DB. Note the gap: only 4 of 28 services have tests, and the largest untested ones are `ComplaintService`, `BackupService`, `ContractService`, `BillService` — add tests there before changing their status/settlement semantics. **Frontend has no tests, no linter, and no typechecker**; verify those changes by building and exercising by hand. `npm run build` and `mvn test` are the automated gates.

## Database: schema is manual, `ddl-auto: none`

`application.yml` sets `spring.jpa.hibernate.ddl-auto: none`. Adding a field to a `@Entity` does **not** create a column. Every schema change needs a new `backend/sql/migrate_*.sql` plus a note in `README.md`. A missing migration surfaces as a runtime query error (e.g. `building.image_url`), not a startup failure.

Existing migration scripts are ordered and must be applied by hand on old databases. New columns default to readable Chinese status strings (`"空闲"`, `"已租"`) or English (`PENDING`, `ACTIVE`), and the code accepts both forms — check the existing comparisons before introducing a third spelling.

## Persistence: JPA is primary, MyBatis-Plus is an exception

- Most data access goes through `com.house.repo.*Repository` extending `JpaRepository` (**42 repos**), with derived query methods (`findByRoomIdAndStatus`, `findByStatusIn`, `findByNameContainingOrPhoneContaining`).
- **`UserRepository` is the one MyBatis-Plus `BaseMapper`**, because `UserController`/`UserService` need server-side paging (`selectPage`). There is no `PaginationInnerInterceptor` bean configured — if you add MyBatis paging elsewhere, register the interceptor or page queries will silently return unpaged results.
- The project has **both** `spring-boot-starter-data-jpa` and `mybatis-plus-boot-starter` on the classpath. Don't introduce new MyBatis mappers, mappers XML (none exist), or a second persistence style without a clear reason.
- **No queries inside loops.** Batch endpoints (`ContractService.batchTerminate`) used to call `billRepository.findByContractId` and `roomRepository.findById` per row — a textbook N+1 that grows with the selection. Pre-fetch with `findByContractIdIn` / `findAllById` into a `Map` and look up in memory. If you pre-fetch, note that absent keys mean "no rows"; `getOrDefault(id, false)` is not the same as `anyMatch`.

## Backend layering and conventions

`com.house.{model, repo, dto, service, controller, config, util}`.

- **`model`** — JPA entities, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, plus explicit getters/setters on some entities (`Tenant`, `Room`, `Contract`, `Bill`). Follow the file you are editing; don't strip hand-written accessors.
- **`controller`** — thin-ish; but many controllers inject repositories directly and assemble responses (e.g. `DashboardController`, `ExportController`, `MobileRoomController`). Match the local file rather than refactoring toward strict layering. **New business logic belongs in `service/` anyway**: the complaint bug where `TenantComplaintController` re-implemented `ComplaintService.create` (different complaint-number format, no dedup guard) is the cautionary example, fixed by adding `ComplaintService.createByTenant`.
- **The tenant portal is split by concern**, all sharing prefix `/api/tenant`: `TenantAuthController` (register/login/current/switch-room/update-phone), `TenantHomeController` (home/vacant-rooms/rent-intent), `TenantMessageController` (preferences/messages/dashboard, plus `POST /messages/read-all`), `TenantSignController` (e-sign), plus `TenantComplaintController` / `TenantContractController` / `TenantRepairController` for phase-2 flows. Shared helpers live in `TenantPortalSupport` (masking, room summary, request parsing) and `TenantMessageText` (bilingual message templates). Add new tenant endpoints to the matching controller, not a new one.
- **`TenantPhase1Controller` is the legacy tenant surface** (`/api/bill/*`, `/api/payment/*`, `/api/notice/*` mounted at `/api`, **not** under `/tenant`). It is what `tenant-frontend` actually calls, so it is live code, not dead — but keep changes minimal and compatible. Its `GET /bill/list` returns a `summary` block computed over **all** filtered-out rows (`totalAmount`/`paidAmount`/`unpaidAmount`/`unpaidCount`/`billCount`), so the frontend must not recompute totals from the current page. `GET /tenant/payment/order/{outTradeNo}` (same controller, under the `/tenant` path) backs the post-callback status confirmation and enforces tenant ownership via bill → contract.
- **Deposit data is split by audience.** `/api/deposits` is the **admin** global view (`SUPER_ADMIN`/`ADMIN` only). Tenants read their own via `GET /api/contract/deposits` (in `TenantContractController`), which resolves the tenant's contracts first and sums `RECEIVE`/`REFUND` into a `{received, refunded, balance}` summary. Never let the tenant surface call `/api/deposits` — it leaks other tenants' names, contract numbers and amounts.
- **`service`** — business rules, `@Transactional` on writes. `BillService`, `ContractService`, `RepairOrderService`, `OperationSnapshotService` hold the real logic. Any operation that writes more than one table (e.g. payment callback updating `payment_order` + `payment_record` + `bill`) belongs in a single `@Transactional` service method, not in the controller — see `PaymentService.settlePaymentCallback`.
- **`GlobalExceptionHandler` catches what controllers don't.** `com.house.config.GlobalExceptionHandler` maps `IllegalArgumentException`/`IllegalStateException` → 400, `AccessDeniedException` → 403, and specific Spring exceptions → 405 / 413 / 415 / 404 / 400. Anything else is logged and becomes 500. Controllers still catch business errors themselves and return `ResponseEntity.badRequest().body(<string>)`, so error payloads remain **inconsistent**: sometimes a bare string, sometimes `{"message": ...}`. `MeterReadingController` additionally has a local `@ExceptionHandler`.
- **Never hand-write `e.response?.data` in a page.** Use `getErrorMessage(error, fallback)` from `frontend/src/api/index.js` — hand-rolled variants render `[object Object]` when the body is a JSON object and drop the timeout/offline handling. Converted repo-wide (the last batch covered `Settings.vue`, `Buildings.vue`, `Backup.vue`, `Rooms.vue`, `Contracts.vue`, `utils/undo.js` and three `staff-mobile` files); grep before adding: `grep -rn 'response?.data' frontend/src staff-mobile/src`. The one legitimate exception is reading **structured** error fields for a custom dialog (`Bills.vue` batch-SMS shows `failReason`/`latestMessageTitle`) — keep its `|| '未知错误'` fallback.
- **Auth failures are 401, permission failures are 403.** `SecurityConfig` registers an `authenticationEntryPoint` (401) and `accessDeniedHandler` (403); the axios interceptor depends on that split (401 → redirect `/login`, 403 → toast only). Don't remove them — without them Spring Security defaults to 403 for everything and the login redirect never fires.
- **Business messages are Chinese.** Throw `IllegalArgumentException`/`IllegalStateException` with a Chinese message; it reaches the UI verbatim.
- **`Map.of(...)` throws NPE on any null value.** It is the single most common cause of unexplained 500s here — it has already broken `TenantPortalController.home`, `MobileRoomController.roomSummary` and `TenantService.update`. When building a response or metadata map with values that can be null (entity fields from legacy rows, optional dates, `? null : ...` branches), use `new LinkedHashMap<>()` + `put(...)` instead. `Map.of` is only safe for literals and non-null primitives. **`e.getMessage()` is the sneakiest case**: `Map.of("message", e.getMessage())` returns `null` for exceptions without a message → NPE inside the error path. Wrap it: `e.getMessage() == null ? "操作失败" : e.getMessage()`.
- **Undo responses go through `UndoResponse`.** Do not hand-write `"undoSnapshotId" / "undoExpireAt" / "undoTip"`; call `UndoResponse.of(snapshot)` or `UndoResponse.withData("data", entity, snapshot, tip)`. The helper allows a null `expiredAt` so a future change to `capture()` cannot turn every delete endpoint into a 500.
- **`update` methods are partial updates, not full replacements.** Edit dialogs submit only changed fields. Merge field-by-field from the existing row (`if (hasText(incoming.getX())) existing.setX(...)`) and never blindly assign, or a missing field becomes `null` (see `RoomService.update`, `TenantService.update`, `FeeItemController.update`). Two real incidents: `FeeItemController` did `save(incoming)`, so toggling 启用/停用 from the list (which sends only `{enabled}`) **wiped name/unitPrice/unit/category**; and any PUT that persists a request-body entity must first confirm the row exists (`findById(id).map(...)`), otherwise `save()` with a supplied id becomes an UPDATE of a missing row.
- **Never hardcode a primary key on a `@GeneratedValue(IDENTITY)` entity.** `new ReminderSettings(); setId(1L); save()` is treated by Hibernate as an UPDATE of row 1 — if that row doesn't exist you get `ObjectOptimisticLockingFailureException` (a 500) on a clean database. This hit `ReminderSettingsController` and `BackupService.saveConfig`. Instead look the row up and reuse its id, or leave the id null so the DB assigns it.
- **Request-body parsing goes through `com.house.util.RequestValues`** (`parseLong`, `parseInt`, `text`, `textOrDefault`). It is lenient by design — unparseable values become null/default instead of throwing. Several older copies still exist (`ComplaintService`, `TenantPortalSupport`, `TenantPhase2Support`); use `RequestValues` in new code rather than adding a fifth variant.
- **Logging** is inconsistent: `@Slf4j`-free files use `System.out`/`e.printStackTrace()` (see `BillService.generateMonthlyBills`), a few use SLF4J (`FileStorageService`). Prefer SLF4J for new code, but don't mass-rewrite existing handlers.
- **Scheduled jobs** (`@EnableScheduling` on `HouseApplication`): monthly bill generation (`BillService` cron `0 0 1 1 * ?`), contract-expiry reminders (`ReminderService` 0:05), **contract auto-expiry** (`ContractExpiryService` 0:15, config `app.contract.*`), snapshot cleanup (`OperationSnapshotService`), orphan image cleanup (`ImageStorageService`). Check these before changing bill or contract status semantics.
- **Contract auto-expiry has two safety rules**: it only releases a room to `IDLE` when the contract has **no unpaid bills** and the room has **no other active contract**. Otherwise it just flips the contract to `EXPIRED` — a room with arrears stays `RENTED` on purpose (it is the trace for collection). Manual trigger: `POST /api/contracts/expire-now` (button 处理到期 on the contracts page).
- **Meter readings reject regressions.** `MeterReadingBatchService.upsertBatch` compares against the room's previous reading by date; a smaller water/electric value throws with the room, date and previous reading in the message. This guards against typing errors generating absurd bills. All three write paths (manual entry, Excel import, delta generation) go through it.

## Room and bill status values

Status is a `String`, and comparisons were historically duplicated across files. **Always go through the helper classes** — they normalize both English and Chinese spellings and are the single source of truth. There are **four**, one per status family:

- **`com.house.model.RoomStatus`** — `IDLE` / `RENTED` / `MAINTENANCE`. `normalize()` maps `空闲`/`已租`/`维修` and legacy aliases (`空置`, `在租`, `维修中`). Helpers: `isIdle`, `isRented`, `isMaintenance`, `matches(raw, filter)`.
- **`com.house.model.BillStatus`** — `PENDING` / `PARTIAL` / `PAID` / `VOID`. `normalize()` maps `待支付`/`部分支付`/`已支付`/`作废`/`OBSOLETE`/`已作废`. Helpers: `isPending`, `isPartial`, `isPaid`, `isVoid`, `isFinal` (paid or void), `isOutstanding` (not final).
- **`com.house.model.ContractStatus`** — `ACTIVE` / `PENDING` / `TERMINATED` / `EXPIRED`. `normalize()` maps `进行中`/`待生效`/`已终止`/`已到期`. Helpers: `isActive`, `isPending`, `isTerminated`, `isExpired`, `isFinal`. **`ACTIVE_RAW_VALUES`** is the list to pass to JPA derived queries when you need "生效中的合同" — legacy rows contain `进行中`, so `findByStatus("ACTIVE")` silently misses them. Use `findByStatusIn(ContractStatus.ACTIVE_RAW_VALUES)` / `findByRoomIdAndStatusIn(roomId, ContractStatus.ACTIVE_RAW_VALUES)` instead of the two-step "query ACTIVE then query 进行中" pattern.
- **`com.house.model.WorkflowStatus`** — 维修工单/投诉共用生命周期，**小写带下划线**（前端分组依赖它，别改）：`pending` / `processing` / `pending_accept` / `pending_confirm` / `completed` / `closed` / `cancelled` / `assigned`. Helpers: `isCompleted`, `isFinal`, and `IN_PROGRESS` list.

Reading paths (`RoomService.listByBuildingAndFloor`, `BillController.list`, `BillController.get`) normalize on output so the frontend never receives mixed spellings. `backend/sql/migrate_normalize_status_values.sql` cleans historical rows; it is idempotent.

Do **not** add new `"空闲".equals(...)` / `"PAID".equals(...)` / `"ACTIVE".equals(...)` / `"pending".equals(...)` literals — extend the helper classes instead. If you need a display name (e.g. 中文 for the AI assistant), switch on `normalize()` and keep the mapping in one place, as `AiEntityResolver.displayContractStatus` does.

One legitimate exception: `TenantPhase1Controller.normalizeStatus(status, total, paid)` **recomputes** bill status from paid/total (it isn't pure spelling normalization). It delegates the void check to `BillStatus.isVoid` and returns `BillStatus.*` constants; keep it that way rather than replacing it with `BillStatus.normalize`.

## Security and auth

- JWT bearer, stateless. `JwtAuthenticationFilter` puts `ROLE_<role>` into the security context; roles are `SUPER_ADMIN`, `ADMIN`, `USER`.
- `SecurityConfig` uses `@EnableMethodSecurity` with **128 `@PreAuthorize` annotations** across controllers (239 mapped endpoints total — the rest rely on the URL rules below; audit before adding an unauthenticated one).
- **Public surface is deliberately narrow.** Only these are reachable without a token: `/api/auth/**`, tenant register/login (`/api/tenant/login*`, `/api/tenant/register*`), `POST /api/payment/callback`, `OPTIONS /**`, `GET /api/files/**`, `GET /api/images/**`, `GET /api/notice/**`, and `POST /internal/sign/callback` (e-sign gateway). **Never widen this with a wildcard** — an earlier `/internal/**` `permitAll` exposed an admin-password-reset endpoint to the internet.
- **`anyRequest().authenticated()` is NOT an authorization rule — it admits `ROLE_TENANT`.** Every admin/staff endpoint must carry `@PreAuthorize`. This was violated by `DepositController`, `BuildingController`, `DashboardController` and `FeeItemController`: a tenant JWT could read all 120 deposit records (other tenants' names, contract numbers, amounts) and `POST /api/deposits` would go through. Before adding a controller, decide its roles explicitly — `hasAnyRole('SUPER_ADMIN','ADMIN','USER')` for staff-shared read endpoints, `hasAnyRole('SUPER_ADMIN','ADMIN')` for configuration/money, `hasRole('TENANT')` for the tenant portal. Grep for unguarded controllers with:
  ```bash
  for f in backend/src/main/java/com/house/controller/*.java; do
    n=$(grep -c '@PreAuthorize' "$f"); m=$(grep -cE '@(Get|Post|Put|Delete|Patch)Mapping' "$f")
    [ "$m" -gt 0 ] && [ "$n" -eq 0 ] && echo "UNGUARDED: $f"
  done
  ```
  (`AuthController`, `FileController`, `ImageController`, `SignCallbackController` are the known-intentional leftovers — public auth surface or token-less image loads. `BackupController` does its own `hasAnyRole` check inside each method.)
- **Admin password rules** (enforced by endpoint split, not by a hidden backdoor): `PUT /api/users/{id}/reset-password` is `SUPER_ADMIN`-only, so only an admin can reset another admin's password; `PUT /api/users/change-password` is for the logged-in user changing their own and requires the old password. Do not add a token/query-param escape hatch — the old `InternalController.resetAdminPassword` (hardcoded `DEV_RESET_TOKEN_2026`) was removed for exactly that reason.
- **Never log secrets.** `reset-password` used to write the plaintext `newPassword` into the operation-log metadata; it now only stores `userId`. Never put passwords, `Tenant.idCard`, or tokens into `operationLogService.log(...)` metadata or `Map.of` snapshots.
- Blacklist management (`GET /api/tenants/blacklist`, `POST|DELETE /api/tenants/{id}/blacklist`) is restricted to `SUPER_ADMIN`/`ADMIN`. `GET /api/tenants/{id}/blacklist-check` intentionally stays open to any authenticated user because the contract-creation wizard warns `USER` accounts about blacklisted tenants.
- CORS is configured **twice** — `SecurityConfig.corsConfigurationSource()` (localhost:5173 only) and `WebMvcConfig.addCorsMappings()` (`allowedOrigins("*")`, which is invalid with credentials). Changing one without the other produces confusing preflight failures.
- Secrets come from env with insecure defaults in `application.yml` (`JWT_SECRET`, `CRYPTO_SECRET`). Never hardcode new ones.
- `Tenant.idCard` is encrypted via `@Convert(converter = CryptoConverter.class)` (AES/GCM). Keep that annotation on any new sensitive column and don't log decrypted values.
- **`POST /api/payment/callback` currently has no signature verification** — it trusts `outTradeNo` being unguessable (`"PO" + timestamp + UUID`). This is a known, accepted gap for the mock payment flow; **add HMAC/gateway verification before any real payment integration**, and never widen the trusted params without it.
- Dev artifacts with credentials are **no longer tracked** (`frontend/login.json`, `frontend/send_login.js`, and a database dump under `backups/` were removed and added to `.gitignore`). Don't re-add local scripts containing the default `admin/admin123`, and don't commit database dumps — they contain real PII and password hashes.

## Operation snapshots (undo)

Destructive operations (delete/edit tenant, terminate contract, void bill, …) call `operationSnapshotService.capture(...)` **before** mutating, then `complete(...)` after, and return `undoSnapshotId` / `undoExpireAt` in the response body. The frontend turns that into an undo toast via `frontend/src/utils/undo.js` (`showUndoToast`).

When you add a destructive endpoint, follow this pattern; when you add an entity type, check `OperationSnapshotService` recovery switch — it dispatches per table name and silently ignores unknown tables. Undo window and retention are in `app.snapshot.*`.

## AI assistant

`backend/src/main/java/com/house/ai/` — Spring AI `ChatClient` with `@Tool` methods. The model only parses intent; **all reads/writes go through Java tool methods**, never direct model-driven SQL.

The package is split by concern — put new code in the matching file:

- `AiAssistantService` (220 lines) — orchestration only: provider fallback loop, `@Tool` declarations, `InvocationState` ThreadLocal.
- `AiModelGateway` — provider ordering/validation, `ChatClient` construction, base-url normalization (`/v1` vs `/v4` vs full completions path).
- `AiEntityResolver` — spoken input → entities: room-number noise stripping (`101房间`/`A栋101`), tenant name/phone matching, `今天`/`上月` parsing, ambiguity detection.
- `AiIntentHandlers` — the five business intents (meter reading, batch meter, SMS, contract query, electricity query).
- `AiEntityResolverTest` — 12 unit tests for the resolver; it is the only part testable without a live model.

- Endpoint: `POST /api/ai-assistant/execute`, body `{"message": "..."}`, `SUPER_ADMIN`/`ADMIN` only. Per-user `ReentrantLock` prevents concurrent commands.
- **No API key configured ⇒ `intent: "NOT_CONFIGURED"`** with an actionable Chinese message, not a generic 500. Keep that behavior: it's the difference between "broken" and "needs setup".
- Keys come only from env (`AI_PRIMARY_API_KEY`, `AI_SILICONFLOW_API_KEY`, `AI_BACKUP_API_KEY`). `application.yml` defaults are empty on purpose — never commit a key.
- Admin UI: `frontend/src/components/AiAssistantDrawer.vue` mounted in `App.vue`, opened by the header button or `Ctrl/⌘ + J`.
- `suggestions` in the response is `List<Map<String,Object>>` (structured candidates: `tenantName` / `buildingName` / `roomNumber` / `contractStatus`), **not** a string array. Render via a formatter, and when the user clicks one, complete the sentence according to `intent` (`SEND_SMS` / `QUERY_CONTRACT`).

## Frontend conventions

- **Composition API + `<script setup>`** everywhere. No TypeScript, no Pinia/Vuex — auth and theme live in `localStorage` via helpers in `frontend/src/api/index.js`.
- **Pages over ~700 lines should push logic into `composables/`.** Existing extras: `useRoomQr` (single + batch QR PDF), `useBatchPayment` (bill bulk collect), `useRoomBillGenerate` (building→floor→room cascade), `useContractAttachments` (version history). Pure formatters/predicates go to `utils/` instead (e.g. `utils/bills.js`: `isBillFinal`, `remainOf`, `isOverdue`). **Existing debt, not a precedent**: `Rooms.vue` (1267), `MeterReadings.vue` (1244), `Bills.vue` (1055), `Contracts.vue` (909), `Tenants.vue` (829), `RoomDetail.vue` (727) and `staff-mobile/src/pages/MobileBillsCenter.vue` (1106) all exceed the line budget. When you touch one for a real reason, extract a composable rather than growing it further.
- **Rename on destructure, not in the template.** Composables expose neutral names (`visible`, `form`, `open`, `submit`); pages alias them to the template's historical names (`visible: batchPayVisible`, `open: openBatchPay`). Changing template identifiers is how you silently break a page.
- **Never `catch {}` around a load.** A swallowed error makes an empty dropdown indistinguishable from a failed request. Use `getErrorMessage` and show it.
- **Routes are lazy-loaded** (`() => import(...)`). Unmatched paths fall through to `pages/NotFound.vue`. `router.afterEach` sets `document.title` from `meta.title`.
- **Search inputs must debounce.** Use `utils/debounce.js`; 300ms is the established default. Call `.cancel()` in `onBeforeUnmount`.
- **All HTTP goes through the single axios instance in `frontend/src/api/index.js`.** Pages import named helpers (`getTenants`, `createContract`, …) plus `getUser`/`hasRole`. Add new endpoints there rather than calling axios from a page.
  - Default `timeout` is 15s. Blob/export upload endpoints must pass `timeout: 60000` explicitly.
  - The response interceptor redirects to `/login` on 401, shows a warning toast on 403 (without navigating away), and warns on 413.
  - **Always surface errors via `getErrorMessage(error, fallback)`** exported from the same module. It handles the backend's inconsistent error shapes (bare string, `{message}`, `{error}`), timeouts, and offline cases. Do not hand-write `e.response?.data || e.message`.
- **Permissions on the client** are `can*()` helpers at the bottom of `api/index.js` (`canEditTenant`, `canVoidBill`, …). Page-level access is controlled by `meta.roles` in `router/index.js`. When adding a gated action, update both: the route's `meta.roles` and the button's `v-if`.
- **Styling** uses Element Plus plus CSS variables from `frontend/src/styles/theme.css`. Use `--bg-main`, `--bg-surface`, `--text-main`, `--text-muted`, `--border-color`, `--accent-color`, `--danger-color`, `--success-color` — hardcoded colors break dark mode. Theme switching (`utils/theme.js`) writes a `theme-dark`/`theme-light` class on `<html>` and is persisted per user via `updateProfile`.
- **Mobile pages live in `staff-mobile/`**, not in `frontend/`. That project has its own router (`/mobile/*`), `MobileLayout` tab bar, and Capacitor back-button handling. QR codes resolve `/qr/room/:id` → `/mobile/room/:id` **on the staff-mobile origin**, so `app.qr.base-url` must point at the mobile deployment, not the admin one.
- **Tenant pages live in `tenant-frontend/`**, not in `frontend/` or `staff-mobile/`. Conventions differ from the admin app:
  - No Element Plus. UI primitives are hand-rolled in `src/styles.css` (`.panel`, `.btn`, `.segmented`, `.tag-*`, `.empty-state`, `.stat-card`, `.star`) plus `src/components/` (`PageHeader`, `EmptyState`, `LoadingBlock`).
  - **Never use `alert()`/`confirm()`** — use `src/utils/toast.js` (`showSuccess`/`showError`/`confirmDialog`/`promptDialog`). The old code used 23 `alert()` calls; they are all gone, keep it that way.
  - **`getErrorMessage(error, fallback)`** is exported from `src/api/index.js` and handles the backend's mixed error shapes. Don't hand-write `e.response?.data`.
  - Status/label formatting lives in `src/utils/format.js` (`billStatusText`, `repairStatusText`, `complaintStatusText`, `contractStatusText`, `renewStatusText`, `money`, `formatTime`). Don't re-derive per page — the four pages used to disagree on casing.
  - Routes carry `meta.title` (drives `document.title`) and `meta.public` (login/register bypass auth). Detail pages need `PageHeader` with an explicit `to` so a deep link still has a working back target.
  - **The UI is Chinese-only.** A half-finished `utils/i18n.js` was removed: only 2 of 24 pages used it, so switching to English left 22 pages in Chinese. `Tenant.languagePreference` still exists and **does** drive backend message templates (`TenantMessageText`) — the 偏好 page exposes it as "消息语言", not as a UI language switch. Don't reintroduce a UI-level i18n layer without covering every page.
  - List pages share `composables/usePagedList.js` (`rows`/`loading`/`hasMore`/`loadMore`/`resetAndLoad`, plus `extra` for backend-provided summaries). Don't copy the pagination boilerplate into a new page.
  - `RenewApplication.status` is an **Integer** (0/1/2), not a string — unlike bills/contracts.
  - Payment flow: create order → trigger mock callback → **re-query** `GET /api/tenant/payment/order/{outTradeNo}` to confirm `paid`. Don't trust the callback response alone.
- **Dashboard charts** are ECharts instances tracked in an array and disposed in `onBeforeUnmount`; there is also a `resize` listener. Any new chart must follow this pattern or it leaks on route change. Charts render only when there is real data — empty states are shown instead of placeholder series, so never reintroduce `|| [12000, ...]`-style fallbacks.

## Gotchas

- `frontend/src/pages/OperationHistory.vue` is not a route — it is embedded inside `Settings.vue`. Deleting it "because nothing links to it" breaks settings.
- **All uploaded files live in MySQL** (`image_blob`, `data` LONGBLOB). `/api/images/{id}` and `/api/files/{storedName}` both read from it. Nothing is written to disk any more, so deployment needs **no persistent volume** — and a database backup contains the images too (the backup zip is just `database.sql`).
  - `FileStorageService` stores a UUID-prefixed `stored_name`; that column exists solely so legacy `/api/files/<name>` URLs keep working. Business tables store either `image_ids` (room/building) or a `/api/files/...` URL (repair/complaint/attachments).
  - `ImageStorageService.cleanupOrphans()` deletes `ref_count = 0` rows older than a day. Anything saved through `FileStorageService` is written with `ref_count = 1` so it is never swept. If you add a new "upload then reference later" flow, decide its `ref_count` deliberately.
  - Applying `migrate_files_to_db.sql` adds `stored_name`; `backend/sql/import_disk_files_to_db.sh` imports any pre-existing files from the old upload dir (idempotent).
- `FileStorageService` validates extension against `app.file.allowed-extensions` (jpg/jpeg/png/gif/webp) — PDFs and Excel are rejected by upload even though PDF generation exists server-side.
- Most list endpoints return **unpaginated** `List<...>` (`TenantController`, `RoomController`, `ExportController`). `TenantService.search` and `DashboardController` load collections once instead of inside loops — keep it that way.
- **`GET /api/bills` is dual-mode.** Omit `page` → returns the old plain `List<Bill>` (used by CSV export and internal callers). Pass `page` + `size` → returns `{content, totalElements, totalPages, page, size}`. **`page` is 0-based** because `staff-mobile` already called it that way; `Bills.vue` keeps a 1-based `currentPage` for `el-pagination` and subtracts 1 on the wire.
- **Financial summary must not be derived from a page.** `GET /api/bills/summary` computes totals over *all* filtered rows and returns the same shape the summary cards need (`total/paid/unpaid/rate/activeCount/paidCount`). Never recompute those numbers from `content` in the browser — that silently reports one page as the whole month.
- **Styling** uses Element Plus plus CSS variables from `frontend/src/styles/theme.css`. Use `--bg-main`, `--bg-surface`, `--text-main`, `--text-muted`, `--border-color`, `--accent-color`, `--danger-color`, `--success-color` — hardcoded colors break dark mode. Theme switching (`utils/theme.js`) writes a `theme-dark`/`theme-light` class on `<html>` and is persisted per user via `updateProfile`. Exception: print/PDF style blocks and room-status badges keep fixed colors intentionally.
- `HouseApplication` seeds a `SUPER_ADMIN` `admin` in a `CommandLineRunner` and swallows exceptions (`catch (Exception e) { // ignore }`). If login fails after a schema change, that swallowed error is a likely cause.
- **The seeded admin password comes from `ADMIN_INIT_PASSWORD`** (`app.admin.init-password`). Empty → falls back to `admin123` for local dev and logs a warning. Never display it in any UI: the old `Login.vue` hints (admin/staff-mobile) were removed — don't reintroduce them.
- `TestController`/`GET /api/test/ping` was removed — it had no `@PreAuthorize` and was reachable by any authenticated user. Don't revive it as a template for new endpoints.

## Regression checks before you commit

Cheap greps that catch the recurring mistakes above:

```bash
# status literals that bypass the helpers (should return nothing)
grep -rn '"空闲"\|"已租"\|"进行中"\|"待支付"' --include='*.java' backend/src/main/java | grep -v model/
grep -rn '"ACTIVE"\|"PAID"\|"pending_confirm"' --include='*.java' backend/src/main/java | grep -v model/

# Map.of fed by a possibly-null exception message
grep -rn 'Map.of("message", e.getMessage())' --include='*.java' backend/src/main/java

# hand-rolled axios error handling in pages
grep -rn 'response?.data' frontend/src staff-mobile/src | grep -v api/index.js

# wildcard permitAll (only the documented narrow paths may be public)
grep -n 'permitAll' backend/src/main/java/com/house/config/SecurityConfig.java

# hardcoded primary key on a generated-id entity (UPDATE of a missing row -> 500)
# (match real calls, not comments)
grep -rn 'setId([0-9]L*)' --include='*.java' backend/src/main/java | grep -v '^\s*//' | grep -v '// '

# PUT handlers that save the *request-body* entity rather than a merged/looked-up one
# (safe patterns: save(existing) / save(msg) after findById; risky: save(<@RequestBody param>))
grep -rn -A6 '@PutMapping' --include='*.java' backend/src/main/java | grep -E '@RequestBody'

# admin controllers with endpoints but zero @PreAuthorize (tenant JWT can reach them)
for f in backend/src/main/java/com/house/controller/*.java; do
  n=$(grep -c '@PreAuthorize' "$f"); m=$(grep -cE '@(Get|Post|Put|Delete|Patch)Mapping' "$f")
  [ "$m" -gt 0 ] && [ "$n" -eq 0 ] && echo "UNGUARDED: $(basename $f)"
done
```
