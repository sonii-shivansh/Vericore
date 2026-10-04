#!/usr/bin/env bash
set -euo pipefail
APP="./build/install/vericore/bin/vericore"
mkdir -p output
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"audit-lab","version":"1.0"}}}' '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' '{"jsonrpc":"2.0","id":3,"method":"ping","params":{}}' '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"vericore_get_engineering_reality","arguments":{"repoPath":"."}}}' '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"vericore_get_context_snapshot","arguments":{"repoPath":"."}}}' '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"vericore_get_change_contract","arguments":{"repoPath":"."}}}' | "$APP" mcp > output/mcp-audit.jsonl
python3 - <<'PY'
import json
responses=[json.loads(line) for line in open('output/mcp-audit.jsonl') if line.strip()]
by_id={x.get('id'):x for x in responses}
assert by_id[1].get('result',{}).get('protocolVersion')
assert by_id[3].get('result') == {}
tools=by_id[2].get('result',{}).get('tools',[])
names={t.get('name') for t in tools}
required={'vericore_analyze_repository','vericore_impact_analysis','vericore_get_engineering_reality','vericore_get_context_snapshot','vericore_get_change_contract','vericore_prepare_change','vericore_verify_change'}
assert not required-names, required-names
assert not any(name.startswith('codecontext_') for name in names)
for i in (4,5,6):
    result=by_id[i].get('result'); assert result and result.get('isError') is False
    assert ''.join(x.get('text','') for x in result.get('content',[]))
text=''.join(x.get('text','') for x in by_id[6]['result']['content']); contract=json.loads(text)
assert len(contract.get('fingerprint',''))==64 and contract.get('repository')
PY

prepare_fixture="$(mktemp -d)"
trap 'rm -rf "$prepare_fixture"' EXIT
mkdir -p "$prepare_fixture/src"
printf '%s\n' 'class App' > "$prepare_fixture/src/App.kt"
git -C "$prepare_fixture" init -q
git -C "$prepare_fixture" config user.name "Vericore MCP Audit"
git -C "$prepare_fixture" config user.email "vericore-mcp-audit@example.invalid"
git -C "$prepare_fixture" add .
git -C "$prepare_fixture" commit -qm "baseline"
python3 - "$prepare_fixture" <<'PY' | "$APP" mcp > output/mcp-prepare-audit.jsonl
import json
import sys
repo=sys.argv[1]
def call(i,name,args):
    return {"jsonrpc":"2.0","id":i,"method":"tools/call","params":{"name":name,"arguments":args}}
print(json.dumps({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"audit","version":"1.0"}}}))
print(json.dumps(call(2,"vericore_prepare_change",{"repoPath":repo,"changeSummary":"update App"})))
print(json.dumps(call(3,"vericore_get_change_contract",{"repoPath":repo})))
PY
test -s "$prepare_fixture/output/engineering-context.json"
test -s "$prepare_fixture/output/engineering-preparation.json"
test -s "$prepare_fixture/output/agent-change-contract.json"
python3 - "$prepare_fixture/output/engineering-context.json" <<'PY'
import json, sys
snapshot=json.load(open(sys.argv[1]))
assert snapshot["schemaVersion"] == "1.0"
assert "snapshotDigest" in snapshot
PY
python3 - "$prepare_fixture/output/agent-change-contract.json" <<'PY'
import json, sys
p=json.load(open(sys.argv[1]))
assert len(p.get("fingerprint","")) == 64
assert p["repository"]
PY
python3 - "output/mcp-prepare-audit.jsonl" "$prepare_fixture/output/agent-change-contract.json" <<'PY'
import json, sys
rows=[json.loads(x) for x in open(sys.argv[1]) if x.strip()]
by={r["id"]:r for r in rows}
result=by[3]["result"]
assert result["isError"] is False
returned=json.loads("".join(x.get("text","") for x in result["content"]))
persisted=json.load(open(sys.argv[2]))
assert returned == persisted
PY

echo "MCP audit: PASS"
