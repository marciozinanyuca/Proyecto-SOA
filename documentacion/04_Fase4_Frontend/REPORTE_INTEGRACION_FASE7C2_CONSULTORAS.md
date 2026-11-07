# EcoInvest — Integración Fase 7C.2: Consultoras Ambientales

Fecha: 07/11/2026. Entorno: Windows, PowerShell, Oracle JDK 17.0.12, Spring Boot 3.2.4, React y Vite 8.3.5.

## 1. Objetivo y conclusión

Conectar Consultoras Ambientales con el backend local y verificar una única consulta real a SENACE, conservando Monitor HTTP y sin habilitar Dashboard, Explorador ni el datastream genérico.

La consulta manual desde React devolvió HTTP 200 y un array de **15.137 filas**, procesado por la vista con procedencia `API_DIRECTA_SENACE`. El navegador pudo leer los metadatos CORS. Se comprobaron búsqueda, paginación y detalle sin nuevas consultas. Las comprobaciones DOM no detectaron desbordamiento horizontal global en los tres tamaños previstos.

El flujo real de Consultoras quedó verificado dentro de esta ejecución. No se certifica end-to-end todo EcoInvest. La aserción visual de bloqueo de Dashboard/Explorador fue inconclusa y debe distinguirse de sus pruebas automatizadas satisfactorias. No hubo capturas persistidas ni comparación visual exhaustiva con Stitch en esta fase.

## 2. Estado inicial y alcance

- Monitor HTTP procedía de la Fase 7C.1.
- Consultoras disponía de diseño, filtros, modelo, adaptador y fixtures artificiales del Lote 5.
- El backend ya implementaba transporte SENACE, mapeo, metadatos y política HTTP.
- Se autorizaron edición frontend, pruebas/build, inspección de configuración, lectura limitada de dos claves, arranque backend, arranque Vite, una consulta manual real y este reporte por etapas separadas.
- No se modificaron backend, OpenAPI, SQLite, Resilience4j, diseños Stitch ni dependencias. No se realizaron commits.

Antecedentes:

- [Auditoría 7A](REPORTE_AUDITORIA_INTEGRACION_FASE7A.md).
- [Perfil backend 7B](REPORTE_IMPLEMENTACION_FASE7B_BACKEND.md).
- [Monitor 7C.1](REPORTE_INTEGRACION_FASE7C1_MONITOR.md).
- [Consultoras Lote 5](REPORTE_IMPLEMENTACION_FASE4B_LOTE5.md).

## 3. Archivos del alcance de implementación

Las siguientes rutas son relativas a la raíz del repositorio:

| Archivo | Cambio |
|---|---|
| `frontend/src/api/source.ts` | Permiso explícito `monitor,consultoras`; factoría restringida por operación; fuente Consultoras HTTP/mock sin fallback silencioso. |
| `frontend/src/app/App.tsx` | Inyección de configuración central en Consultoras, preservando el control de acceso por vista. |
| `frontend/src/features/consultoras/consultorasModel.ts` | Carga mediante fuente central, escenario opcional para HTTP y mensajes dependientes del modo. |
| `frontend/src/features/consultoras/ConsultorasPage.tsx` | Inicio HTTP inactivo, consulta manual, etiquetas HTTP reales y ocultación de escenarios artificiales. |
| `frontend/tests/api/source.test.mjs` | Permisos, destinos, restricciones por operación y conservación de Monitor. |
| `frontend/tests/api/consultoras.test.mjs` | HTTP, headers, errores, ausencia, duplicados, filtros locales y cancelación/respuestas obsoletas con transporte inyectado. |

Estos archivos se trabajaron sobre el estado existente; no se descartaron modificaciones ajenas. El resumen `git diff --stat` respecto de Git no constituye por sí solo una atribución exhaustiva de todos los cambios de esta sesión: algunos archivos del alcance no aparecen en ese diff de archivos seguidos.

## 4. Configuración HTTP y mock

Configuración pública del proceso Vite utilizado:

