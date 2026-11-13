import type { User } from './apiRepository';

const key = 'samuschat.desktop.session';
export interface SavedSession { token: string; refreshToken?: string; user?: User | null }

export function loadSession(): SavedSession {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (typeof value?.token === 'string' && value.token) return value;
  } catch { /* Older sessions can still be recovered from this tab. */ }
  try { return { token: sessionStorage.getItem(key) ?? '' }; }
  catch { return { token: '' }; }
}

export function saveSession(value: SavedSession) {
  try {
    if (value.token) localStorage.setItem(key, JSON.stringify(value));
    else localStorage.removeItem(key);
    sessionStorage.removeItem(key);
  } catch {
    // Keep the current tab usable when persistent browser storage is unavailable.
    try {
      if (value.token) sessionStorage.setItem(key, value.token);
      else sessionStorage.removeItem(key);
    } catch { /* The in-memory session remains usable. */ }
  }
}
