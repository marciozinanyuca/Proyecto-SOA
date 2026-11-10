# EcoInvest — Diseño Fase 4A: arquitectura y preparación del frontend

Fecha: **04/11/2026**, America/Lima. Estado: **diseño inicial aprobado; implementación pendiente de autorización específica**.

Este documento se creó después de presentar el plan y recibir autorización. No se crearon componentes React, configuraciones frontend ni fixtures; no se instalaron paquetes, ejecutaron pruebas, servidores, Maven o solicitudes HTTP. No se modificaron backend, OpenAPI, SQLite, BCRP, SENACE, Resilience4j, índice documental o reportes históricos.

## 1. Estado inicial del frontend y continuidad

La inspección de `frontend/`, incluidos elementos ocultos, no encontró archivos. No existe una aplicación React, package.json, configuración Vite, router o servicio API que deba conservarse o ampliarse. No se debe presentar este diseño como una aplicación ya implementada.

La búsqueda de recursos visuales habituales dentro del repositorio no encontró imágenes, SVG, archivos Figma o nombres de prototipo reutilizables. Esto no prueba que no existan prototipos externos: la ruta externa mencionada históricamente no fue inspeccionada en esta fase. No se crearon logos ni se atribuyó una implementación a un prototipo inexistente en el repositorio.

Se localizaron las seis skills EcoInvest bajo `.agents/skills/`. Se aplicó principalmente frontend, con criterios de arquitectura y seguridad y el procedimiento documental para este entregable. Su disponibilidad no concede permisos de instalación, red o ejecución.

Node y npm aparecen como comandos disponibles en el entorno. **No se ejecutaron para comprobar versiones ni se verificó compatibilidad con una versión concreta de Vite**. Esa comprobación debe preceder a cualquier instalación autorizada.

### Backend de referencia

| Estado | Evidencia |
|---|---|
| Implementación pública SENACE en cuatro lotes | [Lote 4](../03_Fase3_SENACE/REPORTE_IMPLEMENTACION_FASE3B2_LOTE4.md) |
| Decisiones D01–D19 aprobadas | [Decisiones SENACE](../03_Fase3_SENACE/REPORTE_DECISIONES_FASE3B1_SENACE.md) |
| 32 pruebas integradas con transporte simulado | [Fase 3C.1](../03_Fase3_SENACE/REPORTE_VALIDACION_FASE3C1_SENACE.md) |
| Dos consultas Java reales independientes satisfactorias, proyectos/consultoras | [Fase 3C.2](../03_Fase3_SENACE/REPORTE_VALIDACION_FASE3C2_SENACE.md) |
| Apto para comenzar frontend con contratos/mocks; operación integral pendiente | [Cierre backend 3C.3](../03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md) |

No se suman las ejecuciones anteriores como una suite actual. Java se mantiene en 17 / Oracle JDK 17.0.12 y Spring Boot en 3.2.4. BCRP Fase 2C conserva su cierre técnico. No existe certificación end-to-end de toda la aplicación.

## 2. Arquitectura propuesta

Aplicación cliente React + TypeScript organizada por funcionalidades, con infraestructura HTTP común y componentes visuales reutilizables. El navegador consulta exclusivamente EcoInvest para obtener los datos gubernamentales; no recibe ni necesita auth_key SENACE.

```text
React / vistas
  → hooks y adaptadores por funcionalidad
  → cliente HTTP común
  → API EcoInvest (contrato OpenAPI vigente)
  → integraciones backend existentes SENACE/BCRP
```

Un origen de datos intercambiable permitirá empezar con fixtures artificiales y sustituirlos por HTTP real de forma progresiva. Deben compartirse los tipos y adaptadores: evitar un segundo modelo mock incompatible con el contrato.

El estado de la interfaz debe depender del contenido y metadatos, además del HTTP. La procedencia no se deduce de la primera fila ni la disponibilidad del proveedor del estado del circuito.

### Navegación

Cuatro destinos, propuestos mediante hash en el lote inicial:

```text
#/dashboard
#/explorador
#/consultoras
#/monitor
```

Se recomiendan enlaces semánticos, título de vista y foco accesible al cambiar pantalla. El hash evita exigir al alojamiento una regla de reescritura SPA y permite comenzar sin una dependencia de router. Si aparecen rutas anidadas, enlaces complejos o necesidades de navegación que lo justifiquen, evaluar un router con aprobación de dependencia; no implementarlo por costumbre.

## 3. Tecnologías y justificación

| Tecnología | Propuesta y motivo | Estado |
|---|---|---|
| React | Composición de cuatro vistas y controles reutilizables | Objetivo aprobado; no instalado |
| TypeScript estricto | Modelado de opcionales, estados y adaptadores del contrato | Propuesto para inicialización |
| Vite con soporte React/TS | Construcción de una SPA cliente que consume backend independiente | Diseño aprobado; versiones/compatibilidad pendientes de comprobar |
| CSS | Tokens, layout responsive y estilos de componentes sin framework adicional inicial | Propuesto |
| fetch / AbortController | HTTP y cancelación nativos sin una librería de cliente adicional | Propuesto |
| Estado React | Filtros, estado de solicitudes y reutilización mínima de resultados | Sin gestor global adicional inicialmente |
| Generador OpenAPI/TypeScript | Mantener tipos alineados con el contrato | Herramienta concreta pendiente de evaluación y autorización |
| Mapa | Biblioteca y cartografía por seleccionar | Decisión separada antes del Lote 4 |
| Herramientas de tests/lint | Verificación de tipos, adaptadores, componentes y flujos controlados | Paquetes y comandos exactos por proponer antes de instalar/ejecutar |

No se han seleccionado versiones “más recientes” ni se ha verificado disponibilidad remota. No hubo consulta HTTP de documentación o registro de paquetes en esta fase. Antes de instalar, confirmar Node/npm, requerimientos de las versiones elegidas, dependencias directas y scripts previstos; no actualizar el entorno silenciosamente si existe incompatibilidad.

No se justifica ahora añadir bibliotecas de estado, tablas, gráficos, UI o HTTP. Cada necesidad se revisará con el lote afectado.

## 4. Organización de carpetas y archivos previstos

La siguiente estructura es propuesta, **no creada**:

```text
frontend/
  package.json
  package-lock.json                 # si se adopta npm; generado en instalación autorizada
  index.html
  vite.config.ts
  tsconfig*.json                    # nombres concretos al aprobar el scaffold
  src/
    main.tsx
    vite-env.d.ts
    app/
      App.tsx
      AppShell.tsx
      navigation.ts
      config.ts
    api/
      client.ts
      types.ts                     # punto de acceso; generado o reexportado según decisión
      generated/                   # solo si se aprueba generación
      adapters/
    components/
      AsyncState.tsx
      ProvenancePanel.tsx
      FilterBar.tsx
      MetricCard.tsx
      DataTable.tsx
    features/
      dashboard/
      explorador/
      consultoras/
      monitor/
    mocks/
      fixtures.ts
      mockApi.ts
    styles/
      tokens.css
      global.css
```

Los nombres son un inventario inicial, no autorización para crear todos los archivos de una vez. Cada lote presentará la lista exacta y comprobará si la carpeta continúa vacía o contiene trabajo nuevo. No ejecutar un scaffold que sobrescriba trabajo existente.

Los componentes compartidos deben surgir de usos concretos. No añadir una abstracción compleja o un sistema de diseño completo antes de necesitarlo.

## 5. Estrategia OpenAPI / TypeScript

Fuente única: [backend/src/main/resources/openapi.yaml](../../backend/src/main/resources/openapi.yaml). No se modifica durante frontend.

| Operación | Entrada | Salida / códigos relevantes |
|---|---|---|
| GET /api/dashboard | sector obligatorio; anio opcional default 2017 | DashboardResponse; 200 completo o degradado |
| GET /api/consultoras | Sin parámetros | Array ConsultoraSenacePublica; 200/502/503; metadatos en headers |
| GET /api/senace/datastream | nombre opcional default CarteraProyectos | Envolvente con data array/objeto/null; 200/502/503 |
| GET /api/estado-servicios | Sin parámetros | EstadoServiciosResponse; 200; métricas lógicas, sin llamadas externas |

