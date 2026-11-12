# EcoInvest — LKG, Bloque 2: coordinación interna SENACE/BCRP

Fecha: 11/11/2026. Zona horaria: America/Lima.

## 1. Resumen ejecutivo

Se implementaron coordinadores internos que utilizan los clientes SENACE/BCRP existentes, guardan respuestas válidas mediante el repositorio JSON del Bloque 1 y recuperan respaldos utilizables cuando falla el proveedor o el circuito impide el transporte.

La compilación offline terminó en **BUILD SUCCESS** con Oracle JDK 17.0.12. La última ejecución de ocho clases de prueba terminó con **223 pruebas satisfactorias, cero fallos, cero errores y cero omisiones**. Incluye 27 pruebas nuevas del Bloque 2 y regresiones de clientes, integración y almacenamiento.

**La coordinación permanece sin activación pública.** Los coordinadores no son beans registrados automáticamente y el controlador no los consume. No cambian OpenAPI, frontend, headers, respuestas HTTP ni el Monitor, que conserva `respaldoLocal=NO_IMPLEMENTADO`.

La evidencia procede de código y pruebas con respuestas artificiales, no de consultas oficiales ni de una validación end-to-end desplegada. No se leyeron credenciales, iniciaron servicios reales, abrieron bases SQLite, instalaron dependencias, realizaron commits ni despliegues.

## 2. Estado inicial y autorizaciones

Antecedente: [REPORTE_IMPLEMENTACION_LKG_BLOQUE1.md](REPORTE_IMPLEMENTACION_LKG_BLOQUE1.md). El repositorio de snapshots ya tenía claves cerradas, esquema versionado, lectura validada, control de tamaño, checksum, temporal y reemplazo atómico, bloqueo de propietario y pruebas aisladas.

El usuario autorizó seis archivos nuevos y modificaciones exclusivamente en `SenaceClient.java` y `BcrpClient.java`. Aprobó las políticas temporales y de arrays vacíos propuestas para el diseño, manteniendo la nueva coordinación fuera de endpoints. Después autorizó compilación y ocho clases de prueba offline con directorios temporales. Finalmente autorizó crear este reporte.

No se modificaron archivos del Bloque 1, reportes históricos, índice documental, YAML, contrato, controlador ni frontend.

## 3. Archivos creados y modificados

| Acción | Archivo | Responsabilidad |
|---|---|---|
| Crear | [ResultadoLkg.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/ResultadoLkg.java) | Resultado interno con datos, procedencia, intento API, respaldo, calidad y estados de almacenamiento. |
| Crear | [SenaceSnapshotCoordinator.java](../backend/src/main/java/com/ecoinvest/soa/client/SenaceSnapshotCoordinator.java) | Coordinación de los dos recursos SENACE verificados, sin registro Spring automático. |
| Crear | [BcrpSnapshotCoordinator.java](../backend/src/main/java/com/ecoinvest/soa/client/BcrpSnapshotCoordinator.java) | Coordinación de observaciones económicas por serie/año exactos. |
| Crear | [SenaceSnapshotCoordinatorTest.java](../backend/src/test/java/com/ecoinvest/soa/client/SenaceSnapshotCoordinatorTest.java) | Trece pruebas de coordinación SENACE y regresión. |
| Crear | [BcrpSnapshotCoordinatorTest.java](../backend/src/test/java/com/ecoinvest/soa/client/BcrpSnapshotCoordinatorTest.java) | Nueve pruebas de coordinación BCRP y regresión. |
| Crear | [LkgCoordinationIntegrationTest.java](../backend/src/test/java/com/ecoinvest/soa/integration/LkgCoordinationIntegrationTest.java) | Cinco pruebas con proxies AOP y contexto mínimo. |
| Modificar | [SenaceClient.java](../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java) | Entrada interna de cartera completa y reutilización de mapeo sin filtros. |
| Modificar | [BcrpClient.java](../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java) | Acceso interno al contexto económico vigente sin HTTP. |

