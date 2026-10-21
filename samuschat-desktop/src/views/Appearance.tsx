import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ChatController } from '../controllers/useChatController';
import { attachmentHref } from '../models/apiRepository';
import { AvatarCropDialog } from './AvatarCropDialog';

export const AppearanceMotion = createContext(false);
export function appearanceHref(url?: string, animated = false) { return attachmentHref(!animated && url?.endsWith('.gif') ? url.slice(0, -4) + '.png' : url); }
export function AppearanceImage({ url, alt, className = '' }: { url?: string; alt: string; className?: string }) {
  const animated = useContext(AppearanceMotion);
  const href = appearanceHref(url, animated);
  return href ? <img src={href} alt={alt} className={className} loading="lazy" decoding="async" /> : null;
}
export function MediaPicker({ label, url, busy, save, avatar = false }: { label: string; url?: string; busy: boolean; avatar?: boolean; save: (file: File | null) => Promise<boolean> }) {
  const [pending, setPending] = useState<File | null>(null);
  const [error, setError] = useState('');
  return <div className="appearance-picker"><label>{label}<input aria-label={label} type="file" accept="image/png,image/jpeg,image/gif" disabled={busy} onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; setError(''); if (file) {
    if (avatar) { if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || !file.size || file.size > 4 * 1024 * 1024 || file.type === 'image/gif' && file.size > 2 * 1024 * 1024) setError('Use PNG/JPG de até 4 MB ou GIF de até 2 MB.'); else setPending(file); }
    else void save(file);
  } }} /></label>{error && <p role="alert">{error}</p>}{url && <button type="button" disabled={busy} onClick={() => { void save(null); }}>Remover {label.toLocaleLowerCase('pt-BR')}</button>}{pending && <AvatarCropDialog file={pending} close={() => setPending(null)} save={save} />}</div>;
}
export function ServerAppearanceDialog({ controller: c, close, error }: { controller: ChatController; close: () => void; error: string }) {
  const server = c.servers.find(item => item.id === c.serverId)!;
  const [color, setColor] = useState(server.accentColor ?? '#5865F2');
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={close}><h2>Personalizar servidor</h2>{error && <p className="error-notice" role="alert">{error}</p>}
    <AppearanceImage url={server.bannerUrl} alt="Capa do servidor" className="appearance-banner" />
    <p>PNG/JPG até 4 MB; GIF até 2 MB e 60 quadros. Ícone até 512 px; capa e fundo GIF até 960×540. GIFs também têm limite de pixels somados.</p>
    <MediaPicker label="Ícone do servidor" url={server.iconUrl} busy={c.busy} save={file => c.serverMedia('icon', file)} />
    <MediaPicker label="Capa do servidor" url={server.bannerUrl} busy={c.busy} save={file => c.serverMedia('banner', file)} />
    <MediaPicker label="Fundo do servidor" url={server.backgroundUrl} busy={c.busy} save={file => c.serverMedia('background', file)} />
    <form onSubmit={event => { event.preventDefault(); void c.saveServerAppearance(color); }}><label htmlFor="server-color">Cor do servidor</label><input id="server-color" type="color" value={color} onChange={event => setColor(event.target.value)} disabled={c.busy} /><div className="dialog-actions"><button type="button" onClick={close}>Fechar</button><button className="primary" disabled={c.busy}>Salvar cor</button></div></form>
  </dialog>;
}
