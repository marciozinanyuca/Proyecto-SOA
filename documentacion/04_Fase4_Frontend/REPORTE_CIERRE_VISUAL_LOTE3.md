# EcoInvest — Cierre visual del Dashboard, Fase 4B Lote 3

Fecha: 07/11/2026.

**Estado: V01 y V02 corregidos y verificados dentro del alcance de revisión local en modo demostración.** No constituye certificación end-to-end ni auditoría completa de accesibilidad.

## 1. Objetivo y antecedentes

Corregir el desbordamiento móvil V01 y mejorar la altura de los controles propios del Dashboard V02, conservando el diseño basado en Stitch y la lógica existente.

- [Implementación del Lote 3](REPORTE_IMPLEMENTACION_FASE4B_LOTE3.md).
- [Validación visual inicial y defectos](REPORTE_VALIDACION_VISUAL_LOTE3.md).
- [Referencia y conciliación Stitch](REPORTE_REVISION_STITCH_FASE4A.md).

La validación inicial identificó un documento de aproximadamente 744 px de ancho en un viewport de 375 px. Los controles propios medían aproximadamente 39–43 px de alto. Esos antecedentes se conservan sin sobrescribir sus reportes o capturas.

## 2. Archivo modificado y cambios aplicados

Único archivo productivo modificado en esta corrección: [DashboardPage.css](../../frontend/src/features/dashboard/DashboardPage.css).

| Selectores | Cambio |
|---|---|
| `.dashboard-page`, `.dash-data` | Pista explícita `minmax(0, 1fr)` para permitir contracción sin adoptar el ancho mínimo intrínseco de sus descendientes |
| `.dashboard-page > *`, `.dash-data > *` | `min-width: 0` para paneles hijos |
| `.dash-filter-grid` y variantes responsive | Columnas flexibles con mínimo cero, conservando la distribución por resolución |
| Inputs y selects propios | Mínimo de ancho cero y altura mínima de 44 px |
| `.dash-table-scroll` | Mínimo de ancho cero, máximo del 100 % y `overflow-x: auto` local |
| `.dash-primary`, `.dash-secondary`, paginación y summary de escenarios | Altura mínima de 44 px |
| `table.dash-sr-only` | `display: block` para que la tabla accesible recortada respete su caja de 1 px y no ensanche el documento |

No se añadió `overflow-x: hidden` global. No se modificaron DashboardPage.tsx, API, fixtures, AppShell, navegación, estilos globales, backend, OpenAPI o SQLite. No se instalaron paquetes ni se cambiaron reglas de negocio.

## 3. Investigación y resultado intermedio

La primera corrección confinó correctamente la tabla visible y elevó los controles medidos a 44 px, pero el documento móvil todavía alcanzó 480 px. Ese resultado intermedio está en [correccion/resultados.json](evidencias/lote3/correccion/resultados.json).

La inspección DOM posterior identificó elementos de la tabla accesible del gráfico, especialmente su caption, extendiéndose hasta aproximadamente 480 px. Aunque la tabla tenía la clase de contenido visualmente oculto, su presentación de tabla mantenía una contribución de ancho intrínseco. Se añadió la regla específica `table.dash-sr-only { display: block; }` dentro del mismo CSS autorizado.

La corrección no elimina el contenido alternativo del DOM ni utiliza `display: none`. No obstante, su exposición semántica a tecnologías asistivas no fue comprobada con un lector de pantalla y permanece como límite de esta revisión.

Se repitieron typecheck, pruebas y build después de este ajuste, además de las medidas y capturas finales. No se considera V01 resuelto por compilación solamente.

## 4. Medidas DOM antes y después

| Viewport | Documento inicial | Documento final | clientWidth final | Resultado |
|---|---:|---:|---:|---|
| Escritorio 1440 × 900 | 1425 px | 1425 px | 1425 px | Sin desbordamiento horizontal global |
| Tablet 768 × 1024 | 753 px | 753 px | 753 px | Sin desbordamiento horizontal global |
| Móvil 375 × 812 | 744 px | 360 px | 360 px | V01 corregido en el caso revisado |

