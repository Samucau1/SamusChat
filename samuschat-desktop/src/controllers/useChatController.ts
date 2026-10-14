import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { ApiRepository, mergeApiMessages, type ApiMessage, type ApiServer, type Contact, type User, type Member, type MemberRole } from '../models/apiRepository';

export interface Conversation { id: string; name: string; description: string; channelId?: number; contact?: string; serverId?: number }
export function useChatController(api: ApiRepository, user: User, setUser: (user: User) => void, onError: (error: unknown) => void) {
  const [servers, setServers] = useState<ApiServer[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
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
  const available: Conversation[] = serverId === null ? contacts.map(item => ({ id: `dm:${item.email}`, name: item.username, description: item.email, contact: item.email })) :
    (servers.find(item => item.id === serverId)?.channels ?? []).filter(channel => channel.type === 'TEXT').map(channel => ({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId }));
  const conversations = available.filter(item => [item.name, item.contact ?? ''].some(value => value.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR'))));
  async function refresh() {
    const [nextServers, nextContacts] = await Promise.all([api.servers(), api.contacts()]);
    setServers(nextServers); setContacts(nextContacts);
  }
  useEffect(() => { let alive = true; Promise.all([api.servers(), api.contacts()]).then(([s, c]) => { if (alive) { setServers(s); setContacts(c); } }).catch(problem => { if (alive) errorRef.current(problem); }); return () => { alive = false; }; }, [api]);
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
  function selectArea(id: number | null) {
    setServerId(id); setQuery(''); setMembers([]);
    const channel = servers.find(item => item.id === id)?.channels.find(item => item.type === 'TEXT');
    switchConversation(channel ? { id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: id! } : null);
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
  async function run(operation: () => Promise<void>) { if (working.current) return false; working.current = true; setBusy(true); try { await operation(); return true; } catch (problem) { errorRef.current(problem); return false; } finally { working.current = false; setBusy(false); } }
  async function saveProfile(name: string) { await run(async () => { setUser(await api.saveProfile(name.trim())); setProfileOpen(false); }); }
  async function saveAppearance(bio: string, color: string) { return run(async () => { setUser(await api.saveAppearance(bio.trim(), color)); }); }
  async function profileMedia(slot: 'avatar' | 'banner' | 'background', file: File | null) { return run(async () => { setUser(await api.profileMedia(slot, file)); }); }
  async function saveServerAppearance(color: string) { if (serverId === null) return false; const id = serverId; return run(async () => { const result = await api.saveServerAppearance(id, color); setServers(previous => previous.map(item => item.id === id ? result : item)); }); }
  async function serverMedia(slot: 'icon' | 'banner' | 'background', file: File | null) { if (serverId === null) return false; const id = serverId; return run(async () => { const result = await api.serverMedia(id, slot, file); setServers(previous => previous.map(item => item.id === id ? result : item)); }); }
  async function createServer(name: string, description: string) { return await run(async () => { const result = await api.createServer(name.trim(), description.trim()); await refresh(); setServerId(result.id); const channel = result.channels.find(item => item.type === 'TEXT'); switchConversation(channel ? { id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: result.id } : null); }); }
  async function joinServer(id: number) { return await run(async () => { await api.joinServer(id); await refresh(); setServerId(id); switchConversation(null); }); }
  async function createChannel(name: string, type: 'TEXT' | 'VOICE' = 'TEXT') { if (serverId === null) return false; const id = serverId; return await run(async () => { const channel = await api.createChannel(id, name.trim(), type); await refresh(); if (type === 'TEXT') selectConversation({ id: `channel:${channel.id}`, name: channel.name, description: 'Conversa do servidor', channelId: channel.id, serverId: id }); }); }
  async function loadMembers() { if (serverId === null) return; const id = serverId; return run(async () => { setMembers(await api.members(id)); }); }
  async function updateRole(email: string, role: MemberRole) { if (serverId === null) return; const id = serverId; return run(async () => { const result = await api.updateRole(id, email, role); setMembers(previous => previous.map(item => item.userEmail === email ? result : item)); }); }
  async function removeMember(email: string) { if (serverId === null) return false; const id = serverId; return run(async () => { await api.removeMember(id, email); if (email === user.email) { await refresh(); selectArea(null); } else setMembers(previous => previous.filter(item => item.userEmail !== email)); }); }
  async function deleteMessage(id: number) { const current = conversation; if (current?.channelId === undefined) return false; return run(async () => { await api.deleteMessage(current.channelId!, id); if (selected.current?.id === current.id) setMessages(previous => previous.filter(item => item.id !== id)); }); }
  function hasDraft(id: string) { const saved = drafts.current.get(id); return Boolean(saved && (saved.text || saved.file)); }
  async function loadAttachment(url: string) { try { return await api.attachment(url); } catch (problem) { errorRef.current(problem); return null; } }
  return { loadAttachment, user, saveAppearance, profileMedia, saveServerAppearance, serverMedia, members, loadMembers, updateRole, removeMember, deleteMessage, hasDraft, servers, serverId, conversations, conversation, messages, draft, setDraft: updateDraft, query, setQuery, profile: { name: user.username, status: user.email }, profileOpen, setProfileOpen, selectArea, selectConversation, sendMessage, saveProfile, createServer, joinServer, createChannel, busy, loading, connection, hasOlder, loadOlder, file, chooseFile, refresh: () => run(refresh) };
}
export type ChatController = ReturnType<typeof useChatController>;
