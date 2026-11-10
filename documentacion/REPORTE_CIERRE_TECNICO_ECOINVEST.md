# EcoInvest — Cierre técnico, ejecución local y demostración

Fecha de consolidación: **07/11/2026**. Repositorio: `D:\Documentos\GitHub\Proyecto-SOA`.

## 1. Resumen ejecutivo

EcoInvest es una plataforma SOA de inteligencia gubernamental que integra **SENACE y BCRP**. Dispone de una API Spring Boot con cuatro operaciones GET y un frontend React/TypeScript con Dashboard Analítico, Explorador Geográfico, Consultoras Ambientales y Monitor de Salud SOA.

Las cuatro vistas están implementadas. La integración HTTP local se validó progresivamente en 7C.1–7C.3 con consultas reales limitadas. Posteriormente, 7D consolidó regresiones y contraste con datos artificiales y 7E resolvió P01, el renderizado no acotado del Explorador, dentro de los tamaños medidos.

La última ejecución frontend acreditada en 7E tuvo **159 pruebas satisfactorias, typecheck y build correctos**. Son pruebas locales con fixtures/transporte controlado, no 159 consultas oficiales ni certificación end-to-end completa.

Este cierre es **técnico y documental del alcance desarrollado**, no una declaración de preparación productiva definitiva. Permanecen limitaciones de cobertura, precisión y georreferencia, memoria proporcional al volumen, accesibilidad exhaustiva y seguridad/despliegue productivo.

Durante la elaboración de este documento se efectuó únicamente inspección estática y creación del reporte autorizado. **No se ejecutaron pruebas, Maven, servidores, HTTP, instalaciones o commits; no se cargaron credenciales ni se accedió a SQLite.** Los comandos siguientes son una guía, no acciones realizadas en esta tarea.

## 2. Arquitectura final

```text
React + TypeScript / Vite
  ├─ AppShell y navegación hash: cuatro vistas
  ├─ Selección explícita mock | http
  ├─ Cliente fetch, AbortController, adaptadores y validación
  ├─ Fixtures artificiales identificados, solo en modo mock
  └─ Snapshot Dashboard en memoria → Explorador sin otro GET
                  |
                  | HTTP local manual
                  v
API Spring Boot / EcoInvestController / interfaces OpenAPI generadas
  ├─ BcrpClient → RestTemplate/Jackson → API oficial BCRP
  ├─ SenaceClient → SenaceTransport dedicado → API oficial SENACE
  |                └─ DTOs, SenaceMapper y ResultadoConsultaSenace
  └─ CircuitBreakerRegistry → métricas lógicas del Monitor
```

El contrato fuente es [backend/src/main/resources/openapi.yaml](../backend/src/main/resources/openapi.yaml). Maven genera interfaces/modelos en `backend/target/`; no deben editarse manualmente.

Los clientes conservan límites separados. SENACE usa Apache HttpClient 5 específico, sin modificar globalmente RestTemplate de BCRP. Los errores y la procedencia se representan mediante resultados y metadatos, sin sustituciones económicas o registros simulados automáticos.

No se introdujo un Service artificial solo para añadir una capa. La orquestación pública se encuentra en el controlador existente.

## 3. Estructura actual y tecnologías

| Ruta/componente | Función |
|---|---|
| `backend/` | Proyecto Maven Wrapper y API Java/Spring Boot. |
| `backend/src/main/java/com/ecoinvest/soa/` | Aplicación, configuración, controlador, clientes, DTOs y mapper. |
| `backend/src/main/resources/` | OpenAPI, configuración base y perfil `integracion-local`. |
| `backend/src/test/` | Pruebas unitarias, contextos aislados y pruebas opt-in documentadas. |
| `frontend/src/app/` | Shell, rutas y configuración pública. |
| `frontend/src/api/` | Cliente, adaptadores, validación, coordinador y almacenes de sesión. |
| `frontend/src/features/` | Dashboard, Explorador, Consultoras y Monitor. |
| `frontend/src/mocks/` | Datos artificiales y transporte de demostración. |
| `frontend/tests/api/` | Pruebas Node de lógica/API y regresión de volumen. |
| `documentacion/` | Reportes por fases y continuidad. |
| `.agents/skills/` | Procedimientos del agente; no conceden permisos adicionales. |

Tecnologías confirmadas por código/configuración y reportes:

