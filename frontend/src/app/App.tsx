import { useEffect, useRef, useState } from 'react';
import { PreparationView } from '../components/PreparationView';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ExploradorPage } from '../features/explorador/ExploradorPage';
import { ConsultorasPage } from '../features/consultoras/ConsultorasPage';
import { MonitorPage } from '../features/monitor/MonitorPage';
import { AppShell } from './AppShell';
import { navigation, resolveNavigation } from './navigation';

export function App() {
  const [hash, setHash] = useState(window.location.hash);
  const [menuOpen, setMenuOpen] = useState(false);
  const contentRef = useRef<HTMLElement>(null);
  const hasNavigated = useRef(false);
  const page = resolveNavigation(hash);

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
    <AppShell page={page} menuOpen={menuOpen} onToggleMenu={() => setMenuOpen((open) => !open)}>
      <main className="workspace" id="contenido" ref={contentRef} tabIndex={-1}>
        {page ? (page.id === 'dashboard' ? <DashboardPage /> : page.id === 'explorador' ? <ExploradorPage /> : page.id === 'consultoras' ? <ConsultorasPage /> : page.id === 'monitor' ? <MonitorPage /> : <PreparationView page={page} />) : (
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
