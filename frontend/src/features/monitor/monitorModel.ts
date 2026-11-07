import { createApiClient } from '../../api/client.js';
import type { HttpResult } from '../../api/client.js';
import { adaptMonitor } from '../../api/adapters/monitor.js';
import type { MonitorData } from '../../api/adapters/monitor.js';
import type { Adapted, CircuitMetrics } from '../../api/types.js';
import { createMockFetch } from '../../mocks/mockFetch.js';
import type { Scenario } from '../../mocks/fixtures.js';

export const monitorScenarios = [
  { value: 'monitor', label: 'Circuitos CLOSED / OPEN · datos artificiales' },
  { value: 'monitorHalfOpen', label: 'Circuitos HALF_OPEN / DISABLED' },
  { value: 'monitorDemoPartial', label: 'Snapshot incompleto' },
  { value: 'monitorDemoEmpty', label: 'Objeto sin métricas · degradado' },
  { value: 'monitorDemoOptional', label: 'Contadores y tasa ausentes' },
  { value: 'monitorDemoError', label: 'Error HTTP 503 artificial' },
  { value: 'monitorDemoInvalid', label: 'Formato JSON incompatible' },
] as const satisfies readonly { value: Scenario; label: string }[];
export type MonitorScenario = typeof monitorScenarios[number]['value'];
export interface MonitorSnapshot { scenario: MonitorScenario; http: HttpResult; result: Adapted<MonitorData>; completedAt: number }
export async function loadMonitor(scenario: MonitorScenario, signal?: AbortSignal): Promise<MonitorSnapshot> {
  const api = createApiClient({ mode: 'mock', fetcher: createMockFetch({ '/api/estado-servicios': scenario }, 300) });
  const http = await api.estadoServicios(signal);
  return { scenario, http, result: adaptMonitor(http), completedAt: Date.now() };
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
  if (snapshot.result.state === 'error') return 'No se obtuvo un snapshot utilizable de la demostración. Las métricas desconocidas no se sustituyen por cero.';
  if (snapshot.result.state === 'degraded') return 'Snapshot incompleto: faltan servicios o metadatos. El adaptador lo clasifica como degradado, no como disponibilidad externa negativa.';
  return 'Snapshot artificial recibido. Los estados y contadores describen circuitos y llamadas lógicas, no disponibilidad real de SENACE o BCRP.';
}
