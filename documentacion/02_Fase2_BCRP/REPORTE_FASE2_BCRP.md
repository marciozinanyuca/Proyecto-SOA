# Fase 2 — Diagnóstico y preparación de la integración real BCRP

**Proyecto:** EcoInvest, plataforma de inteligencia gubernamental SENACE + BCRP.  
**Fecha:** 18 de octubre de 2026.  
**Repositorio:** `D:\Documentos\GitHub\Proyecto-SOA`.  
**Naturaleza:** lectura y diagnóstico; la única creación autorizada es este documento.

No se modificaron backend, frontend, reportes anteriores, Resilience4j o SQLite. No se ejecutaron compilaciones, pruebas, instalaciones ni commits. No se consultó la API real o documentación remota del BCRP. Por tanto, los nombres económicos, unidades, periodicidades, formatos de fechas y disponibilidad de las series siguen pendientes de validación oficial; no se deducen como hechos a partir de códigos o comentarios.

Se consultó `REPORTE_IMPLEMENTACION_FASE1A.md`. El usuario denomina Fase 1B al cierre de estabilización; el reporte existente conserva el nombre Fase 1A. Ese reporte registra generación OpenAPI y compilación satisfactorias y 18 pruebas superadas, con release 17 y runtime Java 20.0.2. Es un resultado previo, no una ejecución nueva ni una prueba de conectividad real BCRP.

## 1. Arquitectura actual de integración BCRP

### 1.1. Flujo de consulta

```text
GET /api/dashboard?sector={sector}&anio={anio}
  → ApiApi generada desde OpenAPI
  → EcoInvestController.obtenerDashboard
      → consulta SENACE existente, sin cambios en esta fase
      → BcrpClient.obtenerDatosBcrp(sector, anio)
          → selección local del código por sector
          → GET {bcrpBaseUrl}/{codigoSerie}/json
          → RestTemplate deserializa Map.class
          → extrae periods[último].values[0]
      → DashboardResponse: indicador opcional y procedencia
```

No hay endpoint público BCRP independiente ni capa Service propia en este flujo. El monitor `/api/estado-servicios` expone métricas BCRP, no observaciones económicas ni un sondeo activo del proveedor.

Evidencia: [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), líneas **35–78 y 81–113**; [BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java), líneas **40–88**.

### 1.2. Endpoint y configuración

La base predeterminada declarada por el cliente es:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api
```

La plantilla actual es:

```text
GET https://estadisticas.bcrp.gob.pe/estadisticas/series/api/{codigoSerie}/json
```

Para hidrocarburos, con la configuración predeterminada, el destino construido sería:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json
```

Esto identifica el destino configurado en el dominio BCRP, no acredita una respuesta obtenida durante esta fase. La propiedad `external.apis.bcrp-base-url` puede reemplazarlo. `application.yml` no declara esa propiedad; no se inspeccionaron secretos ni variables externas para determinar un override efectivo.

Evidencia: `BcrpClient.java`, líneas **19–20 y 50–57**; [application.yml](../../backend/src/main/resources/application.yml), líneas **1–25**.

### 1.3. Series declaradas en código

| Sector normalizado | Código configurado | Estado de significado económico, unidad y periodicidad |
|---|---|---|
| HIDROCARBUROS | PM05141AA | No verificado externamente |
| MINERIA / MINERÍA | PN01774AM | No verificado externamente |
| ELECTRICIDAD / ENERGIA | PN01780AM | No verificado externamente |
| PESCA | PN01773AM | No verificado externamente |
| AGRICULTURA | PN01771AM | No verificado externamente |
| TRANSPORTES | PN01781AM | No verificado externamente |

Evidencia: `BcrpClient.java`, líneas **24–34**. Hay seis códigos distintos. No debe asumirse que todos representen variaciones porcentuales de PBI o compartan periodicidad solo porque el código los agrupe.

### 1.4. Parámetros año y sector

- El consumidor envía `sector` y `anio` como query parameters del dashboard.
- OpenAPI exige `sector`; `anio` es opcional y tiene default 2017.
- El controlador también sustituye año nulo por 2017 y sector nulo/vacío por Hidrocarburos.
- El sector se usa localmente para seleccionar el código. No se envía como query parameter al BCRP.
- El año no se envía al BCRP, no determina un rango y no se usa para filtrar la respuesta.
- `consultarBcrp()` conserva Hidrocarburos/2017 como parámetros por defecto de ese método Java; no es una ruta HTTP independiente.

