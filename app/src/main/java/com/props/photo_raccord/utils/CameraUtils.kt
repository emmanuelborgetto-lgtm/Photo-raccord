package com.props.photo_raccord.utils

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
    val safeProjet = projet.ifEmpty { "Projet" }

    imageCapture.takePicture(/* executor = */ cameraExecutor, /* callback = */ object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(imageProxy: ImageProxy) = try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
            val isPortrait = rotation == 90f || rotation == 270f
            val finalWidth = if (isPortrait) bitmap.height else bitmap.width
            val finalHeight = if (isPortrait) bitmap.width else bitmap.height
            val bannerHeight = (finalHeight * 0.08f).toInt()
            val finalBitmap = android.graphics.Bitmap.createBitmap(finalWidth, finalHeight + bannerHeight)
            val canvas = android.graphics.Canvas(finalBitmap)
            canvas.save()
            val matrix = android.graphics.Matrix().apply {
                postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                postRotate(rotation)
                postTranslate(finalWidth / 2f, finalHeight / 2f)
            }
            canvas.drawBitmap(bitmap, matrix, null)
            canvas.restore()
            bitmap.recycle()
            val paddingX = finalWidth * 0.02f
            val textSizeTitle = finalWidth * 0.035f
            val textSizeDate = finalWidth * 0.028f
            val textYTitle = finalHeight + (bannerHeight * 0.45f)
            val textYDate = finalHeight + (bannerHeight * 0.85f)
            val textYCenterRight = finalHeight + (bannerHeight * 0.62f)
            val paintBg = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
            val paintProjet = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY; textSize = textSizeTitle
                typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val paintDate = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE; textSize = textSizeDate
                typeface = android.graphics.Typeface.DEFAULT; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val paintDecor = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE; textSize = textSizeTitle
                typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val paintSequence = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE; textSize = textSizeTitle
                typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            canvas.drawRect(/* left = */ 0f, /* top = */
                finalHeight.toFloat(), /* right = */
                finalWidth.toFloat(), /* bottom = */
                finalBitmap.height.toFloat(), /* paint = */
                paintBg)
            canvas.drawText(safeProjet, paddingX, textYTitle, paintProjet)
            canvas.drawText(currentDate, paddingX, textYDate, paintDate)
            canvas.drawText("Décor: $decor", finalWidth / 2f, textYCenterRight, paintDecor)
            canvas.drawText("Seq: $sequence", finalWidth - paddingX, textYCenterRight, paintSequence)
            val savedUri = saveImageToGallery(context, finalBitmap, safeProjet)
            if (savedUri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    photoDao.insert(PhotoEntity(uri = savedUri, projet = safeProjet, sequence = sequence, decor = decor, date = currentDate))
                }
                mainExecutor.execute { onPhotoSaved("Sauvegardée dans $safeProjet") }
            } else {
                mainExecutor.execute { onPhotoSaved("Erreur lors de la sauvegarde") }
            }
            finalBitmap.recycle
        } catch (e: Throwable) {
            Log.e("CameraX", "Erreur traitement photo", e)
            mainExecutor.execute { onPhotoSaved("Erreur : ${e.message ?: "Inconnue"}") }
        } finally { imageProxy.close() }
        override fun onError(exception: ImageCaptureException) {
            mainExecutor.execute { onPhotoSaved("Erreur lors de la capture") }
        }
    })
}
