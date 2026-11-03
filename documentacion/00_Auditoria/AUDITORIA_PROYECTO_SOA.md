# Auditoría técnica de Proyecto-SOA

**Fecha de la inspección:** 18 de octubre de 2026.  
**Repositorio:** `D:\Documentos\GitHub\Proyecto-SOA`.  
**Método:** inspección estática de código y configuración. No se instalaron dependencias, compiló el proyecto, iniciaron servicios ni ejecutaron pruebas. Se omitieron secretos, `.git/`, `target/` y `node_modules/`.

Este archivo documenta los resultados de la auditoría previa. Su creación fue autorizada posteriormente por el usuario; no implica cambios de implementación. Las referencias de líneas corresponden a los archivos inspeccionados durante la auditoría.

**Conclusión principal:** existe un backend **EcoInvest orientado a SENACE + BCRP**, con integración parcial y datos simulados. No se encontró implementación de OEFA, riesgo ESG ni frontend React. La carpeta `frontend/` existe, pero está vacía. Una implementación visible no equivale a una funcionalidad probada.

## 1. IDENTIDAD Y OBJETIVO DEL PROYECTO

El POM declara **EcoInvest SOA**, con descripción «Orquestador SOA para EcoInvest Perú» y coordenadas `com.ecoinvest:soa:0.0.1-SNAPSHOT`. El contrato se titula **EcoInvest Perú API** y declara integración de SENACE y BCRP.

Evidencia: [backend/pom.xml](../../backend/pom.xml), líneas 11–15; [openapi.yaml](../../backend/src/main/resources/openapi.yaml), líneas 1–7; [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), desde la línea 39.

El propósito funcional visible comprende un dashboard de proyectos filtrado por sector y año, conteos de proyectos y aprobaciones, un indicador económico sectorial, métricas de circuit breakers, un directorio de consultoras y una consulta genérica de datastreams SENACE.

### Discrepancias verificadas

| Tema | Estado real |
|---|---|
| EcoMetrics, OEFA y ESG | No se encontraron implementaciones ni endpoints. |
| Java 21 | El POM declara Java 17. |
| Spring Boot | El POM declara 3.2.4; `backend/HELP.md` enlaza documentación 4.1.1. |
| React y TypeScript | `frontend/` está vacío. No hay `package.json` ni `src/services/api.ts`. |
| Respaldo SQLite | Hay configuración y dependencias; los fallbacks construyen datos en memoria. |
| Datos SENACE | Proyectos y consultoras se construyen desde registros hardcodeados incluso tras recibir una respuesta externa. |
| Año BCRP | Se devuelve como metadato, pero no selecciona el período consultado. |
| Contrato | Está en `backend/src/main/resources/openapi.yaml`; no existe `backend/openapi.yaml`. |
| POM raíz | No existe; el módulo Maven está en `backend/`. |

`backend/HELP.md` contiene documentación genérica de arranque, no una especificación funcional. Sus enlaces no determinan la versión real del proyecto.

## 2. ARQUITECTURA ACTUAL

### Stack y versiones declaradas

| Elemento | Versión o mecanismo |
|---|---|
| Java | `java.version=17` |
| Spring Boot | 3.2.4 |
| Spring Cloud BOM | 2023.0.1 |
| Maven Wrapper | Maven 3.9.9 |
| OpenAPI | 3.0.3; versión funcional 1.0.0 |
| OpenAPI Generator | 7.4.0 |
| Springdoc WebMVC UI | 2.5.0 |
| Swagger annotations | 2.2.21 |
| jackson-databind-nullable | 0.2.6 |
| SQLite JDBC | 3.45.2.0 |
| HTTP saliente | RestTemplate |
| Persistencia declarada | Spring Data JPA, Hibernate, dialecto SQLite |
| Resiliencia declarada | Spring Cloud Circuit Breaker Resilience4j |
| Frontend | Sin implementación disponible |

Fuentes: `backend/pom.xml`, líneas 5–89; `backend/.mvn/wrapper/maven-wrapper.properties`, líneas 1–3. No se verificó el JDK instalado ni el árbol de dependencias efectivamente resuelto.

### Estructura relevante

