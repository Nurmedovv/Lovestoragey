package com.lovestory.app.presentation.common

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.lovestory.app.domain.repository.AppPrefs

object FontColorHelper {

    private const val PREFS_NAME = AppPrefs.PREFS_NAME
    private const val KEY_FONT_COLOR = "font_color"

    val COLOR_PRESETS = intArrayOf(
        Color.WHITE,
        Color.BLACK,
        Color.parseColor("#F44336"),
        Color.parseColor("#FF9800"),
        Color.parseColor("#FFEB3B"),
        Color.parseColor("#4CAF50"),
        Color.parseColor("#2196F3"),
        Color.parseColor("#3F51B5"),
        Color.parseColor("#9C27B0"),
        Color.parseColor("#424242"),
        Color.parseColor("#CDDC39"),
        Color.parseColor("#795548")
    )

    fun getColor(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FONT_COLOR, Color.WHITE)
    }

    fun saveColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FONT_COLOR, color).apply()
    }

    fun applyToRoot(root: View) {
        val color = getColor(root.context)
        if (root is TextView && isStaticTextView(root)) {
            root.setTextColor(color)
        }
        if (root is EditText && isStaticTextView(root)) {
            root.setHintTextColor(color)
        }
        applyRecursive(root, color)
    }

    fun refreshRoot(root: View) {
        val color = getColor(root.context)
        if (root is TextView && isStaticTextView(root)) {
            root.setTextColor(color)
        }
        if (root is EditText && isStaticTextView(root)) {
            root.setHintTextColor(color)
        }
        applyRecursive(root, color)
    }

    private fun isStaticTextView(tv: TextView): Boolean {
        val current = tv.currentTextColor
        for (preset in COLOR_PRESETS) {
            if (current == preset) return true
        }
        return false
    }

    private fun applyRecursive(view: View, color: Int) {
        if (view !is ViewGroup) return
        val vg = view as ViewGroup
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)

            if (child is EditText && isStaticTextView(child)) {
                child.setTextColor(color)
                child.setHintTextColor(color)
            } else if (child is TextView && isStaticTextView(child)) {
                child.setTextColor(color)
            }

            if (child is ViewGroup && child !is androidx.recyclerview.widget.RecyclerView) {
                applyRecursive(child, color)
            }
        }
    }
}
