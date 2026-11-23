import { test, expect, type Page } from '@playwright/test';
import { notificationDestination } from '../src/models/notificationDestination';

const recipient = 'usr_1_abcdefghijklmnopqrstuv';
const contact = 'usr_2_abcdefghijklmnopqrstuv';

async function mountChat(page: Page, search: string, delayDirectories = false) {
  await page.goto('/' + search);
  await page.evaluate(async ({ recipientId, contactId, delayDirectories }) => {
    const reactPath = '/node_modules/.vite/deps/react.js', domPath = '/node_modules/.vite/deps/react-dom_client.js';
    const controllerPath = '/src/controllers/useChatController.ts';
    const react = await import(reactPath), dom = await import(domPath), { useChatController } = await import(controllerPath);
    const React = react.default ?? react, { createRoot } = dom.default ?? dom;
    const probe = (window as any).notificationProbe = { errors: [] };
    const directoriesReady = delayDirectories ? new Promise<void>(resolve => { probe.releaseDirectories = resolve; }) : Promise.resolve();
    const api = {
      token: 'test-token',
      servers: async () => { await directoriesReady; return [{ id: 10, name: 'Equipe', channels: [{ id: 42, name: 'Geral', type: 'TEXT' }] }]; },
      contacts: async () => { await directoriesReady; return [{ email: contactId, username: 'Contato' }]; },
      unreadDirect: async () => [], history: async () => [], readDirect: async () => 0,
    };
    const user = { id: 1, username: 'Destinatário', email: 'owner@example.test', publicId: recipientId };
    const container = document.createElement('div'); document.body.append(container);
    function Client() {
      probe.current = useChatController(api, user, () => {}, (error: Error) => probe.errors.push(error.message));
      return React.createElement('p', null, probe.current.conversation?.name ?? 'Sem conversa');
    }
    const root = createRoot(container); root.render(React.createElement(Client));
    probe.close = () => { root.unmount(); container.remove(); };
  }, { recipientId: recipient, contactId: contact, delayDirectories });
}

test.afterEach(async ({ page }) => {
  await page.evaluate(() => (window as any).notificationProbe?.close());
});

test('payloads inválidos e identificadores privados são ignorados', () => {
  expect(notificationDestination(`?notification=direct&recipient=${recipient}&contact=${contact}`)).toEqual({ recipientId: recipient, contactId: contact });
  expect(notificationDestination(`?notification=channel&recipient=${recipient}&channel=42`)).toEqual({ recipientId: recipient, channelId: 42 });
  expect(notificationDestination('?notification=direct&recipient=private@example.test&contact=other@example.test')).toBeNull();
  expect(notificationDestination(`?notification=channel&recipient=${recipient}&channel=-1`)).toBeNull();
});

test('aviso privado abre a conversa após restaurar o diretório da conta', async ({ page }) => {
  await mountChat(page, `?notification=direct&recipient=${recipient}&contact=${contact}`);
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current?.conversation?.contact)).toBe(contact);
  expect(await page.evaluate(() => location.search)).toBe('');
  expect(await page.evaluate(() => (window as any).notificationProbe.errors)).toEqual([]);
});

test('aviso de canal seleciona o servidor e o canal corretos', async ({ page }) => {
  await mountChat(page, `?notification=channel&recipient=${recipient}&channel=42`);
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current?.conversation?.channelId)).toBe(42);
  expect(await page.evaluate(() => (window as any).notificationProbe.current.serverId)).toBe(10);
});

for (const kind of ['direct', 'channel']) {
  test(`clique ${kind} durante o carregamento aguarda os diretórios`, async ({ page }) => {
    await mountChat(page, '', true);
    await expect.poll(() => page.evaluate(() => Boolean((window as any).notificationProbe.current))).toBe(true);
    await page.evaluate(({ recipientId, contactId, kind }) => {
      navigator.serviceWorker.dispatchEvent(new MessageEvent('message', { data: {
        type: 'SAMUSCHAT_OPEN_NOTIFICATION',
        url: kind === 'direct' ? `/?notification=direct&recipient=${recipientId}&contact=${contactId}`
          : `/?notification=channel&recipient=${recipientId}&channel=42`,
      } }));
    }, { recipientId: recipient, contactId: contact, kind });
    expect(await page.evaluate(() => (window as any).notificationProbe.errors)).toEqual([]);
    await page.evaluate(() => (window as any).notificationProbe.releaseDirectories());
    await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current.conversation?.id))
      .toBe(kind === 'direct' ? `dm:${contact}` : 'channel:42');
    expect(await page.evaluate(() => location.search)).toBe('');
  });
}

test('canal indisponível não altera a conversa e informa o erro', async ({ page }) => {
  await mountChat(page, `?notification=channel&recipient=${recipient}&channel=999`);
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.errors))
    .toEqual(['A conversa deste aviso não está disponível para esta conta.']);
  expect(await page.evaluate(() => (window as any).notificationProbe.current.conversation)).toBeNull();
});

test('mensagem em primeiro plano para outra conta é ignorada', async ({ page }) => {
  await mountChat(page, '');
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current?.servers.length)).toBe(1);
  await page.evaluate(({ recipientId, contactId }) => {
    navigator.serviceWorker.dispatchEvent(new MessageEvent('message', { data: {
      type: 'SAMUSCHAT_NEW_MESSAGE', url: `/?notification=direct&recipient=${contactId}&contact=${recipientId}`,
    } }));
  }, { recipientId: recipient, contactId: contact });
  expect(await page.evaluate(() => (window as any).notificationProbe.current.pushNotice)).toBeNull();
});

test('aviso destinado a outra conta exige a conta correta', async ({ page }) => {
  await mountChat(page, `?notification=direct&recipient=${contact}&contact=${recipient}`);
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.errors)).toEqual(['Este aviso pertence a outra conta do SamusChat.']);
  expect(await page.evaluate(() => (window as any).notificationProbe.current.conversation)).toBeNull();
});

test('aviso do service worker em primeiro plano oferece a conversa de canal', async ({ page }) => {
  await mountChat(page, '');
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current?.servers.length)).toBe(1);
  await page.evaluate(({ recipientId }) => {
    navigator.serviceWorker.dispatchEvent(new MessageEvent('message', { data: {
      type: 'SAMUSCHAT_NEW_MESSAGE', url: `/?notification=channel&recipient=${recipientId}&channel=42`,
    } }));
  }, { recipientId: recipient });
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current.pushNotice?.channelId)).toBe(42);
  await page.evaluate(() => {
    const controller = (window as any).notificationProbe.current;
    controller.openNotification(controller.pushNotice);
  });
  await expect.poll(() => page.evaluate(() => (window as any).notificationProbe.current.conversation?.channelId)).toBe(42);
});
