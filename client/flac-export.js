import flacScriptUrl from 'libflacjs/dist/libflac.min.js?url';
import { Encoder } from 'libflacjs/lib/encoder.js';
import { exportFlacData } from 'libflacjs/lib/utils/flac-utils.js';

let ready;

async function loadFlac() {
  if (globalThis.Flac?.isReady?.()) return globalThis.Flac;
  if (ready) return ready;
  ready = new Promise((resolve, reject) => {
    const finish = () => {
      const flac = globalThis.Flac;
      if (flac?.isReady?.()) resolve(flac);
      else if (flac?.on) flac.on('ready', () => resolve(flac));
      else reject(new Error('The offline FLAC encoder did not initialize.'));
    };
    if (globalThis.Flac) {
      finish();
      return;
    }
    const script = document.createElement('script');
    script.src = flacScriptUrl;
    script.async = true;
    script.onload = finish;
    script.onerror = () => reject(new Error('Could not load the offline FLAC encoder.'));
    document.head.appendChild(script);
  }).catch(error => {
    ready = null;
    throw error;
  });
  return ready;
}

function readSample(view, offset, bits) {
  if (bits === 16) return view.getInt16(offset, true);
  if (bits === 24) {
    const value = view.getUint8(offset) | (view.getUint8(offset + 1) << 8) | (view.getUint8(offset + 2) << 16);
    return value & 0x800000 ? value | 0xff000000 : value;
  }
  if (bits === 32) return view.getInt32(offset, true);
  throw new Error(`Unsupported PCM source depth: ${bits}-bit.`);
}

export async function encodeOfflineFlac(wavBlob, requestedDepth = 24) {
  const Flac = await loadFlac();
  const buffer = await wavBlob.arrayBuffer();
  if (buffer.byteLength < 44) throw new Error('The source WAV is incomplete.');
  const view = new DataView(buffer);
  const signature = String.fromCharCode(...new Uint8Array(buffer, 0, 4));
  if (signature !== 'RIFF' || String.fromCharCode(...new Uint8Array(buffer, 8, 4)) !== 'WAVE') {
    throw new Error('The selected source is not a PCM WAV file.');
  }
  const channels = view.getUint16(22, true);
  const sampleRate = view.getUint32(24, true);
  const sourceDepth = view.getUint16(34, true);
  const bits = Number(requestedDepth) || (sourceDepth === 32 ? 24 : sourceDepth);
  const dataOffset = 44;
  const bytesPerSample = sourceDepth / 8;
  const dataLength = view.getUint32(40, true);
  const sampleCount = Math.floor(dataLength / Math.max(1, channels * bytesPerSample));

  if (!channels || !sampleRate || !sampleCount || ![16, 24, 32].includes(sourceDepth) || ![16, 24].includes(bits)) {
    throw new Error('FLAC export requires a valid 16-, 24- or 32-bit PCM WAV source and a 16- or 24-bit target.');
  }

  const interleaved = new Int32Array(sampleCount * channels);
  for (let index = 0; index < interleaved.length; index++) {
    const sample = readSample(view, dataOffset + index * bytesPerSample, sourceDepth);
    const shift = sourceDepth - bits;
    interleaved[index] = shift > 0 ? Math.round(sample / (2 ** shift)) : shift < 0 ? sample * (2 ** -shift) : sample;
  }

  const encoder = new Encoder(Flac, {
    sampleRate,
    channels,
    bitsPerSample: bits,
    compression: 5,
    totalSamples: sampleCount,
    verify: false
  });

  try {
    if (!encoder.initialized) throw new Error(`The FLAC encoder does not support ${sampleRate.toLocaleString()} Hz at ${bits} bits.`);
    const chunkSize = 65536;
    for (let offset = 0; offset < interleaved.length; offset += chunkSize) {
      const end = Math.min(offset + chunkSize, interleaved.length);
      const frames = (end - offset) / channels;
      if (!encoder.encode(interleaved.subarray(offset, end), frames, true)) throw new Error('FLAC encoding failed.');
    }
    if (!encoder.encode()) throw new Error('FLAC finalization failed.');
    const encoded = await exportFlacData(encoder.rawData, encoder.metadata, false);
    return new Blob([encoded], { type: 'audio/flac' });
  } finally {
    encoder.destroy();
  }
}
