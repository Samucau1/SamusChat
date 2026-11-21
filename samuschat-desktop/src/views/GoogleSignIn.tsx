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
  const dialog = useRef<HTMLDialogElement>(null);
  const latest = useRef({ busy, onCredential, onError });
  latest.current = { busy, onCredential, onError };
  const [enabled, setEnabled] = useState(false);
  const [problem, setProblem] = useState('');
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let alive = true;
    let resize: ResizeObserver | undefined;
    const callback = (credential: string) => {
      if (alive && !latest.current.busy) { dialog.current?.close(); latest.current.onCredential(credential); }
    };
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
      setReady(true);
    }).catch(error => { if (alive) { setProblem(error instanceof Error ? error.message : 'Não foi possível carregar o Google.'); latest.current.onError(error); } });
    return () => { alive = false; resize?.disconnect(); if (receive === callback) receive = undefined; };
  }, [api]);
  return <div className="google-auth" hidden={!enabled} aria-busy={busy} style={{ pointerEvents: busy ? 'none' : undefined }}>
    <div className="google-auth-divider"><span>ou continue com</span></div>
    <button type="button" className="google-auth-trigger" disabled={busy} aria-haspopup="dialog" onClick={() => dialog.current?.showModal()}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.39-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-2 3.01v2.5h3.24c1.9-1.75 2.98-4.32 2.98-7.34Z"/><path fill="#34A853" d="M12 22c2.7 0 4.96-.9 6.62-2.43l-3.24-2.5c-.9.6-2.05.97-3.38.97-2.6 0-4.8-1.76-5.59-4.12H3.07v2.59A10 10 0 0 0 12 22Z"/><path fill="#FBBC05" d="M6.41 13.92A6 6 0 0 1 6.1 12c0-.67.11-1.32.31-1.92V7.49H3.07A10 10 0 0 0 2 12c0 1.61.38 3.14 1.07 4.51l3.34-2.59Z"/><path fill="#EA4335" d="M12 5.96c1.47 0 2.79.5 3.83 1.5l2.87-2.87A9.62 9.62 0 0 0 12 2a10 10 0 0 0-8.93 5.49l3.34 2.59C7.2 7.72 9.4 5.96 12 5.96Z"/></svg>
      <span>Continuar com Google</span><span className="google-auth-arrow" aria-hidden="true">↗</span>
    </button>
    <dialog ref={dialog} className="google-auth-dialog" aria-labelledby="google-auth-title" onClick={event => { if (event.target === event.currentTarget) dialog.current?.close(); }}>
      <div className="google-auth-dialog-header"><h2 id="google-auth-title">Continuar com Google</h2><button type="button" aria-label="Fechar login Google" onClick={() => dialog.current?.close()}>×</button></div>
      <p>Escolha sua conta para entrar no SamusChat.</p>
      {problem && <p className="error-notice" role="alert">{problem}</p>}
      {!problem && !ready && <p role="status">Carregando Google…</p>}
      <div className="google-auth-button" ref={host} />
    </dialog>
    <details className="google-auth-help"><summary>Já tem uma conta com outro email?</summary>
      <p>Se o email da sua conta Google não é Gmail nem de uma organização Google, preencha a senha atual acima para vincular sua conta.</p>
    </details>
  </div>;
}
