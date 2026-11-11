# EcoInvest: preparación Docker y Azure Container Apps

Fecha: 09/11/2026. Alcance: archivos preparados y revisión estática. **No se han construido imágenes, ejecutado las nuevas pruebas, creado recursos Azure ni desplegado el proyecto en esta actividad.** No se consultaron SENACE/BCRP ni se cargaron credenciales.

## 1. Arquitectura propuesta

```text
Navegador HTTPS
  → Container App frontend pública, Nginx no privilegiado :8080
      ├─ React/TypeScript compilado, navegación hash
      └─ GET /api/{dashboard,consultoras,estado-servicios}
          → HTTPS / FQDN interno de Container App backend :8081
              → Spring Boot, perfil azure, Java Oracle 17.0.12
                  ├─ SENACE, clave inyectada desde Key Vault
                  └─ BCRP, API pública sin clave
```

Las dos aplicaciones pertenecen al mismo entorno Container Apps. Solo el frontend tiene ingress externo. Azure termina TLS en el ingress; Nginx verifica TLS/hostname del salto HTTPS interno. Backend escucha en `0.0.0.0:8081`; el ingress interno dirige a ese puerto. El navegador nunca usa `127.0.0.1` para acceder al backend desplegado.

No hay nueva base de datos, volumen SQLite, importación `.env`, SAGA o microservicios de negocio nuevos. Se conservan los cuatro endpoints del backend. El proxy publica únicamente las tres operaciones usadas por las vistas: el Explorador reutiliza Dashboard y el genérico permanece bloqueado públicamente.

## 2. Archivos preparados

| Archivo | Responsabilidad |
|---|---|
| [backend/Dockerfile](../../backend/Dockerfile) | Construcción Maven 3.9.9 y runtime sobre una imagen Oracle JDK aprobada; verifica versión 17.0.12 y proveedor Oracle en build; usuario runtime 10001. |
| [backend/.dockerignore](../../backend/.dockerignore) | Allowlist de POM y fuentes principales: excluye credenciales locales, bases, target y tests. |
| [application-azure.yml](../../backend/src/main/resources/application-azure.yml) | Bind cloud, exclusiones DataSource/Hibernate/JPA, probes, errores sin detalles y documentación web desactivada. |
| [AzureProfileTest.java](../../backend/src/test/java/com/ecoinvest/soa/config/AzureProfileTest.java) | Pruebas preparadas de ConfigData real, guardas previas al refresh, aislamiento y MVC/probes sin proveedores. |
| [frontend/Dockerfile](../../frontend/Dockerfile) | Build Node 22.16.0 con lockfile y runtime Nginx no privilegiado; variables públicas fijadas para mismo origen. |
| [frontend/.dockerignore](../../frontend/.dockerignore) | Evita enviar node_modules, dist, archivos .env y otros datos locales al contexto. |
| [Nginx](../../frontend/nginx/default.conf.template) | Estáticos, proxy HTTPS, sin reintentos, respuestas sin caché para API y healthz local. |
| [source.ts](../../frontend/src/api/source.ts), [config.ts](../../frontend/src/app/config.ts) | Añaden `same-origin` resuelto desde el navegador; mantienen el destino local anterior y las capacidades explícitas. |
| [source.test.mjs](../../frontend/tests/api/source.test.mjs) | Casos adicionales de mismo origen, destino rechazado y ausencia de GET al construir el cliente. |
| DashboardPage, ConsultorasPage, MonitorPage | Etiquetas HTTP neutrales: backend configurado, sin afirmar que se ejecuta localmente o que está disponible. |
| [foundation.bicep](../../infra/azure/foundation.bicep) | ACR sin admin, Key Vault RBAC, identidades separadas, permisos mínimos, logs y entorno Consumption. |
| [apps.bicep](../../infra/azure/apps.bicep) | Dos Container Apps, imágenes versionadas, secreto referenciado, probes y rutas de ingress. |

No se modifican `application.yml`, `application-integracion-local.yml`, POM, OpenAPI, clientes SENACE/BCRP o políticas Resilience4j.

## 3. Requisitos y decisiones antes de ejecutar

