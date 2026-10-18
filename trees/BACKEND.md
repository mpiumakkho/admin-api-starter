# Canonical Backend Tree

ไฟล์นี้เป็น source of truth ของ **path** ทุกไฟล์ที่สร้างใหม่ต้องอยู่ใน path ที่ระบุที่นี่ หรือเป็น business module ใต้ `app/` ที่ทำตาม `module_layers` ใน `contracts/structure.yaml` ส่วนที่ต่างจาก tree ของ template มีเหตุผลอยู่ใน `docs/02-DECISIONS.md` (P-007, P-010, P-016, P-018)

คอลัมน์ **May depend on**: `common` หมายถึง `accesscontrol.common.*` ส่วน `<mod>.pub` หมายถึง public surface ของ module นั้น (`service`, `dto`, `event`, `spi` และ class ที่ root ของ module)

## Repository root

```
admin-api-starter/
├── pom.xml                  # spring-boot-starter-parent 4.1.x, java.version 25
├── mvnw, mvnw.cmd, .mvn/
├── README.md  UPGRADING.md
├── .gitignore  .editorconfig
├── docs/01-OVERVIEW.md      # ภาพรวม: โมดูลต่อกันอย่างไร คำขอและการเปลี่ยนแปลงข้อมูลหนึ่งครั้งผ่านอะไรบ้าง ข้อมูลอยู่ที่ไหน
├── docs/02-DECISIONS.md     # ADR ของ template ที่ใช้ + ADR ของโปรเจกต์ (P-xxx)
├── docs/OPERATIONS.md       # ดูแลระบบหลังนำขึ้นใช้งานจริง: งานตามเวลา คีย์ การสำรองข้อมูล การตรวจสถานะ log ที่ควรเฝ้า
├── docs/API-COVERAGE.md     # endpoint ของ API.md ที่ server นี้ทำแล้ว สิทธิ์ และ test ที่เรียก (สร้างด้วย tools/api-coverage.py)
├── trees/BACKEND.md         # ไฟล์นี้
├── contracts/*.yaml         # structure, profile, dependency rules, naming, permissions
├── docker/
│   ├── docker-compose.yml   # postgres:17 (port 5433), mailpit (1025 / 8025)
│   └── Dockerfile           # eclipse-temurin:25-jdk → 25-jre
├── tools/
│   ├── gen-keys.sh          # คู่ key RS256 สำหรับ access-control.token.keys
│   ├── new-module.sh        # โครง module ใหม่ใต้ app/
│   ├── check_structure.py   # ตรวจโครงสร้างตาม contracts/structure.yaml (CI รันทุก push)
│   └── api-coverage.py      # เทียบ API.md กับ controller แล้วเขียน docs/API-COVERAGE.md (--check เพื่อตรวจอย่างเดียว)
├── .github/workflows/ci.yml
└── src/
```

## `src/main/java/com/adminstarter/`

| Path | Purpose |
|---|---|
| `Application.java` | `@SpringBootApplication` + `@ConfigurationPropertiesScan` อยู่ที่ root package ตาม convention ของ Spring Boot จึง scan bean, entity และ repository ของทั้ง `accesscontrol` และ `app` ได้เองโดยไม่ต้องตั้งค่าเพิ่ม (P-013) |

## `src/main/java/com/adminstarter/accesscontrol/` — user management (foundation)

| Path | Purpose |
|---|---|
| `AccessControlProperties.java` | ค่า config ทั้งหมดใต้ `access-control.*` พร้อม default ตาม API.md และ `TIME_ZONE` ที่งาน cron ทุกตัวใส่เป็น `zone` (P-030) |
| `package-info.java` | `@NullMarked`: ทุก package มี `package-info.java` ของตัวเอง |

### `config/` — wiring เท่านั้น ไม่มี logic

