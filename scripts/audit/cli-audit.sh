#!/usr/bin/env bash
set -euo pipefail
APP="./build/install/vericore/bin/vericore"
REPO="$(pwd)"
[[ -x "$APP" ]] || { echo "CLI not installed: $APP" >&2; exit 1; }
run(){ echo "==> $*"; "$@"; }
run "$APP" --version
run "$APP" --help
for command in analyze impact architecture architecture-drift architecture-contract context-snapshot context-diff reality pr-intelligence repo-qa plan prepare verify ask evolution server mcp setup doctor; do run "$APP" "$command" --help >/dev/null; done
run "$APP" analyze "$REPO" >/dev/null
run "$APP" reality "$REPO" --json >/dev/null
run "$APP" architecture "$REPO" --json >/dev/null
run "$APP" context-snapshot "$REPO" --json >/dev/null
run "$APP" evolution >/dev/null
rm -rf output
run "$APP" prepare "audit release workflow" --path "$REPO" >/dev/null
[[ -s output/engineering-context.json && -s output/engineering-plan.json && -s output/agent-change-contract.json ]]
run "$APP" verify --path "$REPO" --plan output/engineering-plan.json --contract output/agent-change-contract.json --output output/verification.json >/dev/null
[[ -s output/verification.json ]]
# Release-candidate regressions for the historical 0.8.1 audit findings.
doctor_fixture="$(mktemp -d)"
contract_fixture="$(mktemp -d)"
qa_fixture="$(mktemp -d)"
drift_fixture="$(mktemp -d)"
trap 'rm -rf "$doctor_fixture" "$contract_fixture" "$qa_fixture" "$drift_fixture"' EXIT

if (cd "$doctor_fixture" && "$REPO/$APP" doctor > doctor.log 2>&1); then
  echo "doctor unexpectedly succeeded outside a Git repository" >&2
  cat "$doctor_fixture/doctor.log" >&2
  exit 1
fi
grep -q "Vericore needs attention" "$doctor_fixture/doctor.log"

printf '%s\n' 'class FixtureSource' > "$contract_fixture/FixtureSource.kt"
cat > "$contract_fixture/.vericore-architecture-contract.json" <<'JSON'
{
  "schemaVersion": "1.0",
  "maxFindings": 2147483647,
  "maxCycles": 2147483647,
  "allowedSeverities": ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
  "requiredRules": ["ARCH-LAYER-001"]
}
JSON
if "$APP" architecture-contract "$contract_fixture" --json > "$contract_fixture/contract.log" 2>&1; then
  echo "architecture-contract unexpectedly passed the failing fixture" >&2
  cat "$contract_fixture/contract.log" >&2
  exit 1
fi
grep -q "Architecture contract failed" "$contract_fixture/contract.log"
! grep -q "Unexpected error occurred" "$contract_fixture/contract.log"
! grep -Eq '^[[:space:]]+at ' "$contract_fixture/contract.log"

cat > "$qa_fixture/Target.kt" <<'KOTLIN'
package fixture
class Target
KOTLIN
cat > "$qa_fixture/Dependent.kt" <<'KOTLIN'
package fixture
import fixture.Target
class Dependent
KOTLIN
"$APP" repo-qa "What depends on Target?" --path "$qa_fixture" --evidence-output "$qa_fixture/grounded-evidence.json" > "$qa_fixture/repo-qa.json"
python3 - "$qa_fixture/repo-qa.json" <<'PY'
import json, sys
result = json.load(open(sys.argv[1]))
evidence = [item["citation"] for item in result["evidence"]]
assert any("Dependent.kt" in citation.get("relatedPaths", []) for citation in evidence), evidence
assert any("Dependent.kt" in citation.get("detail", "") for citation in evidence), evidence
PY

