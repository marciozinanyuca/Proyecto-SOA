# EcoInvest — Fase 3B.2, Lote 3: integración de SenaceClient

**Fecha:** 03/11/2026, America/Lima.  
**Estado:** implementación compilada y validada mediante transporte simulado y AOP aislado.  
**Runtime:** Oracle JDK 17.0.12; Spring Boot 3.2.4; Maven Wrapper 3.9.9.  
**Resultado:** BUILD SUCCESS; 228 casos contabilizados por Surefire: **219 satisfactorios, 9 omitidos, 0 fallos y 0 errores**.  
**Pendiente:** Lote 4, integración pública del controlador y OpenAPI. No iniciado.  
**Alcance de esta creación documental:** únicamente este archivo, autorizado después de comunicar los resultados.

## 1. Objetivo y estado inicial

Integrar el transporte del Lote 2, los DTOs oficiales y el mapper del Lote 1 en SenaceClient. Sustituir el procesamiento fijo por deserialización de respuestas SENACE y mapeo de sus campos, manteniendo compilación con los consumidores actuales sin modificar todavía controlador o contrato.

La implementación puede procesar respuestas del transporte productivo, pero **en este lote no se consultó SENACE real**. Los valores de todos los tests son artificiales y la fuente HTTP se sustituyó por mocks. El origen API_DIRECTA_SENACE observado en tests describe el comportamiento esperado del código, no una nueva observación oficial.

Referencias de continuidad:

- [AGENTS.md](../../AGENTS.md).
- [Decisiones D01–D19 aprobadas](REPORTE_DECISIONES_FASE3B1_SENACE.md).
- [Diseño Fase 3B.1](REPORTE_DISENO_FASE3B1_SENACE.md).
- [Validación Fase 3A y actualización del 02/11/2026](REPORTE_FASE3A_VALIDACION_SENACE.md).
- [Implementación del Lote 1](REPORTE_IMPLEMENTACION_FASE3B2_LOTE1.md).
- [Implementación del Lote 2](REPORTE_IMPLEMENTACION_FASE3B2_LOTE2.md).
- [Cierre técnico BCRP Fase 2C](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md).

### Situación anterior a este lote

| Componente | Estado previo confirmado |
|---|---|
| Lote 1 | DTOs, ResultadoConsultaSenace y SenaceMapper implementados; 53 pruebas previas documentadas |
| Lote 2 | Transporte Apache específico, configuración y aislamiento del RestTemplate; 71 pruebas previas documentadas |
| SenaceClient | RestTemplate, lectura Map.class, rutas /data.json y construcción de registros SIMULADO incluso tras HTTP satisfactorio |
| Fallbacks SENACE | Proyectos y consultoras simulados; genérico NO_DISPONIBLE |
| Controller | Consumo de List<Map<String,Object>> y procedencia calculada desde primera fila; vacío etiquetado SIMULADO |
| OpenAPI | Cuatro GET existentes; descripciones históricas de simulación SENACE |
| BCRP | Fase 2C cerrada en su alcance técnico; fuera de este encargo |

Los totales reales anteriores de CarteraProyectos y ConsultorasAmbientales son evidencias históricas, no constantes del cliente. No se codificaron 2.567 o 15.015 como tamaños esperados.

## 2. Decisiones aprobadas aplicadas

Las reglas D01–D19 se mantuvieron. No se modificaron fórmulas, catálogos económicos, semántica temporal o reglas de identidad/vigencia.

| Grupo | Aplicación en este lote |
|---|---|
| D01–D04 | Fechas originales, año de inicio, dd/MM/uuuu estricto y default 2017 mediante SenaceMapper |
| D05 | Actividad original y clave normalizada sin equivalencias nuevas con sectores BCRP |
| D06 | Conteos del resultado filtrado; no proyectos únicos certificados |
| D07–D09 | Consultoras sin deduplicación ni RUC/registro/nivel/estado o vigencia inventados |
| D10 | Propiedades normalizadas ausentes omitidas en los adaptadores; DTO original conservado |
| D11 | Eliminación del fallback simulado automático y de las listas fijas del cliente |
| D12–D13 | Firmas y raíces públicas conservadas; API interna tipada para que el Lote 4 consuma metadatos independientes |
| D14–D15 | Credencial administrada solo por el transporte/configuración; default histórico retirado del cliente; sin nueva importación local |
| D16–D17 | Transporte único SENACE; límites, timeout, TLS, redirecciones y reintentos sin cambios de política |
| D18–D19 | Rechazo de respuesta estructuralmente inválida y calidad temporal explícita |

### Ampliación de alcance aprobada antes de editar

La inspección demostró que dos archivos no eran suficientes:

1. ResultadoConsultaSenace no distinguía configuración ausente/inválida y circuito abierto mediante motivos específicos.
2. SenaceTransport solo admitía los dos recursos verificados y exigía array para todos. Preservar seis recursos heredados sin un segundo transporte requería ampliar su lista explícita y diferenciar la raíz esperada.
3. Era necesario probar esa separación en SenaceTransportTest.

Se presentó una propuesta de cinco archivos y el usuario respondió «si». La misma aprobación incluyó preparar una prueba AOP mínima dentro del test existente y la transición de errores de los adaptadores antiguos.

