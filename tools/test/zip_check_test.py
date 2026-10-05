#!/usr/bin/env python3
"""Negative control for the shipped-dex rule in tools/zip_check.py.

A package check that only ever sees good packages proves nothing, so this test
mutates the released APK until the rule has to fire:

  1. the real APK passes the portable inspection (positive control),
  2. renaming Pcm.framesFromBytes inside its dex - the shape a regression back
     to the buggy frame arithmetic would take - makes the rule fail,
  3. the CLI reports exactly the failure it is supposed to, on that package.

Rewriting the archive drops the APK Signing Block and re-aligns stored entries,
so the mutated copy is expected to fail those checks too. Only the frame-math
line is asserted on, and the same file is shown passing before mutation.

Standard library only, no Android tools, runs anywhere.
"""

import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
APK = os.path.join(ROOT, "release", "AUDIO-rec.apk")
CHECKER = os.path.join(ROOT, "tools", "zip_check.py")
RULE = "byte-aware frame math"

# same length, so the dex stays well-formed byte for byte
NAME = b"framesFromBytes"
MUTANT = b"bytesFromFrames"


def run_checker(apk):
    proc = subprocess.run([sys.executable, CHECKER, apk],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return proc.returncode, proc.stdout.decode("utf-8", "replace")


def main():
    if not os.path.exists(APK):
        print("release/AUDIO-rec.apk is not built - nothing to mutate")
        return 1

    problems = []
    with zipfile.ZipFile(APK) as z:
        dex = z.read("classes.dex")

    if dex.count(NAME) < 1:
        print("the released dex does not name %s at all - the rule cannot be tested"
              % NAME.decode())
        return 1

    code, out = run_checker(APK)
    print("  the released package passes the inspection: %s"
          % ("yes" if code == 0 else "NO"))
    if code != 0:
        problems.append("the real APK failed its own inspection")
        print(out)

    # ---- mutation: the helper the fixed capture loop calls is gone ----------
    mutated = dex.replace(NAME, MUTANT)
    if mutated == dex:
        problems.append("mutation did not change the dex")
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import zip_check  # noqa: E402  (same module the CLI runs)

    _, _, before = zip_check.dex_strings_type_methods(dex)
    _, _, after = zip_check.dex_strings_type_methods(mutated)
    was = [m for m in before if m[1] == NAME.decode()]
    still = [m for m in after if m[1] == NAME.decode()]
    print("  the dex declares %s %d times; after the rename, %d"
          % (NAME.decode(), len(was), len(still)))
    if not was:
        problems.append("the parser missed the helper in the released dex")
    if still:
        problems.append("the parser still sees the renamed helper")

    temp = tempfile.mkdtemp(prefix="zip_check_test")
    try:
        mutant_apk = os.path.join(temp, "mutated.apk")
        with zipfile.ZipFile(APK) as src, zipfile.ZipFile(mutant_apk, "w",
                                                          zipfile.ZIP_DEFLATED) as dst:
            for info in src.infolist():
                data = mutated if info.filename == "classes.dex" else src.read(info.filename)
                dst.writestr(info, data)

        code, out = run_checker(mutant_apk)
        fired = [line for line in out.splitlines() if RULE in line and "FAIL" in line]
        print("  the mutated package is rejected: %s" % ("yes" if code != 0 else "NO"))
        print("  the rule that fires: %s" % (fired[0].strip() if fired else "none"))
        if code == 0:
            problems.append("the mutated package still passed the inspection")
        if not fired:
            problems.append("the mutation did not trip the %s rule" % RULE)

        # the positive control must not have been a fluke of the same run
        code, out = run_checker(APK)
        if code != 0 or RULE not in out or "FAIL" in out.split(RULE)[0][-80:]:
            problems.append("re-running the released package did not pass cleanly")
    finally:
        shutil.rmtree(temp, ignore_errors=True)

    print()
    if problems:
        for problem in problems:
            print("  PROBLEM: %s" % problem)
        return 1
    print("the package inspection catches a dex without the fixed frame arithmetic")
    return 0


if __name__ == "__main__":
    sys.exit(main())
