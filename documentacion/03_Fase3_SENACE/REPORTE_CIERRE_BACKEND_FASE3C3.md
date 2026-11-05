# EcoInvest — Fase 3C.3: revisión de cierre del backend y preparación para React

Fecha: **04/11/2026**, America/Lima. Alcance: revisión estática del código y contraste con evidencias anteriores. No se ejecutaron Maven, pruebas, HTTP, servidores o SQLite durante esta revisión. La única creación autorizada es este reporte; no se modificaron código productivo, configuración, frontend, índice o antecedentes.

## 1. Resumen ejecutivo

**El backend está preparado para comenzar el desarrollo de React + TypeScript sobre el contrato vigente y mocks explícitos.** No se identificó un defecto productivo reproducible pendiente que obligue a reimplementar SENACE o BCRP antes de construir las vistas.

La preparación contractual no equivale a preparación operativa completa. Antes de conectar React a una instancia real deben definirse la configuración del proceso Java y un arranque que no abra o modifique SQLite incidentalmente. El dashboard completo con ambos proveedores reales, el arranque productivo y el despliegue siguen sin certificación end-to-end.

Se recomienda cerrar el alcance de implementación e integración aislada del backend, conservar los pendientes operativos independientes e iniciar frontend solamente mediante un encargo explícito. Este reporte no inicia frontend ni autoriza ejecuciones adicionales.

## 2. Estado de las fases

| Fase | Estado y alcance acreditado | Evidencia / pendiente |
|---|---|---|
| 1 — Estabilización | Completada según antecedentes | [Reporte histórico](../01_Fase1/REPORTE_IMPLEMENTACION_FASE1A.md); no se repitieron pruebas |
| 2 — BCRP | Cerrada dentro de su alcance técnico | [Cierre 2C](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md); no equivale a dashboard end-to-end |
| 3A — SENACE oficial | Validación de rutas, acceso y muestras documentada | [Validación oficial](REPORTE_FASE3A_VALIDACION_SENACE.md); cobertura global no certificada |
| 3B.1 — Diseño y decisiones | Decisiones D01–D19 aprobadas | [Decisiones](REPORTE_DECISIONES_FASE3B1_SENACE.md) |
| 3B.2 — Implementación | Cuatro lotes implementados | [Lote 4](REPORTE_IMPLEMENTACION_FASE3B2_LOTE4.md) documenta cierre y antecedentes |
| 3C.1 — Integración controlada | Completada con respuestas artificiales y transporte simulado | [Reporte 3C.1](REPORTE_VALIDACION_FASE3C1_SENACE.md): 32 casos satisfactorios |
| 3C.2 — SENACE Java real | Completada en alcance de cliente directo | [Reporte 3C.2](REPORTE_VALIDACION_FASE3C2_SENACE.md): dos ejecuciones independientes satisfactorias |
| 3C.3 — Revisión de preparación | Revisión documental/estática completada mediante este reporte | No se realiza arranque ni nueva validación operativa |
| Frontend | Pendiente de encargo e implementación | No se crearon archivos React |
| Operación integral / despliegue | Pendiente independiente | Configuración, aislamiento de persistencia y prueba HTTP por autorizar |

El [índice documental](../README.md) mantiene referencias históricas anteriores a estos cierres: aún describe SENACE como simulado y muestras pendientes. No representa el estado más reciente del código. No se actualizó porque no estaba autorizado; los reportes nuevos son las referencias de continuidad pertinentes.

## 3. Validaciones confirmadas y fronteras de evidencia

| Evidencia | Hecho confirmado | Lo que no demuestra |
|---|---|---|
| Lote 4, 03/11/2026 | Generación OpenAPI, compilación y selección de 270 pruebas satisfactorias, nueve omitidas | Ejecución integral con proveedores reales |
| 3C.1, 04/11/2026 | 32 pruebas integradas: MVC → controlador → cliente real → transporte simulado → Jackson/DTO/mapper; AOP real, CORS y aislamiento del contexto | Red oficial, credencial o comportamiento de todo el arranque productivo |
| 3C.2, 04/11/2026 | Transporte/cliente Java reales procesaron 2.567 filas de proyectos y 15.015 de consultoras | MVC y AOP con proveedor real en una misma ejecución |
| BCRP 2C, octubre de 2026 | Observaciones económicas reales y resiliencia aislada documentadas | Disponibilidad futura permanente o nuevos GET en esta fase |
| Inspección 3C.3 | Contrato, controlador y configuración actuales coherentes con esas fronteras | Nueva compilación, suite completa o validación HTTP |

