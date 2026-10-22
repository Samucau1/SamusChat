// Real existing accounts, one USB phone, normal desktop CDP test tab.
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {mediaEvaluate} from './media-session-cdp.mjs';
import {installAudioTiming} from './probe-audio-timing.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),serial=process.argv[2];
if(!/^[A-Za-z0-9_-]+$/.test(serial??''))throw Error('USB serial required');
const adb=(...a)=>execFileSync(path.join(root,'.android-sdk/platform-tools/adb.exe'),['-s',serial,...a],{encoding:'utf8',timeout:20000,stdio:['ignore','pipe','pipe']});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
function nodes(){adb('shell','uiautomator','dump','/sdcard/samus-room-usb.xml');const xml=adb('shell','cat','/sdcard/samus-room-usb.xml');return [...xml.matchAll(/<node\b([^>]+)>/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]])));}
async function tap(text){for(let i=0;i<10;i++){const n=nodes().find(n=>n.text===text||n['content-desc']===text);if(n){const b=n.bounds.match(/\d+/g).map(Number);adb('shell','input','tap',String((b[0]+b[2])>>1),String((b[1]+b[3])>>1));return;}if(i===4)adb('shell','input','swipe','500','1050','500','500','300');await delay(400);}throw Error('Android room control unavailable: '+text);}
async function pcClick(text){return mediaEvaluate(`(()=>{const b=[...document.querySelectorAll('button')].find(b=>b.getAttribute('aria-label')===${JSON.stringify(text)}||b.textContent.trim()===${JSON.stringify(text)}||b.title===${JSON.stringify(text)});if(!b)throw Error('PC room control unavailable');b.click();return true;})()`);}
const fixture=await mediaEvaluate(`(async()=>{const headers={Authorization:'Bearer '+sessionStorage.getItem('samuschat.desktop.session')};const servers=(await(await fetch('/api/servers',{headers})).json()).data;const server=servers.find(s=>s.channels.some(c=>c.type==='VOICE'));if(!server)throw Error('A shared voice server is required');return {name:server.name,channel:server.channels.find(c=>c.type==='VOICE')};})()`);
if(!process.argv.includes('--existing-room')) {
await pcClick(fixture.name);await pcClick('\u260e '+fixture.channel.name);
adb('shell','am','start','-n','com.samuschat/.MainActivity');await tap('Servidores');await tap('Servidor '+fixture.name);await tap(fixture.channel.name);await tap('Entrar na chamada');
for(let i=0;i<30;i++){if(await mediaEvaluate(`document.querySelector('.voice-room')?.textContent.includes('2/6 participantes')`))break;if(i===29)throw Error('Room did not connect');await delay(1000);}
await tap('Compartilhar tela do celular');await tap('Iniciar');
adb('shell','am','start','-a','android.intent.action.VIEW','-d','https://m.youtube.com/watch?v=aqz-KE-bpKQ','-p','com.android.chrome');adb('forward','tcp:19223','localabstract:chrome_devtools_remote');
}
for(let i=0;i<30;i++){try{if(await mediaEvaluate(`(async()=>{const v=document.querySelector('video');if(!v||v.readyState<2)return false;const p=document.getElementById('movie_player');p?.unMute?.();p?.setVolume?.(50);v.muted=false;v.volume=.5;await v.play();return !v.paused;})()`,19223,true))break;}catch{}if(i===29)throw Error('YouTube source unavailable');await delay(1000);}
await delay(3000);await mediaEvaluate(installAudioTiming);
for(let i=0;i<12;i++){
 await mediaEvaluate(`(async()=>{const v=document.querySelector('video'),p=document.getElementById('movie_player');p?.unMute?.();p?.setVolume?.(50);v.muted=false;v.volume=.5;await v.play();return true;})()`,19223,true);
 await delay(1000);
 if(await mediaEvaluate('window.reverseAudioProbe.rms>.001'))break;
 if(i===11)throw Error('Source audio remained silent before measurement');
}
await mediaEvaluate(installAudioTiming);
const stats=async()=>mediaEvaluate(`(async()=>{const reports=await Promise.all((window.validationPeers??[]).filter(p=>p.connectionState==='connected').map(p=>p.getStats()));const inbound=reports.flatMap(r=>[...r.values()].filter(s=>s.type==='inbound-rtp'));return {audio:window.audioTiming,signal:window.reverseAudioProbe,video:inbound.filter(s=>s.kind==='video').map(s=>({frames:s.framesDecoded,fps:s.framesPerSecond,width:s.frameWidth,height:s.frameHeight,freezes:s.freezeCount})),voiceBytes:inbound.filter(s=>s.kind==='audio').reduce((n,s)=>n+s.bytesReceived,0),rendered:[...document.querySelectorAll('.voice-room video.call-video')].map(v=>({time:v.currentTime,width:v.videoWidth,height:v.videoHeight}))};})()`);
const before=await stats();const samples=[];
for(let i=0;i<6;i++){await delay(10000);const source=await mediaEvaluate(`(()=>{const v=document.querySelector('video');return {time:v.currentTime,paused:v.paused,muted:v.muted};})()`,19223,true);const receiver=await stats();samples.push({source,receiver});assert.ok(!source.paused&&!source.muted,'Source interrupted');}
const after=samples.at(-1).receiver;
assert.ok(after.video[0]?.frames>before.video[0]?.frames+500,'Room video did not progress');
assert.ok(after.voiceBytes>before.voiceBytes,'Room voice RTP did not progress');
assert.ok(after.audio.starts>1000&&after.signal.rms>.001,'Room screen audio missing');
assert.ok(samples.at(-1).source.time-samples[0].source.time>45,'Source did not advance continuously');
assert.equal(after.video[0].freezes-before.video[0].freezes,0,'Video froze during room playback');
assert.equal(after.audio.gaps,0,'Screen audio scheduling gaps');assert.equal(after.audio.overlaps,0,'Screen audio overlapped');
const result={passed:true,atUtc:new Date().toISOString(),direction:'Android to PC',participants:2,seconds:60,before,samples};
fs.writeFileSync(path.join(root,'logs/beta-local-room-usb.json'),JSON.stringify(result,null,2)+'\n');
console.log({passed:true,participants:2,seconds:60,frames:after.video[0].frames-before.video[0].frames,audioStarts:after.audio.starts});
// Leave running for optional human listening; do not close the real browser.
