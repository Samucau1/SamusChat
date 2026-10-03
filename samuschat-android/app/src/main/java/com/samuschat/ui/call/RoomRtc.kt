package com.samuschat.ui.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import com.samuschat.BuildConfig
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One capture/factory shared by the room's peer connections, including screen projection. */
class RoomRtc(
    private val context: Context, private val ice: List<IceServerConfig>,
    private val onConnection: (String, Boolean) -> Unit,
    private val onVideo: (String, VideoTrack) -> Unit,
    private val onRemoteSharing: (String, Boolean) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onSharingStopped: () -> Unit
) {
    val egl = EglBase.create()
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val oldMode = audio.mode
    private val oldSpeaker = audio.isSpeakerphoneOn
    private val module: JavaAudioDeviceModule
    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val microphone: AudioTrack
    private val videoSource: VideoSource
    private val screenTrack: VideoTrack
    private val peers = linkedMapOf<String, Link>()
    private var screen: ScreenCapturerAndroid? = null
    private var texture: SurfaceTextureHelper? = null
    private var generation = 0
    private var sharing = false
    private var closed = false

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        module = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(module)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        microphone = factory.createAudioTrack("room-microphone", audioSource)
        videoSource = factory.createVideoSource(true)
        screenTrack = factory.createVideoTrack("room-screen", videoSource)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (BuildConfig.DEBUG) main.postDelayed(object : Runnable {
            override fun run() {
                if (closed) return
                peers.values.forEach { link -> link.peer.getStats { report ->
                    val inbound = report.statsMap.values.filter { it.type == "inbound-rtp" }.map {
                        "kind=${it.members["kind"]} packets=${it.members["packetsReceived"]} frames=${it.members["framesDecoded"]}"
                    }
                    val transport = report.statsMap.values.firstOrNull { it.type == "transport" && it.members["selectedCandidatePairId"] != null }
                    val pair = report.statsMap[transport?.members?.get("selectedCandidatePairId") as? String]
                    val candidate = report.statsMap[pair?.members?.get("localCandidateId") as? String]
                    android.util.Log.i("RoomStats", "peer=${link.id} inbound=$inbound localCandidate=${candidate?.members?.get("candidateType")}")
                } }
                main.postDelayed(this, 5000)
            }
        }, 5000)
    }
    inner class Link(val id: String, caller: Boolean) {
        val candidates = java.util.concurrent.CopyOnWriteArrayList<GatheredCandidate>()
        @Volatile var candidateAt = 0L
        var control: DataChannel? = null
        var sent = false
        var answerSet = false
        var localSdp: String? = null
        var disposed = false
        var connected = false
        val peer: PeerConnection
        init {
            val config = PeerConnection.RTCConfiguration(ice.map { PeerConnection.IceServer.builder(it.urls).setUsername(it.username).setPassword(it.credential).createIceServer() })
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            if (BuildConfig.CALLS_FORCE_RELAY) config.iceTransportsType = PeerConnection.IceTransportsType.RELAY
            peer = checkNotNull(factory.createPeerConnection(config, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    if (state == PeerConnection.IceConnectionState.FAILED) main.post { if (!closed && !disposed) onFailure("Falha de conexão no canal. Confira a rede e tente entrar novamente.") }
                }
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { main.post { if (!closed && !disposed) {
                    connected = state == PeerConnection.PeerConnectionState.CONNECTED
                    onConnection(id, connected)
                    if (state == PeerConnection.PeerConnectionState.DISCONNECTED) main.postDelayed({ if (!closed && !disposed && !connected) onFailure("Conexão do canal perdida. Entre novamente.") }, 35000)
                } } }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onIceCandidate(candidate: IceCandidate) { candidates.add(GatheredCandidate(candidate.sdpMLineIndex, candidate.sdp)); candidateAt = System.currentTimeMillis() }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onRenegotiationNeeded() {}
                override fun onDataChannel(channel: DataChannel) { main.post { if (!closed && !disposed) attach(channel) else channel.dispose() } }
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                    (receiver.track() as? VideoTrack)?.let { track -> main.post { if (!closed && !disposed) onVideo(id, track) } }
                }
            }))
            peer.addTrack(microphone, listOf("room")); peer.addTrack(screenTrack, listOf("room"))
            if (caller) attach(peer.createDataChannel("call-control", DataChannel.Init()))
            main.postDelayed({ if (!closed && !disposed && !connected) onFailure("A conexão de mídia do canal expirou. Confira a rede e o TURN.") }, 60000)
        }
        private fun attach(channel: DataChannel) {
            if (channel.label() != "call-control" || control != null) { channel.close(); channel.dispose(); return }
            control = channel
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}
                override fun onStateChange() { main.post { if (!closed && !disposed) advertise() } }
                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (closed || disposed || buffer.binary || buffer.data.remaining() > 32) return
                    val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
                    val value = String(bytes, Charsets.UTF_8)
                    if (value in listOf("screen:on", "screen:off")) main.post { if (!closed && !disposed) onRemoteSharing(id, value == "screen:on") }
                }
            })
        }
        fun advertise() { control?.takeIf { it.state() == DataChannel.State.OPEN }?.send(DataChannel.Buffer(ByteBuffer.wrap((if (sharing) "screen:on" else "screen:off").toByteArray()), false)) }
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
        suspend fun local(offer: Boolean, remoteOffer: String? = null): String {
            localSdp?.let { return it }
            if (remoteOffer != null) set(SessionDescription(SessionDescription.Type.OFFER, remoteOffer), false)
            set(create(offer), true)
            check(withTimeoutOrNull(15000) { while (!closed && !disposed && (candidates.isEmpty() || System.currentTimeMillis() - candidateAt < 1200)) delay(100); true } == true) { "Tempo de negociação do canal esgotado" }
            check(!closed && !disposed)
            return completeSdp(peer.localDescription.description, candidates.toList()).also { check(it.contains("a=candidate:")); localSdp = it }
        }
        suspend fun answer(sdp: String) { set(SessionDescription(SessionDescription.Type.ANSWER, sdp), false); answerSet = true }
        fun close() { disposed = true; control?.unregisterObserver(); control?.close(); peer.close(); peer.dispose(); control?.dispose(); control = null }
    }
    fun link(id: String, caller: Boolean): Link = peers.getOrPut(id) { Link(id, caller) }
    fun retain(ids: Set<String>) { peers.keys.filter { it !in ids }.forEach { peers.remove(it)?.close() } }
    fun mute(value: Boolean) { if (!closed) module.setMicrophoneMute(value) }
    fun speaker(value: Boolean) { audio.isSpeakerphoneOn = value }
    fun share(permission: Intent) {
        check(!closed && screen == null)
        val current = ++generation
        val capturer = ScreenCapturerAndroid(permission, object : MediaProjection.Callback() {
            override fun onStop() { main.post { if (!closed && current == generation && screen != null) { stopSharing(); onSharingStopped() } } }
            override fun onCapturedContentResize(width: Int, height: Int) {
                if (width <= 0 || height <= 0) return
                main.post { if (!closed && current == generation) {
                    val scale = 960.0 / maxOf(width, height)
                    screen?.changeCaptureFormat(maxOf(2, (width * scale).toInt() / 2 * 2), maxOf(2, (height * scale).toInt() / 2 * 2), 15)
                } }
            }
        })
        screen = capturer
        texture = SurfaceTextureHelper.create("room-screen", egl.eglBaseContext)
        capturer.initialize(texture, context, videoSource.capturerObserver)
        val metrics = context.resources.displayMetrics
        capturer.startCapture(if (metrics.widthPixels > metrics.heightPixels) 960 else 540, if (metrics.widthPixels > metrics.heightPixels) 540 else 960, 15)
        sharing = true; peers.values.forEach { it.advertise() }
    }
    fun stopSharing() {
        generation++
        val capture = screen; screen = null
        capture?.stopCapture(); capture?.dispose(); texture?.dispose(); texture = null
        sharing = false; peers.values.forEach { it.advertise() }
    }
    fun close() {
        if (closed) return
        closed = true; stopSharing(); peers.values.forEach { it.close() }; peers.clear()
        microphone.dispose(); screenTrack.dispose(); audioSource.dispose(); videoSource.dispose(); factory.dispose(); module.release(); egl.release()
        audio.isSpeakerphoneOn = oldSpeaker; audio.mode = oldMode
    }
}
