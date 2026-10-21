const pending = new WeakMap<RTCPeerConnection, { sdp: string; operation: Promise<void> }>();

/** One answer application per peer, including concurrent repeated poll responses. */
export async function applyRemoteAnswer(peer: RTCPeerConnection, sdp: string): Promise<void> {
  const active = pending.get(peer);
  if (active) {
    if (active.sdp !== sdp) throw new Error('A chamada recebeu respostas de conexão diferentes.');
    return active.operation;
  }
  if (peer.remoteDescription?.type === 'answer' && peer.remoteDescription.sdp === sdp) return;
  if (peer.signalingState !== 'have-local-offer') throw new Error('A resposta da chamada chegou sem uma oferta pendente. Encerre e tente novamente.');
  const operation = peer.setRemoteDescription({ type: 'answer', sdp });
  pending.set(peer, { sdp, operation });
  try { await operation; } finally { if (pending.get(peer)?.operation === operation) pending.delete(peer); }
}
