# EcoInvest — Validación visual del Dashboard, Fase 4B Lote 3

Fecha: 07/11/2026. Estado: **REVISIÓN EJECUTADA; CIERRE VISUAL PENDIENTE**.

## 1. Objetivo y alcance

Revisar en navegador el Dashboard Analítico implementado en React + TypeScript y contrastarlo con la referencia original de Google Stitch. Se utilizaron exclusivamente respuestas artificiales del modo mock existente.

No se modificaron archivos productivos, backend, OpenAPI, SQLite ni el proyecto remoto Stitch. No se instalaron dependencias ni se inició el Lote 4. Esta revisión no certifica integración end-to-end de EcoInvest.

Antecedentes:

- [Implementación del Dashboard](REPORTE_IMPLEMENTACION_FASE4B_LOTE3.md).
- [Conciliación previa con Stitch](REPORTE_REVISION_STITCH_FASE4A.md).

## 2. Referencia y herramientas

Proyecto Stitch: `Proyecto SOA - EcoInvest`, `projects/3389709097732066354`.

Pantalla: `a708110083bb4fa38ee8aa0935dcb1a9`. Se recuperó mediante MCP, en modo lectura, la captura original `projects/3389709097732066354/files/3332757910231984049`.

La herramienta habitual de automatización devolvió `trusted Node process exited unexpectedly` al inicializar. Se continuó con Microsoft Edge instalado, en modo headless, perfil temporal propio y Chrome DevTools Protocol (CDP) local. No se utilizó el perfil personal del usuario.

Se inició Vite desde `frontend/` mediante:

```powershell
npm run dev -- --port=5173 --strictPort
```

La salida efectiva del script fue `vite --host 127.0.0.1`; Vite 8.3.5 quedó disponible en `http://127.0.0.1:5173/`. La configuración existente fija host local y strictPort. Se abrió `/#/dashboard`.

Edge se inició con `--headless=new`, `--no-first-run`, `--disable-background-networking`, depuración restringida a `127.0.0.1`, puerto dinámico y directorio temporal aislado. CDP se utilizó desde Node mediante WebSocket nativo, sin instalar herramientas. No se desactivó TLS ni se leyeron credenciales.

## 3. Procedimiento y límites de observación

Se realizaron capturas completas con viewport de escritorio 1440 × 900, tablet 768 × 1024 y móvil 375 × 812, con deviceScaleFactor 1. La emulación cambió dimensiones con `mobile: false`: evalúa layout a esas anchuras, pero no certifica comportamiento de un teléfono físico o gestos táctiles reales.

Se inspeccionaron imágenes renderizadas, dimensiones DOM, mensajes, conteos, navegación, filtros, búsqueda, paginación, escenarios y eventos de excepción JavaScript. Las interacciones se ejecutaron mediante DOM/CDP; se envió además una tecla Tab real por CDP para comprobar foco visible. No se realizó una auditoría completa con lector de pantalla.

La primera captura de escritorio se tomó durante loading; posteriormente se capturó también el resultado ready. Se conservan ambas evidencias sin presentar la captura inicial como resultado definitivo. Las capturas `*-ready.png` corresponden a TODOS/2018 después de aplicar filtros. Algunas incluyen el enlace de salto al contenido enfocado, como evidencia de teclado, no como defecto visual.

## 4. Evidencia disponible

| Evidencia | Archivo |
|---|---|
| Escritorio con datos | [escritorio-ready.png](evidencias/lote3/escritorio-ready.png) |
| Tablet con datos | [tablet-ready.png](evidencias/lote3/tablet-ready.png) |
| Móvil con datos y desbordamiento | [movil-ready.png](evidencias/lote3/movil-ready.png) |
| Escritorio durante carga | [escritorio.png](evidencias/lote3/escritorio.png) |
| Primera captura tablet | [tablet.png](evidencias/lote3/tablet.png) |
| Primera captura móvil | [movil.png](evidencias/lote3/movil.png) |
| Medidas, escenarios, foco y red | [resultados.json](evidencias/lote3/resultados.json) |
| Año, medidas de contenedores y navegación | [comprobaciones-adicionales.json](evidencias/lote3/comprobaciones-adicionales.json) |

Las capturas completas tienen altura superior al viewport para documentar la página completa. No contienen datos oficiales reales ni credenciales.

## 5. Comparación con Stitch

