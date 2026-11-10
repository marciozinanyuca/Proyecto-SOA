# EcoInvest — Implementación Fase 3B.2, Lote 2: transporte y configuración SENACE

**Fecha:** 03/11/2026, zona America/Lima.  
**Estado:** implementación compilada y validada mediante pruebas aisladas; integración con SenaceClient todavía pendiente.  
**Runtime acreditado:** Oracle JDK 17.0.12.  
**Resultado de la ejecución seleccionada:** BUILD SUCCESS; 71 casos, 0 fallos, 0 errores y 0 omitidos.  
**Siguiente lote:** Lote 3, sujeto a una nueva solicitud y autorización. No se inició automáticamente.

## 1. Objetivo

Preparar un transporte HTTP exclusivo para los dos recursos SENACE verificados, con autenticación en memoria, destinos restringidos, lectura acotada y presupuestos temporales independientes. Mantener aislados BCRP y el RestTemplate existente y no conectar todavía el nuevo transporte al cliente o controlador productivos.

Este reporte documenta código inspeccionado, cambios autorizados y ejecuciones realizadas por Codex. No contiene credenciales, respuestas reales del proveedor ni volcados completos de propiedades de Surefire.

Referencias de continuidad:

- [Reglas del repositorio](../../AGENTS.md).
- [Decisiones aprobadas D01–D19](REPORTE_DECISIONES_FASE3B1_SENACE.md).
- [Diseño Fase 3B.1](REPORTE_DISENO_FASE3B1_SENACE.md).
- [Implementación y pruebas del Lote 1](REPORTE_IMPLEMENTACION_FASE3B2_LOTE1.md).
- [Validación SENACE, incluida actualización vigente del 02/11/2026](REPORTE_FASE3A_VALIDACION_SENACE.md).
- [Cierre técnico BCRP Fase 2C](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md).

Los números de línea de este documento corresponden al código inspeccionado al cerrar este lote; pueden cambiar posteriormente.

## 2. Estado inicial

EcoInvest utiliza Java 17, Spring Boot 3.2.4, Maven Wrapper y OpenAPI Generator 7.4.0. El contrato fuente permanece en backend/src/main/resources/openapi.yaml. BCRP Fase 2C está cerrado dentro de su alcance específico y no se reabrió.

El Lote 1 ya contenía los siguientes componentes, que no se modificaron:

| Componente | Función existente |
|---|---|
| ProyectoSenaceDto | Trece propiedades oficiales; ID Long validado, textos originales y coordenadas BigDecimal |
| ConsultoraSenaceDto | Ocho propiedades oficiales; sin RUC, registro, nivel, estado o identidad inventados |
| ResultadoConsultaSenace<T> | Filas, procedencia, estados, cobertura NO_VERIFICADA y conteos independientes |
| SenaceMapper | Transformaciones puras, fecha estricta, filtros, conteos y calidad explícita |
| SenaceMapperTest | 53 casos satisfactorios documentados en una ejecución anterior |

Las 53 pruebas del Lote 1 no se volvieron a ejecutar en esta selección; no deben sumarse a las 71 actuales como si fueran una sola suite.

La evidencia previa SENACE corresponde a arrays de CarteraProyectos, con 2.567 registros, y ConsultorasAmbientales, con 15.015 filas. Esas cantidades son observaciones históricas, no constantes de negocio ni prueba de cobertura global. No hubo consultas al proveedor en el Lote 2.

SenaceClient y EcoInvestController conservaban su comportamiento anterior, incluido el uso de registros SIMULADO. El nuevo transporte no estaba creado. AppConfig construía el RestTemplate compartido con timeouts de conexión y lectura de cinco segundos, dejando la selección de implementación a Spring Boot.

El índice documental conserva estados históricos anteriores y no se actualizó mediante este encargo. Para continuar debe prevalecer la evidencia más reciente de este reporte y del Lote 1 sobre una fila antigua del índice.

## 3. Decisiones aplicadas y autorizaciones

Se aplicaron principalmente D14–D17: configuración explícita, aislamiento SENACE, sin reintentos ni redirecciones automáticas, límite inicial de 6 MiB y presupuestos de conexión 5 s, inactividad 10 s y total HTTP 25 s. Se preservaron las decisiones de mapeo del Lote 1 y no se añadieron reglas de negocio.

La implementación utilizó autorizaciones separadas:

