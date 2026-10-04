#!/usr/bin/env bash
#
# AUDIO-rec :: off-device test suite.
#
# Compiles the audio code under the bundled toolchain JRE (no Android SDK, no
# emulator, no phone) and runs all three layers:
#
#   1. FormatSelfTest   container round-trips: WAV / AIFF / FLAC / Ogg framing,
#                       counters, byte order, and the 48 kHz resampler
#   2. ReaderCheck      the "foreign file" corpus from gen_foreign.py - files no
#                       AUDIO-rec writer ever touched
#   3. format_check.py  an independent scorer that re-parses every artifact
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
    tools/test/stubs/com/mostakim/audiorec/audio/FormatProbe.java \
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
echo "all off-device checks passed"
