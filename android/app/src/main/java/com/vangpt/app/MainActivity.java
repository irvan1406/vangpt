package com.vangpt.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

// VanGPT — aplikasi web-to-app: membungkus situs VanGPT dalam WebView.
// Pola sama dengan VanNota (tanpa jembatan printer): JavaScript + DOM
// storage aktif (preferensi identitas tersimpan lokal), tema hitam,
// tautan luar dibuka di browser, tombol Kembali menelusuri riwayat.
public class MainActivity extends Activity {
    private WebView webView;
    private boolean showingError;

    private static final String ERROR_HTML =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>body{background:#050505;color:#e0e0e0;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center}" +
            "a{display:inline-block;margin-top:18px;background:#b30000;color:#fff;padding:14px 28px;border-radius:12px;text-decoration:none;font-weight:bold}</style></head>" +
            "<body><div><div style='font-size:44px'>&#128225;</div><h2>Koneksi terputus</h2>" +
            "<p>VanGPT butuh internet untuk menyala.</p>" +
            "<a href='" + BuildConfig.WEB_APP_URL + "'>COBA LAGI</a></div></body></html>";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.rgb(5, 5, 5));
            getWindow().setNavigationBarColor(Color.rgb(5, 5, 5));

            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(5, 5, 5));
            setContentView(webView);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setUserAgentString(settings.getUserAgentString() + " VanGPTAndroid/1.0");

            webView.setWebChromeClient(new WebChromeClient());

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String host = uri.getHost();
                    if (host != null && host.equals("irvan1406.github.io")) return false;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (url.startsWith("https://")) showingError = false;
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame() && !showingError) {
                        showingError = true;
                        view.loadDataWithBaseURL(null, ERROR_HTML, "text/html", "UTF-8", null);
                    }
                }
            });

            webView.loadUrl(BuildConfig.WEB_APP_URL);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) webView.destroy();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