1. El usuario aprobó crear transporte y tests, modificar application.yml y añadir HttpClient 5 al POM. También aprobó un byte adicional de comprobación para detectar exceso sobre un cuerpo de longitud desconocida.
2. Se detectó que añadir HttpClient 5 activaría su selección automática para el RestTemplate compartido. Se solicitó y obtuvo permiso adicional para AppConfig.java y AppConfigTest.java, exclusivamente para fijar su implementación anterior.
3. Se solicitaron y autorizaron compilación offline y pruebas específicas, indicando expresamente el servidor efímero de loopback.
4. Tras obtener resultados se solicitó y obtuvo permiso para crear este reporte. Esa autorización no incluye actualizar README, otros reportes o comenzar el Lote 3.

No se instalaron herramientas o dependencias por red, no se ejecutó Spring Boot completo y no se hicieron commits.

## 4. Archivos creados y modificados

### Archivos creados para la implementación

| Archivo | Contenido y evidencia |
|---|---|
| [SenaceTransport.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceTransport.java) | Componente interno, recursos permitidos, respuesta segura, presupuestos, sesión Apache y consumidor de streaming; 469 líneas al revisar |
| [SenaceTransportTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceTransportTest.java) | Fixtures artificiales, 61 casos sin red y nueve casos opt-in con HTTP local; 633 líneas al revisar |

### Archivos existentes modificados con autorización

| Archivo | Cambio limitado |
|---|---|
| [backend/pom.xml](../../backend/pom.xml) | Dependencia org.apache.httpcomponents.client5:httpclient5 sin versión explícita; administrada por Spring Boot |
| [application.yml](../../backend/src/main/resources/application.yml) | Puente explícito de variables SENACE y desactivación de logs Apache capaces de divulgar URI/cuerpo |
| [AppConfig.java](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java) | Selección explícita de SimpleClientHttpRequestFactory; conserva conexión/lectura de 5 s, líneas 15–20 |
| [AppConfigTest.java](../../backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java) | Verifica tipo de factory y ambos timeouts de 5.000 ms, líneas 15–20 |

El presente Markdown es una creación documental posterior y autorizada por separado. No se añadieron archivos auxiliares, scripts, fixtures externos o un nuevo Service.

### Aislamiento comprobado

Se registraron hashes SHA-256 de 14 archivos previos bajo backend/src fuera del alcance modificable. Permanecieron iguales después de implementar y después de ejecutar Maven. Solo aparecieron los dos archivos nuevos previstos de fuente/test, además de las cuatro modificaciones autorizadas.

Permanecieron sin cambios los componentes del Lote 1, BcrpClient, SenaceClient, EcoInvestController y el contrato OpenAPI. Los bloques de configuración BCRP, SQLite y Resilience4j tampoco se alteraron. No se modificó frontend.

git diff --check no detectó problemas. Git mostró los cuatro archivos existentes modificados y los dos nuevos sin seguimiento. Se observó una advertencia de normalización LF/CRLF del POM; no es un fallo de compilación ni se ejecutó un formateo general.

## 5. Diseño del transporte

### 5.1. Tecnología y razón de selección

Se utiliza Apache HttpClient 5 asíncrono, versión 5.2.3 administrada por Spring Boot 3.2.4; HttpCore 5.2.4 también está administrado por ese BOM. Los artefactos y fuentes pertinentes ya estaban disponibles localmente. Los comandos Maven se ejecutaron con -o.

La inspección de las fuentes locales de JDK 17 mostró que java.net.http puede reintentar conexiones de manera interna: java.net.http/jdk/internal/net/http/MultiExchange.java, líneas 465–548, incluye RETRY_CONNECT habilitado por defecto. Desactivarlo mediante propiedades globales de JVM no ofrecía el aislamiento solicitado. Se obtuvo permiso antes de incorporar la dependencia alternativa.

También se inspeccionaron fuentes locales de Spring Boot: ClientHttpRequestFactories.get selecciona HttpComponents cuando detecta la biblioteca. El classpath Surefire previo no contenía HttpClient 5. Por eso se fijó SimpleClientHttpRequestFactory explícitamente en AppConfig, en lugar de aceptar un cambio incidental en el transporte de BCRP.

### 5.2. Entrada y salida internas

Firma pública:

```java
RespuestaTransporte consultar(Recurso recurso);
```

Recurso es un enum cerrado con:

| Enum | Ruta aprobada, sin credencial |
|---|---|
| CARTERA_PROYECTOS | https://datosabiertos.senace.gob.pe/Api/datastreams/CarteraProyectos |
| CONSULTORAS_AMBIENTALES | https://datosabiertos.senace.gob.pe/Api/datastreams/ConsultorasAmbientales |

