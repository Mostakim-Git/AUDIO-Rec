#!/usr/bin/env python3
"""Mutation test for the view-tree rules in tools/static_check.py.

    python3 tools/test/static_check_test.py

The blank-window bug class is "a view is built, filled with children, and then
never reaches a parent".  These rules exist to stop it coming back, so this test
re-introduces each real defect into a throw-away copy of the sources and asserts
the checker reports it.  A rule that cannot fail is not a rule.

Exit code is non-zero when a case slips through.
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(REPO, "app/src/main/java")
CHECKER = os.path.join(REPO, "tools/static_check.py")

# (name, file, text to remove, text to put in its place, expected message)
CASES = [
    ("Export Files list drops every row card",
     "com/mostakim/audiorec/ui/screens/ExportsScreen.java",
     "            col.addView(card);\n", "",
     "'card' gets children but is never added"),
    ("Device Presets list drops every preset card",
     "com/mostakim/audiorec/ui/screens/PresetsScreen.java",
     "            col.addView(card);\n", "",
     "'card' gets children but is never added"),
    ("Storage health card is built but never shown",
     "com/mostakim/audiorec/ui/screens/StorageScreen.java",
     "        mHealth.addView(card);\n", "",
     "'card' gets children but is never added"),
    ("a child laid out with no height and no weight",
     "com/mostakim/audiorec/ui/screens/StorageScreen.java",
     "    private void permissionsCard() {",
     "    private void invisible(LinearLayout col) {\n"
     "        col.addView(Ui.spacer(act, 4), new LinearLayout.LayoutParams(\n"
     "                ViewGroup.LayoutParams.MATCH_PARENT, 0));\n"
     "    }\n\n    private void permissionsCard() {",
     "x 0 with no weight is invisible"),
    ("a device read divided by the channel count (the force-stop of 1.0.x)",
     "com/mostakim/audiorec/audio/AudioEngine.java",
     "            int framesRead = decoded / channels;",
     "            int framesRead = read / channels;",
     "a device read counts bytes"),
    ("a screen that fills nothing in",
     "com/mostakim/audiorec/ui/screens/SessionsScreen.java",
     None, None,
     "page never adds anything to itself"),
]

EMPTY_SCREEN = """package com.mostakim.audiorec.ui.screens;

import android.widget.LinearLayout;

import com.mostakim.audiorec.ui.MainActivity;

/** a page that builds nothing: the blank-window shape, caught by the checker */
public class SessionsScreen extends Screen {
    public SessionsScreen(MainActivity a) {
        super(a, "Sessions", "does nothing", true);
    }

    @Override
    protected void build(LinearLayout col) {
    }
}
"""


def run_checker(root):
    out = subprocess.run([sys.executable, CHECKER, root], capture_output=True, text=True)
    return out.returncode, out.stdout


def main():
    clean_rc, clean_out = run_checker(SRC)
    failures = 0
    if clean_rc != 0:
        failures += 1
        print("  FAIL  the real sources do not pass the checker")
        print(clean_out)
    print("  the real sources pass the view-tree rules")

    for name, rel, old, new, expected in CASES:
        with tempfile.TemporaryDirectory() as tmp:
            root = os.path.join(tmp, "java")
            shutil.copytree(SRC, root)
            path = os.path.join(root, rel)
            if old is None:
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(EMPTY_SCREEN)
            else:
                with open(path, encoding="utf-8") as fh:
                    text = fh.read()
                if text.count(old) < 1:
                    print("  FAIL  %s: the mutation no longer applies (%s)" % (name, rel))
                    failures += 1
                    continue
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(text.replace(old, new, 1))
            rc, out = run_checker(root)
            if rc == 0 or expected not in out:
                print("  FAIL  %s: not reported" % name)
                failures += 1
            else:
                print("  ok    %s" % name)

    print()
    if failures:
        print("%d mutation(s) slipped through the checker" % failures)
        return 1
    print("all %d view-tree mutations are caught" % len(CASES))
    return 0


if __name__ == "__main__":
    sys.exit(main())
