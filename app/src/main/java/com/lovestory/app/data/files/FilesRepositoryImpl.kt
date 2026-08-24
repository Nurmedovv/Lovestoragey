package com.lovestory.app.data.files

import android.content.Context
import android.net.Uri
import com.lovestory.app.FileManager
import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.domain.model.FileLocation
import com.lovestory.app.domain.repository.FilesRepository

// делегирует существующему object FileManager — поведение 1:1
class FilesRepositoryImpl(private val context: Context) : FilesRepository {

    override fun initializeStorage() = FileManager.initializeStorage(context)

    override fun getStoragePath(location: FileLocation, fileName: String): String =
        FileManager.getStoragePath(context, location, fileName)

    override fun generateUniqueFileName(originalName: String): String =
        FileManager.generateUniqueFileName(originalName)

    override suspend fun saveFileFromUri(uri: Uri, location: FileLocation): AppFile? =
        FileManager.saveFileFromUri(context, uri, location)

    override fun deleteFile(filePath: String): Boolean = FileManager.deleteFile(filePath)

    override suspend fun deleteFileFromDb(filePath: String) =
        FileManager.deleteFileFromDb(context, filePath)

    override suspend fun getFilesForCalendarDate(day: Int, month: String): List<AppFile> =
        FileManager.getFilesForCalendarDate(context, day, month)

    override suspend fun getFilesForGallery(): List<AppFile> =
        FileManager.getFilesForGallery(context)

    override suspend fun getFilesForFilesPage(): List<AppFile> =
        FileManager.getFilesForFilesPage(context)

    override suspend fun getAllCalendarFiles(): List<AppFile> =
        FileManager.getAllCalendarFiles(context)

    override fun createCalendarDateId(day: Int, month: String): String =
        FileManager.createCalendarDateId(day, month)
}
