# EcoInvest — Fase 2C: diagnóstico HTTP de la integración BCRP

**Fecha de ejecución:** 18 de octubre de 2026, hora local de Perú.  
**Alcance:** cliente BCRP independiente; sin iniciar EcoInvest completo ni acceder a SQLite.  
**Resultado:** consulta real de 2018 satisfactoria en esta ejecución. La causa exacta del HTML recibido anteriormente sigue sin determinarse.

## 1. Objetivo y autorizaciones

Diagnosticar por qué el recurso anual de Hidrocarburos de 2018 había entregado HTML al cliente Java, mientras el usuario observaba información económica desde Microsoft Edge en su conexión doméstica.

Se autorizó crear y eliminar una prueba temporal, ejecutar Maven con JDK 17.0.12 y realizar un único GET al recurso de 2018, sin seguir redirecciones ni consultar 2017. La creación de este reporte recibió autorización independiente después de comunicar los resultados.

No se modificó código productivo, SENACE, SQLite, OpenAPI, Resilience4j, certificados, versiones o dependencias. No se inició el servidor, instalaron herramientas ni realizaron commits. No se copiaron cookies, tokens o datos de sesión del navegador ni se alteraron encabezados para imitar Edge.

## 2. Antecedentes y evidencias

La Fase 2B documentó generación OpenAPI, compilación y 75 pruebas locales satisfactorias en [REPORTE_IMPLEMENTACION_FASE2B.md](REPORTE_IMPLEMENTACION_FASE2B.md). Esas pruebas no se repitieron completas durante este diagnóstico.

La primera prueba real con Java 20.0.2 falló durante TLS, antes de recibir HTTP. El diagnóstico posterior identificó una raíz GlobalSign Root R46 ausente en ese JDK y presente en el JDK 17.0.12 instalado. No se importaron certificados ni se desactivaron validaciones.

Al repetir la prueba anterior con Java 17.0.12:

| Recurso | Resultado observado anteriormente |
|---|---|
| PM04974AA / 2017 | HTTPS válido, HTTP 200, JSON económico válido; período 2017, valor `-2.44780196733593`, origen `API_DIRECTA_BCRP` |
| PM04974AA / 2018 | HTTPS válido, HTTP 200, HTML con referencias a `_Incapsula_Resource`; resultado `NO_DISPONIBLE / FORMATO_INVALIDO` |

La prueba anterior terminó con un fallo por 2018. Su archivo fuente temporal ya fue eliminado; se inspeccionaron sus evidencias conservadas:

- [XML de la prueba real anterior](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientRealIT.xml).
- [Resumen de la prueba real anterior](../../backend/target/surefire-reports/com.ecoinvest.soa.client.BcrpClientRealIT.txt).
- [Cierre parcial y diagnóstico SENACE](../03_Fase3_SENACE/REPORTE_FASE3_SENACE.md).

Los archivos de `target/` son artefactos generados y pueden desaparecer con una limpieza o ser reemplazados por futuras ejecuciones.

## 3. Inspección local del cliente

### 3.1. URL y selección temporal

El catálogo asigna Hidrocarburos a `PM04974AA`: [BcrpClient.java, líneas 33–39](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java).

