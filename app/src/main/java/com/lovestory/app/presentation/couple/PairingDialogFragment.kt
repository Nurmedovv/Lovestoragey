package com.lovestory.app.presentation.couple

import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.di.appContainer
import com.lovestory.app.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import com.lovestory.app.R
import com.lovestory.app.presentation.common.applyRoundedCorners

// диалог привязки пары: создать новую или вступить в существующую по коду
class PairingDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val container = context.appContainer

        // проверяем авторизацию
        val authManager = container.authRepository
        if (!authManager.isSignedIn()) {
            Toast.makeText(context, getString(R.string.auth_required), Toast.LENGTH_SHORT).show()
            dismiss()
            return MaterialAlertDialogBuilder(context, R.style.LovestoryDialogTheme).create()
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }

        // кнопка "Создать пару"
        val btnCreate = TextView(context).apply {
            text = getString(R.string.pairing_create)
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
            setPadding(0, 16, 0, 16)
            gravity = Gravity.CENTER
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        // разделитель
        val divider = TextView(context).apply {
            text = getString(R.string.pairing_or)
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }

        // поле ввода кода
        val codeInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(6))
            hint = getString(R.string.pairing_code_hint)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
            visibility = android.view.View.GONE
        }

        // кнопка "Вступить"
        val btnJoin = TextView(context).apply {
            text = getString(R.string.pairing_join)
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
            setPadding(0, 8, 0, 16)
            gravity = Gravity.CENTER
            setBackgroundResource(android.R.drawable.list_selector_background)
            visibility = android.view.View.GONE
        }

        var isJoinMode = false

        btnCreate.setOnClickListener {
            if (isJoinMode) {
                isJoinMode = false
                btnCreate.text = getString(R.string.pairing_create)
                codeInput.visibility = android.view.View.GONE
                btnJoin.visibility = android.view.View.GONE
                divider.visibility = android.view.View.VISIBLE
            } else {
                val store = requireContext().appContainer.coupleSessionStore
                if (store.isPaired()) {
                    val code = store.getCoupleId()?.take(6)?.uppercase() ?: ""
                    MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                        .setTitle(getString(R.string.pairing_code_title))
                        .setMessage(getString(R.string.pairing_code_message, code))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show()
                        .applyRoundedCorners()
                    dismiss()
                } else {
                    createCouple(authManager)
                }
            }
        }

        divider.setOnClickListener {
            // переключаемся в режим вступления
            isJoinMode = true
            btnCreate.text = getString(R.string.pairing_back)
            codeInput.visibility = android.view.View.VISIBLE
            btnJoin.visibility = android.view.View.VISIBLE
            divider.visibility = android.view.View.GONE
        }

        btnJoin.setOnClickListener {
            val code = codeInput.text.toString().trim().uppercase()
            if (code.length != 6) {
                Toast.makeText(context, getString(R.string.pairing_code_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinCouple(authManager, code)
        }

        layout.addView(btnCreate)
        layout.addView(divider)
        layout.addView(codeInput)
        layout.addView(btnJoin)

        return MaterialAlertDialogBuilder(context, R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.pairing_title))
            .setView(layout)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    private fun createCouple(authManager: AuthRepository) {
        val userUid = authManager.getUserUid() ?: return
        val userName = authManager.getUserName() ?: getString(R.string.partner_default)
        requireActivity().lifecycleScope.launch {
            try {
                val container = requireContext().appContainer
                val coupleId = container.createCoupleUseCase(userUid, userName)
                container.coupleRepository.refreshFcmToken()
                if (!isAdded) return@launch
                val code = coupleId.take(6).uppercase()
                val message = getString(R.string.pairing_info_message, userName, getString(R.string.waiting_partner), code)
                MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                    .setTitle(getString(R.string.pairing_code_title))
                    .setMessage(message)
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
                    .applyRoundedCorners()
                dismiss()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Ошибка создания пары", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.pairing_not_found), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun joinCouple(authManager: AuthRepository, code: String) {
        val userUid = authManager.getUserUid() ?: return
        val userName = authManager.getUserName() ?: getString(R.string.partner_default)
        requireActivity().lifecycleScope.launch {
            try {
                val container = requireContext().appContainer
                val success = container.joinCoupleByCodeUseCase(code, userUid, userName)
                if (!isAdded) return@launch
                if (success) {
                    container.coupleRepository.refreshFcmToken()
                    val partnerName = container.coupleRepository.getOtherPartnerName() ?: getString(R.string.partner_default)
                    val message = getString(R.string.pairing_info_message, userName, partnerName, code)
                    MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                        .setTitle(getString(R.string.pairing_success))
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.ok), null)
                        .show()
                        .applyRoundedCorners()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.pairing_not_found), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Ошибка привязки", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.pairing_not_found), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val TAG = "PairingDialogFragment"
    }
}
