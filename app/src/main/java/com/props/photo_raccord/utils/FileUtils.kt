package com.props.photo_raccord.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.createBitmap
import androidx.core.content.edit
import com.props.photo_raccord.PhotoEntity

/**
 * Décode une image en tentant d'abord sa pleine résolution d'origine — que ce soit une
 * photo prise par l'appareil (résolution max du capteur) ou un fichier importé (résolution
 * d'origine du fichier). Aucune réduction n'est appliquée par défaut. Si la mémoire
 * disponible ne suit pas sur cet appareil précis (OutOfMemoryError), réduit progressivement
 * plutôt que de faire planter l'application.
 *
 * Applique aussi la rotation indiquée par le tag EXIF Orientation du fichier source (voir
 * [applyExifRotation]) avant de retourner le bitmap, pour que le bandeau — toujours dessiné
 * après cet appel par les fonctions appelantes — soit posé sur une image déjà correctement
 * orientée.
 */
fun decodeFullResolutionSafely(path: String): Bitmap {
    var sampleSize = 1
    var lastError: OutOfMemoryError? = null
    repeat(4) {
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(path, options)
                ?: throw IllegalArgumentException("Échec du décodage du bitmap")
            return applyExifRotation(bitmap, path)
        } catch (e: OutOfMemoryError) {
            lastError = e
            System.gc()
            sampleSize *= 2
        }
    }
    throw lastError ?: IllegalArgumentException("Mémoire insuffisante pour traiter cette image")
}

/**
 * Applique la rotation indiquée par le tag EXIF Orientation du fichier source.
 *
 * BitmapFactory ignore ce tag lors du décodage : sans cette étape, une photo prise portrait
 * (CameraX n'écrit que le tag EXIF, il ne pivote pas les pixels), ou importée depuis un autre
 * téléphone ou un appareil photo qui fait de même, ressortirait couchée — avec le bandeau
 * posé du mauvais côté puisqu'il est dessiné après coup sur les pixels tels que décodés.
 *
 * Ne fait rien (retourne le bitmap tel quel, sans allocation supplémentaire) si aucune
 * rotation n'est nécessaire — le cas le plus courant (paysage, ou source déjà correctement
 * orientée).
 */
private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap {
    val rotation = try {
        ExifInterface(path).rotationDegrees
    } catch (_: Exception) {
        0
    }
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

/** Creates a new bitmap with the original photo untouched and the banner appended below it. */
fun createBanneredBitmap(
    source: Bitmap,
    projet: String,
    date: String,
    decor: String,
    sequence: String
): Bitmap {
    val bannerHeight = (source.height * 0.08f).toInt().coerceAtLeast(1)
    val result = createBitmap(source.width, source.height + bannerHeight)
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

        // Mise à jour des métadonnées EXIF avec les nouvelles valeurs de séquence/décor,
        // pour rester cohérent avec le bandeau visible sur la photo.
        writePhotoExif(context, uri, projet, newSequence, newDecor)

        photoOnly.recycle()
        updatedBitmap.recycle()
        sourceBitmap.recycle()
    } catch (e: Exception) {
        Log.e("FileUtils", "Erreur bandeau", e)
    }
}

/**
 * Decodes an image selected through Android's document picker.
 *
 * Some document providers return streams that cannot reliably be decoded twice
 * with BitmapFactory (for example cloud/document-provider implementations).
 * We therefore copy the selected document to a temporary local file first and
 * perform both the bounds read and the actual decode from that same file.
 *
 * La résolution d'origine du fichier importé est conservée telle quelle (aucune
 * réduction n'est appliquée) ; seul un manque de mémoire réel sur l'appareil
 * déclenche un repli progressif via [decodeFullResolutionSafely].
 */
private fun decodeSelectedImage(
    context: Context,
    sourceUri: Uri
): Bitmap {
    val tempFile = File.createTempFile("photo_import_", ".img", context.cacheDir)

    try {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Impossible d'ouvrir l'image sélectionnée")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Image invalide ou format non pris en charge")
        }

        return decodeFullResolutionSafely(tempFile.absolutePath)
    } finally {
        tempFile.delete()
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
    val importDate = Date()
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(importDate)
    val safeProjet = projet.ifBlank { "Projet" }

    // Le dossier personnalisé peut être configuré (storage_tree_uri) sans être réellement
    // utilisable : autorisation SAF perdue OU dossier supprimé/déplacé/indisponible (voir
    // checkStorageAccess). Auparavant, seule la permission était vérifiée : un dossier
    // physiquement supprimé mais avec une autorisation encore valide faisait planter
    // l'import avec une erreur brute au lieu de basculer sur le stockage par défaut.
    val storageStatus = checkStorageAccess(context, customTreeUriString)
    val useCustomTree = storageStatus == StorageStatus.Ok

    if (storageStatus == StorageStatus.PermissionLost || storageStatus == StorageStatus.FolderMissing) {
        Log.w("FileUtils", "Dossier configuré inutilisable ($storageStatus), retour au stockage par défaut")
        prefs.edit { remove("storage_tree_uri") }
    }

    // Decode from a local temporary copy rather than directly from the
    // document-provider stream. This fixes imports from providers for which
    // BitmapFactory cannot reliably decode the selected URI.
    val bitmap = decodeSelectedImage(context, sourceUri)
    val finalBitmap = createBanneredBitmap(bitmap, safeProjet, date, decor, sequence)
    var finalUri: Uri?

    try {
        if (useCustomTree) {
            val rootDir = DocumentFile.fromTreeUri(context, customTreeUriString!!.toUri())
                ?: throw IllegalArgumentException("Dossier de stockage inaccessible")
            var projectDir = rootDir.findFile(safeProjet)
            if (projectDir == null) projectDir = rootDir.createDirectory(safeProjet)
            val fileName = buildUniquePhotoFileName(safeProjet, decor, sequence, importDate) { candidate ->
                projectDir?.findFile(candidate) != null
            }
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
            val fileName = buildUniquePhotoFileName(safeProjet, decor, sequence, importDate) { candidate ->
                File(projectDir, candidate).exists()
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

    val savedUri = finalUri
        ?: throw IllegalArgumentException("Impossible de créer la photo importée")

    // Écriture des métadonnées EXIF (application, projet, décor, séquence) — best effort.
    writePhotoExif(context, savedUri, safeProjet, sequence, decor)

    return savedUri.toString() to date
}

fun deletePhotoFile(context: Context, photo: PhotoEntity) {
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