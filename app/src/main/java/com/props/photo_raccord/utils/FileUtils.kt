package com.props.photo_raccord.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates a new bitmap with the original photo untouched and the banner appended below it. */
fun createBanneredBitmap(
    source: Bitmap,
    projet: String,
    date: String,
    decor: String,
    sequence: String
): Bitmap {
    val bannerHeight = (source.height * 0.08f).toInt().coerceAtLeast(1)
    val result = Bitmap.createBitmap(
        source.width,
        source.height + bannerHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(result)
    canvas.drawBitmap(source, 0f, 0f, null)
    drawInfoBanner(
        canvas,
        source.width,
        source.height.toFloat(),
        result.height.toFloat(),
        projet,
        date,
        decor,
        sequence
    )
    return result
}

fun updatePhotoBanner(
    context: Context,
    photoUriString: String,
    projet: String,
    newSequence: String,
    newDecor: String,
    date: String
) {
    try {
        val uri = photoUriString.toUri()
        val sourceBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return

        // Photos created by PhotoRaccord contain an 8% banner. Remove that
        // old banner first, then rebuild the image with the new banner below.
        val photoHeight = (sourceBitmap.height / 1.08f).toInt()
        if (photoHeight <= 0 || photoHeight >= sourceBitmap.height) {
            sourceBitmap.recycle()
            return
        }

        val photoOnly = Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            photoHeight
        )

        val updatedBitmap = createBanneredBitmap(
            photoOnly,
            projet,
            date,
            newDecor,
            newSequence
        )

        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            updatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        photoOnly.recycle()
        updatedBitmap.recycle()
        sourceBitmap.recycle()
    } catch (e: Exception) {
        Log.e("FileUtils", "Erreur bandeau", e)
    }
}

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

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(sourceUri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: throw IllegalArgumentException("Impossible de lire l'image sélectionnée")

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("Image invalide")
    }

    val maxDimension = 1920
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = if (maxSide > maxDimension) {
        (maxSide + maxDimension - 1) / maxDimension
    } else 1

    val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: throw IllegalArgumentException("Échec du décodage de l'image")

    val finalBitmap = createBanneredBitmap(bitmap, safeProjet, date, decor, sequence)
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
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            } ?: throw IllegalArgumentException("Impossible d'écrire l'image importée")
        } else {
            val projectDir = getDefaultPhotoProjectDirectory(context, safeProjet)
            if (!projectDir.exists() && !projectDir.mkdirs()) {
                throw IllegalArgumentException("Impossible de créer le dossier du projet")
            }
            val outputFile = File(projectDir, fileName)
            outputFile.outputStream().use {
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            finalUri = Uri.fromFile(outputFile)
        }
    } finally {
        bitmap.recycle()
        finalBitmap.recycle()
    }

    return finalUri?.toString()?.let { it to date }
        ?: throw IllegalArgumentException("Impossible de créer la photo importée")
}

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
    } catch (e: Exception) {
        Log.e("GalleryScreen", "Erreur suppression fichier", e)
    }
}
