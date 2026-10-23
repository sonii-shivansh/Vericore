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
cat > "$FIXTURE/.gitignore" <<'EOF'
baseline-drift.secret
EOF

cd "$FIXTURE"
git init -q
git config user.name "Vericore V3 E2E"
git config user.email "v3-e2e@example.com"
git add .
git commit -qm "initial fixture"

python3 - "$APP" <<'PY'
import json, os, pathlib, subprocess, sys

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
        "agentContext":{"provider":" Cursor ","agentId":"agent-e2e-1","sessionId":"session-e2e-1","eventId":"event-e2e-1"},
        "specification":{
            "requiredBehaviors":["Return the updated target value"],
            "forbiddenBehaviors":["Do not alter unrelated files"],
            "protectedInvariants":["Keep the public method signature stable"],
            "acceptanceCriteria":["The target value is updated by the requested operation"],
            "requiredEvidence":["Relevant unit test results"],
            "evidenceBindings":[
                {"criterionId":"acceptance-criterion-001","evidenceIds":["file.1"]}
            ]
        }
    }}
}])

result = next(r["result"] for r in prepare if r.get("id")==2)
assert result["isError"] is False, result
prepared = json.loads(next(x["text"] for x in result["content"]))
assert prepared["contract"]["fingerprint"]
assert prepared["contract"]["schemaVersion"] == "3.1", prepared["contract"]
assert prepared["contract"]["agentContext"] == {
    "schemaVersion":"1.0","provider":"cursor","agentId":"agent-e2e-1",
    "sessionId":"session-e2e-1","eventId":"event-e2e-1","source":"mcp"
}, prepared["contract"]
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
assert evaluation["notEvaluatedCount"] == 4 and evaluation["evaluatedCount"] == 1, evaluation
criterion_statuses = {item["id"]: item["status"] for item in evaluation["criteria"]}
assert criterion_statuses["acceptance-criterion-001"] == "INCONCLUSIVE", evaluation
assert all(status == "NOT_EVALUATED" for criterion_id, status in criterion_statuses.items()
           if criterion_id != "acceptance-criterion-001"), evaluation

# The CLI receipt must carry the same explicit evaluation and remain REVIEW_REQUIRED.
cli_verify = subprocess.run(
    [app, "verify", "--path", str(root)],
    cwd=root,
    text=True,
    capture_output=True
)
assert cli_verify.returncode == 0, cli_verify.stdout + chr(10) + cli_verify.stderr
receipt = json.loads((root/"output/verification-receipt.json").read_text())
assert receipt["schemaVersion"] == "1.5", receipt
assert receipt["agentContext"] == prepared["contract"]["agentContext"], receipt
assert receipt["preparedBaselineDigest"] and len(receipt["preparedBaselineDigest"]) == 64, receipt
assert receipt["baselineManifestValid"] is True, receipt
assert receipt["unplannedBaselineDriftPaths"] == [], receipt
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

# An ignored untracked file is not reported by Git's standard changed-file listing,
# but it is still a candidate input and must fail the prepare-time baseline check.
ignored_drift = root/"baseline-drift.secret"
ignored_drift.write_text("fixture drift\n")
ignore_check = subprocess.run(
    ["git", "check-ignore", "baseline-drift.secret"], cwd=root, text=True, capture_output=True
)
assert ignore_check.returncode == 0, ignore_check.stdout + chr(10) + ignore_check.stderr
drift_path = root/"output/baseline-drift-verification.json"
drift_run = subprocess.run(
    [app, "verify", "--path", str(root), "--contract-only", "--output", str(drift_path)],
    cwd=root, text=True, capture_output=True
)
assert drift_run.returncode != 0, drift_run.stdout + chr(10) + drift_run.stderr
drift_verification = json.loads(drift_path.read_text())
assert drift_verification["status"] == "FAIL", drift_verification
assert "baseline-drift.secret" in drift_verification["changedPaths"], drift_verification
ignored_drift.unlink()

# Exercise the CLI adapter boundary after the MCP flow has been fully audited.
cli_prepare = subprocess.run(
    [app, "prepare", "Record CLI agent context", "--path", str(root),
     "--planned-path", "src/main/kotlin/Target.kt", "--agent-provider", "Codex",
     "--agent-id", "cli-agent-1", "--agent-session-id", "cli-session-1",
     "--agent-event-id", "cli-event-1"],
    cwd=root, text=True, capture_output=True
)
assert cli_prepare.returncode == 0, cli_prepare.stdout + chr(10) + cli_prepare.stderr
cli_contract = json.loads((root/"output/agent-change-contract.json").read_text())
assert cli_contract["schemaVersion"] == "3.1", cli_contract
expected_cli_source = "github-action" if os.environ.get("GITHUB_ACTIONS") == "true" else "cli"
assert cli_contract["agentContext"] == {
    "schemaVersion":"1.0","provider":"codex","agentId":"cli-agent-1",
    "sessionId":"cli-session-1","eventId":"cli-event-1","source":expected_cli_source
}, cli_contract