```text
Proyecto-SOA/
├── backend/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── HELP.md
│   ├── ecoinvest.db
│   └── src/
│       ├── main/
│       │   ├── java/com/ecoinvest/soa/
│       │   │   ├── SoaApplication.java
│       │   │   ├── config/AppConfig.java
│       │   │   ├── controller/EcoInvestController.java
│       │   │   └── client/
│       │   │       ├── BcrpClient.java
│       │   │       └── SenaceClient.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── openapi.yaml
│       └── test/java/com/ecoinvest/soa/
│           ├── SoaApplicationTests.java
│           ├── config/AppConfigTest.java
│           └── client/
│               ├── BcrpClientTest.java
│               └── SenaceClientTest.java
└── frontend/                         # Vacío
```

No se encontraron capas propias `service`, `repository`, `entity` ni DTOs manuales.

### Configuración

[application.yml](../../backend/src/main/resources/application.yml), líneas 1–25, declara:

- Puerto **8081** y nombre `ecoinvest-soa`.
- Datasource `jdbc:sqlite:ecoinvest_fallback.db`.
- Driver `org.sqlite.JDBC`.
- Dialecto `org.hibernate.community.dialect.SQLiteDialect`.
- `ddl-auto: update`.

La ubicación SQLite es relativa al directorio desde el que se ejecute la aplicación. Existe `backend/ecoinvest.db`, cuyo nombre no coincide con la base configurada. No se abrió su contenido ni se verificaron tablas.

Los clientes admiten propiedades externas de URL base y autenticación SENACE. No se inspeccionaron archivos secretos ni se verificaron los valores recibidos por un proceso en ejecución.

### Flujo real

```text
Consumidor HTTP —frontend ausente—
    → API Spring MVC
    → EcoInvestController
        → SenaceClient → RestTemplate → API SENACE
        → BcrpClient   → RestTemplate → API BCRP
    → DashboardResponse generado
```

No existe una capa Service intermedia. La orquestación está en el controlador y las dos consultas del dashboard son secuenciales (`EcoInvestController.java`, líneas 43–44).

### Resiliencia, caché y persistencia

- `AppConfig.java`, líneas 12–17: RestTemplate con timeout de conexión y lectura de 5 segundos.
- Clientes: anotaciones `@CircuitBreaker` y métodos de fallback.
- `application.yml`, desde la línea 15: instancias `senace` y `bcrp`, ventana de 5, umbral de fallos del 50 % y espera abierta de 10 segundos.
- Controlador: lectura de métricas desde `CircuitBreakerRegistry`.

No se encontró acceso SQLite en los clientes, entidades, repositorios, consultas JDBC, caché implementada, reintentos ni bulkheads configurados.

La aplicación efectiva de las anotaciones de resiliencia no está verificada. El POM no declara explícitamente `spring-boot-starter-aop`; debe comprobarse el soporte de aspectos resuelto antes de confiar en los fallbacks automáticos. Leer el registro de métricas no demuestra que las llamadas estén interceptadas.

## 3. CONTRATO OPENAPI

Archivo real: [backend/src/main/resources/openapi.yaml](../../backend/src/main/resources/openapi.yaml).

### Operaciones incluidas

| Método | Ruta | Parámetros | Respuesta |
|---|---|---|---|
| GET | `/api/dashboard` | `sector`: query, string, obligatorio; `anio`: query, integer, opcional | `200 application/json`, `DashboardResponse` |
| GET | `/api/estado-servicios` | Ninguno | `200 application/json`, `EstadoServiciosResponse` |

No se documentan cuerpos de solicitud ni respuestas de error.

### Esquemas

| Esquema | Campo | Tipo |
|---|---|---|
| DashboardResponse | total | integer |
| DashboardResponse | aprobados | integer |
| DashboardResponse | variacionSeleccionada | number |
| DashboardResponse | proyectos | array de object sin propiedades definidas |
| DashboardResponse | fuentes | object sin propiedades definidas |
| EstadoServiciosResponse | senace | object sin propiedades definidas |
| EstadoServiciosResponse | bcrp | object sin propiedades definidas |

No se declaran campos obligatorios en los esquemas. No se tipan los proyectos, la procedencia, los períodos o las métricas de circuit breaker. Evidencia: contrato, líneas 45–68.

### Implementación y generación

`EcoInvestController implements ApiApi` e importa `com.ecoinvest.api.ApiApi`, `com.ecoinvest.model.DashboardResponse` y `com.ecoinvest.model.EstadoServiciosResponse` (controlador, líneas 3–5 y 23).