No se añade /data.json. Otros seis datastreams del cliente antiguo no se eliminaron, investigaron ni conectaron al transporte nuevo.

RespuestaTransporte contiene estado, código HTTP opcional, tipo de contenido clasificado, Content-Type reducido a valores seguros, bytes leídos, longitud declarada opcional, cuerpoCompleto y cuerpo opcional. Su toString excluye cuerpo, credencial, URI y mensajes externos. Los errores no pueden llevar cuerpo publicable: el constructor rechaza esa combinación.

Solo HTTP 200 con cuerpo completo, UTF-8 válido, un único documento JSON y raíz array produce RECIBIDO con cuerpo. La validación de objetos/fila y el mapeo a los DTO del Lote 1 se integrarán en el cliente durante el Lote 3. No se confunde un array recibido con datos de negocio ya validados.

En HTTP de error se conserva el código y se descarta el cuerpo para el consumidor. HTML se identifica por contenido; los demás cuerpos de error pueden conservar tipo DESCONOCIDO porque no se valida su JSON de negocio. No se afirma que un Content-Type application/json acredite JSON válido.

### 5.3. Restricción de destino

La validación de base, líneas 180 y siguientes, permite exclusivamente:

- Esquema HTTPS.
- Host exacto datosabiertos.senace.gob.pe, sin subdominios o sufijos alternativos.
- Puerto implícito o 443.
- Ruta /Api/datastreams, con barra final opcional.
- Sin userinfo, query o fragmento aportados por configuración.

Las rutas finales proceden del enum, no de una cadena arbitraria. La clave se codifica y agrega en memoria mediante auth_key. Configuración ausente o inválida se rechaza antes de abrir una sesión HTTP.

### 5.4. Sesión y recursos

ApacheSession, líneas 232 y siguientes, crea una instancia aislada por consulta, con una conexión máxima y un reactor de una hebra. Se desactivan reintentos automáticos, redirecciones, cookies y autenticación HTTP automática. Se usa HTTP/1 y Connection: close.

El TLS utiliza SSLContexts.createSystemDefault y la verificación normal de hostname de la biblioteca. No se introducen trust-all, certificados importados o verificadores permisivos.

El despacho inicial utiliza el ejecutor administrado por Apache. La cancelación del Future y el cierre inmediato de la sesión se realizan en los caminos de salida. No se creó un pool global ni un temporizador propio sin gestión de cierre.

El resolver consulta una sola dirección de las devueltas, evitando failover implícito entre direcciones. Comprueba cancelación/plazo antes y después de resolver. Esto puede reducir disponibilidad frente a una dirección inaccesible, de forma deliberada para no incorporar intentos adicionales silenciosos.

Una llamada DNS nativa del sistema puede resistir interrupción. No se garantiza su terminación forzosa al vencer el plazo: se cancela el intercambio, se cierra la sesión y se comprueba que ese resultado tardío no continúe normalmente hacia una nueva conexión. La prueba no simuló bloqueo nativo de DNS ni certificó terminación instantánea de todas las hebras internas.

## 6. Configuración local y nube

El puente implementado en application.yml es:

```yaml
external:
  apis:
    senace-base-url: ${SENACE_BASE_URL:}
    senace-auth-key: ${SENACE_AUTH_KEY:}
```

No se incorporó ninguna credencial predeterminada al nuevo transporte. Los placeholders vacíos permiten construir el componente sin fallar por una variable ausente. Una consulta devuelve CONFIGURACION_AUSENTE y no realiza HTTP si falta un valor necesario.

Esto describe el transporte nuevo, no la corrección del cliente antiguo: el default histórico de SenaceClient todavía debe retirarse en el lote posterior autorizado.

### Desarrollo local

- No se leyó, modificó ni importó automáticamente backend/.env.apis.local.
- Spring Boot no carga ese archivo por su existencia.
- El mecanismo actualmente implementado requiere variables disponibles en el proceso Java, o propiedades Spring equivalentes suministradas explícitamente.
- No se añadió un import relativo dependiente del directorio de ejecución.
- La eventual importación local explícita con ruta absoluta y formato compatible queda como opción por concretar y validar, no como funcionalidad implementada aquí.

Por tanto, no se certificó un arranque desde raíz, backend e IDE cargando ese archivo. El puente de propiedades es independiente del cwd, pero cada lanzador debe proporcionar su entorno. No se alteró el .env de la raíz.

### Despliegue

Proporcionar SENACE_BASE_URL y SENACE_AUTH_KEY mediante configuración del servicio de alojamiento o su mecanismo de credenciales. No incorporar el archivo local a imagen, repositorio o fixture. No se eligió ni configuró un proveedor de nube.