1. Elegir suscripción, región admitida, grupo de recursos nuevo y prefijo (por ejemplo `ecoinvest`, letras minúsculas/dígitos/guiones, comenzando y terminando en alfanumérico). Revisar cuotas y presupuesto.
2. Disponer de Docker Linux, Azure CLI/Bicep y acceso al registry elegido. No se han diagnosticado ni instalado estas herramientas en esta actividad.
3. **Seleccionar y verificar una imagen Linux Oracle JDK 17.0.12**, preferiblemente por digest. `JAVA_IMAGE` es obligatorio: no se inventó un tag ni se cambió a Temurin para ejecutar Maven o la aplicación. La etapa Maven solo aporta su distribución binaria. El acceso/licencia de la imagen Oracle, utilidades de shell y CA deben verificarse antes de construir. Si no se consigue esa imagen, detenerse y aprobar una alternativa de versión/distribución explícitamente.
4. Verificar disponibilidad y arquitectura de las imágenes base Node/Nginx/Maven declaradas; resolver y fijar sus digests para una release reproducible, y revisar vulnerabilidades. Las bases no fueron descargadas o escaneadas todavía.
5. La cuenta que despliega foundation necesita permisos para crear recursos **y asignar roles RBAC**. Para publicar imágenes necesita `AcrPush` o equivalente. El operador que introduce la clave en Key Vault necesita permisos de escritura de secretos; no se concede ese acceso automáticamente al usuario.
6. Confirmar si el portal será público o requerirá autenticación de usuarios. La plantilla actual deja el frontend público; las consultas manuales y el allowlist **no son autenticación ni protección contra abuso**. Antes de publicarlo ampliamente, evaluar autenticación de Container Apps y control de frecuencia/cuotas. No se ha añadido un sistema de usuarios.

## 4. Configuración y secretos

Frontend compilado:

```text
VITE_API_MODE=http
VITE_API_BASE_URL=same-origin
VITE_HTTP_FEATURES=monitor,consultoras,dashboard,explorador
```

Vite integra estas variables durante el build. Cambiar variables de Container Apps después no reconfigura el bundle; para este diseño no es necesario: usa siempre el origen de la página. `BACKEND_FQDN`, suministrado al contenedor Nginx por Bicep, solo configura el proxy al arrancar. No contiene credenciales.

Backend:

```text
SPRING_PROFILES_ACTIVE=azure
SENACE_BASE_URL=https://datosabiertos.senace.gob.pe/Api/datastreams
SENACE_AUTH_KEY=<referencia a secreto Azure, nunca valor en código>
```

Después de foundation, crear desde el **portal de Key Vault** el secreto llamado `senace-auth-key`, sin compartir su valor en la conversación ni colocarlo en parámetros CLI. La plantilla apps usa una referencia sin versión a ese secreto. No enviar `backend/.env.apis.local` a Docker/Azure ni leerlo para construir imágenes.

La identidad backend tiene `AcrPull` sobre ACR y `Key Vault Secrets User` sobre el vault dedicado. La identidad frontend tiene únicamente `AcrPull`: no puede leer la credencial SENACE. ACR no tiene usuario administrador habilitado. No se imprimen claves de Log Analytics en outputs.

Key Vault usa endpoint público con autorización RBAC, no acceso anónimo; private endpoints/firewall quedan fuera de esta preparación y requieren diseño de red si la organización los exige. La protección contra purga implica retención incluso al eliminar recursos: considerar el costo y la recuperación.

## 5. Aislamiento y salud

El perfil Azure excluye las tres autoconfiguraciones de persistencia. `ddl-auto:none` es una defensa adicional y **no sustituye esas exclusiones**. No montar ni copiar SQLite; no activar perfiles extra o variables que anulen el aislamiento. El test nuevo comprueba las exclusiones antes de inicializar beans y rechaza infraestructura persistente.

Backend: probes `/actuator/health/liveness` y `/actuator/health/readiness`, puerto 8081. Frontend: `/healthz`, puerto 8080. La sonda de frontend comprueba Nginx, no hace una consulta de negocio. Las sondas backend no deben consultar SENACE/BCRP; se prepara una prueba que verifica ausencia de interacción con esos bordes.

El startup backend admite aproximadamente 240 s antes de superar el umbral. Backend tiene inicialmente 1 CPU/2 GiB y una única réplica; frontend 0,25 CPU/0,5 GiB, entre 0 y 2 réplicas. Son parámetros iniciales, no un dimensionamiento medido. Una réplica mínima backend genera consumo continuo. ACR, Key Vault y logs también pueden generar cargos.

