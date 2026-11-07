/** Tipos manuales: backend/src/main/resources/openapi.yaml, schemas y cuatro GET.
 * Sin required en los modelos fuente: las propiedades se conservan opcionales.
 * number NO promete exactitud BigDecimal/int64; data generico sigue unknown.
 */
export type OrigenSenace = 'API_DIRECTA_SENACE' | 'NO_DISPONIBLE';
export type EstadoConsulta = 'EXITO' | 'VACIA' | 'ERROR_EXTERNO' | 'FORMATO_INVALIDO' | 'PARCIAL';
export type EstadoFiltrado = 'APLICADO' | 'SIN_COINCIDENCIAS' | 'NO_EVALUABLE' | 'NO_APLICABLE';
export type MotivoSenace = 'RESPUESTA_OFICIAL_VACIA' | 'SIN_COINCIDENCIAS' | 'FECHAS_NO_EVALUABLES'
  | 'LISTA_AUSENTE' | 'FILA_NULA' | 'ID_AUSENTE' | 'ERROR_EXTERNO' | 'FORMATO_INVALIDO'
  | 'LIMITE_LECTURA' | 'RESPUESTA_PARCIAL' | 'CONFIGURACION_AUSENTE' | 'CONFIGURACION_INVALIDA'
  | 'CIRCUITO_ABIERTO' | 'DESTINO_INVALIDO' | 'RECURSO_INVALIDO' | 'REDIRECCION_RECHAZADA'
  | 'ERROR_HTTP' | 'ERROR_TLS' | 'TIMEOUT_CONEXION' | 'TIMEOUT_INACTIVIDAD'
  | 'PLAZO_TOTAL_AGOTADO' | 'CODIFICACION_NO_ADMITIDA' | 'INTERRUMPIDO';
export type AdvertenciaSenace = 'FECHA_AUSENTE' | 'FECHA_INVALIDA' | 'COORDENADAS_INCOMPLETAS' | 'COORDENADAS_FUERA_DE_RANGO';
export interface ConteosSenace {
  recibidos?: number; aceptados?: number; devueltos?: number; aprobados?: number;
  fechasNoEvaluables?: number; fechasNoEvaluablesEnActividad?: number;
}
export interface ConsultaSenace {
  estado?: EstadoConsulta; origen?: OrigenSenace; estadoFiltrado?: EstadoFiltrado;
  cobertura?: 'NO_VERIFICADA'; cuerpoCompleto?: boolean; motivo?: MotivoSenace;
  conteos?: ConteosSenace; advertencias?: AdvertenciaSenace[];
}
export interface ProyectoSenacePublico {
  id?: number; titular?: string; ruc?: string; titulo?: string; proyecto?: string;
  unidadProyecto?: string; tipo?: string; actividad?: string; sector?: string;
  fechaInicio?: string; anio?: number; estado?: string; descripcion?: string;
  longitud?: number; latitud?: number; resolucion?: string; origen?: OrigenSenace;
}
export interface ConsultoraSenacePublica {
  razonSocial?: string; actividad?: string; subsector?: string; tipoSolicitud?: string;
  resolucion?: string; fechaRd?: string; inicioVigenciaRd?: string; finVigenciaRd?: string;
}
export interface IndicadorBcrp {
  codigoSerie?: string; descripcion?: string; unidad?: string; periodicidad?: string;
  sectorSolicitado?: string; sectorEconomico?: string; ambito?: string;
  anioSolicitado?: number; periodo?: string; valor?: number; origen?: string;
  motivo?: string; urlFuente?: string;
}
export interface DashboardResponse {
  total?: number; aprobados?: number; variacionSeleccionada?: number;
  indicadorBcrp?: IndicadorBcrp; proyectos?: ProyectoSenacePublico[];
  fuentes?: { senace?: OrigenSenace; bcrp?: string; sectorConsultado?: string; anioConsultado?: number };
  consultaSenace?: ConsultaSenace;
}
export interface DatastreamResponse {
  datastream?: string; origen?: OrigenSenace; data?: unknown; consulta?: ConsultaSenace;
  recursoValidado?: boolean; mensaje?: string; datastreamsSoportados?: string[];
}
/** OpenAPI solo tipa estos dos objetos, no sus propiedades internas. */
export interface EstadoServiciosResponse { senace?: Record<string, unknown>; bcrp?: Record<string, unknown> }
/** Vista adaptada del objeto documentado; no modifica el contrato generado. */
export interface CircuitMetrics {
  servicio?: string; estado?: string; tasaFallosPorcentaje?: number;
  llamadasFallidas?: number; llamadasExitosas?: number;
  alcanceMetricas?: 'LLAMADAS_LOGICAS'; disponibilidadExterna?: 'NO_VERIFICADA'; respaldoLocal?: 'NO_IMPLEMENTADO';
}
export type ViewState = 'ready' | 'empty' | 'degraded' | 'error';
export interface Adapted<T> {
  state: ViewState;
  data?: T;
  reason?: string;
  warnings: string[];
  /** El sobre HTTP se conserva por separado; este estado no sustituye su status. */
}
