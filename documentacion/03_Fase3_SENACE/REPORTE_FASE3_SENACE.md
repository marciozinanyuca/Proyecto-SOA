# EcoInvest — Cierre parcial de Fase 2C y diagnóstico de Fase 3 SENACE

**Fecha:** 18 de octubre de 2026.  
**Proyecto:** EcoInvest, plataforma de inteligencia gubernamental SENACE + BCRP.  
**Estado:** BCRP parcialmente validado mediante ejecución real; integración real SENACE pendiente de validación oficial y adaptación.

Este documento se creó con autorización expresa posterior a presentar el diagnóstico. Durante la inspección y documentación no se modificaron archivos productivos, dependencias, Java, Maven, Resilience4j, SQLite o frontend. No se ejecutaron Maven, consultas HTTP o nuevas pruebas, ni se realizaron commits. No se inició el servidor completo.

La migración a Java 21 queda pospuesta hasta la etapa final del proyecto. No se propone como requisito para la Fase 3.

## 1. Alcance y evidencias revisadas

Se revisaron las evidencias locales de la prueba real BCRP con JDK 17 y la integración SENACE existente:

- [Evidencia XML de Fase 2C](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientRealIT.xml).
- [Resultado textual de Fase 2C](../../backend/target/surefire-reports/com.ecoinvest.soa.client.BcrpClientRealIT.txt).
- [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java).
- [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java).
- [Contrato OpenAPI](../../backend/src/main/resources/openapi.yaml).
- [Configuración de aplicación](../../backend/src/main/resources/application.yml).
- [SenaceClientTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java).
- Pruebas y resultados existentes de `EcoInvestControllerTest`.

Las referencias de líneas corresponden al contenido inspeccionado. Los artefactos bajo target/ son generados y pueden desaparecer en un futuro clean. Por ello se conserva aquí el resumen de evidencias; no se presenta su existencia como una nueva ejecución.

## 2. Cierre parcial de la Fase 2C BCRP

### 2.1. Resultado de la ejecución real registrada

La JVM del test y Maven utilizaron **Oracle JDK 17.0.12**, instalado en:

```text
C:\Program Files\Java\jdk-17
```

La selección de JAVA_HOME y PATH se limitó al proceso temporal. La prueba comprobó el runtime y la ausencia de un truststore personalizado. Se mantuvo la validación HTTPS normal y no se importaron certificados.

| Año | Serie | HTTPS / HTTP | Cuerpo y resultado |
|---|---|---|---|
| 2017 | PM04974AA | TLS superado; HTTP 200 | JSON económico válido; período 2017; BigDecimal `-2.44780196733593`; origen API_DIRECTA_BCRP |
| 2018 | PM04974AA | TLS superado; HTTP 200 | HTML con referencias a `_Incapsula_Resource`; cliente devuelve NO_DISPONIBLE y FORMATO_INVALIDO |

En ambos casos se registró `Content-Type: text/html; charset=UTF-8`. En 2017 el cuerpo era JSON, mientras que en 2018 era HTML auténtico. La cabecera por sí sola no permite distinguir las respuestas.

Para 2017 se contrastó el resultado del cliente con el cuerpo HTTP capturado en memoria, sin mocks o cifras precargadas. Se verificaron código, descripción, año solicitado, período efectivo, unidad, BigDecimal y coherencia de los dos valores económicos.

La unidad fue **Variación porcentual interanual (%)**, con periodicidad ANUAL y ámbito SECTORIAL. Su denominación procede del catálogo aprobado y se contrastó la identidad económica con el nombre de serie recibido; el JSON no aporta una propiedad independiente de unidad.

### 2.2. Interpretación del fallo conjunto

El registro Surefire contiene **1 prueba ejecutada, 1 fallo, 0 errores y 0 omitidas**. La prueba intentó ambos años y falló por el cuerpo HTML de 2018, al contrastarlo como JSON. La compilación fue satisfactoria.

El resultado no es una nueva prueba de error TLS: para ambos años hubo respuesta HTTP sobre HTTPS validado. El cuerpo de 2018 es compatible con una capa de protección web, pero las referencias observadas no constituyen una identificación exhaustiva de su política o causa de activación.

El cliente productivo no publicó una cifra ficticia para 2018. Expresó la ausencia de forma segura como `NO_DISPONIBLE / FORMATO_INVALIDO`.

### 2.3. Matriz de verificación BCRP

