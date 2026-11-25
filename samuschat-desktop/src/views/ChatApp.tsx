import { MobileNavigation, useMobileLayout } from './MobileNavigation';
import { FriendsPanel, SearchIcon } from './Friends';
import { ServerSettings } from './ServerSettings';
import { UserProfileDialog } from './UserProfileDialog';
import { MicrophoneSettings } from './MicrophoneSettings';
import { SecureAttachment } from './SecureAttachment';
import { DirectCallPanel, VoiceRoomPanel } from './CallStage';
import type { ScreenPreferences } from '../controllers/useScreenPreferences';
import { useEffect, useRef, useState, type CSSProperties } from 'react';
import type { ChatController } from '../controllers/useChatController';
import type { VoiceRoomController } from '../controllers/useVoiceRoomController';
import type { CallController } from '../controllers/useCallController';
import { AppearanceMotion, AppearanceImage, ServerAppearanceDialog, appearanceHref } from './Appearance';
import { MembersDialog } from './MembersDialog';
import { attachmentHref } from '../models/apiRepository';
import { Settings } from './Settings';
import { ProfileSettings } from './ProfileSettings';

function Avatar({ name, url }: { name: string; url?: string }) { return url ? <AppearanceImage url={url} alt="" className="avatar avatar-image" /> : <span className="avatar" aria-hidden="true">{name.slice(0, 2).toUpperCase()}</span>; }
function ServerDialog({ kind, controller: c, close, error }: { kind: 'create' | 'join' | 'channel'; controller: ChatController; close: () => void; error: string }) {
  const ref = useRef<HTMLDialogElement>(null); const [name, setName] = useState(''); const [description, setDescription] = useState(''); const [channelType, setChannelType] = useState<'TEXT' | 'VOICE'>('TEXT');
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={close}><form onSubmit={event => { event.preventDefault(); void (async () => { const ok = kind === 'join' ? await c.joinServer(name) : kind === 'create' ? await c.createServer(name, description) : await c.createChannel(name, channelType); if (ok) close(); })(); }}>
    <h2>{kind === 'join' ? 'Entrar em um servidor' : kind === 'channel' ? 'Criar canal' : 'Criar servidor'}</h2>{error && <p className="error-notice" role="alert">{error}</p>}
    {kind === 'join' && <p className="muted">Peça o código de 3 caracteres a um administrador. Maiúsculas e minúsculas são diferentes.</p>}<label htmlFor="server-name">{kind === 'join' ? 'Código do servidor' : 'Nome'}</label><input id="server-name" autoFocus type="text" autoCapitalize="none" autoCorrect="off" spellCheck={false} minLength={kind === 'join' ? 3 : undefined} maxLength={kind === 'join' ? 3 : 100} value={name} onChange={event => setName(event.target.value)} required />
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
  const mobile = useMobileLayout();
  const [navigationOpen, setNavigationOpen] = useState(false);
  const chatMain = useRef<HTMLElement>(null);
  useEffect(() => { if (!mobile) setNavigationOpen(false); }, [mobile]);
  const closeNavigation = () => setNavigationOpen(false);
  function selectNavigation() { setNavigationOpen(false); requestAnimationFrame(() => chatMain.current?.focus({ preventScroll: true })); }
  const historyRef = useRef<HTMLDivElement>(null); const fileRef = useRef<HTMLInputElement>(null);
  const [dialog, setDialog] = useState<'create' | 'join' | 'channel' | null>(null);
  const [friendSearch, setFriendSearch] = useState(false);
  const [viewingProfile, setViewingProfile] = useState<string | null>(null);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [reduceMotion, setReduceMotion] = useState(() => localStorage.getItem('samuschat.reduceMotion') !== 'false' || window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [membersOpen, setMembersOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [leaveOpen, setLeaveOpen] = useState(false);
  useEffect(() => { setDeleteId(null); setMembersOpen(false); setLeaveOpen(false); setAppearanceOpen(false); }, [c.serverId, c.conversation?.id]);
  const lastMessage = c.messages.at(-1)?.id;
  useEffect(() => { const el = historyRef.current; if (el) el.scrollTop = el.scrollHeight; }, [lastMessage, c.conversation?.id]);
  const server = c.servers.find(item => item.id === c.serverId); const conversation = c.conversation;
  return <AppearanceMotion.Provider value={!reduceMotion}><div className="app-shell" style={{ '--accent': server?.accentColor ?? c.user.accentColor ?? 'var(--brand)' } as CSSProperties}>
    <MobileNavigation mobile={mobile} open={navigationOpen} close={closeNavigation} select={selectNavigation}><nav className="server-rail" aria-label="Servidores">
      <button className={`server-icon home ${c.serverId === null ? 'selected' : ''}`} title="Amigos" aria-label="Amigos" aria-pressed={c.serverId === null} onClick={() => { setFriendSearch(false); c.selectArea(null); }}>SC</button><span className="rail-divider" />
      {c.servers.map(item => <button key={item.id} className={`server-icon ${item.id === c.serverId ? 'selected' : ''}`} title={item.name} aria-label={item.name} aria-pressed={item.id === c.serverId} onClick={() => c.selectArea(item.id)}>{item.iconUrl ? <AppearanceImage url={item.iconUrl} alt="" className="server-image" /> : item.name.slice(0, 2).toUpperCase()}</button>)}
      <button className="server-icon add-server" aria-label="Criar servidor" title="Criar servidor" onClick={() => setDialog('create')}>+</button><button className="server-icon" aria-label="Entrar em servidor" title="Entrar em servidor" onClick={() => setDialog('join')}>↗</button>
      <span className="rail-caption">SAMUS<br />CHAT</span>
    </nav>
    <aside className="sidebar"><header className="sidebar-header" style={{ borderBottomColor: server?.accentColor }}><strong>{server?.name ?? 'Amigos'}</strong>{server ? <ServerSettings key={server.id} server={server} admin={server.myRole ? server.myRole === 'ADMIN' : server.ownerEmail === c.profile.status || server.ownerEmail === c.user.email} busy={c.busy} channel={() => setDialog('channel')} members={() => setMembersOpen(true)} appearance={() => setAppearanceOpen(true)} leave={() => setLeaveOpen(true)} refresh={() => { void c.refresh(); }} /> : <span className="badge">{mobile ? 'WEB' : 'DESKTOP'}</span>}</header>
      <div className="sidebar-body">{!server && <button className="find-friends" onClick={() => { setFriendSearch(true); c.selectArea(null); }}><SearchIcon/><span>Encontrar amigos</span></button>}<label className="sr-only" htmlFor="search">Buscar {server ? 'canais' : 'amigos'}</label><input id="search" className="search" placeholder={server ? 'Buscar canais' : 'Filtrar conversas'} value={c.query} onChange={event => c.setQuery(event.target.value)} />

        {c.notificationStatus && <p role="status" className="message-notice">{c.notificationStatus}</p>}
        {c.notice && <div role="status" className="message-notice">Nova mensagem de {c.notice.senderUsername}<button onClick={c.openNotice}>Abrir conversa</button><button onClick={c.dismissNotice} aria-label="Fechar aviso">Fechar</button></div>}
        {c.pushNotice && <div role="status" className="message-notice">Você tem uma nova mensagem.<button onClick={() => c.openNotification(c.pushNotice!)}>Abrir conversa</button><button onClick={c.dismissPushNotice} aria-label="Fechar aviso de mensagem">Fechar</button></div>}
        <p className="section-label">{server ? 'CANAIS DE TEXTO' : 'CONVERSAS PRIVADAS'}</p><nav aria-label={server ? 'Canais' : 'Conversas'}>{c.conversations.map(item => <button key={item.id} className={`conversation-link ${item.id === conversation?.id ? 'active' : ''}`} aria-current={item.id === conversation?.id ? 'page' : undefined} onClick={() => c.selectConversation(item)}>{server ? <span>#</span> : <Avatar name={item.name} url={c.contacts.find(contact => contact.email === item.contact)?.avatarUrl} />}<span className="conversation-summary"><strong className="conversation-name">{item.name}</strong></span>{item.contact && !!c.unread[item.contact] && <small className="unread-badge" aria-label={`${c.unread[item.contact]} mensagens nao lidas`}>{c.unread[item.contact]}</small>}{c.hasDraft(item.id) && <small className="draft-badge" aria-hidden="true">Rascunho</small>}</button>)}</nav>
        {server && <><p className="section-label">CANAIS DE CHAMADA</p>{server.channels.filter(item => item.type === 'VOICE').map(item => <button key={item.id} className="conversation-link" disabled={!!calls.call || calls.busy || rooms.busy || !!rooms.room} onClick={() => { void rooms.join(item.id); }}>☎ {item.name}</button>)}</>}
        {!c.conversations.length && <p className="muted">{c.query ? 'Nenhum resultado.' : server ? 'Nenhum canal de texto disponível.' : 'Nenhum contato disponível.'}</p>}
        {!server && <button className="text-button" onClick={() => { void c.refresh().catch(() => {}); }}>Atualizar lista</button>}<div className="sidebar-note"><span className="status-dot" /> Seu espaço para se conectar<p>Continue suas conversas do celular aqui.</p></div>
      </div>
      <div className="profile-bar"><button className="profile-summary" aria-label={`Ver perfil de ${c.profile.name}`} onClick={() => setViewingProfile(c.user.publicId ?? c.user.email)}><Avatar name={c.profile.name} url={c.user.avatarUrl} /><span><strong>{c.profile.name}</strong><small>Sua conta</small></span></button><Settings iconOnly profile={close => <ProfileSettings controller={c} logout={logout} error={error} close={close} />}><div className="sidebar-settings-content">{mobile && <MicrophoneSettings stream={rooms.room ? rooms.microphoneStream : calls.microphoneStream} change={rooms.room ? rooms.changeMicrophone : calls.changeMicrophone} />}
        <button className="notification-opt-in" disabled={c.notificationBusy} onClick={()=>void c.enableNotifications()}>Ativar avisos de mensagens</button>
        {c.pushEnabled && <button className="notification-opt-in" disabled={c.notificationBusy} onClick={()=>void c.disableNotifications()}>Desativar push neste navegador</button>}
          <label className="motion-setting"><input type="checkbox" checked={reduceMotion} onChange={event => { setReduceMotion(event.target.checked); localStorage.setItem('samuschat.reduceMotion', String(event.target.checked)); }} /> Economizar animações</label>
        </div></Settings></div>
    </aside></MobileNavigation>
    <main ref={chatMain} tabIndex={-1} aria-label="Conversa" className="chat" inert={mobile && navigationOpen} style={(server?.backgroundUrl ?? (!server ? c.user.backgroundUrl : undefined)) ? { backgroundImage: `linear-gradient(155deg,var(--image-overlay-start),var(--image-overlay-end)),url("${appearanceHref(server?.backgroundUrl ?? c.user.backgroundUrl)}")`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined}><header className="chat-header"><button className="mobile-menu-trigger" aria-label="Abrir conversas e servidores" aria-expanded={navigationOpen} aria-controls="app-navigation" aria-haspopup="dialog" onClick={() => setNavigationOpen(true)}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg></button><MicrophoneSettings stream={rooms.room ? rooms.microphoneStream : calls.microphoneStream} change={rooms.room ? rooms.changeMicrophone : calls.changeMicrophone} />{conversation?.contact ? <button className="chat-profile-link" aria-label={`Ver perfil de ${conversation.name}`} onClick={() => setViewingProfile(conversation.contact!)}><Avatar name={conversation.name} url={c.contacts.find(contact => contact.email === conversation.contact)?.avatarUrl} /><strong>{conversation.name}</strong></button> : <><span className="channel-symbol">{conversation?.channelId !== undefined ? '#' : '@'}</span><strong>{conversation?.name ?? 'Suas conversas'}</strong></>}<span className="header-description">{conversation?.description ?? 'Selecione um amigo ou servidor'}</span>{conversation?.contact && <button className="primary call-start" disabled={!!calls.call || calls.busy || !!rooms.room || rooms.busy} onClick={() => { void calls.invite(conversation.contact!); }}>Ligar</button>}</header>
      {error && <div className="error-notice" role="alert">{error}<button aria-label="Fechar aviso" onClick={clearError}>×</button></div>}
      <div className="connection-status" data-connection={c.connection} role="status"><span className="status-dot" /> {c.connection}</div>
      <VoiceRoomPanel rooms={rooms} preferences={screenPreferences} email={c.profile.status} name={c.profile.name} avatarUrl={c.user.avatarUrl} />
      <DirectCallPanel contacts={c.contacts} calls={calls} preferences={screenPreferences} email={c.profile.status} name={c.profile.name} avatarUrl={c.user.avatarUrl} />
      <div className="message-history" ref={historyRef} role="log" aria-label="Histórico da conversa" aria-live="polite">
        {!conversation ? !server ? <FriendsPanel api={c.api} initialSearch={friendSearch} onProfile={setViewingProfile} onTalk={c.startDirect} onChange={c.refresh}/> : <section className="welcome"><span className="welcome-icon">SC</span><p className="eyebrow">SEU PRÓXIMO PAPO COMEÇA AQUI</p><h1>Bem-vindo, {c.profile.name}</h1><p>Escolha um amigo para conversar ou entre em um servidor. As mensagens ficam na sua conta e aparecem também no celular.</p></section> : <>
          <section className="welcome"><AppearanceImage url={server?.bannerUrl} alt="Capa do servidor" className="appearance-banner" /><span className="welcome-icon">{server ? '#' : '@'}</span><h1>{server ? `Bem-vindo a #${conversation.name}` : `Converse com ${conversation.name}`}</h1><p>{conversation.description}</p></section>
          {c.hasOlder && <button className="text-button" disabled={c.loading} onClick={() => { void c.loadOlder(); }}>Carregar mensagens anteriores</button>}{c.loading && <p role="status" className="muted">Carregando mensagens…</p>}
          {c.messages.map(item => { const href = attachmentHref(item.attachmentUrl); return <article className="message" key={item.id}><button className="message-profile-avatar" aria-label={`Ver perfil de ${item.senderUsername}`} onClick={() => setViewingProfile(item.senderEmail)}><Avatar name={item.senderUsername} url={item.senderAvatarUrl ?? (item.senderEmail === c.profile.status ? c.user.avatarUrl : c.contacts.find(contact => contact.email === item.senderEmail)?.avatarUrl)} /></button><div><div className="message-meta"><button className="message-profile-name" aria-label={`Ver perfil de ${item.senderUsername}`} onClick={() => setViewingProfile(item.senderEmail)}><strong className={item.senderEmail === c.profile.status ? 'own-name' : ''}>{item.senderUsername}</strong></button><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</time>{conversation.channelId !== undefined && item.senderEmail === c.profile.status && <button className="message-delete" aria-label={`Excluir mensagem ${item.id}`} disabled={c.busy} onClick={() => setDeleteId(item.id)}>Excluir</button>}</div>{item.content && <p>{item.content}</p>}{href && <SecureAttachment href={href} image={item.attachmentType === 'IMAGE'} load={c.loadAttachment} />}</div></article>; })}
          {!c.loading && !c.messages.length && <p className="empty-message">A conversa está começando. Envie a primeira mensagem.</p>}
        </>}
      </div>
      {conversation && <form className="composer" onSubmit={event => { event.preventDefault(); void c.sendMessage(); }}>
        {c.file && <div className="file-selected">{c.file.name}<button type="button" aria-label="Remover anexo" onClick={() => c.chooseFile(null)}>×</button></div>}
        <div className="composer-field">{conversation.channelId !== undefined && <><input type="file" ref={fileRef} className="sr-only" tabIndex={-1} onChange={event => { c.chooseFile(event.target.files?.[0] ?? null); event.target.value = ''; }} /><button type="button" className="attach-button" aria-label="Anexar arquivo" disabled={c.busy} onClick={() => fileRef.current?.click()}>+</button></>}
          <label className="sr-only" htmlFor="message">Mensagem para {conversation.name}</label><textarea id="message" rows={2} maxLength={2000} placeholder={`Conversar com ${conversation.name}`} value={c.draft} onChange={event => c.setDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void c.sendMessage(); } }} /><button className="primary" disabled={c.busy || (!c.draft.trim() && !c.file)} type="submit">{c.busy ? 'Enviando…' : 'Enviar'}</button></div><small>Enter para enviar · Shift + Enter para nova linha <span>{c.draft.length}/2000</span></small>
      </form>}
    </main>
    {viewingProfile && <UserProfileDialog key={viewingProfile} api={c.api} id={viewingProfile} onChange={c.refresh} onTalk={c.startDirect} own={viewingProfile === c.user.publicId || viewingProfile === c.user.email ? { ...c.user, publicId: c.user.publicId ?? '', relationship: 'SELF' } : undefined} close={() => setViewingProfile(null)} />}
    {appearanceOpen && <ServerAppearanceDialog controller={c} close={() => setAppearanceOpen(false)} error={error} />}
    {membersOpen && <MembersDialog controller={c} close={() => setMembersOpen(false)} error={error} onProfile={setViewingProfile} />}
    {deleteId !== null && <ConfirmDialog title="Excluir mensagem" text="Excluir esta mensagem da conversa?" busy={c.busy} error={error} close={() => setDeleteId(null)} confirm={() => c.deleteMessage(deleteId)} />}
    {leaveOpen && <ConfirmDialog title="Sair do servidor" text="Você deixará de ter acesso aos canais deste servidor." busy={c.busy} error={error} close={() => setLeaveOpen(false)} confirm={() => c.removeMember(c.profile.status)} />}
    {dialog && <ServerDialog kind={dialog} controller={c} error={error} close={() => setDialog(null)} />}
  </div></AppearanceMotion.Provider>;
}
