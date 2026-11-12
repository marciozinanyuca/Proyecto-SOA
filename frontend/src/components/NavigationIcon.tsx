import type { ReactNode } from 'react';
import type { ViewId } from '../app/navigation';

type IconKind = ViewId | 'menu' | 'close' | 'sun';

// SVG de navegación sencillo; no sustituye los logos de identidad originales.
export function NavigationIcon({ kind }: { readonly kind: IconKind }) {
  const paths: Record<IconKind, ReactNode> = {
    dashboard: <><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M7 16v-4m5 4V8m5 8v-6" /></>,
    explorador: <><path d="m9 18-6 3V6l6-3 6 3 6-3v15l-6 3-6-3Z" /><path d="M9 3v15m6-12v15" /></>,
    consultoras: <><path d="M5 21V3h10v18M15 9h4v12M3 21h18M8 7h1m2 0h1M8 11h1m2 0h1M8 15h1m2 0h1M9 21v-3h2v3" /></>,
    monitor: <><path d="m7 7-5 5 5 5m10-10 5 5-5 5m-3-14-4 18" /></>,
usuarios: <><circle cx="9" cy="7" r="4" /><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
    menu: <path d="M4 6h16M4 12h16M4 18h16" />,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></>,
  };
  return <svg className="navigation-icon" width="24" height="24" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{paths[kind]}</svg>;
}
