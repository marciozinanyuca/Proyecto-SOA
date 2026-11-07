# EcoInvest — Optimización del Explorador Geográfico, Fase 7E

Fecha: 07/11/2026. Repositorio: `D:\Documentos\GitHub\Proyecto-SOA`.

## 1. Resumen y cierre de P01

Se resolvió el renderizado no acotado del Explorador sin dependencias adicionales, sin eliminar registros y sin modificar Dashboard, clientes API o backend.

La lista ahora representa **20 filas por página**. El plano representa las coordenadas correspondientes a esa página y, cuando corresponde, un punto adicional para la selección fuera de página: **máximo 21 puntos**. Las coordenadas exactamente coincidentes comparten un punto con contador y acceso a una lista paginada de todas sus filas. No se promedian posiciones ni se deduplican registros.

Se ejecutaron typecheck, **159 pruebas satisfactorias** y build. En el nuevo benchmark antes/después, con idénticos fixtures y procedimiento, 20.000 proyectos pasaron de **10.336–11.759 ms** a **216,7–217,2 ms** hasta el DOM observado. No se calculan porcentajes de mejora ni se presentan estos resultados como SLA.

**P01 se considera cerrado dentro del alcance medido:** lista/puntos acotados, registros accesibles, selección preservada, ausencia de GET duplicados y responsive comprobado. El procesamiento y almacenamiento de los datos completos siguen creciendo con su volumen; no se promete rendimiento ilimitado.

## 2. Contexto y evidencias previas

Se revisaron AGENTS.md, las instrucciones pertinentes del proyecto y:

- [Validación integral 7D y hallazgo P01](REPORTE_VALIDACION_INTEGRAL_FASE7D.md).
- [Implementación del Explorador, Lote 4](REPORTE_IMPLEMENTACION_FASE4B_LOTE4.md).
- [Integración Dashboard–Explorador 7C.3](REPORTE_INTEGRACION_FASE7C3_DASHBOARD_EXPLORADOR.md).

7D había observado aproximadamente 0,4/2,7/10,7 segundos con 1.000/5.000/20.000 proyectos artificiales. Esas cifras son antecedentes, **no la línea base numérica utilizada aquí**. Para comparar se midió nuevamente la versión previa, antes de editar, mediante el mismo script que después se repitió sobre la versión optimizada.

No se repitieron consultas oficiales: todo el benchmark y las nuevas regresiones de navegador utilizaron datos artificiales e inyección de Responses en memoria.

## 3. Diagnóstico del coste

El código anterior:

- Construía `entries`, `all`, estados, marcadores y límites en cada renderizado.
- Renderizaba todas las filas y todos los elementos de marcador SVG.
- Cambiar selección o zoom volvía a recorrer datos y reconciliar miles de nodos.
- El scroll de lista limitaba el área visible, no los nodos creados.
- La selección buscaba por recorrido de la lista y los límites utilizaban arrays y expansión en Math.min/Math.max.

La línea base separó procesamiento puro de navegación/DOM. El pipeline puro legado de filas/puntos tardó pocos milisegundos, mientras renderizar o actualizar la lista/SVG completos requirió cientos o miles. Por tanto, no se atribuyó la lentitud de React al transporte ni se introdujo una caché HTTP o una estrategia de red nueva.

## 4. Alcance autorizado y archivos

Se aprobaron exclusivamente estos cuatro archivos para implementación:

| Ruta relativa a la raíz | Cambio |
|---|---|
| `frontend/src/features/explorador/ExploradorPage.tsx` | Memoización, paginación, puntos de página/selección, coincidencias exactas y conteos transparentes. |
| `frontend/src/features/explorador/exploradorModel.ts` | Índice de búsqueda/posiciones, filtros, resumen espacial, paginación y grupos visibles. |
| `frontend/src/features/explorador/ExploradorPage.css` | Estilos locales de paginación, aviso de coincidencias y contador SVG. |
| `frontend/tests/api/explorador.test.mjs` | Pruebas de conservación, accesibilidad lógica de páginas, selección, límites y coincidencias. |

