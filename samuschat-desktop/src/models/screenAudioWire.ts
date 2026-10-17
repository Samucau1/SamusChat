export const opusReady = 'audio:opus-v1';
export function isOpusPacket(packet: ArrayBuffer): boolean {
  const bytes = new Uint8Array(packet);
  return bytes.length >= 9 && bytes.length <= 4096 && bytes[0] === 83 && bytes[1] === 65 && bytes[2] === 1 && bytes[3] === 0;
}
export function opusPacket(bytes: Uint8Array, sequence: number): ArrayBuffer {
  const packet = new Uint8Array(8 + bytes.length); packet.set([83, 65, 1, 0]);
  new DataView(packet.buffer).setUint32(4, sequence, true); packet.set(bytes, 8); return packet.buffer;
}
export function opusHeader(): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(19); bytes.set(new TextEncoder().encode('OpusHead')); bytes[8] = 1; bytes[9] = 1; new DataView(bytes.buffer).setUint32(12, 48000, true); return bytes;
}
export function legacyScreenPcm(packet: ArrayBuffer): ArrayBuffer {
  const input = new DataView(packet), out = new DataView(new ArrayBuffer(packet.byteLength / 3));
  for (let i = 0, j = 0; i + 5 < packet.byteLength; i += 6, j += 2) out.setInt16(j, Math.round((input.getInt16(i, true) + input.getInt16(i + 2, true) + input.getInt16(i + 4, true)) / 3), true);
  return out.buffer as ArrayBuffer;
}