La diferencia de 15 px entre viewport y clientWidth corresponde al espacio observado de la barra vertical del navegador. El criterio relevante se cumple: `document.scrollWidth === document.clientWidth` en las tres resoluciones finales.

### Tabla visible

| Resolución | Ancho cliente del contenedor | Ancho desplazable | Desplazamiento programático comprobado |
|---|---:|---:|---:|
| Escritorio | 1087 px | 1087 px | 0 px; cabe sin scroll horizontal |
| Tablet | 711 px | 726 px | 15 px |
| Móvil | 326 px | 726 px | 100 px |

La tabla conserva su contenido ancho y se desplaza dentro de su contenedor sin ensanchar la página. No se ocultaron columnas ni se modificaron datos.

### Controles

El mínimo observado fue **44 px** en escritorio, tablet y móvil para los controles propios muestreados: selects, input de búsqueda, botón de consulta, botones de paginación y summary del selector de escenarios. V02 queda resuelto para los controles medidos, sin alterar los botones globales del shell.

## 5. Validaciones ejecutadas

Fecha: 07/11/2026. Directorio: `D:\Documentos\GitHub\Proyecto-SOA\frontend`.

```powershell
npm run typecheck
npm run test:api
npm run build
```

Se ejecutaron dos pasadas durante la corrección: una tras ajustar grids y controles, y otra después de corregir la tabla accesible. Ambas finalizaron con código 0. No se suman las pruebas de ambas ejecuciones como una suite mayor.

| Validación final | Resultado |
|---|---|
| Typecheck | Satisfactorio |
| test:api | 54 pruebas aprobadas, 0 fallos, 0 canceladas, 0 omitidas |
| Build | Satisfactorio, Vite 8.3.5, 33 módulos transformados |

El build final generó aproximadamente 15,93 kB de CSS y 255,70 kB de JavaScript. Los scripts produjeron artefactos locales en `.api-test-build/` y `dist/`; no se editaron manualmente. No se ejecutaron Maven ni pruebas Java.

Node 22.16.0 y npm 10.9.2 corresponden al entorno verificado en actividades previas; no se repitieron comandos de versión en esta corrección. No se cambiaron versiones ni dependencias.

## 6. Revisión local y evidencia responsive

Se inició exclusivamente Vite local mediante `npm run dev -- --port=5173 --strictPort`; su salida efectiva fue `vite --host 127.0.0.1`, disponible en `http://127.0.0.1:5173/`.

Se utilizó Edge headless con perfil temporal aislado, depuración local y CDP desde Node, sin instalar herramientas. Las dimensiones se emularon con deviceScaleFactor 1 y `mobile: false`: estas pruebas validan el layout a esos tamaños, no un teléfono físico ni gestos táctiles reales.

Se esperó a que el KPI inicial mostrase 4 antes de capturar. Las capturas son de página completa, con viewport de las dimensiones indicadas, por lo que su altura total excede la altura del viewport.

| Evidencia final | Ruta |
|---|---|
| Escritorio | [escritorio.png](evidencias/lote3/correccion/final/escritorio.png) |
| Tablet | [tablet.png](evidencias/lote3/correccion/final/tablet.png) |
| Móvil | [movil.png](evidencias/lote3/correccion/final/movil.png) |
| Medidas y escenarios finales | [resultados.json](evidencias/lote3/correccion/final/resultados.json) |

Las capturas finales de escritorio y móvil se inspeccionaron visualmente. La de tablet se generó y cuenta con medidas DOM finales; su composición ya había sido inspeccionada en la revisión anterior. La captura de escritorio presenta una posición inusual del contenido fijo de la sidebar y las capturas muestran el enlace de salto visible; no se investigó si se debe al foco o a la captura de página completa después de emular tamaños. Por ello, esta evidencia no certifica coincidencia píxel a píxel del shell. El shell no fue modificado y su revisión no se amplía automáticamente en esta corrección.

## 7. Interacciones y regresión

Durante la primera pasada con el CSS corregido se comprobaron:

- Cambio a TODOS y año 2018: 8 registros, 2 aprobados y valor artificial −0,75 %.
- Paginación: filas 1–3 a 4–6 de 8.
- Búsqueda sin coincidencias: lista local vacía sin alterar KPI.
- Navegación a Consultoras en preparación y regreso al Dashboard, con foco en `contenido`.
- Escenarios empty, degraded, error y success.

