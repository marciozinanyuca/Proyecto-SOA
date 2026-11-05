# EcoInvest — Revisión de prototipos Stitch, Fase 4A

Fecha: **04/11/2026**, America/Lima. Estado: **referencias originales inspeccionadas; conciliación y plan presentados; implementación pendiente**.

Documento creado con autorización posterior a la presentación del análisis. No se crearon componentes, descargaron archivos al repositorio, instalaron dependencias o modificaron diseños Stitch. No se ejecutaron Maven, servidores, SQLite ni consultas SENACE/BCRP. Los recursos Stitch se consultaron mediante MCP y se analizaron en memoria.

## 1. Estado del MCP Stitch y alcance

La conexión funciona: se recuperaron metadatos del proyecto, lista de pantallas, HTML de las cuatro vistas y sus capturas. La evidencia procede de herramientas MCP reales, no de nombres o identificadores supuestos.

Herramientas utilizadas para esta inspección:

```text
mcp__stitch__get_project
mcp__stitch__list_screens
mcp__stitch__download_file
```

No se llamaron herramientas de edición, generación, subida, eliminación o actualización. No se reutilizó una clave escrita en la conversación: la conexión MCP ya configurada gestionó su autenticación. No se publican credenciales o URLs de descarga autenticadas.

### Jerarquía aplicada

1. [OpenAPI vigente](../../backend/src/main/resources/openapi.yaml) y backend real para capacidades y respuestas.
2. [Decisiones SENACE D01–D19](../03_Fase3_SENACE/REPORTE_DECISIONES_FASE3B1_SENACE.md) para reglas aprobadas.
3. Pantallas Stitch para composición, recursos e identidad visual.
4. Capturas adicionales del usuario, si se aportan; no se requirieron para sustituir las referencias originales recuperadas.

Este reporte complementa el [diseño inicial 4A](REPORTE_DISENO_FASE4A_FRONTEND.md), sin sobrescribirlo. La afirmación histórica de que no había recursos visuales en el repositorio sigue siendo cierta para esa inspección; ahora se dispone de referencias remotas verificadas en Stitch.

## 2. Skills identificadas y aplicación

La comprobación local mostró:

- `.agent/`: **no existe**.
- `.agent/skills/`: **no existe**.
- `.agents/skills/`: existe y contiene skills EcoInvest y de flujos Stitch.
- `frontend/`: sigue vacío en esta revisión.

No se asumió equivalencia entre rutas. Las skills se localizaron en la ruta que efectivamente existe.

| Grupo | Skills encontradas |
|---|---|
| EcoInvest | ecoinvest-frontend, ecoinvest-soa-architecture, ecoinvest-security, ecoinvest-api-integration, ecoinvest-testing, ecoinvest-documentation |
| Análisis / diseño | design-md, enhance-prompt, site-md, taste-design |
| Componentes / UI | stitch-react-components, stitch-react-native, react-vite-dashboard, shadcn-ui |
| Extracción / transferencia | stitch-code-to-design, stitch-extract-design-md, stitch-extract-static-html, stitch-upload-to-stitch |
| Generación / sistemas | stitch-generate-design, stitch-manage-design-system, stitch-loop |

Se aplicaron criterios EcoInvest de frontend, arquitectura, seguridad y documentación. Se leyeron las instrucciones reales de design-md y stitch-react-components para evaluar recuperación/adaptación, sin ejecutar sus fases de creación.

**Procedencia:** nombres y archivos locales no acreditan por sí solos que cada paquete provenga oficialmente de Google/OpenAI. No se auditó su origen o versión ni se atribuye una certificación oficial inexistente.

### Diferencias entre instrucciones de skills y herramientas actuales

design-md describe una secuencia de recuperación y síntesis de DESIGN.md, pero incluye firmas y métodos de descarga que no coinciden exactamente con el MCP disponible. stitch-react-components exige scripts de descarga, archivos `.stitch/metadata.json`, auditoría de capturas y posteriores pasos de conversión/validación.

En esta tarea prevaleció el alcance explícito: **inspección sin escritura ni ejecución de conversión**. Se utilizó download_file con los nombres de recurso realmente devueltos por MCP, conforme a la instrucción actual de esa herramienta, sin reconstruir URLs o ejecutar scripts. No se afirma haber completado las fases obligatorias de conversión de stitch-react-components; una implementación futura deberá reconciliar sus requisitos con permisos y herramientas vigentes.

No se generaron DESIGN.md, SITE.md, metadatos `.stitch/`, recursos locales o componentes por activar una skill.

## 3. Proyecto y cuatro pantallas localizadas

Proyecto exacto: **Proyecto SOA - EcoInvest**.

```text
projects/3389709097732066354
```

