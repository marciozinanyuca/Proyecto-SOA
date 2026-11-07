import type { ReactNode } from 'react';
import ecoInvestLogo from '../assets/ecoinvest-logo.png';
import senaceLogo from '../assets/senace-logo.webp';
import bcrpLogo from '../assets/bcrp-logo.png';
import { NavigationIcon } from '../components/NavigationIcon';
import { navigation } from './navigation';
import type { NavigationItem } from './navigation';

interface AppShellProps {
  readonly page: NavigationItem | undefined;
  readonly menuOpen: boolean;
  readonly onToggleMenu: () => void;
  readonly children: ReactNode;
}

export function AppShell({ page, menuOpen, onToggleMenu, children }: AppShellProps) {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#contenido" onClick={(event) => {
        event.preventDefault();
        document.getElementById('contenido')?.focus();
      }}>Saltar al contenido</a>
      <aside className="sidebar" aria-label="EcoInvest">
        <div className="brand-row">
          <a className="brand" href="#/dashboard" aria-label="EcoInvest, Dashboard Analítico">
            <img className="brand-logo" src={ecoInvestLogo} alt="" />
            <span>EcoInvest</span>
          </a>
          <button className="menu-toggle" type="button" aria-controls="primary-navigation"
            aria-expanded={menuOpen} onClick={onToggleMenu}>
            <NavigationIcon kind={menuOpen ? 'close' : 'menu'} />
            <span>{menuOpen ? 'Cerrar' : 'Menú'}</span>
          </button>
        </div>
        <div className={`sidebar-content${menuOpen ? ' is-open' : ''}`}>
          <p className="sidebar-caption">INTELIGENCIA GUBERNAMENTAL</p>
          <nav id="primary-navigation" aria-label="Navegación principal">
            <ul className="navigation-list">
              {navigation.map((item) => (
                <li key={item.id}>
                  <a className="navigation-link" href={`#/${item.id}`} aria-current={page?.id === item.id ? 'page' : undefined}>
                    <NavigationIcon kind={item.id} /><span>{item.label}</span>
                  </a>
                </li>
              ))}
            </ul>
          </nav>
          <div className="sidebar-note">
            <span className="eyebrow">ESTRUCTURA INICIAL</span>
            <p>Las vistas están en preparación.</p>
            <span className="muted">Sin conexión a proveedores.</span>
          </div>
        </div>
      </aside>
      <div className="app-body">
        <header className="topbar">
          <p className="page-title">{page?.label ?? 'EcoInvest'}</p>
          <div className="source-group" aria-label="Fuentes previstas; sin conexión en esta etapa">
            <div className="source-label"><img className="source-logo source-logo--bcrp" src={bcrpLogo} alt="" /><div><strong>BCRP</strong><span>DATOS<br />MACROECONÓMICOS</span></div></div>
            <span className="source-divider" aria-hidden="true" />
            <div className="source-label source-label--senace"><img className="source-logo source-logo--senace" src={senaceLogo} alt="" /><div><strong>SENACE</strong><span>INFORMACIÓN<br />AMBIENTAL</span></div></div>
          </div>
          <div className="theme-label"><NavigationIcon kind="sun" /><span>Modo<br /><strong>Claro</strong></span></div>
          <span className="stage-label">Frontend en preparación</span>
        </header>
        {children}
      </div>
    </div>
  );
}
