# Estado actual y continuidad técnica de EcoInvest

**Fecha de auditoría:** 29 de octubre de 2026, zona America/Lima.  
**Repositorio:** `D:\Documentos\GitHub\Proyecto-SOA`.  
**Última fase funcional iniciada:** 3A.2 — Autenticación y muestras reales SENACE.  
**Situación:** revisión textual del manual completada; consultas autenticadas y muestras pendientes. BCRP cerrado dentro de su alcance técnico específico. No hay certificación end-to-end.

Este reporte se creó mediante autorización expresa del usuario. Durante esta auditoría solo se realizaron lecturas locales y consultas de estado Git. No se ejecutaron Maven, pruebas, HTTP o servidores; no se abrió SQLite, leyó .env, imprimieron credenciales, modificó código o reorganizaron carpetas. La única creación autorizada es este archivo; se comprobó previamente que no existía.

## 1. Resumen ejecutivo

EcoInvest es un backend SOA de integración SENACE + BCRP. No implementa OEFA ni cálculo ESG. Los cuatro módulos de producto previstos son Dashboard Analítico, Explorador Geográfico, Registro de Consultoras Ambientales y Monitor de Salud SOA. React + TypeScript es el stack previsto para el frontend, pero la carpeta frontend/ está vacía: no existe una aplicación React implementada que pueda auditarse o declararse integrada.

BCRP dispone de catálogo anual aprobado, selección exacta del año, validación JSON explícita, BigDecimal, metadatos y ausencia sin cifras ficticias. Las evidencias locales conservan consultas reales satisfactorias del catálogo para años seleccionados y una prueba AOP aislada. No es necesario repetir esas fases como si estuvieran pendientes ni modificar BCRP o Resilience4j para continuar SENACE.

SENACE realiza solicitudes HTTP pero descarta la respuesta de proyectos y consultoras y devuelve listas construidas con datos fijos SIMULADO. No hay parser oficial, mapeo de campos reales o paginación implementada. La ayuda oficial y el manual se revisaron, pero no se han obtenido muestras autenticadas de ambos recursos en las actividades observadas.

La documentación está organizada en documentacion/ por fases. Los reportes históricos no se reescribieron; varios contienen diagnósticos posteriormente resueltos. Este documento integra código, evidencias de pruebas anteriores y actividades de la conversación que todavía no se habían incorporado al reporte SENACE.

### Convención de evidencia

| Etiqueta | Significado |
|---|---|
| CÓDIGO | Verificado por inspección del archivo actual; no implica ejecución satisfactoria |
| REPORTE | Afirmación histórica documentada; debe interpretarse con su fecha y alcance |
| PRUEBA PREVIA | Resultado anterior de Surefire leído localmente y/o ejecución histórica documentada |
| SESIÓN | Actividad observada en la conversación, sin artefacto permanente equivalente necesariamente |
| NO VERIFICADO | Falta evidencia o ejecución apropiada |
| AUTORIZACIÓN PENDIENTE | No ejecutar sin aprobación específica para la siguiente acción |

## 2. Arquitectura y tecnologías

### 2.1. Estructura real

```text
Proyecto-SOA/
  backend/
    pom.xml
    mvnw / mvnw.cmd
    .mvn/wrapper/maven-wrapper.properties
    src/main/java/com/ecoinvest/soa/
      SoaApplication.java
      client/BcrpClient.java
      client/SenaceClient.java
      config/AppConfig.java
      controller/EcoInvestController.java
    src/main/resources/application.yml
    src/main/resources/openapi.yaml
    src/test/java/com/ecoinvest/soa/       # cinco clases de prueba permanentes
    target/                              # artefactos generados y evidencias históricas
    ecoinvest.db                          # archivo observado de 0 bytes; no abierto
    HELP.md                              # ayuda generada; no es el contrato técnico
  frontend/                              # cero archivos observados
  documentacion/
    README.md
    00_Auditoria/
    01_Fase1/
    02_Fase2_BCRP/
    03_Fase3_SENACE/
    ESTADO_ACTUAL_ECOINVEST.md
  .github/ / .idea/ / .git/
  .env                                   # no leído
```

No existe pom.xml u openapi.yaml en la raíz. El contrato correcto está bajo backend/src/main/resources/. backend/HELP.md es documentación de plantilla: contiene enlaces a Spring Boot 4.1.1, pero eso no representa la versión del proyecto. El POM actual es la evidencia de versión.

### 2.2. Stack confirmado

