/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.props.photo_raccord.utils

import android.content.Context
import android.graphics.Bitmap.createBitmap
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import java.io.File

fun updatePhotoBanner(context: Context, photoUriString: String, projet: String, newSequence: String, newDecor: String, date: String) {
    try {
        val uri = photoUriString.toUri()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val sourceBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val totalWidth = sourceBitmap.width
        val totalHeight = sourceBitmap.height
        val photoHeight = (totalHeight / 1.08f).toInt()
        val bannerHeight = totalHeight - photoHeight
        if (photoHeight <= 0 || bannerHeight <= 0) return

        val photoOnly = createBitmap(sourceBitmap, 0, 0, totalWidth, photoHeight)
        val updatedBitmap = createBitmap(totalWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(updatedBitmap)
        canvas.drawBitmap(photoOnly, 0f, 0f, null)
        drawInfoBanner(canvas, totalWidth, photoHeight.toFloat(), totalHeight.toFloat(), projet, date, newDecor, newSequence)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> updatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out) }
        photoOnly.recycle(); updatedBitmap.recycle(); sourceBitmap.recycle()
    } catch (e: Exception) { Log.e("FileUtils", "Erreur bandeau", e) }
}

/**
 * Supprime le fichier physique correspondant à une photo.
 * Les URI SAF sont supprimées via DocumentsContract ; les fichiers privés
 * de PhotoRaccord sont supprimés directement du stockage de l'application.
 * La corbeille système n'est pas applicable aux fichiers situés dans
 * Android/data : ils ne sont pas des éléments MediaStore.
 */
fun deletePhotoFile(context: Context, photo: com.props.photo_raccord.PhotoEntity) {
    try {
        val uri = photo.uri.toUri()
        when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return)
                if (file.exists() && !file.delete()) {
                    Log.e("GalleryScreen", "Impossible de supprimer ${file.absolutePath}")
                }
            }
            "content" -> {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null)
                }
            }
            else -> Log.w("GalleryScreen", "URI non prise en charge pour suppression : $uri")
        }
    } catch (e: Exception) { Log.e("GalleryScreen", "Erreur suppression fichier", e) }
}