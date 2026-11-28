import { test, expect, type Page } from '@playwright/test';

async function connectCall(page: Page, automatic = false) {
  await page.context().grantPermissions(['microphone', 'camera']);
  await page.goto('/');
  await page.evaluate(async automatic => {
    const reactPath = '/node_modules/.vite/deps/react.js';
    const domPath = '/node_modules/.vite/deps/react-dom_client.js';
    const controllerPath = '/src/controllers/useCallController.ts';
    const panelPath = '/src/views/CallStage.tsx';
    const profilePath = '/src/models/screenShare.ts';
    const react = await import(reactPath), dom = await import(domPath);
    const { useCallController } = await import(controllerPath);
    const { DirectCallPanel } = await import(panelPath);
    const { screenProfile } = await import(profilePath);
    const React = react.default ?? react;
    const { createRoot } = dom.default ?? dom;
    const probe = (window as any).cameraProbe = { controllers: {}, errors: [] };
    let session: any = null;
    probe.peers = [];
    window.RTCPeerConnection = class extends RTCPeerConnection {
      constructor(config?: RTCConfiguration) { super(config); probe.peers.push(this); }
    };
    const api = {
      ice: async () => { probe.iceRequests = (probe.iceRequests ?? 0) + 1; return []; },
      calls: async () => session ? [session] : [],
      call: async () => { if (probe.failPoll) { probe.failPoll = false; throw new Error("offline"); } return session; },
      invite: async () => session = { id: 'camera-test', caller: 'pc', callee: 'mobile', channelId: null, state: 'RINGING' },
      callAction: async (_id: string, action: string, sdp?: string) => {
        session = { ...session };
        if (action === 'accept') session.state = 'CONNECTING';
        if (action === 'restart' && session.answer === sdp) { session.offer = null; session.answer = null; }
        if (action === 'answer') session.state = 'ACTIVE';
        if (action === 'offer' || action === 'answer') session[action] = sdp;
        if (action === 'end') session.state = 'ENDED';
        return session;
      },
    };
    const preferences = { profile: screenProfile(750, 30), automatic, hardware: null, choose() {} };
    const container = document.createElement('div');
    container.style.cssText = 'position:fixed;inset:0;z-index:10000;background:#111;overflow:auto';
    document.body.append(container);
    function Client({ email }: { email: string }) {
      const calls = useCallController(api, email, (error: Error) => probe.errors.push(error.message), preferences);
      probe.controllers[email] = calls;
      return React.createElement('div', { 'data-client': email }, React.createElement(DirectCallPanel, {
        calls, preferences, email, name: email, contacts: [],
      }));
    }
    const root = createRoot(container);
    root.render(React.createElement(React.Fragment, null,
      React.createElement(Client, { email: 'pc' }), React.createElement(Client, { email: 'mobile' })));
    probe.close = () => { root.unmount(); container.remove(); };
  }, automatic);
  await expect.poll(() => page.evaluate(() => !!(window as any).cameraProbe.controllers.pc)).toBe(true);
  await page.evaluate(() => (window as any).cameraProbe.controllers.pc.invite('mobile'));
  await expect(page.locator('[data-client=mobile]').getByRole('button', { name: 'Aceitar', exact: true })).toBeVisible();
  await page.locator('[data-client=mobile]').getByRole('button', { name: 'Aceitar', exact: true }).click();
  await expect.poll(() => page.evaluate(() => Object.values((window as any).cameraProbe.controllers)
    .every((controller: any) => controller.status === 'Em chamada')), { timeout: 20000 }).toBe(true);
}

