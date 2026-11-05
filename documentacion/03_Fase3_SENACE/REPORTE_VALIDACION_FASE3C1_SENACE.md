# EcoInvest — Validación integrada SENACE, Fase 3C.1

Fecha: **04/11/2026**, zona America/Lima (UTC−05:00).

Estado: **VALIDACIÓN INTEGRADA CON TRANSPORTE SIMULADO SATISFACTORIA**.

Este reporte fue creado con autorización específica después de implementar y ejecutar la prueba autorizada. No certifica acceso real a SENACE ni funcionamiento end-to-end de EcoInvest. La creación del documento no autoriza la Fase 3C.2.

## 1. Objetivo y alcance

Comprobar conjuntamente el flujo:

```text
MockMvc → EcoInvestController real → proxy AOP de SenaceClient real
  → SenaceTransport simulado → Jackson real → DTOs reales
  → SenaceMapper real → modelos OpenAPI → serialización MVC real
```

El dashboard utiliza BcrpClient simulado. Las respuestas de transporte son JSON artificiales pequeños con nombres de campos oficiales; no se utilizaron datasets reales, credenciales ni llamadas externas.

La etiqueta API_DIRECTA_SENACE en estos escenarios significa que el código interpreta una respuesta artificial como una respuesta oficial exitosa según su contrato. **No acredita una nueva consulta oficial**. Lo mismo se aplica a API_DIRECTA_BCRP en los fixtures económicos.

## 2. Estado inicial y antecedentes

Los cuatro lotes de Fase 3B.2 estaban implementados:

| Lote | Componentes disponibles | Referencia |
|---|---|---|
| 1 | DTOs, mapper puro y resultado interno | [Reporte Lote 1](REPORTE_IMPLEMENTACION_FASE3B2_LOTE1.md) |
| 2 | Transporte dedicado y configuración SENACE | [Reporte Lote 2](REPORTE_IMPLEMENTACION_FASE3B2_LOTE2.md) |
| 3 | SenaceClient con deserialización, mapeo y fallbacks | [Reporte Lote 3](REPORTE_IMPLEMENTACION_FASE3B2_LOTE3.md) |
| 4 | Controlador, contrato público, headers y Jackson | [Reporte Lote 4](REPORTE_IMPLEMENTACION_FASE3B2_LOTE4.md) |

El Lote 4 documentó 270 pruebas satisfactorias y nueve omitidas en su selección final del 03/11/2026. Esa ejecución es histórica e independiente de las 32 pruebas de este reporte; **no se suman para afirmar una suite actual de 302 casos**.

Se mantienen las decisiones D01–D19 del [reporte de decisiones](REPORTE_DECISIONES_FASE3B1_SENACE.md). No se introdujeron nuevas reglas de negocio. BCRP Fase 2C permanece cerrado en su alcance técnico: no se repitieron consultas ni se alteró su catálogo.

## 3. Componentes reales y simulados

| Componente | Tratamiento en esta prueba |
|---|---|
| EcoInvestController | Bean real, rutas MVC y modelos públicos existentes |
| SenaceClient | Bean real obtenido mediante proxy Spring AOP |
| SenaceMapper | Implementación real utilizada internamente por el cliente |
| ProyectoSenaceDto y ConsultoraSenaceDto | Deserialización real de los cuerpos artificiales |
| ResultadoConsultaSenace | Estados, motivos, conteos y cobertura reales |
| Jackson | ObjectMapper configurado por autoconfiguración selectiva de Boot |
| JsonNullableModule | Bean obtenido mediante la fábrica productiva AppConfig.jsonNullableModule() |
| Modelos OpenAPI | Clases generadas por Maven a partir del contrato vigente |
| CircuitBreaker | Registro, aspecto, política y transiciones reales |
| SenaceTransport | Único mock de la frontera HTTP SENACE; no se abre una sesión de transporte |
| BcrpClient | Mock con indicador decimal artificial o ausencia explícita |

No se simula SenaceClient. El cliente conserva su copia privada de Jackson y sus reglas estrictas; el test no sustituye el mapper ni prepara directamente resultados tipados para el controlador.

## 4. Configuración del contexto de pruebas

Se utiliza WebApplicationContextRunner con una lista explícita de autoconfiguraciones:

