# EcoInvest — LKG, Bloque 3B: adaptación frontend React

Fecha: 11/11/2026. Zona horaria: America/Lima.

## 1. Resumen ejecutivo

Se adaptaron tipos, validación, adaptadores y las cuatro vistas React para interpretar el contrato Last Known Good (LKG) del backend. La interfaz distingue procedencia directa, respaldo local y ausencia de respaldo utilizable, con captura, edad, advertencias e intento API separados de los datos mostrados.

Resultados ejecutados:

- `npm run typecheck`: correcto, código 0.
- `npm run test:api`: **191 pruebas satisfactorias, cero fallos, cancelaciones u omisiones**, código 0.
- `npm run build`: correcto, código 0.
- Revisión de las cuatro vistas en **1440×900, 768×1024 y 375×812**, con escenarios artificiales.
- Sin desbordamiento horizontal global en las doce combinaciones medidas; las tablas extensas desplazan dentro de su contenedor.
- Sin errores JavaScript observados ni solicitudes de página a `/api/*` o destinos externos durante la revisión satisfactoria.

**No se activó LKG en un servidor real ni se modificaron backend/OpenAPI.** No hubo consultas SENACE/BCRP, lectura de credenciales, acceso SQLite, instalaciones, commits o despliegues. Vite y los navegadores de prueba fueron cerrados. La evidencia es de frontend con fixtures y transportes controlados, no de integración real desplegada.

## 2. Contexto y autorizaciones

Se consultaron AGENTS.md, las skills pertinentes y los reportes [Bloque 1](REPORTE_IMPLEMENTACION_LKG_BLOQUE1.md), [Bloque 2](REPORTE_IMPLEMENTACION_LKG_BLOQUE2.md), [Bloque 3A](REPORTE_IMPLEMENTACION_LKG_BLOQUE3A.md) y [cierre técnico anterior](REPORTE_CIERRE_TECNICO_ECOINVEST.md), contrastándolos con [OpenAPI vigente](../backend/src/main/resources/openapi.yaml) y código frontend.

El usuario autorizó cuatro archivos nuevos y diecisiete modificaciones frontend. Después autorizó typecheck, pruebas y build; separadamente autorizó Vite/navegador con respuestas artificiales y revisión responsive. Finalmente autorizó este reporte.

El frontend anterior solo aceptaba origen directo SENACE, descartaba los headers LKG y ocultaba algunos datos respaldados al comprobar exclusivamente API_DIRECTA. Monitor solo aceptaba NO_IMPLEMENTADO. Estos eran los puntos concretos de incompatibilidad, sin necesidad de reconstruir vistas o duplicar la capa API.

## 3. Archivos creados y modificados

### 3.1 Nuevos

| Archivo | Responsabilidad |
|---|---|
| [src/api/lkg.ts](../frontend/src/api/lkg.ts) | Validación, disponibilidad y presentación segura LKG, sin HTTP, reloj de caducidad o persistencia. |
| [LkgNotice.tsx](../frontend/src/components/LkgNotice.tsx) | Aviso reutilizable de procedencia, captura, antigüedad e intento API. |
| [LkgNotice.css](../frontend/src/components/LkgNotice.css) | Estilo local responsive del aviso, sin ocultar overflow global. |
| [tests/api/lkg.test.mjs](../frontend/tests/api/lkg.test.mjs) | Treinta casos nuevos ejecutados con respuestas artificiales. |

### 3.2 Modificados