También se explicó y aprobó una limitación temporal: sin modificar el controlador, los errores de los adaptadores pueden producir HTTP 500 y las listas vacías siguen etiquetadas SIMULADO. No se presenta este comportamiento como implementación de la política pública final 502/503 o degradación del dashboard.

La autorización de editar fue independiente de la posterior autorización Maven, y de la creación de este reporte.

## 3. Archivos modificados y revisión de alcance

Se modificaron exclusivamente estos cinco archivos durante la implementación del Lote 3:

| Archivo | Cambio |
|---|---|
| [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java) | Transporte inyectado, Jackson local, DTOs/mapper, resultados tipados, adaptadores y fallbacks seguros |
| [SenaceClientTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java) | Reemplazo de expectativas de simulación por fixtures, mocks, seguridad, compatibilidad y AOP real aislado |
| [ResultadoConsultaSenace.java](../../backend/src/main/java/com/ecoinvest/soa/client/ResultadoConsultaSenace.java) | Extensión aditiva de Motivo, sin cambiar componentes del record o sus estados |
| [SenaceTransport.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceTransport.java) | Seis recursos heredados, nombres/rutas y raíz JSON esperada por recurso |
| [SenaceTransportTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceTransportTest.java) | Doce casos nuevos de recursos heredados: seis rutas/objetos y seis rechazos de arrays |

No se creó otra capa Service, transporte, repositorio, entidad o archivo auxiliar. Los dos archivos de transporte ya existían como trabajo del Lote 2 aunque Git todavía los mostraba sin seguimiento.

Se registraron hashes SHA-256 de 16 archivos protegidos y permanecieron iguales tras implementación y Maven. Incluyen los DTOs, mapper, controlador, contrato, configuración, AppConfig, POM y otros archivos previos fuera del alcance. Los cambios preexistentes del Lote 2 no se descartaron ni se atribuyen nuevamente al Lote 3.

git diff --check no encontró problemas. Las advertencias LF/CRLF de Git corresponden a normalización de finales de línea; no se hizo un formateo general. No se hicieron commits ni cambios de rama.

Este reporte se creó después con permiso específico. No se actualizaron README, reportes anteriores, skills o AGENTS.md.

## 4. Cambios en SenaceClient

### 4.1. Constructor y dependencias

En SenaceClient.java, líneas 42–61:

```java
public SenaceClient(SenaceTransport transport, ObjectMapper objectMapper)
```

El cliente ya no recibe RestTemplate ni contiene propiedades de credencial. Se retiraron senaceAuthKey, el default histórico y la construcción local de URI autenticada. La autenticación y el transporte quedan en SenaceTransport, sin leer el archivo local de configuración.

SenaceMapper sigue siendo puro y se reutiliza directamente. El ObjectMapper inyectado se copia antes de configurarlo; no se altera el compartido por Spring o BCRP.

### 4.2. Métodos tipados nuevos

```java
ResultadoConsultaSenace<ProyectoMapeado> consultarProyectos(String sector, Integer anio);
ResultadoConsultaSenace<ConsultoraMapeada> consultarConsultoras();
```

Se encuentran en las líneas 65–73 y están preparados para el controlador del Lote 4. Conservan filas y metadatos juntos, incluso cuando no existe primera fila.

Una llamada por un bean proxy Spring obtiene resultados tipados de ausencia mediante fallback. Una instancia construida directamente sin aspecto puede lanzar ConsultaSenaceException en los caminos de error: las pruebas unitarias inspeccionan esa excepción segura y las pruebas AOP demuestran el comportamiento con proxy. No atribuir interceptación a un simple new SenaceClient(...).

### 4.3. Métodos existentes conservados

```java
List<Map<String, Object>> obtenerProyectos(String sector, Integer anio);
List<Map<String, Object>> obtenerProyectos();
List<Map<String, Object>> obtenerConsultoras();
Map<String, Object> consultarDatastream(String nombre);
```

Estos métodos conservan los tipos requeridos por EcoInvestController. Los adaptadores de listas son transitorios, no el contrato definitivo de metadatos.

Proyectos conserva valores obtenidos de los trece campos y alias compatibles titulo/proyecto y sector/actividad. El año es el derivado por el mapper. No se añaden inversión, moneda, región, expediente o iniciales artificiales.

Consultoras expone los ocho conceptos observados con nombres internos: razonSocial, actividad, subsector, tipoSolicitud, resolucion, fechaRd, inicioVigenciaRd y finVigenciaRd. No reconstruye los campos ficticios antiguos. Cada fila adaptada usa la procedencia del resultado de consulta, no una inferencia desde otra fila.

Los adaptadores omiten valores normalizados null. Las fechas originales y los demás textos se conservan según el mapper. No se usa la ausencia como cero o string fijo.

### 4.4. Retirada de simulación

Se eliminaron los constructores de proyectos/consultoras fijos y los filtros sobre esas listas. No existe fallback automático SIMULADO en SenaceClient.

El valor SIMULADO permanece en el enum histórico de ResultadoConsultaSenace como representación posible del modelo, pero el nuevo cliente no lo utiliza. El controlador sigue conteniendo la etiqueta incorrecta de vacío, fuera del alcance del lote.

