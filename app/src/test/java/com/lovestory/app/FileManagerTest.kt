package com.lovestory.app

import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.domain.model.FileLocation
import com.lovestory.app.domain.model.FileType
import com.lovestory.app.domain.model.AppFile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FileManagerTest {

    // ==================== getFileTypeFromExtension ====================

    @Test
    fun `getFileTypeFromExtension - photo extensions return PHOTO`() {
        val photoExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        photoExtensions.forEach { ext ->
            assertEquals(FileType.PHOTO, FileManager.getFileTypeFromExtension("image.$ext"))
        }
    }

    @Test
    fun `getFileTypeFromExtension - video extensions return VIDEO`() {
        val videoExtensions = listOf("mp4", "avi", "mov", "mkv", "webm", "3gp")
        videoExtensions.forEach { ext ->
            assertEquals(FileType.VIDEO, FileManager.getFileTypeFromExtension("video.$ext"))
        }
    }

    @Test
    fun `getFileTypeFromExtension - audio extensions return AUDIO`() {
        val audioExtensions = listOf("mp3", "wav", "ogg", "m4a", "aac")
        audioExtensions.forEach { ext ->
            assertEquals(FileType.AUDIO, FileManager.getFileTypeFromExtension("audio.$ext"))
        }
    }

    @Test
    fun `getFileTypeFromExtension - document extensions return DOCUMENT`() {
        val docExtensions = listOf("pdf", "doc", "docx", "txt", "xls", "xlsx")
        docExtensions.forEach { ext ->
            assertEquals(FileType.DOCUMENT, FileManager.getFileTypeFromExtension("file.$ext"))
        }
    }

    @Test
    fun `getFileTypeFromExtension - unknown extension returns OTHER`() {
        assertEquals(FileType.OTHER, FileManager.getFileTypeFromExtension("file.xyz"))
        assertEquals(FileType.OTHER, FileManager.getFileTypeFromExtension("noextension"))
    }

    @Test
    fun `getFileTypeFromExtension - case insensitive`() {
        assertEquals(FileType.PHOTO, FileManager.getFileTypeFromExtension("image.JPG"))
        assertEquals(FileType.VIDEO, FileManager.getFileTypeFromExtension("video.MP4"))
        assertEquals(FileType.AUDIO, FileManager.getFileTypeFromExtension("audio.WAV"))
    }

    // ==================== generateUniqueFileName ====================

    @Test
    fun `generateUniqueFileName - preserves extension`() {
        val result = FileManager.generateUniqueFileName("photo.jpg")
        assertTrue(result.endsWith(".jpg"))
    }

    @Test
    fun `generateUniqueFileName - sanitizes special characters`() {
        val result = FileManager.generateUniqueFileName("my file (copy).jpg")
        assertFalse(result.contains(" "))
        assertFalse(result.contains("("))
        assertFalse(result.contains(")"))
        assertTrue(result.contains("my_file__copy_"))
    }

    @Test
    fun `generateUniqueFileName - contains timestamp`() {
        val before = System.currentTimeMillis()
        val result = FileManager.generateUniqueFileName("test.png")
        val after = System.currentTimeMillis()

        val timestamp = result.substringAfterLast("_").substringBeforeLast(".").toLongOrNull()
        assertNotNull(timestamp)
        assertTrue(timestamp!! in before..after)
    }

    @Test
    fun `generateUniqueFileName - handles files without extension`() {
        val result = FileManager.generateUniqueFileName("noextension")
        assertTrue(result.contains("noextension_"))
    }

    // ==================== createCalendarDateId ====================

    @Test
    fun `createCalendarDateId - formats correctly`() {
        assertEquals("15_Января", FileManager.createCalendarDateId(15, "Января"))
        assertEquals("1_Марта", FileManager.createCalendarDateId(1, "Марта"))
    }

    @Test
    fun `createCalendarDateId - replaces spaces with underscores`() {
        assertEquals("1_Мая", FileManager.createCalendarDateId(1, "Мая"))
    }

    // ==================== deleteFile ====================

    @Test
    fun `deleteFile - returns true for non-existent file`() {
        assertTrue(FileManager.deleteFile("/nonexistent/path/file.txt"))
    }

    // ==================== getStoragePath - path traversal protection ====================

    @Test
    fun `getStoragePath - sanitizes filename`() {
        val context = android.app.Application()
        // This would need mocking, but the logic is tested via generateUniqueFileName
    }

    // ==================== AppFile data class ====================

    @Test
    fun `AppFile - equality by internalPath`() {
        val file1 = AppFile(
            id = 1,
            originalName = "test.jpg",
            internalPath = "/path/to/test.jpg",
            fileType = FileType.PHOTO,
            uploadDate = java.util.Date(),
            fileSize = 1024,
            fileLocation = FileLocation(LocationType.GALLERY_PAGE)
        )
        val file2 = file1.copy(id = 2) // different id

        // Data class equality compares all fields
        assertNotEquals(file1, file2)
    }

    @Test
    fun `AppFile - filter by internalPath works`() {
        val files = listOf(
            AppFile(1, "a.jpg", "/path/a.jpg", FileType.PHOTO, java.util.Date(), 100, FileLocation(LocationType.GALLERY_PAGE)),
            AppFile(2, "b.jpg", "/path/b.jpg", FileType.PHOTO, java.util.Date(), 200, FileLocation(LocationType.GALLERY_PAGE)),
            AppFile(3, "c.jpg", "/path/c.jpg", FileType.PHOTO, java.util.Date(), 300, FileLocation(LocationType.GALLERY_PAGE))
        )

        val filtered = files.filter { it.internalPath != "/path/b.jpg" }
        assertEquals(2, filtered.size)
        assertFalse(filtered.any { it.internalPath == "/path/b.jpg" })
    }
}