| Archivo | Cambio |
|---|---|
| [types.ts](../frontend/src/api/types.ts) | Nuevos orígenes, interfaces/enums LKG y campos opcionales. |
| [types.typecheck.ts](../frontend/src/api/types.typecheck.ts) | Comprobaciones positivas/negativas del contrato TypeScript. |
| [validation.ts](../frontend/src/api/validation.ts) | Orígenes ampliados y validación del objeto opcional `lkg`. |
| [client.ts](../frontend/src/api/client.ts) | Conservación de los siete headers nuevos, sin nuevo transporte. |
| [adapters/dashboard.ts](../frontend/src/api/adapters/dashboard.ts) | Disponibilidad independiente por fuente, coherencia y advertencias. |
| [adapters/consultoras.ts](../frontend/src/api/adapters/consultoras.ts) | Parseo estricto de headers y respaldo utilizable conservando array raíz. |
| [adapters/monitor.ts](../frontend/src/api/adapters/monitor.ts) | Estados del módulo y observaciones locales opcionales. |
| [dashboardModel.ts](../frontend/src/features/dashboard/dashboardModel.ts) | Proyectos utilizables con respaldo y escenarios explícitos. |
| [DashboardPage.tsx](../frontend/src/features/dashboard/DashboardPage.tsx) | KPI y avisos separados de SENACE/BCRP. |
| [ExploradorPage.tsx](../frontend/src/features/explorador/ExploradorPage.tsx) | Aviso SENACE reutilizando la respuesta existente. |
| [consultorasModel.ts](../frontend/src/features/consultoras/consultorasModel.ts) | Mensajes y conteos de filas respaldadas, incluido vacío conocido. |
| [ConsultorasPage.tsx](../frontend/src/features/consultoras/ConsultorasPage.tsx) | Procedencia, captura y textos compatibles con degradación utilizable. |
| [monitorModel.ts](../frontend/src/features/monitor/monitorModel.ts) | Interpretación local, etiquetas y escenarios del Monitor. |
| [MonitorPage.tsx](../frontend/src/features/monitor/MonitorPage.tsx) | Panel LKG y tabla de observaciones con límites explícitos. |
| [MonitorPage.css](../frontend/src/features/monitor/MonitorPage.css) | Panel y scroll interno de observaciones. |
| [fixtures.ts](../frontend/src/mocks/fixtures.ts) | Escenarios LKG artificiales explícitos, conservando los anteriores. |
| [package.json](../frontend/package.json) | Incorporación del test LKG al runner existente; sin dependencias nuevas. |

No se modificaron AppShell, navegación, logos, configuración de fuente, requests/cancelación, almacenamiento del snapshot frontend, modelo de optimización del Explorador, backend, OpenAPI, Resilience4j ni diseños remotos Stitch. Los cambios backend visibles en el árbol Git pertenecen a bloques anteriores y fueron preservados.

## 4. Contrato integrado

### 4.1 Tipos y campos

Se mantienen tipos manuales trazables a OpenAPI, respetando optionalidad y ausencia. Se añadieron `RESPALDO_LOCAL` y `SIN_RESPALDO` a `OrigenSenace`, conservando API_DIRECTA_SENACE y NO_DISPONIBLE. BCRP conserva su origen textual y reconoce las procedencias aplicables sin mezclarlo con SENACE.

Nuevas interfaces: `LkgIntentoApi`, `LkgRespaldo`, `LkgMetadata`, `ObservacionSnapshot` y `EstadoModuloLkg`, con estados de lectura/escritura y operación correspondientes al contrato.

`ConsultaSenace.lkg`, `IndicadorBcrp.lkg` y `EstadoServiciosResponse.lkg` son opcionales. Una respuesta histórica sin ellos no se reinterpreta como respaldo ni se rechaza por esa ausencia.

### 4.2 Disponibilidad frente a éxito externo

Se conservó `official()` como comprobación estricta de API directa, particularmente para no ampliar silenciosamente la interpretación del datastream genérico.

Las nuevas funciones `usableSenace()` y `usableBcrp()` distinguen datos utilizables de éxito directo. Para respaldo JSON se exige evidencia suficiente: lectura AVAILABLE, captura válida UTC, edad no negativa/segura, antigüedad booleana y estado de intento fallido compatible; en los cuerpos JSON se exige además versión de esquema soportada y cobertura NO_VERIFICADA.

