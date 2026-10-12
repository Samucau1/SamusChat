import { test, expect } from '@playwright/test';
import { constrainScreen, constrainSender } from '../src/models/screenShare';

test('modo econômico limita captura, quadros, bitrate e resolução efetiva', async () => {
  let constraints: MediaTrackConstraints | undefined;
  let applied: RTCRtpSendParameters | undefined;
  const track = { contentHint: '', applyConstraints: async (value: MediaTrackConstraints) => { constraints = value; }, getSettings: () => ({ width: 1920, height: 1080 }) } as unknown as MediaStreamTrack;
  const sender = { track, getParameters: () => ({ encodings: [{}], transactionId: 'transaction' }), setParameters: async (value: RTCRtpSendParameters) => { applied = value; } } as unknown as RTCRtpSender;
  await constrainScreen(track, 'economy'); await constrainSender(sender, 'economy');
  expect(track.contentHint).toBe('detail');
  expect(constraints).toEqual({ width: { max: 960 }, height: { max: 540 }, frameRate: { max: 10 } });
  expect(applied?.encodings[0]).toMatchObject({ maxBitrate: 700000, maxFramerate: 10, scaleResolutionDownBy: 2 });
  expect(applied?.transactionId).toBe('transaction');
  await constrainSender(sender, 'balanced'); expect(applied?.encodings[0].maxBitrate).toBe(1500000);
});
