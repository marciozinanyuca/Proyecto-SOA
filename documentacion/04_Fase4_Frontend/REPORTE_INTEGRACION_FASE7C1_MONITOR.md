# EcoInvest — 7C.1: primera comunicación HTTP frontend–backend

Fecha: 07/11/2026, America/Lima.

**Estado: integración HTTP local del Monitor comprobada con un único GET real y respuesta 200.** Las otras tres vistas permanecieron bloqueadas en modo HTTP. No se consultaron proveedores ni se certifica integración end-to-end de todo EcoInvest.

## 1. Objetivo y antecedentes

Conectar exclusivamente Monitor de Salud SOA React con `GET /api/estado-servicios` de Spring Boot, usando el perfil `integracion-local`, sin credenciales SENACE ni uso de SQLite.

- [Auditoría 7A](REPORTE_AUDITORIA_INTEGRACION_FASE7A.md).
- [Perfil backend y prueba 7B](REPORTE_IMPLEMENTACION_FASE7B_BACKEND.md).
- [Implementación frontend del Monitor](REPORTE_IMPLEMENTACION_FASE4B_LOTE6.md).
- [Contrato fuente](../../backend/src/main/resources/openapi.yaml).

Las autorizaciones fueron independientes: cambios frontend, validación npm, revisión previa de configuración backend, arranque Spring Boot, arranque Vite, un GET desde navegador/capturas y este reporte. No se interpretó un permiso como autorización automática de las etapas siguientes.

## 2. Archivos y cambios aplicados

### Creados

| Archivo | Responsabilidad |
|---|---|
| [source.ts](../../frontend/src/api/source.ts) | Parser de configuración pública, autorización por vista y fábrica sobre el cliente existente |
| [source.test.mjs](../../frontend/tests/api/source.test.mjs) | Quince casos de configuración, destinos, bloqueo y ausencia de fallback |

### Modificados

| Archivo | Cambio |
|---|---|
| [config.ts](../../frontend/src/app/config.ts) | Lee exclusivamente los tres nombres públicos acordados y valida su combinación |
| [vite-env.d.ts](../../frontend/src/vite-env.d.ts) | Declara VITE_API_MODE y VITE_HTTP_FEATURES junto a la URL existente |
| [App.tsx](../../frontend/src/app/App.tsx) | Impide montar las vistas no habilitadas en HTTP y muestra error de configuración seguro |
| [monitorModel.ts](../../frontend/src/features/monitor/monitorModel.ts) | Selecciona fuente explícita; mantiene adaptMonitor y omite escenario artificial en resultados HTTP |
| [MonitorPage.tsx](../../frontend/src/features/monitor/MonitorPage.tsx) | HTTP manual sin carga inicial, etiquetas de fuente, errores y ocultación de escenarios mock |
| [monitor.test.mjs](../../frontend/tests/api/monitor.test.mjs) | Cinco casos HTTP con transporte simulado, sin acceso real |
| [package.json](../../frontend/package.json) | Incluye source.test.mjs en test:api |

Se aprobaron diez archivos posibles, pero [tsconfig.api-tests.json](../../frontend/tsconfig.api-tests.json) no necesitó un cambio efectivo: su patrón `src/api/**/*.ts` ya incorpora source.ts. El resultado es dos archivos nuevos y siete modificados, no una modificación artificial de configuración para completar una lista.

No se modificaron Dashboard, Explorador, Consultoras, estilos Stitch, AppShell, backend, OpenAPI, SQLite, clientes SENACE/BCRP o Resilience4j. No hubo nuevas dependencias, cambios de versiones o commits.

## 3. Selección central de fuente

Configuración pública de la sesión HTTP autorizada:

```text
VITE_API_MODE=http
VITE_API_BASE_URL=http://127.0.0.1:8081
VITE_HTTP_FEATURES=monitor
```

Reglas implementadas:

- Sin modo configurado, se conserva mock como valor inicial de desarrollo documentado.
- Solo se admiten los modos mock/http. Un valor desconocido, incluso una cadena vacía de modo, produce error; no activa mocks silenciosamente.
- En HTTP se exige permiso explícito `monitor` y URL local válida.
- Esta subfase admite únicamente base HTTP `127.0.0.1:8081`, sin prefijo `/api`, credenciales, query o fragmento. No se amplía a dominios, localhost u otros puertos sin una decisión posterior.
- En mock, permisos HTTP residuales se consideran configuración incoherente; para revertir hay que eliminarlos expresamente.
- Errores de configuración muestran categorías seguras, sin reflejar URLs o valores arbitrarios de entrada.
- La fábrica reutiliza createApiClient; no duplica fetch, adaptadores o serialización.

