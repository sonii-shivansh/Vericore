#!/usr/bin/env bash
set -euo pipefail

CLI=${CLI:?CLI must point to the installed Vericore executable}
REPO=${1:?repository path required}
OUT="${2:-$REPO/output/production-e2e}"
mkdir -p "$OUT"
export OUT

cd "$REPO"

capture() {
  local name="$1"; shift
  echo "===== $name ====="
  set +e
  "$@" >"$OUT/$name.stdout" 2>"$OUT/$name.stderr"
  local rc=$?
  set -e
  printf '%s\n' "$rc" >"$OUT/$name.exit"
  echo "exit=$rc"
  return "$rc"
}

expect_success() {
  capture "$@" || {
    echo "Expected success but command failed: $1" >&2
    return 1
  }
}

expect_failure() {
  local name="$1"; shift
  set +e
  "$@" >"$OUT/$name.stdout" 2>"$OUT/$name.stderr"
  local rc=$?
  set -e
  printf '%s\n' "$rc" >"$OUT/$name.exit"
  test "$rc" -ne 0 || {
    echo "Expected failure but command succeeded: $name" >&2
    return 1
  }
}

expect_success 00-version "$CLI" --version
expect_success 01-help "$CLI" --help

commands=(
  analyze impact architecture architecture-drift architecture-contract
  context-snapshot context-diff evidence-graph reality pr-intelligence
  repo-qa plan prepare verify ask evolution server mcp mcp-config setup doctor
)
for i in "${!commands[@]}"; do
  expect_success "help-$(printf '%02d' "$((i+2))")-${commands[$i]}" "$CLI" "${commands[$i]}" --help
done

expect_success 20-doctor "$CLI" doctor --path "$REPO"
expect_success 21-analyze "$CLI" analyze "$REPO" --clear-cache

python3 - "$REPO" "$OUT" <<'PY'
import json, pathlib, sys
repo, out = map(pathlib.Path, sys.argv[1:])
required = [
    repo/"output/index.html",
    repo/"output/analysis-snapshot.json",
    repo/"output/engineering-risks.json",
]
for p in required:
    assert p.is_file() and p.stat().st_size > 0, p
json.loads((repo/"output/analysis-snapshot.json").read_text())
json.loads((repo/"output/engineering-risks.json").read_text())
PY

SOURCE_FILE="$(git -C "$REPO" ls-files '*.java' '*.kt' | head -1)"
test -n "$SOURCE_FILE"

expect_success 22-evidence-graph "$CLI" evidence-graph "$REPO" --json
expect_success 23-reality "$CLI" reality "$REPO" --json
expect_success 24-architecture "$CLI" architecture "$REPO" --json
cp "$REPO/output/architecture.json" "$REPO/output/architecture-baseline.json"
expect_success 25-architecture-drift "$CLI" architecture-drift "$REPO" --baseline "$REPO/output/architecture-baseline.json" --json

cat > "$REPO/.vericore-architecture-contract.json" <<'JSON'
{"schemaVersion":"1.0","maxFindings":100000,"maxCycles":100000,"allowedSeverities":["LOW","MEDIUM","HIGH","CRITICAL"],"requiredRules":[]}
JSON
expect_success 26-architecture-contract "$CLI" architecture-contract "$REPO" --json
rm -f "$REPO/.vericore-architecture-contract.json"

expect_success 27-context-snapshot "$CLI" context-snapshot "$REPO" --json
cp "$REPO/output/engineering-context.json" "$REPO/output/context-before.json"
expect_success 28-context-snapshot-repeat "$CLI" context-snapshot "$REPO" --json
cp "$REPO/output/engineering-context.json" "$REPO/output/context-after.json"
expect_success 29-context-diff "$CLI" context-diff "$REPO/output/context-before.json" "$REPO/output/context-after.json" --json
expect_success 30-pr-intelligence "$CLI" pr-intelligence "$REPO" --json
expect_success 31-evolution "$CLI" evolution "$REPO" --months 1 --interval 30
expect_success 32-impact "$CLI" impact "$REPO" "$SOURCE_FILE" --json
expect_success 33-repo-qa "$CLI" repo-qa "Which files are the main architectural hotspots?" --path "$REPO" --max-results 8 --evidence-output "$REPO/output/grounded-evidence.json"
expect_success 34-plan "$CLI" plan "Safely improve the selected repository component" --repository "$REPO" --changed "$SOURCE_FILE" --evidence "$REPO/output/grounded-evidence.json" --output "$REPO/output/engineering-plan.json"

python3 - "$REPO" <<'PY'
import json, pathlib, sys
r=pathlib.Path(sys.argv[1])
for name in ["architecture.json","architecture-drift.json","architecture-contract.json",
             "engineering-context.json","engineering-context-diff.json",
             "engineering-reality.json","pr-intelligence.json","change-impact.json",
             "grounded-evidence.json","engineering-plan.json"]:
    p=r/"output"/name
    assert p.is_file() and p.stat().st_size > 0, name
    json.loads(p.read_text())
PY

mkdir -p "$REPO/output/agent-e2e"
expect_success 35-prepare "$CLI" prepare "Add a harmless verification marker to the selected source file"   --path "$REPO"   --planned-path "$SOURCE_FILE"   --plan-output "$REPO/output/agent-e2e/engineering-plan.json"   --contract-output "$REPO/output/agent-e2e/agent-change-contract.json"   --output "$REPO/output/agent-e2e/engineering-context.json"

