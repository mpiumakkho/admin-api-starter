# 02 — Decisions (ADR)

โปรเจกต์นี้ตั้งจาก template **access-control** (`accessbase-docs`, schema_version 1) ADR ของ template ยังใช้อยู่ ยกเว้นข้อที่ ADR ของโปรเจกต์ด้านล่างเขียนทับไว้ ถ้าจะตัดสินใจอะไรที่ขัดกับรายการนี้ ให้เพิ่ม ADR ใหม่ที่อ้างถึงข้อเดิม ห้ามแก้ข้อเดิมเงียบ ๆ

รูปแบบ: **Context → Decision → Consequences**

## ADR ของ template ที่ใช้กับโปรเจกต์นี้

| ADR | เรื่อง | สถานะในโปรเจกต์นี้ |
| --- | --- | --- |
| 0001 | Authentication ทำเองบน Spring Security + JWT (ไม่ใช้ Keycloak) | ใช้ |
| 0002 | Permission model: Role → Permission, โค้ดเช็ค permission ไม่เช็ค role | ใช้ แต่รูปแบบ code เปลี่ยนตาม P-002 และหนึ่ง user มีได้หลาย role ตาม P-019 |
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

- Status: ถูกแทนที่โดย P-019
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

- Status: ความยาวขั้นต่ำถูกเปลี่ยนโดย P-017 (ย้ายไปอยู่ในการตั้งค่า `passwordMinLength` และไม่มี `access-control.password.min-length` แล้ว) ส่วนการเข้าสู่ระบบด้วย email และการไม่มี must-change-password ยังใช้
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

## P-019 หลาย role ต่อ user, การมอบ role มีช่วงเวลา และ role ในตัวมาจากโค้ด (แทน P-003)

- Context: P-003 เก็บ role เดียวใน `users.role_id` และตัดเรื่อง role ชั่วคราวออก แต่ระบบองค์กรต้องการทั้งสองอย่าง: NIST RBAC (ANSI INCITS 359) ให้หนึ่งคนถือได้หลาย role, Microsoft Entra PIM และ Salesforce ให้การมอบมีวันเริ่มและวันสิ้นสุด และตั้งกติกาการมอบแยกตาม role ได้ ส่วน role ในตัวของ Entra และ AWS เหมือนกันทุก installation และแก้ไม่ได้ API.md จึงเปลี่ยน `User.roleId` เป็น `User.roles: RoleAssignment[]` เพิ่ม `/role-assignments` และ `Role.assignment`
- Decision:
  - ตาราง `role_assignments` (id, user_id FK cascade, role_id FK, starts_at, ends_at null = ถาวร, reason, assigned_by FK set null, assigned_at, expiry_logged_at) แทน `users.role_id` migration ย้าย role เดิมของทุกคนเป็นการมอบแบบถาวรที่เริ่มตั้งแต่ `created_at` แล้วลบคอลัมน์
  - การมอบนับเมื่อ `starts_at <= now < ends_at` สิทธิ์ของคนคือสิทธิ์รวมของทุก role ที่นับอยู่ `PermissionAuthenticationConverter` ยังคิดใหม่ทุก request (P-015): cache `accounts` เก็บรายการการมอบพร้อมวันที่ แล้วเลือกที่นับ ณ ตอนนั้น การมอบจึงหมดผลตรงเวลา ไม่ต้องรอ cache หมดอายุ `Actor` ถือชุด role id แทน role เดียว
  - กฎ: มอบได้เมื่อถือทุกสิทธิ์ของ role นั้น จัดการใครได้เมื่อถือทุกสิทธิ์ของทุก role ที่เขาถือตอนนี้หรือที่นัดไว้ ห้ามแก้การมอบของตัวเอง และห้ามแก้สิทธิ์ของ role ที่ตัวเองถืออยู่ (`self_change`) ทั้งหมดอยู่ใน `GrantPolicy` และ `SelfProtectionPolicy` เหมือนเดิม
  - `roles` เพิ่ม `assignment_max_days` (1–365 หรือ null) และ `assignment_reason_required` เกินวันตอบ `assignment_too_long` ไม่มีเหตุผลตอบ `reason_required` (422) การมอบซ้อนช่วงกับของเดิมของ role เดียวกันตอบ `already_assigned` (409)
  - ต้องมี user ที่ active ถือ role ที่มีทุกสิทธิ์แบบถาวรเหลืออย่างน้อยหนึ่งคน (แบบ super admin คนสุดท้ายของ Google Workspace) การถอด การใส่วันสิ้นสุด การระงับ หรือการลบที่ทำให้ไม่เหลือ ตอบ `last_administrator` (409) เพราะเมื่อ role หมดอายุได้ ระบบอาจไม่มีผู้ดูแลเหลือ
  - role ในตัว (`is_system`) ซิงก์ชื่อ คำอธิบาย และสิทธิ์จาก `CoreRoles`/`AppRoles` ทุกครั้งที่เริ่มระบบ ไม่ใช่แค่ครั้งแรก Administrator ได้ทุกสิทธิ์ที่ประกาศไว้ รวมสิทธิ์ที่เพิ่มทีหลัง `PATCH` แก้ได้แค่ `tone` และ `assignment` นอกนั้นตอบ `system_role`
  - การมอบที่หมดอายุยังเก็บไว้ให้ต่ออายุได้ แล้วลบพร้อม activity ตาม `activityRetentionDays` (P-018) `RoleAssignmentExpiryJob` รันทุก 5 นาที เขียน `role_assignment.expired` (actor null) ครั้งเดียวต่อการมอบผ่าน `expiry_logged_at` แล้วส่ง live update ทุก instance รัน job นี้ได้พร้อมกันเพราะ update แบบมีเงื่อนไขทำให้เขียนครั้งเดียว
  - activity ใหม่: `role_assignment.added`, `.updated`, `.removed`, `.expired` แทน `user.updated` ที่ field `roleId` และ `activity.actor_id` เป็น null ได้ เพิ่ม `ErrorCode` สี่ตัวข้างบน
- Consequences: สัญญา API เปลี่ยนแบบใช้กับของเดิมไม่ได้ (`User.roleId`, `POST /users/role`) frontend และ server ต้องเปลี่ยนพร้อมกันตาม P-001 `SettingsService.requiresTwoFactor` รับชุด role (P-016) ส่วน `defaultRoleId` ใช้กับ user ใหม่ที่ไม่ได้ระบุ role ผู้ดูแลที่เคยแก้สิทธิ์ของ Manager จะเห็นค่ากลับเป็นตามโค้ดหลังอัปเกรด ต้องคัดลอกเป็น role ใหม่แทน

## P-020 สาขา: module `branch`, คนสังกัดหนึ่งสาขา และการมอบ role จำกัดเฉพาะสาขา (ต่อจาก P-019)

- Context: API.md เพิ่มสาขาแบบเลือกเปิดได้ (`branchesEnabled` ในการตั้งค่า) Google Workspace ให้ผู้ใช้แต่ละคนอยู่ใน organizational unit เดียว ส่วน Azure RBAC ให้การมอบ role มีขอบเขต (scope) API.md จึงเพิ่ม `User.branchId` (สาขาที่สังกัด หรือ null) และ `RoleAssignment.branchId` (null = ทั้งองค์กร) กับ `/branches`
- Decision:
  - module ใหม่ `branch` ({controller, service, repository, entity, dto, event, spi}) อยู่ใต้ identity ในทิศทางการพึ่งพา: `authorization` ← `settings` ← `branch` ← `identity` เพราะ identity ต้องตรวจว่าสาขามีอยู่และเปิดอยู่ ส่วน branch ไม่พึ่ง module ใดนอกจาก common รายชื่อ module ใน `ModuleBoundaryTest`, `contracts/dependency-rules.yaml` และ `contracts/structure.yaml` เพิ่ม `branch` (แบบเดียวกับ P-007)
  - ตาราง `branches` (code unique ไม่สนตัวพิมพ์, name, active) `users.branch_id` และ `role_assignments.branch_id` เป็น FK (`V5__branches.sql`) สาขาที่ปิดเก็บคนและการมอบเดิมไว้ แต่รับคนหรือการมอบใหม่ไม่ได้ (`branch_closed`) การลบตอบ `branch_in_use` ถ้ายังมีคนสังกัดหรือถือ role ที่สาขานั้นตอนนี้หรือภายหลัง ซึ่ง identity ตอบผ่าน `branch/spi/BranchMemberProvider` (แบบ `RoleHolderProvider` ของ P-019) การมอบที่หมดแล้วของสาขานั้นถูกลบไปด้วย (on delete cascade)
  - สิทธิ์ที่ user management ตรวจ (`hasAuthority`) มาจากการมอบแบบทั้งองค์กรเท่านั้น การมอบเฉพาะสาขาให้สิทธิ์ในสาขานั้น `Actor.branchPermissions` และ `Actor.can(permission, branchId)` ให้ module ธุรกิจใช้ ผู้ดูแลระดับสาขา (จัดการคนในสาขาตัวเอง) ยังไม่ทำ เพราะกฎ "ห้ามให้สิทธิ์เกินที่มี" ต้องคิดแยกตามขอบเขต
  - `last_administrator` นับเฉพาะการมอบแบบถาวรและทั้งองค์กร การมอบซ้อนช่วงนับแยกตาม (role, ขอบเขต)
  - permission ใหม่ `branches.view` และ `branches.manage` ใน `CorePermissions` (Administrator ได้เองตาม P-019) Manager ได้ `branches.view`
  - activity `branch.created`, `branch.updated`, `branch.deleted` และ `ErrorCode` `CODE_TAKEN`, `BRANCH_IN_USE`, `BRANCH_CLOSED` (409)
