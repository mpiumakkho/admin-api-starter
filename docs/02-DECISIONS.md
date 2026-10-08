# 02 — Decisions (ADR)

โปรเจกต์นี้ตั้งจาก template **access-control** (`accessbase-docs`, schema_version 1) ADR ของ template ยังใช้อยู่ ยกเว้นข้อที่ ADR ของโปรเจกต์ด้านล่างเขียนทับไว้ ถ้าจะตัดสินใจอะไรที่ขัดกับรายการนี้ ให้เพิ่ม ADR ใหม่ที่อ้างถึงข้อเดิม ห้ามแก้ข้อเดิมเงียบ ๆ

รูปแบบ: **Context → Decision → Consequences**

## ADR ของ template ที่ใช้กับโปรเจกต์นี้

| ADR | เรื่อง | สถานะในโปรเจกต์นี้ |
|---|---|---|
| 0001 | Authentication ทำเองบน Spring Security + JWT (ไม่ใช้ Keycloak) | ใช้ |
| 0002 | Permission model: Role → Permission, โค้ดเช็ค permission ไม่เช็ค role | ใช้ แต่รูปแบบ code เปลี่ยนตาม P-002 และหนึ่ง user มีหนึ่ง role ตาม P-003 |
| 0003 | Token: access JWT 15 นาที + refresh cookie แบบ opaque หมุนทุกครั้ง, stateless, permission resolve จาก DB + cache | ใช้ พร้อมส่วนเพิ่มใน P-004 |
| 0004 | Enforcement ที่ method (`@PreAuthorize`), กฎป้องกันตัวเองอยู่ใน `policy/` | ใช้ |
| 0005 | Modular monolith, module-first + layer เดิม, ArchUnit คุม boundary | ใช้ |
| 0006 | Reuse แบบ template repo | ไม่เกี่ยว: โปรเจกต์นี้เป็น service ที่ derive มา |
| 0007 | Frontend = Angular 22 + ng-zorro-antd | ถูกแทนที่โดย P-009 |
| 0008 | เมนู derive จาก route config | ไม่เกี่ยว: เป็นเรื่องของ frontend (admin-ng-starter ทำเอง) |
| 0009 | Permission ประกาศจากโค้ดผ่าน `PermissionCatalog` / `RoleTemplate` | ใช้ |
| 0010 | State ฝั่ง Angular = signals | ไม่เกี่ยว: เป็นเรื่องของ frontend |

## P-001 สัญญา API คือ API.md ของ admin-ng-starter

- Context: หน้าจอทั้งหมดของ admin-ng-starter เสร็จแล้ว และคุยกับ server ผ่าน `HttpAdminApi` ตามที่ `src/app/user-management/API.md` กำหนด ส่วน endpoint ของ template (`/api/admin/users`, `PUT /roles/{id}/permissions`, pagination ฝั่ง server ฯลฯ) เป็นอีกชุดหนึ่ง
- Decision: ทำ endpoint ตาม API.md ทุกตัว ภายใต้ `/api` เช่น `/api/users`, `/api/roles`, `/api/activity`, `/api/settings`, `/api/events` ส่วน sign-in และบัญชีตัวเองใช้ชื่อ path ของ template คือ `/api/auth/*` และ `/api/me/*` (ตาม P-004) list ตอบเป็น array เต็ม ไม่มี `PageResponse` เพราะหน้าจอกรอง เรียง และแบ่งหน้าเอง
- Consequences: แก้สัญญาเมื่อไรต้องแก้ API.md, `HttpAdminApi`, `localBackend` และ server ใน change เดียวกัน `contracts/permissions.yaml` มีตาราง endpoint → permission ที่ตรงกับ API.md

## P-002 Permission code แบบ `resource.action`

- Context: template ใช้ `user:read` แต่ catalog.ts ของ frontend, role ที่เก็บไว้ และหน้าจอทุกหน้า ใช้ `users.view`
- Decision: ใช้ code ชุดเดียวกับ frontend (`users.view`, `roles.manage` …) pattern `^[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*$`
- Consequences: `CorePermissions` ต้องตรงกับ `PERMISSION_GROUPS` ใน catalog.ts เสมอ ส่วน `SecurityRuleTest` ตรวจว่าทุก `hasAuthority()` อ้าง code ที่ประกาศไว้จริง

