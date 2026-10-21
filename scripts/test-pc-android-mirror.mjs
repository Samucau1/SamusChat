// Physical-device probe. Requires the disposable H2 API on 18082, Vite on
// 5174 targeting it, and assembleValidation -PapiBaseUrl=http://127.0.0.1:18082/.
// Does not authenticate against the development API or modify normal app data.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../samuschat-desktop/node_modules/playwright');
const root = fileURLToPath(new URL('../', import.meta.url));
const adb = path.join(root, '.android-sdk/platform-tools/adb.exe');
const serial = process.argv[2];
if (!serial || !/^[A-Za-z0-9_-]+$/.test(serial)) throw new Error('Provide one USB serial as the first argument.');
const requestedCodec = process.argv[3] ?? 'default';
const youtube = process.env.SAMUSCHAT_PROBE_YOUTUBE === '1';
if (!['default', 'VP8', 'H264'].includes(requestedCodec)) throw new Error('Codec must be default, VP8 or H264.');
const pkg = 'com.samuschat.validation', api = 'http://127.0.0.1:18082';
const out = path.join(root, 'logs/pc-android-mirror', requestedCodec === 'default' ? '' : requestedCodec.toLowerCase()); mkdirSync(out, { recursive: true });
const device = (...args) => execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function ui() {
  assert.match(device('shell', 'uiautomator', 'dump', '/sdcard/samus-mirror-test.xml'), /dumped to:/);
  return [...device('shell', 'cat', '/sdcard/samus-mirror-test.xml').matchAll(/<node\b([^>]+)>/g)].map(match =>
    Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(attr => [attr[1], attr[2]])));
}
async function tap(text) {
  const deadline = Date.now() + 30000;
  let lastNodes = [];
  while (Date.now() < deadline) {
    lastNodes = ui();
    const matches = lastNodes.filter(node => node.text === text || node['content-desc'] === text);
    if (matches.length) {
      const bounds = matches.at(-1).bounds.match(/\d+/g).map(Number);
      device('shell', 'input', 'tap', String(Math.round((bounds[0] + bounds[2]) / 2)), String(Math.round((bounds[1] + bounds[3]) / 2))); return;
    }
    await delay(300);
  }
  if (lastNodes.some(node => node.package === pkg)) writeFileSync(path.join(out, 'missing-control.json'), JSON.stringify(lastNodes, null, 2));
  throw new Error(`Android UI missing: ${text}`);
}
function tapField(password) {
  const node = ui().find(item => item.class === 'android.widget.EditText' && (item.password === 'true') === password);
  assert.ok(node, 'Missing Android login field');
  const bounds = node.bounds.match(/\d+/g).map(Number);
  device('shell', 'input', 'tap', String(Math.round((bounds[0] + bounds[2]) / 2)), String(Math.round((bounds[1] + bounds[3]) / 2)));
}
function shot(name) { writeFileSync(path.join(out, name), execFileSync(adb, ['-s', serial, 'exec-out', 'screencap', '-p'], { timeout: 20000 })); }
async function request(method, route, body, token) {
  const result = await fetch(api + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const envelope = await result.json(); assert.equal(result.ok && envelope.success, true, `Test API failed: ${route}`); return envelope.data;
}
const run = Date.now(), password = 'MirrorSynthetic123!';
const pc = { username: `MirrorPC${run}`, email: `mirror_pc_${run}@example.test`, password };
const cell = { username: `MirrorCell${run}`, email: `mirror_cell_${run}@example.test`, password };
const installed = name => { try { return device('shell', 'pm', 'path', name).includes('package:'); } catch { return false; } };
let browser, token, normalWasInstalled = false, validationWasInstalled = false, validationTouched = false;
try {
  assert.equal((await (await fetch(api + '/actuator/health')).json()).status, 'UP');
  token = (await request('POST', '/api/auth/register', pc)).token;
  const cellToken = (await request('POST', '/api/auth/register', cell)).token;
  for (const accountToken of [token, cellToken]) {
    const form = new FormData();
    const image = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    // Opaque red fixture allows visual confirmation rather than a transparent pixel.
    image.set([255, 50, 50, 255, 50, 50], 13);
    image[image.indexOf(Buffer.from([0x21, 0xf9, 0x04])) + 3] = 0;
    form.append('file', new Blob([image], { type: 'image/gif' }), 'avatar.gif');
    const uploaded = await fetch(api + '/api/users/me/media/avatar', { method: 'POST', headers: { Authorization: `Bearer ${accountToken}` }, body: form });
    assert.equal(uploaded.ok, true, 'Synthetic avatar upload failed');
  }
  normalWasInstalled = installed('com.samuschat');
  validationWasInstalled = installed(pkg);
  console.log('Installing isolated validation APK; normal app/account remain unchanged.');
  device('install', '-r', path.join(root, 'samuschat-android/app/build/outputs/apk/validation/app-validation.apk'));
  validationTouched = true;
  // This package is explicitly disposable. Never clear com.samuschat.
  device('shell', 'pm', 'clear', pkg);
  device('shell', 'pm', 'grant', pkg, 'android.permission.RECORD_AUDIO');
  device('reverse', 'tcp:18082', 'tcp:18082');
  device('shell', 'am', 'start', '-W', '-n', `${pkg}/com.samuschat.MainActivity`);
  await tap('Email'); device('shell', 'input', 'text', cell.email);
  tapField(true); device('shell', 'input', 'text', password); device('shell', 'input', 'keyevent', '4');
  await tap('Entrar');
  await tap('Amigos');
  browser = await chromium.launch({ channel: process.env.SAMUSCHAT_PROBE_BROWSER || undefined, ignoreDefaultArgs: youtube ? ['--mute-audio'] : [], args: youtube ? ['--auto-select-tab-capture-source-by-title=SamusChat YouTube validation', '--autoplay-policy=no-user-gesture-required'] : ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--autoplay-policy=no-user-gesture-required'] });
  const context = await browser.newContext({ permissions: ['microphone'], viewport: { width: 1440, height: 900 } });
  await context.addInitScript(({ codec, youtube }) => {
    if (youtube) Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      const context = new AudioContext(), output = context.createMediaStreamDestination(), tone = context.createOscillator();
      tone.connect(output); tone.start(); await context.resume(); return output.stream;
    } });
    const Native = window.RTCPeerConnection;
    if (codec !== 'default') {
      const originalOffer = Native.prototype.createOffer;
      Native.prototype.createOffer = function(...args) {
        const transceiver = this.getTransceivers().find(item => item.sender.track?.kind === 'video');
        const codecs = RTCRtpSender.getCapabilities('video').codecs;
        const preferred = codecs.filter(item => item.mimeType.toLowerCase() === `video/${codec}`.toLowerCase());
        if (!preferred.length) throw new Error(`Browser cannot offer ${codec}`);
        transceiver.setCodecPreferences([...preferred, ...codecs.filter(item => !preferred.includes(item))]);
        return originalOffer.apply(this, args);
      };
    }
    window.mirrorPeers = [];
    window.screenAudioProbe = { packets: 0, nonzero: 0, bytes: 0, errors: [] };
    const createChannel = Native.prototype.createDataChannel;
    Native.prototype.createDataChannel = function(...args) {
      const channel = createChannel.apply(this, args), send = channel.send.bind(channel);
      if (args[0] === 'device-audio') channel.send = buffer => {
        if (typeof buffer === 'string') return send(buffer);
        window.screenAudioProbe.packets++;
        window.screenAudioProbe.bytes += buffer.byteLength;
        if (new Uint8Array(buffer).some(sample => sample !== 0)) window.screenAudioProbe.nonzero++;
        return send(buffer);
      };
      return channel;
    };
    window.addEventListener('error', event => window.screenAudioProbe.errors.push(event.message));
    window.RTCPeerConnection = new Proxy(Native, { construct(Target, args) { const peer = new Target(...args); window.mirrorPeers.push(peer); return peer; } });
  }, { codec: requestedCodec, youtube });
  const page = await context.newPage(); await page.goto('http://127.0.0.1:5174/');
  await page.getByLabel('Email', { exact: true }).fill(pc.email); await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await page.getByRole('button', { name: `${cell.username}`, exact: true }).click();
  await page.getByRole('button', { name: 'Ligar', exact: true }).click();
  await tap('Aceitar'); await page.locator('.call-panel strong').filter({ hasText: 'Em chamada' }).waitFor({ timeout: 30000 });
  await page.locator('[data-participant-id=remote] .call-avatar img').waitFor();
  const photoNodes = ui();
  assert.ok(photoNodes.some(node => node['content-desc'] === `Foto de ${pc.username}`), 'Android must show PC avatar');
  assert.ok(photoNodes.some(node => node.text === 'Falando'), 'Android must indicate received synthetic microphone activity');
  if (youtube) {
    const source = await context.newPage();
    await source.goto('https://www.youtube.com/watch?v=aqz-KE-bpKQ', { waitUntil: 'domcontentloaded', timeout: 60000 });
    for (const label of ['Reject all', 'Rejeitar tudo', 'Accept all', 'Aceitar tudo']) {
      const button = source.getByRole('button', { name: label, exact: true }); if (await button.count()) await button.first().click();
    }
    await source.locator('video').first().waitFor({ timeout: 30000 });
    await source.evaluate(async () => { const video = document.querySelector('video'); video.muted = false; video.volume = 0.5; await video.play(); });
    await source.waitForFunction(() => { const video = document.querySelector('video'); return video && video.currentTime > 2 && !video.paused; }, { timeout: 30000 });
    await source.evaluate(() => { document.title = 'SamusChat YouTube validation'; });
    await page.bringToFront();
    console.log('Capturing real YouTube tab with native Edge display/audio capture.');
  } else await page.evaluate(() => {
    Object.defineProperty(navigator.mediaDevices, 'getDisplayMedia', { configurable: true, value: async options => {
      const height = options.video.height.ideal, width = Math.ceil(height * 16 / 9 / 2) * 2;
      const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
      const paint = canvas.getContext('2d'); paint.fillStyle = '#2468bc'; paint.fillRect(0, 0, width, height);
      const stream = canvas.captureStream(30), mirror = document.createElement('video'); mirror.muted = true; mirror.srcObject = stream; await mirror.play();
      const audioContext = new AudioContext(), output = audioContext.createMediaStreamDestination();
      const tone = audioContext.createOscillator(), gain = audioContext.createGain();
      tone.frequency.value = 997; gain.gain.value = 0.2; tone.connect(gain); gain.connect(output); tone.start(); await audioContext.resume();
      const audioTrack = output.stream.getAudioTracks()[0]; stream.addTrack(audioTrack);
      audioTrack.addEventListener('ended', () => { tone.stop(); void audioContext.close(); });
      let frame = 0;
      const timer = setInterval(() => {
        paint.fillStyle = '#2468bc'; paint.fillRect(0, 0, width, height);
        if (mirror.readyState >= 2) paint.drawImage(mirror, 70, 55, width - 140, height - 110);
        paint.strokeStyle = '#fff'; paint.lineWidth = 6; paint.strokeRect(16, 16, width - 32, height - 32);
        paint.fillStyle = '#ff3344'; paint.fillRect(25 + frame++ % 120, height / 2, 18, 18);
        paint.fillStyle = '#fff'; paint.font = '24px sans-serif'; paint.fillText(`Recursive test frame ${frame}`, 32, 45);
      }, 33);
      const track = stream.getVideoTracks()[0], stop = track.stop.bind(track);
      track.stop = () => { clearInterval(timer); mirror.pause(); mirror.srcObject = null; stop(); };
      return stream;
    } });
  });
  await page.getByRole('button', { name: 'Compartilhar tela', exact: true }).click();
  await page.getByRole('button', { name: 'Parar transmissão', exact: true }).waitFor();
  console.log('Recursive stream active. Sampling physical Android rendering.');
  await delay(6000); shot('android-inline.png');
  await tap('Transmissão recebida'); shot('android-controls.png');
  console.log('Opening Android fullscreen.');
  await tap('Assistir em tela cheia');
  await delay(6000); shot('android-fullscreen.png');
  const stats = await page.evaluate(async () => {
    const result = [], microphone = [];
    for (const peer of window.mirrorPeers) {
      const report = await peer.getStats();
      report.forEach(item => {
        if (item.type === 'outbound-rtp' && item.kind === 'video') result.push({ frames: item.framesEncoded, codec: report.get(item.codecId)?.mimeType, width: item.frameWidth, height: item.frameHeight, fps: item.framesPerSecond, encoder: item.encoderImplementation });
        if (item.type === 'inbound-rtp' && item.kind === 'audio') microphone.push({ packets: item.packetsReceived, energy: item.totalAudioEnergy, level: item.audioLevel });
      });
    }
    return { sender: result, microphone, screenAudio: window.screenAudioProbe, notices: [...document.querySelectorAll('.call-audio-notice,.error-notice')].map(item => item.textContent) };
  });
  const processId = device('shell', 'pidof', pkg).trim();
  const log = device('logcat', '-d', `--pid=${processId}`, '-s', 'CallStats', 'ScreenPreview');
  writeFileSync(path.join(out, 'android-stats.log'), log);
  const receivedFrames = [...log.matchAll(/frames=(\d+)/g)].map(match => Number(match[1]));
  const devicePackets = [...log.matchAll(/deviceAudioPackets=(\d+) nonzeroSamples=(\d+)/g)].map(match => ({ packets: Number(match[1]), nonzero: Number(match[2]) }));
  const opusPlayback = [...log.matchAll(/screenOpusFrames=(\d+) head=(\d+) route=(\d+)/g)].map(match => ({ frames: Number(match[1]), head: Number(match[2]), route: Number(match[3]) }));
  writeFileSync(path.join(out, 'audio-diagnostics.json'), JSON.stringify({ ...stats, devicePackets, opusPlayback, receivedFrames }, null, 2));
  assert.ok(opusPlayback.at(-1)?.frames >= 100 && opusPlayback.at(-1)?.head > 48000, 'Android must decode and play PC Opus screen audio');
  assert.equal(opusPlayback.at(-1)?.route, 2, 'USB probe without headset must route screen audio to the loudspeaker');
  assert.ok(receivedFrames.length >= 2 && receivedFrames.at(-1) > receivedFrames[0] + 100, 'Android stopped decoding frames');
  if (requestedCodec !== 'default') assert.ok(stats.sender.some(item => item.codec?.toLowerCase() === `video/${requestedCodec}`.toLowerCase()), 'Requested codec was not negotiated');
  writeFileSync(path.join(out, 'result.json'), JSON.stringify({ syntheticRecursiveSource: true, normalAppPreserved: normalWasInstalled, requestedCodec, ...stats, receivedFrames, androidStatsAvailable: log.includes('CallStats'), visualReviewRequired: true }, null, 2));
  console.log(JSON.stringify({ ...stats, receivedFrames, devicePackets, opusPlayback, visualReviewRequired: true }));
} finally {
  if (token) { for (const call of await request('GET', '/api/calls', null, token).catch(() => [])) await request('POST', `/api/calls/${call.id}`, { action: 'end' }, token).catch(() => {}); }
  if (browser) await browser.close();
  if (validationTouched) try { device('shell', 'am', 'force-stop', pkg); if (!validationWasInstalled) device('uninstall', pkg); device('reverse', '--remove', 'tcp:18082'); if (normalWasInstalled) device('shell', 'am', 'start', '-n', 'com.samuschat/.MainActivity'); } catch { /* Preserve the original failure. */ }
}