- Consequences: เพิ่มสาขาหรือขอบเขตอื่น (เขต, ภาค) ทีหลังได้โดยเปลี่ยน `branch_id` เป็นขอบเขตแบบลำดับชั้น module ธุรกิจที่กรองข้อมูลตามสาขาใช้ `Actor.can(permission, branchId)` แทน `hasAuthority` ตรง ๆ

## P-021 เหตุผลของการเปลี่ยนแปลงสำคัญ: `Activity.reason` และ `reasonsRequired` ในการตั้งค่า

- Context: ระบบองค์กรให้ผู้ดูแลระบุเหตุผล (justification) ของการกระทำที่ตัดหรือเปลี่ยนการเข้าถึงของคนอื่น แบบ Microsoft Entra PIM และ log ของผู้ดูแลใน Google Workspace ก่อนหน้านี้มีเหตุผลเฉพาะตอนมอบ role (P-019) ซึ่งเก็บเป็น `changes` ชื่อ `reason`
- Decision:
  - เหตุผลมีที่เดียวคือ `audit_logs.reason` (`V6__reasons.sql`) ส่งออกเป็น `ActivityResponse.reason` (null เมื่อไม่มี) event ที่มีเหตุผลพกมาเอง: `UserStatusChanged`, `UserDeleted`, `UserPasswordReset`, `TwoFactorChanged` (เมื่อผู้ดูแล reset), `SessionsEnded`, `RoleAssignmentAdded` (เหตุผลของการมอบ ย้ายออกจาก `changes`) และ `RoleAssignmentRemoved`
  - การตั้งค่า `reasonsRequired` เลือกได้ห้าอย่าง: `status`, `delete`, `removeRole`, `resetSignIn`, `endSessions` ค่าเริ่มต้นคือ `status` กับ `delete` เก็บใน `admin_settings.reasons_required` เมื่อเลือกไว้แล้วไม่มีเหตุผล ตอบ `reason_required` (422) ทุก module ตรวจผ่าน `SettingsService.requireReason(action, reason)` ซึ่งคืนเหตุผลที่ตัดช่องว่างแล้ว ส่วนที่ไม่ได้เลือกยังส่งเหตุผลมาได้
  - คำขอที่รับ `reason`: `POST /users/status`, `/users/delete`, `/users/:id/reset-password`, `/users/:id/two-factor/reset`, `/users/:id/sessions/end`, `POST /role-assignments/batch` (กับ `removeRoleId`) และ `DELETE /role-assignments/:id`, `DELETE /sessions/:id` ซึ่งรับ body `{ reason }` ที่ไม่บังคับ การกระทำกับตัวเอง (session หรือรหัสผ่านของตัวเอง) ไม่ต้องมีเหตุผล
  - การแก้เหตุผลของการมอบที่มีอยู่ (`PATCH /role-assignments/:id`) ยังลง `changes` ชื่อ `reason` เพราะเป็นข้อมูลของการมอบที่เปลี่ยน ไม่ใช่เหตุผลของคำขอ
- Consequences: entry `role_assignment.added` เก่าที่เก็บเหตุผลใน `changes` ยังอ่านได้ตามเดิม `DELETE` ที่มี body บาง proxy อาจตัดทิ้ง ถ้าต้องเจอให้เปลี่ยนเป็นคำขอ `POST` แบบ PIM (`roleAssignmentScheduleRequests`) ทีหลัง เหตุผลยาวไม่เกิน 500 ตัวอักษรเท่ากับเหตุผลของการมอบ

## P-022 บทบาทที่ห้ามถือร่วมกัน (separation of duties) เป็นกฎใน authorization ที่ identity ตรวจตอนมอบ

- Context: ระบบองค์กรกันไม่ให้คนเดียวทั้งทำและตรวจงานเดียวกัน ตาม static separation of duty ของ NIST RBAC และ SoD rules ของ SAP GRC กับ Okta API.md เพิ่ม `/role-conflicts`: กฎหนึ่งข้อมีชื่อ คำอธิบาย และ role ตั้งแต่ 2 ถึง 20 ตัวที่ห้ามคนเดียวถือพร้อมกัน
- Decision:
  - กฎอยู่ใน module `authorization` (เจ้าของ role): entity `RoleConflict`, ตาราง `role_conflicts` กับ `role_conflict_roles` (`V7__role_conflicts.sql`, FK ไป `roles` แบบ on delete cascade), `RoleConflictController` (`/api/role-conflicts`: อ่านได้ทุกคนที่ลงชื่อเข้าใช้ แก้ด้วย `roles.manage`) และ `RoleConflictService.rulesWith(roleId)` ให้ identity เรียก ไม่มี module ใหม่
  - identity ตรวจใน `RoleAssignmentService.check` ทุกทางที่มอบ role (มอบรายคน, batch, ผู้ใช้ใหม่, นำเข้า) และตอนเลื่อนวันสิ้นสุด: ถ้าคนนั้นถือ role อื่นในกฎเดียวกันในช่วงเวลาที่ทับกัน ตอบ `ROLE_CONFLICT` (409) ไม่สนขอบเขต (ทั้งองค์กรหรือสาขา) เพราะ role ของสาขาก็ทำให้คนเดียวทำทั้งสองอย่างได้
  - การสร้างกฎไม่ตรวจคนที่ถืออยู่ก่อนแล้ว หน้า roles แสดงรายชื่อที่ต้องแก้ (คำนวณจาก `GET /users` กับกฎ) แบบ access review แล้วกฎจะกันคนใหม่ตั้งแต่นั้น
  - ลบ role แล้ว role นั้นหลุดจากกฎ กฎที่เหลือ role ไม่ถึงสองตัวถูกลบไปด้วยใน transaction เดียวกับการลบ role
  - activity `role_conflict.created`, `.updated`, `.deleted` (ชนิด roles) พร้อม `roles` เป็นรายการที่เพิ่มและเอาออก
- Consequences: ยังไม่มีข้อยกเว้นแบบ mitigating control ของ SAP ถ้าต้องให้บางคนถือทั้งสองได้ ต้องแก้กฎ การมอบที่ทับกับ role ที่ "จะเริ่ม" ภายหลังก็ถูกกันด้วย เพราะนับทั้งที่ถืออยู่และที่นัดไว้

## P-023 ยืนยันตัวตนซ้ำก่อนการเปลี่ยนแปลงที่เสี่ยง (step-up re-authentication) ด้วยเวลาที่ session ยืนยันล่าสุด

- Context: browser ที่ลงชื่อเข้าใช้ค้างไว้ หรือ session ที่ถูกขโมย ทำได้ทุกอย่างที่เจ้าของทำได้ ระบบองค์กรจึงขอรหัสผ่านอีกครั้งก่อนการกระทำที่ยึดหรือปิดระบบได้ แบบ sudo mode ของ GitHub และ reauthentication ของ Microsoft Entra API.md เพิ่มหัวข้อ "Confirming who you are", `POST /me/reauthenticate`, `reauthMinutes` ในการตั้งค่า และ code `reauth_required`
- Decision:
  - `user_sessions.verified_at` (`V8__reauthentication.sql`) คือเวลาที่ session นั้นพิสูจน์ตัวตนล่าสุด: ตอน sign-in และตอน `POST /api/me/reauthenticate` สำเร็จ การ refresh ไม่นับ แถวเดิมได้ค่า `created_at` ส่วน `admin_settings.reauth_minutes` (0–1440, ค่าเริ่มต้น 10, 0 = ไม่ถามซ้ำ) กำหนดว่าการยืนยันใช้ได้นานเท่าไร
  - ไม่ใส่เวลาลงใน token (แบบ claim `auth_time` ของ OpenID Connect) เพราะทุก request อ่านแถว session อยู่แล้ว (P-004) `SessionChecker.verifiedAt()` แทน `isOpen()` จึงได้เวลามาพร้อมการตรวจว่า session ยังเปิดอยู่ และหลังยืนยันแล้ว client ไม่ต้องเปลี่ยน token แค่ส่งคำขอเดิมซ้ำ
  - `PermissionAuthenticationConverter` ใส่ `Actor.recentSignIn` = `reauthMinutes` เป็น 0 หรือ `verified_at` ยังไม่เกินเวลานั้น โดยถาม `SettingsService.reauthWindow()` ซึ่ง cache ไว้ใน `reauth-window` แบบ `two-factor-roles` (P-016) service ที่ทำการเปลี่ยนแปลงเสี่ยงเรียก `actor.requireRecentSignIn()` หลังตรวจว่าทำได้ (สิทธิ์, `forbidden`, `self_change`, `not_found`) และก่อนเปลี่ยนอะไร ถ้าเกินเวลาตอบ `REAUTH_REQUIRED` (403)
  - รายการตายตัวในโค้ด: การมอบ แก้ และถอด role (รวม `POST /users` และ `/users/import` ที่ให้ role ผู้ใช้ใหม่), การแก้สิทธิ์ของ role และการลบ role, การลบผู้ใช้, การ reset รหัสผ่านหรือ two-step sign-in ของคนอื่น, การจบ session ของคนอื่น และ `PATCH /settings` คนที่แก้การตั้งค่าได้จึงเอาข้อใดข้อหนึ่งออกเองไม่ได้ ปรับได้แค่เวลา
  - ใช้ 403 ไม่ใช่ 401 `insufficient_user_authentication` ของ RFC 9470 เพราะ 401 ของแอปแปลว่าให้ refresh token ซึ่งแก้เรื่องนี้ไม่ได้ และเข้าชุดกับ `two_factor_mandatory`
  - `POST /api/me/reauthenticate` อยู่ใน `SessionController` (authentication ซึ่งเป็นเจ้าของ session) ใช้ `LoginService` ตรวจรหัสผ่านและรหัส 6 หลักแบบเดียวกับ sign-in: ผิดนับรวมใน lockout และ `RateLimitFilter` จำกัดต่อ IP เท่ากับ `/api/auth/login` ไม่เขียน activity เพราะการเปลี่ยนแปลงที่ตามมามี entry ของตัวเองอยู่แล้ว
