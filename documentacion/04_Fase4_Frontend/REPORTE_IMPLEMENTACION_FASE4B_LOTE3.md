# EcoInvest — Fase 4B, Lote 3: Dashboard Analítico

Fecha: 07/11/2026. Alcance: implementación frontend en modo demostración, validación estática, pruebas del modelo y build. No certifica integración con backend ni revisión visual en navegador.

## 1. Objetivo y estado inicial

Implementar la primera vista funcional del frontend React + TypeScript reutilizando el scaffold, AppShell, navegación, logos y estilos del Lote 1 y la capa API, adaptadores, coordinador de solicitudes y mocks del Lote 2.

Antes de este lote, las cuatro vistas eran estructurales. Solo Dashboard se conecta ahora a una interacción funcional con respuestas artificiales. Explorador, Consultoras y Monitor permanecen en preparación. El backend y su OpenAPI no se modificaron en este lote.

Referencias de continuidad:

- [Implementación Lote 1](REPORTE_IMPLEMENTACION_FASE4B_LOTE1.md).
- [Implementación Lote 2](REPORTE_IMPLEMENTACION_FASE4B_LOTE2.md).
- [Revisión Stitch](REPORTE_REVISION_STITCH_FASE4A.md).
- [Contrato fuente](../../backend/src/main/resources/openapi.yaml).

## 2. Referencia Stitch utilizada

Se recuperaron en modo lectura los metadatos, HTML y captura de la pantalla original. No se modificó el proyecto remoto ni se generaron diseños nuevos.

| Recurso | Identificación |
|---|---|
| Proyecto | Proyecto SOA - EcoInvest |
| Proyecto MCP | projects/3389709097732066354 |
| Pantalla | EcoInvest Perú - Dashboard Analítico (Claro / Oscuro) |
| ID de pantalla | a708110083bb4fa38ee8aa0935dcb1a9 |
| HTML | projects/3389709097732066354/files/3560949116595053470 |
| Captura | projects/3389709097732066354/files/3332757910231984049 |

La composición preserva shell existente, panel de filtros, tres tarjetas KPI, panel central amplio y tabla inferior. Se adoptaron superficies claras, bordes suaves y acentos verdes y turquesa. Los logos existentes se reutilizan mediante el shell; no se descargaron nuevamente. No se copió HTML completo como JSX ni se utilizó dangerouslySetInnerHTML, CDN o scripts del prototipo.

La captura y el HTML se inspeccionaron como referencia. La fidelidad del resultado renderizado todavía no se comprobó en navegador.

## 3. Archivos creados y modificados

### Creados

| Archivo | Responsabilidad |
|---|---|
| [DashboardPage.tsx](../../frontend/src/features/dashboard/DashboardPage.tsx) | Vista, filtros, estados, KPI, gráfico, tabla y metadatos |
| [DashboardPage.css](../../frontend/src/features/dashboard/DashboardPage.css) | Estilos propios y reglas responsive |
| [dashboardModel.ts](../../frontend/src/features/dashboard/dashboardModel.ts) | Carga mock, distribución por estado, búsqueda y paginación puras |
| [dashboard.test.mjs](../../frontend/tests/api/dashboard.test.mjs) | Pruebas automatizadas del modelo y su integración con mocks y adaptador |

### Modificados

| Archivo | Cambio delimitado |
|---|---|
| [App.tsx](../../frontend/src/app/App.tsx) | Sustituye únicamente la vista estructural Dashboard por DashboardPage |
| [fixtures.ts](../../frontend/src/mocks/fixtures.ts) | Añade variantes de demostración; conserva fixtures históricos |
| [tsconfig.api-tests.json](../../frontend/tsconfig.api-tests.json) | Incluye el modelo puro del Dashboard en la compilación de pruebas |
| [package.json](../../frontend/package.json) | Añade dashboard.test.mjs al comando test:api existente |

No se añadieron dependencias, librerías de gráficos ni herramientas de pruebas. No se reconstruyeron navegación, shell o recursos. No se modificaron backend, OpenAPI, SQLite, configuración de proveedores ni otras vistas.

## 4. Integración y adaptaciones funcionales

El flujo implementado es:

```text
DashboardPage
  → createLatestRequest
  → loadDashboardDemo
  → createApiClient(mode: mock)
  → createMockFetch
  → fixture artificial seleccionado
  → adaptDashboard
  → KPI / distribución / tabla / metadatos
```

Se reutilizan los mecanismos existentes sin una segunda capa de transporte o un fallback nuevo. La construcción del cliente de esta vista utiliza explícitamente mode: mock. No se consulta el backend ni se cambia automáticamente a datos artificiales ante un error HTTP real.

