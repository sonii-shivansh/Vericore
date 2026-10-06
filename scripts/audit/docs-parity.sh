#!/usr/bin/env bash
set -euo pipefail

APP="./build/install/vericore/bin/vericore"
CLI_DOC="docs/CLI.md"

[[ -x "$APP" ]] || { echo "CLI not installed: $APP" >&2; exit 1; }
[[ -f "$CLI_DOC" ]] || { echo "Missing CLI documentation: $CLI_DOC" >&2; exit 1; }

help_output="$("$APP" --help)"

python3 - "$CLI_DOC" "$help_output" <<'PY'
import re
import sys

cli_doc = sys.argv[1]
help_output = sys.argv[2]

# Clikt's command listing uses one command per indented line after "Commands:".
runtime = set()
in_commands = False
for line in help_output.splitlines():
    if line.strip() == "Commands:":
        in_commands = True
        continue
    if not in_commands:
        continue
    match = re.match(r"^\s{2,}([a-z][a-z0-9-]*)\s{2,}", line)
    if match:
        runtime.add(match.group(1))

documented = set(
    re.findall(r"^##\s+\d+\.\s+`([a-z][a-z0-9-]*)`\s*$",
               open(cli_doc, encoding="utf-8").read(), re.MULTILINE)
)

missing = sorted(runtime - documented)
stale = sorted(documented - runtime)

if missing or stale:
    print("CLI documentation parity: FAIL")
    if missing:
        print("Runtime commands missing from docs/CLI.md:")
        print("\n".join(f"  - {name}" for name in missing))
    if stale:
        print("Commands documented but absent from runtime --help:")
        print("\n".join(f"  - {name}" for name in stale))
    sys.exit(1)

print(f"CLI documentation parity: PASS ({len(runtime)} runtime commands documented)")
PY
