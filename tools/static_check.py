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

4. the view tree - the interface is built in Java, so a container that is filled
   in and then never handed to a parent draws nothing at all.  Four pages came
   out blank or half-blank this way (one card, two list loops, one empty state).
   Also caught here: a child laid out 0 pixels tall with no weight, which is
   invisible, and a layout resource being inflated when ``res/layout`` is empty.

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


# ------------------------------------------------------------------ view tree
# the shell, every page and every dialog is built in Java; nothing is inflated
VIEW_TYPES = ("LinearLayout|FrameLayout|RelativeLayout|TableLayout|TableRow|"
              "ScrollView|HorizontalScrollView|RadioGroup|View|ViewGroup")
VIEW_DECL = re.compile(r"\b(?:" + VIEW_TYPES + r")\s+(\w+)\s*=\s*(?:Ui\.\w+|new\s+\w+)")
LAYOUT_PARAMS = re.compile(r"new\s+(\w+)\.LayoutParams\s*\(")
# calls that hand a view to something that will attach it
# a page that reaches the window through one of the base-class helpers
PAGE_HELPERS = ("col.addView(", "card(", "cardStyled(", "empty(", "emptyCard(",
                "hairline(", "addContentView(")

ATTACHING_CALL = re.compile(r"\b(?:set|add|attach|show)[A-Za-z_]*\s*\(([^()]*)\)")


def enclosing_body(lines, index):
    """the text of the innermost block around ``index`` (a method or a loop)"""
    start = None
    for k in range(index, -1, -1):
        if re.match(r"^ {4,8}(?:@?[A-Za-z_][\w<>,.\[\]\s]*?)\s+\w+\s*\([^;]*\)\s*\{?\s*$",
                    lines[k]) and lines[k].count("(") == lines[k].count(")"):
            start = k
            break
    if start is None:
        return "\n".join(lines)
    depth = 0
    for j in range(start, len(lines)):
        depth += lines[j].count("{") - lines[j].count("}")
        if depth == 0 and j > start:
            return "\n".join(lines[start:j + 1])
    return "\n".join(lines[start:])


def call_arguments(text, open_paren):
    """text between the parentheses that start at ``open_paren``"""
    depth = 0
    for i in range(open_paren, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren + 1:i]
    return text[open_paren + 1:]


def top_level_args(text):
    out, depth, cur = [], 0, []
    for ch in text:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    out.append("".join(cur).strip())
    return out


def check_view_tree(root):
    """views that are built, filled with children, and then dropped"""
    sites = 0
    for path in java_files(root):
        text = open(path, encoding="utf-8", errors="replace").read()
        lines = text.split("\n")
        for i, line in enumerate(lines):
            m = VIEW_DECL.search(line)
            if not m:
                continue
            name = m.group(1)
            body = enclosing_body(lines, i)
            if not re.search(r"\b" + re.escape(name) + r"\.addView\(", body):
                continue
            sites += 1
            attached = (
                re.search(r"addView\(\s*" + re.escape(name) + r"\s*[,)]", body)
                or re.search(r"\breturn\s+" + re.escape(name) + r"\s*;", body)
                or re.search(r"=\s*" + re.escape(name) + r"\s*;", body.replace(line, "", 1))
                or any(re.search(r"\b" + re.escape(name) + r"\b", args)
                       for args in ATTACHING_CALL.findall(body))
            )
            if not attached:
                problems.append("%s:%d  '%s' gets children but is never added to a "
                                "parent, returned or stored - it draws nothing"
                                % (path, i + 1, name))
        for m in LAYOUT_PARAMS.finditer(text):
            args = top_level_args(call_arguments(text, m.end() - 1))
            line_no = text[:m.start()].count("\n") + 1
            if len(args) == 2 and args[1] == "0":
                problems.append("%s:%d  a child measured %s x 0 with no weight is "
                                "invisible" % (path, line_no, args[0]))
            elif len(args) == 3 and args[1] == "0" and args[2] in ("0", "0f", "0.0f"):
                problems.append("%s:%d  a child measured %s x 0 with weight 0 is "
                                "invisible" % (path, line_no, args[0]))
        if path.endswith(("Dialogs.java", "MainActivity.java")):
            if re.search(r"(?<!android\.)R\.layout\.", text):
                problems.append("%s  inflates a layout resource while res/layout is empty"
                                % path)
    for path in java_files(root):
        base = os.path.basename(path)
        if not base.endswith("Screen.java") or base == "Screen.java":
            continue
        text = open(path, encoding="utf-8", errors="replace").read()
        if not any(h in text for h in PAGE_HELPERS):
            problems.append("%s  page never adds anything to itself" % path)
    stats["view-tree sites"] = sites


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"
    res = "app/src/main/res"
    strings = read_strings(res) if os.path.isdir(res) else {}
    print("AUDIO-rec :: static checks over %s" % root)
    check_formats(root, strings)
    check_extras(root)
    check_view_tree(root)
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
    print("no format-string, intent-extra or view-tree problems")
    return 0


if __name__ == "__main__":
    sys.exit(main())
