import type { ScreenPreferences } from '../controllers/useScreenPreferences';
import type { useScreenFeedback } from '../controllers/useScreenFeedback';
import { screenFrameRates, screenHeights } from '../models/screenShare';
export function ScreenShareControls({ preferences: p, locked, feedback }: { preferences: ScreenPreferences; locked: boolean; feedback: ReturnType<typeof useScreenFeedback> }) {
  const metrics = feedback?.metrics;
  return <div className="screen-controls">
    <div className="screen-control-fields"><label>Resolução da transmissão<select aria-label="Resolução da transmissão" disabled={locked} value={p.profile.height} onChange={event => p.choose(Number(event.target.value), p.profile.frameRate)}>{screenHeights.map(height => <option key={height} value={height}>{height}p{height === 2160 ? ' · 4K' : ''}</option>)}</select></label>
      <label>FPS da transmissão<select aria-label="FPS da transmissão" disabled={locked} value={p.profile.frameRate} onChange={event => p.choose(p.profile.height, Number(event.target.value))}>{screenFrameRates.map(fps => <option key={fps} value={fps}>{fps} fps</option>)}</select></label>
      <label className="screen-auto"><input type="checkbox" checked={p.automatic} onChange={event => p.setAutomatic(event.target.checked)} /> Ajustar ao desempenho</label>
    </div>
    <small>{p.hardware ? `Sugestão para esta máquina: ${p.hardware.recommendation.height}p/${p.hardware.recommendation.frameRate} fps` : 'Verificando capacidade da máquina…'} · mínimo 750p/30 fps</small>
    {feedback && <p className="screen-feedback" role="status">Em uso: {feedback.profile.height}p/{feedback.profile.frameRate} fps{metrics?.fps !== undefined ? ` · saída medida: ${Math.round(metrics.fps)} fps` : ''}{metrics?.height ? ` · vídeo enviado: ${metrics.height}p` : ''}{metrics?.powerEfficient !== undefined ? metrics.powerEfficient ? ' · codificação eficiente indicada pelo navegador' : ' · codificação sem indicação de eficiência' : ''}</p>}
    {feedback?.notice && <p className="screen-feedback-notice" role="status">{feedback.notice}</p>}
  </div>;
}
