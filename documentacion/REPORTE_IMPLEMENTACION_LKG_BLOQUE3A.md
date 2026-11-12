# EcoInvest — LKG, Bloque 3A: integración pública backend y OpenAPI

Fecha: 11/11/2026. Zona horaria de ejecución: America/Lima.

## 1. Resumen ejecutivo

Se implementó la integración pública backend de los coordinadores Last Known Good (LKG) de SENACE/BCRP mediante activación opt-in. El contrato OpenAPI distingue respuesta directa, respaldo local y ausencia de respaldo utilizable, incluyendo captura, antigüedad y resultado seguro del intento API y del almacenamiento.

La generación OpenAPI y compilación terminaron en **BUILD SUCCESS** con Oracle JDK 17.0.12. La última ejecución seleccionada terminó con **308 pruebas satisfactorias, cero fallos, cero errores y cero omisiones**, distribuidas en once clases. Incluye 26 pruebas nuevas de configuración/contrato público, así como regresiones de clientes, coordinación, controlador, MVC y almacenamiento.

**LKG permanece desactivado por defecto.** No se habilitó en producción, se inició servidor real ni se realizó despliegue. La validación del modo habilitado fue exclusivamente mediante contextos Spring mínimos, MockMvc, transportes artificiales y directorios temporales.

El frontend no se modificó y todavía requiere adaptación antes de consumir las nuevas procedencias. El datastream genérico permanece sin LKG para todos sus recursos. No se consultaron APIs oficiales, cargaron credenciales, abrieron bases SQLite, instalaron dependencias ni realizaron commits.

## 2. Estado de partida y autorizaciones

Antecedentes:

- [Bloque 1: almacenamiento JSON y validación](REPORTE_IMPLEMENTACION_LKG_BLOQUE1.md).
- [Bloque 2: coordinación interna SENACE/BCRP](REPORTE_IMPLEMENTACION_LKG_BLOQUE2.md).
- [Cierre técnico anterior de EcoInvest](REPORTE_CIERRE_TECNICO_ECOINVEST.md), como antecedente, no como descripción inmutable del código posterior.

El Bloque 1 proporcionó repositorio JSON versionado con validación al guardar y leer, integridad, límite de archivo, bloqueo de propietario y reemplazo atómico. El Bloque 2 añadió coordinación independiente del contrato público y obtuvo una ejecución histórica de 223 pruebas satisfactorias.

El usuario aprobó el diseño del Bloque 3A, cinco modificaciones y cinco archivos nuevos, incluidos cambios OpenAPI, política HTTP y headers. Posteriormente autorizó generación, compilación y pruebas offline con temporales. Finalmente autorizó este reporte.

La autorización no incluyó frontend, activación operativa del despliegue, proveedores reales, cambios de políticas Resilience4j, modificaciones de perfiles YAML, commits ni actualización de otros reportes/índice. Se preservó el trabajo pendiente de los bloques anteriores.

## 3. Archivos del bloque

| Acción | Archivo | Función |
|---|---|---|
| Modificar | [openapi.yaml](../backend/src/main/resources/openapi.yaml) | Procedencias LKG, metadatos, headers, Monitor y documentación de compatibilidad. |
| Modificar | [EcoInvestController.java](../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java) | Selección condicional del camino LKG para Dashboard/Consultoras y observaciones de Monitor. |
| Modificar | [SnapshotConfiguration.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotConfiguration.java) | Registro opt-in de reloj, repositorio observado, servicio de estado y coordinadores. |
| Modificar | [SenaceSnapshotCoordinator.java](../backend/src/main/java/com/ecoinvest/soa/client/SenaceSnapshotCoordinator.java) | Captura persistida confirmada en respuestas directas cuya escritura fue SAVED. |
| Modificar | [BcrpSnapshotCoordinator.java](../backend/src/main/java/com/ecoinvest/soa/client/BcrpSnapshotCoordinator.java) | Captura confirmada y acceso al contexto económico seguro para representar ausencias. |
| Crear | [LkgPublicMapper.java](../backend/src/main/java/com/ecoinvest/soa/controller/LkgPublicMapper.java) | Traducción del resultado interno a modelos OpenAPI, política HTTP y headers seguros. |
| Crear | [SnapshotStatusService.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotStatusService.java) | Últimas observaciones locales acotadas por clave, sin leer datasets desde Monitor. |
| Crear | [ObservedSnapshotRepository.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/ObservedSnapshotRepository.java) | Decorador que observa operaciones terminadas sin I/O adicional. |
| Crear | [LkgConfigurationTest.java](../backend/src/test/java/com/ecoinvest/soa/config/LkgConfigurationTest.java) | Cinco pruebas de configuración opt-in y observabilidad. |
| Crear | [LkgPublicIntegrationTest.java](../backend/src/test/java/com/ecoinvest/soa/integration/LkgPublicIntegrationTest.java) | Veintiún casos ejecutados, incluidos parámetros, con MVC/contrato/AOP reales. |