Las selecciones históricas no se suman para presentar una suite conjunta no ejecutada. El runtime de las ejecuciones anteriores fue Oracle JDK 17.0.12; el proyecto declara Java 17 y Spring Boot 3.2.4. La migración Java permanece fuera del alcance.

En 3C.2 HTTP 200 se infirió del camino de aceptación del cliente, no se capturó como metadato independiente; Content-Type y bytes originales no se midieron desde ese test. Ambos resultados tuvieron EXITO, API_DIRECTA_SENACE, cuerpoCompleto=true y cobertura NO_VERIFICADA. No se comprobó que auth_key fuera obligatorio.

## 4. Revisión de los cuatro endpoints

Fuente contractual: [openapi.yaml](../../backend/src/main/resources/openapi.yaml). Implementación: [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java).

El contrato utiliza OpenAPI 3.0.3 y servidor local declarado http://localhost:8081. Maven genera interfaces/modelos; no se editan artefactos target/ manualmente.

### 4.1. GET /api/dashboard

Referencias: OpenAPI línea 9; controlador línea 67.

| Aspecto | Estado actual |
|---|---|
| Parámetros | sector string obligatorio; anio integer opcional, default 2017 |
| Semántica temporal | Año de inicio de FECHA_INICIO mediante dd/MM/uuuu estricto; nunca año de aprobación |
| Respuesta | Objeto DashboardResponse |
| Modelos | ProyectoSenacePublico, FuentesDashboard, ConsultaSenace, ConteosSenace, IndicadorBcrp y enums |
| HTTP documentado | 200, tanto completo como degradado |
| Validación de petición | Sector omitido produce 400 según pruebas previas MVC; esa respuesta no está enumerada expresamente en OpenAPI |
| Procedencia | consultaSenace y fuentes, independientes de la primera fila; indicadorBcrp con metadatos propios |
| Evidencia | Flujo MVC integrado con transporte simulado; SENACE y BCRP reales comprobados por separado |

Campos raíz existentes: total, aprobados, variacionSeleccionada, indicadorBcrp, proyectos, fuentes y consultaSenace. **El esquema no declara una lista required de propiedades del modelo**: no prometer a TypeScript obligatoriedad solo porque el controlador las rellene en el camino actual.

total y aprobados cuentan registros filtrados; no proyectos únicos certificados ni universo oficial. Ante fallo SENACE se omiten total/aprobados/proyectos. Con vacío oficial o selección conocida vacía se conserva una lista vacía y conteos conocidos, con estados que distinguen VACIA, SIN_COINCIDENCIAS y NO_EVALUABLE. La calidad temporal limita la interpretación del cero.

ProyectoSenacePublico conserva: id, titular, ruc, titulo/proyecto como aliases compatibles, unidadProyecto, tipo, actividad/sector como aliases sin equivalencia económica, fechaInicio, anio derivado, estado, descripcion, longitud, latitud, resolucion y origen. Los opcionales ausentes se omiten. No hay inversión, moneda, región o expediente inventados.

Cuando falla SENACE, el controlador sigue hacia BCRP y conserva el indicador disponible. variacionSeleccionada coincide con indicadorBcrp.valor cuando hay observación; ante ausencia no se publica una constante. IndicadorBcrp expone código, descripción, unidad, periodicidad, sector solicitado/económico, ámbito, año, período efectivo, valor, origen, motivo y URL fuente cuando correspondan.

**Implicación React:** response.ok no basta. Hay que inspeccionar consultaSenace, fuentes e indicadorBcrp para mostrar estado degradado, ausencia y advertencias. No usar `total ?? 0` como si desconocido fuera cero. Mantener independientes el filtro ambiental y el alcance económico BCRP.

