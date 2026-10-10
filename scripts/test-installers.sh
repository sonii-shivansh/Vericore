#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
UNIX_INSTALLER="$SCRIPT_DIR/install.sh"
WINDOWS_INSTALLER="$SCRIPT_DIR/install.ps1"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}
assert_contains() { local file="$1" pattern="$2"; grep -Fq -- "$pattern" "$file" || fail "$file does not contain: $pattern"; }
assert_not_contains() { local file="$1" pattern="$2"; if grep -Fq -- "$pattern" "$file"; then fail "$file must not contain: $pattern"; fi; }

# Source contracts plus an offline fake-release integration test.
assert_contains "$UNIX_INSTALLER" 'api.github.com/repos/$REPO/releases/latest'
assert_contains "$UNIX_INSTALLER" 'VERICORE_INSTALL_ROOT'
assert_contains "$UNIX_INSTALLER" 'ARCHIVE="vericore-${VERSION}-${ASSET}.tar.gz"'
assert_contains "$UNIX_INSTALLER" 'releases/download/v${VERSION}'
assert_contains "$UNIX_INSTALLER" 'SHA256SUMS'
assert_contains "$UNIX_INSTALLER" 'SHA-256 verification failed'
assert_contains "$UNIX_INSTALLER" 'Linux/x86_64'
assert_contains "$UNIX_INSTALLER" 'Darwin/arm64'
assert_contains "$UNIX_INSTALLER" 'Unsupported platform'
assert_contains "$UNIX_INSTALLER" 'ln -sfn'
assert_not_contains "$UNIX_INSTALLER" 'rm -rf "$INSTALL_ROOT"'
assert_contains "$UNIX_INSTALLER" 'Installation container is not dedicated to Vericore'
assert_contains "$UNIX_INSTALLER" 'does not look like a Vericore distribution'
assert_contains "$UNIX_INSTALLER" 'STAGE_DIR="$(mktemp -d "$INSTALL_ROOT/.vericore-stage.XXXXXX")"'

assert_contains "$WINDOWS_INSTALLER" 'api.github.com/repos/sonii-shivansh/Vericore/releases/latest'
assert_contains "$WINDOWS_INSTALLER" '[string]$Version = '
assert_contains "$WINDOWS_INSTALLER" 'vericore-$Version-$asset.zip'
assert_contains "$WINDOWS_INSTALLER" 'releases/download/v$Version'
assert_contains "$WINDOWS_INSTALLER" 'SHA256SUMS'
assert_contains "$WINDOWS_INSTALLER" 'Get-FileHash'
assert_contains "$WINDOWS_INSTALLER" 'AMD64'
assert_contains "$WINDOWS_INSTALLER" 'SetEnvironmentVariable'
assert_contains "$WINDOWS_INSTALLER" 'filesystem root'
assert_contains "$WINDOWS_INSTALLER" 'non-empty directory that is not a recognized Vericore installation'
assert_contains "$WINDOWS_INSTALLER" '.Vericore.stage.'
assert_not_contains "$WINDOWS_INSTALLER" 'Remove-Item -Recurse -Force $InstallRoot'
assert_not_contains "$WINDOWS_INSTALLER" 'releases/latest/download'

TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT
MOCK_BIN="$TEST_ROOT/mock-bin"
FIXTURE="$TEST_ROOT/fixture"
HOME_DIR="$TEST_ROOT/home"
BIN_DIR="$HOME_DIR/.local/bin"
INSTALL_ROOT="$HOME_DIR/.local/share/vericore"
mkdir -p "$MOCK_BIN" "$FIXTURE/vericore/bin" "$FIXTURE/vericore/lib" "$HOME_DIR"
cat > "$FIXTURE/vericore/bin/vericore" <<'CLI'
#!/usr/bin/env bash
if [[ "${1:-}" == "--version" ]]; then
  echo "Vericore 0.9.0"
else
  echo "fake cli"
fi
CLI
chmod +x "$FIXTURE/vericore/bin/vericore"
printf 'fixture application jar\n' > "$FIXTURE/vericore/lib/vericore-0.9.0.jar"
ARCHIVE="$TEST_ROOT/vericore-0.9.0-linux-x64.tar.gz"
tar -czf "$ARCHIVE" -C "$FIXTURE" vericore
printf '%s  %s\n' "$(sha256sum "$ARCHIVE" | awk '{print $1}')" "$(basename "$ARCHIVE")" > "$TEST_ROOT/SHA256SUMS"
export VERICORE_TEST_ARCHIVE="$ARCHIVE"
export VERICORE_TEST_SUMS="$TEST_ROOT/SHA256SUMS"
export VERICORE_TEST_CURL_LOG="$TEST_ROOT/curl.log"
cat > "$MOCK_BIN/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
destination=""
url=""
while (($#)); do
  if [[ "$1" == "-o" ]]; then
    destination="$2"
    shift 2
  else
    url="$1"
    shift
  fi
done
printf '%s\n' "$url" >> "$VERICORE_TEST_CURL_LOG"
case "$url" in
  */SHA256SUMS) cp "$VERICORE_TEST_SUMS" "$destination" ;;
  */vericore-0.9.0-linux-x64.tar.gz) cp "$VERICORE_TEST_ARCHIVE" "$destination" ;;
  *) echo "Unexpected mocked curl URL: $url" >&2; exit 22 ;;