La capacidad HTTP devuelta permite únicamente estadoServicios. Dashboard, Consultoras y Datastream quedan bloqueados también en el objeto cliente de esta fábrica, antes de invocar fetch. La guarda de transporte exige exactamente GET a `/api/estado-servicios` en el destino autorizado.

En App.tsx, las rutas Dashboard/Explorador/Consultoras se conservan navegables pero muestran Integración no habilitada y no montan sus componentes en modo HTTP. Esto evita cargas mock ocultas y consultas reales por visitar la ruta inicial Dashboard.

La protección es un límite de la implementación/frontend de esta fase, no una autorización de seguridad del servidor frente a clientes ajenos. Los otros endpoints backend siguen existiendo; no se alteró su acceso por backend.

## 4. Monitor en mock frente a HTTP

Se conservaron createLatestRequest y adaptMonitor.

| Comportamiento | Mock | HTTP |
|---|---|---|
| Carga inicial | Conserva la demostración existente | No realiza GET al montar |
| Actualización | Fixture seleccionado, explícito | Botón manual de consulta al backend |
| Selector de escenarios | Visible | No se renderiza |
| Aviso | MODO DEMOSTRACIÓN — DATOS ARTIFICIALES | MODO HTTP — BACKEND LOCAL |
| Etiquetas de estado HTTP | Simulado | HTTP backend |
| Números | Exclusivamente fixtures | Exclusivamente respuesta HTTP validada |
| Fallo | Error del escenario | Error sin cambio a mock ni fuente alternativa |

El botón HTTP se deshabilita durante una consulta para reducir duplicaciones. La ausencia de carga inicial evita dos GET por el montaje de desarrollo de React StrictMode. No hay polling o peticiones por cambiar de tamaño de pantalla.

La hora sigue siendo local del cliente, registrada al completar la carga; no se atribuye a SENACE/BCRP. CLOSED describe circuito cerrado, no proveedor operativo. −1 se representa No calculada. NO_VERIFICADA y NO_IMPLEMENTADO conservan sus significados distintos.

## 5. Validación frontend ejecutada

Fecha: 07/11/2026. Directorio: `D:\Documentos\GitHub\Proyecto-SOA\frontend`.

```powershell
npm run typecheck
npm run test:api
npm run build
```

Resultado: código 0 en la secuencia autorizada.

| Validación | Resultado |
|---|---|
| Typecheck | Satisfactorio |
| test:api actual | 118 pruebas aprobadas, 0 fallos, 0 canceladas, 0 omitidas |
| Build | Satisfactorio, Vite 8.3.5, 47 módulos transformados |

Las 118 pruebas pertenecen a una sola ejecución actual: 98 existentes más quince de fuente y cinco de Monitor HTTP. Las nuevas pruebas HTTP utilizan fetch inyectado/artificial, no el backend real. El GET real posterior es una evidencia separada.

El build produjo aproximadamente 35,15 kB CSS y 302,23 kB JavaScript. No cambió el CSS del Monitor. Se escribieron artefactos locales de compilación/build, sin edición manual o instalación de paquetes.

Las pruebas cubren configuración válida/inválida, destinos prohibidos, bloqueo de otras operaciones, cancelación previa, preservación mock, errores de red/formato sin fallback y correspondencia de métricas HTTP inyectadas. No hubo fallos que requirieran corrección en esta ejecución.

## 6. Verificación previa de configuración backend

Antes del arranque se revisaron el YAML real, nombres de recursos, posibles nombres de overrides de entorno Spring/server/external y opciones Java/Maven, sin imprimir sus valores ni leer archivos de credenciales.

Resultados:

- Perfil conserva 127.0.0.1:8081.
- Exclusiones DataSourceAutoConfiguration, HibernateJpaAutoConfiguration y JpaRepositoriesAutoConfiguration presentes.
- No se detectaron overrides de arranque del grupo inspeccionado en ese proceso.
- SENACE_BASE_URL y SENACE_AUTH_KEY no estaban presentes en el proceso inspeccionado; solo se comprobó presencia, no se leyó contenido de credenciales.
- No se encontraron archivos `.mvn/jvm.config` o `.mvn/maven.config` en las rutas raíz/backend revisadas.
- No se encontraron componentes productivos DataSource/JPA/JDBC manuales en la búsqueda realizada.
- Se contrastó el resumen final 7B: cinco pruebas de aislamiento aprobadas.

