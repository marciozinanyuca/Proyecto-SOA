# EcoInvest — Fase 4B, Lote 5: Consultoras Ambientales

Fecha: 07/11/2026.

**Estado: implementación y validación local en modo demostración completadas dentro del alcance autorizado.** No se conectó el backend ni se consultó SENACE. Los resultados son artificiales; no acreditan empresas, autorizaciones o vigencias reales.

## 1. Objetivo y estado inicial

Implementar Consultoras Ambientales reutilizando el shell y navegación existentes, la capa API, adaptadores y mocks aprobados. Dashboard y Explorador ya estaban implementados; Consultoras conservaba una vista estructural en preparación.

Referencias de continuidad:

- [Lote 1: scaffold y shell](REPORTE_IMPLEMENTACION_FASE4B_LOTE1.md).
- [Lote 2: API y adaptadores](REPORTE_IMPLEMENTACION_FASE4B_LOTE2.md).
- [Lote 3: Dashboard](REPORTE_IMPLEMENTACION_FASE4B_LOTE3.md).
- [Cierre visual del Dashboard](REPORTE_CIERRE_VISUAL_LOTE3.md).
- [Lote 4: Explorador offline](REPORTE_IMPLEMENTACION_FASE4B_LOTE4.md).
- [Conciliación Stitch](REPORTE_REVISION_STITCH_FASE4A.md).
- [OpenAPI fuente](../../backend/src/main/resources/openapi.yaml).

Se respetaron las autorizaciones por etapas: implementación, ejecución de typecheck/pruebas/build, revisión local con Vite y navegador, y creación de este reporte.

## 2. Referencia original de Stitch

| Recurso | Identificación |
|---|---|
| Proyecto | Proyecto SOA - EcoInvest |
| ID | projects/3389709097732066354 |
| Pantalla | EcoInvest Perú - Consultoras Ambientales |
| ID de pantalla | 00e4138a0a8e456c84a79ad7ff680db2 |
| HTML recuperado | projects/3389709097732066354/screens/00e4138a0a8e456c84a79ad7ff680db2/fileEntries/html |
| Captura recuperada | projects/3389709097732066354/screens/00e4138a0a8e456c84a79ad7ff680db2/fileEntries/screenshot |

Se recuperaron mediante MCP los metadatos, HTML y captura en modo lectura. La referencia contiene filtros superiores, tres tarjetas KPI y una tabla con búsqueda, detalle y paginación. También presenta campos y métricas no respaldados por el contrato y textos corruptos, que no se trasladaron como funcionalidad real.

No se modificó el proyecto Stitch ni se descargaron otra vez los logos. No se copió HTML completo como JSX ni se utilizaron scripts, CDN o bibliotecas UI del prototipo.

## 3. Conciliación del prototipo con el contrato

La operación disponible es `GET /api/consultoras`: array raíz con ocho campos opcionales, metadatos en headers y respuestas 200, 502 y 503.

| Elemento original | Implementación conciliada |
|---|---|
| Consultoras registradas / entidades | Filas recibidas de una respuesta utilizable, sin deduplicar |
| Vigentes / habilitadas | Filas que coinciden con filtros locales; no se deriva estado administrativo |
| Autorizadas para Hidrocarburos | Filas filtradas con resolución informada; no acredita autorización |
| Búsqueda por RUC | Búsqueda por razón social y los otros siete campos existentes |
| Nivel y estado | Filtros por actividad, subsector y tipo de solicitud |
| RUC / registro / nivel / estado en tabla | Ocho campos reales del modelo público |
| Ver detalle | Panel con los ocho textos originales y sus ausencias |
| CSV/JSON | No implementados; no se añadieron botones sin comportamiento |
| RNCA/SUNAT, vigencia o habilitación | No se afirma validación administrativa ni integración adicional |

