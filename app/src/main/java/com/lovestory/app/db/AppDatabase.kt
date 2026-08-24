package com.lovestory.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, FileEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun fileDao(): FileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isPinned INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO notes_new (id, content, timestamp, isPinned) SELECT id, content, timestamp, isPinned FROM notes")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                db.execSQL("""
                    CREATE TABLE files_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        originalName TEXT NOT NULL,
                        internalPath TEXT NOT NULL,
                        fileType TEXT NOT NULL,
                        uploadDate INTEGER NOT NULL,
                        fileSize INTEGER NOT NULL,
                        locationType TEXT NOT NULL,
                        targetId TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO files_new (id, originalName, internalPath, fileType, uploadDate, fileSize, locationType, targetId) SELECT id, originalName, internalPath, fileType, uploadDate, fileSize, locationType, targetId FROM files")
                db.execSQL("DROP TABLE files")
                db.execSQL("ALTER TABLE files_new RENAME TO files")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_files_locationType_targetId ON files (locationType, targetId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lovestory_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
