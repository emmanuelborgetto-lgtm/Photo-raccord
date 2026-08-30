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

package com.props.photo_raccord.screens

import android.Manifest
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.props.photo_raccord.AppDatabase
import com.props.photo_raccord.utils.takeAndProcessPhoto
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CameraScreen(projet: String, sequence: String, decor: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    // Utilisation de l'API native pour les permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
    }

    // Vérification initiale de la permission
    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasPermission = permissionCheck
        if (!permissionCheck) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(notificationMessage) {
        if (notificationMessage != null) {
            kotlinx.coroutines.delay(2000.milliseconds)
            notificationMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(
                projet = projet,
                sequence = sequence,
                decor = decor,
                lifecycleOwner = lifecycleOwner,
                onClose = onClose,
                onPhotoSaved = { message -> notificationMessage = message }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("L'application a besoin d'accéder à l'appareil photo.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Autoriser la caméra")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text("Annuler") }
            }
        }

        // Notification toast
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
    lifecycleOwner: LifecycleOwner,
    onClose: () -> Unit,
    onPhotoSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // État pour la caméra, le zoom, et le provider déjà résolu (voir DisposableEffect ci-dessous)
    var camera: Camera? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(5f) }

    // Nettoyage des ressources
    DisposableEffect(Unit) {
        onDispose {
            // 1. Arrêt propre de l'executor
            cameraExecutor.shutdownNow()
            try {
                if (!cameraExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cameraExecutor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                cameraExecutor.shutdownNow()
                Thread.currentThread().interrupt()
            }

            // 2. Libération de la caméra.
            // IMPORTANT : on réutilise le provider déjà résolu (stocké lors du bindToLifecycle)
            // au lieu de rappeler ProcessCameraProvider.getInstance(context).get(), qui est un
            // appel BLOQUANT sur le thread appelant. Comme onDispose s'exécute sur le thread
            // principal, ce .get() pouvait geler l'UI le temps que CameraX libère le matériel
            // caméra (c'est la cause probable des "Skipped N frames" au retour de cet écran).
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
                // Ignorer les erreurs de libération
            }
        }
    }

    // Gestion de l'orientation
    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        orientationEventListener.enable()
        onDispose { orientationEventListener.disable() }
    }

    // Gestion du zoom
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        camera?.let { cam ->
                            val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                            cam.cameraControl.setZoomRatio(
                                if (currentZoom > 1.2f) 1f else 2.5f.coerceAtMost(maxZoom)
                            )
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    camera?.let { cam ->
                        val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        cam.cameraControl.setZoomRatio(
                            (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                        )
                    }
                }
            }
    ) {
        // Preview de la caméra
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener(
                    {
                        // .get() est sans risque ici : le listener n'est appelé qu'APRÈS
                        // résolution du Future, donc l'appel ne bloque pas.
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }

                        try {
                            // Désassocier les anciennes liaisons
                            provider.unbindAll()

                            // Créer une nouvelle liaison
                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )

                            // Mettre à jour les limites de zoom
                            camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
                                minZoom = state.minZoomRatio
                                maxZoom = state.maxZoomRatio
                            }
                        } catch (exc: Exception) {
                            android.util.Log.e("CameraX", "Échec de la liaison de la caméra", exc)
                        }
                    },
                    ContextCompat.getMainExecutor(ctx)
                )
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Contrôles de la caméra
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp)
                .safeDrawingPadding()
        ) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text("Retour", color = Color.White)
            }

            // Bouton de capture
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center)
                    .background(Color.White, CircleShape)
                    .border(4.dp, Color.LightGray, CircleShape)
                    .clickable(
                        onClick = {
                            takeAndProcessPhoto(
                                context = context,
                                imageCapture = imageCapture,
                                cameraExecutor = cameraExecutor,
                                coroutineScope = coroutineScope,
                                photoDao = photoDao,
                                projet = projet,
                                sequence = sequence,
                                decor = decor,
                                onPhotoSaved = onPhotoSaved
                            )
                        }
                    )
            ) {}

            // Bouton du flash
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    imageCapture.flashMode = flashMode
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        }
    }
}