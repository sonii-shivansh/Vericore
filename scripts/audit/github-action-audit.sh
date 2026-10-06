#!/usr/bin/env bash
set -euo pipefail

action_file="action.yml"
[[ -f "$action_file" ]]

grep -q '^name: Vericore$' "$action_file"
grep -q 'using: composite' "$action_file"
grep -q 'default: analyze' "$action_file"
grep -q 'default: latest' "$action_file"
grep -q 'SHA256SUMS' "$action_file"
grep -q 'Unsupported Vericore command' "$action_file"
grep -q 'Unsupported runner' "$action_file"

for input in command path version args; do
  grep -q "^  $input:" "$action_file"
done

echo "GitHub Action metadata audit passed."