La URL se construye en las líneas 181–182 usando el código aprobado y el mismo año en los extremos del rango:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04974AA/json/2018/2018/esp
```

No se encontró discrepancia entre esa construcción y la URL oficial de referencia facilitada por el usuario.

### 3.2. Lectura y validación

En `BcrpClient.java`:

- Línea 73: obtiene el cuerpo como `String`, sin depender del Content-Type para deserializar un DTO.
- Líneas 79–87: Jackson interpreta el contenido y rechaza JSON inválido como `FORMATO_INVALIDO`, sin registrar cuerpos completos.
- Líneas 91–101: valida estructura e identidad económica de una sola serie.
- Líneas 106–127: selecciona por igualdad exacta del período solicitado, sin depender de la posición.
- Líneas 137–144: construye `BigDecimal` y publica valor, período y origen.
- Líneas 152–183: prepara metadatos y valores ausentes, sin sustituciones constantes.

Por tanto, un encabezado `text/html` no basta para rechazar una respuesta: el cliente puede aceptar JSON económico servido con ese Content-Type. HTML auténtico sigue siendo un formato inválido para el contrato económico.

### 3.3. Configuración HTTP

[AppConfig.java, líneas 13–17](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java) establece conexión y lectura de cinco segundos. No configura explícitamente proxy ni redirecciones. El comportamiento efectivo depende de la fábrica HTTP utilizada.

El cliente productivo no conserva actualmente estado HTTP, encabezados de respuesta o URL efectiva en su resultado económico. No se modificó esa observabilidad durante el diagnóstico.

## 4. Entorno Java y Maven

Se seleccionó Java únicamente en el proceso de prueba:

```text
JAVA_HOME=C:\Program Files\Java\jdk-17
Java=17.0.12
Proveedor=Oracle Corporation
Maven=3.9.9
```

Tanto `java -version` como `mvnw.cmd -version` confirmaron Java 17.0.12. La evidencia producida dentro de la JVM de prueba confirmó el mismo `java.version` y `java.home`.

En la inspección local previa, `JAVA_HOME` del proceso general estaba vacío y `java` se resolvía mediante `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`; esa resolución no garantiza por sí sola Java 17. No se modificó permanentemente PATH ni JAVA_HOME.

No existían los archivos inspeccionados `backend/.mvn/jvm.config`, `backend/.mvn/maven.config`, ni los archivos de usuario `.m2/settings.xml` y `.m2/toolchains.xml`. No aparecieron variables de entorno de proxy u opciones JVM/Maven entre los nombres revisados. Esto no descarta intermediarios transparentes o configuraciones externas al proceso.

En la JVM de diagnóstico no estaban definidas las propiedades:

```text
https.proxyHost
http.proxyHost
socksProxyHost
java.net.useSystemProxies
javax.net.ssl.trustStore
```

La conexión informó `usingProxy=false`. Esta observación se refiere al transporte de la ejecución; no prueba ausencia de toda infraestructura intermedia de red.

## 5. Procedimiento ejecutado

Se creó temporalmente:

```text
backend/src/test/java/com/ecoinvest/soa/client/BcrpClientHttpDiagnosticIT.java
```

La prueba:

1. Exigió Java 17.0.12 y activación explícita mediante propiedad de sistema.
2. Instanció directamente el `BcrpClient` actual con Jackson y un RestTemplate independiente, sin contexto Spring productivo ni mocks.
3. Utilizó `SimpleClientHttpRequestFactory`, conexión y lectura de 5.000 ms y buffering para observar el mismo cuerpo que consumía el cliente.
4. Desactivó el seguimiento automático de redirecciones exclusivamente en el transporte temporal. Esta es una diferencia controlada respecto de una ejecución con configuración de redirecciones por defecto.
5. Preparó una única conexión y no incorporó reintentos del procedimiento. No realizó una consulta de control a 2017.
6. Registró estado HTTP, metadatos permitidos y clasificación del cuerpo, sin volcarlo ni copiar datos de sesión.
7. Invocó `obtenerDatosBcrp("Hidrocarburos", 2018)` y registró los campos económicos seleccionados.

La fábrica observada fue `sun.net.www.protocol.https.HttpsURLConnectionImpl`. La instrumentación registraba la URL solicitada como efectiva porque no permitía seguir redirecciones. Dado que se recibió directamente HTTP 200, no hubo una cadena de redirecciones observada que explicar.

Por seguridad, el archivo temporal estaba preparado para indicar la presencia de `Location` sin publicar su contenido arbitrario. No fue necesario examinar un destino porque no se recibió un estado de redirección.

### Comandos

Desde `backend/`, en un proceso temporal de PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o '-Dtest=BcrpClientHttpDiagnosticIT' '-Decoinvest.bcrp.httpDiagnostic=true' test
```

El primer intento dentro del sandbox falló al abrir el repositorio Maven `C:\.m2\repository`, antes de ejecutar la prueba o consultar BCRP. Se repitió el comando con acceso autorizado fuera del sandbox. No fue un reintento de una solicitud HTTP al proveedor.

