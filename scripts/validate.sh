#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for file in scripts/*.sh load-testing/*.sh; do bash -n "$file"; done
python3 -m py_compile scripts/capture-evidence.py
python3 -m json.tool docs/dashboard.json >/dev/null
echo "Sintaxis Bash/Python y JSON correctos"
