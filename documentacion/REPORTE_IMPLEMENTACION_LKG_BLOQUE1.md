# EcoInvest — LKG, Bloque 1: almacenamiento y validación aislados

Fecha de ejecución: 11/11/2026, zona America/Lima.

## 1. Resumen ejecutivo

Se implementó y validó el primer bloque del mecanismo Last Known Good (LKG): un repositorio local de snapshots JSON, validación de contenido y metadatos, integridad, publicación atómica y control de propietario/concurrencia.

La compilación offline terminó en **BUILD SUCCESS** y se ejecutaron **22 pruebas: cero fallos, cero errores y cero omisiones**, con Oracle JDK 17.0.12.

**No existe todavía fallback funcional integrado con SENACE/BCRP.** Los clientes, controlador, OpenAPI y frontend conservan su comportamiento anterior. El módulo Spring nuevo está desactivado por defecto. Este bloque no cambia el Monitor a respaldo implementado.

No se realizaron consultas externas, lectura de credenciales, arranque completo de Spring Boot, acceso a SQLite, instalaciones, commits ni despliegues.

## 2. Alcance autorizado y estado inicial

El usuario autorizó crear siete clases de producción, dos clases de pruebas y excluir el directorio local de snapshots mediante `.gitignore`. Autorizó posteriormente compilación offline y ejecución exclusiva de las dos clases nuevas, incluyendo temporales JUnit. Finalmente autorizó este documento.

El paquete `snapshot` y el archivo `.gitignore` de la raíz no existían antes del bloque. Se creó este último sin modificar los archivos de exclusión existentes en subcarpetas.

El objetivo general sigue siendo respaldar las respuestas completas y validadas de CarteraProyectos y ConsultorasAmbientales, y observaciones BCRP por serie/período exactos. La coordinación con los clientes pertenece a un bloque posterior.

## 3. Archivos creados

Todas las rutas enlazadas son relativas a este documento.

| Archivo | Responsabilidad |
|---|---|
| [SnapshotKey.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotKey.java) | Recursos autorizados y construcción de rutas internas. |
| [SnapshotEnvelope.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotEnvelope.java) | Sobre versionado, fechas, evidencia, payload y checksum. |
| [SnapshotRepository.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotRepository.java) | Contrato interno de lectura/escritura y estados seguros. |
| [JsonFileSnapshotRepository.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/JsonFileSnapshotRepository.java) | Lectura limitada, bloqueo de propietario, escritura temporal y publicación atómica. |
| [SnapshotValidator.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotValidator.java) | Validación estructural/semántica y verificación de integridad. |
| [SnapshotProperties.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotProperties.java) | Directorio, límite de archivo y política temporal explícita. |
| [SnapshotConfiguration.java](../backend/src/main/java/com/ecoinvest/soa/snapshot/SnapshotConfiguration.java) | Beans Spring opt-in; sin conexión a los clientes. |
| [JsonFileSnapshotRepositoryTest.java](../backend/src/test/java/com/ecoinvest/soa/snapshot/JsonFileSnapshotRepositoryTest.java) | Catorce pruebas del repositorio con temporales y datos artificiales. |
| [SnapshotValidatorTest.java](../backend/src/test/java/com/ecoinvest/soa/snapshot/SnapshotValidatorTest.java) | Ocho pruebas de claves, validación, configuración y metadatos. |
| [.gitignore](../.gitignore) | Exclusión de `/snapshots/` y `/backend/snapshots/`. |

No se modificaron `SenaceClient`, `SenaceTransport`, `SenaceMapper`, `BcrpClient`, `ResultadoConsultaSenace`, `EcoInvestController`, OpenAPI, políticas Resilience4j, frontend ni configuraciones YAML existentes.

## 4. Diseño implementado

### 4.1 Claves y rutas

`SnapshotKey` acepta solamente los dos recursos SENACE verificados, sin año, o las seis series BCRP del catálogo actual con año entre 1 y 9999. No acepta nombres arbitrarios de archivos ni rutas externas.

Estructura relativa al directorio configurado:

```text
senace/cartera-proyectos.json
senace/consultoras-ambientales.json
bcrp/PM04973AA/2017.json
```

La lista de códigos BCRP es una allowlist del almacenamiento, no una modificación del catálogo económico. Su mantenimiento deberá coordinarse con cambios futuros del catálogo.

### 4.2 Sobre del snapshot

Cada archivo contiene `schemaVersion`, `key`, `requestStartedAt`, `capturedAt`, `bodyComplete`, `coverage`, `validation`, `recordCount`, `payload` y `checksum`.