### Filtros

- Sector/actividad de demostración: Hidrocarburos, Minería y TODOS.
- Año de inicio: 2017 y 2018; selección inicial Hidrocarburos/2017.
- Los controles modifican una selección pendiente y el botón aplica la consulta artificial.
- Si los controles cambian después de una respuesta, se informa que los datos siguen correspondiendo a la selección aplicada previamente.
- FECHA_INICIO se presenta como año de inicio, nunca como aprobación.
- Las opciones delimitan escenarios visuales; no constituyen catálogo completo SENACE ni equivalencia automática entre ACTIVIDAD y BCRP.

### Tres KPI

1. Registros de proyectos filtrados.
2. Registros con estado Aprobado en la selección.
3. Indicador económico BCRP, con unidad, período, ámbito y código de serie.

Los conteos no se denominan proyectos únicos certificados. La indisponibilidad no se transforma en cero. En escenarios SENACE degradado/configuración ausente, el indicador BCRP permanece independiente y visible si está disponible. Las cifras económicas de los fixtures son artificiales, no observaciones oficiales.

### Gráfico central

La visualización original mensual y de correlación se reemplaza por distribución de registros por ESTADO, calculada únicamente sobre las filas utilizables de la selección. Se conserva el espacio central de visualización.

Se implementó con CSS, sin dependencia gráfica. Incluye alternativa accesible mediante tabla, etiquetas, conteos y leyenda. Un estado ausente se etiqueta como no disponible; no se inventa una categoría administrativa. Si la lista es desconocida, no se genera un agregado cero. Una lista conocida vacía se distingue expresamente.

No se muestran inversión, evolución mensual, correlación, tendencias trimestrales ni métricas SUNAT.

### Tabla

Columnas: titular, RUC del titular, proyecto, actividad, fecha de inicio y estado. Conserva RUC textual, incluidos ceros iniciales, y muestra ausencias como no disponibles.

La búsqueda es local, insensible a mayúsculas y acentos; examina campos textuales cargados. La paginación muestra tres filas por página y no realiza solicitudes adicionales. No deduplica ni fabrica expediente, región o campos ausentes. No se implementaron exportaciones o botones de detalle sin comportamiento.

## 5. Mocks, procedencia y estados

El aviso visible es **MODO DEMOSTRACIÓN — DATOS ARTIFICIALES**, acompañado de la indicación de que no existe conexión al backend.

Se añadieron 48 variantes: tres sectores × dos años × ocho escenarios. Los registros y valores están identificados como artificiales en el código y en la interfaz.

| Escenario | Tratamiento |
|---|---|
| Respuesta satisfactoria | Conteos, filas y distribución coherentes |
| Respuesta oficial vacía simulada | Cero conocido y lista vacía con metadatos del escenario |
| Filtro sin coincidencias | Lista vacía diferenciada de proveedor vacío |
| SENACE indisponible | Conteos desconocidos, indicador BCRP conservado |
| Configuración ausente | Ausencia explícita, sin registros de sustitución |
| Fechas no evaluables | Advertencias y conteos de calidad temporal |
| Opcionales ausentes | Ausencia preservada, sin convertirla en cero |
| Error HTTP artificial | Estado de error, sin resultado utilizable inventado |

Los estados loading, ready, empty, degraded y error se derivan del flujo existente; también se permite cancelar. El coordinador descarta respuestas obsoletas y se cancela al desmontar la vista. Los cambios de filtro no desencadenan consultas por cada renderizado.

Los metadatos se muestran independientemente de la primera fila: modo, HTTP simulado, consulta, filtrado, cobertura, cuerpo completo simulado, fechas no evaluables y motivo seguro. La cobertura permanece NO_VERIFICADA cuando la proporciona el fixture. Un cuerpo completo no acredita cobertura oficial completa.

## 6. Pruebas y comandos ejecutados

Ejecución autorizada desde `D:\Documentos\GitHub\Proyecto-SOA\frontend`, el 07/11/2026:

```powershell
npm run typecheck
npm run test:api
npm run build
```

Los comandos se encadenaron con comprobación de código de salida antes de continuar. El proceso terminó con código 0. No se instalaron dependencias ni se inició un servidor.

| Validación | Resultado observado |
|---|---|
| Typecheck app y configuración Node | Satisfactorio |
| Compilación de módulos para pruebas | Satisfactoria |
| Suite test:api actual | 54 pruebas, 54 aprobadas, 0 fallos, 0 canceladas, 0 omitidas |
| Build Vite 8.3.5 | Satisfactorio; 33 módulos transformados |

