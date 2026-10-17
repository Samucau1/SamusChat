export interface Crop { x: number; y: number; zoom: number }
/** Normalized offsets always address the available overflow, never distort pixels. */
export function cropRect(width: number, height: number, crop: Crop) {
  if (![width, height, crop.x, crop.y, crop.zoom].every(Number.isFinite) || width <= 0 || height <= 0) throw new Error('Imagem inválida.');
  const side = Math.min(width, height) / Math.max(1, Math.min(4, crop.zoom));
  return { x: (width - side) * Math.max(0, Math.min(1, crop.x)), y: (height - side) * Math.max(0, Math.min(1, crop.y)), side };
}
export async function cropAvatar(image: HTMLImageElement, crop: Crop): Promise<File> {
  const rect = cropRect(image.naturalWidth, image.naturalHeight, crop);
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 512;
  canvas.getContext('2d')!.drawImage(image, rect.x, rect.y, rect.side, rect.side, 0, 0, 512, 512);
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('Não foi possível preparar a foto.')), 'image/png'));
  return new File([blob], 'avatar.png', { type: 'image/png' });
}
