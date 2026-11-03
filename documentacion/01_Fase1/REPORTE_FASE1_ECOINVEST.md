# Informe de diagnóstico y planificación — Fase 1 de EcoInvest

**Fase:** estabilización del backend.  
**Fecha de inspección:** 18 de octubre de 2026.  
**Repositorio:** `D:\Documentos\GitHub\Proyecto-SOA`.  
**Alcance de la inspección:** archivos fuente, configuración y pruebas de `backend/`.

No se modificaron archivos durante el diagnóstico, se ejecutó Maven, se iniciaron servicios ni se accedió a SQLite. No se inspeccionaron el frontend o los prototipos externos. La creación de este documento fue autorizada posteriormente y es la única modificación realizada para documentar esta fase.

Las referencias de líneas corresponden al contenido inspeccionado durante el diagnóstico. Una implementación visible no equivale a una funcionalidad probada.

## Contexto definitivo y restricciones

El proyecto se llama **EcoInvest**, una plataforma de inteligencia gubernamental que integra **SENACE y BCRP**. No utiliza OEFA, no se llama EcoMetrics y no calcula riesgo ESG.

Los cuatro módulos visuales finales serán:

1. Dashboard Analítico.
2. Explorador Geográfico.
3. Registro de Consultoras Ambientales.
4. Monitor de Salud SOA.

Primero se completará y estabilizará el backend. El frontend React + TypeScript se desarrollará después. Los prototipos y logos se encuentran, según el contexto proporcionado por el usuario, en `D:\Documentos\UTP 2026\(SOA) Arquitectura Orientada al Servicio\Prototipo_SOA`; no se inspeccionaron en esta fase.

Restricciones para una futura implementación:

- Trabajar exclusivamente dentro de `backend/`.
- No crear ni modificar el frontend.
- No migrar Java o Spring Boot ni cambiar paquetes existentes.
- No eliminar endpoints o funcionalidades existentes.
- Mantener OpenAPI como fuente de verdad.
- No editar manualmente archivos generados bajo `target/`.
- No incorporar OEFA ni ESG.
- No presentar valores simulados como datos reales.
- No revelar secretos ni realizar commits.
- No modificar la base de datos sin autorización.
- Evitar refactorizaciones amplias o cambios ajenos a la fase.
- Solicitar autorización antes de Maven, arranques o cualquier operación que genere archivos o pueda actualizar SQLite, indicando comando y efectos.

La autorización para crear este reporte no autoriza implementar las correcciones propuestas.

## 1. Estado actual confirmado del backend

### 1.1. Plataforma y compatibilidad

| Elemento | Evidencia confirmada | Límite de verificación |
|---|---|---|
| Java del proyecto | `java.version=17` | No se compiló |
| Java disponible en la terminal inspeccionada | `java -version` devuelve **20.0.2** | No demuestra qué JDK utilizaría Maven |
| Spring Boot | **3.2.4** | Dependencias resueltas sin verificar |
| Spring Cloud BOM | **2023.0.1** | Sin validación del árbol transitivo |
| OpenAPI Generator | **7.4.0**, generador Spring, `useSpringBoot3=true` | No se regeneraron interfaces |
| Springdoc | **2.5.0** | Sin arranque de Swagger UI |
| SQLite JDBC | **3.45.2.0** | Sin conexión o modificación de base |
| HTTP | RestTemplate, conexión y lectura de 5 segundos | Sin peticiones externas |

Fuentes: [backend/pom.xml](../../backend/pom.xml), líneas **5–18, 35–38, 44–74 y 107–130**; [AppConfig.java](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java), líneas **12–17**.

No hay una incompatibilidad demostrada por ejecución. La configuración apunta a Java 17 y Spring Boot 3; no se recomienda migrar versiones en esta fase. La diferencia con el JDK 20 de la terminal debe quedar controlada al autorizar la compilación.

### 1.2. Cuatro endpoints existentes

| Método y ruta | Implementación | En OpenAPI |
|---|---|---|
| `GET /api/dashboard` | `obtenerDashboard(sector, anio)` | Sí |
| `GET /api/estado-servicios` | `obtenerEstadoServicios()` | Sí |
| `GET /api/consultoras` | `obtenerConsultoras()` | No |
| `GET /api/senace/datastream` | `consultarDatastream(nombre)` | No |

