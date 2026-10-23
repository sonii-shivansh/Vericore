#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
cat > "$TMP/bin/vericore" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$CAPTURE_ARGS"
exit "${MOCK_EXIT_CODE:-0}"
MOCK
chmod +x "$TMP/bin/vericore"
export PATH="$TMP/bin:$PATH"
export GITHUB_ACTION_PATH="$ROOT"
export CAPTURE_ARGS="$TMP/args.txt"
export VERICORE_COMMAND=prepare
export VERICORE_PATH="$TMP/repo with spaces"
export VERICORE_ARGS="--planned-path src/Payment.kt"
export VERICORE_CHANGE_SUMMARY="Add payment validation"
export VERICORE_AGENT_PROVIDER="Cursor"
export VERICORE_AGENT_ID="agent-7"
export VERICORE_AGENT_SESSION_ID="session-9"
export VERICORE_AGENT_EVENT_ID="event-11"
bash "$ROOT/scripts/action-run.sh"
cat > "$TMP/expected.txt" <<EOF
prepare
Add payment validation
--path
$TMP/repo with spaces
--agent-provider
Cursor
--agent-id
agent-7
--agent-session-id
session-9
--agent-event-id
event-11
--planned-path
src/Payment.kt
EOF
diff -u "$TMP/expected.txt" "$CAPTURE_ARGS"
: > "$CAPTURE_ARGS"
VERICORE_AGENT_PROVIDER=""
if bash "$ROOT/scripts/action-run.sh" >"$TMP/stdout" 2>"$TMP/stderr"; then
  echo "Expected missing provider to fail." >&2; exit 1
fi
[[ ! -s "$CAPTURE_ARGS" ]]
grep -q "agent-provider is required" "$TMP/stderr"
VERICORE_AGENT_ID=""
VERICORE_AGENT_SESSION_ID=""
VERICORE_AGENT_EVENT_ID=""
VERICORE_CHANGE_SUMMARY=""
if bash "$ROOT/scripts/action-run.sh" >"$TMP/stdout" 2>"$TMP/stderr"; then
  echo "Expected missing change summary to fail." >&2; exit 1
fi
[[ ! -s "$CAPTURE_ARGS" ]]
grep -q "change-summary input is required" "$TMP/stderr"
VERICORE_CHANGE_SUMMARY="Add payment validation"
VERICORE_AGENT_PROVIDER=""
VERICORE_ARGS=""
export MOCK_EXIT_CODE=17
set +e
bash "$ROOT/scripts/action-run.sh" >/dev/null 2>&1
status=$?
set -e
[[ "$status" -eq 17 ]]
echo "GitHub Action prepare argument tests passed."
