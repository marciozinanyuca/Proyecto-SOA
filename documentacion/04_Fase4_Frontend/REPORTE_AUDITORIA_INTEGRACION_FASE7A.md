# EcoInvest — Fase 4B, Lote 7A: auditoría de integración frontend–backend

Fecha: 07/11/2026. Alcance: inspección estática y planificación.

**No se implementaron cambios operativos ni se arrancaron servicios.** La autorización recibida permite crear únicamente este reporte. No aprueba automáticamente el perfil propuesto, la conexión HTTP o las etapas 7B–7D.

## 1. Resumen ejecutivo

Las cuatro vistas React están implementadas y validadas localmente con mocks explícitos. El backend tiene las cuatro operaciones contratadas e integraciones SENACE/BCRP ya implementadas, con validaciones históricas aisladas y de clientes reales. Falta comprobar su operación conjunta por HTTP desde React.

Dos cambios son necesarios antes de esa conexión:

1. Un arranque backend aislado de DataSource/JPA/SQLite. La configuración normal sigue declarando SQLite y `ddl-auto: update`; cambiar únicamente el DDL a none no evita abrir la base.
2. Selección central y explícita de fuente frontend. Actualmente cada modelo construye un cliente mock y las vistas contienen etiquetas/escenarios de demostración. Configurar solo VITE_API_BASE_URL no las conecta.

Se recomienda empezar la conexión real exclusivamente por Monitor: `/api/estado-servicios` lee circuitos internos y no consulta proveedores. Consultoras y Dashboard/Explorador deben habilitarse posteriormente con autorizaciones y presupuesto de red propios.

## 2. Fuentes y frontera de evidencia

- [Cierre backend 3C.3](../03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md).
- [Lote 2: capa API](REPORTE_IMPLEMENTACION_FASE4B_LOTE2.md).
- [Lote 6: Monitor y última suite frontend](REPORTE_IMPLEMENTACION_FASE4B_LOTE6.md).
- [Revisión Stitch](REPORTE_REVISION_STITCH_FASE4A.md).
- [Lote 4: Explorador](REPORTE_IMPLEMENTACION_FASE4B_LOTE4.md).
- [Lote 5: Consultoras](REPORTE_IMPLEMENTACION_FASE4B_LOTE5.md).
- [Contrato OpenAPI](../../backend/src/main/resources/openapi.yaml).

También se inspeccionaron POM, configuración, aplicación principal, controlador, clientes, tests de aislamiento/CORS, adaptadores, modelos de vista y configuración Vite. Se usaron lecturas y búsquedas locales; no Maven, pruebas, HTTP o arranque durante 7A. No se leyeron archivos .env ni valores de credenciales. La línea de credencial de application.yml se omitió de la salida de inspección.

Los reportes históricos acreditan sus propias ejecuciones. No se repitieron en esta auditoría ni se suman como una suite nueva. El índice documental conserva referencias antiguas: no debe prevalecer sobre cierres posteriores ni actualizarse sin autorización.

## 3. Estado confirmado de las cuatro vistas

| Vista | Implementación actual | Conexión productiva pendiente |
|---|---|---|
| Dashboard | Filtros de demostración, tres KPI, distribución por estado, tabla y metadatos | Sustituir carga mock por fuente HTTP autorizada; ampliar tipos de consulta sin trasladar restricciones artificiales al contrato |
| Explorador | Plano SVG offline, filtros, lista y ficha, fixtures geográficos separados | Reutilizar proyectos de una selección HTTP; nunca completar coordenadas reales con fixtures |
| Consultoras | Ocho campos, filtros locales, diez filas por página, detalle y headers | Consultar array/headers del backend y verificar CORS con navegador |
| Monitor | Dos tarjetas, actualización manual, snapshot, límites y hora del cliente | Primera vista candidata a HTTP local, sin proveedor externo |

La última ejecución frontend documentada terminó con **98 pruebas aprobadas, typecheck y build correctos**, seguida de revisión Edge local en tres resoluciones. Esta evidencia corresponde a mocks y lógica frontend, no a operación HTTP con Spring Boot.

## 4. Riesgo de arranque backend

### 4.1. Configuración vigente

[application.yml](../../backend/src/main/resources/application.yml) declara:

