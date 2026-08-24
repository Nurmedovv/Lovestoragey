package com.lovestory.app.domain.repository

import android.net.Uri
import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.domain.model.FileLocation

// хранилище файлов приложения: локальные файлы + метаданные в Room.
// Контекст реализация получает в конструкторе (application context) —
// все операции app-scoped: filesDir и ContentResolver одинаковы для любого контекста
interface FilesRepository {
    fun initializeStorage()

    fun getStoragePath(location: FileLocation, fileName: String): String
    fun generateUniqueFileName(originalName: String): String

    suspend fun saveFileFromUri(uri: Uri, location: FileLocation): AppFile?
    fun deleteFile(filePath: String): Boolean
    suspend fun deleteFileFromDb(filePath: String)

    suspend fun getFilesForCalendarDate(day: Int, month: String): List<AppFile>
    suspend fun getFilesForGallery(): List<AppFile>
    suspend fun getFilesForFilesPage(): List<AppFile>
    suspend fun getAllCalendarFiles(): List<AppFile>

    fun createCalendarDateId(day: Int, month: String): String
}
