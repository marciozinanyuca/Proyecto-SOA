---
name: ecoinvest-testing
description: "Diseñar, ejecutar con permiso o evaluar pruebas Java/Spring de EcoInvest, incluyendo RestTemplate, MockMvc y Resilience4j. No sustituye observaciones reales por mocks ni activa consultas externas por defecto."
---

# Pruebas y evidencia de ejecución

## Elegir la frontera

Consultar [tests existentes](../../../backend/src/test/java/com/ecoinvest/soa/) y el [POM](../../../backend/pom.xml). Para antecedentes específicos AOP, usar [reporte Resilience4j](../../../documentacion/02_Fase2_BCRP/REPORTE_FASE2C_RESILIENCE4J.md) sin dar por activa una configuración futura solo por el resultado histórico.

| Objetivo | Prueba apropiada |
|---|---|
| Parser, fechas, decimales y ausencia | Casos unitarios con muestras pequeñas y origen identificado |
| Conversores y errores de RestTemplate | MockRestServiceServer enlazado antes de invocar; transporte sin red |
| Contrato/serialización y rutas | MockMvc con dependencias controladas; distinguir standalone y contexto |
| Interceptación Resilience4j | Bean administrado por Spring y aspecto real en contexto mínimo |
| Acceso al proveedor | Integración real separada, opt-in y con presupuesto HTTP autorizado |

## Preparación y ejecución

- Confirmar alcance y autorización de comandos antes de ejecutarlos. Maven offline puede generar OpenAPI y escribir target/; una prueba live puede hacer red aunque Maven use -o.
- Para pruebas autorizadas, seleccionar Oracle JDK 17.0.12 en el proceso y comprobar Java de Maven y JVM del test. release 17 no demuestra runtime 17.
- Evitar arranque productivo: datasource/JPA y ddl-auto pueden tocar SQLite. Elegir contexto mínimo sin component scanning amplio o exclusiones explícitas justificadas. No asumir que webEnvironment NONE desactiva persistencia.
- Preparar fixtures solo con evidencia o mutaciones técnicas claramente identificadas. Una respuesta simulada con origen API_DIRECTA no acredita datos externos reales.
- Ejecutar el conjunto más pequeño que compruebe el cambio y las regresiones relevantes. Ampliar o repetir solo por cambios, fallos o dudas concretas.

## Resilience4j

Comprobar proxy/advisor, llamada por el bean, eventos, métricas, fallback, rechazo sin HTTP y recuperación. Una llamada directa al fallback no prueba interceptación. Leer configuración efectiva y política de ventana antes de suponer umbrales. No forzar estados manualmente y presentarlos como recuperación natural. Separar excepciones de retornos normales de ausencia.

## Resultado verificable

Registrar comando, directorio, Java, fecha, clase/casos, resultados y artefactos locales. No sumar suites de distintas ejecuciones como una nueva suite. Separar primer fallo y ejecución final; no ocultar una expectativa incorrecta del test como defecto productivo. Si no hubo ejecución, declarar pruebas preparadas o pendientes.

Los reportes target/ son volátiles y sus XML pueden incluir detalles de entorno; extraer solo evidencia necesaria. Limpiar archivos temporales únicamente dentro del alcance aprobado y preservar tests permanentes. No declarar end-to-end por MockMvc, mocks o una consulta aislada.
