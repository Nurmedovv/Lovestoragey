package com.lovestory.app.presentation.common

import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import com.lovestory.app.R

fun Dialog.applyRoundedCorners(radiusDp: Int = 20) {
    window?.let { window ->
        val radiusPx = (radiusDp * context.resources.displayMetrics.density).toInt()
        val colorRes = if ((context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            R.color.dialog_bg_dark
        } else {
            R.color.dialog_bg_light
        }
        val drawable = GradientDrawable().apply {
            @Suppress("DEPRECATION")
            setColor(context.getColor(colorRes))
            cornerRadius = radiusPx.toFloat()
        }
        window.setBackgroundDrawable(drawable)
    }
}
