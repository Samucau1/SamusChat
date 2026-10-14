import { useEffect, useRef, useState } from 'react';
import { captureMicrophone, rememberMicrophone, selectedMicrophone } from '../models/microphone';

export function MicrophoneSettings({ stream, change }: { stream: MediaStream | null; change: (id: string) => Promise<void> }) {
  const [open, setOpen] = useState(false);
  return <><button type="button" onClick={() => setOpen(true)}>Microfone</button>{open && <MicrophoneDialog stream={stream} change={change} close={() => setOpen(false)} />}</>;
}
function MicrophoneDialog({ stream, change, close }: { stream: MediaStream | null; change: (id: string) => Promise<void>; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const owned = useRef<MediaStream | null>(null);
  const alive = useRef(true);
  const [testing, setTesting] = useState<MediaStream | null>(null);
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [selected, setSelected] = useState(selectedMicrophone);
  const [level, setLevel] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const current = stream || testing;
  async function list() {
    const inputs = (await navigator.mediaDevices.enumerateDevices()).filter(device => device.kind === 'audioinput');
    if (alive.current) setDevices(inputs);
  }
  useEffect(() => {
    alive.current = true; dialog.current?.showModal();
    const update = () => { void list().catch(() => {}); };
    update(); navigator.mediaDevices?.addEventListener('devicechange', update);
    return () => { alive.current = false; navigator.mediaDevices?.removeEventListener('devicechange', update); owned.current?.getTracks().forEach(track => track.stop()); };
  }, []);
  useEffect(() => {
    if (!current) return;
    const context = new AudioContext(), source = context.createMediaStreamSource(current), analyser = context.createAnalyser();
    analyser.fftSize = 1024; source.connect(analyser); const samples = new Float32Array(analyser.fftSize);
    const timer = window.setInterval(() => {
      analyser.getFloatTimeDomainData(samples);
      const rms = Math.sqrt(samples.reduce((sum, value) => sum + value * value, 0) / samples.length);
      setLevel(Math.min(100, Math.round(rms * 500)));
    }, 100);
    void context.resume().catch(() => {});
    return () => { clearInterval(timer); source.disconnect(); void context.close(); };
  }, [current]);
  async function test() {
    setBusy(true); setError('');
    try {
      if (!stream) {
        const next = await captureMicrophone(selected);
        if (!alive.current) { next.getTracks().forEach(track => track.stop()); return; }
        owned.current?.getTracks().forEach(track => track.stop()); owned.current = next; setTesting(next);
      }
      await list();
    } catch (problem) { if (alive.current) setError(problem instanceof Error ? problem.message : 'Não foi possível testar o microfone.'); }
    finally { if (alive.current) setBusy(false); }
  }
  async function choose(id: string) {
    setBusy(true); setError('');
    try {
      await change(id);
      if (!alive.current) return;
      rememberMicrophone(id); setSelected(id);
      if (!stream && owned.current) {
        const next = await captureMicrophone(id);
        if (!alive.current) { next.getTracks().forEach(track => track.stop()); return; }
        owned.current.getTracks().forEach(track => track.stop()); owned.current = next; setTesting(next);
      }
    } catch (problem) { if (alive.current) setError(problem instanceof Error ? problem.message : 'Não foi possível trocar o microfone.'); }
    finally { if (alive.current) setBusy(false); }
  }
  return <dialog ref={dialog} aria-labelledby="microphone-title" onCancel={close} className="microphone-dialog">
    <h2 id="microphone-title">Microfone do PC</h2>
    <label>Entrada de áudio<select aria-label="Entrada de áudio" value={selected} disabled={busy} onChange={event => { void choose(event.target.value); }}>
      <option value="">Padrão do Windows</option>
      {selected && !devices.some(device => device.deviceId === selected) && <option value={selected}>Entrada salva indisponível</option>}
      {devices.map((device, index) => <option key={device.deviceId || index} value={device.deviceId}>{device.label || `Microfone ${index + 1}`}</option>)}
    </select></label>
    <button type="button" disabled={busy} onClick={() => { void test(); }}>{stream ? 'Atualizar entradas' : 'Testar microfone'}</button>
    <p>Fale e confira o nível de entrada. A troca durante a chamada preserva o estado de silêncio.</p>
    <meter aria-label="Nível do microfone" min={0} max={100} value={level} />
    <p role="status">{current ? current.getAudioTracks()[0]?.enabled === false ? 'Microfone silenciado na chamada' : level > 0 ? 'Voz detectada' : 'Sem sinal agora. Fale ou escolha outra entrada.' : 'Clique em Testar microfone para permitir e listar as entradas.'}</p>
    {error && <p role="alert">{error}</p>}
    <p>Se não houver sinal, confira o microfone em Sistema → Som → Entrada e a permissão do navegador no Windows.</p>
    <button type="button" onClick={close}>Concluído</button>
  </dialog>;
}
