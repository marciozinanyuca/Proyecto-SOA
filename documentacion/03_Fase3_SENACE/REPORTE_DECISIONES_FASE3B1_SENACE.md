# EcoInvest — Decisiones de Fase 3B.1 para integración SENACE

**Fecha:** 2 de noviembre de 2026.  
**Estado:** decisiones aprobadas por el usuario; implementación no iniciada.  
**Autorización utilizada en esta ejecución:** crear únicamente este reporte.  
**Siguiente fase:** 3B.2, por lotes y con autorizaciones independientes.

## 1. Alcance y evidencia de aprobación

El usuario solicitó cerrar decisiones de diseño sin modificar producción. Codex presentó una tabla compacta de recomendaciones, una política HTTP por endpoint y cuatro lotes. Ante la pregunta de aprobar esas recomendaciones y crear este archivo, el usuario respondió «si», sin excepciones.

Este documento registra como aprobadas las recomendaciones efectivamente presentadas en esa propuesta final. No convierte en aprobada toda alternativa del [reporte de diseño](REPORTE_DISENO_FASE3B1_SENACE.md), ni autoriza implementar archivos, generar modelos, ejecutar Maven, pruebas, HTTP o servidores. Tampoco autoriza actualizar el índice o otros reportes.

Se aplicaron las skills disponibles de arquitectura, integración, seguridad, testing y documentación, junto con [AGENTS.md](../../AGENTS.md). No se consultaron archivos de credenciales.

La evidencia vigente de datos se encuentra en la actualización del 02/11 del [reporte Fase 3A](REPORTE_FASE3A_VALIDACION_SENACE.md). Los apartados anteriores de ese documento son históricos. BCRP Fase 2C sigue [cerrada dentro de su alcance técnico](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md).

## 2. Decisiones propuestas y finalmente aprobadas

Todas las filas siguientes pasaron de recomendación a aprobación en la respuesta del usuario. Su aplicación depende de autorizar los lotes de implementación.

| ID | Tema | Decisión aprobada | Riesgo aceptado o condición |
|---|---|---|---|
| D01 | Texto temporal | Conservar FECHA_INICIO como texto original | No reemplazar el dato recibido por una interpretación |
| D02 | Semántica de anio | Año de inicio a partir de FECHA_INICIO, nunca año de aprobación | Documentar esa interpretación en API y futura UI |
| D03 | Formato | dd/MM/uuuu estricto; errores de parseo explícitos | Regla de producto aprobada con base en evidencia, no formato universal certificado por el proveedor |
| D04 | Año predeterminado | Mantener 2017 | No interpretar anio opcional como consulta de todos los años |
| D05 | Actividad | Conservar original; normalizar espacios, mayúsculas y acentos solo para búsqueda | No alterar el catálogo BCRP ni fusionar actividades económicas distintas |
| D06 | Conteos | total y aprobados representan registros filtrados | No denominarlos proyectos únicos o cobertura oficial completa |
| D07 | Consultoras | Conservar las filas recibidas sin deduplicación automática | Filas similares no prueban error o identidad empresarial |
| D08 | Campos no suministrados | No inventar RUC, registro, nivel o estado de consultoras | Propiedades opcionales/omitidas; no copiar valores del mock |
| D09 | Vigencia | No derivar activa/vencida con la evidencia actual | Mantener textos de fechas disponibles y ausencias |
| D10 | Ausencia | Campos ausentes opcionales; omitir propiedades normalizadas sin valor | Advertir cambios frente a consumidores que esperaban strings fijos |
| D11 | Simulación | Retirar simulación automática del camino productivo real | Fallos y ausencia quedan explícitos; no sustitución por listas fijas |
| D12 | Rutas/formas | Conservar cuatro endpoints y sus raíces de respuesta | Los nuevos códigos HTTP son un cambio observable aprobado por separado en la política siguiente |
| D13 | Procedencia | Estado independiente de la primera fila; metadatos en dashboard y headers seguros en consultoras | Documentar OpenAPI y exposición CORS de los headers |
| D14 | Configuración | Mapeo explícito de SENACE_BASE_URL/SENACE_AUTH_KEY; eliminar default de clave | Sin configuración, no intentar HTTP SENACE ni impedir por ello servir BCRP |
| D15 | Local/nube | Variables equivalentes en nube e importación local opcional explícita del archivo ignorado | No asumir carga automática de .env ni desplegar el archivo local por defecto |
| D16 | Transporte | Cliente SENACE aislado, sin cambiar RestTemplate compartido | Sin nuevas dependencias, redirecciones automáticas o reintentos |
| D17 | Presupuesto productivo inicial | Lectura máxima 6 MiB; conexión 5 s; inactividad de lectura 10 s; plazo HTTP total 25 s | Límite durante streaming, incluidos errores; no comprobación posterior a una descarga ilimitada |
| D18 | Estructura inválida | Rechazar respuesta estructuralmente inválida con motivo explícito | No descartar filas silenciosamente y publicar el resto como completo |
| D19 | Calidad temporal | Conservar problema temporal explícito; al filtrar, informar fechas no evaluables | No asignar año solicitado a una fecha inválida ni presentar exclusiones como ausencia oficial |

