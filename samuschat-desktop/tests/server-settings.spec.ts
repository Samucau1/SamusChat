import { test, expect } from '@playwright/test';

async function setup(page: import('@playwright/test').Page, admin: boolean) {
  const user = { id: 1, username: 'Preview', email: 'preview@example.test', publicId: 'usr_preview' };
  await page.addInitScript(user => localStorage.setItem('samuschat.desktop.session', JSON.stringify({ token: 'preview-only', refreshToken: 'preview-only', user })), user);
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path === '/api/users/me') data = user;
    if (path === '/api/servers') data = [{ id: 123, name: 'Servidor Preview', ownerEmail: 'usr_other', myRole: admin ? 'ADMIN' : 'USER', inviteCode: admin ? 'A#%' : undefined, channels: [{ id: 44, name: 'geral', type: 'TEXT' }] }];
    return route.fulfill({ json: { success: true, data } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Servidor Preview', exact: true }).click();
}

test('administrador vê código e ações apenas ao abrir engrenagem', async ({ page }) => {
  await setup(page, true);
  await expect(page.getByRole('button', { name: 'Criar canal', exact: true })).toHaveCount(0);
  await expect(page.getByText('A#%', { exact: true })).toHaveCount(0);
  const gear = page.getByRole('button', { name: 'Configurações do servidor', exact: true });
  await gear.click();
  const panel = page.getByRole('region', { name: 'Ações do servidor' });
  await expect(panel).toContainText('A#%');
  for (const name of ['Criar canal', 'Membros do servidor', 'Personalizar servidor', 'Atualizar lista', 'Sair do servidor']) await expect(panel.getByRole('button', { name, exact: true })).toBeVisible();
  await page.screenshot({ path: '../logs/ui-server-settings.png' });
  await page.keyboard.press('Escape');
  await expect(panel).toHaveCount(0);
  await expect(gear).toBeFocused();
  await gear.click();
  await panel.getByRole('button', { name: 'Criar canal', exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(page.getByRole('dialog')).toContainText('Criar canal');
});

test('membro não vê código nem ações administrativas', async ({ page }) => {
  await setup(page, false);
  await page.getByRole('button', { name: 'Configurações do servidor', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Ações do servidor' });
  await expect(panel).not.toContainText('Código do servidor');
  await expect(panel.getByRole('button', { name: 'Criar canal', exact: true })).toHaveCount(0);
  await expect(panel.getByRole('button', { name: 'Personalizar servidor', exact: true })).toHaveCount(0);
  await expect(panel.getByRole('button', { name: 'Membros do servidor', exact: true })).toBeVisible();
  await expect(panel.getByRole('button', { name: 'Sair do servidor', exact: true })).toBeVisible();
});

test('convite envia três caracteres especiais sem conversão numérica', async ({ page }) => {
  await setup(page, false);
  let received: unknown;
  await page.route('**/api/servers/join', route => {
    received = route.request().postDataJSON();
    return route.fulfill({ json: { success: true, data: 'Entrou no servidor' } });
  });
  await page.route('**/api/servers', route => route.fulfill({ json: { success: true, data: [
    { id: 123, name: 'Servidor Preview', myRole: 'USER', channels: [] },
    { id: 987, name: 'Servidor Convidado', myRole: 'USER', channels: [{ id: 88, name: 'geral', type: 'TEXT' }] },
  ] } }));
  await page.getByRole('button', { name: 'Entrar em servidor', exact: true }).click();
  const input = page.getByLabel('Código do servidor', { exact: true });
  await expect(input).toHaveAttribute('maxlength', '3');
  await input.fill('A#%');
  await page.getByRole('dialog').getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(received).toEqual({ code: 'A#%' });
  await expect(page.locator('.sidebar-header')).toContainText('Servidor Convidado');
});
