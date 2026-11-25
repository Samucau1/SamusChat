import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import {reconnectingClient, recoveryTask} from '../models/recovery';
import {useDirectInbox} from './useDirectInbox';
import { clearNotificationDestination, notificationDestination, type NotificationDestination } from '../models/notificationDestination';
import { ApiRepository, mergeApiMessages, type ApiMessage, type ApiServer, type Contact, type User, type Member, type MemberRole } from '../models/apiRepository';

export interface Conversation { id: string; name: string; description: string; channelId?: number; contact?: string; serverId?: number }
export function useChatController(api: ApiRepository, user: User, setUser: (user: User) => void, onError: (error: unknown) => void, clearError: () => void = () => {}) {
  const [servers, setServers] = useState<ApiServer[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [directoriesLoaded, setDirectoriesLoaded] = useState(false);
  const [pushNotice, setPushNotice] = useState<NotificationDestination | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [serverId, setServerId] = useState<number | null>(null);
  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ApiMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState('');
  const [profileOpen, setProfileOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [hasOlder, setHasOlder] = useState(false);
  const [connection, setConnection] = useState('Disponível');
  const [file, setFile] = useState<File | null>(null);
  const drafts = useRef(new Map<string, { text: string; file: File | null }>());
  const selected = useRef(conversation); selected.current = conversation;
  const working = useRef(false);
  const errorRef = useRef(onError); errorRef.current = onError;
  const syncSelected = useRef<()=>void>(()=>{});
  const directInbox=useDirectInbox(api,user,selected,setMessages,syncSelected,messages);
  const available: Conversation[] = serverId === null ? contacts.map(item => ({ id: `dm:${item.email}`, name: item.username, description: 'Conversa privada', contact: item.email })) :
    (servers.find(item => item.id === serverId)?.channels ?? []).filter(channel => channel.type === 'TEXT').map(channel => ({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId }));
  const conversations = available.filter(item => [item.name, item.contact ?? ''].some(value => value.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR'))));
  async function directory() { const lists = await Promise.all([api.contacts(), api.directContacts()]); return [...new Map(lists.flat().map(item => [item.email, item])).values()]; }
  async function refresh() {
    const [nextServers, nextContacts] = await Promise.all([api.servers(), directory()]);
    setServers(nextServers); setContacts(nextContacts); return nextServers;
  }
  useEffect(() => { let alive = true; Promise.all([api.servers(), directory()]).then(([s, c]) => { if (alive) { setServers(s); setContacts(c); setDirectoriesLoaded(true); } }).catch(problem => { if (alive) errorRef.current(problem); }); return () => { alive = false; }; }, [api]);
  useEffect(() => {
    let alive = true;
    const recovery = recoveryTask(async () => { try { const value = await directory(); if (alive) setContacts(value); recovery.reset(); } catch { /* Back off until the next refresh. */ } }, () => true, () => !document.hidden && navigator.onLine, 30000, 60000);
    const refreshContacts = () => { void recovery.request(); };
    recovery.schedule();
    window.addEventListener('focus', refreshContacts);
    return () => { alive = false; recovery.stop(); window.removeEventListener('focus', refreshContacts); };
  }, [api]);
  function updateDraft(text: string) {
    if (selected.current) drafts.current.set(selected.current.id, { text, file });
    setDraft(text);
  }
  function switchConversation(item: Conversation | null) {
    selected.current = item;
    const saved = item ? drafts.current.get(item.id) : undefined;
    setConversation(item); setDraft(saved?.text ?? ''); setFile(saved?.file ?? null);
  }
  function selectConversation(item: Conversation) { switchConversation(item); }
  function startDirect(person: { publicId: string; username: string; avatarUrl?: string }) {
    setContacts(previous => [...previous.filter(p=>p.email!==person.publicId),{email:person.publicId,username:person.username,avatarUrl:person.avatarUrl}]);
    setServerId(null);setQuery('');switchConversation({id:`dm:${person.publicId}`,name:person.username,description:'Conversa privada',contact:person.publicId});
  }
  function selectArea(id: number | null) {
    setServerId(id); setQuery(''); setMembers([]);
    const channel = servers.find(item => item.id === id)?.channels.find(item => item.type === 'TEXT');
    switchConversation(channel ? { id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: id! } : null);
  }
  function openNotification(target: NotificationDestination) {
    if (target.recipientId !== user.publicId) {
      errorRef.current(new Error('Este aviso pertence a outra conta do SamusChat.'));
      return;
    }
    setPushNotice(null);
    const contact = contacts.find(item => item.email === target.contactId);
    if (contact) {
      setServerId(null); setQuery('');
      switchConversation({ id: `dm:${contact.email}`, name: contact.username, description: 'Conversa privada', contact: contact.email });
      return;
    }
    const server = servers.find(item => item.channels.some(channel => channel.id === target.channelId && channel.type === 'TEXT'));
    const channel = server?.channels.find(item => item.id === target.channelId);
    if (server && channel) {
      setServerId(server.id); setQuery('');
      switchConversation({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: server.id });
      return;
    }
    errorRef.current(new Error('A conversa deste aviso não está disponível para esta conta.'));
  }
  useEffect(() => {
    if (!directoriesLoaded) return;
    const target = notificationDestination(window.location.search);
    if (target) {
      openNotification(target);
      clearNotificationDestination();
    }
  }, [directoriesLoaded, contacts, servers]);
  useEffect(() => {
    const requested = (event: Event) => {
      const target = (event as CustomEvent<NotificationDestination>).detail;
      // main.tsx keeps the destination in the URL until the account directories are ready.
      if (!directoriesLoaded) return;
      if (target?.recipientId) { openNotification(target); clearNotificationDestination(); }
    };
    window.addEventListener('samuschat:open-notification', requested);
    return () => window.removeEventListener('samuschat:open-notification', requested);
  }, [directoriesLoaded, contacts, servers, user.publicId]);
  useEffect(() => {
    if (!('serviceWorker' in navigator)) return;
    const received = (event: MessageEvent) => {
      if (event.data?.type !== 'SAMUSCHAT_NEW_MESSAGE' || typeof event.data.url !== 'string') return;
      if (!URL.canParse(event.data.url, window.location.origin)) return;
      const url = new URL(event.data.url, window.location.origin);
      if (url.origin !== window.location.origin || url.pathname !== '/') return;
      const target = notificationDestination(url.search);
      if (!target || target.recipientId !== user.publicId) return;
      if (target.contactId && (directInbox.connected || selected.current?.contact === target.contactId)) return;
      if (target.channelId && selected.current?.channelId === target.channelId) return;
      setPushNotice(target);
    };
    navigator.serviceWorker.addEventListener('message', received);
    return () => navigator.serviceWorker.removeEventListener('message', received);
  }, [user.publicId, directInbox.connected]);
  function openNotice() {
    if (!directInbox.notice) return;
    openNotification({ recipientId: user.publicId ?? '', contactId: directInbox.notice.senderEmail });
    directInbox.dismissNotice();
  }
  useEffect(() => {
    setMessages([]); setPage(0); setHasOlder(false);
    if (!conversation) { setLoading(false); setConnection('Disponível'); return; }
    const current = conversation; const abort = new AbortController();
    let alive = true; let connected = false; let initial = true;
    const sync = async (initial = false) => {
      try { const items = await api.history(current, 0, abort.signal); if (alive) { setMessages(previous => mergeApiMessages(previous, items)); if (initial) setHasOlder(items.length === 50); } }
      catch (problem) { if (alive) { setConnection('Sem conexão'); errorRef.current(problem); } }
      finally { if (alive && initial) setLoading(false); }
    };
    const recovery = recoveryTask(async () => { const first = initial; initial = false; await sync(first); }, () => !(current.contact ? directInbox.live.current : connected));
    setLoading(true); void recovery.request();
    syncSelected.current=()=>{recovery.reset();void recovery.request();};
    let client: Client | undefined;
    let stopClient: (() => void) | undefined;
    if (current.channelId !== undefined) {
      setConnection('Conectando');
      const url = new URL('/ws/websocket', window.location.href); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
      const session = reconnectingClient({ brokerURL: url.href, connectHeaders: { Authorization: `Bearer ${api.token}` }, connectionTimeout: 10000,
        onConnect: () => {
          if (!alive) return; connected = true; setConnection('Ao vivo');
          client!.subscribe(current.channelId !== undefined ? `/topic/channel/${current.channelId}` : '/user/queue/direct-messages', event => {
            try { const item = JSON.parse(event.body) as ApiMessage; if (alive && item.id !== undefined && (current.channelId !== undefined || (item.senderEmail === (user.publicId ?? user.email) && item.recipientEmail === current.contact) || (item.senderEmail === current.contact && item.recipientEmail === (user.publicId ?? user.email)))) setMessages(previous => item.type === 'DELETE' ? previous.filter(existing => existing.id !== item.id) : mergeApiMessages(previous, [item])); }
            catch { if (alive) void recovery.request(); }
          }); recovery.reset(); void recovery.request();
        },
        onWebSocketClose: () => { connected = false; if (alive) { setConnection('Reconectando'); recovery.schedule(); } },
        onStompError: () => { connected = false; if (alive) setConnection('Atualização periódica'); client?.forceDisconnect(); },
      }); client = session.client; stopClient = session.stop; client.activate();
    } else setConnection('Atualização periódica');
    const offline = () => { connected = false; if (alive) setConnection('Reconectando'); client?.forceDisconnect(); };
    window.addEventListener('offline', offline);
    const online = () => { recovery.reset(); recovery.schedule(); };
    window.addEventListener('online', online);
    return () => { alive = false; syncSelected.current=()=>{}; recovery.stop(); window.removeEventListener('offline', offline); window.removeEventListener('online', online); abort.abort(); stopClient?.(); };
  }, [api, conversation?.id]);
  async function loadOlder() {
    if (!conversation || loading || !hasOlder) return;
    const current = conversation; setLoading(true);
    try { const items = await api.history(current, page + 1); if (selected.current?.id === current.id) { setMessages(previous => mergeApiMessages(previous, items)); setPage(value => value + 1); setHasOlder(items.length === 50); } }
    catch (problem) { errorRef.current(problem); }
    finally { if (selected.current?.id === current.id) setLoading(false); }
  }
  async function sendMessage() {
    const current = conversation; const text = draft.trim(); const attachment = file;
    if (!current || working.current || (!text && !attachment) || text.length > 2000) return;
    working.current = true; setBusy(true);
    try {
      const message = attachment && current.channelId !== undefined ? await api.upload(current.channelId, attachment, text) : await api.send(current, text);
      const saved = drafts.current.get(current.id);
      if (saved) {
        const next = { text: saved.text === draft ? '' : saved.text, file: saved.file === attachment ? null : saved.file };
        if (!next.text && !next.file) drafts.current.delete(current.id);
        else drafts.current.set(current.id, next);
      }
      if (selected.current?.id === current.id) { setMessages(previous => mergeApiMessages(previous, [message])); setDraft(value => value === draft ? '' : value); setFile(value => value === attachment ? null : value); }
    } catch (problem) { errorRef.current(problem); }
    finally { working.current = false; setBusy(false); }
  }
  function chooseFile(value: File | null) { if (value && value.size > 10 * 1024 * 1024) { errorRef.current(new Error('O arquivo deve ter no máximo 10 MB.')); return; } if (selected.current) drafts.current.set(selected.current.id, { text: draft, file: value }); setFile(value); }
  async function run(operation: () => Promise<void>, rethrow = false) { if (working.current) return false; working.current = true; setBusy(true); clearError(); try { await operation(); return true; } catch (problem) { errorRef.current(problem); if (rethrow) throw problem; return false; } finally { working.current = false; setBusy(false); } }
  async function saveProfile(name: string) { return run(async () => { setUser(await api.saveProfile(name.trim())); setProfileOpen(false); }); }
  async function saveAppearance(bio: string, color: string) { return run(async () => { setUser(await api.saveAppearance(bio.trim(), color)); }); }
  async function profileMedia(slot: 'avatar' | 'banner' | 'background', file: File | null) { return run(async () => { setUser(await api.profileMedia(slot, file)); }, true); }
  async function saveServerAppearance(color: string) { if (serverId === null) return false; const id = serverId; return run(async () => { const result = await api.saveServerAppearance(id, color); setServers(previous => previous.map(item => item.id === id ? result : item)); }); }
  async function serverMedia(slot: 'icon' | 'banner' | 'background', file: File | null) { if (serverId === null) return false; const id = serverId; return run(async () => { const result = await api.serverMedia(id, slot, file); setServers(previous => previous.map(item => item.id === id ? result : item)); }, true); }
  async function createServer(name: string, description: string) { return await run(async () => { const result = await api.createServer(name.trim(), description.trim()); await refresh(); setServerId(result.id); const channel = result.channels.find(item => item.type === 'TEXT'); switchConversation(channel ? { id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: result.id } : null); }); }
  async function joinServer(code: string) { return await run(async () => { await api.joinServer(code.trim()); const next = await refresh(); const joined = next.find(item => !servers.some(previous => previous.id === item.id)); setServerId(joined?.id ?? null); switchConversation(null); }); }
  async function createChannel(name: string, type: 'TEXT' | 'VOICE' = 'TEXT') { if (serverId === null) return false; const id = serverId; return await run(async () => { const channel = await api.createChannel(id, name.trim(), type); await refresh(); if (type === 'TEXT') selectConversation({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: id }); }); }
  async function loadMembers() { if (serverId === null) return; const id = serverId; return run(async () => { setMembers(await api.members(id)); }); }
  async function updateRole(email: string, role: MemberRole) { if (serverId === null) return; const id = serverId; return run(async () => { const result = await api.updateRole(id, email, role); setMembers(previous => previous.map(item => item.userEmail === email ? result : item)); }); }
  async function removeMember(email: string) { if (serverId === null) return false; const id = serverId; return run(async () => { await api.removeMember(id, email); if (email === user.email || email === user.publicId) { await refresh(); selectArea(null); } else setMembers(previous => previous.filter(item => item.userEmail !== email)); }); }
  async function deleteMessage(id: number) { const current = conversation; if (current?.channelId === undefined) return false; return run(async () => { await api.deleteMessage(current.channelId!, id); if (selected.current?.id === current.id) setMessages(previous => previous.filter(item => item.id !== id)); }); }
  function hasDraft(id: string) { const saved = drafts.current.get(id); return Boolean(saved && (saved.text || saved.file)); }
  async function loadAttachment(url: string) { try { return await api.attachment(url); } catch (problem) { errorRef.current(problem); return null; } }
  return { api, startDirect, ...directInbox, pushNotice, openNotification, openNotice, dismissPushNotice: () => setPushNotice(null), contacts, loadAttachment, user, saveAppearance, profileMedia, saveServerAppearance, serverMedia, members, loadMembers, updateRole, removeMember, deleteMessage, hasDraft, servers, serverId, conversations, conversation, messages, draft, setDraft: updateDraft, query, setQuery, profile: { name: user.username, status: user.publicId ?? user.email }, profileOpen, setProfileOpen, selectArea, selectConversation, sendMessage, saveProfile, createServer, joinServer, createChannel, busy, loading, connection: conversation?.contact ? (directInbox.connected ? 'Ao vivo' : 'Reconectando') : connection, hasOlder, loadOlder, file, chooseFile, refresh: () => run(async () => { await refresh(); }) };
}
export type ChatController = ReturnType<typeof useChatController>;
