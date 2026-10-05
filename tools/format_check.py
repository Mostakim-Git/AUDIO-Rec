#!/usr/bin/env python3
"""
AUDIO-rec :: independent container verifier.

Companion to tools/test/FormatSelfTest.java.  The harness writes every container
the app can record plus the reference payload it wanted to produce; this script
re-decodes the containers from the *specification* side - not from the Java that
wrote them - and fails loudly on any difference.  That makes it a referee for:

  * RIFF / RF64 / WAVE_FORMAT_EXTENSIBLE headers, chunk sizes, channel masks
  * AIFF FORM/COMM/SSND, the 80-bit sample rate and big-endian sample packing
  * Ogg page framing, lacing values, page CRCs, sequence numbers, granules
  * FLAC (delegated to tools/flac_check.py, which has its own decoder)

    python3 tools/format_check.py <dir-with-self-test-output>

Exits non-zero if anything mismatches.
"""
import math
import os
import struct
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

checks = 0
failures = []


def check(ok, what):
    global checks
    checks += 1
    if not ok:
        failures.append(what)
        print("  FAIL  %s" % what)
    return ok


# --------------------------------------------------------------------- helpers
def parse_name(path):
    """wav-48000-2-16.bin -> (48000, 2, 16)"""
    stem = os.path.basename(path).split(".")[0]
    _, rate, ch, depth = stem.split("-")
    return int(rate), int(ch), int(depth)


def read_ref(path):
    return open(path, "rb").read()


