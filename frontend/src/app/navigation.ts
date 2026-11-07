export type ViewId = 'dashboard' | 'explorador' | 'consultoras' | 'monitor';

export interface NavigationItem {
  readonly id: ViewId;
  readonly label: string;
  readonly eyebrow: string;
  readonly description: string;
}

export const navigation: readonly NavigationItem[] = [
  { id: 'dashboard', label: 'Dashboard Analítico', eyebrow: 'PANORAMA GENERAL', description: 'Registros de proyectos e indicadores económicos, con su contexto y procedencia.' },
  { id: 'explorador', label: 'Explorador Geográfico', eyebrow: 'EXPLORACIÓN TERRITORIAL', description: 'Una vista espacial y una lista accesible de los proyectos con coordenadas disponibles.' },
  { id: 'consultoras', label: 'Consultoras Ambientales', eyebrow: 'INFORMACIÓN AMBIENTAL', description: 'Consulta de filas y campos originales del recurso ConsultorasAmbientales.' },
  { id: 'monitor', label: 'Monitor de Salud SOA', eyebrow: 'CIRCUITOS Y RESILIENCIA', description: 'Estados de circuitos y métricas de llamadas lógicas, con sus límites de interpretación.' },
];

export function resolveNavigation(hash: string): NavigationItem | undefined {
  return navigation.find((item) => hash === `#/${item.id}`);
}
