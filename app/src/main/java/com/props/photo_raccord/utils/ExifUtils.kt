package com.props.photo_raccord.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface

/**
 * Écrit dans les métadonnées EXIF du fichier JPEG désigné par [uri] :
 * - le logiciel ayant produit la photo (TAG_SOFTWARE)
 * - une description lisible combinant projet / décor / séquence (TAG_IMAGE_DESCRIPTION),
 *   affichée par la plupart des visionneuses de photos sous "Description"
 * - une version structurée clé=valeur (TAG_USER_COMMENT), ré-exploitable par du code
 *
 * Échoue silencieusement (avec un log) en cas de problème d'accès au fichier : l'écriture
 * EXIF est une amélioration annexe, elle ne doit jamais faire échouer la sauvegarde de la
 * photo elle-même ni la mise à jour de ses informations.
 */
fun writePhotoExif(context: Context, uri: Uri, projet: String, sequence: String, decor: String) {
    try {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Photo-raccord")
            exif.setAttribute(
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                "Projet: $projet - Decor: $decor - Sequence: $sequence"
            )
            exif.setAttribute(
                ExifInterface.TAG_USER_COMMENT,
                "app=Photo-raccord;projet=$projet;decor=$decor;sequence=$sequence"
            )
            exif.saveAttributes()
        }
    } catch (e: Exception) {
        Log.e("ExifUtils", "Écriture des métadonnées EXIF impossible pour $uri", e)
    }
}