Los archivos sin seguimiento del Bloque 1 se preservaron. No se descartó trabajo previo ni se crearon commits.

## 4. Arquitectura y flujo

```text
Coordinador interno construido explicitamente
  -> cliente real a traves de proxy Resilience4j
     -> transporte simulado en pruebas / transporte existente en codigo
     -> validacion y fallback seguro actuales
  -> respuesta directa valida: intentar guardar, devolver procedencia directa
  -> fallo: leer snapshot de clave exacta
     -> AVAILABLE + integridad valida: mapear, devolver RESPALDO_LOCAL
     -> ausente/invalido/vencido/error: SIN_RESPALDO, datos desconocidos
```

El coordinador no crea otro transporte, no realiza GET al leer el archivo, no añade reintentos y no envuelve con otro circuit breaker sus operaciones de disco. Los consumidores públicos actuales siguen llamando a los métodos históricos.

Los constructores reciben cliente, repositorio y reloj. SENACE recibe además un `ObjectMapper` del que usa copia privada. La aplicación futura deberá suministrar los clientes mediante sus proxies; construir un cliente directamente no activa anotaciones AOP. Las pruebas unitarias distinguen explícitamente ese caso de las pruebas de integración con aspecto real.

## 5. Coordinación SENACE

`SenaceClient.consultarCarteraCompleta()` es una entrada nueva con `@CircuitBreaker(name="senace", fallbackMethod="fallbackCarteraCompleta")`. Reutiliza transporte, deserialización y validación actuales. No aplica el año predeterminado 2017 ni filtros de actividad. Su helper privado `mapearCarteraCompleta()` también es utilizado por el datastream genérico de proyectos, manteniendo su comportamiento sin filtros.

`SenaceSnapshotCoordinator.consultarProyectos(actividad, anio)`:

1. Captura el inicio de solicitud e invoca la entrada completa a través del cliente suministrado.
2. Exige procedencia directa, estado EXITO/VACIA y cuerpo completo.
3. Construye el payload permitido desde los DTO originales de todas las filas, preservando los trece campos conocidos, precisión decimal y RUC textual.
4. Valida el payload e intenta guardar el conjunto completo.
5. Aplica `SenaceMapper.mapearProyectos()` para la selección solicitada. El año nulo conserva el default 2017 y representa FECHA_INICIO.

`consultarConsultoras()` utiliza el método existente del cliente, conserva los DTO originales de todas las filas y los ocho campos, sin deduplicación ni inferencia de vigencia.

Cuando la consulta falla, el coordinador admite únicamente `ReadStatus.AVAILABLE`; exige edad no negativa y vuelve a verificar el sobre mediante `SnapshotValidator`. Después mapea el payload almacenado. EXPIRED, INVALID, MISSING y errores no se convierten en listas oficiales vacías.

Los metadatos de calidad del mapper se copian a `CalidadSenace` sin reutilizar su declaración de origen como prueba de consulta directa. La procedencia final se fija explícitamente en `ResultadoLkg`. La cobertura del respaldo continúa siendo `NO_VERIFICADA`.

No se respaldaron automáticamente los seis datastreams heredados. El genérico conserva su ruta y manejo previo; no consulta al coordinador ni lo activa.

## 6. Coordinación BCRP

`BcrpClient.contextoConsulta(sector, anio)` es un método de paquete que delega en `resultadoBase()` sin realizar transporte. Permite al coordinador identificar código, año y metadatos desde el catálogo vigente sin mantener otro catálogo económico.

`BcrpSnapshotCoordinator.consultar(sector, anio)` invoca el método anotado existente `obtenerDatosBcrp()`. Una respuesta elegible exige origen `API_DIRECTA_BCRP`, valor `BigDecimal`, ausencia de motivo de error y coincidencia exacta de código/período. Se almacena únicamente el payload interno `serie`, `periodo`, `valor`.

