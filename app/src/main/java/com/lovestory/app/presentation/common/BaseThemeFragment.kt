package com.lovestory.app.presentation.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.lovestory.app.presentation.main.MainActivity
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper

abstract class BaseThemeFragment<VB : ViewBinding> : Fragment(),
    ThemeChangeListener, GlassChangeListener, FontColorChangeListener {

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isDarkTheme = (activity as? MainActivity)?.getCurrentSystemTheme() ?: false
        applyTheme(isDarkTheme)
        (activity as? MainActivity)?.registerThemeListener(this)
        (activity as? MainActivity)?.registerGlassListener(this)
        (activity as? MainActivity)?.registerFontColorListener(this)
        onViewCreated(view, savedInstanceState, isDarkTheme)
    }

    abstract fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean)

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.unregisterThemeListener(this)
        (activity as? MainActivity)?.unregisterGlassListener(this)
        (activity as? MainActivity)?.unregisterFontColorListener(this)
        _binding = null
    }

    override fun onThemeChanged(isDarkTheme: Boolean) {
        applyTheme(isDarkTheme)
    }

    override fun onGlassChanged() {
        val b = _binding ?: return
        GlassEffectHelper.refreshRoot(b.root)
    }

    override fun onFontColorChanged() {
        val b = _binding ?: return
        FontColorHelper.refreshRoot(b.root)
    }

    abstract fun applyTheme(isDarkTheme: Boolean)
}
