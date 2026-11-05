# EcoInvest — Fase 3B.2, Lote 4: integración pública SENACE y OpenAPI

**Fecha:** 03/11/2026, America/Lima.  
**Estado:** implementación pública compilada y validada mediante pruebas aisladas; validación externa y end-to-end pendientes.  
**Runtime acreditado:** Oracle JDK 17.0.12; Maven Wrapper 3.9.9; Spring Boot 3.2.4.  
**Ejecución final:** BUILD SUCCESS; 279 casos contabilizados por Surefire: **270 satisfactorios, 9 omitidos, 0 fallos y 0 errores**.  
**Alcance documental de esta ejecución:** crear únicamente este reporte después de autorización. No se actualizaron índice o antecedentes ni se inició una fase siguiente.

## 1. Objetivo del Lote 4

Completar la integración pública del controlador con los resultados tipados SENACE de los Lotes 1–3, actualizar OpenAPI como fuente de verdad y aplicar la política de degradación y errores aprobada.

Se mantienen cuatro rutas GET y sus raíces JSON. El dashboard continúa con BCRP cuando SENACE falla; consultoras conserva array incluso en errores; el genérico conserva objeto envolvente; el monitor no consulta proveedores.

Este reporte distingue implementación, mocks, contextos mínimos y consultas reales. No hubo HTTP a SENACE o BCRP durante el lote. Un origen API_DIRECTA_SENACE dentro de un fixture no constituye una observación oficial nueva.

Referencias:

- [Reglas del repositorio](../../AGENTS.md).
- [Decisiones D01–D19 y política HTTP](REPORTE_DECISIONES_FASE3B1_SENACE.md).
- [Diseño Fase 3B.1](REPORTE_DISENO_FASE3B1_SENACE.md).
- [Validación SENACE Fase 3A](REPORTE_FASE3A_VALIDACION_SENACE.md).
- [Lote 1: modelos y mapper](REPORTE_IMPLEMENTACION_FASE3B2_LOTE1.md).
- [Lote 2: transporte y configuración](REPORTE_IMPLEMENTACION_FASE3B2_LOTE2.md).
- [Lote 3: integración del cliente](REPORTE_IMPLEMENTACION_FASE3B2_LOTE3.md).
- [Cierre técnico BCRP Fase 2C](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md).

Las líneas citadas corresponden al código revisado al cerrar el lote y pueden variar después.

## 2. Estado inicial

Los componentes existentes ya proporcionaban:

| Componente | Estado anterior |
|---|---|
| DTOs y SenaceMapper | Trece campos de proyectos, ocho de consultoras, originales, precisión y reglas aprobadas |
| ResultadoConsultaSenace | Filas y metadatos independientes, calidad, ausencia y conteos conocidos/desconocidos |
| SenaceTransport | Transporte específico, destinos restringidos, auth_key en memoria, límites y cancelación |
| SenaceClient | Consultas tipadas y adaptadores compatibles; simulación automática retirada |
| Circuit breaker senace | Proxy/fallbacks y rechazo comprobados en contexto mínimo del Lote 3 |
| Controller | Todavía usaba adaptadores de listas; los fallos impedían llegar a BCRP y el vacío se etiquetaba SIMULADO |
| OpenAPI | Cuatro rutas, pero sin contrato público SENACE tipado/metadatos y con descripciones históricas de simulación |

El Lote 3 documentó una selección previa con 219 casos satisfactorios y nueve omitidos. No se suma esa ejecución al resultado actual. Las cantidades reales anteriores de los datasets tampoco se usan como constantes de negocio.

La actualización pública estaba pendiente: el cliente ya distinguía errores, pero el controlador antiguo podía producir 500 mediante adaptadores y perder procedencia en listas vacías.

## 3. Decisiones aplicadas y autorizaciones

Se aplicaron D01–D19 y la política HTTP aprobada sin redefinir reglas de negocio:

- Año de inicio de FECHA_INICIO, no aprobación; default 2017.
- Actividad original y normalización de búsqueda ya realizada por el mapper.
- Conteos de registros filtrados evaluables, no proyectos únicos certificados.
- Consultoras sin deduplicación, identidad artificial o vigencia derivada.
- Ausencia honesta y metadatos independientes de filas.
- Sin simulación automática, nuevas equivalencias económicas o cambios de catálogo BCRP.
- Rutas y raíces conservadas; nuevos códigos de error previamente aprobados.
- Uso de los componentes existentes sin otro transporte o Service.

### Autorizaciones utilizadas

1. Se aprobó editar controlador, OpenAPI y su test, incluida la propuesta de cinco headers SENACE.
2. Se aprobó generación/compilación y pruebas offline con HTTP local desactivado.
3. Tras un fallo demostrado de serialización JsonNullable se solicitó y aprobó ampliar a AppConfig.java y AppConfigTest.java. No se modificaron antes de esa aprobación.
4. Se aprobó la prueba adicional de configuración y se incluyó en la selección final.
5. Después de comunicar resultados se autorizó crear este reporte.

