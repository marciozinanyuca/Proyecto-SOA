import { useEffect, useRef, useState } from 'react';
import { createLatestRequest } from '../../api/requests';
import { NavigationIcon } from '../../components/NavigationIcon';
import type { SourceConfig } from '../../api/source';
import { circuitLabel, countDisplay, loadMonitor, metricsFor, monitorMessage, monitorScenarios, rateDisplay } from './monitorModel';
import type { MonitorScenario, MonitorSnapshot } from './monitorModel';
import './MonitorPage.css';

const services = [{ key: 'senace', name: 'SENACE', title: 'Servicio de Información Ambiental', description: 'Circuito de la integración SENACE' }, { key: 'bcrp', name: 'BCRP', title: 'Servicio de Indicadores Económicos', description: 'Circuito de la integración BCRP' }] as const;

export function MonitorPage({ source }: { readonly source: SourceConfig }) {
  const isMock = source.valid && source.mode === 'mock';
  const [scenario, setScenario] = useState<MonitorScenario>('monitor');
  const [snapshot, setSnapshot] = useState<MonitorSnapshot>();
  const [phase, setPhase] = useState<'idle' | 'loading' | 'settled' | 'cancelled' | 'error'>(isMock ? 'loading' : 'idle');
  const requests = useRef(createLatestRequest<MonitorSnapshot>()); const mounted = useRef(false);
  const settled = phase === 'settled' ? snapshot : undefined;
  const busy = phase === 'loading';
  async function refresh(requested: MonitorScenario) {
    setPhase('loading'); setSnapshot(undefined);
    const result = await requests.current.run(signal => loadMonitor(requested, signal, source));
    if (!mounted.current || result.state === 'stale') return;
    if (result.state !== 'current') { setPhase(result.state === 'cancelled' ? 'cancelled' : 'error'); return; }
    setSnapshot(result.value); setPhase('settled');
  }
  useEffect(() => {
    mounted.current = true;
    // HTTP es opt-in manual: StrictMode y el montaje no lanzan ningun GET.
    if (isMock) void refresh('monitor');
    return () => { mounted.current = false; requests.current.cancel(); };
  }, []);
  const timestamp = settled ? new Date(settled.completedAt).toLocaleString('es-PE') : 'Sin consulta completada';

  return <div className="monitor-page">
    <h1 className="monitor-sr">Monitor de Salud SOA</h1>
    <div className="monitor-demo"><strong>{isMock ? 'MODO DEMOSTRACIÓN — DATOS ARTIFICIALES' : 'MODO HTTP — BACKEND CONFIGURADO'}</strong><span>{isMock ? 'Sin backend ni consultas a proveedores' : 'Solo métricas internas; sin health checks externos'}</span></div>
    <section className="surface monitor-toolbar" aria-label="Consulta manual del snapshot">
      <div className="monitor-toolbar-main"><div><span className="eyebrow">OBSERVACIÓN DE CIRCUITOS Y LLAMADAS LÓGICAS</span><p className="monitor-client-time">Última consulta completada · hora local del cliente: <strong>{timestamp}</strong></p><p>Sin polling automático. La hora no procede de SENACE o BCRP.</p></div><div className="monitor-actions"><button type="button" className="monitor-primary" disabled={!isMock && busy} onClick={() => { void refresh(scenario); }}>{busy ? 'Consultando…' : isMock ? 'Actualizar snapshot mock' : 'Consultar Monitor por HTTP'}</button>{busy && <button type="button" onClick={() => requests.current.cancel()}>Cancelar</button>}</div></div>
      {isMock && <details className="monitor-tools"><summary>Escenario de demostración</summary><label>Respuesta artificial<select value={scenario} onChange={event => setScenario(event.target.value as MonitorScenario)}>{monitorScenarios.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><p>La selección requiere pulsar Actualizar. Cambiar un fixture no simula una avería ni modifica circuitos del backend.</p></details>}
    </section>
    <div role="status" aria-live="polite" className={`monitor-feedback monitor-feedback--${settled?.result.state ?? phase}`}>{busy ? isMock ? 'Cargando snapshot artificial…' : 'Consultando el backend configurado…' : phase === 'cancelled' ? 'Consulta cancelada. No se publican resultados de la operación cancelada.' : phase === 'error' ? 'No se pudo completar la consulta. No se activa una fuente alternativa.' : settled ? monitorMessage(settled) : 'Sin snapshot. Pulsa Consultar Monitor por HTTP para realizar una única consulta manual.'}{isMock && settled && scenario !== settled.scenario && <p>Escenario pendiente de aplicar. Se muestra: {monitorScenarios.find(item => item.value === settled.scenario)?.label}.</p>}</div>
    <div className="monitor-content" aria-busy={busy}>
      <section className="monitor-services" aria-label="Circuitos SENACE y BCRP">
        {services.map(service => {
          const metrics = metricsFor(settled, service.key); const state = circuitLabel(metrics?.estado);
          return <article className="surface monitor-service" key={service.key} aria-labelledby={`monitor-${service.key}-title`}>
            <header className="monitor-service-header"><span className="monitor-service-icon"><NavigationIcon kind={service.key === 'senace' ? 'consultoras' : 'monitor'} /></span><div><h2 id={`monitor-${service.key}-title`}>{service.title} ({service.name})</h2><p>{service.description}</p></div><span className="monitor-source-badge">{isMock ? 'MOCK' : 'HTTP'}</span></header>
            <div className={`monitor-circuit monitor-circuit--${state.tone}`}><span>ESTADO DEL CIRCUIT BREAKER</span><strong>{busy ? 'Cargando…' : metrics?.estado ?? 'No disponible'}</strong><p>{state.label}</p></div>
            <p className="monitor-circuit-help">{state.explanation}</p>
            <dl className="monitor-metrics"><div><dt>Llamadas fallidas</dt><dd>{countDisplay(metrics?.llamadasFallidas)}</dd></div><div><dt>Llamadas exitosas</dt><dd>{countDisplay(metrics?.llamadasExitosas)}</dd></div><div><dt>Tasa de fallos</dt><dd>{rateDisplay(metrics?.tasaFallosPorcentaje)}</dd></div></dl>
            <div className="monitor-rate-note">Una tasa −1 se muestra como No calculada. No se transforma en 0 % ni se reconstruye a partir de otros contadores.</div>
            <section className="monitor-service-scope" aria-label={`Alcance de ${service.name}`}><dl><div><dt>Servicio informado</dt><dd>{metrics?.servicio ?? 'No disponible'}</dd></div><div><dt>Alcance de las métricas</dt><dd>{metrics?.alcanceMetricas ?? 'No disponible'}</dd></div><div><dt>Disponibilidad externa</dt><dd>{metrics?.disponibilidadExterna ?? 'No disponible'}</dd></div><div><dt>Respaldo local</dt><dd>{metrics?.respaldoLocal ?? 'No disponible'}</dd></div></dl></section>
            <div className="monitor-backup-note">El backend no implementa respaldo SQLite o caché de recuperación. No se infiere disponibilidad externa del estado del circuito.</div>
          </article>;
        })}
      </section>

      <section className="surface monitor-snapshot" aria-labelledby="monitor-snapshot-title"><header className="monitor-panel-heading"><div><span className="eyebrow">CONSULTA ACTUAL · SIN HISTORIAL PERSISTENTE</span><h2 id="monitor-snapshot-title">Snapshot de circuitos y alcance</h2><p>Resumen de la última respuesta completada de esta vista. No es un historial de solicitudes o consola de logs.</p></div><span className="monitor-http">{isMock ? 'HTTP simulado' : 'HTTP backend'}: {settled?.http.status ?? 'No disponible'}</span></header>
        <div className="monitor-table-scroll" role="region" tabIndex={0} aria-label="Snapshot actual con desplazamiento horizontal local"><table className="monitor-table"><caption className="monitor-sr">{isMock ? 'Métricas artificiales de circuitos' : 'Métricas internas obtenidas del backend'}; no confirman disponibilidad del proveedor</caption><thead><tr>{['Servicio', 'Circuito', 'Fallidas', 'Exitosas', 'Tasa de fallos', 'Alcance', 'Disponibilidad externa', 'Respaldo local'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead><tbody>{services.map(service => { const metrics = metricsFor(settled, service.key); return <tr key={service.key}><th scope="row">{service.name}</th><td>{metrics?.estado ?? 'No disponible'}</td><td>{countDisplay(metrics?.llamadasFallidas)}</td><td>{countDisplay(metrics?.llamadasExitosas)}</td><td>{rateDisplay(metrics?.tasaFallosPorcentaje)}</td><td>{metrics?.alcanceMetricas ?? 'No disponible'}</td><td>{metrics?.disponibilidadExterna ?? 'No disponible'}</td><td>{metrics?.respaldoLocal ?? 'No disponible'}</td></tr>; })}</tbody></table></div>
        <dl className="monitor-query-meta"><div><dt>Modo configurado</dt><dd>{isMock ? 'MOCK · datos artificiales' : 'HTTP · backend configurado'}</dd></div><div><dt>Fuente contractual</dt><dd>GET /api/estado-servicios</dd></div><div><dt>Estado del adaptador</dt><dd>{settled?.result.state ?? (busy ? 'loading' : phase)}</dd></div><div><dt>Motivo seguro</dt><dd>{settled?.http.issue ?? settled?.result.reason ?? 'Sin motivo adicional disponible'}</dd></div></dl>
      </section>
      <section className="surface monitor-limitations" aria-labelledby="monitor-limitations-title"><h2 id="monitor-limitations-title">Cómo interpretar estas métricas</h2><div className="monitor-limitations-grid"><article><h3>Llamadas lógicas</h3><p>Los contadores reflejan la instrumentación interna de circuit breakers; pueden incluir fallos de configuración. No equivalen necesariamente al número de solicitudes HTTP externas.</p></article><article><h3>Disponibilidad no verificada</h3><p>CLOSED no significa proveedor operativo. OPEN tampoco acredita por sí solo una caída externa. Este endpoint no realiza health checks a SENACE o BCRP.</p></article><article><h3>Sin telemetría adicional</h3><p>Latencia, solicitudes bloqueadas, historial y logs no están expuestos por el contrato. No se muestran cifras de sustitución ni botones para simular fallos.</p></article><article><h3>Sin respaldo local implementado</h3><p>NO_IMPLEMENTADO describe el respaldo local. NO_VERIFICADA describe la disponibilidad externa. No son estados intercambiables ni promesas de recuperación.</p></article></div></section>
    </div>
  </div>;
}
