import type { HttpResult } from '../client.js';
import type { Adapted, DatastreamResponse } from '../types.js';
import { arrayOf, boolean, consulta, fields, finite, integer, invalid, official, oneOf, origins, precisionWarnings, record, string } from '../validation.js';

const legacy = ['GastoEspecifica', 'GastoFuente', 'GastoGenerica', 'SolicitudAcceso', 'Visitas', 'Reclamos'];
export function adaptDatastream(response: HttpResult): Adapted<DatastreamResponse> {
  if (response.issue) return invalid(response.issue);
  if (![200, 502, 503].includes(response.status ?? 0)) return invalid('HTTP_NO_ADMITIDO');
  if (!fields(response.json, { datastream: string, origen: oneOf(origins), consulta, recursoValidado: boolean, mensaje: string, datastreamsSoportados: arrayOf(string) })) return invalid();
  const data = response.json as DatastreamResponse;
  if (response.status !== 200) {
    if (data.data !== null) return invalid('ERROR_SIN_NULL_EXPLICITO');
    return { state: 'error', data, reason: data.consulta?.motivo ?? `HTTP_${response.status}`, warnings: [] };
  }
  const name = data.datastream;
  if (name === 'CarteraProyectos') {
    if (!Array.isArray(data.data) || !data.data.every((v) => fields(v, {
      ...Object.fromEntries(['TITULAR', 'RUC', 'TITULO_PROYECTO', 'UNIDAD_PROYECTO', 'TIPO', 'ACTIVIDAD', 'FECHA_INICIO', 'ESTADO', 'DESCRIPCION', 'RESOLUCION']
        .map((key) => [key, (value: unknown) => value === null || string(value)])),
      ID: integer, LONGITUD: (v) => v === null || finite(v), LATITUD: (v) => v === null || finite(v),
    }) && record(v) && integer(v.ID))) return invalid();
  } else if (name === 'ConsultorasAmbientales') {
    if (!Array.isArray(data.data) || !data.data.every((v) => fields(v, Object.fromEntries(
      ['RAZON_SOCIAL', 'ACTIVIDAD', 'SUBSECTOR', 'TIPO_SOLICITUD', 'RESOLUCION', 'FECHA_RD', 'INICIO_VIGENCIA_RD', 'FIN_VIGENCIA_RD'].map((key) => [key, (value: unknown) => value === null || string(value)]))))) return invalid();
  } else if (!name || !legacy.includes(name) || !record(data.data)) return invalid();
  const warnings = precisionWarnings();
  if (name && legacy.includes(name)) warnings.push('ESQUEMA_RECURSO_HEREDADO_NO_VERIFICADO');
  if (data.origen && data.consulta?.origen && data.origen !== data.consulta.origen) return invalid('PROCEDENCIA_INCOHERENTE');
  if (!official(data.consulta)) return { state: 'degraded', data, reason: 'PROCEDENCIA_NO_CONFIRMADA', warnings };
  if (data.consulta?.estado === 'VACIA' && (!Array.isArray(data.data) || data.data.length !== 0)) return invalid('VACIO_INCOHERENTE');
  return { state: Array.isArray(data.data) && data.data.length === 0 ? 'empty' : 'ready', data, warnings };
}