La revisión inicial de configuración no se presentó como prueba de un servidor ya iniciado. En el proceso posterior se volvió a comprobar ausencia de overrides del grupo evaluado antes de lanzar Maven.

## 7. Arranque real de Spring Boot autorizado

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\backend`.

JAVA_HOME y PATH se configuraron solo en el proceso PowerShell, con `C:\Program Files\Java\jdk-17`. Se verificó 17.0.12 antes de continuar. El log de la JVM productiva también informó Java 17.0.12.

```powershell
.\mvnw.cmd -o `
  "-Dspring-boot.run.profiles=integracion-local" `
  "-Dspring-boot.run.arguments=--spring.config.location=classpath:/application.yml --spring.config.additional-location= --spring.config.import= --spring.profiles.include= --external.apis.senace-base-url= --external.apis.senace-auth-key=" `
  spring-boot:run
```

El arranque restringió fuentes a recursos classpath evaluados, sin imports/additional-location/perfiles incluidos, y dejó SENACE explícitamente sin configuración/credencial. No cargó backend/.env.apis.local.

Evidencia observada en log:

- Spring Boot 3.2.4.
- Único perfil activo: integracion-local.
- Tomcat 10.1.19 inició en puerto 8081.
- Started SoaApplication a las 14:13:00 -05:00, tiempo de arranque 3,255 s.
- Comprobación de socket: escucha en 127.0.0.1:8081, proceso 30368.

Maven offline ejecutó fases previas de generación OpenAPI y compilación, incluida compilación de fuentes test, sin ejecutar una suite Surefire durante spring-boot:run. No se editaron target/ manualmente ni se realizaron descargas autorizadas implícitamente.

El arranque no recibió credenciales SENACE. No se invocaron endpoints Dashboard, Consultoras, Datastream, Actuator o documentación para comprobar el servidor: se reservó el presupuesto al GET del Monitor desde React.

## 8. Vite y procedimiento de navegador

Se inició Vite en un proceso temporal con las tres variables públicas indicadas, desde frontend/:

```powershell
npm run dev -- --port=5173 --strictPort
```

La salida efectiva del script fue `vite --host 127.0.0.1`; quedó disponible en `http://127.0.0.1:5173/`. Iniciar Vite no ejecutó un GET backend.

Después de autorización específica se abrió Edge headless con perfil temporal aislado, sin utilizar el perfil personal del usuario. Se empleó CDP/WebSocket nativo de Node, sin instalar herramientas.

Secuencia observada:

1. Abrir Dashboard y navegar a Explorador y Consultoras.
2. Comprobar mensaje de bloqueo y ausencia de los tres componentes.
3. Entrar en Monitor y comprobar ausencia de snapshot, valores No disponible y cero controles de escenarios.
4. Confirmar cero solicitudes backend antes de consultar.
5. Pulsar una única vez Consultar Monitor por HTTP.
6. Observar respuesta, comparar valores y capturar las tres resoluciones sin recargar o repetir el GET.

## 9. HTTP y CORS reales

La ejecución de navegador comenzó aproximadamente a las 14:16:19 -05:00. El GET se procesó a las 14:16:22 -05:00.

| Dato | Evidencia |
|---|---|
| Método/recurso | GET http://127.0.0.1:8081/api/estado-servicios |
| Número de solicitudes backend | Exactamente una |
| Status real | 200 |
| Content-Type | application/json |
| Access-Control-Allow-Origin | * |
| Vary | Origin, Access-Control-Request-Method, Access-Control-Request-Headers |
| Origen de la página | http://127.0.0.1:5173 |
| Lectura por React | Satisfactoria; adaptador ready y métricas visibles |

Se capturaron solo headers de respuesta relevantes y no sensibles. El campo origin del evento Network.requestWillBeSent quedó null en el registro; no se presenta como evidencia del header Origin enviado. La comprobación CORS se sustenta en página y backend de distintos orígenes, respuesta con Allow-Origin y lectura satisfactoria por fetch/React.

