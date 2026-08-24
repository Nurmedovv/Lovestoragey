package com.lovestory.app.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentSettingsRelationshipsBinding
import com.lovestory.app.di.appContainer
import com.lovestory.app.R
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.applyRoundedCorners
import com.lovestory.app.presentation.couple.PairingDialogFragment
import com.lovestory.app.AuthManager
import com.lovestory.app.domain.repository.AuthRepository
import com.lovestory.app.domain.repository.CoupleSessionStore
import kotlinx.coroutines.launch

// Категория настроек «Отношения»: Google-аккаунт, никнейм, пара.
// Логика перенесена из прежнего монолитного SettingsFragment без изменений поведения.
class SettingsRelationshipsFragment : BaseThemeFragment<FragmentSettingsRelationshipsBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsRelationshipsBinding {
        return FragmentSettingsRelationshipsBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        updatePairingStatus()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        setupGoogleSignIn()
        setupNickname()
        setupPairing()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    @Suppress("DEPRECATION")
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { token ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val authManager = AuthManager(requireContext())
                    val success = authManager.firebaseAuthWithGoogle(token)
                    if (success) {
                        updateSignInStatus()
                        Toast.makeText(requireContext(), getString(R.string.auth_signed_in, authManager.getUserName()), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.auth_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), getString(R.string.signin_error, e.statusCode), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGoogleSignIn() {
        val authManager = AuthManager(requireContext())
        updateSignInStatus()

        binding.llGoogleSignIn.setOnClickListener {
            if (authManager.isSignedIn()) {
                MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                    .setTitle(getString(R.string.sign_out))
                    .setMessage(authManager.getUserName() ?: "")
                    .setPositiveButton(getString(R.string.sign_out)) { _, _ ->
                        authManager.signOut()
                        updateSignInStatus()
                        updatePairingStatus()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
                    .applyRoundedCorners()
            } else {
                val signInIntent = authManager.googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun updateSignInStatus() {
        val authManager = AuthManager(requireContext())
        if (authManager.isSignedIn()) {
            binding.tvSignInStatus.text = authManager.getUserEmail() ?: ""
        } else {
            binding.tvSignInStatus.text = ""
        }
    }

    private fun setupNickname() {
        val authManager = requireContext().appContainer.authRepository
        binding.tvNicknameStatus.text = authManager.getUserName() ?: ""
        binding.llNickname.setOnClickListener {
            if (!authManager.isSignedIn()) {
                Toast.makeText(requireContext(), getString(R.string.auth_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val input = EditText(requireContext()).apply {
                setText(authManager.getUserName() ?: "")
                setSelection(text.length)
                setPadding(64, 32, 64, 16)
            }
            MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                .setTitle(getString(R.string.nickname_title))
                .setView(input)
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        authManager.saveUserName(newName)
                        binding.tvNicknameStatus.text = newName
                        updateNicknameInFirestore(newName)
                        updateSignInStatus()
                        updatePairingStatus()
                        Toast.makeText(requireContext(), getString(R.string.nickname_updated), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
                .applyRoundedCorners()
        }
    }

    private fun updateNicknameInFirestore(newName: String) {
        requireContext().appContainer.coupleRepository.updateMyName(newName)
    }

    private fun setupPairing() {
        updatePairingStatus()

        binding.llPairing.setOnClickListener {
            val container = requireContext().appContainer
            val authManager = container.authRepository
            if (!authManager.isSignedIn()) {
                Toast.makeText(requireContext(), getString(R.string.auth_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (container.coupleSessionStore.isPaired()) {
                showPairedInfo(container.coupleSessionStore, authManager)
            } else {
                val dialog = PairingDialogFragment()
                dialog.show(childFragmentManager, PairingDialogFragment.TAG)
            }
        }
    }

    private fun showPairedInfo(store: CoupleSessionStore, authManager: AuthRepository) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val code = store.getCoupleId()?.take(6)?.uppercase() ?: ""
                val myName = authManager.getUserName() ?: getString(R.string.me_default)
                val partnerName = requireContext().appContainer.coupleRepository.getOtherPartnerName() ?: getString(R.string.partner_default)
                if (!isAdded) return@launch
                MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                    .setTitle(getString(R.string.pairing_paired))
                    .setMessage(getString(R.string.pairing_info_message, myName, partnerName, code))
                    .setPositiveButton(getString(R.string.pairing_regenerate)) { _, _ ->
                        confirmRegenerate(authManager)
                    }
                    .setNegativeButton(getString(R.string.pairing_unpair)) { _, _ ->
                        confirmUnpair()
                    }
                    .setNeutralButton(getString(R.string.cancel), null)
                    .show()
                    .applyRoundedCorners()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Ошибка загрузки информации о паре", e)
            }
        }
    }

    private fun confirmUnpair() {
        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.pairing_unpair_title))
            .setMessage(getString(R.string.pairing_unpair_message))
            .setPositiveButton(getString(R.string.pairing_unpair)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        requireContext().appContainer.unpairUseCase()
                        if (isAdded) {
                            updatePairingStatus()
                            Toast.makeText(requireContext(), getString(R.string.pairing_unpaired), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Ошибка отвязки", e)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun confirmRegenerate(authManager: AuthRepository) {
        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.pairing_regenerate_title))
            .setMessage(getString(R.string.pairing_regenerate_message))
            .setPositiveButton(getString(R.string.pairing_regenerate)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val container = requireContext().appContainer
                        container.unpairUseCase()
                        val userUid = authManager.getUserUid() ?: return@launch
                        val userName = authManager.getUserName() ?: getString(R.string.partner_default)
                        val newCoupleId = container.createCoupleUseCase(userUid, userName)
                        if (!isAdded) return@launch
                        val code = newCoupleId.take(6).uppercase()
                        Toast.makeText(requireContext(), getString(R.string.pairing_code_message, code), Toast.LENGTH_LONG).show()
                        updatePairingStatus()
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Ошибка регенерации пары", e)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun updatePairingStatus() {
        val store = requireContext().appContainer.coupleSessionStore
        if (store.isPaired()) {
            val code = store.getCoupleId()?.take(6)?.uppercase() ?: ""
            binding.tvPairingStatus.text = getString(R.string.pairing_paired) + " ($code)"
        } else {
            binding.tvPairingStatus.text = getString(R.string.pairing_not_paired)
        }
    }

    private companion object {
        const val TAG = "SettingsRelationships"
    }
}
