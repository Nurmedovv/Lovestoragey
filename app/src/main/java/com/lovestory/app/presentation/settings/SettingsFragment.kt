package com.lovestory.app.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lovestory.app.databinding.FragmentSettingsBinding
import com.lovestory.app.db.AppDatabase
import com.lovestory.app.di.appContainer
import com.lovestory.app.domain.model.FileType
import com.lovestory.app.R
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Хост экрана настроек: главный список категорий + статистика.
// Категории открываются вложенными фрагментами в settingsContainer (childFragmentManager),
// возврат — системный Back или стрелка в шапке подкатегории.
class SettingsFragment : BaseThemeFragment<FragmentSettingsBinding>() {

    private var backCallback: OnBackPressedCallback? = null
    private var backStackListener: (() -> Unit)? = null
    private var hideContainerRunnable: Runnable? = null

    private companion object {
        const val ANIM_DURATION_MS = 220L
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        loadStatistics()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.settingsTitle.text = getString(R.string.settings_title)

        setupCategories()
        setupNavigation()

        // восстановление состояния после пересоздания: если вложенный экран
        // был открыт (стек сохранился) — сразу показываем контейнер
        syncVisibility()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    override fun onDestroyView() {
        hideContainerRunnable?.let { binding.root.removeCallbacks(it) }
        hideContainerRunnable = null
        backStackListener?.let { childFragmentManager.removeOnBackStackChangedListener(it) }
        backStackListener = null
        backCallback = null
        super.onDestroyView()
    }

    private fun setupCategories() {
        binding.llCategoryDates.setOnClickListener { openCategory(SettingsDatesFragment()) }
        binding.llCategoryAppearance.setOnClickListener { openCategory(SettingsAppearanceFragment()) }
        binding.llCategorySecurity.setOnClickListener { openCategory(SettingsSecurityFragment()) }
        binding.llCategoryRelationships.setOnClickListener { openCategory(SettingsRelationshipsFragment()) }
        binding.llCategoryData.setOnClickListener { openCategory(SettingsDataFragment()) }
    }

    private fun openCategory(fragment: Fragment) {
        syncVisibility(hasSubScreen = true)
        childFragmentManager.beginTransaction()
            // въезд справа при открытии, возврат слева при Back
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.settingsContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupNavigation() {
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (childFragmentManager.backStackEntryCount > 0) {
                    childFragmentManager.popBackStack()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        backCallback = callback

        // слушатель синхронизирует видимость и состояние Back при каждом изменении стека
        val listener: () -> Unit = {
            syncVisibility()
        }
        childFragmentManager.addOnBackStackChangedListener(listener)
        backStackListener = listener
    }

    private fun syncVisibility() {
        val hasSubScreen = childFragmentManager.backStackEntryCount > 0 ||
                childFragmentManager.findFragmentById(R.id.settingsContainer) != null
        syncVisibility(hasSubScreen)
    }

    private fun syncVisibility(hasSubScreen: Boolean) {
        // отменяем отложенное скрытие контейнера (быстрая навигация вперёд-назад)
        hideContainerRunnable?.let { binding.root.removeCallbacks(it) }
        hideContainerRunnable = null

        backCallback?.isEnabled = hasSubScreen
        if (hasSubScreen) {
            binding.settingsMainMenu.visibility = View.GONE
            binding.settingsContainer.visibility = View.VISIBLE
        } else {
            // список показываем сразу, а контейнер прячем только после анимации
            // выхода — иначе мгновенный GONE обрезает анимацию возврата
            binding.settingsMainMenu.visibility = View.VISIBLE
            val hide = Runnable {
                hideContainerRunnable = null
                if (isAdded && childFragmentManager.backStackEntryCount == 0) {
                    binding.settingsContainer.visibility = View.GONE
                }
            }
            hideContainerRunnable = hide
            binding.root.postDelayed(hide, ANIM_DURATION_MS)
        }
    }

    private fun loadStatistics() {
        val context = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            val filesRepository = context.appContainer.filesRepository
            val galleryFiles = withContext(Dispatchers.IO) { filesRepository.getFilesForGallery() }
            val allFiles = withContext(Dispatchers.IO) { filesRepository.getFilesForFilesPage() }
            val calendarFiles = withContext(Dispatchers.IO) { filesRepository.getAllCalendarFiles() }
            val noteCount = withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).noteDao().count() }

            val photoCount = galleryFiles.count { it.fileType == FileType.PHOTO }
            var totalSize = 0L
            galleryFiles.forEach { totalSize += it.fileSize }
            allFiles.forEach { totalSize += it.fileSize }
            calendarFiles.forEach { totalSize += it.fileSize }
            val sizeMB = totalSize / (1024.0 * 1024.0)

            binding.tvStatsPhotos.text = getString(R.string.stats_photos, photoCount)
            binding.tvStatsFiles.text = getString(R.string.stats_files, allFiles.size)
            binding.tvStatsCalendarFiles.text = getString(R.string.stats_calendar_files, calendarFiles.size)
            binding.tvStatsStorage.text = getString(R.string.stats_storage, sizeMB)
            binding.tvStatsNotes.text = getString(R.string.stats_notes, noteCount)
        }
    }
}
