package com.lovestory.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    indices = [Index("locationType", "targetId")]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val internalPath: String,
    val fileType: String,
    val uploadDate: Long,
    val fileSize: Long,
    val locationType: String,
    val targetId: String = ""
)