Tras el ajuste final de la tabla accesible se repitieron los cuatro escenarios principales en móvil:

| Escenario | KPI observados | Ancho de documento |
|---|---|---:|
| empty | 0, 0, indicador artificial 1,25 % | 360 px |
| degraded | SENACE no disponible; BCRP artificial 1,25 % | 360 px |
| error | Los tres valores no disponibles | 360 px |
| success | 4, 1, indicador artificial 1,25 % | 360 px |

No se registraron eventos `Runtime.exceptionThrown` en los recorridos monitorizados. No hubo solicitudes externas ni solicitudes cuya ruta comenzase por `/api/`; los módulos fuente servidos desde `/src/api/` no se contabilizaron como llamadas backend.

Las 54 pruebas siguen cubriendo lógica y API artificial; no son pruebas completas de componentes React o de accesibilidad.

## 8. Comparación visual y límites

Se conservan las superficies claras, acentos verde/turquesa, tres KPI, panel central, tabla y jerarquía de la versión revisada frente a Stitch. El móvil ahora presenta paneles dentro del ancho disponible; la tabla es el área desplazable horizontalmente.

Permanecen las diferencias funcionales aprobadas: distribución por ESTADO en lugar de series mensuales ficticias, ausencia de inversión y correlación no soportadas, año de inicio y aviso explícito de datos artificiales. No se reintrodujeron métricas inexistentes.

No se declara una réplica exacta del prototipo ni una auditoría WCAG completa. Quedan fuera de la validación: lectores de pantalla, dispositivos físicos, todas las combinaciones de zoom/tamaño de fuente, otros navegadores e integración con backend real. La captura fija del shell descrita arriba merece revisión puntual si se pretende certificar el diseño completo más allá de V01/V02.

## 9. Aislamiento y estado final

Vite y el Edge aislado de revisión se cerraron al terminar. No se inició Spring Boot, no se accedió a SQLite, no se consultaron SENACE/BCRP y no se realizaron commits. No se borraron reportes, capturas previas ni perfiles temporales; no se atribuye una limpieza que no fue ejecutada.

**V01 y V02 quedan cerrados dentro del alcance de las resoluciones, escenarios y controles comprobados.** El Dashboard permanece en modo demostración; este cierre no habilita automáticamente conexión real, cambios de backend o implementación del Explorador.

Siguiente paso recomendado: compartir este reporte con ChatGPT para planificar el Lote 4 — Explorador Geográfico, manteniendo autorizaciones separadas para archivos, cartografía, tiles, instalaciones y tráfico. Si se requiere certificación visual del shell completo, revisar primero la anomalía de captura indicada sin asumir un defecto productivo demostrado.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest corrigió V01 y V02 modificando únicamente `frontend/src/features/dashboard/DashboardPage.css`. Los grids usan mínimos cero, la tabla visible tiene scroll local y los controles propios medidos alcanzan 44 px. Una primera pasada dejó 480 px de ancho móvil; se localizó además la tabla accesible visualmente oculta del gráfico y se ajustó su presentación con `display: block`. La revisión final midió 360 px de documento y 360 px de clientWidth en viewport 375 px, sin desbordamiento global. Escritorio y tablet también igualan documentWidth/clientWidth.

La validación final pasó typecheck, 54 pruebas y build. Los escenarios empty, degraded, error y ready se comprobaron con datos artificiales; no hubo excepciones JavaScript monitorizadas ni tráfico backend/proveedores. Hay capturas y resultados en `documentacion/04_Fase4_Frontend/evidencias/lote3/correccion/final/`. No se modificaron lógica, contratos o backend.

El cierre corresponde a V01/V02 y al Dashboard mock, no a una certificación end-to-end o accesibilidad completa. La captura de página completa de escritorio muestra una posición inusual de la sidebar que no se investigó; no afirmar fidelidad píxel a píxel o ausencia de cualquier defecto del shell. No se inició el Lote 4. Preparar su siguiente prompt con las decisiones de cartografía aún sujetas a aprobación.
