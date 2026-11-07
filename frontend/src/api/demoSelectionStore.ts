/** Memoria exclusiva de demostración. Sin persistencia, polling o transporte propio. */
export function createDemoSelectionStore<T>() {
  const cache = new Map<string, T>();
  const pending = new Map<string, { controller: AbortController; promise: Promise<T>; users: number }>();
  let latest: T | undefined;
  return {
    peek: () => latest === undefined ? undefined : structuredClone(latest),
    async get(key: string, load: (signal: AbortSignal) => Promise<T>, signal?: AbortSignal): Promise<T> {
      if (signal?.aborted) throw new DOMException('Cancelada', 'AbortError');
      const cached = cache.get(key);
      if (cached !== undefined) { latest = cached; return structuredClone(cached); }
      let task = pending.get(key);
      if (!task) {
        const controller = new AbortController();
        const created: { controller: AbortController; users: number; promise: Promise<T> } = { controller, users: 0, promise: Promise.resolve(undefined as T) };
        created.promise = Promise.resolve().then(() => load(controller.signal)).then((value) => {
          if (controller.signal.aborted) throw new DOMException('Cancelada', 'AbortError');
          cache.set(key, structuredClone(value));
          if (cache.size > 12) cache.delete(cache.keys().next().value!);
          return value;
        }).finally(() => { if (pending.get(key) === created) pending.delete(key); });
        task = created; pending.set(key, created);
      }
      const active = task; active.users++;
      return new Promise<T>((resolve, reject) => {
        let settled = false;
        const finish = (error: unknown, value?: T) => {
          if (settled) return; settled = true; signal?.removeEventListener('abort', abort); active.users--;
          // Permite reutilizar una carga durante el montaje doble de StrictMode.
          setTimeout(() => { if (active.users === 0 && pending.get(key) === active) active.controller.abort(); }, 0);
          if (error) reject(error); else { latest = structuredClone(value!); resolve(structuredClone(value!)); }
        };
        const abort = () => finish(new DOMException('Cancelada', 'AbortError'));
        signal?.addEventListener('abort', abort, { once: true });
        if (signal?.aborted) abort();
        active.promise.then(value => finish(undefined, value), error => finish(error));
      });
    },
  };
}
