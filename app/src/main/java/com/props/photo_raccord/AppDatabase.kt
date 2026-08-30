/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN date TEXT NOT NULL DEFAULT ''")
            }
        }

        // Script de migration de la version 2 vers 3 : ajout des index pour accélérer
        // les requêtes filtrées par "projet" et par "projet" + "decor".
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_projet ON photos(projet)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_projet_decor ON photos(projet, decor)")
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