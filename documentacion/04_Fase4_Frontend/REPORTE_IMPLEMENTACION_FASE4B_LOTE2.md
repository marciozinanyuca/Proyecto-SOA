# EcoInvest — Fase 4B, Lote 2: cliente API, tipos, adaptadores y mocks

Fecha de cierre: **07/11/2026**, America/Lima (UTC−05:00). Estado: **implementación y validación aislada satisfactorias**.

Este reporte se creó con autorización específica después de ejecutar las validaciones aprobadas. No conecta la capa API al shell ni inicia los lotes de pantallas funcionales. No certifica integración con backend real o funcionamiento end-to-end.

## 1. Objetivo y estado inicial

Preparar una capa de datos reutilizable para las cuatro operaciones de EcoInvest, conservando HTTP, metadatos, opcionales y estados de ausencia/degradación. Proveer fixtures artificiales y pruebas sin tráfico real.

El [Lote 1](REPORTE_IMPLEMENTACION_FASE4B_LOTE1.md) ya había creado React/TypeScript/Vite, navegación, logos y vistas estructurales. No existían src/api/, src/mocks/ o tests/api/ al comenzar este lote. No se reconstruyeron scaffold o componentes visuales.

Referencias funcionales: [OpenAPI](../../backend/src/main/resources/openapi.yaml), [cierre backend 3C.3](../03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md) y [conciliación Stitch](REPORTE_REVISION_STITCH_FASE4A.md). El diseño visual sigue subordinado a los datos realmente disponibles.

## 2. Autorizaciones y límites respetados

El usuario aprobó la lista de archivos y modificaciones antes de implementar, y posteriormente autorizó por separado typecheck, pruebas API y build. La creación de este reporte tuvo un permiso posterior independiente.

- No se instalaron dependencias ni se actualizaron versiones.
- No se inició Vite, Spring Boot u otro servidor en este lote.
- No se consultó backend, SENACE o BCRP.
- No se leyó configuración local con credenciales.
- No se modificaron backend, OpenAPI, Resilience4j, SQLite o Stitch.
- AppShell, navegación, logos, estilos y las cuatro vistas estructurales permanecieron intactos.
- No se actualizaron README/reportes históricos ni se hicieron commits.

La implementación se mantuvo desacoplada de las pantallas: crear un cliente no dispara solicitudes. Los tests de transporte inyectan funciones artificiales, no usan un servidor de mocks.

## 3. Arquitectura implementada

```text
Consumidor futuro de una vista
  → createLatestRequest, si necesita cancelar/descartar resultados antiguos
  → ApiClient explícito: mode=http o mode=mock
  → HttpResult: status + headers + JSON unknown + texto original / issue
  → adaptador por operación
  → Adapted<T>: ready / empty / degraded / error, datos y advertencias
```

El transporte no interpreta el éxito funcional solo a partir del HTTP. El adaptador tampoco sustituye el sobre HTTP: el consumidor debe conservar ambos para distinguir transporte, formato y estado semántico.

No hay caché, reintentos, polling, almacenamiento persistente o hooks React nuevos. El coordinador de solicitudes no ejecuta nada hasta recibir una operación; una instancia por consumidor evita cancelaciones entre vistas independientes.

### Cliente

[client.ts](../../frontend/src/api/client.ts), createApiClient, línea 23:

```typescript
dashboard(sector: string, anio?: number, signal?: AbortSignal): Promise<HttpResult>
consultoras(signal?: AbortSignal): Promise<HttpResult>
datastream(nombre?: string, signal?: AbortSignal): Promise<HttpResult>
estadoServicios(signal?: AbortSignal): Promise<HttpResult>
```

HttpResult conserva:

- mode: http o mock, seleccionado explícitamente.
- status: código recibido o null si no se recibió una respuesta HTTP.
- Headers relevantes normalizados a minúsculas: content-type y los cinco X-Senace-* conocidos.
- json: unknown hasta validación.
- rawText: cuerpo textual original únicamente en memoria, cuando pudo leerse.
- issue: categoría segura de configuración/parámetros/red/formato/cancelación.

