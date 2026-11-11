import test from 'node:test';
import assert from 'node:assert/strict';
import { createConsultorasSource, createDashboardSource, createMonitorSource, featureAccess, parseSourceConfig, sourcePresentation } from '../../.api-test-build/api/source.js';

const httpEnv = { VITE_API_MODE: 'http', VITE_API_BASE_URL: 'http://127.0.0.1:8081', VITE_HTTP_FEATURES: 'monitor' };

test('nube resuelve same-origin desde el navegador sin aceptar destinos externos configurados', async () => {
  const config = parseSourceConfig({ ...httpEnv, VITE_API_BASE_URL: 'same-origin' }, 'https://ecoinvest.example');
  assert.equal(config.valid, true);
  assert.equal(config.baseUrl, 'https://ecoinvest.example');
  assert.equal(sourcePresentation(config).heading, 'BACKEND HTTP CONFIGURADO');
  const requests = [];
  const api = createMonitorSource(config, 'monitor', async url => {
    requests.push(String(url)); return new Response('{}');
  });
  assert.equal(requests.length, 0);
  await api.estadoServicios();
  assert.deepEqual(requests, ['https://ecoinvest.example/api/estado-servicios']);
  assert.equal((await api.datastream('CarteraProyectos')).issue, 'CONFIGURACION_INVALIDA');
  assert.equal(requests.length, 1);
  assert.equal(parseSourceConfig({ ...httpEnv, VITE_API_BASE_URL: 'https://other.example' }).valid, false);
});

test('same-origin no admite origen ausente, credenciales, prefijos, query, fragmentos o HTTP remoto', () => {
  for (const origin of [undefined, '', 'http://other.example', 'https://user:password@example.test',
    'https://example.test/api', 'https://example.test?key=artificial', 'https://example.test/#dashboard']) {
    const config = parseSourceConfig({ ...httpEnv, VITE_API_BASE_URL: 'same-origin' }, origin);
    assert.equal(config.valid, false);
    assert.equal(config.reason, 'URL_INVALIDA');
  }
  const local = parseSourceConfig({ ...httpEnv, VITE_API_BASE_URL: 'same-origin' }, 'http://127.0.0.1:8080');
  assert.equal(local.valid, true);
  assert.equal(local.baseUrl, 'http://127.0.0.1:8080');
});

test('7D shell describe configuracion HTTP sin afirmar conexion o respuesta recibida', () => {
  const presentation = sourcePresentation(parseSourceConfig(httpEnv));
  assert.equal(presentation.stage, 'Modo HTTP seleccionado');
  assert.equal(presentation.heading, 'BACKEND LOCAL CONFIGURADO');
  assert.ok(presentation.description.includes('cada vista'));
  assert.ok(presentation.note.includes('no acredita'));
  assert.ok(presentation.entitiesLabel.includes('no acreditan disponibilidad externa'));
  assert.ok(!Object.values(presentation).join(' ').includes('Frontend en preparación'));
});

test('7D shell distingue demostracion y configuracion invalida sin mostrar valores de entrada', () => {
  const mock = sourcePresentation(parseSourceConfig({}));
  assert.equal(mock.stage, 'Demostración · datos artificiales');
  const invalid = sourcePresentation(parseSourceConfig({ VITE_API_MODE: 'VALOR_ARTIFICIAL_INVALIDO' }));
  assert.equal(invalid.heading, 'CONSULTAS BLOQUEADAS');
  assert.ok(!JSON.stringify(invalid).includes('VALOR_ARTIFICIAL_INVALIDO'));
  assert.notEqual(mock.stage, invalid.stage);
});

test('configuración ausente conserva mock explícito y las cuatro vistas', () => {
  const config = parseSourceConfig({});
  assert.equal(config.valid, true); assert.equal(config.mode, 'mock');
  for (const feature of ['dashboard', 'explorador', 'consultoras', 'monitor']) assert.equal(featureAccess(config, feature).allowed, true);
});

test('HTTP válido habilita exclusivamente Monitor y normaliza la base', () => {
  const config = parseSourceConfig({ ...httpEnv, VITE_API_BASE_URL: 'http://127.0.0.1:8081/' });
  assert.equal(config.valid, true); assert.equal(config.mode, 'http');
  assert.equal(config.baseUrl, 'http://127.0.0.1:8081');
  assert.equal(featureAccess(config, 'monitor').allowed, true);
  for (const feature of ['dashboard', 'explorador', 'consultoras']) {
    const access = featureAccess(config, feature);
    assert.equal(access.allowed, false); assert.match(access.message, /no habilitada/);
  }
});

for (const mode of ['', 'HTTP', 'auto', 'invalid']) test(`modo inválido ${JSON.stringify(mode)} no recurre a mock`, () => {
  const config = parseSourceConfig({ VITE_API_MODE: mode });
  assert.equal(config.valid, false); assert.equal(config.reason, 'MODO_INVALIDO');
  assert.equal(featureAccess(config, 'monitor').allowed, false);
});

