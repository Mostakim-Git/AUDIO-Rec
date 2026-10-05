#!/usr/bin/env python3
"""Negative control for the signing-certificate rule in tools/apk_check.py.

    python3 tools/test/sign_check_test.py

The rule exists because of a real accident: the key that signed AUDIO-rec 1.0.0
through 1.0.3 was lost when the toolchain directory was rebuilt, a fresh key was
generated in its place, and the next build would have been an APK that Android
refuses to install over the one already on the phone.  So the gate now reads the
certificate out of the APK Signing Block and requires it to be one this project
owns.

A rule that only ever sees good input proves nothing.  This test signs a copy of
the released APK with a throw-away key and requires the gate to reject exactly
that, and only that, about it.

Standard library only; it shells out to the toolchain's keytool and apksigner.
"""

import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
APK = os.path.join(ROOT, "release", "AUDIO-rec.apk")
CHECKER = os.path.join(ROOT, "tools", "apk_check.py")
TC = os.path.join(ROOT, "tools", ".toolchain")
JAVA = os.path.join(TC, "jre", "bin", "java")
KEYTOOL = os.path.join(TC, "jre", "bin", "keytool")
APKSIGNER = os.path.join(TC, "apksigner.jar")
RULE = "signed by a certificate this project owns"


def run(cmd):
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)


def main():
    for tool in (JAVA, KEYTOOL, APKSIGNER):
        if not os.path.exists(tool):
            print("  skipped: the toolchain is not fetched (bash tools/fetch-toolchain.sh)")
            return 0
    if not os.path.exists(APK):
        print("  skipped: no built APK (bash tools/build.sh --release)")
        return 0

    tmp = tempfile.mkdtemp(prefix="audiorec-sign-")
    try:
        ks = os.path.join(tmp, "throwaway.jks")
        other = os.path.join(tmp, "other.apk")
        made = run([KEYTOOL, "-genkeypair", "-keystore", ks, "-alias", "throwaway",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                    "-storepass", "throwaway", "-keypass", "throwaway",
                    "-dname", "CN=Not The AUDIO-rec Key"])
        if made.returncode != 0:
            print("  FAIL  could not make a throw-away key:")
            print(made.stdout.decode("utf-8", "replace"))
            return 1
        signed = run([JAVA, "-jar", APKSIGNER, "sign", "--ks", ks,
                      "--ks-pass", "pass:throwaway", "--key-pass", "pass:throwaway",
                      "--out", other, APK])
        if signed.returncode != 0:
            print("  FAIL  could not re-sign the APK with the throw-away key")
            print(signed.stdout.decode("utf-8", "replace")[-800:])
            return 1

        # the released APK passes, the re-signed copy does not, and the only thing
        # wrong with it is whose key signed it
        good = run([sys.executable, CHECKER, APK]).stdout.decode("utf-8", "replace")
        bad = run([sys.executable, CHECKER, other]).stdout.decode("utf-8", "replace")
        if "release gate passed" not in good:
            print("  FAIL  the released APK does not pass its own gate")
            return 1
        # the gate prints each failure twice: where it is found, then in the summary
        fired = sorted({" ".join(line.split()) for line in bad.splitlines()
                        if line.startswith("  FAIL")})
        ok = len(fired) == 1 and RULE in fired[0]
        print("  the released APK passes; re-signed with another key it is rejected:")
        for line in fired:
            print("    " + line)
        if not ok:
            print("  FAIL  the certificate rule did not fire as the only problem "
                  "(%d problem(s) reported)" % len(fired))
            return 1
        print("the signing-certificate rule catches a package signed by another key")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
