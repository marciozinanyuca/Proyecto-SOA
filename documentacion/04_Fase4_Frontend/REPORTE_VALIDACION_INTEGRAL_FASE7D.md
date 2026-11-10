# EcoInvest — Validación integral y consolidación frontend–backend, Fase 7D

Fecha: 07/11/2026. Repositorio: `D:\Documentos\GitHub\Proyecto-SOA`.

## 1. Resumen ejecutivo

Se auditó la integración frontend existente tras 7C.1–7C.3, se corrigieron etiquetas históricas del shell y tres problemas de contraste, y se ampliaron las pruebas de regresión y volumen. La ejecución final de typecheck, **151 pruebas satisfactorias** y build fue correcta.

La revisión de navegador utilizó exclusivamente modo mock y respuestas artificiales inyectadas en memoria. **No se inició Spring Boot, no se cargaron credenciales SENACE y no se consultaron SENACE/BCRP ni SQLite.** No se repitieron las consultas reales de fases anteriores.

Las cuatro vistas conservaron navegación, fuentes explícitas, opcionales y degradación. No se observaron consultas al montar en HTTP ni solicitudes API/externas durante los recorridos instrumentados. Responsive y teclado básico se comprobaron dentro del alcance descrito.

**Pendiente relevante:** el Explorador renderiza todas las filas y marcadores. Con 20.000 proyectos artificiales, la navegación hasta completar el DOM tardó aproximadamente **10,7 segundos**. No se corrigió ese comportamiento porque el usuario autorizó únicamente documentarlo y no introducir virtualización o clustering.

La Fase 7D queda validada dentro del alcance funcional y de correcciones aprobado, **con reserva explícita de rendimiento para listas extensas** y sin certificación WCAG completa ni end-to-end exhaustiva de toda la aplicación.

## 2. Antecedentes y frontera de evidencia

Se consultaron AGENTS.md, las skills pertinentes de frontend, arquitectura, integración, testing, seguridad/documentación, el contrato OpenAPI y los componentes actuales de `frontend/src/app/`, `frontend/src/api/` y las cuatro funcionalidades.

Referencias:

- [Monitor HTTP 7C.1](REPORTE_INTEGRACION_FASE7C1_MONITOR.md).
- [Consultoras HTTP 7C.2](REPORTE_INTEGRACION_FASE7C2_CONSULTORAS.md).
- [Cierre QA local 7C.2](REPORTE_CIERRE_QA_FASE7C2.md).
- [Dashboard y Explorador 7C.3](REPORTE_INTEGRACION_FASE7C3_DASHBOARD_EXPLORADOR.md).
- [Contrato OpenAPI](../../backend/src/main/resources/openapi.yaml).

Las consultas reales de esos antecedentes siguen siendo evidencia histórica separada: Monitor, Consultoras y una selección Dashboard fueron verificados en sus respectivas fases. Las respuestas de navegador de 7D son artificiales y no acreditan disponibilidad actual de los proveedores.

## 3. Diagnóstico inicial

| Área | Estado observado por código y reportes | Riesgo o defecto |
|---|---|---|
| Navegación | Cuatro rutas hash y bloqueo previo según configuración. | Requería regresión conjunta y teclado. |
| Fuente | Selección HTTP/mock explícita y capacidades restringidas; sin fallback automático. | El allowlist frontend es control operativo, no autorización del servidor. |
| Dashboard | Carga HTTP manual; filtro por actividad/año de inicio, KPI, gráfico y tabla. | Precisión Number de JavaScript; datos ausentes deben seguir sin convertirse a cero. |
| Explorador | Reutiliza el snapshot Dashboard; plano SVG offline y lista alternativa. | Renderizado completo de listas/marcadores, superposición y volumen no medido. |
| Consultoras | Diez filas DOM por página; filtros y búsqueda locales. | Procesamiento del conjunto completo y coste de búsqueda con 15.000–20.000 filas. |
| Monitor | Métricas de circuitos y llamadas lógicas; disponibilidad externa no verificada. | No confundir estado del circuito con salud del proveedor. |
| Shell | Textos fijos de preparación y desconexión. | Contradicción semántica confirmada con las vistas HTTP implementadas. |
| Accesibilidad | Labels, foco y tamaños definidos en CSS. | Contraste y teclado requerían revisión efectiva. |

