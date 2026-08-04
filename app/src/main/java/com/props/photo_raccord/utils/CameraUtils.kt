package com.props.photo_raccord.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OutputFileOptions
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

/**
 * Safer, lower-memory take + process:
 * - Save image directly to MediaStore using ImageCapture OutputFileOptions.
 * - Offload banner drawing / DB insert / re-encode to Dispatchers.IO.
 * - Decode with downsampling to avoid OOM when drawing the banner.
 *
 * Notes:
 * - This implementation decodes a downsampled bitmap for drawing the banner to avoid OOM.
 *   If you require preserving full resolution for the overlay, you need a different strategy
 *   (native processing, stream-based compositing, or a persistent native worker).
 */
// important imports omitted for brevity — use same imports as before

fun takeAndProcessPhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: java.util.concurrent.Executor,
    coroutineScope: CoroutineScope,
    photoDao: PhotoDao,
    projet: String,
    sequence: String,
    decor: String,
    onPhotoSaved: (String) -> Unit
) {
    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val safeProjet = projet.ifEmpty { "Projet" }

    // Prepare ContentValues to describe the new image (do NOT call resolver.insert here)
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/$safeProjet")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    // IMPORTANT: pass the collection URI (not an item URI). CameraX will insert the item.
    val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val outputOptions = OutputFileOptions.Builder(resolver, collectionUri, values).build()

    imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            // CameraX saved a JPEG and inserted the item; obtain the created item Uri
            val savedUri = outputFileResults.savedUri
            if (savedUri == null) {
                mainExecutor.execute { onPhotoSaved("Erreur lors de la sauvegarde (URI manquante)") }
                return
            }

            // Offload heavy processing to IO dispatcher
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Downsample parameters to avoid OOM when drawing banner
                    val MAX_DECODE_DIMENSION = 1920

                    // Read bounds
                    var input = resolver.openInputStream(savedUri)
                    if (input == null) throw Exception("Impossible d'ouvrir le flux image")
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, boundsOpts)
                    input.close()

                    val srcW = boundsOpts.outWidth
                    val srcH = boundsOpts.outHeight
                    val sample = if (srcW <= 0 || srcH <= 0) 1 else {
                        val maxSide = maxOf(srcW, srcH)
                        val ratio = (maxSide + MAX_DECODE_DIMENSION - 1) / MAX_DECODE_DIMENSION
                        ratio.coerceAtLeast(1)
                    }

                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inMutable = true
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    input = resolver.openInputStream(savedUri)
                    if (input == null) throw Exception("Impossible d'ouvrir le flux image (2)")
                    val bitmap = BitmapFactory.decodeStream(input, null, decodeOpts) ?: throw Exception("decodeBitmap failed")
                    input.close()

                    // Draw banner on the decoded bitmap
                    val canvas = Canvas(bitmap)
                    val finalWidth = bitmap.width
                    val finalHeight = bitmap.height
                    val bannerHeight = (finalHeight * 0.08f).toInt().coerceAtLeast(1)

                    val paintBg = Paint().apply { color = Color.BLACK }
                    canvas.drawRect(0f, (finalHeight - bannerHeight).toFloat(), finalWidth.toFloat(), finalHeight.toFloat(), paintBg)

                    val paddingX = finalWidth * 0.02f
                    val textSizeTitle = finalWidth * 0.035f
                    val textSizeDate = finalWidth * 0.028f
                    val textYTitle = finalHeight - (bannerHeight * 0.55f)
                    val textYDate = finalHeight - (bannerHeight * 0.2f)
                    val textYCenterRight = finalHeight - (bannerHeight * 0.38f)

                    val paintProjet = Paint().apply {
                        color = Color.GRAY; textSize = textSizeTitle
                        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                    }
                    val paintDate = Paint().apply {
                        color = Color.WHITE; textSize = textSizeDate
                        typeface = Typeface.DEFAULT; isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                    }
                    val paintDecor = Paint().apply {
                        color = Color.WHITE; textSize = textSizeTitle
                        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    val paintSequence = Paint().apply {
                        color = Color.WHITE; textSize = textSizeTitle
                        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
                        textAlign = Paint.Align.RIGHT
                    }

                    canvas.drawText(safeProjet, paddingX, textYTitle, paintProjet)
                    canvas.drawText(currentDate, paddingX, textYDate, paintDate)
                    canvas.drawText("Décor: $decor", finalWidth / 2f, textYCenterRight, paintDecor)
                    canvas.drawText("Seq: $sequence", finalWidth - paddingX, textYCenterRight, paintSequence)

                    // Overwrite the stored JPEG with the bitmap including banner.
                    resolver.openOutputStream(savedUri, "wt")?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    // Release memory
                    bitmap.recycle()

                    // Mark pending = 0 for API Q+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(savedUri, values, null, null)
                    }

                    // Persist metadata to DB
                    photoDao.insert(PhotoEntity(uri = savedUri.toString(), projet = safeProjet, sequence = sequence, decor = decor, date = currentDate))

                    // Notify on main
                    mainExecutor.execute { onPhotoSaved("Sauvegardée dans $safeProjet") }
                } catch (e: Throwable) {
                    Log.e("CameraUtils", "Erreur traitement photo", e)
                    mainExecutor.execute { onPhotoSaved("Erreur lors du traitement") }
                }
            }
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraX", "Erreur capture", exception)
            mainExecutor.execute { onPhotoSaved("Erreur lors de la capture") }
        }
    })
}