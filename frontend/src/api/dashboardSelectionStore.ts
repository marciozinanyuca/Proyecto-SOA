/** Última respuesta HTTP de la sesión. Sin transporte, historial ni persistencia. */
export function createDashboardSelectionStore<T>() {
  let generation = 0;
  let latest: T | undefined;
  return {
    peek: (): T | undefined => latest === undefined ? undefined : structuredClone(latest),
    begin(): number { latest = undefined; return ++generation; },
    publish(ticket: number, value: T): boolean {
      if (ticket !== generation) return false;
      latest = structuredClone(value);
      return true;
    },
  };
}