Un respaldo declarado con EXPIRED, INVALID, MISSING u otro estado no utilizable se rechaza. No se convierte en cero o lista oficial vacía. Los metadatos mal tipados/incoherentes producen error de validación; un objeto LKG incompleto sobre datos directos puede generar advertencia sin perder esos datos válidos.

React no fija ni recalcula las políticas temporales del backend. La edad se presenta como la informada al recibir la respuesta y no se incrementa mediante polling o reloj local. La navegación al Explorador conserva la captura original, sin afirmar frescura actual.

## 5. Estados y presentación por vista

### 5.1 Dashboard Analítico

Los KPI, gráfico y tabla aceptan proyectos respaldados cuando son utilizables. El indicador económico respaldado conserva su valor conocido sin convertir ausencia en cero. Las procedencias de SENACE y BCRP se muestran separadas.

El aviso reutilizable presenta origen comprensible, fecha UTC, edad informada, condición de antigüedad, intento API y operaciones locales conocidas. Con respaldo advierte que la captura es histórica y no representa una consulta nueva satisfactoria. Con escritura fallida informa que no se acredita una captura persistida nueva, sin descartar los datos directos.

Se mantienen FECHA_INICIO como año de filtrado, default 2017, conteos de registros, RUC textual y limitaciones económicas/geográficas existentes. Se preservan los controles manuales HTTP y no se activa fallback a fixtures.

### 5.2 Explorador Geográfico

La vista obtiene la misma respuesta del Dashboard mediante el almacenamiento existente. Se añadió únicamente presentación del origen/captura SENACE; no un segundo cliente, consulta backend, localStorage o nueva caché.

El modelo de optimización 7E no fue modificado: 20 filas por página, hasta 21 puntos SVG, coincidencias exactas agrupadas y todas las filas accesibles mediante lista/filtros/paginación. No se añadieron coordenadas a datos HTTP ni cartografía externa. Si la respuesta no contiene coordenadas utilizables, se conserva la lista y su estado informativo.

La revisión visual con respaldo heredado del Dashboard mostró su aviso conservado. Separadamente, el escenario de coordenadas artificiales permitió comprobar selección de marcador por teclado y zoom, sin atribuirlo a geografía oficial.

### 5.3 Consultoras Ambientales

Se conserva array raíz de ocho campos opcionales, orden, duplicados, fechas originales, búsqueda, filtros, detalle y paginación de diez filas. No se infieren RUC, ID empresarial, empresas únicas, niveles EIA o vigencia administrativa.

El cliente captura estos headers adicionales, normalizados a minúsculas dentro de `HttpResult.headers`:

```text
X-Senace-Respaldo-Capturado-En
X-Senace-Respaldo-Edad-Segundos
X-Senace-Respaldo-Antiguo
X-Senace-Intento-Estado
X-Senace-Intento-Motivo
X-Senace-Snapshot-Lectura
X-Senace-Snapshot-Escritura
```

Continúa conservando los cinco anteriores de origen, estado, cobertura, motivo y cuerpo completo. Edades y booleanos se parsean estrictamente; los valores omitidos no se rellenan.

**Diferencia de transporte importante:** el endpoint no transmite `versionEsquema` ni una cobertura adicional del respaldo por header. La validación de Consultoras utiliza exclusivamente los headers disponibles y la cobertura superior; no inventa campos para satisfacer el validador del cuerpo JSON de Dashboard. La ausencia de esos campos no se presenta como un valor observado.

HTTP 200 con RESPALDO_LOCAL permite mostrar filas y conteos conocidos con advertencia. Un respaldo vacío acreditado puede mostrar cero filas de esa captura. HTTP 502/503 con SIN_RESPALDO mantiene error y valores desconocidos: `[]` no acredita cero consultoras oficiales.

Los textos anteriores que confundían cualquier degraded con procedencia no confirmada se adaptaron para los respaldos utilizables.

### 5.4 Monitor de Salud SOA

