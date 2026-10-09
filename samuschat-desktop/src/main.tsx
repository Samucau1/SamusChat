import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { useChatController } from './controllers/useChatController';
import { useCallController } from './controllers/useCallController';
import { useSession } from './controllers/useSession';
import { ChatApp } from './views/ChatApp';
import { AuthScreen } from './views/AuthScreen';
import './styles.css';

function ConnectedApp({ session: s }: { session: ReturnType<typeof useSession> }) {
  const controller = useChatController(s.api, s.user!, s.setUser, s.handleError);
  const calls = useCallController(s.api, s.user!.email, s.handleError);
  return <ChatApp controller={controller} calls={calls} error={s.error} clearError={() => s.setError('')} logout={s.logout} />;
}
function App() {
  const session = useSession();
  if (session.restoring) return <main className="loading-screen" role="status">Restaurando sua sessão…</main>;
  return session.user ? <ConnectedApp key={session.user.id} session={session} /> : <AuthScreen session={session} />;
}
createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