En recuperación se consulta exclusivamente la misma clave; no se buscan años vecinos ni otras series. Se reconstruyen los metadatos económicos desde el contexto vigente y se marca también el mapa interno como `RESPALDO_LOCAL`. La URL del contexto se elimina de los datos recuperados; no se persiste ninguna URL en el snapshot.

Un retorno `SIN_OBSERVACION` o `PERIODO_FUERA_DE_RANGO` se trata como ausencia económica explícita, no como caída: no se guarda ni se oculta mediante un valor histórico. Parámetros inválidos tampoco generan acceso al repositorio ni transporte. Esta distinción deberá conservarse al diseñar el contrato público.

Para una respuesta económica válida, `cuerpoCompleto=true` expresa la finalización normal de lectura y parseo mediante RestTemplate; no acredita cobertura oficial. No se introdujo un límite de streaming BCRP nuevo: se conserva el comportamiento de transporte existente.

## 7. Resultado interno y procedencia

`ResultadoLkg<T>` incluye:

- `datos`: lista de proyectos/consultoras o mapa económico; null cuando no hay datos utilizables.
- `origen`: `API_DIRECTA_SENACE`, `API_DIRECTA_BCRP`, `RESPALDO_LOCAL` o `SIN_RESPALDO`.
- `intentoApi`: estado, motivo seguro y evidencia de cuerpo completo del intento actual.
- `respaldo`: captura UTC, edad, indicador de antigüedad, versión de esquema y cobertura.
- `calidadSenace`: estado de los datos, filtrado, conteos y advertencias del mapper, cuando corresponde.
- `lectura` y `escritura`: estados internos del repositorio, cuando se intentó la operación.

Se impide construir un resultado `RESPALDO_LOCAL` sin datos, metadata y lectura AVAILABLE, o un `SIN_RESPALDO` con datos. `toString()` omite el contenido.

Los conteos de una selección respaldada describen las filas almacenadas y filtradas, no una observación actual del universo oficial. No se convierten errores o ausencia de respaldo en cero.

## 8. Políticas aprobadas y configuración

| Fuente | stale-after aprobado | max-age aprobado |
|---|---|---|
| SENACE | `PT24H` | `P90D` |
| BCRP | `P7D` | `P365D` |

Son duraciones ISO-8601 compatibles con `SnapshotProperties`; los días son intervalos de 24 horas. Se utiliza fecha de captura UTC. Superar stale-after marca el respaldo como antiguo, pero puede utilizarse hasta max-age; superar max-age produce EXPIRED. Exactamente en los límites se conserva la comparación estricta `>` del repositorio.

Estas políticas fueron aplicadas explícitamente en los fixtures/configuración de pruebas. **No se activaron ni se agregaron defaults productivos**: `SnapshotConfiguration` del Bloque 1 sigue requiriendo configuración explícita para habilitarse. Tampoco se registraron coordinadores mediante `@Component` o nuevos beans.

Para vacíos SENACE se mantiene la política aprobada: primera copia vacía válida permitida; una nueva copia vacía no reemplaza la anterior no vacía. La respuesta directa vacía sí se devuelve como tal, informando `EMPTY_REPLACEMENT_REJECTED` del almacenamiento. No se oculta automáticamente detrás de filas anteriores.

Una actualización con inicio anterior o igual al snapshot existente se rechaza según el control temporal del repositorio. El resultado directo puede seguir devolviéndose al consumidor interno; la escritura rechazada no lo convierte en fallo del proveedor. El descarte de respuestas obsoletas en UI no forma parte de este bloque.

## 9. Resilience4j y aislamiento del perfil

La coordinación se sitúa después de la invocación al proxy. El aspecto registra la llamada lógica al cliente antes de que se evalúe el respaldo. La lectura local no se cuenta como nueva consulta exitosa al proveedor y un error de escritura no cambia las métricas del circuito.

