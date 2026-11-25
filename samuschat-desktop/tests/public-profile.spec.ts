import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  const user = { id: 1, publicId: 'usr_preview', username: 'Preview', email: 'private@example.test', bio: 'Meu perfil.' };
  await page.addInitScript(user => localStorage.setItem('samuschat.desktop.session', JSON.stringify({ token: 'preview-only', refreshToken: 'preview-only', user })), user);
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path === '/api/users/me') data = user;
    if (path === '/api/calls/contacts') data = [{ email: 'usr_marina', username: 'Marina' }];
    if (path === '/api/direct-messages') data = [{ id: 1, senderEmail: 'usr_marina', senderUsername: 'Marina', content: 'Olá!', createdAt: '2026-11-25T12:00:00Z' }];
    if (path === '/api/friends/profiles/usr_marina') data = { publicId: 'usr_marina', username: 'Marina', bio: 'Sobre Marina.', relationship: 'FRIEND' };
    return route.fulfill({ json: { success: true, data } });
  });
  await page.goto('/');
});

test('nome e foto da conta abrem visualização, enquanto engrenagem mantém edição', async ({ page }) => {
  const opener = page.getByRole('button', { name: 'Ver perfil de Preview', exact: true });
  for (const target of [opener.locator('strong'), opener.locator('.avatar')]) {
    await target.click();
    const dialog = page.getByRole('dialog', { name: 'Perfil do usuário' });
    await expect(dialog).toContainText('Meu perfil.');
    await expect(dialog).toContainText('usr_preview');
    await expect(dialog).not.toContainText('private@example.test');
    await expect(dialog.getByRole('textbox')).toHaveCount(0);
    await dialog.press('Escape');
    await expect(opener).toBeFocused();
  }
  await page.getByRole('button', { name: 'Configurações', exact: true }).click();
  await page.getByRole('tab', { name: 'Perfil', exact: true }).click();
  await expect(page.getByLabel('Nome de usuário', { exact: true })).toBeVisible();
});

test('cabeçalho, nome e foto nas mensagens abrem o perfil público em todos os temas', async ({ page }) => {
  await page.getByRole('button', { name: 'Marina', exact: true }).click();
  for (const [index, theme] of ['purple', 'black-green', 'blue', 'black-orange'].entries()) {
    await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
    const opener = page.locator(index % 3 === 0 ? '.chat-profile-link' : index % 3 === 1 ? '.message-profile-name' : '.message-profile-avatar');
    await opener.click();
    const dialog = page.getByRole('dialog', { name: 'Perfil do usuário' });
    await expect(dialog).toContainText('Sobre Marina.');
    await expect(dialog).toContainText('usr_marina');
    await expect(dialog.getByRole('textbox')).toHaveCount(0);
    await page.setViewportSize({ width: 390, height: 844 });
    const bounds = await dialog.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390);
    if (index === 0) await page.screenshot({ path: '../logs/ui-public-profile.png' });
    await dialog.getByRole('button', { name: 'Fechar', exact: true }).click();
    await expect(opener).toBeFocused();
    await page.setViewportSize({ width: 1440, height: 900 });
  }
});

test('falha ao buscar outro perfil mantém diálogo acessível e permite fechar', async ({ page }) => {
  await page.route('**/api/friends/profiles/*', route => route.fulfill({ status: 404, json: { success: false, message: 'Perfil indisponível.' } }));
  await page.getByRole('button', { name: 'Marina', exact: true }).click();
  await page.locator('.chat-profile-link').click();
  const dialog = page.getByRole('dialog', { name: 'Perfil do usuário' });
  await expect(dialog.getByRole('alert')).toContainText('Não foi possível carregar');
  await dialog.getByRole('button', { name: 'Fechar perfil', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('.chat-profile-link')).toBeFocused();
});
