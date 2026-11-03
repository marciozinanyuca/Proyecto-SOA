# EcoInvest — Configuración de Agent Skills para Codex

**Fecha:** 2 de noviembre de 2026.  
**Repositorio:** `D:\Documentos\GitHub\Proyecto-SOA`.  
**Codex inspeccionado:** CLI 0.166.0.  
**Alcance:** seis skills de instrucciones y AGENTS.md; sin cambios funcionales.  
**Fase funcional conservada:** 3A.2 SENACE. No se inició Fase 3B.

## 1. Objetivo, autorizaciones y procedimiento

Se diseñó un conjunto de skills para arquitectura, integración, seguridad, pruebas, documentación y frontend. El usuario aprobó primero los siete archivos de instrucciones y las validaciones locales. La creación de este reporte recibió autorización independiente posterior a comunicar los resultados.

Se utilizó skill-creator disponible en la instalación local: diseño con descripciones discriminantes, instrucciones concisas, referencias progresivas y validación mediante quick_validate.py. No se usó el inicializador porque habría generado metadatos UI adicionales innecesarios para la estructura mínima aprobada. No se añadieron scripts, assets, referencias copiadas, dependencias, conectores MCP o archivos agents/openai.yaml.

Antes de crear los archivos se comprobó que no existían AGENTS.md ni .agents/skills/ en el repositorio. Se revisaron el estado de continuidad, índice, cierre BCRP, diagnóstico SENACE, POM, contrato y componentes relevantes sin leer credenciales.

La guía skill-creator se encontraba disponible localmente y fue proporcionada en la conversación. No se consultó documentación externa ni se afirma haber verificado por HTTP una versión más reciente del estándar durante esta tarea.

## 2. Archivos creados

| Archivo | Responsabilidad |
|---|---|
| [AGENTS.md](../AGENTS.md) | Reglas comunes de identidad, alcance, contrato, versiones, evidencia y permisos |
| [ecoinvest-soa-architecture/SKILL.md](../.agents/skills/ecoinvest-soa-architecture/SKILL.md) | Límites SOA, decisiones de arquitectura y compatibilidad OpenAPI |
| [ecoinvest-api-integration/SKILL.md](../.agents/skills/ecoinvest-api-integration/SKILL.md) | Investigación y adaptación de clientes externos con evidencia real |
| [ecoinvest-security/SKILL.md](../.agents/skills/ecoinvest-security/SKILL.md) | Credenciales, configuración, logs, TLS, Git y exposición REST |
| [ecoinvest-testing/SKILL.md](../.agents/skills/ecoinvest-testing/SKILL.md) | Diseño y evaluación de pruebas Java/Spring y evidencia de ejecución |
| [ecoinvest-documentation/SKILL.md](../.agents/skills/ecoinvest-documentation/SKILL.md) | Reportes acumulativos, decisiones y continuidad para ChatGPT |
| [ecoinvest-frontend/SKILL.md](../.agents/skills/ecoinvest-frontend/SKILL.md) | Frontend React/TypeScript cuando la tarea lo autorice |

Los seis SKILL.md tienen frontmatter YAML con name y description, nombres coincidentes con su carpeta y longitudes inferiores al límite del validador. No se definió una política explicit-only: se conserva la selección implícita normal. No se necesita agents/openai.yaml para este conjunto mínimo.

Estructura:

```text
AGENTS.md
.agents/skills/
  ecoinvest-soa-architecture/SKILL.md
  ecoinvest-api-integration/SKILL.md
  ecoinvest-security/SKILL.md
  ecoinvest-testing/SKILL.md
  ecoinvest-documentation/SKILL.md
  ecoinvest-frontend/SKILL.md
```

Este reporte es un octavo archivo creado con aprobación posterior; no se modificó documentacion/README.md porque su actualización no se incluyó en la autorización.

## 3. Contenido y límites de las skills

### Arquitectura

Se aplica a decisiones sobre componentes, contratos y compatibilidad, no a toda edición Java. Exige contrastar contrato y código, justificar capas nuevas por responsabilidad real y mantener independientes conceptos económicos BCRP y ambientales SENACE. Entrega impacto, archivos y criterios de aceptación sin refactorizaciones por patrón.

