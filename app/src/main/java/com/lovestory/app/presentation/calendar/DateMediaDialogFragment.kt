package com.lovestory.app.presentation.calendar

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
import com.lovestory.app.presentation.common.ThemeChangeListener
import com.lovestory.app.presentation.common.applyRoundedCorners
import com.lovestory.app.presentation.common.isSystemDarkTheme

// диалог для просмотра и управления файлами конкретной даты
class DateMediaDialogFragment : DialogFragment(),
    ThemeChangeListener {

    private lateinit var dateFilesAdapter: FileAdapter
    private val dateFiles = mutableListOf<AppFile>()
    private var dateFilesStatusView: TextView? = null
    private var currentDay: Int = 0
    private var currentMonth: String = ""

    // регистрация системного файлового пикера
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

    companion object {
        private const val TAG = "DateMediaDialog"
        private const val ARG_DAY = "day"
        private const val ARG_MONTH = "month"

        // создаёт экземпляр диалога с переданными днём и месяцем
        fun newInstance(day: Int, month: String): DateMediaDialogFragment {
            val args = Bundle().apply {
                putInt(ARG_DAY, day)
                putString(ARG_MONTH, month)
            }
            return DateMediaDialogFragment().apply {
                arguments = args
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        currentDay = arguments?.getInt(ARG_DAY) ?: 1
        currentMonth = arguments?.getString(ARG_MONTH) ?: ""
        val isDarkTheme = requireContext().isSystemDarkTheme()

        val dialogView = layoutInflater.inflate(R.layout.dialog_date_simple, null)

        val textColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        // вычисляет размеры экрана для адаптации диалога
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

        // устанавливает максимальную высоту диалога
        val rootLayout = dialogView.findViewById<LinearLayout>(R.id.dialogRootLayout)
        DialogGlassHelper.applyDialogBackground(rootLayout, isDarkTheme)
        dialogView.findViewById<View>(R.id.dateDialogContent)?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }
        dialogView.findViewById<View>(R.id.dateDialogNavBar)?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }
        rootLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            maxDialogHeight
        )

        // настраивает ScrollView внутри диалога
        val scrollView = dialogView.findViewById<ScrollView>(R.id.contentScrollView)
        val scrollViewParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        )
        scrollViewParams.weight = 1f
        scrollView.layoutParams = scrollViewParams

        // инициализирует элементы UI
        val titleText = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val dateFilesStatus = dialogView.findViewById<TextView>(R.id.dateFilesStatus)
        dateFilesStatusView = dateFilesStatus
        val uploadToDateButton = dialogView.findViewById<TextView>(R.id.uploadToDateButton)
        val deleteDateFilesButton = dialogView.findViewById<TextView>(R.id.deleteDateFilesButton)
        val closeButton = dialogView.findViewById<TextView>(R.id.closeButton)
        val dateFilesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.dateFilesRecyclerView)

        // устанавливает заголовок с днём и месяцем
        titleText.text = getString(R.string.date_dialog_title_format, currentDay, currentMonth)
        titleText.setTextColor(textColor)
        dateFilesStatus.setTextColor(textColor)

        // настраивает RecyclerView для файлов даты
        setupDateFilesRecyclerView(dateFilesRecyclerView, maxDialogHeight)
        loadDateFiles()
        updateDateFilesStatus(dateFilesStatus)

        // кнопка загрузки файлов
        uploadToDateButton.setOnClickListener {
            openFilePickerForDate()
        }

        // кнопка удаления всех файлов даты
        deleteDateFilesButton.setOnClickListener {
            if (dateFiles.isNotEmpty()) {
                deleteAllDateFiles()
            }
        }

        // кнопка закрытия диалога
        closeButton.setOnClickListener {
            dismiss()
        }

        // создаёт диалог с прозрачным фоном и затемнением
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

    // настраивает RecyclerView с фиксированной высотой
    private fun setupDateFilesRecyclerView(recyclerView: RecyclerView, maxDialogHeight: Int) {
        dateFilesAdapter = FileAdapter(emptyList(),
            onFileClick = { file ->
                showFilePreview(file)
            },
            onFileLongClick = { file ->
                deleteDateFile(file)
            }
        )

        recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = dateFilesAdapter

            fun Int.dpToPx(): Int {
                return (this * requireContext().resources.displayMetrics.density).toInt()
            }

            val headerHeight = 80.dpToPx()
            val buttonsHeight = 120.dpToPx()
            val padding = 40.dpToPx()

            val maxRecyclerHeight = maxDialogHeight - headerHeight - buttonsHeight - padding

            // устанавливает фиксированную высоту
            layoutParams.height = maxRecyclerHeight

            setHasFixedSize(true)
            setItemViewCacheSize(30)

            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        }
    }

    // загружает файлы для текущей даты
    private fun loadDateFiles() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                requireContext().appContainer.filesRepository.getFilesForCalendarDate(currentDay, currentMonth)
            }
            dateFiles.clear()
            dateFiles.addAll(files)
            dateFilesAdapter.attachFiles(dateFiles)
            updateDateFilesStatus()
        }
    }

    // обновляет статус (количество файлов)
    private fun updateDateFilesStatus(statusView: TextView? = dateFilesStatusView) {
        statusView?.text = getString(R.string.date_files_count, dateFiles.size)
    }

    // открывает системный файловый пикер
    private fun openFilePickerForDate() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.pick_files_for_date)))
        } catch (ex: android.content.ActivityNotFoundException) {
            // без уведомления
        }
    }

    // обрабатывает выбранные файлы
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
                        saveFileToDate(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling selected files", e)
        }
    }

    // сохраняет файл в папку даты
    private suspend fun saveFileToDate(uri: Uri): Boolean {
        return try {
            val dateId = requireContext().appContainer.filesRepository.createCalendarDateId(currentDay, currentMonth)
            val location = FileLocation(LocationType.CALENDAR_DATE, dateId)

            val savedFile = requireContext().appContainer.filesRepository.saveFileFromUri(uri, location)
            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    dateFiles.add(0, savedFile)
                    dateFilesAdapter.notifyItemInserted(0)
                    updateDateFilesStatus()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // открывает файл для просмотра
    private fun showFilePreview(file: AppFile) {
        FileOpener.openFile(requireContext(), file)
    }

    // удаляет один файл
    private fun deleteDateFile(file: AppFile) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                requireContext().appContainer.deleteFileUseCase(file.internalPath)
            }
            if (success) {
                val position = dateFiles.indexOf(file)
                if (position != -1) {
                    dateFiles.removeAt(position)
                    dateFilesAdapter.notifyItemRemoved(position)
                    updateDateFilesStatus()
                    parentFragmentManager.setFragmentResult("files_changed", Bundle.EMPTY)
                }
            }
        }
    }

    // удаляет все файлы даты
    private fun deleteAllDateFiles() {
        if (dateFiles.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.delete_all_title))
            .setMessage(getString(R.string.delete_all_date_message))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                lifecycleScope.launch {
                    val filesToDelete = dateFiles.toList()
                    withContext(Dispatchers.IO) {
                        filesToDelete.forEach {
                            requireContext().appContainer.deleteFileUseCase(it.internalPath)
                        }
                    }
                    dateFiles.clear()
                    dateFilesAdapter.notifyDataSetChanged()
                    updateDateFilesStatus()
                    parentFragmentManager.setFragmentResult("files_changed", Bundle.EMPTY)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

}