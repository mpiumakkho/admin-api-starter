# admin-api-starter

Backend ของ [admin-ng-starter](../admin-ng-starter) สำหรับจัดการผู้ใช้ บทบาท สิทธิ์ การเข้าสู่ระบบ (JWT + 2FA) ประวัติการใช้งาน และการตั้งค่า ทำครบทุก endpoint ใน [`API.md`](../admin-ng-starter/src/app/user-management/API.md) ที่หน้าจอใช้

- **สัญญา API:** [`API.md`](https://github.com/mpiumakkho/admin-ng-starter/blob/main/src/app/user-management/API.md) ใน admin-ng-starter (ในเครื่อง: [`../admin-ng-starter/src/app/user-management/API.md`](../admin-ng-starter/src/app/user-management/API.md)) ส่วน endpoint ไหนของสัญญาที่ server นี้ทำแล้ว ตรวจสิทธิ์อะไร และมี test ไหนเรียก ดูที่ [docs/API-COVERAGE.md](docs/API-COVERAGE.md)
- **Stack:** Java 25, Spring Boot 4.1, Spring Security 7 (OAuth2 resource server + Nimbus), Spring Data JPA (Hibernate 7), PostgreSQL 17, Flyway, Caffeine, Bucket4j, Argon2id, Thymeleaf (อีเมล), ArchUnit, Testcontainers
- **Template:** access-control (`accessbase-docs`, schema_version 1) org `adminstarter` ส่วนที่ต่างจาก template มี ADR อยู่ใน [docs/02-DECISIONS.md](docs/02-DECISIONS.md)
- **โครงสร้าง:** [trees/BACKEND.md](trees/BACKEND.md)

## สิ่งที่ต้องมี

- JDK 25
- Docker (สำหรับ PostgreSQL กับ Mailpit ตอนพัฒนา และสำหรับ integration test)

## รัน

```bash
docker compose -f docker/docker-compose.yml up -d     # postgres:17 ที่ port 5433, mailpit ที่ 1025/8025
ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD=change-me-123 ./mvnw spring-boot:run
```

- API อยู่ที่ `http://localhost:8080/api`
- การ start ครั้งแรกจะสร้าง schema, permission 10 ตัว, role 4 ตัว (Administrator, Manager, Support, Viewer) และผู้ดูแลคนแรก `admin@example.com` โดยใช้รหัสจาก `ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD` ถ้าไม่ได้ตั้งไว้ ระบบจะสุ่มรหัสให้และพิมพ์ลง log ครั้งเดียว
- อีเมลเชิญและอีเมลรีเซ็ตรหัสผ่านทั้งหมดไปอยู่ใน Mailpit: http://localhost:8025

### ใช้กับ admin-ng-starter

ใน `admin-ng-starter/src/app/app.config.ts`:

```ts
provideUserManagement({ api: HttpAdminApi, apiUrl: '/api', liveUpdates: true }),
```

จากนั้นรัน `npm start` ซึ่ง `proxy.conf.json` จะส่งต่อ `/api/…` ไปที่ `http://localhost:8080/api/…` ทำให้ cookie เป็นแบบ same-origin และไม่ต้องตั้ง CORS

## การเข้าสู่ระบบโดยย่อ

| | |
|---|---|
| Access token | JWT RS256 อายุ 15 นาที อยู่ใน body ของคำตอบ ฝั่ง Angular เก็บใน memory เท่านั้น claim มีแค่ `sub` (user) กับ `sid` (session) ไม่มีสิทธิ์อยู่ใน token |
| Refresh cookie | ค่าสุ่ม 256 bit เก็บใน DB เป็น SHA-256 ตั้งเป็น `HttpOnly; SameSite=Strict; Path=/api/auth` และ `Secure` ใน production หมุนใหม่ทุกครั้งที่ refresh ถ้า cookie เก่าถูกใช้ซ้ำเกิน 30 วินาที ถือว่าถูกขโมยและจบ session |
| ทุก request | ตรวจว่า session ยังเปิดอยู่และบัญชียัง active แล้วจึงดึงสิทธิ์ (cache 60 วินาที โดย evict หลัง commit) การระงับผู้ใช้หรือการจบ session จึงมีผลทันที |
| ป้องกันการเดา | ใส่รหัสผิด 5 ครั้งติดกันจะถูกล็อก 1 นาที และเพิ่มเป็นสองเท่าทุกครั้งจนถึง 30 นาที endpoint `/api/auth/*` มี rate limit ต่อ IP |
| ดูแลบัญชีตามที่ IT audit ขอ | ระงับบัญชีที่ไม่มีใครเข้าใช้นานกว่า `suspendInactiveDays` ทุกคืน (ปิดเป็นค่าเริ่มต้น และไม่ระงับผู้ดูแลสูงสุดคนสุดท้าย), server ตัด session ที่ไม่มี request นานกว่า `sessionTimeoutMinutes` และส่งอีเมลแจ้งเมื่อเข้าสู่ระบบจากอุปกรณ์ใหม่ ได้รับ role อีเมลหรือรหัสผ่านถูกเปลี่ยน หรือบัญชีถูกระงับเพราะไม่ได้ใช้ (P-027) |
| Log ตามที่ audit และกฎหมายไทยขอ | เก็บ activity อย่างน้อย 90 วันตามมาตรา 26 ของ พ.ร.บ.คอมพิวเตอร์ (`activityRetentionDays` 90–3650) และบันทึกการเข้าสู่ระบบที่ล้มเหลวของบัญชีที่มีอยู่จริง (`auth.sign_in_failed` พร้อมสาเหตุ) กับการล็อกบัญชี (`auth.account_locked`) (P-026) |
| กฎรหัสผ่าน | ความยาวขั้นต่ำ (`passwordMinLength`) และจำนวนรหัสล่าสุดที่ห้ามใช้ซ้ำ (`passwordHistory`) มาจากหน้าตั้งค่า ใช้กับทุกที่ที่ตั้งรหัส ใครก็อ่านได้ที่ `GET /api/auth/password-policy` (P-017) |
| รหัสสำรองของ two-step sign-in | เปิด two-step sign-in แล้วได้รหัสใช้ครั้งเดียว 10 รหัส (`abcde-fghjk`) แสดงครั้งเดียว เก็บเป็น hash แบบรหัสผ่าน ใช้แทนรหัสจากแอปได้ทุกที่ที่ถามรหัส สร้างชุดใหม่ได้ที่ `POST /api/me/recovery-codes` (ต้องยืนยันตัวตนซ้ำ) ปิด two-step sign-in แล้วรหัสถูกลบ (P-025) |
| Two-step sign-in ตาม role | role ใน `twoFactorRoleIds` ของหน้าตั้งค่าต้องเปิด two-step sign-in ก่อน ระหว่างนั้นทุก request ตอบ 403 `two_factor_mandatory` ยกเว้นสิ่งที่ใช้เปิดมัน (P-016) |
| หลาย role และ role ชั่วคราว | หนึ่งคนถือได้หลาย role สิทธิ์คือผลรวมของ role ที่นับอยู่ การมอบแต่ละครั้งมีวันเริ่ม วันสิ้นสุด (หรือถาวร) และเหตุผล แต่ละ role ตั้งกติกาได้ (มอบได้นานสุดกี่วัน, ต้องมีเหตุผล) หมดเวลาแล้วหยุดนับทันที ต้องมีผู้ใช้ที่ถือ role ที่มีทุกสิทธิ์แบบถาวรเหลืออย่างน้อยหนึ่งคน (`last_administrator`) ส่วน role ในตัวใช้ชื่อและสิทธิ์ตามโค้ดทุกครั้งที่ start (P-019) |
| สาขา | เปิดใช้ได้ในหน้าตั้งค่า (`branchesEnabled`) ผู้ใช้สังกัดได้หนึ่งสาขาหรือไม่สังกัด และมอบ role เฉพาะสาขาได้ ซึ่งให้สิทธิ์เฉพาะในสาขานั้น (`Actor.can(permission, branchId)` สำหรับ module ธุรกิจ) ส่วนสิทธิ์ของ user management (`hasAuthority`) และ `last_administrator` นับเฉพาะ role ทั้งองค์กร สาขาที่ปิดรับคนหรือการมอบใหม่ไม่ได้ (`branch_closed`) และลบสาขาที่ยังมีคนไม่ได้ (`branch_in_use`) (P-020) |
| บทบาทที่ห้ามถือร่วมกัน | กฎ (`/api/role-conflicts`) ระบุ role ที่คนเดียวห้ามถือพร้อมกัน การมอบ role ที่ทับช่วงเวลากับ role อื่นในกฎเดียวกันถูกปฏิเสธด้วย `role_conflict` ทุกขอบเขต ทั้งมอบรายคน, batch, ผู้ใช้ใหม่ และการเลื่อนวันสิ้นสุด ส่วนคนที่ถืออยู่ก่อนสร้างกฎ หน้า roles แสดงเป็นรายการที่ต้องแก้ (P-022) |
| ผู้ดูแลระดับสาขา | role ที่จำกัดเฉพาะสาขาให้ `users.view`, `users.create`, `users.edit` และ `activity.view` กับคนที่สังกัดสาขานั้น ผู้จัดการสาขาจึงดู เพิ่ม แก้ไข และมอบ role ที่จำกัดสาขาให้คนในสาขาตัวเองได้ และเห็น activity ของคนในสาขา กฎ "ห้ามให้สิทธิ์เกินที่ตัวเองมี" นับตามขอบเขตของแต่ละ role ส่วนการลบผู้ใช้ role การตั้งค่า และสาขา ยังต้องมี role ทั้งองค์กร (P-024) |
| ยืนยันตัวตนซ้ำก่อนทำสิ่งเสี่ยง | การมอบ แก้ หรือถอด role (รวมผู้ใช้ใหม่), การแก้สิทธิ์หรือลบ role, การลบผู้ใช้, การ reset รหัสผ่านหรือ 2FA และจบ session ของคนอื่น และการแก้การตั้งค่า ต้องเข้าสู่ระบบหรือยืนยันรหัสผ่านมาไม่เกิน `reauthMinutes` (ค่าเริ่มต้น 10 นาที, 0 = ไม่ถาม) ไม่อย่างนั้นตอบ 403 `reauth_required` แล้วยืนยันได้ที่ `POST /api/me/reauthenticate` (P-023) |
| เหตุผลของการเปลี่ยนแปลงสำคัญ | การระงับหรือเปิดใช้บัญชี ลบผู้ใช้ ถอดบทบาท reset รหัสผ่านหรือ 2FA และจบ session ของคนอื่น รับ `reason` ได้ ระบบเก็บไว้ใน activity log (`Activity.reason`) ส่วน `reasonsRequired` ในหน้าตั้งค่าเลือกว่าข้อไหนต้องมี ไม่มีจะตอบ `reason_required` (ค่าเริ่มต้นคือการระงับกับการลบ) (P-021) |

## Config ที่ต้องตั้งใน production (`--spring.profiles.active=prod`)

| Env | ใช้ทำอะไร |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `AC_JWT_KID`, `AC_JWT_PRIVATE_KEY`, `AC_JWT_PUBLIC_KEY` | key สำหรับเซ็น token ใช้ `tools/gen-keys.sh` สร้าง ถ้าไม่ตั้งไว้ ทุกครั้งที่ restart ทุกคนต้อง refresh token ใหม่ และแต่ละ instance จะมี key ของตัวเอง |
| `AC_ENCRYPTION_PASSWORD`, `AC_ENCRYPTION_SALT` | key สำหรับเข้ารหัส secret ของ two-step sign-in ใน DB (P-014) สร้างครั้งเดียวด้วย `tools/gen-keys.sh` แล้วเก็บไว้ตลอด ถ้าเปลี่ยน secret ที่เก็บไว้จะอ่านไม่ได้ ถ้าไม่ตั้งไว้ แอปจะไม่ start |
| `AC_ALLOWED_ORIGINS` | origin ของ dashboard (ใช้ทั้ง CORS และตรวจ `Origin` ของ endpoint ที่ใช้ cookie) |
| `AC_DASHBOARD_URL`, `AC_MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | ลิงก์ในอีเมลและ SMTP |
| `ACCESS_CONTROL_BOOTSTRAP_ADMIN_EMAIL`, `ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD` | ผู้ดูแลคนแรก (ใช้เฉพาะตอนที่ยังไม่มีผู้ใช้เลย) |

ค่าอื่น ๆ (อายุ token, lockout, rate limit, จำนวน activity สูงสุดที่ตอบ) อยู่ใน `AccessControlProperties` ส่วนกฎของบัญชี (ความยาวและประวัติรหัสผ่าน, role ที่ต้องใช้ two-step sign-in, ระยะเก็บ activity) ตั้งในหน้าตั้งค่า (`PATCH /api/settings`) ไม่ใช่ config (P-016–P-018)

## คำสั่ง

| คำสั่ง | ทำอะไร |
|---|---|
| `./mvnw test` | unit test และ ArchUnit (กติกา module, layer, naming, security, transaction) |
| `./mvnw verify` | test ทั้งหมดรวม integration test กับ PostgreSQL ใน Testcontainers (ข้ามให้เองถ้าไม่มี Docker) |
| `python .claude/skills/access-control-project-bootstrapper/scripts/check_structure.py --repo . --side backend` | ตรวจโครงสร้างตาม `contracts/structure.yaml` (รันบน checkout ที่สะอาด ไม่มี `target/`) |
| `docker build -f docker/Dockerfile -t admin-api-starter .` | image สำหรับ deploy |

## รันหลาย instance

ทำได้เลย เพราะ session, refresh token และ activity อยู่ใน PostgreSQL ส่วน live update ส่งต่อกันผ่าน `LISTEN/NOTIFY` (P-015) มีเงื่อนไขอยู่สองข้อ:

- ทุก instance ต้องใช้ `AC_JWT_*` และ `AC_ENCRYPTION_*` ชุดเดียวกัน
- rate limit กับ cache ของสิทธิ์ (60 วินาที) นับแยกต่อ instance
