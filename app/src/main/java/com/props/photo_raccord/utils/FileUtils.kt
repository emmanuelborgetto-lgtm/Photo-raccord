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

package com.props.photo_raccord.utils

import android.content.Context
import android.graphics.Bitmap.createBitmap
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri

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

        // Dessin du bandeau via la fonction commune (voir BannerUtils.kt)
        drawInfoBanner(canvas, totalWidth, photoHeight.toFloat(), totalHeight.toFloat(), projet, date, newDecor, newSequence)

        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> updatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out) }
        photoOnly.recycle(); updatedBitmap.recycle(); sourceBitmap.recycle()
    } catch (e: Exception) { Log.e("FileUtils", "Erreur bandeau", e) }
}

fun deletePhotoFile(context: Context, photo: com.props.photo_raccord.PhotoEntity) {
    try {
        val uri = photo.uri.toUri()
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    } catch (e: Exception) { Log.e("GalleryScreen", "Erreur suppression fichier", e) }
}