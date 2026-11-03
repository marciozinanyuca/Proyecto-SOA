---
name: ecoinvest-api-integration
description: "Investigar o implementar clientes SENACE y BCRP de EcoInvest con autenticación documentada, JSON real, procedencia y cobertura verificables. No concede permiso de red ni define reglas de negocio ausentes."
---

# Integración de APIs externas

## Evidencia a consultar

Leer el cliente afectado en [client/](../../../backend/src/main/java/com/ecoinvest/soa/client/) y solo el reporte pertinente del [índice](../../../documentacion/README.md). El [estado de noviembre de 2026](../../../documentacion/ESTADO_ECOINVEST_PARA_GPT_2026-11-02.md) documenta una respuesta SENACE array comunicada por el usuario, no un fixture ni un esquema universal.

## Antes de consultar o modificar

- Diferenciar documentación oficial, observaciones reales, mocks y suposiciones. Confirmar dominio, ruta y mecanismo de autenticación antes de usar una credencial; no probar combinaciones automáticamente.
- Definir presupuesto de solicitudes, tamaño, timeout, redirecciones y condición de parada conforme al permiso vigente. Si no hay paginación documentada, explicar el riesgo de solicitar el conjunto completo.
- Una variable local no garantiza herencia al proceso de herramientas; comprobar solo presencia cuando sea necesario. Un archivo .env no se carga por existir. No repetir indefinidamente reinicios sin nueva evidencia.

## Procesar la respuesta

- Clasificar transporte/TLS, estado HTTP, formato, error semántico, vacío y parcial por separado. HTTP 200 y application/json no prueban datos válidos o autenticación obligatoria.
- Verificar raíz objeto/array, envoltorios, nombres de campos, tipos, nulos y cardinalidad antes de diseñar DTOs. No deserializar como Map por conveniencia si la evidencia muestra un array.
- Mantener el cuerpo y los datos personales fuera de logs. Registrar metadatos seguros, esquema limitado y agregados necesarios; no leer o imprimir configuración ajena al recurso.
- No alterar globalmente RestTemplate u ObjectMapper compartidos para resolver un caso de un solo proveedor. Justificar configuración local del lector/cliente.
- Conservar BigDecimal y período exacto cuando el dato sea económico. No inventar agregaciones, equivalencias sectoriales, fechas, moneda, inversión o coordenadas.
- Separar filas de procedencia, estado y cobertura. Vacío oficial, error, simulación y datos parcialmente recibidos deben poder distinguirse aunque no haya primera fila.
- Declarar si se recibió el cuerpo completo, si existe paginación y qué universo se comprobó. No afirmar completitud del dataset por contar un array.

## Cierre del trabajo

Entregar endpoint sin credencial, mecanismo verificado, evidencia de esquema, diccionario con derivados/ausentes/decisiones y propuesta mínima de implementación. No llamar al proveedor de nuevo solo para obtener una salida más cómoda si la evidencia existente basta. Solicitar autorización para acciones que excedan el presupuesto o para persistir muestras nuevas.
