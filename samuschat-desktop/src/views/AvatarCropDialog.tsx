import { useEffect, useRef, useState } from 'react';
import { cropAvatar, cropRect } from '../models/avatarCrop';

export function AvatarCropDialog({ file, close, save }: { file: File; close: () => void; save: (file: File) => Promise<boolean> }) {
  const dialog = useRef<HTMLDialogElement>(null), canvas = useRef<HTMLCanvasElement>(null);
  const [image, setImage] = useState<HTMLImageElement | null>(null);
  const [crop, setCrop] = useState({ x: 0.5, y: 0.5, zoom: 1 });
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const drag = useRef<{ x: number; y: number; left: number; top: number } | null>(null);
  useEffect(() => {
    dialog.current?.showModal(); let active = true;
    const url = URL.createObjectURL(file), source = new Image();
    source.onload = () => { if (active) {
      if (source.naturalWidth > 4096 || source.naturalHeight > 4096 || source.naturalWidth * source.naturalHeight > 16_000_000) setError('Use imagem de até 4096 px e 16 megapixels.');
      else setImage(source);
    } };
    source.onerror = () => { if (active) setError('Não foi possível abrir a imagem.'); }; source.src = url;
    return () => { active = false; URL.revokeObjectURL(url); };
  }, [file]);
  useEffect(() => {
    if (!image || !canvas.current) return;
    const rect = cropRect(image.naturalWidth, image.naturalHeight, crop), context = canvas.current.getContext('2d')!;
    context.clearRect(0, 0, 512, 512); context.drawImage(image, rect.x, rect.y, rect.side, rect.side, 0, 0, 512, 512);
  }, [image, crop]);
  async function confirm(original = false) {
    if (busy || !image) return; setBusy(true); setError('');
    try { if (await save(original ? file : await cropAvatar(image, crop))) close(); else setError('Não foi possível salvar a foto. Tente novamente.'); }
    catch (problem) { setError(problem instanceof Error ? problem.message : 'Não foi possível salvar.'); }
    finally { setBusy(false); }
  }
  return <dialog ref={dialog} className="avatar-crop-dialog" aria-label="Enquadrar foto de perfil" onCancel={event => { event.preventDefault(); if (!busy) close(); }}>
    <h2>Enquadrar foto de perfil</h2><p>Arraste a imagem e ajuste o zoom. O círculo mostra como sua foto vai aparecer.</p>
    <canvas ref={canvas} width={512} height={512} className="avatar-crop-preview" aria-label="Prévia circular da foto" onPointerDown={event => {
      if (busy) return; event.currentTarget.setPointerCapture(event.pointerId); drag.current = { x: event.clientX, y: event.clientY, left: crop.x, top: crop.y };
    }} onPointerMove={event => {
      if (!drag.current || !image) return;
      const rect = cropRect(image.naturalWidth, image.naturalHeight, crop), scale = event.currentTarget.clientWidth / rect.side;
      const x = drag.current.left - (event.clientX - drag.current.x) / Math.max(1, (image.naturalWidth - rect.side) * scale);
      const y = drag.current.top - (event.clientY - drag.current.y) / Math.max(1, (image.naturalHeight - rect.side) * scale);
      setCrop(previous => ({ ...previous, x: Math.max(0, Math.min(1, x)), y: Math.max(0, Math.min(1, y)) }));
    }} onPointerUp={() => { drag.current = null; }} onPointerCancel={() => { drag.current = null; }} />
    <label>Zoom<input type="range" min="1" max="4" step="0.01" value={crop.zoom} disabled={busy} onChange={event => setCrop({ ...crop, zoom: Number(event.target.value) })} /></label>
    <label>Posição horizontal<input type="range" min="0" max="1" step="0.01" value={crop.x} disabled={busy} onChange={event => setCrop({ ...crop, x: Number(event.target.value) })} /></label>
    <label>Posição vertical<input type="range" min="0" max="1" step="0.01" value={crop.y} disabled={busy} onChange={event => setCrop({ ...crop, y: Number(event.target.value) })} /></label>
    {file.type === 'image/gif' && <p>O recorte salva uma foto estática. Para manter a animação, use o GIF original.</p>}
    {error && <p role="alert">{error}</p>}
    <div className="dialog-actions"><button type="button" disabled={busy} onClick={close}>Cancelar recorte</button><button type="button" disabled={busy} onClick={() => setCrop({ x: 0.5, y: 0.5, zoom: 1 })}>Centralizar</button>{file.type === 'image/gif' && <button type="button" disabled={busy || !image} onClick={() => { void confirm(true); }}>Usar GIF original</button>}<button type="button" className="primary" disabled={busy || !image} onClick={() => { void confirm(); }}>Salvar enquadramento</button></div>
  </dialog>;
}
