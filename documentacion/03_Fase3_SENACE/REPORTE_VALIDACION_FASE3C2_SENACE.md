# EcoInvest — Fase 3C.2: validación controlada con SENACE real

Fecha: **04/11/2026**, America/Lima (UTC−05:00).

Estado: **Validación directa del cliente Java con ambos recursos reales satisfactoria, dentro del alcance descrito**. No certifica el funcionamiento end-to-end de EcoInvest ni la cobertura completa de los datasets oficiales.

## 1. Objetivo de la fase

Verificar que las clases productivas SenaceTransport, SenaceClient, DTOs, SenaceMapper y ResultadoConsultaSenace procesan respuestas oficiales de CarteraProyectos y ConsultorasAmbientales. Se realizaron dos ejecuciones independientes, cada una con un único GET aprobado. No se repitieron las consultas para obtener estadísticas adicionales.

Las estadísticas y filtros posteriores se calcularon sobre los DTOs conservados temporalmente en memoria. No se guardaron cuerpos completos, filas, credenciales o datasets como fixtures.

## 2. Estado inicial

- Fase 3B.2 implementó los cuatro lotes SENACE: modelos/mapper, transporte, cliente y exposición pública.
- [Fase 3C.1](REPORTE_VALIDACION_FASE3C1_SENACE.md) verificó el flujo integrado MVC/Jackson/AOP con transporte SENACE y BCRP simulados: 32 casos satisfactorios el 04/11/2026.
- Las consultas históricas de Fase 3A obtuvieron 2.567 filas de proyectos y 15.015 filas de consultoras mediante otro procedimiento. No se incorporaron esos totales como expectativas de este test.
- BCRP Fase 2C permanece cerrado dentro de su alcance; no se hicieron consultas ni modificaciones BCRP en esta fase.
- Permanecen vigentes las [decisiones D01–D19](REPORTE_DECISIONES_FASE3B1_SENACE.md).

La evidencia de este reporte procede de ejecuciones Java reales capturadas por las herramientas durante la sesión; no se presenta una salida aportada por el usuario ni un fixture como nueva observación oficial.

## 3. Componentes Java reales utilizados

Archivo creado con autorización:

`backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java`

Flujo ejecutado:

```text
JUnit opt-in → SenaceClient real, llamada directa a consultarDatastream(recurso)
  → SenaceTransport real / Apache HttpClient 5
  → HTTPS SENACE → respuesta JSON array
  → Jackson del cliente → DTOs oficiales → SenaceMapper real
  → metadatos internos y validaciones agregadas del test
```

No se utilizó Mockito ni sustitución del transporte. No se creó un contexto Spring. El ObjectMapper se construyó mediante Jackson2ObjectMapperBuilder con el módulo obtenido de AppConfig.jsonNullableModule(), sin importar AppConfig completo. SenaceClient utiliza su copia privada estricta de Jackson.

Se eligió consultarDatastream para obtener todos los DTOs sin imponer el filtro anual predeterminado. El código real del cliente valida las filas y las mapea antes de entregar el resultado genérico. Después se reutilizan los DTOs con el mapper para verificar campos, perfiles y el filtro de proyectos, sin volver a invocar el cliente.

**Límite importante:** esta ejecución es directa y no atraviesa un proxy AOP. La interceptación y fallbacks AOP se verificaron con respuestas artificiales en 3C.1; no se atribuye esa prueba a estas dos consultas reales.