No se modificaron en este bloque `SenaceClient`, `SenaceTransport`, `BcrpClient`, `SenaceMapper`, `ResultadoConsultaSenace`, `ResultadoLkg`, archivos del repositorio base, YAML, frontend ni políticas Resilience4j. Las modificaciones de los clientes visibles en Git corresponden al Bloque 2 y fueron preservadas.

Maven generó artefactos en `backend/target/`, sin edición manual. Entre los modelos nuevos figuran `LkgMetadata`, `LkgIntentoApi`, `LkgRespaldo`, `LkgEstadoLectura`, `LkgEstadoEscritura`, `EstadoModuloLkg` y `ObservacionSnapshot`.

## 4. Arquitectura resultante

```text
EcoInvestController
  |-- LKG desactivado: clientes y respuestas historicas
  |-- LKG habilitado:
       |-- Dashboard -> coordinador SENACE + coordinador BCRP, independientes
       |-- Consultoras -> coordinador SENACE
       |-- Monitor -> metricas de circuitos + ultimas observaciones en memoria
       |-- Datastream generico -> camino directo anterior, SIN LKG

Coordinador -> cliente proxificado por Resilience4j -> transporte existente
           -> repositorio observado -> repositorio JSON validado
           -> ResultadoLkg -> LkgPublicMapper -> modelos OpenAPI
```

El almacenamiento continúa fuera de los métodos interceptados del proveedor. Un fallo de escritura no se transforma en fallo del cliente ni invalida sus datos correctos. La recuperación no es una nueva consulta externa y no convierte el intento fallido en éxito externo.

El controlador conserva su constructor de tres dependencias. La inyección opcional `configurarLkg()` recibe ambos coordinadores y el servicio de estado únicamente cuando el módulo está registrado. Sin esos beans se conserva el camino histórico. El mapper público no realiza red ni acceso al repositorio.

## 5. Activación opt-in y políticas

La condición sigue siendo:

```properties
ecoinvest.snapshots.enabled=true
```

La propiedad ausente o con valor false no registra los beans LKG. No se añadió una activación a `application.yml`, `application-integracion-local.yml`, perfiles cloud ni configuración remota.

Para habilitarlo posteriormente se requiere configuración explícita equivalente a:

```properties
ecoinvest.snapshots.senace-stale-after=PT24H
ecoinvest.snapshots.senace-max-age=P90D
ecoinvest.snapshots.bcrp-stale-after=P7D
ecoinvest.snapshots.bcrp-max-age=P365D
```

El directorio se toma de `ECOINVEST_SNAPSHOT_DIR`; el predeterminado local continúa siendo `snapshots`, relativo al proceso. Debe utilizarse un directorio conservado, excluido de Git y de las imágenes, con ancestros confiables y permisos apropiados. Ningún directorio productivo fue creado mediante esta tarea.

Las duraciones son intervalos ISO-8601 desde la captura UTC: los días representan 24 horas. Superar stale-after marca antigüedad; superar max-age impide usar automáticamente el snapshot. El límite exacto conserva la comparación estricta `>` del repositorio. Habilitar sin política completa produce un error de configuración explícito; esa situación fue probada sin arrancar servicios.

