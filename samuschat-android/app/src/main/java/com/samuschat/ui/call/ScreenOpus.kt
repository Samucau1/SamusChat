package com.samuschat.ui.call

import android.content.Context
import android.media.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue

/** Versioned packets: magic + unsigned sequence + one 20 ms Opus frame. */
object ScreenAudioWire {
    const val READY = "audio:opus-v1"
    fun isOpus(bytes: ByteArray) = bytes.size in 9..4096 && bytes[0] == 83.toByte() && bytes[1] == 65.toByte() && bytes[2] == 1.toByte() && bytes[3] == 0.toByte()
    fun packet(data: ByteArray, sequence: Int): ByteArray = ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(83, 65, 1, 0)).putInt(sequence).put(data).array()
    fun legacy(pcm: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteBuffer.allocate(pcm.size / 3).order(ByteOrder.LITTLE_ENDIAN)
        while (input.remaining() >= 6) out.putShort(((input.short.toInt() + input.short.toInt() + input.short.toInt()) / 3).toShort())
        return out.array()
    }
}

internal fun opusHeader(): ByteArray = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
    .put("OpusHead".toByteArray()).put(1).put(1).putShort(0).putInt(48000).putShort(0).put(0).array()

/** Uses the OS codec: no bundled encoder or new native dependency. */
class ScreenOpusEncoder(private val packetSequence: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger()) : AutoCloseable {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val info = MediaCodec.BufferInfo()
    private var sequence = 0
    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1920)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
        } catch (failure: Exception) { codec.release(); throw failure }
    }
    fun encode(pcm: ByteArray, send: (ByteArray) -> Unit) {
        val input = codec.dequeueInputBuffer(0)
        if (input >= 0) {
            codec.getInputBuffer(input)!!.apply { clear(); put(pcm) }
            codec.queueInputBuffer(input, 0, pcm.size, sequence++ * 20000L, 0)
        }
        while (true) {
            val output = codec.dequeueOutputBuffer(info, 0)
            if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (output < 0) break
            try {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val bytes = ByteArray(info.size)
                    codec.getOutputBuffer(output)!!.apply { position(info.offset); limit(info.offset + info.size); get(bytes) }
                    if (bytes.size <= 4088) send(ScreenAudioWire.packet(bytes, packetSequence.getAndIncrement()))
                }
            } finally { codec.releaseOutputBuffer(output, false) }
        }
    }
    override fun close() { runCatching { codec.stop() }; codec.release() }
}

class ScreenOpusPlayer(private val context: Context) : AutoCloseable {
    private val queue = ArrayBlockingQueue<ByteArray>(5)
    @Volatile private var running = true
    private val thread = Thread({
        var codec: MediaCodec? = null
        var player: AudioTrack? = null
        try {
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(opusHeader()))
            format.setByteBuffer("csd-1", ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(0).apply { flip() })
            format.setByteBuffer("csd-2", ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(80_000_000).apply { flip() })
            codec.configure(format, null, null, 0); codec.start()
            val info = MediaCodec.BufferInfo()
            var last = -1L; var frames = 0L
            while (running) {
                val packet = queue.take()
                val sequence = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(4).toLong() and 0xffffffffL
                if (sequence <= last) continue
                last = sequence
                val input = codec.dequeueInputBuffer(5000)
                if (input < 0) continue
                codec.getInputBuffer(input)!!.apply { clear(); put(packet, 8, packet.size - 8) }
                codec.queueInputBuffer(input, 0, packet.size - 8, sequence * 20000L, 0)
                while (running) {
                    val output = codec.dequeueOutputBuffer(info, 0)
                    if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outputFormat = codec.outputFormat
                        val rate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = if (outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        player?.release()
                        player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(channels).build())
                            .setBufferSizeInBytes(maxOf(9600, AudioTrack.getMinBufferSize(rate, channels, AudioFormat.ENCODING_PCM_16BIT))).setTransferMode(AudioTrack.MODE_STREAM).build()
                        preferScreenAudioOutput(context, player); player.play(); continue
                    }
                    if (output < 0) break
                    try {
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(output)!!
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            player?.write(buffer, info.size, AudioTrack.WRITE_BLOCKING)
                            frames++
                            if (com.samuschat.BuildConfig.DEBUG && frames % 50 == 0L) android.util.Log.i("CallStats", "screenOpusFrames=$frames head=${player?.playbackHeadPosition} route=${player?.routedDevice?.type}")
                        }
                    } finally { codec.releaseOutputBuffer(output, false) }
                }
            }
        } catch (_: InterruptedException) { /* normal shutdown */ }
        catch (failure: Exception) { if (com.samuschat.BuildConfig.DEBUG) android.util.Log.e("CallStats", "screenOpusDecodeFailed", failure) }
        finally { running = false; runCatching { player?.stop() }; player?.release(); runCatching { codec?.stop() }; codec?.release() }
    }, "screen-opus-playback").apply { start() }
    fun receive(bytes: ByteArray) { if (running && ScreenAudioWire.isOpus(bytes) && !queue.offer(bytes)) { queue.poll(); queue.offer(bytes) } }
    override fun close() { running = false; queue.clear(); thread.interrupt(); thread.join(1000) }
}
