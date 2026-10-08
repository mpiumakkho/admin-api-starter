---
name: access-control-project-bootstrapper
description: Create, extend and verify projects built on the access-control Enterprise RBAC Foundation (Spring Boot 4 + Angular 22 + ng-zorro-antd). Use this skill whenever the user wants to start a new project from the access-control template, add a backend module or frontend feature to such a project, check that a repo follows the canonical structure, or asks anything like "ตั้งโปรเจกต์ใหม่จาก access-control", "เพิ่ม module", "ตรวจโครงสร้าง", "scaffold RBAC project", "bootstrap from template" — even if they don't name the skill. Also use it before finishing any task that created or moved files in an access-control-based repo, to run the structure check.
---

# access-control project bootstrapper

This skill has no architectural knowledge of its own. Everything it does is driven by the
repository's documentation and contracts:

| Need | Read |
|---|---|
| Rules an agent must follow | `AGENTS.md` |
| Why the structure is the way it is | `docs/01-OVERVIEW.md`, `docs/02-DECISIONS.md` |
| Canonical paths, purpose, allowed dependencies | `trees/BACKEND.md`, `trees/FRONTEND.md` |
| Machine-readable structure | `contracts/structure.yaml` |
| Versions and fixed technology choices | `contracts/project-profile.yaml` |
| Dependency rules (ArchUnit / ESLint spec) | `contracts/dependency-rules.yaml` |
| Naming | `contracts/naming.yaml` |
| Core permissions / roles / endpoints | `contracts/permissions.yaml` |
| Human step-by-step | `docs/13-NEW-PROJECT-GUIDE.md` |

If something you need is not in those files, stop and ask the user; do not invent structure.
If the user asks for something the contracts forbid (PrimeNG, NgRx, `hasRole`, a menu table,
a `util/` package, a different Spring Boot line…), explain which ADR/contract forbids it and
offer the documented alternative instead of complying silently.

## Workflows

### 1. Verify an existing repo (`check`)

Run first in any repo that claims to follow access-control, and again before you finish a task.

```bash
python3 <skill-dir>/scripts/check_structure.py --repo <backend-repo> --side backend
python3 <skill-dir>/scripts/check_structure.py --repo <frontend-repo> --side frontend
```

- Exit code 0 = conforms. Non-zero prints one line per violation: `MISSING <path>`, `FORBIDDEN <path>`, `LAYER-MISSING <module>/<layer>`, `DEPTH <path>`, `PATTERN <path>`.
- Fix violations by moving/creating files to match `trees/*.md`; never by editing `contracts/structure.yaml` to match the repo (that requires a doc change + ADR).
- After the structure check passes, run the real gates: backend `./mvnw -q verify` (ArchUnit lives in `src/test/java/com/<org>/architecture/`), frontend `npm run lint && npm test`.

### 2. Create a new project from the template (`init`)

Follow `docs/13-NEW-PROJECT-GUIDE.md` §0–§4 literally. Before creating anything, collect these
answers (ask if missing; do not guess):

1. `org` (lowercase, used as `com.<org>` and `@<org>`) and project name
2. First business module(s), singular lowercase (backend) and plural kebab-case (frontend feature)
3. Business permissions as `resource:action` (validate against `code_pattern` in `contracts/permissions.yaml`)
4. Initial roles (UPPER_SNAKE) and which permissions each gets

Then:

```bash
python3 <skill-dir>/scripts/scaffold.py init --repo <backend-repo> --side backend --org <org>
python3 <skill-dir>/scripts/scaffold.py init --repo <frontend-repo> --side frontend --org <org>
```

`scaffold.py init` creates every folder and placeholder file listed in `contracts/structure.yaml`
(placeholders contain a header comment pointing to the canonical tree entry). It never overwrites
an existing file. After scaffolding:

- Fill `AppPermissions.java` / `AppRoles.java` from the answers above (`docs/11-EXTENSION-POINTS.md` E1/E2).
- Write `application.yml` from `docs/11` E3 with values from `contracts/project-profile.yaml` `security_defaults`.
- Register the first business route in `app.routes.ts` with `data: { permission, menu }` (`docs/11` F3).
- Delete the sample module/feature named in `structure.yaml` (`sample_module`, `sample_feature`) unless the user wants to keep it as reference.
- Run workflow 1.

### 3. Add a module / feature (`add`)

```bash
python3 <skill-dir>/scripts/scaffold.py add-module  --repo <backend-repo>  --org <org> --name product
python3 <skill-dir>/scripts/scaffold.py add-feature --repo <frontend-repo> --org <org> --name products
```

- Backend: creates `app/<name>/{controller,service,repository,entity,dto}` with placeholder files. Then add the module's permissions to `AppPermissions`, a Flyway file under `db/migration/app/`, and `@PreAuthorize` on every endpoint (`docs/10`, `docs/08`).
- Frontend: creates `features/<name>/` flat (fewer than `feature_layers_threshold_files` files) or with `{api,model,store,pages,components}` when `--layers` is passed. Then add the route with `data.permission`.
- Cross-module code must respect `contracts/dependency-rules.yaml` R2/R3: only `service`/`dto`/`event`/`spi` (backend) or `model`/`index.ts` (frontend) of other modules.

### 4. Review a change against the rules (`review`)

When asked to review a PR or diff in an access-control repo, check in this order and report per rule id:

1. Structure — workflow 1
2. `contracts/dependency-rules.yaml` — every rule id; quote the offending import
3. `contracts/naming.yaml` — file/class names
4. `docs/12` Definition of Done — `@PreAuthorize` present, permission in catalog, route has `data.permission`, destructive actions use `ConfirmService`, docs/contracts updated when rules changed

## Guardrails

- Read `AGENTS.md` at the start of every session in the repo; it is short.
- Never create `util/`, `utils/`, `helpers/`, `shared/` dumping grounds, `*.module.ts`, or an entity with a `Ms`/`Tb` prefix.
- Never place permission checks on role names. Never put permissions or roles in JWT claims.
- Never store tokens in `localStorage`/`sessionStorage`.
- Never edit files under `accesscontrol/` or `projects/access-control/` inside a *derived* project to make it "work for this project" — propose the change to the template instead (`docs/13` §5).
- When the structure check and the docs disagree, the docs win; report the mismatch to the user and fix `contracts/` in the same change.

## References

- `references/checklist.md` — one-page checklist used by workflows 2–4 (copy it into your final report).

## Requirements

`python3` with `pyyaml` (`pip install pyyaml`). Scripts read `contracts/` from the target repo first, then fall back to the copy bundled with this documentation set.