Los errores iniciales y las correcciones se conservan en la sección 12. No se presentan todas las primeras ejecuciones como satisfactorias.

## 4. Archivos modificados

### Alcance inicial

| Archivo | Cambio |
|---|---|
| [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java) | Consumo tipado, degradación independiente, modelos públicos, política HTTP, headers y metadatos |
| [openapi.yaml](../../backend/src/main/resources/openapi.yaml) | Modelos y enums públicos, campos opcionales, headers, respuestas 502/503 y corrección de descripciones |
| [EcoInvestControllerTest.java](../../backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java) | 59 casos MockMvc, fixtures y contexto MVC mínimo para preflight |

### Ampliación expresa

| Archivo | Cambio mínimo aprobado |
|---|---|
| [AppConfig.java](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java) | Bean JsonNullableModule para serializar el tipo generado en la respuesta genérica, líneas 14–19 |
| [AppConfigTest.java](../../backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java) | Verificación de registro automático, arrays/objetos/null y precisión decimal; conserva test del RestTemplate |

El registro del módulo utiliza jackson-databind-nullable 0.2.6, que ya era una dependencia del proyecto. No se instaló una dependencia nueva ni se cambió el POM en este lote.

### Revisión de alcance

Inicialmente se registraron hashes de 18 archivos protegidos. Dos pasaron después al alcance adicional aprobado, AppConfig y AppConfigTest. Los otros **16 archivos conservaron sus hashes** después de la ejecución final.

Permanecieron intactos BcrpClient, SenaceClient, SenaceTransport, SenaceMapper, ResultadoConsultaSenace, DTOs, configuración de proveedores/SQLite/Resilience4j y demás fuentes fuera del alcance. El esquema fuente IndicadorBcrp se comparó con el anterior y conserva su contenido, normalizando solo finales de línea para la comparación.

git diff --check no encontró problemas. Git todavía muestra cambios previos de otros lotes: se preservaron y no se atribuyen a este. No hubo commits, cambio de rama o edición manual de target/.

El presente Markdown es una creación posterior e independiente. No se actualizaron README, reportes históricos o skills.

## 5. Contrato OpenAPI resultante

### 5.1. Rutas y parámetros

| Método/ruta | Entrada conservada | Raíz de respuesta |
|---|---|---|
| GET /api/dashboard | sector requerido; anio opcional, default 2017 | Objeto DashboardResponse |
| GET /api/consultoras | Sin parámetros nuevos | Array de ConsultoraSenacePublica |
| GET /api/senace/datastream | nombre opcional, default CarteraProyectos | Objeto DatastreamSenaceResponse |
| GET /api/estado-servicios | Sin parámetros nuevos | Objeto EstadoServiciosResponse |

Se conserva la versión API declarada 1.0.0 y el generador 7.4.0. No se añadió un endpoint, una ruta versionada o un parámetro incompatible.

### 5.2. Modelos públicos

| Tipo | Función |
|---|---|
| DashboardResponse | Conserva indicador BCRP, fuentes y métricas conocidas; añade consultaSenace |
| ProyectoSenacePublico | Campos verificados y alias compatibles; sin inversión, moneda, región o expediente inventados |
| ConsultoraSenacePublica | Ocho conceptos reales, opcionales cuando no existe valor |
| ConsultaSenace | Estado, origen, filtrado, cobertura, completitud, motivo, conteos y advertencias |
| ConteosSenace | Conteos Integer opcionales; ausencia no equivale a cero |
| FuentesDashboard | Procedencia por servicio y sector/año consultados |
| DatastreamSenaceResponse | Envolvente datastream/origen/data/consulta y recursoValidado; mensajes/lista de recursos seguros en errores |
| IndicadorBcrp | Esquema económico anterior conservado |
| EstadoServiciosResponse | Métricas lógicas, circuito y aclaración de disponibilidad no comprobada |

Se generan además OrigenSenace, EstadoConsultaSenace, EstadoFiltradoSenace, CoberturaSenace, MotivoSenace y AdvertenciaSenace como enums.

La generación final escribió 15 tipos entre modelos/enums, más ApiApi y ApiUtil. Maven compiló 27 fuentes con release 17. Las firmas Java generadas de consultoras y genérico ahora retornan modelos públicos; el controlador se adaptó en el mismo lote. No se encontraron otros consumidores de ApiApi que requirieran cambios en el repositorio inspeccionado.

### 5.3. Opcionales y precisión

