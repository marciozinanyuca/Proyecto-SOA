# EcoInvest — Fase 4B, Lote 4: Explorador Geográfico

Fecha: 07/11/2026.

**Estado: implementación y validación local del Explorador offline completadas dentro del alcance autorizado.** Los datos son artificiales. No existe cartografía base contratada ni certificación geoespacial o integración end-to-end con backend.

## 1. Objetivo y estado inicial

Implementar la segunda vista funcional de EcoInvest conservando shell, navegación, logos y Dashboard existentes. El punto de partida incluía Dashboard en modo demostración, cliente API con fetch y adaptadores, mocks explícitos, coordinador de solicitudes y pruebas Node. El Explorador era una vista en preparación.

Referencias:

- [Revisión Stitch Fase 4A](REPORTE_REVISION_STITCH_FASE4A.md).
- [Implementación Lote 1](REPORTE_IMPLEMENTACION_FASE4B_LOTE1.md).
- [Implementación Lote 2](REPORTE_IMPLEMENTACION_FASE4B_LOTE2.md).
- [Implementación Dashboard](REPORTE_IMPLEMENTACION_FASE4B_LOTE3.md).
- [Cierre visual Dashboard](REPORTE_CIERRE_VISUAL_LOTE3.md).
- [Contrato OpenAPI](../../backend/src/main/resources/openapi.yaml).

No se reconstruyó el scaffold ni se modificaron backend, OpenAPI, SQLite, AppShell, navegación o estilos globales. No se instalaron dependencias.

## 2. Referencia Stitch inspeccionada

| Recurso | Identificación |
|---|---|
| Proyecto | Proyecto SOA - EcoInvest |
| ID | projects/3389709097732066354 |
| Pantalla | EcoInvest Perú - Explorador Geográfico |
| Pantalla MCP | projects/3389709097732066354/screens/156dce54ec4346fcae22ed3061eb711b |
| HTML | projects/3389709097732066354/files/18308596521326607367 |
| Captura | projects/3389709097732066354/files/14284738265624431124 |

Se recuperaron mediante MCP el HTML y la captura originales en modo lectura. La composición observada contiene filtros superiores, panel espacial grande a la izquierda y ficha lateral a la derecha. Se conservaron esa distribución y la identidad clara verde/turquesa/azul.

No se modificó el proyecto remoto, no se descargaron nuevamente logos y no se copió HTML completo como JSX. No se incorporaron CDN, scripts o credenciales del prototipo.

## 3. Decisión cartográfica

Se aprobó una visualización SVG local, sin biblioteca nueva ni servicios de tiles, para este lote. No se instaló Leaflet o React Leaflet. La alternativa de mapa navegable con cartografía base queda sujeta a selección, evaluación y autorización posteriores.

El panel se rotula **Esquema de coordenadas**, **Offline** y **sin mapa base ni CRS certificado**. No se dibuja una silueta de Perú como si fuese una capa oficial.

Los puntos utilizan una transformación equirectangular ilustrativa:

```text
x = (longitud + 180) / 360 × 100
y = (90 - latitud) / 180 × 100
```

La extensión visual se ajusta a los puntos utilizables con margen. El zoom modifica el viewBox; Acercar, Alejar y Ajustar tienen comportamiento local. No existe geocodificación, tráfico cartográfico, descarga de tiles o servicio geográfico oficial integrado.

La validación acepta números finitos y rangos globales latitud [-90, 90] y longitud [-180, 180]. Esto no certifica datum, CRS, ubicación verdadera o pertenencia territorial. No se intercambian ejes ni se rellenan ausencias con cero. Un cero explícito dentro de rango se conserva como dato, sin certificarlo como ubicación válida real.

