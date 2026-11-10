import type { ConsultoraSenacePublica, DashboardResponse, DatastreamResponse } from './types.js';

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