El esquema no declara propiedades required en muchos modelos. Los tipos no deben prometer que total, proyectos, fechas o metadatos opcionales siempre estarán presentes. La implementación actual rellena algunos campos sistemáticamente, pero eso no cambia por sí solo la obligatoriedad contractual.

### Generación y adaptación

1. Evaluar una herramienta de generación de tipos únicamente, evitando un cliente pesado innecesario. Confirmar su compatibilidad con el contrato y el entorno antes de proponer instalación.
2. Si se autoriza, generar en frontend sin tocar target/ ni modelos Java. Establecer un comando explícito y revisión de cambios generados.
3. Si no se aprueba una herramienta inicial, un subconjunto manual debe citar el contrato, mantener opcionales y tener comprobaciones de adaptación; no crear un modelo paralelo que se considere nueva fuente de verdad.
4. Los tipos estáticos no validan el JSON de red. Añadir adaptadores proporcionados para comprobar estructura y discriminar estados, especialmente objetos genéricos.
5. data del genérico debe tratarse como unknown hasta verificar recurso/forma/estado. No exponer al UI el JsonNullable interno Java: el navegador recibe array, objeto o null normales.

Los campos del genérico de recursos verificados conservan los nombres oficiales de DTO, por ejemplo ID/ACTIVIDAD/FECHA_INICIO. El dashboard y la ruta pública de consultoras usan sus modelos camelCase; no confundir ambas representaciones.

### Precisión numérica

- RUC permanece string, con ceros iniciales si existen.
- Un int64 JSON puede superar Number.MAX_SAFE_INTEGER. JSON.parse convencional puede perder precisión antes de que un adaptador lo examine; convertir a BigInt después no recupera los dígitos perdidos.
- BigDecimal serializado como número JSON se interpreta como number convencional y no conserva precisión arbitraria. La UI no debe afirmar aritmética económica exacta sobre ese tipo.
- Para presentación inicial, definir formato/redondeo explícito y no alterar el significado de unidad/período. Antes de un requerimiento de precisión exacta o identidad con enteros grandes, acordar parser/representación apropiados y aprobar cualquier dependencia.
- No cambiar el contrato backend a strings por conveniencia del frontend ni instalar una librería decimal sin necesidad concreta.

## 6. Cliente API, configuración y estados

### Configuración pública

Nombre propuesto: `VITE_API_BASE_URL`, con URL del backend EcoInvest, por ejemplo el servidor local declarado en OpenAPI cuando la integración real se autorice. Las variables VITE_* son públicas en el bundle: nunca colocar SENACE_AUTH_KEY, credenciales o contenido de archivos locales backend bajo ese prefijo.

La selección mocks/HTTP debe ser explícita y legible. Los fixtures llevarán una indicación visible de modo de demostración; no cambiar silenciosamente a datos artificiales cuando una solicitud real falle.

### Comportamiento HTTP común

- Construir parámetros con URL/URLSearchParams y enviar sector expresamente en dashboard.
- Conservar status, headers y resultado de lectura. No descartar el cuerpo de un 502/503 sin interpretar la política del endpoint.
- Leer X-Senace-Origen, X-Senace-Estado, X-Senace-Cobertura y opcionales X-Senace-Motivo/X-Senace-Cuerpo-Completo. Interpretar headers como texto, contemplando valores ausentes o no admitidos.
- Distinguir fallo de transporte navegador, cancelación, HTTP de error, JSON inválido y resultado degradado del negocio.
- No publicar cuerpos externos o detalles sensibles en consola. Mensajes de usuario seguros y trazables a motivos conocidos.
- Usar AbortController y número/clave de solicitud para cancelar o descartar resultados antiguos cuando cambien filtros. No permitir que una respuesta de un año anterior sobrescriba el nuevo filtro.
- Cancelar una solicitud del navegador no garantiza cancelar el trabajo ya iniciado en el proveedor desde el backend.
- Iniciar peticiones por acciones o montaje controlados. No consultar datasets completos en cada pulsación; filtros locales deben trabajar sobre datos existentes.
- Reutilizar resultados por clave de consulta dentro de la sesión y compartir selección/proyectos entre dashboard y explorador. Esta reutilización frontend no implica una nueva caché o persistencia backend.