- Consequences: ทุก request อ่าน `verified_at` จากแถวเดิม ไม่มี query เพิ่ม frontend รับ `reauth_required` ที่ `AdminStore` เปิดหน้าต่างขอรหัสผ่าน แล้วส่งคำขอเดิมซ้ำเอง ถ้ารันหลาย instance การเปลี่ยน `reauthMinutes` อาจช้าได้ถึง TTL ของ cache (60 วินาที) บน instance อื่น การเปลี่ยนแปลงเสี่ยงอื่นที่เพิ่มทีหลัง (เช่น กฎบทบาทที่ห้ามถือร่วมกัน) เพิ่มได้ด้วยการเรียก `requireRecentSignIn()` และเพิ่มในตารางของ API.md

## P-024 ผู้ดูแลระดับสาขา: role ที่จำกัดเฉพาะสาขาให้สิทธิ์จัดการผู้ใช้ 4 ตัวกับคนในสาขานั้น (ต่อจาก P-020)

- Context: P-020 ให้สิทธิ์ของ user management มาจากการมอบแบบทั้งองค์กรเท่านั้น และเลื่อนเรื่องผู้ดูแลระดับสาขาไว้ องค์กรไทยที่แบ่งงานตามสาขา เช่น ธนาคาร ค้าปลีก สหกรณ์ และโรงพยาบาล ให้ผู้จัดการสาขาดูแลพนักงานของสาขาตัวเอง แบบ administrative units ของ Microsoft Entra และ OU admin ของ Google Workspace API.md เพิ่มหัวข้อ "Branch administrators"
- Decision:
  - `CorePermissions.BRANCH_SCOPED` = `users.view`, `users.create`, `users.edit`, `activity.view` คือสิทธิ์ที่การมอบแบบจำกัดสาขาให้กับคนที่สังกัดสาขานั้น (`users.branch_id`) ส่วน `users.delete`, `roles.*`, `settings.*`, `branches.*` และ `activity.export` ยังมาจากการมอบแบบทั้งองค์กรเท่านั้น
  - `ActorAuthentication` มี authority เป็นสิทธิ์ทั้งองค์กรรวมกับสิทธิ์ใน `BRANCH_SCOPED` ที่ถือในสาขาใดก็ได้ `@PreAuthorize` จึงหมายถึง "ทำได้ที่ไหนสักแห่ง" แล้ว service จำกัดเองตามสาขาของคนเป้าหมาย ผ่าน `identity/policy/BranchScopePolicy.allows(actor, permission, branchId)` ทุกคำขอที่กระทำกับคนหนึ่งคน (`UserAccessService.findManageable(actor, userId, permission)`) จึงต้องระบุสิทธิ์ที่ใช้
  - กฎข้อ 2 และ 3 นับตามขอบเขต: `Actor.permissionsIn(branchId)` คือสิทธิ์ทั้งองค์กรรวมกับสิทธิ์ของ role ที่จำกัดอยู่ในสาขานั้น การมอบ role ที่จำกัดสาขาต้องถือทุกสิทธิ์ของ role ในสาขานั้น (`RoleService.requireAssignable(actor, roleId, branchId)`) และการจัดการคนต้องถือทุกสิทธิ์ของแต่ละ role ที่เขาถือตอนนี้หรือที่นัดไว้ ในขอบเขตของ role นั้น ผู้ดูแลสาขาจึงจัดการคนที่ถือ role ทั้งองค์กรที่มีสิทธิ์เกินกว่า role ทั้งองค์กรของตัวเองไม่ได้
  - ผู้ใช้ใหม่ที่ผู้ดูแลสาขาเพิ่มต้องสังกัดสาขาที่เขามี `users.create` และถ้าไม่ได้ระบุ role จะได้ default role แบบจำกัดสาขานั้น เพราะการมอบแบบทั้งองค์กรต้องมีสิทธิ์ทั้งองค์กร การย้ายสาขาย้ายได้เฉพาะไปสาขาที่มี `users.edit`
  - `GET /api/activity` ของคนที่มี `activity.view` แค่ระดับสาขา ตอบเฉพาะ entry ที่ `target_id` เป็นคนในสาขาเหล่านั้น audit ฟังอย่างเดียว (R5) จึงถามรายชื่อผ่าน `audit/spi/ActivityAudienceProvider` ซึ่ง `config/SecurityConfig` สร้างจาก `UserService.idsInBranches()` แบบเดียวกับ P-018
- Consequences: สิทธิ์ของ module ธุรกิจยังเป็นแบบ P-020 (`Actor.can(permission, branchId)`) การปิดสาขา (`branchesEnabled`) ไม่ได้ปิดสิทธิ์ของผู้ดูแลสาขา เพราะข้อมูลยังอยู่ ถ้าต้องการปิดให้ถอด role ที่จำกัดสาขาออก entry ของคนที่ย้ายออกจากสาขาไปแล้วจะไม่แสดงให้ผู้ดูแลสาขาเดิมเห็นอีก เพราะกรองตามสังกัดปัจจุบัน

## P-025 รหัสสำรองของ two-step sign-in: 10 รหัสใช้ครั้งเดียว เก็บเป็น hash แบบรหัสผ่าน (ต่อจาก P-008)

- Context: ก่อนหน้านี้คนที่ทำมือถือหายต้องให้ผู้ดูแล reset two-step sign-in ให้ ซึ่งเป็นงาน helpdesk ที่เกิดบ่อย Google และ GitHub ให้รหัสสำรอง (backup codes) ที่คนไทยคุ้นเคยอยู่แล้ว API.md เพิ่มหัวข้อ "Recovery codes", `GET`/`POST /me/recovery-codes` และให้ `POST /me/two-factor/confirm` ตอบ `{ user, recoveryCodes }`
- Decision:
  - ตาราง `recovery_codes` (`V10__recovery_codes.sql`: user_id FK cascade, code_hash, created_at, used_at) อยู่ใน identity คู่กับ `user_credentials` แต่ละรหัสเป็นตัวอักษรเล็กและตัวเลข 10 ตัวจาก 31 ตัว (ไม่มี 0, 1, i, l, o) แสดงเป็น `abcde-fghjk` สุ่มด้วย `SecureRandom` (ราว 49 bit) และเก็บด้วย `PasswordEncoder` (Argon2id) เหมือนรหัสผ่าน จึงแสดงได้ครั้งเดียวตอนสร้าง
  - `CredentialServiceImpl.checkTwoFactorCode` รับรหัสสำรองแทนรหัส 6 หลักได้ เฉพาะเมื่อรูปแบบไม่ใช่ตัวเลข 6 หลัก จึงไม่ต้องเรียก Argon2 กับรหัสจากแอป ใช้แล้วรหัสนั้นหมดไป รหัสผิดนับรวมใน lockout แบบรหัสผิด ใช้ได้ทุกที่ที่ถามรหัส: sign-in, `POST /me/reauthenticate` (P-023) และ `POST /me/two-factor/disable` ซึ่งตอนนี้ผ่าน `checkTwoFactorCode` ด้วย รหัสผิดจึงนับ lockout เหมือนกัน
  - เปิด two-step sign-in (`confirm`) ได้รหัสชุดแรก, `POST /me/recovery-codes` สร้างชุดใหม่แทนชุดเดิมและเป็นการเปลี่ยนแปลงเสี่ยงของ P-023 (ต้องยืนยันตัวตนซ้ำ) ส่วนการปิดหรือการ reset โดยผู้ดูแลลบรหัสทั้งหมด
  - activity ใหม่: `auth.recovery_code_used` (ผู้ใช้เป็นทั้ง actor และ target พร้อม `recoveryCodesLeft`) และ `auth.recovery_codes_created`
- Consequences: การตรวจรหัสสำรองเรียก Argon2 ได้ถึง 10 ครั้ง จึงช้ากว่ารหัสจากแอป แต่เกิดไม่บ่อยและถูกจำกัดด้วย rate limit กับ lockout ผู้ดูแลยัง reset ให้ได้เหมือนเดิมสำหรับคนที่ทำทั้งมือถือและรหัสสำรองหาย passkey (WebAuthn) ยังไม่ทำ

## P-026 เก็บ activity อย่างน้อย 90 วัน และบันทึกการเข้าสู่ระบบที่ล้มเหลวกับการล็อกบัญชี (ต่อจาก P-018)