- total, aprobados y proyectos se omiten cuando SENACE no proporciona datos utilizables.
- Una selección conocida vacía devuelve total/aprobados 0 y proyectos []; su estado distingue vacío oficial de filtro sin coincidencias.
- Campos normalizados ausentes se omiten; no se publican strings fijos para rellenarlos.
- ID usa int64/Long; RUC permanece String; coordenadas y valores económicos conservan BigDecimal.
- fechaInicio es texto original; anio es año de inicio derivado. La fecha normalizada se usa internamente, sin presentarla como aprobación.
- ConsultaSenace utiliza inclusión NON_NULL en su clase generada para omitir motivo/completitud desconocidos. La anotación procede de x-class-extra-annotation en OpenAPI, no de una edición de target/.
- data del genérico admite JSON variable y null explícito en error. El generador lo representa mediante JsonNullable<Object>; se usa su API generada data(Object) y el módulo de Jackson correspondiente.

OpenAPI documenta los límites del esquema heredado: data opaco de seis recursos no implica validación de sus campos o éxito administrativo.

## 6. Cambios en el controlador

El constructor conserva las mismas dependencias: SenaceClient, BcrpClient y CircuitBreakerRegistry. No se añadió una capa ni se duplicó transporte, mapeo temporal o normalización sectorial.

### Entrada tipada y defensa

consultarSeguro, desde la línea 162, ejecuta una sola llamada al cliente. Admite resultados tipados o la excepción segura del cliente y transforma errores inesperados en ausencia sin conservar causas externas.

Para publicar datos oficiales exige estado EXITO/VACIA, origen API_DIRECTA_SENACE y evidencia de cuerpo completo. Un resultado producido solo por un mapper, sin evidencia HTTP, no basta para publicar datos como oficiales.

Los errores internos de la respuesta tipada no se convierten en una lista exitosa vacía. Los fallos imprevistos tampoco publican sus mensajes, URI o cuerpos.

### Proyectos públicos

proyectoPublico, desde la línea 315, utiliza las propiedades normalizadas del mapper:

- id, titular, ruc, unidadProyecto, tipo, actividad, fechaInicio, estado, descripcion, longitud, latitud y resolucion.
- titulo y proyecto son alias de TITULO_PROYECTO.
- sector es alias de ACTIVIDAD, no traducción al catálogo económico BCRP.
- anio deriva de FECHA_INICIO y origen es metadato de consulta.

No se inventan valores para propiedades que estaban en los mocks antiguos. Los campos originales continúan disponibles en los DTO internos; el contrato público omite valores normalizados sin contenido según D10.

### Metadatos independientes

consultaPublica transforma el resultado interno a modelos generados. Incluye:

```text
estado, origen, estadoFiltrado, cobertura, cuerpoCompleto opcional,
motivo opcional, conteos, advertencias
```

Los conteos conservan recibidos, aceptados, devueltos, aprobados y fechas no evaluables cuando son conocidos. No se crean ceros a partir de null. NO_EVALUABLE significa que al menos una fecha relevante no pudo evaluarse; no implica necesariamente que toda la selección sea inútil.

## 7. Degradación ante fallos SENACE

obtenerDashboard, desde la línea 67:

1. Conserva el sector recibido y default de año 2017.
2. Llama una sola vez a consultarProyectos(sector,anio).
3. Consulta BCRP independientemente del resultado SENACE.
4. Construye indicadorBcrp y fuentes con estados separados.
5. Publica filas y métricas SENACE solo si existe resultado utilizable.
6. Devuelve HTTP 200 completo o degradado, con consultaSenace que explica la diferencia.

### Comportamiento por escenario

| SENACE | Datos del dashboard |
|---|---|
| Datos válidos | Proyectos filtrados, total/aprobados de esa selección y metadatos |
| Array oficial vacío | Proyectos [], métricas conocidas 0, origen oficial, VACIA |
| Sin coincidencias | Proyectos [], métricas 0 de selección, EXITO/SIN_COINCIDENCIAS y recibidos conocido |
| Fechas no evaluables | Selección evaluable y conteos correspondientes, con advertencias y limitación explícita |
| Error, falta de configuración, formato inválido o parcial no utilizable | Sin total/aprobados/proyectos inventados; origen NO_DISPONIBLE y motivo seguro |

Un cero con fechas no evaluables describe registros devueltos/evaluados, no demuestra que no existan proyectos globalmente. La cobertura sigue NO_VERIFICADA.

Los metadatos y el valor BCRP se conservan cuando están disponibles aunque SENACE falle. El controlador mantiene BigDecimal, no selecciona años vecinos ni inventa series. Si BCRP también falla inesperadamente, su ausencia se informa de forma independiente sin borrar un resultado SENACE válido.

La orquestación sigue siendo secuencial. Esta corrección evita abortar antes de BCRP, pero no demuestra un SLA de latencia ni introduce concurrencia. Los límites propios del transporte y del DNS nativo siguen documentados en el Lote 2.

