package com.props.photo_raccord

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PhotoEntity::class], version = 3) // Passage à la version 3 (ajout d'index)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Script de migration de la version 1 vers 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN date TEXT NOT NULL DEFAULT ''")
            }
        }

        // Script de migration de la version 2 vers 3 : ajout des index pour accélérer
        // les requêtes filtrées par "projet" et par "projet" + "decor".
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_projet ON photos(projet)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_projet_decor ON photos(projet, decor)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_raccord_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Ajout des migrations
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}