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

assert_not_contains() {
  local file="$1" pattern="$2"
  if grep -Fq -- "$pattern" "$file"; then
    fail "$file must not contain: $pattern"
  fi
}

# Published assets are versioned (for example, vericore-0.8.2-linux-x64.tar.gz).
# The installers must resolve a tag, construct that exact filename, and verify
# the checksum line for the same exact filename.
assert_contains "$UNIX_INSTALLER" 'api.github.com/repos/$REPO/releases/latest'
assert_contains "$UNIX_INSTALLER" 'VERICORE_VERSION'
assert_contains "$UNIX_INSTALLER" 'ARCHIVE="vericore-${VERSION}-${ASSET}.tar.gz"'
assert_contains "$UNIX_INSTALLER" 'releases/download/v${VERSION}'
assert_contains "$UNIX_INSTALLER" 'SHA256SUMS'
assert_contains "$UNIX_INSTALLER" 'SHA-256 verification failed'
assert_contains "$UNIX_INSTALLER" 'Linux/x86_64'
assert_contains "$UNIX_INSTALLER" 'Darwin/arm64'
assert_contains "$UNIX_INSTALLER" 'Unsupported platform'
assert_contains "$UNIX_INSTALLER" 'ln -sfn'
assert_not_contains "$UNIX_INSTALLER" 'ARCHIVE="vericore-${ASSET}.tar.gz"'

assert_contains "$WINDOWS_INSTALLER" 'api.github.com/repos/sonii-shivansh/Vericore/releases/latest'
assert_contains "$WINDOWS_INSTALLER" "[string]\$Version = 'latest'"
assert_contains "$WINDOWS_INSTALLER" 'vericore-$Version-$asset.zip'
assert_contains "$WINDOWS_INSTALLER" 'releases/download/v$Version'
assert_contains "$WINDOWS_INSTALLER" 'SHA256SUMS'
assert_contains "$WINDOWS_INSTALLER" 'Get-FileHash'
assert_contains "$WINDOWS_INSTALLER" 'AMD64'
assert_contains "$WINDOWS_INSTALLER" 'SetEnvironmentVariable'
assert_not_contains "$WINDOWS_INSTALLER" 'vericore-$asset.zip'
assert_not_contains "$WINDOWS_INSTALLER" 'releases/latest/download'

printf '%s\n' 'Installer contract tests passed.'
