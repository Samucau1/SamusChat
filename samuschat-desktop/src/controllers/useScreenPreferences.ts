import { useEffect, useRef, useState } from 'react';
import { desktopScreenProfile, probeScreenHardware, screenProfile, type ScreenHardware } from '../models/screenShare';
const key = 'samuschat.desktop.screenPreferences';
function savedPreferences() {
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (saved) return { profile: screenProfile(saved.height, saved.frameRate), automatic: saved.automatic !== false, customized: true };
  } catch { /* Ignore invalid or unsupported settings. */ }
  return { profile: desktopScreenProfile, automatic: true, customized: false };
}
export function useScreenPreferences() {
  const [initial] = useState(savedPreferences);
  const [profile, setProfile] = useState(initial.profile);
  const [automatic, setAutomaticState] = useState(initial.automatic);
  const [hardware, setHardware] = useState<ScreenHardware | null>(null);
  const customized = useRef(initial.customized);
  useEffect(() => {
    let alive = true;
    void probeScreenHardware().then(result => { if (alive) { setHardware(result); if (!customized.current) setProfile(result.recommendation); } }).catch(() => {});
    return () => { alive = false; };
  }, []);
  function persist(height: number, frameRate: number, auto: boolean) {
    try { localStorage.setItem(key, JSON.stringify({ height, frameRate, automatic: auto })); } catch { /* Selection still works when storage is unavailable. */ }
  }
  function choose(height: number, frameRate: number) {
    const next = screenProfile(height, frameRate); customized.current = true; setProfile(next); persist(height, frameRate, automatic);
  }
  function setAutomatic(value: boolean) { customized.current = true; setAutomaticState(value); persist(profile.height, profile.frameRate, value); }
  return { profile, automatic, hardware, choose, setAutomatic };
}
export type ScreenPreferences = ReturnType<typeof useScreenPreferences>;
