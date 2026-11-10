# EcoInvest — Cierre QA local de Consultoras, Fase 7C.2

Fecha: 07/11/2026. Revisión local en Windows, React y Vite 8.3.5, mediante Edge headless y CDP.

## 1. Objetivo y resultado

Resolver la comprobación inconclusa de las rutas Dashboard/Explorador en modo HTTP y revisar Consultoras sin nuevas consultas oficiales.

**Resultado:** ambas rutas muestran el bloqueo previsto antes de montar sus vistas. Consultoras HTTP permanece en espera manual y sin datos inventados. En modo mock se comprobaron filas artificiales, estados, medidas responsive y aspectos básicos de teclado. No se encontraron defectos productivos que requirieran edición dentro de los casos revisados.

Se observaron **cero solicitudes a `/api/*`** durante los segmentos instrumentados de navegación y revisión. No se inició Spring Boot, no se cargaron credenciales y no se consultó SENACE/BCRP ni SQLite. No se modificó código, no se instalaron dependencias ni se realizaron commits.

Este cierre complementa el [reporte de integración 7C.2](REPORTE_INTEGRACION_FASE7C2_CONSULTORAS.md); no modifica sus antecedentes ni repite su consulta real. No certifica end-to-end todo EcoInvest.

## 2. Contexto y archivos inspeccionados

Se aplicaron AGENTS.md y los procedimientos pertinentes de frontend, testing y documentación del proyecto. Rutas relativas a la raíz:

- `frontend/src/api/source.ts`: configuración y permisos HTTP por funcionalidad.
- `frontend/src/app/navigation.ts`: resolución exacta del hash.
- `frontend/src/app/App.tsx`: bloqueo anterior a la selección de componente funcional.
- `frontend/src/features/consultoras/ConsultorasPage.tsx`: espera HTTP, carga mock, controles y foco.
- `frontend/src/features/consultoras/ConsultorasPage.css`: grids, contención de tabla, foco y controles.
- `documentacion/04_Fase4_Frontend/REPORTE_INTEGRACION_FASE7C2_CONSULTORAS.md`: evidencia previa y comprobación inconclusa.

No se editaron estos archivos. El único archivo creado en la etapa documental es este reporte.

## 3. Entorno y procedimiento autorizado

Se utilizaron dos procesos Vite separados en loopback, sin backend:

| Proceso | URL local | Configuración pública |
|---|---|---|
| Revisión HTTP | `http://127.0.0.1:5173/` | `VITE_API_MODE=http`, `VITE_API_BASE_URL=http://127.0.0.1:8081`, `VITE_HTTP_FEATURES=monitor,consultoras`. |
| Revisión artificial | `http://127.0.0.1:5174/` | `VITE_API_MODE=mock`, `VITE_API_BASE_URL` y `VITE_HTTP_FEATURES` vacíos. |

Comandos ejecutados desde `frontend/`, con variables públicas limitadas a sus respectivos procesos:

```powershell
# Proceso HTTP: el script existente arranca Vite en loopback.
npm run dev

# Proceso mock independiente, puerto explícito.
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5174 --strictPort
```

Se abrió Edge headless con un perfil temporal aislado y depuración en loopback. La automatización CDP usó scripts en memoria enviados a Node; no se crearon archivos auxiliares de código. Se instrumentaron eventos de red y excepciones JavaScript antes de las comprobaciones de cada segmento.

No se pulsó el botón de consulta de Consultoras en modo HTTP. El puerto backend no se utilizó para verificaciones HTTP directas. Las solicitudes de Vite, módulos y recursos locales necesarias para renderizar no son consultas `/api/*`.

## 4. Causa de la comprobación literal inconclusa

### 4.1. Pérdida de caracteres en el script de QA

Se reprodujo directamente un problema del canal PowerShell → Node: el literal JavaScript `Integración no habilitada`, enviado por la tubería usada para ejecutar el script, llegó a Node con `?` en lugar de `ó`.

La inspección de puntos de código produjo **63** para ese carácter (`?`), en lugar de **243** (`ó`). Por tanto, buscar ese literal alterado en un DOM con texto Unicode correcto devuelve false aunque el bloqueo esté visible.

