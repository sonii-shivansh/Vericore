#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
UNIX_INSTALLER="$SCRIPT_DIR/install.sh"
WINDOWS_INSTALLER="$SCRIPT_DIR/install.ps1"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local file="$1" pattern="$2"
  grep -Fq -- "$pattern" "$file" || fail "$file does not contain: $pattern"
}

assert_contains "$UNIX_INSTALLER" 'releases/latest/download'
assert_contains "$UNIX_INSTALLER" 'SHA256SUMS'
assert_contains "$UNIX_INSTALLER" 'SHA-256 verification failed'
assert_contains "$UNIX_INSTALLER" 'Linux/x86_64'
assert_contains "$UNIX_INSTALLER" 'Darwin/arm64'
assert_contains "$UNIX_INSTALLER" 'Unsupported platform'
assert_contains "$UNIX_INSTALLER" 'ln -sfn'

assert_contains "$WINDOWS_INSTALLER" 'releases/latest/download'
assert_contains "$WINDOWS_INSTALLER" 'SHA256SUMS'
assert_contains "$WINDOWS_INSTALLER" 'Get-FileHash'
assert_contains "$WINDOWS_INSTALLER" 'AMD64'
assert_contains "$WINDOWS_INSTALLER" 'SetEnvironmentVariable'

printf '%s\n' 'Installer contract tests passed.'
