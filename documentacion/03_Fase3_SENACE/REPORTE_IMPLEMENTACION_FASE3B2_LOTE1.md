# EcoInvest — Implementación Fase 3B.2, Lote 1: modelos y mapeo puro SENACE

**Fecha:** 2 de noviembre de 2026.  
**Estado:** implementado, compilado y validado mediante pruebas unitarias específicas.  
**Resultado:** compilación BUILD SUCCESS; 53 casos, cero fallos, cero errores y cero omitidos.  
**Runtime de Maven y pruebas:** Oracle JDK 17.0.12.  
**Alcance:** cinco archivos nuevos, desacoplados del cliente y controlador existentes.

## 1. Objetivo del lote

Preparar DTOs externos, contenedor interno y mapeador determinista de las respuestas oficiales SENACE para los lotes posteriores, sin modificar endpoints, transporte actual, configuración, OpenAPI o persistencia.

Se aplicaron las decisiones D01–D19 del [reporte de decisiones](REPORTE_DECISIONES_FASE3B1_SENACE.md) en lo correspondiente al mapeo. No se implementan aquí las decisiones de transporte, HTTP o configuración asignadas a lotes posteriores.

Hubo autorizaciones separadas para crear los cinco archivos, ejecutar Maven offline y crear este reporte. No se instalaron dependencias, hicieron consultas HTTP, inició Spring Boot o realizaron commits. No se leyeron credenciales ni se abrió SQLite.

## 2. Estado inicial y compatibilidad conservada

Se leyeron AGENTS.md, las skills pertinentes y los reportes de [diseño](REPORTE_DISENO_FASE3B1_SENACE.md), decisiones y [validación SENACE](REPORTE_FASE3A_VALIDACION_SENACE.md), priorizando su actualización del 02/11.

Los cinco destinos no existían antes de comenzar. El repositorio conservaba cambios de fases previas, que no se revirtieron. SenaceClient todavía esperaba Map.class y construía listas SIMULADO; ese comportamiento sigue igual porque este lote solo añade componentes preparados para integración futura.

Los cuatro endpoints mantienen su código, firmas y contrato actuales. No se modificaron SenaceClient, EcoInvestController, BcrpClient, Resilience4j, AppConfig, OpenAPI, application.yml, SQLite o frontend. No se añadió una capa Service ni dependencia nueva.

La Fase 2C BCRP permanece cerrada dentro de su alcance. La creación del mapper no supone integración SENACE productiva ni una nueva validación end-to-end.

## 3. Archivos creados

| Archivo | Contenido |
|---|---|
| [ProyectoSenaceDto.java](../../backend/src/main/java/com/ecoinvest/soa/client/dto/ProyectoSenaceDto.java) | Record externo con trece propiedades Jackson y deserializador de ID entero |
| [ConsultoraSenaceDto.java](../../backend/src/main/java/com/ecoinvest/soa/client/dto/ConsultoraSenaceDto.java) | Record externo con ocho propiedades oficiales |
| [ResultadoConsultaSenace.java](../../backend/src/main/java/com/ecoinvest/soa/client/ResultadoConsultaSenace.java) | Resultado genérico, estados, motivos, advertencias y conteos conocidos/desconocidos |
| [SenaceMapper.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceMapper.java) | Funciones puras, tipos mapeados anidados, fechas, actividad, coordenadas y selección |
| [SenaceMapperTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceMapperTest.java) | 21 métodos de prueba, incluidos parametrizados que producen 53 casos ejecutados |

Los tipos auxiliares se alojan dentro de los archivos aprobados, sin clases o fixtures adicionales. Este reporte se creó después con autorización independiente; el índice documental no se modificó.

## 4. Diseño de DTOs y mapper

### 4.1. DTO de proyectos

ProyectoSenaceDto es un record inmutable con correspondencia explícita @JsonProperty:

| Campo oficial | Componente interno | Tipo |
|---|---|---|
| ID | id | Long |
| TITULAR | titular | String |
| RUC | ruc | String |
| TITULO_PROYECTO | tituloProyecto | String |
| UNIDAD_PROYECTO | unidadProyecto | String |
| TIPO | tipo | String |
| ACTIVIDAD | actividad | String |
| FECHA_INICIO | fechaInicio | String |
| ESTADO | estado | String |
| DESCRIPCION | descripcion | String |
| LONGITUD | longitud | BigDecimal |
| LATITUD | latitud | BigDecimal |
| RESOLUCION | resolucion | String |

