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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.props.photo_raccord.PhotoDao
import com.props.photo_raccord.PhotoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import androidx.core.net.toUri

fun takeAndProcessPhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: Executor,
    coroutineScope: CoroutineScope,
    photoDao: PhotoDao,
    projet: String,
    sequence: String,
    decor: String,
    onPhotoSaved: (String) -> Unit
) {
    val prefs = context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE)
    val showInGallery = prefs.getBoolean("show_in_gallery", true)

    // 1. Récupération de l'URI personnalisée
    val customTreeUriString = prefs.getString("storage_tree_uri", null)

    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val safeProjet = if (projet.isBlank()) "Projet" else projet
    val resolver = context.contentResolver

    // 2. Création d'un fichier temporaire pour la capture CameraX
    val tempFile = java.io.File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 3. Traitement de l'image depuis le fichier temporaire
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

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = scaleFactor
                        inMutable = true
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions) ?: throw Exception("Échec du décodage du bitmap")

                    val canvas = Canvas(bitmap)
                    val width = bitmap.width
                    val height = bitmap.height
                    val bannerHeight = (height * 0.08f).toInt().coerceAtLeast(1)

                    // Dessin du bandeau via la fonction commune (voir BannerUtils.kt)
                    drawInfoBanner(canvas, width, (height - bannerHeight).toFloat(), height.toFloat(), safeProjet, currentDate, decor, sequence)

                    // 4. Sauvegarde dans le dossier approprié
                    var finalUri: android.net.Uri? = null
                    val fileName = "IMG_${System.currentTimeMillis()}.jpg"

                    if (!customTreeUriString.isNullOrEmpty()) {
                        try {
                            val treeUri = customTreeUriString.toUri()
                            val rootDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                            var projectDir = rootDir?.findFile(safeProjet)
                            if (projectDir == null) {
                                projectDir = rootDir?.createDirectory(safeProjet)
                            }
                            val newFile = projectDir?.createFile("image/jpeg", fileName)
                            finalUri = newFile?.uri
                        } catch (e: Exception) {
                            Log.e("CameraUtils", "Erreur SAF, basculement vers MediaStore", e)
                        }
                    }

                    // Basculement vers MediaStore (Défaut) si SAF n'est pas utilisé ou a échoué
                    var pendingValues: ContentValues? = null
                    if (finalUri == null) {
                        pendingValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/PhotoRaccord/$safeProjet")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }
                        finalUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pendingValues)
                    }

                    if (finalUri == null) throw Exception("Impossible de créer le fichier de destination")

                    resolver.openOutputStream(finalUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    } ?: throw Exception("Impossible d'ouvrir le flux de sortie")

                    bitmap.recycle()

                    if (pendingValues != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        pendingValues.clear()
                        pendingValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(finalUri, pendingValues, null, null)
                    }

                    photoDao.insert(PhotoEntity(uri = finalUri.toString(), projet = safeProjet, sequence = sequence, decor = decor, date = currentDate))

                    // 5. Gestion du masquage de la galerie (.nomedia)
                    if (!showInGallery) {
                        if (!customTreeUriString.isNullOrEmpty()) {
                            try {
                                val treeUri = customTreeUriString.toUri()
                                val rootDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                                if (rootDir != null && rootDir.findFile(".nomedia") == null) {
                                    rootDir.createFile("application/octet-stream", ".nomedia")
                                }
                            } catch (e: Exception) { Log.e("CameraUtils", "Erreur création .nomedia SAF", e) }
                        } else {
                            try {
                                val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
                                val photoRaccordDir = java.io.File(dcimDir, "PhotoRaccord")
                                if (!photoRaccordDir.exists()) photoRaccordDir.mkdirs()
                                val nomediaFile = java.io.File(photoRaccordDir, ".nomedia")
                                if (!nomediaFile.exists()) nomediaFile.createNewFile()
                            } catch (e: Exception) { Log.e("CameraUtils", "Erreur création .nomedia Défaut", e) }
                        }
                    }

                    mainExecutor.execute { onPhotoSaved("Sauvegardée dans $safeProjet") }

                } catch (e: Throwable) {
                    Log.e("CameraUtils", "Erreur lors du traitement de la photo", e)
                    mainExecutor.execute { onPhotoSaved("Erreur : ${e.message ?: "Traitement échoué"}") }
                } finally {
                    // Nettoyage impératif du fichier temporaire
                    if (tempFile.exists()) tempFile.delete()
                }
            }
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraX", "Erreur de capture", exception)
            mainExecutor.execute { onPhotoSaved("Erreur : ${exception.message ?: "Capture échouée"}") }
        }
    })
}