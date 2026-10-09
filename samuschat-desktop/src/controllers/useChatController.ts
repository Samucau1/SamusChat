import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { ApiRepository, mergeApiMessages, type ApiMessage, type ApiServer, type Contact, type User } from '../models/apiRepository';

export interface Conversation { id: string; name: string; description: string; channelId?: number; contact?: string; serverId?: number }
export function useChatController(api: ApiRepository, user: User, setUser: (user: User) => void, onError: (error: unknown) => void) {
  const [servers, setServers] = useState<ApiServer[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
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
  const selected = useRef(conversation); selected.current = conversation;
  const working = useRef(false);
  const errorRef = useRef(onError); errorRef.current = onError;
  const available: Conversation[] = serverId === null ? contacts.map(item => ({ id: `dm:${item.email}`, name: item.username, description: item.email, contact: item.email })) :
    (servers.find(item => item.id === serverId)?.channels ?? []).filter(channel => channel.type === 'TEXT').map(channel => ({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId }));
  const conversations = available.filter(item => item.name.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR')));
  async function refresh() {
    const [nextServers, nextContacts] = await Promise.all([api.servers(), api.contacts()]);
    setServers(nextServers); setContacts(nextContacts);
  }
  useEffect(() => { let alive = true; Promise.all([api.servers(), api.contacts()]).then(([s, c]) => { if (alive) { setServers(s); setContacts(c); } }).catch(problem => { if (alive) errorRef.current(problem); }); return () => { alive = false; }; }, [api]);
  function selectConversation(item: Conversation) { setConversation(item); setDraft(''); setFile(null); }
  function selectArea(id: number | null) {
    setServerId(id); setQuery(''); setDraft(''); setFile(null);
    const channel = servers.find(item => item.id === id)?.channels.find(item => item.type === 'TEXT');
    setConversation(channel ? { id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: id! } : null);
  }
  useEffect(() => {
    setMessages([]); setPage(0); setHasOlder(false);
    if (!conversation) { setLoading(false); setConnection('Disponível'); return; }
    const current = conversation; const abort = new AbortController();
    let alive = true; let polling = false; let connected = false;
    const sync = async (initial = false) => {
      if (polling) return; polling = true;
      try { const items = await api.history(current, 0, abort.signal); if (alive) { setMessages(previous => mergeApiMessages(previous, items)); if (initial) setHasOlder(items.length === 50); } }
      catch (problem) { if (alive) { setConnection('Sem conexão'); errorRef.current(problem); } }
      finally { polling = false; if (alive && initial) setLoading(false); }
    };
    setLoading(true); void sync(true);
    let client: Client | undefined;
    if (current.channelId !== undefined) {
      setConnection('Conectando');
      const url = new URL('/ws/websocket', window.location.href); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
      client = new Client({ brokerURL: url.href, connectHeaders: { Authorization: `Bearer ${api.token}` }, reconnectDelay: 3000, connectionTimeout: 10000,
        onConnect: () => {
          if (!alive) return; connected = true; setConnection('Ao vivo');
          client!.subscribe(`/topic/channel/${current.channelId}`, event => {
            try { const item = JSON.parse(event.body) as ApiMessage; if (alive && item.id !== undefined) setMessages(previous => item.type === 'DELETE' ? previous.filter(existing => existing.id !== item.id) : mergeApiMessages(previous, [item])); }
            catch { if (alive) void sync(); }
          }); void sync();
        },
        onWebSocketClose: () => { connected = false; if (alive) setConnection('Reconectando'); },
        onStompError: () => { connected = false; if (alive) setConnection('Atualização periódica'); },
      }); client.activate();
    } else setConnection('Atualização periódica');
    const timer = window.setInterval(() => { if (!connected) void sync(); }, 3000);
    return () => { alive = false; abort.abort(); clearInterval(timer); void client?.deactivate(); };
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
      if (selected.current?.id === current.id) { setMessages(previous => mergeApiMessages(previous, [message])); setDraft(value => value === draft ? '' : value); setFile(value => value === attachment ? null : value); }
    } catch (problem) { errorRef.current(problem); }
    finally { working.current = false; setBusy(false); }
  }
  function chooseFile(value: File | null) { if (value && value.size > 10 * 1024 * 1024) { errorRef.current(new Error('O arquivo deve ter no máximo 10 MB.')); return; } setFile(value); }
  async function run(operation: () => Promise<void>) { if (working.current) return false; working.current = true; setBusy(true); try { await operation(); return true; } catch (problem) { errorRef.current(problem); return false; } finally { working.current = false; setBusy(false); } }
  async function saveProfile(name: string) { await run(async () => { setUser(await api.saveProfile(name.trim())); setProfileOpen(false); }); }
  async function createServer(name: string, description: string) { return await run(async () => { const result = await api.createServer(name.trim(), description.trim()); await refresh(); setServerId(result.id); const channel = result.channels.find(item => item.type === 'TEXT'); setConversation(channel ? { id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: result.id } : null); }); }
  async function joinServer(id: number) { return await run(async () => { await api.joinServer(id); await refresh(); setServerId(id); setConversation(null); }); }
  async function createChannel(name: string) { if (serverId === null) return false; const id = serverId; return await run(async () => { const channel = await api.createChannel(id, name.trim()); await refresh(); selectConversation({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: id }); }); }
  return { servers, serverId, conversations, conversation, messages, draft, setDraft, query, setQuery, profile: { name: user.username, status: user.email }, profileOpen, setProfileOpen, selectArea, selectConversation, sendMessage, saveProfile, createServer, joinServer, createChannel, busy, loading, connection, hasOlder, loadOlder, file, chooseFile, refresh: () => run(refresh) };
}
export type ChatController = ReturnType<typeof useChatController>;
