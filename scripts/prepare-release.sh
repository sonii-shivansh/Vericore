#!/usr/bin/env bash
set -euo pipefail

# Vericore local release-preparation helper.
# Official packaging and publishing are performed by .github/workflows/release.yml.
# Run this helper to verify release readiness before tagging or certifying a release candidate.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ "$#" -gt 1 ]; then
  echo "Error: Unexpected additional arguments: $*" >&2
  echo "Usage: $0 [target-version]" >&2
  exit 1
fi

VERSION_FILE="src/main/kotlin/com/vericore/core/Version.kt"
if [ ! -f "$VERSION_FILE" ]; then
  echo "Error: Version file not found at $VERSION_FILE" >&2
  exit 1
fi

match_count="$(grep -c 'const val current: String = "[^"]*"' "$VERSION_FILE" || true)"
if [ "$match_count" -eq 0 ]; then
  echo "Error: No matching Version.current declaration found in $VERSION_FILE" >&2
  exit 1
fi

if [ "$match_count" -gt 1 ]; then
  echo "Error: Ambiguous application version: multiple ($match_count) Version.current declarations found in $VERSION_FILE" >&2
  exit 1
fi

version_decl="$(grep -o 'const val current: String = "[^"]*"' "$VERSION_FILE")"
application_version="$(sed -E 's/.*"([^"]+)"/\1/' <<< "$version_decl")"

if [[ ! "$application_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "Error: Application version '$application_version' in $VERSION_FILE is not a valid release version format" >&2
  exit 1
fi

TARGET_INPUT="${1:-}"
if [ -z "$TARGET_INPUT" ]; then
  TARGET_VERSION="$application_version"
else
  if [[ "$TARGET_INPUT" =~ ^v([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?)$ ]]; then
    TARGET_VERSION="${BASH_REMATCH[1]}"
  elif [[ "$TARGET_INPUT" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
    TARGET_VERSION="$TARGET_INPUT"
  else
    echo "Error: Invalid release version format: '$TARGET_INPUT'. Expected [v]MAJOR.MINOR.PATCH." >&2
    exit 1
  fi
fi

raw_props="$(./gradlew -q properties)"
declared="$(awk -F': ' '$1 == "version" {print $2; exit}' <<< "$raw_props")"
if [ -z "$declared" ]; then
  echo "Error: Unable to resolve Gradle project version from gradlew properties" >&2
  exit 1
fi

if [[ ! "$declared" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "Error: Malformed Gradle project version: '$declared'" >&2
  exit 1
fi

if [ "$declared" != "$TARGET_VERSION" ]; then
  echo "Error: Gradle version mismatch: declared '$declared' != target '$TARGET_VERSION'" >&2
  exit 1
fi

if [ "$application_version" != "$TARGET_VERSION" ]; then
  echo "Error: Application version mismatch: Version.kt '$application_version' != target '$TARGET_VERSION'" >&2
  exit 1
fi

if [ "$declared" != "$application_version" ]; then
  echo "Error: Version mismatch: Gradle '$declared' != Version.kt '$application_version'" >&2
  exit 1
fi

echo "🚀 Vericore ${TARGET_VERSION} release preparation"
./gradlew --no-daemon clean test installDist

APP="build/install/vericore/bin/vericore"
if [ ! -x "$APP" ]; then
  echo "Error: Expected executable not found or not executable: $APP" >&2
  exit 1
fi

version_output="$("$APP" --version)"
echo "$version_output"

expected_cli_output="vericore version ${TARGET_VERSION}"
if [ "$version_output" != "$expected_cli_output" ]; then
  echo "Error: Unexpected CLI version output: '$version_output' does not match expected '$expected_cli_output'" >&2
  exit 1
fi

bash scripts/test-runtime-packaging.sh

echo ""
echo "Release preparation: PASS"
echo "Official packaging/publishing: .github/workflows/release.yml"
