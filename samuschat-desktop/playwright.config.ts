import { defineConfig } from '@playwright/test';
const browserChannel = process.env.SAMUSCHAT_TEST_BROWSER_CHANNEL;
if (browserChannel && !['msedge', 'chrome'].includes(browserChannel)) throw new Error('Use msedge ou chrome como canal de teste.');
export default defineConfig({
  testDir: './tests', workers: 1, timeout: 60000,
  use: { channel: browserChannel, baseURL: 'http://127.0.0.1:5174', viewport: { width: 1440, height: 900 }, permissions: ['microphone'], trace: 'retain-on-failure',
    launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--autoplay-policy=no-user-gesture-required'] } },
  webServer: { command: 'npm run dev -- --port 5174 --strictPort', url: 'http://127.0.0.1:5174', reuseExistingServer: false,
    env: { SAMUSCHAT_API_URL: process.env.SAMUSCHAT_TEST_API_URL || 'http://127.0.0.1:18082' } },
});
