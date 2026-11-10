import type { ProyectoSenacePublico } from '../../api/types.js';
import { createApiClient } from '../../api/client.js';
import { adaptDashboard } from '../../api/adapters/dashboard.js';
import { createMockFetch } from '../../mocks/mockFetch.js';
import { createDemoSelectionStore } from '../../api/demoSelectionStore.js';
import { dashboardDemoStore, dashboardHttpStore, initialQuery, knownProjects, loadDashboardDemo } from '../dashboard/dashboardModel.js';
import type { DashboardQuery, DashboardSnapshot } from '../dashboard/dashboardModel.js';
import type { DashboardDemoState } from '../../mocks/fixtures.js';

export type ExplorerScenario = DashboardDemoState | 'coordinates';
const geoStore = createDemoSelectionStore<DashboardSnapshot>();
export const previousSelection = () => dashboardDemoStore.peek();
/** Lectura sin carga: el Explorador HTTP nunca completa ni refresca una selección. */
export const previousHttpSelection = () => dashboardHttpStore.peek();
export function initialSelection() { return previousSelection()?.query ?? { ...initialQuery }; }
export async function loadExplorer(query: DashboardQuery, scenario: ExplorerScenario, signal?: AbortSignal) {
  if (scenario !== 'coordinates') return loadDashboardDemo(query, scenario, signal);
  return geoStore.get(`geo:${query.sector}:${query.anio}`, async sharedSignal => {
    const key = `exploradorDemo:${query.sector}:${query.anio}` as import('../../mocks/fixtures.js').Scenario;
    const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/dashboard': key }, 300) });
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

export const EXPLORER_PAGE_SIZE = 20;
export type ExplorerEntry = NonNullable<ReturnType<typeof projectEntries>>[number];
type Position = NonNullable<ReturnType<typeof point>>;
export interface ExplorerIndex {
  entries: ExplorerEntry[];
  search: Map<string, string[]>;
  positions: Map<string, { key: string; pos: Position }>;
  states: string[];
}
const normalizeSearch = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es').trim();

/** Una vez por respuesta: conserva filas/orden e identidad técnica por índice, no por ID. */
export function createExplorerIndex(snapshot: DashboardSnapshot | undefined): ExplorerIndex | undefined {
  const entries = projectEntries(snapshot);
  if (entries === undefined) return undefined;
  const search = new Map<string, string[]>();
  const positions: ExplorerIndex['positions'] = new Map();
  const states = new Set<string>();
  for (const entry of entries) {
    const row = entry.row;
    states.add(row.estado ?? 'No disponible');
    search.set(entry.key, [row.titulo, row.proyecto, row.titular, row.ruc, row.actividad]
      .filter((value): value is string => typeof value === 'string').map(normalizeSearch));
    const pos = point(row);
    // Igualdad exacta de los números recibidos por JavaScript. Sin redondeo o promedio.
    if (pos) positions.set(entry.key, { key: `${row.latitud},${row.longitud}`, pos });
  }
  return { entries, search, positions, states: [...states] };
}

export function filterIndexedExplorer(index: ExplorerIndex | undefined, state = '', search = '') {
  if (!index) return undefined;
  const term = normalizeSearch(search);
  if (!state && !term) return index.entries;
  return index.entries.filter(entry => (!state || (entry.row.estado ?? 'No disponible') === state)
    && (!term || index.search.get(entry.key)!.some(value => value.includes(term))));
}

export interface ExplorerPointGroup {
  key: string;
  pos: Position;
  entries: ExplorerEntry[];
}
export function explorerSpatial(index: ExplorerIndex | undefined, entries: ExplorerEntry[] | undefined) {
  if (!index || entries === undefined) return undefined;
  const byKey = new Map<string, ExplorerEntry>();
  const groups = new Map<string, ExplorerPointGroup>();
  let coordinateCount = 0;
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const entry of entries) {
    byKey.set(entry.key, entry);
    const coordinate = index.positions.get(entry.key);
    if (!coordinate) continue;
    coordinateCount++;
    const group = groups.get(coordinate.key);
    if (group) group.entries.push(entry);
    else groups.set(coordinate.key, { ...coordinate, entries: [entry] });
    minX = Math.min(minX, coordinate.pos.x); maxX = Math.max(maxX, coordinate.pos.x);
    minY = Math.min(minY, coordinate.pos.y); maxY = Math.max(maxY, coordinate.pos.y);
  }
  const bounds = groups.size ? { x: minX - 3, y: minY - 3, width: maxX - minX + 6, height: maxY - minY + 6 }
    : { x: 0, y: 0, width: 100, height: 100 };
  return { byKey, groups, coordinateCount, bounds };
}

export function paginateExplorer(entries: ExplorerEntry[] | undefined, requested: number) {
  if (entries === undefined) return { rows: undefined, total: undefined, page: 1, pages: 0, start: undefined, end: undefined };
  const pages = Math.ceil(entries.length / EXPLORER_PAGE_SIZE);
  const page = Math.min(Math.max(1, Number.isSafeInteger(requested) ? requested : 1), Math.max(1, pages));
  return { rows: entries.slice((page - 1) * EXPLORER_PAGE_SIZE, page * EXPLORER_PAGE_SIZE), total: entries.length, page, pages,
    start: entries.length ? (page - 1) * EXPLORER_PAGE_SIZE + 1 : 0, end: Math.min(page * EXPLORER_PAGE_SIZE, entries.length) };
}

/** Puntos de la página y, si corresponde, uno adicional para la selección fuera de página. */
export function pagePointGroups(index: ExplorerIndex | undefined, spatial: ReturnType<typeof explorerSpatial>,
    pageEntries: ExplorerEntry[] | undefined, selected: string | undefined): ExplorerPointGroup[] | undefined {
  if (!index || !spatial || pageEntries === undefined) return undefined;
  const keys = new Set<string>();
  for (const entry of pageEntries) {
    const coordinate = index.positions.get(entry.key);
    if (coordinate) keys.add(coordinate.key);
  }
  if (selected !== undefined && spatial.byKey.has(selected)) {
    const coordinate = index.positions.get(selected);
    if (coordinate) keys.add(coordinate.key);
  }
  return [...keys].flatMap(key => {
    const group = spatial.groups.get(key);
    return group ? [group] : [];
  });
}