- Puerto 8081.
- DataSource `jdbc:sqlite:ecoinvest_fallback.db` y driver SQLite.
- Dialecto Hibernate SQLite.
- `spring.jpa.hibernate.ddl-auto: update`.
- Configuración SENACE mediante variables del entorno.
- Logging Apache HttpClient desactivado para evitar divulgación de URIs autenticadas.
- Circuit breakers senace/bcrp con ventana 5, umbral 50 % y espera 10 s.

[pom.xml](../../backend/pom.xml) mantiene Java 17, Spring Boot 3.2.4, starter-data-jpa, sqlite-jdbc 3.45.2.0, Hibernate community dialects, Web, Actuator, Resilience4j y Apache HttpClient 5. OpenAPI Generator 7.4.0 genera interfaces/modelos durante Maven.

El archivo de base indicado es relativo al directorio de trabajo. Un arranque accidental desde raíz, backend o IDE podría abrir o crear una base en ubicaciones distintas. No se inspeccionó ni se abrió ninguna base para esta auditoría.

### 4.2. Dependencias reales de persistencia

No se localizaron en `backend/src/main` entidades, repositorios JPA, JdbcTemplate, EntityManager, DriverManager o componentes que necesiten persistencia. El controlador depende de SenaceClient, BcrpClient y CircuitBreakerRegistry. AppConfig proporciona RestTemplate y JsonNullableModule; los clientes usan transportes HTTP/Jackson.

La aplicación principal utiliza SpringBootApplication sin exclusiones productivas. Por tanto, las dependencias en el POM pueden activar autoconfiguración de persistencia aunque los endpoints no consulten una base. La ausencia de entidades no acredita ausencia de conexión.

No se identificaron runners o tareas de inicialización que ejecuten llamadas a proveedores en el código productivo buscado. Los constructores inspeccionados configuran dependencias; el HTTP se inicia al invocar las operaciones de cliente. Esto requiere corroboración en la futura validación de arranque, no se declara probado aquí.

## 5. Estrategia de aislamiento SQLite/JPA

### Propuesta para 7B

Crear `backend/src/main/resources/application-integracion-local.yml` con:

```yaml
server:
  address: 127.0.0.1

spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
      - org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
```

No es un archivo creado ni una configuración ya validada. Mantiene puerto y reglas HTTP existentes; no elimina dependencias del POM ni modifica Resilience4j.

Esas mismas exclusiones ya aparecen en [SoaApplicationTests.java](../../backend/src/test/java/com/ecoinvest/soa/SoaApplicationTests.java:7). Su presencia acredita un patrón existente, no la suficiencia del futuro perfil bajo todas las condiciones del proceso.

No se propone confiar en `ddl-auto: none` como barrera. El control principal es impedir crear DataSource y componentes JPA. Las autoconfiguraciones JDBC/transaccionales adicionales deben evaluarse por sus condiciones: sin DataSource normalmente no aplican, pero la aceptación depende de comprobar los beans efectivos. Un DataSource manual o autoconfiguración añadida posteriormente invalidaría esa suposición.

### Prueba aislada propuesta

Crear `backend/src/test/java/com/ecoinvest/soa/config/IntegracionLocalProfileTest.java` para cargar el recurso/perfil real, sin copiar las exclusiones únicamente a propiedades de test y aparentar validar el perfil. Debe comprobar:

- Perfil efectivo y exclusiones cargadas.
- Ausencia de DataSource, EntityManagerFactory, EntityManager y repositorios persistentes.
- Clientes, módulos Jackson, registro de circuitos y controlador disponibles sin dependencia de base.
- Ausencia de llamadas al transporte SENACE/BCRP durante creación del contexto y consulta de Monitor, con dependencias externas controladas.
- Compatibilidad de las cuatro operaciones mediante MVC aislado, sin red oficial.

Después podrá autorizarse un arranque local y verificar loopback, cuatro rutas y ausencia de infraestructura persistente. Ningún test debe abrir SQLite para comprobar que no se modifica. Debe descartarse o sanearse también cualquier override de configuración que reintroduzca DataSource; no imprimir el entorno completo ni secretos para revisarlo.

## 6. Configuración y credenciales

