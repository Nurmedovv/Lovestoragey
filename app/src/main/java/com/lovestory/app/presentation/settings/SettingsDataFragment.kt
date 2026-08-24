package com.lovestory.app.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentSettingsDataBinding
import com.lovestory.app.di.appContainer
import com.lovestory.app.R
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.applyRoundedCorners
import com.lovestory.app.data.backup.ExportOptions
import kotlinx.coroutines.launch

// Категория настроек «Данные»: экспорт и импорт (ZIP-бэкап).
// Логика перенесена из прежнего монолитного SettingsFragment без изменений поведения.
class SettingsDataFragment : BaseThemeFragment<FragmentSettingsDataBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsDataBinding {
        return FragmentSettingsDataBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        setupExportImport()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    // --- Экспорт / Импорт ---

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromUri(it) }
    }

    private fun setupExportImport() {
        binding.llExport.setOnClickListener { performExport() }
        binding.llImport.setOnClickListener { performImport() }
    }

    private fun performExport() {
        val items = arrayOf(
            getString(R.string.export_option_notes),
            getString(R.string.export_option_files),
            getString(R.string.export_option_background),
            getString(R.string.export_option_settings)
        )
        val checkedItems = booleanArrayOf(true, true, true, true)

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.export_options_title))
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val options = ExportOptions(
                    includeNotes = checkedItems[0],
                    includeFiles = checkedItems[1],
                    includeBackground = checkedItems[2],
                    includeSettings = checkedItems[3]
                )
                startExport(options)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun startExport(options: ExportOptions) {
        binding.tvExportStatus.text = getString(R.string.export_started)

        viewLifecycleOwner.lifecycleScope.launch {
            val exportFile = requireContext().appContainer.backupRepository.exportData(options)

            if (exportFile != null) {
                val savedUri = requireContext().appContainer.backupRepository.saveToDownloads(exportFile)
                binding.tvExportStatus.text = getString(R.string.export_success)

                val shareUri = savedUri ?: FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    exportFile
                )

                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.export_share)))
                requireContext().appContainer.backupRepository.cancelNotification()
            } else {
                binding.tvExportStatus.text = getString(R.string.export_error)
                Toast.makeText(requireContext(), getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performImport() {
        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    private fun importFromUri(uri: android.net.Uri) {
        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.import_confirm_title))
            .setMessage(getString(R.string.import_confirm_message))
            .setPositiveButton(getString(R.string.import_confirm_replace)) { _, _ ->
                startImport(uri, replaceExisting = true)
            }
            .setNegativeButton(getString(R.string.import_confirm_merge)) { _, _ ->
                startImport(uri, replaceExisting = false)
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun startImport(uri: android.net.Uri, replaceExisting: Boolean) {
        binding.tvImportStatus.text = getString(R.string.import_started)

        viewLifecycleOwner.lifecycleScope.launch {
            val success = requireContext().appContainer.backupRepository.importData(uri, replaceExisting)

            if (success) {
                binding.tvImportStatus.text = getString(R.string.import_success)
                requireContext().appContainer.backupRepository.cancelNotification()
                Toast.makeText(requireContext(), getString(R.string.import_restart), Toast.LENGTH_LONG).show()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 1500)
            } else {
                binding.tvImportStatus.text = getString(R.string.import_error)
                Toast.makeText(requireContext(), getString(R.string.import_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
