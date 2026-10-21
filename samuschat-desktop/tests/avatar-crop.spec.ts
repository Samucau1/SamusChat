import { test, expect } from '@playwright/test';
import { cropRect } from '../src/models/avatarCrop';

test('recorte quadrado preserva proporção e limites em retrato, paisagem e zoom', () => {
  for (const [width, height] of [[800, 400], [400, 800], [512, 512]]) {
    for (const zoom of [1, 2, 4]) for (const x of [0, 0.5, 1]) for (const y of [0, 0.5, 1]) {
      const rect = cropRect(width, height, { zoom, x, y });
      expect(rect.side).toBe(Math.min(width, height) / zoom);
      expect(rect.x).toBeGreaterThanOrEqual(0); expect(rect.y).toBeGreaterThanOrEqual(0);
      expect(rect.x + rect.side).toBeLessThanOrEqual(width); expect(rect.y + rect.side).toBeLessThanOrEqual(height);
    }
  }
  expect(cropRect(800, 400, { zoom: 2, x: 1, y: 0 })).toEqual({ x: 600, y: 0, side: 200 });
  expect(() => cropRect(0, 400, { zoom: 1, x: 0, y: 0 })).toThrow();
});

test('editor cancela sem upload e salva o enquadramento escolhido como imagem quadrada', async ({ page, request }) => {
  const name = `crop_${crypto.randomUUID().slice(0, 8)}`, email = `${name}@example.test`, password = 'CropSynthetic123!';
  expect((await request.post('/api/auth/register', { headers: { 'X-Forwarded-For': `crop-${crypto.randomUUID()}` }, data: { username: name, email, password } })).ok()).toBeTruthy();
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password); await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await page.locator('.profile-button').click();
  const bytes = Buffer.from(await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 800; canvas.height = 400;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#ff0000'; context.fillRect(0, 0, 800, 400);
    context.fillStyle = '#0000ff'; context.fillRect(600, 0, 200, 400);
    return canvas.toDataURL('image/png').split(',')[1];
  }), 'base64');
  let uploads = 0; page.on('request', req => { if (req.method() === 'POST' && req.url().endsWith('/api/users/me/media/avatar')) uploads++; });
  const file = { name: 'paisagem.png', mimeType: 'image/png', buffer: bytes };
  await page.getByLabel('Foto ou GIF do perfil', { exact: true }).setInputFiles(file);
  const editor = page.getByRole('dialog', { name: 'Enquadrar foto de perfil', exact: true });
  await editor.getByRole('button', { name: 'Cancelar recorte', exact: true }).click(); expect(uploads).toBe(0);
  await page.getByLabel('Foto ou GIF do perfil', { exact: true }).setInputFiles(file);
  await editor.getByLabel('Zoom', { exact: true }).fill('2');
  await editor.getByLabel('Posição horizontal', { exact: true }).fill('1');
  await editor.getByRole('button', { name: 'Salvar enquadramento', exact: true }).click();
  await expect(editor).not.toBeVisible(); expect(uploads).toBe(1);
  const avatar = page.locator('.profile-button img');
  await expect.poll(() => avatar.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBe(256);
  const sample = await avatar.evaluate((image: HTMLImageElement) => {
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = 256;
    const context = canvas.getContext('2d')!; context.drawImage(image, 0, 0);
    return { width: image.naturalWidth, height: image.naturalHeight, center: [...context.getImageData(128, 128, 1, 1).data] };
  });
  expect(sample.width).toBe(sample.height); expect(sample.center[2]).toBeGreaterThan(240); expect(sample.center[0]).toBeLessThan(10);
});