No se justificó reconstruir vistas, cambiar el backend o instalar dependencias.

## 4. Autorizaciones y archivos

La primera autorización permitió modificar cinco archivos y crear un test. Posteriormente, tras presentar los hallazgos de contraste, se autorizó modificar tres CSS adicionales. Las ejecuciones y navegador tuvieron permisos separados. Este documento se creó tras autorización específica y comprobación de que no existía.

| Ruta relativa a la raíz | Cambio de 7D |
|---|---|
| `frontend/src/app/AppShell.tsx` | Recibe configuración y sustituye textos históricos por presentación honesta de fuente. |
| `frontend/src/app/App.tsx` | Pasa `publicConfig` al shell. |
| `frontend/src/api/source.ts` | Añade `sourcePresentation`, sin alterar permisos o transporte. |
| `frontend/tests/api/source.test.mjs` | Pruebas de etiquetas HTTP/mock/configuración inválida y ausencia de afirmaciones de conexión. |
| `frontend/package.json` | Añade `integral.test.mjs` al runner existente, sin dependencias nuevas. |
| `frontend/tests/api/integral.test.mjs` | Archivo nuevo: regresión integrada de funciones y volumen artificial. |
| `frontend/src/styles/global.css` | Color del texto SENACE del encabezado: `#0e7490`. |
| `frontend/src/features/consultoras/ConsultorasPage.css` | Fondo y borde del botón principal: `#047857`. |
| `frontend/src/features/monitor/MonitorPage.css` | Fondo y borde del botón principal: `#047857`. |

Se preservaron las modificaciones preexistentes del repositorio. No atribuir todo el estado Git a esta fase. No se modificaron OpenAPI, backend, Resilience4j, SQLite, clientes externos, diseños Stitch, dependencias o políticas de servidor. No hubo commits.

## 5. Coherencia de la fuente y cambios del shell

`sourcePresentation` describe configuración, no estado de conexión:

| Configuración | Encabezado/etiqueta | Información complementaria |
|---|---|---|
| HTTP válido | `Modo HTTP seleccionado` / `BACKEND LOCAL CONFIGURADO` | Consulta manual; resultados y procedencia en cada vista. La configuración no acredita conexión activa ni disponibilidad externa. |
| Mock válido | `Demostración · datos artificiales` / `MODO DEMOSTRACIÓN` | Respuestas artificiales identificadas y sin consultas a proveedores. |
| Inválida | `Configuración inválida` / `CONSULTAS BLOQUEADAS` | Revisar configuración pública; no se activa otra fuente. |

La etiqueta accesible del grupo de logos ahora indica que son entidades de referencia y que sus logos no acreditan disponibilidad externa.

No se añadió un indicador global de conexión, telemetría o almacenamiento de estados de proveedor. Las respuestas efectivamente recibidas, status y metadatos siguen informándose en cada pantalla. Se conservaron layout, navegación, logos y paleta; los cambios de color posteriores respondieron a defectos medidos.

## 6. Pruebas automáticas ejecutadas

Directorio: `frontend/`. Fecha: 07/11/2026. Runtime frontend: Node disponible en el entorno, instalado previamente; no se reinstaló ni se verificó de nuevo su versión exacta en 7D. Java no se ejecutó: se mantuvo la restricción de Oracle JDK 17.0.12 sin modificar el backend.

Comandos autorizados:

```powershell
npm run typecheck
npm run test:api
npm run build
```

| Ejecución | Typecheck | Tests | Build |
|---|---|---|---|
| Inicial, después de shell/tests | Código 0 | 151 pass, 0 fail/cancelled/skipped | Código 0, Vite 8.3.5, 48 módulos. |
| Final, después del contraste | Código 0 | **151 pass, 0 fail/cancelled/skipped** | Código 0, Vite 8.3.5, 48 módulos. |

