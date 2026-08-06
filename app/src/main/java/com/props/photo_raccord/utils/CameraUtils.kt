package com.props.photo_raccord.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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

/**
 * Capture et traite une photo avec :
 * - Sauvegarde directe via MediaStore
 * - Décodage optimisé (downsampling) pour éviter les OOM
 * - Dessin de la bannière en arrière-plan
 * - Insertion en base de données
 */
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
    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val safeProjet = if (projet.isBlank()) "Projet" else projet
    val resolver = context.contentResolver

    // Préparation des ContentValues pour MediaStore
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/$safeProjet")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    // URI de la collection (pas d'un élément)
    val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val outputOptions = ImageCapture.OutputFileOptions.Builder(resolver, collectionUri, values).build()

    imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val savedUri = outputFileResults.savedUri
            if (savedUri == null) {
                mainExecutor.execute {
                    onPhotoSaved("Erreur : URI manquante après sauvegarde")
                }
                return
            }

            // Traitement lourd en arrière-plan
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // --- ÉTAPE 1 : Lire les dimensions sans charger l'image ---
                    var inputStream = resolver.openInputStream(savedUri)
                        ?: throw Exception("Impossible d'ouvrir le flux image (1)")

                    val boundsOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }

                    BitmapFactory.decodeStream(inputStream, null, boundsOptions)
                    inputStream.close()

                    val srcWidth = boundsOptions.outWidth
                    val srcHeight = boundsOptions.outHeight

                    if (srcWidth <= 0 || srcHeight <= 0) {
                        throw Exception("Dimensions d'image invalides")
                    }

                    // --- ÉTAPE 2 : Calculer le facteur de downsampling ---
                    // Taille maximale pour le traitement (1920px pour éviter les OOM)
                    val maxDimension = 1920
                    val scaleFactor = if (srcWidth > maxDimension || srcHeight > maxDimension) {
                        val maxSide = maxOf(srcWidth, srcHeight)
                        (maxSide + maxDimension - 1) / maxDimension
                    } else {
                        1
                    }

                    // --- ÉTAPE 3 : Décoder avec downsampling ---
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = scaleFactor
                        inMutable = true
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    inputStream = resolver.openInputStream(savedUri)
                        ?: throw Exception("Impossible d'ouvrir le flux image (2)")

                    val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                        ?: throw Exception("Échec du décodage du bitmap")

                    inputStream.close()

                    // --- ÉTAPE 4 : Dessiner la bannière ---
                    val canvas = Canvas(bitmap)
                    val width = bitmap.width
                    val height = bitmap.height
                    val bannerHeight = (height * 0.08f).toInt().coerceAtLeast(1)

                    // Fond noir pour la bannière
                    val paintBg = Paint().apply { color = Color.BLACK }
                    canvas.drawRect(
                        0f,
                        (height - bannerHeight).toFloat(),
                        width.toFloat(),
                        height.toFloat(),
                        paintBg
                    )

                    // Calcul des positions du texte
                    val paddingX = width * 0.02f
                    val textSizeTitle = width * 0.035f
                    val textSizeDate = width * 0.028f
                    val textYTitle = height - (bannerHeight * 0.55f)
                    val textYDate = height - (bannerHeight * 0.2f)
                    val textYCenterRight = height - (bannerHeight * 0.38f)

                    // Style pour le projet (gris)
                    val paintProjet = Paint().apply {
                        color = Color.GRAY
                        textSize = textSizeTitle
                        typeface = Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                    }

                    // Style pour la date (blanc)
                    val paintDate = Paint().apply {
                        color = Color.WHITE
                        textSize = textSizeDate
                        typeface = Typeface.DEFAULT
                        isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                    }

                    // Style pour le décor (blanc, centré)
                    val paintDecor = Paint().apply {
                        color = Color.WHITE
                        textSize = textSizeTitle
                        typeface = Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }

                    // Style pour la séquence (blanc, à droite)
                    val paintSequence = Paint().apply {
                        color = Color.WHITE
                        textSize = textSizeTitle
                        typeface = Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        textAlign = Paint.Align.RIGHT
                    }

                    // Dessin du texte
                    canvas.drawText(safeProjet, paddingX, textYTitle, paintProjet)
                    canvas.drawText(currentDate, paddingX, textYDate, paintDate)
                    canvas.drawText("Décor: $decor", width / 2f, textYCenterRight, paintDecor)
                    canvas.drawText("Seq: $sequence", width - paddingX, textYCenterRight, paintSequence)

                    // --- ÉTAPE 5 : Sauvegarder le bitmap modifié ---
                    resolver.openOutputStream(savedUri, "wt")?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    } ?: throw Exception("Impossible d'ouvrir le flux de sortie")

                    // --- ÉTAPE 6 : Libérer la mémoire ---
                    bitmap.recycle()

                    // --- ÉTAPE 7 : Marquer comme non-pending pour Android 10+ ---
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(savedUri, values, null, null)
                    }

                    // --- ÉTAPE 8 : Sauvegarder en base de données ---
                    photoDao.insert(
                        PhotoEntity(
                            uri = savedUri.toString(),
                            projet = safeProjet,
                            sequence = sequence,
                            decor = decor,
                            date = currentDate
                        )
                    )

                    // --- ÉTAPE 9 : Notifier le succès ---
                    mainExecutor.execute {
                        onPhotoSaved("Sauvegardée dans $safeProjet")
                    }

                } catch (e: Throwable) {
                    Log.e("CameraUtils", "Erreur lors du traitement de la photo", e)
                    mainExecutor.execute {
                        onPhotoSaved("Erreur : ${e.message ?: "Traitement échoué"}")
                    }
                }
            }
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraX", "Erreur de capture", exception)
            mainExecutor.execute {
                onPhotoSaved("Erreur : ${exception.message ?: "Capture échouée"}")
            }
        }
    })
}