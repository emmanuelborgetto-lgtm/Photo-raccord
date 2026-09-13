package com.props.photo_raccord.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.core.content.edit

private const val PREFS_NAME = "photo_raccord_prefs"
private const val PREF_STORAGE_TREE_URI = "storage_tree_uri"
private const val PREF_SHOW_IN_GALLERY = "show_in_gallery"

fun getDefaultPhotoDirectory(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "PhotoRaccord")
fun getDefaultPhotoProjectDirectory(context: Context, projet: String): File = File(getDefaultPhotoDirectory(context), projet)

/**
 * Vérifie que l'application détient toujours une autorisation persistée valide (lecture ET
 * écriture) sur ce dossier SAF.
 *
 * Une préférence "storage_tree_uri" peut exister sans que l'autorisation associée soit
 * encore valable : c'est le cas après une désinstallation/réinstallation si les préférences
 * ont été restaurées par la sauvegarde automatique d'Android (allowBackup), car les
 * autorisations persistées, elles, sont toujours révoquées par le système à la
 * désinstallation et ne sont jamais restaurées par la sauvegarde. Sans cette vérification,
 * l'application tenterait d'écrire dans un dossier dont elle n'a en réalité plus le droit
 * d'accès.
 */
fun hasValidTreePermission(context: Context, treeUriString: String): Boolean {
    val treeUri = treeUriString.toUri()
    return context.contentResolver.persistedUriPermissions.any {
        it.uri == treeUri && it.isReadPermission && it.isWritePermission
    }
}

/** État réel du dossier de stockage personnalisé, au-delà de la simple préférence enregistrée. */
sealed class StorageStatus {
    /** Aucun dossier personnalisé configuré : stockage privé par défaut utilisé. */
    object Default : StorageStatus()
    /** Dossier personnalisé configuré et pleinement accessible. */
    object Ok : StorageStatus()
    /** Autorisation SAF perdue (ex : réinstallation de l'application, voir [hasValidTreePermission]). */
    object PermissionLost : StorageStatus()
    /** Autorisation valide, mais dossier introuvable (supprimé, déplacé, carte SD retirée...). */
    object FolderMissing : StorageStatus()
}

/**
 * Détermine l'état réel du dossier de stockage personnalisé configuré.
 *
 * Va au-delà de [hasValidTreePermission] : une autorisation SAF peut rester techniquement
 * valide alors que le dossier lui-même a été supprimé, déplacé, ou rendu indisponible (carte
 * SD retirée). Cette fonction vérifie donc à la fois l'autorisation ET l'existence physique
 * du dossier, pour permettre de distinguer les deux cas et d'agir en conséquence (repli sur
 * le stockage par défaut, proposition de resélection dans l'interface...).
 */
fun checkStorageAccess(context: Context, treeUriString: String?): StorageStatus {
    if (treeUriString.isNullOrEmpty()) return StorageStatus.Default
    if (!hasValidTreePermission(context, treeUriString)) return StorageStatus.PermissionLost
    return try {
        val dir = DocumentFile.fromTreeUri(context, treeUriString.toUri())
        if (dir != null && dir.exists() && dir.canWrite()) StorageStatus.Ok
        else StorageStatus.FolderMissing
    } catch (e: Exception) {
        StorageStatus.FolderMissing
    }
}

fun ensureDefaultNomedia(context: Context, showInGallery: Boolean) {
    try {
        val directory = getDefaultPhotoDirectory(context)
        val nomedia = File(directory, ".nomedia")
        if (showInGallery) { if (nomedia.exists()) nomedia.delete() }
        else { if (!directory.exists()) directory.mkdirs(); if (!nomedia.exists()) nomedia.createNewFile() }
    } catch (e: Exception) { Log.e("CameraUtils", "Erreur gestion .nomedia du dossier par défaut", e) }
}

// decodeFullResolutionSafely est désormais définie dans FileUtils.kt (même package),
// et partagée entre la capture caméra et l'import de photos.