No sumar ambas ejecuciones como 302 pruebas diferentes. La cifra de suite actual es 151. No se ejecutó Maven ni ninguna suite backend.

El runner compila las funciones TypeScript para tests y ejecuta los archivos Node existentes más `integral.test.mjs`. Sus artefactos locales y `dist/` son efectos de validación, no cambios manuales en archivos generados. La comprobación estática del parche no detectó errores de whitespace; aparecieron avisos Git de conversión LF/CRLF, sin reformateo global.

## 7. Matriz de regresión

| Caso | Evidencia de 7D | Resultado/alcance |
|---|---|---|
| Cuatro rutas y fuente HTTP | Navegador con configuración completa y capa inyectada. | Cero invocaciones de fuente artificial al montar/navegar inicialmente; ningún GET API real. |
| Modo mock conservado | Navegador Vite mock y suite existente. | Cuatro vistas ready y etiquetas explícitas de demostración. |
| Configuración inválida | Tests de fuente/presentación. | Mensajes seguros; sin activar una fuente alternativa. No se abrió una ejecución Vite inválida. |
| Capacidades y datastream bloqueado | Suite de fuentes existente y regresión. | Permisos por operación conservados; no se amplió allowlist. |
| Dashboard normal | Respuesta inyectada de 6 proyectos. | 6 registros, 3 aprobados, indicador artificial 1.25 %. Gráfico 3/3. |
| SENACE ausente/BCRP disponible | Respuesta inyectada. | degraded; conteos SENACE No disponible, indicador 1.25 %. |
| SENACE disponible/BCRP ausente | Respuesta inyectada. | degraded; 6/3 y BCRP No disponible. |
| Vacío oficial Dashboard | Respuesta inyectada. | empty; 0/0 e indicador independiente conservado. |
| JSON inválido Dashboard | Respuesta inyectada. | error; KPI desconocidos, sin fixtures de sustitución. |
| Consultoras vacía/502/503 | Respuestas inyectadas. | Vacío oficial muestra 0; errores muestran No disponible. |
| Consultoras sin headers | Respuesta inyectada de 6 filas. | degraded; conteos de filas sin acreditar procedencia. |
| Consultoras JSON inválido | Respuesta inyectada. | error, sin conteos ficticios. |
| Monitor | Respuesta inyectada CLOSED/OPEN. | NO_VERIFICADA y NO_IMPLEMENTADO visibles; no se afirma salud externa. |
| Reutilización Dashboard–Explorador | Navegador y tests. | 6 filas/4 marcadores de la misma respuesta; cero invocaciones adicionales al navegar. |
| Cancelación | Navegador con demora artificial. | Consulta cancelada; Explorador queda esperando, sin reutilizar un éxito anterior. |
| Respuestas obsoletas | Suite del coordinador y almacén. | No publican la selección anterior. No se provocaron carreras sobre proveedores. |
| Recarga | Navegador. | Snapshot perdido; Explorador muestra espera sin GET. |
| Duplicados/opcionales | Tests con datos artificiales. | Filas conservadas; coordenadas/RUC ausentes no se fabrican. |
| null y formas inválidas | Suite de adaptadores existente. | Se mantienen reglas del contrato/validadores; no hubo cambio para aceptar null arbitrariamente. |

Los valores anteriores son **artificiales**, salvo referencias históricas explícitas a reportes 7C. No son nuevas observaciones de SENACE/BCRP.

## 8. Procedimiento de navegador y aislamiento de red

Se ejecutó Vite HTTP en loopback con:

```text
VITE_API_MODE=http
VITE_API_BASE_URL=http://127.0.0.1:8081
VITE_HTTP_FEATURES=monitor,consultoras,dashboard,explorador
```

Comando desde `frontend/`:

```powershell
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort
```

No hubo servidor backend en 8081. En Edge headless aislado se instaló una sustitución de `window.fetch` para las operaciones previstas, que devolvía Responses artificiales en memoria, conservando el cliente y los adaptadores productivos. Un aviso QA identificaba las respuestas como artificiales. No se añadió ese mecanismo al código de la aplicación.