- Context: มาตรา 26 ของ พ.ร.บ.ว่าด้วยการกระทำความผิดเกี่ยวกับคอมพิวเตอร์ และประกาศกระทรวงดิจิทัลฯ เรื่องการเก็บรักษาข้อมูลจราจรทางคอมพิวเตอร์ (2564) ให้ผู้ให้บริการเก็บข้อมูลจราจรไว้ไม่น้อยกว่า 90 วัน แต่ P-018 ให้ตั้ง `activityRetentionDays` ได้ต่ำสุด 30 วัน ส่วนผู้ตรวจสอบตาม ISO 27001 และแนวทางความเสี่ยงด้าน IT ของ ธปท. มักขอ log การเข้าใช้ที่ล้มเหลวด้วย ซึ่งก่อนหน้านี้ไม่มี API.md จึงปรับช่วงเป็น 90–3650 วัน และเพิ่ม `auth.sign_in_failed` กับ `auth.account_locked`
- Decision:
  - `UpdateSettingsRequest.activityRetentionDays` เป็น `@Min(90)` ส่วน `V11__activity_retention_minimum.sql` ปรับค่าที่ต่ำกว่า 90 ขึ้นเป็น 90 และเปลี่ยน check constraint เป็น 90–3650
  - `LoginService` เขียน `auth.sign_in_failed` (event `SignInFailed` ของ authentication) ทุกครั้งที่ปฏิเสธบัญชีที่มีอยู่จริง ทั้งตอน sign-in และ `POST /me/reauthenticate` โดยให้เจ้าของบัญชีเป็นทั้ง actor และ target และใส่ `cause` ลงใน changes ได้แก่ `password`, `code`, `locked` (บัญชีถูกล็อกอยู่ ไม่ว่ารหัสจะถูกหรือผิด), `suspended`, `invited` ส่วน IP, browser และ OS มาจาก request เหมือน entry อื่น อีเมลที่ไม่ตรงกับใครจะไม่ถูกบันทึก เพื่อไม่ให้สิ่งที่พิมพ์ผิด เช่นรหัสผ่านที่พิมพ์ลงช่องอีเมล ไปอยู่ใน log
  - `CredentialServiceImpl` เขียน `auth.account_locked` (event `AccountLocked` ของ identity) พร้อม `lockMinutes` ทุกครั้งที่ความผิดครั้งนั้นทำให้บัญชีถูกล็อก ไม่ว่าจะมาจาก sign-in, การยืนยันตัวตนซ้ำ หรือการปิด two-step sign-in และเพิ่ม `CredentialService.lockedNow()` ให้ `LoginService` แยก `locked` ออกจาก `password` ได้ โดยคำตอบของ API ยังเป็น `invalid_credentials` เหมือนเดิม
- Consequences: entry ที่ล้มเหลวส่ง live update เหมือน entry อื่น การเดารหัสจำนวนมากจึงทำให้หน้าจอของผู้ดูแลโหลดข้อมูลใหม่บ่อยขึ้น แต่ถูกจำกัดด้วย rate limit ต่อ IP และการล็อกบัญชีอยู่แล้ว ถ้าต้องตามการเดาด้วยอีเมลที่ไม่มีอยู่จริง ให้ดูจาก log ของ server หรือ WAF installation ที่เคยตั้งต่ำกว่า 90 วันจะเก็บนานขึ้นตั้งแต่คืนแรกหลังอัปเกรด

## P-027 ดูแลบัญชีตามที่ IT audit ขอ: ระงับบัญชีที่ไม่ได้ใช้, server ตัด session ที่ไม่ได้ใช้งาน และอีเมลแจ้งเตือนความปลอดภัย

- Context: ผู้ตรวจสอบ IT ในไทยมักถามถึงบัญชีที่ไม่มีคนใช้แต่ยังเปิดอยู่ และอยากให้เจ้าของบัญชีรู้ตัวเมื่อมีอะไรเกิดกับบัญชี ส่วน `sessionTimeoutMinutes` เดิมมีแต่แอปที่บังคับ (P-007) refresh cookie ใน browser ที่ทิ้งไว้จึงยังใช้ได้ API.md เพิ่ม `suspendInactiveDays`, ให้ server บังคับ idle timeout และเพิ่มหัวข้อ "Emails about your account"
- Decision:
  - `admin_settings.suspend_inactive_days` (`V12__account_hygiene.sql`: 0–3650, ค่าเริ่มต้น 0 = ปิด) และ `identity/service/InactiveAccountJob` รันทุกคืน (02:30) ระงับบัญชี active ที่ `last_active_at` (หรือ `created_at` ถ้าไม่เคยเข้าใช้) เก่ากว่าค่านั้น เขียน `user.suspended` แบบไม่มี actor พร้อม `inactiveDays` แล้วจบ session ของเขาผ่าน `UserSuspended` ข้ามคนที่ถ้าระงับแล้วจะไม่เหลือผู้ดูแลที่มีทุกสิทธิ์ (`RoleAssignmentResolver.isAdministrator`, กฎข้อ 6)
  - `last_active_at` ขยับทั้งตอน sign-in และทุกครั้งที่ refresh (`UserService.recordActivity` แทน `recordSignIn`) คนที่ใช้แอปต่อเนื่องด้วย "Keep me signed in" จึงไม่ถูกนับว่าไม่ได้ใช้
  - server ตัด session ที่ไม่มี request นานกว่า `sessionTimeoutMinutes` (บวก 1 นาทีที่ `last_seen_at` คลาดได้, `UserSession.idleAt`) ทั้งใน `SessionService.verifiedAt` (ทุก request) และ `LoginService.refresh` ค่า timeout cache ใน `idle-timeout` แบบ `reauth-window` (P-023)
  - อีเมลแจ้งเตือนส่งผ่าน `NotificationService.sendNotice` และ template `mail/notice.html` จาก `notification/service/SecurityNoticeListener` (`@TransactionalEventListener` หลัง commit) สำหรับ: sign-in จาก browser และ OS ที่บัญชีไม่เคยใช้ (event ใหม่ `NewDeviceSignedIn` ของ authentication ไม่นับการ sign-in ครั้งแรก), การได้รับ role ของคนที่ active, การเปลี่ยนอีเมล (ส่งไปที่อีเมลเดิม), การเปลี่ยนรหัสผ่าน และการถูกระงับเพราะไม่ได้ใช้ (`UserInactiveSuspended` ของ identity) notification จึงพึ่ง `UserService`, `RoleService` และ `BranchService.nameOf` เพิ่ม
- Consequences: ช่องทางอื่นอย่าง LINE Official Account เพิ่มได้ภายหลังด้วยการ implement `NotificationService` อีกตัวหรือรวมหลายช่องทาง โดยไม่ต้องแก้ listener ส่วน session ที่จบไปเกิน 30 วันถูกลบโดย `SessionCleanupJob` อุปกรณ์ที่ไม่ได้ใช้นานกว่านั้นจึงนับเป็นอุปกรณ์ใหม่อีกครั้ง ระงับอัตโนมัติจะเริ่มมีผลคืนแรกหลังตั้งค่า และการคืนสถานะทำได้ตามปกติจากหน้า Users

## P-028 ทบทวนสิทธิ์ (access review) เป็น module ใหม่ `review`: ผู้ดูแลของแต่ละคนตัดสินทีละการมอบ แล้วถอดตามผลเมื่อปิดรอบ