| Aspecto | Estado y límite |
|---|---|
| Selección temporal, catálogo y precisión en pruebas locales | Verificados en Fase 2B con pruebas simuladas; no equivalen a disponibilidad externa |
| TLS con JDK 17 en los dos intentos reales | Verificado |
| Integración real PM04974AA / 2017 | Verificada para esa observación y esa ejecución |
| Rechazo seguro de HTML de 2018 | Verificado en ejecución real |
| Obtención económica real para 2018 mediante el cliente | Pendiente: el intento recibió HTML |
| Otras series y otros años con el cliente real | Pendientes; no extrapolar el éxito de 2017 |
| Interceptación AOP de Resilience4j | Pendiente; el cliente se instanció directamente |
| Funcionamiento end-to-end del dashboard o EcoInvest completo | No verificado |

**Cierre:** la Fase 2C permanece parcialmente validada. No debe declararse la integración BCRP completamente certificada.

### 2.4. Restricciones que se conservan

- Mantener el rechazo de HTML, JSON inválido y estructuras inesperadas.
- No sustituir ausencia por constantes, observaciones vecinas o datos simulados.
- No intentar eludir mecanismos de protección del proveedor.
- No introducir reintentos, cambios de headers o automatizaciones para sortear la protección como parte de esta fase.
- No modificar BCRP, Resilience4j o SQLite para iniciar el diagnóstico SENACE.

La fuente temporal de la prueba real fue eliminada al terminar. Los archivos protegidos y cacerts conservaron sus hashes. No se inició EcoInvest completo ni se accedió a SQLite.

## 3. Arquitectura SENACE existente

```text
Consumidor HTTP
  → ApiApi generada desde OpenAPI
  → EcoInvestController
      → SenaceClient
          → RestTemplate compartido
          → destino SENACE configurado
      → listas y metadatos públicos
```

No hay una capa Service o adaptador de DTOs SENACE propios. La consolidación del dashboard permanece en el controlador.

| Ruta EcoInvest | Método del cliente | Dataset declarado y comportamiento |
|---|---|---|
| GET /api/dashboard?sector=…&anio=… | obtenerProyectos | Consulta CarteraProyectos, pero retorna proyectos fijos filtrados |
| GET /api/consultoras | obtenerConsultoras | Consulta ConsultorasAmbientales, pero retorna consultoras fijas |
| GET /api/senace/datastream?nombre=… | consultarDatastream | Envuelve el cuerpo externo sin adaptar registros |
| GET /api/estado-servicios | Lectura del registro de circuit breakers | Métricas SENACE; no son un sondeo activo ni prueba de disponibilidad de la API |

Evidencia: cliente, líneas **51–102**; controlador, líneas **38–48 y 102–134**.

## 4. Endpoints, parámetros, formato y autenticación

### 4.1. Destino declarado en código

El cliente declara como base predeterminada:

```text
https://datosabiertos.senace.gob.pe/Api/datastreams
```

Construye peticiones con esta forma:

```text
GET {senaceBaseUrl}/{datastream}/data.json?auth_key=<REDACTADO>
```

La URL es una configuración encontrada en el repositorio. No se consultó el servicio ni su documentación en esta inspección; no se acredita todavía que esos nombres identifiquen recursos vigentes o que esa sea la interfaz oficial actual recomendada.

Evidencia: `SenaceClient.java`, líneas **22–27 y 105–109**.

### 4.2. Catálogo declarado

El código enumera ocho nombres:

```text
CarteraProyectos
ConsultorasAmbientales
GastoEspecifica
GastoFuente
GastoGenerica
SolicitudAcceso
Visitas
Reclamos
```

La lista está declarada en las líneas **29–39**; sus comentarios no sustituyen una validación oficial. La futura validación debe priorizar proyectos y consultoras. No es necesario consultar los ocho recursos para cumplir el alcance inicial.

El método genérico acepta un String libre y no lo contrasta con esta lista. Aunque el parámetro interno se llama datastreamGuid, no se comprobó que el proveedor admita estos nombres como identificadores o que requiera GUID reales.

### 4.3. Parámetros

| Parámetro | Comprobado en el repositorio | Pendiente externamente |
|---|---|---|
| sector | Query del dashboard; filtro local sobre registros fijos | Catálogo oficial, alias y posibilidad de filtrado remoto |
| anio | Query del dashboard; default 2017; filtro local | Fecha de negocio aplicable y filtros temporales admitidos |
| nombre | Query del endpoint genérico; default CarteraProyectos | Identificadores vigentes y valores admitidos |
| auth_key | Se añade a la query externa | Necesidad actual, obtención, validez, permisos y restricciones |
| Paginación o límite | No implementados | Sintaxis, valores máximos, orden y completitud |

