# EcoInvest — Estado actualizado para análisis y continuidad en ChatGPT

**Fecha:** 2 de noviembre de 2026.  
**Repositorio:** `D:\Documentos\GitHub\Proyecto-SOA`.  
**Fase activa:** 3A.2 — Validación de acceso y estructura real SENACE.  
**Estado principal:** CarteraProyectos respondió con un array real de 2.567 elementos en una prueba local reportada por el usuario; ConsultorasAmbientales aún no tiene muestra verificada. El backend continúa devolviendo registros SENACE simulados. Fase 3B no iniciada.

Este reporte se creó por solicitud del usuario. No reemplaza ni modifica reportes históricos. Durante su preparación no se realizaron HTTP, Maven, pruebas, servidores, cambios productivos o lecturas de credenciales. La configuración local se comprobó por existencia y exclusión Git, sin mostrar su contenido.

## 1. Resumen ejecutivo

EcoInvest integra SENACE y BCRP con arquitectura SOA. El backend utiliza Java 17 y Spring Boot 3.2.4. El frontend previsto es React + TypeScript, pero frontend/ sigue vacío. No incorporar OEFA ni riesgo ESG.

BCRP tiene integración anual y trazable, con observaciones reales seleccionadas y resiliencia verificada en contexto aislado. Su Fase 2C está cerrada dentro de ese alcance. No hay motivo demostrado para repetirla o modificar BCRP/Resilience4j al continuar SENACE.

El avance reciente es una respuesta de datos SENACE obtenida por el usuario ejecutando el script temporal preparado por Codex. Se recibió completo el cuerpo HTTP de CarteraProyectos: 1.509.133 bytes, JSON interpretable, array raíz de 2.567 elementos. Se inspeccionó estructuralmente solo los dos primeros objetos. Esto no acredita cobertura completa de toda la cartera SENACE ni validez de todos los registros.

Ese resultado revela una incompatibilidad concreta: SenaceClient solicita Map.class mientras el recurso observado devuelve un array. Además, el cliente actual descarta la respuesta y construye datos fijos SIMULADO. No se ha corregido aún.

El usuario autorizó almacenar su credencial completa en un archivo local excluido de Git. Ya existe backend/.env.apis.local con las URLs externas y la clave SENACE. Sin embargo, **ni el script diagnóstico ni Spring Boot leen todavía ese archivo**. La siguiente decisión debe separar la adaptación del diagnóstico de la futura configuración productiva.

## 2. Fuentes y fuerza de la evidencia

| Fuente | Qué permite afirmar | Limitación |
|---|---|---|
| Código actual inspeccionado | Versiones declaradas, rutas, uso de Map y construcción SIMULADO | No equivale a ejecución nueva del backend |
| Reportes históricos y Surefire anteriores | Resultados BCRP y suites anteriores | Fechas, runtimes y alcances diferentes; no sumar como una única suite actual |
| Salida PowerShell compartida por el usuario | HTTP, tamaño, array y campos de CarteraProyectos en esa ejecución | Codex no capturó directamente ese cuerpo; no hay fixture o archivo de respuesta guardado |
| Conversación de continuidad | Autorizaciones, revisión del manual y cambios locales recientes | Algunos hallazgos todavía no están incorporados al reporte SENACE histórico |
| Git y archivos actuales | Script temporal, exclusión y archivo local existente | No se leyó el valor de la credencial durante esta auditoría |

No se debe afirmar que el BcrpClient o SenaceClient productivo ejecutaron la prueba PowerShell. Esta utilizó .NET HttpClient directamente.

Referencias principales:

- [Estado anterior de continuidad, 29/10](ESTADO_ACTUAL_ECOINVEST.md).
- [Índice por fases](README.md).
- [Cierre técnico BCRP](02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md).
- [Validación SENACE histórica](03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md).
- [Diagnóstico SENACE](03_Fase3_SENACE/REPORTE_FASE3_SENACE.md).

## 3. Arquitectura y estado del código

### Stack y estructura

