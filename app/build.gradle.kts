// ============================================================================
// W2A / ZEngine — app Android (WebView com o jogo em assets/www)
//
// Tudo que muda por app vem de app/w2a.properties (escrito pelo CI a partir
// de dados validados) — nada de "sed" em código-fonte.
// Assinatura: arquivo .properties apontado por W2A_SIGNING_FILE (fora do repo).
//
// Código Kotlin em ../zekt: "main" sempre; cada recurso opcional tem a pasta
// com o SDK (iap, ads, firebase) e a vazia (noiap, noads, nofirebase). Assim o
// APK só leva o que o jogo usa (sem permissão de compra/anúncio à toa).
// ============================================================================
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val app = Properties().apply {
    val f = file("w2a.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun appProp(key: String, def: String): String = app.getProperty(key)?.takeIf { it.isNotBlank() } ?: def

val features = appProp("features", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
val admobAppId = appProp("admobAppId", "")
val hasIap = "iap" in features
val hasAds = "ads" in features && Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$").matches(admobAppId)
val hasFirebase = "firebase" in features && file("google-services.json").exists()

// Firebase: o plugin do Google lê o google-services.json (só quando o projeto usa)
if (hasFirebase) apply(plugin = "com.google.gms.google-services")

val signing = Properties().apply {
    val path = System.getenv("W2A_SIGNING_FILE")
    if (!path.isNullOrBlank() && file(path).exists()) file(path).inputStream().use { load(it) }
}

android {
    // Namespace do código é fixo; o ID do app (applicationId) vem do w2a.properties
    namespace = "com.w2a.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = appProp("applicationId", "com.w2a.app")
        minSdk = appProp("minSdk", libs.versions.minSdk.get()).toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appProp("versionCode", "1").toInt()
        versionName = appProp("versionName", "1.0.0")
        resValue("string", "app_name", appProp("appName", "App"))
        resValue("color", "splash_bg", appProp("backgroundColor", "#000000"))
        manifestPlaceholders["screenOrientation"] = appProp("orientation", "sensorLandscape")
        manifestPlaceholders["admobAppId"] = if (hasAds) admobAppId else ""
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf(
                "../zekt/main",
                if (hasIap) "../zekt/iap" else "../zekt/noiap",
                if (hasAds) "../zekt/ads" else "../zekt/noads",
                if (hasFirebase) "../zekt/firebase" else "../zekt/nofirebase"
            ))
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    signingConfigs {
        create("release") {
            val store = signing.getProperty("storeFile")
            if (!store.isNullOrBlank() && file(store).exists()) {
                storeFile = file(store)
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Arquivos do jogo não são comprimidos de novo (áudio/imagens já são) e
        // nada é ignorado (o padrão do aapt descarta pastas que começam com "_")
        noCompress += listOf("png", "jpg", "jpeg", "webp", "ogg", "m4a", "mp3", "webm", "mp4", "zlp", "wasm")
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    if (hasIap) implementation(libs.play.billing)
    if (hasAds) implementation(libs.play.services.ads)
    if (hasFirebase) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.messaging)
    }
}
