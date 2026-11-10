import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApiClient } from '../../.api-test-build/api/client.js';
import { createMockFetch } from '../../.api-test-build/mocks/mockFetch.js';

test('cuatro rutas, parametros y transporte GET sin credenciales', async () => {
  const calls = [];
  const api = createApiClient({ mode: 'http', baseUrl: 'http://localhost:8081', fetcher: async (url, init) => {
    calls.push({ url, init }); return new Response('{}');
  } });
  assert.equal(calls.length, 0);
  await api.dashboard('Minería & prueba', 2017); await api.consultoras();
  await api.datastream('CarteraProyectos'); await api.estadoServicios();
  assert.deepEqual(calls.map(c => c.url.pathname), ['/api/dashboard', '/api/consultoras', '/api/senace/datastream', '/api/estado-servicios']);
  assert.equal(calls[0].url.searchParams.get('sector'), 'Minería & prueba');
  assert.equal(calls[0].url.searchParams.get('anio'), '2017');
  for (const { init } of calls) { assert.equal(init.method, 'GET'); assert.equal(init.credentials, 'omit'); assert.equal(init.redirect, 'error'); }
});

test('anio y nombre omitidos permiten defaults del contrato, no anio inventado', async () => {
  const urls = [];
  const api = createApiClient({ mode: 'http', baseUrl: 'http://localhost:8081', fetcher: async u => { urls.push(u); return new Response('{}'); } });
  await api.dashboard('TODOS'); await api.datastream();
  assert.equal(urls[0].searchParams.has('anio'), false); assert.equal(urls[1].searchParams.has('nombre'), false);
  assert.equal((await api.dashboard('  ')).issue, 'PARAMETROS_INVALIDOS');
  assert.equal(urls.length, 2);
});

test('status headers json y texto original se conservan en error HTTP', async () => {
  const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/consultoras': 'consultants503' }) });
  const r = await api.consultoras();
  assert.equal(r.status, 503); assert.deepEqual(r.json, []); assert.equal(r.rawText, '[]');
  assert.equal(r.headers['x-senace-motivo'], 'CONFIGURACION_AUSENTE'); assert.equal(r.issue, undefined);
});

test('json invalido conserva status, no expone excepcion externa', async () => {
  const r = await createApiClient({ mode: 'mock', fetcher: async () => new Response('<html>prueba</html>', { status: 502 }) }).consultoras();
  assert.equal(r.status, 502); assert.equal(r.issue, 'FORMATO'); assert.equal(r.json, undefined);
});

test('error de red no hace retry ni fallback mock', async () => {
  let calls = 0;
  const api = createApiClient({ mode: 'http', baseUrl: 'http://localhost:8081', fetcher: async () => { calls++; throw new Error('DETALLE_NO_PUBLICABLE'); } });
  const r = await api.consultoras();
  assert.equal(calls, 1); assert.equal(r.issue, 'RED'); assert.equal(r.mode, 'http');
  assert.equal(JSON.stringify(r).includes('DETALLE_NO_PUBLICABLE'), false);
});

test('configuracion ausente/invalida no llama al transporte', async () => {
  let calls = 0;
  for (const baseUrl of [null, 'javascript:alert(1)', 'https://user:pass@example.invalid', 'https://example.invalid?auth_key=prueba']) {
    const r = await createApiClient({ mode: 'http', baseUrl, fetcher: async () => { calls++; return new Response('{}'); } }).consultoras();
    assert.match(r.issue, /^CONFIGURACION_/);
  }
  assert.equal(calls, 0);
});

test('cancelacion previa no invoca transporte', async () => {
  const controller = new AbortController(); controller.abort(); let calls = 0;
  const r = await createApiClient({ mode: 'mock', fetcher: async () => { calls++; return new Response('[]'); } }).consultoras(controller.signal);
  assert.equal(r.issue, 'CANCELADA'); assert.equal(calls, 0);
});

test('mock explicito nunca invoca fetch global y senal aborta espera artificial', async () => {
  const previous = globalThis.fetch; let realCalls = 0;
  globalThis.fetch = async () => { realCalls++; throw new Error('RED_PROHIBIDA'); };
  try {
    const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/dashboard': 'dashboardSuccess', '/api/consultoras': 'consultantsSuccess' }, 10) });
    assert.equal((await api.dashboard('TODOS')).mode, 'mock');
    const controller = new AbortController(); const pending = api.consultoras(controller.signal); controller.abort();
    assert.equal((await pending).issue, 'CANCELADA');
    assert.equal((await api.estadoServicios()).issue, 'RED');
    assert.equal(realCalls, 0);
  } finally { globalThis.fetch = previous; }
});

test('rawText preserva literal decimal pero JSON number no garantiza BigDecimal', async () => {
  const literal = '{"valor":0.12345678901234567890123456789}';
  const r = await createApiClient({ mode: 'mock', fetcher: async () => new Response(literal) }).estadoServicios();
  assert.equal(r.rawText, literal); assert.equal(typeof r.json.valor, 'number');
  assert.notEqual(String(r.json.valor), '0.12345678901234567890123456789');
});