## 5. Integración con transporte y mapper

### 5.1. Flujo tipado

```text
Entrada publica protegida por CircuitBreaker
  -> funcion privada de consulta
  -> SenaceTransport.consultar(recurso)
  -> verificacion de estado, HTTP 200 y cuerpo completo
  -> Jackson local: raiz array y DTOs tipados
  -> SenaceMapper
  -> ResultadoConsultaSenace con evidencia HTTP trasladada
```

Las funciones privadas compartidas evitan duplicar lógica o depender de una autoinvocación entre métodos anotados. El método sin argumentos aplica el default del mapper directamente, sin llamar al otro método público protegido.

### 5.2. Condiciones previas al parseo

recibir, desde la línea 165, exige RECIBIDO, HTTP 200, cuerpoCompleto true y cuerpo no null antes de deserializar datos.

Un cuerpo que parece JSON válido pero está marcado incompleto no se procesa. Un resultado null, un cuerpo ausente o una excepción inesperada del transporte no se convierten en respuesta oficial vacía.

### 5.3. Deserialización defensiva

La copia local de Jackson:

- Rechaza documentos adicionales mediante FAIL_ON_TRAILING_TOKENS.
- Rechaza coerción de escalares a listas y de arrays unitarios a escalares.
- No convierte floats a enteros.
- No convierte números o booleanos en campos textuales, incluido RUC.
- No convierte strings en coordenadas decimales, incluidas cadenas vacías.
- Conserva BigDecimal y BigInteger para números de los objetos genéricos heredados.

El parser comprueba la raíz antes de leer el tipo correspondiente. Las excepciones de Jackson se sustituyen por error seguro, sin causa o mensaje con campos/cuerpo externo.

Se conserva el deserializador existente del ID Long: no se modificaron sus reglas ni se impuso positividad adicional. Los campos desconocidos de los DTOs siguen sujetos a su configuración previa de ignorarlos; no se declara que un esquema futuro entero quede certificado por ello.

### 5.4. Mapeo y calidad

Se reutilizan sin cambios las reglas del Lote 1:

- dd/MM/uuuu estricto y FECHA_INICIO como año de inicio, no aprobación.
- Default 2017, actividad original y normalización solo de búsqueda.
- Exclusión temporal explícita y conteo de fechas no evaluables.
- Conteos de registros, no identidad certificada de proyectos.
- Coordenadas BigDecimal con validación de par/rango; valores originales conservados y sin ceros sustitutos.
- Consultoras sin deduplicación o cálculo de vigencia.

Una fila de proyecto sin ID o una fila null invalida el resultado completo. Un fallo de tipos durante deserialización tampoco publica el prefijo ya leído. Las consultoras no reciben una clave empresarial o campo obligatorio nuevo sin evidencia; un objeto con propiedades opcionales ausentes no se rechaza por inventar requisitos de negocio.

## 6. Manejo de estados, errores y procedencia

### 6.1. Resultado interno

ResultadoConsultaSenace conserva sus componentes y enums EstadoConsulta, EstadoFiltrado, Origen y Cobertura. Solo se añadieron motivos técnicos seguros, desde la línea 26 del archivo.

Se mantienen las distinciones:

| Situación | Estado y metadatos |
|---|---|
| Datos válidos con coincidencias | EXITO, API_DIRECTA_SENACE, conteos del mapper |
| Array oficial vacío | VACIA, API_DIRECTA_SENACE, recibidos 0 y motivo RESPUESTA_OFICIAL_VACIA |
| Datos oficiales sin coincidencias | EXITO, SIN_COINCIDENCIAS; recibidos conocido, devueltos 0 |
| Fechas relevantes no evaluables | EstadoFiltrado NO_EVALUABLE y motivo FECHAS_NO_EVALUABLES, sin asignar un año ficticio |
| Error/configuración ausente | ERROR_EXTERNO, NO_DISPONIBLE, motivo específico y conteos desconocidos |
| Formato incompatible | FORMATO_INVALIDO, NO_DISPONIBLE, sin filas |
| Cuerpo cortado o límite | PARCIAL, NO_DISPONIBLE, sin prefijo publicable |
| Circuito abierto | ERROR_EXTERNO, NO_DISPONIBLE, motivo CIRCUITO_ABIERTO, sin nueva llamada al transporte |

La cobertura permanece NO_VERIFICADA. HTTP completo no equivale a cobertura histórica, territorial o empresarial completa.

### 6.2. Traducción de fallos del transporte

| Estado del transporte | Traducción principal |
|---|---|
| CONFIGURACION_AUSENTE / CONFIGURACION_INVALIDA | ERROR_EXTERNO y motivo homónimo |
| DESTINO_INVALIDO / RECURSO_INVALIDO | ERROR_EXTERNO y motivo homónimo |
| REDIRECCION_RECHAZADA | ERROR_EXTERNO con motivo específico |
| ERROR_HTTP / ERROR_TLS | ERROR_EXTERNO con distinción por motivo |
| ERROR_TRANSPORTE | ERROR_EXTERNO |
| TIMEOUT_CONEXION / TIMEOUT_INACTIVIDAD / PLAZO_TOTAL_AGOTADO | Motivo específico; PARCIAL si existen bytes de cuerpo incompleto, o ERROR_EXTERNO en caso contrario |
| LIMITE_TAMANO | PARCIAL, LIMITE_LECTURA |
| RESPUESTA_INCOMPLETA | PARCIAL, RESPUESTA_PARCIAL |
| HTML_INESPERADO / JSON_INVALIDO / ESTRUCTURA_INVALIDA | FORMATO_INVALIDO |
| CODIFICACION_NO_ADMITIDA | FORMATO_INVALIDO y motivo específico |
| INTERRUMPIDO | Motivo específico; estado según exista cuerpo parcial |