| Path | Purpose |
|---|---|
| `SecurityConfig.java` | `SecurityFilterChain`: `/api/auth/**` เปิด, `/api/**` ต้องมี access token, ที่เหลือปิด; CORS; `PasswordEncoder` (Argon2id); ใส่ `RequestContextFilter`, `RateLimitFilter` และ `TwoFactorSetupFilter`; bean `ActivityAudienceProvider` จาก `UserService` (P-024) |
| `JwtConfig.java` | `JwtEncoder` / `JwtDecoder` (Nimbus) จาก `JwtKeyProvider` |
| `CacheConfig.java` | Caffeine cache แบบ transaction-aware: `accounts`, `role-permissions`, `two-factor-roles`, `reauth-window`, `idle-timeout` |
| `AsyncConfig.java` | `@EnableAsync` (อีเมล), `@EnableScheduling` (งานกลางคืนตามเขตเวลา `access-control.time-zone`, ping SSE); bean `ActivityRetentionProvider` จาก `SettingsService` (P-018) |
| `ClockConfig.java` | `Clock` สำหรับทุก timestamp |
| `JacksonConfig.java` | request body แบบเข้มงวด (กฎข้อ 5) |
| `EncryptionConfig.java` | `TextEncryptor` (AES-256-GCM) สำหรับ secret ที่เก็บใน DB ถ้าไม่มี key แอปจะไม่ start (P-014) |

### `security/` — adapter ของ Spring Security

| Path | Purpose | May depend on |
|---|---|---|
| `PermissionAuthenticationConverter.java` | JWT → `ActorAuthentication` ตรวจว่า session ยังเปิด, บัญชี active เลือก role ที่นับ ณ ตอนนั้นแล้วรวมสิทธิ์ (P-019) ดูว่ามี role ที่บังคับ two-step sign-in ที่ยังปิดอยู่หรือไม่ (`Actor.twoFactorSetupRequired`) และ session ยืนยันตัวตนมาไม่เกิน `reauthMinutes` หรือไม่ (`Actor.recentSignIn`, P-023) | `identity.pub`, `authorization.pub`, `settings.pub`, `SessionChecker` |
| `ActorAuthentication.java` | principal คือ `Actor` ส่วน authority คือ permission code | `common` |
| `SessionChecker.java` | interface ที่ authentication implement (P-010): session ยังเปิดอยู่ไหม และยืนยันตัวตนล่าสุดเมื่อไร (P-023) | — |
| `RequestContextFilter.java` | bind `RequestContext` (ScopedValue) + MDC `request_id` | `common` |
| `RateLimitFilter.java` | Bucket4j ต่อ IP บน `/api/auth/*` และ `/api/me/reauthenticate` → 429 | `common` |
| `TwoFactorSetupFilter.java` | ต่อจาก `BearerTokenAuthenticationFilter`: คนที่ role บังคับ two-step sign-in แต่ยังไม่เปิด ได้แค่สิ่งที่ใช้เปิดมัน ที่เหลือ → 403 `two_factor_mandatory` (P-016) | `common` |
| `SecurityProblemHandler.java` | 401/403 เป็น ProblemDetail ที่มี `code` | `common` |
| `jwt/JwtIssuer.java`, `jwt/JwtKeyProvider.java` | ออก access token (`sub`, `sid`) และจัดการ key ตาม `kid` | — |

### `common/` — shared kernel

| Path | Purpose |
|---|---|
| `constant/ErrorCode.java` | code ทั้งหมดของ API.md พร้อม HTTP status |
| `exception/AccessControlException.java` | การปฏิเสธที่ตั้งใจไว้ (ไม่มี stack trace) |
| `error/ApiExceptionHandler.java` | `@RestControllerAdvice` → ProblemDetail + `code` + `requestId` และแปลง constraint ของ DB เป็น code |
| `context/RequestContext.java`, `Device.java`, `Actor.java` | request นี้มาจากไหน (ip, browser, os) และใครเป็นผู้ทำ; `Actor.requireRecentSignIn()` ให้การเปลี่ยนแปลงเสี่ยงตรวจการยืนยันตัวตนซ้ำ (P-023) |
| `dto/FieldChange.java` | หนึ่ง field ที่เปลี่ยนใน activity log (ActivityChange) |

