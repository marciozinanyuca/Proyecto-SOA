import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';
import { createMockFetch } from '../../.api-test-build/mocks/mockFetch.js';
import { createApiClient } from '../../.api-test-build/api/client.js';
import { adaptDashboard } from '../../.api-test-build/api/adapters/dashboard.js';
import { adaptConsultoras } from '../../.api-test-build/api/adapters/consultoras.js';
import { adaptMonitor } from '../../.api-test-build/api/adapters/monitor.js';
import { official } from '../../.api-test-build/api/validation.js';
import { lkgMetadata, usableSenace, usableBcrp, usableBackup, captureLabel, ageLabel, originLabel, safeLkgReason } from '../../.api-test-build/api/lkg.js';
import { dashboardBackupWarnings, knownProjects, distribution, loadDashboardHttp } from '../../.api-test-build/features/dashboard/dashboardModel.js';
import { createExplorerIndex, explorerSpatial, paginateExplorer, pagePointGroups } from '../../.api-test-build/features/explorador/exploradorModel.js';
import { usableRows, consultantKpis, filterConsultants, emptyFilters } from '../../.api-test-build/features/consultoras/consultorasModel.js';
import { dashboardDetailsForMonitor, lkgStateLabel, lkgExplanation, snapshotResourceLabel, metricsFor } from '../../.api-test-build/features/monitor/monitorModel.js';
import { createDashboardSelectionStore } from '../../.api-test-build/api/dashboardSelectionStore.js';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';
import { parseSourceConfig } from '../../.api-test-build/api/source.js';

// Todos los valores y respuestas son ARTIFICIALES. Ningun fetch se delega a la red.
const key = state => `dashboardDemo:Hidrocarburos:2017:${state}`;
function response(name) {
  const value = fixture(name);
  return { mode: 'mock', status: value.status, json: value.body,
    headers: Object.fromEntries(new Headers(value.headers).entries()) };
}
function dashboard(state) { return response(key(state)); }

test('Dashboard conserva avisos por fuente y antiguedad aunque los detalles esten en Monitor', () => {
  assert.deepEqual(dashboardBackupWarnings(adaptDashboard(dashboard('success')).data), []);
  const both = dashboardBackupWarnings(adaptDashboard(dashboard('lkgBoth')).data);
  assert.equal(both.length, 2);
  assert.ok(both[0].startsWith('SENACE: respaldo local'));
  assert.ok(both[1].startsWith('BCRP: respaldo local'));
  const old = dashboardBackupWarnings(adaptDashboard(dashboard('lkgOld')).data);
  assert.ok(old.every(value => value.includes('respaldo antiguo') && value.includes('UTC')));
  assert.ok(dashboardBackupWarnings(adaptDashboard(dashboard('lkgWriteFailure')).data)
    .some(value => value.includes('no se pudo actualizar')));
  assert.deepEqual(dashboardBackupWarnings(undefined), []);
});

test('Monitor recibe solo metadatos de la consulta Dashboard y no reutiliza un error como exito', () => {
  const http = dashboard('lkgBoth');
  const sample = { query: { sector: 'Hidrocarburos', anio: 2017 }, http,
    result: adaptDashboard(http), receivedAt: '2026-11-12T12:00:00Z' };
  const details = dashboardDetailsForMonitor(sample);
  assert.equal(details.senace.origin, 'RESPALDO_LOCAL');
  assert.equal(details.bcrp.origin, 'RESPALDO_LOCAL');
  assert.equal(details.senace.metadata.respaldo.capturadoEn, sample.result.data.consultaSenace.lkg.respaldo.capturadoEn);
  assert.equal(details.receivedAt, sample.receivedAt);
  assert.deepEqual(details.query, sample.query);
  assert.equal(details.mode, 'mock');
  assert.equal('proyectos' in details, false);
  assert.equal('http' in details, false);
  assert.equal(dashboardDetailsForMonitor(undefined), undefined);
  assert.equal(dashboardDetailsForMonitor({ ...sample, result: { state: 'error' } }), undefined);
});
const config = parseSourceConfig({ VITE_API_MODE: 'http', VITE_API_BASE_URL: 'http://127.0.0.1:8081', VITE_HTTP_FEATURES: 'monitor,consultoras,dashboard,explorador' });

test('contratos anteriores siguen disponibles sin metadata LKG', () => {
  assert.equal(adaptDashboard(response('dashboardSuccess')).state, 'ready');
  assert.equal(adaptConsultoras(response('consultantsSuccess')).state, 'ready');
  assert.equal(adaptMonitor(response('monitor')).state, 'ready');
  assert.equal(adaptDashboard(response('dashboardSuccess')).data.consultaSenace.lkg, undefined);
});

