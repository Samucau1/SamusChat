export interface Conversation { id: string; name: string; description: string; kind: 'channel' | 'direct'; serverId?: string }
export interface Server { id: string; name: string; initials: string }
export interface Message { id: string; conversationId: string; author: string; text: string; createdAt: string; own: boolean }
export interface Profile { name: string; status: string }
export interface ChatRepository {
  conversations: Conversation[];
  servers: Server[];
  initialMessages: Message[];
}