La línea base, implementación, comandos de validación, navegador posterior y este documento se autorizaron por etapas separadas.

No se modificaron backend, OpenAPI, SQLite, Dashboard, Monitor, Consultoras, clientes API, `dashboardSelectionStore.ts`, credenciales, políticas HTTP, dependencias o diseños remotos Stitch. Se preservaron cambios preexistentes. No se realizaron commits ni despliegues.

## 5. Solución implementada

### 5.1. Índice por respuesta

`createExplorerIndex` prepara una vez por snapshot conocido:

- Filas en orden original y claves técnicas por posición, sin usar ID empresarial o ID de proyecto como clave única.
- Campos normalizados para búsqueda, conservando los valores originales.
- Coordenadas utilizables y su posición SVG.
- Estados disponibles.

`filterIndexedExplorer` aplica estado/búsqueda sobre todas las filas, no solo la página. `explorerSpatial` construye lookup por clave, coincidencias y límites mediante recorrido, sin expansión de arrays a Math.min/Math.max.

En React se reutilizan esos resultados con useMemo según snapshot/filtros. Selección y zoom no reconstruyen todo el índice o filtrado. La ficha obtiene la fila por Map, en lugar de buscarla linealmente en cada selección.

### 5.2. Lista paginada

`paginateExplorer` presenta un máximo de 20 filas, conserva orden/duplicados y permite:

- Página anterior/siguiente.
- Ir directamente a una página mediante input numérico.
- Buscar y filtrar sobre el conjunto completo.
- Ver cuántas filas se muestran y cuántas cumplen la selección.

No se recorta el snapshot ni se descartan registros para acelerar el renderizado. Los proyectos sin coordenadas siguen accesibles.

### 5.3. Puntos acotados y coincidencias

`pagePointGroups` representa las coordenadas de las filas de la página y añade el punto seleccionado si está fuera de ella. Hay como máximo 21 coordenadas representadas; varias filas pueden compartir un único punto.

La agrupación es por **igualdad exacta de latitud/longitud numéricas recibidas por JavaScript**, sin redondeo, promedio o desplazamiento. No es clustering espacial ni certifica CRS o precisión decimal más allá de los límites existentes del navegador.

- Un punto de una sola fila selecciona esa ficha.
- Un punto con varias filas muestra contador y abre la lista de coincidencias exactas, también paginada de 20 en 20.
- Se puede volver al conjunto filtrado completo.
- El contador representa filas filtradas con esa coordenada, no proyectos únicos o empresas certificadas.
- No se inventan posiciones para datos ausentes o fuera de rango.

Los límites del viewBox se calculan sobre toda la selección filtrada y permanecen estables al paginar. El zoom conserva su lógica previa y no utiliza un proveedor cartográfico.

### 5.4. Selección y transparencia

Cambiar página conserva la ficha seleccionada y, si hay coordenadas, su punto. Si la selección está fuera de la página, se informa en la lista. Cambiar estado/búsqueda reinicia selección, página, zoom y restricción de coincidencias según corresponde, para no mantener una ficha ajena al filtro.

La UI informa separadamente:

- Registros de la respuesta disponible.
- Registros filtrados.
- Filas con coordenadas en rango.
- Puntos actualmente representados.
- Página y subconjunto de coincidencias, cuando aplica.

Se conserva la advertencia del esquema SVG offline, sin mapa base, CRS certificado, regiones, expedientes o ubicación real acreditada. No se presenta el subconjunto dibujado como todo el universo de datos.

## 6. Validaciones automáticas efectivamente ejecutadas

Fecha: 07/11/2026. Directorio: `frontend/`.

```powershell
npm run typecheck
npm run test:api
npm run build
```

| Validación | Resultado |
|---|---|
| Typecheck | Código de salida 0. |
| Suite frontend | **159 pruebas, 159 pass, 0 fail, 0 cancelled, 0 skipped**. |
| Build | Código 0, Vite 8.3.5, 48 módulos. |