El deserializador anidado de ID acepta solo un token JSON entero representable como Long. Rechaza fracciones, notación de token flotante, strings numéricos, booleanos, arrays y desbordamientos sin truncar. ID ausente/null puede existir en el DTO de transporte y es rechazado explícitamente al mapear.

No se impuso positividad al identificador porque no estaba acreditada como regla. El DTO no depende de modelos generados o Spring. Las propiedades desconocidas se ignoran mediante Jackson, sin convertirlas en campos de negocio inventados; la validación de raíz y transporte corresponde al cliente futuro.

### 4.2. DTO de consultoras

ConsultoraSenaceDto contiene exactamente RAZON_SOCIAL, ACTIVIDAD, SUBSECTOR, TIPO_SOLICITUD, RESOLUCION, FECHA_RD, INICIO_VIGENCIA_RD y FIN_VIGENCIA_RD, todos String y con @JsonProperty explícito. No incluye RUC, registro, nivel, estado o identificador ficticios.

Las fechas se conservan como texto: no se derivó vigencia ni se aplicaron reglas temporales de proyectos a consultoras.

### 4.3. Firmas del mapeador

```java
ProyectoMapeado mapearProyecto(ProyectoSenaceDto origen);
ConsultoraMapeada mapearConsultora(ConsultoraSenaceDto origen);

ResultadoConsultaSenace<ProyectoMapeado> mapearProyectos(
    List<ProyectoSenaceDto> registros, String actividad, Integer anio);

ResultadoConsultaSenace<ConsultoraMapeada> mapearConsultoras(
    List<ConsultoraSenaceDto> registros);

String normalizarActividad(String actividad);
```

ProyectoMapeado, ConsultoraMapeada y EstadoFecha son tipos anidados de SenaceMapper. Conservan referencia al DTO original inmutable y propiedades normalizadas separadas. No tienen semántica de entidad persistida o respuesta HTTP pública.

SenaceMapper no tiene Spring Context, acceso a archivos/configuración, transporte, SQL, reloj o identificadores aleatorios. No se conecta al cliente actual.

## 5. Reglas de transformación aplicadas

### Originales, opcionales y precisión

- Los DTOs preservan originales; los campos normalizados opcionales convierten null y strings vacíos/en blanco en ausencia.
- Un string no vacío conserva su contenido exacto, sin recortar títulos, nombres o resoluciones.
- Actividad y FECHA_INICIO se conservan también directamente como texto original en el proyecto mapeado.
- RUC permanece texto, incluidos ceros iniciales en los fixtures artificiales.
- Coordenadas mantienen BigDecimal; no hay conversión intermedia a double en el mapeo.
- No existen propiedades de inversión, región, moneda o expediente inventadas.

### Fechas y selección anual

Se utiliza dd/MM/uuuu con ResolverStyle.STRICT y formato textual de dos dígitos para día/mes y cuatro para año. No se infiere cultura ni se corrige una fecha imposible. Una fecha ambigua como 05/04/2017 se interpreta como 5 de abril conforme a la decisión aprobada.

EstadoFecha distingue VALIDA, AUSENTE e INVALIDA. La fecha original permanece disponible; la normalizada y anioInicio solo existen si el parseo es válido. Se emiten advertencias seguras FECHA_AUSENTE o FECHA_INVALIDA sin propagar mensajes con el texto recibido.

mapearProyectos aplica 2017 cuando anio es null. No equivale a año de aprobación. Si actividad es null/blanca o TODOS, no se limita por actividad; el filtro de año sigue aplicándose.

Las fechas no evaluables no toman el año solicitado. Se contabilizan globalmente y para la actividad seleccionada; estas últimas producen estadoFiltrado NO_EVALUABLE y motivo FECHAS_NO_EVALUABLES, incluso si hay una lista de coincidencias evaluables. NO_EVALUABLE significa que al menos una fecha relevante no pudo evaluarse, no necesariamente que toda la selección sea inútil.

### Actividad y conteos

La clave de comparación normaliza espacios, mayúsculas mediante Locale.ROOT y acentos. Conserva el original y no traduce Energía a Electricidad ni otras actividades a sectores distintos.

Los conteos describen registros recibidos, aceptados estructuralmente, devueltos por filtros y aprobados de esa lista. Aprobado se compara sin distinguir mayúsculas, sin una nueva reclasificación de los demás estados. No se calculan proyectos únicos ni cobertura oficial.

### Coordenadas

