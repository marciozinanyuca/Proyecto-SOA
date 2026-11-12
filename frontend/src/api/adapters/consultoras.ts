import type { HttpResult } from '../client.js';
import type { Adapted, ConsultaSenace, ConsultoraSenacePublica } from '../types.js';
import { lkgWarnings, usableBackup, usableSenace } from '../lkg.js';
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
  const lkg: Record<string, unknown> = {};
  const attempt: Record<string, unknown> = {};
  const stored: Record<string, unknown> = {};
  if (h['x-senace-intento-estado'] !== undefined) attempt.estado = h['x-senace-intento-estado'];
  if (h['x-senace-intento-motivo'] !== undefined) attempt.motivo = h['x-senace-intento-motivo'];
  if (Object.keys(attempt).length) lkg.intentoApi = attempt;
  if (h['x-senace-respaldo-capturado-en'] !== undefined) stored.capturadoEn = h['x-senace-respaldo-capturado-en'];
  if (h['x-senace-respaldo-edad-segundos'] !== undefined) {
    const age = h['x-senace-respaldo-edad-segundos'];
    if (!/^(0|[1-9]\d*)$/.test(age) || !Number.isSafeInteger(Number(age))) return invalid('HEADER_INVALIDO');
    stored.edadSegundos = Number(age);
  }
  if (h['x-senace-respaldo-antiguo'] !== undefined) {
    if (!['true', 'false'].includes(h['x-senace-respaldo-antiguo'])) return invalid('HEADER_INVALIDO');
    stored.antiguo = h['x-senace-respaldo-antiguo'] === 'true';
  }
  // Este endpoint no transmite versionEsquema por header: no inventarla para satisfacer un validator.
  if (Object.keys(stored).length) lkg.respaldo = stored;
  if (h['x-senace-snapshot-lectura'] !== undefined) lkg.lectura = h['x-senace-snapshot-lectura'];
  if (h['x-senace-snapshot-escritura'] !== undefined) lkg.escritura = h['x-senace-snapshot-escritura'];
  if (Object.keys(lkg).length) candidate.lkg = lkg;
  if (!consulta(candidate)) return invalid('HEADER_INVALIDO');
  const data: ConsultorasData = { rows: response.json as ConsultoraSenacePublica[], consulta: candidate };
  const warnings = candidate.cobertura ? [] : ['COBERTURA_AUSENTE'];
  if (response.status !== 200) {
    if (data.rows.length !== 0) return invalid('ERROR_CON_FILAS');
    return { state: 'error', data, reason: data.consulta.motivo ?? `HTTP_${response.status}`, warnings };
  }
  const local = data.consulta.origen === 'RESPALDO_LOCAL';
  if (local && !usableBackup(data.consulta.lkg, false)) return invalid('LKG_NO_UTILIZABLE');
  if (data.consulta.origen === 'SIN_RESPALDO') return invalid('AUSENCIA_CON_DATOS');
  if (!(local ? usableSenace(data.consulta, false) : official(data.consulta))) return { state: 'degraded', data, reason: 'PROCEDENCIA_NO_CONFIRMADA', warnings };
  if (data.consulta.estado === 'VACIA' && data.rows.length) return invalid('VACIO_INCOHERENTE');
  const localWarnings = lkgWarnings(data.consulta.origen, data.consulta.lkg, 'SENACE');
  warnings.push(...localWarnings);
  return { state: localWarnings.length ? 'degraded' : data.rows.length ? 'ready' : 'empty', data, warnings };
}
