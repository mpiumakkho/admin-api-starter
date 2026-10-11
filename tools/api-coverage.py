#!/usr/bin/env python3
"""
Which endpoints of the user management API this server has: every request in API.md (the contract, in
admin-ng-starter) beside the controller method that answers it, the permission its @PreAuthorize checks, and the
integration tests that call it. Then the error codes of API.md against ErrorCode. Writes docs/API-COVERAGE.md.

  python tools/api-coverage.py                 # API.md from ../admin-ng-starter
  python tools/api-coverage.py --api <path>    # API.md from somewhere else
  python tools/api-coverage.py --check         # write nothing; fail when an endpoint or error code is missing

Run it after changing a controller or API.md, and commit docs/API-COVERAGE.md with the change.
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_API = REPO.parent / "admin-ng-starter" / "src" / "app" / "user-management" / "API.md"
API_ON_GITHUB = "https://github.com/mpiumakkho/admin-ng-starter/blob/main/src/app/user-management/API.md"
OUTPUT = REPO / "docs" / "API-COVERAGE.md"
MAIN = REPO / "src" / "main" / "java"
TESTS = REPO / "src" / "test" / "java"


@dataclass
class Endpoint:
    method: str
    path: str  # as API.md writes it, without the query
    section: str
    permission: str  # the API.md cell, or "" when the table has no permission column
    handler: str = ""
    authorize: str = ""
    tests: set[str] = field(default_factory=set)

    @property
    def key(self) -> tuple[str, str]:
        return self.method, normalize(self.path)


def normalize(path: str) -> str:
    """/users/:id/sessions and /api/users/{id}/sessions both become /users/{}/sessions"""
    path = path.split("?")[0].removeprefix("/api")
    path = re.sub(r":\w+|\{[^}]*\}", "{}", path)
    return path.rstrip("/") or "/"


def contract(api: Path) -> list[Endpoint]:
    endpoints: list[Endpoint] = []
    prose: list[Endpoint] = []
    section, columns = "", []
    for line in api.read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            section = line[3:].strip()
        elif line.startswith("| Request"):
            columns = [cell.strip() for cell in line.strip("|").split("|")]
        else:
            match = re.match(r"^\| `(GET|POST|PATCH|PUT|DELETE) ([^`]+)` \|(.*)\|\s*$", line)
            if match:
                cells = [cell.strip() for cell in match.group(3).split("|")]
                permission = cells[-1] if columns and columns[-1] == "Permission" else ""
                endpoints.append(Endpoint(match.group(1), match.group(2).split("?")[0], section, permission))
            else:
                # Described in a paragraph rather than a table, such as GET /events under "Live updates"
                for method, path in re.findall(r"`(GET|POST|PATCH|PUT|DELETE) (/[^`]+)`", line):
                    prose.append(Endpoint(method, path.split("?")[0], section, ""))
    known = {endpoint.key for endpoint in endpoints}
    for endpoint in prose:
        if endpoint.key not in known:
            known.add(endpoint.key)
            endpoints.append(endpoint)
    return endpoints


def controllers() -> dict[tuple[str, str], tuple[str, str]]:
    """(method, path) -> (Controller.method, @PreAuthorize expression)"""
    found = {}
    for source in MAIN.rglob("*Controller.java"):
        text = source.read_text(encoding="utf-8")
        if "@RestController" not in text:
            continue
        base = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"', text)
        base_path = base.group(1) if base else ""
        for match in re.finditer(r"((?:[ \t]*@[^\n]+\n)+)[ \t]*(?:public\s+)?[\w<>\[\], ?]+\s+(\w+)\s*\(", text):
            annotations, name = match.group(1), match.group(2)
            mapping = re.search(r'@(Get|Post|Patch|Put|Delete)Mapping(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)")?', annotations)
            if not mapping:
                continue
            authorize = re.search(r'@PreAuthorize\("([^"]*)"\)', annotations)
            key = (mapping.group(1).upper(), normalize(base_path + (mapping.group(2) or "")))
            found[key] = (f"{source.stem}.{name}", authorize.group(1) if authorize else "")
    return found


def test_calls(endpoint_keys: set[tuple[str, str]]) -> dict[tuple[str, str], set[str]]:
    """(method, path) -> the test classes that call it, read from how ApiIntegrationTest and MockMvcTester are used"""
    calls: dict[tuple[str, str], set[str]] = {}
    # Each pattern with the method when the call does not name it
    patterns: list[tuple[re.Pattern[str], str | None]] = [
        # get("/api/users/" + id + "/sessions", browser)
        (re.compile(r'\bget\(\s*"(/api[^"]*)"((?:\s*\+\s*[^,"]+?)?(?:\s*\+\s*"([^"]*)")?)\s*,'), "GET"),
        # send(HttpMethod.PATCH, "/api/users/" + id, browser, body)
        (re.compile(r'\bsend\(\s*HttpMethod\.(\w+),\s*"(/api[^"]*)"((?:\s*\+\s*[^,"]+?)?(?:\s*\+\s*"([^"]*)")?)\s*,'), None),
        # mvc.post().uri("/api/auth/login")
        (re.compile(r'\.(get|post|patch|put|delete)\(\)\s*\.uri\(\s*"(/api[^"]*)"'), None),
    ]
    paths: dict[str, list[tuple[str, str]]] = {}
    for key in endpoint_keys:
        paths.setdefault(key[1], []).append(key)
    for source in TESTS.rglob("*.java"):
        text = source.read_text(encoding="utf-8")
        # A literal path passed to a helper, such as fromLink("/api/auth/password-reset/confirm", …), when only one
        # endpoint has that path, so the method cannot be wrong
        for literal in re.findall(r'"(/api/[^"\s]*)"', text):
            matches = paths.get(normalize(literal), [])
            if len(matches) == 1:
                calls.setdefault(matches[0], set()).add(source.stem)
        for pattern, fixed in patterns:
            for m in pattern.finditer(text):
                if fixed:
                    method, prefix, tail, suffix = fixed, m.group(1), m.group(2), m.group(3)
                elif pattern.pattern.startswith(r"\bsend"):
                    method, prefix, tail, suffix = m.group(1), m.group(2), m.group(3), m.group(4)
                else:
                    method, prefix, tail, suffix = m.group(1).upper(), m.group(2), "", None
                path = prefix + ("{}" if tail and tail.strip() and not tail.strip().startswith('+ "') else "") + (suffix or "")
                calls.setdefault((method, normalize(path)), set()).add(source.stem)
    return calls


def error_codes(api: Path) -> tuple[list[str], list[str]]:
    """The codes the Errors table of API.md names, and the ones ErrorCode declares"""
    text = api.read_text(encoding="utf-8")
    errors = text[text.index("## Errors"):] if "## Errors" in text else ""
    contract_codes = sorted({code for row in re.findall(r"^\| `\d{3}` \|(.*)$", errors, flags=re.M)
                             for code in re.findall(r"`([a-z_]+)`", row)})
    enum = next(MAIN.rglob("ErrorCode.java")).read_text(encoding="utf-8")
    server_codes = sorted(name.lower() for name in re.findall(r"^\s+([A-Z_]+)\(HttpStatus", enum, flags=re.M))
    return contract_codes, server_codes


def permission_status(endpoint: Endpoint) -> str:
    """
    Whether @PreAuthorize names the permission API.md gives; "" when API.md gives none. "Without it, …" in API.md means
    everyone signed in may call it and the service narrows what they get, so isAuthenticated() is right there.
    """
    needed = re.findall(r"`([a-z]+\.[a-z]+)`", endpoint.permission)
    if not needed:
        return ""
    if "without it" in endpoint.permission and endpoint.authorize == "isAuthenticated()":
        return "✅ service"
    return "✅" if all(permission in endpoint.authorize for permission in needed) else "⚠️"


def render(endpoints: list[Endpoint], extra: list[tuple[tuple[str, str], str]], codes: tuple[list[str], list[str]]) -> str:
    """extra: what the controllers answer and API.md does not list, as ((method, path), Controller.method)"""
    done = sum(1 for endpoint in endpoints if endpoint.handler)
    tested = sum(1 for endpoint in endpoints if endpoint.tests)
    contract_codes, server_codes = codes
    lines = [
        "# API coverage",
        "",
        "<!-- Generated by tools/api-coverage.py. Do not edit by hand: change the code or API.md, then run the script again. -->",
        "",
        f"สัญญาของ API อยู่ที่ [`API.md`]({API_ON_GITHUB}) ใน admin-ng-starter (ในเครื่อง: `../admin-ng-starter/src/app/user-management/API.md`)",
        "ไฟล์นี้บอกว่า server ตัวนี้ทำ endpoint ไหนแล้ว ตรวจสิทธิ์ตรงกับสัญญาไหม และมี integration test ไหนเรียกบ้าง",
        "",
        f"- **Endpoint:** ทำแล้ว {done} จาก {len(endpoints)}",
        f"- **มี integration test เรียกตรง:** {tested} จาก {len(endpoints)}",
        f"- **Error code:** สัญญามี {len(contract_codes)} ตัว, `ErrorCode` มี {len(server_codes)} ตัว",
        "",
        "สถานะ: ✅ ทำแล้ว ❌ ยังไม่มีใน controller",
        "",
        "คอลัมน์สิทธิ์: ✅ `@PreAuthorize` ตรวจสิทธิ์ที่สัญญาระบุ, ✅ service คือสัญญาบอกว่าไม่มีสิทธิ์ก็เรียกได้แต่ได้ข้อมูลน้อยลง"
        " (เช่น เห็นแค่ตัวเอง) จึงเปิดให้ทุกคนที่เข้าสู่ระบบแล้วให้ service กรองเอง, ⚠️ ไม่ตรง, ว่างคือสัญญาไม่ได้ระบุสิทธิ์ในตาราง",
        "",
        "Integration test: class ที่เรียก endpoint นั้นตรงๆ (`ApiIntegrationTest` คือ helper ที่ทุก IT ใช้เข้าสู่ระบบ) ขีดคือยังไม่มี test เรียก",
    ]
    section = None
    for endpoint in endpoints:
        if endpoint.section != section:
            section = endpoint.section
            lines += ["", f"## {section}", "", "| | Request | สิทธิ์ (API.md) | `@PreAuthorize` | | Controller | Integration test |",
                      "| --- | --- | --- | --- | --- | --- | --- |"]
        permission = re.sub(r"\s*\|\s*", " ", endpoint.permission)
        lines.append(" | ".join([
            "|" + (" ✅" if endpoint.handler else " ❌"),
            f"`{endpoint.method} {endpoint.path}`",
            permission or "—",
            f"`{endpoint.authorize}`" if endpoint.authorize else "—",
            permission_status(endpoint) or " ",
            f"`{endpoint.handler}`" if endpoint.handler else "—",
            ", ".join(sorted(endpoint.tests)) or "—",
        ]) + " |")
    if extra:
        lines += ["", "## มีใน server แต่ไม่อยู่ในสัญญา", "", "| Request | Controller |", "| --- | --- |"]
        lines += [f"| `{method} {path}` | `{handler}` |" for (method, path), handler in extra]
    lines += ["", "## Error codes", ""]
    missing = [code for code in contract_codes if code not in server_codes]
    unlisted = [code for code in server_codes if code not in contract_codes]
    lines.append("✅ ทุก code ในตาราง Errors ของสัญญามีใน `ErrorCode`" if not missing
                 else f"❌ สัญญามีแต่ `ErrorCode` ไม่มี: {', '.join(f'`{code}`' for code in missing)}")
    if unlisted:
        lines.append("")
        lines.append(f"`ErrorCode` มีแต่ตาราง Errors ของสัญญาไม่ได้ระบุ: {', '.join(f'`{code}`' for code in unlisted)}")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=(__doc__ or "").strip().partition("\n")[0])
    parser.add_argument("--api", type=Path, default=DEFAULT_API, help="path to API.md")
    parser.add_argument("--check", action="store_true", help="write nothing; exit 1 when an endpoint or error code is missing")
    args = parser.parse_args()
    if not args.api.exists():
        print(f"API.md not found at {args.api}; pass --api <path>", file=sys.stderr)
        return 2

    endpoints = contract(args.api)
    handlers = controllers()
    calls = test_calls({endpoint.key for endpoint in endpoints})
    for endpoint in endpoints:
        endpoint.handler, endpoint.authorize = handlers.get(endpoint.key, ("", ""))
        endpoint.tests = calls.get(endpoint.key, set())
    keys = {endpoint.key for endpoint in endpoints}
    extra = sorted((key, handler) for key, (handler, _) in handlers.items() if key not in keys)
    codes = error_codes(args.api)

    missing = [f"{e.method} {e.path}" for e in endpoints if not e.handler]
    missing_codes = [code for code in codes[0] if code not in codes[1]]
    wrong_permission = [f"{e.method} {e.path}" for e in endpoints if permission_status(e) == "⚠️"]
    if args.check:
        for label, items in (("missing endpoints", missing), ("missing error codes", missing_codes), ("permission differs", wrong_permission)):
            if items:
                print(f"{label}: {', '.join(items)}")
        print(f"{len(endpoints) - len(missing)}/{len(endpoints)} endpoints, {len(codes[0]) - len(missing_codes)}/{len(codes[0])} error codes")
        return 1 if missing or missing_codes or wrong_permission else 0

    OUTPUT.parent.mkdir(exist_ok=True)
    OUTPUT.write_text(render(endpoints, extra, codes), encoding="utf-8", newline="\n")
    print(f"wrote {OUTPUT.relative_to(REPO)}: {len(endpoints) - len(missing)}/{len(endpoints)} endpoints")
    return 0


if __name__ == "__main__":
    sys.exit(main())
