# EcoInvest — Diseño Fase 3B.1: integración real SENACE

**Fecha:** 2 de noviembre de 2026.  
**Naturaleza:** propuesta de diseño, sin implementación.  
**Autorización utilizada:** crear únicamente este reporte. No implica aprobación de las decisiones de negocio, cambios productivos o ejecución de Fase 3B.2.

## 1. Resumen ejecutivo

La investigación previa verificó respuestas reales de CarteraProyectos y ConsultorasAmbientales. Ambas tienen raíz JSON array, pero el cliente Java actual solicita Map.class y después construye registros SIMULADO. El primer objetivo de implementación será consumir y representar fielmente esos datos, separando filas, procedencia, estado y cobertura.

Se propone conservar los cuatro endpoints existentes, OpenAPI como fuente de contrato y BCRP intacto. El diseño utiliza modelos tipados de entrada, mapeo acotado y un resultado de consulta independiente de las listas. No se justifica introducir una capa Service adicional solo para cumplir un patrón.

Persisten decisiones sobre fecha/año, normalización de actividad, identidad de consultoras, campos ausentes y manejo HTTP degradado. Se presentan alternativas y recomendaciones para aprobación; ninguna se implementó.

Por preferencia expresa del usuario, las respuestas de seguimiento deben ser breves y remitir al detalle de este reporte. Esta preferencia no requiere modificar instrucciones o reportes anteriores.

## 2. Estado de partida

### 2.1. Arquitectura y restricciones vigentes

- EcoInvest integra exclusivamente SENACE y BCRP; no OEFA o ESG.
- Java 17, Oracle JDK 17.0.12 para ejecuciones autorizadas, Spring Boot 3.2.4 y Maven Wrapper 3.9.9.
- Contrato en [openapi.yaml](../../backend/src/main/resources/openapi.yaml); OpenAPI Generator 7.4.0 genera ApiApi y modelos. No editar target/ manualmente.
- [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java) implementa el contrato y coordina los dos clientes.
- [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java) realiza transporte y actualmente construye listas fijas.
- RestTemplate compartido con tiempos de conexión/lectura de cinco segundos. No modificar globalmente sus convertidores o políticas para resolver un proveedor.
- BCRP Fase 2C cerrada en su alcance específico. No modificar cliente, catálogo o Resilience4j en este lote.
- SQLite está configurado, pero no hay respaldo persistente implementado. No abrir la base o iniciar el contexto completo.
- Frontend React + TypeScript pendiente; carpeta vacía según inspecciones previas. La compatibilidad sigue siendo relevante para contrato y consumidores futuros.

Las cinco skills solicitadas están disponibles: arquitectura, integración, seguridad, testing y documentación. Se aplicaron junto con [AGENTS.md](../../AGENTS.md), respetando la autorización de diseño. No se activó frontend ni se realizaron HTTP, Maven, pruebas, instalaciones o commits.

### 2.2. Rutas que deben conservarse

```text
GET /api/dashboard
GET /api/estado-servicios
GET /api/consultoras
GET /api/senace/datastream
```

Mantener una ruta no basta para garantizar compatibilidad: también importan raíz de respuesta, parámetros, campos, tipos, ausencias y códigos HTTP. Cualquier ruptura debe aprobarse explícitamente.

## 3. Evidencias oficiales utilizadas

La fuente vigente es la actualización acumulativa del 02/11/2026 del [reporte Fase 3A](REPORTE_FASE3A_VALIDACION_SENACE.md), no sus primeros apartados históricos que todavía indicaban muestras pendientes.

También se revisaron el [estado del 02/11](../ESTADO_ECOINVEST_PARA_GPT_2026-11-02.md), [índice](../README.md) y [cierre BCRP](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md). Los dos primeros contienen fotografías anteriores a las consultas completas y deben leerse con ese límite.

Fuentes oficiales ya consultadas, sin repetir HTTP en este diseño:

- [Ayuda API SENACE](https://datosabiertos.senace.gob.pe/Api/Help).
- [Manual SENACE](https://datosabiertos.senace.gob.pe/Api/Manual/Guía-uso-Portal-datos-abiertos.pdf), versión 1.0 del 19/08/2020, leído textualmente en una fase anterior.

| Recurso observado | Resultado previo | Alcance |
|---|---|---|
| /Api/datastreams/CarteraProyectos | HTTP 200, application/json, 1.509.133 bytes, array de 2.567 objetos | Trece campos presentes en todos los objetos; sin nulos JSON; ID único en esa respuesta |
| /Api/datastreams/ConsultorasAmbientales | HTTP 200, application/json, 4.785.774 bytes, array de 15.015 objetos | Ocho campos string presentes en todos; sin nulos JSON; sin identificador oficial explícito |

Se utilizaron las rutas sin /data.json y auth_key en query, sin publicar la clave. El éxito con la credencial enviada no prueba que esta sea obligatoria, ni autoriza probar otras claves o mecanismos.

Los cuerpos completos se recibieron en memoria y no se guardaron. Las estadísticas están documentadas; no existe un fixture completo reutilizable. Cuerpo HTTP completo no significa universo oficial completo, y no se confirmó paginación REST, total externo, actualización o cobertura territorial.

## 4. Diagnóstico de incompatibilidades

| Ubicación actual | Hallazgo | Corrección de diseño propuesta |
|---|---|---|
| SenaceClient.java:105–115 | Añade /data.json y pide Map.class | Para los dos recursos verificados, utilizar la ruta observada y validar raíz array |
| Cliente:52–84 | Descarta respuesta y devuelve listas SIMULADO | Mapear filas oficiales en el camino real |
| Cliente:127–150 | Fallback de proyectos/consultoras genera simulación | Recomendación: ausencia explícita, sin sustitución automática por mock |
| Cliente:113–119 | Sanitiza excepción pero pierde categoría HTTP | Error tipado seguro con categoría, sin causa que filtre URI/cuerpo |
| Controlador:45–57 | Conteo por lista y origen por primera fila | Metadatos de consulta independientes y semántica explícita de los conteos |
| OpenAPI:47–79,96–102 | Consultoras array, proyectos genéricos y origen genérico | Esquemas aditivos y metadatos sin cambiar raíz de consultoras silenciosamente |
| Tests SENACE actuales | Exigen Map y SIMULADO tras éxito | Sustituir esas expectativas por arrays y datos oficiales de prueba claramente simulados |

La variante /data.json no se ensayó en las consultas nuevas: su invalidez no está demostrada. La propuesta de alineación se apoya en la ruta documentada y observada, no en una afirmación de caída de la otra.

No eliminar funcionalidades del endpoint genérico: declara otros recursos que no se investigaron en esta fase. La modificación de ruta y esquema debe limitarse a los dos verificados o acordarse expresamente para los demás. No declarar válidos o rotos los seis restantes.

## 5. Modelos de datos propuestos

### 5.1. Separación interna y pública

Flujo propuesto:

```text
EcoInvestController
  -> SenaceClient
       -> transporte SENACE especifico cuando sea necesario
       -> lectura String y Jackson explicito
       -> DTO externo / validacion
       -> mapeo
       -> ResultadoConsultaSenace<T>
  -> modelos publicos generados desde OpenAPI
```

Modelos auxiliares candidatos, nombres sujetos a aprobación:

- ProyectoSenaceDto: trece campos externos con nombres oficiales mediante Jackson.
- ConsultoraSenaceDto: ocho campos externos; fechas originales como texto.
- ResultadoConsultaSenace<T>: filas y metadatos, sin persistencia.
- Tipos de estado/motivo seguros si mejoran claridad frente a strings dispersos.

Ubicación candidata de DTOs: com.ecoinvest.soa.client.dto. No cambiar paquetes existentes. Evitar un DTO externo que dependa de los modelos generados del contrato público. El mapeo puede permanecer en helpers privados puros del cliente si es pequeño; separar un mapper solo si el tamaño y pruebas lo justifican.

No se propone una capa Service sin una responsabilidad nueva real. El controlador conserva orquestación; no debe asumir reglas del proveedor basándose en mapas sin tipo.

### 5.2. Proyectos: requerido, opcional y calidad

Recomendación para registros públicos aceptados: id y origen requeridos; los campos de negocio admiten ausencia documentada cuando el proveedor no los entregue. ID es Long propuesto, no una prueba de su rango futuro o estabilidad global.

La presencia y tipo de los campos externos deben validarse. Un cuerpo con raíz incorrecta, fila no objeto o identificador esencial inválido no debe convertirse silenciosamente en cero registros. Recomendación inicial: rechazar la respuesta estructuralmente inconsistente, con motivo seguro. Admitir solo un subconjunto válido requiere aprobación de una política parcial con conteos, no filtrado silencioso.

Que trece columnas estén presentes hoy no demuestra que todo campo sea contractualmente obligatorio para siempre. Nulos/ausencias de campos opcionales se conservan como ausencia y advertencia, sin inventar valores.

Coordenadas: BigDecimal desde el texto JSON, sin conversión intermedia a double para el modelo. Validar LATITUD en [-90,90] y LONGITUD en [-180,180] solo bajo la hipótesis explícita de grados, sin afirmar CRS, región o exactitud geográfica. Si una pareja es inválida, no reemplazarla por (0,0); acordar omisión de la pareja y aviso de calidad.

### 5.3. Consultoras y filas

El modelo público refleja los ocho campos. No exige RUC, registro, nivel o estado que la respuesta no proporciona. Puede conservar esas propiedades antiguas como opcionales/ausentes si se acuerda compatibilidad; no publicarlas con constantes o etiquetas Activa/Vigente.

No hay clave oficial conocida. Recomendación inicial: conservar filas sin deduplicación automática. Si el consumidor necesita idFila, usar una clave técnica acotada a una consulta, por ejemplo identificador de consulta más índice. Debe documentarse que no es ID oficial ni estable entre actualizaciones. La alternativa de hash de campos no prueba identidad empresarial y necesita resolver filas idénticas; no se recomienda introducirla por defecto.

No crear un registro de consultora único por RAZON_SOCIAL: puede cambiar, repetirse o representar múltiples trámites. Los 15.015 registros no equivalen a 15.015 empresas. Duplicados por clave de negocio y granularidad siguen pendientes.

## 6. Diccionario de correspondencias

### 6.1. Proyectos

Todos los campos siguientes estuvieron presentes en los 2.567 objetos; nulos JSON: 0 %. Vacío significa string vacío o en blanco, no null.

| Campo oficial | Tipo propuesto de entrada | Campo público propuesto | Regla y límite |
|---|---|---|---|
| ID | Long | id | Requerido propuesto; único en respuesta observada, estabilidad pendiente |
| TITULAR | String | titular | Preservar texto, no identidad única |
| RUC | String | ruc opcional | Un vacío observado; nunca convertir a número |
| TITULO_PROYECTO | String | titulo y alias proyecto | Conservar ambos nombres existentes si se aprueba; mismo dato fuente |
| UNIDAD_PROYECTO | String | unidadProyecto | No tratar como expediente |
| TIPO | String | tipo | Preservar etiqueta; no expandir acrónimos automáticamente |
| ACTIVIDAD | String | actividad y alias sector | Ambos conservan el valor original; no equivalencia con sector BCRP |
| FECHA_INICIO | String | fechaInicio | Texto original; normalización separada y condicional |
| ESTADO | String | estado | Conservar etiquetas, sin reinterpretación administrativa |
| DESCRIPCION | String | descripcion | Campo diferente del título |
| LONGITUD | BigDecimal | longitud opcional | Precisión conservada; validación de pareja y rangos explícitos |
| LATITUD | BigDecimal | latitud opcional | CRS y ubicación no certificados |
| RESOLUCION | String | resolucion opcional | 126 vacíos; no equivale a expediente |

Campos derivados propuestos: fechaInicioNormalizada (LocalDate/OpenAPI date), anio solo cuando el parseo sea válido, actividadNormalizada si se aprueba su uso y datos de calidad opcionales. Las iniciales, si se necesitan visualmente, son una derivación de presentación, no evidencia oficial.

Expediente, inversión, moneda y región no tienen fuente explícita. Recomendación: propiedades opcionales omitidas en el camino real; no usar 0, NO REGISTRADO o una región inferida como si fueran observaciones. Si se quiere un marcador visual, debe ser responsabilidad de presentación sobre ausencia explícita.

### 6.2. Consultoras

Todos los campos estuvieron presentes en 15.015 objetos, como strings y sin nulos JSON.

| Campo oficial | Campo público propuesto | Vacíos observados | Tratamiento |
|---|---|---:|---|
| RAZON_SOCIAL | razonSocial | 0 | Conservar; no clave única |
| ACTIVIDAD | actividad | 0 | Separada de subsector y sin inferir especialidades múltiples |
| SUBSECTOR | subsector | 0 | No fusionar con actividad |
| TIPO_SOLICITUD | tipoSolicitud | 0 | No equivale a nivel/estado |
| RESOLUCION | resolucion opcional | 1.588 | Texto; no número de registro supuesto |
| FECHA_RD | fechaRd | 0 | Texto original; formato y semántica pendientes |
| INICIO_VIGENCIA_RD | inicioVigenciaRd opcional | 2 | No derivar estado activo automáticamente |
| FIN_VIGENCIA_RD | finVigenciaRd opcional | 39 | Vacío no implica vigencia indefinida |

RUC, registro oficial, nivel y estado: no disponibles en la respuesta observada. No derivarlos por combinación de campos. Si más adelante se aprueba normalizar las fechas, añadir propiedades distintas del texto original y validar formato/semántica primero.

### 6.3. Original y normalizado

Recomendación general: el DTO conserva el texto fuente; el modelo público conserva originales relevantes y expone derivados separados. Strings vacíos o en blanco pueden convertirse a ausencia en propiedades normalizadas si se aprueba. No recortar o reemplazar silenciosamente el valor original.

La omisión JSON frente a null debe definirse expresamente en OpenAPI/Jackson. Una propiedad opcional omitida no conserva necesariamente la compatibilidad con un consumidor que esperaba el campo fijo del mock; el riesgo debe comunicarse aunque el esquema antiguo fuera genérico.

## 7. Reglas temporales y sectoriales

### 7.1. Alternativas de fecha

| Alternativa | Justificación | Riesgo/consecuencia |
|---|---|---|
| Formato explícito dd/MM/uuuu estricto | 1.629 fechas fueron inequívocas entre los formatos comprobados; compatible con el contexto observado | Las otras 938 no prueban el orden por sí solas; es una decisión explícita, no formato contractual confirmado |
| Varios formatos con detección por fila | Tolera heterogeneidad real si se acredita | No elegir silenciosamente entre formatos ambiguos |
| Solo texto, sin interpretación anual todavía | No introduce semántica no aprobada | No satisface el filtro anio de manera honesta |

Recomendación: aprobar explícitamente DateTimeFormatter dd/MM/uuuu con resolución estricta y definir anio como año de FECHA_INICIO. No llamarlo año de aprobación, resolución o inversión. Preservar fechaInicio original y producir normalizada/derivada solo cuando sea válida.

Mantener el default actual de anio=2017 salvo decisión distinta. El parámetro opcional actual no implica ausencia de filtrado si el framework/controlador aplica ese default; no cambiar su significado incidentalmente. Si se desea un modo sin año, diseñarlo explícitamente en otro cambio compatible.

Para fecha ausente/inválida: no asignar el año consultado ni tomar una fecha vecina. Con filtro anual, excluir de las coincidencias evaluables y comunicar el número no evaluable. Ese resultado puede tener limitación de selección temporal aunque el cuerpo se reciba completo. Sin una política aprobada para esa situación, no afirmar exactitud global del conteo filtrado.

### 7.2. Actividades y normalización

Preservar ACTIVIDAD exacta. Una función explícita de comparación puede recortar espacios externos, usar Locale.ROOT y eliminar acentos si se aprueba para filtros. La clave de comparación no sustituye al dato original ni convierte una actividad desconocida en una conocida.

La variante Residuos Sólidos/Residuos Solidos justifica evaluar normalización ortográfica, no una agrupación económica general. Mantener separadas actividad SENACE y descripción/ámbito BCRP.

TODOS: sin filtro de actividad SENACE; BCRP sigue usando PM04983AA, PBI nacional. Para Agricultura, Pesca, Minería, Hidrocarburos y Electricidad conservar la resolución aprobada del BcrpClient. Transportes/Energía y sectores sin serie continúan con ausencia justificada. No modificar el catálogo ni trasladar el valor macro a registros individuales.

## 8. Procedencia y tratamiento de ausencias

### 8.1. Resultado interno independiente de las filas

Propuesta de dimensiones:

| Dimensión | Valores/semántica propuesta |
|---|---|
| origen | API_DIRECTA_SENACE, NO_DISPONIBLE o SIMULADO solo en un modo de demostración explícito aprobado |
| estadoConsulta | EXITO, VACIA, ERROR_EXTERNO, FORMATO_INVALIDO, PARCIAL |
| estadoFiltrado | APLICADO, SIN_COINCIDENCIAS, NO_EVALUABLE o NO_APLICABLE |
| coberturaDataset | NO_VERIFICADA mientras no exista evidencia externa suficiente |
| cuerpoCompleto | Si se llegó al final de la respuesta HTTP; no garantiza universo completo |
| motivo/advertencias | Códigos seguros, no mensajes del proveedor con URI/cuerpo |
| conteos | Recibidos, aceptados, devueltos y excluidos/no evaluables cuando se conocen |

Un array raíz vacío validado es consulta VACIA con origen oficial, no error y no SIMULADO. Una lista vacía después de filtros no equivale a proveedor vacío: estadoFiltrado SIN_COINCIDENCIAS. Los fallos no deben producir conteos oficiales 0 por mera ausencia de datos.

Una lectura cortada por tamaño o timeout no se deserializa como respuesta válida. Puede comunicarse PARCIAL con datos no publicados y métricas no confiables omitidas. No ofrecer fragmentos como filas oficiales. Si las fechas de algunas filas no son evaluables, expresar por separado esa limitación de filtrado, sin confundirla con truncamiento HTTP.

Recomendación: retirar simulación automática del fallback real. Si se desea conservar demo, requiere selección explícita y procedencia inequívoca; no implica crear otro endpoint o configuración sin aprobación.

### 8.2. Contrato por endpoint

| Ruta | Forma conservada | Metadatos propuestos |
|---|---|---|
| /api/dashboard | Objeto existente con proyectos array | Añadir consultaSenace opcional; mantener fuentes.senace y detalle BCRP |
| /api/consultoras | Array raíz | Headers documentados de estado, procedencia y cobertura; filas con origen cuando existan |
| /api/senace/datastream | Objeto envolvente | Conservar datastream, origen y data; data puede ser array; añadir estado/motivo/cobertura |
| /api/estado-servicios | Objeto de métricas existente | Sin nuevas consultas a proveedores; mantener NO_IMPLEMENTADO para respaldo |

Headers candidatos para consultoras: X-Senace-Origen, X-Senace-Estado y X-Senace-Cobertura. Añadir conteos solo si se justifican y se conocen. Documentarlos en OpenAPI. Para lectura desde navegador deben exponerse mediante CORS dentro de un cambio expresamente aprobado del controlador; no modificar AppConfig o políticas compartidas por defecto.

Alternativa: cambiar consultoras a un objeto con datos/metadatos simplifica el consumidor, pero rompe su raíz array. Requiere aprobación explícita o versión nueva. No es un cambio compatible silencioso.

### 8.3. Conteos del dashboard

- total propuesto: número de registros devueltos tras filtros, no proyectos únicos certificados.
- aprobados propuesto: registros de esa misma selección con estado Aprobado según comparación acordada; no equivalencia con un universo de certificaciones únicas.
- Exponer recibidos y filtros en consultaSenace. No usar los 2.567 como constante ni supuesto de estabilidad.
- En error, no publicar métricas oficiales inventadas; omitir total/aprobados no confiables o documentar explícitamente otro contrato aprobado.
- Un resultado sin coincidencias evaluables puede devolver 0 si se identifica como conteo de selección y se declara cuántas fechas no pudieron evaluarse; no afirmar que no existan otros registros potenciales.
- BCRP se resuelve independientemente: puede existir un indicador económico aunque SENACE falle, sin simular cartera ambiental.

### 8.4. Política HTTP pendiente

Opción A: mantener respuestas agregadas degradadas HTTP 200 con estados/motivos explícitos. Minimiza cambios y permite dashboard con BCRP disponible; obliga al consumidor a interpretar metadatos.

Opción B: utilizar 502/503 para determinados fallos de proveedor o configuración y documentar respuestas de error. Puede ser más expresiva, especialmente para consultoras, pero cambia comportamiento y pruebas de consumidores. No alterar códigos automáticamente.

La política final debe definirse por endpoint; un dashboard parcialmente útil no tiene necesariamente el mismo tratamiento que un recurso cuya única fuente falló. Mantener coherencia entre estado HTTP, headers y cuerpo.

## 9. Estrategia de configuración segura

Propiedades actuales a conservar o mapear explícitamente: external.apis.senace-base-url y external.apis.senace-auth-key. Variables acordadas: SENACE_BASE_URL y SENACE_AUTH_KEY. No asumir que Spring convierte automáticamente estos nombres cortos en la jerarquía completa.

### Desarrollo local

Alternativa recomendada base: inyectar variables del proceso y referenciarlas explícitamente en la configuración Spring, sin valores en código. Eliminar el default de credencial del cliente cuando ese cambio se autorice.

Como conveniencia opcional, importar backend/.env.apis.local mediante un mecanismo Spring explícito y compatible con su formato, limitado al perfil local. Verificar soporte del formato, posible indicación de properties y resolución de rutas al ejecutar desde raíz/backend/IDE; no fijar una ruta absoluta del equipo. La sintaxis y el comportamiento exactos de importación deben validarse en el lote autorizado, no asumirse aquí.

No usar dot-sourcing o ejecutar el archivo. No modificarlo para adaptar el diseño ni adjuntarlo a reportes. Una importación opcional no debe ocultar que falta una clave requerida.

### Nube

Configurar las mismas propiedades mediante variables/secretos del servicio de alojamiento. No desplegar el archivo local ignorado como requisito. Separar el mecanismo de entrega del valor del contrato de configuración de la aplicación.

Decidir fallo de arranque frente a estado de servicio no configurado: recomendación evaluar configuración de SENACE antes de su primera consulta y producir categoría segura; si se exige fail-fast, documentar que puede impedir servir BCRP. No elegir esa política de disponibilidad por accidente al retirar el default.

### Transporte y errores

Mantener TLS y hostname. Nunca publicar auth_key, URI autenticada, cookies, cuerpo completo o causas de excepción inseguras. Conservar categorías seguras de HTTP/conexión/formato para observabilidad.

Si es necesario limitar tamaño o evitar redirecciones en Java, utilizar un transporte específico SENACE justificado, sin cambiar globalmente RestTemplate compartido o AppConfig y sin afectar BCRP. Obtener String no impone por sí solo un límite al cuerpo; la implementación de ese control debe ser real y probada, no una comprobación después de una descarga ilimitada.

Los 6 MiB/25 segundos usados en el diagnóstico no son automáticamente la política productiva: confirmar presupuesto de memoria, tiempo y tamaño esperado antes de fijarlo. No añadir caché, reintentos, persistencia o nuevas dependencias como parte implícita de este lote.

## 10. Cambios mínimos propuestos

| Archivo/área | Cambio candidato, sujeto a autorización |
|---|---|
| backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java | Ruta observada para recursos priorizados, array, mapeo tipado y resultado de consulta; fallbacks compatibles sin simulación automática si se aprueba |
| backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java | Solo integración SENACE: metadatos, conteos, filtros y headers; conservar BCRP |
| backend/src/main/resources/openapi.yaml | Esquemas y metadatos aditivos; documentar ausencias, headers y códigos aprobados |
| backend/src/main/resources/application.yml | Solo puente de configuración si se aprueba expresamente; no tocar datasource o Resilience4j |
| backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java | Reemplazar expectativas Map/SIMULADO del camino real por transporte/parseo verificados |
| backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java | Compatibilidad, vacío/error, metadatos, conteos y filtros |
| DTOs/resultado/mapper o configuración específica nuevos | Crear únicamente los auxiliares aprobados, con nombres y paquetes definidos antes de implementar |

No modificar BcrpClient.java, política Resilience4j, SQLite, frontend o versiones. No editar target/. No ampliar el endpoint genérico con capacidades no verificadas o quitar sus otros recursos por simplificar la implementación.

## 11. Plan de pruebas 3B.2

No se ejecutó este plan. Las pruebas deberán usar fixtures pequeñas sanitizadas representativas de los esquemas observados; no copiar el dataset completo ni datos personales innecesarios. Los valores técnicos artificiales se identificarán como prueba, no como observaciones SENACE.

### Cliente y mapeo

- Una serie de objetos válida por cada recurso, raíz array vacía y array de un elemento.
- Ruta exacta sin /data.json para los dos recursos verificados; autenticación correcta en petición simulada y ausencia en logs.
- JSON válido con Content-Type incorrecto, HTML auténtico, JSON inválido, raíz objeto con mensaje de error y array con fila no objeto.
- ID inválido/ausente y política aprobada de rechazo; opcionales nulos/vacíos sin datos inventados.
- RUC conservado como texto y repetido sin deduplicación de proyectos.
- Coordenadas BigDecimal precisas; pareja inválida o ausente sin (0,0) ficticio.
- Fechas explícitas, casos ambiguos, imposibles, vacíos y pérdida de evaluabilidad temporal.
- Actividad original conservada, comparación normalizada acordada y actividad desconocida sin serie sustituta.
- Ocho campos de consultoras, ausencia de RUC/registro/nivel/estado y no derivación de vigencia.
- HTTP 400/401/403/500, timeout, desconexión, límite de bytes y redirección sin reenvío de credencial.

### Controlador y contrato

- Las cuatro rutas conservadas y su raíz de respuesta.
- Dashboard con datos, proveedor vacío, selección vacía, error y limitación temporal.
- Origen/metadatos disponibles aun con lista vacía; métricas desconocidas no fingidas como cero.
- Consultoras array más headers, incluidos escenarios vacíos/error conforme a política HTTP aprobada.
- Endpoint genérico conserva objeto y acepta data array; otros recursos existentes no se eliminan ni se declaran verificados.
- Metadatos BCRP, precisión y ausencia continúan iguales, usando mocks sin alterar su cliente.

### Configuración y aislamiento

Probar resolución explícita de propiedades, falta de configuración y rechazo de destino indebido sin imprimir valores. MockRestServiceServer para transporte y MockMvc para contrato, sin APIs reales o SQLite.

Preservar anotaciones/fallbacks Resilience4j; si cambian retornos, comprobar su compatibilidad con un contexto mínimo autorizado. No reabrir la batería BCRP o cambiar políticas para hacer pasar tests SENACE.

Antes de cualquier Maven, solicitar autorización con comandos y efectos: regeneración OpenAPI, compilación y escritura target/. Comprobar java -version, Maven y JVM de pruebas bajo Oracle 17.0.12. No declarar operación del cliente hasta compilar/probar el nuevo código, y no declarar end-to-end por MockMvc o contexto mínimo.

## 12. Decisiones pendientes de aprobación

Las recomendaciones siguientes no están aprobadas por autorizar este reporte.

| Tema | Evidencia y alternativas | Recomendación técnica | Consecuencia backend/frontend | Riesgo | Decisión solicitada |
|---|---|---|---|---|---|
| Significado de anio | FECHA_INICIO observada; aprobación no acreditada; también se puede posponer filtro | Año de FECHA_INICIO, etiquetado así | Filtro/documentación UI cambian su interpretación, no la ruta | Confundir inicio y aprobación | Aprobar semántica |
| Formato fecha | 1.629 inequívocas y 938 ambiguas; uno o varios formatos | dd/MM/uuuu estricto con original conservado | Parseo, anio derivado y errores | Interpretación día/mes incorrecta si contrato cambia | Aprobar regla o pedir evidencia adicional |
| Default 2017 | Contrato/controlador actuales | Conservar en este lote | Sin cambio incidental de selección | Confundir parámetro opcional con sin filtro | Confirmar conservación |
| Vacíos y ausentes | Vacíos reales y campos sin fuente; null u omisión | Ausencia opcional omitida en modelo normalizado, original cuando sea relevante | UI debe mostrar no disponible | Consumidor esperaba strings fijos | Aprobar serialización |
| ID y filas inválidas | ID único solo en respuesta observada | id requerido propuesto; respuesta estructural inválida rechazada | Datos confiables o error explícito | Rechazo de un cuerpo por una fila | Aprobar estrictez o política parcial |
| Actividad | Etiquetas originales y variante de tildes | Original más clave explícita de búsqueda normalizada | Filtros consistentes sin reescribir fuente | Fusionar conceptos diferentes | Aprobar normalización |
| Conteos | Registros por fila, no proyectos únicos | total/registros filtrados y aprobados de esa selección | Etiquetas y métricas honestas | Lectura como universo de proyectos | Aprobar denominación |
| Consultoras | Sin ID ni RUC; granularidad no confirmada | Conservar filas sin deduplicar | Directorio de registros, no empresas únicas | Duplicados visuales | Aprobar granularidad inicial |
| idFila | No hay identidad oficial | Solo si lo necesita consumidor, acotado a consulta | Claves UI no estables | Confundirlo con ID oficial | Aprobar necesidad y alcance |
| Vigencia | Tres fechas string; reglas/formato no validados | No derivar activa/vencida todavía | Mostrar datos disponibles sin estado ficticio | Afirmación administrativa incorrecta | Aceptar limitación o investigar con permiso |
| Metadatos consultoras | Array raíz existente | Headers documentados | UI leerá headers expuestos; alternativa es contrato versionado | CORS y compatibilidad | Elegir estrategia |
| Simulación | Fallback actual fijo | No usarla automáticamente en camino real | Estados de error honestos | Cambio de comportamiento percibido | Aprobar retirada del fallback simulado |
| HTTP degradado | Hoy predominan respuestas 200 | Acordar por endpoint antes de codificar | Consumidores deben tratar estado y metadatos | Ruptura de manejo de errores | Elegir 200 degradado o 502/503 documentados |
| Configuración | Archivo local no cargado por Spring | Variables explícitas, importación local opcional | Arranque/deploy reproducibles | Clave ausente o ruta dependiente del cwd | Aprobar mecanismo y fail-fast/degradación |
| Transporte | Cuerpos de hasta 4,8 MB observados | Cliente específico solo si hace falta, con límite real | Memoria/latencia y errores previsibles | Afectar BCRP si se cambia cliente compartido | Aprobar presupuesto y aislamiento |

## 13. Riesgos y limitaciones

- No hay cuerpo conservado para una nueva comprobación semántica sin otra descarga; no ejecutar HTTP para completar el diseño actual.
- El proveedor puede cambiar campos, tamaño y denominaciones. Las cantidades observadas no son constantes de negocio.
- Una raíz array no aporta total externo; no certificar cobertura nacional o histórica.
- Los 15.015 registros de consultoras pueden representar trámites o actividades, no empresas únicas.
- No hay fuente explícita de inversión, región, expediente o RUC de consultoras; la ausencia debe permanecer visible.
- La precisión numérica de coordenadas no acredita CRS o exactitud geográfica.
- Ninguna revisión de texto del manual resuelve reglas jurídicas de vigencia o aprobación por sí sola.
- El objeto público genérico antiguo no obliga técnicamente a sus campos, pero un consumidor podría depender de los valores del mock. Comunicar el cambio y probar contratos.
- Un fallback bien tipado debe seguir siendo compatible con el aspecto existente. No dar por probada la interceptación SENACE por haber probado BCRP.
- El endpoint genérico mantiene recursos no investigados; no asumir su esquema o limitar su funcionalidad sin aprobación.
- Las métricas de circuit breaker no certifican disponibilidad integral. Mantener el monitor separado del estado de una consulta.
- La configuración local ignorada no debe viajar con el código; exclusión Git no cifra valores ni implementa carga de configuración.

## 14. Estado final y siguiente paso

Fase 3B.1 queda como diseño técnico documentado, con alternativas y decisiones abiertas. No se implementaron Java, modelos, contrato, configuración o tests y no se ejecutaron Maven, HTTP, servidores, instalaciones o commits. Se preservan Fase 2C BCRP, SQLite, Resilience4j y frontend.

El siguiente paso es revisar y aprobar las decisiones de la sección 12, definir el primer lote y la lista exacta de archivos auxiliares autorizados. Solo después solicitar implementación Fase 3B.2 y, por separado cuando corresponda, comandos de compilación/pruebas y sus efectos.

No se actualiza el índice documental, reportes de estado o AGENTS.md por asociación; la única creación autorizada de esta ejecución es el presente documento.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest conserva Java 17/Oracle JDK 17.0.12, Spring Boot 3.2.4, OpenAPI fuente y cuatro GET: dashboard, estado-servicios, consultoras y senace/datastream. BCRP Fase 2C está cerrada dentro de su alcance; no modificar BCRP, Resilience4j, SQLite o frontend para este trabajo.

La evidencia vigente del reporte Fase 3A contiene arrays reales: CarteraProyectos 2.567 registros/13 campos y ConsultorasAmbientales 15.015 registros/8 campos. Las rutas sin /data.json respondieron correctamente. El cliente Java todavía exige Map.class, añade /data.json y construye SIMULADO; este diseño no lo implementa.

Propuesta: DTOs externos tipados, lectura String/Jackson validada y ResultadoConsultaSenace<T> separado de filas. Proyectos: Long ID, textos originales, BigDecimal coordenadas, resolución separada; no inventar inversión, moneda, región o expediente. Consultoras: solo ocho campos observados; no inventar RUC/registro/nivel/estado, no deduplicar por razón social ni derivar vigencia automáticamente.

Decisiones principales pendientes: aprobar dd/MM/uuuu estricto y anio como año de FECHA_INICIO (no aprobación); conservar default 2017; vacíos/omisión; actividad original más normalización explícita; total como registros filtrados; identidad técnica de consultoras si hace falta; tratamiento de fallos y simulación. Las recomendaciones no quedaron aprobadas por crear este reporte.

Compatibilidad propuesta: dashboard conserva objeto y añade consultaSenace; consultoras conserva array y usa headers documentados para origen/estado/cobertura, sin cambiar raíz silenciosamente; genérico conserva objeto con data array y no elimina recursos no investigados; monitor mantiene métricas y respaldo NO_IMPLEMENTADO. La procedencia no depende de la primera fila. HTTP 200 degradado frente a 502/503 necesita decisión por ruta.

La configuración debe mapear explícitamente SENACE_BASE_URL/SENACE_AUTH_KEY a propiedades Spring. El archivo local ignorado no se carga automáticamente; importación local opcional o variables del servicio en nube requieren diseño y aprobación, sin valores en código. Un transporte específico SENACE puede limitar tamaño/redirecciones sin alterar cliente compartido BCRP, pero su presupuesto no se hereda automáticamente del diagnóstico.

El siguiente prompt debe revisar decisiones, aprobar un lote mínimo y archivos nuevos antes de Fase 3B.2. Incluir pruebas MockRestServiceServer/MockMvc, arrays, errores, ausencia, precisión, filtros y compatibilidad. No ejecutar Maven sin aprobación y no declarar end-to-end por mocks. Respuestas al usuario breves; detalle en reportes. No se creó ninguna implementación con este documento.
