#!/usr/bin/env bash
set -euo pipefail

REPO="sonii-shivansh/Vericore"
INSTALL_ROOT="\${VERICORE_INSTALL_ROOT:-$HOME/.local/share/vericore}"
BIN_DIR="\${VERICORE_BIN_DIR:-$HOME/.local/bin}"

fail() {
  echo "Vericore installer error: $*" >&2
  exit 1
}

# INSTALL_ROOT is a container for one managed child directory named "vericore".
# Never delete the container itself: it may hold unrelated user data.
if [[ -z "$INSTALL_ROOT" ]]; then
  fail "VERICORE_INSTALL_ROOT must not be empty."
fi
INSTALL_PARENT_INPUT="$(dirname -- "$INSTALL_ROOT")"
INSTALL_BASE="$(basename -- "$INSTALL_ROOT")"
case "$INSTALL_BASE" in
  ""|"."|".."|"/")
    fail "VERICORE_INSTALL_ROOT must name a dedicated directory, not a filesystem root."
    ;;
esac
mkdir -p -- "$INSTALL_PARENT_INPUT"
INSTALL_PARENT="$(cd -P -- "$INSTALL_PARENT_INPUT" && pwd)"
INSTALL_ROOT="$INSTALL_PARENT/$INSTALL_BASE"

if [[ -L "$INSTALL_ROOT" ]]; then
  fail "The installation container must not be a symbolic link: $INSTALL_ROOT"
fi
if [[ -e "$INSTALL_ROOT" && ! -d "$INSTALL_ROOT" ]]; then
  fail "The installation container exists but is not a directory: $INSTALL_ROOT"
fi
mkdir -p -- "$INSTALL_ROOT"
INSTALL_ROOT="$(cd -P -- "$INSTALL_ROOT" && pwd)"

HOME_CANON="$(cd -P -- "$HOME" && pwd)"
if [[ "$INSTALL_ROOT" == "/" || "$INSTALL_ROOT" == "$HOME_CANON" || "$HOME_CANON" == "$INSTALL_ROOT/"* ]]; then
  fail "Refusing an unsafe installation container (filesystem root, home directory, or its ancestor): $INSTALL_ROOT"
