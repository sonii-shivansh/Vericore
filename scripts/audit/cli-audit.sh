#!/usr/bin/env bash
set -euo pipefail
APP="./build/install/vericore/bin/vericore"
REPO="$(pwd)"
[[ -x "$APP" ]] || { echo "CLI not installed: $APP" >&2; exit 1; }
run(){ echo "==> $*"; "$@"; }
run "$APP" --version
run "$APP" --help
for command in analyze impact architecture architecture-drift architecture-contract context-snapshot context-diff reality pr-intelligence repo-qa plan prepare verify ask evolution server mcp setup doctor; do run "$APP" "$command" --help >/dev/null; done
set +e
"$APP" setup --provider gemini </dev/null > /tmp/vericore-setup-noninteractive.stdout 2> /tmp/vericore-setup-noninteractive.stderr
setup_status=$?
set -e
test "$setup_status" -ne 0
test ! -s /tmp/vericore-setup-noninteractive.stdout
grep -q "Non-interactive 'setup' requires" /tmp/vericore-setup-noninteractive.stderr
! grep -Eq '^[[:space:]]+at ' /tmp/vericore-setup-noninteractive.stderr
run "$APP" analyze "$REPO" >/dev/null
run "$APP" reality "$REPO" --json >/dev/null
run "$APP" architecture "$REPO" --json >/dev/null
run "$APP" context-snapshot "$REPO" --json >/dev/null
run "$APP" evolution >/dev/null
rm -rf output
run "$APP" prepare "audit release workflow" --path "$REPO" >/dev/null
[[ -s output/engineering-context.json && -s output/engineering-preparation.json && -s output/engineering-plan.json && -s output/agent-change-contract.json ]]
python3 - <<'PY'
import json
snapshot=json.load(open("output/engineering-context.json"))
prep=json.load(open("output/engineering-preparation.json"))
assert snapshot["schemaVersion"] == "1.0"
for key in ("repositoryCommit","files","totalFiles","totalBytes","languages","dirty","changedPaths","snapshotDigest"):
    assert key in snapshot
assert prep["schemaVersion"] == "1.2"
for key in ("changeSet","evidence","plan","contract"):
    assert key in prep
PY
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

set +e
"$APP" repo-qa "invalid bounds" --path "$contract_fixture" --max-results 0 > "$qa_fixture/qa-invalid.stdout" 2> "$qa_fixture/qa-invalid.stderr"
qa_status=$?
set -e
test "$qa_status" -ne 0
test ! -s "$qa_fixture/qa-invalid.stdout"
grep -q -- "--max-results must be between 1 and 32" "$qa_fixture/qa-invalid.stderr"
! grep -Eq '^[[:space:]]+at ' "$qa_fixture/qa-invalid.stderr"

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
import fixture.C
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
cat > "$drift_fixture/baseline.json" <<'JSON'
{
  "schemaVersion": "1.1",
  "summary": {
    "filesAnalyzed": 3,
    "dependencyEdges": 1,
    "findings": 0,
    "cycles": 0,
    "crossLayerDependencies": 0,
    "highCouplingFiles": 0
  },
  "findings": [],
  "cycles": [],
  "layers": {},
  "dependencyEdges": [
    {"source": "A.kt", "target": "B.kt"}
  ]
}
JSON
"$APP" architecture-drift "$drift_fixture" --baseline "$drift_fixture/baseline.json" --json > "$drift_fixture/drift.log"
grep -q "Added edges: 1" "$drift_fixture/drift.log"
grep -q "Removed edges: 1" "$drift_fixture/drift.log"
grep -q "EDGE_ADDED: A.kt|C.kt" "$drift_fixture/drift.log"
grep -q "EDGE_REMOVED: A.kt|B.kt" "$drift_fixture/drift.log"
echo "CLI audit checkpoint: architecture-drift PASS"

context_fixture="$(mktemp -d)"
trap 'rm -rf "$doctor_fixture" "$contract_fixture" "$qa_fixture" "$drift_fixture" "$context_fixture"' EXIT
printf '%s\n' 'class One' > "$context_fixture/One.kt"
"$APP" context-snapshot "$context_fixture" --json >/dev/null
cp "$context_fixture/output/engineering-context.json" "$context_fixture/before.json"
printf '%s\n' 'class Two' > "$context_fixture/Two.kt"
"$APP" context-snapshot "$context_fixture" --json >/dev/null
"$APP" context-diff "$context_fixture/before.json" "$context_fixture/output/engineering-context.json" --json > "$context_fixture/diff.log"
echo "CLI audit checkpoint: context-diff PASS"
grep -q 'ADDED:' "$context_fixture/diff.log"

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
"$APP" prepare "scope probe" --path "$review_fixture" >/dev/null
printf '%s\n' 'class Related { int value = 1; }' > "$review_fixture/src/Related.java"
set +e
"$APP" verify --path "$review_fixture" > "$review_fixture/review.log" 2>&1
review_status=$?
set -e
test "$review_status" -ne 0
grep -q "REVIEW_REQUIRED" "$review_fixture/review.log"
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
echo "CLI audit checkpoint: Unicode PR intelligence PASS"
"$APP" analyze "$unicode_fixture" >/tmp/vericore-unicode-analyze.txt 2>&1
test -s "$unicode_fixture/output/index.html"
echo "CLI audit checkpoint: Unicode analyze PASS"

malformed_fixture="$(mktemp -d)"
trap 'rm -rf "$doctor_fixture" "$contract_fixture" "$qa_fixture" "$drift_fixture" "$review_fixture" "$unicode_fixture" "$malformed_fixture"' EXIT
printf '%s\n' 'class Broken {' > "$malformed_fixture/Broken.java"
set +e
"$APP" analyze "$malformed_fixture" > "$malformed_fixture/fail.log" 2>&1
parse_status=$?
set -e
test "$parse_status" -ne 0
grep -q "reported parser diagnostics" "$malformed_fixture/fail.log"
"$APP" analyze "$malformed_fixture" --allow-parse-errors > "$malformed_fixture/allow.log" 2>&1
test -s "$malformed_fixture/output/index.html"
echo "CLI audit checkpoint: allow parse errors PASS"

set +e
"$APP" server --host vericore-invalid-host.invalid --port 18181 > /tmp/vericore-server-host.txt 2>&1
host_status=$?
set -e
test "$host_status" -ne 0
grep -q "Unable to resolve server host" /tmp/vericore-server-host.txt

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
grep -q "Non-interactive 'ask' cannot prompt" /tmp/vericore-ask-noninteractive.txt

echo "Historical 0.8.1 regression audit: PASS"

echo "CLI audit: PASS"
