#!/usr/bin/env python3
"""Build the "foreign file" corpus for tools/test/ReaderCheck.java.

Every file here is assembled byte by byte with struct, deliberately NOT with any
AUDIO-rec writer: the point is to prove RawPcmReader against files that came out
of somebody else's application - extra chunks, odd sizes, pad bytes, 8-bit
unsigned, plain 32-bit int, IEEE float, 20-in-24 bits, RF64 with ds64, AIFF-C
codecs, unsupported codecs, truncated files and plain garbage.

Sample pattern, shared with ReaderCheck.java (every value divides exactly, so
the two implementations agree bit for bit):

    code(frame, ch) = (frame * 7 + ch * 13) % 100
    float           = (code - 50) * scale            (see per-kind scale)

Usage:  python3 tools/test/gen_foreign.py <outdir>
"""
import os
import struct
import sys

OUT = None
CASES = []          # (name, rate, channels, depth, frames, kind, expectNull, partial)


def code(frame, ch):
    return (frame * 7 + ch * 13) % 100


def payload(channels, frames, kind):
    out = bytearray()
    for f in range(frames):
        for ch in range(channels):
            c = code(f, ch)
            if kind == 'u8':
                out.append(128 + (c - 50) * 2)
            elif kind == 's16':
                out += struct.pack('<h', (c - 50) * 320)
            elif kind == 's16be':
                out += struct.pack('>h', (c - 50) * 320)
            elif kind == 's24':
                out += struct.pack('<i', (c - 50) * 81920)[:3]
            elif kind == 's24be':
                out += struct.pack('>i', (c - 50) * 81920)[1:]
            elif kind == 'f32':
                out += struct.pack('<f', (c - 50) * 0.02)
            elif kind == 'f32be':
                out += struct.pack('>f', (c - 50) * 0.02)
            else:
                raise ValueError(kind)
    return bytes(out)


def chunk(cid, body):
    out = cid + struct.pack('<I', len(body)) + body
    return out + b'\x00' if len(body) & 1 else out


def be_chunk(cid, body):
    out = cid + struct.pack('>I', len(body)) + body
    return out + b'\x00' if len(body) & 1 else out


