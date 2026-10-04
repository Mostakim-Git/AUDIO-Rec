#!/usr/bin/env python3
"""Static checks for the mistakes the Java compiler and aapt2 cannot see.

    python3 tools/static_check.py [src-dir]

Three classes of bug, all of which compile cleanly and then fail on the phone:

1. format strings - ``String.format("... %d ...", a, b)`` and
   ``getString(R.string.x, args)`` where the number of arguments does not match
   the number of format specifiers.  Too few arguments throws
   MissingFormatArgumentException inside a UI callback.

2. intent/bundle extras - a key written by one screen and read with a different
   spelling somewhere else, so the value silently arrives as the default.  Every
   ``putExtra("k")``/``putExtras``-style write is matched against every
   ``get*Extra("k")`` read; a key that is only ever read (or only ever written)
   is reported, except for documented system keys.

3. string resources - a ``getString(R.string.x, n)`` call whose resource has a
   different number of placeholders, which either shows a literal ``%s`` or
   throws.

Exit code is non-zero when anything is found, so it can gate a build.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

SPEC = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")

# keys written by the Android framework or by another process; never "unread"
SYSTEM_KEYS = {
    "android.intent.extra.STREAM", "android.intent.extra.SUBJECT",
    "android.intent.extra.TEXT", "android.intent.extra.TITLE",
    "android.intent.extra.EMAIL", "android.intent.extra.MIME_TYPES",
}

problems = []
stats = {}


def java_files(root):
    for base, _, files in os.walk(root):
        for f in files:
            if f.endswith(".java"):
                yield os.path.join(base, f)


def specifiers(fmt):
    """count arguments a printf-style format string consumes"""
    n = 0
    for m in SPEC.finditer(fmt):
        if m.group(0) != "%%":
            n += 1
    return n


def split_args(text):
    """split a Java argument list on top-level commas"""
    args, depth, cur, in_str, esc = [], 0, "", False, False
    for ch in text:
        if in_str:
            cur += ch
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
            cur += ch
        elif ch in "([{":
            depth += 1
            cur += ch
        elif ch in ")]}":
            depth -= 1
            cur += ch
        elif ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur.strip())
    return args


def literal_concat(text):
    """join adjacent string literals: "a" + "b" -> ab, None if not constant"""
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', text)
    if not parts:
        return None
    stripped = re.sub(r'"(?:[^"\\]|\\.)*"', "", text)
    stripped = stripped.replace("+", "").strip()
    return "".join(parts) if not stripped else "".join(parts) + "\x00?"


def read_strings(res_dir):
    """name -> value from every values*/strings.xml"""
    out = {}
    for base, _, files in os.walk(res_dir):
        for f in files:
            if f != "strings.xml":
                continue
            try:
                tree = ET.parse(os.path.join(base, f))
            except ET.ParseError as e:
                problems.append("%s: cannot parse (%s)" % (os.path.join(base, f), e))
                continue
            for el in tree.getroot():
                if el.tag != "string" or "name" not in el.attrib:
                    continue
                # aapt escapes nothing here, but XML entities are already decoded
                out[el.attrib["name"]] = "".join(el.itertext())
    return out


def check_formats(root, strings):
    n = 0
    for path in java_files(root):
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r"String\.format\s*\(([^;]*?)\)\s*;", text, re.S):
            call = m.group(1)
            args = split_args(call)
            if len(args) < 2:
                continue
            fmt = literal_concat(args[1])
            if fmt is None or fmt.endswith("\x00?"):
                continue
            want, got = specifiers(fmt), len(args) - 2
            n += 1
            if want != got:
                problems.append("%s: String.format wants %d argument(s), got %d: %r"
                                % (os.path.relpath(path, root), want, got, fmt))
        # getString(R.string.x, a, b)
        for m in re.finditer(r"getString\s*\(\s*R\.string\.(\w+)\s*(,[^;]*?)?\)", text, re.S):
            name, rest = m.group(1), m.group(2)
            if name not in strings:
                problems.append("%s: R.string.%s does not exist"
                                % (os.path.relpath(path, root), name))
                continue
            want = specifiers(strings[name])
            got = len(split_args(rest[1:])) if rest else 0
            n += 1
            if want != got:
                problems.append("%s: R.string.%s has %d placeholder(s), called with %d argument(s)"
                                % (os.path.relpath(path, root), name, want, got))
    stats["format strings checked"] = n


def check_extras(root):
    writes, reads = {}, {}
    for path in java_files(root):
        rel = os.path.relpath(path, root)
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r'put(?:Extra|StringArrayListExtra)\s*\(\s*"([^"]+)"', text):
            writes.setdefault(m.group(1), []).append(rel)
        for m in re.finditer(r'get(?:String|Int|Long|Float|Boolean|Parcelable|Serializable|'
                             r'StringArrayList|CharSequence|Byte|Short|Double|Bundle)Extra\s*\(\s*"([^"]+)"',
                             text):
            reads.setdefault(m.group(1), []).append(rel)
        for m in re.finditer(r'(?:bundle|args|extras)\.get(?:String|Int|Long)\(\s*"([^"]+)"', text):
            reads.setdefault(m.group(1), []).append(rel)
    for key in sorted(reads):
        if key not in writes and key not in SYSTEM_KEYS:
            problems.append("intent extra %r is read in %s but never written by this app"
                            % (key, ", ".join(sorted(set(reads[key])))))
    for key in sorted(writes):
        if key not in reads:
            problems.append("intent extra %r is written in %s but never read"
                            % (key, ", ".join(sorted(set(writes[key])))))
    stats["extras written"] = len(writes)
    stats["extras read"] = len(reads)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"
    res = "app/src/main/res"
    strings = read_strings(res) if os.path.isdir(res) else {}
    print("AUDIO-rec :: static checks over %s" % root)
    check_formats(root, strings)
    check_extras(root)
    for k in sorted(stats):
        print("  %-24s %d" % (k, stats[k]))
    if problems:
        print()
        for p in problems:
            print("  %s" % p)
        print()
        print("%d problem(s) found" % len(problems))
        return 1
    print()
    print("no format-string or intent-extra problems")
    return 0


if __name__ == "__main__":
    sys.exit(main())
