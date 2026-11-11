
import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import './usuarios.css';

type Rol = 'ADMINISTRADOR' | 'ANALISTA';

interface Usuario {
  id: number;
  nombre: string;
  username: string;
  rol: Rol;
  activo: boolean;
}

interface CsrfToken {
  token: string;
  headerName: string;
}

export function UsuariosPage() {
  const [usuarios, setUsuarios] = useState<Usuario[]>([]);
  const [nombre, setNombre] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rol, setRol] = useState<Rol>('ANALISTA');
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [cargando, setCargando] = useState(false);
  const [guardando, setGuardando] = useState(false);

  const cargarUsuarios = useCallback(async () => {
    setCargando(true);

    try {
      const respuesta = await fetch('/api/admin/usuarios', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });

      if (!respuesta.ok) {
        throw new Error(
          respuesta.status === 403
            ? 'No tienes permisos de administrador'
            : 'No se pudo cargar la lista de usuarios'
        );
      }

      const datos: Usuario[] = await respuesta.json();
      setUsuarios(datos);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error al consultar usuarios'
      );
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void cargarUsuarios();
  }, [cargarUsuarios]);

  async function obtenerCsrf(): Promise<CsrfToken> {
    const respuesta = await fetch('/api/auth/csrf', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    });

    if (!respuesta.ok) {
      throw new Error('No se pudo obtener el token de seguridad');
    }

    return respuesta.json();
  }

  async function crearUsuario(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setMensaje('');
    setGuardando(true);

    try {
      const csrf = await obtenerCsrf();

      const respuesta = await fetch('/api/admin/usuarios', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({
          nombre: nombre.trim(),
          username: username.trim(),
          password,
          rol,
        }),
      });

      if (!respuesta.ok) {
        throw new Error(
          respuesta.status === 409
            ? 'Ese nombre de usuario ya existe'
            : respuesta.status === 403
              ? 'Operación no autorizada o token CSRF inválido'
              : `No se pudo crear el usuario (HTTP ${respuesta.status})`
        );
      }

      setNombre('');
      setUsername('');
      setPassword('');
      setRol('ANALISTA');
      setMensaje('Usuario creado correctamente');

      await cargarUsuarios();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error al crear usuario'
      );
    } finally {
      setGuardando(false);
    }
  }

  async function cambiarEstado(usuario: Usuario) {
    setError('');
    setMensaje('');

    try {
      const csrf = await obtenerCsrf();

      const respuesta = await fetch(
        `/api/admin/usuarios/${usuario.id}/estado`,
        {
          method: 'PATCH',
          credentials: 'same-origin',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({ activo: !usuario.activo }),
        }
      );

      if (!respuesta.ok) {
        throw new Error(
          respuesta.status === 409
            ? 'Esta cuenta no puede desactivarse'
            : `No se pudo actualizar la cuenta (HTTP ${respuesta.status})`
        );
      }

      setMensaje('Estado actualizado correctamente');
      await cargarUsuarios();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error al cambiar estado'
      );
    }
  }

  return (
    <div className="usuarios-page">
      <section className="surface" style={{ padding: 24, marginBottom: 20 }}>
        <h1>Administración de usuarios</h1>
        <p>Gestiona las cuentas y los permisos de ECOINVEST PERÚ.</p>

        {error && <p role="alert" style={{ color: '#b42318' }}>{error}</p>}
        {mensaje && <p role="status" style={{ color: '#087f5b' }}>{mensaje}</p>}

        <h2>Registrar nuevo usuario</h2>

        <form onSubmit={crearUsuario}
          style={{ display: 'grid', gap: 12, maxWidth: 460 }}>

          <label>
            Nombre completo
            <input
              style={{ display: 'block', width: '100%' }}
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              maxLength={120}
              required
            />
          </label>

          <label>
            Nombre de usuario
            <input
              style={{ display: 'block', width: '100%' }}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              pattern="[a-zA-Z0-9._-]{3,60}"
              minLength={3}
              maxLength={60}
              required
            />
          </label>

          <label>
            Contraseña
            <input
              style={{ display: 'block', width: '100%' }}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={12}
              maxLength={72}
              autoComplete="new-password"
              required
            />
          </label>

          <label>
            Rol
            <select
              style={{ display: 'block', width: '100%' }}
              value={rol}
              onChange={(e) => setRol(e.target.value as Rol)}
            >
              <option value="ANALISTA">Analista</option>
              <option value="ADMINISTRADOR">Administrador</option>
            </select>
          </label>

          <button type="submit" disabled={guardando}>
            {guardando ? 'Guardando...' : 'Crear usuario'}
          </button>
        </form>
      </section>

      <section className="surface" style={{ padding: 24 }}>
        <h2>Usuarios registrados</h2>

        {cargando ? (
          <p>Cargando usuarios...</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', textAlign: 'left' }}>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Usuario</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Acción</th>
                </tr>
              </thead>
              <tbody>
                {usuarios.map((usuario) => (
                  <tr key={usuario.id}>
                    <td>{usuario.nombre}</td>
                    <td>{usuario.username}</td>
                    <td>{usuario.rol}</td>
                    <td>{usuario.activo ? 'Activo' : 'Inactivo'}</td>
                    <td>
                      <button
                        type="button"
                        disabled={usuario.username === 'admin'}
                        onClick={() => void cambiarEstado(usuario)}
                      >
                        {usuario.activo ? 'Desactivar' : 'Activar'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