| Variable o propiedad | Estado actual | Recomendación |
|---|---|---|
| SENACE_BASE_URL | Mapeada a external.apis.senace-base-url | Entorno temporal del proceso Java; dominio oficial validado por el transporte |
| SENACE_AUTH_KEY | Mapeada a external.apis.senace-auth-key | Solo backend; no mostrar valor, argumentos o URLs autenticadas |
| backend/.env.apis.local | No se importa automáticamente por Spring | No asumir carga; lectura posterior solo con autorización concreta |
| BCRP_BASE_URL | No existe mapeo explícito con ese nombre en YAML | No afirmar que cambiar esta variable cambia el cliente |
| external.apis.bcrp-base-url | BcrpClient la consume con default oficial | Mantener default; si se necesita override, usar propiedad Spring explícita autorizada |
| VITE_API_BASE_URL | Configuración pública declarada | URL del backend, nunca URL autenticada o token SENACE |

Evidencia: SenaceTransport constructor líneas 118–120; BcrpClient @Value línea 42. El transporte SENACE valida HTTPS/host oficial, mantiene rutas verificadas sin /data.json, límite 6 MiB, conexión 5 s, inactividad 10 s, plazo total 25 s, sin redirecciones o reintentos automáticos.

Para el primer arranque/Monitor no se necesita suministrar credenciales SENACE. Su ausencia permite construir el transporte; una futura operación SENACE devuelve configuración ausente en lugar de bloquear BCRP. No deben probarse recursos para comprobar la credencial durante el arranque.

### Procedimiento futuro seguro

1. Seleccionar Oracle JDK 17.0.12 solo en el proceso de prueba y comprobar Java/Maven con autorización.
2. Para Monitor, mantener las credenciales sin cargar y autorizar solo tráfico local.
3. Antes de recursos SENACE, pedir autorización específica de lectura de las claves necesarias y GET presupuestados.
4. Si se usa archivo local, emplear un parser de asignaciones limitado a SENACE_BASE_URL y SENACE_AUTH_KEY: nunca dot-sourcing, Invoke-Expression, ejecución del archivo o volcado de valores.
5. Pasar las variables al proceso hijo Java; no persistirlas en Windows ni ampliarlas a Vite. Restaurar el entorno del lanzador al finalizar.
6. No registrar headers de autenticación, rawText, cookies o URIs con auth_key.

Si se decide automatizar el lanzamiento, proponer por separado `backend/Iniciar-IntegracionLocal.ps1`, con validación de claves, perfil y cierre/restauración del entorno. No se crea por este reporte. Alternativamente, usar variables ya presentes en una sesión temporal, verificando solo presencia, sin instalar herramientas o introducir importación Spring nueva.

## 7. Contratos HTTP y adaptadores

### 7.1. GET /api/estado-servicios

- Parámetros: ninguno.
- Respuesta: objeto EstadoServiciosResponse con senace/bcrp; HTTP documentado 200.
- Campos internos observados: servicio, estado, tasaFallosPorcentaje, llamadasFallidas, llamadasExitosas, respaldoLocal, alcanceMetricas, disponibilidadExterna.
- Adaptador: [monitor.ts](../../frontend/src/api/adapters/monitor.ts).
- Sin headers de procedencia SENACE específicos requeridos. El endpoint no realiza health checks externos.
- CLOSED/OPEN/HALF_OPEN/DISABLED no acreditan disponibilidad del proveedor. Tasa −1 no es 0 %.
- OpenAPI describe los objetos, pero no tipa cada propiedad interna; el adaptador actual valida los campos conocidos.
- Un objeto incompleto se clasifica degraded; no se inventa estado empty.
- Pruebas futuras: GET local con CORS, números opcionales y sentinel −1; ningún cliente externo invocado.

### 7.2. GET /api/consultoras

- Parámetros: ninguno.
- Array raíz de ocho campos camelCase opcionales; no RUC, ID oficial, nivel o estado.
- HTTP: 200 para datos/vacío oficial, 502 formato inválido, 503 indisponibilidad/configuración/circuito/otras ausencias.
- Headers: X-Senace-Origen, X-Senace-Estado, X-Senace-Cobertura y opcionales X-Senace-Motivo/X-Senace-Cuerpo-Completo.
- Adaptador: [consultoras.ts](../../frontend/src/api/adapters/consultoras.ts).
- Los errores conservan [] pero no representan cero oficial. Un [] sin headers suficientes se clasifica degradado.
- Riesgo práctico: official() exige cuerpoCompleto=true; debe comprobarse que el navegador puede leer X-Senace-Cuerpo-Completo, además de los tres headers básicos.
- Pruebas futuras: 200 con filas/vacío, 502/503, headers visibles, campos omitidos, duplicados y fechas originales. No reproducir errores provocando múltiples GET reales.

