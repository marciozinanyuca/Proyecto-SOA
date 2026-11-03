# EcoInvest — Cierre de la Fase 2C BCRP

**Fecha:** 19 de octubre de 2026.  
**Proyecto:** EcoInvest, integración SENACE + BCRP.  
**Entorno verificado:** Windows, Oracle JDK 17.0.12, Maven Wrapper con Maven 3.9.9, Spring Boot 3.2.4.  
**Conclusión:** la Fase 2C puede cerrarse dentro del alcance de verificación del cliente BCRP y de su interceptación en un contexto Spring aislado. No se certifica funcionamiento end-to-end de EcoInvest ni disponibilidad permanente del proveedor.

## 1. Alcance y autorizaciones

La validación final completó las cinco series anuales restantes del catálogo mediante cinco GET públicos individuales para 2018. Se autorizó una consulta documental adicional, sin reintentos ni seguimiento de redirecciones, y un único archivo fuente temporal para la prueba.

La creación de este reporte recibió autorización independiente después de comunicar los resultados. No se ejecutaron consultas adicionales para redactarlo.

No se modificó código productivo, SENACE, SQLite, frontend, OpenAPI, Resilience4j, versiones, certificados o dependencias. No se inició el servidor completo, instalaron herramientas ni realizaron commits. No se utilizaron datos de sesión del navegador, encabezados para simular Edge o técnicas para eludir protecciones.

## 2. Evidencias y antecedentes

| Documento o artefacto | Alcance |
|---|---|
| [REPORTE_IMPLEMENTACION_FASE2B.md](REPORTE_IMPLEMENTACION_FASE2B.md) | Catálogo, contrato, procesamiento anual y 75 pruebas locales previas |
| [REPORTE_FASE2A_VALIDACION_BCRP.md](REPORTE_FASE2A_VALIDACION_BCRP.md) | Validación oficial previa de familias, códigos y sintaxis |
| [REPORTE_FASE2C_DIAGNOSTICO_HTTP.md](REPORTE_FASE2C_DIAGNOSTICO_HTTP.md) | Evidencias reales de Hidrocarburos y variación del contenido recibido |
| [REPORTE_FASE2C_RESILIENCE4J.md](REPORTE_FASE2C_RESILIENCE4J.md) | Proxy, fallback, apertura, rechazo y recuperación en contexto mínimo |
| [REPORTE_FASE3_SENACE.md](../03_Fase3_SENACE/REPORTE_FASE3_SENACE.md) | Diagnóstico SENACE previo; contiene el estado histórico de BCRP en su fecha |
| [XML de la validación final](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientCatalogRealIT.xml) | Cinco observaciones reales y un caso de inspección documental |
| [Resumen Surefire final](../../backend/target/surefire-reports/com.ecoinvest.soa.client.BcrpClientCatalogRealIT.txt) | Seis casos, sin fallos ni errores |

Los reportes son acumulativos: el reporte SENACE anterior conserva el intento fallido de Hidrocarburos 2018, mientras los documentos posteriores registran una consulta satisfactoria. No se modificaron retrospectivamente esos antecedentes.

Los artefactos bajo target/ son generados y pueden desaparecer o reemplazarse durante futuras ejecuciones. Este documento conserva sus resultados principales.

## 3. Inspección local y catálogo vigente

[BcrpClient.java, líneas 33–39](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java) contiene:

| Filtro | Serie | Alcance económico |
|---|---|---|
| AGRICULTURA | PM04969AA | Agropecuario - Agrícola |
| PESCA | PM04971AA | Pesca |
| MINERIA / MINERÍA | PM04973AA | Minería e Hidrocarburos - Minería Metálica |
| HIDROCARBUROS | PM04974AA | Minería e Hidrocarburos - Hidrocarburos |
| ELECTRICIDAD | PM04978AA | Electricidad y Agua |
| TODOS | PM04983AA | PBI nacional |

La familia económica es «Producto bruto interno por sectores productivos (variación porcentual interanual)». La unidad publicada por el catálogo aprobado es **Variación porcentual interanual (%)**, con periodicidad ANUAL.

