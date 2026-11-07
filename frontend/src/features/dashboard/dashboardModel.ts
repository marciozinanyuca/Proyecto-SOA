import { createApiClient } from '../../api/client.js';
import { createDemoSelectionStore } from '../../api/demoSelectionStore.js';
import type { HttpResult } from '../../api/client.js';
import { adaptDashboard } from '../../api/adapters/dashboard.js';
import type { Adapted, DashboardResponse, ProyectoSenacePublico } from '../../api/types.js';
import { official } from '../../api/validation.js';
import { createMockFetch } from '../../mocks/mockFetch.js';
import type { DashboardDemoSector, DashboardDemoState, DashboardDemoYear, Scenario } from '../../mocks/fixtures.js';

export interface DashboardQuery { sector: DashboardDemoSector; anio: DashboardDemoYear }
export interface DashboardSnapshot {
  query: DashboardQuery; scenario: DashboardDemoState; http: HttpResult; result: Adapted<DashboardResponse>;
}
export const dashboardDemoStore = createDemoSelectionStore<DashboardSnapshot>();
export const initialQuery: Readonly<DashboardQuery> = { sector: 'Hidrocarburos', anio: 2017 };
export const sectors = ['Hidrocarburos', 'Minería', 'TODOS'] as const;
export const years = [2017, 2018] as const;
export const scenarios: readonly { value: DashboardDemoState; label: string }[] = [
  { value: 'success', label: 'Respuesta satisfactoria' }, { value: 'empty', label: 'Respuesta oficial vacía (simulada)' },
  { value: 'noMatches', label: 'Sin coincidencias' }, { value: 'degraded', label: 'SENACE indisponible · BCRP disponible' },
  { value: 'config', label: 'Configuración SENACE ausente' }, { value: 'dates', label: 'Fechas no evaluables' },
  { value: 'optional', label: 'Campos opcionales ausentes' }, { value: 'error', label: 'Error HTTP artificial' },
];
/** Reutiliza el transporte mock aprobado. Ningun camino construye mode:http. */
export async function loadDashboardDemo(query: DashboardQuery, scenario: DashboardDemoState, signal?: AbortSignal): Promise<DashboardSnapshot> {
  const key: Scenario = `dashboardDemo:${query.sector}:${query.anio}:${scenario}`;
  return dashboardDemoStore.get(key, async sharedSignal => {
    const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/dashboard': key }, 300) });
    const http = await api.dashboard(query.sector, query.anio, sharedSignal);
    if (http.issue === 'CANCELADA') throw new DOMException('Cancelada', 'AbortError');
    return { query: { ...query }, scenario, http, result: adaptDashboard(http) };
  }, signal);
}
/** Desconocido no se convierte en lista vacia ni se agrega como cero. */
export function knownProjects(result: Adapted<DashboardResponse>): ProyectoSenacePublico[] | undefined {
  return result.state !== 'error' && official(result.data?.consultaSenace) ? result.data?.proyectos : undefined;
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
export function stateMessage(result: Adapted<DashboardResponse>): string {
  if (result.state === 'error') return 'No se pudo obtener un resultado utilizable de la demostración.';
  if (result.reason === 'RESPUESTA_OFICIAL_VACIA') return 'El escenario simula una respuesta SENACE oficial vacía.';
  if (result.reason === 'SIN_COINCIDENCIAS') return 'La consulta simulada no tiene registros que coincidan con los filtros.';
  if (result.reason === 'FECHAS_NO_EVALUABLES') return 'Hay fechas no evaluables. Los conteos describen solo los registros evaluables de la selección.';
  if (result.state === 'degraded') return 'Información parcial: algunas fuentes o campos no están disponibles en este escenario.';
  return 'Selección disponible. Todos los registros e indicadores mostrados son artificiales.';
}
