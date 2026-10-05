#!/usr/bin/env bash
#
# AUDIO-rec :: headless UI construction test.
#
# Compiles the app's real ui/ sources against stand-in Android classes and
# stand-in engines, then runs MainActivity.onCreate(), the sidebar and all twelve
# screens without a device, measures and lays the view tree out, and inspects it.
#
# This is the test that catches "the app opens onto a blank window": the first
# shipped build did exactly that, because the shell layout looked for its panes in
# onFinishInflate(), which only the XML inflater ever calls.
#
# Usage:  bash tools/test/ui/run.sh
set -euo pipefail
cd "$(dirname "$0")/../../.."

TC=tools/.toolchain
OUT=build/ui
JAVA="$TC/jre/bin/java"
AAPT2="$TC/aapt2"
APP=app/src/main
JDKBASE_UI="$OUT/jdkbase"

[ -x "$JAVA" ] || { echo "toolchain JRE missing at $JAVA" >&2; exit 1; }

mkdir -p "$OUT/classes"

# --------------------------------------------------------------- java.* for ECJ
# The app is compiled against android.jar normally, which also carries the core
# java.* stubs.  Here android.* comes from the harness stubs instead, so java.*
# has to be lifted out of the running runtime image.
if [ ! -d "$JDKBASE_UI/java/lang" ]; then
    echo "==> extracting java.* from the runtime image (first run only)"
    mkdir -p "$OUT/tools"
    "$JAVA" -jar "$TC/ecj-3.45.0.jar" -source 8 -target 8 -proc:none -nowarn \
        -d "$OUT/tools" tools/JdkBaseExtract.java >/dev/null
    "$JAVA" -cp "$OUT/tools" JdkBaseExtract "$JDKBASE_UI" \
        java/lang java/lang/reflect java/lang/annotation java/lang/invoke \
        java/lang/constant java/util java/util/function java/util/concurrent \
        java/util/regex java/util/stream java/io java/text java/nio java/nio/charset \
        java/math java/security java/security/cert java/time >/dev/null
fi

# ------------------------------------------------------------------- resources
# Only R.java is needed, and it is written to the same build/gen the package build
# and the minimum-SDK audit use, so a fresh clone needs no full build before the
# tests can run.
RGEN=build/gen
if [ ! -f "$RGEN/com/mostakim/audiorec/R.java" ]; then
    echo "==> aapt2 (generating R.java into $RGEN)"
    mkdir -p "$RGEN"
    "$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip"
    "$AAPT2" link -o "$OUT/base.apk" -I "$TC/android.jar" \
        --manifest "$APP/AndroidManifest.xml" --java "$RGEN" \
        --min-sdk-version 29 --target-sdk-version 34 \
        --version-code 1 --version-name 1.0.0 --no-version-vectors \
        "$OUT/res.zip" >/dev/null
fi

# --------------------------------------------------------------------- compile
echo
echo "==> ECJ compile (harness stubs + fakes + the real ui sources)"
{
    find tools/test/ui/stubs tools/test/ui/fakes -name '*.java'
    find app/src/main/java/com/mostakim/audiorec/ui -name '*.java'
    find "$RGEN" -name 'R.java'
    echo app/src/main/java/com/mostakim/audiorec/db/Models.java
    echo app/src/main/java/com/mostakim/audiorec/util/Prefs.java
    echo app/src/main/java/com/mostakim/audiorec/util/Fmt.java
    echo app/src/main/java/com/mostakim/audiorec/util/Formats.java
    echo app/src/main/java/com/mostakim/audiorec/util/Ids.java
    echo app/src/main/java/com/mostakim/audiorec/share/Downloads.java
    echo app/src/main/java/com/mostakim/audiorec/audio/Pcm.java
    echo app/src/main/java/com/mostakim/audiorec/audio/AudioDevice.java
    echo tools/test/ui/UiSmokeTest.java
} > "$OUT/sources.txt"

rm -rf "$OUT/classes"
mkdir -p "$OUT/classes"
"$JAVA" -jar "$TC/ecj-3.45.0.jar" \
    -source 8 -target 8 -proc:none -nowarn -encoding UTF-8 \
    -bootclasspath "$JDKBASE_UI" \
    -cp "$JDKBASE_UI" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt" 2>&1 | grep -v "^$" || true

if [ ! -f "$OUT/classes/UiSmokeTest.class" ]; then
    echo
    echo "!! the harness did not compile" >&2
    exit 1
fi

# ------------------------------------------------------------------------- run
echo
echo "==> window construction, every screen, at phone and tablet size"
"$JAVA" -cp "$OUT/classes" UiSmokeTest "$@"
