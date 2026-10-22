import {test,expect} from '@playwright/test';
const seconds=Number(process.env.SAMUSCHAT_SOAK_SECONDS??30);
if(!Number.isInteger(seconds)||seconds<30||seconds>1800)throw Error('Soak duration must be 30..1800 seconds');
test('three participants sustain room voice and screen audio, then release capture',async({browser,request},info)=>{
 test.setTimeout((seconds+120)*1000);
 const password='DisposableRoomSoak42!';
 const contexts=[],pages=[],accounts=[];
 const evidence:unknown[]=[];
 try{
  for(let i=0;i<3;i++){
   const username=`soak${i}_${crypto.randomUUID().slice(0,8)}`,email=username+'@example.com';
   const r=await request.post('/api/auth/register',{data:{username,email,password}});expect(r.ok()).toBe(true);
   accounts.push({username,email,token:(await r.json()).data.token});
   const context=await browser.newContext({permissions:['microphone']});contexts.push(context);
   await context.addInitScript(()=>{
    const state={peers:[] as RTCPeerConnection[],rms:0,audioChunks:0,screen:null as MediaStream|null};
    Object.assign(window,{soakState:state});
    const Native=RTCPeerConnection;
    window.RTCPeerConnection=new Proxy(Native,{construct(target,args){const peer=new target(...args as [RTCConfiguration?]);state.peers.push(peer);return peer;}});
    const copy=AudioBuffer.prototype.copyToChannel;
    AudioBuffer.prototype.copyToChannel=function(data,channel,offset){
     if(this.sampleRate===48000){let power=0;for(const x of data)power+=x*x;state.rms=Math.max(state.rms,Math.sqrt(power/data.length));state.audioChunks++;}
     return copy.call(this,data,channel,offset);
    };
    Object.defineProperty(navigator.mediaDevices,'getDisplayMedia',{value:async()=>{
     const canvas=document.createElement('canvas');canvas.width=1600;canvas.height=900;
     const ctx=canvas.getContext('2d')!;let n=0;
     const timer=setInterval(()=>{ctx.fillStyle=`hsl(${n++%360} 60% 40%)`;ctx.fillRect(0,0,1600,900);ctx.fillStyle='white';ctx.fillText(String(n),30,30);},33);
     const stream=canvas.captureStream(30),audio=new AudioContext({sampleRate:48000}),destination=audio.createMediaStreamDestination(),tone=audio.createOscillator();
     tone.frequency.value=440;tone.connect(destination);tone.start();await audio.resume();stream.addTrack(destination.stream.getAudioTracks()[0]);
     const track=stream.getVideoTracks()[0],stop=track.stop.bind(track);
     track.stop=()=>{clearInterval(timer);tone.stop();void audio.close();stop();};state.screen=stream;return stream;
    }});
   });
   const page=await context.newPage();pages.push(page);
  }
  const owner={Authorization:`Bearer ${accounts[0].token}`};
  const r=await request.post('/api/servers',{headers:owner,data:{name:'Soak '+crypto.randomUUID().slice(0,6)}});expect(r.ok()).toBe(true);const server=(await r.json()).data;
  for(const a of accounts.slice(1))expect((await request.post(`/api/servers/${server.id}/join`,{headers:{Authorization:`Bearer ${a.token}`}})).ok()).toBe(true);
  expect((await request.post(`/api/servers/${server.id}/channels`,{headers:owner,data:{name:'soak-voice',type:'VOICE'}})).ok()).toBe(true);
  for(let i=0;i<3;i++){
   const page=pages[i];await page.goto('/');await page.getByLabel('Email',{exact:true}).fill(accounts[i].email);await page.getByLabel('Senha',{exact:true}).fill(password);await page.getByRole('button',{name:'Entrar',exact:true}).click();
   await page.getByRole('button',{name:server.name,exact:true}).click();await page.getByRole('button',{name:/soak-voice/}).click();
  }
  for(const page of pages)await expect(page.locator('.voice-room')).toContainText('3/6 participantes',{timeout:30000});
  await pages[0].getByRole('button',{name:'Compartilhar tela no canal',exact:true}).click();
  for(const page of pages.slice(1))await expect.poll(()=>page.locator('.voice-room video.call-video').evaluateAll(v=>v.some(e=>(e as HTMLVideoElement).videoWidth>0))).toBe(true);
  const sample=async()=>Promise.all(pages.map(page=>page.evaluate(async()=>{
   const state=(window as unknown as {soakState:{peers:RTCPeerConnection[];rms:number;audioChunks:number}}).soakState;
   const reports=await Promise.all(state.peers.filter(p=>p.connectionState==='connected').map(p=>p.getStats()));
   const inbound=reports.flatMap(r=>[...r.values()].filter(s=>s.type==='inbound-rtp'));
   return {rms:state.rms,chunks:state.audioChunks,peers:reports.length,audioBytes:inbound.filter(s=>s.kind==='audio').reduce((n,s)=>n+s.bytesReceived,0),videoFrames:inbound.filter(s=>s.kind==='video').reduce((n,s)=>n+(s.framesDecoded??0),0)};
  })));
  let previous=await sample();
  for(let elapsed=0;elapsed<seconds;elapsed+=10){
   await pages[0].waitForTimeout(10000);const next=await sample();evidence.push({elapsed:elapsed+10,clients:next});
   for(let i=0;i<3;i++){expect(next[i].peers).toBe(2);expect(next[i].audioBytes).toBeGreaterThan(previous[i].audioBytes);if(i>0){expect(next[i].videoFrames).toBeGreaterThan(previous[i].videoFrames);expect(next[i].chunks).toBeGreaterThan(previous[i].chunks);expect(next[i].rms).toBeGreaterThan(.001);}}
   previous=next;
  }
  await pages[0].getByRole('button',{name:/Parar transmiss/}).click();
  expect(await pages[0].evaluate(()=>(window as unknown as {soakState:{screen:MediaStream}}).soakState.screen.getTracks().every(t=>t.readyState==='ended'))).toBe(true);
  for(const page of pages)await page.getByRole('button',{name:'Sair do canal de chamada',exact:true}).click();
  for(const page of pages)await expect(page.locator('.voice-room')).toHaveCount(0);
 }finally{
  await info.attach('room-soak-evidence',{body:JSON.stringify({seconds,evidence},null,2),contentType:'application/json'});
  for(const context of contexts)await context.close();
 }
});