```text
VITE_API_MODE=http
VITE_API_BASE_URL=http://127.0.0.1:8081
VITE_HTTP_FEATURES=monitor,consultoras
```

- Se conserva también la combinación limitada `monitor`.
- La combinación ampliada es explícita; variantes no admitidas se rechazan como configuración inválida.
- El cliente de cada vista permite únicamente su operación: Monitor no obtiene capacidad Consultoras ni viceversa.
- Dashboard, Explorador y datastream genérico permanecen bloqueados en la fuente HTTP habilitada.
- No se monta una consulta automática de Consultoras, ni se cambia a fixtures tras un fallo HTTP.
- El modo mock permanece separado y rotulado como datos artificiales.
- Las restricciones frontend son un control operativo, no autenticación o autorización del servidor.

## 5. Pruebas ejecutadas

Directorio: `frontend/`. Fecha: 07/11/2026.

| Comando | Resultado observado | Alcance |
|---|---|---|
| `npm run typecheck` | Código de salida 0. | TypeScript de aplicación y configuración Node. |
| `npm run test:api` | **131 pruebas, 131 satisfactorias, 0 fallos, 0 canceladas, 0 omitidas**. | Tipos/modelos, adaptadores, cliente, fuentes y escenarios artificiales; no acredita datos reales. |
| `npm run build` | Código de salida 0; Vite 8.3.5 construyó 47 módulos. | Typecheck adicional y artefactos en `dist/`; no valida por sí solo navegador o proveedores. |

La suite comprobó, entre otros casos, allowlist Monitor/Consultoras, bloqueo de operaciones adicionales, ausencia de fallback a mock, headers, HTTP 502/503, array vacío oficial, ausencia de headers, duplicados, campos opcionales, cancelación y descarte de resultados obsoletos. Los errores del proveedor no se provocaron mediante consultas reales.

## 6. Configuración backend y credenciales

Se inspeccionó [`application-integracion-local.yml`](../../backend/src/main/resources/application-integracion-local.yml), que mantiene:

- Bind `127.0.0.1` y puerto `8081`.
- Exclusión de `DataSourceAutoConfiguration`.
- Exclusión de `HibernateJpaAutoConfiguration`.
- Exclusión de `JpaRepositoriesAutoConfiguration`.

La configuración base conserva `ddl-auto: update`; por ello no se debe sustituir este arranque por uno normal sin evaluar persistencia.

En el proceso de diagnóstico no se detectaron variables de override relevantes de Spring, servidor, propiedades externas o argumentos JVM/Maven. En `.mvn/` se observó el directorio wrapper, sin los archivos adicionales `jvm.config` o `maven.config`. No se encontró configuración productiva manual DataSource/EntityManager en la búsqueda estática realizada. Los puertos estaban libres antes del arranque.

Tras autorización se leyeron exclusivamente `SENACE_BASE_URL` y `SENACE_AUTH_KEY` de `backend/.env.apis.local`, sin ejecutar el archivo como código. Se comprobó presencia no vacía, ausencia de claves duplicadas y destino HTTPS oficial. No se mostraron valores. Git confirmó que el archivo estaba ignorado y no apareció entre los archivos seguidos consultados.

Las variables se suministraron en el proceso temporal de arranque backend. No se persistieron en Windows, argumentos, archivos frontend, fixtures o reportes. No se introdujeron credenciales en `VITE_*`. La recepción de datos confirma que la configuración funcionó en esta consulta; sin una consulta comparativa no demuestra que el proveedor exija autenticación para cualquier acceso ni acredita todas las propiedades de la credencial.

## 7. Arranque autorizado y comandos

Se verificó `C:\Program Files\Java\jdk-17\bin\java.exe -version`: Oracle Java **17.0.12**.

Desde `backend/`, con `JAVA_HOME` y `PATH` ajustados solo para ese proceso y las dos variables SENACE cargadas mediante un parser restringido:

