package com.lovestory.app.presentation.common

import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.domain.model.FileLocation
import com.lovestory.app.domain.model.AppFile
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lovestory.app.R
import com.lovestory.app.di.appContainer
import com.lovestory.app.presentation.main.MainActivity
import com.lovestory.app.presentation.common.FileAdapter
import com.lovestory.app.presentation.common.FileOpener
import com.lovestory.app.presentation.common.DialogGlassHelper
import com.lovestory.app.presentation.common.applyRoundedCorners
import com.lovestory.app.presentation.common.isSystemDarkTheme

abstract class BaseFilePageDialogFragment : DialogFragment(),
    ThemeChangeListener {

    protected abstract val tag_: String
    protected abstract val title: String
    protected abstract val statusPrefix: String
    protected abstract val fileFilter: String
    protected abstract val filePickerTitle: String
    protected abstract val locationType: LocationType
    protected abstract val layoutRes: Int
    protected abstract val recyclerViewId: Int
    protected abstract val statusViewId: Int

    protected lateinit var fileAdapter: FileAdapter
    protected val filesList = mutableListOf<AppFile>()
    private var statusView: TextView? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) handleSelectedFiles(data)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val isDarkTheme = requireContext().isSystemDarkTheme()
        val dialogView = layoutInflater.inflate(layoutRes, null)

        val textColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        val screenHeight = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requireActivity().windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.heightPixels
        }
        val maxDialogHeight = (screenHeight * 0.80).toInt()

        val rootLayout = dialogView.findViewById<LinearLayout>(R.id.dialogRootLayout)
        DialogGlassHelper.applyDialogBackground(rootLayout, isDarkTheme)
        dialogView.findViewById<View>(R.id.fileDialogContent)?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }
        dialogView.findViewById<View>(R.id.galleryDialogContent)?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }
        dialogView.findViewById<View>(R.id.fileDialogNavBar)?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }
        dialogView.findViewById<View>(R.id.galleryDialogNavBar)?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }
        rootLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            maxDialogHeight
        )

        val scrollView = dialogView.findViewById<ScrollView>(R.id.contentScrollView)
        val scrollViewParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        )
        scrollViewParams.weight = 1f
        scrollView.layoutParams = scrollViewParams

        val titleText = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val status = dialogView.findViewById<TextView>(statusViewId)
        statusView = status
        val uploadButton = dialogView.findViewById<TextView>(R.id.uploadButton)
        val deleteButton = dialogView.findViewById<TextView>(R.id.deleteButton)
        val closeButton = dialogView.findViewById<TextView>(R.id.closeButton)
        val recyclerView = dialogView.findViewById<RecyclerView>(recyclerViewId)

        titleText.text = title
        titleText.setTextColor(textColor)
        status.setTextColor(textColor)

        setupRecyclerView(recyclerView, maxDialogHeight)
        loadFiles()
        updateStatus(statusView)

        uploadButton.setOnClickListener { openFilePicker() }
        deleteButton.setOnClickListener { if (filesList.isNotEmpty()) deleteAllFiles() }
        closeButton.setOnClickListener { dismiss() }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        @Suppress("DEPRECATION")
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.dimAmount = 0.7f

        return dialog
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.registerThemeListener(this)
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.unregisterThemeListener(this)
    }

    override fun onThemeChanged(isDarkTheme: Boolean) {
        dismiss()
    }

    private fun setupRecyclerView(recyclerView: RecyclerView, maxDialogHeight: Int) {
        fileAdapter = FileAdapter(filesList,
            onFileClick = { file -> FileOpener.openFile(requireContext(), file) },
            onFileLongClick = { file -> deleteFile(file) }
        )

        recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = fileAdapter

            fun Int.dpToPx(): Int {
                return (this * requireContext().resources.displayMetrics.density).toInt()
            }

            val headerHeight = 80.dpToPx()
            val buttonsHeight = 120.dpToPx()
            val padding = 40.dpToPx()
            val maxRecyclerHeight = maxDialogHeight - headerHeight - buttonsHeight - padding

            layoutParams.height = maxRecyclerHeight
            setHasFixedSize(false)
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        }
    }

    private fun loadFiles() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { loadFilesFromStorage() }
            filesList.clear()
            filesList.addAll(result)
            fileAdapter.attachFiles(filesList)
            updateStatus()
        }
    }

    protected abstract suspend fun loadFilesFromStorage(): List<AppFile>

    private fun updateStatus(view: TextView? = statusView) {
        view?.text = "$statusPrefix ${filesList.size}"
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = fileFilter
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, filePickerTitle))
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(requireContext(), getString(R.string.no_file_picker), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSelectedFiles(data: Intent) {
        try {
            val uris = mutableListOf<Uri>()
            val clipData = data.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else {
                data.data?.let { uris.add(it) }
            }
            lifecycleScope.launch {
                for (uri in uris) {
                    withContext(Dispatchers.IO) {
                        saveFile(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag_, "Error handling selected files", e)
        }
    }

    private suspend fun saveFile(uri: Uri): Boolean {
        return try {
            val location = FileLocation(locationType)
            val savedFile = requireContext().appContainer.filesRepository.saveFileFromUri(uri, location)
            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    filesList.add(0, savedFile)
                    fileAdapter.notifyItemInserted(0)
                    updateStatus()
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteFile(file: AppFile) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                requireContext().appContainer.deleteFileUseCase(file.internalPath)
            }
            if (success) {
                val position = filesList.indexOf(file)
                if (position != -1) {
                    filesList.removeAt(position)
                    fileAdapter.notifyItemRemoved(position)
                    updateStatus()
                    (activity as? MainActivity)?.sharedViewModel?.triggerFilesChanged()
                    // сигнал галерее: она слушает Fragment Result API, а не LiveData
                    parentFragmentManager.setFragmentResult("files_changed", Bundle.EMPTY)
                }
            }
        }
    }

    private fun deleteAllFiles() {
        if (filesList.isEmpty()) return

        val filesToDelete = filesList.toList()

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.delete_all_title))
            .setMessage(getString(R.string.delete_all_message))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                requireActivity().lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        filesToDelete.forEach {
                            requireContext().appContainer.deleteFileUseCase(it.internalPath)
                        }
                    }
                    filesList.clear()
                    fileAdapter.notifyDataSetChanged()
                    updateStatus()
                    (activity as? MainActivity)?.sharedViewModel?.triggerFilesChanged()
                    // сигнал галерее: она слушает Fragment Result API, а не LiveData
                    parentFragmentManager.setFragmentResult("files_changed", Bundle.EMPTY)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }
}