### Modelo de estados

| Estado UI | Significado |
|---|---|
| loading | Solicitud o transición en curso; distinguir si se conserva una selección anterior |
| ready | Datos utilizables para la vista con procedencia disponible |
| empty | Vacío conocido; diferenciar vacío oficial y filtro sin coincidencias |
| degraded | Una fuente/indicador ausente o calidad parcial, con información restante utilizable |
| error | No hay resultado utilizable para la operación; conservar HTTP/motivo seguro |

Un HTTP 200 del dashboard puede ser degraded. Un [] con HTTP 503 en consultoras es error y no un conteo oficial cero. Una propiedad ausente, null o un indicador sin observación no se convierten en cero ni en un año vecino.

### Fixtures iniciales

Preparar JSON artificial mínimo para éxito, vacío oficial, sin coincidencias, fechas no evaluables, error 502, error 503, SENACE caído/BCRP disponible e indicador económico ausente. Incluir casos de cancelación/respuesta obsoleta y headers opcionales.

No copiar datasets ni datos personales reales. Un fixture con origen API_DIRECTA_SENACE solo representa un escenario contractual artificial; la pantalla debe dejar claro que está en modo mocks.

## 7. Diseño de las cuatro vistas

### 7.1. Dashboard Analítico

Estructura: título/contexto → filtros sector/año de inicio → tarjetas de registros/aprobados/indicador → lista o tabla de proyectos → procedencia, cobertura y advertencias.

- Consumir /api/dashboard y mostrar los valores realmente disponibles.
- Total y aprobados son registros filtrados, no proyectos únicos certificados.
- Año inicial 2017 según contrato; rotular “año de inicio”, nunca aprobación.
- Mostrar descripción, ámbito, unidad y período del indicador BCRP. TODOS implica contexto de PBI nacional, no agregado económico de proyectos.
- Conservar la actividad SENACE original y distinguir la normalización de búsqueda de una equivalencia con sector BCRP.
- Si falla SENACE, conservar indicador BCRP disponible y ocultar/marcar como desconocidas las métricas SENACE.
- No añadir inversión, moneda, región o expediente ausentes.

### 7.2. Explorador Geográfico

Estructura: filtros/contexto compartidos → área de mapa y panel de lista/detalle → conteo de registros con/sin coordenadas y advertencia de cobertura.

- Reutilizar los proyectos disponibles del dashboard para la misma selección. No existe endpoint de mapa dedicado.
- Una vista sin filtro anual podría usar el genérico CarteraProyectos, pero debe aprobarse su alcance/volumen y adaptar nombres oficiales; no cargarlo automáticamente además del dashboard.
- Mostrar únicamente puntos con coordenadas utilizables; no sustituir ausencia por (0,0).
- La plausibilidad en grados globales no certifica CRS, región ni ubicación efectiva. Mantener la advertencia y no inventar divisiones geográficas.
- Proporcionar lista accesible alternativa al mapa y selección sincronizada; no depender exclusivamente del color o del ratón.
- Biblioteca de mapa, cartografía base y solicitudes de tiles quedan pendientes. Requieren revisar necesidad, atribución/licencia, privacidad, volumen y autorización de tráfico/dependencias antes de implementar el Lote 4.

### 7.3. Consultoras Ambientales

Estructura: estado/procedencia → búsqueda y filtros locales → tabla paginada visualmente → detalle de ocho campos y conteo de filas.

