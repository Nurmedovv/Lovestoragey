package com.lovestory.app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FileDao {

    @Query("SELECT * FROM files WHERE locationType = :locationType AND targetId = :targetId ORDER BY uploadDate DESC")
    suspend fun getByLocation(locationType: String, targetId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE locationType = :locationType ORDER BY uploadDate DESC")
    suspend fun getByLocationType(locationType: String): List<FileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity)

    @Delete
    suspend fun delete(file: FileEntity)

    @Query("DELETE FROM files WHERE internalPath = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT * FROM files")
    suspend fun getAll(): List<FileEntity>

    @Query("DELETE FROM files")
    suspend fun deleteAll()
}
