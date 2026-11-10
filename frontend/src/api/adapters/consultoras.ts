import type { HttpResult } from '../client.js';
import type { Adapted, ConsultaSenace, ConsultoraSenacePublica } from '../types.js';
import { consulta, fields, invalid, official, textFields } from '../validation.js';

export interface ConsultorasData { rows: ConsultoraSenacePublica[]; consulta: ConsultaSenace }
export function adaptConsultoras(response: HttpResult): Adapted<ConsultorasData> {
  if (response.issue) return invalid(response.issue);
  if (![200, 502, 503].includes(response.status ?? 0)) return invalid('HTTP_NO_ADMITIDO');
  if (!Array.isArray(response.json) || !response.json.every((v) => fields(v,
    textFields(['razonSocial', 'actividad', 'subsector', 'tipoSolicitud', 'resolucion', 'fechaRd', 'inicioVigenciaRd', 'finVigenciaRd'])))) return invalid();
  const h = response.headers;
  const candidate: Record<string, unknown> = {};
  for (const [name, key] of [['origen', 'x-senace-origen'], ['estado', 'x-senace-estado'], ['cobertura', 'x-senace-cobertura'], ['motivo', 'x-senace-motivo']] as const) {
    if (h[key] !== undefined) candidate[name] = h[key];
  }
  if (h['x-senace-cuerpo-completo'] !== undefined) {
    if (!['true', 'false'].includes(h['x-senace-cuerpo-completo'])) return invalid('HEADER_INVALIDO');
    candidate.cuerpoCompleto = h['x-senace-cuerpo-completo'] === 'true';
  }
  if (!consulta(candidate)) return invalid('HEADER_INVALIDO');
  const data: ConsultorasData = { rows: response.json as ConsultoraSenacePublica[], consulta: candidate };
  const warnings = candidate.cobertura ? [] : ['COBERTURA_AUSENTE'];
  if (response.status !== 200) {
    if (data.rows.length !== 0) return invalid('ERROR_CON_FILAS');
    return { state: 'error', data, reason: data.consulta.motivo ?? `HTTP_${response.status}`, warnings };
  }
  if (!official(data.consulta)) return { state: 'degraded', data, reason: 'PROCEDENCIA_NO_CONFIRMADA', warnings };
  if (data.consulta.estado === 'VACIA' && data.rows.length) return invalid('VACIO_INCOHERENTE');
  return { state: data.rows.length ? 'ready' : 'empty', data, warnings };
}