test('HTTP requiere URL y permiso de Monitor expresos', () => {
  assert.deepEqual(parseSourceConfig({ VITE_API_MODE: 'http', VITE_HTTP_FEATURES: 'monitor' }), { valid: false, reason: 'URL_AUSENTE' });
  for (const features of [undefined, '', 'dashboard', 'monitor,dashboard', 'monitor,monitor', 'all']) {
    const config = parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: features });
    assert.equal(config.valid, false); assert.equal(config.reason, 'FEATURES_INVALIDAS');
  }
});

test('mock no acepta permisos HTTP residuales silenciosamente', () => {
  assert.equal(parseSourceConfig({ VITE_API_MODE: 'mock', VITE_HTTP_FEATURES: 'monitor' }).valid, false);
});

test('se rechazan destinos no autorizados, credenciales, prefijos y query', () => {
  for (const url of ['https://example.invalid', 'http://localhost:8081', 'http://127.0.0.1:8082', 'https://127.0.0.1:8081',
    'http://127.0.0.1:8081/api', 'http://127.0.0.1:8081?auth_key=FIXTURE_NO_SECRET',
    'http://usuario:FIXTURE_NO_SECRET@127.0.0.1:8081', 'http://127.0.0.1:8081/#fragment', 'texto-invalido']) {
    const config = parseSourceConfig({ ...httpEnv, VITE_API_BASE_URL: url });
    assert.equal(config.valid, false); assert.equal(config.reason, 'URL_INVALIDA');
    assert.ok(!JSON.stringify(config).includes(url));
  }
});

test('errores de configuración no muestran valores de entrada ni crean transportes', () => {
  const config = parseSourceConfig({ VITE_API_MODE: 'VALOR_QUE_NO_SE_DEBE_REFLEJAR' });
  assert.ok(!featureAccess(config, 'monitor').message.includes('VALOR_QUE_NO_SE_DEBE_REFLEJAR'));
  let calls = 0;
  assert.throws(() => createMonitorSource(config, 'monitor', async () => { calls++; }), /CONFIGURACION_PUBLICA_INVALIDA/);
  assert.equal(calls, 0);
});

test('construcción y protección de las otras tres operaciones no hacen HTTP', async () => {
  let calls = 0;
  const api = createMonitorSource(parseSourceConfig(httpEnv), 'monitor', async () => { calls++; return new Response('{}'); });
  assert.equal(calls, 0);
  for (const result of await Promise.all([api.dashboard('Hidrocarburos', 2017), api.consultoras(), api.datastream('CarteraProyectos')])) {
    assert.equal(result.mode, 'http'); assert.equal(result.status, null); assert.equal(result.issue, 'CONFIGURACION_INVALIDA');
  }
  assert.equal(calls, 0);
});

test('única capacidad HTTP usa GET Monitor y opciones seguras del cliente existente', async () => {
  const observed = [];
  const api = createMonitorSource(parseSourceConfig(httpEnv), 'monitor', async (url, init) => {
    observed.push({ url: String(url), init }); return new Response('{}', { status: 200 });
  });
  const response = await api.estadoServicios();
  assert.equal(response.mode, 'http'); assert.equal(response.status, 200); assert.equal(observed.length, 1);
  assert.equal(observed[0].url, 'http://127.0.0.1:8081/api/estado-servicios');
  assert.equal(observed[0].init.method, 'GET'); assert.equal(observed[0].init.credentials, 'omit');
  assert.equal(observed[0].init.redirect, 'error'); assert.equal(observed[0].init.headers.Authorization, undefined);
});

test('HTTP fallido no devuelve fixtures ni reintenta', async () => {
  let calls = 0;
  const api = createMonitorSource(parseSourceConfig(httpEnv), 'monitor', async () => { calls++; throw new Error('FALLO ARTIFICIAL'); });
  const result = await api.estadoServicios();
  assert.equal(result.mode, 'http'); assert.equal(result.issue, 'RED'); assert.equal(result.json, undefined); assert.equal(calls, 1);
});

test('mock permanece aislado de fetch HTTP incluso si se suministra transporte', async () => {
  let calls = 0;
  const api = createMonitorSource(parseSourceConfig({ VITE_API_MODE: 'mock' }), 'monitor', async () => { calls++; throw new Error('PROHIBIDO'); });
  const result = await api.estadoServicios(); assert.equal(result.mode, 'mock'); assert.equal(result.status, 200); assert.equal(calls, 0);
});

test('cancelación previa impide GET HTTP', async () => {
  let calls = 0; const controller = new AbortController(); controller.abort();
  const api = createMonitorSource(parseSourceConfig(httpEnv), 'monitor', async () => { calls++; return new Response('{}'); });
  const result = await api.estadoServicios(controller.signal);
  assert.equal(result.issue, 'CANCELADA'); assert.equal(calls, 0);
});

test('monitor,consultoras es combinación explícita y no habilita Dashboard o Explorador', () => {
  const config = parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: 'monitor,consultoras' });
  assert.equal(config.valid, true); assert.deepEqual(config.httpFeatures, ['monitor', 'consultoras']);
  for (const feature of ['monitor', 'consultoras']) assert.equal(featureAccess(config, feature).allowed, true);
  for (const feature of ['dashboard', 'explorador']) assert.equal(featureAccess(config, feature).allowed, false);
});

