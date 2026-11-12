import { createMonitorSource, parseSourceConfig } from '../../api/source.js';
import type { SourceConfig } from '../../api/source.js';
import type { FetchLike } from '../../api/client.js';
import type { HttpResult } from '../../api/client.js';
import { adaptMonitor } from '../../api/adapters/monitor.js';
import type { MonitorData } from '../../api/adapters/monitor.js';
import type { Adapted, CircuitMetrics } from '../../api/types.js';
import type { Scenario } from '../../mocks/fixtures.js';
import type { DashboardSnapshot } from '../dashboard/dashboardModel.js';

/** Solo metadatos de la respuesta Dashboard de esta pestaña; no consulta ni copia filas. */
export function dashboardDetailsForMonitor(snapshot: DashboardSnapshot | undefined) {
  if (!snapshot || snapshot.result.state === 'error' || !snapshot.result.data) return undefined;
  const data = snapshot.result.data;
  return {
    query: { ...snapshot.query }, mode: snapshot.http.mode, receivedAt: snapshot.receivedAt,
    senace: { origin: data.consultaSenace?.origen, metadata: data.consultaSenace?.lkg },
    bcrp: { origin: data.indicadorBcrp?.origen, metadata: data.indicadorBcrp?.lkg },
  };
}

export const monitorScenarios = [
  { value: 'monitor', label: 'Circuitos CLOSED / OPEN · datos artificiales' },
  { value: 'monitorHalfOpen', label: 'Circuitos HALF_OPEN / DISABLED' },
  { value: 'monitorDemoPartial', label: 'Snapshot incompleto' },
  { value: 'monitorDemoEmpty', label: 'Objeto sin métricas · degradado' },
  { value: 'monitorDemoOptional', label: 'Contadores y tasa ausentes' },
  { value: 'monitorDemoError', label: 'Error HTTP 503 artificial' },
  { value: 'monitorDemoInvalid', label: 'Formato JSON incompatible' },
  { value: 'monitorLkgUnverified', label: 'LKG artificial · habilitado sin verificación' },
  { value: 'monitorLkgOperational', label: 'LKG artificial · operaciones locales verificadas' },
  { value: 'monitorLkgIncident', label: 'LKG artificial · incidencias locales' },
] as const satisfies readonly { value: Scenario; label: string }[];
export type MonitorScenario = typeof monitorScenarios[number]['value'];
export interface MonitorSnapshot { scenario?: MonitorScenario; http: HttpResult; result: Adapted<MonitorData>; completedAt: number }
export async function loadMonitor(scenario: MonitorScenario, signal?: AbortSignal, source: SourceConfig = parseSourceConfig({}), fetcher?: FetchLike): Promise<MonitorSnapshot> {
  const api = createMonitorSource(source, scenario, fetcher);
  const http = await api.estadoServicios(signal);
  return { ...(api.mode === 'mock' ? { scenario } : {}), http, result: adaptMonitor(http), completedAt: Date.now() };
}
export function circuitLabel(state: string | undefined) {
  switch (state) {
    case 'CLOSED': return { label: 'Circuito cerrado', tone: 'closed', explanation: 'Las llamadas no se rechazan por apertura del circuito. No certifica disponibilidad del proveedor.' };
    case 'OPEN': return { label: 'Circuito abierto', tone: 'open', explanation: 'El circuito rechaza llamadas protegidas según su política. No implica un respaldo local operativo.' };
    case 'HALF_OPEN': return { label: 'Recuperación en evaluación', tone: 'half', explanation: 'El circuito permite llamadas de prueba según su política; no acredita recuperación externa definitiva.' };
    case 'DISABLED': return { label: 'Circuito deshabilitado', tone: 'neutral', explanation: 'La protección del circuito está deshabilitada; no indica el estado real del proveedor.' };
    default: return { label: state === undefined ? 'Estado no disponible' : 'Estado sin interpretación local', tone: 'neutral', explanation: 'No se interpreta un estado ausente o desconocido como disponibilidad externa.' };
  }
}
export function rateDisplay(value: number | undefined): string {
  if (value === -1) return 'No calculada';
  if (value === undefined || !Number.isFinite(value) || value < 0 || value > 100) return 'No disponible';
  return `${value.toLocaleString('es-PE')} %`;
}
export function countDisplay(value: number | undefined): string {
  return value !== undefined && Number.isSafeInteger(value) && value >= 0 ? value.toLocaleString('es-PE') : 'No disponible';
}
export function metricsFor(snapshot: MonitorSnapshot | undefined, service: 'senace' | 'bcrp'): CircuitMetrics | undefined {
  return snapshot?.http.status === 200 && snapshot.result.state !== 'error' ? snapshot.result.data?.[service] : undefined;
}
export function monitorMessage(snapshot: MonitorSnapshot): string {
  if (snapshot.result.state === 'error') return `No se obtuvo un snapshot utilizable ${snapshot.http.mode === 'mock' ? 'de la demostración' : 'del backend HTTP'}. Las métricas desconocidas no se sustituyen por cero. No se activa una fuente alternativa.`;
  if (snapshot.result.data?.lkg?.estado === 'CON_INCIDENCIAS') return 'Métricas recibidas con incidencias del módulo LKG. Las incidencias locales no certifican caída de los proveedores.';
  if (snapshot.result.state === 'degraded') return 'Snapshot incompleto: faltan servicios o metadatos. El adaptador lo clasifica como degradado, no como disponibilidad externa negativa.';
  return `${snapshot.http.mode === 'mock' ? 'Snapshot artificial recibido.' : 'Snapshot recibido del backend por HTTP.'} Los estados y contadores describen circuitos y llamadas lógicas, no disponibilidad real de SENACE o BCRP.`;
}

export function lkgStateLabel(state: string | undefined): string {
  switch (state) {
    case 'NO_IMPLEMENTADO': return 'LKG no habilitado/informado · contrato histórico';
    case 'HABILITADO_SIN_VERIFICACION': return 'LKG habilitado sin verificación';
    case 'OPERATIVO': return 'LKG operativo según las últimas operaciones locales';
    case 'CON_INCIDENCIAS': return 'LKG con incidencias locales';
    default: return 'Estado LKG no informado';
  }
}
export function lkgExplanation(snapshot: MonitorSnapshot | undefined): string {
  if (!snapshot || snapshot.result.state === 'error') return 'Sin observaciones utilizables. No se afirma existencia o integridad del respaldo.';
  const data = snapshot.result.data;
  if (!data?.lkg) return data?.senace?.respaldoLocal === 'NO_IMPLEMENTADO' && data.bcrp?.respaldoLocal === 'NO_IMPLEMENTADO'
    ? 'El backend conserva el contrato histórico sin LKG informado o habilitado. Esto no permite inspeccionar archivos locales.'
    : 'No se recibió el objeto LKG. No se deduce que haya archivos ni que estén íntegros.';
  return 'Últimas observaciones del proceso actual; no inventario completo ni verificación continua. Pueden estar vacías tras reiniciar el backend aunque existan archivos. Actualizar Monitor no realiza consultas adicionales a proveedores ni verifica archivos desde React.';
}
export function snapshotResourceLabel(value: string | undefined): string {
  return value && /^(CarteraProyectos|ConsultorasAmbientales|PM04969AA|PM04971AA|PM04973AA|PM04974AA|PM04978AA|PM04983AA)$/.test(value)
    ? value : 'Recurso no informado o no reconocido';
}
