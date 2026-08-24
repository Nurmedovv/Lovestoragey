package com.lovestory.app.presentation.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar
import com.lovestory.app.domain.repository.AppPrefs

object DialogGlassHelper {

    private const val PREFS_NAME = AppPrefs.PREFS_NAME
    private const val KEY_DIALOG_OPACITY = "dialog_opacity"

    fun getOpacity(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DIALOG_OPACITY, 50)
    }

    fun saveOpacity(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DIALOG_OPACITY, percent).apply()
    }

    fun applyDialogBackground(root: android.view.View, isDarkTheme: Boolean) {
        val sliderValue = getOpacity(root.context)
        val alpha = if (sliderValue <= 50) {
            (255 - 25 * sliderValue / 50).toInt()
        } else {
            (230 * (100 - sliderValue) / 50).toInt()
        }.coerceIn(0, 255)

        val fillColor = if (isDarkTheme) {
            android.graphics.Color.argb(alpha, 0x2A, 0x2A, 0x2A)
        } else {
            android.graphics.Color.argb(alpha, 0xFF, 0xFF, 0xFF)
        }

        root.background = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = 48f
        }
    }

    fun applyDialogContentBorder(view: android.view.View, isDarkTheme: Boolean) {
        val strokeColor = if (isDarkTheme) {
            android.graphics.Color.parseColor("#50FFFFFF")
        } else {
            android.graphics.Color.parseColor("#66999999")
        }

        view.background = GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#30FFFFFF"))
            cornerRadius = 32f
            setStroke(1, strokeColor)
        }
    }

    fun setupSlider(seekBar: SeekBar, context: Context, onApply: (() -> Unit)? = null) {
        seekBar.max = 100
        seekBar.progress = getOpacity(context)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    saveOpacity(context, progress)
                    onApply?.invoke()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }
}
