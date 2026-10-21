// Real public YouTube playback probe. Requires the existing authorized USB call.
import {mediaEvaluate,mediaStats} from './media-session-cdp.mjs';
import {mkdirSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const count=Number(process.argv[2]??20);assert.ok(Number.isInteger(count)&&count>=20&&count<=180);
const label=process.argv[3]??'24fps';assert.match(label,/^[\w-]+$/);
const delay=ms=>new Promise(r=>setTimeout(r,ms));
for(let i=0;i<30;i++) {
  try { if(await mediaEvaluate("(()=>{const v=document.querySelector('video');if(!v||v.readyState<2)return false;v.muted=false;v.volume=.5;v.play().catch(()=>{});return true})()",19223))break; } catch {}
  await delay(1000);
}
const evidence=[];
let frozen=0,maxFrozen=0;
await delay(3000); // Exclude initial decoder startup from the stability interval.
for(let i=0;i<count;i++) {
  await delay(1000);
  const receiver=await mediaEvaluate(mediaStats);
  const source=await mediaEvaluate("(()=>{const v=document.querySelector('video'),q=v?.getVideoPlaybackQuality();return {paused:v?.paused,time:v?.currentTime,ready:v?.readyState,width:v?.videoWidth,height:v?.videoHeight,presented:q?.totalVideoFrames,dropped:q?.droppedVideoFrames}})()",19223);
  const sample={at:Date.now(),receiver,source};
  const previous=evidence.at(-1);
  if(previous && !source.paused && source.time>previous.source.time+0.3) {
    const now=receiver.rendered[0],before=previous.receiver.rendered[0];
    frozen=now && before && now.frames>before.frames && now.time>before.time ? 0 : frozen+1;
    maxFrozen=Math.max(maxFrozen,frozen);
  } else frozen=0;
  evidence.push(sample);
}
mkdirSync('logs/android-performance',{recursive:true});
writeFileSync(`logs/android-performance/playback-${label}-${count}s.json`,JSON.stringify(evidence,null,2));
const first=evidence[0],last=evidence.at(-1);
const result={sourceAdvanced:last.source.time-first.source.time,sourcePausedSamples:evidence.filter(s=>s.source.paused).length,decodedFrames:(last.receiver.video[0]?.frames||0)-(first.receiver.video[0]?.frames||0),renderedFrames:(last.receiver.rendered[0]?.frames||0)-(first.receiver.rendered[0]?.frames||0),maxFrozenSamples:maxFrozen,audio:last.receiver.audio,timing:last.receiver.timing,newFreezes:(last.receiver.video[0]?.freezes||0)-(first.receiver.video[0]?.freezes||0)};
console.log(JSON.stringify(result));
assert.ok(result.sourceAdvanced>count*.85&&result.sourcePausedSamples===0,'Motion test invalid: YouTube source did not play continuously');
assert.ok(result.decodedFrames>100,'Receiver must decode moving screen video');
assert.ok(result.renderedFrames>100,'Video element must display moving screen video');
assert.ok(maxFrozen<3,'Receiver froze while the source continued for three samples');
assert.equal(result.newFreezes,0,'WebRTC detected short video freezes during the stability interval');
assert.ok(result.audio?.rms>0.001,'Screen audio must contain a decoded signal');
assert.equal(result.timing?.overlaps,0,'Audio scheduling must not overlap chunks');
assert.equal(result.timing?.gaps,0,'Audio scheduling underruns require investigation');
console.log('PASS: Android to PC real video, rendered progress and decoded audio signal');
