package com.lovestory.app.data.backup

import com.lovestory.app.R
import com.lovestory.app.FileManager
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.domain.repository.BackupRepository
import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.domain.model.FileLocation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lovestory.app.db.AppDatabase
import com.lovestory.app.db.FileEntity
import com.lovestory.app.db.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.lovestory.app.presentation.common.DialogGlassHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper

data class ExportOptions(
    val includeNotes: Boolean = true,
    val includeFiles: Boolean = true,
    val includeBackground: Boolean = true,
    val includeSettings: Boolean = true
)

// контекст приходит из AppContainer (application context):
// cacheDir/filesDir/prefs/ContentResolver/NotificationManager одинаковы для любого контекста
class ExportManager(private val context: Context) : BackupRepository {

    companion object {
        private const val TAG = "ExportManager"
        private const val CHANNEL_ID = "export_channel"
        private const val NOTIFICATION_ID = 3001
        private const val MANIFEST_VERSION = 1
    }

    override suspend fun exportData(options: ExportOptions): File? = withContext(Dispatchers.IO) {
        try {
            showNotification(context, context.getString(R.string.export_notification_preparing), 0)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "lovestory_export_$timestamp.zip"
            val exportFile = File(context.cacheDir, fileName)

            val fileDao = AppDatabase.getDatabase(context).fileDao()
            val noteDao = AppDatabase.getDatabase(context).noteDao()

            val allFiles = if (options.includeFiles) fileDao.getAll() else emptyList()
            val allNotes = if (options.includeNotes) noteDao.getAll() else emptyList()
            val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

            val totalItems = allFiles.size + allNotes.size + 1
            var progress = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(exportFile))).use { zos ->
                // manifest.json
                writeManifest(zos, allFiles.size, allNotes.size, options)
                progress++
                showNotification(context, context.getString(R.string.export_notification_manifest), (progress * 100 / totalItems))

                // notes.json
                if (options.includeNotes) {
                    writeNotes(zos, allNotes)
                    progress++
                    showNotification(context, "${context.getString(R.string.export_notification_notes)} ($progress/$totalItems)", (progress * 100 / totalItems))
                }

                // files_metadata.json + файлы
                if (options.includeFiles) {
                    writeFileMetadata(zos, allFiles)

                    for ((index, fileEntity) in allFiles.withIndex()) {
                        val sourceFile = File(fileEntity.internalPath)
                        if (sourceFile.exists()) {
                            val entryPath = getExportPath(fileEntity)
                            zos.putNextEntry(ZipEntry("files/$entryPath"))
                            FileInputStream(sourceFile).use { fis ->
                                fis.buffered().use { bis ->
                                    bis.copyTo(zos)
                                }
                            }
                            zos.closeEntry()
                        }
                        if (index % 10 == 0) {
                            val fileProgress = progress + index + 1
                            showNotification(context, "${context.getString(R.string.export_notification_files)} (${index + 1}/${allFiles.size})", (fileProgress * 100 / totalItems))
                        }
                    }
                    progress += allFiles.size
                }

                // кастомный фон
                if (options.includeBackground) {
                    val backgroundUri = prefs.getString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, null)
                    if (backgroundUri != null) {
                        writeBackground(zos, context, Uri.parse(backgroundUri))
                    }
                }

