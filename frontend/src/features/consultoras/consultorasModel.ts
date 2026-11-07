import { createConsultorasSource, parseSourceConfig } from '../../api/source.js';
import type { SourceConfig } from '../../api/source.js';
import type { FetchLike } from '../../api/client.js';
import type { HttpResult } from '../../api/client.js';
import { adaptConsultoras } from '../../api/adapters/consultoras.js';
import type { ConsultorasData } from '../../api/adapters/consultoras.js';
import type { Adapted, ConsultoraSenacePublica } from '../../api/types.js';
import { official } from '../../api/validation.js';
import type { Scenario } from '../../mocks/fixtures.js';

export const consultantFields = [
  ['razonSocial', 'Razón social'], ['actividad', 'Actividad'], ['subsector', 'Subsector'],
  ['tipoSolicitud', 'Tipo de solicitud'], ['resolucion', 'Resolución'], ['fechaRd', 'Fecha RD original'],
  ['inicioVigenciaRd', 'Inicio de vigencia RD original'], ['finVigenciaRd', 'Fin de vigencia RD original'],
] as const satisfies readonly (readonly [keyof ConsultoraSenacePublica, string])[];
export const consultantScenarios = [
  { value: 'consultorasDemoSuccess', label: 'HTTP 200 · filas artificiales y duplicados' },
  { value: 'consultantsEmpty', label: 'HTTP 200 · respuesta oficial vacía simulada' },
  { value: 'consultants502', label: 'HTTP 502 · formato externo inválido' },
  { value: 'consultants503', label: 'HTTP 503 · configuración ausente' },
  { value: 'consultorasDemoNoHeaders', label: 'HTTP 200 · filas sin headers SENACE' },
  { value: 'consultorasDemoEmptyNoHeaders', label: 'HTTP 200 · array vacío sin headers SENACE' },
  { value: 'consultorasDemoInvalid', label: 'HTTP 200 · estructura JSON inválida' },
  { value: 'consultorasDemoOptional', label: 'HTTP 200 · opcionales y vacíos' },
] as const satisfies readonly { value: Scenario; label: string }[];
export type ConsultantScenario = typeof consultantScenarios[number]['value'];
export interface ConsultantSnapshot { scenario?: ConsultantScenario; http: HttpResult; result: Adapted<ConsultorasData> }
export interface ConsultantFilters { search: string; actividad: string; subsector: string; tipoSolicitud: string }
export const emptyFilters: Readonly<ConsultantFilters> = { search: '', actividad: '', subsector: '', tipoSolicitud: '' };
export async function loadConsultants(scenario: ConsultantScenario, signal?: AbortSignal, source: SourceConfig = parseSourceConfig({}), fetcher?: FetchLike): Promise<ConsultantSnapshot> {
  const api = createConsultorasSource(source, scenario, fetcher);
  const http = await api.consultoras(signal);
  return { ...(api.mode === 'mock' ? { scenario } : {}), http, result: adaptConsultoras(http) };
}
export function usableRows(snapshot: ConsultantSnapshot | undefined): ConsultoraSenacePublica[] | undefined {
  return snapshot?.http.status === 200 && snapshot.result.state !== 'error' ? snapshot.result.data?.rows : undefined;
}
export function displayField(value: string | undefined): string { return value === undefined || value.trim() === '' ? 'No disponible' : value; }
function normalize(value: string): string { return value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es').trim(); }
export function filterConsultants(rows: readonly ConsultoraSenacePublica[] | undefined, filters: ConsultantFilters) {
  if (rows === undefined) return undefined;
  const term = normalize(filters.search);
  // El índice identifica una fila de esta respuesta; no es un ID empresarial.
  return rows.map((row, index) => ({ row, key: String(index) })).filter(({ row }) =>
    (!filters.actividad || row.actividad === filters.actividad)
    && (!filters.subsector || row.subsector === filters.subsector)
    && (!filters.tipoSolicitud || row.tipoSolicitud === filters.tipoSolicitud)
    && (!term || consultantFields.some(([key]) => typeof row[key] === 'string' && normalize(row[key]!).includes(term))));
}
export function filterOptions(rows: readonly ConsultoraSenacePublica[] | undefined, key: 'actividad' | 'subsector' | 'tipoSolicitud'): string[] {
  return [...new Set(rows?.flatMap(row => typeof row[key] === 'string' && row[key]!.trim() ? [row[key]!] : []) ?? [])].sort((a, b) => a.localeCompare(b, 'es'));
}
export function consultantKpis(snapshot: ConsultantSnapshot | undefined, filtered: ReturnType<typeof filterConsultants>) {
  const rows = usableRows(snapshot);
  // Un [] sin procedencia no acredita el vacío oficial. Las filas no vacías se
  // cuentan como recibidas sin afirmar que proceden oficialmente de SENACE.
  const known = rows !== undefined && (rows.length > 0 || official(snapshot?.result.data?.consulta));
  return { received: known ? rows.length : undefined, filtered: known ? filtered?.length : undefined,
    withResolution: known ? filtered?.filter(({ row }) => typeof row.resolucion === 'string' && row.resolucion.trim() !== '').length : undefined };
}
export function paginateConsultants(entries: ReturnType<typeof filterConsultants>, page: number, pageSize = 10) {
  if (!Number.isSafeInteger(pageSize) || pageSize < 1) throw new Error('TAMANO_PAGINA_INVALIDO');
  if (entries === undefined) return { rows: undefined, total: undefined, page: 1, pages: 0, start: undefined, end: undefined };
  const pages = Math.ceil(entries.length / pageSize);
  const current = Math.min(Math.max(Number.isSafeInteger(page) ? page : 1, 1), Math.max(1, pages));
  return { rows: entries.slice((current - 1) * pageSize, current * pageSize), total: entries.length, page: current, pages,
    start: entries.length ? (current - 1) * pageSize + 1 : 0, end: Math.min(current * pageSize, entries.length) };
}
export function consultantMessage(snapshot: ConsultantSnapshot): string {
  if (snapshot.http.status === 502) return `Formato externo inválido${snapshot.http.mode === 'mock' ? ' simulado' : ''} (HTTP 502). El array de error no acredita cero consultoras.`;
  if (snapshot.http.status === 503) return `SENACE no disponible${snapshot.http.mode === 'mock' ? ' en este escenario' : ' en esta consulta'} (HTTP 503). No hay un conteo oficial conocido.`;
  if (snapshot.result.state === 'error') return 'No se obtuvo una respuesta utilizable. No se sustituyen errores por filas ni se activa una fuente alternativa.';
  if (snapshot.result.state === 'degraded') return 'Procedencia no confirmada: faltan metadatos SENACE. Las filas recibidas no se presentan como oficiales; un array vacío no certifica ausencia de consultoras.';
  if (snapshot.result.state === 'empty') return `Respuesta oficial vacía${snapshot.http.mode === 'mock' ? ' simulada' : ''} (HTTP 200). No equivale a un error del proveedor.`;
  return `${snapshot.http.mode === 'mock' ? 'Filas artificiales disponibles.' : 'Filas recibidas del backend con procedencia SENACE informada.'} Se conservan orden, duplicados y fechas originales, sin deducir vigencia.`;
}
