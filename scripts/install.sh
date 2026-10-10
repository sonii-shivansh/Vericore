#!/usr/bin/env bash
set -euo pipefail

REPO="sonii-shivansh/Vericore"
INSTALL_ROOT="${VERICORE_INSTALL_ROOT:-$HOME/.local/share/vericore}"
BIN_DIR="${VERICORE_BIN_DIR:-$HOME/.local/bin}"

fail() {
  echo "Vericore installer error: $*" >&2
  exit 1
}

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

VERSION="${VERICORE_VERSION:-latest}"
if [[ "$VERSION" == "latest" ]]; then
  release_json="$(curl -fL --retry 3 --retry-delay 1 --silent --show-error \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$REPO/releases/latest")"
  VERSION="$(printf '%s' "$release_json" | awk -F'"' '/"tag_name"[[:space:]]*:/ { print $4; exit }')"
fi
VERSION="${VERSION#v}"
[[ "$VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+([-+][0-9A-Za-z.-]+)?$ ]] || fail "Unable to resolve a valid Vericore release version: $VERSION"

ARCHIVE="vericore-${VERSION}-${ASSET}.tar.gz"
BASE_URL="https://github.com/$REPO/releases/download/v${VERSION}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

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

rm -rf "$INSTALL_ROOT"
mkdir -p "$INSTALL_ROOT" "$BIN_DIR"
tar -xzf "$TMP_DIR/$ARCHIVE" -C "$INSTALL_ROOT"
ln -sfn "$INSTALL_ROOT/vericore/bin/vericore" "$BIN_DIR/vericore"
chmod +x "$INSTALL_ROOT/vericore/bin/vericore" "$BIN_DIR/vericore"

if [[ ":${PATH}:" != *":$BIN_DIR:"* ]]; then
  echo "Installed to $BIN_DIR/vericore."
  echo "Add $BIN_DIR to PATH to use 'vericore' from every shell."
else
  "$BIN_DIR/vericore" --version
fi

echo "Installation complete."