Los errores no producen conteos oficiales cero. Si una lista ya se deserializó y el mapper rechazó su estructura, recibidos puede conocerse sin que aceptados/devueltos sean conocidos.

### 6.3. Evidencia de cuerpo completo

El cliente copia cuerpoCompleto del transporte al resultado mapeado. Sin respuesta HTTP o bytes de cuerpo, la ausencia puede conservar null para no inventar evidencia. Un error HTTP con cuerpo completo conserva true aunque el contenido no sea publicable; una lectura parcial conserva false.

El mapper por sí solo no acredita recepción HTTP. No se deduce procedencia de la primera fila.

El modelo tipado no recibió campos nuevos para código HTTP, Content-Type o cantidad de bytes: conserva categorías mediante Motivo y cuerpoCompleto. Los detalles HTTP originales existen en RespuestaTransporte durante la consulta, pero no se afirma que todos se propaguen a ResultadoConsultaSenace. Ampliar esa observabilidad posteriormente requiere alcance explícito.

### 6.4. Seguridad

ConsultaSenaceException, desde la línea 343, contiene un resultado de error sin filas y un mensaje fijo con motivo. No conserva causa externa, cuerpo, URL o credencial. Los fallbacks registran únicamente motivos de enum, sin Throwable adjunto.

Un nombre de datastream no reconocido no se refleja íntegramente en el fallback: se utiliza NO_RECONOCIDO. No se pasan rutas arbitrarias al transporte.

No se leyó o modificó backend/.env.apis.local ni el .env de la raíz. No se implementó otra importación de configuración. Los niveles de logging seguros y las variables explícitas del Lote 2 permanecieron intactos.

## 7. Compatibilidad con controlador y Resilience4j

### 7.1. Compatibilidad compilable

EcoInvestController compila con las firmas existentes y conserva:

- GET /api/dashboard.
- GET /api/estado-servicios.
- GET /api/consultoras.
- GET /api/senace/datastream.

No se modificó su código ni OpenAPI. Las pruebas actuales del controlador siguen usando mocks de los clientes y validan compatibilidad de rutas y serialización dentro de esa frontera. No prueban la nueva integración real cliente–controlador.

### 7.2. Limitaciones transitorias aprobadas

Los métodos antiguos de listas propagan una excepción segura en caso de fallo, incluso desde sus fallbacks, para evitar devolver un vacío que el controlador convertiría en ceros. Al no existir todavía el manejo público final, esos caminos pueden producir HTTP 500.

El dashboard llama primero a SENACE; si esa llamada falla, el controlador actual no continúa hasta obtener y devolver BCRP. Por tanto, todavía no implementa la degradación aprobada de conservar BCRP disponible ante fallo SENACE. El componente BCRP permanece intacto, pero la orquestación pública está pendiente.

Si SENACE devuelve vacío oficial o el filtro no encuentra coincidencias, el adaptador conserva la lista vacía; el controlador actual sigue asignando SIMULADO a esa lista. El resultado tipado sí conserva la procedencia correcta. El Lote 4 debe consumir ese resultado directamente.

Los metadatos de los métodos tipados son la solución preparada, no el uso definitivo de los adaptadores. No se incorporaron ThreadLocal, cachés o almacenamiento de la última consulta para intentar recuperar metadatos después de otra llamada.

Las descripciones OpenAPI que todavía mencionan simulación SENACE quedaron históricamente desactualizadas tras este lote y deben corregirse en el Lote 4, no se modificaron aquí.

### 7.3. Métodos protegidos y fallbacks

Todos utilizan el breaker existente senace. No se modificaron configuración, umbrales, nombres o políticas.

| Método protegido | Fallback | Resultado en fallo |
|---|---|---|
| consultarProyectos(String,Integer) | fallbackConsultaProyectos(String,Integer,Throwable) | ResultadoConsultaSenace de ausencia |
| consultarConsultoras() | fallbackConsultaConsultoras(Throwable) | ResultadoConsultaSenace de ausencia |
| obtenerProyectos(String,Integer) | fallbackSenace(String,Integer,Throwable) | Excepción segura, sin lista simulada |
| obtenerProyectos() | fallbackSenaceSimple(Throwable) | Excepción segura |
| obtenerConsultoras() | fallbackConsultoras(Throwable) | Excepción segura |
| consultarDatastream(String) | fallbackDatastreamGenerico(String,Throwable) | Objeto NO_DISPONIBLE con metadatos |

Los fallbacks tienen los argumentos originales más Throwable y retorno compatible con el método protegido. Los métodos públicos no finales son interceptables.

