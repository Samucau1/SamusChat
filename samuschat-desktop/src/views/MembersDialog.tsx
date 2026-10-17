import { AppearanceImage } from './Appearance';
import { useEffect, useRef, useState } from 'react';
import type { ChatController } from '../controllers/useChatController';
import type { MemberRole } from '../models/apiRepository';

export function MembersDialog({ controller: c, close, error }: { controller: ChatController; close: () => void; error: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  const [target, setTarget] = useState<string | null>(null);
  useEffect(() => { ref.current?.showModal(); void c.loadMembers(); }, []);
  const own = c.members.find(item => item.userEmail === c.profile.status);
  return <dialog ref={ref} onCancel={close} className="members-dialog"><h2>Membros do servidor</h2>
    {error && <p className="error-notice" role="alert">{error}</p>}
    {c.busy && <p role="status">Aguarde…</p>}
    <ul className="member-list">{c.members.map(item => <li key={item.id}>{item.avatarUrl && <AppearanceImage url={item.avatarUrl} alt={`Foto de ${item.username}`} className="avatar avatar-image" />}<div><strong>{item.username}</strong></div>
      {own?.role === 'ADMIN' && item.userEmail !== c.profile.status ? <><select aria-label={`Cargo de ${item.username}`} value={item.role} disabled={c.busy} onChange={event => { void c.updateRole(item.userEmail, event.target.value as MemberRole); }}><option value="USER">Membro</option><option value="MOD">Moderador</option><option value="ADMIN">Administrador</option></select><button className="danger" disabled={c.busy} onClick={() => setTarget(item.userEmail)}>Remover {item.username}</button></> : <small>{item.role === 'ADMIN' ? 'Administrador' : item.role === 'MOD' ? 'Moderador' : 'Membro'}</small>}
    </li>)}</ul>
    {target && <div className="removal-confirmation"><p>Remover {target} do servidor?</p><button disabled={c.busy} onClick={() => setTarget(null)}>Cancelar remoção</button><button className="danger" disabled={c.busy} onClick={() => { void c.removeMember(target).then(ok => { if (ok) setTarget(null); }); }}>Confirmar remoção</button></div>}
    <div className="dialog-actions"><button disabled={c.busy} onClick={() => { void c.loadMembers(); }}>Atualizar membros</button><button className="primary" onClick={close}>Fechar</button></div>
  </dialog>;
}