El transporte SENACE mantiene 6 MiB, 5 s de conexión, 10 s de inactividad y 25 s totales; BCRP conserva sus límites actuales. Nginx espera hasta 45 s de inactividad de respuesta para la orquestación secuencial, no reintenta y conserva 502/503 y headers de procedencia. Ese timeout Nginx no se presenta como plazo total HTTP nuevo.

Las métricas Resilience4j viven en cada proceso y se pierden al reiniciar/reemplazar una réplica. No escalar backend sin evaluar esta semántica. CLOSED y probes UP no certifican disponibilidad de proveedores. La readiness de frontend no garantiza que el backend o SENACE estén disponibles.

CORS wildcard permanece en el controlador interno. El navegador consume el mismo origen, y el proxy retira los headers de autorización CORS permisivos; no se amplía la política global del controlador. El backend no se debe cambiar a ingress externo sin una revisión independiente.

## 6. Validaciones propuestas: todavía no ejecutadas

Desde `frontend/`, con autorización independiente:

```powershell
npm run typecheck
npm run test:api
npm run build
```

Desde `backend/`, con Oracle JDK 17.0.12 y autorización independiente:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -o '-Dtest=AzureProfileTest' test
```

Maven escribe `target/`, genera OpenAPI y compila tests. El test seleccionado usa MVC en memoria y transportes simulados; no escucha un puerto, no requiere claves ni abre SQLite. Si faltan dependencias en caché, detenerse. Su preparación no constituye prueba ejecutada ni sustituye probar los contenedores.

Antes de ejecutar Maven verificar que no hay overrides de perfiles/persistencia, agentes o listeners locales que puedan alterar el test. No cargar el archivo local de claves para estas pruebas.

Validación de Bicep, únicamente si CLI/Bicep ya están disponibles y se autoriza:

```powershell
az bicep build --file infra/azure/foundation.bicep --stdout
az bicep build --file infra/azure/apps.bicep --stdout
```

La compilación de plantillas verifica sintaxis/tipos según la versión instalada; no comprueba cuotas, permisos, disponibilidad de imágenes o conectividad. Azure puede requerir descarga inicial de Bicep: no permitir instalaciones/red por implicación.

## 7. Secuencia de despliegue: solo guía, requiere autorización posterior

Los comandos de esta sección **no fueron ejecutados**. Nunca introducir la clave SENACE en ellos. El primer despliegue debe hacerse en un grupo de recursos dedicado para facilitar reversión.

1. Confirmar cuenta/suscripción, región, permisos, presupuesto y decisión de acceso público. Los providers necesarios son Microsoft.App, Microsoft.OperationalInsights, Microsoft.ContainerRegistry, Microsoft.KeyVault y Microsoft.ManagedIdentity; registrar cualquiera que falte requiere autorización.
2. Crear el grupo y desplegar foundation. Ejemplo desde la raíz, después de definir `$resourceGroup`, `$azureRegion` y `$deploymentPrefix`:

```powershell
az group create --name $resourceGroup --location $azureRegion
az deployment group create --resource-group $resourceGroup --name ecoinvest-foundation --template-file infra/azure/foundation.bicep --parameters prefix=$deploymentPrefix
```

3. Registrar los outputs no secretos: ACR, vault y entorno. Crear `senace-auth-key` mediante el portal con permisos apropiados. Esperar propagación RBAC; si falla autorización, detenerse y corregir permisos, sin reintentar consultas oficiales.
4. Construir imágenes Linux con un tag de release único, después de aprobar red/builds. Las siguientes variables representan nombres y referencias públicas, no secretos:

```powershell
docker build --platform linux/amd64 --build-arg "JAVA_IMAGE=$oracleJavaImage" -t "$registryServer/ecoinvest/backend:$releaseTag" ./backend
docker build --platform linux/amd64 -t "$registryServer/ecoinvest/frontend:$releaseTag" ./frontend
```

Los builds descargan bases/dependencias y Maven genera el JAR/OpenAPI. El Dockerfile backend omite tests: **la aprobación del build requiere haber ejecutado las validaciones por separado**. No ejecuta Spring Boot ni accede a proveedores durante la construcción. Usar exclusivamente los contextos `./backend` y `./frontend`, no la raíz.

5. Revisar/scannear imágenes y ejecutar pruebas locales de contenedor con autorización. Arrancar backend con perfil azure y sin clave inicialmente; comprobar probes y Monitor, sin consultar proveedores. Para probar Nginx→backend por TLS local hace falta un destino/certificado de prueba confiable; no desactivar verificación TLS para que pase. La prueba real de ese salto puede hacerse posteriormente en Azure con Monitor, que no usa proveedores.
6. Publicar imágenes al ACR creado, con autorización:

```powershell
az acr login --name $registryName
docker push "$registryServer/ecoinvest/backend:$releaseTag"
docker push "$registryServer/ecoinvest/frontend:$releaseTag"
az deployment group create --resource-group $resourceGroup --name ecoinvest-apps --template-file infra/azure/apps.bicep --parameters prefix=$deploymentPrefix imageTag=$releaseTag
```

7. El output `frontendUrl` será el enlace público; el Dashboard se abre añadiendo `/#/dashboard`. No existe URL Azure verificada todavía. El FQDN interno del backend se inyecta en Nginx, no en el bundle.
8. Verificar revisiones/probes/logs sanitizados y realizar una consulta manual a Monitor desde el navegador. Navegar entre las cuatro vistas no debe ejecutar consultas oficiales automáticamente. Comprobar tablas, headers y errores con controles locales antes de autorizar datos reales.
9. Solicitar presupuesto específico para consultas oficiales: Consultoras implica un GET SENACE; Dashboard puede implicar un GET SENACE y otro BCRP. Abrir Explorador reutiliza el resultado sin GET. No repetir automáticamente fallos o timeouts.

