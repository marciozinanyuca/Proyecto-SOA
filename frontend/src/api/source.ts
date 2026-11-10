import { createApiClient } from './client.js';
import type { ApiClient, FetchLike, HttpResult } from './client.js';
import { createMockFetch } from '../mocks/mockFetch.js';
import type { Scenario } from '../mocks/fixtures.js';

export interface PublicSourceEnv { VITE_API_MODE?: string; VITE_API_BASE_URL?: string; VITE_HTTP_FEATURES?: string }
export type SourceConfig =
  | { valid: true; mode: 'mock'; baseUrl: string | null; httpFeatures: readonly [] }
  | { valid: true; mode: 'http'; baseUrl: string; httpFeatures: readonly ['monitor'] | readonly ['monitor', 'consultoras'] | readonly ['monitor', 'consultoras', 'dashboard', 'explorador'] }
  | { valid: false; reason: 'MODO_INVALIDO' | 'URL_INVALIDA' | 'URL_AUSENTE' | 'FEATURES_INVALIDAS' };
export type SourceFeature = 'dashboard' | 'explorador' | 'consultoras' | 'monitor';

/** Describe configuración, nunca disponibilidad ni resultados de una consulta. */
export function sourcePresentation(config: SourceConfig) {
  const entitiesLabel = 'Entidades de referencia SENACE y BCRP; sus logos no acreditan disponibilidad externa';
  if (!config.valid) return {
    stage: 'Configuración inválida', heading: 'CONSULTAS BLOQUEADAS',
    description: 'Revisa la configuración pública antes de consultar.',
    note: 'No se activa una fuente alternativa.', entitiesLabel,
  };
  if (config.mode === 'mock') return {
    stage: 'Demostración · datos artificiales', heading: 'MODO DEMOSTRACIÓN',
    description: 'Respuestas artificiales identificadas en cada vista.',
    note: 'Sin consultas a proveedores.', entitiesLabel,
  };
  return {
    stage: 'Modo HTTP seleccionado', heading: 'BACKEND LOCAL CONFIGURADO',
    description: 'Consulta manual; resultados y procedencia en cada vista.',
    note: 'La configuración no acredita conexión activa ni disponibilidad externa.', entitiesLabel,
  };
}

/** Solo configuracion publica; no importa ni evalua archivos del backend. */
export function parseSourceConfig(env: PublicSourceEnv): SourceConfig {
  const mode = env.VITE_API_MODE === undefined ? 'mock' : env.VITE_API_MODE.trim();
  if (mode !== 'mock' && mode !== 'http') return { valid: false, reason: 'MODO_INVALIDO' };
  const features = env.VITE_HTTP_FEATURES?.trim() ?? '';
  if ((mode === 'http' && !['monitor', 'monitor,consultoras', 'monitor,consultoras,dashboard,explorador'].includes(features)) || (mode === 'mock' && features !== '')) {
    return { valid: false, reason: 'FEATURES_INVALIDAS' };
  }
  const rawUrl = env.VITE_API_BASE_URL?.trim();
  if (!rawUrl && mode === 'http') return { valid: false, reason: 'URL_AUSENTE' };
  let baseUrl: string | null = null;
  if (rawUrl) {
    try {
      const url = new URL(rawUrl);
      // Integracion progresiva: solo destino local y base sin prefijo /api.
      if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.port !== '8081'
          || url.pathname !== '/' || url.search || url.hash || url.username || url.password) {
        return { valid: false, reason: 'URL_INVALIDA' };
      }
      baseUrl = url.origin;
    } catch { return { valid: false, reason: 'URL_INVALIDA' }; }
  } else if (env.VITE_API_BASE_URL !== undefined && mode === 'mock' && env.VITE_API_BASE_URL !== '') {
    return { valid: false, reason: 'URL_INVALIDA' };
  }
  return mode === 'http'
    ? { valid: true, mode, baseUrl: baseUrl!, httpFeatures: features === 'monitor,consultoras,dashboard,explorador' ? ['monitor', 'consultoras', 'dashboard', 'explorador'] : features === 'monitor,consultoras' ? ['monitor', 'consultoras'] : ['monitor'] }
    : { valid: true, mode, baseUrl, httpFeatures: [] };
}

export function featureAccess(config: SourceConfig, feature: SourceFeature): { allowed: boolean; message?: string } {
  if (!config.valid) return { allowed: false, message: `Configuración pública inválida (${config.reason}). No se realizaron consultas ni se activaron mocks.` };
  if (config.mode === 'http' && !config.httpFeatures.some(value => value === feature)) return { allowed: false, message: 'Integración HTTP no habilitada para esta pantalla. Solo se permiten las vistas expresamente configuradas; no se ejecutan consultas ni se sustituye la fuente por mocks.' };
  return { allowed: true };
}

/** Capacidad por operacion sobre el cliente existente; sin transporte paralelo. */
function createRestrictedSource(config: SourceConfig, feature: 'monitor' | 'consultoras' | 'dashboard', scenario: Scenario, fetcher?: FetchLike): ApiClient {
  if (!config.valid || !featureAccess(config, feature).allowed) throw new Error('CONFIGURACION_PUBLICA_INVALIDA');
  const path = feature === 'monitor' ? '/api/estado-servicios' : feature === 'consultoras' ? '/api/consultoras' : '/api/dashboard';
  if (config.mode === 'mock') return createApiClient({ mode: 'mock', fetcher: createMockFetch({ [path]: scenario }, 300) });
  const transport = fetcher ?? globalThis.fetch.bind(globalThis);
  const expectedUrl = `${config.baseUrl}${path}`;
  const guarded: FetchLike = (input, init) => {
    const url = input instanceof Request ? input.url : String(input);
    const parsed = new URL(url);
    const allowed = feature === 'dashboard'
      ? parsed.origin + parsed.pathname === expectedUrl && !parsed.hash && !parsed.username && !parsed.password
        && [...parsed.searchParams.keys()].every(key => key === 'sector' || key === 'anio')
        && parsed.searchParams.getAll('sector').length === 1 && !!parsed.searchParams.get('sector')?.trim()
        && parsed.searchParams.getAll('anio').length <= 1
        && (!parsed.searchParams.has('anio') || /^-?\d+$/.test(parsed.searchParams.get('anio')!))
      : url === expectedUrl;
    if (!allowed || init?.method !== 'GET') throw new Error('RUTA_HTTP_NO_AUTORIZADA');
    return transport(input, init);
  };
  const client = createApiClient({ mode: 'http', baseUrl: config.baseUrl, fetcher: guarded });
  const blocked = (): Promise<HttpResult> => Promise.resolve({ mode: 'http', status: null, headers: {}, issue: 'CONFIGURACION_INVALIDA' });
  return { mode: 'http', estadoServicios: feature === 'monitor' ? client.estadoServicios : blocked,
    dashboard: feature === 'dashboard' ? client.dashboard : blocked, consultoras: feature === 'consultoras' ? client.consultoras : blocked, datastream: blocked };
}

export function createMonitorSource(config: SourceConfig, scenario: Scenario, fetcher?: FetchLike): ApiClient {
  return createRestrictedSource(config, 'monitor', scenario, fetcher);
}

export function createConsultorasSource(config: SourceConfig, scenario: Scenario, fetcher?: FetchLike): ApiClient {
  return createRestrictedSource(config, 'consultoras', scenario, fetcher);
}

export function createDashboardSource(config: SourceConfig, scenario: Scenario, fetcher?: FetchLike): ApiClient {
  return createRestrictedSource(config, 'dashboard', scenario, fetcher);
}