- Campos: razonSocial, actividad, subsector, tipoSolicitud, resolucion, fechaRd, inicioVigenciaRd y finVigenciaRd.
- No deduplicar por razón social; no llamar a cada fila empresa única.
- No inventar RUC, ID, registro, nivel, estado o cálculo de vigencia.
- Conservar fechas originales; blanco opcional se presenta como “no disponible” sin significado administrativo añadido.
- Para grandes volúmenes, comenzar con paginación de presentación y filtrado local que no renderice todas las filas. Esa paginación no se debe anunciar como paginación oficial o ahorro de descarga.
- Una clave técnica de fila para React no es identificador oficial y no debe mostrarse como tal.

### 7.4. Monitor de Salud SOA

Estructura: explicación del alcance → tarjeta por proveedor → estado del circuito y métricas → limitaciones de disponibilidad y respaldo.

- Consumir /api/estado-servicios, sin crear nuevas comprobaciones de proveedor desde el navegador.
- Rotular métricas de llamadas lógicas y circuit breaker. Circuito CLOSED no implica proveedor comprobado disponible.
- Mostrar disponibilidad externa NO_VERIFICADA y respaldo local NO_IMPLEMENTADO.
- El modelo de métricas es flexible en OpenAPI: el adaptador valida los campos actuales sin inventar un esquema oficial exhaustivo.
- Comenzar con actualización manual; decidir polling únicamente si se justifica frecuencia y ciclo de vida. No usar polling del monitor como permiso para consultar datasets.

## 8. Componentes compartidos y diseño visual

### Lenguaje visual propuesto

Barra lateral con cuatro destinos en escritorio, encabezado con identidad EcoInvest y contexto de la vista, área principal con filtros, tarjetas y contenido. En móvil, navegación compacta, disposición en una columna y controles con etiquetas legibles.

Paleta sobria con acento verde/azul, superficies claras, jerarquía tipográfica y espacios consistentes. Es una dirección propuesta, no una reproducción de prototipos verificados. Usar tipografía del sistema inicialmente para evitar descargas externas de fuentes.

| Componente conceptual | Responsabilidad |
|---|---|
| AppShell / navegación | Layout, destino activo, título y foco de navegación |
| FilterBar | Controles etiquetados, contexto seleccionado y acción de consulta |
| MetricCard | Valor, unidad, período y estado de ausencia sin cero ficticio |
| AsyncState | Loading, vacío, error y reintento manual según presupuesto |
| ProvenancePanel | Fuente, cobertura, estado de consulta, advertencias y modo mocks |
| DataTable | Encabezados, celdas opcionales, paginación visual y adaptación móvil |
| Aviso de degradación | Mantener información utilizable sin ocultar la fuente fallida |

Accesibilidad: navegación por teclado, foco visible, labels explícitos, estados comunicados mediante texto y no solo color, contraste comprobable, regiones de estado y tablas con encabezados. El mapa tendrá una alternativa de lista.

No se generaron imágenes ni se seleccionó un paquete de iconos. Si se aportan prototipos después, inspeccionarlos y reconciliarlos con el contrato real, sin prometer campos ausentes por imitar una maqueta.

## 9. Plan de implementación por lotes

Cada lote requiere autorización de sus archivos. Instalación, scripts de validación, servidores y tráfico tienen permisos independientes según el encargo. Las pruebas se incorporan desde el lote correspondiente, no se posponen todas al final.

### Lote 1 — Inicialización, configuración y navegación

- **Archivos previstos:** package.json, lockfile de npm si se aprueba, index.html, vite.config.ts, tsconfig del scaffold acordado, main.tsx, App/AppShell/navigation/config y estilos base.
- **Dependencias:** React, React DOM, TypeScript y herramientas Vite/React necesarias; versiones exactas por verificar y aprobar.
- **Validaciones propuestas:** comprobación de versiones Node/npm, revisión de scripts, typecheck y build con comandos acordados. Un servidor de desarrollo requiere aprobación aparte.
- **Aceptación:** cuatro destinos navegables, layout responsive, configuración pública diferenciada, sin llamadas API ni credenciales, sin modificar backend.

### Lote 2 — Cliente API, tipos, adaptadores y mocks

