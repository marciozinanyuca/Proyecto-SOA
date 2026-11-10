import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoSelectionStore } from '../../.api-test-build/api/demoSelectionStore.js';
import { coordinateState, point, projectEntries, selectEntry, loadExplorer, previousSelection, createExplorerIndex, filterIndexedExplorer, explorerSpatial, paginateExplorer, pagePointGroups, EXPLORER_PAGE_SIZE } from '../../.api-test-build/features/explorador/exploradorModel.js';
import { dashboardHttpStore, loadDashboardDemo, loadDashboardHttp } from '../../.api-test-build/features/dashboard/dashboardModel.js';
import { previousHttpSelection } from '../../.api-test-build/features/explorador/exploradorModel.js';
import { parseSourceConfig } from '../../.api-test-build/api/source.js';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';

function artificialSnapshot(rows) {
  return { query: { sector: 'TODOS', anio: 2017 }, http: { mode: 'http', status: 200, headers: {} },
    result: { state: 'degraded', warnings: [], data: { proyectos: rows, consultaSenace: {
      origen: 'API_DIRECTA_SENACE', estado: rows.length ? 'EXITO' : 'VACIA', cuerpoCompleto: true, cobertura: 'NO_VERIFICADA' } } } };
}

test('7E indice conserva orden, RUC, opcionales y duplicados con claves tecnicas distintas', () => {
  const row = { id: 1, ruc: '00000000000', titulo: 'ARTIFICIAL', actividad: 'Minería', estado: 'Aprobado' };
  const index = createExplorerIndex(artificialSnapshot([row, row, {}]));
  assert.equal(index.entries.length, 3); assert.deepEqual(index.entries.map(entry => entry.key), ['0', '1', '2']);
  assert.equal(index.entries[0].row.ruc, '00000000000'); assert.equal(index.entries[2].row.latitud, undefined);
  assert.equal(filterIndexedExplorer(index, '', 'mineria').length, 2);
  assert.equal(filterIndexedExplorer(index, 'Aprobado').length, 2);
  assert.equal(filterIndexedExplorer(index, '', '00000000000').length, 2);
  assert.equal(filterIndexedExplorer(index, '', 'NO_EXISTE').length, 0);
});

for (const size of [1000, 5000, 20000]) test(`7E ${size} registros accesibles en paginas sin perdida y puntos acotados`, () => {
  const rows = Array.from({ length: size }, (_, i) => ({ id: Math.floor(i / 2), titulo: `ARTIFICIAL ${i}`, estado: i % 2 ? 'Otros' : 'Aprobado',
    ...(i % 3 === 0 ? {} : i % 3 === 1 ? { latitud: -12, longitud: -70 } : { latitud: -10 + i / 100000, longitud: -75 + i / 100000 }) }));
  const index = createExplorerIndex(artificialSnapshot(rows));
  const entries = filterIndexedExplorer(index); const spatial = explorerSpatial(index, entries);
  const seen = new Set();
  for (let page = 1; page <= Math.ceil(size / EXPLORER_PAGE_SIZE); page++) {
    const slice = paginateExplorer(entries, page);
    assert.ok(slice.rows.length <= 20);
    for (const entry of slice.rows) { assert.equal(seen.has(entry.key), false); seen.add(entry.key); }
    const markers = pagePointGroups(index, spatial, slice.rows, undefined);
    assert.ok(markers.length <= 20);
    assert.equal(new Set(markers.map(group => group.key)).size, markers.length);
  }
  assert.equal(seen.size, size);
  assert.equal(spatial.coordinateCount, rows.filter(row => row.latitud !== undefined).length);
  assert.equal(filterIndexedExplorer(index, '', `ARTIFICIAL ${size - 1}`).length, 1);
  assert.equal(paginateExplorer(entries, Number.MAX_SAFE_INTEGER).end, size);
});