| Tecnología | Versión o condición verificable |
|---|---|
| Java | Proyecto declara Java 17; ejecuciones backend acordadas con Oracle JDK **17.0.12**. |
| Spring Boot | **3.2.4** declarado en `backend/pom.xml`. |
| Maven | Wrapper presente (`backend/mvnw`, `mvnw.cmd`, `.mvn/wrapper/`); reportes acreditan Maven **3.9.9**. |
| OpenAPI | Contrato **3.0.3**, generación Maven **7.4.0** documentada. |
| Jackson / JsonNullable | Deserialización y serialización verificadas en pruebas de backend. |
| Resilience4j / Spring AOP | Circuit breakers y aspectos existentes, probados en contextos aislados. |
| HTTP SENACE | Apache HttpClient 5 exclusivo, límites de streaming y plazos. |
| React / React DOM | **19.3.1** declarados en `frontend/package.json`. |
| TypeScript | **7.0.2** declarado. |
| Vite / plugin React | **8.3.5 / 6.2.1** declarados. |
| Node/npm | Entorno histórico verificado: **22.16.0 / 10.9.2**. No se ejecutaron nuevamente en este cierre. |
| Requisito Node del frontend | `^20.19.0 || >=22.12.0`, según package.json. |
| Persistencia | Dependencias JPA/SQLite y configuración histórica presentes; no equivalen a respaldo operativo. |

Las versiones declaradas en package.json no son una nueva inspección del árbol instalado. No se migró Java, Spring Boot, paquetes o dependencias para producir este cierre.

## 4. Endpoints y contrato público

Todos conservan método GET y rutas existentes:

| Endpoint | Parámetros | Respuesta y errores | Uso frontend |
|---|---|---|---|
| `/api/dashboard` | `sector` obligatorio String; `anio` opcional, default 2017. | Objeto DashboardResponse; HTTP 200 puede estar degradado. SENACE/BCRP independientes. Conteos o valores desconocidos se omiten según contrato. | Consulta manual Dashboard; Explorador reutiliza su snapshot. |
| `/api/consultoras` | Sin parámetros de paginación backend. | Array raíz de ocho campos opcionales. 200 con filas o vacío oficial; 502 por formato inválido; 503 por ausencia/indisponibilidad. Metadatos en headers. | Consulta manual, búsqueda/filtros/paginación local. |
| `/api/estado-servicios` | Sin parámetros. | Objeto de métricas SENACE/BCRP; no consulta proveedores. | Actualización manual del Monitor, sin polling. |
| `/api/senace/datastream` | `nombre` opcional; default CarteraProyectos. | Objeto envolvente con data flexible: arrays de recursos verificados, objeto heredado o null en errores; 200/502/503. | Cliente/adaptador existentes, pero capacidad HTTP genérica bloqueada en la selección actual de vistas. |

`anio` es año de inicio derivado de FECHA_INICIO mediante `dd/MM/uuuu` estricto, **no año de aprobación**. Sector se compara con ACTIVIDAD normalizada sin modificar su valor original. El catálogo económico BCRP no se infiere de una equivalencia ambiental arbitraria.

Headers de Consultoras:

```text
X-Senace-Origen
X-Senace-Estado
X-Senace-Cobertura
X-Senace-Motivo
X-Senace-Cuerpo-Completo
```

La respuesta oficial vacía no es equivalente a un array vacío de error. HTTP 200 tampoco demuestra por sí solo disponibilidad de ambas fuentes.

El genérico conserva CarteraProyectos, ConsultorasAmbientales y seis recursos heredados: GastoEspecifica, GastoFuente, GastoGenerica, SolicitudAcceso, Visitas y Reclamos. Los esquemas de esos seis recursos **no están certificados por las validaciones prioritarias**. No usar esa ruta como excusa para descargar recursos no investigados.

## 5. Matriz final de funcionalidades

| Componente | Implementado | Evidencia | Límites/pendientes |
|---|---|---|---|
| Dashboard Analítico | Consulta manual HTTP/mock, actividad/año de inicio, tres KPI, distribución por estado, tabla, metadatos y degradación. | 7C.3: consulta real Hidrocarburos/2017; 7D/7E: regresiones artificiales. | No inversión, correlación, tendencias inexistentes o certificación global de proyectos. Precisión JS limitada. |
| Explorador Geográfico | SVG offline, lista, búsqueda/estado, ficha, zoom, snapshot compartido, 20 filas por página y hasta 21 puntos. Coincidencias exactas accesibles por lista paginada. | 7C.3: respuesta real compartida; 7E: 159 tests y benchmark/QA artificial. | Sin tiles, mapa oficial, región o CRS certificado. Datos completos/índices siguen en memoria O(N). |
| Consultoras Ambientales | Ocho campos públicos, filtros locales, búsqueda, diez filas DOM por página, detalle, duplicados y fechas originales. | 7C.2: GET real con 15.137 filas; 7D: volumen artificial hasta 20.000. | Filas no equivalen a empresas únicas. No RUC/registro/nivel/estado o vigencia inventados. Búsqueda de volumen aún optimizable. |
| Monitor de Salud SOA | Circuitos, tasa, llamadas fallidas/exitosas, alcance, respaldo y disponibilidad declarados; consulta manual. | 7C.1: GET local real; pruebas y QA artificial posteriores. | CLOSED no certifica disponibilidad; no historial, logs, latencia o respaldo SQLite inventados. |
| API Spring Boot | Cuatro endpoints y OpenAPI generado; orquestación con ausencia/degradación honestas. | 3C.1 integrado aislado; perfil 7B; ejecuciones HTTP locales 7C. | No certificación exhaustiva de seguridad/despliegue o todos los escenarios productivos. |
| Integración SENACE/BCRP | Clientes reales, rutas y series aprobadas, deserialización, procedencia, errores y resiliencia. | Reportes reales 2C, 3C.2 y 7C; AOP y negativos con simulaciones. | Proveedores pueden variar o devolver HTML/error. Cobertura NO_VERIFICADA y autenticación obligatoria no demostrada por una comparación específica. |