El dashboard exige `sector` en el contrato; `anio` es opcional. El controlador usa 2017 cuando no recibe año. El datastream tiene `CarteraProyectos` como valor predeterminado.

Evidencia: [openapi.yaml](../../backend/src/main/resources/openapi.yaml), líneas **9–43**; [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), líneas **39–41, 80–84 y 87–98**.

### 1.3. Arquitectura y generación

```text
API → EcoInvestController → SenaceClient / BcrpClient
                         → RestTemplate → API externa
```

No existe una capa Service propia. No es necesario introducirla para el primer lote.

`EcoInvestController` implementa `com.ecoinvest.api.ApiApi` y utiliza modelos de `com.ecoinvest.model`. Maven configura su generación desde:

```text
backend/src/main/resources/openapi.yaml
```

La configuración contiene `interfaceOnly=true`, `useSpringBoot3=true` y `skipDefaultInterface=true`.

Evidencia: controlador, líneas **3–5 y 23**; POM, líneas **107–130**.

La generación está configurada, pero no comprobada. No se inspeccionaron ni modificaron archivos bajo `target/`.

### 1.4. Datos y degradación

| Fuente | Comportamiento visible |
|---|---|
| SENACE proyectos | Hace HTTP, comprueba que exista un mapa no vacío y devuelve cuatro proyectos hardcodeados filtrados |
| SENACE consultoras | Hace HTTP, comprueba respuesta no vacía y devuelve cuatro consultoras hardcodeadas |
| SENACE datastream | Devuelve el cuerpo externo dentro de un mapa; no valida su estructura |
| BCRP | Extrae el primer valor del último período, sin seleccionar el año solicitado |
| Fallbacks | Construyen información en memoria; no consultan SQLite |
| Monitor | Expone métricas de circuit breaker y una afirmación fija de SQLite activo |

Esto acredita código existente, no funcionamiento probado.

## 2. Problemas técnicos encontrados y correcciones mínimas

### 2.1. P0 — Posible exposición de credenciales en logs

**Archivo y líneas:** [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java), líneas **107–111**, que incorporan `auth_key` a la URL; líneas **54, 78 y 97**, que registran la URL completa; líneas **123–138**, que registran mensajes de excepción potencialmente portadores de la URL.

**Causa:** se utiliza la URL autenticada como mensaje de diagnóstico.

**Consecuencia:** una credencial configurada podría terminar en consola, archivos de log o herramientas de observabilidad.

**Corrección mínima:** registrar únicamente servicio, operación y parámetros no sensibles. En errores, utilizar una categoría controlada y, cuando corresponda, el estado HTTP; evitar mensajes externos sin sanitización.

**Riesgo de regresión:** bajo; se reduce detalle de diagnóstico.

**Prueba:** utilizar una credencial ficticia identificable y capturar logs de éxito y error; comprobar que no aparece ni la credencial ni la URL autenticada.

### 2.2. P0 — Datos fijos SENACE presentados como respuesta directa

**Archivo y líneas:** `SenaceClient.java`, líneas **52–62 y 76–85**. Registros fijos desde las líneas **164 y 224**.

**Causa:** después de recibir la respuesta no se transforma su contenido. Se invocan constructores de datos fijos con origen `API_DIRECTA_SENACE`.

**Consecuencia:** el consumidor recibe proyectos, coordenadas, inversiones y consultoras que no proceden de esa consulta externa.

**Corrección mínima inmediata:** identificar explícitamente esos registros como `SIMULADO`, tanto en la ruta de éxito actual como en el fallback. Mantener temporalmente las estructuras y los endpoints.

**Corrección funcional posterior:** sustituir la construcción fija por un adaptador basado en respuestas SENACE reales y sanitizadas. No inventar el esquema externo.

**Riesgo de regresión:** medio; consumidores que dependan del texto de procedencia observarán un cambio intencional. Los registros seguirán siendo provisionales.

**Prueba:** devolver desde el mock un registro identificable y comprobar que no se presenta un registro fijo como procedente de la API. Mientras exista simulación, toda respuesta fija debe declararla.

### 2.3. P0 — BCRP y dashboard sustituyen datos ausentes por constantes

**Archivos y líneas:** [BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java), líneas **73–88**; `EcoInvestController.java`, líneas **51–71**.

**Causa:** el cliente usa `-2.45` o `3.53` cuando no obtiene un número; el controlador incorpora otra alternativa `-2.45`.