Se mantuvieron los nombres y la configuración de circuitos existentes: ventana 5, umbral de fallos 50 % y espera en OPEN de 10 segundos. No se añadieron reintentos ni se forzó manualmente OPEN en las pruebas nuevas; se alcanzó mediante fallos controlados.

Límites conservados del cliente BCRP:

- Algunos errores de formato retornan normalmente; el circuito puede contabilizarlos como llamadas completadas. Una prueba confirma esa semántica histórica y simultáneamente verifica que no se declara origen directo con datos disponibles.
- El fallback actual comunica `ERROR_EXTERNO` también ante circuito abierto. Las pruebas verifican que OPEN bloquea transporte y permite recuperar respaldo, pero el resultado BCRP no identifica todavía ese motivo específico cuando lo absorbió el fallback. No se alteró el fallback público para corregirlo incidentalmente.

El contexto de integración carga el perfil real `integracion-local` mediante ConfigData, incluyendo las exclusiones DataSource/JPA y política Resilience4j. Se comprueba ausencia de DataSource, EntityManager, EntityManagerFactory y ServletWebServerFactory, y presencia de proxies AOP reales. No se inicia la aplicación completa ni un puerto HTTP.

SENACE usa transporte simulado; BCRP usa RestTemplate real enlazado a MockRestServiceServer sin sockets. Las pruebas no cargan archivos de credenciales. El aislamiento se sustenta en código/configuración y aserciones, no en una auditoría externa de todas las llamadas del sistema operativo.