### 4.2. GET /api/consultoras

Referencias: OpenAPI línea 49; controlador líneas 109–110; modelo línea 279 del contrato.

Sin parámetros. Raíz JSON array de ConsultoraSenacePublica, con ocho propiedades opcionales:

```text
razonSocial, actividad, subsector, tipoSolicitud,
resolucion, fechaRd, inicioVigenciaRd, finVigenciaRd
```

| Situación | HTTP | Cuerpo |
|---|---:|---|
| Filas oficiales disponibles | 200 | Array de filas |
| Respuesta oficial vacía | 200 | [] |
| JSON/estructura/codificación inválidos | 502 | [] |
| Configuración ausente, indisponibilidad, circuito abierto o parcial no utilizable | 503 | [] |

Headers documentados y expuestos por CORS: X-Senace-Origen, X-Senace-Estado y X-Senace-Cobertura. También se incluyen X-Senace-Motivo y X-Senace-Cuerpo-Completo cuando existe evidencia aplicable. Los headers son texto HTTP: el adaptador frontend debe interpretar el booleano y contemplar ausencia.

No hay RUC, ID, registro, nivel o estado administrativo inventados. No se deduplican filas ni se deriva vigencia activa/vencida. Una fila puede tener propiedades opcionales omitidas. No usar razón social como clave única React; una clave técnica de presentación no debe presentarse como ID oficial.

**Implicación React:** [] con 503 no significa cero consultoras oficiales. Conservar status y headers antes de interpretar la lista. Las 15.015 filas observadas aconsejan búsqueda/segmentación visual y renderizado acotado; no obligan a introducir ahora una librería o paginación backend no aprobada.

Validación: MVC y headers con mocks/cliente real simulado; consumo directo real de consultoras validado en 3C.2. Falta operación navegador-servidor-proveedor en un mismo entorno.

### 4.3. GET /api/senace/datastream

Referencias: OpenAPI línea 113; controlador línea 119; modelo línea 370 del contrato.

Parámetro nombre string opcional, default CarteraProyectos. Conserva los ocho nombres históricos. Los dos verificados utilizan rutas oficiales sin /data.json; los otros seis mantienen su camino heredado sin certificar esquema/disponibilidad.

Raíz objeto DatastreamSenaceResponse con datastream, origen, data, consulta y recursoValidado; ante error puede incluir mensaje seguro y datastreamsDisponibles. No se declaran propiedades required en el esquema.

| Recurso / situación | data | HTTP |
|---|---|---:|
| CarteraProyectos o ConsultorasAmbientales exitoso | Array con nombres oficiales del DTO, distintos del modelo público camelCase del dashboard/consultoras | 200 |
| Array oficial vacío | [] | 200 |
| Recurso heredado admitido | Objeto opaco, semántica externa no verificada | 200 |
| Formato inválido | null explícito | 502 |
| Indisponibilidad/configuración/circuito abierto/recurso no admitido/parcial | null explícito | 503 |

El genérico no aplica el default anual 2017 a proyectos. data es flexible y nullable en el contrato; genera JsonNullable<Object> en Java. React recibe JSON normal, no debe implementar el envoltorio interno JsonNullable ni esperar `{present:true}`.

**Implicación React:** tratar data como unknown y discriminar por recurso/estado/forma, nunca como any confiable. Los arrays del genérico usan nombres oficiales como ID/ACTIVIDAD/FECHA_INICIO. El dashboard y consultoras son las rutas preferibles para las vistas tipadas; el genérico puede servir para un explorador sin filtro anual, con adaptador explícito y límites de renderizado.

### 4.4. GET /api/estado-servicios

Referencias: OpenAPI línea 37; controlador línea 155; modelo línea 467 del contrato.

Sin parámetros. HTTP 200 con EstadoServiciosResponse: senace y bcrp son objetos descritos, pero no tienen todas sus propiedades tipadas individualmente en OpenAPI.