```powershell
.\mvnw.cmd -o '-Dspring-boot.run.profiles=integracion-local' '-Dspring-boot.run.arguments=--spring.config.location=classpath:/application.yml --spring.config.additional-location= --spring.config.import= --spring.profiles.include=' spring-boot:run
```

No se incluyeron los overrides de credencial vacía utilizados anteriormente en Monitor. Maven offline regeneró OpenAPI y compiló fuentes y tests en `target/`; no se ejecutó una suite Maven de pruebas en esta fase. Aparecieron advertencias de API obsoleta en `SenaceTransport` y operaciones sin comprobación de tipos en `SenaceTransportTest`; no impidieron el arranque ni se corrigieron fuera del alcance.

Logs observados: Java 17.0.12, PID 12440, un solo perfil activo `integracion-local`, Tomcat puerto 8081 y aplicación iniciada. No aparecieron mensajes de inicialización DataSource/JPA en la salida revisada.

Desde `frontend/`, con las tres variables públicas indicadas:

```powershell
npm run dev -- --host=127.0.0.1 --port=5173 --strictPort
```

El script mostrado por npm ejecutó `vite --host 127.0.0.1`; la salida confirmó `http://127.0.0.1:5173/`. No debe asumirse por el comando solicitado que todos sus argumentos adicionales fueron trasladados por npm.

## 8. Procedimiento de navegador y presupuesto real

La herramienta inicial de navegador falló antes de abrir la página; no produjo un GET backend. Tras autorización adicional se utilizó Edge headless aislado con perfil temporal, depuración en loopback y control mediante CDP desde Node, sin instalar herramientas ni guardar auxiliares de código.

Un primer destino `#consultoras` no correspondía a la ruta de la aplicación y no mostró la vista. Se revisó la navegación y se abrió correctamente `#/consultoras`. Esto no generó consultas backend.

Antes de pulsar el botón se observó **0 solicitudes backend** y existencia del control manual. Se pulsó una única vez. Durante la observación se registró exclusivamente:

```http
GET http://127.0.0.1:8081/api/consultoras
```

Recurso externo correspondiente: `https://datosabiertos.senace.gob.pe/Api/datastreams/ConsultorasAmbientales`, mediante el transporte productivo ya implementado y `auth_key` solo en memoria. No se registró ni publicó la URL autenticada. No hubo consultas de Dashboard, datastream, Monitor adicionales o BCRP.

Los límites del transporte productivo no se modificaron: lectura máxima 6.291.456 bytes, conexión 5 s, inactividad 10 s y plazo total 25 s; sin redirecciones ni reintentos automáticos. No se midieron individualmente esos límites en esta consulta ni se sometió al proveedor a pruebas de fallo.

## 9. Resultado HTTP y CORS

Evidencia capturada directamente de la única respuesta backend:

| Dato | Valor observado |
|---|---|
| HTTP backend | `200` |
| Content-Type | `application/json` |
| X-Senace-Origen | `API_DIRECTA_SENACE` |
| X-Senace-Estado | `EXITO` |
| X-Senace-Cobertura | `NO_VERIFICADA` |
| X-Senace-Cuerpo-Completo | `true` |
| X-Senace-Motivo | No presente; la vista mostró ausencia de motivo adicional. |
| Access-Control-Allow-Origin | `*`, configuración existente no modificada. |
| Access-Control-Expose-Headers | Los cinco headers `X-Senace-*` previstos, incluido Motivo. |

Los metadatos renderizados por React coincidieron con los headers: modo HTTP, estado 200, origen oficial informado, EXITO, cobertura NO_VERIFICADA y cuerpo completo. Esta lectura a través de la vista acredita que los headers relevantes fueron accesibles al cliente JavaScript, más allá de observarlos en la red.

El HTTP 200 observado es el de EcoInvest en el navegador. No se capturaron directamente status/headers HTTP upstream de SENACE; el resultado upstream se conoce a través del transporte y metadatos productivos.

## 10. Datos agregados y calidad

