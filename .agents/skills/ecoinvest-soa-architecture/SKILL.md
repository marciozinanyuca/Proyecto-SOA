---
name: ecoinvest-soa-architecture
description: "Diseñar o revisar arquitectura SOA, límites de componentes y compatibilidad OpenAPI de EcoInvest. No aplicar a toda edición Java ni sustituir el análisis de un proveedor externo."
---

# Arquitectura SOA de EcoInvest

## Contexto dirigido

Consultar el [contrato](../../../backend/src/main/resources/openapi.yaml), [POM](../../../backend/pom.xml) y [controlador](../../../backend/src/main/java/com/ecoinvest/soa/controller/EcoInvestController.java) para verificar la implementación, no deducirla de un diagrama histórico. Para estado de fases, localizar el cierre vigente en el [índice](../../../documentacion/README.md).

## Procedimiento

1. Definir el comportamiento solicitado y los consumidores afectados. Distinguir cambio interno, adición compatible y ruptura de contrato.
2. Trazar ruta pública → controlador → cliente o servicio existente → proveedor. No introducir una capa Service solo para satisfacer un patrón: justificarla por orquestación, transformación o reglas compartidas reales.
3. Comparar contrato e implementación, incluyendo parámetros, ausencia, tipos decimales y metadatos. Si requiere cambio de contrato autorizado, definirlo antes de regenerar interfaces/modelos.
4. Mantener independientes los conceptos SENACE y BCRP: un filtro ambiental no acredita equivalencia con un sector económico. TODOS como contexto económico no implica un agregado calculado de proyectos.
5. Identificar el cambio mínimo y sus dependencias. No modificar integración validada por proximidad de archivos; explicitar cualquier necesidad de tocarla.
6. Para SQLite, diferenciar configuración declarada, persistencia implementada y efectos de arranque. Proponer aislamiento antes de una verificación que pueda escribir datos.

## Resultado esperado

Entregar decisión, alternativas relevantes, archivos afectados, impacto de compatibilidad y criterios de aceptación. Si solo se revisó código, indicar validación pendiente. Para decisiones sustanciales, proponer un registro documental dentro del alcance, sin crearlo automáticamente.

Esta skill trata límites y contratos; la investigación del JSON o autenticación del proveedor corresponde a una tarea de integración, y la revisión profunda de credenciales solo cuando sea necesaria o solicitada.