## P-003 หนึ่ง user มีหนึ่ง role

- Context: model ของ frontend คือ `User.roleId` ตัวเดียว ทั้งหน้าจอและกฎ "ห้ามให้สิทธิ์เกินกว่าที่ตัวเองมี" คิดจาก role เดียว
- Decision: `users.role_id` (FK → `roles`) แทนตาราง `user_roles` ไม่มี role ชั่วคราวหรือ `expires_at` และไม่มี `roles_version` เพราะสิทธิ์ resolve ใหม่ทุก request (cache 60 วินาที โดย evict หลัง commit ทุกครั้งที่ user หรือ role เปลี่ยน)
- Consequences: ถ้าวันหนึ่งต้องการหลาย role ต่อคน ต้องเปลี่ยนทั้ง frontend model และกฎใน access-rules.ts ด้วย

## P-004 รายละเอียด token ที่เพิ่มจาก ADR-0003

- Context: frontend มีหน้า "อุปกรณ์ที่เข้าสู่ระบบ" (ดูและสั่งจบ session ของตัวเองหรือคนที่ดูแล) และ API.md กำหนดว่าการระงับผู้ใช้หรือการจบ session ต้องมีผลทันที นอกจากนี้ตามมาตรฐานแล้ว cookie ชื่อ `__Host-` ใช้คู่กับ `Path` อื่นนอกจาก `/` ไม่ได้
- Decision:
  - หนึ่งครั้งที่ sign-in คือหนึ่ง **session** (ตาราง `user_sessions`) refresh token ทุกตัวผูกกับ session ส่วน access JWT มี claim `sid` แทน `rv`
  - ทุก request ตรวจว่า session ยังเปิดอยู่ และบัญชียัง active (`PermissionAuthenticationConverter`) จึงตอบ 401 ทันทีหลังจบ session หรือระงับผู้ใช้
  - refresh cookie มี `Path=/api/auth` เพื่อให้ `/api/auth/logout` จบ session ได้แม้ access token หมดอายุแล้ว ชื่อเป็น `__Secure-ac_rt` ใน production และ `ac_rt` ตอนพัฒนา (ไม่มี HTTPS) server ไม่สนใจ header `Authorization` ที่ส่งมากับ `/api/auth/**`
  - refresh token ที่ถูกแทนแล้วยังใช้ได้อีก 30 วินาที (`reuse-grace`) รองรับกรณีสองแท็บ refresh พร้อมกัน เกินจากนั้นถือว่าถูกขโมย และจบ session นั้น
  - "Keep me signed in" ทำให้ cookie อยู่ข้ามการปิด browser และมีอายุ 30 วัน ถ้าไม่เลือก อายุคือ 12 ชั่วโมงแบบ sliding
  - `/api/events` (SSE) ปิด stream เองเมื่อ access token ที่ใช้เปิดหมดอายุ
- Consequences: ทุก request มีการอ่านแถว `user_sessions` หนึ่งครั้ง (ด้วย primary key) และเขียน `last_seen_at` ไม่เกินนาทีละครั้งต่อ session

## P-005 Activity log เขียนใน transaction เดียวกับการเปลี่ยนแปลง และลบ user จริง

- Context: template เขียน audit แบบ `AFTER_COMMIT` และลบ user แบบ soft delete ส่วน API.md ต้องการ "หนึ่งรายการต่อการเปลี่ยนแปลง" และการ import แบบ "ทั้งหมดหรือไม่เลย" frontend ไม่มีแนวคิด user ที่ถูกลบแต่ยังอยู่
- Decision: `AuditListener` ใช้ `@EventListener` จึงทำงานใน transaction ของการเปลี่ยนแปลง การลบ user เป็นการลบจริง โดย session, credential และลิงก์ในอีเมลถูกลบตามด้วย `on delete cascade` ส่วน `audit_logs` ไม่มี FK และเก็บชื่อไว้เป็นข้อความ
- Consequences: ไม่มีกรณีที่การเปลี่ยนแปลงถูกบันทึกแต่ไม่มี log หรือมี log แต่การเปลี่ยนแปลงถูก rollback และเพราะ email ไม่ถูกจองไว้ จึงเชิญคนเดิมกลับมาด้วย email เดิมได้ อีเมลและ SSE ส่งหลัง commit (`@TransactionalEventListener`)

