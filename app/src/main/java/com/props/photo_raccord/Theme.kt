package com.props.photo_raccord

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

// Définition des familles de polices
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_regular, FontWeight.Normal)
)

val DM_Mono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal)
)

@Composable
fun PhotoRaccordTheme(
    context: Context,
    theme: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    // Appliquer les polices directement via MaterialTheme
    MaterialTheme(
        colorScheme = com.props.photo_raccord.utils.getColorScheme(theme, context),
        typography = Typography(
            titleMedium = TextStyle(
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            ),
            bodySmall = TextStyle(
                fontFamily = DM_Mono,
                fontSize = 12.sp
            ),
            labelSmall = TextStyle(
                fontFamily = DM_Mono,
                fontSize = 8.sp
            ),
                    labelLarge = TextStyle(
                    fontFamily = DM_Mono,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        ),
        // Pour les grands titres
        titleLarge = TextStyle(
            fontFamily = BarlowCondensed,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        ),
        // Pour le texte standard
        bodyLarge = TextStyle(
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        )
        ),
        content = content
    )
}