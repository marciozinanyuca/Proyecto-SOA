# EcoInvest — Fase 4B, Lote 6: Monitor de Salud SOA

Fecha: 07/11/2026.

**Estado: implementación, pruebas y revisión local del Monitor en modo demostración completadas dentro del alcance autorizado.** No se conectó Spring Boot ni se consultaron proveedores. No constituye una certificación end-to-end o de disponibilidad real de SENACE/BCRP.

## 1. Objetivo y estado inicial

Implementar la cuarta vista funcional del frontend conservando los Lotes 1–5. Al comenzar, Dashboard, Explorador offline y Consultoras estaban implementados con mocks; Monitor conservaba la vista estructural. Ya existían createApiClient, adaptMonitor, createLatestRequest, createMockFetch y fixtures de circuitos.

Referencias:

- [Conciliación Stitch Fase 4A](REPORTE_REVISION_STITCH_FASE4A.md).
- [Lote 2: capa API](REPORTE_IMPLEMENTACION_FASE4B_LOTE2.md).
- [Lote 5: Consultoras](REPORTE_IMPLEMENTACION_FASE4B_LOTE5.md).
- [Cierre backend 3C.3](../03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md).
- [OpenAPI fuente](../../backend/src/main/resources/openapi.yaml).

Se autorizaron por separado implementación, typecheck/pruebas/build, revisión local con Vite/navegador y este reporte. No se inició el Lote 7.

## 2. Referencia Stitch recuperada

| Recurso | Identificación |
|---|---|
| Proyecto | Proyecto SOA - EcoInvest |
| ID | projects/3389709097732066354 |
| Pantalla | EcoInvest Perú - Monitor de Salud SOA |
| ID pantalla | fd012dd20dc14515bf7f90eb05b45000 |
| HTML | projects/3389709097732066354/screens/fd012dd20dc14515bf7f90eb05b45000/fileEntries/html |
| Captura | projects/3389709097732066354/screens/fd012dd20dc14515bf7f90eb05b45000/fileEntries/screenshot |

Se recuperaron metadatos, HTML y captura mediante MCP en modo lectura. Se observó toolbar superior, dos tarjetas de servicio con estado y métricas, y panel inferior de historial/console ficticios. Se conservaron la distribución, identidad del shell, paleta clara verde/turquesa/azul, tarjetas y jerarquía.

No se modificó Stitch, no se descargaron logos nuevamente y no se copiaron HTML completo, CDN o scripts del prototipo como implementación React.

## 3. Contrato y campos efectivamente utilizados

Operación: `GET /api/estado-servicios`. El contrato declara respuesta 200 con objetos senace y bcrp y describe las métricas, pero no tipa exhaustivamente sus propiedades internas. La inspección del controlador confirmó los siguientes nombres; los tipos/adaptador frontend existentes los validan en ejecución:

| Campo | Tipo utilizado | Interpretación |
|---|---|---|
| servicio | string opcional | Nombre informado del servicio |
| estado | string opcional | Estado del circuito, sin inferir disponibilidad externa |
| tasaFallosPorcentaje | number finito opcional | Tasa informada; −1 se presenta No calculada |
| llamadasFallidas | entero seguro no negativo opcional | Contador de llamadas lógicas fallidas |
| llamadasExitosas | entero seguro no negativo opcional | Contador de llamadas lógicas exitosas |
| respaldoLocal | NO_IMPLEMENTADO opcional | No existe respaldo operativo implementado |
| alcanceMetricas | LLAMADAS_LOGICAS opcional | Alcance interno; no conteo certificado de HTTP externo |
| disponibilidadExterna | NO_VERIFICADA opcional | No se realizó comprobación directa del proveedor |

El controlador obtiene métricas de CircuitBreaker.Metrics; no se añadieron nuevos campos o endpoints. El Monitor no realiza llamadas directas a SENACE/BCRP.

## 4. Conciliación del prototipo

