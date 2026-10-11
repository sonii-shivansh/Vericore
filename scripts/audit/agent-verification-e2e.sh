#!/usr/bin/env bash
set -euo pipefail

APP="${APP:-./build/install/vericore/bin/vericore}"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

mkdir -p "$FIXTURE/src/main/kotlin"
cat > "$FIXTURE/pom.xml" <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.vericore</groupId>
  <artifactId>v3-e2e-fixture</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
  </properties>
</project>
EOF
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
    payload = chr(10).join(json.dumps(r) for r in requests) + chr(10)
    p = subprocess.run([app, "mcp"], input=payload, text=True, capture_output=True, check=True)
    return [json.loads(line) for line in p.stdout.splitlines() if line.strip()]

prepare = mcp([{
    "jsonrpc":"2.0","id":1,"method":"initialize",
    "params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"v3-e2e","version":"1.0"}}
}, {
    "jsonrpc":"2.0","id":2,"method":"tools/call",
    "params":{"name":"vericore_prepare_change","arguments":{
        "repoPath":str(root),
        "changeSummary":"Update Target value safely",
        "plannedPaths":["src/main/kotlin/Target.kt"],
        "specification":{
            "requiredBehaviors":["Return the updated target value"],
            "forbiddenBehaviors":["Do not alter unrelated files"],
            "protectedInvariants":["Keep the public method signature stable"],
            "acceptanceCriteria":["The target value is updated by the requested operation"],
            "requiredEvidence":["Relevant unit test results"],
            "evidenceBindings":[
                {"criterionId":"acceptance-criterion-001","evidenceIds":["file.1"]},
                {"criterionId":"required-evidence-001","evidenceIds":["verification-command-001"],"rule":"ALL_BOUND_COMMANDS_SUCCEEDED"}
            ]
        }
    }}
}])

result = next(r["result"] for r in prepare if r.get("id")==2)
assert result["isError"] is False, result
prepared = json.loads(next(x["text"] for x in result["content"]))
assert prepared["contract"]["fingerprint"]
assert prepared["plan"]["plannedPaths"] == ["src/main/kotlin/Target.kt"]
assert prepared["plan"]["specification"]["requiredBehaviors"] == ["Return the updated target value"]
assert prepared["plan"]["specification"] == prepared["contract"]["specification"]
assert (root/"output/agent-change-contract.json").is_file()
assert (root/"output/engineering-plan.json").is_file()

plan = prepared["plan"]
(root/"src/main/kotlin/Target.kt").write_text(chr(10).join(["class Target {", "    fun value(): String = \"after\"", "}"]) + chr(10))

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
persisted_contract = json.loads(next(x["text"] for x in contract_result["content"]))
assert persisted_contract["fingerprint"] == prepared["contract"]["fingerprint"]
assert persisted_contract["specification"] == prepared["contract"]["specification"]
verify_result = next(r["result"] for r in verify if r.get("id")==5)
assert verify_result["isError"] is False, verify_result
verification = json.loads(next(x["text"] for x in verify_result["content"]))
assert verification["status"] == "REVIEW_REQUIRED", verification
evaluation = verification["specificationEvaluation"]
assert evaluation["status"] == "NOT_EVALUATED", evaluation
assert evaluation["notEvaluatedCount"] == 3 and evaluation["evaluatedCount"] == 2, evaluation
criterion_statuses = {item["id"]: item["status"] for item in evaluation["criteria"]}
assert criterion_statuses["acceptance-criterion-001"] == "INCONCLUSIVE", evaluation
assert criterion_statuses["required-evidence-001"] == "PASS", evaluation
assert all(status == "NOT_EVALUATED" for criterion_id, status in criterion_statuses.items()
           if criterion_id not in {"acceptance-criterion-001", "required-evidence-001"}), evaluation

# The CLI receipt must carry the same explicit evaluation and remain REVIEW_REQUIRED.
cli_verify = subprocess.run(
    [app, "verify", "--path", str(root)],
    cwd=root,
    text=True,
    capture_output=True
)
assert cli_verify.returncode == 0, cli_verify.stdout + chr(10) + cli_verify.stderr
receipt = json.loads((root/"output/verification-receipt.json").read_text())
assert receipt["schemaVersion"] == "1.2", receipt
assert receipt["status"] == "REVIEW_REQUIRED", receipt
assert receipt["specificationEvaluation"]["status"] == "NOT_EVALUATED", receipt
assert receipt["specificationEvaluation"] == evaluation, receipt

contract_only_path = root/"output/contract-only-verification.json"
contract_only_run = subprocess.run(
    [app, "verify", "--path", str(root), "--contract-only", "--output", str(contract_only_path)],
    cwd=root,
    text=True,
    capture_output=True
)
assert contract_only_run.returncode == 0, contract_only_run.stdout + chr(10) + contract_only_run.stderr
contract_only = json.loads(contract_only_path.read_text())
assert contract_only["status"] == "REVIEW_REQUIRED", contract_only
assert contract_only["specificationEvaluation"]["status"] == "NOT_EVALUATED", contract_only
contract_only_statuses = {
    item["id"]: item["status"] for item in contract_only["specificationEvaluation"]["criteria"]
}
assert contract_only_statuses["required-evidence-001"] == "NOT_EVALUATED", contract_only

print("V3-001 Agent Verification E2E: PASS")
PY