Se repitió la comparación usando `Integraci\u00f3n no habilitada` dentro de la expresión enviada al navegador. Esta comparación dio true para ambas rutas después de esperar el elemento efectivo.

**Conclusión:** se comprobó una causa técnica de falso negativo del procedimiento de QA. No se encontró un defecto de codificación del título de la aplicación. Esta reproducción explica el riesgo del literal anterior, sin atribuir retrospectivamente todos los detalles de aquella ejecución a una única causa no registrada.

### 4.2. Espera del primer renderizado

En esta revisión, una navegación inicial a Dashboard seguida de una espera fija de 900 ms aún no devolvió el encabezado esperado. Una comprobación posterior que esperó la existencia de `#source-status-title` sí lo obtuvo correctamente.

Se sustituyó la espera fija del procedimiento por sondeo acotado del elemento real. No se modificó React ni se afirmó un tiempo universal de renderizado. La falta temporal de DOM también puede producir un false prematuro en este entorno.

### 4.3. Ruta

`resolveNavigation` reconoce exactamente hashes con barra: `#/dashboard`, `#/explorador`, `#/consultoras` y `#/monitor`. La forma `#dashboard` no es equivalente. Las comprobaciones concluyentes de esta revisión utilizaron las rutas correctas. No se atribuye el fallo previo a una ruta errónea sin evidencia.

## 5. Rutas bloqueadas en modo HTTP

| Comprobación | Dashboard | Explorador |
|---|---|---|
| Hash efectivo | `#/dashboard` | `#/explorador` |
| Encabezado observado | `Integración no habilitada` | `Integración no habilitada` |
| Comparación Unicode segura | true | true |
| Vista funcional montada | No se observó `.dashboard-page`. | No se observaron los contenedores de Explorador consultados. |
| Solicitudes API observadas | 0 | 0 |

Mensaje de estado observado:

> Integración HTTP no habilitada para esta pantalla. Solo se permiten las vistas expresamente configuradas; no se ejecutan consultas ni se sustituye la fuente por mocks.

La inspección de `App.tsx` confirma que `!access.allowed` selecciona la sección de bloqueo antes de las ramas que instancian DashboardPage o ExploradorPage. Los imports estáticos de módulos no equivalen a montar sus componentes React. `featureAccess` no concede permiso HTTP a ninguna de esas dos funcionalidades con la configuración revisada.

La combinación de código, DOM y red resuelve la comprobación pendiente del reporte anterior. El allowlist frontend sigue siendo un control operativo y no seguridad del servidor.

## 6. Consultoras en espera HTTP

En `#/consultoras` se observó:

- Encabezado `Consultoras Ambientales` y componente Consultoras montado.
- Aviso `MODO HTTP — BACKEND LOCAL` y descripción de consulta manual.
- Estado: `Sin resultado. Pulsa Consultar Consultoras por HTTP para iniciar una consulta manual.`
- Los tres KPI muestran `No disponible`, sin sustituir ausencia por cero.
- Búsqueda y los tres filtros permanecen deshabilitados antes de cargar filas.
- `Limpiar filtros` está deshabilitado sin filtros aplicados.
- Botón `Consultar Consultoras por HTTP` habilitado y de 44 px de altura.
- Selector de escenarios artificiales ausente.
- Cero solicitudes API durante la navegación y espera instrumentadas.

No se pulsó Consultar en HTTP, no se verificaron de nuevo credenciales ni datos oficiales y no hubo fallback a mocks.

## 7. Escenarios artificiales de Consultoras

Todos los datos de esta sección son **fixtures artificiales**, no observaciones SENACE. Se mantuvo visible `MODO DEMOSTRACIÓN — DATOS ARTIFICIALES` y `Sin conexión al backend`.

| Escenario seleccionado y aplicado | Estado renderizado | KPI observados | Filas DOM |
|---|---|---|---:|
| `consultorasDemoSuccess` | ready | 14 recibidas, 14 filtradas, 9 con resolución informada. | 10 |
| `consultantsEmpty` | empty | 0 / 0 / 0, vacío oficial simulado. | 0 |
| `consultants502` | error | No disponible en los tres KPI. | 0 |
| `consultants503` | error | No disponible en los tres KPI. | 0 |
| `consultorasDemoNoHeaders` | degraded | 14 / 14 / 9, sin acreditar procedencia por headers. | 10 |
| `consultorasDemoOptional` | ready | 2 / 2 / 0. | 2 |

