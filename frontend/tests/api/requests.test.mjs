import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';

test('respuesta antigua se descarta aunque ignore cancelacion', async () => {
  const latest = createLatestRequest(); let resolveOld; let oldSignal;
  const old = latest.run(signal => { oldSignal = signal; return new Promise(resolve => { resolveOld = resolve; }); });
  const current = await latest.run(async () => 'nuevo');
  assert.equal(oldSignal.aborted, true); assert.deepEqual(current, { state: 'current', value: 'nuevo' });
  resolveOld('antiguo'); assert.deepEqual(await old, { state: 'stale' });
});
test('cancelar resultado actual no publica valor', async () => {
  const latest = createLatestRequest(); let resolve;
  const pending = latest.run(() => new Promise(r => { resolve = r; })); latest.cancel(); resolve('descartar');
  assert.deepEqual(await pending, { state: 'cancelled' });
});
test('error de operacion es seguro y coordinadores independientes', async () => {
  const a = createLatestRequest(), b = createLatestRequest();
  assert.deepEqual(await a.run(async () => { throw new Error('NO_PUBLICAR'); }), { state: 'error', reason: 'OPERACION_FALLIDA' });
  assert.deepEqual(await b.run(async () => 0), { state: 'current', value: 0 });
});
