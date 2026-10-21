/** Prefer resilience to measured arrival jitter on screen video, when supported. */
export function configureScreenReceiver(receiver: RTCRtpReceiver): boolean {
  if (receiver.track.kind !== 'video' || !('jitterBufferTarget' in receiver)) return false;
  try {
    (receiver as RTCRtpReceiver & { jitterBufferTarget: number | null }).jitterBufferTarget = 320;
    return true;
  } catch { return false; } // Keep the browser's automatic buffer on older implementations.
}