`obtenerDashboard` y `obtenerEstadoServicios` implementan las operaciones del contrato. Sus mappings dependen de la interfaz generada; no se repiten en el controlador. Los archivos generados no están en el árbol fuente inspeccionado. Se omitió `target/`, por lo que no se verificó su contenido o compatibilidad de compilación.

`backend/pom.xml`, líneas 107–130, configura:

- `openapi-generator-maven-plugin:7.4.0`, goal `generate`.
- Entrada `${project.basedir}/src/main/resources/openapi.yaml`.
- Generador `spring`.
- Interfaces en `com.ecoinvest.api` y modelos en `com.ecoinvest.model`.
- `interfaceOnly=true`, `useSpringBoot3=true`, `skipDefaultInterface=true`.

El goal utiliza la fase de generación de fuentes del plugin. No se personaliza la salida; se espera el directorio predeterminado bajo `target/generated-sources/openapi`. No se ejecutó Maven para comprobarlo.

### Rutas fuera del contrato

| Método | Ruta | Parámetros | Respuesta Java |
|---|---|---|---|
| GET | `/api/consultoras` | Ninguno | `List<Map<String,Object>>` |
| GET | `/api/senace/datastream` | `nombre`: query, default `CarteraProyectos` | `Map<String,Object>` |

Evidencia: controlador, líneas 87–98.

### Inconsistencias

- Faltan las dos rutas adicionales en OpenAPI.
- `sector` es obligatorio en el contrato, pero el método contiene una alternativa para `null` o vacío. Esto no garantiza que Spring acepte una petición sin el parámetro.
- El contrato no declara el valor predeterminado de año; el controlador usa 2017.
- Los objetos genéricos dificultan verificar respuestas y generar clientes tipados.
- No se describen degradación, simulación, ausencia de datos ni errores.

## 4. ESTADO DEL MÓDULO BCRP

### Archivos involucrados

- `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java`.
- `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java`.
- `backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java`.
- `backend/src/main/resources/application.yml`.
- `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java`.

### Flujo implementado

1. El dashboard recibe sector y año e invoca `obtenerDatosBcrp`.
2. Se normaliza el sector con `trim().toUpperCase()`.
3. Se selecciona un código de serie desde un mapa fijo.
4. Se consulta `{bcrpBaseUrl}/{codigoSerie}/json`.
5. RestTemplate deserializa a `Map.class`.
6. Se copia la respuesta y agregan metadatos.
7. Se toma el primer valor del último elemento de `periods`.
8. Se expone como `variacionSeleccionada`.

Evidencia: `BcrpClient.java`, líneas 48–88.

| Sector | Serie declarada |
|---|---|
| Hidrocarburos | PM05141AA |
| Mineria / Minería | PN01774AM |
| Electricidad / Energia | PN01780AM |
| Pesca | PN01773AM |
| Agricultura | PN01771AM |
| Transportes | PN01781AM |

La tabla refleja el código, no una verificación externa de nombres, unidades o periodicidad.

### Fechas y deserialización

No hay `LocalDate`, `Year` ni DTO de períodos. El año no se incorpora a la URL ni selecciona un período. No se interpreta `periods[].name`; se presupone que el último elemento es el deseado. Se realizan casts no comprobados a listas de mapas y strings, y se usa `Double.parseDouble(values.get(0))`.

Una consulta de 2017 puede devolver un período diferente etiquetado con `anioConsultado=2017`.

### Valores referenciales y fallback

Si la extracción no obtiene un número, se devuelve `-2.45` para hidrocarburos y `3.53` para los demás sectores. Puede conservarse `origen=API_DIRECTA_BCRP` aunque el número sea referencial (`BcrpClient.java`, líneas 65–69 y 85–88).

El fallback crea un mapa y un período sintético en memoria. No lee SQLite (`BcrpClient.java`, líneas 95–119).

### Pendientes y riesgos

- Seleccionar el período solicitado y definir el tratamiento de series de distinta periodicidad.
- Validar códigos, unidades y significado económico antes de agruparlos en un indicador común.
- Resolver explícitamente sectores desconocidos; actualmente usan la serie de hidrocarburos.
- Normalizar acentos: existe `ENERGIA`, pero no `ENERGÍA`.
- Validar estructura, valores ausentes y números no finitos antes de convertir a BigDecimal.
- Distinguir datos reales, ausentes y simulados.
- Implementar persistencia si se mantiene el respaldo SQLite anunciado.
- Verificar resiliencia y comportamiento mediante pruebas autorizadas.

