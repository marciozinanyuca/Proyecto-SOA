# EcoInvest — Fase 2C: verificación aislada de Resilience4j

**Fecha del reporte:** 19 de octubre de 2026.  
**Ejecución documentada:** 18 de octubre de 2026, finalizada a las 21:25:27, hora de Perú.  
**Entorno:** Oracle JDK 17.0.12, Spring Boot 3.2.4, Maven 3.9.9.  
**Resultado final:** BUILD SUCCESS; dos pruebas, cero fallos, cero errores y cero omitidas.

## 1. Objetivo y alcance autorizado

Verificar que las llamadas a BcrpClient atraviesan un proxy real de Spring AOP y que el aspecto de Resilience4j ejecuta el fallback, registra errores y gobierna apertura, rechazo y recuperación del circuit breaker.

Se autorizó crear y eliminar una única prueba temporal y ejecutar Maven con respuestas HTTP simuladas. Este documento recibió autorización independiente después de comunicar los resultados.

No se modificó código productivo, SENACE, SQLite, OpenAPI, frontend, Resilience4j, versiones o dependencias. No se inició EcoInvest completo, se realizaron llamadas externas, instalaron herramientas ni realizaron commits.

La evidencia corresponde a un contexto Spring mínimo que utiliza las autoconfiguraciones disponibles y las propiedades reales del proyecto. No constituye una ejecución del contexto productivo completo ni una prueba end-to-end del dashboard.

## 2. Antecedentes revisados

- [REPORTE_IMPLEMENTACION_FASE2B.md](REPORTE_IMPLEMENTACION_FASE2B.md): 75 pruebas locales previas; interceptación AOP pendiente.
- [REPORTE_FASE2C_DIAGNOSTICO_HTTP.md](REPORTE_FASE2C_DIAGNOSTICO_HTTP.md): consultas reales y diagnóstico de contenido HTTP; no certificaba AOP.
- [REPORTE_FASE3_SENACE.md](../03_Fase3_SENACE/REPORTE_FASE3_SENACE.md): diagnóstico SENACE y estado histórico de BCRP.

El reporte SENACE conserva una observación anterior fallida de 2018. El reporte HTTP posterior documenta una consulta real satisfactoria de ese año. Ninguna de esas pruebas directas del cliente acreditaba por sí sola la interceptación mediante un bean Spring.

## 3. Diagnóstico del código y dependencias

### 3.1. Anotaciones y firmas

En [BcrpClient.java](../../backend/src/main/java/com/ecoinvest/soa/client/BcrpClient.java):

| Líneas | Evidencia |
|---|---|
| 20–21 | Clase administrable como componente Spring; no es final |
| 53–55 | `consultarBcrp()` usa `@CircuitBreaker(name = "bcrp", fallbackMethod = "fallbackBcrp")` |
| 58–60 | `obtenerDatosBcrp(String, Integer)` usa el mismo breaker y `fallbackBcrpPorSector` |
| 63–88 | El método privado ejecuta HTTP y procesa la respuesta |
| 73 | Errores HTTP y conexión pueden propagarse desde RestTemplate |
| 84–87 | JSON inválido se convierte en retorno normal con FORMATO_INVALIDO |
| 191–202 | Fallbacks públicos con retorno Map, argumentos originales y Throwable adicional |

Los dos métodos protegidos son públicos y no finales. Ambos llaman al método privado `ejecutarConsulta`; no dependen de una autoinvocación entre los dos métodos anotados. El método privado no necesita interceptación propia: la frontera protegida es la entrada pública a través del proxy.

La llamada del controlador se realiza sobre BcrpClient inyectado por constructor: [EcoInvestController.java, líneas 25–32 y 43](../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java). Esa estructura permite atravesar el proxy, aunque esta prueba no inició el controlador.

### 3.2. Dependencias efectivamente disponibles

El POM declara Spring Cloud CircuitBreaker Resilience4j en [pom.xml, líneas 35–38](../../backend/pom.xml). La inspección del classpath conservado por Surefire verificó artefactos transitivos:

