# AGENTS.md

Agent guide for this repo. Read `README.md` and `FEATURE_SUMMARY.md` before changing business flows — they document the feature set and the manual SQL migrations.

## What this is

房屋租赁管理系统 (house rental management): 楼栋/房间、租户、合同、账单与收款、押金、水电抄表、维修工单、用户/权限、操作日志与撤销快照，外加减量移动端页面（扫码进入）。

**Three** apps, no monorepo tooling — treat them as separate projects:

- `backend/` — Spring Boot 3.4.2, Java 17, Maven. Package root `com.house`. The only API provider.
- `frontend/` — 管理端 (desktop admin). Vue 3 + Vite 4 + Element Plus, plain JavaScript. Root alias `@` → `frontend/src`. Port 5173.
- `staff-mobile/` — 员工移动端 (staff mobile). Separate Vue 3 + Vite project with its own dependencies, `package.json`, `vite.config.js`, router and Capacitor Android project (`com.house.staff`). Port 5180.
- `backend/sql/` — schema, hand-maintained.
- `backend/test-data/meter-import/` — fixtures for the meter-reading Excel import (see its `README.md`).

**Do not re-introduce cross-imports.** `frontend/` must not import from `staff-mobile/` and vice versa; the two share no code, only the HTTP API. `frontend/` has no Capacitor dependency. `tenant-frontend/` exists on `origin/dev` as an unfinished tenant portal and is intentionally not part of the current build.

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
```

**Requires JDK 17.** Lombok 1.18.36 crashes on JDK 21+ with `com.sun.tools.javac.code.TypeTag :: UNKNOWN`. On a machine with multiple JDKs use `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn ...`.

**Backend has JUnit tests** (`backend/src/test/`, run with `mvn test` on JDK 17) covering status normalization, payment state transitions and move-out settlement — 64 cases, all Mockito-based unit tests with no DB. **Frontend has no tests, no linter, and no typechecker**; verify those changes by building and exercising by hand. `npm run build` and `mvn test` are the automated gates.

## Database: schema is manual, `ddl-auto: none`

`application.yml` sets `spring.jpa.hibernate.ddl-auto: none`. Adding a field to a `@Entity` does **not** create a column. Every schema change needs a new `backend/sql/migrate_*.sql` plus a note in `README.md`. A missing migration surfaces as a runtime query error (e.g. `building.image_url`), not a startup failure.

Existing migration scripts are ordered and must be applied by hand on old databases. New columns default to readable Chinese status strings (`"空闲"`, `"已租"`) or English (`PENDING`, `ACTIVE`), and the code accepts both forms — check the existing comparisons before introducing a third spelling.

## Persistence: JPA is primary, MyBatis-Plus is an exception

- Most data access goes through `com.house.repo.*Repository` extending `JpaRepository` (18 repos), with derived query methods (`findByRoomIdAndStatus`, `findByNameContainingOrPhoneContaining`).
- **`UserRepository` is the one MyBatis-Plus `BaseMapper`**, because `UserController`/`UserService` need server-side paging (`selectPage`). There is no `PaginationInnerInterceptor` bean configured — if you add MyBatis paging elsewhere, register the interceptor or page queries will silently return unpaged results.
- The project has **both** `spring-boot-starter-data-jpa` and `mybatis-plus-boot-starter` on the classpath. Don't introduce new MyBatis mappers, mappers XML (none exist), or a second persistence style without a clear reason.

## Backend layering and conventions

`com.house.{model, repo, dto, service, controller, config, util}`.

- **`model`** — JPA entities, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, plus explicit getters/setters on some entities (`Tenant`, `Room`, `Contract`, `Bill`). Follow the file you are editing; don't strip hand-written accessors.
- **`controller`** — thin-ish; but many controllers inject repositories directly and assemble responses (e.g. `DashboardController`, `ExportController`, `MobileRoomController`). Match the local file rather than refactoring toward strict layering.
- **The tenant portal is split by concern**, all sharing prefix `/api/tenant`: `TenantAuthController` (register/login/current/switch-room/update-phone), `TenantHomeController` (home/vacant-rooms/rent-intent), `TenantMessageController` (preferences/messages/dashboard), `TenantSignController` (e-sign). Shared helpers live in `TenantPortalSupport` (masking, room summary, request parsing) and `TenantMessageText` (bilingual message templates). Add new tenant endpoints to the matching controller, not a new one.
- **`service`** — business rules, `@Transactional` on writes. `BillService`, `ContractService`, `RepairOrderService`, `OperationSnapshotService` hold the real logic.
- **`GlobalExceptionHandler` catches what controllers don't.** `com.house.config.GlobalExceptionHandler` maps `IllegalArgumentException`/`IllegalStateException` → 400, `AccessDeniedException` → 403, and specific Spring exceptions → 405 / 413 / 415 / 404 / 400. Anything else is logged and becomes 500. Controllers still catch business errors themselves and return `ResponseEntity.badRequest().body(<string>)`, so error payloads remain **inconsistent**: sometimes a bare string, sometimes `{"message": ...}`. `MeterReadingController` additionally has a local `@ExceptionHandler`.
- **Never hand-write `e.response?.data` in a page.** Use `getErrorMessage(error, fallback)` from `frontend/src/api/index.js` — hand-rolled variants render `[object Object]` when the body is a JSON object and drop the timeout/offline handling.
- **Auth failures are 401, permission failures are 403.** `SecurityConfig` registers an `authenticationEntryPoint` (401) and `accessDeniedHandler` (403); the axios interceptor depends on that split (401 → redirect `/login`, 403 → toast only). Don't remove them — without them Spring Security defaults to 403 for everything and the login redirect never fires.
- **Business messages are Chinese.** Throw `IllegalArgumentException`/`IllegalStateException` with a Chinese message; it reaches the UI verbatim.
- **`Map.of(...)` throws NPE on any null value.** It is the single most common cause of unexplained 500s here — it has already broken `TenantPortalController.home`, `MobileRoomController.roomSummary` and `TenantService.update`. When building a response or metadata map with values that can be null (entity fields from legacy rows, optional dates, `? null : ...` branches), use `new LinkedHashMap<>()` + `put(...)` instead. `Map.of` is only safe for literals and non-null primitives.
- **Undo responses go through `UndoResponse`.** Do not hand-write `"undoSnapshotId" / "undoExpireAt" / "undoTip"`; call `UndoResponse.of(snapshot)` or `UndoResponse.withData("data", entity, snapshot, tip)`. The helper allows a null `expiredAt` so a future change to `capture()` cannot turn every delete endpoint into a 500.
- **`update` methods are partial updates, not full replacements.** Edit dialogs submit only changed fields. Merge field-by-field from the existing row (`if (hasText(incoming.getX())) existing.setX(...)`) and never blindly assign, or a missing field becomes `null` and violates NOT NULL (see `RoomService.update`, `TenantService.update`).
- **Logging** is inconsistent: `@Slf4j`-free files use `System.out`/`e.printStackTrace()` (see `BillService.generateMonthlyBills`), a few use SLF4J (`FileStorageService`). Prefer SLF4J for new code, but don't mass-rewrite existing handlers.
- **Scheduled jobs** (`@EnableScheduling` on `HouseApplication`): monthly bill generation (`BillService` cron `0 0 1 1 * ?`), contract-expiry reminders (`ReminderService`), snapshot cleanup (`OperationSnapshotService`, cron values in `app.snapshot.*`). Check these before changing bill or contract status semantics.

## Room and bill status values

Status is a `String`, and comparisons were historically duplicated across files. **Always go through `com.house.model.RoomStatus` / `com.house.model.BillStatus`** — they normalize both English and Chinese spellings and are the single source of truth:

- Room canonical values: `空闲` / `已租` / `维修`. `RoomStatus.normalize()` maps `IDLE`/`RENTED`/`MAINTENANCE` and legacy aliases (`空置`, `在租`, `维修中`). Helpers: `isIdle`, `isRented`, `isMaintenance`, `matches(raw, filter)`.
- Bill canonical values: `PENDING` / `PARTIAL` / `PAID` / `VOID`. `BillStatus.normalize()` maps `待支付`/`部分支付`/`已支付`/`作废`/`OBSOLETE`. Helpers: `isPending`, `isPartial`, `isPaid`, `isVoid`, `isFinal` (paid or void), `isOutstanding` (not final).

Reading paths (`RoomService.listByBuildingAndFloor`, `BillController.list`, `BillController.get`) normalize on output so the frontend never receives mixed spellings. `backend/sql/migrate_normalize_status_values.sql` cleans historical rows; it is idempotent.

Do **not** add new `"空闲".equals(...)` / `"PAID".equals(...)` literals — extend the helper classes instead.

## Security and auth

- JWT bearer, stateless. `JwtAuthenticationFilter` puts `ROLE_<role>` into the security context; roles are `SUPER_ADMIN`, `ADMIN`, `USER`.
- `SecurityConfig` uses `@EnableMethodSecurity` with ~52 `@PreAuthorize` annotations. Only `/api/auth/**`, `OPTIONS`, and `GET /api/files/**` are public; everything else requires a token.
- Blacklist management (`GET /api/tenants/blacklist`, `POST|DELETE /api/tenants/{id}/blacklist`) is restricted to `SUPER_ADMIN`/`ADMIN`. `GET /api/tenants/{id}/blacklist-check` intentionally stays open to any authenticated user because the contract-creation wizard warns `USER` accounts about blacklisted tenants.
- CORS is configured **twice** — `SecurityConfig.corsConfigurationSource()` (localhost:5173 only) and `WebMvcConfig.addCorsMappings()` (`allowedOrigins("*")`, which is invalid with credentials). Changing one without the other produces confusing preflight failures.
- Secrets come from env with insecure defaults in `application.yml` (`JWT_SECRET`, `CRYPTO_SECRET`). Never hardcode new ones.
- `Tenant.idCard` is encrypted via `@Convert(converter = CryptoConverter.class)` (AES/GCM). Keep that annotation on any new sensitive column and don't log decrypted values.
- Leftover dev artifacts are **tracked in git**: `frontend/login.json` and `frontend/send_login.js` contain the default `admin/admin123` credential. Don't extend them; prefer removing them when you are already in that area.

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
- **Pages over ~700 lines should push logic into `composables/`.** Existing extras: `useRoomQr` (single + batch QR PDF), `useBatchPayment` (bill bulk collect), `useRoomBillGenerate` (building→floor→room cascade), `useContractAttachments` (version history). Pure formatters/predicates go to `utils/` instead (e.g. `utils/bills.js`: `isBillFinal`, `remainOf`, `isOverdue`).
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
- **Dashboard charts** are ECharts instances tracked in an array and disposed in `onBeforeUnmount`; there is also a `resize` listener. Any new chart must follow this pattern or it leaks on route change. Charts render only when there is real data — empty states are shown instead of placeholder series, so never reintroduce `|| [12000, ...]`-style fallbacks.

## Gotchas

- `frontend/src/pages/OperationHistory.vue` is not a route — it is embedded inside `Settings.vue`. Deleting it "because nothing links to it" breaks settings.
- `WebMvcConfig` maps `/uploads/**` to `app.file.upload-dir`, which defaults to `./src/main/resources/imgs` relative to the backend working directory. `FileController` and the upload dir must agree, and files written there land inside the source tree.
- `FileStorageService` validates extension against `app.file.allowed-extensions` (jpg/jpeg/png/gif/webp) — PDFs and Excel are rejected by upload even though PDF generation exists server-side.
- Most list endpoints return **unpaginated** `List<...>` (`TenantController`, `RoomController`, `ExportController`). `TenantService.search` and `DashboardController` load collections once instead of inside loops — keep it that way.
- **`GET /api/bills` is dual-mode.** Omit `page` → returns the old plain `List<Bill>` (used by CSV export and internal callers). Pass `page` + `size` → returns `{content, totalElements, totalPages, page, size}`. **`page` is 0-based** because `staff-mobile` already called it that way; `Bills.vue` keeps a 1-based `currentPage` for `el-pagination` and subtracts 1 on the wire.
- **Financial summary must not be derived from a page.** `GET /api/bills/summary` computes totals over *all* filtered rows and returns the same shape the summary cards need (`total/paid/unpaid/rate/activeCount/paidCount`). Never recompute those numbers from `content` in the browser — that silently reports one page as the whole month.
- **Styling** uses Element Plus plus CSS variables from `frontend/src/styles/theme.css`. Use `--bg-main`, `--bg-surface`, `--text-main`, `--text-muted`, `--border-color`, `--accent-color`, `--danger-color`, `--success-color` — hardcoded colors break dark mode. Theme switching (`utils/theme.js`) writes a `theme-dark`/`theme-light` class on `<html>` and is persisted per user via `updateProfile`. Exception: print/PDF style blocks and room-status badges keep fixed colors intentionally.
- `HouseApplication` seeds a `SUPER_ADMIN` `admin` in a `CommandLineRunner` and swallows exceptions (`catch (Exception e) { // ignore }`). If login fails after a schema change, that swallowed error is a likely cause.
- The seeded admin password exists in three places (`init_db.sql`, `UserService.initDefaultAdmin`, `Login.vue` hint). They must stay in sync.