| Elemento | Estado confirmado |
|---|---|
| Java | 17 en [pom.xml](../backend/pom.xml), línea 17; conservar Oracle JDK 17.0.12 en ejecuciones autorizadas |
| Spring Boot | 3.2.4, POM línea 8 |
| Spring Cloud | 2023.0.1, POM línea 18 |
| Maven | Wrapper, distribución 3.9.9 documentada y verificada previamente |
| OpenAPI Generator | 7.4.0, POM línea 110; genera interfaces y modelos, no editarlos en target/ |
| Contrato | [backend/src/main/resources/openapi.yaml](../backend/src/main/resources/openapi.yaml) |
| Código principal | backend/src/main/java/com/ecoinvest/soa/ |
| Frontend | Carpeta vacía; sin aplicación, package.json o api.ts |
| SQLite | Dependencias/configuración presentes; fallback persistente no implementado |

Flujo actual: consumidor → EcoInvestController → SenaceClient/BcrpClient → proveedores externos. No hay capa de servicio adicional verificada en el código actual.

Cuatro rutas a conservar:

```text
GET /api/dashboard?sector={sector}&anio={anio}
GET /api/estado-servicios
GET /api/consultoras
GET /api/senace/datastream?nombre={recurso}
```

El controlador implementa ApiApi generado desde OpenAPI. El dashboard conserva total, aprobados, proyectos, fuentes, variacionSeleccionada e indicadorBcrp. Proyectos y consultoras siguen definidos como objetos genéricos en el contrato, no como modelos oficiales SENACE verificados.

RestTemplate compartido: cinco segundos de conexión y lectura en [AppConfig.java](../backend/src/main/java/com/ecoinvest/soa/config/AppConfig.java). Configuración de puerto: 8081. No iniciar el contexto completo como diagnóstico: application.yml tiene datasource SQLite y ddl-auto=update.

### SENACE actual

En [SenaceClient.java](../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java):

- Línea 23: base predeterminada https://datosabiertos.senace.gob.pe/Api/datastreams.
- Líneas 105–109: añade /{recurso}/data.json y auth_key en query.
- Línea 115: usa getForObject(url, Map.class).
- Línea 61: proyectos se construyen con origen SIMULADO después de comprobar un mapa no vacío.
- Línea 84: consultoras también se construyen con origen SIMULADO.
- Líneas 134 y 140: fallbacks en memoria simulados, no SQLite.
- Líneas 170–269: listas fijas de proyectos y consultoras.

Los filtros de sector y año operan sobre proyectos fijos de 2017, no sobre una respuesta oficial. El genérico devuelve un mapa sin validar esquema. Las excepciones se sanitizan, pero no conservan categoría HTTP útil. Existe un default de credencial en el código histórico; no debe usarse para esta continuación ni reproducirse.

En [EcoInvestController.java](../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java), líneas 45–57, total y aprobados se calculan desde la lista y el origen SENACE se toma de la primera fila; una lista vacía se etiqueta SIMULADO. Antes de integrar datos reales debe decidirse cómo representar consulta vacía, error y cobertura parcial independientemente de las filas.

### Persistencia y frontend

application.yml declara jdbc:sqlite:ecoinvest_fallback.db y ddl-auto=update. No se han verificado entidades, repositorios o fallback persistente implementado. El archivo backend/ecoinvest.db observado anteriormente tenía cero bytes y no es el nombre configurado; no se abrió. El monitor comunica NO_IMPLEMENTADO como respaldo local.

El frontend sigue pendiente. Los módulos previstos son Dashboard Analítico, Explorador Geográfico, Registro de Consultoras Ambientales y Monitor de Salud SOA. No declarar ninguno operativo end-to-end.

## 4. Fases completadas y pendientes

| Fase | Estado y evidencia | Qué no repetir o qué falta |
|---|---|---|
| 1 / estabilización inicial | Lote documentado con compilación y 18 pruebas previas satisfactorias | No reabrir defectos corregidos sin evidencia nueva |
| 2A BCRP | Catálogo oficial anual verificado | No volver a códigos iniciales incorrectos |
| 2B BCRP | Integración anual implementada, 75 pruebas locales previas | La suite histórica completa usó runtime 20.0.2 con objetivo Java 17; no afirmar que esas 75 se ejecutaron en 17 |
| 2C BCRP | Cerrada en alcance específico: consultas reales y AOP aislado con JDK 17.0.12 | Dashboard integral y disponibilidad permanente no certificados |
| Organización documental | Once reportes movidos, setenta enlaces ajustados, índice creado | Conservar rutas actuales y evidencia histórica |
| 3A documental SENACE | Portal/ayuda y texto del manual revisados | No repetir descargas sin pregunta nueva; capturas no examinadas visualmente |
| 3A.2 CarteraProyectos | Usuario reportó cuerpo HTTP completo y array real; estructura de dos objetos conocida | Falta perfil de todos los registros y reglas de negocio |
| 3A.2 ConsultorasAmbientales | No hay muestra verificada | Solicitar aprobación de consulta y volumen antes de ejecutarla |
| Configuración local de APIs | Archivo ignorado creado con autorización | Script y backend aún no lo cargan |
| 3B implementación SENACE | No iniciada | Requiere muestras, mapeo aprobado y autorización de cambios |
| Frontend / end-to-end | Pendientes | Después de estabilizar backend real |

