# Add project specific R8 rules here.
# AGP will combine all keep rule files in src/main/keepRules to pass to R8
#
# For more details, see
#   https://d.android.com/r/tools/r8/keep-rules

# NOTE : AndroidX, Compose, CameraX, Room et Coil embarquent déjà leurs propres
# règles ProGuard "consumer" dans leurs AAR. Les anciennes règles -keep class
# androidx.** / androidx.compose.** / kotlinx.coroutines.** { *; } désactivaient
# le shrinking/obfuscation sur des pans entiers du code et gonflaient l'APK
# release inutilement. On ne garde que ce qui concerne notre propre code.

# Room : entités et DAO utilisés par réflexion au runtime
-keep class com.props.photo_raccord.PhotoEntity { *; }
-keep interface com.props.photo_raccord.PhotoDao { *; }

# ViewModels (peuvent être instanciés par réflexion selon le mécanisme utilisé)
-keep class * extends androidx.lifecycle.ViewModel { *; }

-keepattributes *Annotation*

# Si votre projet utilise WebView avec JS, décommentez et spécifiez le nom
# qualifié complet de la classe d'interface JavaScript :
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}