Referencias: [test, línea 44](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java#L44), [consultoras, línea 98](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java#L98), [guardas y construcción, línea 135](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java#L135), [consulta única, línea 162](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java#L162). Los fragmentos #L dependen del visor Markdown.

## 4. Estrategia de configuración y credenciales

El usuario autorizó por separado crear el test, compilarlo y, para cada recurso, leer las dos claves locales necesarias y realizar una consulta.

Configuración empleada: `backend/.env.apis.local`, interpretado como texto dentro de la JVM de prueba, ejecutada desde backend/. Se consumieron únicamente:

```text
SENACE_BASE_URL
SENACE_AUTH_KEY
```

El parser no ejecuta PowerShell, no expande expresiones ni exporta valores al entorno del sistema. Acepta valores literales y comillas exteriores, rechaza claves necesarias duplicadas o valores incompletos y valida el destino HTTPS oficial antes de construir el cliente. No se utilizó un default histórico de credencial ni se modificó el archivo local.

Los valores no se pasaron como argumentos Maven, no se imprimieron y no se copiaron a archivos temporales o reportes. El test desactiva temporalmente los logs Apache pertinentes y restaura los niveles al finalizar; no vuelca excepciones externas, causas ni cuerpos. Los fallos de cliente se presentan mediante categorías seguras.

La clase está desactivada por defecto mediante `ecoinvest.test.senace-real=true`. Antes de leer configuración exige además selección exacta de un único método, `ecoinvest.test.senace-credentials=true`, recurso explícito y fuente de configuración explícita. Esas guardas técnicas no sustituyen la aprobación humana del encargo.

Referencia: [lectura de configuración, línea 229](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java#L229), [control de logs, línea 274](../../backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java#L274).

El éxito acredita que la solicitud emitida con la credencial configurada obtuvo datos procesables. **No demuestra que auth_key sea obligatorio ni que cualquier JWT de sesión sea una API key compatible**: no se hicieron comparaciones sin credencial o con mecanismos alternativos.

## 5. Endpoints y presupuesto autorizado

| Recurso | URL sin credencial | Solicitudes realizadas |
|---|---|---:|
| CarteraProyectos | https://datosabiertos.senace.gob.pe/Api/datastreams/CarteraProyectos | 1 GET |
| ConsultorasAmbientales | https://datosabiertos.senace.gob.pe/Api/datastreams/ConsultorasAmbientales | 1 GET |
| **Total** | Solo dominio y rutas oficiales verificadas, sin /data.json | **2 GET** |

auth_key se añadió en memoria por el transporte productivo; no se reproduce la URL autenticada.

| Control | Configuración productiva conservada |
|---|---|
| Máximo de cuerpo | 6.291.456 bytes (6 MiB), control durante streaming; mecanismo centinela existente |
| Conexión | 5 segundos |
| Inactividad de lectura | 10 segundos |
| Presupuesto HTTP total | 25 segundos |
| Reintentos automáticos | Desactivados |
| Redirecciones automáticas | Desactivadas |
| TLS y hostname | Validación normal; sin cambios en certificados o truststore |

No hubo solicitudes BCRP, a otros datastreams o de control. Al no utilizar paginación documentada, ambas operaciones podían recibir el conjunto devuelto por el endpoint. No se afirma que el límite local reduzca el trabajo del servidor.

El límite de cuerpo no limita por sí solo toda la memoria de DTOs/parseo; el presupuesto HTTP tampoco representa la duración total de Maven o de todo el análisis. Se conservan las limitaciones de DNS/cancelación documentadas en el [Lote 2](REPORTE_IMPLEMENTACION_FASE3B2_LOTE2.md).

## 6. Resultados HTTP reales y alcance de observación

| Recurso | Estado del cliente | Procedencia | Cuerpo completo | Cobertura | Filas |
|---|---|---|---|---|---:|
| CarteraProyectos | EXITO | API_DIRECTA_SENACE | true | NO_VERIFICADA | 2.567 |
| ConsultorasAmbientales | EXITO | API_DIRECTA_SENACE | true | NO_VERIFICADA | 15.015 |

El cliente solo acepta RECIBIDO completo con HTTP 200 en su método interno recibir(). Por ello, **HTTP 200 es una inferencia sustentada en ese camino de código y su éxito**, no un campo HTTP capturado directamente por el test.

SenaceClient no expone el Content-Type original, tamaño exacto, Content-Length o duración aislada del intercambio en su respuesta pública interna. Estos campos **no se midieron ni se inventaron**. Las cifras históricas de bytes de Fase 3A no se reutilizan como tamaños medidos de estas ejecuciones.

El transporte/cliente validaron JSON completo con raíz array y deserialización tipada. El formato se acreditó por procesamiento real, no únicamente por un encabezado Content-Type. El éxito HTTPS con el transporte normal acredita que estas conexiones superaron la validación TLS, sin constituir una auditoría exhaustiva de la cadena del servidor.

## 7. Validación de CarteraProyectos

La prueba validarCarteraProyectos procesó **2.567 registros**, con **cero IDs repetidos** en la respuesta observada. La unicidad dentro de esta respuesta no demuestra estabilidad histórica del ID ni proyectos únicos certificados.

Se verificaron las correspondencias de los trece campos del DTO con el mapeo real. Los originales se conservan; los opcionales en blanco siguen las reglas aprobadas. RUC permanece String e ID Long, sin conversión flotante. LATITUD y LONGITUD permanecen BigDecimal; las coordenadas mapeadas se compararon con las originales mediante igualdad decimal que conserva escala.

| Campo | Tipo DTO observado | Ausente o null tras DTO | En blanco |
|---|---|---:|---:|
| ID | Long, 2.567 | 0 | 0 |
| TITULAR | String, 2.567 | 0 | 0 |
| RUC | String, 2.567 | 0 | 1 |
| TITULO_PROYECTO | String, 2.567 | 0 | 0 |
| UNIDAD_PROYECTO | String, 2.567 | 0 | 0 |
| TIPO | String, 2.567 | 0 | 0 |
| ACTIVIDAD | String, 2.567 | 0 | 0 |
| FECHA_INICIO | String, 2.567 | 0 | 0 |
| ESTADO | String, 2.567 | 0 | 0 |
| DESCRIPCION | String, 2.567 | 0 | 0 |
| LONGITUD | BigDecimal, 2.567 | 0 | 0 |
| LATITUD | BigDecimal, 2.567 | 0 | 0 |
| RESOLUCION | String, 2.567 | 0 | 126 |

Son perfiles de las propiedades deserializadas. Después del DTO no se distingue entre campo JSON ausente y campo explícitamente null; tampoco se realiza aquí un inventario independiente de campos externos desconocidos ignorados por los DTOs.

### Fechas y distribución por año de inicio

Los 2.567 valores fueron evaluables bajo la regla aprobada **dd/MM/uuuu estricta**: 2.567 VALIDA, cero ausentes y cero inválidas. Esto no cambia la semántica de FECHA_INICIO a fecha de aprobación ni acredita una interpretación alternativa día/mes.

| Año de inicio interpretado | Registros |
|---|---:|
| 2008 | 1 |
| 2016 | 164 |
| 2017 | 296 |
| 2018 | 377 |
| 2019 | 318 |
| 2020 | 227 |
| 2021 | 357 |
| 2022 | 382 |
| 2023 | 345 |
| 2024 | 100 |
| **Total** | **2.567** |

El rango observado no debe convertirse en catálogo fijo o prueba de actualización del proveedor. La presencia de un registro de 2008 es un dato observado; no se declara error sin evidencia adicional.

### Coordenadas

Se observaron 2.567 pares BigDecimal y cero coordenadas no utilizables por las validaciones vigentes del mapper. Los rangos se evalúan bajo el supuesto documentado de grados globales; no validan CRS, región, localización efectiva ni elegibilidad cartográfica de cada punto.

### Filtro en memoria

Usando el mismo conjunto, sin nuevo GET:

```text
Actividad de comparación: MINERIA
Año de FECHA_INICIO: 2017
Registros filtrados: 105
Aprobados entre registros filtrados: 93
Estado de filtrado: APLICADO
Fechas no evaluables: 0
```

Se verificaron los conteos y la selección frente a las filas mapeadas. El resultado del mapper puro conserva cuerpoCompleto desconocido: no se usa el mapper para fabricar evidencia HTTP. La evidencia true pertenece a la consulta original del cliente.

No se inventó inversión, moneda, región o expediente ni se transformó ACTIVIDAD en equivalencia económica BCRP.

## 8. Validación de ConsultorasAmbientales

Se procesaron y mapearon **15.015 filas**, conservando orden y cardinalidad. La comparación por igualdad de las ocho columnas encontró **cero repeticiones exactas**. Esto no demuestra 15.015 empresas distintas, unicidad por razón social o ausencia de duplicados de negocio.

| Campo | Tipo DTO observado | Ausente o null tras DTO | En blanco |
|---|---|---:|---:|
| RAZON_SOCIAL | String, 15.015 | 0 | 0 |
| ACTIVIDAD | String, 15.015 | 0 | 0 |
| SUBSECTOR | String, 15.015 | 0 | 0 |
| TIPO_SOLICITUD | String, 15.015 | 0 | 0 |
| RESOLUCION | String, 15.015 | 0 | 1.588 |
| FECHA_RD | String, 15.015 | 0 | 0 |
| INICIO_VIGENCIA_RD | String, 15.015 | 0 | 2 |
| FIN_VIGENCIA_RD | String, 15.015 | 0 | 39 |

Se compararon los ocho campos con su correspondencia mapeada y se verificó que cada resultado conserva la referencia a su DTO original en el mismo orden. Los blancos opcionales se normalizan a ausencia conforme al mapper; los textos de fecha no vacíos se conservan sin deducir vigencia administrativa.

No se añadieron RUC, ID, registro, nivel o estado ficticios. ACTIVIDAD y SUBSECTOR permanecen conceptos distintos. No se deduplicó ni se calculó activo/vencido.

## 9. Calidad, cobertura y resultados agregados

| Aspecto | Observación real | Límite de interpretación |
|---|---|---|
| Cardinalidad | 2.567 proyectos; 15.015 filas de consultoras | Coincide con cifras históricas, pero no son constantes ni universo certificado |
| Identificación de proyectos | ID no null, Long, sin repetición en la respuesta | No demuestra estabilidad histórica |
| Fechas de proyectos | Todas parseables con regla aprobada | No demuestra fecha de aprobación ni elimina ambigüedad semántica histórica |
| Coordenadas | BigDecimal conservado y rangos utilizables | No demuestra CRS o ubicación real |
| Consultoras | Ocho campos conservados y sin repeticiones exactas | No define identidad empresarial |
| Ausencias | Blancos visibles en agregados y normalización honesta | DTO no distingue ausencia de clave frente a null JSON |
| Cobertura | NO_VERIFICADA | Recibir cuerpo completo no demuestra dataset completo |
| Autenticación | Solicitudes con clave configurada obtuvieron datos | No se hizo control de obligatoriedad o equivalencia JWT |

No se imprimieron nombres, RUC, resoluciones, proyectos individuales o filas completas. La distribución anual y conteos son agregados, no una muestra de registros personales.

## 10. Evidencia del mapeo Java

Las comprobaciones utilizaron las implementaciones productivas existentes:

- [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java): validación de recepción completa, deserialización y mapeo antes de responder.
- [SenaceTransport.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceTransport.java): destino, auth_key, HTTP/TLS y límites.
- [SenaceMapper.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceMapper.java): fechas, actividad, campos opcionales, coordenadas y conteos.
- [ProyectoSenaceDto.java](../../backend/src/main/java/com/ecoinvest/soa/client/dto/ProyectoSenaceDto.java): trece campos, ID estricto, RUC String y coordenadas BigDecimal.
- [ConsultoraSenaceDto.java](../../backend/src/main/java/com/ecoinvest/soa/client/dto/ConsultoraSenaceDto.java): ocho campos originales.
- [ResultadoConsultaSenace.java](../../backend/src/main/java/com/ecoinvest/soa/client/ResultadoConsultaSenace.java): metadatos, estados y cobertura.

El resultado genérico entrega los DTOs después de ejecutar su validación/mapeo real. Los análisis adicionales vuelven a aplicar el mapper sobre esas mismas filas para comprobar correspondencias y filtros. **No son otra consulta ni prueban por separado una nueva llamada a consultarProyectos(sector, anio)**. Ese método tipado y su orquestación MVC tienen evidencia con transporte simulado en 3C.1.

## 11. Comandos y pruebas ejecutadas

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\backend`.

Configuración temporal de proceso, usada en las ejecuciones:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

No se cambió permanentemente JAVA_HOME, PATH, JDK o configuración del sistema.

### Compilación autorizada sin ejecución de tests

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests test-compile
```

Resultado: **BUILD SUCCESS**, 04/11/2026 **06:40:03 −05:00**, tiempo total **01:05 min**. Oracle JDK 17.0.12 y Maven 3.9.9 confirmados. Se compilaron 27 fuentes productivas y nueve archivos de tests, incluido SenaceRealApiTest. No se ejecutaron tests en este paso.

### Ejecución autorizada de CarteraProyectos

```powershell
.\mvnw.cmd -o '-Dtest=SenaceRealApiTest#validarCarteraProyectos' '-Decoinvest.test.senace-real=true' '-Decoinvest.test.senace-credentials=true' '-Decoinvest.test.senace-config=local' '-Decoinvest.test.senace-resource=CarteraProyectos' test
```

Resultado: **BUILD SUCCESS**, 04/11/2026 **06:41:10 −05:00**, tiempo Maven **35,654 s**. Surefire: **1 ejecutada, 0 fallos, 0 errores, 0 omitidas**; duración de clase informada **5,729 s**. Una solicitud externa.

### Ejecución autorizada de ConsultorasAmbientales

```powershell
.\mvnw.cmd -o '-Dtest=SenaceRealApiTest#validarConsultorasAmbientales' '-Decoinvest.test.senace-real=true' '-Decoinvest.test.senace-credentials=true' '-Decoinvest.test.senace-config=local' '-Decoinvest.test.senace-resource=ConsultorasAmbientales' test
```

Resultado: **BUILD SUCCESS**, 04/11/2026 **06:42:24 −05:00**, tiempo Maven **42,474 s**. Surefire: **1 ejecutada, 0 fallos, 0 errores, 0 omitidas**; duración de clase **11,211 s** en XML, consola 11,21 s. Una solicitud externa.

Las dos ejecuciones son independientes, cada una selecciona un método. No se afirma que existió una sola suite actual de dos casos ni se suman con las 32 pruebas de 3C.1 para atribuir una ejecución conjunta inexistente.

El XML final de Surefire confirmó java.version=17.0.12, java.vendor=Oracle Corporation y java.home=C:\Program Files\Java\jdk-17. El test también exige ese runtime antes de acceder a configuración o HTTP.

### Artefactos y conservación de evidencia

- [XML Surefire actual](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.integration.SenaceRealApiTest.xml).
- [Resumen Surefire actual](../../backend/target/surefire-reports/com.ecoinvest.soa.integration.SenaceRealApiTest.txt).

Al compartir clase, **la segunda ejecución sustituyó los reportes Surefire de la primera**. El XML actual acredita ConsultorasAmbientales; la evidencia de CarteraProyectos corresponde a la salida de herramientas capturada durante la sesión y transcrita aquí. No se repitió la solicitud ni se crearon copias de artefactos por conveniencia. Los archivos target/ son volátiles.

Maven regeneró OpenAPI 7.4.0 bajo target/ en sus ciclos autorizados; no se modificó el contrato fuente ni se editaron modelos generados manualmente. Offline impide resolución remota Maven, **no** el HTTP ejecutado explícitamente por el test opt-in.

## 12. Aislamiento de SQLite y de la aplicación

La prueba instancia clases explícitamente, sin ApplicationContext, SpringBootTest, SoaApplication, DataSource, EntityManager, autoconfiguración JPA, servidor MVC o procesos auxiliares creados por el test. No importa AppConfig completo ni crea el RestTemplate compartido.

No se ejecutaron comandos SQLite ni se inició el backend completo. No hubo código de persistencia en el flujo. La validación de aislamiento se sustenta en la construcción explícita inspeccionada y el alcance ejecutado, **no** en una nueva aserción de beans de un contexto inexistente ni en una auditoría OS de archivos. Las aserciones de ausencia de beans pertenecen a 3C.1.

Apache crea los recursos internos necesarios para su HTTP real y el transporte conserva sus mecanismos productivos de cierre. Maven/Surefire ejecuta sus JVM habituales autorizadas. Esto no equivale a iniciar EcoInvest o un servidor de pruebas.

## 13. Errores y correcciones pendientes

No se produjeron fallos de compilación ni fallos de test en las tres ejecuciones descritas. No se demostró un defecto productivo que requiriera corrección.

Persisten avisos no fatales de API obsoleta en SenaceTransport, operaciones genéricas sin comprobación en SenaceTransportTest y configuración opcional de postprocesado de OpenAPI. No se ampliaron cambios para resolverlos.

Limitación concreta de observabilidad: el cliente no entrega código HTTP/Content-Type/tamaño originales al consumidor de esta prueba. Por ello se registra HTTP 200 como inferido y se deja Content-Type no observado directamente. No se cambió producción para añadir estos campos. Si una futura tarea exige observabilidad directa, debe justificar y autorizar el cambio mínimo; no requiere repetir ahora las consultas exitosas.

Único archivo de código creado en esta fase: SenaceRealApiTest.java. Se mantuvieron intactos los cambios productivos preexistentes. El presente reporte es una creación documental posterior específicamente autorizada. No se modificó README ni otros reportes, no se hicieron commits y no se instalaron dependencias.

## 14. Limitaciones del proveedor y de la validación

1. Es una observación puntual del 04/11/2026, no una garantía de disponibilidad o frescura futura.
2. No se certificó cobertura, paginación o universo completo del proveedor.
3. No se probaron otros datastreams ni variantes de ruta/auth.
4. No se hizo una prueba de control sin credencial; no se certifica obligatoriedad del token.
5. No se provocaron errores externos, redirecciones, límites o timeouts reales: su manejo tiene evidencia simulada/aislada previa.
6. Los 6 MiB y tiempos se conservaron; no se midieron tamaño exacto ni cada fase de transporte en esta ejecución. El éxito implica que el flujo no rechazó las respuestas por sus límites, no un nuevo benchmark exhaustivo.
7. Los tipos observados son posteriores a deserialización; no se inventa un inventario de claves desconocidas del JSON original.
8. Duplicados exactos distintos de cero no fueron observados en consultoras; no equivale a una regla de unicidad.
9. La ubicación real y la semántica administrativa de fechas siguen limitadas a decisiones/evidencia previas.
10. No se combinó en una sola ejecución MVC + proveedor real + AOP. No declarar end-to-end a partir de sumar 3C.1 y 3C.2.

## 15. Estado final de Fase 3C.2

| Criterio | Resultado |
|---|---|
| Acceso HTTPS mediante transporte productivo, rutas verificadas y credencial local | Satisfactorio en ambos GET |
| Procesamiento JSON array, DTOs y mapper reales | Satisfactorio |
| Precisión, fechas, campos y conteos | Satisfactorio en los datos observados |
| Ausencias, procedencia y cobertura | Conservadas explícitamente |
| Sin simulaciones como sustitución ni filas publicadas | Cumplido en las ejecuciones |
| Sin SQLite, arranque completo o cambios productivos | Cumplido en el alcance inspeccionado/ejecutado |
| Observabilidad HTTP directa completa | Limitada; 200 inferido y Content-Type no capturado |
| Cobertura oficial y end-to-end de EcoInvest | No certificados |

**Puede cerrarse Fase 3C.2 dentro del alcance de validación directa controlada del cliente con ambos recursos reales**, conservando las limitaciones anteriores. No se ha certificado el arranque completo, despliegue, persistencia, frontend o dashboard end-to-end.

## 16. Requisitos restantes antes del frontend

- Revisar conjuntamente los cierres 3C.1 y 3C.2 sin repetir implementaciones o consultas exitosas por rutina.
- Definir si se necesita una validación aislada adicional de orquestación pública con datos reales; debe tener presupuesto y archivos propios, evitando GET redundantes.
- Confirmar la estrategia de configuración del proceso productivo/local/nube. Que el test lea el archivo local no demuestra que Spring Boot lo cargue automáticamente.
- Planificar por separado cualquier arranque real: application.yml conserva configuración SQLite/JPA y no debe iniciarse incidentalmente con riesgo de ddl-auto.
- Acordar criterios de preparación del backend y decisiones de producto pendientes sobre cobertura, identidad de consultoras y semántica geográfica, sin inventar reglas.
- Mantener las cuatro rutas y el contrato OpenAPI; al preparar React, usar metadatos, ausencia y errores reales. No iniciar frontend mediante este reporte.

## 17. Siguiente paso recomendado

Compartir este reporte y el de 3C.1 con ChatGPT para preparar un prompt de **revisión de cierre del backend y planificación de la siguiente validación**, no de reimplementación de SENACE.

El siguiente encargo debería inspeccionar el estado actual, distinguir pruebas directas de MVC y decidir el alcance mínimo faltante antes del frontend. Si propone arrancar Spring Boot, utilizar SQLite, ejecutar HTTP, crear tests/reportes o cambiar configuración, debe solicitar las autorizaciones pertinentes antes de actuar. No hay una nueva subfase funcional iniciada por este documento.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest es integración SOA SENACE+BCRP con Java 17, Oracle JDK 17.0.12, Spring Boot 3.2.4, OpenAPI y Resilience4j. BCRP Fase 2C continúa cerrado en su alcance técnico. Fase 3B.2 implementó los cuatro lotes SENACE. Fase 3C.1 validó MVC/controlador/cliente/Jackson/mapper/AOP con transporte y BCRP simulados: 32 casos satisfactorios, sin SQLite ni HTTP externo.

Fase 3C.2 ahora está validada dentro de su alcance directo con SENACE real. Se creó únicamente backend/src/test/java/com/ecoinvest/soa/integration/SenaceRealApiTest.java, opt-in desactivado por defecto, con selección exacta de método y autorizaciones explícitas de recurso/credenciales/configuración. No se modificó producción, controlador, OpenAPI, BCRP, Resilience4j, SQLite o frontend.

El test instancia SenaceTransport y SenaceClient reales con Jackson/DTOs/mapper productivos, sin mocks, contexto Spring, servidor o persistencia. Lee solamente SENACE_BASE_URL y SENACE_AUTH_KEY del archivo local ignorado dentro de la JVM autorizada, como texto y sin mostrar valores. Consulta una vez consultarDatastream por recurso y reutiliza DTOs en memoria para análisis; no llama otra vez al proveedor para filtrar. No prueba AOP real en esta fase.

El 04/11/2026 la compilación offline test-compile terminó BUILD SUCCESS a las 06:40:03 −05:00. La prueba seleccionada de CarteraProyectos terminó BUILD SUCCESS a las 06:41:10, un caso sin fallos/errores/omisiones; ConsultorasAmbientales terminó BUILD SUCCESS a las 06:42:24, otro caso independiente sin fallos/errores/omisiones. Oracle JDK 17.0.12 y Maven 3.9.9. No presentar ambas como una sola suite ejecutada ni sumarlas con 3C.1.

Se hicieron exactamente dos GET a las rutas oficiales sin /data.json: /Api/datastreams/CarteraProyectos y /Api/datastreams/ConsultorasAmbientales, HTTPS normal, sin reintentos/redirecciones, con los límites existentes de 6 MiB y 5/10/25 segundos. Ambos devolvieron resultados EXITO, API_DIRECTA_SENACE, cuerpoCompleto=true y cobertura NO_VERIFICADA. HTTP 200 es inferido por la condición de aceptación del cliente; el Content-Type original y bytes exactos no se capturaron. No se demostró que auth_key fuera obligatorio.

Proyectos: 2.567 filas; cero IDs repetidos; ID Long, RUC String, coordenadas BigDecimal conservadas; todas las fechas parseables bajo dd/MM/uuuu estricto, cero no evaluables. Años: 2008=1, 2016=164, 2017=296, 2018=377, 2019=318, 2020=227, 2021=357, 2022=382, 2023=345, 2024=100. Cero coordenadas no utilizables bajo los rangos del mapper, sin certificar CRS/ubicación. Un RUC en blanco y 126 resoluciones en blanco. Filtro MINERIA/2017 en memoria: 105 registros y 93 aprobados. Son registros filtrados, no proyectos únicos certificados.

Consultoras: 15.015 filas y ocho campos String conservados, sin repeticiones exactas de las ocho columnas; no afirmar empresas únicas. Blancos: RESOLUCION=1.588, INICIO_VIGENCIA_RD=2, FIN_VIGENCIA_RD=39. No se inventaron RUC, ID, registro, nivel, estado o vigencia. Los DTOs no distinguen clave ausente de null JSON en los perfiles.

Los reportes Surefire comparten clase: el XML actual acredita la segunda ejecución y sustituyó el primero. La primera se conserva como salida capturada en sesión y en este reporte, sin repetir GET. No se guardaron cuerpos completos ni se publicaron filas/secretos. El transporte abrió solo los recursos HTTP necesarios; no se arrancó Spring Boot ni se accedió a SQLite.

El cierre es del cliente directo con proveedores reales, no end-to-end. Siguiente prompt recomendado: revisar cierre del backend con este reporte y REPORTE_VALIDACION_FASE3C1_SENACE.md, confirmar configuración de proceso/despliegue y planificar únicamente la validación mínima que falte antes del frontend. No asumir carga automática de .env, no arrancar el contexto productivo con SQLite/JPA por accidente, no repetir BCRP o consultas SENACE exitosas sin motivo y no iniciar frontend o nuevas fases automáticamente.