test('renova ICE e retoma chamada direta com o mesmo microfone', async ({ page }) => {
  await connectCall(page);
  await page.evaluate(() => {
    const probe = (window as any).cameraProbe;
    probe.failPoll = true;
    probe.initialTrack = probe.controllers.pc.microphoneStream.getAudioTracks()[0];
    probe.initialOffer = probe.controllers.pc.call.offer;
    probe.controllers.pc.toggleMute();
    const peer = probe.peers[0];
    Object.defineProperty(peer, 'connectionState', { configurable: true, value: 'failed' });
    peer.dispatchEvent(new Event('connectionstatechange'));
    delete peer.connectionState;
  });
  await expect.poll(() => page.evaluate(() => {
    const probe = (window as any).cameraProbe, pc = probe.controllers.pc;
    return pc.call?.offer && pc.call.offer !== probe.initialOffer && pc.call.answer &&
      pc.status === 'Em chamada' && pc.muted && probe.iceRequests >= 4 &&
      pc.microphoneStream.getAudioTracks()[0] === probe.initialTrack;
  }), { timeout: 35000 }).toBe(true);
});

test('protege voz com perda sustentada e restaura o bitrate após recuperação', async ({ page }) => {
  test.setTimeout(90000);
  await connectCall(page, true);
  await page.evaluate(() => {
    const probe = (window as any).cameraProbe;
    const pc = probe.peers[0] as RTCPeerConnection;
    const video = pc.getSenders().find(sender => sender.track?.kind === 'video')!;
    probe.bitrate = () => video.getParameters().encodings[0].maxBitrate;
    probe.loss = 0.08;
    Object.defineProperty(pc, 'getStats', { value: async () => new Map([['audio', {
      type: 'remote-inbound-rtp', kind: 'audio', timestamp: performance.now(), fractionLost: probe.loss, jitter: 0.005
    }]]) });
    const canvas = document.createElement('canvas'); canvas.width = 1334; canvas.height = 750;
    canvas.getContext('2d')!.fillRect(0, 0, 1334, 750);
    navigator.mediaDevices.getDisplayMedia = async () => canvas.captureStream(30);
  });
  await page.locator('[data-client=pc]').getByRole('button', { name: 'Compartilhar tela', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.bitrate()), { timeout: 16000 }).toBe(1250000);
  await page.evaluate(() => { (window as any).cameraProbe.loss = 0; });
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.bitrate()), { timeout: 45000 }).toBe(2500000);
});
test.afterEach(async ({ page }) => {
  await page.evaluate(() => (window as any).cameraProbe?.close());
});

test('câmera bidirecional usa a chamada existente e libera a captura ao desligar e encerrar', async ({ page }) => {
  await connectCall(page);
  const pc = page.locator('[data-client=pc]'), mobile = page.locator('[data-client=mobile]');
  await pc.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.controllers.mobile.remoteCamera)).toBe(true);
  await expect.poll(() => mobile.locator('[data-participant-id=remote] video')
    .evaluate((video: HTMLVideoElement) => video.videoWidth)).toBeGreaterThan(0);
  await expect(pc.getByRole('button', { name: 'Compartilhar tela', exact: true })).toBeDisabled();
  await page.evaluate(() => {
    const probe = (window as any).cameraProbe;
    probe.firstTrack = probe.controllers.pc.cameraPreview.getVideoTracks()[0];
  });
  await mobile.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect.poll(() => pc.locator('[data-participant-id=remote] video')
    .evaluate((video: HTMLVideoElement) => video.videoWidth)).toBeGreaterThan(0);
  await pc.getByRole('button', { name: 'Desligar câmera', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.firstTrack.readyState)).toBe('ended');
  await expect(mobile.locator('[data-participant-id=remote] video')).toHaveCount(0);
  await expect(pc.locator('[data-participant-id=remote] video')).toHaveCount(1);
  await expect(pc.getByRole('button', { name: 'Compartilhar tela', exact: true })).toBeEnabled();
  await pc.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect(pc.getByRole('button', { name: 'Desligar câmera', exact: true })).toBeVisible();
  await page.evaluate(() => {
    const probe = (window as any).cameraProbe;
    probe.tracks = Object.values(probe.controllers).flatMap((controller: any) =>
      [...controller.cameraPreview.getTracks(), ...controller.microphoneStream.getTracks()]);
  });
  await pc.getByRole('button', { name: 'Encerrar chamada', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.tracks.every((track: MediaStreamTrack) => track.readyState === 'ended'))).toBe(true);
  expect(await page.evaluate(() => (window as any).cameraProbe.errors)).toEqual([]);
});

