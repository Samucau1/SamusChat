import { useState } from 'react';
import type { ChatRepository, Profile } from '../models/chat';

export function useChatController(repository: ChatRepository) {
  const [serverId, setServerId] = useState<string | null>('samus');
  const [conversationId, setConversationId] = useState('general');
  const [messages, setMessages] = useState(repository.initialMessages);
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState('');
  const [profileOpen, setProfileOpen] = useState(false);
  const [profile, setProfile] = useState<Profile>({ name: 'Você', status: 'Disponível' });
  const conversations = repository.conversations.filter(item =>
    (serverId ? item.serverId === serverId : item.kind === 'direct') && item.name.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR')));
  const conversation = repository.conversations.find(item => item.id === conversationId)!;

  function selectArea(id: string | null) {
    setServerId(id); setQuery(''); setDraft('');
    const first = repository.conversations.find(item => id ? item.serverId === id : item.kind === 'direct');
    if (first) setConversationId(first.id);
  }
  function selectConversation(id: string) { setConversationId(id); setDraft(''); }
  function sendMessage() {
    const text = draft.trim();
    if (!text || text.length > 2000) return;
    setMessages(previous => [...previous, { id: crypto.randomUUID(), conversationId, author: profile.name, text, createdAt: new Date().toISOString(), own: true }]);
    setDraft('');
  }
  function saveProfile(name: string) {
    const normalized = name.trim();
    if (!normalized || normalized.length > 40) return;
    setProfile(previous => ({ ...previous, name: normalized })); setProfileOpen(false);
  }
  return { servers: repository.servers, serverId, conversations, conversation, messages: messages.filter(item => item.conversationId === conversationId), draft, setDraft, query, setQuery, profile, profileOpen, setProfileOpen, selectArea, selectConversation, sendMessage, saveProfile };
}
export type ChatController = ReturnType<typeof useChatController>;
