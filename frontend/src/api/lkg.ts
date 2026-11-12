import type { ConsultaSenace, EstadoModuloLkg, IndicadorBcrp, LkgMetadata } from './types.js';

/** Trazabilidad: schemas Lkg* y EstadoModuloLkg de OpenAPI. Sin red, reloj de expiracion ni persistencia. */
export const readStates = ['AVAILABLE', 'MISSING', 'EXPIRED', 'INVALID', 'IO_ERROR', 'OWNERSHIP_UNAVAILABLE'] as const;
export const writeStates = ['SAVED', 'INVALID', 'EMPTY_REPLACEMENT_REJECTED', 'OLDER_RESPONSE_REJECTED', 'IO_ERROR', 'ATOMIC_MOVE_UNSUPPORTED', 'OWNERSHIP_UNAVAILABLE'] as const;
export const moduleStates = ['HABILITADO_SIN_VERIFICACION', 'OPERATIVO', 'CON_INCIDENCIAS'] as const;
const attemptStates = ['EXITO', 'VACIA', 'ERROR_EXTERNO', 'FORMATO_INVALIDO', 'PARCIAL', 'NO_EVALUABLE', 'SIN_OBSERVACION'];
const safeReasons = ['RESPUESTA_OFICIAL_VACIA', 'SIN_COINCIDENCIAS', 'FECHAS_NO_EVALUABLES', 'LISTA_AUSENTE', 'FILA_NULA', 'ID_AUSENTE',
  'ERROR_EXTERNO', 'FORMATO_INVALIDO', 'LIMITE_LECTURA', 'RESPUESTA_PARCIAL', 'CONFIGURACION_AUSENTE', 'CONFIGURACION_INVALIDA',
  'CIRCUITO_ABIERTO', 'DESTINO_INVALIDO', 'RECURSO_INVALIDO', 'REDIRECCION_RECHAZADA', 'ERROR_HTTP', 'ERROR_TLS',
  'TIMEOUT_CONEXION', 'TIMEOUT_INACTIVIDAD', 'PLAZO_TOTAL_AGOTADO', 'CODIFICACION_NO_ADMITIDA', 'INTERRUMPIDO',
  'SIN_OBSERVACION', 'PERIODO_FUERA_DE_RANGO', 'SECTOR_NO_SOPORTADO', 'ANIO_INVALIDO'];
const obj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const text = (v: unknown): v is string => typeof v === 'string';
const bool = (v: unknown): v is boolean => typeof v === 'boolean';
const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const enumOf = (values: readonly string[]) => (v: unknown): boolean => text(v) && values.includes(v);
const fields = (v: unknown, checks: Record<string, (x: unknown) => boolean>): v is Record<string, unknown> =>
  obj(v) && Object.entries(checks).every(([key, check]) => !(key in v) || check(v[key]));
export const safeLkgReason = (v: unknown): string => text(v) && safeReasons.includes(v) ? v : 'MOTIVO_NO_INFORMADO';

export function utcDate(value: unknown): value is string {
  if (!text(value) || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|\+00:00)$/.test(value)) return false;
  const stamp = Date.parse(value);
  return Number.isFinite(stamp) && new Date(stamp).toISOString().slice(0, 19) === value.slice(0, 19);
}

export function lkgMetadata(value: unknown): value is LkgMetadata {
  return fields(value, {
    intentoApi: v => fields(v, { estado: enumOf(attemptStates), motivo: enumOf(safeReasons), cuerpoCompleto: bool }),
    respaldo: v => fields(v, { capturadoEn: utcDate, edadSegundos: count, antiguo: bool,
      versionEsquema: n => count(n) && n > 0, cobertura: enumOf(['NO_VERIFICADA']) }),
    lectura: enumOf(readStates), escritura: enumOf(writeStates),
  });
}

