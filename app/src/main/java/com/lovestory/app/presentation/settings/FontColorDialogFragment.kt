package com.lovestory.app.presentation.settings

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.lovestory.app.R
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.presentation.common.DialogGlassHelper
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.isSystemDarkTheme

class FontColorDialogFragment : DialogFragment() {

    private var onColorSelected: ((Int) -> Unit)? = null

    companion object {
        const val TAG = "FontColorDialog"
        private const val CUSTOM_COLORS_COUNT = 3
        private const val PREFS_NAME = AppPrefs.PREFS_NAME
        private const val KEY_CUSTOM_COLOR = "custom_color_"

        fun newInstance(onColorSelected: (Int) -> Unit): FontColorDialogFragment {
            return FontColorDialogFragment().apply {
                this.onColorSelected = onColorSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val isDarkTheme = requireContext().isSystemDarkTheme()
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_font_color, null)

        val rootLayout = dialogView.findViewById<LinearLayout>(R.id.dialogRootLayout)
        DialogGlassHelper.applyDialogBackground(rootLayout, isDarkTheme)

        val contentArea = dialogView.findViewById<LinearLayout>(R.id.colorDialogContent)
        contentArea?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }

        val grid = dialogView.findViewById<GridLayout>(R.id.colorPresetGrid)
        val closeButton = dialogView.findViewById<TextView>(R.id.closeButton)
        val titleLabel = dialogView.findViewById<TextView>(R.id.dialogTitle)

        val fontColor = FontColorHelper.getColor(requireContext())
        titleLabel.setTextColor(fontColor)

        setupPresetGrid(grid)

        closeButton.setOnClickListener { dismiss() }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        @Suppress("DEPRECATION")
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.dimAmount = 0.7f

        return dialog
    }

    private fun setupPresetGrid(grid: GridLayout) {
        val size = 40
        val margin = 4
        val currentColor = FontColorHelper.getColor(requireContext())
        grid.columnCount = 12

        for (color in FontColorHelper.COLOR_PRESETS) {
            val circle = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    setStroke(2, Color.BLACK)
                    if (color == currentColor) {
                        setStroke(4, Color.parseColor("#FF4081"))
                    }
                }
                isClickable = true
                isFocusable = true
                tag = color
                setOnClickListener {
                    FontColorHelper.saveColor(requireContext(), color)
                    onColorSelected?.invoke(color)
                    dismiss()
                }
            }
            grid.addView(circle)
        }
    }
}
