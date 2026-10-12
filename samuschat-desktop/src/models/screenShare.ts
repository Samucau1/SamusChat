export const desktopScreenProfile = {
  width: 1600, height: 900, minHeight: 750, frameRate: 30, maxBitrate: 4_000_000,
} as const;

export function screenCaptureOptions(): DisplayMediaStreamOptions {
  const profile = desktopScreenProfile;
  // getDisplayMedia disallows mandatory min/exact constraints in the picker.
  return { video: { width: { ideal: profile.width }, height: { ideal: profile.height }, frameRate: { ideal: profile.frameRate } }, audio: false };
}

export async function constrainScreen(track: MediaStreamTrack) {
  const profile = desktopScreenProfile;
  track.contentHint = 'motion';
  await track.applyConstraints({ width: { ideal: profile.width, max: profile.width }, height: { min: profile.minHeight, ideal: profile.height, max: profile.height }, frameRate: { exact: profile.frameRate } });
  const settings = track.getSettings();
  if (settings.height === undefined || settings.height < profile.minHeight || settings.frameRate === undefined || Math.abs(settings.frameRate - profile.frameRate) > 0.01) {
    throw new Error('A fonte selecionada não forneceu pelo menos 750p e captura configurada em 30 fps. Escolha outra tela ou janela.');
  }
}

export async function constrainSender(sender: RTCRtpSender) {
  const parameters = sender.getParameters();
  if (!parameters.encodings?.length) return;
  for (const encoding of parameters.encodings) {
    encoding.maxBitrate = desktopScreenProfile.maxBitrate;
    encoding.maxFramerate = desktopScreenProfile.frameRate;
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