- Versión inicial: 1.
- Fechas UTC mediante `Instant`; la antigüedad se calcula desde la captura, nunca desde el reinicio o la fecha del archivo.
- Cobertura: `NO_VERIFICADA`.
- Validación interna: `VALIDADO`, sin implicar certificación del proveedor.
- Checksum SHA-256 del contenido y metadatos, con representación canónica de números y claves JSON.
- Copias defensivas del payload y `toString()` sin contenido externo.

El checksum detecta corrupción accidental; no autentica el archivo frente a un actor que pueda modificarlo y recalcularlo.

### 4.3 Validación del payload

Para SENACE se exige array raíz, objetos por fila, tipos compatibles y nombres de campos autorizados. Proyectos exige ID entero representable como `Long`; RUC permanece textual y las coordenadas son numéricas opcionales. Se reutilizan los métodos por fila del mapper existente. No se aplica filtro anual ni de actividad en el repositorio.

Consultoras conserva orden, duplicados, vacíos y fechas originales. No se crean identificadores, RUC, niveles o estados administrativos.

Se rechazan campos adicionales en el payload del snapshot. El futuro coordinador deberá construirlo desde los DTO oficiales permitidos, en lugar de persistir indiscriminadamente la respuesta cruda. Los textos oficiales permitidos se conservan; el módulo no pretende detectar secretos incrustados arbitrariamente en un texto de origen.

Para BCRP, el payload interno mínimo tiene exactamente `serie`, `periodo` y `valor`; la serie y el período deben coincidir con la clave y el valor debe ser numérico. Es un formato interno nuevo, no el `Map` público actual ni la respuesta HTTP BCRP sin transformar. El coordinador futuro deberá aportar solamente una observación validada por el cliente y reconstruir los metadatos económicos desde el catálogo vigente.

La validación se ejecuta al guardar y al leer, incluyendo esquema, clave, fecha, cantidad y checksum. Se rechazan fechas futuras y capturas anteriores al inicio declarado de la solicitud.

### 4.4 Escritura, integridad y concurrencia

La escritura utiliza un temporal propio en el mismo directorio del destino, límite de bytes durante serialización, `FileChannel.force(true)`, relectura/validación y `Files.move` con `ATOMIC_MOVE` y `REPLACE_EXISTING`.

Si el movimiento atómico no está soportado, se informa un estado específico y no se intenta una sustitución no atómica. Los temporales incompletos nunca se leen como respaldo. La eliminación del temporal propio se intenta al finalizar; no se hace una limpieza general de archivos huérfanos.

El acceso se serializa dentro de una instancia y se mantiene un bloqueo nativo `.owner.lock` durante su vida activa para impedir un segundo propietario cooperante. `close()` libera los recursos. El constructor no realiza operaciones de archivos; una lectura sin directorio devuelve ausencia sin crearlo. Una lectura con directorio existente puede crear el archivo de bloqueo.

Una actualización cuyo inicio de solicitud no sea posterior al respaldo existente se rechaza, evitando que una respuesta anterior completada tarde sobrescriba la más reciente. Solicitudes con la misma marca temporal también se rechazan como actualización: el coordinador futuro deberá contemplar esta condición. No se presupone un reloj monotónico global entre procesos.

La lectura impone un máximo tanto por tamaño declarado del archivo como por bytes efectivamente leídos. Se rechazan enlaces simbólicos y determinados archivos especiales detectables en la ruta.

## 5. Configuración y políticas pendientes

`SnapshotConfiguration` requiere `ecoinvest.snapshots.enabled=true`. Sin esa activación no registra los beans del módulo. No se añadió esta propiedad a los YAML existentes.

| Configuración | Comportamiento actual |
|---|---|
| `ECOINVEST_SNAPSHOT_DIR` | Directorio configurable; valor local predeterminado `snapshots`, relativo al directorio del proceso. |
| `ecoinvest.snapshots.max-file-bytes` | Máximo predeterminado de archivo completo: 16 MiB; acepta entre 1 KiB y 64 MiB. Es independiente del límite HTTP SENACE de 6 MiB, que no se alteró. |
| `ecoinvest.snapshots.senace-stale-after` | Duración ISO-8601 obligatoria al habilitar. |
| `ecoinvest.snapshots.senace-max-age` | Duración ISO-8601 obligatoria al habilitar. |
| `ecoinvest.snapshots.bcrp-stale-after` | Duración ISO-8601 obligatoria al habilitar. |
| `ecoinvest.snapshots.bcrp-max-age` | Duración ISO-8601 obligatoria al habilitar. |

No se adoptaron TTL productivos por defecto: siguen requiriendo decisión del usuario. Las duraciones presentes en tests son exclusivamente políticas artificiales de prueba.

Una lectura vencida devuelve `EXPIRED`, conserva el archivo e incluye su metadata/payload para inspección interna. **El futuro coordinador debe impedir servirlo como respaldo utilizable**; no debe limitarse a comprobar que `snapshot` sea distinto de null.

