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
  private generation = 0;
  private closed = false;
  constructor(private blocked: (value: boolean) => void) {}
  async activate() {
    if (this.closed) return;
    this.context ??= new AudioContext({ latencyHint: 'interactive' });
    const context = this.context;
    try { await context.resume(); } catch (error) { if (!this.closed) throw error; }
    if (!this.closed && this.context === context) this.blocked(context.state !== 'running');
  }
  attach(channel: RTCDataChannel): boolean {
    if (channel.label !== 'device-audio') return false;
    if (this.closed || [...this.channels].some(item => item === channel)) { channel.close(); return true; }
    this.channels.add(channel); channel.binaryType = 'arraybuffer';
    channel.onclose = () => this.channels.delete(channel);
    channel.onmessage = event => {
      if (this.closed || !(event.data instanceof ArrayBuffer)) return;
      const samples = decodeDeviceAudio(event.data), context = this.context;
      if (!samples || !context) return;
      if (context.state !== 'running') { this.blocked(true); return; }
      // Drop queued playback when delayed; never accumulate stale audio.
      if (this.nextTime - context.currentTime > 0.1) {
        for (const source of this.sources) { source.onended = null; source.stop(); source.disconnect(); }
        this.sources.clear(); this.nextTime = context.currentTime;
      }
      const buffer = context.createBuffer(1, samples.length, 16000);
      buffer.copyToChannel(samples, 0);
      const source = context.createBufferSource(); source.buffer = buffer; source.connect(context.destination);
      this.sources.add(source); source.onended = () => { this.sources.delete(source); source.disconnect(); };
      const at = Math.max(context.currentTime, this.nextTime); source.start(at); this.nextTime = at + buffer.duration;
    };
    return true;
  }
  async startCapture(stream: MediaStream): Promise<boolean> {
    this.stopCapture(); const generation = this.generation;
    if (!stream.getAudioTracks().length) return false;
    await this.activate();
    if (this.closed || generation !== this.generation || !this.context) return false;
    const context = this.context;
    await context.audioWorklet.addModule('/device-audio-worklet.js');
    if (this.closed || generation !== this.generation) return false;
    const source = context.createMediaStreamSource(new MediaStream(stream.getAudioTracks()));
    const worklet = new AudioWorkletNode(context, 'device-audio');
    worklet.port.onmessage = event => {
      if (this.closed || generation !== this.generation || !(event.data instanceof ArrayBuffer)) return;
      for (const channel of this.channels) if (channel.readyState === 'open' && channel.bufferedAmount < 3200) {
        try { channel.send(event.data); } catch { /* The peer can leave during a packet. */ }
      }
    };
    source.connect(worklet); worklet.connect(context.destination);
    this.capture = { source, worklet }; return true;
  }
  stopCapture() {
    this.generation++;
    if (this.capture) {
      this.capture.worklet.port.onmessage = null; this.capture.worklet.port.close();
      this.capture.source.disconnect(); this.capture.worklet.disconnect(); this.capture = null;
    }
  }
  close() {
    this.closed = true; this.stopCapture();
    for (const channel of this.channels) { channel.onmessage = channel.onclose = null; channel.close(); }
    this.channels.clear();
    for (const source of this.sources) { source.onended = null; source.stop(); source.disconnect(); }
    this.sources.clear(); if (this.context) void this.context.close(); this.context = null;
  }
}