Se bloquearon destinos API reales mediante la sesión CDP durante las pruebas relevantes y se observaron eventos de red antes de los recorridos. Las invocaciones al fetch inyectado se contabilizaron como operaciones simuladas; **no son GET reales al backend**. Los listeners no registraron solicitudes API ni destinos HTTP externos en los segmentos instrumentados. Vite, módulos y recursos locales sí produjeron tráfico local necesario para renderizar.

Para revisar mocks nativos se inició otro Vite en 5174 con `VITE_API_MODE=mock` y URL/features vacíos. Después de las correcciones de contraste se utilizó una última ejecución mock en 5173.

Limitación del procedimiento conservada: recargar retiró la capa de QA asociada al contexto temporal. Un script que esperaba su presencia agotaba su espera y se interrumpió. La aplicación sí había recargado correctamente, con Explorador esperando y sin snapshot. Se repuso la capa en memoria antes de medir volumen; no se corrigió producción para solucionar un problema del harness.

No se observaron eventos JavaScript `Runtime.exceptionThrown` en los recorridos donde se instrumentaron. No equivale a una auditoría exhaustiva de todos los mensajes de consola o de todo el tráfico del sistema operativo.

## 9. Rendimiento: Consultoras

### 9.1. Pruebas Node

Los tests generan registros artificiales en pares repetidos, mantienen ocho campos/opcionales, comprueban conteos, opciones, búsqueda, paginación y orden. No usan datasets oficiales.

Resultados de la ejecución final:

| Filas | Generación/carga/adaptación medida | Cinco pares de operaciones de filtro/búsqueda | Delta heap Node observado |
|---|---:|---|---:|
| 15.000 | 146,60 ms | 34,00; 32,22; 38,17; 34,35; 39,68 ms | 33.201.640 bytes |
| 20.000 | 124,98 ms | 45,38; 43,43; 43,89; 45,90; 64,73 ms | 33.885.544 bytes |

La ejecución inicial dio aproximadamente 158/129 ms para carga/adaptación. La variabilidad muestra que estos valores no constituyen un SLA ni una comparación lineal fiable. Las mediciones incluyen asignaciones, generación/aserciones y no fuerzan GC. El delta de heap no representa memoria retenida o fuga demostrada.

### 9.2. Navegador

| Filas artificiales | Consulta artificial hasta estado asentado | Búsqueda sin coincidencias | Filas tabla DOM | Heap JS antes/después aproximado |
|---|---:|---:|---:|---|
| 15.000 | 252,5 ms | 307,6 ms | 10 | 12.031.857 / 21.786.900 bytes |
| 20.000 | 326,6 ms | 499,4 ms | 10 | 22.397.329 / 38.956.319 bytes |

Los tiempos incluyen los costes de coordinación navegador/sondeo y no aíslan CPU, React o pintura. No se aplicó un protocolo estadístico de múltiples sesiones. Heap depende de GC y del estado previo, incluso con información precisa de memoria habilitada.

La tabla conserva diez filas visibles en el DOM, aunque el procesamiento recorre el conjunto. Con 20.000 filas, la búsqueda completa es perceptible. Se clasifica como **optimización pendiente**, no fallo de corrección ni motivo para añadir automáticamente una biblioteca de tablas. Una futura medición puede justificar normalización/indexación reutilizable, debounce u otras mejoras, con decisión explícita sobre comportamiento y alcance.

## 10. Rendimiento: Dashboard y Explorador

El test Node de 20.000 proyectos, 10.000 puntos coincidentes y opcionales tardó aproximadamente 177,06 ms en la ejecución final del pipeline puro. **No mide DOM ni SVG.**

Mediciones directas de navegador al navegar desde Dashboard a Explorador sobre una respuesta artificial compartida:

| Proyectos | Marcadores SVG | Tiempo navegación → DOM observado | Heap JS usado | Nodos de documentos reportados por CDP |
|---|---:|---:|---:|---:|
| 1.000 | 666 | 400,9 ms | 22.494.351 bytes | 17.664 |
| 5.000 | 3.333 | 2.721,1 ms | 43.746.420 bytes | 103.320 |
| 20.000 | 13.333 | 10.661,2 ms | 165.605.319 bytes | 425.842 |

Los contadores CDP pueden incluir nodos aún no recolectados de navegaciones anteriores; no son necesariamente el número exacto de elementos activos. Los elementos de marcador coinciden espacialmente en el fixture, por lo que no representan puntos visualmente separados.

### Hallazgo P01 — Renderizado no acotado

- **Causa confirmada:** `ExploradorPage.tsx` recorre todos los `markers` y todas las `entries` para crear elementos DOM/SVG. El scroll limita el área visible de la lista, no su cantidad de nodos.
- **Consecuencia observada:** con 5.000–20.000 filas artificiales la navegación tarda varios segundos y crece la memoria utilizada.
- **Clasificación:** limitación importante para listas extensas; no es un fallo de datos y no invalida las selecciones pequeñas verificadas en 7C.3. No está resuelta ni debe declararse aptitud ilimitada de volumen.
- **Corrección futura mínima a diseñar:** limitar el renderizado de lista mediante paginación/ventana y definir explícitamente el tratamiento de marcadores extensos o coincidentes. Si se limita el conjunto visible, informar la diferencia entre total y representados; no ocultar filas o cambiar conteos silenciosamente.
- **Archivos candidatos futuros:** `frontend/src/features/explorador/ExploradorPage.tsx`, `exploradorModel.ts`, posiblemente `ExploradorPage.css` y sus tests.
- **Autorización:** el usuario decidió documentarlo como pendiente. No se implementó virtualización, clustering, truncado, límite ni nuevas dependencias.

## 11. Responsive y navegación

En la primera revisión HTTP con respuestas controladas se midieron las cuatro vistas y en una segunda se repitió en mock nativo. Para cada vista se utilizaron:

| Viewport | clientWidth documento | scrollWidth documento |
|---|---:|---:|
| 1440 × 900 | 1425 | 1425 |
| 768 × 1024 | 753 | 753 |
| 375 × 812 | 360 | 360 |

No se detectó desbordamiento horizontal global en los recorridos medidos. El espacio restante corresponde al scrollbar del navegador. La muestra responsive no fue un ensayo de texto arbitrariamente largo o de todos los escenarios de volumen.

Se conservó la estructura existente de scroll interno de tablas. No se reescribió layout o diseño Stitch. No se guardaron capturas ni se realizó una nueva comparación píxel a píxel contra prototipos; las comprobaciones fueron de DOM, interacción, estilos computados y medidas, por lo que **no se declara una auditoría visual integral de cada panel**.

## 12. Teclado y accesibilidad básica

Comprobaciones realizadas:

- Inputs/selects de las cuatro vistas: cero controles sin label asociado o aria-label en la comprobación aplicada.
- En muestras mock, cero controles HTML visibles propios con altura inferior a 44 px. Esto no certifica el tamaño táctil de todos los marcadores SVG o enlaces.
- Consultoras: activación de detalle con Espacio y traslado de foco al encabezado de la ficha; retorno al botón de tabla al cerrar por click DOM.
- Menú móvil: apertura mediante Espacio, `aria-expanded=true`.
- Navegación mediante Enter desde un enlace a Consultoras: ruta correcta, menú cerrado y foco en `#contenido`.
- Los estilos de foco y orden básico se conservaron; no se ejecutó lector de pantalla.

### Hallazgos de contraste y corrección autorizada

La revisión calculó luminancia relativa a partir de foreground/background computados de elementos seleccionados. Los fondos medidos fueron sólidos o el primer fondo opaco del ancestro. No se evaluaron exhaustivamente gradientes, imágenes, estados disabled, todas las interacciones o contraste no textual.

