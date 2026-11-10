# EcoInvest — Fase 7C.3: Dashboard HTTP y Explorador compartido

Fecha: 07/11/2026. Entorno de ejecución: Windows, PowerShell, Oracle Java 17.0.12, Spring Boot 3.2.4, React y Vite 8.3.5.

## 1. Resumen ejecutivo

Se implementó la selección explícita de fuente HTTP para Dashboard y Explorador, conservando Monitor y Consultoras. Dashboard consulta manualmente y guarda una única respuesta en memoria de sesión; Explorador lee esa respuesta sin transporte propio. No se trasladó la caché artificial a HTTP, no hay persistencia en localStorage y no se activan mocks ante fallos.

Se ejecutaron **143 pruebas frontend satisfactorias**, typecheck y build. Posteriormente se realizó **un único GET manual real** al Dashboard local con `sector=Hidrocarburos&anio=2017`. Respondió HTTP 200, con 48 registros filtrados, 40 aprobados e indicador BCRP anual PM04974AA de valor -2.44780196733593. Ambas fuentes informaron procedencia directa. SENACE mantuvo cobertura NO_VERIFICADA.

Explorador reutilizó las 48 filas y sus coordenadas en rango sin otro GET. La navegación de regreso conservó la respuesta. Se comprobaron filtro local, selección sincronizada, zoom, selección pendiente y medidas responsive en tres resoluciones. No se certifica geografía oficial, frescura del proveedor ni integración end-to-end exhaustiva de todo EcoInvest.

## 2. Antecedentes y autorizaciones

Se consultaron instrucciones AGENTS.md, contrato OpenAPI, componentes actuales y reportes pertinentes:

- [Auditoría 7A](REPORTE_AUDITORIA_INTEGRACION_FASE7A.md).
- [Perfil seguro 7B](REPORTE_IMPLEMENTACION_FASE7B_BACKEND.md).
- [Monitor 7C.1](REPORTE_INTEGRACION_FASE7C1_MONITOR.md).
- [Consultoras 7C.2](REPORTE_INTEGRACION_FASE7C2_CONSULTORAS.md).
- [Cierre QA local 7C.2](REPORTE_CIERRE_QA_FASE7C2.md).
- [Dashboard Lote 3](REPORTE_IMPLEMENTACION_FASE4B_LOTE3.md).
- [Explorador Lote 4](REPORTE_IMPLEMENTACION_FASE4B_LOTE4.md).

Se aplicaron los procedimientos pertinentes de arquitectura, frontend, integración, seguridad, testing y documentación. Se obtuvieron autorizaciones separadas para implementación, validaciones frontend, inspección de configuración, uso limitado de configuración SENACE, arranque backend, arranque Vite, navegador/consulta real y creación de este documento.

No se modificaron backend, OpenAPI, políticas de proveedores, Resilience4j, SQLite, diseños Stitch o dependencias. No se realizaron commits ni se inició 7D.

## 3. Estado inicial y diagnóstico

Antes del cambio:

- La configuración HTTP central aceptaba `monitor` o `monitor,consultoras`.
- Dashboard y Explorador estaban bloqueados en HTTP y cargaban mocks al montarse en modo demostración.
- Sus tipos de consulta estaban limitados a sectores y años de fixtures.
- `demoSelectionStore.ts` conservaba cargas artificiales compartidas y una caché limitada a 12 entradas; su semántica no era adecuada como política implícita de datos reales.
- Las etiquetas de ambas vistas declaraban escenarios artificiales.

En el backend inspeccionado, `/api/dashboard` consulta proyectos SENACE una vez y después BCRP independientemente del resultado SENACE. El mapper compara ACTIVIDAD normalizada y el año derivado estrictamente de FECHA_INICIO. TODOS no limita actividad. El catálogo BCRP es independiente y no se modificó.

## 4. Archivos creados y modificados

Rutas relativas a la raíz del repositorio:

| Archivo | Acción y finalidad |
|---|---|
| `frontend/src/api/dashboardSelectionStore.ts` | Creado: último resultado HTTP, copias defensivas y control de generaciones, sin transporte o persistencia. |
| `frontend/src/api/source.ts` | Ampliado: combinación completa explícita, capacidad Dashboard restringida a su GET y parámetros. |
| `frontend/src/app/App.tsx` | Inyección de la configuración central a Dashboard y Explorador, manteniendo el control previo de acceso. |
| `frontend/src/features/dashboard/dashboardModel.ts` | Consulta HTTP manual, resultado compartido, tipos de filtros generales y mensajes dependientes de fuente. |
| `frontend/src/features/dashboard/DashboardPage.tsx` | Espera HTTP, consulta explícita, recuperación del resultado y controles/etiquetas según modo. |
| `frontend/src/features/dashboard/DashboardPage.css` | Estilo de inputs editables y foco consistente con el filtro existente. |
| `frontend/src/features/explorador/exploradorModel.ts` | Lectura del resultado HTTP sin carga y conservación de funciones geográficas existentes. |
| `frontend/src/features/explorador/ExploradorPage.tsx` | Reutilización, espera sin consulta previa, selección efectiva de solo lectura y etiquetas honestas. |
| `frontend/tests/api/source.test.mjs` | Permisos completos, parametrización, capacidades y preservación de Monitor/Consultoras. |
| `frontend/tests/api/dashboard.test.mjs` | HTTP simulado, metadatos, degradación, errores, cancelación y almacén. |
| `frontend/tests/api/explorador.test.mjs` | Lecturas compartidas sin GET extra ni coordenadas artificiales. |

Se preservaron cambios preexistentes del repositorio. Otros archivos modificados o sin seguimiento en Git no deben atribuirse por asociación a esta fase. No se editaron `demoSelectionStore.ts`, configuración frontend central, tipos OpenAPI, cliente fetch, adaptador Dashboard, componentes Monitor/Consultoras ni estilos del Explorador.

## 5. Configuración de fuente y aislamiento de capacidades

Configuración pública usada en Vite:

```text
VITE_API_MODE=http
VITE_API_BASE_URL=http://127.0.0.1:8081
VITE_HTTP_FEATURES=monitor,consultoras,dashboard,explorador
```

El parser admite esta combinación exacta, además de las dos combinaciones anteriores. Rechaza valores no admitidos, destinos no autorizados y configuraciones incompletas. No habilita el datastream genérico.

- `createDashboardSource` concede únicamente la operación Dashboard.
- La guarda verifica origen/ruta, método GET, parámetro sector único y no vacío, año opcional y ausencia de parámetros adicionales.
- Monitor y Consultoras conservan capacidades separadas y su comportamiento manual.
- Explorador tiene permiso de renderizado, pero carece de transporte HTTP propio para proyectos.
- El cliente existente mantiene `credentials: omit`, `redirect: error` y no incorpora reintentos o fallback a fixtures.

Estas restricciones frontend son controles operativos, no una frontera de autorización del servidor.

## 6. Resultado compartido y política de frescura

`dashboardHttpStore` utiliza el nuevo almacén de una sola respuesta:

- `peek()` devuelve una copia defensiva sin consultar nada.
- `begin()` inicia una generación y elimina el resultado anterior.
- `publish(ticket, value)` publica solo si la generación sigue siendo la actual.
- El snapshot incluye selección efectiva, HttpResult, Adapted de DashboardResponse y `receivedAt` del cliente. Los snapshots HTTP no incluyen escenario artificial.
- Cancelación y generaciones impiden que una respuesta anterior reemplace la nueva.
- Un error nuevo sustituye el resultado anterior; no se presenta un éxito viejo como si perteneciera a una consulta reciente.
- No hay historial, TTL, polling, recarga al montar o persistencia. Recargar la página pierde el snapshot.

La hora es de recepción del cliente, no un timestamp del proveedor. La UI advierte que no garantiza frescura. Dashboard restaura el snapshot al volver de otra vista. Explorador solo lo lee al montarse; no hay sincronización bidireccional ficticia ni gestores globales adicionales.

`demoSelectionStore.ts` continúa exclusivo de demostración; sus fixtures y caché no alimentan HTTP.

## 7. Filtros y reglas conservadas

- Sector es texto conforme al contrato, no enum de fixtures.
- Dashboard HTTP permite editar actividad/sector, con sugerencias de las claves conocidas. No se presenta esa lista como catálogo oficial completo de ACTIVIDAD.
- El año es un entero editable e inicia en 2017; no queda limitado a 2017/2018.
- El año corresponde a FECHA_INICIO, no fecha de aprobación.
- Las sugerencias no crean equivalencias ambientales/económicas. Electricidad conserva el contexto económico Electricidad y Agua; TODOS usa PBI nacional. Transportes, Energia y actividades sin serie reconocida pueden no tener indicador económico.
- Los filtros editados son pendientes hasta la consulta manual; el snapshot mantiene su selección efectiva.
- En Explorador HTTP, sector/año están deshabilitados y se ofrece regresar a Dashboard. Estado y búsqueda son filtros locales.
- No se inventaron inversión, expedientes, regiones, tendencias, correlaciones o coordenadas.

