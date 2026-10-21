import { isOpusPacket, legacyScreenPcm, opusHeader, opusPacket, opusReady } from './screenAudioWire';
import { planAudioChunk } from './audioPlayout';
export function decodeDeviceAudio(packet: ArrayBuffer): Float32Array<ArrayBuffer> | null {
  if (!packet.byteLength || packet.byteLength > 640 || packet.byteLength % 2) return null;
  const view = new DataView(packet), samples = new Float32Array(packet.byteLength / 2);
  for (let i = 0; i < samples.length; i++) samples[i] = view.getInt16(i * 2, true) / 32768;
  return samples;
}

/** Separate from microphone RTP, compatible with Android's device-audio channel. */
export class DeviceAudioTransport {
  private context: AudioContext | null = null;
  private channels = new Set<RTCDataChannel>();
  private sources = new Set<AudioBufferSourceNode>();
  private capture: { source: MediaStreamAudioSourceNode; worklet: AudioWorkletNode } | null = null;
  private nextTime = 0;
  private timelines = new Map<RTCDataChannel, number>();
  private modern = new Set<RTCDataChannel>();
  private decoders = new Map<RTCDataChannel, any>();
  private sequences = new Map<RTCDataChannel, number>();
  private encoder: any = null;
  private generation = 0;
  private captureSequence = 0;
  private closed = false;
  constructor(private blocked: (value: boolean) => void, private notice: (value: string) => void = () => {}) {}
  async activate() {
    if (this.closed) return;
    this.context ??= new AudioContext({ latencyHint: 'interactive', sampleRate: 48000 });
    const context = this.context;
    try { await context.resume(); } catch (error) { if (!this.closed) throw error; }
    if (!this.closed && this.context === context) this.blocked(context.state !== 'running');
  }
  attach(channel: RTCDataChannel): boolean {
    if (channel.label !== 'device-audio') return false;
    if (this.closed || [...this.channels].some(item => item === channel)) { channel.close(); return true; }
    this.channels.add(channel); channel.binaryType = 'arraybuffer';
    const advertise = () => { if (channel.readyState === 'open' && (window as any).AudioDecoder) channel.send(opusReady); };
    channel.addEventListener('open', advertise); advertise();
    channel.onclose = () => { this.channels.delete(channel); this.modern.delete(channel); this.timelines.delete(channel); this.decoders.get(channel)?.close(); this.decoders.delete(channel); this.sequences.delete(channel); };
    channel.onmessage = event => {
      if (this.closed) return;
      if (event.data === opusReady) { this.modern.add(channel); return; }
      if (!(event.data instanceof ArrayBuffer)) return;
      if (isOpusPacket(event.data)) {
        const NativeDecoder = (window as any).AudioDecoder, Chunk = (window as any).EncodedAudioChunk;
        if (!NativeDecoder || !Chunk) return;
        let decoder = this.decoders.get(channel);
        if (!decoder) {
          decoder = new NativeDecoder({ output: (frame: any) => {
            try { const samples = new Float32Array(frame.numberOfFrames); frame.copyTo(samples, { planeIndex: 0, format: 'f32-planar' }); this.play(samples, frame.sampleRate, channel); }
            finally { frame.close(); }
          }, error: () => { this.modern.delete(channel); } });
          decoder.configure({ codec: 'opus', sampleRate: 48000, numberOfChannels: 1, description: opusHeader() }); this.decoders.set(channel, decoder);
        }
        const sequence = new DataView(event.data).getUint32(4, true);
        if (sequence <= (this.sequences.get(channel) ?? -1) || decoder.decodeQueueSize >= 12 || decoder.state !== 'configured') return;
        this.sequences.set(channel, sequence);
        decoder.decode(new Chunk({ type: 'key', timestamp: sequence * 20000, data: new Uint8Array(event.data, 8) }));
      } else {
        const samples = decodeDeviceAudio(event.data); if (samples) this.play(samples, 16000, channel);
      }
    };
    return true;
  }
  private play(samples: Float32Array<ArrayBuffer>, rate: number, channel: RTCDataChannel) {
    const context = this.context; if (!context || this.closed) return;
    if (context.state !== 'running') { this.blocked(true); return; }
    const at = planAudioChunk(context.currentTime, this.timelines.get(channel), samples.length / rate);
    if (at === null) return; // Drop overflow rather than overlap previously queued audio.
    const buffer = context.createBuffer(1, samples.length, rate); buffer.copyToChannel(samples, 0);
    const source = context.createBufferSource(); source.buffer = buffer; source.connect(context.destination);
    this.sources.add(source); source.onended = () => { this.sources.delete(source); source.disconnect(); };
    source.start(at); const next = at + buffer.duration;
    this.timelines.set(channel, next); this.nextTime = next;
  }
  async startCapture(stream: MediaStream): Promise<boolean> {
    this.stopCapture(); const generation = this.generation;
    if (!stream.getAudioTracks().length) return false;
    await this.activate();
    if (this.closed || generation !== this.generation || !this.context) return false;
    const context = this.context;
    if (context.state !== 'running') throw new Error('Ative o áudio antes de transmitir.');
    await context.audioWorklet.addModule('/device-audio-worklet.js');
    if (this.closed || generation !== this.generation) return false;
    const source = context.createMediaStreamSource(new MediaStream(stream.getAudioTracks()));
    const worklet = new AudioWorkletNode(context, 'device-audio', { processorOptions: { targetRate: 48000 } });
    const NativeEncoder = (window as any).AudioEncoder, AudioData = (window as any).AudioData;
    if (NativeEncoder && AudioData) {
      try {
        this.encoder = new NativeEncoder({ output: (chunk: any) => {
          if (this.closed || generation !== this.generation) return;
          const bytes = new Uint8Array(chunk.byteLength); chunk.copyTo(bytes);
          if (bytes.length > 4088) return;
          const packet = opusPacket(bytes, Math.round(chunk.timestamp / 20000));
          for (const channel of this.channels) if (this.modern.has(channel) && channel.readyState === 'open' && channel.bufferedAmount < 800) { try { channel.send(packet); } catch {} }
        }, error: () => { this.encoder = null; } });
        this.encoder.configure({ codec: 'opus', sampleRate: 48000, numberOfChannels: 1, bitrate: 64000 });
      } catch { this.encoder?.close(); this.encoder = null; }
    }
    let sequence = this.captureSequence, lastNotice = 0;
    worklet.port.onmessage = event => {
      if (this.closed || generation !== this.generation || !(event.data instanceof ArrayBuffer)) return;
      const targets = [...this.channels].filter(channel => channel.readyState === 'open');
      const compressed = this.encoder?.state === 'configured';
      if (compressed && this.encoder.encodeQueueSize < 3 && targets.some(channel => this.modern.has(channel))) {
        const frame = new AudioData({ format: 's16', sampleRate: 48000, numberOfFrames: event.data.byteLength / 2, numberOfChannels: 1, timestamp: sequence * 20000, data: event.data });
        try { this.encoder.encode(frame); } finally { frame.close(); }
      }
      sequence++;
      this.captureSequence = sequence;
      const legacy = targets.filter(channel => !compressed || !this.modern.has(channel));
      if (legacy.length) {
        const packet = legacyScreenPcm(event.data);
        for (const channel of legacy) if (channel.bufferedAmount < 3200) { try { channel.send(packet); } catch {} }
      }
      if (performance.now() - lastNotice > 1000) {
        const samples = new Int16Array(event.data), active = samples.some(sample => Math.abs(sample) > 100);
        this.notice(!targets.length ? 'Aguardando conexão do áudio' : active ? 'Enviando áudio da transmissão' : 'Aguardando som da fonte compartilhada'); lastNotice = performance.now();
      }
    };
    source.connect(worklet); worklet.connect(context.destination);
    this.capture = { source, worklet }; return true;
  }
  stopCapture() {
    this.generation++;
    this.encoder?.close(); this.encoder = null;
    if (this.capture) {
      this.capture.worklet.port.onmessage = null; this.capture.worklet.port.close();
      this.capture.source.disconnect(); this.capture.worklet.disconnect(); this.capture = null;
    }
  }
  close() {
    this.closed = true; this.stopCapture();
    for (const channel of this.channels) { channel.onmessage = channel.onclose = null; channel.close(); }
    this.channels.clear(); this.modern.clear(); this.timelines.clear(); this.sequences.clear();
    for (const decoder of this.decoders.values()) decoder.close(); this.decoders.clear();
    for (const source of this.sources) { source.onended = null; source.stop(); source.disconnect(); }
    this.sources.clear(); if (this.context) void this.context.close(); this.context = null;
  }
}