La definición exacta de campos estructuralmente esenciales, nombres de clases y detalles de serialización debe seguir el contrato aprobado y quedar verificable en el lote; no se considera aprobada una política de negocio adicional por elegir un tipo Java.

## 3. Decisiones pospuestas y límites de la aprobación

| Tema | Situación | Acción futura |
|---|---|---|
| ID técnico de consultoras | No hay ID/RUC oficial; identidad estable no acreditada | No introducir una identidad oficial ficticia. Proponer clave técnica solo si un consumidor la necesita |
| Deduplicación empresarial | Granularidad de las 15.015 filas no confirmada | Pospuesta; conservar filas según D07 |
| Vigencia administrativa | Sin formato y reglas verificadas | Pospuesta; no estado activo/vencido |
| CRS y región | Rangos numéricos plausibles no prueban referencia geográfica | No derivar región o certificar CRS sin investigación autorizada |
| Inversión/moneda/expediente | Sin fuente explícita en respuesta de proyectos | Mantener ausencia; no completar mediante otras APIs automáticamente |
| Cobertura y paginación oficial | Sin total externo o parámetros REST acreditados | Mantener NO_VERIFICADA; no inventar page/limit |
| Otros datastreams | Seis recursos declarados no investigados aquí | Preservar capacidades existentes; no aplicarles un esquema o ruta verificados solo para dos |
| Modo demostración | Se retira fallback simulado automático, no se diseñó modo demo nuevo | Cualquier modo explícito requiere un encargo separado |
| Algoritmo exacto de importación local | Aprobada su posibilidad, no un lector global arbitrario | Definir solución mínima compatible con Boot 3.2.4, rutas de ejecución y propiedades |
| Casos de límite/redirección y taxonomía detallada | Hay política general, no tabla exhaustiva de cada excepción | Proponer mapeo técnico coherente con secciones 4–5 antes de codificar si introduce comportamiento no cubierto |
| Verificación end-to-end | No iniciada | Separada de MockMvc, mocks o consultas del script |

No se realizarán nuevas consultas para resolver esos puntos bajo autorización documental. Si una pregunta bloquea un lote, justificar la necesidad de nueva evidencia y solicitarla.

## 4. Política de compatibilidad aprobada

### 4.1. Rutas y raíces

| Endpoint | Raíz conservada | Adaptación prevista |
|---|---|---|
| GET /api/dashboard | Objeto | Metadatos SENACE aditivos y procedencia independiente; detalle BCRP intacto |
| GET /api/consultoras | Array | Headers de consulta/procedencia/cobertura, incluso cuando el array sea vacío |
| GET /api/estado-servicios | Objeto | Conservar métricas; no consultar proveedores desde el endpoint |
| GET /api/senace/datastream | Objeto envolvente | Conservar datastream/origen/data; permitir data array para recursos verificados y errores seguros |

No envolver silenciosamente consultoras en un objeto ni eliminar endpoints. Para el dashboard conservar nombres existentes, pero no inventar métricas cuando SENACE falla. Documentar como opcionales las propiedades que no puedan determinarse.

### 4.2. Datos y campos

Proyectos utilizará los trece campos observados mediante el mapeo de diseño: ID, TITULAR, RUC, TITULO_PROYECTO, UNIDAD_PROYECTO, TIPO, ACTIVIDAD, FECHA_INICIO, ESTADO, DESCRIPCION, LONGITUD, LATITUD y RESOLUCION. Coordenadas con precisión decimal preservada; ID único solo en la respuesta observada, sin prometer estabilidad global.

