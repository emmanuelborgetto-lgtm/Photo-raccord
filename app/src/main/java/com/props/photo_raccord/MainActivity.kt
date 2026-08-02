package com.props.photo_raccord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.props.photo_raccord.screens.CameraScreen
import com.props.photo_raccord.screens.GalleryScreen
import com.props.photo_raccord.screens.ProjectSelectionScreen
import com.props.photo_raccord.screens.Screen
import com.props.photo_raccord.screens.SessionScreen
import com.props.photo_raccord.screens.SettingsScreen
import com.props.photo_raccord.utils.getColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PhotoRaccordApp() }
    }
}

@Composable
fun PhotoRaccordApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photo_raccord_prefs", MODE_PRIVATE) }
    var currentTheme by remember { mutableStateOf(prefs.getString("app_theme", "DEFAULT") ?: "DEFAULT") }

    MaterialTheme(colorScheme = getColorScheme(currentTheme, context)) {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            AppNavigation(currentTheme = currentTheme, onThemeChanged = { newTheme ->
                currentTheme = newTheme
                prefs.edit { putString("app_theme", newTheme) }
            })
        }
    }
}

@Composable
fun AppNavigation(currentTheme: String, onThemeChanged: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE) }
    var projet by remember { mutableStateOf(prefs.getString("last_projet", "") ?: "") }
    var sequence by remember { mutableStateOf("") }
    var decor by remember { mutableStateOf("") }
    var currentScreen by remember { mutableStateOf(if (projet.isNotBlank()) Screen.SESSION else Screen.PROJECT) }

    if (currentScreen != Screen.PROJECT) {
        BackHandler {
            currentScreen = when (currentScreen) {
                Screen.CAMERA, Screen.GALLERY, Screen.SETTINGS -> Screen.SESSION
                Screen.SESSION -> Screen.PROJECT
                else -> Screen.PROJECT
            }
        }
    }

    LaunchedEffect(projet) { prefs.edit { putString("last_projet", projet) } }

    when (currentScreen) {
        Screen.PROJECT -> ProjectSelectionScreen(currentProjet = projet, onProjectSelected = { selectedProjet -> projet = selectedProjet; currentScreen = Screen.SESSION })
        Screen.SESSION -> SessionScreen(projet = projet, sequence = sequence, decor = decor, onSequenceChange = { sequence = it }, onDecorChange = { decor = it },
            onStartCamera = { currentScreen = Screen.CAMERA }, onOpenGallery = { currentScreen = Screen.GALLERY }, onOpenSettings = { currentScreen = Screen.SETTINGS }, onBackToProjects = { currentScreen = Screen.PROJECT })
        Screen.CAMERA -> CameraScreen(projet = projet, sequence = sequence, decor = decor, onClose = { currentScreen = Screen.SESSION })
        Screen.GALLERY -> GalleryScreen(projet = projet, onClose = { currentScreen = Screen.SESSION })
        Screen.SETTINGS -> SettingsScreen(currentTheme = currentTheme, onThemeChanged = onThemeChanged,
            onProjetRenamed = { oldName, newName -> if (projet == oldName) projet = newName },
            onProjetDeleted = { deletedProjet -> if (projet == deletedProjet) { projet = ""; currentScreen = Screen.PROJECT } },
            onClose = { currentScreen = Screen.SESSION })
    }
}