Evidencia adicional del cliente:

- Líneas 73–87: cuerpo String y deserialización Jackson explícita; rechazo de JSON inválido.
- Líneas 91–101: validación estructural e identidad por denominación económica.
- Líneas 106–127: selección por igualdad exacta del año, sin elegir por posición.
- Líneas 137–144: BigDecimal y coherencia entre valor y variacionSeleccionada.
- Líneas 170–182: metadatos y URL anual.
- Líneas 191–202: fallback sin indicador ficticio ni respaldo persistente implementado.

No se identificó una necesidad de modificar el cliente para realizar la validación restante.

## 4. Diseño de la validación final

### 4.1. Año y presupuesto de consultas

Se seleccionó 2018 porque ya existían muestras oficiales documentadas para el catálogo anual y una observación satisfactoria de Hidrocarburos con el cliente actual. No se hicieron barridos históricos ni consultas de años alternativos al aparecer errores.

Base oficial:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api
```

Se realizaron cinco solicitudes económicas y una documental, una por recurso. No se implementaron reintentos ni seguimiento automático de redirecciones.

### 4.2. Archivo temporal y aislamiento

Se creó, con autorización:

```text
backend/src/test/java/com/ecoinvest/soa/client/BcrpClientCatalogRealIT.java
```

La clase exigía la propiedad `ecoinvest.bcrp.catalogLive=true`. Utilizó BcrpClient real, ObjectMapper y RestTemplate independiente, con conexión y lectura de cinco segundos, TLS normal y redirecciones desactivadas en el transporte temporal.

No se levantó contexto Spring productivo. No hubo mocks en las consultas económicas ni dependencia de SENACE, SQLite o servidor web. La instrumentación capturó en memoria el cuerpo de la misma respuesta consumida por el cliente, mediante buffering, sin realizar una descarga adicional para comparar el valor.

Se registraron URL, estado, Content-Type, tamaño, presencia de marcador de protección y metadatos económicos. No se volcaron cuerpos completos, cabeceras sensibles o mensajes de excepción con contenido externo.

### 4.3. Comandos ejecutados

Desde backend/, en un proceso temporal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o '-Dtest=BcrpClientCatalogRealIT' '-Decoinvest.bcrp.catalogLive=true' test
```

Se ejecutó con acceso autorizado a la caché Maven y la red fuera del sandbox. El modo offline evitó descargas Maven, pero no impidió los GET explícitamente autorizados de la prueba. Maven regeneró OpenAPI y compiló en target/; no se editaron manualmente sus artefactos.

Java, Maven y las propiedades Surefire confirmaron `java.version=17.0.12` y `java.home=C:\Program Files\Java\jdk-17`. La selección no se aplicó permanentemente al sistema.

## 5. Resultados económicos reales

Todas las consultas siguientes recibieron HTTP 200, cuerpo JSON económico y `Content-Type: text/html; charset=UTF-8`. No presentaron Location ni el marcador `_Incapsula_Resource` observado en un intento anterior de Hidrocarburos.

| Filtro | Serie | Período | Valor decimal recibido (%) | Ámbito | Bytes |
|---|---|---|---:|---|---:|
| AGRICULTURA | PM04969AA | 2018 | 9.07731729270536 | SECTORIAL | 323 |
| PESCA | PM04971AA | 2018 | 47.6847546513897 | SECTORIAL | 304 |
| MINERIA | PM04973AA | 2018 | -1.73797597703714 | SECTORIAL | 345 |
| ELECTRICIDAD | PM04978AA | 2018 | 4.39284781615264 | SECTORIAL | 318 |
| TODOS | PM04983AA | 2018 | 3.96935133550218 | NACIONAL | 302 |

URLs oficiales de las observaciones:

- [Agrícola, 2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04969AA/json/2018/2018/esp).
- [Pesca, 2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04971AA/json/2018/2018/esp).
- [Minería metálica, 2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04973AA/json/2018/2018/esp).
- [Electricidad y Agua, 2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04978AA/json/2018/2018/esp).
- [PBI nacional, 2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04983AA/json/2018/2018/esp).

