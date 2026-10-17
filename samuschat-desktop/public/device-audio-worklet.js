// Android wire format: PCM16 little-endian, mono, 16 kHz, 20 ms packets.
class DeviceAudioProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    this.targetRate = options?.processorOptions?.targetRate || 16000;
    this.packetSize = this.targetRate / 50;
    this.packet = new ArrayBuffer(this.packetSize * 2);
    this.view = new DataView(this.packet);
    this.position = 0;
    this.phase = 0;
    this.sum = 0;
    this.count = 0;
  }
  process(inputs) {
    const channels = inputs[0];
    if (!channels?.length) return true;
    for (let i = 0; i < channels[0].length; i++) {
      let value = 0;
      for (const channel of channels) value += channel[i] || 0;
      this.sum += value / channels.length; this.count++;
      this.phase += this.targetRate;
      while (this.phase >= sampleRate) {
        this.phase -= sampleRate;
        const sample = Math.max(-1, Math.min(1, (this.count ? this.sum / this.count : value / channels.length)));
        this.view.setInt16(this.position * 2, Math.round(sample * (sample < 0 ? 32768 : 32767)), true);
        this.sum = 0; this.count = 0;
        if (++this.position === this.packetSize) {
          this.port.postMessage(this.packet, [this.packet]);
          this.packet = new ArrayBuffer(this.packetSize * 2); this.view = new DataView(this.packet); this.position = 0;
        }
      }
    }
    // Outputs stay silent: never play captured system sound back locally.
    return true;
  }
}
registerProcessor('device-audio', DeviceAudioProcessor);
