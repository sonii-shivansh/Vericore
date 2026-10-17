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
# Keep repository-relative Markdown links healthy as docs move or are consolidated.
python3 - <<'PY'
import re
from pathlib import Path
from urllib.parse import unquote, urlsplit

roots = [Path('.'), Path('docs'), Path('.github')]
markdown_files = sorted({
    path
    for root in roots
    for path in (root.glob('*.md') if root == Path('.') else root.rglob('*.md'))
    if path.is_file() and '.git' not in path.parts
})
link_pattern = re.compile(r'!?\[[^\]]*\]\(([^)]+)\)')
destination_pattern = re.compile(r'\s*(?:<([^>]+)>|(\S+))')
broken = []
checked = 0

def without_fenced_code(text):
    output = []
    fence_char = None
    fence_size = 0
    for line in text.splitlines():
        marker = re.match(r'^[ \t]{0,3}(`{3,}|~{3,})', line)
        if fence_char is None:
            if marker:
                fence_char = marker.group(1)[0]
                fence_size = len(marker.group(1))
            else:
                output.append(line)
        elif re.match(r'^[ \t]{0,3}' + re.escape(fence_char) + '{' + str(fence_size) + r',}[ \t]*$', line):
            fence_char = None
            fence_size = 0
    return '\n'.join(output)

for source in markdown_files:
    content = without_fenced_code(source.read_text(encoding='utf-8'))
    for match in link_pattern.finditer(content):
        destination = destination_pattern.match(match.group(1))
        if not destination:
            continue
        target = destination.group(1) or destination.group(2)
        parsed = urlsplit(target)
        if parsed.scheme or parsed.netloc or target.startswith('#'):
            continue
        relative_path = unquote(parsed.path)
        if not relative_path:
            continue
        resolved = Path(relative_path.lstrip('/')) if relative_path.startswith('/') else source.parent / relative_path
        checked += 1
        if not resolved.exists():
            broken.append((source.as_posix(), target))

if broken:
    print('Markdown relative-link audit: FAIL')
    for source, target in broken:
        print(f'  - {source}: {target}')
    raise SystemExit(1)

print(f'Markdown relative-link audit: PASS ({checked} local links checked across {len(markdown_files)} Markdown files)')
PY