Las cifras corresponden a las respuestas observadas durante esta ejecución y podrían estar sujetas a revisiones posteriores del proveedor. No son valores incorporados al código ni sustitutos de consultas futuras.

### 5.1. Comprobaciones por observación

Las cinco consultas cumplieron:

1. URL exacta con código aprobado y rango anual 2018/2018.
2. Una sola serie y denominación económica completa coincidente.
3. Una observación de período 2018 y una columna de valor.
4. Resultado de tipo BigDecimal comparado con el decimal del cuerpo recibido.
5. Coherencia exacta entre valor y variacionSeleccionada.
6. Año solicitado y período efectivo correctos.
7. Descripción, unidad, periodicidad y ámbito correspondientes al catálogo aprobado.
8. Origen API_DIRECTA_BCRP y motivo de ausencia nulo.

El JSON observado no incluyó un campo independiente `code` en el objeto de serie. No se presenta el código del catálogo como si hubiera sido leído de ese campo: la identificación se corroboró con la URL oficial y el nombre de serie recibido.

La unidad fue verificada contra el catálogo aprobado, respaldado por la validación oficial previa; no se detectó en esta prueba un campo JSON independiente de unidad. La prueba no fabricó esa evidencia.

### 5.2. Hidrocarburos: evidencia previa que completa el catálogo

Las consultas de Hidrocarburos no se repitieron en esta ejecución:

| Serie | Año | Valor real observado anteriormente | Documento |
|---|---|---:|---|
| PM04974AA | 2017 | -2.44780196733593 | Diagnóstico HTTP y evidencia de la prueba real inicial con JDK 17 |
| PM04974AA | 2018 | -0.0345569324875896 | REPORTE_FASE2C_DIAGNOSTICO_HTTP.md |

El intento previo que entregó HTML en 2018 sigue siendo una observación válida de indisponibilidad semántica del proveedor. El éxito posterior no determina su causa ni garantiza que no se repita.

## 6. Consulta documental y limitación