Se validan pares y límites globales bajo supuesto documentado de grados: latitud [-90,90], longitud [-180,180]. Una pareja incompleta o fuera de rango se omite en propiedades normalizadas y produce advertencia, preservando valores originales en el DTO. Una pareja ausente permanece ausente. Un cero realmente recibido y dentro de rango se conserva, sin fabricarlo para una ausencia.

Esta validación no prueba CRS, datum, precisión geográfica, ubicación Perú o región.

### Estructura y filas

mapearProyecto rechaza fila nula o ID ausente con mensajes fijos. En colecciones, una fila estructuralmente inválida produce FORMATO_INVALIDO y no publica un prefijo válido como si fuera respuesta completa. Un input de lista null se distingue de una lista oficial vacía.

En consultoras se exige fila no nula, sin imponer una clave empresarial inexistente o convertir razón social en identificador. Se conserva el orden y las filas repetidas. No se deduplica ni deriva un estado administrativo.

## 6. Resultado de consulta y metadatos

ResultadoConsultaSenace<T> contiene lista inmutable, origen, estado de consulta, estado de filtrado, cobertura, cuerpoCompleto opcional, motivo, conteos y advertencias inmutables.

| Dimensión | Valores disponibles |
|---|---|
| Origen | API_DIRECTA_SENACE, NO_DISPONIBLE, SIMULADO |
| EstadoConsulta | EXITO, VACIA, ERROR_EXTERNO, FORMATO_INVALIDO, PARCIAL |
| EstadoFiltrado | APLICADO, SIN_COINCIDENCIAS, NO_EVALUABLE, NO_APLICABLE |
| Cobertura | NO_VERIFICADA |

El enum SIMULADO permite representar explícitamente ese origen, pero el mapper no crea datos simulados ni lo utiliza como fallback automático. Los tests que ejercitan ese origen son artificiales y no implementan un nuevo modo demo productivo.

Conteos usa Integer opcional: null significa desconocido o no aplicable, no cero. Se validan no negatividad, relaciones entre conteos y correspondencia de devueltos con la lista cuando ese dato se conoce.

Ejemplos distinguibles:

- Array oficial vacío: API_DIRECTA_SENACE, VACIA, recibidos 0.
- Lista no vacía sin coincidencias de filtros: EXITO, SIN_COINCIDENCIAS y recibidos conocido.
- Lista ausente o fila inválida: FORMATO_INVALIDO, NO_DISPONIBLE, sin publicación de filas.
- Error externo: estado específico y conteos desconocidos.
- Respuesta parcial: PARCIAL, sin filas, cuerpoCompleto false y conteos desconocidos.

En resultados del mapper, cuerpoCompleto es null: mapear una lista no demuestra recepción HTTP. Del mismo modo, el origen oficial representa la procedencia declarada por el flujo al que se destina el componente, no evidencia de una conexión real ejecutada por el mapper.

## 7. Pruebas ejecutadas y resultados

### 7.1. Entorno y comandos autorizados

Directorio: D:\Documentos\GitHub\Proyecto-SOA\backend.

En procesos temporales:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests compile
```

Luego:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -o '-Dtest=SenaceMapperTest' test
```

Se utilizaron cachés locales con acceso aprobado fuera del sandbox. No se instalaron dependencias. La configuración JAVA_HOME/PATH no fue persistida en el sistema.

### 7.2. Resultados acreditados

| Operación | Resultado | Fecha/hora registrada |
|---|---|---|
| Generación OpenAPI y compilación | BUILD SUCCESS, 14 fuentes compiladas con release 17 | 02/11/2026 22:09:23 -05:00 |
| Prueba específica | BUILD SUCCESS; 53 casos, 0 fallos, 0 errores, 0 omitidos | 02/11/2026 22:10:01 -05:00 |

java -version y Maven confirmaron Oracle 17.0.12. El XML Surefire confirma java.version=17.0.12 y java.home=C:\Program Files\Java\jdk-17. No fue necesario corregir código tras un fallo de compilación o de estas pruebas.

Maven regeneró interfaces/modelos y otros artefactos bajo target/, sin editar manualmente esos archivos ni cambiar el contrato fuente. La selección -Dtest ejecutó únicamente SenaceMapperTest; no se repitió toda la suite histórica ni las consultas reales BCRP/SENACE.

### 7.3. Cobertura del test

Los 21 métodos de prueba incluyen parámetros y producen 53 casos:

- Trece campos de proyectos y ocho de consultoras con Jackson explícito.
- ID Long máximo, rechazo de fracciones, strings/coerciones y desbordamientos.
- Ausencia de una restricción de positividad no aprobada.
- RUC textual y decimales de alta precisión.
- Campos opcionales, null y strings vacíos sin pérdida del DTO original.
- Fechas válidas, bisiestas, imposibles, ambiguas y no evaluables.
- Normalización de actividad sin alterar originales o traducir sectores.
- Año predeterminado, selección por año/actividad y conteos de aprobados.
- Diferencia entre proveedor vacío, selección sin coincidencias, error y parcial.
- Coordenadas en límites, cero realmente recibido, parejas ausentes/incompletas y fuera de rango.
- Consultoras repetidas preservadas y ausencia de reglas de vigencia.
- Metadatos independientes de filas, copia defensiva e invariantes del contenedor.

Los fixtures son artificiales y están identificados como tales en el test. No contienen filas reales, credenciales o cuerpos completos del proveedor.

Evidencias generadas:

- [Resumen Surefire](../../backend/target/surefire-reports/com.ecoinvest.soa.client.SenaceMapperTest.txt).
- [XML Surefire](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.SenaceMapperTest.xml).

Estos archivos son volátiles y pueden desaparecer al limpiar target/. No adjuntar un XML completo sin revisar sus propiedades de entorno; este reporte registra solo los valores pertinentes.

## 8. Revisión de alcance, SQLite y limitaciones

Se compararon hashes SHA-256 de 40 archivos previos de código, tests, documentación, skills, POM, wrapper, gitignore y script diagnóstico, sin leer archivos de credenciales. Permanecieron iguales tras implementación y ejecuciones autorizadas.

Se comprobó que solo se añadieron los cinco archivos de fuente/test previstos bajo backend/src. git diff --check no señaló errores. Los nuevos archivos permanecen sin seguimiento, junto con cambios anteriores del repositorio; no se hicieron commits.

Las pruebas seleccionadas no tienen Spring Context, HTTP, SQL o lectura de configuración. No se inició la aplicación y no se abrió SQLite. Solo se consultaron metadatos del archivo backend/ecoinvest.db: cero bytes y última modificación 12/10/2026 22:19:49, anterior a la ejecución del lote. No se interpretó ese archivo como respaldo operativo ni se inspeccionaron tablas.

Limitaciones importantes:

1. El transporte, lectura acotada y configuración todavía no existen en estos componentes: corresponden al Lote 2.
2. SenaceClient y EcoInvestController siguen en su implementación anterior; los endpoints aún no utilizan el mapper ni sus metadatos.
3. Los resultados no son modelos públicos ni una política HTTP implementada. Omisión JSON de opcionales y headers se decidirán en el contrato del lote correspondiente.
4. La precisión y estructura fueron comprobadas con fixtures pequeños; no se descargaron o probaron datasets reales aquí.
5. La fecha estricta es una decisión aprobada de interpretación, no confirmación contractual universal del proveedor.
6. No se certificaron cobertura geográfica, estabilidad global de ID, identidad empresarial o vigencia administrativa.
7. No se probó integración AOP nueva: no se modificaron métodos protegidos o fallbacks existentes.
8. No se verificó end-to-end ni compatibilidad de un nuevo contrato público, porque este lote no lo cambia.

## 9. Estado final del Lote 1

| Criterio | Estado |
|---|---|
| Cinco archivos autorizados creados | Cumplido |
| Correspondencia de 13/8 campos oficiales | Implementada y probada con fixtures |
| Mapeo puro, originales y precisión | Implementado y probado |
| Fechas, actividad, opcionales y coordenadas | Implementados conforme a reglas aprobadas en el alcance descrito |
| Resultado independiente de filas | Implementado y probado |
| Compilación Oracle JDK 17.0.12 | BUILD SUCCESS |
| Pruebas específicas | 53 casos satisfactorios |
| Archivos existentes y endpoints intactos | Sin cambios comprobados en archivos previos |
| Integración con proveedor/cliente/controlador | Pendiente de lotes posteriores |

El Lote 1 está completado dentro de su alcance. Su existencia no retira todavía las listas SIMULADO del cliente productivo.

## 10. Cambios pendientes del Lote 2

Según el plan aprobado, el Lote 2 propondrá:

- backend/src/main/java/com/ecoinvest/soa/client/SenaceTransport.java.
- backend/src/test/java/com/ecoinvest/soa/client/SenaceTransportTest.java.
- Cambios limitados a configuración SENACE en backend/src/main/resources/application.yml, solo bajo autorización concreta.