### Logs

Se añadieron niveles OFF entrecomillados para org.apache.hc.client5.http y org.apache.hc.core5.http. Los mensajes de diagnóstico y wire de esas bibliotecas pueden contener URI o cuerpos. Esta decisión evita depender de que nadie active DEBUG sobre el nuevo cliente por accidente. Debe conservarse en el despliegue; una sobrescritura externa de logging puede invalidar esa protección.

El transporte no registra URLs autenticadas, cabeceras, cookies, cuerpos o cadenas de causas. Los tests de configuración verifican esas propiedades. Las pruebas locales desactivan temporalmente esas categorías y restauran sus niveles al finalizar.

## 7. Límites de tamaño y timeout implementados

| Control | Configuración productiva | Mecanismo y alcance |
|---|---|---|
| Conexión | 5 s | ConnectionConfig, timeout TLS y presupuesto de fase vigilado por el llamador hasta el marcador de conexión |
| Inactividad | 10 s | Configuración de socket/respuesta y control de último avance del consumidor |
| Total HTTP | 25 s | Deadline monotónico iniciado antes de crear la sesión, espera acotada del Future, cancelación y cierre |
| Cuerpo aceptado | 6.291.456 bytes | Consumidor incremental con capacidad limitada y rechazo antes de almacenar más cuerpo |
| Comprobación de exceso | Un byte adicional aprobado | Byte centinela contabilizado y descartado; no se devuelve un prefijo como JSON |

Los timeouts de conexión e inactividad no se presentan como sustituto del total. El bucle de espera recalcula presupuestos y espera como máximo 100 ms o el plazo menor restante. Una respuesta con progreso continuo puede vencer el presupuesto total.

### Casos de volumen y framing

- Content-Length mayor que el máximo: rechazo antes de almacenar el cuerpo, también en HTTP de error.
- Content-Length ausente o transferencia fragmentada: capacidad incremental y límite real del consumidor.
- Cuerpo exactamente igual al límite: aceptado si se recibe el final y el JSON es válido; se verificó un fixture de 6 MiB sin longitud declarada.
- Cuerpo superior: se consume como máximo un byte centinela adicional en el consumidor y se rechaza todo el resultado publicable.
- Final prematuro frente a longitud declarada: RESPUESTA_INCOMPLETA, sin cuerpo publicable.
- Los buffers TCP/TLS/HTTP pueden haber precargado más datos antes del consumidor. El límite no es un tope de tráfico del servidor ni de bytes de paquetes de red.
- Si un servidor declara una longitud menor, el parser HTTP delimita la entidad por el framing recibido. El componente no certifica bytes ajenos a esa entidad como parte del dataset. Connection: close evita reutilizar la conexión; JSON y cobertura continúan siendo verificaciones distintas.

### Codificación y compresión

Se solicita Accept-Encoding: identity y se desactiva la compresión en RequestConfig. Se rechazan codificaciones de contenido inesperadas; no hay descompresión de un cuerpo arbitrariamente grande. UTF-8 se decodifica en modo estricto, sin reemplazar bytes inválidos. Charset declarado diferente del admitido se clasifica de forma segura.

Un Content-Type text/html no basta por sí solo para rechazar JSON genuino: el contenido completo determina si se trata de HTML o JSON. HTML real, JSON inválido, documentos múltiples y raíz distinta de array se rechazan.

### Límites de la evidencia temporal

Los tests utilizan presupuestos reducidos para hacer reproducibles conexión, inactividad y total sin esperar 25 segundos por caso. La integración local comprobó cancelación con progreso continuo y que el servidor observó el cierre de la conexión. No certificó un límite de tiempo real estricto bajo toda planificación del sistema, pausas de JVM, DNS nativo bloqueado o tiempos de liberación.

El presupuesto HTTP y la limpieza de recursos no son garantías de tiempo real para cualquier trabajo de CPU. La validación JSON acotada se ejecuta al finalizar el cuerpo; su coste y la sincronización del consumidor no se sometieron a una prueba de rendimiento adversarial de 6 MiB. Ese límite debe conservarse explícito al evaluar requisitos futuros de latencia estricta.

## 8. Clasificación segura de errores

| Estado interno | Significado y tratamiento |
|---|---|
| RECIBIDO | HTTP 200 y array JSON completo válido a nivel sintáctico; todavía requiere deserialización y mapeo de filas |
| CONFIGURACION_AUSENTE | Falta URL base o clave; sin intento HTTP |
| CONFIGURACION_INVALIDA | Credencial con caracteres de control; sin intento HTTP |
| DESTINO_INVALIDO / RECURSO_INVALIDO | Base o recurso no permitido; sin intento HTTP |
| REDIRECCION_RECHAZADA | Código 3xx; no seguir Location ni reenviar auth_key |
| ERROR_HTTP | Respuesta distinta de 200/3xx tratada como error; conservar código, no publicar cuerpo |
| ERROR_TLS | Excepción TLS identificada por tipo; sin debilitar validación |
| TIMEOUT_CONEXION | Conexión/fase previa al envío fuera de presupuesto |
| TIMEOUT_INACTIVIDAD | Inactividad de socket o consumidor |
| PLAZO_TOTAL_AGOTADO | Presupuesto global vencido; cancelación y cierre |
| ERROR_TRANSPORTE | Fallo de I/O u otra causa no clasificada; mensaje externo descartado |
| LIMITE_TAMANO | Longitud excesiva o byte centinela observado; sin prefijo publicable |
| RESPUESTA_INCOMPLETA | Fin prematuro o inconsistencia de longitud observable |
| HTML_INESPERADO | HTML real en respuesta 200, sin divulgarlo |
| JSON_INVALIDO | Sintaxis inválida, cuerpo vacío o documentos múltiples |
| ESTRUCTURA_INVALIDA | JSON válido cuya raíz no es array |
| CODIFICACION_NO_ADMITIDA | Compresión/charset no admitidos o UTF-8 inválido |
| INTERRUMPIDO | Llamador interrumpido o intercambio cancelado; conservar interrupción cuando corresponda |

Los fallos propios utilizan mensajes fijos basados en enum. Las causas externas se examinan por clase y no se reenvían. El Content-Type público se reduce a valores reconocidos u otro; no se conserva una cabecera arbitraria como dato de diagnóstico.

cuerpoCompleto refleja el final observable de la entidad HTTP, no validez de negocio ni cobertura del dataset. Por ejemplo, un HTML completamente recibido sigue siendo inválido. El transporte no genera métricas, fechas, registros simulados o cifras sustitutivas.

No se asignaron todavía códigos HTTP públicos de dashboard/consultoras: corresponden al controlador y contrato de un lote posterior. Tampoco se añadieron anotaciones o políticas nuevas de Resilience4j.

## 9. Pruebas realmente ejecutadas

### 9.1. Directorio, runtime y comandos

Directorio de ejecución:

```text
D:\Documentos\GitHub\Proyecto-SOA\backend
```

Compilación autorizada:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests compile
```

Pruebas autorizadas, en otro proceso temporal con el mismo JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -o '-Dtest=SenaceTransportTest,AppConfigTest' '-Decoinvest.test.local-http=true' test
```

JAVA_HOME y PATH solo se alteraron en los procesos de ejecución, sin configuración permanente del sistema. java -version, Maven y propiedades seleccionadas de Surefire confirmaron Java 17.0.12, Oracle, con java.home=C:\Program Files\Java\jdk-17. Maven Wrapper utilizó Maven 3.9.9.

### 9.2. Resultados acreditados

| Operación | Resultado | Finalización registrada |
|---|---|---|
| Generación OpenAPI y compile | BUILD SUCCESS; 15 fuentes con release 17; salida 0 | 03/11/2026 00:32:27 -05:00 |
| Selección SenaceTransportTest y AppConfigTest | BUILD SUCCESS; 71 casos; 0 fallos, 0 errores, 0 omitidos; salida 0 | 03/11/2026 00:33:07 -05:00 |

La compilación registró 16,251 s y la ejecución Maven de pruebas 24,203 s. Se compilaron siete archivos de tests, pero solo se ejecutó la selección indicada. OpenAPI se regeneró bajo target/ a partir del contrato intacto; no hubo edición manual de artefactos generados.

### 9.3. Composición de los 71 casos

| Grupo | Casos ejecutados | Frontera |
|---|---|---|
| SenaceTransportTest | 61 | Sesiones/consumidor simulados, sin sockets |
| SenaceTransportTest.LocalHttp | 9 | Cliente Apache real contra servidor efímero 127.0.0.1 |
| AppConfigTest | 1 | Construcción directa del bean; sin contexto productivo |

La salida/XML de Surefire presenta una particularidad de agrupación: el archivo de la clase LocalHttp contiene 70 casos, de los cuales 61 tienen classname de la clase exterior y nueve de la anidada; el XML exterior declara cero. Se inspeccionaron los classname de los testcase para obtener el desglose. No se contaron 70 como si todos fueran casos HTTP locales ni se duplicaron los casos entre XML.

