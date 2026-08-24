package com.lovestory.app.presentation.common

import android.content.Context
import android.content.res.Configuration

fun Context.isSystemDarkTheme(): Boolean {
    return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