**Consecuencia:** ausencia o corrupción de información se convierte en un indicador aparentemente válido. El origen puede seguir siendo `API_DIRECTA_BCRP`.

**Corrección mínima:** representar el indicador no disponible explícitamente, sin constante sustitutiva; documentar en OpenAPI su ausencia o nulabilidad y conservar los demás campos. Las constantes del fallback, si se mantienen provisionalmente, deben declararse simuladas.

**Riesgo de regresión:** medio; el consumidor debe admitir ausencia de indicador. No hay frontend actual que estabilizar en este lote.

**Prueba:** respuesta nula, `periods` ausente o vacío, valor inválido y número no finito. Ningún caso debe convertirse silenciosamente en una variación real.

### 2.4. P1 — SQLite se anuncia como respaldo sin acceso a base

**Archivos y líneas:** `SenaceClient.java`, **122–144**; `BcrpClient.java`, **95–119**; `EcoInvestController.java`, **100–110**, especialmente **110**.

**Causa:** etiquetas, mensajes y monitor afirman uso de SQLite; los métodos solo construyen colecciones en memoria.

**Consecuencia:** se comunica persistencia y disponibilidad no verificadas.

**Corrección mínima:** declarar los fallbacks como simulados y el respaldo persistente como no implementado. Conservar el campo del monitor, cambiando su contenido para describir la realidad.

**Riesgo de regresión:** bajo a medio por cambios de texto y procedencia.

**Prueba:** invocar fallbacks directamente y verificar que no afirman recuperación desde SQLite. El monitor no debe declarar SQLite activo sin comprobación.

### 2.5. P1 — Pruebas SENACE incompatibles con el código

**Archivo y líneas:** [SenaceClientTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java), **26–45**.

**Causas:**

- El mock usa `List.class`; el cliente solicita `Map.class`.
- Se esperan registros de minería de 2024 y electricidad de 2023, inexistentes en los datos fijos.
- Se compara `Minería` con registros `Mineria`, sin normalización de acentos.

**Consecuencia:** los tests no verifican el comportamiento pretendido. Por lectura, el fallback directo de electricidad devuelve una lista vacía, contradiciendo `isNotEmpty()`.

**Corrección mínima:** ajustar el tipo del mock y separar pruebas de filtrado, ausencia de resultados, fallback y procedencia. No modificar producción para fabricar registros que satisfagan expectativas incorrectas.

**Riesgo de regresión:** bajo; evitar que las nuevas pruebas simplemente consoliden defectos actuales.

**Prueba:** casos independientes con datos controlados y expectativas justificadas.

Los tests BCRP también deben dejar de llamar “SQLite” a un fallback en memoria: [BcrpClientTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java), desde la línea **25**.

### 2.6. P1 — Año BCRP ignorado al seleccionar información

**Archivo y líneas:** `BcrpClient.java`, **53–88**.

**Causa:** `anio` solo aparece como metadato. La URL no lo incorpora y se toma el último período sin interpretar su nombre.

**Consecuencia:** el dashboard puede combinar proyectos de 2017 con un indicador de otro año.

**Corrección mínima:** seleccionar un período compatible con el año solicitado y devolver el período efectivamente utilizado. Si no existe, declarar dato no disponible.

**Dependencia:** verificar formato temporal, periodicidad y significado de cada serie. No aplicar una agregación anual inventada a series mensuales.

**Riesgo de regresión:** medio; el número devuelto puede cambiar o quedar ausente.

**Prueba:** respuesta con varios años y períodos desordenados; comprobar que el año solicitado gobierna la selección. Los casos mensuales requieren una regla de negocio definida.

### 2.7. P1 — OpenAPI no cubre toda la API

**Archivos y líneas:** [openapi.yaml](../../backend/src/main/resources/openapi.yaml), desde **8**; `EcoInvestController.java`, **87–98**.

**Causa:** consultoras y datastream se agregaron directamente al controlador.

**Consecuencia:** la fuente de verdad omite dos endpoints y no describe simulación o errores. `proyectos`, `fuentes` y métricas tienen esquemas demasiado genéricos.

**Corrección mínima inicial:** incorporar las dos rutas preservando métodos, parámetros y formas actuales; documentar procedencia y ausencia del indicador. Usar las interfaces regeneradas y evitar mappings duplicados.

