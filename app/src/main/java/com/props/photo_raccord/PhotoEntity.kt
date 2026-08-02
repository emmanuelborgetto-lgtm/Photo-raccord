package com.props.photo_raccord

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Index sur "projet" (utilisé dans quasiment toutes les requêtes) et sur "projet"+"decor"
// (utilisé pour la liste des décors distincts) pour accélérer ces requêtes quand la base grossit.
@Entity(
    tableName = "photos",
    indices = [Index(value = ["projet"]), Index(value = ["projet", "decor"])]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uri: String,
    val projet: String,
    val sequence: String,
    val decor: String,
    val date: String = ""
)