Se admiten NO_IMPLEMENTADO histórico y los estados HABILITADO_SIN_VERIFICACION, OPERATIVO y CON_INCIDENCIAS. Un contrato anterior sin LKG informado no permite deducir existencia o ausencia de archivos.

El panel LKG muestra alcance del proceso, límite informado y observaciones opcionales de fuente/recurso/período, existencia e integridad observadas, fechas, última captura válida conocida, antigüedad/vencimiento y lectura/escritura.

Las observaciones vacías después de un reinicio no equivalen a archivos inexistentes. OPERATIVO se presenta como resultado de últimas operaciones locales, no certificación continua del disco. CLOSED/OPEN siguen sin acreditar disponibilidad externa; disponibilidadExterna conserva NO_VERIFICADA.

React no lee archivos, inicia verificaciones automáticas ni añade solicitudes para completar información. Se conserva actualización manual y ausencia de polling.

## 6. Estados globales, precisión y seguridad

- ready/empty anteriores permanecen compatibles; loading continúa siendo estado de carga de las vistas.
- Respaldo utilizable se clasifica degraded con advertencia, no error. Los datos siguen disponibles.
- Fallo al guardar sobre datos directos produce advertencia sin perder sus valores.
- Ausencia, errores de formato y respaldo no utilizable no se transforman en datos actuales ni ceros inventados.
- Los avisos usan motivos permitidos y etiquetas seguras; URLs autenticadas o textos externos arbitrarios no se publican como motivo LKG o nombre de recurso.
- `rawText` continúa sin imprimirse en UI/logs.
- Se mantienen validación de int64 seguro y advertencias de precisión JavaScript; no se introducen cálculos económicos exactos, redondeos sustitutivos ni pérdida deliberada de RUC textual.
- La cobertura permanece NO_VERIFICADA; coordenadas en rango no certifican CRS o ubicación real.
- MODO DEMOSTRACIÓN — DATOS ARTIFICIALES se mantiene exclusivamente en modo mock. Los avisos LKG dentro de mocks identifican adicionalmente su carácter artificial.

## 7. Pruebas automatizadas y build realmente ejecutados

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\frontend`.

```powershell
npm run typecheck
npm run test:api
npm run build
```

Los tres comandos finalizaron con código 0. El runner existente compiló a `.api-test-build/` y ejecutó diez archivos de tests Node, incluido `lkg.test.mjs`. No se instaló un framework nuevo.

Resultado de la ejecución actual:

| Métrica | Resultado |
|---|---:|
| Tests | 191 |
| Satisfactorios | 191 |
| Fallos | 0 |
| Cancelados | 0 |
| Omitidos | 0 |
| Todo | 0 |
| Duración reportada por Node test runner | 3065,0046 ms |

No se suman resultados de otras fases. Los treinta casos nuevos LKG forman parte de estas 191 pruebas.

La selección cubrió contrato histórico, fuentes directas/respaldadas individuales y combinadas, captura antigua, ausencia, fallo de escritura, rechazo de respaldos vencidos/corruptos/incompletos, coherencia de origen por fila/fuente, headers presentes/ausentes, ocho campos y duplicados de Consultoras, Monitor, sanitización, snapshot compartido sin GET duplicado, cancelación y resultados obsoletos. Los tests HTTP con fetch inyectado son artificiales aunque `HttpResult.mode` indique http.

Runtime Node observado durante la automatización: v22.16.0. Versiones declaradas del frontend: React 19.3.1, TypeScript 7.0.2 y Vite 8.3.5. El build mostró Vite 8.3.5, 51 módulos transformados y finalización correcta. `npm run build` volvió a ejecutar typecheck como parte de su script.

Artefactos generados: `.api-test-build/` y `dist/`, sin modificación manual. El bundle principal reportó aproximadamente 332,80 kB (98,99 kB gzip) y CSS 37,12 kB (6,83 kB gzip). Estas cifras de build no son un benchmark de carga ni una auditoría de rendimiento completa.

La evidencia de los comandos fue su salida capturada en la sesión; no se creó un reporte TAP permanente adicional ni se repitieron tests para redactar este documento.

## 8. Revisión local en navegador

### 8.1 Preparación y problemas de entorno

Las herramientas integradas de navegador/Node REPL fallaron al inicializar el entorno Windows. Se utilizó Edge headless instalado, controlado por CDP local mediante Node, sin instalar paquetes, con perfiles temporales propios y sin modificar perfiles del usuario.

Hubo dos incidencias de preparación, sin cambios de producto:

1. La primera invocación npm con argumentos adicionales interpretó el puerto como directorio y devolvió 404 para la página. Se detuvo ese proceso y se utilizó el arranque simple.
2. Asignar cadenas vacías a variables de entorno desde PowerShell las eliminó. Vite recuperó valores públicos de la configuración local, combinando modo mock con permisos HTTP residuales; la aplicación mostró error de configuración y no llegó a las vistas. No se tomó ese intento como validación visual satisfactoria.

Se verificó el módulo público servido por Vite y se corrigió el arranque manteniendo valores vacíos dentro del proceso Node, sin editar archivos de entorno:

```powershell
node --input-type=module -e "process.env.VITE_API_MODE='mock'; process.env.VITE_API_BASE_URL=''; process.env.VITE_HTTP_FEATURES=''; process.argv=['node','vite']; await import('./node_modules/vite/bin/vite.js');"
```

Este comando es el efectivamente utilizado para la revisión final, no un cambio en los scripts permanentes. Vite quedó en `http://127.0.0.1:5173/` con las cuatro vistas en modo mock.

