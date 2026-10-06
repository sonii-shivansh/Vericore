#!/usr/bin/env bash
set -euo pipefail
APP="./build/install/vericore/bin/vericore"
mkdir -p output
"$APP" mcp-config > output/mcp-config.json
# Seed the persisted contract with the CLI prepare path. The MCP prepare tool
# returns an in-memory plan/contract but does not itself create this file.
"$APP" prepare "release audit MCP contract validation" --path "." >/dev/null
python3 - <<'PY'
import json
config=json.load(open('output/mcp-config.json'))
server=config['mcpServers']['vericore']
assert server == {'command':'vericore','args':['mcp']}
PY
printf '%s\n' \
'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"audit-lab","version":"1.0"}}}' \
'{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
'{"jsonrpc":"2.0","id":3,"method":"ping","params":{}}' \
'{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"vericore_get_engineering_reality","arguments":{"repoPath":"."}}}' \
'{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"vericore_get_context_snapshot","arguments":{"repoPath":"."}}}' \
'{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"vericore_prepare_change","arguments":{"repoPath":".","changeSummary":"release audit MCP contract validation"}}}' \
'{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"vericore_get_change_contract","arguments":{"repoPath":"."}}}' \
'{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"vericore_verify_change","arguments":{"repoPath":".","plan":{}}}}' \
| "$APP" mcp > output/mcp-audit.jsonl
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
for i in (4,5,6,7):
    result=by_id[i].get('result'); assert result and result.get('isError') is False
    assert ''.join(x.get('text','') for x in result.get('content',[]))
text=''.join(x.get('text','') for x in by_id[7]['result']['content']); contract=json.loads(text)
assert len(contract.get('fingerprint',''))==64 and contract.get('repository')
# verify_change must reject an invalid/empty plan rather than falsely report success.
verify=by_id[8]
assert verify.get('error',{}).get('code') == -32602 or verify.get('result',{}).get('isError') is True
PY
echo "MCP audit: PASS"
