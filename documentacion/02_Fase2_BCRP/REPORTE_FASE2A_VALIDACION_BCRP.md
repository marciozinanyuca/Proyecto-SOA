# Fase 2A — Validación oficial de series BCRP para EcoInvest

**Fecha de consulta:** 18 de octubre de 2026.  
**Proyecto:** EcoInvest, backend Java 17 / Spring Boot 3.2.4, integración SENACE + BCRP.  
**Resultado:** seis códigos existentes y consultables; cinco asignaciones sectoriales incorrectas y una serie de hidrocarburos pertinente, aunque de una familia distinta a la recomendada para un dashboard homogéneo de PBI anual.

Se leyó `REPORTE_FASE2_BCRP.md` y se contrastó con el cliente actual. Solo se realizaron lecturas locales y GET públicos oficiales, pequeños y delimitados. La única creación de archivo de esta fase es este reporte. No se modificó backend, SENACE, frontend, Resilience4j, SQLite o reportes anteriores; no se inició la aplicación, ejecutó Maven, instaló dependencias ni realizó commits.

## 1. Fuentes oficiales consultadas

### 1.1. Documentación y catálogos

- [Guía oficial de API BCRPData](https://estadisticas.bcrp.gob.pe/estadisticas/series/ayuda/api): estructura GET, formatos, rangos, idioma y restricción de frecuencia al combinar códigos.
- [Categoría oficial mensual: PBI por sectores](https://estadisticas.bcrp.gob.pe/estadisticas/series/mensuales/pbi-por-sectores): agrupación económica y metadatos de códigos existentes.
- [Categoría oficial anual: PBI por sectores](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/pbi-por-sectores): alternativas anuales identificadas por nombre y código, sin barrido de códigos.

Se accedió también a la portada de BCRPData para navegar a esas categorías. La página genérica `/series/anuales` tuvo timeout en el visor; la categoría anual específica sí pudo consultarse.

### 1.2. Fichas de las seis series configuradas

| Código | Página oficial consultada |
|---|---|
| PM05141AA | [Hidrocarburos](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM05141AA/html) |
| PN01774AM | [Indicador de Demanda Interna](https://estadisticas.bcrp.gob.pe/estadisticas/series/mensuales/resultados/PN01774AM/html) |
| PN01780AM | [Agrícola - Mandarina](https://estadisticas.bcrp.gob.pe/estadisticas/series/mensuales/resultados/PN01780AM/html) |
| PN01773AM | [PBI Desestacionalizado - mensual](https://estadisticas.bcrp.gob.pe/estadisticas/series/mensuales/resultados/PN01773AM/html) |
| PN01771AM | [Sectores Primarios](https://estadisticas.bcrp.gob.pe/estadisticas/series/mensuales/resultados/PN01771AM/html) |
| PN01781AM | [Agrícola - Naranja](https://estadisticas.bcrp.gob.pe/estadisticas/series/mensuales/resultados/PN01781AM/html) |

### 1.3. GET de API completados

Se obtuvieron **siete respuestas HTTP 200**, todas con cuerpo JSON y `Content-Type: text/html; charset=UTF-8`:

1. [PM05141AA, 2017–2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2017/2018/esp): dos observaciones anuales.
2. [Cinco códigos mensuales actuales, enero de 2017](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PN01774AM-PN01780AM-PN01773AM-PN01771AM-PN01781AM/json/2017-1/2017-1/esp): un mes y cinco columnas.
3. [Los mismos cinco códigos, enero de 2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PN01774AM-PN01780AM-PN01773AM-PN01771AM-PN01781AM/json/2018-1/2018-1/esp): un mes y cinco columnas.
4. [PM05141AA, 2004–2005](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2004/2005/esp): solo se devuelve 2005.
5. [PN01780AM, diciembre de 2006–enero de 2007](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PN01780AM/json/2006-12/2007-1/esp): solo se devuelve enero de 2007.
6. [Seis alternativas anuales verificadas, 2017–2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04969AA-PM04971AA-PM04973AA-PM04974AA-PM04978AA-PM04983AA/json/2017/2018/esp): dos años y seis columnas.
7. [PM05141AA, solo 2004](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2004/2004/esp): lista de períodos vacía.

Los primeros intentos de JSON mediante el visor web no fueron accesibles. Una petición desde el sandbox tampoco conectó. Las muestras se recuperaron mediante GET de PowerShell con aprobación de ejecución fuera del sandbox, sin guardar archivos auxiliares. No se hicieron descargas masivas, POST, búsquedas de códigos por enumeración o consultas a proveedores ajenos al BCRP.

## 2. Tabla de series: identidad, unidad y periodicidad verificadas

Las descripciones siguientes reproducen la denominación económica de las fichas y `config.series[].name`. La periodicidad se confirma por las fichas y los períodos devueltos, no por el sufijo del código.

| Código | Descripción económica oficial | Unidad | Periodicidad | Existencia y disponibilidad observadas |
|---|---|---|---|---|
| PM05141AA | Producción minera e hidrocarburos por principales productos (variación porcentual interanual) - Hidrocarburos | Variación porcentual interanual, % | Anual | Ficha y JSON; 2017 y 2018 recuperados |
| PN01774AM | Producto bruto interno y demanda interna (índice 2007 = 100) - Indicador de Demanda Interna | Índice, base 2007 = 100 | Mensual | Ficha y JSON; enero de 2017 y 2018 recuperados |
| PN01780AM | Producción agropecuaria (miles de toneladas) - Agrícola - Mandarina | Miles de toneladas | Mensual | Ficha y JSON; enero de 2017 y 2018 recuperados |
| PN01773AM | Producto bruto interno y demanda interna (índice 2007 = 100) - PBI Desestacionalizado - mensual | Índice, base 2007 = 100; desestacionalizado | Mensual | Ficha y JSON; enero de 2017 y 2018 recuperados |
| PN01771AM | Producto bruto interno y demanda interna (índice 2007 = 100) - Sectores Primarios | Índice, base 2007 = 100 | Mensual | Ficha y JSON; enero de 2017 y 2018 recuperados |
| PN01781AM | Producción agropecuaria (miles de toneladas) - Agrícola - Naranja | Miles de toneladas | Mensual | Ficha y JSON; enero de 2017 y 2018 recuperados |

Fuentes: fichas individuales de la sección 1.2 y muestras API 1–3. Disponibilidad observada no significa SLA ni garantiza años futuros o cualquier período histórico.

### 2.1. Contraste con el código actual

[BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java), líneas **25–33**, conserva esas seis series. Las líneas **50–57** construyen `/{codigoSerie}/json` sin rango. Las líneas **71–84** toman el último período y su primer valor, sin interpretar fecha o unidad. `anioConsultado`, línea **65**, es solo un metadato local.

[EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), líneas **49–76**, publica número y procedencia, pero no código, unidad o período. [openapi.yaml](../../backend/src/main/resources/openapi.yaml), líneas **89–99**, documenta el indicador opcional y la procedencia, sin un objeto económico tipado.

El diagnóstico de Fase 2 queda confirmado. Además, esta validación detecta Content-Type incorrecto y reordenación de columnas, aspectos que las pruebas basadas en Map mockeado no podían demostrar.

## 3. Relación real con el sector asignado

| Sector EcoInvest | Código actual | Evaluación económica |
|---|---|---|
| Hidrocarburos | PM05141AA | Pertinente: producción de hidrocarburos, variación anual. No debe describirse sin matices como miembro de la familia PBI sectorial, porque la ficha lo clasifica en producción minera e hidrocarburos. |
| Minería / Mineria | PN01774AM | Incorrecto: demanda interna, no minería. |
| Electricidad / Energia | PN01780AM | Incorrecto: producción de mandarina, sin correspondencia con electricidad. |
| Pesca | PN01773AM | Incorrecto: PBI desestacionalizado agregado, no pesca. |
| Agricultura | PN01771AM | Incorrecto como indicador específico: agregado de sectores primarios, no solo agricultura. |
| Transportes | PN01781AM | Incorrecto: producción de naranja, no transportes. |

Son conclusiones de correspondencia entre catálogo oficial y mapa Java, no resultados de ejecutar el dashboard.

### 3.1. Comparabilidad

Las seis series **no son comparables como una misma métrica**: mezclan una tasa anual, tres índices mensuales y dos volúmenes físicos de productos agrícolas. Que varias tengan base 2007 o unidad común no convierte demanda interna, PBI agregado y sectores primarios en el mismo concepto. Tampoco permite interpretar índices como porcentajes.

No corresponde sumarlas, promediarlas, hacer un ranking de crecimiento entre sus valores brutos o mostrarlas todas bajo “Variación porcentual del PBI sectorial”. Mandarina y naranja son productos distintos, y su volumen no mide desempeño de electricidad o transporte.

## 4. Ejemplos pequeños de JSON y observaciones reales

Los bloques siguientes proceden de respuestas oficiales de esta sesión. Solo se normalizaron espacios; cuando se recorta un bloque se indica. Son datos públicos sin credenciales o información personal. No son mocks.

### 4.1. PM05141AA: dos años

Fuente: [API anual 2017–2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2017/2018/esp).

```json
{
  "config": {
    "title": "Producción minera e hidrocarburos por principales productos (variación porcentual interanual)",
    "series": [
      {
        "name": "Producción minera e hidrocarburos por principales productos (variación porcentual interanual) - Hidrocarburos",
        "dec": "1"
      }
    ]
  },
  "periods": [
    {"name": "2017", "values": ["-2.44780196733593"]},
    {"name": "2018", "values": ["-0.0345569324875896"]}
  ]
}
```

El año se representa como String de cuatro dígitos y los valores también son Strings. La precisión transmitida es mayor que el formato visual de la tabla HTML. `dec: "1"` no debe usarse para truncar arbitrariamente el valor recibido; los resultados HTML de esta serie se muestran a una cifra decimal.

### 4.2. Cinco series mensuales: estructura y orden

Fuente: [consulta conjunta de enero de 2017](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PN01774AM-PN01780AM-PN01773AM-PN01771AM-PN01781AM/json/2017-1/2017-1/esp). Respuesta completa con formato normalizado:

```json
{
  "config": {
    "title": "Producto bruto interno y demanda interna (índice 2007 = 100)",
    "series": [
      {"name": "Producto bruto interno y demanda interna (índice 2007 = 100) - Sectores Primarios", "dec": "1"},
      {"name": "Producto bruto interno y demanda interna (índice 2007 = 100) - PBI Desestacionalizado - mensual", "dec": "1"},
      {"name": "Producto bruto interno y demanda interna (índice 2007 = 100) - Indicador de Demanda Interna", "dec": "1"},
      {"name": "Producción agropecuaria (miles de toneladas) - Agrícola - Mandarina", "dec": "1"},
      {"name": "Producción agropecuaria (miles de toneladas) - Agrícola - Naranja", "dec": "1"}
    ]
  },
  "periods": [
    {
      "name": "Ene.2017",
      "values": ["138.300737656216", "159.998577212189", "162.117410450866", "5.729939", "25.667409"]
    }
  ]
}
```

**Hallazgo:** la petición empezaba por PN01774AM, pero la respuesta empieza por Sectores Primarios (PN01771AM). Los valores se corresponden con el orden de `config.series`, no se debe asumir el orden solicitado. En estas muestras los objetos de series no incluyen un campo de código. `config.title` describe el primer grupo, no todas las unidades de la consulta combinada.

Para el lote mínimo se recomienda **una serie por petición**, con código conocido por contexto, comprobando que exista una sola serie y una columna. Evita deducir identidad por posición de la URL o por el título global.

### 4.3. Comparación temporal observada de las seis series

| Código | Período 1 | Valor recibido | Período 2 | Valor recibido |
|---|---|---:|---|---:|
| PM05141AA | 2017 | -2.44780196733593 | 2018 | -0.0345569324875896 |
| PN01774AM | Ene.2017 | 162.117410450866 | Ene.2018 | 166.786701089637 |
| PN01780AM | Ene.2017 | 5.729939 | Ene.2018 | 3.685209 |
| PN01773AM | Ene.2017 | 159.998577212189 | Ene.2018 | 164.625418244199 |
| PN01771AM | Ene.2017 | 138.300737656216 | Ene.2018 | 139.449815337223 |
| PN01781AM | Ene.2017 | 25.667409 | Ene.2018 | 24.52787 |

Fuentes: API 1, 2 y 3 de la sección 1.3. Los valores mensuales representan enero, no el año completo. Se eligieron muestras pequeñas deliberadamente.

### 4.4. Ausencia observada

Para [PM05141AA en 2004](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2004/2004/esp), el HTTP fue 200 y se recibió el mismo `config`, con esta propiedad:

```json
{"periods": []}
```

Es un extracto, no el cuerpo completo. En el rango 2004–2005 solo apareció 2005; para mandarina, diciembre de 2006–enero de 2007 solo apareció `Ene.2007`. Por tanto, un período solicitado puede omitirse sin una fila con valor null, y HTTP 200 no significa que exista la observación solicitada.

**Límite:** no se encontró en estas muestras un hueco interno con un marcador textual, String vacío o null. No se afirma que `n.d.` u otro token sea la representación oficial universal de ausencia. Los parsers deberán conservar el manejo seguro de valores no numéricos y documentar nuevos formatos si se observan.

## 5. Sintaxis temporal oficial y comportamiento HTTP

La [guía de API](https://estadisticas.bcrp.gob.pe/estadisticas/series/ayuda/api) define segmentos GET para código(s), formato, inicio, fin e idioma. Permite hasta diez series de igual frecuencia; sin rango obtiene datos recientes. Español es el idioma predeterminado. Es preferible declararlo para estabilizar el parser.

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api/{codigos}/{formato}/{inicio}/{fin}/{idioma}
```

Aplicaciones comprobadas en esta sesión:

| Frecuencia | Inicio / fin | Ejemplo probado |
|---|---|---|
| Anual | YYYY / YYYY | [PM05141AA/json/2017/2018/esp](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2017/2018/esp) |
| Anual, un año | YYYY / mismo YYYY | [PM05141AA/json/2004/2004/esp](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM05141AA/json/2004/2004/esp) |
| Mensual | YYYY-M / YYYY-M | [PN01780AM/json/2006-12/2007-1/esp](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PN01780AM/json/2006-12/2007-1/esp) |

Una consulta anual de un año deberá usar, por ejemplo, `/json/2017/2017/esp` y validar `periods[].name == "2017"`. La guía y las muestras sustentan esa construcción. No se comprobó sintaxis trimestral, innecesaria para las series revisadas, y no se propone una.

### 5.1. Content-Type: defecto de integración confirmado

Las siete respuestas API obtenidas directamente enviaron:

```http
HTTP 200
Content-Type: text/html; charset=UTF-8
```

El cuerpo sí era JSON válido. El cliente actual solicita `Map.class` mediante RestTemplate sin adaptación específica. La configuración [AppConfig.java](../../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java), líneas **12–17**, solo define timeouts. Esto puede impedir seleccionar el convertidor Jackson, aunque el cuerpo sea correcto. No se ejecutó EcoInvest contra la API para afirmar el resultado exacto de esa excepción en la aplicación.

**Corrección mínima futura propuesta:** leer la respuesta como String en BcrpClient y deserializarla explícitamente con el ObjectMapper de Spring Boot ya disponible, validando JSON y estructura. No ampliar globalmente RestTemplate para aceptar cualquier HTML, porque también lo utiliza SENACE. Un cuerpo de página HTML debe producir error de formato, no un dato económico. No modificar Resilience4j para ocultar el problema.

No se comprobó si un header Accept diferente corrige la cabecera del servidor. La observación corresponde a los GET realizados.

## 6. Evaluación de indicadores para un año completo

### 6.1. Métrica recomendada

Para un dashboard con selector anual y campo `variacionSeleccionada`, se recomienda **variación porcentual interanual del PBI del sector, publicada directamente como serie anual por BCRP**. Es una recomendación de diseño apoyada en el catálogo, no una regla ya aprobada o implementada.

Usar una observación anual oficial evita fabricar promedios de índices, promedios de tasas mensuales, acumulaciones o elegir diciembre como si fuera el año completo. El indicador es contexto económico del sector; no mide inversión SENACE, impacto ambiental ni causalidad de los proyectos.

PM05141AA es anual y pertinente para hidrocarburos. Puede conservarse si la etiqueta elegida es crecimiento de producción de hidrocarburos. Para homogeneidad conceptual se recomienda la familia anual PBI de la siguiente sección. La coincidencia de valores de dos series en dos años no prueba equivalencia universal de metodología.

Los cinco códigos mensuales actuales no son apropiados como indicador anual sectorial en sus asignaciones actuales. Incluso corregida su etiqueta, enero no representa todo el año. No se propone agregar sus meses.

### 6.2. Alternativas oficiales verificadas

La [categoría anual](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/pbi-por-sectores) contiene una familia de PBI sectorial con unidad de variación porcentual interanual. Además se recuperaron dos años en JSON de estos seis códigos:

| Uso propuesto | Código | Componente oficial de esa familia | Frecuencia / unidad | Límite sectorial |
|---|---|---|---|---|
| Agricultura | PM04969AA | Agropecuario - Agrícola | Anual / % interanual | Agrícola; no confundir con todo Agropecuario |
| Pesca | PM04971AA | Pesca | Anual / % interanual | Correspondencia sectorial directa |
| Minería | PM04973AA | Minería e Hidrocarburos - Minería Metálica | Anual / % interanual | Cubre minería metálica, no toda actividad minera imaginable |
| Hidrocarburos | PM04974AA | Minería e Hidrocarburos - Hidrocarburos | Anual / % interanual | Alternativa homogénea dentro de PBI |
| Electricidad, referencia más amplia | PM04978AA | Electricidad y Agua | Anual / % interanual | Incluye agua; no equivale a electricidad pura ni a toda energía |
| TODOS, contexto nacional opcional | PM04983AA | PBI | Anual / % interanual | Nacional; no agregado calculado de sectores SENACE |

Fichas individuales: [PM04969AA](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM04969AA/html), [PM04971AA](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM04971AA/html), [PM04973AA](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM04973AA/html), [PM04974AA](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM04974AA/html), [PM04978AA](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM04978AA/html), [PM04983AA](https://estadisticas.bcrp.gob.pe/estadisticas/series/anuales/resultados/PM04983AA/html).

### 6.3. Valores anuales de contraste

Fuente: [GET de alternativas 2017–2018](https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PM04969AA-PM04971AA-PM04973AA-PM04974AA-PM04978AA-PM04983AA/json/2017/2018/esp). Valores tal como llegaron, sin redondear:

| Código | 2017 | 2018 |
|---|---:|---:|
| PM04969AA | 3.05113403435524 | 9.07731729270536 |
| PM04971AA | 4.66525619320596 | 47.6847546513897 |
| PM04973AA | 4.4758295916302 | -1.73797597703714 |
| PM04974AA | -2.44780196733593 | -0.0345569324875896 |
| PM04978AA | 1.13808221876177 | 4.39284781615264 |
| PM04983AA | 2.51883545370886 | 3.96935133550218 |

Son observaciones consultadas, no constantes para incorporar a producción. Pueden revisarse por el proveedor. Comparten frecuencia y tipo de tasa; eso permite compararlas como crecimiento anual de sus respectivos ámbitos, sin sumar sus porcentajes ni confundir la magnitud absoluta de sectores.

## 7. Series a conservar o sustituir

| Código actual | Decisión recomendada | Sustitución verificada / condición |
|---|---|---|
| PM05141AA | Económicamente pertinente; conservar solo con etiqueta correcta o sustituir para homogeneidad PBI | PM04974AA si se aprueba PBI anual como métrica común |
| PN01774AM | Retirar del mapeo de minería | PM04973AA si se acepta minería metálica como alcance explícito |
| PN01780AM | Retirar del mapeo electricidad/energía | PM04978AA solo como referencia Electricidad y Agua, con etiqueta y aprobación explícitas |
| PN01773AM | Retirar del mapeo de pesca | PM04971AA |
| PN01771AM | Retirar del mapeo de agricultura | PM04969AA para componente agrícola |
| PN01781AM | Retirar del mapeo de transportes | Sin alternativa específica verificada en esta consulta; devolver ausencia justificada hasta validarla |

No se afirma que BCRP carezca de una serie de transportes. No apareció una específica en la familia anual consultada; no se hizo una búsqueda exhaustiva. No sustituir por Otros Servicios, PBI total o una fruta para evitar un indicador vacío.

Para `ENERGIA`, no extender automáticamente la referencia de Electricidad y Agua a hidrocarburos y todas las formas de energía. El alias actual requiere decisión de producto; de no aprobarse un proxy explícito, usar ausencia por cobertura no soportada.

## 8. Comportamiento recomendado para TODOS

Eliminar la caída silenciosa en hidrocarburos. Dos comportamientos válidos requieren decisión de producto:

1. **Recomendación:** contexto macroeconómico nacional mediante PM04983AA, con `ambito=NACIONAL`, descripción PBI y etiqueta “PBI nacional: variación anual”. Es una serie verificada, no una media de proyectos o sectores filtrados.
2. Mantener el dashboard de proyectos para TODOS y omitir el indicador sectorial con motivo `SIN_SERIE_SECTORIAL`, si el producto exige estrictamente una coincidencia sectorial.

Nunca calcular promedio simple o suma de las tasas de sectores ni presentar PBI nacional como crecimiento de la cartera SENACE. La primera opción es una propuesta, no una decisión implementada.

## 9. Campos propuestos para indicadorBcrp

Objeto aditivo en DashboardResponse; mantener `fuentes.bcrp` como String y conservar `variacionSeleccionada` como número opcional compatible.

| Campo | Regla propuesta |
|---|---|
| codigoSerie | Código realmente consultado, del catálogo verificado |
| descripcion | Denominación económica oficial, sin renombrar índices como tasas |
| unidad | Para el catálogo anual propuesto: porcentaje de variación interanual |
| periodicidad | ANUAL para el lote recomendado |
| sectorSolicitado | Filtro recibido por EcoInvest |
| sectorEconomico | Ámbito real: por ejemplo Electricidad y Agua o Minería Metálica |
| ambito | SECTORIAL o NACIONAL, importante para TODOS |
| anioSolicitado | Año seleccionado, distinto de la prueba de período observado |
| periodo | Año efectivo validado contra periods[].name; ausente si no hay observación |
| valor | BigDecimal obtenido del String oficial; ausente cuando falte el dato |
| origen | API_DIRECTA_BCRP o NO_DISPONIBLE, coherente con fuentes.bcrp |
| motivo | Solo si corresponde: SIN_OBSERVACION, SECTOR_NO_SOPORTADO, ERROR_EXTERNO o FORMATO_INVALIDO |
| urlFuente | GET público sin credenciales, para trazabilidad de la observación |

`config.series[].name` permite verificar descripción; las respuestas observadas no incluyen un código o unidad en campos independientes. Esos metadatos deben venir del contexto de petición y catálogo validado, respaldado por ficha oficial. No inventar propiedades del JSON externo.

No exponer JSON íntegro sin necesidad. Mantener precisión recibida en backend y dejar el redondeo visual para una regla documentada. La ausencia continúa por omisión de valor y variación, no por cero. El período solicitado puede comunicarse aun cuando no exista observación, pero no etiquetarse como período observado.

## 10. Plan mínimo de implementación posterior

**No ejecutado; requiere revisión y autorización.**

1. Aprobar la familia anual PBI y los alcances: minería metálica, agricultura, proxy Electricidad y Agua, alias ENERGIA y comportamiento TODOS. Transportes permanece sin dato hasta validar alternativa.
2. Modificar BcrpClient para resolver solo códigos aprobados, sin default silencioso para desconocidos. Mantener las anotaciones y configuración Resilience4j sin cambios.
3. Consultar una serie por GET: `/json/{anio}/{anio}/esp`. Leer String y parsear JSON con el ObjectMapper existente para soportar el Content-Type observado, sin tocar el RestTemplate compartido o SENACE.
4. Validar una serie, una columna, período igual al solicitado y valor decimal finito; ausencia explícita para lista vacía, valor inválido o sector no soportado. No seleccionar el último dato sin validar fecha.
5. Agregar el esquema indicadorBcrp en OpenAPI y propagarlo en el controlador preservando rutas, campos y lógica SENACE. Mantener la omisión del indicador ausente y no modificar SQLite.
6. Preparar pruebas con cuerpos reales pequeños aquí documentados: dos años, fuera de rango, lista vacía, Content-Type text/html con JSON válido, HTML real inválido, decimales y columnas inesperadas. No llamar APIs en tests unitarios.
7. Con autorización posterior, regenerar OpenAPI mediante Maven y ejecutar compilación/pruebas aisladas. No editar target/ manualmente ni instalar dependencias nuevas sin permiso.

### Archivos probables

- `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java`.
- `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java`, solo integración BCRP.
- `backend/src/main/resources/openapi.yaml`.
- `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java`.
- `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java`.
- Tipos auxiliares BCRP o fixtures únicamente si se autorizan expresamente.

No se necesita modificar POM, versiones, paquetes existentes, SENACE, AppConfig, Resilience4j, wrapper o configuración de base para aplicar esta estrategia mínima.

### Criterios de aceptación propuestos

- El año determina URL y período observado, sin años vecinos de reemplazo.
- Toda tasa tiene identidad, unidad, periodicidad y ámbito verificados.
- El backend procesa el JSON con Content-Type observado sin aceptar HTML arbitrario como dato.
- Los sectores desconocidos y transportes no caen en hidrocarburos.
- TODOS comunica explícitamente contexto nacional o ausencia sectorial.
- Las cuatro rutas y sus campos actuales se conservan; metadatos nuevos son aditivos.
- No se inventan tasas, agregaciones anuales o equivalencias sectoriales.
- Pruebas de precisión, ausencia, formato y regresión pasan tras autorización de ejecución.

## 11. Evidencias, riesgos y limitaciones

- Se confirmó existencia mediante fichas y JSON con observaciones en dos años distintos para cada código actual. No se verificó toda su historia o disponibilidad futura.
- El visor web pudo mostrar fichas oficiales, algunas con contenido cacheado; las muestras API sí se obtuvieron mediante HTTP directo en esta sesión.
- Se observaron siete GET exitosos pequeños. Los intentos fallidos de visor o sandbox no prueban caída del BCRP.
- El orden de las columnas puede diferir del orden solicitado, observado en las consultas mensuales. No se afirma una regla de ordenación universal del proveedor.
- Ausencia confirmada por omisión de filas o periods vacío en los límites examinados. No se verificaron todos los marcadores posibles de huecos internos.
- La cabecera text/html se observó en todas las muestras; no se probó cada combinación de headers o infraestructura intermedia. No se ejecutó RestTemplate contra producción en esta fase.
- Las alternativas fueron verificadas por catálogo, fichas y un GET de dos años; electricidad/agua, minería metálica y PBI nacional requieren etiquetas de alcance explícitas. Son proxies o decisiones de ámbito cuando corresponda, no equivalencias universales.
- No se validó una alternativa específica para transportes. Es preferible declarar ausencia antes que ampliar artificialmente el significado de otra serie.
- Los valores económicos pueden revisarse. Una fixture debe registrar origen y fecha; una prueba no debe exigir que el proveedor mantenga indefinidamente una cifra histórica exacta.
- Cambiar el mapeo y la métrica alterará números del dashboard de manera intencional. Debe documentarse como corrección económica, preservando el formato HTTP compatible.
- No se modificó ni se evaluó de nuevo la interceptación AOP; sus pendientes anteriores siguen separados de esta validación económica.
- El contenido fuente del repositorio conserva cambios de fases anteriores. No se revirtieron ni se añadieron modificaciones a esos archivos durante esta fase.

## RESULTADO PARA REVISIÓN CON CHATGPT

Las seis series actuales existen, pero **cinco están asignadas al sector incorrecto**: minería usa demanda interna, pesca usa PBI desestacionalizado, agricultura usa sectores primarios, electricidad usa mandarina y transportes usa naranja. PM05141AA sí mide variación anual de producción de hidrocarburos.

Se validaron alternativas anuales oficiales de PBI: PM04969AA agrícola, PM04971AA pesca, PM04973AA minería metálica, PM04974AA hidrocarburos, PM04978AA electricidad y agua y PM04983AA PBI nacional. No se verificó alternativa específica de transportes. Se recomienda un indicador de variación anual publicado directamente, sin agregar meses. TODOS puede usar PBI nacional claramente identificado, previa decisión de producto.

La API acepta rangos anuales YYYY/YYYY y devuelve años como Strings; para meses se comprobaron YYYY-M y nombres como Ene.2017. Un año sin datos devolvió periods vacío. Las respuestas JSON llegaron con Content-Type text/html y las consultas combinadas reordenaron columnas respecto de la petición. Por eso el lote mínimo debería consultar una sola serie, leer texto y validar JSON explícitamente, sin alterar el cliente compartido SENACE.

**Siguiente paso recomendado:** revisar y aprobar este catálogo y sus alcances, especialmente Electricidad y Agua, Minería Metálica, ENERGIA, TODOS y ausencia de transportes. Después autorizar un lote acotado en BcrpClient, controlador, OpenAPI y pruebas que agregue indicadorBcrp con código, unidad, año efectivo y procedencia. No se implementó ninguna corrección ni se ejecutó Maven en esta fase; se espera la revisión del reporte.