for (const [state, senace, bcrp] of [
  ['lkgSenace', 'RESPALDO_LOCAL', 'API_DIRECTA_BCRP'], ['lkgBcrp', 'API_DIRECTA_SENACE', 'RESPALDO_LOCAL'],
  ['lkgBoth', 'RESPALDO_LOCAL', 'RESPALDO_LOCAL'], ['lkgOld', 'RESPALDO_LOCAL', 'RESPALDO_LOCAL'],
]) test(`dashboard ${state}: usable con advertencia y procedencia independiente`, () => {
  const adapted = adaptDashboard(dashboard(state));
  assert.equal(adapted.state, 'degraded');
  assert.equal(adapted.data.fuentes.senace, senace); assert.equal(adapted.data.fuentes.bcrp, bcrp);
  assert.ok(knownProjects(adapted).length > 0); assert.ok(usableBcrp(adapted.data.indicadorBcrp));
  assert.equal(adapted.data.indicadorBcrp.valor, 1.25);
  assert.equal(distribution(knownProjects(adapted)).reduce((sum, row) => sum + row.count, 0), adapted.data.total);
  if (senace === 'RESPALDO_LOCAL') {
    assert.equal(official(adapted.data.consultaSenace), false);
    assert.equal(usableSenace(adapted.data.consultaSenace), true);
    assert.equal(adapted.data.consultaSenace.lkg.intentoApi.estado, 'ERROR_EXTERNO');
    assert.ok(adapted.data.proyectos.every(row => row.origen === senace));
  }
  if (state === 'lkgOld') assert.ok(adapted.warnings.includes('SENACE_RESPALDO_ANTIGUO'));
});

test('LKG directo con escritura SAVED no se confunde con respaldo recuperado', () => {
  const r = dashboard('lkgBoth');
  const meta = r.json.consultaSenace.lkg;
  meta.intentoApi = { estado: 'EXITO', cuerpoCompleto: true };
  delete meta.lectura; meta.escritura = 'SAVED';
  r.json.consultaSenace.origen = r.json.fuentes.senace = 'API_DIRECTA_SENACE';
  r.json.proyectos.forEach(row => { row.origen = 'API_DIRECTA_SENACE'; });
  r.json.indicadorBcrp.origen = r.json.fuentes.bcrp = 'API_DIRECTA_BCRP';
  r.json.indicadorBcrp.lkg = structuredClone(meta);
  assert.equal(adaptDashboard(r).state, 'ready');
});

test('sin respaldo no inventa lista ni ceros oficiales', () => {
  const adapted = adaptDashboard(dashboard('lkgMissing'));
  assert.equal(adapted.state, 'degraded'); assert.equal(knownProjects(adapted), undefined);
  assert.equal(adapted.data.total, undefined); assert.equal(adapted.data.indicadorBcrp.valor, undefined);
  const wrong = dashboard('lkgMissing'); wrong.json.total = 0;
  assert.equal(adaptDashboard(wrong).state, 'error');
});

test('fallo al guardar no descarta la respuesta directa ni inventa captura persistida', () => {
  const adapted = adaptDashboard(dashboard('lkgWriteFailure'));
  assert.equal(adapted.state, 'degraded'); assert.ok(knownProjects(adapted).length);
  assert.ok(usableBcrp(adapted.data.indicadorBcrp));
  assert.equal(adapted.data.consultaSenace.lkg.respaldo, undefined);
  assert.ok(adapted.warnings.includes('SENACE_FALLO_AL_GUARDAR_RESPALDO'));
});

for (const status of ['EXPIRED', 'INVALID', 'MISSING', 'IO_ERROR', 'OWNERSHIP_UNAVAILABLE']) {
  test(`un respaldo declarado con lectura ${status} nunca se acepta como utilizable`, () => {
    const r = dashboard('lkgSenace'); r.json.consultaSenace.lkg.lectura = status;
    assert.equal(adaptDashboard(r).state, 'error');
    assert.equal(usableBackup(r.json.consultaSenace.lkg), false);
  });
}