esac
CURL
chmod +x "$MOCK_BIN/curl"

HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$INSTALL_ROOT" VERICORE_BIN_DIR="$BIN_DIR" \
  bash "$UNIX_INSTALLER" >/dev/null
[[ "$("$BIN_DIR/vericore" --version)" == *"0.9.0"* ]] || fail "Fresh installation did not run the installed CLI."
[[ -f "$INSTALL_ROOT/vericore/lib/vericore-0.9.0.jar" ]] || fail "Fresh installation omitted the application jar."

IMPORTANT_FILE="$HOME_DIR/important-user-data.txt"
printf 'must survive installer upgrade\n' > "$IMPORTANT_FILE"
HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$INSTALL_ROOT" VERICORE_BIN_DIR="$BIN_DIR" \
  bash "$UNIX_INSTALLER" >/dev/null
[[ -f "$IMPORTANT_FILE" ]] || fail "Upgrade deleted unrelated user data."
[[ "$("$BIN_DIR/vericore" --version)" == *"0.9.0"* ]] || fail "Upgraded CLI did not run."

CURL_CALLS_BEFORE="$(wc -l < "$VERICORE_TEST_CURL_LOG")"

# The launcher directory must not alias the install container, and the installer
# must not overwrite an unrelated executable already named "vericore".
if HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$INSTALL_ROOT" VERICORE_BIN_DIR="$INSTALL_ROOT" \
  bash "$UNIX_INSTALLER" >/dev/null 2>&1; then
  fail "Installer accepted overlapping installation and launcher directories."
fi
[[ -f "$INSTALL_ROOT/vericore/lib/vericore-0.9.0.jar" ]] || fail "Overlapping-directory rejection damaged the existing installation."

UNRELATED_BIN="$TEST_ROOT/unrelated-bin"
mkdir -p "$UNRELATED_BIN"
printf 'do not replace this file\n' > "$UNRELATED_BIN/vericore"
if HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$INSTALL_ROOT" VERICORE_BIN_DIR="$UNRELATED_BIN" \
  bash "$UNIX_INSTALLER" >/dev/null 2>&1; then
  fail "Installer overwrote a pre-existing unrelated launcher file."
fi
[[ "$(cat "$UNRELATED_BIN/vericore")" == "do not replace this file" ]] || fail "Installer modified a pre-existing unrelated launcher file."

UNSAFE_HOME="$TEST_ROOT/unsafe-home"
mkdir -p "$UNSAFE_HOME"
printf 'keep home data\n' > "$UNSAFE_HOME/sentinel.txt"
if HOME="$UNSAFE_HOME" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$UNSAFE_HOME" VERICORE_BIN_DIR="$TEST_ROOT/bin" \
  bash "$UNIX_INSTALLER" >/dev/null 2>&1; then
  fail "Installer accepted HOME as its installation container."
fi
[[ -f "$UNSAFE_HOME/sentinel.txt" ]] || fail "Unsafe HOME test deleted a sentinel file."

UNSAFE_CONTAINER="$TEST_ROOT/not-dedicated"
mkdir -p "$UNSAFE_CONTAINER"
printf 'keep this file\n' > "$UNSAFE_CONTAINER/notes.txt"
if HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$UNSAFE_CONTAINER" VERICORE_BIN_DIR="$TEST_ROOT/bin" \
  bash "$UNIX_INSTALLER" >/dev/null 2>&1; then
  fail "Installer accepted a container containing unrelated data."
fi
[[ -f "$UNSAFE_CONTAINER/notes.txt" ]] || fail "Installer deleted data from a non-dedicated container."

UNRECOGNIZED_CONTAINER="$TEST_ROOT/unrecognized-root"
mkdir -p "$UNRECOGNIZED_CONTAINER/vericore"
printf 'keep non-Vericore contents\n' > "$UNRECOGNIZED_CONTAINER/vericore/important.txt"
if HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="$UNRECOGNIZED_CONTAINER" VERICORE_BIN_DIR="$TEST_ROOT/bin" \
  bash "$UNIX_INSTALLER" >/dev/null 2>&1; then
  fail "Installer accepted an unrecognized existing vericore directory."
fi
[[ -f "$UNRECOGNIZED_CONTAINER/vericore/important.txt" ]] || fail "Installer deleted an unrecognized vericore directory."

if HOME="$HOME_DIR" PATH="$MOCK_BIN:$PATH" VERICORE_VERSION="0.9.0" \
  VERICORE_INSTALL_ROOT="/" VERICORE_BIN_DIR="$TEST_ROOT/bin" \
  bash "$UNIX_INSTALLER" >/dev/null 2>&1; then
  fail "Installer accepted filesystem root as its installation container."
fi
CURL_CALLS_AFTER="$(wc -l < "$VERICORE_TEST_CURL_LOG")"
[[ "$CURL_CALLS_BEFORE" == "$CURL_CALLS_AFTER" ]] || fail "Unsafe destination tests attempted downloads."

printf '%s\n' 'Installer contract and Unix path-safety tests passed.'
