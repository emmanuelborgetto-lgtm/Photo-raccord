# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }
-keepclassmembers class * implements androidx.camera.core.Camera {
    public <methods>;
}

# Room
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-keep class * extends androidx.room.Database { *; }

# Coil
-keep class coil.** { *; }
-keep interface coil.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# Gestion des fuites mémoire
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.lifecycle.ViewModelInject <methods>;
    @androidx.room.* <methods>;
}

# Conserver les classes des ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }