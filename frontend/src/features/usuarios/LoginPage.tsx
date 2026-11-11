
import { useState } from 'react';
import type { FormEvent } from 'react';
import './usuarios.css';

interface LoginPageProps {
  onLogin: () => Promise<void>;
}

export function LoginPage({ onLogin }: LoginPageProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function iniciarSesion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setLoading(true);

    try {
      const csrfResponse = await fetch('/api/auth/csrf', {
        credentials: 'same-origin',
        headers: {
          Accept: 'application/json',
        },
      });

      if (!csrfResponse.ok) {
        throw new Error('No se pudo obtener el token CSRF');
      }

      const csrf: {
        parameterName: string;
        token: string;
      } = await csrfResponse.json();

      const datos = new URLSearchParams();
      datos.set('username', username);
      datos.set('password', password);
      datos.set(csrf.parameterName, csrf.token);

      const respuestaLogin = await fetch('/login', {
        method: 'POST',
        credentials: 'same-origin',
        redirect: 'manual',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: datos,
      });

      if (
        respuestaLogin.type !== 'opaqueredirect' &&
        !respuestaLogin.ok &&
        respuestaLogin.status !== 302
      ) {
        throw new Error('No se pudo procesar el inicio de sesión');
      }

      const sesion = await fetch('/api/auth/me', {
        credentials: 'same-origin',
        headers: {
          Accept: 'application/json',
        },
      });

      if (sesion.status === 401 || sesion.status === 403) {
        throw new Error('Usuario o contraseña incorrectos');
      }

      if (!sesion.ok) {
        throw new Error('No se pudo verificar la sesión');
      }

      const usuario = await sesion.json();

      if (!usuario.username) {
        throw new Error('No se pudo identificar al usuario');
      }

      setPassword('');
      await onLogin();

    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'No se pudo iniciar sesión'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="surface login-page">
      <h1>Iniciar sesión</h1>
      <p>Accede a ECOINVEST con tu cuenta autorizada.</p>

      <form onSubmit={iniciarSesion}>
        <div>
          <label htmlFor="username">Usuario</label>
          <input
            id="username"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
        </div>

        <div>
          <label htmlFor="password">Contraseña</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        <button type="submit" disabled={loading}>
          {loading ? 'Ingresando...' : 'Iniciar sesión'}
        </button>

        {error && <p role="alert">{error}</p>}
      </form>
    </section>
  );
}
