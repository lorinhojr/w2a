package com.meuapp.jogo;

import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WebView webView = new WebView(this);
        setContentView(webView);

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

        // 5. Garante que o jogo use toda a tela e não abra o Chrome externo
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // Em caso de erro de carregamento, tente recarregar ou mostre uma mensagem
            }
        });

        // 6. Carrega o jogo
        // IMPORTANTE: Se o index.html estiver lá, isso tem que abrir.
        webView.loadUrl("file:///android_asset/www/index.html");
    }
}