La suite final conserva las regresiones anteriores y agrega ocho casos efectivos de 7E. No es una suma de suites históricas. No se ejecutó Maven, Java o pruebas backend; Oracle JDK 17.0.12 y Spring Boot permanecieron sin cambios.

Cobertura añadida:

- Conservación de orden, RUC String, opcionales y filas duplicadas con claves técnicas distintas.
- Acceso a los 1.000/5.000/20.000 registros recorriendo todas las páginas, sin pérdidas ni repeticiones de clave.
- Cota de 20 filas y puntos correspondientes a página.
- Selección fuera de página y máximo de 21 puntos.
- Coincidencias exactas sin promediar, redondear o deduplicar.
- Diferencia entre consulta desconocida, vacío oficial y lista sin coordenadas.
- Encuadre global estable entre páginas y normalización de números de página inválidos.

La comprobación estática del parche no detectó errores de whitespace; Git avisó de conversiones LF/CRLF existentes sin reformateo global. No se editaron artefactos generados manualmente.

## 7. Metodología antes/después

### 7.1. Entorno y fixtures

Se utilizó Vite 8.3.5 en desarrollo, Edge headless con perfil temporal nuevo para cada versión, viewport **1440 × 900**, depuración loopback y memoria JS observada con información precisa habilitada.

Configuración pública de Vite, sin backend:

```text
VITE_API_MODE=http
VITE_API_BASE_URL=http://127.0.0.1:8081
VITE_HTTP_FEATURES=monitor,consultoras,dashboard,explorador
```

Comando desde `frontend/`:

```powershell
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort
```

Una sustitución temporal de window.fetch devolvía un Response artificial exclusivamente para Dashboard. No se añadió ese código al repositorio. Se bloqueó el destino API real en CDP y se instrumentaron solicitudes API/externas y excepciones.

Los fixtures de 1.000/5.000/20.000 proyectos fueron idénticos en ambas versiones:

- Un tercio aproximado sin coordenadas.
- Un tercio con coordenadas coincidentes (-12, -70).
- Un tercio con coordenadas distintas y en rango.
- IDs repetidos, estados alternados y RUC opcional textual.
- Títulos y titular expresamente artificiales.

Por cada tamaño se ejecutaron **dos muestras**, en orden ascendente. Cada muestra navegó nuevamente a Dashboard, instaló datos artificiales, pulsó la consulta una vez y abrió Explorador reutilizando el snapshot. No hubo GET real ni consultas adicionales para abrir Explorador.

### 7.2. Qué se midió

- Navegación: desde cambio de hash hasta observar filas y completar dos requestAnimationFrame.
- Selección: click en la primera fila, aparición de ficha y dos frames.
- Zoom: acción Acercar y dos frames.
- Búsqueda: título exacto del último registro, hasta una fila coincidente y dos frames.
- DOM: elementos activos con `document.querySelectorAll('*').length`, no contadores acumulados de nodos desconectados.
- Heap: `performance.memory.usedJSHeapSize`, sin GC forzado.
- Procesamiento puro: tres recorridos del pipeline legado de filas/puntos como control separado, no como medición completa del índice nuevo.

Se reutilizó el mismo script de medición antes/después. La paginación es nueva y se midió solo después, sin inventar un valor anterior equivalente.

### 7.3. Límites de la comparación

Los tiempos incluyen coordinación CDP, sondeo, React y oportunidad de frames; no aíslan tiempo exclusivo de CPU o primera pintura interactiva. La selección y los controles respondieron en los casos ejercitados, pero el nombre "hasta DOM observado" es más preciso que una certificación universal de interactividad.

Solo hay dos muestras por tamaño, con distinto calentamiento de JIT/GC y posibles efectos del sistema. Los perfiles son nuevos por versión, pero se reutilizan dentro de sus muestras. No hay control estadístico suficiente para SLA, percentiles o porcentajes garantizados de mejora. Se registran los rangos y todas las muestras, sin porcentajes.

