package com.lovestory.app

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lovestory.app.db.AppDatabase
import com.lovestory.app.db.FileEntity
import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.domain.model.FileLocation
import com.lovestory.app.domain.model.FileType
import com.lovestory.app.domain.model.LocationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

object FileManager {

    private const val TAG = "FileManager"
    private const val FILES_DIRECTORY = "app_files"
    private const val CALENDAR_DIRECTORY = "calendar_dates"
    private const val GALLERY_DIRECTORY = "gallery"
    private const val FILES_PAGE_DIRECTORY = "files_page"

    private fun getFileDao(context: Context) = AppDatabase.getDatabase(context).fileDao()

    fun initializeStorage(context: Context) {
        val directories = listOf(
            "${context.filesDir.path}/$FILES_DIRECTORY",
            "${context.filesDir.path}/$FILES_DIRECTORY/$CALENDAR_DIRECTORY",
            "${context.filesDir.path}/$FILES_DIRECTORY/$GALLERY_DIRECTORY",
            "${context.filesDir.path}/$FILES_DIRECTORY/$FILES_PAGE_DIRECTORY"
        )

        directories.forEach { dirPath ->
            val directory = File(dirPath)
            if (!directory.exists()) {
                directory.mkdirs()
            }
        }
    }

    fun getStoragePath(context: Context, location: FileLocation, fileName: String): String {
        val baseDir = "${context.filesDir.path}/$FILES_DIRECTORY"
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")

        return when (location.type) {
            LocationType.CALENDAR_DATE -> {
                val safeTargetId = location.targetId.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                val dateDir = "$baseDir/$CALENDAR_DIRECTORY/$safeTargetId"
                File(dateDir).mkdirs()
                "$dateDir/$safeFileName"
            }
            LocationType.GALLERY_PAGE -> "$baseDir/$GALLERY_DIRECTORY/$safeFileName"
            LocationType.FILES_PAGE -> "$baseDir/$FILES_PAGE_DIRECTORY/$safeFileName"
        }
    }

    fun getFileTypeFromExtension(fileName: String): FileType {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> FileType.PHOTO
            "mp4", "avi", "mov", "mkv", "webm", "3gp" -> FileType.VIDEO
            "mp3", "wav", "ogg", "m4a", "aac" -> FileType.AUDIO
            "pdf", "doc", "docx", "txt", "xls", "xlsx" -> FileType.DOCUMENT
            else -> FileType.OTHER
        }
    }

    fun generateUniqueFileName(originalName: String): String {
        val sanitized = originalName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val extension = sanitized.substringAfterLast('.', "")
        val nameWithoutExtension = sanitized.substringBeforeLast('.', sanitized)
        val timestamp = System.currentTimeMillis()

        return "${nameWithoutExtension}_${timestamp}.${extension}"
    }

    suspend fun saveFileFromUri(context: Context, uri: Uri, location: FileLocation): AppFile? {
        return try {
            val originalName = getFileNameFromUri(context, uri) ?: "file_${System.currentTimeMillis()}"
            val uniqueFileName = generateUniqueFileName(originalName)

            val destinationPath = getStoragePath(context, location, uniqueFileName)

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return null
            }

            inputStream.use { stream ->
                File(destinationPath).outputStream().use { outputStream ->
                    stream.copyTo(outputStream)
                }
            }

            val file = File(destinationPath)
            val fileExists = file.exists()
            val fileSize = file.length()
            val canRead = file.canRead()

            if (fileExists && fileSize > 0 && canRead) {
                val appFile = AppFile(
                    id = 0,
                    originalName = originalName,
                    internalPath = destinationPath,
                    fileType = getFileTypeFromExtension(originalName),
                    uploadDate = Date(),
                    fileSize = fileSize,
                    fileLocation = location
                )

                withContext(Dispatchers.IO) {
                    getFileDao(context).insert(appFile.toEntity())
                }

                appFile
            } else {
                if (fileExists) {
                    file.delete()
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file from URI", e)
            null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            var result: String? = null
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            result = cursor.getString(displayNameIndex)
                        }
                    }
                }
            }
            if (result == null) {
                result = uri.path?.substringAfterLast('/')
            }
            result
        } catch (e: Exception) {
            uri.path?.substringAfterLast('/')
        }
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val fileExistsBefore = file.exists()

            if (!fileExistsBefore) {
                return true
            }

            val success = file.delete()
            val fileExistsAfter = file.exists()

            success && !fileExistsAfter
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
            false
        }
    }

    suspend fun deleteFileFromDb(context: Context, filePath: String) {
        getFileDao(context).deleteByPath(filePath)
    }

    suspend fun getFilesByLocation(context: Context, location: FileLocation): List<AppFile> {
        val dao = getFileDao(context)
        val entities = when (location.type) {
            LocationType.CALENDAR_DATE -> dao.getByLocation(location.type.name, location.targetId)
            LocationType.GALLERY_PAGE -> dao.getByLocationType(location.type.name)
            LocationType.FILES_PAGE -> dao.getByLocationType(location.type.name)
        }
        return entities.map { it.toAppFile() }
    }

    suspend fun getFilesForCalendarDate(context: Context, day: Int, month: String): List<AppFile> {
        val dateId = "${day}_${month.replace(" ", "_")}"
        val location = FileLocation(LocationType.CALENDAR_DATE, dateId)
        return getFilesByLocation(context, location)
    }

    suspend fun getFilesForGallery(context: Context): List<AppFile> {
        val location = FileLocation(LocationType.GALLERY_PAGE)
        return getFilesByLocation(context, location)
    }

    suspend fun getFilesForFilesPage(context: Context): List<AppFile> {
        val location = FileLocation(LocationType.FILES_PAGE)
        return getFilesByLocation(context, location)
    }

    suspend fun getAllCalendarFiles(context: Context): List<AppFile> {
        return getFileDao(context).getByLocationType(LocationType.CALENDAR_DATE.name).map { it.toAppFile() }
    }

    fun createCalendarDateId(day: Int, month: String): String {
        return "${day}_${month.replace(" ", "_")}"
    }

    private fun AppFile.toEntity() = FileEntity(
        id = id,
        originalName = originalName,
        internalPath = internalPath,
        fileType = fileType.name,
        uploadDate = uploadDate.time,
        fileSize = fileSize,
        locationType = fileLocation.type.name,
        targetId = fileLocation.targetId
    )

    private fun FileEntity.toAppFile() = AppFile(
        id = id,
        originalName = originalName,
        internalPath = internalPath,
        fileType = FileType.valueOf(fileType),
        uploadDate = Date(uploadDate),
        fileSize = fileSize,
        fileLocation = FileLocation(LocationType.valueOf(locationType), targetId)
    )
}
