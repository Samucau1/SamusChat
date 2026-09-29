// Exercise the disposable production stack with Node's built-in HTTP client.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const base = process.env.PUBLIC_BASE_URL ?? 'http://localhost:18080';
const compose = (...args) => execFileSync('docker', ['compose', '-f', 'docker-compose.prod.yml', ...args], { encoding: 'utf8' });
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
const user = { username: 'smoke', email: 'smoke@example.com', password: 'SmokeTest123!' };
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
for (const backend of ['backend1', 'backend2', 'backend3']) {
  const content = compose('exec', '-T', backend, 'wget', '-qO-', 'http://localhost:8080' + uploadPath);
  assert.equal(content, 'shared upload', backend);
}
console.log('All instances healthy; PostgreSQL, authentication, routing and shared uploads passed.');
