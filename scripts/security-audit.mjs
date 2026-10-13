// Controlled assessment: disposable API only; no production targets or real credentials.
import { createHmac, randomUUID } from 'node:crypto';
import { createServer, request as httpRequest } from 'node:http';
import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const api = 'http://127.0.0.1:18085';
const desktop = 'http://127.0.0.1:5175';
if (!process.argv.includes('--disposable-local-api')) throw new Error('Use --disposable-local-api somente com scripts/start-desktop-test-api.ps1 -Port 18085.');
const run = randomUUID().slice(0, 8), rows = [], fixtures = [], createdFiles = new Map();
let sequence = Math.floor(Math.random() * 249) * 250, requests = 0;
const ip = () => `198.18.${Math.floor(++sequence / 250)}.${sequence % 250 + 1}`;
const record = (client, id, secure, evidence) => rows.push({ client, id, result: secure ? 'CONTROL_OBSERVED' : 'FINDING', evidence });
async function call(route, { client = 'PC', token, method = 'GET', body, headers = {}, raw = false } = {}) {
  if (!route.startsWith('/') || route.startsWith('//')) throw new Error('Local path required.');
  if (++requests > 260) throw new Error('Bounded request budget exceeded.');
  const finalHeaders = { 'User-Agent': client === 'Android' ? 'SamusChat-security-test/Android OkHttp-compatible' : 'SamusChat-security-test/PC', 'X-Forwarded-For': ip(), ...headers };
  if (token) finalHeaders.Authorization = `Bearer ${token}`;
  if (body !== undefined && !(body instanceof FormData)) { finalHeaders['Content-Type'] = 'application/json'; body = JSON.stringify(body); }
  const response = await fetch(api + route, { method, body, headers: finalHeaders, signal: AbortSignal.timeout(15000) });
  const text = await response.text();
  return { status: response.status, headers: Object.fromEntries(response.headers), data: raw ? text : JSON.parse(text || 'null') };
}
const denied = result => [400, 401, 403, 404].includes(result.status) && !result.data?.success;
async function requireData(route, options) {
  const result = await call(route, options);
  if (result.status !== 200 || !result.data?.success) throw new Error(`Fixture failed: ${route} HTTP ${result.status}`);
  return result.data.data;
}
async function account(client, label, password = 'SecurityTest123!') {
  const name = `sec_${run}_${label}`.slice(0, 30), email = `${name}@example.test`;
  const result = await requireData('/api/auth/register', { client, method: 'POST', body: { username: name, email, password } });
  return { name, email, password, token: result.token };
}
function multipart(content, filename = 'audit.txt', mime = 'text/plain') {
  const body = new FormData(); body.append('file', new Blob([content], { type: mime }), filename); return body;
}
async function upload(fixture, content, filename, mime) {
  const data = await requireData(`/api/channels/${fixture.channel}/upload`, { client: fixture.client, token: fixture.owner.token, method: 'POST', body: multipart(content, filename, mime) });
  const url = new URL(data.attachmentUrl);
  if (url.origin !== api || !url.pathname.startsWith('/uploads/')) throw new Error('Unexpected upload origin.');
  createdFiles.set(url.pathname, fixture.owner.token);
  return url.pathname;
}
function expiredJwt(email) {
  // Known key belongs exclusively to the disposable test launcher, not production.
  const head = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify({ sub: email, av: 0, exp: Math.floor(Date.now() / 1000) - 60 })).toString('base64url');
  return `${head}.${body}.${createHmac('sha256', 'chave_exclusiva_de_testes_minimo_32_caracteres_aqui').update(`${head}.${body}`).digest('base64url')}`;
}
async function stomp(token, destination, publish) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(api.replace('http:', 'ws:') + '/ws/websocket');
    let settled = false;
    const finish = outcome => { if (!settled) { settled = true; clearTimeout(timer); socket.close(); resolve(outcome); } };
    const timer = setTimeout(() => finish('TIMEOUT'), 4500);
    socket.onopen = () => socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\n${token ? `Authorization:Bearer ${token}\n` : ''}\n\0`);
    socket.onmessage = event => {
      const frame = String(event.data);
      if (frame.startsWith('ERROR')) finish('ERROR');
      else if (frame.startsWith('MESSAGE')) finish('MESSAGE');
      else if (frame.startsWith('CONNECTED')) {
        if (!destination) finish('CONNECTED');
        else {
          socket.send(`SUBSCRIBE\nid:audit\ndestination:${destination}\nack:auto\n\n\0`);
          if (publish) setTimeout(() => { if (!settled) void publish().catch(reject); }, 300);
        }
      }
    };
    socket.onerror = () => finish('ERROR');
    socket.onclose = () => finish('CLOSED');
  });
}

await requireData('/api/users/me', { token: (await account('PC', 'health')).token });
for (const client of ['PC', 'Android']) {
  const owner = await account(client, client.toLowerCase() + '_owner');
  const outsider = await account(client, client.toLowerCase() + '_other');
  const member = await account(client, client.toLowerCase() + '_member');
  const server = await requireData('/api/servers', { client, token: owner.token, method: 'POST', body: { name: `Security ${client} ${run}`, description: 'Disposable audit' } });
  const channel = server.channels[0].id;
  const fixture = { client, owner, outsider, member, server, channel }; fixtures.push(fixture);
  await requireData(`/api/servers/${server.id}/join`, { client, token: member.token, method: 'POST' });
  const message = await requireData(`/api/channels/${channel}/messages`, { client, token: owner.token, method: 'POST', body: { content: 'Private audit message' } });
  for (const route of ['/api/users/me', '/api/servers', `/api/channels/${channel}/messages`, '/api/calls', '/actuator/env', '/actuator/metrics']) {
    const result = await call(route, { client }); record(client, `auth_required:${route}`, result.status === 401, { status: result.status });
  }
  const unsigned = Buffer.from('{"alg":"none"}').toString('base64url') + '.' + Buffer.from(JSON.stringify({ sub: owner.email, av: 0 })).toString('base64url') + '.';
  for (const [name, token] of [['malformed', 'invalid'], ['tampered', owner.token.slice(0, -5) + 'xxxxx'], ['alg_none', unsigned], ['expired', expiredJwt(owner.email)]]) {
    const result = await call('/api/users/me', { client, token }); record(client, `jwt_${name}`, result.status === 401, { status: result.status });
  }
  for (const [name, route, method, body] of [
    ['server', `/api/servers/${server.id}`], ['members', `/api/servers/${server.id}/members`],
    ['history', `/api/channels/${channel}/messages`], ['send', `/api/channels/${channel}/messages`, 'POST', { content: 'Unauthorized' }],
    ['message_delete', `/api/channels/${channel}/messages/${message.id}`, 'DELETE'],
    ['server_appearance', `/api/servers/${server.id}/appearance`, 'PUT', { accentColor: '#112233' }],
  ]) {
    const result = await call(route, { client, token: outsider.token, method, body }); record(client, `idor_${name}`, denied(result), { status: result.status });
  }
  const role = await call(`/api/servers/${server.id}/members/role`, { client, token: member.token, method: 'PUT', body: { userEmail: member.email, role: 'ADMIN' } });
  record(client, 'role_escalation', denied(role), { status: role.status });
  await requireData(`/api/direct-messages?contact=${encodeURIComponent(member.email)}`, { client, token: owner.token, method: 'POST', body: { content: 'Private direct message audit' } });
  const direct = await requireData(`/api/direct-messages?contact=${encodeURIComponent(owner.email)}`, { client, token: outsider.token });
  record(client, 'direct_message_isolation', Array.isArray(direct) && direct.length === 0, { returnedMessages: direct.length });
  const invite = await requireData('/api/calls', { client, token: owner.token, method: 'POST', body: { callee: member.email, requestId: randomUUID() } });
  const stolenCall = await call(`/api/calls/${invite.id}`, { client, token: outsider.token });
  record(client, 'call_isolation', denied(stolenCall), { status: stolenCall.status });
  await requireData(`/api/calls/${invite.id}`, { client, token: owner.token, method: 'POST', body: { action: 'end' } });
  const weakRegistration = await call('/api/auth/register', { client, method: 'POST', body: { username: 'weak_' + run + client.toLowerCase(), email: 'weak_' + run + client.toLowerCase() + '@example.test', password: '123456' } });
  record(client, 'weak_password', denied(weakRegistration), { status: weakRegistration.status, passwordLength: 6, commonPasswordAccepted: Boolean(weakRegistration.data?.data?.token) });
  const weak = await account(client, client.toLowerCase() + '_guess_target');
  const sameIp = ip(), baseline = [];
  for (let n = 0; n < 6; n++) baseline.push((await call('/api/auth/login', { client, method: 'POST', headers: { 'X-Forwarded-For': sameIp }, body: { email: weak.email, password: 'wrong_password' } })).status);
  record(client, 'fixed_ip_throttling', baseline[5] === 429, { attempts: 6, statuses: baseline });
  const rotated = []; let obtained = false;
  for (const password of ['wrong_1', 'wrong_2', weak.password]) {
    const result = await call('/api/auth/login', { client, method: 'POST', body: { email: weak.email, password } }); rotated.push(result.status); obtained ||= Boolean(result.data?.data?.token);
  }
  record(client, 'forwarded_ip_bypass', !obtained && rotated.includes(429), { afterFixedIpLimit: rotated, controlledAccountLoginSucceeded: obtained, changedHeaderNotNetworkAddress: true });
  const duplicate = await call('/api/auth/register', { client, method: 'POST', body: { username: `duplicate_${run}`, email: owner.email, password: owner.password } });
  record(client, 'generic_registration_error', !duplicate.data?.error?.includes('Email ja cadastrado'), { status: duplicate.status, reportsExistingEmail: duplicate.data?.error?.includes('Email ja cadastrado') });
  const injection = await call('/api/auth/login', { client, method: 'POST', body: { email: "' OR 1=1 --", password: 'invalid' } });
  record(client, 'login_injection', denied(injection), { status: injection.status, noToken: !injection.data?.data?.token });
  const invalidJson = await fetch(api + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': ip() }, body: '{', signal: AbortSignal.timeout(15000) });
  const errorText = await invalidJson.text();
  record(client, 'error_stack', !/java\.lang|at com\.|stackTrace|SQLException/.test(errorText), { status: invalidJson.status, stackTraceExposed: /java\.lang|at com\.|stackTrace|SQLException/.test(errorText) });
  const privatePath = await upload(fixture, 'Private attachment audit');
  const publicRead = await call(privatePath, { client, raw: true });
  const authorizedRead = await call(privatePath, { client, token: owner.token, raw: true });
  record(client, 'attachment_owner_read', authorizedRead.status === 200 && authorizedRead.data === 'Private attachment audit', { status: authorizedRead.status });
  record(client, 'attachment_public_read', publicRead.status !== 200, { status: publicRead.status, privateContentWithoutToken: publicRead.data === 'Private attachment audit' });
  const deleted = await call(`/api/files?fileUrl=${encodeURIComponent(api + privatePath)}`, { client, token: outsider.token, method: 'DELETE' });
  const after = await call(privatePath, { client, raw: true });
  const uploadRoot = path.join(root, 'chatapp-backend/target/desktop-test-uploads');
  const removedFromStorage = !(await readdir(uploadRoot)).includes(path.basename(privatePath));
  record(client, 'attachment_cross_account_delete', denied(deleted) && !removedFromStorage, { deleteStatus: deleted.status, before: publicRead.status, after: after.status, removedFromStorage });
  const beforeFiles = new Set(await readdir(uploadRoot));
  const orphanContent = `orphan-audit-${run}-${client}`;
  const rejectedUpload = await call(`/api/channels/${channel}/upload`, { client, token: outsider.token, method: 'POST', body: multipart(orphanContent) });
  const newFiles = (await readdir(uploadRoot)).filter(file => !beforeFiles.has(file));
  let orphanFound = false;
  for (const file of newFiles) if (file.endsWith('.txt') && await readFile(path.join(uploadRoot, file), 'utf8') === orphanContent) { orphanFound = true; createdFiles.set('/uploads/' + file, outsider.token); }
  record(client, 'upload_before_authorization', denied(rejectedUpload) && !orphanFound, { status: rejectedUpload.status, fileSavedDespiteDenial: orphanFound });
  const fakeImage = await call('/api/users/me/media/avatar', { client, token: owner.token, method: 'POST', body: multipart('<html>Not an image</html>', 'avatar.png', 'image/png') });
  record(client, 'appearance_content_validation', denied(fakeImage), { status: fakeImage.status });
  const oversize = await call(`/api/channels/${channel}/messages?size=2147483647`, { client, token: owner.token });
  record(client, 'pagination_bound', denied(oversize), { status: oversize.status });
  const cors = await call('/api/users/me', { client, token: owner.token, headers: { Origin: 'https://untrusted.example.test' } });
  record(client, 'rest_cors', !cors.headers['access-control-allow-origin'], { status: cors.status, allowOrigin: cors.headers['access-control-allow-origin'] ?? null });
  const noAuthWs = await stomp(null);
  const otherWs = await stomp(outsider.token, `/topic/channel/${channel}`);
  record(client, 'websocket_auth', noAuthWs !== 'CONNECTED' && noAuthWs !== 'TIMEOUT', { outcome: noAuthWs });
  record(client, 'websocket_cross_account', otherWs === 'ERROR' || otherWs === 'CLOSED', { outcome: otherWs });
  const retainedSubscription = await stomp(member.token, `/topic/channel/${channel}`, async () => {
    await requireData(`/api/servers/${server.id}/members/${encodeURIComponent(member.email)}`, { client, token: owner.token, method: 'DELETE' });
    await requireData(`/api/channels/${channel}/messages`, { client, token: owner.token, method: 'POST', body: { content: 'After removal private message' } });
  });
  record(client, 'websocket_after_membership_removal', retainedSubscription !== 'MESSAGE', { outcome: retainedSubscription });
  console.log(`${client}: ${rows.filter(row => row.client === client).length} checks completed (tokens omitted).`);
}

// Local interception proof: capture only our synthetic account's HTTP login/header.
let observedPassword = false, capturedToken;
const proxy = createServer((request, response) => {
  const buffers = [];
  request.on('data', chunk => buffers.push(chunk));
  request.on('end', () => {
    const body = Buffer.concat(buffers); observedPassword ||= body.toString().includes(fixtures[0].owner.password);
    if (request.headers.authorization) capturedToken = request.headers.authorization.replace(/^Bearer /, '');
    const upstream = httpRequest(api + request.url, { method: request.method, headers: { ...request.headers, host: '127.0.0.1:18085', 'X-Forwarded-For': ip() } }, incoming => { response.writeHead(incoming.statusCode, incoming.headers); incoming.pipe(response); });
    upstream.on('error', () => { response.writeHead(502); response.end(); }); upstream.end(body);
  });
});
await new Promise(resolve => proxy.listen(0, '127.0.0.1', resolve));
try {
  const proxyUrl = `http://127.0.0.1:${proxy.address().port}`;
  const login = await fetch(proxyUrl + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: fixtures[0].owner.email, password: fixtures[0].owner.password }) });
  const token = (await login.json()).data.token;
  await fetch(proxyUrl + '/api/users/me', { headers: { Authorization: `Bearer ${token}` } });
  const replay = await call('/api/users/me', { token: capturedToken });
  rows.push({ client: 'Shared', id: 'development_http_capture', result: 'ENVIRONMENT_LIMITATION', evidence: { observedSyntheticPassword: observedPassword, observedBearer: Boolean(capturedToken), replayStatus: replay.status, environment: 'local HTTP only; production TLS rejection is covered separately' } });
} finally { await new Promise(resolve => proxy.close(resolve)); }