**Riesgo de regresión:** medio; la regeneración puede cambiar firmas Java. Verificar antes de modificar el controlador.

**Prueba:** generación y compilación autorizadas, más pruebas HTTP de las cuatro rutas.

### 2.8. P1 — Errores externos y cuerpos inválidos sin clasificación propia

**Archivos y líneas:** `AppConfig.java`, **12–17**; `SenaceClient.java`, **56–58, 80–82 y 99–104**; `BcrpClient.java`, **60–88**.

**Causa:** se deserializa directamente a `Map.class`, sin validación suficiente ni manejo específico de respuesta inesperada.

**Consecuencia:** HTML, errores HTTP, JSON incompatible o `data=null` pueden producir excepciones o resultados engañosos.

**Corrección mínima:** validar estructuras esperadas y clasificar transporte, HTTP y formato inválido. No ampliar indiscriminadamente los convertidores para aceptar HTML como JSON.

**Riesgo de regresión:** medio; respuestas antes aceptadas superficialmente pasarían a degradación o error explícito.

**Prueba:** JSON válido, JSON malformado, HTML, Content-Type incorrecto, HTTP 401/429/500 y fallo de transporte.

### 2.9. P2 — Parámetros y normalización inconsistentes

**Archivos y líneas:** `BcrpClient.java`, **25–33 y 54–55**; `SenaceClient.java`, **94–96 y 151–160**; `EcoInvestController.java`, **39–41**; `openapi.yaml`, **14–25**.

**Causas y consecuencias:**

- Sector desconocido BCRP se transforma silenciosamente en hidrocarburos.
- `TODOS` funciona en el filtro SENACE, pero BCRP lo trata como sector desconocido.
- Acentos y alias no se resuelven uniformemente.
- Año predeterminado 2017 no documentado como tal.
- `nombre` no se valida contra el catálogo de datastreams declarado.

**Corrección mínima:** definir y documentar valores admitidos, normalizar consistentemente y no sustituir sectores desconocidos por otro. Preservar el parámetro existente `nombre`.

**Riesgo de regresión:** medio; entradas antes toleradas pueden devolver error de validación.

**Prueba:** sectores con acentos, espacios, desconocidos y `TODOS`; datastream inválido y año no admitido.

### 2.10. Aspectos pendientes de validación, sin fallo confirmado

**Resilience4j:** existen anotaciones y configuración, pero no se verificó la interceptación. El POM declara Spring Cloud Circuit Breaker, no explícitamente el starter AOP ni el starter Spring Boot de Resilience4j. Se necesita inspeccionar dependencias resueltas; esto no demuestra por sí solo que el fallback esté roto.

`SenaceClient.obtenerProyectos()` llama internamente a la sobrecarga con parámetros —líneas **65–67**—. Esa invocación interna no debe usarse para demostrar interceptación independiente de la segunda anotación.

**Validación:** existe `jakarta.validation-api`, pero no un proveedor explícito mediante `spring-boot-starter-validation`. Debe verificarse antes de depender de nuevas restricciones.

**Jackson:** no hay `ObjectMapper` propio. No se observa un problema que justifique introducirlo o migrar librerías.

**SQLite:** [application.yml](../../backend/src/main/resources/application.yml), líneas **7–13**, configura `ddl-auto: update`. Un arranque o `@SpringBootTest` podría abrir o alterar la base. Debe aislarse antes de ejecutar pruebas.

## 3. Pruebas recomendadas

No se ejecutó ninguna de las pruebas siguientes.

| Grupo | Verificación |
|---|---|
| Logs | Credencial ficticia ausente de logs de éxito y error |
| SENACE | Tipo correcto del mock, procedencia explícita y resultados vacíos válidos |
| BCRP | Valor válido, nulo, malformado, no finito y período solicitado |
| Fallback | Datos simulados identificados; ninguna afirmación falsa de SQLite |
| Resiliencia | Bean realmente interceptado, fallback activado, métricas y apertura del circuito |
| HTTP externo | Errores HTTP, HTML, Content-Type incorrecto y transporte |
| Contrato | Cuatro rutas disponibles con parámetros y respuestas documentados |
| Controlador | Ausencia de indicador sin constante oculta; fuentes consistentes |
| Aislamiento | Pruebas sin conexiones externas ni apertura de SQLite |

### 3.1. Comandos que requieren autorización previa

Directorio de ejecución previsto:

```text
D:\Documentos\GitHub\Proyecto-SOA\backend
```

Diagnóstico de dependencias:

```powershell
.\mvnw.cmd dependency:tree "-Dincludes=io.github.resilience4j:*,org.aspectj:*,org.springframework:spring-aop,org.hibernate.validator:*"
```

**Efectos posibles:** descarga del wrapper, plugins y dependencias; escritura en cachés Maven. No inicia la aplicación.

Generación y compilación:

```powershell
.\mvnw.cmd -DskipTests compile
```

**Efectos posibles:** generación OpenAPI, compilación y escritura bajo `target/`; descargas de dependencias. No ejecuta pruebas ni debería iniciar el contexto Spring.

Suite de pruebas:

```powershell
.\mvnw.cmd test
```

**Efectos posibles:** generación, compilación e informes bajo `target/`. Con las pruebas actuales puede iniciar contextos Spring y acceder a SQLite; no se recomienda ejecutarlo antes de aislarlas.

La generación por Maven sería automática y autorizada; no se editarían manualmente archivos generados. Los comandos se documentan para revisión y no se ejecutaron.

## 4. Archivos exactos que necesitan modificaciones

### 4.1. Primer lote pequeño: procedencia, seguridad y contrato

| Archivo | Modificación propuesta |
|---|---|
| `backend/src/main/resources/openapi.yaml` | Documentar cuatro rutas, procedencia y ausencia del indicador |
| `backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java` | Eliminar logs de URLs autenticadas; declarar registros fijos simulados; corregir mensajes de respaldo |
| `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java` | Eliminar sustitución silenciosa por constantes en ruta directa; declarar fallback simulado; rechazar números no finitos; evitar mensajes externos sensibles |
| `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java` | Retirar valor económico oculto; corregir afirmación SQLite activo; ajustar firmas al contrato generado |
| `backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java` | Corregir mocks y expectativas; verificar procedencia y fallos |
| `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java` | Actualizar procedencia y casos de ausencia/valores inválidos |
| `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java` —nuevo— | Verificar cuatro rutas e indicador ausente |

**Condicional:** `backend/pom.xml`, únicamente si el diagnóstico autorizado confirma falta de soporte necesario para interceptación. No agregar dependencias a ciegas.

Para ejecutar toda la suite también habría que aislar de persistencia los tests de contexto existentes, incluyendo:

- `backend/src/test/java/com/ecoinvest/soa/SoaApplicationTests.java`.
- `backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java`.

Alternativamente, configurar sus exclusiones exclusivamente en pruebas. La solución exacta debe definirse antes de ejecutar la suite.

No se propone modificar la configuración productiva de SQLite en este lote.

### 4.2. Lotes posteriores

La adaptación real SENACE afectaría principalmente `SenaceClient.java`, sus pruebas y, si el esquema lo exige, nuevos DTOs y el contrato. La selección temporal BCRP afectaría `BcrpClient.java`, sus pruebas y los campos de período del contrato. La validación de parámetros debe coordinar contrato, controlador y clientes.

No se propone una refactorización amplia ni una capa Service nueva para resolver este primer lote.

## 5. Plan de correcciones priorizado

1. **Seguridad y honestidad de datos:** logs, simulación, indicador ausente y monitor.
2. **Contrato:** añadir rutas omitidas sin cambiar URLs ni métodos.
3. **Pruebas coherentes y aisladas:** corregir expectativas y evitar SQLite.
4. **Validación autorizada de dependencias, generación y compilación.**
5. **Resiliencia:** corregir únicamente lo que el diagnóstico demuestre necesario.
6. **Integración SENACE real:** adaptar muestras verificadas.
7. **BCRP temporalmente consistente:** validar series y reglas de selección.
8. **Validación de parámetros y errores externos.**

El primer lote estabiliza la transparencia y la superficie de la API, pero no completa la integración real. Los registros SENACE seguirían siendo provisionales y explícitamente simulados hasta el lote de adaptación.

### Dependencias de planificación

- Obtener respuestas sanitizadas SENACE antes de definir su adaptador.
- Verificar unidades, formatos de período y periodicidad BCRP antes de decidir selección o agregación.
- Definir la representación contractual del indicador ausente antes de ajustar modelos y controlador.
- Verificar dependencias transitivas antes de agregar soporte de AOP o validación.
- Aislar contextos Spring de persistencia antes de ejecutar tests.
- Obtener autorización antes de modificaciones de implementación, Maven o pruebas.

