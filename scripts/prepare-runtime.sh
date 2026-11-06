#!/usr/bin/env bash
set -euo pipefail

DIST_DIR="${1:?usage: prepare-runtime.sh <distribution-directory>}"
APP_HOME="$(cd "$DIST_DIR" && pwd)"
JDK_HOME="${JAVA_HOME:-}"
if [[ -z "$JDK_HOME" ]]; then
  if [[ "$(uname -s)" == "Darwin" ]]; then
    JDK_HOME="$(/usr/libexec/java_home)"
  else
    JAVA_BIN="$(command -v java)"
    JDK_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
  fi
fi
JDEPS="$JDK_HOME/bin/jdeps"
JLINK="$JDK_HOME/bin/jlink"

if [[ ! -x "$JDEPS" || ! -x "$JLINK" ]]; then
  echo "ERROR: a JDK with jdeps and jlink is required." >&2
  exit 1
fi

rm -rf "$APP_HOME/jre"
JARS=()
while IFS= read -r jar; do
  JARS+=("$jar")
done < <(find "$APP_HOME/lib" -type f -name '*.jar' | sort)
if [[ ${#JARS[@]} -eq 0 ]]; then
  echo "ERROR: no application jars found under $APP_HOME/lib" >&2
  exit 1
fi

APP_JAR=""
DEPENDENCY_CLASSPATH=""
for jar in "${JARS[@]}"; do
  case "$(basename "$jar")" in
    vericore-*.jar)
      if [[ -n "$APP_JAR" ]]; then
        echo "ERROR: multiple Vericore application jars found under $APP_HOME/lib" >&2
        exit 1
      fi
      APP_JAR="$jar"
      ;;
  esac
done

if [[ -z "$APP_JAR" ]]; then
  echo "ERROR: Vericore application jar not found under $APP_HOME/lib" >&2
  exit 1
fi

for jar in "${JARS[@]}"; do
  if [[ "$jar" == "$APP_JAR" ]]; then
    continue
  fi
  if [[ -z "$DEPENDENCY_CLASSPATH" ]]; then
    DEPENDENCY_CLASSPATH="$jar"
  else
    DEPENDENCY_CLASSPATH="$DEPENDENCY_CLASSPATH:$jar"
  fi
done

MODULES="$($JDEPS \
  --multi-release 25 \
  --ignore-missing-deps \
  --print-module-deps \
  --recursive \
  --class-path "$DEPENDENCY_CLASSPATH" \
  "$APP_JAR" \
  | tail -n 1 \
  | tr -d '[:space:]')"
if [[ -z "$MODULES" ]]; then
  echo "ERROR: jdeps returned no runtime modules." >&2
  exit 1
fi
MODULES="$MODULES,jdk.crypto.ec,java.net.http"

# Temurin 24+ enables JEP 493 runtime-image linking. Avoid jlink service binding:
# service binding can pull jdk.jlink into the new image, which cannot be
# re-linked in this mode. Runtime providers required by Vericore are explicit
# above and in the jdeps-derived module set.
"$JLINK" \
  --add-modules "$MODULES" \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --output "$APP_HOME/jre"

SCRIPT="$APP_HOME/bin/vericore"
if [[ ! -f "$SCRIPT" ]]; then
  echo "ERROR: launcher not found: $SCRIPT" >&2
  exit 1
fi

if ! grep -q 'VERICORE_BUNDLED_JAVA' "$SCRIPT"; then
  TMP_SCRIPT="$SCRIPT.tmp"
  awk '
    /^[[:space:]]*if \[ -n "\$JAVA_HOME" \]/ && !inserted {
      print "# Prefer the runtime bundled with this distribution."
      print "if [ -x \"$APP_HOME/jre/bin/java\" ]; then"
      print "    JAVA_HOME=\"$APP_HOME/jre\""
      print "    export JAVA_HOME"
      print "fi"
      print "# VERICORE_BUNDLED_JAVA"
      inserted = 1
    }
    { print }
    END {
      if (!inserted) {
        print "ERROR: could not find Gradle JAVA_HOME selection block in launcher." > "/dev/stderr"
        exit 2
      }
    }
  ' "$SCRIPT" > "$TMP_SCRIPT"
  mv "$TMP_SCRIPT" "$SCRIPT"
fi

chmod +x "$SCRIPT"
"$APP_HOME/jre/bin/java" -version
"$SCRIPT" --version