Consultoras reflejará RAZON_SOCIAL, ACTIVIDAD, SUBSECTOR, TIPO_SOLICITUD, RESOLUCION, FECHA_RD, INICIO_VIGENCIA_RD y FIN_VIGENCIA_RD. No trasladar los campos fijos antiguos como observaciones oficiales.

Conservar propiedades compatibles y alias solo con origen claro: titulo/proyecto pueden referenciar TITULO_PROYECTO; sector puede ser un alias documentado de la actividad original, no del ámbito económico BCRP. La definición final pública debe quedar en OpenAPI, no solo en un Map construido a mano.

Mantener objetos de transporte y resultado interno separados de los modelos públicos generados. No editar target/ manualmente.

### 4.3. Filtros y métricas

Se conserva el default anio=2017. El filtro compara año de inicio parseado estrictamente, sin cultura automática del equipo ni interpretación como aprobación.

La clave normalizada de actividad no sustituye el valor original. TODOS mantiene selección de todas las actividades SENACE y contexto PBI nacional en BCRP. Sectores sin correspondencia aprobada conservan indicador ausente; no modificar BcrpClient o su catálogo.

total cuenta registros devueltos por la selección; aprobados cuenta los registros Aprobado de esa misma selección. Fechas no evaluables deben quedar visibles como limitación de filtrado. Un cero filtrado no puede presentarse como afirmación de inexistencia global si existen registros no evaluables.

## 5. Política de errores aprobada

| Situación | Dashboard | Consultoras |
|---|---|---|
| SENACE indisponible | HTTP 200 degradado; conservar BCRP disponible y omitir métricas SENACE desconocidas | HTTP 503, raíz [] y headers de estado |
| Respuesta oficial vacía | HTTP 200, origen oficial, estado vacío y conteos 0 | HTTP 200, [] y estado oficial vacío |
| JSON o estructura inválidos | HTTP 200 degradado con motivo explícito | HTTP 502, [] y headers |
| Configuración SENACE ausente | HTTP 200 degradado, estado no configurado y sin intento HTTP SENACE | HTTP 503, [] y headers |

Los nuevos 502/503 son una modificación observable del comportamiento y deben constar en OpenAPI y pruebas. La aprobación de esta política no constituye una prueba de compatibilidad con consumidores desconocidos.

El endpoint genérico conserva objeto envolvente y aplica categorías equivalentes: indisponibilidad/configuración como servicio no disponible y formato de proveedor inválido como error de integración, sin reenviar un cuerpo externo. Debe documentarse el mapeo público exacto y preservar sus recursos existentes. No devolver la credencial o el código 401 del proveedor como si fuera necesariamente un fallo de autenticación del usuario de EcoInvest.

No utilizar mocks como fallback automático. Los fallbacks deben devolver ausencia/estado seguro compatibles con sus métodos protegidos. Ajustar una firma de retorno exige comprobar su correspondencia con el aspecto, sin cambiar nombres de breaker, política o configuración Resilience4j por conveniencia.

### Procedencia y cobertura

- La fuente no se deduce de la primera fila.
- Array oficial vacío conserva procedencia oficial; vacío tras filtros es un resultado distinto.
- Dashboard incorpora detalle de consulta independiente del array y mantiene fuentes.senace.
- Consultoras mantiene headers seguros documentados. Los nombres propuestos en el diseño, como X-Senace-Origen, X-Senace-Estado y X-Senace-Cobertura, deben fijarse consistentemente en contrato/controlador/tests.
- CORS debe exponer los headers aprobados al navegador; no ampliar otras políticas de seguridad incidentalmente.
- Recibir el final del cuerpo HTTP no acredita cobertura histórica/territorial. coberturaDataset continúa NO_VERIFICADA.
- Una lectura cortada nunca se procesa como JSON completo. Clasificar parcial y motivo seguro, sin métricas oficiales inventadas.

## 6. Estrategia de configuración y seguridad

### 6.1. Configuración de aplicación

Mapear explícitamente SENACE_BASE_URL y SENACE_AUTH_KEY a external.apis.senace-base-url y external.apis.senace-auth-key. No depender de que Spring infiera ese puente a partir de nombres abreviados.

Eliminar el valor predeterminado de credencial del cliente cuando se autorice su implementación. Si SENACE no está configurado, no hacer solicitudes externas y mantener BCRP utilizable. Esto implica que la inyección de configuración no debe provocar inadvertidamente fallo de todo el contexto por un placeholder obligatorio sin política de degradación.