Se registra un reloj nombrado `snapshotClock`, sustituible explícitamente en pruebas, y los coordinadores reciben los proxies administrados por Spring. No se introduce persistencia JPA/SQLite. `SnapshotConfiguration` ofrece un repositorio observado por defecto cuando no existe otro bean `SnapshotRepository`; una sustitución personalizada deberá conservar validación y observabilidad equivalentes.

Las pruebas del perfil cargan ConfigData real de `integracion-local` y comprueban las exclusiones declaradas, bind loopback y ausencia de infraestructura persistente en sus contextos mínimos. No equivalen a haber arrancado la aplicación completa con toda posible configuración externa.

## 6. Contrato público y procedencia

### 6.1 Orígenes y compatibilidad

`OrigenSenace` admite ahora `API_DIRECTA_SENACE`, `RESPALDO_LOCAL`, `SIN_RESPALDO` y el valor histórico `NO_DISPONIBLE`. BCRP documenta `API_DIRECTA_BCRP`, `RESPALDO_LOCAL`, `SIN_RESPALDO` y `NO_DISPONIBLE` histórico, manteniendo sus propiedades de origen textuales existentes.

El modo desactivado sigue usando los valores históricos. Con LKG, SIN_RESPALDO significa que la operación no entregó datos utilizables; no necesariamente que no exista ningún archivo en disco. `lectura` distingue MISSING, EXPIRED, INVALID u otra incidencia cuando se intentó leer. En una ausencia económica explícita puede no haberse intentado lectura.

Se conservaron los cuatro GET, parámetros, default anual 2017, raíces de respuesta, campos de negocio y precisión decimal. Añadir valores enum es una extensión del contrato pero puede requerir cambios en consumidores estrictos; no se considera compatible automáticamente con los validadores frontend actuales.

### 6.2 Metadatos LKG

`ConsultaSenace.lkg` e `IndicadorBcrp.lkg` son opcionales y se omiten cuando el módulo no participa. Incluyen:

| Campo | Interpretación |
|---|---|
| `intentoApi.estado` | Resultado seguro del intento actual: EXITO, VACIA, ERROR_EXTERNO, FORMATO_INVALIDO, PARCIAL, NO_EVALUABLE o SIN_OBSERVACION. |
| `intentoApi.motivo` | Código permitido, no texto de excepción ni cuerpo externo. |
| `intentoApi.cuerpoCompleto` | Evidencia conocida del intento actual. |
| `respaldo.capturadoEn` | Fecha UTC de captura persistida confirmada; no timestamp del proveedor. |
| `respaldo.edadSegundos` | Edad de esa captura, no edad económica de la observación. |
| `respaldo.antiguo` | Supera stale-after; un respaldo servido no puede estar EXPIRED. |
| `respaldo.versionEsquema` | Versión interna de archivo. |
| `respaldo.cobertura` | NO_VERIFICADA, también para BCRP: no se certifica cobertura por recibir una respuesta. |
| `lectura` / `escritura` | Estados seguros de las operaciones que se efectuaron. |

En datos respaldados, `ConsultaSenace.estado`, filtrado, conteos y advertencias describen los registros entregados. El error del proveedor se mantiene separadamente en `lkg.intentoApi`; no se reetiqueta como consulta externa exitosa.

`ConsultaSenace.cuerpoCompleto` y el header equivalente describen el cuerpo utilizado: con RESPALDO_LOCAL corresponden a la captura validada. No indican que el intento actual terminó correctamente; para ese alcance debe usarse `lkg.intentoApi.cuerpoCompleto`.

En respuesta directa se incorpora fecha de captura persistida únicamente si el repositorio devolvió SAVED. Con fallo de escritura o rechazo de reemplazo no se inventa esa fecha ni se realiza una lectura adicional solo para completarla. Su ausencia no demuestra que no haya un respaldo anterior.

## 7. Política por endpoint

### 7.1 GET /api/dashboard

Con LKG habilitado utiliza los dos coordinadores una vez cada uno y conserva su independencia. HTTP 200 puede contener datos directos, respaldo, ausencia o combinación degradada. El fallo de una fuente no impide evaluar la otra.

