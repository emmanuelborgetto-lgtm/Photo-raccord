package com.props.photo_raccord

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class PhotoraccordApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        // Pré-initialisation de CameraX en arrière-plan sur le thread principal
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Le provider est initialisé et mis en cache par le système CameraX
        }, ContextCompat.getMainExecutor(this))
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                // Cache mémoire dédié : évite de redécoder les vignettes à chaque
                // scroll/recomposition de la galerie, ce qui rend le défilement plus fluide.
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}