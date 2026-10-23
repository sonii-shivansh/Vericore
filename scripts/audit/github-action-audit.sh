#!/usr/bin/env bash
set -euo pipefail
action_file="action.yml"
installer="scripts/action-install.sh"
[[ -f "$action_file" && -f "$installer" ]]
grep -q '^name: Vericore AI Verification$' "$action_file"
grep -q 'using: composite' "$action_file"
grep -q 'default: analyze' "$action_file"
grep -q 'default: latest' "$action_file"
grep -q 'GITHUB_ACTION_PATH' "$action_file"
grep -q 'Unsupported Vericore command' "$action_file"
grep -q 'scripts/action-run.sh' "$action_file"
grep -q 'vericore verify --path' scripts/action-run.sh
grep -q 'prepare)' scripts/action-run.sh
grep -q 'github-action' src/main/kotlin/com/vericore/cli/PrepareCommand.kt
grep -q 'vericore "$VERICORE_COMMAND" "$VERICORE_PATH"' "$action_file"
grep -q 'action_verification_summary.py' "$action_file"
grep -q 'GITHUB_STEP_SUMMARY' scripts/action_verification_summary.py
python3 scripts/audit/test_action_verification_summary.py
bash -n "$installer"
bash -n scripts/action-run.sh
bash scripts/audit/test_action_run.sh
grep -q 'SHA256SUMS' "$installer"
grep -q 'api.github.com/repos/sonii-shivansh/Vericore/releases/latest' "$installer"
grep -q 'Authorization: Bearer' "$installer"
grep -q 'archive="vericore-' "$installer"
grep -q 'Unsupported runner' "$installer"
grep -q 'sha256sum' "$installer"
grep -q 'GITHUB_PATH' "$installer"
for input in command path version args change-summary agent-provider agent-id agent-session-id agent-event-id; do
  grep -q "^  $input:" "$action_file"
done
echo "GitHub Action metadata audit passed."