fun takeAndProcessPhoto(
    context: Context, imageCapture: ImageCapture, cameraExecutor: Executor,
    coroutineScope: CoroutineScope, photoDao: PhotoDao, projet: String,
    sequence: String, decor: String, onPhotoSaved: (String) -> Unit
) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val showInGallery = prefs.getBoolean(PREF_SHOW_IN_GALLERY, false)
    val customTreeUriString = prefs.getString(PREF_STORAGE_TREE_URI, null)
    val captureDate = Date()
    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(captureDate)
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val safeProjet = projet.ifBlank { "Projet" }
    val resolver = context.contentResolver
    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)
                    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) throw Exception("Dimensions d'image invalides")

                    // Résolution maximale du capteur : on tente d'abord un décodage en pleine
                    // résolution. Si la mémoire disponible ne suit pas sur cet appareil précis
                    // (OutOfMemoryError), on réduit progressivement plutôt que de planter.
                    val bitmap = decodeFullResolutionSafely(tempFile.absolutePath)

                    val finalBitmap = createBanneredBitmap(bitmap, safeProjet, currentDate, decor, sequence)
                    var finalUri: Uri? = null
                    var pending = false

                    // Le dossier personnalisé peut être configuré (storage_tree_uri) sans être
                    // réellement utilisable : autorisation SAF perdue OU dossier supprimé/déplacé/
                    // indisponible (voir checkStorageAccess). Dans les deux cas, on retombe sur le
                    // stockage par défaut et on nettoie la préférence obsolète pour que les
                    // Paramètres reflètent la réalité.
                    val storageStatus = checkStorageAccess(context, customTreeUriString)
                    val useCustomTree = storageStatus == StorageStatus.Ok

                    if (storageStatus == StorageStatus.PermissionLost || storageStatus == StorageStatus.FolderMissing) {
                        Log.w("CameraUtils", "Dossier configuré inutilisable ($storageStatus), retour au stockage par défaut")
                        prefs.edit { remove(PREF_STORAGE_TREE_URI) }
                    }

                    if (useCustomTree) {
                        try {
                            val rootDir = DocumentFile.fromTreeUri(context, customTreeUriString!!.toUri())
                            var projectDir = rootDir?.findFile(safeProjet)
                            if (projectDir == null) projectDir = rootDir?.createDirectory(safeProjet)
                            val fileName = buildUniquePhotoFileName(safeProjet, decor, sequence, captureDate) { candidate ->
                                projectDir?.findFile(candidate) != null
                            }
                            finalUri = projectDir?.createFile("image/jpeg", fileName)?.uri
                        } catch (e: Exception) { Log.e("CameraUtils", "Erreur SAF", e) }
                    }

                    if (finalUri == null && !useCustomTree) {
                        val projectDir = getDefaultPhotoProjectDirectory(context, safeProjet)
                        if (!projectDir.exists() && !projectDir.mkdirs()) throw Exception("Impossible de créer le dossier $safeProjet")
                        val fileName = buildUniquePhotoFileName(safeProjet, decor, sequence, captureDate) { candidate ->
                            File(projectDir, candidate).exists()
                        }
                        val outputFile = File(projectDir, fileName)
                        outputFile.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        finalUri = Uri.fromFile(outputFile)
                    }

                    if (finalUri == null) {
                        // MediaStore désambiguïse déjà automatiquement les noms en doublon au
                        // sein d'un même RELATIVE_PATH ; pas besoin d'une vérification manuelle
                        // supplémentaire ici (cas de repli, rarement atteint).
                        val fileName = buildUniquePhotoFileName(safeProjet, decor, sequence, captureDate)
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PhotoRaccord/$safeProjet")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }
                        finalUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        pending = true
                    }

                    if (finalUri == null) throw Exception("Impossible de créer le fichier de destination")
                    if (finalUri.scheme != "file") {
                        resolver.openOutputStream(finalUri)?.use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                            ?: throw Exception("Impossible d'ouvrir le flux de sortie")
                    }
                    if (pending && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        resolver.update(finalUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    }

                    bitmap.recycle()
                    finalBitmap.recycle()
                    photoDao.insert(PhotoEntity(uri = finalUri.toString(), projet = safeProjet, sequence = sequence, decor = decor, date = currentDate))

                    // Écriture des métadonnées EXIF (application, projet, décor, séquence) — best effort,
                    // ne doit jamais faire échouer la sauvegarde de la photo elle-même.
                    writePhotoExif(context, finalUri, safeProjet, sequence, decor)

                    if (!showInGallery) {
                        if (useCustomTree) {
                            try {
                                val rootDir = DocumentFile.fromTreeUri(context, customTreeUriString!!.toUri())
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