### Integración de APIs

Se aplica a investigación o implementación de clientes externos. Prioriza dominio y autenticación documentados, estructura objeto/array verificada, nulos, procedencia y cobertura. Separa HTTP 200 de datos válidos, cuerpo completo de universo completo y configuración disponible de configuración efectivamente cargada. No concede permiso de red ni inventa parámetros o reglas de negocio.

### Seguridad

Se aplica cuando credenciales, logs, transporte o exposición requieren revisión. No abre archivos locales autenticados como contexto por defecto. Verifica seguimiento/exclusión antes de proponer archivos de configuración; evita URL autenticada y mensajes de excepción inseguros. No transforma cualquier cambio ordinario en auditoría general ni ejecuta rotación o cambios remotos automáticamente.

### Pruebas

Selecciona frontera apropiada: unidad/parser, MockRestServiceServer, MockMvc, Spring mínimo con aspecto real o integración externa opt-in. Distingue release de runtime Java y mocks de observaciones reales. Para AOP requiere proxy, eventos, métricas y comportamiento, no solo invocación directa del fallback. No afirma ejecución por preparar comandos.

### Documentación

Se aplica a auditorías, reportes y transferencia de contexto. Ordena evidencia por contenido, preserva fallos históricos y distingue resultados del usuario de capturas directas del agente. No actualiza documentos relacionados o el índice por implicación. Esta skill fue seleccionada y leída para redactar el presente reporte dentro del alcance autorizado.

### Frontend

Se aplica a trabajo expresamente autorizado en React/TypeScript. Verifica primero si existe una aplicación y sigue el contrato real. Contempla Dashboard Analítico, Explorador Geográfico, Consultoras Ambientales y Monitor de Salud SOA. No crea un scaffold, instala paquetes o cambia el backend por mencionar una pantalla. La UI debe distinguir simulación, ausencia, error, vacío y parcial, sin exponer la clave del proveedor en el navegador.

## 4. Reglas comunes de AGENTS.md

AGENTS.md concentra las reglas compartidas para evitar repetirlas seis veces:

- Identidad EcoInvest y alcance SENACE + BCRP.
- Mantener Java 17/JDK 17.0.12 y Spring Boot 3.2.4 salvo una tarea de migración autorizada.
- OpenAPI fuente y compatibilidad de los cuatro endpoints existentes.
- No editar manualmente target/.
- Conservar el cierre BCRP salvo evidencia nueva, dependencia demostrada o encargo explícito.
- No presentar simulaciones o sustituciones ficticias como datos reales.
- Respetar diagnóstico restringido, alcance y autorizaciones vigentes sin solicitar repetidamente lo ya aprobado.
- No cargar, registrar o divulgar credenciales innecesariamente; archivo local no equivale a carga automática por Spring.
- Preservar cambios previos, no avanzar de fase automáticamente y mantener documentación por fases.
- Distinguir hechos, reportes, pruebas, resultados comunicados, hipótesis y decisiones.

No impone prohibiciones permanentes que anulen una futura tarea explícitamente autorizada. Las skills tampoco conceden permisos de ejecución o amplían el encargo del usuario.

## 5. Validaciones realizadas

### 5.1. Formato oficial local

Se ejecutó el validador disponible en:

```text
C:\Users\adrie\.codex\skills\.system\skill-creator\scripts\quick_validate.py
```

Forma de invocación aplicada a cada carpeta:

```powershell
$env:PYTHONUTF8 = '1'
python 'C:/Users/adrie/.codex/skills/.system/skill-creator/scripts/quick_validate.py' `
  '.agents/skills/ecoinvest-documentation'