## 8. Resultados de benchmark

### 8.1. Tiempo hasta DOM observado y tamaño de renderizado

| Proyectos | Antes: dos muestras (ms) | Después: dos muestras (ms) | Filas DOM antes → después | Puntos SVG antes → después | Elementos activos antes → después |
|---|---|---|---|---|---|
| 1.000 | 1138,5 / 1036,1 | 58,7 / 50,8 | 1000 → 20 | 666 → 7 | 9160 → 334 |
| 5.000 | 3393,3 / 3248,8 | 86,3 / 76,1 | 5000 → 20 | 3333 → 7 | 45161 → 334 |
| 20.000 | 11759,2 / 10336,4 | 216,7 / 217,2 | 20000 → 20 | 13333 → 7 | 180161 → 334 |

Los siete puntos posteriores corresponden a las coordenadas de la primera página del fixture, no a una constante de producción. En otra página o selección pueden existir hasta 21 puntos. Los registros completos permanecen en memoria y accesibles mediante lista/búsqueda/paginación.

### 8.2. Selección, zoom y búsqueda

Valores en milisegundos, muestra 1 / muestra 2:

| Proyectos | Selección antes | Selección después | Zoom antes | Zoom después | Búsqueda antes | Búsqueda después |
|---|---|---|---|---|---|---|
| 1.000 | 675,0 / 1060,3 | 23,6 / 34,0 | 190,5 / 1005,3 | 27,2 / 34,4 | 95,4 / 103,7 | 21,5 / 23,2 |
| 5.000 | 1797,8 / 1920,5 | 25,7 / 36,9 | 1655,8 / 1616,2 | 29,0 / 29,6 | 342,7 / 335,7 | 23,8 / 26,2 |
| 20.000 | 5649,7 / 5333,4 | 32,9 / 33,6 | 5714,6 / 5053,6 | 27,8 / 34,6 | 1176,6 / 1179,1 | 27,0 / 20,0 |

Una acción posterior de paginación en 20.000 filas tardó **27,5 ms**, y abrir la lista de **6.667 coincidencias exactas** tardó **26,5 ms** en el recorrido medido. Son observaciones únicas, no percentiles.

### 8.3. Heap JS observado

Bytes por muestra, sin GC forzado:

| Proyectos | Antes, muestras 1 / 2 | Después, muestras 1 / 2 |
|---|---|---|
| 1.000 | 29.164.492 / 55.910.493 | 15.264.443 / 19.247.788 |
| 5.000 | 42.032.805 / 167.975.897 | 17.372.685 / 39.168.920 |
| 20.000 | 134.555.660 / 538.997.495 | 62.366.127 / 108.935.486 |

La fuerte variabilidad del heap anterior muestra efectos de recolección y objetos aún vivos de recorridos previos. No se interpreta como fuga demostrada ni se garantiza ahorro porcentual. El índice, snapshot y datos completos siguen ocupando memoria proporcional a N.

### 8.4. Procesamiento puro como control

En la línea base, el recorrido legado de filas/puntos medido tres veces por muestra quedó aproximadamente entre 0,2–2,4 ms para 1.000, 0,5–0,9 ms para 5.000 y 6,0–9,5 ms para 20.000. Después, el mismo control quedó aproximadamente entre 0,1–0,5, 0,4–1,1 y 1,5–7,7 ms respectivamente.

Este control no mide construcción del nuevo índice ni permite atribuirle una mejora intrínseca a las funciones legadas; su variabilidad depende del entorno. La evidencia principal es el DOM acotado y el descenso del coste observado de navegación/actualizaciones, no una supuesta aceleración de transporte o JavaScript puro.

## 9. Regresiones en navegador

Todas las filas utilizadas fueron artificiales.