El proyecto es **EcoInvest, SENACE + BCRP**. Referencias históricas a EcoMetrics, OEFA o ESG no describen la funcionalidad actual.

## 6. Integraciones externas

### 6.1. SENACE

Base admitida por el transporte actual:

```text
https://datosabiertos.senace.gob.pe/Api/datastreams
```

El código exige ese path base, con o sin barra final; el mero dominio raíz no basta. Recursos verificados:

- `/Api/datastreams/CarteraProyectos`
- `/Api/datastreams/ConsultorasAmbientales`

No añaden `/data.json`. El transporte incorpora `auth_key` en memoria y no debe divulgar URLs autenticadas, cuerpos o credenciales.

Límites implementados/documentados: 6.291.456 bytes durante streaming, conexión 5 s, inactividad 10 s, plazo total HTTP 25 s, sin redirecciones ni reintentos automáticos. TLS y validación de hostname normales. Sin configuración SENACE utilizable se devuelve ausencia segura, sin detener por ello BCRP.

### 6.2. BCRP

API pública oficial predeterminada:

```text
https://estadisticas.bcrp.gob.pe/estadisticas/series/api
```

No se requiere una API key BCRP en la integración implementada. La propiedad del cliente es `external.apis.bcrp-base-url`, con valor oficial por defecto. No asumir que una variable `BCRP_BASE_URL` de un archivo local tenga mapeo Spring explícito equivalente.

| Clave admitida | Serie | Alcance económico |
|---|---|---|
| AGRICULTURA | PM04969AA | Agrícola. |
| PESCA | PM04971AA | Pesca. |
| MINERIA / MINERÍA | PM04973AA | Minería metálica. |
| HIDROCARBUROS | PM04974AA | Hidrocarburos. |
| ELECTRICIDAD | PM04978AA | Electricidad y Agua. |
| TODOS | PM04983AA | PBI nacional. |

Son series anuales de variación porcentual interanual; la consulta usa un año concreto, sin reemplazar datos por años vecinos o constantes. Transportes, Energia y claves no reconocidas no reciben una serie ficticia. Las etiquetas públicas deben conservar el alcance económico real devuelto.

## 7. Evidencias de pruebas: historia y alcance

### 7.1. Backend y proveedores

| Fase/reporte | Evidencia histórica | Qué no demuestra |
|---|---|---|
| Fase 1 | Estabilización, generación y 18 pruebas locales documentadas. | No es una suite actual completa. |
| Fase 2B | Catálogo anual BCRP y 75 pruebas locales. | No son 75 consultas oficiales. |
| Cierre 2C BCRP | HTTPS con JDK 17; Hidrocarburos 2017/2018 y cinco series restantes para 2018. AOP/circuitos probados separadamente con simulaciones. | No garantiza disponibilidad permanente; hubo antecedentes de HTML de protección rechazado de forma segura. |
| Fase 3C.1 | 32 pruebas integradas con controlador/cliente/mapper/Jackson/AOP reales y transporte SENACE/BCRP simulado, sin SQLite. | No son consultas oficiales ni aplicación completa levantada. |
| Fase 3C.2 | 04/11/2026: dos ejecuciones reales aisladas, una por recurso SENACE; 2.567 proyectos y 15.015 filas de consultoras. | No son dos casos de una misma suite conservada ni acreditan cobertura oficial completa. |
| Fase 7B | 5 pruebas finales satisfactorias del perfil real, exclusiones y ausencia de infraestructura de persistencia. | No es una auditoría de syscalls de todas las futuras ejecuciones. |

### 7.2. Frontend por lotes

| Lote | Resultado y contribución documentados |
|---|---|
| 1 | Scaffold React/TypeScript/Vite, shell, logos y navegación; typecheck/build y revisión local documentados. |
| 2 | Cliente, tipos, adaptadores y mocks: 39 pruebas satisfactorias. |
| 3 | Dashboard artificial: 54 pruebas; responsive corregido y validado posteriormente en su cierre visual. |
| 4 | Explorador artificial offline: 64 pruebas y revisión local. |
| 5 | Consultoras artificial: 80 pruebas y revisión local. |
| 6 | Monitor artificial: 98 pruebas y revisión local. |

