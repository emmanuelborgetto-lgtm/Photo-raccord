/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 */
package com.props.photo_raccord.utils

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Génère un nom de fichier lisible et unique pour une photo, au format :
 *   date-courte_projet_decor_seqXXX.jpg
 * ex : 250911-1437_mon-projet_salon-principal_seq12a.jpg
 *
 * La date est placée en tête pour que les photos se trient chronologiquement par défaut
 * dans n'importe quel gestionnaire de fichiers, même si elles sont un jour extraites de
 * leurs sous-dossiers par projet (ex : partage groupé).
 *
 * [fileExists] doit indiquer si un fichier portant ce nom existe déjà à l'endroit visé ;
 * un suffixe numérique est alors ajouté et incrémenté jusqu'à trouver un nom libre. C'est
 * un cas bien réel ici : reprendre plusieurs fois le même décor/séquence au cours d'un
 * tournage est l'usage normal de l'application, donc deux photos peuvent tomber sur la
 * même minute.
 */
fun buildUniquePhotoFileName(
    projet: String,
    decor: String,
    sequence: String,
    date: Date = Date(),
    fileExists: (String) -> Boolean = { false }
): String {
    val dateShort = SimpleDateFormat("yyMMdd-HHmm", Locale.getDefault()).format(date)
    val baseName = listOf(
        dateShort,
        sanitizeForFileName(projet),
        sanitizeForFileName(decor),
        "seq${sanitizeForFileName(sequence)}"
    ).filter { it.isNotBlank() }.joinToString("_")

    var candidate = "$baseName.jpg"
    var suffix = 2
    while (fileExists(candidate)) {
        candidate = "$baseName-$suffix.jpg"
        suffix++
    }
    return candidate
}

/**
 * Normalise un texte libre (nom de projet, décor, séquence...) pour en faire un composant
 * de nom de fichier sûr : minuscules, sans accents, sans espaces ni caractères interdits
 * sur un système de fichiers (Android comme exFAT/Windows, pour le cas où le dossier serait
 * copié ailleurs).
 */
private fun sanitizeForFileName(input: String): String {
    val withoutAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutAccents
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
}