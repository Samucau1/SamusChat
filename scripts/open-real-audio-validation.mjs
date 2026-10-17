// Interactive validation requested by the user. Credentials are supplied only
// through the process environment, never persisted or printed by this script.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../samuschat-desktop/node_modules/playwright');
const root = fileURLToPath(new URL('../', import.meta.url));
const credentials = JSON.parse(process.env.SAMUSCHAT_VALIDATION_CREDENTIALS || '{}');
delete process.env.SAMUSCHAT_VALIDATION_CREDENTIALS;
if (!credentials.pc || !credentials.cell) throw new Error('Provide the two authorized test accounts in the process environment.');
const serial = process.argv[2]; if (!/^[A-Za-z0-9_-]+$/.test(serial || '')) throw new Error('USB serial required');
const adb = path.join(root, '.android-sdk/platform-tools/adb.exe');
const device = (...args) => execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function leaveVoiceSessions(token) {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const result = await fetch('http://127.0.0.1:8080/api/servers', { headers }); const servers = (await result.json()).data;
  for (const server of servers) for (const channel of server.channels.filter(item => item.type === 'VOICE')) {
    const response = await fetch(`http://127.0.0.1:8080/api/channels/${channel.id}/call/leave`, { method: 'POST', headers });
    assert.ok(response.ok, 'Could not leave an existing voice session');
  }
  const active = (await (await fetch('http://127.0.0.1:8080/api/calls', { headers })).json()).data;
  for (const call of active) await fetch(`http://127.0.0.1:8080/api/calls/${call.id}`, { method: 'POST', headers, body: JSON.stringify({ action: 'end' }) });
}
function ui() {
  device('shell', 'uiautomator', 'dump', '/sdcard/samus-real-audio-ui.xml');
  return [...device('shell', 'cat', '/sdcard/samus-real-audio-ui.xml').matchAll(/<node\b([^>]+)>/g)].map(match => Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(attr => [attr[1], attr[2]])));
}
function clickNode(node) {
  const bounds = node.bounds.match(/\d+/g).map(Number);
  device('shell', 'input', 'tap', String(Math.round((bounds[0] + bounds[2]) / 2)), String(Math.round((bounds[1] + bounds[3]) / 2)));
}
async function tap(text) {
  const until = Date.now() + 45000;
  while (Date.now() < until) { const node = ui().find(item => item.text === text || item['content-desc'] === text); if (node) { clickNode(node); return; } await delay(500); }
  throw new Error(`Required Android control unavailable: ${text}`);
}
device('shell', 'input', 'keyevent', '224');
device('shell', 'am', 'start', '-n', 'com.samuschat/.MainActivity');
device('reverse', 'tcp:8080', 'tcp:8080');
await delay(2000);
let nodes = ui();
if (nodes.some(node => node.text === 'Entrar')) {
  let field = nodes.find(node => node.class === 'android.widget.EditText' && node.password !== 'true'); assert.ok(field); clickNode(field); device('shell', 'input', 'text', credentials.cell.email);
  field = ui().find(node => node.class === 'android.widget.EditText' && node.password === 'true'); assert.ok(field); clickNode(field); device('shell', 'input', 'text', credentials.cell.password); device('shell', 'input', 'keyevent', '4'); await tap('Entrar');
}
nodes = ui(); if (nodes.some(node => node['content-desc'] === 'Encerrar chamada')) await tap('Encerrar chamada');
await tap('Perfil');
let confirmedCell = false;
for (let attempt = 0; attempt < 5; attempt++) {
  if (ui().some(node => node.text === credentials.cell.email)) { confirmedCell = true; break; }
  device('shell', 'input', 'swipe', '360', '1100', '360', '400', '350'); await delay(500);
}
assert.ok(confirmedCell, 'The phone is not logged into the authorized cellular test account');
await tap('Amigos');
const cellLogin = await fetch('http://127.0.0.1:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(credentials.cell) });
const cellToken = (await cellLogin.json()).data.token; await leaveVoiceSessions(cellToken);
device('shell', 'am', 'force-stop', 'com.samuschat'); device('shell', 'am', 'start', '-n', 'com.samuschat/.MainActivity');
delete credentials.cell;
console.log('Authorized cellular test account confirmed.');
const browser = await chromium.launch({ channel: 'msedge', headless: false, ignoreDefaultArgs: ['--mute-audio'], args: ['--remote-debugging-address=127.0.0.1', '--remote-debugging-port=19222', '--auto-select-tab-capture-source-by-title=SamusChat YouTube validation', '--autoplay-policy=no-user-gesture-required', '--start-maximized'] });
const context = await browser.newContext({ permissions: ['microphone'], viewport: null });
await context.addInitScript(() => {
  const Native = window.RTCPeerConnection; window.validationPeers = [];
  window.RTCPeerConnection = new Proxy(Native, { construct(Target, args) { const peer = new Target(...args); window.validationPeers.push(peer); return peer; } });
});
const page = await context.newPage();
await page.goto('http://127.0.0.1:5173/');
await page.getByLabel('Email', { exact: true }).fill(credentials.pc.email); await page.getByLabel('Senha', { exact: true }).fill(credentials.pc.password);
const loginResponse = page.waitForResponse(response => response.url().endsWith('/api/auth/login') && response.request().method() === 'POST');
await page.getByRole('button', { name: 'Entrar', exact: true }).click();
const token = (await (await loginResponse).json()).data.token; delete credentials.pc;
await leaveVoiceSessions(token);
const contacts = (await (await context.request.get('http://127.0.0.1:8080/api/calls/contacts', { headers: { Authorization: `Bearer ${token}` } })).json()).data;
assert.equal(contacts.length, 1, 'Expected only the other authorized test account');
const existing = page.getByRole('button', { name: 'Encerrar chamada', exact: true }); if (await existing.count()) await existing.click();
const source = await context.newPage();
await source.goto('https://www.youtube.com/watch?v=aqz-KE-bpKQ', { waitUntil: 'domcontentloaded', timeout: 60000 });
for (const label of ['Reject all', 'Rejeitar tudo', 'Accept all', 'Aceitar tudo']) { const button = source.getByRole('button', { name: label, exact: true }); if (await button.count()) await button.first().click(); }
await source.locator('video').first().waitFor({ timeout: 30000 });
await source.evaluate(async () => { const video = document.querySelector('video'); video.muted = false; video.volume = 0.5; await video.play(); });
await source.waitForFunction(() => { const video = document.querySelector('video'); return video && video.currentTime > 2 && !video.paused; }, { timeout: 30000 });
await source.evaluate(() => { document.title = 'SamusChat YouTube validation'; });
await page.bringToFront();
await page.getByRole('button', { name: contacts[0].username, exact: true }).click();
await page.getByRole('button', { name: 'Ligar', exact: true }).click(); await tap('Aceitar');
try { await page.locator('.call-panel strong').filter({ hasText: 'Em chamada' }).waitFor({ timeout: 30000 }); }
catch {
  const diagnostics = await page.evaluate(() => ({
    status: document.querySelector('.call-stage-header strong')?.textContent,
    errors: [...document.querySelectorAll('.error-notice')].map(item => item.textContent),
    peers: window.validationPeers.map(peer => ({
      state: peer.connectionState, ice: peer.iceConnectionState,
      localMdns: peer.localDescription?.sdp.includes('.local'), remoteMdns: peer.remoteDescription?.sdp.includes('.local'),
      localCandidates: peer.localDescription?.sdp.match(/a=candidate:/g)?.length,
      remoteCandidates: peer.remoteDescription?.sdp.match(/a=candidate:/g)?.length
    }))
  }));
  console.log(JSON.stringify(diagnostics));
  console.log('REAL_TEST_CONNECTION_PENDING: visible browser retained for diagnosis.');
  await new Promise(resolve => { process.on('SIGINT', resolve); process.on('SIGTERM', resolve); browser.on('disconnected', resolve); }); await browser.close(); process.exit(1);
}
await page.getByRole('button', { name: 'Compartilhar tela', exact: true }).click();
await page.getByRole('button', { name: 'Parar transmissão', exact: true }).waitFor({ timeout: 30000 });
console.log('REAL_TEST_ACTIVE: YouTube tab is streaming from the PC test account to the cellular test account.');
console.log('Waiting for the user to confirm whether sound is audible on the phone.');
// Keep the visible session available for listening and manual validation.
await new Promise(resolve => { process.on('SIGINT', resolve); process.on('SIGTERM', resolve); browser.on('disconnected', resolve); });
await browser.close();