### 9.4. Cobertura sin red

Los 61 casos verificaron, entre otros:

- Recursos y URI oficiales, codificación de clave ficticia y ausencia de /data.json.
- Ausencia de configuración y rechazo previo de host, esquema, puerto, userinfo, query, fragmento o ruta no admitidos.
- Redirecciones 301/302/303/307/308 y errores 400/401/403/500/503 mediante fixtures.
- JSON genuino con Content-Type text/html, HTML genuino con Content-Type application/json, JSON inválido, documentos múltiples y raíz no array.
- Compatibilidad de una respuesta artificial con DTO y mapper existentes, conservando precisión decimal.
- Array artificial de exactamente 6 MiB sin Content-Length, byte de exceso, longitud declarada excesiva y fin prematuro.
- Límite en cuerpos de error, capacidad de streaming, compresión/charset no admitidos y UTF-8 inválido.
- Presupuestos distintos de conexión, inactividad y total mediante Future pendientes; cancelación e interrupción.
- Clasificación por tipos de excepción y ausencia de contenido sensible en representaciones de resultado.
- Puente de propiedades y configuración de logs sin cargar el archivo local de credenciales.

Los valores de prueba son artificiales. Ningún fixture contiene personas reales, un JWT o una clave utilizable.

### 9.5. Nueve casos HTTP locales

La clase anidada LocalHttp está condicionada por:

```text
ecoinvest.test.local-http=true
```

Sin esa propiedad el grupo no debe iniciar el servidor. En la ejecución autorizada sí estuvo habilitada.

Los casos comprobaron:

1. JSON fragmentado sin Content-Length.
2. Rechazo de redirección sin petición al destino Location.
3. Ausencia de reintento ante 503 con Retry-After.
4. Rechazo de HTML real.
5. Aceptación de JSON etiquetado text/html.
6. Rechazo de fin anticipado respecto de longitud declarada.
7. Rechazo de exceso durante streaming.
8. Timeout de inactividad de un cuerpo lento, sin nueva petición.
9. Timeout total pese a progreso continuo y cierre observado por el servidor.

La fixture sustituye el destino únicamente dentro de la Session inyectada por el test. El método público sigue validando la base oficial; no se habilita localhost en producción. La solicitud real al servidor artificial no incorpora siquiera la clave ficticia construida para la validación de URI.

Se usó HTTP de loopback, no HTTPS ni el proveedor. No se deshabilitó TLS: estos casos no ejercitan TLS. El servidor utilizó puerto efímero, pool acotado y cierre en AfterEach; se comprobó la terminación de sus workers.

### 9.6. Artefactos de evidencia

- [XML de transporte y grupo local](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceTransportTest$LocalHttp.xml).
- [XML de la clase exterior](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceTransportTest.xml).
- [XML AppConfigTest](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.config.AppConfigTest.xml).

Los XML son volátiles y pueden contener propiedades del entorno. No compartirlos íntegramente sin revisión. Este documento conserva exclusivamente runtime, resultados y límites pertinentes.

## 10. Resultados, errores y limitaciones

### Resultado de esta implementación

La primera compilación y la primera ejecución de esta selección terminaron satisfactoriamente. No hubo un fallo de pruebas seguido de correcciones que deba ocultarse o confundirse con el resultado final.

Se registraron advertencias, no errores:

- SenaceTransport utiliza o sobrescribe una API marcada como obsoleta por las bibliotecas. No se ejecutó una compilación adicional con Xlint para identificar el símbolo exacto; no se inventa aquí su origen concreto.
- SenaceTransportTest contiene operaciones genéricas no comprobadas, relacionadas con la preparación de pruebas. No impidieron compilación o ejecución.
- La JVM informó sobre class sharing por bootstrap classpath ampliado durante los tests.
- OpenAPI Generator emitió su aviso habitual sobre postprocesamiento Java no configurado. No se instaló ni ejecutó un formateador.

Estas advertencias quedan documentadas; no justificaron refactorizaciones o una nueva ejecución sin necesidad.

### SQLite y restricciones

No se inició contexto productivo, no se abrió una conexión SQLite y no se accedió a tablas. Solo se revisaron metadatos de backend/ecoinvest.db: longitud 0 bytes y LastWriteTime 12/10/2026 22:19:49, anterior al lote. Esto no acredita persistencia implementada ni permite afirmar que exista respaldo operativo.