| Componente | Estado/evidencia |
|---|---|
| Java declarado | 17, [pom.xml](../backend/pom.xml), línea 17 |
| Spring Boot | 3.2.4, POM línea 8 |
| Spring Cloud | 2023.0.1, POM línea 18 |
| Maven Wrapper | Distribución Maven 3.9.9 en [.mvn/wrapper/maven-wrapper.properties](../backend/.mvn/wrapper/maven-wrapper.properties) |
| OpenAPI Generator | 7.4.0; generador spring, interfaceOnly, useSpringBoot3 y skipDefaultInterface, POM líneas 107–126 |
| Springdoc | 2.5.0, POM líneas 55–60 |
| Jackson | Databind 2.15.4 observado en classpath histórico Surefire; no se resolvieron dependencias nuevamente |
| Resilience4j | Starter Spring Cloud declarado; Boot 3 y Spring 6 2.1.0 observados en classpath histórico |
| AOP | Spring AOP 6.1.5 y AspectJ Weaver 1.9.21 observados en evidencia histórica |
| Persistencia declarada | Spring Data JPA, sqlite-jdbc 3.45.2.0 y dialectos Hibernate; no equivalen a un fallback implementado |
| Frontend | React + TypeScript previsto; no hay package.json, servicios o componentes en frontend/ |

El archivo release del JDK local `C:\Program Files\Java\jdk-17` confirma Oracle 17.0.12. Las pruebas reales y AOP históricas registran ese runtime. Esta auditoría no ejecutó java ni Maven y no certifica qué JVM elegiría una nueva consola sin configurar JAVA_HOME/PATH. Mantener JDK 17.0.12 en procesos temporales autorizados; Java 21 sigue pospuesto.

### 2.3. Flujo y contrato

```text
Consumidor futuro
  → API OpenAPI / EcoInvestController
    → SenaceClient → plataforma oficial SENACE → actualmente listas SIMULADO
    → BcrpClient → API oficial BCRP → validación anual / BigDecimal / ausencia
```

[EcoInvestController.java](../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), línea 22, implementa ApiApi generado. OpenAPI Generator produce interfaces en com.ecoinvest.api y modelos en com.ecoinvest.model. No editar target/ manualmente.

[AppConfig.java](../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java), líneas 13–17, configura RestTemplate compartido con conexión y lectura de cinco segundos. No hay una política explícita de proxy o redirecciones en ese archivo. [application.yml](../backend/src/main/resources/application.yml), línea 2, declara puerto 8081.

## 3. Estado por fases

Orden lógico basado en contenido y evidencias, no en nombres de archivo o fechas de modificación. Los diagnósticos SENACE de octubre 18 se intercalaron con el cierre posterior BCRP.

| Fase/subfase | Objetivo | Estado actual | Evidencia | Pendientes |
|---|---|---|---|---|
| Auditoría inicial, 18/10 | Identificar el repositorio real | Diagnóstico completado; histórico | [Auditoría](00_Auditoria/AUDITORIA_PROYECTO_SOA.md), REPORTE | No reutilizar como estado actual los defectos luego corregidos |
| Fase 1, 18/10 | Diagnóstico y estabilización inicial | Lote implementado y validado en su alcance | [Diagnóstico](01_Fase1/REPORTE_FASE1_ECOINVEST.md), [Implementación 1A](01_Fase1/REPORTE_IMPLEMENTACION_FASE1A.md); 18 pruebas históricas | No equivale a SENACE real o dashboard end-to-end |
| Fase 1B | Cierre denominado así por el usuario | No hay reporte separado 1B; no inventar otro lote | El diagnóstico Fase 2 aclara la nomenclatura; el reporte 1A conserva el cierre validado | Mantener distinción de nombres |
| Fase 2, 18/10 | Diagnóstico BCRP | Completada como diagnóstico histórico | [Diagnóstico BCRP](02_Fase2_BCRP/REPORTE_FASE2_BCRP.md) | Sus códigos y carencias iniciales ya no describen el cliente actual |
| Fase 2A, 18/10 | Validar series y unidades oficiales | Completada dentro de muestras documentadas | [Validación oficial](02_Fase2_BCRP/REPORTE_FASE2A_VALIDACION_BCRP.md) | No ampliar catálogo sin validación específica |
| Fase 2B, 18/10 | Implementar integración anual trazable | Implementada; 75 pruebas locales satisfactorias | [Implementación](02_Fase2_BCRP/REPORTE_IMPLEMENTACION_FASE2B.md), CÓDIGO y PRUEBA PREVIA | Suite completa no repetida aquí sobre JDK 17 |
| Fase 2C HTTP/TLS, 18/10 | Cliente real y contenido inesperado | Verificada para observaciones seleccionadas | [Diagnóstico HTTP](02_Fase2_BCRP/REPORTE_FASE2C_DIAGNOSTICO_HTTP.md), Surefire | Disponibilidad externa permanente no certificada |
| Fase 2C AOP, 18/10, reporte 19/10 | Interceptación y recuperación | Verificada en contexto mínimo | [Resilience4j](02_Fase2_BCRP/REPORTE_FASE2C_RESILIENCE4J.md), 2 pruebas previas | Contexto productivo integral separado |
| Fase 2C catálogo, 19/10 | Cinco series restantes | Cerrada en alcance técnico específico | [Cierre BCRP](02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md), 6 casos previos | Dashboard end-to-end independiente |
| Fase 3 diagnóstico, 18/10 | Identificar simulación y plan SENACE | Diagnóstico completado; integración real pendiente | [Diagnóstico SENACE](03_Fase3_SENACE/REPORTE_FASE3_SENACE.md), CÓDIGO | Muestras, parser y reglas reales |
| Fase 3A documental, 19/10 | Portal y ayuda oficiales | Parcial | [Validación SENACE](03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md), 4 GET documentados | Campos, autenticación efectiva y cobertura |
| Organización documental, 29/10 | Mover reportes y corregir enlaces | Completada | [Índice](README.md), 11 reportes organizados y 91 enlaces locales comprobados | Mantener índice en futuras fases |
| Fase 3A.2, revisión del manual | Confirmar instrucciones de uso de API | Lectura textual completada; subfase global parcial | SESIÓN: HTTP 200 del PDF de 16 páginas, lectura en memoria | Capturas no examinadas visualmente; compatibilidad de rutas y límites sin resolver |
| Fase 3A.2, muestras | Obtener proyectos y consultoras reales | No ejecutada; autorización actual pendiente | No hay muestras/artefactos de datos en el trabajo observado | Credencial disponible, decisión de volumen y 2 GET autorizados |
| Fase 3B | Mapeo e implementación real | No iniciada | Plan histórico, sin implementación | Primero evidencia real y aprobación |
| Frontend / end-to-end | Cuatro módulos y consolidación integral | Pendiente | frontend/ vacío; sin ejecución integral acreditada | Después de backend SENACE estable |