Se conservan composición general, superficies claras, paleta verde/turquesa, tarjetas, jerarquía visual, bordes, tabla y navegación del shell. La tabla es más ancha que la original porque contiene los ocho campos: se permite desplazamiento horizontal local, incluso en escritorio, sin ensanchar el documento.

## 4. Archivos creados y modificados

### Creados

| Archivo | Responsabilidad |
|---|---|
| [ConsultorasPage.tsx](../../frontend/src/features/consultoras/ConsultorasPage.tsx) | Vista, filtros, escenarios, KPI, tabla, detalle y metadatos |
| [ConsultorasPage.css](../../frontend/src/features/consultoras/ConsultorasPage.css) | Estilos propios, scroll local, responsive y foco |
| [consultorasModel.ts](../../frontend/src/features/consultoras/consultorasModel.ts) | Carga mock, campos, filtros, conteos, paginación y mensajes |
| [consultoras.test.mjs](../../frontend/tests/api/consultoras.test.mjs) | Dieciséis pruebas nuevas de modelo y flujo artificial |

### Modificados

| Archivo | Cambio delimitado |
|---|---|
| [App.tsx](../../frontend/src/app/App.tsx) | Activa la ruta existente de Consultoras con ConsultorasPage |
| [fixtures.ts](../../frontend/src/mocks/fixtures.ts) | Añade cinco escenarios artificiales sin reemplazar los anteriores |
| [tsconfig.api-tests.json](../../frontend/tsconfig.api-tests.json) | Incluye consultorasModel.ts en compilación de pruebas |
| [package.json](../../frontend/package.json) | Añade consultoras.test.mjs a test:api |

No se modificaron Dashboard, Explorador, AppShell, navegación, logos, estilos globales, backend, OpenAPI o SQLite. No se cambiaron versiones, dependencias, cliente API o adaptadores existentes. No se realizaron commits.

## 5. Modelo y flujo de datos

```text
ConsultorasPage
  → createLatestRequest
  → loadConsultants
  → createApiClient(mode: mock)
  → createMockFetch
  → fixture con array y headers simulados
  → adaptConsultoras
  → filtros locales → KPI / página de tabla / detalle
```

Se reutilizaron los cuatro componentes solicitados sin crear una segunda capa API. No se utilizó modo HTTP ni fallback implícito a mocks.

Campos disponibles, conservados en tabla y detalle:

1. razonSocial.
2. actividad.
3. subsector.
4. tipoSolicitud.
5. resolucion.
6. fechaRd.
7. inicioVigenciaRd.
8. finVigenciaRd.

Las fechas siguen siendo strings originales. No se calculan antigüedad, actividad, vencimiento o habilitación. Los strings ausentes o vacíos se muestran como No disponible; los valores originales no vacíos no se reescriben. No se inventan RUC, registro, ID empresarial, nivel EIA o estado.

## 6. Tres KPI y reglas de ausencia

| KPI | Fórmula |
|---|---|
| Filas recibidas | Longitud del array utilizable recibido |
| Filas filtradas | Longitud de las coincidencias de búsqueda y filtros |
| Con resolución informada | Filas filtradas donde resolucion es string y trim() no está vacío |

Una resolución informada solo acredita presencia de texto, no autorización o vigencia. No se cuentan empresas únicas ni se eliminan duplicados.

Los conteos solo se publican cuando existen filas no vacías utilizables o un vacío oficial confirmado por los metadatos del escenario. Un array vacío sin headers suficientes deja los KPI como No disponible. En un resultado degradado con filas no vacías se permiten conteos de filas recibidas, acompañados de procedencia no confirmada; no se presentan como datos oficiales.

HTTP 502/503 o formato inválido dejan los tres KPI desconocidos. Un filtro local sin coincidencias sobre una respuesta conocida sí puede producir cero, sin alterar el total de filas recibidas.

## 7. Búsqueda, filtros, tabla y detalle

