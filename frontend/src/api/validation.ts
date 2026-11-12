import type { Adapted, ConsultaSenace } from './types.js';
import { lkgMetadata } from './lkg.js';

export const origins = ['API_DIRECTA_SENACE', 'NO_DISPONIBLE', 'RESPALDO_LOCAL', 'SIN_RESPALDO'] as const;
export const queryStates = ['EXITO', 'VACIA', 'ERROR_EXTERNO', 'FORMATO_INVALIDO', 'PARCIAL'] as const;
export const filterStates = ['APLICADO', 'SIN_COINCIDENCIAS', 'NO_EVALUABLE', 'NO_APLICABLE'] as const;
export const reasons = ['RESPUESTA_OFICIAL_VACIA', 'SIN_COINCIDENCIAS', 'FECHAS_NO_EVALUABLES', 'LISTA_AUSENTE', 'FILA_NULA', 'ID_AUSENTE', 'ERROR_EXTERNO', 'FORMATO_INVALIDO', 'LIMITE_LECTURA', 'RESPUESTA_PARCIAL', 'CONFIGURACION_AUSENTE', 'CONFIGURACION_INVALIDA', 'CIRCUITO_ABIERTO', 'DESTINO_INVALIDO', 'RECURSO_INVALIDO', 'REDIRECCION_RECHAZADA', 'ERROR_HTTP', 'ERROR_TLS', 'TIMEOUT_CONEXION', 'TIMEOUT_INACTIVIDAD', 'PLAZO_TOTAL_AGOTADO', 'CODIFICACION_NO_ADMITIDA', 'INTERRUMPIDO'] as const;
export const notices = ['FECHA_AUSENTE', 'FECHA_INVALIDA', 'COORDENADAS_INCOMPLETAS', 'COORDENADAS_FUERA_DE_RANGO'] as const;
export function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
export const string = (v: unknown): v is string => typeof v === 'string';
export const finite = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
export const integer = (v: unknown): v is number => finite(v) && Number.isSafeInteger(v);
export const count = (v: unknown): v is number => integer(v) && v >= 0;
export const boolean = (v: unknown): v is boolean => typeof v === 'boolean';
export const oneOf = (values: readonly string[]) => (v: unknown) => string(v) && values.includes(v);
export const arrayOf = (check: (v: unknown) => boolean) => (v: unknown): boolean => Array.isArray(v) && v.every(check);
export function fields(value: unknown, checks: Record<string, (v: unknown) => boolean>): value is Record<string, unknown> {
  return record(value) && Object.entries(checks).every(([key, check]) => !(key in value) || check(value[key]));
}
export function textFields(names: readonly string[]): Record<string, (v: unknown) => boolean> {
  return Object.fromEntries(names.map((name) => [name, string]));
}
export function consulta(value: unknown): value is ConsultaSenace {
  return fields(value, {
    estado: oneOf(queryStates), origen: oneOf(origins), estadoFiltrado: oneOf(filterStates),
    cobertura: oneOf(['NO_VERIFICADA']), cuerpoCompleto: boolean, motivo: oneOf(reasons),
    advertencias: arrayOf(oneOf(notices)),
    lkg: lkgMetadata,
    conteos: (v) => fields(v, Object.fromEntries(['recibidos', 'aceptados', 'devueltos', 'aprobados', 'fechasNoEvaluables', 'fechasNoEvaluablesEnActividad'].map((k) => [k, count]))),
  });
}
export function official(meta: ConsultaSenace | undefined): boolean {
  return meta?.origen === 'API_DIRECTA_SENACE' && meta.cuerpoCompleto === true
    && (meta.estado === 'EXITO' || meta.estado === 'VACIA');
}
export function invalid<T>(reason = 'FORMATO_INVALIDO'): Adapted<T> {
  return { state: 'error', reason, warnings: [] };
}
export function precisionWarnings(): string[] { return ['NUMEROS_JS_NO_GARANTIZAN_PRECISION_DECIMAL_EXACTA']; }
