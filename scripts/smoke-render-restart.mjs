// Disposable local container only. Never restarts Render or reads production data.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const base = process.env.RENDER_SMOKE_URL ?? 'http://127.0.0.1:19081';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
const container = 'samuschat-consolidation-app';
assert.equal(execFileSync('docker', ['inspect', '--format', '{{index .Config.Labels "samuschat.validation"}}', container], { encoding: 'utf8' }).trim(), 'consolidation');
async function ready() {
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    try { const r = await fetch(base + '/actuator/health/readiness', { signal: AbortSignal.timeout(3000) }); if (r.ok && (await r.json()).status === 'UP') return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Local validation container did not become ready');
}
async function api(path, body, token) {
  const form = body instanceof FormData;
  const response = await fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: {
    ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(!form && body !== undefined ? { 'Content-Type': 'application/json' } : {}),
  }, body: body === undefined ? undefined : form ? body : JSON.stringify(body) });
  assert.equal(response.status, 200);
  const value = await response.json(); assert.equal(value.success, true); return value.data;
}
await ready();
const stamp = crypto.randomUUID().slice(0, 8);
const email = `restart_${stamp}@example.test`, password = 'DisposableRestartOnly42!';
const session = await api('/api/auth/register', { username: `restart_${stamp}`, email, password });
const server = await api('/api/servers', { name: 'Restart validation' }, session.token);
assert.match(server.inviteCode, /^[A-Za-z0-9!@#$%*+_-]{3}$/);
const image = new FormData();
image.append('file', new Blob([readFileSync(new URL('../samuschat-desktop/tests/fixtures/social-avatar.png', import.meta.url))], { type: 'image/png' }), 'avatar.png');
const profile = await api('/api/users/me/media/avatar', image, session.token);
const upload = new FormData(); upload.append('file', new Blob(['Persistent validation bytes'], { type: 'text/plain' }), 'persistent.txt');
const message = await api(`/api/channels/${server.channels[0].id}/upload`, upload, session.token);
const imageUrl = new URL(profile.avatarUrl, base), attachmentUrl = new URL(message.attachmentUrl, base);
assert.match(imageUrl.pathname, /^\/uploads\/appearance\/[0-9a-f-]{36}\.(png|jpg)$/);
assert.match(attachmentUrl.pathname, /^\/uploads\/[0-9a-f-]{36}\.txt$/);
const imageBefore = Buffer.from(await (await fetch(base + imageUrl.pathname)).arrayBuffer());
assert.ok(imageBefore.length > 0);
// Exact synthetic file paths in this labelled test container; no host files are touched.
execFileSync('docker', ['exec', container, 'rm', '-f', '/app' + imageUrl.pathname, '/app' + attachmentUrl.pathname]);
execFileSync('docker', ['restart', container], { timeout: 60000 });
await ready();
const renewed = await api('/api/auth/login', { email, password });
assert.equal((await api('/api/users/me', undefined, renewed.token)).avatarUrl, profile.avatarUrl);
assert.equal((await api(`/api/servers/${server.id}`, undefined, renewed.token)).inviteCode, server.inviteCode);
assert.deepEqual(Buffer.from(await (await fetch(base + imageUrl.pathname)).arrayBuffer()), imageBefore);
const attachment = await fetch(base + attachmentUrl.pathname, { headers: { Authorization: `Bearer ${renewed.token}` } });
assert.equal(attachment.status, 200); assert.equal(await attachment.text(), 'Persistent validation bytes');
assert.equal((await fetch(base + attachmentUrl.pathname)).status, 401);
console.log('PASS: existing login, server code, avatar and protected attachment survived cache removal and container restart.');
