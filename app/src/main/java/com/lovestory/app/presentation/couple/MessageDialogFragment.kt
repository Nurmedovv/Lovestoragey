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
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.di.appContainer
import com.lovestory.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.lovestory.app.R
import com.lovestory.app.presentation.common.applyRoundedCorners

class MessageDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }

        val lastMessageLabel = TextView(context).apply {
            text = getString(R.string.message_last_sent)
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            setPadding(0, 0, 0, 8)
        }

        val isDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val hintColor = if (isDark) android.graphics.Color.LTGRAY else android.graphics.Color.GRAY

        val lastMessageText = TextView(context).apply {
            textSize = 16f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
            text = getString(R.string.message_placeholder)
        }

        // загружаем последнее сообщение ОТ ТЕКУЩЕГО ПОЛЬЗОВАТЕЛЯ
        val container = context.appContainer
        if (container.coupleSessionStore.isPaired()) {
            lifecycleScope.launch {
                val last = container.coupleRepository.getMyLastSentMessage() ?: return@launch
                lastMessageText.text = last
            }
        }

        val hintLabel = TextView(context).apply {
            text = getString(R.string.message_dialog_hint)
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            setPadding(0, 0, 0, 8)
        }

        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(SendMessageUseCase.MAX_MESSAGE_LENGTH))
            hint = getString(R.string.message_input_hint)
            textSize = 18f
            setTextColor(textColor)
            setHintTextColor(hintColor)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        val counter = TextView(context).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
            gravity = Gravity.END
            text = "0/${SendMessageUseCase.MAX_MESSAGE_LENGTH}"
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                counter.text = "${s?.length ?: 0}/${SendMessageUseCase.MAX_MESSAGE_LENGTH}"
            }
        })

        layout.addView(lastMessageLabel)
        layout.addView(lastMessageText)
        layout.addView(hintLabel)
        layout.addView(input)
        layout.addView(counter)

        return MaterialAlertDialogBuilder(context, R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.message_dialog_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.send)) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener { dialog.applyRoundedCorners() }
            }
    }

    private fun sendMessage(text: String) {
        requireActivity().lifecycleScope.launch {
            val success = requireContext().appContainer.sendMessageUseCase(text)
            if (!success) {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.message_send_error),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        const val TAG = "MessageDialogFragment"
    }
}
