---
name: ecoinvest-frontend
description: "Diseñar o implementar frontend EcoInvest autorizado en React y TypeScript: dashboard, mapa, consultoras y salud SOA. No inicia frontend por mencionar vistas ni cambia APIs backend sin alcance explícito."
---

# Frontend EcoInvest

## Verificar el punto de partida

Inspeccionar frontend/ antes de asumir aplicación, dependencias, router o servicio API existentes. El [estado histórico](../../../documentacion/ESTADO_ECOINVEST_PARA_GPT_2026-11-02.md) describe una carpeta vacía: verificar si esto cambió. No generar un nuevo scaffold sobre trabajo existente o instalar paquetes sin alcance autorizado.

Consultar el [contrato OpenAPI](../../../backend/src/main/resources/openapi.yaml) y las respuestas realmente disponibles. No tratar un ejemplo visual o un objeto genérico del contrato como esquema oficial exhaustivo.

## Diseñar los módulos

| Módulo | Datos y decisiones relevantes |
|---|---|
| Dashboard Analítico | /api/dashboard; filtros acordados, período y ámbito económico BCRP; ausencia y procedencia SENACE separadas |
| Explorador Geográfico | Coordenadas y cobertura verificadas de proyectos; no existe una ruta de mapa dedicada por defecto |
| Consultoras Ambientales | /api/consultoras; campos reales acordados, no trasladar el mock histórico a un modelo oficial |
| Monitor de Salud SOA | /api/estado-servicios; métricas del breaker no equivalen a disponibilidad end-to-end o respaldo SQLite |

## Implementación dentro del alcance

- Centralizar consumo HTTP y tipos del contrato en el mecanismo que tenga el proyecto, sin inventar rutas. Si faltan campos o filtros backend, describir la necesidad en lugar de cambiar el contrato incidentalmente.
- Representar carga, error, vacío, ausencia económica, parcial y SIMULADO de manera distinguible. No mostrar cero por falta de valor ni etiquetar datos simulados como oficiales.
- Mantener filtros y resultados sincronizados: cancelar o descartar respuestas obsoletas y mostrar año/período efectivos. No asumir equivalencia entre sectores SENACE y alcance BCRP.
- No exponer claves SENACE en bundles, URLs del navegador o almacenamiento cliente. La UI consume EcoInvest, no requiere el token del proveedor.
- Reutilizar componentes y tokens visuales existentes. Si no hay diseño implementado, proponer estructura responsiva y accesible con controles etiquetados, navegación por teclado y estados legibles.
- En mapas, distinguir coordenadas ausentes/inválidas, CRS y cobertura sin rellenar posiciones ficticias. No presentar conteos de una muestra como universo completo.

## Verificación y entrega

Elegir validaciones proporcionales al cambio y comandos autorizados del proyecto; no presuponer tooling inexistente. Diferenciar una revisión de tipos o test simulado de una prueba contra backend real. Informar vistas operativas, prototipos, datos simulados y limitaciones, sin certificar todo EcoInvest por completar una pantalla.
