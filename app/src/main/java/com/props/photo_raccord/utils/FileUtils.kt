package com.props.photo_raccord.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun saveImageToGallery(context: Context, bitmap: android.graphics.Bitmap, projet: String): String? {
    val safeProjet = projet.ifEmpty { "Projet" }
    val prefs = context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE)
    val customTreeUriString = prefs.getString("storage_tree_uri", null)

    if (!customTreeUriString.isNullOrEmpty()) {
        try {
            val treeUri = customTreeUriString.toUri()
            val resolver = context.contentResolver
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            var projectFolderUri: Uri? = null

            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    if (name.equals(safeProjet, ignoreCase = true) && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        projectFolderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                        break
                    }
                }
            }

            if (projectFolderUri == null) {
                projectFolderUri = DocumentsContract.createDocument(resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, safeProjet)
            }

            val targetFolderUri = projectFolderUri ?: rootUri
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val imageUri = DocumentsContract.createDocument(resolver, targetFolderUri, "image/jpeg", fileName)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out) }
                return imageUri.toString()
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Erreur SAF", e)
        }
    }

    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/$safeProjet")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    return if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            imageUri.toString()
        } catch (_: Exception) { null }
    } else null
}

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
        val photoOnly = android.graphics.Bitmap.createBitmap(sourceBitmap, 0, 0, totalWidth, photoHeight)
        val updatedBitmap = android.graphics.Bitmap.createBitmap(totalWidth, totalHeight)
        val canvas = android.graphics.Canvas(updatedBitmap)
        canvas.drawBitmap(photoOnly, 0f, 0f, null)
        val paddingX = totalWidth * 0.02f
        val textSizeTitle = totalWidth * 0.035f
        val textSizeDate = totalWidth * 0.028f
        val textYTitle = photoHeight + (bannerHeight * 0.45f)
        val textYDate = photoHeight + (bannerHeight * 0.85f)
        val textYCenterRight = photoHeight + (bannerHeight * 0.62f)
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
        canvas.drawRect(0f, photoHeight.toFloat(), totalWidth.toFloat(), totalHeight.toFloat(), paintBg)
        canvas.drawText(projet, paddingX, textYTitle, paintProjet)
        canvas.drawText(date, paddingX, textYDate, paintDate)
        canvas.drawText("Décor: $newDecor", totalWidth / 2f, textYCenterRight, paintDecor)
        canvas.drawText("Seq: $newSequence", totalWidth - paddingX, textYCenterRight, paintSequence)
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