Estas cifras son snapshots de suites de distintas fases. **No deben sumarse para afirmar una ejecución conjunta.**

### 7.3. Integración local real y consolidación posterior

| Fase | Resultado histórico |
|---|---|
| 7C.1 | Un GET real local Monitor, 200; circuitos CLOSED y métricas iniciales. Sin consultas externas. Suite frontend de esa fase: 118. |
| 7C.2 | Un GET real Consultoras, 200 y 15.137 filas, CORS legible y origen API_DIRECTA_SENACE. Suite de esa fase: 131. |
| Cierre QA 7C.2 | Sin proveedores: bloqueo de rutas resuelto mediante comparación Unicode y espera de DOM; evidencia histórica conservada. |
| 7C.3 | Un GET real Dashboard Hidrocarburos/2017: 48 registros, 40 aprobados, indicador -2.44780196733593, 48 filas compartidas con Explorador sin otro GET. Suite: 143. |
| 7D | Sin proveedores: shell, regresiones, volumen y correcciones de contraste. Suite final: 151. |
| 7E | Sin proveedores: optimización P01 y benchmark antes/después. **Última suite: 159**, con typecheck/build correctos. |

El 200 de 7C.2/7C.3 fue observado directamente en el backend desde el navegador. No se capturaron exhaustivamente todos los intercambios upstream; conservar las limitaciones específicas de cada reporte. Los conteos son observaciones de esas respuestas y fechas, no constantes del producto.

### 7.4. Rendimiento y cierre de P01

En el benchmark equivalente de 7E con 20.000 proyectos artificiales:

- Navegación al DOM observado: **10.336–11.759 ms antes / 216,7–217,2 ms después**, dos muestras por versión/tamaño.
- Primera página: 20.000 → 20 filas y 13.333 → 7 puntos en ese fixture.
- DOM: 180.161 → 334 elementos activos en la muestra inicial.
- Selección y zoom posteriores en torno a 28–35 ms, con variabilidad y costes de observación.
- Todas las filas permanecen accesibles; se verificaron 6.667 coincidencias exactas en 334 páginas.

P01 quedó cerrado para el alcance medido, no para volúmenes ilimitados. Las cifras incluyen desarrollo/CDP/frames; no son SLA ni porcentaje garantizado de mejora. El heap depende de GC y no demuestra ausencia de fugas.

## 8. Requisitos de ejecución local

1. Windows/PowerShell para los ejemplos siguientes.
2. Oracle JDK **17.0.12** para backend. Ruta histórica validada: `C:\Program Files\Java\jdk-17`; comprobarla en cada equipo.
3. Maven Wrapper del propio `backend/`. Offline requiere dependencias/plugins ya presentes en la caché.
4. Node compatible con package.json; entorno histórico Node 22.16.0 y npm 10.9.2.
5. Dependencias frontend ya instaladas para ejecutar Vite. En un clon nuevo, su instalación requiere una operación separada; este documento no instala ni actualiza nada.
6. Puertos loopback 8081 (backend) y 5173 (frontend) disponibles.
7. Para SENACE real, configuración vigente únicamente en el proceso backend.

No es necesario Java, Spring Boot o credencial SENACE para una demostración frontend con dependencias ya disponibles.

## 9. Modo demostración: solo frontend y datos artificiales

Abrir una terminal PowerShell dedicada:

```powershell
Set-Location 'D:\Documentos\GitHub\Proyecto-SOA\frontend'
$env:VITE_API_MODE='mock'
$env:VITE_API_BASE_URL=''
$env:VITE_HTTP_FEATURES=''
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort
```

Abrir `http://127.0.0.1:5173/#/dashboard` y navegar a las otras tres vistas. El shell y los avisos deben identificar **datos artificiales**. Los escenarios simulan respuestas contractuales; que un fixture contenga API_DIRECTA no convierte ese registro en información oficial real.

Recorrido recomendado:

1. Dashboard: filtros de demostración, KPI, gráfico por estado y tabla.
2. Explorador: selección heredada y escenario de coordenadas artificiales cuando se quiera mostrar marcadores; lista paginada, coincidencias y ficha.
3. Consultoras: búsqueda/filtros, diez filas por página, detalle y escenarios vacío/error/degradado.
4. Monitor: estados de circuitos artificiales, métricas y limitaciones.

Los sectores/años soportados por los fixtures no son el catálogo oficial completo. No cambiar a HTTP para obtener más combinaciones durante una demostración sin evaluar el tráfico externo.

Detener Vite con Ctrl+C. No es necesario arrancar backend o SQLite para este modo.

