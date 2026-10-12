import { test, expect } from '@playwright/test';
import { constrainScreen, constrainSender, screenCaptureOptions, screenProfile, lowerScreenProfile, screenMetrics, screenUnderPressure, encoderSample } from '../src/models/screenShare';

test('PC solicita 900p, exige captura a 30 fps e mantém bitrate por conexão', async () => {
  let constraints: MediaTrackConstraints | undefined;
  let applied: RTCRtpSendParameters | undefined;
  const track = { contentHint: '', applyConstraints: async (value: MediaTrackConstraints) => { constraints = value; }, getSettings: () => ({ width: 1600, height: 900, frameRate: 30 }) } as unknown as MediaStreamTrack;
  const sender = { track, getParameters: () => ({ encodings: [{}], transactionId: 'transaction' }), setParameters: async (value: RTCRtpSendParameters) => { applied = value; } } as unknown as RTCRtpSender;
  expect(screenCaptureOptions()).toEqual({ video: { width: { ideal: 1600 }, height: { ideal: 900 }, frameRate: { ideal: 30 } }, audio: false });
  await constrainScreen(track); await constrainSender(sender);
  expect(track.contentHint).toBe('motion');
  expect(constraints).toEqual({ height: { exact: 900 }, frameRate: { exact: 30 } });
  expect(applied?.encodings[0]).toMatchObject({ maxBitrate: 4000000, maxFramerate: 30, scaleResolutionDownBy: 1 });
  expect(applied?.degradationPreference).toBe('maintain-framerate-and-resolution');
  expect(applied?.transactionId).toBe('transaction');
});

test('qualidades manuais e redução automática respeitam piso e não ampliam a escolha', () => {
  for (const [height, fps] of [[720, 30], [900, 15], [100, 60], [2160, 999]]) expect(() => screenProfile(height, fps)).toThrow('750p');
  let current = screenProfile(2160, 60);
  const sequence: string[] = [];
  while (true) {
    sequence.push(`${current.height}/${current.frameRate}`);
    expect(current.height).toBeGreaterThanOrEqual(750); expect(current.frameRate).toBeGreaterThanOrEqual(30);
    const next = lowerScreenProfile(current); if (!next) break; current = next;
  }
  expect(sequence).toEqual(['2160/60', '2160/30', '1440/30', '1080/30', '900/30', '750/30']);
  expect(lowerScreenProfile(screenProfile(750, 30))).toBeNull();
});

test('feedback mede somente codificação e não confunde tela estática com sobrecarga', () => {
  const metrics = screenMetrics({ timestamp: 3000, frames: 160, bytes: 210000, encodeSeconds: 1.8, height: 1080, reason: 'cpu', powerEfficient: false },
    { timestamp: 1000, frames: 100, bytes: 10000, encodeSeconds: 1 });
  expect(metrics.fps).toBe(30); expect(metrics.bitrate).toBe(800000);
  expect(metrics.encodeMs).toBeCloseTo(13.333, 2); expect(metrics.powerEfficient).toBe(false);
  expect(screenUnderPressure(metrics, screenProfile(1080, 60))).toBe(true);
  expect(screenUnderPressure({ reason: 'none', fps: 0 }, screenProfile(750))).toBe(false);
  expect(screenUnderPressure({ reason: 'bandwidth' }, screenProfile())).toBe(true);
  expect(screenMetrics({ timestamp: 500, frames: 1, bytes: 2, encodeSeconds: 0.01 }, { timestamp: 1000, frames: 40, bytes: 100, encodeSeconds: 1 }).encodeMs).toBeUndefined();
  const report = new Map([['video', { type: 'outbound-rtp', kind: 'video', timestamp: 3000, framesEncoded: 160, qualityLimitationReason: 'cpu' }], ['audio', { type: 'outbound-rtp', kind: 'audio', timestamp: 3000 }]]) as unknown as RTCStatsReport;
  expect(encoderSample(report)?.frames).toBe(160);
});

test('PC rejeita captura abaixo de 750p ou configurada abaixo de 30 fps', async () => {
  for (const settings of [{ height: 720, frameRate: 30 }, { height: 900, frameRate: 15 }, {}]) {
    const track = { applyConstraints: async () => {}, getSettings: () => settings } as unknown as MediaStreamTrack;
    await expect(constrainScreen(track)).rejects.toThrow('750p');
  }
});
