#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# AUDIO-rec  ::  offline toolchain bootstrap
#
# Downloads the exact, pinned set of build tools used by tools/build.sh into
# tools/.toolchain (git-ignored, ~155 MB).  Nothing here needs Android Studio,
# the Android SDK manager or Gradle - every component is fetched from a source
# that is reachable from a plain HTTPS-only sandbox:
#
#   * JRE 17        -> PyPI wheel `jdk4py`          (Temurin, manylinux x64)
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
# The version is pinned, and it has to be one d8 can read.  `pip download jdk4py`
# without a version takes whatever is newest, and the newest is a Java 25 runtime
# whose class files are major version 69: ECJ still compiles happily against them,
# but d8 (R8 8.2.2) refuses the java.* library classes with "Unsupported class
# file major version", which is a build that fails only on a machine that fetched
# the toolchain fresh.  Java 17 is what this toolchain is built for.
JDK4PY="17.0.9.2"
if [ ! -x "$TC/jre/bin/java" ]; then
  echo "--> fetching JRE (jdk4py $JDK4PY / Temurin 17)"
  python3 -m pip download "jdk4py==$JDK4PY" --no-deps -d "$TMP/jre" -q
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

# ------------------------------------------- API 29 platform jar (audit) -----
# tools/minsdk_check.py compiles the app against android-29.jar so that every
# API-30+ symbol shows up as a compile error.  GitHub's contents API cannot
# return a 20 MB file, so the jar comes down through its raw media type, pinned to
# a commit so the audit always compiles against the same platform jar.
if [ ! -f "$TC/android-29.jar" ]; then
  echo "--> fetching the API 29 platform jar (minimum-SDK audit)"
  if command -v gh >/dev/null 2>&1; then
    gh api "repos/Sable/android-platforms/contents/android-29/android.jar?ref=1e98db1a199e8f7f85541af26bfc27019501b132" \
        -H "Accept: application/vnd.github.raw" > "$TC/android-29.jar"
  else
    echo "    gh is not installed: the audit needs network access for this jar" >&2
  fi
fi

# --------------------------------------------- java.* for the batch compiler ---
# ECJ replaces javac here and needs a boot classpath; the app is compiled against
# android.jar, which carries no java.* classes, so they are lifted out of the JRE
# that is already in the toolchain.
if [ ! -d "$TC/jdkbase/java/lang" ]; then
  echo "--> extracting java.* from the JRE (boot classpath for ECJ)"
  mkdir -p "$TC/jdktools"
  "$JAVA" -jar "$TC/ecj-3.45.0.jar" -source 8 -target 8 -proc:none -nowarn \
      -d "$TC/jdktools" "$ROOT/tools/JdkBaseExtract.java" >/dev/null
  "$JAVA" -cp "$TC/jdktools" JdkBaseExtract "$TC/jdkbase" \
      java/lang java/lang/reflect java/lang/annotation java/lang/invoke \
      java/lang/constant java/util java/util/function java/util/concurrent \
      java/util/regex java/util/stream java/io java/text java/nio java/nio/charset \
      java/math java/security java/security/cert java/time >/dev/null
fi

# ------------------------------------------------------------ signing key ---
# The key lives in the repository (tools/keys/audiorec-release.jks) so every
# release, on any machine, is signed by the same certificate - without that, an
# installed AUDIO-rec refuses the next version as "App not installed", because
# Android will not replace an app whose signature changed.  It is a self-signed
# app-distribution key for an offline app: nothing else depends on it.
#
# It is never regenerated here.  A fresh key produces a different certificate and
# Android then refuses the new version with "App not installed" - and any app data
# (recordings, sessions) is lost when the old version has to be uninstalled first.
# That is exactly what happened between 1.0.3 and 1.0.4: the toolchain holding the
# key was rebuilt from scratch, the key was regenerated, and every published
# release up to 1.0.3 is signed by a certificate nobody has any more.  The key now
# lives in the repository and this script only ever copies or verifies it.
KEYS="$ROOT/tools/keys"
PIN="$KEYS/cert.sha256"
extract_cert() {
  "$TC/jre/bin/keytool" -list -v -keystore "$1" -storepass audiorec -alias audiorec 2>/dev/null \
      | awk '/SHA256:/ {print tolower($2); exit}' | tr -d ':'
}
if [ ! -f "$TC/audiorec-release.jks" ]; then
  if [ ! -f "$KEYS/audiorec-release.jks" ]; then
    cat >&2 <<'EOF'
!! the release signing key is missing from the repository (tools/keys/)

   Do not generate a new one casually: every installed copy of AUDIO-rec is signed
   with the key that is in the repository, and a different key means the next
   release cannot be installed over the old one.  If you really mean to start a
   new signing identity, run this script with AUDIOREC_NEW_KEY=1 and say so in the
   release notes.
EOF
    if [ "${AUDIOREC_NEW_KEY:-0}" != "1" ]; then
      exit 1
    fi
    echo "--> generating a NEW signing identity (AUDIOREC_NEW_KEY=1)"
    mkdir -p "$KEYS"
    "$TC/jre/bin/keytool" -genkeypair -v \
        -keystore "$KEYS/audiorec-release.jks" \
        -alias audiorec -keyalg RSA -keysize 4096 -validity 10000 \
        -storepass audiorec -keypass audiorec \
        -dname "CN=Mostakim Billah, OU=AUDIO-rec, O=AUDIO-rec Studio, L=Dhaka, C=BD" \
        >/dev/null
    extract_cert "$KEYS/audiorec-release.jks" > "$PIN"
    echo "    new certificate fingerprint written to tools/keys/cert.sha256"
  fi
  echo "--> using the repository signing key"
  cp "$KEYS/audiorec-release.jks" "$TC/audiorec-release.jks"
else
  echo "--> signing key present"
fi

got=$(extract_cert "$TC/audiorec-release.jks")
if [ -f "$PIN" ]; then
  want=$(tr -d '[:space:]' < "$PIN" | tr 'A-Z' 'a-z')
  if [ "$got" != "$want" ]; then
    echo "!! the signing key does not match tools/keys/cert.sha256" >&2
    echo "   expected $want" >&2
    echo "   found    $got" >&2
    echo "   every build signed with another key is an install that cannot upgrade." >&2
    exit 1
  fi
  echo "    signing certificate matches tools/keys/cert.sha256"
fi

# the runtime has to be one d8 can read - see the note above
major=$("$JAVA" -jar "$TC/ecj-3.45.0.jar" -version 2>/dev/null | head -1 || true)
jre_ver=$("$JAVA" -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
if [ -n "$jre_ver" ] && [ "$jre_ver" -gt 17 ] 2>/dev/null; then
  echo "!! the toolchain JRE is Java $jre_ver; d8 in this toolchain reads Java 17 (tools/fetch-toolchain.sh pins it)" >&2
  exit 1
fi
echo "    JRE: $("$JAVA" -version 2>&1 | head -1)"

"$TC/aapt2" version
"$JAVA" -cp "$TC/d8.jar" com.android.tools.r8.D8 --version
echo "==> toolchain ready"
