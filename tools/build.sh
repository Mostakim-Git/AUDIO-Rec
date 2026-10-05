#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# AUDIO-rec  ::  Gradle-free Android build
#
#   resources  aapt2 compile/link      -> build/base.apk + generated R.java
#   sources    ECJ (javac drop-in)     -> build/classes/**/*.class
#   dex        d8 (R8 8.2.2)           -> build/dex/classes.dex
#   package    tools/ziptool.py        -> build/AUDIO-rec-unsigned.apk
#   sign       apksigner               -> release/AUDIO-rec.apk
#
# usage:  ./tools/build.sh [--release|--debug] [--no-verify]
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/tools/.toolchain"
APP="$ROOT/app/src/main"
BUILD="$ROOT/build"
JAVA="$TC/jre/bin/java"
AAPT2="$TC/aapt2"

APP_ID="com.mostakim.audiorec"
# release/VERSION is the one place the version lives: the build, the tag the
# CI workflow attaches the APK to and the release notes all read it from here.
VERSION_NAME="$(cat "$ROOT/release/VERSION" 2>/dev/null | tr -d '[:space:]')"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
VERSION_CODE="$(echo "$VERSION_NAME" | awk -F. '{printf "%d", $1*10000 + $2*100 + $3}')"
MIN_SDK=29          # Android 10
TARGET_SDK=34       # Android 14
KS="$TC/audiorec-release.jks"
KS_PASS="audiorec"
KS_ALIAS="audiorec"

MODE="release"
VERIFY=1
for arg in "$@"; do
  case "$arg" in
    --debug)     MODE="debug" ;;
    --release)   MODE="release" ;;
    --no-verify) VERIFY=0 ;;
    *) echo "unknown option: $arg"; exit 2 ;;
  esac
done

[ -x "$JAVA" ] || { echo "!! toolchain missing - run ./tools/fetch-toolchain.sh"; exit 1; }
[ -f "$TC/android.jar" ] || { echo "!! android.jar missing - run ./tools/fetch-toolchain.sh"; exit 1; }
[ -f "$KS" ] || { echo "!! signing key missing - run ./tools/fetch-toolchain.sh"; exit 1; }

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/classes" "$BUILD/dex" "$BUILD/gen" "$ROOT/release"

# ------------------------------------------------------------------ resources
step "aapt2 compile (res -> flat resources)"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/res.zip"

step "aapt2 link (flat resources -> base.apk + R.java)"
"$AAPT2" link \
  -o "$BUILD/base.apk" \
  -I "$TC/android.jar" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --no-version-vectors \
  "$BUILD/res.zip"

# -------------------------------------------------------------------- compile
# android.jar is the boot classpath so the exact platform API is compiled
# against, but it has no java.lang.invoke package - the compiler needs
# LambdaMetafactory to represent lambdas, so those java.base classes are lifted
# out of the running runtime image once (see tools/JdkBaseExtract.java).
JDKBASE="$TC/jdkbase"
if [ ! -d "$JDKBASE" ]; then
  step "extracting java.lang.invoke support classes (first run only)"
  mkdir -p "$BUILD/tools"
  "$JAVA" -jar "$TC/ecj-3.45.0.jar" -source 8 -target 8 -proc:none -nowarn \
    -d "$BUILD/tools" "$ROOT/tools/JdkBaseExtract.java"
  "$JAVA" -cp "$BUILD/tools" JdkBaseExtract "$JDKBASE"
fi
BCP="$TC/android.jar:$JDKBASE"

step "ECJ compile (java 8 bytecode, android.jar bootclasspath)"
find "$APP/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
"$JAVA" -jar "$TC/ecj-3.45.0.jar" \
  -source 8 -target 8 \
  -bootclasspath "$BCP" \
  -cp "$BCP" \
  -proc:none -nowarn \
  -encoding UTF-8 \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt"

NB="$(find "$BUILD/classes" -name '*.class' | wc -l)"
echo "    compiled $NB classes"

# ------------------------------------------------------------------------ dex
step "d8 (dex + desugar)"
mkdir -p "$BUILD/dex"
"$JAVA" -cp "$TC/d8.jar" com.android.tools.r8.D8 \
  --min-api "$MIN_SDK" \
  --release \
  --lib "$TC/android.jar" \
  --lib "$TC/jdkbase" \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class')

# -------------------------------------------------------------------- package
step "packing APK (zipalign 4k, resources.arsc stored)"
rm -rf "$BUILD/apex" && mkdir -p "$BUILD/apex"
( cd "$BUILD/apex" && unzip -qo "$BUILD/base.apk" )

SPECS=()
while IFS= read -r f; do
  rel="${f#"$BUILD/apex/"}"
  SPECS+=("$rel::$f")
done < <(find "$BUILD/apex" -type f | sort)
for d in "$BUILD/dex"/*.dex; do
  SPECS+=("$(basename "$d")::$d")
done
python3 "$ROOT/tools/ziptool.py" "$BUILD/AUDIO-rec-unsigned.apk" 4096 "${SPECS[@]}"

# ----------------------------------------------------------------------- sign
step "apksigner ($MODE keystore: audiorec-release)"
OUT="$ROOT/release/AUDIO-rec.apk"
"$JAVA" -jar "$TC/apksigner.jar" sign \
  --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
  --min-sdk-version "$MIN_SDK" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$BUILD/AUDIO-rec-unsigned.apk" 2>/dev/null

if [ "$VERIFY" = "1" ]; then
  step "verify"
  "$JAVA" -jar "$TC/apksigner.jar" verify --min-sdk-version "$MIN_SDK" "$OUT" 2>/dev/null \
    && echo "    signature OK (v1+v2+v3)"

  # Which key signed this?  Only the certificate in tools/keys may ever be used:
  # a different one cannot be installed over an installed AUDIO-rec.
  PIN="$ROOT/tools/keys/cert.sha256"
  if [ -f "$PIN" ]; then
    want=$(tr -d '[:space:]' < "$PIN" | tr 'A-Z' 'a-z')
    got=$("$JAVA" -jar "$TC/apksigner.jar" verify --print-certs "$OUT" 2>/dev/null \
          | awk '/certificate SHA-256 digest/ {print tolower($NF); exit}')
    if [ "$got" != "$want" ]; then
      echo "!! the APK is signed by the wrong certificate" >&2
      echo "   expected $want (tools/keys/cert.sha256)" >&2
      echo "   found    $got" >&2
      exit 1
    fi
    echo "    signed by the repository certificate ($got)"
  fi
fi

step "done"
ls -la "$OUT"
sha256sum "$OUT" | awk '{print $1}' > "$ROOT/release/AUDIO-rec.apk.sha256"
echo "    SHA-256 $(cat "$ROOT/release/AUDIO-rec.apk.sha256")"
"$AAPT2" dump badging "$OUT" | head -4
