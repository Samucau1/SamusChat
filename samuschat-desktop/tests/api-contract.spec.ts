import { test, expect } from '@playwright/test';
import { ApiRepository, ApiError } from '../src/models/apiRepository';

test('contratos REST desktop: verbos, URLs, codificação, corpo e autenticação', async () => {
  const api = new ApiRepository('jwt');
  const original = globalThis.fetch;
  let captured: { url: string; init: RequestInit };
  globalThis.fetch = async (url, init) => {
    captured = { url: String(url), init: init ?? {} };
    return new Response(JSON.stringify({ success: true, data: [] }), { headers: { 'Content-Type': 'application/json' } });
  };
  const email = 'ana+pc@example.com';
  const cases: [() => Promise<unknown>, string, string, unknown?][] = [
    [() => api.login(email, 'secret'), 'POST', '/api/auth/login', { email, password: 'secret' }],
    [() => api.register('Ana', email, 'secret'), 'POST', '/api/auth/register', { username: 'Ana', email, password: 'secret' }],
    [() => api.googleLogin('credential'), 'POST', '/api/auth/google', { idToken: 'credential' }],
    [() => api.googleGis('credential', 'csrf'), 'POST', '/api/auth/google/gis', { credential: 'credential', g_csrf_token: 'csrf' }],
    [() => api.requestRecovery(email), 'POST', '/api/auth/password/request', { email }],
    [() => api.verifyRecovery(email, '0012'), 'POST', '/api/auth/password/verify', { email, code: '0012' }],
    [() => api.resetPassword(email, 'reset', 'secret'), 'POST', '/api/auth/password/reset', { email, resetToken: 'reset', password: 'secret' }],
    [() => api.profile(), 'GET', '/api/users/me'],
    [() => api.saveProfile('Ana Silva'), 'PUT', '/api/users/me/username?newUsername=Ana%20Silva'],
    [() => api.saveAppearance('Olá', '#123456'), 'PUT', '/api/users/me/appearance', { bio: 'Olá', accentColor: '#123456' }],
    [() => api.servers(), 'GET', '/api/servers'],
    [() => api.server(3), 'GET', '/api/servers/3'],
    [() => api.createServer('Projeto', 'descrição'), 'POST', '/api/servers', { name: 'Projeto', description: 'descrição' }],
    [() => api.joinServer(3), 'POST', '/api/servers/3/join'],
    [() => api.createChannel(3, 'voz', 'VOICE'), 'POST', '/api/servers/3/channels', { name: 'voz', type: 'VOICE' }],
    [() => api.members(3), 'GET', '/api/servers/3/members'],
    [() => api.updateRole(3, email, 'MOD'), 'PUT', '/api/servers/3/members/role', { userEmail: email, role: 'MOD' }],
    [() => api.removeMember(3, email), 'DELETE', '/api/servers/3/members/ana%2Bpc%40example.com'],
    [() => api.history({ channelId: 4 }, 2), 'GET', '/api/channels/4/messages?page=2&size=50'],
    [() => api.history({ contact: email }, 2), 'GET', '/api/direct-messages?contact=ana%2Bpc%40example.com&page=2'],
    [() => api.send({ channelId: 4 }, 'olá'), 'POST', '/api/channels/4/messages', { content: 'olá' }],
    [() => api.send({ contact: email }, 'olá'), 'POST', '/api/direct-messages?contact=ana%2Bpc%40example.com', { content: 'olá' }],
    [() => api.deleteMessage(4, 7), 'DELETE', '/api/channels/4/messages/7'],
    [() => api.deleteFile('/uploads/a b.txt'), 'DELETE', '/api/files?fileUrl=%2Fuploads%2Fa%20b.txt'],
    [() => api.contacts(), 'GET', '/api/calls/contacts'],
    [() => api.ice(), 'GET', '/api/calls/ice'],
    [() => api.calls(), 'GET', '/api/calls'],
    [() => api.call('id'), 'GET', '/api/calls/id'],
    [() => api.callAction('id', 'offer', 'sdp'), 'POST', '/api/calls/id', { action: 'offer', sdp: 'sdp' }],
    [() => api.joinCallRoom(4), 'POST', '/api/channels/4/call/join'],
    [() => api.callRoom(4), 'GET', '/api/channels/4/call'],
    [() => api.leaveCallRoom(4), 'POST', '/api/channels/4/call/leave'],
    [() => api.registerDevice('a+b'), 'POST', '/api/devices/register', { fcmToken: 'a+b' }],
    [() => api.unregisterDevice('a+b'), 'DELETE', '/api/devices/unregister?fcmToken=a%2Bb'],
    [() => api.saveServerAppearance(3, '#123456'), 'PUT', '/api/servers/3/appearance', { accentColor: '#123456' }],
    [() => api.profileMedia('avatar', null), 'DELETE', '/api/users/me/media/avatar'],
    [() => api.serverMedia(3, 'background', null), 'DELETE', '/api/servers/3/media/background'],
  ];
  try {
    for (const [call, method, url, body] of cases) {
      await call();
      expect(captured!.url).toBe(url);
      expect(captured!.init.method ?? 'GET').toBe(method);
      expect(new Headers(captured!.init.headers).get('Authorization')).toBe('Bearer jwt');
      if (body !== undefined) expect(JSON.parse(String(captured!.init.body))).toEqual(body);
    }
    const file = new File(['GIF89a'], 'a.gif', { type: 'image/gif' });
    await api.profileMedia('avatar', file);
    expect(captured!.url).toBe('/api/users/me/media/avatar');
    expect((captured!.init.body as FormData).get('file')).toBeInstanceOf(File);
    expect(new Headers(captured!.init.headers).has('Content-Type')).toBe(false);
    await api.serverMedia(3, 'icon', file);
    expect(captured!.url).toBe('/api/servers/3/media/icon');
    await api.upload(4, file, 'legenda');
    expect((captured!.init.body as FormData).get('content')).toBe('legenda');
    await api.invite(email);
    expect(captured!.url).toBe('/api/calls');
    expect(JSON.parse(String(captured!.init.body))).toMatchObject({ callee: email, requestId: expect.any(String) });
  } finally { globalThis.fetch = original; }
});

test('falhas HTTP, envelopes inválidos e limites locais preservam o contrato de erro', async () => {
  const api = new ApiRepository();
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, error: 'Sem permissão' }), { status: 401 });
    await expect(api.profile()).rejects.toMatchObject({ status: 401, message: 'Sem permissão' });
    globalThis.fetch = async () => new Response('not-json');
    await expect(api.profile()).rejects.toBeInstanceOf(ApiError);
    globalThis.fetch = async () => { throw new TypeError('network'); };
    await expect(api.profile()).rejects.toMatchObject({ status: 0 });
    expect(() => api.profileMedia('avatar', new File(['x'], 'a.svg', { type: 'image/svg+xml' }))).toThrow('Use PNG/JPG');
  } finally { globalThis.fetch = original; }
});
