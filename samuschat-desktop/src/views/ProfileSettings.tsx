import { useState } from 'react';
import type { ChatController } from '../controllers/useChatController';
import { AppearanceImage, MediaPicker } from './Appearance';

export function ProfileSettings({ controller: c, logout, error, close }: {
  controller: ChatController; logout(): void; error: string; close(): void;
}) {
  const [name, setName] = useState(c.profile.name);
  const [bio, setBio] = useState(c.user.bio ?? '');
  const [color, setColor] = useState(c.user.accentColor ?? '#A969CF');
  return <form className="profile-form" onSubmit={event => {
    event.preventDefault(); void c.saveProfile(name).then(ok => { if (ok) close(); });
  }}>
    <AppearanceImage url={c.user.bannerUrl} alt="Capa do perfil" className="appearance-banner" />
    <header className="profile-overview">
      {c.user.avatarUrl ? <AppearanceImage url={c.user.avatarUrl} alt="" className="avatar avatar-image" /> : <span className="avatar" aria-hidden="true">{c.profile.name.slice(0, 2).toUpperCase()}</span>}
      <div><h2>Seu perfil</h2><p>{c.user.email}</p></div>
    </header>
    {error && <p className="error-notice" role="alert">{error}</p>}
    <section className="profile-section" aria-labelledby="profile-images-title">
      <h3 id="profile-images-title">Imagens do perfil</h3>
      <p className="profile-help">PNG ou JPG até 4 MB. GIF até 2 MB e 60 quadros; até 512 px na foto e 960 × 540 na capa e no fundo.</p>
      <MediaPicker avatar label="Foto ou GIF do perfil" url={c.user.avatarUrl} busy={c.busy} save={file => c.profileMedia('avatar', file)} />
      <MediaPicker label="Capa do perfil" url={c.user.bannerUrl} busy={c.busy} save={file => c.profileMedia('banner', file)} />
      <MediaPicker label="Fundo do perfil" url={c.user.backgroundUrl} busy={c.busy} save={file => c.profileMedia('background', file)} />
      <p className="profile-help">As imagens são salvas após a seleção ou a confirmação do recorte.</p>
    </section>
    <section className="profile-section" aria-labelledby="profile-personalize-title">
      <h3 id="profile-personalize-title">Personalização</h3>
      <label htmlFor="profile-bio">Sobre mim</label>
      <textarea id="profile-bio" value={bio} maxLength={190} onChange={event => setBio(event.target.value)} disabled={c.busy} />
      <div className="profile-color-row"><div><label htmlFor="profile-color">Cor do perfil</label><p className="profile-help">Seu destaque pessoal nos servidores e conversas.</p></div><input id="profile-color" type="color" value={color} onChange={event => setColor(event.target.value)} disabled={c.busy} /></div>
      <button type="button" className="button-secondary" disabled={c.busy} onClick={() => { void c.saveAppearance(bio, color); }}>Salvar aparência</button>
    </section>
    <section className="profile-section" aria-labelledby="profile-account-title">
      <h3 id="profile-account-title">Sua conta</h3>
      <label htmlFor="profile-name">Nome de usuário</label>
      <input id="profile-name" value={name} onChange={event => setName(event.target.value)} maxLength={50} required disabled={c.busy} />
    </section>
    <div className="dialog-actions profile-save-actions"><button type="button" className="button-secondary" onClick={close}>Cancelar</button><button className="primary" disabled={c.busy || !name.trim()}>Salvar</button></div>
    <section className="profile-signout"><div><h3>Sessão da conta</h3><p className="profile-help">Sair encerra as sessões desta conta em todos os dispositivos.</p></div><button type="button" className="danger" disabled={c.busy} onClick={logout}>Sair da conta</button></section>
  </form>;
}