| Elemento | Antes | Corrección | Después medido |
|---|---:|---|---:|
| SENACE del encabezado, texto pequeño en `#f8fafc` | 3,52:1 | Color `#0e7490` en `global.css`. | **5,12:1** |
| Botón principal Consultoras, texto blanco pequeño | 3,77:1 | Fondo/borde `#047857`. | **5,48:1** |
| Botón principal Monitor, texto blanco pequeño | 3,77:1 | Fondo/borde `#047857`. | **5,48:1** |

La referencia básica para texto normal pequeño fue 4,5:1. Dashboard y Explorador ya mostraron aproximadamente 5,48:1 en los botones principales medidos; no se modificaron. La etiqueta de etapa y un texto muted medidos dieron alrededor de 4,76:1 y el eyebrow sobre canvas 4,55:1.

Después de corregir se repitieron las pruebas/build y la medición en navegador mock. Consultoras y Monitor no presentaron desbordamiento global en las tres resoluciones. No hubo solicitudes API/externas ni excepciones instrumentadas en esa revisión final.

**No se certifica WCAG completo.** Quedan fuera lectores de pantalla, evaluación sistemática de todas las combinaciones de colores/estados, navegación exhaustiva y ensayo táctil real.

## 13. Seguridad y configuración

- No se leyó ni cargó configuración local SENACE en 7D.
- No se introdujeron credenciales, tokens, URLs autenticadas o datos personales reales en tests, frontend o evidencias.
- El cliente mantiene errores categorizados y headers relevantes sin usar mensajes de excepción externos como texto público.
- No se imprimió rawText de respuestas oficiales ni se guardaron cuerpos de proveedor.
- No se añadieron HTML sin sanear, scripts externos o mecanismos de fallback automático.
- La ausencia de datos sigue representándose como No disponible; un array de error no se interpreta como vacío oficial.
- Se preservaron los contratos y las distinciones NO_VERIFICADA, NO_IMPLEMENTADO y LLAMADAS_LOGICAS.

### CORS y despliegue

El backend conserva CORS wildcard existente y exposición de headers de Consultoras; no se modificó ni se ejercitó un servidor en esta fase. Para despliegue se deberá revisar por entorno el conjunto de orígenes, controles de acceso y exposición de datos. CORS no sustituye autenticación o autorización, y el allowlist de la aplicación no protege por sí solo los endpoints del servidor.

No se decidió ninguna política productiva nueva. No se efectuó escaneo completo de historial Git, análisis de dependencias o auditoría de secretos de todo el repositorio.

## 14. Recursos y limpieza

Se utilizaron únicamente Vite local y Edge temporal. No se arrancó Spring Boot, no se ejecutó Java/Maven, no se abrió SQLite y no se instalaron herramientas.

La primera revisión empleó un perfil temporal `ecoinvest-7d-*` y puertos 5173/5174 con depuración temporal en 58019. La comprobación final de contraste usó otro perfil `ecoinvest-7d-contrast-*`, Vite en 5173 y depuración en 57299.

Ambos navegadores se cerraron; Vite se detuvo y cada directorio temporal autorizado se eliminó tras validar su ruta absoluta dentro de TEMP. La verificación final no encontró procesos escuchando en 8081, 5173, 5174, 57299 o 58019. No se eliminaron reportes ni archivos de trabajo ajenos.

## 15. Estado final y riesgos pendientes

| Área | Estado al cierre |
|---|---|
| Integraciones 7C previas | Conservadas; no reconsultadas oficialmente. |
| Regresión automatizada 7D | 151 pruebas satisfactorias en la ejecución final. |
| Shell | Textos corregidos según configuración, sin falsas afirmaciones de conexión. |
| Contraste puntual | Tres defectos corregidos y medidos. |
| Responsive básico | Sin overflow global en las muestras de cuatro vistas/tres resoluciones. |
| Consulta manual, errores y snapshot | Verificados mediante capa local artificial y tests. |
| Consultoras 15.000–20.000 filas | Corrección/paginación conservadas; búsqueda perceptible pendiente de optimización. |
| Explorador masivo | **Pendiente P01:** renderizado no acotado, 10,7 s con 20.000 proyectos artificiales. |
| Accesibilidad completa | No certificada. |
| Seguridad/despliegue | Políticas productivas, acceso y CORS por entorno pendientes. |
| End-to-end completo | No certificado por este QA sin backend. |

