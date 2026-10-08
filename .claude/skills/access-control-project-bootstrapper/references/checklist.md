# Bootstrap / review checklist (copy into the final report)

## Structure
- [ ] `check_structure.py --side backend` exit 0
- [ ] `check_structure.py --side frontend` exit 0
- [ ] No `target/`, `.idea/`, `node_modules/`, `dist/` committed; `.gitignore` present

## Backend
- [ ] `AppPermissions implements PermissionCatalog` — codes match `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$`
- [ ] `AppRoles implements RoleTemplate` — codes UPPER_SNAKE, only reference existing permission codes
- [ ] Every `/api/admin/**` and business handler has `@PreAuthorize("hasAuthority('…')")`; no `hasRole(` anywhere
- [ ] New module has `controller/ service/ repository/ entity/ dto/`; cross-module imports only `service|dto|event|spi`
- [ ] `@Transactional` only in `service/`
- [ ] Flyway file under `db/migration/app/` named `V<n>__<snake>.sql`
- [ ] `./mvnw verify` green (ArchUnit + slices + Testcontainers)

## Frontend
- [ ] `app.config.ts` has `provideAccessControl(...)` and `withInterceptors([authInterceptor])`
- [ ] `/admin` mounts `ACCESS_CONTROL_ROUTES`
- [ ] Every guarded route has `data: { permission, menu? }`; sidebar derives from routes
- [ ] Destructive actions go through `ConfirmService`
- [ ] Imports from library only via `@<org>/access-control`; ng-zorro imported per component
- [ ] No `*.module.ts`, no NgRx, no `localStorage` for tokens
- [ ] `npm run lint && npm test && npm run build` green; bundle within budget

## Documentation
- [ ] If any rule/structure changed: `docs/`, `trees/`, `contracts/` updated in the same PR + ADR if it contradicts `docs/02`
- [ ] Project `README` records template tag and org
