#!/usr/bin/env bash
set -euo pipefail

action_file="action.yml"
installer="scripts/action-install.sh"
[[ -f "$action_file" ]]
[[ -f "$installer" ]]

grep -q '^name: Vericore AI Verification$' "$action_file"
grep -q 'using: composite' "$action_file"
grep -q 'default: analyze' "$action_file"
grep -q 'default: latest' "$action_file"
grep -q 'GITHUB_ACTION_PATH' "$action_file"
grep -q 'Unsupported Vericore command' "$action_file"
grep -q 'vericore verify --path' "$action_file"
grep -q 'vericore "$VERICORE_COMMAND" "$VERICORE_PATH"' "$action_file"

bash -n "$installer"
grep -q 'SHA256SUMS' "$installer"
grep -q 'api.github.com/repos/sonii-shivansh/Vericore/releases/latest' "$installer"
grep -q 'Authorization: Bearer \\$GITHUB_TOKEN' "$installer"
grep -q 'archive="vericore-' "$installer"
grep -q 'Unsupported runner' "$installer"
grep -q 'sha256sum' "$installer"
grep -q 'GITHUB_PATH' "$installer"

for input in command path version args; do
  grep -q "^  $input:" "$action_file"
done

echo "GitHub Action metadata audit passed."
