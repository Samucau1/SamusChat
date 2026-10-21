import { useEffect, useState } from 'react';

/** Measures audio only; never connects the microphone to the speakers. */
export function useSpeaking(stream?: MediaStream | null, muted = false) {
  const [speaking, setSpeaking] = useState(false);
  useEffect(() => {
    setSpeaking(false);
    const tracks = stream?.getAudioTracks().filter(track => track.readyState === 'live') ?? [];
    if (muted || !tracks.length) return;
    const context = new AudioContext();
    const source = context.createMediaStreamSource(new MediaStream(tracks));
    const analyser = context.createAnalyser(); analyser.fftSize = 1024;
    source.connect(analyser);
    const samples = new Float32Array(analyser.fftSize);
    let lastVoice = -Infinity;
    const resume = () => { void context.resume().catch(() => {}); };
    resume(); window.addEventListener('pointerdown', resume);
    const timer = window.setInterval(() => {
      analyser.getFloatTimeDomainData(samples);
      const rms = Math.sqrt(samples.reduce((sum, sample) => sum + sample * sample, 0) / samples.length);
      const live = tracks.some(track => track.enabled && !track.muted && track.readyState === 'live');
      if (live && rms >= 0.02) lastVoice = performance.now();
      setSpeaking(live && performance.now() - lastVoice < 400);
    }, 100);
    return () => { clearInterval(timer); window.removeEventListener('pointerdown', resume); source.disconnect(); analyser.disconnect(); void context.close().catch(() => {}); };
  }, [stream, muted]);
  return speaking;
}
