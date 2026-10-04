const DB_KEY = 'audio-rec.local-workspace.v1';
const AUDIO_DB = 'audio-rec.local-audio.v1';
const AUDIO_STORE = 'recordings';

const clone = value => JSON.parse(JSON.stringify(value));
const makeId = prefix => `${prefix}-${globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`}`;
const today = () => new Date().toISOString().slice(0, 10);

function readState() {
  try {
    const raw = localStorage.getItem(DB_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeState(state) {
  try {
    localStorage.setItem(DB_KEY, JSON.stringify(state));
  } catch (error) {
    throw new Error(`Could not save the local workspace: ${error?.message || 'device storage is unavailable'}`);
  }
}

function createWorkspace() {
  const sessionId = makeId('session');
  return {
    sessions: [{
      id: sessionId,
      name: 'My first session',
      type: 'Music',
      description: 'A private workspace saved on this device.',
      date: today(),
      duration: '00:00',
      tracks: 2,
      format: 'PCM · input format negotiated by Android',
      status: 'In progress',
      color: 'orange'
    }],
    tracks: [
      { id: makeId('track'), name: 'Input 1', sessionId, input: 'Input 1', gain: 0, muted: false, armed: true, color: 'orange', recorded: false },
      { id: makeId('track'), name: 'Input 2', sessionId, input: 'Input 2', gain: 0, muted: false, armed: false, color: 'purple', recorded: false }
    ],
    presets: [],
    exports: []
  };
}

function ensureState() {
  let state = readState();
  if (!state || !state.workspace) {
    state = { profile: null, workspace: createWorkspace() };
    writeState(state);
  }
  return state;
}

function openAudioDb() {
  if (!globalThis.indexedDB) return Promise.reject(new Error('This WebView does not support local audio storage.'));
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(AUDIO_DB, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(AUDIO_STORE)) db.createObjectStore(AUDIO_STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('Could not open local audio storage.'));
    request.onblocked = () => reject(new Error('Local audio storage is busy. Close other AUDIO-rec tabs and try again.'));
  });
}

async function audioTransaction(mode, operation) {
  const db = await openAudioDb();
  try {
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction(AUDIO_STORE, mode);
      const store = transaction.objectStore(AUDIO_STORE);
      let request;
      try { request = operation(store); }
      catch (error) { reject(error); return; }
      transaction.oncomplete = () => resolve(request?.result);
      transaction.onerror = () => reject(transaction.error || request?.error || new Error('Could not access local audio storage.'));
      transaction.onabort = () => reject(transaction.error || new Error('Local audio storage was interrupted.'));
    });
  } finally {
    db.close();
  }
}

function base64ToBlob(value, type = 'audio/wav') {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type });
}

function resourceRoute(path) {
  const match = path.match(/^\/resources\/(sessions|tracks|presets|exports)(?:\/([^/]+))?$/);
  return match ? { kind: match[1], id: match[2] } : null;
}

function findResource(workspace, kind, id) {
  const item = workspace[kind]?.find(value => value.id === id);
  if (!item) throw new Error(`${kind.slice(0, -1)} not found.`);
  return item;
}

function updateSessionTrackCount(workspace, sessionId, delta) {
  const session = workspace.sessions.find(item => item.id === sessionId);
  if (session) session.tracks = Math.max(0, Number(session.tracks || 0) + delta);
}

export async function offlineApi(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  let body = {};
  if (options.body) {
    try { body = JSON.parse(options.body); }
    catch { throw new Error('The local request was not valid JSON.'); }
  }

  if (path === '/auth/me' && method === 'GET') {
    const state = readState();
    if (!state?.profile) throw new Error('No local profile yet.');
    return clone(state.profile);
  }
  if (path === '/auth/demo' && method === 'POST') {
    const state = ensureState();
    if (!state.profile) state.profile = { id: makeId('local-user'), name: 'Local studio', email: 'local@device', local: true };
    writeState(state);
    return clone(state.profile);
  }
  if (path === '/auth/logout' && method === 'POST') return { ok: true };
  if (path === '/workspace' && method === 'GET') return clone(ensureState().workspace);

  const audioMatch = path.match(/^\/audio\/([^/]+)$/);
  if (audioMatch && method === 'POST') {
    const state = ensureState();
    const track = findResource(state.workspace, 'tracks', audioMatch[1]);
    if (typeof body.data !== 'string') throw new Error('Missing WAV recording.');
    const blob = base64ToBlob(body.data);
    const wav = new DataView(await blob.slice(0, 44).arrayBuffer());
    if (blob.size < 44 || String.fromCharCode(...new Uint8Array(await blob.slice(0, 4).arrayBuffer())) !== 'RIFF') {
      throw new Error('The WAV recording is invalid.');
    }
    await audioTransaction('readwrite', store => store.put(blob, track.id));
    const sampleRate = wav.getUint32(24, true) || Number(body.rate) || 48000;
    const depth = wav.getUint16(34, true) || Number(body.depth) || 16;
    const seconds = Math.floor((wav.getUint32(40, true) || Math.max(0, blob.size - 44)) / Math.max(1, wav.getUint32(28, true) || sampleRate * (depth / 8)));
    const duration = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
    Object.assign(track, { recorded: true, recordedRate: sampleRate, recordedDepth: depth, duration, size: `${(blob.size / 1048576).toFixed(1)} MB` });
    const session = state.workspace.sessions.find(item => item.id === track.sessionId);
    if (session && (!session.duration || session.duration === '00:00')) session.duration = duration;
    writeState(state);
    return { ok: true, duration, size: track.size };
  }

  const route = resourceRoute(path);
  if (route) {
    const state = ensureState();
    const { workspace } = state;
    if (!route.id && method === 'POST') {
      const item = { ...body, id: makeId(route.kind.slice(0, -1)) };
      if (route.kind === 'sessions') Object.assign(item, { date: item.date || today(), duration: item.duration || '00:00', tracks: 0, color: item.color || 'orange' });
      if (route.kind === 'tracks') {
        if (!workspace.sessions.some(session => session.id === item.sessionId)) throw new Error('Choose a session on this device first.');
        Object.assign(item, { recorded: false, armed: item.armed ?? false, gain: Number(item.gain) || 0 });
        updateSessionTrackCount(workspace, item.sessionId, 1);
      }
      if (route.kind === 'exports') Object.assign(item, { date: item.date || today(), status: item.status || 'Ready' });
      workspace[route.kind].unshift(item);
      writeState(state);
      return clone(item);
    }
    if (route.id && method === 'PATCH') {
      const item = findResource(workspace, route.kind, route.id);
      const previousSessionId = item.sessionId;
      Object.assign(item, body);
      if (route.kind === 'tracks' && body.sessionId && body.sessionId !== previousSessionId) {
        updateSessionTrackCount(workspace, previousSessionId, -1);
        updateSessionTrackCount(workspace, body.sessionId, 1);
      }
      writeState(state);
      return clone(item);
    }
    if (route.id && method === 'DELETE') {
      const item = findResource(workspace, route.kind, route.id);
      const removedTrackIds = [];
      if (route.kind === 'sessions') {
        workspace.tracks = workspace.tracks.filter(track => {
          if (track.sessionId === route.id) removedTrackIds.push(track.id);
          return track.sessionId !== route.id;
        });
        workspace.exports = workspace.exports.filter(file => file.sessionId !== route.id);
      } else if (route.kind === 'tracks') {
        removedTrackIds.push(item.id);
        updateSessionTrackCount(workspace, item.sessionId, -1);
        workspace.exports = workspace.exports.filter(file => file.trackId !== item.id);
      }
      workspace[route.kind] = workspace[route.kind].filter(value => value.id !== route.id);
      writeState(state);
      await Promise.all(removedTrackIds.map(id => audioTransaction('readwrite', store => store.delete(id)).catch(() => undefined)));
      return { ok: true };
    }
  }

  throw new Error(`This action is not available in the local workspace: ${method} ${path}`);
}

export async function saveOfflineRecording(trackId, wavBlob, metadata = {}) {
  if (!(wavBlob instanceof Blob) || wavBlob.size < 44) throw new Error('The WAV recording is empty or invalid.');
  const header = new DataView(await wavBlob.slice(0, 44).arrayBuffer());
  const signature = String.fromCharCode(...new Uint8Array(await wavBlob.slice(0, 4).arrayBuffer()));
  if (signature !== 'RIFF' || String.fromCharCode(...new Uint8Array(await wavBlob.slice(8, 12).arrayBuffer())) !== 'WAVE') {
    throw new Error('The WAV recording is invalid.');
  }
  await audioTransaction('readwrite', store => store.put(wavBlob, trackId));
  const state = ensureState();
  const track = findResource(state.workspace, 'tracks', trackId);
  const sampleRate = header.getUint32(24, true) || Number(metadata.rate) || 48000;
  const depth = header.getUint16(34, true) || Number(metadata.depth) || 16;
  const seconds = Math.floor((header.getUint32(40, true) || wavBlob.size - 44) / Math.max(1, header.getUint32(28, true) || sampleRate * (depth / 8)));
  const duration = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
  Object.assign(track, { recorded: true, recordedRate: sampleRate, recordedDepth: depth, duration, size: `${(wavBlob.size / 1048576).toFixed(1)} MB` });
  const session = state.workspace.sessions.find(item => item.id === track.sessionId);
  if (session && (!session.duration || session.duration === '00:00')) session.duration = duration;
  writeState(state);
  return { ok: true, duration, size: track.size };
}

export async function getOfflineRecording(trackId) {
  const blob = await audioTransaction('readonly', store => store.get(trackId));
  return blob || null;
}

export function requestPersistentLocalStorage() {
  try { return navigator.storage?.persist?.().catch(() => false); }
  catch { return Promise.resolve(false); }
}
