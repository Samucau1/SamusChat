// Scoped to the local SamusChat test page, or the explicitly opened YouTube test tab.
export async function mediaEvaluate(expression, port = 19222, youtube = false) {
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const target = targets.find(t => t.type === 'page' && (port === 19222 && !youtube ? t.url.startsWith('http://127.0.0.1:5173/') : t.url.includes('youtube.com/watch')));
  if (!target) throw Error('Media test page unavailable');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject) => {socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
  try {
    const response = new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP timeout')),20000);socket.addEventListener('message',event=>{const m=JSON.parse(event.data);if(m.id===1){clearTimeout(timer);resolve(m);}});});
    socket.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true,userGesture:true}}));
    const result = await response;
    if (result.result.exceptionDetails) throw Error(result.result.exceptionDetails.text);
    return result.result.result.value;
  } finally {socket.close();}
}
export const mediaStats = `(async()=>{
  const reports=await Promise.all((window.validationPeers||[]).filter(p=>p.connectionState==='connected').map(p=>p.getStats()));
  return {status:document.querySelector('.call-stage-header strong')?.textContent,
    rendered:[...document.querySelectorAll('[data-participant-id=remote] video')].map(v=>({width:v.videoWidth,height:v.videoHeight,time:v.currentTime,paused:v.paused,readyState:v.readyState,frames:v.getVideoPlaybackQuality().totalVideoFrames,dropped:v.getVideoPlaybackQuality().droppedVideoFrames})),
    video:reports.flatMap(r=>[...r.values()].filter(s=>s.type==='inbound-rtp'&&s.kind==='video').map(s=>({width:s.frameWidth,height:s.frameHeight,fps:s.framesPerSecond,frames:s.framesDecoded,dropped:s.framesDropped,bytes:s.bytesReceived,decoder:s.decoderImplementation,freezes:s.freezeCount,freezeSeconds:s.totalFreezesDuration}))),
    audio:window.reverseAudioProbe,timing:window.audioTiming};
})()`;