## P-006 รหัสผ่านขั้นต่ำ 8 ตัว ไม่มี username และไม่มี must-change-password

- Context: frontend ตรวจรหัสผ่านขั้นต่ำ 8 ตัว (`NEW_PASSWORD_LENGTH`) เข้าสู่ระบบด้วย email และไม่มีหน้าบังคับเปลี่ยนรหัส
- Decision: `access-control.password.min-length: 8` เข้าสู่ระบบด้วย email (ไม่สนตัวพิมพ์เล็กใหญ่ และ unique ด้วย `lower(email)`) ผู้ดูแลไม่เคยรู้รหัสผ่านของใคร: คนที่ถูกเชิญตั้งรหัสเองจากลิงก์ ส่วนการ reset จะส่งลิงก์ให้ตั้งรหัสใหม่ (ดู P-017)
- Consequences: ถ้าจะเพิ่มความยาวขั้นต่ำ ให้แก้ทั้ง property และ `NEW_PASSWORD_LENGTH` (ดู P-017)

## P-007 Module เพิ่ม: `settings` และ `liveupdate`

- Decision: `settings` เก็บ `AdminSettings` (หนึ่งแถว) ส่วน `liveupdate` ถือ SSE stream ไว้ใน memory และได้ยินการเปลี่ยนแปลงผ่าน `ActivityRecorded` ของ audit อย่างเดียว เพราะทุกการเปลี่ยนแปลงมี activity
- Consequences: ถ้ารันหลาย instance stream จะได้ยินเฉพาะการเปลี่ยนแปลงที่เกิดบน instance เดียวกัน ก่อน scale out ต้องส่งต่อผ่าน `LISTEN/NOTIFY` ของ PostgreSQL หรือ broker (ทำแล้วใน P-015)

## P-008 Two-step sign-in (TOTP) อยู่ใน identity

- Decision: secret อยู่ใน `user_credentials` (ตามที่ template เตรียม `mfa_secret` ไว้) algorithm คือ RFC 6238 (SHA-1, 30 วินาที, 6 หลัก, ยอมคลาด ±1 step) ตรงกับ totp.ts รหัสที่ผิดนับรวมกับรหัสผ่านที่ผิดใน lockout (ดู P-016)
- Consequences: secret เก็บแบบไม่เข้ารหัส ถ้าต้องการเข้ารหัส at rest ให้เพิ่ม ADR (ทำแล้วใน P-014)

## P-009 Frontend คือ admin-ng-starter

- Context: ADR-0007 ของ template เลือก ng-zorro-antd พร้อม library `projects/access-control` แต่ frontend ของโปรเจกต์นี้มีอยู่แล้ว (Angular 22 กับชุด `ui` ของตัวเอง)
- Decision: contract ฝั่ง frontend ของ template (`trees/FRONTEND.md`, กฎ F1–F8) ไม่ใช้กับโปรเจกต์นี้ แต่หลักของ template ยังใช้ครบ: access token อยู่ใน memory เท่านั้น ไม่เก็บใน `localStorage`, refresh ครั้งเดียวเมื่อเจอ 401 พร้อมคิว request และ UI ซ่อนปุ่มเป็นแค่ UX
- Consequences: `check_structure.py --side frontend` ไม่มีความหมายกับ admin-ng-starter

## P-010 ปรับโครงสร้าง backend จาก trees ของ template