La consolidación funcional aprobada se completó; no debe presentarse como ausencia de pendientes de rendimiento o preparación productiva definitiva.

## 16. Próximos pasos recomendados

1. Diseñar y autorizar un lote pequeño para **renderizado acotado del Explorador**, con pruebas de conservación de selección, acceso a todas las filas y transparencia de cantidad representada. No empezar por añadir una dependencia sin demostrar necesidad.
2. Medir de nuevo los mismos tamaños artificiales para comparar DOM, tiempos y memoria con metodología equivalente; no volver a descargar datasets oficiales.
3. Evaluar por separado la búsqueda de Consultoras si el presupuesto de interacción exige reducir los aproximadamente 500 ms observados en el peor recorrido realizado. Definir el objetivo antes de optimizar.
4. Completar QA visual/accesibilidad con capturas autorizadas y criterios explícitos si se requiere un cierre visual más fuerte; no inferirlo de build o medidas DOM.
5. Tratar configuración de nube, CORS por entorno, acceso y operación como un encargo posterior independiente. No iniciar despliegues por asociación.

Este reporte no autoriza nuevos trabajos ni actualiza automáticamente README u otros reportes.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest utiliza Java 17.0.12/Spring Boot 3.2.4 y React. Las integraciones reales de Monitor, Consultoras y Dashboard/Explorador se verificaron antes en 7C.1–7C.3. **En 7D no se consultaron proveedores, no se cargaron credenciales, no se arrancó Spring Boot ni se abrió SQLite.**

Se corrigió AppShell para distinguir demostración, HTTP configurado y configuración inválida, sin afirmar disponibilidad. Se añadió sourcePresentation en source.ts, se pasó publicConfig desde App.tsx y se crearon pruebas integrales artificiales con el runner existente. No hubo nuevas dependencias o cambios backend/OpenAPI. La ejecución final pasó typecheck, **151 pruebas** y build.

El navegador, con respuestas inyectadas en memoria y mock nativo, confirmó cero GET al montar en HTTP, consulta manual, errores 502/503, degradación independiente de SENACE/BCRP, cancelación, reutilización del snapshot sin carga adicional y pérdida del snapshot al recargar. Las cuatro vistas conservaron navegación y medidas sin desbordamiento global en 1440, 768 y 375 px. Teclado y labels básicos funcionaron; no se certificó WCAG ni se guardaron capturas.

Se detectaron y corrigieron, con autorización adicional, tres problemas de contraste: texto SENACE del header ahora #0e7490 (5,12:1) y botones principales Consultoras/Monitor #047857 con texto blanco (5,48:1). La validación final no registró API/HTTP externos o excepciones instrumentadas. Vite/Edge se cerraron y los perfiles temporales se eliminaron.

El pendiente principal es **P01: rendimiento del Explorador**. Renderiza toda la lista/SVG; con 1.000/5.000/20.000 proyectos artificiales se midieron aproximadamente 0,4/2,7/10,7 s hasta observar el DOM, con heap de unos 165,6 MB en el caso mayor. No se implementaron límites, paginación de lista, virtualización o clustering. El usuario autorizó documentarlo como pendiente. Consultoras con 20.000 filas mantuvo diez filas DOM; carga artificial unos 327 ms y búsqueda sin coincidencias unos 499 ms, sin equivaler a SLA o fuga de memoria.

Siguiente prompt recomendado: diseñar un lote explícito de renderizado acotado del Explorador, conservando todas las filas accesibles, selección y conteos honestos; medir con los mismos fixtures sin proveedores. No reabrir BCRP, no repetir consultas oficiales, no tocar backend ni desplegar automáticamente. La fase funcional está consolidada dentro del alcance revisado, con pendientes de volumen, accesibilidad exhaustiva y seguridad productiva claramente separados.