- Context: ผู้ตรวจสอบตาม ISO 27001 (A.5.18 สิทธิ์การเข้าถึง) และแนวทางความเสี่ยงด้าน IT ของ ธปท. ขอหลักฐานว่าองค์กรทบทวนสิทธิ์ของผู้ใช้เป็นรอบ เช่น ทุก 6 เดือนหรือปีละครั้ง หลายองค์กรยังทำด้วย Excel ที่ส่งต่อกันทางอีเมล Microsoft Entra มี access reviews ที่ให้ผู้ทบทวนตัดสินว่าแต่ละคนยังควรถือสิทธิ์อยู่หรือไม่ แล้วถอดตามผลเมื่อจบรอบ API.md เพิ่มหัวข้อ "Access reviews", permission `reviews.manage` และ code `review_closed`
- Decision:
  - module ใหม่ `review` ({controller, service, repository, entity, dto, event}) พึ่ง `identity`, `authorization`, `branch` และ `settings` ส่วน module อื่นไม่พึ่ง review นอกจาก audit และ notification ที่ฟัง event ของมัน รายชื่อ module ใน `ModuleBoundaryTest`, `contracts/dependency-rules.yaml` และ `contracts/structure.yaml` เพิ่ม `review` (แบบเดียวกับ P-020)
  - ตาราง `access_reviews` (ชื่อ, role และสาขาที่ครอบคลุม, `due_at`, สถานะ, ผู้เปิดและผู้ปิด), `access_review_reviewers` และ `access_review_items` (`V13__access_reviews.sql`) แต่ละรายการคือการมอบ role หนึ่งรายการที่ถืออยู่ตอนนี้หรือที่นัดไว้ ของคนในขอบเขตตอนเปิดรอบ (สาขาของรอบคือสาขาที่คนสังกัด `users.branch_id`) รายการเก็บชื่อ อีเมล ชื่อ role ขอบเขต ช่วงเวลา และชื่อผู้ตัดสินไว้เป็นข้อความ และไม่มี FK ไปที่ `users` หรือ `role_assignments` รายงานจึงยังอ่านได้แม้คน role หรือการมอบนั้นถูกลบไปแล้ว
  - คนที่ตัดสินรายการได้คือผู้ดูแลของเจ้าของการมอบ คือคนที่จัดการเขาได้ด้วย `users.edit` (กฎข้อ 1, 3 และ 4 จึงรวมผู้ดูแลสาขาของ P-024) และมีทุกสิทธิ์ของ role นั้นในขอบเขตของการมอบ ไม่ต้องมี `users.edit` ในขอบเขตของการมอบแบบการถอดเอง เพราะการตัดสินยังไม่เปลี่ยนอะไร ผู้จัดการสาขาจึงทบทวน role ทั้งองค์กรของพนักงานสาขาตัวเองได้ เมื่อเขามีทุกสิทธิ์ของ role นั้นทั้งองค์กร นอกจากนี้ผู้ทบทวนที่คนเปิดรอบระบุชื่อไว้ (`reviewerIds`) ตัดสินได้ทุกรายการ ไม่มีใครตัดสินรายการของตัวเอง (`self_change`) identity ตอบเรื่องนี้ผ่าน `identity/service/RoleReviewSupport` ซึ่งตรวจทีละชุด โดยอ่านคนและการมอบของเขาครั้งเดียว เพราะรอบหนึ่งอาจมีหลายพันรายการ คนที่ไม่มี `reviews.manage` และไม่ได้ถูกระบุชื่อเห็นรอบที่เปิดอยู่ซึ่งมีรายการที่เขาตัดสินได้ (เฉพาะรายการเหล่านั้น) และรอบที่ปิดแล้วซึ่งเขาเคยตัดสิน (เฉพาะรายการที่เขาตัดสิน) `GET /access-reviews` ซึ่ง app โหลดทุกครั้งที่ข้อมูลเปลี่ยน จึงไม่ต้องคำนวณรายการของรอบที่ปิดแล้ว ผู้ทบทวนคำนวณจากสิทธิ์ ณ ตอนที่ถาม ไม่ได้บันทึกไว้ตอนเปิดรอบ
  - การตัดสิน (`keep` หรือ `remove` พร้อม note) ยังไม่เปลี่ยนสิทธิ์ และเปลี่ยนได้จนปิดรอบ การตัดสิน `remove` ใช้ข้อ `removeRole` ของ `reasonsRequired` กับ note แบบเดียวกับการถอด role (P-021) จึงไม่ใช่การเปลี่ยนแปลงเสี่ยงของ P-023 แต่การปิดรอบด้วยมือเป็น (`requireRecentSignIn`)
  - การปิดรอบ (`POST /access-reviews/:id/close` หรือ `AccessReviewJob` ซึ่งรันทุกชั่วโมงและปิดรอบที่ถึง `due_at`) ถอดการมอบที่ตัดสิน `remove` ผ่าน `RoleReviewSupport.removeReviewed` ซึ่งเขียน `role_assignment.removed` แบบไม่มี actor พร้อมชื่อรอบใน `accessReview` และ note เป็นเหตุผล แต่ละรายการได้ผล (`outcome`): `kept`, `removed`, `not_reviewed` (ไม่มีใครตัดสิน จึงคงไว้ตามที่ตกลง), `gone` (การมอบหมดหรือถูกถอดไปก่อนแล้ว) หรือ `last_administrator` (ถอดแล้วจะไม่เหลือผู้ดูแลที่มีทุกสิทธิ์ตามกฎข้อ 6 จึงคงไว้แทนที่จะทำให้ปิดรอบไม่ได้)
  - permission ใหม่ `reviews.manage` ใน `CorePermissions` (เปิดรอบ ปิดรอบ ลบรอบที่ยังเปิด และเห็นทุกรายการ) มาจากการมอบแบบทั้งองค์กรเท่านั้น Administrator ได้เองตาม P-019 role อื่นไม่ได้เป็นค่าเริ่มต้น เพราะผ่านรอบทบทวนแล้วถอด role ของใครก็ได้ยกเว้นผู้ดูแลคนสุดท้าย
  - activity ชนิดใหม่: `access_review.started` (`roleId`, `branchId`, `dueAt`, `reviewers` และจำนวน `assignments`), `access_review.decided` (`decision` กับจำนวน `assignments` และ note เป็นเหตุผล), `access_review.closed` (`kept`, `removed`, `notReviewed`; ไม่มี actor เมื่อ server ปิดเอง) และ `access_review.deleted` ส่วน `ErrorCode.REVIEW_CLOSED` (409) ตอบการตัดสิน การปิด หรือการลบรอบที่ปิดแล้ว รอบที่ปิดแล้วจึงลบไม่ได้ และอยู่เป็นหลักฐาน
  - ตอนเปิดรอบ notification ส่งอีเมลให้ผู้ทบทวนที่ active แต่ละคน ว่ามีกี่รายการให้ตัดสินและครบกำหนดเมื่อไร event `AccessReviewStarted` มีรายชื่อผู้ทบทวนมาด้วย notification จึงพึ่งแค่ event ของ review
- Consequences: ยังไม่มีอีเมลเตือนก่อนครบกำหนด รอบอัตโนมัติ (เช่น ทุกไตรมาส) และการให้แต่ละคนทบทวนสิทธิ์ของตัวเองแบบ Entra ซึ่งเพิ่มได้ภายหลังโดยไม่ต้องเปลี่ยนตาราง คนที่ได้หรือเสียสิทธิ์จัดการคนระหว่างรอบจะเห็นรายการตามสิทธิ์ปัจจุบัน ถ้ารันหลาย instance `AccessReviewJob` ปิดรอบซ้ำไม่ได้ เพราะรอบที่ปิดแล้วถูกข้าม (อ่านแบบ `for update`)

## P-029 อีเมลเป็นภาษาไทยหรืออังกฤษตามภาษาที่แต่ละคนใช้ในแอป: `users.language` และ `defaultLanguage` ในการตั้งค่า

- Status: ใช้ ยกเว้น property ของเขตเวลา ซึ่งเปลี่ยนจาก `access-control.mail.time-zone` เป็น `access-control.time-zone` ตาม P-030
- Context: อีเมลทุกฉบับ (เชิญ, รีเซ็ตรหัสผ่าน, แจ้งเตือนเรื่องบัญชีของ P-027 และทบทวนสิทธิ์ของ P-028) เขียนเป็นภาษาอังกฤษไว้ในโค้ดและ template และบอกเวลาเป็น UTC ขณะที่แอปใช้ได้ทั้งไทยและอังกฤษ องค์กรไทยส่วนใหญ่อยากได้อีเมลภาษาไทยที่บอกเวลาไทยและปี พ.ศ. ส่วนพนักงานต่างชาติอยากได้ภาษาอังกฤษ Google และ Microsoft ส่งอีเมลตามภาษาที่ผู้ใช้ตั้งไว้ API.md เพิ่ม `User.language`, `defaultLanguage` ในการตั้งค่า และหัวข้อ "The language of emails"
- Decision:
  - `users.language` (`V14__email_language.sql`: `th`, `en` หรือ null) คือภาษาที่คนนั้นใช้แอปล่าสุด แอปบันทึกให้เองผ่าน `PATCH /users/:id` เมื่อเข้าสู่ระบบหรือสลับภาษา คนนั้นแก้ของตัวเองได้โดยไม่ต้องมีสิทธิ์ (แบบชื่อและเบอร์โทร) และคนที่มี `users.edit` ตั้งให้คนที่ตัวเองจัดการได้ รวมถึงตอนเพิ่มผู้ใช้ใหม่ ซึ่งอีเมลเชิญจะใช้ภาษานั้น การเปลี่ยนภาษาไม่ลง activity log เพราะเป็นความชอบส่วนตัวแบบธีม ไม่เกี่ยวกับการเข้าถึง และ `PATCH /users/:id` ที่ไม่ได้เปลี่ยนสิ่งที่ log ติดตามจะไม่เขียน `user.updated` อีกต่อไป
  - `admin_settings.default_language` (ค่าเริ่มต้น `th`) คือภาษาของอีเมลถึงคนที่ยังไม่มี `language` เช่นคนที่เพิ่งถูกเชิญ
  - ข้อความอยู่ใน `i18n/mail.properties` (อังกฤษ) และ `i18n/mail_th.properties` ผ่าน `MessageSource` ของ Spring (`spring.messages.*`: format ทุกข้อความด้วย MessageFormat และไม่หันไปใช้ locale ของเครื่อง server) template ทั้งสามใช้ `#{...}` `NotificationService` จึงรับผู้รับพร้อมภาษา (`Recipient`) ชนิดของอีเมล (`kind`) และค่าที่ใส่ในข้อความ แทนข้อความที่เขียนเสร็จแล้ว แล้ว `MailNotificationService` เขียนเป็นภาษาของผู้รับ ช่องทางอื่นอย่าง LINE ใช้ข้อความชุดเดียวกันได้ `notification/service/RecipientResolver` หาภาษาของแต่ละคนจาก `UserService` และ `SettingsService` event `AccessReviewStarted.Reviewer` จึงพกแค่ id ของผู้ทบทวนกับจำนวน
  - เวลาในอีเมลเป็นเขตเวลาของ `access-control.mail.time-zone` (ค่าเริ่มต้น `Asia/Bangkok`) พร้อมบอกว่าเป็น GMT เท่าไร ภาษาไทยใช้ปี พ.ศ. (`ThaiBuddhistChronology`) และชื่อเดือนแบบย่อภาษาไทย
- Consequences: เพิ่มภาษาที่สามได้ด้วยไฟล์ `mail_<code>.properties` ไฟล์แปลของแอป และขยาย check constraint กับ `@Pattern` ของ `language` เทสของ server ไม่ตรวจข้อความในอีเมลแล้ว แต่ตรวจชนิด ค่า และภาษาของผู้รับ ส่วน `MailNotificationServiceTest` ตรวจอีเมลที่เขียนออกมาทั้งสองภาษา และ `MailMessagesTest` ตรวจว่าไฟล์ทั้งสองภาษามี key ตรงกัน

## P-030 เขตเวลาขององค์กร `access-control.time-zone`: ใช้ทั้งเวลาในอีเมลและเวลาที่งาน cron รัน (เปลี่ยน P-029 บางส่วน)