test('7E seleccion fuera de pagina conserva ficha y añade a lo sumo un punto', () => {
  const rows = Array.from({ length: 45 }, (_, i) => ({ id: 7, titulo: `ARTIFICIAL ${i}`, latitud: -10 + i / 1000, longitud: -70 + i / 1000 }));
  const index = createExplorerIndex(artificialSnapshot(rows)); const entries = filterIndexedExplorer(index);
  const spatial = explorerSpatial(index, entries); const first = paginateExplorer(entries, 1);
  const markers = pagePointGroups(index, spatial, first.rows, '44');
  assert.equal(markers.length, 21); assert.equal(spatial.byKey.get('44').row.titulo, 'ARTIFICIAL 44');
  assert.equal(markers.at(-1).entries[0].key, '44');
  assert.equal(pagePointGroups(index, spatial, first.rows, '0').length, 20);
  const filtered = filterIndexedExplorer(index, '', 'ARTIFICIAL 0');
  const narrowed = explorerSpatial(index, filtered);
  assert.equal(narrowed.byKey.has('44'), false);
  assert.equal(pagePointGroups(index, narrowed, filtered, '44').length, 1);
});

test('7E coincidencias exactas comparten punto sin promediar, redondear o deduplicar filas', () => {
  const repeated = { latitud: -12.123456789, longitud: -70.123456789, titulo: 'ARTIFICIAL' };
  const rows = Array.from({ length: 51 }, () => ({ ...repeated }));
  rows.push({ ...repeated, longitud: -70.123456788 });
  const index = createExplorerIndex(artificialSnapshot(rows)); const entries = filterIndexedExplorer(index);
  const spatial = explorerSpatial(index, entries);
  assert.equal(spatial.groups.size, 2); assert.equal(spatial.coordinateCount, 52);
  const markers = pagePointGroups(index, spatial, paginateExplorer(entries, 1).rows, undefined);
  assert.equal(markers.length, 1); assert.equal(markers[0].entries.length, 51);
  assert.deepEqual(markers[0].pos, point(repeated));
  const coincidentRows = markers[0].entries;
  assert.equal(paginateExplorer(coincidentRows, 1).rows.length, 20);
  assert.equal(paginateExplorer(coincidentRows, 3).rows.length, 11);
  assert.equal(paginateExplorer(coincidentRows, 3).rows.at(-1).key, '50');
  assert.equal(spatial.byKey.get('50').row.longitud, repeated.longitud);
});

test('7E sin coordenadas conserva lista y distingue desconocido de vacio oficial', () => {
  const unknown = createExplorerIndex(undefined);
  assert.equal(unknown, undefined); assert.equal(filterIndexedExplorer(unknown), undefined);
  assert.equal(paginateExplorer(undefined, 1).rows, undefined);
  assert.equal(pagePointGroups(undefined, undefined, undefined, undefined), undefined);
  const empty = createExplorerIndex(artificialSnapshot([]));
  assert.deepEqual(paginateExplorer(empty.entries, 1).rows, []);
  const index = createExplorerIndex(artificialSnapshot([{ titulo: 'ARTIFICIAL' }, { latitud: 95, longitud: 0 }]));
  const spatial = explorerSpatial(index, index.entries);
  assert.equal(spatial.coordinateCount, 0); assert.equal(index.entries.length, 2);
  assert.deepEqual(pagePointGroups(index, spatial, index.entries, undefined), []);
  assert.deepEqual(spatial.bounds, { x: 0, y: 0, width: 100, height: 100 });
});

test('7E limites de encuadre corresponden a toda la seleccion y paginar no los altera', () => {
  const rows = Array.from({ length: 30 }, (_, i) => ({ latitud: -10 + i / 100, longitud: -75 + i / 100 }));
  const index = createExplorerIndex(artificialSnapshot(rows)); const spatial = explorerSpatial(index, index.entries);
  const before = structuredClone(spatial.bounds);
  pagePointGroups(index, spatial, paginateExplorer(index.entries, 1).rows, '29');
  pagePointGroups(index, spatial, paginateExplorer(index.entries, 2).rows, '0');
  assert.deepEqual(spatial.bounds, before);
  assert.equal(paginateExplorer(index.entries, -3).page, 1);
  assert.equal(paginateExplorer(index.entries, NaN).page, 1);
  assert.equal(paginateExplorer(index.entries, 100).page, 2);
});