## 8. Pruebas frontend ejecutadas

Fecha 07/11/2026, directorio `frontend/`:

| Comando | Resultado observado |
|---|---|
| `npm run typecheck` | Código 0, TypeScript de aplicación/configuración correcto. |
| `npm run test:api` | **143 tests, 143 pass, 0 fail, 0 cancelled, 0 skipped**. |
| `npm run build` | Código 0, typecheck y Vite 8.3.5; 48 módulos construidos. |

Es una ejecución actual de la suite completa seleccionada por `test:api`, no una suma de cifras históricas. Los nuevos casos usan transporte inyectado y fixtures artificiales pequeños; no consultan proveedores.

Cobertura relevante:

- Allowlist completo y conservación de configuraciones previas.
- Construcción de cliente sin GET y restricción de capacidades.
- Parámetros sector/año fuera del catálogo de demostración.
- Una consulta alimenta múltiples lecturas sin otro GET.
- SENACE ausente/BCRP disponible y SENACE disponible/BCRP ausente.
- Error de red sin reintentos o fallback y sustitución del resultado antiguo.
- Cancelación, copias defensivas y rechazo de publicaciones obsoletas.
- Vacío oficial frente a formato inválido, RUC textual y ausencia de coordenadas.
- Preservación de Monitor y Consultoras en la suite.

Las pruebas de funciones puras no demuestran por sí solas comportamiento React/StrictMode; la observación de navegador de esta fase complementa el caso de montaje y navegación ejecutado.

## 9. Preparación backend y seguridad

Antes del arranque se confirmó Oracle Java **17.0.12**, puertos libres y ausencia de nombres de overrides relevantes de Spring/servidor/propiedades externas/JVM/Maven en el proceso inspeccionado. `.mvn/` contenía wrapper y no se encontraron `jvm.config` o `maven.config` adicionales en la guarda de arranque.

El perfil [`application-integracion-local.yml`](../../backend/src/main/resources/application-integracion-local.yml) conserva bind `127.0.0.1:8081` y exclusiones de DataSourceAutoConfiguration, HibernateJpaAutoConfiguration y JpaRepositoriesAutoConfiguration. No se encontraron configuraciones productivas manuales DataSource/EntityManager en la búsqueda realizada. La configuración base mantiene `ddl-auto: update`: no debe arrancarse normalmente bajo una restricción de no acceso SQLite.

Tras autorización se leyeron únicamente SENACE_BASE_URL y SENACE_AUTH_KEY del archivo ignorado `backend/.env.apis.local`, mediante parser de pares clave/valor, sin ejecutarlo como PowerShell. Se validaron presencia no vacía, duplicados y destino HTTPS oficial. No se mostraron valores ni se propagaron a VITE, reportes, fixtures o argumentos.

La configuración se suministró al proceso backend temporal; no se importó permanentemente el archivo ni se modificó configuración Windows. El éxito de la consulta demuestra que la configuración fue utilizable en este flujo; no prueba por comparación que la API exija credencial en todos los casos.

## 10. Comandos y arranque real

Con JAVA_HOME y PATH locales al proceso apuntando a `C:\Program Files\Java\jdk-17`, desde `backend/`:

```powershell
.\mvnw.cmd -o '-Dspring-boot.run.profiles=integracion-local' '-Dspring-boot.run.arguments=--spring.config.location=classpath:/application.yml --spring.config.additional-location= --spring.config.import= --spring.profiles.include=' spring-boot:run
```

Maven offline generó modelos/interfaces OpenAPI y compiló fuentes y tests en `target/`. No ejecutó una suite Surefire durante este arranque. Se observaron las advertencias existentes de API obsoleta en SenaceTransport y operaciones de tipos no comprobadas en SenaceTransportTest; no se modificaron para este lote.

Logs: Java 17.0.12, PID 18972, un único perfil activo `integracion-local`, Tomcat en 8081 y arranque finalizado. No se observaron mensajes de inicialización DataSource/JPA en los logs revisados.

Desde `frontend/`, con las variables públicas indicadas:

```powershell
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort
```

