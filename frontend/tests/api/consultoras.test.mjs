import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';
import { adaptConsultoras } from '../../.api-test-build/api/adapters/consultoras.js';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';
import { parseSourceConfig } from '../../.api-test-build/api/source.js';
import { consultantFields, consultantKpis, consultantMessage, displayField, emptyFilters, filterConsultants, filterOptions, loadConsultants, paginateConsultants, usableRows } from '../../.api-test-build/features/consultoras/consultorasModel.js';

// Datos artificiales; ninguna observación de SENACE ni información personal real.
function snapshot(scenario = 'consultorasDemoSuccess') {
  const item = fixture(scenario);
  const http = { mode: 'mock', status: item.status, headers: Object.fromEntries(new Headers(item.headers).entries()), json: item.body };
  return { scenario, http, result: adaptConsultoras(http) };
}
const select = (s, filters = emptyFilters) => filterConsultants(usableRows(s), filters);

test('solo se describen los ocho campos contractuales', () => {
  assert.deepEqual(consultantFields.map(([key]) => key), ['razonSocial', 'actividad', 'subsector', 'tipoSolicitud', 'resolucion', 'fechaRd', 'inicioVigenciaRd', 'finVigenciaRd']);
});

test('KPI describen filas conocidas sin convertirlas en empresas únicas', () => {
  const s = snapshot(); const entries = select(s);
  assert.equal(s.result.state, 'ready');
  assert.deepEqual(consultantKpis(s, entries), { received: 14, filtered: 14, withResolution: 9 });
  assert.deepEqual(entries[0].row, entries[12].row);
  assert.notEqual(entries[0].key, entries[12].key);
});

test('búsqueda normaliza acentos/caso y conserva originales y orden', () => {
  const s = snapshot(); const rows = usableRows(s); const copy = structuredClone(rows);
  assert.equal(select(s, { ...emptyFilters, search: '  MINERIA  ' }).length, 7);
  assert.equal(select(s, { ...emptyFilters, search: 'FECHA ORIGINAL NO EVALUADA' }).length, 1);
  assert.equal(select(s, { ...emptyFilters, search: 'RESOLUCION ARTIFICIAL 12' }).length, 1);
  assert.deepEqual(rows, copy);
  assert.deepEqual(select(s).map(entry => entry.key), Array.from({ length: 14 }, (_, i) => String(i)));
});

test('los tres filtros se combinan localmente sin deduplicar', () => {
  const s = snapshot();
  const filters = { ...emptyFilters, actividad: 'Minería', subsector: 'SUBSECTOR ARTIFICIAL A', tipoSolicitud: 'SOLICITUD ARTIFICIAL A' };
  const entries = select(s, filters);
  assert.equal(entries.length, 3); // filas 1, 7 y repetición de la primera
  assert.equal(consultantKpis(s, entries).withResolution, 1);
  assert.deepEqual(filterOptions(usableRows(s), 'actividad'), ['Hidrocarburos', 'Minería']);
});

test('paginación conserva duplicados, limita filas y ajusta páginas fuera de rango', () => {
  const entries = select(snapshot());
  const first = paginateConsultants(entries, 1); const second = paginateConsultants(entries, 99);
  assert.equal(first.rows.length, 10); assert.equal(first.start, 1); assert.equal(first.end, 10);
  assert.equal(second.rows.length, 4); assert.equal(second.page, 2); assert.equal(second.end, 14);
  assert.equal(second.rows[2].key, '12'); assert.deepEqual(second.rows[2].row, first.rows[0].row);
  assert.equal(paginateConsultants(entries, -4).page, 1);
  assert.throws(() => paginateConsultants(entries, 1, 0), /TAMANO_PAGINA_INVALIDO/);
});

test('volumen artificial grande no aumenta el número de filas de una página', () => {
  const rows = Array.from({ length: 20000 }, () => ({ razonSocial: 'FILA ARTIFICIAL REPETIDA' }));
  const entries = filterConsultants(rows, emptyFilters);
  assert.equal(entries.length, 20000);
  assert.equal(paginateConsultants(entries, 500).rows.length, 10);
  assert.equal(paginateConsultants(entries, 500).start, 4991);
});

test('el vacío oficial permite conteos cero; filtro sin coincidencias conserva recibidos', () => {
  const empty = snapshot('consultantsEmpty');
  assert.equal(empty.result.state, 'empty');
  assert.deepEqual(consultantKpis(empty, select(empty)), { received: 0, filtered: 0, withResolution: 0 });
  const full = snapshot(); const unmatched = select(full, { ...emptyFilters, search: 'NO_EXISTE' });
  assert.deepEqual(consultantKpis(full, unmatched), { received: 14, filtered: 0, withResolution: 0 });
  assert.equal(paginateConsultants(unmatched, 3).pages, 0);
});

for (const scenario of ['consultants502', 'consultants503']) test(`${scenario}: array de error no es cero oficial`, () => {
  const s = snapshot(scenario); assert.equal(s.result.state, 'error'); assert.equal(usableRows(s), undefined);
  assert.deepEqual(consultantKpis(s, select(s)), { received: undefined, filtered: undefined, withResolution: undefined });
  assert.equal(paginateConsultants(select(s), 1).total, undefined);
  assert.ok(consultantMessage(s).includes(String(s.http.status)));
});

