#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# AUDIO-rec  ::  offline toolchain bootstrap
#
# Downloads the exact, pinned set of build tools used by tools/build.sh into
# tools/.toolchain (git-ignored, ~155 MB).  Nothing here needs Android Studio,
# the Android SDK manager or Gradle - every component is fetched from a source
# that is reachable from a plain HTTPS-only sandbox:
#
#   * JRE 25        -> PyPI wheel `jdk4py`          (Temurin, manylinux x64)
#   * ECJ 3.45      -> npm tarball `@drxiaozhi/minapk` (Eclipse batch compiler,
#                      used as a drop-in `javac` replacement)
#   * android.jar   -> same npm tarball              (API 34 platform stubs)
#   * d8.jar        -> same npm tarball              (dexer from R8 8.2.2)
#   * apksigner.jar -> same npm tarball              (APK Signature Scheme v1/v2/v3)
#   * aapt2         -> npm tarball `aaptjs3`         (linux x64, aapt2 2.20)
#
# Run once:   ./tools/fetch-toolchain.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/tools/.toolchain"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TC"
need() { [ -e "$TC/$1" ] || return 0; return 1; }

echo "==> AUDIO-rec toolchain bootstrap"
echo "    target: $TC"

# ---------------------------------------------------------------- JRE -------
if [ ! -x "$TC/jre/bin/java" ]; then
  echo "--> fetching JRE (jdk4py / Temurin 25)"
  python3 -m pip download jdk4py --no-deps -d "$TMP/jre" -q
  ( cd "$TMP/jre" && unzip -q -o ./*.whl )
  cp -r "$TMP"/jre/jdk4py/java-runtime "$TC/jre"
  chmod +x "$TC/jre/bin/"*
else
  echo "--> JRE present"
fi
JAVA="$TC/jre/bin/java"

# ------------------------------------------------- npm-hosted android tools -
if [ ! -f "$TC/d8.jar" ] || [ ! -f "$TC/android.jar" ] || [ ! -f "$TC/ecj-3.45.0.jar" ] \
   || [ ! -f "$TC/apksigner.jar" ]; then
  echo "--> fetching Android build tools (minapk bundle)"
  curl -fsSL -o "$TMP/minapk.tgz" \
      "https://registry.npmjs.org/@drxiaozhi/minapk/-/minapk-0.4.0.tgz"
  mkdir -p "$TMP/minapk" && tar xzf "$TMP/minapk.tgz" -C "$TMP/minapk"
  cp "$TMP/minapk/package/tools/"{android.jar,d8.jar,ecj-3.45.0.jar,apksigner.jar} "$TC/"
else
  echo "--> d8 / android.jar / ecj / apksigner present"
fi

# --------------------------------------------------------------- aapt2 ------
if [ ! -x "$TC/aapt2" ]; then
  echo "--> fetching aapt2 (aaptjs3)"
  curl -fsSL -o "$TMP/aapt2.tgz" \
      "https://registry.npmjs.org/aaptjs3/-/aaptjs3-2.0.2.tgz"
  mkdir -p "$TMP/aapt2" && tar xzf "$TMP/aapt2.tgz" -C "$TMP/aapt2"
  cp "$TMP/aapt2/package/bin/x64/linux/aapt2" "$TC/aapt2"
  chmod +x "$TC/aapt2"
else
  echo "--> aapt2 present"
fi

# ------------------------------------------------------------ signing key ---
if [ ! -f "$TC/audiorec-release.jks" ]; then
  echo "--> generating AUDIO-rec signing key (self-signed, 10000 days)"
  "$JAVA" -jar "$TC/../.toolchain/jre/bin/../lib/jrt-fs.jar" >/dev/null 2>&1 || true
  "$TC/jre/bin/keytool" -genkeypair -v \
      -keystore "$TC/audiorec-release.jks" \
      -alias audiorec -keyalg RSA -keysize 4096 -validity 10000 \
      -storepass audiorec -keypass audiorec \
      -dname "CN=Mostakim Billah, OU=AUDIO-rec, O=AUDIO-rec Studio, L=Dhaka, C=BD" \
      >/dev/null
else
  echo "--> signing key present"
fi

"$TC/aapt2" version
"$JAVA" -cp "$TC/d8.jar" com.android.tools.r8.D8 --version
echo "==> toolchain ready"