## 8. Exposición de consultoras

obtenerConsultoras, líneas 109–116, consume consultarConsultoras una sola vez y devuelve una lista de modelos públicos, nunca un objeto envolvente.

Los ocho campos son:

| Propiedad pública | Campo oficial |
|---|---|
| razonSocial | RAZON_SOCIAL |
| actividad | ACTIVIDAD |
| subsector | SUBSECTOR |
| tipoSolicitud | TIPO_SOLICITUD |
| resolucion | RESOLUCION |
| fechaRd | FECHA_RD |
| inicioVigenciaRd | INICIO_VIGENCIA_RD |
| finVigenciaRd | FIN_VIGENCIA_RD |

Se conservan filas repetidas y textos originales no vacíos. Los vacíos/ausentes normalizados se omiten; no se convierten en vigencia indefinida. No aparecen ID oficial, RUC, registro, nivel o estado inventados.

La procedencia se comunica en headers, independiente de la primera fila. Un array oficial vacío continúa siendo oficial. Todos los errores definidos conservan cuerpo [].

## 9. Endpoint genérico

consultarDatastream, desde la línea 119, conserva la consulta del cliente y su objeto envolvente. No aplica filtros anuales adicionales ni llama al método de proyectos para obtener metadatos mediante otra petición.

### Envolvente

```text
datastream, origen, data, consulta, recursoValidado
```

En errores puede añadir mensaje fijo y datastreamsSoportados. data es null explícito y no contiene el cuerpo externo. No se refleja el mensaje arbitrario del cliente o del proveedor.

- Para CarteraProyectos y ConsultorasAmbientales, data conserva array y nombres oficiales que entrega el cliente.
- El genérico de proyectos conserva registros de distintos años; no aplica el default 2017 del dashboard.
- GastoEspecifica, GastoFuente, GastoGenerica, SolicitudAcceso, Visitas y Reclamos conservan el camino heredado del Lote 3 y data objeto.
- recursoValidado indica la evidencia histórica de ruta/estructura en Fase 3A, no disponibilidad actual o cobertura.
- Los seis recursos heredados siguen con recursoValidado false. No se verificaron externamente en este lote.

El controlador transforma únicamente metadatos seguros y enums reconocidos. Una estructura incoherente, un motivo arbitrario o una raíz incompatible produce error de integración sin reenviar información externa. Un nombre desconocido se expresa como NO_RECONOCIDO y no se devuelve literalmente una entrada que pudiera contener una URL autenticada.

Se conservan los límites anteriores: JSON de un recurso no investigado no basta para certificar su éxito semántico o reconocer todos sus posibles errores encapsulados en HTTP 200. No se inventó un esquema para resolver esa incertidumbre.

## 10. Monitor SOA

obtenerEstadoServicios, desde la línea 155, consulta únicamente CircuitBreakerRegistry.

Conserva servicio, estado de circuito, tasaFallosPorcentaje, llamadasFallidas, llamadasExitosas y respaldoLocal NO_IMPLEMENTADO. Añade aclaraciones:

```text
alcanceMetricas = LLAMADAS_LOGICAS
disponibilidadExterna = NO_VERIFICADA
```

El estado CLOSED/OPEN no constituye una petición de salud al proveedor. Un fallo lógico de configuración puede incrementar métricas sin HTTP externo. La validación técnica histórica de BCRP tampoco garantiza disponibilidad en el instante de consultar este monitor.

No se cambiaron nombres, políticas o propiedades de circuit breakers. No se consultan SENACE, BCRP o SQLite desde este endpoint.

## 11. Política HTTP, headers y CORS

### Matriz pública

| Situación | Dashboard | Consultoras | Genérico |
|---|---|---|---|
| Datos utilizables | 200, objeto con datos | 200, array | 200, objeto con data |
| Vacío oficial | 200, métricas 0 y origen oficial | 200, [] oficial | 200, data [] y VACIA |
| Configuración ausente/inválida o indisponibilidad | 200 degradado, BCRP independiente | 503, [] | 503, objeto con data null |
| Circuito abierto | 200 degradado | 503, [] | 503, data null |
| Formato/estructura/codificación inválidos | 200 degradado | 502, [] | 502, data null |
| Respuesta parcial no utilizable | 200 degradado | 503, [] | 503, data null |

La respuesta 401 del proveedor no se reenvía como autenticación fallida del usuario EcoInvest. Se traduce a indisponibilidad con motivo seguro. El genérico conserva el tratamiento de recurso no admitido como ausencia segura, sin introducir un código 400 nuevo para ese caso.

### Headers de consultoras

