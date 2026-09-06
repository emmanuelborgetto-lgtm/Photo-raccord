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

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color

// Schéma de base imposant le fond noir et corrigeant le gris par défaut
val BaseDarkColorScheme = darkColorScheme(
    background = Color.Black,
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E), // Par défaut pour le thème SYSTEM
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White
)

val KakiColorScheme = BaseDarkColorScheme.copy(
    primary = Color(0xFFD66B37),
    secondary = Color(0xFF4B2E20),
    tertiary = Color(0xFFE8D8B7),
    primaryContainer = Color(0xFF4B2E20),
    onPrimaryContainer = Color(0xFFE8D8B7),
    surfaceVariant = Color(0xFF4B2E20) // Fond des cartes : Burnt brown (Marron foncé)
)

val FujiColorScheme = BaseDarkColorScheme.copy(
    primary = Color(0xFFB5A6C9),          // Lavande clair
    secondary = Color(0xFFD8C2E5),        // Violet pastel secondaire
    tertiary = Color(0xFFF4EEE0),         // Blanc cassé
    primaryContainer = Color(0xFF423354), // Violet sombre pour les boutons et bandeaux
    onPrimaryContainer = Color(0xFFF4EEE0),// Texte sur fond violet
    surfaceVariant = Color(0xFF2D2338)    // Violet très sombre pour le fond des cartes
)

val HanadaColorScheme = BaseDarkColorScheme.copy(
    primary = Color(0xFF3C6E8F), // Hanada (Indigo blue)
    secondary = Color(0xFFBCBCBE), // Gin (Silver)
    tertiary = Color(0xFFF8F4E9), // Kinari (Natural white)
    primaryContainer = Color(0xFF3C6E8F), // Fond des boutons et bandeaux
    onPrimaryContainer = Color(0xFFF8F4E9), // Texte sur fond bleu
    surfaceVariant = Color(0xFF1E3747) // Variante sombre pour le fond des cartes
)

fun getColorScheme(theme: String, context: Context): ColorScheme = when (theme) {
    "KAKI" -> KakiColorScheme
    "FUJI" -> FujiColorScheme
    "HANADA" -> HanadaColorScheme
    else -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context).copy(
                background = Color.Black,
                surface = Color.Black
            )
        } else {
            BaseDarkColorScheme
        }
    }
}