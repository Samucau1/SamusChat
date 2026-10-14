import { useEffect, useRef } from 'react';
import type { ScreenPreferences } from '../controllers/useScreenPreferences';
import type { useScreenFeedback } from '../controllers/useScreenFeedback';
import { screenFrameRates, screenHeights } from '../models/screenShare';
export type ScreenFeedback = ReturnType<typeof useScreenFeedback>;
export function ScreenShareFeedback({ feedback }: { feedback: ScreenFeedback }) {
  if (!feedback) return null;
  const metrics = feedback.metrics;
  return <div className="screen-diagnostics">
    <p className="screen-feedback" role="status"><span className="live-badge">AO VIVO</span> {feedback.profile.height}p · {feedback.profile.frameRate} fps configurados
      {metrics?.fps !== undefined ? ` · ${Math.round(metrics.fps)} fps enviados` : ' · aguardando dados de envio'}
      {metrics?.height ? ` · ${metrics.height}p enviados` : ''}
      {metrics?.bitrate !== undefined ? ` · ${(metrics.bitrate / 1_000_000).toFixed(1)} Mbps` : ''}
      {metrics?.powerEfficient ? ' · codificação eficiente' : ''}</p>
    {feedback.notice && <p className="screen-feedback-notice" role="status">{feedback.notice}</p>}
  </div>;
}
export function ScreenShareControls({ preferences: p, locked, close }: { preferences: ScreenPreferences; locked: boolean; close: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} className="screen-settings-dialog" aria-labelledby="screen-settings-title" onCancel={close}>
    <header className="screen-settings-header"><div><span className="eyebrow">TRANSMISSÃO DE TELA</span><h2 id="screen-settings-title">Qualidade da transmissão</h2></div><button type="button" aria-label="Fechar configurações da transmissão" onClick={close}>×</button></header>
    <p>Escolha a qualidade para sua máquina. As preferências ficam salvas para a próxima transmissão.</p>
    <div className="screen-controls">
    <div className="screen-control-fields"><label>Resolução da transmissão<select aria-label="Resolução da transmissão" disabled={locked} value={p.profile.height} onChange={event => p.choose(Number(event.target.value), p.profile.frameRate)}>{screenHeights.map(height => <option key={height} value={height}>{height}p{height === 2160 ? ' · 4K' : ''}</option>)}</select></label>
      <label>FPS da transmissão<select aria-label="FPS da transmissão" disabled={locked} value={p.profile.frameRate} onChange={event => p.choose(p.profile.height, Number(event.target.value))}>{screenFrameRates.map(fps => <option key={fps} value={fps}>{fps} fps</option>)}</select></label>
      <label className="screen-auto"><input type="checkbox" checked={p.automatic} onChange={event => p.setAutomatic(event.target.checked)} /> Ajustar ao desempenho</label>
    </div>
    <small>{p.hardware ? `Sugestão para esta máquina: ${p.hardware.recommendation.height}p/${p.hardware.recommendation.frameRate} fps` : 'Verificando capacidade da máquina…'} · mínimo 750p/30 fps</small>
    </div>
    {locked && <p className="settings-notice">Pare a transmissão para alterar resolução ou FPS. O ajuste de desempenho pode ser ativado durante o envio.</p>}
    <p className="settings-footnote">O FPS enviado depende da fonte, do hardware e da conexão. Você pode acompanhar os dados na ligação.</p>
    <div className="dialog-actions"><button className="primary" onClick={close}>Concluído</button></div>
  </dialog>;
}