Los conteos siguen siendo registros filtrados por actividad y año de FECHA_INICIO, no proyectos únicos. La cartera completa se respalda antes del filtrado. Los proyectos publicados reciben el mismo origen de `consultaSenace`, incluyendo RESPALDO_LOCAL; no se infiere procedencia de la primera fila.

Ante SIN_RESPALDO SENACE se omiten proyectos y métricas desconocidas. BCRP conserva contexto económico de catálogo y motivo seguro aunque no haya valor. El valor es siempre de la serie/año exactos; SIN_OBSERVACION y PERIODO_FUERA_DE_RANGO no se ocultan mediante valores históricos. No se inventan inversiones, regiones, expedientes ni equivalencias sectoriales.

El modo LKG añade `Cache-Control: no-store` para evitar reutilización accidental de una respuesta con edad/metadatos variables.

### 7.2 GET /api/consultoras

Se conserva array raíz con los ocho campos opcionales, fechas originales y duplicados. No se añaden RUC, ID, nivel o vigencia administrativa.

| Situación | HTTP | Cuerpo |
|---|---:|---|
| Datos directos válidos | 200 | Filas disponibles |
| Array directo oficialmente vacío | 200 | `[]` con origen directo |
| Respaldo validado no vencido | 200 | Filas respaldadas, incluso vacío conocido |
| Formato externo inválido sin respaldo utilizable | 502 | `[]`, SIN_RESPALDO |
| Indisponibilidad/configuración ausente/cuerpo parcial sin respaldo utilizable | 503 | `[]`, SIN_RESPALDO |

El código de error corresponde al intento externo; la causa de no poder usar el respaldo se informa adicionalmente mediante su estado de lectura. `[]` en 502/503 nunca significa cero consultoras oficiales.

Se conservan los cinco headers X-Senace anteriores y se incorporan:

```text
X-Senace-Respaldo-Capturado-En
X-Senace-Respaldo-Edad-Segundos
X-Senace-Respaldo-Antiguo
X-Senace-Intento-Estado
X-Senace-Intento-Motivo
X-Senace-Snapshot-Lectura
X-Senace-Snapshot-Escritura
```

Los campos desconocidos o no aplicables se omiten. La exposición CORS incluye los headers nuevos sin ampliar los orígenes existentes; el wildcard histórico no se corrigió incidentalmente. El camino LKG devuelve `Cache-Control: no-store`.

Se mantiene la protección de vacíos: una nueva respuesta directa vacía no reemplaza una copia no vacía; se devuelve el vacío actual con `EMPTY_REPLACEMENT_REJECTED`, sin ocultarlo con filas anteriores. Una consulta posterior que falle sí puede recuperar la copia previa si continúa utilizable.

### 7.3 GET /api/estado-servicios

Mantiene circuitos, tasa de fallos y contadores de llamadas lógicas, con `disponibilidadExterna=NO_VERIFICADA`. CLOSED no certifica disponibilidad externa. Sin LKG, `respaldoLocal` conserva NO_IMPLEMENTADO y el objeto raíz `lkg` se omite.

Habilitado, el objeto raíz `lkg` describe:

- Estado HABILITADO_SIN_VERIFICACION, OPERATIVO o CON_INCIDENCIAS.
- Alcance `ULTIMAS_OBSERVACIONES_DEL_PROCESO`.
- Máximo de 128 observaciones, conservando las claves observadas más recientemente.
- Fuente/recurso/período, existencia, integridad, fecha observada, última verificación, captura válida conocida, edad y estados de lectura/escritura.

El estado por fuente se incorpora en su campo `respaldoLocal`. OPERATIVO requiere una operación local validada; no basta con registrar beans. Una operación fallida se distingue de una captura previa correcta.

Monitor no hace HTTP, escaneo del directorio ni relectura de datasets. Sus observaciones se pierden al reiniciar; archivos ya existentes quedan NO VERIFICADOS hasta que una operación los examine. No es inventario completo ni comprobación continua de integridad. Una última captura conocida puede conservarse como antecedente aunque una lectura posterior resulte inválida; los campos de existencia/integridad y verificación delimitan su interpretación.

