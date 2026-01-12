plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services) // Google Services para AdMob e Firebase
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
            viewBinding = true // Para facilitar binding de views
        }
        
        // Para os providers funcionarem corretamente
        manifestPlaceholders = mapOf(
            "applicationId" to applicationId,
            "exported" to "true" // Android 12+ requer explicitamente
        )
        
        // Para AdMob
        resValue("string", "app_name", "Meu App") // Nome do app
        resValue("string", "admob_app_id", "ca-app-pub-3940256099942544~3347511713") // ID de teste
        resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/6300978111") // Banner de teste
        resValue("string", "admob_interstitial_id", "ca-app-pub-3940256099942544/1033173712") // Interstitial de teste
        
        // Para performance
        multiDexEnabled = true // Para apps grandes
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
            isMinifyEnabled = true // Ativar minify para release
            isShrinkResources = true // Reduzir recursos
            isDebuggable = false
            
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
            
            // Configurações específicas para release
            resValue("string", "app_name", "Meu App") // Nome do app em release
            resValue("string", "admob_app_id", "SEU_ID_REAL_AQUI") // Substituir pelo ID real
            buildConfigField("boolean", "IS_DEBUG", "false")
        }

        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug" // Para poder instalar debug e release juntos
            versionNameSuffix = "-DEBUG"
            buildConfigField("boolean", "IS_DEBUG", "true")
            
            // IDs de teste para AdMob
            resValue("string", "admob_app_id", "ca-app-pub-3940256099942544~3347511713")
            resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "admob_interstitial_id", "ca-app-pub-3940256099942544/1033173712")
        }
        
        // Build type para desenvolvimento
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
    
    // Configurações de build
    buildFeatures {
        compose = false // Se não usar Compose
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
                "META-INF/LGPL2.1",
                "**/publicsuffixes.gz",
                "androidsupportmultidexversion.txt"
            )
            // Manter arquivos nativos
            pickFirsts += listOf(
                "**/libc++_shared.so",
                "**/libjsc.so"
            )
        }
    }
    
    // Configurações de lint (opcional)
    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
    
    // Para testes
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // ==================== ANDROIDX CORE ====================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core)              // Para FileProvider (não KTX)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // ==================== WEBVIEW ====================
    implementation(libs.androidx.webkit)            // WebView moderno
    
    // ==================== ACTIVITY/FRAGMENT ====================
    implementation(libs.androidx.activity.ktx)      // Para permissões em tempo de execução
    implementation(libs.androidx.fragment.ktx)      // Para compatibilidade
    implementation(libs.androidx.legacy.support)    // Para compatibilidade com código antigo
    
    // ==================== GOOGLE SERVICES ====================
    // AdMob e Analytics
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("com.google.android.gms:play-services-base:18.3.0")
    
    // Firebase (opcional mas recomendado)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging") // Para notificações push
    
    // ==================== PERFORMANCE ====================
    implementation("androidx.multidex:multidex:2.0.1") // Para apps grandes
    
    // ==================== UTILITÁRIOS ====================
    // Para imagens (se precisar)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Para network (se precisar)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // ==================== PARA TESTES ====================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // ==================== PARA PLUGINS C2/C3 ====================
    // Dependências adicionais que podem ser necessárias para plugins específicos
    
    // Para câmera
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Para localização
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // Para notificações
    implementation("androidx.core:core:1.13.0")
    
    // Para vibração
    implementation("androidx.core:core:1.13.0")
    
    // ==================== KOTLIN EXTENSIONS ====================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}")
    
    // ==================== DEBUG ====================
    debugImplementation("com.facebook.stetho:stetho:1.6.0")
    debugImplementation("com.facebook.stetho:stetho-okhttp3:1.6.0")
}

// Adicione isso no final do arquivo se quiser tasks customizadas
tasks.register("printVersions") {
    doLast {
        println("Compile SDK: ${android.compileSdk}")
        println("Min SDK: ${android.defaultConfig.minSdk}")
        println("Target SDK: ${android.defaultConfig.targetSdk}")
        println("Version Code: ${android.defaultConfig.versionCode}")
        println("Version Name: ${android.defaultConfig.versionName}")
    }
}