| Elemento | Resultado observado |
|---|---|
| Sidebar y header | Conservan identidad, navegación lateral en escritorio y encabezado con fuentes; en tablet/móvil existe menú colapsable |
| Logos | Los tres recursos de imagen cargaron correctamente en las resoluciones revisadas |
| Filtros | Distribución superior y acción verde coherentes con la composición original; etiquetas adaptadas al año de inicio |
| Tres KPI | Tarjetas blancas, iconos, jerarquía numérica y acentos verde/turquesa coherentes con la referencia |
| Panel central | Conserva panel amplio, leyenda y paleta; utiliza distribución por estado en lugar de métricas ficticias |
| Tabla inferior | Conserva búsqueda, encabezado, filas, badges y paginación, adaptados a campos disponibles |
| Bordes y superficies | Paneles claros con bordes suaves, radios y sombras discretas semejantes a Stitch |
| Espaciado y densidad | La implementación es más extensa verticalmente por avisos y metadatos; no es una réplica píxel a píxel |
| Tipografía | Jerarquía conservada; no se certificó coincidencia exacta de fuentes o métricas tipográficas con el prototipo |

### Diferencias intencionales aprobadas

- Distribución de registros por ESTADO en lugar de evolución mensual, inversión o correlación estadística no disponibles.
- KPI de registros filtrados y aprobados en lugar de expedientes certificados, viabilidad o inversión.
- Año de FECHA_INICIO, no fecha de aprobación ni supuesto año fiscal.
- Aviso permanente de datos artificiales, selección de escenarios y metadatos de calidad/cobertura.
- Ausencia de sesión/rol inventados, telemetría ficticia, verificación SUNAT, exportaciones sin implementar y botones sin comportamiento.

Estas diferencias preservan honestidad funcional y no deben corregirse reintroduciendo información inexistente.

## 6. Resultados responsive

| Resolución | Medidas y resultado |
|---|---|
| 1440 × 900 | scrollWidth inicial 1425 px, sin desbordamiento horizontal global observado; tres KPI alineados y paneles legibles |
| 768 × 1024 | scrollWidth inicial 753 px, sin desbordamiento horizontal global observado; KPI apilados, filtros en dos columnas y menú colapsable |
| 375 × 812 | scrollWidth 744 px: desbordamiento global confirmado, contenido y controles recortados |

En móvil, `.workspace` mide 375 px y `.dashboard-page` 343 px, pero la pista de grid de `.dashboard-page` alcanza aproximadamente 727,8 px. `.dash-data`, `.dash-records` y `.dash-filters` adoptan ese ancho. El contenedor `.dash-table-scroll` termina midiendo aproximadamente 725,8 px, por lo que su scroll no queda confinado al ancho útil del dispositivo.

La evidencia es consistente con la contribución del ancho mínimo intrínseco de la tabla a los grids con pistas automáticas y descendientes con `min-width: auto`. La corrección propuesta debe verificarse en navegador; no se aplicó experimentalmente ni se da por demostrada su suficiencia.

## 7. Interacciones verificadas

| Comprobación | Resultado observado |
|---|---|
| Entrada inicial | Hidrocarburos/2017, 4 registros, 1 aprobado y valor artificial 1,25 % |
| Cambiar sector sin aplicar | Aparece aviso de filtros pendientes; los datos anteriores permanecen identificados |
| Aplicar TODOS | 8 registros, 2 aprobados, tabla de tres páginas |
| Aplicar año 2018 | Indicador artificial −0,75 % y período 2018 |
| Paginación | Siguiente cambia de filas 1–3 a 4–6 de 8 |
| Búsqueda sin coincidencias | Cero filas de búsqueda local, sin alterar KPI de la selección |
| Escenario empty | Conteos conocidos cero, tabla vacía y explicación de respuesta oficial vacía simulada |
| Escenario degraded | Conteos SENACE no disponibles e indicador BCRP conservado |
| Escenario error | KPI no disponibles y mensaje de error, sin sustitución por cero |
| Opcionales ausentes | Ausencia explícita, sin cifras inventadas |
| Fechas no evaluables | Advertencia temporal visible y selección evaluable conservada |
| Regreso a success | Datos recuperados dentro del modo artificial |
| Menú móvil | aria-expanded cambia a true y se muestran los cuatro destinos |
| Navegación | Consultoras conserva su vista en preparación; regresar a Dashboard funciona y el foco vuelve a `contenido` |
| Teclado | Tab alcanza el enlace de salto; se observó outline sólido de 3 px |
| Identificación mock | Aviso de datos artificiales visible en las tres resoluciones, aunque en móvil queda afectado por el desbordamiento |

La búsqueda de coincidencias positivas, cancelación y descarte de respuestas obsoletas cuentan con pruebas del modelo documentadas en el reporte de implementación; no se presentan aquí como nuevas comprobaciones visuales exhaustivas. Tampoco se certifica recorrido completo por teclado o comportamiento de lector de pantalla.

