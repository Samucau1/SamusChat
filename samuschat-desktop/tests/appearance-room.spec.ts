import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';

const clientIp = () => `198.21.${Math.floor(Math.random() * 254) + 1}.${Math.floor(Math.random() * 254) + 1}`;
const test = base.extend({ extraHTTPHeaders: async ({}, use) => { await use({ 'X-Forwarded-For': clientIp() }); } });
const password = 'AppearanceTest123!';
async function account(request: APIRequestContext, prefix: string) {
  const username = `${prefix}_${crypto.randomUUID().slice(0, 8)}`, email = `${username}@example.com`;
  const result = await request.post('/api/auth/register', { data: { username, email, password } });
  expect(result.ok()).toBeTruthy();
  return { username, email, token: (await result.json()).data.token as string };
}
async function login(page: Page, email: string) {
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click(); await expect(page.getByRole('button', { name: 'Amigos', exact: true })).toBeVisible();
}
const gif = { name: 'avatar.gif', mimeType: 'image/gif', buffer: Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64') };

test('perfil GIF e servidor personalizado persistem; membros, cargos e exclusão funcionam', async ({ page, request }) => {
  const a = await account(request, 'custom_a'), b = await account(request, 'custom_b');
  await login(page, a.email);
  await page.locator('.profile-button').click();
  await page.getByLabel('Foto ou GIF do perfil', { exact: true }).setInputFiles(gif);
  await expect(page.getByRole('dialog').locator('img.avatar')).toHaveAttribute('src', /\.png$/);
  await page.getByLabel('Fundo do perfil', { exact: true }).setInputFiles(gif);
  await expect(page.locator('main.chat')).toHaveCSS('background-image', /appearance.*\.png/);
  await page.getByLabel('Sobre mim', { exact: true }).fill('Meu perfil personalizado');
  await page.getByLabel('Cor do perfil', { exact: true }).fill('#123456');
  await page.getByRole('button', { name: 'Salvar aparência', exact: true }).click();
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await page.reload(); await page.locator('.profile-button').click();
  await expect(page.getByLabel('Sobre mim', { exact: true })).toHaveValue('Meu perfil personalizado');
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await page.getByLabel('Economizar animações').uncheck();
  await expect(page.locator('.profile-button img')).toHaveAttribute('src', /\.gif$/);
  await page.getByLabel('Economizar animações').check();
  await page.getByRole('button', { name: 'Criar servidor', exact: true }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Servidor visual'); await page.getByRole('button', { name: 'Criar', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  const id = Number((await page.locator('.server-id').innerText()).split(':')[1]);
  expect((await request.post(`/api/servers/${id}/join`, { headers: { Authorization: `Bearer ${b.token}` } })).ok()).toBeTruthy();
  await page.getByRole('button', { name: 'Personalizar servidor', exact: true }).click();
  await page.getByLabel('Ícone do servidor', { exact: true }).setInputFiles(gif);
  await expect(page.getByRole('button', { name: 'Remover ícone do servidor', exact: true })).toBeEnabled();
  await page.getByLabel('Fundo do servidor', { exact: true }).setInputFiles(gif);
  await expect(page.locator('main.chat')).toHaveCSS('background-image', /appearance.*\.png/);
  await page.getByRole('button', { name: 'Fechar', exact: true }).click();
  await page.getByRole('button', { name: 'Membros do servidor', exact: true }).click();
  await page.getByLabel(`Cargo de ${b.username}`).selectOption('MOD');
  await expect(page.getByLabel(`Cargo de ${b.username}`)).toHaveValue('MOD');
  await page.getByRole('button', { name: 'Fechar', exact: true }).click();
  await page.getByLabel(/^Mensagem para/).fill('Excluir esta mensagem'); await page.getByRole('button', { name: 'Enviar ↗' }).click();
  await expect(page.locator('.message')).toHaveCount(1); await page.getByRole('button', { name: /^Excluir mensagem/ }).click();
  await page.getByRole('button', { name: 'Confirmar', exact: true }).click(); await expect(page.locator('.message')).toHaveCount(0);
});

test('canal de chamada: dois clientes, áudio, tela econômica e saída', async ({ page, browser, request }) => {
  const a = await account(request, 'room_a'), b = await account(request, 'room_b');
  const created = await request.post('/api/servers', { headers: { Authorization: `Bearer ${a.token}` }, data: { name: 'Sala desktop' } });
  const server = (await created.json()).data;
  expect((await request.post(`/api/servers/${server.id}/join`, { headers: { Authorization: `Bearer ${b.token}` } })).ok()).toBeTruthy();
  const channel = await request.post(`/api/servers/${server.id}/channels`, { headers: { Authorization: `Bearer ${a.token}` }, data: { name: 'reunião', type: 'VOICE' } });
  expect(channel.ok()).toBeTruthy();
  const context = await browser.newContext({ permissions: ['microphone'], extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } });
  const other = await context.newPage();
  try {
    await login(page, a.email); await login(other, b.email);
    await page.getByRole('button', { name: 'Sala desktop', exact: true }).click(); await other.getByRole('button', { name: 'Sala desktop', exact: true }).click();
    await page.getByRole('button', { name: '☎ reunião', exact: true }).click(); await other.getByRole('button', { name: '☎ reunião', exact: true }).click();
    await expect(page.locator('.voice-room')).toContainText('2/6 participantes');
    await expect(page.locator('.voice-room video')).toHaveCount(1, { timeout: 30000 });
    await page.getByRole('button', { name: 'Silenciar microfone do canal' }).click();
    await expect(page.getByRole('button', { name: 'Ativar microfone do canal' })).toHaveAttribute('aria-pressed', 'true');
    await page.evaluate(() => {
      Object.defineProperty(navigator.mediaDevices, 'getDisplayMedia', { value: async () => {
        const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 360;
        const stream = canvas.captureStream(10), context = canvas.getContext('2d')!;
        const timer = setInterval(() => { context.fillStyle = '#5865f2'; context.fillRect(0, 0, 640, 360); }, 100);
        const track = stream.getVideoTracks()[0], stop = track.stop.bind(track); track.stop = () => { clearInterval(timer); stop(); };
        return stream;
      } });
    });
    await page.getByRole('button', { name: 'Compartilhar tela no canal', exact: true }).click();
    await expect(other.locator('.voice-room video.call-video')).toBeVisible({ timeout: 30000 });
    await expect.poll(() => other.locator('.voice-room video.call-video').evaluate((video: HTMLVideoElement) => video.videoWidth)).toBeGreaterThan(0);
    await page.getByRole('button', { name: 'Parar transmissão do canal' }).click(); await expect(other.locator('.voice-room video.call-video')).toHaveCount(0);
    await other.getByRole('button', { name: 'Sair do canal de chamada' }).click();
    await expect(page.locator('.voice-room')).toContainText('1/6 participantes');
    await page.getByRole('button', { name: 'Sair do canal de chamada' }).click(); await expect(page.locator('.voice-room')).toHaveCount(0);
    const rooms = await request.get('/api/calls', { headers: { Authorization: `Bearer ${a.token}` } }); expect((await rooms.json()).data).toEqual([]);
  } finally { await context.close(); }
});