test('7C3 Explorador HTTP lee ultima respuesta sin llamadas, sin coordenadas prestadas del mock', async () => {
  const source = parseSourceConfig({ VITE_API_MODE: 'http', VITE_API_BASE_URL: 'http://127.0.0.1:8081', VITE_HTTP_FEATURES: 'monitor,consultoras,dashboard,explorador' });
  dashboardHttpStore.begin(); assert.equal(previousHttpSelection(), undefined);
  let calls = 0;
  const body = structuredClone(fixture('dashboardDemo:Hidrocarburos:2017:success').body);
  const value = await loadDashboardHttp({ sector: 'Hidrocarburos', anio: 2017 }, source, undefined,
    async () => { calls++; return new Response(JSON.stringify(body)); });
  for (let n = 0; n < 4; n++) {
    const selection = previousHttpSelection();
    assert.deepEqual(selection.query, value.query);
    const entries = projectEntries(selection);
    assert.equal(entries.length, body.proyectos.length);
    assert.ok(entries.every(entry => point(entry.row) === undefined));
    selectEntry(entries, entries[0]?.key); projectEntries(selection, 'Aprobado');
  }
  assert.equal(calls, 1);
  const copy = previousHttpSelection(); copy.query.anio = 1999;
  assert.equal(previousHttpSelection().query.anio, 2017);
});

test('coordenadas: ausencia, incompletas, no finitas y rangos; no se sustituyen por cero', () => {
  for (const row of [{}, { latitud: -12 }, { longitud: -75 }]) {
    assert.equal(coordinateState(row), 'AUSENTES_O_INCOMPLETAS'); assert.equal(point(row), undefined);
  }
  for (const row of [{ latitud: 91, longitud: -75 }, { latitud: -12, longitud: 181 }, { latitud: NaN, longitud: -75 }, { latitud: 0, longitud: Infinity }]) {
    assert.equal(coordinateState(row), 'FUERA_DE_RANGO'); assert.equal(point(row), undefined);
  }
  assert.equal(coordinateState({ latitud: 0, longitud: 0 }), 'UTILIZABLES');
});

test('plano conserva orden longitud horizontal/latitud vertical sin intercambiar ejes', () => {
  assert.deepEqual(point({ longitud: 0, latitud: 0 }), { x: 50, y: 50 });
  assert.deepEqual(point({ longitud: -180, latitud: 90 }), { x: 0, y: 0 });
  assert.deepEqual(point({ longitud: 180, latitud: -90 }), { x: 100, y: 100 });
  const original = { longitud: -74.912345678, latitud: -8.541234567 };
  const before = structuredClone(original); point(original); assert.deepEqual(original, before);
});

test('almacén comparte carga pendiente, conserva copias y evita GET duplicados', async () => {
  const store = createDemoSelectionStore(); let calls = 0; let release;
  const loader = () => { calls++; return new Promise(resolve => { release = resolve; }); };
  const first = store.get('selection', loader); const second = store.get('selection', loader);
  await Promise.resolve(); release({ rows: [1] });
  const [a, b] = await Promise.all([first, second]); a.rows.push(2);
  assert.deepEqual(b, { rows: [1] });
  assert.deepEqual(await store.get('selection', loader), { rows: [1] }); assert.equal(calls, 1);
});

test('cancelar un consumidor no cancela la carga de otro consumidor', async () => {
  const store = createDemoSelectionStore(); const controller = new AbortController(); let release;
  const loader = () => new Promise(resolve => { release = resolve; });
  const first = store.get('same', loader, controller.signal); const second = store.get('same', loader);
  const rejected = assert.rejects(first, { name: 'AbortError' });
  await Promise.resolve(); controller.abort(); release('available');
  await rejected; assert.equal(await second, 'available');
});

