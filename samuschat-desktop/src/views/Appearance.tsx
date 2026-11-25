import { createContext, useContext, useEffect, useId, useRef, useState } from 'react';
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
  const input = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const [pending, setPending] = useState<File | null>(null);
  const [feedback, setFeedback] = useState('');
  const [status, setStatus] = useState<'idle' | 'checking' | 'ready' | 'saving' | 'saved' | 'error'>('idle');
  const disabled = busy || status === 'checking' || status === 'saving';
  async function persist(file: File | null) {
    setStatus('saving'); setFeedback(file ? 'Enviando e validando a imagem…' : 'Removendo imagem…');
    try {
      if (!await save(file)) throw new Error('Não foi possível salvar. Tente novamente.');
      setStatus('saved'); setFeedback(file ? 'Imagem aceita e salva na sua conta.' : 'Imagem removida da sua conta.');
      return true;
    } catch (problem) {
      setStatus('error'); setFeedback(problem instanceof Error ? problem.message : 'Imagem recusada. Tente outro arquivo.');
      throw problem;
    }
  }
  async function choose(file: File) {
    setStatus('checking'); setFeedback('Verificando compatibilidade…');
    try {
      if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || !file.size) throw new Error('Arquivo recusado: escolha uma imagem PNG, JPG ou GIF válida.');
      if (file.size > (file.type === 'image/gif' ? 2 : 4) * 1024 * 1024) throw new Error(file.type === 'image/gif' ? 'GIF recusado: o limite é 2 MB.' : 'Imagem recusada: o limite é 4 MB.');
      const source = URL.createObjectURL(file);
      try {
        await new Promise<void>((resolve, reject) => {
          const image = new Image();
          image.onload = () => image.naturalWidth > 4096 || image.naturalHeight > 4096 || image.naturalWidth * image.naturalHeight > 16_000_000
            ? reject(new Error('Imagem recusada: até 4096 px e 16 megapixels.')) : resolve();
          image.onerror = () => reject(new Error('Imagem recusada: o arquivo não pode ser aberto.'));
          image.src = source;
        });
      } finally { URL.revokeObjectURL(source); }
      if (avatar) { setStatus('ready'); setFeedback('Imagem compatível. Ajuste o enquadramento para salvar.'); setPending(file); }
      else await persist(file);
    } catch (problem) { setStatus('error'); setFeedback(problem instanceof Error ? problem.message : 'Não foi possível validar a imagem.'); }
  }
  return <div className="appearance-picker" data-state={status}>
    <div className={`media-picker-preview${avatar ? ' media-picker-avatar' : ''}`} aria-hidden="true">{url ? <AppearanceImage url={url} alt="" /> : <span>↑</span>}</div>
    <div className="media-picker-content"><span id={titleId} className="media-picker-title">{label}</span>
      <input ref={input} className="sr-only" tabIndex={-1} aria-label={label} type="file" accept="image/png,image/jpeg,image/gif" disabled={disabled} onChange={event => {
        const file = event.target.files?.[0]; event.target.value = ''; if (file) void choose(file);
      }}/>
      <div className="media-picker-actions"><button type="button" className="button-secondary media-picker-choose" aria-label={`Escolher ${label.toLocaleLowerCase('pt-BR')}`} disabled={disabled} onClick={() => input.current?.click()}>{url ? 'Trocar imagem' : 'Escolher imagem'}</button>
        {url && <button type="button" className="media-picker-remove" disabled={disabled} onClick={() => { void persist(null).catch(() => {}); }}>Remover {label.toLocaleLowerCase('pt-BR')}</button>}</div>
      <small className="media-format-hint">PNG ou JPG até 4 MB · GIF até 2 MB</small>
      {feedback && <p className={`media-feedback ${status}`} role={status === 'error' ? 'alert' : 'status'}>{status === 'saved' ? '✓ ' : status === 'error' ? '! ' : ''}{feedback}</p>}
    </div>
    {pending && <AvatarCropDialog file={pending} close={() => {
      setPending(null); setStatus(current => current === 'ready' ? 'idle' : current);
      setFeedback(current => current === 'Imagem compatível. Ajuste o enquadramento para salvar.' ? 'Seleção cancelada. Sua imagem anterior foi mantida.' : current);
    }} save={persist}/>}
  </div>;
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
