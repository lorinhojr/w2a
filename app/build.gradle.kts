plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // IMPORTANTE PARA O AAB: O namespace deve ser dinâmico
    namespace = "PACOTE_DINAMICO"
    compileSdk = 34 // Versão estável recomendada para evitar erros de preview

    defaultConfig {
        // IDENTIDADE NA PLAY STORE: O applicationId deve ser dinâmico
        applicationId = "PACOTE_DINAMICO"
        
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Configuração de assinatura automática (gerenciada pelo main.yml)
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

    kotlinOptions {
        jvmTarget = "11"
    }

    // Garante que os recursos do jogo (assets) não sejam comprimidos 
    // Isso ajuda na velocidade de carregamento do Construct 3
    aaptOptions {
        noCompress(".*")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // BIBLIOTECA VITAL PARA O CONSTRUCT 3 (WebViewAssetLoader)
    implementation("androidx.webkit:webkit:1.12.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