```java
AopAutoConfiguration.class
JacksonAutoConfiguration.class
HttpMessageConvertersAutoConfiguration.class
WebMvcAutoConfiguration.class
CircuitBreakerAutoConfiguration.class
```

La configuración interna declara exclusivamente los mocks de transporte/BCRP, el módulo JsonNullable, el cliente y el controlador. No importa AppConfig completo: reutiliza su método de creación del módulo y evita crear el RestTemplate compartido.

Se carga application.yml para extraer únicamente las propiedades resilience4j.* hacia una fuente de propiedades del contexto. No se cargan archivos .env ni se resuelven credenciales para construir el transporte. No se copia la configuración productiva de datasource/JPA al contexto.

Cada escenario obtiene un contexto nuevo, mocks nuevos y un registro de circuitos nuevo. WebApplicationContextRunner cierra el contexto al terminar. No se reinicia o fuerza manualmente el estado del circuito para aparentar una transición.

Referencias: [configuración del contexto, línea 357](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceIntegrationTest.java#L357), [aislamiento y proxy, línea 381](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceIntegrationTest.java#L381), [beans, línea 414](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceIntegrationTest.java#L414). Las líneas corresponden al archivo creado en esta fase; los fragmentos #L son orientativos según el visor Markdown.

## 5. Archivos creados o modificados

### Archivo de pruebas creado

`backend/src/test/java/com/ecoinvest/soa/integration/SenaceIntegrationTest.java`

El archivo no existía antes de la implementación. Se creó como prueba independiente y no se modificaron tests históricos.

### Documento creado posteriormente

`documentacion/03_Fase3_SENACE/REPORTE_VALIDACION_FASE3C1_SENACE.md`

No se modificaron archivos productivos, OpenAPI, AppConfig, dependencias, Java, SENACE/BCRP productivos, Resilience4j, SQLite, frontend, README ni reportes históricos. El estado Git ya contenía modificaciones y archivos sin seguimiento de fases anteriores; se preservaron y no se atribuyen a esta fase.

Maven escribió artefactos generados y resultados bajo backend/target/ conforme a la autorización. No se editaron manualmente.

## 6. Escenarios ejecutados

La clase contiene **32 casos ejecutados**, incluidos los casos expandidos de pruebas parametrizadas:

| Grupo / método | Casos | Comprobaciones principales |
|---|---:|---|
| projectsTraverseMvcClientDtoMapperAndGeneratedModel | 1 | Array → DTO → mapper → modelo público; ID superior a 2^53 sin pérdida, RUC textual, coordenadas BigDecimal, actividad original, default 2017, conteos y BCRP coherente |
| selectedYearIsStartYearAndNotApprovalYear | 1 | Año explícito 2018 filtra FECHA_INICIO y se envía a BCRP |
| emptySelectionsAndTemporalQualityRemainDistinct | 4 | Vacío oficial, filtro sin coincidencias, fecha inválida y fecha ausente; procedencia independiente de filas y conteos temporales |
| realFallbackPreservesBcrpWhenSenaceCannotBeUsed | 7 | ID fraccionario, fila null, JSON inválido, raíz incorrecta, cuerpo incompleto, error HTTP y configuración ausente; fallback AOP y continuidad BCRP |
| missingEconomicIndicatorDoesNotInventValueOrRemoveSenace | 1 | SENACE disponible sin indicador BCRP; ausencia económica sin constantes |
| consultantsKeepEightOriginalFieldsDuplicatesAndNoInventedIdentity | 1 | Ocho campos públicos, dos filas repetidas conservadas, fila opcional vacía, fechas originales, ausencia de identidad/estado ficticios |
| blankConsultantStringsAreOmittedAndDatesAreNotInterpreted | 1 | Blanco omitido y texto de fecha conservado sin vigencia derivada |
| consultantStatusCodesAndArrayRootFollowActualClientResults | 6 | Vacío, error externo, configuración, estructura inválida, HTML clasificado por transporte y respuesta incompleta; array raíz y 200/502/503 |
| genericJsonNullableUsesMvcObjectMapperForArraysAndObjects | 3 | Proyectos y consultoras como arrays; Visitas como objeto heredado, sin afirmar validación oficial; genérico sin filtro anual implícito y decimal preciso |
| genericEmptyArrayAndExplicitNullErrorsRemainDifferent | 4 | [] oficial frente a data:null en formato inválido, configuración y error externo; códigos HTTP |
| circuitOpensFromActualFailuresAndDashboardStillConsultsBcrp | 1 | Apertura real por fallos, rechazo sin nueva invocación al transporte y BCRP consultado en cada dashboard |
| unsafeExternalExceptionIsNotPublishedByRealFallbackOrMvc | 1 | Excepción artificial insegura no aparece en cuerpos ni headers de tres endpoints |
| monitorAndCorsUseMvcWithoutProviderCalls | 1 | Monitor sin interacción con proveedores, preflight GET aceptado/POST rechazado y headers de procedencia expuestos |
| **Total** | **32** | **Cero fallos, errores y omisiones** |

### Precisiones sobre los fixtures

- Proyectos incluye los 13 nombres oficiales, pero todos sus valores identificativos son artificiales.
- Las coordenadas contienen más precisión decimal que un double; se comprueba el JSON serializado con un lector BigDecimal, sin convertir el valor esperado a double.
- El ID 9007199254740993 verifica la conservación como Long y no como número flotante.
- El caso parcial entrega un documento [] sintácticamente interpretable junto con cuerpoCompleto=false. El cliente debe rechazarlo como evidencia incompleta, no tratarlo como vacío oficial.
- Algunos cuerpos malformados se inyectan como RECIBIDO completo deliberadamente para comprobar la defensa del cliente en esa frontera simulada. Esto no afirma que el transporte real acepte esos documentos.
- Visitas solo comprueba compatibilidad interna del camino heredado con un objeto artificial. Su esquema externo y disponibilidad siguen sin validarse oficialmente.

Referencias de métodos: proyectos línea 74; fechas/calidad línea 116; degradación línea 148; consultoras línea 195; genérico línea 254; AOP línea 302; sanitización línea 323; monitor/CORS línea 339 del archivo de pruebas.

## 7. Comandos, runtime y resultados

Directorio de ejecución:

```text
D:\Documentos\GitHub\Proyecto-SOA\backend
```

Comandos autorizados y ejecutados en un único proceso PowerShell, con detención si fallaba la compilación:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
.\mvnw.cmd -o '-Dtest=SenaceIntegrationTest' '-Decoinvest.test.local-http=false' test
```

JAVA_HOME y PATH se cambiaron únicamente dentro del proceso de ejecución, no permanentemente en el sistema.

| Evidencia | Resultado observado |
|---|---|
| java -version | Oracle Java 17.0.12, build 17.0.12+8-LTS-286 |
| Maven Wrapper | Apache Maven 3.9.9; Java 17.0.12; runtime C:\Program Files\Java\jdk-17 |
| Generación | OpenAPI Generator 7.4.0 procesó las cuatro operaciones y generó interfaces/modelos en target/ |
| Compilación explícita | BUILD SUCCESS; 27 fuentes; final 04/11/2026 06:17:30 −05:00; 8,826 s |
| Compilación de tests | Ocho fuentes de test compiladas durante test; ejecutar testCompile no significa ejecutar todas esas clases |
| Selección ejecutada | Solamente com.ecoinvest.soa.integration.SenaceIntegrationTest |
| Resultado Surefire | Tests run: 32; Failures: 0; Errors: 0; Skipped: 0 |
| Duración de la clase | 62,668 s en XML; consola redondeó 62,67 s |
| Maven test | BUILD SUCCESS; final 04/11/2026 06:20:02 −05:00; tiempo total informado 02:29 min |
| Código de salida | 0 |
| Runtime dentro de Surefire | java.version=17.0.12; java.vendor=Oracle Corporation; java.home=C:\Program Files\Java\jdk-17 |

La evidencia Surefire se consultó extrayendo únicamente el resumen y esas tres propiedades de runtime; no se publica el XML completo ni propiedades sensibles del entorno.

Artefactos locales de evidencia, regenerables y volátiles:

- [Resumen Surefire](../../backend/target/surefire-reports/com.ecoinvest.soa.integration.SenaceIntegrationTest.txt).
- [XML Surefire](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.integration.SenaceIntegrationTest.xml).
- [Contrato fuente](../../backend/src/main/resources/openapi.yaml).

## 8. Aislamiento de SQLite e infraestructura

Antes de ejercer cada escenario se comprobó que el contexto no contiene beans de:

```java
DataSource.class
EntityManager.class
EntityManagerFactory.class
ServletWebServerFactory.class
RestTemplate.class
```

Las aserciones pasaron en los 32 casos. Los beans SenaceTransport y BcrpClient se verificaron como mocks; SenaceClient y controlador se verificaron como no mocks, y el cliente como proxy AOP.

No se usa SpringBootTest, SoaApplication, escaneo amplio ni autoconfiguración JPA/datasource/servidor. MockMvc trabaja con servlet de prueba en memoria y no abre un puerto. No se iniciaron servidores HTTP locales, procesos auxiliares de prueba o clientes externos activos. Maven/Surefire sí utiliza sus procesos/JVM habituales como parte de la ejecución autorizada.

No se ejecutaron comandos SQLite ni se abrió la base mediante el contexto. La evidencia de aislamiento es estructural y mediante aserciones del contexto; no se realizó una auditoría de sistema operativo de todos los posibles accesos a archivos.

La propiedad ecoinvest.test.local-http=false se mantuvo en el comando como protección del alcance acordado; la selección -Dtest limita la ejecución a la nueva clase. **El modo offline de Maven por sí solo no impide llamadas HTTP desde un test**: aquí la ausencia de red proviene del diseño y de las dependencias simuladas.

## 9. AOP, fallbacks y Jackson

### Resilience4j

- Las llamadas MVC llegan al bean SenaceClient administrado por Spring y envuelto por AOP.
- Los escenarios de fallo comprueban el contador de llamadas fallidas del circuito, además de la respuesta MVC. No se llama directamente al fallback para simular interceptación.
- La prueba de apertura toma el mínimo entre minimumNumberOfCalls y slidingWindowSize de la configuración efectiva, según la ventana de conteo vigente. Con la política actual se observaron cinco fallos ERROR_HTTP seguidos de CIRCUITO_ABIERTO.
- El estado OPEN se alcanza por las llamadas fallidas reales del código, sin transición forzada.
- La siguiente solicitud es rechazada sin nueva llamada al transporte y el contador de llamadas no permitidas aumenta a uno.
- BCRP simulado continúa consultándose en cada operación, incluida la rechazada por el circuito SENACE.
- Configuración ausente se distingue por motivo, pero sigue contabilizando un fallo lógico según la implementación vigente. Las métricas del circuito no equivalen a una prueba de salud externa.

No se volvió a probar la recuperación temporal OPEN → HALF_OPEN → CLOSED en esta clase; no era un escenario obligatorio de este lote. No se atribuye a estas pruebas la recuperación documentada históricamente en otras fases.

### Jackson y contrato generado

- Se ejercita el ObjectMapper usado por los conversores MVC del contexto, con JsonNullableModule registrado como bean mediante la fábrica productiva.
- El endpoint genérico serializa data como array u objeto sin envoltorio incorrecto {present:true}.
- Los errores mantienen la propiedad data con null explícito; el vacío oficial conserva [].
- Los opcionales sin valor se omiten donde lo define el contrato; no se inventan valores para evitar null.
- Se preservan coordenadas y números genéricos BigDecimal, así como ID entero Long y RUC String.
- El lector BigDecimal del test solo inspecciona el JSON ya producido; no sustituye la configuración de serialización MVC.
- OpenAPI se regeneró mediante Maven; no se tocaron manualmente los modelos generados.

## 10. Problemas y correcciones

La compilación y la primera ejecución autorizada de la selección **no presentaron fallos**. No se detectó un defecto productivo reproducible en los escenarios cubiertos y no se aplicaron correcciones productivas.

Se observaron advertencias no fatales:

1. Uso de API obsoleta en SenaceTransport.
2. Operaciones genéricas sin comprobación en SenaceTransportTest durante la compilación de tests históricos.
3. Aviso de JVM sobre class sharing al utilizar instrumentación de pruebas.
4. Aviso informativo de OpenAPI Generator sobre JAVA_POST_PROCESS_FILE no configurado.

No se amplió el alcance para resolver estos avisos. La revisión Git no detectó un nuevo archivo fuente ajeno al test autorizado; las modificaciones preexistentes siguieron presentes. No se hicieron commits.

## 11. Limitaciones del entorno y de la evidencia

- No se usó SENACE real ni su credencial: no se acredita autenticación, TLS, disponibilidad, volumen actual o contenido oficial vigente.
- BcrpClient está simulado: los indicadores de estos casos no son nuevas observaciones económicas.
- No se ejecutaron las suites históricas completas; 32 es el total de esta selección actual.
- El transporte real no se ejercita aquí. Sus límites, deadlines, redirecciones y streaming dependen de su implementación y validaciones anteriores; esta fase prueba la reacción del cliente/controlador a sus resultados simulados.
- La configuración mínima no demuestra por sí sola el arranque productivo completo, la carga real de variables locales/nube o el registro de todos los beans de la aplicación.
- Cobertura NO_VERIFICADA se conserva: ni un cuerpo completo ni un HTTP 200 demuestran la cobertura total del dataset oficial.
- Se comprueban campos y tipos representativos del contrato, no se ejecuta un validador independiente de todas las combinaciones posibles del esquema OpenAPI.
- No se ejecutó frontend ni un navegador. CORS se verificó con solicitudes MockMvc, no en un despliegue real.
- No se leyó configuración local con credenciales ni se escribieron cuerpos externos o secretos en fixtures/reportes.

## 12. Estado final de Fase 3C.1

| Criterio | Resultado |
|---|---|
| Compilación y generación OpenAPI con JDK requerido | Verificado mediante ejecución |
| Controlador + cliente + DTOs + mapper reales en el mismo flujo | Verificado con transporte simulado |
| Jackson/JsonNullable en conversores MVC | Verificado |
| Proxy AOP, fallback, apertura y rechazo | Verificado |
| Dashboard conserva BCRP ante errores SENACE | Verificado con BCRP simulado |
| Cuatro rutas, consultoras array, genérico flexible, monitor | Verificado en los escenarios de la clase |
| Ausencia de infraestructura persistente y servidor | Verificada mediante aserciones del contexto |
| Consultas oficiales nuevas o end-to-end productivo | No ejecutado; pendiente independiente |

**Fase 3C.1 queda completada dentro de su alcance de validación integrada con respuestas artificiales y transporte simulado.** No se declara EcoInvest certificado end-to-end ni SENACE real completamente validado por este resultado.

## 13. Propuesta de Fase 3C.2 — SENACE real controlado

Esta propuesta no se ejecutó y requiere un encargo posterior con autorizaciones específicas.

### Objetivo

Ejercitar el transporte SENACE y cliente reales en un contexto aislado, manteniendo fuera el arranque productivo, SQLite y proveedores adicionales. Contrastar estructura, mapeo y metadatos con respuestas oficiales actuales.

### Orden sugerido

1. Leer este reporte, los reportes de Lotes 2–4, las decisiones aprobadas y la validación oficial 3A vigente.
2. Diseñar el mecanismo de ejecución antes de escribir archivos. Identificar configuración mínima y presupuesto de dos GET como máximo inicial, uno por recurso prioritario, sin reintentos automáticos.
3. Solicitar autorización para usar únicamente SENACE_BASE_URL y SENACE_AUTH_KEY desde el entorno o configuración local aprobada. Verificar presencia sin divulgar valores; no asumir carga automática de .env.
4. Solicitar aprobación de HTTP y volumen. Mantener rutas oficiales sin /data.json, TLS/hostname normales, redirecciones desactivadas y límites vigentes del transporte: 6 MiB, conexión 5 s, inactividad 10 s y presupuesto total 25 s, con sus limitaciones documentadas.
5. Ejecutar proyectos y consultoras solo si se autoriza. Registrar HTTP, tipo/estado de contenido, cuerpo completo, conteos y metadatos seguros. No publicar respuestas completas o datos personales innecesarios.
6. Contrastar campos, fechas de inicio, ausencia de métricas no disponibles y conservación de filas de consultoras. No imponer los totales históricos 2.567/15.015 como constantes o expectativas actuales.
7. Documentar resultados reales, fallos y límites mediante autorización independiente. Si aparece un defecto productivo, demostrarlo y solicitar permiso de corrección antes de editar producción.

### Archivos de referencia

- [SenaceTransport.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceTransport.java).
- [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java).
- [SenaceMapper.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceMapper.java).
- [ResultadoConsultaSenace.java](../../backend/src/main/java/com/ecoinvest/soa/client/ResultadoConsultaSenace.java).
- [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java).
- [AppConfig.java](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java).
- [application.yml](../../backend/src/main/resources/application.yml), sin leer archivos de credenciales por asociación.
- [Validación oficial SENACE](REPORTE_FASE3A_VALIDACION_SENACE.md).

### Criterios de aceptación propuestos

- Procedencia sustentada por una ejecución oficial real y separada de mocks.
- Errores y cuerpos incompletos tratados como ausencia/error, sin datos de sustitución.
- Compatibilidad del mapeo con la respuesta observada, sin afirmar cobertura total.
- Ausencia de acceso a SQLite, servidor completo o consultas BCRP repetidas.
- Respeto del presupuesto autorizado y de la confidencialidad de configuración/logs.
- Reporte que distinga resultados positivos, restricciones del proveedor y pruebas no realizadas.

No se propone editar de nuevo los cuatro lotes por rutina ni modificar BCRP, Resilience4j o frontend. Un end-to-end del dashboard con arranque real seguirá requiriendo planificación y autorización propias.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE y BCRP con Java 17, Spring Boot 3.2.4, Maven Wrapper, OpenAPI y Resilience4j. BCRP Fase 2C permanece cerrada en su alcance técnico; no se repitieron consultas. Fase 3B.2 terminó la implementación de DTOs/mapper, transporte dedicado, cliente real y contrato/controlador. Las decisiones D01–D19 siguen vigentes.

El 04/11/2026 se completó Fase 3C.1 mediante el nuevo archivo backend/src/test/java/com/ecoinvest/soa/integration/SenaceIntegrationTest.java. No se modificó producción. Se usaron EcoInvestController y SenaceClient reales, proxy AOP real, DTOs y mapper reales, Jackson con JsonNullableModule de la fábrica productiva y modelos generados OpenAPI. Solo SenaceTransport y BcrpClient fueron mocks.

La compilación offline terminó BUILD SUCCESS a las 06:17:30 −05:00. La ejecución offline de SenaceIntegrationTest terminó BUILD SUCCESS a las 06:20:02 −05:00 con 32 casos, cero fallos, cero errores y cero omitidos. Surefire confirmó Oracle JDK 17.0.12 en C:\Program Files\Java\jdk-17; Maven fue 3.9.9. No sumar estas pruebas a selecciones históricas para presentar una suite nueva no ejecutada.

Se verificaron filtros por año de FECHA_INICIO/default 2017 y actividad normalizada, precisión Long/BigDecimal, conteos, fechas no evaluables, vacío oficial frente a filtro sin coincidencias, ausencia honesta de indicadores, consultoras con ocho campos y duplicados conservados, respuestas 200/502/503, headers/CORS y genérico con arrays/objeto/null. El circuito SENACE abrió por fallos reales del código con transporte simulado y rechazó una nueva llamada sin invocar transporte; el dashboard siguió usando BCRP simulado. La recuperación temporal no fue probada nuevamente en esta clase.

Los 32 contextos mínimos comprobaron ausencia de DataSource, EntityManager, EntityManagerFactory, ServletWebServerFactory y RestTemplate. No hubo HTTP real, sockets de prueba, servidor completo, acceso SQLite, instalaciones, frontend o commits. La ausencia de infraestructura se acredita mediante diseño/aserciones del contexto, no por una auditoría de archivos del sistema operativo. Las respuestas y valores económicos fueron artificiales; no acreditan autenticación ni datos externos nuevos.

La Fase 3C.1 queda completada en ese alcance. No existe certificación end-to-end. Siguiente prompt recomendado: diseñar Fase 3C.2 de SENACE real controlado en contexto aislado con transporte/cliente reales, sin SQLite o arranque completo, máximo inicial de dos GET oficiales y permisos separados para archivos, credencial, HTTP/volumen, Maven y reporte. Mantener los límites de transporte, TLS y redirecciones actuales; no codificar los totales históricos como expectativas fijas. No iniciar automáticamente la fase ni modificar producción sin un defecto demostrado y autorización.
