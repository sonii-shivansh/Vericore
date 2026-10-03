#!/usr/bin/env bash
set -euo pipefail

# Vericore local release-preparation helper.
# Official 0.8.0 packaging and publishing are performed by .github/workflows/release.yml.

EXPECTED_VERSION="0.8.0"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

declared="$(./gradlew -q properties | awk -F': ' '$1 == "version" {print $2; exit}')"
application_version="$(grep -o 'const val current: String = "[^"]*"' src/main/kotlin/com/vericore/core/Version.kt | sed -E 's/.*"([^"]+)"/\1/')"

test "$declared" = "$EXPECTED_VERSION"
test "$application_version" = "$EXPECTED_VERSION"

echo "🚀 Vericore ${EXPECTED_VERSION} release preparation"
./gradlew --no-daemon clean test installDist

APP="build/install/vericore/bin/vericore"
test -x "$APP"
version_output="$($APP --version)"
echo "$version_output"
grep -F "$EXPECTED_VERSION" <<< "$version_output" >/dev/null

bash scripts/test-runtime-packaging.sh

echo ""
echo "Release preparation: PASS"
echo "Official packaging/publishing: .github/workflows/release.yml"
