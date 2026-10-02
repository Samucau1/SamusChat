package com.samuschat.ui.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import com.samuschat.BuildConfig
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RtcCall(
    private val context: Context, ice: List<IceServerConfig>, caller: Boolean,
    private val onConnection: (Boolean) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onVideo: (VideoTrack?) -> Unit,
    private val onRemoteSharing: (Boolean) -> Unit,
    private val onSharingStopped: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    val egl: EglBase = EglBase.create()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val previousMode = audioManager.mode
    private val previousSpeaker = audioManager.isSpeakerphoneOn
    @Volatile private var closed = false
    private val factory: PeerConnectionFactory
    private val audioModule: JavaAudioDeviceModule
    private val peer: PeerConnection
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private val videoSource: VideoSource
    private val videoTrack: VideoTrack
    private var screen: ScreenCapturerAndroid? = null
    private var texture: SurfaceTextureHelper? = null
    private var channel: DataChannel? = null
    private var control: DataChannel? = null
    private var sharingState = false
    private var sharingGeneration = 0
    private val deviceAudio = DeviceAudio()
    private val candidates = java.util.concurrent.CopyOnWriteArrayList<GatheredCandidate>()
    @Volatile private var lastCandidateAt = 0L

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        audioModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val config = PeerConnection.RTCConfiguration(ice.map { PeerConnection.IceServer.builder(it.urls).setUsername(it.username).setPassword(it.credential).createIceServer() })
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        if (BuildConfig.CALLS_FORCE_RELAY) config.iceTransportsType = PeerConnection.IceTransportsType.RELAY
        peer = checkNotNull(factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.FAILED) main.post { if (!closed) onFailure("Não foi possível conectar a chamada. Confira a rede e a configuração TURN.") }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                main.post { if (!closed) onConnection(state == PeerConnection.PeerConnectionState.CONNECTED) }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                candidates.add(GatheredCandidate(candidate.sdpMLineIndex, candidate.sdp))
                lastCandidateAt = System.currentTimeMillis()
                if (BuildConfig.DEBUG) android.util.Log.i("CallStats", "gatheredCandidate media=${candidate.sdpMLineIndex}")
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dataChannel: DataChannel) { main.post { if (!closed) attachChannel(dataChannel) else dataChannel.dispose() } }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { track -> main.post { if (!closed) onVideo(track) } }
            }
        }))
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("microphone", audioSource)
        videoSource = factory.createVideoSource(true)
        videoTrack = factory.createVideoTrack("screen", videoSource)
        peer.addTrack(audioTrack, listOf("call")); peer.addTrack(videoTrack, listOf("call"))
        if (caller) {
            attachChannel(peer.createDataChannel("device-audio", DataChannel.Init().apply { ordered = false; maxRetransmits = 0 }))
            attachChannel(peer.createDataChannel("call-control", DataChannel.Init()))
        }
        if (BuildConfig.DEBUG) main.postDelayed(object : Runnable {
            override fun run() {
                if (closed) return
                peer.getStats { report ->
                    val inbound = report.statsMap.values.filter { it.type == "inbound-rtp" }.map {
                        mapOf("kind" to it.members["kind"], "packets" to it.members["packetsReceived"], "frames" to it.members["framesDecoded"])
                    }
                    val transport = report.statsMap.values.firstOrNull { it.type == "transport" && it.members["selectedCandidatePairId"] != null }
                    val pair = report.statsMap[transport?.members?.get("selectedCandidatePairId") as? String]
                    val candidate = report.statsMap[pair?.members?.get("localCandidateId") as? String]
                    android.util.Log.i("CallStats", "inbound=$inbound localCandidate=${candidate?.members?.get("candidateType")}")
                }
                main.postDelayed(this, 5000)
            }
        }, 5000)
    }

    private fun attachChannel(value: DataChannel) {
        if (value.label() == "call-control" && control == null) {
            control = value
            value.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}
                override fun onStateChange() { main.post { if (!closed) sendSharingState() } }
                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (closed || buffer.binary || buffer.data.remaining() > 32) return
                    val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
                    val message = String(bytes, Charsets.UTF_8)
                    if (message == "screen:on" || message == "screen:off") main.post { if (!closed) onRemoteSharing(message == "screen:on") }
                }
            })
            return
        }
        if (value.label() != "device-audio" || channel != null) { value.close(); value.dispose(); return }
        channel = value
        value.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (closed || buffer.data.remaining() > 640) return
                val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
                if (buffer.binary) deviceAudio.receive(bytes)
            }
        })
    }

    private suspend fun create(offer: Boolean): SessionDescription = suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { if (continuation.isActive) continuation.resume(sdp) }
            override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }
        if (offer) peer.createOffer(observer, MediaConstraints()) else peer.createAnswer(observer, MediaConstraints())
    }
    private suspend fun set(sdp: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserver {
            override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }
        if (local) peer.setLocalDescription(observer, sdp) else peer.setRemoteDescription(observer, sdp)
    }
    suspend fun offer(): String = localDescription(true)
    suspend fun answer(offer: String): String { set(SessionDescription(SessionDescription.Type.OFFER, offer), false); return localDescription(false) }
    suspend fun remoteAnswer(answer: String) { set(SessionDescription(SessionDescription.Type.ANSWER, answer), false) }
    private suspend fun localDescription(offer: Boolean): String {
        check(!closed) { "Chamada encerrada" }
        set(create(offer), true)
        // Some interfaces continue gathering after a usable TURN candidate is available.
        // Non-trickle signaling can proceed after a quiet interval; do not wait forever on an unreachable interface.
        check(withTimeoutOrNull(15000) {
            while (!closed && (candidates.isEmpty() || System.currentTimeMillis() - lastCandidateAt < 1200)) delay(100)
            true
        } == true) { "Tempo de negociação da chamada esgotado" }
        check(!closed) { "Chamada encerrada" }
        delay(200)
        check(!closed) { "Chamada encerrada" }
        val sdp = completeSdp(peer.localDescription.description, candidates.toList())
        if (BuildConfig.DEBUG) android.util.Log.i("CallStats", "gatheringComplete candidates=${candidates.size} sdpHasCandidates=${sdp.contains("a=candidate:")}")
        check(sdp.contains("a=candidate:")) { "Nenhum caminho de mídia disponível. Verifique a configuração TURN." }
        return sdp
    }
    // Keep AudioRecord running: rapid track enable/disable can race its native restart.
    // The audio module replaces microphone samples with silence while muted.
    fun mute(muted: Boolean) { if (!closed) audioModule.setMicrophoneMute(muted) }
    fun speaker(enabled: Boolean) { audioManager.isSpeakerphoneOn = enabled }
    fun share(permission: Intent) {
        check(screen == null)
        val generation = ++sharingGeneration
        val capturer = ScreenCapturerAndroid(permission, object : MediaProjection.Callback() {
            override fun onStop() { main.post { if (!closed && generation == sharingGeneration && screen != null) { stopSharing(); onSharingStopped() } } }
            override fun onCapturedContentResize(width: Int, height: Int) {
                if (width <= 0 || height <= 0) return
                main.post {
                    if (!closed && generation == sharingGeneration && screen != null) {
                        val scale = 960.0 / maxOf(width, height)
                        screen?.changeCaptureFormat(maxOf(2, (width * scale).toInt() / 2 * 2), maxOf(2, (height * scale).toInt() / 2 * 2), 15)
                    }
                }
            }
        })
        screen = capturer
        texture = SurfaceTextureHelper.create("call-screen", egl.eglBaseContext)
        capturer.initialize(texture, context, videoSource.capturerObserver)
        val metrics = context.resources.displayMetrics
        val width = if (metrics.widthPixels > metrics.heightPixels) 960 else 540
        val height = if (metrics.widthPixels > metrics.heightPixels) 540 else 960
        capturer.startCapture(width, height, 15)
        sharingState = true; sendSharingState()
    }
    fun deviceSound(enabled: Boolean) {
        if (!enabled) { deviceAudio.stopCapture(); return }
        if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("Áudio do dispositivo requer Android 10 ou superior")
        val projection = checkNotNull(screen?.mediaProjection) { "Compartilhe a tela primeiro" }
        val data = checkNotNull(channel) { "Aguarde a conexão da chamada" }
        check(data.state() == DataChannel.State.OPEN) { "Aguarde a conexão da chamada" }
        deviceAudio.start(projection, data) { main.post { if (!closed) onFailure("Falha na captura do áudio do dispositivo") } }
    }
    fun stopSharing() {
        sharingGeneration++
        deviceAudio.stopCapture()
        val capturer = screen; screen = null
        capturer?.stopCapture(); capturer?.dispose(); texture?.dispose(); texture = null
        sharingState = false; sendSharingState()
    }
    private fun sendSharingState() {
        control?.takeIf { it.state() == DataChannel.State.OPEN }?.send(DataChannel.Buffer(ByteBuffer.wrap((if (sharingState) "screen:on" else "screen:off").toByteArray()), false))
    }
    fun close() {
        if (closed) return
        closed = true; stopSharing(); channel?.unregisterObserver(); channel?.close(); control?.unregisterObserver(); control?.close(); deviceAudio.close()
        peer.close(); peer.dispose(); channel?.dispose(); channel = null; control?.dispose(); control = null
        audioTrack.dispose(); videoTrack.dispose(); audioSource.dispose(); videoSource.dispose(); factory.dispose(); audioModule.release(); egl.release()
        audioManager.isSpeakerphoneOn = previousSpeaker; audioManager.mode = previousMode
    }
}
