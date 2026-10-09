import { useState } from 'react';
import type { useSession } from '../controllers/useSession';

export function AuthScreen({ session: s }: { session: ReturnType<typeof useSession> }) {
  const [register, setRegister] = useState(false);
  const [name, setName] = useState(''); const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [recovering, setRecovering] = useState(false); const [stage, setStage] = useState(0);
  const [code, setCode] = useState(''); const [resetToken, setResetToken] = useState(''); const [confirm, setConfirm] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false);
  async function recovery() {
    setBusy(true); s.setError('');
    try {
      if (stage === 0) { const message = await s.api.post<string>('/api/auth/password/request', { email: email.trim() }); setNotice(message); setStage(1); }
      else if (stage === 1) { const result = await s.api.post<{ resetToken: string }>('/api/auth/password/verify', { email: email.trim(), code }); setResetToken(result.resetToken); setPassword(''); setStage(2); setNotice('Código confirmado. Escolha sua nova senha.'); }
      else { if (password !== confirm) throw new Error('As senhas precisam ser iguais.'); await s.api.post('/api/auth/password/reset', { email: email.trim(), resetToken, password }); setResetToken(''); setRecovering(false); setPassword(''); setNotice('Senha alterada. Entre com a nova senha.'); }
    } catch (problem) { s.handleError(problem); } finally { setBusy(false); }
  }
  return <main className="auth-shell"><section className="auth-intro"><div className="brand-mark">SC</div><p className="eyebrow">SAMUSCHAT PARA COMPUTADOR</p><h1>Suas conversas.<br />Mais espaço para conectar.</h1><p>Servidores, amigos e projetos no mesmo lugar. Continue no computador usando sua conta do celular.</p><div className="auth-feature"># Converse nos seus servidores</div><div className="auth-feature">@ Encontre seus amigos</div><div className="auth-feature">↗ Compartilhe ideias e arquivos</div></section>
    <section className="auth-card"><h2>{recovering ? 'Recuperar senha' : register ? 'Crie sua conta' : 'Bom ter você por aqui'}</h2><p className="muted">{recovering ? 'Siga as etapas para recuperar o acesso.' : 'Use a mesma conta do SamusChat Android.'}</p>
      {s.error && <p className="error-notice" role="alert">{s.error}</p>}{notice && <p role="status">{notice}</p>}
      <form onSubmit={event => { event.preventDefault(); if (recovering) void recovery(); else void s.authenticate(register, name, email, password); }}>
        {register && !recovering && <label>Nome de usuário<input autoComplete="username" value={name} onChange={event => setName(event.target.value)} minLength={3} maxLength={30} pattern="[A-Za-z0-9_-]{3,30}" required /></label>}
        <label>Email<input type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} disabled={recovering && stage > 0} required /></label>
        {recovering && stage === 1 ? <label>Código de quatro dígitos<input inputMode="numeric" autoComplete="one-time-code" value={code} onChange={event => setCode(event.target.value)} pattern="[0-9]{4}" maxLength={4} required /></label> : (!recovering || stage === 2) && <label>{recovering ? 'Nova senha' : 'Senha'}<input type="password" autoComplete={register || recovering ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} minLength={6} maxLength={100} required /></label>}
        {recovering && stage === 2 && <label>Confirme a senha<input type="password" autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} minLength={6} maxLength={100} required /></label>}
        <button className="primary" disabled={s.busy || busy}>{s.busy || busy ? 'Aguarde…' : recovering ? stage === 0 ? 'Enviar código' : stage === 1 ? 'Validar código' : 'Salvar senha' : register ? 'Criar conta' : 'Entrar'}</button>
      </form>
      <button className="text-button" disabled={s.busy || busy} onClick={() => { setRecovering(false); setStage(0); setResetToken(''); setNotice(''); s.setError(''); if (!recovering) setRegister(!register); }}>{recovering ? 'Voltar para o login' : register ? 'Já tenho uma conta' : 'Criar uma conta'}</button>
      {!register && !recovering && <button className="text-button" onClick={() => { setRecovering(true); setStage(0); setNotice(''); s.setError(''); }}>Esqueci minha senha</button>}
    </section></main>;
}
