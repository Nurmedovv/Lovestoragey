package com.lovestory.app.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentSettingsAppearanceBinding
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.R
import com.lovestory.app.presentation.main.MainActivity
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.DialogGlassHelper
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.applyRoundedCorners

// Категория настроек «Оформление»: вибрация, язык, фон, цвет шрифта, прозрачность.
// Логика перенесена из прежнего монолитного SettingsFragment без изменений поведения.
class SettingsAppearanceFragment : BaseThemeFragment<FragmentSettingsAppearanceBinding>() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsAppearanceBinding {
        return FragmentSettingsAppearanceBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        updateBackgroundStatus()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        sharedPreferences = requireContext().getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        setupVibrationSwitch()
        setupLanguage()
        setupGlassOpacity()
        setupDialogOpacity()
        setupFontColor()
        setupBackground()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    private fun setupVibrationSwitch() {
        val isVibrationEnabled = sharedPreferences.getBoolean(AppPrefs.KEY_VIBRATION_ENABLED, true)

        binding.vibrationSwitch.setOnCheckedChangeListener(null)
        binding.vibrationSwitch.isChecked = isVibrationEnabled
        binding.vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting(AppPrefs.KEY_VIBRATION_ENABLED, isChecked)
            if (isChecked) {
                vibrate(50)
            }
        }
    }

    private fun setupLanguage() {
        val currentLang = sharedPreferences.getString(AppPrefs.KEY_APP_LANGUAGE, "system") ?: "system"
        binding.tvLanguage.text = getLanguageLabel(currentLang)

        binding.llLanguage.setOnClickListener {
            showLanguagePicker()
        }
    }

    private fun getLanguageLabel(lang: String): String {
        return when (lang) {
            "ru" -> getString(R.string.language_russian)
            "en" -> getString(R.string.language_english)
            "es" -> getString(R.string.language_spanish)
            "it" -> getString(R.string.language_italian)
            "fr" -> getString(R.string.language_french)
            "de" -> getString(R.string.language_german)
            "pt" -> getString(R.string.language_portuguese)
            "pl" -> getString(R.string.language_polish)
            "tr" -> getString(R.string.language_turkish)
            "ar" -> getString(R.string.language_arabic)
            else -> getString(R.string.language_system)
        }
    }

    private fun showLanguagePicker() {
        val ctx = requireContext()
        val languages = listOf(
            ctx.getString(R.string.language_system),
            ctx.getString(R.string.language_russian),
            ctx.getString(R.string.language_english),
            ctx.getString(R.string.language_spanish),
            ctx.getString(R.string.language_italian),
            ctx.getString(R.string.language_french),
            ctx.getString(R.string.language_german),
            ctx.getString(R.string.language_portuguese),
            ctx.getString(R.string.language_polish),
            ctx.getString(R.string.language_turkish),
            ctx.getString(R.string.language_arabic)
        )
        val languageCodes = listOf("system", "ru", "en", "es", "it", "fr", "de", "pt", "pl", "tr", "ar")
        val currentLang = sharedPreferences.getString(AppPrefs.KEY_APP_LANGUAGE, "system") ?: "system"
        val currentIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx, R.style.LovestoryDialogTheme)
            .setTitle(ctx.getString(R.string.language_title))
            .setSingleChoiceItems(languages.toTypedArray(), currentIndex) { dialog, which ->
                val selectedLang = languageCodes[which]
                sharedPreferences.edit().putString(AppPrefs.KEY_APP_LANGUAGE, selectedLang).apply()
                binding.tvLanguage.text = languages[which]
                applyLanguage(selectedLang)
                dialog.dismiss()
            }
            .setNegativeButton(ctx.getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun applyLanguage(lang: String) {
        sharedPreferences.edit().putString(AppPrefs.KEY_APP_LANGUAGE, lang).apply()
        requireActivity().recreate()
    }

    private fun setupGlassOpacity() {
        val opacity = GlassEffectHelper.getOpacity(requireContext())
        binding.tvGlassOpacity.text = "$opacity%"
        GlassEffectHelper.applyToRoot(binding.root)

        GlassEffectHelper.setupSlider(binding.seekGlassOpacity, requireContext()) {
            val progress = binding.seekGlassOpacity.progress
            binding.tvGlassOpacity.text = "$progress%"
            (activity as? MainActivity)?.notifyGlassChanged()
        }
    }

    private fun setupDialogOpacity() {
        val opacity = DialogGlassHelper.getOpacity(requireContext())
        binding.tvDialogOpacity.text = "$opacity%"

        DialogGlassHelper.setupSlider(binding.seekDialogOpacity, requireContext()) {
            val progress = binding.seekDialogOpacity.progress
            binding.tvDialogOpacity.text = "$progress%"
        }
    }

    private fun setupFontColor() {
        val currentColor = FontColorHelper.getColor(requireContext())
        updateFontColorPreview(currentColor)

        binding.llFontColor.setOnClickListener {
            val dialog = FontColorDialogFragment.newInstance { color ->
                updateFontColorPreview(color)
                (activity as? MainActivity)?.notifyFontColorChanged()
            }
            dialog.show(childFragmentManager, FontColorDialogFragment.TAG)
        }
    }

    private fun updateFontColorPreview(color: Int) {
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setStroke(2, android.graphics.Color.BLACK)
        }
        binding.tvFontColorPreview.background = drawable
    }

    private fun setupBackground() {
        updateBackgroundStatus()

        binding.llBackground.setOnClickListener {
            val hasCustom = sharedPreferences.getString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, null) != null
            val items = if (hasCustom) {
                arrayOf(
                    getString(R.string.settings_background_set),
                    getString(R.string.settings_background_clear)
                )
            } else {
                arrayOf(getString(R.string.settings_background_set))
            }

            MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                .setTitle(getString(R.string.settings_background))
                .setItems(items) { _, which ->
                    if (which == 0) {
                        (activity as? MainActivity)?.pickBackgroundImage()
                    } else {
                        (activity as? MainActivity)?.clearCustomBackground()
                        updateBackgroundStatus()
                    }
                }
                .show()
                .applyRoundedCorners()
        }
    }

    private fun updateBackgroundStatus() {
        val hasCustom = sharedPreferences.getString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, null) != null
        binding.tvBackgroundStatus.text = if (hasCustom) {
            getString(R.string.settings_background_set)
        } else {
            getString(R.string.settings_background_default)
        }
    }

    private fun saveSetting(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private fun vibrate(duration: Long) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val vibrationEffect = android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            Log.w("SettingsAppearance", "Vibration failed", e)
        }
    }
}