El archivo local backend/.env.apis.local permanece ignorado. La opción de importarlo es explícita y limitada a desarrollo local; no ejecutarlo como código ni imprimir valores. Determinar resolución de ruta y formato de properties compatible en el lote correspondiente. No modificar el archivo de credenciales como parte implícita de adaptar Java.

En nube, inyectar configuración equivalente desde el servicio de alojamiento. No incluir el archivo local en Git o en el despliegue como requisito. No modificar el .env registrado de la raíz.

### 6.2. Transporte independiente SENACE

Presupuesto aprobado inicial:

```text
Una solicitud por consulta, sin reintentos automaticos.
Maximo de lectura: 6 MiB = 6.291.456 bytes.
Conexion: 5 segundos.
Inactividad de lectura: 10 segundos.
Plazo total HTTP: 25 segundos, incluida conexion y lectura.
Sin redirecciones automaticas.
TLS y hostname normales.
```

El control debe detener la lectura durante streaming, antes de almacenar un cuerpo mayor que el límite. Aplicarlo también a respuestas de error. Obtener String primero y comprobar su longitud después no cumple este requisito. El plazo total no se satisface solo con un timeout por cada lectura, ya que varias lecturas podrían prolongar la operación indefinidamente.

La configuración no altera RestTemplate compartido o AppConfig y no debe afectar al cliente BCRP. Aprovechar bibliotecas existentes o JDK 17; si se demuestra necesidad de dependencia nueva, justificar y solicitar autorización antes de añadirla.

No reenviar auth_key por redirección. No publicar URI completa, Authorization, cookies, cuerpo externo o causas inseguras de excepciones. Conservar categorías seguras para diagnóstico. El conteo de bytes, codificación y eventual compresión debe quedar explícito en implementación/tests sin asumir descargas ilimitadas.

No añadir caché, persistencia, reintentos, consultas de salud externas o procesamiento concurrente como parte implícita del lote.

## 7. Plan de implementación por lotes

Todos los nombres siguientes son propuestas de archivos para solicitar autorización de implementación. Crear este reporte no autoriza crearlos. Los nombres se expresan desde la raíz del repositorio.

### Lote 1 — Modelos y mapeo puro

**Objetivo:** representar los trece y ocho campos y aplicar las reglas aprobadas sin red.

Archivos nuevos propuestos:

- backend/src/main/java/com/ecoinvest/soa/client/dto/ProyectoSenaceDto.java.
- backend/src/main/java/com/ecoinvest/soa/client/dto/ConsultoraSenaceDto.java.
- backend/src/main/java/com/ecoinvest/soa/client/ResultadoConsultaSenace.java.
- backend/src/main/java/com/ecoinvest/soa/client/SenaceMapper.java.
- backend/src/test/java/com/ecoinvest/soa/client/SenaceMapperTest.java.

**Dependencias:** decisiones D01–D10 y D18–D19; Jackson y Java existentes. No conectar aún controladores o cambiar contratos públicos.

**Riesgos:** fechas ambiguas, identidad no certificada, pérdida de precisión y opcionales confundidos con valores ficticios.

**Pruebas necesarias:** campos, arrays pequeños, nulos/vacíos, RUC textual, coordenadas precisas, parser temporal estricto, actividad original/clave de búsqueda, filtros y ausencia de deduplicación.

**Aceptación:** mapeo determinista sin red, datos originales relevantes preservados y ausencia explícita. El lote permanece compilable con el cliente actual al no cambiar todavía sus firmas.

**Autorizaciones:** creación de esos archivos; Maven/pruebas por comandos y efectos concretos posteriormente.

### Lote 2 — Transporte y configuración

**Objetivo:** solicitud oficial acotada y configuración que pueda degradarse sin inutilizar BCRP.

Archivos propuestos:

- Crear backend/src/main/java/com/ecoinvest/soa/client/SenaceTransport.java.
- Crear backend/src/test/java/com/ecoinvest/soa/client/SenaceTransportTest.java.
- Modificar backend/src/main/resources/application.yml solo para el puente SENACE y la opción local expresamente acordada.

**Dependencias:** resultado interno del lote 1; API HTTP ya disponible/JDK 17; no nuevas dependencias.

**Riesgos:** límite aplicado tarde, timeout total inefectivo, filtración en excepciones y ruta local dependiente del cwd.

