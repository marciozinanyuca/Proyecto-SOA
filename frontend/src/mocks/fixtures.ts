import type { ConsultaSenace, ConsultoraSenacePublica, DashboardResponse, ProyectoSenacePublico } from '../api/types.js';

/** TODOS los registros y valores de este archivo son artificiales.
 * API_DIRECTA en un fixture describe un escenario contractual, no una observacion.
 */
const official: ConsultaSenace = {
  estado: 'EXITO', origen: 'API_DIRECTA_SENACE', estadoFiltrado: 'APLICADO',
  cobertura: 'NO_VERIFICADA', cuerpoCompleto: true, conteos: { recibidos: 1, aceptados: 1, devueltos: 1, aprobados: 1 }, advertencias: [],
};
const indicator = { codigoSerie: 'PM04974AA', descripcion: 'INDICADOR ARTIFICIAL DE PRUEBA', unidad: 'Variación porcentual interanual (%)',
  periodicidad: 'ANUAL', anioSolicitado: 2017, periodo: '2017', valor: 1.25, origen: 'API_DIRECTA_BCRP' };
const project = { id: 1, titular: 'TITULAR ARTIFICIAL', ruc: '00000000000', titulo: 'PROYECTO ARTIFICIAL', actividad: 'Hidrocarburos',
  fechaInicio: '05/04/2017', anio: 2017, estado: 'Aprobado', latitud: -12.123456789, longitud: -70.123456789, origen: 'API_DIRECTA_SENACE' };
const consultant = { razonSocial: 'CONSULTORA ARTIFICIAL', actividad: 'ACTIVIDAD DE PRUEBA', subsector: 'SUBSECTOR DE PRUEBA',
  tipoSolicitud: 'SOLICITUD DE PRUEBA', resolucion: 'RESOLUCION DE PRUEBA', fechaRd: 'TEXTO ORIGINAL', inicioVigenciaRd: 'INICIO ORIGINAL', finVigenciaRd: 'FIN ORIGINAL' };
const failed: ConsultaSenace = { estado: 'ERROR_EXTERNO', origen: 'NO_DISPONIBLE', estadoFiltrado: 'NO_APLICABLE',
  cobertura: 'NO_VERIFICADA', motivo: 'CONFIGURACION_AUSENTE', conteos: {}, advertencias: [] };
const circuit = { servicio: 'SENACE', estado: 'CLOSED', tasaFallosPorcentaje: -1, llamadasFallidas: 0, llamadasExitosas: 0,
  alcanceMetricas: 'LLAMADAS_LOGICAS', disponibilidadExterna: 'NO_VERIFICADA', respaldoLocal: 'NO_IMPLEMENTADO' };
