import { useEffect, useRef, useState } from 'react';
import type { ApiRepository } from '../models/apiRepository';

type GoogleIdentity = {
  initialize(options: { client_id: string; callback(response: { credential: string }): void; auto_select: boolean }): void;
  renderButton(element: HTMLElement, options: Record<string, string | number>): void;
};
let loading: Promise<GoogleIdentity> | undefined;
let initialized = '';
let receive: ((credential: string) => void) | undefined;

function loadGoogle(): Promise<GoogleIdentity> {
  return loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.onload = () => {
      const sdk = (window as unknown as { google?: { accounts?: { id?: GoogleIdentity } } }).google?.accounts?.id;
      if (sdk) resolve(sdk); else { loading = undefined; reject(new Error('Login Google indisponível. Use email e senha.')); }
    };
    script.onerror = () => { loading = undefined; script.remove(); reject(new Error('Não foi possível carregar o Google. Use email e senha.')); };
    document.head.append(script);
  });
}

export function GoogleSignIn({ api, busy, onCredential, onError }: {
  api: ApiRepository; busy: boolean; onCredential(credential: string): void; onError(problem: unknown): void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const latest = useRef({ busy, onCredential, onError });
  latest.current = { busy, onCredential, onError };
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    let alive = true;
    const callback = (credential: string) => { if (alive && !latest.current.busy) latest.current.onCredential(credential); };
    void api.googleConfig().then(async ({ clientId }) => {
      if (!alive || !clientId) return;
      setEnabled(true);
      const sdk = await loadGoogle();
      if (!alive || !host.current) return;
      receive = callback;
      if (initialized !== clientId) {
        sdk.initialize({ client_id: clientId, auto_select: false, callback: response => receive?.(response.credential) });
        initialized = clientId;
      }
      sdk.renderButton(host.current, { type: 'standard', theme: 'outline', size: 'large', text: 'signin_with', locale: 'pt-BR', width: 280 });
    }).catch(problem => { if (alive) latest.current.onError(problem); });
    return () => { alive = false; if (receive === callback) receive = undefined; };
  }, [api]);
  return <div hidden={!enabled} aria-busy={busy} style={{ pointerEvents: busy ? 'none' : undefined }}>
    <p className="muted">Ou entre com sua conta Google</p><div ref={host} />
    <p className="muted">Se sua conta existente usa email externo ao Google, preencha a senha atual antes de continuar.</p>
  </div>;
}
