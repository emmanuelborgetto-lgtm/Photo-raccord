package com.props.photo_raccord

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Trois palettes personnalisées (Ambre, Violet, Turquoise), qui s'ajoutent à la palette
// "Système" (couleur dynamique Android 12+, ou thème sombre par défaut sinon).
//
// Chaque rôle Material 3 est dérivé de la même teinte pour rester cohérent visuellement
// (boutons actifs et désactivés, séparateurs, fonds d'écran, cartes, menus déroulants,
// boîtes de dialogue, bandeau de notification...). Volontairement désaturées (tons plus
// doux/poussiéreux qu'une version "pleine saturation") pour un rendu plus sobre. Seules
// les couleurs d'erreur restent standard (rouge), pour rester universellement reconnaissables.
//
// "primary" est calibré pour bien fonctionner à la fois comme fond de bouton (texte blanc
// lisible dessus) ET comme couleur de titre d'écran (lisible directement sur le fond sombre).

val AmberColorScheme = darkColorScheme(
    primary = Color(0xFF926C2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF523F1E),
    onPrimaryContainer = Color(0xFFEDE8DE),
    inversePrimary = Color(0xFF926C2A),

    secondary = Color(0xFFCDC9C1),
    onSecondary = Color(0xFF3B352B),
    secondaryContainer = Color(0xFF41331B), // Bandeau d'info des vignettes photo
    onSecondaryContainer = Color(0xFFEDE8DE),

    tertiary = Color(0xFFC6CDC1),
    onTertiary = Color(0xFF323B2B),
    tertiaryContainer = Color(0xFF46533C),
    onTertiaryContainer = Color(0xFFE5EDDE),

    background = Color(0xFF161513),
    onBackground = Color(0xFFE7E6E4),
    surface = Color(0xFF161513),
    onSurface = Color(0xFFE7E6E4), // Sert aussi de base aux boutons désactivés (via l'alpha M3)
    surfaceVariant = Color(0xFF4A443B),
    onSurfaceVariant = Color(0xFFCCC8C2),

    surfaceDim = Color(0xFF131210),
    surfaceBright = Color(0xFF5B5449),
    surfaceContainerLowest = Color(0xFF0E0D0C),
    surfaceContainerLow = Color(0xFF1F1D1A),
    surfaceContainer = Color(0xFF272520),
    surfaceContainerHigh = Color(0xFF36322B),
    surfaceContainerHighest = Color(0xFF453F36),

    outline = Color(0xFF938976),
    outlineVariant = Color(0xFF60594D), // Couleur des HorizontalDivider()

    inverseSurface = Color(0xFFE7E6E4),
    inverseOnSurface = Color(0xFF37342F)
)

val VioletColorScheme = darkColorScheme(
    primary = Color(0xFFAA49CA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF451E52),
    onPrimaryContainer = Color(0xFFE9DEED),
    inversePrimary = Color(0xFFAA49CA),

    secondary = Color(0xFFCAC1CD),
    onSecondary = Color(0xFF372B3B),
    secondaryContainer = Color(0xFF371B41), // Bandeau d'info des vignettes photo
    onSecondaryContainer = Color(0xFFE9DEED),

    tertiary = Color(0xFFCDC1C5),
    onTertiary = Color(0xFF3B2B30),
    tertiaryContainer = Color(0xFF533C43),
    onTertiaryContainer = Color(0xFFEDDEE3),

    background = Color(0xFF151316),
    onBackground = Color(0xFFE6E4E7),
    surface = Color(0xFF151316),
    onSurface = Color(0xFFE6E4E7), // Sert aussi de base aux boutons désactivés (via l'alpha M3)
    surfaceVariant = Color(0xFF463B4A),
    onSurfaceVariant = Color(0xFFC9C2CC),

    surfaceDim = Color(0xFF131013),
    surfaceBright = Color(0xFF56495B),
    surfaceContainerLowest = Color(0xFF0D0C0E),
    surfaceContainerLow = Color(0xFF1D1A1F),
    surfaceContainer = Color(0xFF252027),
    surfaceContainerHigh = Color(0xFF332B36),
    surfaceContainerHighest = Color(0xFF413645),

    outline = Color(0xFF8C7693),
    outlineVariant = Color(0xFF5B4D60), // Couleur des HorizontalDivider()

    inverseSurface = Color(0xFFE6E4E7),
    inverseOnSurface = Color(0xFF352F37)
)

val TurquoiseColorScheme = darkColorScheme(
    primary = Color(0xFF297D8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E4A52),
    onPrimaryContainer = Color(0xFFDEEBED),
    inversePrimary = Color(0xFF297D8E),

    secondary = Color(0xFFC1CBCD),
    onSecondary = Color(0xFF2B393B),
    secondaryContainer = Color(0xFF1B3B41), // Bandeau d'info des vignettes photo
    onSecondaryContainer = Color(0xFFDEEBED),

    tertiary = Color(0xFFC2C1CD),
    onTertiary = Color(0xFF2C2B3B),
    tertiaryContainer = Color(0xFF3E3C53),
    onTertiaryContainer = Color(0xFFDFDEED),

    background = Color(0xFF131516),
    onBackground = Color(0xFFE4E6E7),
    surface = Color(0xFF131516),
    onSurface = Color(0xFFE4E6E7), // Sert aussi de base aux boutons désactivés (via l'alpha M3)
    surfaceVariant = Color(0xFF3B474A),
    onSurfaceVariant = Color(0xFFC2CACC),

    surfaceDim = Color(0xFF101313),
    surfaceBright = Color(0xFF49585B),
    surfaceContainerLowest = Color(0xFF0C0D0E),
    surfaceContainerLow = Color(0xFF1A1E1F),
    surfaceContainer = Color(0xFF202627),
    surfaceContainerHigh = Color(0xFF2B3436),
    surfaceContainerHighest = Color(0xFF364245),

    outline = Color(0xFF768E93),
    outlineVariant = Color(0xFF4D5D60), // Couleur des HorizontalDivider()

    inverseSurface = Color(0xFFE4E6E7),
    inverseOnSurface = Color(0xFF2F3637)
)