Solo auth_key se incorpora actualmente a la query externa. Sector y año no se envían al proveedor. No se añaden headers explícitos de autenticación en el cliente inspeccionado.

### 4.4. Autenticación y secretos

La configuración usa las propiedades `external.apis.senace-base-url` y `external.apis.senace-auth-key`. No se inspeccionaron archivos secretos ni se verificó si un proceso dispone de una clave real válida. Los valores secretos no se reproducen en este documento.

La validación futura debe confirmar primero el mecanismo vigente. Si se necesita una credencial, debe obtenerse por un canal apropiado y no incorporarse a código, comandos visibles, reportes o fixtures. No deben usarse claves encontradas en ejemplos como si fueran credenciales autorizadas.

### 4.5. Formato esperado frente al real

El cliente ejecuta `restTemplate.getForObject(url, Map.class)`, líneas **113–119**. Esto espera un objeto JSON deserializable a Map; no hay DTO o parser del esquema externo.

No está comprobado si la respuesta real es objeto, array, envoltorio paginado, encabezados/filas o una estructura diferente. Tampoco su Content-Type, codificación, tipos, campos nulos o marcadores de ausencia. No se propone un parser basado en suposiciones.

El defecto Content-Type observado en BCRP no se atribuye automáticamente a SENACE. Debe comprobarse la respuesta SENACE antes de escoger un tratamiento local de texto/Jackson.

## 5. Limitaciones y problemas confirmados

### 5.1. Proyectos simulados tras una petición externa

`obtenerProyectos` solo comprueba que el mapa recibido no sea nulo o vacío. Después llama a `construirProyectosBase("SIMULADO")` y filtra esa lista.

Evidencia: cliente, líneas **52–62**. Los cuatro proyectos fijos se construyen desde la línea **170** y pertenecen a 2017, en hidrocarburos y minería.

**Consecuencia:** una petición externa satisfactoria no alimenta el dashboard con los registros recibidos. Cambiar de año no consulta otro período remoto; filtra los datos fijos y puede devolver vacío.

### 5.2. Consultoras simuladas

`obtenerConsultoras` también valida únicamente mapa no vacío y después devuelve cuatro consultoras fijas con origen SIMULADO.

Evidencia: líneas **75–84 y 230–269**.

**Consecuencia:** razón social, RUC, registro, sectores, nivel y vigencia no se actualizan desde el cuerpo externo.

### 5.3. Filtrado incompleto y semántica temporal sin definir

El filtro usa equalsIgnoreCase sobre el texto sectorial y compara anio con el valor del mapa. No normaliza acentos; `Minería` y `Mineria` pueden no coincidir. TODOS, sector nulo o vacío dejan pasar todos los registros.

Evidencia: líneas **157–167**.

Antes de filtrar datos reales debe definirse qué fecha determina el año: ingreso, resolución, certificación u otra. No debe inferirse automáticamente de una columna que parezca fecha ni reutilizar el año BCRP como regla de negocio SENACE.

### 5.4. Procedencia inferida desde los registros

El controlador usa el origen del primer proyecto y, cuando la lista está vacía, declara SIMULADO.

Evidencia: `EcoInvestController.java`, líneas **55–57**.

Esto refleja el estado provisional actual, pero no servirá para diferenciar una consulta oficial vacía, un error, una página incompleta o datos simulados. La procedencia debe viajar independientemente de la existencia de filas.

### 5.5. Endpoint genérico con validación insuficiente

El método envuelve cualquier respuesta retornada como `data` y origen API_DIRECTA_SENACE, incluso si el cuerpo es null. No valida esquema ni completitud.

Evidencia: cliente, líneas **92–102**. No se debe considerar este endpoint un adaptador terminado ni prueba de autenticidad de sus filas.

### 5.6. Errores y observabilidad segura

La sanitización evita propagar mensajes de RestClientException que podrían contener URLs autenticadas. Los logs propios no muestran esas URLs, y los fallbacks tampoco imprimen la excepción externa.

Evidencia: líneas **54, 77, 95, 113–119 y 131–149**.

