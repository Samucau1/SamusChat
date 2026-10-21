/** Small jitter reserve, with a hard bound. Never rewind over queued audio. */
export function planAudioChunk(now: number, next: number | undefined, duration: number): number | null {
  if (!Number.isFinite(now) || !Number.isFinite(duration) || duration <= 0) return null;
  const at = next === undefined || next < now ? now + 0.24 : next;
  return at + duration > now + 0.4 + 1e-9 ? null : at;
}