Las 54 pruebas corresponden a una ejecución conjunta actual: 39 pruebas existentes y 15 añadidas para Dashboard. No son una suma de ejecuciones históricas. El build vuelve a ejecutar el typecheck según el script existente.

Cobertura nueva: selección inicial, seis combinaciones sector/año, coherencia de conteos e indicador, vacíos distintos, degradación independiente de BCRP, fechas no evaluables, opcionales, distribución, ausencia de deduplicación, búsqueda, RUC textual, paginación, ausencia de fetch global, cancelación y descarte de respuestas obsoletas. También se comprueba que los fixtures históricos continúan disponibles.

El runtime Node 22.16.0 y npm 10.9.2 se habían verificado en las actividades previas; en esta ejecución no se repitieron los comandos de versión. Las versiones de dependencias se conservaron. La salida del build identifica Vite 8.3.5.

Artefactos producidos: módulos compilados en `frontend/.api-test-build/` y bundle en `frontend/dist/`. El build registró aproximadamente 255,70 kB de JavaScript (79,60 kB gzip) y 15,62 kB de CSS (3,86 kB gzip). No se editó manualmente ningún artefacto generado.

## 7. Fidelidad, responsive y accesibilidad

Implementado por código: paleta clara, tarjetas y paneles semejantes a Stitch, tabla horizontalmente desplazable, estados diferenciados, foco visible, labels, mensajes de estado, alternativa accesible del gráfico y reglas responsive a 1200, 900 y 560 px.

**Pendiente de comprobar en navegador:** composición final respecto a la captura, comportamiento en escritorio/tablet/móvil, navegación interactiva, teclado y lectores de pantalla. Las pruebas ejecutadas cubren lógica pura y capa API, no montan componentes React ni verifican el DOM o el CSS renderizado.

No se afirma fidelidad visual comprobada ni accesibilidad certificada por el éxito del build.

## 8. Limitaciones y riesgos

- Solo existen seis combinaciones de filtros de demostración; no representan la cobertura real de SENACE.
- Los mocks no validan conectividad, CORS, credenciales o respuestas operativas del backend.
- Los números JSON usan precisión JavaScript; no se implementó aritmética decimal económica exacta. El adaptador existente rechaza int64 inseguros y números no finitos.
- La gráfica describe exclusivamente filas disponibles, no el universo oficial.
- Las etiquetas API_DIRECTA de los fixtures describen escenarios contractuales simulados; el aviso visible y las fuentes indican su carácter artificial.
- El logo EcoInvest conserva el tamaño del recurso anterior; no se realizó optimización fuera de alcance.
- No se ejecutó backend, Maven, SQLite, SENACE, BCRP ni una prueba end-to-end.
- No se efectuaron commits ni modificaciones remotas de Stitch.

## 9. Estado final y siguiente paso

Dashboard implementado en modo demostración, con typecheck, pruebas del modelo/capa API y build satisfactorios. El cierre visual del lote permanece pendiente de una revisión autorizada en navegador local; no debe confundirse con integración productiva completa.

Siguiente paso recomendado: autorizar una revisión local con Vite y navegador para comprobar diseño, filtros, búsqueda, paginación, escenarios y responsive sin backend. Después, preparar por separado el Lote 4 — Explorador Geográfico. La biblioteca cartográfica, el proveedor de tiles y su tráfico requerirán decisión y autorización propias; no se han elegido ni instalado.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest ya dispone de Dashboard Analítico React + TypeScript en modo demostración explícito. Reutiliza el shell y los logos existentes, el cliente API, adaptDashboard, createLatestRequest y createMockFetch. Implementa filtros Hidrocarburos/Minería/TODOS para 2017/2018, tres KPI honestos, distribución por ESTADO, tabla con búsqueda y paginación y metadatos independientes de filas. No inventa inversión, correlación, series temporales o datos SUNAT.

La ejecución del 07/11/2026 completó typecheck, 54 pruebas automatizadas y build Vite sin fallos. Son pruebas de lógica y API simulada, no una revisión visual o end-to-end. Backend, OpenAPI y las otras tres pantallas permanecieron fuera del cambio.

La próxima tarea debe revisar visualmente el Dashboard con autorización separada para servidor/navegador local. Después puede planificarse el Explorador, sin dar por aprobados librería cartográfica, tiles, instalaciones o consultas externas. No repetir implementación del Dashboard ni atribuir a los mocks datos oficiales reales.