- Búsqueda local sobre los ocho campos textuales, insensible a mayúsculas y acentos.
- Filtros exactos por actividad, subsector y tipoSolicitud; sus opciones se obtienen de los valores recibidos.
- Las opciones se ordenan para presentación; el orden de las filas no cambia.
- Los filtros se combinan y reinician la página y la selección de detalle.
- No se hacen solicitudes al buscar, filtrar, paginar o abrir detalle.
- Se renderizan como máximo diez filas de tabla por página; no existe paginación backend nueva.
- Se conservan filas repetidas y campos opcionales. Las claves técnicas son índices de la respuesta, no identificadores empresariales oficiales.
- La acción Ver detalle muestra la fila exacta, mueve el foco a su encabezado y permite cerrar devolviendo el foco al botón de origen cuando permanece montado.
- La tabla tiene región enfocáble y scroll horizontal propio. Los contenedores grid usan mínimos cero para evitar el defecto móvil observado en el Dashboard histórico.

La paginación limita nodos DOM de la tabla, no el tamaño de descarga ni el costo de filtrar el array en memoria. No se incorporó una biblioteca de tablas, virtualización o gestor global nuevo.

## 8. Fixtures y estados

Los cinco fixtures añadidos son:

- consultorasDemoSuccess: catorce filas artificiales, incluida una repetición y una fila con campos ausentes.
- consultorasDemoNoHeaders: mismas filas sin headers SENACE.
- consultorasDemoEmptyNoHeaders: array vacío sin headers SENACE.
- consultorasDemoInvalid: objeto en lugar de array.
- consultorasDemoOptional: dos filas con opcionales ausentes y resolución vacía/blanca.

Se reutilizan consultantsEmpty, consultants502 y consultants503. Ningún nombre, resolución o fixture nuevo procede de una fila personal real. Catorce es un tamaño de fixture, no una constante del negocio o cobertura oficial. Tampoco se usa 15.015 como total fijo.

| Escenario | Estado y presentación |
|---|---|
| HTTP 200 con filas y metadatos | ready, conteos de filas y cobertura NO_VERIFICADA |
| HTTP 200 con [] oficial simulado | empty, cero conocido |
| HTTP 502 con [] | error de formato; conteos desconocidos |
| HTTP 503 con [] | error de disponibilidad/configuración; conteos desconocidos |
| HTTP 200 con filas sin headers | degraded, filas visibles con procedencia no confirmada |
| HTTP 200 con [] sin headers | degraded, no equivale a vacío oficial |
| HTTP 200 con estructura incorrecta | error; no filas de sustitución |
| Opcionales ausentes | ready con ausencias honestas |

Se implementan además loading, cancelación y descarte de respuestas obsoletas. El selector de escenario requiere aplicar la consulta; se informa si el escenario editado aún no corresponde a los datos mostrados.

La vista muestra **MODO DEMOSTRACIÓN — DATOS ARTIFICIALES** y metadatos de HTTP simulado, origen, estado, cobertura, cuerpo completo y motivo seguro. Una respuesta completa no acredita cobertura oficial completa. No se publican rawText, cuerpos externos o credenciales.

## 9. Pruebas y build ejecutados

Fecha: 07/11/2026. Directorio: `D:\Documentos\GitHub\Proyecto-SOA\frontend`.

```powershell
npm run typecheck
npm run test:api
npm run build
```

Los comandos se ejecutaron de forma secuencial comprobando códigos de salida. La secuencia terminó con código 0, sin fallos que requirieran correcciones en esta ejecución.

| Validación | Resultado |
|---|---|
| Typecheck | Satisfactorio |
| test:api actual | 80 pruebas aprobadas, 0 fallos, 0 canceladas, 0 omitidas |
| Build | Satisfactorio; Vite 8.3.5, 41 módulos transformados |

Las 80 pruebas pertenecen a una sola ejecución actual: las 64 existentes y dieciséis nuevas. No es suma de suites históricas. El build generó aproximadamente 28,70 kB CSS y 286,27 kB JavaScript; produjo artefactos locales en dist/ y .api-test-build/, sin edición manual.

