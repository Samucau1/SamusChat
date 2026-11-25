import { useEffect, useRef, useState, type ReactNode } from 'react';

const mobileQuery = '(max-width: 800px), (max-width: 1000px) and (max-height: 500px)';

export function useMobileLayout() {
  const [mobile, setMobile] = useState(() => window.matchMedia(mobileQuery).matches);
  useEffect(() => {
    const media = window.matchMedia(mobileQuery);
    const changed = () => setMobile(media.matches);
    function viewport() {
      if (window.visualViewport && window.visualViewport.scale !== 1) return;
      document.documentElement.style.setProperty('--mobile-viewport-height', `${window.visualViewport?.height ?? innerHeight}px`);
    }
    viewport(); media.addEventListener('change', changed);
    window.visualViewport?.addEventListener('resize', viewport);
    window.addEventListener('resize', viewport);
    return () => {
      media.removeEventListener('change', changed);
      window.visualViewport?.removeEventListener('resize', viewport);
      window.removeEventListener('resize', viewport);
      document.documentElement.style.removeProperty('--mobile-viewport-height');
    };
  }, []);
  return mobile;
}

export function MobileNavigation({ mobile, open, close, children, select }: {
  mobile: boolean; open: boolean; close(): void; select(): void; children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const restoreFocus = useRef(true);
  useEffect(() => {
    if (!mobile || !open) return;
    restoreFocus.current = true;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeButton.current?.focus();
    function keyboard(event: KeyboardEvent) {
      // A native settings/profile dialog owns its own keyboard interaction.
      if (document.querySelector('dialog[open]')) return;
      if (event.key === 'Escape') { event.preventDefault(); close(); }
      if (event.key !== 'Tab') return;
      const items = [...(ref.current?.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex="0"]') ?? [])].filter(item => item.getClientRects().length > 0);
      const first = items[0], last = items.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
    document.addEventListener('keydown', keyboard);
    return () => { document.removeEventListener('keydown', keyboard); if (restoreFocus.current) previous?.focus(); };
  }, [mobile, open]);
  return <>
    {mobile && open && <button className="mobile-navigation-backdrop" tabIndex={-1} aria-label="Fechar navegação" onClick={close}/>}
    <div ref={ref} id="app-navigation" className={`mobile-navigation${mobile && open ? ' is-open' : ''}`} role={mobile && open ? 'dialog' : undefined} aria-modal={mobile && open ? true : undefined} aria-label={mobile && open ? 'Conversas e servidores' : undefined} inert={mobile && !open} onClick={event => {
      const target = event.target instanceof Element ? event.target : null;
      if (mobile && target?.closest('.server-icon, .conversation-link, .find-friends')) { restoreFocus.current = false; select(); }
    }}>
      <button ref={closeButton} className="mobile-navigation-close" aria-label="Fechar menu" onClick={close}>×</button>
      {children}
    </div>
  </>;
}