El controlador publica servicio, estado, tasaFallosPorcentaje, llamadasFallidas, llamadasExitosas, respaldoLocal, alcanceMetricas y disponibilidadExterna. Su significado es el estado/métricas de circuitos y llamadas lógicas. respaldoLocal=NO_IMPLEMENTADO; alcanceMetricas=LLAMADAS_LOGICAS; disponibilidadExterna=NO_VERIFICADA.

El endpoint no consulta proveedores. Fallos de configuración pueden formar parte de sus métricas; un circuito cerrado no garantiza que la red oficial esté disponible.

**Implicación React:** el monitor debe rotular circuitos/métricas, no declarar un proveedor disponible por inferencia. Como el esquema es flexible, usar un adaptador con validación de forma o proponer posteriormente tipado aditivo. Esta mejora no bloquea el diseño visual.

## 5. Configuración y seguridad

### SENACE

[application.yml](../../backend/src/main/resources/application.yml), líneas 8–9, mapea explícitamente:

```text
SENACE_BASE_URL → external.apis.senace-base-url
SENACE_AUTH_KEY → external.apis.senace-auth-key
```

Los defaults son vacíos, no credenciales. Sin configuración el transporte no realiza HTTP y devuelve configuración ausente. El dashboard mantiene BCRP y responde degradado; consultoras/genérico responden 503 conforme a política.

backend/.env.apis.local está definido como archivo local ignorado, pero **Spring Boot no lo importa automáticamente**. El lector del test 3C.2 es una utilidad dentro de ese test, no una implementación de arranque productivo. Antes de integración real se necesita un procedimiento aprobado que inyecte solamente esas claves en el proceso Java o una alternativa explícita de configuración.

No se leyeron valores de credenciales durante esta revisión. No se debe trasladar auth_key a React ni a variables públicas del bundle; el navegador consume EcoInvest, no autentica directamente contra SENACE.

### BCRP

[BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java), línea 42, utiliza external.apis.bcrp-base-url con default oficial. No necesita una API key para las series públicas verificadas. application.yml no contiene un mapeo explícito BCRP_BASE_URL equivalente al de SENACE; no asumir que una clave con ese nombre en un .env tiene efecto productivo.

No es necesario tocar BCRP para iniciar frontend; conservar su catálogo, metadatos y ausencia honesta.

### Logs, errores y CORS

- Los clientes conservan categorías/motivos seguros y no deben publicar URL autenticada o cuerpos externos. Las pruebas anteriores verifican caminos de sanitización concretos, no una auditoría de seguridad integral de despliegue.
- application.yml líneas 15–16 desactiva logs Apache susceptibles de registrar URIs. Mantener esta protección al crear perfiles.
- El controlador línea 47 permite origins="*" y línea 109 expone los cinco headers SENACE en consultoras. No se habilitan credenciales de navegador por esa anotación.
- CORS permite desarrollar React en otro origen con los GET actuales; su comportamiento MockMvc fue probado. La configuración real de proxy/HTTPS/orígenes del despliegue aún requiere verificación.
- Restringir orígenes y revisar política de exposición antes de despliegue. No es un bloqueo para trabajar con mocks ni autorización para modificar ahora CORS.

### Proceso local y nube

El frontend necesita solamente la URL de EcoInvest, gestionada como configuración pública de su herramienta de construcción futura. El backend recibe sus variables de proveedor mediante configuración de proceso/IDE o mecanismo del alojamiento, sin empaquetar archivos locales.

No se eligió proveedor cloud ni mecanismo de secretos concreto. La configuración local debe evitar valores en argumentos visibles, historial compartido, logs o repositorio. No se propone importación automática nueva sin revisar rutas desde backend/, raíz e IDE.

## 6. Riesgos de ejecución

### SQLite/JPA: riesgo operativo confirmado

application.yml declara datasource SQLite y `spring.jpa.hibernate.ddl-auto: update` en líneas 21–27. Un arranque general puede abrir o modificar una base relativa al directorio de ejecución. La existencia de dependencias/archivos no demuestra un respaldo funcional: el monitor declara NO_IMPLEMENTADO.

Solo se encontraron application.yml y openapi.yaml como archivos directos en backend/src/main/resources durante la inspección; no existe allí un perfil de desarrollo seguro independiente ya preparado. No arrancar Spring Boot para comprobar incidentalmente React sin resolver ese aislamiento.