Las pruebas nuevas verifican ocho campos, KPI, duplicados, filtros combinados, búsqueda, orden, paginación, carga artificial de 20.000 filas con páginas de diez, vacío oficial, HTTP 502/503, headers ausentes, estructura inválida, fechas y opcionales, detalle, ausencia de fetch global, cancelación y resultados obsoletos.

La prueba de 20.000 filas comprueba lógica y tamaño de página; no es un benchmark de memoria, fluidez React o rendimiento con dataset real.

Se conservaron React 19.3.1, TypeScript 7.0.2 y Vite 8.3.5. Node 22.16.0 y npm 10.9.2 son antecedentes verificados del entorno; no se repitieron comandos de versión en esta ejecución. No se ejecutó Maven ni se instalaron dependencias.

## 10. Validación visual y responsive

Con autorización independiente se inició Vite local mediante:

```powershell
npm run dev -- --port=5173 --strictPort
```

La salida efectiva fue `vite --host 127.0.0.1`, disponible en `http://127.0.0.1:5173/`. Se abrió `/#/consultoras` en Edge headless con perfil temporal aislado, usando CDP y WebSocket nativo de Node.

Se esperó a observar diez filas antes de capturar. Las capturas son de página completa, con viewport de los tamaños indicados. Se emularon dimensiones con deviceScaleFactor 1 y mobile:false; no se certifican dispositivos físicos o gestos táctiles.

| Viewport | clientWidth / scrollWidth global | Filas DOM | Ancho cliente / desplazable de tabla | Altura mínima de controles muestreados |
|---|---:|---:|---:|---:|
| 1440 × 900 | 1425 / 1425 px | 10 | 1087 / 1296 px | 44 px |
| 768 × 1024 | 753 / 753 px | 10 | 711 / 1296 px | 44 px |
| 375 × 812 | 360 / 360 px | 10 | 326 / 1296 px | 44 px |

No hubo desbordamiento horizontal global. Se comprobó desplazamiento de 100 px dentro de la tabla en cada resolución. Los 15 px de diferencia entre viewport y clientWidth corresponden al espacio observado de la barra vertical.

Evidencias:

- [Escritorio](evidencias/lote5/escritorio.png).
- [Tablet](evidencias/lote5/tablet.png).
- [Móvil](evidencias/lote5/movil.png).
- [Resultados DOM e interacciones](evidencias/lote5/resultados.json).

Se inspeccionaron visualmente las capturas de escritorio y móvil. La captura tablet se generó y tiene medidas DOM; no se afirma una inspección visual independiente exhaustiva de esa imagen.

## 11. Interacciones observadas en navegador

| Comprobación | Resultado |
|---|---|
| Inicio | KPI 14 recibidas, 14 filtradas y 9 con resolución; diez filas renderizadas |
| Abrir detalle | Ocho campos y foco en consultants-detail-title |
| Cerrar detalle | Panel de selección vuelve a su estado informativo; se solicita foco al botón de origen por código |
| Filtro actividad | KPI 14 / 7 / 3 y siete filas |
| Búsqueda sin coincidencias | KPI 14 / 0 / 0, sin filas de tabla |
| Limpiar y siguiente página | Filas 11–14, cuatro filas DOM |
| Array oficial vacío simulado | KPI 0 / 0 / 0 y mensaje específico |
| HTTP 502 y 503 | Los tres KPI No disponible, sin conteo oficial cero |
| Sin headers con filas | KPI de filas conservados y mensaje de procedencia no confirmada |
| Sin headers con [] | KPI desconocidos y aviso de que no acredita vacío oficial |
| Estructura inválida | Mensaje de error y KPI desconocidos |
| Opcionales | KPI 2 / 2 / 0 y ausencias explícitas |
| Navegación | Dashboard, Explorador y regreso a Consultoras disponibles; foco final en contenido |

