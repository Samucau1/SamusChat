import { ScreenShareControls } from './ScreenShareControls';
import type { ScreenPreferences } from '../controllers/useScreenPreferences';
import { useEffect, useRef, useState, type CSSProperties } from 'react';
import type { ChatController } from '../controllers/useChatController';
import type { VoiceRoomController } from '../controllers/useVoiceRoomController';
import type { CallController } from '../controllers/useCallController';
import { AppearanceMotion, AppearanceImage, MediaPicker, ServerAppearanceDialog, appearanceHref } from './Appearance';
import { MembersDialog } from './MembersDialog';
import { attachmentHref } from '../models/apiRepository';

function Avatar({ name, url }: { name: string; url?: string }) { return url ? <AppearanceImage url={url} alt="" className="avatar avatar-image" /> : <span className="avatar" aria-hidden="true">{name.slice(0, 2).toUpperCase()}</span>; }
function Media({ stream, muted = false, video = false }: { stream: MediaStream | null; muted?: boolean; video?: boolean }) {
  const ref = useRef<HTMLVideoElement>(null);
  const [needsPlay, setNeedsPlay] = useState(false);
  useEffect(() => { const el = ref.current; if (el) { el.srcObject = stream; if (stream) void el.play().catch(() => setNeedsPlay(true)); } return () => { if (el) el.srcObject = null; }; }, [stream]);
  return <><video ref={ref} muted={muted} autoPlay playsInline className={video ? 'call-video' : 'audio-only'} />{needsPlay && <button className="primary" onClick={() => { void ref.current?.play().then(() => setNeedsPlay(false)); }}>Ativar áudio</button>}</>;
}
function ProfileDialog({ controller: c, logout, error }: { controller: ChatController; logout: () => void; error: string }) {
  const [name, setName] = useState(c.profile.name); const [bio, setBio] = useState(c.user.bio ?? ''); const [color, setColor] = useState(c.user.accentColor ?? '#5865F2'); const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={() => c.setProfileOpen(false)}><form onSubmit={event => { event.preventDefault(); void c.saveProfile(name); }}>
    <AppearanceImage url={c.user.bannerUrl} alt="Capa do perfil" className="appearance-banner" /><h2>Seu perfil</h2><Avatar name={c.profile.name} url={c.user.avatarUrl} /><p>{c.profile.status}</p><MediaPicker label="Foto ou GIF do perfil" url={c.user.avatarUrl} busy={c.busy} save={file => c.profileMedia('avatar', file)} /><MediaPicker label="Fundo do perfil" url={c.user.backgroundUrl} busy={c.busy} save={file => c.profileMedia('background', file)} /><MediaPicker label="Capa do perfil" url={c.user.bannerUrl} busy={c.busy} save={file => c.profileMedia('banner', file)} /><p>PNG/JPG até 4 MB; GIF até 2 MB, 60 quadros e 512 px no avatar ou 960×540 na capa.</p><label htmlFor="profile-bio">Sobre mim</label><textarea id="profile-bio" value={bio} maxLength={190} onChange={event => setBio(event.target.value)} disabled={c.busy} /><label htmlFor="profile-color">Cor do perfil</label><input id="profile-color" type="color" value={color} onChange={event => setColor(event.target.value)} disabled={c.busy} /><button type="button" className="primary" disabled={c.busy} onClick={() => { void c.saveAppearance(bio, color); }}>Salvar aparência</button>{error && <p className="error-notice" role="alert">{error}</p>}<label htmlFor="profile-name">Nome de usuário</label><input autoFocus id="profile-name" value={name} onChange={event => setName(event.target.value)} maxLength={50} required />
    <div className="dialog-actions"><button type="button" onClick={logout}>Sair da conta</button><button type="button" onClick={() => c.setProfileOpen(false)}>Cancelar</button><button className="primary" disabled={c.busy || !name.trim()}>Salvar</button></div>
  </form></dialog>;
}
function ServerDialog({ kind, controller: c, close, error }: { kind: 'create' | 'join' | 'channel'; controller: ChatController; close: () => void; error: string }) {
  const ref = useRef<HTMLDialogElement>(null); const [name, setName] = useState(''); const [description, setDescription] = useState(''); const [channelType, setChannelType] = useState<'TEXT' | 'VOICE'>('TEXT');
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={close}><form onSubmit={event => { event.preventDefault(); void (async () => { const ok = kind === 'join' ? await c.joinServer(Number(name)) : kind === 'create' ? await c.createServer(name, description) : await c.createChannel(name, channelType); if (ok) close(); })(); }}>
    <h2>{kind === 'join' ? 'Entrar em um servidor' : kind === 'channel' ? 'Criar canal' : 'Criar servidor'}</h2>{error && <p className="error-notice" role="alert">{error}</p>}
    <label htmlFor="server-name">{kind === 'join' ? 'ID do servidor' : 'Nome'}</label><input id="server-name" autoFocus type={kind === 'join' ? 'number' : 'text'} min={1} maxLength={100} value={name} onChange={event => setName(event.target.value)} required />
    {kind === 'channel' && <label>Tipo de canal<select aria-label="Tipo de canal" value={channelType} onChange={event => setChannelType(event.target.value as 'TEXT' | 'VOICE')}><option value="TEXT">Texto</option><option value="VOICE">Chamada</option></select></label>}
    {kind === 'create' && <><label htmlFor="server-description">Descrição</label><input id="server-description" maxLength={500} value={description} onChange={event => setDescription(event.target.value)} /></>}
    <div className="dialog-actions"><button type="button" onClick={close}>Cancelar</button><button className="primary" disabled={c.busy}>{c.busy ? 'Aguarde…' : kind === 'join' ? 'Entrar' : 'Criar'}</button></div>
  </form></dialog>;
}
function ConfirmDialog({ title, text, busy, error, close, confirm }: { title: string; text: string; busy: boolean; error: string; close: () => void; confirm: () => Promise<boolean> }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={close}><h2>{title}</h2><p>{text}</p>{error && <p className="error-notice" role="alert">{error}</p>}<div className="dialog-actions"><button disabled={busy} onClick={close}>Cancelar</button><button className="danger" disabled={busy} onClick={() => { void confirm().then(ok => { if (ok) close(); }); }}>Confirmar</button></div></dialog>;
}
export function ChatApp({ controller: c, calls, rooms, screenPreferences, error, clearError, logout }: { controller: ChatController; calls: CallController; rooms: VoiceRoomController; screenPreferences: ScreenPreferences; error: string; clearError: () => void; logout: () => void }) {
  const historyRef = useRef<HTMLDivElement>(null); const fileRef = useRef<HTMLInputElement>(null);
  const [dialog, setDialog] = useState<'create' | 'join' | 'channel' | null>(null);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [reduceMotion, setReduceMotion] = useState(() => localStorage.getItem('samuschat.reduceMotion') !== 'false' || window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [membersOpen, setMembersOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [leaveOpen, setLeaveOpen] = useState(false);
  useEffect(() => { setDeleteId(null); setMembersOpen(false); setLeaveOpen(false); setAppearanceOpen(false); }, [c.serverId, c.conversation?.id]);
  const lastMessage = c.messages.at(-1)?.id;
  useEffect(() => { const el = historyRef.current; if (el) el.scrollTop = el.scrollHeight; }, [lastMessage, c.conversation?.id]);
  const server = c.servers.find(item => item.id === c.serverId); const conversation = c.conversation;
  return <AppearanceMotion.Provider value={!reduceMotion}><div className="app-shell" style={{ '--accent': server?.accentColor ?? c.user.accentColor ?? '#5865F2' } as CSSProperties}>
    <nav className="server-rail" aria-label="Servidores">
      <button className={`server-icon home ${c.serverId === null ? 'selected' : ''}`} title="Amigos" aria-label="Amigos" aria-pressed={c.serverId === null} onClick={() => c.selectArea(null)}>SC</button><span className="rail-divider" />
      {c.servers.map(item => <button key={item.id} className={`server-icon ${item.id === c.serverId ? 'selected' : ''}`} title={item.name} aria-label={item.name} aria-pressed={item.id === c.serverId} onClick={() => c.selectArea(item.id)}>{item.iconUrl ? <AppearanceImage url={item.iconUrl} alt="" className="server-image" /> : item.name.slice(0, 2).toUpperCase()}</button>)}
      <button className="server-icon add-server" aria-label="Criar servidor" title="Criar servidor" onClick={() => setDialog('create')}>+</button><button className="server-icon" aria-label="Entrar em servidor" title="Entrar em servidor" onClick={() => setDialog('join')}>↗</button>
      <span className="rail-caption">SAMUS<br />CHAT</span>
    </nav>
    <aside className="sidebar"><header className="sidebar-header" style={{ borderBottomColor: server?.accentColor }}><strong>{server?.name ?? 'Amigos'}</strong><span className="badge">DESKTOP</span></header>
      <div className="sidebar-body"><label className="sr-only" htmlFor="search">Buscar {server ? 'canais' : 'amigos'}</label><input id="search" className="search" placeholder={server ? 'Buscar canais' : 'Buscar amigos'} value={c.query} onChange={event => c.setQuery(event.target.value)} />
        <p className="section-label">{server ? 'CANAIS DE TEXTO' : 'CONVERSAS PRIVADAS'}</p><nav aria-label={server ? 'Canais' : 'Conversas'}>{c.conversations.map(item => <button key={item.id} className={`conversation-link ${item.id === conversation?.id ? 'active' : ''}`} aria-current={item.id === conversation?.id ? 'page' : undefined} onClick={() => c.selectConversation(item)}><span>{server ? '#' : '@'}</span><strong className="conversation-name">{item.name}</strong>{c.hasDraft(item.id) && <small className="draft-badge" aria-hidden="true">Rascunho</small>}</button>)}</nav>
        {server && <><p className="section-label">CANAIS DE CHAMADA</p>{server.channels.filter(item => item.type === 'VOICE').map(item => <button key={item.id} className="conversation-link" disabled={!!calls.call || calls.busy || rooms.busy || !!rooms.room} onClick={() => { void rooms.join(item.id); }}>☎ {item.name}</button>)}</>}
        {!c.conversations.length && <p className="muted">{c.query ? 'Nenhum resultado.' : server ? 'Nenhum canal de texto disponível.' : 'Nenhum contato disponível.'}</p>}
        {server && <><p className="server-id">ID do servidor: {server.id}</p><button className="text-button" onClick={() => setDialog('channel')}>+ Criar canal</button><button className="text-button" onClick={() => setMembersOpen(true)}>Membros do servidor</button><button className="text-button" onClick={() => setLeaveOpen(true)}>Sair do servidor</button>{server.ownerEmail === c.profile.status && <button className="text-button" onClick={() => setAppearanceOpen(true)}>Personalizar servidor</button>}</>}
        <button className="text-button" onClick={() => { void c.refresh().catch(() => {}); }}>Atualizar lista</button><div className="sidebar-note"><span className="status-dot" /> Seu espaço para se conectar<p>Continue suas conversas do celular aqui.</p></div>
      </div>
      <button className="profile-button" onClick={() => c.setProfileOpen(true)}><Avatar name={c.profile.name} url={c.user.avatarUrl} /><span><strong>{c.profile.name}</strong><small>{c.profile.status}</small></span><span className="profile-edit">Editar</span></button>
    </aside>
    <main className="chat" style={(server?.backgroundUrl ?? (!server ? c.user.backgroundUrl : undefined)) ? { backgroundImage: `linear-gradient(#313338e8,#313338e8),url("${appearanceHref(server?.backgroundUrl ?? c.user.backgroundUrl)}")`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined}><header className="chat-header"><span className="channel-symbol">{conversation?.channelId !== undefined ? '#' : '@'}</span><strong>{conversation?.name ?? 'Suas conversas'}</strong><span className="header-description">{conversation?.description ?? 'Selecione um amigo ou servidor'}</span>{conversation?.contact && <button className="primary call-start" disabled={!!calls.call || calls.busy || !!rooms.room || rooms.busy} onClick={() => { void calls.invite(conversation.contact!); }}>Ligar</button>}</header>
      {error && <div className="error-notice" role="alert">{error}<button aria-label="Fechar aviso" onClick={clearError}>×</button></div>}
      <label className="motion-setting"><input type="checkbox" checked={reduceMotion} onChange={event => { setReduceMotion(event.target.checked); localStorage.setItem('samuschat.reduceMotion', String(event.target.checked)); }} /> Economizar animações</label><div className="connection-status" role="status"><span className="status-dot" /> {c.connection}</div>
      {rooms.room && <section className="call-panel voice-room" aria-label="Canal de chamada"><div><strong>Canal de chamada</strong><span>{rooms.room.participants.length}/{rooms.room.capacity} participantes</span></div><button aria-pressed={rooms.muted} onClick={rooms.toggleMute}>{rooms.muted ? 'Ativar microfone do canal' : 'Silenciar microfone do canal'}</button><button disabled={rooms.screenStarting} onClick={() => { void rooms.share(); }}>{rooms.sharing ? 'Parar transmissão do canal' : 'Compartilhar tela no canal'}</button><button className="danger" onClick={() => { void rooms.leave(); }}>Sair do canal de chamada</button><ScreenShareControls preferences={screenPreferences} locked={rooms.sharing || rooms.screenStarting} feedback={rooms.feedback} /><p>{rooms.room.participants.map(item => item.username).join(', ')}</p>{rooms.remote.map(item => <div key={item.id}><small>{rooms.room?.participants.find(contact => contact.email === item.email)?.username ?? item.email}</small><Media stream={item.stream} video={item.sharing} /></div>)}</section>}
      {calls.call && <section className="call-panel" aria-label="Chamada"><div><strong>{calls.status}</strong><span>{calls.call.caller === c.profile.status ? calls.call.callee : calls.call.caller}</span></div>
        {calls.call.state === 'RINGING' && calls.call.callee === c.profile.status ? <><button className="primary" disabled={calls.busy} onClick={() => { void calls.accept(); }}>Aceitar</button><button className="danger" onClick={() => { void calls.end(true); }}>Recusar</button></> : <><button disabled={calls.call.state === 'RINGING'} aria-pressed={calls.muted} onClick={calls.toggleMute}>{calls.muted ? 'Ativar microfone' : 'Silenciar microfone'}</button><button disabled={calls.status !== 'Em chamada' || calls.screenStarting} onClick={() => { void calls.share(); }}>{calls.sharing ? 'Parar transmissão' : 'Compartilhar tela'}</button><button className="danger" onClick={() => { void calls.end(); }}>Encerrar chamada</button></>}
        <ScreenShareControls preferences={screenPreferences} locked={calls.sharing || calls.screenStarting} feedback={calls.feedback} />{calls.sharing && <button onClick={() => calls.setPreviewVisible(!calls.previewVisible)}>{calls.previewVisible ? 'Ocultar prévia' : 'Mostrar prévia'}</button>}<Media stream={calls.remote} video={calls.remoteSharing} />{calls.preview && <div className="local-preview"><small>Sua transmissão</small><Media stream={calls.preview} muted video /></div>}
      </section>}
      <div className="message-history" ref={historyRef} role="log" aria-label="Histórico da conversa" aria-live="polite">
        {!conversation ? <section className="welcome"><span className="welcome-icon">SC</span><p className="eyebrow">SEU PRÓXIMO PAPO COMEÇA AQUI</p><h1>Bem-vindo, {c.profile.name}</h1><p>Escolha um amigo para conversar ou entre em um servidor. As mensagens ficam na sua conta e aparecem também no celular.</p></section> : <>
          <section className="welcome"><AppearanceImage url={server?.bannerUrl} alt="Capa do servidor" className="appearance-banner" /><span className="welcome-icon">{server ? '#' : '@'}</span><h1>{server ? `Bem-vindo a #${conversation.name}` : `Converse com ${conversation.name}`}</h1><p>{conversation.description}</p></section>
          {c.hasOlder && <button className="text-button" disabled={c.loading} onClick={() => { void c.loadOlder(); }}>Carregar mensagens anteriores</button>}{c.loading && <p role="status" className="muted">Carregando mensagens…</p>}
          {c.messages.map(item => { const href = attachmentHref(item.attachmentUrl); return <article className="message" key={item.id}><Avatar name={item.senderUsername} /><div><div className="message-meta"><strong className={item.senderEmail === c.profile.status ? 'own-name' : ''}>{item.senderUsername}</strong><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</time>{conversation.channelId !== undefined && item.senderEmail === c.profile.status && <button className="message-delete" aria-label={`Excluir mensagem ${item.id}`} disabled={c.busy} onClick={() => setDeleteId(item.id)}>Excluir</button>}</div>{item.content && <p>{item.content}</p>}{href && <a className="attachment" href={href} target="_blank" rel="noopener noreferrer">{item.attachmentType === 'IMAGE' && <img src={href} alt="Imagem anexada" loading="lazy" />}Abrir anexo ↗</a>}</div></article>; })}
          {!c.loading && !c.messages.length && <p className="empty-message">A conversa está começando. Envie a primeira mensagem.</p>}
        </>}
      </div>
      {conversation && <form className="composer" onSubmit={event => { event.preventDefault(); void c.sendMessage(); }}>
        {c.file && <div className="file-selected">{c.file.name}<button type="button" aria-label="Remover anexo" onClick={() => c.chooseFile(null)}>×</button></div>}
        <div className="composer-field">{conversation.channelId !== undefined && <><input type="file" ref={fileRef} className="sr-only" tabIndex={-1} onChange={event => { c.chooseFile(event.target.files?.[0] ?? null); event.target.value = ''; }} /><button type="button" className="attach-button" aria-label="Anexar arquivo" disabled={c.busy} onClick={() => fileRef.current?.click()}>+</button></>}
          <label className="sr-only" htmlFor="message">Mensagem para {conversation.name}</label><textarea id="message" rows={2} maxLength={2000} placeholder={`Conversar com ${conversation.name}`} value={c.draft} onChange={event => c.setDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void c.sendMessage(); } }} /><button className="primary" disabled={c.busy || (!c.draft.trim() && !c.file)} type="submit">{c.busy ? 'Enviando…' : 'Enviar ↗'}</button></div><small>Enter para enviar · Shift + Enter para nova linha <span>{c.draft.length}/2000</span></small>
      </form>}
    </main>
    {appearanceOpen && <ServerAppearanceDialog controller={c} close={() => setAppearanceOpen(false)} error={error} />}
    {membersOpen && <MembersDialog controller={c} close={() => setMembersOpen(false)} error={error} />}
    {deleteId !== null && <ConfirmDialog title="Excluir mensagem" text="Excluir esta mensagem da conversa?" busy={c.busy} error={error} close={() => setDeleteId(null)} confirm={() => c.deleteMessage(deleteId)} />}
    {leaveOpen && <ConfirmDialog title="Sair do servidor" text="Você deixará de ter acesso aos canais deste servidor." busy={c.busy} error={error} close={() => setLeaveOpen(false)} confirm={() => c.removeMember(c.profile.status)} />}
    {c.profileOpen && <ProfileDialog controller={c} logout={logout} error={error} />}{dialog && <ServerDialog kind={dialog} controller={c} error={error} close={() => setDialog(null)} />}
  </div></AppearanceMotion.Provider>;
}
