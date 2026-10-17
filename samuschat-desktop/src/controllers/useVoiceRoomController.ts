import { DeviceAudioTransport } from '../models/deviceAudio';
import { captureMicrophone, rememberMicrophone } from '../models/microphone';
import { useScreenFeedback } from './useScreenFeedback';
import type { ScreenPreferences } from './useScreenPreferences';
import { useEffect, useRef, useState } from 'react';
import { ApiRepository, type CallSession, type VoiceRoom } from '../models/apiRepository';
import { constrainScreen, constrainSender, captureScreen, screenShareError, preferEfficientEncoder } from '../models/screenShare';
import { gathered } from './useCallController';

interface Peer { connection: RTCPeerConnection; sender: RTCRtpSender; stream: MediaStream; control?: RTCDataChannel; sharing: boolean; email: string; }
export function useVoiceRoomController(api: ApiRepository, email: string, onError: (error: unknown) => void, preferences: ScreenPreferences) {
  const [room, setRoom] = useState<VoiceRoom | null>(null);
  const [busy, setBusy] = useState(false);
  const [muted, setMuted] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [preview, setPreview] = useState<MediaStream | null>(null);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [screenStarting, setScreenStarting] = useState(false);
  const screenProfile = useRef(preferences.profile);
  const preferencesRef = useRef(preferences); preferencesRef.current = preferences;
  const [remote, setRemote] = useState<{ id: string; email: string; stream: MediaStream; sharing: boolean }[]>([]);
  const current = useRef<VoiceRoom | null>(null);
  const peers = useRef(new Map<string, Peer>());
  const microphone = useRef<MediaStream | null>(null);
  const blank = useRef<MediaStream | null>(null);
  const screen = useRef<MediaStream | null>(null);
  const audio = useRef<DeviceAudioTransport | null>(null);
  const [audioBlocked, setAudioBlocked] = useState(false);
  const [audioNotice, setAudioNotice] = useState('');
  const [microphoneStream, setMicrophoneStream] = useState<MediaStream | null>(null);
  const ice = useRef<RTCIceServer[]>([]);
  const epoch = useRef(0);
  const active = useRef(false);
  const working = useRef(false);
  const sharingWorking = useRef(false);
  const errorRef = useRef(onError); errorRef.current = onError;
  const feedback = useScreenFeedback(sharing, preferences.automatic, screenProfile, () => screen.current?.getVideoTracks()[0], () => [...peers.current.values()].map(item => item.sender));
  const updateRemote = () => setRemote([...peers.current].map(([id, peer]) => ({ id, email: peer.email, stream: new MediaStream(peer.stream.getTracks()), sharing: peer.sharing })));
  function cleanup() {
    audio.current?.close(); audio.current = null;
    epoch.current++;
    for (const peer of peers.current.values()) peer.connection.close();
    peers.current.clear();
    for (const stream of [microphone.current, blank.current, screen.current]) stream?.getTracks().forEach(track => track.stop());
    microphone.current = blank.current = screen.current = null; current.current = null;
    if (active.current) { setMicrophoneStream(null); setAudioBlocked(false); setAudioNotice(''); setRoom(null); setRemote([]); setMuted(false); setSharing(false); setPreview(null); setPreviewVisible(false); }
  }
  async function leave() {
    const id = current.current?.channelId; cleanup();
    if (id !== undefined) try { await api.leaveCallRoom(id); } catch (problem) { if (active.current) errorRef.current(problem); }
  }
  function attach(peer: Peer, control: RTCDataChannel) {
    if (audio.current?.attach(control)) return;
    if (control.label !== 'call-control') { control.close(); return; }
    peer.control = control;
    control.onopen = () => control.send(screen.current ? 'screen:on' : 'screen:off');
    control.onmessage = event => { if (['screen:on', 'screen:off'].includes(event.data) && active.current) { peer.sharing = event.data === 'screen:on'; updateRemote(); } };
  }
  function prepare(session: CallSession): Peer {
    const existing = peers.current.get(session.id); if (existing) return existing;
    const connection = new RTCPeerConnection({ iceServers: ice.current });
    const mic = microphone.current!; connection.addTrack(mic.getAudioTracks()[0], mic);
    const video = screen.current ?? blank.current!;
    const sender = connection.addTrack(video.getVideoTracks()[0], video);
    preferEfficientEncoder(connection, sender, preferencesRef.current.hardware);
    const peer: Peer = { connection, sender, stream: new MediaStream(), sharing: false, email: session.caller === email ? session.callee : session.caller };
    peers.current.set(session.id, peer);
    connection.ondatachannel = event => attach(peer, event.channel);
    if (session.caller === email) {
      attach(peer, connection.createDataChannel('call-control'));
      attach(peer, connection.createDataChannel('device-audio', { ordered: false, maxRetransmits: 0 }));
    }
    connection.ontrack = event => { if (active.current && peers.current.get(session.id) === peer) { peer.stream.addTrack(event.track); updateRemote(); } };
    connection.onconnectionstatechange = () => {
      if (connection.connectionState === 'failed' && active.current) errorRef.current(new Error(`Mídia interrompida com ${peer.email}. Saia e entre no canal novamente.`));
      if (connection.connectionState === 'connected' && screen.current) void constrainSender(peer.sender, screenProfile.current).catch(errorRef.current);
    };
    return peer;
  }
  async function sync(snapshot: VoiceRoom, generation: number) {
    const previousSize = peers.current.size;
    current.current = snapshot; setRoom(snapshot);
    const sessions = snapshot.sessions.filter(item => !['ENDED', 'EXPIRED', 'DECLINED'].includes(item.state));
    for (const [id, peer] of peers.current) if (!sessions.some(item => item.id === id)) { peer.connection.close(); peers.current.delete(id); updateRemote(); }
    for (const session of sessions) {
      if (!active.current || epoch.current !== generation) return;
      const peer = prepare(session), connection = peer.connection;
      if (session.caller === email && !session.offer) {
        await connection.setLocalDescription(await connection.createOffer()); await gathered(connection);
        if (epoch.current !== generation) return;
        await api.callAction(session.id, 'offer', connection.localDescription!.sdp);
      } else if (session.callee === email && session.offer && !session.answer) {
        if (!connection.remoteDescription) await connection.setRemoteDescription({ type: 'offer', sdp: session.offer });
        await connection.setLocalDescription(await connection.createAnswer()); await gathered(connection);
        if (epoch.current !== generation) return;
        await api.callAction(session.id, 'answer', connection.localDescription!.sdp);
      } else if (session.caller === email && session.answer && !connection.remoteDescription) {
        await connection.setRemoteDescription({ type: 'answer', sdp: session.answer });
      }
    }
    if (screen.current && peers.current.size !== previousSize) for (const peer of peers.current.values()) {
      await constrainSender(peer.sender, screenProfile.current);
    }
  }
  useEffect(() => {
    active.current = true; let alive = true; let timer: number;
    const tick = async () => {
      if (!alive) return;
      if (!working.current && current.current) {
        working.current = true; const generation = epoch.current;
        try { const snapshot = await api.callRoom(current.current.channelId); if (alive && generation === epoch.current) await sync(snapshot, generation); }
        catch (problem) { if (alive && generation === epoch.current) { errorRef.current(problem); await leave(); } }
        finally { working.current = false; }
      }
      if (alive) timer = window.setTimeout(tick, 1500);
    };
    void tick();
    return () => { alive = false; active.current = false; clearTimeout(timer); const id = current.current?.channelId; cleanup(); if (id !== undefined) void api.leaveCallRoom(id).catch(() => {}); };
  }, [api, email]);
  async function join(id: number) {
    if (working.current || current.current) return;
    working.current = true; setBusy(true); const generation = epoch.current;
    let captured: MediaStream | undefined;
    try {
      audio.current = new DeviceAudioTransport(setAudioBlocked, setAudioNotice);
      void audio.current.activate().catch(() => setAudioBlocked(true));
      captured = await captureMicrophone();
      const config = await api.ice();
      if (!active.current || epoch.current !== generation) { captured.getTracks().forEach(track => track.stop()); return; }
      microphone.current = captured; setMicrophoneStream(captured); ice.current = config;
      const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 9; canvas.getContext('2d')?.fillRect(0, 0, 16, 9);
      blank.current = canvas.captureStream(1); blank.current.getVideoTracks()[0].enabled = false;
      const result = await api.joinCallRoom(id);
      if (!active.current || epoch.current !== generation) { await api.leaveCallRoom(id); return; }
      await sync(result, generation);
    } catch (problem) { captured?.getTracks().forEach(track => track.stop()); await leave(); if (active.current) errorRef.current(problem); }
    finally { working.current = false; if (active.current) setBusy(false); }
  }
  function toggleMute() { const track = microphone.current?.getAudioTracks()[0]; if (track) { track.enabled = !track.enabled; setMuted(!track.enabled); } }
  async function stopSharing() {
    audio.current?.stopCapture(); setAudioNotice('');
    const previous = screen.current; screen.current = null;
    previous?.getTracks().forEach(track => track.stop());
    setSharing(false); setPreview(null); setPreviewVisible(false);
    for (const peer of peers.current.values()) {
      try { await peer.sender.replaceTrack(blank.current?.getVideoTracks()[0] ?? null); } catch { /* Peer may have left. */ }
      if (peer.control?.readyState === 'open') peer.control.send('screen:off');
    }
  }
  async function share() {
    if (sharingWorking.current || !current.current) return;
    sharingWorking.current = true; let captured: MediaStream | undefined;
    try {
      if (screen.current) { await stopSharing(); return; }
      const generation = epoch.current;
      const chosen = preferences.profile; preferences.choose(chosen.height, chosen.frameRate); screenProfile.current = chosen; setScreenStarting(true);
      captured = await captureScreen(chosen);
      const track = captured.getVideoTracks()[0];
      if (!track) throw new Error('A captura não forneceu vídeo.');
      await constrainScreen(track, chosen);
      if (!active.current || epoch.current !== generation) { captured.getTracks().forEach(track => track.stop()); return; }
      screen.current = captured;
      for (const peer of peers.current.values()) {
        await peer.sender.replaceTrack(track); await constrainSender(peer.sender, screenProfile.current);
        if (peer.control?.readyState === 'open') peer.control.send('screen:on');
      }
      try {
        const sending = await audio.current?.startCapture(captured);
        if (active.current && epoch.current === generation) setAudioNotice(sending ? 'Áudio da transmissão ativado' : 'Esta fonte veio sem áudio. No seletor, marque Compartilhar áudio ou escolha uma aba com som.');
      } catch { if (active.current) setAudioNotice('Não foi possível enviar o áudio da tela. Pare e selecione outra fonte com áudio.'); }
      if (!active.current || epoch.current !== generation || screen.current !== captured) return;
      track.onended = () => { if (screen.current?.getVideoTracks()[0] === track) void stopSharing(); };
      setSharing(true); setPreview(captured); setPreviewVisible(true);
    } catch (problem) { captured?.getTracks().forEach(track => track.stop()); await stopSharing(); if (active.current && !(problem instanceof DOMException && problem.name === 'NotAllowedError')) errorRef.current(screenShareError(problem)); }
    finally { sharingWorking.current = false; if (active.current) setScreenStarting(false); }
  }
  async function changeMicrophone(id: string) {
    const generation = epoch.current, previous = microphone.current;
    if (!previous) { rememberMicrophone(id); return; }
    const next = await captureMicrophone(id);
    if (generation !== epoch.current || microphone.current !== previous) { next.getTracks().forEach(track => track.stop()); return; }
    const track = next.getAudioTracks()[0]; track.enabled = previous.getAudioTracks()[0].enabled;
    const changed: RTCRtpSender[] = [];
    try {
      for (const peer of peers.current.values()) {
        const sender = peer.connection.getSenders().find(item => item.track?.kind === 'audio');
        if (sender) { await sender.replaceTrack(track); changed.push(sender); }
      }
    } catch (problem) {
      await Promise.allSettled(changed.map(sender => sender.replaceTrack(previous.getAudioTracks()[0])));
      next.getTracks().forEach(track => track.stop()); throw problem;
    }
    if (generation !== epoch.current) { next.getTracks().forEach(track => track.stop()); return; }
    microphone.current = next; setMicrophoneStream(next); rememberMicrophone(id); previous.getTracks().forEach(track => track.stop());
  }
  const activateAudio = () => { void audio.current?.activate().catch(() => setAudioBlocked(true)); };
  return { microphoneStream, changeMicrophone, audioBlocked, audioNotice, activateAudio, feedback, screenStarting, room, busy, muted, sharing, remote, preview: previewVisible ? preview : null, previewVisible, setPreviewVisible, join, leave, toggleMute, share };
}
export type VoiceRoomController = ReturnType<typeof useVoiceRoomController>;
