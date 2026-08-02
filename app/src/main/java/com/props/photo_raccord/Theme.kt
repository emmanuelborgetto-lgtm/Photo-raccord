package com.props.photo_raccord

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PhotoRaccordTheme(
    context: Context,
    theme: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = com.props.photo_raccord.utils.getColorScheme(theme, context),
        content = content
    )
}
