import { useEffect, useRef, useState } from 'react';
import type { ChatController } from '../controllers/useChatController';

function Avatar({ name }: { name: string }) { return <span className="avatar" aria-hidden="true">{name.slice(0, 2).toUpperCase()}</span>; }

function ProfileDialog({ controller: c }: { controller: ChatController }) {
  const [name, setName] = useState(c.profile.name);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} onCancel={() => c.setProfileOpen(false)} onClose={() => c.setProfileOpen(false)}>
    <form onSubmit={event => { event.preventDefault(); c.saveProfile(name); }}>
      <h2>Seu perfil</h2><p>Como você quer aparecer nas próximas mensagens?</p>
      <label htmlFor="profile-name">Nome</label><input autoFocus id="profile-name" value={name} onChange={event => setName(event.target.value)} maxLength={40} required />
      <div className="dialog-actions"><button type="button" onClick={() => c.setProfileOpen(false)}>Cancelar</button><button className="primary" disabled={!name.trim()}>Salvar</button></div>
    </form>
  </dialog>;
}

export function ChatApp({ controller: c }: { controller: ChatController }) {
  const historyRef = useRef<HTMLDivElement>(null);
  useEffect(() => { const el = historyRef.current; if (el) el.scrollTop = el.scrollHeight; }, [c.messages.length, c.conversation.id]);
  const server = c.servers.find(item => item.id === c.serverId);
  return <div className="app-shell">
    <nav className="server-rail" aria-label="Servidores">
      <button className={`server-icon home ${c.serverId === null ? 'selected' : ''}`} title="Amigos" aria-label="Amigos" aria-pressed={c.serverId === null} onClick={() => c.selectArea(null)}>SC</button>
      <span className="rail-divider" />
      {c.servers.map(item => <button key={item.id} className={`server-icon ${item.id === c.serverId ? 'selected' : ''}`} title={item.name} aria-label={item.name} aria-pressed={item.id === c.serverId} onClick={() => c.selectArea(item.id)}>{item.initials}</button>)}
      <span className="rail-caption">SAMUS<br />CHAT</span>
    </nav>
    <aside className="sidebar">
      <header className="sidebar-header"><strong>{server?.name ?? 'Amigos'}</strong><span className="badge">PREVIEW</span></header>
      <div className="sidebar-body">
        <label className="sr-only" htmlFor="search">Buscar {server ? 'canais' : 'amigos'}</label>
        <input id="search" className="search" placeholder={server ? 'Buscar canais' : 'Buscar amigos'} value={c.query} onChange={event => c.setQuery(event.target.value)} />
        <p className="section-label">{server ? 'CANAIS DE TEXTO' : 'CONVERSAS PRIVADAS'}</p>
        <nav aria-label={server ? 'Canais' : 'Conversas'}>{c.conversations.map(item => <button key={item.id} className={`conversation-link ${item.id === c.conversation.id ? 'active' : ''}`} aria-current={item.id === c.conversation.id ? 'page' : undefined} onClick={() => c.selectConversation(item.id)}><span>{server ? '#' : '@'}</span>{item.name}</button>)}</nav>
        {!c.conversations.length && <p className="muted">Nenhum resultado.</p>}
        <div className="sidebar-note"><span className="status-dot" /> Seu espaço para se conectar<p>Troque ideias, acompanhe projetos e encontre seus amigos.</p></div>
      </div>
      <button className="profile-button" onClick={() => c.setProfileOpen(true)}><Avatar name={c.profile.name} /><span><strong>{c.profile.name}</strong><small><span className="status-dot" /> {c.profile.status}</small></span><span className="profile-edit">Editar</span></button>
    </aside>
    <main className="chat">
      <header className="chat-header"><span className="channel-symbol">{server ? '#' : '@'}</span><strong>{c.conversation.name}</strong><span className="header-description">{c.conversation.description}</span></header>
      <div className="prototype-banner">Protótipo local · dados fictícios · mensagens temporárias</div>
      <div className="message-history" ref={historyRef} role="log" aria-label="Histórico da conversa" aria-live="polite">
        <section className="welcome"><span className="welcome-icon">{server ? '#' : '@'}</span><p className="eyebrow">SEU PRÓXIMO PAPO COMEÇA AQUI</p><h1>{server ? `Bem-vindo a #${c.conversation.name}` : `Converse com ${c.conversation.name}`}</h1><p>{c.conversation.description}</p></section>
        <div className="date-divider"><span>Conversa de demonstração</span></div>
        {c.messages.map(item => <article className="message" key={item.id}><Avatar name={item.author} /><div><div className="message-meta"><strong className={item.own ? 'own-name' : ''}>{item.author}</strong><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</time></div><p>{item.text}</p></div></article>)}
        {!c.messages.length && <p className="empty-message">A conversa está começando. Envie a primeira mensagem.</p>}
      </div>
      <form className="composer" onSubmit={event => { event.preventDefault(); c.sendMessage(); }}>
        <div className="composer-field"><label className="sr-only" htmlFor="message">Mensagem para {c.conversation.name}</label><textarea id="message" rows={1} maxLength={2000} placeholder={`Conversar em ${server ? '#' : '@'}${c.conversation.name}`} value={c.draft} onChange={event => c.setDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); c.sendMessage(); } }} /><button className="primary" disabled={!c.draft.trim()} type="submit">Enviar <span aria-hidden="true">↗</span></button></div>
        <small>Enter para enviar · Shift + Enter para nova linha <span>{c.draft.length}/2000</span></small>
      </form>
    </main>
    {c.profileOpen && <ProfileDialog controller={c} />}
  </div>;
}
