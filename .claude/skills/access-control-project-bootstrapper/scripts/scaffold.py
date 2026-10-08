#!/usr/bin/env python3
"""Scaffold folders and placeholder files from contracts/structure.yaml.

Usage:
    scaffold.py init        --repo <path> --side backend|frontend --org <org> [--contracts <dir>]
    scaffold.py add-module  --repo <backend-repo>  --org <org> --name <module>            # backend business module
    scaffold.py add-feature --repo <frontend-repo> --org <org> --name <feature> [--layers] # frontend feature

Never overwrites an existing file. Every placeholder starts with a comment that points to the
canonical tree entry so the next author (human or AI) knows what belongs there.
Prints one line per created path; exit 0 on success, 2 on usage error.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover
    print("pyyaml is required: pip install pyyaml", file=sys.stderr)
    sys.exit(2)

JAVA_MODULE_RE = re.compile(r"^[a-z][a-z0-9]*$")
FE_FEATURE_RE = re.compile(r"^[a-z][a-z0-9-]*$")


def load_structure(repo: Path, explicit: Path | None) -> dict:
    for c in [explicit, repo / "contracts", Path(__file__).resolve().parents[4] / "contracts"]:
        if c and (c / "structure.yaml").exists():
            return yaml.safe_load((c / "structure.yaml").read_text(encoding="utf-8"))
    print("contracts/structure.yaml not found", file=sys.stderr)
    sys.exit(2)


def sub(s: str, org: str) -> str:
    return s.replace("<org>", org)


class Writer:
    def __init__(self, repo: Path) -> None:
        self.repo = repo
        self.created: list[str] = []

    def dir(self, p: Path) -> None:
        if not p.exists():
            p.mkdir(parents=True, exist_ok=True)
            self.created.append(str(p.relative_to(self.repo)) + "/")

    def file(self, p: Path, content: str) -> None:
        if p.exists():
            return
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        self.created.append(str(p.relative_to(self.repo)))


# --------------------------------------------------------------------------- placeholders

def java_placeholder(rel: str, pkg: str, cls: str) -> str:
    kind = "interface" if cls.endswith(("Catalog", "Template", "Customizer", "Repository")) and not cls.endswith("Impl") else "class"
    if cls == "package-info":
        return f"// canonical: trees/BACKEND.md -> {rel}\n@org.jspecify.annotations.NullMarked\npackage {pkg};\n"
    return (
        f"// canonical: trees/BACKEND.md -> {rel}\n"
        f"// TODO implement according to docs/ (see AGENTS.md). Do not add logic that belongs to another layer.\n"
        f"package {pkg};\n\n"
        f"public {kind} {cls} {{\n}}\n"
    )


def ts_placeholder(rel: str) -> str:
    return f"// canonical: trees/FRONTEND.md -> {rel}\n// TODO implement according to docs/ (see AGENTS.md).\nexport {{}};\n"


def text_placeholder(rel: str, comment: str = "#") -> str:
    return f"{comment} canonical: {rel}\n"


# --------------------------------------------------------------------------- backend

def backend_init(repo: Path, s: dict, org: str, w: Writer) -> None:
    b = s["backend"]
    src = repo / sub(b["source_root"], org)
    test = repo / sub(b["test_root"], org)
    res = repo / b["resources_root"]
    base_pkg = f"com.{org}"
    fnd_name = b["roots"]["foundation"]
    app_name = b["roots"]["application"]
    fnd = src / fnd_name
    app = src / app_name

    def jfile(dir_: Path, name: str, pkg: str) -> None:
        rel = str((dir_ / name).relative_to(repo))
        w.file(dir_ / name, java_placeholder(rel, pkg, name.removesuffix(".java")))

    for f in b.get("foundation_root_files", []):
        jfile(fnd, f, f"{base_pkg}.{fnd_name}")

    for mod, spec in b["foundation_modules"].items():
        for layer in spec.get("layers", []):
            w.dir(fnd / mod / layer)
        for f in spec.get("extra_files", []):
            jfile(fnd / mod, f, f"{base_pkg}.{fnd_name}.{mod}")

    for pkg, spec in b.get("foundation_non_module_packages", {}).items():
        for f in spec.get("files", []):
            jfile(fnd / pkg, f, f"{base_pkg}.{fnd_name}.{pkg}")
        for subdir, files in spec.get("subfolders", {}).items():
            for f in files:
                jfile(fnd / pkg / subdir, f, f"{base_pkg}.{fnd_name}.{pkg}.{subdir}")

    a = b["application"]
    for f in a.get("root_files", []):
        jfile(app, f, f"{base_pkg}.{app_name}")
    for f in a.get("config_files", []):
        jfile(app / "config", f, f"{base_pkg}.{app_name}.config")
    sample = a.get("sample_module")
    if sample:
        for layer in a.get("module_layers", []):
            w.dir(app / sample / layer)

    rs = b.get("resources", {})
    for f in rs.get("required_files", []):
        p = res / f
        comment = "<!--" if f.endswith(".html") or f.endswith(".xml") else ("--" if f.endswith(".sql") else "#")
        body = text_placeholder(f"src/main/resources/{f}", comment)
        if comment == "<!--":
            body = f"<!-- canonical: src/main/resources/{f} -->\n"
        w.file(p, body)
    for d in rs.get("required_folders", []):
        w.dir(res / d)

    t = b.get("tests", {})
    adir = test / t.get("architecture_dir", "architecture")
    for f in t.get("architecture_files", []):
        jfile(adir, f, f"{base_pkg}.architecture")
    w.dir(test / "support")

    for f in b.get("repo_root_required", []):
        p = repo / f
        if f.endswith(".yml") or f.endswith(".sh") or f in (".gitignore",):
            w.file(p, text_placeholder(f))
        elif f.endswith(".md"):
            w.file(p, f"# {f}\n\n<!-- canonical: trees/BACKEND.md -->\n")
        elif f == "pom.xml":
            w.file(p, "<!-- canonical: trees/BACKEND.md -> pom.xml ; versions: contracts/project-profile.yaml -->\n")
        elif f == "docker/Dockerfile":
            w.file(p, text_placeholder(f))
        else:
            w.file(p, text_placeholder(f))


def backend_add_module(repo: Path, s: dict, org: str, name: str, w: Writer) -> None:
    if not JAVA_MODULE_RE.match(name):
        print(f"module name must match {JAVA_MODULE_RE.pattern} (singular, lowercase): {name}", file=sys.stderr)
        sys.exit(2)
    b = s["backend"]
    src = repo / sub(b["source_root"], org)
    app = src / b["roots"]["application"] / name
    for layer in b["application"]["module_layers"]:
        w.dir(app / layer)
    w.file(app / "README.md",
           f"# app/{name}\n\nBusiness module `{name}` — layers per docs/04. "
           f"Declare its permissions in `app/config/AppPermissions.java`, migrations in `db/migration/app/`.\n")


# --------------------------------------------------------------------------- frontend

def frontend_init(repo: Path, s: dict, org: str, w: Writer) -> None:
    f = s["frontend"]
    lib = f["library"]
    libroot = repo / lib["lib"]
    for x in lib.get("required_files", []):
        p = repo / x
        if x.endswith("package.json"):
            w.file(p, f'{{\n  "name": "@{org}/access-control",\n  "version": "0.1.0",\n  "peerDependencies": {{ "@angular/core": "^22.0.0", "ng-zorro-antd": "^22.0.0" }}\n}}\n')
        elif x.endswith("ng-package.json"):
            w.file(p, '{\n  "$schema": "../../node_modules/ng-packagr/ng-package.schema.json",\n  "dest": "../../dist/access-control",\n  "lib": { "entryFile": "src/public-api.ts" }\n}\n')
        else:
            w.file(p, ts_placeholder(x))
    for folder, files in lib.get("folders", {}).items():
        w.dir(libroot / folder)
        for x in files:
            rel = str((libroot / folder / x).relative_to(repo))
            w.file(libroot / folder / x, ts_placeholder(rel))

    app = f["application"]
    approot = repo / app["app"]
    for x in app.get("required_files", []):
        p = repo / x
        if x.endswith(".html"):
            w.file(p, f"<!-- canonical: trees/FRONTEND.md -> {x} -->\n")
        elif x.endswith(".scss"):
            w.file(p, f"// canonical: trees/FRONTEND.md -> {x}\n")
        else:
            w.file(p, ts_placeholder(x))
    for folder, files in app.get("folders", {}).items():
        w.dir(approot / folder)
        for x in files:
            rel = str((approot / folder / x).relative_to(repo))
            w.file(approot / folder / x, ts_placeholder(rel))
    sample = app.get("sample_feature")
    if sample:
        for layer in app.get("feature_layers", []):
            w.dir(approot / sample / layer)

    for x in f.get("repo_root_required", []):
        p = repo / x
        if x.endswith(".md"):
            w.file(p, f"# {x}\n\n<!-- canonical: trees/FRONTEND.md -->\n")
        elif x.endswith(".json"):
            w.file(p, f"// canonical: trees/FRONTEND.md -> {x}\n{{}}\n" if x != "package.json" else
                   f'{{\n  "name": "{org}-web",\n  "private": true,\n  "_canonical": "trees/FRONTEND.md; versions: contracts/project-profile.yaml"\n}}\n')
        else:
            w.file(p, text_placeholder(x, "//" if x.endswith((".js", ".ts")) else "#"))


def frontend_add_feature(repo: Path, s: dict, name: str, layers: bool, w: Writer) -> None:
    if not FE_FEATURE_RE.match(name):
        print(f"feature name must be kebab-case: {name}", file=sys.stderr)
        sys.exit(2)
    f = s["frontend"]
    approot = repo / f["application"]["app"]
    feat = approot / "features" / name
    w.dir(feat)
    if layers:
        for layer in f["application"]["feature_layers"]:
            w.dir(feat / layer)
    w.file(feat / "README.md",
           f"# features/{name}\n\nBusiness feature `{name}` — layers per docs/05 once it exceeds "
           f"{f['application'].get('feature_layers_threshold_files', 6)} files. Register its route in app.routes.ts with data.permission.\n")


# --------------------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["init", "add-module", "add-feature"])
    ap.add_argument("--repo", required=True, type=Path)
    ap.add_argument("--side", choices=["backend", "frontend"])
    ap.add_argument("--org", required=True)
    ap.add_argument("--name")
    ap.add_argument("--layers", action="store_true")
    ap.add_argument("--contracts", type=Path)
    args = ap.parse_args()

    if not re.match(r"^[a-z][a-z0-9]*$", args.org):
        print("org must be lowercase alphanumeric (used as com.<org> and @<org>)", file=sys.stderr)
        return 2
    repo = args.repo.resolve()
    repo.mkdir(parents=True, exist_ok=True)
    s = load_structure(repo, args.contracts)
    w = Writer(repo)

    if args.command == "init":
        if not args.side:
            print("--side required for init", file=sys.stderr)
            return 2
        (backend_init if args.side == "backend" else frontend_init)(repo, s, args.org, w)
    elif args.command == "add-module":
        if not args.name:
            print("--name required", file=sys.stderr)
            return 2
        backend_add_module(repo, s, args.org, args.name, w)
    else:
        if not args.name:
            print("--name required", file=sys.stderr)
            return 2
        frontend_add_feature(repo, s, args.name, args.layers, w)

    for c in w.created:
        print("created", c)
    print(f"{len(w.created)} path(s) created; run check_structure.py next")
    return 0


if __name__ == "__main__":
    sys.exit(main())