Se utilizó Edge headless aislado con perfil temporal y depuración en loopback, sin el perfil personal del usuario ni instalaciones adicionales.

## 11. Consulta real autorizada y presupuesto

La revisión abrió primero Explorador sin snapshot: mostró espera, no mostró herramientas artificiales y produjo cero solicitudes backend. Después abrió Dashboard: filtros Hidrocarburos/2017, sin selector de escenarios y sin carga automática.

Se pulsó una vez el botón manual:

```http
GET http://127.0.0.1:8081/api/dashboard?sector=Hidrocarburos&anio=2017
```

| Destino | Presupuesto y evidencia |
|---|---|
| EcoInvest `/api/dashboard` | **1 GET observado directamente en CDP**, parámetros acordados. |
| SENACE CarteraProyectos | Máximo previsto 1 llamada lógica; código controlador/cliente realiza una consulta, sin reintentos en SenaceTransport. Metadatos acreditan respuesta API_DIRECTA_SENACE. No se instrumentó un contador de red upstream independiente. |
| BCRP PM04974AA anual 2017 | Máximo previsto 1 llamada lógica; se observó un log `Consultando BCRP: serie PM04974AA, año 2017` y resultado directo. No se trazaron paquetes/conexiones o posibles detalles de red del RestTemplate. |
| Explorador | **0 GET adicionales** al navegar o filtrar. |
| Monitor/Consultoras/datastream | No se consultaron. |

El presupuesto previsto era un GET local y hasta una solicitud por proveedor. La evidencia contabiliza directamente el GET navegador y la invocación BCRP registrada; para SENACE se apoya en el flujo inspeccionado y metadatos, sin afirmar una captura exhaustiva de todos los intercambios upstream.

No hubo reintentos manuales, consultas de control, barridos históricos o fallos provocados. No se alteraron límites, timeouts o políticas de proveedores.

## 12. HTTP y resultados reales

Respuesta observada en navegador:

| Dato | Valor |
|---|---|
| Status EcoInvest | 200 |
| Content-Type | application/json |
| Access-Control-Allow-Origin | `*`, configuración existente. |
| Estado del adaptador/UI | ready |
| Errores JavaScript instrumentados | 0 |

El estado 200 de esta tabla es el backend local, no un status upstream capturado directamente. El JSON se inspeccionó en memoria para agregados; no se guardó el cuerpo completo.

### SENACE

| Campo agregado | Valor informado |
|---|---:|
| Registros recibidos | 2567 |
| Aceptados | 2567 |
| Devueltos tras filtro | 48 |
| Aprobados | 40 |
| Fechas no evaluables | 0 |
| Fechas no evaluables en actividad | 0 |
| Proyectos con coordenadas numéricas en rango | 48 |

Metadatos: estado EXITO, origen API_DIRECTA_SENACE, filtrado APLICADO, cobertura NO_VERIFICADA, cuerpoCompleto true y advertencias vacías. RUC de los titulares permaneció String cuando estaba presente.

Son registros filtrados, no proyectos únicos certificados. Cuerpo completo no demuestra cobertura completa del dataset oficial. Coordenadas en rango no certifican CRS, datum, región o ubicación real. No se publicaron títulos, nombres, RUC o resoluciones individuales.

### BCRP

| Campo | Valor observado |
|---|---|
| Código | PM04974AA |
| Descripción | Producto bruto interno por sectores productivos (variación porcentual interanual) - Minería e Hidrocarburos - Hidrocarburos |
| Unidad | Variación porcentual interanual (%) |
| Periodicidad | ANUAL |
| Año solicitado / período efectivo | 2017 / 2017 |
| Valor recibido | -2.44780196733593 |
| Origen | API_DIRECTA_BCRP |
| Ámbito | SECTORIAL |

URL pública de fuente informada, sin autenticación:

`https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04974AA/json/2017/2017/esp`

El KPI mostró `-2.448 %` por formato visual de locale; el JSON/objeto conserva el número recibido dentro de la representación JavaScript. No se introdujo aritmética decimal exacta en el navegador ni se volvió a certificar toda Fase 2C BCRP.

## 13. Reutilización y comportamiento de ambas vistas