Propuesta futura: perfil explícito de desarrollo/integración o contexto de ejecución acordado que no autoconfigure datasource/JPA. **Cambiar ddl-auto a none por sí solo no impide abrir una conexión**. La solución deberá verificarse mediante ausencia de infraestructura persistente, no solo por una bandera aparente.

### Volumen y latencia

No hay paginación pública documentada para consultoras ni para el genérico. Un montaje de componentes o cambios rápidos de filtros pueden multiplicar consultas costosas. Diseñar solicitudes bajo demanda, cancelación de resultados obsoletos y evitar polling automático de datasets completos. El monitor no consulta proveedores y tiene otra naturaleza.

No se introdujo caché o persistencia por esta revisión. La secuencia del dashboard y sus tiempos no se midieron end-to-end; el frontend debe soportar latencia y degradación sin confundir un timeout propio con ausencia oficial.

### Precisión Java → JavaScript

El contrato utiliza id int64 y números JSON decimales preservados como BigDecimal en Java. `JSON.parse` convencional transforma números a number y puede perder enteros mayores que Number.MAX_SAFE_INTEGER o precisión decimal. Java correcto no implica precisión arbitraria en TypeScript.

Para la primera UI: mantener RUC como string, no efectuar cálculos económicos exactos sobre doubles y no afirmar identidad exacta de un int64 fuera del rango seguro. Antes de una funcionalidad que requiera enteros grandes exactos debe acordarse una estrategia de parser/representación, o un cambio de contrato expresamente autorizado. No se propone cambiar el ID existente a string incidentalmente. En los registros reales actuales no se documentó aquí un máximo de ID que permita descartar ese riesgo general.

## 7. Pendientes bloqueantes y no bloqueantes

| Pendiente | Clasificación | Acción mínima |
|---|---|---|
| Ausencia de un defecto productivo demostrado en lo cubierto | No hay bloqueo demostrado para iniciar frontend con mocks | No reimplementar lotes |
| Definir alcance inicial de las vistas y estrategia de tipos/adaptadores | BLOQUEANTE PARA FRONTEND únicamente como tarea de arranque del propio frontend | Encargo explícito de diseño/implementación; no exige corregir backend |
| Carga segura de configuración del proceso Java | RECOMENDABLE ANTES DE INTEGRACIÓN REAL; necesaria para operar SENACE | Diseñar/instruir inyección explícita, sin asumir .env automático |
| Aislar SQLite/JPA del arranque de desarrollo | RECOMENDABLE ANTES DE INTEGRACIÓN REAL; condición de seguridad para autorizar ese arranque sin DB | Perfil/procedimiento acotado y comprobación de aislamiento |
| HTTP local de cuatro rutas en proceso real | RECOMENDABLE ANTES DE INTEGRACIÓN REAL | Presupuesto propio, proveedor controlado o real según necesidad y permiso |
| Seleccionar estrategia de precisión para un requerimiento exacto | Condicional a esa funcionalidad, no bloqueo universal visual | Resolver antes de usar IDs grandes/cálculos exactos |
| Tipado exhaustivo del monitor y data del genérico | No bloqueante | Adaptadores frontend; cambio aditivo futuro solo si se justifica |
| Documentar 400 por sector omitido en OpenAPI | Mejora contractual no bloqueante | Futuro cambio mínimo autorizado; enviar siempre sector desde UI |
| Actualizar índice documental histórico | Continuidad, no bloqueo funcional | Autorización documental independiente |
| Orígenes CORS finales, HTTPS/proxy y configuración de alojamiento | POSPONIBLE HASTA DESPLIEGUE | Revisar antes de publicar |
| Persistencia, deduplicación de consultoras, región o vigencia derivadas | Fuera del cierre mínimo; no necesarias para UI honesta | No añadir reglas ausentes |

No se requiere volver a consultar SENACE/BCRP para comenzar componentes con mocks basados en el contrato. No convertir una validación end-to-end pendiente en impedimento para todo el desarrollo visual.

## 8. Matriz de preparación para frontend

