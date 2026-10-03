package com.samuschat.ui.call

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuschat.SamusChatApplication
import com.samuschat.data.repository.data
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.VideoTrack
import java.util.UUID

data class CallUiState(
    val contacts: List<CallContact> = emptyList(), val call: CallSession? = null,
    val busy: Boolean = false, val connected: Boolean = false, val muted: Boolean = false,
    val speaker: Boolean = false, val sharing: Boolean = false, val deviceAudio: Boolean = false,
    val remoteSharing: Boolean = false, val error: String? = null, val notice: String? = null,
    val room: CallRoom? = null, val roomName: String = "", val roomConnected: Set<String> = emptySet(), val roomSharing: Set<String> = emptySet()
)

class CallViewModel(private val app: SamusChatApplication, val email: String) : ViewModel() {
    private val mutable = MutableStateFlow(CallUiState())
    val state = mutable.asStateFlow()
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val roomVideos = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    var roomRtc: RoomRtc? = null; private set
    var rtc: RtcCall? = null; private set
    private val mutex = Mutex()
    private var offerSent = false
    private var answerSet = false
    private var localSdp: String? = null
    private var foreground = true
    private var lastSuccess = System.currentTimeMillis()
    private var acceptedLocally = false
    private var dismissedId: String? = null
    private var connectionTimer: Job? = null
    private var poll: Job

