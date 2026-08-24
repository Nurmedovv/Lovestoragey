package com.lovestory.app.presentation.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.lovestory.app.R
import com.lovestory.app.domain.repository.AppPrefs

object GlassEffectHelper {

    private const val PREFS_NAME = AppPrefs.PREFS_NAME
    private const val KEY_GLASS_OPACITY = "glass_opacity"

    fun getOpacity(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_GLASS_OPACITY, 50)
    }

    fun saveOpacity(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_GLASS_OPACITY, percent).apply()
    }

    fun applyToRoot(root: View) {
        val opacity = getOpacity(root.context)
        val rootType = getGlassType(root.background)
        if (rootType != null) {
            root.background = createGlassDrawable(opacity, rootType)
        }
        applyRecursive(root, opacity)
    }

    fun refreshRoot(root: View) {
        val opacity = getOpacity(root.context)
        val rootType = getGlassType(root.background)
        if (rootType != null) {
            root.background = createGlassDrawable(opacity, rootType)
        }
        applyRecursive(root, opacity)
    }

    private fun getGlassType(bg: android.graphics.drawable.Drawable?): String? {
        if (bg is RippleDrawable) return null
        return when (bg) {
            is StateListDrawable -> "selector"
            is LayerDrawable -> "layer"
            is GradientDrawable -> "simple"
            else -> null
        }
    }

    private fun applyRecursive(view: View, opacityPercent: Int) {
        if (view !is ViewGroup) return
        val vg = view as ViewGroup
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)

            if (child is android.widget.SeekBar || child is android.widget.Switch
                || child.id == R.id.navIndicator) {
                continue
            }

            val type = getGlassType(child.background)
            if (type != null) {
                child.background = createGlassDrawable(opacityPercent, type)
            }

            if (child is ViewGroup && child !is androidx.recyclerview.widget.RecyclerView) {
                applyRecursive(child, opacityPercent)
            }
        }
    }

    fun createGlassDrawable(opacityPercent: Int, type: String = "simple"): android.graphics.drawable.Drawable {
        val fillAlpha = if (opacityPercent <= 50) {
            (230 - 167 * opacityPercent / 50).toInt()
        } else {
            (63 * (100 - opacityPercent) / 50).toInt()
        }.coerceIn(0, 255)
        val strokeAlpha = (fillAlpha * 2).coerceIn(0, 255)
        val fill = android.graphics.Color.argb(fillAlpha, 255, 255, 255)
        val stroke = android.graphics.Color.argb(strokeAlpha, 255, 255, 255)

        val normal = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 48f
            setStroke(3, stroke)
        }

        return when (type) {
            "selector" -> {
                val pressedFill = android.graphics.Color.argb((fillAlpha + 30).coerceIn(0, 255), 255, 255, 255)
                val pressed = GradientDrawable().apply {
                    setColor(pressedFill)
                    cornerRadius = 48f
                    setStroke(3, stroke)
                }
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), pressed)
                    addState(intArrayOf(), normal)
                }
            }
            "layer" -> {
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        android.graphics.Color.argb((fillAlpha * 0.8f).toInt().coerceIn(0, 255), 255, 255, 255),
                        android.graphics.Color.argb((fillAlpha * 0.2f).toInt().coerceIn(0, 255), 255, 255, 255)
                    )
                ).apply { cornerRadius = 72f }
            }
            else -> normal
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
