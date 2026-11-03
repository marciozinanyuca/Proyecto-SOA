# EcoInvest: instrucciones del repositorio

## Identidad y continuidad

- El proyecto es EcoInvest, integración SOA de SENACE y BCRP. No añadir OEFA o ESG por referencias históricas.
- Consultar [índice documental](documentacion/README.md) y el reporte de continuidad pertinente más reciente antes de decidir qué está pendiente. [Estado del 02/11/2026](documentacion/ESTADO_ECOINVEST_PARA_GPT_2026-11-02.md) es una referencia inicial, no una autoridad inmutable sobre el código futuro.
- Al crear estas instrucciones, la fase activa es 3A.2 SENACE; Fase 3B y frontend no han comenzado. No avanzar de fase por implicación.
- Fase 2C BCRP está cerrada en su alcance técnico. No repetirla o modificar sus componentes sin evidencia nueva, una dependencia demostrada del cambio o encargo explícito.

## Alcance y autorizaciones

- Las instrucciones explícitas del usuario prevalecen sobre estas convenciones y las skills. Respetar los archivos, operaciones y límites autorizados para cada tarea; no convertir una autorización documental en autorización productiva.
- En diagnósticos restringidos, no ejecutar HTTP, Maven, pruebas, servidores, instalaciones, SQLite o commits sin aprobación específica. Una skill o un comando de ejemplo no concede esa aprobación.
- Si una operación ya está autorizada en la sesión y conserva alcance, no volver a pedir permiso por rutina. Ante una ampliación real de alcance, explicar el cambio y solicitarla antes de actuar.
- Preservar modificaciones preexistentes, incluidos archivos sin seguimiento. No cambiar de rama, descartar trabajo ni crear commits salvo encargo explícito.

## Invariantes técnicos

- Mantener Java 17, Oracle JDK 17.0.12 para ejecuciones acordadas y Spring Boot 3.2.4 por ahora. No migrar versiones o paquetes incidentalmente; una migración explícitamente autorizada se evalúa por separado.
- El contrato fuente es [openapi.yaml](backend/src/main/resources/openapi.yaml). Mantener compatibilidad de GET /api/dashboard, /api/estado-servicios, /api/consultoras y /api/senace/datastream. Un cambio incompatible necesita aprobación.
- Generar interfaces/modelos mediante Maven cuando esté autorizado; no editar manualmente backend/target/ ni otros artefactos generados.
- No asumir persistencia por encontrar un archivo SQLite o dependencias JPA. El contexto productivo puede actualizar la base por ddl-auto; las pruebas aisladas no deben abrirla por accidente.
- No presentar datos SIMULADO como oficiales ni sustituir ausencia por constantes, años vecinos o coordenadas inventadas. Conservar alcance económico, precisión y procedencia verificables.

## Credenciales y red

- No leer secretos salvo necesidad y autorización concretas. Nunca imprimirlos, copiarlos a reportes, fixtures o código ni registrar URLs autenticadas, cookies o excepciones que los incluyan.
- backend/.env.apis.local es configuración local ignorada; no cargarlo automáticamente como contexto del agente. Si una tarea autoriza usarlo, consumir únicamente las claves necesarias sin mostrar valores. No asumir que Spring Boot o un script lo cargan.
- Verificar dominio y mecanismo documentado antes de transmitir credenciales. Mantener TLS y validación de hostname; no imitar navegadores o eludir protecciones. No seguir redirecciones autenticadas sin verificar destino y autorización.
- Respetar presupuesto HTTP, límite de lectura y reintentos acordados. Un corte local no limita el trabajo del servidor ni convierte JSON truncado en muestra válida.

## Evidencia y documentación

- Mantener reportes por fases en documentacion/. No sobrescribir antecedentes o reportes existentes sin autorización; los hallazgos nuevos deben tener fecha y alcance.
- Distinguir código inspeccionado, reportes, pruebas ejecutadas, resultados comunicados por el usuario, hipótesis y decisiones pendientes. Una respuesta HTTP completa no demuestra cobertura completa del dataset.
- No declarar pruebas exitosas sin ejecución acreditada ni integración end-to-end por pruebas MockMvc, mocks o contexto mínimo. Registrar comando, runtime, fecha, resultado y límites cuando se ejecuten pruebas.
- Corregir enlaces afectados por movimientos autorizados; conservar rutas históricas en texto y números de línea sin reescribir su significado. Actualizar el índice solo dentro del alcance autorizado.

## Skills del proyecto

Las skills bajo .agents/skills/ aportan procedimientos especializados. Seleccionar solo las pertinentes; no cargar las seis por defecto ni duplicar estas reglas en cada una. Su selección no autoriza acciones externas o cambios adicionales.
