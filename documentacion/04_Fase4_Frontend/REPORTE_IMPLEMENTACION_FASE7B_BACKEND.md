# EcoInvest — Fase 4B, Lote 7B: perfil de integración local sin persistencia

Fecha: 07/11/2026, America/Lima.

**Estado: perfil creado y validado en contexto Spring de prueba con MVC en memoria.** Compilación offline satisfactoria y cinco pruebas aprobadas. No se inició servidor real, no se conectó React y no se consultaron proveedores.

## 1. Objetivo y estado inicial

Preparar un perfil opt-in `integracion-local` para la futura integración HTTP con React sin crear infraestructura SQLite/JPA. La configuración normal continúa declarando SQLite y `ddl-auto: update`; este lote no la modifica ni autoriza su arranque.

Antecedentes:

- [Auditoría 7A](REPORTE_AUDITORIA_INTEGRACION_FASE7A.md).
- [Cierre backend 3C.3](../03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md).
- [Último cierre frontend: Lote 6](REPORTE_IMPLEMENTACION_FASE4B_LOTE6.md).

La inspección Git detectó cambios previos sin seguimiento, incluidos frontend, reportes/evidencias y un archivo SQLite en la raíz. Se preservaron. No se abrió esa base, no se comparó su contenido ni se atribuye su existencia a este lote.

## 2. Archivos creados y alcance respetado

| Archivo | Finalidad |
|---|---|
| [application-integracion-local.yml](../../backend/src/main/resources/application-integracion-local.yml) | Perfil productivo opt-in con loopback y exclusiones de persistencia |
| [IntegracionLocalProfileTest.java](../../backend/src/test/java/com/ecoinvest/soa/config/IntegracionLocalProfileTest.java) | Carga del perfil real, guardas previas y validaciones aisladas |

Se crearon únicamente esos dos archivos antes de la autorización documental. La corrección durante las pruebas afectó exclusivamente al test nuevo. No se modificaron application.yml original, POM, OpenAPI, clientes, Resilience4j, CORS, frontend o SQLite. No se instaló software ni se realizaron commits.

Maven escribió artefactos generados en target/ como efecto autorizado de compilación y pruebas. No se editaron manualmente. Este reporte se creó después, con autorización independiente, sin actualizar el índice u otros reportes.

## 3. Configuración del perfil

```yaml
server:
  address: 127.0.0.1
  port: 8081

spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
      - org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
```

El perfil no importa archivos locales de credenciales, no elimina dependencias y no modifica políticas HTTP o de resiliencia. No depende de `ddl-auto: none`: la barrera consiste en evitar la creación de DataSource/JPA.

Las propiedades SQLite y ddl-auto del archivo base siguen presentes por herencia, pero las autoconfiguraciones que las convertirían en infraestructura están excluidas bajo este perfil. Sin activar el perfil, permanece el riesgo histórico de abrir o crear una base relativa al directorio de ejecución.

## 4. Prueba sobre el perfil real

El test utiliza SpringBootTest con `classes = SoaApplication.class`, `webEnvironment = MOCK`, ActiveProfiles `integracion-local` y AutoConfigureMockMvc. Carga ConfigData real, no una copia de exclusiones inyectada como propiedad de test.

Para acotar las fuentes de configuración durante la prueba se fija `spring.config.location=classpath:/application.yml` y se vacían additional-location, import e include. Spring carga el recurso derivado `application-integracion-local.yml` mediante el perfil activo. No se carga backend/.env.apis.local ni se leen/imprimen valores de SENACE_AUTH_KEY.

### Guardas anteriores a inicializar beans

Un ApplicationContextInitializer comprueba:

- Único perfil activo: integracion-local.
- PropertySource procedente del recurso application-integracion-local.yml.
- Lista efectiva de exclusiones con las tres clases esperadas.
- Dirección 127.0.0.1 y puerto 8081.

Después registra un BeanFactoryPostProcessor que busca tipos persistentes sin inicialización eager de FactoryBeans y nombres convencionales de DataSource/JPA/JDBC. Si se detectan, falla antes de continuar con los singletons.