test('permissão negada preserva a voz e permite tentar a câmera novamente', async ({ page }) => {
  await connectCall(page);
  await page.evaluate(() => {
    const probe = (window as any).cameraProbe;
    probe.capture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getUserMedia = async constraints => {
      if (constraints?.video) throw new DOMException('denied', 'NotAllowedError');
      return probe.capture(constraints);
    };
  });
  const pc = page.locator('[data-client=pc]');
  await pc.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.errors)).toEqual(['Permita o acesso à câmera para ativar o vídeo.']);
  expect(await page.evaluate(() => {
    const controller = (window as any).cameraProbe.controllers.pc;
    return { status: controller.status, camera: controller.cameraEnabled, mic: controller.microphoneStream.getAudioTracks()[0].readyState };
  })).toEqual({ status: 'Em chamada', camera: false, mic: 'live' });
  await page.evaluate(() => { navigator.mediaDevices.getUserMedia = (window as any).cameraProbe.capture; });
  await pc.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect(pc.getByRole('button', { name: 'Desligar câmera', exact: true })).toBeVisible();
});

test('encerrar enquanto a permissão está pendente descarta a câmera liberada depois', async ({ page }) => {
  await connectCall(page);
  await page.evaluate(() => {
    const probe = (window as any).cameraProbe;
    const capture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    const permission = new Promise<void>(resolve => { probe.release = resolve; });
    navigator.mediaDevices.getUserMedia = async constraints => {
      if (!constraints?.video) return capture(constraints);
      probe.requested = true;
      await permission;
      const stream = await capture(constraints); probe.delayedTrack = stream.getVideoTracks()[0];
      return stream;
    };
  });
  const pc = page.locator('[data-client=pc]');
  await pc.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.requested)).toBe(true);
  await pc.getByRole('button', { name: 'Encerrar chamada', exact: true }).click();
  await page.evaluate(() => (window as any).cameraProbe.release());
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.delayedTrack?.readyState)).toBe('ended');
  expect(await page.evaluate(() => (window as any).cameraProbe.controllers.pc.cameraEnabled)).toBe(false);
});

test('alternar tela e câmera mantém a conexão e atualiza o tipo de vídeo remoto', async ({ page }) => {
  await connectCall(page);
  await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 1334; canvas.height = 750;
    const paint = canvas.getContext('2d')!; paint.fillStyle = '#2468bc'; paint.fillRect(0, 0, canvas.width, canvas.height);
    const probe = (window as any).cameraProbe;
    navigator.mediaDevices.getDisplayMedia = async () => {
      probe.screenStream = canvas.captureStream(30);
      return probe.screenStream;
    };
  });
  const pc = page.locator('[data-client=pc]');
  await pc.getByRole('button', { name: 'Compartilhar tela', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.controllers.mobile.remoteSharing)).toBe(true);
  await expect(pc.getByRole('button', { name: 'Ligar câmera', exact: true })).toBeDisabled();
  await pc.getByRole('button', { name: 'Parar transmissão', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.controllers.mobile.remoteSharing)).toBe(false);
  await pc.getByRole('button', { name: 'Ligar câmera', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.controllers.mobile.remoteCamera)).toBe(true);
  await page.evaluate(() => {
    const track = (window as any).cameraProbe.controllers.pc.cameraPreview.getVideoTracks()[0];
    track.stop(); track.dispatchEvent(new Event('ended'));
  });
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.controllers.mobile.remoteCamera)).toBe(false);
  await pc.getByRole('button', { name: 'Compartilhar tela', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as any).cameraProbe.controllers.mobile.remoteSharing)).toBe(true);
  expect(await page.evaluate(() => (window as any).cameraProbe.controllers.pc.status)).toBe('Em chamada');
  expect(await page.evaluate(() => (window as any).cameraProbe.errors)).toEqual([]);
});
