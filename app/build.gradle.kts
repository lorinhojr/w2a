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
     * ASSINATURA SIMPLES E DIRETA
     */
    signingConfigs {
        create("release") {
            // Verifica se existe chave1.jks no diretório do app
            val keystoreFile = file("chave1.jks")
            
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword") ?: "android"
                keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias") ?: "androiddebugkey"
                keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword") ?: "android"
                println("✅ Usando chave1.jks para assinatura")
            } else {
                // Tenta usar variável de ambiente
                val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")
                if (storeFilePath != null && storeFilePath.isNotBlank()) {
                    storeFile = file(storeFilePath)
                    storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword") ?: "android"
                    keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias") ?: "androiddebugkey"
                    keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword") ?: "android"
                    println("✅ Usando keystore de variável: $storeFilePath")
                } else {
                    println("⚠ Nenhum keystore configurado, usando debug.keystore padrão")
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            
            // Só usa signing se tiver configurado
            if (signingConfigs.findByName("release")?.storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
                println("✅ Build release será assinado")
            } else {
                println("⚠ Build release não será assinado (usará debug)")
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
    
    // REMOVE A APPTIONS DEPRECIADA
    // aaptOptions {
    //     cruncherEnabled = false
    // }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.webkit)
}