| Elemento Stitch | Adaptación implementada |
|---|---|
| CLOSED (Operativo) | CLOSED / Circuito cerrado, con advertencia de que no certifica proveedor operativo |
| Latencia promedio | No se muestra una cifra inexistente |
| Solicitudes bloqueadas | Sustituido por llamadas exitosas, campo disponible |
| Tasa de fallo | Tasa real del escenario; −1 No calculada, ausencia No disponible |
| Fallback SQLite/caché | NO_IMPLEMENTADO, sin promesa de recuperación |
| Historial reciente | Snapshot actual y explicación del alcance, sin persistir eventos |
| Consola de logs | Panel informativo; no se crea API de logs ni buffer ficticio |
| Simular fallo BCRP | No se implementa el control inexistente |
| Actualizar telemetría | Actualización manual explícita de la fuente mock |
| Última evaluación ESB | Hora local del cliente de la última consulta completada, sin atribuir timestamp al proveedor |

El espacio visual de los paneles inferiores se conserva para una tabla del snapshot y cuatro explicaciones: llamadas lógicas, disponibilidad no verificada, ausencia de telemetría adicional y respaldo no implementado.

## 5. Archivos creados y modificados

### Creados

| Archivo | Responsabilidad |
|---|---|
| [MonitorPage.tsx](../../frontend/src/features/monitor/MonitorPage.tsx) | Toolbar, actualización, tarjetas, snapshot y límites |
| [MonitorPage.css](../../frontend/src/features/monitor/MonitorPage.css) | Estilos propios, responsive, foco y scroll local |
| [monitorModel.ts](../../frontend/src/features/monitor/monitorModel.ts) | Carga artificial, etiquetas, formatos y selección segura de métricas |
| [monitor.test.mjs](../../frontend/tests/api/monitor.test.mjs) | Dieciocho pruebas nuevas de lógica e integración mock |

### Modificados

| Archivo | Cambio delimitado |
|---|---|
| [App.tsx](../../frontend/src/app/App.tsx) | Activa la ruta existente Monitor |
| [fixtures.ts](../../frontend/src/mocks/fixtures.ts) | Añade cinco escenarios artificiales conservando los históricos |
| [tsconfig.api-tests.json](../../frontend/tsconfig.api-tests.json) | Incluye monitorModel.ts en compilación de pruebas |
| [package.json](../../frontend/package.json) | Añade monitor.test.mjs al script test:api |

No se modificaron Dashboard, Explorador, Consultoras, AppShell, navegación, estilos globales, backend, OpenAPI o SQLite. No se cambiaron versiones o dependencias. No se implementó persistencia/caché de telemetría ni transporte nuevo.

## 6. Componentes y funcionamiento

```text
MonitorPage
  → createLatestRequest
  → loadMonitor
  → createApiClient(mode: mock)
  → createMockFetch
  → fixture explícito
  → adaptMonitor
  → tarjetas / snapshot actual / explicación de límites
```

La vista realiza una carga inicial y cargas manuales. No contiene polling, intervalos de actualización o consultas por renderizado. El montaje doble de desarrollo de React StrictMode puede iniciar y cancelar una carga inicial adicional; no implica polling ni acceso externo.

El selector de escenarios solo cambia la selección pendiente; el botón Actualizar aplica el escenario. Si los controles no coinciden con la respuesta mostrada, se informa qué escenario sigue aplicado. Solicitudes nuevas cancelan las anteriores y se descartan respuestas obsoletas mediante el coordinador existente. Al desmontarse la vista se cancela su operación.

La hora se obtiene con Date.now() al completar la carga del cliente y se presenta como hora local del cliente. Se muestra también al completar una respuesta de error, sin afirmar actualización de métricas válidas. Durante loading o cancelación no se publican métricas o timestamp anteriores como resultado nuevo.

El aviso permanente es **MODO DEMOSTRACIÓN — DATOS ARTIFICIALES**, con indicación de que no hay backend ni consultas a proveedores.

## 7. Estados y ausencia

