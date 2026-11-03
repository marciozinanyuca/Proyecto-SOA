# Reporte de implementación — Fase 2B de EcoInvest

**Fecha:** 18 de octubre de 2026.  
**Proyecto:** EcoInvest, integración gubernamental SENACE + BCRP.  
**Objetivo:** indicadores BCRP anuales con identidad económica verificada, trazabilidad y correspondencia con el año seleccionado.  
**Resultado:** generación OpenAPI y compilación satisfactorias; **75 pruebas ejecutadas, 0 fallos, 0 errores y 0 omitidas**.

La implementación se realizó sobre los cinco archivos autorizados. Después se autorizó ejecutar Maven offline y, finalmente, crear este reporte. No se certifica funcionamiento end-to-end con APIs externas reales.

## 1. Antecedentes y alcance

Se leyeron y contrastaron con el código:

- [REPORTE_FASE2_BCRP.md](REPORTE_FASE2_BCRP.md).
- [REPORTE_FASE2A_VALIDACION_BCRP.md](REPORTE_FASE2A_VALIDACION_BCRP.md).
- [REPORTE_IMPLEMENTACION_FASE1A.md](../01_Fase1/REPORTE_IMPLEMENTACION_FASE1A.md).

La validación oficial previa identificó cinco asignaciones sectoriales incorrectas, selección temporal ausente y JSON servido con Content-Type text/html. También verificó alternativas anuales oficiales. Durante Fase 2B no se repitieron consultas al proveedor: las pruebas utilizan respuestas pequeñas documentadas y casos técnicos controlados.

No se modificaron SENACE, sus datos simulados, frontend, AppConfig, SQLite, configuración Resilience4j, POM, versiones, paquetes o wrapper. No se instalaron dependencias ni realizaron commits. No se crearon archivos auxiliares fuente o fixtures fuera de los cinco archivos autorizados; el catálogo auxiliar es un record privado dentro de BcrpClient.

Se generaron automáticamente artefactos bajo target/ mediante Maven autorizado; no se editaron manualmente los modelos generados.

## 2. Archivos modificados

| Archivo | Cambio de esta fase |
|---|---|
| `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java` | Catálogo anual, consulta por año, Jackson explícito, validación estructural y temporal, BigDecimal y metadatos |
| `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java` | Solo integración BCRP: publicación de detalle e indicador coherentes, sin conversión por double |
| `backend/src/main/resources/openapi.yaml` | Objeto opcional indicadorBcrp y documentación económica y de ausencia |
| `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java` | Pruebas con HTTP simulado y RestTemplate real |
| `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java` | Metadatos, precisión, ausencia y regresión de cuatro rutas |

Este reporte se creó posteriormente con autorización independiente. Los archivos modificados de fases anteriores permanecían en el árbol de trabajo; no deben confundirse con cambios nuevos de Fase 2B.

## 3. Catálogo económico implementado

Fuente del catálogo: aprobación expresa del usuario y validación oficial documentada en Fase 2A.

| Filtro normalizado | Código | Alcance económico público | Ámbito |
|---|---|---|---|
| AGRICULTURA | PM04969AA | Agropecuario - Agrícola | SECTORIAL |
| PESCA | PM04971AA | Pesca | SECTORIAL |
| MINERIA / MINERÍA | PM04973AA | Minería e Hidrocarburos - Minería Metálica | SECTORIAL |
| HIDROCARBUROS | PM04974AA | Minería e Hidrocarburos - Hidrocarburos | SECTORIAL |
| ELECTRICIDAD | PM04978AA | Electricidad y Agua | SECTORIAL |
| TODOS | PM04983AA | PBI | NACIONAL |

Todas las series del catálogo son anuales y la unidad declarada es **Variación porcentual interanual (%)**. La descripción pública conserva la familia oficial «Producto bruto interno por sectores productivos (variación porcentual interanual)» y el componente específico.

- TODOS es contexto de PBI nacional; no representa un agregado calculado de la cartera SENACE.
- Electricidad conserva explícitamente el alcance Electricidad y Agua.
- Minería conserva explícitamente el alcance Minería Metálica.
- TRANSPORTES, ENERGIA, ENERGÍA y sectores desconocidos devuelven SECTOR_NO_SOPORTADO sin HTTP externo ni código sustituto.
- La normalización elimina diferencias de acentos y usa Locale.ROOT. No convierte energía en electricidad.

