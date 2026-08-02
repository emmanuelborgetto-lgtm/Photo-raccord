package com.props.photo_raccord

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider

import java.text.SimpleDateFormat
import java.util.Date

// Plein écran
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

// Swipe en plein écran
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged

// Tri et recherche (icones)
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import java.util.Locale

// Listes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType

// Settings
import android.net.Uri
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.MoreVert

import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

// File explo
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// Icones
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border

import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn

import androidx.compose.material.icons.filled.Share

// Photo
import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector


// Calculs en fond
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("photo_raccord_prefs", MODE_PRIVATE) }

            // Récupère le thème sauvegardé (par défaut "DEFAULT")
            var currentTheme by remember { mutableStateOf(prefs.getString("app_theme", "DEFAULT") ?: "DEFAULT") }

            val isDarkTheme = isSystemInDarkTheme()
            val colors = when (currentTheme) {
                "AMBER" -> AmberColorScheme
                "VIOLET" -> VioletColorScheme
                "TURQUOISE" -> TurquoiseColorScheme
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (isDarkTheme) darkColorScheme() else lightColorScheme()
                }
            }

            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        currentTheme = currentTheme,
                        onThemeChanged = { newTheme ->
                            currentTheme = newTheme
                            prefs.edit { putString("app_theme", newTheme) }
                        }
                    )
                }
            }
        }
    }
}

enum class Screen {
    PROJECT, SESSION, CAMERA, GALLERY, SETTINGS
}

