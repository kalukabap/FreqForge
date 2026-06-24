package com.freqforge.audio

data class Channel(
    val id: Int,
    var frequency: Float = 440f,
    var volume: Float = 0.5f,
    var waveformType: WaveformType = WaveformType.SINE,
    var isEnabled: Boolean = false,
    var pan: Float = 0f      // -1.0 (left) to 1.0 (right)
)
