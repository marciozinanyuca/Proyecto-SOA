
export interface UsuarioSesion {
  username: string;
  roles: string[];
}

export async function obtenerSesion(): Promise<UsuarioSesion | null> {
  const respuesta = await fetch('/api/auth/me', {
    credentials: 'same-origin',
    redirect: 'manual',
    headers: {
      Accept: 'application/json',
    },
  });

  if (
    respuesta.status === 401 ||
    respuesta.status === 403 ||
    respuesta.status === 0 ||
    respuesta.type === 'opaqueredirect'
  ) {
    return null;
  }

  if (!respuesta.ok) {
    throw new Error('No se pudo consultar la sesión');
  }

  return (await respuesta.json()) as UsuarioSesion;
}

export function esAdministrador(
  usuario: UsuarioSesion | null
): boolean {
  return usuario?.roles.includes('ROLE_ADMINISTRADOR') ?? false;
}