No se ejecutaron solicitudes HTTP a SENACE, BCRP u otros proveedores. Los únicos sockets de aplicación autorizados fueron los de loopback de las pruebas. Maven se ejecutó offline; no se instalaron herramientas ni se cambiaron versiones del proyecto.

### Límites que deben conservarse en la continuidad

1. No hay integración end-to-end ni cambio funcional de endpoints: SenaceClient aún no invoca SenaceTransport.
2. El transporte comprueba sintaxis y raíz array; el esquema de cada fila y su mapeo se integrarán después.
3. Las pruebas locales no certifican TLS real SENACE, compatibilidad de credencial actual o disponibilidad del proveedor.
4. El timeout total y la cancelación se probaron con presupuestos reducidos y escenarios locales concretos, no con toda condición de DNS/SO/JVM.
5. La terminación de DNS nativo y de todas las hebras internas de Apache no se midió exhaustivamente. No afirmar garantía de parada forzosa universal.
6. Un cuerpo completo y JSON válido no certifican paginación, cobertura histórica o identidad empresarial.
7. No se verificó carga del archivo local desde raíz, backend e IDE; no existe import automático.
8. La protección de logs depende también de mantener los niveles seguros y no volcar respuestas desde consumidores futuros.
9. El RestTemplate compartido mantiene factory/timeouts comprobados; no se repitió toda la batería BCRP ni se afirma ausencia de cualquier cambio transitorio de classpath por inspección sola.
10. No se midieron carga concurrente, memoria total o rendimiento adversarial del parseo de 6 MiB. El límite de cuerpo no equivale a máximo de memoria de todo el proceso.

## 11. Estado final del Lote 2

| Criterio | Estado comprobado |
|---|---|
| Transporte exclusivo SENACE creado | Implementado y compilado |
| Recursos y destinos restringidos | Probados sin red; no se modificó la lista del cliente antiguo |
| Configuración explícita y ausencia segura | Implementadas y probadas en el componente nuevo |
| Límite de 6 MiB y centinela | Probados con fixtures y exceso en HTTP local |
| Conexión/inactividad/total | Implementados; pruebas de presupuesto y escenarios HTTP locales satisfactorias |
| Sin redirecciones ni reintentos | Configurados; verificados en los casos preparados, incluido 503 real de loopback |
| Aislamiento de RestTemplate | Factory Simple y timeouts 5 s comprobados por AppConfigTest |
| Compilación y selección de pruebas | BUILD SUCCESS, 71 casos satisfactorios |
| Integración productiva SENACE | Pendiente, fuera del Lote 2 |
| TLS real, cobertura y end-to-end | No certificados por este lote |

El lote queda implementado y validado dentro del alcance aislado descrito. No equivale al cierre de toda Fase 3B.2 ni elimina la simulación del cliente actual.

## 12. Pendientes del Lote 3

El siguiente encargo debe inspeccionar y proponer, antes de modificar:

- Integración de SenaceClient con SenaceTransport y los DTOs del Lote 1.
- Deserialización tipada de arrays, validación estructural y rechazo honesto de filas inválidas.
- Mapeo real de proyectos y consultoras mediante SenaceMapper, conservando originales, precisión y reglas aprobadas.
- Retirada del default histórico de credencial y de la simulación automática del camino real, dentro del alcance aprobado para ese lote.
- Traducción del resultado de transporte a ResultadoConsultaSenace, sin deducir procedencia de la primera fila ni atribuir cobertura global.
- Traslado explícito de cuerpoCompleto desde la evidencia HTTP; el mapper por sí solo lo mantiene desconocido.
- Compatibilidad con firmas llamadas por el controlador y con fallbacks de Resilience4j. No cambiar políticas o nombres de breakers para adaptar tipos por conveniencia.
- Preservación del endpoint genérico y de los seis recursos no investigados; no aplicarles automáticamente las rutas/esquemas de los dos recursos verificados.
- Tests simulados de integración cliente–transporte–mapper, sin SQLite ni consultas reales.

Los candidatos principales a leer/modificar son SenaceClient.java y SenaceClientTest.java, además de leer SenaceTransport, ResultadoConsultaSenace, SenaceMapper y ambos DTOs. Cualquier adaptación adicional debe proponerse y autorizarse expresamente.

Las políticas públicas, metadatos HTTP, CORS, controlador y OpenAPI deben seguir la división por lotes aprobada. No trasladar automáticamente todos esos cambios al Lote 3 si el encargo mantiene su integración pública para el Lote 4.

## 13. Siguiente paso recomendado