### Module

| Module | Layers | Public surface | หน้าที่ |
|---|---|---|---|
| `identity/` | controller, service, repository, entity, dto, event, policy | `UserService`, `CredentialService`, `dto/*`, `event/*` | user, รหัสผ่าน (Argon2id + lockout, กฎจาก settings และประวัติใน `previous_passwords`: P-017), 2FA (TOTP) พร้อมรหัสสำรอง (`RecoveryCodeService`, `recovery_codes`: P-025), กฎป้องกันตัวเอง, การมอบ role ที่มีช่วงเวลา (`role_assignments`, `RoleAssignmentExpiryJob`: P-019), ระงับบัญชีที่ไม่ได้ใช้ (`InactiveAccountJob`: P-027), สิ่งที่การทบทวนสิทธิ์ถาม (`RoleReviewSupport`: การมอบที่รอบครอบคลุม ใครตัดสินได้ และการถอดเมื่อปิดรอบ P-028) ทั้งองค์กรหรือเฉพาะสาขา และสาขาที่ user สังกัด (`BranchMemberResolver`: P-020), ผู้ดูแลระดับสาขา (`policy/BranchScopePolicy`: สิทธิ์ 4 ตัวที่ role ของสาขาให้กับคนในสาขานั้น P-024); `UserController` (`/api/users`), `RoleAssignmentController` (`/api/role-assignments`), `MeController` (`/api/me`) |
| `authentication/` | controller, service, repository, entity, dto, event | `event/*` | login, refresh rotation + reuse detection, บันทึกการเข้าสู่ระบบที่ล้มเหลว (`SignInFailed`: P-026), session/อุปกรณ์, ลิงก์ในอีเมล, ยืนยันตัวตนซ้ำ (`user_sessions.verified_at`: P-023); `AuthController` (`/api/auth`, รวม `GET /password-policy`), `SessionController` (รวม `POST /api/me/reauthenticate`) |
| `authorization/` | controller, service, repository, entity, dto, event, policy, spi | `RoleService`, `CorePermissions`, `CoreRoles`, `spi/*`, `dto/*`, `event/*` | role, permission catalog (sync ตอน start), `GrantPolicy`, กฎบทบาทที่ห้ามถือร่วมกัน (`RoleConflictService`: P-022); `RoleController` (`/api/roles`), `RoleConflictController` (`/api/role-conflicts`) |
| `settings/` | controller, service, repository, entity, dto, event | `SettingsService`, `dto/*`, `event/*` | `AdminSettings` รวมกฎของบัญชี (`twoFactorRoleIds`, `passwordMinLength`, `passwordHistory`, `activityRetentionDays`), `branchesEnabled`, `reasonsRequired` และ `reauthMinutes`; `SettingsService.requireReason` ให้ module อื่นตรวจเหตุผล (P-021) และ `reauthWindow` (cache) ให้ security คิด `Actor.recentSignIn` (P-023); `SettingsController` (`/api/settings`); `RoleEventListener` เอา role ที่ถูกลบออกจาก `twoFactorRoleIds` |
| `branch/` | controller, service, repository, entity, dto, event, spi | `BranchService`, `spi/*`, `dto/*`, `event/*` | สาขา (P-020): `Branch` (code ไม่ซ้ำแบบไม่สนตัวพิมพ์, เปิด/ปิด); `BranchController` (`/api/branches`); `BranchService.requireOpen` ให้ identity ตรวจสาขาก่อนรับคนหรือมอบ role; `spi/BranchMemberProvider` ให้ identity ตอบว่าสาขายังมีคนอยู่ไหม |
| `audit/` | controller, service, repository, entity, dto, event, spi | `event/ActivityRecorded`, `spi/*` | ฟัง event ของทุก module แล้วเขียน `audit_logs` ใน transaction เดียวกัน พร้อมเหตุผลที่ event พกมา (`reason`, P-021); `ActivityController` (`/api/activity?from=&to=`); `AuditRetentionJob` ลบ activity ที่เก่ากว่าที่ `ActivityRetentionProvider` บอกทุกคืน; คนที่มี `activity.view` แค่ระดับสาขาเห็นเฉพาะ entry ของคนที่ `ActivityAudienceProvider` บอก (P-024) |
| `notification/` | service | `NotificationService` | อีเมลเชิญและรีเซ็ตรหัสผ่าน อีเมลแจ้งเตือนเรื่องบัญชีจาก `SecurityNoticeListener` (P-027) และอีเมลบอกผู้ทบทวนเมื่อเปิดรอบทบทวนสิทธิ์จาก `AccessReviewNoticeListener` (P-028) (Thymeleaf, `@Async`, หลัง commit) เป็นภาษาของผู้รับที่ `RecipientResolver` หาให้ ข้อความจาก `i18n/mail*.properties` (P-029) |
| `review/` | controller, service, repository, entity, dto, event | `event/*` | ทบทวนสิทธิ์ (P-028): `AccessReview` กับ `AccessReviewItem` (การมอบตามที่เป็นตอนเปิดรอบ เก็บเป็นข้อความ ไม่มี FK); `AccessReviewService` (เปิด ตัดสิน ปิด ลบ โดยถาม identity ผ่าน `RoleReviewSupport`); `AccessReviewJob` ปิดรอบที่ครบกำหนดทุกชั่วโมง; `AccessReviewController` (`/api/access-reviews`) |
| `liveupdate/` | controller, service | — | SSE `/api/events` ส่ง `NOTIFY admin_changes` ใน transaction ของการเปลี่ยนแปลง แล้ว `ChangeNotificationListener` ของทุก instance ได้ยินและส่งต่อให้ stream โดยรวมการเปลี่ยนแปลงที่เกิดติด ๆ กันเป็นข้อความเดียว (P-015) |

