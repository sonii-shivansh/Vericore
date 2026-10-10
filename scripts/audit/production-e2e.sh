#!/usr/bin/env bash
set -euo pipefail

CLI=${CLI:?CLI must point to the installed Vericore executable}
REPO=${1:?repository path required}
# Canonicalize once so the allowlist roots and every REST request use the same
# absolute path, even when the caller supplies a relative path or symlink.
REPO="$(cd "$REPO" && pwd -P)"
OUT="${2:-$REPO/output/production-e2e}"
COMMAND_TIMEOUT_SECONDS="${VERICORE_E2E_COMMAND_TIMEOUT_SECONDS:-180}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
export OUT

cd "$REPO"

# The Kotlin compiler repository has a large committed compiler test-data corpus.
# Exclude those fixture directories for this live target while retaining the normal
# 50k source-file safety limit.
KOTLIN_CONFIG_CREATED=false
if [[ "${TARGET_REPOSITORY:-}" == "google/kotlin" && ! -e "$REPO/.vericore.json" ]]; then
  cat > "$REPO/.vericore.json" <<'JSON'
{
  "excludePaths": [".git", ".idea", ".gradle", "build", "target", "node_modules", ".vscode", "out", "dist", ".next", "testData", "testdata"],
  "maxFilesAnalyze": 50000
}
JSON
  KOTLIN_CONFIG_CREATED=true
fi
cleanup_kotlin_config() {
  if [[ "$KOTLIN_CONFIG_CREATED" == true ]]; then
    rm -f "$REPO/.vericore.json"
  fi
}
trap cleanup_kotlin_config EXIT

capture() {
  local name="$1"; shift
  echo "===== $name ====="
  set +e
  timeout --signal=TERM --kill-after=15s "${COMMAND_TIMEOUT_SECONDS}s" "$@" >"$OUT/$name.stdout" 2>"$OUT/$name.stderr"
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
  timeout --signal=TERM --kill-after=15s "${COMMAND_TIMEOUT_SECONDS}s" "$@" >"$OUT/$name.stdout" 2>"$OUT/$name.stderr"
  local rc=$?
  set -e
  printf '%s\n' "$rc" >"$OUT/$name.exit"
  test "$rc" -ne 124 || {
    echo "Command timed out after ${COMMAND_TIMEOUT_SECONDS}s: $name" >&2
    return 1
  }
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

# Avoid piping the complete tracked-file list into `head`: with pipefail enabled,
# Git can receive SIGPIPE (exit 141) after head exits early. This used to make
# otherwise-successful large-repository E2E runs fail before impact/prepare/verify.
SOURCE_LIST="$OUT/source-files.list"
git -C "$REPO" ls-files -z -- '*.java' '*.kt' > "$SOURCE_LIST"
SOURCE_FILE="$(python3 - "$SOURCE_LIST" <<'PY'
import os, pathlib, sys
paths = pathlib.Path(sys.argv[1]).read_bytes().split(b"\0")
first = next((p for p in paths if p), b"")
print(os.fsdecode(first))
PY
)"
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
# The temporary Kotlin fixture-exclusion config is needed during analysis, but it
# must not appear as an unplanned working-tree change during contract verification.
if [[ "${TARGET_REPOSITORY:-}" == "google/kotlin" ]]; then
  cleanup_kotlin_config
  KOTLIN_CONFIG_CREATED=false
  # Removing the temporary analysis-exclusion config changes repository state.
  # Rebuild evidence after cleanup so prepare binds to the exact state it sees.
  expect_success 34a-reanalyze-clean-state "$CLI" analyze "$REPO" --clear-cache
fi
expect_success 35-prepare "$CLI" prepare "Add a harmless verification marker to the selected source file"   --path "$REPO"   --planned-path "$SOURCE_FILE"   --plan-output "$REPO/output/agent-e2e/engineering-plan.json"   --contract-output "$REPO/output/agent-e2e/agent-change-contract.json"   --output "$REPO/output/agent-e2e/engineering-preparation.json"

cp "$REPO/$SOURCE_FILE" "$OUT/original-source"
printf '\n// Vericore production E2E verification marker\n' >> "$REPO/$SOURCE_FILE"

# The Kotlin compiler and Quarkus monorepos' full analysis graphs exceed a practical
# live-smoke budget when combined with their generated build/test commands. Contract-
# only mode still checks persisted plan/contract fingerprints, repository identity,
# prepared HEAD, and exact planned mutation scope. Petclinic and RuneLite retain the
# full verification path, including execution of declared verification commands.
VERIFY_MODE_ARGS=()
if [[ "${TARGET_REPOSITORY:-}" == "google/kotlin" || "${TARGET_REPOSITORY:-}" == "quarkusio/quarkus" ]]; then
  VERIFY_MODE_ARGS+=(--contract-only)
fi
expect_success 36-verify-planned "$CLI" verify "${VERIFY_MODE_ARGS[@]}" --path "$REPO"   --plan "$REPO/output/agent-e2e/engineering-plan.json"   --contract "$REPO/output/agent-e2e/agent-change-contract.json"   --output "$REPO/output/agent-e2e/verification.json"

python3 - "$REPO/output/agent-e2e/verification.json" <<'PY'
import json, os, sys
r=json.load(open(sys.argv[1]))
if os.environ.get("TARGET_REPOSITORY") in {"google/kotlin", "quarkusio/quarkus"}:
    assert r["status"] == "PASS" and r["valid"] is True, r
    assert r["changedPaths"], r
