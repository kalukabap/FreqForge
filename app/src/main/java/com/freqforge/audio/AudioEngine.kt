package com.freqforge.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.concurrent.thread

class AudioEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val MAX_CHANNELS = 8
        private const val BUFFER_SIZE_MS = 100      // 100ms buffer
        private const val CHUNK_SIZE_SAMPLES = 1024

        // Pre-compute phase step for common frequencies to reduce CPU
        private const val TWO_PI = 2.0 * PI
    }

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackThread: Thread? = null

    private val channels = mutableListOf<Channel>()
    private var masterVolume = 0.8f

    // Phase accumulators per channel (DoubleArray for precision)
    private val phases = DoubleArray(MAX_CHANNELS)

    // Buffer for one chunk of samples
    private val bufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(SAMPLE_RATE * 2 * BUFFER_SIZE_MS / 1000 * 2) // stereo 16-bit

    val isActive: Boolean get() = isPlaying

    init {
        // Initialize with default channels
        for (i in 0 until MAX_CHANNELS) {
            channels.add(Channel(id = i, frequency = 220f * (1 + i * 0.5f)))
        }
    }

    fun getChannels(): List<Channel> = channels.toList()

    fun updateChannel(id: Int, frequency: Float? = null, volume: Float? = null,
                      waveform: WaveformType? = null, enabled: Boolean? = null,
                      pan: Float? = null) {
        val ch = channels.getOrNull(id) ?: return
        frequency?.let { ch.frequency = it.coerceIn(20f, 20000f) }
        volume?.let { ch.volume = it.coerceIn(0f, 1f) }
        waveform?.let { ch.waveformType = it }
        enabled?.let { ch.isEnabled = it }
        pan?.let { ch.pan = it.coerceIn(-1f, 1f) }
    }

    fun setMasterVolume(vol: Float) {
        masterVolume = vol.coerceIn(0f, 1f)
    }

    fun getMasterVolume(): Float = masterVolume

    fun start() {
        if (isPlaying) return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }

        audioTrack = track
        isPlaying = true
        phases.fill(0f)

        track.play()

        playbackThread = thread(name = "AudioMixer", isDaemon = true) {
            val shortBuffer = ShortArray(CHUNK_SIZE_SAMPLES * 2) // stereo
            while (isPlaying) {
                fillBuffer(shortBuffer)
                track.write(shortBuffer, 0, shortBuffer.size)
            }
            track.stop()
            track.release()
            audioTrack = null
        }
    }

    fun stop() {
        isPlaying = false
        playbackThread?.join(500)
        playbackThread = null
        phases.fill(0f)
    }

    fun togglePlay(): Boolean {
        if (isPlaying) stop() else start()
        return isPlaying
    }

    private fun fillBuffer(buffer: ShortArray) {
        val halfLen = buffer.size / 2

        for (i in 0 until halfLen) {
            var leftSample = 0.0
            var rightSample = 0.0

            for (chIdx in 0 until MAX_CHANNELS) {
                val ch = channels[chIdx]
                if (!ch.isEnabled || ch.volume <= 0f) {
                    phases[chIdx] = phases[chIdx] + TWO_PI * ch.frequency / SAMPLE_RATE
                    if (phases[chIdx] > TWO_PI) phases[chIdx] = phases[chIdx] - TWO_PI
                    continue
                }

                val phase = phases[chIdx]
                val freq = ch.frequency
                val vol = ch.volume.toDouble()
                val pan = ch.pan.toDouble()

                // Generate waveform sample
                val value = when (ch.waveformType) {
                    WaveformType.SINE -> sin(phase)
                    WaveformType.SQUARE -> sign(sin(phase))
                    WaveformType.SAWTOOTH -> ((phase / PI) % 2.0) - 1.0
                    WaveformType.TRIANGLE -> 2.0 * kotlin.math.abs(((phase / PI) % 2.0) - 1.0) - 1.0
                }

                // Advance phase
                phases[chIdx] = phases[chIdx] + TWO_PI * freq / SAMPLE_RATE
                if (phases[chIdx] > TWO_PI) phases[chIdx] = phases[chIdx] - TWO_PI

                // Apply pan (stereo positioning)
                val leftGain = vol * if (pan <= 0.0) 1.0 else (1.0 - pan)
                val rightGain = vol * if (pan >= 0.0) 1.0 else (1.0 + pan)

                leftSample += value * leftGain
                rightSample += value * rightGain
            }

            // Apply master volume, clamp, convert to 16-bit
            val finalLeft = (leftSample * masterVolume).coerceIn(-1.0, 1.0)
            val finalRight = (rightSample * masterVolume).coerceIn(-1.0, 1.0)

            buffer[i * 2] = (finalLeft * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2 + 1] = (finalRight * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
