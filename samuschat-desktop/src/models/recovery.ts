import {Client, type StompConfig} from '@stomp/stompjs';

/** Equal jitter: half to all of an exponentially growing, capped window. */
export class RecoveryBackoff {
  private ceiling: number;
  constructor(private readonly base = 3000, private readonly cap = 30000, private readonly random = Math.random) { this.ceiling = base; }
  next() { const delay = Math.floor(this.ceiling * (0.5 + this.random() * 0.5)); this.ceiling = Math.min(this.cap, this.ceiling * 2); return delay; }
  reset() { this.ceiling = this.base; }
}

/** Coalesce bursts into at most one trailing request; never overlap work. */
export function recoveryTask(run: () => Promise<void>, shouldPoll: () => boolean, canRun = () => navigator.onLine, base = 3000, cap = 30000) {
  const backoff = new RecoveryBackoff(base, cap);
  let running = false, pending = false, stopped = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const clear = () => { clearTimeout(timer); timer = undefined; };
  const schedule = () => { clear(); if (!stopped && shouldPoll()) timer = setTimeout(() => { void request(); }, backoff.next()); };
  async function request() {
    if (stopped) return;
    if (running) { pending = true; return; }
    clear();
    if (!canRun()) { schedule(); return; }
    running = true;
    try { await run(); }
    catch { /* Callers report request failures; keep the recovery loop alive. */ }
    finally {
      running = false;
      if (pending && !stopped) { pending = false; void request(); }
      else schedule();
    }
  }
  return {request, schedule, reset: () => backoff.reset(), stop: () => { stopped = true; pending = false; clear(); }};
}

/** Own the retry timer instead of relying on STOMP's fixed retry interval. */
export function reconnectingClient(config: Partial<StompConfig>) {
  const backoff = new RecoveryBackoff();
  let stopped = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const retry = () => {
    if (stopped || timer !== undefined) return;
    timer = setTimeout(() => {
      timer = undefined;
      if (stopped) return;
      if (!navigator.onLine) { retry(); return; }
      client.activate();
    }, backoff.next());
  };
  const client = new Client({...config, reconnectDelay: 0,
    onConnect: frame => { if (stopped) return; backoff.reset(); config.onConnect?.(frame); },
    onWebSocketClose: event => {
      if (stopped) return;
      config.onWebSocketClose?.(event);
      // With automatic reconnect disabled, deactivate before activating again.
      void client.deactivate().then(retry);
    },
  });
  const online = () => retry();
  window.addEventListener('online', online);
  return {client, stop: () => { stopped = true; clearTimeout(timer); window.removeEventListener('online', online); void client.deactivate(); }};
}