- **Archivos previstos:** api/client.ts, acceso a tipos, adaptadores por respuesta, mocks/fixtures.ts y mockApi.ts; tests proporcionados con archivos exactos a aprobar.
- **Dependencias:** fetch/AbortController nativos; herramienta de generación o test solo si se justifica y aprueba.
- **Validaciones:** status/headers conservados, opcionales, data array/objeto/null, 200 degradado, 502/503, cancelación y descarte de respuestas obsoletas mediante respuestas artificiales.
- **Aceptación:** fixtures pequeños y modo mocks visible; ningún token o dato personal; ninguna solicitud de red obligatoria.

### Lote 3 — Dashboard Analítico

- **Archivos previstos:** features/dashboard/DashboardPage.tsx, hook/adaptador de vista si procede, estilos y pruebas de los escenarios reales del contrato.
- **Dependencias:** Lotes 1–2 y componentes compartidos que ya tengan uso concreto.
- **Validaciones:** filtros año/actividad sincronizados, conteos correctos de fixture, ausencia económica, BCRP disponible con SENACE caído y advertencias temporales.
- **Aceptación:** las tarjetas no fabrican valores; ámbito/período/procedencia visibles; no modifica reglas del backend.

### Lote 4 — Explorador Geográfico

- **Archivos previstos:** features/explorador/ExploradorPage.tsx, adaptador de puntos y componente de mapa/lista; nombres concretos tras decisión de cartografía.
- **Dependencias:** reutilización de datos/filtros y aprobación separada de biblioteca/mapa/tiles si se necesitan.
- **Validaciones:** coordenadas ausentes/invalidas, lista accesible, filtros y selección sincronizados, ausencia de consulta duplicada.
- **Aceptación:** no posiciones inventadas ni regiones inferidas; no solicitudes de tiles o datasets fuera del permiso.

### Lote 5 — Consultoras Ambientales

- **Archivos previstos:** features/consultoras/ConsultorasPage.tsx, tabla/filtros y adaptador de headers si no quedó en Lote 2.
- **Dependencias:** Lotes 1–2; no librería de tablas inicial obligatoria.
- **Validaciones:** ocho campos, vacíos, duplicados conservados, [] oficial frente a error y paginación visual de fixture voluminoso artificial.
- **Aceptación:** no renderizar todas las filas en el DOM; no estado/ID administrativo inventado; sin consultas por pulsación de búsqueda local.

### Lote 6 — Monitor de Salud SOA

- **Archivos previstos:** features/monitor/MonitorPage.tsx, adaptador de métricas y tarjetas compartidas.
- **Dependencias:** Lotes 1–2; datos flexibles validados por adaptador.
- **Validaciones:** estado de circuito, campos opcionales, métricas lógicas, error y ausencia de inferencia de disponibilidad.
- **Aceptación:** rotulado correcto de NO_VERIFICADA/NO_IMPLEMENTADO y actualización controlada.

### Lote 7 — Integración progresiva, pruebas y responsive

- **Archivos previstos:** configuración pública, adaptadores/componentes que presenten incompatibilidades demostradas y pruebas de flujos; lista exacta al iniciar el lote.
- **Dependencias:** backend con procedimiento de arranque aprobado y aislamiento SQLite/JPA resuelto; permiso de HTTP local/proveedores según alcance.
- **Validaciones:** una vista por vez, CORS/headers en navegador, red y cancelación, contrato real, teclado y tamaños de pantalla. Diferenciar pruebas con mocks de las que contacten backend o proveedores.
- **Aceptación:** conexión progresiva sin credenciales cliente, ausencia/degradación correctas y sin peticiones redundantes. No declarar end-to-end global por una sola pantalla.

La distribución de siete lotes es adecuada. Se recomienda trabajar accesibilidad, responsive y tests desde el primero y reservar el Lote 7 para consolidación, no para descubrir todas las incompatibilidades al final.

## 10. Riesgos y dependencias