## 8. Reversión y límites

- No se modificaron archivos locales `.env` ni el modo de ejecución `npm run dev` existente. El build Docker no los recibe.
- Antes de un despliegue nuevo conservar tag/digest de release anterior. Ante fallo, detener nuevas consultas y volver a la revisión/imagen verificada anterior; el primer despliegue no tendrá revisión histórica recuperable.
- No eliminar grupos, vaults o roles automáticamente. Cualquier limpieza requiere autorización y revisión del grupo dedicado; Key Vault con purge protection tiene retención propia.
- No se implementaron autenticación de usuarios, rate limiting, WAF, dominio personalizado, CI/CD, alertas, backups o persistencia. Revisar antes de exposición productiva amplia.
- Los últimos intentos locales SENACE comunicaron timeout de inactividad/plazo. Azure no garantiza corregir ese fallo externo ni demuestra validez de la credencial por sí mismo.
- Cobertura oficial NO_VERIFICADA; coordenadas/CRS no certificados; memoria proporcional a datos cargados; auditoría WCAG completa pendiente. Las capacidades frontend no impiden que un usuario invoque directamente una ruta pública.
- Estado actual: **preparación implementada, validación y despliegue pendientes**. No equivale a certificación productiva ni promesa de despliegue sin incidencias.

## 9. Referencias oficiales

- [Ingress Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/ingress-overview).
- [Comunicación dentro del entorno](https://learn.microsoft.com/en-us/azure/container-apps/connect-apps).
- [Health probes](https://learn.microsoft.com/en-us/azure/container-apps/health-probes).
- [Pull ACR con identidad administrada](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull).
- [Referencia Bicep Container Apps 2025-01-01](https://learn.microsoft.com/en-us/azure/templates/microsoft.app/2025-01-01/containerapps).
- [Imagen oficial Nginx y plantillas envsubst](https://hub.docker.com/_/nginx).

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest dispone ahora de archivos de preparación Docker/Azure: frontend mismo origen con proxy Nginx TLS a backend interno, perfil azure sin persistencia, identidades separadas, Key Vault, ACR, probes y Bicep por etapas. Java permanece Oracle 17.0.12; debe proporcionarse una imagen verificable mediante JAVA_IMAGE. No se han ejecutado pruebas/builds/despliegues ni consultas oficiales. Siguiente paso: autorizar validaciones aisladas, resolver imagen Oracle y herramientas disponibles; después revisar costos/permisos/acceso público y autorizar recursos/builds por separado. No repetir las fases SENACE/BCRP ya implementadas.