La contrapartida es que no se conservan categorías diferenciadas de HTTP 401/403, 429, fallo de conexión o formato. Una mejora futura debería conservar estado/categoría seguros sin restaurar URLs, cuerpos o causas sensibles en los logs.

No se encontró tratamiento específico de HTML, JSON malformado, paginación o un mapa no vacío que solo contenga un mensaje de error.

### 5.7. Fallback, persistencia y resiliencia

- Fallback de proyectos y consultoras: registros simulados en memoria.
- Fallback genérico: NO_DISPONIBLE sin datos persistentes.
- No hay recuperación SQLite en estos métodos.
- Las anotaciones CircuitBreaker existen, pero las pruebas actuales no acreditan interceptación AOP.

Evidencia: líneas **127–150**. No se propone cambiar Resilience4j o implementar persistencia en este diagnóstico.

### 5.8. Restricciones externas aún desconocidas

No se verificaron límites de consultas, términos de uso, frecuencia de actualización, caducidad de credenciales, protección web, tamaño máximo de respuesta o políticas de acceso. Son preguntas pendientes, no restricciones confirmadas.

## 6. Mapeo de datos que debe diseñarse con evidencia oficial

Los registros fijos actuales definen la forma consumida por el backend. Eso no demuestra que todos sus campos existan en la fuente oficial.

| Campos actuales | Validación necesaria antes de mapearlos |
|---|---|
| id, expediente | Identificador oficial estable y criterio de unicidad; un RUC puede tener varios proyectos |
| titular, ruc | Campo oficial de administrado/titular; RUC como String, sin conversión numérica |
| titulo, proyecto | Correspondencia con denominación oficial; evitar duplicación ambigua |
| sector, actividad | Catálogo, alias y significado real; no copiar el catálogo económico BCRP como equivalente |
| fechaInicio, anio | Fecha de negocio acordada, formato, valores ausentes y regla anual |
| estado | Estados oficiales y mapeo que permita contar aprobados sin inventar equivalencias |
| inversion | Disponibilidad, moneda, unidad y significado; no usar cero como dato desconocido |
| latitud, longitud, region | Origen, sistema de referencia, cobertura y validez; no generar coordenadas ficticias |
| iniciales | Campo de presentación derivable del texto solo si se documenta como tal |
| origen | Procedencia efectiva independiente de si hay resultados |
| razón social, registro, nivel, sectores, vigencia de consultora | Campos reales, multivalores y fechas; no inferir actividad solo por aparecer en un listado |

Si un dato no está disponible, debe conservarse como ausencia explícita o campo omitido según el contrato acordado. No se deben completar campos reales con valores del prototipo sin identificar su carácter simulado.

El Explorador Geográfico requerirá coordenadas verificadas; la disponibilidad de registros de proyectos no garantiza cobertura geográfica suficiente para todos ellos.

## 7. Estado de pruebas y contrato

### 7.1. Evidencia existente

`SenaceClientTest.java` utiliza RestTemplate mockeado y cubre:

- Etiquetado SIMULADO de registros fijos después de una respuesta mock.
- Fallbacks simulados o no disponibles y filtro sin coincidencias.
- Envoltorio del datastream genérico.
- Ausencia de credencial ficticia y URL autenticada en logs y excepciones.

Los resultados existentes registran cuatro pruebas SENACE aprobadas. Los diez casos MVC existentes usan el cliente SENACE como mock. No se ejecutaron de nuevo para este diagnóstico y no demuestran conectividad, autenticación o parseo oficial.

### 7.2. Contrato vigente

OpenAPI documenta las cuatro rutas. Consultoras retorna un array de objetos libres y proyectos es un array de objetos sin propiedades definidas. fuentes es también genérico.

Evidencia: `openapi.yaml`, líneas **47–79 y 96–102**.

La descripción de consultoras documenta los registros fijos actuales. Cuando se implemente información oficial, deberá actualizarse el contrato para reflejar el comportamiento real y los campos verificados, manteniendo las rutas y la compatibilidad acordada.

## 8. Plan incremental de Fase 3

**Propuesta no ejecutada. Cada paso que incluya red, archivos auxiliares, implementación o Maven requiere la autorización correspondiente.**

### Etapa 3A — Validación oficial acotada

1. Consultar documentación oficial vigente, con autorización para acceso externo.
2. Confirmar interfaz, identificadores y autenticación de proyectos y consultoras.
3. Si el servicio lo permite, obtener muestras pequeñas mediante GET y límites documentados. No inventar parámetros de paginación o descargar automáticamente el dataset completo.
4. Registrar estado HTTP, Content-Type, estructura, cobertura temporal y actualización, sin credenciales o cuerpos sensibles.
5. Si se recibe protección web o acceso denegado, documentar la limitación y el canal oficial requerido; no eludirla.