**Estado: PARCIAL.** Hay consulta implementada, sin selección temporal consistente ni ejecución exitosa acreditada.

## 5. ESTADO DEL MÓDULO OEFA

No se encontraron `OefaClient`, `OefaResponseDto`, `OefaResultDto`, `OefaCellDto`, `EnvironmentalInfractionsService` o `EnvironmentalInfractionsController`. Tampoco hay URL OEFA ni endpoints correspondientes en el contrato.

| Aspecto | Resultado |
|---|---|
| Consumo de API OEFA | Ausente |
| Procesamiento de `result.fArray` | Ausente |
| Interpretación de encabezados y filas | Ausente |
| Identificación de `ADMINISTRADO` | Ausente |
| Filtrado por RUC u otros criterios | Ausente |
| Integración ambiental con ESG | Ausente |

Los RUC del cliente SENACE forman parte de registros hardcodeados y no constituyen integración OEFA.

No hay un módulo existente que pueda terminarse con pequeños ajustes. Si se incorpora al alcance, probablemente habría que modificar `backend/src/main/resources/openapi.yaml`, `backend/src/main/resources/application.yml` y, si resulta necesario, `backend/pom.xml`. Habría que crear cliente, DTOs, servicio, controlador y pruebas; esos archivos todavía no existen.

Antes se requieren documentación y respuestas sanitizadas de la API, además de reglas para interpretar encabezados, administrado y RUC. No se puede inferir esa correspondencia sin evidencia.

**Estado: PENDIENTE.**

## 6. ESTADO DEL MÓDULO DE RIESGO ESG

No existe `POST /esg-risk` en el contrato ni en los controladores. Tampoco se encontraron DTOs ESG, servicio de cálculo, orquestación BCRP + OEFA, ponderación macroeconómica del 40 %, ponderación ambiental del 60 %, normalización, umbrales o clasificación.

La orquestación existente consolida SENACE y BCRP para un dashboard; no calcula un score ESG.

Para obtener una respuesta consistente sería necesario confirmar el alcance del producto, definir solicitud/respuesta y errores, completar las fuentes, aprobar las reglas de normalización y datos faltantes, e implementar cálculo, trazabilidad y pruebas. La ponderación 40/60 es un requerimiento mencionado por el usuario, no una implementación encontrada. No hay evidencia para inventar una fórmula.

**Estado: PENDIENTE.**

## 7. ESTADO DEL FRONTEND

`frontend/` está vacío, incluyendo la revisión de archivos ocultos. No existen `frontend/package.json`, `frontend/src/services/api.ts`, componentes React, código TypeScript, router, vistas, configuración Vite, proxy de desarrollo ni pruebas de interfaz.

No puede identificarse ninguna pantalla que consuma endpoints ni afirmarse que exista integración React–Spring Boot.

Los comentarios del backend mencionan dashboard, mapa, directorio RNCA y prototipos de Stitch (`SenaceClient.java`, líneas 46–47, 70–72 y 147–149). Estos comentarios no acreditan la existencia de las pantallas.

### Datos simulados

`SenaceClient.java` contiene cuatro proyectos fijos de 2017, dos sectores representados —hidrocarburos y minería— y cuatro consultoras fijas. Incluye coordenadas, inversiones, estados y datos de identificación.

La consulta de proyectos solo verifica que la respuesta externa no esté vacía y después llama a `construirProyectosBase("API_DIRECTA_SENACE")` (líneas 56–62). Las consultoras hacen lo equivalente con `construirConsultorasBase` (líneas 80–85).

Existe soporte backend para un prototipo, pero no una interfaz verificable en este checkout.

## 8. CONFIGURACIÓN, ERRORES Y RIESGOS

### Jackson y ObjectMapper

Spring Boot declarado: 3.2.4. No se encontró `ObjectMapper`, `JsonMapper` ni convertidor HTTP personalizado. La deserialización se delega al RestTemplate construido con RestTemplateBuilder y utiliza `Map.class`.

Se declara `jackson-databind-nullable`; no hay evidencia de Jackson 3. La versión exacta resuelta de Jackson no se verificó mediante Maven. Los enlaces de `HELP.md` a Boot 4.1.1 no deben determinar imports o configuración del proyecto actual.

### Content-Type, HTML y errores HTTP

