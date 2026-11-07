import { useEffect, useRef, useState } from 'react';
import { PreparationView } from '../components/PreparationView';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ExploradorPage } from '../features/explorador/ExploradorPage';
import { ConsultorasPage } from '../features/consultoras/ConsultorasPage';
import { MonitorPage } from '../features/monitor/MonitorPage';
import { AppShell } from './AppShell';
import { navigation, resolveNavigation } from './navigation';
import { publicConfig } from './config';
import { featureAccess } from '../api/source';

export function App() {
  const [hash, setHash] = useState(window.location.hash);
  const [menuOpen, setMenuOpen] = useState(false);
  const contentRef = useRef<HTMLElement>(null);
  const hasNavigated = useRef(false);
  const page = resolveNavigation(hash);
  const access = page ? featureAccess(publicConfig, page.id) : undefined;

  useEffect(() => {
    if (!window.location.hash || window.location.hash === '#') {
      window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#/dashboard`);
      setHash('#/dashboard');
    }
    const onHashChange = () => {
      hasNavigated.current = true;
      setHash(window.location.hash);
      setMenuOpen(false);
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    document.title = `EcoInvest · ${page?.label ?? 'Vista no encontrada'}`;
    if (hasNavigated.current) contentRef.current?.focus();
  }, [page]);

  return (
    <AppShell source={publicConfig} page={page} menuOpen={menuOpen} onToggleMenu={() => setMenuOpen((open) => !open)}>
      <main className="workspace" id="contenido" ref={contentRef} tabIndex={-1}>
        {page ? (!access?.allowed ? <section className="surface not-found" aria-labelledby="source-status-title"><span className="eyebrow">CONFIGURACIÓN DE FUENTE</span><h1 id="source-status-title">{publicConfig.valid ? 'Integración no habilitada' : 'Error de configuración'}</h1><p role="status">{access?.message}</p>{publicConfig.valid && <a className="text-link" href="#/monitor">Ir al Monitor de Salud SOA</a>}</section> : page.id === 'dashboard' ? <DashboardPage source={publicConfig} /> : page.id === 'explorador' ? <ExploradorPage source={publicConfig} /> : page.id === 'consultoras' ? <ConsultorasPage source={publicConfig} /> : page.id === 'monitor' ? <MonitorPage source={publicConfig} /> : <PreparationView page={page} />) : (
          <section className="surface not-found" aria-labelledby="not-found-title">
            <span className="eyebrow">NAVEGACIÓN</span>
            <h1 id="not-found-title">Vista no encontrada</h1>
            <p>Este destino no forma parte de las cuatro vistas de EcoInvest.</p>
            <a className="text-link" href={`#/${navigation[0]!.id}`}>Ir al Dashboard Analítico</a>
          </section>
        )}
      </main>
    </AppShell>
  );
}