No se registran cuerpos, causas o URLs en logs. rawText y json pueden contener información sensible del dataset: no deben imprimirse, persistirse ni adjuntarse a errores desde futuros consumidores.

La URL base es un parámetro explícito. No se importa configuración backend ni se construye un cliente automáticamente desde App.tsx. Se rechazan esquemas ajenos a HTTP(S), URL con userinfo, query o fragmento; no se acepta una URL autenticada como configuración. El transporte utiliza GET, Accept:application/json, credentials:omit y redirect:error.

El cliente codifica sector/nombre con URLSearchParams y valida sector no vacío/año entero seguro. Si anio o nombre se omiten, se omiten en la URL para conservar los defaults del backend; no se añade un año distinto. No se exige un rango temporal nuevo por regla frontend.

Un fallo HTTP 502/503 conserva status, headers y JSON. No es automáticamente un fallo de parseo. Un cuerpo HTML/JSON inválido se clasifica FORMATO conservando la evidencia recibida; una cancelación se distingue de RED. No hay fallback silencioso a fixtures.

## 4. Archivos creados y modificados

### Creados

```text
frontend/src/api/types.ts
frontend/src/api/client.ts
frontend/src/api/requests.ts
frontend/src/api/validation.ts
frontend/src/api/types.typecheck.ts
frontend/src/api/adapters/dashboard.ts
frontend/src/api/adapters/consultoras.ts
frontend/src/api/adapters/datastream.ts
frontend/src/api/adapters/monitor.ts
frontend/src/mocks/fixtures.ts
frontend/src/mocks/mockFetch.ts
frontend/tests/api/client.test.mjs
frontend/tests/api/adapters.test.mjs
frontend/tests/api/requests.test.mjs
frontend/tsconfig.api-tests.json
```

### Modificados

| Archivo | Cambio |
|---|---|
| frontend/package.json | Script test:api con compilación TypeScript y runner Node |
| frontend/.gitignore | Exclusión .api-test-build/ |

No se cambió package-lock.json para instalar herramientas. El lote reutiliza dependencias existentes. Además se crea este reporte con autorización documental propia.

## 5. Tipos y trazabilidad OpenAPI

[types.ts](../../frontend/src/api/types.ts) declara tipos manuales vinculados al contrato backend. Esta decisión fue presentada y aprobada para evitar una dependencia adicional en un contrato pequeño.

Se evaluó conceptualmente openapi-typescript como candidato futuro; **no se consultó su versión, verificó compatibilidad ni instaló durante este lote**. No se afirma generación automática, sincronización garantizada o validación exhaustiva por esos tipos.

Modelos incluidos: DashboardResponse, ProyectoSenacePublico, ConsultoraSenacePublica, IndicadorBcrp, ConsultaSenace, ConteosSenace, DatastreamResponse, EstadoServiciosResponse y enums de consulta/origen/motivos/advertencias. Se mantuvieron propiedades opcionales porque el contrato no enumera required en esos modelos.

data genérico permanece unknown. EstadoServiciosResponse conserva sus objetos flexibles; CircuitMetrics es una interpretación adaptada de campos descritos en el contrato/controlador, no una ampliación del esquema fuente.

types.typecheck.ts se verifica con tsc y contiene expectativas estáticas: modelo vacío admitido, RUC textual, rechazo de RUC numérico, rechazo de un campo RUC inventado en consultoras y acceso directo prohibido a data unknown. No se importa desde la aplicación y se excluye de la emisión de tests.

### Opcionales, null y números

