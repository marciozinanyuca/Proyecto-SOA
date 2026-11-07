import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoSelectionStore } from '../../.api-test-build/api/demoSelectionStore.js';
import { coordinateState, point, projectEntries, selectEntry, loadExplorer, previousSelection } from '../../.api-test-build/features/explorador/exploradorModel.js';
import { loadDashboardDemo } from '../../.api-test-build/features/dashboard/dashboardModel.js';

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