Se utilizó este proyecto, no el otro proyecto del usuario titulado EcoInvest Perú Dashboard.

| Vista | Título devuelto por Stitch | ID de pantalla | Dimensiones de pantalla declaradas |
|---|---|---|---|
| Dashboard | EcoInvest Perú - Dashboard Analítico (Claro / Oscuro) | a708110083bb4fa38ee8aa0935dcb1a9 | 2560 × 3388, DESKTOP |
| Explorador | EcoInvest Perú - Explorador Geográfico | 156dce54ec4346fcae22ed3061eb711b | 2560 × 2460, DESKTOP |
| Consultoras | EcoInvest Perú - Consultoras Ambientales | 00e4138a0a8e456c84a79ad7ff680db2 | 2560 × 2290, DESKTOP |
| Monitor | EcoInvest Perú - Monitor de Salud SOA | fd012dd20dc14515bf7f90eb05b45000 | 2560 × 2830, DESKTOP |

El proyecto contiene además instancias ocultas/variantes anteriores y assets de sistemas de diseño. No se confundió una variante oculta o la etiqueta temporal “Generating Screen...” con una pantalla principal nueva. Los títulos definitivos se obtuvieron de list_screens.

Las dimensiones declaradas de pantalla, el tamaño de una instancia en canvas y las dimensiones de una captura descargada son conceptos diferentes; no se usaron como si fueran una misma resolución.

## 4. Recursos visuales recuperados o recuperables

### HTML y capturas efectivamente recuperados

| Vista | Recurso HTML devuelto por MCP | Longitud de HTML en memoria, caracteres JS |
|---|---|---:|
| Dashboard | projects/3389709097732066354/files/3560949116595053470 | 44.949 |
| Explorador | projects/3389709097732066354/files/18308596521326607367 | 38.488 |
| Consultoras | projects/3389709097732066354/screens/00e4138a0a8e456c84a79ad7ff680db2/fileEntries/html | 34.460 |
| Monitor | projects/3389709097732066354/screens/fd012dd20dc14515bf7f90eb05b45000/fileEntries/html | 35.311 |

Las longitudes no son tamaños HTTP ni bytes de descarga. Las cuatro capturas se recuperaron mediante download_file y se inspeccionaron visualmente. Para Dashboard y Explorador la herramienta informó JPEG de 1067 × 1410 y 1067 × 1026 respectivamente; para las otras devolvió PNG sin dimensiones en imageMetadata. No se afirma auditoría de cada píxel a la resolución declarada 2560.

### Logos originales localizados

| Recurso | Pantalla de asset | Archivo de imagen |
|---|---|---|
| EcoInvest_Logo.png | screens/15938707631270691950 | files/15938707631270694669 |
| senace_logo.webp | screens/16043885115136335728 | files/16043885115136335173 |
| BCRP_logo.png | screens/16043885115136334618 | files/16043885115136334063 |