test('esquema incompleto o incompatible no se presenta como respaldo valido', () => {
  for (const mutate of [meta => { delete meta.respaldo; }, meta => { delete meta.intentoApi; },
    meta => { delete meta.respaldo.capturadoEn; }, meta => { meta.respaldo.versionEsquema = 2; },
    meta => { meta.respaldo.edadSegundos = -1; }, meta => { meta.respaldo.edadSegundos = 1.5; },
    meta => { meta.respaldo.antiguo = 'true'; }, meta => { meta.respaldo.capturadoEn = '2026-02-30T12:00:00Z'; },
    meta => { meta.intentoApi.estado = 'EXITO'; }]) {
    const r = dashboard('lkgSenace'); mutate(r.json.consultaSenace.lkg);
    assert.equal(adaptDashboard(r).state, 'error');
  }
  const incompleteDirect = response('dashboardSuccess'); incompleteDirect.json.consultaSenace.lkg = {};
  assert.equal(adaptDashboard(incompleteDirect).state, 'degraded');
  assert.ok(knownProjects(adaptDashboard(incompleteDirect)).length);
});

test('fuentes y origen por fila siguen siendo coherentes', () => {
  const r = dashboard('lkgBoth'); r.json.proyectos[0].origen = 'API_DIRECTA_SENACE';
  assert.equal(adaptDashboard(r).state, 'error');
  const other = dashboard('lkgBoth'); other.json.fuentes.bcrp = 'API_DIRECTA_BCRP';
  assert.equal(adaptDashboard(other).state, 'error');
});

for (const scenario of ['consultorasLkg', 'consultorasLkgOld', 'consultorasLkgEmpty']) {
  test(`${scenario}: HTTP200 respaldado conserva filas y conteos incluidos ceros conocidos`, () => {
    const r = response(scenario), adapted = adaptConsultoras(r);
    assert.equal(adapted.state, 'degraded');
    assert.equal(adapted.data.consulta.origen, 'RESPALDO_LOCAL');
    assert.equal(adapted.data.consulta.lkg.respaldo.versionEsquema, undefined);
    const snapshot = { http: r, result: adapted };
    const rows = usableRows(snapshot), filtered = filterConsultants(rows, emptyFilters);
    assert.equal(consultantKpis(snapshot, filtered).received, r.json.length);
    assert.deepEqual(rows, r.json);
    assert.equal(adapted.data.consulta.lkg.intentoApi.motivo, 'ERROR_HTTP');
  });
}

for (const scenario of ['consultorasLkg502', 'consultorasLkg503']) {
  test(`${scenario}: array de error no acredita cero consultoras`, () => {
    const r = response(scenario), adapted = adaptConsultoras(r);
    assert.equal(adapted.state, 'error');
    assert.equal(adapted.data.consulta.origen, 'SIN_RESPALDO');
    const snapshot = { http: r, result: adapted };
    assert.equal(usableRows(snapshot), undefined);
    assert.equal(consultantKpis(snapshot, undefined).received, undefined);
  });
}

test('headers LKG incompletos o mal tipados no se rellenan ni se aceptan como respaldo', () => {
  for (const key of ['x-senace-respaldo-capturado-en', 'x-senace-respaldo-edad-segundos', 'x-senace-respaldo-antiguo', 'x-senace-intento-estado', 'x-senace-snapshot-lectura']) {
    const r = response('consultorasLkg'); delete r.headers[key];
    assert.equal(adaptConsultoras(r).state, 'error');
  }
  for (const [key, value] of [['x-senace-respaldo-edad-segundos', '-1'], ['x-senace-respaldo-edad-segundos', '1.5'],
    ['x-senace-respaldo-antiguo', 'TRUE'], ['x-senace-respaldo-capturado-en', 'fecha'], ['x-senace-snapshot-lectura', 'EXPIRED']]) {
    const r = response('consultorasLkg'); r.headers[key] = value;
    assert.equal(adaptConsultoras(r).state, 'error');
  }
});

test('consultoras directas con escritura fallida siguen disponibles', () => {
  const r = response('consultorasLkgWriteFailure'); const adapted = adaptConsultoras(r);
  assert.equal(adapted.state, 'degraded');
  assert.equal(usableRows({ http: r, result: adapted }).length, r.json.length);
  assert.equal(adapted.data.consulta.lkg.respaldo, undefined);
});

test('cliente reutilizado captura los siete headers nuevos sin delegar a fetch global', async () => {
  const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/consultoras': 'consultorasLkgOld' }) });
  const r = await api.consultoras();
  assert.equal(r.mode, 'mock'); assert.equal(r.status, 200);
  assert.equal(r.headers['x-senace-respaldo-antiguo'], 'true');
  assert.equal(r.headers['x-senace-respaldo-edad-segundos'], '1728000');
  assert.equal(r.headers['x-senace-snapshot-lectura'], 'AVAILABLE');
  assert.equal(adaptConsultoras(r).state, 'degraded');
});