Se consultó una sola vez el recurso indicado:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api/
```

Resultado:

```text
HTTP 302
Content-Type: text/html; charset=UTF-8
Cuerpo: 0 caracteres
Location presente
```

No se siguió la redirección, respetando el límite autorizado. Por tanto, este intento no revalidó el contenido de la documentación. La sintaxis anual utilizada sigue respaldada por la documentación oficial consultada en Fase 2A y por las respuestas económicas efectivamente recibidas.

Si se necesitara actualizar la documentación de referencia, debe acordarse una consulta posterior acotada al destino oficial verificado. No se consumieron peticiones adicionales para resolver el 302.

## 7. Resultado de compilación y pruebas

```text
BUILD SUCCESS
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
Finished at: 2026-10-19T19:03:04-05:00
```

El total representa cinco casos económicos parametrizados y un caso de inspección documental. El éxito del caso documental significa que pudo observarse y registrarse la respuesta; no significa que se hubiera obtenido contenido documental útil.

No se repitieron las 75 pruebas locales de Fase 2B. Los seis casos actuales no sustituyen esa suite ni deben sumarse como una sola ejecución conjunta de 81 pruebas.

## 8. Separación entre evidencia real y simulada

| Tipo de prueba | Evidencia | Límite |
|---|---|---|
| Suite local Fase 2B | Contrato, selección temporal, estructuras, precisión, ausencia y rutas | HTTP simulado y pruebas locales; no disponibilidad externa |
| Consultas reales Hidrocarburos | HTTPS y observaciones 2017 y 2018 en ejecuciones distintas | Solo esas observaciones; hubo un HTML previo |
| Validación real final | Cinco series restantes para 2018 | Una observación por serie; sin barrido temporal |
| Prueba AOP Resilience4j | Proxy real, fallback, apertura, rechazo y recuperación | HTTP simulado, contexto mínimo; sin proveedor real |
| Inspección documental final | Respuesta 302 sin seguimiento | Contenido documental no obtenido |

Las cinco observaciones económicas finales no provinieron de mocks ni de constantes. La etiqueta de procedencia no se usa como prueba aislada: se respalda en las conexiones reales al dominio oficial y el contraste con los cuerpos recibidos.

## 9. Errores, ausencia y restricciones externas

En esta ejecución no se observaron HTML auténtico, JSON inválido, períodos vacíos ni fallos HTTP en las cinco series económicas. No se provocaron nuevas consultas para intentar obtener esos escenarios.

El tratamiento de estructuras inválidas, HTML, ausencia y errores de transporte ya estaba cubierto por pruebas locales previas. Adicionalmente, un intento real anterior confirmó rechazo seguro de HTML como NO_DISPONIBLE / FORMATO_INVALIDO. La prueba AOP mostró fallback por HTTP y conexión sin cifras inventadas.

Persisten estas limitaciones:

- El proveedor puede devolver contenido distinto bajo HTTP 200; el Content-Type text/html por sí solo no distingue JSON económico de HTML de protección.
- La causa exacta de la diferencia anterior entre Edge y Java no está demostrada.
- El nombre económico se valida estrictamente; un cambio oficial de denominación podría producir FORMATO_INVALIDO y requerir revisión.
- No se certificó toda la historia disponible, años futuros, datos revisados, rendimiento o disponibilidad sostenida.
- La prueba real directa no atraviesa el proxy Spring; AOP fue demostrado por separado en contexto aislado.
- Una ausencia económica retornada normalmente, como FORMATO_INVALIDO, se contabiliza actualmente como ejecución exitosa del breaker. La política está documentada y no fue modificada.

No se identificaron defectos productivos demostrables pendientes en los casos examinados. Esto no equivale a afirmar ausencia universal de defectos.

## 10. Limpieza y comprobación de alcance

El archivo fuente temporal BcrpClientCatalogRealIT.java fue eliminado y se verificó su ausencia. Permanecen los informes y artefactos generados de Maven bajo target/, sujetos a futuras limpiezas.

Se compararon hashes SHA-256 antes y después para todos los siete archivos encontrados bajo backend/src/main: SoaApplication.java, BcrpClient.java, SenaceClient.java, AppConfig.java, EcoInvestController.java, application.yml y openapi.yaml. La comparación confirmó igualdad.

No se revirtieron cambios preexistentes. Este reporte es la única creación permanente autorizada para documentar el cierre.

## 11. Criterios de cierre

| Criterio | Evidencia | Evaluación |
|---|---|---|
| Mantener JDK 17.0.12 y TLS normal | Versiones de proceso y Surefire; respuestas HTTPS | Cumplido |
| Verificar todo el catálogo aprobado con el cliente real | Cinco series actuales más Hidrocarburos previo | Cumplido para las observaciones seleccionadas |
| Año determina URL y período aceptado | Aserciones del cliente y prueba sobre 2018; antecedentes 2017 | Cumplido dentro de la muestra |
| Conservar precisión y trazabilidad | BigDecimal contrastado, descripción, ámbito, URL y procedencia | Cumplido, con distinción de metadatos del catálogo |
| No sustituir datos ausentes | Código y pruebas locales, fallback y HTML real previo | Cumplido en los escenarios verificados |
| Demostrar interceptación de Resilience4j | Proxy y estados con HTTP simulado | Cumplido en contexto mínimo |
| No introducir cambios productivos | Hashes sin cambios y limpieza temporal | Cumplido |
| Obtener documentación actual en el último intento | HTTP 302 sin seguimiento | No obtenido; limitación documental explícita |
| Certificar dashboard end-to-end | No se inició la aplicación completa | Pendiente independiente |

**Decisión técnica:** la Fase 2C puede cerrarse dentro del alcance específico de cliente BCRP, conectividad, observaciones seleccionadas y resiliencia aislada. El 302 documental no invalida los datos observados ni la sintaxis ya validada, pero debe conservarse como limitación. El cierre no promete disponibilidad permanente y no incluye certificación end-to-end de EcoInvest.

## 12. Preparación de la Fase 3A SENACE

No se consultó SENACE en esta fase. El usuario dispone de un token generado mediante la plataforma oficial; no se leyó, publicó o incorporó ese valor a este reporte o a las pruebas BCRP.

### Prioridades

1. Verificar documentación y dominio oficial vigente, sin enviar credenciales a destinos no confirmados.
2. Confirmar rutas de CarteraProyectos y ConsultorasAmbientales, distinguiendo versiones con y sin /data.json.
3. Verificar el mecanismo de autenticación documentado antes de decidir cómo transmitir la credencial.
4. Solicitar autorización para muestras pequeñas y delimitar el número de peticiones.
5. Utilizar una credencial vigente mediante variable de entorno temporal, sin copiarla a comandos literales, archivos de prueba, logs, excepciones o reportes.
6. Examinar estructura real, campos, fechas, filtros, paginación y cobertura geográfica, sin asumir que los nombres actuales del código son el contrato oficial.
7. Definir el mapeo y la procedencia antes de proponer sustituir registros simulados.

Si el mecanismo oficial exige auth_key en URL, deben preservarse la sanitización y el control de registros para que la URL autenticada no aparezca en salidas. No debe inferirse el mecanismo únicamente a partir del código existente.

### Recursos conocidos, todavía sujetos a verificación vigente

La documentación compartida identifica CarteraProyectos, ConsultorasAmbientales, GastoEspecifica, GastoFuente, GastoGenerica, SolicitudAcceso, Visitas y Reclamos. EcoInvest priorizará los dos primeros. La enumeración no certifica que todos estén disponibles actualmente ni que compartan formato o autenticación.

### Archivos a revisar antes de implementar

- `REPORTE_FASE3_SENACE.md`.
- `backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java`.
- Integración SENACE de `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java`.
- `backend/src/main/resources/openapi.yaml`.
- Configuración pertinente de `backend/src/main/resources/application.yml`, sin divulgar credenciales.
- `backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java`.
- `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java`.

La siguiente etapa debe comenzar como validación oficial y diagnóstico, no como modificación automática del cliente. Cada creación temporal, ejecución Maven o HTTP deberá respetar la autorización de esa etapa. No es necesario cambiar BCRP, Resilience4j o SQLite para iniciar ese trabajo.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE y BCRP con Java 17 y Spring Boot 3.2.4. La Fase 2C BCRP puede cerrarse dentro de su alcance específico: se verificó HTTPS con Oracle JDK 17.0.12, Hidrocarburos para 2017 y 2018 y, el 19/10/2026, las otras cinco series para 2018 mediante el BcrpClient real.

Los valores finales observados fueron Agrícola PM04969AA = 9.07731729270536; Pesca PM04971AA = 47.6847546513897; Minería metálica PM04973AA = -1.73797597703714; Electricidad y Agua PM04978AA = 4.39284781615264; PBI nacional PM04983AA = 3.96935133550218. Son variaciones porcentuales interanuales, no datos simulados ni constantes del código. Las cinco respuestas fueron HTTP 200 con JSON económico declarado text/html, y los valores BigDecimal se contrastaron con el cuerpo recibido.

El código de serie no vino en un campo code independiente: se verificaron URL y denominación. La unidad publicada proviene del catálogo aprobado. La consulta documental adicional devolvió 302 y no se siguió. La prueba final completó seis casos sin fallos; las 75 pruebas locales previas no se repitieron completas.

La interceptación Spring AOP y Resilience4j fue verificada por separado con HTTP simulado, incluyendo apertura, rechazo y recuperación. Se conserva el rechazo seguro de HTML y la ausencia sin cifras ficticias. No se identificaron defectos productivos demostrables en los casos examinados; siguen las limitaciones externas y el pendiente independiente de dashboard end-to-end.

Se eliminó el archivo fuente temporal y los hashes productivos permanecieron iguales. No se inició EcoInvest completo ni se accedió a SQLite. El siguiente trabajo recomendado es la Fase 3A de validación oficial SENACE: rutas vigentes, autenticación, muestras pequeñas de CarteraProyectos y ConsultorasAmbientales, campos y paginación. Usar el token solo mediante variable de entorno temporal después de verificar el mecanismo oficial, sin exponerlo. No se autoriza implícitamente una consulta SENACE o un cambio de implementación mediante este reporte.
