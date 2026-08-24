package com.lovestory.app.presentation.common

import com.lovestory.app.domain.model.FileType
import com.lovestory.app.domain.model.AppFile
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import com.lovestory.app.R
import com.lovestory.app.presentation.gallery.ImageFullscreenDialog

// утилита для открытия файлов через системные приложения
object FileOpener {

    // открывает файл в зависимости от его типа
    fun openFile(context: Context, appFile: AppFile) {
        // для изображений показываем полноэкранный просмотр
        if (appFile.fileType == FileType.PHOTO) {
            showFullscreenImage(context, appFile.internalPath)
        } else {
            openWithSystemApp(context, appFile)
        }
    }

    // показывает изображение в полноэкранном диалоге
    private fun showFullscreenImage(context: Context, imagePath: String) {
        try {
            val dialog = ImageFullscreenDialog.newInstance(imagePath)
            if (context is androidx.fragment.app.FragmentActivity) {
                dialog.show(context.supportFragmentManager, "fullscreen_image")
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error_open_image), Toast.LENGTH_SHORT).show()
        }
    }

    // открывает файл через системное приложение
    private fun openWithSystemApp(context: Context, appFile: AppFile) {
        try {
            val file = File(appFile.internalPath)
            if (!file.exists()) {
                Toast.makeText(context, context.getString(R.string.error_file_not_found), Toast.LENGTH_SHORT).show()
                return
            }

            // получает URI через FileProvider для безопасного доступа
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // определяет MIME-тип
            val mimeType = when (appFile.fileType) {
                FileType.VIDEO -> "video/*"
                FileType.AUDIO -> "audio/*"
                FileType.DOCUMENT -> getDocumentMimeType(appFile.originalName)
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.error_install_file_app), Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
        }
    }

    // определяет MIME-тип для документов
    private fun getDocumentMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }
}