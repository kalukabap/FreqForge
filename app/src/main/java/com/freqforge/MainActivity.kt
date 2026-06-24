package com.freqforge

import android.icu.text.DecimalFormat
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.freqforge.audio.AudioEngine
import com.freqforge.audio.Channel
import com.freqforge.audio.WaveformType
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private val engine = AudioEngine()
    private var isPlaying = false
    private val freqFormat = DecimalFormat("#.#")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupMasterControls()
        buildEffectsCard()
        buildChannelPanels()
    }

    // ============================================================
    // Master controls
    // ============================================================
    private fun setupMasterControls() {
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            isPlaying = engine.togglePlay()
            (it as Button).text = if (isPlaying) "⏹" else "▶"
        }

        val sliderMaster = findViewById<Slider>(R.id.sliderMasterVol)
        sliderMaster.addOnChangeListener { _, value, _ ->
            engine.setMasterVolume(value / 100f)
            findViewById<TextView>(R.id.tvMasterVolVal).text = "${value.toInt()}%"
        }
    }

    // ============================================================
    // Presets & Effects card
    // ============================================================
    private fun buildEffectsCard() {
        val container = findViewById<LinearLayout>(R.id.effectsContainer)

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(0xFF16213E.toInt())
            cardElevation = 4f
            radius = 12f
            setContentPadding(16, 12, 16, 16)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ---- Section: Presets ----
        val tvPresetLabel = TextView(this).apply {
            text = "BRAINWAVE PRESETS"
            setTextColor(0xFFE94560.toInt())
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(tvPresetLabel)

        val tvPresetDesc = TextView(this).apply {
            id = View.generateViewId()
            text = "Tap a preset to configure all channels"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
        }
        root.addView(tvPresetDesc)

        // Preset buttons in 3 rows, 2 cols
        val presetRows = listOf(
            AudioEngine.PRESETS.subList(0, 2),
            AudioEngine.PRESETS.subList(2, 4),
            AudioEngine.PRESETS.subList(4, 6)
        )

        for (rowPresets in presetRows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 6, 0, 0) }
            }

            for (preset in rowPresets) {
                val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = preset.displayName
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply {
                        setMargins(4, 0, 4, 0)
                    }
                    setTextColor(0xFFFFFFFF.toInt())
                    strokeColor = android.content.res.ColorStateList.valueOf(0xFFE94560.toInt())
                    cornerRadius = 20
                    setOnClickListener {
                        engine.applyPreset(preset.name)
                        tvPresetDesc.text = "${preset.displayName}: ${preset.desc}"
                        // Sync channel panel states
                        rebuildChannelPanels()
                    }
                }
                row.addView(btn)
            }
            root.addView(row)
        }

        // Spacer
        root.addView(Space(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 24
        ))

        // ---- Section: 3D Auto-Pan ----
        val tvFxLabel = TextView(this).apply {
            text = "SPATIAL EFFECTS"
            setTextColor(0xFFE94560.toInt())
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(tvFxLabel)

        // Auto-Pan row
        val autoPanRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val autoPanToggle = SwitchMaterial(this).apply {
            text = "Auto-Pan (3D Movement)"
            isChecked = false
            setTextColor(0xFFFFFFFF.toInt())
        }
        autoPanRow.addView(autoPanToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(autoPanRow)

        val autoPanSpeedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvSpeed = TextView(this).apply {
            text = "Speed"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
        }
        val autoPanSpeed = Slider(this).apply {
            valueFrom = 1f
            valueTo = 40f
            value = 8f
            setLabelFormatter { "${freqFormat.format(it / 100f)} Hz" }
        }
        val tvSpeedVal = TextView(this).apply {
            text = "0.08 Hz"
            setTextColor(0xFF4ECDC4.toInt())
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        autoPanSpeedRow.addView(tvSpeed)
        autoPanSpeedRow.addView(autoPanSpeed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        autoPanSpeedRow.addView(tvSpeedVal)
        autoPanSpeedRow.setPadding(0, 0, 0, 4)
        root.addView(autoPanSpeedRow)

        val autoPanDepthRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvDepth = TextView(this).apply {
            text = "Width"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
        }
        val autoPanDepth = Slider(this).apply {
            valueFrom = 0f
            valueTo = 100f
            value = 70f
            setLabelFormatter { "${it.toInt()}%" }
        }
        val tvDepthVal = TextView(this).apply {
            text = "70%"
            setTextColor(0xFF4ECDC4.toInt())
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        autoPanDepthRow.addView(tvDepth)
        autoPanDepthRow.addView(autoPanDepth, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        autoPanDepthRow.addView(tvDepthVal)
        root.addView(autoPanDepthRow)

        // Wire auto-pan
        autoPanToggle.setOnCheckedChangeListener { _, checked ->
            engine.setAutoPan(checked, autoPanSpeed.value / 100f, autoPanDepth.value / 100f)
        }
        autoPanSpeed.addOnChangeListener { _, value, _ ->
            tvSpeedVal.text = "${freqFormat.format(value / 100f)} Hz"
            if (autoPanToggle.isChecked)
                engine.setAutoPan(true, value / 100f, autoPanDepth.value / 100f)
        }
        autoPanDepth.addOnChangeListener { _, value, _ ->
            tvDepthVal.text = "${value.toInt()}%"
            if (autoPanToggle.isChecked)
                engine.setAutoPan(true, autoPanSpeed.value / 100f, value / 100f)
        }

        // ---- Section: Binaural Mode ----
        val binauralRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val binauralToggle = SwitchMaterial(this).apply {
            text = "Binaural Beat Mode"
            isChecked = false
            setTextColor(0xFFFFFFFF.toInt())
        }
        binauralRow.addView(binauralToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(binauralRow)

        val binauralFreqRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvBeat = TextView(this).apply {
            text = "Beat Freq"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
        }
        val binauralBeat = Slider(this).apply {
            valueFrom = 0.5f
            valueTo = 50f
            value = 6f
            setLabelFormatter { "${freqFormat.format(it)} Hz" }
        }
        val tvBeatVal = TextView(this).apply {
            text = "6.0 Hz · Theta"
            setTextColor(0xFF4ECDC4.toInt())
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        binauralFreqRow.addView(tvBeat)
        binauralFreqRow.addView(binauralBeat, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        binauralFreqRow.addView(tvBeatVal)
        root.addView(binauralFreqRow)

        // Beat freq state labels
        val beatStateMap = mapOf(
            0.5f to "Delta", 1f to "Delta", 2f to "Delta", 3f to "Delta",
            4f to "Theta", 5f to "Theta", 6f to "Theta", 7f to "Theta",
            8f to "Alpha", 9f to "Alpha", 10f to "Alpha", 11f to "Alpha",
            12f to "Low Beta", 14f to "Beta", 18f to "Beta", 22f to "Beta", 28f to "Beta",
            30f to "Gamma", 40f to "Gamma"
        )

        fun beatStateName(freq: Float): String {
            return beatStateMap.entries.firstOrNull { freq <= it.key }?.value
                ?: if (freq > 40) "Gamma" else if (freq > 30) "Gamma" else "Mixed"
        }

        binauralToggle.setOnCheckedChangeListener { _, checked ->
            engine.setBinauralMode(checked, binauralBeat.value)
            if (checked) rebuildChannelPanels()
        }
        binauralBeat.addOnChangeListener { _, value, _ ->
            val state = beatStateName(value)
            tvBeatVal.text = "${freqFormat.format(value)} Hz · $state"
            if (binauralToggle.isChecked) {
                engine.setBinauralMode(true, value)
                rebuildChannelPanels()
            }
        }

        // ---- Section: Reverb ----
        val reverbRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reverbToggle = SwitchMaterial(this).apply {
            text = "Reverb (Ambient Space)"
            isChecked = false
            setTextColor(0xFFFFFFFF.toInt())
        }
        reverbRow.addView(reverbToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(reverbRow)

        val reverbMixRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvMix = TextView(this).apply {
            text = "Wet/Dry"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
        }
        val reverbMix = Slider(this).apply {
            valueFrom = 0f
            valueTo = 70f
            value = 30f
            setLabelFormatter { "${it.toInt()}%" }
        }
        val tvMixVal = TextView(this).apply {
            text = "30%"
            setTextColor(0xFF4ECDC4.toInt())
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        reverbMixRow.addView(tvMix)
        reverbMixRow.addView(reverbMix, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        reverbMixRow.addView(tvMixVal)
        root.addView(reverbMixRow)

        reverbToggle.setOnCheckedChangeListener { _, checked ->
            engine.setReverb(checked, reverbMix.value / 100f)
        }
        reverbMix.addOnChangeListener { _, value, _ ->
            tvMixVal.text = "${value.toInt()}%"
            if (reverbToggle.isChecked)
                engine.setReverb(true, value / 100f)
        }

        card.addView(root)
        container.addView(card)
    }

    // ============================================================
    // Channel panels
    // ============================================================
    private var channelPanelViews = mutableListOf<android.view.View>()

    private fun buildChannelPanels() {
        val container = findViewById<LinearLayout>(R.id.channelContainer)
        container.removeAllViews()
        channelPanelViews.clear()

        for (ch in engine.getChannels()) {
            val panel = createChannelPanel(ch)
            container.addView(panel)
            channelPanelViews.add(panel)
        }
    }

    private fun rebuildChannelPanels() {
        val container = findViewById<LinearLayout>(R.id.channelContainer)
        container.removeAllViews()
        channelPanelViews.clear()

        for (ch in engine.getChannels()) {
            val panel = createChannelPanel(ch)
            container.addView(panel)
            channelPanelViews.add(panel)
        }
    }

    private fun createChannelPanel(channel: Channel): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }

            setCardBackgroundColor(0xFF0F3460.toInt())
            cardElevation = 4f
            radius = 12f
            setContentPadding(16, 12, 16, 16)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ── Row 1: header ──
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val enableToggle = SwitchMaterial(this).apply {
            text = "Ch ${channel.id + 1}"
            isChecked = channel.isEnabled
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

        val etFrequency = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 20f
            setTextColor(0xFF4ECDC4.toInt())
            setText(channel.frequency.toInt().toString())
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setBackgroundResource(android.R.drawable.editbox_background)
            minEms = 5
        }

        val tvFreqLabel = TextView(this).apply {
            text = "Hz"
            textSize = 14f
            setTextColor(0xFFA0A0B0.toInt())
            gravity = android.view.Gravity.END
        }

        headerRow.addView(enableToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val freqContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        freqContainer.addView(etFrequency)
        freqContainer.addView(tvFreqLabel)
        headerRow.addView(freqContainer)

        mainLayout.addView(headerRow)

        // ── Row 2: Frequency slider ──
        val freqSlider = Slider(this).apply {
            valueFrom = 20f
            valueTo = 8000f
            value = channel.frequency
            setLabelFormatter { "${it.toInt()} Hz" }
        }

        val freqRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvFreqMin = TextView(this).apply { text = "20Hz"; setTextColor(0xFFA0A0B0.toInt()); textSize = 11f }
        val tvFreqMax = TextView(this).apply { text = "8kHz"; setTextColor(0xFFA0A0B0.toInt()); textSize = 11f }
        freqRow.addView(tvFreqMin)
        freqRow.addView(freqSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        freqRow.addView(tvFreqMax)
        mainLayout.addView(freqRow)

        // ── Row 3: Volume + Pan ──
        val volPanRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val volLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tvVolLabel = TextView(this).apply { text = "Vol"; setTextColor(0xFFA0A0B0.toInt()); textSize = 11f }
        val volSlider = Slider(this).apply {
            valueFrom = 0f; valueTo = 100f; value = channel.volume * 100f
            setLabelFormatter { "${it.toInt()}%" }
        }
        volLayout.addView(tvVolLabel)
        volLayout.addView(volSlider)

        val panLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tvPanLabel = TextView(this).apply {
            text = "Pan"; setTextColor(0xFFA0A0B0.toInt()); textSize = 11f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        val panSlider = Slider(this).apply {
            valueFrom = -100f; valueTo = 100f; value = channel.pan * 100f
            setLabelFormatter { when { it < -30 -> "L"; it > 30 -> "R"; else -> "C" } }
        }
        panLayout.addView(tvPanLabel)
        panLayout.addView(panSlider)

        volPanRow.addView(volLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        volPanRow.addView(panLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        mainLayout.addView(volPanRow)

        // ── Row 4: Waveform ──
        val waveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvWaveLabel = TextView(this).apply {
            text = "Wave:"; setTextColor(0xFFA0A0B0.toInt()); textSize = 14f
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        waveRow.addView(tvWaveLabel)

        val waveGroup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val waveButtons = mutableListOf<RadioButton>()
        for (wave in WaveformType.entries) {
            val rb = RadioButton(this).apply {
                text = wave.displayName
                setTextColor(0xFFFFFFFF.toInt())
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFFE94560.toInt())
                isChecked = (wave == channel.waveformType)
            }
            waveButtons.add(rb)
            waveGroup.addView(rb)
        }
        waveRow.addView(waveGroup, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        mainLayout.addView(waveRow)

        card.addView(mainLayout)

        // ── Wire listeners ──
        val chId = channel.id

        enableToggle.setOnCheckedChangeListener { _, isChecked ->
            engine.updateChannel(chId, enabled = isChecked)
        }

        etFrequency.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val freq = s.toString().toFloatOrNull()
                if (freq != null && freq in 20f..20000f) {
                    engine.updateChannel(chId, frequency = freq)
                    freqSlider.value = freq
                }
            }
        })

        freqSlider.addOnChangeListener { _, value, _ ->
            val freq = value.toInt()
            etFrequency.removeTextChangedListener(etFrequency.tag as? TextWatcher)
            etFrequency.setText(freq.toString())
            etFrequency.setSelection(etFrequency.text.length)
            engine.updateChannel(chId, frequency = value)
        }

        volSlider.addOnChangeListener { _, value, _ ->
            engine.updateChannel(chId, volume = value / 100f)
        }

        panSlider.addOnChangeListener { _, value, _ ->
            engine.updateChannel(chId, pan = value / 100f)
        }

        for ((idx, rb) in waveButtons.withIndex()) {
            rb.setOnClickListener {
                engine.updateChannel(chId, waveform = WaveformType.entries[idx])
                for (other in waveButtons) { other.isChecked = (other == rb) }
            }
        }

        return card
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }
}
