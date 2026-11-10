import { parseSourceConfig } from '../api/source';

// Solo nombres publicos enumerados: nunca cargar configuracion o claves backend.
export const publicConfig = parseSourceConfig({
  VITE_API_MODE: import.meta.env.VITE_API_MODE,
  VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
  VITE_HTTP_FEATURES: import.meta.env.VITE_HTTP_FEATURES,
});
