// Android wire format: PCM16 little-endian, mono, 16 kHz, 20 ms packets.
class DeviceAudioProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.packet = new ArrayBuffer(640);
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
      this.phase += 16000;
      if (this.phase >= sampleRate) {
        this.phase -= sampleRate;
        const sample = Math.max(-1, Math.min(1, this.sum / this.count));
        this.view.setInt16(this.position * 2, Math.round(sample * (sample < 0 ? 32768 : 32767)), true);
        this.sum = 0; this.count = 0;
        if (++this.position === 320) {
          this.port.postMessage(this.packet, [this.packet]);
          this.packet = new ArrayBuffer(640); this.view = new DataView(this.packet); this.position = 0;
        }
      }
    }
    // Outputs stay silent: never play captured system sound back locally.
    return true;
  }
}
registerProcessor('device-audio', DeviceAudioProcessor);