| Comprobación directa | Resultado |
|---|---|
| Explorador antes de Dashboard | Espera explicativa, sin GET ni escenario artificial. |
| Dashboard antes de pulsar | Sin GET; Hidrocarburos/2017 y estado no ocupado. |
| KPI tras consulta | 48 registros, 40 aprobados, -2.448 %. |
| Tabla Dashboard inicial | 3 filas DOM conforme a paginación existente. |
| Explorador tras navegar | 48 filas en lista y 48 elementos de marcador SVG. |
| Filtros sector/año en Explorador | Hidrocarburos/2017, deshabilitados. |
| Selección desde lista | 1 fila y 1 marcador con aria-pressed=true; ficha con 10 etiquetas. |
| Zoom | Se accionó Acercar y se comprobó presencia de viewBox de cuatro componentes; no se certificó exhaustivamente cada nivel. |
| Filtro local Aprobado | 40 filas y 40 elementos de marcador, sin GET adicional. |
| Volver a Dashboard | KPI y selección restaurados, sin GET. |
| Editar año a 2018 sin consultar | Mensaje de filtros pendientes visible. |
| Abrir Explorador después de esa edición | Año efectivo siguió siendo 2017, sin sincronización ficticia. |

Se instrumentaron cero solicitudes API adicionales durante estos recorridos. Los elementos de marcador pueden superponerse; 48 elementos SVG no garantizan 48 puntos visualmente separados. La lista sigue siendo la alternativa para acceder a todas las filas.

No se repitió una consulta real para comprobar ausencia de coordenadas o errores: esos casos se cubrieron en pruebas artificiales y funciones puras. Los filtros BCRP no se alteraron desde Explorador.

## 14. Responsive y evidencia visual no sensible

Medidas DOM de ambas vistas sobre la misma respuesta:

| Vista | Viewport | clientWidth | scrollWidth |
|---|---|---:|---:|
| Dashboard | 1440 × 900 | 1425 | 1425 |
| Dashboard | 768 × 1024 | 753 | 753 |
| Dashboard | 375 × 812 | 360 | 360 |
| Explorador | 1440 × 900 | 1425 | 1425 |
| Explorador | 768 × 1024 | 753 | 753 |
| Explorador | 375 × 812 | 360 | 360 |

No hubo desbordamiento horizontal global en estas medidas. Se revisaron seis imágenes en memoria: una por vista y resolución. Antes de capturarlas se ocultaron temporalmente en el DOM cuerpos de tabla/lista/ficha con registros identificables y se sustituyeron títulos SVG individuales por texto anonimizado. No se modificó código fuente con esas máscaras ni se guardaron capturas en archivos.

Se observaron composición clara, shell conservado, filtros adaptables, KPI y plano SVG offline. No se recuperaron prototipos Stitch de nuevo ni se realizó comparación píxel a píxel. Los encuadres iniciales no constituyen revisión exhaustiva de todos los paneles inferiores o accesibilidad. La máscara de privacidad limita deliberadamente la evaluación visual de texto real en filas/ficha; las interacciones se comprobaron antes por agregados y estados DOM.

El shell mantiene textos históricos como `Frontend en preparación` y `Sin conexión a proveedores` en su bloque estructural, aunque estas vistas ya pueden usar HTTP. No se editaron AppShell ni esos textos porque quedaron fuera del alcance aprobado. Conviene revisarlos en un encargo de coherencia global de etiquetas; no se atribuyen al estado efectivo de las consultas mostrado por las vistas.

## 15. Seguridad, SQLite y cierre operativo

- Credencial SENACE solo en el proceso backend temporal; no en Vite o bundle.
- No se publicaron URLs autenticadas, cuerpos completos, datos personales o excepciones externas con secretos.
- Perfil real con exclusiones de persistencia y único perfil activo confirmado.
- No se ejecutaron comandos SQLite ni se abrieron bases desde herramientas. No se observaron logs de arranque JPA/DataSource.
- Este aislamiento se apoya en configuración, inspección y antecedentes de prueba del perfil; no hubo trazado de syscalls que permita afirmar auditoría absoluta de todos los accesos del proceso.
- Edge se cerró y su perfil temporal autorizado se eliminó tras verificar la ruta dentro de TEMP.
- Vite y Spring Boot/Maven se detuvieron mediante interrupción de sus sesiones.
- La comprobación final no encontró procesos escuchando en 8081, 5173 ni el puerto temporal de depuración 58214.

## 16. Limitaciones y pendientes

