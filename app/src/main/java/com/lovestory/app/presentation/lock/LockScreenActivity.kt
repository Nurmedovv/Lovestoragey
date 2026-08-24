package com.lovestory.app.presentation.lock

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.lovestory.app.databinding.ActivityLockScreenBinding
import com.lovestory.app.R
import com.lovestory.app.di.appContainer
import com.lovestory.app.presentation.main.MainActivity

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lockRepository = appContainer.lockRepository
        val authEnabled = lockRepository.isAuthEnabled()

        if (!authEnabled) {
            navigateToMain()
            return
        }

        if (lockRepository.isLockedOut()) {
            startLockoutTimer()
            return
        }

        binding.unlockButton.setOnClickListener {
            val enteredPin = binding.pinInput.text.toString()

            if (lockRepository.verifyPin(enteredPin)) {
                lockRepository.resetFailedAttempts()
                lockRepository.setLastForegroundTime(0L)
                navigateToMain()
            } else {
                lockRepository.incrementFailedAttempts()
                val attempts = lockRepository.getFailedAttempts()

                if (attempts >= 3) {
                    val lockDuration = lockRepository.getLockoutDuration()
                    val lockUntil = System.currentTimeMillis() + lockDuration
                    lockRepository.setLockUntil(lockUntil)
                    startLockoutTimer()
                } else {
                    val remaining = 3 - attempts
                    Toast.makeText(this, getString(R.string.pin_incorrect_remaining, remaining), Toast.LENGTH_SHORT).show()
                    binding.pinInput.text.clear()
                }
            }
        }

        showBiometricIfAvailable()
    }

    private fun startLockoutTimer() {
        val lockUntil = appContainer.lockRepository.getLockUntil()
        val remainingMs = lockUntil - System.currentTimeMillis()

        if (remainingMs <= 0) {
            appContainer.lockRepository.resetFailedAttempts()
            binding.unlockButton.isEnabled = true
            binding.pinInput.isEnabled = true
            binding.lockMessage.text = getString(R.string.pin_enter_prompt)
            return
        }

        binding.unlockButton.isEnabled = false
        binding.pinInput.isEnabled = false
        binding.pinInput.text.clear()

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                binding.lockMessage.text = getString(R.string.lockout_countdown, seconds)
            }

            override fun onFinish() {
                appContainer.lockRepository.resetFailedAttempts()
                binding.unlockButton.isEnabled = true
                binding.pinInput.isEnabled = true
                binding.lockMessage.text = getString(R.string.pin_enter_prompt)
            }
        }.start()
    }

    private fun showBiometricIfAvailable() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS) {

            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        appContainer.lockRepository.resetFailedAttempts()
                        appContainer.lockRepository.setLastForegroundTime(0L)
                        navigateToMain()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        binding.lockMessage.text = getString(R.string.pin_enter_prompt)
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_app_title))
                .setSubtitle(getString(R.string.biometric_app_subtitle))
                .setNegativeButtonText(getString(R.string.enter_pin))
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun navigateToMain() {
        countDownTimer?.cancel()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