Los errores estructurales o de transporte se convierten en excepción interna segura para que el aspecto registre el fallo. Esto incluye configuración ausente: la métrica refleja un fallo lógico de la llamada, no prueba una petición HTTP ni la indisponibilidad externa del proveedor. El monitor debe seguir distinguiendo estas interpretaciones.

### 7.4. Evidencia AOP ejecutada

Los tests usan ApplicationContextRunner con únicamente AopAutoConfiguration y CircuitBreakerAutoConfiguration, más tres beans explícitos: cliente, ObjectMapper y transporte mock. Cargan propiedades del application.yml para usar el breaker configurado, sin component scanning o autoconfiguración general de Spring Boot.

Se acreditó:

- Existencia de un proxy AOP real del cliente.
- Cinco llamadas satisfactorias con cinco eventos, sin doble conteo por autoinvocación.
- Seis variantes de métodos/fallbacks: errores registrados y retorno o excepción conforme al adaptador.
- Apertura del circuito tras el mínimo efectivo de la ventana real y rechazo posterior sin otra llamada al transporte.
- Ausencia de beans DataSource en los contextos comprobados.

La apertura se obtuvo mediante fallos reales del flujo con respuestas mock, no forzando el estado manualmente. No se probó recuperación temporal SENACE en este lote ni se reabrió la validación BCRP.

Referencias: SenaceClientTest.java, líneas 429, 454, 477 y 496.

## 8. Endpoint genérico y recursos heredados

### 8.1. Ocho recursos conservados

El enum Recurso del transporte, líneas 75–87, conserva una lista cerrada:

| Recurso | Ruta relativa | Raíz esperada | Evidencia externa previa |
|---|---|---|---|
| CarteraProyectos | /Api/datastreams/CarteraProyectos | Array | Verificada en Fase 3A |
| ConsultorasAmbientales | /Api/datastreams/ConsultorasAmbientales | Array | Verificada en Fase 3A |
| GastoEspecifica | /Api/datastreams/GastoEspecifica/data.json | Objeto | No verificada; compatibilidad heredada |
| GastoFuente | /Api/datastreams/GastoFuente/data.json | Objeto | No verificada; compatibilidad heredada |
| GastoGenerica | /Api/datastreams/GastoGenerica/data.json | Objeto | No verificada; compatibilidad heredada |
| SolicitudAcceso | /Api/datastreams/SolicitudAcceso/data.json | Objeto | No verificada; compatibilidad heredada |
| Visitas | /Api/datastreams/Visitas/data.json | Objeto | No verificada; compatibilidad heredada |
| Reclamos | /Api/datastreams/Reclamos/data.json | Objeto | No verificada; compatibilidad heredada |

Para los seis últimos se preservó la ruta y expectativa de objeto del camino anterior. No se aplicó por hipótesis la ruta sin /data.json o el esquema de proyectos/consultoras. Su incorporación al mismo transporte extiende los controles de seguridad existentes sin crear otro cliente HTTP.

Desconocidos se rechazan sin HTTP. Se aceptan nombres conocidos con comparación insensible a mayúsculas y espacios exteriores; nombre ausente/blanco conserva el default CarteraProyectos. No se introdujo descubrimiento o prueba automática de recursos.

### 8.2. Forma de respuesta genérica

Se mantiene el objeto envolvente y sus claves datastream, origen y data cuando hay respuesta aceptada. Se agregan consulta y recursoValidado:

- consulta contiene estado, origen, filtrado, cobertura, cuerpoCompleto cuando se conoce, motivo, conteos y advertencias.
- recursoValidado describe el recurso/ruta/esquema observado en Fase 3A, no disponibilidad actual o cobertura completa.
- El fallback mantiene NO_DISPONIBLE, mensaje seguro y los recursos soportados; agrega metadatos sin exponer cuerpos de error.

Para proyectos, el genérico valida las filas mediante mapearProyecto y conserva todas las filas, sin aplicar el default anual 2017. Para consultoras utiliza mapearConsultoras y conserva duplicados.

data de esos dos recursos contiene DTOs con los nombres oficiales Jackson, no la lista filtrada del dashboard. Se conservan los trece u ocho campos conocidos y originales; las propiedades desconocidas siguen ignoradas por el DTO y no se promete passthrough íntegro de columnas futuras.

Para los seis heredados, data es un objeto opaco con estructura conservada y números de precisión adecuada. No se aplican métricas, identidades, filtros o semántica de los otros datasets. Los conteos permanecen desconocidos porque no existe una regla validada para sus filas.

### 8.3. Limitaciones heredadas

Sintaxis JSON y raíz objeto no acreditan éxito semántico, autenticación o cobertura de un recurso no investigado. No existe todavía un esquema oficial verificado que permita reconocer todos los posibles errores administrativos encapsulados en HTTP 200 de esos seis recursos. No deben anunciarse como integración oficial validada.

No se efectuaron consultas externas para resolver esa incertidumbre. La política pública de códigos HTTP del genérico permanece pendiente del Lote 4; el controlador antiguo continúa envolviendo su resultado con HTTP 200 cuando recibe un objeto de fallback.

## 9. Pruebas realmente ejecutadas

### 9.1. Directorio y comandos

Directorio:

```text
D:\Documentos\GitHub\Proyecto-SOA\backend
```

