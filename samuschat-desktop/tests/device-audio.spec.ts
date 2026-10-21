import { test, expect } from '@playwright/test';
import { decodeDeviceAudio } from '../src/models/deviceAudio';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { isOpusPacket, legacyScreenPcm, opusPacket } from '../src/models/screenAudioWire';

test('Opus versionado e compatibilidade PCM mantêm limites e ordem little endian', () => {
  const packet = opusPacket(new Uint8Array([1, 2, 3]), 77);
  expect(isOpusPacket(packet)).toBe(true); expect(new DataView(packet).getUint32(4, true)).toBe(77);
  for (const size of [0, 8, 4097]) expect(isOpusPacket(new ArrayBuffer(size))).toBe(false);
  const samples = new Int16Array(960).fill(-12000);
  const legacy = legacyScreenPcm(samples.buffer);
  expect(legacy.byteLength).toBe(640); expect(new DataView(legacy).getInt16(0, true)).toBe(-12000);
});

test('PCM Android: little endian, limites, amostras negativas e pacotes invalidos', () => {
  const packet = new ArrayBuffer(6), view = new DataView(packet);
  view.setInt16(0, -32768, true); view.setInt16(2, 0, true); view.setInt16(4, 32767, true);
  expect([...decodeDeviceAudio(packet)!]).toEqual([-1, 0, 32767 / 32768]);
  for (const length of [0, 1, 641, 642]) expect(decodeDeviceAudio(new ArrayBuffer(length))).toBeNull();
});

test('worklet reduz 48kHz/44.1kHz estéreo para PCM16/16kHz mono em pacotes de 20ms', () => {
  for (const rate of [48000, 44100]) {
    const packets: ArrayBuffer[] = [];
    let Processor: any;
    const context = vm.createContext({ sampleRate: rate, ArrayBuffer, DataView, Math,
      AudioWorkletProcessor: class { port = { postMessage: (packet: ArrayBuffer) => packets.push(packet) }; },
      registerProcessor: (_: string, implementation: any) => { Processor = implementation; }
    });
    vm.runInContext(readFileSync(new URL('../public/device-audio-worklet.js', import.meta.url), 'utf8'), context);
    const processor = new Processor();
    const left = new Float32Array(128).fill(0.75), right = new Float32Array(128).fill(0.25);
    const blocks = Math.ceil(rate / 128);
    for (let i = 0; i < blocks; i++) processor.process([[left, right]]);
    expect(packets.length).toBeGreaterThanOrEqual(50);
    expect(packets.length).toBeLessThanOrEqual(51);
    expect(packets.every(packet => packet.byteLength === 640)).toBe(true);
    expect(decodeDeviceAudio(packets[0])![0]).toBeCloseTo(0.5, 3);
  }
});

test('áudio real sintético pelo canal Android: captura, recepção, limite de fila e parada', async ({ page }) => {
  await page.goto('/');
  const result = await page.evaluate(async () => {
    const { DeviceAudioTransport } = await import('/src/models/deviceAudio.ts');
    const tx = new DeviceAudioTransport(() => {}), rx = new DeviceAudioTransport(() => {});
    const a = new RTCPeerConnection(), b = new RTCPeerConnection();
    const sourceContext = new AudioContext(), destination = sourceContext.createMediaStreamDestination();
    const oscillator = sourceContext.createOscillator(); oscillator.frequency.value = 440; oscillator.connect(destination); oscillator.start();
    let packets = 0, audible = 0, wireBytes = 0, decodedRms = 0, decodedRate = 0, decodedFrames = 0;
    const receiverProbe = rx as any, originalPlay = receiverProbe.play.bind(rx);
    receiverProbe.play = (samples: Float32Array, rate: number, channel: RTCDataChannel) => {
      decodedFrames++;
      decodedRms = Math.max(decodedRms, Math.sqrt(samples.reduce((sum, sample) => sum + sample * sample, 0) / samples.length)); decodedRate = rate;
      originalPlay(samples, rate, channel);
    };
    const channel = a.createDataChannel('device-audio', { ordered: false, maxRetransmits: 0 }); tx.attach(channel);
    b.ondatachannel = event => {
      rx.attach(event.channel); const receive = event.channel.onmessage!;
      event.channel.onmessage = data => {
        if (data.data instanceof ArrayBuffer) {
          packets++; wireBytes = data.data.byteLength;
          // Encoded Opus packets may have odd lengths; inspecting them as PCM16 throws.
          const bytes = new Uint8Array(data.data);
          if (bytes.length > 8 && bytes[0] === 83 && bytes[1] === 65 && bytes[2] === 1) audible++;
        }
        receive.call(event.channel, data);
      };
    };
    a.onicecandidate = event => { if (event.candidate) void b.addIceCandidate(event.candidate); };
    b.onicecandidate = event => { if (event.candidate) void a.addIceCandidate(event.candidate); };
    try {
      await Promise.all([tx.activate(), rx.activate(), sourceContext.resume()]);
      await a.setLocalDescription(await a.createOffer()); await b.setRemoteDescription(a.localDescription!);
      await b.setLocalDescription(await b.createAnswer()); await a.setRemoteDescription(b.localDescription!);
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Data channel timeout')), 10000);
        if (channel.readyState === 'open') { clearTimeout(timer); resolve(); }
        else channel.onopen = () => { clearTimeout(timer); resolve(); };
      });
      await tx.startCapture(destination.stream);
      await new Promise(resolve => setTimeout(resolve, 600));
      const before = packets;
      tx.stopCapture(); await new Promise(resolve => setTimeout(resolve, 200));
      const after = packets; await new Promise(resolve => setTimeout(resolve, 150));
      const stopped = packets === after, beforeResume = decodedFrames;
      await tx.startCapture(destination.stream); await new Promise(resolve => setTimeout(resolve, 300));
      const resumedFrames = decodedFrames - beforeResume; tx.stopCapture();
      const receiver = rx as any;
      return { before, audible, wireBytes, decodedRms, decodedRate, stopped, resumedFrames, queued: receiver.sources.size, ahead: receiver.nextTime - receiver.context.currentTime };
    } finally { tx.close(); rx.close(); a.close(); b.close(); oscillator.stop(); await sourceContext.close(); }
  });
  expect(result.before).toBeGreaterThan(10);
  expect(result.audible).toBeGreaterThan(10);
  expect(result.wireBytes).toBeLessThan(640);
  expect(result.decodedRms).toBeGreaterThan(0.05); expect(result.decodedRate).toBe(48000);
  expect(result.stopped).toBe(true);
  expect(result.resumedFrames).toBeGreaterThan(5);
  expect(result.queued).toBeLessThanOrEqual(20);
  expect(result.ahead).toBeLessThanOrEqual(0.4);
});