## 10. Modo HTTP local: preparación backend segura

### 10.1. Perfil obligatorio para este recorrido

Utilizar [application-integracion-local.yml](../backend/src/main/resources/application-integracion-local.yml):

- `server.address=127.0.0.1`.
- `server.port=8081`.
- Exclusiones de DataSourceAutoConfiguration, HibernateJpaAutoConfiguration y JpaRepositoriesAutoConfiguration.

La [configuración base](../backend/src/main/resources/application.yml) aún declara SQLite/JPA con `ddl-auto: update`. **No usar un arranque normal sin el perfil seguro** bajo la restricción de no abrir/modificar bases. `ddl-auto:none` por sí solo no garantiza que DataSource/JPA no abran SQLite.

Antes de arrancar, revisar perfiles y overrides del proceso, configuraciones Maven/JVM adicionales y disponibilidad del puerto. Si existen overrides desconocidos o una fuente de configuración que reintroduzca persistencia, detenerse y revisarlos. No abrir la base para demostrar el aislamiento.

### 10.2. Configuración y credenciales

Mapeo existente:

```text
SENACE_BASE_URL → external.apis.senace-base-url
SENACE_AUTH_KEY → external.apis.senace-auth-key
```

`backend/.env.apis.local` es un archivo local ignorado, **no se carga automáticamente por Spring Boot**. Tampoco debe ejecutarse como script PowerShell. Su uso requiere un parser de pares clave/valor limitado a las claves necesarias o una preparación explícita del proceso.

Para una guía reproducible sin leer ese archivo, puede configurarse una ventana PowerShell dedicada con entrada oculta. No colocar el token literal en comandos, historial compartido o reportes:

```powershell
Set-Location 'D:\Documentos\GitHub\Proyecto-SOA\backend'

# Solo en esta ventana/proceso; no modifica variables de Windows permanentemente.
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version

$env:SENACE_BASE_URL='https://datosabiertos.senace.gob.pe/Api/datastreams'
$senaceSecret=Read-Host 'Credencial SENACE, entrada oculta' -AsSecureString
try {
    $env:SENACE_AUTH_KEY=[System.Net.NetworkCredential]::new('', $senaceSecret).Password
} finally {
    Remove-Variable senaceSecret -ErrorAction SilentlyContinue
}
```

El valor debe estar en texto utilizable dentro del entorno del proceso Java para autenticar; la entrada oculta evita mostrarlo, no convierte las variables de entorno en un almacén cifrado. Mantener esta ventana y sus procesos limitados al uso local necesario. No usar `setx`, no pasar credenciales como argumentos JVM ni copiarlas a VITE, bundle o almacenamiento de navegador.

Si se prefiere el archivo local existente, no duplicar credenciales: aplicar el procedimiento restringido ya aprobado en los reportes de integración, verificando dominio/ruta, duplicados y presencia sin mostrar valores. No asumir que importar ese archivo como configuración de contexto del agente está autorizado.

### 10.3. Arranque backend

Desde esa misma ventana, una vez revisados los overrides:

```powershell
try {
    .\mvnw.cmd -o '-Dspring-boot.run.profiles=integracion-local' '-Dspring-boot.run.arguments=--spring.config.location=classpath:/application.yml --spring.config.additional-location= --spring.config.import= --spring.profiles.include=' spring-boot:run
} finally {
    Remove-Item Env:SENACE_AUTH_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:SENACE_BASE_URL -ErrorAction SilentlyContinue
}
```

Estos argumentos acotan fuentes adicionales conocidas; no sustituyen la revisión de variables/propiedades de mayor prioridad que podrían cambiar el perfil o la persistencia. Comprobar en logs Java 17.0.12, **un solo perfil activo integracion-local** y Tomcat local en 8081. Si el entorno no coincide, detener el proceso antes de consultar.

Maven offline puede regenerar OpenAPI y compilar fuentes/tests en `target/` antes de iniciar. Si faltan dependencias en caché, no habilitar descargas automáticamente. `spring-boot:run` no equivale a ejecutar la suite de pruebas.

El arranque no autoriza consultas oficiales. En los flujos implementados, los botones manuales son los que inician las operaciones. La autorización operativa y presupuesto del usuario deben respetarse cuando un agente conduzca la sesión.

## 11. Modo HTTP local: frontend y recorrido manual

En otra terminal PowerShell dedicada:

```powershell
Set-Location 'D:\Documentos\GitHub\Proyecto-SOA\frontend'
$env:VITE_API_MODE='http'
$env:VITE_API_BASE_URL='http://127.0.0.1:8081'
$env:VITE_HTTP_FEATURES='monitor,consultoras,dashboard,explorador'
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort
```

