import {test, expect} from '@playwright/test';

test('real WebRTC answer can be delivered concurrently and repeated after stable', async ({page}) => {
  await page.goto('/');
  const result = await page.evaluate(async()=>{
    const {applyRemoteAnswer}=await import('/src/models/remoteAnswer.ts');
    const a=new RTCPeerConnection(),b=new RTCPeerConnection();
    a.createDataChannel('test');
    let applies=0;
    try {
      await a.setLocalDescription(await a.createOffer());await b.setRemoteDescription(a.localDescription!);
      await b.setLocalDescription(await b.createAnswer());
      const original=a.setRemoteDescription.bind(a);
      a.setRemoteDescription=async description=>{applies++;await new Promise(r=>setTimeout(r,50));await original(description);};
      const sdp=b.localDescription!.sdp;
      await Promise.all([applyRemoteAnswer(a,sdp),applyRemoteAnswer(a,sdp),applyRemoteAnswer(a,sdp)]);
      await applyRemoteAnswer(a,sdp);
      let invalid=false;try{await applyRemoteAnswer(a,sdp+'\r\n');}catch{invalid=true;}
      return {applies,state:a.signalingState,invalid};
    } finally {a.close();b.close();}
  });
  expect(result).toEqual({applies:1,state:'stable',invalid:true});
});

test('an answer without a local offer is rejected before touching native SDP',async({page})=>{
  await page.goto('/');
  const rejected=await page.evaluate(async()=>{
    const {applyRemoteAnswer}=await import('/src/models/remoteAnswer.ts');const p=new RTCPeerConnection();
    let applied=false;p.setRemoteDescription=async()=>{applied=true;};
    try {await applyRemoteAnswer(p,'invalid');return false;}catch{return !applied;}finally{p.close();}
  });
  expect(rejected).toBe(true);
});
