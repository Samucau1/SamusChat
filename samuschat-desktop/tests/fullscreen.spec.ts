import { test, expect, type Page } from '@playwright/test';

async function viewer(page: Page) {
  await page.goto('/');
  await page.evaluate(async () => {
    const reactPath = '/node_modules/.vite/deps/react.js';
    const domPath = '/node_modules/.vite/deps/react-dom_client.js';
    const panelPath = '/src/views/CallStage.tsx';
    const reactModule = await import(reactPath), domModule = await import(domPath), { DirectCallPanel } = await import(panelPath);
    const React = reactModule.default ?? reactModule, { createRoot } = domModule.default ?? domModule;
    const container = document.createElement('div');
    container.style.cssText = 'position:fixed;inset:0;background:#111214;z-index:10000'; document.body.append(container);
    const canvas = document.createElement('canvas'); canvas.width = 456; canvas.height = 960;
    const context = canvas.getContext('2d')!;
    const draw = () => { context.fillStyle = '#2468bc'; context.fillRect(0, 0, canvas.width, canvas.height); context.fillStyle = '#ffee22'; context.fillRect(10 + Date.now() % 100, 10, 80, 80); };
    draw(); const timer = setInterval(draw, 33), stream = canvas.captureStream(30), root = createRoot(container);
    const calls = { call: { caller: 'pc@example.test', callee: 'android@example.test', state: 'ACTIVE' }, status: 'Em chamada', remoteSharing: true, remote: stream,
      preview: null, sharing: false, muted: false, busy: false, screenStarting: false, feedback: null, toggleMute() {}, share() {}, end() {}, setPreviewVisible() {} };
    const props = { calls, contacts: [{ email: 'android@example.test', username: 'Android' }], preferences: { profile: { width: 1600, height: 900, frameRate: 30 }, automatic: false }, email: 'pc@example.test', name: 'PC' };
    const render = () => root.render(React.createElement(DirectCallPanel, props)); render();
    (window as any).viewer = {
      size: (width: number, height: number) => { canvas.width = width; canvas.height = height; draw(); },
      stopSharing: () => { calls.remoteSharing = false; render(); },
      close: () => { root.unmount(); clearInterval(timer); stream.getTracks().forEach(track => track.stop()); container.remove(); }
    };
  });
  await expect.poll(() => page.locator('.call-video').evaluate((video: HTMLVideoElement) => video.videoHeight)).toBe(960);
}

test('tela cheia nativa acompanha retrato → paisagem → retrato e oferece preencher sem distorção', async ({ page }) => {
  await viewer(page);
  try {
    await page.locator('.call-video').click();
    await page.getByRole('button', { name: 'Tela cheia da transmissão de Android', exact: true }).click();
    await expect.poll(() => page.evaluate(() => (document.fullscreenElement as HTMLElement)?.dataset.participantId)).toBe('remote');
    await page.evaluate(() => (window as any).viewer.size(960, 456));
    await expect.poll(() => page.locator('.call-video').evaluate((video: HTMLVideoElement) => [video.videoWidth, video.videoHeight])).toEqual([960, 456]);
    const dimensions = await page.locator('.call-tile[data-participant-id=remote]').evaluate(element => {
      const rect = element.getBoundingClientRect(); return { width: rect.width, height: rect.height, viewportWidth: innerWidth, viewportHeight: innerHeight };
    });
    expect(dimensions.width).toBe(dimensions.viewportWidth); expect(dimensions.height).toBe(dimensions.viewportHeight);
    await expect(page.locator('.call-video')).toHaveCSS('object-fit', 'contain');
    await page.screenshot({ path: '../docs/images/validacao-2026-10-14/desktop-paisagem-tela-cheia.png' });
    await page.getByRole('button', { name: 'Preencher tela (corta bordas)', exact: true }).click();
    await expect(page.locator('.call-video')).toHaveCSS('object-fit', 'cover');
    await page.screenshot({ path: '../docs/images/validacao-2026-10-14/desktop-preencher-tela.png' });
    await page.getByRole('button', { name: 'Mostrar imagem inteira', exact: true }).click();
    await page.evaluate(() => (window as any).viewer.size(456, 960));
    await expect.poll(() => page.locator('.call-video').evaluate((video: HTMLVideoElement) => [video.videoWidth, video.videoHeight])).toEqual([456, 960]);
    await page.screenshot({ path: '../docs/images/validacao-2026-10-14/desktop-retrato-tela-cheia.png' });
    if (!await page.getByRole('button', { name: 'Sair da tela cheia', exact: true }).count()) await page.locator('.call-video').click();
    await page.getByRole('button', { name: 'Sair da tela cheia', exact: true }).click();
    await expect.poll(() => page.evaluate(() => document.fullscreenElement === null)).toBe(true);
    await expect(page.locator('.call-panel strong')).toHaveText('Em chamada');
    expect(await page.locator('.call-video').evaluate((video: HTMLVideoElement) => (video.srcObject as MediaStream).getVideoTracks()[0].readyState)).toBe('live');
  } finally { await page.evaluate(() => (window as any).viewer.close()); }
});

test('fim da transmissão fecha tela cheia e preserva painel da ligação', async ({ page }) => {
  await viewer(page);
  try {
    await page.locator('.call-video').click();
    await page.getByRole('button', { name: 'Tela cheia da transmissão de Android', exact: true }).click();
    await expect.poll(() => page.evaluate(() => Boolean(document.fullscreenElement))).toBe(true);
    await page.evaluate(() => (window as any).viewer.stopSharing());
    await expect.poll(() => page.evaluate(() => document.fullscreenElement === null)).toBe(true);
    await expect(page.locator('.call-panel strong')).toHaveText('Em chamada');
  } finally { await page.evaluate(() => (window as any).viewer.close()); }
});