- Campos públicos opcionales pueden estar omitidos; no se normalizan a cero o cadenas vacías.
- Los campos conocidos no nullable con null explícito se rechazan en sus validaciones públicas; el genérico admite data:null en error y DTOs crudos con opcionales null donde corresponde.
- RUC sigue siendo string, incluidos ceros iniciales.
- IDs y enteros conocidos se comprueban mediante Number.isSafeInteger; no se utilizan enteros fuera del rango seguro.
- Valores numéricos conocidos deben ser finitos. BigDecimal no se reproduce mediante una clase decimal nueva.
- Los adaptadores económicos/geográficos advierten que number no garantiza precisión decimal exacta.
- rawText conserva el literal JSON recibido. Esto **no es un parser lossless** ni recupera automáticamente la precisión de json ya convertido por JSON.parse.
- Los objetos opacos de recursos heredados no se reinterpretan exhaustivamente: permanecen unknown y no se promete validar todos sus números o campos internos.

## 6. Adaptadores

### Dashboard

[dashboard.ts](../../frontend/src/api/adapters/dashboard.ts), adaptDashboard, línea 12:

- Admite HTTP200 y comprueba forma de campos conocidos, proyectos, indicador y metadatos.
- Conserva independencia SENACE/BCRP y datos ausentes.
- Comprueba coherencia representativa de conteos, procedencias y valores económicos duplicados.
- Clasifica como degraded una fuente no disponible/no confirmada o calidad temporal NO_EVALUABLE.
- Distingue una selección conocida vacía de error; conserva motivo para separar vacío oficial de sin coincidencias.
- No recalcula fechas, aplica nuevas equivalencias sectoriales o fabrica indicadores.

### Consultoras

[consultoras.ts](../../frontend/src/api/adapters/consultoras.ts), adaptConsultoras, línea 6:

- Conserva array raíz y ocho conceptos públicos, orden y filas repetidas.
- Recupera procedencia/estado/cobertura/motivo/cuerpo completo de headers.
- Valida los valores de headers conocidos, incluyendo true/false textual.
- [] con 502/503 es error, no empty oficial; un error que incluye filas se rechaza.
- Sin evidencia suficiente de procedencia oficial, una respuesta 200 se clasifica degraded.
- No deriva vigencia o identidad administrativa ni deduplica.

### Datastream

[datastream.ts](../../frontend/src/api/adapters/datastream.ts), adaptDatastream, línea 6:

- Comprueba envolvente y formas por recurso.
- Conserva los nombres DTO oficiales de los arrays verificados y data opaco para los seis recursos heredados.
- En error 502/503 exige data:null explícito; no lo sustituye por [].
- Valida ID entero seguro en proyectos y tipos conocidos sin transformar a modelos camelCase.
- Añade advertencia de esquema heredado no verificado; éxito local no certifica al proveedor.

### Monitor

[monitor.ts](../../frontend/src/api/adapters/monitor.ts), adaptMonitor, línea 11:

- Comprueba campos conocidos de circuitos, contadores y métricas.
- Conserva estados de circuito, incluyendo los presentes en fixtures, sin traducir CLOSED a proveedor disponible.
- Mantiene LLAMADAS_LOGICAS, NO_VERIFICADA y NO_IMPLEMENTADO.
- Objetos/metadatos incompletos se clasifican degraded sin inventar métricas.

Los adaptadores son validadores deliberadamente acotados. No son un motor completo de JSON Schema ni verifican todas las combinaciones semánticas entre campos o todos los campos adicionales de una respuesta. Esas limitaciones no deben ocultarse al conectar vistas.

## 7. Solicitudes, cancelación y respuestas obsoletas

[requests.ts](../../frontend/src/api/requests.ts), createLatestRequest, línea 5:

- Cada nueva ejecución aborta la anterior de esa instancia.
- Un número de generación impide publicar una respuesta anterior aunque su transporte ignore AbortSignal.
- Retorna current/value, stale, cancelled o error seguro; no propaga mensajes de excepciones externas.
- cancel() aborta la operación vigente sin fabricar datos.
- No se introduce cancelación global, caché o deduplicación automática entre consumidores.

