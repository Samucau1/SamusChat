import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { CallController } from '../controllers/useCallController';
import type { VoiceRoomController } from '../controllers/useVoiceRoomController';
import type { ScreenPreferences } from '../controllers/useScreenPreferences';
import { AppearanceImage } from './Appearance';
import { ScreenShareControls, ScreenShareFeedback, type ScreenFeedback } from './ScreenShareControls';

type IconName = 'mic' | 'screen' | 'phone' | 'gear' | 'eye' | 'expand';
function CallIcon({ name, off = false }: { name: IconName; off?: boolean }) {
  const paths: Record<IconName, ReactNode> = {
    mic: <><rect x="9" y="2" width="6" height="12" rx="3" /><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8" /></>,
    screen: <><rect x="2" y="3" width="20" height="14" rx="2" /><path d="M8 21h8M12 17v4M8 10l4-4 4 4M12 6v8" /></>,
    phone: <path d="M3 16v-5c5-4 13-4 18 0v5h-5v-4a13 13 0 0 0-8 0v4Z" />,
    gear: <><path d="m10 2-.6 3-2 .9-2.6-1.5-2 3.4L5 10v3l-2.2 2.2 2 3.4 2.6-1.5 2 .9.6 3h4l.6-3 2-.9 2.6 1.5 2-3.4L19 13v-3l2.2-2.2-2-3.4-2.6 1.5-2-.9L14 2Z" /><circle cx="12" cy="11.5" r="3" /></>,
    eye: <><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
    expand: <path d="M8 3H3v5M16 3h5v5M3 16v5h5M21 16v5h-5" />,
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}{off && <path d="m3 3 18 18" />}</svg>;
}
function Action({ label, icon, active, danger, disabled, off, onClick }: { label: string; icon: IconName; active?: boolean; danger?: boolean; disabled?: boolean; off?: boolean; onClick: () => void }) {
  return <button className={`call-action${active ? ' active' : ''}${danger ? ' danger' : ''}`} aria-label={label} title={label} aria-pressed={active} disabled={disabled} onClick={onClick}><CallIcon name={icon} off={off} /><span className="sr-only">{label}</span></button>;
}
function Media({ stream, muted = false, video = false }: { stream: MediaStream | null; muted?: boolean; video?: boolean }) {
  const ref = useRef<HTMLVideoElement>(null);
  const [needsPlay, setNeedsPlay] = useState(false);
  useEffect(() => {
    const el = ref.current; let active = true; setNeedsPlay(false);
    if (el) { el.srcObject = stream; if (stream) void el.play().catch(() => { if (active) setNeedsPlay(true); }); }
    return () => { active = false; if (el) el.srcObject = null; };
  }, [stream]);
  return <><video ref={ref} muted={muted} autoPlay playsInline className={video ? 'call-video' : 'audio-only'} />{needsPlay && <button className="primary media-play" onClick={() => { void ref.current?.play().then(() => setNeedsPlay(false)).catch(() => {}); }}>{muted ? 'Assistir à prévia' : 'Ativar áudio'}</button>}</>;
}
interface Participant { id: string; name: string; self?: boolean; avatarUrl?: string; stream?: MediaStream | null; sharing?: boolean; }
function Stage({ title, status, participants, preferences, locked, feedback, children }: { title: string; status: string; participants: Participant[]; preferences: ScreenPreferences; locked: boolean; feedback: ScreenFeedback; children: ReactNode }) {
  const stage = useRef<HTMLElement>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [focused, setFocused] = useState<string | null>(null);
  const [controls, setControls] = useState<string | null>(null);
  const [fullscreen, setFullscreen] = useState<string | null>(null);
  const [fill, setFill] = useState(false);
  const [fullscreenError, setFullscreenError] = useState('');
  const watching = participants.some(item => item.id === focused && item.sharing);
  useEffect(() => {
    const root = stage.current;
    const changed = () => {
      const element = document.fullscreenElement;
      const id = element instanceof HTMLElement && root?.contains(element) ? element.dataset.participantId ?? null : null;
      setFullscreen(id); setControls(id); if (!id) setFill(false);
    };
    document.addEventListener('fullscreenchange', changed);
    return () => {
      document.removeEventListener('fullscreenchange', changed);
      if (document.fullscreenElement && root?.contains(document.fullscreenElement)) void document.exitFullscreen().catch(() => {});
    };
  }, []);
  useEffect(() => {
    if (fullscreen && !participants.some(item => item.id === fullscreen && item.sharing)) void document.exitFullscreen().catch(() => {});
  }, [participants, fullscreen]);
  useEffect(() => {
    if (!fullscreen || controls !== fullscreen) return;
    const timer = window.setTimeout(() => setControls(null), 4000);
    return () => clearTimeout(timer);
  }, [fullscreen, controls]);
  async function toggleFullscreen(element: HTMLElement) {
    setFullscreenError('');
    try {
      if (document.fullscreenElement === element) await document.exitFullscreen();
      else await element.requestFullscreen({ navigationUI: 'hide' });
    } catch { setFullscreenError('O navegador não permitiu tela cheia. Mantenha a página ativa e tente novamente.'); }
  }
  return <section ref={stage} className={`call-panel call-stage${title === 'Canal de chamada' ? ' voice-room' : ''}${watching ? ' watching' : ''}`} aria-label={title}>
    <header className="call-stage-header"><div><strong>{status}</strong><span>{title} · {participants.length} participantes</span></div><span className="call-connection"><span className="status-dot" /> {title === 'Chamada' && status !== 'Em chamada' ? status : 'Voz conectada'}</span></header>
    <div className="call-tiles" style={watching ? { gridTemplateRows: `repeat(${Math.max(participants.length - 1, 1)},minmax(0,1fr))` } : undefined}>{participants.map(item => <article key={item.id} data-participant-id={item.id} className={`call-tile${item.sharing ? ' streaming' : ''}${item.self && item.sharing ? ' local-preview' : ''}${watching && item.id === focused ? ' focused' : ''}${fullscreen === item.id && fill ? ' fill-video' : ''}`} aria-label={item.self && item.sharing ? 'Sua transmissão' : item.name}
      onClick={event => { if (item.sharing && event.target instanceof HTMLVideoElement) setControls(controls === item.id ? null : item.id); }}>
      <Media stream={item.stream ?? null} muted={item.self} video={item.sharing} />
      {!item.sharing && <div className="call-avatar">{item.avatarUrl ? <AppearanceImage url={item.avatarUrl} alt="" /> : item.name.slice(0, 2).toUpperCase()}</div>}
      <div className="call-tile-label"><span>{item.self && item.sharing ? 'Sua transmissão' : item.name}{item.self && !item.sharing ? ' · Você' : ''}</span>{item.sharing && <span className="live-badge">AO VIVO</span>}</div>
      {item.sharing && <button className="stream-focus" aria-label={watching && item.id === focused ? 'Voltar à grade da ligação' : item.self ? 'Ampliar sua transmissão' : `Assistir à transmissão de ${item.name}`} title="Ampliar transmissão" onClick={() => setFocused(watching && item.id === focused ? null : item.id)}><CallIcon name="expand" /></button>}
      {item.sharing && controls === item.id && <>
        <button className="stream-fullscreen" aria-label={fullscreen === item.id ? 'Sair da tela cheia' : item.self ? 'Sua transmissão em tela cheia' : `Tela cheia da transmissão de ${item.name}`} title={fullscreen === item.id ? 'Sair da tela cheia' : 'Tela cheia'} onClick={event => { void toggleFullscreen(event.currentTarget.closest('article')!); }}><CallIcon name="expand" /></button>
        {fullscreen === item.id && <button className="stream-fill" aria-pressed={fill} onClick={() => setFill(!fill)}>{fill ? 'Mostrar imagem inteira' : 'Preencher tela (corta bordas)'}</button>}
      </>}
    </article>)}</div>
    <ScreenShareFeedback feedback={feedback} />
    {fullscreenError && <p role="alert">{fullscreenError}</p>}
    <div className="call-toolbar" role="group" aria-label="Controles da ligação">{children}<Action label="Configurações da transmissão" icon="gear" onClick={() => setSettingsOpen(true)} /></div>
    {settingsOpen && <ScreenShareControls preferences={preferences} locked={locked} close={() => setSettingsOpen(false)} />}
  </section>;
}