Evidencia: [openapi.yaml](../../backend/src/main/resources/openapi.yaml), líneas **9–26**; controlador, **37–42**; cliente, **40–55 y 64–65**.

### 1.5. Interpretación JSON y precisión

`getForObject(url, Map.class)` obtiene un mapa genérico. El cliente copia su contenido y agrega metadatos locales. La extracción acepta una lista `periods`, toma su último elemento, exige que sea un mapa con lista `values`, y lee únicamente `values[0]`. Admite String o Number, convierte mediante Double y rechaza NaN e infinitos.

No se interpreta el nombre del período, no se valida correspondencia entre columnas y serie y no se extrae unidad o descripción oficial. Una estructura que no cumpla esas comprobaciones produce indicador nulo. El controlador convierte un Number finito a BigDecimal, pero la conversión previa por Double puede haber perdido precisión.

Evidencia: cliente, líneas **57–88**; controlador, **49–52**.

## 2. Problemas encontrados con evidencia del código

| Prioridad | Problema y evidencia | Consecuencia | Corrección mínima futura |
|---|---|---|---|
| P0 | Año solo como metadato: cliente 54 y 65 | No se garantiza correspondencia con el año seleccionado | Construir rango según periodicidad oficial y validar períodos recibidos |
| P0 | Selección por posición: cliente 74–78 | El último registro puede ser de otro año o estar desordenado | Interpretar período y seleccionar por semántica temporal |
| P0 | Catálogo sectorial sin metadatos verificados: cliente 25–33 | Puede mostrarse una magnitud inadecuada bajo el nombre variación | Verificar cada serie y documentar unidad, descripción y periodicidad |
| P1 | Sector desconocido usa PM05141AA: cliente 51–52 y 98–99 | Consulta de otro sector sin advertencia | Resolver catálogo explícitamente; no sustituir silenciosamente por hidrocarburos |
| P1 | Dashboard descarta serie y respuesta externa: controlador 65–76 | Consumidor no puede auditar unidad, código o período | Agregar metadatos BCRP tipados preservando campos actuales |
| P1 | Ausencia, formato y fallo confluyen en NO_DISPONIBLE: cliente 63 y 102 | No se diferencia falta de observación de indisponibilidad del proveedor | Incorporar estado/motivo seguro sin alterar Resilience4j |
| P1 | Extracción de la primera columna sin identidad: cliente 78 | No demuestra qué serie corresponde al valor | Validar identidad/metadatos y cantidad de valores conforme al JSON real |
| P2 | Uso de Double: cliente 83 y controlador 52 | Pérdida potencial de precisión decimal | Convertir a BigDecimal desde representación decimal validada |
| P2 | Descripción fija en fallback: cliente 107 | Atribuye significado económico no validado incluso sin observación | Obtener descripción del catálogo verificado o no atribuirla |
| P2 | toUpperCase sin Locale y alias incompletos: cliente 51 | Normalización dependiente de entorno; ENERGÍA no figura | Normalización local y explícita de sectores sin tocar SENACE |

`TODOS`, que puede tener sentido para la lista de proyectos, cae en la serie por defecto BCRP. No existe regla económica para un agregado multisectorial. La fase debe definir indicador ausente con motivo o una serie agregada previamente validada; no inventar una media de sectores.

### 2.1. Errores HTTP, conexión y respuestas vacías

El RestTemplate está configurado con 5 segundos de timeout de conexión y lectura: [AppConfig.java](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java), líneas **12–17**.

El cliente BCRP no contiene un try/catch alrededor de la llamada HTTP. No hay manejo propio de estados HTTP, HTML, Content-Type incorrecto o fallos de conexión. Se delegan al comportamiento del RestTemplate y, si la interceptación está activa, a `@CircuitBreaker` con fallback. No debe afirmarse que una llamada fallida siempre termina en respuesta degradada: la interceptación no ha sido demostrada por las pruebas actuales.

Para respuestas nulas o estructuras vacías/malformadas que sí llegan como Map, la extracción devuelve null y el resultado indica NO_DISPONIBLE. No lanza un error semántico para que el breaker lo registre: una respuesta con transporte exitoso pero datos inutilizables puede no contar como fallo externo.