Las clases esperadas aparecen en el código como valores de aserción, no como configuración alternativa para suplir un perfil ausente. La prueba también contrasta las exclusiones con ConditionEvaluationReport después de crear el contexto.

### Bordes externos y beans reales

Se reemplazan mediante MockBean:

- SenaceTransport.
- BcrpClient.

Se conservan reales el controlador, SenaceClient, configuración Jackson/AppConfig, modelos generados y CircuitBreakerRegistry. Los mocks evitan construir el transporte externo SENACE y ejecutar solicitudes BCRP. Se verifica `verifyNoInteractions(transport, bcrp)` después de cada caso.

El contexto no es WebServerApplicationContext. MockMvc utiliza DispatcherServlet de prueba sin abrir servidor HTTP. No se ejecutó spring-boot:run.

## 5. Casos ejecutados y resultados efectivos

| Caso | Resultado final y alcance |
|---|---|
| actualProfileIsLoadedAndExclusionsAreApplied | Perfil y recurso reales detectados; exclusiones efectivas y reportadas; loopback/puerto esperados; contexto sin servidor real |
| persistenceIsAbsentAndEndpointDependenciesRemainAvailable | Ausencia de DataSource, EntityManagerFactory, EntityManager, JpaRepository, Repository y JdbcOperations; dependencias API presentes |
| monitorUsesMvcWithoutProviderCallsOrServer | GET /api/estado-servicios devuelve 200 y nombres SENACE/BCRP, LLAMADAS_LOGICAS, NO_VERIFICADA y NO_IMPLEMENTADO |
| allFourExistingGetRoutesRemainRegistered | Registra exactamente los cuatro GET del controlador: dashboard, estado-servicios, consultoras y senace/datastream |
| jacksonPreservesNullableArraysObjectsNullAndDecimalPrecision | JsonNullable se serializa como array, objeto y null explícito; BigDecimal conserva su valor decimal en la comprobación Jackson |

La prueba de rutas comprueba registro de mappings, no ejecuta los otros tres endpoints con proveedores. Monitor confirma métricas internas, no disponibilidad externa. Los casos Jackson prueban serialización del ObjectMapper real del contexto, sin afirmar funcionamiento del navegador o de toda la aplicación.

## 6. Evidencia de ausencia de persistencia

Las guardas previas y las aserciones finales no encontraron:

- DataSource.
- EntityManagerFactory o EntityManager.
- Repositorios Spring Data/JPA.
- JdbcOperations/JdbcTemplate.
- Beans convencionales dataSource, entityManagerFactory, entityManager, jdbcTemplate o namedParameterJdbcTemplate.

Se encontraron controlador EcoInvest, SenaceClient, CircuitBreakerRegistry y JsonNullableModule. El contexto se inicializó satisfactoriamente sin la infraestructura persistente que abriría SQLite por el camino habitual inspeccionado.

**Confirmación dentro del alcance comprobado:** no se creó ni utilizó una conexión SQLite mediante DataSource/JPA/JDBC del contexto validado, y el agente no abrió archivos de base para comprobarlo. No se realizó monitorización del sistema de archivos o interceptación exhaustiva de syscalls; no se presenta una garantía universal frente a futuras clases que abran archivos directamente o configuraciones externas nuevas.

El test no lee una base existente, no crea una SQLite de prueba y no utiliza una conexión para demostrar su ausencia. El archivo histórico de la raíz se preservó sin inspección de contenido.

## 7. Java y Maven realmente utilizados

