#!/usr/bin/env bash
#
# AUDIO-rec :: off-device test suite.
#
# Compiles the audio code under the bundled toolchain JRE (no Android SDK, no
# emulator, no phone) and runs all three layers:
#
#   1. FormatSelfTest   container round-trips: WAV / AIFF / FLAC / Ogg framing,
#                       counters, byte order, the 48 kHz resampler and the
#                       sharing rules
#   2. ReaderCheck      the "foreign file" corpus from gen_foreign.py - files no
#                       AUDIO-rec writer ever touched
#   3. format_check.py  an independent scorer that re-parses every artifact
#   4. static_check.py  format strings and intent extras in the sources
#   5. minsdk_check.py  every API-30+ symbol, compiled against the API 29 jar
#   6. apk_check.py     the signed APK: offline, no MP3, components, branding
#
# Usage:  bash tools/test/run.sh [output-dir]        (default build/fmt, ignored)
set -euo pipefail
cd "$(dirname "$0")/../.."

TC=tools/.toolchain
OUT=${1:-build/fmt}
JAVA="$TC/jre/bin/java"

if [ ! -x "$JAVA" ]; then
    echo "toolchain JRE missing at $JAVA" >&2
    exit 1
fi

mkdir -p "$OUT/classes"
echo "==> ECJ compile (tests + the audio sources they exercise)"
"$JAVA" -jar "$TC/ecj-3.45.0.jar" -source 8 -target 8 -nowarn \
    -bootclasspath "$TC/android.jar:$TC/jdkbase" \
    -cp "$TC/android.jar:$TC/jdkbase" \
    -d "$OUT/classes" \
    tools/test/FormatSelfTest.java tools/test/ReaderCheck.java tools/test/ResamplerCheck.java \
    tools/test/ShareCheck.java \
    tools/test/stubs/com/mostakim/audiorec/audio/FormatProbe.java \
    app/src/main/java/com/mostakim/audiorec/share/ShareRules.java \
    app/src/main/java/com/mostakim/audiorec/util/Formats.java \
    app/src/main/java/com/mostakim/audiorec/audio/Pcm.java \
    app/src/main/java/com/mostakim/audiorec/audio/WavWriter.java \
    app/src/main/java/com/mostakim/audiorec/audio/AiffWriter.java \
    app/src/main/java/com/mostakim/audiorec/audio/FlacWriter.java \
    app/src/main/java/com/mostakim/audiorec/audio/OggWriter.java \
    app/src/main/java/com/mostakim/audiorec/audio/RawPcmReader.java \
    app/src/main/java/com/mostakim/audiorec/audio/AudioSink.java

rm -rf "$OUT/artifacts"
mkdir -p "$OUT/artifacts"
echo
echo "==> corpus of foreign files (built with struct, not with our writers)"
python3 tools/test/gen_foreign.py "$OUT/artifacts/foreign"

echo
echo "==> container, resampler and reader checks"
"$JAVA" -cp "$OUT/classes" FormatSelfTest "$OUT/artifacts"

echo
echo "==> independent container scorer"
python3 tools/format_check.py "$OUT/artifacts"

echo
echo "==> static checks over the sources"
python3 tools/static_check.py

echo
echo "==> minimum-SDK audit (API 30+ symbols against the API 29 platform)"
python3 tools/minsdk_check.py

echo
if [ -f release/AUDIO-rec.apk ]; then
    echo "==> release gate for the signed APK"
    if [ -n "$(find app/src/main -newer release/AUDIO-rec.apk -name '*.java' -o \
               -newer release/AUDIO-rec.apk -name '*.xml' 2>/dev/null | head -1)" ]; then
        echo "    note: the APK is older than the sources - rebuild first:"
        echo "          bash tools/build.sh --release"
    fi
    python3 tools/apk_check.py release/AUDIO-rec.apk | tail -n 4
else
    echo "==> release gate skipped: build the APK first (bash tools/build.sh --release)"
fi

echo
echo "all off-device checks passed"
