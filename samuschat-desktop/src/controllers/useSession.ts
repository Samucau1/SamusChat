import { useEffect, useRef, useState } from 'react';
import { ApiError, ApiRepository, type User } from '../models/apiRepository';
import {clearPushAfterLogout} from '../models/browserPush';
import { loadSession, saveSession } from '../models/savedSession';
export function useSession() {
  const [saved] = useState(loadSession);
  const userRef = useRef(saved.user ?? null);
  const [api] = useState(() => new ApiRepository(saved.token, saved.refreshToken ?? '',
    value => saveSession({ ...value, user: userRef.current })));
  const [user, setUser] = useState<User | null>(saved.user ?? null);
  userRef.current = user;
  const [restoring, setRestoring] = useState(Boolean(api.token));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function clearSession() { userRef.current = null; api.remember(''); setUser(null); setError(''); }
  async function logout() {
    if (busy) return; setBusy(true);
    try {
      await api.logout();
      const owner=user?.publicId??user?.email;
      if(owner)await clearPushAfterLogout(owner).catch(()=>{});
      clearSession();
    }
    catch (problem) { handleError(problem); }
    finally { setBusy(false); }
  }
  function handleError(problem: unknown) {
    if (problem instanceof ApiError && problem.status === 401) clearSession();
    setError(problem instanceof Error ? problem.message : 'Ocorreu um erro. Tente novamente.');
  }
  useEffect(() => {
    if (api.token && user) saveSession({ token: api.token, refreshToken: api.refreshToken, user });
  }, [api, user]);
  useEffect(() => {
    let alive = true;
    if (api.token) api.profile().then(value => {
      if (alive) {
        setUser(value);
        void api.rememberSession().catch(() => {});
      }
    }).catch(problem => {
      if (!alive) return;
      if (problem instanceof ApiError && problem.status === 401) clearSession();
      setError(problem instanceof Error ? problem.message : 'Falha ao restaurar a sessão.');
    }).finally(() => { if (alive) setRestoring(false); });
    return () => { alive = false; };
  }, [api]);
  async function authenticate(register: boolean, name: string, email: string, password: string) {
    if (busy) return; setBusy(true); setError('');
    try {
      const result = register ? await api.register(name.trim(), email.trim(), password) : await api.login(email.trim(), password);
      api.remember(result.token, result.refreshToken); const profile = await api.profile();
      setUser(profile);
      void api.rememberSession().catch(() => {});
    } catch (problem) { handleError(problem); }
    finally { setBusy(false); }
  }
  async function reconnect() {
    if (busy) return;
    setBusy(true); setError('');
    try { setUser(await api.profile()); await api.rememberSession(); }
    catch (problem) { handleError(problem); }
    finally { setBusy(false); }
  }
  return { api, user, setUser, restoring, busy, error, setError, handleError, authenticate, logout, reconnect };
}