- Context: `@Scheduled(cron = …)` ใช้เขตเวลาของ JVM ซึ่งใน container (`eclipse-temurin` ของ `docker/Dockerfile`) คือ UTC งานที่ตั้งใจให้รันตอนกลางคืนตามเวลาไทยจึงไปรันตอนสาย เช่น ระงับบัญชีที่ไม่ได้ใช้ (`InactiveAccountJob` 02:30) ไปรัน 09:30, ล้าง session (`SessionCleanupJob` 03:15) ไปรัน 10:15 และลบ activity เก่า (`AuditRetentionJob` 03:30) ไปรัน 10:30 ทั้งหมดอยู่ในเวลางาน ส่วน `access-control.mail.time-zone` ของ P-029 ใช้กับเวลาในอีเมลเท่านั้น
- Decision:
  - `access-control.time-zone` (ค่าเริ่มต้น `Asia/Bangkok`, ตั้งผ่าน env `AC_TIME_ZONE`) คือเขตเวลาขององค์กร ใช้ทั้งเวลาในอีเมลและเวลาที่งาน cron รัน แทน `access-control.mail.time-zone`
  - งาน cron ทุกตัวต้องประกาศ `zone = AccessControlProperties.TIME_ZONE` (`${access-control.time-zone:Asia/Bangkok}`) กฎ J1 ใน `contracts/dependency-rules.yaml` (`SchedulingRuleTest`) ทำให้ build ไม่ผ่านถ้ามีงาน cron ตัวไหนไม่ได้ประกาศ ส่วนงานที่รันตามช่วงเวลา (`fixedDelay`, `fixedRate`) ไม่ขึ้นกับเขตเวลา
  - ไม่เปลี่ยนเขตเวลาของ JVM เพราะเวลาในฐานข้อมูลเป็น `timestamptz` และ log มี offset กำกับอยู่แล้ว
- Consequences: เปลี่ยน `AC_TIME_ZONE` แล้วทั้งเวลาในอีเมลและชั่วโมงที่งานกลางคืนรันจะเปลี่ยนตาม เขตเวลาที่มี daylight saving อาจทำให้งานที่ตั้งไว้ในชั่วโมงที่เปลี่ยนเวลาถูกข้ามหรือรันซ้ำในคืนนั้น (`Asia/Bangkok` ไม่มี daylight saving) เขตเวลานี้กำหนดแค่ว่างานรันตอนไหน ส่วนระยะเวลาที่นับ (เช่น ไม่ได้ใช้ 90 วัน หรือเก็บ activity 365 วัน) ยังนับจาก `Instant` เหมือนเดิม

## P-031 การเปลี่ยนสิ่งที่ใช้กู้บัญชีต้องยืนยันตัวตนซ้ำ: อีเมล และแอป authenticator (ต่อจาก P-023 และ P-016)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V7.5.1, V6.3.4) พบว่า `PATCH /users/:id` เปลี่ยนอีเมลได้โดยไม่ผ่าน `requireRecentSignIn` และเมื่อเปิด two-step sign-in อยู่แล้ว `POST /me/two-factor` ตามด้วย `/confirm` ยังตั้ง secret ใหม่ทับของเดิมพร้อมออกรหัสสำรองชุดใหม่ได้โดยไม่ต้องใช้รหัสเดิม คนที่ได้ access token ไปจึงเปลี่ยนอีเมล ขอลิงก์รีเซ็ตรหัสผ่าน แล้วผูก authenticator ของตัวเอง เป็นการยึดบัญชีถาวร และเป็นทางอ้อม P-023 ที่ `POST /me/recovery-codes` บังคับไว้
- Decision:
  - `UserLifecycleService.update` เรียก `actor.requireRecentSignIn()` เมื่ออีเมลเปลี่ยน หลังตรวจว่าอีเมลว่างและอยู่ในโดเมนที่อนุญาต การแก้ field อื่น (ชื่อ เบอร์โทร สถานะ) ไม่ถาม
  - `TwoFactorService.start` ตอบ `invalid_request` เมื่อ two-step sign-in เปิดอยู่ จะเปลี่ยนแอป authenticator ต้องปิดก่อนด้วยรหัส (`POST /me/two-factor/disable`) แล้วจึงเปิดใหม่ ส่วนการเปิดจากสถานะปิดต้องยืนยันตัวตนซ้ำ เพราะ token ที่ถูกขโมยไปก็ผูกบัญชีของเหยื่อกับ authenticator ของผู้โจมตีได้เหมือนกัน `confirm` ปฏิเสธด้วย `invalid_request` เช่นกันเมื่อเปิดอยู่แล้ว
  - `TwoFactorSetupFilter` ให้ `POST /api/me/reauthenticate` ผ่านระหว่างที่ role บังคับ two-step sign-in (เพิ่มจากรายการของ P-016) คนที่เข้าสู่ระบบนานกว่า `reauthMinutes` แล้วจึงยืนยันรหัสผ่านก่อนเปิดได้ ไม่ติดค้าง
- Consequences: รายการ "Confirming who you are" ใน API.md มี `PATCH /users/:id` เมื่อ `email` เปลี่ยน และ `POST /me/two-factor` เพิ่มขึ้น หน้าบ้านทั้งสองแอปไม่ต้องแก้ เพราะคำขอทั้งสองผ่าน `AdminStore.call` ที่ถามรหัสผ่านแล้วส่งซ้ำเมื่อได้ `reauth_required` อยู่แล้ว `LocalAdminApi` ของทั้งสองแอปตรวจแบบเดียวกัน

## P-032 การตรวจรหัสผ่านและรหัสหลังเข้าสู่ระบบนับรวมใน lockout และมี rate limit (ต่อจาก P-025 และ P-026)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V6.3.1, V2.4.1) พบว่า `POST /me/two-factor/disable` นับรหัสผิดผ่าน `checkTwoFactorCode` ตามที่ P-025 บอก แต่ `TwoFactorService` มี `@Transactional` ที่ไม่มี `noRollbackFor` เมื่อรหัสผิดและโยน `INVALID_CODE` ตัวนับที่เพิ่มไว้จึงถูก rollback ไปด้วย, `checkTwoFactorCode` ไม่ตรวจว่าบัญชีถูกล็อก, `POST /me/password` ไม่นับรหัสผ่านปัจจุบันที่ผิดเลย และทั้งสอง endpoint ไม่มี rate limit คนที่ได้ access token ไปจึงสุ่มรหัสหกหลักจนปิด two-step sign-in ได้ และสุ่มรหัสผ่านเดิมได้เรื่อยๆ
- Decision:
  - `TwoFactorService.disable` และ `CredentialServiceImpl.changeOwnPassword` ใช้ `@Transactional(noRollbackFor = AccessControlException.class)` แบบเดียวกับ `LoginService` การใส่ผิดจึงถูกบันทึกพร้อมคำตอบที่ปฏิเสธ
  - `changeOwnPassword` ตรวจรหัสเดิมผ่าน `checkPassword` ซึ่งนับการใส่ผิดและไม่ตรวจรหัสระหว่างล็อก `checkTwoFactorCode` ไม่ตรวจรหัสระหว่างล็อกเช่นกัน
  - `RateLimitFilter` จำกัด `/api/me/password` และ `/api/me/two-factor/disable` ต่อ IP ด้วยค่าเดียวกับการเข้าสู่ระบบ (`access-control.rate-limit.login`)
- Consequences: การใส่ผิดห้าครั้งที่สอง endpoint นี้ล็อกบัญชีเหมือนการเข้าสู่ระบบ และ `auth.account_locked` ถูกบันทึก (`FailedSignInsIT`) เจ้าของบัญชีที่ใส่รหัสเดิมผิดเองก็ถูกล็อกด้วย ซึ่งเป็นพฤติกรรมเดียวกับหน้าเข้าสู่ระบบ

## P-033 รหัสผ่านของผู้ดูแลคนแรกต้องตั้งเอง ระบบไม่สุ่มแล้วเขียนลง log

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V6.4.1, V14.2.4, V16.2.5) พบว่าเมื่อไม่ได้ตั้ง `access-control.bootstrap.admin-password` `AdminAccountInitializer` สุ่มรหัสผ่านให้ผู้ดูแลคนแรกแล้วเขียนลง log ระดับ WARN เป็นข้อความเปิด รหัสนี้ไม่หมดอายุ และ production เก็บ log แบบ ECS ส่งต่อไปยังระบบรวม log ใครที่อ่าน log ได้จึงได้บัญชีที่มีทุกสิทธิ์ ส่วนรหัสที่ตั้งเองก็ไม่ผ่านกฎรหัสผ่าน
- Decision:
  - เมื่อยังไม่มีผู้ใช้เลย `access-control.bootstrap.admin-password` (`ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD`) ต้องมีค่า และยาวอย่างน้อย `passwordMinLength` ของการตั้งค่า ไม่อย่างนั้นแอปไม่ start และบอกว่าต้องตั้งอะไร (`AdminAccountInitializer.passwordFor`) ข้อความ error ไม่มีรหัสผ่าน
  - ไม่สุ่มรหัสผ่านอีก เลือกแบบนี้แทนการส่งคำเชิญทางอีเมล เพราะคำเชิญที่หมดอายุก่อนผู้ดูแลคนแรกกดจะไม่มีใครส่งใหม่ให้ได้
  - เมื่อมีผู้ใช้แล้ว ค่านี้ไม่ถูกใช้ เปลี่ยนรหัสผ่านต่อจากนั้นทำในแอปตามปกติ
- Consequences: การ start ครั้งแรกโดยไม่ตั้งตัวแปรนี้จะล้มเหลวแทนที่จะได้รหัสสุ่ม README บอกให้ตั้งอยู่แล้ว (`ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD=change-me-123 ./mvnw spring-boot:run`) ผู้ดูแลระบบควรเปลี่ยนรหัสนี้หลังเข้าสู่ระบบครั้งแรก และลบตัวแปรออกจากการ deploy เมื่อไม่ใช้แล้ว

