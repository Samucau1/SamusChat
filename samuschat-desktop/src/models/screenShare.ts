export const screenHeights = [750, 900, 1080, 1440, 2160] as const;
export const screenFrameRates = [30, 60] as const;
export interface ScreenProfile { width: number; height: number; minHeight: number; frameRate: number; maxBitrate: number }
export function screenProfile(height = 900, frameRate = 30): ScreenProfile {
  if (!(screenHeights as readonly number[]).includes(height) || !(screenFrameRates as readonly number[]).includes(frameRate)) throw new Error('Escolha uma resolução de pelo menos 750p e 30 ou 60 fps.');
  const bitrate = { 750: 2_500_000, 900: 4_000_000, 1080: 5_500_000, 1440: 9_000_000, 2160: 16_000_000 }[height]!;
  return { width: Math.ceil(height * 16 / 9 / 2) * 2, height, minHeight: height, frameRate, maxBitrate: bitrate * frameRate / 30 };
}
export const desktopScreenProfile = screenProfile();

export function lowerScreenProfile(profile: ScreenProfile): ScreenProfile | null {
  if (profile.frameRate > 30) return screenProfile(profile.height, 30);
  const index = (screenHeights as readonly number[]).indexOf(profile.height);
  return index > 0 ? screenProfile(screenHeights[index - 1], 30) : null;
}

export function screenCaptureOptions(profile: ScreenProfile = desktopScreenProfile): DisplayMediaStreamOptions {
  // getDisplayMedia disallows mandatory min/exact constraints in the picker.
  return { video: { width: { ideal: profile.width }, height: { ideal: profile.height }, frameRate: { ideal: profile.frameRate } }, audio: false };
}

export async function constrainScreen(track: MediaStreamTrack, profile: ScreenProfile = desktopScreenProfile) {
  track.contentHint = 'motion';
  // Keep aspect ratio: ultrawide sources can require more horizontal pixels.
  await track.applyConstraints({ height: { exact: profile.height }, frameRate: { exact: profile.frameRate } });
  const settings = track.getSettings();
  if (settings.height !== profile.height || settings.frameRate === undefined || Math.abs(settings.frameRate - profile.frameRate) > 0.01) {
    throw new Error(`A fonte selecionada não forneceu ${profile.height}p/${profile.frameRate} fps. O mínimo é 750p/30 fps; escolha outra tela, janela ou qualidade.`);
  }
}

export async function constrainSender(sender: RTCRtpSender, profile: ScreenProfile = desktopScreenProfile) {
  const parameters = sender.getParameters();
  if (!parameters.encodings?.length) return;
  for (const encoding of parameters.encodings) {
    encoding.maxBitrate = profile.maxBitrate;
    encoding.maxFramerate = profile.frameRate;
    encoding.scaleResolutionDownBy = 1;
  }
  // Standardized by W3C; older TypeScript DOM declarations omit this value.
  parameters.degradationPreference = 'maintain-framerate-and-resolution' as RTCDegradationPreference;
  try { await sender.setParameters(parameters); }
  catch (problem) {
    if (problem instanceof TypeError) throw new Error('Atualize o navegador para compartilhar mantendo resolução e 30 fps.');
    throw problem;
  }
}

export interface EncoderSample {
  timestamp: number; frames?: number; bytes?: number; encodeSeconds?: number;
  fps?: number; height?: number; width?: number; reason?: string; powerEfficient?: boolean;
}
export interface ScreenMetrics { fps?: number; height?: number; width?: number; bitrate?: number; encodeMs?: number; reason: string; powerEfficient?: boolean }
export function encoderSample(report: RTCStatsReport): EncoderSample | undefined {
  let sample: EncoderSample | undefined;
  report.forEach(item => {
    if (item.type === 'outbound-rtp' && (item.kind === 'video' || item.mediaType === 'video') && !item.isRemote) {
      sample = { timestamp: item.timestamp, frames: item.framesEncoded, bytes: item.bytesSent, encodeSeconds: item.totalEncodeTime,
        fps: item.framesPerSecond, height: item.frameHeight, width: item.frameWidth, reason: item.qualityLimitationReason, powerEfficient: item.powerEfficientEncoder };
    }
  });
  return sample;
}
export function screenMetrics(sample: EncoderSample, previous?: EncoderSample): ScreenMetrics {
  const seconds = previous ? (sample.timestamp - previous.timestamp) / 1000 : 0;
  const frames = previous && sample.frames !== undefined && previous.frames !== undefined ? sample.frames - previous.frames : 0;
  const valid = seconds > 0 && frames > 0;
  return { fps: valid ? frames / seconds : sample.fps, height: sample.height, width: sample.width,
    bitrate: seconds > 0 && previous?.bytes !== undefined && sample.bytes !== undefined && sample.bytes >= previous.bytes ? (sample.bytes - previous.bytes) * 8 / seconds : undefined,
    encodeMs: valid && sample.encodeSeconds !== undefined && previous?.encodeSeconds !== undefined && sample.encodeSeconds >= previous.encodeSeconds ? (sample.encodeSeconds - previous.encodeSeconds) * 1000 / frames : undefined,
    reason: sample.reason ?? 'unknown', powerEfficient: sample.powerEfficient };
}
export function screenUnderPressure(metrics: ScreenMetrics, profile: ScreenProfile) {
  return metrics.reason === 'cpu' || metrics.reason === 'bandwidth' || (metrics.encodeMs !== undefined && metrics.encodeMs > 1000 / profile.frameRate * 0.85);
}

export interface ScreenHardware { cores?: number; memory?: number; efficientCodec?: string; recommendation: ScreenProfile; probed: boolean }
export async function probeScreenHardware(): Promise<ScreenHardware> {
  const cores = navigator.hardwareConcurrency || undefined;
  const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory;
  let efficientCodec: string | undefined;
  let probed = false;
  const codecs = RTCRtpSender.getCapabilities?.('video')?.codecs ?? [];
  if (navigator.mediaCapabilities?.encodingInfo) {
    for (const mime of ['video/H264', 'video/VP8']) {
      if (!codecs.some(item => item.mimeType.toLowerCase() === mime.toLowerCase())) continue;
      try {
        const result = await navigator.mediaCapabilities.encodingInfo({ type: 'webrtc' as MediaEncodingType,
          video: { contentType: mime, width: 1920, height: 1080, bitrate: 5_500_000, framerate: 30 } });
        probed = true;
        if (result.supported && result.smooth && result.powerEfficient) { efficientCodec = mime; break; }
      } catch { /* Unsupported capability probes do not block native capture. */ }
    }
  }
  const small = (cores !== undefined && cores <= 4) || (memory !== undefined && memory <= 4);
  return { cores, memory, efficientCodec, probed, recommendation: screenProfile(small ? 750 : efficientCodec && (cores ?? 0) >= 8 ? 1080 : 900, 30) };
}
export function preferEfficientEncoder(connection: RTCPeerConnection, sender: RTCRtpSender, hardware: ScreenHardware | null) {
  if (!hardware?.efficientCodec) return;
  const transceiver = connection.getTransceivers().find(item => item.sender === sender);
  const codecs = RTCRtpSender.getCapabilities?.('video')?.codecs ?? [];
  const preferred = codecs.filter(item => item.mimeType.toLowerCase() === hardware.efficientCodec!.toLowerCase());
  if (!transceiver?.setCodecPreferences || !preferred.length) return;
  try { transceiver.setCodecPreferences([...preferred, ...codecs.filter(item => !preferred.includes(item))]); }
  catch { /* Keep the browser's interoperable codec order if unavailable. */ }
}
