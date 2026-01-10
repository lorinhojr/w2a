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
            // Usa keystore gerado no CI ou secreto do GitHub
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")
            val storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword")
            val keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias")
            val keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword")

            if (storeFilePath != null && storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword?.let { storePassword = it }
                keyAlias?.let { keyAlias = it }
                keyPassword?.let { keyPassword = it }
            }
            // Se não tiver variáveis, não configura assinatura (usará debug)
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            
            // Só usa signingConfig se estiver configurado
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")
            if (storeFilePath != null && storeFilePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Se não tiver keystore, usa debug (assina com debug.keystore padrão)
                isDebuggable = true
            }

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