@Composable
fun AppNavigation(currentTheme: String, onThemeChanged: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE) }

    var projet by remember { mutableStateOf(prefs.getString("last_projet", "") ?: "") }
    var sequence by remember { mutableStateOf("") }
    var decor by remember { mutableStateOf("") }

    // Ouverture directe sur SESSION si un projet est déjà sauvegardé, sinon sur PROJECT
    var currentScreen by remember {
        mutableStateOf(if (projet.isNotBlank()) Screen.SESSION else Screen.PROJECT)
    }

    // Gestion de la touche retour Android
    if (currentScreen != Screen.PROJECT) {
        BackHandler {
            currentScreen = when (currentScreen) {
                Screen.CAMERA, Screen.GALLERY, Screen.SETTINGS -> Screen.SESSION
                Screen.SESSION -> Screen.PROJECT
                else -> Screen.PROJECT
            }
        }
    }

    LaunchedEffect(projet) {
        prefs.edit { putString("last_projet", projet) }
    }

    when (currentScreen) {
        Screen.PROJECT -> {
            ProjectSelectionScreen(
                currentProjet = projet,
                onProjectSelected = { selectedProjet ->
                    projet = selectedProjet
                    currentScreen = Screen.SESSION
                }
            )
        }
        Screen.SESSION -> {
            SessionScreen(
                projet = projet, sequence = sequence, decor = decor,
                onSequenceChange = { sequence = it }, onDecorChange = { decor = it },
                onStartCamera = { currentScreen = Screen.CAMERA },
                onOpenGallery = { currentScreen = Screen.GALLERY },
                onOpenSettings = { currentScreen = Screen.SETTINGS },
                onBackToProjects = { currentScreen = Screen.PROJECT }
            )
        }
        Screen.CAMERA -> {
            CameraScreen(
                projet = projet, sequence = sequence, decor = decor,
                onClose = { currentScreen = Screen.SESSION }
            )
        }
        Screen.GALLERY -> {
            GalleryScreen(
                projet = projet,
                onClose = { currentScreen = Screen.SESSION }
            )
        }
        Screen.SETTINGS -> {
            SettingsScreen(
                currentTheme = currentTheme,
                onThemeChanged = onThemeChanged,
                onProjetRenamed = { oldName, newName ->
                    if (projet == oldName) projet = newName
                },
                onProjetDeleted = { deletedProjet ->
                    if (projet == deletedProjet) {
                        projet = ""
                        currentScreen = Screen.PROJECT
                    }
                },
                onClose = { currentScreen = Screen.SESSION }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSelectionScreen(
    currentProjet: String,
    onProjectSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val projetsExistants by photoDao.getDistinctProjets().collectAsState(initial = emptyList())

    var inputProjet by remember { mutableStateOf(currentProjet) }
    var expandedProjet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Photo-raccord",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sélectionner ou créer un projet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        // Le champ ne sert plus qu'à saisir un NOUVEAU nom de projet : il ne filtre plus,
        // et ne s'ouvre plus tout seul pendant la frappe. La liste des projets déjà
        // enregistrés reste consultable en dessous, via la flèche du menu déroulant.
        ExposedDropdownMenuBox(
            expanded = expandedProjet && projetsExistants.isNotEmpty(),
            onExpandedChange = { expandedProjet = it }
        ) {
            OutlinedTextField(
                value = inputProjet,
                onValueChange = { newValue -> inputProjet = newValue },
                label = { Text("Nom du Projet / Film") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputProjet.isNotEmpty()) {
                            IconButton(onClick = { inputProjet = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer")
                            }
                        }
                        if (projetsExistants.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProjet)
                        }
                    }
                }
            )
            ExposedDropdownMenu(
                expanded = expandedProjet && projetsExistants.isNotEmpty(),
                onDismissRequest = { expandedProjet = false }
            ) {
                Text(
                    text = "Projets existants",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                projetsExistants.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            inputProjet = item
                            expandedProjet = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                if (inputProjet.isNotBlank()) {
                    onProjectSelected(inputProjet.trim())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = inputProjet.isNotBlank()
        ) {
            Text("Valider")
        }
        // --- AJOUT : Pousse le contenu suivant vers le bas ---
        Spacer(modifier = Modifier.weight(1f))

        // --- AJOUT : Version de l'application ---
        Text(
            text = "Photo-raccord v0.1 - 2026",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    projet: String, sequence: String, decor: String,
    onSequenceChange: (String) -> Unit, onDecorChange: (String) -> Unit,
    onStartCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToProjects: () -> Unit
) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }

    val decorsExistants by photoDao.getDistinctDecorsParProjet(projet).collectAsState(initial = emptyList())

    var expandedDecor by remember { mutableStateOf(false) }
    // Affiche tous les décors existants (pas seulement ceux qui correspondent au texte
    // tapé) ; ceux qui correspondent remontent en tête de liste grâce au tri stable.
    val sortedDecors = remember(decor, decorsExistants) {
        decorsExistants.sortedByDescending { it.contains(decor, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête : Flèche retour à gauche, nom du projet et bouton Paramètres à droite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBackToProjects) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Changer de projet")
                }
                Column {
                    Text(
                        text = projet,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Paramètres")
            }
        }

        HorizontalDivider()

        // Champ Séquence
        OutlinedTextField(
            value = sequence,
            onValueChange = onSequenceChange,
            label = { Text("Séquence") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (sequence.isNotEmpty()) {
                    IconButton(onClick = { onSequenceChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer la séquence")
                    }
                }
            }
        )

        // Champ Décor
        ExposedDropdownMenuBox(
            expanded = expandedDecor && sortedDecors.isNotEmpty(),
            onExpandedChange = { expandedDecor = it }
        ) {
            OutlinedTextField(
                value = decor,
                onValueChange = { newValue ->
                    onDecorChange(newValue)
                    expandedDecor = true
                },
                label = { Text("Décor") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (decor.isNotEmpty()) {
                            IconButton(onClick = { onDecorChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer le décor")
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDecor)
                    }
                }
            )
            ExposedDropdownMenu(
                expanded = expandedDecor && sortedDecors.isNotEmpty(),
                onDismissRequest = { expandedDecor = false }
            ) {
                sortedDecors.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            onDecorChange(item)
                            expandedDecor = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = onStartCamera,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = sequence.isNotBlank() && decor.isNotBlank()
        ) {
            Text("Ouvrir l'appareil photo")
        }

        OutlinedButton(
            onClick = onOpenGallery,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voir la galerie du projet")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(projet: String, sequence: String, decor: String, onClose: () -> Unit) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notificationMessage) {
        if (notificationMessage != null) {
            kotlinx.coroutines.delay(2000.milliseconds)
            notificationMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(
                projet = projet,
                sequence = sequence,
                decor = decor,
                onClose = onClose,
                onPhotoSaved = { notificationMessage = it }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("L'application a besoin d'accéder à l'appareil photo.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) { Text("Autoriser la caméra") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text("Annuler") }
            }
        }

        notificationMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .safeDrawingPadding(),
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    projet: String,
    sequence: String,
    decor: String,
    onClose: () -> Unit,
    onPhotoSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // NOUVEAU : État du flash (désactivé par défaut)
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val coroutineScope = rememberCoroutineScope()

    // Thread dédié au traitement des photos (rotation, bandeau, écriture disque).
    // Tout ce travail se faisait auparavant sur le thread principal (executor de takePicture),
    // ce qui figeait l'aperçu caméra le temps de chaque capture. Il est maintenant exécuté ici,
    // en arrière-plan, pour que l'UI reste fluide et réactive pendant la prise de vue.
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(5f) }



    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = rotation
            }
        }
        orientationEventListener.enable()
        onDispose { orientationEventListener.disable() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        camera?.let { cam ->
                            val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                            val target = if (current > 1.2f) 1f else 2.5f.coerceAtMost(maxZoom)
                            cam.cameraControl.setZoomRatio(target)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    camera?.let { cam ->
                        val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        val newZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                        cam.cameraControl.setZoomRatio(newZoom)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                        camera = boundCamera

                        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                            minZoom = state.minZoomRatio
                            maxZoom = state.maxZoomRatio
                        }
                    } catch (exc: Exception) {
                        Log.e("CameraX", "Échec", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val buttonColor = if (isPressed) Color.Red else Color.White

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp)
                .safeDrawingPadding()
        ) {
            // BOUTON RETOUR EXISTANT
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text("Retour", color = Color.White)
            }

            // DÉCLENCHEUR EXISTANT
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
                    .background(buttonColor, shape = CircleShape)
                    .border(4.dp, Color.LightGray, CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            takeAndProcessPhoto(context, imageCapture, cameraExecutor, coroutineScope, photoDao, projet, sequence, decor, onPhotoSaved)
                        }
                    )
            )

            // NOUVEAU : BOUTON FLASH À DROITE
            IconButton(
                onClick = {
                    // Basculer entre OFF -> ON -> AUTO -> OFF
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    // Appliquer le réglage à CameraX
                    imageCapture.flashMode = flashMode
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Changer le mode de flash",
                    tint = Color.White
                )
            }
        }
    }
}

// Galerie du projet
@Composable
fun GalleryScreen(projet: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }

    val factory: ViewModelProvider.Factory = remember { GalleryViewModelFactory(photoDao) }
    val viewModel: GalleryViewModel = viewModel(factory = factory)

    LaunchedEffect(projet) {
        viewModel.initProjet(projet)
    }

    val photos by viewModel.photos.collectAsState()
    val decorsExistants by viewModel.decorsExistants.collectAsState()
    val filteredSortedIndexedPhotos by viewModel.filteredSortedIndexedPhotos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    val scope = rememberCoroutineScope()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = projet,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onClose) {
                    Text("Retour")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    label = { Text("Recherche") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Icône de recherche") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer")
                            }
                        }
                    }
                )

                Box {
                    OutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(sortOption.displayName)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Menu de tri")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    viewModel.sortOption.value = option
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredSortedIndexedPhotos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = if (photos.isEmpty()) {
                        "Aucune photo enregistrée pour ce projet."
                    } else {
                        "Aucune photo ne correspond à votre recherche."
                    }
                    Text(message)
                }
            } else {
                // Mémorisé : évite de regrouper toute la liste à chaque recomposition
                // (par ex. à l'ouverture/fermeture de la visionneuse plein écran), le
                // regroupement n'est refait que si les photos ou le tri changent réellement.
                val groupedPhotosWithIndex = remember(filteredSortedIndexedPhotos, sortOption) {
                    filteredSortedIndexedPhotos.groupBy { (_, photo) ->
                        when (sortOption) {
                            SortOption.DATE_DESC, SortOption.DATE_ASC -> "Date : ${photo.date.substringBefore(" ")}"
                            SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> "Séquence ${photo.sequence}"
                            SortOption.DECOR_ASC, SortOption.DECOR_DESC -> "Décor : ${photo.decor}"
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedPhotosWithIndex.forEach { (header, indexedPhotosInGroup) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp)
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        items(
                            count = indexedPhotosInGroup.size,
                            key = { index -> indexedPhotosInGroup[index].second.id },
                            contentType = { "photo_card" } // Indique à Compose de recycler ces éléments ensemble
                        ) { index ->
                            val globalIndex = indexedPhotosInGroup[index].first
                            val photo = indexedPhotosInGroup[index].second

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clickable { selectedPhotoIndex = globalIndex }
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(photo.uri)
                                            .size(300)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Raccord ${photo.sequence}",
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentScale = ContentScale.Crop
                                    )

                                    val infoText = when (sortOption) {
                                        SortOption.DATE_DESC, SortOption.DATE_ASC -> "Seq : ${photo.sequence} | ${photo.decor}"
                                        SortOption.SEQUENCE_ASC, SortOption.SEQUENCE_DESC -> "Décor : ${photo.decor}"
                                        SortOption.DECOR_ASC, SortOption.DECOR_DESC -> "Seq : ${photo.sequence}"
                                    }

                                    Text(
                                        text = infoText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer, // Assure le contraste du texte
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.secondaryContainer) // Fond coloré mais peu saturé
                                            .padding(8.dp), // Padding légèrement augmenté pour aérer le bandeau
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedPhotoIndex?.let { index ->
            if (filteredSortedIndexedPhotos.isEmpty()) {
                selectedPhotoIndex = null
            } else {
                val safeIndex = index.coerceAtMost(filteredSortedIndexedPhotos.size - 1)
                FullScreenImageDialog(
                    photos = filteredSortedIndexedPhotos.map { it.second }, // Extraction de la liste des photos
                    initialIndex = safeIndex,
                    existingDecors = decorsExistants,
                    onUpdatePhoto = { updatedPhoto ->
                        scope.launch(Dispatchers.IO) {
                            updatePhotoBanner(
                                context = context,
                                photoUriString = updatedPhoto.uri,
                                projet = updatedPhoto.projet,
                                newSequence = updatedPhoto.sequence,
                                newDecor = updatedPhoto.decor,
                                date = updatedPhoto.date
                            )
                            photoDao.update(updatedPhoto)
                        }
                    },
                    onDeletePhoto = { photoToDelete ->
                        scope.launch(Dispatchers.IO) {
                            val uri = photoToDelete.uri.toUri()
                            try {
                                if (DocumentsContract.isDocumentUri(context, uri)) {
                                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                                } else {
                                    context.contentResolver.delete(uri, null, null)
                                }
                            } catch (e: Exception) {
                                Log.e("GalleryScreen", "Erreur lors de la suppression du fichier", e)
                            }
                            photoDao.deletePhotos(listOf(photoToDelete))
                        }
                    },
                    onDismiss = { selectedPhotoIndex = null }
                )
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(
    photos: List<PhotoEntity>,
    initialIndex: Int,
    existingDecors: List<String>,
    onUpdatePhoto: (PhotoEntity) -> Unit,
    onDeletePhoto: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current // <-- Récupération du contexte
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    var isZoomedIn by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(photos.size) {
        if (photos.isEmpty()) {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isZoomedIn,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page < photos.size) {
                    val photo = photos[page]
                    ZoomableImage(
                        photo = photo,
                        isCurrentPage = page == pagerState.currentPage,
                        onZoomChanged = { zoomed ->
                            if (page == pagerState.currentPage) {
                                isZoomedIn = zoomed
                            }
                        }
                    )
                }
            }
            // Actions de la visionneuse : bouton Retour en haut à gauche, menu d'actions
            // en haut à droite.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .safeDrawingPadding() // Ajouté pour éviter le chevauchement avec la barre d'état (notch/caméra)
            ) {
                var fabExpanded by remember { mutableStateOf(false) }

                // Calque invisible pour fermer le menu au tap extérieur
                if (fabExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { fabExpanded = false } }
                    )
                }

                // Bouton Retour
                FloatingActionButton(
                    onClick = { onDismiss() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Fermer"
                    )
                }

                // Menu d'actions : Éditer / Partager / Supprimer
                Box(
                    modifier = Modifier.align(Alignment.TopEnd),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Bouton principal en premier (se positionne en haut)
                        FloatingActionButton(
                            onClick = { fabExpanded = !fabExpanded },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                                contentDescription = "Menu d'actions"
                            )
                        }

                        // 2. Menu en second (se déploie vers le bas)
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Éditer
                                FabMenuItem(
                                    text = "Éditer",
                                    icon = Icons.Default.Edit,
                                    onClick = {
                                        fabExpanded = false
                                        showEditDialog = true
                                    }
                                )

                                // Partager
                                FabMenuItem(
                                    text = "Partager",
                                    icon = Icons.Default.Share,
                                    onClick = {
                                        fabExpanded = false
                                        if (pagerState.currentPage < photos.size) {
                                            val currentPhoto = photos[pagerState.currentPage]
                                            val uri = currentPhoto.uri.toUri()
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/jpeg"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Partager le raccord"))
                                        }
                                    }
                                )

                                // Supprimer
                                FabMenuItem(
                                    text = "Supprimer",
                                    icon = Icons.Default.Delete,
                                    isDestructive = true,
                                    onClick = {
                                        fabExpanded = false
                                        if (pagerState.currentPage < photos.size) {
                                            val currentPhoto = photos[pagerState.currentPage]
                                            onDeletePhoto(currentPhoto)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue d'édition de la photo active
    if (showEditDialog && pagerState.currentPage < photos.size) {
        val currentPhoto = photos[pagerState.currentPage]
        EditPhotoDialog(
            photo = currentPhoto,
            existingDecors = existingDecors,
            onConfirm = { newSeq, newDecor ->
                showEditDialog = false
                onUpdatePhoto(currentPhoto.copy(sequence = newSeq, decor = newDecor))
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoDialog(
    photo: PhotoEntity,
    existingDecors: List<String>,
    onConfirm: (newSequence: String, newDecor: String) -> Unit,
    onDismiss: () -> Unit
) {
    var sequence by remember { mutableStateOf(photo.sequence) }
    var decor by remember { mutableStateOf(photo.decor) }
    var expandedDecor by remember { mutableStateOf(false) }

    val filteredDecors = remember(decor, existingDecors) {
        existingDecors.filter { it.contains(decor, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Éditer les informations") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sequence,
                    onValueChange = { sequence = it },
                    label = { Text("Séquence") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedDecor && filteredDecors.isNotEmpty(),
                    onExpandedChange = { isExpanded -> expandedDecor = isExpanded }
                ) {
                    OutlinedTextField(
                        value = decor,
                        onValueChange = { newValue ->
                            decor = newValue
                            expandedDecor = true
                        },
                        label = { Text("Décor") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDecor)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDecor && filteredDecors.isNotEmpty(),
                        onDismissRequest = { expandedDecor = false }
                    ) {
                        filteredDecors.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    decor = item
                                    expandedDecor = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(sequence, decor) },
                enabled = sequence.isNotBlank() && decor.isNotBlank()
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun ZoomableImage(
    photo: PhotoEntity,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    fun clampOffset(proposedOffset: Offset, currentScale: Float, size: IntSize): Offset {
        if (currentScale <= 1f || size.width == 0 || size.height == 0) return Offset.Zero

        val maxX = (size.width * (currentScale - 1f)) / 2f
        val maxY = (size.height * (currentScale - 1f)) / 2f

        return Offset(
            x = proposedOffset.x.coerceIn(-maxX, maxX),
            y = proposedOffset.y.coerceIn(-maxY, maxY)
        )
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(photo.uri)
            .memoryCacheKey("${photo.uri}_${photo.sequence}_${photo.decor}")
            .diskCacheKey("${photo.uri}_${photo.sequence}_${photo.decor}")
            .build(),
        contentDescription = "Photo plein écran",
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                // Gestion du double-clic
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            val targetScale = 5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f

                            val targetOffset = Offset(
                                x = (centerX - tapOffset.x) * (targetScale - 1f),
                                y = (centerY - tapOffset.y) * (targetScale - 1f)
                            )
                            scale = targetScale
                            offset = clampOffset(targetOffset, targetScale, containerSize)
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // Gestion personnalisée du zoom et du déplacement
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        // Si l'image n'est pas zoomée et qu'il n'y a qu'un seul doigt,
                        // on ne consomme pas l'événement pour laisser HorizontalPager gérer le swipe.
                        if (scale <= 1f && pointerCount == 1) {
                            continue
                        }

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            val newOffset = if (newScale > 1f) offset + panChange else Offset.Zero

                            scale = newScale
                            offset = clampOffset(newOffset, newScale, containerSize)
                            onZoomChanged(newScale > 1f)

                            // Consomme le geste uniquement si l'image est zoomée ou en cours de pincement
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentScale = ContentScale.Fit
    )
}

private fun takeAndProcessPhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: Executor,
    coroutineScope: CoroutineScope,
    photoDao: PhotoDao,
    projet: String, sequence: String, decor: String,
    onPhotoSaved: (String) -> Unit
) {
    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    val mainExecutor = ContextCompat.getMainExecutor(context)
    // Nom de secours si le projet est vide : utilisé partout ci-dessous (bandeau, dossier
    // de sauvegarde, entrée en base, message de confirmation) pour que le fichier et la
    // fiche en base pointent toujours vers le même nom de projet.
    val safeProjet = projet.ifEmpty { "Projet" }

    imageCapture.takePicture(
        // Le callback s'exécute maintenant sur cameraExecutor (thread d'arrière-plan) au lieu
        // du thread principal : rotation du bitmap, dessin du bandeau et écriture disque ne
        // bloquent plus l'UI ni l'aperçu caméra.
        cameraExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UseKtx")
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    val bitmap = imageProxy.toBitmap()

                    val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                    val isPortrait = rotation == 90f || rotation == 270f
                    val finalWidth = if (isPortrait) bitmap.height else bitmap.width
                    val finalHeight = if (isPortrait) bitmap.width else bitmap.height

                    val bannerHeight = (finalHeight * 0.08f).toInt()

                    val finalBitmap = createBitmap(finalWidth, finalHeight + bannerHeight)
                    val canvas = Canvas(finalBitmap)

                    canvas.save()
                    val matrix = Matrix().apply {
                        postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                        postRotate(rotation)
                        postTranslate(finalWidth / 2f, finalHeight / 2f)
                    }
                    canvas.drawBitmap(bitmap, matrix, null)
                    canvas.restore()
                    bitmap.recycle()

                    val paddingX = finalWidth * 0.02f
                    val textSizeTitle = finalWidth * 0.035f
                    val textSizeDate = finalWidth * 0.028f

                    val textYTitle = finalHeight + (bannerHeight * 0.45f)
                    val textYDate = finalHeight + (bannerHeight * 0.85f)
                    val textYCenterRight = finalHeight + (bannerHeight * 0.62f)

                    val paintBg = Paint().apply { color = android.graphics.Color.BLACK }
                    val paintProjet = Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = textSizeTitle
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                    }
                    val paintDate = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = textSizeDate
                        typeface = android.graphics.Typeface.DEFAULT
                        isAntiAlias = true
                        textAlign = Paint.Align.LEFT
                    }
                    val paintDecor = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = textSizeTitle
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    val paintSequence = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = textSizeTitle
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                        textAlign = Paint.Align.RIGHT
                    }

                    canvas.drawRect(0f, finalHeight.toFloat(), finalWidth.toFloat(), finalBitmap.height.toFloat(), paintBg)
                    canvas.drawText(safeProjet, paddingX, textYTitle, paintProjet)
                    canvas.drawText(currentDate, paddingX, textYDate, paintDate)
                    canvas.drawText("Décor: $decor", finalWidth / 2f, textYCenterRight, paintDecor)
                    canvas.drawText("Seq: $sequence", finalWidth - paddingX, textYCenterRight, paintSequence)

                    val savedUri = saveImageToGallery(context, finalBitmap, safeProjet)

                    if (savedUri != null) {
                        // safeProjet partout : le fichier sur disque et la fiche en base
                        // doivent toujours désigner le même projet.
                        coroutineScope.launch(Dispatchers.IO) {
                            photoDao.insert(PhotoEntity(uri = savedUri, projet = safeProjet, sequence = sequence, decor = decor, date = currentDate))
                        }
                        mainExecutor.execute { onPhotoSaved("Sauvegardée dans $safeProjet") }
                    } else {
                        mainExecutor.execute { onPhotoSaved("Erreur lors de la sauvegarde") }
                    }

                    finalBitmap.recycle()

                } catch (e: Throwable) {
                    Log.e("CameraX", "Erreur critique lors du traitement de la photo", e)
                    mainExecutor.execute { onPhotoSaved("Erreur : ${e.message ?: "Inconnue"}") }
                } finally {
                    imageProxy.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                mainExecutor.execute { onPhotoSaved("Erreur lors de la capture") }
            }
        }
    )
}

private fun saveImageToGallery(context: Context, bitmap: Bitmap, projet: String): String? {
    val prefs = context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE)
    val customTreeUriString = prefs.getString("storage_tree_uri", null)

    // Si un dossier personnalisé a été sélectionné via l'explorateur
    if (!customTreeUriString.isNullOrEmpty()) {
        try {
            val treeUri = customTreeUriString.toUri()
            val resolver = context.contentResolver
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

            // 1. Recherche du sous-dossier au nom du projet
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            var projectFolderUri: Uri? = null

            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    if (name.equals(projet, ignoreCase = true) && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val docId = cursor.getString(idIndex)
                        projectFolderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        break
                    }
                }
            }

            // 2. Création du sous-dossier s'il n'existe pas
            if (projectFolderUri == null) {
                projectFolderUri = DocumentsContract.createDocument(
                    resolver,
                    rootUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    projet
                )
            }

            // 3. Enregistrement de la photo dans le sous-dossier du projet
            val targetFolderUri = projectFolderUri ?: rootUri
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val imageUri = DocumentsContract.createDocument(
                resolver,
                targetFolderUri,
                "image/jpeg",
                fileName
            )

            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                return imageUri.toString()
            }
        } catch (e: Exception) {
            Log.e("SaveImage", "Erreur d'enregistrement SAF, bascule sur MediaStore", e)
        }
    }

    // Enregistrement par défaut dans MediaStore (DCIM/projet)
    val baseFolder = "DCIM"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseFolder/$projet")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    return if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            imageUri.toString()
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
}

@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChanged: (String) -> Unit,
    onProjetRenamed: (oldName: String, newName: String) -> Unit,
    onProjetDeleted: (projectName: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photo_raccord_prefs", Context.MODE_PRIVATE) }
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val scope = rememberCoroutineScope()

    var customTreeUri by remember {
        mutableStateOf(prefs.getString("storage_tree_uri", null))
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
                customTreeUri = selectedUri.toString()
                prefs.edit { putString("storage_tree_uri", selectedUri.toString()) }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "Échec de prise de permission persistante", e)
            }
        }
    }

    fun getDisplayFolderPath(uriString: String?): String {
        if (uriString.isNullOrEmpty()) return "DCIM (Par défaut)"
        val uri = uriString.toUri()
        val path = uri.path ?: return uriString
        return path.substringAfterLast(":")
    }

    val projets by photoDao.getDistinctProjets().collectAsState(initial = emptyList())

    var projectToRename by remember { mutableStateOf<String?>(null) }
    var newProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var isCleaning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Paramètres",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onClose) { Text("Retour") }
        }

        HorizontalDivider()

        // Tout le contenu ci-dessous défile ensemble si ça dépasse l'écran (et pas
        // seulement la liste des projets, comme auparavant).
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION : Palette de couleurs ---
            Text("Palette de couleurs", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onThemeChanged("AMBER") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme == "AMBER") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentTheme == "AMBER") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Ambre")
                    }
                    Button(
                        onClick = { onThemeChanged("VIOLET") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme == "VIOLET") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentTheme == "VIOLET") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Violet")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onThemeChanged("TURQUOISE") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme == "TURQUOISE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentTheme == "TURQUOISE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Turquoise")
                    }
                    Button(
                        onClick = { onThemeChanged("SYSTEM") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme == "SYSTEM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentTheme == "SYSTEM") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Système")
                    }
                }
            }

            HorizontalDivider()

            // --- SECTION : Dossier de stockage ---
            Text("Dossier de stockage", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dossier actuel : ${getDisplayFolderPath(customTreeUri)}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Changer le dossier")
                        }

                        if (!customTreeUri.isNullOrEmpty()) {
                            IconButton(onClick = {
                                customTreeUri = null
                                prefs.edit { remove("storage_tree_uri") }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Réinitialiser")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- SECTION : Projets enregistrés ---
            Text("Projets enregistrés", style = MaterialTheme.typography.titleMedium)

            if (projets.isEmpty()) {
                Text("Aucun projet enregistré.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    projets.forEach { proj ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(proj, style = MaterialTheme.typography.bodyLarge)
                                Row {
                                    IconButton(onClick = {
                                        projectToRename = proj
                                        newProjectName = proj
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Renommer")
                                    }
                                    IconButton(onClick = { projectToDelete = proj }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- SECTION : Maintenance ---
            Text("Maintenance", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    isCleaning = true
                    scope.launch(Dispatchers.IO) {
                        val allPhotos = photoDao.getAllPhotosOnce()
                        val orphans = allPhotos.filter { photo ->
                            val fileExists = try {
                                val uri = photo.uri.toUri()
                                context.contentResolver.openInputStream(uri)?.use { true } ?: false
                            } catch (_: Exception) {
                                false
                            }
                            !fileExists
                        }

                        if (orphans.isNotEmpty()) {
                            photoDao.deletePhotos(orphans)
                        }

                        withContext(Dispatchers.Main) {
                            isCleaning = false
                            Toast.makeText(
                                context,
                                "${orphans.size} référence(s) orpheline(s) supprimée(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = !isCleaning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isCleaning) "Nettoyage en cours..." else "Nettoyer les références supprimées")
            }
        }
    }

    // Dialogue Renommer
    projectToRename?.let { oldName ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Renommer le projet") },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Nouveau nom") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProjectName.isNotBlank() && newProjectName != oldName) {
                            scope.launch {
                                photoDao.renameProjet(oldName, newProjectName)
                                onProjetRenamed(oldName, newProjectName)
                                projectToRename = null
                            }
                        }
                    }
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) { Text("Annuler") }
            }
        )
    }

    // Dialogue Supprimer
    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Supprimer le projet ?") },
            text = { Text("Toutes les références des photos de \"$proj\" seront supprimées de la base de données.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            photoDao.deleteProjet(proj)
                            onProjetDeleted(proj)
                            projectToDelete = null
                        }
                    }
                ) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Annuler") }
            }
        )
    }
}