### 7.4 GET /api/senace/datastream

Conserva su camino directo anterior sin LKG, incluso para CarteraProyectos y ConsultorasAmbientales. No aplica el filtro anual de Dashboard a proyectos y mantiene la envolvente/data. Los otros seis recursos heredados no reciben respaldo ni se declaran validados. Esta limitación quedó explícita en OpenAPI y se probó que el genérico no crea snapshot.

## 8. Resilience4j y observación del almacenamiento

No se modificaron nombres, aspectos ni políticas de circuitos. Los coordinadores reciben proxies, permiten que Resilience4j registre la llamada al cliente y evalúan archivos después del resultado/fallback. OPEN permite recuperación sin invocar otra vez el transporte.

`ObservedSnapshotRepository` delega las operaciones existentes y conserva solamente metadatos mediante `SnapshotStatusService`; no añade lectura posterior a cada escritura ni almacena el payload en la observabilidad.

Persisten los límites históricos BCRP documentados en Bloque 2: errores lógicos pueden retornar normalmente y su fallback puede informar ERROR_EXTERNO ante OPEN. Estos hechos no se reinterpretan como disponibilidad externa ni se corrigieron cambiando políticas en este bloque.

## 9. Ejecuciones reales de validación

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\backend`.

Configuración temporal de los procesos:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Generación/compilación:

```powershell
java -version
.\mvnw.cmd -o -DskipTests compile
```

Se confirmó Oracle Java 17.0.12, build `17.0.12+8-LTS-286`. La compilación terminó el 11/11/2026 a las **22:26:46 -05:00**, duración Maven **7,251 segundos**, código 0, BUILD SUCCESS.

Comando de pruebas autorizado, ejecutado nuevamente después de corregir imports:

```powershell
.\mvnw.cmd -o "-Dtest=*Snapshot*Test,Lkg*Test,EcoInvestControllerTest,SenaceClientTest,BcrpClientTest,SenaceIntegrationTest" test
```

El primer intento falló en `testCompile`, antes de ejecutar tests: `LkgPublicIntegrationTest` importaba mediante wildcard tanto matchers de MockRestServiceServer como resultados MockMvc, causando ambigüedad en `jsonPath` y `content`. Se corrigió únicamente ese test mediante imports explícitos de `requestTo` y `method`. No fue necesario modificar código productivo o contrato por este fallo.

La última ejecución terminó a las **22:28:40 -05:00**, duración Maven **31,432 segundos**, código 0 y **BUILD SUCCESS**.

| Clase de la última ejecución | Pruebas | Fallos | Errores | Omitidas | Tiempo Surefire |
|---|---:|---:|---:|---:|---:|
| BcrpClientTest | 59 | 0 | 0 | 0 | 0,813 s |
| BcrpSnapshotCoordinatorTest | 9 | 0 | 0 | 0 | 1,551 s |
| SenaceClientTest | 83 | 0 | 0 | 0 | 2,925 s |
| SenaceSnapshotCoordinatorTest | 13 | 0 | 0 | 0 | 0,712 s |
| LkgConfigurationTest | 5 | 0 | 0 | 0 | 0,211 s |
| EcoInvestControllerTest | 59 | 0 | 0 | 0 | 1,448 s |
| LkgCoordinationIntegrationTest | 5 | 0 | 0 | 0 | 0,752 s |
| LkgPublicIntegrationTest | 21 | 0 | 0 | 0 | 4,435 s |
| SenaceIntegrationTest | 32 | 0 | 0 | 0 | 3,479 s |
| JsonFileSnapshotRepositoryTest | 14 | 0 | 0 | 0 | 0,268 s |
| SnapshotValidatorTest | 8 | 0 | 0 | 0 | 0,006 s |
| **Total de esta ejecución** | **308** | **0** | **0** | **0** | — |

No se suman suites de ejecuciones históricas para obtener este total. Los resultados proceden de la selección actual de once clases y fueron contrastados con sus archivos `.txt` en `backend/target/surefire-reports/` antes de redactar este reporte.

La generación OpenAPI escribió modelos/interfaces y otros artefactos dentro de `target/`. No se ejecutaron pruebas opt-in de APIs reales ni suites completas por asociación. Se conservaron advertencias históricas de deprecación/unchecked del transporte y su test; no se modificaron incidentalmente.

## 10. Escenarios verificados y alcance de evidencia

Las pruebas nuevas verifican configuración ausente/desactivada, habilitación con política explícita, rechazo de política incompleta, registro de coordinadores/repositorio observado, ausencia de creación de directorio por simple registro, recuperación de incidencias y registro acotado sin lecturas para Monitor.

El flujo público se comprobó con controlador, clientes, DTOs, mapper, contrato generado, Jackson/JsonNullable y proxies AOP reales; SENACE se sustituye por transporte simulado y BCRP por MockRestServiceServer enlazado a RestTemplate real sin sockets.

Casos ejecutados incluyen:

1. Modo desactivado: raíces y headers históricos, NO_IMPLEMENTADO y ausencia del objeto `lkg`.
2. Monitor habilitado sin verificación: sin I/O de datasets ni llamadas de proveedor.
3. Datos directos y captura, ID grande, RUC textual, BigDecimal y opcionales.
4. Fallo individual de SENACE o BCRP y fallo de ambos con respaldo, preservando independencia.
5. Fallo sin respaldo, sin métricas oficiales cero inventadas.
6. Año económico distinto sin usar el respaldo de 2017; recurso SENACE conservado completo.
7. Ausencia económica explícita sin valor histórico sustitutivo.
8. Consultoras respaldadas con duplicados, fechas originales, captura/edad y CORS, incluida solicitud OPTIONS controlada en MVC.
9. Array directo vacío, conservación de copia no vacía y posterior recuperación.
10. Respaldo corrupto o vencido no servido, y errores externos con array raíz []/502/503.
11. Circuito OPEN alcanzado mediante fallos artificiales, respaldo sin nueva llamada y Monitor independiente del proveedor.
12. Genérico sin LKG y sin creación de archivo.
13. Reconstrucción de contexto/repositorio sobre los mismos temporales, datos conservados y observaciones de Monitor inicialmente vacías.
14. Escritura rechazada por límite real de bytes del archivo, respuesta directa conservada y CON_INCIDENCIAS observable.
15. Excepción artificial con URL/token ficticio no reflejada en headers/cuerpo.

Los contextos cargan el perfil real `integracion-local`; las pruebas comprueban bind 127.0.0.1:8081 declarado y ausencia de DataSource, EntityManager, EntityManagerFactory y ServletWebServerFactory. No se abre ese puerto para probarlo. No se inició Spring Boot completo ni se utilizaron archivos de credenciales o SQLite.

Las capturas y fechas de los tests son artificiales mediante reloj controlado. Persistencia significa reconstrucción de objetos/contextos en la misma JVM y filesystem temporal, no reinicio de proceso real ni conservación garantizada del alojamiento. La prueba de límite de escritura provoca rechazo por tamaño, no disco lleno físico; otras incidencias de almacenamiento se inyectan de forma controlada.

La evidencia de aislamiento se basa en bordes simulados, configuración y aserciones; no equivale a una auditoría del sistema operativo de todas las operaciones de archivos/red. MockMvc no certifica end-to-end de React, navegador, servicio desplegado y proveedores reales.

## 11. Limitaciones, frontend y persistencia

- React no se modificó. Sus validadores/adaptadores actuales pueden rechazar RESPALDO_LOCAL/SIN_RESPALDO o interpretar incorrectamente nuevos estados de Monitor. **No habilitar LKG para esos consumidores hasta implementar y validar el Bloque 3B autorizado.**
- Añadir schemas/enum no resuelve por sí mismo avisos visibles de datos históricos, fechas, motivos y disponibilidad en las cuatro vistas.
- Int64/BigDecimal mantienen los riesgos de precisión JavaScript existentes; este bloque no cambia la representación backend ni los resuelve mediante redondeos.
- CORS conserva el wildcard histórico; su revisión para despliegue seguro es una tarea separada. No se amplió como solución al acceso a headers.
- El Monitor solo conserva hasta 128 claves observadas durante el proceso, no un inventario persistente de todos los respaldos BCRP. No certifica integridad actual del archivo ni disponibilidad de APIs.
- Capturas, validación, JSON y copias defensivas consumen memoria proporcional al dataset; las pruebas no son un benchmark de datasets completos ni del límite de memoria del despliegue.
- Escritura es síncrona y serializada por repositorio; no se añadieron hilos de persistencia, clustering o coordinación distribuida.
- Persisten garantías y límites del Bloque 1: propietario cooperante único, ancestros confiables, dependencia de movimiento atómico y ausencia de certificación frente a pérdida eléctrica/alteración concurrente por un actor con permisos.
- Render Free conserva filesystem efímero: los JSON del contenedor no satisfacen durabilidad entre reinicios/suspensión/redeploys. Se necesita almacenamiento conservado aprobado antes de prometer persistencia en nube. No se cambió ningún plan ni servicio remoto.
- No se respaldan los seis datastreams heredados ni el endpoint genérico en este bloque.

## 12. Reversión y estado final

La reversión operativa propuesta consiste en retirar la activación o configurar `ecoinvest.snapshots.enabled=false` y reiniciar el proceso en una intervención autorizada. Los endpoints volverán al camino histórico y los archivos podrán conservarse sin usarse. No se ejecutó esa operación sobre ningún servicio real.

**Bloque 3A implementado y validado en su alcance backend aislado.** Existe selección pública condicional y contrato generado/probado para procedencia y antigüedad LKG. La configuración del proyecto no lo activa por defecto y no se desplegó.

No se actualizó README, índice ni reportes anteriores. El siguiente paso recomendado es diseñar el Bloque 3B de adaptación frontend y pruebas controladas de consumo, previa autorización específica; no iniciarlo automáticamente ni repetir consultas oficiales para documentar.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó el Bloque 3A de LKG: SnapshotConfiguration registra repositorio observado, servicio de estado y coordinadores únicamente con `ecoinvest.snapshots.enabled=true` y política explícita. Los clientes continúan proxificados por Resilience4j. Se mantienen SENACE PT24H/P90D y BCRP P7D/P365D, sin activación en YAML ni producción.

Dashboard y Consultoras tienen camino público LKG. Dashboard conserva ambas fuentes independientes y HTTP 200 degradado; Consultoras mantiene array raíz y 200 con respaldo válido, 502/503 sin respaldo según causa externa. OpenAPI añade RESPALDO_LOCAL/SIN_RESPALDO, metadata `lkg`, captura/edad/antigüedad, intento API y estados de almacenamiento. Se conservaron NO_DISPONIBLE y comportamiento histórico cuando está desactivado. El genérico permanece sin LKG. Monitor distingue estado de circuito/disponibilidad NO_VERIFICADA de las últimas operaciones locales; no escanea archivos y tras reinicio no presume existencia/integridad.

El 11/11/2026 se generó OpenAPI y compiló offline con Oracle JDK 17.0.12. Tras corregir una ambigüedad de imports exclusivamente en el test nuevo, la ejecución final obtuvo **308 pruebas, cero fallos/errores/omisiones y BUILD SUCCESS**, once clases seleccionadas. Los flujos públicos fueron MockMvc con clientes/mapper/Jackson/AOP reales, transportes artificiales y temporales. No hubo proveedores oficiales, credenciales, SQLite, servidor real, commits ni despliegue.

Se modificaron cinco archivos: OpenAPI, controlador, SnapshotConfiguration y los dos coordinadores; se crearon LkgPublicMapper, SnapshotStatusService, ObservedSnapshotRepository y dos clases de pruebas. No se modificó frontend. **LKG está desactivado por defecto y no debe activarse para React actual antes de adaptar sus tipos, validadores, headers y avisos de respaldo.** Persiste el bloqueo de durabilidad en Render Free. El siguiente prompt debe preparar Bloque 3B sin reimplementar almacenamiento/coordinación ni asumir validación end-to-end real.