El transporte deberá aislar BCRP y el RestTemplate compartido, consumir rutas oficiales aprobadas, rechazar redirecciones autenticadas y aplicar lectura máxima real de 6 MiB, conexión 5 s, inactividad 10 s y plazo HTTP total 25 s, incluidos errores. No introducir reintentos o dependencias nuevas por implicación.

La configuración debe mapear explícitamente SENACE_BASE_URL/SENACE_AUTH_KEY, sin default de credencial y con degradación segura cuando falte, sin impedir servir BCRP. La importación local opcional del archivo ignorado no está implementada; debe concretarse antes de modificar configuración, sin leer valores para diseñarla.

El nuevo transporte podrá utilizar ResultadoConsultaSenace para ausencia y errores, pero deberá distinguir evidencia HTTP real de metadatos producidos por el mapper. No considerar una lista mapeada como prueba de cuerpo recibido completo.

No se inició el Lote 2 mediante este reporte ni se autorizan implícitamente sus archivos, tests, servidores locales o consultas.

## 11. Siguiente paso recomendado

Compartir este reporte con ChatGPT para preparar un prompt exclusivo del Lote 2. Ese prompt debe partir de los cinco componentes existentes, no recrearlos, e inspeccionar sus firmas reales antes de diseñar transporte/configuración.

Solicitar primero un plan con archivos exactos, estrategia de timeout/límite, configuración local/nube, manejo de errores y pruebas simuladas sin SQLite. Aprobar los cambios y las ejecuciones por separado según las restricciones vigentes. No conectar todavía cliente/controlador ni avanzar simultáneamente a lotes 3 y 4.

No actualizar por asociación README, estados históricos o skills; cualquier actualización adicional necesita su alcance autorizado. El usuario prefiere respuestas breves con detalle técnico en los reportes.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó Fase 3B.2 Lote 1: cinco archivos nuevos para modelos y mapeo puro SENACE, sin modificar cliente, controlador, contrato o configuración. DTOs externos record con @JsonProperty de 13 campos de proyectos y 8 de consultoras; proyectos usa Long ID validado sin fracciones/desbordamientos, RUC/fechas String y coordenadas BigDecimal. Consultoras no inventa RUC/ID/registro/nivel/estado ni deriva vigencia.

SenaceMapper es puro y expone mapearProyecto, mapearConsultora, mapearProyectos(lista,actividad,anio), mapearConsultoras y normalizarActividad. Sus tipos anidados conservan DTO original y propiedades normalizadas. Aplica dd/MM/uuuu estricto, año de inicio y default 2017; actividad original más clave normalizada; estructuras esenciales inválidas rechazan el resultado completo; fecha inválida es calidad explícita y se contabiliza al filtrar. Coordenadas incompletas/fuera de rango se omiten en la pareja normalizada con advertencia, sin ceros ficticios. Se conservan filas de consultoras repetidas.

ResultadoConsultaSenace<T> mantiene origen, estados de consulta/filtrado, cobertura NO_VERIFICADA, cuerpoCompleto desconocido en el mapper, conteos opcionales y advertencias, con listas inmutables. Distingue oficial vacío, sin coincidencias, error, formato inválido, parcial y origen SIMULADO explícito, sin activar una simulación automática.

Se ejecutaron comandos Maven Wrapper offline con Oracle JDK 17.0.12: compile con skipTests finalizó BUILD SUCCESS el 02/11/2026 22:09:23 -05:00; SenaceMapperTest finalizó BUILD SUCCESS a las 22:10:01 con 53 casos, cero fallos/errores/omitidos. Surefire confirma Java 17.0.12. No hubo HTTP, Spring Context o acceso SQLite. Se preservaron hashes de 40 archivos previos; solo se añadieron los cinco archivos autorizados, además de este reporte aprobado después. No se repitió la suite completa histórica ni se hicieron commits.

El cliente productivo sigue esperando Map y construyendo SIMULADO; endpoints sin cambio. BCRP Fase 2C cerrada y Resilience4j/SQLite/frontend intactos. El siguiente prompt debe diseñar y solicitar autorización del Lote 2 (transporte/configuración y tests), reutilizando los modelos existentes y respetando límite streaming 6 MiB, conexión 5 s, inactividad 10 s, plazo total HTTP 25 s, sin redirecciones/reintentos o nuevas dependencias implícitas. No iniciar lotes 3/4 ni declarar integración end-to-end.
