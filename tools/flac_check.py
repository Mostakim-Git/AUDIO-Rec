#!/usr/bin/env python3
"""
AUDIO-rec :: independent FLAC verifier.

A from-scratch FLAC *decoder* used as a referee for FlacWriter.  It is written
against the format description rather than against the encoder, so it catches
real mistakes: wrong bit packing, bad Rice partition lengths, wrong predictor
signs, broken CRCs, wrong warm-up handling.

    python3 tools/flac_check.py file.flac [expected_md5_hex]

Exits non-zero on any structural error; prints the decoded statistics.
"""
import hashlib
import struct
import sys

CRC8_TABLE = []
CRC16_TABLE = []
for i in range(256):
    c = i
    for _ in range(8):
        c = ((c << 1) ^ 0x07) & 0xFF if c & 0x80 else (c << 1) & 0xFF
    CRC8_TABLE.append(c)
    c = i << 8
    for _ in range(8):
        c = ((c << 1) ^ 0x8005) & 0xFFFF if c & 0x8000 else (c << 1) & 0xFFFF
    CRC16_TABLE.append(c)


class Bits:
    def __init__(self, data, pos=0):
        self.d = data
        self.p = pos * 8
        self.start = pos * 8

    def read(self, n):
        v = 0
        for _ in range(n):
            byte = self.d[self.p >> 3]
            v = (v << 1) | ((byte >> (7 - (self.p & 7))) & 1)
            self.p += 1
        return v

    def signed(self, n):
        v = self.read(n)
        if n and (v >> (n - 1)) & 1:
            v -= (1 << n)
        return v

    def unary(self):
        n = 0
        while self.read(1) == 0:
            n += 1
        return n

    def align(self):
        self.p = (self.p + 7) & ~7

    def byte(self):
        self.align()
        b = self.p >> 3
        self.p += 8
        return b

    def tell(self):
        return self.p >> 3


def crc8(data):
    c = 0
    for b in data:
        c = CRC8_TABLE[c ^ b]
    return c


def crc16(data):
    c = 0
    for b in data:
        c = ((c << 8) & 0xFFFF) ^ CRC16_TABLE[((c >> 8) ^ b) & 0xFF]
    return c