## 4. Última fase ejecutada

### 4.1. Última actividad funcional anterior a esta auditoría

**Fase iniciada:** 3A.2. **Subtarea efectivamente completada:** consulta y extracción textual del manual oficial SENACE, después de organizar la documentación. La lectura completa textual no significa que toda la subfase de autenticación/muestras haya terminado.

La última llamada HTTP observada fue un GET sin credenciales a:

```text
https://datosabiertos.senace.gob.pe/Api/Manual/Guía-uso-Portal-datos-abiertos.pdf
```

Resultado de la SESIÓN: HTTP 200, application/pdf, 1.635.367 bytes, 16 páginas, versión 1.0 del 19/08/2020. Se utilizó pypdf disponible localmente para leerlo en memoria, con TLS normal, sin reintentos o redirecciones automáticas, sin archivos y sin Maven.

Hallazgos textuales:

- Páginas 13–15: formulario de solicitud de uso de API, obtención de token web y remisión al ejemplo de uso.
- Página 14: vigencia documental de un año; no demuestra vigencia de una credencial concreta hoy.
- La evidencia conjunta con /Api/Help respalda auth_key para el token del formulario de API; no acredita intercambiabilidad de cualquier JWT de sesión.
- No se encontraron parámetros REST de paginación, límite o filtros en el texto extraído. Los controles de filas descritos corresponden a la grilla del portal.
- Las capturas embebidas no fueron examinadas visualmente. No se afirmó haber resuelto con ellas encabezados adicionales o diferencias de /data.json.

**Archivos creados/modificados por esa consulta:** ninguno. **Pruebas Java ejecutadas:** ninguna. La evidencia está en la conversación; el reporte SENACE histórico todavía dice que el manual estaba pendiente y no fue actualizado. Este documento registra la continuidad sin reescribirlo.

### 4.2. Estado de autorización al pausar el trabajo funcional

Se propusieron dos GET de datos, sin /data.json, con credencial de entorno y corte local a 256 KiB por respuesta. La última solicitud de autorización para ese plan no recibió un sí antes de este nuevo encargo de auditoría. Por tanto, las consultas no se ejecutan bajo el presente prompt, que prohíbe HTTP.

Hubo autorizaciones en etapas anteriores para planes acotados, pero no se consumaron por ausencia de variable en los procesos comprobados y luego se volvió a solicitar aprobación en el contexto reorganizado. No convertir esas autorizaciones históricas en evidencia de ejecución ni en permiso implícito para continuar ahora.

**Disponibilidad actual de SENACE_AUTH_KEY:** NO VERIFICADA en esta auditoría. Los intentos anteriores de comprobar su presencia devolvieron no disponible. No se leyó su valor ni se inspeccionó .env. Si el entorno cambió, debe comprobarse únicamente presencia en una continuación autorizada.

### 4.3. Última tarea de Codex en esta ejecución

La tarea más reciente es esta auditoría de continuidad y la creación de ESTADO_ACTUAL_ECOINVEST.md. No debe confundirse con una implementación o prueba SENACE nueva.

## 5. Estado de las APIs

### 5.1. API pública EcoInvest

Contrato: [openapi.yaml](../backend/src/main/resources/openapi.yaml), líneas 9–79. Controlador: líneas 38–120.