test('headers ausentes: filas no vacías degradadas se cuentan sin afirmar origen oficial', () => {
  const s = snapshot('consultorasDemoNoHeaders');
  assert.equal(s.result.state, 'degraded'); assert.equal(s.result.data.consulta.origen, undefined);
  assert.equal(consultantKpis(s, select(s)).received, 14);
  assert.match(consultantMessage(s), /Procedencia no confirmada/);
});

test('array vacío sin headers no acredita vacío oficial', () => {
  const s = snapshot('consultorasDemoEmptyNoHeaders');
  assert.equal(s.result.state, 'degraded'); assert.deepEqual(usableRows(s), []);
  assert.equal(consultantKpis(s, select(s)).received, undefined);
});

test('estructura inválida no ofrece filas ni conteos', () => {
  const s = snapshot('consultorasDemoInvalid');
  assert.equal(s.result.state, 'error'); assert.equal(usableRows(s), undefined);
  assert.equal(consultantKpis(s, select(s)).filtered, undefined);
});

test('opcionales y vacíos no generan identificadores o estado; fechas quedan intactas', () => {
  const s = snapshot('consultorasDemoOptional');
  assert.deepEqual(consultantKpis(s, select(s)), { received: 2, filtered: 2, withResolution: 0 });
  assert.equal(displayField(undefined), 'No disponible'); assert.equal(displayField('  '), 'No disponible');
  assert.equal(displayField('  TEXTO ORIGINAL  '), '  TEXTO ORIGINAL  ');
  const rows = usableRows(snapshot()); assert.equal(rows[1].fechaRd, 'FECHA ORIGINAL NO EVALUADA');
  assert.equal(rows[2].inicioVigenciaRd, '');
  for (const row of rows) for (const field of ['ruc', 'registro', 'id', 'nivel', 'estado', 'activa', 'vigente']) assert.equal(row[field], undefined);
});

test('detalle usa fila exacta incluso cuando existen nombres repetidos', () => {
  const entries = select(snapshot()); const detail = entries.find(entry => entry.key === '12');
  assert.deepEqual(detail.row, entries[0].row); assert.equal(detail.key, '12');
  assert.equal(entries.find(entry => entry.key === '13').row.razonSocial, undefined);
});

test('carga usa solo transporte mock y filtros/páginas no invocan fetch global', async () => {
  const original = globalThis.fetch; let requests = 0;
  globalThis.fetch = () => { requests++; throw new Error('HTTP_REAL_PROHIBIDO'); };
  try {
    const s = await loadConsultants('consultorasDemoSuccess');
    assert.equal(s.http.mode, 'mock'); assert.equal(s.http.status, 200);
    assert.equal(s.http.headers['x-senace-cobertura'], 'NO_VERIFICADA');
    for (let i = 1; i < 5; i++) paginateConsultants(select(s), i);
    assert.equal(requests, 0);
  } finally { globalThis.fetch = original; }
});

test('cancelación y resultado obsoleto no publican otra selección', async () => {
  const coordinator = createLatestRequest();
  const old = coordinator.run(signal => loadConsultants('consultorasDemoSuccess', signal));
  const current = coordinator.run(signal => loadConsultants('consultantsEmpty', signal));
  assert.equal((await old).state, 'stale');
  const result = await current; assert.equal(result.state, 'current'); assert.equal(result.value.result.state, 'empty');
  const cancelled = coordinator.run(signal => loadConsultants('consultorasDemoSuccess', signal));
  coordinator.cancel(); assert.equal((await cancelled).state, 'cancelled');
});

const httpConfig = parseSourceConfig({ VITE_API_MODE: 'http', VITE_API_BASE_URL: 'http://127.0.0.1:8081', VITE_HTTP_FEATURES: 'monitor,consultoras' });
const officialHeaders = { 'Content-Type': 'application/json', 'X-Senace-Origen': 'API_DIRECTA_SENACE', 'X-Senace-Estado': 'EXITO', 'X-Senace-Cobertura': 'NO_VERIFICADA', 'X-Senace-Cuerpo-Completo': 'true' };
const httpRow = { razonSocial: 'FILA ARTIFICIAL HTTP DE PRUEBA', actividad: 'ACTIVIDAD ARTIFICIAL', subsector: 'SUBSECTOR ARTIFICIAL', tipoSolicitud: 'TIPO ARTIFICIAL', resolucion: 'RESOLUCIÓN ARTIFICIAL', fechaRd: 'FECHA ORIGINAL SIN INTERPRETAR', inicioVigenciaRd: '', finVigenciaRd: 'TEXTO ORIGINAL' };