| Estado de circuito | Presentación |
|---|---|
| CLOSED | Circuito cerrado; no proveedor operativo certificado |
| OPEN | Circuito abierto; no implica respaldo local disponible |
| HALF_OPEN | Recuperación en evaluación; no recuperación externa definitiva |
| DISABLED | Circuito deshabilitado; no informa salud externa |
| Ausente | No disponible |
| String desconocido | Valor original con interpretación local no disponible, estilo neutral |

No se añadieron fixtures o significados especulativos para otros estados no utilizados en el código inspeccionado. El adaptador actual acepta estado como string; la vista evita interpretaciones inventadas.

### Valores numéricos

- Tasa −1: No calculada, sin convertirla a 0 %.
- Tasa ausente, no finita o fuera de [0, 100], salvo el sentinel −1: No disponible.
- Contadores ausentes o inválidos: No disponible.
- Cero explícito válido: 0, diferenciado de ausencia.
- No se calcula una tasa alternativa a partir de contadores.

### Estados de consulta

Se respetó adaptMonitor sin modificarlo: puede devolver ready, degraded o error. Un objeto vacío se clasifica degraded, no se inventa un estado empty ni un vacío oficial de métricas. Un resultado ready puede tener contadores opcionales ausentes, que permanecen No disponible.

Además se presentan loading y cancelación en la vista. Un error no genera métricas sustitutas, no recupera un snapshot anterior como si fuese actual y no activa automáticamente otro modo de datos.

## 8. Fixtures usados

Se reutilizan:

- monitor: SENACE CLOSED y BCRP OPEN, con llamadas y tasas artificiales.
- monitorHalfOpen: SENACE HALF_OPEN y BCRP DISABLED.

Se añaden:

- monitorDemoPartial: solo SENACE y metadatos incompletos.
- monitorDemoEmpty: objeto sin métricas.
- monitorDemoOptional: ambos circuitos con contadores/tasa ausentes.
- monitorDemoError: HTTP 503 artificial.
- monitorDemoInvalid: array en lugar de objeto.

Estos fixtures describen escenarios contractuales, no observaciones de circuitos productivos. Cambiar de escenario no simula una avería real ni modifica Resilience4j. No se incluyeron credenciales, datos personales, URLs autenticadas o rawText en UI/reportes.

## 9. Pruebas y build ejecutados

Fecha: 07/11/2026. Directorio: `D:\Documentos\GitHub\Proyecto-SOA\frontend`.

```powershell
npm run typecheck
npm run test:api
npm run build
```

La secuencia comprobó códigos de salida y terminó con código 0. No hubo fallos de compilación/pruebas que requirieran correcciones en esta ejecución.

| Validación | Resultado |
|---|---|
| Typecheck | Satisfactorio |
| test:api actual | 98 pruebas aprobadas, 0 fallos, 0 canceladas, 0 omitidas |
| Build | Satisfactorio, Vite 8.3.5, 45 módulos transformados |

Las 98 pruebas pertenecen a una sola suite actual: 80 existentes y dieciocho nuevas. No es suma de suites históricas. El build produjo aproximadamente 35,15 kB CSS y 299,23 kB JavaScript. Se generaron artefactos locales en dist/ y .api-test-build/, sin edición manual.

Las pruebas nuevas cubren cuatro estados conocidos y desconocidos, tasas/contadores opcionales, sentinel −1, ausencia de métricas ficticias, NO_VERIFICADA/NO_IMPLEMENTADO/LLAMADAS_LOGICAS, snapshots incompletos, errores, carga manual, timestamp del cliente, cancelación, respuesta obsoleta y ausencia de fetch global.

React 19.3.1, TypeScript 7.0.2 y Vite 8.3.5 se conservaron. Node 22.16.0 y npm 10.9.2 son antecedentes del entorno previamente verificado; no se repitieron los comandos de versión en esta ejecución. No se instaló software ni se ejecutó Maven.

## 10. Revisión visual local

Con autorización independiente se inició Vite desde frontend/:

```powershell
npm run dev -- --port=5173 --strictPort
```