`identity/entity/EncryptedSecretConverter` เข้ารหัสคอลัมน์ TOTP secret ของ `UserCredential` ตอนบันทึก และถอดรหัสตอนอ่าน (P-014)

Entity ทุกตัวเป็น JPA entity แบบ JavaBean: field เป็น private, มี getter (`getEmail()`, `isSystem()`), constructor แบบ protected ที่ไม่มี argument ไว้ให้ Hibernate และไม่มี setter สาธารณะ การเปลี่ยนสถานะทำผ่าน method ที่มีความหมาย เช่น `changeStatus()`, `update()`, `signedIn()` (P-013)

ทิศทางการพึ่งพา (ไม่มีวงวน: R8): `authorization` ← `settings` ← `identity` ← `authentication` ← `notification`, `identity` ← `review` (notification ฟังแค่ event ของ review), ส่วน `audit` ฟัง event ของทุกตัว และ `liveupdate` ฟังแค่ `audit` ค่าที่ audit ต้องถาม (`audit/spi`) ต่อเข้ากับ module อื่นใน `config` (P-018)

## `src/main/java/com/adminstarter/app/` — ของโปรเจกต์

| Path | Purpose |
|---|---|
| `config/AppPermissions.java` | `PermissionCatalog` ของ business (ตอนนี้ยังว่าง) |
| `config/AppRoles.java` | `RoleTemplate`: Manager, Support, Viewer |
| `<module>/controller\|service\|repository\|entity\|dto/` | business module ใหม่ (`tools/new-module.sh`) |

## `src/main/resources/`