Todos pertenecen al prefijo projects/3389709097732066354/. Se localizaron sus metadatos y su uso en el HTML; **no se exportaron a frontend/**. Las capturas completas permitieron revisar su apariencia dentro del diseño. La implementación deberá recuperar los archivos originales aprobados, no crear logos nuevos o depender sin evaluación de enlaces remotos autenticados.

### Tecnología del export

El material recuperado es HTML/CSS/SVG y scripts de prototipo, **no una aplicación React exportada**. Incluye Tailwind por CDN, Google Fonts, Material Symbols, bloques de configuración y valores fijos. Hay contenido/metadatos repetidos en algunos documentos y marcadores/enlaces sin lógica productiva.

Reutilizar estructura, SVG y estilos no significa copiar el documento completo como JSX o incrustarlo con dangerouslySetInnerHTML. La adaptación deberá crear componentes semánticos, retirar scripts externos de prototipo y sustituir datos fijos por modelos/adaptadores del contrato.

## 5. Análisis de fidelidad visual

### Elementos comunes observados directamente

- Logo EcoInvest y sidebar clara con cuatro destinos e indicador verde del destino seleccionado.
- Encabezado con título, marcas BCRP/SENACE, control “Modo Claro” y bloque de usuario demostrativo.
- Fondo muy claro, tarjetas blancas, bordes suaves, sombras discretas y acentos verde/turquesa/azul.
- Filtros en panel superior, tarjetas KPI con icono en contenedor coloreado, valores destacados y etiquetas pequeñas.
- Tablas con encabezados compactos, badges, búsqueda, botones CSV/JSON y paginación visual.
- Pie/panel lateral de “Telemetría SOA” que contiene cifras/estados fijos no respaldados.

### Tokens observados en HTML

| Elemento | Referencia concreta | Uso propuesto |
|---|---|---|
| Fondo claro | #f8fafc | Canvas inicial |
| Blanco | #ffffff | Tarjetas y navegación |
| Verde | #10b981 / #059669 | Acciones y selección, sin inferir certificación de datos por color |
| Turquesa | #06b6d4 / #0891b2 | Acentos y etiquetas |
| Texto oscuro / variante oscura | #0f172a / #0b1120 / #131d33 | Texto/capas según contexto |
| Negativo | #e11d48 | Valores o estados negativos con etiqueta textual |
| Cuerpo | Manrope | Referencia visual del HTML concreto |
| Encabezados | Space Grotesk | Jerarquía y títulos |
| Datos/etiquetas monoespaciadas | JetBrains Mono | Identificadores, metadatos y cifras cuando corresponda |
| Iconos | Material Symbols Outlined | Apariencia observada; forma de distribución por aprobar |

El Explorador incluye además un bloque de colores semánticos propio. Antes de implementar se debe consolidar cuáles se utilizan realmente y evitar que cada vista mantenga un tema contradictorio.

### Discrepancia con tema global

get_project declara tema global **DARK**, Inter y un sistema de vidrio oscuro. Sin embargo, las cuatro capturas inspeccionadas son claras, sus HTML usan las familias anteriores y tienen variantes dark mediante clases.

Recomendación: priorizar la **pantalla concreta y sus capturas** para reproducir el diseño elegido; no aplicar automáticamente los tokens globales oscuros a todas las vistas. Modo claro inicial y alcance del control claro/oscuro deben quedar fijados antes del lote visual. No prometer una variante oscura probada por existir clases dark.

### Correcciones de calidad necesarias

Las capturas/HTML presentan algunos textos con mojibake, desbordamientos de etiquetas o elementos pequeños y documentos duplicados. Corregir codificación, wrapping y estructura al convertir, preservando composición y personalidad visual. La fidelidad no exige reproducir un fallo de accesibilidad o texto corrupto.

Solo se verificaron diseños desktop. La adaptación móvil/tablet es una propuesta de ingeniería: sidebar compacta, tarjetas apiladas, filtros flexibles, tablas con desplazamiento o detalle y mapa/lista accesibles. No se afirma que esas variantes existan en Stitch.

## 6. Matriz de conciliación por pantalla

Estados utilizados: **COMPATIBLE**, **ADAPTAR**, **NO SOPORTADO**, **REQUIERE DECISIÓN**. Un elemento no soportado puede conservar su espacio visual con otro contenido válido; no se elimina el panel entero por defecto.

### 6.1. Dashboard Analítico

| Elemento visual observado | Significado del prototipo | Campo/endpoint real | Estado | Ajuste recomendado | Impacto en fidelidad |
|---|---|---|---|---|---|
| Tarjeta “Proyectos en cartera” | 48 expedientes y tendencia trimestral fija | /api/dashboard: total | ADAPTAR | “Registros filtrados”; valor de respuesta; sustituir tendencia por calidad/conteos disponibles | Conserva tarjeta, icono y jerarquía |
| Tarjeta aprobados | 40 y 83,3% de viabilidad | aprobados y total | ADAPTAR | Registros aprobados; no llamarlo viabilidad. Un porcentaje descriptivo requeriría etiqueta/denominador claros y aprobación; no es necesario inicial | Conserva composición |
| Crecimiento sectorial | -2,45 fijo y descripción genérica | indicadorBcrp y variacionSeleccionada | COMPATIBLE | Mostrar valor disponible, unidad, año/período y alcance real; ausencia explícita | Alta |
| Gráfico central combinado | Barras mensuales SENACE, línea mensual PBI, correlación r=0,78 | proyectos filtrados y estado; BCRP anual | NO SOPORTADO | Reutilizar panel para distribución de registros por ESTADO, calculada sobre lista conocida; indicador anual separado | Mantiene gran panel; cambia semántica y gráfico |
| Narrativa de causalidad/correlación | Aprobaciones preceden crecimiento | Ningún cálculo o evidencia disponible | NO SOPORTADO | Aviso de alcance, cobertura y calidad temporal | Conserva pie informativo |
| Sector/año/datastream | Sectores y recursos ilustrativos; año fiscal | sector/anio de dashboard | ADAPTAR | Año de inicio; sectores compatibles sin equivalencias inventadas; selector de recurso no debe cambiar a endpoints ficticios | Alta; opciones revisadas |
| Registro “RUC verificados/SUNAT” | Padrón auditado con expedientes | ProyectoSenacePublico, RUC del titular | ADAPTAR | “Registros de proyectos SENACE”; sin certificación SUNAT o expediente inventado | Tabla y búsqueda conservadas |
| Hora de sincronización fija | Estado oficial del proveedor | No hay timestamp oficial expuesto | ADAPTAR | “Última consulta local”, solo tras respuesta, identificando su origen cliente | Conserva ubicación |
| CSV/JSON | Exportación ilustrativa | Datos ya recibidos | REQUIERE DECISIÓN | Exportación local puede diseñarse después con alcance, filtros y tratamiento de datos, sin endpoint nuevo | Botones conservables, sin falsa funcionalidad |

Una distribución por estado es un agregado de filas de la selección, no serie histórica, evolución temporal o cálculo causal. Si la consulta falla, no construir barras de ceros. No incluir inversión, moneda o valores de SUNAT.

### 6.2. Explorador Geográfico

| Elemento visual observado | Significado del prototipo | Campo/endpoint real | Estado | Ajuste recomendado | Impacto en fidelidad |
|---|---|---|---|---|---|---|
| Panel de mapa con marcadores | Proyectos en silueta estilizada de Perú | proyectos.longitud/latitud | REQUIERE DECISIÓN | Biblioteca, cartografía y proyección de visualización por aprobar; reutilizar selección cargada | Mantiene área/mapa/lista |
| Filtros y botón aprobados | Sector, año y estado | sector/anio; estado de filas | ADAPTAR | Año de inicio y filtrado local explícito de aprobados sobre lista recibida | Alta |
| Ficha lateral | Titular, RUC, proyecto, actividad/tipo, fecha, coordenadas, estado y resolución | Campos ProyectoSenacePublico | COMPATIBLE | Mantener panel, valores opcionales y fechas originales | Alta |
| “ID / expediente” | Lote/código utilizado como identidad | id; resolucion por separado | ADAPTAR | Separar “ID del registro” y “Resolución”; no inventar expediente | Cambian etiquetas |
| “WGS84 / UTM18S/19S” y “SENACE GEOSERVICE v4.2” | CRS y servicio certificados | No expuestos/certificados | NO SOPORTADO | “Coordenadas proporcionadas; CRS no certificado” y cobertura | Mantiene banda descriptiva |
| Región/departamento/elevación | Localización administrativa y altura | No disponibles | NO SOPORTADO | Sustituir por actividad/estado/coordenadas originales, sin geocodificar implícitamente | Ajuste localizado |
| “GPS válido” | Certificación de precisión | Validación por rango del mapper | ADAPTAR | “Coordenadas utilizables por rango”, sin certificar ubicación | Conserva badge |
| Gráfico económico regional trimestral | Impacto local BCRP | Indicador anual sectorial/nacional, sin región | NO SOPORTADO | Tarjeta de calidad/procedencia del registro; no atribuir impacto al proyecto | Conserva tarjeta lateral |
| Capas y enlace expediente completo | Servicios/documentos externos | No existe URL de expediente verificada | REQUIERE DECISIÓN | No habilitar capas/enlace inexistentes; proponer detalle local | Mantiene controles solo si tienen función honesta |

No presentar la silueta decorativa como capa oficial ni convertir rangos globales en prueba de CRS. Si se elige una proyección de mapa, distinguir esa decisión técnica del CRS certificado del dato, que sigue pendiente.

### 6.3. Consultoras Ambientales

| Elemento visual observado | Significado del prototipo | Campo/endpoint real | Estado | Ajuste recomendado | Impacto en fidelidad |
|---|---|---|---|---|---|---|
| Búsqueda por razón social/RUC | Identidad empresarial | razonSocial; RUC inexistente | ADAPTAR | Búsqueda sobre campos disponibles, sin anunciar RUC | Alta |
| Filtros nivel/estado | EIA-d/EIA-sd, vigente/suspendida | actividad, subsector, tipoSolicitud | ADAPTAR | Reutilizar selects con esos conceptos reales | Alta |
| KPI consultoras registradas | 354 empresas únicas | Cantidad de filas recibidas | ADAPTAR | “Filas recibidas”; errores no equivalen a cero | Conserva tarjeta |
| KPI vigentes/habilitadas | 312 y 88,1% habilitación | Sin estado administrativo | NO SOPORTADO | “Filas en selección” según filtros visuales | Conserva tarjeta |
| KPI autorizadas hidrocarburos | Habilitación sectorial | ACTIVIDAD/SUBSECTOR como texto | ADAPTAR | Resumen explícito de filas por filtro o campo disponible; no “autorizadas” sin regla | Conserva tarjeta |
| Tabla RUC/registro/nivel/estado | Identidad/habilitación ficticia | Ocho campos ConsultoraSenacePublica | NO SOPORTADO | Redistribuir razón social, actividad, subsector, tipo, resolución y fechas; detalle para columnas extensas | Conserva tabla y acción de detalle |
| “RNCA / SUNAT” y autorización | Validación administrativa adicional | Sin integración adicional | NO SOPORTADO | Recurso ConsultorasAmbientales de SENACE, cobertura NO_VERIFICADA | Ajuste de texto |
| Paginación | Ventana de filas | Array raíz completo | COMPATIBLE | Paginación de presentación; no afirmar paginación servidor o ahorro de descarga | Alta |
| CSV/JSON | Exportación | Filas recibidas | REQUIERE DECISIÓN | Alcance local y manejo seguro de exportación a aprobar | Alta si se aprueba |

Conservar las ocho correspondencias: razonSocial, actividad, subsector, tipoSolicitud, resolucion, fechaRd, inicioVigenciaRd y finVigenciaRd. No derivar activo/vencido ni deduplicar por nombre. Conteos describen filas, no empresas únicas. Los duplicados hipotéticos deben conservarse aunque la muestra real anterior no tuviera repeticiones exactas.

### 6.4. Monitor de Salud SOA

| Elemento visual observado | Significado del prototipo | Campo/endpoint real | Estado | Ajuste recomendado | Impacto en fidelidad |
|---|---|---|---|---|---|---|
| Tarjetas SENACE/BCRP | Servicios y estado del circuito | /api/estado-servicios: senace/bcrp | COMPATIBLE | Mantener estructura de dos tarjetas y estado real | Alta |
| Fallos/tasa | Telemetría | llamadasFallidas/tasaFallosPorcentaje | COMPATIBLE | Valores presentes o ausencia, sin constantes | Alta |
| Bloqueadas/latencia | Contadores y 830ms | No expuestos | ADAPTAR | llamadasExitosas y alcanceMetricas; no medir latencia cliente como proveedor | Conserva métricas |
| “Simular fallo API BCRP” | Acción destructiva de demostración | No existe endpoint | NO SOPORTADO | No habilitar acción ficticia; sustituir por ayuda sobre circuitos o retirar solo esa acción | Cambio localizado |
| Fallback SQLite/caché | Recuperación operativa | respaldoLocal=NO_IMPLEMENTADO | NO SOPORTADO | Mostrar respaldo no implementado con diseño de aviso | Alta |
| Historial de requests | Eventos persistentes y endpoints ilustrativos | No hay historial disponible | NO SOPORTADO | Reutilizar panel para métricas actuales y explicación de alcance | Conserva panel, cambia contenido |
| Consola/copia JSON/limpieza | Buffer de trazas | No existe buffer de eventos | ADAPTAR | Si se aprueba copia, solo snapshot actual seguro; no llamarlo historial | Conserva zona visual |
| CLOSED “operativo” / API directa | Disponibilidad externa | disponibilidadExterna=NO_VERIFICADA | ADAPTAR | Rotular estado del circuito sin inferir salud real | Alta |
| Actualizar | Consulta de métricas | GET /api/estado-servicios | COMPATIBLE | Refresco manual con hora local identificada | Alta |

Los endpoints ilustrativos dentro de historiales Stitch no constituyen APIs EcoInvest existentes. No crearlos para reproducir una tabla.

### 6.5. Elementos transversales

El panel lateral “API Gateway:24ms / SENACE REST:OK / BCRP SOAP:OK” tiene valores fijos y una denominación BCRP SOAP no respaldada por la integración HTTP JSON existente. Conservar el bloque visual, sustituyendo su contenido por procedencia/alcance disponible o enlace al monitor. No presentar latencia o disponibilidad inventadas.

El bloque “Ing. Supervisor SOA” es contenido demostrativo, no una sesión autenticada. Antes de implementarlo, definir etiqueta neutral o contexto local sin inventar autenticación/roles.

El control claro/oscuro, exportaciones, búsqueda, botones de detalle y acciones deben tener comportamiento real aprobado o comunicar explícitamente su estado no implementado. La fidelidad visual no justifica controles engañosos.

## 7. Componentes reutilizables propuestos

| Componente | Referencia recuperada | Adaptación |
|---|---|---|
| AppShell / Sidebar | Estructura común de cuatro pantallas | Componente único, navegación funcional y foco accesible |
| Header / SourceBadges | Título y logos BCRP/SENACE | Recursos originales locales aprobados; no badge de salud ficticia |
| FilterPanel | Panel superior y selects | Labels de año de inicio/campos reales y estados sincronizados |
| MetricCard | Tres tarjetas de dashboard/consultoras | Valor opcional, unidad y contexto; no tendencia inventada |
| ProvenancePanel | Bandas informativas y pie | Metadatos reales, cobertura y hora de consulta local |
| RecordsTable / DetailPanel | Tablas y ficha geográfica | Campos del contrato, paginación visual y semántica accesible |
| ServiceCircuitCard | Tarjetas del monitor | Estado de circuito y métricas existentes |
| StatusBadge / AsyncState | Colores y chips observados | Estados textuales loading/ready/empty/degraded/error |
| ThemeControl | Botón Modo Claro | Alcance de tema por aprobar; no prometer modo oscuro completo antes de probar |

Primero extraer tokens y piezas realmente comunes; evitar copiar cuatro shells divergentes. No introducir una capa de componentes abstracta sin uso concreto.

## 8. Limitaciones del contrato y reglas de integración

- Dashboard requiere sector y acepta anio opcional default2017. El frontend siempre enviará el contexto seleccionado; no renombrar año de inicio a fiscal/aprobación.
- HTTP200 puede ser degradado: inspeccionar consultaSenace y BCRP por separado. Ausente/null no es cero.
- Consultoras conserva raíz array, incluyendo [] con 502/503. Preservar status y headers antes de clasificar vacío/error.
- Headers de consultoras expuestos: X-Senace-Origen, X-Senace-Estado, X-Senace-Cobertura y opcionales X-Senace-Motivo/X-Senace-Cuerpo-Completo.
- Genérico mantiene data flexible array/objeto/null. No imponer el esquema de proyectos a seis recursos no validados.
- Muchos campos del contrato son opcionales. Los adaptadores deben comprobar formas sin asumir que tipos generados validan runtime.
- BigDecimal/int64 Java no garantiza precisión arbitraria en JSON.parse/number. RUC siempre string. Cualquier exigencia de precisión exacta requiere estrategia explícita, no conversión posterior que aparente recuperar dígitos perdidos.
- AbortController y descarte de respuestas obsoletas evitan sincronización incorrecta de filtros; cancelar navegador no garantiza detener un GET ya enviado al proveedor desde backend.
- Reutilizar datos compartidos entre dashboard/explorador; no duplicar consultas al cambiar de vista si la selección sigue siendo válida.
- Las credenciales SENACE pertenecen al backend, nunca al bundle, VITE_*, almacenamiento o URL del navegador.

El [cierre backend 3C.3](../03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md) conserva pendientes de configuración del proceso y aislamiento SQLite/JPA antes de arrancar Spring Boot. Esos pendientes no bloquean trabajo visual con mocks ni quedan autorizados por este reporte.

## 9. Tecnologías recomendadas y evaluación del export

Se mantiene React + TypeScript y Vite como dirección propuesta, con CSS y fetch nativos inicialmente. No se ejecutaron node --version o npm --version en esta revisión; los diagnósticos deberán autorizarse antes de fijar versiones/instalar. No se consultó registro de paquetes ni se verificó una combinación de versiones remota.

| Área | Recomendación | Decisión pendiente |
|---|---|---|
| Estilos | Extraer tokens/patrones del HTML y adaptarlos a CSS mantenible | Tailwind no obligatorio por existir CDN en prototipo; comparar coste antes de incorporarlo |
| Tipografía | Mantener las familias observadas si se aprueba su distribución | Fuentes locales/externas, licencia y tráfico; no cargar Google Fonts automáticamente |
| Iconos | Conservar lenguaje Material Symbols | Distribución local/subconjunto o SVG autorizado, sin dependencia decorativa innecesaria |
| Gráficos | Barras de agregados de registros con CSS/SVG accesibles inicialmente | Biblioteca solo si interactividad/escala la justifica |
| Cartografía | Componente aislado, mapa/lista sincronizados | Biblioteca, tiles, atribución, red y rendimiento a aprobar |
| HTTP | fetch y AbortController | URL backend pública y modo mocks explícito |
| Tipos | Derivación de OpenAPI cuando se apruebe herramienta | Opcionales, objetos flexibles y precisión requieren adaptadores |

No usar CDN Tailwind, scripts embebidos o URLs autenticadas como dependencia productiva implícita. No añadir shadcn-ui, TanStack Query, componentes Web3 u otra tecnología porque una skill disponible los mencione.

## 10. Plan frontend actualizado

Se conserva el orden de siete lotes, incorporando recuperación visual/tokens al primero y validaciones desde cada lote.

| Lote | Objetivo ajustado | Criterio mínimo de aceptación |
|---|---|---|
| 1 | Scaffold aprobado React/Vite/TS, tokens visuales, recursos originales aprobados, AppShell/navegación | Shell fiel a las capturas, cuatro destinos, sin datos reales inventados o HTTP backend |
| 2 | Cliente/tipos/adaptadores/mocks explícitos | Status/headers/opcionales/degradación/cancelación correctos con fixtures artificiales |
| 3 | Dashboard | Tarjetas y tabla fieles; gráfico central honesto de registros, sin correlación/serie mensual ficticia |
| 4 | Explorador | Mapa/lista/ficha con datos disponibles y coordenadas; tecnología/tráfico aprobados |
| 5 | Consultoras | Tabla de ocho campos, filtros reales, filas conservadas y renderizado acotado |
| 6 | Monitor | Tarjetas de circuitos y métricas reales, sin consola o simulador ficticios |
| 7 | Integración progresiva y consolidación | Backend preparado para arranque seguro, permisos HTTP y pruebas diferenciadas de mocks |

La recuperación del HTML acelera extracción de estructura y tokens, **no elimina la necesidad de adaptar comportamiento**. No conviene convertir simultáneamente las cuatro pantallas ni importar toda la aplicación ficticia del prototipo.

Estrategia de validación futura: capturas locales comparables con la referencia autorizada, revisión de texto/espaciado, teclado/responsive y tests de adaptadores/estados. Navegador/servidor, herramientas y almacenamiento de capturas requieren el alcance autorizado correspondiente. No se ejecutaron estas validaciones por escribirlas.

## 11. Archivos previstos para el primer lote

Inventario propuesto, no creado ni aprobado para escritura:

```text
frontend/package.json
frontend/package-lock.json                  # generado si npm/instalación se aprueban
frontend/index.html
frontend/vite.config.ts
frontend/tsconfig*.json                     # nombres definitivos al fijar scaffold
frontend/src/main.tsx
frontend/src/vite-env.d.ts
frontend/src/app/App.tsx
frontend/src/app/AppShell.tsx
frontend/src/app/navigation.ts
frontend/src/app/config.ts
frontend/src/styles/tokens.css
frontend/src/styles/global.css
frontend/src/assets/ecoinvest-logo.png       # recurso Stitch original, exportación por aprobar
frontend/src/assets/senace-logo.webp
frontend/src/assets/bcrp-logo.png
```

Los componentes de fuente/tema/iconos pueden requerir archivos adicionales; deben incluirse en la lista exacta antes de editar. Las pantallas funcionales y cliente API pertenecen a lotes posteriores. No crear rutas vacías que aparenten funcionalidad terminada: los destinos iniciales deben indicar su alcance estructural.

## 12. Decisiones y autorizaciones necesarias

| Tema | Recomendación presentada | Aprobación necesaria |
|---|---|---|
| Referencia visual | Capturas/HTML concretos claros sobre tema global oscuro | Confirmar modo claro inicial y alcance oscuro antes de construirlo |
| Contenido no soportado | Reutilizar espacios con información válida, según matrices | Fijar gráficos/KPIs/acciones de cada lote; no inventar reglas implícitas |
| Logos originales | Exportar recursos encontrados sin recrearlos | Permiso de creación de archivos assets |
| Fuentes/iconos | Mantener identidad observada con distribución segura | Selección/licencias/descarga o paquetes y tráfico autorizados |
| Node/npm y dependencias | Verificar entorno y versiones antes de instalar | Diagnóstico, instalación/red y lockfile separados de creación documental |
| Mapa | Conservar disposición con proveedor/biblioteca acordados | Dependencia, tiles, atribución y presupuesto de tráfico propios |
| CSV/JSON | Exportación local posible, sin endpoint ficticio | Alcance, columnas, selección y seguridad antes de habilitar botones |
| Claro/oscuro | No copiar un toggle sin definir su comportamiento | Alcance, persistencia local si se desea y pruebas visuales |
| Tráfico API real | Después de configuración e aislamiento backend | Permisos HTTP específicos; no repetir certificaciones por rutina |
| Diseños Stitch | Solo lectura en esta fase | Cualquier edición/publicación/generación requiere permiso independiente |
| Documentación | Este reporte nuevo únicamente | Índice, DESIGN.md, metadatos o reportes adicionales no autorizados por asociación |

La autorización recibida tras presentar la revisión permite crear **este reporte**. No equivale a aprobación de todos los detalles opcionales de producto ni habilita instalaciones, exportaciones locales o implementación de los lotes.

## 13. Próximo paso recomendado

Compartir el reporte con ChatGPT para preparar el prompt del Lote 1 ajustado a Stitch. Ese prompt debería:

1. Leer este reporte, el diseño 4A, AGENTS.md y el contrato.
2. Comprobar de nuevo frontend/ para preservar trabajo nuevo.
3. Autorizar diagnóstico Node/npm y presentar versiones/archivos/dependencias exactos antes de instalar.
4. Fijar modo claro inicial y distribución de logos/fuentes/iconos.
5. Autorizar únicamente scaffold/shell/tokens/navegación del primer lote, sin cuatro pantallas completas, datos de proveedor o acciones ficticias.
6. Separar creación, instalación, tests/build, servidor y tráfico. Mantener backend/SQLite/Stitch sin cambios.

No se requiere editar el proyecto remoto para adaptar el código React al contrato real. Si se desea actualizar los propios prototipos para reflejar las correcciones funcionales, eso debe ser una tarea separada expresamente autorizada.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest tiene backend Java17/Oracle17.0.12, SpringBoot3.2.4, OpenAPI y SENACE+BCRP. BCRP2C está cerrado; SENACE3B.2 implementó cuatro lotes; 3C.1 validó 32 casos integrados con transporte simulado y 3C.2 dos consultas Java reales separadas. No existe end-to-end global. frontend/ sigue vacío. El diseño4A anterior recomendó React+TypeScript/Vite, CSS/fetch y siete lotes, sin instalación.

Ahora se verificó MCP Stitch real y el proyecto exacto Proyecto SOA - EcoInvest, projects/3389709097732066354. Se recuperaron metadatos, HTML y capturas de cuatro pantallas: Dashboard a708110083bb4fa38ee8aa0935dcb1a9; Explorador 156dce54ec4346fcae22ed3061eb711b; Consultoras 00e4138a0a8e456c84a79ad7ff680db2; Monitor fd012dd20dc14515bf7f90eb05b45000. Logos originales EcoInvest/SENACE/BCRP localizados, sin exportarlos al repo. No se modificó Stitch ni se crearon componentes o assets locales.

.agent/ y .agent/skills/ no existen; las skills están en .agents/skills/. Se encontraron design-md, stitch-react-components y otras de generación/transferencia/diseño además de EcoInvest. Se revisaron instrucciones reales, sin ejecutar sus pasos de conversión/escritura. No se certificó origen oficial de esos paquetes por sus nombres. Herramientas MCP actuales y restricciones del usuario prevalecen sobre ejemplos desactualizados de firmas/descarga.

Las pantallas son claras con sidebar, logos y header de fuentes, filtros, tres KPIs y tablas/paneles. HTML usa Manrope, Space Grotesk, JetBrains Mono y Material Symbols; colores #f8fafc, #ffffff, verdes #10b981/#059669 y turquesas #06b6d4/#0891b2. Tema global Stitch declara DARK/Inter: priorizar pantallas concretas y confirmar modo claro/oscuro. HTML es prototipo con Tailwind CDN, scripts/metadatos duplicados y algunos textos corruptos/desbordamientos: reutilizar estructura/SVG/tokens, no copiarlo como React listo o reproducir errores.

Conciliación: dashboard mantiene tarjetas con total/aprobados de registros e indicadorBcrp real; sustituir correlación/gráfico combinado mensual/inversión por distribución de registros por estado y metadatos. No SUNAT/verificación RUC, tendencias ficticias o viabilidad no definida. Explorador conserva mapa/ficha y campos reales de proyectos, sin expediente, región, elevación, CRS certificado, geoservicio o impacto económico regional; mapa/tiles por aprobar. Consultoras conserva tabla/filtros/KPIs reformulados a filas y ocho campos oficiales, sin RUC/registro/nivel/estado/vigencia inventados ni empresas únicas. Monitor conserva tarjetas/circuitos/métricas reales, sustituye latencia/bloqueadas por campos disponibles, historial por snapshot/alcance y SQLite por NO_IMPLEMENTADO; no simulador de fallos. Telemetría lateral fija y BCRP SOAP OK son contenido ficticio a corregir, no endpoints existentes.

Contrato sigue mandando: dashboard sector obligatorio y anio default2017 como año de inicio; 200 degradado; consultoras array con headers y 502/503; genérico data flexible y nombres DTO oficiales; monitor métricas lógicas no disponibilidad externa. Opcionales/ausencias no son cero. RUC string; int64/BigDecimal pueden perder precisión en JS. Sin credenciales SENACE en frontend. CSV/JSON, usuario demostrativo, tema, fuentes/iconos y mapas requieren decisiones antes de habilitar comportamiento.

Plan conserva siete lotes: 1 scaffold/shell/tokens/logos aprobados/navegación fiel; 2 API/tipos/adaptadores/mocks; 3 dashboard honesto; 4 explorador con cartografía aprobada; 5 consultoras; 6 monitor; 7 integración progresiva y responsive. No instalar Tailwind, shadcn, TanStack o Web3 por existir en una skill/export. Node/npm no ejecutados ni versiones verificadas. Próximo prompt debe preparar Lote1 con lista exacta de archivos y permisos separados de diagnóstico, instalación, exportación de assets, tests/build, servidor y tráfico. No iniciar automáticamente implementación, backend, SQLite o modificaciones remotas Stitch.
