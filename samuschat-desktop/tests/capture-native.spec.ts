import { test, expect } from '@playwright/test';

// Exercise Chromium's display-capture pipeline with its synthetic device.
// Unlike canvas overrides, this validates constraints on a display track.
test.use({ launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--auto-select-desktop-capture-source=Entire screen'] } });
test('captura nativa de teste aceita 900p/30 e ajuste ao piso de 750p sem deixar a fonte aberta', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const modulePath = '/src/models/screenShare.ts';
    const { captureScreen, constrainScreen, screenProfile } = await import(modulePath);
    const button = document.createElement('button'); button.id = 'native-capture-test'; button.textContent = 'Testar captura';
    button.onclick = async () => {
      let stream: MediaStream | undefined;
      try {
        stream = await captureScreen(screenProfile(900, 30));
        const track = stream.getVideoTracks()[0];
        await constrainScreen(track, screenProfile(900, 30));
        const initial = track.getSettings();
        await constrainScreen(track, screenProfile(750, 30));
        const floor = track.getSettings(); track.stop();
        button.dataset.result = JSON.stringify({ initial: { height: initial.height, fps: initial.frameRate, surface: initial.displaySurface }, floor: { height: floor.height, fps: floor.frameRate }, stopped: track.readyState });
      } catch (problem) { button.dataset.result = JSON.stringify({ error: String(problem) }); }
      finally { stream?.getTracks().forEach(track => track.stop()); }
    };
    document.body.append(button);
  });
  const button = page.locator('#native-capture-test'); await button.click();
  await expect(button).toHaveAttribute('data-result', /initial|error/);
  const result = JSON.parse((await button.getAttribute('data-result'))!);
  expect(result).not.toHaveProperty('error');
  expect(result.initial).toMatchObject({ height: 900, fps: 30, surface: 'monitor' });
  expect(result.floor).toEqual({ height: 750, fps: 30 });
  expect(result.stopped).toBe('ended');
});
