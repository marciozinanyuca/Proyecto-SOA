# EcoInvest — Fase 3A: validación oficial SENACE

**Fecha:** 19 de octubre de 2026.  
**Estado:** validación documental parcial; muestras de datos y mapeo oficial pendientes.  
**Alcance:** cuatro GET públicos sin credenciales, inspección local y planificación. No se implementaron cambios productivos.

## 1. Objetivo y restricciones

Verificar el portal oficial, las rutas y el mecanismo de autenticación para CarteraProyectos y ConsultorasAmbientales antes de sustituir los registros simulados de EcoInvest.

El usuario autorizó un máximo inicial de cuatro GET secuenciales y condicionales. Las consultas se consumieron en descubrir e inspeccionar documentación. No se enviaron credenciales ni se consultaron los endpoints de datos. La creación de este reporte recibió autorización independiente posterior a comunicar los resultados.

No se modificaron archivos productivos, BCRP, Resilience4j, SQLite, frontend, versiones o dependencias. No se inició Spring Boot, ejecutó Maven, instalaron herramientas ni realizaron commits. No se crearon archivos temporales para las consultas. No se desactivó TLS ni se eludieron controles del proveedor.

La inspección HTTP se realizó en memoria con PowerShell/.NET HttpClient, sin reintentos del procedimiento y con seguimiento automático de redirecciones desactivado. No fue una ejecución del cliente Java ni una nueva certificación de su TLS. Se mantiene la restricción de JDK 17.0.12 para futuras pruebas Java.

## 2. Antecedentes y diagnóstico local

Se revisaron los antecedentes de [REPORTE_FASE3_SENACE.md](REPORTE_FASE3_SENACE.md) y [REPORTE_CIERRE_FASE2C_BCRP.md](../02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md). BCRP permanece cerrado dentro de su alcance técnico específico y no requiere cambios para esta investigación.

### 2.1. Destino y autenticación del cliente actual

[SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java):

| Líneas | Hallazgo |
|---|---|
| 23–24 | Base predeterminada `https://datosabiertos.senace.gob.pe/Api/datastreams`, configurable mediante `external.apis.senace-base-url` |
| 26–27 | Propiedad de credencial con valor predeterminado; no se reproduce ni se utilizó ese valor |
| 105–109 | Construye `/{datastream}/data.json` y añade auth_key en la query |
| 113–119 | Obtiene Map mediante RestTemplate; sustituye excepciones externas por un mensaje fijo sin causa |

No se encontró sobrescritura de la base SENACE en application.yml. La configuración efectiva de un futuro proceso podría variar mediante otras fuentes; no se inició la aplicación para resolverlas.

La presencia, sin imprimir valores, se comprobó para cuatro variables del proceso: EXTERNAL_APIS_SENACE_BASE_URL, EXTERNAL_APIS_SENACE_AUTH_KEY, SENACE_TOKEN y SENACE_AUTH_KEY. Ninguna estaba definida. Esto no prueba ausencia de credenciales en otras fuentes. No se buscaron ni utilizaron secretos adicionales.

### 2.2. Registros simulados

| Método | Comportamiento confirmado |
|---|---|
| obtenerProyectos, líneas 52–62 | Realiza HTTP y exige mapa no vacío; después descarta su contenido, construye cuatro proyectos fijos y filtra localmente |
| obtenerConsultoras, líneas 75–84 | Realiza HTTP y exige mapa no vacío; después devuelve cuatro consultoras fijas |
| consultarDatastream, líneas 91–102 | Devuelve el mapa externo bajo data sin validar esquema |
| Fallbacks, líneas 127–150 | Proyectos y consultoras simulados en memoria; genérico NO_DISPONIBLE; sin persistencia implementada |

Los cuatro proyectos fijos pertenecen a 2017 y a Minería/Hidrocarburos. Sus importes, coordenadas y demás valores no se obtienen del cuerpo HTTP. Las cuatro consultoras también tienen campos fijos. Ambos tipos de registros se etiquetan SIMULADO.

Sector y año solo filtran proyectos simulados, mediante comparación textual de sector y entero de año: líneas 157–167. No se envían esos filtros al proveedor. No hay paginación implementada ni parser oficial de filas.

### 2.3. Dependencias del dashboard y contrato

[EcoInvestController.java, líneas 38–57](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java): el dashboard obtiene proyectos, calcula total con el tamaño de la lista y cuenta aprobados comparando el estado con Aprobado. Infiere origen desde el primer registro y declara SIMULADO cuando la lista está vacía.

Las líneas 111–120 exponen consultoras y datastream genérico. Una integración real necesitará distinguir consulta vacía, respuesta parcial, error y procedencia independientemente de la primera fila.

[openapi.yaml](../../backend/src/main/resources/openapi.yaml), líneas 47–79 y 96–102, define consultoras y proyectos mediante objetos genéricos y documenta el estado simulado actual; no fija un esquema oficial de campos SENACE.

[SenaceClientTest.java](../../backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java) utiliza mocks: verifica registros simulados tras HTTP exitoso, fallbacks, cuerpo genérico y sanitización de logs/excepciones. No acredita autenticación, estructura real o disponibilidad del proveedor.

## 3. Consultas oficiales realizadas

| Nº | URL pública sin credencial | HTTP | Content-Type | Resultado |
|---|---|---|---|---|
| 1 | `https://datosabiertos.senace.gob.pe/` | 302 | text/html; charset=UTF-8 | Destino /home/ en el mismo dominio |
| 2 | `https://datosabiertos.senace.gob.pe/home/` | 200 | text/html; charset=utf-8 | Portal identificado como PORTAL DATOS ABIERTOS SENACE |
| 3 | `https://datosabiertos.senace.gob.pe/home/CatalogoDatos/catalogo` | 200 | text/html; charset=utf-8 | Catálogo y enlace a /Api/Help |
| 4 | `https://datosabiertos.senace.gob.pe/Api/Help` | 200 | text/html; charset=utf-8 | Ejemplos REST y mecanismo auth_key |

El primer 302 no se siguió automáticamente: su destino se inspeccionó y la petición siguiente se hizo explícitamente, consumiendo otro GET del presupuesto. No se solicitó ningún destino ajeno a SENACE.

Se observaron respectivamente 164, 39.479, 93.318 y 43.778 caracteres de HTML. Son tamaños de las páginas, no cantidades de registros de los datasets.

Fuentes oficiales consultadas:

- [Portal de datos abiertos SENACE](https://datosabiertos.senace.gob.pe/home/).
- [Catálogo de datos SENACE](https://datosabiertos.senace.gob.pe/home/CatalogoDatos/catalogo).
- [Ayuda API SENACE](https://datosabiertos.senace.gob.pe/Api/Help).

El portal muestra enlaces institucionales SENACE y su catálogo enlaza la ayuda API bajo el mismo dominio. Las conexiones HTTPS se completaron con validación normal. Esto verifica la cadena de navegación documental observada; no constituye una auditoría de seguridad de la plataforma.

## 4. Endpoints documentados y discrepancia de rutas

La ayuda oficial presenta ejemplos equivalentes a:

```text
GET https://datosabiertos.senace.gob.pe/Api/datastreams/CarteraProyectos?auth_key={API_KEY}
GET https://datosabiertos.senace.gob.pe/Api/datastreams/ConsultorasAmbientales?auth_key={API_KEY}
```

Los marcadores anteriores son descriptivos y no contienen una credencial. No se ejecutaron esas URLs durante esta fase.

| Aspecto | Documentación observada | Código actual | Conclusión |
|---|---|---|---|
| Base | /Api/datastreams/ | Misma base predeterminada | Coincidencia documental |
| Proyectos | CarteraProyectos | CarteraProyectos/data.json | Diferencia de sufijo |
| Consultoras | ConsultorasAmbientales | ConsultorasAmbientales/data.json | Diferencia de sufijo |
| Autenticación | auth_key en query | auth_key en query | Mecanismo nominal coincidente; credencial vigente no verificada |
| Formato | JSON anunciado | Map esperado | Estructura JSON concreta no comprobada |

La página describe los nombres de recursos como GUID de la vista. Eso no demuestra que exista un identificador equivalente dentro de cada registro.

**Hallazgo confirmado:** los ejemplos oficiales examinados no incluyen /data.json y el cliente sí lo añade. **No se ha demostrado que /data.json sea inválido**, porque no se probó ninguna variante de datos. No debe corregirse la ruta automáticamente basándose solo en la diferencia documental.

La ayuda enumera también GastoEspecifica, GastoFuente, GastoGenerica, SolicitudAcceso, Visitas y Reclamos. Se mantiene la prioridad exclusiva de CarteraProyectos y ConsultorasAmbientales; no se probaron los otros recursos.

## 5. Autenticación: confirmado y pendiente

La ayuda oficial indica que la API requiere una key y utiliza auth_key en sus ejemplos. No aporta evidencia suficiente para determinar si el JWT del portal que posee el usuario puede emplearse como esa API key.

| Pregunta | Estado |
|---|---|
| ¿Se documenta auth_key? | Sí |
| ¿El JWT del usuario es intercambiable con esa clave? | No verificado |
| ¿Debe enviarse Authorization Bearer? | No confirmado por la página examinada |
| ¿La credencial del usuario está vigente y autorizada para ambos recursos? | No probado |
| ¿Existe caducidad, renovación, alcance o límite por credencial? | Pendiente |

No se debe inferir equivalencia JWT/API key a partir de su forma, longitud o nombre. No se probó el token como query o encabezado para descubrir empíricamente el mecanismo.

La ayuda enlaza un [manual oficial alojado por SENACE](https://datosabiertos.senace.gob.pe/Api/Manual/Guía-uso-Portal-datos-abiertos.pdf). No se descargó, porque ya se habían consumido los cuatro GET. También aparece un enlace a documentación externa de Junar, que no se consultó.

En una continuación autorizada debe revisarse primero el manual oficial y confirmar cómo obtener o reconocer la credencial apropiada. Si no está disponible en el entorno, pedir al usuario que la configure localmente mediante variable de entorno temporal, sin enviarla por la conversación.

Si auth_key en URL es el mecanismo aplicable, no deben registrarse URLs autenticadas ni mensajes de excepción que las incluyan. No registrar Authorization, cookies, Set-Cookie o cuerpos con secretos. No reenviar credenciales mediante redirecciones a otro dominio.

## 6. Formato, límites y muestras

La documentación anuncia JSON y menciona otros formatos posibles, pero no muestra en el contenido inspeccionado un esquema de datos suficiente para implementar un parser.

No se verificaron:

- Raíz objeto o array, envoltorios, encabezados/filas y nombres de campos.
- Tipos, nulos, codificación y convenciones de ausencia.
- Paginación, tamaño máximo de página o total de registros.
- Parámetros de filtro por sector, fecha o estado.
- Frecuencia de actualización, límites de peticiones o condiciones de acceso detalladas.
- Coordenadas, sistema de referencia, cobertura territorial o fechas de vigencia.

La ayuda describe acceso a la información de un conjunto. Sin un límite documentado, no corresponde ejecutar una descarga completa y denominarla muestra pequeña.

El catálogo presenta conjuntos de consultoras por actividad, entre ellas Agricultura-Riego, Construcción, Electricidad, Hidrocarburos, Minería, Residuos Sólidos, Salud, Saneamiento, Transportes y Vivienda. Esto acredita su presentación en el catálogo, no la estructura del endpoint ConsultorasAmbientales ni que este consolide todos esos conjuntos.

No se obtuvo ninguna muestra JSON de proyectos o consultoras en esta ejecución. No hay resultados de autenticación o errores de datos que puedan atribuirse a las APIs priorizadas.

## 7. Diccionario preliminar para proyectos

Las siguientes filas representan necesidades internas verificadas en el código. Ninguna equivalencia con un campo oficial está confirmada todavía.

| Necesidad EcoInvest | Campo interno actual | Estado oficial | Validación necesaria |
|---|---|---|---|
| Identificador | id | No verificado | ID oficial, estabilidad y unicidad por proyecto/trámite |
| Expediente | expediente | No verificado | Identificador y relación con proyecto |
| Titular | titular | No verificado | Denominación y posibles participantes |
| RUC | ruc | No verificado | Campo oficial, nulos y conservación como texto |
| Denominación | titulo, proyecto | No verificado | Diferenciar proyecto e instrumento/trámite |
| Sector | sector, actividad | No verificado | Catálogo y correspondencia entre ambos conceptos |
| Estado | estado | No verificado | Valores y significado de aprobación |
| Fecha de referencia | fechaInicio | No verificado | Fecha de ingreso, resolución u otra; no asumir equivalencia |
| Año | anio | Posible derivación pendiente | Derivar solo de una fecha con semántica acordada |
| Inversión | inversion | No verificado | Disponibilidad, moneda, unidad y período |
| Región | region | No verificado | Ubigeo, múltiples regiones y cobertura |
| Coordenadas | latitud, longitud | No verificado | Existencia, orden, CRS y ubicación representada |
| Iniciales | iniciales | Presentación derivable pendiente | No confundir con dato oficial |
| Procedencia | origen | Metadato interno | Transportar independientemente de la existencia de filas |

No se debe fabricar una inversión o coordenada para completar un mapa. La ausencia en una futura muestra tampoco demostrará automáticamente ausencia en todo el dataset.

## 8. Diccionario preliminar para consultoras

| Necesidad EcoInvest | Campo interno actual | Estado oficial | Validación necesaria |
|---|---|---|---|
| Razón social | razonSocial | No verificado | Denominación oficial y variantes |
| RUC | ruc | No verificado | Representación textual y nulos |
| Registro | registro | No verificado | Identificador y multiplicidad por especialidad |
| Especialidades/sectores | sectores | No verificado | Texto, array o registros relacionados |
| Nivel/clasificación | nivel | No verificado | Existencia y significado; no inferir desde especialidad |
| Estado | estado | No verificado | Catálogo, suspensión, cancelación u otros |
| Vigencia | Sin campo separado actual | No verificado | Fechas y reglas oficiales |
| Iniciales | iniciales | Presentación derivable pendiente | Regla de visualización si se necesita |
| Procedencia | origen | Metadato interno | Diferenciar datos reales, simulación, vacío y error |

El RUC por sí solo no debe asumirse como identificador único de una fila si una consultora tiene múltiples registros o especialidades.

## 9. Continuación propuesta para completar Fase 3A

No se ejecuta este plan mediante la autorización para documentar el reporte.

1. Solicitar autorización para consultar el manual oficial enlazado, sin credenciales y con tamaño acotado.
2. Resolver documentalmente autenticación, diferencias de rutas y mecanismos de muestra/paginación. Si el manual no lo aclara, documentar el límite y acordar la siguiente fuente oficial; no improvisar encabezados o parámetros.
3. Verificar disponibilidad de credencial temporal apropiada sin revelar su contenido. Si falta, pedir configuración local al usuario.
4. Proponer otro presupuesto explícito de solicitudes, priorizando una muestra acotada de cada recurso. No descargar automáticamente conjuntos completos.
5. Registrar estado, Content-Type, estructura, campos, tipos, nulos, fechas, identificadores, coordenadas y metadatos de completitud. No publicar secretos o cuerpos completos.
6. Actualizar el diccionario con evidencias por campo y separar disponible, derivado, no observado y no verificable.

Si se requieren herramientas auxiliares, pruebas temporales o Maven, deben solicitarse sus autorizaciones específicas. No hace falta iniciar EcoInvest completo o tocar SQLite para validar las muestras.

## 10. Propuesta condicionada para Fase 3B

La Fase 3B debe comenzar después de obtener muestras y acordar las reglas mínimas. Todavía no se autoriza implementar cambios.

| Área | Propuesta condicionada | Criterio de aceptación |
|---|---|---|
| Cliente | Ajustar ruta/autenticación solo con evidencia vigente | Solicitud correcta y credenciales sanitizadas |
| Deserialización | Mapear el esquema real, validando errores y formatos | No aceptar páginas HTML o mensajes de error como registros |
| Proyectos | Sustituir construcción fija por mapeo oficial | Campos y fechas trazables, sin inventar datos |
| Consultoras | Mapear identidad, registros y especialidades reales | Multiplicidad y vigencia respetadas |
| Filtros | Usar filtros documentados o filtrado local sobre cobertura explícita | No presentar una página como universo completo |
| Procedencia | Separar estado de consulta de filas | Vacío oficial distinto de error y simulación |
| Dashboard | Revisar conteos y regla de aprobado | Métricas coherentes con cobertura y semántica oficial |
| OpenAPI | Tipar los campos acordados sin eliminar rutas | Contrato coherente y generación automática |
| Pruebas | Fixtures pequeñas sanitizadas y transporte simulado | Casos de nulos, vacío, formatos, autenticación y paginación real documentada |

Archivos probablemente involucrados, sujetos a autorización posterior:

- `backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java`.
- Integración SENACE de `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java`.
- `backend/src/main/resources/openapi.yaml`.
- `backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java`.
- `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java`.

Cualquier DTO, fixture o auxiliar nuevo debe proponerse antes de crearlo. No se propone modificar BCRP, Resilience4j, SQLite, frontend o versiones.

## 11. Evaluación de avance y riesgos

| Aspecto | Resultado |
|---|---|
| Portal y navegación oficial | Verificados mediante cuatro GET |
| Rutas publicadas sin /data.json | Confirmadas documentalmente |
| Validez/invalidez de la variante /data.json | No probada |
| auth_key como mecanismo anunciado | Confirmado documentalmente |
| JWT del usuario como API key válida | No verificado |
| Muestras de ambos recursos | Pendientes |
| Esquema, campos y paginación | Pendientes |
| Fase 3A completa | No; cierre documental parcial |

Riesgos principales: enviar el tipo de token incorrecto, filtrar secretos mediante URLs o excepciones, usar rutas históricas, asumir tamaño pequeño, mezclar proyectos con expedientes, duplicar consultoras por especialidad, asignar fechas incorrectas al filtro anual y presentar un resultado parcial como completo.

La diferencia documental de ruta es un hallazgo que requiere verificación, no autorización para corregir producción. La existencia del portal y de la ayuda no demuestra que los endpoints de datos estén disponibles con la credencial del usuario.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest conserva BCRP cerrado en su alcance técnico y SENACE aún devuelve registros fijos SIMULADO para proyectos y consultoras. En Fase 3A se realizaron exactamente cuatro GET sin credenciales: raíz SENACE (302), home (200), catálogo (200) y ayuda API (200). No hubo Maven, pruebas Java, archivos temporales, cambios productivos o acceso a SQLite.

La ayuda oficial https://datosabiertos.senace.gob.pe/Api/Help publica /Api/datastreams/CarteraProyectos y /Api/datastreams/ConsultorasAmbientales con auth_key en query y anuncia JSON. Los ejemplos no incluyen /data.json, que sí añade el cliente actual. No se ha probado que esa variante sea inválida. No se confirmó que el JWT del portal equivalga a una API key ni que corresponda Authorization Bearer.

El presupuesto se consumió en navegación documental: no hay muestras JSON, autenticación probada, campos oficiales, paginación o límites confirmados. El diccionario del reporte enumera necesidades internas pendientes de contraste, sin inventar equivalencias. La Fase 3A permanece incompleta.

Siguiente tarea concreta: solicitar autorización para revisar el manual oficial enlazado en /Api/Help, resolver autenticación y acceso acotado y después proponer un nuevo presupuesto de muestras. Si se necesita credencial, pedir al usuario que configure una variable de entorno temporal local sin compartir el valor. Solo después de verificar datos y reglas corresponde autorizar Fase 3B en cliente, integración SENACE del controlador, contrato y pruebas. No modificar BCRP ni Resilience4j.


---

# ACTUALIZACIÓN ACUMULATIVA — 2 de noviembre de 2026

**Leer esta actualización para el estado vigente.** Las secciones anteriores se conservan como evidencia histórica de la investigación inicial; sus pendientes de acceso y muestras no describen por sí solos el avance posterior.

**Alcance autorizado de esta edición:** únicamente este reporte. No se actualiza documentacion/README.md, no se modifica el script o código productivo y no se realizan nuevas consultas para redactarlo.

## 12. Resumen ejecutivo actualizado

Se obtuvo y perfiló una respuesta completa de ambos recursos prioritarios SENACE mediante el script diagnóstico temporal, sin iniciar EcoInvest, ejecutar Maven o abrir SQLite:

| Recurso | HTTP | Content-Type | Bytes recibidos completos | Objetos | Campos |
|---|---|---|---:|---:|---:|
| CarteraProyectos | 200 | application/json | 1.509.133 | 2.567 | 13 |
| ConsultorasAmbientales | 200 | application/json | 4.785.774 | 15.015 | 8 |

Ambos cuerpos fueron JSON válido con raíz array y sin elementos no objeto. Los perfiles recorrieron todos los objetos recibidos y no omitieron campos por el límite de salida. No se guardaron cuerpos o filas en archivos; los resultados consisten en estadísticas saneadas de la ejecución y en este registro documental.

**Recepción completa no equivale a cobertura oficial completa.** No se dispone de total externo, metadatos de actualización o garantía de cobertura temporal/territorial. Los 15.015 registros de consultoras no son un conteo certificado de empresas únicas.

Hay evidencia suficiente para preparar un diseño de mapeo explícito y una propuesta pequeña de Fase 3B. No hay implementación Fase 3B ni certificación del cliente Java o dashboard end-to-end. Las decisiones semánticas y limitaciones siguientes deben resolverse o aceptarse de manera explícita.

## 13. Fuentes y origen de la evidencia nueva

Se aplicaron las skills de integración y seguridad; se consultaron también criterios de testing, documentación y arquitectura disponibles en .agents/skills/. Se respetaron AGENTS.md y las autorizaciones separadas para modificación del script, recurso y volumen.

| Evidencia | Origen | Qué acredita |
|---|---|---|
| Primer HTTP 400 y lecturas de proyectos a 16 KiB, 256 KiB y 2 MiB | Salidas PowerShell comunicadas por el usuario en la conversación | Historia de diagnóstico; no existe cuerpo persistido o captura directa del agente de esos primeros intentos |
| Perfil de consultoras con lectura de 2 MiB | Ejecución directa autorizada del script por las herramientas | HTTP 200, tamaño declarado mayor que el límite y corte seguro sin parsear |
| Perfil completo de consultoras a 6 MiB | Ejecución directa autorizada posterior | JSON válido, array, campos y agregados de los 15.015 objetos recibidos |
| Perfil completo de proyectos | Un GET directo adicional autorizado | JSON válido, array, tipos, nulos, duplicados y estadísticas de los 2.567 objetos |
| Exclusión y configuración del diagnóstico | Inspección local y modificación autorizada del script | Carga explícita del archivo local sin mostrar valores ni cambiar Java |

Las consultas directas completas se realizaron durante la sesión que se documenta el 02/11/2026. No se reconstruyen horas exactas no registradas en la salida. Los cuerpos no se conservaron; no pueden reanalizarse como si existiera un fixture local. Este reporte conserva sus resultados agregados, no un respaldo de los datasets.

Se buscaron candidatos de respuesta guardada .json, .jsonl o .ndjson dentro del repositorio, excluyendo ubicaciones sensibles o ajenas. No se encontró un cuerpo reutilizable de proyectos y por ello se propuso y autorizó una sola consulta adicional de perfil. No se accedió a archivos de credenciales para buscar muestras.

La lectura previa del manual oficial en memoria obtuvo HTTP 200 y PDF de 1.635.367 bytes, 16 páginas, versión 1.0 del 19/08/2020. Las páginas 13–15 describen el formulario de uso de API y su token web. No se examinó visualmente cada captura. La referencia histórica a vigencia de un año no acredita el estado actual de toda credencial.

## 14. Autenticación y endpoints efectivos

### 14.1. Rutas observadas

Se utilizaron únicamente:

~~~text
GET https://datosabiertos.senace.gob.pe/Api/datastreams/CarteraProyectos
GET https://datosabiertos.senace.gob.pe/Api/datastreams/ConsultorasAmbientales
~~~

La autenticación documentada auth_key se incorporó en memoria a la query. No se reproducen valores ni URLs autenticadas. No hubo Authorization Bearer, cookies del navegador, encabezados para simular Edge o variaciones automáticas de ruta.

La ayuda [oficial SENACE](https://datosabiertos.senace.gob.pe/Api/Help) publica esos recursos sin /data.json. Las consultas directas acreditan que esa variante respondió satisfactoriamente con la configuración utilizada.

La variante /data.json construida actualmente por SenaceClient no fue probada en estas ejecuciones: no corresponde declararla inválida sin evidencia. Sí existe una diferencia demostrada entre ruta usada con éxito y construcción productiva actual.

### 14.2. Alcance real de la verificación de credencial

La configuración utilizada permitió enviar las solicitudes y obtener JSON administrativo con HTTP 200. Esto es evidencia de acceso satisfactorio con la clave proporcionada. No demuestra:

- Que la API haga obligatoria la clave en todos los casos.
- Que rechace solicitudes sin ella o con otra clave.
- Que toda credencial JWT del portal equivalga a una API key.
- Que la clave tenga acceso a otros recursos o una vigencia determinada.
- Que el servicio sea permanentemente accesible.

No se hicieron peticiones negativas para probar claves incorrectas ni consultas sin clave para comparar controles de acceso.

### 14.3. Configuración local y carga efectiva

El archivo local backend/.env.apis.local permanece excluido por backend/.gitignore. El diagnóstico consume solo SENACE_BASE_URL y SENACE_AUTH_KEY como texto, sin ejecutar el archivo, modificarlo, imprimirlo o exportarlo al entorno. BCRP_BASE_URL no se utiliza en este diagnóstico.

La base es validada contra el dominio, esquema HTTPS y ruta aprobados. No se usa el default de credencial del cliente Java.

**Nuevo estado:** el diagnóstico ya carga explícitamente esa configuración. **Sin cambio productivo:** Spring Boot todavía no tiene un puente autorizado implementado para ese archivo o esos nombres de variables; no afirmar que el backend quedó configurado para local/nube por adaptar el script.

No se utilizó ni modificó el .env registrado en Git en la raíz.

## 15. Procedimiento y resultados HTTP acumulativos

### 15.1. Límites y seguridad de ejecución

El script [Diagnostico-Senace.ps1](../../Diagnostico-Senace.ps1) ofrece únicamente los dos recursos mediante selección restringida y exige -Consultar. Sin esa opción no lee configuración ni realiza HTTP.

Cada ejecución consulta un único recurso, con TLS/hostname normales, sin reintentos del procedimiento ni redirecciones automáticas, cookies desactivadas, streaming y cancelación HTTP de 25 segundos. El cálculo posterior en memoria tiene duración independiente de ese timeout; una ejecución larga de perfil no implica por sí sola una nueva solicitud o un timeout de red.

El límite se elevó explícitamente de 2 a 6 MiB después de observar el tamaño de consultoras. El valor actual del script es 6.291.456 bytes y también se aprobó expresamente para el GET posterior de proyectos. No se elevó automáticamente por fallo.

Un corte local no limita el volumen que el servidor genera. Se aprobó recibir el cuerpo completo observado, no una paginación documentada.

### 15.2. Historia preservada

| Paso | Resultado | Interpretación |
|---|---|---|
| CarteraProyectos inicial, usuario | HTTP 400; Content-Type JSON; cuerpo no inspeccionado | Causa indeterminada; no se probó que la clave fuera incorrecta |
| Proyectos, límite 16 KiB, usuario | HTTP 200; lectura cortada a 16.384 bytes | Parcial, no interpretada |
| Proyectos, límite 256 KiB, usuario | HTTP 200; 1.509.133 bytes declarados; corte a 262.144 | Parcial, no interpretada |
| Proyectos, límite 2 MiB, usuario | HTTP 200; 1.509.133 bytes recibidos; array de 2.567 | Primera estructura comunicada; dos objetos inspeccionados |
| Consultoras, límite 2 MiB, directo | HTTP 200; 4.785.774 bytes declarados; corte a 2.097.152 | PARCIAL_NO_INTERPRETADO |
| Consultoras, límite 6 MiB, directo | HTTP 200; 4.785.774 declarados y recibidos; JSON array | Cuerpo completo y perfil de todos sus objetos |
| Proyectos, límite 6 MiB, directo | HTTP 200; 1.509.133 declarados y recibidos; JSON array | Perfil agregado completo de la respuesta recibida |

Los comandos de las ejecuciones completas fueron:

~~~powershell
.\Diagnostico-Senace.ps1 -Recurso ConsultorasAmbientales -Consultar
.\Diagnostico-Senace.ps1 -Recurso CarteraProyectos -Consultar
~~~

Se ejecutaron por separado, cada uno bajo su autorización. No deben copiarse y repetirse como parte automática de una lectura documental.

El script validó JSON preservando la raíz array y produjo JSON_ARRAY_PERFILADO_NO_CERTIFICA_SEMANTICA. Los procesos terminaron con código 0; ese código por sí solo no basta para certificar los datos: la evidencia relevante son los metadatos y el perfil emitido.

No hubo Maven o pruebas Java durante estas consultas. Las primeras modificaciones del script fueron verificadas por análisis de sintaxis PowerShell, no por una suite automatizada de sus funciones. No se presenta el diagnóstico como código productivo probado exhaustivamente.

## 16. CarteraProyectos: esquema y calidad agregada

### 16.1. Forma y tipos

- Elementos recibidos: 2.567.
- Objetos: 2.567; elementos no objeto: 0.
- Campos diferentes no sensibles: 13; campos omitidos por límite: 0.
- Los trece campos están presentes en todos los objetos.
- Nulos JSON: 0 en todos los campos; porcentaje de nulos sobre objetos: 0 %.
- Tipos clasificados tras deserialización PowerShell: ID integer; LATITUD/LONGITUD decimal; los otros diez campos string. Esto no reemplaza una definición oficial de esquema o precisión.

Strings vacíos o en blanco: RUC 1 (0,0390 %); RESOLUCION 126 (4,9085 %); los otros once campos 0. Un string vacío no fue contado como null ni como campo faltante.

### 16.2. Identificadores y duplicados

| Campo | Vacíos/nulos | Valores distintos no vacíos | Grupos repetidos | Repeticiones adicionales |
|---|---:|---:|---:|---:|
| ID | 0 | 2.567 | 0 | 0 |
| RUC | 1 | 601 | 348 | 1.965 |

Se comparó igualdad exacta de tipo y representación. No hubo faltantes o valores no escalares para estos candidatos. ID es único **en esta respuesta**, no se ha demostrado su estabilidad entre actualizaciones o unicidad global.

El RUC repetido no es por sí mismo una inconsistencia: un titular puede tener varios proyectos o trámites. No usar RUC como clave única del registro ni deduplicar la cartera por empresa. No se publican los identificadores o valores concretos.

### 16.3. Distribución de ACTIVIDAD

| Valor observado | Registros |
|---|---:|
| Transportes | 781 |
| Minería | 745 |
| Agricultura | 354 |
| Electricidad | 299 |
| Hidrocarburos | 283 |
| Residuos Sólidos | 83 |
| Vivienda | 16 |
| Saneamiento | 5 |
| Residuos Solidos | 1 |
| **Total** | **2.567** |

Se detectó variación ortográfica con/sin tilde en Residuos Sólidos. No se normalizó ni fusionó silenciosamente. El agrupamiento del script aplica sanitización de etiquetas antes de mostrarlas; no hubo categorías omitidas en esta distribución.

La coincidencia nominal de una actividad con el filtro BCRP no prueba equivalencia económica. No asignar una serie a Residuos, Vivienda o Saneamiento sin catálogo aprobado. Transportes sigue sin serie BCRP específica aprobada.

### 16.4. Distribución de TIPO

| Valor observado | Registros |
|---|---:|
| ITS | 1.113 |
| Clasificación | 846 |
| PPC | 160 |
| Cls Anticipada | 101 |
| MEIA-d | 85 |
| IGAPRO | 74 |
| EIA-d | 48 |
| TdR | 48 |
| Actualización | 41 |
| MPPC | 29 |
| EIA-sd | 11 |
| IntegrAmbiente | 8 |
| MTdR | 3 |
| **Total** | **2.567** |

Las etiquetas observadas se conservan. No se expandieron acrónimos o equivalencias de instrumentos que no se verificaron oficialmente en esta fase.

### 16.5. Distribución de ESTADO

| Estado | Registros |
|---|---:|
| Aprobado | 1.568 |
| Otros | 697 |
| Desaprobado | 176 |
| En Evaluación | 126 |
| **Total** | **2.567** |

La etiqueta Aprobado existe y coincide textualmente con el criterio actual del controlador. Antes de convertir el conteo en una métrica de producto hay que acordar qué representa cada fila y cómo intervienen actividad, tipo, fecha y cobertura. No concluir que hay 1.568 proyectos únicos distintos por contar registros de cartera.

### 16.6. FECHA_INICIO

| Clasificación del perfil | Registros |
|---|---:|
| dd/MM/yyyy inequívoco entre los formatos comprobados | 1.629 |
| Ambigua entre dd/MM/yyyy y MM/dd/yyyy | 938 |
| **Total** | **2.567** |

Las 938 fechas ambiguas son compatibles con día/mes y mes/día por sus componentes; no se declararon erróneas. La muestra de fechas inequívocas aporta evidencia favorable a día/mes, pero no sustituye una confirmación contractual de formato para todas las filas.

El perfil comprobó un conjunto limitado de formatos. No se reportaron vacíos, nulos o formatos no reconocidos en esta respuesta. No se publican fechas de registros individuales. No se inspeccionó una distribución por año.

**Decisión pendiente:** significado de FECHA_INICIO y criterio del filtro anual. No asumir fecha de aprobación, resolución o inversión ni elegir una regla de parseo por cultura del equipo. Una propuesta de formato explícito debe indicar su fundamento y aprobarse.

### 16.7. Coordenadas

| Medida | LATITUD | LONGITUD |
|---|---:|---:|
| Mínimo observado | -18.20267292 | -81.30044157 |
| Máximo observado | -0.37840711 | -68.98847376 |
| Nulos/faltantes | 0 | 0 |
| Valores cero | 0 | 0 |
| Valores numéricos finitos dentro de rango global de grados | 2.567 | 2.567 |

Pares numéricos: 2.567; pares dentro de los rangos [-90,90] y [-180,180]: 2.567; pares (0,0): 0; pares con solo una coordenada numérica: 0; sin coordenadas numéricas: 0.

Esto verifica plausibilidad numérica si las unidades fueran grados. No confirma CRS, datum, precisión, punto representativo del proyecto, orden contractual o pertenencia a una geometría administrativa de Perú. No inferir región a partir de esos números sin un trabajo de georreferenciación explícitamente autorizado.

### 16.8. RESOLUCION y ausencias de campos

RESOLUCION presentó 2.441 strings clasificados como texto no publicado y 126 vacíos. No se analizaron los valores de texto para reconstruir expedientes, URLs o números de resolución individuales. No convertirla en expediente sin respaldo.

En la unión de campos de los 2.567 objetos no aparecieron campos explícitos de inversión, moneda, región o expediente. Esto se refiere a esta respuesta observada; no demuestra que la entidad no tenga esos datos en otro recurso. No rellenar los valores con constantes, cero o contenido simulado.

## 17. ConsultorasAmbientales: esquema y calidad agregada

### 17.1. Forma

- Elementos: 15.015, todos objetos.
- Campos diferentes: 8; sin campos omitidos por límite.
- Los ocho campos están presentes en todos los objetos y son string.
- Campos faltantes y nulos JSON: 0; porcentaje de nulos sobre objetos: 0 % en cada campo.
- La raíz array no aportó un envoltorio con paginación o total externo.
- Los 15.015 objetos no acreditan 15.015 consultoras únicas.

### 17.2. Nulos y vacíos

| Campo observado | Presencia | Nulos | Strings vacíos/en blanco | Porcentaje de vacíos sobre 15.015 |
|---|---:|---:|---:|---:|
| ACTIVIDAD | 15.015 | 0 | 0 | 0 % |
| FECHA_RD | 15.015 | 0 | 0 | 0 % |
| FIN_VIGENCIA_RD | 15.015 | 0 | 39 | 0,2597 % |
| INICIO_VIGENCIA_RD | 15.015 | 0 | 2 | 0,0133 % |
| RAZON_SOCIAL | 15.015 | 0 | 0 | 0 % |
| RESOLUCION | 15.015 | 0 | 1.588 | 10,5761 % |
| SUBSECTOR | 15.015 | 0 | 0 | 0 % |
| TIPO_SOLICITUD | 15.015 | 0 | 0 | 0 % |

Los porcentajes se calcularon desde los conteos agregados para esta documentación; no requirieron otra consulta.

### 17.3. Identidad, fechas y restricciones del perfil

No aparecieron RUC, ID, REGISTRO, NUMERO_REGISTRO, NRO_REGISTRO o CODIGO como campos. El script no encontró identificadores candidatos y no calculó duplicados de consultoras por una clave compuesta.

**Duplicados de consultoras: no verificados.** La ausencia de un candidato no equivale a ausencia de duplicados. No deduplicar por RAZON_SOCIAL ni asignar un RUC, registro o ID inventado. Puede haber filas por trámite, subsector, actividad o resolución, pero eso sigue siendo una hipótesis de granularidad.

FECHA_RD, INICIO_VIGENCIA_RD y FIN_VIGENCIA_RD son campos observados de tipo string. Sus nombres sugieren fechas de resolución/vigencia, pero el perfil no comprobó formatos, orden temporal, valores especiales o significado jurídico/administrativo. No etiquetar automáticamente una entidad como activa, vencida o vigente a partir del nombre del campo.

SUBSECTOR y ACTIVIDAD existen; no se obtuvo su distribución en esta ejecución. TIPO_SOLICITUD no debe convertirse en nivel, estado o especialidad sin evidencia semántica.

No aparecieron campos explícitos de nivel, estado, RUC o registro en el conjunto de ocho campos. La futura vista debe admitir ausencia o redefinir sus requisitos de forma transparente, sin copiar los valores fijos del prototipo.

## 18. Diccionario preliminar actualizado: proyectos

Todos los campos de esta tabla tienen presencia 2.567/2.567 y 0 % de nulos JSON. Se señalan strings vacíos aparte. Las correspondencias son propuestas, no cambios implementados.

| Campo oficial | Tipo observado | Vacíos | Posible campo EcoInvest | Transformación propuesta | Semántica/decisión y limitación |
|---|---|---:|---|---|---|
| ID | integer | 0 | id | Preservar identificador recibido | Único en la respuesta; estabilidad global pendiente |
| TITULAR | string | 0 | titular | Conservar texto oficial | No usar como identidad única |
| RUC | string | 1 | ruc opcional | Conservar texto; acordar vacío → ausencia | No convertir a número ni inventar RUC |
| TITULO_PROYECTO | string | 0 | titulo/proyecto | Correspondencia de denominación a acordar | Aclarar diferencia con UNIDAD_PROYECTO |
| UNIDAD_PROYECTO | string | 0 | unidadProyecto | Conservar como concepto separado | No equivale automáticamente a expediente |
| TIPO | string | 0 | tipoInstrumento/tipo | Preservar etiqueta; catálogo explícito si se aprueba | No expandir o agrupar acrónimos por suposición |
| ACTIVIDAD | string | 0 | actividad/sector de presentación | Conservar original; normalización explícita si se aprueba | No equivalencia automática con BCRP |
| FECHA_INICIO | string | 0 | fechaInicio; año derivado condicional | Parser con formato y semántica acordados | 938 fechas ambiguas día/mes; no año de aprobación demostrado |
| ESTADO | string | 0 | estado | Conservar catálogo observado | Métrica Aprobado sujeta a granularidad y filtros |
| DESCRIPCION | string | 0 | descripcion | Preservar si es necesario para el producto | No confundir con TITULO_PROYECTO |
| LONGITUD | decimal del parser | 0 | longitud | Mantener precisión; validar rango acordado | CRS y significado del punto pendientes |
| LATITUD | decimal del parser | 0 | latitud | Mantener precisión; validar rango acordado | Rango global no acredita ubicación correcta |
| RESOLUCION | string | 126 | resolucion opcional | Texto separado; acordar vacío → ausencia | No usar como expediente |

Campos internos sin fuente explícita en esta respuesta: expediente, inversión, moneda y región. Iniciales serían una presentación derivada si se aprueba, no un campo oficial. origen es metadato de consulta, no columna de negocio.

Si el contrato existente exige alguno de esos campos, debe decidirse si se omite, admite ausencia o se obtiene de otra fuente autorizada. No iniciar consultas adicionales para rellenarlos por defecto.

## 19. Diccionario preliminar actualizado: consultoras

Todos los campos tienen presencia 15.015/15.015 y 0 % de nulos JSON. Sus formatos y semántica de negocio no quedan certificados por ser strings.

| Campo oficial | Tipo | Vacíos | Posible campo EcoInvest | Transformación propuesta | Decisión/limitación |
|---|---|---:|---|---|---|
| RAZON_SOCIAL | string | 0 | razonSocial | Preservar texto | No identifica de forma única una empresa o una fila |
| ACTIVIDAD | string | 0 | actividad/especialidad candidata | Conservar separada hasta confirmar significado | No asumir que es lista de especialidades |
| SUBSECTOR | string | 0 | subsector | Mantener campo propio | No fusionar con ACTIVIDAD sin regla aprobada |
| TIPO_SOLICITUD | string | 0 | tipoSolicitud | Preservar denominación | No equivale a nivel o estado |
| RESOLUCION | string | 1.588 | resolucion opcional | Conservar texto; acordar vacío → ausencia | No equivale a número de registro sin evidencia |
| FECHA_RD | string | 0 | fechaResolucion candidata | Parseo solo después de verificar formato/semántica | El nombre no demuestra fecha exacta de negocio |
| INICIO_VIGENCIA_RD | string | 2 | inicioVigencia candidato | Preservar ausencia y validar formato | No calcular vigencia activa aún |
| FIN_VIGENCIA_RD | string | 39 | finVigencia candidato | Preservar ausencia y validar formato | Vacío no implica vigencia indefinida automáticamente |

RUC, registro, nivel y estado explícito no están disponibles en esta respuesta. No construirlos a partir de la lista simulada actual. Si se necesita un identificador técnico de UI, debe diferenciarse de un identificador oficial y acordarse su estabilidad; no introducirlo como dato SENACE.

## 20. Diferencias entre documentación, código y respuestas

| Aspecto | Estado actual y consecuencia |
|---|---|
| Ruta publicada | Sin /data.json, ya observada con éxito para ambos recursos |
| Ruta Java | Añade /data.json; requiere decisión de alineación, no se ensayó esa variante |
| Raíz JSON real | Array para ambos recursos |
| Deserialización Java | getForObject(url, Map.class), incompatible con la forma observada |
| Comportamiento después de HTTP | El cliente sigue construyendo listas SIMULADO |
| Campos de proyectos | Trece observados; inversión/región/expediente internos no tienen fuente explícita |
| Campos de consultoras | Ocho observados; RUC/registro/nivel/estado del mock no aparecen |
| Procedencia del controlador | Se infiere de primera fila y vacío SIMULADO; insuficiente para consulta oficial vacía o error |
| Conteos | Tamaño de la lista; no garantizan cantidad de proyectos o empresas únicos |
| Filtros anuales | Actualmente sobre registros fijos; la semántica de FECHA_INICIO real aún debe acordarse |
| Configuración | El diagnóstico carga archivo local; Spring Boot aún no tiene esa carga implementada |
| Paginación | No confirmada; no inventar page, limit o parámetros similares |

Evidencia de código: [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java), líneas 52–84, 105–119 y 170–269; [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), líneas 38–57 y 111–120.

## 21. Calidad, cobertura y evaluación del cierre

### Verificado dentro del alcance técnico

- Dominio y rutas oficiales empleados, con auth_key incorporado sin divulgarse.
- Dos recursos respondieron HTTP 200 y JSON válido con arrays de objetos.
- Cuerpos recibidos completos dentro del límite autorizado.
- Esquemas y tipos observados sobre todos los registros de ambas respuestas.
- Presencia, nulos y vacíos por campo.
- IDs únicos y RUC repetidos en proyectos, con interpretación limitada.
- Distribuciones de proyectos, formatos de fecha comprobados y calidad numérica de coordenadas.
- Diferencias demostrables frente al contrato interno/prototipos y deserialización actual.
- Lectura de configuración y sanitización del diagnóstico ejercitadas en consultas reales, sin probar todo escenario posible.

### No verificado o pendiente de decisión

1. Cobertura temporal/territorial, actualización y total oficial de cada recurso.
2. Necesidad estricta de autenticación y permisos de todos los recursos.
3. Variante /data.json, límites oficiales de uso o paginación.
4. Semántica temporal y formato contractual para fechas ambiguas.
5. Granularidad y clave estable de consultoras; duplicados por clave de negocio.
6. Formatos/relaciones de fechas de consultoras, distribuciones de sus actividades/subsectores y reglas de vigencia.
7. CRS, precisión y significado de las coordenadas.
8. Equivalencias de actividad, unidad/proyecto, resolución/expediente y métricas de aprobado.
9. Mapeo de campos ausentes hacia el contrato interno y requisitos de UI.
10. Integración Java con proveedor real, pruebas Java del nuevo mapeo y dashboard end-to-end.

**Evaluación:** se puede dar por completada la validación técnica acotada de acceso, forma y perfil descrito de ambos recursos. No procede declarar validación semántica exhaustiva o integración productiva SENACE completa. Fase 3A puede cerrarse en ese alcance solo dejando esas decisiones expresamente pendientes; no ocultarlas bajo un estado genérico COMPLETADO.

Este reporte permite preparar Fase 3B, pero no la autoriza. Si el siguiente lote necesita resolver una pregunta que requiere otra consulta, debe explicar por qué los agregados existentes no bastan y solicitar un presupuesto nuevo. No repetir descargas por rutina ni reabrir BCRP.

## 22. Plan técnico preliminar de Fase 3B — no ejecutado

### Primer paso: decisiones de contrato y negocio

Antes de implementar, acordar:

- Qué representa un registro de cartera y cómo se denomina su conteo.
- Uso de FECHA_INICIO para el filtro anio y formato explícito, sin llamarlo aprobación.
- Normalización de tildes/etiquetas preservando valor original y sin cambiar el catálogo económico BCRP.
- Campos reales disponibles de consultoras y si la vista acepta ausencia de RUC, registro, nivel y estado.
- Tratamiento de strings vacíos, datos ausentes, consulta vacía oficial, errores y cobertura.
- Identidad técnica de consultoras si el consumidor la requiere, sin fingir un ID oficial.
- Configuración local/nube explícita, eliminando dependencia de credenciales predeterminadas si se autoriza ese cambio.

No se propone inventar inversión, moneda, región, expedientes o vigencia. Esas decisiones no se resuelven por normalizar un nombre de campo.

### Lote pequeño posterior, sujeto a aprobación

| Área | Archivos probables | Cambio propuesto | Criterio de aceptación |
|---|---|---|---|
| Deserialización/ruta | SenaceClient.java | Consumir raíz array y ruta acordada; validar error/HTML/estructura | Respuestas verificadas se interpretan y errores no se presentan como filas |
| Proyectos | SenaceClient.java y DTO auxiliar solo si se aprueba | Mapear campos con precisión y ausencia, retirar construcción fija del camino real | Datos trazables; sin sustituciones por mock |
| Consultoras | SenaceClient.java y tipos aprobados | Exponer los ocho campos y ausencia de campos no oficiales | No inventar RUC/registro/nivel/estado |
| Orquestación | Integración SENACE de EcoInvestController.java | Procedencia y estado independientes de primera fila; conteos con significado aprobado | Vacío/error/real/simulado distinguibles |
| Contrato | backend/src/main/resources/openapi.yaml | Definir campos disponibles/ausentes y compatibilidad de las cuatro rutas | Generación posterior correcta; no edición de target/ |
| Configuración opcional | application.yml y/o lector explícitamente aprobado | Puente local/nube para propiedades necesarias, sin valores en código | Falta de clave y destino inválido tienen respuesta segura |
| Pruebas | SenaceClientTest.java, EcoInvestControllerTest.java y auxiliares autorizados | Arrays, nulos/vacíos, errores, filtros, procedencia y compatibilidad | Sin APIs reales ni SQLite; comandos Maven autorizados separadamente |

Rutas exactas de referencia:

- backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java.
- backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java, solo integración SENACE.
- backend/src/main/resources/openapi.yaml.
- backend/src/main/resources/application.yml, solo si se incluye expresamente.
- backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java.
- backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java.

Conservar OpenAPI como fuente, paquetes y versiones. No cambiar BCRP, Resilience4j, SQLite o frontend. No ejecutar Maven o iniciar Spring Boot bajo autorización documental. Una prueba HTTP del script no sustituye la compilación/pruebas del cliente Java futuro.

## 23. Alcance de esta actualización documental

Se añadió esta sección acumulativa manteniendo los bytes anteriores del reporte como prefijo, sin borrar el resultado histórico que indicaba muestras pendientes. Este nuevo bloque lo actualiza cronológicamente y explicita el alcance de cierre.

No se modifican documentacion/README.md, estados anteriores, script, configuración, Java, contrato o tests. No se guarda un fixture ni se crea un archivo con datos crudos. No se lee la credencial para redactar el documento. Las referencias al archivo local identifican su función, no su contenido.

Este reporte es el documento actualizado para compartir con ChatGPT. El índice puede seguir reflejando el estado previo hasta que se autorice su actualización; eso es una limitación documental conocida, no pérdida de los resultados nuevos.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest integra SENACE + BCRP, Java 17 / Oracle JDK 17.0.12, Spring Boot 3.2.4 y OpenAPI como contrato. BCRP Fase 2C permanece cerrada en su alcance específico; no repetirla o modificar Resilience4j para integrar SENACE. Frontend React/TypeScript sigue pendiente y SQLite no implementa respaldo real. No hay certificación end-to-end.

Se completaron consultas directas reales a CarteraProyectos y ConsultorasAmbientales mediante Diagnostico-Senace.ps1, cargando configuración local ignorada como texto, sin imprimir credenciales. Ambas rutas sin /data.json respondieron HTTP 200 y application/json. La clave enviada permitió obtener datos; no se demostró que fuera obligatoria o equivalente a cualquier JWT de sesión.

Proyectos: cuerpo completo de 1.509.133 bytes; 2.567 objetos; trece campos presentes en todos, sin nulos. ID integer y único en la respuesta; LATITUD/LONGITUD decimal; el resto string. RUC tiene un vacío, 601 valores distintos no vacíos y 348 grupos repetidos, no necesariamente errores. RESOLUCION tiene 126 vacíos. Hay nueve etiquetas ACTIVIDAD y trece TIPO. Estados: Aprobado 1.568, Otros 697, Desaprobado 176, En Evaluación 126. Fechas: 1.629 inequívocas dd/MM/yyyy entre formatos comprobados y 938 ambiguas día/mes; no se demostró año de aprobación. Los 2.567 pares de coordenadas son numéricos y plausibles en grados globales, sin validar CRS o geografía. Residuos Sólidos y Residuos Solidos aparecen separados. No hay campos explícitos de inversión, moneda, región o expediente en esta respuesta.

Consultoras: cuerpo completo de 4.785.774 bytes; 15.015 objetos; ocho campos string presentes en todos, sin nulos: ACTIVIDAD, FECHA_RD, FIN_VIGENCIA_RD, INICIO_VIGENCIA_RD, RAZON_SOCIAL, RESOLUCION, SUBSECTOR y TIPO_SOLICITUD. Vacíos: FIN_VIGENCIA_RD 39, INICIO_VIGENCIA_RD 2 y RESOLUCION 1.588. No aparecen RUC, ID, registro, nivel o estado explícitos. No llamar a los 15.015 registros consultoras únicas. Duplicados por clave de negocio y formatos/reglas de vigencia siguen sin verificar.

Los intentos parciales previos de 16 KiB, 256 KiB y 2 MiB se conservaron; no se interpretó JSON truncado. El límite actual 6 MiB y 25 segundos HTTP fue aprobado por recurso. No hubo reintentos, redirecciones, cuerpos guardados, Maven o ejecución Java en estas consultas. Cuerpo completo no significa universo completo; no hay paginación o total externo confirmados.

El backend sigue sin integrar estas respuestas: SenaceClient usa Map.class, añade /data.json y construye SIMULADO; EcoInvestController infiere fuente desde primera fila y cuenta la lista. La configuración local ya es leída por el diagnóstico, no por Spring Boot automáticamente. El script sigue siendo temporal y no reemplaza pruebas del backend.

Puede cerrarse la investigación técnica acotada de acceso/estructura/perfil y preparar Fase 3B, conservando decisiones semánticas explícitas. El siguiente prompt debe acordar fecha anual, granularidad, campos ausentes, identidad de consultoras, normalización y procedencia antes de autorizar un lote pequeño de mapeo array, contrato y pruebas. No inventar RUC, expediente, inversión, región o estado/vigencia ni avanzar a implementación automáticamente.

Solo este reporte fue actualizado con autorización; el índice no cambió. Comparte el documento completo con ChatGPT para que evalúe las decisiones y prepare el siguiente prompt de Codex.