Ejecución autorizada desde `D:\Documentos\GitHub\Proyecto-SOA\backend`. Se configuraron JAVA_HOME y PATH únicamente en el proceso PowerShell ejecutor:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
```

Salida verificada:

- Oracle Java 17.0.12, build 17.0.12+8-LTS-286.
- Maven 3.9.9 mediante Maven Wrapper.
- Runtime Maven: C:\Program Files\Java\jdk-17, Oracle Corporation.
- Windows 11; release de compilación 17.
- La JVM de ejecución del test también informó Java 17.0.12.

No se modificó permanentemente la configuración del sistema ni se migró Java. Se comprobó 17.0.12 antes de continuar con Maven.

## 8. Comandos ejecutados e historial de resultados

```powershell
.\mvnw.cmd -o -DskipTests compile
.\mvnw.cmd -o "-Dtest=IntegracionLocalProfileTest" test
```

### Compilación inicial

BUILD SUCCESS a las 11:18:19 -05:00, duración Maven 6,690 s. Generó interfaces/modelos OpenAPI 7.4.0 y compiló fuentes Java. No se ejecutaron pruebas con compile.

### Primer intento de prueba

El contexto cargó integracion-local e inicializó el DispatcherServlet de prueba. Los cinco casos terminaron en error de preparación por una inyección ambigua de RequestMappingHandlerMapping: existían `requestMappingHandlerMapping` y `controllerEndpointHandlerMapping` de Actuator.

Resultado: 5 casos, 0 fallos de aserción, 5 errores, 0 omitidos; BUILD FAILURE a las 11:18:40. No fue un defecto de DataSource, una apertura SQLite detectada o una incompatibilidad productiva del perfil. No se presentaron los casos como aprobados por haberse inicializado el contexto.

### Corrección mínima

Se añadió `@Qualifier("requestMappingHandlerMapping")` a la inyección del test para seleccionar el mapping de API. No se cambió código productivo, Actuator o las exclusiones.

### Ejecución final

Se repitió exclusivamente:

```powershell
.\mvnw.cmd -o "-Dtest=IntegracionLocalProfileTest" test
```

Resultado final: **5 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**, a las 11:19:19 -05:00. Duración Maven 20,398 s; tiempo reportado de la clase 8,351 s.

No se suman los casos del intento fallido y final como diez pruebas satisfactorias. No se ejecutaron suites completas: Maven compiló las fuentes de test del proyecto como parte de su ciclo, pero Surefire seleccionó únicamente IntegracionLocalProfileTest.

Evidencia final de Surefire: [resumen de la clase](../../backend/target/surefire-reports/com.ecoinvest.soa.config.IntegracionLocalProfileTest.txt). Ese artefacto es volátil y puede desaparecer al limpiar target/. No se copiaron XML completos con propiedades de entorno a este reporte.

## 9. Efectos de Maven y advertencias

Maven offline regeneró modelos/interfaces en target/generated-sources/openapi y compiló target/classes y target/test-classes. No se editaron manualmente artefactos generados ni el OpenAPI fuente.

La caché permitió completar los comandos sin descargas solicitadas; no se habilitó red Maven o instalación de dependencias. Offline no se presenta como una barrera general contra cualquier plugin o aplicación: en esta ejecución los bordes de proveedor estaban simulados.

Advertencias observadas sin ampliar alcance:

- Uso de API deprecada en SenaceTransport existente.
- Operaciones unchecked en SenaceTransportTest existente durante compilación de tests.
- Advertencia JVM de class sharing relacionada con instrumentación de mocks.

No se corrigieron esas advertencias por asociación.

## 10. Ausencia de tráfico externo y servidor

Los dos bordes capaces de consultar proveedores estaban reemplazados antes de la creación de sus beans y las verificaciones Mockito acreditaron cero interacciones durante los cinco casos finales. GET Monitor se realizó mediante MockMvc, no por una conexión HTTP real.

No se inició Tomcat u otro servidor mediante spring-boot:run, no se abrió Vite, no se consultaron SENACE/BCRP y no se cargó el archivo local de credenciales. No se imprimieron tokens, URLs autenticadas, cookies o cuerpos externos.

La afirmación de ausencia de llamadas se limita a las dependencias verificadas y al código inspeccionado; no se realizó captura de paquetes del proceso. La JVM de pruebas terminó con Surefire, sin servicio dejado activo.

## 11. Riesgos y condiciones para autorizar el arranque posterior

Las autoconfiguraciones JDBC/transaccionales adicionales dependen normalmente de DataSource u otros beans persistentes y no dieron lugar a infraestructura detectada en el contexto validado. No se añadieron exclusiones innecesarias al POM o globalmente.

Persisten límites:

1. Un DataSource manual o una configuración externa futura puede anular la estrategia. La guarda comprueba tipos sin inicialización eager y nombres convencionales; un FactoryBean opaco o acceso directo a archivos requiere revisión específica.
2. La prueba acota fuentes de configuración del contexto. Un arranque CLI/IDE con perfiles adicionales, imports, propiedades u overrides distintos no queda certificado por ella.
3. El bind 127.0.0.1/8081 fue comprobado como propiedad; no se probó un socket real o conflicto de puerto.
4. La configuración normal de SQLite sigue intacta. No arrancar sin perfil porque el perfil de prueba haya funcionado.
5. Los clientes reales se sustituyeron en el test. Su construcción productiva y operación HTTP conjunta necesitan autorización posterior; las validaciones previas de proveedor no se repiten automáticamente.
6. No se modificó CORS ni se probó un navegador contra backend real.
7. Las guardas tempranas pertenecen al test, no constituyen un nuevo componente productivo de protección.

Antes de un arranque real se debe autorizar expresamente el comando, comprobar perfil y configuración efectivos sin divulgar secretos, conservar loopback, evitar overrides que reintroduzcan persistencia y limitar inicialmente el uso a Monitor. La prueba no concede permiso para leer credenciales o para endpoints que disparan HTTP externo.

## 12. Estado final y siguiente paso 7C.1

**7B completa su alcance de creación del perfil y validación aislada.** No se autoriza por implicación iniciar el servidor o conectar proveedores.

Siguiente encargo recomendado: **7C.1 — conexión HTTP progresiva del Monitor**, empezando por inspección/diseño de selección central de fuente y habilitación exclusiva de esa vista.

Condiciones propuestas:

- Selección explícita mock/http y URL pública backend, sin credencial SENACE en VITE_*.
- No fallback silencioso HTTP→mock.
- Evitar que el Dashboard, ruta inicial, lance consultas reales por habilitar HTTP globalmente.
- Mantener otras vistas con su fuente explícita o bloqueadas para HTTP hasta su autorización.
- Reutilizar cliente/adaptador existentes y conservar diseños.
- Autorizar separadamente cambios frontend, validaciones, arranque real integracion-local y GET local Monitor.
- No incluir SENACE/BCRP reales, SQLite, cartografía o nuevos endpoints en ese permiso inicial.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest cuenta ahora con `backend/src/main/resources/application-integracion-local.yml`, que fija 127.0.0.1:8081 y excluye DataSourceAutoConfiguration, HibernateJpaAutoConfiguration y JpaRepositoriesAutoConfiguration. No se modificaron application.yml original o dependencias; el arranque normal aún conserva SQLite/ddl-auto:update.

`IntegracionLocalProfileTest` carga el perfil YAML real mediante ConfigData y MVC MOCK, comprueba exclusiones y guardas previas, ausencia de DataSource/EntityManagerFactory/EntityManager/repositorios/JDBC, controlador y registro de circuitos, cuatro rutas registradas y serialización Jackson/JsonNullable. SenaceTransport/BcrpClient son mocks sin interacciones; Monitor devuelve 200 mediante MockMvc. No se abrió SQLite para verificarlo.

Se confirmó Oracle JDK 17.0.12 en Java, Maven y JVM del test, con Maven Wrapper 3.9.9 offline. Compile tuvo BUILD SUCCESS. El primer test falló por dos mappings MVC disponibles debido a Actuator; se añadió un Qualifier solo al test. La ejecución final terminó con cinco pruebas aprobadas, sin fallos/errores/omitidas y BUILD SUCCESS el 07/11/2026 a las 11:19:19 -05:00.

No se inició servidor real, Vite o proveedor ni se cargó .env.apis.local. Esta validación no certifica arranque con configuraciones externas distintas ni integración end-to-end. El siguiente prompt debe preparar 7C.1 Monitor con selección explícita de fuente y permisos separados de implementación, arranque local y HTTP. No repetir 7B ni conectar las cuatro vistas automáticamente.