test('almacén no conserva rechazos y rechaza cancelación previa sin cargar', async () => {
  const store = createDemoSelectionStore(); let calls = 0;
  const loader = async () => { calls++; throw new Error('ARTIFICIAL'); };
  await assert.rejects(store.get('error', loader)); await assert.rejects(store.get('error', loader)); assert.equal(calls, 2);
  const aborted = new AbortController(); aborted.abort();
  await assert.rejects(store.get('new', loader, aborted.signal), { name: 'AbortError' }); assert.equal(calls, 2);
});

test('fixtures geográficos separados, filtro local, marcadores y ficha seleccionan la misma fila', async () => {
  const snapshot = await loadExplorer({ sector: 'Hidrocarburos', anio: 2017 }, 'coordinates');
  assert.equal(snapshot.http.mode, 'mock'); assert.equal(snapshot.result.state, 'ready');
  const entries = projectEntries(snapshot); assert.equal(entries.length, 4);
  assert.equal(entries.filter(entry => point(entry.row)).length, 2);
  assert.equal(entries.filter(entry => coordinateState(entry.row) === 'AUSENTES_O_INCOMPLETAS').length, 1);
  assert.equal(entries.filter(entry => coordinateState(entry.row) === 'FUERA_DE_RANGO').length, 1);
  const approved = projectEntries(snapshot, 'Aprobado'); assert.equal(approved.length, 1);
  assert.equal(selectEntry(entries, approved[0].key), entries[0]);
  assert.equal(selectEntry(projectEntries(snapshot, 'Otros'), approved[0].key), undefined);
  assert.equal(projectEntries(snapshot, '', 'NO_EXISTE').length, 0);
  assert.equal(projectEntries(snapshot, '', entries[0].row.ruc).length, 1);
  assert.equal(typeof entries[0].row.ruc, 'string');
  assert.equal(entries[0].row.latitud, -8.541234567);
  for (const { row } of entries) for (const absent of ['region', 'expediente', 'elevacion', 'inversion', 'urlDocumento']) assert.equal(row[absent], undefined);
});

test('datos reutilizados del Dashboard no reciben coordenadas artificiales implícitas', async () => {
  const query = { sector: 'Minería', anio: 2018 };
  const dashboard = await loadDashboardDemo(query, 'success');
  assert.deepEqual(previousSelection().query, query);
  const explorer = await loadExplorer(query, 'success'); assert.deepEqual(explorer, dashboard);
  assert.ok(projectEntries(explorer).every(entry => point(entry.row) === undefined));
});

test('conserva filas repetidas y opcionales sin identificadores técnicos públicos inventados', () => {
  const row = { titulo: 'ARTIFICIAL' };
  const snapshot = { result: { state: 'ready', warnings: [], data: { proyectos: [row, row], consultaSenace: { origen: 'API_DIRECTA_SENACE', estado: 'EXITO', cuerpoCompleto: true } } } };
  const entries = projectEntries(snapshot); assert.equal(entries.length, 2);
  assert.notEqual(entries[0].key, entries[1].key); assert.equal(entries[0].row.id, undefined);
});

test('vacío oficial y filtro sin coincidencias son distintos de datos desconocidos', async () => {
  for (const state of ['empty', 'noMatches']) {
    const snapshot = await loadExplorer({ sector: 'TODOS', anio: 2017 }, state);
    assert.deepEqual(projectEntries(snapshot), []);
  }
  for (const state of ['degraded', 'config', 'optional', 'error']) {
    const snapshot = await loadExplorer({ sector: 'TODOS', anio: 2017 }, state);
    assert.equal(projectEntries(snapshot), undefined);
  }
});

test('modo offline/mock nunca invoca fetch global, tampoco por filtros o selección', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = () => { calls++; throw new Error('RED_PROHIBIDA'); };
  try {
    const snapshot = await loadExplorer({ sector: 'TODOS', anio: 2018 }, 'coordinates');
    for (let i = 0; i < 5; i++) { const entries = projectEntries(snapshot, 'Aprobado'); selectEntry(entries, entries[0]?.key); }
    assert.equal(calls, 0); assert.equal(snapshot.http.mode, 'mock');
  } finally { globalThis.fetch = original; }
});