La salida efectiva fue `vite --host 127.0.0.1`; se utilizó `http://127.0.0.1:5173/#/monitor`. La revisión se realizó con Edge headless, perfil temporal aislado y CDP mediante WebSocket nativo de Node, sin acceder al perfil personal del usuario.

Se esperó al estado CLOSED inicial antes de capturar. Las imágenes son de página completa con los viewports indicados y por eso tienen mayor altura que el viewport. La emulación usó deviceScaleFactor 1 y mobile:false: no certifica un dispositivo físico o gestos táctiles.

| Viewport | clientWidth / scrollWidth global | Ancho cliente / desplazable de tabla | Scroll local observado | Altura mínima de controles muestreados |
|---|---:|---:|---:|---:|
| 1440 × 900 | 1425 / 1425 px | 1087 / 1087 px | 0 px, cabe completa | 44 px |
| 768 × 1024 | 753 / 753 px | 711 / 768 px | 57 px | 44 px |
| 375 × 812 | 360 / 360 px | 326 / 768 px | 100 px | 44 px |

Sin desbordamiento horizontal global en las tres medidas. La diferencia de 15 px respecto al viewport corresponde al espacio observado de la barra vertical. En tablet/móvil la tabla se desplaza dentro de su contenedor.

Evidencias:

- [Escritorio](evidencias/lote6/escritorio.png).
- [Tablet](evidencias/lote6/tablet.png).
- [Móvil](evidencias/lote6/movil.png).
- [Resultados de la revisión](evidencias/lote6/resultados.json).
- [Comprobación adicional de cancelación](evidencias/lote6/cancelacion.json).

Se inspeccionaron visualmente las capturas de escritorio y móvil. La captura tablet se generó y cuenta con medidas DOM; no se afirma una inspección visual independiente exhaustiva.

## 11. Interacciones y resultados

| Caso | Resultado observado |
|---|---|
| Inicio | CLOSED/OPEN; SENACE 0, 0, No calculada; BCRP 5, 0, 100 %, todo artificial |
| Actualización manual | Hora local pasó de 10:40:42 a 10:40:45, sin atribuir timestamp al proveedor |
| HALF_OPEN / DISABLED | Etiquetas conservadas y diferenciadas |
| Snapshot parcial | SENACE conocido parcialmente, BCRP No disponible; consulta degradada |
| Objeto sin métricas | Ambos servicios No disponible y mensaje de snapshot incompleto |
| Contadores opcionales | Estados conocidos, números No disponible |
| HTTP 503 / estructura inválida | Métricas No disponible y error seguro |
| Volver al fixture inicial | Recupera el snapshot artificial original |
| Teclado básico | Tab alcanzó Saltar al contenido con outline sólido |
| Navegación | Consultoras y retorno a Monitor disponibles; foco final en contenido |

### Cancelación: limitación inicial y comprobación posterior

El primer intento pulsó Actualizar y buscó Cancelar dentro de la misma evaluación DOM antes de que React renderizase el botón. El bloque cancel de resultados.json **no demuestra cancelación**: conservó un snapshot completado.

Se repitió la interacción en evaluaciones separadas. La evidencia cancelacion.json registró clicked:true, el mensaje «Consulta cancelada. No se publican resultados de la operación cancelada.» y ambos estados No disponible. Ese segundo resultado es la comprobación válida, sin borrar el antecedente.

