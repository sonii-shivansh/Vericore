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
python3 - <<'PY'
import yaml
from pathlib import Path
data = yaml.safe_load(Path("action.yml").read_text())
assert data["runs"]["using"] == "composite"
assert set(data["inputs"]) == {"command", "path", "version", "args"}
assert data["inputs"]["command"]["default"] == "analyze"
assert data["inputs"]["version"]["default"] == "latest"
PY
echo "GitHub Action metadata audit passed."
