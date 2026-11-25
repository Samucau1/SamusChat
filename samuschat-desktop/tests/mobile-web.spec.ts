import { test, expect, type Page } from '@playwright/test';
test.use({ hasTouch: true });

async function session(page: Page) {
  const user = { id: 1, username: 'Preview', publicId: 'usr_preview', email: 'preview@example.test' };
  await page.addInitScript(user => localStorage.setItem('samuschat.desktop.session', JSON.stringify({ token: 'preview-only', refreshToken: 'preview-only', user })), user);
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path === '/api/users/me') data = user;
    if (path === '/api/calls/contacts') data = [{ email: 'usr_marina', username: 'Marina' }];
    if (path === '/api/direct-messages') data = [{ id: 1, senderEmail: 'usr_marina', senderUsername: 'Marina', content: 'Uma conversa que cabe no celular.', createdAt: '2026-11-25T12:00:00Z' }];
    return route.fulfill({ json: { success: true, data } });
  });
  await page.goto('/');
}

for (const size of [{ width: 320, height: 568 }, { width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 844, height: 390 }]) {
  test(`navegação acessível e conversa usam a tela em ${size.width}×${size.height}`, async ({ page }) => {
    await page.setViewportSize(size);
    await session(page);
    const menu = page.getByRole('button', { name: 'Abrir conversas e servidores' });
    await expect(menu).toBeVisible();
    await expect(page.locator('.sidebar')).toBeHidden();
    const main = await page.getByRole('main', { name: 'Conversa' }).boundingBox();
    expect(main!.x).toBe(0); expect(main!.width).toBe(size.width);
    await menu.tap();
    const navigation = page.getByRole('dialog', { name: 'Conversas e servidores' });
    await expect(navigation).toBeVisible();
    await expect(navigation.getByRole('button', { name: 'Fechar menu', exact: true })).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(navigation.getByRole('button', { name: 'Configurações', exact: true })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(navigation.getByRole('button', { name: 'Fechar menu', exact: true })).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(navigation).toHaveCount(0);
    await expect(menu).toBeFocused();
    await menu.click();
    await navigation.getByRole('button', { name: 'Marina', exact: true }).click();
    await expect(page.locator('.sidebar')).toBeHidden();
    await expect(page.getByRole('main', { name: 'Conversa' })).toBeFocused();
    const message = page.getByLabel('Mensagem para Marina', { exact: true });
    await message.fill('Mensagem para enviar pelo celular');
    await expect(page.getByRole('button', { name: 'Enviar', exact: true })).toBeVisible();
    const composer = await page.locator('.composer').boundingBox();
    expect(composer!.y + composer!.height).toBeLessThanOrEqual(size.height + 1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (size.width === 390) await page.screenshot({ path: '../logs/mobile-chat.png' });
  });
}

test('compositor permanece acessível quando a área útil diminui com o teclado', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await session(page);
  await page.getByRole('button', { name: 'Abrir conversas e servidores' }).click();
  await page.getByRole('button', { name: 'Marina', exact: true }).click();
  const message = page.getByLabel('Mensagem para Marina', { exact: true }); await message.fill('Rascunho preservado');
  await page.setViewportSize({ width: 390, height: 420 });
  await expect(message).toHaveValue('Rascunho preservado');
  await expect(message).toBeVisible();
  const send = await page.getByRole('button', { name: 'Enviar', exact: true }).boundingBox();
  expect(send!.y + send!.height).toBeLessThanOrEqual(420);
  expect(send!.height).toBeGreaterThanOrEqual(44);
});

test('configurações e perfil permanecem acessíveis dentro do menu móvel', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await session(page);
  const trigger = page.getByRole('button', { name: 'Abrir conversas e servidores' });
  await trigger.tap();
  const gear = page.getByRole('button', { name: 'Configurações', exact: true });
  await gear.tap();
  const settings = page.getByRole('dialog', { name: 'Configurações', exact: true });
  await expect(settings).toBeVisible();
  await expect(settings.getByRole('button', { name: 'Microfone', exact: true })).toBeVisible();
  await settings.getByRole('tab', { name: 'Perfil', exact: true }).tap();
  await expect(settings.getByLabel('Nome de usuário', { exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(settings).toHaveCount(0); await expect(gear).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Conversas e servidores' })).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

async function receiver(page: Page, insecure = false, blockPlayback = false, room = false) {
  await page.addInitScript(({ insecure, blockPlayback }) => {
    Object.defineProperty(navigator.mediaDevices, 'getDisplayMedia', { configurable: true, value: undefined });
    Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, value: false });
    if (insecure) Object.defineProperty(window, 'isSecureContext', { configurable: true, value: false });
    if (blockPlayback) {
      const play = HTMLMediaElement.prototype.play; let rejected = false;
      HTMLMediaElement.prototype.play = function () {
        if (this instanceof HTMLVideoElement && !rejected) { rejected = true; return Promise.reject(new DOMException('Gesture required', 'NotAllowedError')); }
        return play.call(this);
      };
    }
  }, { insecure, blockPlayback });
  await page.route('**/api/**', route => route.fulfill({ json: { success: true, data: [] } }));
  await page.goto('/');
  await page.evaluate(async room => {
    const reactPath = '/node_modules/.vite/deps/react.js', domPath = '/node_modules/.vite/deps/react-dom_client.js', panelPath = '/src/views/CallStage.tsx';
    const reactModule = await import(reactPath), domModule = await import(domPath), { DirectCallPanel, VoiceRoomPanel } = await import(panelPath);
    const React = reactModule.default ?? reactModule, { createRoot } = domModule.default ?? domModule;
    const host = document.createElement('div'); host.style.cssText = 'position:fixed;inset:0;z-index:1000;display:flex;flex-direction:column;background:var(--chat)'; document.body.append(host);
    const canvas = document.createElement('canvas'); canvas.width = 456; canvas.height = 960;
    const context = canvas.getContext('2d')!; context.fillStyle = '#a969cf'; context.fillRect(0, 0, 456, 960);
    const stream = canvas.captureStream(30);
    const calls = { call: { caller: 'self', callee: 'peer', state: 'ACTIVE' }, status: 'Em chamada', remote: stream, remoteSharing: true, remoteCamera: false, preview: null, sharing: false, muted: false, busy: false, screenStarting: false, cameraStarting: false, cameraEnabled: false, feedback: null, toggleMute() {}, share() {}, toggleCamera() {}, end() {}, setPreviewVisible() {} };
    const preferences = { profile: { height: 900, frameRate: 30 }, automatic: false };
    if (room) {
      const rooms = { room: { participants: [{ email: 'self', username: 'Você' }, { email: 'peer', username: 'Marina' }], capacity: 8 }, remote: [{ email: 'peer', stream, sharing: true }], preview: null, sharing: false, muted: false, screenStarting: false, feedback: null, toggleMute() {}, share() {}, leave() {}, setPreviewVisible() {} };
      createRoot(host).render(React.createElement(VoiceRoomPanel, { rooms, preferences, email: 'self', name: 'Você' }));
    } else createRoot(host).render(React.createElement(DirectCallPanel, { calls, contacts: [{ email: 'peer', username: 'Marina' }], preferences, email: 'self', name: 'Você' }));
  }, room);
}

test('recebe vídeo sem suporte ao envio de tela e oferece ampliação sem fullscreen nativo', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await receiver(page);
  const panel = page.getByRole('region', { name: 'Chamada', exact: true });
  await expect(panel.getByRole('button', { name: 'Compartilhar tela', exact: true })).toBeDisabled();
  await expect(panel.getByRole('button', { name: 'Ligar câmera', exact: true })).toBeEnabled();
  await expect(panel).toContainText('assistir às transmissões recebidas');
  await expect.poll(() => panel.locator('video').evaluate((video: HTMLVideoElement) => video.videoHeight)).toBe(960);
  await expect(panel.locator('video')).toHaveAttribute('playsinline', '');
  await panel.getByRole('button', { name: 'Assistir à transmissão de Marina', exact: true }).click();
  await panel.getByRole('button', { name: 'Tela cheia da transmissão de Marina', exact: true }).click();
  const theater = panel.locator('.theater-mode'); await expect(theater).toBeVisible();
  const bounds = await theater.boundingBox(); expect(bounds!.width).toBe(390); expect(bounds!.height).toBe(844);
  const exit = theater.getByRole('button', { name: 'Sair da tela cheia', exact: true });
  await expect(exit).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(theater.getByRole('button', { name: 'Preencher tela (corta bordas)', exact: true })).toBeFocused();
  await page.keyboard.press('Tab'); await expect(exit).toBeFocused();
  await page.screenshot({ path: '../logs/mobile-stream-expanded.png' });
  await page.getByRole('button', { name: 'Preencher tela (corta bordas)', exact: true }).click();
  await expect(theater.locator('video')).toHaveCSS('object-fit', 'cover');
  await page.keyboard.press('Escape'); await expect(theater).toHaveCount(0);
  await expect(panel.locator('video')).toHaveJSProperty('paused', false);
});

test('toque libera reprodução quando o navegador exige gesto para assistir', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await receiver(page, false, true);
  const button = page.getByRole('button', { name: 'Assistir à transmissão', exact: true });
  await expect(button).toBeVisible(); await button.click(); await expect(button).toHaveCount(0);
  await expect(page.locator('.call-video')).toHaveJSProperty('paused', false);
});

test('acesso inseguro explica HTTPS sem bloquear a recepção de vídeo', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await receiver(page, true);
  await expect(page.getByRole('button', { name: 'Compartilhar tela', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Ligar câmera', exact: true })).toBeDisabled();
  await expect(page.locator('.call-capability-notice')).toContainText('HTTPS');
  await expect.poll(() => page.locator('.call-video').evaluate((video: HTMLVideoElement) => video.videoHeight)).toBe(960);
});

test('canal de chamada recebe e amplia transmissão em paisagem sem pedir captura local', async ({ page }) => {
  await page.setViewportSize({ width: 844, height: 390 }); await receiver(page, false, false, true);
  const panel = page.getByRole('region', { name: 'Canal de chamada', exact: true });
  await expect(panel.getByRole('button', { name: 'Compartilhar tela no canal', exact: true })).toBeDisabled();
  await expect.poll(() => panel.locator('video').evaluate((video: HTMLVideoElement) => video.videoHeight)).toBe(960);
  await panel.getByRole('button', { name: 'Tela cheia da transmissão de Marina', exact: true }).tap();
  const bounds = await panel.locator('.theater-mode').boundingBox();
  expect(bounds!.width).toBe(844); expect(bounds!.height).toBe(390);
  await panel.getByRole('button', { name: 'Sair da tela cheia', exact: true }).tap();
  await expect(panel.locator('.theater-mode')).toHaveCount(0);
});