| Método/ruta | Entrada | Estado por código |
|---|---|---|
| GET /api/dashboard | sector obligatorio en contrato; anio opcional, default 2017 | Consolida proyectos SENACE actualmente simulados e indicador BCRP; controlador aplica Hidrocarburos si sector nulo/vacío |
| GET /api/estado-servicios | Sin parámetros | Lee métricas de los breakers; respaldoLocal=NO_IMPLEMENTADO |
| GET /api/consultoras | Sin parámetros | Devuelve lista actual de consultoras SIMULADO |
| GET /api/senace/datastream | nombre opcional, default CarteraProyectos | Devuelve mapa externo genérico o fallback; esquema no validado |

El contrato incluye DashboardResponse con total, aprobados, proyectos, fuentes, variacionSeleccionada e indicadorBcrp opcional. Este último contiene codigoSerie, descripcion, unidad, periodicidad, sectorSolicitado, sectorEconomico, ambito, anioSolicitado, periodo, valor, origen, motivo y urlFuente. Los campos económicos ausentes se omiten mediante anotaciones generadas desde OpenAPI, no editando modelos manualmente.

Estas rutas están implementadas y tienen evidencia MockMvc previa; no se afirma que estén sirviendo peticiones en este momento. No se inició el servidor.

### 5.2. BCRP

Base pública sin autenticación empleada en las pruebas previas:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api/{codigo}/json/{anio}/{anio}/esp
```

| Sector aprobado | Código | Alcance | Observación real previa |
|---|---|---|---|
| AGRICULTURA | PM04969AA | Agrícola | 2018: 9.07731729270536 |
| PESCA | PM04971AA | Pesca | 2018: 47.6847546513897 |
| MINERIA / MINERÍA | PM04973AA | Minería metálica | 2018: -1.73797597703714 |
| HIDROCARBUROS | PM04974AA | Hidrocarburos | 2017: -2.44780196733593; 2018: -0.0345569324875896 |
| ELECTRICIDAD | PM04978AA | Electricidad y Agua | 2018: 4.39284781615264 |
| TODOS | PM04983AA | PBI nacional | 2018: 3.96935133550218 |

Unidad del catálogo aprobado: variación porcentual interanual (%), periodicidad anual. Los valores son observaciones históricas, no constantes implementadas ni promesas de respuesta actual. El JSON observado no incluía code independiente: se contrastaron URL y denominación. La unidad publicada proviene del catálogo aprobado, no de un campo independiente observado en cada JSON.

CÓDIGO: una serie por solicitud, cuerpo String, Jackson local con validación estructural, selección por nombre de año y BigDecimal. Transportes, Energía y sectores desconocidos producen SECTOR_NO_SOPORTADO sin sustituirlos por otra serie. No utiliza años vecinos ni cifras fijas. Valores nulos y motivo explican ausencia.

PRUEBA PREVIA: TLS normal con Oracle 17.0.12; JSON económico servido como text/html aceptado; HTML real previo rechazado como FORMATO_INVALIDO. La última consulta documental al recurso raíz API del BCRP dio 302 y no se siguió. No reabrir el cierre técnico solo por ese resultado documental.

Resilience4j protege los métodos públicos. HTTP/conexión pueden activar fallback; errores semánticos devueltos como resultado normal no se contabilizan como excepciones. La disponibilidad permanente del proveedor, historia completa y contexto productivo end-to-end no están certificados.

### 5.3. SENACE

Base declarada: `https://datosabiertos.senace.gob.pe/Api/datastreams`.

| Recurso | Ruta publicada en ayuda previa | Ruta construida por cliente actual |
|---|---|---|
| Proyectos | /Api/datastreams/CarteraProyectos | /Api/datastreams/CarteraProyectos/data.json |
| Consultoras | /Api/datastreams/ConsultorasAmbientales | /Api/datastreams/ConsultorasAmbientales/data.json |

La autenticación nominal documentada y codificada es auth_key en query. Nunca imprimir una URL autenticada. La diferencia de sufijo está confirmada documentalmente, pero la invalidez de la variante /data.json no está probada. No ensayar automáticamente combinaciones de rutas o credenciales.

El token del formulario de solicitud de uso de API está documentalmente relacionado con auth_key. No se ha probado la credencial del usuario en las rutas de datos ni se debe asumir que un JWT de inicio de sesión cualquiera sea la clave correcta. No hay evidencia suficiente para requerir Authorization Bearer u otro encabezado adicional.

Los métodos de proyectos y consultoras obtienen un Map y solo comprueban que no esté vacío. Luego lo descartan. Los fallbacks también generan SIMULADO; no hay respaldo SQLite. El genérico puede etiquetar API_DIRECTA_SENACE sin validar que su data sea un conjunto correcto, incluso frente a un cuerpo nulo o semánticamente erróneo.

No se verificaron muestras JSON, campos, tipos/nulos, identificadores, fechas de negocio, inversión, moneda, coordenadas, filtros API, paginación o completitud. La ayuda declara JSON, pero no sustituye una muestra verificada.