Cancelar en navegador no acredita que el backend/proveedor haya detenido un trabajo ya comenzado. La reutilización de datos entre vistas deberá diseñarse al conectarlas; este lote no modifica su estado React.

## 8. Fixtures y modo mocks

[fixtures.ts](../../frontend/src/mocks/fixtures.ts) contiene únicamente datos artificiales pequeños. Los nombres señalan esa condición y no se copiaron datasets, personas, empresas o cuerpos completos reales.

Escenarios: dashboard exitoso, vacío oficial, sin coincidencias, SENACE degradado con BCRP disponible, configuración ausente, fechas no evaluables, opcionales omitidos, consultoras duplicadas, errores 502/503, genérico array/objeto/null y monitor con circuitos CLOSED/OPEN/HALF_OPEN/DISABLED.

La etiqueta API_DIRECTA en esos cuerpos describe **un escenario contractual artificial**, no una nueva consulta oficial. El sobre HttpResult mantiene mode:mock para que una futura UI lo identifique. Las pantallas aún no muestran esos fixtures.

[mockFetch.ts](../../frontend/src/mocks/mockFetch.ts), createMockFetch, línea 7, recibe una asignación explícita de ruta a escenario. No delega a fetch global, incluso en ruta no configurada, y permite una demora artificial cancelable. El host mock.invalid es una etiqueta de construcción de URL, nunca se resuelve por este transporte.

fixture() clona el contenido antes de usarlo, evitando contaminar escenarios posteriores. El código mock no transforma un error real en datos inventados.

## 9. Herramientas y ejecución de pruebas

No se instaló Vitest, Jest, MSW, un generador o una biblioteca de validación. Se utilizaron node:test, node:assert/strict y objetos Response/Headers/AbortController disponibles en Node22, más TypeScript ya instalado.

Script agregado:

```text
tsc -p tsconfig.api-tests.json && node --test tests/api/client.test.mjs tests/api/adapters.test.mjs tests/api/requests.test.mjs
```

La configuración emite solamente API/mocks a .api-test-build/ y excluye el módulo de expectativas estáticas. Los imports .js de los fuentes TS permiten ejecutar esa salida ESM bajo el package.json existente. No se modifica el build del shell ni se inicia Vite.

Directorio de ejecución: `D:\Documentos\GitHub\Proyecto-SOA\frontend`.

Comando autorizado y ejecutado el **07/11/2026**:

```powershell
npm run typecheck
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
npm run test:api
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
npm run build
```

Se usó el entorno Node22.16.0/npm10.9.2 ya verificado en Lote1; no se ejecutó una nueva comprobación de versión dentro de esta selección ni se afirma una propiedad runtime capturada por un XML inexistente. Vite informó versión8.3.5 en la salida de build. No se migró el entorno.

## 10. Resultados realmente observados

| Paso | Resultado |
|---|---|
| npm run typecheck | Código0; sin errores TypeScript |
| Compilación de tests API | Correcta |
| node:test | **39 ejecutadas, 39 satisfactorias** |
| Fallos / canceladas / omitidas / todo | 0 / 0 / 0 / 0 |
| Duración informada del runner | 256,7823 ms |
| npm run build | Código0; Vite transformó24módulos y terminó en247ms |
| Comando conjunto | Código de salida0 |

build llama de nuevo typecheck por su script. Esa repetición no es una suite de tests adicional. No se suman las39 pruebas con suites históricas para afirmar una selección conjunta no ejecutada.

### Distribución de los39 casos

| Archivo | Casos | Frontera |
|---|---:|---|
| adapters.test.mjs | 27 | Clasificación, forma, opcionales, conteos, procedencia, headers, precisión y recursos |
| client.test.mjs | 9 | Cuatro rutas, parámetros, HTTP, formato/red, configuración, cancelación y mock sin fetch global |
| requests.test.mjs | 3 | Respuesta obsoleta, cancelación y aislamiento de coordinadores |
| **Total** | **39** | **Transportes controlados; sin red real** |

