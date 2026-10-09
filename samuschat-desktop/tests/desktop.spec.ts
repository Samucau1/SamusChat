import { test as base, expect, type Page, type APIRequestContext } from '@playwright/test';

// Simulate distinct local clients without disabling the API's rate limits.
const clientIp = () => `198.18.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 254) + 1}`;
const test = base.extend({ extraHTTPHeaders: async ({}, use) => { await use({ 'X-Forwarded-For': clientIp() }); } });

const password = 'DesktopTest123!';
async function register(request: APIRequestContext, prefix: string) {
  const name = `${prefix}_${crypto.randomUUID().slice(0, 8)}`; const email = `${name}@example.com`;
  const response = await request.post('/api/auth/register', { data: { username: name, email, password } });
  expect(response.ok(), await response.text()).toBeTruthy(); const result = await response.json(); expect(result.success).toBe(true);
  return { name, email, token: result.data.token as string };
}
async function login(page: Page, email: string) {
  await page.goto('http://127.0.0.1:5174/'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password); await page.getByRole('button', { name: 'Entrar', exact: true }).click(); await expect(page.getByRole('button', { name: 'Amigos', exact: true })).toBeVisible();
}
async function server(request: APIRequestContext, token: string) {
  const response = await request.post('/api/servers', { headers: { Authorization: `Bearer ${token}` }, data: { name: `Projeto_${crypto.randomUUID().slice(0, 6)}`, description: 'Teste desktop isolado' } });
  expect(response.ok()).toBeTruthy(); return (await response.json()).data;
}

test('cadastro, sessão restaurada, perfil salvo e logout', async ({ page }) => {
  const name = `desktop_${crypto.randomUUID().slice(0, 8)}`;
  await page.goto('/'); await page.getByRole('button', { name: 'Criar uma conta', exact: true }).click();
  await page.getByLabel('Nome de usuário', { exact: true }).fill(name); await page.getByLabel('Email', { exact: true }).fill(`${name}@example.com`); await page.getByLabel('Senha', { exact: true }).fill(password); await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
  await expect(page.getByRole('heading', { name: `Bem-vindo, ${name}` })).toBeVisible(); await page.reload(); await expect(page.getByRole('heading', { name: `Bem-vindo, ${name}` })).toBeVisible();
  await page.locator('.profile-button').click(); await page.getByLabel('Nome de usuário').fill(`${name}_novo`); await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: `Bem-vindo, ${name}_novo` })).toBeVisible();
  await page.locator('.profile-button').click(); await page.getByRole('button', { name: 'Sair da conta' }).click(); await expect(page.getByRole('heading', { name: 'Bom ter você por aqui' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('samuschat.desktop.session'))).toBeNull();
});

test('duas contas: criar/entrar em servidor, STOMP, anexo e canal novo', async ({ page, browser, request }) => {
  const a = await register(request, 'canal_a'); const b = await register(request, 'canal_b');
  await login(page, a.email); await page.getByRole('button', { name: 'Criar servidor', exact: true }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Servidor desktop'); await page.getByLabel('Descrição', { exact: true }).fill('Integração com API existente'); await page.getByRole('button', { name: 'Criar', exact: true }).click(); await expect(page.getByRole('dialog')).not.toBeVisible();
  const id = Number((await page.locator('.server-id').innerText()).split(':')[1]);
  const context = await browser.newContext({ permissions: ['microphone'], extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } }); const other = await context.newPage();
  try {
    await login(other, b.email); await other.getByRole('button', { name: 'Entrar em servidor', exact: true }).click(); await other.getByLabel('ID do servidor').fill(String(id)); await other.getByRole('dialog').getByRole('button', { name: 'Entrar', exact: true }).click(); await expect(other.getByRole('dialog')).not.toBeVisible(); await other.locator('.conversation-link').first().click();
    await expect(page.getByRole('status').filter({ hasText: 'Ao vivo' })).toBeVisible(); await expect(other.getByRole('status').filter({ hasText: 'Ao vivo' })).toBeVisible();
    await page.getByLabel(/^Mensagem para/).fill('Mensagem real desktop'); await page.getByRole('button', { name: 'Enviar ↗' }).click(); await expect(other.locator('.message p').filter({ hasText: 'Mensagem real desktop' })).toHaveCount(1);
    await page.locator('input[type=file]').setInputFiles({ name: 'desktop.txt', mimeType: 'text/plain', buffer: Buffer.from('arquivo desktop') }); await page.getByRole('button', { name: 'Enviar ↗' }).click(); await expect(other.getByRole('link', { name: 'Abrir anexo ↗' })).toBeVisible();
    const href = await other.getByRole('link', { name: 'Abrir anexo ↗' }).getAttribute('href'); expect(await (await request.get(href!)).text()).toBe('arquivo desktop');
    await page.getByRole('button', { name: '+ Criar canal', exact: true }).click(); await page.getByRole('dialog').getByLabel('Nome').fill('novo-canal'); await page.getByRole('button', { name: 'Criar', exact: true }).click(); await expect(page.getByRole('dialog')).not.toBeVisible(); await expect(page.getByRole('heading', { name: 'Bem-vindo a #novo-canal' })).toBeVisible();
    await page.screenshot({ path: 'test-results/desktop-chat.png', fullPage: true });
  } finally { await context.close(); }
});