| Header | Valor |
|---|---|
| X-Senace-Origen | API_DIRECTA_SENACE o NO_DISPONIBLE |
| X-Senace-Estado | EXITO, VACIA, ERROR_EXTERNO, FORMATO_INVALIDO o PARCIAL |
| X-Senace-Cobertura | NO_VERIFICADA |
| X-Senace-Motivo | Enum técnico seguro, solo si existe |
| X-Senace-Cuerpo-Completo | true/false si hay evidencia; se omite si es desconocida |

La anotación de consultoras expone esos cinco headers al navegador. Se mantuvo el origen wildcard que ya existía en la clase; no se añadieron orígenes, credenciales CORS o métodos arbitrarios. Los tests verificaron exposición en éxito/error, preflight GET y rechazo de POST.

No se incorporan URI autenticada, claves, cookies, causas o cuerpos externos a esos headers.

## 12. Pruebas ejecutadas, correcciones y resultados

### 12.1. Runtime y comandos

Directorio:

```text
D:\Documentos\GitHub\Proyecto-SOA\backend
```

En procesos temporales, sin cambiar configuración permanente del sistema:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests compile
```

Primera selección de pruebas autorizada:

```powershell
.\mvnw.cmd -o '-Dtest=EcoInvestControllerTest,SenaceClientTest,SenaceMapperTest,SenaceTransportTest' '-Decoinvest.test.local-http=false' test
```

Después de autorizar la ampliación de configuración y corregir los fallos se repitió compile y se ejecutó:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -o '-Dtest=EcoInvestControllerTest,SenaceClientTest,SenaceMapperTest,SenaceTransportTest,AppConfigTest' '-Decoinvest.test.local-http=false' test
```

Java, Maven y Surefire confirmaron Oracle 17.0.12, java.home=C:\Program Files\Java\jdk-17 y Maven 3.9.9. Surefire confirmó ecoinvest.test.local-http=false. No hubo descargas o instalación de dependencias.

### 12.2. Historia de intentos, sin ocultar fallos

| Finalización registrada, -05:00 | Operación | Resultado y causa |
|---|---|---|
| 03/11/2026 23:08:37 | Primera compilación | BUILD FAILURE; un error porque setData esperaba JsonNullable<Object>, no Object |
| 03/11/2026 23:11:17 | Compilación corregida | BUILD SUCCESS tras usar la API generada data(Object) |
| 03/11/2026 23:11:56 | Primera selección de tests | BUILD FAILURE; 277 contabilizados, 16 fallos, 1 error y 9 omitidos |
| 03/11/2026 23:26:32 | Compilación con correcciones finales | BUILD SUCCESS; salida 0; 13,774 s |
| 03/11/2026 23:27:20 | Selección final con AppConfigTest | BUILD SUCCESS; salida 0; 28,426 s; 270 satisfactorios y 9 omitidos |

Los fallos iniciales se clasificaron y corrigieron así:

1. **API generada de data:** se usó el método fluent data(Object), que envuelve correctamente JsonNullable. No se editó el modelo generado.
2. **Serialización productiva real:** sin JsonNullableModule, el campo aparecía como {present:true}. Se solicitó ampliar AppConfig y se añadió su bean; Spring Boot registra los beans Module en su ObjectMapper. Se comprobó con contexto Jackson mínimo y con las respuestas MockMvc.
3. **Motivo opcional:** la anotación como extensión junto al ref no se trasladó al campo generado. Se añadió NON_NULL a ConsultaSenace mediante x-class-extra-annotation en OpenAPI y se regeneró.
4. **Fixture de BCRP:** Mockito devuelve Map vacío por defecto; un test que esperaba ausencia de respuesta no había configurado null explícito. Se corrigió el fixture, sin cambiar el cliente económico o inventar otra regla de ausencia.
5. **Preflight en MockMvc standalone:** la infraestructura standalone de esta versión no registraba el adapter de PreFlightHandler. El test de preflight utiliza ahora un contexto MVC mínimo con EnableWebMvc y mocks, sin autoconfiguración productiva. No se fingió la respuesta CORS manualmente.

La primera ejecución fallida no debe confundirse con la final. El resultado final no es una suma de suites históricas.

### 12.3. Desglose final

| Clase/grupo | Satisfactorios | Omitidos | Fallos | Errores |
|---|---:|---:|---:|---:|
| EcoInvestControllerTest | 59 | 0 | 0 | 0 |
| SenaceClientTest | 83 | 0 | 0 | 0 |
| SenaceMapperTest | 53 | 0 | 0 | 0 |
| SenaceTransportTest, sin sockets | 73 | 0 | 0 | 0 |
| SenaceTransportTest.LocalHttp | 0 | 9 | 0 | 0 |
| AppConfigTest | 2 | 0 | 0 | 0 |
| **Total** | **270** | **9** | **0** | **0** |

Surefire informa 279 porque incluye nueve omitidos. No declarar 279 pruebas superadas. Las 53 del mapper se ejecutaron realmente en esta selección; no se suman como otros 53 históricos adicionales.

