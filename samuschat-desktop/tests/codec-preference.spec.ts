import {test,expect} from '@playwright/test';

test('inconclusive hardware probe prefers H264 in real SDP without removing VP8 fallback',async({page})=>{
  await page.goto('/');
  const result=await page.evaluate(async()=>{
    const {preferEfficientEncoder}=await import('/src/models/screenShare.ts');
    const a=new RTCPeerConnection(),b=new RTCPeerConnection();
    const canvas=document.createElement('canvas');canvas.width=320;canvas.height=180;
    const stream=canvas.captureStream(1);const sender=a.addTrack(stream.getVideoTracks()[0],stream);
    try {
      preferEfficientEncoder(a,sender,null);
      await a.setLocalDescription(await a.createOffer());
      await b.setRemoteDescription(a.localDescription!);await b.setLocalDescription(await b.createAnswer());
      await a.setRemoteDescription(b.localDescription!);
      const sdp=a.localDescription!.sdp;
      const payload=sdp.split('\r\n').find(l=>l.startsWith('m=video '))!.split(' ')[3];
      return {firstIsH264:sdp.includes(`a=rtpmap:${payload} H264/90000`),vp8Retained:/a=rtpmap:\d+ VP8\/90000/.test(sdp),state:a.signalingState};
    }finally{a.close();b.close();stream.getTracks().forEach(t=>t.stop());}
  });
  expect(result).toEqual({firstIsH264:true,vp8Retained:true,state:'stable'});
});
