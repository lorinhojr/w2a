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
        
        buildFeatures {
            buildConfig = true
        }
    }

    /**
     * ASSINATURA FIXA - SEM FALLBACK BUGADO
     */
    signingConfigs {
        create("release") {
            // Procura o keystore na raiz do projeto app/
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")
            
            if (storeFilePath != null && storeFilePath.isNotBlank()) {
                // Se encontrar, configura a assinatura
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword") ?: "android"
                keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias") ?: "androiddebugkey"
                keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword") ?: "android"
            }
            // Se não encontrar, NÃO CONFIGURA - vai usar debug
        }
    }
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            
            // Só usa signing config se estiver configurado
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")
            if (storeFilePath != null && storeFilePath.isNotBlank() && 
                signingConfigs.findByName("release")?.storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Se não configurar, Gradle usa debug.keystore automático
    
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    
        getByName("debug") {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
    
    aaptOptions {
        cruncherEnabled = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.webkit)
}