## 6. Estado del código

| Componente | Implementado | Incompleto o pendiente |
|---|---|---|
| SoaApplication | Entrada Spring Boot | Inicio integral no ejecutado en esta auditoría |
| BcrpClient | Catálogo y consulta anual, validación, BigDecimal, metadatos y fallbacks seguros | No hay necesidad demostrada de cambios para continuar SENACE |
| SenaceClient | HTTP, rutas configurables, sanitización y etiquetas SIMULADO | Parser real, paginación, campos y reglas de proyectos/consultoras |
| EcoInvestController | Cuatro rutas, metadatos BCRP, conteos y métricas | Procedencia independiente de filas, estado oficial y conteos sobre cobertura real SENACE |
| AppConfig | RestTemplate compartido, 5 segundos conexión/lectura | No es una prueba de transporte completo ni configura explícitamente redirecciones |
| OpenAPI | Cuatro rutas y detalle BCRP | Proyectos/consultoras siguen como objetos genéricos, sin campos oficiales tipados |
| Resilience4j | Instancias senace y bcrp configuradas | Prueba aislada BCRP acreditada; no extrapolarla a todos los métodos SENACE |
| SQLite | Dependencias y configuración JPA | No se encontraron entidades, repositorios o escritura/lectura de fallback |
| Frontend | Carpeta | Aplicación React, rutas, pantallas, api.ts e integración no existen |

### Persistencia y efectos potenciales

[application.yml](../backend/src/main/resources/application.yml), líneas 7–13, declara `jdbc:sqlite:ecoinvest_fallback.db`, dialecto SQLite y ddl-auto=update. El archivo observado backend/ecoinvest.db tiene cero bytes y es un nombre distinto del datasource configurado. No se abrió ninguno, no se verificaron tablas y no se interpreta ese archivo como respaldo operativo.

No se encontraron @Entity, JpaRepository, JdbcTemplate, DriverManager o @Repository en src/main. El controlador declara respaldoLocal NO_IMPLEMENTADO en línea 133. Iniciar el contexto productivo podría crear/actualizar la base configurada; no hacerlo sin autorización. El test permanente de contexto excluye datasource, Hibernate y repositorios JPA.

## 7. Pruebas y validaciones

No se ejecutó ninguna prueba durante esta auditoría. Los siguientes resultados se leyeron de reportes y XML Surefire ya existentes.

| Ejecución histórica | Fecha según reportes | Resultado | Runtime y límite |
|---|---|---|---|
| Estabilización Fase 1 | 18/10/2026 | Generación y compilación; 18 pruebas satisfactorias | REPORTE histórico; no reconstruir el total desde XML posteriores sobrescritos |
| Suite Fase 2B | 18/10/2026 | 75 pruebas: 59 BCRP, 4 SENACE, 1 AppConfig, 10 MVC, 1 contexto; cero fallos/errores | XML permanentes registran Java 20.0.2; compilación release 17 |
| BcrpClientRealIT | 18/10/2026 | 1 prueba, 1 fallo; 2017 válido, 2018 HTML | Java 17.0.12; fallo histórico conservado, no regresión nueva |
| BcrpClientHttpDiagnosticIT | 18/10/2026 | 1 prueba, cero fallos/errores; 2018 válido | Java 17.0.12; una observación puntual |
| BcrpClientResilience4jIT | 18/10/2026, reporte 19/10 | 2 pruebas, cero fallos/errores en ejecución final | Java 17.0.12; HTTP simulado, Spring mínimo |
| BcrpClientCatalogRealIT | 19/10/2026 | 6 casos, cero fallos/errores | Java 17.0.12; 5 observaciones reales y 1 inspección documental 302 |
| SENACE portal/ayuda | Reporte 19/10/2026 | 4 GET sin credenciales; 302 + 3 respuestas 200 | Documentación, no muestras autenticadas |
| SENACE manual | Última SESIÓN tras reorganización del 29/10 | HTTP 200; extracción textual de 16 páginas | No Java, no Maven, sin credenciales; no artefacto guardado |

La configuración expuesta del breaker bcrp mostraba minimumNumberOfCalls=100; la ventana COUNT_BASED de cinco limitaba el mínimo efectivo a cinco. Se verificaron CLOSED → OPEN, rechazo sin HTTP y, tras más de diez segundos, HALF_OPEN → CLOSED con diez respuestas exitosas. automaticTransitionFromOpenToHalfOpenEnabled=false. Un primer fallo de expectativa en la prueba temporal fue corregido solo en esa prueba; el resultado final fue satisfactorio.

HTML inválido retornó FORMATO_INVALIDO y se contabilizó como llamada exitosa del breaker porque no se lanzó excepción. Esta política está documentada; no cambiarla automáticamente.

Fuentes de prueba permanentes existentes:

- [BcrpClientTest.java](../backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java): transporte simulado, catálogo, URLs, períodos, estructuras, precisión, ausencia y errores.
- [SenaceClientTest.java](../backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java): mocks, simulación y seguridad de logs; sin datos oficiales.
- [EcoInvestControllerTest.java](../backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java): MockMvc standalone; no servidor real.
- [AppConfigTest.java](../backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java): comprueba que el bean construido no sea nulo; no demuestra todos los parámetros de transporte.
- [SoaApplicationTests.java](../backend/src/test/java/com/ecoinvest/soa/SoaApplicationTests.java): contexto con persistencia excluida.

Las clases temporales *RealIT, *HttpDiagnosticIT y *Resilience4jIT ya no están en src/test; permanecen sus evidencias y pueden quedar clases compiladas en target/. Su existencia en target/ no equivale a pruebas permanentes mantenibles. No sumar suites de fechas y runtimes distintos como una sola ejecución actual.

Evidencia local central: [catálogo real](../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientCatalogRealIT.xml), [AOP](../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientResilience4jIT.xml), [HTTP 2018](../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientHttpDiagnosticIT.xml). No publicar XML completos sin revisar sus propiedades: pueden contener detalles del entorno.

## 8. Archivos y cambios relevantes

### 8.1. Documentación actual

Hay once reportes históricos por fases y un índice, sin Markdown restantes en la raíz al comenzar esta auditoría. backend/HELP.md se conserva fuera de esa organización por ser ayuda generada. Los once reportes están enlazados en la tabla de fases y en [README documental](README.md).

La reorganización autorizada movió los once archivos y modificó setenta destinos de enlaces. Se preservaron nombres y contenido restante. La verificación actual encontró 91 enlaces locales de esos doce documentos con destinos existentes. Las rutas backend/ en enlaces de reportes por fase usan ../../backend/; desde este reporte, situado directamente en documentacion/, se utiliza ../backend/.

Los enlaces entre fases usan rutas relativas correspondientes. Las rutas de código escritas como texto siguen interpretándose desde la raíz; los números de línea son históricos. No se encontraron previamente dependencias de automatizaciones sobre los nombres originales dentro del alcance inspeccionado. Los enlaces a target/ no son portables a un clon sin sus artefactos.

### 8.2. Git actual

Rama observada: **main**. No se cambió de rama, creó commit o alteró el índice Git.

Archivos modificados respecto de Git al inspeccionar:

```text
backend/mvnw.cmd
backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java
backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java
backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java
backend/src/main/resources/openapi.yaml
backend/src/test/java/com/ecoinvest/soa/SoaApplicationTests.java
backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java
backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java
backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java
```

Sin seguimiento: backend/src/test/java/com/ecoinvest/soa/controller/ y documentacion/. Este reporte quedará dentro de esa documentación sin seguimiento. Estos cambios ya existían antes de esta creación; no atribuirlos a la auditoría. No revertirlos ni asumir que están respaldados por un commit o remoto.

### 8.3. Creación de esta auditoría

Único archivo nuevo: `documentacion/ESTADO_ACTUAL_ECOINVEST.md`. No se actualizó README ni el reporte SENACE porque el prompt solo autoriza crear este documento. Las conclusiones del manual se agregan aquí como continuidad de sesión, no como una modificación retroactiva del reporte histórico.

## 9. Problemas y riesgos

| Prioridad | Hallazgo | Consecuencia/acción necesaria |
|---|---|---|
| Alta | SENACE aún devuelve registros fijos | No presentar dashboard o consultoras como datos oficiales integrados |
| Alta | Sin muestras oficiales ni esquema verificado | No implementar campos, filtros, aprobación, inversión o coordenadas por suposición |
| Alta | Diferencia de ruta /data.json | Validar con una ruta documentada antes de decidir el cambio; no declarar roto sin prueba |
| Alta | Credencial no verificada en el proceso actual | Comprobar solo presencia y procedencia apropiada; no pedir token por chat ni leer .env |
| Alta | Sin paginación/límite REST documentado | Decidir explícitamente el riesgo de pedir el dataset; un corte local no limita trabajo del servidor |
| Alta | Datasource con ddl-auto update | No iniciar contexto completo o SQLite como efecto colateral |
| Media | Origen SENACE inferido de primera fila y vacío SIMULADO | Necesitará estado/procedencia independiente de filas en futura implementación autorizada |
| Media | Genérico SENACE sin validación y excepciones sin categoría | Un mapa de error podría confundirse con datos; faltan errores seguros y observables |
| Media | Propiedad de credencial con default en código | No usarla como fallback de autenticación; revisar en lote de configuración autorizado, sin divulgar valor |
| Media | SENACE_AUTH_KEY para diagnóstico no coincide por nombre con la propiedad Spring | No asumir que el backend leerá esa variable automáticamente: el código usa external.apis.senace-auth-key; cualquier puente requiere configuración explícita posterior |
| Media | CORS global asterisco, controlador línea 21 | Revisar antes de exposición pública; no cambiar en esta fase documental |
| Media | Respuestas variables de proveedores | Mantener rechazo seguro; sin elusión de protecciones o reintentos no autorizados |
| Media | Documentación histórica y HELP generado desalineados con estado actual | Preferir código/POM y cierres posteriores; no repetir correcciones ya verificadas |
| Media | Frontend inexistente y dashboard no probado end-to-end | No certificar módulos visuales operativos |
| Media | Cambios sin commit y artefactos target volátiles | Conservar árbol actual y contexto; no borrar ni hacer commits automáticos |

