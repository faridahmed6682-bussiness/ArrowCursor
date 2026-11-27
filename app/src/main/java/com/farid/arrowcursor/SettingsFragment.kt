package com.farid.arrowcursor

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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

        // On-screen control panel (touch arrows + scroll).
        val switchControlPanel: SwitchMaterial = view.findViewById(R.id.switch_control_panel)
        switchControlPanel.isChecked = settings.showControlPanel
        switchControlPanel.setOnCheckedChangeListener { _, isChecked ->
            CursorSettings(requireContext()).showControlPanel = isChecked
        }

        // Magnetic pointer: snap to the nearest button when it stops.
        val switchSnap: SwitchMaterial = view.findViewById(R.id.switch_snap_to_button)
        switchSnap.isChecked = settings.snapToButton
        switchSnap.setOnCheckedChangeListener { _, isChecked ->
            CursorSettings(requireContext()).snapToButton = isChecked
        }

        // Shift + arrow: what the key does (default: fine step).
        val radioShiftArrow: RadioGroup = view.findViewById(R.id.radio_shift_arrow)
        when (settings.shiftArrowAction) {
            "scroll" -> radioShiftArrow.check(R.id.radio_shift_scroll)
            "move" -> radioShiftArrow.check(R.id.radio_shift_move)
            else -> radioShiftArrow.check(R.id.radio_shift_fine)
        }
        radioShiftArrow.setOnCheckedChangeListener { _, checkedId ->
            val action = when (checkedId) {
                R.id.radio_shift_scroll -> "scroll"
                R.id.radio_shift_move -> "move"
                else -> "fine"
            }
            CursorSettings(requireContext()).shiftArrowAction = action
        }

        // Ctrl + arrow: what the key does (default: scroll).
        val radioCtrlArrow: RadioGroup = view.findViewById(R.id.radio_ctrl_arrow)
        when (settings.ctrlArrowAction) {
            "fine" -> radioCtrlArrow.check(R.id.radio_ctrl_fine)
            "move" -> radioCtrlArrow.check(R.id.radio_ctrl_move)
            else -> radioCtrlArrow.check(R.id.radio_ctrl_scroll)
        }
        radioCtrlArrow.setOnCheckedChangeListener { _, checkedId ->
            val action = when (checkedId) {
                R.id.radio_ctrl_fine -> "fine"
                R.id.radio_ctrl_move -> "move"
                else -> "scroll"
            }
            CursorSettings(requireContext()).ctrlArrowAction = action
        }

        // Snap delay: how long the pointer must sit still (default 1 s).
        val radioSnapDelay: RadioGroup = view.findViewById(R.id.radio_snap_delay)
        when (settings.snapDelayMs) {
            500 -> radioSnapDelay.check(R.id.radio_snap_delay_500)
            1500 -> radioSnapDelay.check(R.id.radio_snap_delay_1500)
            2000 -> radioSnapDelay.check(R.id.radio_snap_delay_2000)
            else -> radioSnapDelay.check(R.id.radio_snap_delay_1000)
        }
        radioSnapDelay.setOnCheckedChangeListener { _, checkedId ->
            val delay = when (checkedId) {
                R.id.radio_snap_delay_500 -> 500
                R.id.radio_snap_delay_1500 -> 1500
                R.id.radio_snap_delay_2000 -> 2000
                else -> 1000
            }
            CursorSettings(requireContext()).snapDelayMs = delay
        }

        // Snap radius: 40..160 px, SeekBar max 120, progress = radius - 40.
        val seekSnapRadius: SeekBar = view.findViewById(R.id.seek_snap_radius)
        val textSnapRadiusValue: TextView = view.findViewById(R.id.text_snap_radius_value)
        seekSnapRadius.max = 120
        seekSnapRadius.progress = settings.snapRadiusPx - 40
        textSnapRadiusValue.text =
            "${settings.snapRadiusPx} ${getString(R.string.snap_radius_unit_px)}"
        seekSnapRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = progress + 40
                CursorSettings(requireContext()).snapRadiusPx = radius
                textSnapRadiusValue.text =
                    "$radius ${getString(R.string.snap_radius_unit_px)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No action needed.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // No action needed.
            }
        })

        // Smooth pointer glide (accelerates while an arrow key is held).
        val switchGlide: SwitchMaterial = view.findViewById(R.id.switch_glide)
        switchGlide.isChecked = settings.glideEnabled
        switchGlide.setOnCheckedChangeListener { _, isChecked ->
            CursorSettings(requireContext()).glideEnabled = isChecked
        }

        // Numpad as a second mouse.
        val switchNumpadMouse: SwitchMaterial = view.findViewById(R.id.switch_numpad_mouse)
        switchNumpadMouse.isChecked = settings.numpadMouse
        switchNumpadMouse.setOnCheckedChangeListener { _, isChecked ->
            CursorSettings(requireContext()).numpadMouse = isChecked
        }

        // Reset every setting to the defaults Muse set.
        val buttonResetDefaults: Button = view.findViewById(R.id.button_reset_defaults)
        buttonResetDefaults.setOnClickListener {
            CursorSettings(requireContext()).resetToDefaults()
            Toast.makeText(
                requireContext(),
                getString(R.string.reset_defaults_toast),
                Toast.LENGTH_SHORT,
            ).show()
            requireActivity().recreate()
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
