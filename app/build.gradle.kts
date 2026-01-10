plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "PACOTE_DINAMICO"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "PACOTE_DINAMICO"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        
        // Suporte a WebView moderno
        buildFeatures {
            buildConfig = true
        }
    }

    /**
     * ===============================
     * ASSINATURA (USANDO ENV VARS)
     * ===============================
     */
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")

            if (storeFilePath != null && storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword") ?: "android"
                keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias") ?: "androiddebugkey"
                keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword") ?: "android"
            } else {
                // Fallback para debug keystore
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
    
    // Configurações de recursos - REMOVIDA aaptOptions depreciada
    androidResources {
        // Desabilitar compressão para WebP (se necessário)
        ignoreAssetsPattern += "!.webp:!.webm:!.mp4:!.mp3:!.ogg:!.wav"
    }
    
    // Habilitar namespace para recursos
    buildFeatures {
        buildConfig = true
        viewBinding = false
        dataBinding = false
        aidl = true
        renderScript = false
        resValues = false
        shaders = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.webkit)
}