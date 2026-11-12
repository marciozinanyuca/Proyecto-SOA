import type { ConsultoraSenacePublica, DashboardResponse, DatastreamResponse, LkgMetadata, EstadoModuloLkg } from './types.js';

// Comprobaciones estaticas de contrato; este modulo no se importa desde la aplicacion.
const optional: DashboardResponse = {};
const textRuc: DashboardResponse = { proyectos: [{ ruc: '00000000000' }] };
// @ts-expect-error RUC no es numerico.
const numericRuc: DashboardResponse = { proyectos: [{ ruc: 123 }] };
// @ts-expect-error Consultoras no expone RUC inventado.
const invented: ConsultoraSenacePublica = { ruc: 'NO_OFICIAL' };
function unchecked(generic: DatastreamResponse) {
  // @ts-expect-error data unknown requiere validacion antes de acceder.
  return generic.data.ID;
}
void optional; void textRuc; void numericRuc; void invented; void unchecked;
const optionalLkg: LkgMetadata = {};
const backed: DashboardResponse = { consultaSenace: { origen: 'RESPALDO_LOCAL', lkg: { lectura: 'AVAILABLE' } } };
const monitor: EstadoModuloLkg = { estado: 'HABILITADO_SIN_VERIFICACION', observaciones: [] };
// @ts-expect-error La edad no es un texto.
const invalidAge: LkgMetadata = { respaldo: { edadSegundos: '24' } };
// @ts-expect-error Activo/vigente no es un estado LKG.
const invalidState: EstadoModuloLkg = { estado: 'ACTIVO' };
void optionalLkg; void backed; void monitor; void invalidAge; void invalidState;
