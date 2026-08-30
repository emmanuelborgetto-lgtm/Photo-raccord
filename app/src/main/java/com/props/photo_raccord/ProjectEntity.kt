package com.props.photo_raccord

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Projet indépendant des photos : il peut donc exister même lorsqu'il ne
 * contient encore aucune photo.
 */
@Entity(tableName = "projets")
data class ProjectEntity(
    @PrimaryKey
    val nom: String,
    val createdAt: Long = System.currentTimeMillis()
)
