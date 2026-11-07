import test from 'node:test';
import assert from 'node:assert/strict';
import { initialQuery, sectors, years, scenarios, loadDashboardDemo, knownProjects, distribution, selectTable, displayValue } from '../../.api-test-build/features/dashboard/dashboardModel.js';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';
import { adaptDashboard } from '../../.api-test-build/api/adapters/dashboard.js';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';
import { createDashboardSelectionStore } from '../../.api-test-build/api/dashboardSelectionStore.js';
import { parseSourceConfig } from '../../.api-test-build/api/source.js';
import { loadDashboardHttp, stateMessage } from '../../.api-test-build/features/dashboard/dashboardModel.js';

const httpSource = () => parseSourceConfig({ VITE_API_MODE: 'http', VITE_API_BASE_URL: 'http://127.0.0.1:8081', VITE_HTTP_FEATURES: 'monitor,consultoras,dashboard,explorador' });
const artificialHttpBody = state => structuredClone(fixture(`dashboardDemo:Hidrocarburos:2017:${state}`).body);

test('7C3 una consulta HTTP alimenta lecturas compartidas sin otra solicitud ni fixtures geograficos', async () => {
  const store = createDashboardSelectionStore(); let calls = 0;
  assert.equal(store.peek(), undefined);
  const body = artificialHttpBody('success');
  const value = await loadDashboardHttp({ sector: 'Hidrocarburos', anio: 2017 }, httpSource(), undefined,
    async () => { calls++; return new Response(JSON.stringify(body)); }, store);
  assert.equal(value.http.mode, 'http'); assert.equal(value.scenario, undefined);
  assert.equal(value.result.state, 'ready'); assert.ok(value.receivedAt);
  const first = store.peek(); first.query.sector = 'EDICION LOCAL';
  assert.equal(store.peek().query.sector, 'Hidrocarburos');
  for (let n = 0; n < 5; n++) assert.equal(store.peek().result.data.proyectos.length, body.proyectos.length);
  assert.equal(calls, 1);
  assert.ok(knownProjects(value.result).every(row => row.latitud === undefined && row.longitud === undefined));
});

test('7C3 HTTP respeta actividad libre y ano fuera del catalogo de fixtures', async () => {
  const query = { sector: 'Actividad artificial', anio: 2022 };
  const value = await loadDashboardHttp(query, httpSource(), undefined, async url => {
    const parsed = new URL(url); assert.equal(parsed.searchParams.get('sector'), query.sector);
    assert.equal(parsed.searchParams.get('anio'), '2022');
    return new Response(JSON.stringify(artificialHttpBody('empty')));
  }, createDashboardSelectionStore());
  assert.deepEqual(value.query, query);
});

test('7C3 SENACE ausente conserva BCRP y no genera proyectos ni conteos', async () => {
  const value = await loadDashboardHttp(initialQuery, httpSource(), undefined,
    async () => new Response(JSON.stringify(artificialHttpBody('degraded'))), createDashboardSelectionStore());
  assert.equal(value.result.state, 'degraded'); assert.equal(knownProjects(value.result), undefined);
  assert.equal(value.result.data.total, undefined); assert.equal(value.result.data.indicadorBcrp.origen, 'API_DIRECTA_BCRP');
});

test('7C3 SENACE disponible y BCRP ausente conserva proyectos con degradacion', async () => {
  const body = artificialHttpBody('success'); delete body.indicadorBcrp; delete body.variacionSeleccionada;
  body.fuentes.bcrp = 'NO_DISPONIBLE';
  const value = await loadDashboardHttp(initialQuery, httpSource(), undefined,
    async () => new Response(JSON.stringify(body)), createDashboardSelectionStore());
  assert.equal(value.result.state, 'degraded'); assert.equal(knownProjects(value.result).length, body.total);
  assert.equal(value.result.data.indicadorBcrp, undefined);
});

test('7C3 error nuevo sustituye exito anterior sin fallback y un solo intento', async () => {
  const store = createDashboardSelectionStore(); let calls = 0;
  const previous = store.begin(); store.publish(previous, { obsolete: true });
  const value = await loadDashboardHttp(initialQuery, httpSource(), undefined, async () => { calls++; throw new Error('ARTIFICIAL'); }, store);
  assert.equal(calls, 1); assert.equal(value.http.issue, 'RED'); assert.equal(value.http.mode, 'http');
  assert.equal(value.result.state, 'error'); assert.equal(store.peek().obsolete, undefined);
  assert.equal(store.peek().result.state, 'error'); assert.equal(knownProjects(value.result), undefined);
  assert.ok(stateMessage(value.result, 'http').includes('No se activan mocks'));
});