## P-034 image ของ docker รันด้วย profile `prod` เสมอ และ profile `prod` ไม่ยอมใช้กุญแจเข้ารหัสของโหมดพัฒนา (ต่อจาก P-014)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V13.3.1, V15.2.3) พบว่า `docker/Dockerfile` ไม่ได้ตั้ง profile ไว้ในตัว มีแค่คอมเมนต์บอกให้ส่ง `-e SPRING_PROFILES_ACTIVE=prod` ตอนรัน ถ้าลืม แอปจะรันด้วย `application.yml` ของโหมดพัฒนา ซึ่งมีกุญแจเข้ารหัส secret ของ two-step sign-in ที่เผยแพร่อยู่ใน repo, cookie ที่ไม่ Secure และ JWT key ที่สุ่มใหม่ทุกครั้งที่ start โดยไม่มีอะไรหยุดไว้
- Decision:
  - `docker/Dockerfile` ตั้ง `ENV SPRING_PROFILES_ACTIVE=prod` ใน image ที่ใช้รัน
  - `EncryptionConfig.check` ไม่ยอม start ใน profile `prod` เมื่อ password หรือ salt ตรงกับค่าของโหมดพัฒนาใน `application.yml` และเตือนใน log ทุกครั้งที่ใช้ค่านั้นนอก `prod`
- Consequences: deploy ด้วย image นี้ได้ค่าของ production เสมอ ถ้าขาดตัวแปรที่ `application-prod.yml` ต้องการ แอปจะไม่ start แทนที่จะรันด้วยค่าตัวอย่าง ส่วนการรัน jar ตรงๆ ยังต้องใส่ profile เอง และจะเห็นคำเตือนใน log ถ้าลืม

## P-035 smart lockout: นับการใส่ผิดแยกระหว่างที่ที่เคยเข้าสู่ระบบกับที่อื่น และเริ่มนับใหม่เมื่อเงียบไปหนึ่งวัน (ต่อจาก P-026)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V6.1.1) พบว่า lockout นับต่อบัญชีอย่างเดียว และ `failedAttempts` กลับเป็นศูนย์เฉพาะเมื่อเข้าสู่ระบบสำเร็จหรือเปลี่ยนรหัสผ่าน เมื่อเกินเกณฑ์แล้ว การใส่ผิดทุกครั้งจะล็อกใหม่นานสุด 30 นาที คนที่รู้แค่อีเมลจึงใส่ผิดครั้งเดียวทุก 30 นาทีจาก IP ใดก็ได้เพื่อล็อกบัญชีของคนอื่นไว้ตลอด รวมถึงผู้ดูแลระบบ
- Decision:
  - `user_credentials` นับสองชุด (`V15__familiar_lockout.sql`): ชุด familiar สำหรับ address ที่คนนั้นมี session อยู่ (session ถูกเก็บไว้ 30 วันหลังปิด) และชุดเดิมสำหรับ address อื่น `LoginService` เป็นคนตัดสินว่า address คุ้นเคยหรือไม่จาก `UserSessionRepository.existsByUserIdAndIp` แล้วส่งให้ `CredentialService` การตรวจหลังเข้าสู่ระบบแล้ว (`/me/reauthenticate`, `/me/password`, `/me/two-factor/disable`) นับในชุด familiar แบบเดียวกับ smart lockout ของ Microsoft Entra
  - การนับแต่ละชุดเริ่มใหม่เมื่อการใส่ผิดครั้งล่าสุดเก่ากว่า `access-control.lockout.reset-after` (ค่าเริ่มต้น 24 ชั่วโมง) การเข้าสู่ระบบสำเร็จล้างเฉพาะชุดของที่นั้น การเปลี่ยนรหัสผ่านล้างทั้งสองชุด
- Consequences: คนแปลกหน้าที่เดารหัสจากที่อื่นล็อกได้แค่ address ของตัวเอง เจ้าของบัญชียังเข้าจากที่ที่เคยใช้ได้ คนที่ย้ายมาเข้าจาก address ใหม่ระหว่างที่ถูกล็อกจาก address แปลกหน้าจะเข้าไม่ได้จนกว่าล็อกหมดอายุ ซึ่งตรงกับพฤติกรรมเดิม การตัดสินว่าคุ้นเคยขึ้นกับ IP ที่ถูกต้อง จึงต้องระบุ proxy ที่เชื่อถือได้ให้ถูก (รายการปรับปรุงระยะที่ 2 เรื่อง X-Forwarded-For)

## P-036 รหัสจากแอปและรหัสกู้คืนใช้ได้ครั้งเดียว แม้มาพร้อมกัน (ต่อจาก P-008 และ P-025)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V6.5.1) พบว่ารหัสจากแอปที่ถูกต้องใช้ซ้ำได้ตลอดช่วงที่ยอมรับ (ประมาณ 90 วินาที) คนที่แอบมองหรือดักรหัสได้จึงเข้าสู่ระบบด้วยรหัสเดียวกันได้อีก และรหัสกู้คืนถูกทำเครื่องหมายว่าใช้แล้วผ่าน entity ที่อ่านมาก่อน request สองตัวที่มาพร้อมกันจึงอ่านว่ายังไม่ใช้ทั้งคู่และผ่านทั้งคู่
- Decision:
  - `user_credentials.last_totp_step` (`V16__totp_used_once.sql`) เก็บช่วง 30 วินาทีของรหัสล่าสุดที่รับไว้ `TotpService.matchedStep` บอกว่ารหัสตรงกับช่วงไหน (ยังเทียบทุกช่วงแบบ constant time) แล้ว `UserCredentialRepository.useTotpStep` รับช่วงนั้นด้วย `update ... where last_totp_step is null or last_totp_step < :step` ได้ 0 แถวแปลว่าใช้ไปแล้ว ตอบ `invalid_code` และนับเป็นรหัสผิด การเปิด 2 ขั้นตอนเก็บช่วงของรหัสที่ใช้ยืนยันไว้ด้วย การปิดล้างค่าทิ้ง
  - `RecoveryCodeRepository.useUp` ทำเครื่องหมายด้วย `update ... where used_at is null` ก่อนตอบว่าผ่าน ฐานข้อมูลให้ผ่านได้เพียง request เดียว
- Consequences: เข้าสู่ระบบสองครั้งภายในช่วง 30 วินาทีเดียวกันต้องรอรหัสถัดไป ซึ่งเป็นพฤติกรรมเดียวกับ GitHub และ Google ข้อมูลตัวอย่างในเบราว์เซอร์ (`LocalAdminApi`) ไม่ได้ทำตามข้อนี้ เช่นเดียวกับ lockout เพราะไม่ใช่ขอบเขตความปลอดภัย

## P-037 session มีอายุนับจากตอนเข้าสู่ระบบ และการติดตั้งใหม่ออกจากระบบเมื่อไม่ได้ใช้งาน 30 นาที (ต่อจาก P-027)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V7.3.1, V7.3.2) พบว่า `LoginService.refresh` เลื่อน `expiresAt` ของ session ออกไปอีก 12 ชั่วโมงหรือ 30 วันทุกครั้งที่ refresh session ที่ refresh สม่ำเสมอจึงไม่หมดอายุเลย ส่วน `sessionTimeoutMinutes` เริ่มต้นที่ 0 คือไม่ออกจากระบบเมื่อไม่ได้ใช้งาน cookie หรือ token ที่รั่วไปจึงใช้ได้ไปเรื่อยๆ
- Decision:
  - `expiresAt` ของ session ตั้งครั้งเดียวตอนเข้าสู่ระบบ คือ `access-control.token.refresh-ttl` (12 ชั่วโมง) หรือ `remember-ttl` (30 วัน) การ refresh ไม่เลื่อนมัน refresh token ใหม่หมดอายุพร้อม session และ cookie แบบ remember มี Max-Age เท่าเวลาที่เหลือ 12 ชั่วโมงตรงกับที่ NIST SP 800-63B (rev. 3, 4.2.3) กำหนดให้ AAL2 ยืนยันตัวตนใหม่
  - การติดตั้งใหม่ตั้ง `sessionTimeoutMinutes` เป็น 30 (`SettingsInitializer` และ `DEFAULT_SETTINGS` ใน `catalog.ts` ของทั้งสองแอป) ตาม idle timeout ของ AAL2 ใน NIST SP 800-63B การติดตั้งเดิมคงค่าที่ผู้ดูแลตั้งไว้
- Consequences: คนที่ทำงานต่อเนื่องเกิน 12 ชั่วโมงต้องเข้าสู่ระบบใหม่ และคนที่เลือก remember ต้องเข้าสู่ระบบใหม่ทุก 30 วัน ไม่มี migration เพราะ `createdAt` และ `expiresAt` มีอยู่แล้ว session ที่เปิดอยู่ตอนอัปเกรดจบตาม `expiresAt` ล่าสุดที่ถูกเลื่อนไว้

