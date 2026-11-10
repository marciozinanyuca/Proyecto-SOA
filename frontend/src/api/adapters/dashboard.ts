import type { HttpResult } from '../client.js';
import type { Adapted, DashboardResponse } from '../types.js';
import { arrayOf, consulta, count, fields, finite, integer, invalid, official, oneOf, origins, precisionWarnings, string, textFields } from '../validation.js';

function project(value: unknown): boolean {
  return fields(value, { ...textFields(['titular', 'ruc', 'titulo', 'proyecto', 'unidadProyecto', 'tipo', 'actividad', 'sector', 'fechaInicio', 'estado', 'descripcion', 'resolucion']),
    id: integer, anio: integer, longitud: finite, latitud: finite, origen: oneOf(origins) });
}
function indicator(value: unknown): boolean {
  return fields(value, { ...textFields(['codigoSerie', 'descripcion', 'unidad', 'periodicidad', 'sectorSolicitado', 'sectorEconomico', 'ambito', 'periodo', 'origen', 'motivo', 'urlFuente']), anioSolicitado: integer, valor: finite });
}
export function adaptDashboard(response: HttpResult): Adapted<DashboardResponse> {
  if (response.issue) return invalid(response.issue);
  if (response.status !== 200) return invalid('HTTP_NO_ADMITIDO');
  if (!fields(response.json, { total: count, aprobados: count, variacionSeleccionada: finite, proyectos: arrayOf(project),
    indicadorBcrp: indicator, consultaSenace: consulta,
    fuentes: (v) => fields(v, { senace: oneOf(origins), bcrp: string, sectorConsultado: string, anioConsultado: integer }),
  })) return invalid();
  const data = response.json as DashboardResponse;
  if (data.total !== undefined && data.aprobados !== undefined && data.aprobados > data.total) return invalid('CONTEOS_INCOHERENTES');
  if (data.total !== undefined && data.proyectos !== undefined && data.total !== data.proyectos.length) return invalid('CONTEOS_INCOHERENTES');
  const meta = data.consultaSenace;
  const senace = official(meta);
  const bcrp = data.indicadorBcrp?.origen === 'API_DIRECTA_BCRP' && data.indicadorBcrp.valor !== undefined;
  if (data.variacionSeleccionada !== undefined && data.indicadorBcrp?.valor !== undefined
    && data.variacionSeleccionada !== data.indicadorBcrp.valor) return invalid('INDICADOR_INCOHERENTE');
  if (data.fuentes?.senace && meta?.origen && data.fuentes.senace !== meta.origen) return invalid('PROCEDENCIA_INCOHERENTE');
  if (data.fuentes?.bcrp && data.indicadorBcrp?.origen && data.fuentes.bcrp !== data.indicadorBcrp.origen) return invalid('PROCEDENCIA_INCOHERENTE');
  const warnings = precisionWarnings();
  if (!meta) warnings.push('METADATOS_SENACE_AUSENTES');
  if (meta?.estadoFiltrado === 'NO_EVALUABLE') warnings.push('FECHAS_NO_EVALUABLES');
  const degraded = !senace || !bcrp || meta?.estadoFiltrado === 'NO_EVALUABLE';
  return { state: degraded ? 'degraded' : data.proyectos?.length === 0 ? 'empty' : 'ready', data, warnings,
    ...(meta?.motivo ? { reason: meta.motivo } : {}) };
}
