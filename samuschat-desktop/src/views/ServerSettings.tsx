import { useEffect, useRef, useState } from 'react';
import type { ApiServer } from '../models/apiRepository';

export function ServerSettings({ server, admin, busy, channel, members, appearance, leave, refresh }: {
  server: ApiServer; admin: boolean; busy: boolean;
  channel(): void; members(): void; appearance(): void; leave(): void; refresh(): void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    function outside(event: PointerEvent) { if (!ref.current?.contains(event.target as Node)) setOpen(false); }
    function escape(event: KeyboardEvent) { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus(); } }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [open]);
  function act(action: () => void) { setOpen(false); trigger.current?.focus(); action(); }
  return <div className="server-settings" ref={ref}>
    <button ref={trigger} className="server-settings-trigger" aria-label="Configurações do servidor" title="Configurações do servidor" aria-expanded={open} aria-controls="server-settings-actions" onClick={() => setOpen(value => !value)}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9 3-1 3-3 1-2 3 2 2-1 3 2 3 3-1 2 3h3l1-3 3-1 2-3-2-2 1-3-2-3-3 1-2-3Z"/><circle cx="12" cy="12" r="3"/></svg>
    </button>
    {open && <section id="server-settings-actions" className="server-settings-panel" aria-label="Ações do servidor">
      <h2>Configurações do servidor</h2>
      {admin && <div className="server-invite"><span>Código do servidor</span>{server.inviteCode ? <code>{server.inviteCode}</code> : <small>O código estará disponível após a atualização da API.</small>}<small>Visível apenas para administradores. Compartilhe com quem deseja convidar.</small></div>}
      {admin && <button disabled={busy} onClick={() => act(channel)}>Criar canal</button>}
      <button disabled={busy} onClick={() => act(members)}>Membros do servidor</button>
      {admin && <button disabled={busy} onClick={() => act(appearance)}>Personalizar servidor</button>}
      <button disabled={busy} onClick={() => act(refresh)}>Atualizar lista</button>
      <button className="danger-text" disabled={busy} onClick={() => act(leave)}>Sair do servidor</button>
    </section>}
  </div>;
}
