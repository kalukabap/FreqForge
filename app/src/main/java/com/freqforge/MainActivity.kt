package com.freqforge

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.freqforge.audio.AudioEngine
import com.freqforge.audio.Channel
import com.freqforge.audio.WaveformType
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private val engine = AudioEngine()
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupMasterControls()
        buildChannelPanels()
    }

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

    private fun buildChannelPanels() {
        val container = findViewById<LinearLayout>(R.id.channelContainer)

        for (ch in engine.getChannels()) {
            val panel = createChannelPanel(ch)
            container.addView(panel)

            // Spacing between panels
            val spacer = Space(this)
            spacer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            )
            container.addView(spacer)
        }
    }

    private fun createChannelPanel(channel: Channel): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }

            setCardBackgroundColor(0xFF0F3460.toInt())
            cardElevation = 4f
            radius = 12f
            contentPaddingLeft = 16
            contentPaddingTop = 12
            contentPaddingRight = 16
            contentPaddingBottom = 16
        }

        // Main vertical layout inside the card
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ── Row 1: Channel header with enable toggle ──
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val enableToggle = SwitchMaterial(this).apply {
            text = "Ch ${channel.id + 1}"
            isChecked = channel.isEnabled
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

        val tvFreqLabel = TextView(this).apply {
            text = "Hz"
            textSize = 14f
            setTextColor(0xFFA0A0B0.toInt())
            gravity = android.view.Gravity.END
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

        headerRow.addView(enableToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val freqContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        freqContainer.addView(etFrequency)
        freqContainer.addView(tvFreqLabel)

        headerRow.addView(freqContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        mainLayout.addView(headerRow)

        // ── Row 2: Frequency Slider (fine-tune) ──
        val freqSlider = Slider(this).apply {
            valueFrom = 20f
            valueTo = 8000f
            value = channel.frequency
            setLabelFormatter { "${it.toInt()} Hz" }
        }

        val freqLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tvFreqMin = TextView(this).apply { text = "20Hz"; setTextColor(0xFFA0A0B0.toInt()); textSize = 11f }
        val tvFreqMax = TextView(this).apply { text = "8kHz"; setTextColor(0xFFA0A0B0.toInt()); textSize = 11f }
        freqLayout.addView(tvFreqMin)
        freqLayout.addView(freqSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        freqLayout.addView(tvFreqMax)
        mainLayout.addView(freqLayout)

        // ── Row 3: Volume + Pan ──
        val volPanRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Volume
        val volLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tvVolLabel = TextView(this).apply {
            text = "Vol"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
        }
        val volSlider = Slider(this).apply {
            valueFrom = 0f
            valueTo = 100f
            value = channel.volume * 100f
            setLabelFormatter { "${it.toInt()}%" }
        }
        volLayout.addView(tvVolLabel)
        volLayout.addView(volSlider)
        volPanRow.addView(volLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Pan
        val panLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tvPanLabel = TextView(this).apply {
            text = "Pan"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 11f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        val panSlider = Slider(this).apply {
            valueFrom = -100f
            valueTo = 100f
            value = channel.pan * 100f
            setLabelFormatter {
                when {
                    it < -30 -> "L"
                    it > 30 -> "R"
                    else -> "C"
                }
            }
        }
        panLayout.addView(tvPanLabel)
        panLayout.addView(panSlider)
        volPanRow.addView(panLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        mainLayout.addView(volPanRow)

        // ── Row 4: Waveform Type Buttons ──
        val waveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val tvWaveLabel = TextView(this).apply {
            text = "Wave:"
            setTextColor(0xFFA0A0B0.toInt())
            textSize = 14f
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        waveRow.addView(tvWaveLabel)

        val waveGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

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

        // ── Wire up listeners ──
        val chId = channel.id

        enableToggle.setOnCheckedChangeListener { _, isChecked ->
            engine.updateChannel(chId, enabled = isChecked)
        }

        // Frequency edit text → engine
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

        // Frequency slider → edit text + engine
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

        // Waveform radio group
        for ((idx, rb) in waveButtons.withIndex()) {
            rb.setOnClickListener {
                engine.updateChannel(chId, waveform = WaveformType.entries[idx])
                for (other in waveButtons) {
                    other.isChecked = (other == rb)
                }
            }
        }

        return card
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }
}