- Decision:
  - `security/SessionChecker` เป็น interface ที่ authentication implement เพราะ authentication พึ่ง security อยู่แล้ว (`JwtIssuer`) ถ้าให้ security ไปเรียก authentication กลับจะเกิด cycle (R8) (ดู P-018)
  - `common/exception` มี `AccessControlException` ตัวเดียว ส่วน HTTP status มากับ `ErrorCode`
  - ไม่มี `FlywayConfig` (ตั้งค่าผ่าน `spring.flyway.*`) และไม่มี `RateLimitConfig` (`RateLimitFilter` สร้างใน `SecurityConfig` เพื่อไม่ให้ servlet container ลงทะเบียน filter ซ้ำ) แต่มี `ClockConfig` กับ `JacksonConfig` เพิ่มเข้ามา (ดู P-016)
  - ไม่มี module ตัวอย่าง `app/inventory`
- Consequences: `contracts/structure.yaml` ของโปรเจกต์นี้ระบุโครงสร้างจริง และ `check_structure.py` ตรวจตามนั้น

## P-011 Log แบบ structured ของ Spring Boot

- Decision: production ใช้ `logging.structured.format.console: ecs` แทน `logback-spring.xml` ทุกบรรทัดมี `request_id` (MDC จาก `RequestContextFilter`) และ response มี header `X-Request-Id`

## P-012 รูปแบบ error

- Context: template ใช้ ProblemDetail ร่วมกับ code ตัวพิมพ์ใหญ่ (`SELF_PROTECTION`, 409) ส่วน frontend อ่าน `body.code` เป็นตัวพิมพ์เล็กตามรายการใน `AdminApiErrorCode`
- Decision: error ทุกตัวเป็น RFC 9457 ProblemDetail ที่มี `code` (เช่น `email_taken`, `self_change`) และ `requestId` โดย status ตามตาราง Errors ของ API.md เพิ่ม code ไว้สองตัว: `invalid_token` (400, ลิงก์ในอีเมล) และ `too_many_requests` (429)
- Consequences: เพิ่ม code ใหม่เมื่อไร ต้องเพิ่มใน `ErrorCode.java`, `AdminApiErrorCode`/`CODES` และข้อความแปลของ frontend ใน change เดียวกัน

## P-013 ตาม convention ของ Spring Boot

- Context: tree ของ template วาง `Application.java` ไว้ใน `com.<org>.app` จึงต้องเพิ่ม `scanBasePackages`, `@EntityScan` และ `@EnableJpaRepositories` ให้ Spring เห็น `accesscontrol` ด้วย ส่วน entity ใช้ accessor แบบ record (`email()`) ซึ่งต่างจาก JavaBean ที่คนใช้ Spring/JPA คุ้น
- Decision: `Application.java` อยู่ที่ root package `com.adminstarter` ใช้แค่ `@SpringBootApplication` กับ `@ConfigurationPropertiesScan` และ `@SpringBootTest` หา configuration เองได้ entity ใช้ getter แบบ JavaBean (`getEmail()`, `isSystem()`) ส่วน DTO, event และ value object ยังเป็น record เหมือนเดิม
- Consequences: `contracts/structure.yaml` ไม่บังคับ `app/Application.java` แต่บังคับ `com/adminstarter/Application.java` แทน และ `app/` เหลือไว้สำหรับ business module กับ `app/config` เท่านั้น

## P-014 เข้ารหัส TOTP secret ใน DB (ต่อจาก P-008)

- Context: P-008 เก็บ secret ของ two-step sign-in ไว้แบบไม่เข้ารหัส ใครที่ได้สำเนา DB หรือ backup ไปจึงสร้างรหัส 6 หลักแทนเจ้าของได้
- Decision: คอลัมน์ `two_factor_secret` และ `pending_two_factor_secret` ผ่าน `EncryptedSecretConverter` (JPA `AttributeConverter`) ซึ่งเข้ารหัสด้วย `TextEncryptor` ของ Spring Security (`Encryptors.delux`: AES-256-GCM, IV สุ่มใหม่ทุกครั้ง, key derive จาก `access-control.encryption.password` + `salt`) ค่าในคอลัมน์ขึ้นต้นด้วย `enc:v1:` เพื่อให้เปลี่ยนวิธีเข้ารหัสได้ภายหลัง ส่วนค่าที่ไม่มี prefix (เขียนไว้ก่อนมีการเข้ารหัส) อ่านได้ตามเดิมและจะถูกเข้ารหัสเมื่อบันทึกครั้งถัดไป ถ้าไม่ได้ตั้ง key แอปจะไม่ start (`EncryptionConfig`)
- Consequences: key ต้องเก็บไว้ตลอด ถ้าเปลี่ยนหรือหาย secret ที่เก็บไว้จะอ่านไม่ได้ และทุกคนที่เปิด two-step sign-in ไว้ต้องให้ผู้ดูแลกด reset ให้ ตอนพัฒนาใช้ key ใน `application.yml` ส่วน production ใช้ `AC_ENCRYPTION_PASSWORD` และ `AC_ENCRYPTION_SALT` (`tools/gen-keys.sh` สร้างให้)

