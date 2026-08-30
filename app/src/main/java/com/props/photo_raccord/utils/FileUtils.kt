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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun updatePhotoBanner(context: Context, photoUriString: String, projet: String, newSequence: String, newDecor: String, date: String) {
    try {
        val uri = photoUriString.toUri()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val sourceBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val totalWidth = sourceBitmap.width
        val totalHeight = sourceBitmap.height
        val photoHeight = (totalHeight / 1.08f).toInt()
        val bannerHeight = totalHeight - photoHeight
        if (photoHeight <= 0 || bannerHeight <= 0) return

        val photoOnly = Bitmap.createBitmap(sourceBitmap, 0, 0, totalWidth, photoHeight)
        val updatedBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(updatedBitmap)
        canvas.drawBitmap(photoOnly, 0f, 0f, null)
        drawInfoBanner(canvas, totalWidth, photoHeight.toFloat(), totalHeight.toFloat(), projet, date, newDecor, newSequence)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> updatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        photoOnly.recycle(); updatedBitmap.recycle(); sourceBitmap.recycle()
    } catch (e: Exception) { Log.e("FileUtils", "Erreur bandeau", e) }
}

/**
 * Importe une image externe, lui ajoute le bandeau PhotoRaccord et la place
 * dans le dossier du projet en respectant le stockage configuré dans Settings.
 * Doit être appelée depuis Dispatchers.IO.
 */
fun importAndProcessPhoto(
    context: Context,
    sourceUri: Uri,
    projet: String,
    sequence: String,
    decor: String
): Pair<String, String> {
    val prefs = context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE)
    val customTreeUriString = prefs.getString("storage_tree_uri", null)
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    val safeProjet = if (projet.isBlank()) "Projet" else projet

    val input = context.contentResolver.openInputStream(sourceUri)
        ?: throw IllegalArgumentException("Impossible de lire l'image sélectionnée")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    input.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Image invalide")

    val maxDimension = 1920
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = if (maxSide > maxDimension) (maxSide + maxDimension - 1) / maxDimension else 1
    val bitmapInput = context.contentResolver.openInputStream(sourceUri)
        ?: throw IllegalArgumentException("Impossible de lire l'image sélectionnée")
    val bitmap = bitmapInput.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: throw IllegalArgumentException("Échec du décodage de l'image")

    val width = bitmap.width
    val height = bitmap.height
    val bannerHeight = (height * 0.08f).toInt().coerceAtLeast(1)
    drawInfoBanner(Canvas(bitmap), width, (height - bannerHeight).toFloat(), height.toFloat(), safeProjet, date, decor, sequence)
    val fileName = "IMG_${System.currentTimeMillis()}.jpg"
    var finalUri: Uri? = null

    try {
        if (!customTreeUriString.isNullOrEmpty()) {
            val rootDir = DocumentFile.fromTreeUri(context, customTreeUriString.toUri())
                ?: throw IllegalArgumentException("Dossier de stockage inaccessible")
            var projectDir = rootDir.findFile(safeProjet)
            if (projectDir == null) projectDir = rootDir.createDirectory(safeProjet)
            finalUri = projectDir?.createFile("image/jpeg", fileName)?.uri
                ?: throw IllegalArgumentException("Impossible de créer le fichier dans le dossier du projet")
            context.contentResolver.openOutputStream(finalUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            } ?: throw IllegalArgumentException("Impossible d'écrire l'image importée")
        } else {
            val projectDir = getDefaultPhotoProjectDirectory(context, safeProjet)
            if (!projectDir.exists() && !projectDir.mkdirs()) throw IllegalArgumentException("Impossible de créer le dossier du projet")
            val outputFile = File(projectDir, fileName)
            outputFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            finalUri = Uri.fromFile(outputFile)
        }
    } finally {
        bitmap.recycle()
    }

    if (finalUri == null) throw IllegalArgumentException("Impossible de créer la photo importée")
    return finalUri.toString() to date
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