| Comprobación | Resultado observado |
|---|---|
| Navegación Dashboard → Explorador | Una invocación artificial Dashboard por muestra; abrir Explorador no volvió a cargar. |
| Selección fuera de página | Ficha del registro artificial 2 conservada, 20 filas, 9 puntos y un punto seleccionado tras pasar de página. |
| Coincidencias exactas | Un punto abrió 6.667 filas en 334 páginas, sin dibujar miles de puntos superpuestos. |
| Última página de coincidencias | 7 filas; el último registro artificial 19999 fue accesible. |
| Teclado | Espacio sobre la última fila seleccionó la ficha correspondiente. |
| Volver al conjunto filtrado | Control de salida de coincidencias conservó acceso al conjunto. |
| Filtro Aprobado | 10.000 registros filtrados, máximo 20 filas DOM y selección anterior limpiada. |
| Regresar a Dashboard | Snapshot restaurado sin nueva invocación artificial. |
| Sin coordenadas | Nueva respuesta artificial de 6 filas: lista de 6, cero puntos y mensaje "Sin coordenadas utilizables. Los proyectos siguen disponibles en la lista." |
| Red real | Cero solicitudes API/externas observadas en los recorridos instrumentados. |
| JavaScript | Cero eventos Runtime.exceptionThrown en los segmentos instrumentados del benchmark y regresión. |

La ausencia de pérdida en todas las páginas se comprobó exhaustivamente en tests para los tres tamaños; en navegador se recorrieron páginas concretas, incluida la última de coincidencias. No se pulsaron manualmente las mil páginas de 20.000 registros.

La suite conservó cancelación, respuestas obsoletas, HTTP/mock, RUC textual, opcionales y duplicados. No se cambiaron ni se volvieron a consultar Monitor o Consultoras; su preservación se apoya en alcance de código y suite, no en nuevas validaciones oficiales.

## 10. Responsive, presentación y accesibilidad

Sobre la respuesta artificial de 20.000 filas, tras filtrar Aprobado:

| Viewport | clientWidth | scrollWidth | Filas DOM | Puntos SVG | Controles de paginación bajo 44 px |
|---|---:|---:|---:|---:|---:|
| 1440 × 900 | 1425 | 1425 | 20 | 8 | 0 |
| 768 × 1024 | 753 | 753 | 20 | 8 | 0 |
| 375 × 812 | 360 | 360 | 20 | 8 | 0 |

No se observó desbordamiento global. Se conservaron composición de paneles, paleta, SVG offline, ficha y lista; se añadieron únicamente controles locales y avisos necesarios para explicar la representación parcial.

El marcador de coincidencias tiene nombre accesible y contador visible; Enter/Espacio mantienen activación prevista en el código, y Espacio sobre fila fue probado en navegador. No se certifica tamaño táctil mínimo de todos los puntos SVG ni auditoría WCAG completa.

No se guardaron capturas ni se recuperó Stitch remoto. La validación de presentación se limitó a DOM, dimensiones e interacciones; no se declara comparación visual píxel a píxel ni revisión exhaustiva de todos los textos en distintos dispositivos.

## 11. Seguridad y cierre de recursos

- No se inició Spring Boot ni se ejecutó Maven.
- No se cargaron credenciales o archivos .env.
- No se consultaron SENACE/BCRP ni cartografía externa.
- No se accedió a SQLite ni se modificó backend/OpenAPI.
- No se guardaron cuerpos completos, datos reales, capturas o auxiliares de benchmark en el repositorio.
- El harness de benchmark se ejecutó en memoria mediante PowerShell/Node/CDP y no forma parte del producto o de sus fixtures persistentes.
- Tras línea base y validación posterior se cerraron Edge y Vite y se eliminaron únicamente sus perfiles temporales autorizados, validando antes las rutas dentro de TEMP.
- La comprobación final no encontró procesos escuchando en 8081, 5173 o los puertos temporales 59659/54724.

## 12. Limitaciones conservadas