    init {
        refreshContacts()
        poll = viewModelScope.launch {
            while (isActive) {
                if (foreground || mutable.value.call != null || mutable.value.room != null) {
                    try {
                        mutex.withLock {
                            val token = app.tokens.authorization()
                            val room = mutable.value.room
                            if (room != null) {
                                handleRoom(app.api.callRoom(token, room.channelId).data())
                                lastSuccess = System.currentTimeMillis()
                                return@withLock
                            }
                            val current = mutable.value.call
                            val next = if (current == null) app.api.currentCalls(token).data().firstOrNull()
                                else app.api.call(token, current.id).data()
                            lastSuccess = System.currentTimeMillis()
                            if (next != null && next.id != dismissedId) handle(next)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if ((mutable.value.call != null || mutable.value.room != null) && (error is IllegalStateException || System.currentTimeMillis() - lastSuccess > 20000)) {
                            end(); mutable.value = mutable.value.copy(error = error.message ?: "Conexão perdida. A chamada foi encerrada.")
                        } else if (foreground) mutable.value = mutable.value.copy(error = error.message ?: "Falha ao atualizar chamadas")
                    }
                }
                delay(if (mutable.value.call == null && mutable.value.room == null) 5000 else 2000)
            }
        }
    }
    fun foreground(value: Boolean) { foreground = value }
    fun refreshContacts() = viewModelScope.launch {
        try { mutable.value = mutable.value.copy(contacts = app.api.callContacts(app.tokens.authorization()).data(), error = null) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message) }
    }
    private fun execute(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { mutex.withLock { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "Não foi possível completar a chamada") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun invite(contact: CallContact) = execute {
        check(mutable.value.room == null) { "Saia do canal de chamada primeiro" }
        if (mutable.value.call != null) return@execute
        // Start while the user is interacting with the app, before a delayed remote acceptance.
        try {
            service()
            dismissedId = null; acceptedLocally = true
            handle(app.api.inviteCall(app.tokens.authorization(), CallInvite(contact.email, UUID.randomUUID().toString())).data())
        } catch (e: Exception) {
            finish("Não foi possível iniciar a chamada")
            throw e
        }
    }
    fun accept() = execute {
        val call = mutable.value.call ?: return@execute
        if (!CallPolicy.mayAccept(call, email)) return@execute
        try {
            service()
            acceptedLocally = true
            handle(app.api.callAction(app.tokens.authorization(), call.id, CallAction("accept")).data())
        } catch (e: Exception) {
            end()
            throw e
        }
    }
    fun end(decline: Boolean = false) {
        mutable.value.room?.let { room ->
            finish("Você saiu do canal")
            viewModelScope.launch { runCatching { app.api.leaveCallRoom(app.tokens.authorization(), room.channelId).data() } }
            return
        }
        val current = mutable.value.call ?: return
        // Stop local capture immediately, even when the network is unavailable.
        dismissedId = current.id; finish(if (decline) "Chamada recusada" else "Chamada encerrada")
        viewModelScope.launch {
            runCatching { app.api.callAction(app.tokens.authorization(), current.id, CallAction(if (decline) "decline" else "end")).data() }
        }
    }
    private suspend fun service(screen: Boolean = false) {
        val ready = CompletableDeferred<Unit>()
        CallMediaService.onReady = { ready.complete(Unit) }
        CallMediaService.onStopRequested = { end() }
        app.startForegroundService(Intent(app, CallMediaService::class.java).putExtra("screen", screen))
        check(withTimeoutOrNull(5000) { ready.await(); true } == true) { "Não foi possível iniciar o serviço de chamada" }
    }
    private suspend fun handle(call: CallSession) {
        if (call.ended) { finish(when (call.state) { "DECLINED" -> "Chamada recusada"; "EXPIRED" -> "Chamada expirou"; else -> "Chamada encerrada" }); return }
        mutable.value = mutable.value.copy(call = call, error = null)
        if (call.state == "RINGING") return
        if (!acceptedLocally) {
            // A previous process/device accepted this call; never silently reacquire the microphone.
            end(); return
        }
        if (rtc == null) {
            val ice = app.api.callIce(app.tokens.authorization()).data()
            service()
            rtc = RtcCall(app, ice, call.caller == email,
                onConnection = { connected ->
                    mutable.value = mutable.value.copy(connected = connected)
                    if (connected) { connectionTimer?.cancel(); connectionTimer = null }
                    else watchConnection()
                },
                onFailure = { message -> end(); mutable.value = mutable.value.copy(error = message) },
                onVideo = { remoteVideo.value = it },
                onRemoteSharing = { mutable.value = mutable.value.copy(remoteSharing = it) },
                onSharingStopped = { mutable.value = mutable.value.copy(sharing = false, deviceAudio = false) })
            watchConnection()
        }
        val engine = rtc ?: return
        if (call.caller == email && !offerSent) {
            val sdp = localSdp ?: engine.offer().also { localSdp = it }
            if (rtc !== engine) return
            app.api.callAction(app.tokens.authorization(), call.id, CallAction("offer", sdp)).data(); offerSent = true
        } else if (call.callee == email && call.offer != null && !offerSent) {
            val sdp = localSdp ?: engine.answer(call.offer).also { localSdp = it }
            if (rtc !== engine) return
            app.api.callAction(app.tokens.authorization(), call.id, CallAction("answer", sdp)).data(); offerSent = true
        }
        if (call.caller == email && call.answer != null && !answerSet) { engine.remoteAnswer(call.answer); answerSet = true }
    }
    fun joinRoom(id: Long, name: String) = execute {
        check(mutable.value.call == null && mutable.value.room == null) { "Encerre a chamada atual primeiro" }
        try {
            service()
            val room = app.api.joinCallRoom(app.tokens.authorization(), id).data()
            mutable.value = mutable.value.copy(room = room, roomName = name)
            roomRtc = RoomRtc(app, app.api.callIce(app.tokens.authorization()).data(),
                onConnection = { peer, connected ->
                    val previous = mutable.value.roomConnected
                    mutable.value = mutable.value.copy(roomConnected = if (connected) previous + peer else previous - peer)
                }, onVideo = { peer, track -> roomVideos.value = roomVideos.value + (peer to track) },
                onRemoteSharing = { peer, shared -> val previous = mutable.value.roomSharing; mutable.value = mutable.value.copy(roomSharing = if (shared) previous + peer else previous - peer) },
                onFailure = { message -> end(); mutable.value = mutable.value.copy(error = message) },
                onSharingStopped = { mutable.value = mutable.value.copy(sharing = false) })
            handleRoom(room)
        } catch (e: Exception) {
            runCatching { app.api.leaveCallRoom(app.tokens.authorization(), id).data() }
            finish("Não foi possível entrar no canal"); throw e
        }
    }
    private suspend fun handleRoom(room: CallRoom) {
        val engine = roomRtc ?: return
        mutable.value = mutable.value.copy(room = room, error = null)
        val ids = room.sessions.map { it.id }.toSet()
        roomVideos.value = roomVideos.value.filterKeys { it in ids }
        mutable.value = mutable.value.copy(roomConnected = mutable.value.roomConnected.intersect(ids), roomSharing = mutable.value.roomSharing.intersect(ids))
        engine.retain(ids)
        for (call in room.sessions) {
            val link = engine.link(call.id, call.caller == email)
            if (!link.sent && (call.caller == email || call.offer != null)) {
                val sdp = link.local(call.caller == email, if (call.callee == email) call.offer else null)
                if (roomRtc !== engine) return
                app.api.callAction(app.tokens.authorization(), call.id, CallAction(if (call.caller == email) "offer" else "answer", sdp)).data()
                link.sent = true
            }
            if (call.caller == email && call.answer != null && !link.answerSet) link.answer(call.answer)
        }
    }
    fun mute() { val value = !mutable.value.muted; rtc?.mute(value); roomRtc?.mute(value); mutable.value = mutable.value.copy(muted = value) }
    fun speaker() { val value = !mutable.value.speaker; rtc?.speaker(value); roomRtc?.speaker(value); mutable.value = mutable.value.copy(speaker = value) }
    fun share(permission: Intent) = execute {
        roomRtc?.let { engine ->
            if (mutable.value.sharing) return@execute
            service(screen = true)
            if (roomRtc !== engine) return@execute
            try { engine.share(permission); mutable.value = mutable.value.copy(sharing = true) }
            catch (e: Exception) { engine.stopSharing(); throw e }
            return@execute
        }
        if (!CallPolicy.mayShare(mutable.value.connected, mutable.value.sharing)) return@execute
        val engine = rtc ?: return@execute
        service(screen = true)
        if (rtc !== engine) return@execute
        try { engine.share(permission); mutable.value = mutable.value.copy(sharing = true) }
        catch (e: Exception) { engine.stopSharing(); throw e }
    }
    fun stopSharing() { rtc?.stopSharing(); roomRtc?.stopSharing(); mutable.value = mutable.value.copy(sharing = false, deviceAudio = false) }
    fun deviceAudio() = execute {
        val value = !mutable.value.deviceAudio
        rtc?.deviceSound(value); mutable.value = mutable.value.copy(deviceAudio = value)
    }
    fun permissionDenied() { mutable.value = mutable.value.copy(error = "Permita o microfone para iniciar ou aceitar a chamada.") }
    private fun watchConnection() {
        if (connectionTimer?.isActive == true) return
        connectionTimer = viewModelScope.launch {
            delay(35000)
            if (!mutable.value.connected && mutable.value.call != null) {
                end(); mutable.value = mutable.value.copy(error = "A conexão de mídia expirou. Verifique a rede e tente novamente.")
            }
        }
    }
    private fun finish(notice: String) {
        connectionTimer?.cancel(); connectionTimer = null
        remoteVideo.value = null; rtc?.close(); rtc = null
        roomVideos.value = emptyMap(); roomRtc?.close(); roomRtc = null
        CallMediaService.onReady = null; CallMediaService.onStopRequested = null
        app.stopService(Intent(app, CallMediaService::class.java))
        offerSent = false; answerSet = false; localSdp = null; acceptedLocally = false
        mutable.value = CallUiState(contacts = mutable.value.contacts, notice = notice)
    }
    override fun onCleared() { poll.cancel(); end(); finish("Chamada encerrada") }
}