- Raíz JSON array válido: **15.137 filas** en esta respuesta, frente a las 15.015 históricas. No se convirtió ninguna cifra en constante.
- Se observaron exactamente los ocho nombres públicos previstos.
- No se conservaron cuerpos completos, nombres, resoluciones individuales ni filas reales como evidencia persistente.
- La inspección de cuerpo para agregados se hizo en memoria, reutilizando la respuesta ya recibida; no generó otro GET.

| Campo público observado | Tipos/presencia observados en esta respuesta |
|---|---|
| razonSocial | String |
| actividad | String |
| subsector | String |
| tipoSolicitud | String |
| resolucion | String o propiedad ausente |
| fechaRd | String |
| inicioVigenciaRd | String o propiedad ausente |
| finVigenciaRd | String o propiedad ausente |

La comprobación agregada no encontró valores null en esos campos; no acredita ausencia de strings vacíos ni una regla universal del proveedor. No se realizó un nuevo perfil exhaustivo de nulos, duplicados o semántica de fechas del dataset oficial.

KPI iniciales renderizados:

| KPI | Valor |
|---|---:|
| Filas recibidas | 15.137 |
| Filas filtradas sin filtros locales | 15.137 |
| Filas con resolución informada según el modelo existente | 13.546 |

Estos conteos son de filas; no acreditan empresas únicas, vigencia, habilitación ni cobertura completa del registro oficial. Se preservaron las fechas originales y la semántica aprobada; no se crearon RUC, identificadores empresariales o estados administrativos de consultoras.

## 11. Interacciones, responsive y errores

| Comprobación directa | Resultado y límite |
|---|---|
| Tabla inicial | 10 filas DOM, renderizado paginado. |
| Paginación | Se accionó Siguiente; control disponible. No se compararon contenidos identificables entre páginas. |
| Detalle | Se abrió una fila y se observaron ocho etiquetas de campo; no se publicó su contenido. |
| Búsqueda | Se introdujo texto artificial sin coincidencias; tabla con cero filas. No se confundió con vacío oficial. |
| Monitor | Vista HTTP disponible; no se pulsó su consulta para evitar un GET innecesario. |
| Red durante interacciones | Cero solicitudes backend adicionales. |
| Datos artificiales | El banner de Consultoras no indicaba demostración y los KPI procedían de la respuesta HTTP. |
| JavaScript | Cero eventos `Runtime.exceptionThrown` durante la observación principal. No equivale a auditoría exhaustiva de consola o accesibilidad. |

Medidas DOM de la página Consultoras tras recibir los datos:

| Viewport solicitado | clientWidth documento | scrollWidth documento | Tabla con scroll horizontal local |
|---|---:|---:|---|
| 1440 × 900 | 1425 | 1425 | Sí |
| 768 × 1024 | 753 | 753 | Sí |
| 375 × 812 | 360 | 360 | Sí |

La diferencia de 15 px respecto del viewport corresponde al espacio de scrollbar observado. En las tres comprobaciones `scrollWidth === clientWidth`, sin desbordamiento global. Son medidas DOM, no una revisión visual completa de fidelidad Stitch, controles táctiles o teclado. No se guardaron capturas para evitar persistir registros identificables.

### Comprobación inconclusa que debe conservarse

Al navegar a Dashboard y Explorador, la aserción basada en buscar el texto exacto `Integración no habilitada` devolvió false en ambos casos. No se recuperó el título efectivo ni se investigó la causa antes de cerrar el navegador. Por tanto, **no se acredita visualmente ese mensaje de bloqueo** ni se atribuye el resultado a una causa no demostrada.

No hubo GET adicionales durante esas navegaciones. El bloqueo por configuración y las capacidades restringidas sí están respaldados por inspección de código y la suite automatizada. Conviene completar la comprobación local de UI sin proveedores antes de declarar cerrados todos los criterios visuales.

Los estados HTTP 502/503, vacío oficial, headers ausentes, cancelación y respuestas obsoletas fueron comprobados con transporte artificial en tests, no provocados sobre SENACE real.

## 12. Aislamiento y cierre de procesos

