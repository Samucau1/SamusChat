import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

export type ThemeName = 'purple' | 'black-green' | 'blue' | 'black-orange';
const storageKey = 'samuschat.web.theme';
function readTheme(): ThemeName {
  try {
    const value = localStorage.getItem(storageKey);
    return value === 'black-green' || value === 'blue' || value === 'black-orange' ? value : 'purple';
  }
  catch { return 'purple'; }
}
function applyTheme(theme: ThemeName) {
  document.documentElement.dataset.theme = theme;
}
applyTheme(readTheme());

const ThemeContext = createContext<{
  theme: ThemeName; setTheme(theme: ThemeName): void; saved: boolean;
} | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, updateTheme] = useState<ThemeName>(readTheme);
  const [saved, setSaved] = useState(true);
  function setTheme(next: ThemeName) {
    applyTheme(next);
    updateTheme(next);
    try { localStorage.setItem(storageKey, next); setSaved(true); }
    catch { setSaved(false); }
  }
  useEffect(() => {
    const sync = (event: StorageEvent) => {
      if (event.key !== storageKey && event.key !== null) return;
      const next = readTheme();
      applyTheme(next); updateTheme(next); setSaved(true);
    };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);
  return <ThemeContext.Provider value={{ theme, setTheme, saved }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const theme = useContext(ThemeContext);
  if (!theme) throw new Error('ThemeProvider is required');
  return theme;
}
