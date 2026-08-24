package com.lovestory.app.presentation.files

import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.domain.model.FileLocation
import com.lovestory.app.domain.model.FileType
import com.lovestory.app.domain.model.AppFile
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentFilesBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lovestory.app.R
import com.lovestory.app.di.appContainer
import com.lovestory.app.presentation.main.SharedViewModel
import com.lovestory.app.presentation.files.FilesPageDialogFragment
import com.lovestory.app.presentation.files.FilesViewModel
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.applyRoundedCorners

class FilesFragment : BaseThemeFragment<FragmentFilesBinding>() {

    companion object {
        private const val TAG = "FilesFragment"
    }

    private val viewModel: FilesViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                handleSelectedFiles(data)
            }
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentFilesBinding {
        return FragmentFilesBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        FontColorHelper.applyToRoot(binding.root)
        GlassEffectHelper.refreshRoot(binding.filesActionPanel)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.filesActionPanel.post { GlassEffectHelper.refreshRoot(binding.filesActionPanel) }
        setupFilesPage()
        setupObservers()
        viewModel.loadFiles()
        setupButtonListeners()

        sharedViewModel.filesChanged.observe(viewLifecycleOwner) {
            viewModel.loadFiles()
        }
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    override fun onGlassChanged() {
        GlassEffectHelper.refreshRoot(binding.root)
    }

    override fun onFontColorChanged() {
        FontColorHelper.applyToRoot(binding.root)
    }

    private fun setupFilesPage() {
        binding.filesTitle.text = "\uD83E\uDD7A"
        binding.filesSubtitle.text = getString(R.string.files_subtitle)
    }

    private fun setupObservers() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            updateLastFilePreview(files)
        }
    }

    private fun updateLastFilePreview(files: List<AppFile>) {
        val lastFile = files.firstOrNull()

        if (lastFile != null) {
            when (lastFile.fileType) {
                FileType.PHOTO -> loadImageInOriginalQuality(binding.lastFilePreview, lastFile.internalPath)
                FileType.VIDEO -> loadVideoPreviewInMainField(binding.lastFilePreview, lastFile.internalPath)
                else -> {
                    binding.lastFilePreview.setImageResource(android.R.drawable.ic_menu_edit)
                    binding.lastFilePreview.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
                }
            }
            binding.lastFileInfo.text = ""
            binding.lastFilePreview.background = null
        } else {
            Glide.with(binding.lastFilePreview).clear(binding.lastFilePreview)
            binding.lastFilePreview.setImageDrawable(null)
            binding.lastFileInfo.text = getString(R.string.no_files_message)
            binding.lastFilePreview.background = null
        }

        binding.lastFilePreview.setOnClickListener {
            if (files.isNotEmpty()) showFilesDialog()
        }
        binding.lastFileInfo.setOnClickListener {
            if (files.isNotEmpty()) showFilesDialog()
        }
    }

    private fun loadImageInOriginalQuality(imageView: ImageView, filePath: String) {
        val options = RequestOptions()
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)

        Glide.with(imageView.context)
            .asBitmap()
            .load(filePath)
            .apply(options)
            .into(imageView)
    }

    private fun setupButtonListeners() {
        binding.uploadFilesButton.setOnClickListener { openFilePicker() }
        binding.openFilesButton.setOnClickListener {
            if (viewModel.files.value?.isNotEmpty() == true) showFilesDialog()
        }
        binding.deleteFilesButton.setOnClickListener {
            if (viewModel.files.value?.isNotEmpty() == true) deleteAllFiles()
        }
    }

    private fun showFilesDialog() {
        val dialog = FilesPageDialogFragment.newInstance()
        dialog.show(parentFragmentManager, "files_page_dialog")
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.pick_files_title)))
        } catch (ex: android.content.ActivityNotFoundException) {
            Log.e(TAG, "No file picker app found", ex)
        }
    }

    private fun handleSelectedFiles(data: Intent) {
        try {
            val clipData = data.clipData
            lifecycleScope.launch {
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        saveFileToFilesPage(uri)
                    }
                } else {
                    val uri = data.data
                    if (uri != null) saveFileToFilesPage(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling selected files", e)
        }
    }

    private suspend fun saveFileToFilesPage(uri: Uri): Boolean {
        return try {
            val location = FileLocation(LocationType.FILES_PAGE)
            val savedFile = requireContext().appContainer.filesRepository.saveFileFromUri(uri, location)
            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    viewModel.addFile(savedFile)
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteAllFiles() {
        val files = viewModel.files.value ?: return
        if (files.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.delete_all_title))
            .setMessage(getString(R.string.delete_all_message))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            files.forEach {
                                requireContext().appContainer.deleteFileUseCase(it.internalPath)
                            }
                        }
                        viewModel.clearFiles()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting all files", e)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun loadVideoPreviewInMainField(imageView: ImageView, filePath: String) {
        val options = RequestOptions()
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)

        Glide.with(imageView.context)
            .asBitmap()
            .load(filePath)
            .apply(options)
            .into(imageView)
    }
}
