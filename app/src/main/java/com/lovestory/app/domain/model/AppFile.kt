package com.lovestory.app.domain.model

import java.util.Date

// доменная модель файла приложения
data class AppFile(
    val id: Long,
    val originalName: String,
    val internalPath: String,
    val fileType: FileType,
    val uploadDate: Date,
    val fileSize: Long,
    val fileLocation: FileLocation,
    val description: String = ""
)

// расположение файла внутри приложения
data class FileLocation(
    val type: LocationType,
    val targetId: String = ""
)

enum class LocationType {
    CALENDAR_DATE,
    GALLERY_PAGE,
    FILES_PAGE
}

enum class FileType {
    PHOTO, VIDEO, AUDIO, DOCUMENT, OTHER
}