export interface Fixture { status: number; body: unknown; headers?: Record<string, string> }
const headers = { 'X-Senace-Origen': 'API_DIRECTA_SENACE', 'X-Senace-Estado': 'EXITO', 'X-Senace-Cobertura': 'NO_VERIFICADA', 'X-Senace-Cuerpo-Completo': 'true' };
// Ampliacion Lote 3: respuestas artificiales coherentes con seis combinaciones.
// No modifica los fixtures historicos ni consulta/filtra datasets oficiales.
export type DashboardDemoSector = 'Hidrocarburos' | 'Minería' | 'TODOS';
export type DashboardDemoYear = 2017 | 2018;
export type DashboardDemoState = 'success' | 'empty' | 'noMatches' | 'degraded' | 'config' | 'dates' | 'optional' | 'error';
type DemoKey = `dashboardDemo:${DashboardDemoSector}:${DashboardDemoYear}:${DashboardDemoState}`;
const demoVariants = {} as Record<DemoKey, Fixture>;
for (const sector of ['Hidrocarburos', 'Minería', 'TODOS'] as const) {
  for (const year of [2017, 2018] as const) {
    const allRows: ProyectoSenacePublico[] = Array.from({ length: 8 }, (_, index) => ({
      id: year * 100 + index + 1,
      titular: `TITULAR ARTIFICIAL ${index + 1}`,
      ...(index === 2 ? {} : { ruc: `0000000000${index}` }),
      titulo: `PROYECTO DE DEMOSTRACIÓN ${index + 1}`,
      actividad: index < 4 ? 'Hidrocarburos' : 'Minería', tipo: 'TIPO ARTIFICIAL',
      fechaInicio: `0${index + 1}/04/${year}`, anio: year,
      estado: ['Aprobado', 'En Evaluación', 'Otros', 'Desaprobado'][index % 4],
      origen: 'API_DIRECTA_SENACE',
    }));
    const rows = allRows.filter((row) => sector === 'TODOS' || row.actividad === sector);
    const aprobados = rows.filter((row) => row.estado === 'Aprobado').length;
    const code = sector === 'TODOS' ? 'PM04983AA' : sector === 'Minería' ? 'PM04973AA' : 'PM04974AA';
    const scope = sector === 'TODOS' ? 'PBI nacional' : sector === 'Minería' ? 'Minería metálica' : 'Hidrocarburos';
    const value = year === 2017 ? 1.25 : -0.75; // Valores artificiales, NO observaciones BCRP.
    const economic = { ...indicator, codigoSerie: code, descripcion: `INDICADOR ARTIFICIAL · ${scope}`, sectorSolicitado: sector,
      sectorEconomico: scope, ambito: sector === 'TODOS' ? 'NACIONAL' : 'SECTORIAL', anioSolicitado: year, periodo: String(year), valor: value };
    const meta: ConsultaSenace = { ...official, conteos: { recibidos: allRows.length, aceptados: allRows.length, devueltos: rows.length, aprobados, fechasNoEvaluables: 0 } };
    const base: DashboardResponse = { total: rows.length, aprobados, proyectos: rows, indicadorBcrp: economic, variacionSeleccionada: value,
      fuentes: { senace: 'API_DIRECTA_SENACE', bcrp: 'API_DIRECTA_BCRP', sectorConsultado: sector, anioConsultado: year }, consultaSenace: meta };
    const put = (state: DashboardDemoState, body: unknown, status = 200) => {
      demoVariants[`dashboardDemo:${sector}:${year}:${state}`] = { status, body };
    };
    put('success', base);
    put('empty', { ...base, total: 0, aprobados: 0, proyectos: [], consultaSenace: { ...meta, estado: 'VACIA', estadoFiltrado: 'NO_APLICABLE', motivo: 'RESPUESTA_OFICIAL_VACIA', conteos: { recibidos: 0, aceptados: 0, devueltos: 0, aprobados: 0 } } });
    put('noMatches', { ...base, total: 0, aprobados: 0, proyectos: [], consultaSenace: { ...meta, estadoFiltrado: 'SIN_COINCIDENCIAS', motivo: 'SIN_COINCIDENCIAS', conteos: { recibidos: allRows.length, aceptados: allRows.length, devueltos: 0, aprobados: 0 } } });
    for (const state of ['degraded', 'config'] as const) put(state, {
      indicadorBcrp: economic, variacionSeleccionada: value,
      fuentes: { ...base.fuentes, senace: 'NO_DISPONIBLE' },
      consultaSenace: { ...failed, motivo: state === 'config' ? 'CONFIGURACION_AUSENTE' : 'ERROR_HTTP' },
    });
    put('dates', { ...base, consultaSenace: { ...meta, estadoFiltrado: 'NO_EVALUABLE', motivo: 'FECHAS_NO_EVALUABLES', advertencias: ['FECHA_INVALIDA'],
      conteos: { ...meta.conteos, recibidos: allRows.length + 1, aceptados: allRows.length + 1, fechasNoEvaluables: 1, fechasNoEvaluablesEnActividad: 1 } } });
    put('optional', {});
    put('error', { mensaje: 'ERROR ARTIFICIAL DE DEMOSTRACIÓN' }, 503);
  }
}
type GeoKey = `exploradorDemo:${DashboardDemoSector}:${DashboardDemoYear}`;
const geoVariants = {} as Record<GeoKey, Fixture>;
for (const sector of ['Hidrocarburos', 'Minería', 'TODOS'] as const) for (const year of [2017, 2018] as const) {
  const base = structuredClone(demoVariants[`dashboardDemo:${sector}:${year}:success`]);
  const body = base.body as DashboardResponse;
  body.proyectos = body.proyectos?.map((row, i) => ({ ...row,
    descripcion: 'Registro artificial para comprobar selección y coordenadas. No es una ubicación oficial.',
    unidadProyecto: 'UNIDAD ARTIFICIAL', resolucion: 'RESOLUCIÓN ARTIFICIAL',
    ...(i % 4 === 0 ? { latitud: -8.541234567, longitud: -74.912345678 }
      : i % 4 === 1 ? { latitud: -15.123456789, longitud: -70.234567891 }
      : i % 4 === 2 ? {} : { latitud: 95, longitud: -75 }),
  }));
  geoVariants[`exploradorDemo:${sector}:${year}`] = base;
}
// Lote 5: filas artificiales; los duplicados no son empresas únicas.
const consultantRows: ConsultoraSenacePublica[] = Array.from({ length: 12 }, (_, i) => ({
  razonSocial: `CONSULTORA ARTIFICIAL ${i + 1}`,
  actividad: i % 2 === 0 ? 'Minería' : 'Hidrocarburos',
  subsector: i % 3 === 0 ? 'SUBSECTOR ARTIFICIAL A' : 'SUBSECTOR ARTIFICIAL B',
  tipoSolicitud: i % 2 === 0 ? 'SOLICITUD ARTIFICIAL A' : 'SOLICITUD ARTIFICIAL B',
  ...(i % 4 === 0 ? {} : { resolucion: `RESOLUCIÓN ARTIFICIAL ${i + 1}` }),
  fechaRd: i === 1 ? 'FECHA ORIGINAL NO EVALUADA' : `0${i % 9 + 1}/03/2017`,
  inicioVigenciaRd: i === 2 ? '' : 'TEXTO ORIGINAL DE INICIO',
  finVigenciaRd: 'TEXTO ORIGINAL DE FIN',
}));
consultantRows.push({ ...consultantRows[0] }, {});
export const fixtures = {
  consultorasDemoSuccess: { status: 200, body: consultantRows, headers },
  consultorasDemoNoHeaders: { status: 200, body: consultantRows },
  consultorasDemoEmptyNoHeaders: { status: 200, body: [] },
  consultorasDemoInvalid: { status: 200, body: { mensaje: 'ESTRUCTURA ARTIFICIAL INCORRECTA' }, headers },
  consultorasDemoOptional: { status: 200, body: [{}, { razonSocial: 'CONSULTORA ARTIFICIAL SIN OTROS CAMPOS', resolucion: '   ' }], headers },
  ...geoVariants,
  ...demoVariants,
  dashboardSuccess: { status: 200, body: { total: 1, aprobados: 1, proyectos: [project], variacionSeleccionada: 1.25,
    indicadorBcrp: indicator, fuentes: { senace: 'API_DIRECTA_SENACE', bcrp: 'API_DIRECTA_BCRP', sectorConsultado: 'Hidrocarburos', anioConsultado: 2017 }, consultaSenace: official } },
  dashboardEmpty: { status: 200, body: { total: 0, aprobados: 0, proyectos: [], indicadorBcrp: indicator,
    consultaSenace: { ...official, estado: 'VACIA', estadoFiltrado: 'NO_APLICABLE', motivo: 'RESPUESTA_OFICIAL_VACIA', conteos: { recibidos: 0, devueltos: 0 } } } },
  dashboardNoMatches: { status: 200, body: { total: 0, aprobados: 0, proyectos: [], indicadorBcrp: indicator,
    consultaSenace: { ...official, estadoFiltrado: 'SIN_COINCIDENCIAS', motivo: 'SIN_COINCIDENCIAS', conteos: { recibidos: 1, devueltos: 0 } } } },
  dashboardDegraded: { status: 200, body: { indicadorBcrp: indicator, consultaSenace: failed,
    fuentes: { senace: 'NO_DISPONIBLE', bcrp: 'API_DIRECTA_BCRP' } } },
  dashboardDates: { status: 200, body: { total: 0, aprobados: 0, proyectos: [], indicadorBcrp: indicator,
    consultaSenace: { ...official, estadoFiltrado: 'NO_EVALUABLE', motivo: 'FECHAS_NO_EVALUABLES', advertencias: ['FECHA_INVALIDA'], conteos: { recibidos: 1, devueltos: 0, fechasNoEvaluables: 1 } } } },
  dashboardOptional: { status: 200, body: {} },
  consultantsSuccess: { status: 200, body: [consultant, { ...consultant }, {}], headers },
  consultantsEmpty: { status: 200, body: [], headers: { ...headers, 'X-Senace-Estado': 'VACIA', 'X-Senace-Motivo': 'RESPUESTA_OFICIAL_VACIA' } },
  consultants502: { status: 502, body: [], headers: { ...headers, 'X-Senace-Origen': 'NO_DISPONIBLE', 'X-Senace-Estado': 'FORMATO_INVALIDO', 'X-Senace-Motivo': 'FORMATO_INVALIDO' } },
  consultants503: { status: 503, body: [], headers: { ...headers, 'X-Senace-Origen': 'NO_DISPONIBLE', 'X-Senace-Estado': 'ERROR_EXTERNO', 'X-Senace-Motivo': 'CONFIGURACION_AUSENTE' } },
  datastreamArray: { status: 200, body: { datastream: 'CarteraProyectos', origen: 'API_DIRECTA_SENACE', recursoValidado: true,
    data: [{ ID: 1, TITULAR: 'TITULAR ARTIFICIAL', RUC: '00000000000', TITULO_PROYECTO: 'PROYECTO ARTIFICIAL', ACTIVIDAD: 'Hidrocarburos', FECHA_INICIO: '05/04/2017' }], consulta: { ...official, estadoFiltrado: 'NO_APLICABLE' } } },
  datastreamConsultants: { status: 200, body: { datastream: 'ConsultorasAmbientales', origen: 'API_DIRECTA_SENACE', recursoValidado: true,
    data: [{ RAZON_SOCIAL: 'CONSULTORA ARTIFICIAL', ACTIVIDAD: null }], consulta: { ...official, estadoFiltrado: 'NO_APLICABLE' } } },
  datastreamObject: { status: 200, body: { datastream: 'Visitas', origen: 'API_DIRECTA_SENACE', recursoValidado: false,
    data: { contenidoArtificial: [] }, consulta: { ...official, estadoFiltrado: 'NO_APLICABLE' } } },
  datastreamNull: { status: 503, body: { datastream: 'CarteraProyectos', origen: 'NO_DISPONIBLE', data: null, consulta: failed } },
  monitor: { status: 200, body: { senace: circuit, bcrp: { ...circuit, servicio: 'BCRP', estado: 'OPEN', tasaFallosPorcentaje: 100, llamadasFallidas: 5 } } },
  monitorHalfOpen: { status: 200, body: { senace: { ...circuit, estado: 'HALF_OPEN' }, bcrp: { ...circuit, servicio: 'BCRP', estado: 'DISABLED' } } },
  monitorDemoPartial: { status: 200, body: { senace: { servicio: 'SENACE', estado: 'CLOSED', llamadasExitosas: 0 } } },
  monitorDemoEmpty: { status: 200, body: {} },
  monitorDemoOptional: { status: 200, body: { senace: { servicio: 'SENACE', estado: 'CLOSED', alcanceMetricas: 'LLAMADAS_LOGICAS', disponibilidadExterna: 'NO_VERIFICADA', respaldoLocal: 'NO_IMPLEMENTADO' }, bcrp: { servicio: 'BCRP', estado: 'OPEN', alcanceMetricas: 'LLAMADAS_LOGICAS', disponibilidadExterna: 'NO_VERIFICADA', respaldoLocal: 'NO_IMPLEMENTADO' } } },
  monitorDemoError: { status: 503, body: { mensaje: 'ERROR ARTIFICIAL DE DEMOSTRACIÓN' } },
  monitorDemoInvalid: { status: 200, body: [] },
} satisfies Record<string, Fixture>;
export type Scenario = keyof typeof fixtures;
export function fixture(name: Scenario): Fixture { return structuredClone(fixtures[name]); }
