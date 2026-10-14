// Local Edge capture diagnostic. Saves metadata only, never images or video.
// Synthetic source by default; --physical-monitor selects a real monitor.
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
const require = createRequire(new URL('../samuschat-desktop/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const physical = process.argv.includes('--physical-monitor');
const browser = await chromium.launch({ headless: true, channel: 'msedge', args: [
  '--auto-select-screen-capture-source', ...(physical ? [] : ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream']),
] });
let result;
try {
  const page = await browser.newPage();
  await page.goto('http://127.0.0.1:5175');
  await page.evaluate(async () => {
    const modulePath = '/src/models/screenShare.ts';
    const { captureScreen, constrainScreen, constrainSender, screenProfile } = await import(modulePath);
    const button = document.createElement('button'); button.id = 'edge-monitor-probe'; button.textContent = 'Testar captura';
    button.onclick = async () => {
      let stream, placeholder, caller, receiver, stage = 'capture';
      const outcome = {};
      const settings = track => { const s = track.getSettings(); return { width: s.width, height: s.height, fps: s.frameRate, surface: s.displaySurface }; };
      try {
        stream = await captureScreen(screenProfile(900, 30)); const track = stream.getVideoTracks()[0]; outcome.source = settings(track);
        stage = 'constraints'; await constrainScreen(track, screenProfile(900, 30)); outcome.configured = settings(track);
        stage = 'negotiation'; caller = new RTCPeerConnection(); receiver = new RTCPeerConnection();
        const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 9; placeholder = canvas.captureStream(1);
        const sender = caller.addTrack(placeholder.getVideoTracks()[0], placeholder);
        const video = document.createElement('video'); video.autoplay = true; video.muted = true; document.body.append(video);
        receiver.ontrack = event => { video.srcObject = new MediaStream([event.track]); void video.play().catch(() => {}); };
        async function gather(peer) {
          if (peer.iceGatheringState === 'complete') return;
          await new Promise((resolve, reject) => {
            const timeout = setTimeout(() => { peer.removeEventListener('icegatheringstatechange', changed); reject(new Error('Local ICE timeout')); }, 4000);
            const changed = () => { if (peer.iceGatheringState === 'complete') { clearTimeout(timeout); peer.removeEventListener('icegatheringstatechange', changed); resolve(); } };
            peer.addEventListener('icegatheringstatechange', changed); changed();
          });
        }
        await caller.setLocalDescription(await caller.createOffer()); await gather(caller);
        await receiver.setRemoteDescription(caller.localDescription); await receiver.setLocalDescription(await receiver.createAnswer()); await gather(receiver);
        await caller.setRemoteDescription(receiver.localDescription);
        stage = 'replace'; await sender.replaceTrack(track);
        stage = 'sender'; await constrainSender(sender, screenProfile(900, 30));
        outcome.preference = sender.getParameters().degradationPreference;
        stage = 'decode'; const deadline = performance.now() + 4000;
        while (video.videoHeight < 750 && performance.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
        if (video.videoHeight < 750) throw new Error('Receiver did not decode the minimum height');
        outcome.received = { width: video.videoWidth, height: video.videoHeight };
        button.dataset.result = JSON.stringify({ stage: 'complete', ...outcome });
      } catch (problem) { button.dataset.result = JSON.stringify({ stage, ...outcome, error: { name: problem.name, message: problem.message } }); }
      finally { for (const media of [stream, placeholder]) media?.getTracks().forEach(track => track.stop()); caller?.close(); receiver?.close(); }
    };
    document.body.append(button);
  });
  const button = page.locator('#edge-monitor-probe'); await button.click();
  await page.waitForFunction(() => document.querySelector('#edge-monitor-probe')?.hasAttribute('data-result'), null, { timeout: 20000 });
  result = { browser: 'Microsoft Edge', version: browser.version(), headless: true, physicalMonitor: physical, result: JSON.parse(await button.getAttribute('data-result')) };
} catch (problem) {
  result = { browser: 'Microsoft Edge', version: browser.version(), headless: true, physicalMonitor: physical, unavailable: problem.message.split('\n')[0] };
} finally { await browser.close(); }
await mkdir(new URL('../logs/', import.meta.url), { recursive: true });
await writeFile(new URL(`../logs/desktop-edge-${physical ? 'physical' : 'synthetic'}-probe.json`, import.meta.url), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
if (result.unavailable || result.result?.stage !== 'complete') process.exitCode = 1;