### 7.3. GET /api/dashboard

- sector string obligatorio; anio integer opcional, default 2017. Año de FECHA_INICIO, no aprobación.
- Objeto DashboardResponse: total, aprobados, proyectos, variacionSeleccionada, fuentes, indicadorBcrp y consultaSenace, con opcionales.
- HTTP documentado 200 incluso degradado; parámetros inválidos pueden producir 400 de MVC, no enumerado expresamente en ese contrato.
- SENACE no disponible omite conteos/lista; SENACE vacío o filtros sin coincidencias mantienen ceros conocidos y metadatos diferenciados.
- El controlador continúa consultando BCRP aunque falle SENACE. El valor económico no se sustituye por constante.
- Adaptador: [dashboard.ts](../../frontend/src/api/adapters/dashboard.ts).
- Debe preservarse coherencia variacionSeleccionada/indicadorBcrp.valor y procedencia; 200 no basta para afirmar disponibilidad.
- Los adaptadores rechazan null en campos no declarados nullable. El contrato tiene anotaciones NON_NULL para opcionales y los modelos generados inspeccionados reflejan esas anotaciones. La serialización real por HTTP sigue por verificar, sin editar target/.
- IDs int64 inseguros pueden ser rechazados por frontend; BigDecimal JSON usa number aproximado en JavaScript. No recuperar precisión perdida mediante conversiones posteriores ficticias.
- Pruebas futuras: ausencia/omisión, JSON, conteos y filtros, metadatos económicos, degradación independiente y error de parámetros.

### 7.4. GET /api/senace/datastream

- nombre opcional, default CarteraProyectos.
- Objeto envolvente con datastream, origen, data, consulta y recursoValidado, además de mensajes/campos opcionales del contrato.
- HTTP 200/502/503. En error data debe ser null explícito.
- Para CarteraProyectos/ConsultorasAmbientales, data es array con nombres DTO oficiales; no se interpreta como ProyectoSenacePublico directamente.
- Otros seis recursos conservan esquemas heredados opacos como objeto, sin considerarlos validados oficialmente.
- No aplica el filtro anual predeterminado del Dashboard a la consulta genérica.
- Adaptador: [datastream.ts](../../frontend/src/api/adapters/datastream.ts).
- JsonNullableModule está configurado en AppConfig; probar por HTTP que data:null y array/objeto se serializan como se espera.
- No existe una pantalla genérica adicional: la comprobación puede hacerse en prueba de integración seleccionada. No consultar los seis recursos heredados por rutina.

## 8. CORS y seguridad HTTP local

El controlador tiene `@CrossOrigin(origins = "*")`, línea 47. Consultoras añade exposedHeaders para los cinco metadatos, líneas 116–117. Existen pruebas previas MVC de exposición de headers; no equivalen a una nueva comprobación de navegador contra servidor real.

Vite usa 127.0.0.1 y el backend puerto 8081. Son orígenes distintos. El cliente usa credentials:omit, redirect:error y Accept:application/json; no debe incluir tokens del proveedor. Utilizar consistentemente 127.0.0.1 evita diferencias accidentales respecto a localhost.

El wildcard actual no bloquea necesariamente la primera prueba local sin credenciales de navegador, pero requiere revisión antes de exposición a red/nube. Se propone bind loopback del backend como medida mínima de 7B. Endurecer CORS es una decisión adicional: una configuración WebMvc agregada no garantiza restringir el wildcard de la anotación, porque pueden combinarse configuraciones.

Si se aprueba restringir orígenes, proponer una política coherente y probarla, probablemente modificando EcoInvestController.java para externalizar/eliminar el wildcard y la configuración correspondiente. No ampliar métodos, orígenes o allowCredentials por conveniencia. No se propone modificar CORS sin ese alcance adicional.

## 9. Dónde se fuerza mock y cambios de fuente necesarios

