import test from 'node:test';
import assert from 'node:assert/strict';
import { offlineApi, saveOfflineRecording, getOfflineRecording } from './offline-store.js';

function installLocalStorage() {
  const values = new Map();
  globalThis.localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    clear: () => values.clear()
  };
}

function installIndexedDb() {
  const records = new Map();
  class FakeDb {
    constructor() { this.objectStoreNames = { contains: () => true }; }
    close() {}
    transaction() {
      const transaction = { error: null };
      transaction.objectStore = () => ({
        put: (value, key) => { records.set(key, value); queueMicrotask(() => transaction.oncomplete?.()); return { result: key }; },
        get: key => { const request = { result: records.get(key) }; queueMicrotask(() => transaction.oncomplete?.()); return request; },
        delete: key => { records.delete(key); queueMicrotask(() => transaction.oncomplete?.()); return { result: undefined }; }
      });
      return transaction;
    }
  }
  globalThis.indexedDB = {
    open: () => {
      const request = {};
      queueMicrotask(() => { request.result = new FakeDb(); request.onsuccess?.(); });
      return request;
    }
  };
}

function smallPcmWav() {
  const buffer = new ArrayBuffer(48);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const text = (offset, value) => [...value].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0)));
  text(0, 'RIFF'); view.setUint32(4, 40, true); text(8, 'WAVE'); text(12, 'fmt ');
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, 48000, true); view.setUint32(28, 96000, true);
  view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  text(36, 'data'); view.setUint32(40, 4, true); view.setInt16(44, 1200, true); view.setInt16(46, -1200, true);
  return new Blob([bytes], { type: 'audio/wav' });
}

test('offline profile and workspace initialize locally and survive reload-style reads', async () => {
  installLocalStorage();
  await assert.rejects(offlineApi('/auth/me'), /No local profile/);
  const profile = await offlineApi('/auth/demo', { method: 'POST', body: '{}' });
  assert.equal(profile.local, true);
  assert.equal((await offlineApi('/auth/me')).id, profile.id);
  const workspace = await offlineApi('/workspace');
  assert.equal(workspace.sessions.length, 1);
  assert.equal(workspace.tracks.length, 2);
});

test('offline sessions, tracks, presets, exports and cascades use local CRUD', async () => {
  installLocalStorage();
  await offlineApi('/auth/demo', { method: 'POST', body: '{}' });
  const workspace = await offlineApi('/workspace');
  const session = await offlineApi('/resources/sessions', { method: 'POST', body: JSON.stringify({ name: 'Offline take', type: 'Music' }) });
  const track = await offlineApi('/resources/tracks', { method: 'POST', body: JSON.stringify({ name: 'Lead', sessionId: session.id, input: 'Input 1' }) });
  const preset = await offlineApi('/resources/presets', { method: 'POST', body: JSON.stringify({ name: 'My interface', device: 'Not selected', rate: '48000', depth: '24' }) });
  const file = await offlineApi('/resources/exports', { method: 'POST', body: JSON.stringify({ name: 'Lead WAV', sessionId: session.id, trackId: track.id, format: 'WAV' }) });
  await offlineApi(`/resources/tracks/${track.id}`, { method: 'PATCH', body: JSON.stringify({ gain: 6 }) });
  let current = await offlineApi('/workspace');
  assert.equal(current.tracks.find(item => item.id === track.id).gain, 6);
  assert.equal(current.sessions.find(item => item.id === session.id).tracks, 1);
  await offlineApi(`/resources/presets/${preset.id}`, { method: 'DELETE' });
  await offlineApi(`/resources/sessions/${session.id}`, { method: 'DELETE' });
  current = await offlineApi('/workspace');
  assert.equal(current.tracks.some(item => item.id === track.id), false);
  assert.equal(current.exports.some(item => item.id === file.id), false);
  assert.equal(current.presets.some(item => item.id === preset.id), false);
  assert.equal(current.sessions.some(item => item.id === workspace.sessions[0].id), true);
});

test('offline recordings persist as WAV Blobs in device-local IndexedDB', async () => {
  installLocalStorage();
  installIndexedDb();
  await offlineApi('/auth/demo', { method: 'POST', body: '{}' });
  const workspace = await offlineApi('/workspace');
  const trackId = workspace.tracks[0].id;
  const wav = smallPcmWav();
  const saved = await saveOfflineRecording(trackId, wav, { rate: 48000, depth: 16 });
  assert.equal(saved.ok, true);
  assert.equal((await getOfflineRecording(trackId)).size, wav.size);
  const updated = await offlineApi('/workspace');
  assert.equal(updated.tracks.find(track => track.id === trackId).recorded, true);
  assert.equal(updated.tracks.find(track => track.id === trackId).recordedRate, 48000);
});
