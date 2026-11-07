import test from 'node:test';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { parseSourceConfig } from '../../.api-test-build/api/source.js';
import { createDashboardSelectionStore } from '../../.api-test-build/api/dashboardSelectionStore.js';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';
import { loadDashboardHttp, knownProjects, distribution, selectTable } from '../../.api-test-build/features/dashboard/dashboardModel.js';
import { projectEntries, point } from '../../.api-test-build/features/explorador/exploradorModel.js';
import { loadConsultants, filterConsultants, filterOptions, paginateConsultants, consultantKpis, emptyFilters } from '../../.api-test-build/features/consultoras/consultorasModel.js';
import { loadMonitor, metricsFor } from '../../.api-test-build/features/monitor/monitorModel.js';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';

// Todos los registros y metadatos de este archivo son ARTIFICIALES.
// Ningun fetch se delega a la red; no requiere backend ni credenciales.
const config = () => parseSourceConfig({ VITE_API_MODE: 'http', VITE_API_BASE_URL: 'http://127.0.0.1:8081', VITE_HTTP_FEATURES: 'monitor,consultoras,dashboard,explorador' });
const headers = { 'Content-Type': 'application/json', 'X-Senace-Origen': 'API_DIRECTA_SENACE', 'X-Senace-Estado': 'EXITO', 'X-Senace-Cobertura': 'NO_VERIFICADA', 'X-Senace-Cuerpo-Completo': 'true' };
const response = (body, status = 200, meta = headers) => new Response(JSON.stringify(body), { status, headers: meta });
function artificialConsultants(size) {
  return Array.from({ length: size }, (_, index) => {
    const pair = Math.floor(index / 2);
    return { razonSocial: `CONSULTORA ARTIFICIAL ${pair}`, actividad: `ACTIVIDAD ARTIFICIAL ${pair % 4}`,
      subsector: `SUBSECTOR ARTIFICIAL ${pair % 3}`, tipoSolicitud: `SOLICITUD ARTIFICIAL ${pair % 2}`,
      ...(pair % 5 === 0 ? {} : { resolucion: 'RESOLUCION ARTIFICIAL' }),
      fechaRd: 'TEXTO ORIGINAL ARTIFICIAL', inicioVigenciaRd: '', finVigenciaRd: 'FECHA NO INTERPRETADA' };
  });
}

for (const size of [15000, 20000]) test(`7D Consultoras ${size} filas artificiales: orden, duplicados, filtros y paginacion limitada`, async t => {
  const heapBefore = process.memoryUsage().heapUsed;
  const started = performance.now();
  const body = artificialConsultants(size); let calls = 0;
  const snapshot = await loadConsultants('consultorasDemoSuccess', undefined, config(), async () => { calls++; return response(body); });
  const loadMs = performance.now() - started;
  assert.equal(snapshot.result.state, 'ready');
  const rows = snapshot.result.data.rows;
  assert.equal(rows.length, size); assert.deepEqual(rows[0], rows[1]);
  assert.equal(filterOptions(rows, 'actividad').length, 4);
  assert.equal(filterOptions(rows, 'subsector').length, 3);
  const entries = filterConsultants(rows, emptyFilters);
  const kpis = consultantKpis(snapshot, entries);
  assert.equal(kpis.received, size); assert.equal(kpis.filtered, size);
  assert.equal(kpis.withResolution, size * 4 / 5);
  const last = paginateConsultants(entries, Number.MAX_SAFE_INTEGER);
  assert.equal(last.rows.length, 10); assert.equal(last.end, size);
  assert.equal(last.rows[9].key, String(size - 1));
  assert.equal(paginateConsultants(entries, 1).rows.length, 10);
  assert.notEqual(entries[0].key, entries[1].key);
  const elapsed = [];
  for (let run = 0; run < 5; run++) {
    const begin = performance.now();
    const match = filterConsultants(rows, { ...emptyFilters, search: 'CONSULTORA ARTIFICIAL 0', actividad: 'ACTIVIDAD ARTIFICIAL 0', subsector: 'SUBSECTOR ARTIFICIAL 0', tipoSolicitud: 'SOLICITUD ARTIFICIAL 0' });
    assert.equal(match.length, 2); // Primer par conserva las dos filas.
    assert.equal(paginateConsultants(match, 1).rows.length, 2);
    assert.equal(filterConsultants(rows, { ...emptyFilters, search: 'ZZZ_SIN_COINCIDENCIAS_ARTIFICIALES' }).length, 0);
    elapsed.push(performance.now() - begin);
  }
  assert.equal(calls, 1);
  t.diagnostic(JSON.stringify({ artificial: true, size, loadAndAdaptMs: +loadMs.toFixed(2), filterPairRunsMs: elapsed.map(ms => +ms.toFixed(2)), heapDeltaBytes: process.memoryUsage().heapUsed - heapBefore,
    limitation: 'Node; incluye aserciones y asignaciones; heap sin GC forzado. No mide renderizado DOM ni garantiza SLA.' }));
});