### 8.2 Aislamiento y captura

La automatización solo permitió solicitudes de página al origen Vite local y bloqueó `/api/*` y otros destinos mediante interceptación. En la pasada completa se observaron 51 solicitudes de página de recursos locales, **cero `/api/*`, cero destinos externos, cero solicitudes bloqueadas y cero errores JavaScript**. La segunda pasada de interacciones tampoco registró destinos prohibidos ni excepciones.

Esta evidencia corresponde a las páginas controladas y la instrumentación usada; no es una captura global de paquetes de todos los procesos del sistema. No hubo servidor backend ni credenciales configuradas para esta prueba.

Se guardaron dieciséis PNG exclusivamente artificiales en una carpeta temporal y se inspeccionaron capturas de escritorio, tablet y móvil, incluidos avisos y tabla LKG. No se copiaron al repositorio ni se guardaron registros oficiales identificables.

### 8.3 Mediciones responsive

En las cuatro vistas se obtuvieron las siguientes anchuras globales:

| Viewport | Ancho informado de ventana | scrollWidth del documento en cada vista | Resultado |
|---|---:|---:|---|
| 1440×900 | 1440 px | 1425 px | Sin desbordamiento horizontal global |
| 768×1024 | 768 px | 753 px | Sin desbordamiento horizontal global |
| 375×812 | 375 px | 375 px | Sin desbordamiento horizontal global |

La diferencia en escritorio/tablet corresponde al espacio ocupado por la barra vertical. La revisión comprobó dimensiones DOM reales, no solo compilación CSS.

Ejemplos de scroll interno en móvil:

| Tabla | Ancho del contenedor | scrollWidth interno |
|---|---:|---:|
| Dashboard | 341 px | 726 px |
| Consultoras | 341 px | 1296 px |
| Monitor, circuitos | 341 px | 768 px |
| Monitor, observaciones LKG | 303 px | 928 px |

Los avisos se reorganizaron en columnas fluidas sin extender el documento. No se detectó un defecto visual que requiriera edición adicional. La composición conserva shell, logos, paleta y componentes existentes; esta revisión no recuperó ni modificó Stitch remoto ni constituye una comparación pixel a pixel nueva.

### 8.4 Interacciones observadas