| Área | Evaluación | Condición / pendiente |
|---|---|---|
| 1. Estabilidad de endpoints | Preparado en alcance implementado/probado | Mantener cuatro GET y formas raíz |
| 2. Contrato OpenAPI disponible | Preparado | Fuente vigente y generación Java ya acreditada |
| 3. Tipos TypeScript | Viable con límites | Propiedades mayormente opcionales, int64/decimales y objetos flexibles requieren adaptación |
| 4. Errores 200/502/503 | Preparado | 200 degradado en dashboard; 502/503 arrays/envolvente según ruta |
| 5. Datos ausentes | Preparado | No convertir omitido/null a cero o lista oficial vacía |
| 6. Metadatos SENACE/BCRP | Preparado | Procedencia independiente, cobertura NO_VERIFICADA, ámbitos económicos diferenciados |
| 7. CORS | Acreditado con MockMvc | Navegador/despliegue real pendiente; wildcard a revisar antes de publicar |
| 8. Configuración local | Pendiente operativo acotado | Test real leyó config, Spring productivo no la importa automáticamente |
| 9. SQLite | Riesgo de arranque pendiente | No bloquea mocks; exige aislamiento antes de levantar backend sin DB |
| 10. Pruebas adicionales | No necesarias para comenzar mocks | Prueba operativa posterior con permisos; no repetir suites por rutina |

Conclusión: **apto para iniciar React con contratos/mocks; no declarar listo para operación integral o despliegue**.

## 9. Recomendación de cierre del backend y plan mínimo

### Cierre que sí se recomienda

Dar por concluida la implementación prevista y sus verificaciones aisladas/reales del cliente en los alcances ya documentados. Conservar el cierre BCRP y las decisiones SENACE. No se demostró una necesidad de modificar SenaceClient, BcrpClient, EcoInvestController u OpenAPI para desbloquear el comienzo de React.

### Trabajo operativo mínimo independiente

1. Diseñar configuración temporal de proceso y aislamiento JPA/SQLite sin ejecutar todavía el backend.
2. Identificar un perfil/procedimiento acotado, justificar archivos y pedir permiso antes de crearlos/modificarlos.
3. Proponer una validación local de arranque/rutas con presupuesto y sin credenciales en salidas. Para pruebas de contrato, preferir transporte controlado; para proveedor real, justificar GET estrictamente necesarios y pedir aprobación por recurso.
4. Comprobar puerto/origen/header/serialización y ausencia de persistencia, sin cambiar BCRP o repetir su certificación.
5. Documentar el resultado como validación operativa específica, sin atribuir a un recurso el éxito de toda la aplicación.

Esta futura prueba debe distinguir: arranque de proceso, HTTP local, tráfico externo y potencial acceso DB. Cada uno conserva sus permisos, no queda autorizado por escribir el plan.

### Archivos que podrían requerirse en una fase futura

| Archivo / área | Motivo y condición |
|---|---|
| backend/src/main/resources/application-dev.yml, nombre propuesto | Perfil seguro para desarrollo sin persistencia, si se aprueba esa alternativa |
| backend/src/main/resources/application.yml | Solo si se demuestra necesaria una separación de configuración; no tocar por rutina |
| Un procedimiento de lanzamiento bajo documentación, o auxiliar expresamente aprobado | Inyección temporal de configuración y directorio reproducible; no volcar claves |
| backend/src/test/java/com/ecoinvest/soa/integration/… | Prueba operativa adicional solo tras definir alcance/archivo exacto |
| backend/src/main/resources/openapi.yaml | Opcional para 400 y tipado aditivo del monitor; no bloqueo ni permiso de edición actual |
| documentacion/README.md | Actualización de estados y enlaces con autorización propia |
| frontend/ | Solo bajo encargo posterior de React; actualmente intacto |

No se propone modificar POM, dependencias, paquetes, Java, BCRP, Resilience4j o SQLite para el cierre mínimo.

## 10. Propuesta de arquitectura frontend

Tecnologías aprobadas como objetivo: React + TypeScript. No se eligieron ni instalaron router, librería de tablas, mapa, estado, generación o HTTP. Las decisiones de herramientas se justificarán cuando se encargue la implementación.