## P-015 Live update ข้ามหลาย instance ผ่าน `LISTEN/NOTIFY` (ต่อจาก P-007)

- Context: P-007 เก็บ SSE stream ไว้ใน memory ของแต่ละ instance ถ้ารันหลายตัว การเปลี่ยนแปลงบนตัวหนึ่งจะไม่ไปถึง dashboard ที่ต่ออยู่กับตัวอื่น
- Decision: ทุกครั้งที่มี `ActivityRecorded` liveupdate จะสั่ง `NOTIFY admin_changes` ใน transaction ของการเปลี่ยนแปลงนั้น PostgreSQL ส่งออกไปตอน commit, ทิ้งไปถ้า rollback และรวม `NOTIFY` ที่ซ้ำกันใน transaction เดียวเป็นอันเดียว ส่วน `ChangeNotificationListener` ของทุก instance (รวมถึงตัวที่เกิดการเปลี่ยนแปลง) `LISTEN` อยู่บน connection ของตัวเองใน virtual thread แล้วส่ง `data: changed` ไปยัง stream ที่เปิดอยู่ ถ้า connection หลุดก็ต่อใหม่ โดยรอนานขึ้นเรื่อย ๆ และเมื่อต่อได้แล้ว จะส่ง `changed` หนึ่งครั้งเผื่อมีการเปลี่ยนแปลงที่พลาดไปช่วงนั้น ไม่ต้องใช้ Redis หรือ broker เพิ่ม
- Consequences: แต่ละ instance ใช้ connection ใน pool ถาวรหนึ่งเส้น และ driver ของ PostgreSQL กลายเป็น dependency ตอน compile (ใช้ `PGConnection`) ถ้าจะรันหลาย instance ทุกตัวต้องใช้ `access-control.token.keys` และ `access-control.encryption` ชุดเดียวกัน ไม่อย่างนั้น token จากตัวหนึ่งจะใช้กับอีกตัวไม่ได้ ส่วน rate limit กับ cache ของสิทธิ์ยังนับแยกต่อ instance (cache มี TTL 60 วินาที)


## P-016 Role ที่ต้องใช้ two-step sign-in (ต่อจาก P-008)