**Pruebas necesarias:** configuración ausente/duplicada si corresponde, destino indebido, URL autorizada, errores HTTP, cuerpo limitado, redirecciones, plazos y sanitización. El mecanismo de simulación debe adaptarse al transporte elegido; no imponer MockRestServiceServer si el transporte final no utiliza RestTemplate. No abrir servidores o red durante una fase sin permiso.

**Aceptación:** límite real de streaming, plazos y ausencia de reintentos; sin escritura SQLite o cambio de cliente compartido; configuración ausente expresada como estado seguro.

**Autorizaciones:** archivos concretos y cambio mínimo de configuración; ejecución de pruebas/compilación después. Si se requiere un auxiliar adicional para importación local, proponerlo antes de crearlo.

### Lote 3 — Cliente SENACE real

**Objetivo:** integrar transporte, arrays y mapper, retirando la construcción simulada del camino productivo real.

Modificar:

- backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java.
- backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java.

**Dependencias:** lotes 1 y 2.

**Riesgos:** desalineación de fallbacks, pérdida de comportamiento del endpoint genérico, incompatibilidad de firmas con controlador/tests existentes.

**Pruebas necesarias:** arrays válidos/vacíos, filas estructuralmente incorrectas, errores/formato, retiro de simulación automática, procedencia y compatibilidad de fallbacks.

**Aceptación:** ruta sin /data.json para los recursos verificados, resultado seguro y tipado, ninguna sustitución ficticia. No reducir automáticamente los recursos genéricos existentes.

**Compilabilidad entre lotes:** conservar adaptadores compatibles para firmas consumidas por el controlador o coordinar ese cambio de firma con el lote 4 en una misma unidad compilable. No dejar el repositorio intencionadamente sin compilar entre entregas ni usar un adaptador List que pierda procedencia como solución final. La estrategia exacta requiere revisar los callers antes del cambio.

**Autorizaciones:** modificación de esos archivos y cualquier ajuste de firma dependiente identificado previamente; pruebas/Maven separadamente. No modificar políticas Resilience4j.

### Lote 4 — Contrato público y controlador

**Objetivo:** exponer datos reales, metadatos y política HTTP aprobada conservando rutas/formas.

Modificar:

- backend/src/main/resources/openapi.yaml.
- backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java, solo integración SENACE.
- backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java.

**Dependencias:** resultados de lotes anteriores; especificar contrato antes de consumir modelos públicos generados.

**Riesgos:** nuevos 502/503, métricas omitidas en degradación, headers no visibles por CORS y campos mock que consumidores creían obligatorios.

**Pruebas necesarias:** cuatro rutas, raíces, headers/CORS, serialización de opcionales, filtros, conteos, vacío/error y BCRP intacto mediante mocks.

**Aceptación:** OpenAPI e implementación coherentes, fuente independiente de listas, ausencia sin falsas métricas, consultoras array incluso en estados documentados, genérico objeto y monitor sin nuevas consultas externas.

**Autorizaciones:** modificación de esos archivos; generación/compilación/pruebas solo con aprobación de comandos. No editar artefactos generados.

## 8. Plan de pruebas transversal

No se ejecutaron pruebas durante este cierre documental.

1. Validar nuevas reglas con fixtures pequeños sanitizados. No copiar cuerpos completos o datos personales innecesarios ni presentar valores de prueba como observaciones oficiales.
2. Separar pruebas puras de mapper, transporte simulado y MockMvc. No realizar HTTP real a SENACE en suites locales.
3. Verificar origen/estado incluso con listas vacías y diferenciar proveedor vacío, selección sin coincidencias y fallo.
4. Probar anio=2017 por defecto, parseo estricto, fechas no evaluables y normalización de búsqueda sin reemplazar actividad original.
5. Verificar códigos HTTP exactos de sección 5, headers y exposición CORS. Probar que datos no determinables no se serialicen como cifras oficiales cero.
6. Conservar comprobaciones de precisión, metadatos y ausencia BCRP; no cambiar su cliente o reejecutar consultas reales por esta integración.
7. Comprobar retorno de métodos y fallbacks SENACE con el aspecto cuando cambie la firma; sin cambiar política breaker y con contexto mínimo si se autoriza esa prueba.
8. Mantener datasource/JPA excluidos de contextos que puedan tocar SQLite. No inferir aislamiento solo por no iniciar servidor web.
9. Antes de ejecutar Maven, indicar directorio, comando y efectos sobre OpenAPI/target/. Usar Oracle JDK 17.0.12 y comprobar runtime de Maven/test, no solo release del compilador.
10. Registrar resultados por ejecución con fecha, casos, errores y límites. No declarar end-to-end por mocks, MockMvc o pruebas mínimas.