| Componente | Versión observada |
|---|---|
| Spring AOP | 6.1.5 |
| AspectJ Weaver | 1.9.21 |
| Spring Cloud CircuitBreaker Resilience4j | 3.1.1 |
| Resilience4j Spring Boot 3 | 2.1.0 |
| Resilience4j Spring 6 | 2.1.0 |
| Resilience4j CircuitBreaker | 2.1.0 |

Se encontraron `CircuitBreakerAspect`, `CircuitBreakerAutoConfiguration` y las entradas de autoconfiguración de los JAR locales. No era correcto inferir ausencia de AOP únicamente porque el POM no incluyera directamente un starter AOP.

### 3.3. Configuración existente

[application.yml, líneas 22–25](../../backend/src/main/resources/application.yml):

```yaml
bcrp:
  slidingWindowSize: 5
  failureRateThreshold: 50
  waitDurationInOpenState: 10s
```

No se modificaron estas propiedades. Las propiedades no declaradas conservan los valores aplicados por las bibliotecas actuales.

## 4. Diseño y aislamiento de la prueba

Archivo fuente temporal autorizado:

```text
backend/src/test/java/com/ecoinvest/soa/client/BcrpClientResilience4jIT.java
```

Se utilizó `ApplicationContextRunner` con las autoconfiguraciones de Boot AOP y Resilience4j CircuitBreaker, sin registrar manualmente un aspecto para forzar el resultado.

El contexto contenía ObjectMapper, RestTemplate y BcrpClient como bean. Un `YamlPropertySourceLoader` cargó el application.yml existente, sin cambiar sus valores. No se habilitó component scanning de EcoInvest ni autoconfiguración general de la aplicación.

`MockRestServiceServer` se enlazó al RestTemplate antes de invocar al cliente. Las respuestas y errores fueron simulados en memoria; no se abrió un servidor HTTP ni se llamó a BCRP. La URL de destino construida por el cliente no implicó tráfico externo porque el transporte estaba sustituido por el servidor simulado.

Se comprobó la ausencia de beans DataSource y SenaceClient. No se habilitaron JPA, repositorios o SQLite.

Los datos económicos de las respuestas exitosas se utilizaron como fixture de prueba; el origen API_DIRECTA_BCRP devuelto dentro de este contexto simulado es el comportamiento del cliente bajo prueba y no acredita una observación externa nueva.

## 5. Comandos y entorno

Desde `backend/`, en un proceso temporal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd -o '-Dtest=BcrpClientResilience4jIT' test
```

Se ejecutó con acceso autorizado a la caché Maven fuera del sandbox. El modo offline evitó descargas de dependencias. Se confirmó Java 17.0.12 tanto mediante los comandos iniciales como en las propiedades de la JVM de Surefire.

Maven regeneró artefactos OpenAPI y compiló fuentes y pruebas en target/. No se editaron manualmente archivos generados. La selección Java se limitó al proceso de ejecución; no se cambió permanentemente la configuración del sistema.

## 6. Primera ejecución y corrección de la prueba

La primera ejecución compiló y verificó la presencia del proxy y los fallbacks, pero terminó con dos pruebas, un fallo y cero errores. El fallo fue una expectativa incorrecta del código temporal:

```text
expected: 100
 but was: 5