class Stream:
    def __init__(self, buf):
        self.buf = buf
        self.pos = 0
        assert buf[0:4] == b"fLaC", "not a FLAC stream"
        self.pos = 4
        self.streaminfo = None
        self.md5 = None
        self.tags = {}
        self._metadata()

    def _metadata(self):
        while True:
            h = self.buf[self.pos]
            last = h >> 7
            btype = h & 0x7F
            length = int.from_bytes(self.buf[self.pos + 1:self.pos + 4], "big")
            body = self.buf[self.pos + 4:self.pos + 4 + length]
            self.pos += 4 + length
            if btype == 0:
                b = Bits(body)
                self.min_block = b.read(16)
                self.max_block = b.read(16)
                self.min_frame = b.read(24)
                self.max_frame = b.read(24)
                self.sample_rate = b.read(20)
                self.channels = b.read(3) + 1
                self.bits = b.read(5) + 1
                self.total_samples = (b.read(4) << 32) | b.read(32)
                self.md5 = body[18:34]
            elif btype == 4:
                p = 0
                vlen = int.from_bytes(body[p:p + 4], "little")
                p += 4 + vlen
                count = int.from_bytes(body[p:p + 4], "little")
                p += 4
                for _ in range(count):
                    l = int.from_bytes(body[p:p + 4], "little")
                    p += 4
                    kv = body[p:p + l].decode("utf-8", "replace")
                    p += l
                    if "=" in kv:
                        k, v = kv.split("=", 1)
                        self.tags[k] = v
            if last:
                break

    def frames(self):
        """yield (frame_number, [channel sample lists])"""
        while self.pos < len(self.buf):
            n = self._frame()
            if n is None:
                break
            yield n

    def _frame(self):
        start = self.pos
        b = Bits(self.buf, start)
        sync = b.read(14)
        if sync != 0x3FFE:
            raise ValueError("bad frame sync at byte %d: %04x" % (start, sync))
        b.read(1)
        blocking = b.read(1)
        bs_code = b.read(4)
        sr_code = b.read(4)
        ch_code = b.read(4)
        ss_code = b.read(3)
        b.read(1)
        number = 0
        first = b.read(8)
        if first < 0x80:
            number = first
        else:
            n = 0
            mask = 0x80
            while first & mask:
                n += 1
                mask >>= 1
            value = first & (mask - 1)
            for _ in range(n - 1):
                value = (value << 6) | (b.read(8) & 0x3F)
            number = value
        blocksize = {1: 192, 2: 576, 3: 1152, 4: 2304, 5: 4608, 8: 256, 9: 512,
                     10: 1024, 11: 2048, 12: 4096, 13: 8192, 14: 16384, 15: 32768}.get(bs_code)
        if bs_code == 6:
            blocksize = b.read(8) + 1
        elif bs_code == 7:
            blocksize = b.read(16) + 1
        if blocksize is None:
            blocksize = self.max_block
        sr = {1: 88200, 2: 176400, 3: 192000, 4: 8000, 5: 16000, 6: 22050, 7: 24000,
              8: 32000, 9: 44100, 10: 48000, 11: 96000}.get(sr_code, self.sample_rate)
        if sr_code == 12:
            sr = b.read(8) * 1000
        elif sr_code == 13:
            sr = b.read(16)
        ss = {1: 8, 2: 12, 4: 16, 5: 20, 6: 24, 7: 32}.get(ss_code, self.bits)
        b.align()
        hdr_end = b.tell()
        crc = self.buf[hdr_end]
        if crc8(self.buf[start:hdr_end]) != crc:
            raise ValueError("frame header CRC8 mismatch at %d" % start)
        b.p = (hdr_end + 1) * 8        # step over the header CRC byte

        channels = (ch_code + 1) if ch_code < 8 else 2
        out = []
        for c in range(channels):
            extra_bps = 0
            if ch_code == 8 and c == 1:      # left/side: side has +1 bit
                extra_bps = 1
            elif ch_code == 9 and c == 0:    # right/side
                extra_bps = 1
            elif ch_code == 10 and c == 1:   # mid/side
                extra_bps = 1
            out.append(self._subframe(b, blocksize, ss + extra_bps))
        b.align()
        frame_end = b.tell()
        stored = struct.unpack(">H", self.buf[frame_end:frame_end + 2])[0]
        if crc16(self.buf[start:frame_end]) != stored:
            raise ValueError("frame CRC16 mismatch at %d" % start)

        # channel decorrelation
        if ch_code == 8:
            l, side = out[0], out[1]
            out = [l, [l[i] - side[i] for i in range(len(l))]]
        elif ch_code == 9:
            side, r = out[0], out[1]
            out = [[side[i] + r[i] for i in range(len(r))], r]
        elif ch_code == 10:
            mid, side = out[0], out[1]
            m = [(mid[i] << 1) | (side[i] & 1) for i in range(len(mid))]
            out = [[(m[i] + side[i]) >> 1 for i in range(len(m))],
                   [(m[i] - side[i]) >> 1 for i in range(len(m))]]

        self.pos = frame_end + 2
        return number, blocksize, out

    def _subframe(self, b, blocksize, bps):
        b.read(1)                                   # zero padding
        stype = b.read(6)
        wasted = 0
        if b.read(1):
            wasted = b.unary() + 1
        eff = bps - wasted
        if stype == 0:
            v = b.signed(eff)
            s = [v] * blocksize
        elif stype == 1:
            s = [b.signed(eff) for _ in range(blocksize)]
        elif 8 <= stype <= 12:
            order = stype - 8
            s = [b.signed(eff) for _ in range(order)]
            res = self._residual(b, blocksize, order)
            for i in range(order, blocksize):
                if order == 0:
                    pred = 0
                elif order == 1:
                    pred = s[i - 1]
                elif order == 2:
                    pred = 2 * s[i - 1] - s[i - 2]
                elif order == 3:
                    pred = 3 * s[i - 1] - 3 * s[i - 2] + s[i - 3]
                else:
                    pred = 4 * s[i - 1] - 6 * s[i - 2] + 4 * s[i - 3] - s[i - 4]
                s.append(pred + res[i - order])
        elif stype >= 32:
            order = stype - 31
            s = [b.signed(eff) for _ in range(order)]
            prec = b.read(4) + 1
            shift = b.signed(5)
            qlp = [b.signed(prec) for _ in range(order)]
            res = self._residual(b, blocksize, order)
            for i in range(order, blocksize):
                pred = sum(qlp[j] * s[i - 1 - j] for j in range(order))
                s.append((pred >> shift) + res[i - order])
        else:
            raise ValueError("reserved subframe type %d" % stype)
        if wasted:
            s = [v << wasted for v in s]
        return s

    def _residual(self, b, blocksize, order):
        method = b.read(2)
        p = b.read(4)
        parts = 1 << p
        part_len = blocksize >> p
        if part_len << p != blocksize:
            raise ValueError("partition order %d does not divide blocksize %d" % (p, blocksize))
        out = []
        for i in range(parts):
            count = part_len - order if i == 0 else part_len
            if count < 0:
                raise ValueError("negative partition length (order %d, part_len %d)" % (order, part_len))
            param_bits = 4 if method == 0 else 5
            k = b.read(param_bits)
            escape = (k == (1 << param_bits) - 1)
            if escape:
                raw = b.read(5)
                for _ in range(count):
                    out.append(b.signed(raw) if raw else 0)
            else:
                for _ in range(count):
                    q = b.unary()
                    r = b.read(k) if k else 0
                    v = (q << k) | r
                    out.append((v >> 1) ^ -(v & 1))
        return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    data = open(sys.argv[1], "rb").read()
    st = Stream(data)
    print("FLAC  %d Hz  %d ch  %d-bit  %d samples (%.2f s)  MD5 %s"
          % (st.sample_rate, st.channels, st.bits, st.total_samples,
             st.total_samples / max(1, st.sample_rate), st.md5.hex()))
    print("      tags: %s" % st.tags)
    md5 = hashlib.md5()
    total = 0
    nframes = 0
    first_frame_bytes = None
    bps = st.bits // 8
    for number, blocksize, channels in st.frames():
        nframes += 1
        frames = len(channels[0])
        for i in range(frames):
            for c in range(len(channels)):
                v = channels[c][i]
                md5.update(v.to_bytes(bps, "little", signed=True))
        total += frames
    print("      decoded %d frames, %d samples" % (nframes, total))
    if st.total_samples and total != st.total_samples:
        print("!! sample count mismatch: STREAMINFO says %d, decoded %d"
              % (st.total_samples, total))
        sys.exit(1)
    if md5.digest() != st.md5:
        print("!! MD5 mismatch: decoded %s vs STREAMINFO %s"
              % (md5.hexdigest(), st.md5.hex()))
        sys.exit(1)
    print("      MD5 verified against decoded audio \u2713")
    if len(sys.argv) > 2:
        if st.md5.hex() != sys.argv[2]:
            print("!! expected MD5 %s" % sys.argv[2])
            sys.exit(1)
        print("      matches the expected source MD5 \u2713")


if __name__ == "__main__":
    main()
