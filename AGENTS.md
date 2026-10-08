# AGENTS.md — instructions for AI agents working in this repository

Read this first. Details live in `docs/02-DECISIONS.md`, `trees/BACKEND.md` and `contracts/`.

## What this repository is

`admin-api-starter` is the backend of `../admin-ng-starter`: user management (users, roles, sign-in with JWT and two-step sign-in, signed-in devices, activity log, settings, live updates). It was set up from the **access-control** template (Spring Boot 4.1, Java 25, modular monolith). The HTTP contract is `../admin-ng-starter/src/app/user-management/API.md`; the Angular side depends on it exactly.

Architecture motto: **Design like microservices, build like modular monolith.**

## Non-negotiable rules

1. **Module-first, layers inside.** `com.adminstarter.accesscontrol.<module>.{controller,service,repository,entity,dto,event,policy,spi}`; business modules go under `com.adminstarter.app.<module>`.
2. **Cross-module access only through the public surface:** another module's `service`, `dto`, `event`, `spi` (and root classes such as `CorePermissions`). Never its `entity`, `repository`, `controller` or `policy`. No cycles between modules.
3. **`accesscontrol` never imports `app`.**
4. **`audit` listens to events only;** `liveupdate` hears about changes only through `audit.event.ActivityRecorded`.
5. **Authorization is permission-based:** `@PreAuthorize("hasAuthority('users.edit')")` on every controller method (`isAuthenticated()` at least), never role names. Finer rules (API.md, "Rules the server must check") live in `policy/` and services, not controllers.
6. **Permissions are declared in code** (`PermissionCatalog`, `RoleTemplate`) and must match `catalog.ts` in admin-ng-starter.
7. **The API contract is API.md.** Changing a path, a field or an error code means changing API.md, `HttpAdminApi`, `localBackend` and this server together.
8. **`@Transactional` only in `service/`.** Every change publishes an event; `AuditListener` writes the activity entry in the same transaction.
9. **No dumping grounds:** no `util/`, `helper/`, `*Utils`, `*Helper`.
10. **Never invent structure.** New paths must fit `contracts/structure.yaml`; run the structure check and `./mvnw verify` before finishing. Versions are fixed in `contracts/project-profile.yaml`; changing one needs an ADR in `docs/02-DECISIONS.md`.

## Working conventions

- Documentation prose is Thai with English technical terms; code, identifiers, commit messages and YAML are English.
- Plain Spring Boot conventions: `Application` in the root package `com.adminstarter`; JPA entities with private fields, JavaBean getters (`getEmail()`, `isSystem()`) and intention-revealing methods instead of public setters.
- Modern Java: records for DTOs, events and value objects, `var`, switch expressions, `ScopedValue`, JSpecify `@NullMarked` packages with `@Nullable` where null is allowed.
- Commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` with the module as scope.
- When unsure, prefer the simpler option and say so.