```

Resultado de las seis: Skill is valid! No se instalaron dependencias; PyYAML ya estaba disponible. La variable UTF-8 se limitó al proceso de validación para evitar problemas de lectura de español en Windows.

| Skill | Caracteres de description | Resultado |
|---|---:|---|
| ecoinvest-soa-architecture | 178 | Válida |
| ecoinvest-api-integration | 204 | Válida |
| ecoinvest-security | 207 | Válida |
| ecoinvest-testing | 208 | Válida |
| ecoinvest-documentation | 214 | Válida |
| ecoinvest-frontend | 202 | Válida |

### 5.2. Estructura, enlaces y contenido sensible

Se comprobaron los siete archivos aprobados, nombres de carpetas, frontmatter con las dos claves previstas y 18 enlaces locales. No hubo destinos faltantes. No se detectaron patrones JWT o asignaciones literales de credenciales en esos archivos nuevos. Esta inspección acotada no es un escaneo exhaustivo de secretos del repositorio.

Las rutas de código/documentación de cada skill parten de su carpeta mediante ../../../. No se copiaron los reportes como recursos ni se enlazó el archivo local de credenciales como documento que deba abrirse.

### 5.3. Conservación del proyecto

Se capturaron y compararon hashes SHA-256 de archivos previos del código, pruebas, frontend/documentación existentes y archivos relevantes como POM, wrapper, gitignore y script diagnóstico, excluyendo credenciales. La comparación resultó idéntica antes de crear este reporte.

git diff --check no encontró errores de espacios en los cambios registrados. Emitió advertencias de normalización LF/CRLF en archivos modificados de fases anteriores; no se reinterpretaron como fallos de las skills.

Las siete instrucciones aparecieron como archivos nuevos sin seguimiento. No se ejecutaron git add, commits, cambios de rama o revertidos. Se conservaron las modificaciones preexistentes.

### 5.4. Incidencia del entorno

El helper del sandbox falló al iniciar procesos de validación con helper_unknown_error y setup refresh had errors. Las lecturas y validaciones necesarias se repitieron fuera del sandbox con aprobación. No fue un fallo YAML ni de las instrucciones; no se cambió la configuración del sandbox para resolverlo.

La consulta de versión anterior emitió una advertencia de directorio personal para alias PATH, aunque devolvió codex-cli 0.166.0. La ayuda local funcionó y no ofrece un subcomando superior dedicado a listar skills. No se inventó un comando skills list ni se inició el app-server o una sesión de modelo para probarlo.

## 6. Descubrimiento, selección e invocación: distinciones

| Verificación | Resultado y evidencia |
|---|---|
| Estructura compatible y frontmatter | Verificados con validador local |
| Lectura por ruta | Verificada para ecoinvest-documentation |
| Descubrimiento por el entorno Codex actual | Confirmado: tras la creación, el catálogo de skills de esta sesión incluyó las seis con sus nombres, descripciones y rutas de repositorio |
| Selección documental en esta tarea | Realizada: se eligió ecoinvest-documentation, se leyó su archivo y se aplicó al reporte autorizado |
| Selección automática independiente de las seis | No evaluada mediante una batería de sesiones nuevas |
| Invocación explícita independiente de cada skill | Ejemplos preparados; no se ejecutaron seis inferencias de modelo |
| Calidad de decisiones en tareas reales futuras | Requiere observación de uso y ajustes respaldados por resultados |

La respuesta anterior a la creación de este reporte indicaba que el descubrimiento estaba pendiente. Esa afirmación queda actualizada por la incorporación posterior de las seis skills al catálogo disponible de la sesión. No se atribuye este cambio a un reinicio que no se observó.

La selección automática habilitada es una capacidad, no garantía de activación para cualquier texto. Las descripciones fueron revisadas con escenarios positivos y exclusiones; no se presenta esa revisión como benchmark automático ejecutado.

No se inició una nueva sesión de modelo ni se realizaron llamadas de red para evaluar invocaciones: la tarea prohibía HTTP. Una evaluación independiente futura necesitará un alcance y autorización adecuados.

## 7. Ejemplos de invocación seguros

Los siguientes prompts pueden utilizarse en una sesión de Codex situada en el repositorio. Son ejemplos, no operaciones ejecutadas por este reporte.

```text
$ecoinvest-soa-architecture
Revisa en solo lectura si el controlador y OpenAPI conservan los cuatro endpoints.
No modifiques archivos ni ejecutes Maven o HTTP.
```

```text
$ecoinvest-api-integration
Contrasta el esquema CarteraProyectos comunicado en el reporte con SenaceClient.
Propón el mapeo pendiente sin consultas externas, credenciales o cambios.
```

```text
$ecoinvest-security
Revisa solo los caminos de log y error de SenaceClient.
No abras archivos de credenciales ni ejecutes solicitudes.
```

```text
$ecoinvest-testing
Diseña pruebas con MockRestServiceServer para una respuesta raíz array SENACE.
Entrega únicamente el plan; no crees archivos, ejecutes Maven ni uses red.
```

```text
$ecoinvest-documentation
Identifica el estado vigente por fases a partir de los reportes existentes.
Responde en la conversación; no actualices archivos ni ejecutes HTTP.
```

```text
$ecoinvest-frontend
Propón estados de carga, vacío, error y procedencia para el dashboard.
No crees frontend, instales dependencias o cambies el contrato.
```

Si una nueva sesión no ofrece una skill, comprobar directorio del repositorio, nombre y archivo antes de modificar configuración global. La lectura manual por ruta es distinta del descubrimiento nativo y debe describirse así.

## 8. Mantenimiento y conflictos evitados

- Arquitectura decide fronteras y compatibilidad; integración investiga contratos del proveedor y representación de datos.
- Seguridad concentra reglas de credenciales; no convierte tareas ordinarias en hardening general.
- Testing produce o evalúa evidencia; documentación conserva su origen y alcance sin inventar ejecuciones.
- Frontend no inicia una fase funcional ni amplía autorización backend.
- Los estados y cantidades históricas se consultan en reportes, no se fijan como hechos eternos en las skills.
- No modificar skills globales, skill-creator o configuración de usuario como efecto de ajustar una skill del proyecto.
- Ante un error real de selección o comportamiento, corregir el límite concreto; no acumular prohibiciones universales por cada incidente.

Los nuevos cambios deben validar nuevamente la skill afectada, referencias y límites. Añadir scripts o referencias auxiliares solo si una necesidad concreta lo justifica y está autorizada. No es necesario cargar todas las skills en cada tarea.

## 9. Estado funcional conservado y siguientes pasos

Esta tarea mejora instrucciones de ingeniería; no completa SENACE. La fase activa continúa siendo 3A.2. La respuesta CarteraProyectos reportada por el usuario contiene array de 2.567 elementos, mientras el cliente Java sigue esperando Map y construyendo SIMULADO. ConsultorasAmbientales continúa sin muestra verificada. La configuración local existe, pero su lectura no fue integrada al script o backend mediante esta tarea.

No hubo HTTP, Maven, pruebas de integración, servidores, instalaciones, consultas SQLite o commits. No se modificaron BCRP, Resilience4j, SenaceClient, EcoInvestController, OpenAPI o frontend. Tampoco se actualizaron los reportes históricos o el índice.

Próximas opciones, sujetas a su propia autorización:

1. Usar las skills en un análisis de solo lectura de continuidad para verificar su utilidad con una tarea real.
2. Si se desea una batería independiente de selección/invocación, definir escenarios y permitir explícitamente las sesiones de modelo necesarias, sin habilitar por ello red a proveedores o cambios productivos.
3. Retomar Fase 3A.2 conforme al estado del proyecto: lectura explícita y segura de configuración diagnóstica, presupuesto aprobado de datos y diccionario real antes de Fase 3B.

## RESULTADO PARA REVISIÓN CON CHATGPT

EcoInvest dispone de seis skills de repositorio en .agents/skills/ y un AGENTS.md raíz. Se aplicó skill-creator con estructura mínima name/description y procedimientos específicos. Las seis pasaron quick_validate.py, los 18 enlaces locales existen y no se detectaron patrones de credenciales en las instrucciones nuevas. Los archivos previos conservaron sus hashes.

Codex CLI inspeccionado es 0.166.0. El catálogo disponible de esta sesión ya muestra las seis skills: descubrimiento confirmado en este entorno. ecoinvest-documentation fue seleccionada y leída para generar este reporte. No se ejecutaron sesiones independientes para probar selección automática o invocación explícita de cada una; no declarar esa batería completa ni efectividad universal a partir de una validación estática.

No hay cambios funcionales ni autorización implícita para HTTP, Maven, SQLite o Fase 3B. La fase activa sigue siendo 3A.2 SENACE. Compartir este reporte con ChatGPT permite preparar el siguiente prompt usando las skills pertinentes sin repetir las fases BCRP completadas o exponer credenciales.