## 10. Comandos y resultados realmente ejecutados

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\backend`.

Preparación temporal de los procesos:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Compilación:

```powershell
java -version
.\mvnw.cmd -o -DskipTests compile
```

Runtime confirmado: Oracle Java 17.0.12, build `17.0.12+8-LTS-286`. Compilación finalizada el 11/11/2026 a las 21:59:58 -05:00, duración Maven 7,317 segundos, código 0 y BUILD SUCCESS.

Comando de pruebas ejecutado dos veces, la segunda tras corregir las expectativas de AOP:

```powershell
.\mvnw.cmd -o "-Dtest=SnapshotValidatorTest,JsonFileSnapshotRepositoryTest,SenaceSnapshotCoordinatorTest,BcrpSnapshotCoordinatorTest,LkgCoordinationIntegrationTest,SenaceClientTest,BcrpClientTest,SenaceIntegrationTest" test
```

Primera ejecución: finalizó a las 22:01:01 -05:00, duración 24,292 segundos; 223 pruebas, 2 fallos de expectativas, 0 errores y 0 omisiones, BUILD FAILURE.

La copia inicial válida ya había contado como una llamada exitosa dentro de la ventana. Los tests continuaban inyectando un número fijo de fallos cuando el circuito ya había abierto, acumulando dos rechazos donde esperaban uno. Se corrigió únicamente `LkgCoordinationIntegrationTest` para detener el bucle al abrir el circuito, contar intentos efectivos y comprobar que la siguiente consulta no incrementa el transporte. No fue necesario cambiar código productivo ni política Resilience4j.

Última ejecución: finalizó a las **22:02:05 -05:00**, duración Maven **21,399 segundos**, código 0 y **BUILD SUCCESS**.

| Clase ejecutada | Pruebas | Fallos | Errores | Omitidas | Tiempo Surefire |
|---|---:|---:|---:|---:|---:|
| `BcrpClientTest` | 59 | 0 | 0 | 0 | 0,595 s |
| `BcrpSnapshotCoordinatorTest` | 9 | 0 | 0 | 0 | 1,090 s |
| `SenaceClientTest` | 83 | 0 | 0 | 0 | 2,121 s |
| `SenaceSnapshotCoordinatorTest` | 13 | 0 | 0 | 0 | 0,440 s |
| `LkgCoordinationIntegrationTest` | 5 | 0 | 0 | 0 | 0,672 s |
| `SenaceIntegrationTest` | 32 | 0 | 0 | 0 | 4,736 s |
| `JsonFileSnapshotRepositoryTest` | 14 | 0 | 0 | 0 | 0,363 s |
| `SnapshotValidatorTest` | 8 | 0 | 0 | 0 | 0,010 s |
| **Total de la última ejecución** | **223** | **0** | **0** | **0** | — |

No se suman ejecuciones anteriores a este total. Las 22 pruebas del Bloque 1 forman parte de esta selección actual.

Evidencia: archivos `backend/target/surefire-reports/<paquete>.<Clase>.txt` de las ocho clases, contrastados al crear este reporte. Son artefactos regenerables, no documentos históricos permanentes; no se incorporan XML completos con datos de entorno.

Maven offline regeneró OpenAPI y compiló fuentes bajo `target/`. Se compilaron las clases de test existentes, pero se ejecutaron solamente las ocho seleccionadas. No se editó manualmente ningún artefacto generado. Persisten advertencias históricas de API obsoleta/unchecked en transporte y su test, sin cambios incidentales.

## 11. Cobertura de las pruebas y límites

Se comprobaron:

- Captura SENACE completa antes de filtrar y recuperación con una selección de año/actividad distinta.
- Precisión BigDecimal, ID grande representable como Long, RUC textual, fechas no evaluables, conteos y duplicados.
- Fallo sin respaldo, JSON/filas inválidas, HTML y cuerpo incompleto sin sobrescribir copia válida.
- Primera respuesta vacía, vacío directo sobre copia no vacía y selección sin coincidencias como casos distintos.
- Reconstrucción del repositorio sobre archivos temporales, captura original y antigüedad.
- Corruptos y vencidos no utilizables; antiguos dentro de max-age utilizables con advertencia.
- Fallos de escritura inyectados sin invalidar respuesta directa ni alterar métricas del proveedor.
- BCRP exacto por serie/año, metadatos del catálogo y no sustitución entre períodos o series.
- Concurrencia con respuesta anterior completada tarde, preservando el snapshot de solicitud más reciente.
- OPEN mediante aspecto real, recuperación local y ausencia de nuevo transporte.
- Preservación de clientes y del flujo MVC anterior mediante suites históricas con fixtures.

La reconstrucción del repositorio ocurre dentro de la misma JVM; no se reinició Spring Boot ni un proceso real. Se recrearon contextos de prueba AOP sin compartir su estado entre escenarios. No se hizo un stress test, benchmark de datasets reales, simulación física de disco lleno ni ensayo de corte eléctrico. Los fallos de disco son inyectados y el almacenamiento utiliza únicamente temporales autorizados.

No se evaluó la coordinación con consumidores HTTP nuevos, porque no se habilitó públicamente. Los escenarios API_DIRECTA de los fixtures describen el camino simulado, no llamadas oficiales ejecutadas en esta fase.

## 12. Contratos posteriores y riesgos pendientes

El [OpenAPI actual](../backend/src/main/resources/openapi.yaml) y el controlador continúan aceptando procedencias directas para datos públicos disponibles. `ResultadoConsultaSenace` no fue ampliado. No es correcto conectarle `ResultadoLkg` ni etiquetar sus datos locales como API directa sin diseñar el contrato siguiente.

Para un bloque posterior se requiere autorización específica para:

1. Exponer `RESPALDO_LOCAL`, fecha de captura, edad, antigüedad y motivo seguro del intento API.
2. Mantener separadas las procedencias SENACE/BCRP del Dashboard y las ausencias económicas.
3. Conservar array raíz de Consultoras y definir headers seguros/CORS correspondientes.
4. Adaptar controlador, modelos generados, tipos/adaptadores/vistas React y pruebas de contrato.
5. Registrar coordinadores e inyectarlos de forma explícita con clientes proxificados y configuración temporal aprobada.
6. Cambiar el Monitor únicamente cuando la funcionalidad operativa esté habilitada y verificada; distinguir existencia, integridad/última verificación del respaldo, circuito y disponibilidad externa.
7. Decidir si el motivo de circuito abierto BCRP debe exponerse con más precisión mediante un cambio autorizado.

Persisten los límites de almacenamiento del Bloque 1: único propietario cooperante, directorio/ancestros confiables, dependencia del soporte real de reemplazo atómico y garantías limitadas ante fallos físicos. El orden se decide por inicio de solicitud mediante reloj; marcas iguales rechazan la actualización y retrocesos de reloj no se resuelven mediante coordinación distribuida.

La validación, transformación a JsonNode y copias defensivas consumen memoria proporcional al dataset. Guardar es síncrono y puede añadir latencia de disco; no se introdujeron hilos de escritura en segundo plano ni caché adicional. Falta medir consumo sobre el presupuesto real del despliegue antes de activación.

Render Free no proporciona almacenamiento de archivos durable entre reinicios/suspensión/redeploys. Este bloque no resuelve esa condición ni modifica servicios remotos. Una activación con requisito de persistencia debe utilizar un directorio/volumen conservado o almacenamiento adecuado previamente aprobado; véase el diagnóstico del Bloque 1.

## 13. Estado final y siguiente paso

**Bloque 2 implementado y validado en coordinación aislada.** Se alcanzó recuperación interna con archivos temporales y transportes controlados, conservando contratos públicos y política Resilience4j.

No se declara completado el respaldo automático operativo del sistema desplegado. El usuario sigue viendo los comportamientos públicos anteriores hasta autorizar e implementar el contrato y la integración siguientes.

Siguiente paso recomendado: revisar este reporte y preparar el diseño del Bloque 3 de procedencia/contrato público y activación controlada. No iniciar automáticamente cambios en OpenAPI, Monitor, frontend o despliegue. No se actualizó el índice documental por asociación.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest dispone de repositorio JSON LKG del Bloque 1 y ahora de dos coordinadores internos no registrados automáticamente: `SenaceSnapshotCoordinator` y `BcrpSnapshotCoordinator`, con `ResultadoLkg<T>` separado del contrato HTTP. SENACE utiliza la nueva entrada proxificada `consultarCarteraCompleta()`, respalda DTO completos antes de filtrar y reutiliza el cliente de consultoras. BCRP reutiliza `obtenerDatosBcrp()` y el nuevo contexto interno sin HTTP para respetar código/período exactos y catálogo vigente.

Políticas aprobadas: SENACE PT24H/P90D, BCRP P7D/P365D; todavía sin defaults ni activación productiva. Se mantiene primera copia vacía válida y protección contra reemplazo de copia no vacía por array vacío. BCRP no oculta `SIN_OBSERVACION`/`PERIODO_FUERA_DE_RANGO` con un valor antiguo.

El 11/11/2026 se compiló y ejecutaron ocho suites offline con Oracle JDK 17.0.12: **223 pruebas, cero fallos/errores/omisiones y BUILD SUCCESS final**. Hubo una primera ejecución con dos expectativas AOP incorrectas, corregidas únicamente en el test; la consulta válida inicial ya participaba en la ventana del circuito. Se verificaron proxies reales, OPEN sin nuevo transporte, persistencia entre instancias del repositorio y ausencia de infraestructura SQLite en contexto mínimo. No hubo llamadas oficiales, credenciales, servidor real ni despliegue.

No se modificaron OpenAPI, controlador, ResultadoConsultaSenace, frontend, YAML ni Monitor. LKG sigue fuera de endpoints. Falta diseñar/autorizar contrato público, avisos de respaldo y registro de coordinadores, así como resolver almacenamiento durable en nube. BCRP conserva dos límites históricos: errores lógicos pueden contar como retornos normales en el breaker y su fallback informa ERROR_EXTERNO incluso cuando el rechazo provino de OPEN. El próximo prompt debe partir de este alcance real y no repetir consultas oficiales o activar Bloque 3 automáticamente.
