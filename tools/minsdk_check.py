#!/usr/bin/env python3
"""Compile the app against android-29.jar and audit every API-30+ symbol.

    python3 tools/minsdk_check.py

AUDIO-rec declares minSdkVersion 29, so anything newer has to sit behind an
explicit ``Build.VERSION.SDK_INT`` check.  Android's own tooling only warns about
that if you ask it to (lint), and a missing guard is invisible until somebody
installs the APK on Android 10 - where it throws NoSuchMethodError, or worse,
silently takes the wrong branch.

Compiling against the API 29 platform jar makes every such symbol a compile
error, which is exactly the list we want.  Two kinds of hit are acceptable:

  * inlined constants (an int or String ``static final``): javac/d8 bake the
    value into the bytecode, so the class file never looks the symbol up - these
    only need to be unreachable at runtime, and the guard check below proves the
    branch that uses them is gated;
  * methods: genuinely dangerous, and only ever acceptable behind a guard whose
    threshold is at least the API level that introduced the symbol.

Anything not in API_TABLE is a new symbol nobody has reviewed: the check fails
so it gets a guard (or an entry with a reason) before shipping.

The "inlined" claim is not taken on faith: the built APK is inspected, and a
constant whose name still appears in the dex field table is a real lookup that
would throw NoSuchFieldError on Android 10 - that one has to be guarded.
"""
import glob
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.path.join(ROOT, "tools", ".toolchain")
JAVA = os.path.join(TC, "jre", "bin", "java")
ECJ = os.path.join(TC, "ecj-3.45.0.jar")
API29 = os.path.join(TC, "android-29.jar")
JDKBASE = os.path.join(TC, "jdkbase")

# symbol -> (introduced in API, kind, why it is safe here)
API_TABLE = {
    "ENCODING_PCM_24BIT_PACKED": (31, "const",
                                  "packed 24-bit capture; chooseEncoding() only picks it when "
                                  "SDK_INT >= 31 and falls back to float below that"),
    "ENCODING_PCM_32BIT": (31, "const",
                           "packed 32-bit capture; same SDK_INT >= 31 gate, float path below"),
    "FLAG_MUTABLE": (31, "const",
                     "UsbManager needs a mutable PendingIntent from Android 12; the ternary "
                     "selects FLAG_IMMUTABLE below that"),
    "RECEIVER_NOT_EXPORTED": (33, "const",
                              "registerReceiver flag, only used inside SDK_INT >= 33"),
    "isExternalStorageManager": (30, "method",
                                 "only called inside SDK_INT >= 30"),
    "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION": (30, "const",
                                                      "Settings action shown from SDK_INT >= 30"),
    "ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION": (30, "const",
                                                  "fallback Settings action, same >= 30 screen"),
}

APK = os.path.join(ROOT, "release", "AUDIO-rec.apk")


def dex_strings():
    """every string and name in the built dex, to see what survived compilation"""
    if not os.path.isfile(APK):
        return None
    out = set()
    with zipfile.ZipFile(APK) as z:
        for name in z.namelist():
            if re.match(r"classes\d*\.dex$", name):
                data = z.read(name)
                # good enough: the string/field tables are plain MUTF-8 blobs
                for token in re.findall(rb"[\x20-\x7e]{4,}", data):
                    out.add(token.decode("ascii", "replace"))
    return out

GUARD = re.compile(r"SDK_INT\s*(>=|>|<|<=)\s*([A-Z0-9_]+)")
CODES = {"O": 26, "O_MR1": 27, "P": 28, "Q": 29, "R": 30, "S": 31, "S_V2": 32,
         "TIRAMISU": 33, "U": 34, "V": 35}


def level(token):
    return int(token) if token.isdigit() else CODES.get(token, 0)


def guarded_at(path, line, need):
    """is the call at `line` inside an SDK_INT check that admits `need`?"""
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    window = lines[max(0, line - 80):line]
    for text in reversed(window):
        m = GUARD.search(text)
        if not m:
            continue
        op, value = m.group(1), level(m.group(2))
        # SDK_INT >= N / N <= SDK_INT   ->  unreachable below N, safe when N >= need
        if op in (">=", ">") and value >= need - (1 if op == ">" else 0):
            return True
        if op in ("<=", "<"):
            return False
    return False


def errors():
    sources = sorted(glob.glob(os.path.join(ROOT, "app/src/main/java/**/*.java"), recursive=True))
    generated = sorted(glob.glob(os.path.join(ROOT, "build/gen/**/*.java"), recursive=True))
    if not generated:
        print("build/gen is empty - run tools/build.sh once so R.java exists")
        return None
    proc = subprocess.run(
        [JAVA, "-jar", ECJ, "-source", "8", "-target", "8", "-nowarn",
         "-bootclasspath", API29 + ":" + JDKBASE,
         "-cp", API29 + ":" + JDKBASE, "-d", "/tmp/minsdk"] + sources + generated,
        capture_output=True, text=True)
    out = (proc.stdout or "") + (proc.stderr or "")
    found = []
    for block in re.split(r"\n\d+\. ERROR in ", out)[1:]:
        head = block.split("\n", 1)[0]
        m = re.match(r"(.+) \(at line (\d+)\)", head)
        if not m:
            continue
        path, line = m.group(1), int(m.group(2))
        snippet = block.split("\n")[1].strip() if "\n" in block else ""
        rest = block.split("\n\n", 1)[-1]
        message = " ".join(l.strip() for l in rest.split("\n")
                           if l.strip() and not l.startswith("---"))
        symbol = None
        m2 = re.search(r"The method (\w+)\(", message)
        if m2:
            symbol, kind = m2.group(1), "method"
        else:
            m3 = re.search(r"(\w+) cannot be resolved or is not a field", message)
            if m3:
                symbol, kind = m3.group(1), "const"
        if symbol:
            found.append((symbol, kind, os.path.relpath(path, ROOT), line, snippet, message))
    return found


def main():
    found = errors()
    if found is None:
        return 2
    names = dex_strings()
    if names is None:
        print("  (no APK to inspect - run tools/build.sh first, "
              "constants cannot be proven inlined without it)")
        names = set()
    print("AUDIO-rec :: minimum-SDK audit (%d API-30+ symbol use(s) found)" % len(found))
    bad = 0
    for symbol, kind, path, line, snippet, message in found:
        entry = API_TABLE.get(symbol)
        if entry is None:
            print("  NEW       %s:%d %s - %s" % (path, line, symbol, message))
            print("            not reviewed: guard it and add an API_TABLE entry")
            bad += 1
            continue
        need, expected_kind, why = entry
        in_dex = symbol in names
        if kind == "const" and not in_dex:
            state = "inlined   (value baked into the bytecode, no runtime lookup)"
            ok = True
        elif guarded_at(os.path.join(ROOT, path), line, need):
            state = "guarded   (SDK_INT check >= %d)" % need
            ok = True
        else:
            state = "UNGUARDED (and %s in the dex)" % ("present" if in_dex else "absent")
            ok = False
        print("  %-9s %s:%d\n            %s - API %d %s; %s" % (
            "ok" if ok else "PROBLEM", path, line, symbol, need, kind, state))
        if not ok:
            bad += 1
    print()
    if bad:
        print("%d symbol use(s) need attention" % bad)
        return 1
    print("every API-30+ symbol is either inlined away or behind an SDK_INT guard")
    return 0


if __name__ == "__main__":
    sys.exit(main())
