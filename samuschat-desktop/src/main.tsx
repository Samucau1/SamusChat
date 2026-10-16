import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { useChatController } from './controllers/useChatController';
import { useCallController } from './controllers/useCallController';
import { useVoiceRoomController } from './controllers/useVoiceRoomController';
import { useScreenPreferences } from './controllers/useScreenPreferences';
import { useSession } from './controllers/useSession';
import { ChatApp } from './views/ChatApp';
import { AuthScreen } from './views/AuthScreen';
import './styles.css';

function ConnectedApp({ session: s }: { session: ReturnType<typeof useSession> }) {
  const screenPreferences = useScreenPreferences();
  const controller = useChatController(s.api, s.user!, s.setUser, s.handleError);
  const calls = useCallController(s.api, s.user!.publicId ?? s.user!.email, s.handleError, screenPreferences);
  const rooms = useVoiceRoomController(s.api, s.user!.publicId ?? s.user!.email, s.handleError, screenPreferences);
  return <ChatApp controller={controller} calls={calls} rooms={rooms} screenPreferences={screenPreferences} error={s.error} clearError={() => s.setError('')} logout={s.logout} />;
}
function App() {
  const session = useSession();
  if (session.restoring) return <main className="loading-screen" role="status">Restaurando sua sessão…</main>;
  return session.user ? <ConnectedApp key={session.user.id} session={session} /> : <AuthScreen session={session} />;
}
createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
