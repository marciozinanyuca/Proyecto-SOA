import type { HttpResult } from '../client.js';
import type { Adapted, CircuitMetrics } from '../types.js';
import { count, fields, finite, invalid, oneOf, string } from '../validation.js';

export interface MonitorData { senace?: CircuitMetrics; bcrp?: CircuitMetrics }
function circuit(v: unknown): boolean {
  return fields(v, { servicio: string, estado: string, tasaFallosPorcentaje: finite,
    llamadasFallidas: count, llamadasExitosas: count, alcanceMetricas: oneOf(['LLAMADAS_LOGICAS']),
    disponibilidadExterna: oneOf(['NO_VERIFICADA']), respaldoLocal: oneOf(['NO_IMPLEMENTADO']) });
}
export function adaptMonitor(response: HttpResult): Adapted<MonitorData> {
  if (response.issue) return invalid(response.issue);
  if (response.status !== 200) return invalid('HTTP_NO_ADMITIDO');
  if (!fields(response.json, { senace: circuit, bcrp: circuit })) return invalid();
  const data = response.json as MonitorData;
  const incomplete = !data.senace || !data.bcrp || [data.senace, data.bcrp].some((v) =>
    v?.estado === undefined || v.alcanceMetricas === undefined || v.disponibilidadExterna === undefined || v.respaldoLocal === undefined);
  return { state: incomplete ? 'degraded' : 'ready', data, warnings: ['CIRCUITO_NO_ACREDITA_DISPONIBILIDAD_EXTERNA'] };
}