No hay tratamiento específico de JSON con Content-Type incorrecto, HTML inesperado, estructuras inválidas, errores HTTP diferenciados ni un formato propio de error público. Los clientes esperan deserializar directamente un objeto JSON. Una excepción de transporte, HTTP o conversión solo activaría el fallback automático si la interceptación Resilience4j está operativa.

Además, SENACE acepta cualquier mapa no vacío antes de devolver datos fijos; BCRP puede marcar como directo un resultado sin períodos útiles y con valor referencial; el datastream genérico puede marcar como directa una respuesta nula.

### Credenciales y logs

**Riesgo concreto:** `SenaceClient` incorpora la credencial como query parameter `auth_key` y registra la URL completa. Evidencia: líneas 107–111 y llamadas de logging en las líneas 54, 78 y 97. Los mensajes de excepciones registrados por fallbacks también podrían incluir URLs.

No se revelaron valores secretos. No se verificó una credencial válida en ejecución ni se encontró configuración visible que cargue explícitamente el `.env` raíz.

### CORS, seguridad y validación

- `@CrossOrigin(origins = "*")` en el controlador, línea 22.
- No se encontró autenticación o autorización propia.
- No hay límites de negocio para `anio` ni catálogo contractual de sectores.
- `nombre` del datastream no se valida contra `DATASTREAMS_DISPONIBLES`.
- El POM declara `jakarta.validation-api`, pero no explícitamente `spring-boot-starter-validation`; no se verificó la validación efectiva.
- No hay `@ControllerAdvice` propio.

La lista de ocho datastreams del cliente no acredita disponibilidad externa.

### Persistencia y monitorización

- Los fallbacks no consultan SQLite.
- El monitor devuelve siempre `SQLite Activo (ecoinvest_fallback.db)` sin comprobarlo (`EcoInvestController.java`, línea 110).
- Un circuit breaker `CLOSED` no demuestra disponibilidad externa; sus métricas no son un health check activo.
- Una lista vacía de proyectos se etiqueta como `FALLBACK_SQLITE` por inferencia del controlador, incluso si un resultado vacío fuera válido.
- Iniciar la aplicación con `ddl-auto=update` podría modificar la base; no se inició.

### Pruebas existentes, no ejecutadas

| Prueba | Observación estática |
|---|---|
| SoaApplicationTests | Solo intenta cargar el contexto. |
| AppConfigTest | Comprueba existencia de RestTemplate, no sus timeouts. |
| BcrpClientTest | Comprueba estructura de fallback; no lectura SQLite ni selección temporal. |
| SenaceClientTest | Contiene expectativas incompatibles con el cliente actual. |

En `SenaceClientTest.java`, desde las líneas 26 y 39:

1. El mock usa `getForObject(..., List.class)`; el cliente usa `Map.class`.
2. Se esperan proyectos de minería de 2024, pero los datos fijos son de 2017.
3. Se espera fallback de electricidad de 2023, sin registros correspondientes.
4. El test usa `Minería`, los proyectos usan `Mineria` y el filtro no elimina acentos.

Son contradicciones identificadas por lectura, no resultados de ejecución.

## 9. MATRIZ DE AVANCE

Los estados describen implementación inspeccionada, no certificación operativa.

| Módulo | Archivos principales | Implementado | Pendiente | Estado | Evidencia |
|---|---|---|---|---|---|
| Arranque y configuración | SoaApplication.java, AppConfig.java, application.yml, pom.xml | Aplicación, HTTP, puerto y datasource | Verificar compilación, contexto y dependencias | PARCIAL | Clases y configuración |
| Contrato y generación | openapi.yaml, pom.xml | Dos operaciones y generación | Tipar objetos, rutas restantes y errores | PARCIAL | Contrato 9–68; POM desde 107 |
| Dashboard | EcoInvestController.java | Agregación, conteos, metadatos | Datos reales, período y degradación | PARCIAL | Método desde 39 |
| BCRP | BcrpClient.java | HTTP, mapeo, extracción, fallback fijo | Fechas, validación, procedencia y pruebas | PARCIAL | Métodos desde 53 y 73 |
| SENACE proyectos | SenaceClient.java | HTTP y filtro de datos fijos | Transformar respuesta externa | PARCIAL | Líneas 52–62 |
| Consultoras RNCA | SenaceClient.java, EcoInvestController.java | Ruta y datos fijos | Parseo real y contrato | PARCIAL | Cliente desde 76; controlador desde 88 |
| Datastream genérico | SenaceClient.java, EcoInvestController.java | Consulta y envoltorio | Validación, esquema y errores | PARCIAL | Métodos desde 94 |
| Resiliencia | Clientes, application.yml, controlador | Anotaciones, fallbacks, métricas | Verificar AOP y efecto real | PARCIAL | Configuración desde 15 |
| Persistencia de fallback | pom.xml, application.yml | Dependencias y datasource | Almacenar y recuperar datos | PENDIENTE | Sin repositorios ni acceso SQL |
| OEFA | Sin archivos | Nada encontrado | Integración completa | PENDIENTE | Ausente en fuentes y contrato |
| ESG | Sin archivos | Nada encontrado | Contrato, reglas y orquestación | PENDIENTE | No existe /esg-risk |
| Frontend | frontend/ vacío | Nada encontrado | Recuperar o implementar | PENDIENTE | Inventario completo |
| Pruebas | Cuatro clases de test | Casos básicos y fallback | Corregir inconsistencias y cobertura | PARCIAL | Tests inspeccionados |
| Operación end-to-end | Sin ejecución | No comprobada | Validaciones autorizadas | NO VERIFICABLE | No se ejecutaron servicios o pruebas |