El fallback conserva metadatos de consulta, devuelve números y período nulos, y una lista de períodos vacía. No lee SQLite ni devuelve valores económicos simulados.

Evidencia: cliente, líneas **40–47, 57, 71–88 y 91–113**. Resilience4j conserva su configuración actual en `application.yml`, **22–25**; se describe como dependencia existente y no se propone modificarla en esta fase.

### 2.2. Constantes y simulación

- No quedan constantes económicas sustitutivas -2.45/3.53 en el cliente o controlador actual.
- Hay constantes legítimas de configuración: códigos de serie, año predeterminado 2017 y sector predeterminado.
- El fallback BCRP no simula observaciones; expresa ausencia.
- El valor 7.25 de las pruebas es un dato de mock, no una observación real del BCRP.
- SENACE sigue usando datos SIMULADO, pero queda fuera del alcance de cambios de esta fase.

## 3. Análisis de la selección temporal

**Respuesta a la pregunta principal:** el indicador actual no está garantizado para el año solicitado. Puede coincidir accidentalmente, pero el código no lo comprueba.

### 3.1. Comportamiento actual

```text
Dashboard consulta año A
  → BCRP recibe solo código de serie, sin A
  → se elige el último registro retornado
  → se etiqueta anioConsultado=A
```

`anioConsultado` significa intención de consulta, no período de la observación. Es incorrecto usarlo como prueba del año económico real.

Si el último registro tiene un valor inválido, se devuelve ausencia aunque pudiera haber otro registro válido. Buscar un registro anterior tampoco sería una solución automática: podría introducir otro año o un desplazamiento temporal no autorizado.

### 3.2. Decisiones necesarias

| Caso | Decisión requerida |
|---|---|
| Serie anual verificada | Consultar y seleccionar exactamente el año requerido |
| Serie mensual verificada | Determinar qué observación o agregado anual necesita el dashboard |
| Serie trimestral u otra frecuencia | Definir selección o agregado con significado económico explícito |
| Año sin observaciones | Devolver ausencia, sin recurrir a años vecinos |
| Año parcial | Declarar cobertura y período; no presentar un agregado incompleto como anual completo |
| Períodos duplicados o contradictorios | Rechazar ambigüedad o aplicar una regla documentada, nunca elegir por orden accidental |

No se recomienda elegir diciembre, promedio o suma anual sin validar la serie y aprobar la regla. La variación porcentual de un promedio y el promedio de variaciones no son operaciones intercambiables.

### 3.3. Estrategia mínima recomendada

1. Verificar catálogo y periodicidad oficial por código.
2. Si hay una serie anual adecuada para la métrica del dashboard, preferir esa semántica antes de agregar observaciones de otra frecuencia.
3. Si se mantiene una serie de otra frecuencia, documentar la regla elegida y mostrar el período preciso.
4. Construir la URL con inicio y fin correspondientes a la frecuencia y sintaxis oficial verificadas.
5. Verificar en el JSON que los períodos pertenecen al año pedido, incluso si el proveedor ignora el rango.
6. Seleccionar sin depender del orden de la lista.
7. Publicar el período efectivo y la unidad; cuando falte dato, publicar ausencia y motivo.

La sintaxis de rango BCRP no se fija en este documento como hecho: no se consultó documentación oficial ni la API. La futura implementación no debe inventarla.

## 4. Contrato actual y cambios propuestos

### 4.1. Contrato público actual

`DashboardResponse` contiene `total`, `aprobados`, `variacionSeleccionada`, `proyectos` y `fuentes`. La variación es opcional, y una anotación generada desde OpenAPI omite el campo si no existe valor. `fuentes` es un objeto sin propiedades tipadas.

El controlador solo publica dentro de fuentes:

```text
senace
bcrp
sectorConsultado
anioConsultado
```

No publica `serie`, unidad, período efectivo o descripción BCRP, aunque el mapa interno contenga `serie`. Tampoco hay un esquema de respuesta económica independiente.

Evidencia: `openapi.yaml`, líneas **82–107**; controlador, **65–76**.

### 4.2. Propuesta aditiva y compatible

Conservar las cuatro rutas, sus métodos, query parameters y los campos existentes. Mantener `fuentes.bcrp` como String, no reemplazarlo por un objeto. Agregar opcionalmente un objeto `indicadorBcrp` a `DashboardResponse`, definido con un esquema propio en OpenAPI.

