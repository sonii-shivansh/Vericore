#!/usr/bin/env bash
set -euo pipefail

# Local/Codespaces entrypoint for the deterministic release audit for the current version.
# GitHub Actions runs the same individual audit scripts plus its own live gate.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

chmod +x ./gradlew scripts/audit/*.sh
./gradlew --no-daemon clean test installDist
bash scripts/audit/cli-audit.sh
bash scripts/audit/mcp-audit.sh
bash scripts/audit/rest-audit.sh

printf '%s\n' 'Release audit local: PASS'