## 10. PLAN DE CONTINUACIÓN

**Plan propuesto; no ejecutado.**

### Etapa 1: corregir integraciones existentes y trazabilidad

**Leer:** POM, application.yml, contrato, BcrpClient, SenaceClient, AppConfig, EcoInvestController y pruebas de clientes; obtener respuestas externas sanitizadas.

**Probables modificaciones:** esos archivos, nuevos DTOs externos y pruebas específicas.

**Dependencias:** conocer estructura real SENACE y validar series, periodicidad y significado económico BCRP.

**Aceptación:** proyectos y consultoras provienen del cuerpo externo; el período económico corresponde a la consulta; un valor referencial no se presenta como directo; sectores, años y ausencia de datos tienen reglas explícitas; credenciales fuera de logs; circuit breakers verificados; pruebas contradictorias corregidas.

Antes de ejecutar pruebas debe solicitarse autorización. Las propuestas incluirían parseo de muestras sanitizadas, selección temporal, errores HTTP, HTML, timeouts y fallback, sin exigir llamadas externas para las pruebas con mocks.

### Etapa 2: resolver persistencia y contrato

**Leer:** POM, configuración, contrato, clientes y controlador.

**Probables modificaciones:** application.yml, openapi.yaml, controlador y clientes; nuevas entidades/repositorios u otro mecanismo de almacenamiento si se confirma SQLite.

**Dependencias:** decidir si se requiere respaldo persistente o simulación explícita.

**Aceptación:** si se anuncia SQLite, hay escritura y recuperación verificables; nombre y ubicación consistentes; se distingue respaldo ausente y vencido; contrato con las cuatro rutas y esquemas de proyectos, consultoras, fuentes, métricas y errores.

### Etapa 3: recuperar o implementar frontend e integrarlo

**Leer:** contrato actualizado y controlador; solicitar frontend anterior si existe fuera de este checkout.

**Probables modificaciones:** archivos nuevos bajo frontend/ y CORS del backend según despliegue.

**Dependencias:** API estable y decisión de recuperar o implementar.

**Aceptación:** React/TypeScript presente y ejecutable, cliente API centralizado, dashboard conectado a `/api/dashboard`, monitor a `/api/estado-servicios`, directorio a `/api/consultoras`, filtros compatibles, estados de carga/error/vacío y procedencia visibles. No presentar mocks como datos reales.

### Etapa 4: incorporar OEFA si se confirma el alcance

**Leer:** contrato, clientes actuales, documentación OEFA y muestras sanitizadas con `result.fArray`.

**Probables modificaciones:** contrato y configuración; creación de cliente, DTOs, servicio, controlador y pruebas.

**Dependencias:** endpoint externo confirmado y reglas verificables de encabezados, filas, ADMINISTRADO y RUC.

**Aceptación:** interpretación correcta de filas y encabezados, filtrado respaldado por evidencia, errores y ausencia de datos explícitos y trazabilidad.

### Etapa 5: implementar ESG al final, si corresponde

**Leer:** contratos y servicios completos de las fuentes, reglas de negocio aprobadas.

**Probables modificaciones:** OpenAPI y nuevas clases de solicitud, respuesta, servicio y controlador ESG.

**Dependencias:** fuentes fiables, normalización, ponderación, umbrales y tratamiento de datos faltantes definidos expresamente.

