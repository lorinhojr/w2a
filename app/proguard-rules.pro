# ====================================================
# CONFIGURAÇÃO BÁSICA DO PROGUARD
# ====================================================

# Mantém atributos importantes
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions
-keepattributes *Annotation*
-keepattributes JavascriptInterface
-keepattributes SourceFile,LineNumberTable

# Remove logs em release (otimização)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

# ====================================================
# CLASSES DO ANDROID/ANDROIDX (MANTÉM TUDO)
# ====================================================

-keep class androidx.** { *; }
-dontwarn androidx.**

-keep class android.** { *; }
-dontwarn android.**

# Mantém compatibilidade com WebView
-keep class org.chromium.** { *; }
-dontwarn org.chromium.**

-keep class com.android.webview.** { *; }
-dontwarn com.android.webview.**

# ====================================================
# GOOGLE PLAY SERVICES E ADMOB (ESSENCIAL)
# ====================================================

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.google.ads.** { *; }
-dontwarn com.google.ads.**

# Regras específicas para AdMob
-keep public class com.google.android.gms.ads.** {
    public *;
}

-keep public class com.google.ads.** {
    public *;
}

# Ads mediation
-keep class com.google.android.gms.ads.mediation.** { *; }
-dontwarn com.google.android.gms.ads.mediation.**

# AdMob Adapters
-keep class * extends com.google.android.gms.ads.mediation.customevent.CustomEventAdapter { *; }
-keep class * extends com.google.android.gms.ads.mediation.Adapter { *; }

# ====================================================
# FIREBASE (SE USAR)
# ====================================================

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**

# Firebase Messaging (notificações)
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.messaging.**

# ====================================================
# WEBVIEW E JAVASCRIPT (CRÍTICO PARA PLUGINS C2/C3)
# ====================================================

# Mantém todas as classes do WebView
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**

# Mantém WebViewClient e WebChromeClient
-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }

# Mantém a classe WebView
-keepclassmembers class * extends android.webkit.WebView {
    public *;
    protected *;
}

# Interface JavaScript (ESSENCIAL para plugins)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Mantém métodos chamados via JavaScript
-keepclasseswithmembers class * {
    public void on*(...);
    public void js*(...);
    public void cordova*(...);
}

# ====================================================
# PLUGINS DO CONSTRUCT 2/3 (CORDOVA COMPATIBILITY)
# ====================================================

# Cordova/PhoneGap
-keep class org.apache.cordova.** { *; }
-dontwarn org.apache.cordova.**

-keep class org.apache.cordova.engine.** { *; }
-dontwarn org.apache.cordova.engine.**

# Plugins comuns do C2/C3
-keep class cordova.plugins.** { *; }
-dontwarn cordova.plugins.**

-keep class com.rjfun.cordova.** { *; }
-dontwarn com.rjfun.cordova.**

-keep class com.admob.** { *; }
-dontwarn com.admob.**

# FileProvider e Content Providers
-keep class * extends android.content.ContentProvider { *; }
-keep class * extends androidx.core.content.FileProvider { *; }

# File operations
-keep class * implements java.io.Serializable { *; }

# ====================================================
# ATIVIDADES, SERVICES E COMPONENTES DO APP
# ====================================================

# Mantém todas as activities
-keep public class * extends android.app.Activity {
    public <init>(...);
    public void *(...);
}

# Mantém todas as services
-keep public class * extends android.app.Service {
    public <init>(...);
    public void *(...);
}

# Mantém todos os broadcast receivers
-keep public class * extends android.content.BroadcastReceiver {
    public <init>(...);
    public void *(...);
}

# Mantém content providers
-keep public class * extends android.content.ContentProvider {
    public <init>(...);
    public void *(...);
}

# ====================================================
# REFLEXÃO E ANOTAÇÕES (PLUGINS USAM MUITO)
# ====================================================

# Mantém métodos nativos
-keepclasseswithmembernames class * {
    native <methods>;
}

# Mantém construtores
-keepclasseswithmembers class * {
    public <init>(...);
}

# Mantém métodos com anotações específicas
-keep @interface * {
    *;
}

# Mantém enumerações
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ====================================================
# SERIALIZAÇÃO/PARCELABLE
# ====================================================

# Mantém Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Mantém Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ====================================================
# RECURSOS DO APP
# ====================================================

# Mantém classes R (recursos)
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Mantém BuildConfig
-keep class **.BuildConfig { *; }

# ====================================================
# CLASSES ESPECÍFICAS DO W2A
# ====================================================

# Mantém a MainActivity e métodos relacionados
-keep class PACOTE_DINAMICO.MainActivity {
    *;
}

-keep class PACOTE_DINAMICO.** {
    *;
}

# Mantém métodos chamados via reflexão
-keepclasseswithmembers class PACOTE_DINAMICO.** {
    public *;
}

# ====================================================
# BIBLIOTECAS DE TERCEIROS (SE USAR)
# ====================================================

# Glide (para imagens)
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Retrofit/OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# ====================================================
# CONFIGURAÇÕES DE OTIMIZAÇÃO
# ====================================================

# Otimizações de código (seguro para Android)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Mantém nomes de métodos para debugging
-keepnames class * {
    public <methods>;
}

# Não compacta arquivos .xml e .png (necessário para runtime)
-dontshrink
-dontoptimize resources.txt
-dontobfuscate resources.txt

# ====================================================
# RESOLUÇÃO DE CONFLITOS (WARNINGS)
# ====================================================

# Resolve warnings comuns
-dontnote **
-dontwarn sun.misc.**
-dontwarn javax.annotation.**
-dontwarn org.w3c.dom.**
-dontwarn javax.lang.**
-dontwarn javax.naming.**
-dontwarn javax.net.**
-dontwarn javax.security.**
-dontwarn javax.xml.**
-dontwarn org.xml.**
-dontwarn org.apache.**
-dontwarn android.net.http.**

# Java 8+ compatibility
-dontwarn java.lang.invoke.**
-dontwarn java.util.stream.**

# ====================================================
# REGRAS ESPECIAIS PARA GAMES HTML5
# ====================================================

# Mantém classes de jogos HTML5/Construct
-keep class * {
    public void onGame*(...);
    public void onLoad*(...);
    public void onReady*(...);
}

# Mantém classes com "Game" no nome
-keep class *Game* {
    *;
}

# Mantém classes com "Plugin" no nome
-keep class *Plugin* {
    *;
}

# Mantém classes com "Cordova" no nome
-keep class *Cordova* {
    *;
}

# ====================================================
# EXCLUSÕES FINAIS (SEGURANÇA)
# ====================================================

# Não remover métodos vazios ou construtores padrão
-keepclassmembers class * {
    void <init>();
}

# Mantém métodos getter/setter
-keepclassmembers class * {
    *** get*();
    void set*(***);
}

# Mantém métodos callback
-keepclassmembers class * {
    void *Callback(...);
    void *Listener(...);
}
