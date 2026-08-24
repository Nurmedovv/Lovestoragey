package com.lovestory.app.presentation.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentSettingsSecurityBinding
import com.lovestory.app.di.appContainer
import com.lovestory.app.R
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.applyRoundedCorners

// Категория настроек «Безопасность»: биометрия/PIN и таймаут блокировки.
// Логика перенесена из прежнего монолитного SettingsFragment без изменений поведения.
class SettingsSecurityFragment : BaseThemeFragment<FragmentSettingsSecurityBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsSecurityBinding {
        return FragmentSettingsSecurityBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        setupAuthSwitch()
        setupLockTimeout()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    private fun setupAuthSwitch() {
        binding.authSwitch.isChecked = requireContext().appContainer.lockRepository.isAuthEnabled()
        updateLockTimeoutVisibility(binding.authSwitch.isChecked)

        binding.authSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showBiometricPrompt { success ->
                    if (success) {
                        requireContext().appContainer.lockRepository.setAuthEnabled(true)
                        updateLockTimeoutVisibility(true)
                        val existingPin = requireContext().appContainer.lockRepository.getPinCode()
                        if (existingPin == null) {
                            showPinSetupDialog()
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.auth_enabled_toast), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        binding.authSwitch.isChecked = false
                    }
                }
            } else {
                requireContext().appContainer.lockRepository.setAuthEnabled(false)
                requireContext().appContainer.lockRepository.removePinCode()
                updateLockTimeoutVisibility(false)
            }
        }
    }

    private fun setupLockTimeout() {
        val currentTimeout = requireContext().appContainer.lockRepository.getLockTimeout()
        binding.tvLockTimeout.text = getTimeoutLabel(currentTimeout)

        binding.llLockTimeout.setOnClickListener {
            showTimeoutPicker()
        }
    }

    private fun updateLockTimeoutVisibility(enabled: Boolean) {
        binding.llLockTimeout.alpha = if (enabled) 1.0f else 0.4f
        binding.llLockTimeout.isClickable = enabled
    }

    private fun showTimeoutPicker() {
        val currentTimeout = requireContext().appContainer.lockRepository.getLockTimeout()
        val timeoutOptions = getTimeoutOptions(requireContext())
        val currentIndex = timeoutOptions.indexOfFirst { it.second == currentTimeout }.coerceAtLeast(0)
        val labels = timeoutOptions.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.lock_timeout_dialog_title))
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedTimeout = timeoutOptions[which].second
                requireContext().appContainer.lockRepository.setLockTimeout(selectedTimeout)
                binding.tvLockTimeout.text = getTimeoutLabel(selectedTimeout)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun getTimeoutLabel(timeoutMs: Long): String {
        return getTimeoutOptions(requireContext()).firstOrNull { it.second == timeoutMs }?.first ?: getString(R.string.timeout_1_min)
    }

    private fun showBiometricPrompt(onResult: (Boolean) -> Unit) {
        val activity = requireActivity() as FragmentActivity
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_setup_title))
            .setSubtitle(getString(R.string.biometric_setup_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_negative_button))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showPinSetupDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pin_input, null)
        val pinInput = dialogView.findViewById<EditText>(R.id.pinInput)

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.pin_title))
            .setMessage(getString(R.string.pin_setup_message))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val pin = pinInput.text.toString()
                if (pin.length == 4 && pin.all { it.isDigit() }) {
                    requireContext().appContainer.lockRepository.savePinCode(pin)
                    Toast.makeText(requireContext(), getString(R.string.pin_saved_toast), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.pin_validation_error), Toast.LENGTH_SHORT).show()
                    binding.authSwitch.isChecked = false
                    requireContext().appContainer.lockRepository.setAuthEnabled(false)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                binding.authSwitch.isChecked = false
                requireContext().appContainer.lockRepository.setAuthEnabled(false)
            }
            .setCancelable(false)
            .show()
            .applyRoundedCorners()
    }

    companion object {
        fun getTimeoutOptions(context: Context) = listOf(
            context.getString(R.string.timeout_immediately) to 0L,
            context.getString(R.string.timeout_30_sec) to 30_000L,
            context.getString(R.string.timeout_1_min) to 60_000L,
            context.getString(R.string.timeout_5_min) to 300_000L,
            context.getString(R.string.timeout_15_min) to 900_000L,
            context.getString(R.string.timeout_30_min) to 1_800_000L,
            context.getString(R.string.timeout_1_hour) to 3_600_000L,
            context.getString(R.string.timeout_never) to Long.MAX_VALUE
        )
    }
}