- Context: API.md เพิ่ม `twoFactorRoleIds` ในการตั้งค่า คนที่ถือ role ในรายการนี้และยังไม่ได้เปิด two-step sign-in ยัง sign in ได้ แต่ทุก request ต้องตอบ `403` `two_factor_mandatory` จนกว่าจะเปิด ยกเว้นสิ่งที่ต้องใช้เพื่อเปิดมัน: `GET /me`, `GET /settings`, `GET /roles`, `POST /me/two-factor` และ `POST /me/two-factor/confirm` (ส่วน `/auth/*` ไม่ใช้ access token อยู่แล้ว)
- Decision:
  - `PermissionAuthenticationConverter` ใส่ `Actor.twoFactorSetupRequired` = role ต้องใช้ และบัญชียังปิดอยู่ โดยถาม `SettingsService.requiresTwoFactor(roleId)` ซึ่ง cache ไว้ใน `two-factor-roles` แบบเดียวกับ `accounts` และ `role-permissions` (evict หลัง commit ทุกครั้งที่ settings เปลี่ยน) จึงไม่ต้องใส่อะไรใน token: สถานะคำนวณใหม่ทุก request เหมือนสิทธิ์ (P-003) เปิดเสร็จ request ถัดไปก็ผ่านทันที
  - `security/TwoFactorSetupFilter` อยู่ต่อจาก `BearerTokenAuthenticationFilter` ของ Spring จึงเห็น `Actor` แล้ว และตอบ `two_factor_mandatory` ผ่าน `SecurityProblemHandler` ก่อนการตรวจ permission ใด ๆ filter สร้างใน `SecurityConfig` แบบ `RateLimitFilter` (P-010) ไม่ใช่ bean เพื่อไม่ให้ถูกลงทะเบียนซ้ำ
  - `POST /me/two-factor/disable` ตอบ `two_factor_mandatory` ระหว่างที่ role ยังบังคับ ส่วน `POST /users/:id/two-factor/reset` ของผู้ดูแลยังทำได้ (มือถือหาย) แล้วเจ้าของบัญชีต้องเปิดใหม่ก่อนทำอย่างอื่น
  - `twoFactorRoleIds` เป็น `uuid[]` ใน `admin_settings` ซึ่งมี FK ไม่ได้ การลบ role จึงเอา role นั้นออกจากรายการใน transaction เดียวกัน (`settings/service/RoleEventListener`) โดยไม่มี activity `settings.updated` แยก เพราะ `role.deleted` บอกอยู่แล้ว เหมือน local-admin-api.ts
  - เพิ่ม `ErrorCode.TWO_FACTOR_MANDATORY` (403)
- Consequences: คนที่ถูกกันใช้ `/api/events` ไม่ได้ด้วย live update ของเขาจึงเริ่มทำงานหลังเปิด two-step sign-in แล้ว รายการ path ที่ยกเว้นอยู่ใน `TwoFactorSetupFilter.SETUP` ถ้า API.md เปลี่ยนรายการนี้ต้องแก้ที่นั่นด้วย ถ้ารันหลาย instance การเปลี่ยน `twoFactorRoleIds` อาจช้าได้ถึง TTL ของ cache (60 วินาที) บน instance อื่น

## P-017 กฎรหัสผ่านอยู่ในการตั้งค่า และมีประวัติรหัสผ่าน (เปลี่ยน P-006)

- Context: P-006 ตั้งความยาวขั้นต่ำตายตัวที่ 8 ตัวผ่าน `access-control.password.min-length` ตอนนี้ API.md ให้ผู้ดูแลตั้งเองในหน้าตั้งค่า: `passwordMinLength` (8–64) และ `passwordHistory` (0–24 คือจำนวนรหัสล่าสุด รวมรหัสปัจจุบัน ที่ห้ามใช้ซ้ำ, 0 ไม่ตรวจ) ใช้กับทุกที่ที่ตั้งรหัส และหน้าที่เปิดจากลิงก์ในอีเมลต้องอ่านกฎได้โดยยังไม่ sign in
- Decision:
  - ลบ `access-control.password.min-length` ค่าเริ่มต้นใน settings คือ 8 และ 0 ตาม `DEFAULT_SETTINGS` ใน catalog.ts (`V2__settings_account_rules.sql` มี default และ check constraint) ค่าที่เกินช่วงตอบ `invalid_request` จาก Bean Validation
  - `PasswordPolicy` เป็น pure function ไม่ใช่ bean: `check(password, minLength)` → `weak_password` และ `recentHashes()` กับ `checkNotReused()` → `password_reused` (422) `CredentialServiceImpl` ใช้ตอน `POST /me/password`, `/auth/activate` และ `/auth/password-reset/confirm` ตามลำดับเดียวกับ local-admin-api.ts: ยาวพอก่อน แล้วจึงไม่ซ้ำ รหัสที่ถูกปฏิเสธทำให้ transaction rollback ลิงก์ในอีเมลจึงยังใช้ได้
  - ตาราง `previous_passwords` (`V3__previous_passwords.sql`) เก็บ hash Argon2id ของรหัสที่ถูกแทน ทุกครั้งที่ตั้งรหัสใหม่ รหัสปัจจุบันจะย้ายเข้าไป และเก็บไม่เกิน 24 แถวต่อคนไม่ว่า `passwordHistory` ตอนนั้นจะเป็นเท่าไร (ขึ้นค่าทีหลังจึงนับรหัสที่ใช้ไปแล้วด้วย) แถวถูกลบตาม user ด้วย `on delete cascade`
  - "รหัสล่าสุด N ตัว" คือรหัสปัจจุบัน (ถ้ามี) ตามด้วยประวัติจากใหม่ไปเก่า N ตัวแรก ผู้ดูแลกด reset รหัสของคนอื่น รหัสเดิมย้ายเข้าประวัติก่อนถูกล้าง จึงนับเป็นตัวแรกเมื่อตั้งรหัสใหม่จากลิงก์ เหมือน `checkNewPassword()` ใน local-admin-api.ts
  - `GET /api/auth/password-policy` → `{ minLength, history }` อยู่ใน `AuthController` ใต้ `/api/auth/**` ที่เปิดอยู่แล้ว ไม่ใส่ rate limit เพราะเป็นการอ่านที่ไม่ได้เดาความลับอะไร
  - เพิ่ม `ErrorCode.PASSWORD_REUSED` (422)