- El perfil real mantuvo las tres exclusiones de persistencia y único perfil activo confirmado en logs.
- No se ejecutaron comandos de SQLite ni se abrieron archivos de base de datos desde el agente.
- No hubo indicios de arranque DataSource/JPA en los logs revisados. No se realizó trazado de llamadas al sistema; la evidencia no debe presentarse como auditoría absoluta de todos los accesos a archivos del proceso.
- No se expusieron valores de credenciales ni URLs autenticadas en la evidencia publicada.
- Se cerró Edge y se eliminó exclusivamente su directorio temporal autorizado, validando antes la ruta absoluta dentro de TEMP.
- Se detuvieron Vite y Spring Boot/Maven mediante interrupción de sus sesiones.
- La comprobación final no encontró procesos escuchando en 8081, 5173 o el puerto temporal de depuración 59042.

## 13. Estado final y siguiente paso

**Integración real de Consultoras: satisfactoria dentro del alcance consultado**, con reservas de QA visual indicadas. Monitor conserva su capacidad HTTP y las otras operaciones no se habilitaron. La suite frontend actual de 131 pruebas pasó; no es una suma de suites históricas.

No se necesita repetir el GET oficial para resolver la aserción visual pendiente. La siguiente tarea mínima es comprobar localmente los mensajes de bloqueo de Dashboard/Explorador, revisar teclado y fidelidad visual de Consultoras con datos artificiales o evidencia anonimizada y decidir si hace falta alguna corrección concreta. Esto requiere el permiso operativo correspondiente; no se ejecuta por este reporte.

Antes de conectar Dashboard/Explorador en una fase posterior:

1. Encargar explícitamente la ampliación del allowlist y comportamiento manual/compartido de carga.
2. Conservar perfil sin persistencia y credenciales únicamente backend.
3. Revisar permisos y presupuesto de consultas de proyectos y BCRP por separado.
4. Preservar ausencia, metadatos y degradación sin fallback a mocks.
5. Autorizar ediciones, pruebas, servidores, tráfico y evidencias según su alcance.

No se actualizaron README ni reportes históricos y no se inició la siguiente fase.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE y BCRP con Java 17.0.12/Spring Boot 3.2.4 y React. En 7C.2 se habilitó explícitamente `monitor,consultoras` en la fuente HTTP frontend, manteniendo las capacidades restringidas y el modo mock separado. Consultoras no consulta al montar: requiere botón manual. Se conservaron diseño, filtros, paginación de diez filas, detalle y los ocho campos públicos opcionales, sin inventar identidad o vigencia.

Se ejecutaron typecheck, 131 tests frontend satisfactorios y build. Con el perfil `integracion-local`, sin infraestructura JPA/DataSource autoconfigurada, se arrancaron backend y Vite locales y se realizó exactamente un GET manual a `/api/consultoras`. Respondió 200, array de 15.137 filas, origen API_DIRECTA_SENACE, estado EXITO, cobertura NO_VERIFICADA y cuerpoCompleto true. React leyó los headers CORS. KPI: 15.137 filas recibidas/filtradas inicialmente y 13.546 con resolución informada. No se guardaron cuerpos completos ni datos identificables.

Búsqueda, paginación y detalle se accionaron sin nuevos GET. Se midieron tres viewports sin desbordamiento horizontal global, con scroll de tabla local. La aserción visual del mensaje de bloqueo de Dashboard/Explorador devolvió false y quedó inconclusa; no hubo GET adicionales y los tests de restricciones sí pasaron. No afirmar una revisión visual exhaustiva o certificación end-to-end de todo EcoInvest.

Todos los procesos de prueba se cerraron y el perfil temporal Edge se eliminó. No se modificaron backend/OpenAPI/SQLite/Resilience4j ni se realizaron commits. Próximo prompt recomendado: cierre mínimo de QA local sin nuevas consultas oficiales para verificar las pantallas bloqueadas y accesibilidad/fidelidad, antes de autorizar otra integración. No repetir BCRP ni reconsultar SENACE solo para reproducir evidencia existente.
