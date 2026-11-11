import { useEffect, useMemo, useRef, useState } from 'react';
import { createLatestRequest } from '../../api/requests';
import { NavigationIcon } from '../../components/NavigationIcon';
import type { SourceConfig } from '../../api/source';
import { consultantFields, consultantKpis, consultantMessage, consultantScenarios, displayField, emptyFilters, filterConsultants, filterOptions, loadConsultants, paginateConsultants, usableRows } from './consultorasModel';
import type { ConsultantFilters, ConsultantScenario, ConsultantSnapshot } from './consultorasModel';
import './ConsultorasPage.css';

export function ConsultorasPage({ source }: { readonly source: SourceConfig }) {
  const isMock = source.valid && source.mode === 'mock';
  const [scenario, setScenario] = useState<ConsultantScenario>('consultorasDemoSuccess');
  const [snapshot, setSnapshot] = useState<ConsultantSnapshot>();
  const [phase, setPhase] = useState<'idle' | 'loading' | 'settled' | 'cancelled' | 'error'>(isMock ? 'loading' : 'idle');
  const [filters, setFilters] = useState<ConsultantFilters>({ ...emptyFilters });
  const [page, setPage] = useState(1); const [selected, setSelected] = useState<string>();
  const [updatedAt, setUpdatedAt] = useState<string>();
  const coordinator = useRef(createLatestRequest<ConsultantSnapshot>()); const mounted = useRef(false);
  const detailHeading = useRef<HTMLHeadingElement>(null); const opener = useRef<HTMLButtonElement | null>(null);
  const settled = phase === 'settled' ? snapshot : undefined;
  const rows = usableRows(settled);
  const options = useMemo(() => ({ actividad: filterOptions(rows, 'actividad'), subsector: filterOptions(rows, 'subsector'), tipoSolicitud: filterOptions(rows, 'tipoSolicitud') }), [rows]);
  const filtered = useMemo(() => filterConsultants(rows, filters), [rows, filters]);
  const table = paginateConsultants(filtered, page);
  const kpis = useMemo(() => consultantKpis(settled, filtered), [settled, filtered]);
  const chosen = filtered?.find(entry => entry.key === selected);
  const busy = phase === 'loading';
  async function load(requested: ConsultantScenario) {
    setPhase('loading'); setSnapshot(undefined); setSelected(undefined); setPage(1); setFilters({ ...emptyFilters }); setUpdatedAt(undefined);
    const completion = await coordinator.current.run(signal => loadConsultants(requested, signal, source));
    if (!mounted.current || completion.state === 'stale') return;
    if (completion.state !== 'current') { setPhase(completion.state === 'cancelled' ? 'cancelled' : 'error'); return; }
    setSnapshot(completion.value); setPhase('settled'); setUpdatedAt(new Date().toLocaleTimeString('es-PE'));
  }
  useEffect(() => {
    mounted.current = true;
    // HTTP solo por accion manual; no GET al montar ni en StrictMode.
    if (isMock) void load('consultorasDemoSuccess');
    return () => { mounted.current = false; coordinator.current.cancel(); };
  }, []);
  useEffect(() => { if (chosen) detailHeading.current?.focus(); }, [chosen?.key]);
  function updateFilters(next: ConsultantFilters) { setFilters(next); setPage(1); setSelected(undefined); }
  function changePage(next: number) { setPage(next); setSelected(undefined); }
  function closeDetail() { setSelected(undefined); opener.current?.focus(); }
  function count(value: number | undefined) { return value === undefined ? 'No disponible' : value.toLocaleString('es-PE'); }
  const metadata = settled?.result.data?.consulta;

  return <div className="consultants-page">
    <h1 className="consultants-sr">Consultoras Ambientales</h1>
    <div className="consultants-demo"><strong>{isMock ? 'MODO DEMOSTRACIÓN — DATOS ARTIFICIALES' : 'MODO HTTP — BACKEND CONFIGURADO'}</strong><span>{isMock ? 'Sin conexión al backend' : 'Consulta manual del recurso ConsultorasAmbientales'}</span></div>
    <section className="surface consultants-filters" aria-label="Filtros locales de consultoras">
      <div className="consultants-filter-grid">
        <label className="consultants-search"><span>BÚSQUEDA LOCAL</span><input type="search" value={filters.search} disabled={rows === undefined} placeholder="Razón social o cualquiera de los ocho campos" onChange={event => updateFilters({ ...filters, search: event.target.value })} /></label>
        {(['actividad', 'subsector', 'tipoSolicitud'] as const).map((key, index) => <label key={key}><span>{['ACTIVIDAD', 'SUBSECTOR', 'TIPO DE SOLICITUD'][index]}</span><select disabled={rows === undefined} value={filters[key]} onChange={event => updateFilters({ ...filters, [key]: event.target.value })}><option value="">Todos</option>{options[key].map(value => <option key={value}>{value}</option>)}</select></label>)}
      </div>
      <div className="consultants-filter-footer"><p>Filtros sobre filas cargadas, sin paginación de servidor ni nuevas consultas.</p><button type="button" disabled={!Object.values(filters).some(Boolean)} onClick={() => updateFilters({ ...emptyFilters })}>Limpiar filtros</button></div>
      {!isMock && <div className="consultants-scenario"><button type="button" className="consultants-primary" disabled={busy} onClick={() => { void load(scenario); }}>{busy ? 'Consultando…' : 'Consultar Consultoras por HTTP'}</button>{busy && <button type="button" onClick={() => coordinator.current.cancel()}>Cancelar</button>}<p>Última consulta completada · hora local del cliente: {updatedAt ?? 'Sin consulta completada'}</p></div>}
      {isMock && <details className="consultants-tools"><summary>Escenario de demostración</summary><div className="consultants-scenario"><label>Respuesta artificial<select value={scenario} onChange={event => setScenario(event.target.value as ConsultantScenario)}>{consultantScenarios.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><button type="button" className="consultants-primary" onClick={() => { void load(scenario); }}>Consultar demostración</button>{busy && <button type="button" onClick={() => coordinator.current.cancel()}>Cancelar</button>}</div><p>Última consulta local: {updatedAt ?? '—'}. Cambiar el escenario requiere aplicarlo; no altera por sí solo los datos mostrados.</p></details>}
    </section>
    <div className={`consultants-feedback consultants-feedback--${settled?.result.state ?? phase}`} role="status" aria-live="polite">{busy ? isMock ? 'Cargando filas artificiales…' : 'Consultando Consultoras mediante el backend configurado…' : phase === 'cancelled' ? 'Consulta cancelada. No se publican filas de la operación cancelada.' : phase === 'error' ? 'No se pudo completar la consulta. No se activa una fuente alternativa.' : settled ? consultantMessage(settled) : 'Sin resultado. Pulsa Consultar Consultoras por HTTP para iniciar una consulta manual.'}{isMock && settled && settled.scenario !== scenario && <p>Escenario pendiente de aplicar. Los datos corresponden a {consultantScenarios.find(item => item.value === settled.scenario)?.label}.</p>}</div>
    <div className="consultants-content" aria-busy={busy}>
      <section className="consultants-kpis" aria-label="Conteos de filas, no empresas únicas">
        {[
          { title: 'Filas recibidas', label: 'RESPUESTA CARGADA', value: kpis.received, detail: 'Incluye duplicados y filas con campos ausentes.' },
          { title: 'Filas filtradas', label: 'SELECCIÓN LOCAL', value: kpis.filtered, detail: 'Coincidencias de búsqueda y filtros actuales.' },
          { title: 'Con resolución informada', label: 'CAMPO DISPONIBLE', value: kpis.withResolution, detail: 'Filas filtradas con texto de resolución; no acredita autorización.' },
        ].map((item, index) => <article className="surface consultants-kpi" key={item.title}><div className="consultants-kpi-top"><span className="eyebrow">{item.label}</span><span className={`consultants-kpi-icon consultants-kpi-icon--${index}`}><NavigationIcon kind="consultoras" /></span></div><h2>{item.title}</h2><p className={`consultants-kpi-value consultants-kpi-value--${index}`}>{busy ? 'Cargando…' : count(item.value)}</p><p className="consultants-kpi-description">{item.detail}</p><div className="consultants-kpi-source">{settled?.result.state === 'degraded' ? 'PROCEDENCIA NO CONFIRMADA' : settled?.result.state === 'ready' || settled?.result.state === 'empty' ? isMock ? 'SENACE · ESCENARIO SIMULADO' : 'SENACE · PROCEDENCIA INFORMADA' : 'FUENTE NO DISPONIBLE'}</div></article>)}
      </section>
      <section className="surface consultants-records" aria-labelledby="consultants-table-title">
        <header className="consultants-panel-heading"><div><span className="eyebrow">RECURSO CONSULTORASAMBIENTALES</span><h2 id="consultants-table-title">Directorio de filas de consultoras ambientales</h2><p>Ocho campos de la respuesta, con orden y duplicados conservados. Sin RUC, registro, nivel ni estado administrativo inferidos.</p></div><span className="consultants-badge">{metadata?.cobertura ?? 'COBERTURA NO INFORMADA'}</span></header>
        {table.rows === undefined ? <p className="consultants-placeholder">{busy ? isMock ? 'Esperando la respuesta artificial…' : 'Esperando la respuesta del backend…' : 'Filas no disponibles. Un error no equivale a cero consultoras.'}</p> : table.rows.length === 0 ? <p className="consultants-placeholder">{settled?.result.state === 'degraded' ? 'Array recibido vacío, sin procedencia confirmada. No acredita un vacío oficial.' : rows?.length === 0 ? isMock ? 'Respuesta oficial vacía simulada: no hay filas para mostrar.' : 'Respuesta oficial vacía: no hay filas para mostrar.' : 'No hay coincidencias con los filtros locales.'}</p> : <div className="consultants-table-scroll" tabIndex={0} role="region" aria-label="Tabla de consultoras con desplazamiento horizontal local"><table className="consultants-table"><caption className="consultants-sr">{isMock ? 'Filas artificiales' : 'Filas recibidas del backend'} de ConsultorasAmbientales. Las fechas se conservan sin interpretar vigencia.</caption><thead><tr>{consultantFields.map(([key, label]) => <th key={key} scope="col">{label}</th>)}<th scope="col">Detalle</th></tr></thead><tbody>{table.rows.map(entry => <tr key={entry.key} className={selected === entry.key ? 'consultants-row-selected' : undefined}>{consultantFields.map(([key]) => <td key={key} className={key === 'razonSocial' ? 'consultants-name' : key === 'actividad' || key === 'tipoSolicitud' ? 'consultants-category' : undefined}>{displayField(entry.row[key])}</td>)}<td><button type="button" aria-expanded={selected === entry.key} aria-controls="consultants-detail" aria-label={`Ver detalle de ${displayField(entry.row.razonSocial)}, fila ${Number(entry.key) + 1} de la respuesta`} onClick={event => { opener.current = event.currentTarget; setSelected(entry.key); }}>Ver detalle</button></td></tr>)}</tbody></table></div>}
        {table.total !== undefined && <div className="consultants-pagination"><span>Mostrando {table.start}–{table.end} de {table.total} filas filtradas · máximo 10 filas por página</span><nav aria-label="Paginación local de consultoras"><button type="button" disabled={table.page <= 1} onClick={() => changePage(table.page - 1)}>Anterior</button><span>Página {table.pages ? table.page : 0} de {table.pages}</span><button type="button" disabled={table.page >= table.pages} onClick={() => changePage(table.page + 1)}>Siguiente</button></nav></div>}
      </section>
      <section className="surface consultants-detail" id="consultants-detail" aria-labelledby="consultants-detail-title"><div className="consultants-panel-heading"><h2 id="consultants-detail-title" ref={detailHeading} tabIndex={-1}>Detalle de la fila seleccionada</h2>{chosen && <button type="button" onClick={closeDetail}>Cerrar detalle</button>}</div>{chosen ? <div className="consultants-detail-body"><p>Fila {Number(chosen.key) + 1} de esta respuesta; no es un identificador oficial de empresa.</p><dl>{consultantFields.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{displayField(chosen.row[key])}</dd></div>)}</dl><p>Las fechas son textos originales. No se calcula estado activo, vencido, habilitación ni vigencia administrativa.</p></div> : <p className="consultants-placeholder">Selecciona Ver detalle en una fila. No se realizan solicitudes adicionales.</p>}</section>
      <section className="surface consultants-metadata" aria-labelledby="consultants-meta-title"><h2 id="consultants-meta-title">Procedencia y metadatos HTTP</h2><dl><div><dt>Modo configurado</dt><dd>{isMock ? 'MOCK · DATOS ARTIFICIALES' : 'HTTP · BACKEND CONFIGURADO'}</dd></div><div><dt>{isMock ? 'HTTP simulado' : 'HTTP backend'}</dt><dd>{settled?.http.status ?? 'No disponible'}</dd></div><div><dt>{isMock ? 'X-Senace-Origen · simulado' : 'X-Senace-Origen'}</dt><dd>{metadata?.origen ?? 'No informado'}</dd></div><div><dt>X-Senace-Estado</dt><dd>{metadata?.estado ?? 'No informado'}</dd></div><div><dt>X-Senace-Cobertura</dt><dd>{metadata?.cobertura ?? 'No informada'}</dd></div><div><dt>X-Senace-Cuerpo-Completo</dt><dd>{metadata?.cuerpoCompleto === undefined ? 'No informado' : metadata.cuerpoCompleto ? 'true · no certifica cobertura' : 'false'}</dd></div><div><dt>Motivo seguro</dt><dd>{settled?.http.issue ?? settled?.result.reason ?? 'Sin motivo adicional disponible'}</dd></div><div><dt>Headers de respuesta</dt><dd>{settled?.http.headers['content-type'] ?? 'No disponibles'}</dd></div></dl><p>Los conteos describen filas recibidas o filtradas, no empresas únicas ni un padrón completo certificado. No se deduce vigencia a partir de fechas.</p></section>
    </div>
  </div>;
}