El registro de cerrar detalle muestra activeElement sin ID, por lo que no se presenta como verificación concluyente de identidad del botón enfocado. No se realizó recorrido completo por teclado o lector de pantalla. Los filtros subsector y tipo de solicitud se cubrieron en pruebas de modelo; el recorrido visual ejercitó actividad y búsqueda.

No se registraron eventos Runtime.exceptionThrown. No hubo solicitudes externas ni rutas /api/* al backend; los módulos fuente /src/api/ servidos por Vite no se clasificaron como tráfico backend.

## 12. Fidelidad, limitaciones y riesgos

La estructura conserva filtros, tres KPI, directorio, detalle, paginación y colores del prototipo. El contenido se adapta al contrato y elimina afirmaciones no respaldadas sobre RUC, nivel, empresas únicas o vigencia. La tabla de ocho campos necesita scroll horizontal, intencional y confinado. El detalle permite leer los campos sin depender exclusivamente de desplazarse por la tabla.

Limitaciones:

- Todo el acceso de esta vista sigue siendo mock explícito; no valida backend real, CORS operativo, credenciales o datos SENACE actuales.
- La paginación limita renderizado, pero los datos completos se mantienen y filtran en memoria.
- Las fechas no se interpretan para vigencia; no existe validación administrativa adicional.
- No se implementaron exportaciones ni deduplicación.
- Los índices técnicos cambian si cambia la respuesta y no deben tratarse como identificadores oficiales persistentes.
- No se certifica réplica píxel a píxel de Stitch, conformidad WCAG completa, todos los navegadores, zoom o dispositivos físicos.
- Cancelación y descarte de respuestas obsoletas se comprobaron en pruebas de modelo; no se ejercitaron exhaustivamente con teclado en navegador.

## 13. Aislamiento y estado final

Vite y Edge aislado se cerraron al finalizar. No se inició Spring Boot, no se accedió a SQLite, no se consultó SENACE/BCRP y no se modificó Stitch. No se declara eliminación de perfiles temporales, que no se ejecutó.

**Lote 5 completado dentro de su alcance de implementación y revisión local con datos artificiales.** No equivale a integración end-to-end ni a certificación de disponibilidad de SENACE.

Siguiente paso recomendado: revisar este reporte y preparar por separado el Lote 6 — Monitor de Salud SOA, basándose en métricas reales del contrato, sin inventar telemetría, disponibilidad externa, historial o respaldo SQLite. No se inició ese lote ni el Lote 7.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest ya tiene Consultoras Ambientales funcional en modo demostración. Reutiliza createApiClient, adaptConsultoras, createLatestRequest y createMockFetch; no añadió dependencias o una nueva capa API. La vista expone los ocho campos contractuales, búsqueda y filtros locales por actividad/subsector/tipo de solicitud, tabla de diez filas por página y detalle. Conserva orden, duplicados y fechas originales. No inventa RUC, registro, nivel, ID oficial, estado activo o vigencia.

Los KPI describen filas recibidas, filtradas y filtradas con resolución informada. HTTP 502/503 y formato inválido dejan valores desconocidos. Un array vacío sin headers no acredita vacío oficial; filas sin procedencia se muestran como degradadas. Los fixtures nuevos son artificiales y separados de los históricos.

El 07/11/2026 pasaron typecheck, 80 pruebas actuales y build. La revisión Edge/Vite local comprobó tres resoluciones sin desbordamiento global, scroll local de tabla, diez filas DOM como máximo, detalle, búsqueda, paginación, errores y headers ausentes. No hubo excepciones monitorizadas ni tráfico backend/proveedores. Capturas en documentacion/04_Fase4_Frontend/evidencias/lote5/.

No se modificaron Dashboard, Explorador o backend. La integración real y auditoría completa de accesibilidad siguen fuera de alcance. El siguiente prompt puede diseñar el Lote 6 Monitor, con sus autorizaciones propias y sin repetir esta implementación ni iniciar integración real automáticamente.
