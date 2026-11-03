# Reporte de implementación — Fase 1A de EcoInvest

**Fecha:** 18 de octubre de 2026.  
**Proyecto:** EcoInvest, integración SENACE + BCRP.  
**Fase:** primer lote de estabilización del backend.  
**Estado actualizado:** corrección mínima del wrapper autorizada y aplicada; generación OpenAPI y compilación satisfactorias; **18 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas**.

## 1. Alcance y autorizaciones

Se contrastó `REPORTE_FASE1_ECOINVEST.md` con el código y se implementaron únicamente correcciones en los cuatro archivos productivos autorizados y pruebas bajo `backend/src/test/`.

El usuario autorizó posteriormente intentar compilación y pruebas en modo offline y crear este reporte en la raíz. Tras el bloqueo inicial, autorizó corregir mínimamente `backend/mvnw.cmd` y repetir los comandos offline. No se modificaron el POM ni la configuración productiva de SQLite.

No se cambiaron versiones de Java/Spring Boot o paquetes, no se modificó frontend, no se incorporaron OEFA o ESG y no se realizaron commits. No se inició la aplicación ni se abrió la base de datos mediante las operaciones realizadas. No se instalaron dependencias.

## 2. Cambios productivos

### 2.1. Seguridad SENACE

Archivo: [SenaceClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java).

- Los mensajes de consulta ya no incluyen URL autenticada ni parámetros libres: líneas **54, 77 y 95**.
- Se mantiene la construcción de URL y el envío de la credencial a la API; no se eliminó autenticación HTTP.
- El método privado `consultarDatos`, desde la línea **113**, captura `RestClientException` y genera un mensaje fijo sin conservar la causa externa, que podría contener la URL o el cuerpo de respuesta.
- Los fallbacks ya no registran `Throwable.getMessage()` ni la excepción externa.

**Tradeoff:** la excepción segura no conserva el subtipo HTTP ni la causa original. Esto evita propagar detalles sensibles, pero reduce información de diagnóstico. Una clasificación segura de errores por estado y categoría queda pendiente para un lote posterior.

### 2.2. Procedencia SENACE

En el mismo archivo:

- Proyectos fijos tras una respuesta HTTP no vacía: origen `SIMULADO`, línea **61**.
- Consultoras fijas tras una respuesta HTTP no vacía: origen `SIMULADO`, línea **84**.
- Fallbacks de proyectos y consultoras: `SIMULADO`, líneas **134 y 140**.
- Fallback del datastream genérico: `NO_DISPONIBLE`, línea **147**, con mensaje que declara ausencia de respaldo persistente.

Se conservaron registros, filtros y estructuras existentes. El parseo real de proyectos y consultoras SENACE no se implementó. El datastream genérico conserva el cuerpo externo bajo `data` cuando la consulta retorna.

### 2.3. Indicadores BCRP

Archivo: [BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java).

- Se eliminaron sustituciones silenciosas por `-2.45` y `3.53`.
- La extracción valida estructura de períodos y valores, admite representaciones numéricas y descarta números no finitos.
- Cuando no puede obtenerse el indicador, `variacionSeleccionada` es `null` en el mapa interno y `origen` es `NO_DISPONIBLE`: líneas **62–67**.
- El fallback no fabrica un valor ni un período: `valor`, `variacionSeleccionada` y `periodo` son `null`; `periods` es una lista vacía. No afirma uso de SQLite.
- Los mensajes de fallback no incluyen el mensaje de excepción externo.

No se modificó en este lote la selección temporal: la extracción todavía considera el último período de la respuesta. El año solicitado sigue siendo un problema pendiente.

### 2.4. Dashboard y monitor

Archivo: [EcoInvestController.java](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java).

- La variación comienza ausente, sin constante predeterminada, y solo se convierte a BigDecimal si es finita: líneas **49–52**.
- Si el indicador falta, `fuentes.bcrp` se establece en `NO_DISPONIBLE`: líneas **58–62**.
- En el cliente SENACE actual, una lista fija sin coincidencias conserva procedencia `SIMULADO`; no se atribuye a SQLite: líneas **55–57**.
- `respaldoLocal` comunica `NO_IMPLEMENTADO`: línea **112**.
- Consultoras y datastream se implementan mediante métodos de la interfaz OpenAPI esperada, sin mappings locales duplicados: líneas **91–99**.
- Consultoras utiliza `ResponseEntity<List<Object>>` para corresponder al esquema de lista de objetos libres; el JSON conserva su forma de array de objetos. La firma debe verificarse mediante generación y compilación.

