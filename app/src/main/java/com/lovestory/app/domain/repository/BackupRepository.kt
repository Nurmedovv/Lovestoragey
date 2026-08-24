package com.lovestory.app.domain.repository

import android.net.Uri
import com.lovestory.app.data.backup.ExportOptions
import java.io.File

// экспорт и импорт данных приложения в ZIP.
// Контекст реализация получает в конструкторе (application context)
interface BackupRepository {
    suspend fun exportData(options: ExportOptions): File?
    suspend fun importData(zipUri: Uri, replaceExisting: Boolean): Boolean
    fun cancelNotification()
    fun saveToDownloads(sourceFile: File): Uri?
}
