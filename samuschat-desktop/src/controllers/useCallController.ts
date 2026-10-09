import { useEffect, useRef, useState } from 'react';
import { ApiRepository, type CallSession } from '../models/apiRepository';

const terminal = (state: string) => ['ENDED', 'DECLINED', 'EXPIRED'].includes(state);
async function gathered(peer: RTCPeerConnection) {
  if (peer.iceGatheringState === 'complete') return;
  await new Promise<void>((resolve, reject) => {
    const finish = () => { clearTimeout(timer); peer.removeEventListener('icegatheringstatechange', changed); peer.removeEventListener('connectionstatechange', closed); };
    const changed = () => { if (peer.iceGatheringState === 'complete') { finish(); resolve(); } };
    const closed = () => { if (peer.connectionState === 'closed') { finish(); reject(new Error('Chamada encerrada.')); } };
    const timer = setTimeout(() => { finish(); reject(new Error('Não foi possível encontrar um caminho de mídia. Verifique a configuração TURN.')); }, 15000);
    peer.addEventListener('icegatheringstatechange', changed); peer.addEventListener('connectionstatechange', closed); changed();
  });
}
export function useCallController(api: ApiRepository, email: string, onError: (error: unknown) => void) {
  const [call, setCall] = useState<CallSession | null>(null);
  const [status, setStatus] = useState('');
  const [muted, setMuted] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [remoteSharing, setRemoteSharing] = useState(false);
  const [remote, setRemote] = useState<MediaStream | null>(null);
  const [preview, setPreview] = useState<MediaStream | null>(null);
  const [busy, setBusy] = useState(false);
  const ref = useRef<CallSession | null>(null);
  const peer = useRef<RTCPeerConnection | null>(null);
  const microphone = useRef<MediaStream | null>(null);
  const screen = useRef<MediaStream | null>(null);
  const placeholder = useRef<MediaStream | null>(null);
  const videoSender = useRef<RTCRtpSender | null>(null);
  const control = useRef<RTCDataChannel | null>(null);
  const alive = useRef(true);
  const generation = useRef(0);
  const working = useRef(false);
  const errorRef = useRef(onError); errorRef.current = onError;
  function update(value: CallSession | null) { ref.current = value; if (alive.current) setCall(value); }
  function cleanup() {
    generation.current++; peer.current?.close(); peer.current = null;
    for (const stream of [microphone.current, screen.current, placeholder.current]) stream?.getTracks().forEach(track => track.stop());
    microphone.current = screen.current = placeholder.current = null; videoSender.current = null; control.current = null;
    if (alive.current) { setRemote(null); setPreview(null); setSharing(false); setRemoteSharing(false); setMuted(false); setStatus(''); }
  }
  function attach(channel: RTCDataChannel) {
    if (channel.label !== 'call-control') { channel.close(); return; }
    control.current = channel;
    channel.onmessage = event => { if (alive.current && control.current === channel && ['screen:on', 'screen:off'].includes(event.data)) setRemoteSharing(event.data === 'screen:on'); };
    channel.onopen = () => channel.send(screen.current ? 'screen:on' : 'screen:off');
  }
  async function prepare(caller: boolean) {
    if (peer.current) return peer.current;
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Chamadas exigem HTTPS ou localhost e permissão para o microfone.');
    const currentGeneration = generation.current;
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false });
    if (!alive.current || currentGeneration !== generation.current) { stream.getTracks().forEach(track => track.stop()); throw new Error('Chamada encerrada.'); }
    microphone.current = stream;
    const iceServers = await api.ice();
    if (!alive.current || currentGeneration !== generation.current) throw new Error('Chamada encerrada.');
    const connection = new RTCPeerConnection({ iceServers }); peer.current = connection;
    connection.addTrack(stream.getAudioTracks()[0], stream);
    // Negotiate a video track up front, as the Android client does, so sharing
    // can replace the track without changing the one-off SDP API contract.
    const canvas = document.createElement('canvas'); canvas.width = 1280; canvas.height = 720;
    const context = canvas.getContext('2d')!; context.fillStyle = '#1e1f22'; context.fillRect(0, 0, canvas.width, canvas.height);
    const blank = canvas.captureStream(1); placeholder.current = blank;
    videoSender.current = connection.addTrack(blank.getVideoTracks()[0], blank);
    connection.ondatachannel = event => attach(event.channel);
    if (caller) attach(connection.createDataChannel('call-control'));
    connection.ontrack = event => {
      if (!alive.current || peer.current !== connection) return;
      setRemote(previous => { const value = previous || new MediaStream(); if (!value.getTracks().some(track => track.id === event.track.id)) value.addTrack(event.track); return new MediaStream(value.getTracks()); });
    };
    connection.onconnectionstatechange = () => {
      if (!alive.current || peer.current !== connection) return;
      setStatus(connection.connectionState === 'connected' ? 'Em chamada' : connection.connectionState === 'disconnected' ? 'Conexão interrompida' : 'Conectando');
      if (connection.connectionState === 'failed') { errorRef.current(new Error('A conexão de mídia falhou. Encerre e tente novamente.')); void end(); }
    };
    return connection;
  }
  async function progress(value: CallSession) {
    if (terminal(value.state)) { cleanup(); update(null); return; }
    update(value);
    if (value.state === 'RINGING') { setStatus(value.caller === email ? 'Chamando…' : 'Chamada recebida'); return; }
    const currentGeneration = generation.current;
    if (value.caller === email && !value.offer) {
      const connection = await prepare(true);
      await connection.setLocalDescription(await connection.createOffer()); await gathered(connection);
      if (generation.current !== currentGeneration || !alive.current) return;
      const next = await api.callAction(value.id, 'offer', connection.localDescription!.sdp);
      if (generation.current === currentGeneration && alive.current) update(next);
    } else if (value.callee === email && value.offer && !value.answer) {
      const connection = await prepare(false);
      await connection.setRemoteDescription({ type: 'offer', sdp: value.offer });
      await connection.setLocalDescription(await connection.createAnswer()); await gathered(connection);
      if (generation.current !== currentGeneration || !alive.current) return;
      const next = await api.callAction(value.id, 'answer', connection.localDescription!.sdp);
      if (generation.current === currentGeneration && alive.current) update(next);
    } else if (value.caller === email && value.answer && peer.current && !peer.current.remoteDescription) {
      await peer.current.setRemoteDescription({ type: 'answer', sdp: value.answer });
    }
  }
  useEffect(() => {
    alive.current = true; let active = true; let timer: number;
    const tick = async () => {
      if (!active) return;
      if (!working.current) {
        working.current = true;
        const epoch = generation.current;
        try { const value = ref.current ? await api.call(ref.current.id) : (await api.calls())[0]; if (active && generation.current === epoch && value) await progress(value); }
        catch (problem) { if (active) { errorRef.current(problem); if (ref.current) { const id = ref.current.id; cleanup(); update(null); void api.callAction(id, 'end').catch(() => {}); } } }
        finally { working.current = false; }
      }
      if (active) timer = window.setTimeout(tick, 1500);
    };
    void tick();
    return () => { active = false; alive.current = false; clearTimeout(timer); const id = ref.current?.id; cleanup(); if (id) void api.callAction(id, 'end').catch(() => {}); };
  }, [api, email]);
  async function action(operation: () => Promise<void>) {
    setBusy(true);
    while (working.current && alive.current) await new Promise(resolve => setTimeout(resolve, 25));
    if (!alive.current) return;
    working.current = true;
    try { await operation(); }
    catch (problem) { const id = ref.current?.id; cleanup(); update(null); if (id) void api.callAction(id, 'end').catch(() => {}); errorRef.current(problem); }
    finally { working.current = false; if (alive.current) setBusy(false); }
  }
  async function invite(contact: string) { await action(async () => { const epoch = generation.current; await prepare(true); const value = await api.invite(contact); if (!alive.current || generation.current !== epoch) { await api.callAction(value.id, 'end'); return; } update(value); setStatus('Chamando…'); }); }
  async function accept() { const value = ref.current; if (!value) return; await action(async () => { const epoch = generation.current; await prepare(false); const next = await api.callAction(value.id, 'accept'); if (alive.current && generation.current === epoch) { update(next); setStatus('Conectando'); } }); }
  async function end(decline = false) {
    const id = ref.current?.id; cleanup(); update(null);
    if (id) try { await api.callAction(id, decline ? 'decline' : 'end'); } catch (problem) { errorRef.current(problem); }
  }
  function toggleMute() { const track = microphone.current?.getAudioTracks()[0]; if (track) { track.enabled = !track.enabled; setMuted(!track.enabled); } }
  async function stopSharing() {
    screen.current?.getTracks().forEach(track => track.stop()); screen.current = null;
    await videoSender.current?.replaceTrack(placeholder.current?.getVideoTracks()[0] || null);
    if (control.current?.readyState === 'open') control.current.send('screen:off');
    setSharing(false); setPreview(null);
  }
  async function share() {
    if (screen.current) { await stopSharing(); return; }
    try {
      const sender = videoSender.current; const currentGeneration = generation.current;
      if (!sender) return;
      const stream = await navigator.mediaDevices.getDisplayMedia({ video: { width: 1280, height: 720, frameRate: 15 }, audio: false });
      if (generation.current !== currentGeneration) { stream.getTracks().forEach(track => track.stop()); return; }
      screen.current = stream; await sender.replaceTrack(stream.getVideoTracks()[0]);
      stream.getVideoTracks()[0].onended = () => { void stopSharing(); };
      if (control.current?.readyState === 'open') control.current.send('screen:on');
      setSharing(true); setPreview(stream);
    } catch (problem) { screen.current?.getTracks().forEach(track => track.stop()); screen.current = null; errorRef.current(problem); }
  }
  return { call, status, muted, sharing, remoteSharing, remote, preview, busy, invite, accept, end, toggleMute, share };
}
export type CallController = ReturnType<typeof useCallController>;