Compilación autorizada, con entorno temporal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests compile
```

Pruebas autorizadas en un proceso con el mismo JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -o '-Dtest=SenaceClientTest,SenaceMapperTest,SenaceTransportTest,EcoInvestControllerTest' '-Decoinvest.test.local-http=false' test
```

No se persistió JAVA_HOME/PATH en el sistema. Java y Maven confirmaron Oracle 17.0.12 y Maven 3.9.9. Las propiedades seleccionadas de Surefire confirmaron java.version=17.0.12, java.home=C:\Program Files\Java\jdk-17 y ecoinvest.test.local-http=false.

Maven trabajó offline. Regeneró OpenAPI y compiló 15 fuentes con release 17, así como siete fuentes de tests. Solo se ejecutó la selección indicada; compilar un test no equivale a ejecutarlo. No hubo edición manual de target/.

### 9.2. Fechas y resultados

| Ejecución | Resultado | Finalización registrada | Duración Maven |
|---|---|---|---|
| compile | BUILD SUCCESS, salida 0 | 03/11/2026 22:19:59 -05:00 | 38,651 s |
| test seleccionado | BUILD SUCCESS, salida 0 | 03/11/2026 22:20:56 -05:00 | 43,820 s |

La primera compilación y la primera selección de pruebas terminaron satisfactoriamente. No hubo fallos Maven seguidos de cambios para hacer pasar esta ejecución.

### 9.3. Desglose actual, sin sumar ejecuciones históricas

| Clase/grupo | Satisfactorios | Omitidos | Fallos | Errores |
|---|---:|---:|---:|---:|
| SenaceClientTest | 83 | 0 | 0 | 0 |
| SenaceMapperTest | 53 | 0 | 0 | 0 |
| SenaceTransportTest, sin HTTP | 73 | 0 | 0 | 0 |
| SenaceTransportTest.LocalHttp | 0 | 9 | 0 | 0 |
| EcoInvestControllerTest | 10 | 0 | 0 | 0 |
| **Total** | **219** | **9** | **0** | **0** |

Surefire contabiliza 228 casos porque incluye los nueve omitidos. No describirlos como 228 pruebas superadas. Las 53 pruebas del mapper sí se ejecutaron nuevamente en esta selección; las 71 históricas del Lote 2 no se suman como casos adicionales.

El XML de transporte agrupa 82 casos bajo el reporte de la clase anidada: 73 de la clase exterior y nueve omitidos de LocalHttp. El XML exterior declara cero. Se revisó classname de los testcase para evitar duplicarlos o atribuirles HTTP que no realizaron.

### 9.4. Cobertura comprobada

Los 83 casos del cliente incluyen parametrizaciones y verifican:

- Arrays válidos, vacíos, opcionales, strings vacíos y raíces/filas incompatibles.
- ID sin pérdida de precisión, rechazo de tipos/coerciones y RUC textual.
- Filtros, default anual, fechas no evaluables y separación de vacío oficial frente a selección sin coincidencias.
- Coordenadas precisas y omisión del par normalizado inválido sin alterar originales.
- Consultoras sin deduplicación o vigencia inventada.
- Propagación de cuerpo completo/incompleto y rechazo de JSON aparentemente válido pero cortado.
- Categorías de transporte, configuración ausente y conteos desconocidos en errores.
- Fallbacks sin simulación, firmas compatibles y error seguro en adaptadores antiguos.
- Genérico sin filtro anual para proyectos, filas de consultoras y objetos heredados de seis recursos.
- Copia local de Jackson y ausencia de credenciales/cuerpos en mensajes, causas o logs comprobados.
- Proxy Spring AOP, fallbacks, conteo único y rechazo por circuito abierto.

Los 73 casos de transporte son los 61 anteriores sin sockets más doce añadidos para recursos heredados. Los nueve tests HTTP locales permanecieron desactivados en esta ejecución; no se inició un servidor temporal.

Las diez pruebas del controlador son MockMvc con dependencias simuladas. Mantienen la comprobación de las cuatro rutas, pero no certifican el nuevo cliente real conectado al controlador.

### 9.5. Artefactos

- [SenaceClientTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceClientTest.xml).
- [SenaceMapperTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceMapperTest.xml).
- [Transporte y grupo local XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceTransportTest$LocalHttp.xml).
- [Transporte exterior XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceTransportTest.xml).
- [EcoInvestControllerTest XML](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.controller.EcoInvestControllerTest.xml).

Los artefactos son volátiles. Los XML pueden contener propiedades de entorno: no compartirlos completos sin revisión. Este reporte conserva los resultados y valores de runtime pertinentes.

## 10. Resultados y limitaciones

### Resultado técnico

SenaceClient compila junto al controlador y contrato actuales, utiliza un único transporte SENACE y procesa DTOs/mapeo bajo pruebas. La interceptación de Resilience4j quedó demostrada en el contexto mínimo descrito, no inferida desde anotaciones o llamadas directas a fallbacks.

Las advertencias observadas no impidieron BUILD SUCCESS:

