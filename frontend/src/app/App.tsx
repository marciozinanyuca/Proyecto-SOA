
import { useCallback, useEffect, useRef, useState } from 'react';
import { PreparationView } from '../components/PreparationView';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { ExploradorPage } from '../features/explorador/ExploradorPage';
import { ConsultorasPage } from '../features/consultoras/ConsultorasPage';
import { MonitorPage } from '../features/monitor/MonitorPage';
import { LoginPage } from '../features/usuarios/LoginPage';
import { UsuariosPage } from '../features/usuarios/UsuariosPage';
import { obtenerSesion, esAdministrador } from '../api/auth';
import type { UsuarioSesion } from '../api/auth';
import { AppShell } from './AppShell';
import { navigation, resolveNavigation } from './navigation';
import { publicConfig } from './config';
import { featureAccess } from '../api/source';

export function App() {
  const [hash, setHash] = useState(window.location.hash);
  const [menuOpen, setMenuOpen] = useState(false);
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(null);
  const [cargandoSesion, setCargandoSesion] = useState(true);
  const [cerrandoSesion, setCerrandoSesion] = useState(false);
  const [errorSesion, setErrorSesion] = useState('');

  const contentRef = useRef<HTMLElement>(null);
  const hasNavigated = useRef(false);
  const page = resolveNavigation(hash);

  const access = page
    ? page.id === 'usuarios'
      ? { allowed: true }
      : featureAccess(publicConfig, page.id)
    : undefined;

  const verificarSesion = useCallback(async () => {
    setCargandoSesion(true);
    setErrorSesion('');

    try {
      const sesion = await obtenerSesion();
      setUsuario(sesion);
    } catch {
      setUsuario(null);
      setErrorSesion('No se pudo comprobar la sesión.');
    } finally {
      setCargandoSesion(false);
    }
  }, []);

  useEffect(() => {
    void verificarSesion();
  }, [verificarSesion]);

  useEffect(() => {
    if (!window.location.hash || window.location.hash === '#') {
      window.history.replaceState(
        null,
        '',
        `${window.location.pathname}${window.location.search}#/dashboard`
      );
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

  async function cerrarSesion() {
    setCerrandoSesion(true);

    try {
      const csrfResponse = await fetch('/api/auth/csrf', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });

      if (!csrfResponse.ok) {
        throw new Error('No se pudo obtener el token de seguridad');
      }

      const csrf: {
        parameterName: string;
        token: string;
      } = await csrfResponse.json();

      const datos = new URLSearchParams();
      datos.set(csrf.parameterName, csrf.token);

      const respuesta = await fetch('/logout', {
        method: 'POST',
        credentials: 'same-origin',
        redirect: 'manual',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: datos,
      });

      if (
        respuesta.type !== 'opaqueredirect' &&
        !respuesta.ok &&
        respuesta.status !== 302
      ) {
        throw new Error('El servidor rechazó el cierre de sesión');
      }

      const sesionActual = await obtenerSesion();

      if (sesionActual !== null) {
        throw new Error('La sesión continúa activa');
      }

      setUsuario(null);
      setErrorSesion('');
      window.location.hash = '#/usuarios';
      setHash('#/usuarios');
    } catch (error) {
      window.alert(
        error instanceof Error
          ? error.message
          : 'No se pudo cerrar sesión'
      );
    } finally {
      setCerrandoSesion(false);
    }
  }

  function renderizarUsuarios() {
    if (cargandoSesion) {
      return <section className="surface">
        <p>Verificando sesión...</p>
      </section>;
    }

    if (errorSesion) {
      return <section className="surface">
        <h1>Error de autenticación</h1>
        <p role="alert">{errorSesion}</p>
        <button type="button" onClick={() => void verificarSesion()}>
          Reintentar
        </button>
      </section>;
    }

    if (!usuario) {
      return <LoginPage onLogin={verificarSesion} />;
    }

    if (!esAdministrador(usuario)) {
      return <section className="surface">
        <h1>Acceso restringido</h1>
        <p>Hola, {usuario.username}.</p>
        <p>Tu cuenta no tiene permisos para administrar usuarios.</p>
        <a href="#/dashboard">Ir al Dashboard</a>
      </section>;
    }

    return <UsuariosPage />;
  }

  return (
    <AppShell
      source={publicConfig}
      page={page}
      menuOpen={menuOpen}
      onToggleMenu={() => setMenuOpen((open) => !open)}
      usuario={usuario}
      onLogout={cerrarSesion}
      cerrandoSesion={cerrandoSesion}
    >
      <main
        className="workspace"
        id="contenido"
        ref={contentRef}
        tabIndex={-1}
      >
        {page ? (
          !access?.allowed ? (
            <section className="surface not-found">
              <span className="eyebrow">CONFIGURACIÓN DE FUENTE</span>
              <h1>
                {publicConfig.valid
                  ? 'Integración no habilitada'
                  : 'Error de configuración'}
              </h1>
              <p role="status">{access?.message}</p>
              {publicConfig.valid && (
                <a className="text-link" href="#/monitor">
                  Ir al Monitor de Salud SOA
                </a>
              )}
            </section>
          ) : page.id === 'dashboard' ? (
            <DashboardPage source={publicConfig} />
          ) : page.id === 'explorador' ? (
            <ExploradorPage source={publicConfig} />
          ) : page.id === 'consultoras' ? (
            <ConsultorasPage source={publicConfig} />
          ) : page.id === 'monitor' ? (
            <MonitorPage source={publicConfig} />
          ) : page.id === 'usuarios' ? (
            renderizarUsuarios()
          ) : (
            <PreparationView page={page} />
          )
        ) : (
          <section className="surface not-found">
            <h1>Vista no encontrada</h1>
            <p>Este destino no forma parte de las vistas de EcoInvest.</p>
            <a className="text-link" href={`#/${navigation[0]!.id}`}>
              Ir al Dashboard Analítico
            </a>
          </section>
        )}
      </main>
    </AppShell>
  );
}
