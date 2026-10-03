#!/usr/bin/env bash
set -euo pipefail
APP="./build/install/vericore/bin/vericore"
PORT="18080"
LOG="/tmp/vericore-rest-audit.log"
"$APP" server --host 127.0.0.1 --port "$PORT" >"$LOG" 2>&1 &
PID=$!
cleanup(){ kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; }
trap cleanup EXIT
for _ in $(seq 1 30); do if curl -fsS "http://127.0.0.1:${PORT}/health" >/tmp/vericore-health.json 2>/dev/null; then break; fi; sleep 1; done
curl -fsS "http://127.0.0.1:${PORT}/" >/tmp/vericore-root.json
curl -fsS "http://127.0.0.1:${PORT}/health" >/tmp/vericore-health.json
curl -fsS "http://127.0.0.1:${PORT}/health/live" >/tmp/vericore-live.json
curl -fsS "http://127.0.0.1:${PORT}/health/ready" >/tmp/vericore-ready.json
status=$(curl -sS -o /tmp/vericore-invalid.json -w '%{http_code}' -X POST "http://127.0.0.1:${PORT}/analyze" -H 'Content-Type: application/json' -d '{"repoPath":"https://github.com/example/example.git"}')
if [[ "$status" =~ ^2|^3 ]]; then echo "REST security audit failed: remote repository URL accepted" >&2; exit 1; fi
python3 - <<'PY'
import json
from pathlib import Path
root_text=Path('/tmp/vericore-root.json').read_text()
root=json.loads(root_text) if root_text.lstrip().startswith('{') else None
health=json.loads(Path('/tmp/vericore-health.json').read_text())
version_source=Path('src/main/kotlin/com/vericore/core/Version.kt').read_text()
expected=version_source.split('const val current: String = "', 1)[1].split('"', 1)[0]
assert health.get('status') == 'healthy'
assert health.get('version') == expected, (health.get('version'), expected)
PY
echo "REST audit: PASS"
