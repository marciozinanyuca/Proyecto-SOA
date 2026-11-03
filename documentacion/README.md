# EcoInvest — Documentación técnica

EcoInvest es una plataforma de inteligencia gubernamental con arquitectura SOA que integra información ambiental de SENACE e indicadores económicos oficiales del BCRP. El backend utiliza Java 17, Spring Boot 3.2.4, Maven Wrapper y un contrato OpenAPI del que se generan interfaces y modelos. Los clientes REST consultan proveedores externos y los controladores consolidan las respuestas. El frontend React + TypeScript se desarrollará posteriormente.

## Fases y reportes

| Fase | Estado y alcance documentado | Reportes |
|---|---|---|
| Auditoría inicial | Diagnóstico histórico del repositorio, 18/10/2026 | [Auditoría técnica](00_Auditoria/AUDITORIA_PROYECTO_SOA.md) |
| Fase 1 — Estabilización | Diagnóstico e implementación; generación, compilación y 18 pruebas locales satisfactorias documentadas el 18/10/2026 | [Diagnóstico y plan](01_Fase1/REPORTE_FASE1_ECOINVEST.md), [Implementación Fase 1A](01_Fase1/REPORTE_IMPLEMENTACION_FASE1A.md) |
| Fase 2 — Diagnóstico BCRP | Análisis histórico previo al catálogo anual, 18/10/2026 | [Diagnóstico BCRP](02_Fase2_BCRP/REPORTE_FASE2_BCRP.md) |
| Fase 2A — Series oficiales | Identidad económica y alternativas verificadas documentalmente y mediante muestras oficiales, 18/10/2026 | [Validación de series](02_Fase2_BCRP/REPORTE_FASE2A_VALIDACION_BCRP.md) |
| Fase 2B — Implementación BCRP | Catálogo anual, selección temporal y trazabilidad implementados; 75 pruebas locales satisfactorias, 18/10/2026 | [Implementación Fase 2B](02_Fase2_BCRP/REPORTE_IMPLEMENTACION_FASE2B.md) |
| Fase 2C — Cliente real y resiliencia | Cerrada dentro de su alcance técnico específico: observaciones reales del catálogo y AOP en contexto mínimo, 18–19/10/2026 | [Diagnóstico HTTP](02_Fase2_BCRP/REPORTE_FASE2C_DIAGNOSTICO_HTTP.md), [Resilience4j aislado](02_Fase2_BCRP/REPORTE_FASE2C_RESILIENCE4J.md), [Cierre BCRP](02_Fase2_BCRP/REPORTE_CIERRE_FASE2C_BCRP.md) |
| Fase 3 — Integración SENACE | Diagnóstico y planificación; proyectos y consultoras siguen construidos con registros SIMULADO | [Diagnóstico SENACE](03_Fase3_SENACE/REPORTE_FASE3_SENACE.md) |
| Fase 3A / 3A.2 — Validación SENACE | En curso: documentación parcial registrada; autenticación aplicable y muestras reales pendientes de completar y documentar | [Validación oficial SENACE](03_Fase3_SENACE/REPORTE_FASE3A_VALIDACION_SENACE.md) |

## Alcance de las validaciones

Las observaciones reales BCRP abarcan Hidrocarburos para 2017 y 2018 y las otras cinco series aprobadas para 2018. Las pruebas Resilience4j verificaron proxy, fallback, apertura, rechazo y recuperación con HTTP simulado en un contexto Spring mínimo. Las ejecuciones y sus fechas se detallan en cada reporte; los resultados de suites diferentes no representan una única ejecución conjunta.

**Las pruebas aisladas y las observaciones de un cliente real no certifican la integración end-to-end del dashboard ni de toda la aplicación.** Tampoco garantizan disponibilidad permanente de los proveedores. Esa ejecución integral sigue siendo un pendiente independiente.

El reporte SENACE conserva una validación documental parcial con cuatro GET sin credenciales. No contiene muestras oficiales de proyectos o consultoras. La continuación de Fase 3A.2 debe verificar el manual y el mecanismo aplicable antes de usar una credencial temporal, respetando autorizaciones específicas para HTTP y actualización documental. No deben guardarse tokens o URLs autenticadas en reportes.

## Lectura y mantenimiento

- Los reportes son evidencias históricas. Un diagnóstico anterior puede contener limitaciones que un reporte posterior resuelve. Consultar el cierre más reciente de cada fase sin reescribir las conclusiones originales.
- Los enlaces Markdown al código y a evidencias locales se resuelven desde la ubicación del documento. Las rutas escritas como texto o en bloques de código se interpretan desde la raíz del repositorio, salvo indicación contraria.
- Las referencias de líneas corresponden al momento de inspección de cada reporte y pueden variar con cambios posteriores.
- Las evidencias enlazadas bajo backend/target/ son artefactos generados; pueden no existir en otro clon o desaparecer al limpiar Maven. El contenido resumido en los reportes conserva el contexto de esas ejecuciones.
- Para una fase nueva, añadir su reporte a la carpeta correspondiente y actualizar la tabla con estado, fecha y alcance. Conservar los nombres existentes y comprobar los enlaces relativos.

**Fecha de organización del índice:** 29/10/2026. Esta reorganización solo mueve reportes y ajusta enlaces; no constituye una nueva ejecución técnica ni actualiza automáticamente las validaciones históricas.
