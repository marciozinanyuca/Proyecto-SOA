import type { ProyectoSenacePublico } from '../../api/types.js';
import { createApiClient } from '../../api/client.js';
import { adaptDashboard } from '../../api/adapters/dashboard.js';
import { createMockFetch } from '../../mocks/mockFetch.js';
import { createDemoSelectionStore } from '../../api/demoSelectionStore.js';
import { dashboardDemoStore, initialQuery, knownProjects, loadDashboardDemo } from '../dashboard/dashboardModel.js';
import type { DashboardQuery, DashboardSnapshot } from '../dashboard/dashboardModel.js';
import type { DashboardDemoState } from '../../mocks/fixtures.js';

export type ExplorerScenario = DashboardDemoState | 'coordinates';
const geoStore = createDemoSelectionStore<DashboardSnapshot>();
export const previousSelection = () => dashboardDemoStore.peek();
export function initialSelection() { return previousSelection()?.query ?? { ...initialQuery }; }
export async function loadExplorer(query: DashboardQuery, scenario: ExplorerScenario, signal?: AbortSignal) {
  if (scenario !== 'coordinates') return loadDashboardDemo(query, scenario, signal);
  return geoStore.get(`geo:${query.sector}:${query.anio}`, async sharedSignal => {
    const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/dashboard': `exploradorDemo:${query.sector}:${query.anio}` }, 300) });
    const http = await api.dashboard(query.sector, query.anio, sharedSignal);
    if (http.issue === 'CANCELADA') throw new DOMException('Cancelada', 'AbortError');
    return { query: { ...query }, scenario: 'success', http, result: adaptDashboard(http) };
  }, signal);
}
export type CoordinateState = 'UTILIZABLES' | 'AUSENTES_O_INCOMPLETAS' | 'FUERA_DE_RANGO';
export function coordinateState(row: ProyectoSenacePublico): CoordinateState {
  if (row.latitud === undefined || row.longitud === undefined) return 'AUSENTES_O_INCOMPLETAS';
  return Number.isFinite(row.latitud) && Number.isFinite(row.longitud) && Math.abs(row.latitud) <= 90 && Math.abs(row.longitud) <= 180 ? 'UTILIZABLES' : 'FUERA_DE_RANGO';
}
/** Plano equirectangular global ilustrativo, no certifica CRS ni localización. */
export function point(row: ProyectoSenacePublico) {
  return coordinateState(row) === 'UTILIZABLES'
    ? { x: (row.longitud! + 180) / 360 * 100, y: (90 - row.latitud!) / 180 * 100 } : undefined;
}
export function projectEntries(snapshot: DashboardSnapshot | undefined, state = '', search = '') {
  const rows = snapshot ? knownProjects(snapshot.result) : undefined;
  if (rows === undefined) return undefined;
  const normalize = (text: string) => text.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es').trim();
  const term = normalize(search);
  return rows.map((row, index) => ({ row, key: String(index) })).filter(({ row }) =>
    (!state || (row.estado ?? 'No disponible') === state) && (!term || [row.titulo, row.proyecto, row.titular, row.ruc, row.actividad].some(value => typeof value === 'string' && normalize(value).includes(term))));
}
export function selectEntry(entries: ReturnType<typeof projectEntries>, key: string | undefined) {
  return entries?.find(entry => entry.key === key);
}
