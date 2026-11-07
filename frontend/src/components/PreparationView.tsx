import { NavigationIcon } from './NavigationIcon';
import type { NavigationItem, ViewId } from '../app/navigation';

const sections: Record<ViewId, readonly string[]> = {
  dashboard: ['Contexto de consulta', 'Resumen de registros', 'Visualización y proyectos'],
  explorador: ['Contexto de consulta', 'Mapa y lista accesible', 'Ficha del proyecto'],
  consultoras: ['Búsqueda y selección', 'Resumen de filas', 'Tabla y detalle'],
  monitor: ['Contexto de las métricas', 'Circuitos SENACE y BCRP', 'Alcance y limitaciones'],
};

export function PreparationView({ page }: { readonly page: NavigationItem }) {
  return (
    <div className={`preparation-view preparation-view--${page.id}`}>
      <section className="surface introduction" aria-labelledby="view-heading">
        <div>
          <span className="eyebrow">{page.eyebrow}</span>
          <h1 id="view-heading">{page.label}</h1>
          <p>{page.description}</p>
        </div>
        <span className="preparation-badge"><span aria-hidden="true" />En preparación</span>
      </section>
      <section className="surface preparation-panel" aria-labelledby="preparation-heading">
        <div className="preparation-panel-header">
          <span className="feature-icon"><NavigationIcon kind={page.id} /></span>
          <div><span className="eyebrow">FASE 4B · LOTE 1</span><h2 id="preparation-heading">Un espacio para información con contexto</h2></div>
        </div>
        <p className="preparation-description">La navegación y la estructura visual están disponibles. Los datos, filtros y herramientas de esta vista se incorporarán en los siguientes lotes.</p>
        <ol className="preparation-sections" aria-label="Áreas previstas de esta vista">
          {sections[page.id].map((label, index) => (
            <li key={label}><span className="section-number" aria-hidden="true">0{index + 1}</span><strong>{label}</strong><span className="section-state">Pendiente de implementación</span></li>
          ))}
        </ol>
        <div className="scope-note"><span className="scope-dot" aria-hidden="true" /><p>No se muestran registros, indicadores ni estados de proveedores. Esta etapa no realiza consultas al backend.</p></div>
      </section>
      <footer className="workspace-footer"><span>EcoInvest · SENACE + BCRP</span><span>Estructura visual inicial</span></footer>
    </div>
  );
}