- Consequences: ความยาวขั้นต่ำ 8 ตัวของ P-006 เป็นแค่ค่าเริ่มต้น frontend อ่านค่าจริงจาก settings หรือ `/auth/password-policy` แทน `NEW_PASSWORD_LENGTH` การตรวจประวัติเรียก Argon2 `matches` ได้ถึง N ครั้ง (ไม่เกิน 24) ต่อการตั้งรหัสหนึ่งครั้ง ช้ากว่าเดิมแต่เกิดไม่บ่อย การลด `passwordHistory` ไม่ลบประวัติที่เก็บไว้

## P-018 ระยะเก็บ activity อยู่ในการตั้งค่า ผ่าน spi ของ audit

- Context: API.md ย้ายระยะเก็บ activity มาเป็น `activityRetentionDays` (30–3650, ค่าเริ่มต้น 730) ในหน้าตั้งค่า และ `GET /activity` รับ `from`/`to` แต่ audit ฟังแค่ event (R5) จึงเรียก `SettingsService` ไม่ได้ และ audit ฟัง `SettingsUpdated` ของ settings อยู่แล้ว
- Decision:
  - audit มี layer `spi` ที่มี `ActivityRetentionProvider` ซึ่ง `config/AsyncConfig` สร้างเป็น bean จาก `SettingsService` ส่วน `AuditRetentionJob` ถามค่าทุกคืน การเปลี่ยนค่าในหน้าตั้งค่าจึงมีผลตั้งแต่คืนถัดไป ลบ `access-control.audit.retention-days` (เหลือ `list-limit`) และ naming ของ spi รับ suffix `Provider` เพิ่ม
  - ตัวเชื่อมอยู่ใน `config` ไม่ใช่ใน settings: audit ฟัง event ของ settings อยู่แล้ว ถ้า settings implement spi ของ audit จะเกิด cycle ระหว่างสอง module (R8) ส่วน `config` พึ่งได้ทั้งสองฝั่งโดยไม่มีใครพึ่งกลับ R8 จึงไม่ต้องเปลี่ยน
  - `GET /api/activity?from=&to=` รับ ISO 8601 instant (`Z` หรือ offset เช่น `+07:00`) ทั้งสองไม่บังคับ `from` นับรวม `to` ไม่นับ เรียงใหม่ไปเก่า และยังจำกัดที่ `access-control.audit.list-limit` ค่าที่อ่านไม่ได้ตอบ `invalid_request`
- Consequences: ค่าที่เคยตั้งใน `access-control.audit.retention-days` ถูกข้ามไป installation เดิมได้ 730 วันจาก migration แล้วตั้งใหม่ในหน้าตั้งค่า module อื่นที่อยากให้ audit ถามอะไรก็ทำแบบเดียวกันได้ โดยไม่ทำให้ audit พึ่ง module นั้น