El XML del grupo de transporte vuelve a agrupar 82 casos bajo LocalHttp: 73 de la clase exterior y nueve omitidos del grupo local. El XML exterior declara cero. Se revisaron classname y skipped para evitar duplicar o atribuir HTTP a esos casos.

### 12.4. Fronteras y cobertura

Los 59 casos del controlador cubren los escenarios solicitados, incluyendo parametrizaciones:

- Dashboard con ambos servicios, degradado, vacío oficial, filtro sin coincidencias y fechas no evaluables.
- Configuración ausente, formato inválido, parcialidad y excepciones inesperadas sanitizadas.
- Una llamada SENACE por operación; BCRP continúa ante fallo SENACE.
- Conteos conocidos, omisión de métricas desconocidas, origen independiente de primera fila y cobertura.
- Precisión decimal de coordenadas y ambos valores económicos; metadatos BCRP, PBI nacional y sectores sin serie aprobada.
- Consultoras con ocho campos, duplicados conservados, opcionales y array vacío oficial.
- Arrays de error 502/503 y headers seguros, incluido false frente a completitud desconocida.
- Exposición CORS en éxito/error, preflight GET y rechazo de POST.
- Genérico con arrays de proyectos/consultoras, años distintos sin filtro, seis objetos heredados, vacío y errores sin cuerpo externo.
- Rechazo de metadatos arbitrarios o raíz incompatible, sanitización de entrada desconocida.
- Monitor sin llamadas a clientes y distinción de métricas lógicas.
- Conservación de rutas, sector requerido y default anual.

AppConfigTest conserva la prueba del RestTemplate Simple con 5.000 ms de conexión/lectura. Su otro caso usa solo JacksonAutoConfiguration y AppConfig, con un builder explícito: comprueba registro automático del módulo, data array/objeto/null y precisión BigDecimal del modelo BCRP. Ese único caso incluye varias formas de serialización; no se contabilizan como pruebas Maven separadas.

### 12.5. Aislamiento

- MockMvc no abrió un servidor HTTP.
- El preflight usa AnnotationConfigWebApplicationContext con configuración MVC explícita y mocks, sin EnableAutoConfiguration o component scanning del proyecto. Se comprobó ausencia de DataSource.
- El contexto de configuración utiliza únicamente autoconfiguración Jackson y los beans explícitos, también sin DataSource.
- Los tests AOP heredados mantienen su contexto mínimo con transporte mock.
- Los nueve casos de HTTP local no se ejecutaron.
- No se inició Spring Boot completo, no se llamó a proveedores y no se abrió SQLite.

### 12.6. Artefactos

- [EcoInvestControllerTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.controller.EcoInvestControllerTest.xml).
- [SenaceClientTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceClientTest.xml).
- [SenaceMapperTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceMapperTest.xml).
- [Transporte y grupo local XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceTransportTest$LocalHttp.xml).
- [Transporte exterior XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceTransportTest.xml).
- [AppConfigTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.config.AppConfigTest.xml).

Estos artefactos son volátiles y pueden contener propiedades del entorno. No compartir XML completos sin revisión. Este reporte conserva solamente resultados, runtime y límites pertinentes.

## 13. Compatibilidad y limitaciones

### Compatibilidad conservada

Se conservaron rutas, métodos GET, nombres de parámetros, default 2017, alias de proyectos con origen documentado, precisión y metadatos BCRP. No se modificó el catálogo económico.

Los cambios observables de ausencia, campos opcionales y 502/503 corresponden a la política aprobada. Un consumidor antiguo que dependiera de valores del mock debe adaptarse; no se afirma compatibilidad con consumidores externos desconocidos. La regeneración cambia tipos Java de respuestas aunque la raíz HTTP siga igual.

### Límites técnicos

1. Las respuestas de proveedor de este lote fueron simuladas; no hay observación real del flujo público nuevo.
2. Los contextos mínimos no acreditan arranque de la aplicación completa o comportamiento de datasource/JPA en producción.
3. El dataset completo, su cobertura y la estabilidad global de identificadores no se certifican por recibir un array.
4. Los seis recursos heredados siguen sin validación externa de rutas/esquemas/errores semánticos actuales.
5. Las fechas y la vigencia mantienen las decisiones/limitaciones de fases anteriores; no se derivan estados administrativos.
6. El módulo JsonNullable se registró como soporte específico de un tipo; los tests verifican precisión económica y RestTemplate, no toda posible combinación futura de módulos Jackson.
7. No se midió rendimiento, concurrencia, latencia de orquestación completa o bloqueo DNS nativo. Se conservan los límites documentados del transporte.
8. La configuración local de credenciales no se importó ni validó aquí; las variables deben llegar al proceso de despliegue.
9. Las pruebas MockMvc del controlador utilizan clientes simulados. La integración completa MVC–cliente–transporte no se certifica solo por sumar pruebas de componentes.

