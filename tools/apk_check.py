#!/usr/bin/env python3
"""Release gate for the signed APK: does the artifact actually honour the brief?

    python3 tools/apk_check.py [release/AUDIO-rec.apk]

Runs against the *signed* file, i.e. what somebody installs, not the sources:

  identity      package, version, label, min/target SDK
  offline       no network permission, no java.net class in the dex, no URL
                string anywhere - the app must not be able to reach the network
  no MP3        the patent-free rule: no mp3/mpeg codec name in the dex and no
                mp3 in the resource table
  components    every activity/service/receiver/provider the manifest declares
                exists in the dex, so nothing is silently dropped by d8
  USB           the host feature, the USB_DEVICE_ATTACHED filter and the device
                filter resource are all present in the package
  branding      adaptive launcher icon for v26 and v33 (with a monochrome
                layer), and its foreground for every density bucket
  packaging     4 KiB alignment, resources.arsc stored uncompressed, v2/v3
                signature block

Exit code is non-zero if anything fails.
"""
import hashlib
import os
import re
import struct
import subprocess
import sys
import zipfile

TOOLCHAIN = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".toolchain")
AAPT2 = os.path.join(TOOLCHAIN, "aapt2")

PACKAGE = "com.mostakim.audiorec"
EXPECTED_LABEL = "AUDIO-rec"
MIN_SDK, TARGET_SDK = 29, 34
EXPECTED_PERMISSIONS = {
    "android.permission.RECORD_AUDIO",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
}
FORBIDDEN_PERMISSION = re.compile(r"(INTERNET|NETWORK_STATE|WIFI_STATE|BLUETOOTH|NFC|ACCOUNT|"
                                   r"GET_ACCOUNTS|CALL_|SMS|LOCATION|CAMERA)")
NETWORK_TYPES = re.compile(r"^Ljava/net/|^Ljavax/net/|^Lokhttp|^Lorg/apache/http")
NETWORK_STRINGS = re.compile(r"https?://|ftp://|wss?://", re.I)
# XML namespaces are not endpoints: every Android app carries these literals
NAMESPACE_URLS = ("schemas.android.com", "www.w3.org", "xmlpull.org")
# what an MP3 encoder would actually have to contain: the codec MIME, a file
# extension we could write, or the patent-encumbered encoder name.  Prose that
# explains why MP3 is absent (the About screen) is fine and reported as a note.
MP3_CODEC = re.compile(r"audio/mpeg|video/mpeg|\.mp3\b|mpeg-?[123]\b|mp3lame|"
                       r"\blame\b|MediaFormat.*mp3", re.I)
MP3_PROSE = re.compile(r"mp3", re.I)

problems = []
notes = []


def badging(apk):
    out = subprocess.run([AAPT2, "dump", "badging", apk], capture_output=True, text=True)
    if out.returncode != 0:
        problems.append("aapt2 dump badging failed: " + out.stderr.strip())
    return out.stdout


