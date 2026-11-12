import { createApiClient } from '../../api/client.js';
import { createDemoSelectionStore } from '../../api/demoSelectionStore.js';
import { createDashboardSelectionStore } from '../../api/dashboardSelectionStore.js';
import { createDashboardSource } from '../../api/source.js';
import type { SourceConfig } from '../../api/source.js';
import type { FetchLike, HttpResult } from '../../api/client.js';
import { adaptDashboard } from '../../api/adapters/dashboard.js';
import type { Adapted, DashboardResponse, ProyectoSenacePublico } from '../../api/types.js';
import { ageLabel, captureLabel, usableSenace } from '../../api/lkg.js';
import { createMockFetch } from '../../mocks/mockFetch.js';
import type { DashboardDemoState, Scenario } from '../../mocks/fixtures.js';

export interface DashboardQuery { sector: string; anio: number }
export interface DashboardSnapshot {
  query: DashboardQuery; scenario?: DashboardDemoState; http: HttpResult; result: Adapted<DashboardResponse>; receivedAt?: string;
}
export const dashboardDemoStore = createDemoSelectionStore<DashboardSnapshot>();
export const dashboardHttpStore = createDashboardSelectionStore<DashboardSnapshot>();

/** Avisos de esta respuesta, no del estado global ni de una consulta nueva al Monitor. */
export function dashboardBackupWarnings(data: DashboardResponse | undefined): string[] {
  const warnings: string[] = [];
  for (const [source, origin, metadata] of [
    ['SENACE', data?.consultaSenace?.origen, data?.consultaSenace?.lkg],
    ['BCRP', data?.indicadorBcrp?.origen, data?.indicadorBcrp?.lkg],
  ] as const) {
    if (origin === 'RESPALDO_LOCAL') {
      const stored = metadata?.respaldo;
      const capture = stored?.capturadoEn ? ` · captura ${captureLabel(stored.capturadoEn)}` : '';
      const age = stored?.edadSegundos !== undefined ? ` · edad ${ageLabel(stored.edadSegundos)}` : '';
      warnings.push(`${source}: respaldo local${capture}${age}${stored?.antiguo ? ' · respaldo antiguo' : ''}. No es una consulta nueva satisfactoria al proveedor.`);
    }
    if (metadata?.escritura && ['IO_ERROR', 'INVALID', 'ATOMIC_MOVE_UNSUPPORTED', 'OWNERSHIP_UNAVAILABLE'].includes(metadata.escritura)) {
      warnings.push(`${source}: no se pudo actualizar el respaldo. Esto no invalida los datos directos recibidos.`);
    }
  }
  return warnings;
}
export const initialQuery: Readonly<DashboardQuery> = { sector: 'Hidrocarburos', anio: 2017 };
export const sectors = ['Hidrocarburos', 'Minería', 'TODOS'] as const;
export const years = [2017, 2018] as const;
/** Sugerencias de claves BCRP existentes; no catálogo oficial de actividades SENACE. */
export const httpSectorSuggestions = ['TODOS', 'Agricultura', 'Pesca', 'Minería', 'Hidrocarburos', 'Electricidad', 'Transportes', 'Energia'] as const;
export const scenarios: readonly { value: DashboardDemoState; label: string }[] = [
  { value: 'success', label: 'Respuesta satisfactoria' }, { value: 'empty', label: 'Respuesta oficial vacía (simulada)' },
  { value: 'noMatches', label: 'Sin coincidencias' }, { value: 'degraded', label: 'SENACE indisponible · BCRP disponible' },
  { value: 'config', label: 'Configuración SENACE ausente' }, { value: 'dates', label: 'Fechas no evaluables' },
  { value: 'optional', label: 'Campos opcionales ausentes' }, { value: 'error', label: 'Error HTTP artificial' },
  { value: 'lkgSenace', label: 'LKG artificial · respaldo SENACE' },
  { value: 'lkgBcrp', label: 'LKG artificial · respaldo BCRP' },
  { value: 'lkgBoth', label: 'LKG artificial · ambas fuentes' },
  { value: 'lkgOld', label: 'LKG artificial · respaldo antiguo' },
  { value: 'lkgMissing', label: 'LKG artificial · sin respaldo' },
  { value: 'lkgWriteFailure', label: 'LKG artificial · API directa, escritura fallida' },
];
/** Reutiliza el transporte mock aprobado. Ningun camino construye mode:http. */
export async function loadDashboardDemo(query: DashboardQuery, scenario: DashboardDemoState, signal?: AbortSignal): Promise<DashboardSnapshot> {
  if (!(sectors as readonly string[]).includes(query.sector) || !(years as readonly number[]).includes(query.anio)) throw new Error('SELECCION_MOCK_NO_SOPORTADA');
  const key = `dashboardDemo:${query.sector}:${query.anio}:${scenario}` as Scenario;
  return dashboardDemoStore.get(key, async sharedSignal => {
    const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/dashboard': key }, 300) });
    const http = await api.dashboard(query.sector, query.anio, sharedSignal);
    if (http.issue === 'CANCELADA') throw new DOMException('Cancelada', 'AbortError');
    return { query: { ...query }, scenario, http, result: adaptDashboard(http) };
  }, signal);
}

