
import type { ReactNode } from 'react';
import ecoInvestLogo from '../assets/ecoinvest-logo.png';
import senaceLogo from '../assets/senace-logo.webp';
import bcrpLogo from '../assets/bcrp-logo.png';
import { NavigationIcon } from '../components/NavigationIcon';
import { navigation } from './navigation';
import type { NavigationItem } from './navigation';
import { sourcePresentation } from '../api/source';
import type { SourceConfig } from '../api/source';
import type { UsuarioSesion } from '../api/auth';

interface AppShellProps {
  readonly source: SourceConfig;
  readonly page: NavigationItem | undefined;
  readonly menuOpen: boolean;
  readonly onToggleMenu: () => void;
  readonly usuario: UsuarioSesion | null;
  readonly onLogout: () => Promise<void>;
  readonly cerrandoSesion: boolean;
  readonly children: ReactNode;
}

export function AppShell({
  source,
  page,
  menuOpen,
  onToggleMenu,
  usuario,
  onLogout,
  cerrandoSesion,
  children,
}: AppShellProps) {
  const presentation = sourcePresentation(source);
const menuVisible = navigation.filter(
  (item) =>
    item.id !== 'usuarios' ||
    !usuario ||
    usuario.roles.includes('ROLE_ADMINISTRADOR')
);

  const rol = usuario?.roles.includes('ROLE_ADMINISTRADOR')
    ? 'Administrador'
    : 'Analista';

  return (
    <div className="app-shell">
      <a
        className="skip-link"
        href="#contenido"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('contenido')?.focus();
        }}
      >
        Saltar al contenido
      </a>

      <aside className="sidebar" aria-label="EcoInvest">
        <div className="brand-row">
          <a
            className="brand"
            href="#/dashboard"
            aria-label="EcoInvest, Dashboard Analítico"
          >
            <img className="brand-logo" src={ecoInvestLogo} alt="" />
            <span>EcoInvest</span>
          </a>

          <button
            className="menu-toggle"
            type="button"
            aria-controls="primary-navigation"
            aria-expanded={menuOpen}
            onClick={onToggleMenu}
          >
            <NavigationIcon kind={menuOpen ? 'close' : 'menu'} />
            <span>{menuOpen ? 'Cerrar' : 'Menú'}</span>
          </button>
        </div>

        <div className={`sidebar-content${menuOpen ? ' is-open' : ''}`}>
          <p className="sidebar-caption">INTELIGENCIA GUBERNAMENTAL</p>

          <nav id="primary-navigation" aria-label="Navegación principal">
            <ul className="navigation-list">
            {menuVisible.map((item) => (
                <li key={item.id}>
                  <a
                    className="navigation-link"
                    href={`#/${item.id}`}
                    aria-current={page?.id === item.id ? 'page' : undefined}
                  >
                    <NavigationIcon kind={item.id} />
                    <span>
                    {item.id === 'usuarios' && !usuario
                    ? 'Iniciar sesión'
                    : item.label}
                       </span>
                  </a>
                </li>
              ))}
            </ul>
          </nav>

          <div className="sidebar-note">
            <span className="eyebrow">{presentation.heading}</span>
            <p>{presentation.description}</p>
            <span className="muted">{presentation.note}</span>
          </div>
        </div>
      </aside>

      <div className="app-body">
        <header className="topbar">
          <p className="page-title">{page?.label ?? 'EcoInvest'}</p>

          <div className="source-group" aria-label={presentation.entitiesLabel}>
            <div className="source-label">
              <img
                className="source-logo source-logo--bcrp"
                src={bcrpLogo}
                alt=""
              />
              <div>
                <strong>BCRP</strong>
                <span>DATOS<br />MACROECONÓMICOS</span>
              </div>
            </div>

            <span className="source-divider" aria-hidden="true" />

            <div className="source-label source-label--senace">
              <img
                className="source-logo source-logo--senace"
                src={senaceLogo}
                alt=""
              />
              <div>
                <strong>SENACE</strong>
                <span>INFORMACIÓN<br />AMBIENTAL</span>
              </div>
            </div>
          </div>

          <div className="theme-label">
            <NavigationIcon kind="sun" />
            <span>Modo<br /><strong>Claro</strong></span>
          </div>

          {usuario ? (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
              marginLeft: 'auto',
            }}>
              <div style={{ textAlign: 'right' }}>
                <strong style={{ display: 'block' }}>
                  {usuario.username}
                </strong>
                <span style={{ fontSize: 12, color: '#64748b' }}>
                  {rol}
                </span>
              </div>

              <button
                type="button"
                onClick={() => void onLogout()}
                disabled={cerrandoSesion}
                style={{
                  backgroundColor: '#b42318',
                  color: '#ffffff',
                  border: 'none',
                  padding: '9px 14px',
                  borderRadius: 7,
                  cursor: cerrandoSesion ? 'wait' : 'pointer',
                  fontWeight: 600,
                }}
              >
                {cerrandoSesion ? 'Saliendo...' : 'Cerrar sesión'}
              </button>
            </div>
          ) : (
            <span className="stage-label">{presentation.stage}</span>
          )}
        </header>

        {children}
      </div>
    </div>
  );
}