| Path | Purpose |
|---|---|
| `application.yml` | ค่าสำหรับพัฒนา |
| `application-prod.yml` | เฉพาะส่วนที่ production ต่าง: secret จาก env, cookie `__Secure-`, log แบบ ECS |
| `db/migration/access_control/V1__access_control_schema.sql` | ตารางทั้งหมดของ foundation |
| `db/migration/access_control/V2__settings_account_rules.sql` | กฎของบัญชีใน `admin_settings` พร้อม default และ check constraint (P-016–P-018) |
| `db/migration/access_control/V3__previous_passwords.sql` | ประวัติรหัสผ่าน (P-017) |
| `db/migration/access_control/V4__role_assignments.sql` | หลาย role ต่อคน การมอบแต่ละครั้งมีวันเริ่มและวันสิ้นสุด แทน `users.role_id` (P-019) |
| `db/migration/access_control/V5__branches.sql` | สาขา คนสังกัดสาขา และการมอบ role เฉพาะสาขา (P-020) |
| `db/migration/access_control/V6__reasons.sql` | `audit_logs.reason` เหตุผลของการเปลี่ยนแปลงสำคัญ (P-021) |
| `db/migration/access_control/V7__role_conflicts.sql` | บทบาทที่ห้ามถือร่วมกัน (P-022) |
| `db/migration/access_control/V8__reauthentication.sql` | เวลาที่แต่ละ session ยืนยันตัวตนล่าสุด (P-023) |
| `db/migration/access_control/V9__activity_target_index.sql` | index ของ `audit_logs.target_id` ให้ผู้ดูแลสาขาอ่าน activity ของคนในสาขา (P-024) |
| `db/migration/access_control/V10__recovery_codes.sql` | รหัสสำรองของ two-step sign-in (P-025) |
| `db/migration/access_control/V11__activity_retention_minimum.sql` | เก็บ activity อย่างน้อย 90 วัน (P-026) |
| `db/migration/access_control/V12__account_hygiene.sql` | ระงับบัญชีที่ไม่ได้ใช้ และ session ที่ไม่ได้ใช้งาน (P-027) |
| `db/migration/access_control/V13__access_reviews.sql` | รอบทบทวนสิทธิ์ ผู้ทบทวนที่ระบุชื่อ และการมอบที่แต่ละรอบครอบคลุม (P-028) |
| `db/migration/access_control/V14__email_language.sql` | ภาษาของอีเมลของแต่ละคน และภาษาเริ่มต้นในการตั้งค่า (P-029) |
| `db/migration/app/` | migration ของ business (schema `app`) |
| `templates/mail/invite.html`, `password-reset.html`, `notice.html` | อีเมล ข้อความมาจาก `#{...}` |
| `i18n/mail.properties`, `i18n/mail_th.properties` | ข้อความของอีเมลภาษาอังกฤษและไทย (P-029); `MailMessagesTest` ตรวจว่ามี key ตรงกัน |

## `src/test/java/com/adminstarter/`

| Path | Purpose |
|---|---|
| `architecture/*Test.java` | ArchUnit: ModuleBoundary, LayerRule, NamingRule, ForbiddenPackage, SecurityRule, TransactionRule, SchedulingRule (J1: งาน cron รันตามเขตเวลาขององค์กร) |
| `accesscontrol/<module>/policy/*Test.java`, `service/*Test.java` | unit test |
| `accesscontrol/*IT.java` | ทั้งระบบผ่าน HTTP กับ PostgreSQL จริง (Testcontainers) หนึ่งไฟล์ต่อหนึ่งหัวข้อของ API.md: `AuthenticationFlowIT`, `AccessRulesIT` ("Rules the server must check"), `AccountRulesIT` (กฎของบัญชีในหน้าตั้งค่า), `RoleAssignmentsIT`, `BranchesIT`, `ReasonsIT`, `RoleConflictsIT`, `ReauthenticationIT`, `BranchAdministratorsIT`, `RecoveryCodesIT`, `FailedSignInsIT`, `AccountHygieneIT`, `AccessReviewsIT`, `EmailLanguageIT` และ `LiveUpdateIT` (หลาย instance ผ่าน `LISTEN/NOTIFY`) |
| `support/ApiIntegrationTest.java`, `support/PostgresContainerSupport.java` | โครงสำหรับ IT |