| Campo propuesto | Tipo o semántica | Regla |
|---|---|---|
| codigoSerie | string | Código realmente consultado, nunca el de otro sector por sustitución silenciosa |
| descripcion | string | Descripción oficial verificada |
| unidad | string | Unidad validada en documentación o metadatos oficiales, no deducida del nombre del campo actual |
| periodicidad | string documentado | Frecuencia verificada |
| anioSolicitado | integer | Año recibido y normalizado |
| periodo | string opcional | Período efectivo de la observación; formato documentado tras validación |
| valor | number opcional | Decimal real, sin constantes sustitutas |
| origen | string | API_DIRECTA_BCRP o NO_DISPONIBLE, consistente con fuentes.bcrp |
| estado / motivo | códigos documentados | Diferenciar ausencia de observación, error externo o período no interpretable |

Los nombres son una propuesta, no archivos generados ni implementación existente. No se aporta ejemplo con un valor económico inventado. Debe evaluarse si basta con estado o con motivo para evitar redundancia.

`variacionSeleccionada` se conserva por compatibilidad, pero debe reflejar el mismo valor que el nuevo detalle solo si la métrica de la serie corresponde a una variación. Si la serie tiene otra unidad, la solución no es renombrar arbitrariamente su valor como variación: debe elegirse una serie adecuada o acordarse una evolución explícita del contrato.

### 4.3. Ausencia y errores

- Mantener ausencia por omisión del valor, sin cero, -2.45, 3.53 o año vecino.
- Cuando no hay observación, conservar código y unidad conocidos; no fabricar período efectivo. El período puede faltar o ser null si el contrato lo define expresamente.
- No confundir anioSolicitado con periodo.
- Mantener 200 para el dashboard degradado si se conserva el comportamiento actual. Definir explícitamente el tratamiento de sector/año inválidos antes de introducir nuevos estados HTTP.
- La clasificación de errores debe hacerse de forma local y segura sin modificar anotaciones, dependencias, registro o configuración Resilience4j.

## 5. Plan de implementación incremental

**Este plan no se ejecutó.**

### Paso 1 — Validación oficial autorizada

Solicitar autorización antes de consultar la API real. Obtener documentación y respuestas acotadas para los seis códigos, al menos dos años distintos, y comprobar descripción, unidad, periodicidad, sintaxis de rango, estructura y representación de datos ausentes. No iniciar EcoInvest ni tocar SQLite.

La consulta debe ser limitada y de lectura; no ejecutar barridos ni descargas masivas. Los comandos exactos se propondrán antes de ejecutarlos. Si se necesitan fixtures en archivos, solicitar autorización adicional para crearlos, ya que esta tarea solo autoriza este reporte.

**Salida:** tabla de series y muestras verificadas; no basta con HTTP 200. Si no se dispone de autorización o respuesta válida, no inventar el catálogo ni implementar un parser supuesto.

### Paso 2 — Definir semántica temporal y contrato

Acordar métrica anual o período específico, comportamiento de TODOS/sectores no soportados, unidad y ausencia. Diseñar `indicadorBcrp` como ampliación aditiva y mantener el default 2017 salvo una decisión explícita distinta.

**Salida:** especificación mínima verificable por sector y año; contrato preparado para código, unidad, período y procedencia.

### Paso 3 — Consulta y extracción BCRP

Modificar solo cliente BCRP y tipos auxiliares si son necesarios y autorizados. Construir rango temporal, validar cuerpo y período, extraer decimal sin tránsito innecesario por Double y devolver metadatos coherentes. Mantener las firmas públicas existentes cuando sea posible y los fallbacks de ausencia, sin persistencia o cambios de Resilience4j.

**Salida:** mismo año determina petición y selección; ninguna observación de otro año se presenta como válida para el solicitado.

### Paso 4 — Exponer metadatos en dashboard

Modificar únicamente la parte BCRP del controlador y OpenAPI. Conservar consultas, filtros, datos y lógica SENACE; no ampliar el monitor o incorporar endpoints nuevos innecesarios.

**Salida:** dato trazable desde JSON externo hasta respuesta pública, sin romper campos o rutas existentes.

### Paso 5 — Verificación autorizada

Preparar y ejecutar, con autorización posterior, pruebas unitarias y HTTP simuladas, regeneración OpenAPI y compilación. La autorización de la fase anterior no se utiliza para ejecutar Maven en esta fase de solo diagnóstico.