## 5. BCRP: conservar el avance existente

Catálogo implementado:

| Filtro | Serie | Alcance |
|---|---|---|
| AGRICULTURA | PM04969AA | Agrícola |
| PESCA | PM04971AA | Pesca |
| MINERIA / MINERÍA | PM04973AA | Minería metálica |
| HIDROCARBUROS | PM04974AA | Hidrocarburos |
| ELECTRICIDAD | PM04978AA | Electricidad y Agua |
| TODOS | PM04983AA | PBI nacional |

Consulta pública sin API key: /{codigo}/json/{anio}/{anio}/esp. Una serie por petición, cuerpo String, Jackson explícito, año exacto y BigDecimal. Transportes/Energía/desconocidos quedan sin indicador; no sustituir por otro sector.

Se observaron datos reales de Hidrocarburos 2017/2018 y de las otras cinco series para 2018. Un intento previo de Hidrocarburos 2018 devolvió HTML; se rechazó correctamente. No eludir protecciones ni prometer disponibilidad permanente.

Resilience4j se verificó con proxy Spring CGLIB, errores, fallback, apertura, rechazo y recuperación en contexto mínimo con HTTP simulado. Los retornos normales FORMATO_INVALIDO se cuentan actualmente como éxito del breaker; no modificar esa política de forma automática.

## 6. Documentación oficial SENACE y autenticación

Fuentes ya consultadas en las etapas anteriores:

- [Ayuda oficial API](https://datosabiertos.senace.gob.pe/Api/Help).
- [Manual oficial](https://datosabiertos.senace.gob.pe/Api/Manual/Guía-uso-Portal-datos-abiertos.pdf).

El manual obtenido fue HTTP 200, PDF de 1.635.367 bytes y 16 páginas, versión 1.0 del 19/08/2020. Las páginas 13–15 describen la solicitud de token web para uso de API y una vigencia documental de un año. La ayuda presenta auth_key en query. Eso respalda el token del formulario de API, no la equivalencia universal con cualquier JWT de sesión.

Las rutas publicadas y empleadas en el diagnóstico son:

```text
GET https://datosabiertos.senace.gob.pe/Api/datastreams/CarteraProyectos
GET https://datosabiertos.senace.gob.pe/Api/datastreams/ConsultorasAmbientales
```

La clave se añade en memoria como auth_key y nunca debe publicarse en una URL completa. No se documentó un encabezado Bearer adicional en el material inspeccionado. No probar automáticamente combinaciones de mecanismos.

La variante sin /data.json de proyectos ya respondió con datos estructurados en la prueba del usuario. La variante que usa el cliente productivo con /data.json no fue probada; no declarar que está rota solo por diferencia documental.

No se identificaron parámetros REST oficiales de paginación, filtros o límite en el texto revisado. Los controles de grilla del portal no demuestran esos parámetros. La consulta de proyectos fue una recepción completa de respuesta, no una muestra paginada solicitada al servidor.

## 7. Cronología de los diagnósticos recientes

Las siguientes son ejecuciones locales del usuario con scripts proporcionados por Codex. No se dispone de sus marcas temporales exactas; se registran en orden de conversación, sin inventar horas.

| Paso | Resultado compartido | Interpretación |
|---|---|---|
| Intento inicial PowerShell | ProtocolError; HTTP 400, application/json; cuerpo no leído | Hubo respuesta HTTP, no se demostró causa ni invalidez de clave |
| Script temporal, límite 16 KiB | HTTP 200; application/json; 16.384 bytes leídos; corte | Respuesta posiblemente parcial, no interpretada |
| Script, límite 256 KiB | HTTP 200; Content-Length 1.509.133; 262.144 bytes leídos; corte | Se detectó volumen mayor; no se interpretó JSON truncado |
| Script, límite 2 MiB | HTTP 200; 1.509.133 bytes declarados y recibidos; JSON interpretable | Cuerpo recibido completo dentro del límite |
| Estructura final | Array raíz con 2.567 elementos; dos objetos inspeccionados | Evidencia de forma real, no perfil exhaustivo ni cobertura global |

La prueba satisfactoria acredita que la solicitud enviada con la credencial obtuvo ese recurso; no demuestra que la credencial fuera obligatoria, que el servidor rechace peticiones sin ella o que tenga acceso a todos los recursos.

El fallo 400 inicial y los éxitos posteriores se conservan como observaciones distintas. No se determinó causalmente qué provocó el primer 400 y no corresponde atribuirlo a TLS o autenticación sin más evidencia.

## 8. Diccionario preliminar sustentado en CarteraProyectos

Fuente: salida saneada del script compartida por el usuario. Los dos primeros objetos mostraron estos mismos trece campos; no se inspeccionaron todos los registros ni todos sus valores.

| Campo oficial observado | Tipo del clasificador | Posible uso EcoInvest | Pendiente o decisión |
|---|---|---|---|
| ID | number-or-value | Candidato a id | Confirmar tipo numérico exacto, nulos, estabilidad y unicidad |
| TITULAR | string | Candidato a titular | No asumir identidad única ni formato uniforme |
| RUC | string | Candidato a ruc | Conservar como texto; validar nulos y formato sin convertir a número |
| TITULO_PROYECTO | string | Candidato a denominación/título | Confirmar diferencia con unidad e instrumento |
| UNIDAD_PROYECTO | string | Posible unidad o denominación adicional | No equiparar a expediente sin evidencia |
| TIPO | string | Clasificación por verificar | Obtener valores y significado oficial |
| ACTIVIDAD | string | Candidato a actividad/sector | No imponer equivalencias con filtros BCRP automáticamente |
| FECHA_INICIO | string | Fecha disponible | Perfil de formatos y semántica de negocio antes de derivar año |
| ESTADO | string | Estado disponible | Obtener catálogo antes de definir aprobado |
| DESCRIPCION | string | Descripción disponible | No publicar contenidos innecesarios en diagnósticos |
| LONGITUD | number-or-value | Coordenada candidata | Verificar tipo, nulos, rango, orden y CRS |
| LATITUD | number-or-value | Coordenada candidata | Verificar tipo, nulos, rango, orden y CRS |
| RESOLUCION | string | Resolución disponible | No usar como expediente sin regla justificada |

El clasificador agrupa tipos .NET de valor como number-or-value; no acredita por sí solo integer, decimal o precisión económica exacta. No se observaron nulos en la estructura de esos dos objetos, pero esto no prueba que no existan en el array completo.

**No observados en los dos objetos inspeccionados:** inversión, moneda, región explícita y un campo denominado expediente. No declararlos ausentes de todo el dataset sin perfil completo. No inventarlos ni derivarlos automáticamente de coordenadas, resolución o títulos.

**Derivaciones posibles, todavía no aprobadas:** anio a partir de FECHA_INICIO una vez validada su semántica; iniciales como presentación. El origen API_DIRECTA_SENACE sería metadato interno sustentado por consulta válida, no un campo de negocio observado.

**Completitud:** se recibió el cuerpo HTTP completo, pero no hay total externo contrastado ni evidencia de cobertura histórica/territorial del universo oficial. La raíz array no aporta por sí sola metadatos de paginación.

ConsultorasAmbientales continúa sin diccionario real. No transferir los campos fijos actuales del cliente como si fueran oficiales.

## 9. Configuración local de APIs: cambio más reciente

El usuario solicitó guardar la clave completa en un archivo del proyecto y autorizó su exclusión de Git. Se crearon/modificaron:

| Archivo | Cambio |
|---|---|
| backend/.env.apis.local | Archivo local con BCRP_BASE_URL, SENACE_BASE_URL y SENACE_AUTH_KEY |
| [backend/.gitignore](../backend/.gitignore), líneas 35–36 | Exclusión específica /.env.apis.local |

Se verificó que el archivo existe, que git check-ignore identifica la regla y que no aparece como registrado en Git. Este reporte no contiene su valor y no requiere compartir ese archivo con GPT.

La API pública BCRP probada no requiere clave; no se creó una API key ficticia BCRP. Se centralizaron las URLs públicas y la credencial SENACE bajo nombres diferenciados.

### Estado de carga: pendiente

- [Diagnostico-Senace.ps1](../Diagnostico-Senace.ps1), línea 4, todavía lee SENACE_AUTH_KEY únicamente del entorno del proceso.
- El script no lee backend/.env.apis.local todavía.
- Spring Boot no carga automáticamente un archivo .env; no se implementó importación o puente a ese archivo.
- El código Spring usa external.apis.senace-auth-key y las propiedades base-url. Los nombres BCRP_BASE_URL, SENACE_BASE_URL y SENACE_AUTH_KEY del archivo aún no están mapeados explícitamente a esas propiedades.
- Guardar la configuración no equivale a haber integrado o probado su lectura.

La limitación anterior era que la variable existía en algunas PowerShell del usuario pero no llegaba a las herramientas de esta conversación, incluso fuera del sandbox. Eso no prueba invalidez de la clave. Evitar repetir indefinidamente instrucciones de reinicio o pedir el token por chat.

No modificar el .env de la raíz: se observó que está registrado en Git. No se leyó su contenido ni se cambió durante este trabajo. En la nube, definir las mismas propiedades mediante configuración del servicio o secretos administrados; nunca asumir que el archivo local ignorado viajará en el despliegue.

## 10. Script temporal y estado de Git

Diagnostico-Senace.ps1 está en la raíz y sin seguimiento Git. Tiene:

- Un GET fijo a CarteraProyectos, sin /data.json.
- Credencial por variable de entorno; URL autenticada solo en memoria.
- HttpClient, cookies desactivadas, sin redirecciones automáticas ni reintentos del procedimiento.
- Límite actual de lectura 2 MiB y cancelación/timeout de 25 segundos.
- No parsea una respuesta que alcanza el límite sin haber observado fin de stream.
- Clasificación y esquema limitado a 80 nodos, profundidad cinco y dos elementos por array.
- Mensajes de error saneados, sin imprimir excepciones completas o valores de registros exitosos.

Su sintaxis fue validada localmente; la ejecución HTTP satisfactoria fue la del usuario. No es prueba Java ni implementación productiva. El archivo temporal sigue presente; decidir su conservación o eliminación después de documentar resultados.

Git conserva modificaciones anteriores en wrapper, clientes BCRP/SENACE, controlador, contrato y pruebas. Se sumó backend/.gitignore. documentacion/, el script temporal y la carpeta de tests de controlador aparecen sin seguimiento. No se hicieron commits. No revertir esos cambios ni tratarlos como nuevos de este reporte.

## 11. Pruebas anteriores y límites de validación

| Validación | Resultado histórico | Límite |
|---|---|---|
| Fase 1 | 18 pruebas satisfactorias, compilación/generación | REPORTE histórico |
| Fase 2B | 75 pruebas satisfactorias | Suite local con mocks/HTTP simulado; runtime 20.0.2, objetivo release 17 |
| BCRP HTTP 2018 | 1 prueba satisfactoria posterior a un fallo histórico HTML | JDK 17.0.12; observación puntual |
| BCRP AOP | 2 pruebas satisfactorias en ejecución final | Contexto mínimo y HTTP simulado |
| BCRP catálogo real | 6 casos satisfactorios: 5 datos y 1 inspección documental 302 | JDK 17.0.12; no es una nueva suite completa |
| SENACE CarteraProyectos | HTTP 200 y estructura real compartidos por usuario | PowerShell/.NET, no SenaceClient ni dashboard |

No se ejecutó Maven o pruebas Java durante el trabajo reciente de configuración y diagnóstico SENACE. No hay certificación del backend SENACE real, del frontend o del conjunto end-to-end. No sumar ejecuciones distintas como un total de pruebas actual.

## 12. Problemas confirmados y decisiones pendientes

1. **Array frente a Map:** la respuesta observada de proyectos es un array y el cliente exige Map.class. La futura corrección requiere pruebas con esa estructura; no implementarla durante investigación.
2. **Simulación persistente:** aun con un Map exitoso, el cliente seguiría devolviendo datos fijos. Hay que sustituir construcción fija por mapeo real en un lote autorizado, manteniendo procedencia honesta.
3. **Ruta productiva distinta:** /data.json se añade en el backend; la ruta sin ese sufijo funciona en la observación. Acordar el cambio mínimo sin probar combinaciones automáticamente.
4. **Configuración local no consumida:** archivo creado e ignorado, pero aún sin lector en el diagnóstico ni en Spring.
5. **Consultoras sin muestra:** no diseñar su DTO o equivalencias a partir de la lista fija.
6. **Semántica de negocio no verificada:** fecha anual, estados, sector, coordenadas y unicidad siguen pendientes.
7. **Volumen:** proyectos ya supera los límites iniciales; evitar descargas repetidas de 1,5 MB solo para preguntas que podrían resolverse en una ejecución agregada autorizada.
8. **Cobertura:** 2.567 elementos es conteo de la respuesta, no garantía del universo nacional ni una página documentada.
9. **Credencial local:** no incluir el archivo en Git, reportes, fixtures o contexto enviado a GPT. No publicar URLs autenticadas. La exclusión Git no cifra el archivo ni crea una política de despliegue.
10. **Documentación acumulativa pendiente:** los reportes SENACE y el estado del 29/10 no contienen estos resultados nuevos. Este reporte conserva esa diferencia; no borrar antecedentes.

## 13. Orden recomendado para continuar

### Paso A — Completar diagnóstico sin cambios productivos

Proponer y autorizar la adaptación exclusiva de Diagnostico-Senace.ps1 para leer el archivo local. Debe usar rutas fijas, parsear solo las claves necesarias sin ejecutar contenido del archivo, rechazar configuración ausente y nunca imprimir valores o URL autenticada. Mantener validación TLS normal y no tocar código Java.

La autorización ya utilizada cubría crear el archivo local y su exclusión, no una carga automática ni una nueva consulta HTTP. Solicitar la aprobación del cambio de script y de cualquier llamada nueva antes de ejecutarlos.

### Paso B — Consultoras y perfil agregado de proyectos

Presentar un presupuesto HTTP explícito antes de hacer solicitudes. Priorizar una consulta a ConsultorasAmbientales y, si es necesaria, una sola consulta adicional de proyectos que reúna todas las preguntas de perfil.

Para cada recurso acordar límite de bytes y riesgo de solicitar el conjunto completo, ya que no hay parámetros oficiales de límite confirmados. No aumentar límites automáticamente ni copiar cuerpos completos a archivos por comodidad.

Obtener estructura, campos, tipos, nulos, identificadores y metadatos. En proyectos, producir agregados seguros de formatos de fechas, conteos de estados/actividades, duplicados ID, nulos y rangos de coordenadas, sin publicar filas personales. La selección de fecha o la validez geográfica requiere además semántica oficial y decisiones de negocio, no solo rangos numéricos.

### Paso C — Documentación acumulativa

Solicitar autorización para actualizar [REPORTE_FASE3A_VALIDACION_SENACE.md](03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md) y [README.md](README.md), si corresponde. Conservar todos los intentos previos y separar evidencia comunicada por el usuario de ejecuciones directas de herramientas.

Definir un diccionario oficial por recurso con campos observados, derivados justificados, no observados y decisiones pendientes. Cerrar Fase 3A solo con el alcance realmente verificado o limitaciones expresas.

### Paso D — Proponer Fase 3B

Solo después de acordar muestras y reglas, preparar un lote pequeño para configuración segura local/nube, deserialización array, mapeo real, procedencia y pruebas. Mantener cuatro endpoints y contrato OpenAPI como fuente de verdad; los cambios aditivos o de campos ausentes deben acordarse.

Archivos probablemente involucrados, sujetos a autorización:

- backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java.
- Integración SENACE de backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java.
- backend/src/main/resources/application.yml, únicamente si se aprueba el puente de configuración local/nube.
- backend/src/main/resources/openapi.yaml.
- Tests SENACE y controlador bajo backend/src/test/.
- DTOs o fixtures nuevos solo después de autorización explícita.

No modificar BCRP, Resilience4j, SQLite o frontend como efecto colateral. No iniciar Spring Boot completo, Maven o pruebas sin su autorización.

### Criterios de aceptación del siguiente trabajo

- La configuración local se consume de manera explícita y verificable, sin valores en logs o código.
- Las consultas respetan recurso, presupuesto y volumen aprobados.
- Consultoras se describe desde una respuesta real, o se declara la limitación.
- Los datos no disponibles no se inventan y la respuesta parcial no se presenta como completa.
- El perfil y las reglas de proyectos están sustentados, sin forzar equivalencias de expediente, sector o estado.
- No se afirma operación end-to-end ni se inicia Fase 3B sin aprobación.

## 14. Contexto autocontenido para GPT y próximo prompt

EcoInvest es un proyecto SOA SENACE + BCRP en D:\Documentos\GitHub\Proyecto-SOA. Backend Java 17, Spring Boot 3.2.4, Maven Wrapper 3.9.9, OpenAPI Generator 7.4.0. Mantener Oracle JDK 17.0.12 en pruebas autorizadas. frontend/ está vacío; React + TypeScript es trabajo futuro. No hay OEFA ni ESG. SQLite está configurado pero no implementa fallback; no abrir la base o iniciar el contexto completo.

BCRP Fase 2C está cerrada en su alcance: catálogo anual real, año exacto, BigDecimal, metadatos, ausencia sin constantes y AOP/Resilience4j aislado. No repetir esas fases ni cambiar BCRP/Resilience4j. Dashboard end-to-end sigue pendiente.

La fase activa es 3A.2 SENACE. Ya se revisó ayuda y texto del manual oficial: auth_key para el token del formulario de API, rutas /Api/datastreams/CarteraProyectos y /Api/datastreams/ConsultorasAmbientales sin /data.json. No hay paginación REST confirmada. La variante /data.json que usa el cliente Java no está probada; no concluir invalidez automática.

El usuario ejecutó Diagnostico-Senace.ps1 y compartió una respuesta real CarteraProyectos: HTTP 200, application/json, Content-Length y bytes recibidos 1.509.133, JSON array de 2.567 elementos. Hubo cortes anteriores de 16 KiB y 256 KiB y se autorizó recibir hasta 2 MiB para completar el cuerpo. Solo dos objetos se perfilaron: ID numérico genérico; TITULAR, RUC, TITULO_PROYECTO, UNIDAD_PROYECTO, TIPO, ACTIVIDAD, FECHA_INICIO, ESTADO, DESCRIPCION y RESOLUCION strings; LONGITUD y LATITUD numéricos genéricos. No hay valores de negocio publicados, perfil global de nulos, formato de fecha, CRS, unicidad o cobertura total confirmados. Inversión, moneda, región y expediente no aparecieron en esos dos objetos; no inventarlos. ConsultorasAmbientales aún no tiene muestra.

Hallazgo productivo: SenaceClient sigue haciendo getForObject(..., Map.class), incompatible con el array observado; luego descarta datos y construye proyectos/consultoras SIMULADO. EcoInvestController cuenta esa lista, usa Aprobado literal y origen de primera fila. Fase 3B no ha comenzado. Mantener /api/dashboard, /api/estado-servicios, /api/consultoras y /api/senace/datastream.

Último cambio autorizado: backend/.env.apis.local contiene URLs BCRP/SENACE y credencial SENACE completa, excluido mediante backend/.gitignore. No compartir su contenido con GPT ni imprimirlo. BCRP público no requiere clave. El .env raíz está registrado en Git y no debe usarse para esta credencial. El nuevo archivo no es cargado todavía por el script o Spring Boot. El script sigue leyendo SENACE_AUTH_KEY del entorno; la variable no llegaba a las herramientas de esta conversación, aunque sí a la PowerShell del usuario. No seguir repitiendo instrucciones de reinicio: proponer un lector explícito del archivo local, con aprobación y sin cambiar producción.

GPT debería preparar un prompt acotado para completar Fase 3A.2: inspeccionar estos archivos sin secretos, solicitar autorización para adaptar solo el diagnóstico a la configuración local, presentar presupuesto de consultas para consultoras y perfil agregado de proyectos, pedir aprobación de volumen y HTTP, documentar diccionario real y después solicitar permiso de actualización acumulativa del reporte SENACE/índice. No autorizar Fase 3B implícitamente. La futura Fase 3B debe incluir configuración explícita local/nube, mapeo array y pruebas, después de acordar reglas.

Documentos exactos a solicitar a continuación: este reporte; documentacion/03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md; documentacion/03_Fase3_SENACE/REPORTE_FASE3_SENACE.md; documentacion/02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md; Diagnostico-Senace.ps1; SenaceClient.java con cualquier default de credencial oculto; EcoInvestController.java; openapi.yaml; application.yml sin secretos; pom.xml y tests SENACE/controlador si se prepara implementación. No solicitar el archivo local de credenciales.

Este reporte es la única creación de esta ejecución. No actualiza estados históricos por sobrescritura ni ejecuta automáticamente los siguientes pasos.
