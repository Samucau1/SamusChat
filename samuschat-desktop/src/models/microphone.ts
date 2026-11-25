const storageKey = 'samuschat.microphone';
export function selectedMicrophone() { try { return localStorage.getItem(storageKey) || ''; } catch { return ''; } }
export function rememberMicrophone(id: string) { try { localStorage.setItem(storageKey, id); } catch { /* Storage may be unavailable. */ } }
export async function captureMicrophone(id = selectedMicrophone()): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('O microfone exige HTTPS ou localhost.');
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { ...(id ? { deviceId: { exact: id } } : {}), echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: false });
    if (!stream.getAudioTracks().some(track => track.readyState === 'live')) { stream.getTracks().forEach(track => track.stop()); throw new Error('Nenhum microfone ativo. Abra Microfone e escolha uma entrada.'); }
    return stream;
  } catch (error) {
    if (error instanceof DOMException) {
      if (error.name === 'NotAllowedError') throw new Error('Permita o microfone nas permissões deste site e nas configurações de privacidade do seu dispositivo.');
      if (['NotFoundError', 'OverconstrainedError'].includes(error.name)) throw new Error('O microfone selecionado não está disponível. Abra Microfone e escolha outra entrada.');
      if (error.name === 'NotReadableError') throw new Error('Não foi possível abrir o microfone. Confira se outro aplicativo está usando a entrada e tente novamente.');
    }
    throw error;
  }
}