else:
    assert r["status"] in {"PASS","REVIEW_REQUIRED"}, r
    assert r["verification"]["executionComplete"] is True, r
PY

cp "$OUT/original-source" "$REPO/$SOURCE_FILE"
git -C "$REPO" status --short

# A clean tree after prepare is an expected fail-closed case: there is no planned
# source mutation to verify. This must not be mistaken for a successful verify.
expect_failure 37-verify-without-mutation "$CLI" verify "${VERIFY_MODE_ARGS[@]}" --path "$REPO"   --plan "$REPO/output/agent-e2e/engineering-plan.json"   --contract "$REPO/output/agent-e2e/agent-change-contract.json"   --output "$REPO/output/agent-e2e/verification-no-mutation.json"
grep -Eq 'No source working-tree changes|Status: FAIL|status.*FAIL'   "$OUT/37-verify-without-mutation.stderr" "$OUT/37-verify-without-mutation.stdout"   "$REPO/output/agent-e2e/verification-no-mutation.json"

printf '%s\n' 'unexpected mutation' > "$REPO/.vericore-production-e2e-unexpected"
expect_failure 38-verify-unexpected "$CLI" verify "${VERIFY_MODE_ARGS[@]}" --path "$REPO"   --plan "$REPO/output/agent-e2e/engineering-plan.json"   --contract "$REPO/output/agent-e2e/agent-change-contract.json"
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
"vericore_pr_intelligence","vericore_review","vericore_recommendations","vericore_get_engineering_reality","vericore_get_context_snapshot",
"vericore_get_context_diff","vericore_get_architecture_drift","vericore_get_architecture_contract",
"vericore_prepare_change","vericore_get_change_contract","vericore_get_evidence",
"vericore_change_safety","vericore_verify_change"
}
assert required <= names, sorted(required-names)
assert not any(n.startswith("codecontext_") for n in names)
review=next(t for t in responses[2]["result"]["tools"] if t["name"]=="vericore_review")
props=review["inputSchema"]["properties"]
assert props["includeAi"]["type"]=="boolean"
PY

# Keep the REST audit's allowlist limited to the canonical target checkout.
# The failure being guarded here is request/configuration behavior, not a reason
# to widen the application's filesystem trust boundary.
export VERICORE_ALLOWED_PATHS="$REPO"

# Kotlin's compiler corpus needs its fixture exclusions during REST analysis too.
# The earlier cleanup is required while testing planned source mutations, so
# restore this temporary config only for the REST request and remove it afterward.
if [[ "${TARGET_REPOSITORY:-}" == "google/kotlin" && ! -e "$REPO/.vericore.json" ]]; then
  cat > "$REPO/.vericore.json" <<'JSON'
{
  "excludePaths": [".git", ".idea", ".gradle", "build", "target", "node_modules", ".vscode", "out", "dist", ".next", "testData", "testdata"],
  "maxFilesAnalyze": 50000
}
JSON
  KOTLIN_CONFIG_CREATED=true
fi

"$CLI" server --host 127.0.0.1 --port 18080 >"$OUT/41-rest-server.log" 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true; cleanup_kotlin_config' EXIT
for _ in $(seq 1 30); do
  curl -fsS http://127.0.0.1:18080/health >"$OUT/41-rest-health.json" && break
  sleep 1
done
curl -fsS http://127.0.0.1:18080/ >"$OUT/41-rest-root.txt"
curl -fsS http://127.0.0.1:18080/health/live >"$OUT/41-rest-live.json"
curl -fsS http://127.0.0.1:18080/health/ready >"$OUT/41-rest-ready.json"
set +e
architecture_status="$(curl -sS -o "$OUT/41-rest-architecture.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"repoPath":"'"$REPO"'"}' \
  http://127.0.0.1:18080/architecture)"
architecture_curl_rc=$?
set -e
if [[ "$architecture_curl_rc" -ne 0 || "$architecture_status" != "200" ]]; then
  echo "REST /architecture expected HTTP 200; curl_exit=$architecture_curl_rc http_status=$architecture_status" >&2
  cat "$OUT/41-rest-architecture.json" >&2 || true
  cat "$OUT/41-rest-server.log" >&2 || true
  exit 1
fi
status="$(curl -sS -o "$OUT/41-rest-remote.json" -w '%{http_code}'   -X POST http://127.0.0.1:18080/analyze   -H 'Content-Type: application/json'   -d '{"repoPath":"https://github.com/example/example.git"}')"
# This is an intentional negative security test: curl must capture, not fail on,
# the HTTP response. Assert the precise rejection and its documented error.
[[ "$status" == "400" ]]
python3 - "$OUT/41-rest-remote.json" <<'PY'
import json, pathlib, sys
response=json.loads(pathlib.Path(sys.argv[1]).read_text())
assert "Remote repositories are not supported" in response.get("error", response.get("message", "")), response
PY
kill "$SERVER_PID" 2>/dev/null || true
cleanup_kotlin_config
KOTLIN_CONFIG_CREATED=false
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

cleanup_kotlin_config
KOTLIN_CONFIG_CREATED=false
trap - EXIT

test -z "$(git -C "$REPO" diff --name-only)"
test -z "$(git -C "$REPO" diff --cached --name-only)"
test -z "$(git -C "$REPO" ls-files --others --exclude-standard | grep -Ev '^(output/|\.vericore/)' || true)"

echo "Production E2E audit: PASS"