Se comprobaron, entre otros: 200 degradado; [] oficial frente a []503; headers inválidos; RUC numérico rechazado; int64 inseguro y valores no finitos rechazados en campos conocidos; preservación de duplicados/fechas; genérico con nombres oficiales y null explícito; monitor sin disponibilidad certificada; literal decimal conservado en rawText pero aproximado en JSON number; ausencia de reintentos y de fallback mock automático.

Un caso reemplaza fetch global por una función que falla si se usa para comprobar que el modo mocks no delega a la red, y lo restaura después. Los demás casos de cliente también inyectan transportes artificiales. No se utiliza un servidor o un acceso HTTP “local” oculto.

### Build generado

Salida observada, igual a la del shell previo en nombres/tamaños principales porque la nueva capa todavía no está importada desde las vistas:

| Archivo | Tamaño indicado |
|---|---:|
| dist/index.html | 0,58kB |
| senace-logo-DxdZQO89.webp | 15,33kB |
| bcrp-logo-CsZ92eYH.png | 15,87kB |
| ecoinvest-logo-BKZHfJ2g.png | 628,39kB |
| index-BO6dUan7.css | 8,42kB, gzip2,32kB |
| index-BQwodmWg.js | 227,98kB, gzip71,47kB |

No se guardó un reporte TAP permanente en el repositorio. La evidencia corresponde a la salida capturada por las herramientas en la sesión y resumida aquí; no inventar rutas de artefactos de test no generados.

## 11. Errores, correcciones y limitaciones

La primera selección de validaciones autorizada terminó satisfactoriamente. No hubo un fallo TypeScript o test que requiriese corregir backend o alterar el contrato.

Durante la edición, una petición de parche que repetía el mismo archivo fue rechazada por la herramienta antes de aplicarse. Se reformuló en una única actualización. No fue un fallo de compilación o una ejecución de tests, ni se oculta como si lo fuera.

Límites pendientes:

1. Tipos manuales: mantener trazabilidad al contrato y revisar cambios futuros; no hay generación automática ni test de paridad exhaustiva OpenAPI.
2. Validación de campos conocidos, no auditoría total de datos adicionales ni motor JSON Schema. Los recursos heredados mantienen datos opacos.
3. No hay parser numérico lossless. rawText no debe presentarse como un cálculo decimal exacto ni registrarse para depuración.
4. Los adaptadores retornan datos, motivo y estado; no implementan loading o comportamiento visual. Loading pertenece a futuros consumidores React.
5. No hay selección del modo mocks en UI, banner nuevo o integración de config.ts: el modo es explícito en la fábrica y las vistas siguen en preparación.
6. No se comprobó CORS en navegador ni consumo real del backend en este lote. La evidencia previa del shell y backend conserva su alcance histórico.
7. No hay caché/deduplicación de datos entre dashboard/explorador todavía. El coordinador solo cancela/descarta dentro de su instancia.
8. Los errores del transporte conservan cuerpos en memoria cuando existen; los consumidores futuros no deben imprimirlos o mostrar JSON crudo al usuario.
9. Los tests no arrancan servidores, no validan disponibilidad externa y no son end-to-end.

## 12. Estado final y próximos pasos

**Lote2 completado en su alcance de capa API independiente, adaptadores, fixtures artificiales y validación aislada.** El shell del Lote1 permanece intacto y la aplicación no consulta todavía el backend.

Siguiente lote recomendado: diseñar Dashboard Analítico usando la capa existente, los prototipos Stitch y fixtures explícitos. Antes de editar, presentar archivos y alcance exactos; no reconstruir API, tipos, shell o logos.

Ese lote deberá decidir cómo combinar el sobre HttpResult con Adapted<DashboardResponse>, mostrar modo mock inequívoco, sincronizar año de inicio/actividad y conservar datos ausentes. La distribución central del prototipo debe usar información respaldada, sin inversión, correlación, serie mensual o verificación SUNAT ficticias.