cp "$REPO/$SOURCE_FILE" "$OUT/original-source"
printf '\n// Vericore production E2E verification marker\n' >> "$REPO/$SOURCE_FILE"
expect_success 36-verify-planned "$CLI" verify --path "$REPO"   --plan "$REPO/output/agent-e2e/engineering-plan.json"   --contract "$REPO/output/agent-e2e/agent-change-contract.json"   --output "$REPO/output/agent-e2e/verification.json"

python3 - "$REPO/output/agent-e2e/verification.json" <<'PY'
import json, sys
r=json.load(open(sys.argv[1]))
assert r["status"] in {"PASS","REVIEW_REQUIRED"}, r
assert r["verification"]["executionComplete"] is True, r
PY

cp "$OUT/original-source" "$REPO/$SOURCE_FILE"
git -C "$REPO" status --short

expect_success 37-verify-clean "$CLI" verify --path "$REPO"   --plan "$REPO/output/agent-e2e/engineering-plan.json"   --contract "$REPO/output/agent-e2e/agent-change-contract.json"   --output "$REPO/output/agent-e2e/verification-clean.json"

printf '%s\n' 'unexpected mutation' > "$REPO/.vericore-production-e2e-unexpected"
expect_failure 38-verify-unexpected "$CLI" verify --path "$REPO"   --plan "$REPO/output/agent-e2e/engineering-plan.json"   --contract "$REPO/output/agent-e2e/agent-change-contract.json"
rm -f "$REPO/.vericore-production-e2e-unexpected"
grep -Eq 'FAIL|Verification failed' "$OUT/38-verify-unexpected.stderr" "$OUT/38-verify-unexpected.stdout"

expect_success 39-mcp-config "$CLI" mcp-config

python3 - "$CLI" "$REPO" "$OUT" <<'PY'
import json, pathlib, subprocess, sys
cli, repo, out = map(pathlib.Path, sys.argv[1:])
def rpc(requests, name):
    payload="\n".join(json.dumps(x) for x in requests)+"\n"
    p=subprocess.run([str(cli),"mcp"],input=payload,text=True,capture_output=True,check=True)
    (out/f"{name}.stdout").write_text(p.stdout)
    (out/f"{name}.stderr").write_text(p.stderr)
    return {x.get("id"):x for x in (json.loads(line) for line in p.stdout.splitlines() if line.strip())}
responses=rpc([
 {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"production-e2e","version":"1.0"}}},
 {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}},
 {"jsonrpc":"2.0","id":3,"method":"ping","params":{}},
], "40-mcp-protocol")
assert responses[1]["result"]["protocolVersion"]
assert responses[3]["result"] == {}
names={x["name"] for x in responses[2]["result"]["tools"]}
required={
"vericore_analyze_repository","vericore_impact_analysis","vericore_architecture_analysis",
"vericore_pr_intelligence","vericore_get_engineering_reality","vericore_get_context_snapshot",
"vericore_get_context_diff","vericore_get_architecture_drift","vericore_get_architecture_contract",
"vericore_prepare_change","vericore_get_change_contract","vericore_get_evidence",
"vericore_change_safety","vericore_verify_change"
}
assert required <= names, sorted(required-names)
assert not any(n.startswith("codecontext_") for n in names)
PY

"$CLI" server --host 127.0.0.1 --port 18080 >"$OUT/41-rest-server.log" 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT
for _ in $(seq 1 30); do
  curl -fsS http://127.0.0.1:18080/health >"$OUT/41-rest-health.json" && break
  sleep 1
done
curl -fsS http://127.0.0.1:18080/ >"$OUT/41-rest-root.txt"
curl -fsS http://127.0.0.1:18080/health/live >"$OUT/41-rest-live.json"
curl -fsS http://127.0.0.1:18080/health/ready >"$OUT/41-rest-ready.json"
curl -fsS -H 'Content-Type: application/json' -d '{"repoPath":"'"$REPO"'"}'   http://127.0.0.1:18080/architecture >"$OUT/41-rest-architecture.json"
status="$(curl -sS -o "$OUT/41-rest-remote.json" -w '%{http_code}'   -X POST http://127.0.0.1:18080/analyze   -H 'Content-Type: application/json'   -d '{"repoPath":"https://github.com/example/example.git"}')"
[[ "$status" != 2* && "$status" != 3* ]]
kill "$SERVER_PID" 2>/dev/null || true
trap - EXIT

python3 - "$REPO" "$OUT" <<'PY'
import json, pathlib, sys
repo,out=map(pathlib.Path,sys.argv[1:])
health=json.loads((out/"41-rest-health.json").read_text())
assert health.get("status")=="healthy"
assert health.get("version")
for n in ["41-rest-live.json","41-rest-ready.json","41-rest-architecture.json"]:
    json.loads((out/n).read_text())
assert "CodeContext" not in (out/"41-rest-root.txt").read_text()
PY

python3 - "$REPO" "$OUT" <<'PY'
import json, pathlib, sys
repo,out=map(pathlib.Path,sys.argv[1:])
for p in out.glob("*.stdout"):
    assert p.read_text().strip(), p
for p in repo.joinpath("output").glob("*.json"):
    json.loads(p.read_text())
assert "CodeContext" not in "\n".join(p.read_text(errors="ignore") for p in out.glob("*"))
PY

test -z "$(git -C "$REPO" diff --name-only)"
test -z "$(git -C "$REPO" diff --cached --name-only)"
test -z "$(git -C "$REPO" ls-files --others --exclude-standard | grep -Ev '^(output/|\.vericore/)' || true)"

echo "Production E2E audit: PASS"