Los 75 tests históricos de Fase 2B no son una suite nueva ejecutada bajo este documento. Se conservará evidencia histórica sin sumar ejecuciones de runtimes distintos como un único resultado actual.

## 9. Estado final y siguiente fase recomendada

**Cierre de decisiones Fase 3B.1:** aprobado en el alcance de este documento. Las alternativas no elegidas y puntos de sección 3 siguen pospuestos. El diseño se convierte en base para solicitar lotes de implementación, no en autorización de ejecutarlos automáticamente.

**Fase 3B.2:** no iniciada. Próximo paso recomendado: solicitar autorización del lote 1, confirmando sus archivos exactos, criterios y validación permitida. La autorización de escribir un lote no implica Maven, HTTP, servidores, instalaciones o commits si estos no se incluyen expresamente.

En esta ejecución solo se crea este reporte. No se actualiza documentacion/README.md, estados anteriores, skills o AGENTS.md; no se modifican código, configuración, dependencias, BCRP, Resilience4j, SQLite o frontend. No se leyeron credenciales ni ejecutaron pruebas, HTTP o Maven.

## RESULTADO PARA REVISIÓN CON CHATGPT

El usuario aprobó las recomendaciones finales de Fase 3B.1 y autorizó documentarlas. No aprobó iniciar 3B.2. EcoInvest mantiene Java 17/Oracle JDK 17.0.12, Spring Boot 3.2.4, OpenAPI fuente y cuatro rutas. BCRP Fase 2C permanece cerrada; no tocarlo, Resilience4j, SQLite o frontend.

Decisiones aprobadas: FECHA_INICIO original; anio como año de inicio, no aprobación; dd/MM/uuuu estricto y errores explícitos; default 2017; actividad original más normalización solo de búsqueda; total/aprobados como registros filtrados; consultoras sin deduplicación o campos inventados; sin vigencia derivada; opcionales omitidos honestamente; retirar fallback simulado automático; estado/procedencia independiente de las filas.

HTTP aprobado: dashboard 200 degradado ante fallo, formato inválido o configuración ausente de SENACE, preservando BCRP disponible y omitiendo métricas SENACE desconocidas. Consultoras conserva array: 503 [] para indisponibilidad/configuración y 502 [] para JSON/estructura inválidos, con headers seguros. Vacío oficial es 200 con procedencia oficial. Documentar nuevos códigos, headers y CORS. Genérico conserva objeto y errores seguros equivalentes sin eliminar recursos no investigados. Monitor conserva métricas.

Configuración: puente explícito de SENACE_BASE_URL/SENACE_AUTH_KEY, clave sin default; si falta SENACE, no hacer HTTP y no inutilizar BCRP. Archivo local ignorado con importación opcional explícita, sin asumir carga .env; en nube variables equivalentes. Cliente SENACE aislado: streaming real de 6 MiB incluso para errores, conexión 5 s, inactividad 10 s y plazo total HTTP 25 s; sin reintentos o redirecciones y sin nuevas dependencias salvo aprobación.

Implementación propuesta por cuatro lotes: modelos/mapper/resultado puro; transporte/configuración; cliente real; contrato/controlador. Cada lote tiene archivos exactos, riesgos, pruebas y criterios en sección 7. Mantener compilabilidad entre lotes y correspondencia de fallbacks, sin alterar políticas Resilience4j. Maven, generación y pruebas necesitan su autorización explícita.

Pospuestos: identidad estable/deduplicación de consultoras, vigencia administrativa, CRS/región, fuentes de inversión/moneda/expediente, cobertura oficial y paginación, comportamiento no verificado de otros datastreams, modo demo y detalles técnicos que introduzcan decisiones nuevas. No dar por aprobada una alternativa antigua solo por estar en el reporte de diseño.

Compartir este reporte con ChatGPT para preparar el prompt del lote 1 de 3B.2. El prompt debe delimitar archivos y ejecución, conservar los cambios previos y no autorizar implícitamente red o implementación de todos los lotes. Respuestas al usuario breves y detalle técnico en documentos.
