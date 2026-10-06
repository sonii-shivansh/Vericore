#!/usr/bin/env bash
set -euo pipefail

APP="${APP:-./build/install/vericore/bin/vericore}"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

mkdir -p "$FIXTURE/src/main/kotlin"
cat > "$FIXTURE/src/main/kotlin/Target.kt" <<'EOF'
class Target {
    fun value(): String = "before"
}
EOF

cd "$FIXTURE"
git init -q
git config user.name "Vericore V3 E2E"
git config user.email "v3-e2e@example.com"
git add .
git commit -qm "initial fixture"

python3 - "$APP" <<'PY'
import json, pathlib, subprocess, sys

app = sys.argv[1]
root = pathlib.Path.cwd().resolve()

def mcp(requests):
    payload = "\n".join(json.dumps(r) for r in requests) + "\n"
    p = subprocess.run([app, "mcp"], input=payload, text=True, capture_output=True, check=True)
    return [json.loads(line) for line in p.stdout.splitlines() if line.strip()]

prepare = mcp([{
    "jsonrpc":"2.0","id":1,"method":"initialize",
    "params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"v3-e2e","version":"1.0"}}
}, {
    "jsonrpc":"2.0","id":2,"method":"tools/call",
    "params":{"name":"vericore_prepare_change","arguments":{
        "repoPath":str(root),
        "changeSummary":"Update Target value safely","plannedPaths":["src/main/kotlin/Target.kt"]
    }}
}])

result = next(r["result"] for r in prepare if r.get("id")==2)
assert result["isError"] is False, result
prepared = json.loads(next(x["text"] for x in result["content"]))
assert prepared["contract"]["fingerprint"]\nassert prepared["plan"]["plannedPaths"] == ["src/main/kotlin/Target.kt"]
assert (root/"output/agent-change-contract.json").is_file()
assert (root/"output/engineering-plan.json").is_file()

plan = prepared["plan"]
(root/"src/main/kotlin/Target.kt").write_text('class Target {\n    fun value(): String = "after"\n}\n')

verify = mcp([{
    "jsonrpc":"2.0","id":3,"method":"initialize",
    "params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"v3-e2e","version":"1.0"}}
}, {
    "jsonrpc":"2.0","id":4,"method":"tools/call",
    "params":{"name":"vericore_get_change_contract","arguments":{"repoPath":str(root)}}
}, {
    "jsonrpc":"2.0","id":5,"method":"tools/call",
    "params":{"name":"vericore_verify_change","arguments":{"repoPath":str(root),"plan":plan}}
}])

contract_result = next(r["result"] for r in verify if r.get("id")==4)
assert contract_result["isError"] is False, contract_result
verify_result = next(r["result"] for r in verify if r.get("id")==5)
assert verify_result["isError"] is False, verify_result
verification = json.loads(next(x["text"] for x in verify_result["content"]))
assert verification["status"] in {"PASS","REVIEW_REQUIRED"}, verification

print("V3-001 Agent Verification E2E: PASS")
PY