# Exercise supported metric criteria through preparation, real candidate analysis,
# MCP verification, the CLI receipt, and contract-only verification.
metric_prepare = mcp([{
    "jsonrpc":"2.0","id":6,"method":"initialize",
    "params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"v3-metric-e2e","version":"1.0"}}
}, {
    "jsonrpc":"2.0","id":7,"method":"tools/call",
    "params":{"name":"vericore_prepare_change","arguments":{
        "repoPath":str(root),
        "changeSummary":"Verify candidate repository metrics deterministically",
        "plannedPaths":["src/main/kotlin/Target.kt"],
        "specification":{
            "acceptanceCriteria":[
                "metric:repo.metrics.files>=1",
                "metric:repo.metrics.files==0"
            ],
            "evidenceBindings":[
                {"criterionId":"acceptance-criterion-001","evidenceIds":["repo.metrics"]},
                {"criterionId":"acceptance-criterion-002","evidenceIds":["repo.metrics"]}
            ]
        }
    }}
}])
metric_prepare_result = next(r["result"] for r in metric_prepare if r.get("id")==7)
assert metric_prepare_result["isError"] is False, metric_prepare_result
metric_prepared = json.loads(next(x["text"] for x in metric_prepare_result["content"]))
metric_plan = metric_prepared["plan"]
assert metric_prepared["contract"]["specification"]["evidenceBindings"] == [
    {"criterionId":"acceptance-criterion-001","evidenceIds":["repo.metrics"],"rule":"MANUAL_REVIEW"},
    {"criterionId":"acceptance-criterion-002","evidenceIds":["repo.metrics"],"rule":"MANUAL_REVIEW"}
], metric_prepared["contract"]

metric_verify = mcp([{
    "jsonrpc":"2.0","id":8,"method":"initialize",
    "params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"v3-metric-e2e","version":"1.0"}}
}, {
    "jsonrpc":"2.0","id":9,"method":"tools/call",
    "params":{"name":"vericore_verify_change","arguments":{"repoPath":str(root),"plan":metric_plan}}
}])
metric_verify_result = next(r["result"] for r in metric_verify if r.get("id")==9)
assert metric_verify_result["isError"] is False, metric_verify_result
metric_verification = json.loads(next(x["text"] for x in metric_verify_result["content"]))
metric_evaluation = metric_verification["specificationEvaluation"]
metric_statuses = {item["id"]: item["status"] for item in metric_evaluation["criteria"]}
assert metric_statuses == {
    "acceptance-criterion-001":"PASS",
    "acceptance-criterion-002":"FAIL"
}, metric_evaluation
assert metric_evaluation["status"] == "FAIL", metric_evaluation
assert metric_verification["status"] == "FAIL", metric_verification

# A passing metric criterion must not conceal a failing criterion in the final receipt.
cli_metric_verify = subprocess.run(
    [app, "verify", "--path", str(root)],
    cwd=root,
    text=True,
    capture_output=True
)
assert cli_metric_verify.returncode != 0, cli_metric_verify.stdout + chr(10) + cli_metric_verify.stderr
metric_receipt = json.loads((root/"output/verification-receipt.json").read_text())
assert metric_receipt["status"] == "FAIL", metric_receipt
assert metric_receipt["specificationEvaluation"]["status"] == "FAIL", metric_receipt
assert metric_receipt["specificationEvaluation"] == metric_evaluation, metric_receipt

# Contract-only mode must not reuse prepared metrics or mislabel unavailable evidence as false.
metric_contract_only_path = root/"output/metric-contract-only-verification.json"
metric_contract_only_run = subprocess.run(
    [app, "verify", "--path", str(root), "--contract-only", "--output", str(metric_contract_only_path)],
    cwd=root,
    text=True,
    capture_output=True
)
assert metric_contract_only_run.returncode == 0, metric_contract_only_run.stdout + chr(10) + metric_contract_only_run.stderr
metric_contract_only = json.loads(metric_contract_only_path.read_text())
assert metric_contract_only["status"] == "REVIEW_REQUIRED", metric_contract_only
assert metric_contract_only["specificationEvaluation"]["status"] == "NOT_EVALUATED", metric_contract_only
assert all(
    criterion["status"] == "NOT_EVALUATED"
    for criterion in metric_contract_only["specificationEvaluation"]["criteria"]
), metric_contract_only

print("V3-001 Agent Verification E2E: PASS")
PY