Estructura conceptual, **no creada**:

```text
frontend/src/
  app/                   # composicion de pantallas y configuracion publica
  api/
    client.ts            # URL EcoInvest, status, headers, parseo y cancelacion
    generated/           # tipos desde OpenAPI si se autoriza una herramienta
    adapters/            # desconocidos, opcionales, metadatos y formas por recurso
  features/
    dashboard/
    explorador/
    consultoras/
    monitor/
  components/            # filtros, estados, tablas y presentacion compartida
  mocks/                 # escenarios artificiales claramente identificados
```

### Cliente y tipos

- Cliente reutilizable con fetch del navegador como alternativa inicial sin dependencia nueva; la elección definitiva corresponde al encargo frontend.
- Conservar código HTTP y headers antes de interpretar cuerpos. Un 502/503 de consultoras tiene JSON array útil para la forma pero no contiene datos oficiales válidos.
- Gestionar AbortController y resultados obsoletos; cancelar en navegador no garantiza detener la petición del proveedor ya iniciada.
- Generar tipos desde OpenAPI cuando se apruebe una herramienta adecuada y disponible. La generación no valida JSON en runtime ni resuelve automáticamente precisión numérica.
- Si no se autoriza generación inicial, definir un subconjunto TypeScript alineado con contrato y trazabilidad; evitar tipos duplicados divergentes.
- No usar campos opcionales como obligatorios ni convertir data unknown en estructura conocida sin discriminar.

### Vistas y consumo propuesto

| Vista | Fuente principal | Comportamiento honesto |
|---|---|---|
| Dashboard Analítico | /api/dashboard?sector=…&anio=… | Conteos de registros, año de inicio, indicador con ámbito/unidad/período y avisos de degradación |
| Explorador Geográfico | proyectos del dashboard para selección filtrada; genérico CarteraProyectos solo si se aprueba una vista sin filtro anual | Puntos disponibles, omitir coordenadas ausentes, no inventar región/CRS; no sumar peticiones duplicadas si ya hay datos utilizables |
| Consultoras Ambientales | /api/consultoras | Ocho campos, filas conservadas, filtros visuales y fechas originales; no empresas únicas/estado derivado |
| Monitor SOA | /api/estado-servicios | Circuitos y métricas lógicas; disponibilidad externa no verificada y respaldo no implementado |

### Estados compartidos

```text
loading  → solicitud en curso
ready    → datos utilizables
empty    → selección conocida vacía; distinguir vacío oficial de sin coincidencias
degraded → proveedor/indicador parcial o ausente con otra información utilizable
error    → respuesta no utilizable; preservar código y motivo seguro
```

La procedencia y cobertura deben acompañar los datos. No presentar mocks como observaciones oficiales ni reutilizar valores económicos históricos como datos actuales fijos. Mostrar ausencia como “no disponible” cuando corresponda y no como cero.

### Integración progresiva

1. Acordar contrato/adaptadores y navegación visual de las cuatro vistas.
2. Construir componentes con fixtures artificiales mínimos para éxito, vacío, error y degradación.
3. Resolver configuración/arranque seguro del backend antes de sustituir mocks por HTTP local.
4. Conectar una vista por vez con presupuesto de proveedores y solicitudes bajo demanda.
5. Revisar rendimiento de tablas/mapa antes de añadir dependencias por volumen; no introducir una nueva API paginada por suposición.
6. Planificar despliegue y CORS final como etapa explícita posterior.

## 11. Siguiente fase recomendada

Separar dos líneas de trabajo que no deben confundirse:

- **Preparación operativa local del backend**, si se desea conectar React a un proceso real: configuración temporal, aislamiento de persistencia y diseño de prueba HTTP acotada antes de ejecutar.
- **Inicio de React + TypeScript con contrato y mocks**, que puede comenzar sin esa certificación operativa, previa autorización de archivos y herramientas.