No se registraron eventos Runtime.exceptionThrown durante el recorrido principal. No hubo solicitudes externas ni rutas /api/* al backend. Los módulos /src/api/ servidos por Vite no son peticiones backend.

## 12. Fidelidad y limitaciones

La composición conserva toolbar, dos tarjetas laterales en escritorio, colores de circuitos, métricas y panel inferior amplio de Stitch. En tablet/móvil se apilan las tarjetas; el snapshot dispone de scroll local. Los textos corruptos y afirmaciones ficticias del prototipo no se reprodujeron.

Las diferencias son deliberadas: no hay latencia, solicitudes bloqueadas, logs, historial persistente, simulador BCRP, respaldo SQLite operativo o afirmación de disponibilidad externa. No se reemplaza su ausencia con números artificiales presentados como reales.

Límites:

- La vista sigue en mock; no verifica Spring Boot, CORS, arranque, SQLite o proveedores reales.
- No se certifica fidelidad píxel a píxel, todos los navegadores, zoom o dispositivos físicos.
- Teclado se revisó de forma básica, sin auditoría WCAG o lector de pantalla completos.
- Las transiciones de fixtures no son pruebas de AOP/Resilience4j real; las validaciones backend históricas siguen siendo evidencias separadas.
- No se mantiene una serie histórica, caché o persistencia de telemetría. Solo se muestra el snapshot actual en memoria React.
- No se interpreta un objeto vacío como estado empty porque el adaptador existente lo clasifica degraded.
- Los objetos internos del contrato OpenAPI están descritos, pero no totalmente tipados como schemas de métricas: se conservó el adaptador existente y la compatibilidad con el controlador.

## 13. Aislamiento y estado final

Vite y el Edge aislado se cerraron al finalizar. No se inició Spring Boot, no se accedió a SQLite, no se consultó SENACE/BCRP y no se modificó Stitch. No se realizaron commits. No se declara eliminación de perfiles temporales, que no se ejecutó.

**Lote 6 completado dentro del alcance de implementación y revisión local con mocks.** Las cuatro vistas frontend ya tienen implementación funcional de demostración; esto no equivale a integración real de toda la aplicación.

## 14. Requisitos para la integración real posterior

El Lote 7 requiere un encargo y autorizaciones independientes. Antes de conectar las pantallas:

1. Revisar el cierre backend y preparar un arranque que no modifique SQLite accidentalmente por JPA/ddl-auto.
2. Autorizar explícitamente arranque backend y tráfico local, y presupuestos de proveedores cuando corresponda.
3. Diseñar la selección explícita HTTP/mock por configuración pública; no reutilizar credenciales SENACE en frontend o variables VITE_*.
4. Conectar las vistas al cliente existente sin fallback implícito a mocks, preservando status, headers, ausencia y degradación.
5. Verificar CORS, headers de Consultoras y compatibilidad de las cuatro rutas con evidencia real.
6. Separar el Monitor interno de las consultas a proveedores: obtener estado-servicios no debe añadir health checks externos.
7. Tratar la futura cartografía real y la sincronización completa Dashboard/Explorador como decisiones explícitas todavía pendientes.

No se ejecutó ninguno de estos pasos por asociación con el Lote 6.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest tiene implementadas las cuatro vistas React + TypeScript en modo demostración. El Lote 6 añadió MonitorPage, estilos, modelo puro y dieciocho pruebas, y modificó únicamente App.tsx, fixtures y configuración/comando de pruebas. Reutiliza createApiClient, adaptMonitor, createLatestRequest y createMockFetch; no cambió backend, OpenAPI, otras vistas o dependencias.

El Monitor muestra circuitos SENACE/BCRP, llamadas fallidas/exitosas, tasa, LLAMADAS_LOGICAS, NO_VERIFICADA y NO_IMPLEMENTADO. CLOSED no significa proveedor operativo; −1 es No calculada; ausencias no son cero. La actualización es manual, sin polling, y la hora está etiquetada como local del cliente. No existen logs, historial, latencias o respaldo ficticio.

El 07/11/2026 pasaron typecheck, 98 pruebas actuales y build. Edge/Vite local verificó tres resoluciones sin desbordamiento global, controles de 44 px, estados completos/incompletos, actualización, cancelación válida en segundo intento y navegación. No hubo excepciones monitorizadas o tráfico backend/proveedores. Capturas y JSON están en documentacion/04_Fase4_Frontend/evidencias/lote6/.

El siguiente prompt debe preparar el Lote 7 como integración progresiva explícitamente autorizada, revisando arranque seguro backend/SQLite, configuración HTTP/mock y CORS. No repetir implementación de las vistas, no interpretar los mocks como pruebas reales y no iniciar Spring Boot o consultas externas automáticamente.
