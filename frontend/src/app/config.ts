// Configuración pública. No importar archivos de configuración del backend.
// El Lote 1 no crea clientes HTTP ni consulta esta URL.
export const publicConfig = Object.freeze({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL?.trim() || null,
});