test('HTTP devuelve exclusivamente filas inyectadas con duplicados, fechas y ocho campos', async () => {
  let calls = 0;
  const s = await loadConsultants('consultants503', undefined, httpConfig, async () => { calls++; return new Response(JSON.stringify([httpRow, httpRow, {}]), { status: 200, headers: officialHeaders }); });
  assert.equal(s.http.mode, 'http'); assert.equal(s.scenario, undefined); assert.equal(s.result.state, 'ready');
  assert.equal(calls, 1); assert.equal(usableRows(s).length, 3);
  assert.deepEqual(usableRows(s)[0], usableRows(s)[1]); assert.equal(usableRows(s)[0].fechaRd, httpRow.fechaRd);
  assert.equal(usableRows(s)[2].razonSocial, undefined);
  assert.deepEqual(consultantKpis(s, select(s)), { received: 3, filtered: 3, withResolution: 2 });
  assert.ok(!consultantMessage(s).includes('artificiales disponibles'));
  for (const header of ['x-senace-origen', 'x-senace-estado', 'x-senace-cobertura', 'x-senace-cuerpo-completo']) assert.ok(s.http.headers[header]);
});

test('HTTP vacío acreditado conserva los cinco headers y cero conocido', async () => {
  const s = await loadConsultants('consultorasDemoSuccess', undefined, httpConfig, async () => new Response('[]', { headers: { ...officialHeaders, 'X-Senace-Estado': 'VACIA', 'X-Senace-Motivo': 'RESPUESTA_OFICIAL_VACIA' } }));
  assert.equal(s.result.state, 'empty'); assert.equal(s.http.headers['x-senace-motivo'], 'RESPUESTA_OFICIAL_VACIA');
  assert.equal(consultantKpis(s, select(s)).received, 0); assert.ok(!consultantMessage(s).includes('simulada'));
});

for (const status of [502, 503]) test(`HTTP ${status}: no hay conteos inventados ni fallback o repetición`, async () => {
  let calls = 0;
  const s = await loadConsultants('consultorasDemoSuccess', undefined, httpConfig, async () => { calls++; return new Response('[]', { status, headers: { ...officialHeaders, 'X-Senace-Origen': 'NO_DISPONIBLE', 'X-Senace-Estado': status === 502 ? 'FORMATO_INVALIDO' : 'ERROR_EXTERNO', 'X-Senace-Motivo': status === 502 ? 'FORMATO_INVALIDO' : 'ERROR_HTTP' } }); });
  assert.equal(s.http.status, status); assert.equal(s.http.mode, 'http'); assert.equal(s.result.state, 'error'); assert.equal(calls, 1);
  assert.equal(usableRows(s), undefined); assert.equal(consultantKpis(s, select(s)).received, undefined);
  assert.ok(!consultantMessage(s).includes('simulado'));
});

test('HTTP sin headers es degradado, array vacío sin ellos no es cero oficial', async () => {
  for (const body of [[], [httpRow]]) {
    const s = await loadConsultants('consultorasDemoSuccess', undefined, httpConfig, async () => new Response(JSON.stringify(body)));
    assert.equal(s.result.state, 'degraded'); assert.equal(s.result.data.consulta.origen, undefined);
    assert.equal(consultantKpis(s, select(s)).received, body.length ? 1 : undefined);
  }
});

test('HTTP red, HTML y estructura incorrecta no se sustituyen por fixtures', async () => {
  for (const fetcher of [async () => { throw new Error('DETALLE_NO_PUBLICABLE'); }, async () => new Response('<html>FIXTURE</html>'), async () => new Response('{}')]) {
    const s = await loadConsultants('consultorasDemoSuccess', undefined, httpConfig, fetcher);
    assert.equal(s.http.mode, 'http'); assert.equal(s.result.state, 'error'); assert.equal(usableRows(s), undefined);
    assert.ok(!consultantMessage(s).includes('DETALLE_NO_PUBLICABLE'));
  }
});

test('filtros y páginas tras carga HTTP no generan GET adicionales', async () => {
  let calls = 0;
  const s = await loadConsultants('consultorasDemoSuccess', undefined, httpConfig, async () => { calls++; return new Response(JSON.stringify([httpRow, httpRow]), { headers: officialHeaders }); });
  for (let i = 0; i < 10; i++) { const filtered = select(s, { ...emptyFilters, actividad: httpRow.actividad }); paginateConsultants(filtered, i + 1); }
  assert.equal(calls, 1);
});

test('HTTP cancelación y respuesta obsoleta no publican filas de una solicitud antigua', async () => {
  const coordinator = createLatestRequest(); let release;
  const old = coordinator.run(signal => loadConsultants('consultorasDemoSuccess', signal, httpConfig, () => new Promise(resolve => { release = resolve; })));
  const current = coordinator.run(signal => loadConsultants('consultorasDemoSuccess', signal, httpConfig, async () => new Response('[]', { headers: { ...officialHeaders, 'X-Senace-Estado': 'VACIA' } })));
  release(new Response(JSON.stringify([httpRow]), { headers: officialHeaders }));
  assert.equal((await old).state, 'stale'); assert.equal((await current).value.result.state, 'empty');
  const controller = new AbortController(); controller.abort(); let calls = 0;
  const cancelled = await loadConsultants('consultorasDemoSuccess', controller.signal, httpConfig, async () => { calls++; return new Response('[]'); });
  assert.equal(cancelled.http.issue, 'CANCELADA'); assert.equal(calls, 0);
});