Maven regeneró artefactos OpenAPI y compiló fuentes bajo `target/`. Se mantuvo el modo offline para dependencias; la prueba habilitada sí realizó el GET público autorizado.

## 6. Resultado de la consulta nueva

| Evidencia | Valor |
|---|---|
| Recurso | `PM04974AA/json/2018/2018/esp` |
| Java de prueba | 17.0.12 |
| TLS | Validación HTTPS normal superada; sin trust-all ni cambios de certificados |
| Estado HTTP | 200 |
| Content-Type | `text/html; charset=UTF-8` |
| Longitud recibida | 342 bytes |
| Longitud del texto interpretado como UTF-8 | 339 caracteres Java |
| Clasificación inicial del cuerpo | JSON económico candidato |
| Validación posterior de BcrpClient | Aceptada |
| Marcador `_Incapsula_Resource` | No detectado |
| Serie | PM04974AA |
| Año solicitado | 2018 |
| Período efectivo | 2018 |
| Valor BigDecimal | `-0.0345569324875896` |
| Unidad | Variación porcentual interanual (%) |
| Origen | API_DIRECTA_BCRP |
| Motivo de ausencia | Nulo |
| Conexiones preparadas por el procedimiento | 1 |

Encabezados no sensibles observados:

```text
Content-Type: text/html; charset=UTF-8
Date: Mon, 19 Oct 2026 02:13:11 GMT
Cache-Control: no-store, no-cache, must-revalidate
Server: srv
```

No se registraron valores para Content-Length, Content-Encoding o Via porque no se observaron mediante las lecturas seleccionadas. La fecha declarada por el servidor difiere en varios minutos del reloj local reflejado por Maven; no se usó esa cabecera para establecer una sincronización exacta entre observaciones.

La procedencia real queda respaldada por la llamada HTTPS al dominio oficial desde el cliente sin mocks; no por la etiqueta `API_DIRECTA_BCRP` aislada. El valor publicado procede del parseo `BigDecimal` del cliente implementado. Esta prueba se diseñó como diagnóstico de transporte y contenido; no sustituyó toda la cobertura unitaria ni realizó una comparación independiente nueva de cada campo económico contra un segundo parser de referencia.

### Resultado Maven/Surefire

```text
BUILD SUCCESS
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Finished at: 2026-10-18T21:07:53-05:00
```

La prueba comprobó el entorno y el límite de conexión y registró el resultado real. Su éxito como prueba diagnóstica no debe confundirse con una certificación completa de disponibilidad del proveedor.

Evidencias:

- [XML del diagnóstico](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientHttpDiagnosticIT.xml), entrada `BCRP_HTTP_DIAGNOSTIC`.
- [Resumen Surefire](../../backend/target/surefire-reports/com.ecoinvest.soa.client.BcrpClientHttpDiagnosticIT.txt).

## 7. Comparación e interpretación

| Aspecto | Observación anterior de 2018 | Nueva observación de 2018 |
|---|---|---|
| URL solicitada | Mismo recurso anual oficial | Mismo recurso anual oficial |
| JVM | 17.0.12 | 17.0.12 |
| TLS | Superado | Superado |
| Estado HTTP | 200 | 200 |
| Tipo declarado | text/html | text/html |
| Contenido efectivo | HTML con referencias de protección | JSON económico válido |
| Resultado del cliente | NO_DISPONIBLE / FORMATO_INVALIDO | API_DIRECTA_BCRP, período y valor válidos |

La diferencia demostrada está en el contenido recibido, no en una URL mal construida ni en la incapacidad del cliente para leer JSON etiquetado como `text/html`. La nueva respuesta se obtuvo sin simular Edge, copiar sesiones o eludir controles.

No hay evidencia suficiente para identificar la causa exacta de la variación anterior. Son hipótesis pendientes el momento de consulta, la ruta de red o decisiones de infraestructura externa. El marcador previo es compatible con una respuesta de protección web, pero no acredita por sí solo una regla concreta contra Java.

La observación de Edge fue comunicada por el usuario; no se capturó su tráfico ni se realizó una comparación simultánea de ambas solicitudes. Tampoco hay evidencia retrospectiva suficiente para reconstruir todas las redirecciones o cabeceras del intento anterior.