/** Requisitos conservadores de uso local; no convierte optionalidad del schema en datos inventados. */
export function usableBackup(meta: LkgMetadata | undefined, requireSchema = true): boolean {
  const stored = meta?.respaldo;
  return lkgMetadata(meta) && meta.lectura === 'AVAILABLE' && stored !== undefined
    && utcDate(stored.capturadoEn) && count(stored.edadSegundos) && typeof stored.antiguo === 'boolean'
    && (requireSchema ? stored.versionEsquema === 1 : stored.versionEsquema === undefined || stored.versionEsquema === 1)
    && (requireSchema ? stored.cobertura === 'NO_VERIFICADA' : stored.cobertura === undefined || stored.cobertura === 'NO_VERIFICADA')
    && ['ERROR_EXTERNO', 'FORMATO_INVALIDO', 'PARCIAL'].includes(meta.intentoApi?.estado ?? '');
}

export function usableSenace(meta: ConsultaSenace | undefined, requireSchema = true): boolean {
  return meta?.cuerpoCompleto === true && (meta.estado === 'EXITO' || meta.estado === 'VACIA')
    && (meta.origen === 'API_DIRECTA_SENACE' || (meta.origen === 'RESPALDO_LOCAL' && meta.cobertura === 'NO_VERIFICADA' && usableBackup(meta.lkg, requireSchema)));
}
export function usableBcrp(value: IndicadorBcrp | undefined): value is IndicadorBcrp & { valor: number } {
  return value?.valor !== undefined && Number.isFinite(value.valor)
    && (value.origen === 'API_DIRECTA_BCRP' || (value.origen === 'RESPALDO_LOCAL' && usableBackup(value.lkg)));
}

export function lkgWarnings(origin: string | undefined, meta: LkgMetadata | undefined, source: string): string[] {
  const warnings: string[] = [];
  if (origin === 'RESPALDO_LOCAL') warnings.push(`${source}_RESPALDO_LOCAL`);
  if (meta?.respaldo?.antiguo) warnings.push(`${source}_RESPALDO_ANTIGUO`);
  if (meta?.escritura && ['IO_ERROR', 'INVALID', 'ATOMIC_MOVE_UNSUPPORTED', 'OWNERSHIP_UNAVAILABLE'].includes(meta.escritura)) {
    warnings.push(`${source}_FALLO_AL_GUARDAR_RESPALDO`);
  }
  if (meta && !meta.intentoApi?.estado) warnings.push(`${source}_LKG_INCOMPLETO`);
  return warnings;
}

export function monitorLkg(value: unknown): value is EstadoModuloLkg {
  return fields(value, { estado: enumOf(moduleStates), alcance: enumOf(['ULTIMAS_OBSERVACIONES_DEL_PROCESO']),
    limiteObservaciones: count, observaciones: v => Array.isArray(v) && v.every(row => fields(row, {
      fuente: enumOf(['SENACE', 'BCRP']), recurso: text, periodo: text,
      existencia: enumOf(['EXISTE', 'NO_EXISTE', 'NO_VERIFICADA']), integridad: enumOf(['VALIDADA', 'INVALIDA', 'NO_VERIFICADA']),
      observadoEn: utcDate, verificadoEn: utcDate, ultimaCapturaValida: utcDate,
      edadSegundos: count, antiguo: bool, vencido: bool, lectura: enumOf(readStates), escritura: enumOf(writeStates),
    })) });
}

export function captureLabel(value: string | undefined): string {
  return utcDate(value) ? `${new Date(value).toLocaleString('es-PE', { timeZone: 'UTC' })} UTC` : 'No informada';
}
export function ageLabel(value: number | undefined): string {
  if (!count(value)) return 'No informada';
  return `${Math.floor(value / 86400)} d · ${Math.floor(value % 86400 / 3600)} h · ${Math.floor(value % 3600 / 60)} min (${value.toLocaleString('es-PE')} s)`;
}
export function originLabel(origin: string | undefined): string {
  if (origin === 'RESPALDO_LOCAL') return 'Datos recuperados del respaldo local';
  if (origin === 'API_DIRECTA_SENACE' || origin === 'API_DIRECTA_BCRP') return 'Datos obtenidos directamente de la API';
  if (origin === 'SIN_RESPALDO') return 'Sin respaldo utilizable para esta consulta';
  return 'Fuente no disponible o procedencia no informada';
}