def samples_to_bytes(samples, depth, big_endian=False):
    out = bytearray()
    for v in samples:
        if depth == 8:
            out.append(v & 0xFF)
        else:
            out += int(v).to_bytes(depth // 8, "big" if big_endian else "little", signed=True)
    return bytes(out)


def bytes_to_samples(data, depth, big_endian=False):
    n = len(data) // (depth // 8)
    out = []
    for i in range(n):
        chunk = data[i * (depth // 8):(i + 1) * (depth // 8)]
        out.append(int.from_bytes(chunk, "big" if big_endian else "little", signed=True))
    return out


def le_float32(data):
    n = len(data) // 4
    return [struct.unpack_from("<f", data, i * 4)[0] for i in range(n)]


def i80(b):
    """AIFF extended-precision 80-bit float"""
    expon = struct.unpack(">H", b[:2])[0]
    mant = int.from_bytes(b[2:], "big")
    if expon == 0 and mant == 0:
        return 0.0
    expon &= 0x7FFF
    return math.ldexp(mant, expon - 16383 - 63)


# ------------------------------------------------------------------- WAV/AIFF
def parse_wav(path):
    data = open(path, "rb").read()
    rf64 = data[:4] == b"RF64"
    check(data[:4] == b"RIFF" or rf64, "%s: no RIFF/RF64 magic" % path)
    riff_size = struct.unpack_from("<I", data, 4)[0]
    check(rf64 or riff_size == len(data) - 8,
          "%s: RIFF size %d != file %d" % (path, riff_size, len(data) - 8))
    check(data[8:12] == b"WAVE", "%s: no WAVE tag" % path)

    off, fmt, payload, ds64 = 12, None, None, None
    while off + 8 <= len(data):
        cid = data[off:off + 4]
        size = struct.unpack_from("<I", data, off + 4)[0]
        body = data[off + 8:off + 8 + size]
        if cid == b"ds64":
            ds64 = struct.unpack_from("<QQQ", body, 0)
        elif cid == b"fmt ":
            fmt = body
        elif cid == b"data":
            payload = data[off + 8: off + 8 + (size if size != 0xFFFFFFFF else len(data))]
        off += 8 + size + (size & 1)
    check(fmt is not None and payload is not None, "%s: missing fmt/data" % path)

    tag, channels, rate, byte_rate, align, bits = struct.unpack_from("<HHIIHH", fmt, 0)
    mask = None
    if tag == 0xFFFE:
        valid, mask = struct.unpack_from("<HI", fmt, 18)
        check(valid == bits, "%s: valid bits %d != container bits %d" % (path, valid, bits))
        subtype = fmt[24:26]
        tag = struct.unpack_from("<H", subtype, 0)[0]
    is_float = tag == 3
    check(tag in (1, 3), "%s: unsupported format tag %d" % (path, tag))
    check(align == channels * bits // 8,
          "%s: block align %d != %d" % (path, align, channels * bits // 8))
    check(byte_rate == rate * align, "%s: byte rate %d" % (path, byte_rate))
    if rf64:
        check(ds64 is not None, "%s: RF64 without ds64" % path)
        if ds64:
            check(ds64[1] == len(payload), "%s: ds64 dataSize %d != %d" % (path, ds64[1], len(payload)))
    return dict(rate=rate, channels=channels, bits=bits, float=is_float,
                payload=payload, mask=mask)


def check_wav(path):
    rate, channels, depth = parse_name(path)
    w = parse_wav(path)
    check(w["rate"] == rate, "%s: rate %d" % (path, w["rate"]))
    check(w["channels"] == channels, "%s: channels %d" % (path, w["channels"]))
    check(w["bits"] == depth, "%s: bits %d" % (path, w["bits"]))
    if channels > 2:
        expected = (1 if channels == 1 else 3 if channels == 2 else
                    (0x33 if channels == 4 else 0x63F if channels == 8 else None))
        check(w["mask"] is not None, "%s: %d channels but no EXTENSIBLE header" % (path, channels))
        if w["mask"] is not None and expected:
            check(w["mask"] == expected, "%s: channel mask 0x%X != 0x%X" % (path, w["mask"], expected))
    ref = read_ref(path.replace(".bin", ".pcm"))
    if depth == 32:
        check(w["float"], "%s: 32-bit WAV should be IEEE float" % path)
        got, want = le_float32(w["payload"]), le_float32(ref)
        same = len(got) == len(want) and all(a == b for a, b in zip(got, want))
    else:
        got, want = bytes_to_samples(w["payload"], depth), bytes_to_samples(ref, depth)
        same = got == want
    check(same, "%s: decoded PCM differs from reference (%d vs %d samples)"
          % (path, len(got), len(want)))
    check(len(w["payload"]) == len(ref), "%s: payload %d bytes, reference %d"
          % (path, len(w["payload"]), len(ref)))
    return w


def parse_aiff(path):
    data = open(path, "rb").read()
    check(data[:4] == b"FORM", "%s: no FORM magic" % path)
    check(struct.unpack_from(">I", data, 4)[0] == len(data) - 8, "%s: FORM size" % path)
    check(data[8:12] == b"AIFF", "%s: not AIFF" % path)
    off, comm, ssnd = 12, None, None
    while off + 8 <= len(data):
        cid = data[off:off + 4]
        size = struct.unpack_from(">I", data, off + 4)[0]
        body = data[off + 8:off + 8 + size]
        if cid == b"COMM":
            comm = body
        elif cid == b"SSND":
            ssnd = body
        off += 8 + size + (size & 1)
    check(comm is not None and ssnd is not None, "%s: missing COMM/SSND" % path)
    check(len(comm) == 18, "%s: COMM size %d (18 = with 80-bit rate)" % (path, len(comm)))
    channels, frames, bits = struct.unpack_from(">HIH", comm, 0)
    rate = i80(comm[8:18])
    offset, block = struct.unpack_from(">II", ssnd, 0)
    payload = ssnd[8 + offset:]
    return dict(rate=rate, channels=channels, frames=frames, bits=bits,
                payload=payload, offset=offset, block=block)


def check_aiff(path):
    rate, channels, depth = parse_name(path)
    a = parse_aiff(path)
    check(abs(a["rate"] - rate) < 1e-6, "%s: 80-bit rate %r != %d" % (path, a["rate"], rate))
    check(a["channels"] == channels, "%s: channels %d" % (path, a["channels"]))
    check(a["bits"] == depth, "%s: bits %d" % (path, a["bits"]))
    check(a["offset"] == 0 and a["block"] == 0, "%s: SSND offset/block not 0" % path)
    ref = read_ref(path.replace(".bin", ".be.pcm"))
    check(len(a["payload"]) == len(ref), "%s: payload %d bytes, reference %d"
          % (path, len(a["payload"]), len(ref)))
    check(bytes_to_samples(a["payload"], depth, True) == bytes_to_samples(ref, depth, True),
          "%s: decoded PCM differs from reference" % path)
    check(a["frames"] * channels * (depth // 8) == len(a["payload"]),
          "%s: COMM frame count %d inconsistent with payload" % (path, a["frames"]))
    return a


# ------------------------------------------------------------------------- OGG
CRC_POLY = 0x04C11DB7
CRC_TABLE = []
for _i in range(256):
    _c = _i << 24
    for _ in range(8):
        _c = ((_c << 1) ^ CRC_POLY) & 0xFFFFFFFF if _c & 0x80000000 else (_c << 1) & 0xFFFFFFFF
    CRC_TABLE.append(_c)


def ogg_crc(page):
    crc = 0
    for b in page:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ CRC_TABLE[((crc >> 24) & 0xFF) ^ b]
    return crc


def check_ogg(path, ref_path):
    data = open(path, "rb").read()
    packets, expect_packets = [], []
    ref = open(ref_path, "rb").read()
    off = 0
    while off + 4 <= len(ref):
        n = struct.unpack_from(">I", ref, off)[0]
        expect_packets.append(ref[off + 4:off + 4 + n])
        off += 4 + n

    pos, seq, cur, partial = 0, None, bytearray(), bytearray()
    pages = 0
    granule_last = None
    while pos < len(data):
        check(data[pos:pos + 4] == b"OggS", "%s: page %d: no capture pattern at %d" % (path, pages, pos))
        version = data[pos + 4]
        check(version == 0, "%s: page %d: version %d" % (path, pages, version))
        header_type = data[pos + 5]
        granule = struct.unpack_from("<q", data, pos + 6)[0]
        serial = struct.unpack_from("<I", data, pos + 14)[0]
        page_seq = struct.unpack_from("<I", data, pos + 18)[0]
        stored_crc = struct.unpack_from("<I", data, pos + 22)[0]
        nseg = data[pos + 26]
        lacing = list(data[pos + 27:pos + 27 + nseg])
        body_len = sum(lacing)
        page_len = 27 + nseg + body_len
        page = bytearray(data[pos:pos + page_len])
        page[22:26] = b"\0\0\0\0"
        check(ogg_crc(bytes(page)) == stored_crc,
              "%s: page %d: CRC %08X != %08X" % (path, pages, ogg_crc(bytes(page)), stored_crc))
        check(serial == 0x5ADEC0DE, "%s: page %d: serial %08X" % (path, pages, serial))
        if seq is None:
            check(header_type & 0x02, "%s: first page is not BOS" % path)
            seq = 0
        else:
            check(page_seq == seq, "%s: page %d: sequence %d" % (path, pages, page_seq))
        seq = page_seq + 1
        body = data[pos + 27 + nseg:pos + page_len]
        bpos = 0
        for i, lace in enumerate(lacing):
            piece = body[bpos:bpos + lace]
            cur += piece
            bpos += lace
            if lace < 255:                      # packet ends here
                packets.append(bytes(cur))
                cur = bytearray()
        check(bpos == body_len, "%s: page %d: lacing %d != body %d" % (path, pages, body_len, bpos))
        if header_type & 0x04:
            granule_last = granule
        pos += page_len
        pages += 1

    check(len(packets) == len(expect_packets),
          "%s: %d packets reassembled, expected %d" % (path, len(packets), len(expect_packets)))
    for i, (a, b) in enumerate(zip(packets, expect_packets)):
        if not check(a == b, "%s: packet %d differs (%d vs %d bytes)" % (path, i, len(a), len(b))):
            break
    check(granule_last == 960 * 13, "%s: EOS granule %s" % (path, granule_last))
    print("  %-26s %-6s %d pages, %d packets, CRCs verified"
          % (os.path.basename(path), "OGG", pages, len(packets)))
    return pages, len(packets)


# ------------------------------------------------------------------------ main
def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    d = sys.argv[1]
    files = sorted(f for f in os.listdir(d) if f.endswith(".bin"))
    print("AUDIO-rec :: container verification in %s" % d)

    print("\n-- WAV --")
    for f in [x for x in files if x.startswith("wav-")]:
        p = os.path.join(d, f)
        w = parse_wav(p)
        check_wav(p)
        print("  %-26s %-6s %5d Hz %dch %2d-bit %s  %d bytes"
              % (f, "WAVE", w["rate"], w["channels"], w["bits"],
                 "float" if w["float"] else "int", len(w["payload"])))

    print("\n-- AIFF --")
    for f in [x for x in files if x.startswith("aiff-")]:
        p = os.path.join(d, f)
        a = parse_aiff(p)
        check_aiff(p)
        print("  %-26s %-6s %5d Hz %dch %2d-bit  %d bytes"
              % (f, "AIFF", round(a["rate"]), a["channels"], a["bits"], len(a["payload"])))

    print("\n-- FLAC (tools/flac_check.py) --")
    for f in [x for x in files if x.startswith("flac-")]:
        p = os.path.join(d, f)
        r = subprocess.run([sys.executable, os.path.join(HERE, "flac_check.py"), p],
                           capture_output=True, text=True)
        ok = r.returncode == 0
        check(ok, "%s: flac_check failed: %s" % (f, (r.stdout + r.stderr).strip()))
        print("  " + (r.stdout.strip().splitlines() or [""])[0])

    print("\n-- OGG --")
    if os.path.exists(os.path.join(d, "opus-shape.ogg")):
        check_ogg(os.path.join(d, "opus-shape.ogg"), os.path.join(d, "opus-shape.oggpkts"))
    else:
        check(False, "opus-shape.ogg missing")

    print()
    if failures:
        print("%d/%d checks FAILED:" % (len(failures), checks))
        for f in failures[:20]:
            print("  - %s" % f)
        return 1
    print("all %d checks passed" % checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
