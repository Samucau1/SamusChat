import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTheme, type ThemeName } from '../controllers/useTheme';

const themes: { value: ThemeName; title: string; description: string }[] = [
  { value: 'purple', title: 'Preto e roxo', description: 'Fundos escuros com detalhes roxos suaves.' },
  { value: 'black-green', title: 'Preto absoluto e verde', description: 'Fundo preto com destaques e degradês verdes.' },
  { value: 'blue', title: 'Azul clássico', description: 'O visual anterior, com cinzas escuros e azul.' },
  { value: 'black-orange', title: 'Preto e laranja', description: 'Preto com degradês de laranja avermelhado.' },
];

type SettingsProps = { children?: ReactNode; iconOnly?: boolean; profile?: (close: () => void) => ReactNode };

export function Settings({ children, iconOnly = false, profile }: SettingsProps) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const wasOpen = useRef(false);
  useEffect(() => {
    if (!open && wasOpen.current) trigger.current?.focus();
    wasOpen.current = open;
  }, [open]);
  return <>
    <button ref={trigger} type="button" className={`button-secondary settings-trigger${iconOnly ? ' settings-trigger-icon' : ''}`} aria-label="Configurações" title={iconOnly ? 'Configurações' : undefined} aria-haspopup="dialog" onClick={() => setOpen(true)}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9 3-1 3-3 1-2 3 2 2-1 3 2 3 3-1 2 3h3l1-3 3-1 2-3-2-2 1-3-2-3-3 1-2-3Z" /><circle cx="12" cy="12" r="3" /></svg>{!iconOnly && 'Configurações'}
    </button>
    {open && <SettingsDialog close={() => setOpen(false)} profile={profile}>{children}</SettingsDialog>}
  </>;
}

function SettingsDialog({ close, children, profile }: { close(): void; children?: ReactNode; profile?: SettingsProps['profile'] }) {
  const ref = useRef<HTMLDialogElement>(null);
  const [tab, setTab] = useState<'appearance' | 'profile'>('appearance');
  const { theme, setTheme, saved } = useTheme();
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} className="settings-dialog" aria-labelledby="settings-title" onCancel={event => { event.preventDefault(); close(); }}>
    <header className="settings-heading">
      <div><p className="eyebrow">DO SEU JEITO</p><h2 id="settings-title">Configurações</h2></div>
      <button type="button" className="settings-close" aria-label="Fechar configurações" onClick={close}>×</button>
    </header>
    {profile && <div className="settings-tabs" role="tablist" aria-label="Seções das configurações" onKeyDown={event => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const next = event.key === 'Home' ? 'appearance' : event.key === 'End' ? 'profile' : tab === 'appearance' ? 'profile' : 'appearance';
      setTab(next);
      event.currentTarget.querySelector<HTMLButtonElement>(`#settings-tab-${next}`)?.focus();
    }}>
      <button id="settings-tab-appearance" type="button" role="tab" aria-selected={tab === 'appearance'} aria-controls="settings-panel-appearance" tabIndex={tab === 'appearance' ? 0 : -1} onClick={() => setTab('appearance')}>Aparência</button>
      <button id="settings-tab-profile" type="button" role="tab" aria-selected={tab === 'profile'} aria-controls="settings-panel-profile" tabIndex={tab === 'profile' ? 0 : -1} onClick={() => setTab('profile')}>Perfil</button>
    </div>}
    <section id="settings-panel-appearance" role={profile ? 'tabpanel' : undefined} aria-labelledby={profile ? 'settings-tab-appearance' : undefined} hidden={tab !== 'appearance'}>
    <fieldset className="theme-picker">
      <legend>Aparência</legend>
      <p className="muted">Escolha o tema do SamusChat neste navegador.</p>
      <div className="theme-options">
        {themes.map(option => <label key={option.value} className={`theme-option ${theme === option.value ? 'chosen' : ''}`}>
          <span className={`theme-swatch theme-swatch-${option.value}`} aria-hidden="true"><span /><span /><span /></span>
          <span className="theme-option-title"><input type="radio" name="samuschat-theme" value={option.value} checked={theme === option.value} onChange={() => setTheme(option.value)} /><strong>{option.title}</strong></span>
          <span className="theme-option-description">{option.description}</span>
        </label>)}
      </div>
    </fieldset>
    <p className="settings-feedback" role="status">{saved ? 'Tema aplicado. Sua escolha fica salva neste navegador.' : 'Tema aplicado nesta aba. O navegador não permitiu salvar sua escolha.'}</p>
    {children && <section className="settings-preferences"><h3>Notificações e animações</h3>{children}</section>}
    <div className="dialog-actions"><button type="button" className="primary" onClick={close}>Concluir</button></div>
    </section>
    {profile && <section id="settings-panel-profile" role="tabpanel" aria-labelledby="settings-tab-profile" hidden={tab !== 'profile'}>{profile(close)}</section>}
  </dialog>;
}
