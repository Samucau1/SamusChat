import { test, expect } from '@playwright/test';

test('rascunhos isolados, anexos e envio concluído após trocar de conversa', async ({ page }) => {
  let finishSend!: () => void;
  const pending = new Promise<void>(resolve => { finishSend = resolve; });
  let sending!: () => void;
  const started = new Promise<void>(resolve => { sending = resolve; });
  await page.addInitScript(() => sessionStorage.setItem('samuschat.desktop.session', 'test-token'));
  await page.routeWebSocket('**/ws/websocket', () => {});
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data: unknown = [];
    if (url.pathname === '/api/users/me') data = { id: 1, username: 'Teste', email: 'teste@example.com' };
    if (url.pathname === '/api/servers') data = [{ id: 1, name: 'Projeto', channels: [{ id: 11, name: 'geral', type: 'TEXT' }, { id: 12, name: 'ideias', type: 'TEXT' }] }];
    if (url.pathname === '/api/calls/contacts') data = [{ email: 'amigo@example.com', username: 'Amigo' }];
    if (route.request().method() === 'POST') {
      sending(); await pending;
      data = { id: 1, content: 'Mensagem original', senderUsername: 'Teste', senderEmail: 'teste@example.com', createdAt: new Date().toISOString() };
    }
    await route.fulfill({ json: { success: true, data } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Projeto', exact: true }).click();
  const input = page.getByLabel(/^Mensagem para/);
  await input.fill('Mensagem original');
  await expect(page.locator('.conversation-link').filter({ hasText: 'geral' }).locator('.draft-badge')).toHaveText('Rascunho');
  await page.locator('input[type=file]').setInputFiles({ name: 'nota.txt', mimeType: 'text/plain', buffer: Buffer.from('nota') });
  await page.getByRole('button', { name: '# ideias', exact: true }).click();
  await expect(input).toHaveValue('');
  await expect(page.locator('.file-selected')).toHaveCount(0);
  await input.fill('Outra ideia');
  await page.getByRole('button', { name: 'Amigos', exact: true }).click();
  await page.getByRole('button', { name: '@ Amigo', exact: true }).click();
  await input.fill('Rascunho privado');
  await page.getByRole('button', { name: 'Projeto', exact: true }).click();
  await expect(input).toHaveValue('Mensagem original');
  await expect(page.locator('.file-selected')).toContainText('nota.txt');
  await page.getByRole('button', { name: 'Enviar ↗' }).click();
  await started;
  await page.getByRole('button', { name: '# ideias', exact: true }).click();
  await expect(input).toHaveValue('Outra ideia');
  finishSend();
  await expect(page.getByRole('button', { name: 'Enviar ↗' })).toBeEnabled();
  await expect(input).toHaveValue('Outra ideia');
  await page.getByRole('button', { name: '# geral', exact: true }).click();
  await expect(page.locator('.conversation-link').filter({ hasText: 'geral' }).locator('.draft-badge')).toHaveCount(0);
  await expect(input).toHaveValue('');
  await expect(page.locator('.file-selected')).toHaveCount(0);
  await page.getByRole('button', { name: 'Amigos', exact: true }).click();
  await page.getByRole('button', { name: '@ Amigo', exact: true }).click();
  await expect(input).toHaveValue('Rascunho privado');
  await page.route('**/api/direct-messages?*', async route => {
    if (route.request().method() === 'POST') await route.fulfill({ status: 503, json: { success: false, error: 'Falha simulada' } });
    else await route.fulfill({ json: { success: true, data: [] } });
  });
  await page.getByRole('button', { name: 'Enviar ↗' }).click();
  await expect(page.getByRole('alert')).toContainText('Falha simulada');
  await page.getByRole('button', { name: 'Projeto', exact: true }).click();
  await page.getByRole('button', { name: 'Amigos', exact: true }).click();
  await page.getByRole('button', { name: '@ Amigo', exact: true }).click();
  await expect(input).toHaveValue('Rascunho privado');
  await page.reload();
  await page.getByRole('button', { name: '@ Amigo', exact: true }).click();
  await expect(input).toHaveValue('');
});