**No se detectó un defecto demostrable en BcrpClient que justifique modificar código productivo.** Una ejecución satisfactoria de 2018 no demuestra que el incidente anterior esté resuelto permanentemente.

## 8. Comportamiento recomendado y observabilidad

Conservar el rechazo seguro de HTML inesperado como `NO_DISPONIBLE / FORMATO_INVALIDO`. No publicar valores inventados, reutilizar un año vecino o interpretar una página de protección como dato económico.

Si se autoriza un lote posterior de observabilidad, evaluar registros limitados a serie, año, duración, estado HTTP, Content-Type, longitud recibida y categoría de contenido. Para redirecciones, cualquier destino necesitaría saneamiento; no deben registrarse consultas arbitrarias o información de sesión.

No registrar cuerpos completos, mensajes de excepción con fragmentos externos, cookies, Set-Cookie, tokens ni cabeceras de autenticación. No se implementaron estas mejoras durante esta fase.

## 9. Limpieza y comprobación de alcance

El archivo fuente temporal fue eliminado al finalizar; se verificó su ausencia. Maven dejó los artefactos generados de compilación y Surefire en `target/`, como se había advertido. No se eliminaron manualmente esos artefactos.

Se compararon hashes SHA-256 antes y después para los siete archivos Java/YAML/YML encontrados bajo `backend/src/main`: SoaApplication, BcrpClient, SenaceClient, AppConfig, EcoInvestController, application.yml y openapi.yaml. Permanecieron iguales.

No se revirtieron cambios preexistentes del repositorio. Este reporte es la única creación permanente autorizada para documentar el resultado.

## 10. Estado de cierre y pendientes

| Aspecto | Estado |
|---|---|
| Consulta real 2017 con JDK 17.0.12 | Verificada en ejecución anterior |
| Consulta real 2018 con JDK 17.0.12 | Verificada en esta ejecución |
| TLS normal | Verificado en las observaciones indicadas |
| Aceptación de JSON servido como text/html | Verificada con respuestas reales |
| Rechazo de HTML inesperado | Verificado en la observación anterior |
| Causa exacta de la diferencia Edge/Java | No determinada |
| Disponibilidad estable del proveedor | No certificada mediante una sola observación nueva |
| Interceptación efectiva de Resilience4j | Pendiente de prueba independiente autorizada |
| EcoInvest completo, dashboard y SQLite | No ejecutados ni certificados por esta prueba |

La Fase 2C cuenta ahora con evidencia positiva del cliente para ambos años en ejecuciones distintas y conserva la limitación del incidente HTML previo. Para su cierre formal debe distinguirse la verificación puntual del cliente de la garantía de disponibilidad externa y del pendiente de interceptación AOP. No corresponde declarar funcionamiento end-to-end de toda la aplicación.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest dispone de un BcrpClient que consulta series anuales oficiales, valida el año y conserva precisión con BigDecimal. Con JDK 17.0.12, 2017 entregó anteriormente `-2.44780196733593`. Una nueva consulta única a PM04974AA/2018 entregó HTTP 200, JSON económico aunque declarado text/html, período 2018 y valor `-0.0345569324875896`, origen API_DIRECTA_BCRP. La validación TLS se mantuvo activa y no se siguieron redirecciones.

La respuesta HTML anterior con referencias a `_Incapsula_Resource` no se reprodujo en esta consulta. No se ha identificado su causa exacta ni un defecto local demostrable; debe conservarse el rechazo NO_DISPONIBLE / FORMATO_INVALIDO y evitar cualquier intento de eludir protecciones del proveedor.

La compilación y una prueba diagnóstica finalizaron satisfactoriamente; las 75 pruebas locales previas no se repitieron completas. Se eliminó el archivo fuente temporal y no cambió código productivo ni se accedió a SQLite. Las evidencias están en los informes Surefire enlazados.

El siguiente paso técnico recomendado es revisar estos resultados y decidir una prueba aislada y autorizada de interceptación real de Resilience4j, sin contexto productivo ni SQLite. No hace falta modificar BcrpClient para explicar una variación externa que no está causalmente determinada. La integración real SENACE puede continuar según su plan específico, manteniendo separados los alcances y autorizaciones.