1. El plano representa puntos de página y selección, no todas las ubicaciones simultáneamente; la UI lo informa. La visión global conserva el encuadre de la selección filtrada.
2. La coincidencia exacta usa Number de JavaScript y sus límites de precisión ya aprobados. No se certifica igualdad geodésica ni CRS.
3. Puntos próximos pero no idénticos todavía pueden superponerse visualmente; no hay clustering espacial. Todas las filas siguen accesibles desde lista/búsqueda.
4. El procesamiento de índices y filtros y la memoria del dataset completo siguen siendo O(N). No se ensayaron volúmenes mayores de 20.000.
5. Los tiempos son de desarrollo y un solo equipo, dos muestras por tamaño; incluyen costes de observación y frames. No son resultados de producción, SLA o garantía de latencia.
6. El heap no es memoria retenida estable ni prueba de ausencia de fugas.
7. No se obtuvo certificación WCAG, revisión visual exhaustiva o nueva evidencia oficial end-to-end.

## 13. Estado final y siguiente paso

**Fase 7E completada dentro del alcance autorizado. P01 cerrado para los tamaños y casos medidos**, con las limitaciones anteriores. El límite verificable de renderizado es 20 filas y hasta 21 puntos; la conservación de todos los registros está respaldada por tests y recorridos de navegador.

No se inició otro lote ni un despliegue. No se actualizaron README o reportes históricos por asociación.

Siguiente paso recomendado: revisar este reporte y elegir el pendiente prioritario de continuidad. Si se solicita un cierre visual adicional, autorizar capturas y revisión específica de contador/paginación antes de atribuir fidelidad completa. Si se requieren mapas globales o volúmenes superiores, tratarlos como diseño independiente sin reintroducir DOM ilimitado o consultas duplicadas.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest conserva Java 17.0.12/Spring Boot 3.2.4 y frontend React. P01 de 7D era el renderizado no acotado del Explorador. En 7E se modificaron solo ExploradorPage.tsx, exploradorModel.ts, ExploradorPage.css y explorador.test.mjs, con autorización. No hubo cambios backend/OpenAPI/Dashboard/Monitor/Consultoras/clientes/dependencias ni commits.

La lista usa 20 filas por página con salto directo. El mapa SVG offline muestra puntos de esa página y, si corresponde, el seleccionado fuera de ella, hasta 21. Coordenadas exactamente coincidentes comparten punto con contador y permiten abrir todas sus filas paginadas; no hay promedios, coordenadas añadidas, deduplicación o clustering externo. Índices y filtros se memoizan; selección y zoom no recalculan el dataset completo. Los conteos distinguen respuesta, filtrados, coordenadas y puntos dibujados.

Typecheck, **159 tests** y build pasaron. Un nuevo benchmark antes/después, con el mismo script, fixtures mixtos y dos muestras por tamaño, midió 20.000 proyectos: navegación al DOM **10.336–11.759 ms antes / 216,7–217,2 ms después**; selección aproximadamente 5.333–5.650 / 32,9–33,6 ms y zoom 5.054–5.715 / 27,8–34,6 ms. La primera página pasó de 20.000 filas/13.333 marcadores a 20 filas/7 puntos y de 180.161 a 334 elementos activos. No calcular porcentajes garantizados; GC, frames y calentamiento limitan la comparación.

Navegador comprobó selección fuera de página, 6.667 coincidencias en 334 páginas, acceso a la última fila, teclado, filtro de 10.000 aprobados, snapshot sin nueva carga y seis registros sin coordenadas accesibles en lista. Responsive en 1440/768/375 sin overflow. No se guardaron capturas ni se hizo comparación píxel a píxel con Stitch.

Todo fue artificial/local: cero consultas oficiales, sin credenciales, backend o SQLite. Procesos cerrados y perfiles eliminados. **P01 puede cerrarse en el alcance medido**, conservando límites de memoria O(N), datos completos en sesión y posible superposición de puntos próximos. No iniciar otro lote o despliegue automáticamente; revisar el reporte para decidir continuidad.