const require = createRequire(path.join(root, 'samuschat-desktop/package.json'));
const { chromium } = require('@playwright/test');
const browser = await chromium.launch();
try {
  const fixture = fixtures[0], context = await browser.newContext({ extraHTTPHeaders: { 'X-Forwarded-For': ip() } });
  const page = await context.newPage();
  await page.goto(desktop); await page.getByLabel('Email', { exact: true }).fill(fixture.owner.email); await page.getByLabel('Senha', { exact: true }).fill(fixture.owner.password); await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await page.getByRole('button', { name: 'Amigos', exact: true }).waitFor();
  const savedToken = await page.evaluate(() => sessionStorage.getItem('samuschat.desktop.session'));
  await requireData(`/api/channels/${fixture.channel}/messages`, { token: fixture.owner.token, method: 'POST', body: { content: '<img src=x onerror="window.auditMessageExecuted=true">' } });
  await page.getByRole('button', { name: fixture.server.name, exact: true }).click();
  await page.locator('.conversation-link').first().click();
  await page.getByText('<img src=x onerror="window.auditMessageExecuted=true">', { exact: true }).waitFor();
  record('PC', 'message_html_escaped', !await page.evaluate(() => Boolean(window.auditMessageExecuted)), { textRenderedLiterally: true, scriptExecuted: await page.evaluate(() => Boolean(window.auditMessageExecuted)) });
  await page.locator('.profile-button').click(); await page.getByRole('button', { name: 'Sair da conta', exact: true }).click();
  await page.getByRole('button', { name: 'Entrar', exact: true }).waitFor();
  const replay = await call('/api/users/me', { token: savedToken });
  record('PC', 'logout_token_replay', replay.status === 401, { tokenRemovedLocally: await page.evaluate(() => sessionStorage.getItem('samuschat.desktop.session') === null), replayStatus: replay.status });
  await page.evaluate(() => sessionStorage.setItem('samuschat.desktop.session', 'AUDIT_SENTINEL_NOT_A_TOKEN'));
  const oldToken = fixture.owner.token;
  fixture.owner.token = (await requireData('/api/auth/login', { method: 'POST', body: { email: fixture.owner.email, password: fixture.owner.password } })).token;
  for (const [file, token] of createdFiles) if (token === oldToken) createdFiles.set(file, fixture.owner.token);
  const html = '<!doctype html><script>document.body || document.documentElement; window.auditResult = {executed:true, sentinelReadable: sessionStorage.getItem("samuschat.desktop.session") === "AUDIT_SENTINEL_NOT_A_TOKEN"};</script>';
  for (const target of fixtures) {
    const attempt = await call('/api/channels/' + target.channel + '/upload', { client: target.client, token: target.owner.token, method: 'POST', body: multipart(html, 'audit.html', 'text/plain') });
    let executed = false, sentinelReadable = false;
    if (attempt.status === 200) {
      const pathname = new URL(attempt.data.data.attachmentUrl).pathname;
      createdFiles.set(pathname, target.owner.token);
      if (target.client === 'PC') { await page.goto(desktop + pathname); const outcome = await page.evaluate(() => window.auditResult ?? {}); executed = Boolean(outcome.executed); sentinelReadable = Boolean(outcome.sentinelReadable); }
    }
    record(target.client, 'upload_active_html', denied(attempt), { status: attempt.status, executed, sentinelReadable });
  }
  await context.close();
} finally { await browser.close(); }

for (const [file, token] of createdFiles) await call(`/api/files?fileUrl=${encodeURIComponent(api + file)}`, { token, method: 'DELETE' });
const result = { run, testedAt: new Date().toISOString(), api, desktop, requests, counts: { checks: rows.length, environmentLimitations: rows.filter(row => row.result === 'ENVIRONMENT_LIMITATION').length, controls: rows.filter(row => row.result === 'CONTROL_OBSERVED').length, findings: rows.filter(row => row.result === 'FINDING').length }, rows };
await mkdir(path.join(root, 'logs'), { recursive: true });
await writeFile(path.join(root, 'logs/security-api-results.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result.counts));
for (const row of rows.filter(row => row.result === 'FINDING')) console.log(`${row.client}: ${row.id}: ${JSON.stringify(row.evidence)}`);
// An audit exposing weaknesses is not a passing security gate.
process.exitCode = result.counts.findings ? 1 : 0;