1. Se validó una selección real: Hidrocarburos/2017. No se generaliza disponibilidad a otros sectores/años.
2. Los estados negativos, coordenadas ausentes y degradación independiente se probaron con transporte artificial; no se provocaron fallos oficiales.
3. La respuesta en memoria no se refresca automáticamente y no acredita frescura ni cobertura completa.
4. Los números JSON se interpretan como Number de JavaScript; siguen las advertencias/rechazos del adaptador existente para precisión y enteros inseguros.
5. No se probó rendimiento con miles de marcadores; los coincidentes pueden solaparse. No se añadió clustering, tiles ni cartografía externa.
6. No se ejecutó auditoría exhaustiva de teclado/WCAG o comparación integral con Stitch en 7C.3.
7. Los textos globales históricos del shell requieren revisión de coherencia autorizada si se decide retirarlos.
8. Monitor/Consultoras se preservaron mediante código y pruebas, sin nuevas consultas reales por asociación.
9. No existe conteo independiente de paquetes HTTP upstream; conservar la distinción de evidencia descrita en el presupuesto.

## 17. Estado final, reversión y siguiente paso

**7C.3 implementada y validada dentro del alcance autorizado:** Dashboard realizó una consulta HTTP real satisfactoria y Explorador reutilizó su respuesta sin duplicarla. No se certifica end-to-end exhaustivo de las cuatro vistas ni despliegue productivo.

Reversión operativa: detener Vite y seleccionar explícitamente `VITE_API_MODE=mock` con `VITE_HTTP_FEATURES` vacío. Reiniciar con esa configuración muestra demostración identificada. No descartar cambios Git ni usar mocks como fallback automático ante errores HTTP.

Siguiente propuesta, pendiente de encargo: preparar una revisión 7D de regresión/consistencia global, etiquetas del shell, accesibilidad y límites de volumen. Reutilizar las evidencias reales existentes antes de solicitar nuevas consultas. Autorizar por separado cualquier edición adicional, tests/build, servidores, credenciales, tráfico o capturas persistentes.

No se actualizaron README ni reportes históricos y no se inició 7D automáticamente.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE y BCRP con Java 17.0.12/Spring Boot 3.2.4 y React. Las fases Monitor 7C.1 y Consultoras 7C.2 permanecen vigentes. En 7C.3 se amplió el modo HTTP explícito a `monitor,consultoras,dashboard,explorador`, dejando datastream bloqueado y capacidades por operación. Dashboard no consulta al montar: requiere acción manual. Explorador no tiene transporte propio y lee la última respuesta Dashboard en memoria de sesión; sin respuesta previa muestra espera. No hay localStorage, caché histórica HTTP ni fallback a mocks.

Se creó dashboardSelectionStore.ts y se modificaron source.ts, App.tsx, modelos/vistas Dashboard/Explorador, CSS mínimo del filtro Dashboard y sus tres archivos de pruebas. No hubo cambios backend/OpenAPI/SQLite/Resilience4j/dependencias/Stitch ni commits. La ejecución actual pasó typecheck, 143 pruebas frontend y build.

Con perfil integracion-local y credencial solo backend se autorizó y observó un único GET manual Dashboard Hidrocarburos/2017. Respondió HTTP 200 ready: 2567 filas SENACE recibidas/aceptadas, 48 devueltas, 40 aprobadas, cero fechas no evaluables, cobertura NO_VERIFICADA y cuerpo completo informado. BCRP devolvió PM04974AA anual, período 2017, valor -2.44780196733593 y API_DIRECTA_BCRP. El presupuesto previsto fue una llamada lógica por proveedor; BCRP tuvo un log de invocación y SENACE se acredita por código/metadatos, sin trazado independiente upstream.

Explorador mostró 48 filas y 48 elementos SVG en rango, sin geografía/CRS certificados ni coordenadas añadidas. Selección lista/marcador/ficha y filtro local Aprobado (40) funcionaron. Navegar entre vistas, filtrar o editar año sin consultar no produjo otro GET: Explorador mantuvo el año efectivo 2017. Se midieron ambas vistas a 1440, 768 y 375 px sin desbordamiento global y se revisaron imágenes anonimizadas en memoria. No se guardaron cuerpos o capturas identificables. Procesos cerrados y perfil temporal eliminado.

No repetir esta consulta por rutina. Para un siguiente prompt, proponer alcance 7D explícito y mínimo: regresiones, accesibilidad/coherencia global, textos históricos del shell y decisiones de volumen; no dar por aprobadas nuevas consultas ni certificación end-to-end de todo EcoInvest.
