package com.lovestory.app.presentation.common

// контракты подписки на изменения внешнего вида;
// раньше жили внутри MainActivity, теперь в общем пакете presentation/common
interface ThemeChangeListener {
    fun onThemeChanged(isDarkTheme: Boolean)
}

interface GlassChangeListener {
    fun onGlassChanged()
}

interface FontColorChangeListener {
    fun onFontColorChanged()
}
