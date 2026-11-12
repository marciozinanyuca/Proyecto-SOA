/** Opt-in browser QA against the TEST-SCOPE Java harness, never a production backend.
 * Requires an independently authorized Vite and Chromium/Edge with loopback CDP.
 * Example (after separate permissions):
 * node tests/integration/lkg-local-http.mjs --run-lkg-lab http://127.0.0.1:9222 DIRECT <existing-temp-evidence-directory>
 * Scenario must match the harness stdin state. Rerun after changing it, no automatic provider retries.
 * DIRECT | FAIL_SENACE | FAIL_BCRP | FAIL_BOTH | STALE | CORRUPT | MISSING | INVALID | EXPIRED.
 * Real browser HTTP; all upstream payloads ARTIFICIAL. No injected React fetch or fixtures.
 */
import assert from 'node:assert/strict';
import { realpath, lstat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

const [optIn, cdpArgument, scenario, evidenceArgument] = process.argv.slice(2);
assert.equal(optIn, '--run-lkg-lab', 'Explicit opt-in required');
const expected = {
  DIRECT: ['API_DIRECTA_SENACE', 'API_DIRECTA_BCRP', 'API_DIRECTA_SENACE', 200],
  FAIL_SENACE: ['RESPALDO_LOCAL', 'API_DIRECTA_BCRP', 'RESPALDO_LOCAL', 200],
  FAIL_BCRP: ['API_DIRECTA_SENACE', 'RESPALDO_LOCAL', 'API_DIRECTA_SENACE', 200],
  FAIL_BOTH: ['RESPALDO_LOCAL', 'RESPALDO_LOCAL', 'RESPALDO_LOCAL', 200],
  STALE: ['RESPALDO_LOCAL', 'RESPALDO_LOCAL', 'RESPALDO_LOCAL', 200],
  CORRUPT: ['RESPALDO_LOCAL', 'RESPALDO_LOCAL', 'SIN_RESPALDO', 503],
  MISSING: ['SIN_RESPALDO', 'SIN_RESPALDO', 'SIN_RESPALDO', 503],
  INVALID: ['SIN_RESPALDO', 'API_DIRECTA_BCRP', 'SIN_RESPALDO', 502],
  EXPIRED: ['SIN_RESPALDO', 'SIN_RESPALDO', 'SIN_RESPALDO', 503],
}[scenario];
assert.ok(expected, 'Unsupported scenario');
const cdp = new URL(cdpArgument);
assert.equal(cdp.protocol, 'http:');
assert.equal(cdp.hostname, '127.0.0.1');
assert.ok(cdp.port && !cdp.username && !cdp.password && !cdp.search && cdp.pathname === '/');
const evidence = await realpath(evidenceArgument);
const temporary = await realpath(os.tmpdir());
const relative = path.relative(temporary, evidence);
assert.ok(relative && !relative.startsWith('..') && !path.isAbsolute(relative));
assert.ok(relative.split(path.sep).some(part => part.startsWith('ecoinvest-lkg-b4-')));
assert.ok((await lstat(evidence)).isDirectory(), 'Evidence directory must already be authorized and created');
const frontend = 'http://127.0.0.1:5173';
const backend = 'http://127.0.0.1:8081';
// Harmless identity preflight, no API data operation. Refuse any ordinary backend.
const identity = await fetch(backend + '/', { method: 'OPTIONS', redirect: 'error', signal: AbortSignal.timeout(3000) });
assert.equal(identity.headers.get('X-EcoInvest-Lab'), 'ARTIFICIAL', 'Only the isolated Java lab is permitted');
const version = await (await fetch(new URL('/json/version', cdp), { signal: AbortSignal.timeout(3000) })).json();
const wsUrl = new URL(version.webSocketDebuggerUrl);
assert.equal(wsUrl.protocol, 'ws:');
assert.equal(wsUrl.hostname, '127.0.0.1');
assert.equal(wsUrl.port, cdp.port);
const socket = new WebSocket(wsUrl);
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
let serial = 0;
const pending = new Map();
const listeners = [];
function send(method, params = {}, sessionId) {
  const id = ++serial;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
    pending.set(id, { resolve, reject, timeout });
    socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
  });
}
socket.onmessage = ({ data }) => {
  const message = JSON.parse(data);
  if (message.id) {
    const handler = pending.get(message.id);
    if (!handler) return;
    pending.delete(message.id); clearTimeout(handler.timeout);
    if (message.error) handler.reject(new Error(message.error.message)); else handler.resolve(message.result);
  } else for (const listener of listeners) listener(message);
};
let targetId;
const apiPaths = ['/api/dashboard', '/api/consultoras', '/api/estado-servicios'];
const budget = new Map(apiPaths.map(value => [value, 0]));
const calls = [];
const violations = [];
const jsErrors = [];
const responses = new Map();
const completed = new Map();
const activeBodies = new Set();
const measurements = [];
const interactions = [];
try {
  ({ targetId } = await send('Target.createTarget', { url: 'about:blank' }));
  const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
  const command = (method, params) => send(method, params, sessionId);
  const evaluate = async expression => {
    const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw new Error('Browser evaluation failed');
    return result.result.value;
  };
  listeners.push(message => {
    if (message.sessionId !== sessionId) return;
    const { method, params } = message;
    if (method === 'Fetch.requestPaused') {
      const url = new URL(params.request.url);
      let allow = url.origin === frontend && !url.pathname.startsWith('/api/');
      if (url.origin === backend && apiPaths.includes(url.pathname) && params.request.method === 'GET') {
        allow = budget.get(url.pathname) > 0;
        if (allow) { budget.set(url.pathname, budget.get(url.pathname) - 1); calls.push(url.pathname); }
      }
      if (!allow) violations.push({ origin: url.origin, path: url.pathname, method: params.request.method });
      void command(allow ? 'Fetch.continueRequest' : 'Fetch.failRequest',
        allow ? { requestId: params.requestId } : { requestId: params.requestId, errorReason: 'BlockedByClient' })
        .catch(() => violations.push({ control: 'interception-failed' }));
    }
    if (method === 'Runtime.exceptionThrown') jsErrors.push('Runtime.exceptionThrown');
    // Expected HTTP 502/503 can produce a network console entry; it is not a JS exception.
    if (method === 'Log.entryAdded' && params.entry.level === 'error' && params.entry.source !== 'network')
      jsErrors.push('Browser log error');
    if (method === 'Network.webSocketCreated') {
      const url = new URL(params.url);
      if (url.protocol !== 'ws:' || url.hostname !== '127.0.0.1' || url.port !== '5173')
        violations.push({ control: 'external-websocket' });
    }
    if (method === 'Network.responseReceived') {
      const url = new URL(params.response.url);
      if (url.origin === backend && apiPaths.includes(url.pathname)) responses.set(params.requestId, {
        path: url.pathname, status: params.response.status,
        headers: Object.fromEntries(Object.entries(params.response.headers).map(([key, value]) => [key.toLowerCase(), value])),
      });
    }
    if (method === 'Network.loadingFinished' && responses.has(params.requestId)) {
      const entry = responses.get(params.requestId);
      const work = command('Network.getResponseBody', { requestId: params.requestId }).then(result => {
        assert.equal(entry.headers['x-ecoinvest-lab'], 'ARTIFICIAL');
        entry.body = JSON.parse(result.base64Encoded ? Buffer.from(result.body, 'base64').toString('utf8') : result.body);
        completed.set(entry.path, entry);
      }).catch(() => violations.push({ control: 'response-validation-failed' }));
      activeBodies.add(work); void work.finally(() => activeBodies.delete(work));
    }
  });
  await command('Page.enable'); await command('Runtime.enable'); await command('Network.enable');
  await command('Page.bringToFront');
  await command('Log.enable');
  await command('Network.setCacheDisabled', { cacheDisabled: true });
  await command('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] });
  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  async function until(expression) {
    const deadline = Date.now() + 12000;
    while (Date.now() < deadline) { if (await evaluate(expression)) return; await pause(80); }
    throw new Error('Expected UI state did not appear');
  }
  async function route(name, selector) {
    const before = calls.length;
    await evaluate(`location.hash=${JSON.stringify('#/' + name)}`);
    await until(`Boolean(document.querySelector(${JSON.stringify(selector)}))`);
    await pause(250);
    assert.equal(calls.length, before, 'Navigation must not issue GET');
  }
  async function click(text, apiPath) {
    completed.delete(apiPath); budget.set(apiPath, 1);
    const clicked = await evaluate(`(() => { const button=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(text)}); if(!button||button.disabled)return false; button.click(); return true; })()`);
    assert.equal(clicked, true, 'Manual HTTP control must exist');
    const deadline = Date.now() + 12000;
    while (!completed.has(apiPath) && Date.now() < deadline) await pause(80);
    assert.ok(completed.has(apiPath), 'Expected HTTP response missing');
    await until(`!document.querySelector('[aria-busy="true"]')`);
    await pause(100);
    return completed.get(apiPath);
  }
  async function responsive(name) {
    for (const [width, height] of [[1440, 900], [768, 1024], [375, 812]]) {
      await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
      await evaluate('scrollTo(0,0)');
      await pause(100);
      const result = await evaluate(`({width:innerWidth,documentWidth:document.documentElement.scrollWidth,rows:document.querySelectorAll('tbody tr').length,points:document.querySelectorAll('.explorer-page svg g[role="button"]').length, mockBanner:document.body.innerText.includes('MODO DEMOSTRACI\u00d3N'),notices:document.querySelectorAll('.lkg-notice').length})`);
      measurements.push({ view: name, viewport: [width, height], ...result });
      assert.ok(result.documentWidth <= width, 'Global horizontal overflow');
      assert.equal(result.mockBanner, false, 'HTTP must not silently switch to React mocks');
      if (name === 'explorador') assert.ok(result.points <= 21, 'SVG point limit must remain bounded');
      const image = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
      await writeFile(path.join(evidence, `${scenario}-${name}-${width}.png`), Buffer.from(image.data, 'base64'), { flag: 'wx' });
      const panel = name === 'monitor' ? '.monitor-lkg' : '.lkg-notice';
      if (await evaluate(`Boolean(document.querySelector(${JSON.stringify(panel)}))`)) {
        await evaluate(`document.querySelector(${JSON.stringify(panel)}).scrollIntoView({block:'start',behavior:'instant'})`);
        const lower = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
        await writeFile(path.join(evidence, `${scenario}-${name}-${width}-lkg.png`), Buffer.from(lower.data, 'base64'), { flag: 'wx' });
        await evaluate('scrollTo(0,0)');
      }
    }
  }
  await command('Page.navigate', { url: frontend + '/#/dashboard' });
  await until("Boolean(document.querySelector('.dashboard-page'))");
  await pause(300); assert.equal(calls.length, 0);
  // Native setter dispatches input so React controls retain the effective query.
  await evaluate(`(() => {const input=document.querySelector('input[list="dashboard-sector-suggestions"]');const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,'Hidrocarburos');input.dispatchEvent(new Event('input',{bubbles:true}));})()`);
  const dashboard = await click('Consultar Dashboard por HTTP', '/api/dashboard');
  assert.equal(dashboard.status, 200);
  assert.equal(dashboard.body.consultaSenace.origen, expected[0]);
  assert.equal(dashboard.body.indicadorBcrp.origen, expected[1]);
  for (const [origin, metadata] of [[expected[0], dashboard.body.consultaSenace.lkg], [expected[1], dashboard.body.indicadorBcrp.lkg]]) {
    if (origin === 'RESPALDO_LOCAL') { assert.ok(metadata?.respaldo?.capturadoEn); assert.equal(metadata.lectura, 'AVAILABLE'); }
  }
  if (scenario === 'STALE') assert.equal(dashboard.body.consultaSenace.lkg.respaldo.antiguo, true);
  if (scenario === 'EXPIRED') {
    assert.equal(dashboard.body.consultaSenace.lkg.lectura, 'EXPIRED');
    assert.equal(dashboard.body.indicadorBcrp.lkg.lectura, 'EXPIRED');
  }
  await responsive('dashboard');
  async function setInput(selector, value) {
    await evaluate(`(() => {const input=document.querySelector(${JSON.stringify(selector)});const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,${JSON.stringify(value)});input.dispatchEvent(new Event('input',{bubbles:true}));})()`);
    await pause(100);
  }
  if (dashboard.body.proyectos?.length === 45) {
    const before = calls.length;
    await setInput('.dash-search input', 'PROYECTO ARTIFICIAL 45');
    assert.equal(await evaluate("document.querySelectorAll('.dash-table tbody tr').length"), 1);
    await setInput('.dash-search input', '');
    await evaluate("[...document.querySelectorAll('.dash-pagination button')].find(b=>b.textContent==='Siguiente').click()");
    await pause(100);
    assert.ok(await evaluate("document.querySelector('.dash-pagination').textContent.includes('2')"));
    assert.equal(calls.length, before); interactions.push('dashboard-search-pagination-local');
  }
  await route('explorador', '.explorer-page');
  assert.equal(calls.filter(value => value === '/api/dashboard').length, 1);
  await responsive('explorador');
  if (dashboard.body.proyectos?.length === 45) {
    const before = calls.length;
    assert.equal(await evaluate("document.querySelectorAll('.explorer-list li').length"), 20);
    await evaluate("document.querySelector('.explorer-list li button').focus()");
    await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13, text: '\r', unmodifiedText: '\r' });
    await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
    await until("Boolean(document.querySelector('.explorer-detail-content'))");
    await setInput('.explorer-list input[type="search"]', 'PROYECTO ARTIFICIAL 45');
    assert.equal(await evaluate("document.querySelectorAll('.explorer-list li').length"), 1);
    await setInput('.explorer-list input[type="search"]', '');
    assert.equal(calls.length, before); interactions.push('explorer-keyboard-selection-search-local');
  }
  await route('consultoras', '.consultants-page');
  const consultants = await click('Consultar Consultoras por HTTP', '/api/consultoras');
  assert.equal(consultants.status, expected[3]);
  assert.equal(consultants.headers['x-senace-origen'], expected[2]);
  assert.ok(Array.isArray(consultants.body));
  if (scenario === 'STALE') assert.equal(consultants.headers['x-senace-respaldo-antiguo'], 'true');
  if (scenario === 'CORRUPT') assert.equal(consultants.headers['x-senace-snapshot-lectura'], 'INVALID');
  const visibleHeader = await evaluate(`(() => {const items=[...document.querySelectorAll('dd')];return items.some(item=>item.textContent.includes(${JSON.stringify(expected[2])}))})()`);
  assert.equal(visibleHeader, true, 'UI must read exposed provenance header');
  await responsive('consultoras');
  if (consultants.body.length === 24) {
    const before = calls.length;
    assert.equal(await evaluate("document.querySelectorAll('.consultants-table tbody tr').length"), 10);
    await evaluate("[...document.querySelectorAll('.consultants-pagination button')].find(b=>b.textContent==='Siguiente').click()");
    await pause(100);
    await evaluate("[...document.querySelectorAll('.consultants-pagination button')].find(b=>b.textContent==='Siguiente').click()");
    await pause(100);
    assert.equal(await evaluate("document.querySelectorAll('.consultants-table tbody tr').length"), 4);
    await evaluate("[...document.querySelectorAll('.consultants-table button')].find(b=>b.textContent==='Ver detalle').click()");
    await until("Boolean(document.querySelector('.consultants-detail-body'))");
    await evaluate("[...document.querySelectorAll('.consultants-detail button')].find(b=>b.textContent==='Cerrar detalle').click()");
    await pause(100);
    assert.equal(await evaluate("Boolean(document.querySelector('.consultants-detail-body'))"), false);
    assert.equal(calls.length, before); interactions.push('consultants-pagination-detail-local');
  }
  await route('monitor', '.monitor-page');
  const monitor = await click('Consultar Monitor por HTTP', '/api/estado-servicios');
  assert.equal(monitor.status, 200); assert.ok(monitor.body.lkg);
  assert.equal(monitor.body.senace.disponibilidadExterna, 'NO_VERIFICADA');
  await responsive('monitor');
  assert.deepEqual(calls, apiPaths);
  await Promise.all([...activeBodies]);
  assert.deepEqual(violations, []); assert.deepEqual(jsErrors, []);
  await writeFile(path.join(evidence, `${scenario}-summary.json`), JSON.stringify({
    artificial: true, realLocalHttp: true, scenario, calls, violations, jsErrors, measurements, interactions,
    dashboard: { status: dashboard.status, senace: expected[0], bcrp: expected[1] },
    consultants: { status: consultants.status, origin: expected[2], receivedRows: consultants.body.length },
    monitor: { module: monitor.body.lkg.estado, availability: 'NO_VERIFICADA' },
    limits: ['Synthetic upstream data', 'Screenshots require visual review', 'Not a complete WCAG audit', 'Browser-scoped network observation'],
  }, null, 2), { flag: 'wx' });
  console.log('LKG_LOCAL_HTTP_PASS — ARTIFICIAL UPSTREAM DATA; review screenshots separately.');
} finally {
  if (targetId) await send('Target.closeTarget', { targetId }).catch(() => {});
  socket.close();
  for (const handler of pending.values()) { clearTimeout(handler.timeout); handler.reject(new Error('CDP closed')); }
}
