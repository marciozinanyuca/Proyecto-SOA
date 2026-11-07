export type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
export type TransportIssue = 'CONFIGURACION_AUSENTE' | 'CONFIGURACION_INVALIDA' | 'PARAMETROS_INVALIDOS' | 'RED' | 'FORMATO' | 'CANCELADA';
export interface HttpResult {
  mode: 'http' | 'mock';
  status: number | null;
  headers: Readonly<Record<string, string>>;
  /** Nunca imprimir este texto: puede contener datos personales. Solo memoria. */
  rawText?: string;
  json?: unknown;
  issue?: TransportIssue;
}
export interface ApiClient {
  readonly mode: 'http' | 'mock';
  dashboard(sector: string, anio?: number, signal?: AbortSignal): Promise<HttpResult>;
  consultoras(signal?: AbortSignal): Promise<HttpResult>;
  datastream(nombre?: string, signal?: AbortSignal): Promise<HttpResult>;
  estadoServicios(signal?: AbortSignal): Promise<HttpResult>;
}
type Options = { mode: 'http'; baseUrl: string | null; fetcher?: FetchLike }
  | { mode: 'mock'; fetcher: FetchLike };

/** No realiza operaciones al construir. Sin redirecciones, reintentos o mocks implicitos. */
export function createApiClient(options: Options): ApiClient {
  const transport = options.fetcher ?? globalThis.fetch.bind(globalThis);
  async function get(path: string, params: Record<string, string>, signal?: AbortSignal): Promise<HttpResult> {
    const base = { mode: options.mode, status: null, headers: {} } as const;
    if (signal?.aborted) return { ...base, issue: 'CANCELADA' };
    if (options.mode === 'http' && !options.baseUrl) return { ...base, issue: 'CONFIGURACION_AUSENTE' };
    let url: URL;
    try {
      // mock.invalid nunca se resuelve: el transporte mock se inyecta obligatoriamente.
      url = new URL(options.mode === 'mock' ? 'https://mock.invalid' : options.baseUrl!);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
        return { ...base, issue: 'CONFIGURACION_INVALIDA' };
      }
      url.pathname = url.pathname.replace(/\/$/, '') + path;
      for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
    } catch { return { ...base, issue: 'CONFIGURACION_INVALIDA' }; }
    let response: Response;
    try {
      response = await transport(url, { method: 'GET', signal, redirect: 'error', credentials: 'omit', headers: { Accept: 'application/json' } });
    } catch (error) {
      return { ...base, issue: signal?.aborted || (error instanceof Error && error.name === 'AbortError') ? 'CANCELADA' : 'RED' };
    }
    const headers: Record<string, string> = {};
    for (const name of ['content-type', 'x-senace-origen', 'x-senace-estado', 'x-senace-cobertura', 'x-senace-motivo', 'x-senace-cuerpo-completo']) {
      const value = response.headers.get(name);
      if (value !== null) headers[name] = value;
    }
    const received = { mode: options.mode, status: response.status, headers };
    let rawText: string;
    try { rawText = await response.text(); }
    catch { return { ...received, issue: signal?.aborted ? 'CANCELADA' : 'RED' }; }
    if (signal?.aborted) return { ...received, issue: 'CANCELADA' };
    try { return { ...received, rawText, json: JSON.parse(rawText) as unknown }; }
    catch { return { ...received, rawText, issue: 'FORMATO' }; }
  }
  return {
    mode: options.mode,
    dashboard(sector, anio, signal) {
      if (!sector.trim() || (anio !== undefined && !Number.isSafeInteger(anio))) {
        return Promise.resolve({ mode: options.mode, status: null, headers: {}, issue: 'PARAMETROS_INVALIDOS' });
      }
      return get('/api/dashboard', { sector, ...(anio === undefined ? {} : { anio: String(anio) }) }, signal);
    },
    consultoras: (signal) => get('/api/consultoras', {}, signal),
    datastream: (nombre, signal) => get('/api/senace/datastream', nombre === undefined ? {} : { nombre }, signal),
    estadoServicios: (signal) => get('/api/estado-servicios', {}, signal),
  };
}