test('combinaciones de permisos no aprobadas no amplían el allowlist', () => {
  for (const features of ['consultoras', 'consultoras,monitor', 'monitor, consultoras', 'monitor,consultoras,dashboard', 'monitor,consultoras,consultoras', 'monitor,consultoras,datastream']) {
    assert.equal(parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: features }).valid, false);
  }
});

test('Monitor sigue siendo la única operación de su capacidad con ambos permisos', async () => {
  let calls = 0;
  const config = parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: 'monitor,consultoras' });
  const api = createMonitorSource(config, 'monitor', async url => { calls++; assert.equal(String(url), 'http://127.0.0.1:8081/api/estado-servicios'); return new Response('{}'); });
  assert.equal((await api.consultoras()).issue, 'CONFIGURACION_INVALIDA');
  await api.estadoServicios(); assert.equal(calls, 1);
});

test('Consultoras sin permiso se rechaza antes de invocar un transporte', () => {
  let calls = 0;
  assert.throws(() => createConsultorasSource(parseSourceConfig(httpEnv), 'consultorasDemoSuccess', async () => { calls++; }), /CONFIGURACION_PUBLICA_INVALIDA/);
  assert.equal(calls, 0);
});

test('capacidad Consultoras solo ejecuta su GET, no Monitor ni otros recursos', async () => {
  const requests = [];
  const config = parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: 'monitor,consultoras' });
  const api = createConsultorasSource(config, 'consultorasDemoSuccess', async (url, init) => { requests.push({ url: String(url), init }); return new Response('[]'); });
  assert.equal(requests.length, 0);
  const blocked = await Promise.all([api.dashboard('Hidrocarburos', 2017), api.datastream('ConsultorasAmbientales'), api.estadoServicios()]);
  assert.ok(blocked.every(result => result.issue === 'CONFIGURACION_INVALIDA')); assert.equal(requests.length, 0);
  await api.consultoras(); assert.equal(requests.length, 1);
  assert.equal(requests[0].url, 'http://127.0.0.1:8081/api/consultoras');
  assert.equal(requests[0].init.method, 'GET'); assert.equal(requests[0].init.credentials, 'omit');
  assert.equal(requests[0].init.redirect, 'error'); assert.equal(requests[0].init.headers.Authorization, undefined);
});

const fullHttp = () => parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: 'monitor,consultoras,dashboard,explorador' });

test('7C3 permite las cuatro vistas solo en la combinacion completa aprobada', () => {
  const config = fullHttp();
  assert.equal(config.valid, true);
  for (const view of ['monitor', 'consultoras', 'dashboard', 'explorador']) assert.equal(featureAccess(config, view).allowed, true);
  for (const value of ['dashboard,explorador', 'monitor,consultoras,explorador,dashboard', 'monitor,consultoras,dashboard,explorador,datastream']) {
    assert.equal(parseSourceConfig({ ...httpEnv, VITE_HTTP_FEATURES: value }).valid, false);
  }
});

test('7C3 crear capacidad no consulta; Dashboard solo permite GET parametrizado propio', async () => {
  const calls = [];
  const api = createDashboardSource(fullHttp(), 'dashboardSuccess', async (url, init) => {
    calls.push({ url: new URL(url), init }); return new Response('{}');
  });
  assert.equal(calls.length, 0);
  for (const result of await Promise.all([api.consultoras(), api.estadoServicios(), api.datastream('CarteraProyectos')])) {
    assert.equal(result.issue, 'CONFIGURACION_INVALIDA');
  }
  assert.equal(calls.length, 0);
  await api.dashboard('Actividad artificial con espacios', 2020);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url.pathname, '/api/dashboard');
  assert.equal(calls[0].url.searchParams.get('sector'), 'Actividad artificial con espacios');
  assert.equal(calls[0].url.searchParams.get('anio'), '2020');
  assert.equal(calls[0].init.credentials, 'omit'); assert.equal(calls[0].init.redirect, 'error');
});

test('7C3 permiso antiguo no crea Dashboard ni inicia fetch', () => {
  let calls = 0;
  assert.throws(() => createDashboardSource(parseSourceConfig(httpEnv), 'dashboardSuccess', async () => { calls++; }), /CONFIGURACION_PUBLICA_INVALIDA/);
  assert.equal(calls, 0);
});

test('7C3 Monitor y Consultoras conservan aislamiento con permisos completos', async () => {
  const urls = [];
  const fetcher = async url => { urls.push(new URL(url).pathname); return new Response('{}'); };
  const monitor = createMonitorSource(fullHttp(), 'monitor', fetcher);
  const consultoras = createConsultorasSource(fullHttp(), 'consultorasDemoSuccess', fetcher);
  assert.equal((await monitor.dashboard('TODOS', 2017)).issue, 'CONFIGURACION_INVALIDA');
  assert.equal((await consultoras.dashboard('TODOS', 2017)).issue, 'CONFIGURACION_INVALIDA');
  await monitor.estadoServicios(); await consultoras.consultoras();
  assert.deepEqual(urls, ['/api/estado-servicios', '/api/consultoras']);
});
