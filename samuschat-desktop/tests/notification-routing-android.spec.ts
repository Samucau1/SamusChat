import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

test('USB Android opens notification destinations before login, while active and after account switch', async ({ request }, info) => {
  test.skip(!process.env.SAMUSCHAT_ANDROID_SERIAL, 'Requires explicitly selected USB phone and validation APK');
  test.setTimeout(240000);
  const serial = process.env.SAMUSCHAT_ANDROID_SERIAL!;
  expect(serial).toMatch(/^[A-Za-z0-9_-]+$/);
  const app = 'com.samuschat.validation';
  const adb = (...args: string[]) => execFileSync(path.resolve('../.android-sdk/platform-tools/adb.exe'), ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 });
  const ui = () => {
    try {
      if (!adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/samus-routing-test.xml').includes('dumped to:')) return '';
      return adb('shell', 'cat', '/sdcard/samus-routing-test.xml');
    } catch { return ''; } // The accessibility root can disappear during Activity transitions; polling retries.
  };
  const nodes = () => [...ui().matchAll(/<node\b([^>]+)>/g)].map(m => Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a => [a[1], a[2]])));
  const tapNode = (node: Record<string, string>) => { const b = node.bounds.match(/\d+/g)!.map(Number); adb('shell', 'input', 'tap', String((b[0] + b[2]) >> 1), String((b[1] + b[3]) >> 1)); };
  async function tap(text: string) {
    let candidate: Record<string, string> | undefined;
    await expect.poll(() => { candidate = nodes().find(n => n.text === text || n['content-desc'] === text); return Boolean(candidate); }, { timeout: 30000, intervals: [500] }).toBe(true);
    tapNode(candidate!);
  }
  const password = 'NotificationTest42!';
  const accounts = [];
  for (const prefix of ['RouteOwner', 'RouteSender']) {
    const username = prefix + crypto.randomUUID().slice(0, 8), email = username + '@example.com';
    const response = await request.post('/api/auth/register', { data: { username, email, password } });
    expect(response.ok()).toBe(true);
    const token = (await response.json()).data.token;
    const profile = await request.get('/api/users/me', { headers: { Authorization: `Bearer ${token}` } });
    expect(profile.ok()).toBe(true);
    accounts.push({ username, email, token, publicId: (await profile.json()).data.publicId });
  }
  const [owner, sender] = accounts;
  const headers = { Authorization: `Bearer ${owner.token}` };
  const serverResponse = await request.post('/api/servers', { headers, data: { name: 'USB routing ' + crypto.randomUUID().slice(0, 6) } });
  expect(serverResponse.ok()).toBe(true);
  const server = (await serverResponse.json()).data, channel = server.channels[0];
  const marker = 'USB routing channel ' + Date.now();
  expect((await request.post(`/api/channels/${channel.id}/messages`, { headers, data: { content: marker } })).ok()).toBe(true);
  const directMarker = 'USB routing direct ' + Date.now();
  expect((await request.post('/api/direct-messages', { headers: { Authorization: `Bearer ${sender.token}` }, params: { contact: owner.publicId }, data: { content: directMarker } })).ok()).toBe(true);
  async function login(email: string) {
    await expect.poll(() => nodes().filter(n => n.class === 'android.widget.EditText').length, { timeout: 20000 }).toBe(2);
    for (const [index, value] of [email, password].entries()) {
      tapNode(nodes().filter(n => n.class === 'android.widget.EditText')[index]);
      await expect.poll(() => nodes().filter(n => n.class === 'android.widget.EditText')[index]?.focused, { timeout: 15000 }).toBe('true');
      adb('shell', 'input', 'text', value); adb('shell', 'input', 'keyevent', '4');
    }
    await tap('Entrar');
    await expect.poll(() => ui(), { timeout: 30000 }).toContain('Amigos');
  }
  function notification(recipient: string, target: 'direct' | number) {
    const args = ['shell', 'am', 'start', '-n', `${app}/com.samuschat.MainActivity`, '--es', 'type', 'NEW_MESSAGE', '--es', 'recipientId', recipient, '--es', 'channelId', String(target)];
    if (target === 'direct') args.push('--es', 'contactId', sender.publicId);
    adb(...args);
  }
  // Only the isolated validation application's disposable state is reset.
  adb('shell', 'pm', 'clear', app);
  notification(owner.publicId, 'direct');
  await login(owner.email);
  await expect.poll(() => ui(), { timeout: 30000 }).toContain(directMarker);
  notification(owner.publicId, channel.id);
  await expect.poll(() => ui(), { timeout: 30000 }).toContain(marker);
  notification(sender.publicId, 'direct');
  await expect.poll(() => ui(), { timeout: 15000 }).toContain('Este aviso pertence a outra conta.');
  // Invalid-account notices must leave the current channel in place.
  expect(ui()).toContain(marker);
  await tap('Perfil');
  for (let attempt = 0; attempt < 5 && !nodes().some(n => n.text === 'Sair da conta'); attempt++) adb('shell', 'input', 'swipe', '300', '1000', '300', '350', '300');
  await tap('Sair da conta');
  await expect.poll(() => ui(), { timeout: 20000 }).toContain('Bom te ver de novo!');
  notification(owner.publicId, channel.id);
  await login(sender.email);
  await expect.poll(() => ui(), { timeout: 20000 }).toContain('Este aviso pertence a outra conta.');
  expect(ui()).not.toContain(marker);
  await tap('Perfil');
  for (let attempt = 0; attempt < 5 && !nodes().some(n => n.text === 'Sair da conta'); attempt++) adb('shell', 'input', 'swipe', '300', '1000', '300', '350', '300');
  await tap('Sair da conta');
  await expect.poll(() => ui(), { timeout: 20000 }).toContain('Bom te ver de novo!');
  await info.attach('android-routing-evidence', { body: JSON.stringify({ directBeforeLogin: true, channelWhileActive: true, wrongAccountRejected: true, logout: true, accountSwitchRejected: true, sdk: adb('shell', 'getprop', 'ro.build.version.sdk').trim() }), contentType: 'application/json' });
});
