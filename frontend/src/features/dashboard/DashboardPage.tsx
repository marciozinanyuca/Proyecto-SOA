import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { createLatestRequest } from '../../api/requests';
import { NavigationIcon } from '../../components/NavigationIcon';
import type { DashboardDemoState } from '../../mocks/fixtures';
import { displayValue, distribution, initialQuery, knownProjects, loadDashboardDemo, scenarios, sectors, selectTable, stateMessage, years } from './dashboardModel';
import type { DashboardQuery, DashboardSnapshot } from './dashboardModel';
import './DashboardPage.css';

function Source({ value }: { readonly value: string | undefined }) {
  return <span className="dash-source">{value ?? 'Procedencia no disponible'} <span>· escenario simulado</span></span>;
}

export function DashboardPage() {
  const [draft, setDraft] = useState<DashboardQuery>({ ...initialQuery });
  const [scenario, setScenario] = useState<DashboardDemoState>('success');
  const [phase, setPhase] = useState<'loading' | 'settled' | 'cancelled' | 'error'>('loading');
  const [snapshot, setSnapshot] = useState<DashboardSnapshot>();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [updatedAt, setUpdatedAt] = useState<string>();
  const coordinator = useRef(createLatestRequest<DashboardSnapshot>());
  const mounted = useRef(false);

  async function load(query: DashboardQuery, requestedScenario: DashboardDemoState) {
    setPhase('loading'); setSnapshot(undefined); setSearch(''); setPage(1); setUpdatedAt(undefined);
    const completion = await coordinator.current.run((signal) => loadDashboardDemo(query, requestedScenario, signal));
    if (!mounted.current || completion.state === 'stale') return;
    if (completion.state === 'cancelled') { setPhase('cancelled'); return; }
    if (completion.state === 'error') { setPhase('error'); return; }
    setSnapshot(completion.value); setPhase('settled'); setUpdatedAt(new Date().toLocaleTimeString('es-PE'));
  }

  useEffect(() => {
    mounted.current = true;
    void load({ ...initialQuery }, 'success');
    return () => { mounted.current = false; coordinator.current.cancel(); };
  }, []);

  const result = phase === 'settled' ? snapshot?.result : undefined;
  const data = result?.state !== 'error' ? result?.data : undefined;
  const projects = result ? knownProjects(result) : undefined;
  const bars = distribution(projects);
  const max = Math.max(1, ...(bars?.map((bar) => bar.count) ?? []));
  const table = selectTable(projects, search, page);
  const economic = data?.indicadorBcrp;
  const senaceKnown = projects !== undefined;
  const economicKnown = economic?.origen === 'API_DIRECTA_BCRP' && economic.valor !== undefined;
  const changed = snapshot && (snapshot.query.sector !== draft.sector || snapshot.query.anio !== draft.anio || snapshot.scenario !== scenario);
  const busy = phase === 'loading';
  const queryLabel = snapshot ? `${snapshot.query.sector} · año de inicio ${snapshot.query.anio}` : 'Consulta de demostración';

  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void load({ ...draft }, scenario); }

  return <div className="dashboard-page">
    <h1 className="dash-sr-only">Dashboard Analítico</h1>
    <div className="dash-demo-banner"><span className="dash-demo-dot" aria-hidden="true" /><strong>MODO DEMOSTRACIÓN — DATOS ARTIFICIALES</strong><span>Sin conexión al backend</span></div>

    <section className="surface dash-filters" aria-label="Filtros de la demostración">
      <form onSubmit={submit}>
        <div className="dash-filter-grid">
          <label><span className="eyebrow">ACTIVIDAD / SECTOR</span><select value={draft.sector} onChange={(e) => setDraft({ ...draft, sector: e.target.value as DashboardQuery['sector'] })}>{sectors.map((s) => <option key={s}>{s}</option>)}</select></label>
          <label><span className="eyebrow">AÑO DE INICIO</span><select value={draft.anio} onChange={(e) => setDraft({ ...draft, anio: Number(e.target.value) as DashboardQuery['anio'] })}>{years.map((y) => <option key={y}>{y}</option>)}</select></label>
          <div className="dash-resource"><span className="eyebrow">RECURSO DE REFERENCIA</span><span>Cartera de proyectos SENACE</span></div>
          <div className="dash-load-controls"><button className="dash-primary" type="submit">{busy ? 'Aplicar nueva selección' : 'Consultar demostración'}</button>{busy && <button className="dash-secondary" type="button" onClick={() => coordinator.current.cancel()}>Cancelar</button>}</div>
        </div>
        <div className="dash-filter-bottom"><span className="dash-context">FECHA_INICIO · no fecha de aprobación</span><span>Última consulta local: {updatedAt ?? '—'}</span></div>
      </form>
      <details className="dash-demo-tools"><summary>Escenario de demostración</summary><label>Respuesta artificial<select value={scenario} onChange={(e) => setScenario(e.target.value as DashboardDemoState)}>{scenarios.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}</select></label><p>Solo se simulan Hidrocarburos, Minería y TODOS para 2017 y 2018. No es un catálogo completo de actividades ni una equivalencia automática con BCRP.</p></details>
    </section>

    <div className={`dash-feedback dash-feedback--${result?.state ?? phase}`} role="status" aria-live="polite">
      {busy ? 'Cargando respuesta artificial…' : phase === 'cancelled' ? 'Consulta cancelada. Puedes consultar otra selección.' : phase === 'error' ? 'No se pudo completar la demostración.' : result ? stateMessage(result) : 'Preparando consulta.'}
      {changed && <span>Los filtros editados aún no se han aplicado. Los datos corresponden a {queryLabel}.</span>}
    </div>

    <div className="dash-data" aria-busy={busy}>
      <section className="dash-kpis" aria-label="Indicadores de la selección">
        <article className="surface dash-kpi"><div className="dash-kpi-head"><span className="eyebrow">CARTERA SELECCIONADA</span><span className="dash-kpi-icon"><NavigationIcon kind="dashboard" /></span></div><h2>Registros de proyectos</h2><p className="dash-kpi-value">{busy ? 'Cargando…' : displayValue(senaceKnown ? data?.total : undefined)}</p><p className="dash-kpi-unit">Filtrados por actividad y año de inicio</p><Source value={data?.fuentes?.senace ?? data?.consultaSenace?.origen} /><div className="dash-kpi-footer">{senaceKnown ? 'Selección conocida; no proyectos únicos certificados.' : 'Conteo SENACE no disponible.'}</div></article>
        <article className="surface dash-kpi"><div className="dash-kpi-head"><span className="eyebrow">ESTADO SENACE</span><span className="dash-kpi-icon dash-kpi-icon--green"><NavigationIcon kind="consultoras" /></span></div><h2>Registros aprobados</h2><p className="dash-kpi-value dash-positive">{busy ? 'Cargando…' : displayValue(senaceKnown ? data?.aprobados : undefined)}</p><p className="dash-kpi-unit">Con estado Aprobado en esta selección</p><Source value={data?.fuentes?.senace ?? data?.consultaSenace?.origen} /><div className="dash-kpi-footer">No representa viabilidad ni habilitación administrativa.</div></article>
        <article className="surface dash-kpi"><div className="dash-kpi-head"><span className="eyebrow">CONTEXTO MACROECONÓMICO</span><span className="dash-kpi-icon"><NavigationIcon kind="monitor" /></span></div><h2>Indicador económico BCRP</h2><p className={`dash-kpi-value${economicKnown && economic.valor! < 0 ? ' dash-negative' : ''}`}>{busy ? 'Cargando…' : economicKnown ? `${displayValue(economic.valor)} %` : 'No disponible'}</p><p className="dash-kpi-unit">{economic?.unidad ?? 'Unidad no disponible'} · período {economic?.periodo ?? 'no disponible'}</p><Source value={economic?.origen} /><div className="dash-kpi-footer">{economic?.sectorEconomico ?? 'Ámbito económico no disponible'} · {economic?.codigoSerie ?? 'sin serie'}</div></article>
      </section>

      <section className="surface dash-chart" aria-labelledby="distribution-heading">
        <div className="dash-panel-heading"><div><span className="eyebrow">REGISTROS DE LA SELECCIÓN</span><h2 id="distribution-heading">Distribución por estado</h2><p>{queryLabel}. Sin series mensuales ni correlación económica.</p></div><span className="dash-legend"><i aria-hidden="true" />Cantidad de registros</span></div>
        {bars === undefined ? <div className="dash-empty">{busy ? 'Esperando la selección…' : 'Distribución no disponible: no hay una lista utilizable de proyectos.'}</div> : bars.length === 0 ? <div className="dash-empty">La selección conocida está vacía. No hay registros para distribuir.</div> : <>
          <div className="dash-bar-chart" aria-hidden="true">{bars.map((bar, i) => <div className="dash-bar-column" key={bar.estado}><strong>{bar.count}</strong><div className="dash-bar-track"><div className={`dash-bar dash-bar--${i % 2}`} style={{ height: `${bar.count / max * 100}%` }} /></div><span>{bar.estado}</span></div>)}</div>
          <table className="dash-sr-only"><caption>Alternativa accesible: distribución de los registros seleccionados por estado</caption><thead><tr><th scope="col">Estado</th><th scope="col">Registros</th></tr></thead><tbody>{bars.map((bar) => <tr key={bar.estado}><th scope="row">{bar.estado}</th><td>{bar.count}</td></tr>)}</tbody></table>
        </>}
        <div className="dash-chart-foot">Agregado calculado solo sobre las filas recibidas de esta selección artificial. No representa el universo oficial.</div>
      </section>

      <section className="surface dash-records" aria-labelledby="records-heading">
        <div className="dash-panel-heading"><div><span className="eyebrow">CARTERA DE PROYECTOS</span><h2 id="records-heading">Registros de proyectos SENACE</h2><p>Campos disponibles en la respuesta. Sin verificación SUNAT o expedientes inferidos.</p></div><label className="dash-search"><span>Buscar en la selección</span><input type="search" value={search} disabled={projects === undefined} placeholder="Titular, RUC, proyecto…" onChange={(e) => { setSearch(e.target.value); setPage(1); }} /></label></div>
        {table.rows === undefined ? <div className="dash-empty">{busy ? 'Cargando registros artificiales…' : 'Los registros no están disponibles. Esto no equivale a cero proyectos.'}</div> : table.rows.length === 0 ? <div className="dash-empty">{search ? 'No hay coincidencias para esta búsqueda local.' : 'No hay registros en la selección conocida.'}</div> : <div className="dash-table-scroll" role="region" aria-label="Tabla de proyectos, desplazamiento horizontal si es necesario" tabIndex={0}><table className="dash-table"><caption className="dash-sr-only">Proyectos artificiales de {queryLabel}</caption><thead><tr>{['Titular / empresa', 'RUC del titular', 'Proyecto', 'Actividad', 'Fecha de inicio', 'Estado'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead><tbody>{table.rows.map((row, i) => <tr key={`${table.page}-${i}`}><td><span className="dash-table-title">{row.titular ?? 'No disponible'}</span></td><td className="dash-mono">{row.ruc ?? 'No disponible'}</td><td>{row.titulo ?? row.proyecto ?? 'No disponible'}</td><td>{row.actividad ?? 'No disponible'}</td><td className="dash-mono">{row.fechaInicio ?? 'No disponible'}</td><td><span className={`dash-status${row.estado === 'Aprobado' ? ' dash-status--approved' : ''}`}>{row.estado ?? 'No disponible'}</span></td></tr>)}</tbody></table></div>}
        {table.total !== undefined && <div className="dash-pagination"><span>Mostrando {table.start}–{table.end} de {table.total} filas{search && ' de la búsqueda local'}</span><nav aria-label="Paginación de la tabla"><button type="button" className="dash-secondary" disabled={table.page <= 1} onClick={() => setPage(table.page - 1)}>Anterior</button><span>Página {table.pages ? table.page : 0} de {table.pages}</span><button type="button" className="dash-secondary" disabled={table.page >= table.pages} onClick={() => setPage(table.page + 1)}>Siguiente</button></nav></div>}
      </section>

      <section className="surface dash-provenance" aria-labelledby="provenance-heading"><h2 id="provenance-heading">Procedencia, calidad y cobertura</h2><dl><div><dt>Modo</dt><dd>{snapshot?.http.mode === 'mock' ? 'MOCK · datos artificiales' : 'Demostración en preparación'}</dd></div><div><dt>HTTP simulado</dt><dd>{snapshot?.http.status ?? 'No disponible'}</dd></div><div><dt>Consulta SENACE</dt><dd>{data?.consultaSenace?.estado ?? 'No disponible'}</dd></div><div><dt>Estado de filtrado</dt><dd>{data?.consultaSenace?.estadoFiltrado ?? 'No disponible'}</dd></div><div><dt>Cobertura</dt><dd>{data?.consultaSenace?.cobertura ?? 'No disponible'}</dd></div><div><dt>Cuerpo completo simulado</dt><dd>{data?.consultaSenace?.cuerpoCompleto === undefined ? 'Desconocido' : data.consultaSenace.cuerpoCompleto ? 'Sí; no certifica cobertura' : 'No'}</dd></div><div><dt>Fechas no evaluables</dt><dd>{displayValue(data?.consultaSenace?.conteos?.fechasNoEvaluables)}</dd></div><div><dt>Motivo seguro</dt><dd>{result?.reason ?? 'Sin motivo adicional disponible'}</dd></div></dl><p>Los decimales mostrados usan la precisión de JavaScript; no se realizan cálculos económicos exactos. La cobertura completa del dataset no está verificada.</p></section>
    </div>
  </div>;
}
