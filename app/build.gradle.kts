plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.props.photo_raccord"
    compileSdk {
        version = release(37) {
        }
    }

    defaultConfig {
        applicationId = "com.props.photo_raccord"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Récupération compatible avec le Configuration Cache de Gradle
        val gitCommitCount = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)
        versionCode = gitCommitCount
        versionName = "0.1.$gitCommitCount"
    }

    buildTypes {
        release {
            // Optimisation R8 activée : réduit la taille de l'APK et accélère le code
            // (auparavant désactivée, ce qui annulait tout l'intérêt du build release).
            optimization {
                enable = true
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Room (Base de données locale)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (Préférences)
    implementation(libs.androidx.datastore.preferences)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    implementation(libs.accompanist.permissions)

    // Galerie
    implementation(libs.coil.compose)

    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation("androidx.documentfile:documentfile:1.1.0")
}