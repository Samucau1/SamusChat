import { useEffect, useRef, useState } from 'react';
import type { ApiRepository, PublicProfile } from '../models/apiRepository';
import { AppearanceImage, appearanceHref } from './Appearance';
import { FriendActions } from './Friends';

export function UserProfileDialog({ api, id, own, close, onChange, onTalk }: {
  api: ApiRepository; id: string; own?: PublicProfile; close(): void;
  onChange?(): Promise<unknown>; onTalk?(person: PublicProfile): void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [person, setPerson] = useState<PublicProfile | undefined>(own);
  const ownProfile = useRef(own);
  const opener = useRef(document.activeElement instanceof HTMLElement ? document.activeElement : null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    const abort = new AbortController();
    if (!ownProfile.current) void api.publicProfile(id, abort.signal).then(value => {
      if (!abort.signal.aborted) setPerson(value);
    }).catch(() => {
      if (!abort.signal.aborted) setError('Não foi possível carregar este perfil. Feche e tente novamente.');
    });
    return () => { abort.abort(); dialog?.close(); opener.current?.focus(); };
  }, [api, id]);

  async function act(action: 'request' | 'accept' | 'remove') {
    if (busy) return;
    setBusy(true); setError('');
    try { setPerson(await api.friendAction(id, action)); await onChange?.(); }
    catch (problem) { setError(problem instanceof Error ? problem.message : 'Não foi possível atualizar a amizade.'); }
    finally { setBusy(false); }
  }

  return <dialog ref={ref} className="public-profile-dialog" aria-label="Perfil do usuário" onCancel={close}>
    <header className="settings-heading"><h2>Perfil do usuário</h2><button className="settings-close" aria-label="Fechar perfil" onClick={close}>×</button></header>
    {error && <p className="error-notice" role="alert">{error}</p>}
    {!person && !error && <p role="status">Carregando perfil…</p>}
    {person && <section className="public-profile-card" style={person.backgroundUrl ? {
      backgroundImage: `linear-gradient(var(--image-overlay-start),var(--image-overlay-end)),url("${appearanceHref(person.backgroundUrl)}")`,
    } : undefined}>
      <div className="public-profile-banner" style={{ backgroundColor: person.accentColor ?? 'var(--brand)' }}><AppearanceImage url={person.bannerUrl} alt={`Capa de ${person.username}`} /></div>
      <div className="public-profile-content">
        {person.avatarUrl ? <AppearanceImage url={person.avatarUrl} alt={`Foto de ${person.username}`} className="avatar avatar-image" /> : <span className="avatar" aria-hidden="true">{person.username.slice(0, 2).toUpperCase()}</span>}
        <h3>{person.username}</h3>
        <h4>Sobre mim</h4><p className="public-profile-bio">{person.bio || 'Ainda sem descrição.'}</p>
        <h4>ID do usuário</h4><div className="public-id"><code>{person.publicId}</code><button onClick={() => {
          void navigator.clipboard.writeText(person.publicId).then(() => setCopied(true)).catch(() => setError('Não foi possível copiar o ID.'));
        }}>{copied ? 'Copiado' : 'Copiar ID'}</button></div>
        <div className="social-actions"><FriendActions person={person} busy={busy} act={action => void act(action)} talk={onTalk ? () => { onTalk(person); close(); } : undefined}/></div>
      </div>
    </section>}
    <div className="dialog-actions"><button className="primary" onClick={close}>Fechar</button></div>
  </dialog>;
}
