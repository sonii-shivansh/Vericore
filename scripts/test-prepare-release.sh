#!/usr/bin/env bash
set -euo pipefail

# Vericore regression test suite for scripts/prepare-release.sh
# Tests release-preparation logic using isolated mock environments without executing real builds.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_SCRIPT="$REPO_ROOT/scripts/prepare-release.sh"

if [ ! -f "$TARGET_SCRIPT" ]; then
  echo "Error: Target script not found: $TARGET_SCRIPT" >&2
  exit 1
fi

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

fail() {
  echo "  [FAIL] $*" >&2
  FAILED_TESTS=$((FAILED_TESTS + 1))
}

pass() {
  echo "  [PASS] $*"
  PASSED_TESTS=$((PASSED_TESTS + 1))
}

run_scenario() {
  local id="$1"
  local name="$2"
  local expected_exit="$3"
  local expect_build_invoked="$4"
  local expected_pattern="$5"
  shift 5
  local script_args=("$@")

  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  echo "Running $id: $name..."

  local fixture_dir
  fixture_dir="$(mktemp -d 2>/dev/null || mktemp -d -t 'vc-test-XXXXXX')"

  mkdir -p "$fixture_dir/scripts" "$fixture_dir/src/main/kotlin/com/vericore/core"
  cp "$TARGET_SCRIPT" "$fixture_dir/scripts/prepare-release.sh"
  chmod +x "$fixture_dir/scripts/prepare-release.sh"

  # Create mock Version.kt
  if [ -n "${MOCK_APP_VERSION_RAW:-}" ]; then
    printf '%s\n' "$MOCK_APP_VERSION_RAW" > "$fixture_dir/src/main/kotlin/com/vericore/core/Version.kt"
  else
    local app_version="${MOCK_APP_VERSION:-2.0.0}"
    cat << EOF > "$fixture_dir/src/main/kotlin/com/vericore/core/Version.kt"
package com.vericore.core

object Version {
    const val current: String = "${app_version}"
}
EOF
  fi

  # Create mock test-runtime-packaging.sh
  cat << 'EOF' > "$fixture_dir/scripts/test-runtime-packaging.sh"
#!/usr/bin/env bash
if [ "${MOCK_PACKAGING_FAIL:-false}" = "true" ]; then
  echo "Mock runtime packaging error" >&2
  exit 1
fi
exit 0
EOF
  chmod +x "$fixture_dir/scripts/test-runtime-packaging.sh"

  # Create mock gradlew
  cat << 'EOF' > "$fixture_dir/gradlew"
#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${1:-}" = "-q" ] && [ "${2:-}" = "properties" ]; then
  if [ "${MOCK_GRADLE_FAIL:-false}" = "true" ]; then
    echo "Mock gradle properties error" >&2
    exit 1
  fi
  echo "name: vericore"
  echo "version: ${MOCK_GRADLE_VERSION:-2.0.0}"
  echo "group: com.vericore"
  exit 0
fi

if [ "${1:-}" = "--no-daemon" ] && [ "${2:-}" = "clean" ]; then
  touch "$ROOT/.build_invoked"
  if [ "${MOCK_BUILD_FAIL:-false}" = "true" ]; then
    echo "Mock build execution error" >&2
    exit 1
  fi
  if [ "${MOCK_OMIT_CLI_BINARY:-false}" != "true" ]; then
    mkdir -p "$ROOT/build/install/vericore/bin"
    cat << 'CLIOUT' > "$ROOT/build/install/vericore/bin/vericore"
#!/usr/bin/env bash
if [ "${1:-}" = "--version" ]; then
  if [ "${MOCK_CLI_FAIL:-false}" = "true" ]; then
    echo "Mock CLI binary crash" >&2
    exit 1
  fi
  echo "${MOCK_CLI_OUTPUT:-vericore version 2.0.0}"
  exit 0
fi
echo "Unexpected CLI invocation: $*" >&2
exit 1
CLIOUT
    chmod +x "$ROOT/build/install/vericore/bin/vericore"
  fi
  exit 0
fi

echo "Unexpected mock gradlew invocation: $*" >&2
exit 1
EOF
  chmod +x "$fixture_dir/gradlew"

  local stdout_file="$fixture_dir/stdout.log"
  local stderr_file="$fixture_dir/stderr.log"
  local actual_exit=0

  (
    cd "$fixture_dir"
    set +e
    bash "scripts/prepare-release.sh" "${script_args[@]}" > "$stdout_file" 2> "$stderr_file"
    exit $?
  ) || actual_exit=$?

  local combined_output
  combined_output="$(cat "$stdout_file" "$stderr_file")"

  local failed_case=false

  if [ "$actual_exit" -ne "$expected_exit" ]; then
    fail "$id: Expected exit code $expected_exit, but got $actual_exit"
    failed_case=true
  fi

  if [ "$expect_build_invoked" = "true" ]; then
    if [ ! -f "$fixture_dir/.build_invoked" ]; then
      fail "$id: Expected .build_invoked sentinel to exist, but it was missing"
      failed_case=true
    fi
  else
    if [ -f "$fixture_dir/.build_invoked" ]; then
      fail "$id: Expected .build_invoked sentinel NOT to exist, but build was executed"
      failed_case=true
    fi
  fi

  if [ -n "$expected_pattern" ]; then
    if ! grep -Eq "$expected_pattern" <<< "$combined_output"; then
      fail "$id: Expected output pattern '$expected_pattern' not found in combined output: $combined_output"
      failed_case=true
    fi
  fi

  if [ "$failed_case" = "false" ]; then
    pass "$id: $name"
  fi

  rm -rf "$fixture_dir"
}

