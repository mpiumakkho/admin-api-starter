#!/usr/bin/env python3
"""Verify a repository against contracts/structure.yaml.

Usage:
    check_structure.py --repo <path> --side backend|frontend [--org <org>] [--contracts <dir>]

The contracts directory defaults to <repo>/contracts, falling back to the directory
that ships with this skill (../../../../contracts relative to this file).

Exit code 0 when the repo conforms; 1 when violations were found; 2 on usage error.
Violations are printed one per line:  <KIND> <path or description>
"""
from __future__ import annotations

import argparse
import fnmatch
import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover
    print("pyyaml is required: pip install pyyaml", file=sys.stderr)
    sys.exit(2)


# --------------------------------------------------------------------------- helpers

def load_contracts(repo: Path, explicit: Path | None) -> tuple[dict, dict]:
    candidates = []
    if explicit:
        candidates.append(explicit)
    candidates.append(repo / "contracts")
    candidates.append(Path(__file__).resolve().parents[4] / "contracts")
    for c in candidates:
        if (c / "structure.yaml").exists():
            structure = yaml.safe_load((c / "structure.yaml").read_text(encoding="utf-8"))
            profile_path = c / "project-profile.yaml"
            profile = yaml.safe_load(profile_path.read_text(encoding="utf-8")) if profile_path.exists() else {}
            return structure, profile
    print("contracts/structure.yaml not found", file=sys.stderr)
    sys.exit(2)


def detect_org(repo: Path, side: str, explicit: str | None) -> str:
    if explicit:
        return explicit
    if side == "backend":
        base = repo / "src/main/java/com"
        if base.exists():
            orgs = [p.name for p in base.iterdir() if p.is_dir()]
            if len(orgs) == 1:
                return orgs[0]
    else:
        pkg = repo / "projects/access-control/package.json"
        if pkg.exists():
            import json
            name = json.loads(pkg.read_text(encoding="utf-8")).get("name", "")
            if name.startswith("@") and "/" in name:
                return name[1:].split("/")[0]
    return "<org>"


class Report:
    def __init__(self) -> None:
        self.items: list[str] = []

    def add(self, kind: str, what: str) -> None:
        self.items.append(f"{kind:<14} {what}")

    def ok(self) -> bool:
        return not self.items


def sub(path: str, org: str) -> str:
    return path.replace("<org>", org)


# --------------------------------------------------------------------------- backend

def check_backend(repo: Path, s: dict, org: str, r: Report) -> None:
    b = s["backend"]
    src = repo / sub(b["source_root"], org)
    test = repo / sub(b["test_root"], org)
    res = repo / b["resources_root"]
    fnd = src / b["roots"]["foundation"]
    app = src / b["roots"]["application"]

    # repo root
    for f in b.get("repo_root_required", []):
        if not (repo / f).exists():
            r.add("MISSING", f)
    for pat in b.get("repo_root_forbidden", []):
        for p in repo.glob(pat):
            r.add("FORBIDDEN", str(p.relative_to(repo)))

    if not src.exists():
        r.add("MISSING", str(src.relative_to(repo)))
        return

    # foundation root files
    for f in b.get("foundation_root_files", []):
        if not (fnd / f).exists():
            r.add("MISSING", str((fnd / f).relative_to(repo)))

    # foundation modules
    for mod, spec in b["foundation_modules"].items():
        mdir = fnd / mod
        if not mdir.exists():
            r.add("MISSING", str(mdir.relative_to(repo)))
            continue
        for layer in spec.get("layers", []):
            if not (mdir / layer).is_dir():
                r.add("LAYER-MISSING", f"{mod}/{layer}")
        for f in spec.get("extra_files", []):
            if not (mdir / f).exists():
                r.add("MISSING", str((mdir / f).relative_to(repo)))
        for child in mdir.iterdir():
            if child.is_dir() and child.name not in spec.get("layers", []):
                r.add("UNKNOWN-LAYER", f"{mod}/{child.name}")

    # non-module packages
    for pkg, spec in b.get("foundation_non_module_packages", {}).items():
        pdir = fnd / pkg
        for f in spec.get("files", []):
            if not (pdir / f).exists():
                r.add("MISSING", str((pdir / f).relative_to(repo)))
        for subdir, files in spec.get("subfolders", {}).items():
            for f in files:
                if not (pdir / subdir / f).exists():
                    r.add("MISSING", str((pdir / subdir / f).relative_to(repo)))

    # application
    a = b["application"]
    for f in a.get("root_files", []):
        if not (app / f).exists():
            r.add("MISSING", str((app / f).relative_to(repo)))
    for f in a.get("config_files", []):
        if not (app / "config" / f).exists():
            r.add("MISSING", str((app / "config" / f).relative_to(repo)))
    if app.exists():
        for child in app.iterdir():
            if child.is_dir() and child.name != "config":
                for layer in a.get("module_layers", []):
                    if not (child / layer).is_dir():
                        r.add("LAYER-MISSING", f"app/{child.name}/{layer}")

    # forbidden folders / class patterns / depth
    forbidden = set(b.get("forbidden_folders", []))
    max_depth = int(b.get("max_depth_from_module_root", 4))
    module_roots = [fnd / m for m in b["foundation_modules"]] + (
        [c for c in app.iterdir() if c.is_dir() and c.name != "config"] if app.exists() else []
    )
    for p in src.rglob("*"):
        rel = p.relative_to(repo)
        if p.is_dir() and p.name in forbidden:
            r.add("FORBIDDEN", str(rel))
        if p.is_file() and p.suffix == ".java":
            stem = p.stem
            in_common = "/common/" in str(rel).replace("\\", "/")
            for pat in b.get("forbidden_class_patterns", []):
                if fnmatch.fnmatch(stem, pat) and not (in_common and pat.endswith("Utils")):
                    r.add("PATTERN", f"{rel} matches forbidden class pattern {pat}")
    for mroot in module_roots:
        for p in mroot.rglob("*"):
            depth = len(p.relative_to(mroot).parts)
            if p.is_file() and depth > max_depth:
                r.add("DEPTH", f"{p.relative_to(repo)} (depth {depth} > {max_depth})")

    # resources
    rs = b.get("resources", {})
    for f in rs.get("required_files", []):
        if not (res / f).exists():
            r.add("MISSING", str((res / f).relative_to(repo)))
    for d in rs.get("required_folders", []):
        if not (res / d).is_dir():
            r.add("MISSING", str((res / d).relative_to(repo)) + "/")
    for d in rs.get("forbidden_folders", []):
        if (res / d).exists():
            r.add("FORBIDDEN", str((res / d).relative_to(repo)))

    # tests
    t = b.get("tests", {})
    adir = test / t.get("architecture_dir", "architecture")
    for f in t.get("architecture_files", []):
        if not (adir / f).exists():
            r.add("MISSING", str((adir / f).relative_to(repo)))


