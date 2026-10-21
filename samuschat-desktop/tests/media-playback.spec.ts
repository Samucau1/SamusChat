import { test, expect } from '@playwright/test';

test('voz continua sem quadros de vídeo, durante tela cheia e após parar a tela', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const reactPath = '/node_modules/.vite/deps/react.js', domPath = '/node_modules/.vite/deps/react-dom_client.js', panelPath = '/src/views/CallStage.tsx';
    const react = await import(reactPath), dom = await import(domPath), { DirectCallPanel } = await import(panelPath);
    const React = react.default ?? react, { createRoot } = dom.default ?? dom;
    const container = document.createElement('div'); container.style.cssText = 'position:fixed;inset:0;z-index:10000;background:#111'; document.body.append(container);
    const context = new AudioContext(), destination = context.createMediaStreamDestination(), tone = context.createOscillator();
    const volume = context.createGain(); volume.gain.value = 0; tone.frequency.value = 440; tone.connect(volume); volume.connect(destination); tone.start(); await context.resume();
    // A negotiated Android screen track can be live without having sent a frame.
    const Generator = (window as any).MediaStreamTrackGenerator;
    if (!Generator) throw new Error('This regression requires Chromium video track generation.');
    const track = new Generator({ kind: 'video' }), writer = track.writable.getWriter();
    const stream = new MediaStream([...destination.stream.getAudioTracks(), track]);
    const calls = { call: { caller: 'pc', callee: 'android', state: 'ACTIVE' }, status: 'Em chamada', remote: stream, remoteSharing: false,
      microphoneStream: destination.stream, preview: null, sharing: false, muted: false, busy: false, screenStarting: false, feedback: null, toggleMute() {}, share() {}, end() {}, setPreviewVisible() {} };
    const root = createRoot(container), props = { calls, contacts: [{ email: 'android', username: 'Android', avatarUrl: '/uploads/appearance/00000000-0000-0000-0000-000000000000.png' }], email: 'pc', name: 'PC', preferences: { profile: { width: 1600, height: 900, frameRate: 30 }, automatic: false } };
    const render = () => root.render(React.createElement(DirectCallPanel, props)); render();
    let timer: ReturnType<typeof setInterval> | undefined, analyser: AnalyserNode | undefined;
    const monitor = new AudioContext();
    (window as any).mediaProbe = {
      mute: (muted: boolean) => { calls.muted = muted; render(); },
      voice: (active: boolean) => { volume.gain.value = active ? 1 : 0; },
      measure: async () => {
        if (!analyser) {
          const audio = container.querySelector('[data-participant-id=remote] audio') as HTMLAudioElement;
          const played = (audio as any).captureStream() as MediaStream;
          if (!played.getAudioTracks().length) return 0;
          const source = monitor.createMediaStreamSource(played); analyser = monitor.createAnalyser(); source.connect(analyser);
        }
        await monitor.resume(); const values = new Float32Array(analyser.fftSize); analyser.getFloatTimeDomainData(values);
        return Math.sqrt(values.reduce((sum, value) => sum + value * value, 0) / values.length);
      },
      startVideo: () => {
        calls.remoteSharing = true; render();
        const canvas = document.createElement('canvas'); canvas.width = 960; canvas.height = 750; const paint = canvas.getContext('2d')!;
        timer = setInterval(() => {
          paint.fillStyle = '#2468bc'; paint.fillRect(0, 0, 960, 750);
          const frame = new VideoFrame(canvas, { timestamp: Math.round(performance.now() * 1000) });
          void writer.write(frame).finally(() => frame.close()).catch(() => {});
        }, 33);
      },
      stopVideo: () => { if (timer) clearInterval(timer); calls.remoteSharing = false; render(); },
      close: async () => { if (timer) clearInterval(timer); root.unmount(); tone.stop(); track.stop(); await writer.abort(); await monitor.close(); await context.close(); container.remove(); }
    };
  });
  const audio = page.locator('[data-participant-id=remote] audio');
  try {
    await expect(audio).toHaveCount(1);
    await expect.poll(() => audio.evaluate((element: HTMLAudioElement) => (element.srcObject as MediaStream | null)?.getVideoTracks().length ?? -1)).toBe(0);
    const remoteTile = page.locator('[data-participant-id=remote]');
    await expect(remoteTile).not.toHaveClass(/speaking/);
    await expect(remoteTile.locator('.participant-photo img')).toHaveAttribute('src', /uploads\/appearance/);
    await page.evaluate(() => (window as any).mediaProbe.voice(true));
    await expect(remoteTile).toHaveClass(/speaking/);
    await expect.poll(() => page.evaluate(() => (window as any).mediaProbe.measure())).toBeGreaterThan(0.05);
    const localTile = page.locator('[data-participant-id=self]');
    await expect(localTile).toHaveClass(/speaking/);
    await page.evaluate(() => (window as any).mediaProbe.mute(true));
    await expect(localTile).not.toHaveClass(/speaking/);
    await expect(remoteTile).toHaveClass(/speaking/);
    const firstTime = await audio.evaluate((element: HTMLAudioElement) => element.currentTime);
    await page.evaluate(() => (window as any).mediaProbe.startVideo());
    const video = page.locator('[data-participant-id=remote] video');
    await expect.poll(() => video.evaluate((element: HTMLVideoElement) => element.videoHeight)).toBe(750);
    expect(await video.evaluate((element: HTMLVideoElement) => element.muted)).toBe(true);
    await video.click(); await page.getByRole('button', { name: 'Tela cheia da transmissão de Android', exact: true }).click();
    await expect.poll(() => page.evaluate(() => !!document.fullscreenElement)).toBe(true);
    await expect.poll(() => page.evaluate(() => (window as any).mediaProbe.measure())).toBeGreaterThan(0.05);
    await expect.poll(() => audio.evaluate((element: HTMLAudioElement) => element.currentTime)).toBeGreaterThan(firstTime);
    await page.evaluate(() => (window as any).mediaProbe.stopVideo());
    await expect(video).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => (window as any).mediaProbe.measure())).toBeGreaterThan(0.05);
    await page.evaluate(() => (window as any).mediaProbe.voice(false));
    await expect(remoteTile).not.toHaveClass(/speaking/);
  } finally { await page.evaluate(() => (window as any).mediaProbe.close()); }
});
