export interface User { id: number; username: string; email: string; avatarUrl?: string; bannerUrl?: string; backgroundUrl?: string; accentColor?: string; bio?: string }
export interface Channel { id: number; name: string; type: string }
export interface ApiServer { id: number; name: string; description?: string; ownerEmail?: string; channels: Channel[]; iconUrl?: string; bannerUrl?: string; backgroundUrl?: string; accentColor?: string }
export interface Contact { email: string; username: string }
export type MemberRole = 'ADMIN' | 'MOD' | 'USER';
export interface Member { id: number; userEmail: string; username: string; role: MemberRole; joinedAt: string }
export interface VoiceRoom { channelId: number; participants: Contact[]; sessions: CallSession[]; capacity: number }
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
  logout() { return this.post<void>('/api/auth/logout'); }
  async attachment(value: string) {
    const url = new URL(value, window.location.origin);
    if (!/^\/uploads\/[0-9a-f-]{36}\.[a-z0-9]+$/.test(url.pathname)) throw new ApiError('Anexo inválido.', 0);
    const response = await fetch(url.pathname, { headers: { Authorization: `Bearer ${this.token}` }, signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new ApiError('Não foi possível acessar este anexo.', response.status);
    if (Number(response.headers.get('Content-Length') ?? 0) > 10 * 1024 * 1024) throw new ApiError('Anexo maior que 10 MB.', 0);
    return response.blob();
  }
  register(username: string, email: string, password: string) { return this.post<{ token: string }>('/api/auth/register', { username, email, password }); }
  googleLogin(idToken: string, password?: string) { return this.post<{ token: string }>('/api/auth/google', { idToken, password }); }
  googleGis(credential: string, csrfToken: string, password?: string) { return this.post<{ token: string }>('/api/auth/google/gis', { credential, g_csrf_token: csrfToken, password }); }
  requestRecovery(email: string) { return this.post<string>('/api/auth/password/request', { email }); }
  verifyRecovery(email: string, code: string) { return this.post<{ resetToken: string }>('/api/auth/password/verify', { email, code }); }
  resetPassword(email: string, resetToken: string, password: string) { return this.post<string>('/api/auth/password/reset', { email, resetToken, password }); }
  profile() { return this.request<User>('/api/users/me'); }
  saveProfile(name: string) { return this.request<User>(`/api/users/me/username?newUsername=${encodeURIComponent(name)}`, { method: 'PUT' }); }
  saveAppearance(bio: string, accentColor: string) { return this.request<User>('/api/users/me/appearance', { method: 'PUT', body: JSON.stringify({ bio, accentColor }) }); }
  saveServerAppearance(id: number, accentColor: string) { return this.request<ApiServer>(`/api/servers/${id}/appearance`, { method: 'PUT', body: JSON.stringify({ accentColor }) }); }
  profileMedia(slot: 'avatar' | 'banner' | 'background', file: File | null) { return this.appearanceMedia<User>(`/api/users/me/media/${slot}`, file); }
  serverMedia(id: number, slot: 'icon' | 'banner' | 'background', file: File | null) { return this.appearanceMedia<ApiServer>(`/api/servers/${id}/media/${slot}`, file); }
  private appearanceMedia<T>(path: string, file: File | null) {
    if (!file) return this.request<T>(path, { method: 'DELETE' });
    if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type) || !file.size || file.size > 4 * 1024 * 1024 || (file.type === 'image/gif' && file.size > 2 * 1024 * 1024)) throw new ApiError('Use PNG/JPG de até 4 MB ou GIF de até 2 MB.', 0);
    const body = new FormData(); body.append('file', file);
    return this.request<T>(path, { method: 'POST', body });
  }
  servers() { return this.request<ApiServer[]>('/api/servers'); }
  server(id: number) { return this.request<ApiServer>(`/api/servers/${id}`); }
  members(id: number) { return this.request<Member[]>(`/api/servers/${id}/members`); }
  updateRole(id: number, userEmail: string, role: MemberRole) { return this.request<Member>(`/api/servers/${id}/members/role`, { method: 'PUT', body: JSON.stringify({ userEmail, role }) }); }
  removeMember(id: number, email: string) { return this.request<void>(`/api/servers/${id}/members/${encodeURIComponent(email)}`, { method: 'DELETE' }); }
  contacts() { return this.request<Contact[]>('/api/calls/contacts'); }
  createServer(name: string, description: string) { return this.post<ApiServer>('/api/servers', { name, description }); }
  joinServer(id: number) { return this.post<string>(`/api/servers/${id}/join`); }
  createChannel(id: number, name: string, type: 'TEXT' | 'VOICE' = 'TEXT') { return this.post<Channel>(`/api/servers/${id}/channels`, { name, type }); }
  deleteMessage(channelId: number, messageId: number) { return this.request<void>(`/api/channels/${channelId}/messages/${messageId}`, { method: 'DELETE' }); }
  deleteFile(fileUrl: string) { return this.request<void>(`/api/files?fileUrl=${encodeURIComponent(fileUrl)}`, { method: 'DELETE' }); }
  registerDevice(fcmToken: string) { return this.post<void>('/api/devices/register', { fcmToken }); }
  unregisterDevice(fcmToken: string) { return this.request<void>(`/api/devices/unregister?fcmToken=${encodeURIComponent(fcmToken)}`, { method: 'DELETE' }); }
  joinCallRoom(id: number) { return this.post<VoiceRoom>(`/api/channels/${id}/call/join`); }
  callRoom(id: number) { return this.request<VoiceRoom>(`/api/channels/${id}/call`); }
  leaveCallRoom(id: number) { return this.post<string>(`/api/channels/${id}/call/leave`); }
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
