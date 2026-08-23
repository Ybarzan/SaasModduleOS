#!/usr/bin/env python3
"""
Audit de correspondance des routes API frontend <-> backend.

Compare chaque appel `api.get/post/put/patch/delete('/v1/...')` trouvé dans
frontend/src avec les routes reellement enregistrees dans la spec OpenAPI du
backend (endpoint /api-docs, protege par auth). Signale tout appel frontend
qui ne correspond a aucune route backend reelle (candidat 404/bug).

Usage:
    1. Demarrer le backend (profil dev).
    2. Recuperer un token valide (ex: via /v1/auth/login) et l'exporter:
         set TOKEN=...   (Windows)  /  export TOKEN=...  (bash)
    3. python scripts/audit_api_routes.py [--backend http://localhost:8080/api]

Limites connues (faux positifs a verifier manuellement) :
    - template literals avec interpolation collee sans "/" avant
      (ex: `/v1/x${params}` ou params contient deja "?query=...")
    - template literals imbriques avec ternaire (ex: `${cond ? `...` : ''}`)
    - variable de segment dont la valeur reelle correspond a un segment
      litteral cote backend (le script ne peut pas evaluer la valeur runtime)
"""
import argparse
import json
import os
import re
import sys
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
FRONTEND_SRC = os.path.join(REPO_ROOT, "frontend", "src")

METHODS = {"get", "post", "put", "patch", "delete"}
CALL_RE = re.compile(
    r"\bapi\.(get|post|put|patch|delete)\(\s*(`([^`]*)`|'([^']*)'|\"([^\"]*)\")"
)


def fetch_openapi_spec(backend_url, token):
    req = urllib.request.Request(f"{backend_url}/api-docs")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.load(resp)


def build_backend_routes(spec):
    routes = set()
    for path, methods in spec["paths"].items():
        segs = tuple(
            "*" if s.startswith("{") and s.endswith("}") else s
            for s in path.strip("/").split("/")
        )
        for m in methods:
            if m in METHODS:
                routes.add((m, segs))
    return routes


def normalize_frontend_path(raw):
    raw = raw.split("?")[0]
    raw = re.sub(r"\$\{[^}]*\}", "*", raw)
    raw = raw.strip()
    if not raw.startswith("/"):
        return None
    return tuple("*" if s == "*" else s for s in raw.strip("/").split("/"))


def scan_frontend_calls():
    results = []
    for root, dirs, files in os.walk(FRONTEND_SRC):
        if "__tests__" in root or "node_modules" in root:
            continue
        for fname in files:
            if not (fname.endswith(".ts") or fname.endswith(".tsx")):
                continue
            fpath = os.path.join(root, fname)
            with open(fpath, encoding="utf-8") as f:
                content = f.read()
            for m in CALL_RE.finditer(content):
                method = m.group(1)
                raw_path = m.group(3) or m.group(4) or m.group(5)
                if not raw_path or not raw_path.startswith("/v1"):
                    continue
                line_no = content[: m.start()].count("\n") + 1
                results.append({
                    "file": os.path.relpath(fpath, FRONTEND_SRC),
                    "line": line_no,
                    "method": method.upper(),
                    "path": raw_path,
                })
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", default="http://localhost:8080/api")
    args = parser.parse_args()

    token = os.environ.get("TOKEN")
    if not token:
        print("ATTENTION: variable d'env TOKEN absente, /api-docs pourrait refuser (401).", file=sys.stderr)

    spec = fetch_openapi_spec(args.backend, token)
    backend_routes = build_backend_routes(spec)

    calls = scan_frontend_calls()
    print(f"Scanned {len(calls)} api.* calls to /v1/* across frontend/src")

    mismatches = []
    for c in calls:
        segs = normalize_frontend_path(c["path"])
        if segs is None:
            continue
        match = (c["method"].lower(), segs) in backend_routes
        if not match:
            mismatches.append(c)

    print(f"\n{len(mismatches)} calls with NO matching backend route:\n")
    for r in mismatches:
        print(f"  [{r['method']}] {r['path']}  --  {r['file']}:{r['line']}")


if __name__ == "__main__":
    main()