- API obsoleta en SenaceTransport, ya advertida en el Lote 2. No se ejecutó una compilación adicional con Xlint para identificar el símbolo exacto.
- Operaciones genéricas no comprobadas en SenaceTransportTest.
- Aviso de class sharing de la JVM al ampliar bootstrap classpath durante tests.
- Aviso habitual de OpenAPI Generator sobre postprocesamiento Java no configurado.

No se instalaron herramientas, procesadores de formato o dependencias para resolver esos avisos.

### SQLite y ejecución aislada

No se inició Spring Boot completo ni una conexión SQLite. Los contextos AOP no habilitan autoconfiguración de datasource/JPA y se comprobaron sin beans DataSource. MockMvc no arranca un servidor real.

Solo se inspeccionaron metadatos de backend/ecoinvest.db: 0 bytes y LastWriteTime 12/10/2026 22:19:49, anterior al lote. No se abrieron tablas o archivos de base de datos. Esto no prueba persistencia operativa ni fallback SQLite implementado.

### Límites pendientes

1. No hubo consulta SENACE real, validación de credencial vigente, TLS real o comprobación de disponibilidad externa en este lote.
2. No se ejecutó integración end-to-end ni la orquestación real completa del dashboard.
3. Los adaptadores antiguos conservan compilabilidad, no metadatos públicos definitivos. Persisten los 500 transitorios y la etiqueta de vacío del controlador.
4. Los otros seis recursos siguen sin esquema/semántica oficialmente verificados; conservar la ruta heredada no acredita que funcione hoy.
5. No se probó recuperación temporal SENACE, carga concurrente o bloqueo DNS nativo. Se conservan los límites documentados del transporte en el Lote 2.
6. No se implementó carga del archivo local .env.apis.local. Las variables deben llegar al proceso Java o suministrarse como propiedades equivalentes.
7. No se expusieron todos los metadatos HTTP del transporte en el modelo tipado. Las categorías y completitud no equivalen a conservar cada código HTTP/cabecera.
8. La documentación pública OpenAPI todavía debe reflejar la retirada de simulación y la política de errores final.

## 11. Estado final del Lote 3

| Criterio | Estado confirmado |
|---|---|
| Integración transporte–cliente–DTO–mapper | Implementada y probada con fixtures |
| Reglas aprobadas del Lote 1 | Reutilizadas; 53 casos actuales satisfactorios |
| Ausencia y procedencia independientes de filas | Disponibles en métodos tipados |
| Simulación automática y default de clave en cliente | Retirados |
| Recursos heredados | Conservados con raíz/ruta anteriores y sin declaración de validación oficial |
| Firmas actuales del controlador | Compatibles en compilación y mocks |
| Aspecto Resilience4j | Interceptación, fallbacks y rechazo comprobados con transporte mock |
| Archivos fuera del alcance | 16 hashes protegidos sin cambios |
| Compilación y selección de tests | BUILD SUCCESS; 219 satisfactorios y nueve omitidos |
| Política pública y end-to-end | Pendientes del Lote 4 y validaciones posteriores |

El Lote 3 queda completado en su alcance aislado de implementación y pruebas. No constituye el cierre de toda la integración SENACE ni de Fase 3B.2.

## 12. Pendientes del Lote 4

Antes de implementar, el siguiente encargo debe inspeccionar y aprobar los archivos y la política pública aplicable. Candidatos principales:

- [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java).
- [openapi.yaml](../../backend/src/main/resources/openapi.yaml).
- [EcoInvestControllerTest.java](../../backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java).
- SenaceClient y ResultadoConsultaSenace como dependencias a leer; cualquier modificación adicional necesita alcance explícito.

Trabajos propuestos:

1. Sustituir en el controlador los adaptadores por consultarProyectos y consultarConsultoras, sin realizar consultas duplicadas para obtener filas y metadatos.
2. Conservar BCRP disponible ante error SENACE, omitiendo métricas SENACE desconocidas según la política aprobada.
3. Retirar la inferencia de procedencia desde la primera fila y la etiqueta SIMULADO de listas vacías.
4. Mantener las cuatro rutas y sus raíces: dashboard objeto, consultoras array, genérico objeto y monitor objeto.
5. Añadir metadatos de dashboard y headers de consultoras con exposición CORS concreta, sin ampliar otras políticas incidentalmente.
6. Aplicar códigos HTTP y errores conforme a las decisiones: vacío oficial 200; indisponibilidad/configuración de consultoras 503; formato inválido 502; dashboard degradado con evidencia explícita.
7. Definir en contrato cómo se presentan parcialidad, calidad temporal, opcionales, cobertura NO_VERIFICADA y motivos.
8. Corregir las descripciones OpenAPI históricas de simulación y generar modelos únicamente mediante Maven autorizado.
9. Mantener el monitor de circuit breakers sin consultas externas y aclarar que sus métricas son llamadas lógicas, no pruebas directas de disponibilidad.
10. Ampliar pruebas MockMvc de datos tipados, errores, ausencia, headers y compatibilidad; no afirmar end-to-end por esos mocks.

No modificar BCRP, catálogo, políticas Resilience4j, SQLite o frontend por implicación. No empezar nuevas consultas oficiales para validar recursos heredados sin autorización independiente.

## 13. Siguiente paso recomendado