Evidencia: [BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java), líneas **24–40, 152–189**.

## 4. Consulta temporal y procesamiento HTTP/JSON

### 4.1. Flujo implementado

```text
GET /api/dashboard?sector={sector}&anio={anio}
  → EcoInvestController
  → BcrpClient: catálogo aprobado
  → GET {base}/{codigoSerie}/json/{anio}/{anio}/esp
  → cuerpo String
  → Jackson: estructura y período validados
  → BigDecimal + metadatos
  → variacionSeleccionada e indicadorBcrp.valor coherentes
```

Ejemplo de URL construida para un sector admitido:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04974AA/json/2018/2018/esp
```

No se consulta más de una serie por petición. Se conserva la propiedad existente external.apis.bcrp-base-url. El año nulo conserva el default previo 2017. Los años fuera de 1–9999 producen ANIO_INVALIDO sin llamada externa.

Evidencia: cliente, líneas **63–88 y 152–183**.

### 4.2. Lectura de JSON servido como text/html

La llamada obtiene String.class, línea **73**, y utiliza el ObjectMapper inyectado de Spring Boot, sin reconfigurarlo globalmente. Se crea un ObjectReader local con comprobación de contenido posterior al JSON y soporte decimal para números JSON.

Esto permite interpretar el cuerpo JSON con la cabecera observada `text/html; charset=UTF-8`, sin modificar los convertidores del RestTemplate compartido ni el comportamiento SENACE.

HTML auténtico, JSON inválido, varios documentos JSON concatenados y estructuras incompatibles se rechazan como FORMATO_INVALIDO. No se devuelven cifras sustitutivas ni se registran cuerpos externos completos en esos casos.

Evidencia: cliente, líneas **70–88**; pruebas de cliente con MockRestServiceServer.

### 4.3. Validación estructural y económica

Se exige:

- Raíz JSON de tipo objeto.
- config.series como array con exactamente un objeto.
- Nombre de serie coincidente con la descripción del catálogo aprobado.
- periods como array.
- Cada período como objeto, nombre anual de cuatro dígitos y una sola columna en values.
- Una sola observación coincidente con el año solicitado; un duplicado del año es ambiguo y se rechaza.
- Valor String o número JSON interpretable como BigDecimal.

La comprobación estricta de descripción impide aceptar una serie económicamente distinta con forma JSON válida. Si el proveedor cambia su denominación, el resultado pasará a FORMATO_INVALIDO hasta revisar el catálogo; esta es una decisión conservadora.

### 4.4. Selección temporal

Se recorre periods y se selecciona por igualdad exacta del nombre del año, no por posición. Si la respuesta incluye años vecinos y el solicitado, se toma únicamente el solicitado, con independencia del orden. Si todos los períodos corresponden a otros años, no se utiliza ninguno y se informa PERIODO_FUERA_DE_RANGO.

No se agregan meses, se elige diciembre ni se reemplaza por el año más cercano. El período efectivo solo se publica si existe valor válido.

Evidencia: cliente, líneas **91–149**, en particular **106–126**.

### 4.5. Precisión y ausencia

El valor se construye con BigDecimal a partir de la representación decimal. No pasa por Double en el cliente o la integración del controlador. Se preservan ceros y valores negativos reales.

La ausencia se representa internamente con valor, variación y período nulos; en la respuesta pública se omiten los campos económicos sin dato. origen es NO_DISPONIBLE y motivo explica la causa.

| Motivo | Situación |
|---|---|
| SECTOR_NO_SOPORTADO | No existe código aprobado para el filtro |
| ANIO_INVALIDO | Año fuera del rango de cuatro dígitos admitido |
| SIN_OBSERVACION | Cuerpo vacío, periods vacío o valor JSON null |
| PERIODO_FUERA_DE_RANGO | Hay períodos anuales, pero ninguno coincide con el año solicitado |
| FORMATO_INVALIDO | JSON, identidad, estructura, columnas, duplicados o valor decimal inválidos |
| ERROR_EXTERNO | Fallback existente tras error externo |

Los tokens de prueba como n.d., NaN o Infinity no se presentan como convenciones universales del BCRP. Son casos técnicos para comprobar que no se transforman en cifras ficticias.

## 5. Resilience4j y fallback

Se mantuvieron las anotaciones, nombres y firmas públicas de los métodos protegidos:

```java
@CircuitBreaker(name = "bcrp", fallbackMethod = "fallbackBcrp")
@CircuitBreaker(name = "bcrp", fallbackMethod = "fallbackBcrpPorSector")
```

Los errores HTTP y de conexión continúan propagándose desde el cliente hacia la frontera de resiliencia existente. Las pruebas unitarias verifican esa propagación; no agregan captura HTTP que eluda el breaker.

Los fallbacks conservan el catálogo y metadatos aprobados, sin valor o período sintéticos, y comunican ERROR_EXTERNO cuando corresponde. No se implementó persistencia ni se alteró SQLite.

La interceptación AOP efectiva sigue sin estar certificada por esta suite. Las pruebas de fallback directo no se presentan como prueba de interceptación. Los errores de formato se expresan como ausencia, sin modificar la política existente del breaker para respuestas semánticamente inválidas.

Evidencia: cliente, líneas **53–60, 70–73 y 191–204**.

## 6. Contrato OpenAPI y controlador

### 6.1. Objeto aditivo

[openapi.yaml](../../backend/src/main/resources/openapi.yaml), desde las líneas **94 y 103**, incorpora indicadorBcrp como propiedad opcional de DashboardResponse, con:

| Campo | Semántica |
|---|---|
| codigoSerie | Código aprobado realmente consultado |
| descripcion | Denominación económica oficial |
| unidad | Variación porcentual interanual (%) |
| periodicidad | ANUAL |
| sectorSolicitado | Filtro recibido por EcoInvest |
| sectorEconomico | Alcance real de la serie |
| ambito | SECTORIAL o NACIONAL |
| anioSolicitado | Año seleccionado |
| periodo | Año efectivo observado, solo con dato válido |
| valor | Decimal oficial, solo con dato válido |
| origen | API_DIRECTA_BCRP o NO_DISPONIBLE |
| motivo | Explicación de ausencia; omitida en éxito |
| urlFuente | URL con código y rango anual |

Los campos no disponibles se omiten mediante anotaciones Jackson generadas desde el contrato. No se editó manualmente `IndicadorBcrp.java` ni otro archivo generado.

### 6.2. Compatibilidad de respuesta

Se conservan total, aprobados, proyectos, fuentes y variacionSeleccionada. fuentes.bcrp continúa siendo String.

El controlador toma el BigDecimal del cliente y lo asigna tanto a variacionSeleccionada como a indicadorBcrp.valor. En ausencia, ambos se omiten y fuentes.bcrp es NO_DISPONIBLE. La lógica y datos SENACE no se alteraron.

Evidencia: [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), líneas **49–96**.

Las cuatro rutas permanecen:

```text
GET /api/dashboard
GET /api/estado-servicios
GET /api/consultoras
GET /api/senace/datastream
```

La generación de ApiApi y modelos desde OpenAPI y las pruebas MockMvc verificaron su compatibilidad dentro del entorno de prueba.

## 7. Comandos autorizados y resultados

Directorio de ejecución:

```text
D:\Documentos\GitHub\Proyecto-SOA\backend
```

### 7.1. Generación y compilación

```powershell
.\mvnw.cmd -o -DskipTests compile
```

El primer intento dentro del sandbox terminó con error de acceso al repositorio local `C:\.m2\repository`. Se solicitó aprobación de ejecución fuera del sandbox y se repitió el mismo comando offline.

**Resultado definitivo:** código 0, BUILD SUCCESS, finalizado el **18/10/2026 a las 16:57:32 -05:00**. Se generaron interfaces y modelos, incluido IndicadorBcrp, y se compilaron 10 fuentes con objetivo release 17.

No fue necesario corregir errores de compilación tras ejecutar Maven.

### 7.2. Pruebas

```powershell
.\mvnw.cmd -o test
```

Se ejecutó con acceso aprobado a la caché local, manteniendo modo offline.

**Resultado:** código 0, BUILD SUCCESS, finalizado el **18/10/2026 a las 16:58:15 -05:00**. Total: **75 pruebas, 0 fallos, 0 errores, 0 omitidas**.

| Clase | Pruebas ejecutadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| BcrpClientTest | 59 | 0 | 0 | 0 |
| SenaceClientTest | 4 | 0 | 0 | 0 |
| AppConfigTest | 1 | 0 | 0 | 0 |
| EcoInvestControllerTest | 10 | 0 | 0 | 0 |
| SoaApplicationTests | 1 | 0 | 0 | 0 |
| **Total** | **75** | **0** | **0** | **0** |

Los casos parametrizados se contabilizan individualmente. El incremento respecto de las 18 pruebas anteriores corresponde principalmente al catálogo, estructura JSON, temporalidad y precisión.

### 7.3. Evidencias locales

- [Resultado BCRP](../../backend/target/surefire-reports/com.ecoinvest.soa.client.BcrpClientTest.txt).
- [Resultado MVC](../../backend/target/surefire-reports/com.ecoinvest.soa.controller.EcoInvestControllerTest.txt).
- [Resultado SENACE](../../backend/target/surefire-reports/com.ecoinvest.soa.client.SenaceClientTest.txt).
- [Resultado AppConfig](../../backend/target/surefire-reports/com.ecoinvest.soa.config.AppConfigTest.txt).
- [Resultado de contexto](../../backend/target/surefire-reports/com.ecoinvest.soa.SoaApplicationTests.txt).

Los artefactos bajo target/ son generados y pueden desaparecer al limpiar el build. Este reporte conserva el resumen de resultados observado.

### 7.4. Entorno y límites

La compilación usó release 17, pero el proceso de pruebas utilizó **Java 20.0.2**. No se migró la versión declarada del proyecto. Una ejecución específicamente sobre runtime JDK 17 permanece pendiente si se requiere certificar ese entorno exacto.

No se descargaron dependencias, se llamó a APIs reales durante los tests ni se realizaron operaciones SQLite. El contexto de prueba conserva las exclusiones de persistencia de la fase anterior; no se inició un servicio productivo o un servidor HTTP real.

## 8. Cobertura de los requisitos

| Requisito | Evidencia de validación |
|---|---|
| Resolución correcta de las seis series y alias Minería | Prueba parametrizada de catálogo, metadatos y URL exacta |
| Año en URL anual | MockRestServiceServer exige código y año en ambos segmentos |
| Selección sin depender del orden | Respuesta con 2018 antes de 2017 selecciona 2018 al solicitarlo |
| Períodos fuera del año solicitado | Ausencia PERIODO_FUERA_DE_RANGO |
| periods vacío | Ausencia SIN_OBSERVACION |
| JSON válido como text/html UTF-8 | RestTemplate real con transporte simulado y parseo Jackson |
| HTML auténtico y JSON inválido | Ausencia FORMATO_INVALIDO |
| Una serie y una columna | Rechazo de múltiples series, columnas inválidas y estructuras inesperadas |
| Duplicados y períodos mensuales inesperados | Rechazo explícito |
| BigDecimal preciso | Casos de Strings/números JSON y serialización MVC con más precisión que double |
| Transportes, Energía y desconocidos | Sin serie sustituta, sin HTTP y ausencia documentada |
| TODOS nacional | PM04983AA, sector económico PBI y ámbito NACIONAL |
| Compatibilidad de las cuatro rutas | Pruebas MockMvc con formas y campos existentes |
| Errores HTTP y conexión | Propagación hacia la frontera Resilience4j existente |

Las respuestas económicas pequeñas usan el contenido documentado en Fase 2A. Los decimales largos artificiales y mutaciones estructurales se identifican como casos técnicos, no observaciones reales atribuidas al BCRP.

## 9. Revisión de alcance

- `git diff --check` terminó sin errores de espacios. Git emitió avisos de normalización LF/CRLF, sin fallos de build asociados.
- Se compararon hashes SHA-256 antes y después de la implementación/validación para POM, wrapper, application.yml, SenaceClient, AppConfig y los tests protegidos de SENACE, configuración y contexto. Permanecieron iguales.
- Los únicos cambios fuente de Fase 2B son los cinco archivos autorizados.
- Los cambios de fases previas no fueron revertidos, incluidos reportes y archivos aún no versionados.
- No se editó manualmente target/, no se alteró la base y no se generó este reporte hasta recibir permiso independiente.

## 10. Riesgos y pendientes

1. **No hay ejecución end-to-end nueva:** las pruebas HTTP del cliente son simuladas y MVC usa modo standalone. La validación oficial Fase 2A acredita muestras del proveedor, pero no la aplicación actual conectada a él.
2. **Resilience4j:** la interceptación efectiva y su comportamiento integral siguen pendientes de una prueba específica autorizada; no se cambiaron sus mecanismos.
3. **Identidad estricta:** un cambio de denominación oficial causará FORMATO_INVALIDO hasta revisar el catálogo. Evita aceptar otra serie por accidente, pero requiere mantenimiento.
4. **Transporte real:** el caso text/html con JSON ya está probado localmente; aún falta observar la petición del cliente implementado contra el proveedor, con sus timeouts reales.
5. **SENACE:** sigue en el estado previo, con datos fijos declarados SIMULADO. No se completó su adaptación real en esta fase.
6. **Transportes y Energía:** permanecen sin indicador mientras no haya una serie específicamente verificada y aprobada.
7. **Alcance económico:** Electricidad y Agua, Minería Metálica y PBI nacional deben conservar sus etiquetas públicas. No representan exactamente cualquier proyecto o subsector SENACE.
8. **Errores semánticos y métricas:** FORMATO_INVALIDO o ausencia no se convierten en errores HTTP del proveedor; no se modificó la política de resiliencia para contarlos.
9. **Entorno exacto Java 17:** objetivo de compilación verificado; runtime de pruebas 20.0.2, sin certificación separada en JDK 17.
10. **Consumidores:** deben admitir ausencia del indicador y pueden aprovechar el objeto aditivo; el frontend aún no se implementa.

## 11. Criterios de aceptación del lote

| Criterio | Resultado |
|---|---|
| Usar exclusivamente catálogo anual aprobado | Implementado y probado |
| Año determina petición y observación | Implementado y probado con URL exacta y períodos desordenados |
| Sin año vecino o constante sustitutiva | Implementado y probado |
| JSON leído como texto y Jackson explícito | Implementado y probado con Content-Type observado |
| Validar serie, columnas y formato | Implementado y probado |
| Preservar precisión y coherencia entre valores | Implementado y probado también en serialización |
| Trazabilidad de código, unidad, ámbito, período y procedencia | Implementada y verificada en MVC |
| Sectores no soportados sin sustitución | Implementado y probado sin HTTP |
| Mantener cuatro rutas | Verificado mediante OpenAPI generado y MockMvc |
| Mantener SENACE, AppConfig, versiones, Resilience4j y SQLite | Alcance respetado |
| Compilación y suite satisfactorias | BUILD SUCCESS; 75 pruebas superadas |
| Operación productiva end-to-end | Pendiente; fuera de lo demostrado por esta suite |

## RESULTADO PARA REVISIÓN CON CHATGPT

La Fase 2B está implementada y validada mediante generación OpenAPI, compilación y 75 pruebas locales satisfactorias. EcoInvest resuelve solo las series anuales aprobadas, consulta una por petición con inicio=fin=año seleccionado, procesa JSON como texto incluso si llega text/html y valida identidad, estructura, período y decimal. Ya no depende del último registro ni convierte los valores por double.

El dashboard conserva los campos previos y agrega indicadorBcrp con metadatos trazables. valor y variacionSeleccionada comparten el mismo BigDecimal. TODOS comunica PBI nacional; minería y electricidad reflejan sus ámbitos reales. Transportes, Energía y desconocidos devuelven ausencia justificada sin consultar otro sector.

SENACE, su simulación, frontend, SQLite, Resilience4j y dependencias permanecen intactos respecto del inicio del lote. No se realizaron commits o llamadas reales BCRP durante la validación.

**Pendiente principal:** autorizar y diseñar una verificación real acotada del cliente BCRP implementado, sin abrir SQLite ni depender de SENACE, y evaluar por separado la interceptación Resilience4j. No se debe afirmar funcionamiento end-to-end hasta realizar una ejecución apropiada. Si se requiere certificación estricta en Java 17, repetir la suite con ese JDK sin migrar las versiones del proyecto.
