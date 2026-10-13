// Exercise the disposable production stack with Node's built-in HTTP client.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const base = process.env.PUBLIC_BASE_URL ?? 'https://localhost:18443';
assert.equal(new URL(base).protocol, 'https:', 'Production smoke must use HTTPS');
const compose = (...args) => execFileSync('docker', ['compose',
  ...(process.env.SMOKE_ENV_FILE ? ['--env-file', process.env.SMOKE_ENV_FILE] : []),
  '-f', 'docker-compose.prod.yml', ...(process.env.SMOKE_COMPOSE_OVERRIDE ? ['-f', process.env.SMOKE_COMPOSE_OVERRIDE] : []), ...args], { encoding: 'utf8' });
async function api(path, data, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const multipart = data instanceof FormData;
  if (!multipart) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, {
    method: 'POST', headers, body: multipart ? data : JSON.stringify(data), signal: AbortSignal.timeout(15000),
  });
  const body = await response.json();
  assert.equal(response.status, 200, JSON.stringify(body));
  return body.data;
}

for (const backend of ['backend1', 'backend2', 'backend3']) {
  const health = compose('exec', '-T', backend, 'wget', '-qO-', 'http://localhost:8080/actuator/health/readiness');
  assert.equal(JSON.parse(health).status, 'UP', backend);
}
compose('exec', '-T', 'nginx', 'nginx', '-t');
assert.equal(await (await fetch(base + '/health')).text(), 'ok\n');
if (process.env.SMOKE_HTTP_URL) {
  const insecure = await fetch(process.env.SMOKE_HTTP_URL + '/api/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-Proto': 'https' }, body: '{}', signal: AbortSignal.timeout(15000) });
  assert.equal(insecure.status, 426, 'HTTP and forged forwarding headers must be rejected');
}
const run = Date.now().toString(36);
const user = { username: `smoke_${run}`, email: `smoke_${run}@example.test`, password: 'SmokeTest123!Chat' };
await api('/api/auth/register', user);
const { token } = await api('/api/auth/login', user);
const server = await api('/api/servers', { name: 'Smoke server', description: 'CI' }, token);
const channel = server.channels[0].id;
const message = await api(`/api/channels/${channel}/messages`, { content: 'production smoke' }, token);
assert.equal(message.content, 'production smoke');
const upload = new FormData();
upload.append('file', new Blob(['shared upload'], { type: 'text/plain' }), 'smoke.txt');
const attachment = await api(`/api/channels/${channel}/upload`, upload, token);
assert.ok(attachment.attachmentUrl.startsWith(base + '/uploads/'));
const uploadPath = new URL(attachment.attachmentUrl).pathname;
const anonymous = await fetch(base + uploadPath);
assert.equal(anonymous.status, 401, 'Attachments must require authentication');
const protectedDownload = await fetch(base + uploadPath, { headers: { Authorization: `Bearer ${token}` } });
assert.equal(protectedDownload.status, 200);
assert.equal(await protectedDownload.text(), 'shared upload');
assert.equal(protectedDownload.headers.get('cache-control'), 'no-store');
assert.ok(protectedDownload.headers.get('strict-transport-security'));
const storedName = uploadPath.split('/').at(-1);
assert.match(storedName, /^[0-9a-f-]{36}\.txt$/);
for (const backend of ['backend1', 'backend2', 'backend3']) {
  const content = compose('exec', '-T', backend, 'cat', '/app/uploads/' + storedName);
  assert.equal(content, 'shared upload', backend);
}
await api('/api/auth/logout', {}, token);
assert.equal((await fetch(base + uploadPath, { headers: { Authorization: `Bearer ${token}` } })).status, 401, 'Logout must revoke the old token across instances');
console.log('All instances healthy; PostgreSQL, TLS, HTTP rejection, authentication, protected/shared uploads and logout passed.');