| Riesgo | Mitigación / límite |
|---|---|
| Node/npm disponibles pero versiones no verificadas | Comprobar compatibilidad antes de instalar; no asumir una versión de Vite |
| Backend no importará .env local automáticamente | Preparar configuración del proceso antes de conexión real |
| SQLite/JPA con ddl-auto:update en arranque actual | No arrancar backend incidentalmente; aislamiento explícito antes del Lote 7 |
| JSON number pierde precisión de int64/BigDecimal | Evitar garantías de exactitud no acreditadas; acordar estrategia si el caso la requiere |
| HTTP200 degradado tratado como éxito completo | Adaptación semántica por metadatos, no solo status |
| [] en error tratado como cero oficial | Conservar status/headers y distinguir error/empty |
| Gran volumen de consultoras/mapa | Renderizado acotado, filtros locales, reutilización y medición antes de añadir herramientas |
| Mapas introducen dependencia/tráfico externo | Aprobación independiente de biblioteca, proveedor y presupuesto |
| Datos obsoletos por cambios rápidos | Cancelación y descarte por clave de solicitud/filtros efectivos |
| Esquemas flexibles del monitor/genérico | Tratar como unknown, adaptar, no hacer casts ciegos |
| Índice documental histórico desactualizado | Usar cierres actuales; no actualizar índice sin permiso |
| Prototipos externos no inspeccionados | No afirmar reutilización; revisión separada si se solicita |

No hay una necesidad demostrada de cambiar controlador o OpenAPI para comenzar las vistas con mocks. La preparación del arranque real no bloquea los lotes visuales iniciales.

## 11. Decisiones aprobadas y pendientes

### Aprobación de diseño recibida

El usuario aprobó el plan inicial y la creación de este reporte. La dirección acordada es React + TypeScript, Vite propuesto como herramienta de construcción, organización funcional, cuatro vistas, fetch/CSS nativos inicialmente, navegación hash inicial, contrato OpenAPI, estados explícitos y siete lotes.

**Esta aprobación no autoriza crear el scaffold ni instalar las tecnologías propuestas.** Los nombres y paquetes concretos deben confirmarse en el lote respectivo.

| Decisión pendiente | Momento para resolver | No asumir |
|---|---|---|
| Versiones exactas y compatibilidad Node | Antes de Lote 1/instalación | Que el entorno admite cualquier versión reciente |
| Lista final de archivos y scripts del scaffold | Autorización Lote 1 | Permiso global de escritura frontend por aprobar este reporte |
| Herramienta de tipos OpenAPI | Lote 2 | Que TypeScript estático valida runtime |
| Herramientas de tests/lint | Lotes 1–2 según necesidad | Instalar un conjunto amplio por rutina |
| Biblioteca de mapa/cartografía/tráfico | Antes de Lote 4 | Tiles o servicios externos gratuitos/permitidos sin revisión |
| Tipado/representación de enteros grandes si se exige exactitud | Antes de esa funcionalidad | Que convertir number a string/BigInt recupera precisión perdida |
| Revisión de prototipos externos | Si el usuario pide incorporarlos | Que ya fueron inspeccionados o implementados |
| Alcance geográfico de todos los años frente a selección dashboard | Diseño concreto de explorador | Descargar un segundo dataset automáticamente |
| Configuración y arranque backend sin SQLite | Antes de integración HTTP real | Que el test 3C.2 prepara Spring Boot productivo |

## 12. Siguiente paso recomendado

Preparar un prompt del **Lote 1 de implementación frontend**, que lea este reporte, AGENTS.md, la skill frontend y el contrato, vuelva a verificar frontend/ y presente:

1. Versiones Node/npm y compatibilidad con las versiones propuestas, mediante diagnóstico autorizado.
2. Archivos exactos del scaffold y dependencias mínimas con justificación.
3. Permisos separados para creación, instalación/red de paquetes si corresponde, validaciones y servidor de desarrollo.
4. Navegación/layout con contenido estructural sin presentar fixtures como información oficial.
5. Restricción de no tocar backend, SQLite o credenciales y no iniciar los otros lotes automáticamente.