**Aceptación:** POST /esg-risk documentado e implementado, ponderación 40/60 solo si se confirma, resultado explicable y pruebas autorizadas. No producir un score aparentemente real a partir de constantes encubiertas.

## 11. CONTEXTO TÉCNICO ACTUALIZADO PARA CHATGPT

Este bloque es autocontenido para transferir el estado del proyecto a otro asistente.

**Repositorio:** Proyecto-SOA, `D:\Documentos\GitHub\Proyecto-SOA`. Inspección estática del 18 de octubre de 2026. No se compilaron fuentes, ejecutaron pruebas ni iniciaron servicios.

**Proyecto real:** backend EcoInvest SOA para SENACE + BCRP. No hay implementación EcoMetrics, OEFA o ESG. frontend/ existe, pero está vacío.

**Stack declarado:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.1, Maven Wrapper 3.9.9, OpenAPI Generator 7.4.0, Springdoc 2.5.0, RestTemplate, Resilience4j y dependencias JPA/SQLite. No asumir Java 21 o Spring Boot 4. HELP.md contiene enlaces desalineados con el POM.

**Arquitectura y rutas:** módulo Maven en backend/, sin POM raíz. Contrato en backend/src/main/resources/openapi.yaml. Paquete com.ecoinvest.soa. Flujo EcoInvestController → SenaceClient/BcrpClient → RestTemplate → API externa. No hay capa Service. EcoInvestController implementa com.ecoinvest.api.ApiApi, generada por Maven. GET /api/dashboard y GET /api/estado-servicios están en contrato; GET /api/consultoras y GET /api/senace/datastream están fuera del contrato. Puerto 8081.

**Implementado, sin operación probada:** dashboard, llamadas HTTP SENACE/BCRP, extracción numérica BCRP, filtros sobre registros fijos, fallbacks en memoria y métricas de circuit breakers. Ninguna de estas capacidades se certificó mediante ejecución.

**Incompleto y problemas conocidos:** SENACE descarta el contenido externo de proyectos y consultoras y devuelve registros hardcodeados, incluso marcados como API_DIRECTA_SENACE. BCRP ignora el año al seleccionar períodos y puede sustituir el valor por -2.45 o 3.53 manteniendo procedencia directa. FALLBACK_SQLITE no accede a SQLite. Se configura ecoinvest_fallback.db, pero el archivo visible se llama backend/ecoinvest.db. No hay entidades, repositorios ni SQL. El monitor declara SQLite activo sin comprobarlo.

**Riesgos:** URL SENACE con credencial en query registrada en logs; sanitizarla. CORS abierto. Sin manejo propio de errores, adaptación a Content-Type incorrecto o HTML. Verificar soporte AOP de Resilience4j: no hay starter AOP declarado explícitamente. Hay cuatro clases de pruebas, sin ejecutar; SenaceClientTest usa List.class donde el cliente usa Map.class y espera datos de sectores/años ausentes.

**Ausente:** clases OEFA solicitadas, procesamiento result.fArray, filtrado ambiental por RUC, POST /esg-risk y ponderación 40/60. No inventar normalización ni reglas de negocio.

**Restricciones:** la auditoría se autorizó solo para lectura, sin cambios de implementación, instalaciones, commits o cambios de rama y sin revelar secretos. Este archivo Markdown fue autorizado posteriormente. Antes de ejecutar pruebas, explicar cuáles y solicitar autorización. Una futura implementación requiere una instrucción posterior que la autorice.

**Próxima tarea concreta recomendada:** obtener muestras sanitizadas SENACE/BCRP y corregir primero la fidelidad de las integraciones: transformar datos externos reales y seleccionar el período económico solicitado, eliminando atribuciones engañosas de procedencia.

**Archivos exactos que solicitar para continuar:**

- `backend/pom.xml`
- `backend/src/main/resources/openapi.yaml`
- `backend/src/main/resources/application.yml`, sanitizado
- `backend/src/main/java/com/ecoinvest/soa/SoaApplication.java`
- `backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java`
- `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java`
- `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java`
- `backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java`
- `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java`
- `backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java`
- `backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java`
- `backend/src/test/java/com/ecoinvest/soa/SoaApplicationTests.java`

Solicitar también respuestas externas sanitizadas y, si está en otra ubicación, el frontend anterior. `frontend/src/services/api.ts` no existe en este checkout.
