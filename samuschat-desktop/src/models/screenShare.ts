export type ScreenQuality = 'economy' | 'balanced';
export const screenProfiles = {
  economy: { width: 960, height: 540, frameRate: 10, maxBitrate: 700_000 },
  balanced: { width: 1280, height: 720, frameRate: 15, maxBitrate: 1_500_000 },
} as const;

export async function constrainScreen(track: MediaStreamTrack, quality: ScreenQuality) {
  const profile = screenProfiles[quality];
  track.contentHint = 'detail';
  await track.applyConstraints({ width: { max: profile.width }, height: { max: profile.height }, frameRate: { max: profile.frameRate } });
}

export async function constrainSender(sender: RTCRtpSender, quality: ScreenQuality, bitrateBudget?: number) {
  const profile = screenProfiles[quality];
  const parameters = sender.getParameters();
  if (!parameters.encodings?.length) return;
  const settings = sender.track?.getSettings();
  for (const encoding of parameters.encodings) {
    encoding.maxBitrate = bitrateBudget ?? profile.maxBitrate;
    encoding.maxFramerate = profile.frameRate;
    encoding.scaleResolutionDownBy = Math.max(1, (settings?.width ?? profile.width) / profile.width, (settings?.height ?? profile.height) / profile.height);
  }
  parameters.degradationPreference = 'maintain-resolution';
  await sender.setParameters(parameters);
}