                // settings.json
                if (options.includeSettings) {
                    writeSettings(zos, prefs, context)
                }
            }

            showNotification(context, context.getString(R.string.export_notification_complete), 100)
            Log.d(TAG, "Экспорт завершён: ${exportFile.absolutePath}, размер: ${exportFile.length()}")
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта", e)
            showNotification(context, "${context.getString(R.string.export_notification_error)}: ${e.message}", 0)
            null
        }
    }

    override suspend fun importData(zipUri: Uri, replaceExisting: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            showNotification(context, context.getString(R.string.import_notification_preparing), 0)

            val fileDao = AppDatabase.getDatabase(context).fileDao()
            val noteDao = AppDatabase.getDatabase(context).noteDao()

            if (replaceExisting) {
                val existingFiles = fileDao.getAll()
                for (entity in existingFiles) {
                    val file = File(entity.internalPath)
                    if (file.exists()) file.delete()
                }
                fileDao.deleteAll()
                noteDao.deleteAll()
            }

            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry = zis.nextEntry
                    var manifest: JSONObject? = null
                    var notesJson: JSONArray? = null
                    var filesMetadataJson: JSONArray? = null
                    val tempFiles = mutableListOf<Pair<String, File>>()

                    while (entry != null) {
                        when {
                            entry.name == "manifest.json" -> {
                                manifest = JSONObject(String(zis.readBytes()))
                            }
                            entry.name == "notes.json" -> {
                                notesJson = JSONArray(String(zis.readBytes()))
                            }
                            entry.name == "files_metadata.json" -> {
                                filesMetadataJson = JSONArray(String(zis.readBytes()))
                            }
                            entry.name == "settings.json" -> {
                                val settingsJson = JSONObject(String(zis.readBytes()))
                                restoreSettings(context, settingsJson)
                            }
                            entry.name == "background.jpg" || entry.name == "background.png" -> {
                                val tempFile = File(context.cacheDir, "import_${entry.name}")
                                FileOutputStream(tempFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                tempFiles.add(Pair(entry.name, tempFile))
                            }
                            entry.name.startsWith("files/") -> {
                                val relativePath = entry.name.removePrefix("files/")
                                val tempFile = File(context.cacheDir, "import_${relativePath.replace("/", "_")}")
                                FileOutputStream(tempFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                tempFiles.add(Pair(relativePath, tempFile))
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }

                    // восстанавливаем фон
                    val bgEntry = tempFiles.find { it.first.startsWith("background.") }
                    if (bgEntry != null) {
                        restoreBackground(context, bgEntry.second)
                    }

                    // восстанавливаем файлы и метаданные
                    if (filesMetadataJson != null) {
                        restoreFiles(context, fileDao, filesMetadataJson, tempFiles)
                    }

                    // восстанавливаем заметки
                    if (notesJson != null) {
                        restoreNotes(noteDao, notesJson)
                    }

                    // чистим temp
                    tempFiles.forEach { it.second.delete() }
                }
            }

            showNotification(context, context.getString(R.string.import_notification_complete), 100)
            Log.d(TAG, "Импорт завершён")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка импорта", e)
            showNotification(context, "${context.getString(R.string.import_notification_error)}: ${e.message}", 0)
            false
        }
    }

    private fun writeManifest(zos: ZipOutputStream, fileCount: Int, noteCount: Int, options: ExportOptions) {
        val manifest = JSONObject().apply {
            put("version", MANIFEST_VERSION)
            put("exportDate", System.currentTimeMillis())
            put("appVersion", "1.0")
            put("fileCount", fileCount)
            put("noteCount", noteCount)
            put("includeNotes", options.includeNotes)
            put("includeFiles", options.includeFiles)
            put("includeBackground", options.includeBackground)
            put("includeSettings", options.includeSettings)
        }
        zos.putNextEntry(ZipEntry("manifest.json"))
        zos.write(manifest.toString(2).toByteArray())
        zos.closeEntry()
    }

    private fun writeNotes(zos: ZipOutputStream, notes: List<NoteEntity>) {
        val arr = JSONArray()
        for (note in notes) {
            arr.put(JSONObject().apply {
                put("content", note.content)
                put("timestamp", note.timestamp)
                put("isPinned", note.isPinned)
            })
        }
        zos.putNextEntry(ZipEntry("notes.json"))
        zos.write(arr.toString(2).toByteArray())
        zos.closeEntry()
    }

    private fun writeFileMetadata(zos: ZipOutputStream, files: List<FileEntity>) {
        val arr = JSONArray()
        for (file in files) {
            arr.put(JSONObject().apply {
                put("originalName", file.originalName)
                put("fileType", file.fileType)
                put("uploadDate", file.uploadDate)
                put("fileSize", file.fileSize)
                put("locationType", file.locationType)
                put("targetId", file.targetId)
                put("internalPath", file.internalPath)
            })
        }
        zos.putNextEntry(ZipEntry("files_metadata.json"))
        zos.write(arr.toString(2).toByteArray())
        zos.closeEntry()
    }

    private fun getExportPath(entity: FileEntity): String {
        return when (entity.locationType) {
            "GALLERY_PAGE" -> "gallery/${File(entity.internalPath).name}"
            "FILES_PAGE" -> "files_page/${File(entity.internalPath).name}"
            "CALENDAR_DATE" -> "calendar/${entity.targetId}/${File(entity.internalPath).name}"
            else -> "other/${File(entity.internalPath).name}"
        }
    }

    private fun writeBackground(zos: ZipOutputStream, context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val extension = getBackgroundExtension(context, uri)
            zos.putNextEntry(ZipEntry("background.$extension"))
            inputStream.use { bis ->
                bis.buffered().use { buf ->
                    buf.copyTo(zos)
                }
            }
            zos.closeEntry()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи фона", e)
        }
    }

    private fun getBackgroundExtension(context: Context, uri: Uri): String {
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType == "image/png") return "png"
        } catch (_: Exception) {}
        return "jpg"
    }

    private fun writeSettings(zos: ZipOutputStream, prefs: android.content.SharedPreferences, context: Context) {
        val settings = JSONObject().apply {
            put(AppPrefs.KEY_START_YEAR, prefs.getInt(AppPrefs.KEY_START_YEAR, 0))
            put(AppPrefs.KEY_START_MONTH, prefs.getInt(AppPrefs.KEY_START_MONTH, 0))
            put(AppPrefs.KEY_START_DAY, prefs.getInt(AppPrefs.KEY_START_DAY, 1))
            put(AppPrefs.KEY_END_YEAR, prefs.getInt(AppPrefs.KEY_END_YEAR, 0))
            put(AppPrefs.KEY_END_MONTH, prefs.getInt(AppPrefs.KEY_END_MONTH, 11))
            put(AppPrefs.KEY_END_DAY, prefs.getInt(AppPrefs.KEY_END_DAY, 31))
            put("glass_opacity", GlassEffectHelper.getOpacity(context))
            put("dialog_opacity", DialogGlassHelper.getOpacity(context))
            put("font_color", FontColorHelper.getColor(context))
            put(AppPrefs.KEY_APP_LANGUAGE, prefs.getString(AppPrefs.KEY_APP_LANGUAGE, "system"))
            put(AppPrefs.KEY_VIBRATION_ENABLED, prefs.getBoolean(AppPrefs.KEY_VIBRATION_ENABLED, true))
        }
        zos.putNextEntry(ZipEntry("settings.json"))
        zos.write(settings.toString(2).toByteArray())
        zos.closeEntry()
    }

    private fun restoreSettings(context: Context, json: JSONObject) {
        val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(AppPrefs.KEY_START_YEAR, json.optInt(AppPrefs.KEY_START_YEAR, 0))
            putInt(AppPrefs.KEY_START_MONTH, json.optInt(AppPrefs.KEY_START_MONTH, 0))
            putInt(AppPrefs.KEY_START_DAY, json.optInt(AppPrefs.KEY_START_DAY, 1))
            putInt(AppPrefs.KEY_END_YEAR, json.optInt(AppPrefs.KEY_END_YEAR, 0))
            putInt(AppPrefs.KEY_END_MONTH, json.optInt(AppPrefs.KEY_END_MONTH, 11))
            putInt(AppPrefs.KEY_END_DAY, json.optInt(AppPrefs.KEY_END_DAY, 31))
            putString(AppPrefs.KEY_APP_LANGUAGE, json.optString(AppPrefs.KEY_APP_LANGUAGE, "system"))
            putBoolean(AppPrefs.KEY_VIBRATION_ENABLED, json.optBoolean(AppPrefs.KEY_VIBRATION_ENABLED, true))
            apply()
        }

        if (json.has("glass_opacity")) {
            GlassEffectHelper.saveOpacity(context, json.getInt("glass_opacity"))
        }
        if (json.has("dialog_opacity")) {
            DialogGlassHelper.saveOpacity(context, json.getInt("dialog_opacity"))
        }
        if (json.has("font_color")) {
            FontColorHelper.saveColor(context, json.getInt("font_color"))
        }
    }

    private suspend fun restoreFiles(
        context: Context,
        fileDao: com.lovestory.app.db.FileDao,
        metadataJson: JSONArray,
        tempFiles: List<Pair<String, File>>
    ) {
        val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        FileManager.initializeStorage(context)

        for (i in 0 until metadataJson.length()) {
            val meta = metadataJson.getJSONObject(i)
            val locationType = meta.getString("locationType")
            val targetId = meta.getString("targetId")
            val originalName = meta.getString("originalName")
            val internalPath = meta.getString("internalPath")
            val storedName = File(internalPath).name

            val savedDirName = File(internalPath).parentFile?.name ?: ""

            val exportPath = when (locationType) {
                "GALLERY_PAGE" -> "gallery/$storedName"
                "FILES_PAGE" -> "files_page/$storedName"
                "CALENDAR_DATE" -> "calendar/$savedDirName/$storedName"
                else -> "other/$storedName"
            }

            val tempFile = tempFiles.find { it.first == exportPath }?.second
            if (tempFile != null && tempFile.exists()) {
                val fileLocation = FileLocation(
                    type = LocationType.valueOf(locationType),
                    targetId = targetId
                )
                val storagePath = FileManager.getStoragePath(context, fileLocation, FileManager.generateUniqueFileName(originalName))
                val destFile = File(storagePath)
                tempFile.copyTo(destFile, overwrite = true)

                val entity = FileEntity(
                    originalName = originalName,
                    internalPath = storagePath,
                    fileType = meta.getString("fileType"),
                    uploadDate = meta.getLong("uploadDate"),
                    fileSize = meta.getLong("fileSize"),
                    locationType = locationType,
                    targetId = targetId
                )
                fileDao.insert(entity)
            }
        }
    }

    private suspend fun restoreNotes(noteDao: com.lovestory.app.db.NoteDao, notesJson: JSONArray) {
        for (i in 0 until notesJson.length()) {
            val noteJson = notesJson.getJSONObject(i)
            val note = NoteEntity(
                content = noteJson.getString("content"),
                timestamp = noteJson.getLong("timestamp"),
                isPinned = noteJson.optBoolean("isPinned", false)
            )
            noteDao.insert(note)
        }
    }

    private fun restoreBackground(context: Context, tempFile: File) {
        val destFile = File(context.filesDir, "background_${tempFile.name}")
        tempFile.copyTo(destFile, overwrite = true)
        val uri = Uri.fromFile(destFile).toString()
        context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, uri)
            .apply()
    }

    private fun showNotification(context: Context, text: String, progress: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.export_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        if (progress > 0 && progress < 100) {
            builder.setProgress(100, progress, false)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun cancelNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    override fun saveToDownloads(sourceFile: File): Uri? {
        val fileName = sourceFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            return Uri.fromFile(destFile)
        }
    }
}