## 6. Riesgos de regresión

| Riesgo | Implicación y control propuesto |
|---|---|
| Cambio de procedencia | Cambia semántica de respuesta; documentarlo y verificarlo en pruebas |
| Indicador ausente | Consumidores deben admitir ausencia; definir contrato antes de implementar |
| Regeneración OpenAPI | Puede cambiar firmas o tipos; no corregir editando target/ |
| Fecha BCRP pendiente | Etiquetar datos no resuelve selección temporal; mantenerlo como pendiente explícito |
| Lista SENACE vacía | Distinguir ausencia de proyectos de indisponibilidad; no inferir origen solo desde el primer registro |
| Fallback directo frente a AOP | Invocar método directamente no demuestra interceptación; probar el bean real |
| JDK 20 frente a objetivo 17 | Controlar entorno de compilación sin migrar el proyecto |
| Contextos de test | Pueden abrir SQLite aunque se pretenda probar HTTP; aislarlos |
| Validación más estricta | Entradas antes toleradas podrían rechazarse; documentar parámetros y respuestas |
| Respuestas externas inválidas | Casos superficialmente aceptados pasarían a degradación explícita; probar formato y errores |

No se certificó disponibilidad de APIs, funcionamiento de SQLite, generación correcta o éxito de pruebas mediante ejecución.

## 7. Criterios de aceptación del primer lote

- Se conservan las cuatro rutas y sus métodos HTTP.
- Las cuatro aparecen en OpenAPI.
- No cambian Java, Spring Boot ni paquetes existentes.
- No se incorporan funcionalidades ajenas a EcoInvest.
- Ningún registro fijo se presenta como dato directo o respaldo SQLite.
- Un indicador ausente no se convierte silenciosamente en `-2.45` o `3.53`.
- El monitor no afirma disponibilidad de persistencia sin comprobarla.
- Las credenciales no aparecen en logs.
- Los tests usan tipos y expectativas coherentes.
- Las pruebas no abren SQLite ni llaman a APIs reales.
- La generación, compilación y ejecución solo se realizan tras autorización.
- No se modifica frontend, base de datos ni archivos generados manualmente.

La aceptación del primer lote no implica que SENACE ya esté integrado con datos reales ni que la selección temporal BCRP esté completada. Esas capacidades requieren sus lotes y verificaciones específicos.

## RESULTADO PARA REVISIÓN CON CHATGPT

**EcoInvest tiene un backend parcial SENACE + BCRP con cuatro endpoints existentes; dos faltan en OpenAPI.** Los principales defectos confirmados son datos SENACE hardcodeados etiquetados como directos, constantes BCRP que ocultan ausencia de información, fallbacks falsamente identificados como SQLite, URLs autenticadas registradas y pruebas SENACE contradictorias.

La interceptación Resilience4j y las dependencias transitivas requieren verificación; no deben declararse rotas sin evidencia. Java del proyecto es 17; el comando `java` de la terminal inspeccionada corresponde a 20.0.2.

**Primer lote recomendado:** corregir logs y procedencia, retirar indicadores ficticios implícitos, documentar las cuatro rutas y alinear pruebas aisladas de SQLite. Después completar parseo SENACE y selección temporal BCRP con muestras verificadas.

**Rutas que deben conservarse:**

```text
GET /api/dashboard
GET /api/estado-servicios
GET /api/consultoras
GET /api/senace/datastream
```

**Archivos centrales para continuar:** POM, contrato y application.yml bajo backend/; EcoInvestController, SenaceClient, BcrpClient y AppConfig en sus paquetes actuales; tests de clientes y contexto. No editar target/, cambiar versiones o paquetes ni tocar SQLite sin autorización.

**Alcance definitivo:** EcoInvest integra SENACE y BCRP para Dashboard Analítico, Explorador Geográfico, Registro de Consultoras Ambientales y Monitor de Salud SOA. No incorporar OEFA o ESG. El frontend React + TypeScript se abordará después de estabilizar el backend.

**Estado de autorización:** no se implementó ninguna corrección, ejecutó prueba, instaló dependencia ni alteró el backend o frontend. Solo se autorizó crear este reporte en la raíz. La implementación y los comandos Maven continúan pendientes de autorización expresa.
