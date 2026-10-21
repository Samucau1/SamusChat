// Captures only this isolated synthetic tab, never the user's desktop or tabs.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require('../samuschat-desktop/node_modules/playwright');
const browser = await chromium.launch({ channel: 'msedge', ignoreDefaultArgs: ['--mute-audio'], args: ['--auto-accept-this-tab-capture', '--autoplay-policy=no-user-gesture-required'] });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto('http://127.0.0.1:5174/');
  await page.evaluate(() => {
    document.title = 'SamusChat isolated synthetic audio capture';
    document.body.innerHTML = '<button id="capture">Capturar somente esta aba de teste</button>';
    const context = new AudioContext(), tone = context.createOscillator(), gain = context.createGain();
    tone.frequency.value = 997; gain.gain.value = 0.1; tone.connect(gain); gain.connect(context.destination); tone.start();
    document.querySelector('button').onclick = async () => {
      let stream;
      try {
        await context.resume();
        stream = await navigator.mediaDevices.getDisplayMedia({ video: { displaySurface: 'browser' }, audio: { suppressLocalAudioPlayback: false }, preferCurrentTab: true, selfBrowserSurface: 'include', systemAudio: 'include' });
        const audio = stream.getAudioTracks()[0];
        if (!audio) throw new Error('Native tab capture returned no audio track');
        const source = context.createMediaStreamSource(new MediaStream([audio])), analyser = context.createAnalyser(); source.connect(analyser);
        await new Promise(resolve => setTimeout(resolve, 1200));
        const values = new Float32Array(analyser.fftSize); analyser.getFloatTimeDomainData(values);
        const rms = Math.sqrt(values.reduce((sum, value) => sum + value * value, 0) / values.length);
        window.captureResult = { surface: stream.getVideoTracks()[0].getSettings().displaySurface, audioTracks: stream.getAudioTracks().length, rms };
        source.disconnect(); analyser.disconnect();
      } catch (error) { window.captureResult = { error: String(error) }; }
      finally { stream?.getTracks().forEach(track => track.stop()); tone.stop(); await context.close(); }
    };
  });
  await page.click('#capture');
  await page.waitForFunction(() => window.captureResult, { timeout: 20000 });
  const result = await page.evaluate(() => window.captureResult); console.log(JSON.stringify(result));
  assert.equal(result.surface, 'browser'); assert.ok(result.rms > 0.02, 'Native Edge tab audio must contain the synthetic tone');
} finally { await browser.close(); }