La biblioteca y el proveedor de mapas son decisiones distintas. Los servidores públicos de OpenStreetMap no se consideran de uso ilimitado ni una fuente autorizada para descarga offline. Referencias consultadas al diseñar: [React Leaflet](https://react-leaflet.js.org/docs/start-installation/) y [política de tiles OSM](https://operations.osmfoundation.org/policies/tiles/). No se efectuaron solicitudes de tiles.

## 4. Archivos creados y modificados

### Creados

| Archivo | Responsabilidad |
|---|---|
| [ExploradorPage.tsx](../../frontend/src/features/explorador/ExploradorPage.tsx) | Vista, filtros, selección, SVG, lista y ficha |
| [ExploradorPage.css](../../frontend/src/features/explorador/ExploradorPage.css) | Composición, estados, foco y responsive propios |
| [exploradorModel.ts](../../frontend/src/features/explorador/exploradorModel.ts) | Coordenadas, plano, filtros, selección y carga de escenarios |
| [demoSelectionStore.ts](../../frontend/src/api/demoSelectionStore.ts) | Reutilización en memoria y cargas concurrentes compartidas de demostración |
| [explorador.test.mjs](../../frontend/tests/api/explorador.test.mjs) | Diez pruebas nuevas del modelo y reutilización |

### Modificados

| Archivo | Cambio |
|---|---|
| [App.tsx](../../frontend/src/app/App.tsx) | Conecta únicamente el destino explorador a la nueva vista |
| [fixtures.ts](../../frontend/src/mocks/fixtures.ts) | Añade seis fixtures geográficos separados, sin cambiar los históricos |
| [dashboardModel.ts](../../frontend/src/features/dashboard/dashboardModel.ts) | Delega reutilización de cargas mock al almacén en memoria |
| [tsconfig.api-tests.json](../../frontend/tsconfig.api-tests.json) | Incluye el modelo del Explorador en la compilación de pruebas |
| [package.json](../../frontend/package.json) | Añade explorador.test.mjs al comando test:api |

No se modificó DashboardPage.tsx ni el diseño o lógica de sus controles. Consultoras y Monitor continúan en preparación. No se cambiaron tipos públicos, adaptadores, cliente HTTP o createMockFetch.

## 5. Datos, mocks y funcionalidades

El Explorador utiliza el modelo ProyectoSenacePublico del contrato: ID, titular, RUC textual, título/proyecto, unidad, tipo, actividad, fecha y año de inicio, estado, descripción, coordenadas, resolución y origen, cuando estén disponibles.

No añade región, elevación, expediente, inversión, impacto económico regional, documentos externos o CRS certificado. La ficha muestra ausencias como No disponible. Los IDs internos de selección son índices de fila, no identificadores oficiales nuevos; permiten conservar filas repetidas sin deduplicarlas.

### Fixtures geográficos

Se añadieron variantes `exploradorDemo:{sector}:{anio}` para Hidrocarburos, Minería y TODOS, en 2017 y 2018. Se derivan de copias de los fixtures artificiales existentes y añaden campos expresamente artificiales de descripción, unidad y resolución y casos de coordenadas:

- Coordenadas numéricas dentro de rango, preservando sus decimales JavaScript.
- Coordenadas ausentes.
- Latitud fuera de rango.

Estos fixtures no son respuestas reales ni acreditan ubicación oficial. Los resultados del Dashboard sin coordenadas no se enriquecen silenciosamente: siguen sin marcadores. Para probar marcadores se aplica explícitamente el escenario de coordenadas artificiales.

### Funcionalidades implementadas

- Filtros de sector y año de inicio con consulta mediante botón; año inicial 2017 si no existe selección previa reutilizable.
- Filtro local por estado y búsqueda local por campos cargados, sin solicitudes adicionales.
- Lista alternativa accesible, utilizable aunque no haya coordenadas.
- Marcadores seleccionables mediante clic o Enter/Espacio y ficha sincronizada con la fila correspondiente.
- Selección desde lista de registros sin coordenadas.
- Zoom local del esquema y ajuste de extensión.
- Avisos y metadatos independientes de la presencia de filas.
- Estados loading, ready, empty, degraded y error, con cancelación y descarte de respuestas obsoletas mediante createLatestRequest.
- Aviso visible **MODO DEMOSTRACIÓN — DATOS ARTIFICIALES**.

Una lista desconocida no se convierte en cero. La selección conocida sin coincidencias se diferencia de indisponibilidad. Puntos coincidentes pueden superponerse; la lista permite seleccionar todas las filas. No se certifican empresas/proyectos únicos.

## 6. Reutilización de API y resultados

```text
ExploradorPage
  → createLatestRequest
  → loadExplorer
      → loadDashboardDemo compartido, o escenario geográfico separado
      → createApiClient(mode: mock)
      → createMockFetch
      → adaptDashboard
  → filas tipadas → validación de coordenadas → SVG / lista / ficha
```

El nuevo almacén no introduce transporte propio: conserva resultados en memoria por clave y comparte solicitudes pendientes idénticas. Cada consumidor recibe una copia para evitar mutaciones compartidas. La cancelación de un consumidor no cancela el trabajo necesario para otro. Cuando ya no quedan consumidores, se programa cancelación de la carga pendiente; se permite el remontaje inmediato de StrictMode. El límite de caché es doce resultados por instancia, sin persistencia, polling, TTL o gestor global adicional.

La clave incluye sector, año y escenario. Los fixtures geográficos usan un almacén separado para evitar confundirlos con resultados del Dashboard. No se mezclan datos HTTP reales con mocks.

El Explorador puede iniciar con la última selección aplicada a través del almacén del Dashboard. Esto **no es sincronización bidireccional completa**: al remontarse, DashboardPage conserva su inicialización histórica y los filtros del Explorador no actualizan sus controles automáticamente. Esa ampliación requeriría autorización específica para el estado React compartido y DashboardPage.tsx. No se presenta como funcionalidad terminada.

Las pruebas del almacén verifican una única carga para consumidores concurrentes y reutilización posterior. La revisión de navegador no instrumentó un contador interno de invocaciones mock; la ausencia de duplicados se sustenta en pruebas de modelo, no en un registro de llamadas al proveedor.

## 7. Pruebas ejecutadas y primer fallo

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\frontend`. Fecha: 07/11/2026.

```powershell
npm run typecheck
npm run test:api
npm run build
```

La primera ejecución se detuvo en typecheck con TS2322 en demoSelectionStore.ts: inferencia incompatible entre Promise<T> y Promise<Awaited<T>>. No se ejecutaron pruebas o build en ese intento. Se añadió una anotación explícita al objeto de carga pendiente dentro del archivo autorizado y se repitió la secuencia.

| Validación final | Resultado observado |
|---|---|
| Typecheck | Satisfactorio |
| test:api | 64 pruebas aprobadas, 0 fallos, 0 canceladas, 0 omitidas |
| Build | Satisfactorio; Vite 8.3.5, 37 módulos transformados |

Las 64 pruebas corresponden a una sola suite actual: las 54 existentes y diez nuevas. No se suman resultados de ejecuciones históricas. El build generó aproximadamente 22,07 kB CSS y 270,11 kB JavaScript. Se escribieron artefactos locales de compilación/pruebas en dist/ y .api-test-build/, sin edición manual.

Las pruebas nuevas cubren ausencia/rangos/no finitos, orden de ejes, conservación numérica, caché y cargas compartidas, cancelación independiente, rechazos, filtros y selección, conservación de filas, separación de escenarios geográficos y ausencia de fetch global. No se añaden fixtures personales reales.

React 19.3.1, TypeScript 7.0.2 y Vite 8.3.5 se conservaron. Node 22.16.0/npm 10.9.2 corresponden al entorno verificado previamente; sus comandos de versión no se repitieron en esta ejecución.

## 8. Revisión local en navegador

Con autorización independiente se inició Vite local y Edge headless con perfil temporal aislado. Se utilizó CDP mediante WebSocket nativo de Node. No se instalaron herramientas ni se abrió el perfil personal del usuario.

Comando de Vite desde frontend/:

```powershell
npm run dev -- --port=5173 --strictPort
```

La salida efectiva del script fue `vite --host 127.0.0.1`, disponible en `http://127.0.0.1:5173/`. Se abrió `/#/explorador` y se capturaron páginas completas después de observar cuatro filas cargadas y seleccionar el primer marcador.

| Viewport | clientWidth | scrollWidth del documento | Marcadores / filas | Mínimo de altura de controles HTML muestreados |
|---|---:|---:|---:|---:|
| 1440 × 900 | 1425 px | 1425 px | 2 / 4 | 44 px |
| 768 × 1024 | 753 px | 753 px | 2 / 4 | 44 px |
| 375 × 812 | 360 px | 360 px | 2 / 4 | 44 px |

Sin desbordamiento horizontal global en las tres medidas. La diferencia de 15 px respecto al viewport corresponde al espacio observado de la barra vertical. Se emularon dimensiones con deviceScaleFactor 1 y mobile:false: no equivale a dispositivo físico ni certifica gestos táctiles.

### Evidencias

- [Escritorio](evidencias/lote4/escritorio.png).
- [Tablet](evidencias/lote4/tablet.png).
- [Móvil](evidencias/lote4/movil.png).
- [Resultados DOM, interacciones y red](evidencias/lote4/resultados.json).

Las capturas de escritorio y móvil se inspeccionaron visualmente; la captura tablet se generó y cuenta con medidas DOM, sin afirmar inspección visual exhaustiva independiente. Las alturas de las imágenes exceden el viewport por tratarse de capturas de página completa.

### Interacciones observadas

| Caso | Resultado |
|---|---|
| Marcador inicial | Ficha muestra TITULAR ARTIFICIAL 1 en las tres resoluciones |
| Lista de registro sin coordenadas | Ficha cambia a TITULAR ARTIFICIAL 3 |
| Filtro Aprobado | Una fila y un marcador |
| Búsqueda sin coincidencias | Cero filas y cero marcadores con explicación de selección vacía |
| TODOS/2018 | Ocho filas y cuatro marcadores |
| empty | Lista vacía conocida y mensaje de respuesta oficial vacía simulada |
| degraded | Lista desconocida e indisponibilidad diferenciada |
| error | Mensaje seguro de error, sin proyectos de sustitución |
| success del Dashboard | Ocho filas sin marcadores; coordenadas ausentes preservadas |
| coordinates | Ocho filas y cuatro marcadores restaurados |
| Enter sobre marcador enfocado | aria-pressed true y ficha correspondiente |
| Acercar | Cambia viewBox, sin consulta adicional |

No se capturaron eventos Runtime.exceptionThrown durante el recorrido. No hubo solicitudes externas ni rutas backend /api/*. Los módulos fuente /src/api/ se sirven localmente por Vite y no son peticiones al backend.

## 9. Fidelidad y adaptaciones visuales

Se conserva la estructura principal de Stitch: filtros superiores, panel espacial de gran tamaño y ficha lateral en escritorio, bordes suaves, superficies claras, identidad existente y acentos verdes/turquesa. En tablet y móvil los paneles se apilan, la lista sigue accesible y las ausencias permanecen explícitas.

La diferencia visual principal es deliberada: el prototipo mostraba una silueta estilizada de Perú y supuestos geoservicio, CRS, regiones, elevación e impacto económico. Se sustituyeron por una retícula ilustrativa sin mapa base y campos verificables. La composición se conserva; no se afirma equivalencia cartográfica ni réplica píxel a píxel.

No se detectó desbordamiento global o defecto bloqueante en los escenarios revisados. El área de detalle es extensa cuando se muestran todos los campos; en móvil requiere desplazamiento vertical. No se ha añadido salto automático al detalle ni navegación externa.

## 10. Limitaciones y pendientes

- El plano es ilustrativo: no hay mapa base, fronteras, capas oficiales o precisión geodésica certificada.
- Los marcadores SVG pueden solaparse; la lista permite acceder a cada fila. No se implementó clustering.
- Los 44 px medidos corresponden a controles HTML; no se certifica tamaño táctil mínimo de cada marcador SVG.
- Enter se comprobó en marcador; Espacio está implementado pero no se ejercitó en este recorrido. No existe auditoría completa WCAG o de lectores de pantalla.
- Se verificó la lógica del almacén con pruebas; no se realizó una prueba visual exhaustiva de transición Dashboard ↔ Explorador con todos los escenarios y cancelaciones.
- La sincronización bidireccional de filtros entre vistas no está implementada.
- La caché es exclusiva de mocks y por sesión en memoria; no debe convertirse sin diseño en una estrategia de datos reales o frescura productiva.
- No se probó rendimiento con miles de marcadores. Las cifras históricas de SENACE no se incorporaron como constantes ni se descargaron datasets.
- BigDecimal e int64 conservan las limitaciones JavaScript ya documentadas; no se promete aritmética decimal exacta.
- Sin backend real, CORS operativo o integración end-to-end comprobados en este lote.

## 11. Aislamiento y estado final

Vite y el Edge aislado de revisión se cerraron. No se inició Spring Boot, Maven o SQLite, no se consultó SENACE/BCRP, no se modificó Stitch y no se realizaron commits. No se declara eliminación de perfiles temporales, que no se ejecutó.

**El alcance aprobado del Lote 4 offline está implementado, compilado, probado y revisado localmente en las resoluciones indicadas.** Esto no cierra una futura integración cartográfica real ni certifica toda la aplicación.

Siguiente paso recomendado: revisar este reporte y preparar por separado el Lote 5 — Consultoras Ambientales, reutilizando la capa API y los ocho campos reales. La elección futura de biblioteca y proveedor cartográfico, o la sincronización bidireccional de vistas, debe tratarse como una tarea explícita independiente. No se avanzó automáticamente a los Lotes 5–7.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest tiene implementado el Explorador Geográfico en React + TypeScript, con modo demostración explícito y SVG offline sin tiles ni nuevas dependencias. Reutiliza createApiClient, adaptDashboard, createLatestRequest y createMockFetch. Incluye filtros sector/año/estado, búsqueda local, lista alternativa, marcadores, zoom y ficha sincronizada. Solo se representa un esquema ilustrativo de coordenadas, sin región, expediente, elevación, inversión, geoservicio o CRS certificado inventados.

Se añadieron seis fixtures geográficos separados y un almacén en memoria que comparte cargas y resultados por clave. El Explorador puede iniciar con la última selección del Dashboard; no existe sincronización bidireccional de sus controles. DashboardPage.tsx y backend no se modificaron. Los resultados sin coordenadas siguen sin ellas; no se rellenan silenciosamente.

Se corrigió un fallo inicial de anotación TypeScript en el nuevo almacén. La ejecución final del 07/11/2026 pasó typecheck, 64 pruebas y build. Edge local verificó tres resoluciones sin desbordamiento, selección, filtros, búsqueda, escenarios y Enter sobre marcador; no hubo excepciones monitorizadas ni tráfico backend/externo. Capturas en documentacion/04_Fase4_Frontend/evidencias/lote4/.

El siguiente prompt puede preparar el Lote 5 Consultoras con autorización propia. No repetir este lote ni dar por aprobados proveedor de mapas, instalaciones, tráfico externo, modificaciones backend o sincronización de filtros completa. La validación sigue siendo mock/offline, no end-to-end real.