echo "=== Running Vericore prepare-release.sh Regression Test Suite ==="

# TC-01: Default matching version
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="vericore version 2.0.0" \
  run_scenario "TC-01" "Default no-argument matching version" 0 "true" "Release preparation: PASS"

# TC-02: Explicit matching version
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="vericore version 2.0.0" \
  run_scenario "TC-02" "Explicit matching target version" 0 "true" "Release preparation: PASS" "2.0.0"

# TC-03: Single 'v' prefix
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="vericore version 2.0.0" \
  run_scenario "TC-03" "Single 'v' prefix normalized" 0 "true" "Release preparation: PASS" "v2.0.0"

# TC-04: Repeated prefix rejected
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" \
  run_scenario "TC-04" "Repeated 'vv' prefix rejected" 1 "false" "Invalid release version format" "vv2.0.0"

# TC-05: Malformed version rejected
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" \
  run_scenario "TC-05" "Malformed version rejected" 1 "false" "Invalid release version format" "2.0"

# TC-06: Mismatched target rejected
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" \
  run_scenario "TC-06" "Mismatched target rejected before build" 1 "false" "Gradle version mismatch" "9.9.9"

# TC-07: Gradle/application version disagreement
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.1" \
  run_scenario "TC-07" "Gradle/application version disagreement" 1 "false" "version mismatch"

# TC-08: Gradle properties command failure
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_FAIL="true" \
  run_scenario "TC-08" "Gradle properties failure aborts" 1 "false" ""

# TC-09: Build command failure
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_BUILD_FAIL="true" \
  run_scenario "TC-09" "Build failure halts execution" 1 "true" "Mock build execution error"

# TC-10: Missing CLI executable
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_OMIT_CLI_BINARY="true" \
  run_scenario "TC-10" "Missing CLI executable fails closed" 1 "true" "Expected executable not found"

# TC-11: Incorrect CLI version output
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="vericore version 1.9.9" \
  run_scenario "TC-11" "Incorrect CLI version output rejected" 1 "true" "Unexpected CLI version output"

# TC-12: Runtime packaging check failure
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="vericore version 2.0.0" MOCK_PACKAGING_FAIL="true" \
  run_scenario "TC-12" "Runtime packaging check failure halts execution" 1 "true" "Mock runtime packaging error"

# TC-13: Extracted default version is malformed
MOCK_APP_VERSION="corrupt-version" MOCK_GRADLE_VERSION="2.0.0" \
  run_scenario "TC-13" "Malformed default application version rejected" 1 "false" "not a valid release version format"

# TC-14: Unexpected additional arguments
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" \
  run_scenario "TC-14" "Unexpected additional arguments rejected" 1 "false" "Unexpected additional arguments" "2.0.0" "extra_arg"

# TC-15: Regex wildcard bypass attempt (2x0y0 must not match 2.0.0)
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="vericore version 2x0y0" \
  run_scenario "TC-15" "Regex wildcard false acceptance '2x0y0' rejected" 1 "true" "Unexpected CLI version output"

# TC-16: Correct version embedded in extra text rejected
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="extra text vericore version 2.0.0" \
  run_scenario "TC-16" "Correct version embedded in extra text rejected" 1 "true" "Unexpected CLI version output"

# TC-17: Multiple matching Version.current declarations rejected
MOCK_GRADLE_VERSION="2.0.0" MOCK_APP_VERSION_RAW='package com.vericore.core
object Version {
    const val current: String = "2.0.0"
    const val current: String = "2.0.1"
}' \
  run_scenario "TC-17" "Multiple Version.current declarations rejected" 1 "false" "multiple \(2\) Version\.current declarations"

# TC-18: Zero matching Version.current declarations rejected
MOCK_GRADLE_VERSION="2.0.0" MOCK_APP_VERSION_RAW='package com.vericore.core
object Version {
    const val legacy: String = "2.0.0"
}' \
  run_scenario "TC-18" "Zero Version.current declarations rejected" 1 "false" "No matching Version\.current declaration"

# TC-19: Incorrect case CLI version output rejected
MOCK_APP_VERSION="2.0.0" MOCK_GRADLE_VERSION="2.0.0" MOCK_CLI_OUTPUT="VERICORE VERSION 2.0.0" \
  run_scenario "TC-19" "Incorrect case CLI version output rejected" 1 "true" "Unexpected CLI version output"

echo ""
echo "=== Test Summary ==="
echo "Total:  $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $FAILED_TESTS"

if [ "$FAILED_TESTS" -ne 0 ]; then
  exit 1
fi

echo "All prepare-release.sh regression tests passed successfully."