test('7C3 cancelacion y respuesta antigua no sobrescriben la seleccion nueva', async () => {
  const store = createDashboardSelectionStore(); const controller = new AbortController(); let release;
  const old = loadDashboardHttp(initialQuery, httpSource(), controller.signal,
    () => new Promise(resolve => { release = resolve; }), store);
  const rejected = assert.rejects(old, { name: 'AbortError' });
  controller.abort();
  const current = await loadDashboardHttp({ sector: 'TODOS', anio: 2018 }, httpSource(), undefined,
    async () => new Response(JSON.stringify(artificialHttpBody('empty'))), store);
  release(new Response(JSON.stringify(artificialHttpBody('success')))); await rejected;
  assert.deepEqual(store.peek().query, current.query);
  const token = store.begin(); assert.equal(store.peek(), undefined);
  assert.equal(store.publish(token - 1, current), false); assert.equal(store.peek(), undefined);
});

test('7C3 vacio oficial no equivale a fallo JSON y mantiene RUC textual/opcionales', async () => {
  const empty = await loadDashboardHttp(initialQuery, httpSource(), undefined,
    async () => new Response(JSON.stringify(artificialHttpBody('empty'))), createDashboardSelectionStore());
  assert.equal(empty.result.state, 'empty'); assert.deepEqual(knownProjects(empty.result), []);
  const bad = await loadDashboardHttp(initialQuery, httpSource(), undefined,
    async () => new Response('<html>ARTIFICIAL</html>'), createDashboardSelectionStore());
  assert.equal(bad.result.state, 'error'); assert.equal(knownProjects(bad.result), undefined);
  const ok = await loadDashboardHttp(initialQuery, httpSource(), undefined,
    async () => new Response(JSON.stringify(artificialHttpBody('success'))), createDashboardSelectionStore());
  assert.ok(knownProjects(ok.result).every(row => row.ruc === undefined || typeof row.ruc === 'string'));
});

// Fixtures deliberadamente artificiales; ninguna prueba solicita datos oficiales.
function adapted(sector = 'Hidrocarburos', anio = 2017, state = 'success') {
  const item = fixture(`dashboardDemo:${sector}:${anio}:${state}`);
  return adaptDashboard({ mode: 'mock', status: item.status, headers: item.headers ?? {}, json: item.body });
}

test('inicia en Hidrocarburos, año de inicio 2017', () => {
  assert.deepEqual(initialQuery, { sector: 'Hidrocarburos', anio: 2017 });
});

for (const sector of sectors) for (const anio of years) {
  test(`selección artificial coherente: ${sector}/${anio}`, () => {
    const result = adapted(sector, anio);
    assert.equal(result.state, 'ready');
    const rows = knownProjects(result);
    assert.equal(rows.length, sector === 'TODOS' ? 8 : 4);
    assert.equal(result.data.total, rows.length);
    assert.equal(result.data.aprobados, rows.filter((row) => row.estado === 'Aprobado').length);
    assert.ok(rows.every((row) => row.anio === anio));
    assert.equal(result.data.indicadorBcrp.anioSolicitado, anio);
    assert.equal(String(result.data.indicadorBcrp.periodo), String(anio));
    assert.equal(result.data.indicadorBcrp.codigoSerie, sector === 'TODOS' ? 'PM04983AA' : sector === 'Minería' ? 'PM04973AA' : 'PM04974AA');
    assert.equal(distribution(rows).reduce((sum, item) => sum + item.count, 0), rows.length);
  });
}

test('escenarios vacíos distinguen respuesta vacía de filtro sin coincidencias', () => {
  for (const state of ['empty', 'noMatches']) {
    const result = adapted('Hidrocarburos', 2017, state);
    assert.equal(result.state, 'empty');
    assert.deepEqual(knownProjects(result), []);
    assert.deepEqual(distribution(knownProjects(result)), []);
    assert.equal(result.data.total, 0);
  }
  assert.notEqual(adapted('Hidrocarburos', 2017, 'empty').data.consultaSenace.estado,
    adapted('Hidrocarburos', 2017, 'noMatches').data.consultaSenace.estado);
});

