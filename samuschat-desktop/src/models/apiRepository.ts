export interface User { id: number; username: string; email: string }
export interface Channel { id: number; name: string; type: string }
export interface ApiServer { id: number; name: string; description?: string; channels: Channel[] }
export interface Contact { email: string; username: string }
export interface ApiMessage { id: number; content: string | null; senderEmail: string; senderUsername: string; createdAt: string; attachmentUrl?: string; attachmentType?: string; type?: string }
export interface CallSession { id: string; caller: string; callee: string; state: string; offer: string | null; answer: string | null; channelId: number | null }
export class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }
export class ApiRepository {
  constructor(public token = '') {}
  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (this.token) headers.set('Authorization', `Bearer ${this.token}`);
    if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    let response: Response;
    try { response = await fetch(path, { ...init, headers, signal: init.signal ?? AbortSignal.timeout(30000) }); }
    catch { throw new ApiError('Não foi possível acessar o servidor. Confira sua conexão e tente novamente.', 0); }
    const envelope = await response.json().catch(() => null);
    if (!response.ok || !envelope?.success) throw new ApiError(envelope?.error || envelope?.message || 'Não foi possível concluir a solicitação.', response.status);
    return envelope.data as T;
  }
  post<T>(path: string, body?: unknown) { return this.request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }); }
  login(email: string, password: string) { return this.post<{ token: string }>('/api/auth/login', { email, password }); }
  register(username: string, email: string, password: string) { return this.post<{ token: string }>('/api/auth/register', { username, email, password }); }
  profile() { return this.request<User>('/api/users/me'); }
  saveProfile(name: string) { return this.request<User>(`/api/users/me/username?newUsername=${encodeURIComponent(name)}`, { method: 'PUT' }); }
  servers() { return this.request<ApiServer[]>('/api/servers'); }
  contacts() { return this.request<Contact[]>('/api/calls/contacts'); }
  createServer(name: string, description: string) { return this.post<ApiServer>('/api/servers', { name, description }); }
  joinServer(id: number) { return this.post<string>(`/api/servers/${id}/join`); }
  createChannel(id: number, name: string) { return this.post<Channel>(`/api/servers/${id}/channels`, { name, type: 'TEXT' }); }
  history(conversation: { channelId?: number; contact?: string }, page = 0, signal?: AbortSignal) {
    const path = conversation.channelId !== undefined ? `/api/channels/${conversation.channelId}/messages?page=${page}&size=50` : `/api/direct-messages?contact=${encodeURIComponent(conversation.contact!)}&page=${page}`;
    return this.request<ApiMessage[]>(path, { signal });
  }
  send(conversation: { channelId?: number; contact?: string }, content: string) {
    return this.post<ApiMessage>(conversation.channelId !== undefined ? `/api/channels/${conversation.channelId}/messages` : `/api/direct-messages?contact=${encodeURIComponent(conversation.contact!)}`, { content });
  }
  upload(channelId: number, file: File, content: string) {
    const form = new FormData(); form.append('file', file); if (content) form.append('content', content);
    return this.request<ApiMessage>(`/api/channels/${channelId}/upload`, { method: 'POST', body: form });
  }
  calls() { return this.request<CallSession[]>('/api/calls'); }
  call(id: string) { return this.request<CallSession>(`/api/calls/${id}`); }
  invite(callee: string) { return this.post<CallSession>('/api/calls', { callee, requestId: crypto.randomUUID() }); }
  callAction(id: string, action: string, sdp?: string) { return this.post<CallSession>(`/api/calls/${id}`, { action, sdp }); }
  ice() { return this.request<RTCIceServer[]>('/api/calls/ice'); }
}
export function mergeApiMessages(current: ApiMessage[], incoming: ApiMessage[]) {
  return [...new Map([...current, ...incoming].map(message => [message.id, message])).values()].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id);
}
export function attachmentHref(value: string | undefined): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value, window.location.origin);
    if (!['http:', 'https:'].includes(url.protocol)) return undefined;
    return url.pathname.startsWith('/uploads/') ? url.pathname + url.search : url.href;
  } catch { return undefined; }
}
