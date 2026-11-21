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
    let resize: ResizeObserver | undefined;
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
      const element = host.current;
      let previousWidth = 0;
      const render = () => {
        const width = Math.min(400, Math.floor(element.getBoundingClientRect().width));
        if (!alive || width < 1 || width === previousWidth) return;
        previousWidth = width;
        element.replaceChildren();
        sdk.renderButton(element, { type: 'standard', theme: 'filled_black', size: 'large', shape: 'rectangular', text: 'continue_with', locale: 'pt-BR', width });
      };
      render();
      resize = new ResizeObserver(render);
      resize.observe(element);
    }).catch(problem => { if (alive) latest.current.onError(problem); });
    return () => { alive = false; resize?.disconnect(); if (receive === callback) receive = undefined; };
  }, [api]);
  return <div className="google-auth" hidden={!enabled} aria-busy={busy} style={{ pointerEvents: busy ? 'none' : undefined }}>
    <div className="google-auth-divider"><span>ou continue com</span></div>
    <div className="google-auth-button" ref={host} />
    <details className="google-auth-help"><summary>Já tem uma conta com outro email?</summary>
      <p>Se o email da sua conta Google não é Gmail nem de uma organização Google, preencha a senha atual acima para vincular sua conta.</p>
    </details>
  </div>;
}