- Dashboard: búsqueda redujo las filas renderizadas de 3 a 1; paginación mostró 4–4 de 4 en la segunda página; aplicar actividad mostró Minería/año de inicio 2017.
- Dashboard ready: KPI artificiales 4 registros, 1 aprobado y 1,25 %. Empty: 0/0 y el indicador disponible. Respaldo de ambas fuentes y fallo de escritura conservaron valores utilizables con advertencia. SIN_RESPALDO mostró No disponible en los tres KPI, sin filas.
- Explorador: aviso respaldado conservado al navegar. En escenario separado de coordenadas artificiales hubo 4 filas, 2 marcadores, selección por Enter sincronizada con lista y cambio efectivo de viewBox al acercar. La prueba automatizada de snapshot HTTP compartido comprobó un solo GET inyectado; no se simuló un backend real en el navegador.
- Consultoras: primera página de diez filas; segunda de cuatro (11–14 de 14). Filtro local produjo seis filas. El detalle recibió foco en `consultants-detail-title` y cerrar devolvió el foco al botón de la tabla.
- Consultoras respaldadas vacías: KPI 0/0/0 conocidos para esa captura artificial. Escenario 502: KPI No disponible, no cero. Escritura fallida con filas directas: 14/14/9 y advertencia. Escenario 503: cero filas DOM y error, sin conteo oficial conocido.
- Monitor: se comprobaron habilitado sin verificación con cero observaciones, operativo con una observación y con incidencias. Tab produjo foco visible con contorno de 3 px; no se interpretó CLOSED como disponibilidad externa.

La revisión de teclado/foco es básica y dirigida; no certifica cumplimiento WCAG completo, compatibilidad exhaustiva con lectores de pantalla ni controles táctiles en hardware móvil real.

## 9. Evidencia temporal disponible

Carpeta verificada al redactar:

```text
C:\Users\adrie\AppData\Local\Temp\ecoinvest-lkg-qa-VGfYNh
```

Contiene `dashboard`, `explorador`, `consultoras` y `monitor` en archivos `*-1440.png`, `*-768.png`, `*-375.png`, más `*-375-lkg.png`: dieciséis capturas. Las imágenes son temporales, no portables y pueden eliminarse por limpieza del sistema. Este documento registra su ubicación y alcance; no garantiza conservación futura ni las incorpora como artefactos versionados.

La pasada adicional de interacciones utilizó otro perfil temporal propio en `ecoinvest-lkg-interactions-7iwXMe`, sin generar un reporte permanente ni capturas nuevas del proyecto.

Se cerraron los navegadores de automatización y Vite; la comprobación final del puerto 5173 devolvió **0 listeners**. No se terminaron procesos ajenos ni se inició backend para efectuar esta comprobación.

## 10. Riesgos, limitaciones y condiciones de activación

1. La adaptación frontend está validada con fixtures y transportes inyectados, no contra un servidor con LKG activo. Falta una integración controlada frontend–backend que confirme conjuntamente headers, serialización y comportamiento operativo con el backend preparado.
2. No se activó LKG ni en local real ni en Render. La aprobación de este bloque no autoriza consultas oficiales, carga de credenciales, arranque backend o despliegue.
3. Debe resolverse el directorio/volumen conservado antes de prometer persistencia en nube. El filesystem efímero de Render Free sigue siendo una limitación de durabilidad, aunque las vistas ya interpreten los respaldos.
4. Las observaciones del Monitor no certifican la integridad actual del disco y se reinician con el proceso; la UI lo advierte. Un estado OPERATIVO no garantiza recuperación de toda serie/período ni disponibilidad externa.
5. Edades y etiquetas reflejan el resultado recibido. Una pestaña abierta o una navegación no vuelve recientes los datos ni dispara comprobaciones de caducidad.
6. Las propiedades LKG siguen opcionales en OpenAPI. La UI aplica requisitos conservadores adicionales para presentar respaldo como utilizable y rechaza versiones de esquema no soportadas. Cualquier cambio futuro de esquema/headers debe revisarse coordinadamente.
7. Se conservaron los límites de precisión JavaScript: la UI no certifica exactitud BigDecimal ni acepta silenciosamente int64 inseguro. Las respuestas con identificadores fuera de rango seguro siguen requiriendo una decisión contractual futura, no un redondeo.
8. No se hicieron benchmarks nuevos de 20.000 filas en este bloque. La optimización del Explorador se preservó y sus regresiones existentes se ejecutaron, pero no se atribuyen mejoras de rendimiento nuevas.
9. Los avisos añaden longitud vertical, particularmente en móvil; la revisión mostró información legible sin overflow global. No se introdujo un rediseño ni se eliminaron metadatos para acortar visualmente la pantalla.
10. CORS, seguridad de despliegue, límites backend de memoria/disco y las limitaciones BCRP documentadas en Bloque 3A permanecen fuera de este cambio frontend.