## 8. JavaScript, red y aislamiento

No se capturaron eventos `Runtime.exceptionThrown` durante la secuencia monitorizada. Esto no equivale a una auditoría exhaustiva de todos los niveles de consola o código no ejercitado.

Se observaron 32 eventos de solicitud durante el primer recorrido; todos fueron locales a Vite y no hubo destinos externos. El campo `network.api` de `resultados.json` usa un filtro textual amplio y contiene archivos fuente como `/src/api/client.ts`: son módulos servidos por Vite, **no solicitudes a endpoints backend `/api/*`**. No se observaron llamadas al backend, SENACE o BCRP.

No se inició Spring Boot, Maven o SQLite. Se cerraron mediante sus procesos controlados tanto Vite como el Edge aislado de revisión. No se documenta eliminación del perfil temporal, ya que no se ejecutó su borrado.

## 9. Defectos y prioridades

| ID | Prioridad | Hallazgo | Archivo y propuesta mínima |
|---|---|---|---|
| V01 | Alta; bloquea cierre visual móvil | Desbordamiento global de 744 px a viewport 375 px, con recorte de filtros, gráfico y tabla | [DashboardPage.css](../../frontend/src/features/dashboard/DashboardPage.css): evaluar pistas `minmax(0, 1fr)` y `min-width: 0` en contenedores del Dashboard para confinar el scroll a la tabla |
| V02 | Baja; mejora táctil | Controles principales de unos 43,2 px y botones de paginación de unos 39,1 px de alto | [DashboardPage.css](../../frontend/src/features/dashboard/DashboardPage.css): valorar mínimo 44 px en controles propios del Dashboard; no constituye por sí solo incumplimiento de todos los criterios de accesibilidad |

No se detectó un defecto funcional reproducible en filtros, paginación, degradación o navegación durante el recorrido. No se propone reconstruir la pantalla ni modificar backend, OpenAPI, AppShell o datos del contrato.

## 10. Corrección y validación recomendadas

Solicitar autorización específica antes de editar `frontend/src/features/dashboard/DashboardPage.css`. Priorizar V01 y considerar V02 dentro de la misma autorización si el usuario lo aprueba.

Criterios de aceptación de la corrección:

1. En 375 × 812, el documento no supera horizontalmente el ancho del viewport.
2. Filtros, avisos, KPI, gráfico y metadatos quedan dentro del ancho disponible.
3. Solo la tabla puede desplazarse horizontalmente dentro de su contenedor, sin ensanchar el documento.
4. Escritorio y tablet conservan composición e interacciones anteriores.
5. La selección y el indicador siguen correspondiendo al año aplicado; vacíos y degradaciones conservan su semántica.
6. No aparecen excepciones JavaScript ni tráfico a backend/proveedores.

Las nuevas ejecuciones de validación y capturas deberán autorizarse según el alcance acordado. No iniciar automáticamente el Explorador Geográfico o una integración real.

## 11. Conclusión

La revisión visual e interactiva se ejecutó con evidencia de escritorio, tablet y móvil. La composición general es coherente con Stitch y las adaptaciones funcionales aprobadas; los escenarios principales funcionan en modo artificial. **El Lote 3 no queda cerrado visualmente por el desbordamiento móvil V01.**

Este reporte documenta hallazgos sin correcciones productivas. El siguiente paso es aprobar una modificación mínima de CSS y su revisión de regresión.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest tiene Dashboard React + TypeScript implementado en modo mock; anteriormente superó typecheck, 54 pruebas y build. El 07/11/2026 se revisó en Edge headless con Vite local, sin backend, SQLite ni llamadas a proveedores. Se verificaron sectores, año 2018, búsqueda sin coincidencias, paginación, navegación, escenarios ready/empty/degraded/error y opcionales, aviso artificial y foco visible básico.

Escritorio y tablet no presentaron desbordamiento global. En móvil 375 × 812, el documento alcanzó 744 px y los contenedores grid se expandieron a unos 727,8 px. La prioridad es corregir el ancho intrínseco en `frontend/src/features/dashboard/DashboardPage.css`, previa autorización, y repetir las capturas. No modificar datos, contrato, backend ni reconstruir la pantalla. Como mejora menor se propone ampliar controles táctiles propios a 44 px.

Las evidencias se encuentran en `documentacion/04_Fase4_Frontend/evidencias/lote3/`. No se corrigió código; no existe certificación end-to-end ni cierre visual completo. El siguiente prompt debe centrarse en V01 y su regresión visual antes de continuar con el Lote 4.