No se demuestra una caída actual del proveedor SENACE: no se consultó el servicio en esta auditoría y no hay una muestra autenticada fallida que justifique ese diagnóstico. El bloqueo es de evidencia, disponibilidad de credencial no confirmada y autorización/decisión de volumen; no una incompatibilidad TLS o defecto productivo probado.

## 10. Próxima fase recomendada

**Continuar Fase 3A.2 — validación autenticada acotada de CarteraProyectos y ConsultorasAmbientales. No iniciar todavía Fase 3B.**

### Objetivo concreto

Obtener evidencia oficial suficiente para conocer estructura, campos, tipos, fechas, identificadores, ubicación y completitud, o documentar honestamente la limitación si el acceso o el volumen no permiten una muestra válida.

### Archivos y fuentes a consultar

- [Validación SENACE histórica](03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md).
- [Diagnóstico SENACE](03_Fase3_SENACE/REPORTE_FASE3_SENACE.md).
- Este reporte, especialmente secciones 4, 5 y 9.
- [SenaceClient.java](../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java), [EcoInvestController.java](../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), [OpenAPI](../backend/src/main/resources/openapi.yaml) y [tests SENACE](../backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java) solo como referencia.
- Fuentes oficiales ya identificadas: https://datosabiertos.senace.gob.pe/Api/Help y el manual oficial enlazado. No hace falta repetir su descarga sin una pregunta concreta no resuelta.

### Actividades propuestas, no ejecutadas

1. Solicitar aprobación específica para dos GET de datos y una decisión explícita de volumen al no existir límite REST confirmado. No descargar por defecto conjuntos completos.
2. Comprobar únicamente que SENACE_AUTH_KEY está presente en el proceso y confirmar que corresponde al formulario de uso de API. Si falta, pedir configuración local temporal; nunca el valor por chat. No probar combinaciones de credencial, ruta o encabezados automáticamente.
3. Si se aprueba, emplear un GET a /Api/datastreams/CarteraProyectos y otro a /Api/datastreams/ConsultorasAmbientales con auth_key incorporado en memoria. Sin redirecciones automáticas, reintentos o URLs autenticadas en logs.
4. Evaluar el plan propuesto de streaming con corte local a 256 KiB. Advertir que puede solicitarse el conjunto completo al servidor y que un truncamiento no es un JSON válido ni una muestra completa. Cerrar la conexión al límite y documentar la limitación; no aumentar el presupuesto automáticamente.
5. Registrar solo metadatos seguros y estructura saneada. Distinguir HTTP, autenticación, formato, vacío, parcial y JSON válido. No publicar cuerpos completos ni datos personales innecesarios.
6. Diseñar diccionario con nombres oficiales observados y separar derivados, ausentes en muestra y decisiones de negocio. No inventar inversión, fecha, estado, moneda o coordenadas.
7. Pedir autorización para actualizar acumulativamente el reporte SENACE y, si procede, el índice. Preservar evidencia histórica y explicar las diferencias de fecha y alcance.
8. Proponer Fase 3B solo después de conocer datos y reglas; no implementarla automáticamente.

### Criterios de aceptación

- Presupuesto HTTP respetado, TLS normal y credencial no expuesta.
- Estado y Content-Type de cada intento registrados de forma segura.
- Resultado de autenticación expresado como observación, no inferido solo del formato del token.
- JSON y cobertura clasificados correctamente, sin publicar una respuesta truncada como completa.
- Campo por campo sustentado en datos reales o marcado pendiente; semántica de fecha anual y estado aprobatorio sin suposiciones.
- Si el acceso no permite datos, registrar el impedimento y la siguiente decisión concreta, sin sustituirlos por registros SIMULADO etiquetados como directos.
- Sin modificaciones productivas, Maven, SQLite o frontend.

### Autorizaciones necesarias

HTTP de datos y volumen potencial; cualquier archivo temporal; cualquier actualización del reporte SENACE/índice; cualquier compilación/prueba futura; cualquier implementación Fase 3B. Ninguna de esas acciones está autorizada por el presente prompt de continuidad.

## 11. Información para continuar en ChatGPT