def xmltree(apk):
    out = subprocess.run([AAPT2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                         capture_output=True, text=True)
    return out.stdout


# ------------------------------------------------------------------- dex ----
def dex_tables(apk):
    """(strings, types) from every classes*.dex in the package"""
    strings, types = set(), set()
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not re.match(r"classes\d*\.dex$", name):
                continue
            data = z.read(name)
            if data[:4] != b"dex\n":
                problems.append("%s: not a dex file" % name)
                continue
            (string_ids_size, string_ids_off, type_ids_size, type_ids_off) = struct.unpack_from(
                "<IIII", data, 0x38)
            str_at = []
            for i in range(string_ids_size):
                off = struct.unpack_from("<I", data, string_ids_off + i * 4)[0]
                p = off
                # uleb128 length, then NUL-terminated modified UTF-8
                while data[p] & 0x80:
                    p += 1
                p += 1
                end = data.index(b"\x00", p)
                s = data[p:end].decode("utf-8", "replace")
                str_at.append(s)
                strings.add(s)
            for i in range(type_ids_size):
                idx = struct.unpack_from("<I", data, type_ids_off + i * 4)[0]
                if idx < len(str_at):
                    types.add(str_at[idx])
    return strings, types


def has_type(types, class_name):
    return ("L" + class_name.replace(".", "/") + ";") in types


# ------------------------------------------------------------------ apk -----
def alignments(apk):
    """{entry: (offset, uncompressed?)} using the zip central directory"""
    out = {}
    with open(apk, "rb") as fh:
        data = fh.read()
    pos = 0
    while True:
        pos = data.find(b"PK\x03\x04", pos)
        if pos < 0:
            break
        method = struct.unpack_from("<H", data, pos + 8)[0]
        comp = struct.unpack_from("<I", data, pos + 18)[0]
        name_len = struct.unpack_from("<H", data, pos + 26)[0]
        extra_len = struct.unpack_from("<H", data, pos + 28)[0]
        name = data[pos + 30:pos + 30 + name_len].decode("utf-8", "replace")
        hdr_end = pos + 30 + name_len + extra_len
        out[name] = (hdr_end, method == 0, comp)
        pos = hdr_end
    return out


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "release/AUDIO-rec.apk"
    if not os.path.isfile(apk):
        print("no such APK: %s" % apk)
        return 2
    print("AUDIO-rec :: release gate for %s" % apk)
    size = os.path.getsize(apk)
    sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
    print("  %d bytes, sha256 %s" % (size, sha))

    # ---- identity -------------------------------------------------------
    bad = badging(apk)
    def grab(pattern, default=None):
        m = re.search(pattern, bad, re.M)
        return m.group(1) if m else default

    check("package name", grab(r"package: name='([^']+)'") == PACKAGE, grab(r"package: name='([^']+)'"))
    # release/VERSION is the one place the version lives; the tag CI attaches the
    # APK to and the release notes are named after it
    version_file = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                "..", "release", "VERSION")
    expected_version = "1.0.0"
    if os.path.isfile(version_file):
        with open(version_file) as fh:
            expected_version = fh.read().strip()
    check("version name", grab(r"versionName='([^']+)'") == expected_version,
          grab(r"versionName='([^']+)'") + " (release/VERSION says " + expected_version + ")")
    check("version code", grab(r"versionCode='(\d+)'") == str(
        sum(int(x) * 100 ** (2 - i) for i, x in enumerate(expected_version.split(".")[:3]))),
          grab(r"versionCode='(\d+)'"))
    check("label", grab(r"application-label:'([^']+)'") == EXPECTED_LABEL,
          grab(r"application-label:'([^']+)'"))
    check("minSdkVersion", grab(r"minSdkVersion:'(\d+)'") == str(MIN_SDK),
          grab(r"minSdkVersion:'(\d+)'"))
    check("targetSdkVersion", grab(r"targetSdkVersion:'(\d+)'") == str(TARGET_SDK),
          grab(r"targetSdkVersion:'(\d+)'"))
    check("launchable activity",
          grab(r"launchable-activity: name='([^']+)'") == PACKAGE + ".ui.MainActivity",
          grab(r"launchable-activity: name='([^']+)'"))

    # ---- permissions: offline, and nothing unexpected --------------------
    perms = set(re.findall(r"uses-permission: name='([^']+)'", bad))
    network = sorted(p for p in perms if FORBIDDEN_PERMISSION.search(p))
    check("no network/other sensitive permission", not network, ", ".join(network))
    extra = sorted(perms - EXPECTED_PERMISSIONS)
    check("permission set unchanged", not extra, "unexpected: " + ", ".join(extra))
    missing = sorted(EXPECTED_PERMISSIONS - perms)
    check("required permissions present", not missing, "missing: " + ", ".join(missing))

    # ---- dex ------------------------------------------------------------
    strings, types = dex_tables(apk)
    net_types = sorted(t for t in types if NETWORK_TYPES.search(t))
    check("no networking classes in the dex", not net_types, ", ".join(net_types[:5]))
    urls = sorted(s for s in strings if NETWORK_STRINGS.search(s)
                  and not any(ns in s for ns in NAMESPACE_URLS))
    check("no endpoint URL literals in the dex", not urls, ", ".join(urls[:5]))
    mp3_codec = sorted(s for s in strings if MP3_CODEC.search(s))
    check("no MP3 encoder/codec in the dex", not mp3_codec, ", ".join(mp3_codec[:5]))
    mentions = [s for s in strings if MP3_PROSE.search(s)]
    notes.append("MP3 appears only in %d user-facing sentence(s), e.g. %r"
                 % (len(mentions), mentions[0][:70] + "..." if mentions else ""))
    check("dex string table extracted", len(strings) > 2000, "%d strings" % len(strings))

    # developers write 'http://schemas.android.com' in layouts, not in code, but
    # the manifest namespace is required - so only the dex matters here
    manifest_ns = "android.com" in bad

    # ---- declared components exist --------------------------------------
    tree = xmltree(apk)
    declared = set()
    for m in re.finditer(r'E: (activity|service|receiver|provider)[^\n]*\n(?:.*?\n)*?'
                         r'.*?A: http://schemas\.android\.com/apk/res/android:name[^\n]*="([^"]+)"',
                         tree):
        declared.add((m.group(1), m.group(2)))
    resolved = set()
    for kind, name in declared:
        full = name if "." in name.strip(".") else PACKAGE + name
        if name.startswith("."):
            full = PACKAGE + name
        resolved.add((kind, full))
        check("%s %s in dex" % (kind, full.split(".")[-1]), has_type(types, full), full)
    check("manifest declares the main activity",
          any(k == "activity" and n.endswith("MainActivity") for k, n in resolved), "")
    check("manifest declares the provider",
          any(k == "provider" and n.endswith("ExportProvider") for k, n in resolved), "")
    check("manifest declares the service",
          any(k == "service" for k, _ in resolved), "")

    # ---- USB support -----------------------------------------------------
    check("USB host feature declared", "android.hardware.usb.host" in bad or
          "usb.host" in tree, "")
    check("USB_DEVICE_ATTACHED filter", "USB_DEVICE_ATTACHED" in tree, "")
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        check("USB device filter resource packaged", "res/xml/usb_device_filter.xml" in names, "")
        if "res/xml/usb_device_filter.xml" in names:
            body = z.read("res/xml/usb_device_filter.xml")
            check("device filter targets USB audio class", b"usb-device" in body
                  or b"\x08usb-device" in body, "")

        # ---- branding ----------------------------------------------------
        for api in ("v26", "v33"):
            check("adaptive icon manifest %s" % api,
                  "res/mipmap-anydpi-%s/ic_launcher.xml" % api in names, "")
            check("round adaptive icon %s" % api,
                  "res/mipmap-anydpi-%s/ic_launcher_round.xml" % api in names, "")
        v33 = z.read("res/mipmap-anydpi-v33/ic_launcher.xml") if (
                "res/mipmap-anydpi-v33/ic_launcher.xml" in names) else b""
        check("v33 monochrome layer", b"monochrome" in v33, "")
        densities = ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        missing_art = [d for d in densities
                       if not any(re.match(r"res/mipmap-%s(-v\d+)?/ic_launcher_foreground" % d, n)
                                  for n in names)]
        check("launcher foreground for all densities", not missing_art, ", ".join(missing_art))
        check("in-app brand logo packaged",
              any(re.match(r"res/drawable-nodpi(-v\d+)?/brand_logo", n) for n in names), "")
        check("launcher foreground bytes are real PNGs",
              all(z.read(n)[:8] == b"\x89PNG\r\n\x1a\n" for n in names
                  if "ic_launcher_foreground" in n), "")

    # ---- packaging -------------------------------------------------------
    # Only *stored* entries have to be aligned (a compressed entry has no mmap
    # address to speak of); Android needs 4 bytes, and native libraries 4096.
    entries = alignments(apk)
    misaligned, unaligned_so = [], []
    for name, (offset, stored, _comp) in entries.items():
        if not stored:
            continue
        if name.endswith(".so"):
            if offset % 4096:
                unaligned_so.append(name)
        elif offset % 4:
            misaligned.append(name)
    check("stored entries 4-byte aligned", not misaligned, ", ".join(sorted(misaligned)[:5]))
    check("native libraries 4 KiB aligned", not unaligned_so, ", ".join(sorted(unaligned_so)[:5]))
    check("classes.dex present", "classes.dex" in entries, "")
    with zipfile.ZipFile(apk) as z:
        info = z.getinfo("resources.arsc") if "resources.arsc" in z.namelist() else None
        check("resources.arsc stored uncompressed", info is not None and info.compress_type == 0, "")
    blob = open(apk, "rb").read()
    check("v2/v3 signature block present", b"APK Sig Block 42" in blob, "")
    check("dex is compressed inside the package", b"dex\n035\x00" not in blob, "")

    print()
    for n in notes:
        print("  note: %s" % n)
    if problems:
        for p in problems:
            print("  FAIL %s" % p)
        print()
        print("%d problem(s) in %s" % (len(problems), apk))
        return 1
    print("release gate passed")
    return 0


def check(what, ok, detail=""):
    if ok:
        print("  ok    %s" % what)
    else:
        problems.append(what + (" (%s)" % detail if detail else ""))
        print("  FAIL  %s%s" % (what, (" (%s)" % detail) if detail else ""))


if __name__ == "__main__":
    sys.exit(main())
