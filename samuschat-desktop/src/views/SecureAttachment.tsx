import { useEffect, useState } from 'react';
export function SecureAttachment({ href, image, load }: { href: string; image: boolean; load: (url: string) => Promise<Blob | null> }) {
  const [preview, setPreview] = useState(''); const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!image) return;
    let active = true, url = '';
    void load(href).then(blob => { if (blob && active) { url = URL.createObjectURL(blob); setPreview(url); } });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [href, image]);
  async function download() {
    if (busy) return; setBusy(true);
    try {
      const blob = await load(href); if (!blob) return;
      const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
      anchor.href = url; anchor.download = new URL(href, window.location.origin).pathname.split('/').pop() ?? 'anexo';
      anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } finally { setBusy(false); }
  }
  return <a className="attachment" href={href} onClick={event => { event.preventDefault(); void download(); }} aria-disabled={busy}>{image && preview && <img src={preview} alt="Imagem anexada" loading="lazy" />} {busy ? 'Baixando…' : 'Abrir anexo ↗'}</a>;
}