cat > "$drift_fixture/A.kt" <<'KOTLIN'
package fixture
import fixture.B
class A
KOTLIN
cat > "$drift_fixture/B.kt" <<'KOTLIN'
package fixture
class B
KOTLIN
cat > "$drift_fixture/C.kt" <<'KOTLIN'
package fixture
class C
KOTLIN
"$APP" architecture "$drift_fixture" --json >/dev/null
cp "$drift_fixture/output/architecture.json" "$drift_fixture/baseline.json"
cat > "$drift_fixture/A.kt" <<'KOTLIN'
package fixture
import fixture.C
class A
KOTLIN
"$APP" architecture-drift "$drift_fixture" --baseline "$drift_fixture/baseline.json" --json > "$drift_fixture/drift.log"
grep -q "Added edges: 1" "$drift_fixture/drift.log"
grep -q "Removed edges: 1" "$drift_fixture/drift.log"
grep -q "EDGE_ADDED: A.kt|C.kt" "$drift_fixture/drift.log"
grep -q "EDGE_REMOVED: A.kt|B.kt" "$drift_fixture/drift.log"

# Release-blocker regressions.
review_fixture="$(mktemp -d)"
unicode_fixture="$(mktemp -d)"
trap 'rm -rf "$doctor_fixture" "$contract_fixture" "$qa_fixture" "$drift_fixture" "$review_fixture" "$unicode_fixture"' EXIT

mkdir -p "$review_fixture/src"
printf '%s\n' 'class Related {}' > "$review_fixture/src/Related.java"
git -C "$review_fixture" init -q
git -C "$review_fixture" config user.name "Vericore Audit"
git -C "$review_fixture" config user.email "audit@example.com"
git -C "$review_fixture" add .
git -C "$review_fixture" commit -qm "baseline"
printf '%s\n' 'class Related { int value = 1; }' > "$review_fixture/src/Related.java"
cat > "$review_fixture/output/engineering-plan.json" <<'JSON'
{"schemaVersion":"1.1","changeSummary":"test scope","repository":"","affectedComponents":["src/Related.java"],"plannedPaths":[],"concerns":[],"riskLevel":"LOW","steps":[],"verificationCommands":[],"evidenceIds":[],"uncertainties":[],"contractFingerprint":""}
JSON
python3 - "$review_fixture/output/engineering-plan.json" "$review_fixture" <<'PY'
import json, pathlib, sys
p=pathlib.Path(sys.argv[1]); d=json.loads(p.read_text()); d["repository"]=str(pathlib.Path(sys.argv[2]).resolve()); p.write_text(json.dumps(d))
PY
printf '%s\n' '{"schemaVersion":"2.0","repository":"","changeSummary":"test scope","preparedHead":"","plannedPaths":[],"expectedChangeTypes":{},"expectedComponents":["src/Related.java"],"verificationCommands":[],"evidenceIds":[],"architectureExpectations":[],"fingerprint":"invalid"}' > "$review_fixture/output/agent-change-contract.json"
set +e
"$APP" verify --path "$review_fixture" --plan "$review_fixture/output/engineering-plan.json" --contract "$review_fixture/output/agent-change-contract.json" > "$review_fixture/review.log" 2>&1
review_status=$?
set -e
test "$review_status" -ne 0
grep -q "did not pass" "$review_fixture/review.log"

mkdir -p "$unicode_fixture/src"
printf '%s\n' 'class Unicode {}' > "$unicode_fixture/src/Ünicode.java"
git -C "$unicode_fixture" init -q
git -C "$unicode_fixture" config user.name "Vericore Audit"
git -C "$unicode_fixture" config user.email "audit@example.com"
git -C "$unicode_fixture" add .
git -C "$unicode_fixture" commit -qm "baseline"
printf '%s\n' 'class Unicode { int changed = 1; }' > "$unicode_fixture/src/Ünicode.java"
"$APP" pr-intelligence "$unicode_fixture" --json >/dev/null
test -s "$unicode_fixture/output/pr-intelligence.json"

set +e
"$APP" evolution . --months 0 > /tmp/vericore-evolution-invalid.txt 2>&1
evolution_status=$?
set -e
test "$evolution_status" -ne 0
grep -q -- "--months must be greater than 0" /tmp/vericore-evolution-invalid.txt

set +e
"$APP" ask "noninteractive setup probe" </dev/null > /tmp/vericore-ask-noninteractive.txt 2>&1
ask_status=$?
set -e
if [[ "$ask_status" -eq 0 ]]; then
  echo "ask unexpectedly succeeded without AI configuration" >&2
  exit 1
fi
grep -q "Non-interactive 'ask' cannot prompt" /tmp/vericore-ask-noninteractive.txt\n\necho "Historical 0.8.1 regression audit: PASS"

echo "CLI audit: PASS"