### 2.5. OpenAPI

Archivo: [openapi.yaml](../../backend/src/main/resources/openapi.yaml).

Se conservan y documentan:

| Método | Ruta | Parámetros |
|---|---|---|
| GET | `/api/dashboard` | sector obligatorio; anio opcional, default 2017 |
| GET | `/api/estado-servicios` | Ninguno |
| GET | `/api/consultoras` | Ninguno |
| GET | `/api/senace/datastream` | nombre opcional, default CarteraProyectos |

Las nuevas operaciones tienen `operationId` compatibles con los métodos del controlador, desde las líneas **45 y 60**.

La representación elegida para ausencia del indicador en HTTP es **omitir `variacionSeleccionada`**, con `fuentes.bcrp=NO_DISPONIBLE`. El campo sigue siendo opcional y numérico; no se publica un objeto contenedor o un número ficticio. Internamente se usa `null`. Esta omisión se verificó con MockMvc tras regenerar los modelos.

Para generar la omisión se declaró `x-field-extra-annotation` con Jackson `JsonInclude.NON_NULL` en el contrato, línea **91**. La generación, compilación y las pruebas MVC resultaron satisfactorias. No se modificó manualmente ningún modelo generado.

Se documentan procedencia y ausencia de persistencia, líneas **92, 99 y 102**.

## 3. Archivos modificados o creados

| Archivo | Acción |
|---|---|
| `backend/src/main/resources/openapi.yaml` | Modificado |
| `backend/src/main/java/com/ecoinvest/soa/client/SenaceClient.java` | Modificado |
| `backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java` | Modificado |
| `backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java` | Modificado |
| `backend/src/test/java/com/ecoinvest/soa/client/SenaceClientTest.java` | Modificado |
| `backend/src/test/java/com/ecoinvest/soa/client/BcrpClientTest.java` | Modificado |
| `backend/src/test/java/com/ecoinvest/soa/config/AppConfigTest.java` | Modificado |
| `backend/src/test/java/com/ecoinvest/soa/SoaApplicationTests.java` | Modificado |
| `backend/src/test/java/com/ecoinvest/soa/controller/EcoInvestControllerTest.java` | Creado |
| `backend/mvnw.cmd` | Modificado con autorización adicional: manejo de Target nulo |
| `REPORTE_IMPLEMENTACION_FASE1A.md` | Creado con autorización posterior |

Los reportes anteriores `AUDITORIA_PROYECTO_SOA.md` y `REPORTE_FASE1_ECOINVEST.md` ya existían como archivos no versionados al comenzar este lote; no se modificaron.

## 4. Pruebas preparadas

Estas pruebas se ejecutaron después de corregir el wrapper y finalizaron satisfactoriamente.

| Archivo | Cobertura preparada |
|---|---|
| SenaceClientTest | Registros fijos y fallbacks SIMULADO; filtro vacío válido; datastream directo; URL de petición preservada con credencial ficticia; mensajes y logs sin credenciales |
| BcrpClientTest | Valor externo válido; ausencia en respuestas nulas, malformadas o vacías; valores inválidos y no finitos; fallback sin indicador o período sintético |
| EcoInvestControllerTest | Cuatro rutas mediante MockMvc standalone; ausencia real de propiedad JSON; valor finito preservado; NaN no convertido; procedencia y monitor |
| AppConfigTest | Construcción directa de RestTemplate sin contexto Spring |
| SoaApplicationTests | Carga de contexto con auto-configuraciones de datasource, Hibernate y repositorios JPA excluidas exclusivamente en el test |

Los tests de clientes instancian los clientes directamente con RestTemplate mockeado; no demuestran interceptación AOP. El test MVC no inicia un servidor real. El test de contexto pasó con las auto-configuraciones de persistencia excluidas. No se realizaron llamadas reales SENACE/BCRP ni operaciones sobre SQLite como parte de estas verificaciones.

## 5. Validaciones realizadas y resultados

### 5.1. Revisión estática

