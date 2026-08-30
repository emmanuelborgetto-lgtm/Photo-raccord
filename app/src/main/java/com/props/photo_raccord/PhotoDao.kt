/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.props.photo_raccord

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ProjetCount(val projet: String, val count: Int)

@Dao
interface PhotoDao {
    @Query("SELECT projet, COUNT(*) as count FROM photos GROUP BY projet")
    fun getPhotoCountsByProjet(): Flow<List<ProjetCount>>

    @Query("SELECT * FROM photos WHERE projet = :projet")
    fun getPhotosParProjet(projet: String): Flow<List<PhotoEntity>>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    /**
     * Liste tous les projets, y compris ceux qui n'ont aucune photo.
     * Pour un projet avec des photos, la date de la photo la plus récente est
     * utilisée. Pour un projet vide, on retombe sur sa date de création.
     */
    @Query("""
        SELECT p.nom
        FROM projets p
        LEFT JOIN photos ph ON ph.projet = p.nom
        GROUP BY p.nom, p.createdAt
        ORDER BY
            COALESCE(
                MAX(substr(ph.date, 7, 4) || substr(ph.date, 4, 2) || substr(ph.date, 1, 2) || substr(ph.date, 12, 5)),
                strftime('%Y%m%d%H%M', p.createdAt / 1000, 'unixepoch')
            ) DESC,
            p.nom ASC
    """)
    fun getDistinctProjets(): Flow<List<String>>

    @Query("SELECT DISTINCT decor FROM photos WHERE projet = :projet AND decor != '' ORDER BY decor ASC")
    fun getDistinctDecorsParProjet(projet: String): Flow<List<String>>

    @Query("SELECT * FROM photos")
    suspend fun getAllPhotosOnce(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun createProjet(projet: ProjectEntity)

    @Query("UPDATE photos SET projet = :newName WHERE projet = :oldName")
    suspend fun renameProjetPhotos(oldName: String, newName: String)

    @Query("UPDATE projets SET nom = :newName WHERE nom = :oldName")
    suspend fun renameProjet(oldName: String, newName: String)

    @Query("DELETE FROM photos WHERE projet = :projectName")
    suspend fun deleteProjetPhotos(projectName: String)

    @Query("DELETE FROM projets WHERE nom = :projectName")
    suspend fun deleteProjet(projectName: String)

    @Update
    suspend fun update(photo: PhotoEntity)

    @Delete
    suspend fun deletePhotos(photos: List<PhotoEntity>)

    /**
     * Lors d'une migration depuis l'ancien modèle, les projets connus uniquement
     * par la table photos sont créés dans la nouvelle table projets.
     */
    @Query("INSERT OR IGNORE INTO projets(nom, createdAt) SELECT projet, CAST(strftime('%s','now') AS INTEGER) * 1000 FROM photos WHERE projet != ''")
    suspend fun importExistingProjects()
}
