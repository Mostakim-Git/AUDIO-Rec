#!/usr/bin/env python3
"""
AUDIO-rec :: deterministic, zipalign-aware APK packer.

`zipfile` cannot guarantee the exact byte offsets that Android requires
(resources.arsc uncompressed + 4-byte aligned, native libs page aligned), so we
write the archive ourselves.  ~90 lines, no dependencies.

usage: ziptool.py <out.apk> <alignment> <entry-spec>...

  entry-spec := PATH[::SRC]            -> store a file from disk
                PATH::SRC:comppolicy   -> 'store' | 'deflate' | 'auto'
"""

import os
import struct
import sys
import zlib

STORE, DEFLATE = 0, 8
DOS_TIME = (0 << 11) | (0 << 5) | (0 >> 1)   # 1980-01-01, like aapt2
DOS_DATE = ((1980 - 1980) << 9) | (1 << 5) | 1

# entries that must stay uncompressed for the platform loader / mmap
FORCE_STORE = ("resources.arsc",)
FORCE_STORE_SUFFIX = (".so", ".dex")


def _ext_attr(name):
    return (0o100644 << 16) | 0x20 if not name.endswith("/") else (0o040755 << 16) | 0x10


def _align_to(pad_from, alignment):
    rem = pad_from % alignment
    return 0 if rem == 0 else alignment - rem


class Writer:
    def __init__(self, path, alignment):
        self.path = path
        self.alignment = alignment
        self.fh = open(path, "wb")
        self.offset = 0
        self.entries = []

    def _w(self, data):
        self.fh.write(data)
        self.offset += len(data)

    def add(self, name, src, policy="auto"):
        with open(src, "rb") as f:
            raw = f.read()
        name_b = name.encode("utf-8")
        if policy == "store" or name in FORCE_STORE or name.endswith(FORCE_STORE_SUFFIX):
            method, payload = STORE, raw
        else:
            method, payload = DEFLATE, b""
        if policy != "store" and method == DEFLATE:
            co = zlib.compressobj(9, zlib.DEFLATED, -15)
            body = co.compress(raw) + co.flush()
            # only keep deflate when it actually pays off
            if len(body) >= len(raw):
                method, payload = STORE, raw
            else:
                payload = body
        elif policy == "auto":
            payload = raw

        crc = zlib.crc32(raw) & 0xFFFFFFFF
        # pad so that the payload of *stored* entries lands on an alignment
        # boundary; padding is carried in the local+central extra field.
        extra = b""
        if method == STORE:
            head = self.offset + 30 + len(name_b) + len(extra)
            pad = _align_to(head, self.alignment)
            if pad:
                extra = struct.pack("<HH", 0xD935, pad - 4) + b"\0" * (pad - 4)
        head_off = self.offset
        self._w(struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0, method, DOS_TIME, DOS_DATE,
                            crc, len(payload), len(raw), len(name_b), len(extra)))
        self._w(name_b)
        self._w(extra)
        self._w(payload)
        self.entries.append((name_b, method, crc, len(payload), len(raw), head_off, extra))

    def close(self):
        cd_off = self.offset
        for name_b, method, crc, csize, usize, head_off, extra in self.entries:
            self._w(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0, method,
                                DOS_TIME, DOS_DATE, crc, csize, usize, len(name_b),
                                len(extra), 0, 0, 0, _ext_attr(name_b.decode()), head_off))
            self._w(name_b)
            self._w(extra)
        cd_size = self.offset - cd_off
        n = len(self.entries)
        self._w(struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, n, n, cd_size, cd_off, 0))
        self.fh.close()


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(2)
    out, alignment = sys.argv[1], int(sys.argv[2])
    w = Writer(out, alignment)
    for spec in sys.argv[3:]:
        parts = spec.split("::")
        name = parts[0]
        src = parts[1] if len(parts) > 1 and parts[1] else name
        policy = parts[2] if len(parts) > 2 else "auto"
        w.add(name, src, policy)
    w.close()
    print("packed %s (%d entries, %d bytes)" % (out, len(w.entries), w.offset))


if __name__ == "__main__":
    main()