fi
case "$INSTALL_ROOT" in
  /usr|/usr/*|/usr/local|/usr/local/*|/opt|/opt/*|/etc|/etc/*|/var|/var/*|/bin|/bin/*|/sbin|/sbin/*|/System|/System/*|/Applications|/Applications/*|/Library|/Library/*|/private|/private/*|/root|/root/*)
    fail "Refusing to install under a protected system path: $INSTALL_ROOT"
    ;;
esac

# The container may contain only the managed distribution. This prevents an
# update from silently taking ownership of arbitrary files in a custom target.
shopt -s dotglob nullglob
for entry in "$INSTALL_ROOT"/*; do
  if [[ "$(basename -- "$entry")" != "vericore" ]]; then
    fail "Installation container is not dedicated to Vericore; unexpected entry: $entry"
  fi
done
shopt -u dotglob nullglob

EXISTING_INSTALL="$INSTALL_ROOT/vericore"
if [[ -e "$EXISTING_INSTALL" || -L "$EXISTING_INSTALL" ]]; then
  if [[ -L "$EXISTING_INSTALL" || ! -d "$EXISTING_INSTALL" || ! -x "$EXISTING_INSTALL/bin/vericore" || ! -d "$EXISTING_INSTALL/lib" ]]; then
    fail "Refusing to replace an existing path that does not look like a Vericore distribution: $EXISTING_INSTALL"
  fi
  shopt -s nullglob
  existing_jars=("$EXISTING_INSTALL/lib"/vericore-*.jar)
  shopt -u nullglob
  if (( \${#existing_jars[@]} == 0 )); then
    fail "Refusing to replace an unrecognized installation without a Vericore application jar: $EXISTING_INSTALL"
  fi
fi

command -v curl >/dev/null 2>&1 || fail "curl is required."
command -v tar >/dev/null 2>&1 || fail "tar is required."
if command -v sha256sum >/dev/null 2>&1; then
  CHECKSUM_CMD="sha256sum"
elif command -v shasum >/dev/null 2>&1; then
  CHECKSUM_CMD="shasum"
else
  fail "sha256sum or shasum is required."
fi

OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS/$ARCH" in
  Linux/x86_64|Linux/amd64)
    ASSET="linux-x64"
    ;;
  Darwin/x86_64|Darwin/amd64)
    ASSET="macos-x64"
    ;;
  Darwin/arm64|Darwin/aarch64)
    ASSET="macos-arm64"
    ;;
  *)
    fail "Unsupported platform: $OS/$ARCH. Supported targets are Linux x64, macOS x64, and macOS arm64."
    ;;
esac

VERSION="\${VERICORE_VERSION:-latest}"
if [[ "$VERSION" == "latest" ]]; then
  release_json="$(curl -fL --retry 3 --retry-delay 1 --silent --show-error \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$REPO/releases/latest")"
  VERSION="$(printf '%s' "$release_json" | awk -F'"' '/"tag_name"[[:space:]]*:/ { print $4; exit }')"
fi
VERSION="\${VERSION#v}"
[[ "$VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+([-+][0-9A-Za-z.-]+)?$ ]] || fail "Unable to resolve a valid Vericore release version: $VERSION"

ARCHIVE="vericore-\${VERSION}-\${ASSET}.tar.gz"
BASE_URL="https://github.com/$REPO/releases/download/v\${VERSION}"
TMP_DIR="$(mktemp -d)"
STAGE_DIR=""
BACKUP_PATH="$INSTALL_ROOT/.vericore-backup.$$"
BACKUP_MOVED=0
NEW_INSTALLED=0
INSTALL_COMPLETE=0

cleanup() {
  local status=$?
  trap - EXIT
  if [[ "$INSTALL_COMPLETE" == "1" ]]; then
    if [[ "$BACKUP_MOVED" == "1" && ( -e "$BACKUP_PATH" || -L "$BACKUP_PATH" ) ]]; then
      rm -rf -- "$BACKUP_PATH"
    fi
  else
    if [[ "$NEW_INSTALLED" == "1" && ( -e "$INSTALL_ROOT/vericore" || -L "$INSTALL_ROOT/vericore" ) ]]; then
      rm -rf -- "$INSTALL_ROOT/vericore"
    fi
    if [[ "$BACKUP_MOVED" == "1" && ( -e "$BACKUP_PATH" || -L "$BACKUP_PATH" ) ]]; then
      if ! mv -- "$BACKUP_PATH" "$INSTALL_ROOT/vericore"; then
        echo "Vericore installer warning: previous installation remains at $BACKUP_PATH" >&2
      fi
    fi
  fi
  if [[ -n "$STAGE_DIR" && -d "$STAGE_DIR" ]]; then
    rm -rf -- "$STAGE_DIR"
  fi
  rm -rf -- "$TMP_DIR"
  exit "$status"
}
trap cleanup EXIT

echo "Downloading Vericore $VERSION for $ASSET..."
curl -fL --retry 3 --retry-delay 1 -o "$TMP_DIR/$ARCHIVE" "$BASE_URL/$ARCHIVE"
curl -fL --retry 3 --retry-delay 1 -o "$TMP_DIR/SHA256SUMS" "$BASE_URL/SHA256SUMS"

EXPECTED="$(awk -v file="$ARCHIVE" '$2 == file {print $1; exit}' "$TMP_DIR/SHA256SUMS")"
[[ -n "$EXPECTED" ]] || fail "Checksum entry for $ARCHIVE was not found."

if [[ "$CHECKSUM_CMD" == "sha256sum" ]]; then
  ACTUAL="$(sha256sum "$TMP_DIR/$ARCHIVE" | awk '{print $1}')"
else
  ACTUAL="$(shasum -a 256 "$TMP_DIR/$ARCHIVE" | awk '{print $1}')"
fi
[[ "$ACTUAL" == "$EXPECTED" ]] || fail "SHA-256 verification failed."

# Extract and stage alongside the active install so directory replacement stays
# on the same filesystem. The active install is moved aside only after the
# archive checksum and staged layout have both been validated.
STAGE_DIR="$(mktemp -d "$INSTALL_ROOT/.vericore-stage.XXXXXX")"
tar -xzf "$TMP_DIR/$ARCHIVE" -C "$STAGE_DIR"
STAGED_INSTALL="$STAGE_DIR/vericore"
[[ -d "$STAGED_INSTALL" && -x "$STAGED_INSTALL/bin/vericore" && -d "$STAGED_INSTALL/lib" ]] || fail "Downloaded archive has an invalid Vericore distribution layout."
shopt -s nullglob
staged_jars=("$STAGED_INSTALL/lib"/vericore-*.jar)
shopt -u nullglob
(( \${#staged_jars[@]} > 0 )) || fail "Downloaded archive does not contain the Vericore application jar."

if [[ -e "$INSTALL_ROOT/vericore" || -L "$INSTALL_ROOT/vericore" ]]; then
  [[ ! -e "$BACKUP_PATH" && ! -L "$BACKUP_PATH" ]] || fail "Installer backup path already exists: $BACKUP_PATH"
  mv -- "$INSTALL_ROOT/vericore" "$BACKUP_PATH"
  BACKUP_MOVED=1
fi
if ! mv -- "$STAGED_INSTALL" "$INSTALL_ROOT/vericore"; then
  fail "Could not activate staged installation; the previous installation will be restored when possible."
fi
NEW_INSTALLED=1

mkdir -p -- "$BIN_DIR"
ln -sfn "$INSTALL_ROOT/vericore/bin/vericore" "$BIN_DIR/vericore"
chmod +x "$INSTALL_ROOT/vericore/bin/vericore" "$BIN_DIR/vericore"

if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
  echo "Installed to $BIN_DIR/vericore."
  echo "Add $BIN_DIR to PATH to use 'vericore' from every shell."
else
  "$BIN_DIR/vericore" --version
fi

INSTALL_COMPLETE=1
echo "Installation complete."