La base frontend es el origen **sin `/api`**. El parser actual acepta ese destino local y combinaciones exactas `monitor`, `monitor,consultoras` o la combinación completa mostrada. No usar espacios/reordenamientos o dominios distintos suponiendo que se normalizarán. Cambiar variables requiere reiniciar Vite; no es un selector dinámico de conexión dentro de la vista.

Recorrido y efectos:

| Vista/acción | Efecto esperado |
|---|---|
| Navegar a cualquier vista HTTP | No inicia GET por montaje. |
| Pulsar Consultar Monitor | Un GET local de métricas; no consulta proveedores. |
| Pulsar Consultar Consultoras | GET local y consulta del recurso SENACE; puede traer el conjunto completo, sin paginación oficial acreditada. |
| Pulsar Consultar Dashboard | GET local con sector/año; puede realizar una consulta a SENACE y otra a BCRP. Definir parámetros/presupuesto antes. |
| Abrir Explorador después de Dashboard | Reutiliza snapshot, sin GET adicional. |
| Abrir Explorador sin snapshot | Espera explicativa y acceso al Dashboard, sin consulta silenciosa. |
| Buscar, paginar, elegir fila/punto o zoom | Operación local sobre los datos cargados, sin consultar proveedores. |

No pulsar nuevamente para resolver un timeout/error sin evaluar el resultado y un nuevo presupuesto. No provocar fallos oficiales para demostrar escenarios negativos; usar mocks.

Las etiquetas HTTP significan modo/configuración seleccionados; la respuesta efectiva se acredita con status/metadatos en cada vista. No hay fallback silencioso a mocks. Si falla HTTP, se conserva la ausencia/error correspondiente.

En Explorador, sector/año HTTP se cambian desde Dashboard y requieren nueva consulta explícita. La búsqueda, estado y coincidencias son locales. Los filtros pendientes no sustituyen la selección efectiva del snapshot.

El snapshot es de sesión en memoria: se pierde al recargar, no se persiste en localStorage, no tiene garantía de frescura del proveedor y no se vuelve a consultar al navegar. Cancelar una operación cliente no debe interpretarse como garantía de deshacer trabajo ya iniciado por un servidor/proveedor.

Al terminar: Ctrl+C en ambos procesos, cerrar las ventanas dedicadas y comprobar puertos si es necesario. No dejar credenciales persistidas o procesos temporales de demostración sin supervisión.

## 12. Validaciones locales para futuras tareas

No se ejecutaron al redactar este reporte. Para un encargo de validación autorizado, desde `frontend/`:

```powershell
npm run typecheck
npm run test:api
npm run build
```

El runner actual usa Node/TypeScript y transporte artificial; no necesita Spring Boot o proveedores. El build genera `dist/`; las pruebas generan artefactos locales de compilación. Un build correcto no demuestra responsive, accesibilidad o disponibilidad externa.

Las pruebas backend específicas y las reales opt-in deben seleccionarse según su reporte/alcance. No ejecutar una suite indiscriminada o un contexto productivo suponiendo que todos excluyen persistencia. Las consultas reales requieren configuración y autorización propias, no solo `-o` en Maven.

## 13. Auditoría documental de este cierre

Se inventariaron los reportes Markdown bajo `documentacion/` y su organización por fases. La comprobación estática de enlaces locales en ese árbol revisó **451 referencias** antes de crear este documento:

- No se encontraron archivos destino ausentes después de interpretar sufijos de línea.
- Una referencia histórica utiliza `SoaApplicationTests.java:7` en el reporte 7A. El archivo base existe; el sufijo es notación de línea y no se eliminó ni se reescribió.
- La comprobación fue de existencia de destinos, no de anclas Markdown, semántica de números de línea o disponibilidad futura de artefactos `target/`.
- Las rutas de evidencias/artefactos generados siguen sujetas a la limpieza del workspace; los reportes conservan el contexto histórico.

**No existe README.md en la raíz** del repositorio inspeccionado. [documentacion/README.md](README.md) existe pero su índice y narración aún describen etapas anteriores: frontend futuro y SENACE sin integración real, ya superados por reportes posteriores. Los reportes históricos no deben borrarse o reinterpretarse como si ya conocieran los avances futuros.

Este documento aporta una entrada de continuidad vigente sin actualizar automáticamente ese índice. Actualizar `documentacion/README.md` o crear un README raíz requiere autorización independiente. No se reorganizó documentación ni se corrigieron enlaces ajenos durante este cierre.

## 14. Limitaciones técnicas y de interpretación

