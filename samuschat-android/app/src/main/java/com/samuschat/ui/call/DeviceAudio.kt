package com.samuschat.ui.call

import android.content.Context
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/** Bounded, separate PCM stream; microphone always remains under WebRTC's audio processing. */
class DeviceAudio(private val context: Context) {
    private val opusReady = java.util.concurrent.ConcurrentHashMap.newKeySet<DataChannel>()
    private val opusPlayers = java.util.concurrent.ConcurrentHashMap<DataChannel, ScreenOpusPlayer>()
    fun advertise(channel: DataChannel) {
        if (channel.state() == DataChannel.State.OPEN) channel.send(DataChannel.Buffer(ByteBuffer.wrap(ScreenAudioWire.READY.toByteArray()), false))
    }
    fun receive(channel: DataChannel, buffer: DataChannel.Buffer) {
        if (buffer.data.remaining() !in 1..4096) return
        val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
        if (!buffer.binary) { if (String(bytes, Charsets.UTF_8) == ScreenAudioWire.READY) opusReady.add(channel); return }
        if (ScreenAudioWire.isOpus(bytes)) opusPlayers.getOrPut(channel) { ScreenOpusPlayer(context) }.receive(bytes)
        else receive(bytes)
    }
    fun detach(channel: DataChannel) { opusReady.remove(channel); opusPlayers.remove(channel)?.close() }
    @Volatile private var capturing = false
    @Volatile private var playing = true
    private var record: AudioRecord? = null
    private var captureThread: Thread? = null
    private val opusSequence = java.util.concurrent.atomic.AtomicInteger()
    private val queue = ArrayBlockingQueue<ByteArray>(5)
    private var receivedPackets = 0L
    private var nonzeroSamples = 0L
    private var writtenSamples = 0L
    private val player = AudioTrack.Builder()
        // Screen sound is media, not a second VoIP voice stream. Voice usage
        // routes it to the earpiece and the call-volume slider on this device.
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(maxOf(3200, AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private val playbackThread = Thread({
        preferScreenAudioOutput(context, player)
        player.play()
        while (playing) {
            try {
                val bytes = queue.take()
                val written = player.write(bytes, 0, bytes.size)
                if (written > 0) writtenSamples += written / 2
                if (com.samuschat.BuildConfig.DEBUG && writtenSamples % 16000L == 0L)
                    android.util.Log.i("CallStats", "deviceAudioWritten=$writtenSamples head=${player.playbackHeadPosition} route=${player.routedDevice?.type} writeResult=$written")
            }
            catch (_: InterruptedException) { break }
        }
    }, "call-device-audio-playback").apply { start() }

    fun receive(bytes: ByteArray) {
        if (playing && CallPolicy.validAudioPacket(bytes.size)) {
            receivedPackets++
            for (i in bytes.indices step 2) if (bytes[i] != 0.toByte() || bytes[i + 1] != 0.toByte()) nonzeroSamples++
            if (com.samuschat.BuildConfig.DEBUG && receivedPackets % 50L == 0L)
                android.util.Log.i("CallStats", "deviceAudioPackets=$receivedPackets nonzeroSamples=$nonzeroSamples")
            if (!queue.offer(bytes)) { queue.poll(); queue.offer(bytes) }
        }
    }

    @RequiresApi(29)
    @android.annotation.SuppressLint("MissingPermission")
    fun start(projection: MediaProjection, channels: () -> List<DataChannel>, onFailure: () -> Unit) {
        if (capturing) return
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .excludeUid(Process.myUid()).build()
        val recorder = AudioRecord.Builder().setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(maxOf(19200, AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Áudio do dispositivo indisponível" }
        record = recorder
        recorder.startRecording(); capturing = true
        captureThread = Thread({
            val pcm = ByteArray(1920)
            val encoder = runCatching { ScreenOpusEncoder(opusSequence) }.getOrNull()
            try {
                while (capturing) {
                    val read = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                    if (read < 0) { if (capturing) onFailure(); break }
                    if (capturing && read == pcm.size) {
                        val targets = channels().filter { it.state() == DataChannel.State.OPEN }
                        val modern = targets.filter { it in opusReady && encoder != null }
                        if (modern.isNotEmpty()) encoder?.encode(pcm) { packet -> for (channel in modern) if (channel.bufferedAmount() < 800) channel.send(DataChannel.Buffer(ByteBuffer.wrap(packet), true)) }
                        val legacy = targets.filter { it !in modern }
                        if (legacy.isNotEmpty()) {
                            val packet = ScreenAudioWire.legacy(pcm)
                            for (channel in legacy) if (CallPolicy.mayQueueAudio(channel.bufferedAmount())) channel.send(DataChannel.Buffer(ByteBuffer.wrap(packet), true))
                        }
                    }
                }
            } catch (_: Exception) { if (capturing) onFailure() }
            finally { encoder?.close() }
        }, "call-device-audio-capture").apply { start() }
    }

    fun stopCapture() {
        capturing = false
        record?.let { runCatching { it.stop() }; captureThread?.join(1000); it.release() }
        record = null; captureThread = null
    }
    fun close() {
        stopCapture(); opusReady.clear(); opusPlayers.values.forEach { it.close() }; opusPlayers.clear(); playing = false; queue.clear(); playbackThread.interrupt()
        runCatching { player.stop() }; playbackThread.join(1000); player.release()
    }
}

internal fun preferScreenAudioOutput(context: Context, player: AudioTrack) {
    val manager = context.getSystemService(AudioManager::class.java)
    val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val external = outputs.firstOrNull { it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO) }
    player.preferredDevice = external ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}
