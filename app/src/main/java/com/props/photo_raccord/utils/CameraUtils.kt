/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 */
package com.props.photo_raccord.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.props.photo_raccord.PhotoDao
import com.props.photo_raccord.PhotoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

private const val PREFS_NAME = "photo_raccord_prefs"
private const val PREF_STORAGE_TREE_URI = "storage_tree_uri"
private const val PREF_SHOW_IN_GALLERY = "show_in_gallery"

/** Dossier par défaut, privé à l'application : Android/data/<package>/files/PhotoRaccord. */
fun getDefaultPhotoDirectory(context: Context): File =
    File(context.getExternalFilesDir(null) ?: context.filesDir, "PhotoRaccord")

fun getDefaultPhotoProjectDirectory(context: Context, projet: String): File =
    File(getDefaultPhotoDirectory(context), projet)

fun ensureDefaultNomedia(context: Context, showInGallery: Boolean) {
    try {
        val directory = getDefaultPhotoDirectory(context)
        val nomedia = File(directory, ".nomedia")
        if (showInGallery) {
            if (nomedia.exists()) nomedia.delete()
        } else {
            if (!directory.exists()) directory.mkdirs()
            if (!nomedia.exists()) nomedia.createNewFile()
        }
    } catch (e: Exception) {
        Log.e("CameraUtils", "Erreur gestion .nomedia du dossier par défaut", e)
    }
}

fun takeAndProcessPhoto(
    context: Context, imageCapture: ImageCapture, cameraExecutor: Executor,
    coroutineScope: CoroutineScope, photoDao: PhotoDao, projet: String,
    sequence: String, decor: String, onPhotoSaved: (String) -> Unit
) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val showInGallery = prefs.getBoolean(PREF_SHOW_IN_GALLERY, false)
    val customTreeUriString = prefs.getString(PREF_STORAGE_TREE_URI, null)
    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val safeProjet = if (projet.isBlank()) "Projet" else projet
    val resolver = context.contentResolver
    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)
                    val srcWidth = boundsOptions.outWidth
                    val srcHeight = boundsOptions.outHeight
                    if (srcWidth <= 0 || srcHeight <= 0) throw Exception("Dimensions d'image invalides")
                    val maxDimension = 1920
                    val scaleFactor = if (srcWidth > maxDimension || srcHeight > maxDimension) {
                        val maxSide = maxOf(srcWidth, srcHeight)
                        (maxSide + maxDimension - 1) / maxDimension
                    } else 1
                    val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, BitmapFactory.Options().apply {
                        inSampleSize = scaleFactor; inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888
                    }) ?: throw Exception("Échec du décodage du bitmap")

                    val width = bitmap.width
                    val height = bitmap.height
                    val bannerHeight = (height * 0.08f).toInt().coerceAtLeast(1)
                    drawInfoBanner(Canvas(bitmap), width, (height - bannerHeight).toFloat(), height.toFloat(), safeProjet, currentDate, decor, sequence)

                    var finalUri: Uri? = null
                    var pendingValues: ContentValues? = null
                    val fileName = "IMG_${System.currentTimeMillis()}.jpg"

                    if (!customTreeUriString.isNullOrEmpty()) {
                        try {
                            val rootDir = DocumentFile.fromTreeUri(context, customTreeUriString.toUri())
                            var projectDir = rootDir?.findFile(safeProjet)
                            if (projectDir == null) projectDir = rootDir?.createDirectory(safeProjet)
                            finalUri = projectDir?.createFile("image/jpeg", fileName)?.uri
                        } catch (e: Exception) { Log.e("CameraUtils", "Erreur SAF", e) }
                    }

                    if (finalUri == null && customTreeUriString.isNullOrEmpty()) {
                        val projectDir = getDefaultPhotoProjectDirectory(context, safeProjet)
                        if (!projectDir.exists() && !projectDir.mkdirs()) throw Exception("Impossible de créer le dossier $safeProjet")
                        val outputFile = File(projectDir, fileName)
                        outputFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        finalUri = Uri.fromFile(outputFile)
                    }

                    if (finalUri == null) {
                        pendingValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PhotoRaccord/$safeProjet")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }
                        finalUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pendingValues)
                    }
                    if (finalUri == null) throw Exception("Impossible de créer le fichier de destination")
                    if (finalUri.scheme != "file") {
                        resolver.openOutputStream(finalUri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                            ?: throw Exception("Impossible d'ouvrir le flux de sortie")
                    }
                    bitmap.recycle()
                    if (pendingValues != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        resolver.update(finalUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    }

                    photoDao.insert(PhotoEntity(uri = finalUri.toString(), projet = safeProjet, sequence = sequence, decor = decor, date = currentDate))
                    if (!showInGallery) {
                        if (!customTreeUriString.isNullOrEmpty()) {
                            try {
                                val rootDir = DocumentFile.fromTreeUri(context, customTreeUriString.toUri())
                                if (rootDir != null && rootDir.findFile(".nomedia") == null) rootDir.createFile("application/octet-stream", ".nomedia")
                            } catch (e: Exception) { Log.e("CameraUtils", "Erreur création .nomedia SAF", e) }
                        } else ensureDefaultNomedia(context, false)
                    }
                    mainExecutor.execute { onPhotoSaved("Sauvegardée dans $safeProjet") }
                } catch (e: Throwable) {
                    Log.e("CameraUtils", "Erreur lors du traitement de la photo", e)
                    mainExecutor.execute { onPhotoSaved("Erreur : ${e.message ?: "Traitement échoué"}") }
                } finally { if (tempFile.exists()) tempFile.delete() }
            }
        }
        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraX", "Erreur de capture", exception)
            mainExecutor.execute { onPhotoSaved("Erreur : ${exception.message ?: "Capture échouée"}") }
        }
    })
}