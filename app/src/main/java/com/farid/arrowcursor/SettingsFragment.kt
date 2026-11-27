package com.farid.arrowcursor

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = CursorSettings(requireContext())

        val seekSpeed: SeekBar = view.findViewById(R.id.seek_speed)
        val textSpeedValue: TextView = view.findViewById(R.id.text_speed_value)
        val seekSize: SeekBar = view.findViewById(R.id.seek_size)
        val textSizeValue: TextView = view.findViewById(R.id.text_size_value)
        val radioColor: RadioGroup = view.findViewById(R.id.radio_color)
        val radioClickKey: RadioGroup = view.findViewById(R.id.radio_click_key)
        val switchAutoPause: SwitchMaterial = view.findViewById(R.id.switch_auto_pause)
        val radioLanguage: RadioGroup = view.findViewById(R.id.radio_language)

        // Speed: 8..64 px, SeekBar max 56, progress = speed - 8.
        seekSpeed.max = 56
        seekSpeed.progress = settings.speedPx - 8
        textSpeedValue.text = "${settings.speedPx} ${getString(R.string.speed_unit_px)}"
        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 8
                CursorSettings(requireContext()).speedPx = speed
                textSpeedValue.text = "$speed ${getString(R.string.speed_unit_px)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No action needed.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // No action needed.
            }
        })

        // Size: 24..64 dp, SeekBar max 40, progress = size - 24.
        seekSize.max = 40
        seekSize.progress = settings.sizeDp - 24
        textSizeValue.text = "${settings.sizeDp} ${getString(R.string.size_unit_dp)}"
        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 24
                CursorSettings(requireContext()).sizeDp = size
                textSizeValue.text = "$size ${getString(R.string.size_unit_dp)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No action needed.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // No action needed.
            }
        })

        // Cursor colour.
        when (settings.cursorColor) {
            Color.BLACK -> radioColor.check(R.id.radio_color_black)
            Color.YELLOW -> radioColor.check(R.id.radio_color_yellow)
            Color.GREEN -> radioColor.check(R.id.radio_color_green)
            Color.RED -> radioColor.check(R.id.radio_color_red)
            else -> radioColor.check(R.id.radio_color_white)
        }
        radioColor.setOnCheckedChangeListener { _, checkedId ->
            val color = when (checkedId) {
                R.id.radio_color_black -> Color.BLACK
                R.id.radio_color_yellow -> Color.YELLOW
                R.id.radio_color_green -> Color.GREEN
                R.id.radio_color_red -> Color.RED
                else -> Color.WHITE
            }
            CursorSettings(requireContext()).cursorColor = color
        }

        // Click key.
        when (settings.clickKey) {
            "space" -> radioClickKey.check(R.id.radio_click_space)
            "enter" -> radioClickKey.check(R.id.radio_click_enter)
            else -> radioClickKey.check(R.id.radio_click_both)
        }
        radioClickKey.setOnCheckedChangeListener { _, checkedId ->
            val key = when (checkedId) {
                R.id.radio_click_space -> "space"
                R.id.radio_click_enter -> "enter"
                else -> "both"
            }
            CursorSettings(requireContext()).clickKey = key
        }

        // Auto-pause while typing.
        switchAutoPause.isChecked = settings.autoPause
        switchAutoPause.setOnCheckedChangeListener { _, isChecked ->
            CursorSettings(requireContext()).autoPause = isChecked
        }

        // Language.
        when (settings.language) {
            "en" -> radioLanguage.check(R.id.radio_language_english)
            "bn" -> radioLanguage.check(R.id.radio_language_bengali)
            else -> radioLanguage.check(R.id.radio_language_system)
        }
        radioLanguage.setOnCheckedChangeListener { _, checkedId ->
            val code = when (checkedId) {
                R.id.radio_language_english -> "en"
                R.id.radio_language_bengali -> "bn"
                else -> "system"
            }
            LanguageHelper.setLanguage(requireContext(), code)
            requireActivity().recreate()
        }
    }
}
