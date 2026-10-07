import type { ChatRepository } from './chat';

export const mockRepository: ChatRepository = {
  servers: [
    { id: 'samus', name: 'Comunidade Samus', initials: 'S' },
    { id: 'games', name: 'Sala dos jogos', initials: 'J' },
  ],
  conversations: [
    { id: 'general', serverId: 'samus', name: 'geral', description: 'Um espaço para conversar e compartilhar ideias.', kind: 'channel' },
    { id: 'projects', serverId: 'samus', name: 'projetos', description: 'O que você está criando hoje?', kind: 'channel' },
    { id: 'games-general', serverId: 'games', name: 'geral', description: 'Encontre sua próxima partida.', kind: 'channel' },
    { id: 'alex', name: 'Alex', description: 'Conversa privada de demonstração', kind: 'direct' },
    { id: 'marina', name: 'Marina', description: 'Conversa privada de demonstração', kind: 'direct' },
  ],
  initialMessages: [
    { id: '1', conversationId: 'general', author: 'Marina', text: 'Bem-vindos à nossa comunidade! Como está o dia de vocês?', createdAt: '2026-10-06T13:20:00-03:00', own: false },
    { id: '2', conversationId: 'general', author: 'Alex', text: 'Estou explorando a versão para computador. Gostei de ter os canais sempre à mão.', createdAt: '2026-10-06T13:22:00-03:00', own: false },
    { id: '3', conversationId: 'general', author: 'Você', text: 'Vamos construir esse espaço juntos 🚀', createdAt: '2026-10-06T13:24:00-03:00', own: true },
    { id: '4', conversationId: 'alex', author: 'Alex', text: 'Oi! Que bom te ver por aqui. Vamos conversar?', createdAt: '2026-10-06T13:25:00-03:00', own: false },
  ],
};
