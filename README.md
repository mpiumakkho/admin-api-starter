# admin-api-starter

Backend ของ [admin-ng-starter](../admin-ng-starter) สำหรับจัดการผู้ใช้ บทบาท สิทธิ์ การเข้าสู่ระบบ (JWT + 2FA) ประวัติการใช้งาน และการตั้งค่า ทำครบทุก endpoint ใน [`API.md`](../admin-ng-starter/src/app/user-management/API.md) ที่หน้าจอใช้

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

## Config ที่ต้องตั้งใน production (`--spring.profiles.active=prod`)

| Env | ใช้ทำอะไร |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `AC_JWT_KID`, `AC_JWT_PRIVATE_KEY`, `AC_JWT_PUBLIC_KEY` | key สำหรับเซ็น token ใช้ `tools/gen-keys.sh` สร้าง ถ้าไม่ตั้งไว้ ทุกครั้งที่ restart ทุกคนต้อง refresh token ใหม่ และแต่ละ instance จะมี key ของตัวเอง |
| `AC_ALLOWED_ORIGINS` | origin ของ dashboard (ใช้ทั้ง CORS และตรวจ `Origin` ของ endpoint ที่ใช้ cookie) |
| `AC_DASHBOARD_URL`, `AC_MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | ลิงก์ในอีเมลและ SMTP |
| `ACCESS_CONTROL_BOOTSTRAP_ADMIN_EMAIL`, `ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD` | ผู้ดูแลคนแรก (ใช้เฉพาะตอนที่ยังไม่มีผู้ใช้เลย) |

ค่าอื่น ๆ (อายุ token, lockout, rate limit, retention ของ activity) อยู่ใน `AccessControlProperties`

## คำสั่ง

| คำสั่ง | ทำอะไร |
|---|---|
| `./mvnw test` | unit test และ ArchUnit (กติกา module, layer, naming, security, transaction) |
| `./mvnw verify` | test ทั้งหมดรวม integration test กับ PostgreSQL ใน Testcontainers (ข้ามให้เองถ้าไม่มี Docker) |
| `python .claude/skills/access-control-project-bootstrapper/scripts/check_structure.py --repo . --side backend` | ตรวจโครงสร้างตาม `contracts/structure.yaml` (รันบน checkout ที่สะอาด ไม่มี `target/`) |
| `docker build -f docker/Dockerfile -t admin-api-starter .` | image สำหรับ deploy |

## ยังไม่ได้ทำ

- SSE แบบหลาย instance (ดู P-007)
- การเข้ารหัส TOTP secret ใน DB (ดู P-008)