## P-038 header ป้องกันของเบราว์เซอร์: API ส่ง CSP ที่ไม่อนุญาตอะไรเลย และหน้าเว็บมี config ของ web server ที่ส่ง CSP ของตัวเอง

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V3.4.1, V3.4.3 ถึง V3.4.6) พบว่าทั้ง API และหน้าเว็บทั้งสองไม่ส่ง Content-Security-Policy ไม่มี frame-ancestors และไม่มี Referrer-Policy ถ้ามีช่อง XSS ที่ใดก็ไม่มีชั้นกันที่สอง หน้าเว็บถูกฝังใน iframe เพื่อหลอกให้กดได้ และ token ในลิงก์อีเมลอาจรั่วไปกับ Referer
- Decision:
  - API (`SecurityConfig`) ส่ง `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` และ `Referrer-Policy: no-referrer` เพิ่มจากที่ Spring Security ส่งอยู่แล้ว (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store` และ HSTS เมื่อคำขอมาทาง HTTPS ซึ่งหลัง proxy ต้องตั้ง `X-Forwarded-Proto` ให้ถูก) API ตอบแค่ JSON กับ event stream จึงไม่มีอะไรต้องอนุญาต
  - หน้าเว็บทั้งสองมี `deploy/nginx.conf` ที่เสิร์ฟ build และส่ง CSP ของหน้าเว็บ (`script-src 'self'` ไม่มี inline script: script เลือกธีมก่อนแสดงผลย้ายไปเป็นไฟล์ `public/theme.js` และ admin-ng-starter ปิด `inlineCritical` ใน `angular.json` เพราะต้องใช้ inline script), `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `nosniff` และ HSTS ทดสอบโดยรัน e2e ทั้งชุดของทั้งสองแอปกับ demo build ที่ nginx เสิร์ฟด้วย config นี้ ผ่านครบโดยไม่มีรายงานการละเมิด CSP
- Consequences: ถ้าวาง API กับหน้าเว็บคนละ origin ต้องเพิ่ม origin ของ API ใน `connect-src` ของหน้าเว็บ hosting อื่นที่ไม่ใช่ nginx ต้องตั้ง header ชุดเดียวกันเอง

## P-039 จำกัดขนาด body ก่อนอ่าน และรูปโปรไฟล์ต้องเป็นรูปภาพใน data URL เท่านั้น

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V5.2.1, V5.2.2, V2.2.1, V1.2.2, V1.3.4) พบว่าไม่มีเพดานขนาด body ก่อนที่ Jackson จะอ่าน และการอ่านกับ `@Valid` ทำก่อน `@PreAuthorize` `POST /auth/login` จึงรับ body ขนาดเท่าไรก็ได้โดยไม่ต้องเข้าสู่ระบบ และ `POST /users/import` รับได้ถึงราว 2 GB (1,000 แถว แถวละรูป 2 ล้านตัวอักษร) ส่วน `avatar` ตรวจแค่ความยาว จึงเป็นลิงก์ภายนอก (เบราว์เซอร์ของทุกคนที่เปิดดูจะไปโหลดและเผย IP) หรือ SVG ได้ และ `PATCH /users/:id` ยอมให้ชื่อและแผนกว่าง
- Decision:
  - `RequestSizeFilter` อยู่ต่อจาก `RequestContextFilter` ปฏิเสธ body ที่ใหญ่เกิน `access-control.requests.max-body` (1 MB) ด้วย `400 invalid_request` ทันทีจาก Content-Length และสำหรับ body แบบ chunked ที่ไม่บอกความยาว จะล้มเมื่ออ่านเกิน ยกเว้น `POST /users/import` ที่ได้ `max-import-body` (5 MB)
  - `@Avatar` รับค่าว่าง (ไม่มีรูป) หรือ data URL แบบ base64 ของ `image/jpeg`, `image/png` หรือ `image/webp` ยาวไม่เกิน 100,000 ตัวอักษร ที่ถอด base64 ได้ และ byte แรกตรงกับชนิดที่บอก แอปทั้งสองส่ง JPEG ขนาด 160 pixel ซึ่งเล็กกว่านั้นมาก
  - `firstName`, `lastName` และ `department` ใน `PATCH /users/:id` ละไว้ได้ แต่ส่งเป็นค่าว่างไม่ได้ เหมือนตอนสร้าง
- Consequences: ผู้ใช้เดิมที่มีรูปเป็นลิงก์หรือใหญ่เกินยังเก็บไว้ได้จนกว่าจะเปลี่ยน หน้าเว็บทั้งสองตรวจขนาดไฟล์ CSV ก่อนอ่าน และตรวจรูปแบบรูปโปรไฟล์เหมือนกันในข้อมูลตัวอย่าง

## P-040 เชื่อ `X-Forwarded-For` เฉพาะจาก reverse proxy ที่ระบุใน `AC_TRUSTED_PROXIES`

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V4.1.3) พบว่า `application-prod.yml` ใช้ `forward-headers-strategy: native` แต่ไม่ได้ระบุ `server.tomcat.remoteip.internal-proxies` Tomcat จึงใช้ค่าเริ่มต้นที่เชื่อทุก address ในช่วง private เครื่องในเครือข่ายภายในที่เข้าถึงพอร์ตของแอปได้ตรงๆ จึงส่ง `X-Forwarded-For` ปลอมเพื่อหลบ rate limit ต่อ IP ทำให้ lockout แบบ smart lockout (P-035) เห็นเป็น address คุ้นเคย และทำให้ IP ใน activity log กับรายการอุปกรณ์เชื่อไม่ได้
- Decision: `server.tomcat.remoteip.internal-proxies` มาจาก `AC_TRUSTED_PROXIES` ซึ่งไม่มีค่าเริ่มต้น ถ้าไม่ตั้งแอปไม่ start (`ProductionConfigTest` กันไม่ให้ค่านี้หายไปจาก `application-prod.yml`) OPERATIONS.md บอกว่า proxy ต้องเขียนทับ `X-Forwarded-For` และพอร์ต 8080 ต้องเข้าถึงได้จาก proxy เท่านั้น `deploy/nginx.conf` ของหน้าเว็บทั้งสองทำแบบนั้นอยู่แล้ว
- Consequences: การติดตั้งเดิมต้องเพิ่ม `AC_TRUSTED_PROXIES` ก่อนอัปเกรด ถ้าตั้งผิด (proxy ไม่อยู่ในรายการ) ทุกคำขอจะดูเหมือนมาจาก IP ของ proxy rate limit จะนับรวมทุกคน ให้ตรวจจาก IP ในรายการอุปกรณ์หลังอัปเกรด

## P-041 ต่อฐานข้อมูลและเซิร์ฟเวอร์อีเมลผ่าน TLS ที่ตรวจใบรับรองเท่านั้น

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V12.3.1, V12.3.2) พบว่า SMTP เปิด `starttls.enable` แต่ไม่ได้ตั้ง `starttls.required` เซิร์ฟเวอร์ (หรือใครก็ตามที่อยู่ระหว่างทาง) ที่ไม่เสนอ STARTTLS จึงได้ลิงก์คำเชิญและลิงก์รีเซ็ตรหัสผ่านแบบไม่เข้ารหัส ส่วนฐานข้อมูลไม่มีอะไรบังคับ `sslmode` ค่าเริ่มต้นของ driver (`prefer`) ตกไปเป็นแบบไม่เข้ารหัสได้เงียบๆ และไม่ตรวจใบรับรอง
- Decision:
  - `application-prod.yml` ตั้ง `mail.smtp.starttls.required: true` และ `mail.smtp.ssl.checkserveridentity: true` ส่งไม่ได้ดีกว่าส่งแบบไม่เข้ารหัส (`ProductionConfigTest` กันไม่ให้ค่าเหล่านี้หายไป)
  - `DatabaseTlsConfig` ทำให้ profile `prod` ไม่ start ถ้า `spring.datasource.url` ไม่มี `sslmode=verify-full` ซึ่งเข้ารหัสและตรวจทั้งใบรับรองและชื่อเครื่อง โหมดพัฒนาใช้ฐานข้อมูลใน docker บนเครื่องตัวเองได้ตามเดิม
- Consequences: การติดตั้งเดิมต้องเพิ่ม `sslmode=verify-full` ใน `DB_URL` และเปิด TLS ที่ PostgreSQL ก่อนอัปเกรด ถ้าใบรับรองออกโดย CA ภายใน ต้องให้ `sslrootcert` ชี้ไปที่ใบรับรองของ CA นั้น เซิร์ฟเวอร์อีเมลที่ไม่รองรับ STARTTLS จะส่งไม่ได้และ log `Could not send …` (OPERATIONS.md)

## P-042 การติดตั้งใหม่บังคับ two-step sign-in กับบทบาท Administrator (ต่อจาก P-016)

- Context: การตรวจความปลอดภัยตาม OWASP ASVS 5.0 (V6.3.3) พบว่าการตั้งค่าบังคับ two-step sign-in ต่อบทบาทได้ (P-016) แต่ `twoFactorRoleIds` ของการติดตั้งใหม่ว่าง บทบาท Administrator ซึ่งมีทุกสิทธิ์จึงเข้าสู่ระบบด้วยรหัสผ่านอย่างเดียว รหัสผ่านของผู้ดูแลรั่วครั้งเดียวก็ยึดทั้งระบบได้
- Decision: `SettingsInitializer` ใส่บทบาทจาก `access-control.bootstrap.two-factor-roles` (ค่าเริ่มต้น `ADMINISTRATOR`) ใน `twoFactorRoleIds` ของการติดตั้งใหม่ ผู้ดูแลคนแรกจึงถูก `TwoFactorSetupFilter` ให้เปิด two-step sign-in ตอนเข้าสู่ระบบครั้งแรกก่อนทำอย่างอื่น ผู้ดูแลเปลี่ยนรายการนี้ได้ในหน้าตั้งค่าเหมือนเดิม integration test ตั้งค่านี้เป็นว่างเพื่อเข้าสู่ระบบด้วยรหัสผ่านอย่างเดียว และ `SettingsInitializerTest` ตรวจค่าเริ่มต้น
- Consequences: การติดตั้งเดิมไม่เปลี่ยน (ให้ผู้ดูแลเพิ่ม Administrator ในหน้าตั้งค่าเอง) ข้อมูลตัวอย่างในหน้าเว็บทั้งสอง (`DEFAULT_SETTINGS` ใน `catalog.ts`) ยังว่าง เพราะบัญชีตัวอย่างเข้าสู่ระบบด้วยการกดครั้งเดียวเพื่อสาธิต ไม่ใช่การติดตั้งจริง
