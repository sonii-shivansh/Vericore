#!/usr/bin/env bash
set -euo pipefail

CLI="./build/install/vericore/bin/vericore"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/src"
python3 - "$fixture" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1]) / "src"
for i in range(1, 128):
    (root / f"Class{i}.java").write_text(
        f"package memoryfixture;\npublic class Class{i} {{ }}\n",
        encoding="utf-8",
    )

large = root / "Large.java"
payload = "/*" + (" memory-regression " * 430000) + "*/\n"
large.write_text(payload + "package memoryfixture;\npublic class Large {}\n", encoding="utf-8")
assert large.stat().st_size >= 7_500_000
PY

git -C "$fixture" init -q
git -C "$fixture" config user.name "Vericore Memory Audit"
git -C "$fixture" config user.email "vericore-memory-audit@example.invalid"
git -C "$fixture" add .
git -C "$fixture" commit -qm "memory audit fixture"

time_file="$fixture/time.txt"
set +e
/usr/bin/time -v "$CLI" analyze "$fixture" >"$fixture/analyze-time.log" 2>"$time_file"
status=$?
set -e
test "$status" -eq 0
rss_kb="$(awk -F: '/Maximum resident set size/ {gsub(/ /, "", $2); print $2}' "$time_file")"
test -n "$rss_kb"
echo "Maximum RSS: $rss_kb KB"

# The 0.8.2 audit observed roughly 1.1 GB RSS on this fixture.
# Keep meaningful headroom below that regression ceiling.
test "$rss_kb" -lt 1000000