test('7D Dashboard extenso conserva opcionales, duplicados y puntos coincidentes sin inventar posiciones', async t => {
  const started = performance.now();
  const body = structuredClone(fixture('dashboardSuccess').body);
  const project = { titulo: 'PROYECTO ARTIFICIAL', estado: 'Aprobado', ruc: '00000000000', latitud: -12.123456789, longitud: -70.123456789 };
  body.proyectos = Array.from({ length: 20000 }, (_, index) => index % 2 === 0 ? { ...project } : { titulo: 'PROYECTO ARTIFICIAL OPCIONAL', estado: 'Otros' });
  body.total = 20000; body.aprobados = 10000;
  body.consultaSenace.conteos = { recibidos: 20000, aceptados: 20000, devueltos: 20000, aprobados: 10000 };
  const snapshot = await loadDashboardHttp({ sector: 'TODOS', anio: 2017 }, config(), undefined, async () => response(body), createDashboardSelectionStore());
  assert.equal(snapshot.result.state, 'ready');
  const rows = knownProjects(snapshot.result);
  assert.equal(rows.length, 20000); assert.equal(distribution(rows).reduce((sum, item) => sum + item.count, 0), 20000);
  assert.equal(selectTable(rows, '', 1).rows.length, 3);
  const entries = projectEntries(snapshot);
  assert.equal(entries.length, 20000);
  const points = entries.flatMap(entry => point(entry.row) ? [point(entry.row)] : []);
  assert.equal(points.length, 10000);
  assert.equal(new Set(points.map(pos => `${pos.x},${pos.y}`)).size, 1);
  assert.equal(projectEntries(snapshot, 'Aprobado').length, 10000);
  assert.equal(rows[1].latitud, undefined); assert.equal(rows[1].ruc, undefined);
  t.diagnostic(JSON.stringify({ artificial: true, rows: rows.length, coincidentPoints: points.length, purePipelineMs: +(performance.now() - started).toFixed(2), limitation: 'No mide montaje React ni rendimiento de SVG.' }));
});

test('7D snapshot HTTP: lectura no consulta, nueva seleccion invalida la anterior y una sesion nueva esta vacia', async () => {
  const store = createDashboardSelectionStore(); let calls = 0;
  const fetcher = async () => { calls++; return response(fixture('dashboardSuccess').body); };
  assert.equal(store.peek(), undefined);
  const snapshot = await loadDashboardHttp({ sector: 'Hidrocarburos', anio: 2017 }, config(), undefined, fetcher, store);
  for (let read = 0; read < 8; read++) {
    const copy = store.peek(); projectEntries(copy); selectTable(knownProjects(copy.result), '', 1);
    assert.deepEqual(copy.query, snapshot.query);
  }
  assert.equal(calls, 1);
  const ticket = store.begin(); assert.equal(store.peek(), undefined);
  assert.equal(store.publish(ticket - 1, snapshot), false);
  assert.equal(createDashboardSelectionStore().peek(), undefined);
});

test('7D operaciones aisladas conservan errores y metadatos sin fallback ni solicitudes adicionales', async () => {
  const paths = [];
  const fetcher = async url => {
    const path = new URL(url).pathname; paths.push(path);
    if (path === '/api/consultoras') return response([], 503, { ...headers, 'X-Senace-Origen': 'NO_DISPONIBLE', 'X-Senace-Estado': 'ERROR_EXTERNO', 'X-Senace-Motivo': 'CONFIGURACION_AUSENTE', 'X-Senace-Cuerpo-Completo': 'false' });
    if (path === '/api/estado-servicios') return response(fixture('monitor').body);
    throw new Error('RUTA NO ESPERADA ARTIFICIAL');
  };
  const consultoras = await loadConsultants('consultorasDemoSuccess', undefined, config(), fetcher);
  assert.equal(consultoras.http.mode, 'http'); assert.equal(consultoras.http.status, 503);
  assert.equal(consultoras.result.state, 'error');
  assert.equal(consultantKpis(consultoras, undefined).received, undefined);
  const monitor = await loadMonitor('monitor', undefined, config(), fetcher);
  assert.equal(metricsFor(monitor, 'senace').disponibilidadExterna, 'NO_VERIFICADA');
  assert.equal(metricsFor(monitor, 'senace').respaldoLocal, 'NO_IMPLEMENTADO');
  assert.deepEqual(paths, ['/api/consultoras', '/api/estado-servicios']);
});

test('7D coordinador descarta respuesta obsoleta y una cancelada sin publicar datos', async () => {
  const coordinator = createLatestRequest(); let release;
  const old = coordinator.run(() => new Promise(resolve => { release = resolve; }));
  const current = await coordinator.run(async () => ({ artificial: true }));
  release({ obsolete: true }); assert.equal((await old).state, 'stale');
  assert.equal(current.state, 'current'); assert.deepEqual(current.value, { artificial: true });
  const cancelled = coordinator.run(() => new Promise(resolve => { release = resolve; }));
  coordinator.cancel(); release({ cancelled: true });
  assert.equal((await cancelled).state, 'cancelled');
});
