import { test, expect } from '@playwright/test';
import { constrainScreen, constrainSender, screenCaptureOptions } from '../src/models/screenShare';

test('PC solicita 900p, exige captura a 30 fps e mantém bitrate por conexão', async () => {
  let constraints: MediaTrackConstraints | undefined;
  let applied: RTCRtpSendParameters | undefined;
  const track = { contentHint: '', applyConstraints: async (value: MediaTrackConstraints) => { constraints = value; }, getSettings: () => ({ width: 1600, height: 900, frameRate: 30 }) } as unknown as MediaStreamTrack;
  const sender = { track, getParameters: () => ({ encodings: [{}], transactionId: 'transaction' }), setParameters: async (value: RTCRtpSendParameters) => { applied = value; } } as unknown as RTCRtpSender;
  expect(screenCaptureOptions()).toEqual({ video: { width: { ideal: 1600 }, height: { ideal: 900 }, frameRate: { ideal: 30 } }, audio: false });
  await constrainScreen(track); await constrainSender(sender);
  expect(track.contentHint).toBe('motion');
  expect(constraints).toEqual({ width: { ideal: 1600, max: 1600 }, height: { min: 750, ideal: 900, max: 900 }, frameRate: { exact: 30 } });
  expect(applied?.encodings[0]).toMatchObject({ maxBitrate: 4000000, maxFramerate: 30, scaleResolutionDownBy: 1 });
  expect(applied?.degradationPreference).toBe('maintain-framerate-and-resolution');
  expect(applied?.transactionId).toBe('transaction');
});

test('PC rejeita captura abaixo de 750p ou configurada abaixo de 30 fps', async () => {
  for (const settings of [{ height: 720, frameRate: 30 }, { height: 900, frameRate: 15 }, {}]) {
    const track = { applyConstraints: async () => {}, getSettings: () => settings } as unknown as MediaStreamTrack;
    await expect(constrainScreen(track)).rejects.toThrow('750p');
  }
});