/** Se invoca solo por una acción explícita. No reutiliza la caché de fixtures. */
export async function loadDashboardHttp(query: DashboardQuery, config: SourceConfig, signal?: AbortSignal,
    fetcher?: FetchLike, store = dashboardHttpStore): Promise<DashboardSnapshot> {
  if (!config.valid || config.mode !== 'http') throw new Error('CONFIGURACION_HTTP_INVALIDA');
  const api = createDashboardSource(config, 'dashboardSuccess', fetcher);
  const ticket = store.begin();
  const effective = { ...query };
  const http = await api.dashboard(effective.sector, effective.anio, signal);
  if (signal?.aborted || http.issue === 'CANCELADA') throw new DOMException('Cancelada', 'AbortError');
  const snapshot: DashboardSnapshot = { query: effective, http, result: adaptDashboard(http), receivedAt: new Date().toISOString() };
  // Incluso un error sustituye la selección previa: no conservar un éxito como si fuera reciente.
  store.publish(ticket, snapshot);
  return snapshot;
}
/** Desconocido no se convierte en lista vacia ni se agrega como cero. */
export function knownProjects(result: Adapted<DashboardResponse>): ProyectoSenacePublico[] | undefined {
  return result.state !== 'error' && usableSenace(result.data?.consultaSenace) ? result.data?.proyectos : undefined;
}
export function distribution(rows: readonly ProyectoSenacePublico[] | undefined): { estado: string; count: number }[] | undefined {
  if (rows === undefined) return undefined;
  const counts = new Map<string, number>();
  for (const row of rows) { const state = row.estado?.trim() || 'Estado no disponible'; counts.set(state, (counts.get(state) ?? 0) + 1); }
  return [...counts].map(([estado, count]) => ({ estado, count }));
}
function searchable(value: string): string { return value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es').trim(); }
export function selectTable(rows: readonly ProyectoSenacePublico[] | undefined, search: string, page: number, pageSize = 3) {
  if (!Number.isSafeInteger(pageSize) || pageSize < 1) throw new Error('TAMANO_PAGINA_INVALIDO');
  if (rows === undefined) return { rows: undefined, total: undefined, page: 1, pages: 0, start: undefined, end: undefined };
  const term = searchable(search);
  const matches = rows.filter((row) => [row.titular, row.ruc, row.titulo, row.proyecto, row.actividad, row.estado, row.fechaInicio]
    .some((value) => typeof value === 'string' && searchable(value).includes(term)));
  const pages = Math.ceil(matches.length / pageSize);
  const current = Math.min(Math.max(1, Number.isSafeInteger(page) ? page : 1), Math.max(1, pages));
  return { rows: matches.slice((current - 1) * pageSize, current * pageSize), total: matches.length, page: current, pages,
    start: matches.length ? (current - 1) * pageSize + 1 : 0, end: Math.min(current * pageSize, matches.length) };
}
export const displayValue = (value: number | undefined): string => value === undefined ? 'No disponible' : value.toLocaleString('es-PE');
export function stateMessage(result: Adapted<DashboardResponse>, mode: 'mock' | 'http' = 'mock'): string {
  if (result.state !== 'error' && (result.data?.consultaSenace?.origen === 'RESPALDO_LOCAL' || result.data?.indicadorBcrp?.origen === 'RESPALDO_LOCAL')) {
    return `${mode === 'mock' ? 'Escenario artificial: ' : ''}datos disponibles mediante respaldo local. Revisa por separado captura, antigüedad e intento API de SENACE y BCRP; no se acredita disponibilidad externa actual.`;
  }
  if (mode === 'http') {
    if (result.state === 'error') return 'No se obtuvo una respuesta utilizable del backend. No se activan mocks.';
    if (result.reason === 'RESPUESTA_OFICIAL_VACIA') return 'SENACE informó una respuesta oficial vacía.';
    if (result.reason === 'SIN_COINCIDENCIAS') return 'No hay registros que coincidan con la actividad y el año de inicio consultados.';
    if (result.state === 'degraded') return 'Respuesta degradada: revisa por separado SENACE, BCRP y la calidad temporal.';
    return 'Respuesta del backend disponible. Describe la selección recibida, no cobertura oficial completa.';
  }
  if (result.state === 'error') return 'No se pudo obtener un resultado utilizable de la demostración.';
  if (result.reason === 'RESPUESTA_OFICIAL_VACIA') return 'El escenario simula una respuesta SENACE oficial vacía.';
  if (result.reason === 'SIN_COINCIDENCIAS') return 'La consulta simulada no tiene registros que coincidan con los filtros.';
  if (result.reason === 'FECHAS_NO_EVALUABLES') return 'Hay fechas no evaluables. Los conteos describen solo los registros evaluables de la selección.';
  if (result.state === 'degraded') return 'Información parcial: algunas fuentes o campos no están disponibles en este escenario.';
  return 'Selección disponible. Todos los registros e indicadores mostrados son artificiales.';
}