**Aceptación:** fuente vigente y método autorizado identificados; muestras sanitizadas o limitación explícita. No basta con HTTP 200.

### Etapa 3B — Mapeo y reglas mínimas

Definir un diccionario de campos entre fuente y contrato, fecha que determina el año, catálogo sectorial, estados, identificadores, duplicados, completitud y ausencia. Separar proyectos de consultoras: no presuponer que compartan formato.

**Aceptación:** cada campo publicado tiene origen o derivación documentada. No se inventan moneda, inversión, coordenadas, aprobación o vigencia.

### Etapa 3C — Adaptar proyectos

Sustituir el constructor fijo en la ruta de datos oficiales por transformación validada. Mantener sector y año compatibles con la API pública. Solo filtrar localmente si se dispone del universo necesario o se declara explícitamente el alcance parcial.

El resultado interno debe transportar filas, procedencia y estado de completitud de manera independiente. La forma exacta se acordará antes de implementar, evitando una refactorización amplia.

**Aceptación:** modificar un registro en una fixture cambia el registro público correspondiente; los proyectos no proceden de construirProyectosBase en la ruta oficial. Una lista vacía válida mantiene su procedencia real.

### Etapa 3D — Adaptar consultoras

Transformar el recurso oficial y definir vigencia, sectores y registro con sus reglas verificadas. Conservar la ruta `/api/consultoras` y el array público, salvo evolución expresamente acordada.

**Aceptación:** consultoras oficiales sin datos fijos presentados como reales, con ausencia explícita de campos no disponibles.

### Etapa 3E — Errores, procedencia y contrato

Separar consulta válida vacía, error externo, autorización insuficiente, formato inválido y resultado parcial. Revisar el endpoint genérico sin suprimirlo. Preservar sanitización de credenciales. No alterar BCRP o la configuración Resilience4j.

Decidir expresamente el destino de los fallbacks simulados. Si se mantienen para demostración, deben continuar marcados SIMULADO; no usarlos silenciosamente como respaldo oficial ni permitir que se confundan con un resultado oficial vacío.

**Aceptación:** fuentes y estados coherentes incluso sin filas; contrato actualizado como fuente de verdad y cuatro rutas conservadas.

### Etapa 3F — Pruebas locales y ejecución real aislada

Preparar pruebas con muestras verificadas y transporte simulado para parseo y errores. Con autorización posterior, generar OpenAPI y ejecutar compilación/pruebas sin SQLite.

Después diseñar una prueba real independiente, preferentemente con JDK 17, que use el cliente implementado y recursos acotados. No iniciar EcoInvest completo ni depender de llamadas BCRP para verificar SENACE.

**Aceptación:** evidencia diferenciada entre tests locales, autenticación, consulta real y procesamiento. Un fallback o un HTTP 200 con error no cuenta como integración real satisfactoria.

## 9. Archivos probablemente afectados en la futura implementación

| Archivo | Alcance probable |
|---|---|
| `backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java` | Adaptación real, validación del esquema, filtros, paginación verificada y errores seguros |
| `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java` | Solo consolidación y procedencia SENACE, preservando BCRP y otras rutas |
| `backend/src/main/resources/openapi.yaml` | Campos y estados respaldados por la fuente, con compatibilidad pública |
| `backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java` | Parseo realista, filtros, ausencia, errores y seguridad |
| `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java` | Origen independiente de filas, conteos, compatibilidad de rutas y regresión |

DTOs, tipos auxiliares, fixtures o una prueba real temporal se propondrán después de conocer el esquema y requieren autorización antes de crearlos. No se propone modificar POM, versiones, AppConfig, BcrpClient, configuración de Resilience4j, SQLite o frontend.

## 10. Pruebas recomendadas

