#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
UNIX_SCRIPT="$SCRIPT_DIR/prepare-runtime.sh"
WINDOWS_SCRIPT="$SCRIPT_DIR/prepare-runtime.ps1"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local file="$1" pattern="$2"
  grep -Fq -- "$pattern" "$file" || fail "$file does not contain: $pattern"
}

assert_not_contains() {
  local file="$1" pattern="$2"
  if grep -Fq -- "$pattern" "$file"; then
    fail "$file must not contain: $pattern"
  fi
}

assert_not_contains "$UNIX_SCRIPT" 'mapfile'
assert_not_contains "$UNIX_SCRIPT" 'readarray'
assert_not_contains "$UNIX_SCRIPT" 'sed -i'
assert_contains "$UNIX_SCRIPT" '--class-path'
assert_contains "$UNIX_SCRIPT" '$APP_JAR'
assert_contains "$UNIX_SCRIPT" 'vericore-*.jar'
assert_contains "$UNIX_SCRIPT" 'DEPENDENCY_CLASSPATH'
assert_contains "$UNIX_SCRIPT" 'awk'
assert_not_contains "$UNIX_SCRIPT" '--bind-services'

assert_contains "$WINDOWS_SCRIPT" "'--class-path'"
assert_contains "$WINDOWS_SCRIPT" '$appJar'
assert_contains "$WINDOWS_SCRIPT" 'vericore-*.jar'
assert_contains "$WINDOWS_SCRIPT" '$dependencyClassPath'
assert_not_contains "$WINDOWS_SCRIPT" "'--bind-services'"

printf '%s\n' 'Runtime packaging contract test passed.'
