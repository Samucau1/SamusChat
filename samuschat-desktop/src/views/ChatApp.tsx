import { useEffect, useRef, useState } from 'react';
import type { ChatController } from '../controllers/useChatController';
import type { CallController } from '../controllers/useCallController';
import { attachmentHref } from '../models/apiRepository';

function Avatar({ name }: { name: string }) { return <span className="avatar" aria-hidden="true">{name.slice(0, 2).toUpperCase()}</span>; }
function Media({ stream, muted = false, video = false }: { stream: MediaStream | null; muted?: boolean; video?: boolean }) {
  const ref = useRef<HTMLVideoElement>(null);
  const [needsPlay, setNeedsPlay] = useState(false);
  useEffect(() => { const el = ref.current; if (el) { el.srcObject = stream; if (stream) void el.play().catch(() => setNeedsPlay(true)); } return () => { if (el) el.srcObject = null; }; }, [stream]);
  return <><video ref={ref} muted={muted} autoPlay playsInline className={video ? 'call-video' : 'audio-only'} />{needsPlay && <button className="primary" onClick={() => { void ref.current?.play().then(() => setNeedsPlay(false)); }}>Ativar áudio</button>}</>;
}
function ProfileDialog({ controller: c, logout, error }: { controller: ChatController; logout: () => void; error: string }) {
  const [name, setName] = useState(c.profile.name); const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={() => c.setProfileOpen(false)}><form onSubmit={event => { event.preventDefault(); void c.saveProfile(name); }}>
    <h2>Seu perfil</h2><p>{c.profile.status}</p>{error && <p className="error-notice" role="alert">{error}</p>}<label htmlFor="profile-name">Nome de usuário</label><input autoFocus id="profile-name" value={name} onChange={event => setName(event.target.value)} minLength={3} maxLength={30} pattern="[A-Za-z0-9_-]{3,30}" required />
    <div className="dialog-actions"><button type="button" onClick={logout}>Sair da conta</button><button type="button" onClick={() => c.setProfileOpen(false)}>Cancelar</button><button className="primary" disabled={c.busy || !name.trim()}>Salvar</button></div>
  </form></dialog>;
}
function ServerDialog({ kind, controller: c, close, error }: { kind: 'create' | 'join' | 'channel'; controller: ChatController; close: () => void; error: string }) {
  const ref = useRef<HTMLDialogElement>(null); const [name, setName] = useState(''); const [description, setDescription] = useState('');
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={close}><form onSubmit={event => { event.preventDefault(); void (async () => { const ok = kind === 'join' ? await c.joinServer(Number(name)) : kind === 'create' ? await c.createServer(name, description) : await c.createChannel(name); if (ok) close(); })(); }}>
    <h2>{kind === 'join' ? 'Entrar em um servidor' : kind === 'channel' ? 'Criar canal de texto' : 'Criar servidor'}</h2>{error && <p className="error-notice" role="alert">{error}</p>}
    <label htmlFor="server-name">{kind === 'join' ? 'ID do servidor' : 'Nome'}</label><input id="server-name" autoFocus type={kind === 'join' ? 'number' : 'text'} min={1} maxLength={100} value={name} onChange={event => setName(event.target.value)} required />
    {kind === 'create' && <><label htmlFor="server-description">Descrição</label><input id="server-description" maxLength={500} value={description} onChange={event => setDescription(event.target.value)} /></>}
    <div className="dialog-actions"><button type="button" onClick={close}>Cancelar</button><button className="primary" disabled={c.busy}>{c.busy ? 'Aguarde…' : kind === 'join' ? 'Entrar' : 'Criar'}</button></div>
  </form></dialog>;
}
export function ChatApp({ controller: c, calls, error, clearError, logout }: { controller: ChatController; calls: CallController; error: string; clearError: () => void; logout: () => void }) {
  const historyRef = useRef<HTMLDivElement>(null); const fileRef = useRef<HTMLInputElement>(null);
  const [dialog, setDialog] = useState<'create' | 'join' | 'channel' | null>(null);
  const lastMessage = c.messages.at(-1)?.id;
  useEffect(() => { const el = historyRef.current; if (el) el.scrollTop = el.scrollHeight; }, [lastMessage, c.conversation?.id]);
  const server = c.servers.find(item => item.id === c.serverId); const conversation = c.conversation;
  return <div className="app-shell">
    <nav className="server-rail" aria-label="Servidores">
      <button className={`server-icon home ${c.serverId === null ? 'selected' : ''}`} title="Amigos" aria-label="Amigos" aria-pressed={c.serverId === null} onClick={() => c.selectArea(null)}>SC</button><span className="rail-divider" />
      {c.servers.map(item => <button key={item.id} className={`server-icon ${item.id === c.serverId ? 'selected' : ''}`} title={item.name} aria-label={item.name} aria-pressed={item.id === c.serverId} onClick={() => c.selectArea(item.id)}>{item.name.slice(0, 2).toUpperCase()}</button>)}
      <button className="server-icon add-server" aria-label="Criar servidor" title="Criar servidor" onClick={() => setDialog('create')}>+</button><button className="server-icon" aria-label="Entrar em servidor" title="Entrar em servidor" onClick={() => setDialog('join')}>↗</button>
      <span className="rail-caption">SAMUS<br />CHAT</span>
    </nav>
    <aside className="sidebar"><header className="sidebar-header"><strong>{server?.name ?? 'Amigos'}</strong><span className="badge">DESKTOP</span></header>
      <div className="sidebar-body"><label className="sr-only" htmlFor="search">Buscar {server ? 'canais' : 'amigos'}</label><input id="search" className="search" placeholder={server ? 'Buscar canais' : 'Buscar amigos'} value={c.query} onChange={event => c.setQuery(event.target.value)} />
        <p className="section-label">{server ? 'CANAIS DE TEXTO' : 'CONVERSAS PRIVADAS'}</p><nav aria-label={server ? 'Canais' : 'Conversas'}>{c.conversations.map(item => <button key={item.id} className={`conversation-link ${item.id === conversation?.id ? 'active' : ''}`} aria-current={item.id === conversation?.id ? 'page' : undefined} onClick={() => c.selectConversation(item)}><span>{server ? '#' : '@'}</span>{item.name}</button>)}</nav>
        {!c.conversations.length && <p className="muted">{c.query ? 'Nenhum resultado.' : server ? 'Nenhum canal de texto disponível.' : 'Nenhum contato disponível.'}</p>}
        {server && <><p className="server-id">ID do servidor: {server.id}</p><button className="text-button" onClick={() => setDialog('channel')}>+ Criar canal</button></>}
        <button className="text-button" onClick={() => { void c.refresh().catch(() => {}); }}>Atualizar lista</button><div className="sidebar-note"><span className="status-dot" /> Seu espaço para se conectar<p>Continue suas conversas do celular aqui.</p></div>
      </div>
      <button className="profile-button" onClick={() => c.setProfileOpen(true)}><Avatar name={c.profile.name} /><span><strong>{c.profile.name}</strong><small>{c.profile.status}</small></span><span className="profile-edit">Editar</span></button>
    </aside>
    <main className="chat"><header className="chat-header"><span className="channel-symbol">{conversation?.channelId !== undefined ? '#' : '@'}</span><strong>{conversation?.name ?? 'Suas conversas'}</strong><span className="header-description">{conversation?.description ?? 'Selecione um amigo ou servidor'}</span>{conversation?.contact && <button className="primary call-start" disabled={!!calls.call || calls.busy} onClick={() => { void calls.invite(conversation.contact!); }}>Ligar</button>}</header>
      {error && <div className="error-notice" role="alert">{error}<button aria-label="Fechar aviso" onClick={clearError}>×</button></div>}
      <div className="connection-status" role="status"><span className="status-dot" /> {c.connection}</div>
      {calls.call && <section className="call-panel" aria-label="Chamada"><div><strong>{calls.status}</strong><span>{calls.call.caller === c.profile.status ? calls.call.callee : calls.call.caller}</span></div>
        {calls.call.state === 'RINGING' && calls.call.callee === c.profile.status ? <><button className="primary" disabled={calls.busy} onClick={() => { void calls.accept(); }}>Aceitar</button><button className="danger" onClick={() => { void calls.end(true); }}>Recusar</button></> : <><button disabled={calls.call.state === 'RINGING'} aria-pressed={calls.muted} onClick={calls.toggleMute}>{calls.muted ? 'Ativar microfone' : 'Silenciar microfone'}</button><button disabled={calls.status !== 'Em chamada'} onClick={() => { void calls.share(); }}>{calls.sharing ? 'Parar transmissão' : 'Compartilhar tela'}</button><button className="danger" onClick={() => { void calls.end(); }}>Encerrar chamada</button></>}
        <Media stream={calls.remote} video={calls.remoteSharing} />{calls.preview && <div className="local-preview"><small>Sua transmissão</small><Media stream={calls.preview} muted video /></div>}
      </section>}
      <div className="message-history" ref={historyRef} role="log" aria-label="Histórico da conversa" aria-live="polite">
        {!conversation ? <section className="welcome"><span className="welcome-icon">SC</span><p className="eyebrow">SEU PRÓXIMO PAPO COMEÇA AQUI</p><h1>Bem-vindo, {c.profile.name}</h1><p>Escolha um amigo para conversar ou entre em um servidor. As mensagens ficam na sua conta e aparecem também no celular.</p></section> : <>
          <section className="welcome"><span className="welcome-icon">{server ? '#' : '@'}</span><h1>{server ? `Bem-vindo a #${conversation.name}` : `Converse com ${conversation.name}`}</h1><p>{conversation.description}</p></section>
          {c.hasOlder && <button className="text-button" disabled={c.loading} onClick={() => { void c.loadOlder(); }}>Carregar mensagens anteriores</button>}{c.loading && <p role="status" className="muted">Carregando mensagens…</p>}
          {c.messages.map(item => { const href = attachmentHref(item.attachmentUrl); return <article className="message" key={item.id}><Avatar name={item.senderUsername} /><div><div className="message-meta"><strong className={item.senderEmail === c.profile.status ? 'own-name' : ''}>{item.senderUsername}</strong><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</time></div>{item.content && <p>{item.content}</p>}{href && <a className="attachment" href={href} target="_blank" rel="noopener noreferrer">{item.attachmentType === 'IMAGE' && <img src={href} alt="Imagem anexada" loading="lazy" />}Abrir anexo ↗</a>}</div></article>; })}
          {!c.loading && !c.messages.length && <p className="empty-message">A conversa está começando. Envie a primeira mensagem.</p>}
        </>}
      </div>
      {conversation && <form className="composer" onSubmit={event => { event.preventDefault(); void c.sendMessage(); }}>
        {c.file && <div className="file-selected">{c.file.name}<button type="button" aria-label="Remover anexo" onClick={() => c.chooseFile(null)}>×</button></div>}
        <div className="composer-field">{conversation.channelId !== undefined && <><input type="file" ref={fileRef} className="sr-only" tabIndex={-1} onChange={event => { c.chooseFile(event.target.files?.[0] ?? null); event.target.value = ''; }} /><button type="button" className="attach-button" aria-label="Anexar arquivo" disabled={c.busy} onClick={() => fileRef.current?.click()}>+</button></>}
          <label className="sr-only" htmlFor="message">Mensagem para {conversation.name}</label><textarea id="message" rows={2} maxLength={2000} placeholder={`Conversar com ${conversation.name}`} value={c.draft} onChange={event => c.setDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void c.sendMessage(); } }} /><button className="primary" disabled={c.busy || (!c.draft.trim() && !c.file)} type="submit">{c.busy ? 'Enviando…' : 'Enviar ↗'}</button></div><small>Enter para enviar · Shift + Enter para nova linha <span>{c.draft.length}/2000</span></small>
      </form>}
    </main>
    {c.profileOpen && <ProfileDialog controller={c} logout={logout} error={error} />}{dialog && <ServerDialog kind={dialog} controller={c} error={error} close={() => setDialog(null)} />}
  </div>;
}
