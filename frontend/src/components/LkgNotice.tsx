import type { LkgMetadata } from '../api/types';
import { ageLabel, captureLabel, originLabel, safeLkgReason } from '../api/lkg';
import './LkgNotice.css';

/** Solo presentacion del resultado recibido. No refresca, recalcula TTL ni lee archivos. */
export function LkgNotice({ source, origin, metadata, mock = false }: {
  readonly source: 'SENACE' | 'BCRP'; readonly origin?: string; readonly metadata?: LkgMetadata; readonly mock?: boolean;
}) {
  if (origin === undefined && metadata === undefined) return null;
  const stored = metadata?.respaldo;
  const backed = origin === 'RESPALDO_LOCAL';
  const failedWrite = metadata?.escritura !== undefined
    && ['IO_ERROR', 'INVALID', 'ATOMIC_MOVE_UNSUPPORTED', 'OWNERSHIP_UNAVAILABLE'].includes(metadata.escritura);
  return <section className={`lkg-notice${backed || failedWrite ? ' lkg-notice--warning' : ''}`} aria-label={`Procedencia ${source}`}>
    <h3>{source} · {originLabel(origin)}</h3>
    {mock && <p><strong>Escenario artificial de demostración; no observación oficial.</strong></p>}
    {backed && <p>Se utilizan datos históricos respaldados. No representan una consulta nueva satisfactoria al proveedor.</p>}
    {stored && <dl>
      <div><dt>Captura persistida confirmada</dt><dd>{stored.capturadoEn ? <time dateTime={stored.capturadoEn}>{captureLabel(stored.capturadoEn)}</time> : 'No informada'}</dd></div>
      <div><dt>Edad informada al recibir la respuesta</dt><dd>{ageLabel(stored.edadSegundos)}</dd></div>
      <div><dt>Antigüedad</dt><dd>{stored.antiguo === undefined ? 'No informada' : stored.antiguo ? 'Supera el umbral de advertencia' : 'Dentro del umbral de advertencia informado'}</dd></div>
    </dl>}
    {stored?.antiguo && <p><strong>Respaldo antiguo:</strong> supera el umbral de advertencia informado por el backend. No se recalcula su vencimiento en el navegador.</p>}
    {metadata && <dl>
      <div><dt>Último intento API informado</dt><dd>{metadata.intentoApi?.estado ?? 'No informado'}</dd></div>
      <div><dt>Motivo del intento API</dt><dd>{safeLkgReason(metadata.intentoApi?.motivo)}</dd></div>
      <div><dt>Lectura del respaldo</dt><dd>{metadata.lectura ?? 'No realizada o no informada'}</dd></div>
      <div><dt>Actualización del respaldo</dt><dd>{metadata.escritura ?? 'No realizada o no informada'}</dd></div>
    </dl>}
    {failedWrite && <p><strong>No se pudo actualizar el respaldo.</strong> Esto no invalida los datos directos recibidos. No se acredita una nueva captura persistida.</p>}
    {metadata?.escritura === 'EMPTY_REPLACEMENT_REJECTED' && <p>La respuesta vacía actual no reemplazó la copia anterior no vacía.</p>}
    {metadata?.escritura === 'OLDER_RESPONSE_REJECTED' && <p>Esta respuesta no reemplazó una captura de solicitud posterior o con la misma marca temporal.</p>}
    {origin === 'SIN_RESPALDO' && <p>No hay datos utilizables para esta consulta. La lectura informa si el archivo falta, está vencido, es inválido o no pudo comprobarse.</p>}
    {backed && <p>La completitud del cuerpo utilizado corresponde a la captura histórica; no acredita cobertura oficial ni disponibilidad externa actual.</p>}
  </section>;
}
