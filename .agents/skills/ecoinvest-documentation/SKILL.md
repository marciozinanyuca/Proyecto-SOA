---
name: ecoinvest-documentation
description: "Crear o actualizar auditorías, decisiones, reportes por fases y contexto de continuidad EcoInvest para ChatGPT. No implica cambios productivos, reorganizaciones o actualización de todos los documentos relacionados."
---

# Documentación y continuidad de EcoInvest

## Encontrar la evidencia vigente

Partir del [índice](../../../documentacion/README.md), localizar el reporte de continuidad pertinente más reciente y contrastar sus afirmaciones con el código o los resultados disponibles. El [estado del 02/11/2026](../../../documentacion/ESTADO_ECOINVEST_PARA_GPT_2026-11-02.md) es antecedente útil, no un estado que deba copiarse indefinidamente.

## Procedimiento

- Identificar entregable, destino, permiso de creación/actualización y existencia previa. No sobrescribir un reporte ni actualizar el índice por asociación si no está incluido en la tarea.
- Ordenar fases por contenido y evidencia, no por mtime o nombre. Distinguir último diagnóstico, última implementación, última prueba y última autorización pendiente.
- Etiquetar hechos de código, reportes, pruebas previas, resultados aportados por el usuario e hipótesis. Una salida pegada por el usuario no es un artefacto capturado directamente por el agente.
- Añadir hallazgos de forma acumulativa cuando se actualice un reporte histórico. Conservar intentos fallidos, resultados posteriores, fechas y límites, sin hacer pasar el documento original por conocimiento retrospectivo.
- Citar rutas actuales y líneas cuando sean útiles. Resumir comandos y evidencia sin cuerpos, credenciales o propiedades de entorno sensibles. No adjuntar XML completos o archivos locales autenticados.
- En movimientos autorizados, corregir solo destinos afectados y validar enlaces; no reescribir cifras o conclusiones. Desde este SKILL.md la raíz está a ../../../; los enlaces en el reporte deben calcularse desde su propio destino.

## Contenido de continuidad

Entregar contexto autocontenido: identidad, arquitectura real, fases cerradas y alcance, estado por proveedor, simulación pendiente, última acción, resultados, archivos relevantes, decisiones y siguiente tarea concreta. Distinguir código existente de funcionalidad probada, y cuerpo HTTP completo de dataset completo.

Para informes de pruebas, conservar runtime, fecha, comando, resultados y frontera de integración. Para un plan, especificar archivos y criterios de aceptación sin ejecutarlo por escribirlo. No convertir un pendiente histórico resuelto en una nueva tarea ni perder decisiones todavía abiertas.

## Verificación final

Comprobar archivo creado/actualizado, enlaces locales y ausencia de valores sensibles en los archivos del alcance. Informar ruta absoluta/relativa y límite de validación. Actualizar el índice solo si se autorizó y el estado registrado realmente cambió. No afirmar pruebas, consultas o revisión de capturas que no ocurrieron.
