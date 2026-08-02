package com.props.photo_raccord.screens

import android.Manifest
import android.view.OrientationEventListener
import android.view.Surface
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.props.photo_raccord.AppDatabase
import com.props.photo_raccord.utils.takeAndProcessPhoto
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(projet: String, sequence: String, decor: String, onClose: () -> Unit) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var notificationMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notificationMessage) { if (notificationMessage != null) { kotlinx.coroutines.delay(2000); notificationMessage = null } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(projet, sequence, decor, onClose) { notificationMessage = it }
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
            Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).safeDrawingPadding(), color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium) {
                Text(message, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun CameraPreview(projet: String, sequence: String, decor: String, onClose: () -> Unit, onPhotoSaved: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    val photoDao = remember { AppDatabase.getDatabase(context).photoDao() }
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(5f) }

    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return
                }
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

    Box(modifier = Modifier.fillMaxSize()
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { camera?.let { cam -> val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f; cam.cameraControl.setZoomRatio(if (current > 1.2f) 1f else 2.5f.coerceAtMost(maxZoom)) } }) }
        .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> camera?.let { cam -> val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f; cam.cameraControl.setZoomRatio((currentZoom * zoom).coerceIn(minZoom, maxZoom)) } } }) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx)
            ProcessCameraProvider.getInstance(ctx).addListener({
                val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                try {
                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    camera = boundCamera
                    boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { state -> minZoom = state.minZoomRatio; maxZoom = state.maxZoomRatio }
                } catch (exc: Exception) { android.util.Log.e("CameraX", "Échec", exc) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }, modifier = Modifier.fillMaxSize())

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(32.dp).safeDrawingPadding()) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Text("Retour", color = Color.White) }
            Box(modifier = Modifier.size(72.dp).align(Alignment.Center).background(Color.White, CircleShape).border(4.dp, Color.LightGray, CircleShape)
                .clickable(onClick = { takeAndProcessPhoto(context, imageCapture, cameraExecutor, coroutineScope, photoDao, projet, sequence, decor, onPhotoSaved) })) {}
            IconButton(onClick = {
                flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON; ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF }
                imageCapture.flashMode = flashMode
            }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(when (flashMode) { ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn; ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto; else -> Icons.Default.FlashOff }, "Flash", tint = Color.White)
            }
        }
    }
}