Política conservadora del almacenamiento: permite una primera respuesta vacía validada, pero rechaza reemplazar un respaldo no vacío por uno vacío. Esta salvaguarda está probada a nivel de repositorio, permanece sin uso productivo y debe confirmarse o ajustarse antes de activar la coordinación pública.

No se carga `.env`, `.env.apis.local` ni ninguna credencial. Un directorio configurado distinto de los dos predeterminados debe excluirse por separado de Git y de los contextos de build antes de utilizarlo.

## 6. Comandos realmente ejecutados

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\backend`.

Configuración temporal de cada proceso PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Primera ejecución:

```powershell
java -version
.\mvnw.cmd -o -DskipTests compile
```

Segunda ejecución:

```powershell
.\mvnw.cmd -o "-Dtest=SnapshotValidatorTest,JsonFileSnapshotRepositoryTest" test
```

El runtime mostrado fue Oracle Java 17.0.12, build `17.0.12+8-LTS-286`. El wrapper está configurado para Maven 3.9.9 y OpenAPI Generator para 7.4.0. Ambos comandos se ejecutaron offline y finalizaron con código 0.

La compilación terminó el 11/11/2026 a las 21:37:55 -05:00, con duración Maven de 8,399 segundos. Las pruebas terminaron a las 21:39:04 -05:00, con duración Maven de 11,155 segundos.

El primer intento de iniciar el proceso dentro del sandbox falló con `helper_unknown_error: setup refresh had errors`, antes de ejecutar Java/Maven. Se utilizó posteriormente la ejecución escalada autorizada. Esto no fue un fallo de compilación ni de pruebas.

Maven regeneró interfaces/modelos OpenAPI y otros artefactos bajo `backend/target/`; no se editaron manualmente. En el paso de pruebas se compilaron también las clases de test existentes, pero Surefire ejecutó solamente las dos seleccionadas.

Advertencias observadas: uso de API obsoleta en `SenaceTransport` y operaciones unchecked en su test histórico. No se modificaron esos archivos en este bloque.

## 7. Resultados verificables

| Suite ejecutada | Pruebas | Fallos | Errores | Omitidas | Tiempo Surefire |
|---|---:|---:|---:|---:|---:|
| `JsonFileSnapshotRepositoryTest` | 14 | 0 | 0 | 0 | 0,607 s |
| `SnapshotValidatorTest` | 8 | 0 | 0 | 0 | 0,245 s |
| **Total de esta ejecución** | **22** | **0** | **0** | **0** | — |

Evidencias locales, regenerables y no permanentes:

- `backend/target/surefire-reports/com.ecoinvest.soa.snapshot.JsonFileSnapshotRepositoryTest.txt`
- `backend/target/surefire-reports/com.ecoinvest.soa.snapshot.SnapshotValidatorTest.txt`

Escenarios ejecutados: claves no autorizadas; esquema/tipos/ID inválidos; identidad económica exacta; optionalidad; duplicados y fechas originales; precisión decimal tras almacenamiento; copia defensiva; integridad y manipulación; lectura ausente; primera copia vacía; rechazo de actualización parcial/inválida/vacía sobre copia no vacía; envejecimiento y vencimiento; corrupción JSON y claves duplicadas; reconstrucción del repositorio; propietario exclusivo; cierre; respuestas concurrentes; límite de escritura; temporal huérfano; configuración Spring desactivada; política temporal inválida; fallos inyectados de publicación y movimiento atómico no soportado.

La persistencia se comprobó cerrando y reconstruyendo el repositorio sobre el mismo directorio temporal. **No se reinició un proceso JVM ni un contexto completo Spring Boot.**

Los errores de publicación y de soporte de movimiento atómico se inyectaron mediante el punto de extensión de prueba. No se provocaron cortes eléctricos, disco lleno real ni fallos físicos de almacenamiento.

El bloqueo se comprobó entre dos instancias dentro de una JVM; no se ensayaron dos procesos independientes. La prueba concurrente usa dos tareas y verifica el resultado final, no constituye un stress test prolongado.

## 8. Aislamiento y límites de seguridad

Las pruebas usan solamente datos artificiales y directorios `@TempDir` de JUnit. El único contexto Spring utilizado en estas suites registra la configuración opt-in desactivada, sin autoconfiguración Boot ni escaneo de persistencia. No se invocan clientes externos ni se crean DataSource/EntityManager en estas pruebas.

No se accedió a bases SQLite ni se inició el backend completo. No hubo tráfico oficial. Esta evidencia se apoya en la configuración y el código de las pruebas ejecutadas; no equivale a una captura de sistema operativo de todas las llamadas de archivos/red.

El directorio y sus ancestros deben estar bajo control del operador. Las comprobaciones de enlaces no proporcionan aislamiento frente a un actor con permisos para sustituir rutas concurrentemente. No se ejecutaron pruebas nativas de symlinks/junctions ni ACL Windows. El repositorio no crea permisos administrativos ni endurece automáticamente ACL.

`force(true)` y movimiento atómico no certifican recuperación frente a pérdida eléctrica ni garantizan fsync del directorio en todos los sistemas. El bloqueo está pensado para un propietario cooperante por directorio, no para despliegue distribuido con múltiples réplicas.

La serialización y validación mantienen estructuras/copia del payload en memoria; el límite de bytes de archivo no constituye un límite total del heap. Falta medir datasets grandes y el consumo agregado antes de habilitarlo en un backend con memoria limitada.

## 9. Persistencia en Render y despliegue

El almacenamiento del filesystem de Render Free es efímero y el plan gratuito no admite discos persistentes. Por tanto, escribir JSON dentro del contenedor actual **no garantiza conservarlos entre reinicios, suspensión o redeploys**.

La primera versión es utilizable sobre un directorio local conservado o volumen persistente adecuado. Para satisfacer durabilidad en nube se necesita una decisión separada: servicio con disco persistente o almacenamiento externo apropiado. No se cambiaron planes, recursos, Dockerfiles ni servicios remotos.

Referencia oficial consultada durante el diseño: [Render — Free instances](https://render.com/docs/free).

## 10. Pendientes de integración y aceptación

1. Aprobar la política temporal y el tratamiento de respuestas vacías antes de habilitar la coordinación.
2. Diseñar/autorizar coordinadores para capturar los DTO SENACE completos antes del filtrado y la observación BCRP exacta después de su validación actual.
3. Resolver fallback por errores normales y excepcionales, incluido circuito abierto, preservando las métricas y políticas Resilience4j. Ninguno de estos flujos fue probado en este bloque.
4. Mantener una respuesta API válida aunque falle la escritura de disco; informar el problema de almacenamiento por separado.
5. Revalidar el snapshot y su edad antes de usarlo; no servir estados `INVALID`, `EXPIRED`, `MISSING` o errores como datos actuales.
6. Autorizar cambios de `ResultadoConsultaSenace`, controlador y OpenAPI antes de exponer `RESPALDO_LOCAL`. Actualmente los gates públicos aceptan exclusivamente procedencia directa para datos disponibles.
7. Adaptar después tipos/adaptadores/vistas React, headers de Consultoras y Monitor para fecha, procedencia, integridad y causa segura del fallback. No cambiar `NO_IMPLEMENTADO` anticipadamente.
8. Probar reinicio de contexto/proceso, coordinación con AOP real y fallos de proveedor mediante transportes artificiales, sin solicitudes oficiales.
9. Acordar almacenamiento persistente de despliegue y límites operativos de memoria, directorio, permisos y único propietario.

Los cuatro endpoints permanecen intactos: `/api/dashboard`, `/api/consultoras`, `/api/senace/datastream` y `/api/estado-servicios`. No se añadió respaldo para los otros seis datastreams heredados.

## 11. Estado final

**Bloque 1 implementado y validado en su alcance aislado.** El almacenamiento conserva datos entre instancias del repositorio sobre el mismo directorio; no se ha completado el objetivo global de fallback automático persistente de EcoInvest.

No se actualizaron el índice documental ni reportes históricos. El siguiente paso es revisar este informe y autorizar el diseño/archivos del bloque de coordinación, sin activación pública implícita.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest conserva su backend Java 17/Spring Boot 3.2.4 y frontend existente. Se añadieron siete clases en `backend/src/main/java/com/ecoinvest/soa/snapshot/`, dos tests en el paquete equivalente de pruebas y exclusiones locales en `.gitignore`. El repositorio JSON limita claves a dos recursos SENACE y seis series BCRP con año exacto; valida lectura/escritura, fechas, integridad y contenido; utiliza temporales, reemplazo atómico y bloqueo de propietario. TTL explícitos y configuración opt-in desactivada por defecto.

El 11/11/2026 se ejecutaron compilación y dos suites con Maven offline y Oracle JDK 17.0.12: **BUILD SUCCESS, 22 pruebas, cero fallos/errores/omisiones**. Las pruebas son artificiales; no hubo consultas oficiales, credenciales, SQLite ni aplicación completa. Reinicio significa reconstrucción del repositorio en la misma JVM, y los fallos de publicación fueron inyectados.

Todavía no se modificaron clientes, mapper, resultado SENACE, controlador, Resilience4j, OpenAPI, Monitor ni React. No hay fallback LKG operativo ni despliegue nuevo. Falta decidir antigüedad/arrays vacíos, implementar coordinadores preservando métricas, probar integración con transportes controlados y autorizar metadatos públicos/frontend. Render Free no proporciona la durabilidad de archivos requerida; se necesita almacenamiento persistente separado antes de prometer conservación en nube.