Cambiar escenario requirió aplicar el botón de demostración; no se interpretaron los status artificiales como respuestas de red reales. No se ejecutó una nueva suite automatizada para estos escenarios: las comprobaciones anteriores fueron interacciones de navegador sobre la UI.

## 8. Responsive y composición

Se evaluaron los tres viewports solicitados. Las medidas en espera HTTP fueron:

| Viewport | clientWidth | scrollWidth | Filtros sin datos | Botones propios medidos |
|---|---:|---:|---|---|
| 1440 × 900 | 1425 | 1425 | Deshabilitados | 44 px |
| 768 × 1024 | 753 | 753 | Deshabilitados | 44 px |
| 375 × 812 | 360 | 360 | Deshabilitados | 44 px |

Con filas artificiales cargadas:

| Viewport | clientWidth documento | scrollWidth documento | Ancho contenedor tabla | scrollWidth tabla |
|---|---:|---:|---:|---:|
| 1440 × 900 | 1425 | 1425 | 1087 | 1296 |
| 768 × 1024 | 753 | 753 | 711 | 1296 |
| 375 × 812 | 360 | 360 | 326 | 1296 |

No se detectó desbordamiento horizontal global. La tabla mantiene desplazamiento horizontal dentro de su contenedor. El espacio de 15 px respecto del ancho solicitado es el scrollbar observado.

Se inspeccionaron imágenes temporales en memoria de los tres viewports con datos artificiales; no se guardaron archivos de captura. En la zona superior se observaron:

- Escritorio: sidebar lateral, encabezado con identidad, filtros en cuatro columnas y tres KPI en línea.
- Tablet: menú compacto, filtros en dos columnas y KPI apilados.
- Móvil: menú compacto, banner y filtros apilados, anchos contenidos y botón accesible sin desbordamiento global.

La revisión visual en memoria se limitó a los encuadres iniciales capturados. Las medidas de tabla y comprobaciones de detalle complementan esa observación, pero no sustituyen una revisión visual exhaustiva de cada panel inferior o una comparación píxel a píxel con Stitch. No se recuperó de nuevo el prototipo remoto ni se modificaron estilos.

## 9. Teclado y accesibilidad básica

Resultados directos:

- Todos los inputs/selects consultados de Consultoras tenían label asociado o nombre accesible por `aria-label`: cero controles sin etiqueta en esa comprobación.
- Los controles propios visibles medidos no tuvieron alturas inferiores a 44 px.
- Tab desde la búsqueda local llevó a un SELECT.
- El control enfocado mostró outline sólido de 3 px.
- Con la página del navegador en primer plano, Espacio sobre un botón de detalle abrió la ficha y trasladó el foco a `#consultants-detail-title`.
- La ficha abierta presentó ocho etiquetas de campo.
- Al cerrar mediante el botón de la ficha, el foco regresó al botón de la tabla.
- En espera HTTP se comprobó que Tab desde el botón de consulta permite continuar la navegación; no se activó la consulta.

Una primera secuencia sintética de Enter no abrió el detalle y no se consideró prueba de un defecto React. Se corrigió la preparación del procedimiento poniendo la página en primer plano y usando Espacio; la activación por teclado y el foco funcionaron. No se certifica específicamente la secuencia Enter inicial ni se afirma que el cierre de ficha fuera activado por teclado: ese cierre se accionó por click DOM para comprobar el retorno de foco.

Alcance limitado: no se ejecutaron lectores de pantalla, análisis automatizado WCAG, auditoría de contraste completa o pruebas exhaustivas de teclado de todo AppShell. No se detectó un bloqueo de accesibilidad en las comprobaciones realizadas.

## 10. Red, seguridad y ausencia de efectos externos

