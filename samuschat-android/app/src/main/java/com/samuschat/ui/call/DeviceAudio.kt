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
class DeviceAudio {
    @Volatile private var capturing = false
    @Volatile private var playing = true
    private var record: AudioRecord? = null
    private var captureThread: Thread? = null
    private val queue = ArrayBlockingQueue<ByteArray>(5)
    private var receivedPackets = 0L
    private var nonzeroSamples = 0L
    private val player = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(maxOf(3200, AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private val playbackThread = Thread({
        player.play()
        while (playing) {
            try { val bytes = queue.take(); player.write(bytes, 0, bytes.size) }
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
    fun start(projection: MediaProjection, channel: DataChannel, onFailure: () -> Unit) {
        if (capturing) return
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .excludeUid(Process.myUid()).build()
        val recorder = AudioRecord.Builder().setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(maxOf(6400, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Áudio do dispositivo indisponível" }
        record = recorder
        recorder.startRecording(); capturing = true
        captureThread = Thread({
            val pcm = ByteArray(640)
            try {
                while (capturing) {
                    val read = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                    if (read < 0) { if (capturing) onFailure(); break }
                    if (capturing && read == pcm.size && channel.state() == DataChannel.State.OPEN && CallPolicy.mayQueueAudio(channel.bufferedAmount()))
                        channel.send(DataChannel.Buffer(ByteBuffer.wrap(pcm.copyOf()), true))
                }
            } catch (_: Exception) { if (capturing) onFailure() }
        }, "call-device-audio-capture").apply { start() }
    }

    fun stopCapture() {
        capturing = false
        record?.let { runCatching { it.stop() }; captureThread?.join(1000); it.release() }
        record = null; captureThread = null
    }
    fun close() {
        stopCapture(); playing = false; queue.clear(); playbackThread.interrupt()
        runCatching { player.stop() }; playbackThread.join(1000); player.release()
    }
}
