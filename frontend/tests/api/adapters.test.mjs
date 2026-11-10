import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';
import { adaptDashboard } from '../../.api-test-build/api/adapters/dashboard.js';
import { adaptConsultoras } from '../../.api-test-build/api/adapters/consultoras.js';
import { adaptDatastream } from '../../.api-test-build/api/adapters/datastream.js';
import { adaptMonitor } from '../../.api-test-build/api/adapters/monitor.js';

function response(name) {
  const f = fixture(name);
  return { mode: 'mock', status: f.status, headers: Object.fromEntries(new Headers(f.headers).entries()), json: f.body };
}
for (const [scenario, state] of [['dashboardSuccess', 'ready'], ['dashboardEmpty', 'empty'], ['dashboardNoMatches', 'empty'],
  ['dashboardDegraded', 'degraded'], ['dashboardDates', 'degraded'], ['dashboardOptional', 'degraded']]) {
  test(`dashboard: ${scenario}`, () => assert.equal(adaptDashboard(response(scenario)).state, state));
}
test('dashboard no transforma desconocido en cero ni pierde BCRP', () => {
  const r = adaptDashboard(response('dashboardDegraded'));
  assert.equal(r.data.total, undefined); assert.equal(r.data.proyectos, undefined);
  assert.equal(r.data.indicadorBcrp.valor, 1.25); assert.equal(r.reason, 'CONFIGURACION_AUSENTE');
});
test('dashboard rechaza forma incorrecta, null no permitido y RUC numerico', () => {
  for (const json of [[], null, { total: null }, { proyectos: [{ ruc: 123 }] }, { total: -1 }, { total: 1.5 }]) {
    assert.equal(adaptDashboard({ mode: 'mock', status: 200, headers: {}, json }).state, 'error');
  }
});
test('int64 inseguro y numeros no finitos no son datos utilizables', () => {
  for (const value of [9007199254740993, Infinity, NaN]) {
    const r = response('dashboardSuccess'); r.json.proyectos[0].id = value;
    assert.equal(adaptDashboard(r).state, 'error');
  }
  const r = response('dashboardSuccess'); r.json.proyectos[0].id = Number.MAX_SAFE_INTEGER;
  assert.equal(adaptDashboard(r).state, 'ready');
});
test('RUC texto y decimal aproximado se conservan con advertencia', () => {
  const r = adaptDashboard(response('dashboardSuccess'));
  assert.equal(r.data.proyectos[0].ruc, '00000000000'); assert.equal(typeof r.data.proyectos[0].latitud, 'number');
  assert.ok(r.warnings.includes('NUMEROS_JS_NO_GARANTIZAN_PRECISION_DECIMAL_EXACTA'));
});
test('conteos y procedencias incoherentes se rechazan', () => {
  const r = response('dashboardSuccess'); r.json.aprobados = 2;
  assert.equal(adaptDashboard(r).state, 'error');
  const p = response('dashboardSuccess'); p.json.fuentes.senace = 'NO_DISPONIBLE';
  assert.equal(adaptDashboard(p).state, 'error');
});
for (const [scenario, state] of [['consultantsSuccess', 'ready'], ['consultantsEmpty', 'empty'], ['consultants502', 'error'], ['consultants503', 'error']]) {
  test(`consultoras: ${scenario}`, () => assert.equal(adaptConsultoras(response(scenario)).state, state));
}
test('consultoras preserva filas repetidas, fechas y ocho conceptos sin derivar identidad', () => {
  const r = adaptConsultoras(response('consultantsSuccess'));
  assert.equal(r.data.rows.length, 3); assert.deepEqual(r.data.rows[0], r.data.rows[1]);
  assert.equal(r.data.rows[0].fechaRd, 'TEXTO ORIGINAL');
  for (const field of ['id', 'ruc', 'nivel', 'registro', 'estado']) assert.equal(field in r.data.rows[0], false);
  assert.deepEqual(r.data.rows[2], {});
});
test('sin headers no se declara vacio oficial', () => {
  assert.equal(adaptConsultoras({ mode: 'mock', status: 200, headers: {}, json: [] }).state, 'degraded');
});
test('headers invalidos y error HTTP con filas son rechazados', () => {
  const r = response('consultantsSuccess'); r.headers['x-senace-cuerpo-completo'] = 'quizas';
  assert.equal(adaptConsultoras(r).state, 'error');
  const e = response('consultants503'); e.json = [{}]; assert.equal(adaptConsultoras(e).reason, 'ERROR_CON_FILAS');
});
for (const scenario of ['datastreamArray', 'datastreamConsultants', 'datastreamObject']) {
  test(`generico: ${scenario}`, () => assert.equal(adaptDatastream(response(scenario)).state, 'ready'));
}
test('generico conserva null explicito de error y no certifica recurso heredado', () => {
  const n = adaptDatastream(response('datastreamNull')); assert.equal(n.state, 'error'); assert.equal(n.data.data, null);
  const legacy = adaptDatastream(response('datastreamObject'));
  assert.ok(legacy.warnings.includes('ESQUEMA_RECURSO_HEREDADO_NO_VERIFICADO'));
});
test('generico no convierte campos oficiales en camelCase ni acepta raiz equivocada', () => {
  const r = response('datastreamArray'); const result = adaptDatastream(r);
  assert.equal(result.data.data[0].ID, 1); assert.equal(result.data.data[0].id, undefined);
  r.json.data = {}; assert.equal(adaptDatastream(r).state, 'error');
});
test('generico error sin data null y envolvente invalida se rechazan', () => {
  const r = response('datastreamNull'); delete r.json.data;
  assert.equal(adaptDatastream(r).state, 'error');
  assert.equal(adaptDatastream({ mode: 'mock', status: 200, headers: {}, json: [] }).state, 'error');
});
for (const scenario of ['monitor', 'monitorHalfOpen']) {
  test(`monitor: ${scenario}`, () => {
    const r = adaptMonitor(response(scenario)); assert.equal(r.state, 'ready');
    assert.equal(r.data.senace.disponibilidadExterna, 'NO_VERIFICADA');
    assert.ok(r.warnings.includes('CIRCUITO_NO_ACREDITA_DISPONIBILIDAD_EXTERNA'));
  });
}
test('monitor opcional no inventa metricas y no admite disponibilidad certificada', () => {
  const r = adaptMonitor({ mode: 'mock', status: 200, headers: {}, json: {} });
  assert.equal(r.state, 'degraded'); assert.equal(r.data.senace, undefined);
  const e = response('monitor'); e.json.senace.disponibilidadExterna = 'VERIFICADA';
  assert.equal(adaptMonitor(e).state, 'error');
});
