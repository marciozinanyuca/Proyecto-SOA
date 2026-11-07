import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture } from '../../.api-test-build/mocks/fixtures.js';
import { adaptMonitor } from '../../.api-test-build/api/adapters/monitor.js';
import { createLatestRequest } from '../../.api-test-build/api/requests.js';
import { circuitLabel, countDisplay, loadMonitor, metricsFor, monitorMessage, rateDisplay } from '../../.api-test-build/features/monitor/monitorModel.js';

// Telemetría artificial de prueba; nunca observaciones reales de proveedores.
function snapshot(scenario = 'monitor') {
  const f = fixture(scenario);
  const http = { mode: 'mock', status: f.status, headers: {}, json: f.body };
  return { scenario, http, result: adaptMonitor(http), completedAt: 1234 };
}

for (const [state, label] of [['CLOSED', 'Circuito cerrado'], ['OPEN', 'Circuito abierto'], ['HALF_OPEN', 'Recuperación en evaluación'], ['DISABLED', 'Circuito deshabilitado']]) {
  test(`${state}: etiqueta de circuito sin promesa de disponibilidad`, () => {
    assert.equal(circuitLabel(state).label, label);
    assert.ok(!circuitLabel(state).label.includes('Operativo'));
    assert.ok(!circuitLabel(state).label.includes('Proveedor disponible'));
  });
}

test('estado desconocido o ausente no se transforma en CLOSED', () => {
  assert.equal(circuitLabel(undefined).label, 'Estado no disponible');
  assert.equal(circuitLabel('VALOR_NO_CONOCIDO').tone, 'neutral');
});

test('tasa -1 es no calculada; cero conocido no es ausencia', () => {
  assert.equal(rateDisplay(-1), 'No calculada');
  assert.equal(rateDisplay(0), '0 %'); assert.equal(rateDisplay(100), '100 %');
  for (const v of [undefined, -2, 101, NaN, Infinity]) assert.equal(rateDisplay(v), 'No disponible');
  assert.equal(rateDisplay(12.5), '12.5 %');
});

test('contadores opcionales no se completan con constantes', () => {
  assert.equal(countDisplay(0), '0');
  for (const v of [undefined, -1, 1.5, Infinity, NaN, Number.MAX_SAFE_INTEGER + 1]) assert.equal(countDisplay(v), 'No disponible');
});

test('snapshot completo preserva métricas y límites del contrato', () => {
  const s = snapshot(); assert.equal(s.result.state, 'ready');
  const senace = metricsFor(s, 'senace'); const bcrp = metricsFor(s, 'bcrp');
  assert.equal(senace.estado, 'CLOSED'); assert.equal(bcrp.estado, 'OPEN');
  assert.equal(rateDisplay(senace.tasaFallosPorcentaje), 'No calculada');
  for (const metrics of [senace, bcrp]) {
    assert.equal(metrics.disponibilidadExterna, 'NO_VERIFICADA');
    assert.equal(metrics.respaldoLocal, 'NO_IMPLEMENTADO');
    assert.equal(metrics.alcanceMetricas, 'LLAMADAS_LOGICAS');
    for (const key of ['latencia', 'solicitudesBloqueadas', 'historial', 'logs', 'timestamp']) assert.equal(metrics[key], undefined);
  }
});

test('HALF_OPEN y DISABLED se conservan desde los fixtures existentes', () => {
  const s = snapshot('monitorHalfOpen');
  assert.equal(metricsFor(s, 'senace').estado, 'HALF_OPEN');
  assert.equal(metricsFor(s, 'bcrp').estado, 'DISABLED');
});

test('snapshot parcial conserva lo conocido sin inventar el otro servicio', () => {
  const s = snapshot('monitorDemoPartial'); assert.equal(s.result.state, 'degraded');
  assert.equal(metricsFor(s, 'senace').llamadasExitosas, 0);
  assert.equal(metricsFor(s, 'senace').disponibilidadExterna, undefined);
  assert.equal(metricsFor(s, 'bcrp'), undefined);
  assert.match(monitorMessage(s), /Snapshot incompleto/);
});

test('objeto sin métricas usa degraded del adaptador, no vacío oficial', () => {
  const s = snapshot('monitorDemoEmpty'); assert.equal(s.result.state, 'degraded');
  assert.equal(metricsFor(s, 'senace'), undefined); assert.equal(metricsFor(s, 'bcrp'), undefined);
});

test('números opcionales ausentes siguen ausentes incluso en snapshot ready', () => {
  const s = snapshot('monitorDemoOptional'); assert.equal(s.result.state, 'ready');
  for (const service of ['senace', 'bcrp']) {
    const metrics = metricsFor(s, service);
    assert.equal(countDisplay(metrics.llamadasFallidas), 'No disponible');
    assert.equal(countDisplay(metrics.llamadasExitosas), 'No disponible');
    assert.equal(rateDisplay(metrics.tasaFallosPorcentaje), 'No disponible');
  }
});

for (const scenario of ['monitorDemoError', 'monitorDemoInvalid']) test(`${scenario}: no publica métricas ficticias`, () => {
  const s = snapshot(scenario); assert.equal(s.result.state, 'error');
  assert.equal(metricsFor(s, 'senace'), undefined); assert.equal(metricsFor(s, 'bcrp'), undefined);
  assert.match(monitorMessage(s), /No se obtuvo/);
});

test('la carga es explícita, obtiene HTTP mock y fecha de finalización del cliente', async () => {
  const start = Date.now();
  const s = await loadMonitor('monitor');
  assert.equal(s.http.mode, 'mock'); assert.equal(s.http.status, 200);
  assert.equal(s.http.headers['content-type'], 'application/json');
  assert.ok(s.completedAt >= start && s.completedAt <= Date.now());
  assert.equal(s.http.json.timestamp, undefined);
});

test('actualización manual obtiene el escenario nuevo sin modificar circuitos externos', async () => {
  const first = await loadMonitor('monitor'); const second = await loadMonitor('monitorHalfOpen');
  assert.equal(metricsFor(first, 'senace').estado, 'CLOSED');
  assert.equal(metricsFor(second, 'senace').estado, 'HALF_OPEN');
  assert.equal(first.scenario, 'monitor'); assert.equal(second.scenario, 'monitorHalfOpen');
});

test('modo mock no usa fetch global ni reintenta HTTP de error', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = () => { calls++; throw new Error('RED_PROHIBIDA'); };
  try {
    const s = await loadMonitor('monitorDemoError');
    assert.equal(s.http.status, 503); assert.equal(s.result.state, 'error'); assert.equal(calls, 0);
  } finally { globalThis.fetch = original; }
});

test('cancelación y respuestas obsoletas no publican telemetría anterior', async () => {
  const coordinator = createLatestRequest();
  const old = coordinator.run(signal => loadMonitor('monitor', signal));
  const current = coordinator.run(signal => loadMonitor('monitorDemoPartial', signal));
  assert.equal((await old).state, 'stale');
  const result = await current; assert.equal(result.state, 'current'); assert.equal(result.value.scenario, 'monitorDemoPartial');
  const pending = coordinator.run(signal => loadMonitor('monitor', signal)); coordinator.cancel();
  assert.equal((await pending).state, 'cancelled');
});
