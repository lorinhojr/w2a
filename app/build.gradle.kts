plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "PACOTE_DINAMICO"
    compileSdk = 34

    defaultConfig {
        applicationId = "PACOTE_DINAMICO"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    /**
     * ===============================
     * ASSINATURA (USANDO ENV VARS)
     * ===============================
     * Essas variáveis vêm direto do GitHub Actions:
     *
     * ORG_GRADLE_PROJECT_storeFile
     * ORG_GRADLE_PROJECT_storePassword
     * ORG_GRADLE_PROJECT_keyAlias
     * ORG_GRADLE_PROJECT_keyPassword
     */
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("ORG_GRADLE_PROJECT_storeFile")

            if (storeFilePath != null && storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword")
                keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias")
                keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            // debug continua usando a debug.keystore automática
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // ESSENCIAL PARA O CONSTRUCT 3
    implementation("androidx.webkit:webkit:1.12.1")
}