- Se revisaron diffs de archivos productivos y pruebas.
- `git diff --check` terminó sin errores de espacios; Git emitió avisos de normalización LF/CRLF en dos archivos de test.
- Se verificó que no permanecieran en el código productivo las constantes sustitutivas, etiquetas `FALLBACK_SQLITE` o afirmaciones `SQLite Activo`.
- Se revisó `git status --short`: los cambios de implementación se limitan al alcance autorizado.

Estas verificaciones no sustituyen compilación, generación o pruebas.

### 5.2. Compilación autorizada

Directorio: `D:\Documentos\GitHub\Proyecto-SOA\backend`.

```powershell
.\mvnw.cmd -o -DskipTests compile
```

**Resultado:** código de salida **1**. El wrapper falló antes de iniciar Maven:

```text
No se puede indizar en una matriz nula.
FullyQualifiedErrorId : NullArray,Microsoft.PowerShell.Commands.InvokeCommandCommand
Cannot start maven from wrapper
```

No se llegó a la generación OpenAPI o compilación Java.

### 5.3. Pruebas autorizadas

```powershell
.\mvnw.cmd -o test
```

**Resultado:** código de salida **1**, con el mismo fallo del wrapper. No se ejecutaron tests y no hay resultado de éxito o fallo de las aserciones.

### 5.4. Diagnóstico del bloqueo

La lectura de [backend/mvnw.cmd](../../backend/mvnw.cmd), líneas **92–95**, muestra indexación de `.Target[0]` al resolver la ubicación de `.m2`:

```powershell
if ((Get-Item $MAVEN_M2_PATH).Target[0] -eq $null) {
```

Esto es consistente con el error de matriz nula observado, pero no se obtuvo una traza interna que identifique inequívocamente esa línea. No debe confundirse este fallo de arranque del wrapper con un fallo del código Java.

No se encontró un comando `mvn` disponible mediante `Get-Command mvn` en esta terminal. No se descargó Maven ni se alteró el wrapper. Una corrección del wrapper requiere autorización adicional por estar fuera de los archivos autorizados.

### 5.5. Corrección autorizada del wrapper y resultados definitivos

La sección anterior conserva el diagnóstico inicial. Después el usuario autorizó modificar el wrapper y repetir los comandos. En `backend/mvnw.cmd`, líneas **91–96**, se materializó `Target` como array y se comprobó si está vacío antes de acceder al índice:

```powershell
$mavenM2Targets = @((Get-Item -LiteralPath $MAVEN_M2_PATH).Target)
if ($mavenM2Targets.Count -eq 0 -or [string]::IsNullOrEmpty($mavenM2Targets[0])) {
  $MAVEN_WRAPPER_DISTS = "$MAVEN_M2_PATH/wrapper/dists"
} else {
  $MAVEN_WRAPPER_DISTS = $mavenM2Targets[0] + "/wrapper/dists"
}
```

Se confirmó antes de ejecutar que la distribución configurada Maven 3.9.9 ya estaba en caché. No fue necesario descargarla. El primer intento posterior a la corrección encontró una restricción de acceso a `C:\.m2\repository`; se solicitó aprobación de ejecución fuera del sandbox y se repitió manteniendo modo offline.

| Comando desde backend/ | Resultado definitivo |
|---|---|
| `.\mvnw.cmd -o -DskipTests compile` | Código 0, BUILD SUCCESS; OpenAPI generado, 9 fuentes compiladas con release 17 |
| `.\mvnw.cmd -o test` | Código 0, BUILD SUCCESS; 18 pruebas, 0 fallos, 0 errores, 0 omitidas |

| Clase de prueba | Casos ejecutados | Fallos / errores |
|---|---:|---|
| BcrpClientTest | 8 | 0 / 0 |
| SenaceClientTest | 4 | 0 / 0 |
| AppConfigTest | 1 | 0 / 0 |
| EcoInvestControllerTest | 4 | 0 / 0 |
| SoaApplicationTests | 1 | 0 / 0 |

Los resultados Surefire están bajo `backend/target/surefire-reports/`. Los artefactos OpenAPI se generaron automáticamente bajo `target/`, sin edición manual. La ejecución confirmó Java 20.0.2 como runtime de los tests, con compilación objetivo release 17. No se realizó una ejecución separada sobre un JDK 17.