test('mensagem privada chega ao outro cliente e falha preserva rascunho', async ({ page, browser, request }) => {
  const a = await register(request, 'dm_a'); const b = await register(request, 'dm_b');
  const context = await browser.newContext({ extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } }); const other = await context.newPage();
  try {
    await login(page, a.email); await login(other, b.email); await page.getByRole('button', { name: `@ ${b.name}`, exact: true }).click(); await other.getByRole('button', { name: `@ ${a.name}`, exact: true }).click();
    await page.getByLabel(/^Mensagem para/).fill('Privada entre computadores'); await page.getByLabel(/^Mensagem para/).press('Enter'); await expect(other.locator('.message p').filter({ hasText: 'Privada entre computadores' })).toHaveCount(1);
    await page.route('**/api/direct-messages?*', async route => { if (route.request().method() === 'POST') await route.fulfill({ status: 503, json: { success: false, error: 'Falha simulada de rede' } }); else await route.continue(); });
    await page.getByLabel(/^Mensagem para/).fill('Preservar meu rascunho'); await page.getByRole('button', { name: 'Enviar ↗' }).click(); await expect(page.getByRole('alert')).toContainText('Falha simulada'); await expect(page.getByLabel(/^Mensagem para/)).toHaveValue('Preservar meu rascunho');
  } finally { await context.close(); }
});

test('paginação carrega histórico anterior sem duplicatas', async ({ page, request }) => {
  const a = await register(request, 'historico'); const s = await server(request, a.token); const id = s.channels[0].id;
  for (let i = 0; i < 52; i++) expect((await request.post(`/api/channels/${id}/messages`, { headers: { Authorization: `Bearer ${a.token}`, 'X-Forwarded-For': `198.19.0.${Math.floor(i / 20) + 1}` }, data: { content: `Histórico ${i}` } })).ok()).toBeTruthy();
  await login(page, a.email); await page.getByRole('button', { name: s.name, exact: true }).click(); await expect(page.locator('.message')).toHaveCount(50); await page.getByRole('button', { name: 'Carregar mensagens anteriores' }).click(); await expect(page.locator('.message')).toHaveCount(52); await expect(page.locator('.message p').filter({ hasText: /^Histórico 0$/ })).toHaveCount(1);
});

test('chamada individual: convite, aceite, mídia WebRTC, microfone e encerramento remoto', async ({ page, browser, request }) => {
  const a = await register(request, 'voz_a'); const b = await register(request, 'voz_b');
  const context = await browser.newContext({ permissions: ['microphone'], extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } }); const other = await context.newPage();
  try {
    await login(page, a.email); await login(other, b.email); await page.getByRole('button', { name: `@ ${b.name}`, exact: true }).click(); await page.getByRole('button', { name: 'Ligar', exact: true }).click();
    await expect(other.getByRole('button', { name: 'Aceitar', exact: true })).toBeVisible(); await other.getByRole('button', { name: 'Aceitar', exact: true }).click(); await expect(page.locator('.call-panel strong')).toHaveText('Em chamada', { timeout: 30000 }); await expect(other.locator('.call-panel strong')).toHaveText('Em chamada', { timeout: 30000 });
    await page.getByRole('button', { name: 'Silenciar microfone' }).click(); await expect(page.getByRole('button', { name: 'Ativar microfone' })).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('button', { name: 'Ativar microfone' }).click();
    // Exercise real WebRTC track replacement with a synthetic screen source;
    // the native browser picker remains a manual hardware test.
    await page.evaluate(() => {
      Object.defineProperty(navigator.mediaDevices, 'getDisplayMedia', { value: async () => {
        const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 360;
        const context = canvas.getContext('2d')!; const stream = canvas.captureStream(15);
        const timer = setInterval(() => { context.fillStyle = '#5865f2'; context.fillRect(0, 0, 640, 360); context.fillStyle = '#fff'; context.fillText(String(Date.now()), 40, 40); }, 60);
        const track = stream.getVideoTracks()[0]; const stop = track.stop.bind(track); track.stop = () => { clearInterval(timer); stop(); };
        return stream;
      } });
    });
    await page.getByRole('button', { name: 'Compartilhar tela' }).click(); await expect(page.locator('.local-preview')).toBeVisible(); await expect(other.locator('video.call-video')).toBeVisible();
    await expect.poll(() => other.locator('video.call-video').evaluate((video: HTMLVideoElement) => video.videoWidth)).toBeGreaterThan(0);
    await page.getByRole('button', { name: 'Parar transmissão' }).click(); await expect(page.locator('.local-preview')).not.toBeVisible(); await expect(other.locator('video.call-video')).not.toBeVisible();
    await other.getByRole('button', { name: 'Encerrar chamada' }).click(); await expect(page.locator('.call-panel')).not.toBeVisible();
    const active = await request.get('/api/calls', { headers: { Authorization: `Bearer ${a.token}` } }); expect((await active.json()).data).toEqual([]);
  } finally { await context.close(); }
});

test('erro de login aparece e formulário permanece utilizável', async ({ page, request }) => {
  const a = await register(request, 'login_erro'); await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(a.email); await page.getByLabel('Senha', { exact: true }).fill('SenhaIncorreta123!'); await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible(); await expect(page.getByLabel('Email', { exact: true })).toHaveValue(a.email);
  await page.getByLabel('Senha', { exact: true }).fill(password); await page.getByRole('button', { name: 'Entrar', exact: true }).click(); await expect(page.getByRole('button', { name: 'Amigos', exact: true })).toBeVisible();
  await page.setViewportSize({ width: 1024, height: 768 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