for (const [name, state] of [['monitorLkgUnverified', 'ready'], ['monitorLkgOperational', 'ready'], ['monitorLkgIncident', 'degraded']]) {
  test(`${name}: estado local independiente de circuitos y disponibilidad externa`, () => {
    const r = response(name), result = adaptMonitor(r), snapshot = { http: r, result, completedAt: 0 };
    assert.equal(result.state, state);
    assert.equal(metricsFor(snapshot, 'senace').disponibilidadExterna, 'NO_VERIFICADA');
    assert.match(lkgExplanation(snapshot), /proceso actual/);
    if (name === 'monitorLkgUnverified') assert.deepEqual(result.data.lkg.observaciones, []);
    if (name === 'monitorLkgOperational') assert.equal(result.data.lkg.observaciones[0].integridad, 'VALIDADA');
    if (name === 'monitorLkgIncident') assert.equal(metricsFor(snapshot, 'senace').estado, 'OPEN');
  });
}

test('monitor anterior y monitor sin informacion no certifican archivos inexistentes', () => {
  const r = response('monitor'), old = { http: r, result: adaptMonitor(r), completedAt: 0 };
  assert.match(lkgExplanation(old), /histórico/);
  assert.match(lkgStateLabel('HABILITADO_SIN_VERIFICACION'), /sin verificación/);
  assert.equal(lkgStateLabel(undefined), 'Estado LKG no informado');
  const invalid = response('monitorLkgOperational'); invalid.json.lkg.observaciones[0].edadSegundos = -1;
  assert.equal(adaptMonitor(invalid).state, 'error');
  const partial = response('monitorLkgUnverified'); partial.json.lkg = {};
  assert.equal(adaptMonitor(partial).state, 'degraded');
});

test('presentacion segura no publica URLs autenticadas o motivos externos arbitrarios', () => {
  const secret = 'https://example.invalid/?auth_key=ARTIFICIAL_NO_PUBLICAR';
  assert.equal(safeLkgReason(secret), 'MOTIVO_NO_INFORMADO');
  assert.equal(snapshotResourceLabel(secret), 'Recurso no informado o no reconocido');
  assert.equal(lkgMetadata({ intentoApi: { estado: 'ERROR_EXTERNO', motivo: secret } }), false);
  assert.equal(originLabel(secret), 'Fuente no disponible o procedencia no informada');
  assert.equal(captureLabel(secret), 'No informada');
  assert.match(captureLabel('2026-11-10T12:00:00Z'), /UTC/);
  assert.match(ageLabel(3600), /1 h/);
  assert.equal(ageLabel(undefined), 'No informada');
});

test('Dashboard y Explorador reutilizan la misma respuesta respaldada sin segundo GET', async () => {
  let calls = 0;
  const store = createDashboardSelectionStore();
  const body = fixture(key('lkgBoth')).body;
  // Se reutilizan coordenadas ya artificiales del fixture original para esta prueba, nunca datos HTTP reales.
  body.proyectos[0].latitud = -12.1; body.proyectos[0].longitud = -77.1;
  const snapshot = await loadDashboardHttp({ sector: 'Hidrocarburos', anio: 2017 }, config, undefined,
    async () => { calls++; return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } }); }, store);
  assert.equal(calls, 1); assert.equal(snapshot.http.mode, 'http');
  const shared = store.peek();
  const index = createExplorerIndex(shared), spatial = explorerSpatial(index, index.entries);
  const page = paginateExplorer(index.entries, 1);
  const points = pagePointGroups(index, spatial, page.rows, undefined);
  assert.ok(page.rows.length <= 20); assert.ok(points.length <= 21);
  assert.equal(shared.result.data.consultaSenace.origen, 'RESPALDO_LOCAL');
  assert.equal(shared.result.data.consultaSenace.lkg.respaldo.capturadoEn, snapshot.result.data.consultaSenace.lkg.respaldo.capturadoEn);
  assert.equal(calls, 1);
});

test('cancelacion y resultados obsoletos no publican un respaldo antiguo sobre respuesta posterior', async () => {
  const latest = createLatestRequest();
  let release;
  const old = latest.run(() => new Promise(resolve => { release = resolve; }));
  const current = await latest.run(async () => adaptDashboard(dashboard('lkgBcrp')));
  release(adaptDashboard(dashboard('lkgOld')));
  assert.equal((await old).state, 'stale'); assert.equal(current.state, 'current');
  assert.equal(current.value.data.consultaSenace.origen, 'API_DIRECTA_SENACE');
  let resolveCancelled;
  const pending = latest.run(() => new Promise(resolve => { resolveCancelled = resolve; }));
  latest.cancel(); resolveCancelled(adaptDashboard(dashboard('lkgBoth')));
  assert.notEqual((await pending).state, 'current');
});
