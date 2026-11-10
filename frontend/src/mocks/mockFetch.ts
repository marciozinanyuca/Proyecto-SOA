import type { FetchLike } from '../api/client.js';
import { fixture } from './fixtures.js';
import type { Scenario } from './fixtures.js';

type MockPath = '/api/dashboard' | '/api/consultoras' | '/api/senace/datastream' | '/api/estado-servicios';
/** Transporte artificial puro: no delega a fetch global, ni siquiera ante rutas desconocidas. */
export function createMockFetch(scenarios: Partial<Record<MockPath, Scenario>>, delayMs = 0): FetchLike {
  return async (input, init) => {
    if (init?.signal?.aborted) throw new DOMException('Cancelada', 'AbortError');
    const url = new URL(input instanceof Request ? input.url : String(input));
    const scenario = scenarios[url.pathname as MockPath];
    if (!scenario) throw new Error('RUTA_MOCK_NO_CONFIGURADA');
    if (delayMs > 0) await new Promise<void>((resolve, reject) => {
      const onAbort = () => { clearTimeout(timer); init?.signal?.removeEventListener('abort', onAbort); reject(new DOMException('Cancelada', 'AbortError')); };
      const timer = setTimeout(() => { init?.signal?.removeEventListener('abort', onAbort); resolve(); }, delayMs);
      init?.signal?.addEventListener('abort', onAbort, { once: true });
      if (init?.signal?.aborted) onAbort();
    });
    const data = fixture(scenario);
    return new Response(JSON.stringify(data.body), { status: data.status,
      headers: { 'Content-Type': 'application/json', 'X-EcoInvest-Mock': 'true', ...data.headers } });
  };
}