test('degradación y configuración ausente conservan BCRP sin inventar conteos SENACE', () => {
  for (const state of ['degraded', 'config']) {
    const result = adapted('Hidrocarburos', 2017, state);
    assert.equal(result.state, 'degraded');
    assert.equal(knownProjects(result), undefined);
    assert.equal(result.data.total, undefined);
    assert.equal(result.data.aprobados, undefined);
    assert.equal(result.data.indicadorBcrp.origen, 'API_DIRECTA_BCRP');
    assert.equal(result.data.variacionSeleccionada, result.data.indicadorBcrp.valor);
  }
});

test('fechas no evaluables conservan metadatos; opcionales y errores no son ceros', () => {
  const dates = adapted('Hidrocarburos', 2017, 'dates');
  assert.equal(dates.state, 'degraded');
  assert.equal(dates.data.consultaSenace.conteos.fechasNoEvaluables, 1);
  assert.ok(knownProjects(dates).length > 0);
  assert.equal(knownProjects(adapted('Hidrocarburos', 2017, 'optional')), undefined);
  assert.equal(adapted('Hidrocarburos', 2017, 'error').state, 'error');
  assert.equal(displayValue(undefined), 'No disponible');
  assert.equal(displayValue(0), '0');
});

test('distribución no deduplica ni fabrica estados ausentes', () => {
  const row = { estado: 'Aprobado' };
  assert.deepEqual(distribution([row, row, {}]), [
    { estado: 'Aprobado', count: 2 }, { estado: 'Estado no disponible', count: 1 },
  ]);
  assert.equal(distribution(undefined), undefined);
});

test('búsqueda normalizada, RUC textual y paginación sobre filas cargadas', () => {
  const rows = knownProjects(adapted('TODOS'));
  assert.equal(selectTable(rows, '', 1).rows.length, 3);
  assert.equal(selectTable(rows, '', 99).page, 3);
  assert.equal(selectTable(rows, '', 3).rows.length, 2);
  assert.equal(selectTable(rows, 'mineria', 1).total, 4);
  const withRuc = rows.find((row) => row.ruc !== undefined);
  assert.equal(typeof withRuc.ruc, 'string');
  assert.ok(withRuc.ruc.startsWith('0'));
  assert.ok(selectTable(rows, withRuc.ruc, 1).total >= 1);
  assert.equal(selectTable(rows, 'sin coincidencias artificiales', 9).total, 0);
  assert.equal(selectTable(undefined, '', 1).total, undefined);
  assert.throws(() => selectTable(rows, '', 1, 0), /TAMANO_PAGINA_INVALIDO/);
});

test('carga de demostración no utiliza fetch global y conserva selección aplicada', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('HTTP_REAL_PROHIBIDO'); };
  try {
    const snapshot = await loadDashboardDemo({ sector: 'Minería', anio: 2018 }, 'success');
    assert.equal(snapshot.http.mode, 'mock');
    assert.deepEqual(snapshot.query, { sector: 'Minería', anio: 2018 });
    assert.equal(snapshot.result.state, 'ready');
  } finally { globalThis.fetch = original; }
});

test('coordinador descarta filtros obsoletos y admite cancelación', async () => {
  const coordinator = createLatestRequest();
  const old = coordinator.run((signal) => loadDashboardDemo({ sector: 'TODOS', anio: 2017 }, 'success', signal));
  const current = coordinator.run((signal) => loadDashboardDemo({ sector: 'Minería', anio: 2018 }, 'success', signal));
  assert.equal((await old).state, 'stale');
  const completed = await current;
  assert.equal(completed.state, 'current');
  assert.equal(completed.value.query.anio, 2018);
  const cancelled = coordinator.run((signal) => loadDashboardDemo(initialQuery, 'success', signal));
  coordinator.cancel();
  assert.ok(['cancelled', 'stale'].includes((await cancelled).state));
});

test('todos los escenarios tienen fixtures separados sin reemplazar los históricos', () => {
  for (const sector of sectors) for (const year of years) for (const state of scenarios) {
    assert.ok(fixture(`dashboardDemo:${sector}:${year}:${state.value}`));
  }
  assert.ok(fixture('dashboardSuccess'));
});
