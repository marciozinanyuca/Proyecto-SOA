import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { createLatestRequest } from '../../api/requests';
import type { SourceConfig } from '../../api/source';
import { sectors, years, scenarios, stateMessage } from '../dashboard/dashboardModel';
import type { DashboardQuery, DashboardSnapshot } from '../dashboard/dashboardModel';
import { coordinateState, createExplorerIndex, explorerSpatial, filterIndexedExplorer, initialSelection, loadExplorer, pagePointGroups, paginateExplorer, previousHttpSelection, previousSelection } from './exploradorModel';
import type { ExplorerPointGroup } from './exploradorModel';
import type { ExplorerScenario } from './exploradorModel';
import './ExploradorPage.css';

export function ExploradorPage({ source }: { readonly source: SourceConfig }) {
  const isMock = source.valid && source.mode === 'mock';
  const [restored] = useState(() => isMock ? undefined : previousHttpSelection());
  const [draft, setDraft] = useState<DashboardQuery>(() => isMock ? initialSelection() : restored?.query ?? { sector: 'Hidrocarburos', anio: 2017 });
  const [scenario, setScenario] = useState<ExplorerScenario>(() => previousSelection()?.scenario ?? 'coordinates');
  const [snapshot, setSnapshot] = useState<DashboardSnapshot | undefined>(restored);
  const [appliedScenario, setAppliedScenario] = useState<ExplorerScenario>();
  const [phase, setPhase] = useState<'idle' | 'loading' | 'settled' | 'cancelled' | 'error'>(isMock ? 'loading' : restored ? 'settled' : 'idle');
  const [state, setState] = useState(''); const [search, setSearch] = useState('');
  const [selection, setSelection] = useState<string>(); const [zoom, setZoom] = useState(1);
  const [page, setPage] = useState(1);
  const [coordinateFilter, setCoordinateFilter] = useState<string>();
  const requests = useRef(createLatestRequest<DashboardSnapshot>()); const mounted = useRef(false);
  async function load(query: DashboardQuery, requested: ExplorerScenario) {
    if (!isMock) return; // HTTP es solo lectura de la respuesta del Dashboard.
    setPhase('loading'); setSnapshot(undefined); setSelection(undefined); setState(''); setSearch(''); setZoom(1); setPage(1); setCoordinateFilter(undefined);
    const response = await requests.current.run(signal => loadExplorer(query, requested, signal));
    if (!mounted.current || response.state === 'stale') return;
    if (response.state !== 'current') { setPhase(response.state === 'cancelled' ? 'cancelled' : 'error'); return; }
    setSnapshot(response.value); setAppliedScenario(requested); setPhase('settled');
  }
  useEffect(() => {
    mounted.current = true;
    if (isMock) {
      const previous = previousSelection();
      void load(previous?.query ?? initialSelection(), previous?.scenario ?? 'coordinates');
    }
    return () => { mounted.current = false; requests.current.cancel(); };
  }, []);
  const settled = phase === 'settled' ? snapshot : undefined;
  const index = useMemo(() => createExplorerIndex(settled), [settled]);
  const entries = useMemo(() => filterIndexedExplorer(index, state, search), [index, state, search]);
  const spatial = useMemo(() => explorerSpatial(index, entries), [index, entries]);
  const listed = coordinateFilter === undefined ? entries : spatial?.groups.get(coordinateFilter)?.entries;
  const paginated = useMemo(() => paginateExplorer(listed, page), [listed, page]);
  const markers = useMemo(() => pagePointGroups(index, spatial, paginated.rows, selection), [index, spatial, paginated.rows, selection]);
  const all = index?.entries;
  const states = index?.states ?? [];
  const chosen = selection === undefined ? undefined : spatial?.byKey.get(selection); const row = chosen?.row;
  const busy = phase === 'loading';
  const changed = isMock && settled && (draft.sector !== settled.query.sector || draft.anio !== settled.query.anio || scenario !== appliedScenario);
  // El encuadre usa toda la selección filtrada, independientemente de la página.
  const { x: minX, y: minY, width, height } = spatial?.bounds ?? { x: 0, y: 0, width: 100, height: 100 };
  const boxWidth = width / zoom, boxHeight = height / zoom;
  const viewBox = `${minX + (width - boxWidth) / 2} ${minY + (height - boxHeight) / 2} ${boxWidth} ${boxHeight}`;
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void load({ ...draft }, scenario); }
  function choose(key: string) { setSelection(key); }
  function resetSelection() { setSelection(undefined); setCoordinateFilter(undefined); setPage(1); }
  function choosePoint(group: ExplorerPointGroup) {
    if (group.entries.length === 1) { setSelection(group.entries[0]!.key); return; }
    setCoordinateFilter(group.key); setPage(1); setSelection(undefined);
  }
  const text = (value: string | number | undefined) => value === undefined || value === '' ? 'No disponible' : String(value);

  return <div className="explorer-page">
    <h1 className="explorer-sr">Explorador Geográfico</h1>
    <div className="explorer-demo"><strong>{isMock ? 'MODO DEMOSTRACIÓN — DATOS ARTIFICIALES' : 'MODO HTTP — RESPUESTA COMPARTIDA DEL BACKEND'}</strong><span>{isMock ? 'Offline · sin backend ni cartografía externa' : 'Sin consultas adicionales ni cartografía externa'}</span></div>
    <section className="surface explorer-filters" aria-label="Filtros del Explorador">
      <form onSubmit={submit}><div className="explorer-filter-grid">
        <label>ACTIVIDAD / SECTOR<select disabled={!isMock} value={draft.sector} onChange={e => setDraft({ ...draft, sector: e.target.value })}>{(isMock ? sectors : [draft.sector]).map(value => <option key={value}>{value}</option>)}</select></label>
        <label>AÑO DE INICIO<select disabled={!isMock} value={draft.anio} onChange={e => setDraft({ ...draft, anio: Number(e.target.value) })}>{(isMock ? years : [draft.anio]).map(value => <option key={value}>{value}</option>)}</select></label>
        <label>ESTADO · FILTRO LOCAL<select disabled={all === undefined} value={state} onChange={e => { setState(e.target.value); resetSelection(); setZoom(1); }}><option value="">Todos los estados</option>{states.map(value => <option key={value}>{value}</option>)}</select></label>
        {isMock ? <button className="explorer-primary" type="submit">Consultar demostración</button> : <a href="#/dashboard">Cambiar selección en Dashboard</a>}
      </div><p>{isMock ? 'FECHA_INICIO, no fecha de aprobación. Sectores y años limitados a los mocks aprobados.' : 'Selección efectiva del Dashboard. Los filtros de estado y búsqueda son locales. No se vuelve a consultar al proveedor.'}</p></form>
      {isMock && <details className="explorer-tools"><summary>Escenario de demostración</summary><label>Respuesta artificial<select value={scenario} onChange={e => setScenario(e.target.value as ExplorerScenario)}><option value="coordinates">Coordenadas artificiales: válidas, ausentes y fuera de rango</option>{scenarios.map(item => <option value={item.value} key={item.value}>{item.label}</option>)}</select></label><p>El escenario de coordenadas añade fixtures separados. La selección heredada del Dashboard no se completa con ubicaciones inventadas.</p></details>}
    </section>
    <div className={`explorer-feedback explorer-feedback--${settled?.result.state ?? phase}`} role="status" aria-live="polite">{busy ? 'Cargando selección artificial…' : phase === 'cancelled' ? 'Consulta cancelada.' : phase === 'error' ? 'No se pudo completar la demostración.' : settled ? stateMessage(settled.result, isMock ? 'mock' : 'http') : 'Sin consulta previa. Obtén una respuesta manual desde Dashboard para explorar sus proyectos.'}{changed && <p>Filtros pendientes de aplicar. Se muestran {settled?.query.sector}, año de inicio {settled?.query.anio}.</p>}{!isMock && <p>Respuesta en memoria de esta sesión; sin garantía de frescura del proveedor. Hora de recepción del cliente: {settled?.receivedAt ? new Date(settled.receivedAt).toLocaleString('es-PE') : 'Sin respuesta'}.</p>}{busy && <button type="button" onClick={() => requests.current.cancel()}>Cancelar</button>}</div>

    <div className="explorer-layout" aria-busy={busy}>
      <section className="surface explorer-spatial" aria-labelledby="spatial-title">
        <header className="explorer-panel-header"><div><span className="eyebrow">DISTRIBUCIÓN DE LA SELECCIÓN</span><h2 id="spatial-title">Esquema de coordenadas</h2><p>Visualización ilustrativa offline. Sin mapa base ni CRS certificado.</p></div><span className="explorer-offline">SIN SERVICIOS EXTERNOS</span></header>
        <div className="explorer-map">
          {markers === undefined ? <p className="explorer-map-message">{busy ? 'Esperando datos artificiales…' : 'Coordenadas desconocidas: la consulta no ofrece una lista utilizable.'}</p> : markers.length === 0 ? <p className="explorer-map-message">{entries?.length === 0 ? 'No hay registros en esta selección.' : spatial?.coordinateCount ? 'Esta página no tiene puntos representables. Las demás páginas conservan sus registros y coordenadas.' : 'Sin coordenadas utilizables. Los proyectos siguen disponibles en la lista.'}</p> : <>
            <svg className="explorer-plane" viewBox={viewBox} aria-label="Plano ilustrativo de longitud y latitud; marcadores seleccionables">
              <title>{isMock ? 'Coordenadas artificiales, sin cartografía oficial' : 'Coordenadas recibidas, sin cartografía oficial ni CRS certificado'}</title>
              {markers.map(marker => <g key={marker.key} role="button" tabIndex={0} aria-label={marker.entries.length > 1 ? `Ver ${marker.entries.length} registros con estas coordenadas exactas en la lista paginada` : `Seleccionar ${marker.entries[0]!.row.titulo ?? marker.entries[0]!.row.proyecto ?? 'registro'}; ID ${text(marker.entries[0]!.row.id)}`} aria-pressed={selection === undefined ? coordinateFilter === marker.key : index?.positions.get(selection)?.key === marker.key} onClick={() => choosePoint(marker)} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); choosePoint(marker); } }}>
                <circle cx={marker.pos.x} cy={marker.pos.y} r={marker.entries.length > 1 || (selection !== undefined && index?.positions.get(selection)?.key === marker.key) ? .38 : .25} className={marker.entries.length > 1 ? 'explorer-marker explorer-marker--shared' : marker.entries[0]!.row.estado === 'Aprobado' ? 'explorer-marker explorer-marker--approved' : 'explorer-marker'} />
                {marker.entries.length > 1 && <text x={marker.pos.x} y={marker.pos.y} className="explorer-marker-count" textAnchor="middle" dominantBaseline="central" aria-hidden="true">{marker.entries.length}</text>}
                <title>{marker.entries.length > 1 ? `${marker.entries.length} filas coincidentes; no empresas o proyectos únicos certificados` : `${text(marker.entries[0]!.row.titulo)} · ${text(marker.entries[0]!.row.estado)}`}</title>
              </g>)}
            </svg>
            <div className="explorer-map-controls" aria-label="Escala del esquema"><button type="button" aria-label="Acercar esquema" disabled={zoom >= 2} onClick={() => setZoom(value => Math.min(2, value + .25))}>+</button><button type="button" aria-label="Alejar esquema" disabled={zoom <= 1} onClick={() => setZoom(value => Math.max(1, value - .25))}>−</button><button type="button" onClick={() => setZoom(1)}>Ajustar</button></div>
            <span className="explorer-map-label">Eje horizontal: longitud · vertical: latitud</span>
          </>}
        </div>
        <div className="explorer-spatial-foot" aria-live="polite"><span>Registros en la respuesta: {all === undefined ? 'No disponible' : all.length}</span><span>Registros filtrados: {entries === undefined ? 'No disponible' : entries.length}</span><span>Con coordenadas en rango: {spatial?.coordinateCount ?? 'No disponible'}</span><span>Puntos representados: {markers?.length ?? 'No disponible'}</span><span>El plano muestra la página de la lista y, si está fuera de ella, el punto seleccionado. Máximo 21 puntos. Coordenadas exactamente coincidentes comparten un punto con contador; no se deduplican filas. Los rangos no certifican referencia espacial ni ubicación real.</span></div>
        <section className="explorer-list" aria-labelledby="project-list-title"><div className="explorer-list-header"><h2 id="project-list-title">Lista alternativa de proyectos</h2><label>Buscar en las filas cargadas<input type="search" value={search} disabled={all === undefined} placeholder="Titular, RUC o proyecto" onChange={e => { setSearch(e.target.value); resetSelection(); setZoom(1); }} /></label>
          {coordinateFilter !== undefined && <div className="explorer-coincidences"><p>Mostrando {listed?.length ?? 0} filas filtradas con coordenadas exactamente coincidentes. Elige una fila para ver su ficha.</p><button type="button" onClick={() => { setCoordinateFilter(undefined); setPage(1); }}>Volver a todos los resultados filtrados</button></div>}
          {chosen && !paginated.rows?.some(entry => entry.key === chosen.key) && <p>La ficha seleccionada permanece abierta fuera de esta página.</p>}
        </div>
          {paginated.rows === undefined ? <p className="explorer-placeholder">Lista no disponible; no equivale a cero proyectos.</p> : paginated.rows.length === 0 ? <p className="explorer-placeholder">Selección conocida sin coincidencias.</p> : <ul>{paginated.rows.map(entry => <li key={entry.key}><button type="button" aria-pressed={selection === entry.key} onClick={() => choose(entry.key)}><span><strong>{text(entry.row.titulo ?? entry.row.proyecto)}</strong><small>{text(entry.row.titular)} · {text(entry.row.actividad)}</small></span><span className="explorer-list-state">{text(entry.row.estado)}<small>{coordinateState(entry.row) === 'UTILIZABLES' ? 'Coordenadas en rango' : coordinateState(entry.row) === 'FUERA_DE_RANGO' ? 'Fuera de rango' : 'Coordenadas ausentes/incompletas'}</small></span></button></li>)}</ul>}
          {paginated.total !== undefined && <div className="explorer-pagination"><p role="status">Mostrando {paginated.start}–{paginated.end} de {paginated.total} filas{coordinateFilter !== undefined ? ' de esta coincidencia exacta' : ' filtradas'} · máximo 20 por página</p><nav aria-label="Paginación local de proyectos"><button type="button" disabled={paginated.page <= 1} onClick={() => setPage(paginated.page - 1)}>Anterior</button><label>Página<input type="number" min="1" max={Math.max(1, paginated.pages)} step="1" disabled={!paginated.pages} value={paginated.page} onChange={event => setPage(Number.isSafeInteger(event.target.valueAsNumber) ? event.target.valueAsNumber : 1)} /></label><span>de {paginated.pages}</span><button type="button" disabled={!paginated.pages || paginated.page >= paginated.pages} onClick={() => setPage(paginated.page + 1)}>Siguiente</button></nav></div>}
        </section>
      </section>

      <aside className="surface explorer-detail" aria-labelledby="detail-title"><header className="explorer-panel-header"><div><span className="eyebrow">DATOS DISPONIBLES</span><h2 id="detail-title">Ficha del proyecto seleccionado</h2></div></header>
        {!row ? <p className="explorer-placeholder">Selecciona un marcador o una fila de la lista. La lista funciona sin mapa.</p> : <div className="explorer-detail-content"><div className="explorer-owner"><span className="eyebrow">TITULAR</span><h3>{text(row.titular)}</h3><p>RUC: {text(row.ruc)}</p></div><dl>
          <div><dt>ID del registro · estabilidad no certificada</dt><dd>{text(row.id)}</dd></div>
          <div><dt>Proyecto</dt><dd>{text(row.titulo ?? row.proyecto)}</dd></div>
          <div><dt>Descripción</dt><dd>{text(row.descripcion)}</dd></div>
          <div><dt>Actividad original</dt><dd>{text(row.actividad)}</dd></div>
          <div><dt>Tipo / unidad del proyecto</dt><dd>{text(row.tipo)} / {text(row.unidadProyecto)}</dd></div>
          <div><dt>Fecha de inicio original / año de inicio</dt><dd>{text(row.fechaInicio)} / {text(row.anio)}</dd></div>
          <div><dt>Coordenadas originales</dt><dd>Latitud: {text(row.latitud)}<br />Longitud: {text(row.longitud)}<small>{coordinateState(row)} · referencia espacial no verificada</small></dd></div>
          <div className="explorer-state"><dt>Estado original</dt><dd>{text(row.estado)}</dd></div>
          <div><dt>Resolución original</dt><dd>{text(row.resolucion)}</dd></div>
          <div><dt>{isMock ? 'Origen de la fila · escenario simulado' : 'Origen de la fila informado'}</dt><dd>{text(row.origen)}</dd></div>
        </dl></div>}
        <section className="explorer-quality"><h3>Procedencia y cobertura</h3><dl><div><dt>Modo</dt><dd>{isMock ? 'MOCK · ARTIFICIAL' : 'HTTP · RESPUESTA COMPARTIDA'}</dd></div><div><dt>Consulta</dt><dd>{text(settled?.result.data?.consultaSenace?.estado)}</dd></div><div><dt>{isMock ? 'Origen · simulado' : 'Origen informado'}</dt><dd>{text(settled?.result.data?.consultaSenace?.origen)}</dd></div><div><dt>Cobertura</dt><dd>{text(settled?.result.data?.consultaSenace?.cobertura)}</dd></div><div><dt>{isMock ? 'Cuerpo completo simulado' : 'Cuerpo completo informado'}</dt><dd>{settled?.result.data?.consultaSenace?.cuerpoCompleto === undefined ? 'No disponible' : settled.result.data.consultaSenace.cuerpoCompleto ? 'Sí; no certifica cobertura' : 'No'}</dd></div><div><dt>Motivo seguro</dt><dd>{text(settled?.result.reason)}</dd></div></dl><p>Sin región, expediente, elevación ni impacto económico regional acreditados. Decimales sujetos a precisión de JavaScript.</p></section>
      </aside>
    </div>
  </div>;
}
