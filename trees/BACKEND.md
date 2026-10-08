# Canonical Backend Tree

ไฟล์นี้เป็น source of truth ของ **path** ทุกไฟล์ที่สร้างใหม่ต้องอยู่ใน path ที่ระบุที่นี่ หรือเป็น business module ใต้ `app/` ที่ทำตาม `module_layers` ใน `contracts/structure.yaml` ส่วนที่ต่างจาก tree ของ template มีเหตุผลอยู่ใน `docs/02-DECISIONS.md` (P-007, P-010)

คอลัมน์ **May depend on**: `common` หมายถึง `accesscontrol.common.*` ส่วน `<mod>.pub` หมายถึง public surface ของ module นั้น (`service`, `dto`, `event`, `spi` และ class ที่ root ของ module)

## Repository root

```
admin-api-starter/
├── pom.xml                  # spring-boot-starter-parent 4.1.x, java.version 25
├── mvnw, mvnw.cmd, .mvn/
├── README.md  AGENTS.md  CLAUDE.md  UPGRADING.md
├── .gitignore  .editorconfig
├── docs/02-DECISIONS.md     # ADR ของ template ที่ใช้ + ADR ของโปรเจกต์ (P-xxx)
├── trees/BACKEND.md         # ไฟล์นี้
├── contracts/*.yaml         # structure, profile, dependency rules, naming, permissions
├── .claude/skills/access-control-project-bootstrapper/   # check_structure.py, scaffold.py
├── docker/
│   ├── docker-compose.yml   # postgres:17 (port 5433), mailpit (1025 / 8025)
│   └── Dockerfile           # eclipse-temurin:25-jdk → 25-jre
├── tools/
│   ├── gen-keys.sh          # คู่ key RS256 สำหรับ access-control.token.keys
│   └── new-module.sh        # โครง module ใหม่ใต้ app/
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
| `AccessControlProperties.java` | ค่า config ทั้งหมดใต้ `access-control.*` พร้อม default ตาม API.md |
| `package-info.java` | `@NullMarked`: ทุก package มี `package-info.java` ของตัวเอง |

### `config/` — wiring เท่านั้น ไม่มี logic

| Path | Purpose |
|---|---|
| `SecurityConfig.java` | `SecurityFilterChain`: `/api/auth/**` เปิด, `/api/**` ต้องมี access token, ที่เหลือปิด; CORS; `PasswordEncoder` (Argon2id); ใส่ `RequestContextFilter` และ `RateLimitFilter` |
| `JwtConfig.java` | `JwtEncoder` / `JwtDecoder` (Nimbus) จาก `JwtKeyProvider` |
| `CacheConfig.java` | Caffeine cache แบบ transaction-aware: `accounts`, `role-permissions` |
| `AsyncConfig.java` | `@EnableAsync` (อีเมล), `@EnableScheduling` (งานกลางคืน, ping SSE) |
| `ClockConfig.java` | `Clock` สำหรับทุก timestamp |
| `JacksonConfig.java` | request body แบบเข้มงวด (กฎข้อ 5) |
| `EncryptionConfig.java` | `TextEncryptor` (AES-256-GCM) สำหรับ secret ที่เก็บใน DB ถ้าไม่มี key แอปจะไม่ start (P-014) |

### `security/` — adapter ของ Spring Security

| Path | Purpose | May depend on |
|---|---|---|
| `PermissionAuthenticationConverter.java` | JWT → `ActorAuthentication` ตรวจว่า session ยังเปิด, บัญชี active และดึงสิทธิ์ปัจจุบัน | `identity.pub`, `authorization.pub`, `SessionChecker` |
| `ActorAuthentication.java` | principal คือ `Actor` ส่วน authority คือ permission code | `common` |
| `SessionChecker.java` | interface ที่ authentication implement (P-010) | — |
| `RequestContextFilter.java` | bind `RequestContext` (ScopedValue) + MDC `request_id` | `common` |
| `RateLimitFilter.java` | Bucket4j ต่อ IP บน `/api/auth/*` → 429 | `common` |
| `SecurityProblemHandler.java` | 401/403 เป็น ProblemDetail ที่มี `code` | `common` |
| `jwt/JwtIssuer.java`, `jwt/JwtKeyProvider.java` | ออก access token (`sub`, `sid`) และจัดการ key ตาม `kid` | — |

### `common/` — shared kernel

| Path | Purpose |
|---|---|
| `constant/ErrorCode.java` | code ทั้งหมดของ API.md พร้อม HTTP status |
| `exception/AccessControlException.java` | การปฏิเสธที่ตั้งใจไว้ (ไม่มี stack trace) |
| `error/ApiExceptionHandler.java` | `@RestControllerAdvice` → ProblemDetail + `code` + `requestId` และแปลง constraint ของ DB เป็น code |
| `context/RequestContext.java`, `Device.java`, `Actor.java` | request นี้มาจากไหน (ip, browser, os) และใครเป็นผู้ทำ |
| `dto/FieldChange.java` | หนึ่ง field ที่เปลี่ยนใน activity log (ActivityChange) |

### Module

| Module | Layers | Public surface | หน้าที่ |
|---|---|---|---|
| `identity/` | controller, service, repository, entity, dto, event, policy | `UserService`, `CredentialService`, `dto/*`, `event/*` | user, รหัสผ่าน (Argon2id + lockout), 2FA (TOTP), กฎป้องกันตัวเอง; `UserController` (`/api/users`), `MeController` (`/api/me`) |
| `authentication/` | controller, service, repository, entity, dto, event | `event/*` | login, refresh rotation + reuse detection, session/อุปกรณ์, ลิงก์ในอีเมล; `AuthController` (`/api/auth`), `SessionController` |
| `authorization/` | controller, service, repository, entity, dto, event, policy, spi | `RoleService`, `CorePermissions`, `CoreRoles`, `spi/*`, `dto/*`, `event/*` | role, permission catalog (sync ตอน start), `GrantPolicy`; `RoleController` (`/api/roles`) |
| `settings/` | controller, service, repository, entity, dto, event | `SettingsService`, `dto/*`, `event/*` | `AdminSettings`; `SettingsController` (`/api/settings`) |
| `audit/` | controller, service, repository, entity, dto, event | `event/ActivityRecorded` | ฟัง event ของทุก module แล้วเขียน `audit_logs` ใน transaction เดียวกัน; `ActivityController` (`/api/activity`) |
| `notification/` | service | `NotificationService` | อีเมลเชิญและรีเซ็ตรหัสผ่าน (Thymeleaf, `@Async`, หลัง commit) |
| `liveupdate/` | controller, service | — | SSE `/api/events` ส่ง `NOTIFY admin_changes` ใน transaction ของการเปลี่ยนแปลง แล้ว `ChangeNotificationListener` ของทุก instance ได้ยินและส่งต่อให้ stream โดยรวมการเปลี่ยนแปลงที่เกิดติด ๆ กันเป็นข้อความเดียว (P-015) |

`identity/entity/EncryptedSecretConverter` เข้ารหัสคอลัมน์ TOTP secret ของ `UserCredential` ตอนบันทึก และถอดรหัสตอนอ่าน (P-014)

Entity ทุกตัวเป็น JPA entity แบบ JavaBean: field เป็น private, มี getter (`getEmail()`, `isSystem()`), constructor แบบ protected ที่ไม่มี argument ไว้ให้ Hibernate และไม่มี setter สาธารณะ การเปลี่ยนสถานะทำผ่าน method ที่มีความหมาย เช่น `changeStatus()`, `update()`, `signedIn()` (P-013)

ทิศทางการพึ่งพา (ไม่มีวงวน: R8): `authorization` ← `settings` ← `identity` ← `authentication` ← `notification`, ส่วน `audit` ฟัง event ของทุกตัว และ `liveupdate` ฟังแค่ `audit`

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
| `db/migration/app/` | migration ของ business (schema `app`) |
| `templates/mail/invite.html`, `password-reset.html` | อีเมล |

## `src/test/java/com/adminstarter/`

| Path | Purpose |
|---|---|
| `architecture/*Test.java` | ArchUnit: ModuleBoundary, LayerRule, NamingRule, ForbiddenPackage, SecurityRule, TransactionRule |
| `accesscontrol/<module>/policy/*Test.java`, `service/*Test.java` | unit test |
| `accesscontrol/AuthenticationFlowIT.java`, `AccessRulesIT.java` | ทั้งระบบผ่าน HTTP กับ PostgreSQL จริง (Testcontainers) |
| `support/ApiIntegrationTest.java`, `support/PostgresContainerSupport.java` | โครงสำหรับ IT |
