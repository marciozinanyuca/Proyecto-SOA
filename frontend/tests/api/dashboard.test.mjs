import test from 'node:test';
import assert from 'node:assert/strict';
import { initialQuery, sectors, years, scenarios, loadDashboardDemo, knownProjects, distribution, selectTable, displayValue } from '../../.api-test-build/features/dashboard/dashboardModel.js';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';
import { adaptDashboard } from '../../.api-test-build/api/adapters/dashboard.js';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';

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
