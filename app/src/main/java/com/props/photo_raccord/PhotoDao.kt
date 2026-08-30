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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT COUNT(*) FROM photos WHERE projet = :projet")
    fun getPhotoCountByProject(projet: String): Flow<Int>
    @Query("SELECT * FROM photos WHERE projet = :projet")
    fun getPhotosParProjet(projet: String): Flow<List<PhotoEntity>>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT DISTINCT projet FROM photos WHERE projet != '' ORDER BY projet ASC")
    fun getDistinctProjets(): Flow<List<String>>

    @Query("SELECT DISTINCT decor FROM photos WHERE projet = :projet AND decor != '' ORDER BY decor ASC")
    fun getDistinctDecorsParProjet(projet: String): Flow<List<String>>

    @Query("SELECT * FROM photos")
    suspend fun getAllPhotosOnce(): List<PhotoEntity>

    @Query("UPDATE photos SET projet = :newName WHERE projet = :oldName")
    suspend fun renameProjet(oldName: String, newName: String)

    @Query("DELETE FROM photos WHERE projet = :projectName")
    suspend fun deleteProjet(projectName: String)

    @Update
    suspend fun update(photo: PhotoEntity)

    @Delete
    suspend fun deletePhotos(photos: List<PhotoEntity>)
}