EcoInvest es Proyecto-SOA en Windows. Su backend real está en backend/, Java declarado 17, Spring Boot 3.2.4, Spring Cloud 2023.0.1 y Maven Wrapper 3.9.9. Conservar Oracle JDK 17.0.12 para pruebas autorizadas. React + TypeScript es futuro: frontend/ está vacío. No hay OEFA ni ESG. SQLite está configurado con ddl-auto update pero no hay implementación de respaldo; no iniciar la aplicación completa ni abrir la base como parte de diagnósticos.

El contrato fuente es backend/src/main/resources/openapi.yaml. Genera com.ecoinvest.api y com.ecoinvest.model; no editar target/. EcoInvestController implementa cuatro GET: /api/dashboard, /api/estado-servicios, /api/consultoras y /api/senace/datastream. Mantenerlos. El controlador está en com.ecoinvest.soa.controller; los clientes están en com.ecoinvest.soa.client.

Fases completadas: estabilización inicial con 18 pruebas históricas; BCRP anual Fase 2B con 75 pruebas locales, runtime de esa suite 20.0.2 y compilación release 17; Fase 2C cerrada en alcance técnico mediante JDK 17.0.12, consultas reales de Hidrocarburos 2017/2018 y cinco series restantes 2018, además de AOP/Resilience4j aislado. No repetir esas fases como pendientes ni cambiar BCRP/Resilience4j. Su catálogo es AGRICULTURA PM04969AA, PESCA PM04971AA, MINERIA PM04973AA, HIDROCARBUROS PM04974AA, ELECTRICIDAD PM04978AA (Electricidad y Agua), TODOS PM04983AA (PBI nacional). BCRP usa /json/{anio}/{anio}/esp, Jackson explícito y BigDecimal. Transportes/Energía/desconocidos quedan sin indicador. HTML inesperado se rechaza; no eludir protecciones. Ninguna evidencia certifica todo el dashboard end-to-end.

Documentación organizada el 29/10/2026: documentacion/README.md, 00_Auditoria, 01_Fase1, 02_Fase2_BCRP y 03_Fase3_SENACE. Se movieron once reportes, ajustaron setenta enlaces y comprobaron 91 destinos locales. Sus contenidos históricos se conservaron; no confundir diagnósticos anteriores con código actual. Todo documentacion/ y tests de controlador están sin seguimiento Git; hay otros cambios previos sin commit en clientes, controlador, contrato, wrapper y tests. Preservarlos, no revertirlos ni crear commits automáticamente.

Última fase iniciada: 3A.2 SENACE. Última subtarea funcional completada: lectura textual en memoria del manual oficial, HTTP 200, 1.635.367 bytes, 16 páginas, versión 1.0 del 19/08/2020. Las páginas 13–15 relacionan el token del formulario de uso de API con sus instrucciones de uso; la ayuda previa publica auth_key en query y rutas sin /data.json. No confundir cualquier JWT de sesión con API key. El código actual añade /data.json; esa discrepancia aún no demuestra invalidez. No se confirmaron parámetros REST de paginación/límites. La grilla visual del manual no acredita parámetros API. Las capturas del PDF no se examinaron visualmente. El reporte histórico SENACE aún no tiene estos hallazgos del manual; este estado sí los conserva como evidencia de SESIÓN.

SenaceClient sigue consultando un Map y después construyendo cuatro proyectos de 2017 y cuatro consultoras fijas SIMULADO. No hay muestras auténticas, parser, campos o reglas de negocio verificados. El dashboard cuenta esa lista y determina aprobado por texto Aprobado; además infiere origen de la primera fila y vacío SIMULADO. No implementar equivalencias, coordenadas o inversiones por suposición.

Siguiente prompt recomendado para Codex: continuar exclusivamente Fase 3A.2, presentar y solicitar autorización para máximo dos GET oficiales, uno por recurso, con decisión explícita de volumen y credencial apropiada en SENACE_AUTH_KEY temporal. La presencia actual de esa variable no está verificada; comprobaciones antiguas dieron ausente. No pedir ni imprimir su valor, no leer .env ni registrar URLs con auth_key. El corte local propuesto de 256 KiB no limita el trabajo del servidor y puede truncar JSON; explicarlo antes de aprobar. No hubo aprobación de ese último plan tras la reorganización y este encargo prohíbe HTTP. No continuar automáticamente.

Tras consultas autorizadas, documentar estructura, campos, tipos, nulos, fechas, sectores, estados, inversión/moneda si existen, ubicación, identificadores, paginación y completitud. Separar datos observados, derivados y ausentes. Pedir permiso para actualizar acumulativamente documentacion/03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md. Solo después preparar Fase 3B con archivos y pruebas acotados, sin implementar sin aprobación. Restricciones: no cambiar versiones o paquetes, backend productivo, BCRP, Resilience4j, SQLite o frontend; no Maven, HTTP, servidores, instalaciones, commits o auxiliares sin autorización específica.

Este reporte se basa en inspección local y evidencias anteriores, sin nueva ejecución de servicios o pruebas. Su creación es el único cambio de esta auditoría.
