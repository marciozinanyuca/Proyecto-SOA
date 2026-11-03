---
name: ecoinvest-security
description: "Revisar credenciales, configuración local o nube, logs, TLS y exposición REST de EcoInvest. Usar para cambios o auditorías de seguridad pertinentes, no como auditoría completa obligatoria de cualquier tarea."
---

# Seguridad de configuración y servicios

## Alcance de la revisión

Identificar primero qué secreto, flujo o endpoint está involucrado y qué lectura está autorizada. El [estado del proyecto](../../../documentacion/ESTADO_ECOINVEST_PARA_GPT_2026-11-02.md) contiene contexto de configuración sin necesidad de abrir el archivo local de credenciales.

## Credenciales y Git

- Verificar presencia, origen de configuración y nombres de claves sin imprimir valores. No pedir tokens en conversación. Si una lectura está autorizada para autenticar una consulta, usar el valor en memoria sin incorporarlo al comando literal o al resultado.
- Antes de proponer un archivo local, comprobar existencia, seguimiento con git ls-files y exclusión con git check-ignore. Un .gitignore no elimina del historial un archivo registrado ni cifra su contenido.
- backend/.env.apis.local es una convención local, no una configuración productiva activa. No asumir que las variables allí definidas coinciden con propiedades Spring o que el proceso las ha importado.
- Para nube, proponer inyección de configuración por el servicio de alojamiento; no incorporar el archivo local a la imagen o repositorio. No inventar una API key para un proveedor público que no la requiere.
- Si se encuentra una credencial expuesta, describir el lugar y el riesgo sin reproducirla. Proponer corrección o rotación proporcional; no borrar historial, cambiar credenciales o modificar configuración remota automáticamente.

## Logs, errores y red

- Revisar logs normales y caminos de error: RestTemplate y excepciones anidadas pueden incluir URL autenticada o cuerpo. Evitar tanto la filtración como una sanitización que impida distinguir categorías de fallo.
- No imprimir Authorization, cookies, Set-Cookie, JWT, mensajes sin sanear o archivos de entorno completos. Preferir código HTTP, tipo de excepción, tamaño y clasificación del contenido.
- Mantener validación TLS y hostname. Distinguir truststore/JDK de HTTP o error de JSON sin desactivar seguridad, importar certificados o instalar herramientas incidentalmente.
- Restringir destinos y redirecciones autenticadas; no simular navegadores, reutilizar sesiones o eludir controles del proveedor.

## Endpoints

Revisar según el encargo CORS, validación de parámetros, endpoints genéricos, límites, errores públicos y exposición de métricas. Distinguir un riesgo confirmado de una posibilidad; presentar corrección mínima y regresión esperable, sin ampliar una tarea pequeña a un rediseño de autenticación.

## Entrega

Comunicar hallazgos con ubicación y evidencia no sensible, impacto y mitigación propuesta/aplicada. Enumerar qué configuración se verificó realmente y qué sigue sin probar; nunca adjuntar el archivo local de credenciales como evidencia.
