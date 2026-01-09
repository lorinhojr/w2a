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
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); 
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        
        // Habilita acesso total a arquivos locais (Essencial para Construct 3)
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Se der erro de "Arquivo não encontrado" ou "Acesso negado", vai aparecer aqui!
                view.loadData("Erro: " + error.getDescription(), "text/html", "UTF-8");
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }
}