- Los listeners CDP se activaron antes de los segmentos de navegación revisados y no registraron solicitudes con pathname `/api/*`.
- No se inició Spring Boot ni se consultó el backend para verificar salud.
- No se cargó `backend/.env.apis.local` ni se leyeron valores de credenciales.
- No se consultaron SENACE, BCRP ni recursos de cartografía.
- No se accedió a SQLite.
- No se guardaron registros oficiales ni capturas con datos reales identificables.
- No se observaron eventos `Runtime.exceptionThrown` en los segmentos que instrumentaron excepciones; esto no es una auditoría exhaustiva de consola.

## 11. Defectos, cambios y validaciones no ejecutadas

| Hallazgo | Clasificación | Acción |
|---|---|---|
| Literal con `ó` convertido a `?` en PowerShell → Node | Defecto del procedimiento de comparación, reproducido. | Usar Unicode escapado o canal UTF-8 controlado en futuras automatizaciones. No requiere editar la aplicación. |
| Espera fija antes del primer renderizado | Fragilidad del procedimiento, observada. | Esperar un selector real mediante sondeo acotado. |
| Dashboard/Explorador bloqueados | Comportamiento esperado, confirmado. | Sin corrección productiva. |
| Consultoras en espera HTTP y estados mock | Comportamiento esperado en los casos observados. | Sin corrección productiva. |

No se modificó código. No se ejecutaron typecheck, tests o build nuevamente, porque no hubo cambios y las validaciones anteriores permanecen documentadas en el reporte de integración. No se presentan las 131 pruebas históricas como una ejecución nueva de este QA.

## 12. Cierre de recursos y estado final

Se cerró Edge, se detuvieron ambos procesos Vite y se eliminó únicamente el perfil temporal autorizado tras verificar su ruta absoluta dentro de TEMP. La comprobación final no encontró procesos escuchando en 8081, 5173, 5174 ni en el puerto temporal de depuración 60710.

**Cierre QA local 7C.2: satisfactorio dentro del alcance revisado.** La comprobación inconclusa de las rutas quedó resuelta sin cambios de código ni nuevas consultas oficiales. Los límites de accesibilidad y fidelidad visual se conservan explícitos.

No se inició 7C.3 ni se amplió el allowlist. Una siguiente fase de integración requiere un encargo explícito con archivos, comportamiento de consulta y presupuesto de tráfico separados. No es necesario repetir el GET oficial de Consultoras para resolver este pendiente, ya cerrado.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest mantiene Java 17.0.12/Spring Boot 3.2.4 y React. La integración real de Consultoras de 7C.2 ya estaba documentada: un GET, HTTP 200, 15.137 filas, origen API_DIRECTA_SENACE, cobertura NO_VERIFICADA y CORS legible. Esos datos no se volvieron a consultar en este QA.

Se resolvió el falso negativo de Dashboard/Explorador: el canal PowerShell → Node convierte el literal acentuado a un texto con `?`, y una espera fija también pudo preceder al renderizado. Comparando Unicode escapado y esperando `#source-status-title`, ambas rutas correctas `#/dashboard` y `#/explorador` mostraron `Integración no habilitada`. El código bloquea antes de montar sus vistas y no hubo solicitudes API observadas.

Consultoras HTTP quedó en espera manual, KPI desconocidos como No disponible, filtros deshabilitados y escenarios artificiales ocultos. Con Vite mock separado se verificaron ready, empty, 502/503, degraded y opcionales; no se atribuyeron fixtures a SENACE. Se midieron los tres tamaños sin desbordamiento global y con scroll de tabla local. Se comprobaron etiquetas, altura mínima de controles, Tab, foco visible y apertura de detalle mediante Espacio; el retorno de foco se verificó al cerrar por click. No se hizo auditoría WCAG completa ni comparación exhaustiva con Stitch.

No hubo cambios productivos, credenciales, Spring Boot, SQLite, instalaciones, commits ni nuevas ejecuciones de suites. Solo se creó este reporte tras autorización. Vite/Edge se cerraron y el perfil temporal se eliminó. El QA local puede darse por cerrado dentro de ese alcance; no declarar certificación end-to-end de las cuatro vistas ni avanzar a 7C.3 sin una nueva instrucción.
