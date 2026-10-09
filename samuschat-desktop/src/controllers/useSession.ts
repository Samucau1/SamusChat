import { useEffect, useState } from 'react';
import { ApiError, ApiRepository, type User } from '../models/apiRepository';
const key = 'samuschat.desktop.session';
export function useSession() {
  const [api] = useState(() => new ApiRepository(sessionStorage.getItem(key) || ''));
  const [user, setUser] = useState<User | null>(null);
  const [restoring, setRestoring] = useState(Boolean(api.token));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function logout() { api.token = ''; sessionStorage.removeItem(key); setUser(null); setError(''); }
  function handleError(problem: unknown) {
    if (problem instanceof ApiError && problem.status === 401 && user) logout();
    setError(problem instanceof Error ? problem.message : 'Ocorreu um erro. Tente novamente.');
  }
  useEffect(() => {
    let alive = true;
    if (api.token) api.profile().then(value => { if (alive) setUser(value); }).catch(problem => {
      if (!alive) return;
      if (problem instanceof ApiError && problem.status === 401) { api.token = ''; sessionStorage.removeItem(key); }
      setError(problem instanceof Error ? problem.message : 'Falha ao restaurar a sessão.');
    }).finally(() => { if (alive) setRestoring(false); });
    return () => { alive = false; };
  }, [api]);
  async function authenticate(register: boolean, name: string, email: string, password: string) {
    if (busy) return; setBusy(true); setError('');
    try {
      const result = register ? await api.register(name.trim(), email.trim(), password) : await api.login(email.trim(), password);
      api.token = result.token; const profile = await api.profile();
      sessionStorage.setItem(key, result.token); setUser(profile);
    } catch (problem) { api.token = ''; sessionStorage.removeItem(key); handleError(problem); }
    finally { setBusy(false); }
  }
  return { api, user, setUser, restoring, busy, error, setError, handleError, authenticate, logout };
}
