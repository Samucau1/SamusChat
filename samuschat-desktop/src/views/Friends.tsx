import { useEffect, useRef, useState } from 'react';
import type { ApiRepository, PublicProfile } from '../models/apiRepository';
import { AppearanceImage } from './Appearance';

export function SearchIcon() { return <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 5 5"/></svg>; }
function Photo({ person }: { person: PublicProfile }) { return person.avatarUrl ? <AppearanceImage url={person.avatarUrl} alt="" className="avatar avatar-image" /> : <span className="avatar">{person.username.slice(0,2).toUpperCase()}</span>; }
type Action = 'request' | 'accept' | 'remove';
export function FriendActions({ person, busy, act, talk }: { person: PublicProfile; busy: boolean; act(action: Action): void; talk?(): void }) {
  if (person.relationship === 'SELF') return null;
  if (person.relationship === 'FRIEND') return <>{talk && <button className="primary" onClick={talk}>Conversar</button>}<button className="social-secondary" disabled={busy} onClick={()=>act('remove')}>Remover amigo</button></>;
  if (person.relationship === 'INCOMING') return <><button className="primary" disabled={busy} onClick={()=>act('accept')}>Aceitar</button><button className="social-secondary" disabled={busy} onClick={()=>act('remove')}>Recusar</button></>;
  if (person.relationship === 'OUTGOING') return <><span className="social-tag">Solicitação enviada</span><button className="social-secondary" disabled={busy} onClick={()=>act('remove')}>Cancelar solicitação</button></>;
  return <button className="primary" disabled={busy} onClick={()=>act('request')}>Adicionar amigo</button>;
}
export function FriendsPanel({ api, initialSearch, onProfile, onTalk, onChange }: {
  api: ApiRepository; initialSearch: boolean; onProfile(id: string): void; onTalk(person: PublicProfile): void; onChange(): Promise<unknown>;
}) {
  const [tab,setTab] = useState<'friends'|'pending'|'search'>(initialSearch?'search':'friends');
  const [query,setQuery]=useState(''); const [people,setPeople]=useState<PublicProfile[]>([]);const [results,setResults]=useState<PublicProfile[]>([]);
  const [loading,setLoading]=useState(true);const [searching,setSearching]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [notice,setNotice]=useState('');
  useEffect(()=>{if(initialSearch)setTab('search');},[initialSearch]);
  useEffect(()=>{let alive=true;const abort=new AbortController();const load=()=>{void api.friends(abort.signal).then(value=>{if(alive){setPeople(value);setError('');}}).catch(e=>{if(alive)setError(e.message);}).finally(()=>{if(alive)setLoading(false);});};load();const timer=setInterval(load,15000);return()=>{alive=false;abort.abort();clearInterval(timer);};},[api]);
  useEffect(()=>{setResults([]);setSearching(false);if(tab!=='search'||query.trim().length<2)return;let alive=true;const abort=new AbortController();setSearching(true);const timer=setTimeout(()=>{void api.searchFriends(query.trim(),abort.signal).then(value=>{if(alive){setResults(value);setError('');}}).catch(e=>{if(alive)setError(e.message);}).finally(()=>{if(alive)setSearching(false);});},300);return()=>{alive=false;abort.abort();clearTimeout(timer);};},[api,query,tab]);
  async function act(person: PublicProfile,action: Action) {
    if(busy)return;setBusy(true);setError('');setNotice('');
    try {const updated=await api.friendAction(person.publicId,action);setResults(previous=>previous.map(p=>p.publicId===updated.publicId?updated:p));setPeople(await api.friends());await onChange();setNotice(action==='accept'?'Amizade aceita. Vocês já podem conversar.':action==='request'?'Solicitação enviada. Aguarde a pessoa aceitar.':person.relationship==='FRIEND'?'Amizade removida. O histórico da conversa foi preservado.':'Solicitação removida.');}
    catch(e){setError(e instanceof Error?e.message:'Não foi possível atualizar a amizade.');}finally{setBusy(false);}
  }
  const friends=people.filter(p=>p.relationship==='FRIEND'),pending=people.filter(p=>p.relationship!=='FRIEND');
  const shown=tab==='search'?results:tab==='pending'?pending:friends;
  return <section className="friends-panel" aria-label="Central de amigos"><p className="eyebrow">CONEXÕES DO SEU JEITO</p><h1>Seus amigos</h1><p className="muted">Membros de um servidor não são adicionados automaticamente. A amizade começa com um convite aceito.</p>
    <div className="social-tabs" role="tablist" aria-label="Listas de amigos">{([['friends',`Amigos · ${friends.length}`],['pending',`Solicitações · ${pending.length}`],['search','Encontrar pessoas']] as const).map(([id,label])=><button key={id} role="tab" aria-selected={tab===id} className={tab===id?'selected':''} onClick={()=>{setTab(id);setNotice('');}}>{label}</button>)}</div>
    {tab==='search'&&<div className="social-search"><SearchIcon/><label className="sr-only" htmlFor="friend-search">Procurar por nome ou ID</label><input autoFocus id="friend-search" value={query} maxLength={100} placeholder="Nome de usuário ou ID completo" onChange={e=>setQuery(e.target.value)}/></div>}
    {error&&<p className="error-notice" role="alert">{error}</p>}{notice&&<p className="social-success" role="status">{notice}</p>}
    {(loading||searching)&&<p className="muted" role="status">{searching?'Procurando pessoas…':'Carregando amigos…'}</p>}
    {!loading&&!searching&&!shown.length&&<div className="social-empty"><SearchIcon/><h2>{tab==='search'?(query.trim().length<2?'Encontre sua próxima conversa':'Nenhuma pessoa encontrada'):tab==='pending'?'Nenhuma solicitação pendente':'Sua lista de amigos começa aqui'}</h2><p>{tab==='search'?'Digite pelo menos dois caracteres do nome ou cole o ID completo.':tab==='pending'?'Os convites recebidos e enviados aparecerão aqui.':'Use a lupa para procurar alguém pelo nome ou ID e enviar um convite.'}</p>{tab==='friends'&&<button className="primary" onClick={()=>setTab('search')}>Encontrar amigos</button>}</div>}
    <ul className="social-list">{shown.map(person=><li key={person.publicId}><button className="social-person" aria-label={`Ver perfil de ${person.username}`} onClick={()=>onProfile(person.publicId)}><Photo person={person}/><span><strong>{person.username}</strong><small>{person.relationship==='INCOMING'?'Quer ser seu amigo':person.relationship==='FRIEND'?'Seu amigo':person.publicId}</small></span></button><div className="social-actions"><FriendActions person={person} busy={busy} act={action=>void act(person,action)} talk={()=>onTalk(person)}/></div></li>)}</ul>
  </section>;
}
