import test from 'node:test';
import assert from 'node:assert/strict';
import { demoWav, encodeFlac, makeTestWav } from './audio.js';

test('demo WAV is a valid, uncompressed PCM file', () => {
  const wav = demoWav();
  assert.equal(wav.toString('ascii', 0, 4), 'RIFF');
  assert.equal(wav.toString('ascii', 8, 12), 'WAVE');
  assert.equal(wav.readUInt16LE(20), 1);
  assert.equal(wav.readUInt16LE(34), 16);
  assert.equal(wav.readUInt32LE(24), 48000);
  assert.equal(wav.length, wav.readUInt32LE(4) + 8);
});

test('FLAC exports preserve a PCM source and produce a native FLAC stream', async () => {
  const source = makeTestWav({ sampleRate: 48000, bits: 24, frames: 4800 });
  const flac = await encodeFlac(source, 24);
  assert.equal(flac.toString('ascii', 0, 4), 'fLaC');
  assert.ok(flac.length > 100);
});

test('32-bit WAV stems can be exported as 24-bit FLAC', async () => {
  const source = makeTestWav({ sampleRate: 48000, bits: 32, frames: 4800 });
  const flac = await encodeFlac(source, 24);
  assert.equal(flac.toString('ascii', 0, 4), 'fLaC');
  assert.ok(flac.length > 100);
});

test('FLAC does not claim to support 32-bit PCM', async () => {
  const source = makeTestWav({ sampleRate: 48000, bits: 32, frames: 48 });
  await assert.rejects(encodeFlac(source, 32), /16- or 24-bit PCM/);
});
