package com.freqforge.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sign
import kotlin.random.Random
import kotlin.concurrent.thread

class AudioEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val MAX_CHANNELS = 8
        private const val BUFFER_SIZE_MS = 80
        private const val CHUNK_SIZE_SAMPLES = 1024
        private const val TWO_PI = 2.0 * PI

        // Binaural beat presets for brainwave states
        data class Preset(val name: String, val displayName: String, val desc: String)
        val PRESETS = listOf(
            Preset("delta", "Delta", "1-4 Hz · Deep Sleep"),
            Preset("theta", "Theta", "4-8 Hz · Meditation"),
            Preset("alpha", "Alpha", "8-12 Hz · Relaxation"),
            Preset("beta", "Beta", "14-30 Hz · Focus"),
            Preset("gamma", "Gamma", "40+ Hz · Peak State"),
            Preset("random", "Random Calm", "∞ · Mixed Serenity")
        )
    }

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackThread: Thread? = null
    private val channels = mutableListOf<Channel>()
    private var masterVolume = 0.8f

    // Phase accumulators
    private val phases = DoubleArray(MAX_CHANNELS)
    private var sampleCounter = 0L

    // ---- Auto-pan (3D movement) ----
    private var autoPanEnabled = false
    private var autoPanSpeed = 0.08f        // Hz — slow sway
    private var autoPanDepth = 0.7f         // 0-1 how wide the sweep

    // ---- Binaural beat mode ----
    private var binauralMode = false
    private var binauralBeatFreq = 6f       // Hz — target beat

    // ---- Reverb (simple Schroeder-style) ----
    private var reverbEnabled = false
    private var reverbMix = 0.3f            // wet/dry
    private val combDelays = intArrayOf(155, 201, 277, 341)  // samples
    private val combGains = doubleArrayOf(0.55, 0.52, 0.48, 0.45)
    private val allPassDelay = 111
    private val allPassGain = 0.3
    private val combBuffers = Array(4) { DoubleArray(512) }
    private val allPassBufferL = DoubleArray(256)
    private val allPassBufferR = DoubleArray(256)
    private var combIndices = IntArray(4)
    private var allPassIdx = 0

    private val bufferSize: Int

    val isActive: Boolean get() = isPlaying
    val currentPresetName: String? get() = _currentPreset
    private var _currentPreset: String? = null

    init {
        for (i in 0 until MAX_CHANNELS) {
            channels.add(Channel(id = i, frequency = 220f * (1 + i * 0.25f)))
        }
        bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE * 2 * BUFFER_SIZE_MS / 1000 * 2)
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
        _currentPreset = null
    }

    fun setMasterVolume(vol: Float) { masterVolume = vol.coerceIn(0f, 1f) }
    fun getMasterVolume(): Float = masterVolume

    // ---- Auto-pan controls ----
    fun setAutoPan(enabled: Boolean, speed: Float = 0.08f, depth: Float = 0.7f) {
        autoPanEnabled = enabled
        autoPanSpeed = speed.coerceIn(0.01f, 1f)
        autoPanDepth = depth.coerceIn(0f, 1f)
    }
    fun isAutoPanEnabled(): Boolean = autoPanEnabled
    fun getAutoPanSpeed(): Float = autoPanSpeed
    fun getAutoPanDepth(): Float = autoPanDepth

    // ---- Binaural controls ----
    fun setBinauralMode(enabled: Boolean, beatFreq: Float = 6f) {
        binauralMode = enabled
        binauralBeatFreq = beatFreq.coerceIn(0.1f, 100f)
        if (enabled) applyBinauralPairing()
    }
    fun isBinauralMode(): Boolean = binauralMode
    fun getBinauralBeatFreq(): Float = binauralBeatFreq

    // ---- Reverb controls ----
    fun setReverb(enabled: Boolean, mix: Float = 0.3f) {
        reverbEnabled = enabled
        reverbMix = mix.coerceIn(0f, 0.7f)
    }
    fun isReverbEnabled(): Boolean = reverbEnabled
    fun getReverbMix(): Float = reverbMix

    // ---- Preset system ----
    fun applyPreset(presetName: String) {
        // Reset all
        for (ch in channels) {
            ch.isEnabled = false
            ch.volume = 0.3f
            ch.pan = 0f
            ch.waveformType = WaveformType.SINE
        }

        when (presetName) {
            "delta" -> {
                // Deep sleep: 1-4 Hz binaural beat
                pairChannel(0, 1, 200f, 1.5f)   // 1.5 Hz delta
                singleChannel(2, 100f, 0.15f, WaveformType.TRIANGLE)
                singleChannel(3, 80f, 0.12f, WaveformType.SINE)
                autoPanEnabled = false
                binauralMode = false
            }
            "theta" -> {
                // Meditation: 4-8 Hz binaural beat
                pairChannel(0, 1, 220f, 6f)     // 6 Hz theta
                pairChannel(2, 3, 280f, 5f)     // 5 Hz theta
                singleChannel(4, 180f, 0.15f, WaveformType.SINE)
                autoPanEnabled = true; autoPanSpeed = 0.06f; autoPanDepth = 0.6f
                binauralMode = false
            }
            "alpha" -> {
                // Relaxation: 8-12 Hz
                pairChannel(0, 1, 200f, 10f)    // 10 Hz alpha
                pairChannel(2, 3, 150f, 9f)     // 9 Hz alpha
                singleChannel(4, 250f, 0.12f, WaveformType.TRIANGLE)
                singleChannel(5, 88f, 0.1f, WaveformType.SINE)
                autoPanEnabled = true; autoPanSpeed = 0.08f; autoPanDepth = 0.5f
                binauralMode = false
            }
            "beta" -> {
                // Focus: 14-30 Hz
                pairChannel(0, 1, 180f, 18f)    // 18 Hz low beta
                pairChannel(2, 3, 240f, 22f)    // 22 Hz mid beta
                singleChannel(4, 320f, 0.2f, WaveformType.SINE)
                autoPanEnabled = false
                binauralMode = false
            }
            "gamma" -> {
                // Peak: 40+ Hz
                pairChannel(0, 1, 160f, 40f)    // 40 Hz gamma
                pairChannel(2, 3, 210f, 48f)    // 48 Hz gamma
                singleChannel(4, 380f, 0.25f, WaveformType.SAWTOOTH)
                singleChannel(5, 520f, 0.15f, WaveformType.SINE)
                autoPanEnabled = false
                binauralMode = false
            }
            "random" -> {
                // Random calming mix
                val rng = Random(System.currentTimeMillis())
                val enabledCount = rng.nextInt(3, 6)
                val used = mutableSetOf<Int>()
                for (j in 0 until enabledCount) {
                    val idx = (0 until MAX_CHANNELS).filter { it !in used }.random(rng)
                    used.add(idx)
                    val ch = channels[idx]
                    ch.frequency = rng.nextFloat() * 340f + 50f   // 50-390 Hz
                    ch.volume = rng.nextFloat() * 0.35f + 0.15f   // 0.15-0.5
                    ch.pan = rng.nextFloat() * 1.6f - 0.8f        // -0.8 to 0.8
                    ch.waveformType = WaveformType.entries[rng.nextInt(WaveformType.entries.size)]
                    ch.isEnabled = true
                }
                autoPanEnabled = true; autoPanSpeed = 0.05f + rng.nextFloat() * 0.12f; autoPanDepth = 0.5f + rng.nextFloat() * 0.4f
                binauralMode = false
            }
        }
        _currentPreset = presetName
        resetReverb()
    }

    private fun pairChannel(leftIdx: Int, rightIdx: Int, baseFreq: Float, beatFreq: Float) {
        channels[leftIdx].apply {
            frequency = baseFreq; volume = 0.4f; pan = -1f; isEnabled = true; waveformType = WaveformType.SINE
        }
        channels[rightIdx].apply {
            frequency = baseFreq + beatFreq; volume = 0.4f; pan = 1f; isEnabled = true; waveformType = WaveformType.SINE
        }
    }

    private fun singleChannel(idx: Int, freq: Float, vol: Float, wave: WaveformType) {
        channels[idx].apply {
            frequency = freq; volume = vol; pan = 0f; isEnabled = true; waveformType = wave
        }
    }

    private fun applyBinauralPairing() {
        for (i in 0 until 4) {
            val base = channels[i * 2]
            val paired = channels[i * 2 + 1]
            if (base.isEnabled || paired.isEnabled) {
                val avgFreq = (base.frequency + paired.frequency) / 2f
                base.frequency = avgFreq
                base.pan = -1f
                paired.frequency = avgFreq + binauralBeatFreq
                paired.pan = 1f
                paired.isEnabled = true
            }
        }
    }

    private fun resetReverb() {
        for (buf in combBuffers) buf.fill(0.0)
        allPassBufferL.fill(0.0)
        allPassBufferR.fill(0.0)
        combIndices.fill(0)
        allPassIdx = 0
    }

    // ---- Playback ----
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
        phases.fill(0.0)
        sampleCounter = 0L
        resetReverb()

        track.play()

        playbackThread = thread(name = "AudioMixer", isDaemon = true) {
            val shortBuffer = ShortArray(CHUNK_SIZE_SAMPLES * 2)
            while (isPlaying) {
                fillBuffer(shortBuffer)
                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
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
        phases.fill(0.0)
    }

    fun togglePlay(): Boolean {
        if (isPlaying) stop() else start()
        return isPlaying
    }

    // ---- Core mixer ----
    private fun fillBuffer(buffer: ShortArray) {
        val halfLen = buffer.size / 2

        // Pre-compute LFO phase for this chunk
        val lfoPhase = if (autoPanEnabled) {
            sin(TWO_PI * autoPanSpeed * sampleCounter / SAMPLE_RATE)
        } else 0.0

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

                // Effective pan: manual pan ± auto-pan LFO
                val effectivePan: Double
                if (binauralMode) {
                    effectivePan = if (chIdx % 2 == 0) -1.0 else 1.0
                } else if (autoPanEnabled) {
                    effectivePan = (ch.pan.toDouble() + lfoPhase * autoPanDepth)
                        .coerceIn(-1.0, 1.0)
                } else {
                    effectivePan = ch.pan.toDouble()
                }

                // Generate sample
                val rawSample = when (ch.waveformType) {
                    WaveformType.SINE -> sin(phase)
                    WaveformType.SQUARE -> sign(sin(phase))
                    WaveformType.SAWTOOTH -> ((phase / PI) % 2.0) - 1.0
                    WaveformType.TRIANGLE -> 2.0 * abs(((phase / PI) % 2.0) - 1.0) - 1.0
                }

                // Advance phase
                phases[chIdx] = phases[chIdx] + TWO_PI * freq / SAMPLE_RATE
                if (phases[chIdx] > TWO_PI) phases[chIdx] = phases[chIdx] - TWO_PI

                // Pan (stereo gain)
                val leftGain = vol * if (effectivePan <= 0.0) 1.0 else (1.0 - effectivePan)
                val rightGain = vol * if (effectivePan >= 0.0) 1.0 else (1.0 + effectivePan)

                leftSample += rawSample * leftGain
                rightSample += rawSample * rightGain
            }

            // Master volume
            var dryL = leftSample * masterVolume
            var dryR = rightSample * masterVolume

            // ---- Simple reverb (Schroeder) ----
            if (reverbEnabled) {
                val wet = processReverb(dryL, dryR)
                dryL = dryL * (1.0 - reverbMix) + wet.first * reverbMix
                dryR = dryR * (1.0 - reverbMix) + wet.second * reverbMix
            }

            // Clamp + convert to 16-bit
            val finalL = dryL.coerceIn(-1.0, 1.0)
            val finalR = dryR.coerceIn(-1.0, 1.0)

            buffer[i * 2] = (finalL * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i * 2 + 1] = (finalR * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        sampleCounter += halfLen
    }

    // ---- Schroeder reverb (parallel comb → series all-pass) ----
    private fun processReverb(inputL: Double, inputR: Double): Pair<Double, Double> {
        var outL = 0.0
        var outR = 0.0

        // Parallel comb filters
        for (c in 0..3) {
            val idx = combIndices[c]
            val delay = combDelays[c] % combBuffers[c].size
            val buf = combBuffers[c]
            val delayed = buf[idx]
            val newSampleL = inputL + delayed * combGains[c]
            val newSampleR = inputR + delayed * combGains[c]
            buf[idx] = (newSampleL + newSampleR) / 2.0
            outL += delayed
            outR += delayed
            combIndices[c] = (idx + 1) % buf.size
        }

        outL /= 4.0
        outR /= 4.0

        // Series all-pass filter
        val apIdx = allPassIdx
        val apSize = allPassBufferL.size
        val bufL = allPassBufferL
        val bufR = allPassBufferR
        val delayedL = bufL[apIdx]
        val delayedR = bufR[apIdx]
        bufL[apIdx] = outL + delayedL * allPassGain
        bufR[apIdx] = outR + delayedR * allPassGain
        outL = -outL + (bufL[apIdx] + delayedL * allPassGain) * (1.0 - allPassGain)
        outR = -outR + (bufR[apIdx] + delayedR * allPassGain) * (1.0 - allPassGain)
        allPassIdx = (apIdx + 1) % apSize

        return Pair(outL, outR)
    }
}
