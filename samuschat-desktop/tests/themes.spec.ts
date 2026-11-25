import { test, expect } from '@playwright/test';

test.beforeEach(async ({ context }) => {
  await context.route('**/api/**', route => route.fulfill({
    json: { success: true, data: route.request().url().includes('/auth/google/config') ? { clientId: '' } : [] },
  }));
});

test('tema verde aplica preto absoluto, persiste após recarga e Escape devolve foco', async ({ page }) => {
  await page.goto('/');
  const settings = page.getByRole('button', { name: 'Configurações', exact: true });
  await settings.click();
  await page.getByRole('radio', { name: /^Preto absoluto e verde/ }).check();
  await expect(page.locator('html')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
  await page.getByRole('dialog').press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(settings).toBeFocused();
  await page.reload();
  await expect(page.locator('html')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
  await settings.click();
  await expect(page.getByRole('radio', { name: /^Preto absoluto e verde/ })).toBeChecked();
  await page.getByRole('radio', { name: /^Preto e roxo/ }).check();
  await page.getByRole('button', { name: 'Concluir', exact: true }).click();
  await page.reload();
  await expect(page.locator('html')).toHaveCSS('background-color', 'rgb(6, 5, 7)');
});

test('troca de tema sincroniza abas abertas sem reiniciar a sessão', async ({ page, context }) => {
  await page.goto('/');
  const second = await context.newPage();
  await second.goto('/');
  await page.getByRole('button', { name: 'Configurações', exact: true }).click();
  await page.getByRole('radio', { name: /^Preto absoluto e verde/ }).check();
  await expect(second.locator('html')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
  await second.getByRole('button', { name: 'Configurações', exact: true }).click();
  await expect(second.getByRole('radio', { name: /^Preto absoluto e verde/ })).toBeChecked();
});

for (const option of [
  { title: 'Azul clássico', background: 'rgb(32, 36, 46)' },
  { title: 'Preto e laranja', background: 'rgb(0, 0, 0)' },
]) {
  test(`${option.title} permanece selecionado após recarregar em tela estreita`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    await page.getByRole('button', { name: 'Configurações', exact: true }).click();
    await expect(page.getByRole('radio')).toHaveCount(4);
    await page.getByRole('radio', { name: new RegExp('^' + option.title) }).check();
    await page.getByRole('button', { name: 'Concluir', exact: true }).click();
    await page.reload();
    await expect(page.locator('html')).toHaveCSS('background-color', option.background);
    await page.getByRole('button', { name: 'Configurações', exact: true }).click();
    await expect(page.getByRole('radio', { name: new RegExp('^' + option.title) })).toBeChecked();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}

test('tema continua utilizável se o navegador impedir a gravação da preferência', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.setItem = () => { throw new DOMException('Storage blocked', 'SecurityError'); };
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.getByRole('button', { name: 'Configurações', exact: true }).click();
  await page.getByRole('radio', { name: /^Preto absoluto e verde/ }).check();
  await expect(page.locator('html')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
  await expect(page.getByRole('dialog').getByRole('status')).toContainText('não permitiu salvar');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const bounds = await page.getByRole('dialog').boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390);
});

test('engrenagem no rodapé reúne tema e perfil, preserva edição entre abas e salva o nome', async ({ page }) => {
  const profile = { id: 1, username: 'Preview', email: 'preview@example.test', publicId: 'usr_1_preview' };
  await page.addInitScript(user => localStorage.setItem('samuschat.desktop.session', JSON.stringify({ token: 'preview-only', refreshToken: 'preview-only', user })), profile);
  await page.route('**/api/users/me', route => route.fulfill({ json: { success: true, data: profile } }));
  await page.route('**/api/users/me/username?*', route => {
    profile.username = new URL(route.request().url()).searchParams.get('newUsername')!;
    return route.fulfill({ json: { success: true, data: profile } });
  });
  await page.goto('/');
  const gear = page.getByRole('button', { name: 'Configurações', exact: true });
  await expect(gear).toHaveCount(1);
  await expect(page.locator('.profile-bar')).toContainText('Preview');
  await expect(gear).toHaveText('');
  await gear.click();
  await expect(page.getByRole('tab', { name: 'Aparência', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('tab', { name: 'Perfil', exact: true }).click();
  await page.getByLabel('Nome de usuário', { exact: true }).fill('Preview novo');
  await page.getByRole('tab', { name: 'Perfil', exact: true }).press('ArrowLeft');
  await expect(page.getByRole('tab', { name: 'Aparência', exact: true })).toBeFocused();
  await page.getByRole('radio', { name: /^Preto absoluto e verde/ }).check();
  await page.getByRole('tab', { name: 'Perfil', exact: true }).click();
  await expect(page.getByLabel('Nome de usuário', { exact: true })).toHaveValue('Preview novo');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.profile-bar')).toContainText('Preview novo');
  await expect(gear).toBeFocused();
});