Quedó un aviso de operaciones no comprobadas en `BcrpClient`, asociado al uso de mapas sin tipado de la respuesta externa. No impidió compilar ni pasar las pruebas; su tratamiento puede coordinarse con DTOs en un lote posterior.

## 6. Riesgos y pendientes

1. **Generación y compilación verificadas:** nuevas firmas y modelos compilan; queda el aviso de operaciones no comprobadas en BcrpClient.
2. **Pruebas verificadas con alcance limitado:** 18 pasan y el contexto de test arranca sin persistencia. No certifican operación productiva o conexión a las APIs reales.
3. **Resilience4j:** interceptación y dependencias transitivas pendientes; los tests unitarios de fallback no la certifican.
4. **SENACE:** proyectos y consultoras siguen siendo fijos, ahora identificados como simulados. Falta adaptación real de respuestas.
5. **BCRP:** selección del año, periodicidad, unidades y pertinencia sectorial pendientes. Se conserva la selección del último período.
6. **Parámetros:** alias, acentos, sectores desconocidos, TODOS y validación de datastreams no fueron ampliados en este lote.
7. **Errores externos:** la sanitización SENACE reduce detalle diagnóstico. Falta una clasificación segura de errores HTTP y formatos.
8. **Persistencia:** no implementada. La configuración productiva de SQLite permanece sin cambios y un arranque normal todavía debe considerarse capaz de acceder a la base.
9. **Compatibilidad de consumidores:** ahora deben admitir indicador omitido y las procedencias SIMULADO/NO_DISPONIBLE. URLs, métodos y formas generales se preservan en el código y contrato, pendientes de verificación HTTP.
10. **Entorno Java:** el proyecto declara Java 17 y se compiló con release 17; los tests se ejecutaron con Java 20.0.2. No se migró el proyecto y falta una ejecución equivalente sobre JDK 17 si se requiere certificar ese runtime.

## 7. Criterios de aceptación y estado

| Criterio | Estado |
|---|---|
| Cambios limitados a archivos autorizados | Revisado estáticamente |
| Cuatro endpoints preservados en contrato y código | Verificado con generación y MockMvc |
| Registros fijos identificados SIMULADO | Verificado en pruebas unitarias |
| Sin constantes económicas sustitutivas | Revisado estáticamente |
| Ausencia del indicador expresada sin número ficticio | Verificado con generación y pruebas de serialización MVC |
| Sin afirmaciones falsas de SQLite | Revisado estáticamente |
| Logs propios SENACE sin URL autenticada ni mensaje externo | Verificado en prueba con credencial ficticia |
| Pruebas aisladas de SQLite y transporte externo | Ejecutadas con mocks, MVC standalone y exclusiones de persistencia |
| Compilación y tests satisfactorios | BUILD SUCCESS; 18 pruebas sin fallos ni errores |
| Sin cambios de versiones, frontend, base o archivos generados manualmente | Respetado por las operaciones realizadas |

## RESULTADO PARA REVISIÓN CON CHATGPT

Se aplicó el primer lote de estabilización EcoInvest dentro de los archivos autorizados. SENACE conserva sus peticiones HTTP y registros provisionales, ahora SIMULADO; los errores propios se sanitizan para no propagar URLs autenticadas. BCRP ya no inventa constantes en ausencia de datos y el dashboard omite el indicador ausente con fuente NO_DISPONIBLE. El monitor declara respaldo NO_IMPLEMENTADO. OpenAPI documenta las cuatro rutas y controla la omisión del campo mediante una anotación Jackson generada desde el contrato.

Se prepararon pruebas unitarias y MVC, y se excluyó persistencia del test de contexto. No se tocaron dependencias, versiones, paquetes, frontend ni SQLite.

**El fallo inicial del wrapper se resolvió con autorización adicional. La compilación offline y las 18 pruebas terminaron en BUILD SUCCESS. Esto confirma el lote dentro de la cobertura de pruebas, no el funcionamiento end-to-end con APIs externas reales.**

La siguiente tarea recomendada es abordar la integración real SENACE con respuestas sanitizadas y reglas verificables, y después la selección temporal BCRP. La verificación AOP permanece pendiente. No es necesario repetir esta suite mientras no haya cambios o nuevas razones de validación. No se instalaron dependencias ni se editó la base; solo se generaron artefactos de build y se ejecutó el contexto de test aislado.
