/** Coordinador por consumidor. Dos instancias no se cancelan entre si.
 * Descarta resultados antiguos incluso si el transporte ignora AbortSignal.
 * No introduce cache, polling ni solicitudes por su cuenta.
 */
export function createLatestRequest<T>() {
  let generation = 0;
  let controller: AbortController | undefined;
  return {
    async run(operation: (signal: AbortSignal) => Promise<T>): Promise<
      { state: 'current'; value: T } | { state: 'stale' } | { state: 'cancelled' } | { state: 'error'; reason: 'OPERACION_FALLIDA' }
    > {
      const ticket = ++generation;
      controller?.abort();
      const current = new AbortController();
      controller = current;
      try {
        const value = await operation(current.signal);
        if (ticket !== generation) return { state: 'stale' };
        return current.signal.aborted ? { state: 'cancelled' } : { state: 'current', value };
      } catch {
        if (ticket !== generation) return { state: 'stale' };
        return current.signal.aborted ? { state: 'cancelled' } : { state: 'error', reason: 'OPERACION_FALLIDA' };
      }
    },
    cancel() { controller?.abort(); },
  };
}