Compartir este reporte con ChatGPT para preparar un prompt exclusivo del Lote 3. Ese prompt debe partir de los componentes ya creados, inspeccionar sus firmas y acordar cómo mantener compilación/compatibilidad durante la transición del cliente.

Antes de implementar, aprobar los archivos concretos y los cambios de firma necesarios. Antes de ejecutar Maven, solicitar autorización con comandos offline, JDK 17.0.12 y efectos en target/. No hacer HTTP real salvo una fase separada con presupuesto explícito.

No modificar BcrpClient, catálogo económico, Resilience4j, SQLite o frontend por proximidad. No repetir las fases BCRP cerradas. No recrear DTOs/mapper ni afirmar que los endpoints ya consumen SENACE real.

En la creación de este reporte no se ejecutaron nuevamente Maven o tests y no se modificó documentación histórica, índice o código productivo.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó la implementación aislada de Fase 3B.2 Lote 2 bajo Java 17/Oracle JDK 17.0.12 y Spring Boot 3.2.4. Se crearon SenaceTransport.java y SenaceTransportTest.java; se modificaron pom.xml, application.yml, AppConfig.java y AppConfigTest.java con autorización. El transporte todavía no está conectado a SenaceClient o EcoInvestController: los endpoints mantienen el comportamiento anterior, incluida simulación SENACE.

El transporte usa HttpClient 5 asíncrono 5.2.3, versión administrada por Boot y disponible localmente. Permite únicamente los recursos CarteraProyectos y ConsultorasAmbientales mediante rutas oficiales sin /data.json, valida HTTPS/host/puerto/path, agrega auth_key en memoria y desactiva redirecciones/reintentos/cookies/autenticación automática. Utiliza TLS normal. Retorna cuerpo solo ante HTTP 200, UTF-8 válido y un documento JSON completo con raíz array; errores llevan estados seguros, sin cuerpos o causas externas publicables.

Se implementaron límite incremental de 6.291.456 bytes más un byte centinela aprobado, conexión 5 s, inactividad 10 s y presupuesto total HTTP 25 s con cancelación y cierre. No confundir límite del consumidor con bytes de red precargados, ni cuerpo completo con cobertura oficial. DNS nativo puede no terminar inmediatamente; no hay garantía de tiempo real universal ni evaluación exhaustiva de parseo/limpieza bajo condiciones adversas.

application.yml mapea SENACE_BASE_URL y SENACE_AUTH_KEY a external.apis.senace-base-url y external.apis.senace-auth-key con ausencia permitida, y desactiva logs Apache capaces de exponer URI/cuerpos. No se leyó o importó .env.apis.local. La configuración del entorno debe llegar al proceso; una importación local explícita queda pendiente de concretar. El default antiguo de SenaceClient todavía no fue retirado.

Añadir HttpClient habría cambiado la autodetección del RestTemplate compartido. Con permiso adicional se fijó SimpleClientHttpRequestFactory, conservando timeouts de 5 s, y se añadió la comprobación correspondiente. Los 14 archivos previos fuera del alcance conservan sus hashes, incluidos los componentes del Lote 1 y BCRP/cliente/controlador/contrato.

Se ejecutaron compile offline y la selección SenaceTransportTest,AppConfigTest con ecoinvest.test.local-http=true. Compilación BUILD SUCCESS el 03/11/2026 00:32:27 -05:00; pruebas BUILD SUCCESS a las 00:33:07: 71 casos, cero fallos/errores/omitidos. Desglose real: 61 sin red, nueve con cliente Apache contra loopback y uno de AppConfig. Los XML agrupan los 70 primeros bajo el reporte anidado; no duplicar ese conteo. Hay advertencias de API obsoleta y genéricos no comprobados, no fallos. No se repitieron las 53 pruebas previas del mapper ni la batería BCRP.

No se consultó SENACE real, no se inició Spring Boot completo, no se accedió a SQLite, no se instalaron dependencias por red y no se hicieron commits. Las pruebas HTTP locales no certifican TLS o disponibilidad externa ni end-to-end.

El siguiente prompt debe proponer Lote 3: integrar SenaceClient con transporte, DTOs, mapper y ResultadoConsultaSenace; deserializar arrays y retirar simulación/default de clave dentro del alcance autorizado; conservar firmas y fallbacks compatibles sin cambiar políticas Resilience4j; preservar recursos genéricos no investigados. El contrato/controlador públicos siguen pendientes del lote correspondiente. Solicitar archivos y ejecuciones concretas; no iniciar automáticamente los lotes 3 o 4.