1. **Cobertura oficial NO_VERIFICADA.** Recibir el cuerpo completo y contar filas no acredita la totalidad del universo administrativo o ausencia de paginación no detectada.
2. **Circuitos no equivalen a salud externa.** CLOSED/OPEN/HALF_OPEN describen protección interna; LLAMADAS_LOGICAS no es necesariamente número de GET upstream.
3. **Georreferencia no certificada.** Coordenadas en rango no demuestran CRS, datum, región o ubicación real. El SVG no es cartografía oficial.
4. **Memoria O(N).** Paginación acota DOM, no datos/índices completos. Heap depende de GC y no hay prueba universal de ausencia de fugas.
5. **Precisión JavaScript.** BigDecimal/int64 backend pueden exceder representación exacta Number. Se mantienen validación/advertencias y rechazo de enteros inseguros; no hay aritmética decimal económica exacta en navegador.
6. **Consultoras son filas.** No se inventan RUC, registro, nivel, ID empresarial, vigencia o estado administrativo; no se deduplican como empresas únicas.
7. **Fechas y sectores.** FECHA_INICIO no es aprobación; ACTIVIDAD SENACE no acredita una equivalencia BCRP nueva. No hay agregaciones temporales inventadas.
8. **Disponibilidad variable.** Los proveedores pueden devolver HTML, errores o ausencia. Los clientes deben conservar su tratamiento seguro; no eludir protecciones ni reemplazar por cifras ficticias.
9. **WCAG no certificado.** Se verificaron aspectos básicos, tres correcciones de contraste y teclado concreto, no una auditoría exhaustiva con lectores de pantalla o todos los dispositivos.
10. **Persistencia histórica.** Existe configuración JPA/SQLite y un archivo de base en el repositorio de trabajo; eso no prueba respaldo funcional. El Monitor informa NO_IMPLEMENTADO.
11. **Seguridad productiva pendiente.** CORS wildcard local no sustituye controles de acceso. Orígenes, autenticación/autorización, exposición de datos, logs y secretos en nube requieren diseño por entorno.
12. **Integración no exhaustiva.** Las observaciones reales cubren recursos/selecciones concretos y los negativos suelen ser artificiales. No hay certificación global de todas las combinaciones ni despliegue productivo.

## 15. Trabajos futuros y prioridades

| Prioridad | Trabajo propuesto | Condición |
|---|---|---|
| Documental | Actualizar el índice `documentacion/README.md` y decidir README raíz con enlace a este cierre. | Autorización independiente; preservar historia. |
| QA | Revisión visual final autorizada de paginación/contadores del Explorador y accesibilidad más amplia. | Sin repetir consultas oficiales si mocks bastan; capturas no sensibles y alcance explícito. |
| Rendimiento | Evaluar búsqueda de Consultoras si el objetivo de interacción exige reducir los ~499 ms observados con 20.000 filas artificiales. | Definir objetivo y medir antes; sin dependencias por costumbre. |
| Operación local | Si se desea automatizar arranque seguro, diseñar un launcher limitado a las claves necesarias con revisión de overrides. | No existe por la mera presencia del archivo .env; solicitar alcance y archivos. |
| Producción | Estrategia de secretos, TLS, control de acceso/CORS, observabilidad segura, recursos, límites y despliegue. | Diseño/validación independiente, sin publicar automáticamente. |
| Datos/geografía | Definir frescura, cobertura, semántica administrativa y referencia espacial si aparecen evidencias oficiales adicionales. | No inventar campos, normalizaciones o servicios sin respaldo. |

P01 no debe reabrirse por rutina: quedó cerrado en 7E dentro del alcance medido. Nuevos volúmenes, mapas globales u objetivos de latencia distintos son requisitos nuevos que deben justificarse. La Fase 2C BCRP también permanece cerrada en su alcance, sin repetirla salvo evidencia nueva.

## 16. Documentos de referencia vigentes

### Backend e integraciones

- [Cierre BCRP 2C](02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md).
- [Decisiones SENACE](03_Fase3_SENACE/REPORTE_DECISIONES_FASE3B1_SENACE.md).
- [Controlador/contrato SENACE, Lote 4](03_Fase3_SENACE/REPORTE_IMPLEMENTACION_FASE3B2_LOTE4.md).
- [Validación integrada artificial 3C.1](03_Fase3_SENACE/REPORTE_VALIDACION_FASE3C1_SENACE.md).
- [Validación real SENACE 3C.2](03_Fase3_SENACE/REPORTE_VALIDACION_FASE3C2_SENACE.md).
- [Preparación backend 3C.3](03_Fase3_SENACE/REPORTE_CIERRE_BACKEND_FASE3C3.md).

### Frontend y cierre