def fmt_pcm(channels, rate, bits, tag=1, mask=None, container_bits=None):
    container_bits = container_bits or bits
    block = max(1, channels * container_bits // 8)
    body = struct.pack('<HHIIHH', tag, channels, rate, rate * block, block, container_bits)
    if tag == 0xFFFE:
        body += struct.pack('<HHI', 22, bits, mask or 0) + pcm_guid(1)
    return body


def pcm_guid(first):
    return bytes([first, 0, 0, 0, 0, 0, 0x10, 0, 0x80, 0, 0, 0xAA, 0, 0x38, 0x9B, 0x71])


def write(name, blob, case=None):
    with open(os.path.join(OUT, name), 'wb') as fh:
        fh.write(blob)
    if case:
        CASES.append((name,) + case)


def wav(name, chunks, magic=b'RIFF', tail=b'', case=None):
    body = b'WAVE' + b''.join(chunks) + tail
    write(name, magic + struct.pack('<I', len(body)) + body, case)


def aiff(name, chunks, form=b'AIFF', case=None):
    body = form + b''.join(chunks)
    write(name, b'FORM' + struct.pack('>I', len(body)) + body, case)


def ext80(rate):
    """80-bit IEEE extended, as an AIFF COMM sample rate"""
    exp, mant = 16383 + 31, rate << 32
    while mant >= (1 << 64):
        mant >>= 1
        exp += 1
    while mant < (1 << 63):
        mant <<= 1
        exp -= 1
    return struct.pack('>HQ', exp, mant)


def comm(channels, frames, bits, rate, codec=None):
    body = struct.pack('>hIh', channels, frames, bits) + ext80(rate)
    return body + codec if codec else body


def ssnd(data, offset=0):
    body = struct.pack('>II', offset, 0) + b'\x00' * offset + data
    return body


def main(out):
    global OUT
    OUT = out
    os.makedirs(OUT, exist_ok=True)

    # ---------------------------------------------------- plain baselines --
    rate, frames, ch = 44100, 100, 2
    wav('plain16.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 16)),
                        chunk(b'data', payload(ch, frames, 's16'))],
        case=(rate, ch, 16, frames, 's16', 0, 0))

    # real-world chunk soup: odd JUNK, LIST, fact before and after the data
    rate, frames, ch = 48000, 250, 2
    wav('chunked24.wav', [chunk(b'JUNK', b'\x01\x02\x03'),
                          chunk(b'LIST', b'INFOISFT' + struct.pack('<I', 6) + b'python'),
                          chunk(b'fact', struct.pack('<I', frames)),
                          chunk(b'fmt ', fmt_pcm(ch, rate, 24)),
                          chunk(b'data', payload(ch, frames, 's24')),
                          chunk(b'LIST', b'INFOICMT' + struct.pack('<I', 4) + b'note')],
        tail=b'\x00' * 3, case=(rate, ch, 24, frames, 's24', 0, 0))

    # odd data chunk: 3 bytes of mono 8-bit needs a pad byte in the stream
    rate, frames, ch = 8000, 3, 1
    wav('odd8.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 8)),
                     chunk(b'data', payload(ch, frames, 'u8'))],
        case=(rate, ch, 8, frames, 'u8', 0, 0))

    # every possible byte value, to pin the unsigned 8-bit mapping
    write('ramp8.wav',
          b'RIFF' + struct.pack('<I', 4 + 8 + 16 + 8 + 256) + b'WAVE'
          + chunk(b'fmt ', fmt_pcm(1, 11025, 8)) + chunk(b'data', bytes(range(256))),
          case=(11025, 1, 8, 256, 'raw_u8', 0, 0))

    # 32-bit int extremes: does the sign come through?
    rate, frames, ch = 96000, 8, 1
    data = struct.pack('<i', -2147483648) + struct.pack('<i', 2147483647) + b'\x00' * 24
    wav('int32extreme.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 32)), chunk(b'data', data)],
        case=(rate, ch, 32, frames, 'raw_i32', 0, 0))

    # IEEE float, both as tag 3 and inside an EXTENSIBLE wrapper
    rate, frames, ch = 48000, 80, 2
    wav('float32.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 32, tag=3)),
                        chunk(b'data', payload(ch, frames, 'f32'))],
        case=(rate, ch, 32, frames, 'f32', 0, 0))
    wav('extfloat.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 32, tag=0xFFFE, mask=0x3)
                               .replace(pcm_guid(1), pcm_guid(3))),
                         chunk(b'data', payload(ch, frames, 'f32'))],
        case=(rate, ch, 32, frames, 'f32', 0, 0))

    # EXTENSIBLE 24-bit PCM, 4 channels (FL|FR|BL|BR), and 20 valid bits in a
    # 24-bit container (left justified, so it must read as 24-bit)
    rate, frames, ch = 48000, 40, 4
    wav('ext4ch24.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 24, tag=0xFFFE, mask=0x33)),
                         chunk(b'data', payload(ch, frames, 's24'))],
        case=(rate, ch, 24, frames, 's24', 0, 0))
    rate, frames, ch = 44100, 40, 1
    wav('ext20in24.wav', [chunk(b'fmt ', fmt_pcm(ch, rate, 20, tag=0xFFFE, mask=0x4,
                                                 container_bits=24)),
                          chunk(b'data', payload(ch, frames, 's24'))],
        case=(rate, ch, 24, frames, 's24', 0, 0))

    # RF64: data size stays -1, ds64 carries the truth, and a junk chunk
    # follows the audio. Guessing "file length - dataOffset" reads the tail.
    rate, frames, ch = 192000, 500, 2
    data = payload(ch, frames, 's24')
    ds64 = b'ds64' + struct.pack('<I', 28) + struct.pack('<QQQI', 0, len(data), frames, 0)
    wav('rf64.wav', [ds64, chunk(b'fmt ', fmt_pcm(ch, rate, 24)),
                     b'data' + struct.pack('<I', 0xFFFFFFFF) + data,
                     chunk(b'junk', b'\xAA' * 512)],
        magic=b'RF64', case=(rate, ch, 24, frames, 's24', 0, 0))

    # ------------------------------------- codecs we must leave alone ------
    wav('mulaw.wav', [chunk(b'fmt ', fmt_pcm(1, 8000, 8, tag=7)),
                      chunk(b'data', b'\x7F' * 64)],
        case=(0, 0, 0, 0, None, 1, 0))
    wav('adpcm.wav', [chunk(b'fmt ', fmt_pcm(1, 8000, 4, tag=2)),
                      chunk(b'data', b'\x00' * 64)],
        case=(0, 0, 0, 0, None, 1, 0))
    wav('float64.wav', [chunk(b'fmt ', fmt_pcm(1, 48000, 64, tag=3)),
                        chunk(b'data', b'\x00' * 64)],
        case=(0, 0, 0, 0, None, 1, 0))
    wav('extmsadpcm.wav', [chunk(b'fmt ', fmt_pcm(2, 48000, 16, tag=0xFFFE, mask=0x3)
                                 .replace(pcm_guid(1), pcm_guid(2))),
                           chunk(b'data', b'\x00' * 64)],
        case=(0, 0, 0, 0, None, 1, 0))

    # ------------------------------------------------------------- AIFF ---
    rate, frames, ch = 44100, 120, 2
    aiff('plain16.aif', [be_chunk(b'COMM', comm(ch, frames, 16, rate)),
                         be_chunk(b'ANNO', b'odd!'),
                         be_chunk(b'SSND', ssnd(payload(ch, frames, 's16be')))],
         case=(rate, ch, 16, frames, 's16be', 0, 0))

    # nonzero SSND offset field: 4 bytes of alignment must be skipped
    rate, frames, ch = 96000, 90, 2
    aiff('offset24.aif', [be_chunk(b'COMM', comm(ch, frames, 24, rate)),
                          be_chunk(b'SSND', ssnd(payload(ch, frames, 's24be'), offset=4))],
         case=(rate, ch, 24, frames, 's24be', 0, 0))

    # COMM frame count of 0: derive the length from the SSND payload
    rate, frames, ch = 22050, 33, 1
    aiff('zeroframes.aif', [be_chunk(b'COMM', comm(ch, 0, 16, rate)),
                            be_chunk(b'SSND', ssnd(payload(ch, frames, 's16be')))],
         case=(rate, ch, 16, frames, 's16be', 0, 0))

    # AIFF-C: little-endian PCM and big-endian float
    rate, frames, ch = 48000, 70, 2
    aiff('sowt.aifc', [be_chunk(b'COMM', comm(ch, frames, 16, rate, codec=b'sowt')),
                       be_chunk(b'SSND', ssnd(payload(ch, frames, 's16')))],
         form=b'AIFC', case=(rate, ch, 16, frames, 's16', 0, 0))
    aiff('fl32.aifc', [be_chunk(b'COMM', comm(ch, frames, 32, rate, codec=b'fl32')),
                       be_chunk(b'SSND', ssnd(payload(ch, frames, 'f32be')))],
         form=b'AIFC', case=(rate, ch, 32, frames, 'f32', 0, 0))

    for codec in (b'ima4', b'ulaw', b'alaw', b'GSM '):
        name = 'skip_%s.aifc' % codec.strip().decode().lower()
        aiff(name, [be_chunk(b'COMM', comm(1, 64, 16, 22050, codec=codec)),
                    be_chunk(b'SSND', ssnd(b'\x00' * 64))],
             form=b'AIFC', case=(0, 0, 0, 0, None, 1, 0))

    # ------------------------------------- rejects, abuse and truncation --
    write('garbage.bin', bytes(range(256)) * 4, case=(0, 0, 0, 0, None, 1, 0))
    write('empty.wav', b'', case=(0, 0, 0, 0, None, 1, 0))
    write('riffonly.wav', b'RIFF' + struct.pack('<I', 4) + b'WAVE',
          case=(0, 0, 0, 0, None, 1, 0))
    write('form_ilbm.iff', b'FORM' + struct.pack('>I', 12) + b'ILBM' + b'BMHD' + b'\x00' * 4,
          case=(0, 0, 0, 0, None, 1, 0))
    write('garbage.rf64', b'RF64' + struct.pack('<I', 0xFFFFFFFF) + bytes(range(64)),
          case=(0, 0, 0, 0, None, 1, 0))

    # header promises 1000 bytes of audio, only 20 are there: the read loop
    # must stop (EOF or -1) instead of inventing samples or spinning
    real = payload(1, 10, 's16')
    blob = (b'RIFF' + struct.pack('<I', 4 + 8 + 16 + 8 + 20) + b'WAVE'
            + chunk(b'fmt ', fmt_pcm(1, 48000, 16))
            + b'data' + struct.pack('<I', 1000) + real)
    write('truncated.wav', blob, case=(48000, 1, 16, 500, 's16', 0, 1))

    with open(os.path.join(OUT, 'expected.txt'), 'w') as fh:
        fh.write('# generated by tools/test/gen_foreign.py; read by ReaderCheck.java\n')
        fh.write('# name rate channels depth frames kind|null expectNull partial\n')
        for name, r, c, d, fr, k, null, part in CASES:
            fh.write('%-18s %7d %2d %3d %6d %-8s %d %d\n'
                     % (name, r, c, d, fr, k or '-', null, part))
    print('%d foreign files written to %s (index: expected.txt)' % (len(CASES), OUT))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'foreign')