No se realizó un GET adicional por PowerShell, Node o curl para verificar la API. La respuesta se obtuvo de la misma solicitud monitorizada mediante CDP; recuperar su cuerpo no implicó otra petición al servidor.

CORS funciona en este escenario local con credentials:omit. El wildcard actual no se cambió y no se considera una política final aprobada para despliegue en red/nube.

## 10. Métricas reales recibidas y mostradas

| Servicio | Circuito | Fallidas | Exitosas | Tasa backend | Presentación |
|---|---|---:|---:|---:|---|
| SENACE | CLOSED | 0 | 0 | −1 | No calculada |
| BCRP | CLOSED | 0 | 0 | −1 | No calculada |

Ambos objetos informaron LLAMADAS_LOGICAS, disponibilidadExterna NO_VERIFICADA y respaldoLocal NO_IMPLEMENTADO. Las comparaciones automatizadas registraron `statesMatch: true` y `countsAndRatesMatch: true`.

Estos ceros y estados proceden de la respuesta real del backend; no de fixtures. Los contadores iniciales no se interpretan como peticiones externas exitosas o disponibilidad oficial de proveedores. Las tarjetas muestran Circuito cerrado y sus advertencias, no Operativo.

No se mostraron el OPEN/100 %/cinco fallos del fixture inicial del Lote 6. El selector artificial estaba ausente y las etiquetas fueron MODO HTTP — BACKEND LOCAL, HTTP backend:200 y HTTP · backend local.

## 11. Bloqueo de otras vistas y presupuesto

| Ruta frontend visitada | Resultado |
|---|---|
| Dashboard | Integración no habilitada; componente no montado |
| Explorador | Integración no habilitada; componente no montado |
| Consultoras | Integración no habilitada; componente no montado |
| Monitor antes del botón | Sin snapshot y cero GET backend |
| Monitor después del botón | Un GET a estado-servicios |

No se registraron solicitudes a otros endpoints backend ni destinos externos en el navegador. No se habilitaron rutas HTTP nuevas. No se ejecutaron health checks a proveedores desde el Monitor.

La aplicación backend se inspeccionó y ejecutó bajo perfil seguro sin configuración SENACE; el endpoint utilizado solo lee CircuitBreakerRegistry. El arranque/log no mostró actividad de clientes externos y las métricas iniciales tienen cero llamadas. No se hizo captura de paquetes del proceso Java: la ausencia de tráfico externo se afirma dentro de ese flujo inspeccionado y la monitorización de navegador, no como auditoría universal de red del sistema operativo.

## 12. Evidencia visual y responsive

- [Escritorio](evidencias/7c1/escritorio.png).
- [Tablet](evidencias/7c1/tablet.png).
- [Móvil](evidencias/7c1/movil.png).
- [Resultados, headers permitidos y métricas comparadas](evidencias/7c1/resultados.json).

| Viewport | clientWidth | scrollWidth global | Resultado |
|---|---:|---:|---|
| 1440 × 900 | 1425 px | 1425 px | Sin desbordamiento horizontal global |
| 768 × 1024 | 753 px | 753 px | Sin desbordamiento horizontal global |
| 375 × 812 | 360 px | 360 px | Sin desbordamiento horizontal global |

La diferencia de 15 px corresponde al espacio de la barra vertical observado. Las capturas son de página completa con viewports emulados y alturas de imagen superiores al viewport. Se usó deviceScaleFactor 1 y mobile:false, sin afirmar dispositivo físico o auditoría WCAG completa.

Se inspeccionaron visualmente escritorio y móvil. La captura tablet fue generada y cuenta con medidas DOM; no se afirma inspección visual independiente exhaustiva de esa imagen. No se modificaron estilos Stitch y se conserva la composición previa del Monitor.

No se registraron eventos Runtime.exceptionThrown durante el recorrido. Esto no equivale a revisión exhaustiva de todos los niveles de consola o de código no ejercitado.

## 13. SQLite, límites de prueba y cierre de procesos

Se conservó el perfil 7B sin cambios, con las tres exclusiones. El único perfil activo observado en producción fue integracion-local y el proceso no recibió overrides adicionales detectados. No se abrieron bases desde herramientas, no se leyó contenido SQLite y no se cambiaron sus archivos deliberadamente.