Compartir este reporte con ChatGPT y solicitar un prompt exclusivo del Lote 4. Debe partir de los dos métodos tipados existentes y de las decisiones aprobadas, sin recrear DTOs, mapper o transporte.

El primer paso del Lote 4 será revisar el contrato y proponer cambios concretos del controlador/OpenAPI/tests, incluida la degradación del dashboard. Solicitar autorización antes de editar y antes de ejecutar Maven, indicando JDK 17.0.12, modo offline y efectos en target/.

Las validaciones reales del proveedor y end-to-end deben permanecer como tareas separadas con presupuestos y aislamiento explícitos. No abrir SQLite o iniciar la aplicación completa accidentalmente para comprobar un cambio de contrato.

Durante la creación de este reporte no se volvieron a ejecutar pruebas, Maven o HTTP ni se modificó código productivo. El índice documental y los antecedentes permanecen intactos.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó Fase 3B.2 Lote 3: SenaceClient utiliza SenaceTransport, los DTOs oficiales y SenaceMapper existentes. Se mantuvieron Java 17/Oracle JDK 17.0.12 y Spring Boot 3.2.4. BCRP Fase 2C continúa cerrado; no se modificaron BCRP, controlador, OpenAPI, configuración, SQLite o frontend en este lote.

Se modificaron cinco archivos autorizados: SenaceClient.java, SenaceClientTest.java, ResultadoConsultaSenace.java, SenaceTransport.java y SenaceTransportTest.java. El resultado interno solo recibió motivos aditivos para configuración, circuito y transporte; no cambió su constructor o estados. No se creó otra estrategia HTTP o nueva capa Service.

El cliente ofrece consultarProyectos(String,Integer) y consultarConsultoras() con ResultadoConsultaSenace tipado. Conserva obtenerProyectos(String,Integer), obtenerProyectos(), obtenerConsultoras() y consultarDatastream(String) para que compile el controlador antiguo. Los dos métodos tipados conservan procedencia, conteos, calidad y cuerpoCompleto desde el transporte. Un error produce ausencia mediante fallback al atravesar el proxy; una instancia directa sin AOP puede lanzar ConsultaSenaceException, que no incluye causa, URI o cuerpo externo.

Se retiraron las listas simuladas y el default de clave de SenaceClient. Se deserializan arrays completos con Jackson privado, sin modificar el mapper compartido; RUC permanece texto, ID y coordenadas mantienen precisión y tipos incompatibles se rechazan. Las reglas de fecha/actividad/default 2017 y calidad son las aprobadas y siguen en SenaceMapper. No se inventan inversión, región, expediente, RUC de consultoras o vigencia.

El genérico mantiene ocho recursos y objeto envolvente datastream/origen/data, con metadatos aditivos. Proyectos/consultoras usan rutas verificadas sin /data.json y raíz array; proyectos genérico no aplica filtro anual. Los otros seis conservan /data.json y raíz objeto del camino heredado, con recursoValidado=false, sin validar su semántica ni efectuar consultas reales. Todos usan el transporte acotado existente; límites y políticas no se alteraron.

Se conservó el breaker senace. El contexto mínimo real de Spring AOP, con transporte mock y sin DataSource, verificó proxy, fallbacks compatibles, ausencia de doble conteo y rechazo por circuito abierto sin otra llamada al transporte. Los fallos de configuración también cuentan como fallos lógicos; las métricas no certifican disponibilidad externa. No se probó recuperación temporal SENACE.

Ejecuciones acreditadas del 03/11/2026: compile offline BUILD SUCCESS a las 22:19:59 -05:00; pruebas seleccionadas BUILD SUCCESS a las 22:20:56. Surefire contabilizó 228 casos: 219 satisfactorios y nueve omitidos, cero fallos/errores. Desglose aprobado actual: 83 cliente, 53 mapper, 73 transporte sin red y diez controlador; nueve LocalHttp desactivados mediante ecoinvest.test.local-http=false. No sumar las 71 pruebas históricas del Lote 2 a este total. Java y Surefire confirmaron Oracle 17.0.12. Los 16 archivos protegidos conservaron hashes.

No hubo HTTP real, servidor de pruebas, Spring Boot completo, conexión SQLite, instalación de dependencias o commits. Las diez pruebas del controlador usan mocks y no acreditan integración con el cliente real. Persisten advertencias no fatales de API obsoleta y genéricos en el transporte/tests.

Límite público importante: los adaptadores antiguos propagan errores seguros, que pueden producir 500 hasta el Lote 4. El dashboard aún no continúa a BCRP si falla primero SENACE y sigue etiquetando listas vacías como SIMULADO. La política 200 degradado/502/503 y los metadatos públicos todavía no están implementados. El contrato conserva descripciones históricas que requieren actualización.

Siguiente prompt recomendado: Lote 4, inspección y propuesta para EcoInvestController.java, openapi.yaml y EcoInvestControllerTest.java. Usar los métodos tipados, conservar cuatro rutas/raíces, procedencia independiente, métricas honestas, degradación con BCRP disponible, headers/CORS de consultoras y errores aprobados. Solicitar archivos y ejecuciones concretas. No reimplementar lotes previos, no cambiar BCRP/Resilience4j/SQLite y no declarar funcionamiento end-to-end sin su validación independiente.