La conexión a backend real sigue requiriendo permisos propios y preparación de proceso/aislamiento SQLite del cierre3C.3. Este reporte no autoriza iniciar Vite, Spring Boot, consultas externas o los Lotes3–7.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó Fase4B Lote2 el07/11/2026. El frontend React/TypeScript/Vite del Lote1 permanece visualmente intacto: AppShell, navegación hash, logos, estilos y cuatro destinos “En preparación” no se modificaron. Backend/OpenAPI/BCRP/SENACE/Resilience4j/SQLite/Stitch permanecen fuera del cambio. No hubo instalaciones, servidores, commits ni llamadas reales.

Se crearon src/api/types.ts, client.ts, requests.ts, validation.ts, types.typecheck.ts; adapters/dashboard.ts, consultoras.ts, datastream.ts y monitor.ts; src/mocks/fixtures.ts y mockFetch.ts; tests/api/client.test.mjs, adapters.test.mjs y requests.test.mjs; tsconfig.api-tests.json. Se modificaron solamente package.json para test:api y .gitignore para .api-test-build/. Este reporte se creó con autorización posterior independiente.

createApiClient exige modo explícito http o mock y no consulta al construirse. Expone las cuatro GET y conserva status/headers/JSON unknown/rawText/issue, sin retry, redirecciones, credenciales de navegador o fallback automático a mocks. Sector obligatorio/anio opcional y nombre opcional se codifican sin imponer defaults nuevos. AbortSignal y createLatestRequest cancelan/descartan respuestas antiguas sin estado global compartido.

Tipos manuales trazables a OpenAPI con opcionales conservados, RUC string, data unknown y enums. No se instaló generador: openapi-typescript quedó como candidato no verificado, no como dependencia. Los validadores comprueban campos conocidos, no todo JSON Schema o cada combinación semántica. Los campos numéricos conocidos rechazan no finitos/IDs inseguros; number no conserva precisión BigDecimal arbitraria, rawText preserva el literal en memoria y no debe imprimirse. Los recursos heredados siguen opacos/no certificados.

Adaptadores mantienen independencia SENACE/BCRP y clasifican dashboard200degradado, vacío/sin coincidencias/calidad temporal; consultoras array200/502/503 con headers y filas repetidas; genérico array/objeto/null y nombres oficiales; monitor con LLAMADAS_LOGICAS, NO_VERIFICADA y NO_IMPLEMENTADO, sin inferir salud por circuito. Datos desconocidos no se convierten a cero. Mocks pequeños expresamente artificiales, sin datos personales, nunca fetch global en modo mock y sin cambio silencioso tras un fallo HTTP.

Validación autorizada ejecutada desde frontend/: npm run typecheck; npm run test:api; npm run build, deteniendo ante fallos. Todo código0. node:test:39casos satisfactorios,0fallos/canceladas/omitidas,256,7823ms;27adaptadores+9cliente+3coordinador. Typecheck incluye expectativas estáticas. No hubo dependencias nuevas: runner/assert nativos Node y TypeScript instalado. Build Vite8.3.5 transformó24módulos en247ms; shell y bundle principal conservaron nombres/tamaños porque la capa todavía no se importa en UI. No hay suite conjunta con pruebas históricas ni reporte TAP persistente adicional.

No se inició navegador ni se verificó CORS/API real en este lote. No declarar end-to-end. Siguiente prompt recomendado: plan y autorización Lote3 Dashboard, reutilizando API/adaptadores/mocks y diseño Stitch, definiendo banner modo mock, estados/loading/degraded, filtros por año de inicio y agregados honestos; sin reimplementar capas previas ni conectar backend real automáticamente. La operación real conserva pendientes de configuración e aislamiento SQLite/JPA y permisos independientes.