La evidencia fuerte de ausencia de DataSource/JPA es la prueba 7B sobre ese perfil, complementada aquí por el arranque acotado y el flujo Monitor sin persistencia. No se consultó un endpoint de beans ni se instrumentaron syscalls/handles del proceso real. Por tanto, este reporte no atribuye una prueba exhaustiva de acceso a archivos que no se realizó.

Edge se cerró después de capturar evidencia. Se enviaron interrupciones controladas a Vite y Maven/servidor. La primera comprobación de sockets mostró un registro transitorio de 5173 con PID 0; la segunda confirmó **puertos 8081 y 5173 sin listeners**, y el proceso backend 30368 ya no existía.

No se declara eliminación de perfiles temporales, que no se ejecutó. Las variables temporales no se persistieron en configuración del sistema. No se realizaron commits.

## 14. Estado final y reversión

**7C.1 comprobó el flujo React → fetch/CORS → Spring MVC → controlador → CircuitBreakerRegistry → JSON → adaptMonitor → vista.** Es comunicación HTTP local real, distinta de las pruebas con transportes simulados.

No certifica el flujo de Dashboard con SENACE+BCRP, Consultoras real, cartografía, todas las rutas en modo HTTP o despliegue. No se inició 7C.2.

Para volver a demostración, iniciar otra sesión sin las variables HTTP, o seleccionar `VITE_API_MODE=mock` y eliminar explícitamente VITE_HTTP_FEATURES. Mantener `monitor` como permiso residual en modo mock produce error de configuración por diseño. Cambiar configuración de Vite requiere reinicio; no se usa un error HTTP como mecanismo de reversión.

No es necesario revertir código, modificar backend o arrancar el perfil normal para seguir usando mocks.

## 15. Próximo paso recomendado

Compartir este reporte y preparar un encargo independiente 7C.2. La habilitación de Consultoras sería una continuación acotada, pero requiere revisar la ampliación de VITE_HTTP_FEATURES, adaptación de sus textos/fuente, headers CORS y un presupuesto explícito del GET SENACE que esa operación dispara.

No ampliar automáticamente el allowlist, leer credenciales, reutilizar el permiso de Monitor para otros endpoints o conectar las cuatro vistas a la vez. Conservar el perfil seguro y confirmar nuevamente configuración si cambia el proceso o el entorno.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest completó la primera comunicación HTTP local real exclusivamente para Monitor. La selección central valida VITE_API_MODE, VITE_API_BASE_URL y VITE_HTTP_FEATURES; esta fase solo admite http://127.0.0.1:8081 y permiso monitor. Dashboard/Explorador/Consultoras no se montan en HTTP y no se convierten silenciosamente a mocks. Monitor consulta manualmente, sin GET al montar o polling y sin selector artificial en HTTP.

Se añadieron source.ts y source.test.mjs, y se modificaron configuración pública, App, modelo/vista y tests de Monitor y package.json. tsconfig.api-tests.json no necesitó cambio efectivo. Typecheck, 118 pruebas actuales y build fueron satisfactorios. Backend, OpenAPI y estilos no cambiaron.

El 07/11/2026 se arrancó Spring Boot 3.2.4 con Oracle JDK 17.0.12, único perfil integracion-local, fuentes classpath acotadas y SENACE sin credencial. Se confirmó escucha 127.0.0.1:8081. Desde Vite/Edge se realizó exactamente un GET a /api/estado-servicios: HTTP 200, JSON y Access-Control-Allow-Origin:*. React mostró exactamente los dos circuitos CLOSED, llamadas 0/0 y tasas −1 como No calculada. NO_VERIFICADA y NO_IMPLEMENTADO permanecen explícitos; no se afirma salud externa.

Las tres vistas protegidas se verificaron bloqueadas y no hubo otras solicitudes backend, tráfico externo de navegador o excepciones monitorizadas. Capturas en documentacion/04_Fase4_Frontend/evidencias/7c1/ muestran tres resoluciones sin desbordamiento. Edge, Vite y backend quedaron cerrados; puertos libres comprobados. No se abrieron bases desde herramientas; el aislamiento se apoya en perfil/validación 7B y no en una auditoría de syscalls del servidor real.

El siguiente prompt debe definir 7C.2 con alcance y permisos nuevos, posiblemente Consultoras, sin ampliar rutas, cargar credenciales o consultar SENACE por asociación. No repetir 7C.1 ni declarar integración end-to-end de todo EcoInvest.
