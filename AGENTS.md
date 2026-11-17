# AGENTS.md

Agent guide for this repo. Read `README.md` and `FEATURE_SUMMARY.md` before changing business flows — they document the feature set and the manual SQL migrations.

## What this is

房屋租赁管理系统 (house rental management): 楼栋/房间、租户、合同、账单与收款、押金、水电抄表、维修工单、用户/权限、操作日志与撤销快照，外加减量移动端页面（扫码进入）。

Two apps, no monorepo tooling — treat them as separate projects:

- `backend/` — Spring Boot 3.4.2, Java 17, Maven. Package root `com.house`.
- `frontend/` — Vue 3 + Vite 4 + Element Plus, plain JavaScript (no TypeScript). Root alias `@` → `frontend/src`.
- `backend/sql/` — schema, hand-maintained.
- `backend/test-data/meter-import/` — fixtures for the meter-reading Excel import (see its `README.md`).

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
```

**Requires JDK 17.** Lombok 1.18.36 crashes on JDK 21+ with `com.sun.tools.javac.code.TypeTag :: UNKNOWN`. On a machine with multiple JDKs use `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn ...`.

There is **no test suite, no linter, and no typechecker**. There are no `src/test` directories and no `lint`/`test` npm scripts. Verify changes by building both apps and exercising them by hand — `npm run build` and `mvn clean compile` are the only automated gates available.

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
- **`service`** — business rules, `@Transactional` on writes. `BillService`, `ContractService`, `RepairOrderService`, `OperationSnapshotService` hold the real logic.
- **No global `@ControllerAdvice`.** Only `MeterReadingController` has a local `@ExceptionHandler`. Most controllers catch and return `ResponseEntity.badRequest().body(<string>)`, so error payloads are **inconsistent**: sometimes a bare string, sometimes `{"message": ...}`. Frontend error handling compensates with `e.response?.data?.message || e.response?.data` — keep both shapes working, or normalize deliberately across all call sites.
- **Business messages are Chinese.** Throw `IllegalArgumentException`/`IllegalStateException` with a Chinese message; it reaches the UI verbatim.
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

## Frontend conventions

- **Composition API + `<script setup>`** everywhere. No TypeScript, no Pinia/Vuex — auth and theme live in `localStorage` via helpers in `frontend/src/api/index.js`.
- **Routes are lazy-loaded** (`() => import(...)`). Unmatched paths fall through to `pages/NotFound.vue`. `router.afterEach` sets `document.title` from `meta.title`.
- **Search inputs must debounce.** Use `utils/debounce.js`; 300ms is the established default. Call `.cancel()` in `onBeforeUnmount`.
- **All HTTP goes through the single axios instance in `frontend/src/api/index.js`.** Pages import named helpers (`getTenants`, `createContract`, …) plus `getUser`/`hasRole`. Add new endpoints there rather than calling axios from a page.
  - Default `timeout` is 15s. Blob/export upload endpoints must pass `timeout: 60000` explicitly.
  - The response interceptor redirects to `/login` on 401, shows a warning toast on 403 (without navigating away), and warns on 413.
  - **Always surface errors via `getErrorMessage(error, fallback)`** exported from the same module. It handles the backend's inconsistent error shapes (bare string, `{message}`, `{error}`), timeouts, and offline cases. Do not hand-write `e.response?.data || e.message`.
- **Permissions on the client** are `can*()` helpers at the bottom of `api/index.js` (`canEditTenant`, `canVoidBill`, …). Page-level access is controlled by `meta.roles` in `router/index.js`. When adding a gated action, update both: the route's `meta.roles` and the button's `v-if`.
- **Styling** uses Element Plus plus CSS variables from `frontend/src/styles/theme.css`. Use `--bg-main`, `--bg-surface`, `--text-main`, `--text-muted`, `--border-color`, `--accent-color`, `--danger-color`, `--success-color` — hardcoded colors break dark mode. Theme switching (`utils/theme.js`) writes a `theme-dark`/`theme-light` class on `<html>` and is persisted per user via `updateProfile`.
- **Mobile pages** (`pages/mobile/*`) are declared `isLoginPage`-style bare routes — no sidebar/header. QR codes resolve `/qr/room/:id` → `/mobile/room/:id`. They still require a token, and `USER`-role accounts are bounced back to the room page from meter/repair/bill routes by the router guard.
- **Dashboard charts** are ECharts instances tracked in an array and disposed in `onBeforeUnmount`; there is also a `resize` listener. Any new chart must follow this pattern or it leaks on route change. Charts render only when there is real data — empty states are shown instead of placeholder series, so never reintroduce `|| [12000, ...]`-style fallbacks.

## Gotchas

- `frontend/src/pages/OperationHistory.vue` is not a route — it is embedded inside `Settings.vue`. Deleting it "because nothing links to it" breaks settings.
- `WebMvcConfig` maps `/uploads/**` to `app.file.upload-dir`, which defaults to `./src/main/resources/imgs` relative to the backend working directory. `FileController` and the upload dir must agree, and files written there land inside the source tree.
- `FileStorageService` validates extension against `app.file.allowed-extensions` (jpg/jpeg/png/gif/webp) — PDFs and Excel are rejected by upload even though PDF generation exists server-side.
- List endpoints largely return **unpaginated** `List<...>` (`TenantController`, `RoomController`, `BillController`, `ExportController`). `Bills.vue` compensates with client-side pagination over the full response. `TenantService.search` and `DashboardController` now load collections once instead of inside loops — keep it that way.
- **Styling** uses Element Plus plus CSS variables from `frontend/src/styles/theme.css`. Use `--bg-main`, `--bg-surface`, `--text-main`, `--text-muted`, `--border-color`, `--accent-color`, `--danger-color`, `--success-color` — hardcoded colors break dark mode. Theme switching (`utils/theme.js`) writes a `theme-dark`/`theme-light` class on `<html>` and is persisted per user via `updateProfile`. Exception: print/PDF style blocks and room-status badges keep fixed colors intentionally.
- `HouseApplication` seeds a `SUPER_ADMIN` `admin` in a `CommandLineRunner` and swallows exceptions (`catch (Exception e) { // ignore }`). If login fails after a schema change, that swallowed error is a likely cause.
- The seeded admin password exists in three places (`init_db.sql`, `UserService.initDefaultAdmin`, `Login.vue` hint). They must stay in sync.
