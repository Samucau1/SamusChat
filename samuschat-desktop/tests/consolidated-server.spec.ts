import { test, expect, type Page } from '@playwright/test';

test('duas contas usam o código real do servidor e compartilham mensagens e anexos', async ({ page, browser, request }) => {
  expect(['localhost', '127.0.0.1']).toContain(new URL(process.env.SAMUSCHAT_TEST_API_URL ?? 'http://127.0.0.1:18082').hostname);
  const password = 'ConsolidationOnly42!';
  const stamp = crypto.randomUUID().slice(0, 8);
  const owner = `owner_${stamp}@example.test`, member = `member_${stamp}@example.test`;
  for (const [username, email] of [[`owner_${stamp}`, owner], [`member_${stamp}`, member]]) {
    const response = await request.post('/api/auth/register', { data: { username, email, password } });
    expect(response.ok(), await response.text()).toBe(true);
  }
  async function login(target: Page, email: string) {
    await target.goto('/');
    await target.getByLabel('Email', { exact: true }).fill(email);
    await target.getByLabel('Senha', { exact: true }).fill(password);
    await target.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(target.getByRole('heading', { name: 'Seus amigos', exact: true })).toBeVisible();
  }
  await login(page, owner);
  await page.getByRole('button', { name: 'Criar servidor', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Nome', { exact: true }).fill('Servidor consolidado');
  await page.getByRole('button', { name: 'Criar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Configurações do servidor', exact: true }).click();
  const code = await page.locator('.server-invite code').innerText();
  expect(code).toMatch(/^[A-Za-z0-9!@#$%*+_-]{3}$/);
  await page.keyboard.press('Escape');
  const context = await browser.newContext();
  try {
    const other = await context.newPage();
    await login(other, member);
    await other.getByRole('button', { name: 'Entrar em servidor', exact: true }).click();
    await other.getByLabel('Código do servidor', { exact: true }).fill(code);
    await other.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(other.getByRole('dialog')).toHaveCount(0);
    await other.getByRole('button', { name: 'Configurações do servidor', exact: true }).click();
    await expect(other.locator('.server-invite')).toHaveCount(0);
    await expect(other.getByRole('button', { name: 'Criar canal', exact: true })).toHaveCount(0);
    await other.keyboard.press('Escape');
    await other.locator('.conversation-link').first().click();
    const text = `Mensagem consolidada ${stamp}`;
    await page.getByLabel(/^Mensagem para/).fill(text);
    await page.getByRole('button', { name: 'Enviar', exact: true }).click();
    await expect(other.locator('.message p').filter({ hasText: text })).toBeVisible();
    await page.locator('.composer input[type=file]').setInputFiles({ name: 'consolidated.txt', mimeType: 'text/plain', buffer: Buffer.from('Anexo consolidado') });
    await page.getByRole('button', { name: 'Enviar', exact: true }).click();
    await expect(other.getByRole('link', { name: 'Abrir anexo ↗' })).toBeVisible();
  } finally { await context.close(); }
});
