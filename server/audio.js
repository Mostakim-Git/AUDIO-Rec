import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const Flac = require('libflacjs')();
const { Encoder } = require('libflacjs/lib/encoder');
const { exportFlacData } = require('libflacjs/lib/utils');
const flacReady = Flac.isReady() ? Promise.resolve() : new Promise(resolve => Flac.on('ready', resolve));

/** Encode a PCM WAV buffer as a native FLAC stream. FLAC supports 16- and 24-bit PCM. */
export async function encodeFlac(wav, targetDepth) {
  await flacReady;
  const channels = wav.readUInt16LE(22);
  const rate = wav.readUInt32LE(24);
  const sourceBits = wav.readUInt16LE(34);
  const bits = Number(targetDepth) || sourceBits;
  const marker = wav.indexOf(Buffer.from('data'));
  const start = marker + 8;
  const count = Math.floor((wav.length - start) / (channels * (sourceBits / 8)));

  if (![16, 24, 32].includes(sourceBits) || ![16, 24].includes(bits) || !channels || marker < 0 || !count) {
    throw new Error('FLAC supports 16- or 24-bit PCM. Keep 32-bit sources as WAV or choose 24-bit FLAC.');
  }

  const pcm = new Int32Array(count * channels);
  const step = sourceBits / 8;
  for (let i = 0; i < pcm.length; i++) {
    const pos = start + i * step;
    let sample;
    if (sourceBits === 16) sample = wav.readInt16LE(pos);
    else if (sourceBits === 24) {
      const value = wav[pos] | (wav[pos + 1] << 8) | (wav[pos + 2] << 16);
      sample = (value & 0x800000) ? value | 0xff000000 : value;
    } else sample = wav.readInt32LE(pos);

    const shift = sourceBits - bits;
    pcm[i] = shift > 0 ? Math.round(sample / (2 ** shift)) : shift < 0 ? sample * (2 ** -shift) : sample;
  }

  const encoder = new Encoder(Flac, {
    sampleRate: rate,
    channels,
    bitsPerSample: bits,
    compression: 5,
    totalSamples: count,
    verify: false
  });
  try {
    if (!encoder.initialized) throw new Error(`FLAC does not support ${rate.toLocaleString()} Hz at ${bits} bits on this encoder.`);
    for (let i = 0; i < pcm.length; i += 65536) {
      if (!encoder.encode(pcm.subarray(i, Math.min(i + 65536, pcm.length)))) throw new Error('FLAC encoding failed.');
    }
    if (!encoder.encode()) throw new Error('FLAC finalization failed.');
    return Buffer.from(await exportFlacData(encoder.rawData, encoder.metadata, false));
  } finally {
    encoder.destroy();
  }
}

export function demoWav(sampleRate = 48000, bits = 16) {
  bits = [16, 24, 32].includes(Number(bits)) ? Number(bits) : 16;
  const count = sampleRate * 5;
  const bytesPerSample = bits / 8;
  const dataBytes = count * bytesPerSample;
  const wav = Buffer.alloc(44 + dataBytes);
  wav.write('RIFF');
  wav.writeUInt32LE(wav.length - 8, 4);
  wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(sampleRate, 24);
  wav.writeUInt32LE(sampleRate * bytesPerSample, 28);
  wav.writeUInt16LE(bytesPerSample, 32);
  wav.writeUInt16LE(bits, 34);
  wav.write('data', 36);
  wav.writeUInt32LE(dataBytes, 40);
  for (let i = 0; i < count; i++) {
    const value = Math.sin(i / sampleRate * Math.PI * 2 * 220) * 0.04 * (2 ** (bits - 1) - 1) * Math.exp(-i / sampleRate);
    const offset = 44 + i * bytesPerSample;
    if (bits === 16) wav.writeInt16LE(Math.round(value), offset);
    else if (bits === 24) { const sample = Math.round(value); wav[offset] = sample & 255; wav[offset + 1] = (sample >> 8) & 255; wav[offset + 2] = (sample >> 16) & 255; }
    else wav.writeInt32LE(Math.round(value), offset);
  }
  return wav;
}

export function makeTestWav({ sampleRate = 48000, bits = 24, channels = 1, frames = 4800 } = {}) {
  const bytesPerSample = bits / 8;
  const dataLength = frames * channels * bytesPerSample;
  const wav = Buffer.alloc(44 + dataLength);
  wav.write('RIFF'); wav.writeUInt32LE(wav.length - 8, 4);
  wav.write('WAVEfmt ', 8); wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20); wav.writeUInt16LE(channels, 22);
  wav.writeUInt32LE(sampleRate, 24); wav.writeUInt32LE(sampleRate * channels * bytesPerSample, 28);
  wav.writeUInt16LE(channels * bytesPerSample, 32); wav.writeUInt16LE(bits, 34);
  wav.write('data', 36); wav.writeUInt32LE(dataLength, 40);
  for (let i = 0; i < frames * channels; i++) {
    const value = Math.round(Math.sin(i * 0.075) * 0.45 * (2 ** (bits - 1) - 1));
    const offset = 44 + i * bytesPerSample;
    if (bits === 16) wav.writeInt16LE(value, offset);
    else if (bits === 24) { wav[offset] = value & 255; wav[offset + 1] = (value >> 8) & 255; wav[offset + 2] = (value >> 16) & 255; }
    else if (bits === 32) wav.writeInt32LE(value, offset);
  }
  return wav;
}
