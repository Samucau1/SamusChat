import {test,expect} from '@playwright/test';
import {configureScreenReceiver} from '../src/models/screenReceiver';

test('receiving screen video uses the browser jitter target API',async({page})=>{
  await page.goto('/');
  const result=await page.evaluate(async()=>{
    const {configureScreenReceiver}=await import('/src/models/screenReceiver.ts');const p=new RTCPeerConnection();
    try {const r=p.addTransceiver('video').receiver;const supported='jitterBufferTarget' in r;return {supported,applied:configureScreenReceiver(r),target:(r as any).jitterBufferTarget};}finally{p.close();}
  });
  expect(result.supported).toBe(true);expect(result.applied).toBe(true);expect(result.target).toBe(320);
});
test('voice and unsupported/rejected settings preserve native behavior',()=>{
  const voice={track:{kind:'audio'},jitterBufferTarget:null};
  expect(configureScreenReceiver(voice as unknown as RTCRtpReceiver)).toBe(false);expect(voice.jitterBufferTarget).toBeNull();
  expect(configureScreenReceiver({track:{kind:'video'}} as RTCRtpReceiver)).toBe(false);
  const rejected={track:{kind:'video'},set jitterBufferTarget(_:number){throw new Error('unsupported');}};
  expect(configureScreenReceiver(rejected as unknown as RTCRtpReceiver)).toBe(false);
});