| Ubicación | Comportamiento actual |
|---|---|
| dashboard/dashboardModel.ts:28 | createApiClient con mode:mock y fixture por sector/año/escenario |
| explorador/exploradorModel.ts:15–17 | Reutiliza carga Dashboard o fixture geográfico mock separado |
| consultoras/consultorasModel.ts:30 | createApiClient con mode:mock |
| monitor/monitorModel.ts:21 | createApiClient con mode:mock |
| app/config.ts | Solo declara VITE_API_BASE_URL; no selecciona modo ni conecta las vistas |
| api/client.ts | Ya soporta modo http con URL explícita; no hay fallback mock implícito |

### Diseño propuesto

- Añadir selección validada de fuente pública `VITE_API_MODE=mock|http` y habilitación progresiva explícita por pantalla, por ejemplo `VITE_HTTP_FEATURES=monitor`.
- El valor ausente puede conservar mock como configuración de desarrollo documentada. Un valor inválido debe producir error de configuración, no cambiar silenciosamente a mocks.
- En modo HTTP, una pantalla no habilitada debe permanecer bloqueada para consultas reales, con explicación. No cambiarla implícitamente a mock por error o permiso ausente. Si se desea mezclar fuentes por pantalla, debe declararse explícitamente la selección y sus etiquetas.
- Crear una pequeña fábrica de fuente que invoque createApiClient existente; no duplicar fetch, tipos, adaptadores o manejo de status.
- No iniciar HTTP solo por arrancar Vite. La habilitación de la vista y su operación manual deben formar parte del plan aprobado; el montaje de rutas no autorizadas no puede lanzar GET de proveedores.
- Primero Monitor; otras vistas no deben conectarse accidentalmente porque Dashboard sea la ruta inicial.
- Cambiar avisos, textos de HTTP simulado, selectores de fixture y modo mostrado según la fuente real. Nunca mostrar datos HTTP como artificiales o fixtures como oficiales.
- Conservar cancelación y descarte de respuestas obsoletas. No reutilizar la caché de demostración para HTTP sin política de frescura y claves que incluyan fuente/URL/consulta.

### Particularidades de Dashboard/Explorador

Los tipos actuales restringen sector/año a opciones de demostración y las vistas importan escenarios artificiales. Al habilitar HTTP, separar consulta real de esos unions y de los fixtures, preservando default 2017 y sectores aprobados sin inventar equivalencias SENACE/BCRP.

El Explorador debe compartir resultados efectivos de proyectos sin un endpoint cartográfico. El estado actual no ofrece sincronización bidireccional completa de filtros con Dashboard: debe diseñarse explícitamente antes de ampliar su comportamiento. Nunca aplicar el fixture coordinates a una respuesta HTTP real. El esquema SVG seguirá siendo offline y no certificará CRS; proveedor de tiles y cartografía base son decisiones independientes pendientes.

## 10. Plan por etapas, archivos y aceptación

### 7A — Auditoría y diseño

Único archivo creado: este reporte. Sin pruebas, servicios o conexiones. Las propuestas siguientes requieren autorización propia.

### 7B — Perfil backend y validación aislada

Archivos mínimos propuestos:

- Crear `backend/src/main/resources/application-integracion-local.yml`.
- Crear `backend/src/test/java/com/ecoinvest/soa/config/IntegracionLocalProfileTest.java`.
- Opcional, sujeto a decisión separada: `backend/Iniciar-IntegracionLocal.ps1` para configuración/lanzamiento seguro.

No modificar POM, clientes BCRP/SENACE, Resilience4j, OpenAPI o base. Validar primero sin credenciales y sin proveedores.

Aceptación: perfil real cargado, contexto sin infraestructura persistente, endpoints disponibles por MVC controlado, cero llamadas externas durante arranque/Monitor y configuración loopback. Un test que excluye persistencia solo por propiedades copiadas no acredita que el recurso de perfil sea correcto.

Reversión: detener procesos, desactivar el perfil nuevo y conservar cambios previos. Desactivar el perfil seguro no autoriza arrancar el perfil normal con SQLite. No borrar bases o usar git reset/checkout para revertir trabajo ajeno.

### 7C — Conexión HTTP progresiva

Primer sublote Monitor:

