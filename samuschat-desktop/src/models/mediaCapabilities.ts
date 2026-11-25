export function screenCaptureSupport() {
  if (!window.isSecureContext) return {
    supported: false,
    message: 'Abra o SamusChat pelo endereço HTTPS para usar câmera, microfone e compartilhamento de tela.',
  };
  if (typeof navigator.mediaDevices?.getDisplayMedia !== 'function') return {
    supported: false,
    message: 'Este navegador não permite compartilhar a tela deste dispositivo. Você pode assistir às transmissões recebidas. No Android, use o aplicativo SamusChat para transmitir sua tela.',
  };
  return { supported: true, message: '' };
}

export function cameraSupported() {
  return window.isSecureContext && typeof navigator.mediaDevices?.getUserMedia === 'function';
}
