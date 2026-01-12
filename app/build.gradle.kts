plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Adicionar plugin do Google Services se for usar AdMob
    // alias(libs.plugins.google.services) // Se tiver na sua libs.versions.toml
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
        
        // Para os providers funcionarem corretamente
        manifestPlaceholders["applicationId"] = applicationId
        
        // Para Android 12+
        if (minSdk >= 31) {
            manifestPlaceholders["exported"] = "true"
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
    
    // Para o FileProvider e compatibilidade
    packaging {
        resources {
            // Excluir arquivos desnecessários para reduzir APK
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    // Dependências básicas existentes
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.webkit)
    
    // 🔧 ADICIONAR ESSAS PARA PLUGINS DO C2/C3:
    
    // 1. PARA FileProvider funcionar (ESSENCIAL)
    implementation("androidx.core:core:1.12.0")
    
    // 2. Para compatibilidade com AndroidX
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    
    // 3. PARA ADMOB (opcional - adicione só se for usar)
    // implementation("com.google.android.gms:play-services-ads:22.6.0")
    
    // 4. PARA GOOGLE PLAY SERVICES (opcional)
    // implementation("com.google.android.gms:play-services-base:18.3.0")
    
    // 5. Para permissões em tempo de execução (Android 6+)
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // 6. Para WebView moderno
    implementation("androidx.webkit:webkit:1.9.0")
    
    // 7. Para notificações (Android 13+)
    implementation("androidx.core:core-ktx:1.12.0")
}