Persisten avisos no fatales de API obsoleta en SenaceTransport, genéricos no comprobados en su test, class sharing de la JVM y postprocesamiento OpenAPI no configurado. No se instalaron herramientas o dependencias para eliminarlos.

Solo se revisaron metadatos de backend/ecoinvest.db: 0 bytes y LastWriteTime 12/10/2026 22:19:49. No se abrieron archivos SQLite o tablas. No se implementó persistencia/fallback por encontrar ese archivo.

## 14. Estado final del Lote 4

| Criterio | Resultado confirmado |
|---|---|
| Controller consume resultados tipados | Implementado y probado mediante mocks |
| Dashboard conserva BCRP ante fallo SENACE | Comprobado en escenarios MockMvc |
| Procedencia y calidad independientes de filas | Expuestas en consultaSenace, envolvente y headers |
| Array raíz de consultoras | Conservado en éxito, vacío y errores definidos |
| Genérico y recursos heredados | Conservados con esquema flexible y límites de verificación explícitos |
| Monitor sin proveedores | Comprobado mediante ausencia de interacción con clientes |
| OpenAPI fuente y modelos generados | Generación y compilación satisfactorias, sin editar target/ |
| Serialización JsonNullable/CORS | Fallos iniciales corregidos y comprobados en contextos aislados |
| Regresión fuera del alcance | Sin cambios no autorizados; 16 hashes protegidos iguales |
| Selección final | 270 satisfactorios, nueve omitidos; BUILD SUCCESS |
| Flujo real externo/end-to-end | Pendiente |

El Lote 4 queda completado dentro de su alcance de implementación y pruebas aisladas. La creación de este reporte no inicia más trabajo productivo.

## 15. Evaluación del cierre de Fase 3B.2

Puede considerarse completada la implementación prevista por los cuatro lotes y su validación aislada:

1. Modelos y mapeo puro.
2. Transporte y configuración.
3. Integración del cliente.
4. Contrato y controlador públicos.

El resultado es un backend con componentes conectados en código, política pública implementada y evidencia de generación/compilación y selección de tests. No quedan los bloqueos públicos transitorios del Lote 3 en los escenarios cubiertos: el dashboard ya continúa hacia BCRP y ya no etiqueta un vacío oficial como SIMULADO.

Este cierre es **del alcance de implementación/pruebas aisladas**, no una certificación real completa de SENACE o EcoInvest. No se acredita end-to-end por MockMvc, configuración Jackson o tests AOP separados. Las validaciones posteriores deben conservarse como pendientes independientes y con autorización.

## 16. Validaciones externas y end-to-end pendientes

Antes de declarar operativo el flujo real completo se propone:

- Diseñar una prueba integrada aislada del controlador con cliente real de código, mapper y configuración Jackson, sustituyendo únicamente transporte. Así se valida la unión de componentes que hoy se prueba por fronteras separadas.
- Verificar después, con permiso específico, consultas SENACE reales limitadas usando la configuración vigente sin exponerla, sin iniciar contexto productivo o SQLite por accidente.
- Contrastar mapeo público con los datos obtenidos, cuidando nulos, tipos, fechas, volumen, procedencia y cobertura no verificada.
- Planificar un end-to-end separado para dashboard y resto de rutas, con decisión explícita sobre persistencia/arranque. No usar un arranque general para comprobar incidentalmente un cambio pequeño.
- Validar configuración del proceso local/nube y CORS del futuro frontend cuando corresponda a su fase.

No se concede aquí autorización HTTP, presupuesto de peticiones, arranque, modificación SQLite o despliegue. No se propone repetir BCRP Fase 2C sin una nueva necesidad demostrada; las primeras pruebas integradas pueden mantener BCRP simulado.

## 17. Siguiente paso recomendado

Compartir este reporte con ChatGPT para preparar un prompt de validación integrada controlada. Como orden incremental, comenzar con el flujo completo aislado y transporte simulado, y evaluar luego la validación SENACE real con presupuesto aprobado. La denominación de una eventual Fase 3C sería una propuesta de continuidad, no una fase iniciada por este documento.

El siguiente prompt debería leer controlador, contrato, SenaceClient, ResultadoConsultaSenace, transporte y AppConfig actuales, además de este reporte y las decisiones. No recrear los cuatro lotes ni reintroducir simulación productiva.

Debe delimitar archivos, aislamiento de datasource, comandos JDK 17.0.12/Maven offline y las pruebas concretas antes de ejecutar. Cualquier consulta real requiere su autorización y credencial configurada localmente, nunca copiada a reportes.

