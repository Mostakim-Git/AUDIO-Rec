#!/usr/bin/env python3
"""Independent inspection of the packaged APK - standard library only.

    python3 tools/zip_check.py release/AUDIO-rec.apk

`tools/apk_check.py` is the full release gate: it reads the binary manifest with
aapt2 and knows every component the app declares.  That needs the (gitignored)
toolchain, so it cannot run everywhere the APK is published from.  This script
is the portable half: it opens the package as a zip and checks the things that
must be true of the file itself, on any machine with Python.

  * the file is a real zip/APK and holds the three entries every APK needs
  * the dex is substantial (not a stub), carries no networking or MP3 code, and
    declares the byte-aware capture arithmetic the sources in the tree use
  * the signature is present three ways: v1 (META-INF), v2/v3 (APK Signing Block)
  * stored entries sit on 4 KiB boundaries (zipalign -p 4096)
  * the branding entries the app is built around are in the package

Exit code is non-zero when something does not hold.
"""
import os
import re
import struct
import sys
import zipfile

REQUIRED = ("AndroidManifest.xml", "classes.dex", "resources.arsc")
# strings that must not appear inside classes.dex
FORBIDDEN = (b"java/net/", b"java.net.", b"okhttp", b"lame", b"libmp3",
             b"MP3Encoder", b"MediaRecorder;")
# ... and no real URLs: the only http:// string an app carries is the AOSP
# attribute namespace, which is not an endpoint
URL = re.compile(rb"https?://[^\x00\"' ]{0,80}")
AOSP_NAMESPACE = b"http://schemas.android.com"
MIN_DEX = 100 * 1024
SIG_BLOCK = b"APK Sig Block 42"

problems = []
checks = [0]


def check(what, ok, detail=""):
    checks[0] += 1
    if ok:
        print("  ok    %s%s" % (what, ("  " + detail) if detail else ""))
    else:
        print("  FAIL  %s%s" % (what, ("  " + detail) if detail else ""))
        problems.append(what)


def dex_strings_type_methods(blob):
    """(strings, type descriptors, method ids) from a dex, with plain struct reads.

    A method id names its class, its shorty proto and its name; that is enough to
    check that the shipped bytecode really declares the fix instead of trusting
    that the APK in hand was built from the sources in the tree.
    """
    def u32(off):
        return struct.unpack_from("<I", blob, off)[0]

    def uleb(off):
        value = shift = 0
        while True:
            byte = blob[off]
            off += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                return off
            shift += 7

    def string_at(idx):
        # string_data_item: uleb128 utf16 length, then NUL-terminated MUTF-8
        off = uleb(u32(strings_off + 4 * idx))
        end = blob.index(b"\x00", off)
        return blob[off:end].decode("utf-8", "replace")

    strings_size, strings_off = u32(0x38), u32(0x3C)
    types_size, types_off = u32(0x40), u32(0x44)
    protos_off = u32(0x4C)
    # header: proto_ids at 0x48, field_ids at 0x50, method_ids at 0x58
    methods_size, methods_off = u32(0x58), u32(0x5C)

    strings = [string_at(i) for i in range(strings_size)]
    types = [strings[u32(types_off + 4 * i)] for i in range(types_size)]

    methods = []
    for i in range(methods_size):
        class_idx, proto_idx, name_idx = struct.unpack_from(
            "<HHI", blob, methods_off + 8 * i)
        shorty = strings[u32(protos_off + 12 * proto_idx)]
        methods.append((types[class_idx], strings[name_idx], shorty))
    return strings, types, methods


def main(path):
    if not os.path.isfile(path):
        print("  FAIL  %s does not exist" % path)
        return 1
    raw = open(path, "rb").read()
    print("AUDIO-rec :: zip inspection of %s (%d bytes)" % (path, len(raw)))

    check("file starts with the zip magic", raw[:4] == b"PK\x03\x04")
    check("archive is intact (testzip)", zipfile.ZipFile(path).testzip() is None)

    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        infos = {i.filename: i for i in z.infolist()}

        for name in REQUIRED:
            check("entry %s" % name, name in infos)

        missing = [n for n in names if n.lower().endswith((".mp3", ".mp2", ".mp1"))]
        check("no MP3 audio entries", not missing, str(missing) if missing else "")

        dex = infos.get("classes.dex")
        if dex:
            check("classes.dex is substantial", dex.file_size >= MIN_DEX,
                  "%d bytes" % dex.file_size)
            blob = z.read("classes.dex")
            for needle in FORBIDDEN:
                check("dex is free of %r" % needle.decode("ascii", "replace"),
                      needle not in blob)
            found = [u for u in URL.findall(blob) if not u.startswith(AOSP_NAMESPACE)]
            strings, types, methods = dex_strings_type_methods(blob)
            declared = {(cls, name): shorty for cls, name, shorty in methods}
            # a shorty proto is one character per type - return type first - so
            # int f(int, int, int) is "IIII"
            frame = declared.get(("Lcom/mostakim/audiorec/audio/Pcm;", "framesFromBytes"))
            check("shipped dex declares the byte-aware frame math (Pcm.framesFromBytes)",
                  frame == "IIII", "int (int, int, int) -> %s" % (frame or "missing"))
            samples = declared.get(("Lcom/mostakim/audiorec/audio/Pcm;", "samplesFromBytes"))
            check("shipped dex declares Pcm.samplesFromBytes", samples == "III",
                  "int (int, int) -> %s" % (samples or "missing"))
            decode = declared.get(
                ("Lcom/mostakim/audiorec/audio/AudioEngine;", "decodeBytes"))
            check("AudioEngine.decodeBytes reports a sample count in the shipped dex",
                  bool(decode) and decode[0] == "I", "shorty %s" % (decode or "missing"))
            check("dex carries no network URLs", not found,
                  ", ".join(u.decode("ascii", "replace") for u in found[:3]))

        # stored entries (no compression) must be 4 KiB aligned for mmap loading
        bad = []
        for i in z.infolist():
            if i.compress_type == zipfile.ZIP_STORED:
                if i.header_offset + 30 + len(i.filename) + len(i.extra) != \
                        (i.header_offset + 30 + len(i.filename) + len(i.extra) + 4095) // 4096 * 4096:
                    bad.append(i.filename)
        check("stored entries are 4 KiB aligned", not bad,
              "%d entries" % sum(1 for i in z.infolist() if i.compress_type == zipfile.ZIP_STORED))

        v1 = [n for n in names if n.startswith("META-INF/")
              and n.upper().endswith((".RSA", ".DSA", ".EC"))]
        check("v1 signature present", bool(v1), ", ".join(v1))

        branding = [n for n in names if n.startswith("res/mipmap-")]
        check("launcher icons in the package", len(branding) >= 4,
              "%d entries" % len(branding))
        check("adaptive icon (anydpi-v26)", any("mipmap-anydpi-v26" in n for n in names))
        check("brand artwork in the package",
              any("brand_logo" in n for n in names))

    # v2/v3: the APK Signing Block sits just before the end-of-central-directory
    eocd = raw.rfind(b"PK\x05\x06")
    check("central directory found", eocd > 0)
    if eocd > 0:
        cd_offset = struct.unpack_from("<I", raw, eocd + 16)[0]
        check("v2/v3 signing block present", SIG_BLOCK in raw[max(0, cd_offset - 4096):cd_offset],
              "APK Signing Block before the central directory")

    print()
    if problems:
        print("%d problem(s) found in %d checks" % (len(problems), checks[0]))
        return 1
    print("%d zip checks passed" % checks[0])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "release/AUDIO-rec.apk"))