**Salida:** casos temporales, ausencia, metadatos y regresión aprobados. Una prueba real acotada del proveedor sería independiente y requeriría autorización.

## 6. Archivos que sería necesario modificar

| Archivo | Motivo futuro |
|---|---|
| `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java` | Catálogo verificado, URL temporal, parser de período, extracción y metadatos |
| `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java` | Conservar/propagar detalle BCRP y precisión sin tocar lógica SENACE |
| `backend/src/main/resources/openapi.yaml` | Esquema aditivo de metadatos y reglas de ausencia/errores |
| `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java` | Verificación de URL, año, unidad, parser y errores |
| `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java` | Detalle económico y compatibilidad del dashboard |

**Opcionales, sujetos a autorización de implementación:** DTOs auxiliares dentro del paquete BCRP actual o un subpaquete nuevo sin renombrar paquetes existentes; fixtures JSON en `backend/src/test/resources/` si se aprueba su creación. No son necesarios nuevos starters para un adaptador mínimo.

No se propone modificar `SenaceClient.java`, frontend, POM, versiones, wrapper, AppConfig, configuración Resilience4j o SQLite. `application.yml` fue leído; no necesita cambiarse para resolver el defecto temporal. No editar manualmente `target/`: los modelos públicos se regenerarán desde OpenAPI solo con autorización.

## 7. Pruebas que deberían realizarse

### 7.1. Cobertura actual y límites

[BcrpClientTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java), líneas **24–77**, cubre valor mock válido, indicadores inválidos/no finitos, estructuras nulas/vacías y fallback directo. Usa `anyString()` para URL, por lo que no detecta que el año esté ausente. Sus períodos de ejemplo no incluyen identidad temporal.

[EcoInvestControllerTest.java](../../backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java), líneas **33–77**, cubre omisión de indicador, preservación de número finito, NaN y otras rutas. No comprueba serie, unidad o período.

El reporte anterior registra 8 casos BCRP, 4 MVC y 18 totales aprobados. No prueba autenticidad económica, selección de año ni disponibilidad externa. No se repitieron aquí.

### 7.2. Casos nuevos propuestos

| Grupo | Casos y aserciones |
|---|---|
| Catálogo | Cada sector resuelve código, unidad y periodicidad verificados; desconocidos y TODOS no caen silenciosamente en hidrocarburos |
| URL temporal | Capturar URL exacta con ArgumentCaptor o MockRestServiceServer; dos años producen rangos distintos y válidos según documentación |
| Selección | Respuesta con varios años y orden invertido: resultado del año solicitado, sin depender de posición |
| Contradicción externa | Proveedor retorna un período fuera del rango: no etiquetarlo como año solicitado |
| Frecuencia | Serie anual y otras frecuencias conforme a reglas aprobadas; año parcial y meses faltantes sin agregación tácita |
| Ausencia | Año sin datos, período sin valor, marcador no numérico, null, lista vacía y respuesta sin campos |
| Formato | Período no interpretable, duplicados y valores/columnas incompatibles |
| Precisión | Decimales extensos y valores negativos/cero reales preservados; NaN/infinitos rechazados |
| Metadatos | Código, unidad y período provienen de catálogo/respuesta verificados; no del año decorativo |
| Transporte simulado | Timeout/conexión, HTTP 4xx/5xx, HTML y Content-Type incorrecto; resultado controlado sin falsificar datos |
| Dashboard | Nuevo detalle consistente con variacionSeleccionada y fuentes.bcrp; indicador ausente continúa omitido |
| Regresión | Cuatro rutas preservadas; SENACE y monitor conservan su comportamiento |

Usar mocks para lógica pura y MockRestServiceServer para probar deserialización y errores del RestTemplate real sin red. No limitar los casos HTTP a mapas fabricados: eso no verifica el procesamiento JSON.

Para pruebas directas de clientes, comprobar excepciones HTTP por separado del fallback. La interceptación AOP no debe darse por probada ni alterarse en esta fase. Si se necesitase verificar el comportamiento de extremo a extremo con el breaker, se documentaría como validación separada sin modificar Resilience4j.

No arrancar el contexto productivo de forma que abra SQLite. Las pruebas de controlador pueden seguir siendo standalone y los tests de contexto deben conservar exclusiones de persistencia.

## 8. Riesgos de regresión