- Modificar `frontend/src/app/config.ts` y `frontend/src/vite-env.d.ts`.
- Crear `frontend/src/api/source.ts` y `frontend/tests/api/source.test.mjs` como propuestas de nombres para selección/fábrica, reutilizando client.ts.
- Modificar `frontend/src/features/monitor/monitorModel.ts`, `MonitorPage.tsx` y `frontend/tests/api/monitor.test.mjs`.
- Modificar package.json/configuración de pruebas solo si es necesario incorporar archivos nuevos.
- Aplicar en App.tsx una barrera mínima de consultas de otras rutas si el diseño central la requiere; presentar cambio concreto antes de editar.

Posteriores sublotes independientes:

- Consultoras: `consultorasModel.ts`, `ConsultorasPage.tsx`, `consultoras.test.mjs`.
- Dashboard/Explorador: sus modelos/vistas/tests, diseño de resultado compartido y selección efectiva. No adoptar demoSelectionStore como caché productiva por rutina.
- No modificar CSS o estilos aprobados salvo incompatibilidad demostrada y autorizada.

Aceptación: modo y URL validados, Monitor realiza solo GET local autorizado, otras pantallas no hacen llamadas accidentales, no fallback HTTP→mock, status y opcionales conservados, avisos coherentes. Cualquier persistencia del modo o cambio de diseño requiere alcance explícito.

Reversión: detener Vite y seleccionar explícitamente mock, sin fingir HTTP exitoso. Conservar código y evidencias; revertir un parche únicamente con autorización y sin descartar cambios preexistentes.

### 7D — Integración y evidencia

Proponer un test opt-in `frontend/tests/integration/backend-http.test.mjs` y, si procede, una prueba backend HTTP aislada adicional con transportes controlados. Los archivos exactos deben confirmarse antes de crearlos según las herramientas disponibles.

Primero errores/CORS/serialización con respuestas controladas; después, si se aprueba, presupuesto acotado de proveedor. Monitor no consume ese presupuesto. Una consulta Dashboard puede provocar un GET SENACE y uno BCRP; volver a pedir Dashboard para Explorador duplicaría trabajo si no se comparte el resultado. Consultoras implica un GET propio y puede descargar una respuesta grande.

Aceptación: cuatro rutas compatibles, headers legibles desde navegador, JSON y null/omisiones correctos, errores 200 degradado/502/503 distinguidos, ausencia de credenciales en cliente/evidencias, sin acceso SQLite y revisión responsive sin regresiones. No provocar errores reales con barridos o reintentos para cubrir casos que pueden simularse.

Reversión: cancelar prueba, detener procesos, mantener modo mock explícito y documentar fallos. No corregir automáticamente backend o políticas del proveedor durante la validación.

## 11. Comandos propuestos: no ejecutados en 7A

Desde backend/, en un proceso PowerShell temporal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o -DskipTests compile
.\mvnw.cmd -o "-Dtest=IntegracionLocalProfileTest" test
```

La ruta JDK es la utilizada anteriormente: reconfirmar que corresponde a Oracle 17.0.12 antes de ejecutar. La propiedad release 17 no prueba runtime 17. Maven puede generar OpenAPI y escribir target/; no tocar manualmente artefactos generados. Offline no garantiza que dependencias/plugins estén disponibles: si falta algo, detenerse y pedir autorización, no instalar o habilitar red automáticamente.

Después de aprobar perfil, pruebas y arranque, sin credenciales para Monitor:

```powershell
.\mvnw.cmd -o "-Dspring-boot.run.profiles=integracion-local" spring-boot:run
```

Este comando inicia servidor y puede escribir artefactos; no queda autorizado por documentarlo. Debe verificarse el perfil y ausencia de persistencia antes de utilizarlo. No incorporar tokens en argumentos.

Desde frontend/, cuando se aprueben cambios y validaciones:

```powershell
npm run typecheck
npm run test:api
npm run build
```

Para una sesión posterior de integración exclusivamente Monitor, tras implementar la configuración propuesta y recibir permiso:

```powershell
$env:VITE_API_MODE = 'http'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8081'
$env:VITE_HTTP_FEATURES = 'monitor'
npm run dev
```

Esos nombres adicionales son propuestas, no configuración que funcione hoy. Se deben definir/restaurar solo en el proceso temporal. No incluir credenciales. El GET local de Monitor puede ser `Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/estado-servicios' -Method Get`; comprobar CORS requiere además navegador/origen o una petición con Origin controlado. Ninguno se ejecutó en esta fase.