```

El objeto de configuración exponía `minimumNumberOfCalls=100`, mientras la ventana COUNT_BASED tenía tamaño cinco. En esta combinación, el umbral efectivo de llamadas se limita por el tamaño de la ventana: tras cinco fallos el breaker ya estaba OPEN. Las llamadas siguientes fueron rechazadas, por lo que no podían incrementar el contador de fallos HTTP como asumía la prueba.

Se corrigió únicamente la prueba temporal para calcular, en una ventana de conteo, el mínimo entre `minimumNumberOfCalls` y `slidingWindowSize`. No se alteró ninguna propiedad productiva para hacer pasar la prueba.

La segunda ejecución completó apertura y recuperación correctamente. Los informes Surefire actuales corresponden a esa ejecución final; el fallo inicial quedó comunicado durante la sesión y se documenta aquí por transparencia.

## 7. Evidencias de interceptación

### 7.1. Proxy y aspecto

La prueba comprobó `AopUtils.isAopProxy(client)` y encontró un único CircuitBreakerAspect. El bean observado fue:

```text
com.ecoinvest.soa.client.BcrpClient$$SpringCGLIB$$0
```

Sus advisors incluyeron el advice `CircuitBreakerAspect.circuitBreakerAroundAdvice` y el pointcut `matchAnnotatedClassOrMethod(circuitBreakerAnnotation)`.

No se invocaron los fallbacks directamente para demostrar protección: se llamó a los métodos públicos del bean obtenido del contexto.

### 7.2. Configuración observada y aplicada

| Propiedad | Valor |
|---|---|
| slidingWindowSize | 5 |
| failureRateThreshold | 50 % |
| Espera en OPEN | 10.000 ms |
| minimumNumberOfCalls expuesto | 100 |
| Mínimo efectivo en esta ventana de conteo | 5 |
| permittedNumberOfCallsInHalfOpenState | 10 |
| automaticTransitionFromOpenToHalfOpenEnabled | false |

Los tres valores declarados en YAML se verificaron mediante aserciones sobre la configuración del breaker. La apertura efectiva también quedó probada, sin asumir que el valor expuesto de llamadas mínimas operaba independientemente de la ventana.

### 7.3. Errores y fallback

Se programaron cinco errores alternando HTTP 500 y una IOException simulada. La primera invocación utilizó `consultarBcrp()`; las siguientes, `obtenerDatosBcrp("Hidrocarburos", 2018)`. Así se ejercitaron ambas anotaciones y sus respectivas firmas de fallback.

Los resultados de fallback cumplieron:

```text
origen = NO_DISPONIBLE
motivo = ERROR_EXTERNO
valor = null
variacionSeleccionada = null
periodo = null
```

El breaker registró cinco errores y cambió de CLOSED a OPEN. El fallback no impidió que la excepción se contabilizara en el mecanismo de protección.

### 7.4. Rechazo en OPEN

Una nueva llamada por el proxy devolvió ausencia mediante fallback y produjo un evento NOT_PERMITTED. El contador de llamadas no permitidas fue uno.

La verificación de expectativas del MockRestServiceServer confirmó que esa llamada no consumió otra respuesta HTTP. Es una evidencia de cortocircuito real, no únicamente de la ejecución manual de un método fallback.

### 7.5. Recuperación según política

Se esperaron 10.200 ms, sin modificar el tiempo configurado ni forzar manualmente el estado. La siguiente llamada permitió pasar de OPEN a HALF_OPEN. Como la transición automática estaba desactivada, esperar por sí solo no se presentó como evidencia de transición: la llamada posterior fue la que inició la evaluación.

Se suministraron diez respuestas económicas exitosas simuladas, conforme al número de permisos de HALF_OPEN. Tras la primera se comprobó HALF_OPEN; al completar las diez se comprobó CLOSED.

Secuencia de eventos registrada:

```text
ERROR × 5
FAILURE_RATE_EXCEEDED
STATE_TRANSITION
NOT_PERMITTED
STATE_TRANSITION
SUCCESS × 10
STATE_TRANSITION
```

Las aserciones de estado, los contadores y la secuencia corroboran CLOSED → OPEN → HALF_OPEN → CLOSED.

## 8. HTML inválido y semántica del breaker

La segunda prueba creó un contexto independiente y devolvió HTML auténtico mediante HTTP simulado. BcrpClient devolvió FORMATO_INVALIDO, sin lanzar una excepción al aspecto.

Resultado observado:

```text
failedCalls = 0
successfulCalls = 1
```

Esto demuestra una distinción importante: un retorno normal puede comunicar ausencia económica y, a la vez, contabilizarse como ejecución exitosa del circuit breaker. No significa que el HTML haya sido aceptado como información económica.

No se identifica este comportamiento como un defecto de configuración por sí mismo. Si se desea contabilizar indisponibilidad semántica como fallo, debe acordarse una política separada y analizar su impacto sobre respuestas sin observación, sectores no soportados y formatos inválidos. No se implementó ese cambio.

## 9. Resultados finales y evidencia conservada

```text
BUILD SUCCESS
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Finished at: 2026-10-18T21:25:27-05:00
```

Casos ejecutados:

1. `proxyFallbackOpeningAndRecovery`: proxy, ambas anotaciones, errores, fallbacks, apertura, rechazo y recuperación.
2. `invalidHtmlIsNormalReturnNotBreakerError`: diferencia entre ausencia por formato y error contabilizado por el breaker.

Archivos de evidencia:

- [XML Surefire](../../backend/target/surefire-reports/TEST-com.ecoinvest.soa.client.BcrpClientResilience4jIT.xml), incluidas las entradas `AOP_EVIDENCE`.
- [Resumen Surefire](../../backend/target/surefire-reports/com.ecoinvest.soa.client.BcrpClientResilience4jIT.txt).

Estos artefactos son generados y pueden desaparecer con una limpieza de target/. No se repitieron las 75 pruebas previas ni se realizaron nuevas consultas oficiales durante esta verificación.

## 10. Limpieza y límites

Se eliminó el archivo fuente temporal y se verificó su ausencia. Los artefactos compilados y los informes generados permanecen bajo target/; no se editaron manualmente.

Se compararon hashes SHA-256 antes y después para los siete archivos encontrados bajo backend/src/main: SoaApplication.java, BcrpClient.java, SenaceClient.java, AppConfig.java, EcoInvestController.java, application.yml y openapi.yaml. Todos permanecieron iguales. Los cambios preexistentes del repositorio se conservaron.

La prueba acredita la interceptación del bean BcrpClient en un contexto mínimo con las autoconfiguraciones seleccionadas y las propiedades actuales. No verifica todas las condiciones, exclusiones o personalizaciones que pudieran existir al iniciar la aplicación completa. Tampoco certifica Resilience4j de SENACE, servidor HTTP, métricas del endpoint de salud o persistencia.

## 11. Conclusión y siguientes pasos

No se encontró un problema que justifique cambiar dependencias, anotaciones, firmas de fallback o configuración productiva para habilitar la interceptación probada. La discrepancia inicial correspondió al diseño de la prueba, no a una corrección pendiente de producción.

| Aspecto | Estado |
|---|---|
| Proxy y aspecto reales de BcrpClient en contexto mínimo | Verificados |
| Fallback por HTTP y conexión | Verificado |
| Ausencia sin cifras ficticias | Verificada |
| Apertura y rechazo sin HTTP | Verificados |
| Recuperación conforme a espera y permisos | Verificada |
| Tratamiento de HTML y conteo semántico | Verificado y documentado |
| Contexto productivo completo | No ejecutado |
| Integración end-to-end del dashboard | No verificada por esta prueba |

El pendiente específico de interceptación AOP de BcrpClient queda cubierto con este alcance aislado. Puede continuarse la validación oficial SENACE según su plan y autorizaciones. Si se decide conservar esta cobertura como prueba permanente, deberá autorizarse un archivo de pruebas mantenible en lugar del temporal eliminado; no requiere por sí mismo cambios productivos.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest mantiene Java 17 y Spring Boot 3.2.4. Se verificó con Oracle JDK 17.0.12 que BcrpClient registrado como bean Spring obtiene un proxy CGLIB con CircuitBreakerAspect. Usando autoconfiguraciones disponibles y el YAML actual, cinco errores HTTP/conexión simulados activaron los fallbacks de ambos métodos anotados y abrieron el breaker bcrp. La llamada en OPEN fue rechazada sin HTTP. Después de más de diez segundos, diez respuestas exitosas simuladas permitieron HALF_OPEN → CLOSED.

La configuración expone cien llamadas mínimas, pero su ventana COUNT_BASED de cinco limita el umbral efectivo a cinco. Una expectativa inicial incorrecta de la prueba fue corregida exclusivamente en el archivo temporal. La ejecución final tuvo dos pruebas satisfactorias. HTML inválido sigue devolviendo FORMATO_INVALIDO y se contabiliza como retorno normal exitoso para el breaker; cambiar esa semántica requiere una decisión independiente.

No se modificó código productivo ni configuración, no hubo HTTP real, SQLite o servidor completo. El archivo fuente temporal fue eliminado y los hashes productivos permanecieron iguales. La evidencia está en Surefire. El resultado verifica AOP en el contexto mínimo; no debe presentarse como certificación end-to-end de EcoInvest. No se propone una corrección productiva de Resilience4j basada en estos resultados.