export function DirectCallPanel({ calls, preferences, email, name, avatarUrl }: { calls: CallController; preferences: ScreenPreferences; email: string; name: string; avatarUrl?: string }) {
  const call = calls.call; if (!call) return null;
  const contact = call.caller === email ? call.callee : call.caller;
  return <Stage title="Chamada" status={calls.status} preferences={preferences} locked={calls.sharing || calls.screenStarting} feedback={calls.feedback}
    participants={[{ id: 'self', name, self: true, avatarUrl, stream: calls.preview, sharing: !!calls.preview }, { id: 'remote', name: contact, stream: calls.remote, sharing: calls.remoteSharing }]}>
    {call.state === 'RINGING' && call.callee === email ? <><button className="primary call-accept" disabled={calls.busy} onClick={() => { void calls.accept(); }}>Aceitar</button><Action label="Recusar" icon="phone" danger onClick={() => { void calls.end(true); }} /></> : <>
      {calls.audioNotice && <span className="call-audio-notice" role="status">{calls.audioNotice}</span>}
      {calls.audioBlocked && <button onClick={calls.activateAudio}>Ativar áudio da transmissão</button>}
      <Action label={calls.muted ? 'Ativar microfone' : 'Silenciar microfone'} icon="mic" off={calls.muted} active={calls.muted} disabled={call.state === 'RINGING'} onClick={calls.toggleMute} />
      <Action label={calls.sharing ? 'Parar transmissão' : 'Compartilhar tela'} icon="screen" active={calls.sharing} disabled={calls.status !== 'Em chamada' || calls.screenStarting} onClick={() => { void calls.share(); }} />
      {calls.sharing && <Action label={calls.previewVisible ? 'Ocultar prévia' : 'Mostrar prévia'} icon="eye" active={calls.previewVisible} onClick={() => calls.setPreviewVisible(!calls.previewVisible)} />}
      <Action label="Encerrar chamada" icon="phone" danger onClick={() => { void calls.end(); }} />
    </>}
  </Stage>;
}
export function VoiceRoomPanel({ rooms, preferences, email, name, avatarUrl }: { rooms: VoiceRoomController; preferences: ScreenPreferences; email: string; name: string; avatarUrl?: string }) {
  const room = rooms.room; if (!room) return null;
  const participants = room.participants.map(item => {
    const self = item.email === email, remote = rooms.remote.find(peer => peer.email === item.email);
    return { id: item.email, name: self ? name : item.username, self, avatarUrl: self ? avatarUrl : undefined, stream: self ? rooms.preview : remote?.stream, sharing: self ? !!rooms.preview : remote?.sharing };
  });
  return <Stage title="Canal de chamada" status={`${room.participants.length}/${room.capacity} participantes`} participants={participants} preferences={preferences} locked={rooms.sharing || rooms.screenStarting} feedback={rooms.feedback}>
    {rooms.audioNotice && <span className="call-audio-notice" role="status">{rooms.audioNotice}</span>}
    {rooms.audioBlocked && <button onClick={rooms.activateAudio}>Ativar áudio da transmissão</button>}
    <Action label={rooms.muted ? 'Ativar microfone do canal' : 'Silenciar microfone do canal'} icon="mic" off={rooms.muted} active={rooms.muted} onClick={rooms.toggleMute} />
    <Action label={rooms.sharing ? 'Parar transmissão do canal' : 'Compartilhar tela no canal'} icon="screen" active={rooms.sharing} disabled={rooms.screenStarting} onClick={() => { void rooms.share(); }} />
    {rooms.sharing && <Action label={rooms.previewVisible ? 'Ocultar prévia' : 'Mostrar prévia'} icon="eye" active={rooms.previewVisible} onClick={() => rooms.setPreviewVisible(!rooms.previewVisible)} />}
    <Action label="Sair do canal de chamada" icon="phone" danger onClick={() => { void rooms.leave(); }} />
  </Stage>;
}