- [Lote 1](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE4B_LOTE1.md), [Lote 2](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE4B_LOTE2.md), [Lote 3](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE4B_LOTE3.md).
- [Lote 4](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE4B_LOTE4.md), [Lote 5](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE4B_LOTE5.md), [Lote 6](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE4B_LOTE6.md).
- [Auditoría 7A](04_Fase4_Frontend/REPORTE_AUDITORIA_INTEGRACION_FASE7A.md), [Perfil seguro 7B](04_Fase4_Frontend/REPORTE_IMPLEMENTACION_FASE7B_BACKEND.md).
- [Monitor 7C.1](04_Fase4_Frontend/REPORTE_INTEGRACION_FASE7C1_MONITOR.md), [Consultoras 7C.2](04_Fase4_Frontend/REPORTE_INTEGRACION_FASE7C2_CONSULTORAS.md), [QA local 7C.2](04_Fase4_Frontend/REPORTE_CIERRE_QA_FASE7C2.md).
- [Dashboard/Explorador 7C.3](04_Fase4_Frontend/REPORTE_INTEGRACION_FASE7C3_DASHBOARD_EXPLORADOR.md).
- [Validación integral 7D](04_Fase4_Frontend/REPORTE_VALIDACION_INTEGRAL_FASE7D.md).
- [Optimización Explorador 7E](04_Fase4_Frontend/REPORTE_OPTIMIZACION_EXPLORADOR_FASE7E.md).

## 17. Conclusión final

EcoInvest dispone de una base funcional local consolidada para demostrar las cuatro vistas con datos artificiales y realizar consultas HTTP manuales mediante el backend configurado de forma aislada de persistencia. Se conservaron el contrato, procedencia y ausencia honesta de información, y se corrigió el principal cuello de renderizado identificado.

Las capacidades terminadas no deben confundirse con disponibilidad permanente de proveedores, cobertura oficial completa, georreferencia certificada o aprobación productiva. El siguiente trabajo puede ser documental/QA u operativo según decisión explícita; este cierre no inicia ninguna fase automáticamente.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest es SOA SENACE+BCRP, backend Java 17/Oracle JDK 17.0.12 y Spring Boot 3.2.4, frontend React 19.3.1/TypeScript 7.0.2/Vite 8.3.5. Cuatro GET vigentes: dashboard, consultoras, estado-servicios y senace/datastream. OpenAPI es la fuente; no editar target manualmente. El genérico existe pero no está habilitado como capacidad HTTP de las vistas y seis recursos heredados no están certificados.

Las cuatro pantallas están implementadas. Modo mock usa datos artificiales explícitos y no necesita backend. HTTP local usa VITE_API_MODE=http, base http://127.0.0.1:8081 y features exactos monitor,consultoras,dashboard,explorador. Todas las cargas HTTP son manuales; Explorador reutiliza la última respuesta Dashboard sin GET, sin localStorage o refresco automático. SENACE recibe configuración solo en backend y .env.apis.local no se importa automáticamente. Base admitida: https://datosabiertos.senace.gob.pe/Api/datastreams. BCRP usa API pública sin clave.

Para backend local utilizar integracion-local, que fija loopback/8081 y excluye DataSource/JPA. La configuración normal aún contiene SQLite/ddl-auto:update; no arrancar sin revisar perfil/overrides. No hay respaldo SQLite operativo. No poner credenciales en VITE ni leerlas por asociación.

Evidencias reales históricas: BCRP 2C cerrado; SENACE 3C.2 procesó 2.567 proyectos y 15.015 filas de consultoras en pruebas aisladas. 7C.1 verificó Monitor HTTP; 7C.2 un GET real devolvió 15.137 consultoras; 7C.3 un GET Dashboard Hidrocarburos/2017 devolvió 48 registros, 40 aprobados e indicador -2.44780196733593, reutilizado por Explorador sin otro GET. No fijar esas cifras como constantes ni repetir consultas para documentarlas.

7D corrigió textos del shell y contraste con 151 pruebas. 7E cerró P01 con 159 pruebas frontend, typecheck/build y benchmark artificial equivalente: 20.000 proyectos pasaron de 10,3–11,8 s a unos 217 ms hasta DOM observado. Explorador renderiza 20 filas y hasta 21 puntos, agrupa solo coordenadas exactamente coincidentes con contador y permite todas sus filas por paginación. No elimina registros; memoria e índices siguen O(N). No hay clustering externo, tiles o CRS certificado.

Última suite vigente documentada: **159 pruebas artificiales**, no suma histórica ni nueva ejecución en este cierre. Cobertura NO_VERIFICADA, CLOSED no demuestra proveedor disponible, coordenadas/precisión JS limitadas, WCAG completo y seguridad/despliegue productivos pendientes. No certificar end-to-end exhaustivo.

Este reporte fue la única creación autorizada en el cierre; no hubo nuevas ejecuciones, credenciales, SQLite o consultas. No existe README raíz y el índice documentacion/README.md está desactualizado; actualizarlos requiere permiso separado. Siguiente prompt recomendado: mantenimiento documental del índice y, si se desea, guía README enlazada a este cierre, o QA visual/accesibilidad acotado, sin reimplementar funcionalidades ni desplegar automáticamente.
