import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { mockRepository } from './models/mockRepository';
import { useChatController } from './controllers/useChatController';
import { ChatApp } from './views/ChatApp';
import './styles.css';

function App() { return <ChatApp controller={useChatController(mockRepository)} />; }
createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