No se asigna automáticamente un número de nueva fase funcional. El siguiente prompt debe elegir su objetivo y alcance concreto, leer los cierres 3C.1/3C.2 y este informe, y solicitar solo los permisos que realmente requiera. No volver a implementar los cuatro lotes ni a consultar SENACE/BCRP para reconstruir información ya acreditada.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest es una integración SOA SENACE+BCRP con Java 17, Oracle JDK 17.0.12, Spring Boot 3.2.4, OpenAPI y Resilience4j. El frontend React + TypeScript sigue pendiente. BCRP Fase 2C está cerrado en su alcance técnico y no debe repetirse sin nueva necesidad. SENACE Fase 3B.2 está implementada en cuatro lotes; las decisiones D01–D19 están aprobadas.

Fase 3C.1 verificó 32 casos integrados MVC → controlador → SenaceClient real/proxy AOP → transporte simulado → Jackson/DTOs/mapper → modelos públicos. Fase 3C.2 ejecutó dos consultas independientes con transporte/cliente Java reales, sin contexto productivo: 2.567 filas de proyectos y 15.015 de consultoras procesadas satisfactoriamente. No sumar esas pruebas como una suite conjunta. No existe una ejecución única MVC + ambos proveedores reales + arranque productivo certificada end-to-end.

La revisión estática 3C.3 concluye que se puede iniciar React con contratos y mocks. No hay un defecto productivo demostrado que obligue a rehacer backend. Las cuatro rutas GET se conservan: dashboard con sector obligatorio/anio default2017 y HTTP200 incluso degradado; consultoras array con ocho campos opcionales y 200/502/503, metadatos en headers; genérico con nombre opcional, envolvente data array/objeto/null y 200/502/503; monitor sin consultas externas, con métricas de circuitos y disponibilidad externa NO_VERIFICADA.

Dashboard distingue ausencia de métricas de cero y mantiene BCRP independiente cuando SENACE falla. Año corresponde a FECHA_INICIO, nunca fecha de aprobación. Consultoras no tiene RUC/ID/registro/nivel/estado inventados ni deduplicación/vigencia derivada. Los seis recursos heredados del genérico no están oficialmente validados. El genérico usa nombres oficiales del DTO; las vistas tipadas usan modelos públicos camelCase. El monitor no certifica salud externa.

Contrato OpenAPI disponible y generado; la mayoría de propiedades no tiene required, por lo que TypeScript debe admitir opcionales. data genérico es flexible, no JsonNullable visible al navegador. Int64 y BigDecimal de Java no garantizan precisión arbitraria en JSON.parse/number: definir estrategia si se requieren IDs grandes o cálculos exactos. RUC debe mantenerse string. La falta de tipado exhaustivo del monitor y de respuesta 400 documentada por sector omitido son mejoras no bloqueantes.

Pendientes operativos reales: application.yml mapea SENACE_BASE_URL/SENACE_AUTH_KEY con defaults vacíos, pero no importa backend/.env.apis.local. El lector de 3C.2 no prepara el proceso Spring productivo. Configuración SQLite/JPA y ddl-auto:update permanece activa; antes de arrancar backend para React debe diseñarse aislamiento efectivo, no basta poner ddl-auto:none. No existe perfil dev independiente en los recursos inspeccionados. BCRP utiliza propiedad external.apis.bcrp-base-url con default oficial; no asumir que BCRP_BASE_URL en un .env se mapea por existir. No se necesita una API key BCRP pública.

CORS actual origins=* y headers SENACE expuestos permite preparación local, con evidencia MockMvc. Verificar navegador/entorno y restringir orígenes antes del despliegue. No enviar auth_key al frontend. Índice documentacion/README.md está atrasado respecto a los reportes nuevos; no se actualizó sin autorización.

Siguiente prompt puede elegir diseño/inicio frontend con mocks, o preparación operativa local del backend antes de integración real. Archivos futuros posibles: perfil application-dev.yml/procedimiento de lanzamiento seguro, test operativo expresamente definido e índice documental; son propuestas, no permisos. No hay necesidad demostrada de cambiar clientes/controlador/OpenAPI ahora. Mantener intactos Java, BCRP, Resilience4j, SQLite y contratos; cualquier arranque, HTTP, Maven, archivo o cambio requiere el alcance autorizado correspondiente. Este reporte no inicia nuevas fases.
