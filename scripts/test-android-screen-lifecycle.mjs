// Requires the authorized test call connected in the visible Edge session.
import {execFileSync} from 'node:child_process';
import {writeFileSync,mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
import {mediaEvaluate,mediaStats} from './media-session-cdp.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const serial=process.argv[2];assert.match(serial||'',/^[\w-]+$/);
const adb=(...args)=>execFileSync(path.join(root,'.android-sdk/platform-tools/adb.exe'),['-s',serial,...args],{encoding:'utf8',timeout:20000,stdio:['ignore','pipe','pipe']});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function tap(label) {
  for(let i=0;i<12;i++) {
    adb('shell','uiautomator','dump','/sdcard/media-lifecycle.xml');
    const xml=adb('shell','cat','/sdcard/media-lifecycle.xml');
    const node=[...xml.matchAll(/<node\b([^>]+)>/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]]))).find(n=>n.text===label||n['content-desc']===label);
    if(node){const b=node.bounds.match(/\d+/g).map(Number);adb('shell','input','tap',String((b[0]+b[2])>>1),String((b[1]+b[3])>>1));return;}
    await delay(500);
  }
  throw Error(`Missing media control: ${label}`);
}
const rotation=adb('shell','settings','get','system','user_rotation').trim();
const auto=adb('shell','settings','get','system','accelerometer_rotation').trim();
const evidence=[];
try {
  await mediaEvaluate("(()=>{document.querySelector('video')?.play().catch(()=>{});return true})()",19223);
  await delay(20000);
  assert.ok((await mediaEvaluate(mediaStats)).audio?.frames>500,'Warm up enough audio packets to expose a sequence reset');
  adb('shell','am','start','-n','com.samuschat/.MainActivity');
  await delay(500);
  for(let cycle=0;cycle<2;cycle++) {
    await tap('Parar transmissão'); await delay(2000);
    assert.doesNotMatch(adb('shell','dumpsys','media_projection'),/\(com\.samuschat,/,'Projection must be released');
    const stopped=await mediaEvaluate(mediaStats);await delay(1500);const settled=await mediaEvaluate(mediaStats);
    assert.equal(settled.video[0]?.frames,stopped.video[0]?.frames,'No continuing video frames after stop');
    await tap('Compartilhar tela do celular');await tap('Iniciar');await delay(3000);
    const restarted=await mediaEvaluate(mediaStats);
    assert.ok(restarted.video[0]?.frames>settled.video[0]?.frames,'Video must resume');
    await mediaEvaluate('(()=>{window.reverseAudioProbe={frames:0,rms:0};return true})()');
    adb('shell','am','start','-a','android.intent.action.VIEW','-d','https://m.youtube.com/watch?v=aqz-KE-bpKQ','-p','com.android.chrome');
    for(let i=0;i<20;i++) {
      try {if(await mediaEvaluate("(()=>{const v=document.querySelector('video');if(!v||v.readyState<2)return false;v.muted=false;v.volume=.5;v.play().catch(()=>{});return true})()",19223))break;}catch{}
      await delay(500);
    }
    let resumedAudio, source;
    for(let i=0;i<14;i++) {
      source=await mediaEvaluate("(()=>{const v=document.querySelector('video');if(v){v.muted=false;v.volume=.5;v.play().catch(()=>{});}return {paused:v?.paused,muted:v?.muted,time:v?.currentTime}})()",19223);
      await delay(500);resumedAudio=await mediaEvaluate(mediaStats);
      if(resumedAudio.audio?.frames>10 && resumedAudio.audio?.rms>.001)break;
    }
    evidence.push({cycle,stopped,restarted,resumedAudio,source});
    assert.ok(!source?.paused && !source?.muted,'Source must be playing and unmuted for an audio regression');
    assert.ok(resumedAudio.audio?.frames>10 && resumedAudio.audio?.rms>.001,'Audible decoded signal must resume on the same channel');
    adb('shell','am','start','-n','com.samuschat/.MainActivity');await delay(500);
  }
  adb('shell','settings','put','system','accelerometer_rotation','0');
  for(const [turn,landscape] of [['1',true],['0',false]]) {
    adb('shell','settings','put','system','user_rotation',turn);
    let stats;
    for(let i=0;i<15;i++){await delay(500);stats=await mediaEvaluate(mediaStats);const v=stats.video[0];if(v && (landscape?v.width>v.height:v.height>v.width))break;}
    assert.ok(stats.video[0] && (landscape?stats.video[0].width>stats.video[0].height:stats.video[0].height>stats.video[0].width),'Receiver follows display rotation');
    assert.ok(stats.video[0].fps<=25,'Device respects the requested 24fps limit within stats-window tolerance');evidence.push({rotation:turn,stats});
  }
  console.log(JSON.stringify({passed:true,evidence}));
} finally {
  for(const [key,value] of [['user_rotation',rotation],['accelerometer_rotation',auto]]) {
    if(value==='null')adb('shell','settings','delete','system',key);else adb('shell','settings','put','system',key,value);
  }
  mkdirSync(path.join(root,'logs/android-performance'),{recursive:true});
  writeFileSync(path.join(root,'logs/android-performance/lifecycle.json'),JSON.stringify(evidence,null,2));
}