Antes de activar para usuarios se recomienda autorizar explícitamente una fase controlada de integración, verificar configuración opt-in y almacenamiento, y definir un presupuesto de tráfico si se necesitan proveedores reales. No debe habilitarse por asociación con este reporte.

## 11. Estado final y siguiente paso

**Bloque 3B implementado y validado en su alcance frontend controlado.** Las cuatro vistas interpretan el contrato LKG con compatibilidad histórica, mensajes explícitos y sin convertir respaldo utilizable en error automático ni ausencia en cero.

No se modificaron reportes históricos o índice documental al crear este archivo. No se creó commit ni se inició Bloque 4.

Siguiente paso recomendado: revisar los reportes 3A/3B y diseñar, con permisos independientes, una validación conjunta controlada que no presuponga consultas oficiales ni durabilidad del alojamiento. Separar esa aceptación funcional de una futura activación o despliegue real.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó el Bloque 3B frontend LKG: cuatro archivos nuevos (`src/api/lkg.ts`, `components/LkgNotice.tsx/.css`, `tests/api/lkg.test.mjs`) y diecisiete modificaciones autorizadas en tipos, validación, cliente, tres adaptadores, modelos/vistas, CSS Monitor, fixtures y runner. No se modificaron backend, OpenAPI, AppShell, navegación, stores compartidos ni modelo 7E del Explorador.

Dashboard acepta fuentes directas/respaldadas independientemente y muestra captura/edad/intento API. Explorador reutiliza la misma respuesta sin otro GET ni alterar paginación de 20 filas/hasta 21 puntos. Consultoras interpreta los doce headers SENACE totales, conserva array/ocho campos/duplicados y no exige campos de esquema que el endpoint no transmite. Monitor distingue contrato histórico, habilitado sin verificación, operativo e incidencias, sin afirmar integridad actual o disponibilidad externa.

El 11/11/2026 pasaron typecheck, **191 pruebas, cero fallos/cancelaciones/omisiones**, y build Vite 8.3.5. Se revisaron las cuatro vistas en 1440×900, 768×1024 y 375×812 con Edge headless, fixtures explícitos y bloqueo de /api/externos. Sin overflow global ni errores JavaScript observados; búsqueda, filtros, paginación, teclado/foco, vacíos y errores comprobados. Hay dieciséis PNG artificiales temporales; Vite quedó cerrado y puerto 5173 libre.

La revisión tuvo incidencias de preparación del entorno, no correcciones funcionales adicionales: herramientas integradas indisponibles, argumentos npm interpretados como directorio y variables vacías eliminadas por PowerShell. Se usó Vite mock efectivo con variables fijadas en el proceso Node, sin editar .env.

**LKG no se activó en servidor real ni se desplegó.** No hubo Spring Boot, APIs oficiales, credenciales o SQLite. No es certificación end-to-end real ni WCAG completa. Persisten precisión JS, consumo proporcional a volumen y necesidad de almacenamiento durable en nube. El próximo prompt debe preparar una validación conjunta/activación controlada con autorizaciones explícitas, sin reimplementar bloques ni iniciar Bloque 4 automáticamente.
