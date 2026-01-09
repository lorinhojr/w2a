package com.meuapp.jogo;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader; // Import necessário para WebViewAssetLoader

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // FORÇA ACELERAÇÃO DE HARDWARE (Igual o Construct faz)
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        WebView webView = new WebView(this);
        setContentView(webView);

        // Configurações críticas para sair da tela preta
        WebSettings settings = webView.getSettings();
        
        // 1. Ativa o JavaScript (Obrigatório para o motor do jogo)
        settings.setJavaScriptEnabled(true);
        
        // 2. Ativa o armazenamento local (Onde o jogo salva dados e variáveis)
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        
        // 3. AS LINHAS MÁGICAS: Permite que o JS leia os arquivos .json e .js da pasta assets
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        
        // 4. Melhora a performance do jogo
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Desativa o zoom que pode quebrar o layout do C3
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Faz o jogo ocupar a tela toda (Imersivo)
        webView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Configuração do WebViewAssetLoader para permitir Web Workers e melhor segurança
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        // 5. Garante que o jogo use toda a tela e não abra o Chrome externo
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Isso faz o Android pensar que o jogo está num site https://appassets.androidplatform.net/assets/www/index.html  
                // em vez de um arquivo local. Isso libera o uso de Workers!
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // Em caso de erro de carregamento, tente recarregar ou mostre uma mensagem
            }
        });

        // 6. Carrega o jogo usando o asset loader (necessário para Web Workers funcionarem)
        // IMPORTANTE: Esta URL especial permite que o jogo acesse recursos locais como se fosse um site HTTPS
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");

        /*
         * IMPORTANTE: No seu build.gradle (Module: app) adicione esta dependência:
         * implementation 'androidx.webkit:webkit:1.8.0'
         * 
         * E no AndroidManifest.xml, certifique-se de ter:
         * <uses-permission android:name="android.permission.INTERNET" />
         */
    }
}
