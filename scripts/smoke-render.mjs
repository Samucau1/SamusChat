// Run against the isolated Render container and disposable PostgreSQL, never production.
import assert from 'node:assert/strict';

const base = process.env.RENDER_SMOKE_URL ?? 'http://127.0.0.1:19080';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'Smoke requires a local disposable deployment');
async function api(path, data, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (data !== undefined && !(data instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, { method: data === undefined ? 'GET' : 'POST', headers,
    body: data === undefined ? undefined : data instanceof FormData ? data : JSON.stringify(data), signal: AbortSignal.timeout(15000) });
  const body = await response.json();
  assert.equal(response.status, 200, `${path}: ${JSON.stringify(body)}`);
  return body.data;
}

const deadline = Date.now() + 180000;
while (true) {
  try {
    const response = await fetch(base + '/actuator/health/readiness', { signal: AbortSignal.timeout(3000) });
    if (response.ok && (await response.json()).status === 'UP') break;
  } catch { /* Java may still be starting or applying Flyway migrations. */ }
  assert.ok(Date.now() < deadline, 'Render container did not become ready');
  await new Promise(resolve => setTimeout(resolve, 1000));
}
const index = await fetch(base + '/');
assert.equal(index.status, 200);
assert.match(await index.text(), /<div id="root"><\/div>/);
const worker = await fetch(base + '/push-sw.js');
assert.equal(worker.status, 200);
assert.match(worker.headers.get('content-type'), /javascript/);
assert.match(worker.headers.get('cache-control'), /no-cache/);
assert.equal((await fetch(base + '/api/servers')).status, 401);
assert.equal((await fetch(base + '/.env')).status, 404);

const stamp = crypto.randomUUID().slice(0, 8);
const session = await api('/api/auth/register', { username: 'render_' + stamp, email: `render_${stamp}@example.test`, password: 'RenderSmokeOnly123!' });
const server = await api('/api/servers', { name: 'Render smoke ' + stamp }, session.token);
const channel = server.channels[0].id;
const socketUrl = new URL('/ws/websocket', base); socketUrl.protocol = 'ws:';
const socket = new WebSocket(socketUrl);
const frames = [];
socket.addEventListener('message', event => frames.push(String(event.data)));
const waitFor = async predicate => {
  const until = Date.now() + 15000;
  while (!predicate()) { assert.ok(Date.now() < until, 'STOMP frame did not arrive'); await new Promise(resolve => setTimeout(resolve, 50)); }
};
try {
  await waitFor(() => socket.readyState === WebSocket.OPEN);
  socket.send(`CONNECT\naccept-version:1.2\nAuthorization:Bearer ${session.token}\nheart-beat:0,0\n\n\0`);
  await waitFor(() => frames.some(frame => frame.startsWith('CONNECTED')));
  socket.send(`SUBSCRIBE\nid:render-smoke\ndestination:/topic/channel/${channel}\n\n\0`);
  // Spring's simple broker has no subscription receipts; allow the inbound executor to subscribe.
  await new Promise(resolve => setTimeout(resolve, 500));
  const message = await api(`/api/channels/${channel}/messages`, { content: 'Redis-free Render message ' + stamp }, session.token);
  await waitFor(() => frames.some(frame => frame.startsWith('MESSAGE') && frame.includes(message.content)));
  const form = new FormData(); form.append('file', new Blob(['Render smoke attachment'], { type: 'text/plain' }), 'smoke.txt');
  const attachment = await api(`/api/channels/${channel}/upload`, form, session.token);
  assert.ok(attachment.attachmentUrl.startsWith(base + '/uploads/'));
  const attachmentPath = new URL(attachment.attachmentUrl).pathname;
  assert.equal((await fetch(base + attachmentPath)).status, 401);
  const download = await fetch(base + attachmentPath, { headers: { Authorization: `Bearer ${session.token}` } });
  assert.equal(download.status, 200); assert.equal(await download.text(), 'Render smoke attachment');
  await api('/api/auth/logout', {}, session.token);
  assert.equal((await fetch(base + '/api/servers', { headers: { Authorization: `Bearer ${session.token}` } })).status, 401);
} finally { socket.close(); }
console.log('PASS: React, service worker, readiness/PostgreSQL/Flyway, auth, Redis-free STOMP, protected uploads and logout.');