// Options de tri
enum class SortOption(val displayName: String) {
    DATE_DESC("Plus récentes"),
    DATE_ASC("Plus anciennes"),
    SEQUENCE_ASC("Séquence ↑"),
    SEQUENCE_DESC("Séquence ↓"),
    DECOR_ASC("Décor A-Z"),
    DECOR_DESC("Décor Z-A")
}

private fun updatePhotoBanner(
    context: Context,
    photoUriString: String,
    projet: String,
    newSequence: String,
    newDecor: String,
    date: String // Nouvel argument
) {
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

        val photoOnly = Bitmap.createBitmap(sourceBitmap, 0, 0, totalWidth, photoHeight)
        val updatedBitmap = createBitmap(totalWidth, totalHeight)
        val canvas = Canvas(updatedBitmap)

        canvas.drawBitmap(photoOnly, 0f, 0f, null)

        val paddingX = totalWidth * 0.02f
        val textSizeTitle = totalWidth * 0.035f
        val textSizeDate = totalWidth * 0.028f

        val textYTitle = photoHeight + (bannerHeight * 0.45f)
        val textYDate = photoHeight + (bannerHeight * 0.85f)
        val textYCenterRight = photoHeight + (bannerHeight * 0.62f)

        val paintBg = Paint().apply { color = android.graphics.Color.BLACK }

        val paintProjet = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = textSizeTitle
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        val paintDate = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = textSizeDate
            typeface = android.graphics.Typeface.DEFAULT
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        val paintDecor = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = textSizeTitle
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val paintSequence = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = textSizeTitle
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(0f, photoHeight.toFloat(), totalWidth.toFloat(), totalHeight.toFloat(), paintBg)

        canvas.drawText(projet, paddingX, textYTitle, paintProjet)
        canvas.drawText(date, paddingX, textYDate, paintDate)

        canvas.drawText("Décor: $newDecor", totalWidth / 2f, textYCenterRight, paintDecor)
        canvas.drawText("Seq: $newSequence", totalWidth - paddingX, textYCenterRight, paintSequence)

        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            updatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        photoOnly.recycle()
        updatedBitmap.recycle()
        sourceBitmap.recycle()
    } catch (e: Exception) {
        Log.e("UpdateBanner", "Erreur lors du remplacement du bandeau", e)
    }
}