# --------------------------------------------------------------------------- frontend

def check_frontend(repo: Path, s: dict, org: str, r: Report) -> None:
    f = s["frontend"]
    for x in f.get("repo_root_required", []):
        if not (repo / x).exists():
            r.add("MISSING", x)
    for pat in f.get("repo_root_forbidden", []):
        for p in repo.glob(pat):
            r.add("FORBIDDEN", str(p.relative_to(repo)))

    lib = f["library"]
    for x in lib.get("required_files", []):
        if not (repo / x).exists():
            r.add("MISSING", x)
    libroot = repo / lib["lib"]
    for folder, files in lib.get("folders", {}).items():
        d = libroot / folder
        if not d.is_dir():
            r.add("MISSING", str(d.relative_to(repo)) + "/")
            continue
        for x in files:
            if not (d / x).exists():
                r.add("MISSING", str((d / x).relative_to(repo)))

    app = f["application"]
    for x in app.get("required_files", []):
        if not (repo / x).exists():
            r.add("MISSING", x)
    approot = repo / app["app"]
    for folder, files in app.get("folders", {}).items():
        d = approot / folder
        if not d.is_dir():
            r.add("MISSING", str(d.relative_to(repo)) + "/")
            continue
        for x in files:
            if not (d / x).exists():
                r.add("MISSING", str((d / x).relative_to(repo)))

    # feature layer check: once a feature has more than threshold files, it must use layer folders
    threshold = int(app.get("feature_layers_threshold_files", 6))
    layers = set(app.get("feature_layers", []))
    features = approot / "features"
    if features.exists():
        for feat in features.iterdir():
            if not feat.is_dir():
                continue
            files = [p for p in feat.rglob("*") if p.is_file() and not p.name.endswith(".spec.ts")]
            top_level_dirs = {p.name for p in feat.iterdir() if p.is_dir()}
            if len(files) > threshold and not top_level_dirs & layers:
                r.add("LAYER-MISSING", f"features/{feat.name} has {len(files)} files but no layer folders {sorted(layers)}")
            for d in top_level_dirs - layers:
                r.add("UNKNOWN-LAYER", f"features/{feat.name}/{d}")

    # forbidden file patterns everywhere under projects/
    for pat in f.get("forbidden_file_patterns", []):
        for p in (repo / "projects").rglob(pat):
            r.add("FORBIDDEN", str(p.relative_to(repo)))

    # token storage guard (auth folder must not mention browser storage)
    auth_dir = libroot / "auth"
    if auth_dir.exists():
        for p in auth_dir.rglob("*.ts"):
            txt = p.read_text(encoding="utf-8", errors="ignore")
            if "localStorage" in txt or "sessionStorage" in txt:
                r.add("FORBIDDEN", f"{p.relative_to(repo)} uses browser storage for auth state")


# --------------------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", required=True, type=Path)
    ap.add_argument("--side", required=True, choices=["backend", "frontend"])
    ap.add_argument("--org", default=None)
    ap.add_argument("--contracts", default=None, type=Path)
    args = ap.parse_args()

    repo = args.repo.resolve()
    if not repo.exists():
        print(f"repo not found: {repo}", file=sys.stderr)
        return 2
    structure, _profile = load_contracts(repo, args.contracts)
    org = detect_org(repo, args.side, args.org)
    report = Report()
    (check_backend if args.side == "backend" else check_frontend)(repo, structure, org, report)

    if report.ok():
        print(f"OK  {args.side} structure conforms to contracts/structure.yaml (org={org})")
        return 0
    print(f"FAIL {args.side}: {len(report.items)} violation(s) (org={org})")
    for line in report.items:
        print("  " + line)
    return 1


if __name__ == "__main__":
    sys.exit(main())