| Grupo | Casos |
|---|---|
| Parseo | Respuesta oficial válida, envoltorio, campos opcionales, tipos y nulos |
| Proyectos | Identificador estable, varios proyectos por titular, duplicados y campos ausentes |
| Temporalidad | Año derivado de la fecha acordada; formatos válidos/invalidos; límites de año |
| Sectores | Acentos, alias documentados, TODOS y valores desconocidos sin sustitución arbitraria |
| Consultoras | Registro, sectores múltiples, estado/vigencia y datos desconocidos |
| Completitud | Paginación, última página, repetición o pérdida de filas y límites de consulta |
| Procedencia | Lista oficial vacía frente a error y simulación; sin inferencia desde la primera fila |
| Transporte | HTTP 401/403/429/5xx, conexión, timeout y respuestas de error con HTTP 200 |
| Formato inesperado | HTML real, JSON inválido y Content-Type diferente, según observación del servicio |
| Seguridad | Credencial ficticia ausente de logs, excepciones, URL pública y fixtures |
| Regresión | Cuatro rutas, integración BCRP intacta y ausencia de acceso SQLite |

Las pruebas de JSON deben incluir transporte simulado realista, no solo mapas mockeados, para ejercitar la deserialización. No se debe ejecutar una prueba de paginación con reglas inventadas del proveedor.

## 11. Riesgos y criterios de aceptación

### Riesgos principales

- El endpoint configurado podría no ser la interfaz vigente o usar identificadores distintos.
- Puede requerirse una credencial o un canal de acceso no disponible todavía.
- Una consulta aparentemente completa puede ser solo una página; los conteos del dashboard serían incorrectos si no se reconoce.
- Campos del prototipo pueden no existir en la fuente; rellenarlos conservaría una simulación encubierta.
- Interpretar otra fecha como año de proyecto alteraría filtros y conteos.
- Las etiquetas de estado y vigencia pueden tener significado específico y no admitir equivalencias simples.
- Una evolución de esquema externo puede requerir actualización del adaptador sin que cambie HTTP 200.
- Cambiar el tratamiento de listas vacías o fallbacks afecta consumidores y debe documentarse.
- Iniciar el contexto productivo mantiene el riesgo de abrir SQLite con ddl-auto update; no debe utilizarse para estas verificaciones.

### Criterios de aceptación de la futura integración

1. Fuente, endpoint y mecanismo de autenticación verificados oficialmente.
2. Datos publicados trazables a registros recibidos, sin usar datos fijos como oficiales.
3. Sector y año siguen reglas documentadas y producen resultados verificables.
4. Paginación y cobertura reconocidas antes de calcular totales definitivos.
5. Campos inexistentes expresados como ausencia; sin inversiones, ubicaciones o estados ficticios.
6. Procedencia válida incluso cuando no hay registros.
7. Errores y protección web tratados sin evadir controles ni filtrar credenciales.
8. Cuatro rutas preservadas y OpenAPI actualizado antes de regenerar modelos.
9. Pruebas locales satisfactorias tras autorización y comprobación real separada, sin SQLite o servidor completo.
10. BCRP permanece parcialmente validado; sus pendientes no se declaran resueltos por completar SENACE.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE y BCRP. La Fase 2C BCRP tiene cierre parcial: JDK 17.0.12 superó TLS para 2017 y 2018; PM04974AA/2017 produjo el valor real BigDecimal -2.44780196733593 con origen API_DIRECTA_BCRP. El intento de 2018 recibió HTML y el cliente lo rechazó con FORMATO_INVALIDO. No se certificaron todos los años, series, Resilience4j o la aplicación completa. Java 21 se pospone hasta la etapa final.

La integración SENACE actual hace peticiones a una base declarada con auth_key, pero descarta las respuestas de proyectos y consultoras y construye cuatro registros fijos de cada tipo con origen SIMULADO. El genérico devuelve data sin validar esquema. Sector y año solo filtran los proyectos fijos. El controlador infiere origen desde el primer proyecto y marca una lista vacía como SIMULADO, lo que deberá evolucionar antes de publicar datos reales.

No se ha verificado en esta fase que los endpoints, identificadores, formato, autenticación, paginación o condiciones de acceso configurados sean los vigentes del proveedor. No se deben implementar parsers o reglas sobre supuestos.

**Siguiente paso recomendado:** autorizar una Fase 3A de validación oficial acotada de proyectos y consultoras SENACE. Confirmar documentación y autenticación, obtener muestras sanitizadas y definir el diccionario de campos, fecha anual, sectores, estados y completitud. Después autorizar un lote pequeño en cliente, consolidación SENACE del controlador, contrato y pruebas. No tocar BCRP, SQLite, frontend o Resilience4j y no eludir protección de proveedores.

Este reporte es documentación y planificación. No se efectuaron cambios de implementación ni consultas externas para prepararlo.