## 12. Riesgos y autorizaciones pendientes

| Riesgo o decisión | Clasificación / acción |
|---|---|
| Arranque normal abre o crea SQLite | Bloqueante antes de arrancar backend bajo la restricción actual; aprobar y verificar perfil |
| Las vistas fuerzan mock | Bloqueante para conexión real; aprobar fuente explícita central y cambios de etiquetas |
| Dashboard inicia al montar | Impedir GET de proveedores accidental al habilitar HTTP; empezar solo Monitor |
| CORS wildcard actual | Revisar para exposición fuera de loopback; no asumir que configuración adicional lo restringe |
| Headers SENACE no visibles en navegador | Validación necesaria para Consultoras; no declarar vacío oficial sin metadatos suficientes |
| Propiedades null frente a omitidas | Verificar serialización HTTP; no normalizar datos inventando valores |
| IDs/decimales JavaScript | Conservar advertencias y validación; rediseño de precisión exacta requiere alcance explícito |
| Caché/demo y filtros compartidos | No mezclar fuente real con fixtures o resultados obsoletos |
| Tráfico externo y volumen | Permisos por recursos y número de consultas; sin reintentos o descargas por asociación |
| Histórico/índice desactualizado | Reportes actuales prevalecen; actualización del índice no autorizada aquí |

La creación de este reporte no aprueba las decisiones técnicas como implementación ni otorga acceso a secretos, pruebas, arranque, HTTP, instalaciones o commits.

## 13. Siguiente paso recomendado

Preparar el encargo **7B: perfil integracion-local sin persistencia**, limitado inicialmente a los dos archivos backend propuestos. Revisar su diseño y solicitar permisos separados para creación, Maven offline y prueba aislada. Mantener Oracle JDK 17.0.12 y no cambiar las políticas Resilience4j o integraciones ya validadas.

Solo después de ese resultado, conectar Monitor en un sublote 7C. No intentar las cuatro vistas reales simultáneamente ni repetir las fases de proveedor cerradas sin evidencia nueva.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest tiene cuatro vistas React funcionales con mocks. La última validación frontend documentada tuvo 98 pruebas y build satisfactorios; sigue sin integración end-to-end con Spring Boot. El backend declara Java 17/Spring Boot 3.2.4 y puerto 8081. La configuración normal conserva SQLite y ddl-auto:update, aunque no se localizaron dependencias productivas de entidades/repositorios o JDBC. No arrancarlo con esa configuración bajo la prohibición de acceso SQLite.

Se propone un perfil integracion-local que excluya DataSourceAutoConfiguration, HibernateJpaAutoConfiguration y JpaRepositoriesAutoConfiguration, más una prueba que cargue ese perfil real y compruebe ausencia de DataSource/EntityManagerFactory y llamadas externas. El test histórico SoaApplicationTests ya usa esas exclusiones, pero no demuestra que un perfil nuevo arranque seguro.

Las cuatro cargas frontend fuerzan mode:mock y las pantallas contienen textos/escenarios artificiales. VITE_API_BASE_URL no basta. Se propone selección central mock/http y habilitación progresiva solo de Monitor inicialmente; las rutas no autorizadas deben impedir GET reales, sin fallback silencioso. El Explorador reutilizará proyectos sin endpoint cartográfico ni inyección de coordenadas artificiales en HTTP.

SENACE_BASE_URL/SENACE_AUTH_KEY están mapeadas a propiedades backend; .env.apis.local no se importa. BCRP_BASE_URL no tiene mapeo explícito equivalente; BcrpClient ya tiene URL oficial predeterminada. No colocar credenciales en VITE_* ni leerlas sin autorización. CORS actual permite * y expone cinco headers para Consultoras; la prueba de navegador real está pendiente.

En 7A solo se inspeccionó y creó este reporte. No se ejecutaron Maven, tests, HTTP, Spring Boot, Vite o SQLite. El siguiente prompt debe pedir 7B con archivos y permisos concretos, sin empezar 7C/7D automáticamente. No reimplementar vistas o proveedores ya validados.