Al crear este Markdown no se ejecutaron nuevos tests, Maven, HTTP o servicios, ni se modificaron otros documentos. Se conserva la historia y sus fallos iniciales.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó Fase 3B.2 Lote 4 en su alcance de implementación y pruebas aisladas, con Java 17/Oracle JDK 17.0.12, Spring Boot 3.2.4 y OpenAPI Generator 7.4.0. Los cuatro lotes SENACE están implementados: DTOs/mapper, transporte, cliente y ahora exposición pública. BCRP Fase 2C permanece cerrado; su cliente, catálogo y esquema IndicadorBcrp no se modificaron.

Se editaron inicialmente EcoInvestController.java, openapi.yaml y EcoInvestControllerTest.java. Una incompatibilidad demostrada de JsonNullable requirió autorización adicional para AppConfig.java y AppConfigTest.java: se registró JsonNullableModule ya disponible en las dependencias, sin cambiar RestTemplate, timeouts o precisión económica. Los otros 16 archivos protegidos conservaron hashes. No se hicieron commits ni se actualizaron README o reportes históricos.

Se conservan GET /api/dashboard, /api/consultoras, /api/senace/datastream y /api/estado-servicios. Dashboard consume una sola consulta tipada SENACE, publica consultaSenace y sigue hacia BCRP ante errores. En error SENACE omite métricas/proyectos desconocidos; vacío oficial y filtro sin coincidencias conservan procedencia oficial y estados diferentes. Año sigue siendo inicio de FECHA_INICIO con default 2017. No se inventan inversión, región, expediente o equivalencias económicas.

Consultoras mantiene array raíz con ocho campos, sin RUC/ID/registro/nivel/estado ficticios, deduplicación o vigencia derivada. Datos/vacío oficial producen 200; indisponibilidad/configuración/circuito abierto/parcial no utilizable 503 []; formato inválido 502 []. Headers: X-Senace-Origen, X-Senace-Estado, X-Senace-Cobertura, X-Senace-Motivo y X-Senace-Cuerpo-Completo. Los dos últimos son opcionales según evidencia. Se exponen por CORS sin ampliar el origen wildcard previo o permitir POST.

El genérico mantiene objeto datastream/origen/data y agrega consulta/recursoValidado. data es array para proyectos/consultoras y no aplica año 2017 al genérico; los seis recursos heredados conservan objeto y rutas previas, sin afirmar validación oficial. En error data null y motivo seguro, sin reenviar cuerpo/mensaje externo. Monitor solo lee circuitos y aclara LLAMADAS_LOGICAS, disponibilidadExterna NO_VERIFICADA y respaldoLocal NO_IMPLEMENTADO.

OpenAPI tipa modelos públicos, metadatos, conteos, headers y 502/503, y corrige descripciones antiguas. Los opcionales se omiten; data usa JsonNullable<Object> generado para contenido flexible/null explícito. Se utiliza data(Object), no se modifica target/. El módulo Jackson evita la salida incorrecta {present:true}. Se conservan BigDecimal, ID Long y RUC String.

Historia verificable del 03/11/2026: primera compilación falló por setData(Object) frente a JsonNullable; tras usar la API generada compiló. Primera selección de pruebas falló con 16 fallos y un error sobre 277 casos, nueve omitidos: serialización sin módulo, motivo null por anotación de ref, fixture Mockito con Map vacío y preflight standalone sin adapter. Se corrigieron con permiso y sin ocultar esa historia.

Ejecución final: compile offline BUILD SUCCESS a las 23:26:32 -05:00; test final BUILD SUCCESS a las 23:27:20. Surefire contabilizó 279: 270 satisfactorios, nueve omitidos, cero fallos/errores. Desglose actual: 59 controlador, 83 cliente, 53 mapper, 73 transporte sin sockets y dos configuración. Los nueve HTTP locales estuvieron desactivados. No sumar pruebas de reportes históricos. Java/Maven y Surefire confirmaron JDK 17.0.12 y Maven 3.9.9.

Las pruebas incluyen MockMvc, un contexto MVC mínimo para preflight, AOP mínimo heredado y Jackson mínimo para registrar el módulo, sin Spring Boot completo o DataSource. No hubo HTTP real, servidor HTTP, SQLite, instalaciones o despliegue. Persisten avisos no fatales de API obsoleta/genéricos del transporte y límites anteriores de DNS/latencia/cobertura.

Puede cerrarse Fase 3B.2 como implementación y validación aislada. No declarar integración externa o end-to-end completa. Siguiente prompt recomendado: diseñar primero una prueba integrada aislada de MVC–cliente–mapper–Jackson con transporte simulado y sin SQLite; después evaluar consultas SENACE reales acotadas y end-to-end separados, siempre con permisos específicos. No avanzar automáticamente, reimplementar lotes, cambiar BCRP/Resilience4j o comenzar frontend.