| Riesgo | Mitigación propuesta |
|---|---|
| Corregir código de serie cambia el indicador observado | Validación económica documentada por sector, pruebas de catálogo y decisión explícita |
| Confundir serie mensual con anual | Periodicidad verificada y regla aprobada; no inferir por sufijos |
| Nuevos campos alteran modelos generados | Evolución aditiva; regeneración y compilación autorizadas, sin edición manual |
| Pérdida de compatibilidad del campo variacionSeleccionada | Mantenerlo y alinear su semántica con la serie; no sustituirlo por un objeto |
| Convertir fuentes.bcrp de string a objeto | Evitarlo; nuevo detalle separado |
| Agregación económica incorrecta | No inventar promedio/suma/último mes; documentar cobertura y método |
| Año sin datos resuelto con año próximo | Rechazar desplazamiento silencioso; mantener ausencia |
| Cambios decimales pequeños por BigDecimal | Tests de precisión y comparación numérica; informar corrección de representación |
| Parser dependiente de formato o idioma | Fixtures oficiales verificadas y rechazo explícito de formatos desconocidos |
| Más solicitudes al proveedor | Acotar consultas por rango, sin reintentos nuevos ni barridos |
| Error capturado altera métricas del breaker | Mantener configuración/anotaciones y explicitar comportamiento de errores; no introducir cambios generales de resiliencia |
| Retorno temprano por validación impide otras partes del dashboard | Definir contrato de entradas inválidas y degradación antes de implementar |

## 9. Criterios de aceptación

1. Las cuatro rutas, métodos y parámetros existentes se conservan; no se exige crear una ruta BCRP independiente.
2. El año determina el rango consultado y la selección efectiva, demostrados por pruebas de URL y de respuesta fuera de orden.
3. El valor real puede rastrearse a una observación de la API oficial; los mocks no se presentan como evidencia de autenticidad económica.
4. Cada serie admitida tiene código, descripción, unidad y periodicidad verificados. Sectores no soportados no se sustituyen silenciosamente.
5. La respuesta pública incluye código, unidad, período efectivo y procedencia, sin reemplazar los campos existentes por tipos incompatibles.
6. Si no existe observación, el valor queda ausente, la procedencia indica NO_DISPONIBLE y el motivo es explícito; no se inventa período ni se utiliza un año distinto.
7. La selección o agregación para frecuencias no anuales tiene una regla de negocio aprobada y verificable; no se inventa una fórmula.
8. Los errores y cuerpos inesperados no se convierten en cifras aparentemente válidas.
9. Los números se preservan con precisión adecuada; cero y negativos reales no se confunden con ausencia.
10. Pruebas locales y generación/compilación pasan cuando se autorice ejecutarlas. Se distingue esa evidencia de una eventual comprobación real del proveedor.
11. No se modifica SENACE, frontend, Resilience4j, SQLite, paquetes o versiones; no se instalan dependencias ni se editan generados manualmente.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest ya elimina las constantes económicas ficticias y expresa correctamente la ausencia del indicador. La estabilización anterior registra 18 pruebas aprobadas; en esta fase no se ejecutaron comandos de build o test.

El cliente actual apunta por defecto a `https://estadisticas.bcrp.gob.pe/estadisticas/series/api/{serie}/json`. Hidrocarburos usa PM05141AA; otros sectores usan cinco códigos adicionales del mapa local. El sector selecciona una serie en Java y el año no se envía ni filtra: se devuelve `periods[último].values[0]` etiquetado con el año solicitado. Por tanto, todavía no existe garantía temporal o económica del indicador.

El dashboard conserva solo el número y la procedencia, descartando código y demás metadatos. Se propone un detalle BCRP aditivo en OpenAPI con código, unidad, periodicidad, período, valor y procedencia, manteniendo las cuatro rutas y campos existentes. No se modificará SENACE, frontend, Resilience4j o SQLite.

**Siguiente paso recomendado:** solicitar autorización para verificar documentación y realizar consultas reales acotadas al BCRP. Validar primero significado, unidad, periodicidad y sintaxis temporal de las seis series; después acordar la selección anual y autorizar un lote mínimo en BcrpClient, controlador, contrato y pruebas. Sin esa validación no debe implementarse una URL temporal supuesta ni una agregación inventada.

La única creación realizada durante este diagnóstico es `REPORTE_FASE2_BCRP.md`. No se consultó el proveedor ni se implementó el plan.
