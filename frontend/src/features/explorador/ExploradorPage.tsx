import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { createLatestRequest } from '../../api/requests';
import { sectors, years, scenarios, stateMessage } from '../dashboard/dashboardModel';
import type { DashboardQuery, DashboardSnapshot } from '../dashboard/dashboardModel';
import { coordinateState, initialSelection, loadExplorer, point, previousSelection, projectEntries, selectEntry } from './exploradorModel';
import type { ExplorerScenario } from './exploradorModel';
import './ExploradorPage.css';

export function ExploradorPage() {
  const [draft, setDraft] = useState<DashboardQuery>(initialSelection);
  const [scenario, setScenario] = useState<ExplorerScenario>(() => previousSelection()?.scenario ?? 'coordinates');
  const [snapshot, setSnapshot] = useState<DashboardSnapshot>();
  const [appliedScenario, setAppliedScenario] = useState<ExplorerScenario>();
  const [phase, setPhase] = useState<'loading' | 'settled' | 'cancelled' | 'error'>('loading');
  const [state, setState] = useState(''); const [search, setSearch] = useState('');
  const [selection, setSelection] = useState<string>(); const [zoom, setZoom] = useState(1);
  const requests = useRef(createLatestRequest<DashboardSnapshot>()); const mounted = useRef(false);
  async function load(query: DashboardQuery, requested: ExplorerScenario) {
    setPhase('loading'); setSnapshot(undefined); setSelection(undefined); setState(''); setSearch(''); setZoom(1);
    const response = await requests.current.run(signal => loadExplorer(query, requested, signal));
    if (!mounted.current || response.state === 'stale') return;
    if (response.state !== 'current') { setPhase(response.state === 'cancelled' ? 'cancelled' : 'error'); return; }
    setSnapshot(response.value); setAppliedScenario(requested); setPhase('settled');
  }
  useEffect(() => {
    mounted.current = true;
    const previous = previousSelection();
    void load(previous?.query ?? initialSelection(), previous?.scenario ?? 'coordinates');
    return () => { mounted.current = false; requests.current.cancel(); };
  }, []);
  const settled = phase === 'settled' ? snapshot : undefined;
  const entries = projectEntries(settled, state, search);
  const all = projectEntries(settled);
  const states = [...new Set(all?.map(entry => entry.row.estado ?? 'No disponible') ?? [])];
  const markers = entries?.flatMap(entry => { const pos = point(entry.row); return pos ? [{ ...entry, pos }] : []; });
  const chosen = selectEntry(entries, selection); const row = chosen?.row;
  const busy = phase === 'loading';
  const changed = settled && (draft.sector !== settled.query.sector || draft.anio !== settled.query.anio || scenario !== appliedScenario);
  // Extensión ilustrativa a partir de puntos conocidos; sin contorno o tiles oficiales.
  const xs = markers?.map(marker => marker.pos.x) ?? []; const ys = markers?.map(marker => marker.pos.y) ?? [];
  const minX = xs.length ? Math.min(...xs) - 3 : 0; const minY = ys.length ? Math.min(...ys) - 3 : 0;
  const width = xs.length ? Math.max(...xs) - minX + 3 : 100;
  const height = ys.length ? Math.max(...ys) - minY + 3 : 100;
  const boxWidth = width / zoom, boxHeight = height / zoom;
  const viewBox = `${minX + (width - boxWidth) / 2} ${minY + (height - boxHeight) / 2} ${boxWidth} ${boxHeight}`;
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void load({ ...draft }, scenario); }
  function choose(key: string) { setSelection(key); }
  function resetSelection() { setSelection(undefined); }
  const text = (value: string | number | undefined) => value === undefined || value === '' ? 'No disponible' : String(value);

  return <div className="explorer-page">
    <h1 className="explorer-sr">Explorador Geográfico</h1>
    <div className="explorer-demo"><strong>MODO DEMOSTRACIÓN — DATOS ARTIFICIALES</strong><span>Offline · sin backend ni cartografía externa</span></div>
    <section className="surface explorer-filters" aria-label="Filtros del Explorador">
      <form onSubmit={submit}><div className="explorer-filter-grid">
        <label>ACTIVIDAD / SECTOR<select value={draft.sector} onChange={e => setDraft({ ...draft, sector: e.target.value as DashboardQuery['sector'] })}>{sectors.map(value => <option key={value}>{value}</option>)}</select></label>
        <label>AÑO DE INICIO<select value={draft.anio} onChange={e => setDraft({ ...draft, anio: Number(e.target.value) as DashboardQuery['anio'] })}>{years.map(value => <option key={value}>{value}</option>)}</select></label>
        <label>ESTADO · FILTRO LOCAL<select disabled={all === undefined} value={state} onChange={e => { setState(e.target.value); resetSelection(); setZoom(1); }}><option value="">Todos los estados</option>{states.map(value => <option key={value}>{value}</option>)}</select></label>
        <button className="explorer-primary" type="submit">Consultar demostración</button>
      </div><p>FECHA_INICIO, no fecha de aprobación. Sectores y años limitados a los mocks aprobados.</p></form>
      <details className="explorer-tools"><summary>Escenario de demostración</summary><label>Respuesta artificial<select value={scenario} onChange={e => setScenario(e.target.value as ExplorerScenario)}><option value="coordinates">Coordenadas artificiales: válidas, ausentes y fuera de rango</option>{scenarios.map(item => <option value={item.value} key={item.value}>{item.label}</option>)}</select></label><p>El escenario de coordenadas añade fixtures separados. La selección heredada del Dashboard no se completa con ubicaciones inventadas.</p></details>
    </section>
    <div className={`explorer-feedback explorer-feedback--${settled?.result.state ?? phase}`} role="status" aria-live="polite">{busy ? 'Cargando selección artificial…' : phase === 'cancelled' ? 'Consulta cancelada.' : phase === 'error' ? 'No se pudo completar la demostración.' : settled ? stateMessage(settled.result) : 'Sin resultado.'}{changed && <p>Filtros pendientes de aplicar. Se muestran {settled.query.sector}, año de inicio {settled.query.anio}.</p>}{busy && <button type="button" onClick={() => requests.current.cancel()}>Cancelar</button>}</div>

    <div className="explorer-layout" aria-busy={busy}>
      <section className="surface explorer-spatial" aria-labelledby="spatial-title">
        <header className="explorer-panel-header"><div><span className="eyebrow">DISTRIBUCIÓN DE LA SELECCIÓN</span><h2 id="spatial-title">Esquema de coordenadas</h2><p>Visualización ilustrativa offline. Sin mapa base ni CRS certificado.</p></div><span className="explorer-offline">SIN SERVICIOS EXTERNOS</span></header>
        <div className="explorer-map">
          {markers === undefined ? <p className="explorer-map-message">{busy ? 'Esperando datos artificiales…' : 'Coordenadas desconocidas: la consulta no ofrece una lista utilizable.'}</p> : markers.length === 0 ? <p className="explorer-map-message">{entries?.length === 0 ? 'No hay registros en esta selección.' : 'No hay coordenadas utilizables. Selecciona proyectos desde la lista o aplica el escenario de coordenadas artificiales.'}</p> : <>
            <svg className="explorer-plane" viewBox={viewBox} aria-label="Plano ilustrativo de longitud y latitud; marcadores seleccionables">
              <title>Coordenadas artificiales, sin cartografía oficial</title>
              {markers.map(marker => <g key={marker.key} role="button" tabIndex={0} aria-label={`Seleccionar ${marker.row.titulo ?? marker.row.proyecto ?? 'registro'}; ID ${text(marker.row.id)}`} aria-pressed={selection === marker.key} onClick={() => choose(marker.key)} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); choose(marker.key); } }}>
                <circle cx={marker.pos.x} cy={marker.pos.y} r={selection === marker.key ? .38 : .25} className={marker.row.estado === 'Aprobado' ? 'explorer-marker explorer-marker--approved' : 'explorer-marker'} /><title>{text(marker.row.titulo)} · {text(marker.row.estado)}</title>
              </g>)}
            </svg>
            <div className="explorer-map-controls" aria-label="Escala del esquema"><button type="button" aria-label="Acercar esquema" disabled={zoom >= 2} onClick={() => setZoom(value => Math.min(2, value + .25))}>+</button><button type="button" aria-label="Alejar esquema" disabled={zoom <= 1} onClick={() => setZoom(value => Math.max(1, value - .25))}>−</button><button type="button" onClick={() => setZoom(1)}>Ajustar</button></div>
            <span className="explorer-map-label">Eje horizontal: longitud · vertical: latitud</span>
          </>}
        </div>
        <div className="explorer-spatial-foot"><span>Filas visibles: {entries === undefined ? 'No disponible' : entries.length}</span><span>Con coordenadas en rango: {markers === undefined ? 'No disponible' : markers.length}</span><span>Los rangos no certifican referencia espacial ni ubicación real. Puntos coincidentes pueden superponerse.</span></div>
        <section className="explorer-list" aria-labelledby="project-list-title"><div className="explorer-list-header"><h2 id="project-list-title">Lista alternativa de proyectos</h2><label>Buscar en las filas cargadas<input type="search" value={search} disabled={all === undefined} placeholder="Titular, RUC o proyecto" onChange={e => { setSearch(e.target.value); resetSelection(); setZoom(1); }} /></label></div>
          {entries === undefined ? <p className="explorer-placeholder">Lista no disponible; no equivale a cero proyectos.</p> : entries.length === 0 ? <p className="explorer-placeholder">Selección conocida sin coincidencias.</p> : <ul>{entries.map(entry => <li key={entry.key}><button type="button" aria-pressed={selection === entry.key} onClick={() => choose(entry.key)}><span><strong>{text(entry.row.titulo ?? entry.row.proyecto)}</strong><small>{text(entry.row.titular)} · {text(entry.row.actividad)}</small></span><span className="explorer-list-state">{text(entry.row.estado)}<small>{coordinateState(entry.row) === 'UTILIZABLES' ? 'Coordenadas en rango' : coordinateState(entry.row) === 'FUERA_DE_RANGO' ? 'Fuera de rango' : 'Coordenadas ausentes/incompletas'}</small></span></button></li>)}</ul>}
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
          <div><dt>Origen de la fila · escenario simulado</dt><dd>{text(row.origen)}</dd></div>
        </dl></div>}
        <section className="explorer-quality"><h3>Procedencia y cobertura</h3><dl><div><dt>Modo</dt><dd>MOCK · ARTIFICIAL</dd></div><div><dt>Consulta</dt><dd>{text(settled?.result.data?.consultaSenace?.estado)}</dd></div><div><dt>Origen · simulado</dt><dd>{text(settled?.result.data?.consultaSenace?.origen)}</dd></div><div><dt>Cobertura</dt><dd>{text(settled?.result.data?.consultaSenace?.cobertura)}</dd></div><div><dt>Cuerpo completo simulado</dt><dd>{settled?.result.data?.consultaSenace?.cuerpoCompleto === undefined ? 'No disponible' : settled.result.data.consultaSenace.cuerpoCompleto ? 'Sí; no certifica cobertura' : 'No'}</dd></div><div><dt>Motivo seguro</dt><dd>{text(settled?.result.reason)}</dd></div></dl><p>Sin región, expediente, elevación ni impacto económico regional acreditados. Decimales sujetos a precisión de JavaScript.</p></section>
      </aside>
    </div>
  </div>;
}
