import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { constrainScreen, constrainSender, encoderSample, lowerScreenProfile, screenMetrics, screenUnderPressure, type EncoderSample, type ScreenMetrics, type ScreenProfile } from '../models/screenShare';
export function useScreenFeedback(active: boolean, automatic: boolean, profile: MutableRefObject<ScreenProfile>, getTrack: () => MediaStreamTrack | undefined, getSenders: () => RTCRtpSender[]) {
  const [feedback, setFeedback] = useState<{ profile: ScreenProfile; metrics?: ScreenMetrics; notice?: string } | null>(null);
  const options = useRef({ automatic, getTrack, getSenders }); options.current = { automatic, getTrack, getSenders };
  useEffect(() => {
    if (!active) { setFeedback(null); return; }
    let alive = true, timer: number, pressure = 0, cooldown = 0;
    let previous = new Map<RTCRtpSender, EncoderSample>();
    setFeedback({ profile: profile.current });
    const tick = async () => {
      if (!alive) return;
      try {
        const senders = options.current.getSenders();
        const reports = await Promise.allSettled(senders.map(async sender => ({ sender, sample: encoderSample(await sender.getStats()) })));
        if (!alive) return;
        const samples: ScreenMetrics[] = [], next = new Map<RTCRtpSender, EncoderSample>();
        for (const result of reports) if (result.status === 'fulfilled' && result.value.sample) {
          const { sender, sample } = result.value;
          samples.push(screenMetrics(sample, previous.get(sender))); next.set(sender, sample);
        }
        previous = next;
        const overloaded = samples.find(item => screenUnderPressure(item, profile.current));
        const metrics = overloaded ?? samples.find(item => item.fps !== undefined) ?? samples[0];
        pressure = overloaded ? pressure + 1 : 0;
        const lower = lowerScreenProfile(profile.current);
        let notice: string | undefined;
        if (pressure >= 3) {
          if (!lower) notice = 'Limite mínimo de 750p/30 fps atingido. A máquina ou a rede ainda limitam a transmissão.';
          else if (!options.current.automatic) notice = 'A máquina ou a rede estão limitando a transmissão. Escolha uma qualidade menor ou ative o ajuste automático.';
          else if (Date.now() >= cooldown) {
            const track = options.current.getTrack(), before = profile.current;
            if (track?.readyState === 'live') {
              try {
                await constrainScreen(track, lower);
                if (!alive) return;
                for (const sender of options.current.getSenders()) await constrainSender(sender, lower);
                profile.current = lower;
                notice = `Ajuste automático para ${lower.height}p/${lower.frameRate} fps por limitação ${overloaded?.reason === 'bandwidth' ? 'da rede' : 'de desempenho'}.`;
              } catch {
                if (!alive) return;
                try { await constrainScreen(track, before); for (const sender of options.current.getSenders()) await constrainSender(sender, before); } catch { /* Native capture may have ended. */ }
                notice = 'Esta fonte não permitiu o ajuste automático. Pare a transmissão e escolha uma qualidade menor.';
              }
              cooldown = Date.now() + 10000; pressure = 0;
            }
          }
        }
        if (alive) setFeedback(current => ({ profile: profile.current, metrics, notice: notice ?? current?.notice }));
      } catch { /* Missing diagnostics do not interrupt media. */ }
      finally { if (alive) timer = window.setTimeout(tick, 2000); }
    };
    timer = window.setTimeout(tick, 2000);
    return () => { alive = false; clearTimeout(timer); previous.clear(); };
  }, [active, profile]);
  return feedback;
}
