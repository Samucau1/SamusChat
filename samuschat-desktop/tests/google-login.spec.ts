import { test, expect, type Page } from '@playwright/test';

const sdk = `window.google = { accounts: { id: {
 initialize(options) { window.googleCallback = options.callback; },
 renderButton(host) { const button = document.createElement('button'); button.textContent = 'Entrar com Google';
 button.onclick = () => window.googleCallback({credential:'test-google-id-token'}); host.append(button); }
} } };`;
async function setup(page: Page, clientId = 'test.apps.googleusercontent.com') {
  await page.route('**/api/**', route => route.fulfill({ json: { success: true, data: [] } }));
  await page.route('**/api/auth/google/config', route => route.fulfill({ json: { success: true, data: { clientId } } }));
  await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({ contentType: 'text/javascript', body: sdk }));
}
test('Google fica oculto sem configuração; login por senha continua disponível', async ({ page }) => {
  await setup(page, ''); await page.goto('/');
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Entrar com Google', exact: true })).toHaveCount(0);
});
test('Google inicia sessão com CSRF e preserva a renovação sem guardar o token Google', async ({ page, context }) => {
  await setup(page);
  let submitted: any;
  await page.route('**/api/auth/google/gis', async route => {
    submitted = route.request().postDataJSON();
    const cookies = await context.cookies(route.request().url());
    expect(cookies.find(cookie => cookie.name === 'g_csrf_token')?.value).toBe(submitted.g_csrf_token);
    await route.fulfill({ json: { success: true, data: { token: 'access', refreshToken: 'refresh' } } });
  });
  await page.route('**/api/users/me', route => route.fulfill({ json: { success: true, data: { id: 42, username: 'GoogleUser', email: 'user@example.test' } } }));
  await page.goto('/');
  await page.getByLabel('Senha', { exact: true }).fill('existing-password');
  await page.getByRole('button', { name: 'Entrar com Google', exact: true }).click();
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('samuschat.desktop.session') || '{}').refreshToken)).toBe('refresh');
  expect(submitted.credential).toBe('test-google-id-token');
  expect(submitted.password).toBe('existing-password');
  expect((await context.cookies()).find(cookie => cookie.name === 'g_csrf_token')).toBeUndefined();
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('test-google-id-token');
});
test('token rejeitado mostra erro e mantém formulário de login', async ({ page, context }) => {
  await setup(page);
  await page.route('**/api/auth/google/gis', route => route.fulfill({ status: 401, json: { success: false, error: 'Token Google invalido ou expirado' } }));
  await page.goto('/'); await page.getByRole('button', { name: 'Entrar com Google', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Token Google invalido');
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeEnabled();
  expect((await context.cookies()).find(cookie => cookie.name === 'g_csrf_token')).toBeUndefined();
});
test('falha ao carregar Google permite continuar com email e senha', async ({ page }) => {
  await setup(page);
  await page.route('https://accounts.google.com/gsi/client', route => route.abort());
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Não foi possível carregar o Google');
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeEnabled();
});
