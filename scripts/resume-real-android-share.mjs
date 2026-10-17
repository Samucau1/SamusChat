import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const serial = process.argv[2]; if (!/^[A-Za-z0-9_-]+$/.test(serial || '')) throw new Error('USB serial required');
const adb = (...args) => execFileSync(path.join(root, '.android-sdk/platform-tools/adb.exe'), ['-s', serial, ...args], { encoding: 'utf8', timeout: 20000 });
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function evaluate(port, match, expression) {
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const target = targets.find(item => item.type === 'page' && item.url.includes(match)); if (!target) throw new Error('Test page unavailable');
  const socket = new WebSocket(target.webSocketDebuggerUrl); await new Promise(resolve => socket.addEventListener('open', resolve, { once: true }));
  const response = new Promise(resolve => socket.addEventListener('message', event => { const message = JSON.parse(event.data); if (message.id === 1) resolve(message); }));
  socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, returnByValue: true, awaitPromise: true, userGesture: true } }));
  const result = await response; socket.close(); if (result.result.exceptionDetails) throw new Error('Test page action failed'); return result.result.result.value;
}
async function tap(text) {
  const until = Date.now() + 45000;
  while (Date.now() < until) {
    adb('shell', 'uiautomator', 'dump', '/sdcard/samus-quality-ui.xml');
    const xml = adb('shell', 'cat', '/sdcard/samus-quality-ui.xml');
    const node = [...xml.matchAll(/<node\b([^>]+)>/g)].map(match => Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(attr => [attr[1], attr[2]]))).find(item => item.text === text || item['content-desc'] === text);
    if (node) { const bounds = node.bounds.match(/\d+/g).map(Number); adb('shell', 'input', 'tap', String(Math.round((bounds[0] + bounds[2]) / 2)), String(Math.round((bounds[1] + bounds[3]) / 2))); return; }
    await delay(500);
  }
  throw new Error(`Android control unavailable: ${text}`);
}
if (!process.argv.includes('--existing-call')) {
await evaluate(19222, '127.0.0.1:5173', "(()=>{window.reverseAudioProbe={frames:0,rms:0};const button=[...document.querySelectorAll('button')].find(i=>i.textContent==='Ligar');if(!button||button.disabled)throw Error('Call unavailable');button.click();return true;})()");
await tap('Aceitar');
for (let attempt = 0; attempt < 60; attempt++) {
  if (await evaluate(19222, '127.0.0.1:5173', "document.querySelector('.call-stage-header strong')?.textContent==='Em chamada'")) break;
  if (attempt === 59) throw new Error('Call did not connect'); await delay(500);
}
}
await tap('Compartilhar tela do celular'); await tap('Iniciar');
adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', 'https://m.youtube.com/watch?v=aqz-KE-bpKQ', '-p', 'com.android.chrome');
adb('forward', 'tcp:19223', 'localabstract:chrome_devtools_remote');
await delay(1500);
console.log(await evaluate(19223, 'youtube.com/watch', `(async()=>{
  const video=document.querySelector('video'); if(!video) throw Error('YouTube video not loaded');
  const player=document.getElementById('movie_player');
  const qualities=player?.getAvailableQualityLevels?.()||[];
  if(qualities.includes('hd720')) { player.setPlaybackQualityRange?.('hd720','hd720'); player.setPlaybackQuality?.('hd720'); }
  video.muted=false;video.volume=0.5;await video.play();
  try {await video.requestFullscreen(); await screen.orientation.lock('landscape');} catch {}
  return JSON.stringify({videoPlaying:!video.paused,sourceWidth:video.videoWidth,sourceHeight:video.videoHeight,availableQualities:qualities});
})()`));
await delay(4000);
console.log(await evaluate(19222, '127.0.0.1:5173', `(async()=>{
  const tile=document.querySelector('[data-participant-id=remote]'); tile?.querySelector('.stream-focus')?.click();
  const reports=await Promise.all((window.validationPeers||[]).filter(peer=>peer.connectionState==='connected').map(peer=>peer.getStats()));
  return JSON.stringify({status:document.querySelector('.call-stage-header strong')?.textContent,video:[...document.querySelectorAll('.call-video')].map(video=>({width:video.videoWidth,height:video.videoHeight})),stats:reports.flatMap(report=>[...report.values()].filter(item=>item.type==='inbound-rtp'&&item.kind==='video').map(item=>({width:item.frameWidth,height:item.frameHeight,fps:item.framesPerSecond,frames:item.framesDecoded}))),audio:window.reverseAudioProbe});
})()`));
console.log('QUALITY_TEST_ACTIVE: Android native capture and YouTube playback are available for user review.');
