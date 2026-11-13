import { test, expect, type Page } from '@playwright/test';

const key = 'samuschat.desktop.session';
const user = { id: 42, username: 'SavedUser', email: 'saved@example.test' };

async function mountSession(page: Page) {
  await page.evaluate(async () => {
    const reactPath = '/node_modules/.vite/deps/react.js', domPath = '/node_modules/.vite/deps/react-dom_client.js';
    const sessionPath = '/src/controllers/useSession.ts';
    const react = await import(reactPath), dom = await import(domPath), { useSession } = await import(sessionPath);
    const React = react.default ?? react, { createRoot } = dom.default ?? dom;
    const container = document.createElement('div'); document.body.append(container);
    const probe = (window as any).sessionProbe = {};
    function Client() { probe.current = useSession(); return null; }
    const root = createRoot(container); root.render(React.createElement(Client));
    probe.close = () => { root.unmount(); container.remove(); };
  });
  await expect.poll(() => page.evaluate(() => (window as any).sessionProbe.current?.restoring)).toBe(false);
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({ json: { success: true, data: [] } }));
  await page.route('**/api/users/me', route => route.fulfill({ json: { success: true, data: user } }));
});

test('login fica salvo ao fechar a aba e reabrir a conta', async ({ page, context }) => {
  await page.route('**/api/auth/login', route => route.fulfill({ json: { success: true, data: { token: 'access', refreshToken: 'refresh' } } }));
  await page.goto('/'); await mountSession(page);
  await page.evaluate(() => (window as any).sessionProbe.current.authenticate(false, '', 'saved@example.test', 'example-password'));
  await expect.poll(() => page.evaluate(storageKey => JSON.parse(localStorage.getItem(storageKey) ?? '{}').user?.email, key)).toBe(user.email);
  expect(await page.evaluate(storageKey => sessionStorage.getItem(storageKey), key)).toBeNull();
  const address = page.url(); await page.close();
  const reopened = await context.newPage();
  await reopened.route('**/api/**', route => route.fulfill({ json: { success: true, data: [] } }));
  await reopened.route('**/api/users/me', route => route.fulfill({ json: { success: true, data: user } }));
  await reopened.goto(address); await mountSession(reopened);
  expect(await reopened.evaluate(() => (window as any).sessionProbe.current.user.email)).toBe(user.email);
  expect(await reopened.evaluate(storageKey => JSON.parse(localStorage.getItem(storageKey)!).refreshToken, key)).toBe('refresh');
});

test('sessão antiga da aba migra para armazenamento persistente com renovação', async ({ page }) => {
  await page.route('**/api/auth/session', route => route.fulfill({ json: { success: true, data: { token: 'legacy', refreshToken: 'renewable' } } }));
  await page.goto('/');
  await page.evaluate(storageKey => sessionStorage.setItem(storageKey, 'legacy'), key);
  await mountSession(page);
  await expect.poll(() => page.evaluate(storageKey => JSON.parse(localStorage.getItem(storageKey) ?? '{}').refreshToken, key)).toBe('renewable');
  expect(await page.evaluate(storageKey => sessionStorage.getItem(storageKey), key)).toBeNull();
});

test('requisições simultâneas renovam uma vez e repetem com o novo token', async ({ page }) => {
  let renewals = 0;
  await page.route('**/api/users/me', route => route.fulfill(route.request().headers().authorization === 'Bearer renewed'
    ? { json: { success: true, data: user } } : { status: 401, json: { success: false } }));
  await page.route('**/api/auth/refresh', async route => {
    renewals++;
    await new Promise(resolve => setTimeout(resolve, 100));
    await route.fulfill({ json: { success: true, data: { token: 'renewed' } } });
  });
  await page.goto('/');
  const result = await page.evaluate(async () => {
    const modulePath = '/src/models/apiRepository.ts';
    const { ApiRepository } = await import(modulePath);
    const api = new ApiRepository('expired', 'refresh');
    const users = await Promise.all([api.profile(), api.profile(), api.profile()]);
    return { token: api.token, users: users.map((value: any) => value.email) };
  });
  expect(renewals).toBe(1);
  expect(result).toEqual({ token: 'renewed', users: [user.email, user.email, user.email] });
});

test('falha de rede preserva a conta salva ao abrir o aplicativo', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(({ storageKey, profile }) => localStorage.setItem(storageKey,
    JSON.stringify({ token: 'access', refreshToken: 'refresh', user: profile })), { storageKey: key, profile: user });
  await page.route('**/api/users/me', route => route.abort('connectionrefused'));
  await mountSession(page);
  expect(await page.evaluate(() => (window as any).sessionProbe.current.user.email)).toBe(user.email);
  expect(await page.evaluate(storageKey => JSON.parse(localStorage.getItem(storageKey)!).refreshToken, key)).toBe('refresh');
});

test('renovação pendente não restaura uma sessão encerrada', async ({ page }) => {
  let release!: () => void, requested = false;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/users/me', route => route.fulfill({ status: 401, json: { success: false } }));
  await page.route('**/api/auth/refresh', async route => {
    requested = true; await gate;
    await route.fulfill({ json: { success: true, data: { token: 'renewed' } } });
  });
  await page.goto('/');
  await page.evaluate(async () => {
    const modulePath = '/src/models/apiRepository.ts', { ApiRepository } = await import(modulePath);
    const probe = (window as any).renewalProbe = { api: new ApiRepository('expired', 'refresh') };
    probe.pending = probe.api.profile().catch(() => null);
  });
  await expect.poll(() => requested).toBe(true);
  await page.evaluate(() => (window as any).renewalProbe.api.remember(''));
  release();
  await page.evaluate(() => (window as any).renewalProbe.pending);
  expect(await page.evaluate(() => (window as any).renewalProbe.api.token)).toBe('');
});

for (const status of [503, 401]) {
  test(`renovação com HTTP ${status} mantém ou encerra a conta conforme a resposta`, async ({ page }) => {
    await page.goto('/');
    await page.evaluate(({ storageKey, profile }) => localStorage.setItem(storageKey,
      JSON.stringify({ token: 'expired', refreshToken: 'refresh', user: profile })), { storageKey: key, profile: user });
    await page.route('**/api/users/me', route => route.fulfill({ status: 401, json: { success: false } }));
    await page.route('**/api/auth/refresh', route => route.fulfill({ status, json: { success: false } }));
    await mountSession(page);
    const saved = await page.evaluate(storageKey => JSON.parse(localStorage.getItem(storageKey) ?? 'null'), key);
    if (status === 503) {
      expect(saved.refreshToken).toBe('refresh');
      expect(await page.evaluate(() => (window as any).sessionProbe.current.user.email)).toBe(user.email);
    } else {
      expect(saved).toBeNull();
      expect(await page.evaluate(() => (window as any).sessionProbe.current.user)).toBeNull();
    }
  });
}