class GalleryViewModel(private val photoDao: PhotoDao) : ViewModel() {
    private val _projet = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.DATE_DESC)

    fun initProjet(projet: String) {
        _projet.value = projet
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = _projet
        .flatMapLatest { p ->
            if (p.isNotBlank()) photoDao.getPhotosParProjet(p) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val decorsExistants: StateFlow<List<String>> = _projet
        .flatMapLatest { p ->
            if (p.isNotBlank()) photoDao.getDistinctDecorsParProjet(p) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSortedIndexedPhotos = combine(
        photos, searchQuery, sortOption
    ) { list, query, sort ->
        val filtered = if (query.isEmpty()) list else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            list.filter {
                it.decor.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                        it.sequence.lowercase(Locale.getDefault()).contains(lowerQuery)
            }
        }

        fun parseSeq(seq: String): Pair<Int, String> {
            val num = seq.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            val alpha = seq.filter { it.isLetter() }.lowercase(Locale.getDefault())
            return Pair(num, alpha)
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        fun parseDate(dateStr: String): Long {
            return try { dateFormat.parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }
        }

        val sorted = when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { parseDate(it.date) }
            SortOption.DATE_ASC -> filtered.sortedBy { parseDate(it.date) }
            SortOption.SEQUENCE_ASC -> filtered.sortedWith(compareBy({ parseSeq(it.sequence).first }, { parseSeq(it.sequence).second }))
            SortOption.SEQUENCE_DESC -> filtered.sortedWith(compareByDescending<PhotoEntity> { parseSeq(it.sequence).first }.thenByDescending { parseSeq(it.sequence).second })
            SortOption.DECOR_ASC -> filtered.sortedBy { it.decor.lowercase(Locale.getDefault()) }
            SortOption.DECOR_DESC -> filtered.sortedByDescending { it.decor.lowercase(Locale.getDefault()) }
        }

        sorted.mapIndexed { index, photo -> index to photo }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class GalleryViewModelFactory(private val dao: PhotoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun FabMenuItem(
    text: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(imageVector = icon, contentDescription = text)
        }
    }
}