También puede compartirse este reporte con ChatGPT para revisar decisiones antes de autorizar ese lote. No se inicia frontend ni trabajo operativo por crear este documento.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE+BCRP con backend Java 17/Oracle JDK17.0.12, Spring Boot3.2.4 y OpenAPI. BCRP2C está cerrado en su alcance técnico. SENACE3B.2 está implementada en cuatro lotes; 3C.1 pasó 32 pruebas integradas con transporte simulado; 3C.2 pasó dos ejecuciones Java reales independientes contra proyectos y consultoras. No existe certificación end-to-end global. El cierre3C.3 permite iniciar React con contratos y mocks, dejando configuración de proceso/aislamiento SQLite antes de integración real.

Fase4A verificó frontend/ vacío, sin aplicación React o package.json, y no encontró recursos visuales habituales/prototipos dentro del repositorio. Los prototipos externos históricos no se inspeccionaron. Node/npm se localizaron como comandos, pero no se ejecutaron ni se verificaron versiones. Las seis skills EcoInvest están disponibles. No se instalaron paquetes, crearon componentes, ejecutaron tests/servidores o HTTP ni se cambió backend.

El usuario aprobó el diseño y autorizó únicamente este documento: documentacion/04_Fase4_Frontend/REPORTE_DISENO_FASE4A_FRONTEND.md. Arquitectura propuesta: React+TypeScript con Vite, organización app/api/components/features/mocks/styles, cuatro vistas y navegación hash inicial, CSS/fetch/AbortController nativos, sin gestor global o librerías de UI/tablas/gráficos por rutina. Versiones, herramientas de tipos/tests y mapa quedan sujetas a evaluación/aprobación concretas.

Las cuatro vistas son dashboard, explorador, consultoras y monitor. Dashboard usa /api/dashboard con sector obligatorio y anio default2017, año de FECHA_INICIO, no aprobación. Manejar 200 degradado y BCRP independiente, campos ausentes sin cero ficticio y procedencia/cobertura. Explorador reutiliza proyectos del dashboard para evitar GET duplicados; no hay endpoint mapa. Una consulta genérica sin filtro anual requiere decisión de alcance; no inventar región/CRS/coordenadas. Consultoras consume /api/consultoras como array de ocho campos opcionales con status/headers 200/502/503, conserva filas y fechas sin ID/RUC/nivel/estado/vigencia inventados; usar renderizado acotado. Monitor consume /api/estado-servicios como métricas lógicas de circuitos, no disponibilidad externa comprobada o respaldo SQLite.

OpenAPI sigue como fuente única. La mayoría de campos son opcionales; data genérico es unknown hasta discriminar y usa nombres DTO oficiales frente a camelCase público. JsonNullable es interno Java, no envoltorio de navegador. Int64/BigDecimal JSON pueden perder precisión en number/JSON.parse: no convertir RUC a número ni prometer exactitud arbitraria. Generación de tipos es viable pero herramienta por aprobar. URL backend pública propuesta VITE_API_BASE_URL; nunca credenciales SENACE en VITE_*, bundle o almacenamiento cliente.

Plan: Lote1 scaffold/config/navegación; Lote2 cliente/tipos/adaptadores/mocks; Lote3 dashboard; Lote4 explorador con mapa/cartografía aprobados; Lote5 consultoras; Lote6 monitor; Lote7 integración progresiva y consolidación de pruebas/responsive. Incorporar accesibilidad/tests desde cada lote. Estados loading/ready/empty/degraded/error y modo mocks visible, sin fallback silencioso a datos artificiales.

Siguiente prompt recomendado: diseñar y autorizar Lote1 con lista exacta de archivos, versiones y dependencias mínimas tras diagnóstico Node/npm, separando creación/instalación/validaciones/servidor. No usar esta aprobación documental como permiso para instalar o escribir frontend. No ejecutar backend con SQLite/JPA ddl-auto:update por accidente ni repetir consultas BCRP/SENACE para preparar pantallas. No comenzar otros lotes automáticamente.
